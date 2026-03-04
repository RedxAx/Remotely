package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowType;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FlowEditorScreen extends InfiniteScreen implements UiHost {
    private final FlowGraph graph;
    private final String serverId;
    private static Screen parent;
    private final Map<String, NodeWidget> widgetCache = new HashMap<>();
    private static final float WIRE_HIT_RADIUS = 6.0f;
    private static final int WIRE_OUT_OFFSET = 26;
    private static final int WIRE_LANE_SPACING = 6;
    private static final int SELECTION_BORDER_PADDING = 2;
    private final Set<String> selectedNodeIds = new HashSet<>();
    private final Set<String> selectionBase = new HashSet<>();
    private boolean isSelecting = false;
    private boolean selectionAdditive = false;
    private double selectionStartX = 0;
    private double selectionStartY = 0;
    private double selectionEndX = 0;
    private double selectionEndY = 0;

    private SidePanel paletteSidePanel;
    private final Map<NodeDefinition.NodeCategory, PopupWidget> categoryPopups = new HashMap<>();
    private static final List<NodeDefinition.NodeCategory> CATEGORY_ORDER = List.of(
        NodeDefinition.NodeCategory.EVENT,
        NodeDefinition.NodeCategory.ACTION,
        NodeDefinition.NodeCategory.LOGIC,
        NodeDefinition.NodeCategory.DATA,
        NodeDefinition.NodeCategory.VARIABLE,
        NodeDefinition.NodeCategory.FUNCTION,
        NodeDefinition.NodeCategory.ENTITY,
        NodeDefinition.NodeCategory.WORLD,
        NodeDefinition.NodeCategory.INVENTORY,
        NodeDefinition.NodeCategory.SCOREBOARD,
        NodeDefinition.NodeCategory.ECONOMY,
        NodeDefinition.NodeCategory.PERMISSION,
        NodeDefinition.NodeCategory.VISUAL,
        NodeDefinition.NodeCategory.UTILITY,
        NodeDefinition.NodeCategory.DATABASE,
        NodeDefinition.NodeCategory.HTTP,
        NodeDefinition.NodeCategory.DISCORD
    );

    private IconButton headerBackground;
    private final List<IconButton> headerButtons = new ArrayList<>();
    private boolean initialized;

    private int initialWidth;
    private int initialHeight;

    private static class DragState {
        String sourceNodeId;
        String sourcePin;
        boolean isDragging;
    }
    private final DragState dragState;
    private NodeWidget dragPinWidget;
    private ItemSelectorWidget nodeItemSelector;
    private NodeWidget focusedNode;

    private String pendingSourceNodeId;
    private String pendingSourcePin;

    private double dragMouseX = 0;
    private double dragMouseY = 0;

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

        GraphSnapshot(Map<String, FlowNode> nodes, List<FlowConnection> connections, Set<String> selectedIds) {
            this.nodes = new HashMap<>();
            for (Map.Entry<String, FlowNode> entry : nodes.entrySet()) {
                FlowNode node = entry.getValue();
                this.nodes.put(entry.getKey(), new FlowNode(
                    node.getType(),
                    node.getX(),
                    node.getY(),
                    new HashMap<>(node.getInputValues())
                ));
            }
            this.connections = new ArrayList<>(connections);
            this.selectedIds = new HashSet<>(selectedIds);
        }
    }

    private final List<GraphSnapshot> undoStack = new ArrayList<>();
    private final List<GraphSnapshot> redoStack = new ArrayList<>();
    private static final int MAX_UNDO_SIZE = 50;
    private boolean isUndoing = false;

    public FlowEditorScreen(FlowGraph graph) {
        this(graph, null);
    }

    public FlowEditorScreen(FlowGraph graph, String serverId) {
        this(graph, serverId, null);
    }

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

        for (var entry : graph.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            NodeWidget widget = new NodeWidget((int)entry.getValue().getX(), (int)entry.getValue().getY(), entry.getValue(), graph, nodeId, serverId, () -> deleteNode(nodeId));
            addWorldWidget(widget);
            widgetCache.put(entry.getKey(), widget);
        }
    }

    public String getServerId() {
        return serverId;
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
        selectedNodeIds.clear();
        selectionBase.clear();
        focusedNode = null;
        dragState.sourceNodeId = null;
        dragState.sourcePin = null;
        dragState.isDragging = false;
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
        return "change.png";
    }

    public void refreshNodeRegistry() {
        for (NodeWidget widget : widgetCache.values()) {
            removeWorldWidget(widget);
        }
        widgetCache.clear();
        for (var entry : graph.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            NodeWidget widget = new NodeWidget((int)entry.getValue().getX(), (int)entry.getValue().getY(), entry.getValue(), graph, nodeId, serverId, () -> deleteNode(nodeId));
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
            headerBackground = new IconButton.Builder().pos(10, 10).size(1, 28).build();
            headerBackground.active = false;
            addHudWidget(headerBackground);

            createPaletteSidePanel();
            createHeaderButtons();
            initialized = true;
        }

        if (headerBackground != null) {
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
        for (NodeDefinition.NodeCategory category : CATEGORY_ORDER) {
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
        Map<NodeDefinition.NodeCategory, List<NodeDefinition>> categories = new HashMap<>();
        for (NodeDefinition.NodeCategory category : CATEGORY_ORDER) {
            categories.put(category, new ArrayList<>());
        }

        for (NodeDefinition def : NodeRegistry.getInstance().getAllDefinitions(serverId).values()) {
            if (def.isHidden()) {
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

        for (NodeDefinition.NodeCategory category : CATEGORY_ORDER) {
            PopupWidget targetPopup = getCategoryPopup(category);
            if (targetPopup == null) {
                continue;
            }
            targetPopup.clearRows();
            List<NodeDefinition> nodes = categories.getOrDefault(category, new ArrayList<>());
            nodes.sort(comparator);
            for (NodeDefinition def : nodes) {
                IconButton btn = new IconButton.Builder()
                    .label(def.getDisplayName())
                    .onClick(() -> addNodeAtCenter(def.getId()))
                    .build();
                targetPopup.addRow("", Collections.singletonList(btn), 20, true, false);
            }
        }
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
        return switch (category) {
            case EVENT -> "Events";
            case ACTION -> "Actions";
            case LOGIC -> "Logic";
            case DATA -> "Data";
            case VARIABLE -> "Variables";
            case FUNCTION -> "Functions";
            case ENTITY -> "Entities";
            case WORLD -> "World";
            case INVENTORY -> "Inventory";
            case SCOREBOARD -> "Scoreboard";
            case ECONOMY -> "Economy";
            case PERMISSION -> "Permissions";
            case VISUAL -> "Visual";
            case UTILITY -> "Utility";
            case DATABASE -> "Database";
            case HTTP -> "HTTP";
            case DISCORD -> "Discord";
        };
    }

    private void addNodeAtCenter(String type) {
        double[] center = screenToWorld(width / 2.0, height / 2.0);
        int x = (int) (center[0] - 50);
        int y = (int) (center[1] - 20);
        addNode(x, y, type, null);
    }

    private void createHeaderButtons() {
        IconButton backButton = new IconButton.Builder().size(18, 18).imagePath("close.png")
                .onClick(() -> {
                    if (parent != null) {
                        client.setScreen(parent);
                    } else {
                        close();
                    }
                })
                .build();
        headerButtons.add(backButton);

        IconButton saveButton = new IconButton.Builder()
            .size(18, 18)
            .imagePath("save.png")
            .onClick(this::onSave)
            .build();
        headerButtons.add(saveButton);
        addDrawableChild(saveButton, backButton);
    }

    private void layoutHeaderButtons() {
        int padding = 5;
        int totalWidth = 0;

        for (IconButton button : headerButtons) {
            totalWidth += button.getWidth();
        }
        totalWidth += Math.max(0, headerButtons.size() - 1) * padding;

        headerBackground.setWidth(totalWidth + (padding * 2));
        headerBackground.setHeight(30);
        headerBackground.setPosition(width - headerBackground.getWidth() - 10, 10);

        int startY = headerBackground.getY();
        int currentX = headerBackground.getX() + headerBackground.getWidth() - padding;
        for (IconButton button : headerButtons) {
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
            layoutHeaderButtons();
        }
    }

    @Override
    public void close() {
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

        renderWires(context);

        for (Widget widget : worldWidgets) {
            widget.render(context, worldMouseX, worldMouseY, delta);
        }
        renderSelectionHighlights(context);
        context.getMatrices().pop();

        renderSelectionBox(context);

        for (Widget widget : hudWidgets) {
            widget.render(context, mouseX, mouseY, delta);
        }

        if (paletteSidePanel != null) {
            paletteSidePanel.update();
            paletteSidePanel.renderHeader(context);
        }

        for (Notification notification : Notification.getActiveNotifications()) {
            notification.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderWires(IDrawContext context) {
        if (graph.getConnections() == null) return;

        for (FlowConnection conn : graph.getConnections()) {
            NodeWidget source = widgetCache.get(conn.getSourceNodeId());
            NodeWidget target = widgetCache.get(conn.getTargetNodeId());

            if (source != null && target != null) {
                double[] start = source.getPinBounds(conn.getSourcePin(), false);
                double[] end = target.getPinBounds(conn.getTargetPin(), true);

                if (start != null && end != null) {
                    float startX = (float) (start[0] + start[2]/2);
                    float startY = (float) (start[1] + start[3]/2);
                    float endX = (float) (end[0] + end[2]/2);
                    float endY = (float) (end[1] + end[3]/2);

                    FlowType sourceType = source.getPinType(conn.getSourcePin(), false);
                    int wireColor = (sourceType != null) ? sourceType.getColor() : ThemeManager.getColor(ThemeColor.innerBorder);
                    int laneOffset = getWireLaneOffset(conn);
                    drawWire(context, startX, startY, endX, endY, wireColor, laneOffset);
                }
            }
        }

        if (dragState.isDragging && dragState.sourceNodeId != null) {
            double[] sourcePinWorld = null;
            NodeWidget source = widgetCache.get(dragState.sourceNodeId);
            FlowType sourceType = null;
            if (source != null) {
                double[] bounds = source.getPinBounds(dragState.sourcePin, false);
                if (bounds != null) {
                    sourcePinWorld = new double[] { bounds[0] + bounds[2]/2, bounds[1] + bounds[3]/2 };
                    sourceType = source.getPinType(dragState.sourcePin, false);
                }
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

        if (dragState.isDragging) {
            return true;
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = (NodeWidget) worldWidgets.get(i);
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

        drawSegmentBorder(context, x1, y1, outX, y1, borderColor);
        drawSegmentBorder(context, outX, y1, outX, midY, borderColor);
        drawSegmentBorder(context, outX, midY, inX, midY, borderColor);
        drawSegmentBorder(context, inX, midY, inX, y2, borderColor);
        drawSegmentBorder(context, inX, y2, x2, y2, borderColor);

        drawSegmentFill(context, x1, y1, outX, y1, color);
        drawSegmentFill(context, outX, y1, outX, midY, color);
        drawSegmentFill(context, outX, midY, inX, midY, color);
        drawSegmentFill(context, inX, midY, inX, y2, color);
        drawSegmentFill(context, inX, y2, x2, y2, color);
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
        String key = conn.getSourceNodeId() + ":" + conn.getSourcePin() + ">" + conn.getTargetNodeId() + ":" + conn.getTargetPin();
        int lane = Math.floorMod(key.hashCode(), 5) - 2;
        return lane * WIRE_LANE_SPACING;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (paletteSidePanel != null && paletteSidePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int)worldMouse[0];
        int wy = (int)worldMouse[1];

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (handleRightClick(wx, wy, (int)mouseX, (int)mouseY)) {
                return true;
            }
            return true;
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = (NodeWidget) worldWidgets.get(i);

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
                    double[] outputBounds = widget.getPinBounds(pinName, false);
                    if (outputBounds != null) {
                        dragMouseX = undistortedCoords[0];
                        dragMouseY = undistortedCoords[1];
                        startWireDrag(widget, pinName);
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

    private void startWireDrag(NodeWidget widget, String pinName) {
        dragState.isDragging = true;
        dragState.sourceNodeId = findNodeId(widget);
        dragState.sourcePin = pinName;
        dragPinWidget = widget;
        pendingSourceNodeId = null;
        pendingSourcePin = null;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedWidget instanceof NodeWidget) {
            captureSnapshot();
            syncNodePosition((NodeWidget) draggedWidget);
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = (NodeWidget) worldWidgets.get(i);
            if (widget.mouseReleased(wx, wy, button)) {
                return true;
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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

    private void syncNodePositions() {
        for (Map.Entry<String, NodeWidget> entry : widgetCache.entrySet()) {
            FlowNode node = graph.getNodes().get(entry.getKey());
            NodeWidget widget = entry.getValue();
            if (node != null && widget != null) {
                node.setX(widget.getX());
                node.setY(widget.getY());
            }
        }
    }

    private void syncNodePosition(NodeWidget widget) {
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
        NodeWidget widget = widgetCache.get(nodeId);
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
        NodeWidget widget = widgetCache.remove(nodeId);
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
        if (dragState.isDragging && nodeId.equals(dragState.sourceNodeId)) {
            dragState.isDragging = false;
            dragState.sourceNodeId = null;
            dragState.sourcePin = null;
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

        NodeWidget widget = findNodeAt(wx, wy);
        if (widget != null) {
            String pinName = widget.getPinAtPosition(wx, wy);
            if (pinName != null && disconnectPin(widget, pinName, wx, wy)) {
                return true;
            }
            selectNode(widget, hasShiftDown() || hasControlDown());
            return true;
        }

        showAllNodesMenu(screenX, screenY);
        return true;
    }

    private NodeWidget findNodeAt(int wx, int wy) {
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = worldWidgets.get(i);
            if (widget instanceof NodeWidget nodeWidget) {
                if (nodeWidget.isMouseOver(wx, wy)) {
                    return nodeWidget;
                }
            }
        }
        return null;
    }


    private void selectNode(NodeWidget widget, boolean toggle) {
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
        for (Map.Entry<String, NodeWidget> entry : widgetCache.entrySet()) {
            NodeWidget widget = entry.getValue();
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

    private void renderSelectionHighlights(IDrawContext context) {
        if (selectedNodeIds.isEmpty()) {
            return;
        }
        int accentColor = ThemeManager.getDefaultAccent().getAccentColor();
        int border = ThemeManager.getAnimatedColor("flow_node_selection_border".hashCode(),
            (accentColor & 0x00FFFFFF) | 0xAA000000);
        for (String nodeId : selectedNodeIds) {
            NodeWidget widget = widgetCache.get(nodeId);
            if (widget == null) {
                continue;
            }
            int x1 = widget.getX() - SELECTION_BORDER_PADDING;
            int y1 = widget.getY() - SELECTION_BORDER_PADDING;
            int x2 = widget.getX() + widget.getWidth() + SELECTION_BORDER_PADDING;
            int y2 = widget.getY() + widget.getHeight() + SELECTION_BORDER_PADDING;
            context.fillBorder(x1, y1, x2, y2, 1, border);
        }
    }

    private void renderSelectionBox(IDrawContext context) {
        if (!isSelecting) {
            return;
        }
        int accentColor = ThemeManager.getDefaultAccent().getAccentColor();
        int fill = ThemeManager.getAnimatedColor("selection_fill".hashCode(),
            (accentColor & 0x00FFFFFF) | 0x44000000);
        int border = ThemeManager.getAnimatedColor("selection_border".hashCode(),
            (accentColor & 0x00FFFFFF) | 0xAA000000);

        int x1 = (int) Math.min(selectionStartX, selectionEndX);
        int y1 = (int) Math.min(selectionStartY, selectionEndY);
        int x2 = (int) Math.max(selectionStartX, selectionEndX);
        int y2 = (int) Math.max(selectionStartY, selectionEndY);
        context.fill(x1, y1, x2, y2, fill);
        context.fillBorder(x1, y1, x2, y2, 1, border);
    }

    private boolean canConnect(NodeWidget sourceWidget, String sourcePin, NodeWidget targetWidget, String targetPin) {
        FlowType sourceType = sourceWidget.getPinType(sourcePin, false);
        FlowType targetType = targetWidget.getPinType(targetPin, true);
        NodeDefinition.PinType sourceKind = sourceWidget.getPinKind(sourcePin, false);
        NodeDefinition.PinType targetKind = targetWidget.getPinKind(targetPin, true);

        if (sourceType == null || targetType == null) {
            return false;
        }

        if (sourceKind == NodeDefinition.PinType.FLOW || targetKind == NodeDefinition.PinType.FLOW) {
            return sourceKind == NodeDefinition.PinType.FLOW
                && targetKind == NodeDefinition.PinType.FLOW
                && sourceType == FlowType.EXECUTION
                && targetType == FlowType.EXECUTION;
        }

        if (sourceKind != NodeDefinition.PinType.DATA || targetKind != NodeDefinition.PinType.DATA) {
            return false;
        }

        return sourceType.isCompatibleWith(targetType);
    }

    private void tryCompleteWire(double worldMouseX, double worldMouseY, double screenMouseX, double screenMouseY) {
        int wx = (int) worldMouseX;
        int wy = (int) worldMouseY;

        boolean connected = false;

        for (Widget widget : worldWidgets) {
            if (widget instanceof NodeWidget targetWidget) {
                if (targetWidget != dragPinWidget) {
                    String targetPin = targetWidget.getPinAtPosition(wx, wy);
                    if (targetPin != null) {
                        String targetNodeId = findNodeId(targetWidget);
                        double[] bounds = targetWidget.getPinBounds(targetPin, true);

                        if (bounds != null && canConnect(dragPinWidget, dragState.sourcePin, targetWidget, targetPin)) {
                            FlowConnection newConnection = new FlowConnection(
                                dragState.sourceNodeId,
                                dragState.sourcePin,
                                targetNodeId,
                                targetPin
                            );

                            removeExistingInputConnection(targetNodeId, targetPin);
                            graph.getConnections().add(newConnection);
                            refreshInputWidgets(targetNodeId);
                            captureSnapshot();
                            connected = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!connected && dragPinWidget != null) {
            FlowType sourceType = dragPinWidget.getPinType(dragState.sourcePin, false);
            if (sourceType != null && sourceType != FlowType.EXECUTION) {
                pendingSourceNodeId = dragState.sourceNodeId;
                pendingSourcePin = dragState.sourcePin;
                showAddNodeMenu((int) screenMouseX, (int) screenMouseY, sourceType);
            }
        }
    }


    private void bringToFront(NodeWidget widget) {
        if (widget == null) {
            return;
        }
        worldWidgets.remove(widget);
        worldWidgets.add(widget);
    }

    private void removeExistingInputConnection(String nodeId, String pinName) {
        if (graph.getConnections() == null) return;

        NodeWidget targetWidget = widgetCache.get(nodeId);
        if (targetWidget != null) {
            NodeDefinition.PinType pinType = targetWidget.getPinKind(pinName, true);
            if (pinType == NodeDefinition.PinType.FLOW) {
                return;
            }
        }

        boolean removed = graph.getConnections().removeIf(conn ->
            conn.getTargetNodeId().equals(nodeId) && conn.getTargetPin().equals(pinName)
        );
        if (removed) {
            refreshInputWidgets(nodeId);
        }
    }

    private String findNodeId(NodeWidget widget) {
        for (Map.Entry<String, NodeWidget> entry : widgetCache.entrySet()) {
            if (entry.getValue() == widget) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void showAddNodeMenu(int x, int y, FlowType sourceType) {
        if (nodeItemSelector != null) {
            remove(nodeItemSelector);
            nodeItemSelector = null;
        }

        clearSelection();

        double[] worldPos = screenToWorld(x, y);
        int worldX = (int) worldPos[0];
        int worldY = (int) worldPos[1];

        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this);

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(serverId)) {
            List<NodeDefinition> definitions = new ArrayList<>(NodeRegistry.getInstance().getAllDefinitions(serverId).values());
            definitions.removeIf(NodeDefinition::isHidden);
            definitions.sort(Comparator
                .comparingInt(NodeDefinition::getPriority)
                .thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            for (NodeDefinition def : definitions) {
                String compatiblePin = null;
                if (sourceType != null) {
                    compatiblePin = findCompatibleInput(def, sourceType);
                    if (compatiblePin == null) {
                        continue;
                    }
                }
                String pinName = compatiblePin;
                int finalWorldX = worldX;
                int finalWorldY = worldY;
                builder.addItem(def.getDisplayName(),
                    () -> {
                        captureSnapshot();
                        addNode(finalWorldX, finalWorldY, def.getId(), pinName);
                    });
            }
        }

        nodeItemSelector = builder.build();
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(x, y);
    }

    private String findCompatibleInput(NodeDefinition definition, FlowType sourceType) {
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (input.getType() == NodeDefinition.PinType.DATA && sourceType.isCompatibleWith(input.getDataType())) {
                return input.getName();
            }
        }
        return null;
    }

    private void showAllNodesMenu(int screenX, int screenY) {
        if (nodeItemSelector != null) {
            remove(nodeItemSelector);
            nodeItemSelector = null;
        }

        clearSelection();

        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this);

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(serverId)) {
            List<NodeDefinition> definitions = new ArrayList<>(NodeRegistry.getInstance().getAllDefinitions(serverId).values());
            definitions.removeIf(NodeDefinition::isHidden);
            definitions.sort(Comparator
                .comparingInt(NodeDefinition::getPriority)
                .thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            for (NodeDefinition def : definitions) {
                builder.addItem(def.getDisplayName(),
                    () -> {
                        captureSnapshot();
                        addNodeAtCenter(def.getId());
                    });
            }
        }

        nodeItemSelector = builder.build();
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(screenX, screenY);
    }

    private void addNode(int x, int y, String type) {
        addNode(x, y, type, null);
    }

    private void addNode(int x, int y, String type, String autoWirePin) {
        String id = UUID.randomUUID().toString();
        FlowNode node = new FlowNode(type, x, y, new HashMap<>());
        graph.getNodes().put(id, node);

        NodeWidget widget = new NodeWidget(x, y, node, graph, id, serverId, () -> deleteNode(id));
        widgetCache.put(id, widget);
        addWorldWidget(widget);

        if (pendingSourceNodeId != null && pendingSourcePin != null && autoWirePin != null) {
            FlowConnection newConnection = new FlowConnection(
                pendingSourceNodeId,
                pendingSourcePin,
                id,
                autoWirePin
            );
            removeExistingInputConnection(id, autoWirePin);
            graph.getConnections().add(newConnection);
            widget.refreshInputWidgets();
        }

        pendingSourceNodeId = null;
        pendingSourcePin = null;
    }

    private void onSave() {
        syncNodePositions();
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            flowManager.saveFlow(serverId, graph);
        }
    }

    private boolean disconnectPin(NodeWidget widget, String pinName, int wx, int wy) {
        String nodeId = findNodeId(widget);
        if (nodeId == null || graph.getConnections() == null) {
            return false;
        }

        double[] inputBounds = widget.getPinBounds(pinName, true);
        if (isInside(wx, wy, inputBounds)) {
            boolean removed = graph.getConnections().removeIf(conn ->
                nodeId.equals(conn.getTargetNodeId()) && pinName.equals(conn.getTargetPin())
            );
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
                boolean match = nodeId.equals(conn.getSourceNodeId()) && pinName.equals(conn.getSourcePin());
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
        double hitRadiusSq = hitRadius * hitRadius;

        for (FlowConnection conn : graph.getConnections()) {
            NodeWidget source = widgetCache.get(conn.getSourceNodeId());
            NodeWidget target = widgetCache.get(conn.getTargetNodeId());
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

            float c1x = startX + 50 * zoomLevel;
            float c2x = endX - 50 * zoomLevel;

            float step = 0.05f;
            for (float t = 0; t <= 1; t += step) {
                double x = Math.pow(1 - t, 3) * startX + 3 * Math.pow(1 - t, 2) * t * c1x + 3 * (1 - t) * Math.pow(t, 2) * c2x + Math.pow(t, 3) * endX;
                double y = Math.pow(1 - t, 3) * startY + 3 * Math.pow(1 - t, 2) * t * startY + 3 * (1 - t) * Math.pow(t, 2) * endY + Math.pow(t, 3) * endY;

                double dx = worldX - x;
                double dy = worldY - y;
                if (dx * dx + dy * dy <= hitRadiusSq) {
                    return conn;
                }
            }
        }
        return null;
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
                clipboard.nodes.add(new CopiedNode(
                    node.getType(),
                    node.getX() - minX,
                    node.getY() - minY,
                    node.getInputValues()
                ));
            }
        }

        if (graph.getConnections() != null) {
            for (FlowConnection conn : graph.getConnections()) {
                if (selectedNodeIds.contains(conn.getSourceNodeId()) && selectedNodeIds.contains(conn.getTargetNodeId())) {
                    Integer sourceIdx = nodeIdToIndex.get(conn.getSourceNodeId());
                    Integer targetIdx = nodeIdToIndex.get(conn.getTargetNodeId());
                    if (sourceIdx != null && targetIdx != null) {
                        clipboard.connections.add(new CopiedConnection(
                            sourceIdx,
                            conn.getSourcePin(),
                            targetIdx,
                            conn.getTargetPin()
                        ));
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

            NodeWidget widget = new NodeWidget((int) x, (int) y, newNode, graph, newId, serverId, () -> deleteNode(newId));
            addWorldWidget(widget);
            widgetCache.put(newId, widget);

            indexToNewNodeId.put(i, newId);
            newSelectedIds.add(newId);
        }

        for (CopiedConnection conn : clipboard.connections) {
            String sourceId = indexToNewNodeId.get(conn.sourceIndex);
            String targetId = indexToNewNodeId.get(conn.targetIndex);
            if (sourceId != null && targetId != null) {
                FlowConnection newConnection = new FlowConnection(
                    sourceId,
                    conn.sourcePin,
                    targetId,
                    conn.targetPin
                );
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
        undoStack.add(new GraphSnapshot(graph.getNodes(), graph.getConnections(), selectedNodeIds));
        if (undoStack.size() > MAX_UNDO_SIZE) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;

        isUndoing = true;

        GraphSnapshot redoSnapshot = new GraphSnapshot(graph.getNodes(), graph.getConnections(), selectedNodeIds);
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

        GraphSnapshot undoSnapshot = new GraphSnapshot(graph.getNodes(), graph.getConnections(), selectedNodeIds);
        undoStack.add(undoSnapshot);
        if (undoStack.size() > MAX_UNDO_SIZE) {
            undoStack.remove(0);
        }

        GraphSnapshot snapshot = redoStack.remove(redoStack.size() - 1);
        restoreSnapshot(snapshot);

        isUndoing = false;
    }

    private void restoreSnapshot(GraphSnapshot snapshot) {
        for (NodeWidget widget : widgetCache.values()) {
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
            NodeWidget widget = new NodeWidget((int)node.getX(), (int)node.getY(), node, graph, nodeId, serverId, () -> deleteNode(nodeId));
            addWorldWidget(widget);
            widgetCache.put(nodeId, widget);
        }

        graph.getConnections().clear();
        graph.getConnections().addAll(snapshot.connections);

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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (paletteSidePanel != null && paletteSidePanel.isVisible() && paletteSidePanel.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int) worldMouse[0];
        int wy = (int) worldMouse[1];
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = (NodeWidget) worldWidgets.get(i);
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
