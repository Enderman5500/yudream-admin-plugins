package online.yudream.base.plugin.yggc.domain.valobj;

/**
 * 存储在文档库中的 RSA 密钥材料。
 *
 * @param publicKey  X.509 SubjectPublicKeyInfo 的 Base64 文本
 * @param privateKey PKCS#8 的 Base64 文本
 */
public record KeyMaterial(
        String publicKey,
        String privateKey
) {
}
