package online.yudream.base.plugin.mail.interfaces.controller;

import online.yudream.base.plugin.mail.bootstrap.MailPlugin;
import online.yudream.base.plugin.mail.interfaces.http.MailHttpFacade;
import online.yudream.base.plugin.mail.interfaces.support.HttpSupport;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/**
 * 收件箱接口（/inbox/**，plugin:mail:use）。
 *
 * <p>收件箱面向插件的共享邮箱地址（由管理员统一配置），持有使用权限的用户都可浏览、
 * 回复/转发、标记已读与删除——读写的是这些地址的邮箱，不是用户个人数据，
 * 因此放在中立路径 {@code /inbox/**} 而非 {@code /me/**}。</p>
 */
public class MailInboxController {

    private final MailHttpFacade http;

    public MailInboxController(MailHttpFacade http) {
        this.http = http;
    }

    /** 地址选项（仅启用地址，不含敏感配置字段）：收件箱与回复/转发的发件地址来源。 */
    @PluginHttpEndpoint(method = "GET", path = "/inbox/address-options", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse inboxAddressOptions(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.inboxAddressOptions());
    }

    /** 同步 + 查询：先从 IMAP 拉取最新信封落库，再返回持久化副本的过滤分页结果。 */
    @PluginHttpEndpoint(method = "GET", path = "/inbox", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse inbox(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.inbox(request));
    }

    /** 邮件详情：优先返回缓存正文，否则从 IMAP 下载并缓存。 */
    @PluginHttpEndpoint(method = "GET", path = "/inbox/messages/{uid}", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse inboxMessage(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.inboxMessage(request));
    }

    /** 标记/取消标记已读：?uid=&addressId=&folder=&seen=true|false。 */
    @PluginHttpEndpoint(method = "POST", path = "/inbox/seen", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse inboxMarkSeen(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.inboxMarkSeen(request));
    }

    /** 删除邮件：?addressId=&folder=，UID 取路径段；服务端永久移除。 */
    @PluginHttpEndpoint(method = "DELETE", path = "/inbox/messages/{uid}", permission = MailPlugin.USE_PERMISSION)
    public PluginHttpResponse inboxDelete(PluginHttpRequest request) {
        return HttpSupport.guard(() -> http.inboxDelete(request));
    }
}
