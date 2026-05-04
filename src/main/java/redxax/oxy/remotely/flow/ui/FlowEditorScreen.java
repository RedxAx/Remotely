package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowDebugController;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import org.lwjgl.glfw.GLFW;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.UiHost;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.InfiniteScreen;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.*;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class FlowEditorScreen extends InfiniteScreen implements UiHost {
    private static final String CUSTOM_FUNCTION_NODE_PREFIX = "custom_function:";
    private static final Set<FlowEditorScreen> OPEN_SCREENS = new CopyOnWriteArraySet<>();
    protected final FlowGraph graph;
    protected final String serverId;
    private static Screen parent;
    private final Map<String, FlowNodeWidget> widgetCache = new HashMap<>();
    private static final float WIRE_HIT_RADIUS = 6.0f;
    private static final int WIRE_OUT_OFFSET = 26;
    private static final int WIRE_LANE_SPACING = 6;
    private static final int JUNCTION_SIZE = 12;
    private static final int JUNCTION_HALF = JUNCTION_SIZE / 2;
    private final Set<String> selectedNodeIds = new HashSet<>();
    private final Set<String> selectionBase = new HashSet<>();
    private final Map<String, int[]> selectedDragStartPositions = new HashMap<>();
    private boolean isSelecting = false;
    private boolean selectionAdditive = false;
    private double selectionStartX = 0;
    private double selectionStartY = 0;
    private double selectionEndX = 0;
    private double selectionEndY = 0;

    private SidePanel paletteSidePanel;
    private final Map<NodeDefinition.NodeCategory, PopupWidget> categoryPopups = new HashMap<>();
    private List<NodeDefinition.NodeCategory> categoryOrder = List.of();

    private IconButton headerBackground;
    protected final List<IconButton> headerButtons = new ArrayList<>();
    private IconButton debugToggleButton;
    private IconButton debugResumeButton;
    private IconButton debugStepButton;
    private IconButton debugStopButton;
    private boolean initialized;
    private boolean debugMode;

    private int initialWidth;
    private int initialHeight;

    private static class DragState {
        String sourceNodeId;
        String sourcePin;
        boolean isDragging;
        boolean sourceIsInput;
    }
    private final DragState dragState;
    private FlowNodeWidget dragPinWidget;
    private ItemSelectorWidget nodeItemSelector;
    private FlowNodeWidget focusedNode;
    private boolean movingSelectedNodes = false;

    private String pendingSourceNodeId;
    private String pendingSourcePin;
    private boolean pendingSourceIsInput;

    private double dragMouseX = 0;
    private double dragMouseY = 0;
    private FlowGraph.EditorJunction draggedJunction;
    private FlowGraph.EditorJunction dragSourceJunction;
    private double junctionDragOffsetX;
    private double junctionDragOffsetY;

    private static class ClipboardData {
        final List<CopiedNode> nodes = new ArrayList<>();
        final List<CopiedConnection> connections = new ArrayList<>();
    }

    private static class CopiedNode {
        final String type;
        final double relativeX;
        final double relativeY;
        final Map<String, Object> inputValues;

        CopiedNode(String type, double relativeX, double relativeY, Map<String, Object> inputValues) {
            this.type = type;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.inputValues = new HashMap<>(inputValues);
        }
    }

    private static class CopiedConnection {
        final int sourceIndex;
        final String sourcePin;
        final int targetIndex;
        final String targetPin;

        CopiedConnection(int sourceIndex, String sourcePin, int targetIndex, String targetPin) {
            this.sourceIndex = sourceIndex;
            this.sourcePin = sourcePin;
            this.targetIndex = targetIndex;
            this.targetPin = targetPin;
        }
    }

    private final ClipboardData clipboard = new ClipboardData();

    private static class GraphSnapshot {
        final Map<String, FlowNode> nodes;
        final List<FlowConnection> connections;
        final Set<String> selectedIds;
        final boolean function;
        final List<FlowGraph.FunctionParameter> functionInputs;
        final List<FlowGraph.FunctionParameter> functionOutputs;
        final List<FlowGraph.EditorJunction> editorJunctions;
        final List<FlowGraph.EditorPassthrough> editorPassthroughs;

        private GraphSnapshot(Map<String, FlowNode> nodes, List<FlowConnection> connections, Set<String> selectedIds,
                              boolean function, List<FlowGraph.FunctionParameter> functionInputs,
                              List<FlowGraph.FunctionParameter> functionOutputs, List<FlowGraph.EditorJunction> editorJunctions,
                              List<FlowGraph.EditorPassthrough> editorPassthroughs) {
            this.nodes = nodes;
            this.connections = connections;
            this.selectedIds = selectedIds;
            this.function = function;
            this.functionInputs = functionInputs;
            this.functionOutputs = functionOutputs;
            this.editorJunctions = editorJunctions;
            this.editorPassthroughs = editorPassthroughs;
        }

        GraphSnapshot(Map<String, FlowNode> nodes, List<FlowConnection> connections, Set<String> selectedIds) {
            Map<String, FlowNode> copiedNodes = new HashMap<>();
            for (Map.Entry<String, FlowNode> entry : nodes.entrySet()) {
                FlowNode node = entry.getValue();
                copiedNodes.put(entry.getKey(), new FlowNode(
                        node.getType(),
                        node.getX(),
                        node.getY(),
                        new HashMap<>(node.getInputValues())
                ));
            }
            Map<String, FlowNode> immutableNodes = copiedNodes;
            List<FlowConnection> copiedConnections = new ArrayList<>(connections);
            Set<String> copiedSelectedIds = new HashSet<>(selectedIds);
            List<FlowGraph.FunctionParameter> emptyInputs = new ArrayList<>();
            List<FlowGraph.FunctionParameter> emptyOutputs = new ArrayList<>();
            List<FlowGraph.EditorJunction> emptyJunctions = new ArrayList<>();
            List<FlowGraph.EditorPassthrough> emptyPassthroughs = new ArrayList<>();
            this.nodes = immutableNodes;
            this.connections = copiedConnections;
            this.selectedIds = copiedSelectedIds;
            this.function = false;
            this.functionInputs = emptyInputs;
            this.functionOutputs = emptyOutputs;
            this.editorJunctions = emptyJunctions;
            this.editorPassthroughs = emptyPassthroughs;
        }

        GraphSnapshot(FlowGraph graph, Set<String> selectedIds) {
            this(
                copyNodes(graph.getNodes()),
                new ArrayList<>(graph.getConnections()),
                new HashSet<>(selectedIds),
                graph.isFunction(),
                copyFunctionParameters(graph.getFunctionInputs()),
                copyFunctionParameters(graph.getFunctionOutputs()),
                copyEditorJunctions(graph.getEditorJunctions()),
                copyEditorPassthroughs(graph.getEditorPassthroughs())
            );
        }

        private static Map<String, FlowNode> copyNodes(Map<String, FlowNode> nodes) {
            Map<String, FlowNode> copied = new HashMap<>();
            for (Map.Entry<String, FlowNode> entry : nodes.entrySet()) {
                FlowNode node = entry.getValue();
                copied.put(entry.getKey(), new FlowNode(
                    node.getType(),
                    node.getX(),
                    node.getY(),
                    new HashMap<>(node.getInputValues())
                ));
            }
            return copied;
        }
    }

    private record InboundBoundary(String sourceNodeId, String sourcePin, String targetNodeId, String targetPin) {
    }

    private record OutboundBoundary(String sourceNodeId, String sourcePin, String targetNodeId, String targetPin) {
    }

    private record WireSegment(double x1, double y1, double x2, double y2) {
    }

    private final List<GraphSnapshot> undoStack = new ArrayList<>();
    private final List<GraphSnapshot> redoStack = new ArrayList<>();
    private static final int MAX_UNDO_SIZE = 50;
    private boolean isUndoing = false;

    public FlowEditorScreen(FlowGraph graph, String serverId, Screen parent) {
        super();
        this.graph = graph;
        this.serverId = serverId;
        if (!(parent instanceof FlowEditorScreen)) {
            FlowEditorScreen.parent = parent;
        }
        this.dragState = new DragState();
        this.dragPinWidget = null;
        this.nodeItemSelector = null;
        OPEN_SCREENS.add(this);

        for (var entry : graph.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            FlowNodeWidget widget = new FlowNodeWidget((int)entry.getValue().getX(), (int)entry.getValue().getY(), entry.getValue(), graph, nodeId, serverId, () -> deleteNode(nodeId));
            addWorldWidget(widget);
            widgetCache.put(entry.getKey(), widget);
        }
    }

    public String getServerId() {
        return serverId;
    }

    public static void refreshCatalogForServer(String serverId) {
        for (FlowEditorScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.closeNodeItemSelector();
                screen.refreshNodeRegistry();
            }
        }
    }

    public String getFlowId() {
        return graph.getId();
    }

    public void applyGraph(FlowGraph sourceGraph) {
        if (sourceGraph == null) {
            return;
        }
        graph.setId(sourceGraph.getId());
        graph.getNodes().clear();
        if (sourceGraph.getNodes() != null) {
            for (Map.Entry<String, FlowNode> entry : sourceGraph.getNodes().entrySet()) {
                FlowNode node = entry.getValue();
                if (node == null) {
                    continue;
                }
                graph.getNodes().put(entry.getKey(), new FlowNode(
                        node.getType(),
                        node.getX(),
                        node.getY(),
                        node.getInputValues() != null ? new HashMap<>(node.getInputValues()) : new HashMap<>()
                ));
            }
        }
        graph.getConnections().clear();
        if (sourceGraph.getConnections() != null) {
            for (FlowConnection connection : sourceGraph.getConnections()) {
                if (connection == null) {
                    continue;
                }
                graph.getConnections().add(new FlowConnection(
                        connection.getSourceNodeId(),
                        connection.getSourcePin(),
                        connection.getTargetNodeId(),
                        connection.getTargetPin()
                ));
            }
        }
        graph.getLocalVariables().clear();
        if (sourceGraph.getLocalVariables() != null) {
            graph.getLocalVariables().addAll(sourceGraph.getLocalVariables());
        }
        graph.setFunction(sourceGraph.isFunction());
        graph.setFunctionInputs(copyFunctionParameters(sourceGraph.getFunctionInputs()));
        graph.setFunctionOutputs(copyFunctionParameters(sourceGraph.getFunctionOutputs()));
        graph.setEditorJunctions(copyEditorJunctions(sourceGraph.getEditorJunctions()));
        graph.setEditorPassthroughs(copyEditorPassthroughs(sourceGraph.getEditorPassthroughs()));
        ensureEditorJunctions();
        selectedNodeIds.clear();
        selectionBase.clear();
        selectedDragStartPositions.clear();
        focusedNode = null;
        dragState.sourceNodeId = null;
        dragState.sourcePin = null;
        dragState.isDragging = false;
        dragState.sourceIsInput = false;
        undoStack.clear();
        redoStack.clear();
        refreshNodeRegistry();
    }

    public String getDesktopAppId() {
        return "flow-editor";
    }

    public String getDesktopAppTitle() {
        return "Flow Editor";
    }

    public String getDesktopAppIconPath() {
        return "flow.png";
    }

    public void refreshNodeRegistry() {
        for (FlowNodeWidget widget : widgetCache.values()) {
            removeWorldWidget(widget);
        }
        widgetCache.clear();
        for (var entry : graph.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            FlowNodeWidget widget = new FlowNodeWidget((int)entry.getValue().getX(), (int)entry.getValue().getY(), entry.getValue(), graph, nodeId, serverId, () -> deleteNode(nodeId));
            addWorldWidget(widget);
            widgetCache.put(entry.getKey(), widget);
        }
        refreshPalette();
    }

    @Override
    public void init() {
        super.init();
        this.initialWidth = width;
        this.initialHeight = height;

        if (!initialized) {
            headerBackground = new IconButton.Builder().pos(10, 10).size(1, 28).entranceAnimation(false).build();
            headerBackground.active = false;
            addHudWidget(headerBackground);

            createPaletteSidePanel();
            createHeaderButtons();
            initialized = true;
        }

        if (headerBackground != null) {
            syncDebugHeaderVisibility();
            layoutHeaderButtons();
        }
    }

    private void refreshPalette() {
        if (paletteSidePanel == null) {
            return;
        }
        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(serverId)) {
            populateCategoryPopups();
        } else {
            populateFallbackPopups();
        }
    }

    private void createPaletteSidePanel() {
        paletteSidePanel = new SidePanel(this, "palettePanel", this::updatePositions).width(120).y(54).height(height - 65).show();
        paletteSidePanel.container().layout(new ManagedLayout()).columns(1).padding(5);

        categoryPopups.clear();
        categoryOrder = resolveCategoryOrder();
        for (NodeDefinition.NodeCategory category : categoryOrder) {
            PopupWidget popup = new PopupWidget.Builder(getCategoryLabel(category)).enableCollapseOnClose(true).build();
            popup.collapse(true);
            categoryPopups.put(category, popup);
            paletteSidePanel.addWidget(popup);
        }

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(serverId)) {
            populateCategoryPopups();
        } else {
            populateFallbackPopups();
        }
    }

    private void populateCategoryPopups() {
        List<NodeDefinition.NodeCategory> order = categoryOrder.isEmpty() ? resolveCategoryOrder() : categoryOrder;
        Map<NodeDefinition.NodeCategory, List<NodeDefinition>> categories = new HashMap<>();
        for (NodeDefinition.NodeCategory category : order) {
            categories.put(category, new ArrayList<>());
        }

        for (NodeDefinition def : NodeRegistry.getInstance().getAllDefinitions(serverId).values()) {
            if (def.isHidden() || !isAllowedInCurrentEditor(def)) {
                continue;
            }
            NodeDefinition.NodeCategory category = def.getCategory();
            if (def.getId().startsWith("event:")) {
                category = NodeDefinition.NodeCategory.EVENT;
            }
            categories.computeIfAbsent(category, ignored -> new ArrayList<>()).add(def);
        }

        Comparator<NodeDefinition> comparator = Comparator
                .comparingInt(NodeDefinition::getPriority)
                .thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER);

        for (NodeDefinition.NodeCategory category : order) {
            PopupWidget targetPopup = getCategoryPopup(category);
            if (targetPopup == null) {
                continue;
            }
            targetPopup.clearRows();
            List<NodeDefinition> nodes = categories.getOrDefault(category, new ArrayList<>());
            nodes.sort(comparator);
            if (isContentEditor() && category == NodeDefinition.NodeCategory.ABILITY && !nodes.isEmpty()) {
                targetPopup.collapse(false);
            }
            for (NodeDefinition def : nodes) {
                IconButton btn = new IconButton.Builder()
                        .label(def.getDisplayName())
                        .onClick(() -> addNodeAtCenter(def.getId()))
                        .build();
                targetPopup.addRow("", Collections.singletonList(btn), 20, true, false);
            }
        }
    }

    private List<NodeDefinition.NodeCategory> resolveCategoryOrder() {
        List<redxax.oxy.remotely.flow.sync.FlowCategoryMetadata> meta = NodeRegistry.getInstance().getServerCategories(serverId);
        List<NodeDefinition.NodeCategory> result = new ArrayList<>();
        for (redxax.oxy.remotely.flow.sync.FlowCategoryMetadata m : meta) {
            result.add(NodeDefinition.NodeCategory.fromString(m.getId()));
        }
        return result;
    }

    private void populateFallbackPopups() {
        PopupWidget eventsPopup = getCategoryPopup(NodeDefinition.NodeCategory.EVENT);
        PopupWidget actionsPopup = getCategoryPopup(NodeDefinition.NodeCategory.ACTION);
        PopupWidget logicPopup = getCategoryPopup(NodeDefinition.NodeCategory.LOGIC);
        PopupWidget dataPopup = getCategoryPopup(NodeDefinition.NodeCategory.DATA);

        if (eventsPopup != null) {
            eventsPopup.clearRows();
        }
        if (actionsPopup != null) {
            actionsPopup.clearRows();
        }
        if (logicPopup != null) {
            logicPopup.clearRows();
        }
        if (dataPopup != null) {
            dataPopup.clearRows();
        }
    }

    private PopupWidget getCategoryPopup(NodeDefinition.NodeCategory category) {
        return categoryPopups.get(category);
    }

    private String getCategoryLabel(NodeDefinition.NodeCategory category) {
        return category.getDisplayName();
    }

    FlowDebugController debugController() {
        FlowManager flowManager = FlowManager.getInstance();
        return flowManager != null ? flowManager.getDebugController() : null;
    }

    public void refreshDebugState() {
        FlowDebugController controller = debugController();
        debugMode = controller != null && controller.isEnabled();
        syncDebugHeaderVisibility();
        if (headerBackground != null) {
            layoutHeaderButtons();
        }
    }

    private boolean isContentEditor() {
        return CustomContentGraphAdapter.isContentGraph(graph);
    }

    private boolean isAllowedInCurrentEditor(NodeDefinition definition) {
        if (!isContentEditor()) {
            return true;
        }
        String id = definition.getId();
        if (id != null && (id.startsWith("custom_content.") || id.startsWith("ability."))) {
            return true;
        }
        NodeDefinition.NodeCategory category = definition.getCategory();
        return category == NodeDefinition.NodeCategory.LOGIC
            || category == NodeDefinition.NodeCategory.DATA
            || category == NodeDefinition.NodeCategory.VARIABLE
            || category == NodeDefinition.NodeCategory.FUNCTION
            || category == NodeDefinition.NodeCategory.ENTITY
            || category == NodeDefinition.NodeCategory.BLOCK
            || category == NodeDefinition.NodeCategory.ITEM
            || category == NodeDefinition.NodeCategory.WORLD
            || category == NodeDefinition.NodeCategory.VISUAL
            || category == NodeDefinition.NodeCategory.UTILITY;
    }

    private void addNodeAtCenter(String type) {
        double[] center = screenToWorld(width / 2.0, height / 2.0);
        int x = (int) (center[0] - 50);
        int y = (int) (center[1] - 20);
        addNode(x, y, type, null);
    }

    protected void createHeaderButtons() {
        IconButton backButton = new IconButton.Builder().size(18, 18).imagePath("close.png")
                .onClick(() -> {
                    if (parent != null) {
                        client.setScreen(parent);
                    } else {
                        close();
                    }
                })
                .build();
        addHeaderButton(backButton);

        IconButton saveButton = new IconButton.Builder()
                .size(18, 18)
                .imagePath("save.png")
                .onClick(this::onSave)
                .build();
        addHeaderButton(saveButton);

        IconButton organizeButton = new IconButton.Builder()
                .size(18, 18)
                .imagePath("layout.png")
                .onClick(this::organizeGraph)
                .build();
        addHeaderButton(organizeButton);

        debugToggleButton = new IconButton.Builder()
                .size(18, 18)
                .imagePath("report.png")
                .hint("Debug")
                .onClick(this::toggleDebugMode)
                .build();
        addHeaderButton(debugToggleButton);

        debugResumeButton = debugHeaderButton("Continue", 62, () -> {
            FlowDebugController debug = debugController();
            if (debug != null) {
                FlowDebugController.DebugSession session = debug.getActiveSession();
                debug.resume(serverId, session != null ? session.sessionId() : "");
            }
        });
        debugStepButton = debugHeaderButton("Step", 42, () -> stepDebug("into"));
        debugStopButton = debugHeaderButton("Stop", 42, () -> {
            FlowDebugController debug = debugController();
            if (debug != null) {
                FlowDebugController.DebugSession session = debug.getActiveSession();
                debug.stop(serverId, session != null ? session.sessionId() : "");
            }
        });
        addHeaderButton(debugResumeButton);
        addHeaderButton(debugStepButton);
        addHeaderButton(debugStopButton);

        if (showExtractButton()) {
            IconButton extractButton = new IconButton.Builder()
                    .size(18, 18)
                    .imagePath("copy.png")
                    .onClick(this::showExtractFunctionPopup)
                    .build();
            addHeaderButton(extractButton);
        }
        addCustomHeaderButtons();
        syncDebugHeaderVisibility();
    }

    private IconButton debugHeaderButton(String label, int width, Runnable action) {
        return new IconButton.Builder()
                .size(width, 18)
                .label(label)
                .hint(label)
                .onClick(action)
                .autoWidthOnTextChange(true)
                .build();
    }

    private void toggleDebugMode() {
        debugMode = !debugMode;
        FlowDebugController debug = debugController();
        if (debug != null) {
            debug.setEnabled(serverId, debugMode);
        }
        syncDebugHeaderVisibility();
        layoutHeaderButtons();
    }

    private void syncDebugHeaderVisibility() {
        boolean showControls = debugMode;
        if (debugResumeButton != null) {
            debugResumeButton.visible = showControls;
        }
        if (debugStepButton != null) {
            debugStepButton.visible = showControls;
        }
        if (debugStopButton != null) {
            debugStopButton.visible = showControls;
        }
    }

    private void stepDebug(String mode) {
        FlowDebugController debug = debugController();
        if (debug == null) {
            return;
        }
        FlowDebugController.DebugSession session = debug.getActiveSession();
        String sessionId = session != null ? session.sessionId() : "";
        switch (mode) {
            case "into" -> debug.stepInto(serverId, sessionId);
            case "over" -> debug.stepOver(serverId, sessionId);
            case "out" -> debug.stepOut(serverId, sessionId);
            default -> {
            }
        }
    }

    protected void addHeaderButton(IconButton button) {
        if (button == null) {
            return;
        }
        headerButtons.add(button);
        button.entranceAnimationEnabled = false;
        addDrawableChild(button);
    }

    protected boolean showExtractButton() {
        return true;
    }

    protected void addCustomHeaderButtons() {
    }

    private void showExtractFunctionPopup() {
        if (selectedNodeIds.isEmpty()) {
            new Notification("Error", "Select Nodes", Notification.Type.ERROR);
            return;
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("Extract Function").setResizable(false);
        TextInputWidget idInput = new TextInputWidget.Builder()
                .placeholder("function_id")
                .size(200, 22)
                .build();
        builder.addRow("ID", true, 22, idInput);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton extractButton = new AnimatedButton.Builder()
                .label("Extract")
                .accentType(ThemeManager.getAccent("nice"))
                .onClick(() -> {
                    String id = idInput.getText() != null ? idInput.getText().trim() : "";
                    if (!id.matches("^[a-zA-Z0-9_]+$")) {
                        new Notification("Error", "Invalid ID", Notification.Type.ERROR);
                        return;
                    }
                    if (extractSelectionToFunction(id)) {
                        if (popupRef[0] != null) {
                            popupRef[0].hide();
                        }
                    }
                })
                .build();

        builder.addRow("", true, 20, extractButton);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private boolean extractSelectionToFunction(String functionId) {
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null || serverId == null || functionId == null || functionId.isBlank()) {
            return false;
        }
        if (flowManager.getFlowsForServer(serverId).containsKey(functionId)) {
            new Notification("Error", "Function ID Exists", Notification.Type.ERROR);
            return false;
        }
        if (selectedNodeIds.isEmpty()) {
            new Notification("Error", "Select Nodes", Notification.Type.ERROR);
            return false;
        }
        captureSnapshot();

        Set<String> selected = new HashSet<>(selectedNodeIds);
        List<InboundBoundary> inbound = new ArrayList<>();
        List<OutboundBoundary> outbound = new ArrayList<>();
        List<FlowConnection> internal = new ArrayList<>();

        for (FlowConnection connection : graph.getConnections()) {
            boolean sourceSelected = selected.contains(connection.getSourceNodeId());
            boolean targetSelected = selected.contains(connection.getTargetNodeId());
            if (sourceSelected && targetSelected) {
                internal.add(new FlowConnection(
                        connection.getSourceNodeId(),
                        connection.getSourcePin(),
                        connection.getTargetNodeId(),
                        connection.getTargetPin()
                ));
                continue;
            }
            if (!sourceSelected && targetSelected) {
                inbound.add(new InboundBoundary(
                        connection.getSourceNodeId(),
                        connection.getSourcePin(),
                        connection.getTargetNodeId(),
                        connection.getTargetPin()
                ));
                continue;
            }
            if (sourceSelected) {
                outbound.add(new OutboundBoundary(
                        connection.getSourceNodeId(),
                        connection.getSourcePin(),
                        connection.getTargetNodeId(),
                        connection.getTargetPin()
                ));
            }
        }

        FlowGraph functionGraph = flowManager.createFlow(serverId, functionId, true);
        functionGraph.getNodes().clear();
        functionGraph.getConnections().clear();
        functionGraph.getLocalVariables().clear();
        functionGraph.setFunction(true);
        functionGraph.setFunctionInputs(new ArrayList<>());
        functionGraph.setFunctionOutputs(new ArrayList<>());

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        double centerX = 0;
        double centerY = 0;
        int count = 0;

        for (String nodeId : selected) {
            FlowNode node = graph.getNodes().get(nodeId);
            if (node == null) {
                continue;
            }
            functionGraph.getNodes().put(nodeId, new FlowNode(
                    node.getType(),
                    node.getX(),
                    node.getY(),
                    node.getInputValues() != null ? new HashMap<>(node.getInputValues()) : new HashMap<>())
            );
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX());
            maxY = Math.max(maxY, node.getY());
            centerX += node.getX();
            centerY += node.getY();
            count++;
        }
        if (count == 0) {
            return false;
        }
        centerX /= count;
        centerY /= count;

        functionGraph.getConnections().addAll(internal);
        String functionStartId = UUID.randomUUID().toString();
        String functionEndId = UUID.randomUUID().toString();

        functionGraph.getNodes().put(functionStartId, new FlowNode("function_start", minX - 220, minY, new HashMap<>()));
        functionGraph.getNodes().put(functionEndId, new FlowNode("function_end", maxX + 220, maxY, new HashMap<>()));

        Set<String> entryTargets = new HashSet<>();
        Set<String> exitSources = new HashSet<>();
        for (InboundBoundary connection : inbound) {
            if ("flow".equals(connection.targetPin())) {
                entryTargets.add(connection.targetNodeId());
            }
        }
        for (OutboundBoundary connection : outbound) {
            if ("flow".equals(connection.sourcePin())) {
                exitSources.add(connection.sourceNodeId());
            }
        }
        for (String entryTarget : entryTargets) {
            functionGraph.getConnections().add(new FlowConnection(functionStartId, "flow", entryTarget, "flow"));
        }
        for (String exitSource : exitSources) {
            functionGraph.getConnections().add(new FlowConnection(exitSource, "flow", functionEndId, "flow"));
        }

        Map<InboundBoundary, String> inboundParams = new HashMap<>();
        Map<OutboundBoundary, String> outboundParams = new HashMap<>();
        Set<String> usedInputNames = new HashSet<>();
        Set<String> usedOutputNames = new HashSet<>();

        for (InboundBoundary connection : inbound) {
            if ("flow".equals(connection.targetPin())) {
                continue;
            }
            String parameterName = uniqueParameterName(connection.targetPin(), usedInputNames);
            usedInputNames.add(parameterName);
            FlowDataType parameterType = resolveTargetPinType(connection.targetNodeId(), connection.targetPin());
            functionGraph.getFunctionInputs().add(new FlowGraph.FunctionParameter(parameterName, parameterType));
            functionGraph.getConnections().add(new FlowConnection(functionStartId, parameterName, connection.targetNodeId(), connection.targetPin()));
            inboundParams.put(connection, parameterName);
        }

        for (OutboundBoundary connection : outbound) {
            if ("flow".equals(connection.sourcePin())) {
                continue;
            }
            String parameterName = uniqueParameterName(connection.sourcePin(), usedOutputNames);
            usedOutputNames.add(parameterName);
            FlowDataType parameterType = resolveSourcePinType(connection.sourceNodeId(), connection.sourcePin());
            functionGraph.getFunctionOutputs().add(new FlowGraph.FunctionParameter(parameterName, parameterType));
            functionGraph.getConnections().add(new FlowConnection(connection.sourceNodeId(), connection.sourcePin(), functionEndId, parameterName));
            outboundParams.put(connection, parameterName);
        }

        flowManager.saveFlow(serverId, functionGraph);

        String callNodeType = CUSTOM_FUNCTION_NODE_PREFIX + functionId;
        NodeDefinition callDef = buildCustomFunctionNodeDefinition(callNodeType, functionId, functionGraph);
        if (NodeRegistry.getInstance() != null) {
            NodeRegistry.getInstance().registerServerDefinition(serverId, callDef);
        }

        graph.getConnections().removeIf(connection -> selected.contains(connection.getSourceNodeId()) || selected.contains(connection.getTargetNodeId()));
        for (String nodeId : selected) {
            FlowNodeWidget widget = widgetCache.remove(nodeId);
            if (widget != null) {
                removeWorldWidget(widget);
            }
            graph.getNodes().remove(nodeId);
        }

        String callNodeId = UUID.randomUUID().toString();
        FlowNode callNode = new FlowNode(callNodeType, centerX, centerY, new HashMap<>());
        graph.getNodes().put(callNodeId, callNode);
        FlowNodeWidget callWidget = new FlowNodeWidget((int) centerX, (int) centerY, callNode, graph, callNodeId, serverId, () -> deleteNode(callNodeId));
        addWorldWidget(callWidget);
        widgetCache.put(callNodeId, callWidget);

        for (InboundBoundary connection : inbound) {
            if ("flow".equals(connection.targetPin())) {
                graph.getConnections().add(new FlowConnection(connection.sourceNodeId(), connection.sourcePin(), callNodeId, "flow"));
                continue;
            }
            String parameterName = inboundParams.get(connection);
            if (parameterName == null) {
                continue;
            }
            removeExistingInputConnection(callNodeId, parameterName);
            graph.getConnections().add(new FlowConnection(connection.sourceNodeId(), connection.sourcePin(), callNodeId, parameterName));
        }

        for (OutboundBoundary connection : outbound) {
            if ("flow".equals(connection.sourcePin())) {
                removeExistingInputConnection(connection.targetNodeId(), connection.targetPin());
                graph.getConnections().add(new FlowConnection(callNodeId, "flow", connection.targetNodeId(), connection.targetPin()));
                continue;
            }
            String parameterName = outboundParams.get(connection);
            if (parameterName == null) {
                continue;
            }
            removeExistingInputConnection(connection.targetNodeId(), connection.targetPin());
            graph.getConnections().add(new FlowConnection(callNodeId, parameterName, connection.targetNodeId(), connection.targetPin()));
        }

        refreshInputWidgets(callNodeId);
        for (InboundBoundary connection : inbound) {
            refreshInputWidgets(connection.sourceNodeId());
            refreshInputWidgets(connection.targetNodeId());
        }
        for (OutboundBoundary connection : outbound) {
            refreshInputWidgets(connection.targetNodeId());
        }

        selectedNodeIds.clear();
        selectedNodeIds.add(callNodeId);
        focusedNode = callWidget;
        new Notification("Function Extracted", functionId, Notification.Type.SUCCESS);
        return true;
    }

    private String uniqueParameterName(String baseName, Set<String> usedNames) {
        String normalized = (baseName == null || baseName.isBlank()) ? "value" : baseName.trim();
        normalized = normalized.replaceAll("[^a-zA-Z0-9_]", "_");
        if (!normalized.matches("^[a-zA-Z_].*")) {
            normalized = "p_" + normalized;
        }
        String candidate = normalized;
        int index = 2;
        while (usedNames.contains(candidate)) {
            candidate = normalized + "_" + index;
            index++;
        }
        return candidate;
    }

    private FlowDataType resolveTargetPinType(String nodeId, String pinName) {
        if (nodeId == null || pinName == null || nodeId.isBlank() || pinName.isBlank()) {
            return FlowDataType.ANY;
        }
        FlowNodeWidget widget = widgetCache.get(nodeId);
        if (widget == null) {
            return FlowDataType.ANY;
        }
        FlowDataType type = widget.getPinType(pinName, true);
        return type != null ? type : FlowDataType.ANY;
    }

    private FlowDataType resolveSourcePinType(String nodeId, String pinName) {
        if (nodeId == null || pinName == null || nodeId.isBlank() || pinName.isBlank()) {
            return FlowDataType.ANY;
        }
        FlowNodeWidget widget = widgetCache.get(nodeId);
        if (widget == null) {
            return FlowDataType.ANY;
        }
        FlowDataType type = widget.getPinType(pinName, false);
        return type != null ? type : FlowDataType.ANY;
    }

    private NodeDefinition buildCustomFunctionNodeDefinition(String nodeType, String functionId, FlowGraph functionGraph) {
        NodeDefinition.Builder builder = new NodeDefinition.Builder(nodeType, formatFunctionDisplayName(functionId), NodeDefinition.NodeCategory.FUNCTION);
        builder.input("flow", NodeDefinition.PinType.FLOW, FlowDataType.EXECUTION);
        builder.output("flow", NodeDefinition.PinType.FLOW, FlowDataType.EXECUTION);
        if (functionGraph.getFunctionInputs() != null) {
            for (FlowGraph.FunctionParameter param : functionGraph.getFunctionInputs()) {
                if (param != null && param.getName() != null && !param.getName().isBlank()) {
                    builder.input(param.getName(), NodeDefinition.PinType.DATA, param.getType() != null ? param.getType() : FlowDataType.ANY);
                }
            }
        }
        if (functionGraph.getFunctionOutputs() != null) {
            for (FlowGraph.FunctionParameter param : functionGraph.getFunctionOutputs()) {
                if (param != null && param.getName() != null && !param.getName().isBlank()) {
                    builder.output(param.getName(), NodeDefinition.PinType.DATA, param.getType() != null ? param.getType() : FlowDataType.ANY);
                }
            }
        }
        builder.priority(220).color(NodeDefinition.NodeCategory.FUNCTION);
        return builder.build();
    }

    private String formatFunctionDisplayName(String functionId) {
        if (functionId == null || functionId.isBlank()) return "Function";
        String[] parts = functionId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void layoutHeaderButtons() {
        int padding = 5;
        int totalWidth = 0;

        for (IconButton button : headerButtons) {
            if (button.visible) {
                totalWidth += button.getWidth();
            }
        }
        long visibleButtons = headerButtons.stream().filter(button -> button.visible).count();
        totalWidth += Math.max(0, (int) visibleButtons - 1) * padding;

        headerBackground.setWidth(totalWidth + (padding * 2));
        headerBackground.setHeight(30);
        headerBackground.setPosition(width - headerBackground.getWidth() - 10, 10);

        int startY = headerBackground.getY();
        int currentX = headerBackground.getX() + headerBackground.getWidth() - padding;
        for (IconButton button : headerButtons) {
            if (!button.visible) {
                continue;
            }
            currentX -= button.getWidth();
            button.setPosition(currentX, startY + (headerBackground.getHeight() - button.getHeight()) / 2);
            currentX -= padding;
        }
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        if (paletteSidePanel != null && paletteSidePanel.isVisible()) {
            paletteSidePanel.height(height - 65).y(54).update();
        }
        if (headerBackground != null) {
            syncDebugHeaderVisibility();
            layoutHeaderButtons();
        }
    }

    private void organizeGraph() {
        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return;
        }
        captureSnapshot();

        Map<String, List<String>> predecessors = new HashMap<>();
        for (String nodeId : graph.getNodes().keySet()) {
            predecessors.put(nodeId, new ArrayList<>());
        }
        if (graph.getConnections() != null) {
            for (FlowConnection connection : graph.getConnections()) {
                if (connection == null) {
                    continue;
                }
                if (!graph.getNodes().containsKey(connection.getSourceNodeId()) || !graph.getNodes().containsKey(connection.getTargetNodeId())) {
                    continue;
                }
                predecessors.computeIfAbsent(connection.getTargetNodeId(), ignored -> new ArrayList<>()).add(connection.getSourceNodeId());
            }
        }

        Map<String, Integer> layers = new HashMap<>();
        for (String nodeId : graph.getNodes().keySet()) {
            computeOrganizeLayer(nodeId, predecessors, layers, new HashSet<>());
        }

        Map<Integer, List<String>> layerNodes = new LinkedHashMap<>();
        int maxLayer = 0;
        for (Map.Entry<String, Integer> entry : layers.entrySet()) {
            int layer = Math.max(0, entry.getValue());
            maxLayer = Math.max(maxLayer, layer);
            layerNodes.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(entry.getKey());
        }
        for (int layer = 0; layer <= maxLayer; layer++) {
            layerNodes.computeIfAbsent(layer, ignored -> new ArrayList<>());
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxWidth = 0;
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            FlowNode node = entry.getValue();
            FlowNodeWidget widget = widgetCache.get(entry.getKey());
            if (node == null) {
                continue;
            }
            minX = Math.min(minX, (int) Math.round(node.getX()));
            minY = Math.min(minY, (int) Math.round(node.getY()));
            if (widget != null) {
                maxWidth = Math.max(maxWidth, widget.getWidth());
            }
        }
        if (minX == Integer.MAX_VALUE) {
            minX = (int) screenToWorld(width / 2.0, height / 2.0)[0];
            minY = (int) screenToWorld(width / 2.0, height / 2.0)[1];
        }

        int xSpacing = Math.max(280, maxWidth + 120);
        int ySpacing = 48;
        for (int layer = 0; layer <= maxLayer; layer++) {
            List<String> nodes = layerNodes.getOrDefault(layer, new ArrayList<>());
            nodes.sort(Comparator
                .comparingInt(this::organizeSortPriority)
                .thenComparingDouble(id -> graph.getNodes().get(id).getY())
                .thenComparingDouble(id -> graph.getNodes().get(id).getX())
                .thenComparing(id -> id));
            int y = minY;
            for (String nodeId : nodes) {
                FlowNode node = graph.getNodes().get(nodeId);
                FlowNodeWidget widget = widgetCache.get(nodeId);
                if (node == null || widget == null) {
                    continue;
                }
                int x = minX + layer * xSpacing;
                widget.setX(x);
                widget.setY(y);
                node.setX(x);
                node.setY(y);
                y += widget.getHeight() + ySpacing;
            }
        }

        organizeEditorJunctions();
    }

    private int computeOrganizeLayer(String nodeId, Map<String, List<String>> predecessors, Map<String, Integer> layers, Set<String> visiting) {
        Integer existing = layers.get(nodeId);
        if (existing != null) {
            return existing;
        }
        if (!visiting.add(nodeId)) {
            return 0;
        }
        int layer = 0;
        for (String predecessor : predecessors.getOrDefault(nodeId, new ArrayList<>())) {
            if (predecessor == null || predecessor.equals(nodeId)) {
                continue;
            }
            layer = Math.max(layer, computeOrganizeLayer(predecessor, predecessors, layers, visiting) + 1);
        }
        visiting.remove(nodeId);
        layers.put(nodeId, layer);
        return layer;
    }

    private int organizeSortPriority(String nodeId) {
        FlowNode node = graph.getNodes().get(nodeId);
        if (node == null || node.getType() == null) {
            return 50;
        }
        String type = node.getType();
        if (type.startsWith("event:") || "function_start".equals(type)) {
            return 0;
        }
        if ("function_end".equals(type)) {
            return 90;
        }
        return 50;
    }

    public void showNodeInputSelector(List<String> options, String selected, Consumer<String> onSelected, int worldX, int worldY) {
        closeNodeItemSelector();
        if (options == null || options.isEmpty() || onSelected == null) {
            return;
        }
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(this)
                .size(180, 220)
                .dismissOnSelect(true)
                .onClose(() -> removeNodeItemSelector(selectorRef[0]))
                .build();
        selectorRef[0] = selector;
        for (String option : options) {
            selector.addItem(option, () -> onSelected.accept(option));
        }
        selector.setSelectedItem(selected);
        nodeItemSelector = selector;
        addDrawableChild(nodeItemSelector);
        double[] screen = worldToScreen(worldX, worldY);
        nodeItemSelector.show((int) screen[0], (int) screen[1]);
    }

    @Override
    public void close() {
        OPEN_SCREENS.remove(this);
        if (parent != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void renderHandler(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateTransforms(delta);

        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        int undistortedMouseX = (int) undistortedCoords[0];
        int undistortedMouseY = (int) undistortedCoords[1];

        if (dragState.isDragging) {
            dragMouseX = undistortedMouseX;
            dragMouseY = undistortedMouseY;
        }

        renderBackground(context, mouseX, mouseY, delta);

        context.getMatrices().push();
        context.getMatrices().translate(getWidth() / 2.0f, getHeight() / 2.0f, 0);
        context.getMatrices().scale(zoomLevel, zoomLevel, 1.0f);
        context.getMatrices().translate(-getWidth() / 2.0f + panX, -getHeight() / 2.0f + panY, 0);

        double[] worldMouse = screenToWorld(undistortedMouseX, undistortedMouseY);
        int worldMouseX = (int) worldMouse[0];
        int worldMouseY = (int) worldMouse[1];
        IDrawContext worldContext = createWorldDrawContext(context);

        renderWires(worldContext);
        renderDebugWireOverlay(worldContext);

        FlowDebugController debug = debugController();
        for (Widget widget : worldWidgets) {
            if (widget instanceof FlowNodeWidget flowNodeWidget) {
                String nodeId = findNodeId(flowNodeWidget);
                flowNodeWidget.setSelected(nodeId != null && selectedNodeIds.contains(nodeId));
                boolean breakpoint = debug != null && nodeId != null && debug.hasBreakpoint(graph, nodeId);
                boolean pausedHere = debug != null && nodeId != null && debug.isPausedAt(graph.getId(), nodeId);
                if (pausedHere && breakpoint) {
                    flowNodeWidget.setAccent(ThemeManager.getAccent("calm"));
                } else if (pausedHere) {
                    flowNodeWidget.setAccent(ThemeManager.getAccent("nice"));
                } else {
                    flowNodeWidget.setAccent(breakpoint ? ThemeManager.getAccent("danger") : ThemeManager.getDefaultAccent());
                }
            }
            widget.render(worldContext, worldMouseX, worldMouseY, delta);
        }
        renderDebugNodeOverlay(worldContext);
        for (Widget widget : worldWidgets) {
            if (widget instanceof AnimatedWidget animated) {
                animated.renderHintOverlay(worldContext);
            }
        }
        context.getMatrices().pop();

        renderSelectionBox(context);

        for (Widget widget : hudWidgets) {
            widget.render(context, mouseX, mouseY, delta);
        }

        if (paletteSidePanel != null) {
            paletteSidePanel.update();
            paletteSidePanel.renderHeader(context);
        }

        for (Widget widget : hudWidgets) {
            if (widget instanceof AnimatedWidget animated) {
                animated.renderHintOverlay(context);
            }
        }
    }

    private void renderWires(IDrawContext context) {
        if (graph.getConnections() == null) return;

        normalizePassthroughConnections();
        ensureEditorJunctions();
        Set<String> renderedJunctionSources = new HashSet<>();
        for (FlowConnection conn : graph.getConnections()) {
            String sourceNodeId = editorSourceNodeId(conn);
            String sourcePin = editorSourcePin(conn);
            FlowNodeWidget source = widgetCache.get(sourceNodeId);
            FlowNodeWidget target = widgetCache.get(conn.getTargetNodeId());

            if (source != null && target != null) {
                double[] start = source.getPinBounds(sourcePin, false);
                double[] end = target.getPinBounds(conn.getTargetPin(), true);

                if (start != null && end != null) {
                    float startX = (float) (start[0] + start[2]/2);
                    float startY = (float) (start[1] + start[3]/2);
                    float endX = (float) (end[0] + end[2]/2);
                    float endY = (float) (end[1] + end[3]/2);

                    FlowDataType sourceType = source.getPinType(sourcePin, false);
                    int wireColor = (sourceType != null) ? sourceType.getColor() : ThemeManager.getColor(ThemeColor.innerBorder);
                    FlowGraph.EditorPassthrough passthrough = findPassthroughRoute(conn);
                    if (passthrough != null) {
                        FlowNodeWidget passthroughWidget = widgetCache.get(passthrough.getNodeId());
                        double[] output = passthroughWidget != null ? passthroughWidget.getPinBounds(NodeWidget.passthroughOutputPin(passthrough.getInputPin()), false) : null;
                        if (output != null) {
                            if (!passthrough.getNodeId().equals(conn.getTargetNodeId()) || !passthrough.getInputPin().equals(conn.getTargetPin())) {
                                drawWire(context, (float) (output[0] + output[2] / 2), (float) (output[1] + output[3] / 2), endX, endY, wireColor, getWireLaneOffset(conn) / 2);
                            }
                            continue;
                        }
                    }
                    FlowGraph.EditorJunction junction = findEditorJunction(sourceNodeId, sourcePin);
                    if (junction != null && fanoutCount(sourceNodeId, sourcePin) > 1) {
                        String key = sourceNodeId + ":" + sourcePin;
                        if (renderedJunctionSources.add(key)) {
                            drawWire(context, startX, startY, (float) junction.getX(), (float) junction.getY(), wireColor, 0);
                        }
                        drawWire(context, (float) junction.getX(), (float) junction.getY(), endX, endY, wireColor, getWireLaneOffset(conn) / 2);
                    } else {
                        int laneOffset = getWireLaneOffset(conn);
                        drawWire(context, startX, startY, endX, endY, wireColor, laneOffset);
                    }
                }
            }
        }

        renderEditorJunctions(context);

        if (dragState.isDragging && dragState.sourceNodeId != null) {
            double[] sourcePinWorld = null;
            FlowNodeWidget source = widgetCache.get(dragState.sourceNodeId);
            FlowDataType sourceType = null;
            if (dragSourceJunction != null) {
                sourcePinWorld = new double[] { dragSourceJunction.getX(), dragSourceJunction.getY() };
            } else if (source != null) {
                double[] bounds = source.getPinBounds(dragState.sourcePin, dragState.sourceIsInput);
                if (bounds != null) {
                    sourcePinWorld = new double[] { bounds[0] + bounds[2]/2, bounds[1] + bounds[3]/2 };
                }
            }
            if (source != null) {
                sourceType = source.getPinType(dragState.sourcePin, dragState.sourceIsInput);
            }

            if (sourcePinWorld != null) {
                double[] mouseWorld = screenToWorld(dragMouseX, dragMouseY);
                int dragWireColor = (sourceType != null) ? sourceType.getColor() : (ThemeManager.getColor(ThemeColor.innerBorder) & 0x00FFFFFF) | 0x88000000;
                drawWire(context, (float)sourcePinWorld[0], (float)sourcePinWorld[1], (float)mouseWorld[0], (float)mouseWorld[1], dragWireColor, 0);
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        dragMouseX = undistortedCoords[0];
        dragMouseY = undistortedCoords[1];
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (handleNodeItemSelectorMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        dragMouseX = undistortedCoords[0];
        dragMouseY = undistortedCoords[1];
        double[] worldMouse = screenToWorld(dragMouseX, dragMouseY);
        int wx = (int) worldMouse[0];
        int wy = (int) worldMouse[1];

        if (isSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectionEndX = undistortedCoords[0];
            selectionEndY = undistortedCoords[1];
            updateSelectionFromBox();
            return true;
        }

        if (draggedJunction != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggedJunction.setX(worldMouse[0] - junctionDragOffsetX);
            draggedJunction.setY(worldMouse[1] - junctionDragOffsetY);
            return true;
        }

        if (dragState.isDragging) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && movingSelectedNodes && draggedWidget instanceof FlowNodeWidget draggedNode) {
            double[] worldMouseNow = screenToWorld(dragMouseX, dragMouseY);
            int newX = (int) (worldMouseNow[0] - dragOffsetX);
            int newY = (int) (worldMouseNow[1] - dragOffsetY);
            String draggedNodeId = findNodeId(draggedNode);
            int[] draggedStart = draggedNodeId != null ? selectedDragStartPositions.get(draggedNodeId) : null;
            if (draggedStart != null) {
                int moveX = newX - draggedStart[0];
                int moveY = newY - draggedStart[1];
                for (String nodeId : selectedNodeIds) {
                    FlowNodeWidget widget = widgetCache.get(nodeId);
                    int[] start = selectedDragStartPositions.get(nodeId);
                    if (widget != null && start != null) {
                        widget.setX(start[0] + moveX);
                        widget.setY(start[1] + moveY);
                    }
                }
                return true;
            }
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (widget.mouseDragged(wx, wy, button, deltaX, deltaY)) {
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void drawWire(IDrawContext context, float startX, float startY, float endX, float endY, int color, int laneOffset) {
        int alpha = (color >> 24) & 0xFF;
        int borderBase = ThemeManager.getColor(ThemeColor.innerBorder);
        int borderColor = (borderBase & 0x00FFFFFF) | (alpha << 24);

        int x1 = Math.round(startX);
        int y1 = Math.round(startY);
        int x2 = Math.round(endX);
        int y2 = Math.round(endY);
        int outX = x1 + WIRE_OUT_OFFSET + laneOffset;
        int inX = x2 - WIRE_OUT_OFFSET - laneOffset;
        int midY = Math.round((startY + endY) / 2f) + laneOffset;

        for (WireSegment segment : wireSegments(x1, y1, x2, y2, laneOffset)) {
            drawSegmentBorder(context, (int) segment.x1(), (int) segment.y1(), (int) segment.x2(), (int) segment.y2(), borderColor);
        }
        for (WireSegment segment : wireSegments(x1, y1, x2, y2, laneOffset)) {
            drawSegmentFill(context, (int) segment.x1(), (int) segment.y1(), (int) segment.x2(), (int) segment.y2(), color);
        }
    }

    private List<WireSegment> wireSegments(double x1, double y1, double x2, double y2, int laneOffset) {
        int startX = Math.round((float) x1);
        int startY = Math.round((float) y1);
        int endX = Math.round((float) x2);
        int endY = Math.round((float) y2);
        int outX = startX + WIRE_OUT_OFFSET + laneOffset;
        int inX = endX - WIRE_OUT_OFFSET - laneOffset;
        int midY = Math.round((startY + endY) / 2f) + laneOffset;
        return List.of(
            new WireSegment(startX, startY, outX, startY),
            new WireSegment(outX, startY, outX, midY),
            new WireSegment(outX, midY, inX, midY),
            new WireSegment(inX, midY, inX, endY),
            new WireSegment(inX, endY, endX, endY)
        );
    }

    private void drawSegmentBorder(IDrawContext context, int x1, int y1, int x2, int y2, int color) {
        drawSegment(context, x1, y1, x2, y2, color, 5);
    }

    private void drawSegmentFill(IDrawContext context, int x1, int y1, int x2, int y2, int color) {
        drawSegment(context, x1, y1, x2, y2, color, 3);
    }

    private void drawSegment(IDrawContext context, int x1, int y1, int x2, int y2, int color, int thickness) {
        int half = thickness / 2;

        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            context.fill(x1 - half, minY - half, x1 + half + 1, maxY + half + 1, color);
            return;
        }

        if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            context.fill(minX - half, y1 - half, maxX + half + 1, y1 + half + 1, color);
            return;
        }

        context.fill(x1 - half, y1 - half, x1 + half + 1, y1 + half + 1, color);
    }

    private int getWireLaneOffset(FlowConnection conn) {
        String key = editorSourceNodeId(conn) + ":" + editorSourcePin(conn) + ">" + conn.getTargetNodeId() + ":" + conn.getTargetPin();
        int lane = Math.floorMod(key.hashCode(), 5) - 2;
        return lane * WIRE_LANE_SPACING;
    }

    private void ensureEditorJunctions() {
        if (graph.getConnections() == null) {
            return;
        }
        Map<String, List<FlowConnection>> fanouts = new LinkedHashMap<>();
        for (FlowConnection connection : graph.getConnections()) {
            String sourceNodeId = editorSourceNodeId(connection);
            String sourcePin = editorSourcePin(connection);
            FlowNodeWidget source = widgetCache.get(sourceNodeId);
            if (source == null || source.getPinKind(sourcePin, false) != NodeDefinition.PinType.DATA) {
                continue;
            }
            fanouts.computeIfAbsent(sourceNodeId + ":" + sourcePin, ignored -> new ArrayList<>()).add(connection);
        }
        Set<String> activeFanouts = new HashSet<>();
        for (Map.Entry<String, List<FlowConnection>> entry : fanouts.entrySet()) {
            if (entry.getValue().size() > 1) {
                activeFanouts.add(entry.getKey());
            }
        }
        graph.getEditorJunctions().removeIf(junction -> !activeFanouts.contains(junction.getSourceNodeId() + ":" + junction.getSourcePin()));
        for (Map.Entry<String, List<FlowConnection>> entry : fanouts.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            FlowConnection first = entry.getValue().getFirst();
            if (findEditorJunction(first.getSourceNodeId(), first.getSourcePin()) != null) {
                continue;
            }
            FlowGraph.EditorJunction junction = new FlowGraph.EditorJunction(UUID.randomUUID().toString(), first.getSourceNodeId(), first.getSourcePin(), 0, 0);
            if (placeEditorJunction(junction)) {
                graph.getEditorJunctions().add(junction);
            }
        }
    }

    private void organizeEditorJunctions() {
        ensureEditorJunctions();
        if (graph.getConnections() == null) {
            return;
        }
        for (FlowGraph.EditorJunction junction : graph.getEditorJunctions()) {
            placeEditorJunction(junction);
        }
    }

    private boolean placeEditorJunction(FlowGraph.EditorJunction junction) {
        FlowNodeWidget source = widgetCache.get(junction.getSourceNodeId());
        if (source == null || graph.getConnections() == null) {
            return false;
        }
        double[] sourceBounds = source.getPinBounds(junction.getSourcePin(), false);
        if (sourceBounds == null) {
            return false;
        }
        double sourceX = sourceBounds[0] + sourceBounds[2] / 2;
        double sourceY = sourceBounds[1] + sourceBounds[3] / 2;
        List<Double> targetYs = new ArrayList<>();
        double minTargetX = Double.MAX_VALUE;
        for (FlowConnection connection : graph.getConnections()) {
            if (!junction.getSourceNodeId().equals(editorSourceNodeId(connection)) || !junction.getSourcePin().equals(editorSourcePin(connection))) {
                continue;
            }
            FlowNodeWidget target = widgetCache.get(connection.getTargetNodeId());
            double[] targetBounds = target != null ? target.getPinBounds(connection.getTargetPin(), true) : null;
            if (targetBounds == null) {
                continue;
            }
            minTargetX = Math.min(minTargetX, targetBounds[0] + targetBounds[2] / 2);
            targetYs.add(targetBounds[1] + targetBounds[3] / 2);
        }
        targetYs.sort(Double::compareTo);
        double targetY = targetYs.isEmpty() ? sourceY : targetYs.get(targetYs.size() / 2);
        double targetX = minTargetX == Double.MAX_VALUE ? sourceX + 180 : minTargetX;
        double junctionX = targetX > sourceX + 180 ? sourceX + Math.min(220, (targetX - sourceX) * 0.45) : sourceX + 110;
        junction.setX(Math.round(junctionX));
        junction.setY(Math.round(targetY));
        return true;
    }

    private FlowGraph.EditorJunction findEditorJunction(String sourceNodeId, String sourcePin) {
        for (FlowGraph.EditorJunction junction : graph.getEditorJunctions()) {
            if (sourceNodeId.equals(junction.getSourceNodeId()) && sourcePin.equals(junction.getSourcePin())) {
                return junction;
            }
        }
        return null;
    }

    private String editorSourceNodeId(FlowConnection connection) {
        if (connection == null) {
            return "";
        }
        String editorNodeId = connection.getEditorSourceNodeId();
        return editorNodeId != null && !editorNodeId.isBlank() ? editorNodeId : connection.getSourceNodeId();
    }

    private String editorSourcePin(FlowConnection connection) {
        if (connection == null) {
            return "";
        }
        String editorPin = connection.getEditorSourcePin();
        return editorPin != null && !editorPin.isBlank() ? editorPin : connection.getSourcePin();
    }

    private FlowGraph.EditorPassthrough findPassthroughRoute(FlowConnection connection) {
        if (connection != null && connection.getEditorSourceNodeId() != null && !connection.getEditorSourceNodeId().isBlank()) {
            return null;
        }
        if (connection == null || graph.getConnections() == null) {
            return null;
        }
        FlowGraph.EditorPassthrough best = null;
        double bestScore = Double.MAX_VALUE;
        for (FlowGraph.EditorPassthrough passthrough : graph.getEditorPassthroughs()) {
            if (passthrough == null) {
                continue;
            }
            if (passthrough.getNodeId().equals(connection.getTargetNodeId()) && passthrough.getInputPin().equals(connection.getTargetPin())) {
                continue;
            }
            FlowConnection incoming = findIncomingConnection(passthrough.getNodeId(), passthrough.getInputPin());
            if (incoming == null) {
                continue;
            }
            if (connection.getSourceNodeId().equals(incoming.getSourceNodeId()) && connection.getSourcePin().equals(incoming.getSourcePin())) {
                double score = passthroughRouteScore(connection, passthrough);
                if (score < bestScore) {
                    best = passthrough;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private double passthroughRouteScore(FlowConnection connection, FlowGraph.EditorPassthrough passthrough) {
        FlowNodeWidget passthroughWidget = widgetCache.get(passthrough.getNodeId());
        FlowNodeWidget targetWidget = widgetCache.get(connection.getTargetNodeId());
        if (passthroughWidget == null || targetWidget == null) {
            return Double.MAX_VALUE;
        }
        double passthroughCenterX = passthroughWidget.getX() + passthroughWidget.getWidth() / 2.0;
        double passthroughCenterY = passthroughWidget.getY() + passthroughWidget.getHeight() / 2.0;
        double targetCenterX = targetWidget.getX() + targetWidget.getWidth() / 2.0;
        double targetCenterY = targetWidget.getY() + targetWidget.getHeight() / 2.0;
        double dx = targetCenterX - passthroughCenterX;
        double dy = targetCenterY - passthroughCenterY;
        return dx * dx + dy * dy;
    }

    private FlowConnection findIncomingConnection(String nodeId, String inputPin) {
        if (graph.getConnections() == null) {
            return null;
        }
        for (FlowConnection connection : graph.getConnections()) {
            if (nodeId.equals(connection.getTargetNodeId()) && inputPin.equals(connection.getTargetPin())) {
                return connection;
            }
        }
        return null;
    }

    private int fanoutCount(String sourceNodeId, String sourcePin) {
        int count = 0;
        if (graph.getConnections() == null) {
            return 0;
        }
        for (FlowConnection connection : graph.getConnections()) {
            if (sourceNodeId.equals(editorSourceNodeId(connection)) && sourcePin.equals(editorSourcePin(connection))) {
                count++;
            }
        }
        return count;
    }

    private void renderEditorJunctions(IDrawContext context) {
        int fill = ThemeManager.getColor(ThemeColor.inClickableBackground);
        int border = ThemeManager.getColor(ThemeColor.innerBorder);
        int accent = ThemeManager.getDefaultAccent().getAccentColor();
        for (FlowGraph.EditorJunction junction : graph.getEditorJunctions()) {
            int x = Math.round((float) junction.getX());
            int y = Math.round((float) junction.getY());
            context.fill(x - JUNCTION_HALF, y - JUNCTION_HALF, x + JUNCTION_HALF, y + JUNCTION_HALF, fill);
            context.fillBorder(x - JUNCTION_HALF, y - JUNCTION_HALF, x + JUNCTION_HALF, y + JUNCTION_HALF, 1, border);
            context.fill(x - 2, y - 2, x + 2, y + 2, accent);
        }
    }

    private void renderDebugWireOverlay(IDrawContext context) {
        FlowDebugController debug = debugController();
        if (debug == null || graph.getConnections() == null) {
            return;
        }
        FlowDebugController.DebugRecord activeConnection = debug.getLatestConnection(graph.getId());
        if (activeConnection == null) {
            return;
        }
        int color = (ThemeManager.getDefaultAccent().getAccentColor() & 0x00FFFFFF) | 0xCC000000;
        for (FlowConnection conn : graph.getConnections()) {
            if (!conn.getSourceNodeId().equals(activeConnection.sourceNodeId())
                || !conn.getSourcePin().equals(activeConnection.sourcePin())
                || !conn.getTargetNodeId().equals(activeConnection.targetNodeId())
                || !conn.getTargetPin().equals(activeConnection.targetPin())) {
                continue;
            }
            FlowNodeWidget source = widgetCache.get(conn.getSourceNodeId());
            FlowNodeWidget target = widgetCache.get(conn.getTargetNodeId());
            if (source == null || target == null) {
                continue;
            }
            double[] start = source.getPinBounds(conn.getSourcePin(), false);
            double[] end = target.getPinBounds(conn.getTargetPin(), true);
            if (start == null || end == null) {
                continue;
            }
            float startX = (float) (start[0] + start[2] / 2);
            float startY = (float) (start[1] + start[3] / 2);
            float endX = (float) (end[0] + end[2] / 2);
            float endY = (float) (end[1] + end[3] / 2);
            drawWire(context, startX, startY, endX, endY, color, getWireLaneOffset(conn));
        }
    }

    private void renderDebugNodeOverlay(IDrawContext context) {
        FlowDebugController debug = debugController();
        if (debug == null) {
            return;
        }
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            String nodeId = entry.getKey();
            FlowNodeWidget widget = entry.getValue();
            FlowDebugController.DebugRecord record = debug.getLatestRecordForNode(graph.getId(), nodeId);
            boolean activeRecord = record != null && "failure".equals(record.status());
            if (!activeRecord) {
                continue;
            }
            int color = 0x00000000;
            if (activeRecord && "failure".equals(record.status())) {
                color = 0xFFFF4D4D;
            }
            context.fillBorder(widget.getX() - 2, widget.getY() - 2, widget.getX() + widget.getWidth() + 2, widget.getY() + widget.getHeight() + 2, 2, color);
        }
    }

    private FlowGraph.EditorJunction findEditorJunctionAt(int wx, int wy) {
        for (int i = graph.getEditorJunctions().size() - 1; i >= 0; i--) {
            FlowGraph.EditorJunction junction = graph.getEditorJunctions().get(i);
            if (Math.abs(wx - junction.getX()) <= JUNCTION_HALF && Math.abs(wy - junction.getY()) <= JUNCTION_HALF) {
                return junction;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleNodeItemSelectorMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int)worldMouse[0];
        int wy = (int)worldMouse[1];

        if (handleHeaderButtonsClick((int) undistortedCoords[0], (int) undistortedCoords[1], button)) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (debugMode && toggleBreakpointAt(wx, wy)) {
                return true;
            }
            if (handleRightClick(wx, wy, (int)mouseX, (int)mouseY)) {
                return true;
            }
            return true;
        }

        FlowGraph.EditorJunction junction = findEditorJunctionAt(wx, wy);
        if (junction != null) {
            dragMouseX = undistortedCoords[0];
            dragMouseY = undistortedCoords[1];
            focusedNode = null;
            setFocusedWidget(null);
            clearSelection();
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (hasShiftDown() || hasControlDown()))) {
                startJunctionWireDrag(junction);
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                captureSnapshot();
                draggedJunction = junction;
                junctionDragOffsetX = worldMouse[0] - junction.getX();
                junctionDragOffsetY = worldMouse[1] - junction.getY();
                draggedWidget = null;
                movingSelectedNodes = false;
                selectedDragStartPositions.clear();
                return true;
            }
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);

            Widget outputWidget = widget.getOutputWidgetAt(wx, wy);
            if (outputWidget != null) {
                outputWidget.mouseClicked(wx, wy, button);
                if (outputWidget instanceof DropDownWidget<?>) {
                    setFocusedWidget(outputWidget);
                } else {
                    setFocusedWidget(null);
                }
                focusedNode = widget;
                bringToFront(widget);
                selectNode(widget, hasShiftDown() || hasControlDown());
                return true;
            }

            if (widget.isMouseOverPin(wx, wy)) {
                String pinName = widget.getPinAtPosition(wx, wy);
                if (pinName != null) {
                    double[] inputBounds = widget.getPinBounds(pinName, true);
                    if (isInside(wx, wy, inputBounds)) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && widget.getPinKind(pinName, true) == NodeDefinition.PinType.DATA) {
                            toggleInputPassthrough(widget, pinName);
                            return true;
                        }
                        dragMouseX = undistortedCoords[0];
                        dragMouseY = undistortedCoords[1];
                        startWireDrag(widget, pinName, true);
                        setFocusedWidget(null);
                        return true;
                    }

                    double[] outputBounds = widget.getPinBounds(pinName, false);
                    if (isInside(wx, wy, outputBounds)) {
                        dragMouseX = undistortedCoords[0];
                        dragMouseY = undistortedCoords[1];
                        startWireDrag(widget, pinName, false);
                        setFocusedWidget(null);
                        return true;
                    }
                    setFocusedWidget(null);
                    return true;
                }
            }

            Widget inputWidget = widget.getInputWidgetAt(wx, wy);
            if (inputWidget != null) {
                inputWidget.mouseClicked(wx, wy, button);
                if (inputWidget instanceof TextInputWidget) {
                    setFocusedWidget(inputWidget);
                } else {
                    setFocusedWidget(null);
                }
                focusedNode = widget;
                bringToFront(widget);
                selectNode(widget, hasShiftDown() || hasControlDown());
                return true;
            }

            if (widget.isMouseOver(wx, wy)) {
                focusedNode = widget;
                setFocusedWidget(null);
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    draggedWidget = widget;
                    dragOffsetX = wx - widget.getX();
                    dragOffsetY = wy - widget.getY();
                }
                bringToFront(widget);
                selectNode(widget, hasShiftDown() || hasControlDown());
                startSelectedNodeMove(widget, button);
                widget.setLastScreenMouse((int) undistortedCoords[0], (int) undistortedCoords[1]);
                widget.mouseClicked(wx, wy, button);
                return true;
            }
        }

        focusedNode = null;
        setFocusedWidget(null);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hasShiftDown() || hasControlDown()) {
                startSelection(undistortedCoords[0], undistortedCoords[1]);
                return true;
            }
            clearSelection();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean toggleBreakpointAt(int wx, int wy) {
        FlowDebugController debug = debugController();
        if (debug == null) {
            return false;
        }
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (widget.isMouseOver(wx, wy)) {
                String nodeId = findNodeId(widget);
                if (nodeId != null) {
                    debug.toggleBreakpoint(serverId, graph, nodeId);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleHeaderButtonsClick(int mouseX, int mouseY, int button) {
        for (IconButton headerButton : headerButtons) {
            if (headerButton != null && headerButton.visible && headerButton.isMouseOver(mouseX, mouseY)) {
                return headerButton.mouseClicked(mouseX, mouseY, button);
            }
        }
        return false;
    }

    private void startWireDrag(FlowNodeWidget widget, String pinName, boolean isInput) {
        dragState.isDragging = true;
        dragState.sourceNodeId = findNodeId(widget);
        dragState.sourcePin = pinName;
        dragState.sourceIsInput = isInput;
        dragPinWidget = widget;
        dragSourceJunction = null;
        pendingSourceNodeId = null;
        pendingSourcePin = null;
        pendingSourceIsInput = false;
    }

    private void startJunctionWireDrag(FlowGraph.EditorJunction junction) {
        FlowNodeWidget source = widgetCache.get(junction.getSourceNodeId());
        if (source == null) {
            return;
        }
        dragState.isDragging = true;
        dragState.sourceNodeId = junction.getSourceNodeId();
        dragState.sourcePin = junction.getSourcePin();
        dragState.sourceIsInput = false;
        dragPinWidget = source;
        dragSourceJunction = junction;
        pendingSourceNodeId = null;
        pendingSourcePin = null;
        pendingSourceIsInput = false;
    }

    private void toggleInputPassthrough(FlowNodeWidget widget, String inputPin) {
        String nodeId = findNodeId(widget);
        if (nodeId == null) {
            return;
        }
        captureSnapshot();
        boolean removed = graph.getEditorPassthroughs().removeIf(passthrough -> nodeId.equals(passthrough.getNodeId()) && inputPin.equals(passthrough.getInputPin()));
        if (!removed) {
            graph.getEditorPassthroughs().add(new FlowGraph.EditorPassthrough(nodeId, inputPin));
        }
        widget.refreshInputWidgets();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (handleNodeItemSelectorMouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int) worldMouse[0];
        int wy = (int) worldMouse[1];

        if (isSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectionEndX = undistortedCoords[0];
            selectionEndY = undistortedCoords[1];
            updateSelectionFromBox();
            isSelecting = false;
            return true;
        }

        if (dragState.isDragging) {
            tryCompleteWire(worldMouse[0], worldMouse[1], undistortedCoords[0], undistortedCoords[1]);

            dragState.isDragging = false;
            dragState.sourceNodeId = null;
            dragState.sourcePin = null;
            dragState.sourceIsInput = false;
            dragSourceJunction = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedJunction != null) {
            draggedJunction = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedWidget instanceof FlowNodeWidget) {
            captureSnapshot();
            if (movingSelectedNodes) {
                syncNodePositions();
            } else {
                syncNodePosition((FlowNodeWidget) draggedWidget);
            }
            movingSelectedNodes = false;
            selectedDragStartPositions.clear();
            draggedWidget = null;
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (widget.mouseReleased(wx, wy, button)) {
                return true;
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
                && !(getFocusedWidget() instanceof TextInputWidget)) {
            if (!selectedNodeIds.isEmpty()) {
                captureSnapshot();
                deleteSelectedNodes();
                return true;
            }
            if (focusedNode != null) {
                captureSnapshot();
                deleteNode(findNodeId(focusedNode));
                return true;
            }
        }

        boolean hasControl = hasControlDown();
        boolean hasShift = hasShiftDown();

        if (hasControl && !(getFocusedWidget() instanceof TextInputWidget)) {
            if (keyCode == GLFW.GLFW_KEY_C) {
                if (!selectedNodeIds.isEmpty()) {
                    copyNodes();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                if (!clipboard.nodes.isEmpty()) {
                    captureSnapshot();
                    pasteNodes();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                if (!selectedNodeIds.isEmpty()) {
                    cutNodes();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_D) {
                if (!selectedNodeIds.isEmpty()) {
                    captureSnapshot();
                    duplicateNodes();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (hasShift) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    protected void syncNodePositions() {
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            FlowNode node = graph.getNodes().get(entry.getKey());
            FlowNodeWidget widget = entry.getValue();
            if (node != null && widget != null) {
                node.setX(widget.getX());
                node.setY(widget.getY());
            }
        }
    }

    private void syncNodePosition(FlowNodeWidget widget) {
        String nodeId = findNodeId(widget);
        if (nodeId == null) {
            return;
        }
        FlowNode node = graph.getNodes().get(nodeId);
        if (node != null) {
            node.setX(widget.getX());
            node.setY(widget.getY());
        }
    }

    private void refreshInputWidgets(String nodeId) {
        FlowNodeWidget widget = widgetCache.get(nodeId);
        if (widget != null) {
            widget.refreshInputWidgets();
        }
    }

    private void deleteSelectedNodes() {
        if (selectedNodeIds.isEmpty()) {
            return;
        }
        Set<String> toDelete = new HashSet<>(selectedNodeIds);
        selectedNodeIds.clear();
        for (String nodeId : toDelete) {
            deleteNode(nodeId);
        }
    }

    private void deleteNode(String nodeId) {
        if (nodeId == null) {
            return;
        }
        selectedNodeIds.remove(nodeId);
        FlowNodeWidget widget = widgetCache.remove(nodeId);
        if (widget != null) {
            removeWorldWidget(widget);
        }
        if (graph.getNodes() != null) {
            graph.getNodes().remove(nodeId);
        }
        if (graph.getConnections() != null) {
            Set<String> affectedTargets = new HashSet<>();
            graph.getConnections().removeIf(conn -> {
                boolean remove = nodeId.equals(conn.getSourceNodeId()) || nodeId.equals(conn.getTargetNodeId());
                if (remove && !nodeId.equals(conn.getTargetNodeId())) {
                    affectedTargets.add(conn.getTargetNodeId());
                }
                return remove;
            });
            for (String targetId : affectedTargets) {
                refreshInputWidgets(targetId);
            }
        }
        graph.getEditorJunctions().removeIf(junction -> nodeId.equals(junction.getSourceNodeId()));
        graph.getEditorPassthroughs().removeIf(passthrough -> nodeId.equals(passthrough.getNodeId()));
        if (draggedJunction != null && nodeId.equals(draggedJunction.getSourceNodeId())) {
            draggedJunction = null;
        }
        if (dragState.isDragging && nodeId.equals(dragState.sourceNodeId)) {
            dragState.isDragging = false;
            dragState.sourceNodeId = null;
            dragState.sourcePin = null;
            dragSourceJunction = null;
        }
        if (widget == focusedNode) {
            focusedNode = null;
        }
        if (draggedWidget == widget) {
            draggedWidget = null;
        }
    }

    private boolean handleRightClick(int wx, int wy, int screenX, int screenY) {
        FlowConnection hit = findConnectionAt(wx, wy);
        if (hit != null) {
            removeConnection(hit);
            return true;
        }

        FlowNodeWidget widget = findNodeAt(wx, wy);
        if (widget != null) {
            String pinName = widget.getPinAtPosition(wx, wy);
            if (pinName != null && disconnectPin(widget, pinName, wx, wy)) {
                return true;
            }
            selectNode(widget, hasShiftDown() || hasControlDown());
            if (widget.isFunctionStartOrEnd()) {
                showFunctionNodeContextMenu(screenX, screenY, widget);
            }
            return true;
        }

        showAllNodesMenu(screenX, screenY);
        return true;
    }

    private FlowNodeWidget findNodeAt(int wx, int wy) {
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = worldWidgets.get(i);
            if (widget instanceof FlowNodeWidget FlowNodeWidget) {
                if (FlowNodeWidget.isMouseOver(wx, wy)) {
                    return FlowNodeWidget;
                }
            }
        }
        return null;
    }

    private void showFunctionNodeContextMenu(int screenX, int screenY, FlowNodeWidget widget) {
        List<FlowGraph.FunctionParameter> params = widget.getFunctionParameterList();
        if (params == null) return;

        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this);
        builder.addHeaderButton("add.png", () -> widget.showAddFunctionParameterPopup(), "Add Parameter", ThemeManager.getAccent("nice"));
        for (FlowGraph.FunctionParameter p : params) {
            if (p != null && p.getName() != null) {
                String name = p.getName();
                builder.addItem("Remove: " + name, () -> widget.removeFunctionParameter(name), name, ThemeManager.getAccent("danger"));
            }
        }
        ContextMenuWidget menu = builder.build();
        addDrawableChild(menu);
        menu.show(screenX, screenY);
    }


    private void selectNode(FlowNodeWidget widget, boolean toggle) {
        String nodeId = findNodeId(widget);
        if (nodeId == null) {
            return;
        }
        if (toggle) {
            if (selectedNodeIds.contains(nodeId)) {
                selectedNodeIds.remove(nodeId);
            } else {
                selectedNodeIds.add(nodeId);
            }
        } else {
            if (selectedNodeIds.contains(nodeId)) {
                return;
            }
            selectedNodeIds.clear();
            selectedNodeIds.add(nodeId);
        }
    }

    private void clearSelection() {
        selectedNodeIds.clear();
    }

    private void startSelection(double screenX, double screenY) {
        isSelecting = true;
        selectionStartX = screenX;
        selectionStartY = screenY;
        selectionEndX = screenX;
        selectionEndY = screenY;
        selectionAdditive = hasShiftDown() || hasControlDown();
        selectionBase.clear();
        if (selectionAdditive) {
            selectionBase.addAll(selectedNodeIds);
        } else {
            selectedNodeIds.clear();
        }
        updateSelectionFromBox();
    }

    private void updateSelectionFromBox() {
        double minScreenX = Math.min(selectionStartX, selectionEndX);
        double minScreenY = Math.min(selectionStartY, selectionEndY);
        double maxScreenX = Math.max(selectionStartX, selectionEndX);
        double maxScreenY = Math.max(selectionStartY, selectionEndY);

        double[] worldStart = screenToWorld(minScreenX, minScreenY);
        double[] worldEnd = screenToWorld(maxScreenX, maxScreenY);

        double minWorldX = Math.min(worldStart[0], worldEnd[0]);
        double minWorldY = Math.min(worldStart[1], worldEnd[1]);
        double maxWorldX = Math.max(worldStart[0], worldEnd[0]);
        double maxWorldY = Math.max(worldStart[1], worldEnd[1]);

        Set<String> selection = new HashSet<>();
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            FlowNodeWidget widget = entry.getValue();
            if (widget == null) {
                continue;
            }
            double nodeX = widget.getX();
            double nodeY = widget.getY();
            double nodeX2 = nodeX + widget.getWidth();
            double nodeY2 = nodeY + widget.getHeight();
            if (nodeX2 >= minWorldX && nodeX <= maxWorldX && nodeY2 >= minWorldY && nodeY <= maxWorldY) {
                selection.add(entry.getKey());
            }
        }

        selectedNodeIds.clear();
        if (selectionAdditive) {
            selectedNodeIds.addAll(selectionBase);
        }
        selectedNodeIds.addAll(selection);
    }

    private void renderSelectionBox(IDrawContext context) {
        if (!isSelecting) {
            return;
        }
        int accentColor = ThemeManager.getDefaultAccent().getAccentColor();
        int fill = ThemeManager.getAnimatedColor("selection_fill".hashCode(), (accentColor & 0x00FFFFFF) | 0x44000000);
        int border = ThemeManager.getAnimatedColor("selection_border".hashCode(), (accentColor & 0x00FFFFFF) | 0xAA000000);

        int x1 = (int) Math.min(selectionStartX, selectionEndX);
        int y1 = (int) Math.min(selectionStartY, selectionEndY);
        int x2 = (int) Math.max(selectionStartX, selectionEndX);
        int y2 = (int) Math.max(selectionStartY, selectionEndY);
        context.fill(x1, y1, x2, y2, fill);
        context.fillBorder(x1, y1, x2, y2, 1, border);
    }

    protected boolean canConnect(FlowNodeWidget sourceWidget, String sourcePin, FlowNodeWidget targetWidget, String targetPin) {
        FlowDataType sourceType = sourceWidget.getPinType(sourcePin, false);
        FlowDataType targetType = targetWidget.getPinType(targetPin, true);
        NodeDefinition.PinType sourceKind = sourceWidget.getPinKind(sourcePin, false);
        NodeDefinition.PinType targetKind = targetWidget.getPinKind(targetPin, true);

        if (sourceType == null || targetType == null) {
            return false;
        }

        if (!allowFlowPins() && (sourceKind == NodeDefinition.PinType.FLOW || targetKind == NodeDefinition.PinType.FLOW || sourceKind == NodeDefinition.PinType.EXEC || targetKind == NodeDefinition.PinType.EXEC)) {
            return false;
        }

        if (sourceKind == NodeDefinition.PinType.FLOW || targetKind == NodeDefinition.PinType.FLOW) {
            return sourceKind == NodeDefinition.PinType.FLOW && targetKind == NodeDefinition.PinType.FLOW && sourceType == FlowDataType.EXECUTION && targetType == FlowDataType.EXECUTION;
        }

        if (sourceKind != NodeDefinition.PinType.DATA || targetKind != NodeDefinition.PinType.DATA) {
            return false;
        }

        return isTypeCompatible(sourceType, targetType);
    }

    protected boolean isTypeCompatible(FlowDataType sourceType, FlowDataType targetType) {
        if (sourceType == null || targetType == null) {
            return false;
        }
        if (useStrictTypeCompatibility()) {
            return sourceType.equals(targetType);
        }
        if (sourceType.canConvertTo(targetType)) {
            return true;
        }
        return NodeRegistry.getInstance().canConvertTypes(serverId, sourceType, targetType);
    }

    protected boolean useStrictTypeCompatibility() {
        return false;
    }

    protected boolean allowFlowPins() {
        return true;
    }

    private void tryCompleteWire(double worldMouseX, double worldMouseY, double screenMouseX, double screenMouseY) {
        int wx = (int) worldMouseX;
        int wy = (int) worldMouseY;

        boolean connected = false;

        for (Widget widget : worldWidgets) {
            if (widget instanceof FlowNodeWidget targetWidget) {
                if (targetWidget != dragPinWidget) {
                    String targetPin = targetWidget.getPinAtPosition(wx, wy);
                    if (targetPin != null) {
                        String targetNodeId = findNodeId(targetWidget);
                        double[] inputBounds = targetWidget.getPinBounds(targetPin, true);
                        double[] outputBounds = targetWidget.getPinBounds(targetPin, false);
                        boolean onInput = isInside(wx, wy, inputBounds);
                        boolean onOutput = isInside(wx, wy, outputBounds);

                        if (!dragState.sourceIsInput && onInput && canConnect(dragPinWidget, dragState.sourcePin, targetWidget, targetPin)) {
                                FlowConnection sourceConnection = resolveDragSourceConnection();
                                if (sourceConnection == null) {
                                    break;
                                }
                                FlowConnection newConnection = new FlowConnection(sourceConnection.getSourceNodeId(), sourceConnection.getSourcePin(), targetNodeId, targetPin);
                                removeExistingInputConnection(targetNodeId, targetPin);
                                graph.getConnections().add(newConnection);
                                refreshInputWidgets(targetNodeId);
                                captureSnapshot();
                                connected = true;
                                break;
                        }

                        if (dragState.sourceIsInput && onOutput && canConnect(targetWidget, targetPin, dragPinWidget, dragState.sourcePin)) {
                                FlowConnection newConnection = new FlowConnection(targetNodeId, targetPin, dragState.sourceNodeId, dragState.sourcePin);
                                removeExistingInputConnection(dragState.sourceNodeId, dragState.sourcePin);
                                graph.getConnections().add(newConnection);
                                refreshInputWidgets(dragState.sourceNodeId);
                                captureSnapshot();
                                connected = true;
                                break;
                        }
                    }
                }
            }
        }

        if (!connected && dragPinWidget != null) {
            FlowDataType sourceType = dragPinWidget.getPinType(dragState.sourcePin, dragState.sourceIsInput);
            FlowConnection sourceConnection = !dragState.sourceIsInput ? resolveDragSourceConnection() : null;
            if (sourceType != null && (dragState.sourceIsInput || sourceConnection != null)) {
                pendingSourceNodeId = dragState.sourceIsInput ? dragState.sourceNodeId : sourceConnection.getSourceNodeId();
                pendingSourcePin = dragState.sourceIsInput ? dragState.sourcePin : sourceConnection.getSourcePin();
                pendingSourceIsInput = dragState.sourceIsInput;
                showAddNodeMenu((int) screenMouseX, (int) screenMouseY, sourceType, dragState.sourceIsInput);
            }
        }
    }

    private FlowConnection resolveDragSourceConnection() {
        return new FlowConnection(dragState.sourceNodeId, dragState.sourcePin, "", "");
    }


    private void bringToFront(FlowNodeWidget widget) {
        if (widget == null) {
            return;
        }
        worldWidgets.remove(widget);
        worldWidgets.add(widget);
    }

    private void startSelectedNodeMove(FlowNodeWidget widget, int button) {
        movingSelectedNodes = false;
        selectedDragStartPositions.clear();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        String nodeId = findNodeId(widget);
        if (nodeId == null || !selectedNodeIds.contains(nodeId) || selectedNodeIds.size() <= 1) {
            return;
        }
        movingSelectedNodes = true;
        for (String selectedNodeId : selectedNodeIds) {
            FlowNodeWidget selectedWidget = widgetCache.get(selectedNodeId);
            if (selectedWidget != null) {
                selectedDragStartPositions.put(selectedNodeId, new int[] { selectedWidget.getX(), selectedWidget.getY() });
            }
        }
    }

    private void closeNodeItemSelector() {
        removeNodeItemSelector(nodeItemSelector);
    }

    private void removeNodeItemSelector(ItemSelectorWidget selector) {
        if (selector != null) {
            remove(selector);
        }
        if (selector == nodeItemSelector) {
            nodeItemSelector = null;
        }
    }

    private boolean handleNodeItemSelectorMouseClicked(double mouseX, double mouseY, int button) {
        ItemSelectorWidget selector = nodeItemSelector;
        if (selector == null || !selector.visible) {
            return false;
        }
        if (selector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (selector.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        removeNodeItemSelector(selector);
        return false;
    }

    private boolean handleNodeItemSelectorMouseReleased(double mouseX, double mouseY, int button) {
        ItemSelectorWidget selector = nodeItemSelector;
        return selector != null && selector.visible && selector.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleNodeItemSelectorMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        ItemSelectorWidget selector = nodeItemSelector;
        return selector != null && selector.visible && selector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void removeExistingInputConnection(String nodeId, String pinName) {
        if (graph.getConnections() == null) return;

        boolean removed = graph.getConnections().removeIf(conn -> conn.getTargetNodeId().equals(nodeId) && conn.getTargetPin().equals(pinName)
        );
        if (removed) {
            refreshInputWidgets(nodeId);
        }
    }

    private String findNodeId(FlowNodeWidget widget) {
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            if (entry.getValue() == widget) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void showAddNodeMenu(int x, int y, FlowDataType sourceType, boolean sourceIsInput) {
        closeNodeItemSelector();

        clearSelection();

        double[] worldPos = screenToWorld(x, y);
        int worldX = (int) worldPos[0];
        int worldY = (int) worldPos[1];

        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this)
                .onClose(() -> removeNodeItemSelector(selectorRef[0]));

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(serverId)) {
            List<NodeDefinition> definitions = new ArrayList<>(NodeRegistry.getInstance().getAllDefinitions(serverId).values());
            definitions.removeIf(def -> def.isHidden() || !isAllowedInCurrentEditor(def));
            definitions.sort(Comparator
                    .comparingInt(NodeDefinition::getPriority)
                    .thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            for (NodeDefinition def : definitions) {
                String compatiblePin = null;
                if (sourceType != null) {
                    compatiblePin = findCompatiblePin(def, sourceType, sourceIsInput);
                    if (compatiblePin == null) {
                        continue;
                    }
                }
                String pinName = compatiblePin;
                int finalWorldX = worldX;
                int finalWorldY = worldY;
                addSelectorItem(builder, selectorLabel(def), selectorHint(def), selectorSearchTerms(def), () -> {
                    captureSnapshot();
                    addNode(finalWorldX, finalWorldY, def.getId(), pinName);
                });
            }
        }

        nodeItemSelector = builder.build();
        selectorRef[0] = nodeItemSelector;
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(x, y);
    }

    private String findCompatiblePin(NodeDefinition definition, FlowDataType sourceType, boolean sourceIsInput) {
        List<NodeDefinition.PinDefinition> pins = sourceIsInput ? definition.getOutputs() : definition.getInputs();
        for (NodeDefinition.PinDefinition pin : pins) {
            if (pin.getVisibleWhen() != null && !pin.getVisibleWhen().isEmpty() && !canExposeCompatibleFamilyPin(definition, pin)) {
                continue;
            }
            if (sourceType == FlowDataType.EXECUTION) {
                if (pin.getType() == NodeDefinition.PinType.FLOW && pin.getDataType() == FlowDataType.EXECUTION) {
                    return pin.getName();
                }
                continue;
            }
            if (pin.getType() == NodeDefinition.PinType.DATA && isTypeCompatible(sourceType, pin.getDataType())) {
                return pin.getName();
            }
        }
        return null;
    }

    private boolean canExposeCompatibleFamilyPin(NodeDefinition definition, NodeDefinition.PinDefinition candidate) {
        if (definition.getKind() != NodeDefinition.NodeKind.FAMILY || candidate.getVisibleWhen() == null || candidate.getVisibleWhen().isEmpty()) {
            return false;
        }
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (input.getDirection() == NodeDefinition.PinDirection.INPUT
                    && input.getType() == NodeDefinition.PinType.DATA
                    && (input.getName().equalsIgnoreCase("mode") || input.getName().equalsIgnoreCase("action"))) {
                if (input.getOptions() == null) {
                    return false;
                }
                for (String value : candidate.getVisibleWhen().values()) {
                    for (String option : value.split(",")) {
                        if (input.getOptions().contains(option.trim())) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }
        return false;
    }

    private void showAllNodesMenu(int screenX, int screenY) {
        closeNodeItemSelector();

        clearSelection();

        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this)
                .onClose(() -> removeNodeItemSelector(selectorRef[0]));

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(serverId)) {
            List<NodeDefinition> definitions = new ArrayList<>(NodeRegistry.getInstance().getAllDefinitions(serverId).values());
            definitions.removeIf(def -> def.isHidden() || !isAllowedInCurrentEditor(def));
            definitions.sort(Comparator
                    .comparingInt(NodeDefinition::getPriority)
                    .thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            for (NodeDefinition def : definitions) {
                addSelectorItem(builder, selectorLabel(def), selectorHint(def), selectorSearchTerms(def), () -> {
                    captureSnapshot();
                    addNodeAtCenter(def.getId());
                });
            }
        }

        nodeItemSelector = builder.build();
        selectorRef[0] = nodeItemSelector;
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(screenX, screenY);
    }

    private String selectorLabel(NodeDefinition definition) {
        StringBuilder label = new StringBuilder(definition.getDisplayName());
        if (definition.getCategory() != null) {
            label.append(" - ").append(definition.getCategory().getDisplayName());
        }
        return label.toString();
    }

    private void addSelectorItem(ItemSelectorWidget.Builder builder, String label, String hint, String searchTerms, Runnable action) {
        builder.addItem(label, hint, searchTerms, action);
    }

    private String selectorHint(NodeDefinition definition) {
        if (definition.getDescription() != null && !definition.getDescription().isBlank()) {
            return definition.getDescription();
        }
        return "";
    }

    private String selectorSearchTerms(NodeDefinition definition) {
        StringBuilder label = new StringBuilder();
        if (definition.getDescription() != null && !definition.getDescription().isBlank()) {
            label.append(definition.getDescription());
        }
        appendSearchTerms(label, definition.getAliases());
        appendSearchTerms(label, definition.getTags());
        appendSearchTerms(label, definition.getExamples());
        for (NodeDefinition.PinDefinition pin : definition.getInputs()) {
            label.append(" ").append(pin.getName());
        }
        for (NodeDefinition.PinDefinition pin : definition.getOutputs()) {
            label.append(" ").append(pin.getName());
        }
        return label.toString();
    }

    private void appendSearchTerms(StringBuilder label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            label.append(" - ").append(String.join(" ", values));
        }
    }

    private void addNode(int x, int y, String type) {
        addNode(x, y, type, null);
    }

    private void addNode(int x, int y, String type, String autoWirePin) {
        String id = UUID.randomUUID().toString();
        FlowNode node = new FlowNode(type, x, y, new HashMap<>());
        graph.getNodes().put(id, node);

        FlowNodeWidget widget = new FlowNodeWidget(x, y, node, graph, id, serverId, () -> deleteNode(id));
        widgetCache.put(id, widget);
        addWorldWidget(widget);

        if (pendingSourceNodeId != null && pendingSourcePin != null && autoWirePin != null) {
            FlowConnection newConnection;
            if (pendingSourceIsInput) {
                newConnection = new FlowConnection(id, autoWirePin, pendingSourceNodeId, pendingSourcePin);
                removeExistingInputConnection(pendingSourceNodeId, pendingSourcePin);
                refreshInputWidgets(pendingSourceNodeId);
            } else {
                newConnection = new FlowConnection(pendingSourceNodeId, pendingSourcePin, id, autoWirePin);
                removeExistingInputConnection(id, autoWirePin);
                refreshInputWidgets(id);
            }
            graph.getConnections().add(newConnection);
        }

        pendingSourceNodeId = null;
        pendingSourcePin = null;
        pendingSourceIsInput = false;
    }

    protected void onSave() {
        syncNodePositions();
        saveGraph();
    }

    protected void saveGraph() {
        normalizePassthroughConnections();
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            flowManager.saveFlow(serverId, graph);
        }
    }

    private void normalizePassthroughConnections() {
        if (graph == null || graph.getConnections() == null) {
            return;
        }
        for (FlowConnection connection : graph.getConnections()) {
            String editorNodeId = connection.getEditorSourceNodeId();
            String editorPin = connection.getEditorSourcePin();
            if (editorNodeId != null && !editorNodeId.isBlank() && NodeWidget.isPassthroughOutputPin(editorPin)) {
                connection.setSourceNodeId(editorNodeId);
                connection.setSourcePin(editorPin);
                connection.setEditorSourceNodeId(null);
                connection.setEditorSourcePin(null);
            }
        }
    }

    private boolean disconnectPin(FlowNodeWidget widget, String pinName, int wx, int wy) {
        String nodeId = findNodeId(widget);
        if (nodeId == null || graph.getConnections() == null) {
            return false;
        }

        double[] inputBounds = widget.getPinBounds(pinName, true);
        if (isInside(wx, wy, inputBounds)) {
            boolean removed = graph.getConnections().removeIf(conn -> nodeId.equals(conn.getTargetNodeId()) && pinName.equals(conn.getTargetPin()));
            if (removed) {
                captureSnapshot();
                refreshInputWidgets(nodeId);
            }
            return removed;
        }

        double[] outputBounds = widget.getPinBounds(pinName, false);
        if (isInside(wx, wy, outputBounds)) {
            Set<String> affectedTargets = new HashSet<>();
            boolean removed = graph.getConnections().removeIf(conn -> {
                boolean match = nodeId.equals(editorSourceNodeId(conn)) && pinName.equals(editorSourcePin(conn));
                if (match) {
                    affectedTargets.add(conn.getTargetNodeId());
                }
                return match;
            });
            if (removed) {
                captureSnapshot();
                for (String targetId : affectedTargets) {
                    refreshInputWidgets(targetId);
                }
            }
            return removed;
        }

        return false;
    }

    private FlowConnection findConnectionAt(double worldX, double worldY) {
        if (graph.getConnections() == null) {
            return null;
        }
        double hitRadius = WIRE_HIT_RADIUS / Math.max(zoomLevel, 0.1f);

        for (FlowConnection conn : graph.getConnections()) {
            String sourceNodeId = editorSourceNodeId(conn);
            String sourcePin = editorSourcePin(conn);
            FlowNodeWidget source = widgetCache.get(sourceNodeId);
            FlowNodeWidget target = widgetCache.get(conn.getTargetNodeId());
            if (source == null || target == null) {
                continue;
            }
            double[] start = source.getPinBounds(sourcePin, false);
            double[] end = target.getPinBounds(conn.getTargetPin(), true);
            if (start == null || end == null) {
                continue;
            }
            float startX = (float) (start[0] + start[2] / 2);
            float startY = (float) (start[1] + start[3] / 2);
            float endX = (float) (end[0] + end[2] / 2);
            float endY = (float) (end[1] + end[3] / 2);
            FlowGraph.EditorPassthrough passthrough = findPassthroughRoute(conn);
            if (passthrough != null) {
                FlowNodeWidget passthroughWidget = widgetCache.get(passthrough.getNodeId());
                double[] output = passthroughWidget != null ? passthroughWidget.getPinBounds(NodeWidget.passthroughOutputPin(passthrough.getInputPin()), false) : null;
                if (output != null) {
                    float outputX = (float) (output[0] + output[2] / 2);
                    float outputY = (float) (output[1] + output[3] / 2);
                    if (!passthrough.getNodeId().equals(conn.getTargetNodeId()) || !passthrough.getInputPin().equals(conn.getTargetPin())) {
                        for (WireSegment segment : wireSegments(outputX, outputY, endX, endY, getWireLaneOffset(conn) / 2)) {
                            if (isNearWireSegment(worldX, worldY, segment, hitRadius)) {
                                return conn;
                            }
                        }
                    }
                    continue;
                }
            }
            FlowGraph.EditorJunction junction = findEditorJunction(sourceNodeId, sourcePin);

            if (junction != null && fanoutCount(sourceNodeId, sourcePin) > 1) {
                for (WireSegment segment : wireSegments(startX, startY, junction.getX(), junction.getY(), 0)) {
                    if (isNearWireSegment(worldX, worldY, segment, hitRadius)) {
                        return conn;
                    }
                }
                for (WireSegment segment : wireSegments(junction.getX(), junction.getY(), endX, endY, getWireLaneOffset(conn) / 2)) {
                    if (isNearWireSegment(worldX, worldY, segment, hitRadius)) {
                        return conn;
                    }
                }
            } else {
                for (WireSegment segment : wireSegments(startX, startY, endX, endY, getWireLaneOffset(conn))) {
                    if (isNearWireSegment(worldX, worldY, segment, hitRadius)) {
                        return conn;
                    }
                }
            }
        }
        return null;
    }

    private boolean isNearWireSegment(double x, double y, WireSegment segment, double radius) {
        double minX = Math.min(segment.x1(), segment.x2()) - radius;
        double maxX = Math.max(segment.x1(), segment.x2()) + radius;
        double minY = Math.min(segment.y1(), segment.y2()) - radius;
        double maxY = Math.max(segment.y1(), segment.y2()) + radius;
        if (segment.x1() == segment.x2()) {
            return Math.abs(x - segment.x1()) <= radius && y >= minY && y <= maxY;
        }
        if (segment.y1() == segment.y2()) {
            return Math.abs(y - segment.y1()) <= radius && x >= minX && x <= maxX;
        }
        double dx = segment.x2() - segment.x1();
        double dy = segment.y2() - segment.y1();
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            double pointDx = x - segment.x1();
            double pointDy = y - segment.y1();
            return pointDx * pointDx + pointDy * pointDy <= radius * radius;
        }
        double t = ((x - segment.x1()) * dx + (y - segment.y1()) * dy) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        double nearestX = segment.x1() + t * dx;
        double nearestY = segment.y1() + t * dy;
        double pointDx = x - nearestX;
        double pointDy = y - nearestY;
        return pointDx * pointDx + pointDy * pointDy <= radius * radius;
    }

    private void removeConnection(FlowConnection conn) {
        if (conn == null || graph.getConnections() == null) {
            return;
        }
        if (graph.getConnections().remove(conn)) {
            captureSnapshot();
            refreshInputWidgets(conn.getTargetNodeId());
        }
    }

    private void copyNodes() {
        clipboard.nodes.clear();
        clipboard.connections.clear();

        if (selectedNodeIds.isEmpty()) {
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;

        for (String nodeId : selectedNodeIds) {
            FlowNode node = graph.getNodes().get(nodeId);
            if (node != null) {
                minX = Math.min(minX, node.getX());
                minY = Math.min(minY, node.getY());
            }
        }

        Map<String, Integer> nodeIdToIndex = new HashMap<>();
        int index = 0;
        for (String nodeId : selectedNodeIds) {
            FlowNode node = graph.getNodes().get(nodeId);
            if (node != null) {
                nodeIdToIndex.put(nodeId, index++);
                clipboard.nodes.add(new CopiedNode(node.getType(), node.getX() - minX, node.getY() - minY, node.getInputValues()));
            }
        }

        if (graph.getConnections() != null) {
            for (FlowConnection conn : graph.getConnections()) {
                if (selectedNodeIds.contains(conn.getSourceNodeId()) && selectedNodeIds.contains(conn.getTargetNodeId())) {
                    Integer sourceIdx = nodeIdToIndex.get(conn.getSourceNodeId());
                    Integer targetIdx = nodeIdToIndex.get(conn.getTargetNodeId());
                    if (sourceIdx != null && targetIdx != null) {
                        clipboard.connections.add(new CopiedConnection(sourceIdx, conn.getSourcePin(), targetIdx, conn.getTargetPin()));
                    }
                }
            }
        }
    }

    private void pasteNodes() {
        if (clipboard.nodes.isEmpty()) {
            return;
        }

        double[] screenCenter = screenToWorld(width / 2.0, height / 2.0);
        double[] mouseScreen = new double[] { dragMouseX, dragMouseY };
        double[] mouseWorld = screenToWorld(mouseScreen[0], mouseScreen[1]);

        double pasteX = mouseWorld[0];
        double pasteY = mouseWorld[1];

        clearSelection();

        Map<Integer, String> indexToNewNodeId = new HashMap<>();
        List<String> newSelectedIds = new ArrayList<>();

        for (int i = 0; i < clipboard.nodes.size(); i++) {
            CopiedNode copied = clipboard.nodes.get(i);
            double x = pasteX + copied.relativeX;
            double y = pasteY + copied.relativeY;

            String newId = UUID.randomUUID().toString();
            FlowNode newNode = new FlowNode(copied.type, x, y, new HashMap<>(copied.inputValues));
            graph.getNodes().put(newId, newNode);

            FlowNodeWidget widget = new FlowNodeWidget((int) x, (int) y, newNode, graph, newId, serverId, () -> deleteNode(newId));
            addWorldWidget(widget);
            widgetCache.put(newId, widget);

            indexToNewNodeId.put(i, newId);
            newSelectedIds.add(newId);
        }

        for (CopiedConnection conn : clipboard.connections) {
            String sourceId = indexToNewNodeId.get(conn.sourceIndex);
            String targetId = indexToNewNodeId.get(conn.targetIndex);
            if (sourceId != null && targetId != null) {
                FlowConnection newConnection = new FlowConnection(sourceId, conn.sourcePin, targetId, conn.targetPin);
                removeExistingInputConnection(targetId, conn.targetPin);
                graph.getConnections().add(newConnection);
                refreshInputWidgets(targetId);
            }
        }

        for (String id : newSelectedIds) {
            selectedNodeIds.add(id);
        }
    }

    private void cutNodes() {
        copyNodes();
        deleteSelectedNodes();
    }

    private void duplicateNodes() {
        copyNodes();
        pasteNodes();
    }

    private void captureSnapshot() {
        if (isUndoing) return;
        undoStack.add(new GraphSnapshot(graph, selectedNodeIds));
        if (undoStack.size() > MAX_UNDO_SIZE) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;

        isUndoing = true;

        GraphSnapshot redoSnapshot = new GraphSnapshot(graph, selectedNodeIds);
        redoStack.add(redoSnapshot);
        if (redoStack.size() > MAX_UNDO_SIZE) {
            redoStack.remove(0);
        }

        GraphSnapshot snapshot = undoStack.remove(undoStack.size() - 1);
        restoreSnapshot(snapshot);

        isUndoing = false;
    }

    private void redo() {
        if (redoStack.isEmpty()) return;

        isUndoing = true;

        GraphSnapshot undoSnapshot = new GraphSnapshot(graph, selectedNodeIds);
        undoStack.add(undoSnapshot);
        if (undoStack.size() > MAX_UNDO_SIZE) {
            undoStack.remove(0);
        }

        GraphSnapshot snapshot = redoStack.remove(redoStack.size() - 1);
        restoreSnapshot(snapshot);

        isUndoing = false;
    }

    private void restoreSnapshot(GraphSnapshot snapshot) {
        for (FlowNodeWidget widget : widgetCache.values()) {
            removeWorldWidget(widget);
        }
        widgetCache.clear();

        selectedNodeIds.clear();
        selectedNodeIds.addAll(snapshot.selectedIds);

        graph.getNodes().clear();
        for (Map.Entry<String, FlowNode> entry : snapshot.nodes.entrySet()) {
            String nodeId = entry.getKey();
            FlowNode node = entry.getValue();
            graph.getNodes().put(nodeId, node);
            FlowNodeWidget widget = new FlowNodeWidget((int)node.getX(), (int)node.getY(), node, graph, nodeId, serverId, () -> deleteNode(nodeId));
            addWorldWidget(widget);
            widgetCache.put(nodeId, widget);
        }

        graph.getConnections().clear();
        graph.getConnections().addAll(snapshot.connections);
        graph.setFunction(snapshot.function);
        graph.setFunctionInputs(copyFunctionParameters(snapshot.functionInputs));
        graph.setFunctionOutputs(copyFunctionParameters(snapshot.functionOutputs));
        graph.setEditorJunctions(copyEditorJunctions(snapshot.editorJunctions));
        graph.setEditorPassthroughs(copyEditorPassthroughs(snapshot.editorPassthroughs));
        draggedJunction = null;
        dragSourceJunction = null;

        for (String nodeId : snapshot.nodes.keySet()) {
            refreshInputWidgets(nodeId);
        }
    }

    private boolean isInside(int x, int y, double[] bounds) {
        if (bounds == null) {
            return false;
        }
        return x >= bounds[0] && x <= bounds[0] + bounds[2]
                && y >= bounds[1] && y <= bounds[1] + bounds[3];
    }

    private static List<FlowGraph.FunctionParameter> copyFunctionParameters(List<FlowGraph.FunctionParameter> parameters) {
        List<FlowGraph.FunctionParameter> copied = new ArrayList<>();
        if (parameters == null) {
            return copied;
        }
        for (FlowGraph.FunctionParameter parameter : parameters) {
            if (parameter == null) {
                continue;
            }
            copied.add(new FlowGraph.FunctionParameter(parameter.getName(), parameter.getType()));
        }
        return copied;
    }

    private static List<FlowGraph.EditorJunction> copyEditorJunctions(List<FlowGraph.EditorJunction> junctions) {
        List<FlowGraph.EditorJunction> copied = new ArrayList<>();
        if (junctions == null) {
            return copied;
        }
        for (FlowGraph.EditorJunction junction : junctions) {
            if (junction == null) {
                continue;
            }
            copied.add(new FlowGraph.EditorJunction(
                junction.getId(),
                junction.getSourceNodeId(),
                junction.getSourcePin(),
                junction.getX(),
                junction.getY()
            ));
        }
        return copied;
    }

    private static List<FlowGraph.EditorPassthrough> copyEditorPassthroughs(List<FlowGraph.EditorPassthrough> passthroughs) {
        List<FlowGraph.EditorPassthrough> copied = new ArrayList<>();
        if (passthroughs == null) {
            return copied;
        }
        for (FlowGraph.EditorPassthrough passthrough : passthroughs) {
            if (passthrough == null) {
                continue;
            }
            copied.add(new FlowGraph.EditorPassthrough(passthrough.getNodeId(), passthrough.getInputPin()));
        }
        return copied;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.isVisible() && paletteSidePanel.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int) worldMouse[0];
        int wy = (int) worldMouse[1];
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (widget.mouseScrolled(wx, wy, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (headerBackground != null) {
            layoutHeaderButtons();
        }
        updatePositions();
    }

    @Override
    public <T extends Widget> T addDrawableChild(T widget) {
        super.addDrawableChild(widget);
        return widget;
    }

    @Override
    public int getInitialWidth() {
        return initialWidth;
    }

    @Override
    public int getInitialHeight() {
        return initialHeight;
    }


    @Override
    public Screen asScreen() {
        return this;
    }

    @Override public void updateRenderOrder(List<AnimatedWidget> list) {}
    @Override public void setHitBottom(boolean hitBottom) {}
}

