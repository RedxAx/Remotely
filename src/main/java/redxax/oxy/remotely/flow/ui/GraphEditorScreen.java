package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.DesignerSaveNotifications;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.FlowDebugController;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.data.flow.world.WorldDashboardEntry;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowTypeRef;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.cache.NodeRegistryCache;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.resync.flow.contract.FlowCategoryMetadata;
import redxax.oxy.remotely.flow.sync.FlowConversionRule;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioView;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.ScreenBackedStudioView;
import redxax.oxy.remotely.flow.ui.studio.GuiStudioPreviewView;
import redxax.oxy.remotely.flow.ui.studio.ScoreboardStudioPreviewView;
import redxax.oxy.remotely.flow.ui.studio.StudioDocument;
import redxax.oxy.remotely.flow.ui.studio.StudioPanel;
import redxax.oxy.remotely.flow.ui.studio.StudioResourceRenameAware;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import redxax.oxy.remotely.flow.ui.studio.StudioHeaderProvider;
import redxax.oxy.remotely.flow.ui.studio.StudioViewportState;
import redxax.oxy.remotely.flow.ui.studio.TabStudioPreviewView;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.ui.WorldGenEditorScreen;
import org.lwjgl.glfw.GLFW;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.platform.UiHost;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.*;
import restudio.rescreen.ui.rescreen.ReScreen.HeaderBuilder.Position;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import static restudio.rescreen.config.Config.desktopMode;
import static restudio.rescreen.config.Config.shadow;
import static restudio.rescreen.render.TextRenderer.tr;

public class GraphEditorScreen extends StudioScreen implements UiHost, StudioHeaderProvider, DesktopWindowBehaviorProvider, StudioResourceRenameAware {
    private static final String CUSTOM_FUNCTION_NODE_PREFIX = "custom_function:";
    private static final Gson GSON = new Gson();
    protected static final Set<GraphEditorScreen> OPEN_SCREENS = new CopyOnWriteArraySet<>();
    protected FlowGraph graph;
    protected final String serverId;
    private static Screen parent;
    private final Screen ownerScreen;
    protected final Map<String, FlowNodeWidget> widgetCache = new HashMap<>();
    private static final float WIRE_HIT_RADIUS = 6.0f;
    private static final double FUZZY_WIRE_NODE_MARGIN = 30.0;
    private static final double FUZZY_WIRE_PIN_RADIUS = 42.0;
    private static final int WIRE_OUT_OFFSET = 26;
    private static final int CONNECTION_PAN_EDGE = 56;
    private static final float CONNECTION_PAN_SPEED = 14.0F;
    private final Set<String> selectedNodeIds = new HashSet<>();
    private final Set<String> selectionBase = new HashSet<>();
    private final Map<String, int[]> selectedDragStartPositions = new HashMap<>();
    private boolean isSelecting = false;
    private boolean selectionAdditive = false;
    private double selectionStartX = 0;
    private double selectionStartY = 0;
    private double selectionEndX = 0;
    private double selectionEndY = 0;

    protected SidePanel paletteSidePanel;
    protected StudioPanel paletteStudioPanel;
    private final Map<NodeDefinition.NodeCategory, PopupWidget> categoryPopups = new HashMap<>();
    private List<NodeDefinition.NodeCategory> categoryOrder = List.of();

    private IconButton headerBackground;
    private AnimatedWidget debugToggleButton;
    private AnimatedWidget debugResumeButton;
    private AnimatedWidget debugStepButton;
    private AnimatedWidget debugStopButton;
    private boolean initialized;
    private boolean debugMode;
    private static final float INITIAL_VIEWPORT_MAX_ZOOM = 1.0F;
    private static final float INITIAL_VIEWPORT_START_ZOOM = 3.0F;
    private static final int INITIAL_VIEWPORT_PADDING = 80;
    private static final int INITIAL_VIEWPORT_STABLE_FRAMES = 2;
    private boolean initialViewportFitPending = true;
    private int initialViewportStableFrames;
    private int initialViewportSignature;
    private final Set<String> initialViewportFittedKeys = new HashSet<>();

    private int initialWidth;
    private int initialHeight;
    private final ClientServerView startupServer;
    private final String loaderHint;
    private final String serverTitle;
    private final ReSyncProvisioningService reSyncProvisioningService = new ReSyncProvisioningService();
    private StudioStartupState startupState = StudioStartupState.READY;
    private IconMessage startupIcon;
    private IconButton startupCloseButton;
    private IconButton setupReSyncButton;
    private IconButton welcomeServerButton;
    private boolean startupProbeRunning;
    private boolean setupRunning;
    private volatile boolean reSyncUpdateAvailable;
    private volatile boolean reSyncUpdateRunning;
    private volatile boolean reSyncUpdateProbeRunning;
    private boolean studioChromeBuilt;
    private boolean liveStudioWorkspaceRequested;
    private boolean liveStudioFullEditorMode;
    private long lastStartupProbeAt;

    private enum StudioStartupState {
        LOADING,
        NOT_SUPPORTED,
        SETUP,
        INSTALLING,
        INSTALLED,
        SERVER_STOPPED,
        READY
    }

    private static class DragState {
        String sourceNodeId;
        String sourcePin;
        boolean isDragging;
        boolean sourceIsInput;
    }

    private record NodeSelectorVariant(NodeDefinition.PinDefinition selectorPin, String option) {
    }

    private record FuzzyWireTarget(FlowNodeWidget widget, String pinName, boolean input, double score) {
    }

    private record WirePreviewTarget(FlowNodeWidget widget, String pinName, boolean input) {
    }

    private record WireCompatibilityPreview(String label, int color) {
    }

    private static class FamilyVariantSelectorEntry extends AnimatedWidget {
        private static final int ENTRY_GAP = 1;
        private final String label;
        private final String familyLabel;
        private final VariantLabelWidget labelWidget;
        private final VariantTypeWidget typeWidget;
        private final int typeColumnWidth;

        private FamilyVariantSelectorEntry(String familyLabel, String label, FlowDataType type, int typeColumnWidth, Runnable action) {
            super(0, 0, 150, 14, variantMessage(familyLabel, label, type));
            this.familyLabel = familyLabel != null ? familyLabel : "";
            this.label = label;
            this.labelWidget = new VariantLabelWidget(this.familyLabel, this.label, action);
            this.typeWidget = new VariantTypeWidget(type);
            this.typeColumnWidth = typeColumnWidth > 0 ? typeColumnWidth : typeWidget.desiredWidth();
            this.entranceAnimationEnabled = false;
            this.animateElevation = false;
            this.enableHoverColors = false;
            this.transparent = true;
            setHint("Family Variant\n" + this.familyLabel + "\n" + this.label + "\n" + this.typeWidget.typeLabel);
        }

        private static String variantMessage(String familyLabel, String label, FlowDataType type) {
            String typeLabel = type != null ? type.getDisplayName() : FlowDataType.ANY.getDisplayName();
            return (familyLabel != null ? familyLabel : "") + " " + (label != null ? label : "") + " " + typeLabel;
        }

        private static int variantTypeWidth(FlowDataType type) {
            return VariantTypeWidget.desiredWidth(type);
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            int typeWidth = typeColumnWidth;
            int typeX = getX() + getWidth() - typeWidth;
            labelWidget.setPosition(getX(), getY());
            labelWidget.setSize(Math.max(0, typeX - getX() - ENTRY_GAP), getHeight());
            labelWidget.render(ctx, mouseX, mouseY, 0f);

            typeWidget.setPosition(typeX, getY());
            typeWidget.setSize(typeWidth, getHeight());
            typeWidget.render(ctx, mouseX, mouseY, 0f);
        }

        @Override
        public boolean mouseClicked(ReMouseEvent event) {
            if (!visible || !active || !isMouseOver(event.x(), event.y())) {
                return false;
            }
            if (Widget.dispatchMouseClicked(labelWidget, event.retarget(labelWidget, event.x(), event.y()))) {
                return true;
            }
            return typeWidget.isMouseOver(event.x(), event.y());
        }

        private static class VariantLabelWidget extends AnimatedWidget {
            private final String familyLabel;
            private final String label;
            private final Runnable action;

            private VariantLabelWidget(String familyLabel, String label, Runnable action) {
                super(0, 0, 100, 14, (familyLabel != null ? familyLabel : "") + " " + (label != null ? label : ""));
                this.familyLabel = familyLabel != null ? familyLabel : "";
                this.label = label != null ? label : "";
                this.action = action;
                this.entranceAnimationEnabled = false;
                this.animateElevation = false;
                this.selectable = true;
                setCursorHoverReactive(true);
            }

            @Override
            protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
                int mutedTextColor = ThemeManager.getColor(ThemeColor.textDark);
                int textX = getX() + 5;
                int textY = getY() + (getHeight() - ITextRenderer.fontHeight) / 2 + 1;
                int maxLabelWidth = Math.max(0, getWidth() - 10);
                String separator = " / ";
                String displayLabel = familyLabel.isBlank() ? label : familyLabel + separator + label;
                int displayWidth = tr.getWidth(displayLabel);
                if (displayWidth <= maxLabelWidth && !familyLabel.isBlank()) {
                    int familyWidth = tr.getWidth(familyLabel);
                    int separatorWidth = tr.getWidth(separator);
                    ctx.drawText(familyLabel, textX, textY, mutedTextColor, shadow);
                    ctx.drawText(separator, textX + familyWidth, textY, mutedTextColor, shadow);
                    ctx.drawText(label, textX + familyWidth + separatorWidth, textY, textColor, shadow);
                } else {
                    ctx.drawText(ellipsize(displayLabel, maxLabelWidth), textX, textY, textColor, shadow);
                }
            }

            @Override
            public void onClick(double mouseX, double mouseY, int button) {
                if (button == 0 && action != null) {
                    action.run();
                }
            }

            private String ellipsize(String value, int maxWidth) {
                if (value == null || value.isBlank() || tr.getWidth(value) <= maxWidth) {
                    return value == null ? "" : value;
                }
                String suffix = "...";
                int suffixWidth = tr.getWidth(suffix);
                StringBuilder builder = new StringBuilder(value);
                while (!builder.isEmpty() && tr.getWidth(builder.toString()) + suffixWidth > maxWidth) {
                    builder.setLength(builder.length() - 1);
                }
                return builder + suffix;
            }
        }

        private static class VariantTypeWidget extends AnimatedWidget {
            private static final int TYPE_PIN_SIZE = 8;
            private final String typeLabel;
            private final int typeColor;

            private VariantTypeWidget(FlowDataType type) {
                super(0, 0, 32, 14, type != null ? type.getDisplayName() : FlowDataType.ANY.getDisplayName());
                this.typeLabel = type != null ? type.getDisplayName() : FlowDataType.ANY.getDisplayName();
                this.typeColor = type != null ? type.getColor() : FlowDataType.ANY.getColor();
                this.active = false;
                this.enableHoverColors = false;
                this.animateElevation = false;
                this.entranceAnimationEnabled = false;
            }

            private int desiredWidth() {
                return desiredWidth(typeLabel);
            }

            private static int desiredWidth(FlowDataType type) {
                return desiredWidth(type != null ? type.getDisplayName() : FlowDataType.ANY.getDisplayName());
            }

            private static int desiredWidth(String typeLabel) {
                return TYPE_PIN_SIZE + 10 + tr.getWidth(typeLabel);
            }

