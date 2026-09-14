package online.yudream.base.plugin.mail.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import online.yudream.base.plugin.mail.application.cmd.InboundCheckCmd;
import online.yudream.base.plugin.mail.application.cmd.MailAddressSaveCmd;
import online.yudream.base.plugin.mail.application.port.MailTransportException;
import online.yudream.base.plugin.mail.application.query.InboundCheckQuery;
import online.yudream.base.plugin.mail.domain.aggregate.InboundCheck;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.infrastructure.FakeDocumentStore;
import online.yudream.base.plugin.mail.infrastructure.FakeSecretStore;
import online.yudream.base.plugin.mail.infrastructure.repository.InboundCheckDocumentRepository;
import online.yudream.base.plugin.mail.infrastructure.repository.InboxMessageDocumentRepository;
import online.yudream.base.plugin.mail.infrastructure.repository.MailAddressDocumentRepository;
import online.yudream.base.plugin.mail.support.FakeImapClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 入站核验用例：未配置降级、发件域/关键词/验证码匹配、命中摘要与脱敏落库。 */
class InboundCheckServiceTest {

    private static final long NOW = System.currentTimeMillis();

    private MailAddressService addressService;
    private FakeImapClient imap;
    private InboundCheckDocumentRepository checks;
    private InboundCheckService service;
    private MailAddress address;

    @BeforeEach
    void setUp() {
        FakeDocumentStore documents = new FakeDocumentStore();
        addressService = new MailAddressService(new MailAddressDocumentRepository(documents), new FakeSecretStore());
        checks = new InboundCheckDocumentRepository(documents);
        imap = new FakeImapClient();
        service = new InboundCheckService(addressService, new InboxService(addressService, imap, new InboxMessageDocumentRepository(documents)), checks);
        address = addressService.create(new MailAddressSaveCmd(
                "ops@example.com", null, "VERIFICATION", "", true,
                new MailAddressSaveCmd.SmtpInput("smtp.example.com", 465, "SSL", "ops@example.com", "p"),
                new MailAddressSaveCmd.ImapInput("imap.example.com", 993, "SSL", "ops@example.com", "ip",
                        "INBOX", 20, List.of("partner.com"), List.of("验证码"))));
    }

    private void givenMail(String uid, String from, String text) {
        givenMail(uid, from, "验证码邮件", text);
    }

    private void givenMail(String uid, String from, String subject, String text) {
        imap.withSummaries(List.of(FakeImapClient.summary(uid, subject, from, NOW - 60_000L)), 1);
        imap.withDetail(uid, FakeImapClient.detail(uid, subject, from, NOW - 60_000L, text));
    }

    @Test
    void unavailableWhenImapNotConfigured() {
        MailAddress withoutImap = addressService.create(new MailAddressSaveCmd(
                "no-imap@example.com", null, "OTHER", "", true, null, null));

        InboundCheck record = service.check(new InboundCheckCmd(withoutImap.id(), "123456", 30), "1");

        assertEquals(InboundCheck.UNAVAILABLE, record.status());
        assertTrue(record.message().contains("尚未配置 IMAP"));
        assertEquals(1, checks.count());
    }

    @Test
    void notFoundWhenNoCandidateInWindow() {
        imap.withSummaries(List.of(), 0);

        InboundCheck record = service.check(new InboundCheckCmd(address.id(), "123456", 30), "1");

        assertEquals(InboundCheck.NOT_FOUND, record.status());
        assertTrue(record.message().contains("没有符合发件域条件"));
        assertTrue(record.matchedUid().isEmpty());
    }

    @Test
    void matchedWhenCodeMatchesIgnoringWhitespaceAndCase() {
        givenMail("11", "noreply@partner.com", "您的验 证码 是 ab12CD，5 分钟内有效");

        InboundCheck record = service.check(new InboundCheckCmd(address.id(), "AB12cd", 30), "9");

        assertEquals(InboundCheck.MATCHED, record.status());
        assertEquals("11", record.matchedUid());
        assertEquals("验证码邮件", record.matchedSubject());
        assertEquals("noreply@partner.com", record.matchedFrom());
        assertEquals(NOW - 60_000L, record.matchedAt());
        // 验证码脱敏：保留首尾字符，中间以 * 代替
        assertEquals("A****d", record.codeMask());
        assertEquals("9", record.operatorUserId());
    }

    @Test
    void notFoundWhenCodeDoesNotMatch() {
        givenMail("11", "noreply@partner.com", "您的验证码是 999999");

        InboundCheck record = service.check(new InboundCheckCmd(address.id(), "123456", 30), "1");

        assertEquals(InboundCheck.NOT_FOUND, record.status());
        assertTrue(record.message().contains("不匹配"));
    }

    @Test
    void notFoundWhenRequiredKeywordMissing() {
        // 主题与正文都不含「验证码」这个必需关键词，即便验证码数字命中也不算匹配
        givenMail("11", "noreply@partner.com", "月度回执", "这是一封普通回执 123456");

        InboundCheck record = service.check(new InboundCheckCmd(address.id(), "123456", 30), "1");

        assertEquals(InboundCheck.NOT_FOUND, record.status());
    }

    @Test
    void emptyCodeMatchesByKeywordsOnly() {
        givenMail("11", "noreply@partner.com", "这是一封含验证码字样的回信");

        InboundCheck record = service.check(new InboundCheckCmd(address.id(), "  ", 30), "1");

        assertEquals(InboundCheck.MATCHED, record.status());
        assertTrue(record.codeMask().isEmpty());
    }

    @Test
    void transportFailureBecomesUnavailable() {
        imap.failWith(new MailTransportException("IMAP 连接/认证失败：认证失败，请检查用户名与密码/授权码"));

        InboundCheck record = service.check(new InboundCheckCmd(address.id(), "123456", 30), "1");

        assertEquals(InboundCheck.UNAVAILABLE, record.status());
        assertTrue(record.message().contains("认证失败"));
    }

    @Test
    void windowOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.check(new InboundCheckCmd(address.id(), "123456", 5000), "1"));
        assertEquals(0, checks.count());
    }

    @Test
    void recordsArePagedAndFilterable() {
        givenMail("11", "noreply@partner.com", "您的验证码是 123456");
        service.check(new InboundCheckCmd(address.id(), "123456", 30), "1");
        service.check(new InboundCheckCmd(address.id(), "000000", 30), "1");

        assertEquals(2, service.adminPage(new InboundCheckQuery(null, null, 1, 10)).total());
        assertEquals(1, service.adminPage(new InboundCheckQuery(null, "MATCHED", 1, 10)).total());
        assertEquals(0, service.adminPage(new InboundCheckQuery("other-id", null, 1, 10)).total());
    }
}
