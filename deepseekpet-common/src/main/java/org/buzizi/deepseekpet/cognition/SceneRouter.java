package org.buzizi.deepseekpet.cognition;

import org.bukkit.entity.Player;

/**
 * L4 场景路由：用轻量规则判定当前是陪玩 / 答疑 / 情境陪伴。
 */
public final class SceneRouter {

    private final PromptService prompt;

    public SceneRouter(PromptService prompt) {
        this.prompt = prompt;
    }

    public Scene detect(Player player, String message) {
        // 情境陪伴：血量低于 20%
        if (player.getHealth() <= player.getMaxHealth() * 0.2) {
            return Scene.SITUATIONAL;
        }
        String text = message == null ? "" : message;
        for (String keyword : prompt.qaKeywords()) {
            if (text.contains(keyword)) {
                return Scene.QA;
            }
        }
        return Scene.PLAY;
    }
}
