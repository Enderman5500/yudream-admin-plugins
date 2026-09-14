package online.yudream.base.plugin.yggc.domain.aggregate;

import java.util.List;

/**
 * OAuth 访问令牌（不透明），可绑定角色。
 *
 * @param token       令牌值，同时是文档 ID
 * @param clientId    颁发给的客户端
 * @param userId      用户 ID
 * @param nickname    颁发时用户昵称（展示用）
 * @param profileId   绑定角色 UUID（申请 Select scope 时）
 * @param profileName 绑定角色名（展示用）
 * @param scopes      授权的 scope 列表
 * @param issuedAt    颁发时间（毫秒）
 * @param expiresAt   过期时间（毫秒）
 */
public record OAuthToken(
        String token,
        String clientId,
        String userId,
        String nickname,
        String profileId,
        String profileName,
        List<String> scopes,
        Long issuedAt,
        Long expiresAt
) {

    public boolean expired(long now) {
        return expiresAt == null || expiresAt <= now;
    }

    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
}
