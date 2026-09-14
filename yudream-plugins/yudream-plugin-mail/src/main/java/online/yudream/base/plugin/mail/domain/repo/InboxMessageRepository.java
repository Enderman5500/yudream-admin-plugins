package online.yudream.base.plugin.mail.domain.repo;

import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.mail.domain.aggregate.InboxMessage;

/** 收件箱持久化邮件仓储（collection: mail-inbox-messages）。 */
public interface InboxMessageRepository {

    InboxMessage save(InboxMessage message);

    Optional<InboxMessage> findById(String id);

    /** 某地址 + 收件夹的持久化邮件，按时间新→旧。 */
    List<InboxMessage> findAllForFolder(String addressId, String folder);

    /** 某地址全部持久化邮件（清理用）。 */
    List<InboxMessage> findAllForAddress(String addressId);

    void delete(String id);
}
