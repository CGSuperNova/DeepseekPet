package org.buzizi.deepseekpet.interaction.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 鲸鱼娘交互 GUI。
 *
 * <p>使用 Paper 1.21.6+ 的 <b>Menu Type API</b>（{@link MenuType}）创建原生菜单视图，
 * 而不是旧的 {@code Bukkit.createInventory(holder, ...)}。每个玩家对应一个
 * {@link InventoryView}，由 {@link GuiListener} 通过 {@link #isView(Player, InventoryView)} 识别。</p>
 */
public final class InteractionGui {

    public static final String BUTTON_TALK = "talk";
    private static final String KEY = "deepseekpet.button";

    private final JavaPlugin plugin;
    private final Consumer<Player> onTalk;
    /** 玩家 -> 当前打开的视图，用于点击识别与关闭清理。 */
    private final Map<UUID, InventoryView> views = new ConcurrentHashMap<>();

    public InteractionGui(JavaPlugin plugin, Consumer<Player> onTalk) {
        this.plugin = plugin;
        this.onTalk = onTalk;
    }

    /** 打开交互 GUI。 */
    public void open(Player player) {
        InventoryView view = MenuType.GENERIC_9X1.builder()
                .title(Component.text("鲸鱼娘 · 互动", NamedTextColor.AQUA))
                .build(player);
        Inventory top = view.getTopInventory();
        top.setItem(4, talkButton());
        views.put(player.getUniqueId(), view);
        view.open();
    }

    /** 判断某次点击是否发生在该玩家的鲸鱼娘 GUI 上。 */
    public boolean isView(Player player, InventoryView view) {
        InventoryView stored = views.get(player.getUniqueId());
        return stored != null && stored.equals(view);
    }

    /** 玩家关闭界面时清理记录。 */
    public void close(Player player) {
        views.remove(player.getUniqueId());
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
}
