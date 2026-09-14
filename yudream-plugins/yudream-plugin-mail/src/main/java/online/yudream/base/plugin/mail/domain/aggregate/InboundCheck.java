package online.yudream.base.plugin.mail.domain.aggregate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import online.yudream.base.plugin.mail.domain.support.DocumentValues;

/**
 * 入站核验记录（审计日志，只读）。
 *
 * <p>核验由插件自建 IMAP 客户端完成：拉取指定收件夹最近的回信，按发件域、关键词与验证码匹配。
 * 结论只有 {@link #MATCHED} / {@link #NOT_FOUND} / {@link #UNAVAILABLE}；验证码落库前脱敏。</p>
 */
public record InboundCheck(
        String id,
        String addressId,
        String folder,
        String codeMask,
        String status,
        String message,
        String matchedUid,
        String matchedSubject,
        String matchedFrom,
        long matchedAt,
        String operatorUserId,
        long createdAt
) {

    public static final String MATCHED = "MATCHED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    /** 旧版本（走宿主入站邮箱）产生过的状态，仅用于读取历史记录。 */
    public static final String LEGACY_PENDING = "PENDING";

    private static final Set<String> KNOWN_STATUSES = Set.of(MATCHED, NOT_FOUND, UNAVAILABLE, LEGACY_PENDING);

    public InboundCheck {
        id = requireText(id, "核验记录 ID 不能为空");
        addressId = requireText(addressId, "邮箱地址 ID 不能为空");
        folder = folder == null || folder.isBlank() ? MailAddress.ImapConfig.DEFAULT_FOLDER : folder.trim();
        codeMask = maskCode(codeMask);
        status = normalizeStatus(status);
        message = message == null ? "" : message.trim();
        matchedUid = matchedUid == null ? "" : matchedUid.trim();
        matchedSubject = matchedSubject == null ? "" : matchedSubject.trim();
        matchedFrom = matchedFrom == null ? "" : matchedFrom.trim();
        operatorUserId = operatorUserId == null ? "" : operatorUserId.trim();
        if (createdAt <= 0) {
            createdAt = System.currentTimeMillis();
        }
    }

    /** 未知状态统一归为不可用，避免前端拿到无法渲染的取值。 */
    private static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNAVAILABLE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return KNOWN_STATUSES.contains(normalized) ? normalized : UNAVAILABLE;
    }

    /** 验证码脱敏：保留首尾字符，中间以 * 代替；空值返回空串。 */
    public static String maskCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String trimmed = code.trim();
        if (trimmed.length() <= 2) {
            return "*".repeat(trimmed.length());
        }
        return trimmed.substring(0, 1) + "*".repeat(trimmed.length() - 2) + trimmed.substring(trimmed.length() - 1);
    }

    public static String label(String status) {
        return switch (normalizeStatus(status)) {
            case MATCHED -> "核验通过";
            case LEGACY_PENDING -> "等待回信";
            case NOT_FOUND -> "未匹配";
            default -> "不可用";
        };
    }

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("addressId", addressId);
        document.put("folder", folder);
        document.put("codeMask", codeMask);
        document.put("status", status);
        document.put("message", message);
        document.put("matchedUid", matchedUid);
        document.put("matchedSubject", matchedSubject);
        document.put("matchedFrom", matchedFrom);
        document.put("matchedAt", matchedAt);
        document.put("operatorUserId", operatorUserId);
        document.put("createdAt", createdAt);
        return document;
    }

    public static InboundCheck from(String id, Map<String, Object> document) {
        return new InboundCheck(
                id,
                DocumentValues.text(document, "addressId"),
                DocumentValues.text(document, "folder"),
                DocumentValues.text(document, "codeMask"),
                DocumentValues.text(document, "status"),
                DocumentValues.text(document, "message"),
                DocumentValues.text(document, "matchedUid"),
                DocumentValues.text(document, "matchedSubject"),
                DocumentValues.text(document, "matchedFrom"),
                DocumentValues.number(document, "matchedAt", 0L),
                DocumentValues.text(document, "operatorUserId"),
                DocumentValues.number(document, "createdAt", 0L));
    }

    public boolean matchesAddress(String addressFilter) {
        return addressFilter == null || addressFilter.isBlank() || addressFilter.equals(addressId);
    }

    public boolean matchesStatus(String statusFilter) {
        return statusFilter == null || statusFilter.isBlank() || statusFilter.equalsIgnoreCase(status);
    }

    public static List<String> knownStatuses() {
        return List.of(MATCHED, NOT_FOUND, UNAVAILABLE);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
