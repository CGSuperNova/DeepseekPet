package org.buzizi.deepseekpet.cognition;

import org.bukkit.configuration.ConfigurationSection;
import org.buzizi.deepseekpet.communication.persona.Persona;
import org.buzizi.deepseekpet.platform.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示词分层（L1 身份 / L2 聊天习惯 / L3 颜色语法 / L4 场景路由）：
 * 从 persona.yml 的 layers 读取，运行时按场景拼接成完整 system prompt。
 */
public final class PromptService {

    private final String l1;
    private final String l2;
    private final String l3;
    private final String l2QaExtra;
    private final String l2SituationalExtra;
    private final String l3Qa;
    private final String l3Situational;
    private final List<String> qaKeywords = new ArrayList<>();

    public PromptService(Persona persona, ConfigManager config) {
        ConfigurationSection layers = config.persona().getConfigurationSection("layers");
        String catches = String.join("；", persona.catchphrases());
        this.l1 = get(layers, "L1.text").replace("{catchphrases}", catches);
        this.l2 = get(layers, "L2.text");
        this.l3 = get(layers, "L3.text");
        this.l2QaExtra = get(layers, "L4.L2-qa-extra");
        this.l2SituationalExtra = get(layers, "L4.L2-situational-extra");
        this.l3Qa = get(layers, "L4.L3-qa");
        this.l3Situational = get(layers, "L4.L3-situational");
        qaKeywords.addAll(config.persona().getStringList("layers.L4.qa-keywords"));
    }

    /** 按场景 + 玩家上下文拼接完整 system prompt。 */
    public String buildSystem(Scene scene, String playerContext) {
        StringBuilder sb = new StringBuilder(1024);
        append(sb, l1);
        append(sb, l2);
        if (scene == Scene.QA) {
            append(sb, l2QaExtra);
        } else if (scene == Scene.SITUATIONAL) {
            append(sb, l2SituationalExtra);
        }
        if (scene == Scene.QA) {
            append(sb, l3Qa);
        } else if (scene == Scene.SITUATIONAL) {
            append(sb, l3Situational);
        } else {
            append(sb, l3);
        }
        if (playerContext != null && !playerContext.isBlank()) {
            sb.append("\n【主人当前信息】\n").append(playerContext).append('\n');
        }
        return sb.toString().strip();
    }

    public List<String> qaKeywords() {
        return qaKeywords;
    }

    private static String get(ConfigurationSection section, String path) {
        return section == null ? "" : section.getString(path, "");
    }

    private static void append(StringBuilder sb, String text) {
        if (text != null && !text.isBlank()) {
            sb.append(text.strip()).append("\n\n");
        }
    }
}
