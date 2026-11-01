package org.buzizi.deepseekpet.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 文本工具：把配置里的 {@code &} 颜色代码转成 Adventure 组件。
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** 将含 {@code &} 颜色代码的字符串转为组件。 */
    public static Component color(String text) {
        if (text == null) {
            return Component.empty();
        }
        return LEGACY.deserialize(text);
    }

    /** 将含 {@code &} 颜色代码 + {@code %s} 占位符的字符串转为组件。 */
    public static Component colorf(String format, Object... args) {
        return color(String.format(format, args));
    }

    /** 纯文本组件。 */
    public static Component plain(String text) {
        return Component.text(text == null ? "" : text);
    }
}
