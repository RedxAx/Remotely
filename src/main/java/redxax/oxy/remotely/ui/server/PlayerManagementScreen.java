package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.flow.player.PlayerEventRecord;
import redxax.oxy.remotely.data.flow.player.PlayerFacetMetadata;
import redxax.oxy.remotely.data.flow.player.PlayerFacetState;
import redxax.oxy.remotely.data.flow.player.PlayerSessionRecord;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.player.model.BanInfo;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerDataManager;
import redxax.oxy.remotely.data.playerdata.PlayerEffect;
import redxax.oxy.remotely.data.playerdata.PlayerItem;
import redxax.oxy.remotely.data.playerdata.PlayerStatistic;
import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.ui.widgets.management.BanPlayerPopup;
import redxax.oxy.remotely.ui.widgets.management.InventoryWidget;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.account.Account;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.ResourceManager;
import restudio.rescreen.util.SearchUtils;
import restudio.rescreen.util.TimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static restudio.rescreen.config.Config.desktopMode;

public class PlayerManagementScreen extends ReScreen implements DesktopWindowBehaviorProvider {
    private static final int CONTENT_Y = 60;
    private static final int DETAILS_PANEL_WIDTH = 280;
    private static final String TAB_OVERVIEW = "Overview";
    private static final String TAB_ACTIVITY = "Activity";
    private static final String TAB_HISTORY = "History";
    private static final String TAB_INVENTORY = "Inventory";
    private static final String TAB_ENDER_CHEST = "Ender Chest";
    private static final String TAB_EFFECTS = "Effects";
    private static final String TAB_STATS = "Stats";

    private final UnifiedPlayer player;
    private final PlayerManagerController controller;
    private final Object parent;

    private final Map<String, Container> facetContainers = new LinkedHashMap<>();
    private final Map<String, Container> moduleContainers = new LinkedHashMap<>();
    private final Map<String, String> facetIdsByTabName = new LinkedHashMap<>();
    private final Map<String, String> moduleIdsByTabName = new LinkedHashMap<>();

    private Container overviewContainer;
    private Container activityContainer;
    private Container historyContainer;
    private Container inventoryContainer;
    private Container enderChestContainer;
    private Container effectsContainer;
    private Container statsContainer;
    private SidePanel detailsPanel;

    private IconButton playerHeaderButton;

    private DropDownWidget<String> sourceFilterDropdown;
    private DropDownWidget<String> eventFilterDropdown;
    private DropDownWidget<String> timeFilterDropdown;

    private SearchMode activitySearchMode;
    private SearchMode historySearchMode;
    private SearchMode inventorySearchMode;
    private SearchMode enderChestSearchMode;
    private SearchMode effectsSearchMode;
    private SearchMode statsSearchMode;

    private final List<String> selectedSourceFilters = new ArrayList<>();
    private final List<String> negativeSourceFilters = new ArrayList<>();
    private final List<String> selectedEventFilters = new ArrayList<>();
    private final List<String> negativeEventFilters = new ArrayList<>();
    private final List<String> selectedTimeFilters = new ArrayList<>();
    private final List<String> negativeTimeFilters = new ArrayList<>();

    private String activitySearchQuery = "";
    private String historySearchQuery = "";
    private String inventorySearchQuery = "";
    private String enderChestSearchQuery = "";
    private String effectsSearchQuery = "";
    private String statsSearchQuery = "";
    private boolean updatingFilters;
    private boolean sourceFilterVisible;
    private boolean eventFilterVisible;
    private boolean timeFilterVisible;

    private PlayerDossier dossier;
    private PlayerData liveData;
    private String liveDataSource;
    private Identifier playerFace;
    private boolean faceRequested;

    private String dossierSignature = "";
    private String moduleSignature = "";
    private long playerDataRefreshIntervalMs;
    private long nextPlayerDataRefreshAtMs;
    private long scheduledForcedPlayerDataRefreshAtMs = -1L;
    private volatile long interactionLockUntilMs;

    private List<PlayerStatistic> cachedStats = new ArrayList<>();
    private List<PlayerStatistic> filteredStats = new ArrayList<>();

    public PlayerManagementScreen(UnifiedPlayer player, PlayerManagerController controller, Object parent) {
        this.player = player;
        this.controller = controller;
        this.parent = parent;
    }

    public String getDesktopAppId() {
        return "player-management-" + player.getUuid();
    }

    public String getDesktopAppTitle() {
        return "Player Management";
    }

    public String getDesktopAppIconPath() {
        return "steve.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return DesktopWindowBehavior.DEFAULT_REPLACE;
    }

    @Override
    public void init() {
        super.init();
        playerDataRefreshIntervalMs = resolvePlayerDataRefreshInterval();
        nextPlayerDataRefreshAtMs = 0L;
        statusBar().size(14).visible(false).build();
        buildHeader();
        buildFilters();
        buildTabs();
        buildSidePanel();
        refreshPlayerData(false);
        requestLivePlayerData(true);
        rebuildAll(true);
    }

    @Override
    public void tick() {
        super.tick();
        requestFace();
        refreshLiveDataIfDue();

        PlayerDossier next = controller.getPlayerDossier(player.getUuid());
        String nextDossierSignature = buildDossierSignature(next);
        if (!Objects.equals(nextDossierSignature, dossierSignature)) {
            dossier = next;
            dossierSignature = nextDossierSignature;
            String nextModuleSignature = buildModuleSignature(next);
            boolean modulesChanged = !Objects.equals(nextModuleSignature, moduleSignature);
            moduleSignature = nextModuleSignature;
            if (modulesChanged) {
                rebuildTabsData();
            }
            updateFilterOptions();
            rebuildOverview();
            rebuildActivity();
            rebuildHistory();
            rebuildInventory();
            rebuildEnderChest();
            rebuildEffects();
            rebuildStats();
            rebuildFacetTabs();
            rebuildModuleTabs();
            rebuildSidePanel();
        }
        updateHeaderButton();
    }

    @Override
    public void updatePositions() {
        super.updatePositions();

        if (tabs() != null) {
            tabs().setPosition(5, 35);
            tabs().setSize(width - 10, 18);
            tabs().updateLayout();
        }

        if (detailsPanel != null) {
            detailsPanel.y(CONTENT_Y).height(height - CONTENT_Y - 5).width(DETAILS_PANEL_WIDTH);
        }

        resizeContainer(overviewContainer);
        resizeContainer(activityContainer);
        resizeContainer(historyContainer);
        resizeContainer(inventoryContainer);
        resizeContainer(enderChestContainer);
        resizeContainer(effectsContainer);
        resizeContainer(statsContainer);
        for (Container container : facetContainers.values()) {
            resizeContainer(container);
        }
        for (Container container : moduleContainers.values()) {
            resizeContainer(container);
        }

        int filterY = 36;
        int cursorX = width - 5;

        if (timeFilterDropdown != null && timeFilterDropdown.isVisible()) {
            cursorX -= timeFilterDropdown.getWidth();
            timeFilterDropdown.setPosition(cursorX, filterY);
            cursorX -= 1;
        }
        if (eventFilterDropdown != null && eventFilterDropdown.isVisible()) {
            cursorX -= eventFilterDropdown.getWidth();
            eventFilterDropdown.setPosition(cursorX, filterY);
            cursorX -= 1;
        }
        if (sourceFilterDropdown != null && sourceFilterDropdown.isVisible()) {
            cursorX -= sourceFilterDropdown.getWidth();
            sourceFilterDropdown.setPosition(cursorX, filterY);
        }
    }

