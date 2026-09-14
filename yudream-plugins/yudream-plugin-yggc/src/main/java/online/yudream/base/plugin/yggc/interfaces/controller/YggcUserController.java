package online.yudream.base.plugin.yggc.interfaces.controller;

import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.yggc.bootstrap.YggcPlugin;
import online.yudream.base.plugin.yggc.interfaces.http.YggcHttpFacade;

/**
 * 用户端点：授权确认 / 设备确认 / 个人授权管理（需登录）。
 */
public class YggcUserController {
    private final YggcHttpFacade http;

    public YggcUserController(YggcHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/authorize", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse authorizeContext(PluginHttpRequest request) {
        return http.authorizeContext(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/authorize", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse authorizeDecision(PluginHttpRequest request) {
        return http.authorizeDecision(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/device", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse deviceContext(PluginHttpRequest request) {
        return http.deviceContext(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/device", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse deviceDecision(PluginHttpRequest request) {
        return http.deviceDecision(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/grants", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse myGrants(PluginHttpRequest request) {
        return http.myGrants(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/me/grants/tokens/{token}", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse revokeMyToken(PluginHttpRequest request) {
        return http.revokeMyToken(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/me/grants/clients/{clientId}", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse revokeMyClient(PluginHttpRequest request) {
        return http.revokeMyClient(request);
    }

    // ---- 用户端：Union 跨站角色 ----

    @PluginHttpEndpoint(method = "GET", path = "/me/union/overview", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse unionOverview(PluginHttpRequest request) {
        return http.unionOverview(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/union/bind", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse unionBind(PluginHttpRequest request) {
        return http.unionBind(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/union/bindto", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse unionBindTo(PluginHttpRequest request) {
        return http.unionBindTo(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/union/unbind", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse unionUnbind(PluginHttpRequest request) {
        return http.unionUnbind(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/union/remapuuid", permission = YggcPlugin.USE_PERMISSION)
    public PluginHttpResponse unionRemapUuid(PluginHttpRequest request) {
        return http.unionRemapUuid(request);
    }
}
