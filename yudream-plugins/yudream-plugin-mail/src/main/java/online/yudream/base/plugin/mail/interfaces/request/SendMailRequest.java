package online.yudream.base.plugin.mail.interfaces.request;

import java.util.List;
import online.yudream.base.plugin.mail.application.cmd.SendMailCmd;

/** 发信请求体。收件人/抄送/密送可直接提交字符串数组（每项也可含逗号分隔的多个地址）。 */
public record SendMailRequest(
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

    public SendMailCmd toCmd() {
        return new SendMailCmd(addressId, to, cc, bcc, subject, bodyType, body, inReplyTo, references);
    }
}
