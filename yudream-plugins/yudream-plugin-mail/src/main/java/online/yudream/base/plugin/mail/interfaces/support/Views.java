package online.yudream.base.plugin.mail.interfaces.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import online.yudream.base.plugin.mail.application.dto.InboxResult;
import online.yudream.base.plugin.mail.application.dto.TransportTestResult;
import online.yudream.base.plugin.mail.domain.aggregate.InboundCheck;
import online.yudream.base.plugin.mail.domain.aggregate.InboxMessage;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.aggregate.OutboundRecord;
import online.yudream.base.plugin.spi.system.FrameworkServices;

/**
 * 界面视图装配。
 *
 * <p>安全约定：任何视图都不输出 SMTP/IMAP 密码，只输出“是否已设置”；
 * 用户端视图只含本人可见字段；收发配置里的主机/用户名属于管理端信息，不进用户端视图。</p>
 */
public final class Views {

    private Views() {
    }

    // ---- 邮箱地址 ----

    /** 列表视图：含收发配置（无密码），供管理端表格与抽屉回填。 */
    public static Map<String, Object> addressView(MailAddress address) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", address.id());
        view.put("address", address.address());
        view.put("displayName", address.displayName());
        view.put("purpose", address.purpose().name());
        view.put("purposeLabel", address.purpose().label());
        view.put("enabled", address.enabled());
        view.put("defaultSender", address.defaultSender());
        view.put("remark", address.remark());
        view.put("smtp", smtpView(address.smtp()));
        view.put("imap", imapView(address.imap()));
        view.put("smtpConfigured", address.smtp().configured());
        view.put("imapConfigured", address.imap().configured());
        view.put("createdAt", address.createdAt());
        view.put("updatedAt", address.updatedAt());
        return view;
    }

    /** 详情视图：额外带密码是否已设置（不返回密码本身）。 */
    public static Map<String, Object> addressDetailView(MailAddress address, boolean smtpPasswordSet, boolean imapPasswordSet) {
        Map<String, Object> view = addressView(address);
        view.put("smtpPasswordSet", smtpPasswordSet);
        view.put("imapPasswordSet", imapPasswordSet);
        return view;
    }

    /** 地址选择器选项。 */
    public static Map<String, Object> addressOptionView(MailAddress address) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", address.id());
        view.put("address", address.address());
        view.put("displayName", address.displayName());
        view.put("enabled", address.enabled());
        view.put("defaultSender", address.defaultSender());
        view.put("smtpConfigured", address.smtp().configured());
        view.put("imapConfigured", address.imap().configured());
        view.put("folder", address.imap().folder());
        return view;
    }

    private static Map<String, Object> smtpView(MailAddress.SmtpConfig config) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("host", config.host());
        view.put("port", config.port());
        view.put("security", config.security().name());
        view.put("securityLabel", config.security().label());
        view.put("username", config.username());
        return view;
    }

    private static Map<String, Object> imapView(MailAddress.ImapConfig config) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("host", config.host());
        view.put("port", config.port());
        view.put("security", config.security().name());
        view.put("securityLabel", config.security().label());
        view.put("username", config.username());
        view.put("folder", config.folder());
        view.put("fetchLimit", config.fetchLimit());
        view.put("allowedFromDomains", config.allowedFromDomains());
        view.put("requiredKeywords", config.requiredKeywords());
        return view;
    }

    // ---- 传输测试 ----

    public static Map<String, Object> testResultView(TransportTestResult result) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ok", result.ok());
        view.put("message", result.message());
        view.put("messageCount", result.messageCount());
        return view;
    }

    // ---- 收件箱 ----

    public static Map<String, Object> inboxResultView(InboxResult result) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("records", result.records().stream().map(Views::inboxSummaryView).toList());
        view.put("total", result.total());
        view.put("mailboxTotal", result.mailboxTotal());
        view.put("fetched", result.fetched());
        view.put("folder", result.folder());
        view.put("synced", result.synced());
        view.put("truncated", result.truncated());
        return view;
    }

    /** 列表视图（持久化副本，不含正文）。 */
    public static Map<String, Object> inboxSummaryView(InboxMessage message) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", message.id());
        view.put("addressId", message.addressId());
        view.put("folder", message.folder());
        view.put("uid", message.uid());
        view.put("subject", message.subject());
        view.put("from", message.from());
        view.put("to", message.to());
        view.put("sentAt", message.sentAt());
        view.put("size", message.size());
        view.put("seen", message.seen());
        view.put("bodyFetched", message.bodyFetched());
        return view;
    }

    /** 详情视图：含缓存正文、附件元数据与 Message-ID（不下载附件内容）。 */
    public static Map<String, Object> inboxDetailView(InboxMessage message) {
        Map<String, Object> view = inboxSummaryView(message);
        view.put("cc", message.cc());
        view.put("text", message.text());
        view.put("html", message.html());
        view.put("truncated", message.truncated());
        view.put("messageId", message.messageId());
        view.put("attachments", message.attachments().stream().map(Views::attachmentView).toList());
        return view;
    }

    private static Map<String, Object> attachmentView(InboxMessage.Attachment attachment) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", attachment.name());
        view.put("contentType", attachment.contentType());
        view.put("size", attachment.size());
        return view;
    }

    // ---- 发信记录 ----

    public static Map<String, Object> outboundView(OutboundRecord record, FrameworkServices framework, boolean withBody) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.id());
        view.put("addressId", record.addressId());
        view.put("fromAddress", record.fromAddress());
        view.put("smtpHost", record.smtpHost());
        view.put("to", record.to());
        view.put("cc", record.cc());
        view.put("bcc", record.bcc());
        view.put("subject", record.subject());
        view.put("bodyType", record.bodyType().name());
        view.put("bodyPreview", record.bodyPreview());
        view.put("status", record.status().name());
        view.put("statusLabel", record.status().label());
        view.put("errorMessage", record.errorMessage());
        view.put("source", record.source().name());
        view.put("sourceLabel", record.source().label());
        view.put("operatorUserId", record.operatorUserId());
        view.put("operatorName", userLabel(record.operatorUserId(), framework));
        view.put("createdAt", record.createdAt());
        if (withBody) {
            view.put("body", record.body());
        }
        return view;
    }

    // ---- 入站核验记录 ----

    public static Map<String, Object> inboundCheckView(InboundCheck check, MailAddress address, FrameworkServices framework) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", check.id());
        view.put("addressId", check.addressId());
        view.put("address", address == null ? null : address.address());
        view.put("displayName", address == null ? null : address.displayName());
        view.put("folder", check.folder());
        view.put("codeMask", check.codeMask());
        view.put("status", check.status());
        view.put("statusLabel", InboundCheck.label(check.status()));
        view.put("message", check.message());
        view.put("matchedUid", check.matchedUid());
        view.put("matchedSubject", check.matchedSubject());
        view.put("matchedFrom", check.matchedFrom());
        view.put("matchedAt", check.matchedAt());
        view.put("operatorUserId", check.operatorUserId());
        view.put("operatorName", userLabel(check.operatorUserId(), framework));
        view.put("createdAt", check.createdAt());
        return view;
    }

    /** 解析用户展示名：优先 nickname，其次 username；用户已删除时回退 ID。 */
    private static String userLabel(String userId, FrameworkServices framework) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        if (framework == null) {
            return userId;
        }
        try {
            Optional<String> label = framework.users().findById(Long.parseLong(userId))
                    .map(profile -> {
                        String nickname = profile.nickname();
                        return nickname == null || nickname.isBlank() ? profile.username() : nickname;
                    });
            return label.orElse(userId);
        }
        catch (NumberFormatException e) {
            return userId;
        }
    }

    /** 供 facade 复用：把列表包成 {records,total}。 */
    public static Map<String, Object> page(List<?> records, long total) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("records", records);
        view.put("total", total);
        return view;
    }
}
