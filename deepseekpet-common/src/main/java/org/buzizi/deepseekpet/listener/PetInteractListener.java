package org.buzizi.deepseekpet.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.core.pet.PetInstance;

/**
 * 右键点击鲸鱼娘 → 打开交互 GUI。
 */
public final class PetInteractListener implements Listener {

    private final DeepseekPetCore core;

    public PetInteractListener(DeepseekPetCore core) {
        this.core = core;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        PetInstance pet = core.pets().byEntity(event.getRightClicked());
        if (pet == null) {
            return;
        }
        Player player = event.getPlayer();
        // 只有主人能交互
        if (!pet.owner().getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        core.openInteractionGui(player);
    }
}
