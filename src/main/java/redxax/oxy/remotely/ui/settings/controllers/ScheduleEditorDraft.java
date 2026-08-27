package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.schedule.ServerScheduleModels;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

record ScheduleEditorDraft(String name, boolean enabled, boolean onlyOnline, String timing, String preset,
                           String oneTime, String recurringTime, int weekDay, String monthDay, String cron,
                           String zone, List<ServerScheduleModels.Task> tasks) {
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    ScheduleEditorDraft {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    static ScheduleEditorDraft create(ServerScheduleModels.Schedule schedule, List<ServerScheduleModels.Task> tasks) {
        boolean recurring = schedule != null && schedule.timing().type() == ServerScheduleModels.TimingType.CRON;
        String zone = recurring && !schedule.timing().zoneId().isBlank() ? schedule.timing().zoneId() : ZoneId.systemDefault().getId();
        String cron = recurring ? schedule.timing().cron() : "0 4 * * *";
        String instant = schedule == null || recurring ? "" : local(schedule.timing().runAt(), zone);
        return new ScheduleEditorDraft(schedule == null ? "" : schedule.name(), schedule == null || schedule.enabled(),
                schedule != null && schedule.onlyWhenOnline(), recurring ? "Recurring" : "One Time", ScheduleTimingGuide.preset(cron),
                instant, ScheduleTimingGuide.time(cron), ScheduleTimingGuide.weekDay(cron), ScheduleTimingGuide.monthDay(cron), cron,
                zone, tasks);
    }

    static String instant(String local, String zone) {
        String value = local == null ? "" : local.trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(value, LOCAL).atZone(ZoneId.of(zone)).toInstant().toString();
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException("Use A Local Time Like 2026-08-27 18:00");
        }
    }

    static String local(String instant, String zone) {
        if (instant == null || instant.isBlank()) return "";
        return LOCAL.format(Instant.parse(instant).atZone(ZoneId.of(zone)));
    }

    static String display(String instant, String zone) {
        if (instant == null || instant.isBlank()) return "Not Scheduled";
        return local(instant, zone) + " " + zone;
    }
}
