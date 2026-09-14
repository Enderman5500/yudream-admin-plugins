package online.yudream.base.plugin.yggc.infrastructure.service;

import online.yudream.base.plugin.yggc.domain.valobj.KeyMaterial;
import online.yudream.base.plugin.yggc.infrastructure.repository.YggcRepository;
import online.yudream.base.plugin.yggc.infrastructure.support.JsonSupport;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 密钥与签名服务：材质属性签名（SHA1withRSA，兼容 authlib-injector 生态）
 * 与 Yggdrasil Connect ID Token 签名（RS256 JWT）共用本服务，密钥独立存储。
 */
public class YggcCryptoService {

    private static final String TEXTURE_KEY = "keypair.texture";
    private static final String TOKEN_KEY = "keypair.jwt";
    private static final String UNION_OAUTH2_SIG_KEY = "keypair.union-oauth2-sig";

    private final YggcRepository repository;
    private final SecureRandom random = new SecureRandom();

    public YggcCryptoService(YggcRepository repository) {
        this.repository = repository;
    }

    // ---- 材质签名（Yggdrasil API）----

    public String texturePublicKeyPem() {
        KeyMaterial keyPair = textureKeyPair();
        return pem("PUBLIC KEY", keyPair.publicKey());
    }

    /**
     * 导入 Union 主服务器分发的签名私钥（PEM，PKCS#8 或 PKCS#1）。
     * 导入后本站材质签名与全联邦共用同一密钥，跨站角色数据即可互相验证。
     */
    public KeyMaterial importUnionPrivateKey(String pemText) {
        KeyMaterial material = fromPem(pemText);
        return repository.saveKeyPair(TEXTURE_KEY, material);
    }

