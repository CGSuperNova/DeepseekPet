package org.buzizi.deepseekpet.context;

import org.bukkit.entity.Player;

/**
 * 玩家信息读取器：为对话提供「主人当前信息」的一段文本。
 * free 有 Vault / 背包；premium 额外有 PlaceholderAPI 相关。
 */
public interface PlayerContextProvider {

    /** 唯一 id（用于日志 / 排查）。 */
    String id();

    /** 是否启用（由配置或插件可用性决定）。 */
    boolean enabled();

    /** 提供一段信息；无信息时返回 null / 空。 */
    String provide(Player player);
}
