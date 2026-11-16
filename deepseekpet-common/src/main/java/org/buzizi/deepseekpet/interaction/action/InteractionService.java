package org.buzizi.deepseekpet.interaction.action;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.avatar.PetInstance;
import org.buzizi.deepseekpet.platform.util.Text;
import org.buzizi.deepseekpet.status.Mood;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interaction 板块：玩家互动行为（摸头 / 捏脸 / 抱抱 / 背起）。
 *
 * <p>每个行为支持：心情门槛、心情值影响、时间窗内次数限制、超限触发事件，
 * 以及（premium）模型动画。</p>
 */
public final class InteractionService {

    private final DeepseekPetCore core;
    private final Map<String, InteractionAction> actions = new LinkedHashMap<>();
    private final Map<String, Deque<Long>> history = new ConcurrentHashMap<>();

    public InteractionService(DeepseekPetCore core) {
        this.core = core;
        reload();
    }

    public void reload() {
        actions.clear();
        ConfigurationSection section = core.config().interactionsSection();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Set<Mood> moods = new HashSet<>();
            for (String name : s.getStringList("allowed-moods")) {
                Mood mood = Mood.byName(name);
                if (mood != null) {
                    moods.add(mood);
                }
            }
            actions.put(key, new InteractionAction(
                    key,
                    s.getString("name", key),
                    s.getBoolean("enabled", true),
                    moods,
                    s.getInt("mood-delta", 0),
                    s.getInt("window-seconds", 60),
                    s.getInt("max-times", 5),
                    s.getString("over-limit-event", ""),
                    s.getString("animation", "")
            ));
        }
    }

    public Collection<InteractionAction> actions() {
        return actions.values();
    }

    public InteractionAction action(String id) {
        return actions.get(id);
    }

    /** 执行互动；返回是否成功。 */
    public boolean execute(Player player, String id) {
        InteractionAction action = actions.get(id);
        if (action == null || !action.enabled()) {
            player.sendMessage(Text.color("&e这个互动暂时不可用"));
            return false;
        }
        PetInstance pet = core.pets().byPlayer(player.getUniqueId());
        if (pet == null || !pet.isValid() || pet.entity() == null) {
            player.sendMessage(Text.color("&e你还没有鲸鱼娘，输入 /dp show 召唤一只吧"));
            return false;
        }
        Mood mood = core.mood().mood(player.getUniqueId());
        if (!action.allowedMoods().isEmpty() && !action.allowedMoods().contains(mood)) {
            player.sendMessage(Text.color("&e鲸鱼娘现在的心情（" + mood.display() + "）不太想" + action.name()));
            return false;
        }
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + ":" + id;
        Deque<Long> times = history.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            long windowMs = Math.max(1, action.windowSeconds()) * 1000L;
            while (!times.isEmpty() && now - times.peekFirst() > windowMs) {
                times.pollFirst();
            }
            if (times.size() >= action.maxTimes()) {
                if (action.overLimitEvent() != null && !action.overLimitEvent().isBlank()) {
                    core.events().execute(action.overLimitEvent(), player, false);
                }
                player.sendMessage(Text.color("&e" + action.name() + "太频繁啦，让鲸鱼娘歇一会儿吧"));
                return false;
            }
            times.addLast(now);
        }
        applyEffect(player, pet, action);
        return true;
    }

    private void applyEffect(Player player, PetInstance pet, InteractionAction action) {
        if (action.moodDelta() != 0) {
            core.mood().adjust(player.getUniqueId(), action.moodDelta());
        }
        boolean carry = "carry".equals(action.id());
        if (carry && pet.entity() != null) {
            Entity entity = pet.entity();
            if (entity.getVehicle() != null) {
                entity.leaveVehicle();
                player.sendMessage(Text.color("&b鲸鱼娘下来啦"));
            } else {
                player.addPassenger(entity);
                player.sendMessage(Text.color("&b鲸鱼娘爬到你背上了～"));
            }
        }
        if (core.isPremium() && action.animation() != null && !action.animation().isBlank()) {
            pet.playAnimation(action.animation());
        }
        pet.playClickSound();
        if (!carry) {
            player.sendMessage(Text.color("&b你" + action.name() + "了鲸鱼娘"));
        }
        core.log().event(player.getName(), "互动", action.name());
    }
}
