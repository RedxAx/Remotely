package redxax.oxy.remotely.worldgen.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowDebugController;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.ui.FlowGraphDesignerScreen;
import redxax.oxy.remotely.flow.ui.FlowNodeWidget;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenProjectSettings;
import redxax.oxy.remotely.worldgen.data.WorldGenStage;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.resync.worldgen.contract.WorldGenTargetVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class WorldGenEditorScreen extends FlowGraphDesignerScreen {
    private final String actualServerId;
    private final Screen parentScreen;
    private final WorldGenManager manager = WorldGenManager.getInstance();
    private WorldGenProject project;
    private final String previewId;
    private WorldGenStage activeStage = WorldGenStage.TERRAIN;
    private WorldGenContentBrowserWidget contentBrowser;
    private int activeStageNodeCount = -1;
    private String previewEnvironment = "NORMAL";
    private long previewSeed;
    private String previewPlayerUuid = "";
    private String previewPlayerName = "";
    private ItemSelectorWidget activePlayerSelector;

    public WorldGenEditorScreen(String serverId, ClientServerView server, Screen parent) {
        this(serverId, server, parent, null);
    }

    public WorldGenEditorScreen(String serverId, ClientServerView server, Screen parent, WorldGenProject project) {
        super(initialGraph(serverId, project), serverId, parent);
        this.actualServerId = serverId;
        this.parentScreen = parent;
        this.project = project != null ? project : manager.getOrCreateProject(serverId);
        this.previewId = "worldgen_" + sanitizePreviewId(serverId);
    }

    private static FlowGraph initialGraph(String serverId, WorldGenProject project) {
        WorldGenManager manager = WorldGenManager.getInstance();
        manager.ensureLocalDefinitions(serverId);
        WorldGenProject targetProject = project != null ? project : manager.getOrCreateProject(serverId);
        return manager.toFlowGraph(targetProject.graph(WorldGenStage.TERRAIN));
    }

    public String getActualServerId() {
        return actualServerId;
    }

    @Override
    public void init() {
        super.init();
        manager.requestRegistry(actualServerId);
        manager.requestProjectList(actualServerId);
        if (contentBrowser == null) {
            contentBrowser = new WorldGenContentBrowserWidget(this);
        }
        contentBrowser.rebuild(activeStage);
    }

    @Override
    public void tick() {
        super.tick();
        if (contentBrowser != null) {
            contentBrowser.layout();
            if (paletteSidePanel != null) {
                paletteSidePanel.horizontalOffset(contentBrowser.visibleLayoutWidth() + 8);
            }
            if (activeStageNodeCount != graph.getNodes().size()) {
                contentBrowser.rebuild(activeStage);
                activeStageNodeCount = graph.getNodes().size();
            }
        }
    }

    @Override
    public String getDesktopAppId() {
        return "worldgen-editor";
    }

    @Override
    public String getDesktopAppTitle() {
        return "World Generation";
    }

    @Override
    public String getDesktopAppIconPath() {
        return "node.png";
    }

    @Override
    protected boolean showExtractButton() {
        return false;
    }

    @Override
    protected boolean showDebugControls() {
        return false;
    }

    @Override
    protected FlowDebugController debugController() {
        return null;
    }

    @Override
    protected void addCustomHeaderButtons() {
    }

    @Override
    protected boolean useStrictTypeCompatibility() {
        return true;
    }

    @Override
    protected boolean allowFlowPins() {
        return false;
    }

    @Override
    protected boolean canConnect(FlowNodeWidget sourceWidget, String sourcePin, FlowNodeWidget targetWidget, String targetPin) {
        return super.canConnect(sourceWidget, sourcePin, targetWidget, targetPin);
    }

    @Override
    protected Map<String, Object> optionCatalogContext() {
        return Map.of(WorldGenTargetVersion.OPTION_CONTEXT_KEY, project.getSettings().getTargetVersion());
    }

    void switchStage(WorldGenStage stage) {
        if (stage == null || stage == activeStage) {
            return;
        }
        syncProjectGraph();
        activeStage = stage;
        FlowGraph target = manager.toFlowGraph(project.graph(stage));
        replaceGraph(target);
        activeStageNodeCount = target.getNodes().size();
        contentBrowser.rebuild(activeStage);
    }

    int stageNodeCount(WorldGenStage stage) {
        return stage == activeStage ? graph.getNodes().size() : project.graph(stage).getNodes().size();
    }

    @Override
    protected void saveGraph() {
        syncProjectGraph();
        manager.saveWorldGen(actualServerId, project);
    }

    public void refreshStatus() {
    }

    void showSettingsPopup() {
        WorldGenProjectSettings settings = project.getSettings();
        PopupWidget.Builder builder = new PopupWidget.Builder("World Settings").setResizable(false);
        DropDownWidget<String> targetVersion = new DropDownWidget.Builder<>(WorldGenTargetVersion.supportedIds())
            .size(220, 18)
            .selectedItem(settings.getTargetVersion())
            .maxVisibleItems(10)
            .build();
        TextInputWidget minimumY = settingsInput(settings.getMinY());
        TextInputWidget maximumY = settingsInput(settings.getMaxY());
        TextInputWidget seaLevel = settingsInput(settings.getSeaLevel());
        DropDownWidget<String> structureSafety = new DropDownWidget.Builder<>(List.of("Enabled", "Disabled"))
            .size(220, 18)
            .selectedItem(settings.isVanillaStructureTerrainSafety() ? "Enabled" : "Disabled")
            .build();
        TextInputWidget structureRadius = settingsInput(settings.getVanillaStructureSampleRadius());
        TextInputWidget structureHeightDelta = settingsInput(settings.getVanillaStructureMaxHeightDelta());
        builder.addRow("Minecraft", targetVersion);
        builder.addRow("Minimum Y", minimumY);
        builder.addRow("Maximum Y", maximumY);
        builder.addRow("Sea Level", seaLevel);
        builder.addRow("Structure Safety", structureSafety);
        builder.addRow("Safety Radius", structureRadius);
        builder.addRow("Maximum Height Difference", structureHeightDelta);
        PopupWidget[] popupRef = new PopupWidget[1];
        builder.addTitleAction("Save", () -> {
            settings.setTargetVersion(safeText(targetVersion.getSelectedItem()));
            settings.setMinY((int) parseLong(minimumY.getText(), settings.getMinY()));
            settings.setMaxY((int) parseLong(maximumY.getText(), settings.getMaxY()));
            settings.setSeaLevel((int) parseLong(seaLevel.getText(), settings.getSeaLevel()));
            settings.setVanillaStructureTerrainSafety("Enabled".equals(structureSafety.getSelectedItem()));
            settings.setVanillaStructureSampleRadius((int) parseLong(structureRadius.getText(), settings.getVanillaStructureSampleRadius()));
            settings.setVanillaStructureMaxHeightDelta((int) parseLong(structureHeightDelta.getText(), settings.getVanillaStructureMaxHeightDelta()));
            syncProjectGraph();
            manager.saveWorldGen(actualServerId, project);
            refreshNodeRegistry();
            if (popupRef[0] != null) {
                popupRef[0].hide();
            }
        }, PopupWidget.TitleActionRole.PRIMARY);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private TextInputWidget settingsInput(int value) {
        TextInputWidget input = new TextInputWidget.Builder()
            .placeholder("Value")
            .size(220, 18)
            .build();
        input.setText(String.valueOf(value));
        return input;
    }

    void showPreviewPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Preview")
            .onClose(this::closePlayerSelector)
            .setResizable(false);
        TextInputWidget seedInput = new TextInputWidget.Builder()
            .placeholder("Seed")
            .size(220, 18)
            .build();
        seedInput.setText(String.valueOf(previewSeed));

        DropDownWidget<String> environmentSelect = new DropDownWidget.Builder<>(List.of("NORMAL", "NETHER", "THE_END", "CUSTOM"))
            .size(220, 18)
            .selectedItem(previewEnvironment)
            .build();

        AnimatedButton playerButton = new AnimatedButton.Builder()
            .label(previewPlayerName.isBlank() ? "No Player" : previewPlayerName)
            .size(220, 18)
            .entranceAnimation(false)
            .build();
        playerButton.setAction(() -> showPlayerSelector(playerButton, player -> {
            previewPlayerName = player.name();
            previewPlayerUuid = player.uuid();
            playerButton.setMessage(previewPlayerName.isBlank() ? "No Player" : previewPlayerName);
        }));

        builder.addRow("Seed", seedInput);
        builder.addRow("Environment", environmentSelect);
        builder.addRow("Player", playerButton);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton startButton = new AnimatedButton.Builder()
            .label("Preview")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                previewEnvironment = safeText(environmentSelect.getSelectedItem()).toUpperCase(Locale.ROOT);
                previewSeed = parseLong(seedInput.getText(), 0);
                previewCurrentGraph();
                closePlayerSelector();
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addTitleAction("Preview", () -> startButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showPlayerSelector(AnimatedButton anchor, Consumer<PlayerOption> onSelected) {
        if (anchor == null || onSelected == null) {
            return;
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null) {
            return;
        }
        closePlayerSelector();
        long snapshotRevision = flowManager.getPlayerTrackingSnapshotRevision(actualServerId);
        var overlay = ScreenManager.getInstance().getPopupOverlay();
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(overlay)
            .size(220, 240)
            .dismissOnSelect(true)
            .emptyMessage("No Players")
            .asyncItems(() -> flowManager.requestPlayerTrackingSnapshot(actualServerId),
                () -> previewPlayerSnapshot(flowManager, onSelected, snapshotRevision))
            .onClose(() -> closePlayerSelector(selectorRef[0]))
            .build();
        selector.setLayer(900);
        selector.setPriority(30);
        selectorRef[0] = selector;
        activePlayerSelector = selector;
        selector.setSelectedItem(previewPlayerName.isBlank() ? "No Player" : previewPlayerName);
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

    private List<PlayerOption> previewPlayerOptions(FlowManager flowManager) {
        List<PlayerOption> players = new ArrayList<>();
        players.add(new PlayerOption("No Player", "", ""));
        for (PlayerDossier dossier : flowManager.getOnlinePlayersForServer(actualServerId)) {
            if (dossier == null || safeText(dossier.getPlayerName()).isBlank() || safeText(dossier.getPlayerId()).isBlank()) {
                continue;
            }
            players.add(new PlayerOption(dossier.getPlayerName(), dossier.getPlayerId(), dossier.getPlayerName() + " " + dossier.getPlayerId()));
        }
        return players;
    }

    private ItemSelectorWidget.AsyncItemSnapshot previewPlayerSnapshot(FlowManager flowManager, Consumer<PlayerOption> onSelected, long snapshotRevision) {
        List<PlayerOption> players = previewPlayerOptions(flowManager);
        List<ItemSelectorWidget.AsyncItem> items = players.stream()
            .map(player -> new ItemSelectorWidget.AsyncItem(player.label(), player.uuid(), player.searchTerms(), () -> onSelected.accept(player)))
            .toList();
        boolean loading = players.size() == 1 && flowManager.getPlayerTrackingSnapshotRevision(actualServerId) == snapshotRevision;
        return new ItemSelectorWidget.AsyncItemSnapshot(items, loading, "No Players");
    }

    private record PlayerOption(String name, String uuid, String searchTerms) {
        private String label() {
            return name;
        }
    }

    void showProjectPopup() {
        manager.requestProjectList(actualServerId);
        PopupWidget.Builder builder = new PopupWidget.Builder("WorldGen Projects").setResizable(false);
        List<String> ids = manager.getProjectIds(actualServerId);
        DropDownWidget<String> projectSelect = new DropDownWidget.Builder<>(ids.isEmpty() ? List.of(project.getId()) : ids)
            .size(240, 18)
            .selectedItem(project.getId())
            .build();
        TextInputWidget projectIdInput = new TextInputWidget.Builder()
            .placeholder("Project ID")
            .size(240, 18)
            .build();
        projectIdInput.setText(project.getId());
        builder.addRow("Project", projectSelect);
        builder.addRow("Project ID", projectIdInput);
        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton newButton = new AnimatedButton.Builder()
            .label("New")
            .onClick(() -> {
                syncProjectGraph();
                project = manager.createProjectTemplate(manager.getProjectTemplates().getFirst(), null);
                projectIdInput.setText(project.getId());
                activeStage = WorldGenStage.TERRAIN;
                FlowGraph target = manager.toFlowGraph(project.graph(activeStage));
                replaceGraph(target);
                contentBrowser.rebuild(activeStage);
            })
            .build();
        AnimatedButton openButton = new AnimatedButton.Builder()
            .label("Open")
            .onClick(() -> {
                String selected = safeText(projectSelect.getSelectedItem()).trim();
                if (!selected.isBlank()) {
                    manager.requestProject(actualServerId, selected);
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        AnimatedButton duplicateButton = new AnimatedButton.Builder()
            .label("Duplicate")
            .onClick(() -> {
                syncProjectGraph();
                WorldGenProject copy = manager.copyProject(project);
                copy.setId(projectIdInput.getText().isBlank() ? UUID.randomUUID().toString() : projectIdInput.getText().trim());
                project = copy;
                manager.saveWorldGen(actualServerId, project);
                activeStage = WorldGenStage.TERRAIN;
                FlowGraph target = manager.toFlowGraph(project.graph(activeStage));
                replaceGraph(target);
                contentBrowser.rebuild(activeStage);
            })
            .build();
        AnimatedButton deleteButton = new AnimatedButton.Builder()
            .label("Delete")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                String selected = safeText(projectSelect.getSelectedItem()).trim();
                if (!selected.isBlank()) {
                    manager.deleteProject(actualServerId, selected);
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addTitleAction("New", () -> newButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.SECONDARY);
        builder.addTitleAction("Open", () -> openButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        builder.addTitleAction("Duplicate", () -> duplicateButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.SECONDARY);
        builder.addTitleAction("Delete", () -> deleteButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.DESTRUCTIVE);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    public void loadProject(WorldGenProject project) {
        if (project == null) {
            return;
        }
        syncProjectGraph();
        this.project = project;
        activeStage = WorldGenStage.TERRAIN;
        FlowGraph target = manager.toFlowGraph(project.graph(activeStage));
        replaceGraph(target);
        contentBrowser.rebuild(activeStage);
    }

    private void previewCurrentGraph() {
        syncProjectGraph();
        manager.requestPreview(actualServerId, previewId, project, previewEnvironment, previewSeed, previewPlayerUuid);
    }

    private void syncProjectGraph() {
        syncNodePositions();
        project.setGraph(activeStage, manager.toWorldGenGraph(graph));
    }

    @Override
    protected int viewportFitLeft() {
        return super.viewportFitLeft();
    }

    @Override
    protected int viewportFitWidth() {
        return Math.max(1, super.viewportFitWidth() - (contentBrowser == null ? 0 : contentBrowser.visibleLayoutWidth() + 8));
    }

    @Override
    protected void renderAdditionalStudioPanels(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderAdditionalStudioPanels(context, mouseX, mouseY, delta);
        if (contentBrowser != null) {
            contentBrowser.render(context, mouseX, mouseY, delta);
        }
    }

    String stageDescription(WorldGenStage stage) {
        return switch (stage) {
            case TERRAIN -> "Shape land, oceans, height, and density";
            case BIOME -> "Route climate into biome behavior";
            case SURFACE -> "Paint top, filler, and material layers";
            case CAVE -> "Carve underground spaces and ravines";
            case FEATURE -> "Place ores, vegetation, trees, and lakes";
            case STRUCTURE -> "Control structures and placement safety";
            case SPAWN -> "Define biome spawn tables and group sizes";
        };
    }

    void stopPreview() {
        manager.stopPreview(actualServerId, previewId);
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String sanitizePreviewId(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return "local";
        }
        return serverId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        return contentBrowser != null && contentBrowser.mouseClicked(event) || super.mouseClicked(event);
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        return contentBrowser != null && contentBrowser.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        return contentBrowser != null && contentBrowser.mouseDragged(event) || super.mouseDragged(event);
    }

    @Override
    public boolean mouseScrolled(ReScrollEvent event) {
        return contentBrowser != null && contentBrowser.mouseScrolled(event) || super.mouseScrolled(event);
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        return contentBrowser != null && contentBrowser.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean textInput(ReTextInputEvent event) {
        return contentBrowser != null && contentBrowser.textInput(event) || super.textInput(event);
    }

    @Override
    public void close() {
        ScreenManager.getInstance().setScreen(parentScreen);
    }
}
