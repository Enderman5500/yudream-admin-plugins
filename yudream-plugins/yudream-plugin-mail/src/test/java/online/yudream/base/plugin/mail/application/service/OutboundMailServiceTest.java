package online.yudream.base.plugin.mail.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import online.yudream.base.plugin.mail.application.NotFoundException;
import online.yudream.base.plugin.mail.application.cmd.MailAddressSaveCmd;
import online.yudream.base.plugin.mail.application.cmd.SendMailCmd;
import online.yudream.base.plugin.mail.application.dto.TransportTestResult;
import online.yudream.base.plugin.mail.application.port.MailTransportException;
import online.yudream.base.plugin.mail.application.port.SmtpClient;
import online.yudream.base.plugin.mail.application.query.OutboundRecordQuery;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.aggregate.OutboundRecord;
import online.yudream.base.plugin.mail.infrastructure.FakeDocumentStore;
import online.yudream.base.plugin.mail.infrastructure.FakeSecretStore;
import online.yudream.base.plugin.mail.infrastructure.repository.MailAddressDocumentRepository;
import online.yudream.base.plugin.mail.infrastructure.repository.OutboundRecordDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 自建 SMTP 发信用例：from 取值、真实状态、失败落审计、凭据与配置缺失的处理。 */
class OutboundMailServiceTest {

    private MailAddressService addressService;
    private OutboundRecordDocumentRepository records;
    private CapturingSmtpClient smtp;
    private OutboundMailService service;

    @BeforeEach
    void setUp() {
        FakeDocumentStore documents = new FakeDocumentStore();
        addressService = new MailAddressService(new MailAddressDocumentRepository(documents), new FakeSecretStore());
        records = new OutboundRecordDocumentRepository(documents);
        smtp = new CapturingSmtpClient();
        service = new OutboundMailService(addressService, records, smtp);
    }

    private MailAddress newAddress(String address, boolean withSmtp, boolean withPassword) {
        return addressService.create(new MailAddressSaveCmd(
                address, "对外事务", "NOTIFICATION", "", true,
                withSmtp ? new MailAddressSaveCmd.SmtpInput("smtp.example.com", 465, "SSL", address,
                        withPassword ? "smtp-pass" : "") : null,
                new MailAddressSaveCmd.ImapInput("imap.example.com", 993, "SSL", address, "imap-pass",
                        "INBOX", 20, List.of(), List.of())));
    }

    private static SendMailCmd sendCmd(String addressId, List<String> to) {
        return new SendMailCmd(addressId, to, List.of(), List.of(), "主题", "TEXT", "正文");
    }

    @Test
    void sendsThroughOwnSmtpWithAddressAsFrom() {
        MailAddress address = newAddress("ops@example.com", true, true);

        OutboundRecord record = service.send(sendCmd(address.id(), List.of("user@example.com")),
                OutboundRecord.Source.ADMIN, "1");

        assertEquals(OutboundRecord.Status.SENT, record.status());
        assertEquals("smtp.example.com", record.smtpHost());
        assertEquals("ops@example.com", smtp.lastSettings.fromAddress());
        assertEquals("对外事务", smtp.lastSettings.fromName());
        assertEquals("smtp-pass", smtp.lastSettings.password());
        assertEquals("正文", smtp.lastMessage.text());
        assertNull(smtp.lastMessage.html());
        assertEquals(1, records.count());
    }

    @Test
    void htmlBodyIsSentAsHtmlOnly() {
        MailAddress address = newAddress("ops@example.com", true, true);

        service.send(new SendMailCmd(address.id(), List.of("user@example.com"), List.of("cc@example.com"),
                List.of("bcc@example.com"), "主题", "HTML", "<p>正文</p>"), OutboundRecord.Source.ADMIN, "1");

        assertEquals("<p>正文</p>", smtp.lastMessage.html());
        assertNull(smtp.lastMessage.text());
        assertEquals(List.of("cc@example.com"), smtp.lastMessage.cc());
        assertEquals(List.of("bcc@example.com"), smtp.lastMessage.bcc());
    }

    @Test
    void batchPastedRecipientsAreSplitAndDeduplicated() {
        MailAddress address = newAddress("ops@example.com", true, true);

        OutboundRecord record = service.send(
                sendCmd(address.id(), List.of("a@example.com, b@example.com", "B@example.com")),
                OutboundRecord.Source.ADMIN, "1");

        assertEquals(List.of("a@example.com", "b@example.com"), record.to());
    }

    @Test
    void addressWithoutSmtpConfigCannotSend() {
        MailAddress address = newAddress("no-smtp@example.com", false, false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.send(sendCmd(address.id(), List.of("user@example.com")),
                        OutboundRecord.Source.ADMIN, "1"));
        assertTrue(error.getMessage().contains("尚未配置 SMTP"));
        assertNull(smtp.lastMessage);
        assertEquals(0, records.count());
    }

    @Test
    void missingPasswordIsRecordedAsFailure() {
        MailAddress address = newAddress("no-pass@example.com", true, false);

        OutboundRecord record = service.send(sendCmd(address.id(), List.of("user@example.com")),
                OutboundRecord.Source.ADMIN, "1");

        assertEquals(OutboundRecord.Status.FAILED, record.status());
        assertTrue(record.errorMessage().contains("SMTP 密码"));
        assertNull(smtp.lastMessage);
        assertEquals(1, records.count());
    }

