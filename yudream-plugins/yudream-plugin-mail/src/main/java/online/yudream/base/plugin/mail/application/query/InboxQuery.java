package online.yudream.base.plugin.mail.application.query;

/**
 * 收件箱查询。{@code limit} 只约束「同步时从 IMAP 拉取的信封数量」；
 * 读取走持久化副本，按 {@code page}/{@code size} 分页，过滤条件（发件域、关键词）只依赖信封字段。
 */
public record InboxQuery(
        String addressId,
        String folder,
        Integer limit,
        Integer page,
        Integer size,
        String fromDomain,
        String keyword
) {

    public int safeLimit() {
        return Math.min(Math.max(limit == null ? 0 : limit, 1), 50);
    }

    public int safePage() {
        return Math.max(page == null || page < 1 ? 1 : page, 1);
    }

    public int safeSize() {
        return Math.min(Math.max(size == null || size < 1 ? 20 : size, 1), 100);
    }
}
