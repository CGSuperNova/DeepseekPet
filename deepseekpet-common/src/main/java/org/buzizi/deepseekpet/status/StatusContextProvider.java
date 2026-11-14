package org.buzizi.deepseekpet.status;

import org.bukkit.entity.Player;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.context.PlayerContextProvider;

/**
 * Status 板块 -> Cognition：把鲸鱼娘自己的状态（饱食度等）注入 AI 上下文，
 * 让鲸鱼娘能「感知自己」。
 */
public final class StatusContextProvider implements PlayerContextProvider {

    private final DeepseekPetCore core;

    public StatusContextProvider(DeepseekPetCore core) {
        this.core = core;
    }

    @Override
    public String id() {
        return "status";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String provide(Player player) {
        return "- 鲸鱼娘状态：" + core.feeding().statusLine(player.getUniqueId());
    }
}
