package org.buzizi.deepseekpet.premium;

import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.premium.features.LongTermMemoryFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium 功能注册表：在这里登记所有付费版专有功能。
 *
 * <p>注意：本类被 ProGuard 保留（见 proguard.pro），
 * 而 features 包下的具体实现会被混淆，用于保护付费逻辑。</p>
 */
public final class PremiumFeatureRegistry {

    private static final List<PremiumFeature> FEATURES = new ArrayList<>();

    private PremiumFeatureRegistry() {
    }

    public static void register(DeepseekPetCore core) {
        // 在这里登记 premium 专有功能
        FEATURES.add(new LongTermMemoryFeature(core));

        for (PremiumFeature feature : FEATURES) {
            try {
                feature.onEnable();
                core.plugin().getLogger().info("[premium] 已启用功能：" + feature.name());
            } catch (Exception e) {
                core.plugin().getLogger().severe("[premium] 功能启用失败：" + feature.name() + " -> " + e.getMessage());
            }
        }
    }

    public static void unregister(DeepseekPetCore core) {
        for (PremiumFeature feature : FEATURES) {
            try {
                feature.onDisable();
            } catch (Exception ignored) {
            }
        }
        FEATURES.clear();
    }
}
