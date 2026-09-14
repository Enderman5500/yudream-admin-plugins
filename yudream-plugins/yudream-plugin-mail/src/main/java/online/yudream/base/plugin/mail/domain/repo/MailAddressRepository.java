package online.yudream.base.plugin.mail.domain.repo;

import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;

/** 邮箱地址仓储（collection: mail-addresses）。 */
public interface MailAddressRepository {

    MailAddress save(MailAddress address);

    Optional<MailAddress> findById(String id);

    /** 按归一化后的邮箱地址精确查找（大小写不敏感）。 */
    Optional<MailAddress> findByAddress(String address);

    List<MailAddress> findAll();

    long count();

    void delete(String id);
}
