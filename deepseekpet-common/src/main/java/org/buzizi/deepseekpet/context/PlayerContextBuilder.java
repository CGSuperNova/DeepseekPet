package org.buzizi.deepseekpet.context;

import org.bukkit.entity.Player;
import org.buzizi.deepseekpet.core.config.ConfigManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 汇总各 PlayerContextProvider 的产出，作为「主人当前信息」注入对话。
 *
 * <p>性能：结果按玩家缓存（默认 3 秒），玩家连发消息不会反复解析背包 / PAPI。</p>
 */
public final class PlayerContextBuilder {

    private final ConfigManager config;
    private final List<PlayerContextProvider> providers = new CopyOnWriteArrayList<>();
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public PlayerContextBuilder(ConfigManager config) {
        this.config = config;
        // free 自带的读取器
        providers.add(new VaultContextProvider(config));
        providers.add(new InventoryContextProvider(config));
    }

    /** premium 通过它注册额外的读取器。 */
    public void register(PlayerContextProvider provider) {
        if (provider != null) {
            providers.add(provider);
        }
    }

    /** 构建玩家信息文本；未启用或全无信息时返回空字符串。 */
    public String build(Player player) {
        if (!config.infoEnabled()) {
            return "";
        }
        int ttl = Math.max(0, config.infoCacheSeconds());
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(player.getUniqueId());
        if (cached != null && ttl > 0 && now - cached.ts() < ttl * 1000L) {
            return cached.value();
        }

        StringBuilder sb = new StringBuilder();
        for (PlayerContextProvider provider : providers) {
            try {
                if (!provider.enabled()) {
                    continue;
                }
                String line = provider.provide(player);
                if (line != null && !line.isBlank()) {
                    sb.append(line).append('\n');
                }
            } catch (Throwable ignored) {
                // 单个读取器失败不影响其它
            }
        }
        String value = sb.toString().strip();
        cache.put(player.getUniqueId(), new CacheEntry(now, value));
        return value;
    }

    public List<PlayerContextProvider> providers() {
        return List.copyOf(providers);
    }

    private record CacheEntry(long ts, String value) {
    }
}
