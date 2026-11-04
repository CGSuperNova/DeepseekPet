package org.buzizi.deepseekpet.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.core.pet.PetInstance;

/**
 * 右键点击鲸鱼娘：
 * <ul>
 *   <li>手持可食用物品 → 喂食（双击确认，防误触）；</li>
 *   <li>否则 → 打开交互 GUI。</li>
 * </ul>
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

        boolean offHand = event.getHand() == EquipmentSlot.OFF_HAND;
        ItemStack item = offHand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (core.feeding().isFood(item)) {
            core.feeding().handleFeedRequest(player, pet, item, offHand);
        } else {
            core.openInteractionGui(player);
        }
    }
}
