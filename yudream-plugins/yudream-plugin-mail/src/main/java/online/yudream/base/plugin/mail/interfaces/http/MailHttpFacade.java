package online.yudream.base.plugin.mail.interfaces.http;

import java.util.List;
import java.util.Map;
import online.yudream.base.plugin.mail.application.NotFoundException;
import online.yudream.base.plugin.mail.application.cmd.InboundCheckCmd;
import online.yudream.base.plugin.mail.application.query.InboundCheckQuery;
import online.yudream.base.plugin.mail.application.query.InboxQuery;
import online.yudream.base.plugin.mail.application.query.MailAddressQuery;
import online.yudream.base.plugin.mail.application.query.OutboundRecordQuery;
import online.yudream.base.plugin.mail.application.service.InboundCheckService;
import online.yudream.base.plugin.mail.application.service.InboxService;
import online.yudream.base.plugin.mail.application.service.MailAddressService;
import online.yudream.base.plugin.mail.application.service.OutboundMailService;
import online.yudream.base.plugin.mail.domain.aggregate.InboundCheck;
import online.yudream.base.plugin.mail.domain.aggregate.MailAddress;
import online.yudream.base.plugin.mail.domain.aggregate.OutboundRecord;
import online.yudream.base.plugin.mail.infrastructure.JsonSupport;
import online.yudream.base.plugin.mail.interfaces.request.InboundCheckRequest;
import online.yudream.base.plugin.mail.interfaces.request.MailAddressSaveRequest;
import online.yudream.base.plugin.mail.interfaces.request.SendMailRequest;
import online.yudream.base.plugin.mail.interfaces.support.HttpSupport;
import online.yudream.base.plugin.mail.interfaces.support.Views;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.FrameworkServices;

/**
 * 邮箱中心 HTTP 门面：管理端与用户端控制器共用，负责 request → cmd、结果 → view 转换与用例编排。
 * 控制器只做路径/权限声明与转发，业务规则全部位于应用服务。
 */
public class MailHttpFacade {

    private final MailAddressService addressService;
    private final OutboundMailService outboundService;
    private final InboxService inboxService;
    private final InboundCheckService checkService;
    private final FrameworkServices framework;
    private final JsonSupport json;

    public MailHttpFacade(
            MailAddressService addressService,
            OutboundMailService outboundService,
            InboxService inboxService,
            InboundCheckService checkService,
            FrameworkServices framework,
            JsonSupport json) {
        this.addressService = addressService;
        this.outboundService = outboundService;
        this.inboxService = inboxService;
        this.checkService = checkService;
        this.framework = framework;
        this.json = json;
    }

    // ================= 管理端：邮箱地址 =================

    public PluginHttpResponse addresses(PluginHttpRequest request) {
        MailAddressQuery query = new MailAddressQuery(
                HttpSupport.first(request, "keyword"),
                HttpSupport.filterParam(request, "enabled"),
                HttpSupport.pageParam(request),
                HttpSupport.sizeParam(request, 10));
        var result = addressService.page(query);
        return PluginHttpResponse.ok(Views.page(
                result.records().stream().map(Views::addressView).toList(), result.total()));
    }

    /** 地址详情：带上“密码是否已设置”，供编辑抽屉回填（永不返回密码本身）。 */
    public PluginHttpResponse address(PluginHttpRequest request) {
        MailAddress address = addressService.requireExisting(addressId(request));
        return PluginHttpResponse.ok(Views.addressDetailView(address,
                addressService.smtpPasswordSet(address.id()),
                addressService.imapPasswordSet(address.id())));
    }

    /** 地址选择器：返回全部地址（含停用）与收/发配置完整度，避免管理员手输标识。 */
    public PluginHttpResponse addressOptions() {
        return PluginHttpResponse.ok(addressService.listOptions().stream()
                .map(Views::addressOptionView)
                .toList());
    }

    /** 收件箱地址选择器（use 权限）：只返回启用的地址，视图不含 SMTP/IMAP 凭证等敏感字段。 */
    public PluginHttpResponse inboxAddressOptions() {
        return PluginHttpResponse.ok(addressService.listEnabled().stream()
                .map(Views::addressOptionView)
                .toList());
    }

