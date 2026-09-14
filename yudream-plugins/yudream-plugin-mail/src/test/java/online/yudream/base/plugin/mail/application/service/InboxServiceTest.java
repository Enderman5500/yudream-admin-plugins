package online.yudream.base.plugin.mail.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import online.yudream.base.plugin.mail.application.cmd.MailAddressSaveCmd;
import online.yudream.base.plugin.mail.application.dto.InboxResult;
import online.yudream.base.plugin.mail.application.dto.TransportTestResult;
import online.yudream.base.plugin.mail.application.port.ImapClient;
import online.yudream.base.plugin.mail.application.port.MailTransportException;
import online.yudream.base.plugin.mail.application.query.InboxQuery;
import online.yudream.base.plugin.mail.domain.aggregate.InboxMessage;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.infrastructure.FakeDocumentStore;
import online.yudream.base.plugin.mail.infrastructure.FakeSecretStore;
import online.yudream.base.plugin.mail.infrastructure.repository.InboxMessageDocumentRepository;
import online.yudream.base.plugin.mail.infrastructure.repository.MailAddressDocumentRepository;
import online.yudream.base.plugin.mail.support.FakeImapClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 收件箱用例：同步持久化、降级读缓存、正文缓存、信封过滤与核验候选筛选。 */
class InboxServiceTest {

    private MailAddressService addressService;
    private FakeImapClient imap;
    private InboxMessageDocumentRepository messages;
    private InboxService service;
    private MailAddress address;

    @BeforeEach
    void setUp() {
        FakeDocumentStore documents = new FakeDocumentStore();
        addressService = new MailAddressService(new MailAddressDocumentRepository(documents), new FakeSecretStore());
        imap = new FakeImapClient();
        messages = new InboxMessageDocumentRepository(documents);
        service = new InboxService(addressService, imap, messages);
        address = addressService.create(new MailAddressSaveCmd(
                "ops@example.com", null, "OTHER", "", true,
                new MailAddressSaveCmd.SmtpInput("smtp.example.com", 465, "SSL", "ops@example.com", "p"),
                new MailAddressSaveCmd.ImapInput("imap.example.com", 993, "SSL", "ops@example.com", "ip",
                        "INBOX", 20, List.of("partner.com"), List.of("验证码"))));
    }

    private InboxResult query(String folder, Integer limit, Integer page, Integer size,
                              String fromDomain, String keyword) {
        return service.list(new InboxQuery(address.id(), folder, limit, page, size, fromDomain, keyword));
    }

    @Test
    void listSyncsPersistsAndFiltersByEnvelopeOnly() {
        imap.withSummaries(List.of(
                FakeImapClient.summary("11", "验证码 123456", "noreply@partner.com", 1_700_000_000_000L),
                FakeImapClient.summary("10", "月度对账", "billing@other.com", 1_700_000_100_000L),
                FakeImapClient.summary("9", "无关邮件", "spam@other.com", 1_700_000_200_000L)), 30);

        InboxResult result = query(null, null, null, null, "partner.com", null);

        assertEquals("INBOX", result.folder());
        assertEquals(1, result.total());
        assertEquals("11", result.records().get(0).uid());
        assertEquals(30, result.mailboxTotal());
        assertEquals(3, result.fetched());
        assertTrue(result.synced());
        assertTrue(result.truncated());
        // 列表查询不下载正文
        assertEquals(0, imap.detailFetchCount());
        assertEquals("INBOX", imap.lastFolder());
        assertEquals("ip", imap.lastSettings().password());
        // 信封已落库（3 条）
        assertEquals(3, messages.findAllForFolder(address.id(), "INBOX").size());
    }

