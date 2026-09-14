package online.yudream.base.plugin.mail.interfaces.controller;

import online.yudream.base.plugin.mail.bootstrap.MailPlugin;
import online.yudream.base.plugin.mail.interfaces.http.MailHttpFacade;
import online.yudream.base.plugin.mail.interfaces.support.HttpSupport;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * 管理端接口（/admin/**，plugin:mail:manage）：
 * 邮箱地址与 SMTP/IMAP 配置、连通性测试、收件夹列表、全部发信记录、入站核验与管理端发信。
 */
public class MailAdminController {

    private final MailHttpFacade http;

    public MailAdminController(MailHttpFacade http) {
        this.http = http;
    }

    // -------- 邮箱地址 --------

    @PluginHttpEndpoint(method = "GET", path = "/admin/addresses", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse addresses(PluginHttpRequest request) {
        return http.addresses(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/addresses/{id}", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse address(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.address(request));
    }

    /** 地址选择器数据源（含启用状态与收发配置完整度）。 */
    @PluginHttpEndpoint(method = "GET", path = "/admin/address-options", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse addressOptions() {
        return http.addressOptions();
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/addresses", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse createAddress(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.createAddress(request));
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/addresses/{id}", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse updateAddress(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.updateAddress(request));
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/addresses/{id}/enabled", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse setAddressEnabled(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.setAddressEnabled(request));
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/addresses/{id}/default", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse setDefaultAddress(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.setDefaultAddress(request));
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/addresses/{id}", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteAddress(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.deleteAddress(request));
    }

    /** IMAP 收件夹列表（真实目录，供地址配置里选择）。 */
    @PluginHttpEndpoint(method = "GET", path = "/admin/addresses/{id}/imap-folders", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse imapFolders(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.imapFolders(request));
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/addresses/{id}/test/smtp", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse testSmtp(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.testSmtp(request));
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/addresses/{id}/test/imap", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse testImap(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.testImap(request));
    }

    // -------- 发信与发信记录 --------

    @PluginHttpEndpoint(method = "POST", path = "/admin/messages", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse sendMail(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.sendMail(request));
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/messages", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse messages(PluginHttpRequest request) {
        return http.messages(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/messages/{id}", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse message(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.message(request));
    }

    // -------- 入站核验 --------

    @PluginHttpEndpoint(method = "POST", path = "/admin/inbound-checks", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse checkInbound(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.checkInbound(request));
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/inbound-checks", permission = MailPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse inboundChecks(PluginHttpRequest request) {
        return http.inboundChecks(request);
    }
}
