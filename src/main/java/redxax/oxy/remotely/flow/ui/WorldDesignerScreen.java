package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.flow.world.WorldDashboardEntry;
import redxax.oxy.remotely.data.flow.world.WorldGeneratorDescriptor;
import redxax.oxy.remotely.data.flow.world.WorldInventoryGroup;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.data.flow.world.WorldProfileSettings;
import redxax.oxy.remotely.data.flow.world.WorldRegistryEntry;
import redxax.oxy.remotely.data.flow.world.WorldSnapshot;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioView;
import redxax.oxy.remotely.flow.ui.studio.StudioHeaderProvider;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import redxax.oxy.remotely.flow.ui.studio.StudioSelectorView;
import redxax.oxy.remotely.flow.ui.studio.WorldStudioDocumentView;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static restudio.rescreen.config.Config.desktopMode;

public class WorldDesignerScreen extends StudioScreen implements DesktopWindowBehaviorProvider, StudioHeaderProvider, ReSyncStudioView, StudioSelectorView, WorldStudioDocumentView {
    private final String worldName;
    private final String serverId;
    private final Object parent;
    private final Container worldsList;
    private final Container detailPane;
    private final Map<String, WorldEntryRow> entries = new HashMap<>();
    private final List<AnimatedWidget> detailWidgets = new ArrayList<>();
    private final List<AnimatedWidget> headerActions = new ArrayList<>();
    private ItemSelectorWidget activePlayerSelector;
    private ItemSelectorWidget activeOptionSelector;
    private WorldDetailForm detailForm;
    private String selectedWorldName;
    private boolean initialized;
    private int x;
    private int y;
    private int width;
    private int height;

