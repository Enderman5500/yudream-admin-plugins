package online.yudream.base.plugin.invite.infrastructure;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.invite.domain.InviteBinding;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

/** 邀请码绑定仓储（collection: invite-bindings）。 */
public class InviteBindingRepository extends AbstractDocumentRepository<InviteBinding> {

    public InviteBindingRepository(PluginDocumentStore documents) {
        super(documents, "invite-bindings");
    }

    public InviteBinding save(InviteBinding binding) {
        save(binding.id(), binding, InviteBinding::toDocument);
        return binding;
    }

    public Optional<InviteBinding> find(String id) {
        return findDoc(id).map(doc -> InviteBinding.from(id, doc));
    }

    public List<InviteBinding> findAll() {
        return scanAllDocs().stream()
                .map(doc -> InviteBinding.from(String.valueOf(doc.get("id")), doc))
                .sorted(Comparator.comparingLong(InviteBinding::boundAt).reversed())
                .toList();
    }

    public List<InviteBinding> findByOwner(String ownerId) {
        return findAll().stream()
                .filter(binding -> ownerId.equals(binding.ownerId()))
                .toList();
    }

    public Optional<InviteBinding> findByCode(String code) {
        return findAll().stream()
                .filter(binding -> binding.code().equals(code))
                .findFirst();
    }

    public void delete(String id) {
        super.delete(id);
    }
}
