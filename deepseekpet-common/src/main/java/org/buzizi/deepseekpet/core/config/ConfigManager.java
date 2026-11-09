package org.buzizi.deepseekpet.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 负责加载 / 重载三个配置文件：config.yml、persona.yml、ambient.yml。
 */
public final class ConfigManager {

    private final JavaPlugin plugin;

    private YamlConfiguration config;
    private YamlConfiguration persona;
    private YamlConfiguration ambient;
    private YamlConfiguration feeding;
    private YamlConfiguration feedingFoods;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = (YamlConfiguration) plugin.getConfig();
        this.persona = load("persona.yml");
        this.ambient = load("ambient.yml");
        this.feeding = load("feeding/config.yml");
        this.feedingFoods = load("feeding/foods.yml");
    }

    private YamlConfiguration load(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration config() {
        return config;
    }

    public YamlConfiguration persona() {
        return persona;
    }

    public YamlConfiguration ambient() {
        return ambient;
    }

    public YamlConfiguration feeding() {
        return feeding;
    }

    public YamlConfiguration feedingFoods() {
        return feedingFoods;
    }

    // ---------------------------------------------------------------- pet
    public boolean autoSpawnOnJoin() {
        return config.getBoolean("pet.auto-spawn-on-join", true);
    }

    public String entityId() {
        return config.getString("pet.entity-id", "whalechan:whalechan");
    }

    public String nameFormat() {
        return config.getString("pet.name-format", "玩家 %player% 的鲸鱼娘");
    }

    public boolean showNameTag() {
        return config.getBoolean("pet.show-name-tag", true);
    }

    // ---------------------------------------------------------------- ai
    public String ragUrl() {
        String url = config.getString("ai.rag-url", "http://127.0.0.1:8100");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String ragApiKey() {
        return config.getString("ai.rag-api-key", "");
    }

    public int timeoutSeconds() {
        return config.getInt("ai.timeout-seconds", 120);
    }

    public boolean fallbackToLlm() {
        return config.getBoolean("ai.fallback-to-llm", true);
    }

    public String llmBaseUrl() {
        return config.getString("ai.llm.base-url", "https://api.deepseek.com");
    }

    public String llmApiKey() {
        return config.getString("ai.llm.api-key", "");
    }

    public String llmModel() {
        return config.getString("ai.llm.model", "deepseek-chat");
    }

    public double llmTemperature() {
        return config.getDouble("ai.llm.temperature", 0.7);
    }

    public int llmMaxTokens() {
        return config.getInt("ai.llm.max-tokens", 2048);
    }

    public int memoryMaxTurns() {
        return config.getInt("ai.memory-max-turns", 20);
    }

    // ---------------------------------------------------------------- ambient
    public boolean ambientEnabled() {
        return config.getBoolean("ambient.enabled", true);
    }

    public int ambientFloatSeconds() {
        return config.getInt("ambient.float-seconds", 4);
    }

    public int ambientCheckIntervalSeconds() {
        return config.getInt("ambient.check-interval-seconds", 30);
    }

    // ---------------------------------------------------------------- follow / fallback / info
    public boolean followDefaultEnabled() {
        return config.getBoolean("follow.default-enabled", true);
    }

    public double armorStandFollowDistance() {
        return config.getDouble("fallback.armorstand.follow-distance", 10);
    }

    public double armorStandTpDistance() {
        return config.getDouble("fallback.armorstand.tp-distance", 2);
    }

    public boolean infoEnabled() {
        return config.getBoolean("info.enabled", true);
    }

    public boolean infoVault() {
        return config.getBoolean("info.vault", true);
    }

    public boolean infoInventory() {
        return config.getBoolean("info.inventory", true);
    }

    public int infoCacheSeconds() {
        return config.getInt("info.cache-seconds", 3);
    }

    public String infoCustomPlaceholders() {
        return config.getString("info.custom-placeholders", "");
    }

    // ---------------------------------------------------------------- messages
    public String message(String key, String def) {
        return config.getString("messages." + key, def);
    }

    public List<String> stringList(YamlConfiguration section, String path) {
        Object raw = section.get(path);
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        String single = section.getString(path);
        return single == null ? List.of() : List.of(single);
    }
}
