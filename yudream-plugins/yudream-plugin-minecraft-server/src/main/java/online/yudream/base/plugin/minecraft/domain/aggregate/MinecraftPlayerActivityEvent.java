package online.yudream.base.plugin.minecraft.domain.aggregate;

import java.util.UUID;

/**
 * 一条玩家活动事件，是时间窗统计（在线时长、挂机时长、有效时长）的唯一依据。
 *
 * <p>{@code subServer} 记录这条事件发生在哪台子服上，空字符串表示「没有子服维度」：单机服、
 * 扁平分组的旧式上报，或是整服级别的收尾（服务端离线、扁平快照）。它让回放能只取某一台子服的
 * 事件——群组服下同一个玩家的时间分散在多台子服上，不分维度就回答不了「他在 fabric 上待了多久」。
 *
 * <p>改造之前写入的事件没有这个字段，读取时按空字符串处理，即归入「无子服维度」。因此按具体子服
 * 统计时这些历史事件不会被计入任何子服，只有改造之后的进出服才能按子服归属。
 */
public record MinecraftPlayerActivityEvent(
        String id,
        String serverId,
        String playerId,
        String playerName,
        String subServer,
        Type type,
        long occurredAt
) {
    public enum Type { JOIN, QUIT, AFK_START, AFK_END, SERVER_OFFLINE, SERVER_SNAPSHOT }

    public MinecraftPlayerActivityEvent {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        serverId = requireText(serverId, "Server ID is required");
        playerId = requireText(playerId, "Player ID is required");
        playerName = playerName == null ? "" : playerName.trim();
        subServer = subServer == null ? "" : subServer.trim();
        if (type == null) throw new IllegalArgumentException("Activity event type is required");
        occurredAt = occurredAt <= 0 ? System.currentTimeMillis() : occurredAt;
    }

    /** 不带子服维度的事件：整服级别的收尾与扁平分组的旧式上报走这个重载。 */
    public static MinecraftPlayerActivityEvent create(String serverId, String playerId, String playerName, Type type, long occurredAt) {
        return create(serverId, playerId, playerName, "", type, occurredAt);
    }

    public static MinecraftPlayerActivityEvent create(String serverId, String playerId, String playerName, String subServer, Type type, long occurredAt) {
        return new MinecraftPlayerActivityEvent(null, serverId, playerId, playerName, subServer, type, occurredAt);
    }

    /**
     * 回放某个子服的时间窗时，这条事件是否参与。
     *
     * <p>两侧的空值都表示「不限定」：过滤器为空就是整服统计；事件自身为空说明它没有子服维度
     * （整服级别的收尾，或旧的扁平上报），这种事件必须继续参与任意子服的回放——否则一次服务端
     * 离线就关不掉某个子服上仍然开着的区间，时长会一路算到窗口末尾。
     *
     * <p>因此只有「过滤器指定了子服」且「事件指定了另一个子服」时才排除。
     */
    public boolean appliesToSubServer(String filter) {
        String target = filter == null ? "" : filter.trim();
        return target.isEmpty() || subServer.isEmpty() || target.equals(subServer);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
