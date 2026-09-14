package online.yudream.base.plugin.mail.application.support;

import online.yudream.base.plugin.mail.application.port.ImapClient;
import online.yudream.base.plugin.mail.application.port.SmtpClient;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;

/** 由地址配置与解密后的密码组装传输层连接参数。 */
public final class MailSettingsFactory {

    private MailSettingsFactory() {
    }

    public static SmtpClient.SmtpSettings smtp(MailAddress address, String password) {
        MailAddress.SmtpConfig config = address.smtp();
        return new SmtpClient.SmtpSettings(
                config.host(),
                config.port(),
                config.security(),
                config.username(),
                password,
                address.address(),
                address.displayName());
    }

    public static ImapClient.ImapSettings imap(MailAddress address, String password) {
        MailAddress.ImapConfig config = address.imap();
        return new ImapClient.ImapSettings(
                config.host(),
                config.port(),
                config.security(),
                config.username(),
                password);
    }
}
