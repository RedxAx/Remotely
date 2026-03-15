package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerDataManager;
import redxax.oxy.remotely.data.playerdata.PlayerEffect;
import redxax.oxy.remotely.data.playerdata.PlayerStatistic;
import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.ui.server.PlayerManagementScreen;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TabSwitchWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.SearchUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PlayerDataPopup extends PopupWidget {
    private static final String SEARCH_ROW_ID = "playerdata-search";
    private static final String SUGGEST_ROW_ID = "playerdata-suggest";
    private static final int MAX_SUGGESTIONS = 3;

    private final PlayerDataSection[] sections = PlayerDataSection.values();
    private PlayerDataSection activeSection = PlayerDataSection.Overview;
    private final Container sectionContainer;
    private final Container suggestionContainer;
    private final TextInputWidget searchField;
    private final ScheduledThreadPoolExecutor scheduler;
    private final UnifiedPlayer player;
    private final PlayerManagerController controller;
    private final Screen parentScreen;
    private final Map<PlayerDataSection, Float> scrollOffsets = new EnumMap<>(PlayerDataSection.class);

    private String currentSearch = "";
    private List<PlayerStatistic> cachedStats = new ArrayList<>();
    private List<PlayerStatistic> filteredStats = new ArrayList<>();
    private PlayerData cachedData;
    private long refreshIntervalMs;
    private String lastSource;
    private volatile long interactionLockUntilMs;

    public PlayerDataPopup(Screen parent, UnifiedPlayer player, PlayerManagerController controller) {
        super(0, 0, 520, 340, "Player Data: " + (player.getName() != null ? player.getName() : "Unknown"));
        setLayer(500);
        this.player = player;
        this.controller = controller;
        this.parentScreen = parent;

        Builder builder = new Builder(getTitle()).size(520, 340).setResizable(true).setAntiOutOfBound(true);
        List<String> sectionLabels = new ArrayList<>();
        List<PlayerDataSection> sectionList = new ArrayList<>();
        for (PlayerDataSection section : sections) {
            if (!isSectionAllowed(section)) {
                continue;
            }
            sectionLabels.add(section.label());
            sectionList.add(section);
        }
        PlayerDataSection[] visibleSections = sectionList.toArray(PlayerDataSection[]::new);

        TabSwitchWidget sectionSwitch = new TabSwitchWidget.Builder()
            .options(sectionLabels)
            .size(360, 18)
            .currentIndex(0)
            .build();

        searchField = new TextInputWidget.Builder().placeholder("Search Stats").size(200, 18).build();
        searchField.setIgnoreScissorRegion(true);
        searchField.setActive(true);
        searchField.setVisible(true);
        searchField.onChange = () -> {
            currentSearch = searchField.getText().trim();
            if (activeSection == PlayerDataSection.Stats) {
                applyStatsFilter();
            }
        };
        searchField.onEscape = () -> {
            searchField.setText("");
            currentSearch = "";
            if (activeSection == PlayerDataSection.Stats) {
                applyStatsFilter();
            }
        };

        suggestionContainer = new Container(0, 0, 480, 0);
        suggestionContainer.layout(new ManagedLayout()).columns(3).padding(1).verticalSpacing(1).enableSelecting(false).scrolling(false).backgroundDrawing(false);
        suggestionContainer.setIgnoreScissorRegion(true);
        suggestionContainer.setVisible(false);
        suggestionContainer.setActive(false);

        sectionContainer = new Container(0, 0, 480, 224);
        sectionContainer.layout(new ManagedLayout()).columns(2).padding(4).verticalSpacing(2).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        sectionContainer.entranceAnimationEnabled = false;

        builder.addRow("Section", true, 20, sectionSwitch);
        builder.addRow("", false, 20, new IconButton.Builder().imagePath("external.png").label("Open Player Management").onClick(this::openPlayerManagmentScreen).accentType(ThemeManager.getAccent("calm")).build());
        builder.addRow(SEARCH_ROW_ID, "", true, 20, searchField);
        builder.addRow(SUGGEST_ROW_ID, "", true, 20, suggestionContainer);
        builder.addRow("", true, 224, sectionContainer);
        builder.onClose(this::closePopup);

        PopupWidget configured = builder.build();
        rows.addAll(configured.rows);
        setSize(configured.getWidth(), configured.getHeight());
        setPosition(configured.getX(), configured.getY());

        parent.addDrawableChild(this);
        show();

        scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "Remotely-PlayerData-Refresh");
            thread.setDaemon(true);
            return thread;
        });
        refreshIntervalMs = resolveRefreshInterval();
        scheduleRefresh();

        sectionSwitch.setOnChange(() -> {
            int index = sectionSwitch.getCurrentIndex();
            if (index >= 0 && index < visibleSections.length) {
                scrollOffsets.put(activeSection, sectionContainer.getScrollOffset());
                activeSection = visibleSections[index];
                updateHeaderVisibility();
                if (cachedData != null) {
                    rebuildSection(cachedData);
                }
            }
        });

        sectionContainer.clearWidgets();
        sectionContainer.addWidget(buildInfoRow("Status", "Loading"));
        sectionContainer.updateWidgetPositions();

        if (visibleSections.length > 0) {
            activeSection = visibleSections[0];
        }
        updateHeaderVisibility();
        requestRefresh(true);
        onClose = this::closePopup;
    }

    private boolean isSectionAllowed(PlayerDataSection section) {
        return true;
    }

    private void closePopup() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        hide();
    }

    private void openPlayerManagmentScreen() {
        closePopup();
        controller.requestPlayerDossier(player.getUuid());
        PlayerManagementScreen screen = new PlayerManagementScreen(player, controller, parentScreen);
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            RemotelyClient.INSTANCE.getHost().setScreen(screen);
            return;
        }
        ScreenManager.getInstance().setScreen(screen);
    }

    private void scheduleRefresh() {
        if (refreshIntervalMs <= 0L) {
            return;
        }
        scheduler.scheduleAtFixedRate(() -> requestRefresh(false), 0L, refreshIntervalMs, TimeUnit.MILLISECONDS);
    }

    private long resolveRefreshInterval() {
        if (controller == null || controller.getInstance() == null) {
            return 1000L;
        }
        String raw = controller.getInstance().getSettings().getProperty("playerdata.refresh.intervalMs", "1000");
        try {
            long parsed = Long.parseLong(raw);
            if (parsed < 250L) {
                return 250L;
            }
            if (parsed > 10000L) {
                return 10000L;
            }
            return parsed;
        } catch (Exception ignored) {
            return 1000L;
        }
    }

    private void requestRefresh(boolean immediate) {
        if (controller == null || player == null) {
            return;
        }
        PlayerDataManager dataManager = controller.getPlayerDataManager();
        if (dataManager == null) {
            return;
        }
        long interval = immediate ? 0L : refreshIntervalMs;
        dataManager.refreshIfDue(player.getUuid(), player.getName(), player.isOnline(), interval).thenAccept(data -> {
            if (data == null) {
                return;
            }
            if (System.currentTimeMillis() < interactionLockUntilMs || isInventoryInteractionActive() || isSameData(data, cachedData)) {
                return;
            }
            cachedData = data;
            lastSource = dataManager.getSource(player.getUuid());
            ScreenManager.getInstance().execute(() -> {
                scrollOffsets.put(activeSection, sectionContainer.getScrollOffset());
                rebuildSection(data);
            });
        });
    }

    private boolean isInventoryInteractionActive() {
        if (sectionContainer.getWidgets().isEmpty()) {
            return false;
        }
        AnimatedWidget widget = sectionContainer.getWidgets().getFirst();
        return widget instanceof InventoryWidget inventoryWidget && inventoryWidget.isInteractionActive();
    }

    private void markInventoryInteraction() {
        interactionLockUntilMs = System.currentTimeMillis() + 400L;
    }

    private void rebuildSection(PlayerData data) {
        if (data == null) {
            sectionContainer.clearWidgets();
            sectionContainer.addWidget(buildInfoRow("Status", "No Data"));
            sectionContainer.updateWidgetPositions();
            return;
        }
        if (activeSection == PlayerDataSection.Overview) {
            buildOverview(data);
        } else if (activeSection == PlayerDataSection.Inventory) {
            buildInventory(data);
        } else if (activeSection == PlayerDataSection.EnderChest) {
            buildEnderChest(data);
        } else if (activeSection == PlayerDataSection.Effects) {
            buildEffects(data);
        } else if (activeSection == PlayerDataSection.Stats) {
            buildStats(data);
        }
        Float offset = scrollOffsets.get(activeSection);
        if (offset != null) {
            sectionContainer.setScrollOffset(offset);
        }
    }

    private void updateHeaderVisibility() {
        boolean showSearch = activeSection == PlayerDataSection.Stats;
        setRowVisibility(SEARCH_ROW_ID, showSearch);
        setRowVisibility(SUGGEST_ROW_ID, showSearch && suggestionContainer.isVisible());
        searchField.setVisible(showSearch);
        searchField.setActive(showSearch);
        if (!showSearch) {
            searchField.setText("");
            currentSearch = "";
            suggestionContainer.clearWidgets();
            suggestionContainer.setVisible(false);
            suggestionContainer.setActive(false);
            suggestionContainer.setHeight(0);
        }
    }

    private void buildOverview(PlayerData data) {
        sectionContainer.layout(new ManagedLayout()).columns(1).padding(2).verticalSpacing(2).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        sectionContainer.clearWidgets();
        sectionContainer.setGroupHeaderEnabled(false);

        sectionContainer.addWidget(buildInfoRow("Health", formatNumber(data.health())));
        sectionContainer.addWidget(buildInfoRow("Food", String.valueOf(data.food())));
        sectionContainer.addWidget(buildInfoRow("Saturation", formatNumber(data.saturation())));
        sectionContainer.addWidget(buildInfoRow("XP", data.experienceLevel() + " | " + formatNumber(data.experienceProgress())));
        sectionContainer.addWidget(buildInfoRow("Total XP", String.valueOf(data.totalExperience())));
        sectionContainer.addWidget(buildInfoRow("Game Mode", data.gameMode() != null ? formatLabel(data.gameMode()) : "Unknown"));
        sectionContainer.addWidget(buildInfoRow("Flying", data.flying() ? "Yes" : "No"));
        sectionContainer.addWidget(buildInfoRow("Gliding", data.fallFlying() ? "Yes" : "No"));
        if (player != null && player.isOnline() && lastSource != null && !lastSource.isBlank() && "world".equalsIgnoreCase(lastSource)) {
            sectionContainer.addWidget(buildInfoRow("Source", "Offline"));
        }
        if (data.location() != null) {
            String dimension = data.location().dimension() != null ? data.location().dimension() : "Unknown";
            String position = formatCoord(data.location().x()) + ", " + formatCoord(data.location().y()) + ", " + formatCoord(data.location().z());
            sectionContainer.addWidget(buildInfoRow("Dimension", formatLabel(dimension)));
            sectionContainer.addWidget(buildInfoRow("Location", position));
        }
        sectionContainer.addWidget(buildInfoRow("Inventory", String.valueOf(data.inventory().size())));
        sectionContainer.addWidget(buildInfoRow("Armor", String.valueOf(data.armor().size())));
        sectionContainer.addWidget(buildInfoRow("Effects", String.valueOf(data.effects().size())));
        sectionContainer.addWidget(buildInfoRow("Stats", String.valueOf(data.flattenedStatistics().size())));
        sectionContainer.updateWidgetPositions();
    }

    private void buildInventory(PlayerData data) {
        sectionContainer.layout(new ManagedLayout()).columns(1).padding(0).verticalSpacing(0).enableSelecting(false).scrolling(false).backgroundDrawing(false);
        sectionContainer.clearWidgets();
        sectionContainer.setGroupHeaderEnabled(false);
        InventoryWidget widget = new InventoryWidget(0, 0, sectionContainer.getEffectiveWidth(), sectionContainer.getHeight(), player, controller, data, InventoryWidget.Mode.INVENTORY, this::canManipulateInventory, this::markInventoryInteraction, delay -> scheduler.schedule(() -> requestRefresh(true), delay, TimeUnit.MILLISECONDS));
        sectionContainer.addWidget(widget);
        sectionContainer.updateWidgetPositions();
    }

    private void buildEnderChest(PlayerData data) {
        sectionContainer.layout(new ManagedLayout()).columns(1).padding(0).verticalSpacing(0).enableSelecting(false).scrolling(false).backgroundDrawing(false);
        sectionContainer.clearWidgets();
        sectionContainer.setGroupHeaderEnabled(false);
        InventoryWidget widget = new InventoryWidget(0, 0, sectionContainer.getEffectiveWidth(), sectionContainer.getHeight(), player, controller, data, InventoryWidget.Mode.ENDER_CHEST, this::canManipulateInventory, this::markInventoryInteraction, delay -> scheduler.schedule(() -> requestRefresh(true), delay, TimeUnit.MILLISECONDS));
        sectionContainer.addWidget(widget);
        sectionContainer.updateWidgetPositions();
    }

    private boolean canManipulateInventory() {
        return player != null && player.isOnline() && "rcon".equalsIgnoreCase(lastSource);
    }

    private void buildEffects(PlayerData data) {
        sectionContainer.layout(new ManagedLayout()).columns(1).padding(2).verticalSpacing(2).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        sectionContainer.clearWidgets();
        sectionContainer.setGroupHeaderEnabled(false);
        if (data.effects().isEmpty()) {
            sectionContainer.addWidget(buildInfoRow("Status", "No Effects"));
        } else {
            List<PlayerEffect> effects = new ArrayList<>(data.effects());
            effects.sort(Comparator.comparing(PlayerEffect::id, String.CASE_INSENSITIVE_ORDER));
            for (PlayerEffect effect : effects) {
                sectionContainer.addWidget(buildEffectRow(effect));
            }
        }
        sectionContainer.updateWidgetPositions();
    }

    private void buildStats(PlayerData data) {
        sectionContainer.layout(new ManagedLayout()).columns(1).padding(2).verticalSpacing(2).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        sectionContainer.clearWidgets();
        sectionContainer.setGroupHeaderEnabled(false);
        cachedStats = new ArrayList<>();
        for (PlayerStatistic stat : data.flattenedStatistics()) {
            if (stat == null || isDataVersionStat(stat.key())) {
                continue;
            }
            cachedStats.add(stat);
        }
        cachedStats.sort(Comparator.comparing(PlayerStatistic::key, String.CASE_INSENSITIVE_ORDER));
        applyStatsFilter();
    }

    private void applyStatsFilter() {
        updateSuggestions();
        if (cachedStats.isEmpty()) {
            sectionContainer.clearWidgets();
            sectionContainer.addWidget(buildInfoRow("Status", "No Stats"));
            sectionContainer.updateWidgetPositions();
            return;
        }
        filteredStats = new ArrayList<>();
        if (currentSearch.isEmpty()) {
            filteredStats.addAll(cachedStats);
        } else {
            for (PlayerStatistic stat : cachedStats) {
                String label = formatStatKey(stat.key());
                if (SearchUtils.isFuzzyMatch(label, currentSearch) || label.toLowerCase(Locale.ROOT).contains(currentSearch.toLowerCase(Locale.ROOT))) {
                    filteredStats.add(stat);
                }
            }
        }
        if (filteredStats.isEmpty()) {
            sectionContainer.clearWidgets();
            sectionContainer.addWidget(buildInfoRow("Status", "No Match"));
            sectionContainer.updateWidgetPositions();
            return;
        }
        sectionContainer.clearWidgets();
        String previousCategory = null;
        for (PlayerStatistic stat : filteredStats) {
            String category = extractCategory(stat.key());
            if (!Objects.equals(previousCategory, category)) {
                sectionContainer.addWidget(buildStatsCategoryRow(category));
                previousCategory = category;
            }
            sectionContainer.addWidget(buildStatRow(stat));
        }
        sectionContainer.updateWidgetPositions();
    }

    private void updateSuggestions() {
        suggestionContainer.clearWidgets();
        if (currentSearch.isBlank() || cachedStats.isEmpty()) {
            suggestionContainer.setVisible(false);
            suggestionContainer.setActive(false);
            suggestionContainer.setHeight(0);
            setRowVisibility(SUGGEST_ROW_ID, false);
            return;
        }
        Map<String, Double> scored = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (PlayerStatistic stat : cachedStats) {
            String label = formatStatKey(stat.key());
            double score = SearchUtils.getMatchScore(label, currentSearch);
            if (score < 0.3d) {
                continue;
            }
            Double existing = scored.get(label);
            if (existing == null || score > existing) {
                scored.put(label, score);
            }
        }
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(scored.entrySet());
        ordered.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int count = Math.min(MAX_SUGGESTIONS, ordered.size());
        if (count <= 0) {
            suggestionContainer.setVisible(false);
            suggestionContainer.setActive(false);
            suggestionContainer.setHeight(0);
            setRowVisibility(SUGGEST_ROW_ID, false);
            return;
        }
        int columns = Math.min(3, count);
        suggestionContainer.columns(columns);
        for (int i = 0; i < count; i++) {
            String label = ordered.get(i).getKey();
            suggestionContainer.addWidget(new IconButton.Builder().label(label).size(120, 18).onClick(() -> applySuggestion(label)).build());
        }
        int rows = (count + columns - 1) / columns;
        int height = suggestionContainer.getPadding() * 2 + rows * 18 + Math.max(0, rows - 1) * suggestionContainer.getVerticalSpacing();
        suggestionContainer.setHeight(height);
        suggestionContainer.setVisible(true);
        suggestionContainer.setActive(true);
        suggestionContainer.updateWidgetPositions();
        setRowVisibility(SUGGEST_ROW_ID, true);
    }

    private void applySuggestion(String label) {
        searchField.setText(label);
        currentSearch = label;
        applyStatsFilter();
    }

    private MountableButtonWidget buildInfoRow(String title, String value) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(value).build();
        row.entranceAnimationEnabled = false;
        return row;
    }

    private MountableButtonWidget buildEffectRow(PlayerEffect effect) {
        String title = effect != null ? formatLabel(effect.id()) : "";
        String meta = effect != null ? ("Lv " + (effect.amplifier() + 1) + " | " + (effect.duration() == -1 ? "∞" : effect.duration() + "t")) : "";
        if (effect != null && !effect.showParticles()) {
            meta += " | Hidden";
        }
        if (effect != null && effect.ambient()) {
            meta += " | Ambient";
        }
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(meta).build();
        row.entranceAnimationEnabled = false;
        return row;
    }

    private MountableButtonWidget buildStatRow(PlayerStatistic stat) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(formatStatKey(stat.key())).description(String.valueOf(stat.value())).build();
        row.entranceAnimationEnabled = false;
        return row;
    }

    private MountableButtonWidget buildStatsCategoryRow(String category) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(formatLabel(category)).build();
        row.setHeight(18);
        row.setActive(false);
        row.entranceAnimationEnabled = false;
        return row;
    }

    private boolean isDataVersionStat(String rawKey) {
        return "dataversion".equalsIgnoreCase(formatStatKey(rawKey).replace(" ", ""));
    }

    private boolean isSameData(PlayerData next, PlayerData previous) {
        if (next == previous) {
            return true;
        }
        if (next == null || previous == null) {
            return false;
        }
        if (Double.compare(next.health(), previous.health()) != 0) {
            return false;
        }
        if (next.food() != previous.food()) {
            return false;
        }
        if (Float.compare(next.saturation(), previous.saturation()) != 0) {
            return false;
        }
        if (next.experienceLevel() != previous.experienceLevel()) {
            return false;
        }
        if (Float.compare(next.experienceProgress(), previous.experienceProgress()) != 0) {
            return false;
        }
        if (next.totalExperience() != previous.totalExperience()) {
            return false;
        }
        if (!Objects.equals(next.location(), previous.location())) {
            return false;
        }
        if (!Objects.equals(next.gameMode(), previous.gameMode())) {
            return false;
        }
        if (next.flying() != previous.flying()) {
            return false;
        }
        if (next.fallFlying() != previous.fallFlying()) {
            return false;
        }
        if (!Objects.equals(next.inventory(), previous.inventory())) {
            return false;
        }
        if (!Objects.equals(next.armor(), previous.armor())) {
            return false;
        }
        if (!Objects.equals(next.offhand(), previous.offhand())) {
            return false;
        }
        if (!Objects.equals(next.enderChest(), previous.enderChest())) {
            return false;
        }
        if (!Objects.equals(next.effects(), previous.effects())) {
            return false;
        }
        if (!Objects.equals(next.attributes(), previous.attributes())) {
            return false;
        }
        if (!Objects.equals(next.statistics(), previous.statistics())) {
            return false;
        }
        if (!Objects.equals(next.flattenedStatistics(), previous.flattenedStatistics())) {
            return false;
        }
        return next.onlineOnly() == previous.onlineOnly();
    }

    private String extractCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Other";
        }
        String cleaned = raw;
        if (cleaned.startsWith("stats.")) {
            cleaned = cleaned.substring("stats.".length());
        }
        String[] parts = cleaned.split("\\.");
        String candidate = parts.length > 0 ? parts[0] : cleaned;
        if ("stats".equalsIgnoreCase(candidate) && parts.length > 1) {
            candidate = parts[1];
        }
        if (candidate.contains(":")) {
            String[] split = candidate.split(":", 2);
            candidate = split.length > 1 ? split[1] : split[0];
        }
        return candidate.isBlank() ? "Other" : candidate;
    }

    private String formatCoord(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("minecraft:", "").replace('_', ' ');
        return cleaned.isEmpty() ? cleaned : titleCase(cleaned);
    }

    private String formatStatKey(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("stats.", "").replace("minecraft:", "").replace('_', ' ');
        return titleCase(cleaned.replace(".", " > "));
    }

    private String titleCase(String value) {
        String[] parts = value.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return builder.toString();
    }
}
