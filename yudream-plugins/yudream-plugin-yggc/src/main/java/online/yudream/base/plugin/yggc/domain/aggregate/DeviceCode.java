package online.yudream.base.plugin.yggc.domain.aggregate;

import java.util.List;

/**
 * 设备授权码（RFC 8628）。status：pending / approved / denied。
 */
public record DeviceCode(
        String deviceCode,
        String userCode,
        String clientId,
        String scopes,
        String status,
        String userId,
        String profileId,
        Long expiresAt,
        int intervalSeconds,
        Long lastPolledAt
) {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String DENIED = "denied";
}
