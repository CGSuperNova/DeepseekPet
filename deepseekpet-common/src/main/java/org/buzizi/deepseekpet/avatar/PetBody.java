package org.buzizi.deepseekpet.avatar;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 鲸鱼娘「实体身体」的抽象。
 *
 * <p>不同后端对应不同实现：
 * <ul>
 *   <li>{@link ItemsAdderBody}：ItemsAdder 自定义实体（原版狼 AI，含动画）；</li>
 *   <li>{@link CitizensBody}：Citizens 玩家模型 NPC（无动画，导航跟随）；</li>
 *   <li>{@link ArmorStandBody}：原版盔甲架（无动画，传送跟随）。</li>
 * </ul>
 * 这样 PetInstance 只依赖本接口，不直接依赖 ItemsAdder / Citizens 的类。</p>
 */
public interface PetBody {

    /** 生成实体，失败返回 false。 */
    boolean spawn();

    void despawn();

    boolean isValid();

    /** 底层实体（用于右键点击识别等）。 */
    LivingEntity entity();

    /** 设置头顶名字标签。 */
    void setCustomName(String name);

    /** 播放模型动画；不支持动画的后端返回 false。 */
    boolean playAnimation(String animation, Runnable onFinish);

    /** 开启 / 关闭跟随。 */
    void setFollow(boolean follow);

    /** 每个跟随周期调用一次（主线程）。 */
    void followTick(Player owner);
}
