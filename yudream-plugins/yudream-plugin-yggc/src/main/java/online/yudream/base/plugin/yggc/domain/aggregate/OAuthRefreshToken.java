package online.yudream.base.plugin.yggc.domain.aggregate;

import java.util.List;

/**
 * 刷新令牌，一次性使用（旋转式），与颁发它的访问令牌关联以便联动吊销。
 */
public record OAuthRefreshToken(
        String token,
        String clientId,
        String userId,
        String profileId,
        String accessToken,
        List<String> scopes,
        Long issuedAt,
        Long expiresAt
) {
}
