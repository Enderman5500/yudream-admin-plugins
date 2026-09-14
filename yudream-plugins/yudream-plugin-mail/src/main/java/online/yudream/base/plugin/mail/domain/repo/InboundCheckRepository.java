package online.yudream.base.plugin.mail.domain.repo;

import java.util.List;
import online.yudream.base.plugin.mail.domain.aggregate.InboundCheck;

/** 入站核验记录仓储（collection: mail-inbound-checks，审计日志只增不改不删）。 */
public interface InboundCheckRepository {

    InboundCheck save(InboundCheck check);

    /** 按核验时间倒序返回全量记录。 */
    List<InboundCheck> findAll();

    long count();
}
