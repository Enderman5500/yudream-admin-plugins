package online.yudream.base.plugin.mail.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import online.yudream.base.plugin.mail.application.NotFoundException;
import online.yudream.base.plugin.mail.application.dto.InboxResult;
import online.yudream.base.plugin.mail.application.dto.TransportTestResult;
import online.yudream.base.plugin.mail.application.port.ImapClient;
import online.yudream.base.plugin.mail.application.port.MailTransportException;
import online.yudream.base.plugin.mail.application.query.InboxQuery;
import online.yudream.base.plugin.mail.application.support.MailSettingsFactory;
import online.yudream.base.plugin.mail.domain.aggregate.InboxMessage;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.repo.InboxMessageRepository;

/**
 * 收件箱用例：自动拉取 + 持久化。
 *
 * <p>每次查询先把 IMAP 最近信封 upsert 进文档库（键 = 地址 + 收件夹 + UID，信封以服务端为准、
 * 已下载正文保留），再从持久化副本里过滤分页返回。IMAP 短暂不可用时降级读本地缓存，
 * 页面仍能浏览历史邮件；正文只在首次打开详情时下载并缓存（{@code bodyFetched}）。</p>
 */
public class InboxService {

    /** 核验时最多下载正文的候选邮件数。 */
    public static final int MAX_DETAIL_FETCH = 10;

    private final MailAddressService addressService;
    private final ImapClient imap;
    private final InboxMessageRepository messages;

    public InboxService(MailAddressService addressService, ImapClient imap, InboxMessageRepository messages) {
        this.addressService = addressService;
        this.imap = imap;
        this.messages = messages;
    }

    /** 同步 + 查询：先拉取最新信封落库，再返回持久化副本的过滤分页结果。 */
    public InboxResult list(InboxQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }
        MailAddress address = requireImapReady(query.addressId());
        String folder = resolveFolder(address, query.folder());

        int fetched = 0;
        int mailboxTotal = -1;
        boolean synced = false;
        try {
            int limit = query.limit() == null ? address.imap().fetchLimit() : query.safeLimit();
            ImapClient.InboundMailPage page = imap.fetchSummaries(settings(address), folder, limit);
            upsertSummaries(address.id(), folder, page.records());
            fetched = page.records().size();
            mailboxTotal = page.mailboxTotal();
            synced = true;
        }
        catch (MailTransportException | IllegalArgumentException ignored) {
            // IMAP 不可用：降级读本地缓存，不影响浏览历史邮件
        }

