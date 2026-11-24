package org.buzizi.deepseekpet.communication.post;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * 颜色净化：只放行白名单内的 {@code &} 代码，移除无效组合、
 * 末尾孤立 {@code &}，并限制 {@code &k} 混淆区长度。
 */
public final class ReplySanitizer {

    private static final Set<Character> FULL = Set.of('7', 'f', 'b', 'e', 'c', 'd', 'l', 'o', 'k', 'r');
    private static final Set<Character> QA = Set.of('7', 'e', 'f', 'l', 'r');

    private final JavaPlugin plugin;

    public ReplySanitizer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** @param qa 答疑模式使用更保守的颜色白名单 */
    public String sanitize(String raw, boolean qa) {
        if (raw == null) {
            return "";
        }
        Set<Character> allowed = qa ? QA : FULL;
        StringBuilder out = new StringBuilder(raw.length());
        int kLeft = -1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                char next = Character.toLowerCase(raw.charAt(i + 1));
                if (allowed.contains(next)) {
                    out.append('&').append(next);
                    if (next == 'k') {
                        kLeft = 3;
                    }
                    i++;
                    continue;
                }
                // 无效代码：去掉 &，保留原字符
                continue;
            }
            out.append(c);
            if (kLeft > 0) {
                kLeft--;
                if (kLeft == 0) {
                    out.append("&r");
                    kLeft = -1;
                }
            }
        }
        String result = out.toString();
        if (result.endsWith("&")) {
            plugin.getLogger().warning("[AI净化] 检测到末尾孤立 &，已移除");
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
