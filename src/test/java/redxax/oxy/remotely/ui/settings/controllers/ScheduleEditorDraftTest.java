package redxax.oxy.remotely.ui.settings.controllers;

import org.junit.jupiter.api.Test;
import restudio.rebase.schedule.ServerScheduleModels;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleEditorDraftTest {
    @Test
    void convertsReadableLocalTimeOnlyAtTheWireBoundary() {
        assertEquals("2026-08-27T15:00:00Z", ScheduleEditorDraft.instant("2026-08-27 18:00", "Asia/Riyadh"));
        assertEquals("2026-08-27 18:00", ScheduleEditorDraft.local("2026-08-27T15:00:00Z", "Asia/Riyadh"));
        assertEquals("2026-08-27T15:00:00Z", ScheduleEditorDraft.instant("2026-08-27T18:00", "Asia/Riyadh"));
    }

    @Test
    void completeDraftRetainsEveryUnsavedFieldWithTaskChanges() {
        var task = new ServerScheduleModels.Task("task", 1, ServerScheduleModels.Action.COMMAND, "say hello", 7, true);
        var draft = new ScheduleEditorDraft("Nightly", false, true, "Recurring", ScheduleTimingGuide.WEEKLY,
                "2026-08-27 18:00", "23:45", 4, "19", "45 23 * * 4", "Asia/Riyadh", List.of(task));
        assertEquals("Nightly", draft.name());
        assertEquals("2026-08-27 18:00", draft.oneTime());
        assertEquals("23:45", draft.recurringTime());
        assertEquals("45 23 * * 4", draft.cron());
        assertEquals("say hello", draft.tasks().getFirst().payload());
    }
}
