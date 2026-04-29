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
import java.util.Map;
import java.util.UUID;

public class WorldGenManager {
    private static final WorldGenManager INSTANCE = new WorldGenManager();
    private final Map<String, WorldGenGraph> graphs = new HashMap<>();
    private final Map<String, String> previewStates = new HashMap<>();

    public static WorldGenManager getInstance() {
        return INSTANCE;
    }

    public static String registryServerId(String serverId) {
        return (serverId == null || serverId.isBlank() ? "local" : serverId) + ":worldgen";
    }

    public WorldGenGraph getOrCreateGraph(String serverId) {
        return graphs.computeIfAbsent(serverId, id -> createDefaultGraph());
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
        graphs.put(serverId, graph);
        ReSyncFlowClient client = flowClient(serverId);
        if (client != null) {
            client.sendWorldGenSave(graph);
            new Notification("World Generation", "Saved", Notification.Type.SUCCESS);
        } else {
            new Notification("World Generation", "ReSync Offline", Notification.Type.ERROR);
        }
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
        graphs.put(serverId, graph);
        previewStates.put(previewKey(serverId, previewId), "creating");
        client.sendWorldGenPreviewCreate(graph, previewId, environment, seed, isUuid(playerUuid) ? playerUuid : "");
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
        NodeDefinition.Builder builder = new NodeDefinition.Builder(definition.getId(), definition.getDisplayName(), NodeDefinition.NodeCategory.WORLD_GEN)
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

    private NodeDefinition.PinDefinition toFlowPin(WorldGenNodeDefinition.PinDefinition pin, NodeDefinition.PinDirection direction) {
        NodeDefinition.PinBuilder builder = new NodeDefinition.PinBuilder(pin.name(), NodeDefinition.PinType.DATA, direction, pin.dataType());
        NodeDefinition.WidgetType widgetType = widgetType(pin);
        if (widgetType != null) {
            builder.widget(widgetType);
        }
        if (pin.defaultValue() != null) {
            builder.defaultValue(String.valueOf(pin.defaultValue()));
        }
        if ("distance_func".equals(pin.name())) {
            builder.options(List.of("euclidean", "euclidean_sq", "manhattan", "hybrid"));
        }
        if ("material".equalsIgnoreCase(pin.widgetType())) {
            builder.optionsSource("minecraft:blocks");
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
            WorldGenNodeDefinition.builder("simplex", "Simplex").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("perlin", "Perlin").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("value", "Value").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("cellular", "Cellular").input("seed", FlowDataType.SEED, 0, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").input("distance_func", FlowDataType.STRING, "euclidean", "dropdown").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("white", "White").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("fbm", "Fractal FBm").input("source", FlowDataType.FLOAT, 0f, "number").input("octaves", FlowDataType.FLOAT, 4f, "number").input("lacunarity", FlowDataType.FLOAT, 2f, "number").input("gain", FlowDataType.FLOAT, 0.5f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("ridged", "Fractal Ridged").input("source", FlowDataType.FLOAT, 0f, "number").input("octaves", FlowDataType.FLOAT, 4f, "number").input("lacunarity", FlowDataType.FLOAT, 2f, "number").input("gain", FlowDataType.FLOAT, 0.5f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("ping_pong", "Ping Pong").input("source", FlowDataType.FLOAT, 0f, "number").input("octaves", FlowDataType.FLOAT, 4f, "number").input("lacunarity", FlowDataType.FLOAT, 2f, "number").input("gain", FlowDataType.FLOAT, 0.5f, "number").input("ping_pong_strength", FlowDataType.FLOAT, 2f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("add", "Add").input("a", FlowDataType.FLOAT, 0f, "number").input("b", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("multiply", "Multiply").input("a", FlowDataType.FLOAT, 1f, "number").input("b", FlowDataType.FLOAT, 1f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("remap", "Remap").input("in", FlowDataType.FLOAT, 0f, "number").input("from_min", FlowDataType.FLOAT, -1f, "number").input("from_max", FlowDataType.FLOAT, 1f, "number").input("to_min", FlowDataType.FLOAT, 0f, "number").input("to_max", FlowDataType.FLOAT, 128f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("clamp", "Clamp").input("in", FlowDataType.FLOAT, 0f, "number").input("min", FlowDataType.FLOAT, 0f, "number").input("max", FlowDataType.FLOAT, 1f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("abs", "Abs").input("in", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("min", "Min").input("a", FlowDataType.FLOAT, 0f, "number").input("b", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("max", "Max").input("a", FlowDataType.FLOAT, 0f, "number").input("b", FlowDataType.FLOAT, 0f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("domain_warp_gradient", "Domain Warp Gradient").input("source", FlowDataType.FLOAT, 0f, "number").input("amplitude", FlowDataType.FLOAT, 1f, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("domain_warp_simplex", "Domain Warp Simplex").input("source", FlowDataType.FLOAT, 0f, "number").input("amplitude", FlowDataType.FLOAT, 1f, "number").input("frequency", FlowDataType.FLOAT, 0.01f, "number").input("seed", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("terrace", "Terrace").input("in", FlowDataType.FLOAT, 0f, "number").input("step_count", FlowDataType.FLOAT, 8f, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("seed_offset", "Seed Offset").input("in", FlowDataType.FLOAT, 0f, "number").input("offset", FlowDataType.SEED, 0, "number").output("out", FlowDataType.FLOAT).build(),
            WorldGenNodeDefinition.builder("output_height", "Output Height").input("height", FlowDataType.FLOAT, 64f, "number").build(),
            WorldGenNodeDefinition.builder("output_biome", "Output Biome").input("biome", FlowDataType.FLOAT, 0f, "number").input("temperature", FlowDataType.FLOAT, 0.5f, "number").input("humidity", FlowDataType.FLOAT, 0.5f, "number").build(),
            WorldGenNodeDefinition.builder("output_block", "Output Block").input("block", FlowDataType.BLOCK, null, "material").input("y", FlowDataType.FLOAT, 0f, "number").input("replace", FlowDataType.FLOAT, 1f, "number").build()
        );
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
