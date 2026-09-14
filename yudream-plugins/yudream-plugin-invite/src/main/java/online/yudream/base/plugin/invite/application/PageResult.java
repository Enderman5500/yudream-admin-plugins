package online.yudream.base.plugin.invite.application;

import java.util.List;

/** 分页结果（页码从 1 开始）。 */
public record PageResult<T>(List<T> records, long total) {
}
