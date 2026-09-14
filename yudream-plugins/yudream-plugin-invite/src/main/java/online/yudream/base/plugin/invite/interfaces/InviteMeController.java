package online.yudream.base.plugin.invite.interfaces;

import java.util.Map;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.invite.application.InviteBindingService;
import online.yudream.base.plugin.invite.application.PageResult;
import online.yudream.base.plugin.invite.bootstrap.InvitePlugin;
import online.yudream.base.plugin.invite.domain.InviteBinding;
import online.yudream.base.plugin.invite.infrastructure.JsonSupport;
import online.yudream.base.plugin.invite.interfaces.request.BindInviteRequest;
import online.yudream.base.plugin.invite.interfaces.support.HttpSupport;
import online.yudream.base.plugin.invite.interfaces.support.Views;

/**
 * 用户端接口（/me/**，plugin:invite:bind）：归属一律取 principal.userId()，请求体不含任何归属字段。
 * 邀请码一经绑定不允许用户自行解绑，删除绑定记录只能由管理员在管理端操作。
 */
public class InviteMeController {

    private final InviteBindingService inviteBindingService;
    private final JsonSupport json;

    public InviteMeController(InviteBindingService inviteBindingService, JsonSupport json) {
        this.inviteBindingService = inviteBindingService;
        this.json = json;
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/invite-bindings", permission = InvitePlugin.BIND_PERMISSION)
    public PluginHttpResponse listMyBindings(PluginHttpRequest request) {
        return HttpSupport.guard(() -> {
            String userId = HttpSupport.requireUserId(request);
            PageResult<InviteBinding> result = inviteBindingService.listMine(userId,
                    HttpSupport.pageParam(request), HttpSupport.sizeParam(request, 20));
            return PluginHttpResponse.ok(Map.of(
                    "records", result.records().stream().map(Views::myBindingView).toList(),
                    "total", result.total()));
        });
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/invite-bindings", permission = InvitePlugin.BIND_PERMISSION)
    public PluginHttpResponse bindInvite(PluginHttpRequest request) {
        return HttpSupport.guard(() -> {
            String userId = HttpSupport.requireUserId(request);
            BindInviteRequest body = json.read(request, BindInviteRequest.class);
            InviteBinding binding = inviteBindingService.bindMine(userId, body.code(), body.remark());
            return PluginHttpResponse.ok(Views.myBindingView(binding));
        });
    }
}
