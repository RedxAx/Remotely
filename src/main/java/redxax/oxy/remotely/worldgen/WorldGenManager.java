package redxax.oxy.remotely.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncFlowClient;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.FlowGraphDesignerScreen;
import redxax.oxy.remotely.worldgen.data.WorldGenConnection;
import redxax.oxy.remotely.worldgen.data.WorldGenGraph;
import redxax.oxy.remotely.worldgen.data.WorldGenNode;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenStage;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeDefinition;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeRegistry;
import redxax.oxy.remotely.worldgen.ui.WorldGenEditorScreen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldGenManager {
    private static final WorldGenManager INSTANCE = new WorldGenManager();
    private static final List<String> PROJECT_TEMPLATES = List.of("Continental", "Alpine", "Islands", "Badlands", "Frozen", "Caves");
    private final WorldGenProjectStore projectStore = new WorldGenProjectStore();
    private final WorldGenPreviewController previewController = new WorldGenPreviewController();
    private final Map<String, Object> capabilities = new ConcurrentHashMap<>();

    public static WorldGenManager getInstance() {
        return INSTANCE;
    }

    public static String registryServerId(String serverId) {
        return (serverId == null || serverId.isBlank() ? "local" : serverId) + ":worldgen";
    }

    public void clearCache(String serverId) {
        if (serverId != null && !serverId.isBlank()) {
            projectStore.clearServer(serverId);
        }
    }

    public WorldGenGraph getOrCreateGraph(String serverId) {
        return getOrCreateProject(serverId).getTerrainGraph();
    }

    public WorldGenProject getOrCreateProject(String serverId) {
        return projectStore.getOrCreateProject(serverId, this::createDefaultProject);
    }

    public List<String> getProjectTemplates() {
        return PROJECT_TEMPLATES;
    }

    public WorldGenProject createProjectTemplate(String templateName, String projectId) {
        WorldGenProject project = createDefaultProject();
        String safeTemplateName = templateName == null || templateName.isBlank() ? PROJECT_TEMPLATES.getFirst() : templateName;
        applyProjectTemplate(project, safeTemplateName);
        if (projectId != null && !projectId.isBlank()) {
            project.setId(projectId.trim());
        }
        return project;
    }

    public WorldGenProject copyProject(WorldGenProject project) {
        if (project == null) {
            return createProjectTemplate(PROJECT_TEMPLATES.getFirst(), null);
        }
        return projectStore.copyProject(project, () -> createProjectTemplate(PROJECT_TEMPLATES.getFirst(), null));
    }

    public WorldGenProject getCachedProject(String serverId, String projectId) {
        return projectStore.cachedProject(serverId, projectId);
    }

    public FlowGraph getOrCreateEditorGraph(String serverId, WorldGenStage stage) {
        ensureLocalDefinitions(serverId);
        return toFlowGraph(getOrCreateProject(serverId).graph(stage));
    }

    public FlowGraph getOrCreateEditorGraph(String serverId) {
        ensureLocalDefinitions(serverId);
        return toFlowGraph(getOrCreateGraph(serverId));
    }

    public void saveWorldGen(String serverId, FlowGraph graph) {
        saveWorldGen(serverId, toWorldGenGraph(graph));
    }

    public void saveWorldGen(String serverId, WorldGenGraph graph) {
        if (serverId == null || serverId.isBlank() || graph == null) {
            return;
        }
        WorldGenProject project = getOrCreateProject(serverId);
        project.setTerrainGraph(graph);
        saveWorldGen(serverId, project);
    }

    public void saveWorldGen(String serverId, WorldGenProject project) {
        if (serverId == null || serverId.isBlank() || project == null) {
            return;
        }
        projectStore.setActiveProject(serverId, project);
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.sendWorldGenSave(project);
        } else {
            new Notification("World Generation", "ReSync Offline", Notification.Type.ERROR);
        }
    }

    public void requestProject(String serverId, String projectId) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.requestWorldGenProject(projectId);
        }
    }

    public void requestProjectList(String serverId) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.requestWorldGenProjectList();
        }
    }

    public void requestProjectListIfMissing(String serverId) {
        if (!projectStore.hasProjectList(serverId)) {
            requestProjectList(serverId);
        }
    }

    public void deleteProject(String serverId, String projectId) {
        projectStore.removeCachedProject(serverId, projectId);
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.sendWorldGenProjectDelete(projectId);
            requestProjectList(serverId);
        }
    }

    public void duplicateProject(String serverId, String sourceProjectId, String targetProjectId) {
        if (serverId == null || sourceProjectId == null || targetProjectId == null || targetProjectId.isBlank()) {
            return;
        }
        projectStore.setPendingDuplicateId(serverId, sourceProjectId, targetProjectId);
        requestProject(serverId, sourceProjectId);
    }

    public void handleProjectData(String serverId, WorldGenProject project) {
        if (project == null) {
            return;
        }
        String duplicateId = projectStore.removePendingDuplicateId(serverId, project.getId());
        if (duplicateId != null && !duplicateId.isBlank()) {
            WorldGenProject copy = copyProject(project);
            copy.setId(duplicateId);
            saveWorldGen(serverId, copy);
            return;
        }
        projectStore.setActiveProject(serverId, project);
        ScreenManager.getInstance().execute(() -> {
            if (ScreenManager.getInstance().getCurrentScreen() instanceof WorldGenEditorScreen screen && serverId.equals(screen.getActualServerId())) {
                screen.loadProject(project);
            } else if (ScreenManager.getInstance().getCurrentScreen() instanceof FlowGraphDesignerScreen screen && serverId.equals(screen.getServerId())) {
                screen.loadStudioWorldGenProject(project);
            }
        });
    }

    public void handleProjectList(String serverId, List<String> ids) {
        projectStore.setProjectList(serverId, ids);
        ScreenManager.getInstance().execute(() -> {
            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
            if (studioScreen != null) {
                studioScreen.refreshStudioWorkspace();
            }
        });
    }

    public void handleProjectSaved(String serverId, String projectId) {
        requestProjectList(serverId);
        new Notification("World Generation", "Saved", Notification.Type.SUCCESS);
    }

    public void handleCompileDiagnostics(String serverId, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            boolean success = root.has("success") && root.get("success").getAsBoolean();
            JsonArray diagnostics = root.has("diagnostics") && root.get("diagnostics").isJsonArray() ? root.getAsJsonArray("diagnostics") : new JsonArray();
            if (!success) {
                new Notification("World Generation", firstDiagnostic(diagnostics, "Compile Failed"), Notification.Type.ERROR);
                return;
            }
        } catch (Exception ignored) {
            if (json.contains("\"success\":false")) {
                new Notification("World Generation", "Compile Failed", Notification.Type.ERROR);
            }
        }
    }

    private String firstDiagnostic(JsonArray diagnostics, String fallback) {
        for (JsonElement element : diagnostics) {
            if (element != null && element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("message")) {
                    String message = object.get("message").getAsString();
                    if (message != null && !message.isBlank()) {
                        return message;
                    }
                }
            }
        }
        return fallback;
    }

    private String firstWarning(JsonArray diagnostics) {
        for (JsonElement element : diagnostics) {
            if (element != null && element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                String severity = object.has("severity") ? object.get("severity").getAsString() : "";
                if ("warning".equalsIgnoreCase(severity) && object.has("message")) {
                    return object.get("message").getAsString();
                }
            }
        }
        return "";
    }

    public List<String> getProjectIds(String serverId) {
        return projectStore.getProjectIds(serverId);
    }

    public void requestRegistry(String serverId) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.requestWorldGenRegistry();
        } else {
            ensureLocalDefinitions(serverId);
        }
    }

    public void applyRegistrySnapshot(String serverId, Collection<WorldGenNodeDefinition> definitions) {
        applyRegistrySnapshot(serverId, definitions, null);
    }

    public void applyRegistrySnapshot(String serverId, Collection<WorldGenNodeDefinition> definitions, Object capabilitySnapshot) {
        if (capabilitySnapshot != null) {
            capabilities.put(serverId, capabilitySnapshot);
        }
        Collection<WorldGenNodeDefinition> safeDefinitions = definitions != null ? definitions : List.of();
        if (safeDefinitions.isEmpty()) {
            WorldGenNodeRegistry registry = WorldGenNodeRegistry.getInstance();
            if (!registry.hasDefinitions(serverId)) {
                registerFallbackDefinitions(serverId);
            }
            registerFlowDefinitions(serverId, registry.getAllDefinitions(serverId));
            handleRegistryUpdated(serverId);
            return;
        }
        WorldGenNodeRegistry.getInstance().replaceDefinitions(serverId, safeDefinitions);
        registerFlowDefinitions(serverId, safeDefinitions);
        handleRegistryUpdated(serverId);
    }

    public Object getCapabilities(String serverId) {
        return capabilities.get(serverId);
    }

    public void requestPreview(String serverId, String previewId, FlowGraph graph, String environment, long seed, String playerUuid) {
        requestPreview(serverId, previewId, toWorldGenGraph(graph), environment, seed, playerUuid);
    }

    public void requestPreview(String serverId, String previewId, WorldGenGraph graph, String environment, long seed, String playerUuid) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client == null) {
            new Notification("World Generation", "ReSync Offline", Notification.Type.ERROR);
            return;
        }
        WorldGenProject project = getOrCreateProject(serverId);
        project.setTerrainGraph(graph);
        requestPreview(serverId, previewId, project, environment, seed, playerUuid);
    }

    public void requestPreview(String serverId, String previewId, WorldGenProject project, String environment, long seed, String playerUuid) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client == null) {
            new Notification("World Generation", "ReSync Offline", Notification.Type.ERROR);
            return;
        }
        projectStore.setActiveProject(serverId, project);
        previewController.markCreating(serverId, previewId);
        client.sendWorldGenPreviewApply(null, project, previewId, environment, seed, isUuid(playerUuid) ? playerUuid : "");
        new Notification("World Generation", "Creating Preview", Notification.Type.INFO);
    }

    public void requestSavedProjectPreview(String serverId, String projectId, String previewId, String environment, long seed, String playerUuid) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client == null) {
            new Notification("World Generation", "ReSync Offline", Notification.Type.ERROR);
            return;
        }
        previewController.markCreating(serverId, previewId);
        WorldGenProject cachedProject = projectStore.cachedProject(serverId, projectId);
        if (cachedProject != null) {
            client.sendWorldGenPreviewApply(null, cachedProject, previewId, environment, seed, isUuid(playerUuid) ? playerUuid : "");
        } else {
            client.sendWorldGenPreviewApply(projectId, null, previewId, environment, seed, isUuid(playerUuid) ? playerUuid : "");
        }
        new Notification("World Generation", "Creating Preview", Notification.Type.INFO);
    }

    public void stopPreview(String serverId, String previewId) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.sendWorldGenPreviewStop(previewId);
            previewController.stop(serverId, previewId);
            new Notification("World Generation", "Preview Stopped", Notification.Type.SUCCESS);
        }
    }

    public void handlePreviewStatus(String serverId, String previewId, String status, String message) {
        previewController.update(serverId, previewId, status);
        ScreenManager.getInstance().execute(() -> {
            new Notification("World Generation", message == null || message.isBlank() ? status : message, "ready".equalsIgnoreCase(status) ? Notification.Type.SUCCESS : Notification.Type.ERROR);
            if (ScreenManager.getInstance().getCurrentScreen() instanceof WorldGenEditorScreen screen && serverId.equals(screen.getActualServerId())) {
                screen.refreshStatus();
            }
        });
    }

    public void handleRegistryUpdated(String serverId) {
        ScreenManager.getInstance().execute(() -> {
            if (ScreenManager.getInstance().getCurrentScreen() instanceof WorldGenEditorScreen screen && serverId.equals(screen.getActualServerId())) {
                screen.refreshNodeRegistry();
            }
        });
    }

    public String getPreviewState(String serverId, String previewId) {
        return previewController.state(serverId, previewId);
    }

    public void ensureLocalDefinitions(String serverId) {
        WorldGenNodeRegistry registry = WorldGenNodeRegistry.getInstance();
        if (!registry.hasDefinitions(serverId)) {
            registerFallbackDefinitions(serverId);
            ReSyncFlowClient client = flowClient(serverId);
            if (client != null && client.isConnectedState()) {
                client.requestWorldGenRegistry();
            }
        }
        registerFlowDefinitions(serverId, registry.getAllDefinitions(serverId));
    }

    private void registerFallbackDefinitions(String serverId) {
        WorldGenNodeRegistry registry = WorldGenNodeRegistry.getInstance();
        for (WorldGenNodeDefinition definition : defaultDefinitions()) {
            registry.register(serverId, definition);
        }
    }

    public WorldGenGraph toWorldGenGraph(FlowGraph flowGraph) {
        WorldGenGraph graph = new WorldGenGraph();
        if (flowGraph == null) {
            return graph;
        }
        graph.setId(flowGraph.getId());
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, FlowNode> entry : flowGraph.getNodes().entrySet()) {
            FlowNode node = entry.getValue();
            nodes.put(entry.getKey(), new WorldGenNode(node.getType(), node.getX(), node.getY(), node.getInputValues() != null ? new HashMap<>(node.getInputValues()) : new HashMap<>()));
        }
        List<WorldGenConnection> connections = new ArrayList<>();
        for (FlowConnection connection : flowGraph.getConnections()) {
            connections.add(new WorldGenConnection(connection.getSourceNodeId(), connection.getSourcePin(), connection.getTargetNodeId(), connection.getTargetPin()));
        }
        graph.setNodes(nodes);
        graph.setConnections(connections);
        return graph;
    }

    public FlowGraph toFlowGraph(WorldGenGraph worldGenGraph) {
        FlowGraph graph = new FlowGraph();
        if (worldGenGraph == null) {
            return graph;
        }
        graph.setId(worldGenGraph.getId());
        for (Map.Entry<String, WorldGenNode> entry : worldGenGraph.getNodes().entrySet()) {
            WorldGenNode node = entry.getValue();
            graph.getNodes().put(entry.getKey(), new FlowNode(node.getType(), node.getX(), node.getY(), node.getInputValues() != null ? new HashMap<>(node.getInputValues()) : new HashMap<>()));
        }
        for (WorldGenConnection connection : worldGenGraph.getConnections()) {
            graph.getConnections().add(new FlowConnection(connection.getSourceNodeId(), connection.getSourcePin(), connection.getTargetNodeId(), connection.getTargetPin()));
        }
        graph.setFunction(false);
        graph.getLocalVariables().clear();
        graph.getFunctionInputs().clear();
        graph.getFunctionOutputs().clear();
        return graph;
    }

    private void registerFlowDefinitions(String serverId, Collection<WorldGenNodeDefinition> definitions) {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry == null) {
            return;
        }
        String key = registryServerId(serverId);
        registry.clearServer(key);
        for (WorldGenNodeDefinition definition : definitions) {
            registry.registerServerDefinition(key, toFlowDefinition(definition));
        }
    }

    private NodeDefinition toFlowDefinition(WorldGenNodeDefinition definition) {
        NodeDefinition.Builder builder = new NodeDefinition.Builder(definition.getId(), definition.getDisplayName(), categoryFor(definition))
            .color(definition.getColor())
            .priority(definition.getPriority())
            .description(definition.getDescription())
            .hidden(definition.isHidden());
        for (WorldGenNodeDefinition.PinDefinition pin : definition.getInputs()) {
            builder.input(toFlowPin(pin, NodeDefinition.PinDirection.INPUT));
        }
        for (WorldGenNodeDefinition.PinDefinition pin : definition.getOutputs()) {
            builder.output(toFlowPin(pin, NodeDefinition.PinDirection.OUTPUT));
        }
        return builder.build();
    }

    private NodeDefinition.NodeCategory categoryFor(WorldGenNodeDefinition definition) {
        String category = definition.getCategory();
        if (category == null || category.isBlank() || "World Gen".equals(category)) {
            return NodeDefinition.NodeCategory.WORLD_GEN;
        }
        String id = category.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
        return NodeDefinition.NodeCategory.registerServerCategory(id, category, definition.getColor(), definition.getPriority());
    }

    private NodeDefinition.PinDefinition toFlowPin(WorldGenNodeDefinition.PinDefinition pin, NodeDefinition.PinDirection direction) {
        NodeDefinition.PinBuilder builder = new NodeDefinition.PinBuilder(pin.name(), NodeDefinition.PinType.DATA, direction, pin.dataType());
        NodeDefinition.WidgetType widgetType = widgetType(pin);
        if (widgetType != null) {
            builder.widget(widgetType);
        }
        if (pin.defaultValue() != null) {
            builder.defaultValue(String.valueOf(pin.defaultValue()));
        }
        if (pin.options() != null && !pin.options().isEmpty()) {
            builder.options(pin.options());
        }
        if ("distance_func".equals(pin.name())) {
            builder.options(List.of("euclidean", "euclidean_sq", "manhattan", "hybrid"));
        }
        if ("material".equalsIgnoreCase(pin.widgetType())) {
            builder.optionsSource("minecraft:blocks");
        }
        if (pin.dataType() == FlowDataType.BIOME) {
            builder.widget(NodeDefinition.WidgetType.SEARCHABLE_LIST);
            builder.optionsSource("worldgen:biomes");
        }
        if (pin.dataType() == FlowDataType.ENTITY_TYPE) {
            builder.widget(NodeDefinition.WidgetType.SEARCHABLE_LIST);
            builder.optionsSource("worldgen:entity_types");
        }
        if ("structure_id".equals(pin.name())) {
            builder.widget(NodeDefinition.WidgetType.SEARCHABLE_LIST);
            builder.optionsSource("worldgen:structures");
        }
        if ("tree".equals(pin.name())) {
            builder.widget(NodeDefinition.WidgetType.SEARCHABLE_LIST);
            builder.optionsSource("worldgen:tree_features");
        }
        if ("feature".equals(pin.name())) {
            builder.widget(NodeDefinition.WidgetType.SEARCHABLE_LIST);
            builder.optionsSource("worldgen:features");
        }
        if (pin.constraints() != null && !pin.constraints().isEmpty()) {
            builder.constraints(number(pin.constraints().get("min")), number(pin.constraints().get("max")), number(pin.constraints().get("step")));
        }
        if (pin.description() != null && !pin.description().isBlank()) {
            builder.description(pin.description());
        }
        return builder.build();
    }

    private NodeDefinition.WidgetType widgetType(WorldGenNodeDefinition.PinDefinition pin) {
        String widgetType = pin.widgetType();
        if (widgetType == null || widgetType.isBlank()) {
            return null;
        }
        return switch (widgetType.toLowerCase()) {
            case "number" -> NodeDefinition.WidgetType.NUMBER;
            case "dropdown" -> NodeDefinition.WidgetType.DROPDOWN;
            case "slider" -> NodeDefinition.WidgetType.SLIDER;
            case "toggle" -> NodeDefinition.WidgetType.TOGGLE;
            case "material" -> NodeDefinition.WidgetType.SEARCHABLE_LIST;
            case "searchable", "searchable_list" -> NodeDefinition.WidgetType.SEARCHABLE_LIST;
            default -> NodeDefinition.WidgetType.TEXT;
        };
    }

    private Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<WorldGenNodeDefinition> defaultDefinitions() {
        return List.of(
            terrain(WorldGenNodeDefinition.builder("simplex", "Simplex").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("perlin", "Perlin").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("value", "Value").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("cellular", "Cellular").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").input("distance_func", FlowDataType.STRING, "euclidean", "dropdown").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("white", "White").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("fbm", "Fractal FBm").input("source", FlowDataType.FLOAT, 0f, "number").input("octaves", FlowDataType.FLOAT, 4f, "number").input("lacunarity", FlowDataType.FLOAT, 2f, "number").input("gain", FlowDataType.FLOAT, 0.5f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("ridged", "Fractal Ridged").input("source", FlowDataType.FLOAT, 0f, "number").input("octaves", FlowDataType.FLOAT, 4f, "number").input("lacunarity", FlowDataType.FLOAT, 2f, "number").input("gain", FlowDataType.FLOAT, 0.5f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("ping_pong", "Ping Pong").input("source", FlowDataType.FLOAT, 0f, "number").input("octaves", FlowDataType.FLOAT, 4f, "number").input("lacunarity", FlowDataType.FLOAT, 2f, "number").input("gain", FlowDataType.FLOAT, 0.5f, "number").input("ping_pong_strength", FlowDataType.FLOAT, 2f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("add", "Add").input("a", FlowDataType.FLOAT, 0f, "number").input("b", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("multiply", "Multiply").input("a", FlowDataType.FLOAT, 1f, "number").input("b", FlowDataType.FLOAT, 1f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("remap", "Remap").input("in", FlowDataType.FLOAT, 0f, "number").input("from_min", FlowDataType.FLOAT, -1f, "number").input("from_max", FlowDataType.FLOAT, 1f, "number").input("to_min", FlowDataType.FLOAT, 0f, "number").input("to_max", FlowDataType.FLOAT, 128f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("clamp", "Clamp").input("in", FlowDataType.FLOAT, 0f, "number").input("min", FlowDataType.FLOAT, 0f, "number").input("max", FlowDataType.FLOAT, 1f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("abs", "Abs").input("in", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("min", "Min").input("a", FlowDataType.FLOAT, 0f, "number").input("b", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("max", "Max").input("a", FlowDataType.FLOAT, 0f, "number").input("b", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("domain_warp_gradient", "Domain Warp Gradient").input("source", FlowDataType.FLOAT, 0f, "number").input("amplitude", FlowDataType.FLOAT, 1f, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("domain_warp_simplex", "Domain Warp Simplex").input("source", FlowDataType.FLOAT, 0f, "number").input("amplitude", FlowDataType.FLOAT, 1f, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("terrace", "Terrace").input("in", FlowDataType.FLOAT, 0f, "number").input("step_count", FlowDataType.FLOAT, 8f, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("seed_offset", "Seed Offset").input("in", FlowDataType.FLOAT, 0f, "number").input("offset", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("continental_shelf", "Continental Shelf").input("scale", FlowDataType.FLOAT, 1f, "number").input("ocean", FlowDataType.FLOAT, 0.42f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("mountain_range", "Mountain Range").input("amount", FlowDataType.FLOAT, 1f, "number").input("scale", FlowDataType.FLOAT, 1f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("river_network", "River Network").input("density", FlowDataType.FLOAT, 1f, "number").input("depth", FlowDataType.FLOAT, 24f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("eroded_peaks", "Eroded Peaks").input("amount", FlowDataType.FLOAT, 1f, "number").input("terraces", FlowDataType.FLOAT, 12f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("badlands_plateau", "Badlands Plateau").input("height", FlowDataType.FLOAT, 86f, "number").input("erosion", FlowDataType.FLOAT, 0.55f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("volcanic_field", "Volcanic Field").input("height", FlowDataType.FLOAT, 72f, "number").input("roughness", FlowDataType.FLOAT, 0.7f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("density_from_height", "Density From Height").input("height", FlowDataType.FLOAT, 64f, "number").input("falloff", FlowDataType.FLOAT, 12f, "number").output("density", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("terrain_density", "Terrain Density").input("continentalness", FlowDataType.FLOAT, 0f, "number").input("erosion", FlowDataType.FLOAT, 0f, "number").input("weirdness", FlowDataType.FLOAT, 0f, "number").input("depth", FlowDataType.FLOAT, 0f, "number").input("base", FlowDataType.FLOAT, 64f, "number").input("seed", FlowDataType.SEED, 0, "number").output("density", FlowDataType.FLOAT)),
            terrain(WorldGenNodeDefinition.builder("output_height", "Output Height").input("height", FlowDataType.FLOAT, 64f, "number")),
            terrain(WorldGenNodeDefinition.builder("output_density", "Output Density").input("density", FlowDataType.FLOAT, 0f, "number")),
            terrain(WorldGenNodeDefinition.builder("output_continentalness", "Output Continentalness").input("continentalness", FlowDataType.FLOAT, 0f, "number")),
            terrain(WorldGenNodeDefinition.builder("output_erosion", "Output Erosion").input("erosion", FlowDataType.FLOAT, 0f, "number")),
            terrain(WorldGenNodeDefinition.builder("output_weirdness", "Output Weirdness").input("weirdness", FlowDataType.FLOAT, 0f, "number")),
            terrain(WorldGenNodeDefinition.builder("output_depth", "Output Depth").input("depth", FlowDataType.FLOAT, 0f, "number")),
            terrain(WorldGenNodeDefinition.builder("output_temperature", "Output Temperature").input("temperature", FlowDataType.FLOAT, 0.5f, "number")),
            terrain(WorldGenNodeDefinition.builder("output_humidity", "Output Humidity").input("humidity", FlowDataType.FLOAT, 0.5f, "number")),
            biome(WorldGenNodeDefinition.builder("output_biome", "Output Biome").input("biome", FlowDataType.BIOME, "minecraft:plains", "searchable").input("temperature", FlowDataType.FLOAT, 0.5f, "number").input("humidity", FlowDataType.FLOAT, 0.5f, "number").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle")),
            surface(WorldGenNodeDefinition.builder("output_block", "Output Block").input("block", FlowDataType.BLOCK, null, "material").input("y", FlowDataType.FLOAT, 0f, "number").input("replace", FlowDataType.FLOAT, 1f, "number")),
            biome(WorldGenNodeDefinition.builder("biome_constant", "Biome Constant").input("biome", FlowDataType.BIOME, "minecraft:plains", "searchable").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("biome_profile", "Biome Profile").input("profile", FlowDataType.BIOME, "minecraft:plains", "searchable").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("biome_select", "Biome Select").input("mask", FlowDataType.BOOLEAN, false, "toggle").input("true_biome", FlowDataType.BIOME, "minecraft:forest", "searchable").input("false_biome", FlowDataType.BIOME, "minecraft:plains", "searchable").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("biome_blend", "Biome Blend").input("a", FlowDataType.BIOME, "minecraft:plains", "dropdown").input("b", FlowDataType.BIOME, "minecraft:forest", "dropdown").input("weight", FlowDataType.FLOAT, 0.5f, "number").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("climate_map", "Climate Map").input("temperature", FlowDataType.FLOAT, 0.5f, "number").input("humidity", FlowDataType.FLOAT, 0.5f, "number").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("biome_climate_router", "Biome Climate Router").input("temperature", FlowDataType.FLOAT, 0.5f, "number").input("humidity", FlowDataType.FLOAT, 0.5f, "number").input("continentalness", FlowDataType.FLOAT, 0f, "number").input("erosion", FlowDataType.FLOAT, 0f, "number").input("weirdness", FlowDataType.FLOAT, 0f, "number").input("temperature_scale", FlowDataType.FLOAT, 1f, "number").input("humidity_scale", FlowDataType.FLOAT, 1f, "number").input("keep_vanilla_features", FlowDataType.BOOLEAN, true, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, true, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").input("seed", FlowDataType.SEED, 0, "number").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("temperature", "Temperature").input("value", FlowDataType.FLOAT, 0.5f, "number").output("out", FlowDataType.FLOAT)),
            biome(WorldGenNodeDefinition.builder("humidity", "Humidity").input("value", FlowDataType.FLOAT, 0.5f, "number").output("out", FlowDataType.FLOAT)),
            biome(WorldGenNodeDefinition.builder("continentalness", "Continentalness").input("value", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT)),
            biome(WorldGenNodeDefinition.builder("erosion", "Erosion").input("value", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT)),
            biome(WorldGenNodeDefinition.builder("weirdness", "Weirdness").input("value", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT)),
            surface(WorldGenNodeDefinition.builder("surface_rule", "Surface Rule").input("top", FlowDataType.BLOCK, "minecraft:grass_block", "material").input("filler", FlowDataType.BLOCK, "minecraft:dirt", "material").output("surface", FlowDataType.BLOCK)),
            surface(WorldGenNodeDefinition.builder("material_layer", "Material Layer").input("block", FlowDataType.BLOCK, "minecraft:stone", "material").input("depth", FlowDataType.FLOAT, 3f, "number").output("surface", FlowDataType.BLOCK)),
            surface(WorldGenNodeDefinition.builder("height_band", "Height Band").input("min", FlowDataType.FLOAT, 0f, "number").input("max", FlowDataType.FLOAT, 320f, "number").output("mask", FlowDataType.BOOLEAN)),
            surface(WorldGenNodeDefinition.builder("slope_mask", "Slope Mask").input("min", FlowDataType.FLOAT, 0f, "number").input("max", FlowDataType.FLOAT, 1f, "number").output("mask", FlowDataType.BOOLEAN)),
            surface(WorldGenNodeDefinition.builder("beach_rule", "Beach Rule").input("sand", FlowDataType.BLOCK, "minecraft:sand", "material").output("surface", FlowDataType.BLOCK)),
            surface(WorldGenNodeDefinition.builder("underwater_rule", "Underwater Rule").input("block", FlowDataType.BLOCK, "minecraft:gravel", "material").output("surface", FlowDataType.BLOCK)),
            surface(WorldGenNodeDefinition.builder("snow_rule", "Snow Rule").input("block", FlowDataType.BLOCK, "minecraft:snow_block", "material").output("surface", FlowDataType.BLOCK)),
            cave(WorldGenNodeDefinition.builder("cave_noise", "Cave Noise").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.02f, "number").output("density", FlowDataType.FLOAT)),
            cave(WorldGenNodeDefinition.builder("worm_cave", "Worm Cave").input("radius", FlowDataType.FLOAT, 3f, "number").output("density", FlowDataType.FLOAT)),
            cave(WorldGenNodeDefinition.builder("cheese_cave", "Cheese Cave").input("threshold", FlowDataType.FLOAT, 0.6f, "number").output("density", FlowDataType.FLOAT)),
            cave(WorldGenNodeDefinition.builder("ravine", "Ravine").input("width", FlowDataType.FLOAT, 6f, "number").output("density", FlowDataType.FLOAT)),
            cave(WorldGenNodeDefinition.builder("carve_if", "Carve If").input("mask", FlowDataType.BOOLEAN, false, "toggle").input("density", FlowDataType.FLOAT, 0f, "number").output("density", FlowDataType.FLOAT)),
            cave(WorldGenNodeDefinition.builder("density_combine", "Density Combine").input("a", FlowDataType.FLOAT, 0f, "number").input("b", FlowDataType.FLOAT, 0f, "number").output("density", FlowDataType.FLOAT)),
            cave(WorldGenNodeDefinition.builder("cave_system", "Cave System").input("amount", FlowDataType.FLOAT, 1f, "number").input("scale", FlowDataType.FLOAT, 1f, "number").input("seed", FlowDataType.SEED, 0, "number").output("density", FlowDataType.FLOAT)),
            feature(WorldGenNodeDefinition.builder("ore_vein", "Ore Vein").input("block", FlowDataType.BLOCK, "minecraft:coal_ore", "material").input("size", FlowDataType.FLOAT, 8f, "number").output("feature", FlowDataType.STRING).hidden(true)),
            feature(WorldGenNodeDefinition.builder("tree_feature", "Tree Feature").input("tree", FlowDataType.STRING, "TREE", "searchable").output("feature", FlowDataType.STRING).hidden(true)),
            feature(WorldGenNodeDefinition.builder("vegetation_patch", "Vegetation Patch").input("block", FlowDataType.BLOCK, "minecraft:grass", "material").output("feature", FlowDataType.STRING).hidden(true)),
            feature(WorldGenNodeDefinition.builder("liquid_lake", "Liquid Lake").input("fluid", FlowDataType.BLOCK, "minecraft:water", "material").output("feature", FlowDataType.STRING).hidden(true)),
            feature(WorldGenNodeDefinition.builder("disk", "Disk").input("block", FlowDataType.BLOCK, "minecraft:clay", "material").input("radius", FlowDataType.FLOAT, 4f, "number").output("feature", FlowDataType.STRING).hidden(true)),
            feature(WorldGenNodeDefinition.builder("boulder", "Boulder").input("block", FlowDataType.BLOCK, "minecraft:mossy_cobblestone", "material").output("feature", FlowDataType.STRING).hidden(true)),
            feature(WorldGenNodeDefinition.builder("scatter", "Scatter").input("feature", FlowDataType.STRING, "", "text").input("chance", FlowDataType.FLOAT, 0.1f, "number").output("placement", FlowDataType.STRING).hidden(true)),
            feature(WorldGenNodeDefinition.builder("poisson_scatter", "Poisson Scatter").input("feature", FlowDataType.STRING, "", "text").input("spacing", FlowDataType.FLOAT, 12f, "number").output("placement", FlowDataType.STRING).hidden(true)),
            feature(WorldGenNodeDefinition.builder("biome_filter", "Biome Filter").input("biome", FlowDataType.BIOME, "minecraft:plains", "dropdown").output("mask", FlowDataType.BOOLEAN)),
            feature(WorldGenNodeDefinition.builder("height_filter", "Height Filter").input("min", FlowDataType.FLOAT, 0f, "number").input("max", FlowDataType.FLOAT, 320f, "number").output("mask", FlowDataType.BOOLEAN)),
            feature(WorldGenNodeDefinition.builder("chance_filter", "Chance Filter").input("chance", FlowDataType.FLOAT, 0.5f, "number").input("salt", FlowDataType.SEED, 0, "number").output("mask", FlowDataType.BOOLEAN)),
            structure(WorldGenNodeDefinition.builder("structure_placement", "Structure Placement").input("structure_id", FlowDataType.STRING, "", "searchable").input("spacing", FlowDataType.FLOAT, 32f, "number").input("separation", FlowDataType.FLOAT, 8f, "number").input("salt", FlowDataType.SEED, 0, "number").input("y_offset", FlowDataType.FLOAT, 0f, "number").output("structure", FlowDataType.STRING)),
            spawn(WorldGenNodeDefinition.builder("spawn_rule", "Spawn Rule").input("entity", FlowDataType.ENTITY_TYPE, "minecraft:zombie", "dropdown").input("weight", FlowDataType.FLOAT, 10f, "number").input("min_group", FlowDataType.FLOAT, 1f, "number").input("max_group", FlowDataType.FLOAT, 4f, "number").output("spawn", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("output_features", "Output Features").input("placements", FlowDataType.STRING, "", "text").hidden(true)),
            structure(WorldGenNodeDefinition.builder("output_structures", "Output Structures").input("placements", FlowDataType.STRING, "", "text")),
            spawn(WorldGenNodeDefinition.builder("output_spawns", "Output Spawns").input("table", FlowDataType.STRING, "", "text"))
        );
    }

    private WorldGenNodeDefinition terrain(WorldGenNodeDefinition.Builder builder) {
        return builder.category("Terrain").build();
    }

    private WorldGenNodeDefinition biome(WorldGenNodeDefinition.Builder builder) {
        return builder.category("Biomes").build();
    }

    private WorldGenNodeDefinition surface(WorldGenNodeDefinition.Builder builder) {
        return builder.category("Surface").build();
    }

    private WorldGenNodeDefinition cave(WorldGenNodeDefinition.Builder builder) {
        return builder.category("Caves").build();
    }

    private WorldGenNodeDefinition feature(WorldGenNodeDefinition.Builder builder) {
        return builder.category("Features").build();
    }

    private WorldGenNodeDefinition structure(WorldGenNodeDefinition.Builder builder) {
        return builder.category("Structures").build();
    }

    private WorldGenNodeDefinition spawn(WorldGenNodeDefinition.Builder builder) {
        return builder.category("Spawns").build();
    }

    private WorldGenGraph createDefaultGraph() {
        WorldGenGraph graph = new WorldGenGraph();
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        Map<String, Object> noiseValues = new HashMap<>();
        noiseValues.put("seed", 12001);
        noiseValues.put("frequency", 0.006f);
        nodes.put("terrain_noise_1", new WorldGenNode("simplex", 80, 120, noiseValues));
        Map<String, Object> remapValues = new HashMap<>();
        remapValues.put("from_min", -1f);
        remapValues.put("from_max", 1f);
        remapValues.put("to_min", 58f);
        remapValues.put("to_max", 92f);
        nodes.put("height_remap_1", new WorldGenNode("remap", 360, 120, remapValues));
        Map<String, Object> clampValues = new HashMap<>();
        clampValues.put("min", 48f);
        clampValues.put("max", 128f);
        nodes.put("height_clamp_1", new WorldGenNode("clamp", 640, 120, clampValues));
        nodes.put("output_height_1", new WorldGenNode("output_height", 920, 120, new HashMap<>()));
        graph.setNodes(nodes);
        graph.setConnections(new ArrayList<>(List.of(
            new WorldGenConnection("terrain_noise_1", "out", "height_remap_1", "in"),
            new WorldGenConnection("height_remap_1", "out", "height_clamp_1", "in"),
            new WorldGenConnection("height_clamp_1", "out", "output_height_1", "height")
        )));
        return graph;
    }

    private WorldGenProject createDefaultProject() {
        WorldGenProject project = new WorldGenProject();
        project.setTerrainGraph(createDefaultGraph());
        project.setBiomeGraph(createDefaultBiomeGraph());
        project.setSurfaceGraph(createDefaultSurfaceGraph());
        project.setCaveGraph(createDefaultCaveGraph());
        project.setFeatureGraph(createDefaultFeatureGraph());
        project.setStructureGraph(createDefaultStructureGraph());
        project.setSpawnGraph(createDefaultSpawnGraph());
        return project;
    }

    private void applyProjectTemplate(WorldGenProject project, String templateName) {
        String normalized = templateName == null ? "" : templateName.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "alpine" -> {
                project.getSettings().setTerrainTemplate("alpine");
                setNodeInput(project.getTerrainGraph(), "terrain_noise_1", "frequency", 0.009f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_min", 72f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_max", 156f);
                setNodeInput(project.getTerrainGraph(), "height_clamp_1", "max", 224f);
                setNodeInput(project.getBiomeGraph(), "biome_climate_router_1", "temperature_scale", 0.75f);
                setNodeInput(project.getBiomeGraph(), "biome_climate_router_1", "humidity_scale", 1.15f);
            }
            case "islands" -> {
                project.getSettings().setTerrainTemplate("islands");
                project.getSettings().setSeaLevel(68);
                setNodeInput(project.getTerrainGraph(), "terrain_noise_1", "frequency", 0.012f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_min", 42f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_max", 86f);
                setNodeInput(project.getTerrainGraph(), "height_clamp_1", "min", 28f);
            }
            case "badlands" -> {
                project.getSettings().setTerrainTemplate("badlands");
                project.getSettings().setDefaultBlock("minecraft:terracotta");
                setNodeInput(project.getTerrainGraph(), "terrain_noise_1", "frequency", 0.0075f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_min", 64f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_max", 118f);
                setNodeInput(project.getBiomeGraph(), "output_biome_1", "biome", "minecraft:badlands");
                setNodeInput(project.getBiomeGraph(), "biome_climate_router_1", "temperature_scale", 1.45f);
                setNodeInput(project.getBiomeGraph(), "biome_climate_router_1", "humidity_scale", 0.55f);
            }
            case "frozen" -> {
                project.getSettings().setTerrainTemplate("frozen");
                project.getSettings().setDefaultFluid("minecraft:water");
                setNodeInput(project.getTerrainGraph(), "terrain_noise_1", "frequency", 0.005f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_min", 60f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_max", 96f);
                setNodeInput(project.getBiomeGraph(), "output_biome_1", "biome", "minecraft:snowy_plains");
                setNodeInput(project.getBiomeGraph(), "biome_climate_router_1", "temperature_scale", 0.45f);
                setNodeInput(project.getBiomeGraph(), "biome_climate_router_1", "humidity_scale", 1.25f);
            }
            case "caves" -> {
                project.getSettings().setTerrainTemplate("caves");
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_min", 48f);
                setNodeInput(project.getTerrainGraph(), "height_remap_1", "to_max", 80f);
                setNodeInput(project.getCaveGraph(), "cave_system_1", "amount", 1.6f);
                setNodeInput(project.getCaveGraph(), "cave_system_1", "scale", 1.35f);
                setNodeInput(project.getBiomeGraph(), "output_biome_1", "biome", "minecraft:dripstone_caves");
            }
            default -> project.getSettings().setTerrainTemplate("continental");
        }
    }

    private void setNodeInput(WorldGenGraph graph, String nodeId, String input, Object value) {
        if (graph == null || graph.getNodes() == null) {
            return;
        }
        WorldGenNode node = graph.getNodes().get(nodeId);
        if (node != null) {
            node.getInputValues().put(input, value);
        }
    }

    private WorldGenGraph createDefaultBiomeGraph() {
        WorldGenGraph graph = emptyGraph("biome");
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        Map<String, Object> routerValues = new HashMap<>();
        routerValues.put("temperature_scale", 1f);
        routerValues.put("humidity_scale", 1f);
        routerValues.put("keep_vanilla_features", true);
        routerValues.put("keep_vanilla_structures", true);
        routerValues.put("keep_vanilla_spawns", true);
        routerValues.put("seed", 23001);
        nodes.put("biome_climate_router_1", new WorldGenNode("biome_climate_router", 80, 120, routerValues));
        Map<String, Object> biomeValues = new HashMap<>();
        biomeValues.put("biome", "minecraft:plains");
        biomeValues.put("temperature", 0.5f);
        biomeValues.put("humidity", 0.5f);
        biomeValues.put("keep_vanilla_features", true);
        biomeValues.put("keep_vanilla_structures", true);
        biomeValues.put("keep_vanilla_spawns", true);
        nodes.put("output_biome_1", new WorldGenNode("output_biome", 360, 120, biomeValues));
        graph.setNodes(nodes);
        graph.setConnections(new ArrayList<>(List.of(new WorldGenConnection("biome_climate_router_1", "biome", "output_biome_1", "biome"))));
        return graph;
    }

    private WorldGenGraph createDefaultSurfaceGraph() {
        WorldGenGraph graph = emptyGraph("surface");
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        Map<String, Object> blockValues = new HashMap<>();
        blockValues.put("block", "minecraft:grass_block");
        blockValues.put("y", 0f);
        blockValues.put("replace", 1f);
        nodes.put("output_block_1", new WorldGenNode("output_block", 220, 120, blockValues));
        graph.setNodes(nodes);
        return graph;
    }

    private WorldGenGraph createDefaultCaveGraph() {
        WorldGenGraph graph = emptyGraph("cave");
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        Map<String, Object> noiseValues = new HashMap<>();
        noiseValues.put("seed", 31001);
        noiseValues.put("amount", 0.8f);
        noiseValues.put("scale", 1f);
        nodes.put("cave_system_1", new WorldGenNode("cave_system", 80, 120, noiseValues));
        Map<String, Object> carveValues = new HashMap<>();
        carveValues.put("mask", true);
        nodes.put("carve_if_1", new WorldGenNode("carve_if", 330, 120, carveValues));
        graph.setNodes(nodes);
        graph.setConnections(new ArrayList<>(List.of(new WorldGenConnection("cave_system_1", "density", "carve_if_1", "density"))));
        return graph;
    }

    private WorldGenGraph createDefaultFeatureGraph() {
        return emptyGraph("feature");
    }

    private WorldGenGraph createDefaultStructureGraph() {
        return emptyGraph("structure");
    }

    private WorldGenGraph createDefaultSpawnGraph() {
        WorldGenGraph graph = emptyGraph("spawn");
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        nodes.put("output_spawns_1", new WorldGenNode("output_spawns", 80, 120, new HashMap<>()));
        graph.setNodes(nodes);
        return graph;
    }

    private WorldGenGraph emptyGraph(String id) {
        WorldGenGraph graph = new WorldGenGraph();
        graph.setId(id);
        return graph;
    }

    private ReSyncFlowClient flowClient(String serverId) {
        FlowManager flowManager = FlowManager.getInstance();
        return flowManager != null ? flowManager.ensureFlowClient(serverId) : null;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

}
