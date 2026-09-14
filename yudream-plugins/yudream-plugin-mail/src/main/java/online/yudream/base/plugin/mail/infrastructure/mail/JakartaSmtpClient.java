package online.yudream.base.plugin.mail.infrastructure.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import online.yudream.base.plugin.mail.application.port.MailTransportException;
import online.yudream.base.plugin.mail.application.port.SmtpClient;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * 基于 Jakarta Mail（ANGUS）的 SMTP 客户端实现。
 *
 * <p>每次调用新建 Session 并建立一次连接，用完即关：插件不持有长连接，也不做连接池，
 * 避免在宿主进程里留下后台线程或半开连接。密码只在调用期间存在于内存。</p>
 */
public class JakartaSmtpClient implements SmtpClient {

    private static final String PROTOCOL = "smtp";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;

    @Override
    public void testConnection(SmtpSettings settings) {
        requireConfigured(settings);
        Session session = Session.getInstance(properties(settings), null);
        try (Transport transport = session.getTransport(PROTOCOL)) {
            transport.connect(settings.host(), settings.port(), settings.username(), settings.password());
        }
        catch (MessagingException e) {
            throw new MailTransportException("SMTP 连接/认证失败：" + MailErrors.describe(e), e);
        }
    }

    @Override
    public void send(SmtpSettings settings, OutboundMailMessage message) {
        requireConfigured(settings);
        Session session = Session.getInstance(properties(settings), null);
        try {
            MimeMessage mime = build(session, settings, message);
            try (Transport transport = session.getTransport(PROTOCOL)) {
                transport.connect(settings.host(), settings.port(), settings.username(), settings.password());
                transport.sendMessage(mime, mime.getAllRecipients());
            }
        }
        catch (MessagingException e) {
            throw new MailTransportException("SMTP 投递失败：" + MailErrors.describe(e), e);
        }
    }

    private void requireConfigured(SmtpSettings settings) {
        if (settings == null || settings.host() == null || settings.host().isBlank()
                || settings.username() == null || settings.username().isBlank()) {
            throw new MailTransportException("该邮箱地址尚未配置 SMTP 服务器或用户名");
        }
        if (settings.password() == null || settings.password().isEmpty()) {
            throw new MailTransportException("该邮箱地址尚未设置 SMTP 密码");
        }
    }

    private Properties properties(SmtpSettings settings) {
        Properties properties = new Properties();
        properties.put("mail.transport.protocol", PROTOCOL);
        properties.put("mail." + PROTOCOL + ".host", settings.host());
        properties.put("mail." + PROTOCOL + ".port", String.valueOf(settings.port()));
        properties.put("mail." + PROTOCOL + ".auth", "true");
        properties.put("mail." + PROTOCOL + ".connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MILLIS));
        properties.put("mail." + PROTOCOL + ".timeout", String.valueOf(READ_TIMEOUT_MILLIS));
        properties.put("mail." + PROTOCOL + ".writetimeout", String.valueOf(READ_TIMEOUT_MILLIS));
        if (settings.security() == MailAddress.Security.SSL) {
            properties.put("mail." + PROTOCOL + ".ssl.enable", "true");
        }
        if (settings.security() == MailAddress.Security.STARTTLS) {
            properties.put("mail." + PROTOCOL + ".starttls.enable", "true");
            properties.put("mail." + PROTOCOL + ".starttls.required", "true");
        }
        return properties;
    }

    private MimeMessage build(Session session, SmtpSettings settings, OutboundMailMessage message)
            throws MessagingException {
        try {
            MimeMessage mime = new MimeMessage(session);
            InternetAddress from = settings.fromName() == null || settings.fromName().isBlank()
                    ? new InternetAddress(settings.fromAddress())
                    : new InternetAddress(settings.fromAddress(), settings.fromName(), "UTF-8");
            mime.setFrom(from);
            mime.setRecipients(Message.RecipientType.TO, internetAddresses(message.to()));
            mime.setRecipients(Message.RecipientType.CC, internetAddresses(message.cc()));
            mime.setRecipients(Message.RecipientType.BCC, internetAddresses(message.bcc()));
            mime.setSubject(message.subject(), "UTF-8");
            setThreadHeaders(mime, message);
            if (message.html() != null && message.text() != null) {
                MimeMultipart alternative = new MimeMultipart("alternative");
                alternative.addBodyPart(textPart(message.text(), "plain"));
                alternative.addBodyPart(textPart(message.html(), "html"));
                mime.setContent(alternative);
            }
            else if (message.html() != null) {
                mime.setText(message.html(), "UTF-8", "html");
            }
            else {
                mime.setText(message.text() == null ? "" : message.text(), "UTF-8");
            }
            mime.saveChanges();
            return mime;
        }
        catch (java.io.UnsupportedEncodingException e) {
            throw new MessagingException("发件人编码失败：" + e.getMessage(), e);
        }
    }

    private void setThreadHeaders(MimeMessage mime, OutboundMailMessage message) throws MessagingException {
        if (message.inReplyTo() != null && !message.inReplyTo().isBlank()) {
            mime.setHeader("In-Reply-To", message.inReplyTo().trim());
        }
        if (message.references() != null && !message.references().isBlank()) {
            mime.setHeader("References", message.references().trim());
        }
    }

    private MimeBodyPart textPart(String content, String subtype) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        // setText(text, charset, subtype) 由 jakarta.mail 负责按声明字符集编码，避免中文乱码
        part.setText(content, "UTF-8", subtype);
        return part;
    }

    private InternetAddress[] internetAddresses(List<String> addresses) throws MessagingException {
        if (addresses == null || addresses.isEmpty()) {
            return new InternetAddress[0];
        }
        List<InternetAddress> result = new ArrayList<>(addresses.size());
        for (String address : addresses) {
            result.add(new InternetAddress(address));
        }
        return result.toArray(new InternetAddress[0]);
    }
}
