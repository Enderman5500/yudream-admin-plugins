package online.yudream.base.plugin.yggc.infrastructure.repository;

import online.yudream.base.plugin.yggc.domain.aggregate.AuthSession;
import online.yudream.base.plugin.yggc.domain.aggregate.DeviceCode;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthClient;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthCode;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthRefreshToken;
import online.yudream.base.plugin.yggc.domain.aggregate.OAuthToken;
import online.yudream.base.plugin.yggc.domain.aggregate.ServerJoin;
import online.yudream.base.plugin.yggc.domain.aggregate.YggcSettings;
import online.yudream.base.plugin.yggc.domain.valobj.KeyMaterial;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class YggcRepository {

    private static final int SCAN_PAGE_SIZE = 200;

    private static final String SESSIONS = "sessions";
    private static final String JOINS = "joins";
    private static final String SETTINGS = "settings";
    private static final String SETTINGS_ID = "config";
    private static final String UNION_STATE = "union";
    private static final String UNION_SERVER_LIST_ID = "serverlist";
    private static final String UNION_PRIVATE_KEY_ID = "privatekey";
    private static final String UNION_UUID_REMAP_ID = "uuid-remap";
    private static final String CLIENTS = "oauth-clients";
    private static final String CODES = "oauth-codes";
    private static final String TOKENS = "oauth-tokens";
    private static final String REFRESH = "oauth-refresh";
    private static final String DEVICES = "oauth-devices";

    private final PluginDocumentStore documents;

    public YggcRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    // ---- 传统 Yggdrasil 会话 ----

    public AuthSession saveSession(AuthSession session) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("clientToken", session.clientToken());
        document.put("userId", session.userId());
        document.put("username", session.username());
        document.put("selectedProfileId", session.selectedProfileId());
        document.put("issuedAt", session.issuedAt());
        document.put("expiresAt", session.expiresAt());
        documents.save(SESSIONS, session.accessToken(), document);
        return session;
    }

    public Optional<AuthSession> findSession(String accessToken) {
        return documents.findById(SESSIONS, accessToken).map(this::toSession);
    }

    public List<AuthSession> findSessionsByUser(String userId) {
        return findAllByField(SESSIONS, "userId", userId).stream().map(this::toSession).toList();
    }

    public void deleteSession(String accessToken) {
        documents.delete(SESSIONS, accessToken);
    }

    public ServerJoin saveJoin(ServerJoin join) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("serverId", join.serverId());
        document.put("profileId", join.profileId());
        document.put("username", join.username());
        document.put("accessToken", join.accessToken());
        document.put("expiresAt", join.expiresAt());
        documents.save(JOINS, join.id(), document);
        return join;
    }

    public List<ServerJoin> findJoinsByServer(String serverId) {
        return findAllByField(JOINS, "serverId", serverId).stream().map(this::toJoin).toList();
    }

    // ---- 插件配置 ----

    public Optional<YggcSettings> settings() {
        return documents.findById(SETTINGS, SETTINGS_ID).map(YggcSettings::from);
    }

    public YggcSettings saveSettings(YggcSettings settings) {
        documents.save(SETTINGS, SETTINGS_ID, settings.toDocument());
        return settings;
    }

    // ---- Union 联邦状态 ----

    public Optional<Map<String, Object>> findUnionState(String id) {
        return documents.findById(UNION_STATE, id);
    }

    public Map<String, Object> saveUnionState(String id, Map<String, Object> document) {
        return documents.save(UNION_STATE, id, document);
    }

    /** 皮肤站列表（union_server_list）。 */
    public Optional<Map<String, Object>> serverList() {
        return findUnionState(UNION_SERVER_LIST_ID);
    }

    /** 上游签名私钥的版本信息（私钥本体存于 keypair.texture）。 */
    public Optional<Map<String, Object>> unionPrivateKey() {
        return findUnionState(UNION_PRIVATE_KEY_ID);
    }

    /** UUID 重映射：旧 UUID → 新 UUID（Union 主服务器下发的 remapped_uuid）。 */
    public Map<String, String> uuidMappings() {
        return findUnionState(UNION_UUID_REMAP_ID)
                .map(document -> stringMap(document, "mappings"))
                .orElseGet(LinkedHashMap::new);
    }

    public void saveUuidMappings(Map<String, String> mappings) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("mappings", new LinkedHashMap<>(mappings));
        document.put("updatedAt", System.currentTimeMillis());
        documents.save(UNION_STATE, UNION_UUID_REMAP_ID, document);
    }

    public void clearUuidMapping(String oldUuid) {
        Map<String, String> mappings = uuidMappings();
        if (mappings.remove(oldUuid) != null) {
            saveUuidMappings(mappings);
        }
    }

    // ---- 密钥 ----

    public Optional<KeyMaterial> keyPair(String name) {
        return documents.findById(SETTINGS, name)
                .map(document -> new KeyMaterial(string(document, "publicKey"), string(document, "privateKey")));
    }

    public KeyMaterial saveKeyPair(String name, KeyMaterial keyPair) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("publicKey", keyPair.publicKey());
        document.put("privateKey", keyPair.privateKey());
        Map<String, Object> saved = documents.save(SETTINGS, name, document);
        return new KeyMaterial(string(saved, "publicKey"), string(saved, "privateKey"));
    }

    // ---- OAuth 客户端 ----

    public OAuthClient saveClient(OAuthClient client) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("name", client.name());
        document.put("secretHash", client.secretHash());
        document.put("redirectUris", client.redirectUris());
        document.put("publicClient", client.publicClient());
        document.put("enabled", client.enabled());
        document.put("createdAt", client.createdAt() == null ? System.currentTimeMillis() : client.createdAt());
        documents.save(CLIENTS, client.id(), document);
        return client;
    }

    public Optional<OAuthClient> findClient(String clientId) {
        return documents.findById(CLIENTS, clientId).map(this::toClient);
    }

    public List<OAuthClient> findClients(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return documents.findAll(CLIENTS, page, size).stream().map(this::toClient).toList();
        }
        String needle = keyword.trim().toLowerCase();
        List<OAuthClient> matched = new ArrayList<>();
        int scanPage = 1;
        while (true) {
            List<Map<String, Object>> batch = documents.findAll(CLIENTS, scanPage, SCAN_PAGE_SIZE);
            batch.stream().map(this::toClient)
                    .filter(client -> contains(client.id(), needle) || contains(client.name(), needle))
                    .forEach(matched::add);
            if (batch.size() < SCAN_PAGE_SIZE) {
                break;
            }
            scanPage++;
        }
        int from = Math.max(0, (page - 1) * size);
        return from >= matched.size() ? List.of() : matched.subList(from, Math.min(from + size, matched.size()));
    }

    public long clientCount() {
        return documents.count(CLIENTS);
    }

    public void deleteClient(String clientId) {
        documents.delete(CLIENTS, clientId);
    }

    // ---- 授权码 ----

    public OAuthCode saveCode(OAuthCode code) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("clientId", code.clientId());
        document.put("userId", code.userId());
        document.put("profileId", code.profileId());
        document.put("scopes", code.scopes());
        document.put("redirectUri", code.redirectUri());
        document.put("codeChallenge", code.codeChallenge());
        document.put("challengeMethod", code.challengeMethod());
        document.put("nonce", code.nonce());
        document.put("expiresAt", code.expiresAt());
        documents.save(CODES, code.code(), document);
        return code;
    }

    public Optional<OAuthCode> findCode(String code) {
        return documents.findById(CODES, code).map(this::toCode);
    }

    public void deleteCode(String code) {
        documents.delete(CODES, code);
    }

    // ---- 访问令牌 ----

    public OAuthToken saveToken(OAuthToken token) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("clientId", token.clientId());
        document.put("userId", token.userId());
        document.put("nickname", token.nickname());
        document.put("profileId", token.profileId());
        document.put("profileName", token.profileName());
        document.put("scopes", token.scopes());
        document.put("issuedAt", token.issuedAt());
        document.put("expiresAt", token.expiresAt());
        documents.save(TOKENS, token.token(), document);
        return token;
    }

    public Optional<OAuthToken> findToken(String token) {
        return documents.findById(TOKENS, token).map(this::toToken);
    }

    public List<OAuthToken> findTokens(int page, int size) {
        return documents.findAll(TOKENS, page, size).stream().map(this::toToken).toList();
    }

    public List<OAuthToken> findTokensByUser(String userId) {
        return findAllByField(TOKENS, "userId", userId).stream().map(this::toToken).toList();
    }

    public List<OAuthToken> findTokensByClient(String clientId) {
        return findAllByField(TOKENS, "clientId", clientId).stream().map(this::toToken).toList();
    }

    public void deleteToken(String token) {
        documents.delete(TOKENS, token);
    }

    public long tokenCount() {
        return documents.count(TOKENS);
    }

    public long sessionCount() {
        return documents.count(SESSIONS);
    }

    // ---- 刷新令牌 ----

    public OAuthRefreshToken saveRefreshToken(OAuthRefreshToken token) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("clientId", token.clientId());
        document.put("userId", token.userId());
        document.put("profileId", token.profileId());
        document.put("accessToken", token.accessToken());
        document.put("scopes", token.scopes());
        document.put("issuedAt", token.issuedAt());
        document.put("expiresAt", token.expiresAt());
        documents.save(REFRESH, token.token(), document);
        return token;
    }

    public Optional<OAuthRefreshToken> findRefreshToken(String token) {
        return documents.findById(REFRESH, token).map(this::toRefreshToken);
    }

    public List<OAuthRefreshToken> findRefreshTokensByUser(String userId) {
        return findAllByField(REFRESH, "userId", userId).stream().map(this::toRefreshToken).toList();
    }

    public List<OAuthRefreshToken> findRefreshTokensByClient(String clientId) {
        return findAllByField(REFRESH, "clientId", clientId).stream().map(this::toRefreshToken).toList();
    }

    public List<OAuthRefreshToken> findRefreshTokensByAccessToken(String accessToken) {
        return findAllByField(REFRESH, "accessToken", accessToken).stream().map(this::toRefreshToken).toList();
    }

    public void deleteRefreshToken(String token) {
        documents.delete(REFRESH, token);
    }

    // ---- 设备授权码 ----

    public DeviceCode saveDeviceCode(DeviceCode code) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("userCode", code.userCode());
        document.put("clientId", code.clientId());
        document.put("scopes", code.scopes());
        document.put("status", code.status());
        document.put("userId", code.userId());
        document.put("profileId", code.profileId());
        document.put("expiresAt", code.expiresAt());
        document.put("intervalSeconds", code.intervalSeconds());
        document.put("lastPolledAt", code.lastPolledAt());
        documents.save(DEVICES, code.deviceCode(), document);
        return code;
    }

    public Optional<DeviceCode> findDeviceCode(String deviceCode) {
        return documents.findById(DEVICES, deviceCode).map(this::toDeviceCode);
    }

    public Optional<DeviceCode> findDeviceCodeByUserCode(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = userCode.trim().toUpperCase().replace("-", "");
        return findAllByField(DEVICES, "userCode", userCode.trim()).stream()
                .map(this::toDeviceCode)
                .filter(code -> normalizeUserCode(code.userCode()).equals(normalized))
                .findFirst();
    }

    public void deleteDeviceCode(String deviceCode) {
        documents.delete(DEVICES, deviceCode);
    }

    // ---- 转换 ----

    private AuthSession toSession(Map<String, Object> document) {
        return new AuthSession(string(document, "id"), string(document, "clientToken"), string(document, "userId"),
                string(document, "username"), string(document, "selectedProfileId"), number(document, "issuedAt"),
                number(document, "expiresAt"));
    }

    private ServerJoin toJoin(Map<String, Object> document) {
        return new ServerJoin(string(document, "id"), string(document, "serverId"), string(document, "profileId"),
                string(document, "username"), string(document, "accessToken"), number(document, "expiresAt"));
    }

    private OAuthClient toClient(Map<String, Object> document) {
        return new OAuthClient(string(document, "id"), string(document, "name"), string(document, "secretHash"),
                stringList(document, "redirectUris"), bool(document, "publicClient"), bool(document, "enabled"),
                number(document, "createdAt"));
    }

    private OAuthCode toCode(Map<String, Object> document) {
        return new OAuthCode(string(document, "id"), string(document, "clientId"), string(document, "userId"),
                string(document, "profileId"), stringList(document, "scopes"), string(document, "redirectUri"),
                string(document, "codeChallenge"), string(document, "challengeMethod"), string(document, "nonce"),
                number(document, "expiresAt"));
    }

    private OAuthToken toToken(Map<String, Object> document) {
        return new OAuthToken(string(document, "id"), string(document, "clientId"), string(document, "userId"),
                string(document, "nickname"), string(document, "profileId"), string(document, "profileName"),
                stringList(document, "scopes"), number(document, "issuedAt"), number(document, "expiresAt"));
    }

    private OAuthRefreshToken toRefreshToken(Map<String, Object> document) {
        return new OAuthRefreshToken(string(document, "id"), string(document, "clientId"), string(document, "userId"),
                string(document, "profileId"), string(document, "accessToken"), stringList(document, "scopes"),
                number(document, "issuedAt"), number(document, "expiresAt"));
    }

    private DeviceCode toDeviceCode(Map<String, Object> document) {
        return new DeviceCode(string(document, "id"), string(document, "userCode"), string(document, "clientId"),
                string(document, "scopes"), string(document, "status"), string(document, "userId"),
                string(document, "profileId"), number(document, "expiresAt"),
                number(document, "intervalSeconds") == null ? 5 : number(document, "intervalSeconds").intValue(),
                number(document, "lastPolledAt"));
    }

    private List<Map<String, Object>> findAllByField(String collection, String field, Object value) {
        List<Map<String, Object>> records = new ArrayList<>();
        int page = 1;
        while (true) {
            List<Map<String, Object>> batch = documents.findByField(collection, field, value, page, SCAN_PAGE_SIZE);
            records.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return records;
            }
            page++;
        }
    }

    private String normalizeUserCode(String userCode) {
        return userCode == null ? "" : userCode.trim().toUpperCase().replace("-", "");
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private List<String> stringList(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMap(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Boolean bool(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private Long number(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value));
    }
}
