package redxax.oxy.remotely.ui.widgets.management;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerDataManager;
import redxax.oxy.remotely.data.playerdata.PlayerEffect;
import redxax.oxy.remotely.data.item.UiItem;
import redxax.oxy.remotely.data.playerdata.PlayerItem;
import redxax.oxy.remotely.data.playerdata.PlayerStatistic;
import redxax.oxy.remotely.host.ApplicationHost;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.ResourceManager;
import restudio.rescreen.util.SearchUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private static final int INV_TEXTURE_WIDTH = 256;
    private static final int INV_TEXTURE_HEIGHT = 256;
    private static final int SLOT_SIZE = 18;
    private static final int ICON_SIZE = 16;
    private static final int INV_OFFSET_X = 7;
    private static final int INV_OFFSET_Y = 83;
    private static final int HOTBAR_OFFSET_X = 7;
    private static final int HOTBAR_OFFSET_Y = 141;
    private static final int ARMOR_OFFSET_X = 7;
    private static final int ARMOR_OFFSET_Y = 7;
    private static final int OFFHAND_OFFSET_X = 77;
    private static final int OFFHAND_OFFSET_Y = 61;
    private static final int CRAFT_OFFSET_X = 97;
    private static final int CRAFT_OFFSET_Y = 17;
    private static final int CRAFT_SLOT_0 = 80;
    private static final int CRAFT_SLOT_1 = 81;
    private static final int CRAFT_SLOT_2 = 82;
    private static final int CRAFT_SLOT_3 = 83;
    private static final int INV_TRIM_RIGHT = 80;
    private static final int INV_TRIM_BOTTOM = 90;
    private static final int CHEST_GUI_TEXTURE_WIDTH = 176;
    private static final int CHEST_GUI_TOP_MARGIN = 16;
    private static final int CHEST_GUI_SIDE_MARGIN = 7;
    private static final int CHEST_GUI_PLAYER_INV_OFFSET = 13;
    private static final int CHEST_GUI_HOTBAR_OFFSET = 71;
    private static final int CHEST_GUI_PLAYER_INV_HEIGHT = 96;
    private static final int CHEST_GUI_BOTTOM_TEXTURE_Y = 126;
    private final PlayerDataSection[] sections = PlayerDataSection.values();
    private PlayerDataSection activeSection = PlayerDataSection.Overview;
    private final Container sectionContainer;
    private final Container suggestionContainer;
    private final TextInputWidget searchField;
    private final ScheduledThreadPoolExecutor scheduler;
    private String currentSearch = "";
    private List<PlayerStatistic> cachedStats = new ArrayList<>();
    private List<PlayerStatistic> filteredStats = new ArrayList<>();
    private PlayerData cachedData;
    private final UnifiedPlayer player;
    private final PlayerManagerController controller;
    private long refreshIntervalMs;
    private final Map<PlayerDataSection, Float> scrollOffsets = new EnumMap<>(PlayerDataSection.class);
    private String lastSource;
    private volatile long interactionLockUntilMs;

    public PlayerDataPopup(Screen parent, UnifiedPlayer player, PlayerManagerController controller) {
        super(0, 0, 520, 340, "Player Data: " + (player.getName() != null ? player.getName() : "Unknown"));
        setLayer(500);
        this.player = player;
        this.controller = controller;

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
        builder.addRow(SEARCH_ROW_ID, "", true, 20, searchField);
        builder.addRow(SUGGEST_ROW_ID, "", true, 20, suggestionContainer);
        builder.addRow("", true, 224, sectionContainer);
        builder.onClose(this::closePopup);

        PopupWidget configured = builder.build();
        this.rows.addAll(configured.rows);
        this.setSize(configured.getWidth(), configured.getHeight());
        this.setPosition(configured.getX(), configured.getY());

        parent.addDrawableChild(this);
        show();

        scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "Remotely-PlayerData-Refresh");
            t.setDaemon(true);
            return t;
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
        if (section != PlayerDataSection.Inventory && section != PlayerDataSection.EnderChest) {
            return true;
        }
        ApplicationHost host = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getHost() : null;
        if (host == null) {
            return false;
        }
        return host.getClass().getName().equals("redxax.oxy.remotely.host.MinecraftApplicationHost");
    }


    private void closePopup() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        hide();
    }

    private void scheduleRefresh() {
        if (refreshIntervalMs <= 0) return;
        scheduler.scheduleAtFixedRate(() -> requestRefresh(false), 0, refreshIntervalMs, TimeUnit.MILLISECONDS);
    }

    private long resolveRefreshInterval() {
        if (controller == null || controller.getInstance() == null) return 1000L;
        String raw = controller.getInstance().getSettings().getProperty("playerdata.refresh.intervalMs", "1000");
        try {
            long parsed = Long.parseLong(raw);
            if (parsed < 250) return 250L;
            if (parsed > 10000) return 10000L;
            return parsed;
        } catch (Exception ignored) {
            return 1000L;
        }
    }

    private void requestRefresh(boolean immediate) {
        if (controller == null || player == null) return;
        PlayerDataManager dataManager = controller.getPlayerDataManager();
        if (dataManager == null) return;
        long interval = immediate ? 0 : refreshIntervalMs;
        dataManager.refreshIfDue(player.getUuid(), player.getName(), player.isOnline(), interval).thenAccept(data -> {
            if (data == null) {
                return;
            }
            if (System.currentTimeMillis() < interactionLockUntilMs) {
                return;
            }
            if (isInventoryInteractionActive()) {
                return;
            }
            if (isSameData(data, cachedData)) {
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
        if (widget instanceof InventoryGridWidget grid) {
            return grid.isInteractionActive();
        }
        if (widget instanceof EnderChestWidget chest) {
            return chest.isInteractionActive();
        }
        return false;
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
            String dim = data.location().dimension() != null ? data.location().dimension() : "Unknown";
            String pos = formatCoord(data.location().x()) + ", " + formatCoord(data.location().y()) + ", " + formatCoord(data.location().z());
            sectionContainer.addWidget(buildInfoRow("Dimension", formatLabel(dim)));
            sectionContainer.addWidget(buildInfoRow("Location", pos));
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
        InventoryGridWidget grid = new InventoryGridWidget(0, 0, sectionContainer.getEffectiveWidth(), sectionContainer.getHeight(), data);
        sectionContainer.addWidget(grid);
        sectionContainer.updateWidgetPositions();
    }

    private void buildEnderChest(PlayerData data) {
        sectionContainer.layout(new ManagedLayout()).columns(1).padding(0).verticalSpacing(0).enableSelecting(false).scrolling(false).backgroundDrawing(false);
        sectionContainer.clearWidgets();
        sectionContainer.setGroupHeaderEnabled(false);
        EnderChestWidget grid = new EnderChestWidget(0, 0, sectionContainer.getEffectiveWidth(), sectionContainer.getHeight(), data);
        sectionContainer.addWidget(grid);
        sectionContainer.updateWidgetPositions();
    }

    private boolean canManipulateInventory() {
        return player != null && player.isOnline() && "rcon".equalsIgnoreCase(lastSource);
    }

    private void openSlotEditorPopup(String slotId, String slotLabel, PlayerItem item) {
        if (item == null || item.id() == null || item.id().isBlank()) {
            return;
        }
        if (!canManipulateInventory()) {
            new Notification("Error", "Inventory edit requires RCON source.", Notification.Type.ERROR);
            return;
        }
        if (player.getName() == null || player.getName().isBlank()) {
            new Notification("Error", "Player name unavailable.", Notification.Type.ERROR);
            return;
        }

        PopupWidget.Builder builder = new PopupWidget.Builder("Edit " + slotLabel)
            .size(280, 125)
            .setResizable(false)
            .setAntiOutOfBound(true);

        TextInputWidget countField = new TextInputWidget.Builder()
            .text(String.valueOf(Math.max(1, item.count())))
            .placeholder("Count")
            .build();

        MountableButtonWidget itemInfo = new MountableButtonWidget.Builder(formatLabel(item.id()))
            .description(item.id())
            .build();
        itemInfo.setActive(false);
        builder.addRow("Item", true, 20, itemInfo);
        builder.addRow("Count", true, 20, countField);

        builder.addTitleButton(() -> {
            int count;
            try {
                count = Integer.parseInt(countField.getText().trim());
            } catch (Exception e) {
                new Notification("Error", "Invalid count.", Notification.Type.ERROR);
                return;
            }
            if (count < 1 || count > 99) {
                new Notification("Error", "Count range is 1-99.", Notification.Type.ERROR);
                return;
            }
            String command = "item replace entity " + player.getName() + " " + slotId + " with " + item.id() + " " + count;
            controller.runCustomCommand(player, command);
            builder.getWidget().setVisible(false);
            scheduler.schedule(() -> requestRefresh(true), 180, TimeUnit.MILLISECONDS);
        }, "Apply", ThemeManager.getAccent("nice"));

        builder.addTitleButton(() -> {
            String command = "item replace entity " + player.getName() + " " + slotId + " with minecraft:air";
            controller.runCustomCommand(player, command);
            builder.getWidget().setVisible(false);
            scheduler.schedule(() -> requestRefresh(true), 180, TimeUnit.MILLISECONDS);
        }, "Clear", ThemeManager.getAccent("danger"));

        PopupWidget popup = builder.build();
        ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popup);
        popup.show();
    }


    private void buildEffects(PlayerData data) {
        sectionContainer.layout(new ManagedLayout()).columns(1).padding(2).verticalSpacing(2).enableSelecting(false).scrolling(true).backgroundDrawing(true);
        sectionContainer.clearWidgets();
        sectionContainer.setGroupHeaderEnabled(false);
        if (data.effects().isEmpty()) {
            sectionContainer.addWidget(buildInfoRow("Status", "No Effects"));
        } else {
            List<PlayerEffect> effects = new ArrayList<>(data.effects());
            effects.sort(Comparator.comparing(PlayerEffect::id));
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
        cachedStats.sort(Comparator.comparing(PlayerStatistic::key));
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
        String query = currentSearch != null ? currentSearch.trim() : "";
        filteredStats = new ArrayList<>();
        if (query.isEmpty()) {
            filteredStats.addAll(cachedStats);
        } else {
            for (PlayerStatistic stat : cachedStats) {
                String label = formatStatKey(stat.key());
                if (SearchUtils.isFuzzyMatch(label, query)) {
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
        String query = currentSearch != null ? currentSearch.trim() : "";
        if (query.isBlank() || cachedStats.isEmpty()) {
            suggestionContainer.setVisible(false);
            suggestionContainer.setActive(false);
            suggestionContainer.setHeight(0);
            setRowVisibility(SUGGEST_ROW_ID, false);
            return;
        }
        Map<String, Double> scored = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (PlayerStatistic stat : cachedStats) {
            String label = formatStatKey(stat.key());
            double score = SearchUtils.getMatchScore(label, query);
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
            IconButton btn = new IconButton.Builder()
                .label(label)
                .size(120, 18)
                .onClick(() -> applySuggestion(label))
                .build();
            suggestionContainer.addWidget(btn);
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

    private class InventoryGridWidget extends AnimatedWidget {
        private final Map<Integer, PlayerItem> inventory = new HashMap<>();
        private final PlayerItem[] armor = new PlayerItem[4];
        private PlayerItem offhand;
        private final BufferedImage background;
        private final Map<String, Integer> slotPositions = new HashMap<>();
        private int lastBaseX;
        private int lastBaseY;
        private float lastScale = 1f;
        private int lastVisibleW;
        private int lastVisibleH;
        private PlayerItem heldItem;
        private String lastDragSlot;
        private final Map<String, PlayerItem> pendingUpdates = new LinkedHashMap<>();
        private long lastClickAtMs;
        private String lastClickKey;
        private long syncBlockUntilMs;

        public InventoryGridWidget(int x, int y, int width, int height, PlayerData data) {
            super(x, y, width, height, "");
            setCursorHoverReactive(true);
            if (data != null) {
                if (data.inventory() != null) {
                    for (PlayerItem item : data.inventory()) {
                        if (item != null) {
                            inventory.put(item.slot(), item);
                        }
                    }
                }
                if (data.armor() != null) {
                    mapArmor(data.armor());
                }
            }
            this.offhand = data != null && data.offhand() != null && !data.offhand().isEmpty() ? data.offhand().get(0) : null;
            this.background = ResourceManager.getInstance().getImage(buildTextureIdentifier("minecraft", "textures/gui/container/inventory.png"));
            initSlotPositions();
            this.animateElevation = false;
            this.flat = true;
            this.transparent = true;
            this.active = false;
            this.enableHoverColors = false;
            this.entranceAnimationEnabled = false;
        }

        private void mapArmor(List<PlayerItem> items) {
            int nextIndex = 0;
            for (PlayerItem item : items) {
                if (item == null) {
                    continue;
                }
                int slot = item.slot();
                int index = -1;
                if (slot >= 0 && slot <= 3) {
                    index = slot;
                } else if (slot >= 100 && slot <= 103) {
                    index = slot - 100;
                }
                if (index < 0) {
                    while (nextIndex < armor.length && armor[nextIndex] != null) {
                        nextIndex++;
                    }
                    index = nextIndex < armor.length ? nextIndex : -1;
                }
                if (index >= 0 && index < armor.length) {
                    armor[index] = item;
                }
            }
        }

        private void initSlotPositions() {
            slotPositions.put("helmet", pack(ARMOR_OFFSET_X, ARMOR_OFFSET_Y));
            slotPositions.put("chest", pack(ARMOR_OFFSET_X, ARMOR_OFFSET_Y + SLOT_SIZE));
            slotPositions.put("legs", pack(ARMOR_OFFSET_X, ARMOR_OFFSET_Y + SLOT_SIZE * 2));
            slotPositions.put("boots", pack(ARMOR_OFFSET_X, ARMOR_OFFSET_Y + SLOT_SIZE * 3));
            slotPositions.put("offhand", pack(OFFHAND_OFFSET_X, OFFHAND_OFFSET_Y));

            slotPositions.put("craft_0", pack(CRAFT_OFFSET_X, CRAFT_OFFSET_Y));
            slotPositions.put("craft_1", pack(CRAFT_OFFSET_X + SLOT_SIZE, CRAFT_OFFSET_Y));
            slotPositions.put("craft_2", pack(CRAFT_OFFSET_X, CRAFT_OFFSET_Y + SLOT_SIZE));
            slotPositions.put("craft_3", pack(CRAFT_OFFSET_X + SLOT_SIZE, CRAFT_OFFSET_Y + SLOT_SIZE));

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + row * 9 + col;
                    slotPositions.put("inv_" + slotIndex, pack(INV_OFFSET_X + col * SLOT_SIZE, INV_OFFSET_Y + row * SLOT_SIZE));
                }
            }

            for (int col = 0; col < 9; col++) {
                slotPositions.put("hotbar_" + col, pack(HOTBAR_OFFSET_X + col * SLOT_SIZE, HOTBAR_OFFSET_Y));
            }
        }

        private int pack(int x, int y) {
            return (x << 16) | (y & 0xFFFF);
        }

        private int unpackX(int packed) {
            return (packed >> 16) & 0xFFFF;
        }

        private int unpackY(int packed) {
            return packed & 0xFFFF;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            int availableW = Math.max(1, getWidth());
            int availableH = Math.max(1, getHeight());
            int bgW = background != null ? background.getWidth() : INV_TEXTURE_WIDTH;
            int bgH = background != null ? background.getHeight() : INV_TEXTURE_HEIGHT;
            int visibleW = Math.max(1, Math.min(bgW, INV_TEXTURE_WIDTH - INV_TRIM_RIGHT));
            int visibleH = Math.max(1, Math.min(bgH, INV_TEXTURE_HEIGHT - INV_TRIM_BOTTOM));
            int centerX = getX() + availableW / 2;
            int centerY = getY() + availableH / 2;
            int baseX = centerX - visibleW / 2;
            int baseY = centerY - visibleH / 2;
            float scale = 1f;
            lastBaseX = baseX;
            lastBaseY = baseY;
            lastScale = scale;
            lastVisibleW = visibleW;
            lastVisibleH = visibleH;

            if (background != null && background != ResourceManager.getInstance().getMissingTexture()) {
                ctx.enableScissor(baseX, baseY, baseX + visibleW, baseY + visibleH);
                ctx.drawPixelArt(background, baseX, baseY, bgW, bgH);
                ctx.disableScissor();
            }

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + row * 9 + col;
                    int packed = slotPositions.getOrDefault("inv_" + slotIndex, pack(0, 0));
                    drawItem(ctx, inventory.get(slotIndex), baseX, baseY, unpackX(packed), unpackY(packed), scale);
                }
            }

            for (int col = 0; col < 9; col++) {
                int packed = slotPositions.getOrDefault("hotbar_" + col, pack(0, 0));
                drawItem(ctx, inventory.get(col), baseX, baseY, unpackX(packed), unpackY(packed), scale);
            }

            int helmet = slotPositions.getOrDefault("helmet", pack(0, 0));
            int chest = slotPositions.getOrDefault("chest", pack(0, 0));
            int legs = slotPositions.getOrDefault("legs", pack(0, 0));
            int boots = slotPositions.getOrDefault("boots", pack(0, 0));
            drawItem(ctx, armor[3], baseX, baseY, unpackX(helmet), unpackY(helmet), scale);
            drawItem(ctx, armor[2], baseX, baseY, unpackX(chest), unpackY(chest), scale);
            drawItem(ctx, armor[1], baseX, baseY, unpackX(legs), unpackY(legs), scale);
            drawItem(ctx, armor[0], baseX, baseY, unpackX(boots), unpackY(boots), scale);

            int offhandPacked = slotPositions.getOrDefault("offhand", pack(0, 0));
            drawItem(ctx, offhand, baseX, baseY, unpackX(offhandPacked), unpackY(offhandPacked), scale);

            int craft0 = slotPositions.getOrDefault("craft_0", pack(0, 0));
            int craft1 = slotPositions.getOrDefault("craft_1", pack(0, 0));
            int craft2 = slotPositions.getOrDefault("craft_2", pack(0, 0));
            int craft3 = slotPositions.getOrDefault("craft_3", pack(0, 0));
            drawItem(ctx, inventory.get(CRAFT_SLOT_0), baseX, baseY, unpackX(craft0), unpackY(craft0), scale);
            drawItem(ctx, inventory.get(CRAFT_SLOT_1), baseX, baseY, unpackX(craft1), unpackY(craft1), scale);
            drawItem(ctx, inventory.get(CRAFT_SLOT_2), baseX, baseY, unpackX(craft2), unpackY(craft2), scale);
            drawItem(ctx, inventory.get(CRAFT_SLOT_3), baseX, baseY, unpackX(craft3), unpackY(craft3), scale);

            if (heldItem != null) {
                UiItem uiItem = UiItem.fromPlayerItem(heldItem);
                if (uiItem != null) {
                    ctx.drawItem(uiItem, mouseX - 8, mouseY - 8, 0);
                }
                if (heldItem.count() > 1) {
                    String text = String.valueOf(heldItem.count());
                    int textW = TextRenderer.tr.getWidth(text);
                    ctx.drawText(text, mouseX + 8 - textW, mouseY + 7, 0xFFFFFFFF, true);
                }
            }
        }

        private void drawItem(IDrawContext ctx, PlayerItem item, int baseX, int baseY, int slotX, int slotY, float scale) {
            if (item == null || item.id() == null || item.id().isBlank() || "minecraft:air".equalsIgnoreCase(item.id())) {
                return;
            }
            int slotSize = Math.round(SLOT_SIZE * scale);
            int iconSize = Math.max(8, Math.round(ICON_SIZE * scale));
            int iconX = baseX + Math.round(slotX * scale) + (slotSize - iconSize) / 2;
            int iconY = baseY + Math.round(slotY * scale) + (slotSize - iconSize) / 2;
            UiItem uiItem = UiItem.fromPlayerItem(item);
            if (uiItem != null) {
                ctx.drawItem(uiItem, iconX, iconY, 0);
            }
            int count = item.count();
            if (count > 1) {
                String text = String.valueOf(count);
                int textW = TextRenderer.tr.getWidth(text);
                int textX = baseX + Math.round(slotX * scale) + slotSize - textW - 1;
                int textY = baseY + Math.round(slotY * scale) + slotSize - 8;
                ctx.drawText(text, textX, textY, 0xFFFFFFFF, true);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            markInventoryInteraction();
            if (!canManipulateInventory()) {
                return false;
            }
            SlotSelection selection = findSelection(mouseX, mouseY);
            if (selection == null) {
                if (heldItem != null && (button == 0 || button == 1)) {
                    if (isWithinInventoryBounds(mouseX, mouseY)) {
                        return false;
                    }
                    heldItem = null;
                    flushPendingUpdatesIfReady();
                    return true;
                }
                return false;
            }
            if (selection.commandSlot == null) {
                return false;
            }
            long now = System.currentTimeMillis();
            boolean isDoubleLeftClick = button == 0 && Objects.equals(lastClickKey, selection.key) && now - lastClickAtMs <= 250L;
            lastClickAtMs = now;
            lastClickKey = selection.key;
            if (isDoubleLeftClick) {
                boolean handled = handleDoubleClick(selection);
                flushPendingUpdatesIfReady();
                return handled;
            }
            if (button == 2) {
                if (selection.item != null) {
                    heldItem = copyItem(selection.item, 64);
                    return true;
                }
                return false;
            }
            if (button == 0) {
                boolean handled = handleLeftClick(selection);
                flushPendingUpdatesIfReady();
                return handled;
            }
            if (button == 1) {
                boolean handled = handleRightClick(selection);
                flushPendingUpdatesIfReady();
                return handled;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            markInventoryInteraction();
            if (!canManipulateInventory() || (button != 0 && button != 1) || heldItem == null || heldItem.count() <= 0) {
                return false;
            }
            SlotSelection selection = findSelection(mouseX, mouseY);
            if (selection == null || selection.commandSlot == null) {
                return false;
            }
            if (selection.key.equals(lastDragSlot)) {
                return false;
            }
            if (placeOne(selection)) {
                lastDragSlot = selection.key;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) {
                lastDragSlot = null;
                flushPendingUpdatesIfReady();
            }
            if (button == 1) {
                flushPendingUpdatesIfReady();
            }
            return false;
        }

        private boolean handleLeftClick(SlotSelection selection) {
            PlayerItem slotItem = selection.item;
            if (heldItem == null) {
                if (slotItem == null) {
                    return false;
                }
                heldItem = copyItem(slotItem, slotItem.count());
                setSlotItem(selection.key, null);
                sendSlotUpdate(selection.commandSlot, null);
                return true;
            }
            if (slotItem == null) {
                setSlotItem(selection.key, copyItem(heldItem, heldItem.count()));
                sendSlotUpdate(selection.commandSlot, heldItem);
                heldItem = null;
                return true;
            }
            if (canStack(slotItem, heldItem) && slotItem.count() < 64) {
                int transfer = Math.min(64 - slotItem.count(), heldItem.count());
                if (transfer <= 0) {
                    return false;
                }
                PlayerItem updatedSlot = copyItem(slotItem, slotItem.count() + transfer);
                setSlotItem(selection.key, updatedSlot);
                sendSlotUpdate(selection.commandSlot, updatedSlot);
                int remain = heldItem.count() - transfer;
                heldItem = remain > 0 ? copyItem(heldItem, remain) : null;
                return true;
            }
            PlayerItem oldSlot = copyItem(slotItem, slotItem.count());
            setSlotItem(selection.key, copyItem(heldItem, heldItem.count()));
            sendSlotUpdate(selection.commandSlot, heldItem);
            heldItem = oldSlot;
            return true;
        }

        private boolean handleRightClick(SlotSelection selection) {
            PlayerItem slotItem = selection.item;
            if (heldItem == null) {
                if (slotItem == null) {
                    return false;
                }
                int take = (slotItem.count() + 1) / 2;
                int remain = slotItem.count() - take;
                heldItem = copyItem(slotItem, take);
                PlayerItem updated = remain > 0 ? copyItem(slotItem, remain) : null;
                setSlotItem(selection.key, updated);
                sendSlotUpdate(selection.commandSlot, updated);
                return true;
            }
            return placeOne(selection);
        }

        private boolean placeOne(SlotSelection selection) {
            if (heldItem == null || heldItem.count() <= 0) {
                return false;
            }
            PlayerItem slotItem = selection.item;
            if (slotItem == null) {
                PlayerItem placed = copyItem(heldItem, 1);
                setSlotItem(selection.key, placed);
                sendSlotUpdate(selection.commandSlot, placed);
                heldItem = heldItem.count() > 1 ? copyItem(heldItem, heldItem.count() - 1) : null;
                return true;
            }
            if (!canStack(slotItem, heldItem) || slotItem.count() >= 64) {
                return false;
            }
            PlayerItem updatedSlot = copyItem(slotItem, slotItem.count() + 1);
            setSlotItem(selection.key, updatedSlot);
            sendSlotUpdate(selection.commandSlot, updatedSlot);
            heldItem = heldItem.count() > 1 ? copyItem(heldItem, heldItem.count() - 1) : null;
            return true;
        }

        private boolean handleDoubleClick(SlotSelection selection) {
            PlayerItem template = heldItem != null ? heldItem : selection.item;
            if (template == null) {
                return false;
            }
            int total = heldItem != null ? heldItem.count() : 0;
            for (String key : slotPositions.keySet()) {
                String commandSlot = resolveCommandSlotByKey(key);
                if (commandSlot == null) {
                    continue;
                }
                PlayerItem slotItem = resolveItemByKey(key);
                if (!canStack(slotItem, template)) {
                    continue;
                }
                if (total >= 64) {
                    break;
                }
                int take = Math.min(slotItem.count(), 64 - total);
                if (take <= 0) {
                    continue;
                }
                total += take;
                heldItem = copyItem(template, total);
                int remain = slotItem.count() - take;
                PlayerItem updated = remain > 0 ? copyItem(slotItem, remain) : null;
                setSlotItem(key, updated);
                sendSlotUpdate(commandSlot, updated);
            }
            return heldItem != null;
        }

        private void setSlotItem(String key, PlayerItem item) {
            if (key.startsWith("inv_")) {
                int slot = parseIntSafe(key.substring("inv_".length()), -1);
                if (slot >= 0) {
                    if (item == null) inventory.remove(slot);
                    else inventory.put(slot, item);
                }
                return;
            }
            if (key.startsWith("hotbar_")) {
                int slot = parseIntSafe(key.substring("hotbar_".length()), -1);
                if (slot >= 0) {
                    if (item == null) inventory.remove(slot);
                    else inventory.put(slot, item);
                }
                return;
            }
            switch (key) {
                case "helmet" -> armor[3] = item;
                case "chest" -> armor[2] = item;
                case "legs" -> armor[1] = item;
                case "boots" -> armor[0] = item;
                case "offhand" -> offhand = item;
            }
        }

        private void sendSlotUpdate(String commandSlot, PlayerItem item) {
            if (commandSlot == null || player == null || player.getName() == null || player.getName().isBlank()) {
                return;
            }
            pendingUpdates.put(commandSlot, item == null ? null : copyItem(item, item.count()));
        }

        private boolean canStack(PlayerItem first, PlayerItem second) {
            if (first == null || second == null) {
                return false;
            }
            return Objects.equals(first.id(), second.id()) && Objects.equals(first.tag(), second.tag());
        }

        private PlayerItem copyItem(PlayerItem item, int count) {
            if (item == null) {
                return null;
            }
            return new PlayerItem(item.id(), Math.max(1, count), item.slot(), item.tag());
        }

        private void flushPendingUpdatesIfReady() {
            if (heldItem != null || pendingUpdates.isEmpty()) {
                return;
            }
            syncBlockUntilMs = System.currentTimeMillis() + 550L;
            for (Map.Entry<String, PlayerItem> entry : pendingUpdates.entrySet()) {
                String commandSlot = entry.getKey();
                PlayerItem item = entry.getValue();
                String command = item == null
                    ? "item replace entity " + player.getName() + " " + commandSlot + " with minecraft:air"
                    : "item replace entity " + player.getName() + " " + commandSlot + " with " + item.id() + " " + Math.max(1, item.count());
                controller.runCustomCommand(player, command);
            }
            pendingUpdates.clear();
            scheduler.schedule(() -> requestRefresh(true), 180, TimeUnit.MILLISECONDS);
        }

        private boolean isInteractionActive() {
            return heldItem != null || !pendingUpdates.isEmpty() || System.currentTimeMillis() < syncBlockUntilMs;
        }

        private SlotSelection findSelection(double mouseX, double mouseY) {
            for (Map.Entry<String, Integer> entry : slotPositions.entrySet()) {
                String key = entry.getKey();
                int packed = entry.getValue();
                int slotX = lastBaseX + Math.round(unpackX(packed) * lastScale);
                int slotY = lastBaseY + Math.round(unpackY(packed) * lastScale);
                int slotSize = Math.round(SLOT_SIZE * lastScale);
                if (mouseX < slotX || mouseX > slotX + slotSize || mouseY < slotY || mouseY > slotY + slotSize) {
                    continue;
                }
                PlayerItem item = resolveItemByKey(key);
                String commandSlot = resolveCommandSlotByKey(key);
                String label = resolveSlotLabel(key);
                return new SlotSelection(key, item, commandSlot, label);
            }
            return null;
        }

        private boolean isWithinInventoryBounds(double mouseX, double mouseY) {
            return mouseX >= lastBaseX && mouseX <= lastBaseX + lastVisibleW && mouseY >= lastBaseY && mouseY <= lastBaseY + lastVisibleH;
        }

        private PlayerItem resolveItemByKey(String key) {
            if (key.startsWith("inv_")) {
                int slot = parseIntSafe(key.substring("inv_".length()), -1);
                return slot >= 0 ? inventory.get(slot) : null;
            }
            if (key.startsWith("hotbar_")) {
                int slot = parseIntSafe(key.substring("hotbar_".length()), -1);
                return slot >= 0 ? inventory.get(slot) : null;
            }
            return switch (key) {
                case "helmet" -> armor[3];
                case "chest" -> armor[2];
                case "legs" -> armor[1];
                case "boots" -> armor[0];
                case "offhand" -> offhand;
                default -> null;
            };
        }

        private String resolveCommandSlotByKey(String key) {
            if (key.startsWith("inv_")) {
                int slot = parseIntSafe(key.substring("inv_".length()), -1);
                return slot >= 9 ? "inventory." + (slot - 9) : null;
            }
            if (key.startsWith("hotbar_")) {
                int slot = parseIntSafe(key.substring("hotbar_".length()), -1);
                return slot >= 0 ? "hotbar." + slot : null;
            }
            return switch (key) {
                case "helmet" -> "armor.head";
                case "chest" -> "armor.chest";
                case "legs" -> "armor.legs";
                case "boots" -> "armor.feet";
                case "offhand" -> "weapon.offhand";
                default -> null;
            };
        }

        private String resolveSlotLabel(String key) {
            if (key.startsWith("inv_")) {
                int slot = parseIntSafe(key.substring("inv_".length()), -1);
                return slot >= 0 ? "Inventory " + (slot - 8) : "Inventory";
            }
            if (key.startsWith("hotbar_")) {
                int slot = parseIntSafe(key.substring("hotbar_".length()), -1);
                return slot >= 0 ? "Hotbar " + (slot + 1) : "Hotbar";
            }
            return switch (key) {
                case "helmet" -> "Helmet";
                case "chest" -> "Chestplate";
                case "legs" -> "Leggings";
                case "boots" -> "Boots";
                case "offhand" -> "Offhand";
                default -> "Slot";
            };
        }

        private int parseIntSafe(String value, int fallback) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                return fallback;
            }
        }

        private record SlotSelection(String key, PlayerItem item, String commandSlot, String label) {
        }
    }

    private class EnderChestWidget extends AnimatedWidget {
        private final Map<Integer, PlayerItem> enderItems = new HashMap<>();
        private final Map<Integer, PlayerItem> inventory = new HashMap<>();
        private final BufferedImage background;
        private int lastBaseX;
        private int lastBaseY;
        private int lastGuiWidth;
        private int lastGuiHeight;
        private PlayerItem heldItem;
        private Integer lastDragSlot;
        private final Map<String, PlayerItem> pendingUpdates = new LinkedHashMap<>();
        private long lastClickAtMs;
        private Integer lastClickSlot;
        private long syncBlockUntilMs;

        public EnderChestWidget(int x, int y, int width, int height, PlayerData data) {
            super(x, y, width, height, "");
            setCursorHoverReactive(true);
            if (data != null) {
                if (data.enderChest() != null && data.enderChest().items() != null) {
                    for (PlayerItem item : data.enderChest().items()) {
                        if (item != null) {
                            enderItems.put(item.slot(), item);
                        }
                    }
                }
                if (data.inventory() != null) {
                    for (PlayerItem item : data.inventory()) {
                        if (item != null) {
                            inventory.put(item.slot(), item);
                        }
                    }
                }
            }
            this.background = ResourceManager.getInstance().getImage(buildTextureIdentifier("minecraft", "textures/gui/container/generic_54.png"));
            this.animateElevation = false;
            this.flat = true;
            this.transparent = true;
            this.active = false;
            this.enableHoverColors = false;
            this.entranceAnimationEnabled = false;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            int availableW = Math.max(1, getWidth());
            int availableH = Math.max(1, getHeight());
            int rows = 3;
            int topHeight = CHEST_GUI_TOP_MARGIN + rows * SLOT_SIZE;
            int centerX = getX() + availableW / 2;
            int centerY = getY() + availableH / 2;
            int baseX = centerX - CHEST_GUI_TEXTURE_WIDTH / 2;
            int baseY = centerY - (topHeight + CHEST_GUI_PLAYER_INV_HEIGHT) / 2;
            lastBaseX = baseX;
            lastBaseY = baseY;
            lastGuiWidth = CHEST_GUI_TEXTURE_WIDTH;
            lastGuiHeight = topHeight + CHEST_GUI_PLAYER_INV_HEIGHT;

            if (background != null && background != ResourceManager.getInstance().getMissingTexture()) {
                BufferedImage top = background.getSubimage(0, 0, CHEST_GUI_TEXTURE_WIDTH, Math.min(topHeight, background.getHeight()));
                ctx.drawPixelArt(top, baseX, baseY, CHEST_GUI_TEXTURE_WIDTH, topHeight);

                if (background.getHeight() >= CHEST_GUI_BOTTOM_TEXTURE_Y + CHEST_GUI_PLAYER_INV_HEIGHT) {
                    BufferedImage bottom = background.getSubimage(0, CHEST_GUI_BOTTOM_TEXTURE_Y, CHEST_GUI_TEXTURE_WIDTH, CHEST_GUI_PLAYER_INV_HEIGHT);
                    int bottomY = baseY + topHeight;
                    ctx.drawPixelArt(bottom, baseX, bottomY, CHEST_GUI_TEXTURE_WIDTH, CHEST_GUI_PLAYER_INV_HEIGHT);
                }
            }

            int gridX = CHEST_GUI_SIDE_MARGIN;
            int gridY = CHEST_GUI_TOP_MARGIN;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = row * 9 + col;
                    drawItem(ctx, enderItems.get(slotIndex), baseX, baseY, gridX + col * SLOT_SIZE, gridY + row * SLOT_SIZE, 1f);
                }
            }

            int playerInvY = CHEST_GUI_TOP_MARGIN + rows * SLOT_SIZE + CHEST_GUI_PLAYER_INV_OFFSET;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + row * 9 + col;
                    drawItem(ctx, inventory.get(slotIndex), baseX, baseY, gridX + col * SLOT_SIZE, playerInvY + row * SLOT_SIZE, 1f);
                }
            }

            int hotbarY = CHEST_GUI_TOP_MARGIN + rows * SLOT_SIZE + CHEST_GUI_HOTBAR_OFFSET;
            for (int col = 0; col < 9; col++) {
                drawItem(ctx, inventory.get(col), baseX, baseY, gridX + col * SLOT_SIZE, hotbarY, 1f);
            }

            if (heldItem != null) {
                UiItem uiItem = UiItem.fromPlayerItem(heldItem);
                if (uiItem != null) {
                    ctx.drawItem(uiItem, mouseX - 8, mouseY - 8, 0);
                }
                if (heldItem.count() > 1) {
                    String text = String.valueOf(heldItem.count());
                    int textW = TextRenderer.tr.getWidth(text);
                    ctx.drawText(text, mouseX + 8 - textW, mouseY + 7, 0xFFFFFFFF, true);
                }
            }
        }

        private void drawItem(IDrawContext ctx, PlayerItem item, int baseX, int baseY, int slotX, int slotY, float scale) {
            if (item == null || item.id() == null || item.id().isBlank() || "minecraft:air".equalsIgnoreCase(item.id())) {
                return;
            }
            int slotSize = Math.round(SLOT_SIZE * scale);
            int iconSize = Math.max(8, Math.round(ICON_SIZE * scale));
            int iconX = baseX + Math.round(slotX * scale) + (slotSize - iconSize) / 2;
            int iconY = baseY + Math.round(slotY * scale) + (slotSize - iconSize) / 2;
            UiItem uiItem = UiItem.fromPlayerItem(item);
            if (uiItem != null) {
                ctx.drawItem(uiItem, iconX, iconY, 0);
            }
            int count = item.count();
            if (count > 1) {
                String text = String.valueOf(count);
                int textW = TextRenderer.tr.getWidth(text);
                int textX = baseX + Math.round(slotX * scale) + slotSize - textW - 1;
                int textY = baseY + Math.round(slotY * scale) + slotSize - 8;
                ctx.drawText(text, textX, textY, 0xFFFFFFFF, true);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            markInventoryInteraction();
            if (!canManipulateInventory()) {
                return false;
            }
            SlotSelection selection = findSelection(mouseX, mouseY);
            if (selection == null) {
                if (heldItem != null && (button == 0 || button == 1)) {
                    if (isWithinChestBounds(mouseX, mouseY)) {
                        return false;
                    }
                    heldItem = null;
                    flushPendingUpdatesIfReady();
                    return true;
                }
                return false;
            }
            long now = System.currentTimeMillis();
            boolean isDoubleLeftClick = button == 0 && Objects.equals(lastClickSlot, selection.slotIndex) && now - lastClickAtMs <= 250L;
            lastClickAtMs = now;
            lastClickSlot = selection.slotIndex;
            if (isDoubleLeftClick) {
                boolean handled = handleDoubleClick(selection);
                flushPendingUpdatesIfReady();
                return handled;
            }
            if (button == 2) {
                if (selection.item != null) {
                    heldItem = copyItem(selection.item, 64);
                    return true;
                }
                return false;
            }
            if (button == 0) {
                boolean handled = handleLeftClick(selection);
                flushPendingUpdatesIfReady();
                return handled;
            }
            if (button == 1) {
                boolean handled = handleRightClick(selection);
                flushPendingUpdatesIfReady();
                return handled;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            markInventoryInteraction();
            if (!canManipulateInventory() || (button != 0 && button != 1) || heldItem == null || heldItem.count() <= 0) {
                return false;
            }
            SlotSelection selection = findSelection(mouseX, mouseY);
            if (selection == null) {
                return false;
            }
            if (Objects.equals(lastDragSlot, selection.slotIndex)) {
                return false;
            }
            if (placeOne(selection)) {
                lastDragSlot = selection.slotIndex;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) {
                lastDragSlot = null;
                flushPendingUpdatesIfReady();
            }
            if (button == 1) {
                flushPendingUpdatesIfReady();
            }
            return false;
        }

        private boolean handleLeftClick(SlotSelection selection) {
            PlayerItem slotItem = selection.item;
            if (heldItem == null) {
                if (slotItem == null) {
                    return false;
                }
                heldItem = copyItem(slotItem, slotItem.count());
                setSlotItem(selection, null);
                sendSlotUpdate(selection.commandSlot, null);
                return true;
            }
            if (slotItem == null) {
                setSlotItem(selection, copyItem(heldItem, heldItem.count()));
                sendSlotUpdate(selection.commandSlot, heldItem);
                heldItem = null;
                return true;
            }
            if (canStack(slotItem, heldItem) && slotItem.count() < 64) {
                int transfer = Math.min(64 - slotItem.count(), heldItem.count());
                if (transfer <= 0) {
                    return false;
                }
                PlayerItem updated = copyItem(slotItem, slotItem.count() + transfer);
                setSlotItem(selection, updated);
                sendSlotUpdate(selection.commandSlot, updated);
                int remain = heldItem.count() - transfer;
                heldItem = remain > 0 ? copyItem(heldItem, remain) : null;
                return true;
            }
            PlayerItem old = copyItem(slotItem, slotItem.count());
            setSlotItem(selection, copyItem(heldItem, heldItem.count()));
            sendSlotUpdate(selection.commandSlot, heldItem);
            heldItem = old;
            return true;
        }

        private boolean handleRightClick(SlotSelection selection) {
            PlayerItem slotItem = selection.item;
            if (heldItem == null) {
                if (slotItem == null) {
                    return false;
                }
                int take = (slotItem.count() + 1) / 2;
                int remain = slotItem.count() - take;
                heldItem = copyItem(slotItem, take);
                PlayerItem updated = remain > 0 ? copyItem(slotItem, remain) : null;
                setSlotItem(selection, updated);
                sendSlotUpdate(selection.commandSlot, updated);
                return true;
            }
            return placeOne(selection);
        }

        private boolean placeOne(SlotSelection selection) {
            if (heldItem == null || heldItem.count() <= 0) {
                return false;
            }
            PlayerItem slotItem = selection.item;
            if (slotItem == null) {
                PlayerItem placed = copyItem(heldItem, 1);
                setSlotItem(selection, placed);
                sendSlotUpdate(selection.commandSlot, placed);
                heldItem = heldItem.count() > 1 ? copyItem(heldItem, heldItem.count() - 1) : null;
                return true;
            }
            if (!canStack(slotItem, heldItem) || slotItem.count() >= 64) {
                return false;
            }
            PlayerItem updated = copyItem(slotItem, slotItem.count() + 1);
            setSlotItem(selection, updated);
            sendSlotUpdate(selection.commandSlot, updated);
            heldItem = heldItem.count() > 1 ? copyItem(heldItem, heldItem.count() - 1) : null;
            return true;
        }

        private boolean handleDoubleClick(SlotSelection selection) {
            PlayerItem template = heldItem != null ? heldItem : selection.item;
            if (template == null) {
                return false;
            }
            int total = heldItem != null ? heldItem.count() : 0;
            for (int slotIndex = 0; slotIndex < 27; slotIndex++) {
                PlayerItem slotItem = enderItems.get(slotIndex);
                if (!canStack(slotItem, template)) {
                    continue;
                }
                if (total >= 64) {
                    break;
                }
                int take = Math.min(slotItem.count(), 64 - total);
                if (take <= 0) {
                    continue;
                }
                total += take;
                heldItem = copyItem(template, total);
                int remain = slotItem.count() - take;
                PlayerItem updated = remain > 0 ? copyItem(slotItem, remain) : null;
                setSlotItem(new SlotSelection(slotIndex, slotItem, "enderchest." + slotIndex, "Ender Slot " + (slotIndex + 1), true), updated);
                sendSlotUpdate("enderchest." + slotIndex, updated);
            }
            for (int slotIndex = 9; slotIndex < 36 && total < 64; slotIndex++) {
                PlayerItem slotItem = inventory.get(slotIndex);
                if (!canStack(slotItem, template)) {
                    continue;
                }
                int take = Math.min(slotItem.count(), 64 - total);
                if (take <= 0) {
                    continue;
                }
                total += take;
                heldItem = copyItem(template, total);
                int remain = slotItem.count() - take;
                PlayerItem updated = remain > 0 ? copyItem(slotItem, remain) : null;
                setSlotItem(new SlotSelection(slotIndex, slotItem, "inventory." + (slotIndex - 9), "Inventory " + (slotIndex - 8), false), updated);
                sendSlotUpdate("inventory." + (slotIndex - 9), updated);
            }
            for (int slotIndex = 0; slotIndex < 9 && total < 64; slotIndex++) {
                PlayerItem slotItem = inventory.get(slotIndex);
                if (!canStack(slotItem, template)) {
                    continue;
                }
                int take = Math.min(slotItem.count(), 64 - total);
                if (take <= 0) {
                    continue;
                }
                total += take;
                heldItem = copyItem(template, total);
                int remain = slotItem.count() - take;
                PlayerItem updated = remain > 0 ? copyItem(slotItem, remain) : null;
                setSlotItem(new SlotSelection(slotIndex, slotItem, "hotbar." + slotIndex, "Hotbar " + (slotIndex + 1), false), updated);
                sendSlotUpdate("hotbar." + slotIndex, updated);
            }
            return true;
        }

        private void setSlotItem(SlotSelection selection, PlayerItem item) {
            if (selection.enderSlot) {
                if (item == null) {
                    enderItems.remove(selection.slotIndex);
                } else {
                    enderItems.put(selection.slotIndex, item);
                }
            } else {
                if (item == null) {
                    inventory.remove(selection.slotIndex);
                } else {
                    inventory.put(selection.slotIndex, item);
                }
            }
        }

        private void sendSlotUpdate(String commandSlot, PlayerItem item) {
            if (commandSlot == null || player == null || player.getName() == null || player.getName().isBlank()) {
                return;
            }
            pendingUpdates.put(commandSlot, item == null ? null : copyItem(item, item.count()));
        }

        private boolean canStack(PlayerItem first, PlayerItem second) {
            if (first == null || second == null) {
                return false;
            }
            return Objects.equals(first.id(), second.id()) && Objects.equals(first.tag(), second.tag());
        }

        private PlayerItem copyItem(PlayerItem item, int count) {
            if (item == null) {
                return null;
            }
            return new PlayerItem(item.id(), Math.max(1, count), item.slot(), item.tag());
        }

        private void flushPendingUpdatesIfReady() {
            if (heldItem != null || pendingUpdates.isEmpty()) {
                return;
            }
            syncBlockUntilMs = System.currentTimeMillis() + 550L;
            for (Map.Entry<String, PlayerItem> entry : pendingUpdates.entrySet()) {
                String commandSlot = entry.getKey();
                PlayerItem item = entry.getValue();
                String command = item == null
                    ? "item replace entity " + player.getName() + " " + commandSlot + " with minecraft:air"
                    : "item replace entity " + player.getName() + " " + commandSlot + " with " + item.id() + " " + Math.max(1, item.count());
                controller.runCustomCommand(player, command);
            }
            pendingUpdates.clear();
            scheduler.schedule(() -> requestRefresh(true), 180, TimeUnit.MILLISECONDS);
        }

        private boolean isInteractionActive() {
            return heldItem != null || !pendingUpdates.isEmpty() || System.currentTimeMillis() < syncBlockUntilMs;
        }

        private SlotSelection findSelection(double mouseX, double mouseY) {
            int gridX = CHEST_GUI_SIDE_MARGIN;
            int gridY = CHEST_GUI_TOP_MARGIN;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = row * 9 + col;
                    int slotX = lastBaseX + gridX + col * SLOT_SIZE;
                    int slotY = lastBaseY + gridY + row * SLOT_SIZE;
                    if (mouseX < slotX || mouseX > slotX + SLOT_SIZE || mouseY < slotY || mouseY > slotY + SLOT_SIZE) {
                        continue;
                    }
                    PlayerItem item = enderItems.get(slotIndex);
                    return new SlotSelection(slotIndex, item, "enderchest." + slotIndex, "Ender Slot " + (slotIndex + 1), true);
                }
            }
            int playerInvY = CHEST_GUI_TOP_MARGIN + 3 * SLOT_SIZE + CHEST_GUI_PLAYER_INV_OFFSET;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + row * 9 + col;
                    int slotX = lastBaseX + gridX + col * SLOT_SIZE;
                    int slotY = lastBaseY + playerInvY + row * SLOT_SIZE;
                    if (mouseX < slotX || mouseX > slotX + SLOT_SIZE || mouseY < slotY || mouseY > slotY + SLOT_SIZE) {
                        continue;
                    }
                    return new SlotSelection(slotIndex, inventory.get(slotIndex), "inventory." + (slotIndex - 9), "Inventory " + (slotIndex - 8), false);
                }
            }
            int hotbarY = CHEST_GUI_TOP_MARGIN + 3 * SLOT_SIZE + CHEST_GUI_HOTBAR_OFFSET;
            for (int col = 0; col < 9; col++) {
                int slotX = lastBaseX + gridX + col * SLOT_SIZE;
                int slotY = lastBaseY + hotbarY;
                if (mouseX < slotX || mouseX > slotX + SLOT_SIZE || mouseY < slotY || mouseY > slotY + SLOT_SIZE) {
                    continue;
                }
                return new SlotSelection(col, inventory.get(col), "hotbar." + col, "Hotbar " + (col + 1), false);
            }
            return null;
        }

        private boolean isWithinChestBounds(double mouseX, double mouseY) {
            return mouseX >= lastBaseX && mouseX <= lastBaseX + lastGuiWidth && mouseY >= lastBaseY && mouseY <= lastBaseY + lastGuiHeight;
        }

        private record SlotSelection(int slotIndex, PlayerItem item, String commandSlot, String label, boolean enderSlot) {
        }
    }

    private MountableButtonWidget buildInfoRow(String title, String value) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title)
            .description(value)
            .build();
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
        MountableButtonWidget row = new MountableButtonWidget.Builder(title)
            .description(meta)
            .build();
        row.entranceAnimationEnabled = false;
        return row;
    }

    private MountableButtonWidget buildStatRow(PlayerStatistic stat) {
        String label = formatStatKey(stat.key());
        String value = String.valueOf(stat.value());
        MountableButtonWidget row = new MountableButtonWidget.Builder(label)
            .description(value)
            .build();
        row.entranceAnimationEnabled = false;
        return row;
    }

    private MountableButtonWidget buildStatsCategoryRow(String category) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(formatLabel(category))
            .build();
        row.setHeight(18);
        row.setActive(false);
        row.entranceAnimationEnabled = false;
        return row;
    }

    private boolean isDataVersionStat(String rawKey) {
        String normalized = formatStatKey(rawKey).replace(" ", "");
        return "dataversion".equalsIgnoreCase(normalized);
    }

    private BufferedImage resolveItemIcon(String id) {
        if (id == null || id.isBlank()) return null;
        String namespace = "minecraft";
        String path = id.trim();
        if (path.contains(":")) {
            String[] parts = path.split(":", 2);
            namespace = parts[0].isBlank() ? "minecraft" : parts[0];
            path = parts.length > 1 ? parts[1] : "";
        }
        path = path.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (path.isBlank()) {
            path = "stone";
        }
        Identifier identifier = buildTextureIdentifier(namespace, "textures/item/" + path + ".png");
        BufferedImage image = ResourceManager.getInstance().getImage(identifier);
        if (image == ResourceManager.getInstance().getMissingTexture()) {
            identifier = buildTextureIdentifier(namespace, "textures/block/" + path + ".png");
            image = ResourceManager.getInstance().getImage(identifier);
        }
        return image == ResourceManager.getInstance().getMissingTexture() ? null : image;
    }

    private Identifier buildTextureIdentifier(String namespace, String path) {
        String resolved = namespace + ":" + path;
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            Object hostId = RemotelyClient.INSTANCE.getHost().getFontIdentifier(namespace, path);
            if (hostId != null) {
                resolved = hostId.toString();
            }
        }
        return Identifier.of(resolved);
    }

    private boolean isSameData(PlayerData next, PlayerData prev) {
        if (next == prev) return true;
        if (next == null || prev == null) return false;
        if (Double.compare(next.health(), prev.health()) != 0) return false;
        if (next.food() != prev.food()) return false;
        if (Float.compare(next.saturation(), prev.saturation()) != 0) return false;
        if (next.experienceLevel() != prev.experienceLevel()) return false;
        if (Float.compare(next.experienceProgress(), prev.experienceProgress()) != 0) return false;
        if (next.totalExperience() != prev.totalExperience()) return false;
        if (!Objects.equals(next.location(), prev.location())) return false;
        if (!Objects.equals(next.gameMode(), prev.gameMode())) return false;
        if (next.flying() != prev.flying()) return false;
        if (next.fallFlying() != prev.fallFlying()) return false;
        if (!Objects.equals(next.inventory(), prev.inventory())) return false;
        if (!Objects.equals(next.armor(), prev.armor())) return false;
        if (!Objects.equals(next.offhand(), prev.offhand())) return false;
        if (!Objects.equals(next.enderChest(), prev.enderChest())) return false;
        if (!Objects.equals(next.effects(), prev.effects())) return false;
        if (!Objects.equals(next.attributes(), prev.attributes())) return false;
        if (!Objects.equals(next.statistics(), prev.statistics())) return false;
        if (!Objects.equals(next.flattenedStatistics(), prev.flattenedStatistics())) return false;
        return next.onlineOnly() == prev.onlineOnly();
    }


    private String extractCategory(String raw) {
        if (raw == null || raw.isBlank()) return "Other";
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
        if (candidate.isBlank()) return "Other";
        return candidate;
    }

    private String formatCoord(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatLabel(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replace("minecraft:", "").replace('_', ' ');
        if (cleaned.isEmpty()) return cleaned;
        return titleCase(cleaned);
    }

    private String formatStatKey(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replace("stats.", "").replace("minecraft:", "").replace('_', ' ');
        String spaced = cleaned.replace(".", " > ");
        return titleCase(spaced);
    }

    private String titleCase(String value) {
        String[] parts = value.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return sb.toString();
    }

}
