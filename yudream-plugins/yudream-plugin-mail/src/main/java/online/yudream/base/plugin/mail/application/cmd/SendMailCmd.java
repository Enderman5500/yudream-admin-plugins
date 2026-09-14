package online.yudream.base.plugin.mail.application.cmd;

import java.util.List;
import online.yudream.base.plugin.mail.domain.aggregate.OutboundRecord;

/** 发信入参。收件人/抄送/密送为字符串数组，正文类型为 TEXT 或 HTML；inReplyTo/references 为回复线程头（可空）。 */
public record SendMailCmd(
        String addressId,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String bodyType,
        String body,
        String inReplyTo,
        String references
) {

    public SendMailCmd(String addressId, List<String> to, List<String> cc, List<String> bcc,
                       String subject, String bodyType, String body) {
        this(addressId, to, cc, bcc, subject, bodyType, body, null, null);
    }

    public OutboundRecord.BodyType bodyTypeValue() {
        if (bodyType != null && "HTML".equalsIgnoreCase(bodyType.trim())) {
            return OutboundRecord.BodyType.HTML;
        }
        return OutboundRecord.BodyType.TEXT;
    }

    /** 规范化线程头：去掉首尾空白，空串归一为 null。 */
    public String threadHeader(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
