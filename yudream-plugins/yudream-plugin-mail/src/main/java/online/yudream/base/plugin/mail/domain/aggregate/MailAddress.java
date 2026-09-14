package online.yudream.base.plugin.mail.domain.aggregate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import online.yudream.base.plugin.mail.domain.support.DocumentValues;

/**
 * 邮箱地址（一条可独立启停的收发身份）。
 *
 * <p>本插件自带 SMTP/IMAP 客户端，因此每条地址记录都携带自己的收发配置：
 * {@link SmtpConfig} 决定怎么发信、{@link ImapConfig} 决定怎么收信。
 * 密码不落在文档里——只保存“有没有配”，真正的密码由 {@code PluginSecretStore} 加密存储，
 * 键为 {@code smtp-password:{id}} / {@code imap-password:{id}}。</p>
 */
public record MailAddress(
        String id,
        String address,
        String displayName,
        Purpose purpose,
        boolean enabled,
        boolean defaultSender,
        String remark,
        SmtpConfig smtp,
        ImapConfig imap,
        long createdAt,
        long updatedAt
) {

    public static final int ADDRESS_MAX_LENGTH = 160;
    public static final int DISPLAY_NAME_MAX_LENGTH = 60;
    public static final int REMARK_MAX_LENGTH = 200;

    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9]([A-Za-z0-9\\-]*[A-Za-z0-9])?"
                    + "(\\.[A-Za-z0-9]([A-Za-z0-9\\-]*[A-Za-z0-9])?)+$");

    /** 邮箱地址用途，仅用于管理端识别。 */
    public enum Purpose {
        NOTIFICATION("通知"),
        VERIFICATION("验证码"),
        SUPPORT("客服"),
        MARKETING("推广"),
        OTHER("其他");

        private final String label;

        Purpose(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 传输加密方式；端口留空时按此推断默认端口。 */
    public enum Security {
        SSL("SSL/TLS", 465, 993),
        STARTTLS("STARTTLS", 587, 143),
        NONE("不加密", 25, 143);

        private final String label;
        private final int defaultSmtpPort;
        private final int defaultImapPort;

        Security(String label, int defaultSmtpPort, int defaultImapPort) {
            this.label = label;
            this.defaultSmtpPort = defaultSmtpPort;
            this.defaultImapPort = defaultImapPort;
        }

        public String label() {
            return label;
        }

        public int defaultSmtpPort() {
            return defaultSmtpPort;
        }

        public int defaultImapPort() {
            return defaultImapPort;
        }
    }

    /** SMTP 发信配置（密码除外）。host 与 username 都有值时才算配置完成。 */
    public record SmtpConfig(String host, int port, Security security, String username) {

        public static final int HOST_MAX_LENGTH = 200;
        public static final int USERNAME_MAX_LENGTH = 200;

        public SmtpConfig {
            host = normalizeHost(host);
            security = security == null ? Security.SSL : security;
            username = trimToNull(username);
            if (username != null) {
                requireMax(username, USERNAME_MAX_LENGTH, "SMTP 用户名");
            }
            port = normalizePort(port, security.defaultSmtpPort(), "SMTP");
        }

        public boolean configured() {
            return host != null && username != null;
        }

        public Map<String, Object> toDocument() {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("host", host);
            document.put("port", port);
            document.put("security", security.name());
            document.put("username", username);
            return document;
        }

        public static SmtpConfig from(Map<String, Object> document) {
            if (document == null || document.isEmpty()) {
                return blank();
            }
            return new SmtpConfig(
                    DocumentValues.textOrNull(document, "host"),
                    DocumentValues.intNumber(document, "port", 0),
                    securityOf(DocumentValues.text(document, "security")),
                    DocumentValues.textOrNull(document, "username"));
        }

        public static SmtpConfig blank() {
            return new SmtpConfig(null, 0, Security.SSL, null);
        }
    }

    /** IMAP 收信配置（密码除外）。 */
    public record ImapConfig(
            String host,
            int port,
            Security security,
            String username,
            String folder,
            int fetchLimit,
            List<String> allowedFromDomains,
            List<String> requiredKeywords
    ) {

        public static final String DEFAULT_FOLDER = "INBOX";
        public static final int DEFAULT_FETCH_LIMIT = 20;
        public static final int MAX_FETCH_LIMIT = 50;
        public static final int FOLDER_MAX_LENGTH = 200;
        public static final int MAX_LIST_ITEMS = 20;
        public static final int DOMAIN_MAX_LENGTH = 120;
        public static final int KEYWORD_MAX_LENGTH = 60;

        public ImapConfig {
            host = normalizeHost(host);
            security = security == null ? Security.SSL : security;
            username = trimToNull(username);
            if (username != null) {
                requireMax(username, SmtpConfig.USERNAME_MAX_LENGTH, "IMAP 用户名");
            }
            port = normalizePort(port, security.defaultImapPort(), "IMAP");
            folder = folder == null || folder.isBlank() ? DEFAULT_FOLDER : folder.trim();
            requireMax(folder, FOLDER_MAX_LENGTH, "收件夹");
            fetchLimit = fetchLimit <= 0 ? DEFAULT_FETCH_LIMIT : fetchLimit;
            if (fetchLimit > MAX_FETCH_LIMIT) {
                throw new IllegalArgumentException("单次拉取数量不能超过 " + MAX_FETCH_LIMIT + " 封");
            }
            allowedFromDomains = normalizeDomains(allowedFromDomains);
            requiredKeywords = normalizeKeywords(requiredKeywords);
        }

        public boolean configured() {
            return host != null && username != null;
        }

        public Map<String, Object> toDocument() {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("host", host);
            document.put("port", port);
            document.put("security", security.name());
            document.put("username", username);
            document.put("folder", folder);
            document.put("fetchLimit", fetchLimit);
            document.put("allowedFromDomains", allowedFromDomains);
            document.put("requiredKeywords", requiredKeywords);
            return document;
        }

        public static ImapConfig from(Map<String, Object> document) {
            if (document == null || document.isEmpty()) {
                return blank();
            }
            return new ImapConfig(
                    DocumentValues.textOrNull(document, "host"),
                    DocumentValues.intNumber(document, "port", 0),
                    securityOf(DocumentValues.text(document, "security")),
                    DocumentValues.textOrNull(document, "username"),
                    DocumentValues.text(document, "folder"),
                    DocumentValues.intNumber(document, "fetchLimit", 0),
                    DocumentValues.stringList(document, "allowedFromDomains"),
                    DocumentValues.stringList(document, "requiredKeywords"));
        }

        public static ImapConfig blank() {
            return new ImapConfig(null, 0, Security.SSL, null, DEFAULT_FOLDER, DEFAULT_FETCH_LIMIT, List.of(), List.of());
        }
    }

    public MailAddress {
        id = requireText(id, "邮箱地址 ID 不能为空");
        address = normalizeAddress(address);
        displayName = trimToNull(displayName);
        purpose = purpose == null ? Purpose.OTHER : purpose;
        remark = remark == null ? "" : remark.trim();
        smtp = smtp == null ? SmtpConfig.blank() : smtp;
        imap = imap == null ? ImapConfig.blank() : imap;
        if (displayName != null) {
            requireMax(displayName, DISPLAY_NAME_MAX_LENGTH, "显示名");
        }
        requireMax(remark, REMARK_MAX_LENGTH, "备注");
        // 停用的地址不允许作为默认发件地址，服务层会给出明确错误，这里做兜底归一。
        defaultSender = defaultSender && enabled;
        long now = System.currentTimeMillis();
        if (createdAt <= 0) {
            createdAt = now;
        }
        if (updatedAt <= 0) {
            updatedAt = createdAt;
        }
    }

    public static MailAddress create(
            String id,
            String address,
            String displayName,
            Purpose purpose,
            String remark,
            boolean enabled,
            SmtpConfig smtp,
            ImapConfig imap) {
        long now = System.currentTimeMillis();
        return new MailAddress(id, address, displayName, purpose, enabled, false, remark, smtp, imap, now, now);
    }

    public MailAddress update(
            String address,
            String displayName,
            Purpose purpose,
            String remark,
            SmtpConfig smtp,
            ImapConfig imap) {
        return new MailAddress(id, address, displayName, purpose, enabled, defaultSender, remark, smtp, imap,
                createdAt, System.currentTimeMillis());
    }

    public MailAddress withEnabled(boolean nextEnabled) {
        return new MailAddress(id, address, displayName, purpose, nextEnabled, defaultSender, remark, smtp, imap,
                createdAt, System.currentTimeMillis());
    }

    public MailAddress withDefaultSender(boolean nextDefault) {
        return new MailAddress(id, address, displayName, purpose, enabled, nextDefault, remark, smtp, imap,
                createdAt, System.currentTimeMillis());
    }

    /** 管理端筛选：关键词匹配地址/显示名/备注/收发主机，enabledFilter 为 null 表示不限状态。 */
    public boolean matches(String keyword, Boolean enabledFilter) {
        if (enabledFilter != null && enabledFilter != enabled) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return address.contains(needle)
                || (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(needle))
                || remark.toLowerCase(Locale.ROOT).contains(needle)
                || matchesHost(smtp.host(), needle)
                || matchesHost(imap.host(), needle);
    }

    private static boolean matchesHost(String host, String needle) {
        return host != null && host.toLowerCase(Locale.ROOT).contains(needle);
    }

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("address", address);
        document.put("displayName", displayName);
        document.put("purpose", purpose.name());
        document.put("enabled", enabled);
        document.put("defaultSender", defaultSender);
        document.put("remark", remark);
        document.put("smtp", smtp.toDocument());
        document.put("imap", imap.toDocument());
        document.put("createdAt", createdAt);
        document.put("updatedAt", updatedAt);
        return document;
    }

    public static MailAddress from(String id, Map<String, Object> document) {
        return new MailAddress(
                id,
                DocumentValues.text(document, "address"),
                DocumentValues.textOrNull(document, "displayName"),
                purposeOf(DocumentValues.text(document, "purpose")),
                DocumentValues.bool(document, "enabled", false),
                DocumentValues.bool(document, "defaultSender", false),
                DocumentValues.text(document, "remark"),
                SmtpConfig.from(DocumentValues.map(document, "smtp")),
                ImapConfig.from(DocumentValues.map(document, "imap")),
                DocumentValues.number(document, "createdAt", 0L),
                DocumentValues.number(document, "updatedAt", 0L));
    }

    private static Purpose purposeOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return Purpose.OTHER;
        }
        try {
            return Purpose.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return Purpose.OTHER;
        }
    }

    /** 未知/缺失的加密方式回退为 SSL，避免静默降级成明文传输。 */
    private static Security securityOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return Security.SSL;
        }
        try {
            return Security.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return Security.SSL;
        }
    }

    private static String normalizeHost(String value) {
        String host = trimToNull(value);
        if (host == null) {
            return null;
        }
        requireMax(host, SmtpConfig.HOST_MAX_LENGTH, "邮件服务器地址");
        if (host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("邮件服务器地址不能包含空格：" + host);
        }
        return host;
    }

    private static int normalizePort(int port, int fallback, String label) {
        if (port <= 0) {
            return fallback;
        }
        if (port > 65535) {
            throw new IllegalArgumentException(label + " 端口不合法：" + port);
        }
        return port;
    }

    private static List<String> normalizeDomains(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String domain = value.trim().toLowerCase(Locale.ROOT);
                while (domain.startsWith("@") || domain.startsWith("*")) {
                    domain = domain.startsWith("@") ? domain.substring(1)
                            : domain.substring(domain.startsWith("*.") ? 2 : 1);
                }
                if (domain.isEmpty()) {
                    continue;
                }
                if (domain.length() > ImapConfig.DOMAIN_MAX_LENGTH) {
                    throw new IllegalArgumentException("发件域不能超过 " + ImapConfig.DOMAIN_MAX_LENGTH + " 个字符：" + domain);
                }
                if (!domain.contains(".")) {
                    throw new IllegalArgumentException("发件域格式不正确（应形如 example.com）：" + domain);
                }
                result.add(domain);
            }
        }
        if (result.size() > ImapConfig.MAX_LIST_ITEMS) {
            throw new IllegalArgumentException("发件域最多 " + ImapConfig.MAX_LIST_ITEMS + " 项");
        }
        return List.copyOf(result);
    }

    private static List<String> normalizeKeywords(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                String keyword = value.trim();
                if (keyword.length() > ImapConfig.KEYWORD_MAX_LENGTH) {
                    throw new IllegalArgumentException("关键词不能超过 " + ImapConfig.KEYWORD_MAX_LENGTH + " 个字符：" + keyword);
                }
                result.add(keyword);
            }
        }
        if (result.size() > ImapConfig.MAX_LIST_ITEMS) {
            throw new IllegalArgumentException("关键词最多 " + ImapConfig.MAX_LIST_ITEMS + " 项");
        }
        return List.copyOf(result);
    }

    /** 归一化邮箱地址：去空格、转小写、校验格式与长度。 */
    public static String normalizeAddress(String value) {
        String normalized = requireText(value, "邮箱地址不能为空").toLowerCase(Locale.ROOT);
        requireMax(normalized, ADDRESS_MAX_LENGTH, "邮箱地址");
        if (!ADDRESS_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("邮箱地址格式不正确：" + normalized);
        }
        return normalized;
    }

    /** 收件人地址归一化：去空格、去重、校验格式。 */
    public static List<String> normalizeRecipients(List<String> values, String label) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                for (String part : value.split("[;,]+")) {
                    if (part.isBlank()) {
                        continue;
                    }
                    String address = normalizeAddress(part);
                    if (!result.contains(address)) {
                        result.add(address);
                    }
                }
            }
        }
        if (result.size() > 50) {
            throw new IllegalArgumentException(label + "最多 50 个");
        }
        return List.copyOf(result);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static void requireMax(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(name + "不能超过 " + maxLength + " 个字符");
        }
    }
}
