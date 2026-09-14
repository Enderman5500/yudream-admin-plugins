package online.yudream.base.plugin.invite.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.invite.application.InviteBindingService;
import online.yudream.base.plugin.invite.infrastructure.InviteBindingRepository;
import online.yudream.base.plugin.invite.infrastructure.JsonSupport;
import online.yudream.base.plugin.invite.interfaces.InviteAdminController;
import online.yudream.base.plugin.invite.interfaces.InviteMeController;

/**
 * 邀请码插件：外校成员邀请码绑定（一码一用户、一用户多码、数量不限）。
 * 绑定后用户不可自行解绑，删除绑定记录仅限管理员。
 */
@PluginSpec(code = InvitePlugin.CODE, name = "邀请码", version = InvitePlugin.VERSION,
        description = "外校成员邀请码绑定：一码一人、一人多码、数量不限；绑定后不可自行解绑，删除仅限管理员")
@PluginPermissions({
        @PluginPermission(code = InvitePlugin.BIND_PERMISSION, name = "绑定邀请码", module = "邀请码",
                description = "绑定另一个皮肤站发放的外校成员邀请码，一人可绑定多个；绑定后不可自行解绑"),
        @PluginPermission(code = InvitePlugin.MANAGE_PERMISSION, name = "管理邀请码", module = "邀请码",
                description = "全部用户邀请码绑定总览与删除纠错")
})
@PluginFrontend(moduleName = "invite", menuTitle = "邀请码", menuIcon = "i-ri:key-2-line", menuSort = 74,
        styles = {"style.css"}, routes = {
        // ---- 管理端 ----
        @PluginRoute(path = "/platform/plugins/invite/admin/bindings", name = "platform-plugin-invite-admin-bindings",
                title = "邀请码绑定", icon = "i-ri:key-2-line", component = "invite/AdminBindings",
                permission = InvitePlugin.MANAGE_PERMISSION, sort = 10),
        // ---- 用户端 ----
        @PluginRoute(path = "/platform/plugins/invite/me/invite-codes", name = "platform-plugin-invite-me-invite-codes",
                title = "我的邀请码", icon = "i-ri:key-2-line", component = "invite/MyInviteCodes",
                permission = InvitePlugin.BIND_PERMISSION, sort = 20)
})
public class InvitePlugin implements YuDreamPlugin {

    public static final String CODE = "invite";
    public static final String VERSION = "1.0.0";
    public static final String BIND_PERMISSION = "plugin:invite:bind";
    public static final String MANAGE_PERMISSION = "plugin:invite:manage";

    @Override
    public void onEnable(PluginContext context) {
        JsonSupport json = new JsonSupport(new ObjectMapper());

        InviteBindingRepository bindings = new InviteBindingRepository(context.documents());
        InviteBindingService bindingService = new InviteBindingService(bindings);

        context.registerHttpController(new InviteAdminController(bindingService, context.framework()));
        context.registerHttpController(new InviteMeController(bindingService, json));
    }
}
