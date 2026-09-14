package online.yudream.base.plugin.yggc.interfaces.controller;

import online.yudream.base.plugin.yggc.interfaces.http.YggcHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * Yggdrasil Connect（OAuth 2.0 / OIDC）协议端点，对启动器开放。
 * 与传统协议同根，均挂在 /api/yggdrasil 前缀下（OIDC discovery 地址
 * 为 {apiRoot}/.well-known/openid-configuration）。
 */
public class YggcOAuthController {
    private final YggcHttpFacade http;

    public YggcOAuthController(YggcHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/.well-known/openid-configuration", wrapResult = false)
    public PluginHttpResponse discovery(PluginHttpRequest request) { return http.discovery(request); }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/.well-known/jwks.json", wrapResult = false)
    public PluginHttpResponse jwks(PluginHttpRequest request) { return http.jwks(request); }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/oauth/authorize", wrapResult = false)
    public PluginHttpResponse authorize(PluginHttpRequest request) { return http.authorize(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/oauth/token", wrapResult = false)
    public PluginHttpResponse token(PluginHttpRequest request) { return http.token(request); }

    @PluginHttpEndpoint(method = "POST", path = "/api/yggdrasil/oauth/device", wrapResult = false)
    public PluginHttpResponse device(PluginHttpRequest request) { return http.startDevice(request); }

    @PluginHttpEndpoint(method = "GET", path = "/api/yggdrasil/userinfo", wrapResult = false)
    public PluginHttpResponse userInfo(PluginHttpRequest request) { return http.userInfo(request); }
}
