package online.yudream.base.plugin.mail.application.query;

/** 入站核验记录查询：addressId 与 status 为可选过滤。 */
public record InboundCheckQuery(String addressId, String status, int page, int size) {

    public int safePage() {
        return Math.max(page, 1);
    }

    public int safeSize() {
        return Math.min(Math.max(size, 1), 100);
    }
}
