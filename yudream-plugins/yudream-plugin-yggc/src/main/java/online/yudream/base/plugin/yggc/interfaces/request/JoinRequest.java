package online.yudream.base.plugin.yggc.interfaces.request;

public record JoinRequest(
        String accessToken,
        String selectedProfile,
        String serverId
) {
}
