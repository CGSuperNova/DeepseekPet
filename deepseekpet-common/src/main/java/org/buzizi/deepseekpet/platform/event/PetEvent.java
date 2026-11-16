package org.buzizi.deepseekpet.platform.event;

import org.bukkit.entity.Player;

/**
 * 鲸鱼娘的一个「非玩家主动型事件」。
 * 每个事件有唯一 id，可由 {@code /dp execute} 直接触发。
 */
public interface PetEvent {

    String id();

    String description();

    /** 执行事件。 */
    void execute(Player player);

    /** 执行期间占用冲突锁的时长（tick）；0 表示瞬时事件。 */
    default int durationTicks() {
        return 0;
    }
}