    @Test
    void syncUpsertsEnvelopeAndKeepsCachedBody() {
        imap.withSummaries(List.of(
                FakeImapClient.summary("11", "旧主题", "noreply@partner.com", 1L)), 1);
        // 先下载正文，建立缓存
        imap.withDetail("11", FakeImapClient.detail("11", "旧主题", "noreply@partner.com", 1L, "正文内容"));
        service.detail(address.id(), null, "11");
        assertEquals(1, imap.detailFetchCount());

        // 服务端信封变化 + 已读状态变化：正文与 Message-ID 保留，信封刷新
        imap.withSummaries(List.of(
                new ImapClient.InboundMailSummary("11", "新主题", "noreply@partner.com",
                        List.of("ops@example.com"), 2L, 4096L, true)), 1);

        InboxResult result = query(null, null, null, null, null, null);
        assertEquals(1, result.total());
        assertEquals(1, imap.detailFetchCount());

        InboxMessage stored = messages.findById(InboxMessage.documentId(address.id(), "INBOX", "11")).orElseThrow();
        assertEquals("新主题", stored.subject());
        assertEquals(2L, stored.sentAt());
        assertTrue(stored.seen());
        assertTrue(stored.bodyFetched());
        assertEquals("正文内容", stored.text());
        assertEquals("<msg-11@example.com>", stored.messageId());
    }

    @Test
    void detailCachesBodyAndSkipsSecondFetch() {
        imap.withSummaries(List.of(
                FakeImapClient.summary("11", "验证码", "noreply@partner.com", 1L)), 1);
        imap.withDetail("11", FakeImapClient.detail("11", "验证码", "noreply@partner.com", 1L, "您的验证码是 123456"));

        InboxMessage first = service.detail(address.id(), null, " 11 ");
        InboxMessage second = service.detail(address.id(), null, "11");

        assertEquals("123456", first.text().replaceAll("\\D", ""));
        assertEquals("11", first.uid());
        assertTrue(first.bodyFetched());
        assertEquals(first.text(), second.text());
        // 第二次读缓存，不再访问 IMAP
        assertEquals(1, imap.detailFetchCount());
    }

    @Test
    void listFallsBackToCacheWhenImapUnavailable() {
        imap.withSummaries(List.of(
                FakeImapClient.summary("11", "历史邮件", "noreply@partner.com", 1L)), 1);
        query(null, null, null, null, null, null);
        assertEquals(1, messages.findAllForFolder(address.id(), "INBOX").size());

        imap.failWith(new MailTransportException("IMAP 连接失败"));
        InboxResult result = query(null, null, null, null, null, null);

        assertFalse(result.synced());
        assertEquals(-1, result.mailboxTotal());
        assertFalse(result.truncated());
        assertEquals(1, result.total());
        assertEquals("11", result.records().get(0).uid());
    }

    @Test
    void listPagesPersistedCopy() {
        imap.withSummaries(List.of(
                FakeImapClient.summary("13", "c", "a@partner.com", 3L),
                FakeImapClient.summary("12", "b", "a@partner.com", 2L),
                FakeImapClient.summary("11", "a", "a@partner.com", 1L)), 3);
        query(null, null, null, null, null, null);

        InboxResult page1 = query(null, null, 1, 2, null, null);
        InboxResult page2 = query(null, null, 2, 2, null, null);

        assertEquals(3, page1.total());
        assertEquals(2, page1.records().size());
        assertEquals("13", page1.records().get(0).uid());
        assertEquals(1, page2.records().size());
        assertEquals("11", page2.records().get(0).uid());
    }

    @Test
    void keywordMatchesSubjectFromOrRecipients() {
        imap.withSummaries(List.of(
                FakeImapClient.summary("11", "验证码 123456", "noreply@partner.com", 1L),
                FakeImapClient.summary("10", "月度对账", "opaque@other.com", 2L)), 2);
        query(null, null, null, null, null, null);

        assertEquals(1, query(null, 10, null, null, null, "对账").total());
        assertEquals(2, query(null, 10, null, null, null, "example.com").total());
        assertEquals(2, query(null, 10, null, null, null, null).total());
    }

    @Test
    void requestedFolderOverridesConfiguredFolder() {
        imap.withSummaries(List.of(), 0);

        InboxResult result = query("Archive", 5, null, null, null, null);

        assertEquals("Archive", result.folder());
        assertEquals("Archive", imap.lastFolder());
    }

