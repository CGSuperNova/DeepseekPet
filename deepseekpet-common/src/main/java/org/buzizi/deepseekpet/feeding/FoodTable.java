package org.buzizi.deepseekpet.feeding;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * 原版食物「玩家食用后恢复的饱食度（饥饿值）」对照表。
 *
 * <p>只作为默认基准，config 里可以按 Material 名称覆盖。
 * 不依赖任何随版本变动的 API，保证跨版本稳定。</p>
 */
public final class FoodTable {

    private static final Map<Material, Integer> NUTRITION = new HashMap<>();

    private FoodTable() {
    }

    public static int vanilla(Material material) {
        if (material == null) {
            return 0;
        }
        return NUTRITION.getOrDefault(material, 0);
    }

    public static boolean known(Material material) {
        return material != null && NUTRITION.containsKey(material);
    }

    private static void put(Material material, int nutrition) {
        NUTRITION.put(material, nutrition);
    }

    static {
        put(Material.APPLE, 4);
        put(Material.BAKED_POTATO, 5);
        put(Material.BEEF, 3);
        put(Material.BEETROOT, 1);
        put(Material.BEETROOT_SOUP, 6);
        put(Material.BREAD, 5);
        put(Material.CARROT, 3);
        put(Material.CHICKEN, 2);
        put(Material.CHORUS_FRUIT, 4);
        put(Material.COD, 2);
        put(Material.COOKED_BEEF, 8);
        put(Material.COOKED_CHICKEN, 6);
        put(Material.COOKED_COD, 5);
        put(Material.COOKED_MUTTON, 6);
        put(Material.COOKED_PORKCHOP, 8);
        put(Material.COOKED_RABBIT, 5);
        put(Material.COOKED_SALMON, 6);
        put(Material.COOKIE, 2);
        put(Material.DRIED_KELP, 1);
        put(Material.ENCHANTED_GOLDEN_APPLE, 4);
        put(Material.GLOW_BERRIES, 2);
        put(Material.GOLDEN_APPLE, 4);
        put(Material.GOLDEN_CARROT, 6);
        put(Material.HONEY_BOTTLE, 6);
        put(Material.MELON_SLICE, 2);
        put(Material.MUSHROOM_STEW, 6);
        put(Material.MUTTON, 2);
        put(Material.POISONOUS_POTATO, 2);
        put(Material.PORKCHOP, 3);
        put(Material.POTATO, 1);
        put(Material.PUFFERFISH, 1);
        put(Material.PUMPKIN_PIE, 8);
        put(Material.RABBIT, 3);
        put(Material.RABBIT_STEW, 10);
        put(Material.ROTTEN_FLESH, 4);
        put(Material.SALMON, 2);
        put(Material.SPIDER_EYE, 2);
        put(Material.SUSPICIOUS_STEW, 6);
        put(Material.SWEET_BERRIES, 2);
        put(Material.TROPICAL_FISH, 1);
    }
}
