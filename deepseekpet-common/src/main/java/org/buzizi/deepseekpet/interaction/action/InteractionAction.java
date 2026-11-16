package org.buzizi.deepseekpet.interaction.action;

import org.buzizi.deepseekpet.status.Mood;

import java.util.Set;

/** 一个玩家互动行为（摸头 / 捏脸 / 抱抱 / 背起）的配置。 */
public record InteractionAction(
        String id,
        String name,
        boolean enabled,
        Set<Mood> allowedMoods,
        int moodDelta,
        int windowSeconds,
        int maxTimes,
        String overLimitEvent,
        String animation
) {
}
