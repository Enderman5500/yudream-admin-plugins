package online.yudream.base.plugin.mail.domain.repo;

import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.mail.domain.aggregate.OutboundRecord;

/** 发信记录仓储（collection: mail-outbound-records，审计日志只增不改不删）。 */
public interface OutboundRecordRepository {

    OutboundRecord save(OutboundRecord record);

    Optional<OutboundRecord> findById(String id);

    /** 按发信时间倒序返回全量记录。 */
    List<OutboundRecord> findAll();

    long count();
}
