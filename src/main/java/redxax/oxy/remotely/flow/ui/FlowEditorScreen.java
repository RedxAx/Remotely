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
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.InfiniteScreen;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FlowEditorScreen extends InfiniteScreen {
    private final FlowGraph graph;
    private final String serverId;
    private final Map<String, NodeWidget> widgetCache = new HashMap<>();
    private IconButton saveButton;
    private IconButton backButton;
    private static final float WIRE_THICKNESS = 2.0f;
    private static final float WIRE_HIT_RADIUS = 6.0f;
    private static final int SELECTION_BORDER_PADDING = 2;
    private final Set<String> selectedNodeIds = new HashSet<>();
    private final Set<String> selectionBase = new HashSet<>();
    private boolean isSelecting = false;
    private boolean selectionAdditive = false;
    private double selectionStartX = 0;
    private double selectionStartY = 0;
    private double selectionEndX = 0;
    private double selectionEndY = 0;

    public FlowEditorScreen(FlowGraph graph) {
        this(graph, null);
    }

    public FlowEditorScreen(FlowGraph graph, String serverId) {
        super();
        this.graph = graph;
        this.serverId = serverId;
        this.dragState = new DragState();
        this.dragPinWidget = null;
        this.nodeContextMenu = null;

        for (var entry : graph.getNodes().entrySet()) {
            NodeWidget widget = new NodeWidget((int)entry.getValue().getX(), (int)entry.getValue().getY(), entry.getValue(), graph, entry.getKey());
            addWorldWidget(widget);
            widgetCache.put(entry.getKey(), widget);
        }

        createSaveButton();
        createBackButton();
    }

    private void createSaveButton() {
        saveButton = new IconButton.Builder()
            .label("Save Flow")
            .pos(10, 10)
            .size(100, 20)
            .onClick(() -> onSave())
            .build();
        addDrawableChild(saveButton);
    }

    private void createBackButton() {
        backButton = new IconButton.Builder()
            .label("Back")
            .pos(10, 35)
            .size(60, 20)
            .onClick(() -> close())
            .build();
        addDrawableChild(backButton);
    }

    private void onSave() {
        syncNodePositions();
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            flowManager.saveFlow(serverId, graph);
        }
    }

    private static class DragState {
        String sourceNodeId;
        String sourcePin;
        boolean isDragging;
    }
    private DragState dragState;
    private NodeWidget dragPinWidget;
    private ContextMenuWidget nodeContextMenu;
    private NodeWidget focusedNode;

    private String pendingSourceNodeId;
    private String pendingSourcePin;
    private FlowType pendingSourceType;

    private double dragMouseX = 0;
    private double dragMouseY = 0;

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
                    drawBezier(context, startX, startY, endX, endY, wireColor);
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
                drawBezier(context, (float)sourcePinWorld[0], (float)sourcePinWorld[1],
                          (float)mouseWorld[0], (float)mouseWorld[1], dragWireColor);
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
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        dragMouseX = undistortedCoords[0];
        dragMouseY = undistortedCoords[1];

        if (isSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectionEndX = undistortedCoords[0];
            selectionEndY = undistortedCoords[1];
            updateSelectionFromBox();
            return true;
        }

        if (dragState.isDragging) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void drawBezier(IDrawContext context, float startX, float startY, float endX, float endY, int color) {
        float c1x = startX + 50 * zoomLevel;
        float c1y = startY;
        float c2x = endX - 50 * zoomLevel;
        float c2y = endY;

        float step = 0.05f;

        for (float t = 0; t <= 1; t += step) {
            double x = Math.pow(1-t, 3)*startX + 3*Math.pow(1-t, 2)*t*c1x + 3*(1-t)*Math.pow(t, 2)*c2x + Math.pow(t, 3)*endX;
            double y = Math.pow(1-t, 3)*startY + 3*Math.pow(1-t, 2)*t*c1y + 3*(1-t)*Math.pow(t, 2)*c2y + Math.pow(t, 3)*endY;

            context.fill((int)x - 1, (int)y - 1, (int)x + 2, (int)y + 2, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int)worldMouse[0];
        int wy = (int)worldMouse[1];

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (handleRightClick(wx, wy, (int) undistortedCoords[0], (int) undistortedCoords[1])) {
                return true;
            }
            return true;
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = (NodeWidget) worldWidgets.get(i);

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

            TextInputWidget inputWidget = widget.getInputWidgetAt(wx, wy);
            if (inputWidget != null) {
                inputWidget.mouseClicked(wx, wy, button);
                setFocusedWidget(inputWidget);
                focusedNode = widget;
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
        pendingSourceType = null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
            && !(getFocusedWidget() instanceof TextInputWidget)) {
            if (!selectedNodeIds.isEmpty()) {
                deleteSelectedNodes();
                return true;
            }
            if (focusedNode != null) {
                deleteNode(findNodeId(focusedNode));
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
            showNodeMenu(widget, screenX, screenY);
            return true;
        }

        showAddNodeMenu(wx, wy);
        return true;
    }

    private NodeWidget findNodeAt(int wx, int wy) {
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = worldWidgets.get(i);
            if (widget instanceof NodeWidget) {
                NodeWidget nodeWidget = (NodeWidget) widget;
                if (nodeWidget.isMouseOver(wx, wy)) {
                    return nodeWidget;
                }
            }
        }
        return null;
    }

    private void showNodeMenu(NodeWidget widget, int x, int y) {
        if (nodeContextMenu != null) {
            remove(nodeContextMenu);
            nodeContextMenu = null;
        }
        String nodeId = findNodeId(widget);
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this);
        builder.addItem("Delete Node", () -> deleteNode(nodeId), "Delete this node");
        if (selectedNodeIds.size() > 1) {
            builder.addItem("Delete Selected", this::deleteSelectedNodes, "Delete selected nodes");
        }
        nodeContextMenu = builder.build();
        addDrawableChild(nodeContextMenu);
        nodeContextMenu.show(x, y);
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
            for (String targetId : affectedTargets) {
                refreshInputWidgets(targetId);
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
            float c1y = startY;
            float c2x = endX - 50 * zoomLevel;
            float c2y = endY;

            float step = 0.05f;
            for (float t = 0; t <= 1; t += step) {
                double x = Math.pow(1 - t, 3) * startX + 3 * Math.pow(1 - t, 2) * t * c1x
                    + 3 * (1 - t) * Math.pow(t, 2) * c2x + Math.pow(t, 3) * endX;
                double y = Math.pow(1 - t, 3) * startY + 3 * Math.pow(1 - t, 2) * t * c1y
                    + 3 * (1 - t) * Math.pow(t, 2) * c2y + Math.pow(t, 3) * endY;

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
            refreshInputWidgets(conn.getTargetNodeId());
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
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
            selectionEndX = undistortedCoords[0];
            selectionEndY = undistortedCoords[1];
            updateSelectionFromBox();
            isSelecting = false;
            return true;
        }

        if (dragState.isDragging) {
            double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
            double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
            tryCompleteWire(worldMouse[0], worldMouse[1]);

            dragState.isDragging = false;
            dragState.sourceNodeId = null;
            dragState.sourcePin = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedWidget instanceof NodeWidget) {
            syncNodePosition((NodeWidget) draggedWidget);
        }

        return super.mouseReleased(mouseX, mouseY, button);
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

        for (Notification notification : Notification.getActiveNotifications()) {
            notification.render(context, mouseX, mouseY, delta);
        }
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

    private void tryCompleteWire(double worldMouseX, double worldMouseY) {
        int wx = (int) worldMouseX;
        int wy = (int) worldMouseY;

        boolean connected = false;

        for (Widget widget : worldWidgets) {
            if (widget instanceof NodeWidget) {
                NodeWidget targetWidget = (NodeWidget) widget;
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
                pendingSourceType = sourceType;
                showAddNodeMenu(wx, wy, sourceType);
            }
        }
    }

    private void removeExistingInputConnection(String nodeId, String pinName) {
        if (graph.getConnections() == null) return;

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

    private void showAddNodeMenu(int x, int y) {
        showAddNodeMenu(x, y, null);
    }

    private void showAddNodeMenu(int x, int y, FlowType sourceType) {
        if (nodeContextMenu != null) {
            remove(nodeContextMenu);
            nodeContextMenu = null;
        }

        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this);

        if (NodeRegistry.getInstance() != null) {
            for (NodeDefinition def : NodeRegistry.getInstance().getAllDefinitions().values()) {
                String compatiblePin = null;
                if (sourceType != null) {
                    compatiblePin = findCompatibleInput(def, sourceType);
                    if (compatiblePin == null) {
                        continue;
                    }
                }
                String pinName = compatiblePin;
                builder.addItem(def.getDisplayName(),
                    () -> addNode(x, y, def.getId(), pinName),
                    def.getCategory().toString().toLowerCase());
            }
        } else {
            builder.addItem("Log", () -> addNode(x, y, "log"), "Log To Console");
            builder.addItem("Player Message", () -> addNode(x, y, "player_message"), "Send Message To Player");
            builder.addItem("If", () -> addNode(x, y, "if"), "Conditional Branching");
            builder.addItem("Give Item", () -> addNode(x, y, "give_item"), "Give Item To Player");
        }

        nodeContextMenu = builder.build();
        addDrawableChild(nodeContextMenu);
        nodeContextMenu.show(x, y);
    }

    private String findCompatibleInput(NodeDefinition definition, FlowType sourceType) {
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (input.getType() == NodeDefinition.PinType.DATA && sourceType.isCompatibleWith(input.getDataType())) {
                return input.getName();
            }
        }
        return null;
    }

    private void addNode(int x, int y, String type) {
        addNode(x, y, type, null);
    }

    private void addNode(int x, int y, String type, String autoWirePin) {
        String id = UUID.randomUUID().toString();
        FlowNode node = new FlowNode(type, x, y, new HashMap<>());
        graph.getNodes().put(id, node);

        NodeWidget widget = new NodeWidget(x, y, node, graph, id);
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
        pendingSourceType = null;
    }
}