    public PluginHttpResponse createAddress(PluginHttpRequest request) {
        MailAddressSaveRequest body = json.read(request, MailAddressSaveRequest.class);
        return PluginHttpResponse.ok(Views.addressView(addressService.create(body.toCmd())));
    }

    public PluginHttpResponse updateAddress(PluginHttpRequest request) {
        String id = addressId(request);
        MailAddressSaveRequest body = json.read(request, MailAddressSaveRequest.class);
        return PluginHttpResponse.ok(Views.addressView(addressService.update(id, body.toCmd())));
    }

    public PluginHttpResponse setAddressEnabled(PluginHttpRequest request) {
        boolean enabled = HttpSupport.flagParam(request, "enabled", true);
        return PluginHttpResponse.ok(Views.addressView(addressService.setEnabled(addressId(request), enabled)));
    }

    public PluginHttpResponse setDefaultAddress(PluginHttpRequest request) {
        return PluginHttpResponse.ok(Views.addressView(addressService.setDefault(addressId(request))));
    }

    public PluginHttpResponse deleteAddress(PluginHttpRequest request) {
        addressService.delete(addressId(request));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    /** IMAP 收件夹列表：让管理员从服务端真实目录里选，而不是手输。 */
    public PluginHttpResponse imapFolders(PluginHttpRequest request) {
        return PluginHttpResponse.ok(Map.of("folders", inboxService.folders(addressId(request))));
    }

    public PluginHttpResponse testSmtp(PluginHttpRequest request) {
        return PluginHttpResponse.ok(Views.testResultView(outboundService.testSmtp(addressId(request))));
    }

    public PluginHttpResponse testImap(PluginHttpRequest request) {
        return PluginHttpResponse.ok(Views.testResultView(inboxService.testImap(addressId(request))));
    }

    // ================= 管理端：发信与发信记录 =================

    public PluginHttpResponse sendMail(PluginHttpRequest request) {
        SendMailRequest body = json.read(request, SendMailRequest.class);
        OutboundRecord record = outboundService.send(body.toCmd(), OutboundRecord.Source.ADMIN,
                HttpSupport.operatorId(request));
        failFast(record, OutboundRecord.Source.ADMIN);
        return PluginHttpResponse.ok(Views.outboundView(record, framework, true));
    }

    public PluginHttpResponse messages(PluginHttpRequest request) {
        OutboundRecordQuery query = new OutboundRecordQuery(
                HttpSupport.first(request, "keyword"),
                HttpSupport.first(request, "addressId"),
                HttpSupport.first(request, "status"),
                HttpSupport.pageParam(request),
                HttpSupport.sizeParam(request, 10));
        var result = outboundService.adminPage(query);
        return PluginHttpResponse.ok(Views.page(result.records().stream()
                .map(record -> Views.outboundView(record, framework, false))
                .toList(), result.total()));
    }

    public PluginHttpResponse message(PluginHttpRequest request) {
        String id = HttpSupport.segmentAfter(request.path(), "messages");
        return PluginHttpResponse.ok(Views.outboundView(outboundService.requireById(id), framework, true));
    }

    // ================= 收件箱（自动拉取 + 持久化，use 权限） =================

    public PluginHttpResponse inbox(PluginHttpRequest request) {
        InboxQuery query = new InboxQuery(
                HttpSupport.first(request, "addressId"),
                HttpSupport.first(request, "folder"),
                HttpSupport.intParam(request, "limit"),
                HttpSupport.intParam(request, "page"),
                HttpSupport.intParam(request, "size"),
                HttpSupport.first(request, "fromDomain"),
                HttpSupport.first(request, "keyword"));
        return PluginHttpResponse.ok(Views.inboxResultView(inboxService.list(query)));
    }

    public PluginHttpResponse inboxMessage(PluginHttpRequest request) {
        String uid = HttpSupport.segmentAfter(request.path(), "messages");
        return PluginHttpResponse.ok(Views.inboxDetailView(inboxService.detail(
                HttpSupport.first(request, "addressId"),
                HttpSupport.first(request, "folder"),
                uid)));
    }

    /** 标记/取消标记已读。 */
    public PluginHttpResponse inboxMarkSeen(PluginHttpRequest request) {
        inboxService.markSeen(
                HttpSupport.first(request, "addressId"),
                HttpSupport.first(request, "folder"),
                HttpSupport.first(request, "uid"),
                HttpSupport.flagParam(request, "seen", true));
        return PluginHttpResponse.ok(Map.of("updated", true));
    }

    /** 删除邮件：服务端永久移除。 */
    public PluginHttpResponse inboxDelete(PluginHttpRequest request) {
        String uid = HttpSupport.segmentAfter(request.path(), "messages");
        inboxService.deleteMail(
                HttpSupport.first(request, "addressId"),
                HttpSupport.first(request, "folder"),
                uid);
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse checkInbound(PluginHttpRequest request) {
        InboundCheckRequest body = json.read(request, InboundCheckRequest.class);
        InboundCheck record = checkService.check(body.toCmd(), HttpSupport.operatorId(request));
        MailAddress address = addressOf(record.addressId());
        return PluginHttpResponse.ok(Views.inboundCheckView(record, address, framework));
    }

    public PluginHttpResponse inboundChecks(PluginHttpRequest request) {
        InboundCheckQuery query = new InboundCheckQuery(
                HttpSupport.first(request, "addressId"),
                HttpSupport.first(request, "status"),
                HttpSupport.pageParam(request),
                HttpSupport.sizeParam(request, 10));
        var result = checkService.adminPage(query);
        List<Map<String, Object>> records = result.records().stream()
                .map(record -> Views.inboundCheckView(record, addressOf(record.addressId()), framework))
                .toList();
        return PluginHttpResponse.ok(Views.page(records, result.total()));
    }

    // ================= 用户端 =================

    public PluginHttpResponse mySenders() {
        return PluginHttpResponse.ok(addressService.listEnabled().stream()
                .map(Views::addressOptionView)
                .toList());
    }

    public PluginHttpResponse sendMyMail(PluginHttpRequest request) {
        String userId = HttpSupport.requireUserId(request);
        SendMailRequest body = json.read(request, SendMailRequest.class);
        OutboundRecord record = outboundService.send(body.toCmd(), OutboundRecord.Source.USER, userId);
        failFast(record, OutboundRecord.Source.USER);
        return PluginHttpResponse.ok(Views.outboundView(record, framework, true));
    }

    public PluginHttpResponse myMessages(PluginHttpRequest request) {
        String userId = HttpSupport.requireUserId(request);
        var result = outboundService.myPage(userId, HttpSupport.pageParam(request),
                HttpSupport.sizeParam(request, 10));
        return PluginHttpResponse.ok(Views.page(result.records().stream()
                .map(record -> Views.outboundView(record, framework, false))
                .toList(), result.total()));
    }

    /** 用户端详情：非本人记录一律按不存在处理，不泄露他人记录是否存在。 */
    public PluginHttpResponse myMessage(PluginHttpRequest request) {
        String userId = HttpSupport.requireUserId(request);
        String id = HttpSupport.segmentAfter(request.path(), "messages");
        return PluginHttpResponse.ok(Views.outboundView(outboundService.requireOwned(id, userId), framework, true));
    }

    // ================= 内部工具 =================

    private String addressId(PluginHttpRequest request) {
        return HttpSupport.segmentAfter(request.path(), "addresses");
    }

    private MailAddress addressOf(String addressId) {
        try {
            return addressService.requireExisting(addressId);
        }
        catch (NotFoundException e) {
            return null;
        }
    }

    /** 投递失败已在应用层落审计记录，这里再抛业务错误让前端走标准错误反馈。 */
    private void failFast(OutboundRecord record, OutboundRecord.Source source) {
        if (record.status() != OutboundRecord.Status.FAILED) {
            return;
        }
        if (source == OutboundRecord.Source.USER) {
            // 普通用户不需要看到 SMTP 服务器细节，管理员可在发信记录里看到完整原因
            throw new IllegalArgumentException("邮件发送失败，请联系管理员");
        }
        throw new IllegalArgumentException("邮件发送失败：" + record.errorMessage());
    }
}
