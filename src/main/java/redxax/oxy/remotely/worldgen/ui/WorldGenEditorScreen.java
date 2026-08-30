package redxax.oxy.remotely.worldgen.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowDebugController;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowJson;
import redxax.oxy.remotely.flow.ui.FlowGraphDesignerScreen;
import redxax.oxy.remotely.flow.ui.FlowNodeWidget;
import redxax.oxy.remotely.flow.ui.studio.ReSyncCollaborativeView;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenProjectSettings;
import redxax.oxy.remotely.worldgen.data.WorldGenSerializer;
import redxax.oxy.remotely.worldgen.data.WorldGenStage;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.JsonTreeParser;
import restudio.resync.flow.workspace.WorkspacePatch;
import restudio.resync.worldgen.contract.WorldGenGenerationMode;
import restudio.resync.worldgen.contract.WorldGenTargetVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class WorldGenEditorScreen extends FlowGraphDesignerScreen implements ReSyncCollaborativeView {
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
    private WorldGenNavigationPanel navigationPanel;
    private SettingsWidgets settingsWidgets;

    @Override
    public JsonObject collaborationDocument() {
        syncProjectGraph();
        return JsonTreeParser.parse(WorldGenSerializer.serializeProject(project)).getAsJsonObject();
    }

    @Override
    public void applyCollaborationDocument(JsonObject document, List<WorkspacePatch<JsonElement>> patches) {
        WorldGenProject incoming = WorldGenSerializer.deserializeProject(JsonTreeParser.write(document));
        WorldGenProjectSettings currentSettings = project.getSettings();
        project.setId(incoming.getId());
        project.setVersion(incoming.getVersion());
        project.setTerrainGraph(incoming.getTerrainGraph());
        project.setBiomeGraph(incoming.getBiomeGraph());
        project.setSurfaceGraph(incoming.getSurfaceGraph());
        project.setCaveGraph(incoming.getCaveGraph());
        project.setFeatureGraph(incoming.getFeatureGraph());
        project.setStructureGraph(incoming.getStructureGraph());
        project.setSpawnGraph(incoming.getSpawnGraph());
        copySettings(currentSettings, incoming.getSettings());
        project.setBiomeProfiles(incoming.getBiomeProfiles());
        applyCollaborativeGraph(manager.toFlowGraph(project.graph(activeStage)));
        syncSettingsWidgets(currentSettings);
        refreshStatus();
    }

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
        navigationPanel = new WorldGenNavigationPanel(this);
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
    public String collaborationScope() {
        return "worldgen-stage:" + activeStage.name().toLowerCase(Locale.ROOT);
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
        return Map.of(WorldGenTargetVersion.OPTION_CONTEXT_KEY, manager.targetVersion(actualServerId, project.getSettings().getTargetVersion()));
    }

    void switchStage(WorldGenStage stage) {
        if (stage == null || stage == activeStage) {
            return;
        }
        syncProjectGraph();
        activeStage = stage;
        FlowGraph target = manager.toFlowGraph(project.graph(stage));
        replaceGraph(target);
    }

    int stageNodeCount(WorldGenStage stage) {
        return stage == activeStage ? graph.getNodes().size() : project.graph(stage).getNodes().size();
    }

    WorldGenStage activeStage() {
        return activeStage;
    }

    String projectId() {
        return project.getId();
    }

    String generationMode() {
        return WorldGenGenerationMode.resolve(project.getSettings().getGenerationMode()).displayName();
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
        PopupWidget.Builder builder = new PopupWidget.Builder("World Settings").setResizable(false).onClose(() -> settingsWidgets = null);
        String automaticVersion = "Automatic · " + manager.targetVersion(actualServerId, WorldGenTargetVersion.AUTOMATIC);
        List<String> targetVersions = new ArrayList<>();
        targetVersions.add(automaticVersion);
        targetVersions.addAll(WorldGenTargetVersion.supportedIds());
        DropDownWidget<String> targetVersion = new DropDownWidget.Builder<>(targetVersions)
            .size(220, 18)
            .selectedItem(WorldGenTargetVersion.AUTOMATIC.equals(settings.getTargetVersion()) ? automaticVersion : settings.getTargetVersion())
            .maxVisibleItems(10)
            .onSelectionChanged(value -> updateSettings(() -> settings.setTargetVersion(value.startsWith("Automatic") ? WorldGenTargetVersion.AUTOMATIC : value)))
            .build();
        WorldGenGenerationMode generationMode = WorldGenGenerationMode.resolve(settings.getGenerationMode());
        AnimatedButton generation = new AnimatedButton.Builder().label(generationMode.displayName()).size(220, 18).active(false).build();
        DropDownWidget<String> terrainPreset = new DropDownWidget.Builder<>(manager.getProjectTemplates(WorldGenGenerationMode.VANILLA.displayName()))
            .size(220, 18)
            .selectedItem(switch (safeText(settings.getTerrainTemplate())) {
                case "amplified" -> "Amplified";
                case "large_biomes" -> "Large Biomes";
                default -> "Survival";
            })
            .onSelectionChanged(value -> updateSettings(() -> settings.setTerrainTemplate(switch (safeText(value)) {
                case "Amplified" -> "amplified";
                case "Large Biomes" -> "large_biomes";
                default -> "overworld";
            })))
            .build();
        TextInputWidget minimumY = settingsInput(settings.getMinY(), value -> settings.setMinY((int) parseLong(value, settings.getMinY())));
        TextInputWidget maximumY = settingsInput(settings.getMaxY(), value -> settings.setMaxY((int) parseLong(value, settings.getMaxY())));
        TextInputWidget seaLevel = settingsInput(settings.getSeaLevel(), value -> settings.setSeaLevel((int) parseLong(value, settings.getSeaLevel())));
        DropDownWidget<String> structureSafety = new DropDownWidget.Builder<>(List.of("Enabled", "Disabled"))
            .size(220, 18)
            .selectedItem(settings.isVanillaStructureTerrainSafety() ? "Enabled" : "Disabled")
            .onSelectionChanged(value -> updateSettings(() -> settings.setVanillaStructureTerrainSafety("Enabled".equals(value))))
            .build();
        TextInputWidget structureRadius = settingsInput(settings.getVanillaStructureSampleRadius(), value -> settings.setVanillaStructureSampleRadius((int) parseLong(value, settings.getVanillaStructureSampleRadius())));
        TextInputWidget structureHeightDelta = settingsInput(settings.getVanillaStructureMaxHeightDelta(), value -> settings.setVanillaStructureMaxHeightDelta((int) parseLong(value, settings.getVanillaStructureMaxHeightDelta())));
        List<String> vanillaPolicies = List.of("Keep Vanilla", "Replace Vanilla");
        DropDownWidget<String> vanillaFeatures = new DropDownWidget.Builder<>(vanillaPolicies).size(220, 18)
            .selectedItem(settings.isVanillaFeaturesEnabled() ? "Keep Vanilla" : "Replace Vanilla")
            .onSelectionChanged(value -> updateSettings(() -> settings.setVanillaFeaturesEnabled("Keep Vanilla".equals(value)))).build();
        DropDownWidget<String> vanillaStructures = new DropDownWidget.Builder<>(vanillaPolicies).size(220, 18)
            .selectedItem(settings.isVanillaStructuresEnabled() ? "Keep Vanilla" : "Replace Vanilla")
            .onSelectionChanged(value -> updateSettings(() -> settings.setVanillaStructuresEnabled("Keep Vanilla".equals(value)))).build();
        DropDownWidget<String> vanillaSpawns = new DropDownWidget.Builder<>(vanillaPolicies).size(220, 18)
            .selectedItem(settings.isVanillaSpawnsEnabled() ? "Keep Vanilla" : "Replace Vanilla")
            .onSelectionChanged(value -> updateSettings(() -> settings.setVanillaSpawnsEnabled("Keep Vanilla".equals(value)))).build();
        ReSyncStudioPanelState.identify(targetVersion, "worldgen-setting:target-version");
        ReSyncStudioPanelState.identify(terrainPreset, "worldgen-setting:terrain-preset");
        ReSyncStudioPanelState.identify(minimumY, "worldgen-setting:minimum-y");
        ReSyncStudioPanelState.identify(maximumY, "worldgen-setting:maximum-y");
        ReSyncStudioPanelState.identify(seaLevel, "worldgen-setting:sea-level");
        ReSyncStudioPanelState.identify(vanillaFeatures, "worldgen-setting:vanilla-features");
        ReSyncStudioPanelState.identify(vanillaStructures, "worldgen-setting:vanilla-structures");
        ReSyncStudioPanelState.identify(vanillaSpawns, "worldgen-setting:vanilla-spawns");
        ReSyncStudioPanelState.identify(structureSafety, "worldgen-setting:structure-safety");
        ReSyncStudioPanelState.identify(structureRadius, "worldgen-setting:structure-radius");
        ReSyncStudioPanelState.identify(structureHeightDelta, "worldgen-setting:structure-height-delta");
        settingsWidgets = new SettingsWidgets(targetVersion, terrainPreset, minimumY, maximumY, seaLevel, vanillaFeatures, vanillaStructures, vanillaSpawns,
            structureSafety, structureRadius, structureHeightDelta, automaticVersion);
        builder.addRow("Generation", generation);
        builder.addRow("Minecraft", targetVersion);
        if (generationMode == WorldGenGenerationMode.VANILLA) {
            builder.addRow("Terrain", terrainPreset);
        }
        builder.addRow("Minimum Y", minimumY);
        builder.addRow("Maximum Y", maximumY);
        builder.addRow("Sea Level", seaLevel);
        builder.addRow("Vanilla Features", vanillaFeatures);
        builder.addRow("Vanilla Structures", vanillaStructures);
        builder.addRow("Vanilla Spawns", vanillaSpawns);
        builder.addRow("Structure Safety", structureSafety);
        builder.addRow("Safety Radius", structureRadius);
        builder.addRow("Maximum Height Difference", structureHeightDelta);
        PopupWidget[] popupRef = new PopupWidget[1];
        builder.addTitleAction("Save", () -> {
            String selectedVersion = safeText(targetVersion.getSelectedItem());
            settings.setTargetVersion(selectedVersion.startsWith("Automatic") ? WorldGenTargetVersion.AUTOMATIC : selectedVersion);
            if (generationMode == WorldGenGenerationMode.VANILLA) {
                settings.setTerrainTemplate(switch (safeText(terrainPreset.getSelectedItem())) {
                    case "Amplified" -> "amplified";
                    case "Large Biomes" -> "large_biomes";
                    default -> "overworld";
                });
            }
            settings.setMinY((int) parseLong(minimumY.getText(), settings.getMinY()));
            settings.setMaxY((int) parseLong(maximumY.getText(), settings.getMaxY()));
            settings.setSeaLevel((int) parseLong(seaLevel.getText(), settings.getSeaLevel()));
            settings.setVanillaFeaturesEnabled("Keep Vanilla".equals(vanillaFeatures.getSelectedItem()));
            settings.setVanillaStructuresEnabled("Keep Vanilla".equals(vanillaStructures.getSelectedItem()));
            settings.setVanillaSpawnsEnabled("Keep Vanilla".equals(vanillaSpawns.getSelectedItem()));
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

    private TextInputWidget settingsInput(int value, Consumer<String> onChange) {
        TextInputWidget input = new TextInputWidget.Builder()
            .placeholder("Value")
            .size(220, 18)
            .onChange(next -> updateSettings(() -> onChange.accept(next)))
            .build();
        input.setText(String.valueOf(value));
        return input;
    }

    private void updateSettings(Runnable mutation) {
        mutation.run();
        markWorkspaceMutation();
        refreshStatus();
    }

    private void syncSettingsWidgets(WorldGenProjectSettings settings) {
        SettingsWidgets widgets = settingsWidgets;
        if (widgets == null) {
            return;
        }
        widgets.targetVersion().setSelectedItem(WorldGenTargetVersion.AUTOMATIC.equals(settings.getTargetVersion()) ? widgets.automaticVersion() : settings.getTargetVersion());
        widgets.terrainPreset().setSelectedItem(switch (safeText(settings.getTerrainTemplate())) {
            case "amplified" -> "Amplified";
            case "large_biomes" -> "Large Biomes";
            default -> "Survival";
        });
        widgets.minimumY().setText(String.valueOf(settings.getMinY()));
        widgets.maximumY().setText(String.valueOf(settings.getMaxY()));
        widgets.seaLevel().setText(String.valueOf(settings.getSeaLevel()));
        widgets.vanillaFeatures().setSelectedItem(settings.isVanillaFeaturesEnabled() ? "Keep Vanilla" : "Replace Vanilla");
        widgets.vanillaStructures().setSelectedItem(settings.isVanillaStructuresEnabled() ? "Keep Vanilla" : "Replace Vanilla");
        widgets.vanillaSpawns().setSelectedItem(settings.isVanillaSpawnsEnabled() ? "Keep Vanilla" : "Replace Vanilla");
        widgets.structureSafety().setSelectedItem(settings.isVanillaStructureTerrainSafety() ? "Enabled" : "Disabled");
        widgets.structureRadius().setText(String.valueOf(settings.getVanillaStructureSampleRadius()));
        widgets.structureHeightDelta().setText(String.valueOf(settings.getVanillaStructureMaxHeightDelta()));
    }

    private static void copySettings(WorldGenProjectSettings target, WorldGenProjectSettings source) {
        target.setSeedPolicy(source.getSeedPolicy());
        target.setMinY(source.getMinY());
        target.setMaxY(source.getMaxY());
        target.setSeaLevel(source.getSeaLevel());
        target.setDefaultBlock(source.getDefaultBlock());
        target.setDefaultFluid(source.getDefaultFluid());
        target.setDatapackNamespace(source.getDatapackNamespace());
        target.setGeneratorBackend(source.getGeneratorBackend());
        target.setGenerationMode(source.getGenerationMode());
        target.setTargetVersion(source.getTargetVersion());
        target.setWorldPreset(source.getWorldPreset());
        target.setTerrainTemplate(source.getTerrainTemplate());
        target.setVanillaBiomesEnabled(source.isVanillaBiomesEnabled());
        target.setVanillaFeaturesEnabled(source.isVanillaFeaturesEnabled());
        target.setVanillaStructuresEnabled(source.isVanillaStructuresEnabled());
        target.setVanillaSpawnsEnabled(source.isVanillaSpawnsEnabled());
        target.setVanillaStructureTerrainSafety(source.isVanillaStructureTerrainSafety());
        target.setVanillaStructureSampleRadius(source.getVanillaStructureSampleRadius());
        target.setVanillaStructureMaxHeightDelta(source.getVanillaStructureMaxHeightDelta());
        target.setBiomeVanillaFeatureOverrides(source.getBiomeVanillaFeatureOverrides());
        target.setPreviewEnvironment(source.getPreviewEnvironment());
        target.setActivePreviewPlayer(source.getActivePreviewPlayer());
    }

    private record SettingsWidgets(DropDownWidget<String> targetVersion, DropDownWidget<String> terrainPreset, TextInputWidget minimumY,
                                   TextInputWidget maximumY, TextInputWidget seaLevel, DropDownWidget<String> vanillaFeatures,
                                   DropDownWidget<String> vanillaStructures, DropDownWidget<String> vanillaSpawns,
                                   DropDownWidget<String> structureSafety, TextInputWidget structureRadius, TextInputWidget structureHeightDelta,
                                   String automaticVersion) {
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
        ReSyncStudioPanelState.identify(seedInput, "worldgen-preview:seed");
        ReSyncStudioPanelState.identify(environmentSelect, "worldgen-preview:environment");
        ReSyncStudioPanelState.identify(playerButton, "worldgen-preview:player");
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
        String projectCategory = generationMode();
        DropDownWidget<String> templateSelect = new DropDownWidget.Builder<>(manager.getProjectTemplates(projectCategory))
            .size(240, 18)
            .selectedItem(manager.getProjectTemplates(projectCategory).getFirst())
            .build();
        DropDownWidget<String> categorySelect = new DropDownWidget.Builder<>(manager.getProjectCategories())
            .size(240, 18)
            .selectedItem(projectCategory)
            .onSelectionChanged(category -> {
                List<String> templates = manager.getProjectTemplates(category);
                templateSelect.setItems(templates, templates.getFirst());
            })
            .build();
        ReSyncStudioPanelState.identify(projectSelect, "worldgen-project:project");
        ReSyncStudioPanelState.identify(projectIdInput, "worldgen-project:id");
        ReSyncStudioPanelState.identify(categorySelect, "worldgen-project:category");
        ReSyncStudioPanelState.identify(templateSelect, "worldgen-project:template");
        builder.addRow("Project", projectSelect);
        builder.addRow("Project ID", projectIdInput);
        builder.addRow("Category", categorySelect);
        builder.addRow("Template", templateSelect);
        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton newButton = new AnimatedButton.Builder()
            .label("New")
            .onClick(() -> {
                syncProjectGraph();
                project = manager.createProjectTemplate(safeText(categorySelect.getSelectedItem()), safeText(templateSelect.getSelectedItem()), null);
                projectIdInput.setText(project.getId());
                activeStage = WorldGenStage.TERRAIN;
                FlowGraph target = manager.toFlowGraph(project.graph(activeStage));
                replaceGraph(target);
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
            })
            .build();
        AnimatedButton deleteButton = new AnimatedButton.Builder()
            .label("Delete")
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
        return Math.max(1, super.viewportFitWidth() - (navigationPanel != null ? navigationPanel.layoutWidth() : 0));
    }

    @Override
    protected void renderAdditionalStudioPanels(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderAdditionalStudioPanels(context, mouseX, mouseY, delta);
        if (navigationPanel != null) {
            navigationPanel.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        if (navigationPanel != null) {
            navigationPanel.layout();
        }
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        if (navigationPanel != null && navigationPanel.mouseClicked(event)) {
            return true;
        }
        return super.mouseClicked(event);
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        if (navigationPanel != null && navigationPanel.mouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        if (navigationPanel != null && navigationPanel.mouseDragged(event)) {
            return true;
        }
        return super.mouseDragged(event);
    }

    @Override
    public boolean mouseScrolled(ReScrollEvent event) {
        if (navigationPanel != null && navigationPanel.mouseScrolled(event)) {
            return true;
        }
        return super.mouseScrolled(event);
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        if (navigationPanel != null && navigationPanel.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    String stageDescription(WorldGenStage stage) {
        if (WorldGenGenerationMode.resolve(project.getSettings().getGenerationMode()) == WorldGenGenerationMode.VANILLA) {
            return switch (stage) {
                case TERRAIN -> "Minecraft Shapes Terrain From The World Settings Preset";
                case BIOME -> "Minecraft Routes Its Native Biomes And Climate";
                case SURFACE -> "Minecraft Paints Native Surface Rules";
                case CAVE -> "Minecraft Carves Native Caves And Ravines";
                case FEATURE -> "Add Datapack Ores, Vegetation, Lakes, And Catalog Features";
                case STRUCTURE -> "Add Datapack Structures With Game-Owned Placement";
                case SPAWN -> "Edit Native Biome Spawn Tables And Group Sizes";
            };
        }
        return switch (stage) {
            case TERRAIN -> "Shape Land, Oceans, Height, And Density";
            case BIOME -> "Route Climate Into Biome Behavior";
            case SURFACE -> "Paint Top, Filler, And Material Layers";
            case CAVE -> "Carve Underground Spaces And Ravines";
            case FEATURE -> "Place Ores, Vegetation, Trees, And Lakes";
            case STRUCTURE -> "Control Structures And Placement Safety";
            case SPAWN -> "Define Biome Spawn Tables And Group Sizes";
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
        return FlowJson.text(value);
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
