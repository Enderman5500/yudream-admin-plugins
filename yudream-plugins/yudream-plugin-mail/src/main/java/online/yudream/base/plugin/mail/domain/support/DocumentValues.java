package online.yudream.base.plugin.mail.domain.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档读写取值工具：宿主文档存储返回 {@code Map<String, Object>}，Mongo 反序列化后
 * 数字/布尔/列表的具体实现类不固定，这里统一做宽松转换。
 */
public final class DocumentValues {

    private DocumentValues() {
    }

    /** 文本值，缺失返回空串。 */
    public static String text(Map<String, Object> document, String key) {
        Object value = document == null ? null : document.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /** 文本值，缺失或空白返回 null。 */
    public static String textOrNull(Map<String, Object> document, String key) {
        String value = text(document, key).trim();
        return value.isEmpty() ? null : value;
    }

    public static boolean bool(Map<String, Object> document, String key, boolean fallback) {
        Object value = document == null ? null : document.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if ("true".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    public static long number(Map<String, Object> document, String key, long fallback) {
        Object value = document == null ? null : document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            }
            catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static int intNumber(Map<String, Object> document, String key, int fallback) {
        long value = number(document, key, fallback);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    /** 取嵌套文档（Mongo 子文档反序列化为 Map）。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> document, String key) {
        Object value = document == null ? null : document.get(key);
        return value instanceof Map<?, ?> nested ? (Map<String, Object>) nested : null;
    }

    /** 取嵌套文档列表（附件等子文档集合；非 Map 元素跳过）。 */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> mapList(Map<String, Object> document, String key) {
        Object value = document == null ? null : document.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> nested) {
                result.add((Map<String, Object>) nested);
            }
        }
        return result;
    }

    /** 字符串列表：支持原生 List（Mongo 存储）与逗号/换行分隔的字符串（兼容旧数据）。 */
    public static List<String> stringList(Map<String, Object> document, String key) {
        Object value = document == null ? null : document.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = String.valueOf(item).trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            return result;
        }
        if (value instanceof String text && !text.isBlank()) {
            List<String> result = new ArrayList<>();
            for (String item : text.split("[,\\n]")) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
        return List.of();
    }
}
