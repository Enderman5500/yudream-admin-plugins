package online.yudream.base.plugin.mail.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.mail.application.NotFoundException;
import online.yudream.base.plugin.mail.application.PageResult;
import online.yudream.base.plugin.mail.application.cmd.MailAddressSaveCmd;
import online.yudream.base.plugin.mail.application.query.MailAddressQuery;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.repo.MailAddressRepository;
import online.yudream.base.plugin.mail.domain.support.Ids;
import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;

/**
 * 邮箱地址（收发身份）用例。
 *
 * <p>业务规则：地址全局唯一（大小写不敏感）；默认发件地址必须启用、不能被停用或删除；
 * 停用地址不能发信；单实例最多 {@value #MAX_ADDRESSES} 个地址。</p>
 *
 * <p>凭据规则：SMTP/IMAP 密码只写进 {@link PluginSecretStore}（宿主加密落库），地址文档里不存密码；
 * 更新时密码留空表示保持原密码；配置里 host 被清空则删除对应密钥。</p>
 */
public class MailAddressService {

    /** 单实例地址总量上限，避免被当作无限地址池滥用。 */
    public static final int MAX_ADDRESSES = 50;
    public static final int PASSWORD_MAX_LENGTH = 200;

    static final String SMTP_SECRET_PREFIX = "smtp-password:";
    static final String IMAP_SECRET_PREFIX = "imap-password:";

    private final MailAddressRepository addresses;
    private final PluginSecretStore secrets;

    public MailAddressService(MailAddressRepository addresses, PluginSecretStore secrets) {
        this.addresses = addresses;
        this.secrets = secrets;
    }

    // ---------------- 查询 ----------------

    public PageResult<MailAddress> page(MailAddressQuery query) {
        MailAddressQuery safeQuery = query == null ? new MailAddressQuery(null, null, 1, 10) : query;
        List<MailAddress> matched = addresses.findAll().stream()
                .filter(item -> item.matches(safeQuery.keyword(), safeQuery.enabled()))
                .toList();
        return PageResult.slice(matched, safeQuery.safePage(), safeQuery.safeSize());
    }

    /** 发件地址选择器：只返回启用的地址，默认地址排在最前。 */
    public List<MailAddress> listEnabled() {
        return addresses.findAll().stream()
                .filter(MailAddress::enabled)
                .sorted(Comparator.comparing(MailAddress::defaultSender).reversed()
                        .thenComparing(MailAddress::address))
                .toList();
    }

    /** 管理端地址选项：返回全部地址（含停用）。 */
    public List<MailAddress> listOptions() {
        return addresses.findAll().stream()
                .sorted(Comparator.comparing(MailAddress::defaultSender).reversed()
                        .thenComparing(MailAddress::enabled, Comparator.reverseOrder())
                        .thenComparing(MailAddress::address))
                .toList();
    }

    public Optional<MailAddress> defaultAddress() {
        return addresses.findAll().stream().filter(MailAddress::defaultSender).findFirst();
    }

