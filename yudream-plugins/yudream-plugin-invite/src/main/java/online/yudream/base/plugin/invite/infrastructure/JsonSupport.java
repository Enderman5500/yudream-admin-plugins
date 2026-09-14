package online.yudream.base.plugin.invite.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;

/** 请求体 JSON 解析小工具（控制器→应用层入参转换）。 */
public final class JsonSupport {
    private final ObjectMapper mapper;

    public JsonSupport(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public <T> T read(PluginHttpRequest request, Class<T> type) {
        return read(request.body(), type);
    }

    public <T> T read(Object body, Class<T> type) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        try {
            if (body instanceof String text) {
                if (text.isBlank()) {
                    throw new IllegalArgumentException("请求体不能为空");
                }
                return mapper.readValue(text, type);
            }
            return mapper.convertValue(body, type);
        }
        catch (IllegalArgumentException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalArgumentException("请求体格式不正确：" + e.getMessage());
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
