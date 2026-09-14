package online.yudream.base.plugin.mail.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;

/** 请求体 JSON 解析小工具（控制器 → 应用层入参转换）。 */
public final class JsonSupport {

    private final ObjectMapper mapper;

    public JsonSupport(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public <T> T read(PluginHttpRequest request, Class<T> type) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String body = request.body();
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        try {
            return mapper.readValue(body, type);
        }
        catch (IllegalArgumentException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalArgumentException("请求体格式不正确：" + e.getMessage());
        }
    }

    public <T> T readOrNull(PluginHttpRequest request, Class<T> type) {
        if (request == null || request.body() == null || request.body().isBlank()) {
            return null;
        }
        return read(request, type);
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
