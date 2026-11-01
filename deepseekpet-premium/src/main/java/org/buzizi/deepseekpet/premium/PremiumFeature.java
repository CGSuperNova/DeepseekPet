package org.buzizi.deepseekpet.premium;

/**
 * Premium 专有功能的统一接口。
 * 后续新增付费版功能时，实现该接口并在 {@link PremiumFeatureRegistry} 中注册即可。
 */
public interface PremiumFeature {

    /** 功能名（用于日志）。 */
    String name();

    /** 插件启用时调用。 */
    void onEnable();

    /** 插件关闭时调用。 */
    void onDisable();
}
