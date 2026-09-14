package online.yudream.base.plugin.yggc.interfaces.controller;

import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.yggc.interfaces.http.YggcHttpFacade;

/**
 * Union 成员回调端点：由 Union 主服务器调用（无用户会话，靠主机签名验证）。
 * 对应原插件 /api/union/member/* 路由组。
 */
public class YggcUnionController {

    private final YggcHttpFacade http;

    public YggcUnionController(YggcHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/union/member", wrapResult = false)
    public PluginHttpResponse hello(PluginHttpRequest request) {
        return http.unionMemberHello(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/union/member/updatelist", wrapResult = false)
    public PluginHttpResponse updateList(PluginHttpRequest request) {
        return http.unionMemberUpdateList(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/union/member/updateprivatekey", wrapResult = false)
    public PluginHttpResponse updatePrivateKey(PluginHttpRequest request) {
        return http.unionMemberUpdatePrivateKey(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/union/member/updatebackendkey", wrapResult = false)
    public PluginHttpResponse updateBackendKey(PluginHttpRequest request) {
        return http.unionMemberUpdateBackendKey(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/union/member/sync", wrapResult = false)
    public PluginHttpResponse sync(PluginHttpRequest request) {
        return http.unionMemberSync(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/union/member/remapuuid", wrapResult = false)
    public PluginHttpResponse remapUuid(PluginHttpRequest request) {
        return http.unionMemberRemapUuid(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/union/member/queryemail", wrapResult = false)
    public PluginHttpResponse queryEmail(PluginHttpRequest request) {
        return http.unionMemberQueryEmail(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/union/member/diagnose", wrapResult = false)
    public PluginHttpResponse diagnose(PluginHttpRequest request) {
        return http.unionMemberDiagnose(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/union/member/updateplugin", wrapResult = false)
    public PluginHttpResponse updatePlugin(PluginHttpRequest request) {
        return http.unionMemberUpdatePlugin(request);
    }

    // ---- Union OAuth2（主服务器经本站登录）----

    @PluginHttpEndpoint(method = "GET", path = "/union/member/oauth2", wrapResult = false)
    public PluginHttpResponse oauth2PublicKey(PluginHttpRequest request) {
        return http.unionOauth2PublicKey(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/union/member/oauth2/grant", wrapResult = false)
    public PluginHttpResponse oauth2Grant(PluginHttpRequest request) {
        return http.unionOauth2Grant(request);
    }
}
