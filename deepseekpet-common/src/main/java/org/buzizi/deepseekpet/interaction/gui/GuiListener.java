package org.buzizi.deepseekpet.interaction.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.buzizi.deepseekpet.DeepseekPetCore;

/**
 * 处理鲸鱼娘交互 GUI 的按钮点击（Menu Type API 视图）。
 */
public final class GuiListener implements Listener {

    private final DeepseekPetCore core;

    public GuiListener(DeepseekPetCore core) {
        this.core = core;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InteractionGui gui = core.interactionGui();
        if (!gui.isView(player, event.getView())) {
            return;
        }
        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getInventory())) {
            return; // 只处理 GUI 本体
        }

        String button = gui.buttonOf(event.getCurrentItem());
        if (InteractionGui.BUTTON_TALK.equals(button)) {
            player.closeInventory();
            gui.onTalk().accept(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            core.interactionGui().close(player);
        }
    }
}
