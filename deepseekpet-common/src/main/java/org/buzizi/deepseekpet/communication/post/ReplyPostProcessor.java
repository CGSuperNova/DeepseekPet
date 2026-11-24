package org.buzizi.deepseekpet.communication.post;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.cognition.Scene;
import org.buzizi.deepseekpet.platform.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型输出后处理链：原始日志 -> 颜色净化 -> 空回复兜底 -> 长度/句子边界拆分。
 * 返回的分段仍保留 {@code &} 颜色代码，由发送端统一转义。
 */
public final class ReplyPostProcessor {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ReplySanitizer sanitizer;

    public ReplyPostProcessor(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.sanitizer = new ReplySanitizer(plugin);
    }

    public List<String> process(Player player, String raw, Scene scene) {
        plugin.getLogger().info("[AI原始输出] " + (raw == null ? "" : raw));
        String clean = sanitizer.sanitize(raw, scene == Scene.QA);
        if (isBlankAfterCodes(clean)) {
            plugin.getLogger().warning("[AI净化] 净化后为空，使用兜底台词");
            clean = "嗯……让我想想。";
        }
        return split(clean, Math.max(20, config.maxMessageLength()));
    }

    private boolean isBlankAfterCodes(String s) {
        return s == null || s.replaceAll("&[0-9a-fk-or]", "").isBlank();
    }

    private List<String> split(String text, int max) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n", -1)) {
            String seg = line.trim();
            while (seg.length() > max) {
                int cut = seg.lastIndexOf('。', max);
                if (cut <= 0) {
                    cut = seg.lastIndexOf('？', max);
                }
                if (cut <= 0) {
                    cut = seg.lastIndexOf('！', max);
                }
                if (cut <= 0) {
                    cut = seg.lastIndexOf('!', max);
                }
                if (cut <= 0) {
                    cut = seg.lastIndexOf('?', max);
                }
                if (cut <= 0) {
                    cut = max - 1;
                }
                out.add(seg.substring(0, cut + 1).trim());
                seg = seg.substring(cut + 1).trim();
            }
            if (!seg.isEmpty()) {
                out.add(seg);
            }
        }
        return out;
    }
}
