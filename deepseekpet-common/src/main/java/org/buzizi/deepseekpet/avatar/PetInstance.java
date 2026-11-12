package org.buzizi.deepseekpet.avatar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.platform.config.ConfigManager;
import org.buzizi.deepseekpet.platform.util.Text;

/**
 * 一只属于某玩家的鲸鱼娘。
 *
 * <p>实体后端按可用性选择：ItemsAdder 自定义实体（狼 AI + 动画）→
 * Citizens 玩家模型 NPC（导航跟随，无动画）→ 原版盔甲架（传送跟随，无动画）。
 * 悬浮字 / 点击音效 / 喂食 / 对话等通用逻辑都基于 {@link #entity()}，与后端无关。</p>
 */
public final class PetInstance {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Player owner;
    private final PetBody body;
    private TextDisplay floatingText;
    private boolean follow;

    private PetInstance(JavaPlugin plugin, ConfigManager config, Player owner, PetBody body) {
        this.plugin = plugin;
        this.config = config;
        this.owner = owner;
        this.body = body;
        this.follow = config.followDefaultEnabled();
        body.setFollow(this.follow);
    }

    /** 在玩家身边生成鲸鱼娘。失败返回 {@code null}。 */
    public static PetInstance spawn(JavaPlugin plugin, ConfigManager config, Player owner) {
        PetBody body = chooseBody(plugin, config, owner);
        if (!body.spawn()) {
            return null;
        }
        PetInstance pet = new PetInstance(plugin, config, owner, body);
        pet.applyName();
        return pet;
    }

    private static PetBody chooseBody(JavaPlugin plugin, ConfigManager config, Player owner) {
        var pm = Bukkit.getPluginManager();
        if (pm.isPluginEnabled("ItemsAdder")) {
            return new ItemsAdderBody(plugin, config, owner);
        }
        if (pm.isPluginEnabled("Citizens")) {
            return new CitizensBody(plugin, config, owner);
        }
        return new ArmorStandBody(plugin, config, owner);
    }

    /** 刷新头顶悬浮字（玩家 XXX 的鲸鱼娘）。 */
    public void applyName() {
        String name = config.nameFormat().replace("%player%", owner.getName());
        body.setCustomName(name);
    }

    /** 在头顶显示一条临时的悬浮字（用于随机台词等），若干秒后消失。 */
    public void showFloatingText(String line, int seconds) {
        removeFloatingText();
        LivingEntity entity = body.entity();
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
        LivingEntity entity = body.entity();
        if (entity == null || !entity.isValid()) {
            return;
        }
        Player p = owner.getPlayer();
        if (p != null && p.isOnline()) {
            p.playSound(entity.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    /** 播放模型动画（仅 ItemsAdder 后端支持）。 */
    public boolean playAnimation(String animation, Runnable onFinish) {
        return body.playAnimation(animation, onFinish);
    }

    public boolean playAnimation(String animation) {
        return body.playAnimation(animation, null);
    }

    // ------------------------------------------------------------ 跟随
    public boolean isFollowEnabled() {
        return follow;
    }

    public void setFollow(boolean follow) {
        this.follow = follow;
        body.setFollow(follow);
    }

    /** 每个跟随周期调用（主线程）。 */
    public void followTick() {
        if (follow) {
            body.followTick(owner);
        }
    }

    // ------------------------------------------------------------ 状态
    public boolean isValid() {
        return body != null && body.isValid();
    }

    public LivingEntity entity() {
        return body.entity();
    }

    public Player owner() {
        return owner;
    }

    public PetBody body() {
        return body;
    }

    public void despawn() {
        removeFloatingText();
        body.despawn();
    }
}
