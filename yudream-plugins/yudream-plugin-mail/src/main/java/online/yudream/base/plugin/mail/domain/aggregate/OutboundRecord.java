package online.yudream.base.plugin.mail.domain.aggregate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import online.yudream.base.plugin.mail.domain.support.DocumentValues;

/**
 * 发信记录（审计日志，只读，不提供删除）。
 *
 * <p>发信由插件自建 SMTP 客户端同步完成，因此状态是**真实结果**：
 * {@link Status#SENT} 表示 SMTP 服务器已接收（仍不等于对方已读），{@link Status#FAILED} 表示连接、
 * 认证或投递阶段报错，错误原因原样落入 {@code errorMessage}。</p>
 */
public record OutboundRecord(
        String id,
        String addressId,
        String fromAddress,
        String smtpHost,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        BodyType bodyType,
        String body,
        String bodyPreview,
        Status status,
        String errorMessage,
        Source source,
        String operatorUserId,
        long createdAt
) {

    public static final int SUBJECT_MAX_LENGTH = 200;
    public static final int BODY_MAX_LENGTH = 20000;
    public static final int PREVIEW_LENGTH = 200;

    /** 投递结果。SENT 代表 SMTP 服务器已接收，不代表对方已读。 */
    public enum Status {
        SENT("已发送"),
        FAILED("发送失败");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 发起方：管理端统一发信或用户自助发信。 */
    public enum Source {
        ADMIN("管理端"),
        USER("用户端");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum BodyType {
        TEXT("纯文本"),
        HTML("HTML");

        private final String label;

        BodyType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public OutboundRecord {
        id = requireText(id, "发信记录 ID 不能为空");
        addressId = requireText(addressId, "邮箱地址 ID 不能为空");
        fromAddress = requireText(fromAddress, "发件地址不能为空");
        smtpHost = smtpHost == null ? "" : smtpHost.trim();
        to = MailAddress.normalizeRecipients(to, "收件人");
        cc = MailAddress.normalizeRecipients(cc, "抄送");
        bcc = MailAddress.normalizeRecipients(bcc, "密送");
        if (to.isEmpty()) {
            throw new IllegalArgumentException("收件人不能为空");
        }
        subject = requireText(subject, "邮件主题不能为空");
        requireMax(subject, SUBJECT_MAX_LENGTH, "邮件主题");
        bodyType = bodyType == null ? BodyType.TEXT : bodyType;
        body = body == null ? "" : body;
        requireMax(body, BODY_MAX_LENGTH, "邮件正文");
        if (body.trim().isEmpty()) {
            throw new IllegalArgumentException("邮件正文不能为空");
        }
        bodyPreview = preview(body);
        status = status == null ? Status.SENT : status;
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
        source = source == null ? Source.ADMIN : source;
        operatorUserId = operatorUserId == null ? "" : operatorUserId.trim();
        if (createdAt <= 0) {
            createdAt = System.currentTimeMillis();
        }
    }

    /** 记录一次投递失败（保留已校验的字段，只改写状态与错误文案）。 */
    public OutboundRecord withFailure(String failureMessage) {
        return new OutboundRecord(id, addressId, fromAddress, smtpHost, to, cc, bcc, subject, bodyType, body,
                bodyPreview, Status.FAILED, failureMessage == null ? "SMTP 投递失败" : failureMessage,
                source, operatorUserId, createdAt);
    }

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("addressId", addressId);
        document.put("fromAddress", fromAddress);
        document.put("smtpHost", smtpHost);
        document.put("to", to);
        document.put("cc", cc);
        document.put("bcc", bcc);
        document.put("subject", subject);
        document.put("bodyType", bodyType.name());
        document.put("body", body);
        document.put("bodyPreview", bodyPreview);
        document.put("status", status.name());
        document.put("errorMessage", errorMessage);
        document.put("source", source.name());
        document.put("operatorUserId", operatorUserId);
        document.put("createdAt", createdAt);
        return document;
    }

    public static OutboundRecord from(String id, Map<String, Object> document) {
        return new OutboundRecord(
                id,
                DocumentValues.text(document, "addressId"),
                DocumentValues.text(document, "fromAddress"),
                DocumentValues.text(document, "smtpHost"),
                DocumentValues.stringList(document, "to"),
                DocumentValues.stringList(document, "cc"),
                DocumentValues.stringList(document, "bcc"),
                DocumentValues.text(document, "subject"),
                bodyTypeOf(DocumentValues.text(document, "bodyType")),
                DocumentValues.text(document, "body"),
                DocumentValues.text(document, "bodyPreview"),
                statusOf(DocumentValues.text(document, "status")),
                DocumentValues.text(document, "errorMessage"),
                sourceOf(DocumentValues.text(document, "source")),
                DocumentValues.text(document, "operatorUserId"),
                DocumentValues.number(document, "createdAt", 0L));
    }

    private static BodyType bodyTypeOf(String raw) {
        try {
            return BodyType.valueOf(raw == null || raw.isBlank() ? "TEXT" : raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return BodyType.TEXT;
        }
    }

    /** 旧版本记录的状态是 SUBMITTED（走宿主异步投递），读取时统一按已发送展示。 */
    private static Status statusOf(String raw) {
        if (raw == null || raw.isBlank() || "SUBMITTED".equalsIgnoreCase(raw.trim())) {
            return Status.SENT;
        }
        try {
            return Status.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return Status.SENT;
        }
    }

    private static Source sourceOf(String raw) {
        try {
            return Source.valueOf(raw == null || raw.isBlank() ? "ADMIN" : raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return Source.ADMIN;
        }
    }

    /** 支持以逗号/分号/换行批量粘贴收件人。 */
    public static List<String> splitRecipients(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new java.util.ArrayList<>();
        for (String item : raw.split("[;,\\s\\n]+")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private static String preview(String body) {
        String flattened = body.replaceAll("\\s+", " ").trim();
        return flattened.length() <= PREVIEW_LENGTH ? flattened : flattened.substring(0, PREVIEW_LENGTH) + "…";
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static void requireMax(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(name + "不能超过 " + maxLength + " 个字符");
        }
    }
}
