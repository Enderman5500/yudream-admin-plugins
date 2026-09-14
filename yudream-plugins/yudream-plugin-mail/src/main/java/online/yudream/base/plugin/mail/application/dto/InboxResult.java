package online.yudream.base.plugin.mail.application.dto;

import java.util.List;
import online.yudream.base.plugin.mail.domain.aggregate.InboxMessage;

/**
 * 收件箱查询结果（读取持久化副本）。
 *
 * @param records      过滤 + 分页后的邮件列表（新→旧）
 * @param total        过滤后总数（分页用）
 * @param mailboxTotal 同步时读到的收件夹邮件总数（IMAP 不可用时为 -1）
 * @param fetched      本次同步实际拉取的信封数量
 * @param folder       实际使用的收件夹
 * @param synced       本次是否成功同步 IMAP（false = 读的是本地缓存）
 * @param truncated    同步是否因 limit 截断（fetched &lt; mailboxTotal）
 */
public record InboxResult(
        List<InboxMessage> records,
        long total,
        int mailboxTotal,
        int fetched,
        String folder,
        boolean synced,
        boolean truncated
) {
}
