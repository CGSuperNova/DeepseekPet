package org.buzizi.deepseekpet.interaction.feeding;

import org.bukkit.entity.Player;
import org.buzizi.deepseekpet.avatar.PetInstance;

/**
 * 喂食完成后的扩展点。免费版不注册任何 handler；
 * premium 通过注册它实现「吃下后的台词 / 命令 / 动作」。
 */
@FunctionalInterface
public interface FoodConsumeHandler {

    /**
     * @param player    喂食的玩家
     * @param pet       被喂食的鲸鱼娘
     * @param foodKey   食物 Material 名称
     * @param restored  本次实际恢复的饱食度
     * @param newHunger 喂食后的饱食度
     */
    void onConsume(Player player, PetInstance pet, String foodKey, int restored, int newHunger);
}
