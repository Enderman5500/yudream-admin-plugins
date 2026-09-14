package online.yudream.base.plugin.tarusso.application.service;

import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginAuthorizeRequest;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginDescriptor;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginExchangeRequest;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginIdentity;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginProvider;
import online.yudream.base.plugin.tarusso.bootstrap.TaruSsoPlugin;
import online.yudream.base.plugin.tarusso.domain.aggregate.SsoSettings;
import online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient;

import java.util.List;

public final class TaruSsoLoginProvider implements PluginExternalLoginProvider {

    /**
     * 第三方登录通道标识 = 插件管理标识 {@link TaruSsoPlugin#CODE}。
     * 宿主 SPI 2.27.0 后 login.vue / profile.vue 不再硬编码 "wwoyun"，
     * 登录入口按 {@code /api/external-login/providers} 返回的 enabled 提供方渲染并直接传入 {@code providerCode}，
     * 宿主后端按 {@code descriptor.providerCode()} 精确匹配插件扩展点。
     */
    public static final String PROVIDER_CODE = TaruSsoPlugin.CODE;

    private final SettingsService settings;
    private final StudentInfoService studentInfo;

    public TaruSsoLoginProvider(SettingsService settings, StudentInfoService studentInfo) {
        this.settings = settings;
        this.studentInfo = studentInfo;
    }

    @Override
    public PluginExternalLoginDescriptor descriptor() {
        SsoSettings current = settings.current();
        return new PluginExternalLoginDescriptor(
                PROVIDER_CODE,
                current.displayName(),
                current.icon(),
                List.of(current.protocol().typeCode()),
                20
        );
    }

    @Override
    public boolean enabled() {
        return settings.current().loginEnabled();
    }

    @Override
    public String authorizationUrl(PluginExternalLoginAuthorizeRequest request) {
        if (request == null || request.state() == null || request.state().isBlank()) {
            throw new IllegalArgumentException("授权请求缺少 state");
        }
        ensureType(request.platformType());
        return settings.authorizationUrl(request.state());
    }

    @Override
    public PluginExternalLoginIdentity exchange(PluginExternalLoginExchangeRequest request) {
        if (request == null || request.ticket() == null || request.ticket().isBlank()) {
            throw new IllegalArgumentException("回调缺少票据");
        }
        ensureType(request.platformType());
        SsoSettings current = settings.current();
        SsoProtocolClient.ExternalIdentity identity = settings.exchange(request.ticket(), request.state());
        // 学生档案 upsert 是 best-effort：记录失败不影响登录主流程
        studentInfo.record(identity, current.protocol());
        return new PluginExternalLoginIdentity(
                identity.socialUid(),
                identity.nickname(),
                identity.avatarUrl(),
                identity.gender(),
                identity.location()
        );
    }

    private void ensureType(String platformType) {
        String expected = settings.current().protocol().typeCode();
        if (platformType != null && !platformType.isBlank() && !expected.equalsIgnoreCase(platformType)) {
            throw new IllegalArgumentException("当前协议为 " + expected + "，不支持 " + platformType);
        }
    }
}
