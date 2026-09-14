package online.yudream.base.plugin.minecraft.api;

import java.util.List;
import java.util.Optional;

/** Stable service contract exposed by the minecraft-server plugin. */
public interface PluginMinecraftService {
    List<PluginMinecraftServer> minecraftServers(boolean includeDisabled);
    Optional<PluginMinecraftServer> minecraftServer(String serverId);
    List<PluginMinecraftPlayerActivity> minecraftPlayerActivities(String serverId, int page, int size);
    default Optional<PluginMinecraftOnlineWindow> minecraftOnlineWindow(String serverId, String playerId,
                                                                         long windowStart, long windowEnd) {
        return Optional.empty();
    }

    /** Players online on the server at any moment inside [windowStart, windowEnd], with their in-window durations. */
    default List<PluginMinecraftActivePlayer> minecraftActivePlayers(String serverId, long windowStart,
                                                                     long windowEnd) {
        return List.of();
    }

    /**
     * 玩家在各子服上的时长拆分（新增读取方法，不改变既有方法的签名与语义）。
     *
     * <p>群组服下同一玩家的时间会分散在多个子服上；{@link #minecraftPlayerActivities} 仍然返回
     * 跨子服的合计，本方法返回它的明细。没有子服维度时只有一条 {@code "default"} 记录。
     * 旧版本提供方混跑时自动降级为空列表。
     */
    default List<PluginMinecraftSubServerActivity> minecraftSubServerActivities(String serverId, String playerId) {
        return List.of();
    }
}
