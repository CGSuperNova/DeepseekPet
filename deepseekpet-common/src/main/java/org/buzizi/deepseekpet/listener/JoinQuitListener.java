package org.buzizi.deepseekpet.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.util.Text;

/**
 * 进服自动生成鲸鱼娘，退出时收回。
 */
public final class JoinQuitListener implements Listener {

    private final DeepseekPetCore core;

    public JoinQuitListener(DeepseekPetCore core) {
        this.core = core;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!core.config().autoSpawnOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        // 稍等一瞬，等玩家完全落地再生成
        Bukkit.getScheduler().runTaskLater(core.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            var pet = core.pets().spawnPet(player);
            if (pet != null) {
                player.sendMessage(Text.color(core.config().message("pet-spawned", "&b你的鲸鱼娘已经来到你身边啦～")));
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        core.clearDialog(event.getPlayer().getUniqueId());
        core.pets().despawnPet(event.getPlayer().getUniqueId());
    }
}
