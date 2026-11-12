package org.buzizi.deepseekpet.platform.extension;

import org.buzizi.deepseekpet.DeepseekPetCore;

/**
 * 板块扩展点（Extension Point）。
 *
 * <p>Free 版在这里定义接口，Premium（或未来的第三方扩展）通过
 * {@link BoardRegistry} 注册实现，从而在不改动 Free 代码的前提下扩展某个板块。
 * 每个扩展声明自己所属的板块（avatar / status / interaction / communication /
 * cognition / context / platform）。</p>
 */
public interface BoardExtension {

    /** 所属板块名，对应 7 大板块之一。 */
    String board();

    /** 扩展名（用于日志）。 */
    String name();

    /** 启用（插件已启用时调用）。 */
    void onEnable(DeepseekPetCore core);

    /** 关闭。 */
    void onDisable(DeepseekPetCore core);
}
