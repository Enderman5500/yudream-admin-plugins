package online.yudream.base.plugin.mail.infrastructure.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.repo.MailAddressRepository;
import online.yudream.base.plugin.mail.infrastructure.AbstractDocumentRepository;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

/** 邮箱地址仓储实现（collection: mail-addresses）。 */
public class MailAddressDocumentRepository extends AbstractDocumentRepository<MailAddress>
        implements MailAddressRepository {

    private static final String COLLECTION = "mail-addresses";

    public MailAddressDocumentRepository(PluginDocumentStore documents) {
        super(documents, COLLECTION);
    }

    @Override
    public MailAddress save(MailAddress address) {
        save(address.id(), address, MailAddress::toDocument);
        return address;
    }

    @Override
    public Optional<MailAddress> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return findDoc(id.trim()).map(document -> MailAddress.from(id.trim(), document));
    }

    @Override
    public Optional<MailAddress> findByAddress(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        String normalized = address.trim().toLowerCase(java.util.Locale.ROOT);
        return findAll().stream()
                .filter(item -> item.address().equals(normalized))
                .findFirst();
    }

    @Override
    public List<MailAddress> findAll() {
        return scanAllDocs().stream()
                .map(document -> MailAddress.from(idOf(document), document))
                .sorted(Comparator.comparing(MailAddress::address))
                .toList();
    }

    @Override
    public void delete(String id) {
        super.delete(id);
    }
}
