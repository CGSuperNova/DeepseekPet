package org.buzizi.deepseekpet.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.buzizi.deepseekpet.DeepseekPetCore;

/**
 * 对话模式：玩家在聊天栏输入的内容发给鲸鱼娘。
 */
public final class ChatInputListener implements Listener {

    private final DeepseekPetCore core;

    public ChatInputListener(DeepseekPetCore core) {
        this.core = core;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!core.isInDialog(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        core.handleDialogMessage(event.getPlayer(), event.getMessage());
    }
}
