package online.yudream.base.plugin.tarusso.domain.repo;

import online.yudream.base.plugin.tarusso.domain.aggregate.SsoSettings;

public interface SsoSettingsRepository {

    SsoSettings get();

    SsoSettings save(SsoSettings settings);
}
