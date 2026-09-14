package online.yudream.base.plugin.mail.infrastructure.repository;

import java.util.Comparator;
import java.util.List;
import online.yudream.base.plugin.mail.domain.aggregate.InboundCheck;
import online.yudream.base.plugin.mail.domain.repo.InboundCheckRepository;
import online.yudream.base.plugin.mail.infrastructure.AbstractDocumentRepository;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

/** 入站核验记录仓储实现（collection: mail-inbound-checks）。 */
public class InboundCheckDocumentRepository extends AbstractDocumentRepository<InboundCheck>
        implements InboundCheckRepository {

    private static final String COLLECTION = "mail-inbound-checks";

    public InboundCheckDocumentRepository(PluginDocumentStore documents) {
        super(documents, COLLECTION);
    }

    @Override
    public InboundCheck save(InboundCheck check) {
        save(check.id(), check, InboundCheck::toDocument);
        return check;
    }

    @Override
    public List<InboundCheck> findAll() {
        return scanAllDocs().stream()
                .map(document -> InboundCheck.from(idOf(document), document))
                .sorted(Comparator.comparingLong(InboundCheck::createdAt).reversed())
                .toList();
    }
}
