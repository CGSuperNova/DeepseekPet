package org.buzizi.deepseekpet.interaction.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.avatar.PetInstance;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 可攻击：鲸鱼娘会被攻击判定命中，但伤害被取消（无敌、永不死亡）；
 * 被玩家攻击时按配置随机说一句反馈台词（悬浮字）。
 */
public final class PetDamageListener implements Listener {

    private final DeepseekPetCore core;
    /** 每个主人的鲸鱼娘最近一次反馈时间，用于冷却。 */
    private final Map<UUID, Long> lastReact = new ConcurrentHashMap<>();

    public PetDamageListener(DeepseekPetCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        PetInstance pet = core.pets().byEntity(event.getEntity());
        if (pet == null) {
            return;
        }
        // 无敌：取消一切伤害
        event.setCancelled(true);
        event.setDamage(0);

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        if (!(byEntity.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!core.config().attackEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, core.config().attackCooldownSeconds()) * 1000L;
        UUID key = pet.owner().getUniqueId();
        Long last = lastReact.get(key);
        if (last != null && now - last < cooldown) {
            return;
        }
        lastReact.put(key, now);

        List<String> lines = core.config().attackLines();
        if (!lines.isEmpty()) {
            String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
            pet.showFloatingText(line, Math.max(1, core.config().ambientFloatSeconds()));
        }
        core.log().event(attacker.getName(), "攻击鲸鱼娘", pet.owner().getName());
    }
}
