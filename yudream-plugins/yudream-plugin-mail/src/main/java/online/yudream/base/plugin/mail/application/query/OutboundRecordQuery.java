package online.yudream.base.plugin.mail.application.query;

/** 发信记录查询：keyword 匹配收件人/主题/发件地址，addressId 与 status 为可选过滤。 */
public record OutboundRecordQuery(
        String keyword,
        String addressId,
        String status,
        int page,
        int size
) {

    public int safePage() {
        return Math.max(page, 1);
    }

    public int safeSize() {
        return Math.min(Math.max(size, 1), 100);
    }
}
