package online.yudream.base.plugin.mail.application.service;

import java.util.List;
import java.util.Locale;
import online.yudream.base.plugin.mail.application.PageResult;
import online.yudream.base.plugin.mail.application.cmd.InboundCheckCmd;
import online.yudream.base.plugin.mail.application.port.ImapClient;
import online.yudream.base.plugin.mail.application.port.MailTransportException;
import online.yudream.base.plugin.mail.application.query.InboundCheckQuery;
import online.yudream.base.plugin.mail.domain.aggregate.InboundCheck;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.repo.InboundCheckRepository;
import online.yudream.base.plugin.mail.domain.support.Ids;

/**
 * 入站核验用例：从该地址自己的 IMAP 收件箱拉取时间窗口内的回信，按发件域、关键词与验证码匹配。
 *
 * <p>与旧版本（走宿主入站邮箱能力）不同，这里读的是本插件直连的邮箱，命中时会连同邮件 UID、
 * 主题、发件人与时间一起落审计，管理员可直接去「收件箱」核对。</p>
 */
public class InboundCheckService {

    public static final int DEFAULT_WINDOW_MINUTES = 30;
    public static final int MIN_WINDOW_MINUTES = 1;
    public static final int MAX_WINDOW_MINUTES = 1440;

    private final MailAddressService addressService;
    private final InboxService inboxService;
    private final InboundCheckRepository checks;

    public InboundCheckService(
            MailAddressService addressService,
            InboxService inboxService,
            InboundCheckRepository checks) {
        this.addressService = addressService;
        this.inboxService = inboxService;
        this.checks = checks;
    }

    /** 发起一次核验并落审计记录；任何失败都转成 UNAVAILABLE 结论而不是抛错。 */
    public InboundCheck check(InboundCheckCmd cmd, String operatorUserId) {
        if (cmd == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }
        MailAddress address = addressService.requireExisting(cmd.addressId());
        String code = cmd.verificationCode() == null ? "" : cmd.verificationCode().trim();
        int windowMinutes = normalizeWindow(cmd.windowMinutes());
        String folder = address.imap().folder();
        String status;
        String message;
        ImapClient.InboundMailDetail matched = null;
        if (!address.imap().configured()) {
            status = InboundCheck.UNAVAILABLE;
            message = "该邮箱地址尚未配置 IMAP 服务器与用户名";
        }
        else {
            long since = System.currentTimeMillis() - windowMinutes * 60_000L;
            try {
                List<ImapClient.InboundMailDetail> candidates = inboxService.recentDetails(
                        address, folder, since, address.imap().fetchLimit());
                matched = firstMatch(candidates, address.imap().requiredKeywords(), code);
                if (matched != null) {
                    status = InboundCheck.MATCHED;
                    message = "已匹配到符合条件的回信";
                }
                else {
                    status = InboundCheck.NOT_FOUND;
                    message = candidates.isEmpty()
                            ? "最近 " + windowMinutes + " 分钟内没有符合发件域条件的回信"
                            : "最近 " + windowMinutes + " 分钟内的回信都不匹配关键词或验证码";
                }
            }
            catch (MailTransportException | IllegalArgumentException e) {
                status = InboundCheck.UNAVAILABLE;
                message = e.getMessage() == null ? "读取收件箱失败" : e.getMessage();
            }
        }
        return checks.save(new InboundCheck(Ids.newId(), address.id(), folder, code, status, message,
                matched == null ? "" : matched.uid(),
                matched == null ? "" : matched.subject(),
                matched == null ? "" : matched.from(),
                matched == null ? 0L : matched.sentAt(),
                operatorUserId, 0L));
    }

    public PageResult<InboundCheck> adminPage(InboundCheckQuery query) {
        InboundCheckQuery safeQuery = query == null ? new InboundCheckQuery(null, null, 1, 10) : query;
        List<InboundCheck> matched = checks.findAll().stream()
                .filter(record -> record.matchesAddress(safeQuery.addressId()))
                .filter(record -> record.matchesStatus(safeQuery.status()))
                .toList();
        return PageResult.slice(matched, safeQuery.safePage(), safeQuery.safeSize());
    }

    public long count() {
        return checks.count();
    }

    /** 命中条件：满足全部必需关键词，且（若填了验证码）正文包含该验证码。 */
    private ImapClient.InboundMailDetail firstMatch(
            List<ImapClient.InboundMailDetail> candidates,
            List<String> requiredKeywords,
            String code) {
        for (ImapClient.InboundMailDetail candidate : candidates) {
            String haystack = (candidate.subject() == null ? "" : candidate.subject())
                    + "\n" + (candidate.text() == null ? "" : candidate.text())
                    + "\n" + (candidate.html() == null ? "" : candidate.html());
            if (!containsAll(haystack, requiredKeywords)) {
                continue;
            }
            if (!code.isEmpty() && !containsIgnoringWhitespace(haystack, code)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private boolean containsAll(String haystack, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        String normalized = haystack.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (!normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /** 验证码比较忽略空白与大小写（与旧版宿主能力保持一致的口径）。 */
    private boolean containsIgnoringWhitespace(String haystack, String code) {
        String normalizedHaystack = haystack.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        String normalizedCode = code.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return !normalizedCode.isEmpty() && normalizedHaystack.contains(normalizedCode);
    }

    private int normalizeWindow(Integer windowMinutes) {
        if (windowMinutes == null || windowMinutes <= 0) {
            return DEFAULT_WINDOW_MINUTES;
        }
        if (windowMinutes < MIN_WINDOW_MINUTES || windowMinutes > MAX_WINDOW_MINUTES) {
            throw new IllegalArgumentException(
                    "时间窗口必须在 " + MIN_WINDOW_MINUTES + " - " + MAX_WINDOW_MINUTES + " 分钟之间");
        }
        return windowMinutes;
    }
}
