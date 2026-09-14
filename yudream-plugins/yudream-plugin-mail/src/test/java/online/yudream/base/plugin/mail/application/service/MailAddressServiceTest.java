package online.yudream.base.plugin.mail.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import online.yudream.base.plugin.mail.application.cmd.MailAddressSaveCmd;
import online.yudream.base.plugin.mail.application.query.MailAddressQuery;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.infrastructure.FakeDocumentStore;
import online.yudream.base.plugin.mail.infrastructure.FakeSecretStore;
import online.yudream.base.plugin.mail.infrastructure.repository.MailAddressDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 邮箱地址簿规则与凭据规则：唯一性、默认发件地址保护、密码只进密钥库且可保持/清理。 */
class MailAddressServiceTest {

    private FakeSecretStore secrets;
    private MailAddressService service;

    @BeforeEach
    void setUp() {
        FakeDocumentStore documents = new FakeDocumentStore();
        secrets = new FakeSecretStore();
        service = new MailAddressService(new MailAddressDocumentRepository(documents), secrets);
    }

    private static MailAddressSaveCmd cmd(String address, boolean enabled) {
        return new MailAddressSaveCmd(address, "测试地址", "NOTIFICATION", "备注", enabled,
                new MailAddressSaveCmd.SmtpInput("smtp.example.com", 465, "SSL", address, "smtp-pass"),
                new MailAddressSaveCmd.ImapInput("imap.example.com", 993, "SSL", address, "imap-pass",
                        "INBOX", 20, List.of("example.com"), List.of("验证码")));
    }

    @Test
    void firstEnabledAddressBecomesDefaultSender() {
        MailAddress created = service.create(cmd("ops@example.com", true));
        assertTrue(created.defaultSender());
        assertEquals("ops@example.com", created.address());
        assertTrue(created.smtp().configured());
        assertTrue(created.imap().configured());
    }

    @Test
    void firstDisabledAddressDoesNotBecomeDefaultSender() {
        assertFalse(service.create(cmd("ops@example.com", false)).defaultSender());
    }

    @Test
    void addressIsNormalizedToLowerCase() {
        assertEquals("ops@example.com", service.create(cmd("  Ops@Example.COM ", true)).address());
    }

