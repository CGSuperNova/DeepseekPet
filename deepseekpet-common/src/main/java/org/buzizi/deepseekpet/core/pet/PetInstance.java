package org.buzizi.deepseekpet.core.pet;

import dev.lone.itemsadder.api.CustomEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.core.config.ConfigManager;
import org.buzizi.deepseekpet.util.Text;

/**
 * 一只属于某玩家的鲸鱼娘。
 *
 * <p>实体由 ItemsAdder 自定义实体（type: WOLF）承载，因此移动/跟随
 * 直接复用原版狼的 AI；外观由 ItemsAdder 的模型渲染。</p>
 */
public final class PetInstance {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Player owner;
    private CustomEntity customEntity;
    private LivingEntity entity;
    private TextDisplay floatingText;

    private PetInstance(JavaPlugin plugin, ConfigManager config, Player owner, CustomEntity customEntity, LivingEntity entity) {
        this.plugin = plugin;
        this.config = config;
        this.owner = owner;
        this.customEntity = customEntity;
        this.entity = entity;
    }

    /** 在玩家身边生成鲸鱼娘。失败返回 {@code null}。 */
    public static PetInstance spawn(JavaPlugin plugin, ConfigManager config, Player owner) {
        CustomEntity ce = CustomEntity.spawn(config.entityId(), owner.getLocation());
        if (ce == null) {
            plugin.getLogger().warning("无法生成鲸鱼娘实体 " + config.entityId()
                    + "，请确认 ItemsAdder 已加载该实体（contents/whalechan）");
            return null;
        }
        Entity bukkitEntity = ce.getEntity();
        if (!(bukkitEntity instanceof LivingEntity living)) {
            bukkitEntity.remove();
            return null;
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

        PetInstance pet = new PetInstance(plugin, config, owner, ce, living);
        pet.applyName();
        return pet;
    }

    /** 刷新头顶悬浮字（玩家 XXX 的鲸鱼娘）。 */
    public void applyName() {
        if (entity == null) {
            return;
        }
        String name = config.nameFormat().replace("%player%", owner.getName());
        entity.customName(Component.text(name));
        entity.setCustomNameVisible(config.showNameTag());
    }

    /** 在头顶显示一条临时的悬浮字（用于随机台词等），若干秒后消失。 */
    public void showFloatingText(String line, int seconds) {
        removeFloatingText();
        if (entity == null || !entity.isValid()) {
            return;
        }
        World world = entity.getWorld();
        var loc = entity.getLocation().add(0, entity.getHeight() + 0.45, 0);
        TextDisplay display = world.spawn(loc, TextDisplay.class, d -> {
            d.text(Text.plain(line));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
        });
        this.floatingText = display;
        Bukkit.getScheduler().runTaskLater(plugin, this::removeFloatingText, seconds * 20L);
    }

    public void removeFloatingText() {
        if (floatingText != null && floatingText.isValid()) {
            floatingText.remove();
        }
        floatingText = null;
    }

    /** 给主人播放客户端 UI 点击音效。 */
    public void playClickSound() {
        if (entity == null || !entity.isValid()) {
            return;
        }
        Player p = owner.getPlayer();
        if (p != null && p.isOnline()) {
            p.playSound(entity.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    /**
     * 播放 ItemsAdder 模型动画（如进食、拍肚子）。
     * 模型里没有该动画时会安全跳过。
     *
     * @param animation 动画名（与模型中定义的动画一致）
     * @param onFinish  播放结束后的回调，可为 null
     * @return 是否成功发起播放
     */
    public boolean playAnimation(String animation, Runnable onFinish) {
        if (customEntity == null || entity == null || !entity.isValid()) {
            return false;
        }
        if (animation == null || animation.isBlank()) {
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

    public boolean playAnimation(String animation) {
        return playAnimation(animation, null);
    }

    public boolean isValid() {
        return entity != null && entity.isValid() && customEntity != null;
    }

    public LivingEntity entity() {
        return entity;
    }

    public CustomEntity customEntity() {
        return customEntity;
    }

    public Player owner() {
        return owner;
    }

    public void despawn() {
        removeFloatingText();
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        customEntity = null;
        entity = null;
    }
}
