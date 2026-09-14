package online.yudream.base.plugin.mail.application.port;

/** 邮件传输失败（SMTP 发送或 IMAP 读取）：消息面向管理员，可直接展示。 */
public class MailTransportException extends RuntimeException {

    public MailTransportException(String message) {
        super(message);
    }

    public MailTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
