package org.buzizi.deepseekpet.listener;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.core.pet.PetInstance;

/**
 * Citizens NPC 右键：喂食（主手）或打开交互 GUI。
 * 仅当 Citizens 存在时才注册本监听器（见 DeepseekPetCore）。
 */
public final class CitizensInteractListener implements Listener {

    private final DeepseekPetCore core;

    public CitizensInteractListener(DeepseekPetCore core) {
        this.core = core;
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        LivingEntity entity = event.getNPC().getEntity();
        if (entity == null) {
            return;
        }
        PetInstance pet = core.pets().byEntity(entity);
        if (pet == null) {
            return;
        }
        Player player = event.getClicker();
        if (!pet.owner().getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);

        ItemStack item = player.getInventory().getItemInMainHand();
        if (core.feeding().isFood(item)) {
            core.feeding().handleFeedRequest(player, pet, item, false);
        } else {
            core.openInteractionGui(player);
        }
    }
}
