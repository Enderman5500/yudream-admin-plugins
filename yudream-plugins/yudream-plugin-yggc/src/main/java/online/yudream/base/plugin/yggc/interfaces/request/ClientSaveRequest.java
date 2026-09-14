package online.yudream.base.plugin.yggc.interfaces.request;

import java.util.List;

/**
 * 管理员创建 / 更新 OAuth 客户端。
 */
public record ClientSaveRequest(
        String name,
        List<String> redirectUris,
        Boolean publicClient,
        Boolean enabled
) {
}
