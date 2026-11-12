package org.buzizi.deepseekpet.communication.persona;

import org.bukkit.configuration.file.YamlConfiguration;
import org.buzizi.deepseekpet.platform.config.ConfigManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 鲸鱼娘角色卡：从 persona.yml 读取并拼接成 system prompt。
 */
public final class Persona {

    private final String name;
    private final String displayName;
    private final String systemPrompt;
    private final List<String> catchphrases;

    public Persona(ConfigManager config) {
        YamlConfiguration yml = config.persona();
        this.name = yml.getString("name", "鲸鱼娘");
        this.displayName = yml.getString("display-name", "鲸鱼娘");
        this.catchphrases = config.stringList(yml, "catchphrases");

        String catchLines = catchphrases.stream()
                .map(s -> "「" + s + "」")
                .collect(Collectors.joining("\n"));

        String template = yml.getString("system-prompt", defaultPrompt());
        this.systemPrompt = template
                .replace("{name}", name)
                .replace("{display-name}", displayName)
                .replace("{catchphrases}", catchLines);
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public List<String> catchphrases() {
        return catchphrases;
    }

    private static String defaultPrompt() {
        return "你正在扮演一只叫做「鲸鱼娘」的 Minecraft 服务器宠物，元气可爱、有点小傲娇，"
                + "永远用中文、软萌的语气和主人聊天。知识类问题要依据给出的资料回答，"
                + "资料里没有就坦白说不知道，不要编造，也不要自称 AI。";
    }
}
