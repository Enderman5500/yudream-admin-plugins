package online.yudream.base.plugin.mail.interfaces.controller;

import online.yudream.base.plugin.mail.bootstrap.MailPlugin;
import online.yudream.base.plugin.mail.interfaces.http.MailHttpFacade;
import online.yudream.base.plugin.mail.interfaces.support.HttpSupport;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * 用户端接口（/me/**，plugin:mail:use）：以已启用地址自助发信并查看本人发信记录。
 * 归属一律取 principal.userId()，请求体不含任何用户选择字段；管理员走用户端时同样只看到自己的数据。
 */
public class MailUserController {

    private final MailHttpFacade http;

    public MailUserController(MailHttpFacade http) {
        this.http = http;
    }

    /** 可用发件地址：仅启用中的地址，供用户选择。 */
    @PluginHttpEndpoint(method = "GET", path = "/me/senders", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse senders() {
        return http.mySenders();
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/messages", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse send(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.sendMyMail(request));
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/messages", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse messages(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.myMessages(request));
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/messages/{id}", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse message(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.myMessage(request));
    }
}
