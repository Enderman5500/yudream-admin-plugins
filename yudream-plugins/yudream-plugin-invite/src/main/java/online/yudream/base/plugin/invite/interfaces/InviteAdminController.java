package online.yudream.base.plugin.invite.interfaces;

import java.util.Map;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.invite.application.InviteBindingService;
import online.yudream.base.plugin.invite.application.PageResult;
import online.yudream.base.plugin.invite.bootstrap.InvitePlugin;
import online.yudream.base.plugin.invite.domain.InviteBinding;
import online.yudream.base.plugin.invite.interfaces.support.HttpSupport;
import online.yudream.base.plugin.invite.interfaces.support.Views;

/**
 * 管理端接口（/admin/**，plugin:invite:manage）：邀请码绑定总览（跨用户）与删除纠错。
 */
public class InviteAdminController {

    private final InviteBindingService inviteBindingService;
    private final FrameworkServices framework;

    public InviteAdminController(InviteBindingService inviteBindingService, FrameworkServices framework) {
        this.inviteBindingService = inviteBindingService;
        this.framework = framework;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/invite-bindings", permission = InvitePlugin.MANAGE_PERMISSION)
    public PluginHttpResponse listBindings(PluginHttpRequest request) {
        return HttpSupport.guard(() -> {
            PageResult<InviteBinding> result = inviteBindingService.adminPage(
                    HttpSupport.first(request, "keyword"),
                    HttpSupport.pageParam(request), HttpSupport.sizeParam(request, 10));
            return PluginHttpResponse.ok(Map.of(
                    "records", result.records().stream()
                            .map(binding -> Views.adminBindingView(binding, framework))
                            .toList(),
                    "total", result.total()));
        });
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/invite-bindings/{id}", permission = InvitePlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteBinding(PluginHttpRequest request) {
        return HttpSupport.guard(() -> {
            String id = HttpSupport.segmentAfter(request.path(), "invite-bindings");
            inviteBindingService.adminDelete(id);
            return PluginHttpResponse.ok(Map.of("deleted", true));
        });
    }
}
