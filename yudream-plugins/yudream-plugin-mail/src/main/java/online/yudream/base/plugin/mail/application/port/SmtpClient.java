package online.yudream.base.plugin.mail.application.port;

import java.util.List;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;

/** SMTP 发信端口（实现见 infrastructure.mail.JakartaSmtpClient）。 */
public interface SmtpClient {

    /** 建立连接并完成认证，不投递任何邮件；失败抛 {@link MailTransportException}。 */
    void testConnection(SmtpSettings settings);

    /** 同步投递一封邮件；SMTP 服务器接收即返回，失败抛 {@link MailTransportException}。 */
    void send(SmtpSettings settings, OutboundMailMessage message);

    /** 发信连接参数（含解密后的密码，仅在调用期间存在）。 */
    record SmtpSettings(
            String host,
            int port,
            MailAddress.Security security,
            String username,
            String password,
            String fromAddress,
            String fromName
    ) {
    }

    /** 待投递邮件内容。inReplyTo/references 为回复线程头（Message-ID 链），转发/普通发信传 null。 */
    record OutboundMailMessage(
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String text,
            String html,
            String inReplyTo,
            String references
    ) {
    }
}
