package online.yudream.base.plugin.yggc.domain.aggregate;

import java.util.List;

/**
 * OAuth 2.0 客户端（应用）注册信息。
 *
 * @param id           客户端标识符，全局唯一
 * @param name         应用名称，展示给用户
 * @param secretHash   client_secret 的 SHA-256 十六进制摘要；公共客户端为 null
 * @param redirectUris 已注册回调地址，精确匹配
 * @param publicClient 是否为公共客户端（设备流 / PKCE，无 client_secret）
 * @param enabled      是否启用
 * @param createdAt    创建时间（毫秒）
 */
public record OAuthClient(
        String id,
        String name,
        String secretHash,
        List<String> redirectUris,
        boolean publicClient,
        boolean enabled,
        Long createdAt
) {
}
