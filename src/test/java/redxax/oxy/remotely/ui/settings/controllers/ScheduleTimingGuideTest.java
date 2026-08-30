package redxax.oxy.remotely.ui.settings.controllers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ScheduleTimingGuideTest {
    @Test
    void createsGuidedCronWithoutCronKnowledge() {
        assertEquals("30 4 * * *", ScheduleTimingGuide.cron(ScheduleTimingGuide.DAILY, "04:30", 1, "1", ""));
        assertEquals("15 9 * * 1-5", ScheduleTimingGuide.cron(ScheduleTimingGuide.WEEKDAYS, "09:15", 1, "1", ""));
        assertEquals("0 8 12 * *", ScheduleTimingGuide.cron(ScheduleTimingGuide.MONTHLY, "08:00", 1, "12", ""));
    }

    @Test
    void preservesAndValidatesAdvancedCron() {
        assertEquals(ScheduleTimingGuide.ADVANCED, ScheduleTimingGuide.preset("*/10 * * * *"));
        assertEquals("*/10 * * * *", ScheduleTimingGuide.cron(ScheduleTimingGuide.ADVANCED, "04:00", 1, "1", "*/10 * * * *"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleTimingGuide.cron(ScheduleTimingGuide.ADVANCED, "04:00", 1, "1", "bad"));
    }
}
