package org.buzizi.deepseekpet.interaction.feeding;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.platform.config.ConfigManager;
import org.buzizi.deepseekpet.avatar.PetInstance;
import org.buzizi.deepseekpet.platform.util.Text;
import org.buzizi.deepseekpet.status.HungerStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 喂食系统：饥饿值增减、双击确认喂食、饥饿提示与拒绝对话。
 *
 * <p>饥饿值减少来源：
 * <ol>
 *   <li>自然减少（每 interval-seconds 秒 -amount）；</li>
 *   <li>玩家主动对话（每次 -dialog-cost）。鲸鱼娘自主台词不算。</li>
 * </ol>
 * 玩家手持可食用物品右键鲸鱼娘：第一次提示确认，第二次（窗口内）才真正喂食。</p>
 */
public final class FeedingManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final DeepseekPetCore core;
    private final HungerStore store;
    private final List<FoodConsumeHandler> handlers = new CopyOnWriteArrayList<>();
    private final Map<UUID, PendingFeed> pending = new ConcurrentHashMap<>();

    private volatile FeedingConfig settings;
    private int taskId = -1;
    private long lastNatural;
    private long lastHungry;
    private long lastSave;

    public FeedingManager(JavaPlugin plugin, ConfigManager config, DeepseekPetCore core) {
        this.plugin = plugin;
        this.config = config;
        this.core = core;
        this.store = new HungerStore(plugin);
        this.settings = new FeedingConfig(config.feeding(), config.feedingFoods());
    }

    public void enable() {
        if (!settings.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        lastNatural = now;
        lastHungry = now;
        lastSave = now;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    public void reload() {
        this.settings = new FeedingConfig(config.feeding(), config.feedingFoods());
        if (!settings.enabled()) {
            if (taskId != -1) {
                Bukkit.getScheduler().cancelTask(taskId);
                taskId = -1;
            }
        } else if (taskId == -1) {
            enable();
        }
    }

    public void disable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        store.flush();
    }

    // ------------------------------------------------------------ 扩展点
    public void registerHandler(FoodConsumeHandler handler) {
        if (handler != null) {
            handlers.add(handler);
        }
    }

    public void unregisterHandler(FoodConsumeHandler handler) {
        handlers.remove(handler);
    }

    // ------------------------------------------------------------ 查询
    public FeedingConfig settings() {
        return settings;
    }

    public int maxHunger() {
        return settings.maxHunger();
    }

    public int hunger(UUID playerId) {
        return store.get(playerId, settings.initialHunger());
    }

    public boolean canTalk(UUID playerId) {
        return !settings.enabled() || hunger(playerId) > 0;
    }

    public String refuseLine() {
        return settings.refuseLine();
    }

    public boolean isFood(ItemStack stack) {
        return settings.enabled() && stack != null && settings.isFood(stack.getType());
    }

    /** 玩家主动对话时的饥饿消耗。 */
    public void onDialog(Player player) {
        if (!settings.enabled()) {
            return;
        }
        int cost = settings.dialogCost();
        if (cost > 0) {
            adjust(player.getUniqueId(), -cost);
        }
    }

    // ------------------------------------------------------------ 喂食
    public void handleFeedRequest(Player player, PetInstance pet, ItemStack item, boolean offHand) {
        if (!settings.enabled() || item == null || pet == null || pet.entity() == null) {
            return;
        }
        Material material = item.getType();
        if (!settings.isFood(material)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = (long) (settings.confirmWindowSeconds() * 1000);

        PendingFeed previous = pending.get(playerId);
        if (previous != null
                && previous.entityId().equals(pet.entity().getUniqueId())
                && now - previous.timeMs() <= windowMs) {
            pending.remove(playerId);
            feed(player, pet, material, offHand);
        } else {
            pending.put(playerId, new PendingFeed(pet.entity().getUniqueId(), now));
            player.sendMessage(Text.color(settings.confirmLine().replace("%food%", material.name())));
        }
    }

    private void feed(Player player, PetInstance pet, Material material, boolean offHand) {
        int max = settings.maxHunger();
        int current = hunger(player.getUniqueId());
        int nutrition = Math.max(0, settings.nutrition(material));

        if (current >= max && !settings.consumeWhenFull()) {
            player.sendMessage(Text.color(settings.fullFeedLine()));
            return;
        }
        if (nutrition <= 0) {
            return;
        }

        int next = Math.min(max, current + nutrition);
        int restored = next - current;

        consumeOne(player, offHand);
        if (restored > 0) {
            store.set(player.getUniqueId(), next);
        }

        String message = settings.successLine()
                .replace("%food%", material.name())
                .replace("%amount%", String.valueOf(restored))
                .replace("%hunger%", String.valueOf(next))
                .replace("%max%", String.valueOf(max));
        player.sendMessage(Text.color(message));

        for (FoodConsumeHandler handler : handlers) {
            try {
                handler.onConsume(player, pet, material.name(), restored, next);
            } catch (Throwable ignored) {
                // premium 扩展失败不影响基础喂食
            }
        }
    }

    private void consumeOne(Player player, boolean offHand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        var inventory = player.getInventory();
        ItemStack held = offHand ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        ItemStack copy = held.clone();
        int amount = held.getAmount();
        copy.setAmount(amount <= 1 ? 0 : amount - 1);
        if (offHand) {
            inventory.setItemInOffHand(copy);
        } else {
            inventory.setItemInMainHand(copy);
        }
    }

    // ------------------------------------------------------------ 周期任务
    private void tick() {
        if (!settings.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();

        long naturalMs = settings.naturalIntervalSeconds() * 1000L;
        if (now - lastNatural >= naturalMs) {
            lastNatural = now;
            int amount = settings.naturalAmount();
            if (amount > 0) {
                for (PetInstance pet : core.pets().all()) {
                    if (pet.isValid()) {
                        adjust(pet.owner().getUniqueId(), -amount);
                    }
                }
            }
        }

        long hungryMs = settings.hungryIntervalSeconds() * 1000L;
        if (now - lastHungry >= hungryMs) {
            lastHungry = now;
            showHungryLines();
        }

        if (now - lastSave >= 5000L) {
            lastSave = now;
            store.flushIfDirty();
        }

        long windowMs = (long) (settings.confirmWindowSeconds() * 1000);
        pending.entrySet().removeIf(entry -> now - entry.getValue().timeMs() > windowMs);
    }

    private void showHungryLines() {
        List<String> lines = settings.hungryLines();
        if (lines.isEmpty()) {
            return;
        }
        int threshold = settings.hungryThreshold();
        int floatSeconds = Math.max(1, config.ambientFloatSeconds());
        for (PetInstance pet : core.pets().all()) {
            if (!pet.isValid()) {
                continue;
            }
            if (hunger(pet.owner().getUniqueId()) >= threshold) {
                continue;
            }
            String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
            pet.showFloatingText(line, floatSeconds);
            pet.playClickSound();
        }
    }

    /** 调整饥饿值（自动夹在 [0, max]），只在真正变化时写存储。 */
    public int adjust(UUID playerId, int delta) {
        int max = settings.maxHunger();
        int current = hunger(playerId);
        int next = Math.max(0, Math.min(max, current + delta));
        if (next != current) {
            store.set(playerId, next);
        }
        return next;
    }

    private record PendingFeed(UUID entityId, long timeMs) {
    }
}
