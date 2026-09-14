package online.yudream.base.plugin.yggc.interfaces.request;

/**
 * 用户在设备授权确认页（device）作出允许 / 拒绝决定。
 */
public record DeviceDecisionRequest(
        Boolean approve,
        String profileId,
        String userCode
) {
}
