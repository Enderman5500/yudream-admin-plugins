package online.yudream.base.plugin.yggc.interfaces.request;

public record SignoutRequest(
        String username,
        String password
) {
}
