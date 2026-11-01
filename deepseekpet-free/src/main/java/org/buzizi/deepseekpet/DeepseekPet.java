package org.buzizi.deepseekpet;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DeepseekPet 免费版主类。
 * 免费版包含：鲸鱼娘实体（跟随）、交互 GUI、对话（RAG + 云端 AI + 持久记忆）、
 * 定时/条件随机台词、收起/释放指令。
 */
public final class DeepseekPet extends JavaPlugin {

    private DeepseekPetCore core;

    @Override
    public void onEnable() {
        core = new DeepseekPetCore(this, false);
        core.enable();
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.disable();
        }
    }
}