    /** 解析 PEM 私钥：支持 PKCS#8（BEGIN PRIVATE KEY）与 PKCS#1（BEGIN RSA PRIVATE KEY）。 */
    private KeyMaterial fromPem(String pemText) {
        if (pemText == null || pemText.isBlank()) {
            throw new IllegalArgumentException("私钥内容不能为空");
        }
        String body = pemText.replaceAll("-----(BEGIN|END)[^-]+-----", "").replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("私钥 PEM 解码失败，请确认从 Union 主服务器获取的完整内容");
        }
        if (pemText.contains("BEGIN RSA PRIVATE KEY")) {
            der = wrapPkcs1ToPkcs8(der);
        } else if (!pemText.contains("BEGIN PRIVATE KEY")) {
            throw new IllegalArgumentException("无法识别的私钥格式，仅支持 PKCS#8 / PKCS#1 PEM");
        }
        try {
            KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("私钥内容无效：" + e.getMessage());
        }
        return new KeyMaterial(derivePublicKeyPemBase64(der), Base64.getEncoder().encodeToString(der));
    }

    /** PKCS#1 RSAPrivateKey → PKCS#8 PrivateKeyInfo 包装。 */
    private static byte[] wrapPkcs1ToPkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algorithm = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octet = derTag(0x04, pkcs1);
        byte[] content = concat(version, algorithm, octet);
        return derTag(0x30, content);
    }

    private static byte[] derTag(int tag, byte[] content) {
        int length = content.length;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(tag);
        if (length < 0x80) {
            out.write(length);
        } else if (length < 0x100) {
            out.write(0x81);
            out.write(length);
        } else {
            out.write(0x82);
            out.write(length >> 8);
            out.write(length & 0xff);
        }
        try {
            out.write(content);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    /** 从 PKCS#8 私钥推导公钥（X.509 SubjectPublicKeyInfo）的 base64 内容。 */
    private String derivePublicKeyPemBase64(byte[] pkcs8) {
        try {
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            if (key instanceof java.security.interfaces.RSAPrivateCrtKey crt) {
                java.security.spec.RSAPublicKeySpec publicKeySpec = new java.security.spec.RSAPublicKeySpec(
                        crt.getModulus(), crt.getPublicExponent());
                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
                return Base64.getEncoder().encodeToString(publicKey.getEncoded());
            }
            throw new IllegalArgumentException("私钥为非 CRT 格式，无法推导公钥");
        } catch (Exception e) {
            throw new IllegalArgumentException("从私钥推导公钥失败：" + e.getMessage());
        }
    }

    public String signTexture(String value) {
        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(privateKey(textureKeyPair()));
            signature.update(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("材质属性签名失败：" + e.getMessage(), e);
        }
    }

    // ---- ID Token 签名（Yggdrasil Connect / RS256）----

    public String keyId() {
        try {
            byte[] encoded = publicKey(tokenKeyPair()).getEncoded();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                builder.append(String.format("%02x", digest[index]));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("密钥标识生成失败：" + e.getMessage(), e);
        }
    }

    public Map<String, Object> jwks() {
        RSAPublicKey rsa;
        try {
            rsa = (RSAPublicKey) publicKey(tokenKeyPair());
        } catch (Exception e) {
            throw new IllegalStateException("JWKS 生成失败：" + e.getMessage(), e);
        }
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId());
        jwk.put("n", base64Url(toUnsignedBytes(rsa.getModulus())));
        jwk.put("e", base64Url(toUnsignedBytes(rsa.getPublicExponent())));
        return Map.of("keys", List.of(jwk));
    }

    public String signIdToken(Map<String, Object> claims) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");
            header.put("kid", keyId());
            String encodedHeader = base64Url(JsonSupport.write(header).getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64Url(JsonSupport.write(claims).getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey(tokenKeyPair()));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("ID Token 签名失败：" + e.getMessage(), e);
        }
    }

    // ---- Union OAuth2（站点签名密钥，本地生成）----

    /** Union OAuth2 站点签名密钥对（用于向 Union 主服务器签发用户信息令牌）。 */
    public KeyMaterial unionOauth2SigKeyPair() {
        return repository.keyPair(UNION_OAUTH2_SIG_KEY).orElseGet(() -> generate(UNION_OAUTH2_SIG_KEY, 2048));
    }

    public String unionOauth2SigPublicKeyPem() {
        return pem("PUBLIC KEY", unionOauth2SigKeyPair().publicKey());
    }

    /** SHA256withRSA 签名（Union OAuth2 userInfoToken 签名）。 */
    public String signSha256Rsa(KeyMaterial keyPair, String data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey(keyPair));
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("RSA 签名失败：" + e.getMessage(), e);
        }
    }

    /** HMAC-SHA256（Union OAuth2 memberKey 消息校验码）。 */
    public String hmacSha256Hex(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((data == null ? "" : data).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败：" + e.getMessage(), e);
        }
    }

    /** 使用 Union 主服务器公钥（PEM）做 RSA PKCS#1 加密。 */
    public String rsaEncryptWithPem(String pemPublicKey, String data) {
        try {
            String body = pemPublicKey.replaceAll("-----(BEGIN|END)[^-]+-----", "").replaceAll("\\s", "");
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, publicKey);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal((data == null ? "" : data).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("RSA 加密失败：" + e.getMessage(), e);
        }
    }

    /** 使用 PEM 公钥验证 SHA256 签名（Union 主机验证）。 */
    public boolean verifySha256WithPem(String pemPublicKey, String data, byte[] signature) {
        try {
            String body = pemPublicKey.replaceAll("-----(BEGIN|END)[^-]+-----", "").replaceAll("\\s", "");
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update((data == null ? "" : data).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 随机值 ----

    public String randomToken(int chars) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder builder = new StringBuilder(chars);
        for (int index = 0; index < chars; index++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    public String randomUserCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(8);
        for (int index = 0; index < 8; index++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        builder.insert(4, '-');
        return builder.toString();
    }

    public String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 摘要失败：" + e.getMessage(), e);
        }
    }

    // ---- 密钥管理（管理端） ----

    /** 查看密钥信息（不触发生成）：usage = texture（材质签名）| token（JWT 签名）| union-oauth2（Union OAuth2 签名）。 */
    public Map<String, Object> keyPairInfo(String usage) {
        Optional<KeyMaterial> material = repository.keyPair(keyName(usage));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("usage", usage);
        info.put("exists", material.isPresent());
        material.ifPresent(value -> info.put("publicKey", pem("PUBLIC KEY", value.publicKey())));
        if ("token".equals(usage) && material.isPresent()) {
            info.put("kid", keyId());
        }
        return info;
    }

    /** 重新生成密钥对：旧签名立即失效，需谨慎操作。 */
    public Map<String, Object> regenerateKeyPair(String usage) {
        KeyMaterial material = generate(keyName(usage), switch (keyName(usage)) {
            case TOKEN_KEY -> 2048;
            case UNION_OAUTH2_SIG_KEY -> 2048;
            default -> 4096;
        });
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("usage", usage);
        info.put("publicKey", pem("PUBLIC KEY", material.publicKey()));
        if ("token".equals(usage)) {
            info.put("kid", keyId());
        }
        return info;
    }

    private String keyName(String usage) {
        return switch (usage == null ? "" : usage) {
            case "token" -> TOKEN_KEY;
            case "texture" -> TEXTURE_KEY;
            case "union-oauth2" -> UNION_OAUTH2_SIG_KEY;
            default -> throw new IllegalArgumentException("未知密钥用途：" + usage);
        };
    }

    // ---- 密钥管理 ----

    private KeyMaterial textureKeyPair() {
        return repository.keyPair(TEXTURE_KEY).orElseGet(() -> generate(TEXTURE_KEY, 4096));
    }

    private KeyMaterial tokenKeyPair() {
        return repository.keyPair(TOKEN_KEY).orElseGet(() -> generate(TOKEN_KEY, 2048));
    }

    private KeyMaterial generate(String name, int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize);
            KeyPair keyPair = generator.generateKeyPair();
            return repository.saveKeyPair(name, new KeyMaterial(
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            ));
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥生成失败：" + e.getMessage(), e);
        }
    }

    private PrivateKey privateKey(KeyMaterial keyPair) throws Exception {
        byte[] privateBytes = Base64.getDecoder().decode(keyPair.privateKey());
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
    }

    private PublicKey publicKey(KeyMaterial keyPair) throws Exception {
        byte[] publicBytes = Base64.getDecoder().decode(keyPair.publicKey());
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicBytes));
    }

    private String pem(String type, String base64) {
        StringBuilder builder = new StringBuilder("-----BEGIN ").append(type).append("-----\n");
        for (int index = 0; index < base64.length(); index += 76) {
            builder.append(base64, index, Math.min(index + 76, base64.length())).append('\n');
        }
        return builder.append("-----END ").append(type).append("-----\n").toString();
    }

    private static byte[] toUnsignedBytes(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length > 1 && raw[0] == 0) {
            byte[] trimmed = new byte[raw.length - 1];
            System.arraycopy(raw, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return raw;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
