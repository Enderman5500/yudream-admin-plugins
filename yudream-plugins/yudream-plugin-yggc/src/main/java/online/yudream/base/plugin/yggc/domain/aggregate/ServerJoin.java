package online.yudream.base.plugin.yggc.domain.aggregate;

public record ServerJoin(
        String id,
        String serverId,
        String profileId,
        String username,
        String accessToken,
        Long expiresAt
) {
}
