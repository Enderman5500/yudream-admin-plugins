package online.yudream.base.plugin.mail.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import online.yudream.base.plugin.mail.application.port.ImapClient;
import online.yudream.base.plugin.mail.application.port.MailTransportException;

/** IMAP 端口的内存假实现：可预设信封列表、正文、收件夹列表与失败。 */
public final class FakeImapClient implements ImapClient {

    private final List<InboundMailSummary> summaries = new ArrayList<>();
    private final Map<String, InboundMailDetail> details = new LinkedHashMap<>();
    private List<String> folders = List.of("INBOX", "Archive");
    private int mailboxTotal;
    private RuntimeException failure;

    private String lastFolder;
    private ImapSettings lastSettings;
    private int detailFetchCount;
    private final Map<String, Boolean> seenUpdates = new LinkedHashMap<>();
    private final List<String> deletedUids = new ArrayList<>();

    public FakeImapClient withSummaries(List<InboundMailSummary> value, int total) {
        summaries.clear();
        summaries.addAll(value);
        mailboxTotal = total;
        return this;
    }

    public FakeImapClient withDetail(String uid, InboundMailDetail value) {
        details.put(uid, value);
        return this;
    }

    public FakeImapClient withFolders(List<String> value) {
        folders = value;
        return this;
    }

    public FakeImapClient failWith(RuntimeException value) {
        failure = value;
        return this;
    }

    public String lastFolder() {
        return lastFolder;
    }

    public ImapSettings lastSettings() {
        return lastSettings;
    }

    /** 正文被下载的次数，用于断言“列表查询不下载正文”。 */
    public int detailFetchCount() {
        return detailFetchCount;
    }

    /** uid → 已读标记目标值，按调用顺序记录。 */
    public Map<String, Boolean> seenUpdates() {
        return seenUpdates;
    }

    public List<String> deletedUids() {
        return deletedUids;
    }

    @Override
    public int testConnection(ImapSettings settings, String folder) {
        failIfNeeded();
        lastSettings = settings;
        lastFolder = folder;
        return mailboxTotal;
    }

    @Override
    public List<String> listFolders(ImapSettings settings) {
        failIfNeeded();
        lastSettings = settings;
        return folders;
    }

    @Override
    public InboundMailPage fetchSummaries(ImapSettings settings, String folder, int limit) {
        failIfNeeded();
        lastSettings = settings;
        lastFolder = folder;
        List<InboundMailSummary> window = summaries.size() <= limit
                ? List.copyOf(summaries)
                : List.copyOf(summaries.subList(0, limit));
        return new InboundMailPage(window, Math.max(mailboxTotal, summaries.size()));
    }

    @Override
    public InboundMailDetail fetchDetail(ImapSettings settings, String folder, String uid) {
        failIfNeeded();
        lastSettings = settings;
        lastFolder = folder;
        detailFetchCount++;
        InboundMailDetail detail = details.get(uid);
        if (detail == null) {
            throw new MailTransportException("邮件不存在或已被删除（UID " + uid + "）");
        }
        return detail;
    }

    @Override
    public void markSeen(ImapSettings settings, String folder, String uid, boolean seen) {
        failIfNeeded();
        lastSettings = settings;
        lastFolder = folder;
        seenUpdates.put(uid, seen);
    }

    @Override
    public void delete(ImapSettings settings, String folder, String uid) {
        failIfNeeded();
        lastSettings = settings;
        lastFolder = folder;
        deletedUids.add(uid);
        summaries.removeIf(summary -> summary.uid().equals(uid));
        details.remove(uid);
    }

    /** 便捷构造信封。 */
    public static InboundMailSummary summary(String uid, String subject, String from, long sentAt) {
        return new InboundMailSummary(uid, subject, from, List.of("ops@example.com"), sentAt, 2048L, false);
    }

    /** 便捷构造正文。 */
    public static InboundMailDetail detail(String uid, String subject, String from, long sentAt, String text) {
        return detail(uid, subject, from, sentAt, text, "<msg-" + uid + "@example.com>");
    }

    /** 便捷构造正文（可指定 Message-ID）。 */
    public static InboundMailDetail detail(String uid, String subject, String from, long sentAt, String text,
                                           String messageId) {
        return new InboundMailDetail(uid, subject, from, List.of("ops@example.com"), List.of(), sentAt, 4096L,
                false, text, null, List.of(), false, messageId);
    }

    private void failIfNeeded() {
        if (failure != null) {
            throw failure;
        }
    }
}
