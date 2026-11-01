package org.buzizi.deepseekpet.ambient;

import org.bukkit.configuration.ConfigurationSection;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一条「条件 + 固定文本」的随机台词规则。
 * 支持条件：time（时间点）、time-range（时间段）、date-range（日期段）、
 * weekdays（工作日/休息日/指定星期）、week-parity（单双周）、interval-seconds（每隔多久）。
 */
public final class AmbientRule {

    private final String id;
    private final boolean enabled;
    private final List<String> lines;
    private final long intervalSeconds;

    private final List<LocalTime> exactTimes = new ArrayList<>();
    private final List<TimeRange> timeRanges = new ArrayList<>();
    private final List<DateRange> dateRanges = new ArrayList<>();
    private Set<DayOfWeek> weekdays; // null = 不限
    private Integer weekParity;      // 0=even, 1=odd, null=不限

    private AmbientRule(String id, boolean enabled, List<String> lines, long intervalSeconds) {
        this.id = id;
        this.enabled = enabled;
        this.lines = lines;
        this.intervalSeconds = intervalSeconds;
    }

    public static AmbientRule from(String id, ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        List<String> lines = section.getStringList("lines");
        long interval = section.getLong("interval-seconds", 0L);

        AmbientRule rule = new AmbientRule(id, enabled, lines, interval);

        ConfigurationSection when = section.getConfigurationSection("when");
        if (when != null) {
            rule.parseTime(when.get("time"));
            rule.parseTimeRange(when.get("time-range"));
            rule.parseDateRange(when.get("date-range"));
            rule.parseWeekdays(when.get("weekdays"));
            rule.parseWeekParity(when.getString("week-parity"));
        }
        return rule;
    }

    // ------------------------------------------------------------------ 解析
    private void parseTime(Object raw) {
        for (String s : toStrings(raw)) {
            try {
                exactTimes.add(LocalTime.parse(s.trim()));
            } catch (Exception ignored) {
                // 忽略非法时间
            }
        }
    }

    private void parseTimeRange(Object raw) {
        List<List<String>> pairs = toPairs(raw);
        for (List<String> pair : pairs) {
            if (pair.size() < 2) {
                continue;
            }
            try {
                timeRanges.add(new TimeRange(LocalTime.parse(pair.get(0).trim()), LocalTime.parse(pair.get(1).trim())));
            } catch (Exception ignored) {
            }
        }
    }

    private void parseDateRange(Object raw) {
        List<List<String>> pairs = toPairs(raw);
        for (List<String> pair : pairs) {
            if (pair.size() < 2) {
                continue;
            }
            try {
                dateRanges.add(new DateRange(LocalDate.parse(pair.get(0).trim()), LocalDate.parse(pair.get(1).trim())));
            } catch (Exception ignored) {
            }
        }
    }

    private void parseWeekdays(Object raw) {
        if (raw == null) {
            return;
        }
        if (raw instanceof String s) {
            String lower = s.toLowerCase(Locale.ROOT);
            if ("workday".equals(lower)) {
                weekdays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
            } else if ("weekend".equals(lower)) {
                weekdays = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            }
            return;
        }
        Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        for (String s : toStrings(raw)) {
            try {
                set.add(DayOfWeek.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        if (!set.isEmpty()) {
            weekdays = set;
        }
    }

    private void parseWeekParity(String raw) {
        if (raw == null) {
            return;
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if ("odd".equals(lower)) {
            weekParity = 1;
        } else if ("even".equals(lower)) {
            weekParity = 0;
        }
    }

    // ------------------------------------------------------------------ 匹配
    /** 判断当前时间是否满足所有「条件」。toleranceSeconds 用于时间点的容差窗口。 */
    public boolean matches(LocalDateTime now, int toleranceSeconds) {
        LocalTime t = now.toLocalTime();
        LocalDate d = now.toLocalDate();

        if (!exactTimes.isEmpty()) {
            boolean hit = false;
            for (LocalTime et : exactTimes) {
                LocalTime upper = et.plusSeconds(toleranceSeconds);
                if (inWindow(t, et, upper)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }

        if (!timeRanges.isEmpty()) {
            boolean hit = false;
            for (TimeRange r : timeRanges) {
                if (r.contains(t)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }

        if (!dateRanges.isEmpty()) {
            boolean hit = false;
            for (DateRange r : dateRanges) {
                if (!d.isBefore(r.start) && !d.isAfter(r.end)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }

        if (weekdays != null && !weekdays.contains(now.getDayOfWeek())) {
            return false;
        }

        if (weekParity != null) {
            int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
            if (week % 2 != weekParity) {
                return false;
            }
        }
        return true;
    }

    private static boolean inWindow(LocalTime t, LocalTime start, LocalTime end) {
        if (!start.isAfter(end)) {
            return !t.isBefore(start) && !t.isAfter(end);
        }
        // 跨午夜
        return !t.isBefore(start) || !t.isAfter(end);
    }

    // ------------------------------------------------------------------ 取值
    public boolean hasLines() {
        return !lines.isEmpty();
    }

    public String randomLine() {
        if (lines.isEmpty()) {
            return "";
        }
        return lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public long intervalSeconds() {
        return intervalSeconds;
    }

    // ------------------------------------------------------------------ 工具
    private static List<String> toStrings(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String s) {
            return List.of(s);
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        return List.of(String.valueOf(raw));
    }

    /** 把「可能是 [a,b] 或 [[a,b],[c,d]]」的原始值规整成一组配对。 */
    private static List<List<String>> toPairs(Object raw) {
        List<List<String>> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof List<?> list) {
            boolean allStrings = list.stream().allMatch(o -> o instanceof String || o instanceof Number);
            if (allStrings && list.size() >= 2) {
                List<String> pair = new ArrayList<>();
                for (Object o : list) {
                    pair.add(String.valueOf(o));
                }
                out.add(pair);
                return out;
            }
            for (Object o : list) {
                if (o instanceof List<?> inner) {
                    List<String> pair = new ArrayList<>();
                    for (Object i : inner) {
                        pair.add(String.valueOf(i));
                    }
                    if (pair.size() >= 2) {
                        out.add(pair);
                    }
                }
            }
        }
        return out;
    }

    private record TimeRange(LocalTime start, LocalTime end) {
        boolean contains(LocalTime t) {
            if (!start.isAfter(end)) {
                return !t.isBefore(start) && !t.isAfter(end);
            }
            return !t.isBefore(start) || !t.isAfter(end);
        }
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
