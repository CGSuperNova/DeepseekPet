package org.buzizi.deepseekpet.status;

import org.bukkit.entity.Player;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.context.PlayerContextProvider;

/**
 * Status 板块 -> Cognition：把鲸鱼娘当前心情与对话风格注入 AI 上下文。
 */
public final class MoodContextProvider implements PlayerContextProvider {

    private final DeepseekPetCore core;

    public MoodContextProvider(DeepseekPetCore core) {
        this.core = core;
    }

    @Override
    public String id() {
        return "mood";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String provide(Player player) {
        Mood mood = core.mood().mood(player.getUniqueId());
        return "- 鲸鱼娘心情：" + mood.display() + "（" + core.mood().value(player.getUniqueId()) + "/100）"
                + "；说话风格：" + core.mood().style(mood);
    }
}
