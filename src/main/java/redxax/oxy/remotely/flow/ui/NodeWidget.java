package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.FlowOptionSourceMetadata;
import redxax.oxy.remotely.flow.sync.FlowTypeMetadata;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import restudio.rebase.minecraft.assets.MinecraftAssetsManager;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.render.Render;
import restudio.rescreen.config.Config;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.ColorFieldWidget;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SliderWidget;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final List<NodeDefinition.PinDefinition> visibleInputs = new ArrayList<>();
    private final List<NodeDefinition.PinDefinition> visibleOutputs = new ArrayList<>();
    private final List<FlowBranch> flowBranches = new ArrayList<>();
    private final Runnable onClose;
    private final AnimatedButton closeButton;
    private AnimatedButton addBranchButton;
    private AnimatedButton paramButton;
    private static final int TITLE_HEIGHT = 16;
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
    private static final int CLOSE_BUTTON_WIDTH = 12;
    private static final int CLOSE_BUTTON_HEIGHT = 8;
    private static final String FLOW_BRANCHES_KEY = "__flow_branches";
    private static final String PASSTHROUGH_OUTPUT_PREFIX = "__passthrough:";
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

    private boolean updatingBranchSelection = false;
    private int lastScreenX;
    private int lastScreenY;

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

        if (isFunctionStartType(node.getType()) || isFunctionEndType(node.getType())) {
            this.paramButton = new AnimatedButton.Builder()
                .onClick(this::showParamContextMenu)
                .accentType(ThemeManager.getAccent("nice"))
                .animateElevation(false)
                .entranceAnimation(false)
                .size(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
                .hint("Params")
                .build();
        } else {
            this.paramButton = null;
        }

        if (definition != null) {
            inputs.addAll(definition.getInputs());
            outputs.addAll(definition.getOutputs());
            applyFunctionParameterPins();
            seedDefaultInputValues();
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

    public boolean hasLoadedDefinition() {
        return definition != null;
    }

    private void createInputWidgets() {
        if (graph == null || nodeId == null) return;

        for (NodeDefinition.PinDefinition input : inputs) {
            if (input.getType() != NodeDefinition.PinType.DATA) {
                continue;
            }
            if (!shouldShowInputPin(input) || !shouldShowLiteralInput(input) || !isLiteralInput(input)) {
                continue;
            }
            if (isInputWired(input.getName())) {
                continue;
            }
            Widget widget = buildWidgetForPin(input);
            if (widget != null) {
                inputWidgets.put(input.getName(), widget);
            }
        }
        updatePinVisibility();
    }

    private Widget buildWidgetForPin(NodeDefinition.PinDefinition input) {
        Object currentValue = node.getInputValues() != null ? node.getInputValues().get(input.getName()) : null;
        NodeDefinition.WidgetType widgetType = resolveWidgetType(input);

        switch (widgetType) {
            case DROPDOWN -> {
                List<String> options = resolveOptions(input);
                if (options.isEmpty()) {
                    return buildTextInput(currentValue, input.getOptionsSource());
                }
                String selected = resolveSelected(options, currentValue, input.getDefaultValue());
                return buildSearchableSelector(input, options, selected);
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
                    .onChange(this::saveInputValue)
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
                    .onChange(text -> saveInputValue())
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
                return buildTextInput(currentValue, "");
            }
        }
    }

    private Widget buildTextInput(Object currentValue, String placeholder) {
        String textValue = currentValue != null ? currentValue.toString() : "";
        return new TextInputWidget.Builder()
            .text(textValue)
            .placeholder(placeholder != null ? placeholder : "")
            .forcePlaceholder(false)
            .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
            .onChange(this::saveInputValue)
            .entranceAnimation(false)
            .build();
    }

    protected boolean shouldShowLiteralInput(NodeDefinition.PinDefinition input) {
        return true;
    }

    protected boolean shouldShowInputPin(NodeDefinition.PinDefinition input) {
        return true;
    }

    private void handleInputValueChanged(NodeDefinition.PinDefinition input) {
        saveInputValue();
        if (isCustomContentProviderInput(input)) {
            refreshInputWidgets();
            return;
        }
        updatePinVisibility();
    }

    private Widget buildSearchableSelector(NodeDefinition.PinDefinition input, List<String> options, String selected) {
        AnimatedButton button = new AnimatedButton.Builder()
            .label(selected)
            .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
            .entranceAnimation(false)
            .build();
        button.setAction(() -> {
            var screen = ScreenManager.getInstance().getCurrentScreen();
            if (screen == null) return;
            Consumer<String> onSelected = option -> {
                if ("Loading".equals(option)) {
                    return;
                }
                node.getInputValues().put(input.getName(), option);
                button.setMessage(option);
                handleInputValueChanged(input);
            };
            if (screen instanceof FlowGraphDesignerScreen flowEditorScreen) {
                flowEditorScreen.showNodeInputSelector(options, button.getMessage(), onSelected, button.getX(), button.getY() + button.getHeight());
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
            selector.get().setSelectedItem(button.getMessage());
            screen.addDrawableChild(selector.get());
            selector.get().show(button.getX(), button.getY() + button.getHeight());
        });
        return button;
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
            flowEditorScreen.showNodeInputSelector(options, selected, onSelected, anchor.getX(), anchor.getY() + anchor.getHeight());
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
        selector.get().show(anchor.getX(), anchor.getY() + anchor.getHeight());
    }

    private void showScreenSelector(AnimatedButton anchor, List<String> options, String selected, Consumer<String> onSelected) {
        var screen = ScreenManager.getInstance().getCurrentScreen();
        if (screen == null || anchor == null || options == null || options.isEmpty() || onSelected == null) {
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
        selector.get().show(anchor.getX(), anchor.getY() + anchor.getHeight());
    }

    private List<String> resolveOptions(NodeDefinition.PinDefinition input) {
        List<String> options = input.getOptions();
        if (options != null && !options.isEmpty()) {
            return options;
        }
        if (isNexoExternalIdInput(input)) {
            return resolveNexoExternalIdOptions();
        }
        String source = input.getOptionsSource();
        String catalog = resolveMinecraftCatalog(input.getOptionsSource());
        if (catalog != null && (source == null || !source.startsWith("server:"))) {
            return catalogOptions(catalog);
        }
        if (source != null && !source.isBlank()) {
            List<String> values = OptionCatalogCache.getInstance().getValues(serverId, source);
            if (!values.isEmpty()) {
                return values;
            }
            if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
                requestOptionCatalog(source);
                return List.of("Loading");
            }
            return List.of();
        }
        return List.of();
    }

    private List<String> fallbackSearchableOptions(Object currentValue, String defaultValue) {
        String value = currentValue != null ? currentValue.toString() : defaultValue;
        if (value == null || value.isBlank()) {
            return List.of("Loading");
        }
        return List.of(value);
    }

    private List<String> resolveNexoExternalIdOptions() {
        List<String> values = new ArrayList<>();
        boolean loading = false;
        for (String source : nexoExternalIdSources()) {
            List<String> sourceValues = OptionCatalogCache.getInstance().getValues(serverId, source);
            if (!sourceValues.isEmpty()) {
                values.addAll(sourceValues);
                continue;
            }
            if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
                requestOptionCatalog(source);
                loading = true;
            }
        }
        if (!values.isEmpty()) {
            return values.stream()
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        }
        return loading ? List.of("Loading") : List.of();
    }

    private List<String> nexoExternalIdSources() {
        String contentType = CustomContentGraphAdapter.typeFromNode(node.getType());
        return switch (contentType) {
            case "block" -> List.of("server:custom_content:nexo_block", "server:custom_content:nexo_furniture");
            case "armor" -> List.of("server:custom_content:nexo_armor");
            default -> List.of("server:custom_content:nexo_item");
        };
    }

    private void requestOptionCatalog(String source) {
        FlowManager manager = FlowManager.getInstance();
        if (manager != null) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(source);
        }
    }

    private List<String> catalogOptions(String catalog) {
        return switch (catalog) {
            case "blocks", "block", "material" -> minecraftBlockOptions();
            case "particle" -> minecraftParticleOptions();
            case "sound" -> minecraftSoundOptions();
            default -> List.of();
        };
    }

    private List<String> minecraftSoundOptions() {
        if (!(Config.configManager instanceof RemotelyConfigManager remotelyConfigManager)) {
            return fallbackSoundOptions();
        }
        Path assetsDir = MinecraftAssetsManager.get(remotelyConfigManager).getActiveAssetsDir();
        if (assetsDir == null) {
            return fallbackSoundOptions();
        }
        Path sounds = assetsDir.resolve("minecraft").resolve("sounds.json");
        if (!Files.isRegularFile(sounds)) {
            return fallbackSoundOptions();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(sounds)).getAsJsonObject();
            List<String> options = new ArrayList<>();
            for (String key : root.keySet()) {
                options.add(key.toUpperCase().replace('.', '_'));
            }
            options.sort(String.CASE_INSENSITIVE_ORDER);
            return options;
        } catch (Exception ignored) {
            return fallbackSoundOptions();
        }
    }

    private List<String> fallbackSoundOptions() {
        return List.of(
            "AMBIENT_CAVE",
            "BLOCK_AMETHYST_BLOCK_CHIME",
            "BLOCK_ANVIL_LAND",
            "BLOCK_BEACON_ACTIVATE",
            "BLOCK_CHEST_OPEN",
            "BLOCK_FIRE_EXTINGUISH",
            "BLOCK_NOTE_BLOCK_BELL",
            "BLOCK_NOTE_BLOCK_PLING",
            "BLOCK_PORTAL_TRIGGER",
            "BLOCK_WOODEN_DOOR_OPEN",
            "ENTITY_ARROW_HIT_PLAYER",
            "ENTITY_ENDER_DRAGON_GROWL",
            "ENTITY_ENDERMAN_TELEPORT",
            "ENTITY_EXPERIENCE_ORB_PICKUP",
            "ENTITY_FIREWORK_ROCKET_BLAST",
            "ENTITY_GENERIC_EXPLODE",
            "ENTITY_GENERIC_HURT",
            "ENTITY_LIGHTNING_BOLT_THUNDER",
            "ENTITY_PLAYER_ATTACK_SWEEP",
            "ENTITY_PLAYER_LEVELUP",
            "ENTITY_WITHER_SPAWN",
            "ITEM_TRIDENT_THUNDER",
            "ITEM_TOTEM_USE",
            "UI_BUTTON_CLICK"
        );
    }

    private List<String> minecraftParticleOptions() {
        return List.of(
            "ANGRY_VILLAGER",
            "ASH",
            "BUBBLE",
            "CAMPFIRE_COSY_SMOKE",
            "CLOUD",
            "CRIT",
            "DAMAGE_INDICATOR",
            "DRAGON_BREATH",
            "DRIPPING_LAVA",
            "DRIPPING_WATER",
            "ELECTRIC_SPARK",
            "ENCHANT",
            "ENCHANTED_HIT",
            "END_ROD",
            "EXPLOSION",
            "FIREWORK",
            "FLAME",
            "FLASH",
            "GLOW",
            "HAPPY_VILLAGER",
            "HEART",
            "LARGE_SMOKE",
            "LAVA",
            "PORTAL",
            "SMALL_FLAME",
            "SMOKE",
            "SNOWFLAKE",
            "SOUL",
            "SOUL_FIRE_FLAME",
            "SWEEP_ATTACK",
            "WITCH"
        );
    }

    private List<String> minecraftBlockOptions() {
        if (!(Config.configManager instanceof RemotelyConfigManager remotelyConfigManager)) {
            return List.of();
        }
        Path assetsDir = MinecraftAssetsManager.get(remotelyConfigManager).getActiveAssetsDir();
        if (assetsDir == null) {
            return List.of();
        }
        Path blockStates = assetsDir.resolve("minecraft").resolve("blockstates");
        if (!Files.isDirectory(blockStates)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(blockStates)) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> "minecraft:" + path.getFileName().toString().replaceFirst("\\.json$", ""))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String resolveSelected(List<String> options, Object currentValue, String defaultValue) {
        String selected = currentValue != null ? currentValue.toString() : defaultValue;
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
        if (isCustomContentExternalIdInput(input)) {
            return isNexoProviderSelected() ? NodeDefinition.WidgetType.SEARCHABLE_LIST : NodeDefinition.WidgetType.TEXT;
        }
        String optionsSource = input.getOptionsSource();
        if (optionsSource != null && !optionsSource.isBlank()) {
            NodeRegistry registry = NodeRegistry.getInstance();
            FlowOptionSourceMetadata meta = registry != null ? registry.getServerOptionSource(serverId, optionsSource) : null;
            if (meta != null) {
                return meta.isSearchable() ? NodeDefinition.WidgetType.SEARCHABLE_LIST : NodeDefinition.WidgetType.DROPDOWN;
            }
            String catalog = resolveMinecraftCatalog(optionsSource);
            if (catalog != null) {
                return NodeDefinition.WidgetType.DROPDOWN;
            }
            return NodeDefinition.WidgetType.DROPDOWN;
        }
        if (input.getDataType() == FlowDataType.BOOLEAN) {
            return NodeDefinition.WidgetType.TOGGLE;
        }
        return NodeDefinition.WidgetType.TEXT;
    }

    private boolean isCustomContentProviderInput(NodeDefinition.PinDefinition input) {
        return "provider".equals(input.getName()) && CustomContentGraphAdapter.typeFromNode(node.getType()) != null;
    }

    private boolean isCustomContentExternalIdInput(NodeDefinition.PinDefinition input) {
        return "external_id".equals(input.getName()) && CustomContentGraphAdapter.typeFromNode(node.getType()) != null;
    }

    private boolean isNexoExternalIdInput(NodeDefinition.PinDefinition input) {
        return isCustomContentExternalIdInput(input) && isNexoProviderSelected();
    }

    private boolean isNexoProviderSelected() {
        if (node.getInputValues() == null) {
            return false;
        }
        Object provider = node.getInputValues().get("provider");
        return provider != null && "nexo".equalsIgnoreCase(provider.toString());
    }

    private String resolveMinecraftCatalog(String optionsSource) {
        if (optionsSource == null || optionsSource.isBlank()) {
            return null;
        }
        String catalog = null;
        if (optionsSource.startsWith("server:minecraft:")) {
            catalog = optionsSource.substring("server:minecraft:".length());
        } else if (optionsSource.startsWith("client:minecraft:")) {
            catalog = optionsSource.substring("client:minecraft:".length());
        } else if (optionsSource.startsWith("minecraft:")) {
            catalog = optionsSource.substring("minecraft:".length());
        }
        if (catalog == null) {
            return null;
        }
        NodeRegistry registry = NodeRegistry.getInstance();
        FlowOptionSourceMetadata meta = registry != null ? registry.getServerOptionSource(serverId, optionsSource) : null;
        if (meta != null) {
            return catalog;
        }
        return catalog;
    }

    private void seedDefaultInputValues() {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        for (NodeDefinition.PinDefinition input : inputs) {
            if (input.getDefaultValue() != null && !node.getInputValues().containsKey(input.getName())) {
                node.getInputValues().put(input.getName(), convertValue(input.getDefaultValue(), input.getDataType()));
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
                if (value != null && expected.equalsIgnoreCase(value.toString().trim())) {
                    return true;
                }
            }
            return false;
        }
        String actual = actualValue != null ? actualValue.toString().trim() : "";
        return actual.equalsIgnoreCase(expected);
    }

    public void refreshInputWidgets() {
        applyFunctionParameterPins();
        inputWidgets.clear();
        createInputWidgets();
        createOutputWidgets();
        updateSize();
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
                outputs.add(new NodeDefinition.PinDefinition(parameter.getName(), NodeDefinition.PinType.DATA, NodeDefinition.PinDirection.OUTPUT, parameter.getType()));
            }
        }

        if (isFunctionEndNode() && graph.getFunctionOutputs() != null) {
            for (FlowGraph.FunctionParameter parameter : graph.getFunctionOutputs()) {
                if (!isValidFunctionParameter(parameter)) {
                    continue;
                }
                inputs.add(new NodeDefinition.PinDefinition(parameter.getName(), NodeDefinition.PinType.DATA, NodeDefinition.PinDirection.INPUT, parameter.getType()));
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
                return serverTypes;
            }
        }
        return FlowDataType.values();
    }

    public List<FlowGraph.FunctionParameter> getFunctionParameterList() {
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
    }

    public void showParamContextMenu() {
        var currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen == null) return;

        List<FlowGraph.FunctionParameter> params = getFunctionParameterList();
        if (params == null) return;

        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(currentScreen);
        builder.addHeaderButton("add.png", this::showAddFunctionParameterPopup, "Add Parameter", ThemeManager.getAccent("nice"));
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
        PopupWidget.Builder builder = new PopupWidget.Builder("Add Parameter").setResizable(false);
        TextInputWidget nameInput = new TextInputWidget.Builder()
            .placeholder("parameter_name")
            .size(200, 20)
            .build();
        List<FlowDataType> types = getSupportedFunctionTypes();
        FlowDataType[] selectedType = new FlowDataType[]{FlowDataType.ANY};
        List<String> typeOptions = types.stream().map(FlowDataType::getId).toList();
        AnimatedButton typeButton = buildScreenSelectorButton(typeOptions, selectedType[0].getId(), 200, value -> {
            selectedType[0] = FlowDataType.fromString(value);
        });

        builder.addRow("Name", true, 20, nameInput);
        builder.addRow("Type", true, 18, typeButton);

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
                    type = FlowDataType.ANY;
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
                targetList.add(new FlowGraph.FunctionParameter(rawName, type));
                refreshInputWidgets();
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .size(80, 18)
            .animateElevation(false)
            .entranceAnimation(false)
            .build();

        builder.addRow("", true, 18, addButton);
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
        }
    }

    public void removeFunctionParameter(String name) {
        List<FlowGraph.FunctionParameter> params = getFunctionParameterList();
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i) != null && name.equals(params.get(i).getName())) {
                params.remove(i);
                removeParameterConnections(name);
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

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        int headerBg = ThemeManager.getColor(ThemeColor.inClickableBackground);
        int headerText = ThemeManager.getColor(ThemeColor.text);
        int labelText = ThemeManager.getColor(ThemeColor.textDark);
        int borderColor = ThemeManager.getColor(ThemeColor.innerBorder);

        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + TITLE_HEIGHT, headerBg);
        Render.drawInnerBorder(ctx, getX(), getY(), getWidth(), TITLE_HEIGHT, borderColor);
        ctx.fill(getX(), getY() + TITLE_HEIGHT, getWidth() + getX(), getY() + TITLE_HEIGHT + 1, this.borderColor);
        ctx.drawText(definition != null ? definition.getDisplayName() : "Loading", getX() + 4, getY() + 4, headerText, shadow);

        if (closeButton.visible) {
            int closeX = getX() + getWidth() - PADDING - CLOSE_BUTTON_WIDTH;
            int closeY = (getY() + (TITLE_HEIGHT - CLOSE_BUTTON_HEIGHT) / 2) - 1;
            closeButton.setPosition(closeX, closeY);
            if (paramButton != null) {
                paramButton.setPosition(closeX - CLOSE_BUTTON_WIDTH - 4, closeY);
                paramButton.render(ctx, mouseX, mouseY, 0);
            }
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
            int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
            int pinY = rowY + (ROW_HEIGHT - PIN_BUTTON_SIZE) / 2;
            int pinX = getX() + PADDING;
            drawPinButton(ctx, pinX, pinY, getPinColor(input.getDataType()));
            int textY = rowY + (ROW_HEIGHT - ITextRenderer.fontHeight) / 2 + 1;
            ctx.drawText(input.getName(), pinX + PIN_BUTTON_SIZE + PIN_TEXT_GAP, textY, labelText, shadow);
        }

        for (int i = 0; i < visibleOutputs.size(); i++) {
            NodeDefinition.PinDefinition output = visibleOutputs.get(i);
            int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
            int pinY = rowY + (ROW_HEIGHT - PIN_BUTTON_SIZE) / 2;
            int pinX = rightColumnStart + rightColumnWidth - PIN_BUTTON_SIZE;
            int textY = rowY + (ROW_HEIGHT - ITextRenderer.fontHeight) / 2 + 1;
            AnimatedButton branchWidget = getBranchWidget(output.getName());
            if (branchWidget == null) {
                String outputLabel = passthroughInputPin(output.getName());
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

        ctx.disableScissor();
        ctx.popScissorState();
        renderExpandedDropdownOverlays(ctx, mouseX, mouseY);
    }

    private void renderExpandedDropdownOverlays(IDrawContext ctx, int mouseX, int mouseY) {
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
    }

    private void updateSize() {
        int rowCount = Math.max(visibleInputs.size(), visibleOutputs.size());
        int leftColumnWidth = getLeftColumnWidth();
        int rightColumnWidth = getRightColumnWidth();
        int contentWidth = leftColumnWidth + rightColumnWidth + (leftColumnWidth > 0 && rightColumnWidth > 0 ? COLUMN_GAP : 0);
        int contentHeight = rowCount > 0 ? (rowCount * ROW_HEIGHT + (rowCount - 1) * ROW_SPACING) : 0;
        if (addBranchButton != null && addBranchButton.visible) {
            contentHeight += ROW_HEIGHT + ROW_SPACING;
        }
        int minWidth = (visibleInputs.isEmpty() || visibleOutputs.isEmpty()) ? SINGLE_COLUMN_MIN_WIDTH : DEFAULT_WIDTH;
        int titleWidth = tr.getWidth(definition != null ? definition.getDisplayName() : node.getType()) + PADDING * 2;
        if (closeButton.visible) {
            titleWidth += CLOSE_BUTTON_WIDTH + PADDING;
        }
        if (paramButton != null) {
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
        ctx.fill(x, y, x + PASSTHROUGH_DASH_SIZE, y + PASSTHROUGH_DASH_SIZE, ThemeManager.getColor(ThemeColor.inClickableBackground));
        Render.drawInnerBorder(ctx, x, y, PASSTHROUGH_DASH_SIZE, PASSTHROUGH_DASH_SIZE, border);
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

        int rowY = getRowStartY() + index * (ROW_HEIGHT + ROW_SPACING);
        int pinY = rowY + (ROW_HEIGHT - PIN_BUTTON_SIZE) / 2;
        int pinX = isInput ? getX() + PADDING : getX() + getWidth() - PADDING - PIN_BUTTON_SIZE;
        return new double[]{pinX, pinY, PIN_BUTTON_SIZE, PIN_BUTTON_SIZE};
    }

    public boolean isMouseOverPin(int wx, int wy) {
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
        Widget bestWidget = null;
        for (Widget widget : inputWidgets.values()) {
            if (widget.isVisible() && widget.isMouseOver(wx, wy)) {
                if (bestWidget == null || widget.getPriority() > bestWidget.getPriority()) {
                    bestWidget = widget;
                }
            }
        }
        return bestWidget;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int wx = (int)mouseX;
        int wy = (int)mouseY;

        if (closeButton.visible && closeButton.isMouseOver(wx, wy)) {
            closeButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (paramButton != null && paramButton.isMouseOver(wx, wy)) {
            paramButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        Widget outputWidget = getOutputWidgetAt(wx, wy);
        if (outputWidget != null) {
            outputWidget.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (isMouseOverPin(wx, wy)) {
            return true;
        }

        Widget inputWidget = getInputWidgetAt(wx, wy);
        if (inputWidget != null) {
            inputWidget.mouseClicked(mouseX, mouseY, button);
            if (inputWidget instanceof TextInputWidget) {
                if (ScreenManager.getInstance().getCurrentScreen() != null) {
                    ScreenManager.getInstance().getCurrentScreen().setFocusedWidget(inputWidget);
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (Widget widget : inputWidgets.values()) {
            widget.mouseReleased(mouseX, mouseY, button);
        }
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null) {
                branch.widget.mouseReleased(mouseX, mouseY, button);
            }
        }
        if (addBranchButton != null && addBranchButton.visible) {
            addBranchButton.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && focusedWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        for (Widget widget : inputWidgets.values()) {
            if (widget != focusedWidget && widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && branch.widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        if (addBranchButton != null && addBranchButton.visible && addBranchButton.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && branch.widget.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        for (Widget widget : inputWidgets.values()) {
            if (widget.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && focusedWidget.charTyped(chr, modifiers)) {
            saveInputValue();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && focusedWidget.keyPressed(keyCode, scanCode, modifiers)) {
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

    private void updateInputWidgetPositions() {
        int leftColumnWidth = getLeftColumnWidth();
        int leftColumnEnd = getX() + PADDING + leftColumnWidth;

        for (int i = 0; i < visibleInputs.size(); i++) {
            NodeDefinition.PinDefinition input = visibleInputs.get(i);
            Widget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
                int widgetWidth = getInputWidgetWidth(inputWidget);
                int widgetHeight = getInputWidgetHeight(inputWidget);
                int widgetY = rowY + (ROW_HEIGHT - widgetHeight) / 2;
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
            int labelWidth = tr.getWidth(input.getName());
            int rowWidth = PIN_BUTTON_SIZE + PIN_TEXT_GAP + labelWidth;
            Widget widget = inputWidgets.get(input.getName());
            if (widget != null) {
                rowWidth += INPUT_FIELD_GAP + getInputWidgetWidth(widget);
            }
            width = Math.max(width, rowWidth);
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
                int labelWidth = tr.getWidth(output.getName());
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
        ctx.fill(x, y, x + PIN_BUTTON_SIZE, y + PIN_BUTTON_SIZE, background);
        Render.drawInnerBorder(ctx, x, y, PIN_BUTTON_SIZE, PIN_BUTTON_SIZE, border);
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
                typedValue = convertValue(value, def.getDataType());
            } else if (widget instanceof ToggleWidget toggle) {
                typedValue = toggle.getValue();
            } else if (widget instanceof AnimatedButton button) {
                String value = button.getMessage();
                if (value == null || value.isBlank() || "Loading".equals(value)) {
                    continue;
                }
                typedValue = convertValue(value, def.getDataType());
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

        if (flowOutputs.size() <= 2) {
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
        button.setAction(() -> showStringSelector(button, options, button.getMessage(), value -> {
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
                int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
                int widgetWidth = getOutputWidgetWidth(branchWidget);
                int widgetHeight = getOutputWidgetHeight(branchWidget);
                int widgetY = rowY + (ROW_HEIGHT - widgetHeight) / 2;
                int widgetX = buttonX + rightColumnWidth - PIN_BUTTON_SIZE - PIN_TEXT_GAP - widgetWidth;
                branchWidget.setPosition(widgetX, widgetY);
                branchWidget.setWidth(widgetWidth);
                branchWidget.setHeight(widgetHeight);
                branchWidget.setPriority(visibleOutputs.size() - i);
                branchWidget.setLayer(visibleOutputs.size() - i);
            }
        }

        if (addBranchButton != null && addBranchButton.visible) {
            int rowCount = Math.max(visibleInputs.size(), visibleOutputs.size());
            int rowY = getRowStartY() + rowCount * (ROW_HEIGHT + ROW_SPACING);
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