    public MailAddress requireExisting(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("邮箱地址 ID 不能为空");
        }
        return addresses.findById(id.trim())
                .orElseThrow(() -> new NotFoundException("邮箱地址不存在"));
    }

    /** 发信前置校验：地址必须存在且处于启用状态。 */
    public MailAddress requireEnabled(String id) {
        MailAddress address = requireExisting(id);
        if (!address.enabled()) {
            throw new IllegalArgumentException("邮箱地址已停用，不能用于发信：" + address.address());
        }
        return address;
    }

    public long count() {
        return addresses.count();
    }

    public long countEnabled() {
        return addresses.findAll().stream().filter(MailAddress::enabled).count();
    }

    // ---------------- 写操作 ----------------

    public MailAddress create(MailAddressSaveCmd cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }
        if (addresses.count() >= MAX_ADDRESSES) {
            throw new IllegalArgumentException("邮箱地址数量已达上限 " + MAX_ADDRESSES + " 个");
        }
        MailAddress candidate = MailAddress.create(Ids.newId(), cmd.address(), cmd.displayName(),
                purposeOf(cmd.purpose()), cmd.remark(), cmd.enabled(), smtpConfig(cmd.smtp()), imapConfig(cmd.imap()));
        ensureAddressAvailable(candidate.address(), null);
        boolean firstEnabled = candidate.enabled() && addresses.findAll().stream().noneMatch(MailAddress::enabled);
        MailAddress saved = addresses.save(firstEnabled ? candidate.withDefaultSender(true) : candidate);
        if (saved.defaultSender()) {
            clearOtherDefaults(saved.id());
        }
        storeSecrets(saved, cmd);
        return saved;
    }

    public MailAddress update(String id, MailAddressSaveCmd cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }
        MailAddress existing = requireExisting(id);
        MailAddress candidate = existing.update(cmd.address(), cmd.displayName(), purposeOf(cmd.purpose()),
                cmd.remark(), smtpConfig(cmd.smtp()), imapConfig(cmd.imap()));
        ensureAddressAvailable(candidate.address(), existing.id());
        MailAddress saved = addresses.save(candidate);
        storeSecrets(saved, cmd);
        return saved;
    }

    public MailAddress setEnabled(String id, boolean enabled) {
        MailAddress existing = requireExisting(id);
        if (!enabled && existing.defaultSender()) {
            throw new IllegalArgumentException("默认发件地址不能停用，请先把其他地址设为默认发件地址");
        }
        return addresses.save(existing.withEnabled(enabled));
    }

    public MailAddress setDefault(String id) {
        MailAddress existing = requireExisting(id);
        if (!existing.enabled()) {
            throw new IllegalArgumentException("停用的邮箱地址不能设为默认发件地址");
        }
        MailAddress saved = addresses.save(existing.withDefaultSender(true));
        clearOtherDefaults(saved.id());
        return saved;
    }

    /** 删除地址：默认发件地址受保护；同时清理该地址的全部凭据。 */
    public void delete(String id) {
        MailAddress existing = requireExisting(id);
        if (existing.defaultSender()) {
            throw new IllegalArgumentException("默认发件地址不能删除，请先把其他地址设为默认发件地址");
        }
        addresses.delete(existing.id());
        deleteSecret(SMTP_SECRET_PREFIX + existing.id());
        deleteSecret(IMAP_SECRET_PREFIX + existing.id());
    }

    // ---------------- 凭据 ----------------

    public boolean smtpPasswordSet(String id) {
        return secrets.get(SMTP_SECRET_PREFIX + id).isPresent();
    }

    public boolean imapPasswordSet(String id) {
        return secrets.get(IMAP_SECRET_PREFIX + id).isPresent();
    }

    /** 取 SMTP 密码（仅用于建立连接，不进入任何响应体）。 */
    public String requireSmtpPassword(MailAddress address) {
        return requirePassword(SMTP_SECRET_PREFIX + address.id(), "SMTP");
    }

    public String requireImapPassword(MailAddress address) {
        return requirePassword(IMAP_SECRET_PREFIX + address.id(), "IMAP");
    }

    private String requirePassword(String key, String label) {
        return secrets.get(key)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("该邮箱地址尚未设置 " + label + " 密码"));
    }

    private void storeSecrets(MailAddress saved, MailAddressSaveCmd cmd) {
        applySecret(SMTP_SECRET_PREFIX + saved.id(), saved.smtp().host(),
                cmd.smtp() == null ? null : cmd.smtp().password(), "SMTP");
        applySecret(IMAP_SECRET_PREFIX + saved.id(), saved.imap().host(),
                cmd.imap() == null ? null : cmd.imap().password(), "IMAP");
    }

    /** host 为空视为停用该方向配置，直接删除密钥；密码留空表示保持原值。 */
    private void applySecret(String key, String host, String password, String label) {
        if (host == null) {
            deleteSecret(key);
            return;
        }
        if (password == null || password.isEmpty()) {
            return;
        }
        if (password.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException(label + " 密码不能超过 " + PASSWORD_MAX_LENGTH + " 个字符");
        }
        secrets.put(key, password.getBytes(StandardCharsets.UTF_8));
    }

    private void deleteSecret(String key) {
        try {
            secrets.delete(key);
        }
        catch (RuntimeException ignored) {
            // 删除失败不影响地址删除结果
        }
    }

    // ---------------- 内部工具 ----------------

    private MailAddress.SmtpConfig smtpConfig(MailAddressSaveCmd.SmtpInput input) {
        if (input == null) {
            return MailAddress.SmtpConfig.blank();
        }
        return new MailAddress.SmtpConfig(input.host(), input.port() == null ? 0 : input.port(),
                securityOf(input.security()), input.username());
    }

    private MailAddress.ImapConfig imapConfig(MailAddressSaveCmd.ImapInput input) {
        if (input == null) {
            return MailAddress.ImapConfig.blank();
        }
        return new MailAddress.ImapConfig(input.host(), input.port() == null ? 0 : input.port(),
                securityOf(input.security()), input.username(), input.folder(),
                input.fetchLimit() == null ? 0 : input.fetchLimit(),
                input.allowedFromDomains(), input.requiredKeywords());
    }

    private MailAddress.Security securityOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return MailAddress.Security.SSL;
        }
        try {
            return MailAddress.Security.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return MailAddress.Security.SSL;
        }
    }

    private MailAddress.Purpose purposeOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return MailAddress.Purpose.OTHER;
        }
        try {
            return MailAddress.Purpose.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return MailAddress.Purpose.OTHER;
        }
    }

    private void clearOtherDefaults(String keepId) {
        addresses.findAll().stream()
                .filter(item -> item.defaultSender() && !item.id().equals(keepId))
                .forEach(item -> addresses.save(item.withDefaultSender(false)));
    }

    private void ensureAddressAvailable(String address, String selfId) {
        addresses.findByAddress(address)
                .filter(item -> selfId == null || !item.id().equals(selfId))
                .ifPresent(item -> {
                    throw new IllegalArgumentException("该邮箱地址已存在：" + address);
                });
    }
}
