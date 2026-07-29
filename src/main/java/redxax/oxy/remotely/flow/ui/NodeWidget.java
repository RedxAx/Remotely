package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowTypeRef;
import redxax.oxy.remotely.flow.data.FlowResourceReference;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.data.flow.OptionCatalogLoader;
import redxax.oxy.remotely.data.flow.ReSyncFlowClient;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.FlowOptionSourceMetadata;
import restudio.resync.flow.contract.FlowTypeMetadata;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.render.Render;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.ColorFieldWidget;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SliderWidget;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitleBarStyle;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static restudio.rescreen.config.Config.shadow;
import static restudio.rescreen.render.TextRenderer.tr;

public class NodeWidget extends AnimatedWidget {
    private final FlowNode node;
    private final FlowGraph graph;
    private final String nodeId;
    private final String serverId;
    private final List<NodeDefinition.PinDefinition> inputs = new ArrayList<>();
    private final List<NodeDefinition.PinDefinition> outputs = new ArrayList<>();
    private final NodeDefinition definition;
    private final Map<String, Widget> inputWidgets = new HashMap<>();
    private final Map<String, String> searchableSelectorValues = new HashMap<>();
    private final List<NodeDefinition.PinDefinition> visibleInputs = new ArrayList<>();
    private final List<NodeDefinition.PinDefinition> visibleOutputs = new ArrayList<>();
    private final Set<String> stringTemplateInputNames = new LinkedHashSet<>();
    private final List<FlowBranch> flowBranches = new ArrayList<>();
    private final List<FlowGraph.FunctionParameter> callParameters = new ArrayList<>();
    private final Runnable onClose;
    private final AnimatedButton closeButton;
    private final AnimatedButton openFunctionButton;
    private AnimatedButton addBranchButton;
    private final Map<String, AnimatedButton> addInputButtons = new LinkedHashMap<>();
    private AnimatedButton paramButton;
    private static final TitleBarStyle TITLE_STYLE = TitleBarStyle.COMPACT;
    private static final int TITLE_HEIGHT = TITLE_STYLE.height();
    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_SPACING = 6;
    private static final int PIN_BUTTON_SIZE = 10;
    private static final int PIN_TEXT_GAP = 4;
    private static final int INPUT_FIELD_GAP = 6;
    private static final int INPUT_WIDGET_WIDTH = 90;
    private static final int INPUT_WIDGET_HEIGHT = 16;
    private static final int OUTPUT_WIDGET_WIDTH = 100;
    private static final int PASSTHROUGH_DASH_SIZE = 7;
    private static final int PASSTHROUGH_DASH_GAP = 5;
    private static final int TOGGLE_WIDGET_WIDTH = 28;
    private static final int TOGGLE_WIDGET_HEIGHT = 12;
    private static final int COLUMN_GAP = 12;
    private static final int SINGLE_COLUMN_MIN_WIDTH = 100;
    private static final int DEFAULT_WIDTH = 170;
    private static final int PIN_HIT_PADDING = 4;
    private static final int CLOSE_BUTTON_WIDTH = TITLE_STYLE.controlWidth();
    private static final int CLOSE_BUTTON_HEIGHT = TITLE_STYLE.controlHeight();
    private static final String FLOW_BRANCHES_KEY = "__flow_branches";
    private static final String PASSTHROUGH_OUTPUT_PREFIX = "__passthrough:";
    private static final String REPEATABLE_COUNT_PREFIX = "__repeatable_count:";
    private static final String LEGACY_PERMISSION_COUNT_KEY = "__permission_count";
    private static final String REMOVED_OPTIONAL_INPUTS_KEY = "__removed_optional_inputs";
    private static final String CUSTOM_FUNCTION_NODE_PREFIX = "custom_function:";
    private static final String FUNCTION_START_ID = "function_start";
    private static final String FUNCTION_END_ID = "function_end";
    private static final String FUNCTION_START_MIGRATED_ID = "function.start";
    private static final String FUNCTION_END_MIGRATED_ID = "function.end";
    private static final String FUNCTION_START_CANONICAL_ID = "function.function_start";
    private static final String FUNCTION_END_CANONICAL_ID = "function.function_end";
    private static final String FUNCTION_INPUT_ID = "function_input";
    private static final String FUNCTION_OUTPUT_ID = "function_output";
    private static final String CALL_FUNCTION_ID = "call_function";
    private static final String FUNCTION_INPUT_CANONICAL_ID = "function.function_input";
    private static final String FUNCTION_OUTPUT_CANONICAL_ID = "function.function_output";
    private static final String CALL_FUNCTION_CANONICAL_ID = "call.function";
    private static final String AUTOMATION_VARIABLE_ID = "automation.variable";
    private static final String AUTOMATION_SCHEDULE_ID = "automation.schedule";
    private static final String VARIABLE_CHANGED_EVENT_ID = "event.variable.changed";
    private static final String VARIABLE_CATALOG = "server:resync:variable_definition";
    private static final String TIMER_CATALOG = "server:resync:timer_definition";
    private static final String SCHEDULE_CATALOG = "server:resync:schedule_definition";
    private static final String CALL_PARAMETERS_KEY = "__call_parameters";
    private static final String FUNCTION_SIGNATURE_KEY = "__function_signature";
    private static final String FUNCTION_SIGNATURE_ISSUES_KEY = "__function_signature_issues";

    private boolean updatingBranchSelection = false;
    private int lastScreenX;
    private int lastScreenY;
    private boolean hasLastScreenMouse;