    public WorldDesignerScreen(String worldName, String serverId, Object parent) {
        this.worldName = safeText(worldName);
        this.serverId = safeText(serverId);
        this.parent = parent;
        this.selectedWorldName = safeText(worldName);
        this.worldsList = new Container("worlds-list", 0, 0, 260, 100);
        this.worldsList.layout(new ManagedLayout()).columns(1).padding(4).scrolling(true).backgroundDrawing(true);
        this.detailPane = new Container("world-detail", 0, 0, 300, 100);
        this.detailPane.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true).backgroundDrawing(true);
        createHeaderActions();
    }

    @Override
    public void init() {
        if (initialized) {
            return;
        }
        super.init();
        initialized = true;
        refreshWorlds();
    }

    @Override
    public void selected() {
        init();
        refreshDetails();
    }

    @Override
    public List<AnimatedWidget> getStudioHeaderButtons() {
        return headerActions;
    }

    @Override
    public List<AnimatedWidget> headerButtons() {
        return headerActions;
    }

    @Override
    public void resize(int width, int height) {
        this.x = 10;
        this.y = 36;
        this.width = Math.max(80, width - 20);
        this.height = Math.max(80, height - 44);
        updateLayout();
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        init();
        updateLayout();
        worldsList.render(context, mouseX, mouseY, delta);
        detailPane.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        init();
        if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return detailPane.mouseClicked(mouseX, mouseY, button) || worldsList.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return detailPane.mouseReleased(mouseX, mouseY, button) || worldsList.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return detailPane.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || worldsList.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        return detailPane.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount) || worldsList.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return detailPane.keyPressed(keyCode, scanCode, modifiers) || worldsList.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activeOptionSelector != null && activeOptionSelector.visible && activeOptionSelector.charTyped(chr, modifiers)) {
            return true;
        }
        if (activePlayerSelector != null && activePlayerSelector.visible && activePlayerSelector.charTyped(chr, modifiers)) {
            return true;
        }
        return detailPane.charTyped(chr, modifiers) || worldsList.charTyped(chr, modifiers);
    }

    @Override
    public boolean hasActiveStudioSelector() {
        return activeOptionSelector != null && activeOptionSelector.visible
            || activePlayerSelector != null && activePlayerSelector.visible;
    }

    private void createHeaderActions() {
        headerActions.add(studioHeaderButton("New World", "create.png", this::showCreateWorldPopup, ThemeManager.getAccent("nice")));
        headerActions.add(studioHeaderButton("Import", "download.png", () -> worldManager().importWorlds(serverId), null));
        headerActions.add(studioHeaderButton("Scan", "search.png", () -> worldManager().scanWorlds(serverId), null));
        headerActions.add(studioHeaderButton("Refresh", "reload.png", () -> worldManager().refreshWorldsFromServer(serverId), null));
        headerActions.add(studioHeaderButton("Groups", "resources.png", this::showInventoryGroupsPopup, null));
        headerActions.add(studioHeaderButton("History", "history.png", () -> worldManager().requestWorldAuditSnapshot(serverId), null));
    }

    private IconButton studioHeaderButton(String label, String icon, Runnable action, Accent accent) {
        IconButton.Builder builder = new IconButton.Builder()
            .label(label)
            .imagePath(icon)
            .size(0, 18)
            .autoWidthOnTextChange(true)
            .hint(label)
            .entranceAnimation(false)
            .onClick(action);
        if (accent != null) {
            builder.accentType(accent);
        }
        return builder.build();
    }

    private FlowManager worldManager() {
        return FlowManager.getInstance();
    }

    private void updateLayout() {
        int gap = 8;
        int listWidth = Math.clamp(width / 3, 250, 370);
        worldsList.setPosition(x, y);
        worldsList.setSize(listWidth, height);
        detailPane.setPosition(x + listWidth + gap, y);
        detailPane.setSize(Math.max(80, width - listWidth - gap), height);
        updateEntryWidths();
    }

    private void updateEntryWidths() {
        int entryWidth = Math.max(180, worldsList.getWidth() - 12);
        for (WorldEntryRow entry : entries.values()) {
            entry.widget.setSize(entryWidth, 34);
        }
    }

    @Override
    public void refreshWorlds() {
        FlowManager manager = worldManager();
        if (manager == null) {
            return;
        }
        Map<String, WorldRegistryEntry> worlds = manager.getWorldsForServer(serverId);
        Set<String> nextWorlds = new HashSet<>(worlds.keySet());
        for (String existing : new ArrayList<>(entries.keySet())) {
            if (!containsIgnoreCase(nextWorlds, existing)) {
                WorldEntryRow row = entries.remove(existing);
                if (row != null) {
                    worldsList.removeWidget(row.widget);
                }
            }
        }
        List<String> worldNames = new ArrayList<>(worlds.keySet());
        worldNames.sort(String.CASE_INSENSITIVE_ORDER);
        if (selectedWorldName.isBlank() || !containsIgnoreCase(nextWorlds, selectedWorldName)) {
            selectedWorldName = worldNames.isEmpty() ? "" : worldNames.getFirst();
        }
        for (String worldName : worldNames) {
            upsertWorldEntry(worldName);
        }
        refreshDetails();
        worldsList.updateWidgetPositions();
    }

    private boolean containsIgnoreCase(Collection<String> values, String value) {
        if (values == null || value == null) {
            return false;
        }
        for (String entry : values) {
            if (value.equalsIgnoreCase(safeText(entry))) {
                return true;
            }
        }
        return false;
    }

    private void upsertWorldEntry(String worldName) {
        FlowManager manager = worldManager();
        WorldRegistryEntry world = manager != null ? manager.getWorld(serverId, worldName) : null;
        if (world == null) {
            return;
        }
        String existingKey = findEntryKeyIgnoreCase(entries, worldName);
        WorldDashboardEntry dashboard = findWorldDashboard(worldName);
        String status = dashboard != null ? safeText(dashboard.getStatus()) : world.isLoaded() ? "Loaded" : "Unloaded";
        int players = dashboard != null ? dashboard.getPlayerCount() : 0;
        String environment = dashboard != null && dashboard.getEnvironment() != null ? dashboard.getEnvironment() : world.getEnvironment();
        String difficulty = dashboard != null && dashboard.getDifficulty() != null ? dashboard.getDifficulty() : world.getDifficulty();
        String alias = dashboard != null ? safeText(dashboard.getAlias()) : safeText(world.getProfileSettings().getAlias());
        String description = status + " | " + safeText(environment) + " | " + players + " Players | " + safeText(difficulty);
        String hidden = worldHiddenSummary(world, alias);
        WorldEntryRow row = existingKey == null ? null : entries.get(existingKey);
        if (row != null) {
            if (!Objects.equals(existingKey, worldName)) {
                entries.remove(existingKey);
                entries.put(worldName, row);
            }
            row.update(worldName, description, hidden, world.isLoaded(), worldName.equalsIgnoreCase(selectedWorldName));
            return;
        }
        row = new WorldEntryRow(worldName, description, hidden, world.isLoaded(), worldName.equalsIgnoreCase(selectedWorldName));
        row.widget.setSize(Math.max(180, worldsList.getWidth() - 12), 34);
        worldsList.addWidget(row.widget);
        entries.put(worldName, row);
    }

    private class WorldEntryRow {
        private final MountableButtonWidget widget;
        private final SquareButtonWidget stateButton;
        private final SquareButtonWidget actionsButton;
        private String worldName;

        private WorldEntryRow(String worldName, String description, String hiddenText, boolean loaded, boolean selected) {
            this.worldName = worldName;
            SquareButtonWidget mapButton = new SquareButtonWidget.Builder()
                .imagePath("map.png")
                .hint("Open Map")
                .onClick(() -> worldManager().openWorldMap(serverId, null, this.worldName))
                .build();
            stateButton = new SquareButtonWidget.Builder()
                .imagePath(loaded ? "hide.png" : "add.png")
                .hint(loaded ? "Unload World" : "Load World")
                .onClick(() -> {
                    WorldRegistryEntry world = worldManager().getWorld(serverId, this.worldName);
                    if (world == null) {
                        return;
                    }
                    if (world.isLoaded()) {
                        showWorldUnloadPopup(this.worldName);
                    } else {
                        worldManager().loadWorld(serverId, this.worldName);
                    }
                })
                .build();
            SquareButtonWidget builtActionsButton = new SquareButtonWidget.Builder()
                .imagePath("ContextMenu.png")
                .hint("More Actions")
                .build();
            builtActionsButton.action = () -> showWorldActionsMenu(this.worldName, builtActionsButton);
            actionsButton = builtActionsButton;
            widget = new MountableButtonWidget.Builder(worldName)
                .description(description)
                .hiddenText(hiddenText)
                .onClick(() -> selectWorld(this.worldName))
                .addButton(mapButton)
                .addButton(stateButton)
                .addButton(actionsButton)
                .build();
            update(worldName, description, hiddenText, loaded, selected);
        }

        private void update(String worldName, String description, String hiddenText, boolean loaded, boolean selected) {
            this.worldName = worldName;
            widget.setName(worldName);
            widget.setDescription(description);
            widget.setHiddenText(hiddenText);
            widget.titleColor = selected ? ThemeManager.getAccent("default").getAccentColor() : ThemeManager.getColor(ThemeColor.text);
            stateButton.setIcon(loaded ? "hide.png" : "add.png");
            stateButton.hint = loaded ? "Unload World" : "Load World";
        }
    }

    private String worldHiddenSummary(WorldRegistryEntry world, String alias) {
        StringBuilder hidden = new StringBuilder();
        if (!alias.isBlank()) {
            hidden.append("Alias: ").append(alias);
        }
        if (!safeText(world.getGenerator()).isBlank()) {
            appendHidden(hidden, "Generator: " + world.getGenerator());
        }
        WorldProfileSettings profile = world.getProfileSettings();
        if (!safeText(profile.getInventoryGroupId()).isBlank()) {
            appendHidden(hidden, "Group: " + profile.getInventoryGroupId());
        }
        if (!profile.isPvpEnabled()) {
            appendHidden(hidden, "PVP Off");
        }
        if (!profile.isAutoSaveEnabled()) {
            appendHidden(hidden, "Auto Save Off");
        }
        if (world.isTimeLockEnabled()) {
            appendHidden(hidden, "Time Locked");
        }
        if (world.isWeatherLockEnabled()) {
            appendHidden(hidden, "Weather Locked");
        }
        return hidden.toString();
    }

    private void appendHidden(StringBuilder hidden, String value) {
        if (!hidden.isEmpty()) {
            hidden.append(" | ");
        }
        hidden.append(value);
    }

    private void selectWorld(String worldName) {
        selectedWorldName = safeText(worldName);
        for (String entryName : new ArrayList<>(entries.keySet())) {
            upsertWorldEntry(entryName);
        }
        refreshDetails();
    }

    private void refreshDetails() {
        FlowManager manager = worldManager();
        WorldRegistryEntry world = manager != null ? manager.getWorld(serverId, selectedWorldName) : null;
        if (world == null) {
            clearDetailWidgets();
            addDetailWidget(emptyDetailButton());
            detailPane.updateWidgetPositions();
            detailForm = null;
            return;
        }
        if (detailForm != null && selectedWorldName.equalsIgnoreCase(detailForm.worldName)) {
            detailForm.update(world);
            return;
        }
        clearDetailWidgets();
        WorldProfileSettings profile = world.getProfileSettings();
        int rowWidth = Math.max(220, detailPane.getWidth() - 18);
        TextInputWidget alias = detailInput("Alias", profile.getAlias(), rowWidth / 2 - 5);
        OptionField difficulty = optionField(WorldUiSupport.mergeOptions(List.of("PEACEFUL", "EASY", "NORMAL", "HARD"), safeText(world.getDifficulty()).toUpperCase(Locale.ROOT)),
            safeText(world.getDifficulty()).isBlank() ? "NORMAL" : safeText(world.getDifficulty()).toUpperCase(Locale.ROOT), rowWidth / 2 - 5);
        ToggleWidget hidden = detailToggle("Hidden", profile.isHidden(), 92);
        ToggleWidget forceGameMode = detailToggle("Force Game Mode", profile.isForceGameMode(), 132);
        OptionField gameMode = optionField(WorldUiSupport.mergeOptions(List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"), safeText(profile.getGameMode()).toUpperCase(Locale.ROOT)),
            safeText(profile.getGameMode()).isBlank() ? "SURVIVAL" : safeText(profile.getGameMode()).toUpperCase(Locale.ROOT), rowWidth / 2 - 5);
        ToggleWidget pvp = detailToggle("PVP", profile.isPvpEnabled(), 70);
        ToggleWidget autoSave = detailToggle("Auto Save", profile.isAutoSaveEnabled(), 105);
        ToggleWidget keepSpawn = detailToggle("Keep Spawn", profile.isKeepSpawnLoaded(), 112);
        ToggleWidget animals = detailToggle("Animals", profile.isAnimalSpawnsEnabled(), 92);
        ToggleWidget monsters = detailToggle("Monsters", profile.isMonsterSpawnsEnabled(), 100);
        ToggleWidget hunger = detailToggle("Hunger", profile.isHungerEnabled(), 90);
        ToggleWidget autoHeal = detailToggle("Auto Heal", profile.isAutoHealEnabled(), 100);
        ToggleWidget bedRespawn = detailToggle("Bed Respawn", profile.isBedRespawnEnabled(), 118);
        ToggleWidget anchorRespawn = detailToggle("Anchor Respawn", profile.isAnchorRespawnEnabled(), 132);
        ToggleWidget miscSpawns = detailToggle("Misc Spawns", profile.isNonLivingEntitySpawnsEnabled(), 115);
        TextInputWidget accessPermission = detailInput("Access Permission", profile.getAccessPermission(), rowWidth / 2 - 5);
        TextInputWidget bypassPermission = detailInput("Bypass Permission", profile.getBypassPermission(), rowWidth / 2 - 5);
        TextInputWidget arrivalMessage = detailInput("Arrival Message", profile.getArrivalMessage(), rowWidth / 2 - 5);
        TextInputWidget denyMessage = detailInput("Deny Message", profile.getDenyMessage(), rowWidth / 2 - 5);
        TextInputWidget respawnWorld = detailInput("Respawn World", profile.getRespawnWorld(), rowWidth / 2 - 5);
        TextInputWidget inventoryGroup = detailInput("Inventory Group", profile.getInventoryGroupId(), rowWidth / 2 - 5);
        RowWidget respawnWorldPicker = detailPicker(respawnWorld, rowWidth / 2 - 5, fallbackWorldOptions(world.getWorldName()), value -> respawnWorld.setText(value));
        RowWidget inventoryGroupPicker = detailPicker(inventoryGroup, rowWidth / 2 - 5, inventoryGroupOptions(), value -> inventoryGroup.setText("No Group".equals(value) ? "" : value));
        ToggleWidget customSpawn = detailToggle("Custom Spawn", profile.isCustomSpawnEnabled(), 120);
        TextInputWidget spawnX = detailInput("X", formatDecimal(profile.getSpawnX()), 82);
        TextInputWidget spawnY = detailInput("Y", formatDecimal(profile.getSpawnY()), 82);
        TextInputWidget spawnZ = detailInput("Z", formatDecimal(profile.getSpawnZ()), 82);
        TextInputWidget spawnYaw = detailInput("Yaw", formatDecimal(profile.getSpawnYaw()), 82);
        TextInputWidget spawnPitch = detailInput("Pitch", formatDecimal(profile.getSpawnPitch()), 82);
        TextInputWidget netherWorld = detailInput("Nether World", profile.getLinkedNetherWorld(), rowWidth / 3 - 5);
        TextInputWidget endWorld = detailInput("End World", profile.getLinkedEndWorld(), rowWidth / 3 - 5);
        TextInputWidget overworld = detailInput("Overworld", profile.getLinkedOverworld(), rowWidth / 3 - 5);
        TextInputWidget netherScale = detailInput("Nether Scale", formatDecimal(profile.getNetherScale()), rowWidth / 3 - 5);
        TextInputWidget endScale = detailInput("End Scale", formatDecimal(profile.getEndScale()), rowWidth / 3 - 5);
        ToggleWidget autoNether = detailToggle("Auto Nether", profile.isAutoLinkNetherPortal(), 112);
        ToggleWidget autoEnd = detailToggle("Auto End", profile.isAutoLinkEndPortal(), 92);
        ToggleWidget isolated = detailToggle("Isolated State", world.isIsolatedPlayerState(), 125);
        ToggleWidget timeLock = detailToggle("Time Lock", world.isTimeLockEnabled(), 100);
        TextInputWidget lockedTime = detailInput("Locked Time", String.valueOf(world.getLockedTime()), 120);
        ToggleWidget weatherLock = detailToggle("Weather Lock", world.isWeatherLockEnabled(), 118);
        ToggleWidget storm = detailToggle("Storm", world.isLockedStorm(), 78);
        ToggleWidget thundering = detailToggle("Thunder", world.isLockedThundering(), 92);

        addDetailWidget(metricRow(rowWidth, world));
        addDetailWidget(row("Identity", rowWidth, alias, difficulty.button()));
        addDetailWidget(row("Access", rowWidth, hidden, forceGameMode, gameMode.button()));
        addDetailWidget(row("Permissions", rowWidth, accessPermission, bypassPermission));
        addDetailWidget(row("Messages", rowWidth, arrivalMessage, denyMessage));
        addDetailWidget(row("Rules", rowWidth, pvp, autoSave, keepSpawn, animals, monsters));
        addDetailWidget(row("Player State", rowWidth, hunger, autoHeal, bedRespawn, anchorRespawn, miscSpawns));
        addDetailWidget(row("Travel", rowWidth, respawnWorldPicker, inventoryGroupPicker));
        addDetailWidget(row("Spawn", rowWidth, customSpawn, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch));
        addDetailWidget(row("Links", rowWidth, netherWorld, endWorld, overworld));
        addDetailWidget(row("Portal Scale", rowWidth, netherScale, endScale, autoNether, autoEnd));
        addDetailWidget(row("Runtime", rowWidth, isolated, timeLock, lockedTime, weatherLock, storm, thundering));
        detailForm = new WorldDetailForm(world.getWorldName(), alias, difficulty, hidden, forceGameMode, gameMode, pvp, autoSave,
            keepSpawn, animals, monsters, hunger, autoHeal, bedRespawn, anchorRespawn, miscSpawns, accessPermission,
            bypassPermission, arrivalMessage, denyMessage, respawnWorld, inventoryGroup, customSpawn, spawnX, spawnY, spawnZ,
            spawnYaw, spawnPitch, netherWorld, endWorld, overworld, netherScale, endScale, autoNether, autoEnd, isolated,
            timeLock, lockedTime, weatherLock, storm, thundering);
        addDetailWidget(saveWorldButton(rowWidth, world, alias, difficulty, hidden, forceGameMode, gameMode, pvp, autoSave, keepSpawn,
            animals, monsters, hunger, autoHeal, bedRespawn, anchorRespawn, miscSpawns, accessPermission, bypassPermission,
            arrivalMessage, denyMessage, respawnWorld, inventoryGroup, customSpawn, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
            netherWorld, endWorld, overworld, netherScale, endScale, autoNether, autoEnd, isolated, timeLock, lockedTime,
            weatherLock, storm, thundering));
        detailPane.updateWidgetPositions();
    }

    private void clearDetailWidgets() {
        closeOptionSelector();
        for (AnimatedWidget widget : new ArrayList<>(detailWidgets)) {
            detailPane.removeWidget(widget);
        }
        detailWidgets.clear();
    }

    private class WorldDetailForm {
        private final String worldName;
        private final TextInputWidget alias;
        private final OptionField difficulty;
        private final ToggleWidget hidden;
        private final ToggleWidget forceGameMode;
        private final OptionField gameMode;
        private final ToggleWidget pvp;
        private final ToggleWidget autoSave;
        private final ToggleWidget keepSpawn;
        private final ToggleWidget animals;
        private final ToggleWidget monsters;
        private final ToggleWidget hunger;
        private final ToggleWidget autoHeal;
        private final ToggleWidget bedRespawn;
        private final ToggleWidget anchorRespawn;
        private final ToggleWidget miscSpawns;
        private final TextInputWidget accessPermission;
        private final TextInputWidget bypassPermission;
        private final TextInputWidget arrivalMessage;
        private final TextInputWidget denyMessage;
        private final TextInputWidget respawnWorld;
        private final TextInputWidget inventoryGroup;
        private final ToggleWidget customSpawn;
        private final TextInputWidget spawnX;
        private final TextInputWidget spawnY;
        private final TextInputWidget spawnZ;
        private final TextInputWidget spawnYaw;
        private final TextInputWidget spawnPitch;
        private final TextInputWidget netherWorld;
        private final TextInputWidget endWorld;
        private final TextInputWidget overworld;
        private final TextInputWidget netherScale;
        private final TextInputWidget endScale;
        private final ToggleWidget autoNether;
        private final ToggleWidget autoEnd;
        private final ToggleWidget isolated;
        private final ToggleWidget timeLock;
        private final TextInputWidget lockedTime;
        private final ToggleWidget weatherLock;
        private final ToggleWidget storm;
        private final ToggleWidget thundering;

        private WorldDetailForm(String worldName, TextInputWidget alias, OptionField difficulty, ToggleWidget hidden,
                                ToggleWidget forceGameMode, OptionField gameMode, ToggleWidget pvp, ToggleWidget autoSave,
                                ToggleWidget keepSpawn, ToggleWidget animals, ToggleWidget monsters, ToggleWidget hunger,
                                ToggleWidget autoHeal, ToggleWidget bedRespawn, ToggleWidget anchorRespawn, ToggleWidget miscSpawns,
                                TextInputWidget accessPermission, TextInputWidget bypassPermission, TextInputWidget arrivalMessage,
                                TextInputWidget denyMessage, TextInputWidget respawnWorld, TextInputWidget inventoryGroup,
                                ToggleWidget customSpawn, TextInputWidget spawnX, TextInputWidget spawnY, TextInputWidget spawnZ,
                                TextInputWidget spawnYaw, TextInputWidget spawnPitch, TextInputWidget netherWorld,
                                TextInputWidget endWorld, TextInputWidget overworld, TextInputWidget netherScale,
                                TextInputWidget endScale, ToggleWidget autoNether, ToggleWidget autoEnd, ToggleWidget isolated,
                                ToggleWidget timeLock, TextInputWidget lockedTime, ToggleWidget weatherLock, ToggleWidget storm,
                                ToggleWidget thundering) {
            this.worldName = safeText(worldName);
            this.alias = alias;
            this.difficulty = difficulty;
            this.hidden = hidden;
            this.forceGameMode = forceGameMode;
            this.gameMode = gameMode;
            this.pvp = pvp;
            this.autoSave = autoSave;
            this.keepSpawn = keepSpawn;
            this.animals = animals;
            this.monsters = monsters;
            this.hunger = hunger;
            this.autoHeal = autoHeal;
            this.bedRespawn = bedRespawn;
            this.anchorRespawn = anchorRespawn;
            this.miscSpawns = miscSpawns;
            this.accessPermission = accessPermission;
            this.bypassPermission = bypassPermission;
            this.arrivalMessage = arrivalMessage;
            this.denyMessage = denyMessage;
            this.respawnWorld = respawnWorld;
            this.inventoryGroup = inventoryGroup;
            this.customSpawn = customSpawn;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
            this.spawnYaw = spawnYaw;
            this.spawnPitch = spawnPitch;
            this.netherWorld = netherWorld;
            this.endWorld = endWorld;
            this.overworld = overworld;
            this.netherScale = netherScale;
            this.endScale = endScale;
            this.autoNether = autoNether;
            this.autoEnd = autoEnd;
            this.isolated = isolated;
            this.timeLock = timeLock;
            this.lockedTime = lockedTime;
            this.weatherLock = weatherLock;
            this.storm = storm;
            this.thundering = thundering;
        }

        private void update(WorldRegistryEntry world) {
            WorldProfileSettings profile = world.getProfileSettings();
            updateInput(alias, profile.getAlias());
            difficulty.setValue(safeText(world.getDifficulty()).isBlank() ? "NORMAL" : safeText(world.getDifficulty()).toUpperCase(Locale.ROOT));
            hidden.setValue(profile.isHidden());
            forceGameMode.setValue(profile.isForceGameMode());
            gameMode.setValue(safeText(profile.getGameMode()).isBlank() ? "SURVIVAL" : safeText(profile.getGameMode()).toUpperCase(Locale.ROOT));
            pvp.setValue(profile.isPvpEnabled());
            autoSave.setValue(profile.isAutoSaveEnabled());
            keepSpawn.setValue(profile.isKeepSpawnLoaded());
            animals.setValue(profile.isAnimalSpawnsEnabled());
            monsters.setValue(profile.isMonsterSpawnsEnabled());
            hunger.setValue(profile.isHungerEnabled());
            autoHeal.setValue(profile.isAutoHealEnabled());
            bedRespawn.setValue(profile.isBedRespawnEnabled());
            anchorRespawn.setValue(profile.isAnchorRespawnEnabled());
            miscSpawns.setValue(profile.isNonLivingEntitySpawnsEnabled());
            updateInput(accessPermission, profile.getAccessPermission());
            updateInput(bypassPermission, profile.getBypassPermission());
            updateInput(arrivalMessage, profile.getArrivalMessage());
            updateInput(denyMessage, profile.getDenyMessage());
            updateInput(respawnWorld, profile.getRespawnWorld());
            updateInput(inventoryGroup, profile.getInventoryGroupId());
            customSpawn.setValue(profile.isCustomSpawnEnabled());
            updateInput(spawnX, formatDecimal(profile.getSpawnX()));
            updateInput(spawnY, formatDecimal(profile.getSpawnY()));
            updateInput(spawnZ, formatDecimal(profile.getSpawnZ()));
            updateInput(spawnYaw, formatDecimal(profile.getSpawnYaw()));
            updateInput(spawnPitch, formatDecimal(profile.getSpawnPitch()));
            updateInput(netherWorld, profile.getLinkedNetherWorld());
            updateInput(endWorld, profile.getLinkedEndWorld());
            updateInput(overworld, profile.getLinkedOverworld());
            updateInput(netherScale, formatDecimal(profile.getNetherScale()));
            updateInput(endScale, formatDecimal(profile.getEndScale()));
            autoNether.setValue(profile.isAutoLinkNetherPortal());
            autoEnd.setValue(profile.isAutoLinkEndPortal());
            isolated.setValue(world.isIsolatedPlayerState());
            timeLock.setValue(world.isTimeLockEnabled());
            updateInput(lockedTime, String.valueOf(world.getLockedTime()));
            weatherLock.setValue(world.isWeatherLockEnabled());
            storm.setValue(world.isLockedStorm());
            thundering.setValue(world.isLockedThundering());
        }

        private void updateInput(TextInputWidget input, String value) {
            if (input != null && !input.isFocused() && !Objects.equals(input.getText(), safeText(value))) {
                input.setText(safeText(value));
            }
        }

    }

    private AnimatedWidget metricRow(int rowWidth, WorldRegistryEntry world) {
        WorldDashboardEntry dashboard = findWorldDashboard(world.getWorldName());
        String status = dashboard != null ? safeText(dashboard.getStatus()) : world.isLoaded() ? "Loaded" : "Unloaded";
        int players = dashboard != null ? dashboard.getPlayerCount() : 0;
        IconButton statusButton = metricButton(status, world.isLoaded() ? "play.png" : "hide.png", 120);
        IconButton playersButton = metricButton(players + " Players", "steve.png", 120);
        IconButton environmentButton = metricButton(safeText(world.getEnvironment()).isBlank() ? "Unknown" : world.getEnvironment(), "earth.png", 145);
        IconButton generatorButton = metricButton(safeText(world.getGenerator()).isBlank() ? "Default" : world.getGenerator(), "resources.png", 160);
        return new RowWidget.Builder().size(rowWidth, 20).addWidget(statusButton, playersButton, environmentButton, generatorButton).build();
    }

    private IconButton metricButton(String label, String icon, int width) {
        IconButton button = new IconButton.Builder()
            .label(label)
            .imagePath(icon)
            .size(width, 18)
            .entranceAnimation(false)
            .build();
        button.active = false;
        return button;
    }

    private TitledRowWidget row(String title, int rowWidth, AnimatedWidget... widgets) {
        TitledRowWidget.Builder builder = new TitledRowWidget.Builder()
            .title(title)
            .description(worldPanelDescription(title))
            .size(rowWidth, 30)
            .padding(4);
        for (AnimatedWidget widget : widgets) {
            builder.addWidget(widget);
        }
        return builder.build();
    }

    private String worldPanelDescription(String title) {
        return switch (title) {
            case "Identity" -> "World identity fields.\nIncludes display alias, environment, seed, generator, and difficulty.";
            case "Access" -> "Join and play-state controls.\nCovers visibility, forced game mode, and access behavior.";
            case "Permissions" -> "Permission nodes for entering, bypassing, or managing this world.\nLeave empty when no permission check applies.";
            case "Messages" -> "Player-facing messages.\nShown on join, denial, fallback, or world-specific transitions.";
            case "Rules" -> "Core gameplay toggles.\nControls PvP, saving, spawn loading, and entity spawning.";
            case "Player State" -> "World-specific player state rules.\nControls hunger, healing, respawn behavior, and related state resets.";
            case "Travel" -> "World routing settings.\nControls fallback world and inventory-group selection during transfers.";
            case "Spawn" -> "Custom spawn override.\nIncludes world, X, Y, Z, yaw, and pitch.";
            case "Links" -> "Dimension-style links.\nConnects overworld, nether, and end destinations for travel logic.";
            case "Portal Scale" -> "Portal coordinate scaling.\nUsed when linked dimensions convert travel positions.";
            case "Runtime" -> "Live world locks and isolation.\nControls time, weather, storms, thunder, and runtime isolation.";
            case "Groups" -> "Inventory group editor.\nGroups define which player state is shared across selected worlds.";
            case "Saved Groups" -> "Saved inventory groups for this server.\nSelect one to edit its worlds and preserved state.";
            case "Inventory" -> "Inventory state preserved by this group.\nIncludes main inventory, armor, offhand, and ender chest.";
            case "Location" -> "Location state preserved by this group.\nIncludes effects, last location, and bed spawn.";
            case "Actions" -> "Editor actions for the current world or group.\nSave applies changes; cancel discards the draft.";
            case "Worlds" -> "Worlds included by this selector or inventory group.\nEmpty selections depend on the surrounding field behavior.";
            default -> "";
        };
    }

    private void addDetailWidget(AnimatedWidget widget) {
        detailPane.addWidget(widget);
        detailWidgets.add(widget);
    }

    private TextInputWidget detailInput(String placeholder, String value, int width) {
        TextInputWidget input = new TextInputWidget.Builder()
            .placeholder(placeholder)
            .forcePlaceholder(false)
            .text(safeText(value))
            .size(Math.max(60, width), 20)
            .build();
        input.entranceAnimationEnabled = false;
        return input;
    }

    private RowWidget detailPicker(TextInputWidget input, int width, List<String> options, Consumer<String> onSelected) {
        input.setWidth(Math.max(60, width - 24));
        SquareButtonWidget picker = new SquareButtonWidget.Builder()
            .imagePath("search.png")
            .size(18, 18)
            .hint("Select")
            .entranceAnimation(false)
            .onClick(() -> showOptionSelector(input, options, onSelected))
            .build();
        RowWidget row = new RowWidget.Builder()
            .size(Math.max(80, width), 20)
            .padding(2)
            .addWidget(input)
            .addWidget(picker)
            .build();
        row.entranceAnimationEnabled = false;
        return row;
    }

    private OptionField optionField(List<String> options, String selected, int width) {
        return optionField(options, selected, width, this::worldOptionLabel);
    }

    private OptionField optionField(List<String> options, String selected, int width, Function<String, String> labeler) {
        return new OptionField(options, selected, width, labeler);
    }

    private final class OptionField {
        private final List<String> options;
        private final AnimatedButton button;
        private final Function<String, String> labeler;
        private String value;

        private OptionField(List<String> options, String selected, int width, Function<String, String> labeler) {
            this.options = options == null ? List.of() : options.stream()
                .filter(option -> option != null && !option.isBlank())
                .distinct()
                .toList();
            this.labeler = labeler == null ? value -> value : labeler;
            value = resolveOptionValue(this.options, selected);
            button = new AnimatedButton.Builder()
                .label(optionLabel(value))
                .size(Math.max(80, width), 20)
                .entranceAnimation(false)
                .build();
            button.setAction(() -> showOptionSelector(button, this.options, this::setValue));
        }

        private AnimatedButton button() {
            return button;
        }

        private String value() {
            return value;
        }

        private void setValue(String value) {
            String resolved = resolveOptionValue(options, value);
            if (!Objects.equals(this.value, resolved)) {
                this.value = resolved;
                button.setMessage(optionLabel(resolved));
            }
        }

        private String optionLabel(String value) {
            String label = labeler.apply(safeText(value));
            return label == null || label.isBlank() ? "None" : label;
        }
    }

    private String resolveOptionValue(List<String> options, String value) {
        if (options == null || options.isEmpty()) {
            return safeText(value);
        }
        String text = safeText(value);
        for (String option : options) {
            if (safeText(option).equalsIgnoreCase(text)) {
                return option;
            }
        }
        return options.getFirst();
    }

    private String worldOptionLabel(String value) {
        if (value == null || value.isBlank()) {
            return "None";
        }
        String cleaned = value.trim().replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    private void showOptionSelector(AnimatedWidget anchor, List<String> options, Consumer<String> onSelected) {
        if (anchor == null || onSelected == null) {
            return;
        }
        List<String> choices = options == null ? List.of() : options.stream()
            .filter(option -> option != null && !option.isBlank())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        if (choices.isEmpty()) {
            return;
        }
        closeOptionSelector();
        var overlay = ScreenManager.getInstance().getPopupOverlay();
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(overlay)
            .size(220, 240)
            .dismissOnSelect(true)
            .onClose(() -> closeOptionSelector(selectorRef[0]))
            .build();
        selector.setLayer(900);
        selector.setPriority(30);
        selectorRef[0] = selector;
        for (String option : choices) {
            selector.addItem(option, () -> onSelected.accept(option));
        }
        activeOptionSelector = selector;
        overlay.addDrawableChild(selector);
        selector.show(anchor.getX(), anchor.getY() + anchor.getHeight());
    }

    private void closeOptionSelector() {
        closeOptionSelector(activeOptionSelector);
    }

    private void closeOptionSelector(ItemSelectorWidget selector) {
        if (selector == null) {
            return;
        }
        selector.onClose = null;
        selector.hide();
        ScreenManager.getInstance().getPopupOverlay().remove(selector);
        if (selector == activeOptionSelector) {
            activeOptionSelector = null;
        }
    }

    private ToggleWidget detailToggle(String label, boolean value, int width) {
        return new ToggleWidget.Builder()
            .label(label)
            .toggled(value)
            .size(width, 20)
            .entranceAnimation(false)
            .build();
    }

    private IconButton saveWorldButton(int rowWidth, WorldRegistryEntry world, TextInputWidget alias, OptionField difficulty,
                                       ToggleWidget hidden, ToggleWidget forceGameMode, OptionField gameMode, ToggleWidget pvp,
                                       ToggleWidget autoSave, ToggleWidget keepSpawn, ToggleWidget animals, ToggleWidget monsters,
                                       ToggleWidget hunger, ToggleWidget autoHeal, ToggleWidget bedRespawn, ToggleWidget anchorRespawn,
                                       ToggleWidget miscSpawns, TextInputWidget accessPermission, TextInputWidget bypassPermission,
                                       TextInputWidget arrivalMessage, TextInputWidget denyMessage, TextInputWidget respawnWorld,
                                       TextInputWidget inventoryGroup, ToggleWidget customSpawn, TextInputWidget spawnX, TextInputWidget spawnY,
                                       TextInputWidget spawnZ, TextInputWidget spawnYaw, TextInputWidget spawnPitch, TextInputWidget netherWorld,
                                       TextInputWidget endWorld, TextInputWidget overworld, TextInputWidget netherScale, TextInputWidget endScale,
                                       ToggleWidget autoNether, ToggleWidget autoEnd, ToggleWidget isolated, ToggleWidget timeLock,
                                       TextInputWidget lockedTime, ToggleWidget weatherLock, ToggleWidget storm, ToggleWidget thundering) {
        return new IconButton.Builder()
            .label("Save World")
            .imagePath("save.png")
            .accentType(ThemeManager.getAccent("nice"))
            .size(rowWidth, 22)
            .entranceAnimation(false)
            .onClick(() -> saveWorld(world, alias, difficulty, hidden, forceGameMode, gameMode, pvp, autoSave, keepSpawn, animals,
                monsters, hunger, autoHeal, bedRespawn, anchorRespawn, miscSpawns, accessPermission, bypassPermission, arrivalMessage,
                denyMessage, respawnWorld, inventoryGroup, customSpawn, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, netherWorld,
                endWorld, overworld, netherScale, endScale, autoNether, autoEnd, isolated, timeLock, lockedTime, weatherLock, storm,
                thundering))
            .build();
    }

    private void saveWorld(WorldRegistryEntry world, TextInputWidget alias, OptionField difficulty, ToggleWidget hidden,
                           ToggleWidget forceGameMode, OptionField gameMode, ToggleWidget pvp, ToggleWidget autoSave,
                           ToggleWidget keepSpawn, ToggleWidget animals, ToggleWidget monsters, ToggleWidget hunger, ToggleWidget autoHeal,
                           ToggleWidget bedRespawn, ToggleWidget anchorRespawn, ToggleWidget miscSpawns, TextInputWidget accessPermission,
                           TextInputWidget bypassPermission, TextInputWidget arrivalMessage, TextInputWidget denyMessage,
                           TextInputWidget respawnWorld, TextInputWidget inventoryGroup, ToggleWidget customSpawn, TextInputWidget spawnX,
                           TextInputWidget spawnY, TextInputWidget spawnZ, TextInputWidget spawnYaw, TextInputWidget spawnPitch,
                           TextInputWidget netherWorld, TextInputWidget endWorld, TextInputWidget overworld, TextInputWidget netherScale,
                           TextInputWidget endScale, ToggleWidget autoNether, ToggleWidget autoEnd, ToggleWidget isolated,
                           ToggleWidget timeLock, TextInputWidget lockedTime, ToggleWidget weatherLock, ToggleWidget storm,
                           ToggleWidget thundering) {
        FlowManager manager = worldManager();
        if (manager == null || world == null) {
            return;
        }
        Double parsedSpawnX = parseNullableDouble(spawnX.getText());
        Double parsedSpawnY = parseNullableDouble(spawnY.getText());
        Double parsedSpawnZ = parseNullableDouble(spawnZ.getText());
        Float parsedSpawnYaw = parseNullableFloat(spawnYaw.getText());
        Float parsedSpawnPitch = parseNullableFloat(spawnPitch.getText());
        Double parsedNetherScale = parseNullableDouble(netherScale.getText());
        Double parsedEndScale = parseNullableDouble(endScale.getText());
        Long parsedLockedTime = parseNullableLong(lockedTime.getText());
        if (parsedSpawnX == null || parsedSpawnY == null || parsedSpawnZ == null || parsedSpawnYaw == null || parsedSpawnPitch == null) {
            new Notification("World", "Invalid Spawn", Notification.Type.ERROR);
            return;
        }
        if (parsedNetherScale == null || parsedNetherScale <= 0.0 || parsedEndScale == null || parsedEndScale <= 0.0) {
            new Notification("World", "Invalid Portal Scale", Notification.Type.ERROR);
            return;
        }
        if (parsedLockedTime == null || parsedLockedTime < 0L) {
            new Notification("World", "Invalid Time", Notification.Type.ERROR);
            return;
        }
        WorldProfileSettings profile = new WorldProfileSettings();
        profile.setAlias(alias.getText());
        profile.setHidden(hidden.getValue());
        profile.setAccessPermission(accessPermission.getText());
        profile.setBypassPermission(bypassPermission.getText());
        profile.setRespawnWorld(respawnWorld.getText());
        profile.setForceGameMode(forceGameMode.getValue());
        profile.setGameMode(safeText(gameMode.value()));
        profile.setCustomSpawnEnabled(customSpawn.getValue());
        profile.setSpawnX(parsedSpawnX);
        profile.setSpawnY(parsedSpawnY);
        profile.setSpawnZ(parsedSpawnZ);
        profile.setSpawnYaw(parsedSpawnYaw);
        profile.setSpawnPitch(parsedSpawnPitch);
        profile.setPvpEnabled(pvp.getValue());
        profile.setKeepSpawnLoaded(keepSpawn.getValue());
        profile.setAutoSaveEnabled(autoSave.getValue());
        profile.setAnimalSpawnsEnabled(animals.getValue());
        profile.setMonsterSpawnsEnabled(monsters.getValue());
        profile.setHungerEnabled(hunger.getValue());
        profile.setAutoHealEnabled(autoHeal.getValue());
        profile.setBedRespawnEnabled(bedRespawn.getValue());
        profile.setAnchorRespawnEnabled(anchorRespawn.getValue());
        profile.setNonLivingEntitySpawnsEnabled(miscSpawns.getValue());
        profile.setArrivalMessage(arrivalMessage.getText());
        profile.setDenyMessage(denyMessage.getText());
        profile.setInventoryGroupId(inventoryGroup.getText());
        profile.setLinkedNetherWorld(netherWorld.getText());
        profile.setLinkedEndWorld(endWorld.getText());
        profile.setLinkedOverworld(overworld.getText());
        profile.setNetherScale(parsedNetherScale);
        profile.setEndScale(parsedEndScale);
        profile.setAutoLinkNetherPortal(autoNether.getValue());
        profile.setAutoLinkEndPortal(autoEnd.getValue());
        String worldName = world.getWorldName();
        if (!safeText(difficulty.value()).equalsIgnoreCase(safeText(world.getDifficulty()))) {
            manager.suppressNextWorldSuccessNotification(serverId, "setDifficulty");
            manager.setWorldDifficulty(serverId, worldName, safeText(difficulty.value()));
        }
        manager.suppressNextWorldSuccessNotification(serverId, "setWorldProfile");
        manager.setWorldProfile(serverId, worldName, profile);
        if (isolated.getValue() != world.isIsolatedPlayerState()) {
            manager.suppressNextWorldSuccessNotification(serverId, "setIsolatedPlayerState");
            manager.setWorldIsolatedState(serverId, worldName, isolated.getValue());
        }
        if (timeLock.getValue() != world.isTimeLockEnabled() || parsedLockedTime != world.getLockedTime()) {
            manager.suppressNextWorldSuccessNotification(serverId, "setTimeLock");
            manager.setWorldTimeLock(serverId, worldName, timeLock.getValue(), parsedLockedTime);
        }
        if (weatherLock.getValue() != world.isWeatherLockEnabled() || storm.getValue() != world.isLockedStorm() || thundering.getValue() != world.isLockedThundering()) {
            manager.suppressNextWorldSuccessNotification(serverId, "setWeatherLock");
            manager.setWorldWeatherLock(serverId, worldName, weatherLock.getValue(), storm.getValue(), thundering.getValue());
        }
        new Notification("ReSync", "World Saved", Notification.Type.SUCCESS);
    }

    private IconButton emptyDetailButton() {
        IconButton button = new IconButton.Builder()
            .label("No Worlds")
            .imagePath("earth.png")
            .size(Math.max(160, detailPane.getWidth() - 18), 22)
            .entranceAnimation(false)
            .build();
        button.active = false;
        return button;
    }

    private void showWorldActionsMenu(String worldName, SquareButtonWidget anchor) {
        FlowManager manager = worldManager();
        if (manager == null || anchor == null || worldName == null || worldName.isBlank()) {
            return;
        }
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this)
            .addHeaderButton("map.png", () -> manager.openWorldMap(serverId, null, worldName), "Open Map")
            .addHeaderButton("steve.png", () -> showWorldTeleportPopup(worldName), "Teleport Player")
            .addHeaderButton("search.png", () -> manager.whoWorld(serverId, worldName), "View Players")
            .addIconItem("Clone World", "copy.png", () -> showCloneWorldPopup(worldName), "")
            .addIconItem("Purge Entities", "delete.png", () -> showWorldPurgePopup(worldName), "", ThemeManager.getAccent("calm"))
            .addIconItem("Delete World", "delete.png", () -> showWorldDeletePopup(worldName), "", ThemeManager.getAccent("danger"));
        showContextMenu(anchor.getX() + anchor.getWidth() + 4, anchor.getY() + anchor.getHeight(), builder);
    }

    private void showCreateWorldPopup() {
        FlowManager manager = worldManager();
        if (manager == null) {
            return;
        }
        WorldGenManager.getInstance().requestProjectList(serverId);
        WorldSnapshot snapshot = manager.getWorldSnapshot(serverId);
        List<WorldGeneratorDescriptor> generatorDescriptors = snapshot == null ? List.of() : snapshot.getGeneratorDescriptors();
        TextInputWidget worldInput = new TextInputWidget.Builder().placeholder("World Name").size(220, 18).build();
        TextInputWidget seedInput = new TextInputWidget.Builder().placeholder("Seed").size(220, 18).build();
        OptionField environmentSelect = optionField(List.of("NORMAL", "NETHER", "THE_END", "CUSTOM"), "NORMAL", 220);
        List<GeneratorOption> generatorOptions = createGeneratorOptions(generatorDescriptors);
        List<String> generatorLabels = generatorOptions.stream().map(GeneratorOption::label).toList();
        OptionField generatorSelect = optionField(generatorLabels, generatorOptions.getFirst().label(), 220, value -> value);
        TextInputWidget generatorConfig = new TextInputWidget.Builder().placeholder("Generator Config").size(220, 18).build();
        GeneratorOption[] selectedGenerator = new GeneratorOption[] {generatorOptionByLabel(generatorOptions, generatorSelect.value())};
        applyGeneratorOption(selectedGenerator[0], generatorConfig);
        generatorSelect.button().setAction(() -> showOptionSelector(generatorSelect.button(), generatorLabels, value -> {
            generatorSelect.setValue(value);
            selectedGenerator[0] = generatorOptionByLabel(generatorOptions, value);
            applyGeneratorOption(selectedGenerator[0], generatorConfig);
        }));
        PopupWidget.Builder builder = new PopupWidget.Builder("Create World")
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(420, 220);
        builder.addRow("World", true, 18, worldInput);
        builder.addRow("Seed", true, 18, seedInput);
        builder.addRow("Environment", true, 18, environmentSelect.button());
        builder.addRow("Generator", true, 18, generatorSelect.button());
        builder.addRow("Config", true, 18, generatorConfig);
        PopupWidget[] popupRef = new PopupWidget[1];
        IconButton createButton = new IconButton.Builder()
            .label("Create")
            .imagePath("create.png")
            .accentType(ThemeManager.getAccent("nice"))
            .size(110, 20)
            .onClick(() -> {
                String worldName = safeText(worldInput.getText()).trim();
                if (!WorldUiSupport.isValidSimpleId(worldName)) {
                    new Notification("World", "Invalid World Name", Notification.Type.ERROR);
                    return;
                }
                if (WorldUiSupport.containsIgnoreCase(worldNameOptions(), worldName)) {
                    new Notification("World", "World Exists", Notification.Type.ERROR);
                    return;
                }
                GeneratorOption generatorOption = selectedGenerator[0];
                manager.createWorld(serverId, worldName, seedInput.getText(), safeText(environmentSelect.value()),
                    generatorOption == null ? "" : generatorOption.generator(), generatorConfig.getText());
                selectedWorldName = worldName;
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, createButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private List<GeneratorOption> createGeneratorOptions(List<WorldGeneratorDescriptor> descriptors) {
        List<GeneratorOption> options = new ArrayList<>();
        options.add(new GeneratorOption("Default", "", "", false));
        for (WorldGeneratorDescriptor descriptor : descriptors) {
            if (descriptor == null || safeText(descriptor.getId()).isBlank()) {
                continue;
            }
            String label = safeText(descriptor.getDisplayName()).isBlank() ? descriptor.getId() : descriptor.getDisplayName();
            addGeneratorOption(options, new GeneratorOption(label, descriptor.getId(), descriptor.getDefaultConfig(), descriptor.isConfigurable()));
        }
        Set<String> projectIds = new LinkedHashSet<>(WorldGenManager.getInstance().getProjectIds(serverId));
        ReSyncProjectMetadata metadata = worldManager().getProjectMetadata(serverId);
        for (ReSyncProjectMetadata.ResourceEntry resource : metadata.getResources()) {
            if (resource != null && ReSyncResourceDragPayload.WORLDGEN.equals(resource.getType()) && !safeText(resource.getId()).isBlank()) {
                projectIds.add(resource.getId());
            }
        }
        List<String> sortedProjectIds = new ArrayList<>(projectIds);
        sortedProjectIds.sort(String.CASE_INSENSITIVE_ORDER);
        for (String projectId : sortedProjectIds) {
            if (!safeText(projectId).isBlank()) {
                addGeneratorOption(options, new GeneratorOption("WorldGen: " + projectId, "worldgen_project", projectId, true));
            }
        }
        return options;
    }

    private void addGeneratorOption(List<GeneratorOption> options, GeneratorOption option) {
        for (GeneratorOption existing : options) {
            if (safeText(existing.label()).equalsIgnoreCase(safeText(option.label()))) {
                return;
            }
        }
        options.add(option);
    }

    private GeneratorOption generatorOptionByLabel(List<GeneratorOption> options, String label) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        for (GeneratorOption option : options) {
            if (safeText(option.label()).equalsIgnoreCase(safeText(label))) {
                return option;
            }
        }
        return options.getFirst();
    }

    private void applyGeneratorOption(GeneratorOption option, TextInputWidget generatorConfig) {
        if (generatorConfig == null) {
            return;
        }
        boolean configurable = option != null && option.configurable();
        generatorConfig.active = configurable;
        if (!generatorConfig.isFocused() || !configurable) {
            generatorConfig.setText(configurable ? safeText(option.config()) : "");
        }
    }

    private final class GeneratorOption {
        private final String label;
        private final String generator;
        private final String config;
        private final boolean configurable;

        private GeneratorOption(String label, String generator, String config, boolean configurable) {
            this.label = label;
            this.generator = generator;
            this.config = config;
            this.configurable = configurable;
        }

        private String label() {
            return label;
        }

        private String generator() {
            return generator;
        }

        private String config() {
            return config;
        }

        private boolean configurable() {
            return configurable;
        }
    }

    private void showCloneWorldPopup(String sourceWorld) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Clone World")
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(400, 135);
        TextInputWidget worldInput = new TextInputWidget.Builder().placeholder("Target World").size(220, 18).build();
        ToggleWidget loadAfter = detailToggle("Load After", true, 100);
        builder.addRow("Target", true, 18, worldInput);
        builder.addRow("", true, 18, loadAfter);
        PopupWidget[] popupRef = new PopupWidget[1];
        IconButton cloneButton = new IconButton.Builder()
            .label("Clone")
            .imagePath("copy.png")
            .accentType(ThemeManager.getAccent("nice"))
            .size(110, 20)
            .onClick(() -> {
                String targetWorld = safeText(worldInput.getText()).trim();
                if (!WorldUiSupport.isValidSimpleId(targetWorld)) {
                    new Notification("World", "Invalid World Name", Notification.Type.ERROR);
                    return;
                }
                if (WorldUiSupport.containsIgnoreCase(worldNameOptions(), targetWorld)) {
                    new Notification("World", "World Exists", Notification.Type.ERROR);
                    return;
                }
                worldManager().cloneWorld(serverId, sourceWorld, targetWorld, loadAfter.getValue());
                selectedWorldName = targetWorld;
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, cloneButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showWorldUnloadPopup(String worldName) {
        showFallbackPopup(worldName, fallbackWorld -> worldManager().unloadWorld(serverId, worldName, fallbackWorld));
    }

    private void showWorldDeletePopup(String worldName) {
        FlowManager manager = worldManager();
        if (manager == null) {
            return;
        }
        List<String> fallbackOptions = fallbackWorldOptions(worldName);
        PopupWidget.Builder builder = fallbackBuilder("Delete World", worldName, 170);
        ToggleWidget deleteFiles = detailToggle("Delete Files", false, 115);
        TextInputWidget fallbackInput = new TextInputWidget.Builder()
            .text(selectDefaultFallbackWorld(worldName, fallbackOptions))
            .placeholder("Fallback World")
            .size(220, 18)
            .build();
        builder.addRow("Files", true, 18, deleteFiles);
        builder.addRow("Fallback", true, 18, fallbackInput);
        PopupWidget[] popupRef = new PopupWidget[1];
        IconButton deleteButton = new IconButton.Builder()
            .label("Delete")
            .imagePath("delete.png")
            .accentType(ThemeManager.getAccent("danger"))
            .size(110, 20)
            .onClick(() -> {
                String fallbackWorld = safeText(fallbackInput.getText()).trim();
                if (!WorldUiSupport.containsIgnoreCase(fallbackOptions, fallbackWorld)) {
                    new Notification("World", "Unknown Fallback World", Notification.Type.ERROR);
                    return;
                }
                manager.deleteWorld(serverId, worldName, deleteFiles.getValue(), fallbackWorld);
                if (worldName.equalsIgnoreCase(selectedWorldName)) {
                    selectedWorldName = fallbackWorld;
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, deleteButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showFallbackPopup(String worldName, Consumer<String> action) {
        List<String> fallbackOptions = fallbackWorldOptions(worldName);
        PopupWidget.Builder builder = fallbackBuilder("Unload World", worldName, 130);
        TextInputWidget fallbackInput = new TextInputWidget.Builder()
            .text(selectDefaultFallbackWorld(worldName, fallbackOptions))
            .placeholder("Fallback World")
            .size(220, 18)
            .build();
        builder.addRow("Fallback", true, 18, fallbackInput);
        PopupWidget[] popupRef = new PopupWidget[1];
        IconButton actionButton = new IconButton.Builder()
            .label("Unload")
            .imagePath("hide.png")
            .accentType(ThemeManager.getAccent("danger"))
            .size(110, 20)
            .onClick(() -> {
                String fallbackWorld = safeText(fallbackInput.getText()).trim();
                if (!WorldUiSupport.containsIgnoreCase(fallbackOptions, fallbackWorld)) {
                    new Notification("World", "Unknown Fallback World", Notification.Type.ERROR);
                    return;
                }
                action.accept(fallbackWorld);
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, actionButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private PopupWidget.Builder fallbackBuilder(String title, String worldName, int height) {
        return new PopupWidget.Builder(title + " | " + worldName)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(430, height);
    }

    private void showWorldPurgePopup(String worldName) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Purge Entities")
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(420, 190);
        ToggleWidget monsters = detailToggle("Monsters", true, 96);
        ToggleWidget animals = detailToggle("Animals", false, 90);
        ToggleWidget ambient = detailToggle("Ambient", false, 90);
        ToggleWidget misc = detailToggle("Misc", false, 80);
        ToggleWidget vehicles = detailToggle("Vehicles", false, 92);
        ToggleWidget items = detailToggle("Items", false, 80);
        builder.addRow("Types", true, 18, monsters, animals, ambient);
        builder.addRow("More", true, 18, misc, vehicles, items);
        PopupWidget[] popupRef = new PopupWidget[1];
        IconButton purgeButton = new IconButton.Builder()
            .label("Purge")
            .imagePath("delete.png")
            .accentType(ThemeManager.getAccent("danger"))
            .size(110, 20)
            .onClick(() -> {
                if (!monsters.getValue() && !animals.getValue() && !ambient.getValue() && !misc.getValue() && !vehicles.getValue() && !items.getValue()) {
                    new Notification("World", "Select Purge Types", Notification.Type.ERROR);
                    return;
                }
                worldManager().purgeWorld(serverId, worldName, monsters.getValue(), animals.getValue(), ambient.getValue(), misc.getValue(), vehicles.getValue(), items.getValue());
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, purgeButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showWorldTeleportPopup(String worldName) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Teleport Player")
            .onClose(this::closePlayerSelector)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(440, 205);
        String[] selectedPlayer = {""};
        AnimatedButton playerButton = new AnimatedButton.Builder()
            .label("Select Player")
            .size(220, 18)
            .entranceAnimation(false)
            .build();
        playerButton.setAction(() -> showPlayerSelector(playerButton, selectedPlayer[0], player -> {
            selectedPlayer[0] = player;
            playerButton.setMessage(player.isBlank() ? "Select Player" : player);
        }));
        TextInputWidget xInput = new TextInputWidget.Builder().placeholder("X").size(82, 18).build();
        TextInputWidget yInput = new TextInputWidget.Builder().placeholder("Y").size(82, 18).build();
        TextInputWidget zInput = new TextInputWidget.Builder().placeholder("Z").size(82, 18).build();
        TextInputWidget yawInput = new TextInputWidget.Builder().placeholder("Yaw").size(82, 18).build();
        TextInputWidget pitchInput = new TextInputWidget.Builder().placeholder("Pitch").size(82, 18).build();
        builder.addRow("Player", true, 18, playerButton);
        builder.addRow("Position", true, 18, xInput, yInput, zInput);
        builder.addRow("Rotation", true, 18, yawInput, pitchInput);
        PopupWidget[] popupRef = new PopupWidget[1];
        IconButton spawnButton = new IconButton.Builder()
            .label("Spawn")
            .imagePath("earth.png")
            .size(105, 20)
            .onClick(() -> {
                String playerName = safeText(selectedPlayer[0]).trim();
                if (playerName.isBlank()) {
                    new Notification("World", "Player Required", Notification.Type.ERROR);
                    return;
                }
                worldManager().teleportPlayerToWorldSpawn(serverId, playerName, worldName);
                closePlayerSelector();
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        IconButton teleportButton = new IconButton.Builder()
            .label("Teleport")
            .imagePath("steve.png")
            .accentType(ThemeManager.getAccent("nice"))
            .size(120, 20)
            .onClick(() -> {
                String playerName = safeText(selectedPlayer[0]).trim();
                if (playerName.isBlank()) {
                    new Notification("World", "Player Required", Notification.Type.ERROR);
                    return;
                }
                if (!WorldUiSupport.isCompleteOrBlank(xInput.getText(), yInput.getText(), zInput.getText())
                    || !WorldUiSupport.isCompleteOrBlank(yawInput.getText(), pitchInput.getText())) {
                    new Notification("World", "Complete Coordinates", Notification.Type.ERROR);
                    return;
                }
                worldManager().teleportPlayerToWorld(serverId, playerName, worldName, parseNullableDouble(xInput.getText()),
                    parseNullableDouble(yInput.getText()), parseNullableDouble(zInput.getText()), parseNullableFloat(yawInput.getText()),
                    parseNullableFloat(pitchInput.getText()));
                closePlayerSelector();
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, spawnButton, teleportButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showPlayerSelector(AnimatedWidget anchor, String selected, Consumer<String> onSelected) {
        FlowManager manager = worldManager();
        if (manager == null || anchor == null || onSelected == null) {
            return;
        }
        closePlayerSelector();
        manager.requestPlayerTrackingSnapshot(serverId);
        List<PlayerDossier> players = manager.getOnlinePlayersForServer(serverId);
        var overlay = ScreenManager.getInstance().getPopupOverlay();
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(overlay)
            .size(220, 240)
            .dismissOnSelect(true)
            .emptyMessage("No Players")
            .onClose(() -> closePlayerSelector(selectorRef[0]))
            .build();
        selector.setLayer(900);
        selector.setPriority(30);
        selectorRef[0] = selector;
        activePlayerSelector = selector;
        for (PlayerDossier player : players) {
            if (player == null || safeText(player.getPlayerName()).isBlank()) {
                continue;
            }
            String name = player.getPlayerName();
            String uuid = safeText(player.getPlayerId());
            selector.addItem(name, uuid, name + " " + uuid, () -> onSelected.accept(name));
        }
        selector.setSelectedItem(selected);
        overlay.addDrawableChild(selector);
        selector.show(anchor.getX(), anchor.getY() + anchor.getHeight());
    }

    private void closePlayerSelector() {
        closePlayerSelector(activePlayerSelector);
    }

    private void closePlayerSelector(ItemSelectorWidget selector) {
        if (selector == null) {
            return;
        }
        selector.onClose = null;
        selector.hide();
        ScreenManager.getInstance().getPopupOverlay().remove(selector);
        if (selector == activePlayerSelector) {
            activePlayerSelector = null;
        }
    }

    private void showInventoryGroupsPopup() {
        FlowManager manager = worldManager();
        if (manager == null) {
            return;
        }
        clearDetailWidgets();
        detailForm = null;
        List<WorldInventoryGroup> groups = new ArrayList<>(manager.getWorldInventoryGroupsForServer(serverId));
        groups.sort(Comparator.comparing(group -> safeText(group == null ? "" : group.getGroupId()), String.CASE_INSENSITIVE_ORDER));
        int rowWidth = Math.max(220, detailPane.getWidth() - 18);
        IconButton createButton = new IconButton.Builder()
            .label("Create")
            .imagePath("create.png")
            .accentType(ThemeManager.getAccent("nice"))
            .size(96, 18)
            .entranceAnimation(false)
            .onClick(() -> showInventoryGroupPopup(null))
            .build();
        IconButton closeButton = new IconButton.Builder()
            .label("Close")
            .imagePath("close.png")
            .size(86, 18)
            .entranceAnimation(false)
            .onClick(this::refreshDetails)
            .build();
        addDetailWidget(row("Groups", rowWidth, createButton, closeButton));
        if (groups.isEmpty()) {
            addDetailWidget(row("Saved Groups", rowWidth, readOnlyButton("No Groups")));
        } else {
            for (WorldInventoryGroup group : groups) {
                if (group == null || safeText(group.getGroupId()).isBlank()) {
                    continue;
                }
                IconButton label = readOnlyButton(inventoryGroupSummary(group));
                SquareButtonWidget edit = new SquareButtonWidget.Builder()
                    .imagePath("edit.png")
                    .hint("Edit Group")
                    .onClick(() -> showInventoryGroupPopup(group))
                    .build();
                SquareButtonWidget delete = new SquareButtonWidget.Builder()
                    .imagePath("delete.png")
                    .hint("Delete Group")
                    .accentType(ThemeManager.getAccent("danger"))
                    .onClick(() -> {
                        manager.deleteInventoryGroup(serverId, group.getGroupId());
                        showInventoryGroupsPopup();
                    })
                    .build();
                addDetailWidget(row(group.getGroupId(), rowWidth, label, edit, delete));
            }
        }
        detailPane.updateWidgetPositions();
    }

    private String inventoryGroupSummary(WorldInventoryGroup group) {
        String display = safeText(group.getDisplayName()).isBlank() ? group.getGroupId() : group.getDisplayName();
        List<String> worlds = group.getWorlds() == null ? List.of() : group.getWorlds();
        return display + " | " + worlds.size() + " Worlds | " + summarizeInventoryGroupShares(group);
    }

    private String summarizeInventoryGroupShares(WorldInventoryGroup group) {
        List<String> parts = new ArrayList<>();
        if (group.isShareInventory()) parts.add("Inventory");
        if (group.isShareArmor()) parts.add("Armor");
        if (group.isShareOffhand()) parts.add("Offhand");
        if (group.isShareEnderChest()) parts.add("Ender Chest");
        if (group.isShareHealth()) parts.add("Health");
        if (group.isShareHunger()) parts.add("Hunger");
        if (group.isShareExperience()) parts.add("Experience");
        if (group.isShareGameMode()) parts.add("Game Mode");
        if (group.isSharePotionEffects()) parts.add("Potions");
        if (group.isShareLastLocation()) parts.add("Last Location");
        if (group.isShareBedSpawn()) parts.add("Bed Spawn");
        return parts.isEmpty() ? "No Shared State" : String.join(", ", parts);
    }

    private void showInventoryGroupPopup(WorldInventoryGroup existingGroup) {
        FlowManager manager = worldManager();
        if (manager == null) {
            return;
        }
        boolean editing = existingGroup != null;
        clearDetailWidgets();
        detailForm = null;
        int rowWidth = Math.max(220, detailPane.getWidth() - 18);
        TextInputWidget displayName = new TextInputWidget.Builder()
            .text(editing ? safeText(existingGroup.getDisplayName()) : "")
            .placeholder("Group Name")
            .forcePlaceholder(false)
            .size(Math.max(220, rowWidth - 12), 20)
            .build();
        displayName.entranceAnimationEnabled = false;
        Set<String> selectedWorlds = new LinkedHashSet<>(editing ? existingGroup.getWorlds() : defaultGroupWorldSelection());
        ToggleWidget inventory = detailToggle("Inventory", !editing || existingGroup.isShareInventory(), 92);
        ToggleWidget armor = detailToggle("Armor", !editing || existingGroup.isShareArmor(), 80);
        ToggleWidget offhand = detailToggle("Offhand", !editing || existingGroup.isShareOffhand(), 88);
        ToggleWidget enderChest = detailToggle("Ender Chest", !editing || existingGroup.isShareEnderChest(), 110);
        ToggleWidget health = detailToggle("Health", !editing || existingGroup.isShareHealth(), 82);
        ToggleWidget hunger = detailToggle("Hunger", !editing || existingGroup.isShareHunger(), 86);
        ToggleWidget experience = detailToggle("Experience", !editing || existingGroup.isShareExperience(), 105);
        ToggleWidget gameMode = detailToggle("Game Mode", !editing || existingGroup.isShareGameMode(), 105);
        ToggleWidget potions = detailToggle("Potions", !editing || existingGroup.isSharePotionEffects(), 90);
        ToggleWidget lastLocation = detailToggle("Last Location", !editing || existingGroup.isShareLastLocation(), 115);
        ToggleWidget bedSpawn = detailToggle("Bed Spawn", !editing || existingGroup.isShareBedSpawn(), 100);
        addDetailWidget(row(editing ? "Edit Group" : "Create Group", rowWidth, displayName));
        addInventoryGroupWorldRow(rowWidth, selectedWorlds);
        addDetailWidget(row("Inventory", rowWidth, inventory, armor, offhand, enderChest));
        addDetailWidget(row("Player State", rowWidth, health, hunger, experience, gameMode));
        addDetailWidget(row("Location", rowWidth, potions, lastLocation, bedSpawn));
        IconButton save = new IconButton.Builder()
            .label(editing ? "Save" : "Create")
            .imagePath("save.png")
            .accentType(ThemeManager.getAccent("nice"))
            .size(90, 18)
            .entranceAnimation(false)
            .onClick(() -> {
                String name = safeText(displayName.getText()).trim();
                String id = editing ? safeText(existingGroup.getGroupId()).trim() : inventoryGroupIdFromName(name);
                if (!editing && name.isBlank()) {
                    new Notification("World", "Group Name Required", Notification.Type.ERROR);
                    return;
                }
                if (!WorldUiSupport.isValidSimpleId(id)) {
                    new Notification("World", "Invalid Group Name", Notification.Type.ERROR);
                    return;
                }
                if (!editing && manager.getWorldInventoryGroup(serverId, id) != null) {
                    new Notification("World", "Group Exists", Notification.Type.ERROR);
                    return;
                }
                WorldInventoryGroup group = new WorldInventoryGroup();
                group.setGroupId(id);
                group.setDisplayName(name);
                group.setWorlds(new ArrayList<>(selectedWorlds));
                group.setShareInventory(inventory.getValue());
                group.setShareArmor(armor.getValue());
                group.setShareOffhand(offhand.getValue());
                group.setShareEnderChest(enderChest.getValue());
                group.setShareHealth(health.getValue());
                group.setShareHunger(hunger.getValue());
                group.setShareExperience(experience.getValue());
                group.setShareGameMode(gameMode.getValue());
                group.setSharePotionEffects(potions.getValue());
                group.setShareLastLocation(lastLocation.getValue());
                group.setShareBedSpawn(bedSpawn.getValue());
                if (editing) {
                    manager.updateInventoryGroup(serverId, group);
                } else {
                    manager.createInventoryGroup(serverId, group);
                }
                showInventoryGroupsPopup();
            })
            .build();
        IconButton cancel = new IconButton.Builder()
            .label("Back")
            .imagePath("close.png")
            .size(84, 18)
            .entranceAnimation(false)
            .onClick(this::showInventoryGroupsPopup)
            .build();
        addDetailWidget(row("Actions", rowWidth, save, cancel));
        detailPane.updateWidgetPositions();
    }

    private void addInventoryGroupWorldRow(int rowWidth, Set<String> selectedWorlds) {
        List<String> worlds = worldNameOptions();
        if (worlds.isEmpty()) {
            addDetailWidget(row("Worlds", rowWidth, readOnlyButton("No Worlds")));
            return;
        }
        int buttonWidth = Math.max(110, (rowWidth - 24) / 3);
        List<ToggleWidget> rowToggles = new ArrayList<>();
        for (String world : worlds) {
            ToggleWidget toggle = new ToggleWidget.Builder()
                .label(world)
                .toggled(containsIgnoreCase(selectedWorlds, world))
                .size(buttonWidth, 20)
                .entranceAnimation(false)
                .onChange(value -> {
                    if (value) {
                        selectedWorlds.add(world);
                    } else {
                        selectedWorlds.removeIf(selected -> selected.equalsIgnoreCase(world));
                    }
                })
                .build();
            rowToggles.add(toggle);
            if (rowToggles.size() == 3) {
                addDetailWidget(row("Worlds", rowWidth, rowToggles.toArray(new AnimatedWidget[0])));
                rowToggles.clear();
            }
        }
        if (!rowToggles.isEmpty()) {
            addDetailWidget(row("Worlds", rowWidth, rowToggles.toArray(new AnimatedWidget[0])));
        }
    }

    private String inventoryGroupIdFromName(String name) {
        String id = safeText(name).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        return id.isBlank() ? "group" : id;
    }

    private List<String> defaultGroupWorldSelection() {
        return safeText(selectedWorldName).isBlank() ? List.of() : List.of(selectedWorldName);
    }

    @Override
    public void handleWorldOperationResult(WorldOperationResult result) {
        String action = safeText(result.getAction()).trim().toLowerCase(Locale.ROOT);
        if ("deleteworld".equals(action)) {
            String worldName = resultWorldName(result);
            String key = findEntryKeyIgnoreCase(entries, worldName);
            if (key != null) {
                WorldEntryRow row = entries.remove(key);
                if (row != null) {
                    worldsList.removeWidget(row.widget);
                }
            }
        }
        refreshWorlds();
    }

    private List<String> onlinePlayerNames() {
        FlowManager manager = worldManager();
        return manager == null ? List.of() : WorldUiSupport.normalizeUniqueEntries(manager.getOnlinePlayerNamesForServer(serverId));
    }

    private List<String> worldNameOptions() {
        FlowManager manager = worldManager();
        if (manager == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(manager.getWorldsForServer(serverId).keySet());
        names = WorldUiSupport.normalizeUniqueEntries(names);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private List<String> inventoryGroupOptions() {
        FlowManager manager = worldManager();
        if (manager == null) {
            return List.of("No Group");
        }
        List<String> groups = new ArrayList<>();
        groups.add("No Group");
        for (WorldInventoryGroup group : manager.getWorldInventoryGroupsForServer(serverId)) {
            if (group != null && !safeText(group.getGroupId()).isBlank()) {
                groups.add(group.getGroupId());
            }
        }
        return WorldUiSupport.normalizeUniqueEntries(groups);
    }

    private List<String> fallbackWorldOptions(String worldName) {
        List<String> options = new ArrayList<>(worldNameOptions());
        options.removeIf(option -> option != null && option.equalsIgnoreCase(worldName));
        return options;
    }

    private String selectDefaultFallbackWorld(String worldName, List<String> fallbackOptions) {
        if (fallbackOptions == null || fallbackOptions.isEmpty()) {
            return "";
        }
        WorldRegistryEntry world = worldManager() != null ? worldManager().getWorld(serverId, worldName) : null;
        WorldProfileSettings profile = world == null ? null : world.getProfileSettings();
        List<String> preferred = new ArrayList<>();
        if (profile != null) {
            preferred.add(profile.getRespawnWorld());
            preferred.add(profile.getLinkedOverworld());
        }
        preferred.add("world");
        preferred.add("overworld");
        for (String candidate : preferred) {
            for (String option : fallbackOptions) {
                if (option != null && option.equalsIgnoreCase(safeText(candidate))) {
                    return option;
                }
            }
        }
        return fallbackOptions.getFirst();
    }
    @Override
    public String getDesktopAppId() {
        return "world-designer";
    }

    @Override
    public String getDesktopAppTitle() {
        return "World Designer";
    }

    @Override
    public String getDesktopAppIconPath() {
        return "earth.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return DesktopWindowBehavior.SINGLETON;
    }

    @Override
    public boolean shouldForceSuperScreen() {
        return desktopMode && !(parent instanceof Screen);
    }

    public String worldName() {
        return worldName;
    }

    public String serverId() {
        return serverId;
    }

    @Override
    public <T extends Widget> T addDrawableChild(T widget) {
        if (parent instanceof Screen screen && screen != this) {
            screen.addDrawableChild(widget);
            return widget;
        }
        return super.addDrawableChild(widget);
    }

    @Override
    public void remove(Widget widget) {
        if (parent instanceof Screen screen && screen != this) {
            screen.remove(widget);
            return;
        }
        super.remove(widget);
    }

    private WorldDashboardEntry findWorldDashboard(String worldName) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || worldName == null) {
            return null;
        }
        for (WorldDashboardEntry entry : manager.getWorldDashboardForServer(serverId)) {
            if (entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(worldName)) {
                return entry;
            }
        }
        return null;
    }

    private String findEntryKeyIgnoreCase(Map<String, ?> entries, String target) {
        if (entries == null || target == null || target.isBlank()) {
            return null;
        }
        for (String key : entries.keySet()) {
            if (key != null && key.equalsIgnoreCase(target)) {
                return key;
            }
        }
        return null;
    }

    private String resultWorldName(WorldOperationResult result) {
        if (result == null) {
            return "";
        }
        if (result.getData() != null) {
            for (String key : List.of("worldName", "world")) {
                Object raw = result.getData().get(key);
                String value = raw == null ? "" : String.valueOf(raw).trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return safeText(result.getWorldName()).trim();
    }

    private IconButton readOnlyButton(String text) {
        IconButton button = new IconButton.Builder()
            .label(text)
            .autoWidthOnTextChange(true)
            .entranceAnimation(false)
            .build();
        button.active = false;
        return button;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Long parseNullableLong(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double parseNullableDouble(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Float parseNullableFloat(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0.0F;
        }
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}

