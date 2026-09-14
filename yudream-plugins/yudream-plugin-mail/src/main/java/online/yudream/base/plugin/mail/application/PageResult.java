package online.yudream.base.plugin.mail.application;

import java.util.List;

/** 分页结果（页码从 1 开始）。 */
public record PageResult<T>(List<T> records, long total) {

    /** 对已加载集合做内存分页（插件规模有限，仓储全量扫描后统一切片）。 */
    public static <T> PageResult<T> slice(List<T> all, int page, int size) {
        List<T> safeList = all == null ? List.of() : all;
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min((safePage - 1) * safeSize, safeList.size());
        int to = Math.min(from + safeSize, safeList.size());
        return new PageResult<>(List.copyOf(safeList.subList(from, to)), safeList.size());
    }
}
