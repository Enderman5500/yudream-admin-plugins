package online.yudream.base.plugin.yggc.infrastructure.support;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth 令牌端点使用 application/x-www-form-urlencoded，请求体可能不是 JSON。
 */
public class FormSupport {

    private FormSupport() {
    }

    public static boolean isForm(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded");
    }

    public static Map<String, String> parse(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return result;
        }
        for (String pair : body.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int index = pair.indexOf('=');
            String key = index > -1 ? pair.substring(0, index) : pair;
            String value = index > -1 ? pair.substring(index + 1) : "";
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