    public NodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId) {
        this(x, y, node, graph, nodeId, null, null);
    }

    public NodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId) {
        this(x, y, node, graph, nodeId, serverId, null);
    }

    public NodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId, Runnable onClose) {
        super(x, y, DEFAULT_WIDTH, 100, "");
        setCursorHoverReactive(true);
        this.node = node;
        this.graph = graph;
        this.nodeId = nodeId;
        this.serverId = serverId;
        loadCallParameters();
        this.definition = resolveDefinition(serverId, node.getType());
        if (definition == null) {
            requestNodeRegistry();
        }
        this.enableHoverColors = true;
        this.selectable = true;
        this.animateElevation = false;
        this.entranceAnimationEnabled = false;
        this.onClose = onClose;
        this.closeButton = new AnimatedButton.Builder()
            .onClick(() -> {
                if (this.onClose != null) {
                    this.onClose.run();
                }
            })
            .accentType(ThemeManager.getAccent("danger"))
            .animateElevation(false)
            .entranceAnimation(false)
            .size(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .hint("Delete Node")
            .build();
        this.closeButton.visible = this.onClose != null;
        this.openFunctionButton = new AnimatedButton.Builder()
            .onClick(this::openCustomFunction)
            .accentType(ThemeManager.getAccent("nice"))
            .animateElevation(false)
            .entranceAnimation(false)
            .size(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .hint("Open Function")
            .build();
        this.openFunctionButton.visible = customFunctionId() != null;

        if (isFunctionStartType(node.getType()) || isFunctionEndType(node.getType()) || isFunctionCallNode()) {
            this.paramButton = new AnimatedButton.Builder()
                .label("+")
                .onClick(this::showParamContextMenu)
                .accentType(ThemeManager.getAccent("nice"))
                .animateElevation(false)
                .entranceAnimation(false)
                .size(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
                .hint(isFunctionCallNode() ? "Arguments" : "Params")
                .build();
        } else {
            this.paramButton = null;
        }

        if (definition != null) {
            inputs.addAll(definition.getInputs());
            outputs.addAll(definition.getOutputs());
            applyFunctionParameterPins();
            applyAutomationDefinitionPins();
            applyFunctionCallSignaturePins();
            applyAdvancedInputPins();
            applyAdvancedOutputPins();
            applyRemovedOptionalInputs();
            updateAddInputButtons();
            seedDefaultInputValues();
            updateStringTemplatePins();
            preflightOptionCatalogs();
            createInputWidgets();
            createOutputWidgets();
            updateSize();
        } else {
            createLoadingState();
        }
        setAnimateLayout(true);
    }

    private static NodeDefinition resolveDefinition(String serverId, String nodeType) {
        NodeDefinition definition = NodeRegistry.getInstance() != null ? NodeRegistry.getInstance().getDefinition(serverId, nodeType) : null;
        if (definition != null) {
            return definition;
        }
        if (NodeRegistry.getInstance() == null) {
            return null;
        }
        if (isFunctionStartType(nodeType)) {
            definition = NodeRegistry.getInstance().getDefinition(serverId, FUNCTION_START_MIGRATED_ID);
            if (definition != null) {
                return definition;
            }
            return NodeRegistry.getInstance().getDefinition(serverId, FUNCTION_START_CANONICAL_ID);
        }
        if (isFunctionEndType(nodeType)) {
            definition = NodeRegistry.getInstance().getDefinition(serverId, FUNCTION_END_MIGRATED_ID);
            if (definition != null) {
                return definition;
            }
            return NodeRegistry.getInstance().getDefinition(serverId, FUNCTION_END_CANONICAL_ID);
        }
        String migratedNodeType = migratedFunctionNodeType(nodeType);
        if (migratedNodeType != null) {
            return NodeRegistry.getInstance().getDefinition(serverId, migratedNodeType);
        }
        return null;
    }

    private static String migratedFunctionNodeType(String nodeType) {
        if (nodeType == null) {
            return null;
        }
        return switch (nodeType) {
            case FUNCTION_INPUT_ID -> FUNCTION_INPUT_CANONICAL_ID;
            case FUNCTION_OUTPUT_ID -> FUNCTION_OUTPUT_CANONICAL_ID;
            case CALL_FUNCTION_ID -> CALL_FUNCTION_CANONICAL_ID;
            default -> null;
        };
    }

    private void requestNodeRegistry() {
        FlowManager manager = FlowManager.getInstance();
        if (manager != null) {
            manager.ensureFlowClient(serverId).requestNodeRegistry();
        }
    }

    private String customFunctionId() {
        String type = node != null ? node.getType() : null;
        if (type == null || !type.startsWith(CUSTOM_FUNCTION_NODE_PREFIX)) {
            return null;
        }
        String functionId = type.substring(CUSTOM_FUNCTION_NODE_PREFIX.length());
        return functionId.isBlank() ? null : functionId;
    }

    private void openCustomFunction() {
        String functionId = customFunctionId();
        FlowManager manager = FlowManager.getInstance();
        if (functionId != null && manager != null && serverId != null) {
            manager.openFlowEditor(serverId, null, functionId);
        }
    }

    public boolean hasLoadedDefinition() {
        return definition != null;
    }

    private void createInputWidgets() {
        if (graph == null || nodeId == null) return;

        for (NodeDefinition.PinDefinition input : inputs) {
            if (!isInputWidgetEligible(input) || inputWidgets.containsKey(input.getName())) {
                continue;
            }
            Widget widget = buildWidgetForPin(input);
            if (widget != null) {
                inputWidgets.put(input.getName(), widget);
            }
        }
        updatePinVisibility();
    }

    private void preflightOptionCatalogs() {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        List<OptionCatalogLoader.Request> requests = new ArrayList<>();
        for (NodeDefinition.PinDefinition input : inputs) {
            String source = input != null ? input.getOptionsSource() : null;
            if (source == null || source.isBlank()) {
                continue;
            }
            Map<String, Object> context = optionCatalogContext(input);
            requests.add(OptionCatalogLoader.request(source, context));
        }
        OptionCatalogLoader.preload(serverId, requests);
    }

    private Widget buildWidgetForPin(NodeDefinition.PinDefinition input) {
        Object currentValue = node.getInputValues() != null ? node.getInputValues().get(input.getName()) : null;
        NodeDefinition.WidgetType widgetType = resolveWidgetType(input);

        switch (widgetType) {
            case DROPDOWN -> {
                List<String> options = resolveOptions(input);
                if (options.isEmpty()) {
                    return buildTextInput(input, currentValue, input.getOptionsSource());
                }
                String selected = resolveSelected(options, currentValue, input.getDefaultValue());
                return buildDropdown(input, options, selected);
            }
            case SEARCHABLE_LIST -> {
                List<String> options = resolveOptions(input);
                if (options.isEmpty()) {
                    options = fallbackSearchableOptions(currentValue, input.getDefaultValue());
                }
                String selected = resolveSelected(options, currentValue, input.getDefaultValue());
                return buildSearchableSelector(input, options, selected);
            }
            case TOGGLE -> {
                boolean toggled = currentValue instanceof Boolean ? (boolean) currentValue : Boolean.parseBoolean(String.valueOf(currentValue));
                ToggleWidget widget = new ToggleWidget.Builder()
                    .toggled(toggled)
                    .onChange(() -> {
                        handleInputValueChanged(input);
                    })
                    .entranceAnimation(false)
                    .build();
                widget.setSize(TOGGLE_WIDGET_WIDTH, TOGGLE_WIDGET_HEIGHT);
                return widget;
            }
            case SLIDER -> {
                double value = 0.0;
                if (currentValue instanceof Number n) {
                    value = n.doubleValue();
                } else if (currentValue != null) {
                    try {
                        value = Double.parseDouble(currentValue.toString());
                    } catch (NumberFormatException ignored) {
                    }
                }
                NodeDefinition.PinConstraints constraints = input.getConstraints();
                double min = constraints != null && constraints.getMin() != null ? constraints.getMin() : 0.0;
                double max = constraints != null && constraints.getMax() != null ? constraints.getMax() : 100.0;
                double step = constraints != null && constraints.getStep() != null ? constraints.getStep() : 1.0;
                return new SliderWidget.Builder()
                    .min(min)
                    .max(max)
                    .step(step)
                    .value(value)
                    .onChange(() -> {
                        handleInputValueChanged(input);
                    })
                    .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                    .entranceAnimation(false)
                    .build();
            }
            case NUMBER -> {
                String textValue = currentValue != null ? currentValue.toString() : "";
                TextInputWidget widget = new TextInputWidget.Builder()
                    .text(textValue)
                    .placeholder("")
                    .forcePlaceholder(false)
                    .numericOnly(true)
                    .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                    .onChange(() -> handleInputValueChanged(input))
                    .entranceAnimation(false)
                    .build();
                return widget;
            }
            case MULTILINE -> {
                String textValue = currentValue != null ? currentValue.toString() : "";
                return new TextAreaWidget.Builder()
                    .text(textValue)
                    .placeholder("")
                    .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT * 3)
                    .onChange(text -> handleInputValueChanged(input))
                    .entranceAnimation(false)
                    .build();
            }
            case COLOR -> {
                String textValue = currentValue != null ? currentValue.toString() : "#FFFFFF";
                return new ColorFieldWidget.Builder()
                    .color(textValue)
                    .onChange(() -> {
                        handleInputValueChanged(input);
                    })
                    .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                    .entranceAnimation(false)
                    .build();
            }
            default -> {
                return buildTextInput(input, currentValue, "");
            }
        }
    }

    private Widget buildTextInput(NodeDefinition.PinDefinition input, Object currentValue, String placeholder) {
        String textValue = currentValue != null ? currentValue.toString() : "";
        return new TextInputWidget.Builder()
            .text(textValue)
            .placeholder(placeholder != null ? placeholder : "")
            .forcePlaceholder(false)
            .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
            .onChange(() -> handleInputValueChanged(input))
            .entranceAnimation(false)
            .build();
    }

    protected boolean shouldShowLiteralInput(NodeDefinition.PinDefinition input) {
        if (isStringTemplateValuePin(input)) {
            return false;
        }
        return true;
    }

    protected boolean shouldShowInputPin(NodeDefinition.PinDefinition input) {
        return true;
    }

    private void handleInputValueChanged(NodeDefinition.PinDefinition input) {
        saveInputValue();
        refreshDependentCatalogs(input.getName());
        if (isFunctionCallNode() && "function".equals(input.getName())) {
            refreshInputWidgets();
            return;
        }
        updatePinVisibility();
    }

    private void refreshDependentCatalogs(String contextKey) {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry == null || contextKey == null || contextKey.isBlank()) {
            return;
        }
        Set<String> refreshedSources = new LinkedHashSet<>();
        for (NodeDefinition.PinDefinition candidate : inputs) {
            if (contextKey.equals(candidate.getName())) {
                continue;
            }
            String sourceId = candidate.getOptionsSource();
            if (sourceId == null || sourceId.isBlank()) {
                continue;
            }
            FlowOptionSourceMetadata metadata = registry.getServerOptionSource(serverId, sourceId);
            if (metadata != null && metadata.getContextKeys().contains(contextKey) && refreshedSources.add(sourceId)) {
                refreshOptionCatalog(sourceId);
            }
        }
    }

    private boolean isInputWidgetEligible(NodeDefinition.PinDefinition input) {
        return input != null
            && input.getType() == NodeDefinition.PinType.DATA
            && shouldShowInputPin(input)
            && shouldShowLiteralInput(input)
            && isLiteralInput(input)
            && !isInputWired(input.getName());
    }

    private Widget buildSearchableSelector(NodeDefinition.PinDefinition input, List<String> options, String selected) {
        List<OptionCatalogItem> initialItems = resolveCatalogItems(input, options);
        if (isRealCatalogOption(selected)) {
            searchableSelectorValues.put(input.getName(), selected);
        }
        AnimatedButton button = new AnimatedButton.Builder()
            .label(selectorButtonLabel(initialItems, selected))
            .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
            .entranceAnimation(false)
            .build();
        button.setAction(() -> {
            var screen = ScreenManager.getInstance().getCurrentScreen();
            if (screen == null) return;
            String catalogFailure = catalogFailure(input);
            if (!catalogFailure.isBlank()) {
                new Notification("Catalog Unavailable", catalogFailure, Notification.Type.WARN);
                return;
            }
            List<String> currentOptions = resolveOptions(input);
            if (currentOptions.isEmpty()) {
                currentOptions = fallbackSearchableOptions(node.getInputValues().get(input.getName()), input.getDefaultValue());
            }
            List<OptionCatalogItem> richItems = resolveCatalogItems(input, currentOptions);
            String selectedValue = searchableSelectorValues.getOrDefault(input.getName(), resourceId(node.getInputValues().get(input.getName())));
            Consumer<String> onSelected = option -> {
                if (!isRealCatalogOption(option)) {
                    return;
                }
                searchableSelectorValues.put(input.getName(), option);
                node.getInputValues().put(input.getName(), option);
                button.setMessage(selectorButtonLabel(resolveCatalogItems(input, resolveOptions(input)), option));
                handleInputValueChanged(input);
            };
            int selectorX = hasLastScreenMouse ? lastScreenX : button.getX();
            int selectorY = hasLastScreenMouse ? lastScreenY : button.getY() + button.getHeight();
            Runnable refreshAction = OptionCatalogSelector.refreshAction(serverId, input.getOptionsSource(), optionCatalogContext(input));
            ItemSelectorWidget.AsyncItemSource itemSource = () -> catalogSelectorSnapshot(input, onSelected);
            if (screen instanceof FlowGraphDesignerScreen flowEditorScreen) {
                if (input.getOptionsSource() == null || input.getOptionsSource().isBlank()) {
                    flowEditorScreen.showNodeInputSelectorAtScreen(currentOptions, selectedValue, onSelected, selectorX, selectorY);
                } else {
                    flowEditorScreen.showAsyncNodeInputSelectorAtScreen(refreshAction, itemSource,
                        selectorButtonLabel(richItems, selectedValue), selectorX, selectorY);
                }
                return;
            }
            AtomicReference<ItemSelectorWidget> selector = new AtomicReference<>();
            ItemSelectorWidget.Builder selectorBuilder = new ItemSelectorWidget.Builder(screen)
                .size(180, 220)
                .dismissOnSelect(true)
                .onClose(() -> screen.remove(selector.get()));
            boolean catalogBacked = input.getOptionsSource() != null && !input.getOptionsSource().isBlank();
            if (catalogBacked) {
                selectorBuilder.asyncItems(refreshAction, itemSource);
            }
            selector.set(selectorBuilder.build());
            if (!catalogBacked) {
                for (String option : currentOptions) {
                    selector.get().addItem(option, () -> onSelected.accept(option));
                }
            }
            selector.get().setSelectedItem(selectorButtonLabel(richItems, selectedValue));
            screen.addDrawableChild(selector.get());
            selector.get().show(selectorX, selectorY);
        });
        return button;
    }

    private ItemSelectorWidget.AsyncItemSnapshot catalogSelectorSnapshot(NodeDefinition.PinDefinition input, Consumer<String> onSelected) {
        String source = input != null ? input.getOptionsSource() : null;
        if (source == null || source.isBlank()) {
            return new ItemSelectorWidget.AsyncItemSnapshot(List.of(), false, "No Options");
        }
        Map<String, Object> context = optionCatalogContext(input);
        return OptionCatalogSelector.snapshot(serverId, source, context, List::of,
            () -> searchableSelectorValues.getOrDefault(input.getName(), resourceId(node.getInputValues().get(input.getName()))),
            onSelected, "No Options");
    }

    private List<OptionCatalogItem> resolveCatalogItems(NodeDefinition.PinDefinition input, List<String> values) {
        String source = input != null ? input.getOptionsSource() : null;
        if (source == null || source.isBlank()) {
            return List.of();
        }
        Set<String> accepted = new LinkedHashSet<>(values != null ? values : List.of());
        FlowManager manager = FlowManager.getInstance();
        ReSyncFlowClient flowClient = manager != null ? manager.ensureFlowClient(serverId) : null;
        String contextKey = flowClient != null ? flowClient.optionCatalogContextKey(optionCatalogContext(input)) : "";
        return OptionCatalogCache.getInstance().getItems(serverId, source, contextKey).stream()
            .filter(item -> item != null && item.getValue() != null && (accepted.isEmpty() || accepted.contains(item.getValue())))
            .toList();
    }

    private String catalogFailure(NodeDefinition.PinDefinition input) {
        String source = input != null ? input.getOptionsSource() : null;
        if (source == null || source.isBlank()) {
            return "";
        }
        FlowManager manager = FlowManager.getInstance();
        ReSyncFlowClient flowClient = manager != null ? manager.ensureFlowClient(serverId) : null;
        String contextKey = flowClient != null ? flowClient.optionCatalogContextKey(optionCatalogContext(input)) : "";
        OptionCatalogCache cache = OptionCatalogCache.getInstance();
        String status = cache.getStatus(serverId, source, contextKey);
        if ("available".equals(status) || "stale".equals(status) || "missing".equals(status) && !cache.hasCatalog(serverId, source, contextKey)) {
            return "";
        }
        String diagnostic = cache.getDiagnostic(serverId, source, contextKey);
        return diagnostic.isBlank() ? status : diagnostic;
    }

    private String selectedCatalogLabel(List<OptionCatalogItem> items, String selectedValue) {
        if (selectedValue == null) {
            return "";
        }
        return items.stream()
            .filter(item -> selectedValue.equals(item.getValue()))
            .map(OptionCatalogItem::getLabel)
            .findFirst()
            .orElse(selectedValue);
    }

    private String selectorButtonLabel(List<OptionCatalogItem> items, String selectedValue) {
        String label = selectedCatalogLabel(items, selectedValue);
        return isRealCatalogOption(label) ? label : "Select";
    }

    private boolean isRealCatalogOption(String value) {
        return value != null && !value.isBlank() && !"Loading".equals(value) && !"No Options".equals(value);
    }

    private String catalogSearchTerms(OptionCatalogItem item) {
        Object aliases = item.getMetadata().get("aliases");
        return String.join(" ", item.getValue(), item.getLabel(), item.getDescription(), item.getGroup(), aliases != null ? aliases.toString() : "");
    }

    private DropDownWidget<String> buildDropdown(NodeDefinition.PinDefinition input, List<String> options, String selected) {
        return new DropDownWidget.Builder<>(options)
            .selectedItem(selected)
            .onSelectionChanged(value -> {
                if (node.getInputValues() == null) {
                    node.setInputValues(new HashMap<>());
                }
                node.getInputValues().put(input.getName(), value);
                handleInputValueChanged(input);
            })
            .maxVisibleItems(8)
            .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
            .entranceAnimation(false)
            .build();
    }

    private AnimatedButton buildSelectorButton(List<String> options, String selected, int width, Consumer<String> onSelected) {
        AnimatedButton button = new AnimatedButton.Builder()
            .label(selected)
            .size(width, INPUT_WIDGET_HEIGHT)
            .entranceAnimation(false)
            .build();
        button.setAction(() -> showStringSelector(button, options, button.getMessage(), value -> {
            if (value == null || value.isBlank() || "Loading".equals(value)) {
                return;
            }
            button.setMessage(value);
            onSelected.accept(value);
        }));
        return button;
    }

    private AnimatedButton buildScreenSelectorButton(List<String> options, String selected, int width, Consumer<String> onSelected) {
        AnimatedButton button = new AnimatedButton.Builder()
            .label(selected)
            .size(width, INPUT_WIDGET_HEIGHT)
            .entranceAnimation(false)
            .build();
        button.setAction(() -> showScreenSelector(button, options, button.getMessage(), value -> {
            if (value == null || value.isBlank() || "Loading".equals(value)) {
                return;
            }
            button.setMessage(value);
            onSelected.accept(value);
        }));
        return button;
    }

    private void showStringSelector(AnimatedButton anchor, List<String> options, String selected, Consumer<String> onSelected) {
        var screen = ScreenManager.getInstance().getCurrentScreen();
        if (screen == null || anchor == null || options == null || options.isEmpty() || onSelected == null) {
            return;
        }
        if (screen instanceof FlowGraphDesignerScreen flowEditorScreen) {
            flowEditorScreen.showNodeInputSelectorAtScreen(options, selected, onSelected,
                hasLastScreenMouse ? lastScreenX : anchor.getX(), hasLastScreenMouse ? lastScreenY : anchor.getY() + anchor.getHeight());
            return;
        }
        AtomicReference<ItemSelectorWidget> selector = new AtomicReference<>();
        selector.set(new ItemSelectorWidget.Builder(screen)
            .size(180, 220)
            .dismissOnSelect(true)
            .onClose(() -> screen.remove(selector.get()))
            .build());
        for (String option : options) {
            selector.get().addItem(option, () -> onSelected.accept(option));
        }
        selector.get().setSelectedItem(selected);
        screen.addDrawableChild(selector.get());
        selector.get().show(hasLastScreenMouse ? lastScreenX : anchor.getX(), hasLastScreenMouse ? lastScreenY : anchor.getY() + anchor.getHeight());
    }

    private void showBranchSelector(AnimatedButton anchor, List<String> options, String selected, Consumer<String> onSelected) {
        var screen = ScreenManager.getInstance().getCurrentScreen();
        if (screen == null || anchor == null || options == null || options.isEmpty() || onSelected == null) {
            return;
        }
        int currentMouseX = ScreenManager.getInstance().getMouseX();
        int currentMouseY = ScreenManager.getInstance().getMouseY();
        int selectorX = currentMouseX > 0 ? currentMouseX : hasLastScreenMouse ? lastScreenX : anchor.getX();
        int selectorY = currentMouseY > 0 ? currentMouseY : hasLastScreenMouse ? lastScreenY : anchor.getY() + anchor.getHeight();
        if (screen instanceof FlowGraphDesignerScreen flowEditorScreen) {
            flowEditorScreen.showNodeInputSelectorAtScreen(options, selected, onSelected, selectorX, selectorY);
            return;
        }
        AtomicReference<ItemSelectorWidget> selector = new AtomicReference<>();
        selector.set(new ItemSelectorWidget.Builder(screen)
            .size(180, 220)
            .dismissOnSelect(true)
            .onClose(() -> screen.remove(selector.get()))
            .build());
        for (String option : options) {
            selector.get().addItem(option, () -> onSelected.accept(option));
        }
        selector.get().setSelectedItem(selected);
        screen.addDrawableChild(selector.get());
        selector.get().show(selectorX, selectorY);
    }

    private void showScreenSelector(AnimatedButton anchor, List<String> options, String selected, Consumer<String> onSelected) {
        var overlay = ScreenManager.getInstance().getPopupOverlay();
        if (overlay == null || anchor == null || options == null || options.isEmpty() || onSelected == null) {
            return;
        }
        AtomicReference<ItemSelectorWidget> selector = new AtomicReference<>();
        selector.set(new ItemSelectorWidget.Builder(overlay)
            .size(180, 220)
            .dismissOnSelect(true)
            .onClose(() -> overlay.remove(selector.get()))
            .build());
        selector.get().setLayer(900);
        selector.get().setPriority(30);
        for (String option : options) {
            selector.get().addItem(option, () -> onSelected.accept(option));
        }
        selector.get().setSelectedItem(selected);
        overlay.addDrawableChild(selector.get());
        selector.get().show(anchor.getX(), anchor.getY() + anchor.getHeight());
    }

    private List<String> resolveOptions(NodeDefinition.PinDefinition input) {
        List<String> options = input.getOptions();
        if (options != null && !options.isEmpty()) {
            return options;
        }
        String source = input.getOptionsSource();
        if (source != null && !source.isBlank()) {
            FlowManager manager = FlowManager.getInstance();
            ReSyncFlowClient flowClient = manager != null ? manager.ensureFlowClient(serverId) : null;
            Map<String, Object> context = optionCatalogContext(input);
            String contextKey = flowClient != null ? flowClient.optionCatalogContextKey(context) : "";
            requestOptionCatalog(source, context);
            List<String> values = OptionCatalogCache.getInstance().getValues(serverId, source, contextKey);
            return values;
        }
        return List.of();
    }

    private List<String> fallbackSearchableOptions(Object currentValue, String defaultValue) {
        String value = resourceId(currentValue);
        if (value.isBlank()) {
            value = defaultValue;
        }
        return value == null || value.isBlank() ? List.of() : List.of(value);
    }

    private void requestOptionCatalog(String source, Map<String, Object> context) {
        requestOptionCatalog(source, context, false);
    }

    private void requestOptionCatalog(String source, Map<String, Object> context, boolean forceRefresh) {
        if (forceRefresh) {
            OptionCatalogLoader.refresh(serverId, source, context);
        } else {
            OptionCatalogLoader.preload(serverId, source, context);
        }
    }

    private Map<String, Object> optionCatalogContext(NodeDefinition.PinDefinition input) {
        Map<String, Object> context = new HashMap<>();
        if (node.getInputValues() != null) {
            context.putAll(node.getInputValues());
        }
        if (ScreenManager.getInstance().getCurrentScreen() instanceof FlowGraphDesignerScreen designer) {
            context.putAll(designer.optionCatalogContext());
        }
        context.put("$nodeType", node.getType() != null ? node.getType() : "");
        context.put("$pin", input != null && input.getName() != null ? input.getName() : "");
        if (input != null && input.getName() != null) {
            context.remove(input.getName());
        }
        return context;
    }

    private String resolveSelected(List<String> options, Object currentValue, String defaultValue) {
        String selected = resourceId(currentValue);
        if (selected.isBlank()) {
            selected = defaultValue;
        }
        if (selected == null || selected.isBlank()) {
            selected = options.isEmpty() ? "" : options.getFirst();
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(selected)) {
                return option;
            }
        }
        return selected;
    }

    private NodeDefinition.WidgetType resolveWidgetType(NodeDefinition.PinDefinition input) {
        if (input.getWidgetType() != null && input.getWidgetType() != NodeDefinition.WidgetType.AUTO) {
            return input.getWidgetType();
        }
        String optionsSource = input.getOptionsSource();
        if (optionsSource != null && !optionsSource.isBlank()) {
            NodeRegistry registry = NodeRegistry.getInstance();
            FlowOptionSourceMetadata meta = registry != null ? registry.getServerOptionSource(serverId, optionsSource) : null;
            if (meta != null) {
                String metadataWidget = meta.getWidgetType();
                if (metadataWidget != null && !metadataWidget.isBlank()) {
                    try {
                        NodeDefinition.WidgetType resolved = NodeDefinition.WidgetType.valueOf(metadataWidget.trim().toUpperCase(Locale.ROOT));
                        if (resolved != NodeDefinition.WidgetType.AUTO) {
                            return resolved;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!meta.isSearchable()) {
                    return NodeDefinition.WidgetType.DROPDOWN;
                }
            }
            return NodeDefinition.WidgetType.SEARCHABLE_LIST;
        }
        if (input.getDataType() == FlowDataType.BOOLEAN) {
            return NodeDefinition.WidgetType.TOGGLE;
        }
        return NodeDefinition.WidgetType.TEXT;
    }

    private void seedDefaultInputValues() {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        for (NodeDefinition.PinDefinition input : inputs) {
            if (input.getDefaultValue() != null && !node.getInputValues().containsKey(input.getName())) {
                node.getInputValues().put(input.getName(), convertLiteralValue(input.getDefaultValue(), input));
            }
        }
    }

    private void updatePinVisibility() {
        visibleInputs.clear();
        if (node.getInputValues() == null) {
            for (NodeDefinition.PinDefinition input : inputs) {
                if (shouldShowInputPin(input)) {
                    visibleInputs.add(input);
                }
            }
            createOutputWidgets();
            return;
        }
        seedFlowBranches();
        for (NodeDefinition.PinDefinition input : inputs) {
            boolean shouldShow = shouldShowInputPin(input) && evaluateVisibleWhen(input.getVisibleWhen());
            Widget widget = inputWidgets.get(input.getName());
            if (widget != null) {
                widget.setVisible(shouldShow);
            }
            if (shouldShow) {
                visibleInputs.add(input);
            }
        }
        createOutputWidgets();
        updateSize();
        updateInputWidgetPositions();
        updateOutputWidgetPositions();
    }

    private void seedFlowBranches() {
        List<NodeDefinition.PinDefinition> flowOutputs = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : outputs) {
            if (isFlowOutput(output) && evaluateVisibleWhen(output.getVisibleWhen())) {
                flowOutputs.add(output);
            }
        }
        if (flowOutputs.size() > 2) {
            node.getInputValues().put(FLOW_BRANCHES_KEY, resolveFlowBranches(flowOutputs));
        }
    }

    private boolean evaluateVisibleWhen(Map<String, String> visibleWhen) {
        if (visibleWhen == null || visibleWhen.isEmpty()) {
            return true;
        }
        if (node.getInputValues() == null) {
            return true;
        }
        for (Map.Entry<String, String> condition : visibleWhen.entrySet()) {
            Object actualValue = node.getInputValues().get(condition.getKey());
            boolean matches = false;
            for (String expected : condition.getValue().split(",")) {
                if (matchesVisibleValue(actualValue, expected.trim())) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesVisibleValue(Object actualValue, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        if (actualValue instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value != null && matchesVisibleValue(value, expected)) {
                    return true;
                }
            }
            return false;
        }
        String actual = actualValue != null ? actualValue.toString().trim() : "";
        if (expected.endsWith("*")) {
            String prefix = expected.substring(0, expected.length() - 1);
            return actual.regionMatches(true, 0, prefix, 0, prefix.length());
        }
        return actual.equalsIgnoreCase(expected);
    }

    public void refreshInputWidgets() {
        resetDefinitionPins();
        applyFunctionParameterPins();
        applyAutomationDefinitionPins();
        applyFunctionCallSignaturePins();
        applyAdvancedInputPins();
        applyAdvancedOutputPins();
        applyRemovedOptionalInputs();
        updateAddInputButtons();
        updateStringTemplatePins();
        preflightOptionCatalogs();
        inputWidgets.keySet().removeIf(pinName -> !isInputWidgetEligible(findInputDefinition(pinName)));
        createInputWidgets();
        createOutputWidgets();
        updateSize();
    }

    private void resetDefinitionPins() {
        inputs.clear();
        outputs.clear();
        if (definition != null) {
            inputs.addAll(definition.getInputs());
            outputs.addAll(definition.getOutputs());
        }
    }

    private void applyFunctionCallSignaturePins() {
        if (!isFunctionSignatureNode()) {
            return;
        }
        boolean scheduleNode = AUTOMATION_SCHEDULE_ID.equals(node.getType());
        String functionId = scheduleNode ? scheduledFunctionId()
            : node.getInputValues() != null ? resourceId(node.getInputValues().get("function")) : "";
        NodeRegistry registry = NodeRegistry.getInstance();
        NodeDefinition signature = registry != null && !functionId.isBlank()
            ? registry.getDefinition(serverId, CUSTOM_FUNCTION_NODE_PREFIX + functionId)
            : null;
        boolean dynamic = isInputWired(scheduleNode ? "schedule" : "function");
        List<NodeDefinition.PinDefinition> baseOutputs = new ArrayList<>(outputs);
        FunctionCallPinModel.ResolvedPins resolved = FunctionCallPinModel.resolve(inputs, outputs, signature, dynamic, callParameterPins());
        inputs.clear();
        inputs.addAll(resolved.inputs());
        outputs.clear();
        outputs.addAll(scheduleNode ? baseOutputs : resolved.outputs());
        syncFunctionSignature(resolved.signatureInputs(), scheduleNode ? Map.of() : resolved.signatureOutputs());
    }

    private void applyAutomationDefinitionPins() {
        if (!AUTOMATION_VARIABLE_ID.equals(node.getType()) && !VARIABLE_CHANGED_EVENT_ID.equals(node.getType())) {
            OptionCatalogItem item = automationCatalogItem();
            applyAutomationOwnerPin(item);
            if (item != null && "automation.timer".equals(node.getType())) {
                replacePinDefault("duration", item.getMetadata().get("defaultDuration"));
                String unit = String.valueOf(item.getMetadata().getOrDefault("defaultUnit", "seconds"));
                replacePinDefault("unit", Character.toUpperCase(unit.charAt(0)) + unit.substring(1).toLowerCase(Locale.ROOT));
            }
            if (item != null && !AUTOMATION_SCHEDULE_ID.equals(node.getType())) {
                removeUnknownFunctionConnections();
            }
            return;
        }
        OptionCatalogItem item = catalogItem(VARIABLE_CATALOG, selectedResourceId("variable"));
        if (item == null) {
            return;
        }
        String typeId = String.valueOf(item.getMetadata().getOrDefault("valueType", "any"));
        FlowTypeRef typeRef = FlowTypeRef.parse(typeId);
        FlowDataType dataType = FlowDataType.fromString(typeRef.getTypeId());
        replacePinType(inputs, Set.of("value"), dataType, typeRef);
        replacePinType(outputs, Set.of("value", "old_value", "new_value"), dataType, typeRef);
        if (!FlowDataType.NUMBER.isAssignableFrom(dataType)) {
            replaceActionOptions(List.of("Get", "Set", "Delete", "Exists", "List"));
        }
        applyAutomationOwnerPin(item);
        removeUnknownFunctionConnections();
    }

    private void applyAutomationOwnerPin(OptionCatalogItem item) {
        if (item == null) {
            return;
        }
        String scope = String.valueOf(item.getMetadata().getOrDefault("scope", "server")).toLowerCase(Locale.ROOT);
        if ("flow".equals(scope) || "server".equals(scope)) {
            inputs.removeIf(pin -> "owner".equals(pin.getName()));
            return;
        }
        FlowDataType ownerType = switch (scope) {
            case "player" -> FlowDataType.PLAYER;
            case "entity" -> FlowDataType.ENTITY;
            default -> FlowDataType.STRING;
        };
        replacePinType(inputs, Set.of("owner"), ownerType, FlowTypeRef.simple(ownerType.getId()));
    }

    private void replaceActionOptions(List<String> options) {
        Object selected = node.getInputValues() != null ? node.getInputValues().get("action") : null;
        if (selected != null && options.stream().noneMatch(option -> option.equalsIgnoreCase(selected.toString()))) {
            node.getInputValues().put("action", options.getFirst());
        }
        for (int index = 0; index < inputs.size(); index++) {
            NodeDefinition.PinDefinition pin = inputs.get(index);
            if ("action".equals(pin.getName())) {
                inputs.set(index, new NodeDefinition.PinDefinition(pin.getName(), pin.getType(), pin.getDirection(), pin.getDataType(),
                    pin.getWidgetType(), options, pin.getOptionsSource(), pin.getDefaultValue(), pin.getConstraints(), pin.getVisibleWhen(),
                    pin.getDescription(), pin.isOptional(), pin.getTypeRef(), pin.getRepeatable()));
                return;
            }
        }
    }

    private void replacePinType(List<NodeDefinition.PinDefinition> pins, Set<String> names, FlowDataType dataType, FlowTypeRef typeRef) {
        for (int index = 0; index < pins.size(); index++) {
            NodeDefinition.PinDefinition pin = pins.get(index);
            if (names.contains(pin.getName())) {
                pins.set(index, new NodeDefinition.PinDefinition(pin.getName(), pin.getType(), pin.getDirection(), dataType,
                    pin.getWidgetType(), pin.getOptions(), pin.getOptionsSource(), pin.getDefaultValue(), pin.getConstraints(),
                    pin.getVisibleWhen(), pin.getDescription(), pin.isOptional(), typeRef, pin.getRepeatable()));
            }
        }
    }

    private void replacePinDefault(String name, Object value) {
        if (value == null) {
            return;
        }
        for (int index = 0; index < inputs.size(); index++) {
            NodeDefinition.PinDefinition pin = inputs.get(index);
            if (name.equals(pin.getName())) {
                inputs.set(index, new NodeDefinition.PinDefinition(pin.getName(), pin.getType(), pin.getDirection(), pin.getDataType(),
                    pin.getWidgetType(), pin.getOptions(), pin.getOptionsSource(), String.valueOf(value), pin.getConstraints(), pin.getVisibleWhen(),
                    pin.getDescription(), pin.isOptional(), pin.getTypeRef(), pin.getRepeatable()));
                return;
            }
        }
    }

    private String scheduledFunctionId() {
        OptionCatalogItem item = scheduleCatalogItem();
        if (item == null || !"function".equalsIgnoreCase(String.valueOf(item.getMetadata().get("targetType")))) {
            return "";
        }
        Object targetId = item.getMetadata().get("targetId");
        return targetId != null ? targetId.toString() : "";
    }

    private OptionCatalogItem scheduleCatalogItem() {
        if (!AUTOMATION_SCHEDULE_ID.equals(node.getType()) && !"automation.scheduled_task".equals(node.getType())
            && !"event.scheduled_task".equals(node.getType()) && !"event.schedule".equals(node.getType())) {
            return null;
        }
        return catalogItem(SCHEDULE_CATALOG, selectedResourceId("schedule"));
    }

    private OptionCatalogItem automationCatalogItem() {
        return switch (node.getType()) {
            case "automation.timer", "event.timer" -> catalogItem(TIMER_CATALOG, selectedResourceId("timer"));
            case AUTOMATION_SCHEDULE_ID, "automation.scheduled_task", "event.scheduled_task", "event.schedule" -> scheduleCatalogItem();
            default -> null;
        };
    }

    private String selectedResourceId(String pin) {
        return node.getInputValues() != null ? resourceId(node.getInputValues().get(pin)) : "";
    }

    private OptionCatalogItem catalogItem(String source, String value) {
        if (serverId == null || value == null || value.isBlank()) {
            return null;
        }
        return OptionCatalogCache.getInstance().getItems(serverId, source).stream()
            .filter(item -> value.equals(item.getValue())).findFirst().orElse(null);
    }

    private void syncFunctionSignature(Map<String, String> nextInputs, Map<String, String> nextOutputs) {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        Map<String, String> previousInputs = storedSignatureTypes("inputs");
        Map<String, String> previousOutputs = storedSignatureTypes("outputs");
        boolean genericInputAvailable = inputs.stream().anyMatch(input -> "arguments".equals(input.getName()));
        migrateFunctionPins(FunctionCallPinModel.pinMigrations(previousInputs, nextInputs, true, genericInputAvailable), true);
        migrateFunctionPins(FunctionCallPinModel.pinMigrations(previousOutputs, nextOutputs, false, false), false);
        removeUnknownFunctionConnections();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("inputs", nextInputs);
        snapshot.put("outputs", nextOutputs);
        node.getInputValues().put(FUNCTION_SIGNATURE_KEY, snapshot);
        node.getInputValues().remove(FUNCTION_SIGNATURE_ISSUES_KEY);
    }

    private void migrateFunctionPins(Map<String, String> migrations, boolean input) {
        if (migrations.isEmpty()) {
            return;
        }
        if (graph != null && graph.getConnections() != null) {
            for (FlowConnection connection : graph.getConnections()) {
                if (input && nodeId.equals(connection.getTargetNodeId())) {
                    String target = migrations.get(connection.getTargetPin());
                    if (target != null) {
                        connection.setTargetPin(target);
                    }
                } else if (!input && nodeId.equals(connection.getSourceNodeId())) {
                    String source = migrations.get(connection.getSourcePin());
                    if (source != null) {
                        connection.setSourcePin(source);
                    }
                }
                if (input && nodeId.equals(connection.getEditorSourceNodeId())) {
                    for (Map.Entry<String, String> migration : migrations.entrySet()) {
                        if (passthroughOutputPin(migration.getKey()).equals(connection.getEditorSourcePin())) {
                            connection.setEditorSourcePin(passthroughOutputPin(migration.getValue()));
                        }
                    }
                }
            }
        }
        if (input && graph != null && graph.getEditorPassthroughs() != null) {
            for (FlowGraph.EditorPassthrough passthrough : graph.getEditorPassthroughs()) {
                if (nodeId.equals(passthrough.getNodeId()) && migrations.containsKey(passthrough.getInputPin())) {
                    passthrough.setInputPin(migrations.get(passthrough.getInputPin()));
                }
            }
        }
        if (input) {
            for (Map.Entry<String, String> migration : migrations.entrySet()) {
                if (node.getInputValues().containsKey(migration.getKey()) && !node.getInputValues().containsKey(migration.getValue())) {
                    node.getInputValues().put(migration.getValue(), node.getInputValues().remove(migration.getKey()));
                }
            }
        }
    }

    private void removeUnknownFunctionConnections() {
        if (graph == null || graph.getConnections() == null) {
            return;
        }
        Set<String> inputNames = inputs.stream().map(NodeDefinition.PinDefinition::getName).collect(Collectors.toSet());
        Set<String> outputNames = outputs.stream().map(NodeDefinition.PinDefinition::getName).collect(Collectors.toSet());
        graph.getConnections().removeIf(connection ->
            nodeId.equals(connection.getTargetNodeId()) && !inputNames.contains(connection.getTargetPin())
                || nodeId.equals(connection.getSourceNodeId()) && !outputNames.contains(connection.getSourcePin()));
        for (FlowConnection connection : graph.getConnections()) {
            if (nodeId.equals(connection.getEditorSourceNodeId()) && isPassthroughOutputPin(connection.getEditorSourcePin())
                && !inputNames.contains(passthroughInputPin(connection.getEditorSourcePin()))) {
                connection.setEditorSourceNodeId(null);
                connection.setEditorSourcePin(null);
            }
        }
        if (graph.getEditorPassthroughs() != null) {
            graph.getEditorPassthroughs().removeIf(passthrough ->
                nodeId.equals(passthrough.getNodeId()) && !inputNames.contains(passthrough.getInputPin()));
        }
    }

    private Map<String, String> storedSignatureTypes(String direction) {
        if (node.getInputValues() == null || !(node.getInputValues().get(FUNCTION_SIGNATURE_KEY) instanceof Map<?, ?> snapshot)
            || !(snapshot.get(direction) instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, String> signature = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                signature.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return signature;
    }

    private boolean isFunctionCallNode() {
        return CALL_FUNCTION_ID.equals(node.getType()) || CALL_FUNCTION_CANONICAL_ID.equals(node.getType());
    }

    private boolean isFunctionSignatureNode() {
        return isFunctionCallNode() || AUTOMATION_SCHEDULE_ID.equals(node.getType());
    }

    private void loadCallParameters() {
        if (!isFunctionCallNode() || node.getInputValues() == null || !(node.getInputValues().get(CALL_PARAMETERS_KEY) instanceof Iterable<?> values)) {
            return;
        }
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> entry) || entry.get("name") == null || entry.get("type") == null) {
                continue;
            }
            String name = entry.get("name").toString().trim();
            if (name.isBlank()) {
                continue;
            }
            try {
                FlowTypeRef typeRef = FlowTypeRef.parse(entry.get("type").toString());
                FlowGraph.FunctionParameter parameter = new FlowGraph.FunctionParameter(name, FlowDataType.fromString(typeRef.getTypeId()));
                parameter.setTypeRef(typeRef);
                callParameters.add(parameter);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveCallParameters() {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        if (callParameters.isEmpty()) {
            node.getInputValues().remove(CALL_PARAMETERS_KEY);
            return;
        }
        List<Map<String, String>> values = new ArrayList<>();
        for (FlowGraph.FunctionParameter parameter : callParameters) {
            if (isValidFunctionParameter(parameter)) {
                Map<String, String> value = new LinkedHashMap<>();
                value.put("name", parameter.getName());
                value.put("type", parameter.getTypeRef().toString());
                values.add(value);
            }
        }
        node.getInputValues().put(CALL_PARAMETERS_KEY, values);
    }

    private List<NodeDefinition.PinDefinition> callParameterPins() {
        if (callParameters.isEmpty()) {
            return List.of();
        }
        List<NodeDefinition.PinDefinition> pins = new ArrayList<>();
        for (FlowGraph.FunctionParameter parameter : callParameters) {
            if (!isValidFunctionParameter(parameter)) {
                continue;
            }
            pins.add(new NodeDefinition.PinBuilder(
                parameter.getName(),
                NodeDefinition.PinType.DATA,
                NodeDefinition.PinDirection.INPUT,
                parameter.getType() != null ? parameter.getType() : FlowDataType.ANY
            ).typeRef(parameter.getTypeRef()).build());
        }
        return pins;
    }

    private String nodeTitle() {
        return definition != null ? definition.getDisplayName() : "Loading";
    }

    public void refreshOptionCatalog(String sourceId) {
        boolean refreshAll = sourceId == null || sourceId.isBlank();
        for (NodeDefinition.PinDefinition input : inputs) {
            if (input.getOptionsSource() == null || input.getOptionsSource().isBlank()
                || !refreshAll && !sourceId.equals(input.getOptionsSource())) {
                continue;
            }
            Widget widget = inputWidgets.get(input.getName());
            if (widget instanceof DropDownWidget<?> rawDropdown) {
                @SuppressWarnings("unchecked")
                DropDownWidget<String> dropdown = (DropDownWidget<String>) rawDropdown;
                List<String> options = new ArrayList<>(resolveOptions(input));
                String selected = resourceId(node.getInputValues().get(input.getName()));
                if (selected.isBlank()) {
                    selected = input.getDefaultValue();
                }
                if (selected != null && !selected.isBlank() && !options.contains(selected)) {
                    options.addFirst(selected);
                }
                dropdown.setItems(options, selected);
            } else if (widget instanceof AnimatedButton button && resolveWidgetType(input) == NodeDefinition.WidgetType.SEARCHABLE_LIST) {
                String selected = searchableSelectorValues.getOrDefault(input.getName(), resourceId(node.getInputValues().get(input.getName())));
                List<String> options = resolveOptions(input);
                button.setMessage(selectorButtonLabel(resolveCatalogItems(input, options), selected));
            } else if (widget instanceof TextInputWidget && resolveWidgetType(input) != NodeDefinition.WidgetType.TEXT && !resolveOptions(input).isEmpty()) {
                inputWidgets.remove(input.getName());
                Widget replacement = buildWidgetForPin(input);
                if (replacement != null) {
                    inputWidgets.put(input.getName(), replacement);
                }
            }
        }
        updateSize();
        updateInputWidgetPositions();
    }

    private void applyAdvancedInputPins() {
        for (NodeDefinition.PinDefinition base : repeatableInputDefinitions()) {
            inputs.removeIf(input -> repeatableInputIndex(base, input.getName()) > 0);
            int insertionIndex = repeatableInsertionIndex(base);
            int count = repeatableInputCount(base);
            for (int index = 1; index <= count; index++) {
                inputs.add(insertionIndex++, repeatableInput(base, index));
            }
        }
    }

    private void applyAdvancedOutputPins() {
        for (NodeDefinition.PinDefinition base : repeatableOutputDefinitions()) {
            outputs.removeIf(output -> repeatableInputIndex(base, output.getName()) > 0);
            int insertionIndex = repeatableOutputInsertionIndex(base);
            int count = repeatableInputCount(base);
            Set<String> removed = removedOptionalInputNames();
            for (int index = 1; index <= count; index++) {
                String outputName = index == 1 ? base.getName() : base.getName() + "_" + index;
                if (!removed.contains(outputName)) {
                    outputs.add(insertionIndex++, repeatableOutput(base, index));
                }
            }
        }
    }

    private NodeDefinition.PinDefinition repeatableOutput(NodeDefinition.PinDefinition base, int index) {
        return new NodeDefinition.PinDefinition(
            index == 1 ? base.getName() : base.getName() + "_" + index,
            base.getType(),
            NodeDefinition.PinDirection.OUTPUT,
            base.getDataType(),
            base.getWidgetType(),
            base.getOptions(),
            base.getOptionsSource(),
            base.getDefaultValue(),
            base.getConstraints(),
            base.getVisibleWhen(),
            base.getDescription(),
            index > base.getRepeatable().getMinItems(),
            base.getTypeRef(),
            base.getRepeatable()
        );
    }

    private int repeatableOutputInsertionIndex(NodeDefinition.PinDefinition base) {
        List<NodeDefinition.PinDefinition> definitions = definition != null ? definition.getOutputs() : List.of();
        int definitionIndex = definitions.indexOf(base);
        for (int index = definitionIndex + 1; index < definitions.size(); index++) {
            String nextName = definitions.get(index).getName();
            for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
                if (nextName.equals(outputs.get(outputIndex).getName())) {
                    return outputIndex;
                }
            }
        }
        return outputs.size();
    }

    private NodeDefinition.PinDefinition repeatableInput(NodeDefinition.PinDefinition base, int index) {
        return new NodeDefinition.PinDefinition(
            index == 1 ? base.getName() : base.getName() + "_" + index,
            NodeDefinition.PinType.DATA,
            NodeDefinition.PinDirection.INPUT,
            base.getDataType(),
            base.getWidgetType(),
            base.getOptions(),
            base.getOptionsSource(),
            base.getDefaultValue(),
            base.getConstraints(),
            base.getVisibleWhen(),
            base.getDescription(),
            index > base.getRepeatable().getMinItems(),
            base.getTypeRef(),
            base.getRepeatable()
        );
    }

    private int repeatableInsertionIndex(NodeDefinition.PinDefinition base) {
        List<NodeDefinition.PinDefinition> definitions = definition != null ? definition.getInputs() : List.of();
        int definitionIndex = definitions.indexOf(base);
        for (int index = definitionIndex + 1; index < definitions.size(); index++) {
            String nextName = definitions.get(index).getName();
            for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                if (nextName.equals(inputs.get(inputIndex).getName())) {
                    return inputIndex;
                }
            }
        }
        return inputs.size();
    }

    private void applyRemovedOptionalInputs() {
        Set<String> removed = removedOptionalInputNames();
        inputs.removeIf(input -> input.isOptional() && removed.contains(input.getName()));
    }

    private List<NodeDefinition.PinDefinition> repeatableInputDefinitions() {
        if (definition == null || definition.getInputs() == null) {
            return List.of();
        }
        return definition.getInputs().stream().filter(input -> input.getRepeatable() != null).toList();
    }

    private List<NodeDefinition.PinDefinition> repeatableOutputDefinitions() {
        if (definition == null || definition.getOutputs() == null) {
            return List.of();
        }
        return definition.getOutputs().stream().filter(output -> output.getRepeatable() != null).toList();
    }

    private NodeDefinition.PinDefinition repeatableInputDefinition(String pinName) {
        return repeatableInputDefinitions().stream().filter(base -> repeatableInputIndex(base, pinName) > 0).findFirst().orElse(null);
    }

    private int repeatableInputCount(NodeDefinition.PinDefinition base) {
        NodeDefinition.RepeatablePin repeatable = base.getRepeatable();
        int minimum = repeatable != null ? repeatable.getMinItems() : 1;
        int maximum = repeatable != null ? repeatable.getMaxItems() : minimum;
        if (node.getInputValues() == null) {
            return minimum;
        }
        Object stored = node.getInputValues().get(repeatableCountKey(base));
        if (stored == null && "permissions".equals(repeatable.getGroupId())) {
            stored = node.getInputValues().get(LEGACY_PERMISSION_COUNT_KEY);
        }
        if (stored instanceof Number number) {
            return Math.clamp(number.intValue(), minimum, maximum);
        }
        if (stored != null) {
            try {
                return Math.clamp(Integer.parseInt(stored.toString()), minimum, maximum);
            } catch (NumberFormatException ignored) {
            }
        }
        return minimum;
    }

    private int repeatableInputIndex(NodeDefinition.PinDefinition base, String pinName) {
        if (base.getName().equals(pinName)) {
            return 1;
        }
        String prefix = base.getName() + "_";
        if (pinName == null || !pinName.startsWith(prefix)) {
            return -1;
        }
        try {
            return Integer.parseInt(pinName.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void updateAddInputButtons() {
        Set<String> activeGroups = new LinkedHashSet<>();
        for (NodeDefinition.PinDefinition base : repeatableInputDefinitions()) {
            String groupId = base.getRepeatable().getGroupId();
            if (!evaluateVisibleWhen(base.getVisibleWhen())) {
                continue;
            }
            if (activeRepeatableInputCount(base) >= base.getRepeatable().getMaxItems()) {
                continue;
            }
            activeGroups.add(groupId);
            String itemLabel = base.getRepeatable().getItemLabel();
            addInputButtons.computeIfAbsent(groupId, ignored -> new AnimatedButton.Builder()
                .label("Add " + (itemLabel != null && !itemLabel.isBlank() ? itemLabel : "Value"))
                .onClick(() -> addRepeatableInput(base))
                .animateElevation(false)
                .entranceAnimation(false)
                .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                .build()).visible = true;
        }
        addInputButtons.entrySet().removeIf(entry -> !activeGroups.contains(entry.getKey()));
    }

    private void addRepeatableInput(NodeDefinition.PinDefinition base) {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        Set<String> removed = removedOptionalInputNames();
        int count = repeatableInputCount(base);
        for (int index = 1; index <= count; index++) {
            String pinName = index == 1 ? base.getName() : base.getName() + "_" + index;
            if (removed.remove(pinName)) {
                saveRemovedOptionalInputNames(removed);
                refreshInputWidgets();
                return;
            }
        }
        int nextIndex = Math.min(base.getRepeatable().getMaxItems(), repeatableInputCount(base) + 1);
        node.getInputValues().put(repeatableCountKey(base), nextIndex);
        node.getInputValues().remove(LEGACY_PERMISSION_COUNT_KEY);
        activateRepeatableOutput(base.getRepeatable().getGroupId(), nextIndex);
        refreshInputWidgets();
    }

    private void activateRepeatableOutput(String groupId, int index) {
        NodeDefinition.PinDefinition output = repeatableOutputDefinitions().stream()
            .filter(candidate -> groupId.equals(candidate.getRepeatable().getGroupId()))
            .findFirst()
            .orElse(null);
        if (output == null) {
            return;
        }
        String outputName = index == 1 ? output.getName() : output.getName() + "_" + index;
        Object stored = node.getInputValues().get(FLOW_BRANCHES_KEY);
        List<String> branches = new ArrayList<>();
        if (stored instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value != null && !value.toString().isBlank()) {
                    branches.add(value.toString());
                }
            }
        }
        if (!branches.contains(outputName)) {
            branches.add(outputName);
            node.getInputValues().put(FLOW_BRANCHES_KEY, branches);
        }
    }

    private int activeRepeatableInputCount(NodeDefinition.PinDefinition base) {
        Set<String> removed = removedOptionalInputNames();
        int active = 0;
        for (int index = 1; index <= repeatableInputCount(base); index++) {
            String pinName = index == 1 ? base.getName() : base.getName() + "_" + index;
            if (!removed.contains(pinName)) {
                active++;
            }
        }
        return active;
    }

    public boolean isRepeatableInputPin(String pinName) {
        return repeatableInputDefinition(pinName) != null;
    }

    private String repeatableCountKey(NodeDefinition.PinDefinition base) {
        return REPEATABLE_COUNT_PREFIX + base.getRepeatable().getGroupId();
    }

    private Set<String> removedOptionalInputNames() {
        Set<String> removed = new LinkedHashSet<>();
        if (node.getInputValues() == null) {
            return removed;
        }
        Object stored = node.getInputValues().get(REMOVED_OPTIONAL_INPUTS_KEY);
        if (stored instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value != null && !value.toString().isBlank()) {
                    removed.add(value.toString());
                }
            }
        }
        return removed;
    }

    private void saveRemovedOptionalInputNames(Set<String> removed) {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        if (removed.isEmpty()) {
            node.getInputValues().remove(REMOVED_OPTIONAL_INPUTS_KEY);
        } else {
            node.getInputValues().put(REMOVED_OPTIONAL_INPUTS_KEY, new ArrayList<>(removed));
        }
    }

    public boolean isOptionalInputPin(String pinName) {
        NodeDefinition.PinDefinition input = findInputDefinition(pinName);
        return input != null && input.getDirection() == NodeDefinition.PinDirection.INPUT && input.isOptional();
    }

    public boolean removeOptionalInputPin(String pinName) {
        if (!isOptionalInputPin(pinName)) {
            return false;
        }
        Set<String> removed = removedOptionalInputNames();
        removed.add(pinName);
        saveRemovedOptionalInputNames(removed);
        if (node.getInputValues() != null) {
            node.getInputValues().remove(pinName);
        }
        refreshInputWidgets();
        return true;
    }

    private void applyFunctionParameterPins() {
        if (!isFunctionStartNode() && !isFunctionEndNode()) {
            return;
        }
        inputs.clear();
        outputs.clear();

        NodeDefinition.PinDefinition inputFlowPin = new NodeDefinition.PinDefinition("flow", NodeDefinition.PinType.FLOW, NodeDefinition.PinDirection.INPUT, FlowDataType.EXECUTION);
        NodeDefinition.PinDefinition outputFlowPin = new NodeDefinition.PinDefinition("flow", NodeDefinition.PinType.FLOW, NodeDefinition.PinDirection.OUTPUT, FlowDataType.EXECUTION);

        inputs.add(inputFlowPin);
        outputs.add(outputFlowPin);

        if (graph == null) {
            return;
        }

        if (isFunctionStartNode() && graph.getFunctionInputs() != null) {
            for (FlowGraph.FunctionParameter parameter : graph.getFunctionInputs()) {
                if (!isValidFunctionParameter(parameter)) {
                    continue;
                }
                outputs.add(new NodeDefinition.PinDefinition(parameter.getName(), NodeDefinition.PinType.DATA, NodeDefinition.PinDirection.OUTPUT,
                    parameter.getType(), parameter.getTypeRef()));
            }
        }

        if (isFunctionEndNode() && graph.getFunctionOutputs() != null) {
            for (FlowGraph.FunctionParameter parameter : graph.getFunctionOutputs()) {
                if (!isValidFunctionParameter(parameter)) {
                    continue;
                }
                inputs.add(new NodeDefinition.PinDefinition(parameter.getName(), NodeDefinition.PinType.DATA, NodeDefinition.PinDirection.INPUT,
                    parameter.getType(), parameter.getTypeRef()));
            }
        }
    }

    private boolean isFunctionStartNode() {
        return isFunctionStartType(node.getType());
    }

    private boolean isFunctionEndNode() {
        return isFunctionEndType(node.getType());
    }

    private static boolean isFunctionStartType(String type) {
        return FUNCTION_START_ID.equals(type) || FUNCTION_START_MIGRATED_ID.equals(type) || FUNCTION_START_CANONICAL_ID.equals(type);
    }

    private static boolean isFunctionEndType(String type) {
        return FUNCTION_END_ID.equals(type) || FUNCTION_END_MIGRATED_ID.equals(type) || FUNCTION_END_CANONICAL_ID.equals(type);
    }

    private boolean isValidFunctionParameter(FlowGraph.FunctionParameter parameter) {
        return parameter != null && parameter.getName() != null && !parameter.getName().isBlank();
    }

    private List<FlowDataType> getSupportedFunctionTypes() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry != null) {
            List<FlowDataType> serverTypes = registry.getServerDataTypes(serverId);
            if (!serverTypes.isEmpty()) {
                return serverTypes.stream().filter(this::isUserFacingFunctionType).toList();
            }
        }
        return FlowDataType.values().stream().filter(this::isUserFacingFunctionType).toList();
    }

    private boolean isUserFacingFunctionType(FlowDataType type) {
        return type != null && !"resource_reference".equals(type.getId());
    }

    public List<FlowGraph.FunctionParameter> getFunctionParameterList() {
        if (isFunctionCallNode()) {
            return callParameters;
        }
        if (graph == null) {
            return null;
        }
        if (isFunctionStartNode()) {
            return graph.getFunctionInputs();
        }
        if (isFunctionEndNode()) {
            return graph.getFunctionOutputs();
        }
        return null;
    }

    public void setLastScreenMouse(int x, int y) {
        this.lastScreenX = x;
        this.lastScreenY = y;
        this.hasLastScreenMouse = true;
    }

    public void showParamContextMenu() {
        var currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen == null) return;

        List<FlowGraph.FunctionParameter> params = getFunctionParameterList();
        if (params == null) return;

        String parameterName = isFunctionCallNode() ? "Argument" : "Parameter";
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(currentScreen);
        builder.addHeaderButton("add.png", this::showAddFunctionParameterPopup, "Add " + parameterName, ThemeManager.getAccent("nice"));
        for (FlowGraph.FunctionParameter p : params) {
            if (p != null && p.getName() != null) {
                String name = p.getName();
                builder.addItem("Remove: " + name, () -> removeFunctionParameter(name), name, ThemeManager.getAccent("danger"));
            }
        }
        ContextMenuWidget menu = builder.build();
        currentScreen.addDrawableChild(menu);
        menu.show(lastScreenX, lastScreenY);
    }

    public void showAddFunctionParameterPopup() {
        boolean callArgument = isFunctionCallNode();
        PopupWidget.Builder builder = new PopupWidget.Builder(callArgument ? "Add Argument" : "Add Parameter").setResizable(false);
        TextInputWidget nameInput = new TextInputWidget.Builder()
            .placeholder(callArgument ? "argument_name" : "parameter_name")
            .size(200, 20)
            .build();
        List<FlowDataType> types = getSupportedFunctionTypes().stream()
            .filter(type -> !callArgument || type != FlowDataType.ANY && type != FlowDataType.EXECUTION)
            .toList();
        FlowDataType defaultType = callArgument ? FlowDataType.STRING : FlowDataType.ANY;
        FlowDataType[] selectedType = new FlowDataType[]{defaultType};
        FlowTypeRef[] selectedTypeRef = new FlowTypeRef[]{FlowTypeRef.simple(defaultType.getId())};
        Stream<String> genericTypes = callArgument
            ? Stream.of("list<string>", "set<string>", "map<string,string>", "optional<string>", "result<string>")
            : Stream.of("list<any>", "list<string>", "set<any>", "set<string>", "map<string,any>", "optional<any>", "result<any>");
        List<String> typeOptions = Stream.concat(types.stream().map(FlowDataType::getId), genericTypes).distinct().toList();
        AnimatedButton typeButton = buildScreenSelectorButton(typeOptions, selectedType[0].getId(), 200, value -> {
            selectedTypeRef[0] = FlowTypeRef.parse(value);
            selectedType[0] = FlowDataType.fromString(selectedTypeRef[0].getTypeId());
        });
        String[] selectedCatalog = new String[]{""};
        String[] selectedWidget = new String[]{""};
        Map<String, FlowOptionSourceMetadata> functionCatalogs = functionInputCatalogs();
        AnimatedButton catalogButton = buildScreenSelectorButton(List.copyOf(functionCatalogs.keySet()), "None", 200, value -> {
            FlowOptionSourceMetadata metadata = functionCatalogs.get(value);
            selectedCatalog[0] = metadata != null ? metadata.getId() : "";
            selectedWidget[0] = metadata == null ? "" : metadata.getWidgetType() != null && !metadata.getWidgetType().isBlank()
                ? metadata.getWidgetType()
                : metadata.isSearchable() ? "SEARCHABLE_LIST" : "DROPDOWN";
            if (metadata != null) {
                selectedTypeRef[0] = FlowTypeRef.parse(metadata.getValueType());
                selectedType[0] = FlowDataType.fromString(selectedTypeRef[0].getTypeId());
                typeButton.setMessage(selectedTypeRef[0].toString());
            }
        });
        TextInputWidget defaultInput = new TextInputWidget.Builder()
            .placeholder("default")
            .size(200, 20)
            .build();

        builder.addRow("Name", nameInput);
        builder.addRow("Type", typeButton);
        if (isFunctionStartNode()) {
            builder.addRow("Catalog", catalogButton);
            builder.addRow("Default", defaultInput);
        }

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton addButton = new AnimatedButton.Builder()
            .label("Add")
            .onClick(() -> {
                String rawName = nameInput.getText() != null ? nameInput.getText().trim() : "";
                if (rawName.isBlank() || !rawName.matches("^[a-zA-Z0-9_]+$")) {
                    return;
                }
                FlowDataType type = selectedType[0];
                if (type == null) {
                    type = callArgument ? FlowDataType.STRING : FlowDataType.ANY;
                }

                List<FlowGraph.FunctionParameter> targetList;
                targetList = getFunctionParameterList();

                if (targetList == null) {
                    return;
                }
                for (FlowGraph.FunctionParameter existing : targetList) {
                    if (existing != null && rawName.equalsIgnoreCase(existing.getName())) {
                        return;
                    }
                }
                String optionsSource = isFunctionStartNode() ? selectedCatalog[0] : "";
                String widget = optionsSource.isBlank() ? "" : selectedWidget[0];
                String defaultValue = isFunctionStartNode() && defaultInput.getText() != null ? defaultInput.getText().trim() : "";
                FlowGraph.FunctionParameter parameter = new FlowGraph.FunctionParameter(rawName, type, widget, optionsSource, defaultValue);
                parameter.setTypeRef(selectedTypeRef[0]);
                targetList.add(parameter);
                if (callArgument) {
                    saveCallParameters();
                } else {
                    graph.setFunctionVersion(graph.getFunctionVersion() + 1);
                }
                refreshInputWidgets();
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .size(80, 18)
            .animateElevation(false)
            .entranceAnimation(false)
            .build();

        builder.addTitleAction("Add", () -> addButton.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        popupRef[0] = builder.build();
        if (ScreenManager.getInstance().getCurrentScreen() != null) {
            ScreenManager.getInstance().getCurrentScreen().addDrawableChild(popupRef[0]);
            popupRef[0].show();
        }
    }

    private int findParameterIndex(List<FlowGraph.FunctionParameter> parameters, String name) {
        for (int i = 0; i < parameters.size(); i++) {
            FlowGraph.FunctionParameter parameter = parameters.get(i);
            if (parameter != null && parameter.getName() != null && parameter.getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private FlowGraph.FunctionParameter findParameterByName(List<FlowGraph.FunctionParameter> parameters, String name) {
        for (FlowGraph.FunctionParameter parameter : parameters) {
            if (parameter != null && parameter.getName() != null && parameter.getName().equals(name)) {
                return parameter;
            }
        }
        return null;
    }

    private void renameParameterConnections(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName) || graph == null || graph.getConnections() == null) {
            return;
        }
        for (FlowConnection connection : graph.getConnections()) {
            if (isFunctionStartNode() && nodeId.equals(connection.getSourceNodeId()) && oldName.equals(connection.getSourcePin())) {
                connection.setSourcePin(newName);
            }
            if (isFunctionEndNode() && nodeId.equals(connection.getTargetNodeId()) && oldName.equals(connection.getTargetPin())) {
                connection.setTargetPin(newName);
            }
        }
    }

    private void removeParameterConnections(String parameterName) {
        if (parameterName == null || graph == null || graph.getConnections() == null) {
            return;
        }
        if (isFunctionStartNode()) {
            graph.getConnections().removeIf(connection -> nodeId.equals(connection.getSourceNodeId()) && parameterName.equals(connection.getSourcePin()));
            return;
        }
        if (isFunctionEndNode()) {
            graph.getConnections().removeIf(connection -> nodeId.equals(connection.getTargetNodeId()) && parameterName.equals(connection.getTargetPin()));
            return;
        }
        if (isFunctionCallNode()) {
            graph.getConnections().removeIf(connection -> nodeId.equals(connection.getTargetNodeId()) && parameterName.equals(connection.getTargetPin()));
        }
    }

    public void removeFunctionParameter(String name) {
        List<FlowGraph.FunctionParameter> params = getFunctionParameterList();
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i) != null && name.equals(params.get(i).getName())) {
                params.remove(i);
                removeParameterConnections(name);
                if (isFunctionCallNode()) {
                    saveCallParameters();
                } else {
                    graph.setFunctionVersion(graph.getFunctionVersion() + 1);
                }
                refreshInputWidgets();
                return;
            }
        }
    }

    private boolean isLiteralInput(NodeDefinition.PinDefinition input) {
        FlowDataType type = input.getDataType();
        if (type == null) {
            return false;
        }
        if (isObjectPin(type) && (input.getWidgetType() == null || input.getWidgetType() == NodeDefinition.WidgetType.AUTO)) {
            return false;
        }
        if (input.getWidgetType() != null && input.getWidgetType() != NodeDefinition.WidgetType.AUTO) {
            return true;
        }
        if (input.getOptions() != null && !input.getOptions().isEmpty()) {
            return true;
        }
        if (input.getOptionsSource() != null && !input.getOptionsSource().isBlank()) {
            return true;
        }
        NodeRegistry registry = NodeRegistry.getInstance();
        FlowTypeMetadata meta = registry != null ? registry.getTypeMetadata(serverId, type.getId()) : null;
        if (meta != null) {
            return meta.isLiteralInput();
        }
        return type == FlowDataType.STRING || type == FlowDataType.NUMBER || type == FlowDataType.BOOLEAN || type == FlowDataType.ANY;
    }

    private boolean isObjectPin(FlowDataType type) {
        if (type == null) {
            return false;
        }
        NodeRegistry registry = NodeRegistry.getInstance();
        FlowTypeMetadata meta = registry != null ? registry.getTypeMetadata(serverId, type.getId()) : null;
        if (meta != null) {
            return meta.isObjectPin();
        }
        return type == FlowDataType.PLAYER
            || type == FlowDataType.ENTITY
            || type == FlowDataType.LIVING_ENTITY
            || type == FlowDataType.WORLD
            || type == FlowDataType.BLOCK
            || type == FlowDataType.LOCATION
            || type == FlowDataType.INVENTORY
            || type == FlowDataType.ITEMSTACK;
    }

    private boolean isInputWired(String pinName) {
        if (graph == null || graph.getConnections() == null) {
            return false;
        }
        for (FlowConnection conn : graph.getConnections()) {
            if (conn.getTargetNodeId().equals(nodeId) && conn.getTargetPin().equals(pinName)) {
                return true;
            }
        }
        return false;
    }

    private void createLoadingState() {
        inputs.clear();
        outputs.clear();
        visibleInputs.clear();
        visibleOutputs.clear();
        updateSize();
    }

    public static int getPinColor(FlowDataType dataType) {
        if (dataType == null) {
            return 0xFFAAAAAA;
        }
        return dataType.getColor();
    }

    public static boolean isPassthroughOutputPin(String pinName) {
        return pinName != null && pinName.startsWith(PASSTHROUGH_OUTPUT_PREFIX);
    }

    public static String passthroughOutputPin(String inputPin) {
        return PASSTHROUGH_OUTPUT_PREFIX + inputPin;
    }

    public static String passthroughInputPin(String outputPin) {
        if (!isPassthroughOutputPin(outputPin)) {
            return outputPin;
        }
        return outputPin.substring(PASSTHROUGH_OUTPUT_PREFIX.length());
    }

    @Override
    public void setX(int x) {
        this.x = x;
        targetX = animatedX = x;
        recomputeRelativeScissor();
    }

    @Override
    public void setY(int y) {
        this.y = y;
        targetY = animatedY = y;
        recomputeRelativeScissor();
    }

    @Override
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        targetX = animatedX = x;
        targetY = animatedY = y;
        recomputeRelativeScissor();
    }

    public void morphFromBounds(int startX, int startY, int startWidth, int startHeight) {
        targetX = getX();
        targetY = getY();
        targetWidth = getWidth();
        targetHeight = getHeight();
        animatedX = startX;
        animatedY = startY;
        animatedWidth = Math.max(1, startWidth);
        animatedHeight = Math.max(1, startHeight);
        x = Math.round(animatedX);
        y = Math.round(animatedY);
        width = Math.round(animatedWidth);
        height = Math.round(animatedHeight);
        layoutInitialized = true;
        recomputeRelativeScissor();
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        int headerBg = ThemeManager.getColor(ThemeColor.inClickableBackground);
        int headerText = ThemeManager.getColor(ThemeColor.text);
        int labelText = ThemeManager.getColor(ThemeColor.textDark);
        int borderColor = ThemeManager.getColor(ThemeColor.innerBorder);

        Render.drawLayeredInnerBorder(ctx, getX(), getY(), getWidth(), TITLE_HEIGHT, headerBg, borderColor);
        ctx.fill(getX(), getY() + TITLE_HEIGHT, getWidth() + getX(), getY() + TITLE_HEIGHT + 1, this.borderColor);
        ctx.drawText(nodeTitle(), getX() + 4, getY() + TITLE_STYLE.textYOffset(), headerText, shadow);

        int titleButtonX = getX() + getWidth() - PADDING;
        int titleButtonY = getY() + TITLE_STYLE.controlYOffset();
        if (closeButton.visible) {
            int closeX = titleButtonX - CLOSE_BUTTON_WIDTH;
            closeButton.setPosition(closeX, titleButtonY);
            titleButtonX = closeX - 4;
        }
        if (paramButton != null) {
            int paramX = titleButtonX - CLOSE_BUTTON_WIDTH;
            paramButton.setPosition(paramX, titleButtonY);
            titleButtonX = paramX - 4;
            paramButton.render(ctx, mouseX, mouseY, 0);
        }
        if (openFunctionButton.visible) {
            int openX = titleButtonX - CLOSE_BUTTON_WIDTH;
            openFunctionButton.setPosition(openX, titleButtonY);
            titleButtonX = openX - 4;
            openFunctionButton.render(ctx, mouseX, mouseY, 0);
        }
        if (closeButton.visible) {
            closeButton.render(ctx, mouseX, mouseY, 0);
        }

        ctx.pushScissorState();
        ctx.enableScissor(getX(), getY() + TITLE_HEIGHT + 1, getX() + getWidth(), getY() + getHeight());

        updateInputWidgetPositions();
        updateOutputWidgetPositions();

        if (definition == null) {
            String text = node.getType() == null || node.getType().isBlank() ? "Loading Definition" : "Loading " + node.getType();
            int textX = getX() + PADDING;
            int textY = getY() + TITLE_HEIGHT + PADDING + 2;
            ctx.drawText(text, textX, textY, labelText, shadow);
            ctx.disableScissor();
            ctx.popScissorState();
            return;
        }

        int rightColumnWidth = getRightColumnWidth();
        int rightColumnStart = getX() + getWidth() - PADDING - rightColumnWidth;

        drawPassthroughGuides(ctx);

        for (int i = 0; i < visibleInputs.size(); i++) {
            NodeDefinition.PinDefinition input = visibleInputs.get(i);
            int rowY = getInputRowY(i);
            int rowHeight = getInputRowHeight(i);
            int pinY = rowY + (rowHeight - PIN_BUTTON_SIZE) / 2;
            int pinX = getX() + PADDING;
            drawPinButton(ctx, pinX, pinY, getPinColor(input.getDataType()));
            int textY = rowY + (rowHeight - ITextRenderer.fontHeight) / 2 + 1;
            ctx.drawText(inputLabel(input), pinX + PIN_BUTTON_SIZE + PIN_TEXT_GAP, textY, labelText, shadow);
        }

        for (int i = 0; i < visibleOutputs.size(); i++) {
            NodeDefinition.PinDefinition output = visibleOutputs.get(i);
            int rowY = getOutputRowY(i);
            int rowHeight = getOutputRowHeight(i);
            int pinY = rowY + (rowHeight - PIN_BUTTON_SIZE) / 2;
            int pinX = rightColumnStart + rightColumnWidth - PIN_BUTTON_SIZE;
            int textY = rowY + (rowHeight - ITextRenderer.fontHeight) / 2 + 1;
            AnimatedButton branchWidget = getBranchWidget(output.getName());
            if (branchWidget == null) {
                String outputLabel = outputLabel(output);
                int labelWidth = tr.getWidth(outputLabel);
                int labelX = pinX - PIN_TEXT_GAP - labelWidth;
                ctx.drawText(outputLabel, labelX, textY, labelText, shadow);
            }
            drawPinButton(ctx, pinX, pinY, getPinColor(output.getDataType()));
        }

        for (int i = visibleInputs.size() - 1; i >= 0; i--) {
            NodeDefinition.PinDefinition input = visibleInputs.get(i);
            Widget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                inputWidget.render(ctx, mouseX, mouseY, 0);
            }
        }

        for (int i = visibleOutputs.size() - 1; i >= 0; i--) {
            NodeDefinition.PinDefinition output = visibleOutputs.get(i);
            AnimatedButton branchWidget = getBranchWidget(output.getName());
            if (branchWidget != null) {
                branchWidget.render(ctx, mouseX, mouseY, 0);
            }
        }

        if (addBranchButton != null && addBranchButton.visible) {
            addBranchButton.render(ctx, mouseX, mouseY, 0);
        }
        for (AnimatedButton button : addInputButtons.values()) {
            if (button.visible) {
                button.render(ctx, mouseX, mouseY - Math.round(currentElevationOffset), 0);
            }
        }

        ctx.disableScissor();
        ctx.popScissorState();
        renderExpandedDropdownOverlays(ctx, mouseX, mouseY);
    }

    private void renderExpandedDropdownOverlays(IDrawContext ctx, int mouseX, int mouseY) {
        for (Widget widget : inputWidgets.values()) {
            if (widget instanceof DropDownWidget<?> dropdown && dropdown.isDropdownVisible() && widget.isVisible()) {
                widget.render(ctx, mouseX, mouseY, 0);
            }
        }
    }

    @Override
    public void renderHintOverlay(IDrawContext context) {
        super.renderHintOverlay(context);
        if (closeButton.visible) {
            closeButton.renderHintOverlay(context);
        }
        if (paramButton != null) {
            paramButton.renderHintOverlay(context);
        }
        if (openFunctionButton.visible) {
            openFunctionButton.renderHintOverlay(context);
        }
        for (Widget widget : inputWidgets.values()) {
            if (widget instanceof AnimatedWidget animated && widget.isVisible()) {
                animated.renderHintOverlay(context);
            }
        }
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && branch.widget.isVisible()) {
                branch.widget.renderHintOverlay(context);
            }
        }
        if (addBranchButton != null && addBranchButton.visible) {
            addBranchButton.renderHintOverlay(context);
        }
        for (AnimatedButton button : addInputButtons.values()) {
            if (button.visible) {
                button.renderHintOverlay(context);
            }
        }
    }

    private Map<String, FlowOptionSourceMetadata> functionInputCatalogs() {
        Map<String, FlowOptionSourceMetadata> catalogs = new LinkedHashMap<>();
        catalogs.put("None", null);
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry == null) {
            return catalogs;
        }
        List<FlowOptionSourceMetadata> sources = new ArrayList<>(registry.getServerOptionSources(serverId));
        sources.sort((left, right) -> {
            int displayOrder = String.CASE_INSENSITIVE_ORDER.compare(left.getDisplayName(), right.getDisplayName());
            return displayOrder != 0 ? displayOrder : String.CASE_INSENSITIVE_ORDER.compare(left.getId(), right.getId());
        });
        for (FlowOptionSourceMetadata source : sources) {
            String label = source.getDisplayName();
            if (catalogs.containsKey(label)) {
                label += " · " + source.getId();
            }
            catalogs.put(label, source);
        }
        return catalogs;
    }

    private void updateSize() {
        int leftColumnWidth = getLeftColumnWidth();
        int rightColumnWidth = getRightColumnWidth();
        int contentWidth = leftColumnWidth + rightColumnWidth + (leftColumnWidth > 0 && rightColumnWidth > 0 ? COLUMN_GAP : 0);
        int contentHeight = Math.max(getInputsContentHeight(), getOutputsContentHeight());
        int bottomRows = Math.max(addInputButtons.size(), addBranchButton != null && addBranchButton.visible ? 1 : 0);
        if (bottomRows > 0) {
            contentHeight += bottomRows * ROW_HEIGHT + (contentHeight > 0 ? bottomRows : bottomRows - 1) * ROW_SPACING;
        }
        int minWidth = (visibleInputs.isEmpty() || visibleOutputs.isEmpty()) ? SINGLE_COLUMN_MIN_WIDTH : DEFAULT_WIDTH;
        int titleWidth = tr.getWidth(nodeTitle()) + PADDING * 2;
        if (closeButton.visible) {
            titleWidth += CLOSE_BUTTON_WIDTH + PADDING;
        }
        if (paramButton != null) {
            titleWidth += CLOSE_BUTTON_WIDTH + 4;
        }
        if (openFunctionButton.visible) {
            titleWidth += CLOSE_BUTTON_WIDTH + 4;
        }

        setWidth(Math.max(minWidth, Math.max(titleWidth, PADDING * 2 + contentWidth)));
        setHeight(TITLE_HEIGHT + PADDING * 2 + contentHeight);
    }

    private void drawPassthroughGuides(IDrawContext ctx) {
        for (NodeDefinition.PinDefinition output : visibleOutputs) {
            if (!isPassthroughOutputPin(output.getName())) {
                continue;
            }
            String inputPin = passthroughInputPin(output.getName());
            double[] input = getPinBounds(inputPin, true);
            double[] outputBounds = getPinBounds(output.getName(), false);
            if (input == null || outputBounds == null) {
                continue;
            }
            int x1 = (int) (input[0] + input[2]);
            int y1 = (int) (input[1] + input[3] / 2);
            int x2 = (int) outputBounds[0];
            int y2 = (int) (outputBounds[1] + outputBounds[3] / 2);
            int midX = getX() + getWidth() / 2;
            int fill = getPinColor(output.getDataType());
            int border = ThemeManager.getColor(ThemeColor.innerBorder);
            drawDashedGuide(ctx, x1, y1, midX, y1, fill, border);
            drawDashedGuide(ctx, midX, y1, midX, y2, fill, border);
            drawDashedGuide(ctx, midX, y2, x2, y2, fill, border);
        }
    }

    private void drawDashedGuide(IDrawContext ctx, int x1, int y1, int x2, int y2, int fill, int border) {
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            for (int y = minY; y <= maxY; y += PASSTHROUGH_DASH_SIZE + PASSTHROUGH_DASH_GAP) {
                drawPassthroughDash(ctx, x1, y, fill, border);
            }
            return;
        }
        if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            for (int x = minX; x <= maxX; x += PASSTHROUGH_DASH_SIZE + PASSTHROUGH_DASH_GAP) {
                drawPassthroughDash(ctx, x, y1, fill, border);
            }
        }
    }

    private void drawPassthroughDash(IDrawContext ctx, int centerX, int centerY, int fill, int border) {
        int half = PASSTHROUGH_DASH_SIZE / 2;
        int x = centerX - half;
        int y = centerY - half;
        Render.drawLayeredInnerBorder(ctx, x, y, PASSTHROUGH_DASH_SIZE, PASSTHROUGH_DASH_SIZE, ThemeManager.getColor(ThemeColor.inClickableBackground), border);
        ctx.fill(x + 2, y + 2, x + PASSTHROUGH_DASH_SIZE - 2, y + PASSTHROUGH_DASH_SIZE - 2, fill);
    }

    public double[] getPinBounds(String pinName, boolean isInput) {
        List<NodeDefinition.PinDefinition> pins = isInput ? visibleInputs : visibleOutputs;
        int index = -1;

        for (int i = 0; i < pins.size(); i++) {
            if (pins.get(i).getName().equals(pinName)) {
                index = i;
                break;
            }
        }

        if (index == -1) return null;

        int rowY = isInput ? getInputRowY(index) : getOutputRowY(index);
        int rowHeight = isInput ? getInputRowHeight(index) : getOutputRowHeight(index);
        int pinY = rowY + (rowHeight - PIN_BUTTON_SIZE) / 2;
        int pinX = isInput ? getX() + PADDING : getX() + getWidth() - PADDING - PIN_BUTTON_SIZE;
        return new double[]{pinX, pinY, PIN_BUTTON_SIZE, PIN_BUTTON_SIZE};
    }

    public boolean isMouseOverPin(int wx, int wy) {
        if (getExpandedInputWidgetAt(wx, wy) != null) {
            return false;
        }
        return getPinAtPosition(wx, wy) != null;
    }

    public Widget getOutputWidgetAt(int wx, int wy) {
        updateOutputWidgetPositions();
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && branch.widget.isMouseOver(wx, wy)) {
                return branch.widget;
            }
        }
        if (addBranchButton != null && addBranchButton.visible && addBranchButton.isMouseOver(wx, wy)) {
            return addBranchButton;
        }
        return null;
    }

    public Widget getInputWidgetAt(int wx, int wy) {
        updateInputWidgetPositions();
        Widget expandedWidget = getExpandedInputWidgetAt(wx, wy);
        if (expandedWidget != null) {
            return expandedWidget;
        }
        Widget bestWidget = null;
        for (Widget widget : inputWidgets.values()) {
            if (widget.isVisible() && widget.isMouseOver(wx, wy)) {
                if (bestWidget == null || widget.getPriority() > bestWidget.getPriority()) {
                    bestWidget = widget;
                }
            }
        }
        for (AnimatedButton button : addInputButtons.values()) {
            if (button.visible && isMouseOverRenderedChild(button, wx, wy)) {
                return button;
            }
        }
        return bestWidget;
    }

    public boolean handleBottomInputActionClick(int wx, int wy, int button) {
        updateInputWidgetPositions();
        for (AnimatedButton addButton : addInputButtons.values()) {
            if (!addButton.visible || !isMouseOverRenderedChild(addButton, wx, wy)) {
                continue;
            }
            if (button == 0) {
                addButton.onClick(wx, wy, button);
            }
            return true;
        }
        return false;
    }

    private boolean isMouseOverRenderedChild(Widget widget, int wx, int wy) {
        int visualY = widget.getY() + Math.round(currentElevationOffset);
        return wx >= widget.getX() - 1 && wx < widget.getX() + widget.getWidth() + 1 && wy >= visualY - 1 && wy < visualY + widget.getHeight() + 4;
    }

    private Widget getExpandedInputWidgetAt(int wx, int wy) {
        for (Widget widget : inputWidgets.values()) {
            if (widget instanceof DropDownWidget<?> dropdown && dropdown.isExpanded() && widget.isVisible() && widget.isMouseOver(wx, wy)) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        int wx = (int) event.x();
        int wy = (int) event.y();

        if (closeButton.visible && closeButton.isMouseOver(wx, wy)) {
            Widget.dispatchMouseClicked(closeButton, event);
            return true;
        }

        if (paramButton != null && paramButton.isMouseOver(wx, wy)) {
            Widget.dispatchMouseClicked(paramButton, event);
            return true;
        }

        if (openFunctionButton.visible && openFunctionButton.isMouseOver(wx, wy)) {
            Widget.dispatchMouseClicked(openFunctionButton, event);
            return true;
        }

        Widget outputWidget = getOutputWidgetAt(wx, wy);
        if (outputWidget != null) {
            setLastScreenMouse(wx, wy);
            Widget.dispatchMouseClicked(outputWidget, event);
            return true;
        }

        if (isMouseOverPin(wx, wy)) {
            return true;
        }

        Widget inputWidget = getInputWidgetAt(wx, wy);
        if (inputWidget != null) {
            NodeDefinition.PinDefinition inputDefinition = inputDefinitionForWidget(inputWidget);
            if (event.button() == ReMouseButton.RIGHT && showResourceReferenceMenu(inputDefinition, wx, wy)) {
                return true;
            }
            Widget.dispatchMouseClicked(inputWidget, event);
            if (inputWidget instanceof TextInputWidget || inputWidget instanceof TextAreaWidget) {
                if (ScreenManager.getInstance().getCurrentScreen() != null) {
                    ScreenManager.getInstance().getCurrentScreen().setFocusedWidget(inputWidget);
                }
            }
            return true;
        }

        return super.mouseClicked(event);
    }

    private NodeDefinition.PinDefinition inputDefinitionForWidget(Widget widget) {
        for (Map.Entry<String, Widget> entry : inputWidgets.entrySet()) {
            if (entry.getValue() == widget) {
                return findInputDefinition(entry.getKey());
            }
        }
        return null;
    }

    private boolean showResourceReferenceMenu(NodeDefinition.PinDefinition input, int x, int y) {
        if (input == null || input.getTypeRef() == null || !"resource_reference".equals(input.getTypeRef().getTypeId())) {
            return false;
        }
        Object stored = node.getInputValues() != null ? node.getInputValues().get(input.getName()) : null;
        String id = resourceId(stored);
        String kind = stored instanceof FlowResourceReference reference ? reference.getKind() : "";
        if (kind.isBlank() && !input.getTypeRef().getArguments().isEmpty()) {
            kind = input.getTypeRef().getArguments().getFirst().getTypeId();
        }
        if (kind.isBlank() || id.isBlank()) {
            return false;
        }
        var screen = ScreenManager.getInstance().getCurrentScreen();
        if (screen == null) {
            return false;
        }
        String resourceKind = kind;
        FlowManager manager = FlowManager.getInstance();
        ReSyncFlowClient flowClient = manager != null ? manager.ensureFlowClient(serverId) : null;
        String contextKey = flowClient != null ? flowClient.optionCatalogContextKey(optionCatalogContext(input)) : "";
        OptionCatalogItem catalogItem = OptionCatalogCache.getInstance().getItems(serverId, input.getOptionsSource(), contextKey).stream()
            .filter(item -> item != null && id.equals(item.getValue()))
            .findFirst()
            .orElse(null);
        String label = catalogItem != null ? catalogItem.getLabel() : id;
        String hint = catalogItem != null && !catalogItem.getDescription().isBlank() ? catalogItem.getDescription() : resourceKind + ":" + id;
        ContextMenuWidget menu = new ContextMenuWidget.Builder(screen)
            .addIconItem("Open " + label, "edit.png", () -> {
                if (screen instanceof StudioScreen studioScreen) {
                    studioScreen.openWorkspaceResource(resourceKind, id);
                } else {
                    new Notification("Open Resource", "Open This Flow In ReSync Studio", Notification.Type.ERROR);
                }
            }, hint)
            .build();
        screen.addDrawableChild(menu);
        menu.show(x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        for (Widget widget : inputWidgets.values()) {
            Widget.dispatchMouseReleased(widget, event);
        }
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null) {
                Widget.dispatchMouseReleased(branch.widget, event);
            }
        }
        if (addBranchButton != null && addBranchButton.visible) {
            Widget.dispatchMouseReleased(addBranchButton, event);
        }
        for (AnimatedButton button : addInputButtons.values()) {
            if (button.visible) {
                Widget.dispatchMouseReleased(button, event);
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && Widget.dispatchMouseDragged(focusedWidget, event)) {
            return true;
        }
        TextAreaWidget focusedTextArea = getFocusedTextAreaWidget();
        if (focusedTextArea != null && Widget.dispatchMouseDragged(focusedTextArea, event)) {
            return true;
        }
        for (Widget widget : inputWidgets.values()) {
            if (widget != focusedWidget && widget != focusedTextArea && Widget.dispatchMouseDragged(widget, event)) {
                return true;
            }
        }
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && Widget.dispatchMouseDragged(branch.widget, event)) {
                return true;
            }
        }
        if (addBranchButton != null && addBranchButton.visible && Widget.dispatchMouseDragged(addBranchButton, event)) {
            return true;
        }
        for (AnimatedButton button : addInputButtons.values()) {
            if (button.visible && Widget.dispatchMouseDragged(button, event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(ReScrollEvent event) {
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && Widget.dispatchMouseScrolled(branch.widget, event)) {
                return true;
            }
        }
        for (Widget widget : inputWidgets.values()) {
            if (Widget.dispatchMouseScrolled(widget, event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean textInput(ReTextInputEvent event) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && Widget.dispatchTextInput(focusedWidget, event)) {
            saveInputValue();
            return true;
        }
        TextAreaWidget focusedTextArea = getFocusedTextAreaWidget();
        if (focusedTextArea != null && Widget.dispatchTextInput(focusedTextArea, event)) {
            saveInputValue();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && Widget.dispatchKeyPressed(focusedWidget, event)) {
            saveInputValue();
            return true;
        }
        TextAreaWidget focusedTextArea = getFocusedTextAreaWidget();
        if (focusedTextArea != null && Widget.dispatchKeyPressed(focusedTextArea, event)) {
            saveInputValue();
            return true;
        }
        return false;
    }

    private TextInputWidget getFocusedInputWidget() {
        for (Widget widget : inputWidgets.values()) {
            if (widget instanceof TextInputWidget textInput && textInput.isFocused()) {
                return textInput;
            }
        }
        return null;
    }

    private TextAreaWidget getFocusedTextAreaWidget() {
        for (Widget widget : inputWidgets.values()) {
            if (widget instanceof TextAreaWidget textArea && textArea.isFocused()) {
                return textArea;
            }
        }
        return null;
    }

    private void updateInputWidgetPositions() {
        int leftColumnWidth = getLeftColumnWidth();
        int leftColumnEnd = getX() + PADDING + leftColumnWidth;

        for (int i = 0; i < visibleInputs.size(); i++) {
            NodeDefinition.PinDefinition input = visibleInputs.get(i);
            Widget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                int rowY = getInputRowY(i);
                int rowHeight = getInputRowHeight(i);
                int widgetWidth = getInputWidgetWidth(inputWidget);
                int widgetHeight = getInputWidgetHeight(inputWidget);
                int widgetY = rowY + (rowHeight - widgetHeight) / 2;
                int widgetX = leftColumnEnd - widgetWidth;
                inputWidget.setPosition(widgetX, widgetY);
                inputWidget.setWidth(widgetWidth);
                inputWidget.setHeight(widgetHeight);
                inputWidget.setPriority(visibleInputs.size() - i);
                if (inputWidget instanceof AnimatedWidget w) {
                    w.setLayer(visibleInputs.size() - i);
                }
            }
        }
        for (NodeDefinition.PinDefinition input : inputs) {
            Widget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null && !visibleInputs.contains(input)) {
                inputWidget.setVisible(false);
            }
        }
        int contentHeight = Math.max(getInputsContentHeight(), getOutputsContentHeight());
        int buttonIndex = 0;
        for (AnimatedButton button : addInputButtons.values()) {
            if (!button.visible) {
                continue;
            }
            int rowY = getRowStartY() + contentHeight + (contentHeight > 0 ? ROW_SPACING : 0) + buttonIndex * (ROW_HEIGHT + ROW_SPACING);
            button.setPosition(getX() + PADDING, rowY);
            button.setWidth(INPUT_WIDGET_WIDTH);
            button.setHeight(INPUT_WIDGET_HEIGHT);
            buttonIndex++;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (selected || !ThemeManager.getDefaultAccent().equals(accentType)) {
            return;
        }
        bgColor = ThemeManager.getColor(ThemeColor.innerBackground);
        borderColor = ThemeManager.getColor(ThemeColor.innerBorder);
        outerBorderColor = ThemeManager.getColor(ThemeColor.globalOuterBorder);
    }

    private int getLeftColumnWidth() {
        int width = 0;
        for (NodeDefinition.PinDefinition input : visibleInputs) {
            int labelWidth = tr.getWidth(inputLabel(input));
            int rowWidth = PIN_BUTTON_SIZE + PIN_TEXT_GAP + labelWidth;
            Widget widget = inputWidgets.get(input.getName());
            if (widget != null) {
                rowWidth += INPUT_FIELD_GAP + getInputWidgetWidth(widget);
            }
            width = Math.max(width, rowWidth);
        }
        for (AnimatedButton button : addInputButtons.values()) {
            if (button.visible) {
                width = Math.max(width, button.getWidth());
            }
        }
        return width;
    }

    private int getRightColumnWidth() {
        int width = 0;
        for (NodeDefinition.PinDefinition output : visibleOutputs) {
            AnimatedButton branchWidget = getBranchWidget(output.getName());
            if (branchWidget != null) {
                int rowWidth = getOutputWidgetWidth(branchWidget) + PIN_TEXT_GAP + PIN_BUTTON_SIZE;
                width = Math.max(width, rowWidth);
            } else {
                int labelWidth = tr.getWidth(outputLabel(output));
                int rowWidth = labelWidth + PIN_TEXT_GAP + PIN_BUTTON_SIZE;
                width = Math.max(width, rowWidth);
            }
        }
        if (addBranchButton != null && addBranchButton.visible) {
            width = Math.max(width, addBranchButton.getWidth());
        }
        return width;
    }

    private int getRowStartY() {
        return getY() + TITLE_HEIGHT + PADDING;
    }

    private void drawPinButton(IDrawContext ctx, int x, int y, int color) {
        int background = ThemeManager.getColor(ThemeColor.inClickableBackground);
        int border = ThemeManager.getColor(ThemeColor.innerBorder);
        Render.drawLayeredInnerBorder(ctx, x, y, PIN_BUTTON_SIZE, PIN_BUTTON_SIZE, background, border);
        int inset = 2;
        ctx.fill(x + inset, y + inset, x + PIN_BUTTON_SIZE - inset, y + PIN_BUTTON_SIZE - inset, color);
    }

    private void saveInputValue() {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        for (Map.Entry<String, Widget> entry : inputWidgets.entrySet()) {
            NodeDefinition.PinDefinition def = findInputDefinition(entry.getKey());
            if (def == null) {
                continue;
            }
            Widget widget = entry.getValue();
            if (!widget.isVisible()) {
                continue;
            }
            Object typedValue = null;
            if (widget instanceof TextInputWidget textInput) {
                String value = textInput.getText();
                if (value.isEmpty()) {
                    node.getInputValues().remove(entry.getKey());
                    continue;
                }
                typedValue = convertLiteralValue(value, def);
            } else if (widget instanceof ToggleWidget toggle) {
                typedValue = toggle.getValue();
            } else if (widget instanceof AnimatedButton button) {
                String value = searchableSelectorValues.getOrDefault(entry.getKey(), button.getMessage());
                if (value == null || value.isBlank() || "Loading".equals(value)) {
                    continue;
                }
                typedValue = convertLiteralValue(value, def);
            } else if (widget instanceof DropDownWidget<?> dropdown) {
                Object value = dropdown.getSelectedItem();
                if (value == null) {
                    continue;
                }
                typedValue = convertLiteralValue(value.toString(), def);
            } else if (widget instanceof SliderWidget slider) {
                typedValue = slider.getValue();
            } else if (widget instanceof TextAreaWidget textArea) {
                String value = textArea.getText();
                if (value.isEmpty()) {
                    node.getInputValues().remove(entry.getKey());
                    continue;
                }
                typedValue = value;
            } else if (widget instanceof ColorFieldWidget colorField) {
                String value = colorField.getColor();
                if (value.isEmpty()) {
                    node.getInputValues().remove(entry.getKey());
                    continue;
                }
                typedValue = value;
            }
            if (typedValue != null) {
                node.getInputValues().put(entry.getKey(), typedValue);
            }
        }
        if (updateStringTemplatePins()) {
            updatePinVisibility();
        }
    }

    private int getInputWidgetWidth(Widget widget) {
        if (widget instanceof ToggleWidget) {
            return TOGGLE_WIDGET_WIDTH;
        }
        return widget.getWidth();
    }

    private int getInputWidgetHeight(Widget widget) {
        if (widget instanceof ToggleWidget) {
            return TOGGLE_WIDGET_HEIGHT;
        }
        return widget.getHeight();
    }

    private int getInputRowY(int index) {
        int rowY = getRowStartY();
        for (int i = 0; i < index; i++) {
            rowY += getInputRowHeight(i) + ROW_SPACING;
        }
        return rowY;
    }

    private int getOutputRowY(int index) {
        int rowY = getRowStartY();
        for (int i = 0; i < index; i++) {
            rowY += getOutputRowHeight(i) + ROW_SPACING;
        }
        return rowY;
    }

    private int getInputRowHeight(int index) {
        if (index < 0 || index >= visibleInputs.size()) {
            return ROW_HEIGHT;
        }
        Widget widget = inputWidgets.get(visibleInputs.get(index).getName());
        return widget != null ? Math.max(ROW_HEIGHT, getInputWidgetHeight(widget)) : ROW_HEIGHT;
    }

    private int getOutputRowHeight(int index) {
        if (index < 0 || index >= visibleOutputs.size()) {
            return ROW_HEIGHT;
        }
        AnimatedButton branchWidget = getBranchWidget(visibleOutputs.get(index).getName());
        return branchWidget != null ? Math.max(ROW_HEIGHT, getOutputWidgetHeight(branchWidget)) : ROW_HEIGHT;
    }

    private int getInputsContentHeight() {
        int height = 0;
        for (int i = 0; i < visibleInputs.size(); i++) {
            if (i > 0) {
                height += ROW_SPACING;
            }
            height += getInputRowHeight(i);
        }
        return height;
    }

    private int getOutputsContentHeight() {
        int height = 0;
        for (int i = 0; i < visibleOutputs.size(); i++) {
            if (i > 0) {
                height += ROW_SPACING;
            }
            height += getOutputRowHeight(i);
        }
        return height;
    }

    private NodeDefinition.PinDefinition findInputDefinition(String pinName) {
        for (NodeDefinition.PinDefinition input : inputs) {
            if (input.getName().equals(pinName)) {
                return input;
            }
        }
        return null;
    }

    private Object convertValue(String value, FlowDataType dataType) {
        if (dataType == null || dataType == FlowDataType.ANY) {
            return value;
        }
        String id = dataType.getId();
        try {
            if ("number".equals(id)) {
                return Double.parseDouble(value);
            }
            if ("boolean".equals(id)) {
                return Boolean.parseBoolean(value);
            }
            return value;
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private Object convertLiteralValue(String value, NodeDefinition.PinDefinition definition) {
        FlowTypeRef typeRef = definition != null ? definition.getTypeRef() : null;
        if (typeRef != null && "resource_reference".equals(typeRef.getTypeId()) && !typeRef.getArguments().isEmpty()) {
            return new FlowResourceReference(typeRef.getArguments().getFirst().getTypeId(), value, "server");
        }
        if (typeRef != null) {
            String kind = switch (typeRef.getTypeId()) {
                case "variable_reference" -> ReSyncResourceDragPayload.VARIABLE_DEFINITION;
                case "timer_reference" -> ReSyncResourceDragPayload.TIMER_DEFINITION;
                case "schedule_reference" -> ReSyncResourceDragPayload.SCHEDULE_DEFINITION;
                default -> null;
            };
            if (kind != null) {
                return new FlowResourceReference(kind, value, "server");
            }
        }
        return convertValue(value, definition != null ? definition.getDataType() : FlowDataType.ANY);
    }

    private String resourceId(Object value) {
        if (value instanceof FlowResourceReference reference) {
            return reference.getId() != null ? reference.getId() : "";
        }
        if (value instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id != null ? id.toString() : "";
        }
        return value != null ? value.toString() : "";
    }

    public FlowDataType getPinType(String pinName, boolean isInput) {
        if (isInput) {
            for (NodeDefinition.PinDefinition input : inputs) {
                if (input.getName().equals(pinName)) {
                    return input.getDataType();
                }
            }
        } else {
            for (NodeDefinition.PinDefinition output : outputs) {
                if (output.getName().equals(pinName)) {
                    return output.getDataType();
                }
            }
            for (NodeDefinition.PinDefinition output : visibleOutputs) {
                if (output.getName().equals(pinName)) {
                    return output.getDataType();
                }
            }
        }
        return null;
    }

    public FlowTypeRef getPinTypeRef(String pinName, boolean isInput) {
        if (isInput) {
            for (NodeDefinition.PinDefinition input : inputs) {
                if (input.getName().equals(pinName)) {
                    return input.getTypeRef();
                }
            }
        } else {
            for (NodeDefinition.PinDefinition output : outputs) {
                if (output.getName().equals(pinName)) {
                    return output.getTypeRef();
                }
            }
            for (NodeDefinition.PinDefinition output : visibleOutputs) {
                if (output.getName().equals(pinName)) {
                    return output.getTypeRef();
                }
            }
        }
        return null;
    }

    public String getInputOptionsSource(String pinName) {
        NodeDefinition.PinDefinition input = findInputDefinition(pinName);
        return input != null ? input.getOptionsSource() : null;
    }

    public boolean assignLiteralInput(String pinName, String value) {
        NodeDefinition.PinDefinition input = findInputDefinition(pinName);
        if (input == null) {
            return false;
        }
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        node.getInputValues().put(pinName, convertLiteralValue(value, input));
        refreshInputWidgets();
        updatePinVisibility();
        return true;
    }

    private boolean updateStringTemplatePins() {
        if (isFunctionStartNode() || isFunctionEndNode()) {
            return false;
        }
        List<String> current = new ArrayList<>(stringTemplateInputNames);
        List<String> next = new ArrayList<>(nodeStringTemplateNames());
        Set<String> reservedInputNames = new LinkedHashSet<>();
        for (NodeDefinition.PinDefinition input : inputs) {
            if (!stringTemplateInputNames.contains(input.getName())) {
                reservedInputNames.add(input.getName());
            }
        }
        next.removeIf(reservedInputNames::contains);
        if (current.equals(next)) {
            for (String name : next) {
                if (inputs.stream().noneMatch(input -> name.equals(input.getName()))) {
                    inputs.add(new NodeDefinition.PinDefinition(name, NodeDefinition.PinType.DATA, NodeDefinition.PinDirection.INPUT, FlowDataType.STRING));
                }
            }
            return false;
        }

        Set<String> nextSet = new LinkedHashSet<>(next);
        Set<String> removed = new LinkedHashSet<>(current);
        removed.removeAll(nextSet);
        removeStringTemplateConnections(removed);

        inputs.removeIf(input -> stringTemplateInputNames.contains(input.getName()));
        for (String name : next) {
            inputs.add(new NodeDefinition.PinDefinition(name, NodeDefinition.PinType.DATA, NodeDefinition.PinDirection.INPUT, FlowDataType.STRING));
        }
        stringTemplateInputNames.clear();
        stringTemplateInputNames.addAll(next);
        if (node.getInputValues() != null) {
            for (String name : removed) {
                node.getInputValues().remove(name);
            }
        }
        return true;
    }

    private boolean isStringTemplateValuePin(NodeDefinition.PinDefinition input) {
        return input != null && stringTemplateInputNames.contains(input.getName());
    }

    private void removeStringTemplateConnections(Set<String> removed) {
        if (removed.isEmpty() || graph == null || graph.getConnections() == null) {
            return;
        }
        graph.getConnections().removeIf(connection -> nodeId.equals(connection.getTargetNodeId()) && removed.contains(connection.getTargetPin()));
        graph.getEditorPassthroughs().removeIf(passthrough -> nodeId.equals(passthrough.getNodeId()) && removed.contains(passthrough.getInputPin()));
    }

    private Set<String> nodeStringTemplateNames() {
        Set<String> names = new LinkedHashSet<>();
        if (definition == null || definition.getInputs() == null || node.getInputValues() == null) {
            return names;
        }
        for (NodeDefinition.PinDefinition input : definition.getInputs()) {
            if (!isStringTemplateSourceInput(input)) {
                continue;
            }
            Object value = node.getInputValues().get(input.getName());
            if (value instanceof String text) {
                names.addAll(stringTemplateNames(text));
            }
        }
        return names;
    }

    private boolean isStringTemplateSourceInput(NodeDefinition.PinDefinition input) {
        return input != null
            && input.getType() == NodeDefinition.PinType.DATA
            && input.getDirection() == NodeDefinition.PinDirection.INPUT
            && input.getDataType() == FlowDataType.STRING
            && shouldShowInputPin(input)
            && evaluateVisibleWhen(input.getVisibleWhen());
    }

    private Set<String> stringTemplateNames(String template) {
        Set<String> names = new LinkedHashSet<>();
        if (template == null || template.isEmpty()) {
            return names;
        }
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == '{') {
                if (index + 1 < template.length() && template.charAt(index + 1) == '{') {
                    index += 2;
                    continue;
                }
                int end = template.indexOf('}', index + 1);
                if (end > index + 1) {
                    String name = template.substring(index + 1, end).trim();
                    if (isStringTemplateName(name)) {
                        names.add(name);
                        index = end + 1;
                        continue;
                    }
                }
            }
            index++;
        }
        return names;
    }

    private boolean isStringTemplateName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    public boolean isFunctionStartOrEnd() {
        return isFunctionStartNode() || isFunctionEndNode();
    }

    public String getNodeId() {
        return nodeId;
    }

    public NodeDefinition.PinType getPinKind(String pinName, boolean isInput) {
        if (isInput) {
            for (NodeDefinition.PinDefinition input : inputs) {
                if (input.getName().equals(pinName)) {
                    return input.getType();
                }
            }
        } else {
            for (NodeDefinition.PinDefinition output : outputs) {
                if (output.getName().equals(pinName)) {
                    return output.getType();
                }
            }
            for (NodeDefinition.PinDefinition output : visibleOutputs) {
                if (output.getName().equals(pinName)) {
                    return output.getType();
                }
            }
        }
        return null;
    }

    public String getPinAtPosition(int wx, int wy) {
        for (NodeDefinition.PinDefinition input : visibleInputs) {
            double[] bounds = getPinBounds(input.getName(), true);
            if (bounds != null && isInside(wx, wy, bounds)) {
                return input.getName();
            }
        }
        for (NodeDefinition.PinDefinition output : visibleOutputs) {
            double[] bounds = getPinBounds(output.getName(), false);
            if (bounds != null && isInside(wx, wy, bounds)) {
                return output.getName();
            }
        }
        return null;
    }

    public List<String> getVisibleInputPins() {
        return visibleInputs.stream().map(NodeDefinition.PinDefinition::getName).toList();
    }

    public List<String> getVisibleOutputPins() {
        return visibleOutputs.stream().map(NodeDefinition.PinDefinition::getName).toList();
    }

    public String getInputPinAtPosition(int wx, int wy) {
        int minX = getX();
        int maxX = getX() + PADDING + getLeftColumnWidth() + PIN_HIT_PADDING;
        for (int i = 0; i < visibleInputs.size(); i++) {
            NodeDefinition.PinDefinition input = visibleInputs.get(i);
            double[] bounds = getPinBounds(input.getName(), true);
            if (bounds != null && isInside(wx, wy, bounds)) {
                return input.getName();
            }
            int rowY = getInputRowY(i);
            int rowHeight = getInputRowHeight(i);
            if (wx >= minX && wx <= maxX && wy >= rowY - PIN_HIT_PADDING && wy <= rowY + rowHeight + PIN_HIT_PADDING) {
                return input.getName();
            }
        }
        return null;
    }

    public String getOutputPinAtPosition(int wx, int wy) {
        int minX = getX() + getWidth() - PADDING - getRightColumnWidth() - PIN_HIT_PADDING;
        int maxX = getX() + getWidth();
        for (int i = 0; i < visibleOutputs.size(); i++) {
            NodeDefinition.PinDefinition output = visibleOutputs.get(i);
            double[] bounds = getPinBounds(output.getName(), false);
            if (bounds != null && isInside(wx, wy, bounds)) {
                return output.getName();
            }
            int rowY = getOutputRowY(i);
            int rowHeight = getOutputRowHeight(i);
            if (wx >= minX && wx <= maxX && wy >= rowY - PIN_HIT_PADDING && wy <= rowY + rowHeight + PIN_HIT_PADDING) {
                return output.getName();
            }
        }
        return null;
    }

    private void createOutputWidgets() {
        visibleOutputs.clear();
        flowBranches.clear();

        List<NodeDefinition.PinDefinition> metadataVisibleOutputs = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : outputs) {
            if (evaluateVisibleWhen(output.getVisibleWhen())) {
                metadataVisibleOutputs.add(output);
            }
        }

        List<NodeDefinition.PinDefinition> flowOutputs = new ArrayList<>();
        List<NodeDefinition.PinDefinition> otherOutputs = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : metadataVisibleOutputs) {
            if (isFlowOutput(output)) {
                flowOutputs.add(output);
            } else {
                otherOutputs.add(output);
            }
        }

        if (flowOutputs.size() <= 2 || isBranchingSwitch()) {
            visibleOutputs.addAll(flowOutputs);
            visibleOutputs.addAll(otherOutputs);
            visibleOutputs.sort((left, right) -> Boolean.compare(!isFlowOutput(left), !isFlowOutput(right)));
            appendPassthroughOutputs();
            addBranchButton = null;
            return;
        }

        List<String> selectedBranches = resolveFlowBranches(flowOutputs);
        for (String branch : selectedBranches) {
            NodeDefinition.PinDefinition pin = findOutputDefinition(branch);
            if (pin != null && metadataVisibleOutputs.contains(pin)) {
                visibleOutputs.add(pin);
                flowBranches.add(new FlowBranch(branch, buildBranchSelector(flowOutputs, branch)));
            }
        }

        visibleOutputs.addAll(otherOutputs);
        visibleOutputs.sort((left, right) -> Boolean.compare(!isFlowOutput(left), !isFlowOutput(right)));
        appendPassthroughOutputs();
        saveFlowBranches();
        updateAddBranchButton(flowOutputs);
    }

    private void appendPassthroughOutputs() {
        if (graph == null || graph.getEditorPassthroughs() == null) {
            return;
        }
        for (FlowGraph.EditorPassthrough passthrough : graph.getEditorPassthroughs()) {
            if (passthrough == null || !nodeId.equals(passthrough.getNodeId())) {
                continue;
            }
            NodeDefinition.PinDefinition input = findVisibleInputDefinition(passthrough.getInputPin());
            if (input == null || input.getType() != NodeDefinition.PinType.DATA) {
                continue;
            }
            String outputName = passthroughOutputPin(input.getName());
            boolean exists = false;
            for (NodeDefinition.PinDefinition output : visibleOutputs) {
                if (outputName.equals(output.getName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                visibleOutputs.add(new NodeDefinition.PinDefinition(outputName, NodeDefinition.PinType.DATA, NodeDefinition.PinDirection.OUTPUT, input.getDataType()));
            }
        }
    }

    private NodeDefinition.PinDefinition findVisibleInputDefinition(String pinName) {
        for (NodeDefinition.PinDefinition input : visibleInputs) {
            if (input.getName().equals(pinName)) {
                return input;
            }
        }
        return null;
    }

    private boolean isFlowOutput(NodeDefinition.PinDefinition output) {
        return output.getType() == NodeDefinition.PinType.FLOW && output.getDataType() == FlowDataType.EXECUTION;
    }

    private boolean isBranchingSwitch() {
        if (!"flow.switch_case".equals(node.getType()) || node.getInputValues() == null) {
            return false;
        }
        Object branch = node.getInputValues().get("branch");
        return branch instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(branch));
    }

    private String outputLabel(NodeDefinition.PinDefinition output) {
        String name = passthroughInputPin(output.getName());
        if (!isBranchingSwitch() || !isFlowOutput(output)) {
            return name;
        }
        if ("default".equals(name)) {
            return "Default";
        }
        if (!name.equals("case") && !name.startsWith("case_")) {
            return name;
        }
        Object value = node.getInputValues().get(name);
        if (value == null || value.toString().isBlank()) {
            return name.equals("case") ? "Case" : "Case " + name.substring("case_".length());
        }
        return value.toString();
    }

    private String inputLabel(NodeDefinition.PinDefinition input) {
        if (isFunctionCallNode()) {
            return switch (input.getName()) {
                case "function" -> "Function";
                case "arguments" -> "Value / Arguments";
                case "continue_on_failure" -> "Continue On Failure";
                default -> input.getName();
            };
        }
        if (!"flow.switch_case".equals(node.getType())) {
            return input.getName();
        }
        return switch (input.getName()) {
            case "branch" -> "Create Branches";
            case "cases" -> "Case List";
            case "value" -> "Value";
            case "case" -> "Case";
            default -> input.getName().startsWith("case_") ? "Case " + input.getName().substring("case_".length()) : input.getName();
        };
    }

    private NodeDefinition.PinDefinition findOutputDefinition(String name) {
        for (NodeDefinition.PinDefinition output : outputs) {
            if (output.getName().equals(name)) {
                return output;
            }
        }
        return null;
    }

    private List<String> resolveFlowBranches(List<NodeDefinition.PinDefinition> flowOutputs) {
        List<String> options = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : flowOutputs) {
            options.add(output.getName());
        }

        List<String> selected = new ArrayList<>();
        boolean storedBranchesPresent = false;
        if (node.getInputValues() != null) {
            Object stored = node.getInputValues().get(FLOW_BRANCHES_KEY);
            if (stored instanceof List<?> list) {
                storedBranchesPresent = true;
                for (Object entry : list) {
                    if (entry instanceof String name && options.contains(name)) {
                        if (!selected.contains(name)) {
                            selected.add(name);
                        }
                    }
                }
            }
        }

        if (graph != null && graph.getConnections() != null) {
            for (FlowConnection conn : graph.getConnections()) {
                if (nodeId.equals(conn.getSourceNodeId()) && options.contains(conn.getSourcePin())) {
                    if (!selected.contains(conn.getSourcePin())) {
                        selected.add(conn.getSourcePin());
                    }
                }
            }
        }

        if (selected.isEmpty() && !storedBranchesPresent && !options.isEmpty()) {
            selected.add(options.getFirst());
        }
        return selected;
    }

    private AnimatedButton buildBranchSelector(List<NodeDefinition.PinDefinition> flowOutputs, String selected) {
        List<String> options = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : flowOutputs) {
            options.add(output.getName());
        }
        AnimatedButton button = new AnimatedButton.Builder()
            .label(selected)
            .size(OUTPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
            .entranceAnimation(false)
            .build();
        button.setAction(() -> showBranchSelector(button, options, button.getMessage(), value -> {
            String oldName = button.getMessage();
            if (value == null || value.isBlank() || "Loading".equals(value)) {
                return;
            }
            button.setMessage(value);
            updateFlowBranchSelection(oldName, value);
        }));
        return button;
    }

    private void updateFlowBranchSelection(String oldName, String newName) {
        if (updatingBranchSelection || oldName == null || newName == null || oldName.equals(newName)) {
            return;
        }
        FlowBranch targetBranch = null;
        boolean conflict = false;
        for (FlowBranch branch : flowBranches) {
            if (branch.outputName.equals(oldName)) {
                targetBranch = branch;
            } else if (branch.outputName.equals(newName)) {
                conflict = true;
            }
        }

        if (conflict) {
            updatingBranchSelection = true;
            if (targetBranch != null && targetBranch.widget != null) {
                targetBranch.widget.setMessage(oldName);
            }
            updatingBranchSelection = false;
            return;
        }

        if (targetBranch != null) {
            targetBranch.outputName = newName;
        }

        if (graph != null && graph.getConnections() != null) {
            for (FlowConnection conn : graph.getConnections()) {
                if (nodeId.equals(conn.getSourceNodeId()) && oldName.equals(conn.getSourcePin())) {
                    conn.setSourcePin(newName);
                }
            }
        }

        saveFlowBranches();
        updatePinVisibility();
    }

    private void updateAddBranchButton(List<NodeDefinition.PinDefinition> flowOutputs) {
        if (flowBranches.size() >= flowOutputs.size()) {
            addBranchButton = null;
            return;
        }
        if (addBranchButton == null) {
            addBranchButton = new AnimatedButton.Builder()
                .label("Add Branch")
                .onClick(this::addFlowBranch)
                .animateElevation(false)
                .entranceAnimation(false)
                .size(OUTPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                .build();
        }
        addBranchButton.visible = true;
    }

    private void addFlowBranch() {
        List<String> options = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : outputs) {
            if (isFlowOutput(output)) {
                options.add(output.getName());
            }
        }
        for (String option : options) {
            boolean used = false;
            for (FlowBranch branch : flowBranches) {
                if (branch.outputName.equals(option)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                flowBranches.add(new FlowBranch(option, null));
                break;
            }
        }
        saveFlowBranches();
        updatePinVisibility();
    }

    private void saveFlowBranches() {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        List<String> branches = new ArrayList<>();
        for (FlowBranch branch : flowBranches) {
            branches.add(branch.outputName);
        }
        node.getInputValues().put(FLOW_BRANCHES_KEY, branches);
    }

    private void updateOutputWidgetPositions() {
        int rightColumnWidth = getRightColumnWidth();
        int buttonX = getX() + getWidth() - PADDING - rightColumnWidth;

        for (int i = 0; i < visibleOutputs.size(); i++) {
            NodeDefinition.PinDefinition output = visibleOutputs.get(i);
            AnimatedButton branchWidget = getBranchWidget(output.getName());
            if (branchWidget != null) {
                int rowY = getOutputRowY(i);
                int rowHeight = getOutputRowHeight(i);
                int widgetWidth = getOutputWidgetWidth(branchWidget);
                int widgetHeight = getOutputWidgetHeight(branchWidget);
                int widgetY = rowY + (rowHeight - widgetHeight) / 2;
                int widgetX = buttonX + rightColumnWidth - PIN_BUTTON_SIZE - PIN_TEXT_GAP - widgetWidth;
                branchWidget.setPosition(widgetX, widgetY);
                branchWidget.setWidth(widgetWidth);
                branchWidget.setHeight(widgetHeight);
                branchWidget.setPriority(visibleOutputs.size() - i);
                branchWidget.setLayer(visibleOutputs.size() - i);
            }
        }

        if (addBranchButton != null && addBranchButton.visible) {
            int contentHeight = Math.max(getInputsContentHeight(), getOutputsContentHeight());
            int rowY = getRowStartY() + contentHeight + (contentHeight > 0 ? ROW_SPACING : 0);
            addBranchButton.setPosition(buttonX, rowY);
            addBranchButton.setWidth(Math.min(OUTPUT_WIDGET_WIDTH, rightColumnWidth));
            addBranchButton.setHeight(INPUT_WIDGET_HEIGHT);
        }

        updateFunctionParameterButtonPosition();
    }

    private void updateFunctionParameterButtonPosition() {
    }

    private int getOutputWidgetWidth(Widget widget) {
        return OUTPUT_WIDGET_WIDTH;
    }

    private int getOutputWidgetHeight(Widget widget) {
        return INPUT_WIDGET_HEIGHT;
    }

    private AnimatedButton getBranchWidget(String outputName) {
        for (FlowBranch branch : flowBranches) {
            if (branch.outputName.equals(outputName)) {
                return branch.widget;
            }
        }
        return null;
    }

    private static class FlowBranch {
        private String outputName;
        private final AnimatedButton widget;

        private FlowBranch(String outputName, AnimatedButton widget) {
            this.outputName = outputName;
            this.widget = widget;
        }
    }

    private boolean isInside(int x, int y, double[] bounds) {
        double minX = bounds[0] - PIN_HIT_PADDING;
        double minY = bounds[1] - PIN_HIT_PADDING;
        double maxX = bounds[0] + bounds[2] + PIN_HIT_PADDING;
        double maxY = bounds[1] + bounds[3] + PIN_HIT_PADDING;
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
