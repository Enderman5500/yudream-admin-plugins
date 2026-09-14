package online.yudream.base.plugin.mail.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.mail.application.port.ImapClient;
import online.yudream.base.plugin.mail.application.port.SmtpClient;
import online.yudream.base.plugin.mail.application.service.InboundCheckService;
import online.yudream.base.plugin.mail.application.service.InboxService;
import online.yudream.base.plugin.mail.application.service.MailAddressService;
import online.yudream.base.plugin.mail.application.service.OutboundMailService;
import online.yudream.base.plugin.mail.infrastructure.JsonSupport;
import online.yudream.base.plugin.mail.infrastructure.mail.JakartaImapClient;
import online.yudream.base.plugin.mail.infrastructure.mail.JakartaSmtpClient;
import online.yudream.base.plugin.mail.infrastructure.repository.InboundCheckDocumentRepository;
import online.yudream.base.plugin.mail.infrastructure.repository.InboxMessageDocumentRepository;
import online.yudream.base.plugin.mail.infrastructure.repository.MailAddressDocumentRepository;
import online.yudream.base.plugin.mail.infrastructure.repository.OutboundRecordDocumentRepository;
import online.yudream.base.plugin.mail.interfaces.controller.MailAdminController;
import online.yudream.base.plugin.mail.interfaces.controller.MailInboxController;
import online.yudream.base.plugin.mail.interfaces.controller.MailUserController;
import online.yudream.base.plugin.mail.interfaces.http.MailHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;

/**
 * 邮箱中心插件：多邮箱地址配置 + 插件自建 SMTP/IMAP 收发信。
 *
 * <p>与宿主能力的关系：发信与收信都由本插件自带的 Jakarta Mail（ANGUS）客户端直连邮箱完成，
 * <b>不使用</b>宿主邮件配置与「入站邮箱」平台能力：每个地址独立配置收发服务器、加密方式、用户名，
 * 密码由 {@code PluginSecretStore} 加密落库（宿主不回传，插件也不在任何响应里返回）。
 * 因此宿主未配置 SMTP / 未启用入站邮箱都不影响本插件，页面只需提示“该地址未配置 SMTP/IMAP”。</p>
 */
@PluginSpec(
        code = MailPlugin.CODE,
        name = "邮箱中心",
        version = MailPlugin.VERSION,
        description = "配置多个邮箱地址，用插件自带的 SMTP/IMAP 客户端收发邮件，支持收件箱浏览与入站核验。")
@PluginPermissions({
        @PluginPermission(code = MailPlugin.USE_PERMISSION, name = "使用邮箱中心", module = "邮箱中心",
                description = "以已启用邮箱地址发信、浏览收件箱（回复/转发/已读/删除）并查看发信记录"),
        @PluginPermission(code = MailPlugin.MANAGE_PERMISSION, name = "管理邮箱中心", module = "邮箱中心",
                description = "管理邮箱地址与收发配置、连通性测试、查看全部用户的发信记录与入站核验")
})
@PluginFrontend(
        moduleName = "mail",
        menuTitle = "邮箱中心",
        menuIcon = "i-ri:mail-send-line",
        menuSort = 60,
        styles = {"style.css"},
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/mail/admin/addresses",
                        name = "platform-plugin-mail-admin-addresses",
                        title = "邮箱地址",
                        icon = "i-ri:at-line",
                        component = "mail/AdminAddresses",
                        permission = MailPlugin.MANAGE_PERMISSION,
                        sort = 10
                ),
                @PluginRoute(
                        path = "/platform/plugins/mail/send",
                        name = "platform-plugin-mail-send",
                        title = "发送邮件",
                        icon = "i-ri:mail-send-line",
                        component = "mail/AdminSend",
                        permission = MailPlugin.USE_PERMISSION,
                        sort = 20
                ),
                @PluginRoute(
                        path = "/platform/plugins/mail/inbox",
                        name = "platform-plugin-mail-inbox",
                        title = "收件箱",
                        icon = "i-ri:inbox-2-line",
                        component = "mail/AdminInbox",
                        permission = MailPlugin.USE_PERMISSION,
                        sort = 30
                ),
                @PluginRoute(
                        path = "/platform/plugins/mail/messages",
                        name = "platform-plugin-mail-messages",
                        title = "发信记录",
                        icon = "i-ri:history-line",
                        component = "mail/AdminMessages",
                        permission = MailPlugin.USE_PERMISSION,
                        sort = 40
                ),
                @PluginRoute(
                        path = "/platform/plugins/mail/admin/inbound-checks",
                        name = "platform-plugin-mail-admin-inbound-checks",
                        title = "入站核验",
                        icon = "i-ri:mail-check-line",
                        component = "mail/AdminInboundChecks",
                        permission = MailPlugin.MANAGE_PERMISSION,
                        sort = 50
                )
        }
)
public class MailPlugin implements YuDreamPlugin {

    public static final String CODE = "mail";
    public static final String VERSION = "1.3.1";
    public static final String USE_PERMISSION = "plugin:mail:use";
    public static final String MANAGE_PERMISSION = "plugin:mail:manage";

    @Override
    public void onEnable(PluginContext context) {
        JsonSupport json = new JsonSupport(new ObjectMapper());

        MailAddressDocumentRepository addresses = new MailAddressDocumentRepository(context.documents());
        OutboundRecordDocumentRepository outboundRecords = new OutboundRecordDocumentRepository(context.documents());
        InboundCheckDocumentRepository inboundChecks = new InboundCheckDocumentRepository(context.documents());
        InboxMessageDocumentRepository inboxMessages = new InboxMessageDocumentRepository(context.documents());

        MailAddressService addressService = new MailAddressService(addresses, context.secrets());
        SmtpClient smtpClient = new JakartaSmtpClient();
        ImapClient imapClient = new JakartaImapClient();

        OutboundMailService outboundService = new OutboundMailService(addressService, outboundRecords, smtpClient);
        InboxService inboxService = new InboxService(addressService, imapClient, inboxMessages);
        InboundCheckService checkService = new InboundCheckService(addressService, inboxService, inboundChecks);

        MailHttpFacade http = new MailHttpFacade(addressService, outboundService, inboxService, checkService,
                context.framework(), json);
        context.registerHttpController(new MailAdminController(http));
        context.registerHttpController(new MailInboxController(http));
        context.registerHttpController(new MailUserController(http));
    }
}
