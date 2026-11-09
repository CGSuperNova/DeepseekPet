package org.buzizi.deepseekpet.core.pet;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.core.config.ConfigManager;

/**
 * Citizens 后端：以玩家模型的 NPC 作为鲸鱼娘实体（无 ItemsAdder 动画，导航跟随）。
 */
public final class CitizensBody implements PetBody {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Player owner;

    private NPC npc;

    public CitizensBody(JavaPlugin plugin, ConfigManager config, Player owner) {
        this.plugin = plugin;
        this.config = config;
        this.owner = owner;
    }

    @Override
    public boolean spawn() {
        String name = "whalechan-" + owner.getUniqueId().toString().substring(0, 8);
        NPC created = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        if (created == null) {
            return false;
        }
        created.spawn(owner.getLocation());
        if (!created.isSpawned()) {
            created.destroy();
            return false;
        }
        this.npc = created;
        return true;
    }

    @Override
    public void despawn() {
        if (npc != null) {
            try {
                if (npc.isSpawned()) {
                    npc.despawn();
                }
                npc.destroy();
            } catch (Throwable ignored) {
            }
        }
        npc = null;
    }

    @Override
    public boolean isValid() {
        return npc != null && npc.isSpawned();
    }

    @Override
    public LivingEntity entity() {
        if (npc == null || !npc.isSpawned()) {
            return null;
        }
        Entity e = npc.getEntity();
        return e instanceof LivingEntity living ? living : null;
    }

    @Override
    public void setCustomName(String name) {
        Entity e = entity();
        if (e != null) {
            e.customName(Component.text(name));
            e.setCustomNameVisible(true);
        }
    }

    @Override
    public boolean playAnimation(String animation, Runnable onFinish) {
        return false; // Citizens 玩家模型不支持 ItemsAdder 动画
    }

    @Override
    public void setFollow(boolean follow) {
        if (npc != null && npc.isSpawned()) {
            try {
                npc.getNavigator().cancelNavigation();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void followTick(Player owner) {
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        try {
            npc.getNavigator().setTarget(owner.getLocation());
        } catch (Throwable ignored) {
        }
    }
}
