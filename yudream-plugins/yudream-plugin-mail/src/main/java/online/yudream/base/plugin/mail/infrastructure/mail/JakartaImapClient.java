package online.yudream.base.plugin.mail.infrastructure.mail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import online.yudream.base.plugin.mail.application.port.ImapClient;
import online.yudream.base.plugin.mail.application.port.MailTransportException;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;

/**
 * 基于 Jakarta Mail（ANGUS）的 IMAP 客户端实现。
 *
 * <p>只读语义：收件夹一律以 {@link Folder#READ_ONLY} 打开，jakarta.mail 在该模式下用
 * {@code BODY.PEEK[]} 拉正文，不会把邮件标记成已读、也不会改动服务端任何状态。
 * 列表只取信封（envelope/flags/uid），正文仅在详情与核验候选邮件上按需下载。</p>
 */
public class JakartaImapClient implements ImapClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_FOLDERS = 200;

    @Override
    public int testConnection(ImapSettings settings, String folder) {
        Store store = open(settings);
        try {
            Folder target = openFolder(store, folder);
            try {
                return target.getMessageCount();
            }
            finally {
                closeFolder(target);
            }
        }
        catch (MessagingException e) {
            throw new MailTransportException("IMAP 读取失败：" + MailErrors.describe(e), e);
        }
        finally {
            closeStore(store);
        }
    }

    @Override
    public List<String> listFolders(ImapSettings settings) {
        Store store = open(settings);
        try {
            Folder root = store.getDefaultFolder();
            List<Folder> folders = list(root, "*");
            if (folders.isEmpty()) {
                folders = list(root, "%");
            }
            List<String> names = new ArrayList<>();
            for (Folder folder : folders) {
                if (names.size() >= MAX_FOLDERS) {
                    break;
                }
                if (folder.exists()) {
                    names.add(folder.getFullName());
                }
            }
            names.sort(Comparator
                    .comparingInt((String name) -> MailAddress.ImapConfig.DEFAULT_FOLDER.equalsIgnoreCase(name) ? 0 : 1)
                    .thenComparing(Comparator.naturalOrder()));
            return names;
        }
        catch (MessagingException e) {
            throw new MailTransportException("IMAP 读取收件夹列表失败：" + MailErrors.describe(e), e);
        }
        finally {
            closeStore(store);
        }
    }

    @Override
    public InboundMailPage fetchSummaries(ImapSettings settings, String folder, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MailAddress.ImapConfig.MAX_FETCH_LIMIT);
        Store store = open(settings);
        try {
            Folder target = openFolder(store, folder);
            try {
                int total = target.getMessageCount();
                if (total <= 0) {
                    return new InboundMailPage(List.of(), 0);
                }
                int start = Math.max(1, total - safeLimit + 1);
                Message[] messages = target.getMessages(start, total);
                FetchProfile profile = new FetchProfile();
                profile.add(FetchProfile.Item.ENVELOPE);
                profile.add(FetchProfile.Item.FLAGS);
                profile.add(UIDFolder.FetchProfileItem.UID);
                target.fetch(messages, profile);
                UIDFolder uidFolder = uidFolder(target);
                List<InboundMailSummary> result = new ArrayList<>(messages.length);
                // 新邮件在前
                for (int index = messages.length - 1; index >= 0; index--) {
                    Message message = messages[index];
                    result.add(new InboundMailSummary(
                            String.valueOf(uidFolder.getUID(message)),
                            MimeMessageSupport.subject(message),
                            MimeMessageSupport.formatAddresses(safeFrom(message)),
                            MimeMessageSupport.addressList(safeRecipients(message)),
                            sentAt(message),
                            size(message),
                            seen(message)));
                }
                return new InboundMailPage(List.copyOf(result), total);
            }
            finally {
                closeFolder(target);
            }
        }
        catch (MessagingException e) {
            throw new MailTransportException("IMAP 读取失败：" + MailErrors.describe(e), e);
        }
        finally {
            closeStore(store);
        }
    }

    @Override
    public InboundMailDetail fetchDetail(ImapSettings settings, String folder, String uid) {
        long uidValue = parseUid(uid);
        Store store = open(settings);
        try {
            Folder target = openFolder(store, folder);
            try {
                Message message = uidFolder(target).getMessageByUID(uidValue);
                if (message == null) {
                    throw new MailTransportException("邮件不存在或已被删除（UID " + uid + "）");
                }
                MimeMessageSupport.Bodies bodies = MimeMessageSupport.bodies(message);
                return new InboundMailDetail(
                        uid,
                        MimeMessageSupport.subject(message),
                        MimeMessageSupport.formatAddresses(safeFrom(message)),
                        MimeMessageSupport.addressList(safeRecipients(message)),
                        MimeMessageSupport.addressList(safeRecipients(message, Message.RecipientType.CC)),
                        sentAt(message),
                        size(message),
                        seen(message),
                        bodies.text(),
                        bodies.html(),
                        bodies.attachments(),
                        bodies.truncated(),
                        MimeMessageSupport.messageId(message));
            }
            finally {
                closeFolder(target);
            }
        }
        catch (MessagingException e) {
            throw new MailTransportException("IMAP 读取失败：" + MailErrors.describe(e), e);
        }
        finally {
            closeStore(store);
        }
    }

    @Override
    public void markSeen(ImapSettings settings, String folder, String uid, boolean seen) {
        mutateMessage(settings, folder, uid, "标记已读", (target, message) -> {
            Flags flags = new Flags(Flags.Flag.SEEN);
            if (seen) {
                message.setFlags(flags, true);
            }
            else {
                message.setFlags(flags, false);
            }
        });
    }

    @Override
    public void delete(ImapSettings settings, String folder, String uid) {
        mutateMessage(settings, folder, uid, "删除邮件", (target, message) -> {
            message.setFlags(new Flags(Flags.Flag.DELETED), true);
            target.expunge();
        });
    }

    /** 可写打开收件夹按 UID 定位单封邮件并执行变更；语义与 fetchDetail 相同，只是收件夹以 READ_WRITE 打开。 */
    private interface MessageMutator {
        void apply(Folder folder, Message message) throws MessagingException;
    }

    private void mutateMessage(ImapSettings settings, String folder, String uid, String action, MessageMutator mutator) {
        long uidValue = parseUid(uid);
        Store store = open(settings);
        try {
            Folder target = openFolder(store, folder, true);
            try {
                Message message = uidFolder(target).getMessageByUID(uidValue);
                if (message == null) {
                    throw new MailTransportException("邮件不存在或已被删除（UID " + uid + "）");
                }
                mutator.apply(target, message);
            }
            finally {
                closeFolder(target);
            }
        }
        catch (MessagingException e) {
            throw new MailTransportException(action + "失败：" + MailErrors.describe(e), e);
        }
        finally {
            closeStore(store);
        }
    }

    private Store open(ImapSettings settings) {
        if (settings == null || settings.host() == null || settings.host().isBlank()
                || settings.username() == null || settings.username().isBlank()) {
            throw new MailTransportException("该邮箱地址尚未配置 IMAP 服务器或用户名");
        }
        if (settings.password() == null || settings.password().isEmpty()) {
            throw new MailTransportException("该邮箱地址尚未设置 IMAP 密码");
        }
        String protocol = settings.security() == MailAddress.Security.SSL ? "imaps" : "imap";
        Properties properties = new Properties();
        properties.put("mail.store.protocol", protocol);
        properties.put("mail." + protocol + ".host", settings.host());
        properties.put("mail." + protocol + ".port", String.valueOf(settings.port()));
        properties.put("mail." + protocol + ".connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MILLIS));
        properties.put("mail." + protocol + ".timeout", String.valueOf(READ_TIMEOUT_MILLIS));
        if (settings.security() == MailAddress.Security.STARTTLS) {
            properties.put("mail." + protocol + ".starttls.enable", "true");
            properties.put("mail." + protocol + ".starttls.required", "true");
        }
        Session session = Session.getInstance(properties, null);
        try {
            Store store = session.getStore(protocol);
            store.connect(settings.host(), settings.port(), settings.username(), settings.password());
            return store;
        }
        catch (MessagingException e) {
            throw new MailTransportException("IMAP 连接/认证失败：" + MailErrors.describe(e), e);
        }
    }

    private Folder openFolder(Store store, String folder) throws MessagingException {
        return openFolder(store, folder, false);
    }

    private Folder openFolder(Store store, String folder, boolean writable) throws MessagingException {
        String name = folder == null || folder.isBlank() ? MailAddress.ImapConfig.DEFAULT_FOLDER : folder.trim();
        Folder target = store.getFolder(name);
        if (target == null || !target.exists()) {
            throw new MailTransportException("收件夹不存在：" + name);
        }
        target.open(writable ? Folder.READ_WRITE : Folder.READ_ONLY);
        return target;
    }

    private UIDFolder uidFolder(Folder folder) {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder;
        }
        throw new MailTransportException("该收件夹不支持 UID 访问");
    }

    private List<Folder> list(Folder root, String pattern) throws MessagingException {
        Folder[] folders = root.list(pattern);
        return folders == null ? List.of() : List.of(folders);
    }

    private long parseUid(String uid) {
        try {
            return Long.parseLong(uid == null ? "" : uid.trim());
        }
        catch (NumberFormatException e) {
            throw new MailTransportException("邮件 UID 不合法：" + uid);
        }
    }

    private jakarta.mail.Address[] safeFrom(Message message) {
        try {
            return message.getFrom();
        }
        catch (MessagingException e) {
            return null;
        }
    }

    private jakarta.mail.Address[] safeRecipients(Message message) {
        return safeRecipients(message, Message.RecipientType.TO);
    }

    private jakarta.mail.Address[] safeRecipients(Message message, Message.RecipientType type) {
        try {
            return message.getRecipients(type);
        }
        catch (MessagingException e) {
            return null;
        }
    }

    private long sentAt(Message message) {
        try {
            Date date = message.getSentDate();
            return date == null ? 0L : date.getTime();
        }
        catch (MessagingException e) {
            return 0L;
        }
    }

    private long size(Message message) {
        try {
            return Math.max(message.getSize(), 0);
        }
        catch (MessagingException e) {
            return 0L;
        }
    }

    private boolean seen(Message message) {
        try {
            return message.isSet(Flags.Flag.SEEN);
        }
        catch (MessagingException e) {
            return false;
        }
    }

    private void closeFolder(Folder folder) {
        if (folder == null || !folder.isOpen()) {
            return;
        }
        try {
            folder.close(false);
        }
        catch (MessagingException ignored) {
            // 关闭失败不影响结果
        }
    }

    private void closeStore(Store store) {
        if (store == null) {
            return;
        }
        try {
            store.close();
        }
        catch (MessagingException ignored) {
            // 关闭失败不影响结果
        }
    }
}
