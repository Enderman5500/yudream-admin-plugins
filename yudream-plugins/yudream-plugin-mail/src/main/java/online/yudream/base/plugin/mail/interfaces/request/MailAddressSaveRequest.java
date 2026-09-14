package online.yudream.base.plugin.mail.interfaces.request;

import java.util.List;
import online.yudream.base.plugin.mail.application.cmd.MailAddressSaveCmd;

/**
 * 新增/编辑邮箱地址请求体。
 *
 * <p>密码字段留空表示“保持原密码不变”；把某个方向的 host 清空表示停用该方向配置（并删除已存密码）。</p>
 */
public record MailAddressSaveRequest(
        String address,
        String displayName,
        String purpose,
        String remark,
        Boolean enabled,
        SmtpRequest smtp,
        ImapRequest imap
) {

    public record SmtpRequest(
            String host,
            Integer port,
            String security,
            String username,
            String password
    ) {
    }

    public record ImapRequest(
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

    public MailAddressSaveCmd toCmd() {
        return new MailAddressSaveCmd(
                address,
                displayName,
                purpose,
                remark,
                enabled == null || enabled,
                smtp == null ? null : new MailAddressSaveCmd.SmtpInput(
                        smtp.host(), smtp.port(), smtp.security(), smtp.username(), smtp.password()),
                imap == null ? null : new MailAddressSaveCmd.ImapInput(
                        imap.host(), imap.port(), imap.security(), imap.username(), imap.password(),
                        imap.folder(), imap.fetchLimit(), imap.allowedFromDomains(), imap.requiredKeywords()));
    }
}
