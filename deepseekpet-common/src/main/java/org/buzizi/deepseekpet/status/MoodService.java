package org.buzizi.deepseekpet.status;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.DeepseekPetCore;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 心情服务：维护 0-100 心情值、按饥饿区间自然增减，并为 AI 提供心情对话风格。
 */
public final class MoodService {

    private final JavaPlugin plugin;
    private final DeepseekPetCore core;
    private final MoodStore store;
    private final Map<UUID, Long> lastApply = new ConcurrentHashMap<>();
    private int taskId = -1;

    public MoodService(JavaPlugin plugin, DeepseekPetCore core) {
        this.plugin = plugin;
        this.core = core;
        this.store = new MoodStore(plugin);
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    public void disable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        store.flush();
    }

    public int value(UUID id) {
        return store.get(id, core.config().moodInitial());
    }

    public Mood mood(UUID id) {
        return Mood.fromValue(value(id));
    }

    public void set(UUID id, int value) {
        store.set(id, Math.max(0, Math.min(100, value)));
    }

    public void adjust(UUID id, int delta) {
        if (delta != 0) {
            set(id, value(id) + delta);
        }
    }

    public String style(Mood mood) {
        return core.config().moodStyle(mood.name());
    }

    public String statusLine(UUID id) {
        return mood(id).display() + "（" + value(id) + "/100）";
    }

    /** 每秒检查一次：按当前饥饿值所在区间，每过一段时间增减心情。 */
    private void tick() {
        long now = System.currentTimeMillis();
        List<Map<?, ?>> rules = core.config().moodRules();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            int hunger = core.feeding().hunger(id);
            for (Map<?, ?> rule : rules) {
                int min = (int) number(rule.get("hunger-min"), 0);
                int max = (int) number(rule.get("hunger-max"), 20);
                if (hunger < min || hunger > max) {
                    continue;
                }
                long intervalMs = number(rule.get("interval-seconds"), 30) * 1000L;
                if (now - lastApply.getOrDefault(id, 0L) < intervalMs) {
                    break;
                }
                lastApply.put(id, now);
                adjust(id, (int) number(rule.get("amount"), 0));
                break;
            }
        }
        store.flush();
    }

    private static long number(Object raw, long def) {
        return raw instanceof Number n ? n.longValue() : def;
    }
}
