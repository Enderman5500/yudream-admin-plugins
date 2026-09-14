package online.yudream.base.plugin.mail.infrastructure.mail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import online.yudream.base.plugin.mail.application.port.ImapClient;

/** MIME 解析：信封字段（主题/收发件人）与正文/附件元数据抽取。 */
final class MimeMessageSupport {

    /** 正文（文本与 HTML 各）最大字符数，避免超大邮件撑爆内存与响应体。 */
    static final int MAX_BODY_CHARS = 200_000;
    private static final int MAX_DEPTH = 12;

    private MimeMessageSupport() {
    }

    /** 原始 Message-ID（含尖括号），供回复线程头使用；缺失返回空串。 */
    static String messageId(Message message) {
        try {
            String[] ids = message.getHeader("Message-ID");
            if (ids == null) {
                ids = message.getHeader("Message-Id");
            }
            return ids == null || ids.length == 0 ? "" : ids[0].trim();
        }
        catch (MessagingException e) {
            return "";
        }
    }

    /** 解码后的正文与附件元数据；truncated 表示正文被截断。 */
    record Bodies(String text, String html, List<ImapClient.MailAttachment> attachments, boolean truncated) {
    }

    /** 解码后的主题；失败返回空串。 */
    static String subject(Message message) {
        try {
            String subject = message.getSubject();
            return subject == null ? "" : MimeUtility.decodeText(subject).trim();
        }
        catch (MessagingException | java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    static String formatAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        List<String> result = new ArrayList<>(addresses.length);
        for (Address address : addresses) {
            if (address instanceof InternetAddress internet) {
                result.add(internet.toUnicodeString());
            }
            else if (address != null) {
                result.add(String.valueOf(address));
            }
        }
        return String.join(", ", result);
    }

    static List<String> addressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>(addresses.length);
        for (Address address : addresses) {
            if (address instanceof InternetAddress internet) {
                result.add(internet.toUnicodeString());
            }
            else if (address != null) {
                result.add(String.valueOf(address));
            }
        }
        return List.copyOf(result);
    }

    /** 抽取正文与附件元数据；附件只记录名称/类型/大小，不下载内容。 */
    static Bodies bodies(Part part) {
        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        List<ImapClient.MailAttachment> attachments = new ArrayList<>();
        boolean[] truncated = {false};
        try {
            walk(part, text, html, attachments, truncated, 0);
        }
        catch (MessagingException | IOException e) {
            // 解析失败时保留已抽到的部分，不让一次坏邮件让整个详情接口失败
            attachments.add(new ImapClient.MailAttachment("（部分内容解析失败：" + MailErrors.describe(e) + "）", "text/plain", 0L));
        }
        return new Bodies(text.toString(), html.toString(), List.copyOf(attachments), truncated[0]);
    }

    private static void walk(
            Part part,
            StringBuilder text,
            StringBuilder html,
            List<ImapClient.MailAttachment> attachments,
            boolean[] truncated,
            int depth) throws MessagingException, IOException {
        if (part == null || depth > MAX_DEPTH) {
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int index = 0; index < multipart.getCount(); index++) {
                    walk(multipart.getBodyPart(index), text, html, attachments, truncated, depth + 1);
                }
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nested) {
                walk(nested, text, html, attachments, truncated, depth + 1);
            }
            return;
        }
        if (part.isMimeType("text/plain") && !isAttachment(part)) {
            append(text, readText(part), truncated);
            return;
        }
        if (part.isMimeType("text/html") && !isAttachment(part)) {
            append(html, readText(part), truncated);
            return;
        }
        if (part.isMimeType("text/*")) {
            append(text, readText(part), truncated);
            return;
        }
        String contentType = part.getContentType() == null ? "" : part.getContentType();
        String name = attachmentName(part);
        attachments.add(new ImapClient.MailAttachment(name, baseType(contentType), Math.max(part.getSize(), 0)));
    }

    private static boolean isAttachment(Part part) throws MessagingException {
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            return true;
        }
        String fileName = part.getFileName();
        return fileName != null && !fileName.isBlank();
    }

    private static String attachmentName(Part part) {
        try {
            String fileName = part.getFileName();
            if (fileName != null && !fileName.isBlank()) {
                return MimeUtility.decodeText(fileName).trim();
            }
            String contentType = baseType(part.getContentType());
            return contentType.isEmpty() ? "未命名附件" : contentType;
        }
        catch (MessagingException | java.io.UnsupportedEncodingException ignored) {
            return "未命名附件";
        }
    }

    private static String baseType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String value = semicolon > 0 ? contentType.substring(0, semicolon) : contentType;
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void append(StringBuilder target, String content, boolean[] truncated) {
        if (content == null || content.isEmpty()) {
            return;
        }
        int remaining = MAX_BODY_CHARS - target.length();
        if (remaining <= 0) {
            truncated[0] = true;
            return;
        }
        if (content.length() > remaining) {
            target.append(content, 0, remaining);
            truncated[0] = true;
            return;
        }
        target.append(content);
    }

    private static String readText(Part part) throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof InputStream stream) {
            Charset charset = charsetOf(part.getContentType());
            try (Reader reader = new InputStreamReader(stream, charset)) {
                char[] buffer = new char[8192];
                StringBuilder result = new StringBuilder();
                int read;
                while ((read = reader.read(buffer)) > 0 && result.length() < MAX_BODY_CHARS) {
                    result.append(buffer, 0, read);
                }
                return result.toString();
            }
        }
        return content == null ? "" : String.valueOf(content);
    }

    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("charset\\s*=\\s*\"?([^;\"\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(MimeUtility.javaCharset(matcher.group(1)));
                }
                catch (RuntimeException ignored) {
                    // 未知字符集回退 UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
