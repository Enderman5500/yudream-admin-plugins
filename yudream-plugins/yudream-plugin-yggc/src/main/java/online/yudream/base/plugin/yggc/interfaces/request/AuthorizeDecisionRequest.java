package online.yudream.base.plugin.yggc.interfaces.request;

/**
 * 用户在授权确认页（authorize）作出允许 / 拒绝决定。
 */
public record AuthorizeDecisionRequest(
        Boolean approve,
        String profileId
) {
}
