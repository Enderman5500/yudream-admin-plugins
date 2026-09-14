package online.yudream.base.plugin.mail.infrastructure.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.mail.domain.aggregate.InboxMessage;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.repo.InboxMessageRepository;
import online.yudream.base.plugin.mail.infrastructure.AbstractDocumentRepository;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

/** 收件箱持久化邮件仓储实现（collection: mail-inbox-messages）。 */
public class InboxMessageDocumentRepository extends AbstractDocumentRepository<InboxMessage>
        implements InboxMessageRepository {

    private static final String COLLECTION = "mail-inbox-messages";

    public InboxMessageDocumentRepository(PluginDocumentStore documents) {
        super(documents, COLLECTION);
    }

    @Override
    public InboxMessage save(InboxMessage message) {
        save(message.id(), message, InboxMessage::toDocument);
        return message;
    }

    @Override
    public Optional<InboxMessage> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return findDoc(id.trim()).map(document -> InboxMessage.from(id.trim(), document));
    }

    @Override
    public List<InboxMessage> findAllForFolder(String addressId, String folder) {
        String targetFolder = folder == null || folder.isBlank()
                ? MailAddress.ImapConfig.DEFAULT_FOLDER
                : folder.trim();
        return scanAllDocs().stream()
                .map(document -> InboxMessage.from(idOf(document), document))
                .filter(message -> message.addressId().equals(addressId)
                        && message.folder().equals(targetFolder))
                .sorted(Comparator.comparingLong(InboxMessage::sentAt).reversed())
                .toList();
    }

    @Override
    public List<InboxMessage> findAllForAddress(String addressId) {
        return scanAllDocs().stream()
                .map(document -> InboxMessage.from(idOf(document), document))
                .filter(message -> message.addressId().equals(addressId))
                .toList();
    }
}
