package org.buzizi.deepseekpet.feeding;

import java.util.List;

/**
 * 单个食物的配置。
 *
 * @param nutrition      覆盖的饱食度恢复值（null = 使用原版值）
 * @param message        （premium）吃下后的台词
 * @param commands       （premium）吃下后执行的自定义命令
 * @param animation      （premium）进食动作名
 * @param afterAnimation （premium）进食后动作名
 */
public record FoodEntry(
        Integer nutrition,
        String message,
        List<String> commands,
        String animation,
        String afterAnimation
) {

    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

    public boolean hasCommands() {
        return commands != null && !commands.isEmpty();
    }

    public boolean hasAnimation() {
        return animation != null && !animation.isBlank();
    }

    public boolean hasAfterAnimation() {
        return afterAnimation != null && !afterAnimation.isBlank();
    }
}
