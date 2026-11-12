package org.buzizi.deepseekpet.interaction.feeding;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 喂食系统配置：读取 feeding/config.yml 与 feeding/foods.yml。
 */
public final class FeedingConfig {

    private final YamlConfiguration main;
    private final YamlConfiguration foods;
    private final Map<String, FoodEntry> entries = new HashMap<>();

    public FeedingConfig(YamlConfiguration main, YamlConfiguration foods) {
        this.main = main;
        this.foods = foods;
        parseFoods();
    }

    private void parseFoods() {
        if (foods == null) {
            return;
        }
        ConfigurationSection section = foods.getConfigurationSection("foods");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Integer nutrition = entry.contains("hunger") ? entry.getInt("hunger") : null;
            String message = entry.getString("message");
            List<String> commands = entry.getStringList("commands");
            String animation = entry.getString("animation");
            String afterAnimation = entry.getString("after-animation");
            entries.put(key.toUpperCase(Locale.ROOT),
                    new FoodEntry(nutrition, message, commands, animation, afterAnimation));
        }
    }

    // ---------------------------------------------------------------- 总开关
    public boolean enabled() {
        return main.getBoolean("enabled", true);
    }

    // ---------------------------------------------------------------- 饥饿值
    public int maxHunger() {
        return Math.max(1, main.getInt("hunger.max", 20));
    }

    public int initialHunger() {
        return Math.min(maxHunger(), Math.max(0, main.getInt("hunger.initial", maxHunger())));
    }

    public int naturalAmount() {
        return Math.max(0, main.getInt("hunger.natural.amount", 1));
    }

    public int naturalIntervalSeconds() {
        return Math.max(1, main.getInt("hunger.natural.interval-seconds", 30));
    }

    public int dialogCost() {
        return Math.max(0, main.getInt("hunger.dialog-cost", 1));
    }

    public int hungryThreshold() {
        return Math.max(0, main.getInt("hunger.hungry-threshold", 6));
    }

    public int hungryIntervalSeconds() {
        return Math.max(5, main.getInt("hunger.hungry-interval-seconds", 120));
    }

    public List<String> hungryLines() {
        return main.getStringList("hunger.hungry-lines");
    }

    public String refuseLine() {
        return main.getString("hunger.refuse-line", "本鲸鱼已经饿得没力气说话啦，先喂点东西吧……");
    }

    public String fullLine() {
        return main.getString("hunger.full-line", "本鲸鱼已经吃得饱饱的啦，谢谢主人～");
    }

    // ---------------------------------------------------------------- 喂食
    public double confirmWindowSeconds() {
        return Math.max(0.5, main.getDouble("feeding.confirm-window-seconds", 3.0));
    }

    public String confirmLine() {
        return main.getString("feeding.confirm-line", "&e再右键一次鲸鱼娘，确认喂食「%food%」");
    }

    public String successLine() {
        return main.getString("feeding.success-line",
                "&b鲸鱼娘吃掉了「%food%」，饱食度 +%amount%（%hunger%/%max%）");
    }

    public boolean consumeWhenFull() {
        return main.getBoolean("feeding.consume-when-full", false);
    }

    public String fullFeedLine() {
        return main.getString("feeding.full-feed-line", "&e鲸鱼娘已经吃饱啦，不用再喂了～");
    }

    // ---------------------------------------------------------------- 食物
    public FoodEntry entry(String materialName) {
        return materialName == null ? null : entries.get(materialName.toUpperCase(Locale.ROOT));
    }

    /** 该物品是否为可喂食的食物。 */
    public boolean isFood(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        if (FoodTable.known(material)) {
            return true;
        }
        FoodEntry entry = entries.get(material.name());
        return entry != null && entry.nutrition() != null && entry.nutrition() > 0;
    }

    /** 该食物恢复的饱食度：优先 config 覆盖，否则用原版值。 */
    public int nutrition(Material material) {
        FoodEntry entry = entries.get(material.name());
        if (entry != null && entry.nutrition() != null) {
            return Math.max(0, entry.nutrition());
        }
        return FoodTable.vanilla(material);
    }
}
