package online.yudream.base.plugin.mail.application.service;

import java.util.List;
import java.util.Locale;
import online.yudream.base.plugin.mail.application.NotFoundException;
import online.yudream.base.plugin.mail.application.PageResult;
import online.yudream.base.plugin.mail.application.cmd.SendMailCmd;
import online.yudream.base.plugin.mail.application.dto.TransportTestResult;
import online.yudream.base.plugin.mail.application.port.MailTransportException;
import online.yudream.base.plugin.mail.application.port.SmtpClient;
import online.yudream.base.plugin.mail.application.query.OutboundRecordQuery;
import online.yudream.base.plugin.mail.application.support.MailSettingsFactory;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.aggregate.OutboundRecord;
import online.yudream.base.plugin.mail.domain.repo.OutboundRecordRepository;
import online.yudream.base.plugin.mail.domain.support.Ids;

/**
 * 发信用例：走插件自建 SMTP 客户端，同步得到真实投递结果。
 *
 * <p>SMTP 服务器接收即记为 {@code SENT}（不代表对方已读）；连接、认证或投递报错记为 {@code FAILED}
 * 并保留原因。两种结果都会落审计记录，便于排查“到底发出去没有”。</p>
 */
public class OutboundMailService {

    private final MailAddressService addressService;
    private final OutboundRecordRepository records;
    private final SmtpClient smtp;

    public OutboundMailService(
            MailAddressService addressService,
            OutboundRecordRepository records,
            SmtpClient smtp) {
        this.addressService = addressService;
        this.records = records;
        this.smtp = smtp;
    }

    /** 发信：校验 → 自建 SMTP 投递 → 落审计记录（失败也落库）。 */
    public OutboundRecord send(SendMailCmd cmd, OutboundRecord.Source source, String operatorUserId) {
        if (cmd == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }
        MailAddress address = addressService.requireEnabled(cmd.addressId());
        if (!address.smtp().configured()) {
            throw new IllegalArgumentException("该邮箱地址尚未配置 SMTP 服务器与用户名：" + address.address());
        }
        List<String> to = MailAddress.normalizeRecipients(cmd.to(), "收件人");
        List<String> cc = MailAddress.normalizeRecipients(cmd.cc(), "抄送");
        List<String> bcc = MailAddress.normalizeRecipients(cmd.bcc(), "密送");
        OutboundRecord.BodyType bodyType = cmd.bodyTypeValue();
        // 先构造草稿完成全部字段校验，避免非法内容进入投递流程
        OutboundRecord draft = new OutboundRecord(Ids.newId(), address.id(), address.address(),
                address.smtp().host(), to, cc, bcc, cmd.subject(), bodyType, cmd.body(), "",
                OutboundRecord.Status.SENT, "", source, operatorUserId, 0L);
        try {
            String password = addressService.requireSmtpPassword(address);
            smtp.send(MailSettingsFactory.smtp(address, password), new SmtpClient.OutboundMailMessage(
                    draft.to(),
                    draft.cc(),
                    draft.bcc(),
                    draft.subject(),
                    bodyType == OutboundRecord.BodyType.TEXT ? draft.body() : null,
                    bodyType == OutboundRecord.BodyType.HTML ? draft.body() : null,
                    cmd.threadHeader(cmd.inReplyTo()),
                    cmd.threadHeader(cmd.references())));
            return records.save(draft);
        }
        catch (MailTransportException | IllegalArgumentException e) {
            return records.save(draft.withFailure(e.getMessage()));
        }
    }

    /** SMTP 连通性测试：只连接+认证，不投递。 */
    public TransportTestResult testSmtp(String addressId) {
        MailAddress address = addressService.requireExisting(addressId);
        if (!address.smtp().configured()) {
            return TransportTestResult.failed("尚未配置 SMTP 服务器与用户名");
        }
        try {
            String password = addressService.requireSmtpPassword(address);
            smtp.testConnection(MailSettingsFactory.smtp(address, password));
            return TransportTestResult.ok(
                    "SMTP 连接与认证成功：" + address.smtp().host() + ":" + address.smtp().port(), null);
        }
        catch (MailTransportException | IllegalArgumentException e) {
            return TransportTestResult.failed(e.getMessage());
        }
    }

    /** 管理端：跨用户全量发信记录。 */
    public PageResult<OutboundRecord> adminPage(OutboundRecordQuery query) {
        OutboundRecordQuery safeQuery = query == null ? new OutboundRecordQuery(null, null, null, 1, 10) : query;
        List<OutboundRecord> matched = records.findAll().stream()
                .filter(record -> matches(record, safeQuery))
                .toList();
        return PageResult.slice(matched, safeQuery.safePage(), safeQuery.safeSize());
    }

    /** 用户端：仅本人发起的记录，归属只来自 principal。 */
    public PageResult<OutboundRecord> myPage(String operatorUserId, int page, int size) {
        List<OutboundRecord> mine = records.findAll().stream()
                .filter(record -> record.operatorUserId().equals(operatorUserId))
                .toList();
        return PageResult.slice(mine, page, size);
    }

    public OutboundRecord requireById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("发信记录 ID 不能为空");
        }
        return records.findById(id.trim())
                .orElseThrow(() -> new NotFoundException("发信记录不存在"));
    }

    /** 管理端详情可见任意记录；用户端详情只允许本人记录。 */
    public OutboundRecord requireOwned(String id, String operatorUserId) {
        OutboundRecord record = requireById(id);
        if (!record.operatorUserId().equals(operatorUserId)) {
            throw new NotFoundException("发信记录不存在");
        }
        return record;
    }

    public long count() {
        return records.count();
    }

    private boolean matches(OutboundRecord record, OutboundRecordQuery query) {
        if (query.addressId() != null && !query.addressId().isBlank()
                && !query.addressId().trim().equals(record.addressId())) {
            return false;
        }
        if (query.status() != null && !query.status().isBlank()
                && !query.status().trim().equalsIgnoreCase(record.status().name())) {
            return false;
        }
        String keyword = query.keyword();
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return record.fromAddress().contains(needle)
                || record.subject().toLowerCase(Locale.ROOT).contains(needle)
                || record.to().stream().anyMatch(item -> item.contains(needle))
                || record.operatorUserId().toLowerCase(Locale.ROOT).contains(needle);
    }
}
