package online.yudream.base.plugin.mail.application.port;

import java.util.List;
import online.yudream.base.plugin.mail.domain.aggregate.InboxMessage;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;

/** IMAP 收信端口（实现见 infrastructure.mail.JakartaImapClient）。列表/正文读取只读；标记与删除是显式的写操作。 */
public interface ImapClient {

    /** 连接并打开收件夹，返回邮件总数；失败抛 {@link MailTransportException}。 */
    int testConnection(ImapSettings settings, String folder);

    /** 列出可用收件夹（INBOX 优先）。 */
    List<String> listFolders(ImapSettings settings);

    /** 拉取最近 {@code limit} 封邮件的信封信息（新→旧），并给出收件夹邮件总数。 */
    InboundMailPage fetchSummaries(ImapSettings settings, String folder, int limit);

    /** 按 UID 读取单封邮件正文与附件元数据。 */
    InboundMailDetail fetchDetail(ImapSettings settings, String folder, String uid);

    /** 按 UID 标记/取消标记已读（\\Seen）。需要可写打开收件夹。 */
    void markSeen(ImapSettings settings, String folder, String uid, boolean seen);

    /** 按 UID 删除邮件：设置 \\Deleted 并立即 expunge，服务端永久移除（不经过回收站）。 */
    void delete(ImapSettings settings, String folder, String uid);

    /** 信封分页结果。 */
    record InboundMailPage(List<InboundMailSummary> records, int mailboxTotal) {
    }

    /** 收信连接参数（含解密后的密码，仅在调用期间存在）。 */
    record ImapSettings(
            String host,
            int port,
            MailAddress.Security security,
            String username,
            String password
    ) {
    }

    /** 邮件列表项（只读信封，不下载正文）。 */
    record InboundMailSummary(
            String uid,
            String subject,
            String from,
            List<String> to,
            long sentAt,
            long size,
            boolean seen
    ) {
    }

    /** 邮件详情：正文文本/HTML 与附件元数据（不下载附件内容）。messageId 供回复线程头使用。 */
    record InboundMailDetail(
            String uid,
            String subject,
            String from,
            List<String> to,
            List<String> cc,
            long sentAt,
            long size,
            boolean seen,
            String text,
            String html,
            List<MailAttachment> attachments,
            boolean truncated,
            String messageId
    ) implements InboxMessage.ImapClientDetailSource {
    }

    /** 附件元数据。 */
    record MailAttachment(String name, String contentType, long size)
            implements InboxMessage.ImapClientDetailSource.AttachmentSource {
    }
}
