package org.buzizi.deepseekpet.core.pet;

import dev.lone.itemsadder.api.CustomEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.core.config.ConfigManager;

/**
 * ItemsAdder 后端：自定义实体（type: WOLF），复用原版狼 AI 与模型动画。
 */
public final class ItemsAdderBody implements PetBody {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Player owner;

    private CustomEntity customEntity;
    private LivingEntity entity;

    public ItemsAdderBody(JavaPlugin plugin, ConfigManager config, Player owner) {
        this.plugin = plugin;
        this.config = config;
        this.owner = owner;
    }

    @Override
    public boolean spawn() {
        CustomEntity ce = CustomEntity.spawn(config.entityId(), owner.getLocation());
        if (ce == null) {
            plugin.getLogger().warning("无法生成鲸鱼娘实体 " + config.entityId());
            return false;
        }
        Entity bukkitEntity = ce.getEntity();
        if (!(bukkitEntity instanceof LivingEntity living)) {
            bukkitEntity.remove();
            return false;
        }
        // 复用原版狼 AI：驯服并绑定主人，狼会跟随 / 传送
        if (living instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(owner);
        }
        if (living instanceof Sittable sittable) {
            sittable.setSitting(false);
        }
        living.setPersistent(true);
        living.setRemoveWhenFarAway(false);
        living.setSilent(true);
        living.setInvulnerable(true);
        this.customEntity = ce;
        this.entity = living;
        return true;
    }

    @Override
    public void despawn() {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        customEntity = null;
        entity = null;
    }

    @Override
    public boolean isValid() {
        return entity != null && entity.isValid() && customEntity != null;
    }

    @Override
    public LivingEntity entity() {
        return entity;
    }

    @Override
    public void setCustomName(String name) {
        if (entity != null) {
            entity.customName(Component.text(name));
            entity.setCustomNameVisible(true);
        }
    }

    @Override
    public boolean playAnimation(String animation, Runnable onFinish) {
        if (customEntity == null || entity == null || !entity.isValid() || animation == null || animation.isBlank()) {
            return false;
        }
        Runnable callback = onFinish == null ? () -> {
        } : onFinish;
        try {
            return customEntity.playAnimation(animation, callback);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void setFollow(boolean follow) {
        if (entity instanceof Sittable sittable) {
            sittable.setSitting(!follow);
        }
    }

    @Override
    public void followTick(Player owner) {
        // 原版狼 AI 自动跟随，无需额外处理
    }
}
