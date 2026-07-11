package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.data.flow.player.PlayerEventRecord;
import redxax.oxy.remotely.data.flow.player.PlayerSessionRecord;
import redxax.oxy.remotely.data.managed.PlayerSession;
import redxax.oxy.remotely.data.managed.SessionEvent;
import redxax.oxy.remotely.data.player.management.PlayerSection;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.util.TimeUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PlayerTimelineSections {
    private final PlayerManagementScreen screen;

    PlayerTimelineSections(PlayerManagementScreen screen) {
        this.screen = screen;
    }

    void renderActivity() {
        float scroll = screen.activityContainer.getScrollOffset();
        screen.activityContainer.clearWidgets();
        screen.activityContainer.columns(1);
        screen.activityContainer.addWidget(screen.createLabel("Activity"));
        if (screen.addSectionFeedback(screen.activityContainer, PlayerSection.ACTIVITY) && screen.dossier == null && (screen.managementSnapshot == null || screen.managementSnapshot.localSessions().isEmpty())) {
            finishActivity(scroll);
            return;
        }
        List<PlayerEventRecord> events = screen.getFilteredEvents(null);
        Set<String> renderedEvents = new LinkedHashSet<>();
        for (PlayerEventRecord event : events) {
            if (event == null) continue;
            renderedEvents.add(event.getTimestamp() + ":" + screen.compact(event.getType()).toLowerCase(Locale.ROOT));
            MountableButtonWidget row = new MountableButtonWidget.Builder(screen.formatToken(event.getCategory()) + " / " + screen.formatToken(event.getType())).hiddenText(screen.compact(screen.formatModule(event.getModuleId())) + " | " + TimeUtils.timeSense(event.getTimestamp())).description(screen.buildEventDescription(event)).build();
            row.setHeight(34);
            row.entranceAnimationEnabled = false;
            screen.activityContainer.addWidget(row);
        }
        if (screen.managementSnapshot != null) {
            for (PlayerSession session : screen.managementSnapshot.localSessions()) {
                if (session == null || session.events == null) continue;
                for (SessionEvent event : session.events) {
                    if (event == null || event.type == null || !screen.matchesLocalEvent(event)) continue;
                    String stableId = event.timestamp + ":" + event.type.name().toLowerCase(Locale.ROOT);
                    if (!renderedEvents.add(stableId)) continue;
                    MountableButtonWidget row = new MountableButtonWidget.Builder(screen.formatToken(event.type.name())).hiddenText("Local | " + TimeUtils.timeSense(event.timestamp)).description(screen.compact(event.details)).build();
                    row.setHeight(34);
                    row.entranceAnimationEnabled = false;
                    screen.activityContainer.addWidget(row);
                }
            }
        }
        if (renderedEvents.isEmpty()) screen.activityContainer.addWidget(screen.createEmptyRow(screen.hasActiveActivityFilter() ? "No Match" : "No Activity", screen.hasActiveActivityFilter() ? "No tracked activity matches current filters." : "No tracked activity is available yet."));
        finishActivity(scroll);
    }

    void renderHistory() {
        float scroll = screen.historyContainer.getScrollOffset();
        screen.historyContainer.clearWidgets();
        screen.historyContainer.columns(1);
        screen.historyContainer.addWidget(screen.createLabel("History"));
        if (screen.addSectionFeedback(screen.historyContainer, PlayerSection.HISTORY) && (screen.managementSnapshot == null || screen.managementSnapshot.localSessions().isEmpty())) {
            finishHistory(scroll);
            return;
        }
        List<PlayerSessionRecord> sessions = screen.getFilteredSessions();
        Set<String> renderedSessions = new LinkedHashSet<>();
        for (PlayerSessionRecord session : sessions) {
            if (session == null) continue;
            renderedSessions.add(session.getStartedAt() + ":" + session.getEndedAt());
            boolean active = screen.isActiveSession(session);
            MountableButtonWidget row = new MountableButtonWidget.Builder(active ? "Active Session" : screen.formatToken(session.getSource())).hiddenText(screen.compact(session.getSessionId())).description(screen.buildSessionDescription(session, active)).build();
            row.setHeight(34);
            row.entranceAnimationEnabled = false;
            screen.historyContainer.addWidget(row);
        }
        if (screen.managementSnapshot != null) {
            for (PlayerSession session : screen.managementSnapshot.localSessions()) {
                if (session == null || !screen.matchesLocalSession(session)) continue;
                String stableId = session.startTime + ":" + session.endTime;
                if (!renderedSessions.add(stableId)) continue;
                long end = session.endTime > 0L ? session.endTime : System.currentTimeMillis();
                int eventCount = session.events != null ? session.events.size() : 0;
                MountableButtonWidget row = new MountableButtonWidget.Builder(session.endTime > 0L ? "Local Session" : "Active Session").hiddenText(screen.formatTimestamp(session.startTime)).description(screen.formatDuration(Math.max(0L, end - session.startTime)) + " | " + eventCount + " Events").build();
                row.setHeight(34);
                row.entranceAnimationEnabled = false;
                screen.historyContainer.addWidget(row);
            }
        }
        if (renderedSessions.isEmpty()) screen.historyContainer.addWidget(screen.createEmptyRow(screen.historySearchQuery.isBlank() ? "No History" : "No Match", screen.historySearchQuery.isBlank() ? "This player has no tracked session history yet." : "No sessions match the current search."));
        finishHistory(scroll);
    }

    private void finishActivity(float scroll) {
        screen.activityContainer.updateWidgetPositions();
        screen.activityContainer.setScrollOffset(scroll);
    }

    private void finishHistory(float scroll) {
        screen.historyContainer.updateWidgetPositions();
        screen.historyContainer.setScrollOffset(scroll);
    }
}
