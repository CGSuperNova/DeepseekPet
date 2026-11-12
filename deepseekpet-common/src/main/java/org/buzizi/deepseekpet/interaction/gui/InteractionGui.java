package org.buzizi.deepseekpet.interaction.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * 鲸鱼娘交互 GUI（基于 1.21+ 的 Inventory 自定义界面）。
 * 当前只有一个按钮：「与鲸鱼娘对话」。
 */
public final class InteractionGui implements InventoryHolder {

    public static final String BUTTON_TALK = "talk";
    private static final String KEY = "deepseekpet.button";

    private final JavaPlugin plugin;
    private final Inventory inventory;
    private final Consumer<Player> onTalk;

    public InteractionGui(JavaPlugin plugin, Consumer<Player> onTalk) {
        this.plugin = plugin;
        this.onTalk = onTalk;
        this.inventory = Bukkit.createInventory(this, 9, Component.text("鲸鱼娘 · 互动", NamedTextColor.AQUA));
        this.inventory.setItem(4, talkButton());
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public Consumer<Player> onTalk() {
        return onTalk;
    }

    /** 根据按钮持久化标记判断点击的是什么按钮。 */
    public String buttonOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(plugin, KEY);
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private ItemStack talkButton() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("💬 与鲸鱼娘对话", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("点击后即可给鲸鱼娘发消息", NamedTextColor.GRAY)));
        NamespacedKey key = new NamespacedKey(plugin, KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, BUTTON_TALK);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
