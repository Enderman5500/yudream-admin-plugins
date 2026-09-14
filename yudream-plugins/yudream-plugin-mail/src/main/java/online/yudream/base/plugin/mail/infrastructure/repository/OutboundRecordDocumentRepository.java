package online.yudream.base.plugin.mail.infrastructure.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.mail.domain.aggregate.OutboundRecord;
import online.yudream.base.plugin.mail.domain.repo.OutboundRecordRepository;
import online.yudream.base.plugin.mail.infrastructure.AbstractDocumentRepository;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

/** 发信记录仓储实现（collection: mail-outbound-records）。 */
public class OutboundRecordDocumentRepository extends AbstractDocumentRepository<OutboundRecord>
        implements OutboundRecordRepository {

    private static final String COLLECTION = "mail-outbound-records";

    public OutboundRecordDocumentRepository(PluginDocumentStore documents) {
        super(documents, COLLECTION);
    }

    @Override
    public OutboundRecord save(OutboundRecord record) {
        save(record.id(), record, OutboundRecord::toDocument);
        return record;
    }

    @Override
    public Optional<OutboundRecord> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return findDoc(id.trim()).map(document -> OutboundRecord.from(id.trim(), document));
    }

    @Override
    public List<OutboundRecord> findAll() {
        return scanAllDocs().stream()
                .map(document -> OutboundRecord.from(idOf(document), document))
                .sorted(Comparator.comparingLong(OutboundRecord::createdAt).reversed())
                .toList();
    }
}