            @Override
            protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
                int pinY = getY() + (getHeight() - TYPE_PIN_SIZE) / 2;
                int textY = getY() + (getHeight() - ITextRenderer.fontHeight) / 2 + 1;
                ctx.fill(getX() + 3, pinY, getX() + 3 + TYPE_PIN_SIZE, pinY + TYPE_PIN_SIZE, typeColor);
                ctx.drawText(typeLabel, getX() + TYPE_PIN_SIZE + 8, textY, ThemeManager.getColor(ThemeColor.textDark), shadow);
            }
        }
    }

    private final DragState dragState;
    private FlowNodeWidget dragPinWidget;
    private ItemSelectorWidget nodeItemSelector;
    private FlowNodeWidget focusedNode;
    private boolean movingSelectedNodes = false;

    private String pendingSourceNodeId;
    private String pendingSourcePin;
    private String pendingEditorSourceNodeId;
    private String pendingEditorSourcePin;
    private boolean pendingSourceIsInput;

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
        final boolean function;
        final String functionOwner;
        final String functionNamespace;
        final int functionVersion;
        final String functionDescription;
        final List<FlowGraph.FunctionParameter> functionInputs;
        final List<FlowGraph.FunctionParameter> functionOutputs;
        final List<FlowGraph.EditorPassthrough> editorPassthroughs;

        private GraphSnapshot(Map<String, FlowNode> nodes, List<FlowConnection> connections, Set<String> selectedIds,
                              boolean function, String functionOwner, String functionNamespace, int functionVersion, String functionDescription,
                              List<FlowGraph.FunctionParameter> functionInputs,
                              List<FlowGraph.FunctionParameter> functionOutputs, List<FlowGraph.EditorPassthrough> editorPassthroughs) {
            this.nodes = nodes;
            this.connections = connections;
            this.selectedIds = selectedIds;
            this.function = function;
            this.functionOwner = functionOwner;
            this.functionNamespace = functionNamespace;
            this.functionVersion = functionVersion;
            this.functionDescription = functionDescription;
            this.functionInputs = functionInputs;
            this.functionOutputs = functionOutputs;
            this.editorPassthroughs = editorPassthroughs;
        }

        GraphSnapshot(FlowGraph graph, Set<String> selectedIds) {
            this(
                copyNodes(graph.getNodes()),
                copyConnections(graph.getConnections()),
                new HashSet<>(selectedIds),
                graph.isFunction(),
                graph.getFunctionOwner(),
                graph.getFunctionNamespace(),
                graph.getFunctionVersion(),
                graph.getFunctionDescription(),
                copyFunctionParameters(graph.getFunctionInputs()),
                copyFunctionParameters(graph.getFunctionOutputs()),
                copyEditorPassthroughs(graph.getEditorPassthroughs())
            );
        }

        private static Map<String, FlowNode> copyNodes(Map<String, FlowNode> nodes) {
            Map<String, FlowNode> copied = new HashMap<>();
            for (Map.Entry<String, FlowNode> entry : nodes.entrySet()) {
                FlowNode node = entry.getValue();
                FlowNode copiedNode = new FlowNode(
                    node.getType(),
                    node.getX(),
                    node.getY(),
                    new HashMap<>(node.getInputValues())
                );
                copiedNode.setVersion(node.getVersion());
                copied.put(entry.getKey(), copiedNode);
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

    private record FanoutKey(String sourceNodeId, String sourcePin) {
    }

    private record PinPoint(double x, double y) {
    }

    private final StudioScreen.History<GraphSnapshot> graphHistory = history(() -> new GraphSnapshot(graph, selectedNodeIds), this::restoreSnapshot);

    @Override
    protected StudioScreen.History<?> activeHistory() {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof FocusedJsonResourceDesignerScreen resourceScreen && resourceScreen.hasResourceHistory()) {
            return resourceScreen.resourceHistory();
        }
        return graphHistory;
    }

    public GraphEditorScreen(FlowGraph graph, String serverId, Screen parent) {
        this(graph, serverId, parent, null, "", "");
    }

    public GraphEditorScreen(FlowGraph graph, String serverId, Screen parent, ClientServerView startupServer, String loaderHint) {
        this(graph, serverId, parent, startupServer, loaderHint, startupServer != null ? startupServer.name : "");
    }

    public GraphEditorScreen(FlowGraph graph, String serverId, Screen parent, ClientServerView startupServer, String loaderHint, String serverTitle) {
        super();
        this.zoomLevel = INITIAL_VIEWPORT_MAX_ZOOM;
        this.targetZoomLevel = INITIAL_VIEWPORT_START_ZOOM;
        this.graph = graph;
        this.serverId = serverId;
        this.ownerScreen = parent;
        this.startupServer = startupServer;
        this.loaderHint = safeText(loaderHint);
        this.serverTitle = safeText(serverTitle);
        if (!(parent instanceof GraphEditorScreen)) {
            GraphEditorScreen.parent = parent;
        }
        this.dragState = new DragState();
        this.dragPinWidget = null;
        this.nodeItemSelector = null;
        OPEN_SCREENS.add(this);

        for (var entry : graph.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            FlowNodeWidget widget = createNodeWidget(nodeId, entry.getValue());
            addWorldWidget(widget);
            widgetCache.put(entry.getKey(), widget);
        }
    }

    @Override
    public void resourceRenamed(String type, String oldId, String newId) {
        if (graph != null && oldId.equals(graph.getId())) {
            graph.setId(newId);
        }
    }

    public GraphEditorScreen enableStudioMode() {
        this.studioMode = true;
        this.startupState = StudioStartupState.LOADING;
        return this;
    }

    public void openStudioFlow(FlowGraph targetGraph, String title) {
        if (targetGraph == null) {
            return;
        }
        openStudioGraphDocument(targetGraph.isFunction() ? ReSyncResourceDragPayload.FUNCTION : ReSyncResourceDragPayload.FLOW,
            targetGraph.getId(), title == null || title.isBlank() ? targetGraph.getId() : title, targetGraph);
    }

    public void openWorkspaceFlowEditor(String flowId, String branchPin) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || flowId == null) {
            return;
        }
        FlowGraph targetGraph = manager.getFlowsForServer(serverId).get(flowId);
        if (targetGraph == null) {
            manager.openFlowEditor(serverId, null, flowId, branchPin);
            return;
        }
        openStudioFlow(targetGraph, manager.getFlowName(serverId, flowId));
        if (branchPin != null) {
            focusContentBranch(branchPin);
        }
    }

    public void openWorkspaceFlowEditor(String flowId) {
        openWorkspaceFlowEditor(flowId, null);
    }

    @Override
    protected boolean dropStudioResource(ReSyncResourceDragPayload payload, ReMouseEvent event) {
        if (payload == null || event == null || !payload.isLiteralAssignable() || !isGraphDropArea(event.x(), event.y())) {
            return false;
        }
        double[] undistorted = unDistortMouse(event.x(), event.y());
        double[] world = screenToWorld(undistorted[0], undistorted[1]);
        int worldX = (int) world[0];
        int worldY = (int) world[1];
        FlowNodeWidget target = findNodeAt(worldX, worldY);
        if (target != null) {
            String pin = target.getInputPinAtPosition(worldX, worldY);
            if (pin != null && acceptsResource(target, pin, payload)) {
                captureSnapshot();
                removeExistingInputConnection(findNodeId(target), pin);
                return target.assignLiteralInput(pin, payload.id());
            }
            return false;
        }

        ReSyncResourceDropCapabilities.DropSpec spec = ReSyncResourceDropCapabilities.forType(payload.type());
        NodeRegistry registry = NodeRegistry.getInstance();
        NodeDefinition dropDefinition = spec != null && registry != null ? registry.getDefinition(nodeRegistryServerId(), spec.nodeType()) : null;
        if (dropDefinition == null) {
            return false;
        }
        captureSnapshot();
        FlowConnection connection = findConnectionAt(worldX, worldY);
        if (connection != null && isFlowConnection(connection) && hasFlowPath(dropDefinition)) {
            String nodeId = addNode(worldX - 50, worldY - 20, spec.nodeType(), null, Map.of(spec.inputPin(), payload.id()));
            graph.getConnections().remove(connection);
            FlowConnection incoming = new FlowConnection(connection.getSourceNodeId(), connection.getSourcePin(), nodeId, "flow");
            copyEditorSource(connection, incoming);
            graph.getConnections().add(incoming);
            graph.getConnections().add(new FlowConnection(nodeId, "flow", connection.getTargetNodeId(), connection.getTargetPin()));
            refreshInputWidgets(nodeId);
            refreshInputWidgets(connection.getTargetNodeId());
            return true;
        }
        addNode(worldX - 50, worldY - 20, spec.nodeType(), null, Map.of(spec.inputPin(), payload.id()));
        return true;
    }

    private boolean isGraphDropArea(double mouseX, double mouseY) {
        int left = studioContentBrowser != null ? studioContentBrowser.visibleLayoutWidth() : 0;
        int right = paletteSidePanel != null && paletteSidePanel.isVisible() ? paletteSidePanel.getDesiredWidth() : 0;
        return mouseX > left && mouseX < width - right && mouseY > 30 && mouseY < height;
    }

    private boolean acceptsResource(FlowNodeWidget widget, String pin, ReSyncResourceDragPayload payload) {
        String source = widget.getInputOptionsSource(pin);
        if (("server:resync:" + payload.type()).equals(source)) {
            return true;
        }
        FlowTypeRef typeRef = widget.getPinTypeRef(pin, true);
        if (typeRef == null) {
            return false;
        }
        if (resourceValueType(payload.type()).equals(typeRef.getTypeId())) {
            return true;
        }
        return "resource_reference".equals(typeRef.getTypeId()) && !typeRef.getArguments().isEmpty()
            && payload.type().equals(typeRef.getArguments().getFirst().getTypeId());
    }

    private boolean isFlowConnection(FlowConnection connection) {
        FlowNodeWidget source = widgetCache.get(connection.getSourceNodeId());
        return source != null && source.getPinKind(connection.getSourcePin(), false) == NodeDefinition.PinType.FLOW;
    }

    private boolean hasFlowPath(NodeDefinition definition) {
        boolean input = definition.getInputs().stream().anyMatch(pin -> "flow".equals(pin.getName()) && pin.getType() == NodeDefinition.PinType.FLOW);
        boolean output = definition.getOutputs().stream().anyMatch(pin -> "flow".equals(pin.getName()) && pin.getType() == NodeDefinition.PinType.FLOW);
        return input && output;
    }

    private String resourceValueType(String resourceType) {
        return switch (resourceType) {
            case ReSyncResourceDragPayload.FLOW -> "flow_id";
            case ReSyncResourceDragPayload.FUNCTION -> "function";
            case ReSyncResourceDragPayload.COMMAND -> "command_id";
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "custom_content_id";
            case ReSyncResourceDragPayload.GUI -> "gui_id";
            case ReSyncResourceDragPayload.SCOREBOARD -> "scoreboard_id";
            case ReSyncResourceDragPayload.TAB -> "tab_id";
            case ReSyncResourceDragPayload.CHAT -> "chat_id";
            case ReSyncResourceDragPayload.MOTD_PROFILE -> "motd_profile_id";
            case ReSyncResourceDragPayload.MESSAGE_RULE -> "message_rule_id";
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "recipe_id";
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "text_template_id";
            case ReSyncResourceDragPayload.ADVANCEMENT_TREE -> "advancement_tree_id";
            case ReSyncResourceDragPayload.DIALOG -> "dialog_id";
            case ReSyncResourceDragPayload.TRADE_PROFILE -> "trade_profile_id";
            case ReSyncResourceDragPayload.NPC_DEFINITION -> "npc_id";
            case ReSyncResourceDragPayload.LOOT_TABLE -> "loot_table_id";
            case ReSyncResourceDragPayload.WORLDGEN -> "worldgen_id";
            case ReSyncResourceDragPayload.WORLD -> "world";
            default -> "";
        };
    }

    public void openWorkspaceGuiDesigner(String guiId) {
        openStudioDesigner(ReSyncResourceDragPayload.GUI, guiId);
    }

    public void openWorkspaceScoreboardDesigner(String scoreboardId) {
        openStudioDesigner(ReSyncResourceDragPayload.SCOREBOARD, scoreboardId);
    }

    public void openWorkspaceTabDesigner(String tabId) {
        openStudioDesigner(ReSyncResourceDragPayload.TAB, tabId);
    }

    public void openWorkspaceDialogDesigner(String dialogId) {
        openStudioDesigner(ReSyncResourceDragPayload.DIALOG, dialogId);
    }

    @Override
    public void refreshStudioWorkspace() {
        refreshStudioWorkspace(true);
    }

    @Override
    public void refreshStudioWorkspace(boolean rebuildContentBrowser) {
        super.refreshStudioWorkspace(rebuildContentBrowser);
    }

    @Override
    protected String studioServerId() {
        return serverId;
    }

    @Override
    protected ReSyncStudioView createStudioDocumentView(String type, String id, FlowGraph targetGraph) {
        if (targetGraph != null) {
            return null;
        }
        if (ReSyncResourceDragPayload.GUI.equals(type)) {
            return new GuiStudioPreviewView(serverId, id);
        }
        if (ReSyncResourceDragPayload.SCOREBOARD.equals(type)) {
            return new ScoreboardStudioPreviewView(serverId, id);
        }
        if (ReSyncResourceDragPayload.TAB.equals(type)) {
            return new TabStudioPreviewView(serverId, id);
        }
        return null;
    }

    public String getServerId() {
        return serverId;
    }

    public boolean loadStudioWorldGenProject(WorldGenProject project) {
        if (project == null) {
            return false;
        }
        boolean loaded = false;
        for (StudioDocument document : studioDocuments) {
            if (!ReSyncResourceDragPayload.WORLDGEN.equals(document.type()) || !project.getId().equals(document.id())) {
                continue;
            }
            if (document.view() instanceof ScreenBackedStudioView screenView && screenView.screen() instanceof WorldGenEditorScreen worldGenEditor) {
                worldGenEditor.loadProject(project);
                loaded = true;
            }
        }
        return loaded;
    }

    public boolean isStudioWorkspaceReady() {
        return studioMode && startupState == StudioStartupState.READY && studioChromeBuilt;
    }

    public void dismissStudioWorkspace() {
        saveActiveStudioViewport();
        OPEN_SCREENS.remove(this);
    }

    public GraphEditorScreen setLiveStudioFullEditorMode(boolean fullEditorMode) {
        if (liveStudioFullEditorMode == fullEditorMode) {
            return this;
        }
        liveStudioFullEditorMode = fullEditorMode;
        if (studioMode && studioChromeBuilt) {
            headerButtons.clear();
            debugToggleButton = null;
            debugResumeButton = null;
            debugStepButton = null;
            debugStopButton = null;
            createHeaderButtons();
            syncDebugHeaderVisibility();
            refreshActiveViewHeaderButtons();
        }
        return this;
    }

    public void prepareLiveStudioWorkspace() {
        if (!studioMode || isStudioWorkspaceReady()) {
            return;
        }
        liveStudioWorkspaceRequested = true;
        if (initialized) {
            enterStudioReadyState();
        }
    }

    private String nodeRegistryServerId() {
        return activeNodeRegistryServerId != null && !activeNodeRegistryServerId.isBlank() ? activeNodeRegistryServerId : serverId;
    }

    public static void refreshCatalogForServer(String serverId) {
        refreshCatalogForServer(serverId, null);
    }

    public static void refreshCatalogForServer(String serverId, String sourceId) {
        for (GraphEditorScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.onOptionCatalogRefreshed(sourceId);
            }
        }
    }

    public static GraphEditorScreen getStudioScreen(String serverId) {
        for (GraphEditorScreen screen : OPEN_SCREENS) {
            if (screen != null
                && screen.isStudioMode()
                && screen.startupState == StudioStartupState.READY
                && screen.studioChromeBuilt
                && serverId != null
                && serverId.equals(screen.getServerId())) {
                return screen;
            }
        }
        return null;
    }

    public static void refreshWorldsForServer(String serverId) {
        for (GraphEditorScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.onWorldSnapshotRefreshed();
            }
        }
    }

    public static void handleWorldOperationResultForServer(String serverId, WorldOperationResult result) {
        for (GraphEditorScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.handleWorldOperationResult(result);
            }
        }
    }

    public static void handleWorldAuditSnapshotForServer(String serverId, JsonElement data) {
        for (GraphEditorScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.getServerId())) {
                screen.showWorldAuditSnapshot(data);
            }
        }
    }

    protected void onOptionCatalogRefreshed() {
        onOptionCatalogRefreshed(null);
    }

    protected void onOptionCatalogRefreshed(String sourceId) {
        for (FlowNodeWidget widget : widgetCache.values()) {
            widget.refreshOptionCatalog(sourceId);
        }
        refreshStudioCatalogDocuments();
    }

    private void onWorldSnapshotRefreshed() {
        refreshStudioWorldDocuments();
    }

    private void handleWorldOperationResult(WorldOperationResult result) {
        if (result == null || !result.isSuccess()) {
            return;
        }
        String action = safeText(result.getAction()).trim().toLowerCase(Locale.ROOT);
        handleStudioWorldOperationResult(result);
        if ("whoworld".equals(action)) {
            showWorldWhoPopup(result);
        }
    }

    public String getFlowId() {
        return graph.getId();
    }

    public void focusContentBranch(String branchPin) {
        FlowNode startNode = CustomContentGraphAdapter.findStartNode(graph);
        String nodeId = startNode != null ? findNodeIdForNode(startNode) : null;
        if (nodeId == null) {
            return;
        }
        FlowNode focusNode = startNode;
        if (branchPin != null && graph.getConnections() != null) {
            for (FlowConnection connection : graph.getConnections()) {
                if (nodeId.equals(connection.getSourceNodeId()) && branchPin.equals(connection.getSourcePin())) {
                    FlowNode target = graph.getNodes().get(connection.getTargetNodeId());
                    if (target != null) {
                        focusNode = target;
                    }
                    break;
                }
            }
        }
        selectedNodeIds.clear();
        String focusNodeId = findNodeIdForNode(focusNode);
        selectedNodeIds.add(focusNodeId);
        focusedNode = widgetCache.get(focusNodeId);
        setZoomLevel(1.0f);
        setPan((float) (width / 2.0 - focusNode.getX()), (float) (height / 2.0 - focusNode.getY()));
    }

    private void applyInitialViewportFitIfReady() {
        String viewportFitKey = initialViewportFitKey();
        if (!initialViewportFitPending || initialViewportFittedKeys.contains(viewportFitKey) || width <= 0 || height <= 0) {
            return;
        }
        if (studioMode && (startupState != StudioStartupState.READY || activeStudioDocument == null)) {
            return;
        }
        if (worldWidgets.isEmpty()) {
            initialViewportFitPending = false;
            initialViewportFittedKeys.add(viewportFitKey);
            zoomLevel = INITIAL_VIEWPORT_MAX_ZOOM;
            targetZoomLevel = INITIAL_VIEWPORT_MAX_ZOOM;
            panX = 0.0F;
            panY = 0.0F;
            targetPanX = 0.0F;
            targetPanY = 0.0F;
            return;
        }
        for (Widget widget : worldWidgets) {
            if (widget instanceof FlowNodeWidget flowNodeWidget && !flowNodeWidget.hasLoadedDefinition()) {
                initialViewportStableFrames = 0;
                initialViewportSignature = 0;
                return;
            }
        }
        int signature = initialViewportSignature();
        if (signature == initialViewportSignature) {
            initialViewportStableFrames++;
        } else {
            initialViewportSignature = signature;
            initialViewportStableFrames = 1;
        }
        if (initialViewportStableFrames < INITIAL_VIEWPORT_STABLE_FRAMES) {
            return;
        }
        fitViewportToWorldWidgets();
        initialViewportFitPending = false;
        initialViewportFittedKeys.add(viewportFitKey);
    }

    private String initialViewportFitKey() {
        if (studioMode && activeStudioDocument != null) {
            return activeStudioDocument.key();
        }
        return graph != null && graph.getId() != null ? graph.getId() : "screen";
    }

    private int initialViewportSignature() {
        int signature = worldWidgets.size();
        for (Widget widget : worldWidgets) {
            signature = 31 * signature + widget.getX();
            signature = 31 * signature + widget.getY();
            signature = 31 * signature + widget.getWidth();
            signature = 31 * signature + widget.getHeight();
        }
        return signature;
    }

    private void fitViewportToWorldWidgets() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Widget widget : worldWidgets) {
            minX = Math.min(minX, widget.getX());
            minY = Math.min(minY, widget.getY());
            maxX = Math.max(maxX, widget.getX() + widget.getWidth());
            maxY = Math.max(maxY, widget.getY() + widget.getHeight());
        }
        if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE || maxX == Integer.MIN_VALUE || maxY == Integer.MIN_VALUE) {
            return;
        }
        int viewportWidth = Math.max(1, viewportFitWidth() - INITIAL_VIEWPORT_PADDING * 2);
        int viewportHeight = Math.max(1, viewportFitHeight() - INITIAL_VIEWPORT_PADDING * 2);
        int contentWidth = Math.max(1, maxX - minX);
        int contentHeight = Math.max(1, maxY - minY);
        float fitZoom = Math.min(viewportWidth / (float) contentWidth, viewportHeight / (float) contentHeight);
        fitZoom = Math.clamp(fitZoom, minZoom, Math.min(maxZoom, INITIAL_VIEWPORT_MAX_ZOOM));
        float centerX = (minX + maxX) / 2.0F;
        float centerY = (minY + maxY) / 2.0F;
        float viewportCenterY = viewportFitTop() + viewportFitHeight() / 2.0F;
        targetZoomLevel = fitZoom;
        targetPanX = viewportFitLeft() + viewportFitWidth() / 2.0F - centerX;
        targetPanY = viewportCenterY - centerY;
        isZoomingToMouse = false;
    }

    private int viewportFitTop() {
        return studioMode ? 30 : 0;
    }

    protected int viewportFitLeft() {
        int left = studioMode ? studioContentBrowserWidth() : 0;
        if (paletteSidePanel != null && paletteSidePanel.isVisible() && paletteSidePanel.isLeftAnchored()) {
            left += paletteSidePanel.getDesiredWidth() + 8;
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible() && studioResourcePanel.isLeftAnchored()) {
            left += studioResourcePanel.getDesiredWidth() + 8;
        }
        return left;
    }

    protected int viewportFitWidth() {
        int right = 0;
        if (paletteSidePanel != null && paletteSidePanel.isVisible() && !paletteSidePanel.isLeftAnchored()) {
            right += paletteSidePanel.getDesiredWidth() + 8;
        }
        if (studioResourcePanel != null && studioResourcePanel.isVisible() && !studioResourcePanel.isLeftAnchored()) {
            right += studioResourcePanel.getDesiredWidth() + 8;
        }
        return Math.max(1, width - viewportFitLeft() - right);
    }

    private int viewportFitHeight() {
        if (!studioMode) {
            return height;
        }
        return Math.max(1, studioEditorHeight() - viewportFitTop());
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
        graph.setFunctionOwner(sourceGraph.getFunctionOwner());
        graph.setFunctionNamespace(sourceGraph.getFunctionNamespace());
        graph.setFunctionVersion(sourceGraph.getFunctionVersion());
        graph.setFunctionDescription(sourceGraph.getFunctionDescription());
        graph.setFunctionInputs(copyFunctionParameters(sourceGraph.getFunctionInputs()));
        graph.setFunctionOutputs(copyFunctionParameters(sourceGraph.getFunctionOutputs()));
        graph.setEditorPassthroughs(copyEditorPassthroughs(sourceGraph.getEditorPassthroughs()));
        selectedNodeIds.clear();
        selectionBase.clear();
        selectedDragStartPositions.clear();
        focusedNode = null;
        dragState.sourceNodeId = null;
        dragState.sourcePin = null;
        dragState.isDragging = false;
        dragState.sourceIsInput = false;
        graphHistory.clear();
        initialViewportFittedKeys.remove(initialViewportFitKey());
        refreshNodeRegistry();
    }

    public String getDesktopAppId() {
        if (studioMode) {
            return "resync-studio:" + serverId;
        }
        return "flow-editor";
    }

    public String getDesktopAppTitle() {
        if (studioMode) {
            String title = !serverTitle.isBlank() ? serverTitle : safeText(serverId);
            return title.isBlank() ? "ReSync Studio" : "ReSync Studio - " + title;
        }
        return "Flow Editor";
    }

    public String getDesktopAppIconPath() {
        return "flow.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return studioMode ? DesktopWindowBehavior.SINGLETON : DesktopWindowBehavior.DEFAULT_REPLACE;
    }

    public void refreshNodeRegistry() {
        for (FlowNodeWidget widget : widgetCache.values()) {
            removeWorldWidget(widget);
        }
        widgetCache.clear();
        for (var entry : graph.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            FlowNodeWidget widget = createNodeWidget(nodeId, entry.getValue());
            addWorldWidget(widget);
            widgetCache.put(entry.getKey(), widget);
        }
        scheduleInitialViewportFit();
        refreshPalette();
    }

    private void scheduleInitialViewportFit() {
        if (initialViewportFittedKeys.contains(initialViewportFitKey())) {
            return;
        }
        initialViewportFitPending = true;
        initialViewportStableFrames = 0;
        initialViewportSignature = 0;
    }

    protected FlowNodeWidget createNodeWidget(String nodeId, FlowNode node) {
        return new FlowNodeWidget((int) node.getX(), (int) node.getY(), node, graph, nodeId, nodeRegistryServerId(), () -> deleteNode(nodeId));
    }

    @Override
    public void init() {
        super.init();
        this.initialWidth = width;
        this.initialHeight = height;

        if (!initialized) {
            header().position(Position.TOP).size(30).visible(true);

            if (studioMode) {
                studioEmptyMessage = new IconMessage(0, 0, 180, 96, "Open Or Create An Asset", "remotely.png");
                studioEmptyMessage.entranceAnimationEnabled = false;
                if (liveStudioWorkspaceRequested) {
                    enterStudioReadyState();
                } else {
                    ensureStartupWidgets();
                    setStartupState(StudioStartupState.LOADING, "Loading...\nDetecting ReSync", "remotely.png", false);
                    beginStartupProbe(true);
                }
            } else {
                if (shouldCreatePaletteSidePanel()) {
                    createPaletteSidePanel();
                }
                createHeaderButtons();
            }
            initialized = true;
        }

        if (!studioMode) {
            syncDebugHeaderVisibility();
            layoutHeaderButtons();
        }
        if (studioMode) {
            if (startupState == StudioStartupState.READY) {
                updateStudioLayout();
            } else {
                updateStartupWidgets();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!studioMode || startupState == StudioStartupState.READY) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && manager.isFlowClientConnected(serverId)) {
            enterStudioReadyState();
            return;
        }
        if ((startupState == StudioStartupState.LOADING || startupState == StudioStartupState.SERVER_STOPPED || startupState == StudioStartupState.INSTALLED) && !startupProbeRunning) {
            beginStartupProbe(false);
        }
    }

    private void ensureStartupWidgets() {
        if (startupIcon == null) {
            startupIcon = new IconMessage(0, 0, width, 120, "Loading", "remotely.png");
            startupIcon.entranceAnimationEnabled = false;
        }
        if (startupCloseButton == null) {
            startupCloseButton = new IconButton.Builder()
                .imagePath("close.png")
                .size(18, 18)
                .hint("Back")
                .entranceAnimation(false)
                .onClick(this::close)
                .build();
        }
        if (setupReSyncButton == null) {
            setupReSyncButton = new IconButton.Builder()
                .label("Setup ReSync")
                .imagePath("ReSync.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(180, 20)
                .autoWidthOnTextChange(true)
                .hint("Setup ReSync")
                .entranceAnimation(false)
                .onClick(this::runReSyncStartupAction)
                .build();
            setupReSyncButton.setVisible(false);
        }
        if (welcomeServerButton == null) {
            welcomeServerButton = new IconButton.Builder()
                .label("Open Server")
                .imagePath("server.png")
                .accentType(ThemeManager.getAccent("nice"))
                .size(180, 20)
                .autoWidthOnTextChange(true)
                .hint("Open Server")
                .entranceAnimation(false)
                .onClick(this::openServerScreen)
                .build();
            welcomeServerButton.setVisible(false);
        }
        updateStartupWidgets();
    }

    private void updateStartupWidgets() {
        int headerHeight = 35;
        int availableHeight = height - headerHeight;
        if (startupIcon != null) {
            int iconY = headerHeight + (availableHeight - startupIcon.getHeight()) / 2;
            startupIcon.setPosition(0, iconY);
            startupIcon.setSize(width, startupIcon.getHeight());
        }
        if (startupCloseButton != null) {
            startupCloseButton.setPosition(width - 25, 8);
        }
        int buttonY = startupIcon != null ? startupIcon.getY() + startupIcon.getHeight() + 10 : Math.max(100, height / 2 + 70);
        if (setupReSyncButton != null) {
            setupReSyncButton.setPosition((width - setupReSyncButton.getWidth()) / 2, buttonY);
        }
        if (welcomeServerButton != null) {
            int offset = setupReSyncButton != null && setupReSyncButton.isVisible() ? 28 : 0;
            welcomeServerButton.setPosition((width - welcomeServerButton.getWidth()) / 2, buttonY + offset);
        }
    }

    private void setStartupState(StudioStartupState state, String message, String iconPath, boolean showSetupButton) {
        startupState = state;
        ensureStartupWidgets();
        if (startupIcon != null) {
            startupIcon.setMessage(message);
            startupIcon.setIcon(iconPath);
            startupIcon.setVisible(true);
        }
        if (setupReSyncButton != null) {
            setupReSyncButton.setMessage("Setup ReSync");
            setupReSyncButton.setHint("Setup ReSync");
            setupReSyncButton.setVisible(showSetupButton && !setupRunning);
        }
        if (welcomeServerButton != null) {
            welcomeServerButton.setVisible(state == StudioStartupState.INSTALLED);
        }
        updateStartupWidgets();
    }

    private void beginStartupProbe(boolean force) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            setStartupState(StudioStartupState.NOT_SUPPORTED, "Not Supported\nReSync Is Missing", "stop.png", false);
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && startupProbeRunning) {
            return;
        }
        if (!force && now - lastStartupProbeAt < 800) {
            return;
        }
        startupProbeRunning = true;
        lastStartupProbeAt = now;
        manager.ensureFlowClientForStartup(serverId, startupServer, false);
        if (manager.isFlowClientConnected(serverId)) {
            startupProbeRunning = false;
            enterStudioReadyState();
            return;
        }
        if (force && startupState != StudioStartupState.INSTALLING && startupState != StudioStartupState.INSTALLED) {
            setStartupState(StudioStartupState.LOADING, "Loading...\nDetecting ReSync", "remotely.png", false);
        }
        CompletableFuture.runAsync(this::probeStartupStateAsync);
    }

    private void probeStartupStateAsync() {
        ReSyncProvisioningService.StartupProbeResult result;
        try {
            result = reSyncProvisioningService.computeStartupState(serverId, startupServer, loaderHint);
        } catch (Exception ignored) {
            result = new ReSyncProvisioningService.StartupProbeResult(ReSyncProvisioningService.StartupStatus.SETUP, false, false);
        }
        ReSyncProvisioningService.StartupProbeResult resolvedResult = result;
        ScreenManager.getInstance().execute(() -> {
            startupProbeRunning = false;
            FlowManager manager = FlowManager.getInstance();
            if (manager != null && manager.isFlowClientConnected(serverId)) {
                enterStudioReadyState();
                return;
            }
            if (startupState == StudioStartupState.INSTALLING) {
                return;
            }
            if (resolvedResult.updateChecked()) {
                updateReSyncAvailability(resolvedResult.updateAvailable());
            }
            StudioStartupState resolvedState = startupStateFor(resolvedResult.status());
            if (startupState == StudioStartupState.INSTALLED && resolvedState != StudioStartupState.READY && resolvedState != StudioStartupState.LOADING) {
                return;
            }
            switch (resolvedState) {
                case READY -> enterStudioReadyState();
                case NOT_SUPPORTED -> setStartupState(StudioStartupState.NOT_SUPPORTED, "ReSync Is Not On This Server\nBukkit-Based Server Required", "close.png", false);
                case SERVER_STOPPED -> setStartupState(StudioStartupState.SERVER_STOPPED, "Server Is Offline\nStart The Server To Use ReSync", "stop.png", false);
                case SETUP -> setStartupState(StudioStartupState.SETUP, "Setup ReSync\nInstall And Configure", "ReSync.png", true);
                default -> setStartupState(StudioStartupState.LOADING, "Loading...\nDetecting ReSync", "remotely.png", false);
            }
        });
    }

    private StudioStartupState startupStateFor(ReSyncProvisioningService.StartupStatus status) {
        if (status == null) {
            return StudioStartupState.SETUP;
        }
        return switch (status) {
            case READY -> StudioStartupState.READY;
            case NOT_SUPPORTED -> StudioStartupState.NOT_SUPPORTED;
            case SETUP -> StudioStartupState.SETUP;
            case LOADING -> StudioStartupState.LOADING;
            case SERVER_STOPPED -> StudioStartupState.SERVER_STOPPED;
        };
    }

    private void updateReSyncAvailability(boolean available) {
        reSyncUpdateAvailable = available;
    }

    private void refreshReSyncUpdateAvailabilityAsync() {
        if (reSyncUpdateProbeRunning || !studioMode || liveStudioWorkspaceRequested) {
            return;
        }
        reSyncUpdateProbeRunning = true;
        CompletableFuture.runAsync(() -> {
            boolean available = false;
            try {
                available = reSyncProvisioningService.isReSyncUpdateAvailable(serverId, startupServer);
            } catch (Exception ignored) {
            }
            boolean resolved = available;
            ScreenManager.getInstance().execute(() -> {
                reSyncUpdateProbeRunning = false;
                updateReSyncAvailability(resolved);
            });
        });
    }

    @Override
    public boolean hasReSyncUpdateAvailable() {
        return reSyncUpdateAvailable;
    }

    @Override
    public boolean isReSyncUpdateRunning() {
        return reSyncUpdateRunning;
    }

    @Override
    public void updateReSyncFromContentBrowser() {
        runUpdateFlow();
    }

    private void enterStudioReadyState() {
        startupState = StudioStartupState.READY;
        startupIcon = null;
        startupCloseButton = null;
        setupReSyncButton = null;
        welcomeServerButton = null;
        if (!studioChromeBuilt) {
            createHeaderButtons();
            createStudioWorkspaceChrome(!liveStudioFullEditorMode);
            studioChromeBuilt = true;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager != null) {
            manager.ensureFlowClientForStartup(serverId, startupServer, true);
            manager.onStudioReady(serverId);
            if (liveStudioFullEditorMode) {
                ScreenManager.getInstance().execute(() -> {
                    ensureStudioWorkspacePanels(true);
                    updateStudioLayout();
                    manager.requestInitialFlowData(serverId);
                    WorldGenManager.getInstance().requestProjectListIfMissing(serverId);
                });
            } else {
                manager.requestInitialFlowData(serverId);
                WorldGenManager.getInstance().requestProjectListIfMissing(serverId);
            }
        }
        refreshReSyncUpdateAvailabilityAsync();
        updateStudioLayout();
    }

    private void runReSyncStartupAction() {
        runSetupFlow();
    }

    private void runSetupFlow() {
        if (setupRunning) {
            return;
        }
        setupRunning = true;
        setStartupState(StudioStartupState.INSTALLING, "Installing ReSync...", "remotely.png", false);
        CompletableFuture.runAsync(this::setupReSyncAsync);
    }

    private void setupReSyncAsync() {
        boolean success;
        boolean needsRestart = false;
        String failureMessage = "";
        try {
            FlowManager manager = FlowManager.getInstance();
            if (manager != null && manager.isFlowClientConnected(serverId)) {
                success = true;
            } else {
                ReSyncProvisioningService.OperationResult result = reSyncProvisioningService.setup(serverId, startupServer);
                success = result.success();
                failureMessage = result.failureMessage();
                needsRestart = success;
            }
        } catch (Exception error) {
            success = false;
            failureMessage = error.getMessage() == null || error.getMessage().isBlank() ? "Setup Failed" : error.getMessage();
        }
        boolean completed = success;
        boolean shouldRestart = needsRestart;
        String reason = failureMessage;
        ScreenManager.getInstance().execute(() -> {
            setupRunning = false;
            if (completed) {
                if (shouldRestart) {
                    showInstalledState();
                } else {
                    beginStartupProbe(true);
                }
                return;
            }
            if (reason != null && !reason.isBlank()) {
                new Notification("ReSync", reason, Notification.Type.ERROR);
            }
            setStartupState(StudioStartupState.SETUP, "Setup ReSync\nInstall And Configure", "ReSync.png", true);
        });
    }

    private void runUpdateFlow() {
        if (setupRunning || reSyncUpdateRunning) {
            return;
        }
        reSyncUpdateRunning = true;
        new Notification("ReSync", "Updating ReSync...", Notification.Type.INFO);
        CompletableFuture.runAsync(this::updateReSyncAsync);
    }

    private void updateReSyncAsync() {
        boolean success;
        String failureMessage = "";
        try {
            ReSyncProvisioningService.OperationResult result = reSyncProvisioningService.update(serverId, startupServer);
            success = result.success();
            failureMessage = result.failureMessage();
        } catch (Exception error) {
            success = false;
            failureMessage = error.getMessage() == null || error.getMessage().isBlank() ? "Update Failed" : error.getMessage();
        }
        boolean completed = success;
        String reason = failureMessage;
        ScreenManager.getInstance().execute(() -> {
            reSyncUpdateRunning = false;
            setupRunning = false;
            if (completed) {
                reSyncProvisioningService.clearReleaseCache();
                updateReSyncAvailability(false);
                if (startupState == StudioStartupState.READY) {
                    showUpdatedNotification();
                } else {
                    new Notification("ReSync", "Updated! Restart Server To Activate", Notification.Type.SUCCESS);
                }
                return;
            }
            if (reason != null && !reason.isBlank()) {
                new Notification("ReSync", reason, Notification.Type.ERROR);
            }
            updateReSyncAvailability(true);
        });
    }

    private void showUpdatedNotification() {
        FlowManager manager = FlowManager.getInstance();
        Instance instance = manager != null ? manager.findInstanceByServerId(serverId, startupServer) : null;
        boolean isRunning = instance != null && instance.getState() == InstanceState.RUNNING;
        new Notification("ReSync", isRunning ? "Updated! Restart Server To Activate" : "Updated! Start Server To Activate", Notification.Type.SUCCESS);
    }

    private void showInstalledState() {
        FlowManager manager = FlowManager.getInstance();
        Instance instance = manager != null ? manager.findInstanceByServerId(serverId, startupServer) : null;
        boolean isRunning = instance != null && instance.getState() == InstanceState.RUNNING;
        boolean isSSH = instance != null && instance.getBackendConfig() != null && "SSH".equalsIgnoreCase(instance.getBackendConfig().type);
        StringBuilder message = new StringBuilder();
        message.append("ReSync Installed!");
        if (isRunning) {
            message.append("\nRestart Your Server To Activate");
        } else {
            message.append("\nStart Your Server To Activate");
        }
        if (isSSH) {
            message.append("\nOpen Port ").append(ReSyncProvisioningService.RESYNC_PORT).append(" On Your Host");
        }
        setStartupState(StudioStartupState.INSTALLED, message.toString(), "ReSync.png", false);
        new Notification("ReSync", isRunning ? "Installed! Restart Server To Activate" : "Installed! Start Server To Activate", Notification.Type.SUCCESS);
    }

    private void openServerScreen() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        Instance instance = manager.findInstanceByServerId(serverId, startupServer);
        if (instance == null && startupServer != null) {
            instance = buildTemporaryInstance(startupServer);
        }
        if (instance == null) {
            return;
        }
        RemotelyClient.INSTANCE.openInstanceInTerminal(this, instance);
    }

    private Instance buildTemporaryInstance(ClientServerView server) {
        Map<String, String> creds = new HashMap<>();
        creds.put("identifier", server.identifier);
        creds.put("host", server.sftpIp);
        creds.put("port", String.valueOf(server.sftpPort));
        creds.put("user", server.sftpUser);
        creds.put("password", "");
        creds.put("installing", String.valueOf(server.isInstalling));
        creds.put("suspended", String.valueOf(server.isSuspended));
        Instance instance = new Instance(server.name, "unknown", "");
        instance.setBackendConfig(new BackendConfig("RESTUDIO", creds));
        instance.setServer(true);
        if (server.loader != null) {
            try {
                instance.setModLoader(ModLoader.valueOf(server.loader));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return instance;
    }

    private void refreshPalette() {
        if (paletteSidePanel == null) {
            return;
        }
        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(nodeRegistryServerId())) {
            populateCategoryPopups();
        } else {
            populateFallbackPopups();
        }
    }

    private void createPaletteSidePanel() {
        paletteStudioPanel = rightStudioPanel("palettePanel")
            .show();
        paletteSidePanel = paletteStudioPanel.sidePanel();
        paletteStudioPanel.padding(studioPanelState.padding());

        categoryPopups.clear();
        categoryOrder = resolveCategoryOrder();
        for (NodeDefinition.NodeCategory category : categoryOrder) {
            PopupWidget popup = new PopupWidget.Builder(getCategoryLabel(category)).enableCollapseOnClose(true).build();
            popup.setTitleBadge(catalogGroupName(category));
            ReSyncStudioPanelState.disableEntrance(popup);
            popup.collapse(true);
            categoryPopups.put(category, popup);
            paletteSidePanel.addWidget(popup);
        }

        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(nodeRegistryServerId())) {
            populateCategoryPopups();
        } else {
            populateFallbackPopups();
        }
    }

    @Override
    protected void beforeStudioDocumentSelection() {
        syncNodePositions();
        saveActiveStudioViewport();
    }

    @Override
    protected void afterStudioDocumentSelected(StudioDocument document) {
        restoreStudioViewport(document.viewport());
        activeNodeRegistryServerId = ReSyncResourceDragPayload.WORLDGEN.equals(document.type()) ? WorldGenManager.registryServerId(serverId) : serverId;
        graph = document.graph() != null ? document.graph() : studioEmptyGraph;
        selectedNodeIds.clear();
        selectionBase.clear();
        selectedDragStartPositions.clear();
        focusedNode = null;
        dragState.isDragging = false;
        pendingSourceNodeId = null;
        pendingSourcePin = null;
        graphHistory.clear();
        if (usesStudioPalette(document) && shouldCreatePaletteSidePanel() && paletteSidePanel == null) {
            createPaletteSidePanel();
        }
        refreshNodeRegistry();
        if (paletteSidePanel != null) {
            if (usesStudioPalette(document) && shouldCreatePaletteSidePanel()) {
                paletteSidePanel.show();
            } else {
                paletteSidePanel.hide();
            }
        }
    }

    private boolean usesStudioPalette(StudioDocument document) {
        return document != null
            && document.view() == null
            && document.graph() != null
            && !ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(document.type())
            && !ReSyncResourceDragPayload.COMMAND.equals(document.type());
    }

    private void saveActiveStudioViewport() {
        if (!studioMode || activeStudioDocument == null || activeStudioDocument.viewport() == null) {
            return;
        }
        StudioViewportState viewport = activeStudioDocument.viewport();
        viewport.zoomLevel = zoomLevel;
        viewport.targetZoomLevel = targetZoomLevel;
        viewport.panX = panX;
        viewport.panY = panY;
        viewport.targetPanX = targetPanX;
        viewport.targetPanY = targetPanY;
    }

    private void restoreStudioViewport(StudioViewportState viewport) {
        if (viewport == null) {
            return;
        }
        zoomLevel = viewport.zoomLevel;
        targetZoomLevel = viewport.targetZoomLevel;
        panX = viewport.panX;
        panY = viewport.panY;
        targetPanX = viewport.targetPanX;
        targetPanY = viewport.targetPanY;
        isZoomingToMouse = false;
    }

    @Override
    protected void beforeClearActiveStudioDocument() {
        saveActiveStudioViewport();
    }

    @Override
    protected void afterActiveStudioDocumentCleared() {
        graph = studioEmptyGraph;
        selectedNodeIds.clear();
        selectionBase.clear();
        selectedDragStartPositions.clear();
        focusedNode = null;
        dragState.isDragging = false;
        pendingSourceNodeId = null;
        pendingSourcePin = null;
        graphHistory.clear();
        activeNodeRegistryServerId = serverId;
        if (paletteSidePanel != null) {
            paletteSidePanel.hide();
        }
    }

    private void populateCategoryPopups() {
        List<NodeDefinition.NodeCategory> order = categoryOrder.isEmpty() ? resolveCategoryOrder() : categoryOrder;
        Map<NodeDefinition.NodeCategory, List<NodeDefinition>> categories = new HashMap<>();
        for (NodeDefinition.NodeCategory category : order) {
            categories.put(category, new ArrayList<>());
        }

        for (NodeDefinition def : NodeRegistry.getInstance().getAllDefinitions(nodeRegistryServerId()).values()) {
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
                        .entranceAnimation(false)
                        .onClick(() -> addNodeAtCenter(def.getId()))
                        .build();
                ReSyncStudioPanelState.disableEntrance(btn);
                targetPopup.addRow("", Collections.singletonList(btn), 20, true, false);
            }
        }
    }

    private List<NodeDefinition.NodeCategory> resolveCategoryOrder() {
        List<FlowCategoryMetadata> meta = NodeRegistry.getInstance().getServerCategories(nodeRegistryServerId());
        List<NodeDefinition.NodeCategory> result = new ArrayList<>();
        for (FlowCategoryMetadata m : meta) {
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

    private FlowCategoryMetadata categoryMetadata(NodeDefinition.NodeCategory category) {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (category == null || registry == null) {
            return null;
        }
        for (FlowCategoryMetadata metadata : registry.getServerCategories(nodeRegistryServerId())) {
            if (metadata != null && category.getId().equalsIgnoreCase(metadata.getId())) {
                return metadata;
            }
        }
        return null;
    }

    private String catalogGroupName(NodeDefinition.NodeCategory category) {
        FlowCategoryMetadata metadata = categoryMetadata(category);
        if (metadata != null && metadata.getGroupName() != null && !metadata.getGroupName().isBlank()) {
            return metadata.getGroupName();
        }
        return switch (category != null ? category.getId() : "") {
            case "logic", "data", "variable", "flow", "function", "utility" -> "Flow";
            case "event", "action", "player", "entity", "block", "world", "inventory", "item", "visual", "world_gen" -> "Minecraft";
            case "command", "network", "chat", "scoreboard", "trade", "npc", "loot", "menu", "tab_list", "dialog", "custom_content", "recipe", "advancement", "text", "permission", "ability" -> "ReSync";
            default -> "Integrations";
        };
    }

    private String catalogGroupBadge(NodeDefinition.NodeCategory category) {
        return switch (catalogGroupName(category)) {
            case "ReSync" -> "Re";
            case "Minecraft" -> "MC";
            case "Integrations" -> "App";
            default -> "Flow";
        };
    }

    FlowDebugController debugController() {
        FlowManager flowManager = FlowManager.getInstance();
        return flowManager != null ? flowManager.getDebugController() : null;
    }

    public void refreshDebugState() {
        FlowDebugController controller = debugController();
        debugMode = controller != null && controller.isEnabled();
        syncDebugHeaderVisibility();
        if (!studioMode) {
            layoutHeaderButtons();
        }
    }

    private boolean isContentEditor() {
        return CustomContentGraphAdapter.isContentGraph(graph);
    }

    private void addNodeAtCenter(String type) {
        double[] center = screenToWorld(width / 2.0, height / 2.0);
        int x = (int) (center[0] - 50);
        int y = (int) (center[1] - 20);
        addNode(x, y, type, null);
    }

    protected void createHeaderButtons() {
        if (shouldShowBackButton()) {
            SquareButtonWidget backButton = headerButton("close.png", "Back", () -> {
                if (liveStudioFullEditorMode) {
                    close();
                } else if (parent != null) {
                    client.setScreen(parent);
                } else {
                    close();
                }
            });
            addHeaderButton(backButton);
        }

        addHeaderButton(headerButton("save.png", "Save", this::onSave));

        addHeaderButton(headerButton("layout.png", "Layout", this::organizeGraph));

        addHeaderButton(headerButton("info.png", "Registry Inspector", this::showRegistryInspector));

        if (graph != null && graph.isFunction()) {
            addHeaderButton(headerButton("start.png", "Test Function", this::showFunctionTestPopup));
        }

        debugToggleButton = headerButton("report.png", "Debug", this::toggleDebugMode);
        addHeaderButton(debugToggleButton);

        debugResumeButton = debugHeaderButton("Continue", "start.png", () -> {
            FlowDebugController debug = debugController();
            if (debug != null) {
                FlowDebugController.DebugSession session = debug.getActiveSession();
                debug.resume(serverId, session != null ? session.sessionId() : "");
            }
        });
        debugStepButton = debugHeaderButton("Step", "goforward.png", this::stepDebug);
        debugStopButton = debugHeaderButton("Stop", "stop.png", () -> {
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
            addHeaderButton(headerButton("copy.png", "Extract Function", this::showExtractFunctionPopup));
        }
        addCustomHeaderButtons();
        syncDebugHeaderVisibility();
    }

    protected boolean shouldShowBackButton() {
        return !desktopMode || shouldForceSuperScreen();
    }

    @Override
    public boolean shouldForceSuperScreen() {
        return liveStudioFullEditorMode;
    }

    private SquareButtonWidget debugHeaderButton(String label, String icon, Runnable action) {
        return headerButton(icon, label, action);
    }

    protected SquareButtonWidget headerButton(String icon, String hint, Runnable action) {
        return new SquareButtonWidget.Builder()
                .size(18, 18)
                .identifier(Identifier.icon(icon))
                .hint(hint)
                .onClick(action)
                .entranceAnimation(false)
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
        notifyStudioHeaderButtonsChanged();
    }

    @Override
    protected void syncDebugHeaderVisibility() {
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

    private void stepDebug() {
        FlowDebugController debug = debugController();
        if (debug == null) {
            return;
        }
        FlowDebugController.DebugSession session = debug.getActiveSession();
        String sessionId = session != null ? session.sessionId() : "";
        debug.stepInto(serverId, sessionId);
    }

    protected void addHeaderButton(AnimatedWidget button) {
        if (button == null) {
            return;
        }
        headerButtons.add(button);
        button.entranceAnimationEnabled = false;
        if (studioMode) {
            button.visible = false;
        } else {
            addDrawableChild(button);
        }
    }

    @Override
    public List<AnimatedWidget> getStudioHeaderButtons() {
        List<AnimatedWidget> source = headerButtons;
        if (ownerScreen instanceof GraphEditorScreen && !headerButtons.isEmpty() && "Back".equals(safeText(headerButtons.getFirst().hint))) {
            source = headerButtons.subList(1, headerButtons.size());
        }
        return source.stream()
            .filter(button -> button != null && shouldExposeStudioHeaderButton(button))
            .map(button -> (AnimatedWidget) button)
            .toList();
    }

    private boolean shouldExposeStudioHeaderButton(AnimatedWidget button) {
        if (button == debugResumeButton || button == debugStepButton || button == debugStopButton) {
            return debugMode;
        }
        return true;
    }

    protected boolean showExtractButton() {
        return true;
    }

    protected boolean shouldCreatePaletteSidePanel() {
        return true;
    }

    protected void addCustomHeaderButtons() {
    }

    private void showRegistryInspector() {
        NodeRegistry registry = NodeRegistry.getInstance();
        NodeRegistry.RegistrySessionMetadata session = registry != null ? registry.getRegistrySessionMetadata(nodeRegistryServerId()) : null;
        NodeRegistryCache.CacheDiagnostic cache = NodeRegistryCache.getInstance().getDiagnostic(nodeRegistryServerId());
        PopupWidget.Builder builder = new PopupWidget.Builder("Registry Inspector")
            .size(460, 320)
            .setResizable(true)
            .setMinSize(380, 260);
        if (registry == null || session == null) {
            addRegistryInspectorRow(builder, "Status", "Registry Unavailable", "No compatible live or cached registry is loaded", "danger");
        } else {
            Map<String, Object> diagnostics = session.diagnostics();
            List<String> capabilities = session.capabilities();
            List<String> plugins = registry.getServerPluginIds(nodeRegistryServerId());
            List<String> catalogProviders = registry.getServerOptionSources(nodeRegistryServerId()).stream()
                .map(source -> source.getDisplayName() + " · " + source.getId())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            Set<String> unresolvedTypes = unresolvedRegistryTypes(registry);
            int missingBindings = diagnosticCollectionSize(diagnostics, "missingHandlers")
                + diagnosticCollectionSize(diagnostics, "missingOperations")
                + diagnosticCollectionSize(diagnostics, "missingCatalogs");
            addRegistryInspectorRow(builder, "Server", session.serverIdentity(), session.serverIdentity(), "calm");
            addRegistryInspectorRow(builder, "Contract", "v" + session.contractVersion() + (session.fullSync() ? " · Full" : " · Delta"), String.join(", ", capabilities), "nice");
            addRegistryInspectorRow(builder, "Checksum", abbreviate(session.checksum(), 18), session.checksum(), "calm");
            addRegistryInspectorRow(builder, "Cache", cache.present() ? "Ready · Schema " + cache.schemaVersion() : "Unavailable", cache.invalidationReason(), cache.present() ? "nice" : "warning");
            addRegistryInspectorRow(builder, "Capabilities", String.valueOf(capabilities.size()), String.join(", ", capabilities), "calm");
            addRegistryInspectorRow(builder, "Definitions", String.valueOf(registry.getAllDefinitions(nodeRegistryServerId()).size()), diagnosticDetails(diagnostics, "definitionParity"), "calm");
            addRegistryInspectorRow(builder, "Types", String.valueOf(registry.getServerDataTypes(nodeRegistryServerId()).size()), diagnosticDetails(diagnostics, "typeInventory"), "calm");
            addRegistryInspectorRow(builder, "Unresolved Types", String.valueOf(unresolvedTypes.size()), String.join(", ", unresolvedTypes), unresolvedTypes.isEmpty() ? "nice" : "danger");
            addRegistryInspectorRow(builder, "Catalog Providers", String.valueOf(catalogProviders.size()), String.join(", ", catalogProviders), "calm");
            addRegistryInspectorRow(builder, "Extensions", String.valueOf(plugins.size()), String.join(", ", plugins), "calm");
            int rejectedDefinitions = diagnosticNumber(diagnostics, "rejectedDefinitions");
            addRegistryInspectorRow(builder, "Rejected Definitions", String.valueOf(rejectedDefinitions), diagnosticDetails(diagnostics, "definitionDiagnostics"), rejectedDefinitions == 0 ? "nice" : "danger");
            addRegistryInspectorRow(builder, "Missing Bindings", String.valueOf(missingBindings), missingBindingDetails(diagnostics), missingBindings == 0 ? "nice" : "danger");
            addRegistryInspectorRow(builder, "Resources", String.valueOf(registry.getResourceMetadata(nodeRegistryServerId()).size()), diagnosticDetails(diagnostics, "resourceInventory"), "calm");
            addRegistryInspectorRow(builder, "Resource Audit", String.valueOf(diagnosticNumber(diagnostics, "resourceAuditCount")), diagnosticDetails(diagnostics, "resourceAudit"), "calm");
            addRegistryInspectorRow(builder, "Conversions", String.valueOf(registry.getServerConversionRules(nodeRegistryServerId()).size()), registry.getServerConversionRules(nodeRegistryServerId()).stream().map(rule -> rule.getSourceTypeId() + " → " + rule.getTargetTypeId()).sorted().toList().toString(), "calm");
        }
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private void addRegistryInspectorRow(PopupWidget.Builder builder, String label, String value, String hint, String accent) {
        AnimatedButton widget = new AnimatedButton.Builder()
            .label(value != null && !value.isBlank() ? value : "None")
            .hint(hint != null && !hint.isBlank() ? hint : "None")
            .active(false)
            .accentType(ThemeManager.getAccent(accent))
            .build();
        builder.addRow(label, true, 20, widget);
    }

    private Set<String> unresolvedRegistryTypes(NodeRegistry registry) {
        Set<String> unresolved = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (NodeDefinition definition : registry.getAllDefinitions(nodeRegistryServerId()).values()) {
            List<NodeDefinition.PinDefinition> pins = new ArrayList<>();
            pins.addAll(definition.getInputs());
            pins.addAll(definition.getOutputs());
            for (NodeDefinition.PinDefinition pin : pins) {
                collectUnresolvedTypeRefs(registry, pin.getTypeRef(), unresolved);
            }
        }
        return unresolved;
    }

    private void collectUnresolvedTypeRefs(NodeRegistry registry, FlowTypeRef typeRef, Set<String> unresolved) {
        if (typeRef == null) {
            return;
        }
        if (!registry.resolveType(nodeRegistryServerId(), typeRef.getTypeId()).isResolved()) {
            unresolved.add(typeRef.getTypeId());
        }
        for (FlowTypeRef argument : typeRef.getArguments()) {
            collectUnresolvedTypeRefs(registry, argument, unresolved);
        }
    }

    private int diagnosticNumber(Map<String, Object> diagnostics, String key) {
        Object value = diagnostics != null ? diagnostics.get(key) : null;
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int diagnosticCollectionSize(Map<String, Object> diagnostics, String key) {
        Object value = diagnostics != null ? diagnostics.get(key) : null;
        return value instanceof Collection<?> collection ? collection.size() : 0;
    }

    private String diagnosticDetails(Map<String, Object> diagnostics, String key) {
        Object value = diagnostics != null ? diagnostics.get(key) : null;
        return value != null ? value.toString() : "None";
    }

    private String missingBindingDetails(Map<String, Object> diagnostics) {
        return "Handlers: " + diagnosticDetails(diagnostics, "missingHandlers")
            + "\nOperations: " + diagnosticDetails(diagnostics, "missingOperations")
            + "\nCatalogs: " + diagnosticDetails(diagnostics, "missingCatalogs");
    }

    private String abbreviate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value != null ? value : "";
        }
        return value.substring(0, maximumLength) + "…";
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

    private void showFunctionTestPopup() {
        if (graph == null || !graph.isFunction()) {
            return;
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("Test Function")
            .size(500, 430)
            .setResizable(true)
            .setMinSize(420, 360);
        TextInputWidget nameInput = new TextInputWidget.Builder().text("Fixture").placeholder("Fixture").size(300, 22).build();
        TextAreaWidget inputsInput = new TextAreaWidget.Builder().text(defaultFunctionInputs()).placeholder("{}").size(340, 72).build();
        TextAreaWidget expectedInput = new TextAreaWidget.Builder().text("{}").placeholder("{}").size(340, 72).build();
        TextAreaWidget contextInput = new TextAreaWidget.Builder().text("{}").placeholder("{}").size(340, 60).build();
        TextInputWidget instantInput = new TextInputWidget.Builder().placeholder("2026-03-29T00:30:00Z").size(300, 22).build();
        TextInputWidget zoneInput = new TextInputWidget.Builder().text("UTC").placeholder("UTC").size(300, 22).build();
        builder.addRow("Name", true, 24, nameInput);
        builder.addRow("Inputs", true, 76, inputsInput);
        builder.addRow("Expected", true, 76, expectedInput);
        builder.addRow("Context", true, 64, contextInput);
        builder.addRow("Clock", true, 24, instantInput);
        builder.addRow("Time Zone", true, 24, zoneInput);
        AnimatedButton runButton = new AnimatedButton.Builder()
            .label("Run")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                try {
                    Map<String, Object> inputs = parseFixtureMap(inputsInput.getText());
                    Map<String, Object> expected = parseFixtureMap(expectedInput.getText());
                    Map<String, Object> context = parseFixtureMap(contextInput.getText());
                    FlowManager manager = FlowManager.getInstance();
                    if (manager == null) {
                        throw new IllegalStateException("Flow Manager Unavailable");
                    }
                    new Notification("Function Test", "Running", Notification.Type.INFO);
                    manager.ensureFlowClient(serverId).requestFunctionTest(graph, nameInput.getText(), inputs, expected, context,
                        instantInput.getText(), zoneInput.getText(), 5000L,
                        result -> ScreenManager.getInstance().execute(() -> showFunctionTestResult(result)));
                } catch (RuntimeException exception) {
                    new Notification("Function Test", exception.getMessage() != null ? exception.getMessage() : "Invalid Fixture", Notification.Type.ERROR);
                }
            })
            .build();
        builder.addRow("", true, 22, runButton);
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private String defaultFunctionInputs() {
        JsonObject values = new JsonObject();
        if (graph.getFunctionInputs() != null) {
            for (FlowGraph.FunctionParameter input : graph.getFunctionInputs()) {
                if (input != null && input.getName() != null && !input.getName().isBlank()) {
                    String value = input.getDefaultValue();
                    values.addProperty(input.getName(), value != null ? value : "");
                }
            }
        }
        return GSON.toJson(values);
    }

    private Map<String, Object> parseFixtureMap(String text) {
        JsonElement parsed = JsonParser.parseString(text != null && !text.isBlank() ? text : "{}");
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Fixture Values Must Be A JSON Object");
        }
        Map<?, ?> raw = GSON.fromJson(parsed, Map.class);
        Map<String, Object> values = new LinkedHashMap<>();
        raw.forEach((key, value) -> values.put(String.valueOf(key), value));
        return values;
    }

    private void showFunctionTestResult(JsonObject response) {
        JsonObject error = response != null && response.has("error") && response.get("error").isJsonObject() ? response.getAsJsonObject("error") : null;
        if (error != null) {
            String message = error.has("message") ? error.get("message").getAsString() : "Function Test Failed";
            new Notification("Function Test", message, Notification.Type.ERROR);
            return;
        }
        JsonObject result = response != null && response.has("result") && response.get("result").isJsonObject() ? response.getAsJsonObject("result") : null;
        if (result == null) {
            new Notification("Function Test", "Invalid Test Result", Notification.Type.ERROR);
            return;
        }
        boolean passed = result.has("passed") && result.get("passed").getAsBoolean();
        PopupWidget.Builder builder = new PopupWidget.Builder(passed ? "Function Test Passed" : "Function Test Failed")
            .size(460, 280)
            .setResizable(true)
            .setMinSize(380, 230);
        addRegistryInspectorRow(builder, "Fixture", jsonText(result, "fixture"), jsonText(result, "fixture"), passed ? "nice" : "danger");
        addRegistryInspectorRow(builder, "Outputs", jsonValue(result, "outputs"), jsonValue(result, "outputs"), "calm");
        addRegistryInspectorRow(builder, "Mismatches", jsonValue(result, "mismatches"), jsonValue(result, "mismatches"), passed ? "nice" : "warning");
        addRegistryInspectorRow(builder, "Failure", jsonValue(result, "failure"), jsonValue(result, "failure"), passed ? "nice" : "danger");
        addRegistryInspectorRow(builder, "Clock", jsonText(result, "clockInstant"), jsonText(result, "zoneId"), "calm");
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
        new Notification("Function Test", passed ? "Passed" : "Failed", passed ? Notification.Type.SUCCESS : Notification.Type.ERROR);
    }

    private String jsonValue(JsonObject root, String key) {
        return root != null && root.has(key) && !root.get(key).isJsonNull() ? root.get(key).toString() : "None";
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
        FlowNodeWidget callWidget = createNodeWidget(callNodeId, callNode);
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
                    builder.input(functionParameterPin(param, NodeDefinition.PinDirection.INPUT));
                }
            }
        }
        if (functionGraph.getFunctionOutputs() != null) {
            for (FlowGraph.FunctionParameter param : functionGraph.getFunctionOutputs()) {
                if (param != null && param.getName() != null && !param.getName().isBlank()) {
                    builder.output(functionParameterPin(param, NodeDefinition.PinDirection.OUTPUT));
                }
            }
        }
        builder.priority(220).color(NodeDefinition.NodeCategory.FUNCTION);
        return builder.build();
    }

    private NodeDefinition.PinDefinition functionParameterPin(FlowGraph.FunctionParameter parameter, NodeDefinition.PinDirection direction) {
        NodeDefinition.PinBuilder builder = new NodeDefinition.PinBuilder(parameter.getName(), NodeDefinition.PinType.DATA, direction, parameter.getType() != null ? parameter.getType() : FlowDataType.ANY)
            .typeRef(parameter.getTypeRef());
        NodeDefinition.WidgetType widget = functionParameterWidget(parameter);
        if (widget != null) {
            builder.widget(widget);
        }
        if (parameter.getOptionsSource() != null && !parameter.getOptionsSource().isBlank()) {
            builder.optionsSource(parameter.getOptionsSource());
        }
        if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isBlank()) {
            builder.defaultValue(parameter.getDefaultValue());
        }
        return builder.build();
    }

    private NodeDefinition.WidgetType functionParameterWidget(FlowGraph.FunctionParameter parameter) {
        String widget = parameter.getWidget();
        if (widget != null && !widget.isBlank()) {
            try {
                return NodeDefinition.WidgetType.valueOf(widget.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return parameter.getOptionsSource() != null && !parameter.getOptionsSource().isBlank() ? NodeDefinition.WidgetType.SEARCHABLE_LIST : null;
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
        if (studioMode) return;
        int padding = 5;
        int totalWidth = 0;

        for (AnimatedWidget button : headerButtons) {
            if (button.visible) {
                totalWidth += button.getWidth();
            }
        }
        long visibleButtons = headerButtons.stream().filter(button -> button.visible).count();
        totalWidth += Math.max(0, (int) visibleButtons - 1) * padding;

        int startY = 16;
        int currentX = width - 10;
        int headerHeight = 18;
        if (headerBackground != null) {
            headerBackground.setWidth(totalWidth + (padding * 2));
            headerBackground.setHeight(30);
            headerBackground.setPosition(width - headerBackground.getWidth() - 10, 10);
            startY = headerBackground.getY();
            currentX = headerBackground.getX() + headerBackground.getWidth() - padding;
            headerHeight = headerBackground.getHeight();
        }
        for (AnimatedWidget button : headerButtons) {
            if (!button.visible) {
                continue;
            }
            currentX -= button.getWidth();
            button.setPosition(currentX, startY + (headerHeight - button.getHeight()) / 2);
            currentX -= padding;
        }
    }

    private void notifyStudioHeaderButtonsChanged() {
        if (ownerScreen instanceof GraphEditorScreen studioParent) {
            studioParent.refreshActiveViewHeaderButtons();
        }
        if (studioMode) {
            refreshActiveViewHeaderButtons();
        }
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        if (paletteStudioPanel != null && paletteSidePanel != null && paletteSidePanel.isVisible()) {
            paletteStudioPanel.layout();
        }
        if (studioResourceStudioPanel != null && studioResourcePanel != null && studioResourcePanel.isVisible()) {
            studioResourceStudioPanel.layout();
        }
        if (!studioMode) {
            syncDebugHeaderVisibility();
            layoutHeaderButtons();
        }
        if (studioMode) {
            updateStudioLayout();
        }
    }

    @Override
    protected void updateStudioLayout() {
        super.updateStudioLayout();
        if (paletteStudioPanel != null && paletteSidePanel != null && paletteSidePanel.isVisible()) {
            paletteStudioPanel.layout();
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
        if (type.startsWith("event:") || isFunctionStartType(type)) {
            return 0;
        }
        if (isFunctionEndType(type)) {
            return 90;
        }
        return 50;
    }

    private boolean isFunctionStartType(String type) {
        return "function_start".equals(type) || "function.start".equals(type) || "function.function_start".equals(type);
    }

    private boolean isFunctionEndType(String type) {
        return "function_end".equals(type) || "function.end".equals(type) || "function.function_end".equals(type);
    }

    public void showNodeInputSelector(List<String> options, String selected, Consumer<String> onSelected, int worldX, int worldY) {
        double[] screen = worldToScreen(worldX, worldY);
        showNodeInputSelectorAtScreen(options, selected, onSelected, (int) screen[0], (int) screen[1]);
    }

    public void showNodeInputSelectorAtScreen(List<String> options, String selected, Consumer<String> onSelected, int screenX, int screenY) {
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
        nodeItemSelector.show(screenX, screenY);
    }

    public void showRichNodeInputSelector(List<OptionCatalogItem> items, String selected, Consumer<String> onSelected, int worldX, int worldY) {
        double[] screen = worldToScreen(worldX, worldY);
        showRichNodeInputSelectorAtScreen(items, selected, onSelected, (int) screen[0], (int) screen[1]);
    }

    public void showRichNodeInputSelectorAtScreen(List<OptionCatalogItem> items, String selected, Consumer<String> onSelected, int screenX, int screenY) {
        closeNodeItemSelector();
        if (items == null || items.isEmpty() || onSelected == null) {
            return;
        }
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(this)
            .size(200, 240)
            .dismissOnSelect(true)
            .onClose(() -> removeNodeItemSelector(selectorRef[0]))
            .build();
        selectorRef[0] = selector;
        String previousGroup = null;
        String selectedLabel = selected;
        for (OptionCatalogItem item : items) {
            if (item == null || item.getValue() == null) {
                continue;
            }
            if (!item.getGroup().isBlank() && !item.getGroup().equals(previousGroup)) {
                selector.addSectionHeader(item.getGroup());
                previousGroup = item.getGroup();
            }
            Object aliases = item.getMetadata().get("aliases");
            String searchTerms = String.join(" ", item.getValue(), item.getLabel(), item.getDescription(), item.getGroup(), aliases != null ? aliases.toString() : "");
            selector.addItem(item.getLabel(), item.getIcon(), item.getDescription(), searchTerms, () -> onSelected.accept(item.getValue()));
            if (item.getValue().equals(selected)) {
                selectedLabel = item.getLabel();
            }
        }
        selector.setSelectedItem(selectedLabel);
        nodeItemSelector = selector;
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(screenX, screenY);
    }

    @Override
    public void close() {
        if (studioMode && liveStudioFullEditorMode) {
            FlowManager manager = FlowManager.getInstance();
            if (manager != null) {
                manager.requestCloseLiveStudioSuperScreen(serverId);
                return;
            }
        }
        saveActiveStudioViewport();
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

        ReSyncStudioView activeView = activeStudioView();
        boolean fullEditorView = activeView instanceof ScreenBackedStudioView screenView && screenView.fullEditor();
        if (!fullEditorView) {
            renderBackground(context, mouseX, mouseY, delta);
        }

        if (studioMode && startupState != StudioStartupState.READY) {
            renderStartupSurface(context, mouseX, mouseY, delta);
            return;
        }

        if (activeView != null) {
            activeView.resize(width, studioEditorHeight());
            activeView.render(context, mouseX, mouseY, delta);
            renderStudioOverlays(context, mouseX, mouseY, delta);
            return;
        }

        if (studioMode && activeStudioDocument == null) {
            renderStudioEmptyMessage(context, mouseX, mouseY);
            renderStudioOverlays(context, mouseX, mouseY, delta);
            return;
        }

        applyInitialViewportFitIfReady();

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

        renderWireCompatibilityPreview(context, worldMouseX, worldMouseY);
        renderSelectionBox(context);
        renderStudioDocumentPreview(context);

        renderStudioOverlays(context, mouseX, mouseY, delta);
    }

    private void renderStartupSurface(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateStartupWidgets();
        if (startupIcon != null) {
            startupIcon.render(context, mouseX, mouseY, delta);
        }
        if (startupCloseButton != null && shouldShowBackButton()) {
            startupCloseButton.render(context, mouseX, mouseY, delta);
        }
        if (setupReSyncButton != null && setupReSyncButton.isVisible()) {
            setupReSyncButton.render(context, mouseX, mouseY, delta);
        }
        if (welcomeServerButton != null && welcomeServerButton.isVisible()) {
            welcomeServerButton.render(context, mouseX, mouseY, delta);
        }
        renderStartupHintOverlays(context);
    }

    private void renderStartupHintOverlays(IDrawContext context) {
        if (startupCloseButton != null && shouldShowBackButton()) {
            startupCloseButton.renderHintOverlay(context);
        }
        if (setupReSyncButton != null && setupReSyncButton.isVisible()) {
            setupReSyncButton.renderHintOverlay(context);
        }
        if (welcomeServerButton != null && welcomeServerButton.isVisible()) {
            welcomeServerButton.renderHintOverlay(context);
        }
    }

    @Override
    protected void renderAdditionalStudioPanels(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (activeStudioDocument != null && activeStudioView() == null && paletteStudioPanel != null) {
            renderStudioPanel(paletteStudioPanel, context, mouseX, mouseY, delta);
        }
    }

    private void renderWires(IDrawContext context) {
        updateConnectionAutoPan();
        if (graph.getConnections() == null) return;

        normalizePassthroughConnections();
        Set<FanoutKey> renderedFanouts = new HashSet<>();
        for (FlowConnection conn : graph.getConnections()) {
            int wireColor = wireColor(conn);
            List<FlowConnection> fanout = fanoutConnections(conn);
            if (fanout.size() > 1) {
                FanoutKey key = new FanoutKey(editorSourceNodeId(conn), editorSourcePin(conn));
                if (renderedFanouts.add(key)) {
                    drawFanoutGroup(context, fanout, wireColor);
                }
                continue;
            }
            drawDirectConnection(context, conn, wireColor);
        }

        if (dragState.isDragging && dragState.sourceNodeId != null) {
            double[] sourcePinWorld = null;
            FlowNodeWidget source = widgetCache.get(dragState.sourceNodeId);
            FlowDataType sourceType = null;
            if (source != null) {
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
                drawWire(context, (float)sourcePinWorld[0], (float)sourcePinWorld[1], (float)mouseWorld[0], (float)mouseWorld[1], dragWireColor);
            }
        }
    }

    private void renderWireCompatibilityPreview(IDrawContext context, int worldMouseX, int worldMouseY) {
        WirePreviewTarget target = findWirePreviewTarget(worldMouseX, worldMouseY);
        if (target == null) {
            return;
        }
        WireCompatibilityPreview preview = describeWireCompatibility(target);
        int textWidth = tr.getWidth(preview.label());
        int boxWidth = textWidth + 12;
        int boxHeight = ITextRenderer.fontHeight + 8;
        int left = Math.clamp((int) dragMouseX + 14, 4, Math.max(4, width - boxWidth - 4));
        int top = Math.clamp((int) dragMouseY + 14, 4, Math.max(4, height - boxHeight - 4));
        int background = (ThemeManager.getColor(ThemeColor.innerBorder) & 0x00FFFFFF) | 0xEE000000;
        context.fill(left, top, left + boxWidth, top + boxHeight, background);
        context.fill(left, top, left + 3, top + boxHeight, preview.color());
        context.drawText(preview.label(), left + 7, top + 4, preview.color(), shadow);
    }

    private WirePreviewTarget findWirePreviewTarget(int worldMouseX, int worldMouseY) {
        if (!dragState.isDragging || dragPinWidget == null) {
            return null;
        }
        boolean targetIsInput = !dragState.sourceIsInput;
        for (int index = worldWidgets.size() - 1; index >= 0; index--) {
            Widget widget = worldWidgets.get(index);
            if (!(widget instanceof FlowNodeWidget targetWidget) || targetWidget == dragPinWidget) {
                continue;
            }
            String pinName = targetWidget.getPinAtPosition(worldMouseX, worldMouseY);
            double[] bounds = pinName != null ? targetWidget.getPinBounds(pinName, targetIsInput) : null;
            if (pinName != null && isInside(worldMouseX, worldMouseY, bounds)) {
                return new WirePreviewTarget(targetWidget, pinName, targetIsInput);
            }
        }
        FuzzyWireTarget fuzzy = findFuzzyWireTarget(worldMouseX, worldMouseY);
        return fuzzy != null ? new WirePreviewTarget(fuzzy.widget(), fuzzy.pinName(), fuzzy.input()) : null;
    }

    private WireCompatibilityPreview describeWireCompatibility(WirePreviewTarget target) {
        FlowNodeWidget sourceWidget = target.input() ? dragPinWidget : target.widget();
        String sourcePin = target.input() ? dragState.sourcePin : target.pinName();
        FlowNodeWidget targetWidget = target.input() ? target.widget() : dragPinWidget;
        String targetPin = target.input() ? target.pinName() : dragState.sourcePin;
        int validColor = ThemeManager.getDefaultAccent().getAccentColor();
        int invalidColor = ThemeManager.getAccent("danger").getAccentColor();
        FlowDataType sourceType = sourceWidget.getPinType(sourcePin, false);
        FlowDataType targetType = targetWidget.getPinType(targetPin, true);
        String sourceLabel = sourceType != null ? sourceType.getDisplayName() : "Unknown";
        String targetLabel = targetType != null ? targetType.getDisplayName() : "Unknown";
        if (!canConnect(sourceWidget, sourcePin, targetWidget, targetPin)) {
            return new WireCompatibilityPreview("Incompatible · " + sourceLabel + " → " + targetLabel, invalidColor);
        }
        FlowTypeRef sourceRef = sourceWidget.getPinTypeRef(sourcePin, false);
        FlowTypeRef targetRef = targetWidget.getPinTypeRef(targetPin, true);
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry != null && sourceRef != null && targetRef != null && registry.canAssignTypes(nodeRegistryServerId(), sourceRef, targetRef)) {
            String inferred = !targetRef.getArguments().isEmpty() ? " · " + targetRef : "";
            return new WireCompatibilityPreview("Direct" + inferred, validColor);
        }
        if (targetType != null && sourceType != null && targetType.isAssignableFrom(sourceType)) {
            return new WireCompatibilityPreview("Direct · " + targetLabel, validColor);
        }
        FlowConversionRule rule = registry != null ? registry.findConversionRule(nodeRegistryServerId(), sourceType, targetType) : null;
        if (rule != null) {
            String quality = rule.isLossy() ? "Lossy" : rule.isSafe() ? "Safe" : "Validated";
            int color = rule.isLossy() ? ThemeManager.getAccent("danger").getAccentColor() : validColor;
            return new WireCompatibilityPreview("Convert · Cost " + rule.getCost() + " · " + quality, color);
        }
        return new WireCompatibilityPreview("Convert · " + sourceLabel + " → " + targetLabel, validColor);
    }

    @Override
    public void mouseMoved(ReMouseEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        updateConnectionDragMouse(undistortedCoords[0], undistortedCoords[1]);
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            activeView.mouseMoved(event.retarget(activeView, mouseX, mouseY));
        }
        super.mouseMoved(event);
    }

    private void updateConnectionDragMouse(double screenX, double screenY) {
        if (!dragState.isDragging) {
            dragMouseX = screenX;
            dragMouseY = screenY;
            return;
        }
        int left = viewportFitLeft();
        int top = viewportFitTop();
        int right = left + viewportFitWidth();
        int bottom = top + viewportFitHeight();
        dragMouseX = Math.clamp(screenX, left, Math.max(left, right));
        dragMouseY = Math.clamp(screenY, top, Math.max(top, bottom));
    }

    private void updateConnectionAutoPan() {
        if (!dragState.isDragging) {
            return;
        }
        double left = viewportFitLeft();
        double top = viewportFitTop();
        double right = left + viewportFitWidth();
        double bottom = top + viewportFitHeight();
        double horizontal = edgePanPressure(dragMouseX, left, right);
        double vertical = edgePanPressure(dragMouseY, top, bottom);
        if (horizontal == 0.0 && vertical == 0.0) {
            return;
        }
        float scale = CONNECTION_PAN_SPEED / Math.max(zoomLevel, 0.1F);
        targetPanX += (float) (horizontal * scale);
        targetPanY += (float) (vertical * scale);
        isZoomingToMouse = false;
    }

    private double edgePanPressure(double position, double start, double end) {
        double edge = Math.min(CONNECTION_PAN_EDGE, Math.max(1.0, (end - start) / 3.0));
        if (position <= start + edge) {
            return Math.clamp((start + edge - position) / edge, 0.0, 1.0);
        }
        if (position >= end - edge) {
            return -Math.clamp((position - end + edge) / edge, 0.0, 1.0);
        }
        return 0.0;
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = mouseButtonCode(event);
        double deltaX = event.deltaX();
        double deltaY = event.deltaY();
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        if (dragState.isDragging) {
            updateConnectionDragMouse(undistortedCoords[0], undistortedCoords[1]);
            return true;
        }
        if (handleNodeItemSelectorMouseDragged(event)) {
            return true;
        }
        if (handlePopupWidgetMouseDragged(event)) {
            return true;
        }
        if (handleStudioWorkspaceMouseDragged(event)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseDragged(event.retarget(paletteSidePanel, mouseX, mouseY, deltaX, deltaY))) {
            return true;
        }

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
            if (Widget.dispatchMouseDragged(widget, event.retarget(widget, wx, wy, deltaX, deltaY))) {
                return true;
            }
        }

        return super.mouseDragged(event);
    }

    private int wireColor(FlowConnection connection) {
        FlowNodeWidget source = widgetCache.get(editorSourceNodeId(connection));
        FlowDataType sourceType = source != null ? source.getPinType(editorSourcePin(connection), false) : null;
        return sourceType != null ? sourceType.getColor() : ThemeManager.getColor(ThemeColor.innerBorder);
    }

    private void drawConnectionRoute(IDrawContext context, FlowConnection connection, int color) {
        List<FlowConnection> fanout = fanoutConnections(connection);
        if (fanout.size() > 1) {
            drawWireSegments(context, fanoutSegments(connection, fanout, true), color);
            return;
        }
        drawDirectConnection(context, connection, color);
    }

    private void drawDirectConnection(IDrawContext context, FlowConnection connection, int color) {
        PinPoint start = sourceOutputPoint(connection);
        PinPoint end = targetInputPoint(connection);
        if (start == null || end == null) {
            return;
        }
        drawWire(context, (float) start.x(), (float) start.y(), (float) end.x(), (float) end.y(), color);
    }

    private void drawFanoutGroup(IDrawContext context, List<FlowConnection> connections, int color) {
        if (connections.isEmpty()) {
            return;
        }
        FlowConnection first = connections.getFirst();
        PinPoint source = sourceOutputPoint(first);
        if (source == null) {
            return;
        }
        List<FlowConnection> sorted = sortedFanoutConnections(connections);
        double branchX = fanoutBranchX(source, sorted);
        double minY = source.y();
        double maxY = source.y();
        for (FlowConnection connection : sorted) {
            PinPoint end = targetInputPoint(connection);
            if (end == null) {
                continue;
            }
            minY = Math.min(minY, end.y());
            maxY = Math.max(maxY, end.y());
        }
        drawWireSegments(context, List.of(
            new WireSegment(source.x(), source.y(), branchX, source.y()),
            new WireSegment(branchX, minY, branchX, maxY)
        ), color);
        for (FlowConnection connection : sorted) {
            PinPoint end = targetInputPoint(connection);
            if (end == null) {
                continue;
            }
            drawWireSegments(context, List.of(new WireSegment(branchX, end.y(), end.x(), end.y())), color);
        }
    }

    private List<WireSegment> fanoutSegments(FlowConnection connection, List<FlowConnection> connections, boolean includeShared) {
        PinPoint source = sourceOutputPoint(connection);
        PinPoint end = targetInputPoint(connection);
        if (source == null || end == null) {
            return List.of();
        }
        List<FlowConnection> sorted = sortedFanoutConnections(connections);
        double branchX = fanoutBranchX(source, sorted);
        List<WireSegment> segments = new ArrayList<>();
        if (includeShared) {
            segments.add(new WireSegment(source.x(), source.y(), branchX, source.y()));
            segments.add(new WireSegment(branchX, source.y(), branchX, end.y()));
        }
        segments.add(new WireSegment(branchX, end.y(), end.x(), end.y()));
        return segments;
    }

    private List<FlowConnection> fanoutConnections(FlowConnection seed) {
        if (!isDataSourceConnection(seed) || graph.getConnections() == null) {
            return List.of();
        }
        String sourceNodeId = editorSourceNodeId(seed);
        String sourcePin = editorSourcePin(seed);
        List<FlowConnection> connections = new ArrayList<>();
        for (FlowConnection connection : graph.getConnections()) {
            if (!sourceNodeId.equals(editorSourceNodeId(connection)) || !sourcePin.equals(editorSourcePin(connection))) {
                continue;
            }
            if (sourceOutputPoint(connection) != null && targetInputPoint(connection) != null) {
                connections.add(connection);
            }
        }
        return sortedFanoutConnections(connections);
    }

    private boolean isDataSourceConnection(FlowConnection connection) {
        FlowNodeWidget source = widgetCache.get(editorSourceNodeId(connection));
        return source != null && source.getPinKind(editorSourcePin(connection), false) == NodeDefinition.PinType.DATA;
    }

    private PinPoint sourceOutputPoint(FlowConnection connection) {
        FlowNodeWidget source = widgetCache.get(editorSourceNodeId(connection));
        return pinPoint(source, editorSourcePin(connection), false);
    }

    private PinPoint targetInputPoint(FlowConnection connection) {
        FlowNodeWidget target = widgetCache.get(connection.getTargetNodeId());
        return pinPoint(target, connection.getTargetPin(), true);
    }

    private PinPoint pinPoint(FlowNodeWidget widget, String pin, boolean input) {
        double[] bounds = widget != null ? widget.getPinBounds(pin, input) : null;
        if (bounds == null) {
            return null;
        }
        return new PinPoint(bounds[0] + bounds[2] / 2, bounds[1] + bounds[3] / 2);
    }

    private List<FlowConnection> sortedFanoutConnections(List<FlowConnection> connections) {
        List<FlowConnection> sorted = new ArrayList<>(connections);
        sorted.sort(Comparator
            .comparingDouble(this::targetPinY)
            .thenComparingDouble(this::targetNodeY)
            .thenComparingDouble(this::targetNodeX)
            .thenComparing(connection -> safeString(connection.getTargetNodeId()))
            .thenComparing(connection -> safeString(connection.getTargetPin())));
        return sorted;
    }

    private double targetPinY(FlowConnection connection) {
        PinPoint point = targetInputPoint(connection);
        return point != null ? point.y() : Double.MAX_VALUE;
    }

    private double targetNodeY(FlowConnection connection) {
        FlowNodeWidget widget = widgetCache.get(connection.getTargetNodeId());
        return widget != null ? widget.getY() : Double.MAX_VALUE;
    }

    private double targetNodeX(FlowConnection connection) {
        FlowNodeWidget widget = widgetCache.get(connection.getTargetNodeId());
        return widget != null ? widget.getX() : Double.MAX_VALUE;
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private double fanoutBranchX(PinPoint source, List<FlowConnection> connections) {
        double minTargetX = Double.MAX_VALUE;
        for (FlowConnection connection : connections) {
            PinPoint target = targetInputPoint(connection);
            if (target != null) {
                minTargetX = Math.min(minTargetX, target.x());
            }
        }
        double targetX = minTargetX == Double.MAX_VALUE ? source.x() + 180 : minTargetX;
        if (targetX > source.x() + WIRE_OUT_OFFSET * 3) {
            return Math.round(source.x() + Math.clamp((targetX - source.x()) * 0.45, WIRE_OUT_OFFSET, 220));
        }
        return Math.round(source.x() + WIRE_OUT_OFFSET);
    }

    private void drawWire(IDrawContext context, float startX, float startY, float endX, float endY, int color) {
        drawWireSegments(context, wireSegments(startX, startY, endX, endY), color);
    }

    private void drawWireSegments(IDrawContext context, List<WireSegment> segments, int color) {
        int alpha = (color >> 24) & 0xFF;
        int borderBase = ThemeManager.getColor(ThemeColor.innerBorder);
        int borderColor = (borderBase & 0x00FFFFFF) | (alpha << 24);

        for (WireSegment segment : segments) {
            drawSegmentBorder(context, (int) segment.x1(), (int) segment.y1(), (int) segment.x2(), (int) segment.y2(), borderColor);
        }
        for (WireSegment segment : segments) {
            drawSegmentFill(context, (int) segment.x1(), (int) segment.y1(), (int) segment.x2(), (int) segment.y2(), color);
        }
    }

    private List<WireSegment> wireSegments(double x1, double y1, double x2, double y2) {
        int startX = Math.round((float) x1);
        int startY = Math.round((float) y1);
        int endX = Math.round((float) x2);
        int endY = Math.round((float) y2);
        int outX = startX + WIRE_OUT_OFFSET;
        int inX = endX - WIRE_OUT_OFFSET;
        int midY = Math.round((startY + endY) / 2f);
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
            drawConnectionRoute(context, conn, color);
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
            color = 0xFFFF4D4D;
            context.fillBorder(widget.getX() - 2, widget.getY() - 2, widget.getX() + widget.getWidth() + 2, widget.getY() + widget.getHeight() + 2, 2, color);
        }
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = mouseButtonCode(event);
        if (studioMode && startupState != StudioStartupState.READY) {
            return handleStartupMouseClicked(event);
        }
        if (handleContextMenuMouseClicked(event)) {
            return true;
        }
        if (handleNodeItemSelectorMouseClicked(event)) {
            return true;
        }
        if (handlePopupWidgetMouseClicked(event)) {
            return true;
        }
        double[] headerCoords = unDistortMouse(mouseX, mouseY);
        if (handleHeaderButtonsClick(event, (int) headerCoords[0], (int) headerCoords[1])) {
            return true;
        }
        if (studioMode && handleStudioWorkspaceMouseClicked(event)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseClicked(event.retarget(paletteSidePanel, mouseX, mouseY))) {
            return true;
        }

        double[] worldMouse = screenToWorld(headerCoords[0], headerCoords[1]);
        int wx = (int)worldMouse[0];
        int wy = (int)worldMouse[1];

        if (handleHeaderButtonsClick(event, (int) headerCoords[0], (int) headerCoords[1])) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (debugMode && toggleBreakpointAt(wx, wy)) {
                return true;
            }
            if (handleRightClick(wx, wy, (int)mouseX, (int)mouseY, event.modifiers().shift() || event.modifiers().control())) {
                return true;
            }
            return true;
        }

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);

            if (widget.handleBottomInputActionClick(wx, wy, button)) {
                setFocusedWidget(null);
                focusedNode = widget;
                bringToFront(widget);
                return true;
            }

            Widget outputWidget = widget.getOutputWidgetAt(wx, wy);
            if (outputWidget != null) {
                widget.setLastScreenMouse((int) headerCoords[0], (int) headerCoords[1]);
                Widget.dispatchMouseClicked(outputWidget, event.retarget(outputWidget, wx, wy));
                setFocusedWidget(null);
                focusedNode = widget;
                bringToFront(widget);
                selectNode(widget, event.modifiers().shift() || event.modifiers().control());
                return true;
            }

            if (widget.isMouseOverPin(wx, wy)) {
                String pinName = widget.getPinAtPosition(wx, wy);
                if (pinName != null) {
                    double[] inputBounds = widget.getPinBounds(pinName, true);
                    if (isInside(wx, wy, inputBounds)) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                            && widget.getPinKind(pinName, true) == NodeDefinition.PinType.DATA) {
                            toggleInputPassthrough(widget, pinName);
                            return true;
                        }
                        dragMouseX = headerCoords[0];
                        dragMouseY = headerCoords[1];
                        startWireDrag(widget, pinName, true);
                        setFocusedWidget(null);
                        return true;
                    }

                    double[] outputBounds = widget.getPinBounds(pinName, false);
                    if (isInside(wx, wy, outputBounds)) {
                        dragMouseX = headerCoords[0];
                        dragMouseY = headerCoords[1];
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
                widget.setLastScreenMouse((int) headerCoords[0], (int) headerCoords[1]);
                Widget.dispatchMouseClicked(inputWidget, event.retarget(inputWidget, wx, wy));
                if (inputWidget instanceof TextInputWidget || inputWidget instanceof TextAreaWidget) {
                    setFocusedWidget(inputWidget);
                } else {
                    setFocusedWidget(null);
                }
                focusedNode = widget;
                bringToFront(widget);
                selectNode(widget, event.modifiers().shift() || event.modifiers().control());
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
                selectNode(widget, event.modifiers().shift() || event.modifiers().control());
                startSelectedNodeMove(widget, button);
                widget.setLastScreenMouse((int) headerCoords[0], (int) headerCoords[1]);
                Widget.dispatchMouseClicked(widget, event.retarget(widget, wx, wy));
                return true;
            }
        }

        focusedNode = null;
        setFocusedWidget(null);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.modifiers().shift() || event.modifiers().control()) {
                startSelection(headerCoords[0], headerCoords[1], event.modifiers().shift() || event.modifiers().control());
                return true;
            }
            clearSelection();
        }
        return super.mouseClicked(event);
    }

    private boolean handleContextMenuMouseClicked(ReMouseEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (!(widget instanceof ContextMenuWidget menu) || !menu.isVisible()) {
                continue;
            }
            boolean overMenu = menu.isMouseOver(mouseX, mouseY);
            boolean handled = Widget.dispatchMouseClicked(menu, event.retarget(menu, mouseX, mouseY));
            hideContextMenu();
            if (handled || overMenu) {
                return true;
            }
            return event.button() != ReMouseButton.RIGHT;
        }
        return false;
    }

    private boolean handleStartupMouseClicked(ReMouseEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (startupCloseButton != null && shouldShowBackButton() && Widget.dispatchMouseClicked(startupCloseButton, event.retarget(startupCloseButton, mouseX, mouseY))) {
            return true;
        }
        if (setupReSyncButton != null && setupReSyncButton.isVisible() && Widget.dispatchMouseClicked(setupReSyncButton, event.retarget(setupReSyncButton, mouseX, mouseY))) {
            return true;
        }
        return welcomeServerButton != null && welcomeServerButton.isVisible() && Widget.dispatchMouseClicked(welcomeServerButton, event.retarget(welcomeServerButton, mouseX, mouseY));
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

    private boolean handleHeaderButtonsClick(ReMouseEvent event, int mouseX, int mouseY) {
        List<AnimatedWidget> buttons = new ArrayList<>();
        if (studioMode) {
            buttons.addAll(header().leftButtons);
            buttons.addAll(header().rightButtons);
        } else {
            buttons.addAll(headerButtons);
        }
        for (AnimatedWidget headerButton : buttons) {
            if (headerButton != null && headerButton.visible && headerButton.isMouseOver(mouseX, mouseY)) {
                return Widget.dispatchMouseClicked(headerButton, event.retarget(headerButton, mouseX, mouseY));
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
        pendingSourceNodeId = null;
        pendingSourcePin = null;
        pendingEditorSourceNodeId = null;
        pendingEditorSourcePin = null;
        pendingSourceIsInput = false;
    }

    protected boolean isConnectionDragging() {
        return dragState.isDragging;
    }

    private void toggleInputPassthrough(FlowNodeWidget widget, String inputPin) {
        String nodeId = findNodeId(widget);
        if (nodeId == null) {
            return;
        }
        captureSnapshot();
        boolean removed = graph.getEditorPassthroughs().removeIf(passthrough ->
            nodeId.equals(passthrough.getNodeId()) && inputPin.equals(passthrough.getInputPin()));
        if (!removed) {
            graph.getEditorPassthroughs().add(new FlowGraph.EditorPassthrough(nodeId, inputPin));
        } else if (graph.getConnections() != null) {
            String outputPin = NodeWidget.passthroughOutputPin(inputPin);
            for (FlowConnection connection : graph.getConnections()) {
                if (nodeId.equals(connection.getEditorSourceNodeId()) && outputPin.equals(connection.getEditorSourcePin())) {
                    connection.setEditorSourceNodeId(null);
                    connection.setEditorSourcePin(null);
                }
            }
        }
        widget.refreshInputWidgets();
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = mouseButtonCode(event);
        if (handleNodeItemSelectorMouseReleased(event)) {
            return true;
        }
        if (handlePopupWidgetMouseReleased(event)) {
            return true;
        }
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        if (dragState.isDragging) {
            updateConnectionDragMouse(undistortedCoords[0], undistortedCoords[1]);
            double[] worldMouse = screenToWorld(dragMouseX, dragMouseY);
            tryCompleteWire(worldMouse[0], worldMouse[1], dragMouseX, dragMouseY);
            dragState.isDragging = false;
            dragState.sourceNodeId = null;
            dragState.sourcePin = null;
            dragState.sourceIsInput = false;
            return true;
        }
        if (handleStudioWorkspaceMouseReleased(event)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.mouseReleased(event.retarget(paletteSidePanel, mouseX, mouseY))) {
            return true;
        }

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
            if (Widget.dispatchMouseReleased(widget, event.retarget(widget, wx, wy))) {
                return true;
            }
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        ReKey key = event.key();
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.keyPressed(event.retarget(nodeItemSelector))) {
            return true;
        }
        if (handlePopupWidgetKeyPressed(event)) {
            return true;
        }
        if (handleStudioWorkspaceKeyPressed(event)) {
            return true;
        }
        if ((event.modifiers().control() || event.modifiers().superKey()) && key == ReKey.S) {
            onSave();
            return true;
        }
        if (isKeyboardInputFocused() && super.keyPressed(event)) {
            return true;
        }
        if (key == ReKey.ESCAPE) {
            close();
            return true;
        }
        if ((key == ReKey.DELETE || key == ReKey.BACKSPACE)
                && !isKeyboardInputFocused()) {
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

        boolean hasControl = event.modifiers().control();

        if (hasControl && !isKeyboardInputFocused()) {
            if (key == ReKey.C) {
                if (!selectedNodeIds.isEmpty()) {
                    copyNodes();
                    return true;
                }
            }
            if (key == ReKey.V) {
                if (!clipboard.nodes.isEmpty()) {
                    captureSnapshot();
                    pasteNodes();
                    return true;
                }
            }
            if (key == ReKey.X) {
                if (!selectedNodeIds.isEmpty()) {
                    cutNodes();
                    return true;
                }
            }
            if (key == ReKey.D) {
                if (!selectedNodeIds.isEmpty()) {
                    captureSnapshot();
                    duplicateNodes();
                    return true;
                }
            }
            if (handleStudioHistoryShortcut(event)) {
                return true;
            }
        }

        return super.keyPressed(event);
    }

    private int mouseButtonCode(ReMouseEvent event) {
        ReMouseButton button = event.button();
        return switch (button) {
            case LEFT -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case RIGHT -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case MIDDLE -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            default -> event.nativeButton();
        };
    }


    protected boolean isKeyboardInputFocused() {
        Widget focusedWidget = getFocusedWidget();
        return focusedWidget instanceof TextInputWidget
                || focusedWidget instanceof TextAreaWidget
                || focusedWidget instanceof ItemSelectorWidget;
    }

    @Override
    public boolean textInput(ReTextInputEvent event) {
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.textInput(event.retarget(nodeItemSelector))) {
            return true;
        }
        if (handlePopupWidgetTextInput(event)) {
            return true;
        }
        return handleStudioWorkspaceTextInput(event) || super.textInput(event);
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
            for (FlowConnection connection : graph.getConnections()) {
                if (nodeId.equals(connection.getEditorSourceNodeId())) {
                    connection.setEditorSourceNodeId(null);
                    connection.setEditorSourcePin(null);
                }
            }
            for (String targetId : affectedTargets) {
                refreshInputWidgets(targetId);
            }
        }
        graph.getEditorPassthroughs().removeIf(passthrough -> nodeId.equals(passthrough.getNodeId()));
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

    private boolean handleRightClick(int wx, int wy, int screenX, int screenY, boolean additiveSelection) {
        FlowConnection hit = findConnectionAt(wx, wy);
        if (hit != null) {
            removeConnection(hit);
            return true;
        }

        FlowNodeWidget widget = findNodeAt(wx, wy);
        if (widget != null) {
            if (disconnectPinAt(widget, wx, wy)) {
                return true;
            }
            if (removeOptionalInputPinAt(widget, wx, wy)) {
                return true;
            }
            selectNode(widget, additiveSelection);
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
        builder.addHeaderButton("add.png", widget::showAddFunctionParameterPopup, "Add Parameter", ThemeManager.getAccent("nice"));
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

    private void startSelection(double screenX, double screenY, boolean additiveSelection) {
        isSelecting = true;
        selectionStartX = screenX;
        selectionStartY = screenY;
        selectionEndX = screenX;
        selectionEndY = screenY;
        selectionAdditive = additiveSelection;
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
        FlowTypeRef sourceTypeRef = sourceWidget.getPinTypeRef(sourcePin, false);
        FlowTypeRef targetTypeRef = targetWidget.getPinTypeRef(targetPin, true);
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

        return isTypeCompatible(sourceTypeRef, targetTypeRef, sourceType, targetType);
    }

    protected boolean isTypeCompatible(FlowTypeRef sourceTypeRef, FlowTypeRef targetTypeRef, FlowDataType sourceType, FlowDataType targetType) {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry != null && sourceTypeRef != null && targetTypeRef != null) {
            if (useStrictTypeCompatibility()) {
                return sourceTypeRef.equals(targetTypeRef);
            }
            if (registry.canAssignTypes(nodeRegistryServerId(), sourceTypeRef, targetTypeRef)) {
                return true;
            }
            if (!sourceTypeRef.getArguments().isEmpty() || !targetTypeRef.getArguments().isEmpty()) {
                return false;
            }
        }
        return isTypeCompatible(sourceType, targetType);
    }

    protected boolean isTypeCompatible(FlowDataType sourceType, FlowDataType targetType) {
        if (sourceType == null || targetType == null) {
            return false;
        }
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry != null) {
            sourceType = registry.resolveType(nodeRegistryServerId(), sourceType.getId());
            targetType = registry.resolveType(nodeRegistryServerId(), targetType.getId());
        }
        if (useStrictTypeCompatibility()) {
            return sourceType.equals(targetType);
        }
        if (sourceType.canConvertTo(targetType)) {
            return true;
        }
        return registry != null && registry.canConvertTypes(nodeRegistryServerId(), sourceType, targetType);
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

        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = worldWidgets.get(i);
            if (widget instanceof FlowNodeWidget targetWidget) {
                if (targetWidget != dragPinWidget) {
                    String targetPin = targetWidget.getPinAtPosition(wx, wy);
                    if (targetPin != null) {
                        double[] inputBounds = targetWidget.getPinBounds(targetPin, true);
                        double[] outputBounds = targetWidget.getPinBounds(targetPin, false);
                        boolean onInput = isInside(wx, wy, inputBounds);
                        boolean onOutput = isInside(wx, wy, outputBounds);

                        if (!dragState.sourceIsInput && onInput && canConnect(dragPinWidget, dragState.sourcePin, targetWidget, targetPin)) {
                            connected = connectWireTarget(targetWidget, targetPin, true);
                            break;
                        }

                        if (dragState.sourceIsInput && onOutput && canConnect(targetWidget, targetPin, dragPinWidget, dragState.sourcePin)) {
                            connected = connectWireTarget(targetWidget, targetPin, false);
                            break;
                        }
                    }
                }
            }
        }

        if (!connected) {
            FuzzyWireTarget target = findFuzzyWireTarget(wx, wy);
            if (target != null) {
                connected = connectWireTarget(target.widget(), target.pinName(), target.input());
            }
        }

        if (!connected && dragPinWidget != null) {
            FlowDataType sourceType = dragPinWidget.getPinType(dragState.sourcePin, dragState.sourceIsInput);
            FlowConnection sourceConnection = !dragState.sourceIsInput ? resolveDragSourceConnection() : null;
            if (sourceType != null) {
                pendingSourceNodeId = dragState.sourceIsInput ? dragState.sourceNodeId : sourceConnection.getSourceNodeId();
                pendingSourcePin = dragState.sourceIsInput ? dragState.sourcePin : sourceConnection.getSourcePin();
                pendingEditorSourceNodeId = dragState.sourceIsInput ? null : sourceConnection.getEditorSourceNodeId();
                pendingEditorSourcePin = dragState.sourceIsInput ? null : sourceConnection.getEditorSourcePin();
                pendingSourceIsInput = dragState.sourceIsInput;
                showAddNodeMenu((int) screenMouseX, (int) screenMouseY, sourceType, dragState.sourceIsInput);
            }
        }
    }

    private boolean connectWireTarget(FlowNodeWidget targetWidget, String targetPin, boolean targetIsInput) {
        String targetNodeId = findNodeId(targetWidget);
        if (targetNodeId == null || graph.getConnections() == null) {
            return false;
        }
        if (targetIsInput) {
            FlowConnection sourceConnection = resolveDragSourceConnection();
            if (sourceConnection == null) {
                return false;
            }
            captureSnapshot();
            FlowConnection newConnection = new FlowConnection(sourceConnection.getSourceNodeId(), sourceConnection.getSourcePin(), targetNodeId, targetPin);
            copyEditorSource(sourceConnection, newConnection);
            removeExistingInputConnection(targetNodeId, targetPin);
            graph.getConnections().add(newConnection);
            refreshInputWidgets(targetNodeId);
        } else {
            FlowConnection sourceConnection = resolveConnectionSource(targetNodeId, targetPin);
            if (sourceConnection == null) {
                return false;
            }
            captureSnapshot();
            FlowConnection newConnection = new FlowConnection(sourceConnection.getSourceNodeId(), sourceConnection.getSourcePin(), dragState.sourceNodeId, dragState.sourcePin);
            copyEditorSource(sourceConnection, newConnection);
            removeExistingInputConnection(dragState.sourceNodeId, dragState.sourcePin);
            graph.getConnections().add(newConnection);
            refreshInputWidgets(dragState.sourceNodeId);
        }
        return true;
    }

    private FuzzyWireTarget findFuzzyWireTarget(int wx, int wy) {
        FuzzyWireTarget best = null;
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = worldWidgets.get(i);
            if (!(widget instanceof FlowNodeWidget targetWidget) || targetWidget == dragPinWidget) {
                continue;
            }
            boolean targetIsInput = !dragState.sourceIsInput;
            boolean nearNode = isNearNode(targetWidget, wx, wy);
            List<String> pins = targetIsInput ? targetWidget.getVisibleInputPins() : targetWidget.getVisibleOutputPins();
            for (String pinName : pins) {
                if (!canFuzzyConnect(targetWidget, pinName, targetIsInput)) {
                    continue;
                }
                double[] bounds = targetWidget.getPinBounds(pinName, targetIsInput);
                if (bounds == null) {
                    continue;
                }
                double pinDistance = screenDistanceToPin(wx, wy, bounds);
                if (!nearNode && pinDistance > FUZZY_WIRE_PIN_RADIUS) {
                    continue;
                }
                double score = pinDistance + fuzzyTypePenalty(targetWidget, pinName, targetIsInput) + screenDistanceToNode(wx, wy, targetWidget) * 0.35;
                if (best == null || score < best.score()) {
                    best = new FuzzyWireTarget(targetWidget, pinName, targetIsInput, score);
                }
            }
        }
        return best;
    }

    private boolean canFuzzyConnect(FlowNodeWidget targetWidget, String targetPin, boolean targetIsInput) {
        if (targetIsInput) {
            return canConnect(dragPinWidget, dragState.sourcePin, targetWidget, targetPin);
        }
        return canConnect(targetWidget, targetPin, dragPinWidget, dragState.sourcePin);
    }

    private double fuzzyTypePenalty(FlowNodeWidget targetWidget, String targetPin, boolean targetIsInput) {
        FlowDataType connectionSourceType = targetIsInput ? dragPinWidget.getPinType(dragState.sourcePin, false) : targetWidget.getPinType(targetPin, false);
        FlowDataType connectionTargetType = targetIsInput ? targetWidget.getPinType(targetPin, true) : dragPinWidget.getPinType(dragState.sourcePin, true);
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry != null && connectionSourceType != null && connectionTargetType != null) {
            connectionSourceType = registry.resolveType(nodeRegistryServerId(), connectionSourceType.getId());
            connectionTargetType = registry.resolveType(nodeRegistryServerId(), connectionTargetType.getId());
        }
        if (connectionSourceType == null || connectionTargetType == null || connectionSourceType.equals(connectionTargetType)) {
            return 0.0;
        }
        if (connectionTargetType.isAssignableFrom(connectionSourceType)) {
            return 12.0;
        }
        if (connectionTargetType == FlowDataType.STRING) {
            return 48.0;
        }
        return 28.0;
    }

    private boolean isNearNode(FlowNodeWidget widget, int wx, int wy) {
        double margin = FUZZY_WIRE_NODE_MARGIN / Math.max(zoomLevel, 0.1f);
        return wx >= widget.getX() - margin
            && wx <= widget.getX() + widget.getWidth() + margin
            && wy >= widget.getY() - margin
            && wy <= widget.getY() + widget.getHeight() + margin;
    }

    private double screenDistanceToPin(int wx, int wy, double[] bounds) {
        double centerX = bounds[0] + bounds[2] / 2.0;
        double centerY = bounds[1] + bounds[3] / 2.0;
        return Math.hypot(centerX - wx, centerY - wy) * Math.max(zoomLevel, 0.1f);
    }

    private double screenDistanceToNode(int wx, int wy, FlowNodeWidget widget) {
        double dx = 0.0;
        if (wx < widget.getX()) {
            dx = widget.getX() - wx;
        } else if (wx > widget.getX() + widget.getWidth()) {
            dx = wx - (widget.getX() + widget.getWidth());
        }
        double dy = 0.0;
        if (wy < widget.getY()) {
            dy = widget.getY() - wy;
        } else if (wy > widget.getY() + widget.getHeight()) {
            dy = wy - (widget.getY() + widget.getHeight());
        }
        return Math.hypot(dx, dy) * Math.max(zoomLevel, 0.1f);
    }

    private FlowConnection resolveDragSourceConnection() {
        return resolveConnectionSource(dragState.sourceNodeId, dragState.sourcePin);
    }

    private FlowConnection resolveConnectionSource(String sourceNodeId, String sourcePin) {
        return resolveConnectionSource(sourceNodeId, sourcePin, new HashSet<>());
    }

    private FlowConnection resolveConnectionSource(String sourceNodeId, String sourcePin, Set<String> visited) {
        FlowConnection resolved = new FlowConnection(sourceNodeId, sourcePin, "", "");
        if (!NodeWidget.isPassthroughOutputPin(sourcePin)) {
            return resolved;
        }
        String routeKey = sourceNodeId + ":" + sourcePin;
        if (!visited.add(routeKey)) {
            return resolved;
        }
        FlowConnection incoming = findIncomingConnection(sourceNodeId, NodeWidget.passthroughInputPin(sourcePin));
        if (incoming == null) {
            return resolved;
        }
        String incomingNodeId = incoming.getEditorSourceNodeId() != null && !incoming.getEditorSourceNodeId().isBlank()
            ? incoming.getEditorSourceNodeId()
            : incoming.getSourceNodeId();
        String incomingPin = incoming.getEditorSourcePin() != null && !incoming.getEditorSourcePin().isBlank()
            ? incoming.getEditorSourcePin()
            : incoming.getSourcePin();
        FlowConnection runtimeSource = resolveConnectionSource(incomingNodeId, incomingPin, visited);
        resolved.setSourceNodeId(runtimeSource.getSourceNodeId());
        resolved.setSourcePin(runtimeSource.getSourcePin());
        resolved.setEditorSourceNodeId(sourceNodeId);
        resolved.setEditorSourcePin(sourcePin);
        return resolved;
    }

    private void copyEditorSource(FlowConnection source, FlowConnection target) {
        target.setEditorSourceNodeId(source.getEditorSourceNodeId());
        target.setEditorSourcePin(source.getEditorSourcePin());
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

    protected void closeNodeItemSelector() {
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

    private boolean handleNodeItemSelectorMouseClicked(ReMouseEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        ItemSelectorWidget selector = nodeItemSelector;
        if (selector == null || !selector.visible) {
            return false;
        }
        if (Widget.dispatchMouseClicked(selector, event.retarget(selector, mouseX, mouseY))) {
            return true;
        }
        if (selector.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        removeNodeItemSelector(selector);
        return false;
    }

    private boolean handleNodeItemSelectorMouseReleased(ReMouseEvent event) {
        ItemSelectorWidget selector = nodeItemSelector;
        return selector != null && selector.visible && Widget.dispatchMouseReleased(selector, event.retarget(selector, event.x(), event.y()));
    }

    private boolean handleNodeItemSelectorMouseDragged(ReMouseEvent event) {
        ItemSelectorWidget selector = nodeItemSelector;
        return selector != null && selector.visible && Widget.dispatchMouseDragged(selector, event.retarget(selector, event.x(), event.y(), event.deltaX(), event.deltaY()));
    }

    private void removeExistingInputConnection(String nodeId, String pinName) {
        if (graph.getConnections() == null) return;

        graph.getConnections().removeIf(conn -> conn.getTargetNodeId().equals(nodeId) && conn.getTargetPin().equals(pinName));
    }

    private String findNodeId(FlowNodeWidget widget) {
        for (Map.Entry<String, FlowNodeWidget> entry : widgetCache.entrySet()) {
            if (entry.getValue() == widget) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String findNodeIdForNode(FlowNode node) {
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            if (entry.getValue() == node) {
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
                .usageScope("flow_nodes")
                .onClose(() -> removeNodeItemSelector(selectorRef[0]))
                .beginBatch();

        populateNodeSelector(builder, sourceType, sourceIsInput, worldX, worldY, false);
        builder.endBatch();

        nodeItemSelector = builder.build();
        selectorRef[0] = nodeItemSelector;
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(x, y);
    }

    private String findCompatiblePin(NodeDefinition definition, FlowDataType sourceType, boolean sourceIsInput) {
        List<NodeDefinition.PinDefinition> pins = sourceIsInput ? definition.getOutputs() : definition.getInputs();
        String bestPin = null;
        int bestScore = Integer.MAX_VALUE;
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
            if (pin.getType() == NodeDefinition.PinType.DATA) {
                int score = smartDropCompatibilityScore(sourceType, pin.getDataType(), sourceIsInput);
                if (score < bestScore) {
                    bestScore = score;
                    bestPin = pin.getName();
                    if (score == 0) {
                        break;
                    }
                }
            }
        }
        return bestPin;
    }

    private int smartDropCompatibilityScore(FlowDataType sourceType, FlowDataType pinType, boolean sourceIsInput) {
        if (sourceType == null || pinType == null) {
            return Integer.MAX_VALUE;
        }
        if (sourceType.equals(pinType)) {
            return 0;
        }
        FlowDataType connectionSourceType = sourceIsInput ? pinType : sourceType;
        FlowDataType connectionTargetType = sourceIsInput ? sourceType : pinType;
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry != null) {
            connectionSourceType = registry.resolveType(nodeRegistryServerId(), connectionSourceType.getId());
            connectionTargetType = registry.resolveType(nodeRegistryServerId(), connectionTargetType.getId());
        }
        if (!isTypeCompatible(connectionSourceType, connectionTargetType)) {
            return Integer.MAX_VALUE;
        }
        if (connectionSourceType == FlowDataType.ANY || connectionTargetType == FlowDataType.ANY) {
            return 2;
        }
        if (connectionTargetType.isAssignableFrom(connectionSourceType)) {
            return 1;
        }
        if (connectionTargetType == FlowDataType.STRING) {
            return 4;
        }
        return 3;
    }

    private int smartDropCompatibilityScore(NodeDefinition definition, FlowDataType sourceType, boolean sourceIsInput) {
        String pinName = findCompatiblePin(definition, sourceType, sourceIsInput);
        if (pinName == null) {
            return Integer.MAX_VALUE;
        }
        if (sourceType == FlowDataType.EXECUTION) {
            return 0;
        }
        NodeDefinition.PinDefinition pin = findPin(definition, pinName);
        return pin != null ? smartDropCompatibilityScore(sourceType, pin.getDataType(), sourceIsInput) : Integer.MAX_VALUE;
    }

    private String smartDropSectionLabel(int compatibilityScore) {
        return switch (compatibilityScore) {
            case 0 -> "Exact Type";
            case 1 -> "Direct Type";
            case 2 -> "Any Type";
            case 3 -> "Converted Type";
            default -> "String Conversion";
        };
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
                .usageScope("flow_nodes")
                .onClose(() -> removeNodeItemSelector(selectorRef[0]))
                .beginBatch();

        populateNodeSelector(builder, null, false, 0, 0, true);
        builder.endBatch();

        nodeItemSelector = builder.build();
        selectorRef[0] = nodeItemSelector;
        addDrawableChild(nodeItemSelector);
        nodeItemSelector.show(screenX, screenY);
    }

    private void populateNodeSelector(ItemSelectorWidget.Builder builder, FlowDataType sourceType, boolean sourceIsInput, int worldX, int worldY, boolean atCenter) {
        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(nodeRegistryServerId())) {
            List<NodeDefinition> definitions = new ArrayList<>(NodeRegistry.getInstance().getAllDefinitions(nodeRegistryServerId()).values());
            definitions.removeIf(NodeDefinition::isHidden);
            Comparator<NodeDefinition> catalogOrder = Comparator.comparingInt(NodeDefinition::getPriority).thenComparing(NodeDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER);
            if (sourceType != null) {
                definitions.removeIf(definition -> smartDropCompatibilityScore(definition, sourceType, sourceIsInput) == Integer.MAX_VALUE);
                definitions.sort(Comparator.comparingInt((NodeDefinition definition) -> smartDropCompatibilityScore(definition, sourceType, sourceIsInput)).thenComparing(catalogOrder));
                int typeColumnWidth = selectorVariantTypeColumnWidth(List.of(definitions), sourceType, sourceIsInput);
                int displayedCompatibilityScore = Integer.MIN_VALUE;
                for (NodeDefinition definition : definitions) {
                    int compatibilityScore = smartDropCompatibilityScore(definition, sourceType, sourceIsInput);
                    if (compatibilityScore != displayedCompatibilityScore) {
                        builder.addSectionHeader(smartDropSectionLabel(compatibilityScore));
                        displayedCompatibilityScore = compatibilityScore;
                    }
                    String pinName = findCompatiblePin(definition, sourceType, sourceIsInput);
                    addSelectorItem(builder, definition, compatibilityScore, () -> {
                        captureSnapshot();
                        addNode(worldX, worldY, definition.getId(), pinName);
                    });
                    addSelectorVariantItems(builder, definition, worldX, worldY, pinName, typeColumnWidth, compatibilityScore);
                }
                return;
            }
            definitions.sort(catalogOrder);

            Map<NodeDefinition.NodeCategory, List<NodeDefinition>> categories = new LinkedHashMap<>();
            List<NodeDefinition.NodeCategory> order = categoryOrder.isEmpty() ? resolveCategoryOrder() : categoryOrder;
            for (NodeDefinition.NodeCategory category : order) {
                categories.put(category, new ArrayList<>());
            }
            for (NodeDefinition def : definitions) {
                categories.computeIfAbsent(selectorCategory(def), ignored -> new ArrayList<>()).add(def);
            }

            int typeColumnWidth = selectorVariantTypeColumnWidth(categories.values(), sourceType, sourceIsInput);
            String displayedGroup = null;
            for (Map.Entry<NodeDefinition.NodeCategory, List<NodeDefinition>> entry : categories.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                String group = catalogGroupName(entry.getKey());
                if (!group.equals(displayedGroup)) {
                    builder.addSectionHeader(group);
                    displayedGroup = group;
                }
                builder.addSectionHeader(getCategoryLabel(entry.getKey()));
                for (NodeDefinition def : entry.getValue()) {
                    if (atCenter) {
                        addSelectorItem(builder, def, () -> {
                            captureSnapshot();
                            addNodeAtCenter(def.getId());
                        });
                        addSelectorVariantItemsAtCenter(builder, def, typeColumnWidth);
                    } else {
                        addSelectorItem(builder, def, () -> {
                            captureSnapshot();
                            addNode(worldX, worldY, def.getId(), null);
                        });
                        addSelectorVariantItems(builder, def, worldX, worldY, null, typeColumnWidth);
                    }
                }
            }
        }
    }

    private NodeDefinition.NodeCategory selectorCategory(NodeDefinition definition) {
        String id = definition.getId();
        if (id != null && id.startsWith("event:")) {
            return NodeDefinition.NodeCategory.EVENT;
        }
        NodeDefinition.NodeCategory category = definition.getCategory();
        return category != null ? category : NodeDefinition.NodeCategory.UTILITY;
    }

    private String selectorLabel(NodeDefinition definition) {
        return definition.getDisplayName();
    }

    private void addSelectorItem(ItemSelectorWidget.Builder builder, NodeDefinition definition, Runnable action) {
        addSelectorItem(builder, definition, 0, action);
    }

    private void addSelectorItem(ItemSelectorWidget.Builder builder, NodeDefinition definition, int rankingPriority, Runnable action) {
        NodeDefinition.NodeCategory category = selectorCategory(definition);
        builder.addBadgedItem(selectorLabel(definition), catalogGroupName(category),
            selectorHint(definition), selectorSearchTerms(definition), rankingPriority, action);
    }

    private int selectorVariantTypeColumnWidth(Collection<List<NodeDefinition>> groups, FlowDataType sourceType, boolean sourceIsInput) {
        int width = 0;
        for (List<NodeDefinition> definitions : groups) {
            for (NodeDefinition definition : definitions) {
                String autoWirePin = sourceType != null ? findCompatiblePin(definition, sourceType, sourceIsInput) : null;
                if (sourceType != null && autoWirePin == null) {
                    continue;
                }
                for (NodeSelectorVariant variant : selectorVariants(definition)) {
                    if (autoWirePin != null && !variantExposesPin(definition, variant, autoWirePin)) {
                        continue;
                    }
                    width = Math.max(width, FamilyVariantSelectorEntry.variantTypeWidth(selectorVariantDataType(definition, variant, autoWirePin)));
                }
            }
        }
        return width;
    }

    private void addSelectorVariantItems(ItemSelectorWidget.Builder builder, NodeDefinition definition, int x, int y, String autoWirePin, int typeColumnWidth) {
        addSelectorVariantItems(builder, definition, x, y, autoWirePin, typeColumnWidth, 0);
    }

    private void addSelectorVariantItems(ItemSelectorWidget.Builder builder, NodeDefinition definition, int x, int y, String autoWirePin, int typeColumnWidth, int rankingPriority) {
        for (NodeSelectorVariant variant : selectorVariants(definition)) {
            if (autoWirePin != null && !variantExposesPin(definition, variant, autoWirePin)) {
                continue;
            }
            addSelectorVariantItem(builder, definition, variant, autoWirePin, typeColumnWidth, rankingPriority, () -> {
                captureSnapshot();
                addNode(x, y, definition.getId(), autoWirePin, Map.of(variant.selectorPin().getName(), variant.option()));
            });
        }
    }

    private void addSelectorVariantItemsAtCenter(ItemSelectorWidget.Builder builder, NodeDefinition definition, int typeColumnWidth) {
        for (NodeSelectorVariant variant : selectorVariants(definition)) {
            addSelectorVariantItem(builder, definition, variant, null, typeColumnWidth, 0, () -> {
                captureSnapshot();
                addNodeAtCenter(definition.getId(), Map.of(variant.selectorPin().getName(), variant.option()));
            });
        }
    }

    private void addSelectorVariantItem(ItemSelectorWidget.Builder builder, NodeDefinition definition, NodeSelectorVariant variant, String autoWirePin, int typeColumnWidth, int rankingPriority, Runnable action) {
        FamilyVariantSelectorEntry entry = new FamilyVariantSelectorEntry(
            definition.getDisplayName(),
            selectorVariantLabel(definition, variant),
            selectorVariantDataType(definition, variant, autoWirePin),
            typeColumnWidth,
            () -> {
                closeNodeItemSelector();
                if (action != null) {
                    action.run();
                }
            }
        );
        entry.hint = selectorVariantHint(definition, variant);
        builder.addCustomEntry(entry, selectorVariantSearchTerms(definition, variant), rankingPriority);
    }

    private List<NodeSelectorVariant> selectorVariants(NodeDefinition definition) {
        List<NodeSelectorVariant> variants = new ArrayList<>();
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (!isModeSelectorPin(input)) {
                continue;
            }
            for (String option : input.getOptions()) {
                if (option != null && !option.isBlank()) {
                    variants.add(new NodeSelectorVariant(input, option));
                }
            }
        }
        return variants;
    }

    private boolean isModeSelectorPin(NodeDefinition.PinDefinition input) {
        return input.getDirection() == NodeDefinition.PinDirection.INPUT
            && input.getType() == NodeDefinition.PinType.DATA
            && (input.getName().equalsIgnoreCase("mode") || input.getName().equalsIgnoreCase("action"))
            && input.getOptions() != null
            && !input.getOptions().isEmpty();
    }

    private boolean variantExposesPin(NodeDefinition definition, NodeSelectorVariant variant, String pinName) {
        NodeDefinition.PinDefinition pin = findPin(definition, pinName);
        if (pin == null || pin.getVisibleWhen() == null || pin.getVisibleWhen().isEmpty()) {
            return true;
        }
        String expected = pin.getVisibleWhen().get(variant.selectorPin().getName());
        if (expected == null || expected.isBlank()) {
            return true;
        }
        for (String option : expected.split(",")) {
            if (option.trim().equalsIgnoreCase(variant.option())) {
                return true;
            }
        }
        return false;
    }

    private NodeDefinition.PinDefinition findPin(NodeDefinition definition, String pinName) {
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (input.getName().equals(pinName)) {
                return input;
            }
        }
        for (NodeDefinition.PinDefinition output : definition.getOutputs()) {
            if (output.getName().equals(pinName)) {
                return output;
            }
        }
        return null;
    }

    private FlowDataType selectorVariantDataType(NodeDefinition definition, NodeSelectorVariant variant, String autoWirePin) {
        if (autoWirePin != null) {
            NodeDefinition.PinDefinition pin = findPin(definition, autoWirePin);
            if (pin != null) {
                return selectorPinDataType(pin);
            }
        }
        NodeDefinition.PinDefinition output = firstVariantDataPin(definition.getOutputs(), variant);
        if (output != null) {
            return selectorPinDataType(output);
        }
        NodeDefinition.PinDefinition input = firstVariantDataPin(definition.getInputs(), variant);
        if (input != null) {
            return selectorPinDataType(input);
        }
        return selectorPinDataType(variant.selectorPin());
    }

    private NodeDefinition.PinDefinition firstVariantDataPin(List<NodeDefinition.PinDefinition> pins, NodeSelectorVariant variant) {
        for (NodeDefinition.PinDefinition pin : pins) {
            if (pin == variant.selectorPin() || pin.getType() != NodeDefinition.PinType.DATA || !variantExposesPin(pin, variant)) {
                continue;
            }
            return pin;
        }
        return null;
    }

    private boolean variantExposesPin(NodeDefinition.PinDefinition pin, NodeSelectorVariant variant) {
        if (pin.getVisibleWhen() == null || pin.getVisibleWhen().isEmpty()) {
            return true;
        }
        String expected = pin.getVisibleWhen().get(variant.selectorPin().getName());
        if (expected == null || expected.isBlank()) {
            return true;
        }
        for (String option : expected.split(",")) {
            if (option.trim().equalsIgnoreCase(variant.option())) {
                return true;
            }
        }
        return false;
    }

    private FlowDataType selectorPinDataType(NodeDefinition.PinDefinition pin) {
        if (pin == null) {
            return FlowDataType.ANY;
        }
        if (pin.getType() == NodeDefinition.PinType.FLOW) {
            return FlowDataType.EXECUTION;
        }
        FlowDataType type = pin.getDataType();
        return type != null ? type : FlowDataType.ANY;
    }

    private String selectorVariantLabel(NodeDefinition definition, NodeSelectorVariant variant) {
        return formatSelectorOption(variant.option()) + " " + formatSelectorModeName(variant.selectorPin().getName());
    }

    private String selectorVariantHint(NodeDefinition definition, NodeSelectorVariant variant) {
        String modeText = formatSelectorOption(variant.option()) + " " + formatSelectorModeName(variant.selectorPin().getName());
        String baseHint = selectorHint(definition);
        if (baseHint.isBlank()) {
            return "Adds " + definition.getDisplayName() + " In " + modeText;
        }
        return "Adds " + definition.getDisplayName() + " In " + modeText + "\n" + baseHint;
    }

    private String selectorVariantSearchTerms(NodeDefinition definition, NodeSelectorVariant variant) {
        StringBuilder label = new StringBuilder(selectorSearchTerms(definition));
        label.append(" ")
            .append(definition.getDisplayName())
            .append(" ")
            .append(variant.selectorPin().getName())
            .append(" ")
            .append(variant.option())
            .append(" ")
            .append(formatSelectorOption(variant.option()))
            .append(" ")
            .append(formatSelectorModeName(variant.selectorPin().getName()));
        if (definition.getCategory() != null) {
            label.append(" ").append(definition.getCategory().getDisplayName());
            label.append(" ").append(catalogGroupName(definition.getCategory()));
            label.append(" ").append(catalogGroupBadge(definition.getCategory()));
        }
        return label.toString();
    }

    private String formatSelectorModeName(String name) {
        return "action".equalsIgnoreCase(name) ? "Action" : "Mode";
    }

    private String formatSelectorOption(String option) {
        if (option == null || option.isBlank()) {
            return "";
        }
        String[] parts = option.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(part.substring(0, 1).toUpperCase());
            if (part.length() > 1) {
                result.append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    private String selectorHint(NodeDefinition definition) {
        StringBuilder hint = new StringBuilder();
        if (definition.getDescription() != null && !definition.getDescription().isBlank()) {
            hint.append(definition.getDescription());
        }
        if (!"builtin".equalsIgnoreCase(definition.getOwner())) {
            if (!hint.isEmpty()) {
                hint.append("\n");
            }
            hint.append("Extension: ").append(definition.getOwner());
        }
        if (definition.isDeprecated()) {
            if (!hint.isEmpty()) {
                hint.append("\n");
            }
            hint.append("Deprecated");
            if (definition.getReplacementFor() != null && !definition.getReplacementFor().isBlank()) {
                hint.append(": Use ").append(definition.getReplacementFor());
            }
        }
        if (definition.isDestructive()) {
            if (!hint.isEmpty()) {
                hint.append("\n");
            }
            hint.append("Destructive · ").append(formatSelectorOption(definition.getConfirmationPolicy()));
        }
        if (!definition.getClockDomain().isBlank()) {
            if (!hint.isEmpty()) {
                hint.append("\n");
            }
            hint.append("Clock: ").append(formatSelectorOption(definition.getClockDomain().replace(",", "_and_")));
        }
        return hint.toString();
    }

    private String selectorSearchTerms(NodeDefinition definition) {
        StringBuilder label = new StringBuilder();
        if (definition.getDescription() != null && !definition.getDescription().isBlank()) {
            label.append(definition.getDescription());
        }
        appendSearchTerms(label, definition.getAliases());
        appendSearchTerms(label, definition.getTags());
        appendSearchTerms(label, definition.getExamples());
        label.append(" ").append(definition.getOwner());
        label.append(" ").append(definition.getClockDomain());
        for (NodeDefinition.PinDefinition pin : definition.getInputs()) {
            label.append(" ").append(pin.getName());
        }
        for (NodeDefinition.PinDefinition pin : definition.getOutputs()) {
            label.append(" ").append(pin.getName());
        }
        if (definition.getCategory() != null) {
            label.append(" ").append(definition.getCategory().getDisplayName());
        }
        return label.toString();
    }

    private void appendSearchTerms(StringBuilder label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            label.append(" - ").append(String.join(" ", values));
        }
    }

    private String addNode(int x, int y, String type, String autoWirePin) {
        return addNode(x, y, type, autoWirePin, Map.of());
    }

    private String addNodeAtCenter(String type, Map<String, Object> inputValues) {
        double[] center = screenToWorld(width / 2.0, height / 2.0);
        int x = (int) (center[0] - 50);
        int y = (int) (center[1] - 20);
        return addNode(x, y, type, null, inputValues);
    }

    private String addNode(int x, int y, String type, String autoWirePin, Map<String, Object> inputValues) {
        NodeDefinition definition = NodeRegistry.getInstance() != null ? NodeRegistry.getInstance().getDefinition(nodeRegistryServerId(), type) : null;
        if (definition != null && definition.isDestructive()) {
            new Notification("Destructive Node", formatSelectorOption(definition.getConfirmationPolicy()), Notification.Type.WARN);
        }
        String id = UUID.randomUUID().toString();
        FlowNode node = new FlowNode(type, x, y, new HashMap<>(inputValues));
        graph.getNodes().put(id, node);

        FlowNodeWidget widget = createNodeWidget(id, node);
        widgetCache.put(id, widget);
        addWorldWidget(widget);

        if (pendingSourceNodeId != null && pendingSourcePin != null && autoWirePin != null) {
            FlowConnection newConnection;
            if (pendingSourceIsInput) {
                newConnection = new FlowConnection(id, autoWirePin, pendingSourceNodeId, pendingSourcePin);
                removeExistingInputConnection(pendingSourceNodeId, pendingSourcePin);
            } else {
                newConnection = new FlowConnection(pendingSourceNodeId, pendingSourcePin, id, autoWirePin);
                newConnection.setEditorSourceNodeId(pendingEditorSourceNodeId);
                newConnection.setEditorSourcePin(pendingEditorSourcePin);
                removeExistingInputConnection(id, autoWirePin);
            }
            graph.getConnections().add(newConnection);
            refreshInputWidgets(pendingSourceIsInput ? pendingSourceNodeId : id);
        }

        pendingSourceNodeId = null;
        pendingSourcePin = null;
        pendingEditorSourceNodeId = null;
        pendingEditorSourcePin = null;
        pendingSourceIsInput = false;
        return id;
    }

    protected void onSave() {
        syncNodePositions();
        saveGraph();
    }

    protected void saveGraph() {
        if (studioMode && activeStudioDocument != null && activeStudioDocument.graph() != null) {
            graph = activeStudioDocument.graph();
        }
        normalizePassthroughConnections();
        if (studioMode && activeStudioDocument != null && ReSyncResourceDragPayload.WORLDGEN.equals(activeStudioDocument.type())) {
            WorldGenProject project = WorldGenManager.getInstance().getCachedProject(serverId, activeStudioDocument.id());
            if (project == null) {
                project = WorldGenManager.getInstance().createProjectTemplate("Continental", activeStudioDocument.id());
            }
            project.setTerrainGraph(WorldGenManager.getInstance().toWorldGenGraph(graph));
            WorldGenManager.getInstance().saveWorldGen(serverId, project);
            return;
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (studioMode && activeStudioDocument != null && ReSyncResourceDragPayload.COMMAND.equals(activeStudioDocument.type())) {
            CommandBindingContext command = saveCommandDocument(flowManager);
            if (command == null) {
                return;
            }
            FlowGraph commandGraph = activeStudioDocument.graph() != null ? activeStudioDocument.graph() : graph;
            if (commandGraph == null) {
                return;
            }
            graph = commandGraph;
            if (flowManager.isCommandFlowIdentityBlocked(serverId, activeStudioDocument.id())) {
                new Notification("Command", "ID Conflicts With Content", Notification.Type.ERROR);
                return;
            }
            DesignerSaveNotifications.start(serverId, ReSyncResourceType.FLOW, commandGraph.getId(), "/" + command.command);
            flowManager.saveFlow(serverId, commandGraph);
            flowManager.setCommandBinding(serverId, activeStudioDocument.id(), encodeCommandContext(command));
            return;
        }
        if (flowManager != null && serverId != null) {
            startGraphSaveNotification(graph);
            flowManager.saveFlow(serverId, graph);
        }
    }

    private void startGraphSaveNotification(FlowGraph savingGraph) {
        if (savingGraph == null) {
            return;
        }
        CustomContentDefinition content = CustomContentGraphAdapter.toDefinition(savingGraph);
        if (content != null) {
            DesignerSaveNotifications.start(serverId, ReSyncResourceType.CUSTOM_CONTENT, content.getId(), content.getDisplayName());
            return;
        }
        DesignerSaveNotifications.start(serverId, ReSyncResourceType.FLOW, savingGraph.getId(), savingGraph.getId());
    }

    private CommandBindingContext saveCommandDocument(FlowManager manager) {
        if (manager == null || activeStudioDocument == null) {
            return null;
        }
        CommandBindingContext next = new CommandBindingContext();
        next.command = normalizeCommandLabel(commandLabelInput != null ? commandLabelInput.getText() : activeStudioDocument.id());
        if (next.command.isBlank()) {
            new Notification("Command", "Invalid Label", Notification.Type.ERROR);
            return null;
        }
        next.subcommands = collectCommandPaths();
        next.structured = commandStructuredToggle != null && commandStructuredToggle.getValue();
        return next;
    }

    private void normalizePassthroughConnections() {
        if (graph == null || graph.getConnections() == null) {
            return;
        }
        for (FlowConnection connection : graph.getConnections()) {
            if (!NodeWidget.isPassthroughOutputPin(connection.getSourcePin())) {
                continue;
            }
            FlowConnection resolved = resolveConnectionSource(connection.getSourceNodeId(), connection.getSourcePin());
            if (resolved != null && resolved.getEditorSourceNodeId() != null) {
                connection.setSourceNodeId(resolved.getSourceNodeId());
                connection.setSourcePin(resolved.getSourcePin());
                copyEditorSource(resolved, connection);
            }
        }
        synchronizePassthroughRuntimeSources();
    }

    private void synchronizePassthroughRuntimeSources() {
        for (FlowConnection connection : graph.getConnections()) {
            String editorNodeId = connection.getEditorSourceNodeId();
            String editorPin = connection.getEditorSourcePin();
            if (editorNodeId == null || editorNodeId.isBlank() || !NodeWidget.isPassthroughOutputPin(editorPin)) {
                continue;
            }
            FlowConnection resolved = resolveConnectionSource(editorNodeId, editorPin);
            connection.setSourceNodeId(resolved.getSourceNodeId());
            connection.setSourcePin(resolved.getSourcePin());
        }
    }

    private boolean disconnectPinAt(FlowNodeWidget widget, int wx, int wy) {
        String inputPin = widget.getInputPinAtPosition(wx, wy);
        if (inputPin != null) {
            return disconnectInputPin(widget, inputPin);
        }
        String outputPin = widget.getOutputPinAtPosition(wx, wy);
        return outputPin != null && disconnectOutputPin(widget, outputPin);
    }

    private boolean removeOptionalInputPinAt(FlowNodeWidget widget, int wx, int wy) {
        String pinName = widget.getInputPinAtPosition(wx, wy);
        if (pinName == null || !widget.isOptionalInputPin(pinName)) {
            return false;
        }
        return removeOptionalInputPin(widget, pinName);
    }

    private boolean removeOptionalInputPin(FlowNodeWidget widget, String pinName) {
        if (pinName == null || !widget.isOptionalInputPin(pinName)) {
            return false;
        }
        String nodeId = findNodeId(widget);
        if (nodeId == null) {
            return false;
        }
        captureSnapshot();
        String passthroughPin = NodeWidget.passthroughOutputPin(pinName);
        boolean pairedOutput = widget.getPinKind(pinName, false) != null;
        Set<String> affectedTargets = new HashSet<>();
        if (graph.getConnections() != null) {
            graph.getConnections().removeIf(connection -> {
                boolean incoming = nodeId.equals(connection.getTargetNodeId()) && pinName.equals(connection.getTargetPin());
                boolean passthrough = nodeId.equals(editorSourceNodeId(connection)) && passthroughPin.equals(editorSourcePin(connection));
                boolean repeatedOutput = pairedOutput && nodeId.equals(connection.getSourceNodeId()) && pinName.equals(connection.getSourcePin());
                if (passthrough || repeatedOutput) {
                    affectedTargets.add(connection.getTargetNodeId());
                }
                return incoming || passthrough || repeatedOutput;
            });
        }
        if (graph.getEditorPassthroughs() != null) {
            graph.getEditorPassthroughs().removeIf(passthrough -> nodeId.equals(passthrough.getNodeId()) && pinName.equals(passthrough.getInputPin()));
        }
        if (!widget.removeOptionalInputPin(pinName)) {
            return false;
        }
        for (String targetId : affectedTargets) {
            refreshInputWidgets(targetId);
        }
        return true;
    }

    private boolean disconnectInputPin(FlowNodeWidget widget, String pinName) {
        String nodeId = findNodeId(widget);
        if (nodeId == null || graph.getConnections() == null) {
            return false;
        }
        boolean connected = graph.getConnections().stream().anyMatch(conn -> nodeId.equals(conn.getTargetNodeId()) && pinName.equals(conn.getTargetPin()));
        if (!connected) {
            return false;
        }
        captureSnapshot();
        graph.getConnections().removeIf(conn -> nodeId.equals(conn.getTargetNodeId()) && pinName.equals(conn.getTargetPin()));
        refreshInputWidgets(nodeId);
        return true;
    }

    private boolean disconnectOutputPin(FlowNodeWidget widget, String pinName) {
        String nodeId = findNodeId(widget);
        if (nodeId == null || graph.getConnections() == null) {
            return false;
        }
        Set<String> affectedTargets = new HashSet<>();
        for (FlowConnection connection : graph.getConnections()) {
            if (nodeId.equals(editorSourceNodeId(connection)) && pinName.equals(editorSourcePin(connection))) {
                affectedTargets.add(connection.getTargetNodeId());
            }
        }
        if (affectedTargets.isEmpty()) {
            return false;
        }
        captureSnapshot();
        graph.getConnections().removeIf(conn -> {
            boolean matches = nodeId.equals(editorSourceNodeId(conn)) && pinName.equals(editorSourcePin(conn));
            if (matches) {
                affectedTargets.add(conn.getTargetNodeId());
            }
            return matches;
        });
        for (String targetId : affectedTargets) {
            refreshInputWidgets(targetId);
        }
        return true;
    }

    private FlowConnection findConnectionAt(double worldX, double worldY) {
        if (graph.getConnections() == null) {
            return null;
        }
        double hitRadius = WIRE_HIT_RADIUS / Math.max(zoomLevel, 0.1f);

        for (FlowConnection conn : graph.getConnections()) {
            for (WireSegment segment : hitTestSegments(conn)) {
                if (isNearWireSegment(worldX, worldY, segment, hitRadius)) {
                    return conn;
                }
            }
        }
        return null;
    }

    private List<WireSegment> hitTestSegments(FlowConnection connection) {
        PinPoint end = targetInputPoint(connection);
        List<FlowConnection> fanout = fanoutConnections(connection);
        if (fanout.size() > 1) {
            return fanoutSegments(connection, fanout, false);
        }
        PinPoint start = sourceOutputPoint(connection);
        return start != null && end != null ? wireSegments(start.x(), start.y(), end.x(), end.y()) : List.of();
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
        t = Math.clamp(t, 0, 1);
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
        if (!graph.getConnections().contains(conn)) {
            return;
        }
        captureSnapshot();
        graph.getConnections().remove(conn);
        refreshInputWidgets(conn.getTargetNodeId());
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

            FlowNodeWidget widget = createNodeWidget(newId, newNode);
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

        selectedNodeIds.addAll(newSelectedIds);
    }

    private void cutNodes() {
        copyNodes();
        captureSnapshot();
        deleteSelectedNodes();
    }

    private void duplicateNodes() {
        copyNodes();
        pasteNodes();
    }

    private void captureSnapshot() {
        graphHistory.capture();
    }

    private void restoreSnapshot(GraphSnapshot snapshot) {
        selectedNodeIds.clear();
        selectedNodeIds.addAll(snapshot.selectedIds);

        if (hasSameGraphStateExceptConnections(snapshot)) {
            Set<String> affectedTargets = changedConnectionTargets(graph.getConnections(), snapshot.connections);
            graph.getConnections().clear();
            graph.getConnections().addAll(copyConnections(snapshot.connections));
            for (String targetId : affectedTargets) {
                refreshInputWidgets(targetId);
            }
            return;
        }

        for (FlowNodeWidget widget : widgetCache.values()) {
            removeWorldWidget(widget);
        }
        widgetCache.clear();

        graph.getNodes().clear();
        for (Map.Entry<String, FlowNode> entry : snapshot.nodes.entrySet()) {
            String nodeId = entry.getKey();
            FlowNode node = entry.getValue();
            graph.getNodes().put(nodeId, node);
            FlowNodeWidget widget = createNodeWidget(nodeId, node);
            addWorldWidget(widget);
            widgetCache.put(nodeId, widget);
        }

        graph.getConnections().clear();
        graph.getConnections().addAll(copyConnections(snapshot.connections));
        graph.setFunction(snapshot.function);
        graph.setFunctionOwner(snapshot.functionOwner);
        graph.setFunctionNamespace(snapshot.functionNamespace);
        graph.setFunctionVersion(snapshot.functionVersion);
        graph.setFunctionDescription(snapshot.functionDescription);
        graph.setFunctionInputs(copyFunctionParameters(snapshot.functionInputs));
        graph.setFunctionOutputs(copyFunctionParameters(snapshot.functionOutputs));
        graph.setEditorPassthroughs(copyEditorPassthroughs(snapshot.editorPassthroughs));

        for (String nodeId : snapshot.nodes.keySet()) {
            refreshInputWidgets(nodeId);
        }
    }

    private boolean hasSameGraphStateExceptConnections(GraphSnapshot snapshot) {
        if (graph.isFunction() != snapshot.function || graph.getNodes().size() != snapshot.nodes.size()) {
            return false;
        }
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            FlowNode current = entry.getValue();
            FlowNode saved = snapshot.nodes.get(entry.getKey());
            if (saved == null || current.getVersion() != saved.getVersion() || Double.compare(current.getX(), saved.getX()) != 0
                || Double.compare(current.getY(), saved.getY()) != 0 || !Objects.equals(current.getType(), saved.getType())
                || !Objects.equals(current.getInputValues(), saved.getInputValues())) {
                return false;
            }
        }
        return Objects.equals(graph.getFunctionOwner(), snapshot.functionOwner)
            && Objects.equals(graph.getFunctionNamespace(), snapshot.functionNamespace)
            && graph.getFunctionVersion() == snapshot.functionVersion
            && Objects.equals(graph.getFunctionDescription(), snapshot.functionDescription)
            && sameFunctionParameters(graph.getFunctionInputs(), snapshot.functionInputs)
            && sameFunctionParameters(graph.getFunctionOutputs(), snapshot.functionOutputs)
            && sameEditorPassthroughs(graph.getEditorPassthroughs(), snapshot.editorPassthroughs);
    }

    private Set<String> changedConnectionTargets(List<FlowConnection> current, List<FlowConnection> saved) {
        Set<String> affectedTargets = new HashSet<>();
        for (FlowConnection connection : current) {
            if (saved.stream().noneMatch(candidate -> sameConnection(connection, candidate))) {
                affectedTargets.add(connection.getTargetNodeId());
            }
        }
        for (FlowConnection connection : saved) {
            if (current.stream().noneMatch(candidate -> sameConnection(connection, candidate))) {
                affectedTargets.add(connection.getTargetNodeId());
            }
        }
        return affectedTargets;
    }

    private boolean sameConnection(FlowConnection first, FlowConnection second) {
        return Objects.equals(first.getSourceNodeId(), second.getSourceNodeId()) && Objects.equals(first.getSourcePin(), second.getSourcePin())
            && Objects.equals(first.getTargetNodeId(), second.getTargetNodeId()) && Objects.equals(first.getTargetPin(), second.getTargetPin())
            && Objects.equals(first.getEditorSourceNodeId(), second.getEditorSourceNodeId())
            && Objects.equals(first.getEditorSourcePin(), second.getEditorSourcePin());
    }

    private boolean sameFunctionParameters(List<FlowGraph.FunctionParameter> current, List<FlowGraph.FunctionParameter> saved) {
        List<FlowGraph.FunctionParameter> currentParameters = current != null ? current : List.of();
        List<FlowGraph.FunctionParameter> savedParameters = saved != null ? saved : List.of();
        if (currentParameters.size() != savedParameters.size()) {
            return false;
        }
        for (int index = 0; index < currentParameters.size(); index++) {
            FlowGraph.FunctionParameter currentParameter = currentParameters.get(index);
            FlowGraph.FunctionParameter savedParameter = savedParameters.get(index);
            if (!Objects.equals(currentParameter.getName(), savedParameter.getName()) || !Objects.equals(currentParameter.getType(), savedParameter.getType())
                || !Objects.equals(currentParameter.getTypeRef(), savedParameter.getTypeRef())
                || !Objects.equals(currentParameter.getWidget(), savedParameter.getWidget())
                || !Objects.equals(currentParameter.getOptionsSource(), savedParameter.getOptionsSource())
                || !Objects.equals(currentParameter.getDefaultValue(), savedParameter.getDefaultValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean sameEditorPassthroughs(List<FlowGraph.EditorPassthrough> current, List<FlowGraph.EditorPassthrough> saved) {
        List<FlowGraph.EditorPassthrough> currentPassthroughs = current != null ? current : List.of();
        List<FlowGraph.EditorPassthrough> savedPassthroughs = saved != null ? saved : List.of();
        if (currentPassthroughs.size() != savedPassthroughs.size()) {
            return false;
        }
        for (int index = 0; index < currentPassthroughs.size(); index++) {
            FlowGraph.EditorPassthrough currentPassthrough = currentPassthroughs.get(index);
            FlowGraph.EditorPassthrough savedPassthrough = savedPassthroughs.get(index);
            if (!Objects.equals(currentPassthrough.getNodeId(), savedPassthrough.getNodeId())
                || !Objects.equals(currentPassthrough.getInputPin(), savedPassthrough.getInputPin())) {
                return false;
            }
        }
        return true;
    }

    private static List<FlowConnection> copyConnections(List<FlowConnection> connections) {
        List<FlowConnection> copied = new ArrayList<>();
        if (connections == null) {
            return copied;
        }
        for (FlowConnection connection : connections) {
            if (connection == null) {
                continue;
            }
            FlowConnection copiedConnection = new FlowConnection(connection.getSourceNodeId(), connection.getSourcePin(), connection.getTargetNodeId(), connection.getTargetPin());
            copiedConnection.setEditorSourceNodeId(connection.getEditorSourceNodeId());
            copiedConnection.setEditorSourcePin(connection.getEditorSourcePin());
            copied.add(copiedConnection);
        }
        return copied;
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
            FlowGraph.FunctionParameter copy = new FlowGraph.FunctionParameter(parameter.getName(), parameter.getType(), parameter.getWidget(), parameter.getOptionsSource(), parameter.getDefaultValue());
            copy.setTypeRef(parameter.getTypeRef());
            copied.add(copy);
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

    private WorldDashboardEntry findWorldDashboard(String worldName) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || worldName == null) {
            return null;
        }
        for (WorldDashboardEntry entry : manager.getWorldDashboardForServer(serverId)) {
            if (entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(worldName)) {
                return entry;
            }
        }
        return null;
    }

    private String findEntryKeyIgnoreCase(Map<String, ?> entries, String target) {
        if (entries == null || target == null || target.isBlank()) {
            return null;
        }
        for (String key : entries.keySet()) {
            if (key != null && key.equalsIgnoreCase(target)) {
                return key;
            }
        }
        return null;
    }

    private String resultWorldName(WorldOperationResult result) {
        return resultDataText(result);
    }

    private String resultDataText(WorldOperationResult result) {
        if (result == null) {
            return "";
        }
        if (result.getData() != null) {
            for (String key : List.of("worldName", "world")) {
                Object raw = result.getData().get(key);
                String value = raw == null ? "" : String.valueOf(raw).trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return safeText(result.getWorldName()).trim();
    }

    private void showWorldAuditSnapshot(JsonElement data) {
        JsonArray records = data != null && data.isJsonArray() ? data.getAsJsonArray() : new JsonArray();
        PopupWidget.Builder builder = new PopupWidget.Builder("World History")
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(560, Math.min(330, 120 + Math.min(records.size(), 9) * 22));
        if (records.isEmpty()) {
            builder.addRow("Status", true, 18, readOnlyButton("No Operations"));
        } else {
            int shown = 0;
            for (JsonElement element : records) {
                if (shown >= 9) {
                    break;
                }
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                boolean success = object.has("success") && !object.get("success").isJsonNull() && object.get("success").getAsBoolean();
                String status = success ? "Ok" : "Failed";
                String action = formatTitleWords(jsonText(object, "action").isBlank() ? "Operation" : jsonText(object, "action"));
                String world = jsonText(object, "targetWorld");
                String message = success ? jsonText(object, "message") : jsonText(object, "failureReason");
                long duration = object.has("durationMillis") && !object.get("durationMillis").isJsonNull() ? object.get("durationMillis").getAsLong() : 0L;
                String text = action + (world.isBlank() ? "" : " | " + world) + " | " + duration + "ms" + (message.isBlank() ? "" : " | " + message);
                builder.addRow(status, true, 18, readOnlyButton(text.length() > 72 ? text.substring(0, 69) + "..." : text));
                shown++;
            }
        }
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private void showWorldWhoPopup(WorldOperationResult result) {
        String worldName = resultWorldName(result);
        PopupWidget.Builder builder = new PopupWidget.Builder(worldName.isBlank() ? "World Players" : "Players In " + worldName)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(520, 220);
        Object rawPlayers = result != null && result.getData() != null ? result.getData().get("players") : null;
        List<?> players = rawPlayers instanceof List<?> list ? list : List.of();
        if (players.isEmpty()) {
            builder.addRow("Players", true, 18, readOnlyButton("No Players"));
        } else {
            int shown = 0;
            for (Object player : players) {
                if (shown >= 8) {
                    break;
                }
                builder.addRow("Player", true, 18, readOnlyButton(String.valueOf(player)));
                shown++;
            }
        }
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private IconButton readOnlyButton(String text) {
        IconButton button = new IconButton.Builder()
            .label(text)
            .autoWidthOnTextChange(true)
            .entranceAnimation(false)
            .build();
        button.active = false;
        return button;
    }

    @Override
    protected String jsonText(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return safeText(object.get(key).getAsString());
    }

    @Override
    protected boolean jsonBool(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<String> parseCommaSeparatedList(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        List<String> values = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return WorldUiSupport.normalizeUniqueEntries(values);
    }

    private String formatTitleWords(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('_', ' ').replace('-', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.trim().split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private int textWidth(String value) {
        String clean = safeText(value).replaceAll("(?i)[&§][0-9a-fk-or]", "").replaceAll("<[^>]+>", "");
        if (RemotelyClient.tr != null) {
            return RemotelyClient.tr.getWidth(clean);
        }
        return clean.length() * 6;
    }

    private MinecraftGameAssets getGameAssets() {
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            MinecraftGameAssets gameAssets = RemotelyClient.INSTANCE.getHost().getGameAssets();
            if (gameAssets != null) {
                return gameAssets;
            }
        }
        return MinecraftGameAssets.EMPTY;
    }

    private void drawMinecraftTexture(IDrawContext context, MinecraftGameAssets gameAssets, MinecraftAssetReference reference, Identifier fallbackId, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        if (MinecraftUiPreviewRenderer.drawAssetRegion(context, gameAssets, reference, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight)) {
            return;
        }
        MinecraftUiPreviewRenderer.drawImage(context, fallbackId, x, y, width, height);
    }

    private Long parseNullableLong(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double parseNullableDouble(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Float parseNullableFloat(String value) {
        String text = safeText(value).trim();
        if (text.isBlank()) {
            return 0.0F;
        }
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean mouseScrolled(ReScrollEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        double horizontalAmount = event.horizontalAmount();
        double verticalAmount = event.verticalAmount();
        if (nodeItemSelector != null && nodeItemSelector.visible && nodeItemSelector.mouseScrolled(event.retarget(nodeItemSelector, mouseX, mouseY))) {
            return true;
        }
        if (handlePopupWidgetMouseScrolled(event)) {
            return true;
        }
        if (dragState.isDragging && panFromScroll(horizontalAmount, verticalAmount, event.modifiers().shift())) {
            return true;
        }
        if (handleStudioWorkspaceMouseScrolled(event)) {
            return true;
        }
        if (paletteSidePanel != null && paletteSidePanel.isVisible() && paletteSidePanel.mouseScrolled(event.retarget(paletteSidePanel, mouseX, mouseY))) {
            return true;
        }
        double[] undistortedCoords = unDistortMouse(mouseX, mouseY);
        double[] worldMouse = screenToWorld(undistortedCoords[0], undistortedCoords[1]);
        int wx = (int) worldMouse[0];
        int wy = (int) worldMouse[1];
        for (int i = worldWidgets.size() - 1; i >= 0; i--) {
            FlowNodeWidget widget = (FlowNodeWidget) worldWidgets.get(i);
            if (Widget.dispatchMouseScrolled(widget, event.retarget(widget, wx, wy))) {
                return true;
            }
        }
        return super.mouseScrolled(event);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (!studioMode) {
            layoutHeaderButtons();
        }
        layoutStudioHeaderButtons();
        for (StudioDocument document : studioDocuments) {
            if (document.view() != null) {
                document.view().resize(width, studioMode ? studioEditorHeight() : height);
            }
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
