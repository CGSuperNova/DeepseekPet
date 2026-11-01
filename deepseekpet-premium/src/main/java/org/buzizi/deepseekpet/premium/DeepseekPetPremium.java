package org.buzizi.deepseekpet.premium;

import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.DeepseekPetCore;

/**
 * DeepseekPet 付费版主类。
 * 付费版默认包含免费版全部内容，并在其基础上注册 premium 专有功能。
 */
public final class DeepseekPetPremium extends JavaPlugin {

    private DeepseekPetCore core;

    @Override
    public void onEnable() {
        core = new DeepseekPetCore(this, true);
        core.enable();
        PremiumFeatureRegistry.register(core);
    }

    @Override
    public void onDisable() {
        PremiumFeatureRegistry.unregister(core);
        if (core != null) {
            core.disable();
        }
    }
}
