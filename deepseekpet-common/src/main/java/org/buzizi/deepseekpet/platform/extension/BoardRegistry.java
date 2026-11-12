package org.buzizi.deepseekpet.platform.extension;

import org.buzizi.deepseekpet.DeepseekPetCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 板块扩展注册表：按板块聚合 {@link BoardExtension}，并负责启停。
 *
 * <p>Free 版注册内置实现；Premium 在自己的 FeatureRegistry 里把功能注册进来，
 * 因此 Free / Premium 共用同一套骨架。</p>
 */
public final class BoardRegistry {

    private final Map<String, List<BoardExtension>> byBoard = new LinkedHashMap<>();

    /** 注册并立即启用。 */
    public void register(BoardExtension extension, DeepseekPetCore core) {
        if (extension == null) {
            return;
        }
        byBoard.computeIfAbsent(extension.board(), key -> new ArrayList<>()).add(extension);
        try {
            extension.onEnable(core);
            if (core.log() != null) {
                core.log().info("[board:" + extension.board() + "] 已启用扩展：" + extension.name());
            }
        } catch (Throwable t) {
            if (core.log() != null) {
                core.log().error("[board:" + extension.board() + "] 扩展启用失败：" + extension.name(), t);
            }
        }
    }

    /** 关闭全部扩展。 */
    public void disableAll(DeepseekPetCore core) {
        for (List<BoardExtension> list : byBoard.values()) {
            for (BoardExtension extension : list) {
                try {
                    extension.onDisable(core);
                } catch (Throwable ignored) {
                }
            }
        }
        byBoard.clear();
    }

    public List<BoardExtension> of(String board) {
        return List.copyOf(byBoard.getOrDefault(board, List.of()));
    }

    public Set<String> boards() {
        return Collections.unmodifiableSet(byBoard.keySet());
    }

    public List<BoardExtension> all() {
        List<BoardExtension> out = new ArrayList<>();
        byBoard.values().forEach(out::addAll);
        return out;
    }
}
