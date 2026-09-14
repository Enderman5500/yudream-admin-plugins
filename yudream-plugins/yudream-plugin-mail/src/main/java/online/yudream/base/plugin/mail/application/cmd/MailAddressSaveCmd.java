package online.yudream.base.plugin.mail.application.cmd;

import java.util.List;

/**
 * 新增/编辑邮箱地址入参：基本信息 + SMTP/IMAP 配置（含明文密码，仅用于写入密钥库）。
 * 密码为空表示“保持原密码不变”，不会覆盖已保存的凭据。
 */
public record MailAddressSaveCmd(
        String address,
        String displayName,
        String purpose,
        String remark,
        boolean enabled,
        SmtpInput smtp,
        ImapInput imap
) {

    public record SmtpInput(
            String host,
            Integer port,
            String security,
            String username,
            String password
    ) {
    }

    public record ImapInput(
            String host,
            Integer port,
            String security,
            String username,
            String password,
            String folder,
            Integer fetchLimit,
            List<String> allowedFromDomains,
            List<String> requiredKeywords
    ) {
    }
}
