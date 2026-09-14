package online.yudream.base.plugin.invite.application;

import java.util.List;
import java.util.UUID;
import online.yudream.base.plugin.invite.domain.InviteBinding;
import online.yudream.base.plugin.invite.infrastructure.InviteBindingRepository;

/**
 * 邀请码绑定用例。
 * 规则：一个邀请码全局只能被一个用户绑定；一个用户可绑定多个邀请码、数量不限。
 * 邀请码字符串来自另一个皮肤站，本插件不校验其有效性。
 * 绑定后用户不允许自行解绑；删除绑定记录只能由管理员通过 adminDelete 执行。
 */
public class InviteBindingService {

    public static final int CODE_MAX_LENGTH = 128;
    public static final int REMARK_MAX_LENGTH = 200;

    private final InviteBindingRepository bindings;

    public InviteBindingService(InviteBindingRepository bindings) {
        this.bindings = bindings;
    }

    // ---- 用户端 ----

    public PageResult<InviteBinding> listMine(String principalId, int page, int size) {
        return slice(bindings.findByOwner(principalId), page, size);
    }

    public InviteBinding bindMine(String principalId, String code, String remark) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("邀请码不能为空");
        }
        if (normalized.length() > CODE_MAX_LENGTH) {
            throw new IllegalArgumentException("邀请码长度不能超过 " + CODE_MAX_LENGTH + " 个字符");
        }
        String safeRemark = remark == null ? "" : remark.trim();
        if (safeRemark.length() > REMARK_MAX_LENGTH) {
            throw new IllegalArgumentException("备注长度不能超过 " + REMARK_MAX_LENGTH + " 个字符");
        }
        bindings.findByCode(normalized).ifPresent(existing -> {
            if (existing.ownerId().equals(principalId)) {
                throw new IllegalArgumentException("你已经绑定过这个邀请码");
            }
            throw new IllegalArgumentException("该邀请码已被其他用户绑定");
        });
        InviteBinding binding = new InviteBinding(
                newId(), normalized, principalId, safeRemark, System.currentTimeMillis());
        return bindings.save(binding);
    }

    // ---- 管理端 ----

    public PageResult<InviteBinding> adminPage(String keyword, int page, int size) {
        List<InviteBinding> all = bindings.findAll();
        String needle = keyword == null ? "" : keyword.trim().toLowerCase();
        List<InviteBinding> matched = needle.isEmpty()
                ? all
                : all.stream()
                        .filter(binding -> binding.code().toLowerCase().contains(needle)
                                || binding.ownerId().toLowerCase().contains(needle)
                                || binding.remark().toLowerCase().contains(needle))
                        .toList();
        return slice(matched, page, size);
    }

    public long count() {
        return bindings.count();
    }

    public void adminDelete(String bindingId) {
        bindings.find(bindingId)
                .orElseThrow(() -> new NotFoundException("绑定记录不存在"));
        bindings.delete(bindingId);
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static <T> PageResult<T> slice(List<T> records, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min((safePage - 1) * safeSize, records.size());
        int to = Math.min(from + safeSize, records.size());
        return new PageResult<>(records.subList(from, to), records.size());
    }
}
