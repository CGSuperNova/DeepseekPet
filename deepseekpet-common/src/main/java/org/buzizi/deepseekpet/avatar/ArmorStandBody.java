package org.buzizi.deepseekpet.avatar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.buzizi.deepseekpet.platform.config.ConfigManager;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
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
            as.setSmall(true);          // 小型盔甲架（约一格高，类似小僵尸）
            as.setGravity(false);
            as.setPersistent(true);
            as.setBasePlate(false);
            as.setArms(true);
            as.setHelmet(headItem());
            as.setChestplate(leather(Material.LEATHER_CHESTPLATE));
            as.setLeggings(leather(Material.LEATHER_LEGGINGS));
            as.setBoots(leather(Material.LEATHER_BOOTS));
        });
        return stand != null;
    }

    /** 头颅：默认玩家头颅；配置了 base64 皮肤则使用自定义头颅模型。 */
    private ItemStack headItem() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        String base64 = config.armorStandHeadTexture();
        if (base64 == null || base64.isBlank()) {
            return head;
        }
        try {
            String url = extractSkinUrl(base64.trim());
            if (url == null || url.isBlank()) {
                return head;
            }
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) {
                return head;
            }
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "whalechan");
            profile.getTextures().setSkin(new URL(url));
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
        } catch (Throwable ignored) {
            // 配置格式错误时回退到默认头颅，避免影响生成
        }
        return head;
    }

    /** 从头颅 base64（textures 属性值）中取出皮肤 URL。 */
    private String extractSkinUrl(String base64) {
        String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("textures")) {
            return null;
        }
        JsonObject textures = root.getAsJsonObject("textures");
        if (!textures.has("SKIN")) {
            return null;
        }
        JsonObject skin = textures.getAsJsonObject("SKIN");
        return skin.has("url") ? skin.get("url").getAsString() : null;
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

    /** 在玩家附近找一个「脚下有方块、脚部空气」的安全位置，避免卡墙（小型盔甲架仅占一格）。 */
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
        // 小型盔甲架只占脚下这一格，只需要该格可通行且下方有实体方块
        boolean feet = world.getBlockAt(loc).isPassable();
        boolean ground = !world.getBlockAt(loc.clone().add(0, -1, 0)).isPassable();
        return feet && ground;
    }
}
