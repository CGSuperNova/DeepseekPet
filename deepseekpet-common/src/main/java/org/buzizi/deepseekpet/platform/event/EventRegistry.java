package org.buzizi.deepseekpet.platform.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件注册表：按唯一 id 注册 {@link PetEvent}，并负责冲突检测与执行。
 *
 * <p>冲突规则：同一玩家已有事件在执行时，新事件默认被拒绝；
 * {@code force=true} 时无视冲突强制执行。</p>
 */
public final class EventRegistry {

    private final JavaPlugin plugin;
    private final Map<String, PetEvent> events = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    public EventRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(PetEvent event) {
        if (event != null && event.id() != null && !event.id().isBlank()) {
            events.put(event.id(), event);
        }
    }

    public void clear() {
        events.clear();
    }

    public PetEvent get(String id) {
        return events.get(id);
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(events.keySet());
    }

    public List<PetEvent> all() {
        return new ArrayList<>(events.values());
    }

    public boolean isBusy(Player player) {
        return busy.contains(player.getUniqueId());
    }

    /** 执行事件；返回 false 表示事件不存在或被冲突拒绝。 */
    public boolean execute(String id, Player player, boolean force) {
        PetEvent event = events.get(id);
        if (event == null) {
            return false;
        }
        UUID key = player.getUniqueId();
        if (!force && !busy.add(key)) {
            return false;
        }
        try {
            event.execute(player);
        } catch (Throwable t) {
            plugin.getLogger().warning("执行事件 " + id + " 失败：" + t.getMessage());
        } finally {
            if (!force) {
                int duration = event.durationTicks();
                if (duration <= 0) {
                    busy.remove(key);
                } else {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> busy.remove(key), duration);
                }
            }
        }
        return true;
    }
}
