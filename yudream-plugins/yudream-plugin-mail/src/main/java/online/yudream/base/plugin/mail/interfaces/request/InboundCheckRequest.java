package online.yudream.base.plugin.mail.interfaces.request;

import online.yudream.base.plugin.mail.application.cmd.InboundCheckCmd;

/** 入站核验请求体：对指定地址发起一次收信核验，验证码可留空（只按关键词匹配）。 */
public record InboundCheckRequest(String addressId, String verificationCode, Integer windowMinutes) {

    public InboundCheckCmd toCmd() {
        return new InboundCheckCmd(addressId, verificationCode, windowMinutes);
    }
}
