package online.yudream.base.plugin.yggc.interfaces.request;

public record TokenRequest(
        String accessToken,
        String clientToken
) {
}
