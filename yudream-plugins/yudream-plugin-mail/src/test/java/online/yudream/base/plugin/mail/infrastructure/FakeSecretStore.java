package online.yudream.base.plugin.mail.infrastructure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;

/** 宿主密钥库的内存假实现：语义与宿主一致（空值拒绝、按键覆盖、删除返回是否存在）。 */
public final class FakeSecretStore implements PluginSecretStore {

    private final Map<String, byte[]> secrets = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] secret) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("secret must not be empty");
        }
        secrets.put(key, secret.clone());
    }

    @Override
    public Optional<byte[]> get(String key) {
        byte[] value = secrets.get(key);
        return value == null ? Optional.empty() : Optional.of(value.clone());
    }

    @Override
    public boolean delete(String key) {
        return secrets.remove(key) != null;
    }

    /** 测试辅助：当前保存的密钥数量。 */
    public int size() {
        return secrets.size();
    }
}
