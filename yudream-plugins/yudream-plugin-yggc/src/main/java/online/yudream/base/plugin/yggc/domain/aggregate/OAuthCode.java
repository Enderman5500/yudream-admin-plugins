package online.yudream.base.plugin.yggc.domain.aggregate;

import java.util.List;

/**
 * 授权码（Authorization Code），一次性、短时效。
 */
public record OAuthCode(
        String code,
        String clientId,
        String userId,
        String profileId,
        List<String> scopes,
        String redirectUri,
        String codeChallenge,
        String challengeMethod,
        String nonce,
        Long expiresAt
) {
}
