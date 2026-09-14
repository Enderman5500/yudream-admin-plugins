package online.yudream.base.plugin.mail.application.query;

/** 管理端邮箱地址列表查询（enabled 为 null 表示不限启用状态）。 */
public record MailAddressQuery(String keyword, Boolean enabled, int page, int size) {

    public int safePage() {
        return Math.max(page, 1);
    }

    public int safeSize() {
        return Math.min(Math.max(size, 1), 100);
    }
}
