package online.yudream.base.plugin.invite.interfaces.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.invite.domain.InviteBinding;

/** 界面视图装配：管理端视图含跨用户信息，用户端视图只含本人数据。 */
public final class Views {
    private Views() {
    }

    /** 用户端视图：本人绑定记录，不含任何其他用户信息。 */
    public static Map<String, Object> myBindingView(InviteBinding binding) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", binding.id());
        view.put("code", binding.code());
        view.put("remark", binding.remark());
        view.put("boundAt", binding.boundAt());
        return view;
    }

    /** 管理端视图：含绑定人标识与用户名。 */
    public static Map<String, Object> adminBindingView(InviteBinding binding, FrameworkServices framework) {
        Map<String, Object> view = myBindingView(binding);
        view.put("userId", binding.ownerId());
        view.put("username", username(binding.ownerId(), framework));
        return view;
    }

    /** 解析用户展示名：优先 nickname，其次 username；用户已删除时回退 ID。 */
    private static String username(String ownerId, FrameworkServices framework) {
        if (framework == null) {
            return ownerId;
        }
        try {
            long userId = Long.parseLong(ownerId);
            Optional<String> name = framework.users().findById(userId)
                    .map(profile -> {
                        String nickname = profile.nickname();
                        return nickname == null || nickname.isBlank() ? profile.username() : nickname;
                    });
            return name.orElse(ownerId);
        }
        catch (NumberFormatException e) {
            return ownerId;
        }
    }
}
