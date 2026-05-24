package redxax.oxy.remotely.worldgen.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenStage;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public class WorldGenEditorScreen extends FlowEditorScreen {
    private final String actualServerId;
    private final Screen parentScreen;
    private final WorldGenManager manager = WorldGenManager.getInstance();
    private WorldGenProject project;
    private final String previewId;
    private WorldGenStage activeStage = WorldGenStage.TERRAIN;
    private String previewEnvironment = "NORMAL";
    private long previewSeed;
    private String previewPlayerUuid = "";
    private String previewPlayerName = "";
    private ItemSelectorWidget activePlayerSelector;

    public WorldGenEditorScreen(String serverId, ClientServerView server, Screen parent) {
        this(serverId, server, parent, null);
    }

    public WorldGenEditorScreen(String serverId, ClientServerView server, Screen parent, WorldGenProject project) {
        super(initialGraph(serverId, project), WorldGenManager.registryServerId(serverId), parent);
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
    protected void addCustomHeaderButtons() {
        IconButton projectButton = new IconButton.Builder()
            .size(18, 18)
            .imagePath("folder.png")
            .onClick(this::showProjectPopup)
            .build();
        addHeaderButton(projectButton);

        for (WorldGenStage stage : WorldGenStage.values()) {
            IconButton tabButton = new IconButton.Builder()
                .size(0, 18)
                .label(stage.displayName())
                .centered(true)
                .autoWidthOnTextChange(true)
                .onClick(() -> switchStage(stage))
                .build();
            addHeaderButton(tabButton);
        }

        IconButton previewButton = new IconButton.Builder()
            .size(18, 18)
            .imagePath("start.png")
            .onClick(this::showPreviewPopup)
            .build();
        addHeaderButton(previewButton);
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
    protected void saveGraph() {
        syncProjectGraph();
        manager.saveWorldGen(actualServerId, project);
    }

    public void refreshStatus() {
    }

    private void showPreviewPopup() {
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

        builder.addRow("Seed", true, 20, seedInput);
        builder.addRow("Environment", true, 20, environmentSelect);
        builder.addRow("Player", true, 20, playerButton);

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
        builder.addRow("", true, 20, startButton);
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
        flowManager.requestPlayerTrackingSnapshot(actualServerId);
        List<PlayerOption> players = previewPlayerOptions(flowManager);
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
        for (PlayerOption player : players) {
            selector.addItem(player.label(), player.uuid(), player.searchTerms(), () -> onSelected.accept(player));
        }
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

    private record PlayerOption(String name, String uuid, String searchTerms) {
        private String label() {
            return name;
        }
    }

    private void showProjectPopup() {
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
        builder.addRow("Project", true, 20, projectSelect);
        builder.addRow("Project ID", true, 20, projectIdInput);
        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton newButton = new AnimatedButton.Builder()
            .label("New")
            .onClick(() -> {
                syncProjectGraph();
                project = manager.createProjectTemplate(manager.getProjectTemplates().getFirst(), null);
                projectIdInput.setText(project.getId());
                activeStage = WorldGenStage.TERRAIN;
                applyGraph(manager.toFlowGraph(project.graph(activeStage)));
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
                applyGraph(manager.toFlowGraph(project.graph(activeStage)));
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
        builder.addRow("", true, 20, newButton, openButton);
        builder.addRow("", true, 20, duplicateButton, deleteButton);
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
        applyGraph(manager.toFlowGraph(project.graph(activeStage)));
        refreshNodeRegistry();
    }

    private void previewCurrentGraph() {
        syncProjectGraph();
        manager.requestPreview(actualServerId, previewId, project, previewEnvironment, previewSeed, previewPlayerUuid);
    }

    private void switchStage(WorldGenStage stage) {
        if (stage == null || stage == activeStage) {
            return;
        }
        syncProjectGraph();
        activeStage = stage;
        applyGraph(manager.toFlowGraph(project.graph(activeStage)));
        refreshNodeRegistry();
    }

    private void syncProjectGraph() {
        syncNodePositions();
        project.setGraph(activeStage, manager.toWorldGenGraph(graph));
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
    public void close() {
        ScreenManager.getInstance().setScreen(parentScreen);
    }
}
