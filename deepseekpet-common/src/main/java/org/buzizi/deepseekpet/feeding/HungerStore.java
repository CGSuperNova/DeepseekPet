package org.buzizi.deepseekpet.feeding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 饥饿值持久化：存到 plugins/DeepseekPet/data/hunger.json。
 * 使用「脏标记 + 定时落盘」避免高频写文件。
 */
public final class HungerStore {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() {
    }.getType();

    private final JavaPlugin plugin;
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Integer> data = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public HungerStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data").resolve("hunger.json");
        load();
    }

    private void load() {
        try {
            Files.createDirectories(file.getParent());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "无法创建饥饿值数据目录 " + file.getParent(), e);
            return;
        }
        if (!Files.exists(file)) {
            return;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Integer> loaded = gson.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                data.clear();
                data.putAll(loaded);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "读取饥饿值数据失败：" + file, e);
        }
    }

    public int get(UUID playerId, int initial) {
        Integer value = data.get(playerId.toString());
        return value == null ? initial : value;
    }

    public void set(UUID playerId, int value) {
        data.put(playerId.toString(), value);
        dirty = true;
    }

    public void flushIfDirty() {
        if (dirty) {
            save();
            dirty = false;
        }
    }

    public void flush() {
        save();
        dirty = false;
    }

    private void save() {
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(data, MAP_TYPE, writer);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "保存饥饿值数据失败：" + file, e);
        }
    }
}
