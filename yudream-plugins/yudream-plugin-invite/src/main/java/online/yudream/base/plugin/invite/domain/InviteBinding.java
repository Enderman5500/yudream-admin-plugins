package online.yudream.base.plugin.invite.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邀请码绑定：一个邀请码全局只能被一个用户绑定；一个用户可绑定多个邀请码。
 * 邀请码由另一个皮肤站生成，本插件只做登记，不校验其有效性。
 */
public record InviteBinding(String id, String code, String ownerId, String remark, long boundAt) {

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("code", code);
        document.put("ownerId", ownerId);
        document.put("remark", remark == null ? "" : remark);
        document.put("boundAt", boundAt);
        return document;
    }

    public static InviteBinding from(String id, Map<String, Object> document) {
        return new InviteBinding(
                id,
                string(document, "code"),
                string(document, "ownerId"),
                string(document, "remark"),
                longValue(document.get("boundAt")));
    }

    static String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            }
            catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
