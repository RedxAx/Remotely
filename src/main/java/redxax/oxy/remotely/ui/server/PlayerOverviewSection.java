package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.data.player.management.PlayerSection;
import restudio.rescreen.util.TimeUtils;

final class PlayerOverviewSection {
    private final PlayerManagementScreen screen;

    PlayerOverviewSection(PlayerManagementScreen screen) {
        this.screen = screen;
    }

    void render() {
        float scroll = screen.overviewContainer.getScrollOffset();
        screen.overviewContainer.clearWidgets();
        screen.overviewContainer.columns(2);
        screen.overviewContainer.addWidget(screen.createLabel("Player"));
        screen.overviewContainer.addWidget(screen.createLabel("Live"));
        if (screen.addSectionFeedback(screen.overviewContainer, PlayerSection.OVERVIEW) && screen.liveData == null) {
            finish(scroll);
            return;
        }
        screen.overviewContainer.addWidget(screen.createRow("Name", screen.resolveDisplayName(), screen.resolveIdentityLabel()));
        screen.overviewContainer.addWidget(screen.createRow("Source", screen.resolveLiveSource(), screen.resolveLiveSourceMeta()));
        screen.overviewContainer.addWidget(screen.createRow("Server", screen.controller.getReSyncServerId(), screen.compact(screen.controller.getServerName())));
        screen.overviewContainer.addWidget(screen.createRow("Status", screen.resolveStatusText(), screen.resolveStatusMeta()));
        screen.overviewContainer.addWidget(screen.createRow("UUID", screen.player.getUuid().toString(), "Copy From Header"));
        screen.overviewContainer.addWidget(screen.createRow("Operator", screen.player.isOp() ? "Enabled" : "Disabled", screen.resolveBanMeta()));
        screen.overviewContainer.addWidget(screen.createRow("PlayTime", screen.formatDuration(screen.resolveTotalPlayTime()), screen.resolveSessionMeta()));
        screen.overviewContainer.addWidget(screen.createRow("LastSeen", screen.formatTimestamp(screen.resolveLastSeen()), TimeUtils.timeSense(screen.resolveLastSeen())));
        screen.overviewContainer.addWidget(screen.createRow("Activity", String.valueOf(screen.dossier != null ? screen.dossier.getRecentEvents().size() : 0), "Recent Events"));
        screen.overviewContainer.addWidget(screen.createRow("Sessions", String.valueOf(screen.resolveSessionCount()), "Tracked History"));
        screen.overviewContainer.addWidget(screen.createRow("Modules", String.valueOf(screen.collectModuleIds(screen.dossier).size()), "Tracked Sources"));
        screen.overviewContainer.addWidget(screen.createRow("Data", String.valueOf(screen.dossier != null ? screen.dossier.getFacets().size() : 0), "Facet States"));
        if (screen.liveData == null) {
            screen.overviewContainer.addWidget(screen.createEmptyRow("Live Data", "Waiting for player data."));
        } else {
            screen.addLiveOverviewRows();
        }
        finish(scroll);
    }

    private void finish(float scroll) {
        screen.overviewContainer.updateWidgetPositions();
        screen.overviewContainer.setScrollOffset(scroll);
    }
}
