package org.buzizi.deepseekpet.status;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 心情值持久化（plugins/DeepseekPet/data/mood.json）。 */
public final class MoodStore {

    private final JavaPlugin plugin;
    private final Path file;
    private final Gson gson = new Gson();
    private final Map<UUID, Integer> values = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public MoodStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data").resolve("mood.json");
        load();
    }

    public int get(UUID id, int def) {
        Integer value = values.get(id);
        return value == null ? def : value;
    }

    public void set(UUID id, int value) {
        values.put(id, value);
        dirty = true;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Integer> raw = gson.fromJson(json, new TypeToken<Map<String, Integer>>() {
            }.getType());
            if (raw != null) {
                raw.forEach((key, value) -> {
                    try {
                        values.put(UUID.fromString(key), value);
                    } catch (Exception ignored) {
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("读取 mood.json 失败：" + e.getMessage());
        }
    }

    public void flush() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Map<String, Integer> out = new HashMap<>();
            values.forEach((key, value) -> out.put(key.toString(), value));
            Files.writeString(file, gson.toJson(out), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("写入 mood.json 失败：" + e.getMessage());
        }
    }
}