    @Test
    void duplicateAddressIsRejectedIgnoringCase() {
        service.create(cmd("ops@example.com", true));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(cmd("OPS@example.com", true)));
        assertTrue(error.getMessage().contains("已存在"));
    }

    @Test
    void invalidAddressFormatIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(cmd("not-an-email", true)));
    }

    @Test
    void defaultSenderCannotBeDisabledOrDeleted() {
        MailAddress defaultAddress = service.create(cmd("ops@example.com", true));

        IllegalArgumentException disable = assertThrows(IllegalArgumentException.class,
                () -> service.setEnabled(defaultAddress.id(), false));
        assertTrue(disable.getMessage().contains("默认发件地址不能停用"));

        IllegalArgumentException delete = assertThrows(IllegalArgumentException.class,
                () -> service.delete(defaultAddress.id()));
        assertTrue(delete.getMessage().contains("默认发件地址不能删除"));
    }

    @Test
    void disabledAddressCannotBecomeDefaultAndCannotSend() {
        service.create(cmd("ops@example.com", true));
        MailAddress disabled = service.create(cmd("billing@example.com", false));

        assertThrows(IllegalArgumentException.class, () -> service.setDefault(disabled.id()));
        IllegalArgumentException send = assertThrows(IllegalArgumentException.class,
                () -> service.requireEnabled(disabled.id()));
        assertTrue(send.getMessage().contains("已停用"));
    }

    @Test
    void settingDefaultClearsPreviousDefault() {
        MailAddress first = service.create(cmd("ops@example.com", true));
        MailAddress second = service.create(cmd("billing@example.com", true));
        service.setDefault(second.id());

        assertTrue(service.requireExisting(second.id()).defaultSender());
        assertFalse(service.requireExisting(first.id()).defaultSender());
        assertEquals(1, service.listOptions().stream().filter(MailAddress::defaultSender).count());
    }

    @Test
    void transportSecurityDecidesDefaultPorts() {
        MailAddress withStartTls = service.create(new MailAddressSaveCmd(
                "starttls@example.com", null, "OTHER", "", true,
                new MailAddressSaveCmd.SmtpInput("smtp.example.com", null, "STARTTLS", "u", "p"),
                new MailAddressSaveCmd.ImapInput("imap.example.com", null, "STARTTLS", "u", "p",
                        null, null, List.of(), List.of())));

        assertEquals(587, withStartTls.smtp().port());
        assertEquals(143, withStartTls.imap().port());
        assertEquals(MailAddress.Security.STARTTLS, withStartTls.smtp().security());
        assertEquals(MailAddress.ImapConfig.DEFAULT_FOLDER, withStartTls.imap().folder());
        assertEquals(MailAddress.ImapConfig.DEFAULT_FETCH_LIMIT, withStartTls.imap().fetchLimit());
    }

    @Test
    void unknownSecurityFallsBackToSslInsteadOfPlaintext() {
        MailAddress address = service.create(new MailAddressSaveCmd(
                "weird@example.com", null, "OTHER", "", true,
                new MailAddressSaveCmd.SmtpInput("smtp.example.com", 0, "SOMETHING", "u", "p"), null));

        assertEquals(MailAddress.Security.SSL, address.smtp().security());
        assertEquals(465, address.smtp().port());
    }

    @Test
    void invalidDomainAndFetchLimitAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new MailAddressSaveCmd(
                "bad-domain@example.com", null, "OTHER", "", true, null,
                new MailAddressSaveCmd.ImapInput("imap.example.com", 993, "SSL", "u", "p",
                        "INBOX", 20, List.of("localhost"), List.of()))));

        assertThrows(IllegalArgumentException.class, () -> service.create(new MailAddressSaveCmd(
                "bad-limit@example.com", null, "OTHER", "", true, null,
                new MailAddressSaveCmd.ImapInput("imap.example.com", 993, "SSL", "u", "p",
                        "INBOX", 500, List.of(), List.of()))));
    }

    @Test
    void passwordsAreStoredInSecretStoreAndKeptWhenBlank() {
        MailAddress address = service.create(cmd("ops@example.com", true));

        assertTrue(service.smtpPasswordSet(address.id()));
        assertTrue(service.imapPasswordSet(address.id()));
        assertEquals("smtp-pass", new String(secrets.get("smtp-password:" + address.id()).orElseThrow(),
                StandardCharsets.UTF_8));

        // 密码留空 + 保留 host → 沿用原密码
        service.update(address.id(), new MailAddressSaveCmd(
                "ops@example.com", "测试地址", "NOTIFICATION", "备注", true,
                new MailAddressSaveCmd.SmtpInput("smtp.example.com", 465, "SSL", "ops@example.com", ""),
                new MailAddressSaveCmd.ImapInput("imap.example.com", 993, "SSL", "ops@example.com", null,
                        "INBOX", 20, List.of(), List.of())));

        assertEquals("smtp-pass", new String(secrets.get("smtp-password:" + address.id()).orElseThrow(),
                StandardCharsets.UTF_8));
        assertTrue(service.imapPasswordSet(address.id()));
    }

    @Test
    void newPasswordOverwritesOldOne() {
        MailAddress address = service.create(cmd("ops@example.com", true));

        service.update(address.id(), new MailAddressSaveCmd(
                "ops@example.com", "测试地址", "NOTIFICATION", "备注", true,
                new MailAddressSaveCmd.SmtpInput("smtp.example.com", 465, "SSL", "ops@example.com", "new-pass"),
                null));

        assertEquals("new-pass", new String(secrets.get("smtp-password:" + address.id()).orElseThrow(),
                StandardCharsets.UTF_8));
    }

    @Test
    void clearingHostRemovesStoredPassword() {
        MailAddress address = service.create(cmd("ops@example.com", true));

        MailAddress updated = service.update(address.id(), new MailAddressSaveCmd(
                "ops@example.com", "测试地址", "NOTIFICATION", "备注", true,
                new MailAddressSaveCmd.SmtpInput(null, 0, "SSL", null, null),
                new MailAddressSaveCmd.ImapInput("imap.example.com", 993, "SSL", "ops@example.com", "",
                        "INBOX", 20, List.of(), List.of())));

        assertFalse(updated.smtp().configured());
        assertFalse(service.smtpPasswordSet(address.id()));
        assertTrue(service.imapPasswordSet(address.id()));
    }

    @Test
    void deleteAddressRemovesCredentials() {
        service.create(cmd("ops@example.com", true));
        MailAddress removable = service.create(cmd("billing@example.com", true));

        service.delete(removable.id());

        assertEquals(1, service.count());
        assertEquals(2, secrets.size());
        assertThrows(RuntimeException.class, () -> service.requireSmtpPassword(removable));
    }

    @Test
    void listEnabledAndPageFiltering() {
        service.create(cmd("ops@example.com", true));
        MailAddress billing = service.create(cmd("billing@example.com", true));
        service.create(cmd("disabled@example.com", false));
        service.setDefault(billing.id());

        List<MailAddress> enabled = service.listEnabled();
        assertEquals(2, enabled.size());
        assertEquals(billing.id(), enabled.get(0).id());

        assertEquals(1, service.page(new MailAddressQuery("billing", null, 1, 10)).total());
        assertEquals(2, service.page(new MailAddressQuery(null, true, 1, 10)).total());
        assertEquals(3, service.page(new MailAddressQuery(null, null, 1, 10)).total());
        // 关键词也能命中收发主机
        assertEquals(3, service.page(new MailAddressQuery("example.com", null, 1, 10)).total());
    }
}
