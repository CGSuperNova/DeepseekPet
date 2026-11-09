package org.buzizi.deepseekpet.context;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.buzizi.deepseekpet.core.config.ConfigManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 背包 + 末影箱内容摘要。free 可用。
 *
 * <p>为控制 prompt 长度，物品名用小写 Material 名 + 数量，超过 200 字符截断。</p>
 */
public final class InventoryContextProvider implements PlayerContextProvider {

    private final ConfigManager config;

    public InventoryContextProvider(ConfigManager config) {
        this.config = config;
    }

    @Override
    public String id() {
        return "inventory";
    }

    @Override
    public boolean enabled() {
        return config.infoInventory();
    }

    @Override
    public String provide(Player player) {
        PlayerInventory inventory = player.getInventory();
        String main = summarize(inventory.getContents());
        String ender = summarize(player.getEnderChest().getContents());
        return "- 背包：" + main + "\n- 末影箱：" + ender;
    }

    private String summarize(ItemStack[] contents) {
        if (contents == null) {
            return "空";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            counts.merge(item.getType().name().toLowerCase(), item.getAmount(), Integer::sum);
        }
        if (counts.isEmpty()) {
            return "空";
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (shown++ > 0) {
                sb.append("，");
            }
            sb.append(entry.getKey()).append('×').append(entry.getValue());
            if (sb.length() > 200) {
                sb.append("…");
                break;
            }
        }
        return sb.toString();
    }
}