    @Test
    void listRequiresConfiguredImap() {
        MailAddress withoutImap = addressService.create(new MailAddressSaveCmd(
                "no-imap@example.com", null, "OTHER", "", true, null, null));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.list(new InboxQuery(withoutImap.id(), null, 10, null, null, null, null)));
        assertTrue(error.getMessage().contains("尚未配置 IMAP"));
    }

    @Test
    void detailRequiresUid() {
        assertThrows(IllegalArgumentException.class, () -> service.detail(address.id(), null, "  "));
    }

    @Test
    void markSeenPassesFolderAndFlagAndUpdatesCache() {
        imap.withSummaries(List.of(
                FakeImapClient.summary("11", "验证码", "noreply@partner.com", 1L)), 1);
        query(null, null, null, null, null, null);

        service.markSeen(address.id(), null, "11", true);
        assertEquals(Boolean.TRUE, imap.seenUpdates().get("11"));
        assertTrue(messages.findById(InboxMessage.documentId(address.id(), "INBOX", "11")).orElseThrow().seen());

        service.markSeen(address.id(), "Archive", "10", false);
        assertEquals(Boolean.FALSE, imap.seenUpdates().get("10"));
        assertEquals("Archive", imap.lastFolder());
    }

    @Test
    void markSeenRequiresConfiguredImapAndUid() {
        MailAddress withoutImap = addressService.create(new MailAddressSaveCmd(
                "no-imap@example.com", null, "OTHER", "", true, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.markSeen(withoutImap.id(), null, "1", true));
        assertThrows(IllegalArgumentException.class, () -> service.markSeen(address.id(), null, "", true));
        assertEquals(0, imap.seenUpdates().size());
    }

    @Test
    void deleteRemovesServerMailAndCache() {
        imap.withSummaries(List.of(
                FakeImapClient.summary("11", "验证码", "noreply@partner.com", 1L)), 1);
        query(null, null, null, null, null, null);

        service.deleteMail(address.id(), null, "11");

        assertEquals(List.of("11"), imap.deletedUids());
        assertNull(messages.findById(InboxMessage.documentId(address.id(), "INBOX", "11")).orElse(null));
        assertEquals(0, query(null, null, null, null, null, null).total());
    }

    @Test
    void deleteRequiresConfiguredImapAndUid() {
        MailAddress withoutImap = addressService.create(new MailAddressSaveCmd(
                "no-imap@example.com", null, "OTHER", "", true, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.deleteMail(withoutImap.id(), null, "1"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteMail(address.id(), null, " "));
        assertEquals(0, imap.deletedUids().size());
    }

    @Test
    void foldersComeFromServer() {
        imap.withFolders(List.of("INBOX", "已发送"));

        assertEquals(List.of("INBOX", "已发送"), service.folders(address.id()));
    }

    @Test
    void testImapReportsFolderCount() {
        imap.withSummaries(List.of(), 42);

        TransportTestResult result = service.testImap(address.id());

        assertTrue(result.ok());
        assertEquals(42, result.messageCount());
        assertTrue(result.message().contains("INBOX"));
    }

    @Test
    void testImapReportsFailure() {
        imap.failWith(new MailTransportException("IMAP 连接/认证失败：认证失败，请检查用户名与密码/授权码"));

        TransportTestResult result = service.testImap(address.id());

        assertFalse(result.ok());
        assertTrue(result.message().contains("认证失败"));
    }

    @Test
    void recentDetailsFiltersByWindowAndDomainsAndSkipsMissingBodies() {
        long now = System.currentTimeMillis();
        imap.withSummaries(List.of(
                FakeImapClient.summary("11", "命中", "noreply@partner.com", now - 60_000L),
                FakeImapClient.summary("10", "太旧", "noreply@partner.com", now - 3 * 60 * 60 * 1000L),
                FakeImapClient.summary("9", "域不符", "someone@other.com", now)), 3);
        imap.withDetail("11", FakeImapClient.detail("11", "命中", "noreply@partner.com", now, "验证码 123456"));

        List<ImapClient.InboundMailDetail> details = service.recentDetails(address, null, now - 30 * 60_000L, 20);

        assertEquals(1, details.size());
        assertEquals("11", details.get(0).uid());
        // 窗口外的邮件不下载正文（域不符的也不下载）
        assertEquals(1, imap.detailFetchCount());
    }
}