    @Override
    public void close() {
        if (isDesktopWindow()) {
            super.close();
            return;
        }
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            RemotelyClient.INSTANCE.getHost().openParentScreen(this, parent);
            return;
        }
        if (parent instanceof Screen screen) {
            ScreenManager.getInstance().setScreen(screen);
            return;
        }
        super.close();
    }

    private void buildHeader() {
        header().reset();

        playerHeaderButton = new IconButton.Builder()
            .label(resolveDisplayName())
            .image(playerFace)
            .autoWidthOnTextChange(true)
            .iconSize(16)
            .size(160, 18)
            .centered(false)
            .onClick(() -> {})
            .build();
        header().addLeft(playerHeaderButton);

        if (!desktopMode) {
            header().addRight("close.png", this::close, "");
        }
        header().addRight("reload.png", this::refreshAllData, "Refresh Player")
            .addRight("clipboard.png", this::copyUuid, "Copy UUID")
            .build();
    }

    private void buildFilters() {
        activitySearchMode = new SearchMode(false);
        activitySearchMode.setPlaceholder("Search Activity");
        activitySearchMode.setOnTextChange(this::onActivitySearch);
        activitySearchMode.setOnSearchEnter(this::onActivitySearch);

        historySearchMode = new SearchMode(false);
        historySearchMode.setPlaceholder("Search History");
        historySearchMode.setOnTextChange(this::onHistorySearch);
        historySearchMode.setOnSearchEnter(this::onHistorySearch);

        inventorySearchMode = new SearchMode(false);
        inventorySearchMode.setPlaceholder("Search Inventory");
        inventorySearchMode.setOnTextChange(this::onInventorySearch);
        inventorySearchMode.setOnSearchEnter(this::onInventorySearch);

        enderChestSearchMode = new SearchMode(false);
        enderChestSearchMode.setPlaceholder("Search Ender Chest");
        enderChestSearchMode.setOnTextChange(this::onEnderChestSearch);
        enderChestSearchMode.setOnSearchEnter(this::onEnderChestSearch);

        effectsSearchMode = new SearchMode(false);
        effectsSearchMode.setPlaceholder("Search Effects");
        effectsSearchMode.setOnTextChange(this::onEffectsSearch);
        effectsSearchMode.setOnSearchEnter(this::onEffectsSearch);

        statsSearchMode = new SearchMode(false);
        statsSearchMode.setPlaceholder("Search Stats");
        statsSearchMode.setOnTextChange(this::onStatsSearch);
        statsSearchMode.setOnSearchEnter(this::onStatsSearch);

        sourceFilterDropdown = new DropDownWidget.Builder<>(List.<String>of())
            .size(128, 18)
            .multiSelect(true)
            .onMultiSelectionChanged(dropdown -> {
                if (updatingFilters) {
                    return;
                }
                selectedSourceFilters.clear();
                selectedSourceFilters.addAll(dropdown.getSelectedItems());
                negativeSourceFilters.clear();
                negativeSourceFilters.addAll(dropdown.getNegativeSelectedItems());
                rebuildActiveTabContent();
            })
            .animateElevation(false)
            .build();
        sourceFilterDropdown.setPriority(600);
        sourceFilterDropdown.setVisible(false);

        eventFilterDropdown = new DropDownWidget.Builder<>(List.<String>of())
            .size(128, 18)
            .multiSelect(true)
            .onMultiSelectionChanged(dropdown -> {
                if (updatingFilters) {
                    return;
                }
                selectedEventFilters.clear();
                selectedEventFilters.addAll(dropdown.getSelectedItems());
                negativeEventFilters.clear();
                negativeEventFilters.addAll(dropdown.getNegativeSelectedItems());
                rebuildActiveTabContent();
            })
            .animateElevation(false)
            .build();
        eventFilterDropdown.setPriority(600);
        eventFilterDropdown.setVisible(false);

        timeFilterDropdown = new DropDownWidget.Builder<>(List.of("Hour", "Day", "Week", "Older"))
            .size(108, 18)
            .multiSelect(true)
            .onMultiSelectionChanged(dropdown -> {
                if (updatingFilters) {
                    return;
                }
                selectedTimeFilters.clear();
                selectedTimeFilters.addAll(dropdown.getSelectedItems());
                negativeTimeFilters.clear();
                negativeTimeFilters.addAll(dropdown.getNegativeSelectedItems());
                rebuildActiveTabContent();
            })
            .animateElevation(false)
            .build();
        timeFilterDropdown.setPriority(600);
        timeFilterDropdown.setVisible(false);

        addDrawableChild(sourceFilterDropdown, eventFilterDropdown, timeFilterDropdown);
    }

    private void buildTabs() {
        tabs().builder()
            .position(5, 35)
            .size(width - 10, 18)
            .allowAdd(false)
            .allowClose(false)
            .allowReorder(false)
            .allowRename(false)
            .onTabSelected(this::onTabSwitch)
            .build();

        overviewContainer = createManagedContainer("player_overview", null);
        activityContainer = createManagedContainer("player_activity", activitySearchMode);
        historyContainer = createManagedContainer("player_history", historySearchMode);
        inventoryContainer = createManagedContainer("player_inventory", inventorySearchMode);
        enderChestContainer = createManagedContainer("player_ender_chest", enderChestSearchMode);
        effectsContainer = createManagedContainer("player_effects", effectsSearchMode);
        statsContainer = createManagedContainer("player_stats", statsSearchMode);
    }

    private Container createManagedContainer(String id, SearchMode searchMode) {
        Container existing = getContainer(id);
        if (existing != null) {
            existing.setSearchMode(searchMode);
            return existing;
        }
        Container container = createContainer(id, 5, CONTENT_Y, width - 10, height - CONTENT_Y - 5);
        container.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).scrolling(true).enableSelecting(false);
        container.setSearchMode(searchMode);
        return container;
    }

    private void buildSidePanel() {
        detailsPanel = createSidePanel("player_management_panel").width(DETAILS_PANEL_WIDTH).y(CONTENT_Y).height(height - CONTENT_Y - 5).show();
        Container panel = detailsPanel.container();
        panel.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).scrolling(true).enableSelecting(false);
    }

    private void rebuildAll(boolean initial) {
        if (!initial) {
            dossierSignature = buildDossierSignature(dossier);
        }
        moduleSignature = buildModuleSignature(dossier);
        rebuildTabsData();
        updateFilterOptions();
        rebuildOverview();
        rebuildActivity();
        rebuildHistory();
        rebuildInventory();
        rebuildEnderChest();
        rebuildEffects();
        rebuildStats();
        rebuildFacetTabs();
        rebuildModuleTabs();
        rebuildSidePanel();
        updateHeaderButton();
    }

    private void rebuildTabsData() {
        String activeTabName = tabs().getActiveTab() != null ? tabs().getActiveTab().getName() : TAB_OVERVIEW;

        tabs().clearTabs();
        facetIdsByTabName.clear();
        moduleIdsByTabName.clear();

        tabs().addTab(TAB_OVERVIEW, overviewContainer);
        tabs().addTab(TAB_ACTIVITY, activityContainer);
        for (PlayerFacetState facet : collectTabFacets(dossier)) {
            String tabName = uniqueTabName(resolveFacetTabName(facet));
            facetIdsByTabName.put(tabName, facet.getFacetId());
            tabs().addTab(tabName, getOrCreateFacetContainer(facet.getFacetId()));
        }
        tabs().addTab(TAB_HISTORY, historyContainer);
        if (isInventorySectionAllowed()) {
            tabs().addTab(TAB_INVENTORY, inventoryContainer);
            tabs().addTab(TAB_ENDER_CHEST, enderChestContainer);
        }
        tabs().addTab(TAB_EFFECTS, effectsContainer);
        tabs().addTab(TAB_STATS, statsContainer);

        for (String moduleId : collectModuleIds(dossier)) {
            String tabName = uniqueTabName(formatModule(moduleId));
            moduleIdsByTabName.put(tabName, moduleId);
            tabs().addTab(tabName, getOrCreateModuleContainer(moduleId));
        }

        int activeIndex = 0;
        List<TabsManager.Tab> tabList = tabs().getTabs();
        for (int i = 0; i < tabList.size(); i++) {
            if (Objects.equals(tabList.get(i).getName(), activeTabName)) {
                activeIndex = i;
                break;
            }
        }
        if (!tabList.isEmpty()) {
            tabs().setActiveTab(activeIndex);
        }
    }

    private String uniqueTabName(String baseName) {
        String name = baseName;
        int index = 2;
        while (facetIdsByTabName.containsKey(name) || moduleIdsByTabName.containsKey(name) || isBuiltInTab(name)) {
            name = baseName + ' ' + index++;
        }
        return name;
    }

    private boolean isBuiltInTab(String name) {
        return Objects.equals(name, TAB_OVERVIEW)
            || Objects.equals(name, TAB_ACTIVITY)
            || Objects.equals(name, TAB_HISTORY)
            || Objects.equals(name, TAB_INVENTORY)
            || Objects.equals(name, TAB_ENDER_CHEST)
            || Objects.equals(name, TAB_EFFECTS)
            || Objects.equals(name, TAB_STATS);
    }

    private Container getOrCreateFacetContainer(String facetId) {
        return facetContainers.computeIfAbsent(facetId, id -> createManagedContainer("player_facet_" + sanitizeId(id), activitySearchMode));
    }

    private Container getOrCreateModuleContainer(String moduleId) {
        return moduleContainers.computeIfAbsent(moduleId, id -> createManagedContainer("player_module_" + sanitizeId(id), activitySearchMode));
    }

    private void onTabSwitch(TabsManager.Tab tab) {
        Container tabContainer = tab.getContainer();
        String moduleId = moduleIdsByTabName.get(tab.getName());

        setFilterVisibility(tabContainer == activityContainer, tabContainer == activityContainer || moduleId != null, tabContainer == activityContainer || moduleId != null);
        setActiveContainer(tabContainer);
        updatePositions();
        rebuildSidePanel();
    }

    private void setFilterVisibility(boolean sourceVisible, boolean eventVisible, boolean timeVisible) {
        sourceFilterVisible = sourceVisible;
        eventFilterVisible = eventVisible;
        timeFilterVisible = timeVisible;
        sourceFilterDropdown.setVisible(sourceVisible);
        eventFilterDropdown.setVisible(eventVisible);
        timeFilterDropdown.setVisible(timeVisible);
    }

    private void onActivitySearch(String query) {
        activitySearchQuery = normalizeQuery(query);
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab == null) {
            return;
        }
        if (activeTab.getContainer() == activityContainer || facetIdsByTabName.containsKey(activeTab.getName()) || moduleIdsByTabName.containsKey(activeTab.getName())) {
            rebuildActiveTabContent();
        }
    }

    private void onHistorySearch(String query) {
        historySearchQuery = normalizeQuery(query);
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab != null && activeTab.getContainer() == historyContainer) {
            rebuildHistory();
            rebuildSidePanel();
        }
    }

    private void onInventorySearch(String query) {
        inventorySearchQuery = normalizeQuery(query);
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab != null && activeTab.getContainer() == inventoryContainer) {
            rebuildInventory();
            rebuildSidePanel();
        }
    }

    private void onEnderChestSearch(String query) {
        enderChestSearchQuery = normalizeQuery(query);
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab != null && activeTab.getContainer() == enderChestContainer) {
            rebuildEnderChest();
            rebuildSidePanel();
        }
    }

    private void onEffectsSearch(String query) {
        effectsSearchQuery = normalizeQuery(query);
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab != null && activeTab.getContainer() == effectsContainer) {
            rebuildEffects();
            rebuildSidePanel();
        }
    }

    private void onStatsSearch(String query) {
        statsSearchQuery = normalizeQuery(query);
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab != null && activeTab.getContainer() == statsContainer) {
            rebuildStats();
            rebuildSidePanel();
        }
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private void rebuildActiveTabContent() {
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab == null) {
            return;
        }
        Container container = activeTab.getContainer();
        if (container == overviewContainer) {
            rebuildOverview();
        } else if (container == activityContainer) {
            rebuildActivity();
        } else if (container == historyContainer) {
            rebuildHistory();
        } else if (container == inventoryContainer) {
            rebuildInventory();
        } else if (container == enderChestContainer) {
            rebuildEnderChest();
        } else if (container == effectsContainer) {
            rebuildEffects();
        } else if (container == statsContainer) {
            rebuildStats();
        } else {
            String facetId = facetIdsByTabName.get(activeTab.getName());
            if (facetId != null) {
                rebuildSingleFacetTab(facetId);
                rebuildSidePanel();
                return;
            }
            String moduleId = moduleIdsByTabName.get(activeTab.getName());
            if (moduleId != null) {
                rebuildSingleModuleTab(moduleId);
            }
        }
        rebuildSidePanel();
    }

    private void updateFilterOptions() {
        List<String> sourceItems = collectModuleIds(dossier).stream().map(this::formatModule).toList();

        Set<String> eventValues = new LinkedHashSet<>();
        if (dossier != null) {
            for (PlayerEventRecord event : dossier.getRecentEvents()) {
                eventValues.add(formatToken(event.getCategory()));
            }
        }
        List<String> eventItems = new ArrayList<>(eventValues);
        eventItems.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> timeItems = List.of("Hour", "Day", "Week", "Older");

        updatingFilters = true;

        retainValidSelections(selectedSourceFilters, negativeSourceFilters, sourceItems);
        retainValidSelections(selectedEventFilters, negativeEventFilters, eventItems);
        retainValidSelections(selectedTimeFilters, negativeTimeFilters, timeItems);

        sourceFilterDropdown.setItems(sourceItems, null);
        sourceFilterDropdown.setSelectedItems(selectedSourceFilters, negativeSourceFilters);
        eventFilterDropdown.setItems(eventItems, null);
        eventFilterDropdown.setSelectedItems(selectedEventFilters, negativeEventFilters);
        timeFilterDropdown.setItems(timeItems, null);
        timeFilterDropdown.setSelectedItems(selectedTimeFilters, negativeTimeFilters);

        updatingFilters = false;
    }

    private void retainValidSelections(List<String> positive, List<String> negative, List<String> items) {
        positive.removeIf(item -> !containsIgnoreCase(items, item));
        negative.removeIf(item -> !containsIgnoreCase(items, item));
        negative.removeIf(item -> containsIgnoreCase(positive, item));
    }

    private void rebuildOverview() {
        float scroll = overviewContainer.getScrollOffset();
        overviewContainer.clearWidgets();
        overviewContainer.columns(2);
        overviewContainer.addWidget(createLabel("Player"));
        overviewContainer.addWidget(createLabel("Live"));
        overviewContainer.addWidget(createRow("Name", resolveDisplayName(), resolveIdentityLabel()));
        overviewContainer.addWidget(createRow("Source", resolveLiveSource(), resolveLiveSourceMeta()));
        overviewContainer.addWidget(createRow("Server", controller.getReSyncServerId(), compact(controller.getInstance().getName())));
        overviewContainer.addWidget(createRow("Status", resolveStatusText(), resolveStatusMeta()));
        overviewContainer.addWidget(createRow("UUID", player.getUuid().toString(), "Copy From Header"));
        overviewContainer.addWidget(createRow("Operator", player.isOp() ? "Enabled" : "Disabled", resolveBanMeta()));
        overviewContainer.addWidget(createRow("PlayTime", formatDuration(resolveTotalPlayTime()), resolveSessionMeta()));
        overviewContainer.addWidget(createRow("LastSeen", formatTimestamp(resolveLastSeen()), TimeUtils.timeSense(resolveLastSeen())));
        overviewContainer.addWidget(createRow("Activity", String.valueOf(dossier != null ? dossier.getRecentEvents().size() : 0), "Recent Events"));
        overviewContainer.addWidget(createRow("Sessions", String.valueOf(resolveSessionCount()), "Tracked History"));
        overviewContainer.addWidget(createRow("Modules", String.valueOf(collectModuleIds(dossier).size()), "Tracked Sources"));
        overviewContainer.addWidget(createRow("Data", String.valueOf(dossier != null ? dossier.getFacets().size() : 0), "Facet States"));

        if (liveData != null) {
            overviewContainer.addWidget(createRow("Health", formatNumber(liveData.health()), "Food: " + liveData.food()));
            overviewContainer.addWidget(createRow("XP", liveData.experienceLevel() + " | " + formatNumber(liveData.experienceProgress()), String.valueOf(liveData.totalExperience())));
            overviewContainer.addWidget(createRow("GameMode", compact(formatLabel(liveData.gameMode())), liveData.flying() ? "Flying" : liveData.fallFlying() ? "Gliding" : "Grounded"));
            if (liveData.location() != null) {
                overviewContainer.addWidget(createRow("Dimension", formatLabel(liveData.location().dimension()), "Live Position"));
                overviewContainer.addWidget(createRow("Location", formatCoord(liveData.location().x()) + ", " + formatCoord(liveData.location().y()) + ", " + formatCoord(liveData.location().z()), "XYZ"));
            } else {
                overviewContainer.addWidget(createRow("Dimension", "Unknown", "No Location"));
                overviewContainer.addWidget(createRow("Location", "Unknown", "No Location"));
            }
            overviewContainer.addWidget(createRow("Inventory", String.valueOf(countFilledSlots(liveData.inventory(), 0, 35)), String.valueOf(liveData.inventory().size()) + " Raw Slots"));
            overviewContainer.addWidget(createRow("Effects", String.valueOf(liveData.effects().size()), String.valueOf(liveData.flattenedStatistics().size()) + " Stats"));
        } else {
            overviewContainer.addWidget(createEmptyRow("Live Data", "Waiting for player data."));
        }

        overviewContainer.updateWidgetPositions();
        overviewContainer.setScrollOffset(scroll);
    }

    private void rebuildActivity() {
        float scroll = activityContainer.getScrollOffset();
        activityContainer.clearWidgets();
        activityContainer.columns(1);
        activityContainer.addWidget(createLabel(TAB_ACTIVITY));

        List<PlayerEventRecord> events = getFilteredEvents(null);
        if (events.isEmpty()) {
            activityContainer.addWidget(createEmptyRow(hasActiveActivityFilter() ? "No Match" : "No Activity", hasActiveActivityFilter() ? "No tracked activity matches current filters." : "No tracked activity is available yet."));
            activityContainer.updateWidgetPositions();
            activityContainer.setScrollOffset(scroll);
            return;
        }

        for (PlayerEventRecord event : events) {
            MountableButtonWidget row = new MountableButtonWidget.Builder(formatToken(event.getCategory()) + " / " + formatToken(event.getType()))
                .hiddenText(compact(formatModule(event.getModuleId())) + " | " + TimeUtils.timeSense(event.getTimestamp()))
                .description(buildEventDescription(event))
                .build();
            row.setHeight(34);
            row.entranceAnimationEnabled = false;
            activityContainer.addWidget(row);
        }

        activityContainer.updateWidgetPositions();
        activityContainer.setScrollOffset(scroll);
    }

    private void rebuildHistory() {
        float scroll = historyContainer.getScrollOffset();
        historyContainer.clearWidgets();
        historyContainer.columns(1);
        historyContainer.addWidget(createLabel(TAB_HISTORY));

        List<PlayerSessionRecord> sessions = getFilteredSessions();
        if (sessions.isEmpty()) {
            historyContainer.addWidget(createEmptyRow(historySearchQuery.isBlank() ? "No History" : "No Match", historySearchQuery.isBlank() ? "This player has no tracked session history yet." : "No sessions match the current search."));
            historyContainer.updateWidgetPositions();
            historyContainer.setScrollOffset(scroll);
            return;
        }

        for (PlayerSessionRecord session : sessions) {
            boolean active = isActiveSession(session);
            MountableButtonWidget row = new MountableButtonWidget.Builder(active ? "Active Session" : formatToken(session.getSource()))
                .hiddenText(compact(session.getSessionId()))
                .description(buildSessionDescription(session, active))
                .build();
            row.setHeight(34);
            row.entranceAnimationEnabled = false;
            historyContainer.addWidget(row);
        }

        historyContainer.updateWidgetPositions();
        historyContainer.setScrollOffset(scroll);
    }

    private void rebuildInventory() {
        inventoryContainer.layout(new ManagedLayout()).columns(1).padding(0).verticalSpacing(0).enableSelecting(false).scrolling(false).backgroundDrawing(false);
        inventoryContainer.clearWidgets();
        if (liveData == null) {
            inventoryContainer.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).enableSelecting(false).scrolling(true).backgroundDrawing(true);
            inventoryContainer.addWidget(createEmptyRow("No Data", "Waiting for inventory data."));
        } else {
            InventoryWidget widget = new InventoryWidget(0, 0, inventoryContainer.getEffectiveWidth(), inventoryContainer.getHeight(), player, controller, liveData, InventoryWidget.Mode.INVENTORY, this::canManipulateInventory, this::markInventoryInteraction, this::scheduleLiveDataRefresh);
            widget.setSearchQuery(inventorySearchQuery);
            inventoryContainer.addWidget(widget);
        }
        inventoryContainer.updateWidgetPositions();
    }

    private void rebuildEnderChest() {
        enderChestContainer.layout(new ManagedLayout()).columns(1).padding(0).verticalSpacing(0).enableSelecting(false).scrolling(false).backgroundDrawing(false);
        enderChestContainer.clearWidgets();
        if (liveData == null) {
            enderChestContainer.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).enableSelecting(false).scrolling(true).backgroundDrawing(true);
            enderChestContainer.addWidget(createEmptyRow("No Data", "Waiting for ender chest data."));
        } else {
            InventoryWidget widget = new InventoryWidget(0, 0, enderChestContainer.getEffectiveWidth(), enderChestContainer.getHeight(), player, controller, liveData, InventoryWidget.Mode.ENDER_CHEST, this::canManipulateInventory, this::markInventoryInteraction, this::scheduleLiveDataRefresh);
            widget.setSearchQuery(enderChestSearchQuery);
            enderChestContainer.addWidget(widget);
        }
        enderChestContainer.updateWidgetPositions();
    }

    private void rebuildEffects() {
        float scroll = effectsContainer.getScrollOffset();
        effectsContainer.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        effectsContainer.clearWidgets();
        effectsContainer.addWidget(createLabel(TAB_EFFECTS));
        if (liveData == null) {
            effectsContainer.addWidget(createEmptyRow("No Data", "Waiting for effect data."));
        } else {
            List<PlayerEffect> effects = getFilteredEffects();
            if (effects.isEmpty()) {
                effectsContainer.addWidget(createEmptyRow(effectsSearchQuery.isBlank() ? "No Effects" : "No Match", effectsSearchQuery.isBlank() ? "This player has no active effects." : "No effects match the current search."));
            } else {
                for (PlayerEffect effect : effects) {
                    effectsContainer.addWidget(buildEffectRow(effect));
                }
            }
        }
        effectsContainer.updateWidgetPositions();
        effectsContainer.setScrollOffset(scroll);
    }

    private void rebuildStats() {
        float scroll = statsContainer.getScrollOffset();
        statsContainer.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(4).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        statsContainer.clearWidgets();
        statsContainer.addWidget(createLabel(TAB_STATS));
        cachedStats = new ArrayList<>();
        filteredStats = new ArrayList<>();
        if (liveData == null) {
            statsContainer.addWidget(createEmptyRow("No Data", "Waiting for stats data."));
            statsContainer.updateWidgetPositions();
            statsContainer.setScrollOffset(scroll);
            return;
        }
        for (PlayerStatistic stat : liveData.flattenedStatistics()) {
            if (stat == null || isDataVersionStat(stat.key())) {
                continue;
            }
            cachedStats.add(stat);
        }
        cachedStats.sort(Comparator.comparing(PlayerStatistic::key, String.CASE_INSENSITIVE_ORDER));
        applyStatsFilter();
        statsContainer.setScrollOffset(scroll);
    }

    private void applyStatsFilter() {
        float scroll = statsContainer.getScrollOffset();
        statsContainer.clearWidgets();
        statsContainer.addWidget(createLabel(TAB_STATS));

        if (cachedStats.isEmpty()) {
            statsContainer.addWidget(createEmptyRow("No Stats", "No statistics are available for this player."));
            statsContainer.updateWidgetPositions();
            statsContainer.setScrollOffset(scroll);
            return;
        }

        filteredStats = new ArrayList<>();
        String query = statsSearchQuery;
        if (query.isBlank()) {
            filteredStats.addAll(cachedStats);
        } else {
            for (PlayerStatistic stat : cachedStats) {
                String label = formatStatKey(stat.key());
                if (matchesSearch(label, query)) {
                    filteredStats.add(stat);
                }
            }
        }

        if (filteredStats.isEmpty()) {
            statsContainer.addWidget(createEmptyRow("No Match", "No statistics match the current search."));
            statsContainer.updateWidgetPositions();
            statsContainer.setScrollOffset(scroll);
            return;
        }

        String previousCategory = null;
        for (PlayerStatistic stat : filteredStats) {
            String category = extractCategory(stat.key());
            if (!Objects.equals(previousCategory, category)) {
                statsContainer.addWidget(buildStatsCategoryRow(category));
                previousCategory = category;
            }
            statsContainer.addWidget(buildStatRow(stat));
        }

        statsContainer.updateWidgetPositions();
        statsContainer.setScrollOffset(scroll);
    }

    private void rebuildModuleTabs() {
        for (String moduleId : collectModuleIds(dossier)) {
            rebuildSingleModuleTab(moduleId);
        }
    }

    private void rebuildFacetTabs() {
        for (PlayerFacetState facet : collectTabFacets(dossier)) {
            rebuildSingleFacetTab(facet.getFacetId());
        }
    }

    private void rebuildSingleFacetTab(String facetId) {
        PlayerFacetState facet = findFacet(facetId);
        Container container = getOrCreateFacetContainer(facetId);
        float scroll = container.getScrollOffset();
        container.clearWidgets();
        container.columns(1);
        container.addWidget(createLabel(facet != null ? resolveFacetTitle(facet) : formatToken(facetId)));
        if (facet == null) {
            container.addWidget(createEmptyRow("No Data", "This facet is not available."));
            container.updateWidgetPositions();
            container.setScrollOffset(scroll);
            return;
        }
        addFacetSummary(container, facet);
        List<Map<String, Object>> sections = mapList(facet.getData().get("sections"));
        if (sections.isEmpty()) {
            container.addWidget(createRow(formatToken(facet.getFacetId()), compactMap(facet.getData()), TimeUtils.timeSense(facet.getUpdatedAt())));
        } else {
            for (Map<String, Object> section : sections) {
                String title = textValue(section.get("title"));
                List<Map<String, Object>> rows = filteredFacetRows(section);
                if (rows.isEmpty()) {
                    continue;
                }
                container.addWidget(createLabel(title.isBlank() ? "Data" : title));
                for (Map<String, Object> rowData : rows) {
                    container.addWidget(createFacetRow(rowData));
                }
            }
        }
        container.updateWidgetPositions();
        container.setScrollOffset(scroll);
    }

    private void rebuildSingleModuleTab(String moduleId) {
        Container container = getOrCreateModuleContainer(moduleId);
        float scroll = container.getScrollOffset();
        container.clearWidgets();
        container.columns(1);
        container.addWidget(createLabel(formatModule(moduleId)));

        List<PlayerFacetState> facets = getFilteredModuleData(moduleId);
        List<PlayerEventRecord> events = getFilteredEvents(moduleId);

        if (!facets.isEmpty()) {
            container.addWidget(createLabel("Data"));
            for (PlayerFacetState facet : facets) {
                MountableButtonWidget row = new MountableButtonWidget.Builder(formatToken(facet.getFacetId()))
                    .hiddenText(TimeUtils.timeSense(facet.getUpdatedAt()))
                    .description(compactMap(facet.getData()))
                    .build();
                row.setHeight(34);
                row.entranceAnimationEnabled = false;
                container.addWidget(row);
            }
        }

        if (!events.isEmpty()) {
            container.addWidget(createLabel(TAB_ACTIVITY));
            for (PlayerEventRecord event : events) {
                MountableButtonWidget row = new MountableButtonWidget.Builder(formatToken(event.getCategory()) + " / " + formatToken(event.getType()))
                    .hiddenText(TimeUtils.timeSense(event.getTimestamp()))
                    .description(buildEventDescription(event))
                    .build();
                row.setHeight(34);
                row.entranceAnimationEnabled = false;
                container.addWidget(row);
            }
        }

        if (facets.isEmpty() && events.isEmpty()) {
            container.addWidget(createEmptyRow(hasActiveActivityFilter() ? "No Match" : "No Data", hasActiveActivityFilter() ? "Nothing matches the current filters." : "This source has not provided player data yet."));
        }

        container.updateWidgetPositions();
        container.setScrollOffset(scroll);
    }

    private void rebuildSidePanel() {
        if (detailsPanel == null) {
            return;
        }
        Container panel = detailsPanel.container();
        float scroll = panel.getScrollOffset();
        panel.clearWidgets();
        panel.columns(1);

        panel.addWidget(createLabel("Actions"));
        panel.addWidget(createActionRow("Refresh", "Request fresh dossier and live data.", "reload.png", this::refreshAllData, true, ThemeManager.getAccent("nice")));
        panel.addWidget(createActionRow("Kick", player.isOnline() ? "Disconnect this player." : "Player is offline.", "delete.png", () -> controller.kickPlayer(player, "Kicked by operator"), controller.isServerRunning() && player.isOnline(), ThemeManager.getAccent("danger")));

        boolean banned = player.getBan().getValue() != null;
        panel.addWidget(createActionRow(banned ? "Unban" : "Ban", banned ? compact(resolveBanMeta()) : "Open ban dialog.", banned ? "heart.png" : "close.png", () -> {
            if (player.getBan().getValue() != null) {
                controller.unbanPlayer(player);
            } else {
                new BanPlayerPopup(this, player, controller);
            }
        }, controller.isServerRunning(), banned ? ThemeManager.getAccent("nice") : ThemeManager.getAccent("danger")));
        panel.addWidget(createActionRow(player.isOp() ? "De-Op" : "Op", player.isOp() ? "Remove operator privileges." : "Grant operator privileges.", player.isOp() ? "deop.png" : "op.png", () -> controller.toggleOp(player), controller.isServerRunning(), ThemeManager.getAccent("calm")));

        List<PlayerAction> actions = controller.getPlayerActions();
        if (!actions.isEmpty()) {
            panel.addWidget(createLabel("Custom"));
            for (PlayerAction action : actions) {
                panel.addWidget(createActionRow(action.name, compact(action.command), action.icon, () -> executePlayerAction(action), controller.isServerRunning(), ThemeManager.getAccent("calm")));
            }
        }

        String activeTab = resolveActiveTabName();
        panel.addWidget(createLabel(activeTab));
        switch (activeTab) {
            case TAB_OVERVIEW -> populateOverviewPanel(panel);
            case TAB_ACTIVITY -> populateActivityPanel(panel);
            case TAB_HISTORY -> populateHistoryPanel(panel);
            case TAB_INVENTORY -> populateInventoryPanel(panel);
            case TAB_ENDER_CHEST -> populateEnderChestPanel(panel);
            case TAB_EFFECTS -> populateEffectsPanel(panel);
            case TAB_STATS -> populateStatsPanel(panel);
            default -> {
                String facetId = facetIdsByTabName.get(activeTab);
                if (facetId != null) {
                    populateFacetPanel(panel, facetId);
                } else {
                    populateModulePanel(panel, moduleIdsByTabName.get(activeTab));
                }
            }
        }

        panel.updateWidgetPositions();
        panel.setScrollOffset(scroll);
    }

    private void populateOverviewPanel(Container panel) {
        panel.addWidget(createRow("Status", resolveStatusText(), resolveStatusMeta()));
        panel.addWidget(createRow("Tracking", String.valueOf(dossier != null ? dossier.getRecentEvents().size() : 0) + " Activity", String.valueOf(resolveSessionCount()) + " Sessions"));
        panel.addWidget(createRow("Live Source", resolveLiveSource(), resolveLiveSourceMeta()));
        if (liveData != null) {
            panel.addWidget(createRow("Vitals", formatNumber(liveData.health()) + " HP", "Food: " + liveData.food()));
            panel.addWidget(createRow("Inventory", String.valueOf(countFilledSlots(liveData.inventory(), 0, 35)) + " Filled", canManipulateInventory() ? "Editable" : "Read Only"));
            panel.addWidget(createRow("Effects", String.valueOf(liveData.effects().size()), String.valueOf(liveData.flattenedStatistics().size()) + " Stats"));
        } else {
            panel.addWidget(createEmptyRow("Live Data", "Waiting for player data."));
        }
    }

    private void populateActivityPanel(Container panel) {
        List<PlayerEventRecord> allEvents = dossier != null ? dossier.getRecentEvents() : List.of();
        List<PlayerEventRecord> filteredEvents = getFilteredEvents(null);
        Set<String> modules = new LinkedHashSet<>();
        Set<String> categories = new LinkedHashSet<>();
        for (PlayerEventRecord event : filteredEvents) {
            modules.add(formatModule(event.getModuleId()));
            categories.add(formatToken(event.getCategory()));
        }
        panel.addWidget(createRow("Results", filteredEvents.size() + " Match", allEvents.size() + " Total"));
        panel.addWidget(createRow("Filters", formatVisibleFilterSummary(null), activitySearchQuery.isBlank() ? "No Search" : compact(activitySearchQuery)));
        panel.addWidget(createRow("Sources", String.valueOf(modules.size()), String.valueOf(categories.size()) + " Categories"));
        if (!filteredEvents.isEmpty()) {
            PlayerEventRecord latest = filteredEvents.getFirst();
            panel.addWidget(createRow("Latest", formatToken(latest.getCategory()) + " / " + formatToken(latest.getType()), TimeUtils.timeSense(latest.getTimestamp())));
        } else {
            panel.addWidget(createEmptyRow("Latest", "No matching activity."));
        }
    }

    private void populateHistoryPanel(Container panel) {
        List<PlayerSessionRecord> sessions = getFilteredSessions();
        panel.addWidget(createRow("Search", historySearchQuery.isBlank() ? "Any" : compact(historySearchQuery), sessions.size() + " Match"));
        panel.addWidget(createRow("Sessions", String.valueOf(collectSessions().size()), formatDuration(resolveTotalPlayTime())));
        if (dossier != null && dossier.getActiveSession() != null) {
            PlayerSessionRecord active = dossier.getActiveSession();
            panel.addWidget(createRow("Active", formatToken(active.getSource()), formatDuration(Math.max(0L, System.currentTimeMillis() - active.getStartedAt()))));
        } else {
            panel.addWidget(createRow("Active", "None", "No Live Session"));
        }
        PlayerSessionRecord latestEnded = null;
        for (PlayerSessionRecord session : collectSessions()) {
            if (!isActiveSession(session) && (latestEnded == null || session.getEndedAt() > latestEnded.getEndedAt())) {
                latestEnded = session;
            }
        }
        if (latestEnded != null) {
            panel.addWidget(createRow("Latest End", formatTimestamp(latestEnded.getEndedAt()), formatDuration(latestEnded.getDurationMs())));
        } else {
            panel.addWidget(createEmptyRow("Latest End", "No completed session yet."));
        }
    }

    private void populateInventoryPanel(Container panel) {
        panel.addWidget(createRow("Search", inventorySearchQuery.isBlank() ? "Any" : compact(inventorySearchQuery), canManipulateInventory() ? "Editable" : "Read Only"));
        panel.addWidget(createRow("Source", resolveLiveSource(), canManipulateInventory() ? "RCON" : "No RCON"));
        if (liveData == null) {
            panel.addWidget(createEmptyRow("Inventory", "Waiting for inventory data."));
            return;
        }
        panel.addWidget(createRow("Main", countFilledSlots(liveData.inventory(), 9, 35) + " Filled", "27 Slots"));
        panel.addWidget(createRow("Hotbar", countFilledSlots(liveData.inventory(), 0, 8) + " Filled", "9 Slots"));
        panel.addWidget(createRow("Armor", String.valueOf(liveData.armor().size()) + " Items", liveData.offhand().isEmpty() ? "No Offhand" : formatLabel(liveData.offhand().getFirst().id())));
        panel.addWidget(createRow("Editing", canManipulateInventory() ? "Drag And Drop" : "Disabled", canManipulateInventory() ? "Left, Right, Middle Click" : "Requires RCON Source"));
    }

    private void populateEnderChestPanel(Container panel) {
        panel.addWidget(createRow("Search", enderChestSearchQuery.isBlank() ? "Any" : compact(enderChestSearchQuery), canManipulateInventory() ? "Editable" : "Read Only"));
        panel.addWidget(createRow("Source", resolveLiveSource(), canManipulateInventory() ? "RCON" : "No RCON"));
        if (liveData == null) {
            panel.addWidget(createEmptyRow("Ender Chest", "Waiting for ender chest data."));
            return;
        }
        int enderFilled = liveData.enderChest() != null && liveData.enderChest().items() != null ? countFilledSlots(liveData.enderChest().items(), 0, 26) : 0;
        panel.addWidget(createRow("Chest", enderFilled + " Filled", "27 Slots"));
        panel.addWidget(createRow("Player Inv", countFilledSlots(liveData.inventory(), 9, 35) + " Filled", countFilledSlots(liveData.inventory(), 0, 8) + " Hotbar"));
        panel.addWidget(createRow("Editing", canManipulateInventory() ? "Drag And Drop" : "Disabled", canManipulateInventory() ? "Chest And Inventory" : "Requires RCON Source"));
    }

    private void populateEffectsPanel(Container panel) {
        panel.addWidget(createRow("Search", effectsSearchQuery.isBlank() ? "Any" : compact(effectsSearchQuery), String.valueOf(getFilteredEffects().size()) + " Match"));
        if (liveData == null) {
            panel.addWidget(createEmptyRow("Effects", "Waiting for effect data."));
            return;
        }
        int ambient = 0;
        int hiddenParticles = 0;
        int hiddenIcons = 0;
        PlayerEffect strongest = null;
        for (PlayerEffect effect : getFilteredEffects()) {
            if (effect.ambient()) {
                ambient++;
            }
            if (!effect.showParticles()) {
                hiddenParticles++;
            }
            if (!effect.showIcon()) {
                hiddenIcons++;
            }
            if (strongest == null || effect.amplifier() > strongest.amplifier()) {
                strongest = effect;
            }
        }
        panel.addWidget(createRow("Active", String.valueOf(liveData.effects().size()), ambient + " Ambient"));
        panel.addWidget(createRow("Hidden", hiddenParticles + " Particles", hiddenIcons + " Icons"));
        if (strongest != null) {
            panel.addWidget(createRow("Strongest", formatLabel(strongest.id()), "Lv " + (strongest.amplifier() + 1)));
        } else {
            panel.addWidget(createEmptyRow("Strongest", "No matching effects."));
        }
    }

    private void populateStatsPanel(Container panel) {
        panel.addWidget(createRow("Search", statsSearchQuery.isBlank() ? "Any" : compact(statsSearchQuery), cachedStats.isEmpty() ? "No Data" : filteredStats.size() + " Match"));
        if (cachedStats.isEmpty()) {
            panel.addWidget(createEmptyRow("Stats", "Waiting for stats data."));
            return;
        }
        Set<String> categories = new LinkedHashSet<>();
        PlayerStatistic top = null;
        for (PlayerStatistic stat : filteredStats) {
            categories.add(extractCategory(stat.key()));
            if (top == null || stat.value() > top.value()) {
                top = stat;
            }
        }
        panel.addWidget(createRow("Visible", String.valueOf(filteredStats.size()), String.valueOf(cachedStats.size()) + " Total"));
        panel.addWidget(createRow("Categories", String.valueOf(categories.size()), statsSearchQuery.isBlank() ? "All" : "Filtered"));
        if (top != null) {
            panel.addWidget(createRow("Top", formatStatKey(top.key()), String.valueOf(top.value())));
        } else {
            panel.addWidget(createEmptyRow("Top", "No matching stats."));
        }
    }

    private void populateModulePanel(Container panel, String moduleId) {
        if (moduleId == null) {
            panel.addWidget(createEmptyRow("Module", "No module context is active."));
            return;
        }
        List<PlayerFacetState> facets = getFilteredModuleData(moduleId);
        List<PlayerEventRecord> events = getFilteredEvents(moduleId);
        panel.addWidget(createRow("Search", activitySearchQuery.isBlank() ? "Any" : compact(activitySearchQuery), "Module Scope"));
        panel.addWidget(createRow("Data", facets.size() + " Match", collectModuleData(moduleId).size() + " Total"));
        panel.addWidget(createRow("Events", events.size() + " Match", formatVisibleFilterSummary(moduleId)));
        PlayerFacetState latestFacet = null;
        for (PlayerFacetState facet : collectModuleData(moduleId)) {
            if (latestFacet == null || facet.getUpdatedAt() > latestFacet.getUpdatedAt()) {
                latestFacet = facet;
            }
        }
        if (latestFacet != null) {
            panel.addWidget(createRow("Latest Data", formatToken(latestFacet.getFacetId()), TimeUtils.timeSense(latestFacet.getUpdatedAt())));
        } else {
            panel.addWidget(createEmptyRow("Latest Data", "No facet data for this module."));
        }
    }

    private void populateFacetPanel(Container panel, String facetId) {
        PlayerFacetState facet = findFacet(facetId);
        if (facet == null) {
            panel.addWidget(createEmptyRow("Data", "No facet data is available."));
            return;
        }
        List<Map<String, Object>> sections = mapList(facet.getData().get("sections"));
        int rowCount = 0;
        for (Map<String, Object> section : sections) {
            rowCount += filteredFacetRows(section).size();
        }
        panel.addWidget(createRow("Search", activitySearchQuery.isBlank() ? "Any" : compact(activitySearchQuery), "Dynamic Scope"));
        panel.addWidget(createRow("Updated", TimeUtils.timeSense(facet.getUpdatedAt()), formatModule(facet.getModuleId())));
        panel.addWidget(createRow("Sections", String.valueOf(sections.size()), rowCount + " Rows"));
        if (sections.isEmpty()) {
            panel.addWidget(createRow("Data", compactMap(facet.getData()), formatToken(facet.getFacetId())));
        }
    }

    private AnimatedButton createLabel(String text) {
        AnimatedButton label = new AnimatedButton.Builder().label(text).centered(false).build();
        label.setActive(false);
        label.entranceAnimationEnabled = false;
        return label;
    }

    private MountableButtonWidget createRow(String title, String value, String meta) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(value).hiddenText(meta).build();
        row.setHeight(30);
        row.entranceAnimationEnabled = false;
        return row;
    }

    private MountableButtonWidget createEmptyRow(String title, String description) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(description).build();
        row.setHeight(34);
        row.entranceAnimationEnabled = false;
        row.setActive(false);
        return row;
    }

    private MountableButtonWidget createActionRow(String title, String description, String iconPath, Runnable action, boolean active, Accent accent) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title)
            .description(description)
            .iconPath(iconPath)
            .onClick(action)
            .build();
        row.setHeight(30);
        row.entranceAnimationEnabled = false;
        row.setActive(active);
        if (accent != null) {
            row.accentType = accent;
        }
        return row;
    }

    private void refreshAllData() {
        refreshPlayerData(true);
        requestLivePlayerData(true);
    }

    private void refreshPlayerData(boolean notify) {
        controller.requestPlayerDossier(player.getUuid());
        if (notify) {
            new Notification("Player Management", "Requested fresh player data.", Notification.Type.INFO);
        }
    }

    private void refreshLiveDataIfDue() {
        long now = System.currentTimeMillis();
        if (scheduledForcedPlayerDataRefreshAtMs > 0L && now >= scheduledForcedPlayerDataRefreshAtMs) {
            scheduledForcedPlayerDataRefreshAtMs = -1L;
            requestLivePlayerData(true);
            return;
        }
        if (now >= nextPlayerDataRefreshAtMs) {
            requestLivePlayerData(false);
        }
    }

    private void requestLivePlayerData(boolean immediate) {
        if (controller == null || player == null) {
            return;
        }
        PlayerDataManager dataManager = controller.getPlayerDataManager();
        if (dataManager == null) {
            return;
        }
        nextPlayerDataRefreshAtMs = System.currentTimeMillis() + playerDataRefreshIntervalMs;
        long minInterval = immediate ? 0L : playerDataRefreshIntervalMs;
        dataManager.refreshIfDue(player.getUuid(), player.getName(), player.isOnline(), minInterval).thenAccept(data -> {
            if (data == null) {
                return;
            }
            String nextSource = dataManager.getSource(player.getUuid());
            if (System.currentTimeMillis() < interactionLockUntilMs || isInventoryInteractionActive()) {
                return;
            }
            if (isSameData(data, liveData) && Objects.equals(nextSource, liveDataSource)) {
                return;
            }
            ScreenManager.getInstance().execute(() -> {
                liveData = data;
                liveDataSource = nextSource;
                rebuildOverview();
                rebuildInventory();
                rebuildEnderChest();
                rebuildEffects();
                rebuildStats();
                rebuildSidePanel();
            });
        });
    }

    private long resolvePlayerDataRefreshInterval() {
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

    private void scheduleLiveDataRefresh(long delayMs) {
        scheduledForcedPlayerDataRefreshAtMs = Math.max(scheduledForcedPlayerDataRefreshAtMs, System.currentTimeMillis() + Math.max(0L, delayMs));
    }

    private boolean isInventoryInteractionActive() {
        TabsManager.Tab activeTab = tabs().getActiveTab();
        if (activeTab == null) {
            return false;
        }
        Container container = activeTab.getContainer();
        if (container == null || container.getWidgets().isEmpty()) {
            return false;
        }
        AnimatedWidget widget = container.getWidgets().getFirst();
        return widget instanceof InventoryWidget inventoryWidget && inventoryWidget.isInteractionActive();
    }

    private void markInventoryInteraction() {
        interactionLockUntilMs = System.currentTimeMillis() + 400L;
    }

    private void copyUuid() {
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            RemotelyClient.INSTANCE.getHost().setClipboard(player.getUuid().toString());
            new Notification("Copied UUID", player.getUuid().toString(), Notification.Type.SUCCESS);
        }
    }

    private void requestFace() {
        if (faceRequested) {
            return;
        }
        faceRequested = true;
        Account account = new Account(resolveDisplayName(), player.getUuid().toString(), null, 0);
        account.getFaceIdAsync().thenAccept(faceId -> {
            if (faceId != null) {
                playerFace = faceId;
                updateHeaderButton();
            }
        });
    }

    private void executePlayerAction(PlayerAction action) {
        List<String> variables = findCustomVariables(action.command);
        if (variables.isEmpty()) {
            controller.runCustomCommand(player, action.command);
            return;
        }
        showVariableInputPopup(action, variables);
    }

    private List<String> findCustomVariables(String command) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\$([a-zA-Z0-9_]+)").matcher(command);
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (!"name".equalsIgnoreCase(variable) && !"uuid".equalsIgnoreCase(variable) && !variables.contains(variable)) {
                variables.add(variable);
            }
        }
        return variables;
    }

    private void showVariableInputPopup(PlayerAction action, List<String> variables) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Execute: " + action.name)
            .size(300, 60 + variables.size() * 30)
            .setAntiOutOfBound(true)
            .setResizable(true);

        Map<String, TextInputWidget> inputs = new HashMap<>();
        Runnable execute = () -> {
            String command = action.command;
            for (Map.Entry<String, TextInputWidget> entry : inputs.entrySet()) {
                command = command.replace("$" + entry.getKey(), entry.getValue().getText());
            }
            controller.runCustomCommand(player, command);
            builder.getWidget().setVisible(false);
        };

        for (String variable : variables) {
            TextInputWidget input = new TextInputWidget.Builder().placeholder("Enter value for $" + variable).build();
            inputs.put(variable, input);
            input.onEnter = () -> {
                int index = variables.indexOf(variable);
                if (index >= 0 && index < variables.size() - 1) {
                    TextInputWidget nextWidget = inputs.get(variables.get(index + 1));
                    ((PopupWidget) builder.getWidget()).setFocusedWidget(nextWidget);
                } else {
                    execute.run();
                }
            };
            builder.addRow("", true, 20, input);
        }
        builder.addTitleButton(execute, "Execute", ThemeManager.getAccent("nice"));
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private List<String> collectModuleIds(PlayerDossier currentDossier) {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> tabModules = new LinkedHashSet<>();
        if (currentDossier != null) {
            for (PlayerFacetState facet : currentDossier.getFacets().values()) {
                if (isTabFacet(facet) && facet.getModuleId() != null && !facet.getModuleId().isBlank()) {
                    tabModules.add(facet.getModuleId());
                }
            }
            for (PlayerFacetState facet : currentDossier.getFacets().values()) {
                if (!isTabFacet(facet) && facet.getModuleId() != null && !facet.getModuleId().isBlank()) {
                    ids.add(facet.getModuleId());
                }
            }
            for (PlayerEventRecord event : currentDossier.getRecentEvents()) {
                if (event.getModuleId() != null && !event.getModuleId().isBlank() && !tabModules.contains(event.getModuleId())) {
                    ids.add(event.getModuleId());
                }
            }
        }
        return ids.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<PlayerFacetState> collectTabFacets(PlayerDossier currentDossier) {
        List<PlayerFacetState> facets = new ArrayList<>();
        if (currentDossier == null) {
            return facets;
        }
        for (PlayerFacetState facet : currentDossier.getFacets().values()) {
            if (isTabFacet(facet)) {
                facets.add(facet);
            }
        }
        facets.sort(Comparator.comparingInt(this::resolveFacetPriority).thenComparing(this::resolveFacetTabName, String.CASE_INSENSITIVE_ORDER));
        return facets;
    }

    private List<PlayerFacetState> collectModuleData(String moduleId) {
        List<PlayerFacetState> list = new ArrayList<>();
        if (dossier == null) {
            return list;
        }
        for (PlayerFacetState facet : dossier.getFacets().values()) {
            if (!isTabFacet(facet) && Objects.equals(facet.getModuleId(), moduleId)) {
                list.add(facet);
            }
        }
        list.sort(Comparator.comparing(PlayerFacetState::getFacetId, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private PlayerFacetState findFacet(String facetId) {
        if (dossier == null) {
            return null;
        }
        return dossier.getFacets().get(facetId);
    }

    private boolean isTabFacet(PlayerFacetState facet) {
        return facet != null && facet.getMetadata() != null && facet.getMetadata().isTab();
    }

    private int resolveFacetPriority(PlayerFacetState facet) {
        PlayerFacetMetadata metadata = facet != null ? facet.getMetadata() : null;
        return metadata != null ? metadata.getPriority() : 0;
    }

    private String resolveFacetTabName(PlayerFacetState facet) {
        PlayerFacetMetadata metadata = facet != null ? facet.getMetadata() : null;
        if (metadata != null && metadata.getTabName() != null && !metadata.getTabName().isBlank()) {
            return metadata.getTabName();
        }
        return resolveFacetTitle(facet);
    }

    private String resolveFacetTitle(PlayerFacetState facet) {
        PlayerFacetMetadata metadata = facet != null ? facet.getMetadata() : null;
        if (metadata != null && metadata.getTitle() != null && !metadata.getTitle().isBlank()) {
            return metadata.getTitle();
        }
        return facet != null ? formatToken(facet.getFacetId()) : "Data";
    }

    private List<Map<String, Object>> mapList(Object source) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(source instanceof List<?> list)) {
            return rows;
        }
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                Map<String, Object> data = new LinkedHashMap<>();
                for (Map.Entry<?, ?> value : map.entrySet()) {
                    if (value.getKey() != null) {
                        data.put(String.valueOf(value.getKey()), value.getValue());
                    }
                }
                rows.add(data);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> filteredFacetRows(Map<String, Object> section) {
        List<Map<String, Object>> rows = mapList(section.get("rows"));
        if (activitySearchQuery.isBlank()) {
            return rows;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        String query = activitySearchQuery.toLowerCase(Locale.ROOT);
        for (Map<String, Object> row : rows) {
            if (matchesSearch(compactMap(row).toLowerCase(Locale.ROOT), query)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private void addFacetSummary(Container container, PlayerFacetState facet) {
        for (Map<String, Object> row : mapList(facet.getData().get("summary"))) {
            container.addWidget(createFacetRow(row));
        }
    }

    private MountableButtonWidget createFacetRow(Map<String, Object> rowData) {
        String title = firstText(rowData, "title", "label", "name", "id");
        String value = firstText(rowData, "value", "description", "text", "status");
        String meta = firstText(rowData, "meta", "hiddenText", "subtitle");
        MountableButtonWidget row = new MountableButtonWidget.Builder(title.isBlank() ? "Data" : title)
            .description(value.isBlank() ? compactMap(rowData) : compact(value))
            .hiddenText(compact(meta))
            .build();
        row.setHeight(34);
        row.entranceAnimationEnabled = false;
        return row;
    }

    private String firstText(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            String value = textValue(data.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private List<PlayerFacetState> getFilteredModuleData(String moduleId) {
        List<PlayerFacetState> list = new ArrayList<>();
        for (PlayerFacetState facet : collectModuleData(moduleId)) {
            if (activitySearchQuery.isBlank()) {
                list.add(facet);
                continue;
            }
            String haystack = (formatToken(facet.getFacetId()) + ' ' + compactMap(facet.getData())).toLowerCase(Locale.ROOT);
            if (matchesSearch(haystack, activitySearchQuery.toLowerCase(Locale.ROOT))) {
                list.add(facet);
            }
        }
        return list;
    }

    private List<PlayerEventRecord> getFilteredEvents(String moduleId) {
        List<PlayerEventRecord> list = new ArrayList<>();
        if (dossier == null) {
            return list;
        }
        long now = System.currentTimeMillis();
        String loweredQuery = activitySearchQuery.toLowerCase(Locale.ROOT);
        for (PlayerEventRecord event : dossier.getRecentEvents()) {
            if (moduleId != null && !Objects.equals(event.getModuleId(), moduleId)) {
                continue;
            }
            String sourceLabel = formatModule(event.getModuleId());
            if (moduleId == null && !matchesFilter(sourceLabel, selectedSourceFilters, negativeSourceFilters)) {
                continue;
            }
            String eventTypeLabel = formatToken(event.getCategory());
            if (!matchesFilter(eventTypeLabel, selectedEventFilters, negativeEventFilters)) {
                continue;
            }
            String timeBucket = resolveTimeBucket(now, event.getTimestamp());
            if (!matchesFilter(timeBucket, selectedTimeFilters, negativeTimeFilters)) {
                continue;
            }
            if (!loweredQuery.isBlank()) {
                String haystack = (formatToken(event.getCategory()) + ' ' + formatToken(event.getType()) + ' ' + formatModule(event.getModuleId()) + ' ' + compactMap(event.getData())).toLowerCase(Locale.ROOT);
                if (!matchesSearch(haystack, loweredQuery)) {
                    continue;
                }
            }
            list.add(event);
        }
        return list;
    }

    private List<PlayerSessionRecord> getFilteredSessions() {
        List<PlayerSessionRecord> filtered = new ArrayList<>();
        for (PlayerSessionRecord session : collectSessions()) {
            if (historySearchQuery.isBlank()) {
                filtered.add(session);
                continue;
            }
            String haystack = (compact(session.getSessionId()) + ' ' + formatToken(session.getSource()) + ' ' + buildSessionDescription(session, isActiveSession(session))).toLowerCase(Locale.ROOT);
            if (matchesSearch(haystack, historySearchQuery.toLowerCase(Locale.ROOT))) {
                filtered.add(session);
            }
        }
        return filtered;
    }

    private List<PlayerEffect> getFilteredEffects() {
        List<PlayerEffect> effects = new ArrayList<>();
        if (liveData == null) {
            return effects;
        }
        List<PlayerEffect> source = new ArrayList<>(liveData.effects());
        source.sort(Comparator.comparing(PlayerEffect::id, String.CASE_INSENSITIVE_ORDER));
        for (PlayerEffect effect : source) {
            if (effectsSearchQuery.isBlank()) {
                effects.add(effect);
                continue;
            }
            String haystack = (formatLabel(effect.id()) + ' ' + effect.id() + ' ' + effect.amplifier() + ' ' + effect.duration()).toLowerCase(Locale.ROOT);
            if (matchesSearch(haystack, effectsSearchQuery.toLowerCase(Locale.ROOT))) {
                effects.add(effect);
            }
        }
        return effects;
    }

    private boolean matchesSearch(String haystack, String query) {
        return haystack.contains(query) || SearchUtils.isFuzzyMatch(haystack, query);
    }

    private boolean matchesFilter(String label, List<String> positive, List<String> negative) {
        boolean positiveMatch = positive.isEmpty() || containsIgnoreCase(positive, label);
        boolean negativeMatch = containsIgnoreCase(negative, label);
        return positiveMatch && !negativeMatch;
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (Objects.equals(value, target) || value != null && target != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private String resolveTimeBucket(long now, long timestamp) {
        if (timestamp <= 0L) {
            return "Older";
        }
        long age = Math.max(0L, now - timestamp);
        if (age <= 60L * 60L * 1000L) {
            return "Hour";
        }
        if (age <= 24L * 60L * 60L * 1000L) {
            return "Day";
        }
        if (age <= 7L * 24L * 60L * 60L * 1000L) {
            return "Week";
        }
        return "Older";
    }

    private List<PlayerSessionRecord> collectSessions() {
        List<PlayerSessionRecord> sessions = new ArrayList<>();
        if (dossier != null && dossier.getActiveSession() != null) {
            sessions.add(dossier.getActiveSession());
        }
        if (dossier != null) {
            sessions.addAll(dossier.getSessions());
        }
        return sessions;
    }

    private String resolveActiveTabName() {
        TabsManager.Tab active = tabs().getActiveTab();
        return active != null ? active.getName() : TAB_OVERVIEW;
    }

    private String buildDossierSignature(PlayerDossier currentDossier) {
        BanInfo ban = player.getBan().getValue();
        StringBuilder builder = new StringBuilder();
        builder.append(compact(player.getName())).append('|')
            .append(player.isOnline()).append('|')
            .append(player.isOp()).append('|')
            .append(player.getPingValue()).append('|')
            .append(compact(ban != null ? ban.reason() : "")).append('|');
        if (currentDossier == null) {
            return builder.toString();
        }
        builder.append(currentDossier.getLastSeenAt()).append('|')
            .append(currentDossier.getTotalPlayTimeMs()).append('|')
            .append(compact(currentDossier.getPlayerName())).append('|')
            .append(compact(currentDossier.getPlayerId())).append('|')
            .append(currentDossier.isOnline()).append('|')
            .append(currentDossier.getRecentEvents().size()).append('|')
            .append(currentDossier.getSessions().size()).append('|')
            .append(currentDossier.getFacets().size()).append('|');

        PlayerEventRecord latestEvent = currentDossier.getRecentEvents().isEmpty() ? null : currentDossier.getRecentEvents().getFirst();
        if (latestEvent != null) {
            builder.append(latestEvent.getTimestamp()).append('|')
                .append(compact(latestEvent.getCategory())).append('|')
                .append(compact(latestEvent.getType())).append('|')
                .append(compact(latestEvent.getModuleId())).append('|');
        }
        PlayerSessionRecord activeSession = currentDossier.getActiveSession();
        if (activeSession != null) {
            builder.append(compact(activeSession.getSessionId())).append('|')
                .append(activeSession.getStartedAt()).append('|')
                .append(compact(activeSession.getSource())).append('|');
        }
        List<PlayerFacetState> facets = new ArrayList<>(currentDossier.getFacets().values());
        facets.sort(Comparator.comparing(PlayerFacetState::getFacetId, String.CASE_INSENSITIVE_ORDER));
        for (PlayerFacetState facet : facets) {
            builder.append(compact(facet.getFacetId())).append('|')
                .append(facet.getUpdatedAt()).append('|')
                .append(facet.getData().hashCode()).append('|');
        }
        return builder.toString();
    }

    private String buildModuleSignature(PlayerDossier currentDossier) {
        return String.join("|", collectModuleIds(currentDossier));
    }

    private void resizeContainer(Container container) {
        if (container == null) {
            return;
        }
        container.setPosition(5, CONTENT_Y);
        container.setSize(width - 10, height - CONTENT_Y - 5);
    }

    private String resolveDisplayName() {
        if (dossier != null && dossier.getPlayerName() != null && !dossier.getPlayerName().isBlank()) {
            return dossier.getPlayerName();
        }
        return player.getName() != null && !player.getName().isBlank() ? player.getName() : "Unknown";
    }

    private String resolveIdentityLabel() {
        if (dossier != null && dossier.getPlayerId() != null && !dossier.getPlayerId().isBlank()) {
            return compact(dossier.getPlayerId());
        }
        return compact(player.getUuid().toString());
    }

    private String resolveStatusText() {
        if (player.getBan().getValue() != null) {
            return "Banned";
        }
        if (dossier != null && dossier.isOnline()) {
            return "Online";
        }
        return player.isOnline() ? "Online" : "Offline";
    }

    private String resolveStatusMeta() {
        if (player.isOp()) {
            return "Operator";
        }
        if (dossier != null && dossier.getActiveSession() != null) {
            return formatToken(dossier.getActiveSession().getSource());
        }
        return "No Active Session";
    }

    private String resolveBanMeta() {
        BanInfo ban = player.getBan().getValue();
        if (ban == null) {
            return "No Ban";
        }
        return compact(ban.reason()) + " | " + compact(ban.expires());
    }

    private long resolveTotalPlayTime() {
        return dossier != null ? dossier.getTotalPlayTimeMs() : 0L;
    }

    private long resolveLastSeen() {
        if (dossier != null && dossier.getLastSeenAt() > 0L) {
            return dossier.getLastSeenAt();
        }
        return player.getLastSeenValue();
    }

    private String resolveSessionMeta() {
        if (dossier == null || dossier.getActiveSession() == null) {
            return resolveSessionCount() > 0 ? "History Available" : "No Active Session";
        }
        return "Source: " + formatToken(dossier.getActiveSession().getSource());
    }

    private int resolveSessionCount() {
        int count = dossier != null ? dossier.getSessions().size() : 0;
        if (dossier != null && dossier.getActiveSession() != null) {
            count++;
        }
        return count;
    }

    private boolean isActiveSession(PlayerSessionRecord session) {
        return dossier != null && dossier.getActiveSession() != null && session != null && Objects.equals(dossier.getActiveSession().getSessionId(), session.getSessionId());
    }

    private String resolveLiveSource() {
        if (liveDataSource == null || liveDataSource.isBlank()) {
            return liveData == null ? "Waiting" : "Unknown";
        }
        return formatToken(liveDataSource);
    }

    private String resolveLiveSourceMeta() {
        if (liveData == null) {
            return "Requested";
        }
        return liveData.onlineOnly() ? "Online Only" : "Cached";
    }

    private String buildEventDescription(PlayerEventRecord event) {
        String text = compactMap(event.getData());
        if (text.isBlank()) {
            text = "No Extra Data";
        }
        return text + " | " + TimeUtils.timeSense(event.getTimestamp());
    }

    private String buildSessionDescription(PlayerSessionRecord session, boolean active) {
        StringBuilder builder = new StringBuilder();
        builder.append("Started: ").append(formatTimestamp(session.getStartedAt()));
        if (active) {
            builder.append(" | Running: ").append(formatDuration(Math.max(0L, System.currentTimeMillis() - session.getStartedAt())));
        } else {
            builder.append(" | Ended: ").append(formatTimestamp(session.getEndedAt()));
            builder.append(" | Duration: ").append(formatDuration(session.getDurationMs()));
        }
        return builder.toString();
    }

    private boolean hasActiveActivityFilter() {
        return !selectedSourceFilters.isEmpty()
            || !negativeSourceFilters.isEmpty()
            || !selectedEventFilters.isEmpty()
            || !negativeEventFilters.isEmpty()
            || !selectedTimeFilters.isEmpty()
            || !negativeTimeFilters.isEmpty()
            || !activitySearchQuery.isBlank();
    }

    private String formatVisibleFilterSummary(String moduleId) {
        List<String> parts = new ArrayList<>();
        if (moduleId == null && sourceFilterVisible) {
            parts.add("Source: " + formatFilterSelection(selectedSourceFilters, negativeSourceFilters));
        }
        if (eventFilterVisible) {
            parts.add("Event: " + formatFilterSelection(selectedEventFilters, negativeEventFilters));
        }
        if (timeFilterVisible) {
            parts.add("Time: " + formatFilterSelection(selectedTimeFilters, negativeTimeFilters));
        }
        return parts.isEmpty() ? "No Filters" : compact(String.join(" | ", parts));
    }

    private String formatFilterSelection(List<String> positive, List<String> negative) {
        if (positive.isEmpty() && negative.isEmpty()) {
            return "Any";
        }
        List<String> parts = new ArrayList<>();
        for (String value : positive) {
            parts.add(value);
        }
        for (String value : negative) {
            parts.add("-" + value);
        }
        return compact(String.join(", ", parts));
    }

    private int countFilledSlots(List<PlayerItem> items, int minSlot, int maxSlot) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PlayerItem item : items) {
            if (item == null || item.slot() < minSlot || item.slot() > maxSlot) {
                continue;
            }
            if (item.id() != null && !item.id().isBlank() && !"minecraft:air".equalsIgnoreCase(item.id())) {
                count++;
            }
        }
        return count;
    }

    private String compactMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "No Data";
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            entries.add(formatToken(entry.getKey()) + ": " + compact(String.valueOf(entry.getValue())));
        }
        return compact(String.join(" | ", entries));
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 92 ? normalized.substring(0, 89) + "..." : normalized;
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0L) {
            return "Never";
        }
        return TimeUtils.formatDateTime(timestamp);
    }

    private String formatDuration(long millis) {
        if (millis <= 0L) {
            return "0m";
        }
        long totalMinutes = millis / 60000L;
        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String formatToken(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String[] tokens = value.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }

    private String formatModule(String value) {
        return formatToken(value);
    }

    private String sanitizeId(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9_]+", "_").toLowerCase(Locale.ROOT);
    }

    private boolean isInventorySectionAllowed() {
        return true;
    }

    private boolean canManipulateInventory() {
        return player != null && player.isOnline() && "rcon".equalsIgnoreCase(liveDataSource);
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

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private void updateHeaderButton() {
        if (playerHeaderButton == null) {
            return;
        }
        playerHeaderButton.setMessage(resolveDisplayName());
        playerHeaderButton.setGeneratedIcon(playerFace);
    }

}
