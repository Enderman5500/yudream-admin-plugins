package online.yudream.base.plugin.yggc.infrastructure.support;

import online.yudream.base.plugin.yggc.infrastructure.service.YggcCryptoService;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcUnionClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Union 主机验证（对应原插件 UnionHostVerify 中间件）：
 * 验证 Union 主服务器回调请求的 X-Message-Signature / X-Message-Timestamp / X-Message-Nonce 头。
 * - 时间戳窗口：[now-10s, now+30s]（Unix 秒）；
 * - nonce 60 秒防重放（内存缓存）；
 * - 验签公钥取自 GET {union_api_root} 响应中的 union_host_signature_public_key 字段（缓存 5 分钟）。
 */
public class YggcUnionHostVerifier {

    private static final long NONCE_TTL_MILLIS = 60_000L;
    private static final long PUBLIC_KEY_TTL_MILLIS = 300_000L;
    private static final long TIMESTAMP_BACKWARD_SECONDS = 10;
    private static final long TIMESTAMP_FORWARD_SECONDS = 30;

    private final YggcUnionClient unionClient;
    private final YggcCryptoService cryptoService;
    private final Map<String, Long> nonces = new ConcurrentHashMap<>();
    private volatile String cachedPublicKey;
    private volatile long cachedPublicKeyAt;
    private volatile String cachedApiRoot;

    public YggcUnionHostVerifier(YggcUnionClient unionClient, YggcCryptoService cryptoService) {
        this.unionClient = unionClient;
        this.cryptoService = cryptoService;
    }

    /**
     * 验证主服务器回调。失败时抛出 IllegalArgumentException（HTTP 层转 403）。
     *
     * @param apiRoot   Union API Root
     * @param headers  请求头
     * @param body     原始请求体
     */
    public void verify(String apiRoot, Map<String, List<String>> headers, String body) {
        String signature = firstHeader(headers, "x-message-signature");
        String timestamp = firstHeader(headers, "x-message-timestamp");
        String nonce = firstHeader(headers, "x-message-nonce");
        if (signature == null || timestamp == null || nonce == null) {
            throw new IllegalArgumentException("Union 主机验证失败：缺少签名头");
        }
        long ts = parseTimestamp(timestamp);
        long nowSeconds = System.currentTimeMillis() / 1000;
        if (ts < nowSeconds - TIMESTAMP_BACKWARD_SECONDS || ts > nowSeconds + TIMESTAMP_FORWARD_SECONDS) {
            throw new IllegalArgumentException("Union 主机验证失败：时间戳超出允许范围");
        }
        if (nonces.containsKey(nonce)) {
            throw new IllegalArgumentException("Union 主机验证失败：nonce 已被使用");
        }
        String publicKey = hostSignaturePublicKey(apiRoot);
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Union 主机验证失败：签名不是合法的 base64");
        }
        String payload = (body == null ? "" : body) + timestamp + nonce;
        if (!cryptoService.verifySha256WithPem(publicKey, payload, signatureBytes)) {
            throw new IllegalArgumentException("Union 主机验证失败：签名不匹配");
        }
        long now = System.currentTimeMillis();
        nonces.put(nonce, now);
        if (nonces.size() > 4096) {
            nonces.entrySet().removeIf(entry -> now - entry.getValue() > NONCE_TTL_MILLIS);
        }
    }

    private String hostSignaturePublicKey(String apiRoot) {
        String root = apiRoot == null ? "" : apiRoot;
        long now = System.currentTimeMillis();
        if (cachedPublicKey != null && root.equals(cachedApiRoot)
                && now - cachedPublicKeyAt < PUBLIC_KEY_TTL_MILLIS) {
            return cachedPublicKey;
        }
        YggcUnionClient.UnionResult hello = unionClient.get(root, "", null);
        if (!hello.ok() || hello.json() == null) {
            throw new IllegalArgumentException("Union 主机验证失败：无法获取主服务器公告信息");
        }
        Object key = hello.json().get("union_host_signature_public_key");
        if (!(key instanceof String publicKey) || publicKey.isBlank()) {
            throw new IllegalArgumentException("Union 主机验证失败：主服务器未提供验签公钥");
        }
        cachedPublicKey = publicKey;
        cachedApiRoot = root;
        cachedPublicKeyAt = now;
        return publicKey;
    }

    private static long parseTimestamp(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Union 主机验证失败：时间戳格式非法");
        }
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }
}
