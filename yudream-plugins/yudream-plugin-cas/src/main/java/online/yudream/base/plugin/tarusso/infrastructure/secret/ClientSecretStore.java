package online.yudream.base.plugin.tarusso.infrastructure.secret;

import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ClientSecretStore {

    public static final String KEY = "oidc.client-secret";

    private final PluginSecretStore secrets;

    public ClientSecretStore(PluginSecretStore secrets) {
        this.secrets = secrets;
    }

    public Optional<String> get() {
        return secrets.get(KEY)
                .filter(bytes -> bytes.length > 0)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    public boolean configured() {
        return get().isPresent();
    }

    public void put(String secret) {
        if (secret == null || secret.isBlank()) {
            return;
        }
        secrets.put(KEY, secret.trim().getBytes(StandardCharsets.UTF_8));
    }

    public void clear() {
        secrets.delete(KEY);
    }
}
