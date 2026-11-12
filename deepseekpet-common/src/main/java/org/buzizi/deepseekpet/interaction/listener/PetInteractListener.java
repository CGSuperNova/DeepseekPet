package org.buzizi.deepseekpet.interaction.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.avatar.PetInstance;

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
        // Citizens NPC 由 CitizensInteractListener 处理，避免重复
        if (event.getRightClicked().hasMetadata("NPC")) {
            return;
        }
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

    /**
     * 禁止任何人替换 / 取下盔甲架鲸鱼娘的装备
     * （右手右键只走上面的 GUI / 喂食逻辑）。
     */
    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        PetInstance pet = core.pets().byEntity(event.getRightClicked());
        if (pet != null) {
            event.setCancelled(true);
        }
    }
}
