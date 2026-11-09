package org.buzizi.deepseekpet.core.pet;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.core.config.ConfigManager;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 盔甲架后端：最终降级方案。穿上蓝色皮革衣服 + 玩家头颅当鲸鱼娘，
 * 舍弃动画与狼 AI，移动为「超出距离传送跟随」（尽量避开方块）。
 */
public final class ArmorStandBody implements PetBody {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Player owner;

    private ArmorStand stand;

    public ArmorStandBody(JavaPlugin plugin, ConfigManager config, Player owner) {
        this.plugin = plugin;
        this.config = config;
        this.owner = owner;
    }

    @Override
    public boolean spawn() {
        Location loc = owner.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        this.stand = world.spawn(loc, ArmorStand.class, as -> {
            as.setVisible(true);
            as.setSmall(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setBasePlate(false);
            as.setArms(true);
            as.setHelmet(new ItemStack(Material.PLAYER_HEAD));
            as.setChestplate(leather(Material.LEATHER_CHESTPLATE));
            as.setLeggings(leather(Material.LEATHER_LEGGINGS));
            as.setBoots(leather(Material.LEATHER_BOOTS));
        });
        return stand != null;
    }

    private ItemStack leather(Material material) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(Color.fromRGB(59, 130, 246));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void despawn() {
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
        stand = null;
    }

    @Override
    public boolean isValid() {
        return stand != null && stand.isValid();
    }

    @Override
    public LivingEntity entity() {
        return stand;
    }

    @Override
    public void setCustomName(String name) {
        if (stand != null) {
            stand.customName(Component.text(name));
            stand.setCustomNameVisible(true);
        }
    }

    @Override
    public boolean playAnimation(String animation, Runnable onFinish) {
        return false;
    }

    @Override
    public void setFollow(boolean follow) {
        // 传送跟随由 followTick 控制；关闭时 followTick 不会被调用
    }

    @Override
    public void followTick(Player owner) {
        if (stand == null || !stand.isValid()) {
            return;
        }
        Location ownerLoc = owner.getLocation();
        if (!stand.getWorld().equals(ownerLoc.getWorld())) {
            stand.teleport(findSafe(ownerLoc));
            return;
        }
        double distance = config.armorStandFollowDistance();
        if (stand.getLocation().distanceSquared(ownerLoc) > distance * distance) {
            stand.teleport(findSafe(ownerLoc));
        }
    }

    /** 在玩家附近找一个「脚下有方块、身体两格空气」的安全位置，避免卡墙。 */
    private Location findSafe(Location around) {
        World world = around.getWorld();
        if (world == null) {
            return around;
        }
        double radius = config.armorStandTpDistance();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 24; i++) {
            double x = around.getX() + (rnd.nextDouble() * 2 - 1) * radius;
            double z = around.getZ() + (rnd.nextDouble() * 2 - 1) * radius;
            for (int dy = 2; dy >= -2; dy--) {
                Location candidate = new Location(world, x, around.getY() + dy, z);
                if (isSafe(candidate)) {
                    return candidate;
                }
            }
        }
        return around;
    }

    private boolean isSafe(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        boolean feet = world.getBlockAt(loc).isPassable();
        boolean head = world.getBlockAt(loc.clone().add(0, 1, 0)).isPassable();
        boolean ground = !world.getBlockAt(loc.clone().add(0, -1, 0)).isPassable();
        return feet && head && ground;
    }
}