    @Test
    void emptyRecipientsAreRejectedWithoutSending() {
        MailAddress address = newAddress("ops@example.com", true, true);

        assertThrows(IllegalArgumentException.class,
                () -> service.send(sendCmd(address.id(), List.of("  ")), OutboundRecord.Source.ADMIN, "1"));
        assertNull(smtp.lastMessage);
        assertEquals(0, records.count());
    }

    @Test
    void transportFailureIsRecordedAsFailure() {
        MailAddress address = newAddress("ops@example.com", true, true);
        smtp.failure = new MailTransportException("SMTP 投递失败：550 mailbox unavailable");

        OutboundRecord record = service.send(sendCmd(address.id(), List.of("user@example.com")),
                OutboundRecord.Source.ADMIN, "1");

        assertEquals(OutboundRecord.Status.FAILED, record.status());
        assertTrue(record.errorMessage().contains("550"));
        assertEquals(1, records.count());
    }

    @Test
    void testSmtpReportsSuccessAndFailure() {
        MailAddress address = newAddress("ops@example.com", true, true);

        TransportTestResult ok = service.testSmtp(address.id());
        assertTrue(ok.ok());
        assertTrue(ok.message().contains("smtp.example.com:465"));

        smtp.failure = new MailTransportException("SMTP 连接/认证失败：认证失败，请检查用户名与密码/授权码");
        TransportTestResult failed = service.testSmtp(address.id());
        assertFalse(failed.ok());
        assertTrue(failed.message().contains("认证失败"));

        MailAddress withoutSmtp = newAddress("no-smtp@example.com", false, false);
        assertFalse(service.testSmtp(withoutSmtp.id()).ok());
    }

    @Test
    void myPageOnlyReturnsOwnRecords() {
        MailAddress address = newAddress("ops@example.com", true, true);
        service.send(sendCmd(address.id(), List.of("user@example.com")), OutboundRecord.Source.USER, "7");
        service.send(sendCmd(address.id(), List.of("other@example.com")), OutboundRecord.Source.USER, "8");

        var mine = service.myPage("7", 1, 10);

        assertEquals(1, mine.total());
        assertEquals("7", mine.records().get(0).operatorUserId());
    }

    @Test
    void adminPageFiltersByStatusAndAddress() {
        MailAddress address = newAddress("ops@example.com", true, true);
        service.send(sendCmd(address.id(), List.of("user@example.com")), OutboundRecord.Source.ADMIN, "1");
        smtp.failure = new MailTransportException("SMTP 投递失败：550");
        service.send(sendCmd(address.id(), List.of("user@example.com")), OutboundRecord.Source.ADMIN, "1");

        assertEquals(1, service.adminPage(new OutboundRecordQuery(null, null, "FAILED", 1, 10)).total());
        assertEquals(2, service.adminPage(new OutboundRecordQuery(null, address.id(), null, 1, 10)).total());
        assertEquals(2, service.adminPage(new OutboundRecordQuery("user@example.com", null, null, 1, 10)).total());
    }

    @Test
    void requireOwnedHidesOtherUsersRecords() {
        MailAddress address = newAddress("ops@example.com", true, true);
        OutboundRecord record = service.send(sendCmd(address.id(), List.of("user@example.com")),
                OutboundRecord.Source.USER, "7");

        assertNotNull(service.requireOwned(record.id(), "7"));
        assertThrows(NotFoundException.class, () -> service.requireOwned(record.id(), "8"));
    }

    @Test
    void replyThreadHeadersArePassedToSmtp() {
        MailAddress address = newAddress("ops@example.com", true, true);
        SendMailCmd reply = new SendMailCmd(address.id(), List.of("user@example.com"), List.of(), List.of(),
                "Re: 主题", "TEXT", "回复正文", "<abc@mail.example.com>", "<abc@mail.example.com> <def@mail.example.com>");

        OutboundRecord record = service.send(reply, OutboundRecord.Source.ADMIN, "1");

        assertEquals(OutboundRecord.Status.SENT, record.status());
        assertEquals("<abc@mail.example.com>", smtp.lastMessage.inReplyTo());
        assertEquals("<abc@mail.example.com> <def@mail.example.com>", smtp.lastMessage.references());
    }

    @Test
    void blankThreadHeadersAreNormalizedToNull() {
        MailAddress address = newAddress("ops@example.com", true, true);
        SendMailCmd plain = new SendMailCmd(address.id(), List.of("user@example.com"), List.of(), List.of(),
                "主题", "TEXT", "正文", "  ", "");

        service.send(plain, OutboundRecord.Source.ADMIN, "1");

        assertNull(smtp.lastMessage.inReplyTo());
        assertNull(smtp.lastMessage.references());
    }

    private static final class CapturingSmtpClient implements SmtpClient {

        private SmtpSettings lastSettings;
        private OutboundMailMessage lastMessage;
        private RuntimeException failure;

        @Override
        public void testConnection(SmtpSettings settings) {
            if (failure != null) {
                throw failure;
            }
            lastSettings = settings;
        }

        @Override
        public void send(SmtpSettings settings, OutboundMailMessage message) {
            if (failure != null) {
                throw failure;
            }
            lastSettings = settings;
            lastMessage = message;
        }
    }
}
