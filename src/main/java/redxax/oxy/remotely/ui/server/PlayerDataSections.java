package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.data.flow.player.PlayerFacetState;
import redxax.oxy.remotely.data.player.management.PlayerSection;
import redxax.oxy.remotely.data.playerdata.PlayerStatistic;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.util.TimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

final class PlayerDataSections {
    private final PlayerManagementScreen screen;

    PlayerDataSections(PlayerManagementScreen screen) {
        this.screen = screen;
    }

    void renderStats() {
        float scroll = screen.statsContainer.getScrollOffset();
        screen.statsContainer.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        screen.statsContainer.clearWidgets();
        screen.statsContainer.addWidget(screen.createLabel("Stats"));
        if (screen.addSectionFeedback(screen.statsContainer, PlayerSection.STATS)) {
            finishStats(scroll);
            return;
        }
        screen.cachedStats = new ArrayList<>();
        screen.filteredStats = new ArrayList<>();
        if (screen.liveData == null) {
            screen.statsContainer.addWidget(screen.createEmptyRow("No Data", "Waiting for stats data."));
            finishStats(scroll);
            return;
        }
        for (PlayerStatistic stat : screen.liveData.flattenedStatistics()) {
            if (stat != null && !screen.isDataVersionStat(stat.key())) screen.cachedStats.add(stat);
        }
        screen.cachedStats.sort(Comparator.comparing(PlayerStatistic::key, String.CASE_INSENSITIVE_ORDER));
        applyStatsFilter();
        screen.statsContainer.setScrollOffset(scroll);
    }

    void applyStatsFilter() {
        float scroll = screen.statsContainer.getScrollOffset();
        screen.statsContainer.clearWidgets();
        screen.statsContainer.addWidget(screen.createLabel("Stats"));
        if (screen.cachedStats.isEmpty()) {
            screen.statsContainer.addWidget(screen.createEmptyRow("No Stats", "No statistics are available for this player."));
            finishStats(scroll);
            return;
        }
        screen.filteredStats = new ArrayList<>();
        if (screen.statsSearchQuery.isBlank()) {
            screen.filteredStats.addAll(screen.cachedStats);
        } else {
            for (PlayerStatistic stat : screen.cachedStats) {
                if (screen.matchesSearch(screen.formatStatKey(stat.key()), screen.statsSearchQuery)) screen.filteredStats.add(stat);
            }
        }
        if (screen.filteredStats.isEmpty()) {
            screen.statsContainer.addWidget(screen.createEmptyRow("No Match", "No statistics match the current search."));
            finishStats(scroll);
            return;
        }
        String previousCategory = null;
        for (PlayerStatistic stat : screen.filteredStats) {
            String category = screen.extractCategory(stat.key());
            if (!Objects.equals(previousCategory, category)) {
                screen.statsContainer.addWidget(screen.buildStatsCategoryRow(category));
                previousCategory = category;
            }
            screen.statsContainer.addWidget(screen.buildStatRow(stat));
        }
        finishStats(scroll);
    }

    void renderExtensions() {
        if (screen.extensionsContainer == null) return;
        float scroll = screen.extensionsContainer.getScrollOffset();
        screen.extensionsContainer.clearWidgets();
        screen.extensionsContainer.columns(1);
        screen.extensionsContainer.addWidget(screen.createLabel("Extensions"));
        if (screen.addSectionFeedback(screen.extensionsContainer, PlayerSection.EXTENSIONS) && (screen.managementSnapshot == null || screen.managementSnapshot.extensionFacets().isEmpty())) {
            finishExtensions(scroll);
            return;
        }
        boolean populated = false;
        if (screen.managementSnapshot != null) {
            for (PlayerFacetState facet : screen.managementSnapshot.extensionFacets().values()) {
                if (screen.isTabFacet(facet)) continue;
                screen.extensionsContainer.addWidget(screen.createRow(screen.resolveFacetTitle(facet), screen.compactMap(facet.getData()), TimeUtils.timeSense(facet.getUpdatedAt())));
                populated = true;
            }
        }
        if (!populated) screen.extensionsContainer.addWidget(screen.createEmptyRow("No Extensions", "No extension data is available for this player."));
        finishExtensions(scroll);
    }

    private void finishStats(float scroll) {
        screen.statsContainer.updateWidgetPositions();
        screen.statsContainer.setScrollOffset(scroll);
    }

    private void finishExtensions(float scroll) {
        screen.extensionsContainer.updateWidgetPositions();
        screen.extensionsContainer.setScrollOffset(scroll);
    }
}
