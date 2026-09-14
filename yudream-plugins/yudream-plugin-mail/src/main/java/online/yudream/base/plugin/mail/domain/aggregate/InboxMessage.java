package online.yudream.base.plugin.mail.domain.aggregate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import online.yudream.base.plugin.mail.domain.support.DocumentValues;

/**
 * 收件箱持久化邮件。
 *
 * <p>收件箱从「每次实时读 IMAP」改为「自动拉取 + 落库」：同步时把 IMAP 信封 upsert 进
 * 文档库（键 = 地址 + 收件夹 + UID），正文只在首次打开详情时下载并缓存（{@code bodyFetched}），
 * 此后列表与详情都读本地副本，IMAP 短暂不可用时仍能浏览历史邮件。</p>
 *
 * <p>同一封邮件以 {@link #documentId} 幂等覆盖：信封字段以服务端为准，
 * 已下载的正文与 Message-ID 保留不丢。</p>
 */
public record InboxMessage(
        String id,
        String addressId,
        String folder,
        String uid,
        String messageId,
        String subject,
        String from,
        List<String> to,
        List<String> cc,
        long sentAt,
        long size,
        boolean seen,
        String text,
        String html,
        boolean truncated,
        boolean bodyFetched,
        List<Attachment> attachments,
        long fetchedAt
) {

    /** 单个地址 + 收件夹最多保留的邮件数（超出按时间最旧淘汰）。 */
    public static final int MAX_PERSISTED_PER_FOLDER = 500;

    public InboxMessage {
        addressId = requireText(addressId, "邮箱地址 ID 不能为空");
        folder = folder == null || folder.isBlank() ? MailAddress.ImapConfig.DEFAULT_FOLDER : folder.trim();
        uid = requireText(uid, "邮件 UID 不能为空");
        messageId = messageId == null ? "" : messageId.trim();
        subject = subject == null ? "" : subject.trim();
        from = from == null ? "" : from.trim();
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        text = text == null ? "" : text;
        html = html == null ? "" : html;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        if (sentAt <= 0) {
            sentAt = System.currentTimeMillis();
        }
        if (fetchedAt <= 0) {
            fetchedAt = System.currentTimeMillis();
        }
    }

    /** 只用信封信息构造（同步阶段，正文未下载）。 */
    public static InboxMessage envelope(String addressId, String folder, String uid, String subject,
                                        String from, List<String> to, long sentAt, long size, boolean seen) {
        return new InboxMessage(documentId(addressId, folder, uid), addressId, folder, uid, "",
                subject, from, to, List.of(), sentAt, size, seen, "", "", false, false, List.of(), 0L);
    }

    /** 由详情构造（正文已下载，带 Message-ID 与附件元数据）。 */
    public static InboxMessage fromDetail(String addressId, String folder, ImapClientDetailSource source) {
        return new InboxMessage(documentId(addressId, folder, source.uid()), addressId, folder, source.uid(),
                source.messageId(), source.subject(), source.from(), source.to(), source.cc(), source.sentAt(),
                source.size(), source.seen(), source.text(), source.html(), source.truncated(), true,
                source.attachments().stream()
                        .map(item -> new Attachment(item.name(), item.contentType(), item.size()))
                        .toList(), 0L);
    }

    /** 详情数据源：直接复用 IMAP 端口的详情记录，避免字段搬运。 */
    public interface ImapClientDetailSource {
        String uid();

        String messageId();

        String subject();

        String from();

        List<String> to();

        List<String> cc();

        long sentAt();

        long size();

        boolean seen();

        String text();

        String html();

        boolean truncated();

        List<? extends AttachmentSource> attachments();

        interface AttachmentSource {
            String name();

            String contentType();

            long size();
        }
    }

    /** 保留已下载正文、以新信封刷新其余字段的副本。 */
    public InboxMessage withEnvelope(String newSubject, String newFrom, List<String> newTo, long newSentAt,
                                     long newSize, boolean newSeen) {
        return new InboxMessage(id, addressId, folder, uid, messageId, newSubject, newFrom, newTo, cc,
                newSentAt, newSize, newSeen, text, html, truncated, bodyFetched, attachments,
                System.currentTimeMillis());
    }

    /** 更新已读标记。 */
    public InboxMessage withSeen(boolean value) {
        return new InboxMessage(id, addressId, folder, uid, messageId, subject, from, to, cc, sentAt, size,
                value, text, html, truncated, bodyFetched, attachments, fetchedAt);
    }

    /** 文档主键：地址 + 收件夹 + UID（UID 在同一收件夹内稳定）。 */
    public static String documentId(String addressId, String folder, String uid) {
        return addressId + "/" + (folder == null || folder.isBlank()
                ? MailAddress.ImapConfig.DEFAULT_FOLDER : folder.trim()) + "/" + uid;
    }

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("addressId", addressId);
        document.put("folder", folder);
        document.put("uid", uid);
        document.put("messageId", messageId);
        document.put("subject", subject);
        document.put("from", from);
        document.put("to", to);
        document.put("cc", cc);
        document.put("sentAt", sentAt);
        document.put("size", size);
        document.put("seen", seen);
        document.put("text", text);
        document.put("html", html);
        document.put("truncated", truncated);
        document.put("bodyFetched", bodyFetched);
        document.put("attachments", attachments.stream().map(Attachment::toDocument).toList());
        document.put("fetchedAt", fetchedAt);
        return document;
    }

    public static InboxMessage from(String id, Map<String, Object> document) {
        List<Map<String, Object>> rawAttachments = DocumentValues.mapList(document, "attachments");
        List<Attachment> attachments = new ArrayList<>();
        for (Map<String, Object> item : rawAttachments) {
            attachments.add(new Attachment(
                    DocumentValues.text(item, "name"),
                    DocumentValues.text(item, "contentType"),
                    DocumentValues.number(item, "size", 0L)));
        }
        return new InboxMessage(
                id,
                DocumentValues.text(document, "addressId"),
                DocumentValues.text(document, "folder"),
                DocumentValues.text(document, "uid"),
                DocumentValues.text(document, "messageId"),
                DocumentValues.text(document, "subject"),
                DocumentValues.text(document, "from"),
                DocumentValues.stringList(document, "to"),
                DocumentValues.stringList(document, "cc"),
                DocumentValues.number(document, "sentAt", 0L),
                DocumentValues.number(document, "size", 0L),
                DocumentValues.bool(document, "seen", false),
                DocumentValues.text(document, "text"),
                DocumentValues.text(document, "html"),
                DocumentValues.bool(document, "truncated", false),
                DocumentValues.bool(document, "bodyFetched", false),
                attachments,
                DocumentValues.number(document, "fetchedAt", 0L));
    }

    /** 附件元数据（不下载内容）。 */
    public record Attachment(String name, String contentType, long size) {

        public Map<String, Object> toDocument() {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("name", name);
            document.put("contentType", contentType);
            document.put("size", size);
            return document;
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
