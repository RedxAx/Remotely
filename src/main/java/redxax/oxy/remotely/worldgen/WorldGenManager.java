package redxax.oxy.remotely.worldgen;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncFlowClient;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.worldgen.data.WorldGenConnection;
import redxax.oxy.remotely.worldgen.data.WorldGenGraph;
import redxax.oxy.remotely.worldgen.data.WorldGenNode;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenSerializer;
import redxax.oxy.remotely.worldgen.data.WorldGenStage;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeDefinition;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeRegistry;
import redxax.oxy.remotely.flow.ui.FlowManagerScreen;
import redxax.oxy.remotely.worldgen.ui.WorldGenEditorScreen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorldGenManager {
    private static final WorldGenManager INSTANCE = new WorldGenManager();
    private final Map<String, WorldGenProject> projects = new HashMap<>();
    private final Map<String, List<String>> projectLists = new HashMap<>();
    private final Map<String, String> previewStates = new HashMap<>();
    private final Map<String, String> pendingDuplicateIds = new HashMap<>();

    public static WorldGenManager getInstance() {
        return INSTANCE;
    }

    public static String registryServerId(String serverId) {
        return (serverId == null || serverId.isBlank() ? "local" : serverId) + ":worldgen";
    }

    public WorldGenGraph getOrCreateGraph(String serverId) {
        return getOrCreateProject(serverId).getTerrainGraph();
    }

    public WorldGenProject getOrCreateProject(String serverId) {
        return projects.computeIfAbsent(serverId, id -> createDefaultProject());
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
        projects.put(serverId, project);
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.sendWorldGenSave(project);
            new Notification("World Generation", "Saved", Notification.Type.SUCCESS);
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

    public void deleteProject(String serverId, String projectId) {
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
        pendingDuplicateIds.put(serverId + ":" + sourceProjectId, targetProjectId);
        requestProject(serverId, sourceProjectId);
    }

    public void handleProjectData(String serverId, WorldGenProject project) {
        if (project == null) {
            return;
        }
        String duplicateId = pendingDuplicateIds.remove(serverId + ":" + project.getId());
        if (duplicateId != null && !duplicateId.isBlank()) {
            WorldGenProject copy = WorldGenSerializer.deserializeProject(WorldGenSerializer.serializeProject(project));
            copy.setId(duplicateId);
            saveWorldGen(serverId, copy);
            return;
        }
        projects.put(serverId, project);
        ScreenManager.getInstance().execute(() -> {
            if (ScreenManager.getInstance().getCurrentScreen() instanceof WorldGenEditorScreen screen && serverId.equals(screen.getActualServerId())) {
                screen.loadProject(project);
            }
        });
    }

    public void handleProjectList(String serverId, List<String> ids) {
        projectLists.put(serverId, new ArrayList<>(ids != null ? ids : List.of()));
        ScreenManager.getInstance().execute(() -> {
            if (ScreenManager.getInstance().getCurrentScreen() instanceof FlowManagerScreen screen && serverId.equals(screen.getServerId())) {
                screen.rebuildWorldGenProjects();
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
        if (json.contains("\"success\":false")) {
            new Notification("World Generation", "Compile Failed", Notification.Type.ERROR);
        }
    }

    public List<String> getProjectIds(String serverId) {
        return projectLists.getOrDefault(serverId, List.of());
    }

    public void requestRegistry(String serverId) {
        ensureLocalDefinitions(serverId);
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.requestWorldGenRegistry();
        }
    }

    public void applyRegistrySnapshot(String serverId, Collection<WorldGenNodeDefinition> definitions) {
        WorldGenNodeRegistry.getInstance().replaceDefinitions(serverId, definitions);
        registerFlowDefinitions(serverId, definitions);
        handleRegistryUpdated(serverId);
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
        projects.put(serverId, project);
        previewStates.put(previewKey(serverId, previewId), "creating");
        client.sendWorldGenPreviewApply(null, project, previewId, environment, seed, isUuid(playerUuid) ? playerUuid : "");
        new Notification("World Generation", "Creating Preview", Notification.Type.INFO);
    }

    public void requestSavedProjectPreview(String serverId, String projectId, String previewId, String environment, long seed, String playerUuid) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client == null) {
            new Notification("World Generation", "ReSync Offline", Notification.Type.ERROR);
            return;
        }
        previewStates.put(previewKey(serverId, previewId), "creating");
        client.sendWorldGenPreviewApply(projectId, null, previewId, environment, seed, isUuid(playerUuid) ? playerUuid : "");
        new Notification("World Generation", "Creating Preview", Notification.Type.INFO);
    }

    public void stopPreview(String serverId, String previewId) {
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.sendWorldGenPreviewStop(previewId);
            previewStates.remove(previewKey(serverId, previewId));
            new Notification("World Generation", "Preview Stopped", Notification.Type.SUCCESS);
        }
    }

    public void handlePreviewStatus(String serverId, String previewId, String status, String message) {
        previewStates.put(previewKey(serverId, previewId), status);
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
        return previewStates.getOrDefault(previewKey(serverId, previewId), "stopped");
    }

    public void ensureLocalDefinitions(String serverId) {
        WorldGenNodeRegistry registry = WorldGenNodeRegistry.getInstance();
        if (!registry.hasDefinitions(serverId)) {
            for (WorldGenNodeDefinition definition : defaultDefinitions()) {
                registry.register(serverId, definition);
            }
        }
        registerFlowDefinitions(serverId, registry.getAllDefinitions(serverId));
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
            terrain(WorldGenNodeDefinition.builder("output_height", "Output Height").input("height", FlowDataType.FLOAT, 64f, "number")),
            biome(WorldGenNodeDefinition.builder("output_biome", "Output Biome").input("biome", FlowDataType.BIOME, "minecraft:plains", "searchable").input("temperature", FlowDataType.FLOAT, 0.5f, "number").input("humidity", FlowDataType.FLOAT, 0.5f, "number").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle")),
            surface(WorldGenNodeDefinition.builder("output_block", "Output Block").input("block", FlowDataType.BLOCK, null, "material").input("y", FlowDataType.FLOAT, 0f, "number").input("replace", FlowDataType.FLOAT, 1f, "number")),
            biome(WorldGenNodeDefinition.builder("biome_constant", "Biome Constant").input("biome", FlowDataType.BIOME, "minecraft:plains", "searchable").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("biome_profile", "Biome Profile").input("profile", FlowDataType.BIOME, "minecraft:plains", "searchable").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("biome_select", "Biome Select").input("mask", FlowDataType.BOOLEAN, false, "toggle").input("true_biome", FlowDataType.BIOME, "minecraft:forest", "searchable").input("false_biome", FlowDataType.BIOME, "minecraft:plains", "searchable").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("biome_blend", "Biome Blend").input("a", FlowDataType.BIOME, "minecraft:plains", "dropdown").input("b", FlowDataType.BIOME, "minecraft:forest", "dropdown").input("weight", FlowDataType.FLOAT, 0.5f, "number").output("biome", FlowDataType.BIOME)),
            biome(WorldGenNodeDefinition.builder("climate_map", "Climate Map").input("temperature", FlowDataType.FLOAT, 0.5f, "number").input("humidity", FlowDataType.FLOAT, 0.5f, "number").input("keep_vanilla_features", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_structures", FlowDataType.BOOLEAN, false, "toggle").input("keep_vanilla_spawns", FlowDataType.BOOLEAN, false, "toggle").output("biome", FlowDataType.BIOME)),
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
            feature(WorldGenNodeDefinition.builder("ore_vein", "Ore Vein").input("block", FlowDataType.BLOCK, "minecraft:coal_ore", "material").input("size", FlowDataType.FLOAT, 8f, "number").output("feature", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("tree_feature", "Tree Feature").input("tree", FlowDataType.STRING, "TREE", "searchable").output("feature", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("vegetation_patch", "Vegetation Patch").input("block", FlowDataType.BLOCK, "minecraft:grass", "material").output("feature", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("liquid_lake", "Liquid Lake").input("fluid", FlowDataType.BLOCK, "minecraft:water", "material").output("feature", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("disk", "Disk").input("block", FlowDataType.BLOCK, "minecraft:clay", "material").input("radius", FlowDataType.FLOAT, 4f, "number").output("feature", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("boulder", "Boulder").input("block", FlowDataType.BLOCK, "minecraft:mossy_cobblestone", "material").output("feature", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("scatter", "Scatter").input("feature", FlowDataType.STRING, "", "text").input("chance", FlowDataType.FLOAT, 0.1f, "number").output("placement", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("poisson_scatter", "Poisson Scatter").input("feature", FlowDataType.STRING, "", "text").input("spacing", FlowDataType.FLOAT, 12f, "number").output("placement", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("biome_filter", "Biome Filter").input("biome", FlowDataType.BIOME, "minecraft:plains", "dropdown").output("mask", FlowDataType.BOOLEAN)),
            feature(WorldGenNodeDefinition.builder("height_filter", "Height Filter").input("min", FlowDataType.FLOAT, 0f, "number").input("max", FlowDataType.FLOAT, 320f, "number").output("mask", FlowDataType.BOOLEAN)),
            feature(WorldGenNodeDefinition.builder("chance_filter", "Chance Filter").input("chance", FlowDataType.FLOAT, 0.5f, "number").input("salt", FlowDataType.SEED, 0, "number").output("mask", FlowDataType.BOOLEAN)),
            structure(WorldGenNodeDefinition.builder("structure_placement", "Structure Placement").input("structure_id", FlowDataType.STRING, "", "searchable").input("spacing", FlowDataType.FLOAT, 32f, "number").input("separation", FlowDataType.FLOAT, 8f, "number").input("salt", FlowDataType.SEED, 0, "number").output("structure", FlowDataType.STRING)),
            spawn(WorldGenNodeDefinition.builder("spawn_rule", "Spawn Rule").input("entity", FlowDataType.ENTITY_TYPE, "minecraft:zombie", "dropdown").input("weight", FlowDataType.FLOAT, 10f, "number").input("min_group", FlowDataType.FLOAT, 1f, "number").input("max_group", FlowDataType.FLOAT, 4f, "number").output("spawn", FlowDataType.STRING)),
            feature(WorldGenNodeDefinition.builder("output_features", "Output Features").input("placements", FlowDataType.STRING, "", "text")),
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
        Map<String, Object> simplexValues = new HashMap<>();
        simplexValues.put("seed", 0);
        simplexValues.put("frequency", 0.01f);
        nodes.put("simplex_1", new WorldGenNode("simplex", 80, 90, simplexValues));
        Map<String, Object> remapValues = new HashMap<>();
        remapValues.put("from_min", -1f);
        remapValues.put("from_max", 1f);
        remapValues.put("to_min", 48f);
        remapValues.put("to_max", 96f);
        nodes.put("remap_1", new WorldGenNode("remap", 330, 90, remapValues));
        nodes.put("output_height_1", new WorldGenNode("output_height", 580, 90, new HashMap<>()));
        graph.setNodes(nodes);
        graph.setConnections(new ArrayList<>(List.of(
            new WorldGenConnection("simplex_1", "out", "remap_1", "in"),
            new WorldGenConnection("remap_1", "out", "output_height_1", "height")
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

    private WorldGenGraph createDefaultBiomeGraph() {
        WorldGenGraph graph = emptyGraph("biome");
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        Map<String, Object> biomeValues = new HashMap<>();
        biomeValues.put("biome", "minecraft:forest");
        biomeValues.put("temperature", 0.7f);
        biomeValues.put("humidity", 0.8f);
        nodes.put("output_biome_1", new WorldGenNode("output_biome", 220, 120, biomeValues));
        graph.setNodes(nodes);
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
        noiseValues.put("seed", 31);
        noiseValues.put("frequency", 0.025f);
        nodes.put("cave_noise_1", new WorldGenNode("cave_noise", 80, 120, noiseValues));
        Map<String, Object> carveValues = new HashMap<>();
        carveValues.put("mask", true);
        nodes.put("carve_if_1", new WorldGenNode("carve_if", 330, 120, carveValues));
        graph.setNodes(nodes);
        graph.setConnections(new ArrayList<>(List.of(new WorldGenConnection("cave_noise_1", "density", "carve_if_1", "density"))));
        return graph;
    }

    private WorldGenGraph createDefaultFeatureGraph() {
        WorldGenGraph graph = emptyGraph("feature");
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        Map<String, Object> treeValues = new HashMap<>();
        treeValues.put("tree", "TREE");
        nodes.put("tree_feature_1", new WorldGenNode("tree_feature", 80, 120, treeValues));
        Map<String, Object> scatterValues = new HashMap<>();
        scatterValues.put("chance", 0.08f);
        nodes.put("scatter_1", new WorldGenNode("scatter", 330, 120, scatterValues));
        nodes.put("output_features_1", new WorldGenNode("output_features", 580, 120, new HashMap<>()));
        graph.setNodes(nodes);
        graph.setConnections(new ArrayList<>(List.of(
            new WorldGenConnection("tree_feature_1", "feature", "scatter_1", "feature"),
            new WorldGenConnection("scatter_1", "placement", "output_features_1", "placements")
        )));
        return graph;
    }

    private WorldGenGraph createDefaultStructureGraph() {
        WorldGenGraph graph = emptyGraph("structure");
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        Map<String, Object> placementValues = new HashMap<>();
        placementValues.put("structure_id", "");
        placementValues.put("spacing", 32f);
        placementValues.put("separation", 8f);
        placementValues.put("salt", 10387313);
        nodes.put("structure_placement_1", new WorldGenNode("structure_placement", 80, 120, placementValues));
        nodes.put("output_structures_1", new WorldGenNode("output_structures", 330, 120, new HashMap<>()));
        graph.setNodes(nodes);
        graph.setConnections(new ArrayList<>(List.of(new WorldGenConnection("structure_placement_1", "structure", "output_structures_1", "placements"))));
        return graph;
    }

    private WorldGenGraph createDefaultSpawnGraph() {
        WorldGenGraph graph = emptyGraph("spawn");
        Map<String, WorldGenNode> nodes = new LinkedHashMap<>();
        Map<String, Object> spawnValues = new HashMap<>();
        spawnValues.put("entity", "minecraft:zombie");
        spawnValues.put("weight", 10f);
        spawnValues.put("min_group", 1f);
        spawnValues.put("max_group", 4f);
        nodes.put("spawn_rule_1", new WorldGenNode("spawn_rule", 80, 120, spawnValues));
        nodes.put("output_spawns_1", new WorldGenNode("output_spawns", 330, 120, new HashMap<>()));
        graph.setNodes(nodes);
        graph.setConnections(new ArrayList<>(List.of(new WorldGenConnection("spawn_rule_1", "spawn", "output_spawns_1", "table"))));
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

    private String previewKey(String serverId, String previewId) {
        return serverId + ":" + previewId;
    }
}
