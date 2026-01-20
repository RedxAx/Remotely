package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowType;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlowEditorScreen extends InfiniteScreen {
    private final FlowGraph graph;
    private final String serverId;
    private final Map<String, NodeWidget> widgetCache = new HashMap<>();
    private IconButton saveButton;
    private IconButton backButton;
    private static final float WIRE_THICKNESS = 2.0f;

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

        if (button == 1) {
            showAddNodeMenu(wx, wy);
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
                return true;
            }

            if (widget.isMouseOver(wx, wy)) {
                focusedNode = widget;
                setFocusedWidget(null);
                if (button == 0) {
                    draggedWidget = widget;
                    dragOffsetX = wx - widget.getX();
                    dragOffsetY = wy - widget.getY();
                }
                widget.mouseClicked(wx, wy, button);
                return true;
            }
        }

        focusedNode = null;
        setFocusedWidget(null);
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
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragState.isDragging) {
            double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
            double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
            tryCompleteWire(worldMouse[0], worldMouse[1]);

            dragState.isDragging = false;
            dragState.sourceNodeId = null;
            dragState.sourcePin = null;
            return true;
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
        context.getMatrices().pop();

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

        graph.getConnections().removeIf(conn ->
            conn.getTargetNodeId().equals(nodeId) && conn.getTargetPin().equals(pinName)
        );
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
        }

        pendingSourceNodeId = null;
        pendingSourcePin = null;
        pendingSourceType = null;
    }
}
