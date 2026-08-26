package redxax.oxy.remotely.ui.settings.controllers;

import java.util.List;

final class ScheduleTimingGuide {
    static final String HOURLY = "Every Hour";
    static final String DAILY = "Every Day";
    static final String WEEKDAYS = "Weekdays";
    static final String WEEKLY = "Every Week";
    static final String MONTHLY = "Every Month";
    static final String ADVANCED = "Advanced Cron";
    static final List<String> PRESETS = List.of(HOURLY, DAILY, WEEKDAYS, WEEKLY, MONTHLY, ADVANCED);
    static final List<String> WEEKDAYS_LIST = List.of("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");

    private ScheduleTimingGuide() {
    }

    static String preset(String cron) {
        String value = normalize(cron);
        String[] fields = value.split(" ");
        if (fields.length != 5) return ADVANCED;
        if ("0".equals(fields[0]) && "*".equals(fields[1]) && "*".equals(fields[2]) && "*".equals(fields[3]) && "*".equals(fields[4])) return HOURLY;
        if (number(fields[0], 0, 59) && number(fields[1], 0, 23) && "*".equals(fields[2]) && "*".equals(fields[3])) {
            if ("*".equals(fields[4])) return DAILY;
            if ("1-5".equals(fields[4])) return WEEKDAYS;
            if (number(fields[4], 0, 6)) return WEEKLY;
        }
        if (number(fields[0], 0, 59) && number(fields[1], 0, 23) && number(fields[2], 1, 31) && "*".equals(fields[3]) && "*".equals(fields[4])) return MONTHLY;
        return ADVANCED;
    }

    static String time(String cron) {
        String[] fields = normalize(cron).split(" ");
        return fields.length == 5 && number(fields[0], 0, 59) && number(fields[1], 0, 23)
                ? two(Integer.parseInt(fields[1])) + ":" + two(Integer.parseInt(fields[0])) : "04:00";
    }

    static int weekDay(String cron) {
        String[] fields = normalize(cron).split(" ");
        return fields.length == 5 && number(fields[4], 0, 6) ? Integer.parseInt(fields[4]) : 1;
    }

    static String monthDay(String cron) {
        String[] fields = normalize(cron).split(" ");
        return fields.length == 5 && number(fields[2], 1, 31) ? fields[2] : "1";
    }

    static String cron(String preset, String time, int weekDay, String monthDay, String advanced) {
        if (ADVANCED.equals(preset)) return validateAdvanced(advanced);
        int[] clock = clock(time);
        String minute = String.valueOf(clock[1]);
        String hour = String.valueOf(clock[0]);
        return switch (preset) {
            case HOURLY -> "0 * * * *";
            case DAILY -> minute + " " + hour + " * * *";
            case WEEKDAYS -> minute + " " + hour + " * * 1-5";
            case WEEKLY -> minute + " " + hour + " * * " + Math.clamp(weekDay, 0, 6);
            case MONTHLY -> minute + " " + hour + " " + integer(monthDay, 1, 31, "Day Of Month") + " * *";
            default -> throw new IllegalArgumentException("Choose A Recurring Schedule");
        };
    }

    static String summary(String preset, String time, int weekDay, String monthDay, String advanced, String zone) {
        String location = zone == null || zone.isBlank() ? "UTC" : zone.trim();
        return switch (preset) {
            case HOURLY -> "Runs At The Start Of Every Hour In " + location;
            case DAILY -> "Runs Every Day At " + normalizedTime(time) + " In " + location;
            case WEEKDAYS -> "Runs Monday Through Friday At " + normalizedTime(time) + " In " + location;
            case WEEKLY -> "Runs Every " + WEEKDAYS_LIST.get(Math.clamp(weekDay, 0, 6)) + " At " + normalizedTime(time) + " In " + location;
            case MONTHLY -> "Runs On Day " + integer(monthDay, 1, 31, "Day Of Month") + " At " + normalizedTime(time) + " In " + location;
            case ADVANCED -> "Advanced Cron: " + validateAdvanced(advanced) + " In " + location;
            default -> "Choose A Recurring Schedule";
        };
    }

    private static String validateAdvanced(String cron) {
        String value = normalize(cron);
        String[] fields = value.split(" ");
        if (fields.length != 5) throw new IllegalArgumentException("Advanced Cron Must Have Five Fields");
        for (String field : fields) if (!field.matches("[0-9*/,\\-]+")) throw new IllegalArgumentException("Advanced Cron Contains An Invalid Field");
        if (!"*".equals(fields[2]) && !"*".equals(fields[4])) throw new IllegalArgumentException("Advanced Cron Cannot Restrict Both Day Fields");
        return value;
    }

    private static int[] clock(String time) {
        String[] fields = time == null ? new String[0] : time.trim().split(":");
        if (fields.length != 2) throw new IllegalArgumentException("Time Must Use HH:MM");
        return new int[]{integer(fields[0], 0, 23, "Hour"), integer(fields[1], 0, 59, "Minute")};
    }

    private static String normalizedTime(String time) {
        int[] value = clock(time);
        return two(value[0]) + ":" + two(value[1]);
    }

    private static int integer(String value, int minimum, int maximum, String name) {
        try {
            int result = Integer.parseInt(value == null ? "" : value.trim());
            if (result < minimum || result > maximum) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(name + " Must Be Between " + minimum + " And " + maximum);
        }
    }

    private static boolean number(String value, int minimum, int maximum) {
        try {
            int result = Integer.parseInt(value);
            return result >= minimum && result <= maximum;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
