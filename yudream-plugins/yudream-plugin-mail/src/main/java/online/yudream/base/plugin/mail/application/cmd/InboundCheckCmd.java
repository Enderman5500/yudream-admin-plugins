package online.yudream.base.plugin.mail.application.cmd;

/** 入站核验入参：拉取该地址收件夹最近的邮件，按发件域、关键词与验证码匹配回信。 */
public record InboundCheckCmd(String addressId, String verificationCode, Integer windowMinutes) {
}
