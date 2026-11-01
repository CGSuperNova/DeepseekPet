package org.buzizi.deepseekpet.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.core.config.ConfigManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 持久记忆：把每个玩家与鲸鱼娘的对话历史存成 JSON 文件，
 * 让鲸鱼娘「像和现实中的人说话一样」记得之前聊过的内容。
 *
 * <p>存储位置：plugins/DeepseekPet/memory/&lt;uuid&gt;.json</p>
 */
public final class MemoryStore {

    private static final Type MESSAGE_LIST_TYPE = new TypeToken<List<Message>>() {
    }.getType();

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, List<Message>> cache = new ConcurrentHashMap<>();
    private final Path dir;

    public MemoryStore(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.dir = plugin.getDataFolder().toPath().resolve("memory");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法创建记忆目录 " + dir, e);
        }
    }

    public List<Message> getHistory(UUID playerId) {
        List<Message> list = cache.get(playerId);
        if (list != null) {
            return list;
        }
        list = load(playerId);
        cache.put(playerId, list);
        return list;
    }

    /** 追加一条消息并持久化。 */
    public void append(UUID playerId, String role, String content) {
        List<Message> list = getHistory(playerId);
        synchronized (list) {
            list.add(new Message(role, content));
            trim(list);
            save(playerId, list);
        }
    }

    /** 用一句话替换/概括历史（供 premium 记忆摘要等高级功能使用）。 */
    public void replace(UUID playerId, List<Message> messages) {
        cache.put(playerId, new ArrayList<>(messages));
        save(playerId, messages);
    }

    public void clear(UUID playerId) {
        List<Message> list = new ArrayList<>();
        cache.put(playerId, list);
        save(playerId, list);
    }

    private void trim(List<Message> list) {
        int cap = Math.max(2, config.memoryMaxTurns() * 2);
        while (list.size() > cap) {
            list.remove(0);
        }
    }

    private Path file(UUID playerId) {
        return dir.resolve(playerId.toString() + ".json");
    }

    private List<Message> load(UUID playerId) {
        Path path = file(playerId);
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<Message> list = gson.fromJson(reader, MESSAGE_LIST_TYPE);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "读取记忆失败：" + path, e);
            return new ArrayList<>();
        }
    }

    private void save(UUID playerId, List<Message> list) {
        Path path = file(playerId);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(new ArrayList<>(list), MESSAGE_LIST_TYPE, writer);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "保存记忆失败：" + path, e);
        }
    }

    public void flushAll() {
        cache.forEach(this::save);
    }
}
