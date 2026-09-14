package online.yudream.base.plugin.invite.interfaces.request;

/** 绑定邀请码载荷：不含任何归属字段，归属一律取请求 principal。 */
public record BindInviteRequest(String code, String remark) {
}