        List<InboxMessage> filtered = messages.findAllForFolder(address.id(), folder).stream()
                .filter(message -> matchesEnvelope(message, query))
                .toList();
        int fromIndex = Math.min((query.safePage() - 1) * query.safeSize(), filtered.size());
        int toIndex = Math.min(fromIndex + query.safeSize(), filtered.size());
        return new InboxResult(filtered.subList(fromIndex, toIndex), filtered.size(),
                mailboxTotal, fetched, folder, synced,
                synced && fetched < mailboxTotal);
    }

    /** 邮件详情：优先返回已缓存正文，否则从 IMAP 下载并落库。 */
    public InboxMessage detail(String addressId, String folder, String uid) {
        MailAddress address = requireImapReady(addressId);
        requireUid(uid);
        String target = resolveFolder(address, folder);
        String id = InboxMessage.documentId(address.id(), target, uid.trim());
        Optional<InboxMessage> cached = messages.findById(id);
        if (cached.isPresent() && cached.get().bodyFetched()) {
            return cached.get();
        }
        ImapClient.InboundMailDetail detail = imap.fetchDetail(settings(address), target, uid.trim());
        InboxMessage stored = cached
                .map(previous -> mergeDetail(previous, detail))
                .orElseGet(() -> InboxMessage.fromDetail(address.id(), target, detail));
        return messages.save(stored);
    }

    /** 标记/取消标记已读：写 IMAP 服务端并同步本地副本。 */
    public void markSeen(String addressId, String folder, String uid, boolean seen) {
        MailAddress address = requireImapReady(addressId);
        requireUid(uid);
        String target = resolveFolder(address, folder);
        imap.markSeen(settings(address), target, uid.trim(), seen);
        String id = InboxMessage.documentId(address.id(), target, uid.trim());
        messages.findById(id).ifPresent(message -> messages.save(message.withSeen(seen)));
    }

    /** 删除邮件：服务端永久移除（\Deleted + expunge），并移除本地副本。 */
    public void deleteMail(String addressId, String folder, String uid) {
        MailAddress address = requireImapReady(addressId);
        requireUid(uid);
        String target = resolveFolder(address, folder);
        imap.delete(settings(address), target, uid.trim());
        messages.delete(InboxMessage.documentId(address.id(), target, uid.trim()));
    }

    private void requireUid(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("邮件 UID 不能为空");
        }
    }

    public List<String> folders(String addressId) {
        MailAddress address = requireImapReady(addressId);
        return imap.listFolders(settings(address));
    }

    /** IMAP 连通性测试：连接并打开收件夹，返回邮件总数。 */
    public TransportTestResult testImap(String addressId) {
        MailAddress address = addressService.requireExisting(addressId);
        if (!address.imap().configured()) {
            return TransportTestResult.failed("尚未配置 IMAP 服务器与用户名");
        }
        try {
            int count = imap.testConnection(settings(address), address.imap().folder());
            return TransportTestResult.ok("IMAP 连接成功，收件夹 " + address.imap().folder() + " 共 " + count + " 封邮件", count);
        }
        catch (MailTransportException | IllegalArgumentException e) {
            return TransportTestResult.failed(e.getMessage());
        }
    }

    /**
     * 核验用：按时间窗口与发件域筛出候选邮件，并下载它们的正文（封顶 {@value #MAX_DETAIL_FETCH} 封）。
     * 单封读取失败会被跳过，不影响整体核验结论。
     */
    public List<ImapClient.InboundMailDetail> recentDetails(
            MailAddress address,
            String folder,
            long sinceMillis,
            int summaryLimit) {
        ImapClient.ImapSettings settings = settings(address);
        String target = resolveFolder(address, folder);
        int limit = Math.min(Math.max(summaryLimit, 1), MailAddress.ImapConfig.MAX_FETCH_LIMIT);
        ImapClient.InboundMailPage page = imap.fetchSummaries(settings, target, limit);
        List<ImapClient.InboundMailSummary> candidates = page.records().stream()
                .filter(summary -> summary.sentAt() == 0L || summary.sentAt() >= sinceMillis)
                .filter(summary -> matchesDomains(summary.from(), address.imap().allowedFromDomains()))
                .limit(MAX_DETAIL_FETCH)
                .toList();
        List<ImapClient.InboundMailDetail> details = new ArrayList<>(candidates.size());
        for (ImapClient.InboundMailSummary candidate : candidates) {
            try {
                details.add(imap.fetchDetail(settings, target, candidate.uid()));
            }
            catch (MailTransportException ignored) {
                // 单封读取失败跳过
            }
        }
        return List.copyOf(details);
    }

    /** 收件箱页与核验页共用的默认收件夹解析。 */
    public String resolveFolder(MailAddress address, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return address.imap().folder();
    }

    public MailAddress requireImapReady(String addressId) {
        MailAddress address = addressService.requireExisting(addressId);
        if (!address.imap().configured()) {
            throw new IllegalArgumentException("该邮箱地址尚未配置 IMAP 服务器与用户名：" + address.address());
        }
        return address;
    }

    private ImapClient.ImapSettings settings(MailAddress address) {
        return MailSettingsFactory.imap(address, addressService.requireImapPassword(address));
    }

    /** 信封 upsert：信封字段以服务端为准；已下载的正文、Message-ID 与附件保留。 */
    private void upsertSummaries(String addressId, String folder, List<ImapClient.InboundMailSummary> records) {
        for (ImapClient.InboundMailSummary summary : records) {
            String id = InboxMessage.documentId(addressId, folder, summary.uid());
            InboxMessage message = messages.findById(id)
                    .map(previous -> previous.withEnvelope(summary.subject(), summary.from(), summary.to(),
                            summary.sentAt(), summary.size(), summary.seen()))
                    .orElseGet(() -> InboxMessage.envelope(addressId, folder, summary.uid(), summary.subject(),
                            summary.from(), summary.to(), summary.sentAt(), summary.size(), summary.seen()));
            messages.save(message);
        }
    }

    /** 把 IMAP 详情合并进已有信封副本（信封字段以详情为准）。 */
    private InboxMessage mergeDetail(InboxMessage previous, ImapClient.InboundMailDetail detail) {
        return InboxMessage.fromDetail(previous.addressId(), previous.folder(), detail);
    }

    private boolean matchesEnvelope(InboxMessage message, InboxQuery query) {
        if (query.fromDomain() != null && !query.fromDomain().isBlank()
                && !contains(message.from(), query.fromDomain())) {
            return false;
        }
        String keyword = query.keyword();
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return contains(message.subject(), keyword)
                || contains(message.from(), keyword)
                || message.to().stream().anyMatch(item -> contains(item, keyword));
    }

    private boolean matchesDomains(String from, List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return true;
        }
        return domains.stream().anyMatch(domain -> contains(from, domain));
    }

    private boolean contains(String source, String expected) {
        if (source == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }
}
