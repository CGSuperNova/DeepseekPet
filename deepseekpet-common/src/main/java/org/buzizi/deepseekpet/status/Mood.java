package org.buzizi.deepseekpet.status;

/** 五级心情。 */
public enum Mood {
    DEVASTATED("悲痛欲绝", 0, 20),
    UNHAPPY("不开心", 20, 40),
    NEUTRAL("一般", 40, 60),
    HAPPY("开心", 60, 80),
    ECSTATIC("兴高采烈", 80, 101);

    private final String display;
    private final int min;
    private final int max;

    Mood(String display, int min, int max) {
        this.display = display;
        this.min = min;
        this.max = max;
    }

    public String display() {
        return display;
    }

    /** 0-100 的心情值映射到五级心情。 */
    public static Mood fromValue(int value) {
        for (Mood mood : values()) {
            if (value >= mood.min && value < mood.max) {
                return mood;
            }
        }
        return value < 0 ? DEVASTATED : ECSTATIC;
    }

    public static Mood byName(String name) {
        if (name == null) {
            return null;
        }
        for (Mood mood : values()) {
            if (mood.name().equalsIgnoreCase(name.trim())) {
                return mood;
            }
        }
        return null;
    }
}
