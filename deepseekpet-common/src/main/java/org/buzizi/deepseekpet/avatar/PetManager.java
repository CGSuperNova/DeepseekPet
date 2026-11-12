package org.buzizi.deepseekpet.avatar;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.platform.config.ConfigManager;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有在线玩家与其鲸鱼娘实体之间的映射。
 */
public final class PetManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;

    /** player uuid -> pet */
    private final Map<UUID, PetInstance> pets = new ConcurrentHashMap<>();
    /** entity uuid -> pet（用于快速反查点击的是谁的鲸鱼娘） */
    private final Map<UUID, PetInstance> byEntity = new ConcurrentHashMap<>();

    public PetManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** 生成鲸鱼娘；已存在则返回现有实例。失败返回 null。 */
    public PetInstance spawnPet(Player player) {
        UUID id = player.getUniqueId();
        PetInstance existing = pets.get(id);
        if (existing != null && existing.isValid()) {
            return existing;
        }
        if (existing != null) {
            existing.despawn();
            unregister(existing);
        }

        PetInstance pet = PetInstance.spawn(plugin, config, player);
        if (pet == null) {
            return null;
        }
        pets.put(id, pet);
        byEntity.put(pet.entity().getUniqueId(), pet);
        return pet;
    }

    public void despawnPet(UUID playerId) {
        PetInstance pet = pets.remove(playerId);
        if (pet != null) {
            unregister(pet);
            pet.despawn();
        }
    }

    public void despawnAll() {
        Collection<PetInstance> all = pets.values();
        pets.clear();
        byEntity.clear();
        all.forEach(PetInstance::despawn);
    }

    /** 收起/释放切换。返回 true 表示当前为“显示”状态。 */
    public boolean toggle(Player player) {
        PetInstance pet = pets.get(player.getUniqueId());
        if (pet != null && pet.isValid()) {
            despawnPet(player.getUniqueId());
            return false;
        }
        return spawnPet(player) != null;
    }

    public PetInstance byPlayer(UUID playerId) {
        return pets.get(playerId);
    }

    public PetInstance byEntity(Entity entity) {
        return entity == null ? null : byEntity.get(entity.getUniqueId());
    }

    /** 清理失效（实体被移除/死亡）的条目。 */
    public void sweepInvalid() {
        for (PetInstance pet : pets.values()) {
            if (!pet.isValid()) {
                unregister(pet);
                pets.remove(pet.owner().getUniqueId(), pet);
            }
        }
    }

    /** 每个跟随周期调用：让每只鲸鱼娘执行跟随逻辑（主线程）。 */
    public void tickFollow() {
        for (PetInstance pet : pets.values()) {
            if (pet.isValid()) {
                pet.followTick();
            }
        }
    }

    private void unregister(PetInstance pet) {
        if (pet.entity() != null) {
            byEntity.remove(pet.entity().getUniqueId());
        }
    }

    public Collection<PetInstance> all() {
        return pets.values();
    }
}
