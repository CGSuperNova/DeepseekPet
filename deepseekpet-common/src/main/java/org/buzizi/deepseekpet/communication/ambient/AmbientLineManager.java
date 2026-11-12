package org.buzizi.deepseekpet.communication.ambient;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.platform.config.ConfigManager;
import org.buzizi.deepseekpet.avatar.PetInstance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时 / 条件随机台词：满足条件时从固定文本里随机抽一句，
 * 以悬浮字显示在每只鲸鱼娘头顶，并播放客户端 UI 点击音效。
 */
public final class AmbientLineManager {

    /** 非 interval 规则的最小触发间隔，避免在时间段内刷屏。 */
    private static final long DEFAULT_COOLDOWN_MS = 30 * 60 * 1000L;

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final DeepseekPetCore core;

    private final List<AmbientRule> rules = new ArrayList<>();
    private final Map<String, Long> lastFire = new ConcurrentHashMap<>();
    private int taskId = -1;

    public AmbientLineManager(JavaPlugin plugin, ConfigManager config, DeepseekPetCore core) {
        this.plugin = plugin;
        this.config = config;
        this.core = core;
    }

    public void start() {
        if (!config.ambientEnabled()) {
            return;
        }
        reloadRules();
        long period = Math.max(5, config.ambientCheckIntervalSeconds()) * 20L;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period).getTaskId();
    }

    public void reloadRules() {
        rules.clear();
        ConfigurationSection rulesSection = config.ambient().getConfigurationSection("rules");
        if (rulesSection == null) {
            return;
        }
        for (String key : rulesSection.getKeys(false)) {
            ConfigurationSection section = rulesSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            rules.add(AmbientRule.from(key, section));
        }
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        taskId = -1;
    }

    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        int tolerance = Math.max(5, config.ambientCheckIntervalSeconds());
        for (AmbientRule rule : rules) {
            if (!rule.enabled() || !rule.hasLines()) {
                continue;
            }
            long cooldown = rule.intervalSeconds() > 0
                    ? rule.intervalSeconds() * 1000L
                    : DEFAULT_COOLDOWN_MS;
            long last = lastFire.getOrDefault(rule.id(), 0L);
            if (System.currentTimeMillis() - last < cooldown) {
                continue;
            }
            if (!rule.matches(now, tolerance)) {
                continue;
            }
            lastFire.put(rule.id(), System.currentTimeMillis());
            fire(rule);
        }
    }

    private void fire(AmbientRule rule) {
        String line = rule.randomLine();
        if (line == null || line.isBlank()) {
            return;
        }
        int seconds = Math.max(1, config.ambientFloatSeconds());
        for (PetInstance pet : core.pets().all()) {
            if (pet.isValid()) {
                pet.showFloatingText(line, seconds);
                pet.playClickSound();
            }
        }
    }
}
