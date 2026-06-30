package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.data.flow.SyncedResourceState;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.StudioPanel;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import redxax.oxy.remotely.packcontent.PackContentRegistry;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
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
import java.util.function.Function;

public class ContentDesignerScreen extends GraphEditorScreen {
    private final String flowId;
    private final FlowManager flowManager;
    private final Screen contentDesignerParent;
    private final ReSyncStudioPanelState panelState = new ReSyncStudioPanelState();
    private StudioPanel contentStudioPanel;
    private StudioPanel attributeDesignerPanel;
    private SidePanel contentPanel;
    private SidePanel attributePanel;
    private String selectedBranch = "";
    private String selectedAttributeComponent = "";
    private MountableButtonWidget summaryWidget;
    private final Map<String, MountableButtonWidget> eventRows = new HashMap<>();
    private final List<AnimatedWidget> contentPanelWidgets = new ArrayList<>();
    private final List<AnimatedWidget> attributePanelWidgets = new ArrayList<>();
    private final List<DropDownWidget<String>> panelDropdowns = new ArrayList<>();
    private List<AnimatedWidget> collectingAttributeEditorWidgets;
    private ItemSelectorWidget activeSearchSelector;
    private MountableButtonWidget attributeHeaderWidget;
    private AnimatedWidget selectedAttributeRowWidget;
    private TextInputWidget attributeSearchInput;
    private int attributePanelStaticWidgetCount;
    private boolean collectingAttributePanelOrder;
    private boolean attributeDesignerOpen;
    private boolean attributeDesignerHidContentBrowser;
    private boolean attributeRestoreSearchFocus;
    private String activeAttributeQuery = "";
    private Map<String, Object> activeAttributeComponents = new LinkedHashMap<>();
    private final Map<String, Object> attributePreviewValues = new LinkedHashMap<>();
    private final Map<String, AttributeComponentRowState> attributeComponentRowStates = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> attributeSectionRows = new LinkedHashMap<>();
    private final Map<String, MountableButtonWidget> attributeStatusRows = new LinkedHashMap<>();
    private List<Map<String, Object>> attributeValidationErrors = List.of();
    private static final String ATTRIBUTE_SCHEMA_SOURCE = "server:minecraft:item_attribute_schema";
    private static final Set<String> STUDIO_ROOT_INPUTS = Set.of(
        "content_id",
        "name",
        "material",
        "provider",
        "external_id",
        "custom_model_data",
        "components",
        "lore",
        "tags",
        "enabled",
        "priority",
        "cooldown_scope",
        "cooldown_ticks",
        "permission",
        "cancel_event",
        "consume_event",
        "require_sneaking",
        "require_on_ground",
        "hand_filter",
        "target_filter",
        "allowed_worlds",
        "denied_worlds",
        "chance_percent",
        "max_activations_per_tick",
        CustomContentGraphAdapter.FLOW_BRANCHES_KEY
    );

    public ContentDesignerScreen(String serverId, ClientServerView server, String flowId, Screen parent) {
        super(loadGraph(serverId, flowId), serverId, parent);
        this.flowId = flowId;
        this.contentDesignerParent = parent;
        FlowManager manager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
        this.flowManager = manager != null ? manager : FlowManager.getInstance();
    }

    private static FlowGraph loadGraph(String serverId, String flowId) {
        FlowManager manager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
        if (manager == null) {
            manager = FlowManager.getInstance();
        }
        if (manager == null || serverId == null || flowId == null) {
            return new FlowGraph();
        }
        FlowGraph graph = manager.getFlowsForServer(serverId).get(flowId);
        if (graph != null) {
            return graph;
        }
        FlowGraph fallback = new FlowGraph();
        fallback.setId(flowId);
        return fallback;
    }

    @Override
    public String getDesktopAppId() {
        return "content-designer";
    }

    @Override
    public String getDesktopAppTitle() {
        return "Content Designer";
    }

    @Override
    public String getDesktopAppIconPath() {
        return "ReSync.png";
    }

    @Override
    public void init() {
        ensureContentStart();
        if (widgetCache.size() != graph.getNodes().size()) {
            refreshNodeRegistry();
        }
        super.init();
        if (!CustomContentGraphAdapter.isContentGraph(graph)) {
            close();
            return;
        }
        selectedBranch = firstBranch();
        preloadWorldOptions();
        preloadAttributeSchema();
        buildContentPanel();
        refreshContentPanel();
    }

    @Override
    protected FlowNodeWidget createNodeWidget(String nodeId, FlowNode node) {
        if (node != null && CustomContentGraphAdapter.typeFromNode(node.getType()) != null) {
            return new StudioRootNodeWidget((int) node.getX(), (int) node.getY(), node, graph, nodeId, serverId, () -> {});
        }
        return super.createNodeWidget(nodeId, node);
    }

    @Override
    protected void addCustomHeaderButtons() {
    }

    @Override
    protected boolean shouldCreatePaletteSidePanel() {
        return false;
    }

    @Override
    protected void onOptionCatalogRefreshed() {
        super.onOptionCatalogRefreshed();
        refreshContentPanel();
        if (attributeDesignerOpen) {
            refreshAttributeDesignerContent(true);
        }
    }

    @Override
    public void renderHandler(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderHandler(context, mouseX, mouseY, delta);
        if (contentStudioPanel != null) {
            renderStudioPanel(contentStudioPanel, context, mouseX, mouseY, delta);
        }
        if (attributeDesignerPanel != null) {
            renderStudioPanel(attributeDesignerPanel, context, mouseX, mouseY, delta);
        }
        renderPanelDropdownOverlays(context, mouseX, mouseY, delta);
        renderActiveSearchSelector(context, mouseX, mouseY, delta);
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        if (contentPanel != null) {
            if (contentStudioPanel != null) {
                contentStudioPanel.layout();
            }
        }
        if (attributePanel != null && attributeDesignerPanel != null) {
            attributeDesignerPanel.layout();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (clickExpandedPanelDropdown(mouseX, mouseY, button)) {
            return true;
        }
        if (isAttributeDesignerInteractive() && attributePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (contentPanel != null && contentPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        for (DropDownWidget<String> dropdown : panelDropdowns) {
            if (dropdown.isVisible() && dropdown.isExpanded() && dropdown.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (isAttributeDesignerInteractive() && attributePanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (contentPanel != null && contentPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        for (DropDownWidget<String> dropdown : panelDropdowns) {
            if (dropdown.isVisible() && dropdown.isExpanded() && dropdown.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        if (isAttributeDesignerInteractive() && attributePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (contentPanel != null && contentPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        for (DropDownWidget<String> dropdown : panelDropdowns) {
            if (dropdown.isVisible() && dropdown.isExpanded() && dropdown.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
                return true;
            }
        }
        if (isAttributeDesignerInteractive() && attributePanel.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (contentPanel != null && contentPanel.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (isAttributeDesignerInteractive() && attributePanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (contentPanel != null && contentPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.charTyped(chr, modifiers)) {
            return true;
        }
        if (isAttributeDesignerInteractive() && attributePanel.charTyped(chr, modifiers)) {
            return true;
        }
        if (contentPanel != null && contentPanel.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    protected void onSave() {
        super.onSave();
        updateSummary();
        new Notification("Content", "Saved Content", Notification.Type.SUCCESS);
    }

    @Override
    public void close() {
        closeActiveSearchSelector();
        hideAttributeDesigner();
        super.close();
    }

    @Override
    public void removed() {
        closeActiveSearchSelector();
        restoreAttributeDesignerContentBrowser();
        super.removed();
    }

    private void buildContentPanel() {
        contentStudioPanel = rightStudioPanel("contentPanel")
            .show();
        contentPanel = contentStudioPanel.sidePanel();
        contentStudioPanel.padding(panelState.padding());
        attributeDesignerPanel = leftStudioPanel("attributeDesignerPanel")
            .hide();
        attributePanel = attributeDesignerPanel.sidePanel();
        attributePanel.minWidth(360).width(420);
        attributeDesignerPanel.padding(panelState.padding());
    }

    public static void handleAttributeValidationErrorsForServer(String serverId, List<Map<String, Object>> errors) {
        for (GraphEditorScreen screen : OPEN_SCREENS) {
            if (screen instanceof ContentDesignerScreen contentScreen && serverId != null && serverId.equals(contentScreen.getServerId())) {
                contentScreen.attributeValidationErrors = errors != null ? List.copyOf(errors) : List.of();
                if (contentScreen.attributeDesignerOpen) {
                    contentScreen.refreshAttributeDesignerContent(true);
                } else {
                    contentScreen.refreshContentPanel();
                }
            }
        }
    }

    private void refreshContentPanel() {
        if (contentPanel == null) {
            return;
        }
        Container container = contentPanel.container();
        clearContentPanelWidgets(container);
        eventRows.clear();
        panelDropdowns.clear();
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        String type = CustomContentGraphAdapter.contentType(graph);
        if (definition == null || type == null) {
            return;
        }
        int rowWidth = contentRowWidth();
        summaryWidget = new MountableButtonWidget.Builder(definition.getDisplayName())
            .description(contentSummary(definition))
            .iconPath(iconForType(type))
            .onClick(() -> selectedBranch = firstBranch())
            .build();
        summaryWidget.setSize(rowWidth, 30);
        insertContentPanelWidget(container, summaryWidget);
        insertContentPanelWidget(container, textRow("Name", definition.getDisplayName(), rowWidth, value -> {
            setProperty("name", value);
            updateSummary();
        }));
        insertContentPanelWidget(container, dropdownRow("Type", List.of("item", "armor", "block"), type, rowWidth, value -> {
            FlowNode start = CustomContentGraphAdapter.findStartNode(graph);
            if (start != null) {
                start.setType(CustomContentGraphAdapter.nodeType(value));
                setProperty("material", defaultMaterial(value));
                selectedBranch = firstBranch();
                refreshNodeRegistry();
                refreshContentPanel();
            }
        }));
        insertContentPanelWidget(container, dropdownRow("Provider", providerOptions(), definition.getProvider(), rowWidth, value -> {
            setProperty("provider", value);
            refreshNodeRegistry();
            refreshContentPanel();
        }));
        if ("vanilla".equalsIgnoreCase(definition.getProvider())) {
            insertContentPanelWidget(container, searchableRow("Material", materialOptions(), definition.getMaterial(), rowWidth, value -> {
                setProperty("material", value.toUpperCase(Locale.ROOT));
                requestCatalog(attributeSchemaSource(value));
                updateSummary();
            }));
        } else {
            insertContentPanelWidget(container, searchableRow("External ID", providerAssetOptions(definition.getProvider()), definition.getExternalId(), rowWidth, value -> {
                setProperty("external_id", value);
                updateSummary();
            }));
        }
        insertContentPanelWidget(container, textRow("Model", definition.getCustomModelData() == null ? "" : String.valueOf(definition.getCustomModelData()), rowWidth, value -> {
            setProperty("custom_model_data", value);
            updateSummary();
        }));
        insertContentPanelWidget(container, contentSectionHeader("Display Text", textSummary(definition), rowWidth));
        addTextRows(container, definition, rowWidth);
        insertContentPanelWidget(container, contentSectionHeader("Item Behavior", componentCountSummary(definition.getComponents()), rowWidth));
        addComponentDashboardRows(container, definition, rowWidth);
        insertContentPanelWidget(container, contentSectionHeader("Trigger Rules", ruleSummary(), rowWidth));
        addRulesRows(container, type, rowWidth);
        insertContentPanelWidget(container, contentSectionHeader("Events", CustomContentGraphAdapter.getEnabledTriggerBranches(graph).size() + " Enabled", rowWidth));
        for (CustomContentGraphAdapter.TriggerDescriptor trigger : CustomContentGraphAdapter.triggersForType(type)) {
            insertContentPanelWidget(container, eventRow(trigger, rowWidth));
        }
        container.updateWidgetPositions();
    }

    private void clearContentPanelWidgets(Container container) {
        for (AnimatedWidget widget : new ArrayList<>(contentPanelWidgets)) {
            container.removeWidget(widget);
        }
        contentPanelWidgets.clear();
    }

    private void insertContentPanelWidget(Container container, AnimatedWidget widget) {
        ReSyncStudioPanelState.disableEntrance(widget);
        container.addWidget(widget);
        contentPanelWidgets.add(widget);
    }

    private void clearAttributePanelWidgets(Container container) {
        for (AnimatedWidget widget : new ArrayList<>(attributePanelWidgets)) {
            container.removeWidget(widget);
        }
        attributePanelWidgets.clear();
    }

    private void insertAttributePanelWidget(Container container, AnimatedWidget widget) {
        ReSyncStudioPanelState.disableEntrance(widget);
        if (collectingAttributeEditorWidgets != null) {
            collectingAttributeEditorWidgets.add(widget);
            return;
        }
        attributePanelWidgets.add(widget);
        if (!collectingAttributePanelOrder) {
            container.addWidget(widget);
        }
    }

    private int contentRowWidth() {
        if (contentPanel == null) {
            return Math.max(ReSyncStudioPanelState.MIN_ROW_WIDTH, ReSyncStudioPanelState.DEFAULT_WIDTH - panelState.padding() * 2);
        }
        return contentStudioPanel != null ? contentStudioPanel.rowWidth() : Math.max(ReSyncStudioPanelState.MIN_ROW_WIDTH, contentPanel.getDesiredWidth() - panelState.padding() * 2);
    }

    private int attributeRowWidth() {
        if (attributePanel == null) {
            return 400;
        }
        int desired = attributePanel.getDesiredWidth();
        if (desired <= 0) {
            desired = 420;
        }
        return Math.max(340, desired - panelState.padding() * 2);
    }

    @Override
    protected int viewportFitLeft() {
        int left = super.viewportFitLeft();
        if (attributePanel != null && attributePanel.isVisible() && attributePanel.isLeftAnchored()) {
            left += attributePanel.getDesiredWidth() + 8;
        }
        return left;
    }

    @Override
    protected int viewportFitWidth() {
        int fitWidth = super.viewportFitWidth();
        if (attributePanel != null && attributePanel.isVisible() && attributePanel.isLeftAnchored()) {
            fitWidth -= attributePanel.getDesiredWidth() + 8;
        }
        if (contentPanel != null && contentPanel.isVisible()) {
            fitWidth -= contentPanel.getDesiredWidth() + 8;
        }
        return Math.max(1, fitWidth);
    }

    private void addLogicRows(Container container, String type, int rowWidth) {
        MountableButtonWidget selectedRow = new MountableButtonWidget.Builder(eventTitle(selectedBranch))
            .description(actionSummary(selectedBranch))
            .iconPath("graph.png")
            .addButton(new SquareButtonWidget.Builder().imagePath("search.png").hint("Focus").entranceAnimation(false).onClick(() -> focusContentBranch(selectedBranch)).build())
            .build();
        insertContentPanelWidget(container, selectedRow);
        for (CustomContentGraphAdapter.TriggerDescriptor trigger : CustomContentGraphAdapter.triggersForType(type)) {
            MountableButtonWidget row = new MountableButtonWidget.Builder(eventTitle(trigger.pin()))
                .description((trigger.pin().equals(selectedBranch) ? "Selected" : "Open") + " | " + actionSummary(trigger.pin()))
                .iconPath("graph.png")
                .onClick(() -> {
                    selectedBranch = trigger.pin();
                    focusContentBranch(selectedBranch);
                    refreshContentPanel();
                })
                .build();
            row.setSize(rowWidth, 28);
            row.setSelected(trigger.pin().equals(selectedBranch));
            insertContentPanelWidget(container, row);
        }
    }

    private void addRulesRows(Container container, String type, int rowWidth) {
        insertContentPanelWidget(container, textRow("Permission", textProperty("permission"), rowWidth, value -> setProperty("permission", value)));
        insertContentPanelWidget(container, textRow("Cooldown", textProperty("cooldown_ticks"), rowWidth, value -> setProperty("cooldown_ticks", parseInt(value))));
        insertContentPanelWidget(container, textRow("Chance", textProperty("chance_percent"), rowWidth, value -> setProperty("chance_percent", parseDouble(value))));
        TextInputWidget worlds = new TextInputWidget.Builder()
            .text(textProperty("allowed_worlds"))
            .placeholder("Worlds")
            .forcePlaceholder(false)
            .size(174, 18)
            .onChange(value -> setProperty("allowed_worlds", value))
            .build();
        ReSyncStudioPanelState.disableEntrance(worlds);
        TitledRowWidget worldsRow = new TitledRowWidget.Builder().title("Worlds").description(contentPanelDescription("Worlds")).size(rowWidth, 36).padding(4).addWidget(searchableInputRow(worlds, worldOptions(), true, "server:minecraft:world")).build();
        insertContentPanelWidget(container, worldsRow);
        RowWidget toggles = new RowWidget.Builder()
            .size(rowWidth, 18)
            .padding(4)
            .addWidget(new ToggleWidget.Builder().label("Cancel").toggled(boolProperty("cancel_event")).size(90, 18).entranceAnimation(false).onChange(value -> setProperty("cancel_event", value)).build())
            .addWidget(new ToggleWidget.Builder().label("Consume").toggled(boolProperty("consume_event")).size(90, 18).entranceAnimation(false).onChange(value -> setProperty("consume_event", value)).build())
            .build();
        insertContentPanelWidget(container, toggles);
        if (showsHandFilter(type, selectedBranch)) {
            insertContentPanelWidget(container, dropdownRow("Hand", List.of("any", "main hand", "offhand"), textProperty("hand_filter"), rowWidth, value -> setProperty("hand_filter", value)));
        }
        if (showsTargetFilter(selectedBranch)) {
            insertContentPanelWidget(container, dropdownRow("Target", List.of("any", "player", "living entity", "hostile", "passive"), textProperty("target_filter"), rowWidth, value -> setProperty("target_filter", value)));
        }
    }

    private void addTextRows(Container container, CustomContentDefinition definition, int rowWidth) {
        CodeEditorWidget lore = new CodeEditorWidget(0, 0, Math.max(180, rowWidth - 18), 78);
        ReSyncStudioPanelState.disableEntrance(lore);
        lore.setShowLineNumbers(false);
        lore.setShowSearchNavigation(false);
        lore.setDimNonMatchingLines(false);
        lore.setShowCursorLineHighlight(false);
        lore.setShowSearchMatchHighlight(false);
        lore.setWordWrap(true);
        lore.setText(String.join("\n", definition.getLore() != null ? definition.getLore() : List.of()));
        lore.onChange = text -> setProperty("lore", text);
        insertContentPanelWidget(container, new TitledRowWidget.Builder()
            .title("Lore")
            .description(contentPanelDescription("Lore"))
            .size(rowWidth, 94)
            .padding(4)
            .addWidget(lore)
            .build());
        insertContentPanelWidget(container, textRow("Tags", String.join(", ", definition.getTags() != null ? definition.getTags() : List.of()), rowWidth, value -> setProperty("tags", value)));
    }

    private MountableButtonWidget contentSectionHeader(String title, String description, int width) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title)
            .description(description)
            .build();
        row.setSize(width, 24);
        row.setAccent(ThemeManager.getAccent("calm"));
        return row;
    }

    private String textSummary(CustomContentDefinition definition) {
        int lore = definition.getLore() != null ? definition.getLore().size() : 0;
        int tags = definition.getTags() != null ? definition.getTags().size() : 0;
        return lore + " Lore | " + tags + " Tags";
    }

    private String ruleSummary() {
        List<String> parts = new ArrayList<>();
        if (!textProperty("permission").isBlank()) {
            parts.add("Permission");
        }
        if (parseInt(textProperty("cooldown_ticks")) > 0) {
            parts.add("Cooldown");
        }
        if (parseDouble(textProperty("chance_percent")) < 100) {
            parts.add("Chance");
        }
        if (!textProperty("allowed_worlds").isBlank()) {
            parts.add("Worlds");
        }
        if (boolProperty("cancel_event")) {
            parts.add("Cancel");
        }
        if (boolProperty("consume_event")) {
            parts.add("Consume");
        }
        return parts.isEmpty() ? "No Restrictions" : String.join(" | ", parts);
    }

    private void addAssetRows(Container container, CustomContentDefinition definition, int rowWidth) {
        insertContentPanelWidget(container, dropdownRow("Provider", providerOptions(), definition.getProvider(), rowWidth, value -> {
            setProperty("provider", value);
            refreshNodeRegistry();
            refreshContentPanel();
        }));
        if ("vanilla".equalsIgnoreCase(definition.getProvider())) {
            insertContentPanelWidget(container, searchableRow("Material", materialOptions(), definition.getMaterial(), rowWidth, value -> {
                setProperty("material", value.toUpperCase(Locale.ROOT));
                requestCatalog(attributeSchemaSource(value));
                updateSummary();
            }));
        } else {
            insertContentPanelWidget(container, searchableRow("External ID", providerAssetOptions(definition.getProvider()), definition.getExternalId(), rowWidth, value -> {
                setProperty("external_id", value);
                updateSummary();
            }));
        }
        insertContentPanelWidget(container, textRow("Model", definition.getCustomModelData() == null ? "" : String.valueOf(definition.getCustomModelData()), rowWidth, value -> setProperty("custom_model_data", value)));
    }

    private void addPreviewRows(Container container, CustomContentDefinition definition, String type, int rowWidth) {
        insertContentPanelWidget(container, new MountableButtonWidget.Builder(definition.getDisplayName())
            .description(contentCardPreview(definition, type))
            .iconPath(iconForType(type))
            .build());
        insertContentPanelWidget(container, new MountableButtonWidget.Builder("Events")
            .description(CustomContentGraphAdapter.getEnabledTriggerBranches(graph).size() + " Enabled | " + branchActionCount(selectedBranch) + " Actions")
            .iconPath("graph.png")
            .build());
        insertContentPanelWidget(container, new MountableButtonWidget.Builder("State")
            .description(saveState(definition))
            .iconPath("save.png")
            .build());
        insertContentPanelWidget(container, new MountableButtonWidget.Builder("Validation")
            .description(contentValidationSummary(definition))
            .iconPath("stop.png")
            .build());
    }

    private void addComponentDashboardRows(Container container, CustomContentDefinition definition, int rowWidth) {
        Map<String, Object> components = definition.getComponents() != null ? definition.getComponents() : Map.of();
        insertContentPanelWidget(container, componentToggleRow(
            "Consumable",
            consumableSummary(components),
            components.containsKey("minecraft:consumable") || components.containsKey("minecraft:food"),
            rowWidth,
            this::setConsumableEnabled
        ));
        insertContentPanelWidget(container, componentToggleRow(
            "Glint",
            glintSummary(components),
            Boolean.TRUE.equals(components.get("minecraft:enchantment_glint_override")),
            rowWidth,
            this::setGlintEnabled
        ));
        MountableButtonWidget componentsRow = new MountableButtonWidget.Builder("Components")
            .description(componentCountSummary(components))
            .iconPath("item.png")
            .addButton(new SquareButtonWidget.Builder().imagePath("graph.png").hint("Edit Attributes").entranceAnimation(false).onClick(this::openAttributeDesigner).build())
            .build();
        componentsRow.setSize(rowWidth, 30);
        insertContentPanelWidget(container, componentsRow);
    }

    private MountableButtonWidget componentToggleRow(String title, String description, boolean enabled, int rowWidth, Consumer<Boolean> onChange) {
        ToggleWidget toggle = new ToggleWidget.Builder()
            .toggled(enabled)
            .size(32, 18)
            .onChange(onChange)
            .entranceAnimation(false)
            .build();
        MountableButtonWidget row = new MountableButtonWidget.Builder(title)
            .description(description)
            .iconPath("item.png")
            .addWidget(toggle)
            .build();
        row.setSize(rowWidth, 30);
        row.setSelected(enabled);
        return row;
    }

    private String consumableSummary(Map<String, Object> components) {
        boolean consumable = components.containsKey("minecraft:consumable");
        boolean food = components.containsKey("minecraft:food");
        if (!consumable && !food) {
            return "Off";
        }
        String seconds = consumableSeconds(components.get("minecraft:consumable"));
        String nutrition = foodNutrition(components.get("minecraft:food"));
        if (!seconds.isBlank() && !nutrition.isBlank()) {
            return "Eat " + seconds + "s | Food +" + nutrition;
        }
        if (!seconds.isBlank()) {
            return "Eat " + seconds + "s";
        }
        if (!nutrition.isBlank()) {
            return "Food +" + nutrition;
        }
        return "On";
    }

    private String glintSummary(Map<String, Object> components) {
        if (!components.containsKey("minecraft:enchantment_glint_override")) {
            return "Off";
        }
        return Boolean.TRUE.equals(components.get("minecraft:enchantment_glint_override")) ? "Shown" : "Hidden";
    }

    private String componentCountSummary(Map<String, Object> components) {
        int count = components != null ? components.size() : 0;
        if (count == 0) {
            return "No extra components";
        }
        return count == 1 ? "1 Component" : count + " Components";
    }

    private String consumableSeconds(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object seconds = map.get("consume_seconds");
            if (seconds instanceof Number number) {
                return formatDashboardNumber(number.doubleValue());
            }
            if (seconds != null && !seconds.toString().isBlank()) {
                return seconds.toString();
            }
        }
        return "";
    }

    private String foodNutrition(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object nutrition = map.get("nutrition");
            if (nutrition instanceof Number number) {
                return formatDashboardNumber(number.doubleValue());
            }
            if (nutrition != null && !nutrition.toString().isBlank()) {
                return nutrition.toString();
            }
        }
        return "";
    }

    private String formatDashboardNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void setConsumableEnabled(boolean enabled) {
        Map<String, Object> components = currentComponents();
        if (enabled) {
            Object consumable = defaultComponentValue("minecraft:consumable");
            if (consumable == null) {
                notifyComponentsLoading();
                return;
            }
            components.putIfAbsent("minecraft:consumable", consumable);
            Object food = defaultComponentValue("minecraft:food");
            if (food != null) {
                components.putIfAbsent("minecraft:food", food);
            }
        } else {
            components.remove("minecraft:food");
            components.remove("minecraft:consumable");
        }
        setComponentsAndRefresh(components);
    }

    private void setGlintEnabled(boolean enabled) {
        Map<String, Object> components = currentComponents();
        if (enabled) {
            components.put("minecraft:enchantment_glint_override", true);
        } else {
            components.remove("minecraft:enchantment_glint_override");
        }
        setComponentsAndRefresh(components);
    }

    private Map<String, Object> currentComponents() {
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        return definition != null && definition.getComponents() != null ? copyAttributeComponents(definition.getComponents()) : new LinkedHashMap<>();
    }

    private void setComponentsAndRefresh(Map<String, Object> components) {
        Map<String, Object> normalized = normalizeAttributeComponents(components);
        setProperty("components", copyAttributeComponents(normalized));
        activeAttributeComponents = copyAttributeComponents(normalized);
        updateSummary();
        refreshContentPanelIfAttributeDesignerClosed();
        if (attributeDesignerOpen) {
            syncAttributeDirtyState();
            refreshAttributeComponentList(false);
        }
    }

    private Object defaultComponentValue(String id) {
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        String source = definition != null ? attributeSchemaSource(definition.getMaterial()) : ATTRIBUTE_SCHEMA_SOURCE;
        OptionCatalogItem item = attributeCatalogItem(source, id);
        if (item != null) {
            Object value = defaultComponentValueFromItem(item);
            if (!isEmptyAttributeObject(value)) {
                return value;
            }
        }
        Object fallback = defaultCommonComponentValue(id, definition);
        if (fallback != null) {
            requestCatalog(source);
            return fallback;
        }
        requestCatalog(source);
        return null;
    }

    private boolean isEmptyAttributeObject(Object value) {
        return value instanceof Map<?, ?> map && map.isEmpty();
    }

    private Object defaultCommonComponentValue(String id, CustomContentDefinition definition) {
        String material = definition != null && definition.getMaterial() != null && !definition.getMaterial().isBlank()
            ? definition.getMaterial().toLowerCase(Locale.ROOT)
            : "stick";
        if ("minecraft:food".equals(id)) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("nutrition", 1);
            value.put("saturation", 0.1);
            return value;
        }
        if ("minecraft:consumable".equals(id)) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("consume_seconds", 1.6);
            value.put("animation", "eat");
            value.put("sound", "minecraft:entity.generic.eat");
            value.put("has_consume_particles", true);
            return value;
        }
        if ("minecraft:enchantment_glint_override".equals(id)) {
            return true;
        }
        if ("minecraft:item_model".equals(id)) {
            return "minecraft:" + material;
        }
        if ("minecraft:item_name".equals(id)) {
            return Map.of("text", definition != null && definition.getDisplayName() != null && !definition.getDisplayName().isBlank() ? definition.getDisplayName() : "Item Name");
        }
        if ("minecraft:custom_name".equals(id)) {
            return Map.of("text", definition != null && definition.getDisplayName() != null && !definition.getDisplayName().isBlank() ? definition.getDisplayName() : "Custom Name");
        }
        if ("minecraft:lore".equals(id)) {
            List<String> lore = definition != null && definition.getLore() != null && !definition.getLore().isEmpty() ? definition.getLore() : List.of("Lore");
            return lore.stream().map(line -> Map.of("text", line)).toList();
        }
        if ("minecraft:tooltip_display".equals(id)) {
            return Map.of("hidden_components", List.of("minecraft:attribute_modifiers"));
        }
        if ("minecraft:attribute_modifiers".equals(id)) {
            return List.of(Map.of(
                "type", "minecraft:generic.attack_damage",
                "amount", 1.0,
                "operation", "add_value",
                "slot", "mainhand",
                "id", "remotely:attack_damage"
            ));
        }
        if ("minecraft:trim".equals(id)) {
            return Map.of(
                "material", "minecraft:iron",
                "pattern", "minecraft:sentry"
            );
        }
        if ("minecraft:firework_explosion".equals(id)) {
            return defaultFireworkExplosion();
        }
        if ("minecraft:fireworks".equals(id)) {
            return Map.of(
                "flight_duration", 1,
                "explosions", List.of(defaultFireworkExplosion())
            );
        }
        if ("minecraft:banner_patterns".equals(id)) {
            return List.of(Map.of("pattern", "minecraft:stripe_bottom", "color", "white"));
        }
        if ("minecraft:charged_projectiles".equals(id)) {
            return List.of(simpleItemStack("minecraft:arrow", 1));
        }
        if ("minecraft:bundle_contents".equals(id)) {
            return List.of(simpleItemStack("minecraft:apple", 1));
        }
        if ("minecraft:container".equals(id)) {
            return List.of(Map.of("slot", 0, "item", simpleItemStack("minecraft:stone", 1)));
        }
        if ("minecraft:use_remainder".equals(id)) {
            return simpleItemStack("minecraft:bowl", 1);
        }
        if ("minecraft:weapon".equals(id)) {
            return Map.of(
                "item_damage_per_attack", 1,
                "disable_blocking_for_seconds", 0.0
            );
        }
        if ("minecraft:equippable".equals(id)) {
            return defaultEquippableValue(material);
        }
        if ("minecraft:can_break".equals(id) || "minecraft:can_place_on".equals(id)) {
            return List.of(Map.of("blocks", "minecraft:stone"));
        }
        if ("minecraft:damage_resistant".equals(id)) {
            return Map.of("types", "#minecraft:is_fire");
        }
        if ("minecraft:glider".equals(id) || "minecraft:intangible_projectile".equals(id) || "minecraft:unbreakable".equals(id)) {
            return Map.of();
        }
        if ("minecraft:enchantments".equals(id) || "minecraft:stored_enchantments".equals(id)) {
            return Map.of(
                "minecraft:unbreaking", 1
            );
        }
        if ("minecraft:potion_contents".equals(id)) {
            return Map.of("potion", "minecraft:water");
        }
        if ("minecraft:max_stack_size".equals(id)) {
            return 64;
        }
        if ("minecraft:rarity".equals(id)) {
            return "common";
        }
        if ("minecraft:enchantable".equals(id)) {
            return Map.of("value", 10);
        }
        if ("minecraft:ominous_bottle_amplifier".equals(id)) {
            return 1;
        }
        if ("minecraft:instrument".equals(id)) {
            return "minecraft:ponder_goat_horn";
        }
        if ("minecraft:jukebox_playable".equals(id)) {
            return "minecraft:13";
        }
        if ("minecraft:dyed_color".equals(id)) {
            return 16777215;
        }
        return null;
    }

    private TitledRowWidget textRow(String label, String value, int width, Consumer<String> onChange) {
        TextInputWidget input = new TextInputWidget.Builder()
            .text(value == null ? "" : value)
            .placeholder(label)
            .forcePlaceholder(false)
            .size(174, 18)
            .onChange(onChange)
            .build();
        ReSyncStudioPanelState.disableEntrance(input);
        TitledRowWidget row = new TitledRowWidget.Builder().title(label).description(contentPanelDescription(label)).size(width, 36).padding(4).addWidget(input).build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private TitledRowWidget dropdownRow(String label, List<String> choices, String selected, int width, Consumer<String> onChange) {
        List<String> options = normalizedOptions(choices, selected);
        DropDownWidget<String> dropdown = new DropDownWidget.Builder<>(options)
            .selectedItem(resolveSelectedOption(options, selected))
            .onSelectionChanged(value -> {
                if (isRealOption(value)) {
                    onChange.accept(value);
                }
            })
            .size(174, 18)
            .maxVisibleItems(10)
            .entranceAnimation(false)
            .build();
        panelDropdowns.add(dropdown);
        TitledRowWidget row = new TitledRowWidget.Builder().title(label).description(contentPanelDescription(label)).size(width, 36).padding(4).addWidget(dropdown).build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private TitledRowWidget searchableRow(String label, List<String> choices, String selected, int width, Consumer<String> onChange) {
        List<String> options = normalizedOptions(choices, selected);
        AnimatedButton button = new AnimatedButton.Builder()
            .label(resolveSelectedOption(options, selected))
            .size(174, 18)
            .entranceAnimation(false)
            .build();
        button.setAction(() -> {
            if (options.size() == 1 && "Loading".equals(options.getFirst())) {
                requestMissingCatalogFor(label);
                return;
            }
            showSearchSelector(options, selected, value -> {
                if (isRealOption(value)) {
                    button.setMessage(value);
                    onChange.accept(value);
                }
            }, button.getX(), button.getY() + button.getHeight());
        });
        TitledRowWidget row = new TitledRowWidget.Builder().title(label).description(contentPanelDescription(label)).size(width, 36).padding(4).addWidget(button).build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private String contentPanelDescription(String label) {
        return switch (label) {
            case "ID" -> "Stable content id.\nUsed by recipes, flows, GUIs, and generated resource data.\nChanging it can break existing references.";
            case "Model" -> "Custom model data integer.\nResource packs use this value to pick an alternate item model.\nLeave empty for the base material model.";
            case "Lore" -> "Item tooltip lines.\nOne line per entry.\nPlace gameplay requirements or stats near the top.";
            case "Tags" -> "Comma-separated content tags.\nUsed for grouping, filtering, and flow-side lookup.";
            case "Permission" -> "Permission node required by this trigger or interaction.\nLeave empty when no permission check is needed.";
            case "Cooldown" -> "Repeat delay in ticks.\n20 ticks = 1 second.\nApplies before this action can run again.";
            case "Chance" -> "Success chance as a percent.\n100 always runs.\n0 never runs.";
            case "Worlds" -> "World allow-list.\nEmpty means every world.\nSelect worlds to restrict where the rule applies.";
            case "Hand" -> "Required interaction hand.\nMain hand and offhand can both fire events, so set this when duplicate triggers matter.";
            case "Target" -> "Required target category.\nLimits the trigger to players, living entities, hostile mobs, passive mobs, or similar target groups.";
            case "Provider" -> "Content asset provider.\nVanilla resolves Minecraft materials.\nOther providers resolve external or pack-backed ids.";
            case "Material" -> "Base Minecraft material id.\nControls the default item icon and fallback appearance.";
            case "External ID" -> "Provider-specific asset id.\nOnly used when the selected provider resolves non-vanilla content.";
            default -> "";
        };
    }

    private MountableButtonWidget eventRow(CustomContentGraphAdapter.TriggerDescriptor trigger, int width) {
        boolean enabled = CustomContentGraphAdapter.getEnabledTriggerBranches(graph).contains(trigger.pin());
        boolean selected = trigger.pin().equals(selectedBranch);
        ToggleWidget toggle = new ToggleWidget.Builder()
            .toggled(enabled)
            .size(32, 18)
            .onChange(value -> setBranchEnabled(trigger.pin(), value))
            .entranceAnimation(false)
            .build();
        MountableButtonWidget row = new MountableButtonWidget.Builder(eventTitle(trigger.pin()))
            .description((enabled ? "Enabled" : "Off") + " | " + actionSummary(trigger.pin()))
            .onClick(() -> {
                selectedBranch = trigger.pin();
                focusContentBranch(selectedBranch);
                updateEventRows();
            })
            .addWidget(toggle)
            .build();
        row.setSize(width, 30);
        row.setSelected(selected);
        eventRows.put(trigger.pin(), row);
        return row;
    }

    private void openAttributeDesigner() {
        openAttributeDesigner("");
    }

    private void openAttributeDesigner(String query) {
        activeAttributeQuery = query != null ? query : "";
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        if (definition == null) {
            return;
        }
        closeActiveSearchSelector();
        String source = attributeSchemaSource(definition.getMaterial());
        requestCatalog(source);
        Map<String, Object> components = definition.getComponents() != null ? definition.getComponents() : Map.of();
        activeAttributeComponents = activateNormalizedAttributeComponents(components);
        attributeDesignerOpen = true;
        if (contentDesignerParent instanceof StudioScreen studioScreen) {
            studioScreen.setStudioContentBrowserTemporarilyHidden(true);
            attributeDesignerHidContentBrowser = true;
        }
        if (attributeDesignerPanel != null) {
            attributeDesignerPanel.show();
        }
        updatePositions();
        refreshAttributeDesigner(false);
    }

    private void refreshAttributeDesigner() {
        refreshAttributeDesigner(true);
    }

    private void refreshAttributeDesignerContent(boolean preserveScroll) {
        if (attributePanelStaticWidgetCount > 0) {
            syncAttributeDirtyState();
            refreshAttributeComponentList(preserveScroll);
            return;
        }
        refreshAttributeDesigner(preserveScroll);
    }

    private void refreshAttributeDesigner(boolean preserveScroll) {
        if (!attributeDesignerOpen || attributePanel == null || attributeDesignerPanel == null) {
            return;
        }
        Container container = attributePanel.container();
        float previousScroll = container.getScrollOffset();
        clearAttributePanelWidgets(container);
        selectedAttributeRowWidget = null;
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        if (definition == null) {
            hideAttributeDesigner();
            return;
        }
        String source = attributeSchemaSource(definition.getMaterial());
        requestCatalog(source);
        if (activeAttributeComponents == null) {
            activeAttributeComponents = activateNormalizedAttributeComponents(definition.getComponents() != null ? definition.getComponents() : Map.of());
        }
        int rowWidth = attributeRowWidth();
        MountableButtonWidget header = new MountableButtonWidget.Builder("Item Attributes")
            .description(attributeDesignerSummary(definition, source))
            .iconPath("item.png")
            .addButton(new SquareButtonWidget.Builder().imagePath("close.png").hint("Close").entranceAnimation(false).onClick(this::hideAttributeDesigner).build())
            .build();
        header.setSize(rowWidth, 30);
        attributeHeaderWidget = header;
        insertAttributePanelWidget(container, header);
        TextInputWidget search = new TextInputWidget.Builder()
            .text(activeAttributeQuery)
            .placeholder("Attribute Name")
            .forcePlaceholder(false)
            .size(Math.max(120, rowWidth - 8), 18)
            .build();
        search.setOnChange(() -> {
            activeAttributeQuery = search.getText();
            refreshAttributeComponentList(true);
        });
        attributeSearchInput = search;
        RowWidget searchRow = new RowWidget.Builder()
            .size(rowWidth - 8, 18)
            .padding(4)
            .addWidget(search)
            .build();
        TitledRowWidget searchPanel = new TitledRowWidget.Builder()
            .title("Search")
            .description("Type Name Or Purpose")
            .size(rowWidth, 36)
            .padding(4)
            .addWidget(searchRow)
            .build();
        insertAttributePanelWidget(container, searchPanel);
        if (attributeRestoreSearchFocus) {
            searchPanel.focusFirstFocusableChild();
        }
        attributePanelStaticWidgetCount = attributePanelWidgets.size();
        addAttributeValidationRows(container, rowWidth);
        populateAttributeComponents(container, source, activeAttributeComponents, activeAttributeQuery);
        container.updateWidgetPositions();
        if (preserveScroll) {
            container.setTargetScrollOffset(previousScroll);
        } else if (selectedAttributeRowWidget != null) {
            container.scrollToWidget(selectedAttributeRowWidget);
        } else {
            container.setScrollOffset(0);
        }
        attributeRestoreSearchFocus = false;
    }

    private void refreshAttributeComponentList(boolean preserveScroll) {
        if (!attributeDesignerOpen || attributePanel == null || attributeDesignerPanel == null) {
            return;
        }
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        if (definition == null) {
            hideAttributeDesigner();
            return;
        }
        Container container = attributePanel.container();
        float previousScroll = container.getScrollOffset();
        while (attributePanelWidgets.size() > attributePanelStaticWidgetCount) {
            attributePanelWidgets.removeLast();
        }
        selectedAttributeRowWidget = null;
        String source = attributeSchemaSource(definition.getMaterial());
        int rowWidth = attributeRowWidth();
        collectingAttributePanelOrder = true;
        try {
            addAttributeValidationRows(container, rowWidth);
            populateAttributeComponents(container, source, activeAttributeComponents, activeAttributeQuery);
        } finally {
            collectingAttributePanelOrder = false;
        }
        container.replaceWidgetsFromIndex(attributePanelStaticWidgetCount, attributePanelWidgets.subList(attributePanelStaticWidgetCount, attributePanelWidgets.size()), false);
        if (preserveScroll) {
            container.setTargetScrollOffset(previousScroll);
        } else if (selectedAttributeRowWidget != null) {
            container.scrollToWidget(selectedAttributeRowWidget);
        }
        if (attributeSearchInput != null && attributeSearchInput.isFocused()) {
            setFocusedWidget(attributeSearchInput);
        }
    }

    private void syncAttributeDirtyState() {
        if (!attributeDesignerOpen) {
            return;
        }
        if (attributeHeaderWidget != null) {
            CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
            if (definition != null) {
                String source = attributeSchemaSource(definition.getMaterial());
                attributeHeaderWidget.setDescription(attributeDesignerSummary(definition, source));
            }
        }
    }

    private void hideAttributeDesigner() {
        closeActiveSearchSelector();
        setFocusedWidget(null);
        if (attributePanel != null) {
            clearAttributePanelWidgets(attributePanel.container());
            attributePanel.hideImmediately();
        } else if (attributeDesignerPanel != null) {
            attributeDesignerPanel.hide();
        }
        restoreAttributeDesignerContentBrowser();
        attributeDesignerOpen = false;
        attributeDesignerHidContentBrowser = false;
        activeAttributeComponents = new LinkedHashMap<>();
        attributePreviewValues.clear();
        selectedAttributeComponent = "";
        attributeHeaderWidget = null;
        attributeSearchInput = null;
        attributePanelStaticWidgetCount = 0;
        attributeComponentRowStates.clear();
        attributeSectionRows.clear();
        attributeStatusRows.clear();
        refreshContentPanel();
        updatePositions();
    }

    private void restoreAttributeDesignerContentBrowser() {
        if (attributeDesignerHidContentBrowser && contentDesignerParent instanceof StudioScreen studioScreen) {
            studioScreen.setStudioContentBrowserTemporarilyHidden(false);
        }
        attributeDesignerHidContentBrowser = false;
    }

    private boolean isAttributeDesignerInteractive() {
        return attributeDesignerOpen && attributePanel != null && attributePanel.isVisible();
    }

    private String attributeDesignerSummary(CustomContentDefinition definition, String source) {
        int count = activeAttributeComponents != null ? activeAttributeComponents.size() : 0;
        boolean loaded = OptionCatalogCache.getInstance().hasCatalog(serverId, source);
        String material = definition.getMaterial() != null && !definition.getMaterial().isBlank() ? definition.getMaterial() : "material";
        return material + " | " + (count == 1 ? "1 Attribute" : count + " Attributes") + " | " + (loaded ? "Synced" : "Syncing");
    }

    private DropDownWidget<String> panelDropdown(List<String> choices, String selected, Consumer<String> onChange) {
        List<String> options = normalizedOptions(choices, selected);
        return new DropDownWidget.Builder<>(options)
            .selectedItem(resolveSelectedOption(options, selected))
            .onSelectionChanged(value -> {
                if (isRealOption(value)) {
                    onChange.accept(value);
                }
            })
            .size(240, 18)
            .maxVisibleItems(10)
            .entranceAnimation(false)
            .build();
    }

    private void addAttributeValidationRows(Container container, int rowWidth) {
        if (attributeValidationErrors.isEmpty()) {
            return;
        }
        int count = 0;
        for (Map<String, Object> error : attributeValidationErrors) {
            if (count >= 4) {
                insertAttributePanelWidget(container, attributeStatusRow("Errors", (attributeValidationErrors.size() - count) + " More", rowWidth, true, "validationMore"));
                return;
            }
            String component = String.valueOf(error.getOrDefault("component", ""));
            String message = String.valueOf(error.getOrDefault("message", "Invalid Component"));
            insertAttributePanelWidget(container, attributeStatusRow(component.isBlank() ? "Error" : component, message, rowWidth, true, "validation" + count));
            count++;
        }
    }

    private MountableButtonWidget attributeStatusRow(String title, String description, int width, boolean danger) {
        return attributeStatusRow(title, description, width, danger, title + "\n" + description);
    }

    private MountableButtonWidget attributeStatusRow(String title, String description, int width, boolean danger, String identity) {
        String key = identity + "\n" + danger;
        MountableButtonWidget row = attributeStatusRows.get(key);
        if (row == null) {
            row = new MountableButtonWidget.Builder(title)
                .description(description)
                .build();
            if (danger) {
                row.setAccent(ThemeManager.getAccent("danger"));
            } else {
                row.setActive(false);
            }
            attributeStatusRows.put(key, row);
        }
        row.setName(title);
        row.setDescription(description);
        row.setSize(width, 28);
        return row;
    }

    private void populateAttributeComponents(Container container, String source, Map<String, Object> components, String query) {
        Map<String, OptionCatalogItem> catalog = attributeCatalog(source);
        int rowWidth = attributeRowWidth();
        ensureAttributeComponentRowStates(catalog, rowWidth);
        List<String> active = activeComponents(components, catalog);
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        String material = definition != null ? definition.getMaterial() : "";
        List<OptionCatalogItem> available = availableComponents(components, catalog, query, material);
        ensureSelectedAttribute(components, active);
        insertAttributePanelWidget(container, attributeSectionHeader("Current Attributes", active.isEmpty() ? "None" : active.size() + " Total", rowWidth));
        if (active.isEmpty()) {
            insertAttributePanelWidget(container, attributeStatusRow("No Attributes", "Choose Component", rowWidth, false));
        }
        for (String id : active) {
            addAttributeComponentBlock(container, components, id, catalog.get(id), rowWidth);
        }
        if (available.isEmpty()) {
            insertAttributePanelWidget(container, attributeStatusRow(componentCatalogStatus(source, query), query != null && !query.isBlank() ? "No Matches" : "Synced", rowWidth, false));
        }
        String currentGroup = "";
        for (OptionCatalogItem item : available) {
            String id = item.getValue();
            String group = attributeBrowseGroup(id, item);
            if (!group.equals(currentGroup)) {
                currentGroup = group;
                insertAttributePanelWidget(container, attributeSectionHeader(group, attributeBrowseGroupDescription(group), rowWidth));
            }
            insertAttributePanelWidget(container, attributeComponentRow(id, components, item, rowWidth));
        }
    }

    private void ensureAttributeComponentRowStates(Map<String, OptionCatalogItem> catalog, int width) {
        for (OptionCatalogItem item : catalog.values()) {
            if (item == null || item.getValue() == null || item.getValue().isBlank()) {
                continue;
            }
            AttributeComponentRowState state = attributeComponentRowStates.computeIfAbsent(item.getValue(), this::createAttributeComponentRowState);
            state.item = item;
            state.row.setName(item.getLabel());
            state.row.setSize(width, 28);
        }
    }

    private void ensureSelectedAttribute(Map<String, Object> components, List<String> active) {
        if (!selectedAttributeComponent.isBlank() && !components.containsKey(selectedAttributeComponent) && !attributeComponentRowStates.containsKey(selectedAttributeComponent)) {
            selectedAttributeComponent = "";
        }
    }

    private void addAttributeComponentBlock(Container container, Map<String, Object> components, String id, OptionCatalogItem item, int rowWidth) {
        if (id == null || id.isBlank()) {
            return;
        }
        MountableButtonWidget row = attributeComponentRow(id, components, item, rowWidth);
        insertAttributePanelWidget(container, row);
    }

    private List<AnimatedWidget> collectAttributeEditorWidgets(Runnable builder) {
        List<AnimatedWidget> previous = collectingAttributeEditorWidgets;
        List<AnimatedWidget> widgets = new ArrayList<>();
        collectingAttributeEditorWidgets = widgets;
        try {
            builder.run();
            return widgets;
        } finally {
            collectingAttributeEditorWidgets = previous;
        }
    }

    private MountableButtonWidget attributeSectionHeader(String title, String description, int width) {
        MountableButtonWidget row = attributeSectionRows.get(title);
        if (row == null) {
            row = new MountableButtonWidget.Builder(title)
                .description(description)
                .build();
            row.setActive(false);
            attributeSectionRows.put(title, row);
        }
        row.setName(title);
        row.setDescription(description);
        row.setSize(width, 24);
        return row;
    }

    private String componentCatalogStatus(String source, String query) {
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
            return "Loading";
        }
        if (query != null && !query.isBlank()) {
            return "No Matches";
        }
        return "No Components";
    }

    private MountableButtonWidget attributeComponentRow(String id, Map<String, Object> components, OptionCatalogItem item, int width) {
        AttributeComponentRowState state = attributeComponentRowStates.computeIfAbsent(id, this::createAttributeComponentRowState);
        state.item = item;
        syncAttributeComponentRow(state, components, width);
        return state.row;
    }

    private AttributeComponentRowState createAttributeComponentRowState(String id) {
        ToggleWidget toggle = new ToggleWidget.Builder()
            .size(32, 18)
            .onChange(enabled -> setAttributeComponentEnabled(id, enabled))
            .entranceAnimation(false)
            .build();
        MountableButtonWidget row = new MountableButtonWidget.Builder(componentLabel(id))
            .onClick(() -> handleAttributeComponentClick(id))
            .addWidget(toggle)
            .build();
        return new AttributeComponentRowState(id, row, toggle);
    }

    private void syncAttributeComponentRow(AttributeComponentRowState state, Map<String, Object> components, int width) {
        String id = state.id;
        boolean active = components.containsKey(id);
        boolean selected = id.equals(selectedAttributeComponent);
        Object rowValue = active ? components.get(id) : attributePreviewValues.get(id);
        state.row.setName(state.item != null ? state.item.getLabel() : componentLabel(id));
        state.row.setDescription(attributeRowDescription(id, state.item, active, selected, rowValue));
        state.toggle.setValue(active);
        state.row.setSize(width, 28);
        state.row.setSelected(selected);
        if (selected) {
            if (!state.row.hasVisibleEmbeddedBody() || state.editorDirty) {
                if (state.editorWidgets == null || state.editorDirty) {
                    Map<String, Object> editorComponents = active ? components : previewAttributeComponents(id, state.item);
                    state.editorWidgets = collectAttributeEditorWidgets(() -> addAttributeEditorRows(attributePanel.container(), editorComponents, id, editorComponents.get(id)));
                    state.editorDirty = false;
                }
                state.row.setEmbeddedBody(state.editorWidgets, true);
            }
            selectedAttributeRowWidget = state.row;
        } else if (state.row.hasVisibleEmbeddedBody()) {
            state.row.setEmbeddedBody(null, false);
        }
        if (!active) {
            state.editorDirty = false;
        }
    }

    private void handleAttributeComponentClick(String id) {
        boolean active = activeAttributeComponents.containsKey(id);
        selectedAttributeComponent = id.equals(selectedAttributeComponent) ? "" : id;
        selectedAttributeRowWidget = null;
        if (!selectedAttributeComponent.isBlank() && !active) {
            AttributeComponentRowState state = attributeComponentRowStates.get(id);
            ensureAttributePreviewValue(id, state != null ? state.item : null);
        }
        syncAttributeRowsInPlace();
        if (attributePanel != null) {
            Container container = attributePanel.container();
            container.updateWidgetPositions();
            if (selectedAttributeRowWidget != null) {
                container.scrollToWidget(selectedAttributeRowWidget);
            }
        }
    }

    private void setAttributeComponentEnabled(String id, boolean enabled) {
        Map<String, Object> current = attributeDraftComponents(activeAttributeComponents);
        boolean active = current.containsKey(id);
        if (enabled == active) {
            return;
        }
        if (enabled) {
            AttributeComponentRowState state = attributeComponentRowStates.get(id);
            Object previewValue = attributePreviewValues.get(id);
            if (previewValue != null) {
                current.putIfAbsent(id, copyAttributeValue(previewValue));
                selectedAttributeComponent = id;
                updateAttributeDesignerDraft(current);
            } else if (addDefaultComponent(current, id, state != null ? state.item : null)) {
                selectedAttributeComponent = id;
                updateAttributeDesignerDraft(current);
            }
            return;
        }
        current.remove(id);
        attributePreviewValues.remove(id);
        if (id.equals(selectedAttributeComponent)) {
            selectedAttributeComponent = "";
        }
        updateAttributeDesignerDraft(current);
    }

    private boolean addDefaultComponent(Map<String, Object> components, String id, OptionCatalogItem item) {
        Object value = item != null ? defaultComponentValueFromItem(item) : defaultComponentValue(id);
        if (isEmptyAttributeObject(value)) {
            Object fallback = defaultComponentValue(id);
            if (fallback != null) {
                value = fallback;
            }
        }
        if (value == null) {
            notifyComponentsLoading();
            return false;
        }
        components.putIfAbsent(id, copyAttributeValue(value));
        return true;
    }

    private Map<String, Object> previewAttributeComponents(String id, OptionCatalogItem item) {
        Object value = ensureAttributePreviewValue(id, item);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put(id, copyAttributeValue(value));
        return preview;
    }

    private Object ensureAttributePreviewValue(String id, OptionCatalogItem item) {
        Object existing = attributePreviewValues.get(id);
        if (existing != null) {
            return existing;
        }
        Object value = item != null ? defaultComponentValueFromItem(item) : defaultComponentValue(id);
        if (isEmptyAttributeObject(value)) {
            Object fallback = defaultComponentValue(id);
            if (fallback != null) {
                value = fallback;
            }
        }
        if (value == null) {
            value = Map.of();
        }
        attributePreviewValues.put(id, copyAttributeValue(value));
        return value;
    }

    private void notifyComponentsLoading() {
        new Notification("Components", "Syncing Schema", Notification.Type.INFO);
    }

    private void updateAttributeDesignerDraft(Map<String, Object> components) {
        updateAttributeDesignerDraft(components, true);
    }

    private void updateAttributeDesignerDraft(Map<String, Object> components, boolean refreshList) {
        Map<String, Object> draft = copyAttributeComponents(components);
        commitAttributeDesignerDraft(draft);
        if (refreshList) {
            refreshAttributeComponentList(false);
        }
    }

    private void commitAttributeDesignerDraft(Map<String, Object> components) {
        activeAttributeComponents = normalizeAttributeComponents(components);
        setProperty("components", copyAttributeComponents(activeAttributeComponents));
        attributeValidationErrors = List.of();
        updateSummary();
        refreshContentPanelIfAttributeDesignerClosed();
        syncAttributeDirtyState();
        syncAttributeRowsInPlace();
    }

    private void refreshContentPanelIfAttributeDesignerClosed() {
        if (!attributeDesignerOpen) {
            refreshContentPanel();
        }
    }

    private void markAttributeEditorDirty(String componentId) {
        AttributeComponentRowState state = attributeComponentRowStates.get(componentId);
        if (state == null) {
            return;
        }
        state.editorDirty = true;
        if (!state.row.hasVisibleEmbeddedBody()) {
            state.editorWidgets = null;
        }
    }

    private void syncAttributeRowsInPlace() {
        if (!attributeDesignerOpen || attributePanel == null || attributeComponentRowStates.isEmpty()) {
            return;
        }
        int rowWidth = attributeRowWidth();
        for (AttributeComponentRowState state : attributeComponentRowStates.values()) {
            syncAttributeComponentRow(state, activeAttributeComponents, rowWidth);
        }
    }

    private String attributeRowDescription(String id, OptionCatalogItem item, boolean active, boolean selected, Object value) {
        List<String> parts = new ArrayList<>();
        if (active) {
            if (!hasIntentionalAttributeEditor(id)) {
                return "Not Editable Here";
            }
            String summary = componentValueSummary(id, value);
            parts.add(summary.isBlank() ? "Set Up" : summary);
        } else if (selected) {
            parts.add("Preview");
        }
        String description = attributeCatalogDescription(item);
        if (!description.isBlank()) {
            parts.add(description);
        }
        if (parts.isEmpty()) {
            parts.add(active ? "Set Up" : "Can Add");
        }
        return joinAttributeDescriptionParts(parts);
    }

    private String attributeCatalogDescription(OptionCatalogItem item) {
        return item != null ? cleanAttributeDescriptionPart(item.getDescription()) : "";
    }

    private String joinAttributeDescriptionParts(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            String cleaned = cleanAttributeDescriptionPart(part);
            if (cleaned.isBlank() || !seen.add(cleaned.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(cleaned);
        }
        return builder.toString();
    }

    private String cleanAttributeDescriptionPart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String componentValueSummary(String id, Object value) {
        if ("minecraft:consumable".equals(id)) {
            String seconds = consumableSeconds(value);
            return seconds.isBlank() ? "Use Settings" : "Use Time " + seconds + "s";
        }
        if ("minecraft:food".equals(id)) {
            String nutrition = foodNutrition(value);
            return nutrition.isBlank() ? "Food Settings" : "Food Restored " + nutrition;
        }
        if ("minecraft:use_cooldown".equals(id)) {
            return useCooldownSummary(value);
        }
        if ("minecraft:use_remainder".equals(id)) {
            String stack = itemStackLine(objectValue(value));
            return stack.isBlank() ? "Remainder Item" : "Leaves " + stack;
        }
        if ("minecraft:enchantment_glint_override".equals(id)) {
            return Boolean.TRUE.equals(value) ? "Glint Shown" : "Glint Hidden";
        }
        if ("minecraft:dyed_color".equals(id)) {
            return "Color " + formatColorValue(dyedColorRgb(value));
        }
        if ("minecraft:enchantments".equals(id) || "minecraft:stored_enchantments".equals(id)) {
            int count = enchantmentLevels(value).size();
            return count == 1 ? "1 Enchantment" : count + " Enchantments";
        }
        if ("minecraft:attribute_modifiers".equals(id)) {
            int count = attributeModifiers(value).size();
            return count == 1 ? "1 Modifier" : count + " Modifiers";
        }
        if ("minecraft:trim".equals(id)) {
            return trimSummary(value);
        }
        if ("minecraft:firework_explosion".equals(id) || "minecraft:fireworks".equals(id)) {
            return fireworkSummary(value);
        }
        if ("minecraft:banner_patterns".equals(id)) {
            int count = bannerPatterns(value).size();
            return count == 1 ? "1 Pattern" : count + " Patterns";
        }
        if ("minecraft:charged_projectiles".equals(id)) {
            int count = arrayValue(value).size();
            return count == 1 ? "1 Projectile" : count + " Projectiles";
        }
        if ("minecraft:bundle_contents".equals(id)) {
            int count = arrayValue(value).size();
            return count == 1 ? "1 Item" : count + " Items";
        }
        if ("minecraft:container".equals(id)) {
            int count = arrayValue(value).size();
            return count == 1 ? "1 Slot" : count + " Slots";
        }
        if ("minecraft:potion_contents".equals(id)) {
            return potionContentsSummary(value);
        }
        if ("minecraft:can_break".equals(id) || "minecraft:can_place_on".equals(id)) {
            int count = blockPredicates(value).size();
            return count == 1 ? "1 Block Rule" : count + " Block Rules";
        }
        if ("minecraft:damage_resistant".equals(id)) {
            return damageResistantSummary(value);
        }
        if ("minecraft:weapon".equals(id)) {
            return weaponSummary(value);
        }
        if ("minecraft:equippable".equals(id)) {
            return equippableSummary(value);
        }
        if ("minecraft:enchantable".equals(id)) {
            return "Enchantability " + enchantableValue(value);
        }
        if ("minecraft:instrument".equals(id)) {
            return "Instrument " + formatComponentValue(value);
        }
        if ("minecraft:jukebox_playable".equals(id)) {
            return jukeboxPlayableSummary(value);
        }
        if ("minecraft:glider".equals(id)) {
            return "Glider";
        }
        if ("minecraft:intangible_projectile".equals(id)) {
            return "Intangible Projectile";
        }
        return "";
    }

    private void addAttributeEditorRows(Container container, Map<String, Object> components, String componentId, Object value) {
        if (addCommonAttributeEditorRows(container, components, componentId, value)) {
            return;
        }
        if (addSchemaAttributeEditorRows(container, components, componentId, value)) {
            return;
        }
        addUnsupportedAttributeEditorRows(container, components, componentId);
    }

    private boolean addSchemaAttributeEditorRows(Container container, Map<String, Object> components, String componentId, Object value) {
        Map<String, Object> schema = attributeComponentSchema(componentId);
        if (schema.isEmpty()) {
            return false;
        }
        addSchemaEditorRows(container, components, componentId, componentId, value, schema, componentLabel(componentId));
        return true;
    }

    private void addSchemaEditorRows(Container container, Map<String, Object> components, String componentId, String path, Object value, Map<String, Object> schema, String title) {
        String kind = schemaKind(schema, value);
        switch (kind) {
            case "boolean" -> {
                ToggleWidget toggle = new ToggleWidget.Builder()
                    .label(title)
                    .toggled(Boolean.TRUE.equals(value))
                    .size(180, 18)
                    .onChange(next -> updateNestedAttributeComponent(components, componentId, path, next))
                    .entranceAnimation(false)
                    .build();
                insertAttributePanelWidget(container, attributeEditorRow(title, toggle));
            }
            case "number" -> insertAttributePanelWidget(container, attributeEditorRow(title, attributeNumberEditor(components, componentId, path, value, title)));
            case "array" -> addSchemaArrayEditorRow(container, components, componentId, path, value, title);
            case "object" -> addSchemaObjectEditorRows(container, components, componentId, path, value, schema, title);
            default -> {
                String source = attributeSchemaCatalogSource(componentId, path);
                if (!source.isBlank()) {
                    boolean namespaced = attributeSchemaCatalogUsesNamespacedValues(source);
                    insertAttributePanelWidget(container, attributeEditorRow(title, attributeSearchTextEditor(components, componentId, path, value, catalogOptions(source), false, source, namespaced)));
                    return;
                }
                TextInputWidget input = new TextInputWidget.Builder()
                    .text(formatComponentValue(value))
                    .placeholder(title)
                    .forcePlaceholder(false)
                    .size(Math.max(140, attributeRowWidth() - 80), 18)
                    .build();
                input.setOnChange(() -> updateNestedAttributeComponent(components, componentId, path, input.getText()));
                insertAttributePanelWidget(container, attributeEditorRow(title, input));
            }
        }
    }

    private String attributeSchemaCatalogSource(String componentId, String path) {
        String key = componentId != null ? componentId : "";
        String leaf = path != null && path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "";
        if ("minecraft:damage_type".equals(key)) {
            return "server:minecraft:damage_type";
        }
        if ("minecraft:dye".equals(key) || "minecraft:base_color".equals(key) || key.endsWith("/collar") || key.endsWith("/color") || key.endsWith("/base_color") || key.endsWith("/pattern_color")) {
            return "server:minecraft:dye_color";
        }
        if ("minecraft:provides_trim_material".equals(key) || "minecraft:trim".equals(key) && "material".equals(leaf)) {
            return "server:minecraft:trim_material";
        }
        if ("minecraft:provides_banner_patterns".equals(key) || "minecraft:banner_patterns".equals(key) && "pattern".equals(leaf)) {
            return "server:minecraft:banner_pattern";
        }
        if ("minecraft:break_sound".equals(key) || "minecraft:note_block_sound".equals(key) || "sound".equals(leaf) || "hit_sound".equals(leaf) || "block_sound".equals(leaf)) {
            return "server:minecraft:sound";
        }
        return switch (key) {
            case "minecraft:axolotl/variant" -> "server:minecraft:axolotl_variant";
            case "minecraft:cat/variant" -> "server:minecraft:cat_variant";
            case "minecraft:cat/sound_variant" -> "server:minecraft:cat_sound_variant";
            case "minecraft:chicken/variant" -> "server:minecraft:chicken_variant";
            case "minecraft:chicken/sound_variant" -> "server:minecraft:chicken_sound_variant";
            case "minecraft:cow/variant" -> "server:minecraft:cow_variant";
            case "minecraft:cow/sound_variant" -> "server:minecraft:cow_sound_variant";
            case "minecraft:fox/variant" -> "server:minecraft:fox_variant";
            case "minecraft:frog/variant" -> "server:minecraft:frog_variant";
            case "minecraft:horse/variant" -> "server:minecraft:horse_variant";
            case "minecraft:llama/variant" -> "server:minecraft:llama_variant";
            case "minecraft:mooshroom/variant" -> "server:minecraft:mooshroom_variant";
            case "minecraft:painting/variant" -> "server:minecraft:painting_variant";
            case "minecraft:parrot/variant" -> "server:minecraft:parrot_variant";
            case "minecraft:pig/variant" -> "server:minecraft:pig_variant";
            case "minecraft:pig/sound_variant" -> "server:minecraft:pig_sound_variant";
            case "minecraft:rabbit/variant" -> "server:minecraft:rabbit_variant";
            case "minecraft:salmon/size" -> "server:minecraft:salmon_size";
            case "minecraft:tropical_fish/pattern" -> "server:minecraft:tropical_fish_pattern";
            case "minecraft:villager/variant" -> "server:minecraft:villager_type";
            case "minecraft:wolf/variant" -> "server:minecraft:wolf_variant";
            case "minecraft:wolf/sound_variant" -> "server:minecraft:wolf_sound_variant";
            case "minecraft:zombie_nautilus/variant" -> "server:minecraft:zombie_nautilus_variant";
            default -> "";
        };
    }

    private boolean attributeSchemaCatalogUsesNamespacedValues(String source) {
        return switch (source) {
            case "server:minecraft:axolotl_variant",
                "server:minecraft:dye_color",
                "server:minecraft:fox_variant",
                "server:minecraft:horse_variant",
                "server:minecraft:llama_variant",
                "server:minecraft:mooshroom_variant",
                "server:minecraft:parrot_variant",
                "server:minecraft:rabbit_variant",
                "server:minecraft:salmon_size",
                "server:minecraft:tropical_fish_pattern" -> false;
            default -> true;
        };
    }

    private void addSchemaObjectEditorRows(Container container, Map<String, Object> components, String componentId, String path, Object value, Map<String, Object> schema, String title) {
        Map<String, Object> fields = schemaFields(schema);
        if (fields.isEmpty()) {
            insertAttributePanelWidget(container, attributeStatusRow(title, "No Editable Fields", attributeRowWidth(), false, "schemaEmpty" + componentId));
            return;
        }
        Map<String, Object> object = objectValue(value);
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String field = entry.getKey();
            Map<String, Object> fieldSchema = schemaMap(entry.getValue());
            String childPath = path + "." + field;
            Object fieldValue = object.containsKey(field) ? object.get(field) : defaultValueForSchema(fieldSchema);
            addSchemaEditorRows(container, components, componentId, childPath, fieldValue, fieldSchema, humanizeAttributeToken(field));
        }
    }

    private void addSchemaArrayEditorRow(Container container, Map<String, Object> components, String componentId, String path, Object value, String title) {
        CodeEditorWidget editor = new CodeEditorWidget(0, 0, Math.max(180, attributeRowWidth() - 12), 78);
        ReSyncStudioPanelState.disableEntrance(editor);
        editor.setShowLineNumbers(false);
        editor.setShowSearchNavigation(false);
        editor.setDimNonMatchingLines(false);
        editor.setShowCursorLineHighlight(false);
        editor.setShowSearchMatchHighlight(false);
        editor.setWordWrap(true);
        editor.setText(schemaArrayText(value));
        editor.onChange = text -> updateNestedAttributeComponent(components, componentId, path, schemaArrayValue(text));
        insertAttributePanelWidget(container, attributeLargeEditorRow(title, editor, 94));
    }

    private boolean addCommonAttributeEditorRows(Container container, Map<String, Object> components, String componentId, Object value) {
        if ("minecraft:consumable".equals(componentId)) {
            addConsumableEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:food".equals(componentId)) {
            addFoodEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:use_cooldown".equals(componentId)) {
            addUseCooldownEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:use_remainder".equals(componentId)) {
            addUseRemainderEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:enchantment_glint_override".equals(componentId)) {
            ToggleWidget toggle = new ToggleWidget.Builder()
                .label("Glint Enabled")
                .toggled(Boolean.TRUE.equals(value))
                .size(180, 18)
                .onChange(next -> updateNestedAttributeComponent(components, componentId, componentId, next))
                .entranceAnimation(false)
                .build();
            insertAttributePanelWidget(container, attributeEditorRow("Glint", toggle));
            return true;
        }
        if ("minecraft:custom_model_data".equals(componentId)) {
            addCustomModelDataEditorRow(container, components, componentId, value);
            return true;
        }
        if ("minecraft:item_model".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Model ID", attributeSearchTextEditor(components, componentId, componentId, value, materialModelOptions(), false, "server:minecraft:material")));
            return true;
        }
        if ("minecraft:item_name".equals(componentId) || "minecraft:custom_name".equals(componentId)) {
            addTextComponentEditorRow(container, components, componentId, value);
            return true;
        }
        if ("minecraft:lore".equals(componentId)) {
            addLoreEditorRow(container, components, componentId, value);
            return true;
        }
        if ("minecraft:tooltip_display".equals(componentId)) {
            addTooltipEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:attribute_modifiers".equals(componentId)) {
            addAttributeModifiersEditorRows(container, components, componentId, value);
            return true;
        }
        if ("minecraft:trim".equals(componentId)) {
            addTrimEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:firework_explosion".equals(componentId)) {
            addFireworkExplosionEditorRows(container, components, componentId, componentId, value);
            return true;
        }
        if ("minecraft:fireworks".equals(componentId)) {
            addFireworksEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:banner_patterns".equals(componentId)) {
            addBannerPatternsEditorRows(container, components, componentId, value);
            return true;
        }
        if ("minecraft:charged_projectiles".equals(componentId)) {
            addItemStackListEditorRows(container, components, componentId, value, "Projectiles", false);
            return true;
        }
        if ("minecraft:bundle_contents".equals(componentId)) {
            addItemStackListEditorRows(container, components, componentId, value, "Items", false);
            return true;
        }
        if ("minecraft:container".equals(componentId)) {
            addItemStackListEditorRows(container, components, componentId, value, "Slots", true);
            return true;
        }
        if ("minecraft:can_break".equals(componentId)) {
            addBlockPredicateEditorRows(container, components, componentId, value, "Break Blocks");
            return true;
        }
        if ("minecraft:can_place_on".equals(componentId)) {
            addBlockPredicateEditorRows(container, components, componentId, value, "Place On");
            return true;
        }
        if ("minecraft:max_stack_size".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Stack Size", attributeNumberEditor(components, componentId, componentId, value, "64")));
            return true;
        }
        if ("minecraft:max_damage".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Max Durability", attributeNumberEditor(components, componentId, componentId, value, "100")));
            return true;
        }
        if ("minecraft:damage".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Damage Used", attributeNumberEditor(components, componentId, componentId, value, "0")));
            return true;
        }
        if ("minecraft:repair_cost".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Repair Cost", attributeNumberEditor(components, componentId, componentId, value, "0")));
            return true;
        }
        if ("minecraft:rarity".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Rarity", attributeChoiceEditor(components, componentId, componentId, List.of("common", "uncommon", "rare", "epic"), String.valueOf(value != null ? value : "common"))));
            return true;
        }
        if ("minecraft:enchantable".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Enchantability", attributeNumberEditor(components, componentId, componentId + ".value", enchantableValue(value), "10")));
            return true;
        }
        if ("minecraft:ominous_bottle_amplifier".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Amplifier", attributeNumberEditor(components, componentId, componentId, value, "1")));
            return true;
        }
        if ("minecraft:instrument".equals(componentId)) {
            insertAttributePanelWidget(container, attributeEditorRow("Instrument", attributeSearchTextEditor(components, componentId, componentId, value, instrumentOptions(), false, "server:minecraft:instrument")));
            return true;
        }
        if ("minecraft:jukebox_playable".equals(componentId)) {
            addJukeboxPlayableEditorRows(container, components, componentId, value);
            return true;
        }
        if ("minecraft:dyed_color".equals(componentId)) {
            addDyedColorEditorRows(container, components, componentId, value);
            return true;
        }
        if ("minecraft:enchantments".equals(componentId) || "minecraft:stored_enchantments".equals(componentId)) {
            addEnchantmentsEditorRows(container, components, componentId, value);
            return true;
        }
        if ("minecraft:potion_contents".equals(componentId)) {
            addPotionContentsEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:damage_resistant".equals(componentId)) {
            addDamageResistantEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:weapon".equals(componentId)) {
            addWeaponEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:equippable".equals(componentId)) {
            addEquippableEditorRows(container, components, componentId, objectValue(value));
            return true;
        }
        if ("minecraft:unbreakable".equals(componentId)) {
            addPresenceEditorRow(container, components, componentId, "Durability", "Unbreakable");
            return true;
        }
        if ("minecraft:glider".equals(componentId)) {
            addPresenceEditorRow(container, components, componentId, "Movement", "Glider");
            return true;
        }
        if ("minecraft:intangible_projectile".equals(componentId)) {
            addPresenceEditorRow(container, components, componentId, "Projectile", "Intangible");
            return true;
        }
        return false;
    }

    private void addUnsupportedAttributeEditorRows(Container container, Map<String, Object> components, String componentId) {
        insertAttributePanelWidget(container, attributeStatusRow("Unsupported Attribute", "Remove It Or Use A Supported Attribute", attributeRowWidth(), true));
        AnimatedButton remove = new AnimatedButton.Builder()
            .label("Remove Attribute")
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .accentType(ThemeManager.getAccent("danger"))
            .entranceAnimation(false)
            .onClick(() -> {
                Map<String, Object> current = attributeDraftComponents(components);
                current.remove(componentId);
                selectedAttributeComponent = "";
                updateAttributeDesignerDraft(current);
            })
            .build();
        insertAttributePanelWidget(container, attributeEditorRow("Action", remove));
    }

    private void addPresenceEditorRow(Container container, Map<String, Object> components, String componentId, String title, String label) {
        ToggleWidget toggle = new ToggleWidget.Builder()
            .label(label)
            .toggled(components.containsKey(componentId))
            .size(180, 18)
            .onChange(next -> {
                updateNestedAttributeComponent(components, componentId, componentId, next ? Map.of() : null);
            })
            .entranceAnimation(false)
            .build();
        insertAttributePanelWidget(container, attributeEditorRow(title, toggle));
    }

    private void addCustomModelDataEditorRow(Container container, Map<String, Object> components, String componentId, Object value) {
        Object modelValue = customModelDataValue(value);
        TextInputWidget input = new TextInputWidget.Builder()
            .text(formatComponentValue(modelValue))
            .placeholder("Number")
            .forcePlaceholder(false)
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .build();
        input.setOnChange(() -> {
            Object parsed = parseComponentEditorValue(modelValue instanceof Number ? modelValue : 0.0, input.getText().replace(',', '.'));
            updateNestedAttributeComponent(components, componentId, componentId, Map.of("floats", List.of(parsed)));
        });
        insertAttributePanelWidget(container, attributeEditorRow("Model Data", input));
    }

    private void addDyedColorEditorRows(Container container, Map<String, Object> components, String componentId, Object value) {
        TextInputWidget color = new TextInputWidget.Builder()
            .text(formatColorValue(dyedColorRgb(value)))
            .placeholder("#RRGGBB")
            .forcePlaceholder(false)
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .build();
        color.setOnChange(() -> updateNestedAttributeComponent(components, componentId, componentId, dyedColorValue(currentAttributeValue(components, componentId), color.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Color", attributeColorPicker(color, false)));
    }

    private Object dyedColorValue(Object previous, String text) {
        return parseColorValue(dyedColorRgb(previous), text);
    }

    private int dyedColorRgb(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object rgb = map.get("rgb");
            if (rgb instanceof Number number) {
                return Math.clamp(number.intValue(), 0, 0xFFFFFF);
            }
            if (rgb != null) {
                return parseColorValue(0xFFFFFF, rgb.toString());
            }
        }
        if (value instanceof Number number) {
            return Math.clamp(number.intValue(), 0, 0xFFFFFF);
        }
        if (value != null) {
            return parseColorValue(0xFFFFFF, value.toString());
        }
        return 0xFFFFFF;
    }

    private String formatColorValue(int value) {
        return String.format(Locale.ROOT, "#%06X", Math.clamp(value, 0, 0xFFFFFF));
    }

    private int parseColorValue(int fallback, String text) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String value = text.trim();
        try {
            if (value.startsWith("#")) {
                return Math.clamp(Integer.parseInt(value.substring(1), 16), 0, 0xFFFFFF);
            }
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Math.clamp(Integer.parseInt(value.substring(2), 16), 0, 0xFFFFFF);
            }
            return Math.clamp(Integer.parseInt(value), 0, 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String dyeColorHex(String value) {
        return switch (value != null ? value.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "") : "") {
            case "black" -> "#1D1D21";
            case "blue" -> "#3C44AA";
            case "brown" -> "#835432";
            case "cyan" -> "#169C9C";
            case "gray" -> "#474F52";
            case "green" -> "#5E7C16";
            case "light_blue" -> "#3AB3DA";
            case "light_gray" -> "#9D9D97";
            case "lime" -> "#80C71F";
            case "magenta" -> "#C74EBD";
            case "orange" -> "#F9801D";
            case "pink" -> "#F38BAA";
            case "purple" -> "#8932B8";
            case "red" -> "#B02E26";
            case "white" -> "#F9FFFE";
            case "yellow" -> "#FED83D";
            default -> "#FFFFFF";
        };
    }

    private void addEnchantmentsEditorRows(Container container, Map<String, Object> components, String componentId, Object value) {
        Map<String, Integer> levels = enchantmentLevels(value);
        insertAttributePanelWidget(container, attributeStatusRow("Enchantments", levels.isEmpty() ? "No Enchantments" : levels.size() + " Configured", attributeRowWidth(), false, componentId + "EnchantmentsHeader"));
        int index = 1;
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            addEnchantmentEntryRow(container, components, componentId, entry.getKey(), entry.getValue(), index);
            index++;
        }
        insertAttributePanelWidget(container, attributeEditorRow("Add Enchantment", attributeAddSearchRow("Enchantment", enchantmentOptions(), "server:minecraft:enchantment", valueId -> {
            Map<String, Integer> next = enchantmentLevels(currentAttributeValue(components, componentId));
            next.put(normalizeMinecraftKey(valueId), 1);
            updateAttributeComponentRoot(components, componentId, new LinkedHashMap<>(next), true);
        })));
    }

    private void addEnchantmentEntryRow(Container container, Map<String, Object> components, String componentId, String key, int level, int index) {
        String[] keyRef = {key};
        TextInputWidget idInput = attributeCompactInput(key, "Enchantment");
        TextInputWidget levelInput = attributeCompactInput(String.valueOf(level), "Level");
        idInput.setOnChange(() -> {
            Map<String, Integer> next = enchantmentLevels(currentAttributeValue(components, componentId));
            String previous = keyRef[0];
            int currentLevel = parsePositiveInt(levelInput.getText(), 1);
            next.remove(previous);
            String normalized = normalizeMinecraftKey(idInput.getText());
            next.put(normalized, currentLevel);
            keyRef[0] = normalized;
            updateAttributeComponentRoot(components, componentId, new LinkedHashMap<>(next), false);
        });
        levelInput.setOnChange(() -> {
            Map<String, Integer> next = enchantmentLevels(currentAttributeValue(components, componentId));
            next.put(keyRef[0], parsePositiveInt(levelInput.getText(), 1));
            updateAttributeComponentRoot(components, componentId, new LinkedHashMap<>(next), false);
        });
        TitledRowWidget row = attributeEntryRow("Enchantment " + index, keyRef[0], idInput, attributeSearchButton(idInput, enchantmentOptions(), "server:minecraft:enchantment", value -> {
            idInput.setText(normalizeMinecraftKey(value));
            idInput.runOnChange();
        }), levelInput, attributeDeleteButton(() -> {
            Map<String, Integer> next = enchantmentLevels(currentAttributeValue(components, componentId));
            next.remove(keyRef[0]);
            updateAttributeComponentRoot(components, componentId, new LinkedHashMap<>(next), true);
        }));
        insertAttributePanelWidget(container, row);
    }

    private String enchantmentText(Object value) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : enchantmentLevels(value).entrySet()) {
            lines.add(entry.getKey() + " " + entry.getValue());
        }
        return String.join("\n", lines);
    }

    private Object enchantmentValue(Object previous, String text) {
        Map<String, Integer> levels = parseEnchantmentLevels(text);
        return new LinkedHashMap<>(levels);
    }

    private Map<String, Integer> enchantmentLevels(Object value) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        Object rawLevels = value;
        if (value instanceof Map<?, ?> map) {
            rawLevels = map.containsKey("levels") ? map.get("levels") : map;
        }
        if (rawLevels instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    levels.put(normalizeMinecraftKey(entry.getKey().toString()), parsePositiveInt(entry.getValue(), 1));
                }
            }
        }
        return levels;
    }

    private Map<String, Integer> parseEnchantmentLevels(String text) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return levels;
        }
        for (String rawLine : text.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            String id = line;
            String amount = "1";
            int equals = line.indexOf('=');
            if (equals >= 0) {
                id = line.substring(0, equals).trim();
                amount = line.substring(equals + 1).trim();
            } else {
                String[] parts = line.split("\\s+");
                if (parts.length > 1) {
                    id = parts[0].trim();
                    amount = parts[1].trim();
                }
            }
            if (!id.isBlank()) {
                levels.put(normalizeMinecraftKey(id), parsePositiveInt(amount, 1));
            }
        }
        return levels;
    }

    private int enchantableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return parsePositiveInt(map.get("value"), 10);
        }
        return parsePositiveInt(value, 10);
    }

    private void addAttributeModifiersEditorRows(Container container, Map<String, Object> components, String componentId, Object value) {
        List<Map<String, Object>> modifiers = attributeModifiers(value);
        insertAttributePanelWidget(container, attributeStatusRow("Modifiers", modifiers.isEmpty() ? "No Modifiers" : modifiers.size() + " Configured", attributeRowWidth(), false, componentId + "ModifiersHeader"));
        for (int i = 0; i < modifiers.size(); i++) {
            addAttributeModifierEntryRow(container, components, componentId, modifiers.get(i), i);
        }
        insertAttributePanelWidget(container, attributeEditorRow("Add Attribute", attributeAddSearchRow("Attribute", attributeTypeOptions(), "server:minecraft:attribute", valueId -> {
            List<Object> next = new ArrayList<>(arrayValue(currentAttributeValue(components, componentId)));
            next.add(parseAttributeModifierLine(normalizeMinecraftKey(valueId) + " 1 add_value mainhand"));
            updateAttributeComponentRoot(components, componentId, next, true);
        })));
    }

    private void addAttributeModifierEntryRow(Container container, Map<String, Object> components, String componentId, Map<String, Object> modifier, int index) {
        String type = String.valueOf(modifier.getOrDefault("type", "minecraft:generic.attack_damage"));
        TextInputWidget amountInput = attributeCompactInput(formatComponentValue(modifier.getOrDefault("amount", 1.0)), "Amount");
        AttributeModifierDropdownRefs dropdowns = new AttributeModifierDropdownRefs();
        dropdowns.operation = attributeOperationDropdown(String.valueOf(modifier.getOrDefault("operation", "add_value")), value -> updateAttributeModifierEntry(components, componentId, index, type, amountInput.getText(), value, selectedDropdownValue(dropdowns.slot)));
        dropdowns.slot = attributeDropdown(List.of("any", "mainhand", "offhand", "head", "chest", "legs", "feet", "body"), String.valueOf(modifier.getOrDefault("slot", "any")), value -> updateAttributeModifierEntry(components, componentId, index, type, amountInput.getText(), selectedDropdownValue(dropdowns.operation), value));
        amountInput.setOnChange(() -> updateAttributeModifierEntry(components, componentId, index, type, amountInput.getText(), selectedDropdownValue(dropdowns.operation), selectedDropdownValue(dropdowns.slot)));
        insertAttributePanelWidget(container, attributeEntryRow(attributeValueLabel("server:minecraft:attribute", type), attributeOperationLabel(selectedDropdownValue(dropdowns.operation)) + " | " + titleCaseAttributeToken(selectedDropdownValue(dropdowns.slot)), amountInput, dropdowns.operation, dropdowns.slot, attributeDeleteButton(() -> removeListAttributeEntry(components, componentId, index))));
    }

    private void updateAttributeModifierEntry(Map<String, Object> components, String componentId, int index, String type, String amount, String operation, String slot) {
        List<Object> next = new ArrayList<>(arrayValue(currentAttributeValue(components, componentId)));
        if (index < 0 || index >= next.size()) {
            return;
        }
        next.set(index, parseAttributeModifierLine(normalizeMinecraftKey(type) + " " + amount + " " + operation + " " + slot));
        updateAttributeComponentRoot(components, componentId, next, false);
    }

    private String attributeModifiersText(Object value) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> modifier : attributeModifiers(value)) {
            String type = String.valueOf(modifier.getOrDefault("type", "minecraft:generic.attack_damage"));
            String amount = formatComponentValue(modifier.getOrDefault("amount", 1.0));
            String operation = String.valueOf(modifier.getOrDefault("operation", "add_value"));
            String slot = String.valueOf(modifier.getOrDefault("slot", "any"));
            lines.add(type + " " + amount + " " + operation + " " + slot);
        }
        return String.join("\n", lines);
    }

    private List<Object> attributeModifiersValue(Object previous, String text) {
        List<Object> modifiers = new ArrayList<>();
        for (String line : splitLines(text)) {
            Map<String, Object> modifier = parseAttributeModifierLine(line);
            if (!modifier.isEmpty()) {
                modifiers.add(modifier);
            }
        }
        return modifiers;
    }

    private Map<String, Object> parseAttributeModifierLine(String line) {
        String[] parts = line != null ? line.trim().split("\\s+") : new String[0];
        if (parts.length == 0 || parts[0].isBlank()) {
            return Map.of();
        }
        String type = normalizeMinecraftKey(parts[0]);
        double amount = parts.length > 1 ? parseDouble(parts[1], 1.0) : 1.0;
        String operation = parts.length > 2 && !parts[2].isBlank() ? parts[2].toLowerCase(Locale.ROOT) : "add_value";
        String slot = parts.length > 3 && !parts[3].isBlank() ? parts[3].toLowerCase(Locale.ROOT) : "any";
        String id = parts.length > 4 && !parts[4].isBlank() ? normalizeMinecraftKey(parts[4]) : modifierId(type, operation, slot);
        Map<String, Object> modifier = new LinkedHashMap<>();
        modifier.put("type", type);
        modifier.put("amount", amount);
        modifier.put("operation", operation);
        if (!"any".equals(slot)) {
            modifier.put("slot", slot);
        }
        modifier.put("id", id);
        return modifier;
    }

    private List<Map<String, Object>> attributeModifiers(Object value) {
        Object raw = value;
        if (value instanceof Map<?, ?> map) {
            raw = map.get("modifiers");
        }
        List<Map<String, Object>> modifiers = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    modifiers.add(objectValue(map));
                }
            }
        }
        return modifiers;
    }

    private String modifierId(String type, String operation, String slot) {
        String cleaned = (type + "_" + operation + "_" + slot)
            .replace("minecraft:", "")
            .replace('.', '_')
            .replace('-', '_')
            .toLowerCase(Locale.ROOT);
        return "remotely:" + cleaned;
    }

    private void addTrimEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        insertAttributePanelWidget(container, attributeEditorRow("Material", attributeSearchTextEditor(components, componentId, componentId + ".material", value.getOrDefault("material", "minecraft:iron"), trimMaterialOptions(), false, "server:minecraft:trim_material")));
        insertAttributePanelWidget(container, attributeEditorRow("Pattern", attributeSearchTextEditor(components, componentId, componentId + ".pattern", value.getOrDefault("pattern", "minecraft:sentry"), trimPatternOptions(), false, "server:minecraft:trim_pattern")));
    }

    private String trimSummary(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return "Trim";
        }
        Object material = map.get("material");
        Object pattern = map.get("pattern");
        if (material != null && pattern != null) {
            return "Material " + material + " | Pattern " + pattern;
        }
        if (material != null) {
            return "Material " + material;
        }
        return pattern != null ? "Pattern " + pattern : "Trim";
    }

    private void addFireworksEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        insertAttributePanelWidget(container, attributeEditorRow("Flight", attributeNumberEditor(components, componentId, componentId + ".flight_duration", value.getOrDefault("flight_duration", 1), "Ticks")));
        Object firstExplosion = firstFireworkExplosion(value.get("explosions"));
        addFireworkExplosionEditorRows(container, components, componentId, componentId + ".explosions.0", firstExplosion);
    }

    private void addFireworkExplosionEditorRows(Container container, Map<String, Object> components, String componentId, String path, Object value) {
        Map<String, Object> explosion = fireworkExplosionValue(value);
        insertAttributePanelWidget(container, attributeEditorRow("Shape", attributeDropdown(List.of("small_ball", "large_ball", "star", "creeper", "burst"), String.valueOf(explosion.getOrDefault("shape", "small_ball")), next -> updateFireworkExplosionField(components, componentId, path, "shape", next))));
        TextInputWidget colors = new TextInputWidget.Builder()
            .text(colorListText(explosion.get("colors")))
            .placeholder("#RRGGBB, #RRGGBB")
            .forcePlaceholder(false)
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .build();
        colors.setOnChange(() -> updateFireworkExplosionField(components, componentId, path, "colors", parseColorList(colors.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Colors", attributeColorPicker(colors, true)));
        TextInputWidget fadeColors = new TextInputWidget.Builder()
            .text(colorListText(explosion.get("fade_colors")))
            .placeholder("#RRGGBB, #RRGGBB")
            .forcePlaceholder(false)
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .build();
        fadeColors.setOnChange(() -> updateFireworkExplosionField(components, componentId, path, "fade_colors", parseColorList(fadeColors.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Fade Colors", attributeColorPicker(fadeColors, true)));
        ToggleWidget trail = new ToggleWidget.Builder()
            .label("Trail")
            .toggled(Boolean.TRUE.equals(explosion.get("has_trail")))
            .size(180, 18)
            .onChange(next -> updateFireworkExplosionField(components, componentId, path, "has_trail", next))
            .entranceAnimation(false)
            .build();
        insertAttributePanelWidget(container, attributeEditorRow("Trail", trail));
        ToggleWidget twinkle = new ToggleWidget.Builder()
            .label("Twinkle")
            .toggled(Boolean.TRUE.equals(explosion.get("has_twinkle")))
            .size(180, 18)
            .onChange(next -> updateFireworkExplosionField(components, componentId, path, "has_twinkle", next))
            .entranceAnimation(false)
            .build();
        insertAttributePanelWidget(container, attributeEditorRow("Twinkle", twinkle));
    }

    private void updateFireworkExplosionField(Map<String, Object> components, String componentId, String path, String field, Object value) {
        if (componentId.equals(path)) {
            updateNestedAttributeComponent(components, componentId, componentId + "." + field, value);
            return;
        }
        Map<String, Object> fireworks = objectValue(currentAttributeValue(components, componentId));
        List<Object> explosions = arrayValue(fireworks.get("explosions"));
        Map<String, Object> explosion = fireworkExplosionValue(explosions.isEmpty() ? defaultFireworkExplosion() : explosions.getFirst());
        explosion.put(field, value);
        if (explosions.isEmpty()) {
            explosions.add(explosion);
        } else {
            explosions.set(0, explosion);
        }
        fireworks.put("explosions", explosions);
        fireworks.putIfAbsent("flight_duration", 1);
        updateNestedAttributeComponent(components, componentId, componentId, fireworks);
    }

    private Map<String, Object> defaultFireworkExplosion() {
        return Map.of(
            "shape", "small_ball",
            "colors", List.of(0xFF0000),
            "fade_colors", List.of(0xFFFF00),
            "has_trail", false,
            "has_twinkle", false
        );
    }

    private Object firstFireworkExplosion(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.getFirst();
        }
        return defaultFireworkExplosion();
    }

    private Map<String, Object> fireworkExplosionValue(Object value) {
        Map<String, Object> explosion = objectValue(value);
        if (explosion.isEmpty()) {
            explosion.putAll(defaultFireworkExplosion());
        }
        return explosion;
    }

    private String fireworkSummary(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object shape = map.get("shape");
            if (shape != null) {
                return shape.toString();
            }
            Object duration = map.get("flight_duration");
            Object explosions = map.get("explosions");
            int count = explosions instanceof List<?> list ? list.size() : 0;
            return "Flight " + (duration != null ? duration : 1) + " | " + (count == 1 ? "1 Explosion" : count + " Explosions");
        }
        return "Firework";
    }

    private String colorListText(Object value) {
        List<String> colors = new ArrayList<>();
        for (Object item : arrayValue(value)) {
            if (item instanceof Number number) {
                colors.add(formatColorValue(number.intValue()));
            } else if (item != null && !item.toString().isBlank()) {
                colors.add(formatColorValue(parseColorValue(0xFFFFFF, item.toString())));
            }
        }
        return String.join(", ", colors);
    }

    private List<Object> parseColorList(String text) {
        List<Object> colors = new ArrayList<>();
        for (String value : splitCsv(text)) {
            colors.add(parseColorValue(0xFFFFFF, value));
        }
        return colors;
    }

    private void addBannerPatternsEditorRows(Container container, Map<String, Object> components, String componentId, Object value) {
        CodeEditorWidget editor = new CodeEditorWidget(0, 0, Math.max(180, attributeRowWidth() - 12), 78);
        ReSyncStudioPanelState.disableEntrance(editor);
        editor.setShowLineNumbers(false);
        editor.setShowSearchNavigation(false);
        editor.setDimNonMatchingLines(false);
        editor.setShowCursorLineHighlight(false);
        editor.setShowSearchMatchHighlight(false);
        editor.setWordWrap(true);
        editor.setText(bannerPatternsText(value));
        editor.onChange = text -> updateNestedAttributeComponent(components, componentId, componentId, bannerPatternsValue(text));
        insertAttributePanelWidget(container, attributeLargeEditorRow("Patterns", editor, 94));
        insertAttributePanelWidget(container, attributeEditorRow("Add Pattern", attributeLinePicker(editor, bannerPatternOptions(), "Pattern", "server:minecraft:banner_pattern", valueId -> normalizeMinecraftKey(valueId) + " white")));
    }

    private String bannerPatternsText(Object value) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> pattern : bannerPatterns(value)) {
            String id = String.valueOf(pattern.getOrDefault("pattern", "minecraft:stripe_bottom"));
            String color = String.valueOf(pattern.getOrDefault("color", "white"));
            lines.add(id + " " + color);
        }
        return String.join("\n", lines);
    }

    private List<Object> bannerPatternsValue(String text) {
        List<Object> patterns = new ArrayList<>();
        for (String line : splitLines(text)) {
            Map<String, Object> pattern = parseBannerPatternLine(line);
            if (!pattern.isEmpty()) {
                patterns.add(pattern);
            }
        }
        return patterns;
    }

    private Map<String, Object> parseBannerPatternLine(String line) {
        String[] parts = line != null ? line.trim().split("\\s+") : new String[0];
        if (parts.length == 0 || parts[0].isBlank()) {
            return Map.of();
        }
        Map<String, Object> pattern = new LinkedHashMap<>();
        pattern.put("pattern", normalizeMinecraftKey(parts[0]));
        pattern.put("color", parts.length > 1 && !parts[1].isBlank() ? parts[1].toLowerCase(Locale.ROOT) : "white");
        return pattern;
    }

    private List<Map<String, Object>> bannerPatterns(Object value) {
        List<Map<String, Object>> patterns = new ArrayList<>();
        for (Object item : arrayValue(value)) {
            if (item instanceof Map<?, ?> map) {
                patterns.add(objectValue(map));
            }
        }
        return patterns;
    }

    private void addItemStackListEditorRows(Container container, Map<String, Object> components, String componentId, Object value, String title, boolean slotted) {
        List<Object> items = arrayValue(value);
        insertAttributePanelWidget(container, attributeStatusRow(title, items.isEmpty() ? "No Items" : items.size() + " Configured", attributeRowWidth(), false, componentId + "ItemsHeader"));
        for (int i = 0; i < items.size(); i++) {
            addItemStackEntryRow(container, components, componentId, items.get(i), i, slotted);
        }
        insertAttributePanelWidget(container, attributeEditorRow("Add Item", attributeAddSearchRow("Item", materialOptions(), "server:minecraft:material", valueId -> {
            List<Object> next = new ArrayList<>(arrayValue(currentAttributeValue(components, componentId)));
            next.add(slotted ? parseSlottedItemStackLine(next.size() + " " + itemStackPickerLine(valueId, false), next.size()) : parseItemStackLine(itemStackPickerLine(valueId, false)));
            updateAttributeComponentRoot(components, componentId, next, true);
        })));
    }

    private void addItemStackEntryRow(Container container, Map<String, Object> components, String componentId, Object value, int index, boolean slotted) {
        Map<String, Object> entry = objectValue(value);
        int slot = index;
        Map<String, Object> stack = entry;
        if (slotted && entry.containsKey("item")) {
            slot = parseNonNegativeInt(entry.get("slot"), index);
            stack = objectValue(entry.get("item"));
        }
        TextInputWidget itemInput = attributeCompactInput(String.valueOf(stack.getOrDefault("id", "minecraft:stone")), "Item");
        TextInputWidget countInput = attributeCompactInput(String.valueOf(parsePositiveInt(stack.get("count"), 1)), "Count");
        TextInputWidget slotInput = attributeCompactInput(String.valueOf(slot), "Slot");
        itemInput.setOnChange(() -> updateItemStackEntry(components, componentId, index, slotted, slotInput.getText(), itemInput.getText(), countInput.getText()));
        countInput.setOnChange(() -> updateItemStackEntry(components, componentId, index, slotted, slotInput.getText(), itemInput.getText(), countInput.getText()));
        slotInput.setOnChange(() -> updateItemStackEntry(components, componentId, index, slotted, slotInput.getText(), itemInput.getText(), countInput.getText()));
        if (slotted) {
            insertAttributePanelWidget(container, attributeEntryRow("Item " + (index + 1), "Slot " + slot, slotInput, itemInput, attributeSearchButton(itemInput, materialOptions(), "server:minecraft:material", valueId -> {
                itemInput.setText(normalizeMinecraftKey(valueId));
                itemInput.runOnChange();
            }), countInput, attributeDeleteButton(() -> removeListAttributeEntry(components, componentId, index))));
            return;
        }
        insertAttributePanelWidget(container, attributeEntryRow("Item " + (index + 1), itemInput.getText(), itemInput, attributeSearchButton(itemInput, materialOptions(), "server:minecraft:material", valueId -> {
            itemInput.setText(normalizeMinecraftKey(valueId));
            itemInput.runOnChange();
        }), countInput, attributeDeleteButton(() -> removeListAttributeEntry(components, componentId, index))));
    }

    private void updateItemStackEntry(Map<String, Object> components, String componentId, int index, boolean slotted, String slot, String id, String count) {
        List<Object> next = new ArrayList<>(arrayValue(currentAttributeValue(components, componentId)));
        if (index < 0 || index >= next.size()) {
            return;
        }
        String line = (slotted ? parseNonNegativeInt(slot, index) + " " : "") + normalizeMinecraftKey(id) + " " + parsePositiveInt(count, 1);
        next.set(index, slotted ? parseSlottedItemStackLine(line, index) : parseItemStackLine(line));
        updateAttributeComponentRoot(components, componentId, next, false);
    }

    private String itemStackListText(Object value, boolean slotted) {
        List<String> lines = new ArrayList<>();
        for (Object item : arrayValue(value)) {
            Map<String, Object> entry = objectValue(item);
            if (slotted && entry.containsKey("item")) {
                Map<String, Object> stack = objectValue(entry.get("item"));
                lines.add(parseNonNegativeInt(entry.get("slot"), 0) + " " + itemStackLine(stack));
            } else {
                lines.add(itemStackLine(entry));
            }
        }
        return String.join("\n", lines);
    }

    private String itemStackLine(Map<String, Object> stack) {
        String id = String.valueOf(stack.getOrDefault("id", "minecraft:stone"));
        int count = parsePositiveInt(stack.get("count"), 1);
        return id + " " + count;
    }

    private String itemStackPickerLine(String value, boolean slotted) {
        String id = value != null ? value.trim().toLowerCase(Locale.ROOT) : "minecraft:stone";
        if (id.isBlank()) {
            id = "minecraft:stone";
        }
        return (slotted ? id : normalizeMinecraftKey(id)) + " 1";
    }

    private List<Object> itemStackListValue(String text, boolean slotted) {
        List<Object> items = new ArrayList<>();
        int fallbackSlot = 0;
        for (String line : splitLines(text)) {
            Object item = slotted ? parseSlottedItemStackLine(line, fallbackSlot) : parseItemStackLine(line);
            if (item != null) {
                items.add(item);
                fallbackSlot++;
            }
        }
        return items;
    }

    private Map<String, Object> parseItemStackLine(String line) {
        String[] parts = line != null ? line.trim().split("\\s+") : new String[0];
        if (parts.length == 0 || parts[0].isBlank()) {
            return null;
        }
        String id = normalizeMinecraftKey(parts[0]);
        int count = parts.length > 1 ? parsePositiveInt(parts[1], 1) : 1;
        return simpleItemStack(id, count);
    }

    private Map<String, Object> parseSlottedItemStackLine(String line, int fallbackSlot) {
        String[] parts = line != null ? line.trim().split("\\s+") : new String[0];
        if (parts.length == 0 || parts[0].isBlank()) {
            return null;
        }
        boolean explicitSlot = isInteger(parts[0]);
        int slot = explicitSlot ? parseNonNegativeInt(parts[0], fallbackSlot) : fallbackSlot;
        int idIndex = explicitSlot ? 1 : 0;
        String id = parts.length > idIndex ? parts[idIndex] : "minecraft:stone";
        int countIndex = idIndex + 1;
        int count = parts.length > countIndex ? parsePositiveInt(parts[countIndex], 1) : 1;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("slot", slot);
        entry.put("item", simpleItemStack(normalizeMinecraftKey(id), count));
        return entry;
    }

    private Map<String, Object> simpleItemStack(String id, int count) {
        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("id", normalizeMinecraftKey(id));
        stack.put("count", Math.clamp(count, 1, 99));
        return stack;
    }

    private String normalizeMinecraftKey(String value) {
        String key = value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
        return key.contains(":") ? key : "minecraft:" + key;
    }

    private int parsePositiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value != null) {
            try {
                return Math.max(1, Integer.parseInt(value.toString().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private int parseNonNegativeInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value != null) {
            try {
                return Math.max(0, Integer.parseInt(value.toString().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private boolean isInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private void addPotionContentsEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        insertAttributePanelWidget(container, attributeEditorRow("Potion", attributeSearchTextEditor(components, componentId, componentId + ".potion", value.getOrDefault("potion", "minecraft:water"), potionOptions(), false, "server:minecraft:potion")));
        TextInputWidget color = new TextInputWidget.Builder()
            .text(formatColorValue(potionCustomColor(value)))
            .placeholder("#RRGGBB")
            .forcePlaceholder(false)
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .build();
        color.setOnChange(() -> updateNestedAttributeComponent(components, componentId, componentId + ".custom_color", parseColorValue(potionCustomColor(value), color.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Color", attributeColorPicker(color, false)));
    }

    private void addJukeboxPlayableEditorRows(Container container, Map<String, Object> components, String componentId, Object value) {
        Map<String, Object> object = objectValue(value);
        Object song = object.containsKey("song") ? object.get("song") : value;
        TextInputWidget songInput = new TextInputWidget.Builder()
            .text(formatComponentValue(song != null ? song : "minecraft:13"))
            .placeholder("Song")
            .forcePlaceholder(false)
            .size(Math.max(100, attributeRowWidth() - 112), 18)
            .build();
        songInput.setOnChange(() -> updateNestedAttributeComponent(components, componentId, componentId, jukeboxPlayableValue(currentAttributeValue(components, componentId), songInput.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Song", searchableInputRow(songInput, jukeboxSongOptions(), false, "server:minecraft:jukebox_song")));
    }

    private String jukeboxPlayableValue(Object previous, String song) {
        return normalizeMinecraftKey(song != null && !song.isBlank() ? song : "13");
    }

    private String potionContentsSummary(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return "Potion";
        }
        Object potion = map.get("potion");
        if (potion != null && !potion.toString().isBlank()) {
            return potion.toString();
        }
        if (map.containsKey("custom_color")) {
            return "Color " + formatColorValue(potionCustomColor(map));
        }
        return "Potion";
    }

    private String jukeboxPlayableSummary(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object song = map.get("song");
            return song != null && !song.toString().isBlank() ? "Song " + song : "Song";
        }
        return value != null && !value.toString().isBlank() ? "Song " + value : "Song";
    }

    private int potionCustomColor(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object color = map.get("custom_color");
            if (color instanceof Number number) {
                return Math.clamp(number.intValue(), 0, 0xFFFFFF);
            }
            if (color != null) {
                return parseColorValue(0xFFFFFF, color.toString());
            }
        }
        return 0xFFFFFF;
    }

    private List<String> potionOptions() {
        return catalogOptions("server:minecraft:potion");
    }

    private List<String> enchantmentOptions() {
        return catalogOptions("server:minecraft:enchantment");
    }

    private List<String> attributeTypeOptions() {
        return catalogOptions("server:minecraft:attribute");
    }

    private List<String> bannerPatternOptions() {
        return catalogOptions("server:minecraft:banner_pattern");
    }

    private List<String> dyeColorOptions() {
        List<String> colors = catalogOptions("server:minecraft:dye_color");
        if (colors.equals(List.of("Loading"))) {
            return colors;
        }
        return normalizedOptions(colors, "white");
    }

    private List<String> blockOptions() {
        return catalogOptions("server:minecraft:block");
    }

    private List<String> instrumentOptions() {
        return catalogOptions("server:minecraft:instrument");
    }

    private List<String> equipmentSlotOptions() {
        return List.of("head", "chest", "legs", "feet", "body", "mainhand", "offhand", "saddle");
    }

    private List<String> equipmentAssetOptions() {
        return List.of("minecraft:leather", "minecraft:chainmail", "minecraft:iron", "minecraft:gold", "minecraft:diamond", "minecraft:turtle_scute", "minecraft:netherite", "minecraft:saddle");
    }

    private List<String> soundOptions() {
        return catalogOptions("server:minecraft:sound");
    }

    private List<String> damageTypeOptions() {
        List<String> damageTypes = catalogOptions("server:minecraft:damage_type");
        if (damageTypes.equals(List.of("Loading"))) {
            return damageTypes;
        }
        List<String> options = new ArrayList<>(damageTypes);
        options.addFirst("#minecraft:is_fire");
        return normalizedOptions(options, "#minecraft:is_fire");
    }

    private List<String> jukeboxSongOptions() {
        return catalogOptions("server:minecraft:jukebox_song");
    }

    private List<String> trimMaterialOptions() {
        return catalogOptions("server:minecraft:trim_material");
    }

    private List<String> trimPatternOptions() {
        return catalogOptions("server:minecraft:trim_pattern");
    }

    private Object customModelDataValue(Object value) {
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Object floats = map.get("floats");
            if (floats instanceof List<?> list && !list.isEmpty()) {
                return list.getFirst();
            }
        }
        return 1.0;
    }

    private void addTextComponentEditorRow(Container container, Map<String, Object> components, String componentId, Object value) {
        TextInputWidget input = new TextInputWidget.Builder()
            .text(plainTextComponentValue(value))
            .placeholder("Text")
            .forcePlaceholder(false)
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .build();
        input.setOnChange(() -> updateNestedAttributeComponent(components, componentId, componentId, Map.of("text", input.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Text", input));
    }

    private String plainTextComponentValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object text = map.get("text");
            return text != null ? text.toString() : "";
        }
        return value != null ? value.toString() : "";
    }

    private void addLoreEditorRow(Container container, Map<String, Object> components, String componentId, Object value) {
        CodeEditorWidget editor = new CodeEditorWidget(0, 0, Math.max(180, attributeRowWidth() - 12), 78);
        ReSyncStudioPanelState.disableEntrance(editor);
        editor.setShowLineNumbers(false);
        editor.setShowSearchNavigation(false);
        editor.setDimNonMatchingLines(false);
        editor.setShowCursorLineHighlight(false);
        editor.setShowSearchMatchHighlight(false);
        editor.setWordWrap(true);
        editor.setText(loreText(value));
        editor.onChange = text -> updateNestedAttributeComponent(components, componentId, componentId, loreValue(text));
        insertAttributePanelWidget(container, attributeLargeEditorRow("Lore", editor, 94));
    }

    private String loreText(Object value) {
        List<String> lines = new ArrayList<>();
        for (Object item : arrayValue(value)) {
            lines.add(plainTextComponentValue(item));
        }
        return String.join("\n", lines);
    }

    private List<Object> loreValue(String text) {
        List<Object> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        for (String line : text.split("\\R", -1)) {
            if (!line.isBlank()) {
                lines.add(Map.of("text", line));
            }
        }
        return lines;
    }

    private void addTooltipEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        TextInputWidget hidden = new TextInputWidget.Builder()
            .text(String.join(", ", tooltipHiddenComponents(value)))
            .placeholder("Hidden Components")
            .forcePlaceholder(false)
            .size(Math.max(100, attributeRowWidth() - 112), 18)
            .build();
        hidden.setOnChange(() -> updateNestedAttributeComponent(components, componentId, componentId, Map.of("hidden_components", splitCsv(hidden.getText()))));
        insertAttributePanelWidget(container, attributeEditorRow("Hide In Tooltip", searchableInputRow(hidden, hiddenTooltipComponentOptions(), true, "")));
    }

    private void addBlockPredicateEditorRows(Container container, Map<String, Object> components, String componentId, Object value, String title) {
        List<Map<String, Object>> predicates = blockPredicates(value);
        insertAttributePanelWidget(container, attributeStatusRow(title, predicates.isEmpty() ? "No Blocks" : predicates.size() + " Rules", attributeRowWidth(), false, componentId + "BlocksHeader"));
        for (int i = 0; i < predicates.size(); i++) {
            addBlockPredicateEntryRow(container, components, componentId, predicates.get(i), i);
        }
        insertAttributePanelWidget(container, attributeEditorRow("Add Block", attributeAddSearchRow("Block Or Tag", blockOptions(), "server:minecraft:block", valueId -> {
            List<Object> next = new ArrayList<>(arrayValue(currentAttributeValue(components, componentId)));
            next.add(Map.of("blocks", normalizeBlockPredicate(valueId)));
            updateAttributeComponentRoot(components, componentId, next, true);
        })));
    }

    private void addBlockPredicateEntryRow(Container container, Map<String, Object> components, String componentId, Map<String, Object> predicate, int index) {
        String block = firstBlockPredicateValue(predicate);
        TextInputWidget input = attributeCompactInput(block, "Block Or Tag");
        input.setOnChange(() -> updateBlockPredicateEntry(components, componentId, index, input.getText()));
        insertAttributePanelWidget(container, attributeEntryRow("Block Rule " + (index + 1), block, input, attributeSearchButton(input, blockOptions(), "server:minecraft:block", value -> {
            input.setText(normalizeBlockPredicate(value));
            input.runOnChange();
        }), attributeDeleteButton(() -> removeListAttributeEntry(components, componentId, index))));
    }

    private void updateBlockPredicateEntry(Map<String, Object> components, String componentId, int index, String block) {
        List<Object> next = new ArrayList<>(arrayValue(currentAttributeValue(components, componentId)));
        if (index < 0 || index >= next.size()) {
            return;
        }
        next.set(index, Map.of("blocks", normalizeBlockPredicate(block)));
        updateAttributeComponentRoot(components, componentId, next, false);
    }

    private String firstBlockPredicateValue(Map<String, Object> predicate) {
        Object blocks = predicate.get("blocks");
        if (blocks instanceof List<?> list && !list.isEmpty() && list.getFirst() != null) {
            return list.getFirst().toString();
        }
        return blocks != null && !blocks.toString().isBlank() ? blocks.toString() : "minecraft:stone";
    }

    private String blockPredicateText(Object value) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> predicate : blockPredicates(value)) {
            Object blocks = predicate.get("blocks");
            if (blocks instanceof List<?> list) {
                for (Object block : list) {
                    if (block != null && !block.toString().isBlank()) {
                        lines.add(block.toString());
                    }
                }
            } else if (blocks != null && !blocks.toString().isBlank()) {
                lines.add(blocks.toString());
            }
        }
        return String.join("\n", lines);
    }

    private List<Object> blockPredicateValue(Object previous, String text) {
        List<Object> predicates = new ArrayList<>();
        for (String block : splitLines(text)) {
            predicates.add(Map.of("blocks", normalizeBlockPredicate(block)));
        }
        return predicates;
    }

    private List<Map<String, Object>> blockPredicates(Object value) {
        Object raw = value;
        if (value instanceof Map<?, ?> map) {
            raw = map.get("predicates");
        }
        List<Map<String, Object>> predicates = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    predicates.add(objectValue(map));
                }
            }
        }
        return predicates;
    }

    private String normalizeBlockPredicate(String value) {
        String trimmed = value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
        if (trimmed.startsWith("#")) {
            String tag = trimmed.substring(1);
            return "#" + (tag.contains(":") ? tag : "minecraft:" + tag);
        }
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private List<String> tooltipHiddenComponents(Map<String, Object> value) {
        Object hidden = value.get("hidden_components");
        if (!(hidden instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString());
            }
        }
        return result;
    }

    private void addConsumableEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        insertAttributePanelWidget(container, attributeEditorRow("Use Time", attributeNumberEditor(components, componentId, componentId + ".consume_seconds", value.getOrDefault("consume_seconds", 1.6), "Seconds")));
        insertAttributePanelWidget(container, attributeEditorRow("Animation", attributeChoiceEditor(components, componentId, componentId + ".animation", List.of("eat", "drink", "block", "bow", "crossbow", "spear", "spyglass", "toot_horn", "brush"), String.valueOf(value.getOrDefault("animation", "eat")))));
        insertAttributePanelWidget(container, attributeEditorRow("Sound", attributeSearchTextEditor(components, componentId, componentId + ".sound", value.getOrDefault("sound", "minecraft:entity.generic.eat"), soundOptions(), false, "server:minecraft:sound")));
        ToggleWidget particles = new ToggleWidget.Builder()
            .label("Show Particles")
            .toggled(!Boolean.FALSE.equals(value.get("has_consume_particles")))
            .size(180, 18)
            .onChange(next -> updateNestedAttributeComponent(components, componentId, componentId + ".has_consume_particles", next))
            .entranceAnimation(false)
            .build();
        insertAttributePanelWidget(container, attributeEditorRow("Particles", particles));
    }

    private void addFoodEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        insertAttributePanelWidget(container, attributeEditorRow("Food Restored", attributeNumberEditor(components, componentId, componentId + ".nutrition", value.getOrDefault("nutrition", 1), "Points")));
        Object saturation = value.containsKey("saturation") ? value.get("saturation") : value.getOrDefault("saturation_modifier", 0.1);
        String saturationPath = value.containsKey("saturation_modifier") && !value.containsKey("saturation") ? componentId + ".saturation_modifier" : componentId + ".saturation";
        insertAttributePanelWidget(container, attributeEditorRow("Saturation", attributeNumberEditor(components, componentId, saturationPath, saturation, "Amount")));
        ToggleWidget alwaysEat = new ToggleWidget.Builder()
            .label("Always Eat")
            .toggled(Boolean.TRUE.equals(value.get("can_always_eat")))
            .size(180, 18)
            .onChange(next -> updateNestedAttributeComponent(components, componentId, componentId + ".can_always_eat", next))
            .entranceAnimation(false)
            .build();
        insertAttributePanelWidget(container, attributeEditorRow("Always Eat", alwaysEat));
    }

    private void addUseCooldownEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        insertAttributePanelWidget(container, attributeEditorRow("Cooldown", attributeNumberEditor(components, componentId, componentId + ".seconds", value.getOrDefault("seconds", 1.0), "Seconds")));
        TextInputWidget group = new TextInputWidget.Builder()
            .text(formatComponentValue(value.getOrDefault("cooldown_group", "minecraft:generic")))
            .placeholder("Group")
            .forcePlaceholder(false)
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .build();
        group.setOnChange(() -> updateNestedAttributeComponent(components, componentId, componentId + ".cooldown_group", normalizeMinecraftKey(group.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Cooldown Group", group));
    }

    private void addUseRemainderEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        TextInputWidget item = new TextInputWidget.Builder()
            .text(itemStackLine(value.isEmpty() ? simpleItemStack("minecraft:bowl", 1) : value))
            .placeholder("Item Count")
            .forcePlaceholder(false)
            .size(Math.max(100, attributeRowWidth() - 112), 18)
            .build();
        item.setOnChange(() -> updateNestedAttributeComponent(components, componentId, componentId, parseItemStackLine(item.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Remainder", searchableInputRow(item, materialOptions(), false, "server:minecraft:material")));
    }

    private void addDamageResistantEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        TextInputWidget types = new TextInputWidget.Builder()
            .text(formatComponentValue(value.getOrDefault("types", "#minecraft:is_fire")))
            .placeholder("Damage Type Or Tag")
            .forcePlaceholder(false)
            .size(Math.max(100, attributeRowWidth() - 112), 18)
            .build();
        types.setOnChange(() -> updateNestedAttributeComponent(components, componentId, componentId + ".types", normalizeDamageTypeSet(types.getText())));
        insertAttributePanelWidget(container, attributeEditorRow("Damage Types", searchableInputRow(types, damageTypeOptions(), false, "server:minecraft:damage_type")));
    }

    private void addWeaponEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        insertAttributePanelWidget(container, attributeEditorRow("Attack Damage Cost", attributeNumberEditor(components, componentId, componentId + ".item_damage_per_attack", value.getOrDefault("item_damage_per_attack", 1), "Durability")));
        insertAttributePanelWidget(container, attributeEditorRow("Block Disable Time", attributeNumberEditor(components, componentId, componentId + ".disable_blocking_for_seconds", value.getOrDefault("disable_blocking_for_seconds", 0.0), "Seconds")));
    }

    private void addEquippableEditorRows(Container container, Map<String, Object> components, String componentId, Map<String, Object> value) {
        insertAttributePanelWidget(container, attributeEditorRow("Slot", attributeChoiceEditor(components, componentId, componentId + ".slot", equipmentSlotOptions(), String.valueOf(value.getOrDefault("slot", "head")))));
        insertAttributePanelWidget(container, attributeEditorRow("Equip Sound", attributeSearchTextEditor(components, componentId, componentId + ".equip_sound", value.getOrDefault("equip_sound", "minecraft:item.armor.equip_generic"), soundOptions(), false, "server:minecraft:sound")));
        insertAttributePanelWidget(container, attributeEditorRow("Asset ID", attributeOptionalSearchTextEditor(components, componentId, componentId + ".asset_id", value.getOrDefault("asset_id", ""), equipmentAssetOptions(), "")));
        insertAttributePanelWidget(container, attributeEditorRow("Camera Overlay", attributeOptionalSearchTextEditor(components, componentId, componentId + ".camera_overlay", value.getOrDefault("camera_overlay", ""), materialModelOptions(), "server:minecraft:material")));
        insertAttributePanelWidget(container, attributeEditorRow("Dispensable", attributeBooleanEditor(components, componentId, componentId + ".dispensable", value.getOrDefault("dispensable", true))));
        insertAttributePanelWidget(container, attributeEditorRow("Swappable", attributeBooleanEditor(components, componentId, componentId + ".swappable", value.getOrDefault("swappable", true))));
        insertAttributePanelWidget(container, attributeEditorRow("Damage On Hurt", attributeBooleanEditor(components, componentId, componentId + ".damage_on_hurt", value.getOrDefault("damage_on_hurt", true))));
        insertAttributePanelWidget(container, attributeEditorRow("Equip On Interact", attributeBooleanEditor(components, componentId, componentId + ".equip_on_interact", value.getOrDefault("equip_on_interact", false))));
    }

    private String weaponSummary(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return "Weapon";
        }
        Object damage = map.get("item_damage_per_attack");
        Object disable = map.get("disable_blocking_for_seconds");
        return "Damage Cost " + (damage != null ? damage : 1) + " | Shield Disable " + (disable != null ? disable : 0) + "s";
    }

    private String equippableSummary(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return "Equipment";
        }
        Object slot = map.get("slot");
        Object sound = map.get("equip_sound");
        String slotText = slot != null && !slot.toString().isBlank() ? titleCaseAttributeToken(slot.toString()) : "Slot";
        return sound != null && !sound.toString().isBlank() ? "Slot " + slotText + " | Sound " + sound : "Slot " + slotText;
    }

    private ToggleWidget attributeBooleanEditor(Map<String, Object> components, String componentId, String path, Object value) {
        return new ToggleWidget.Builder()
            .toggled(Boolean.TRUE.equals(value))
            .size(180, 18)
            .onChange(next -> updateNestedAttributeComponent(components, componentId, path, next))
            .entranceAnimation(false)
            .build();
    }

    private Map<String, Object> defaultEquippableValue(String material) {
        String name = material != null ? material.toUpperCase(Locale.ROOT) : "";
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("slot", defaultEquipmentSlot(name));
        value.put("equip_sound", "minecraft:item.armor.equip_generic");
        value.put("dispensable", true);
        value.put("swappable", true);
        value.put("damage_on_hurt", true);
        value.put("equip_on_interact", false);
        return value;
    }

    private String defaultEquipmentSlot(String material) {
        if (material.contains("HELMET") || material.contains("HEAD") || material.contains("SKULL") || material.contains("CARVED_PUMPKIN")) {
            return "head";
        }
        if (material.contains("CHESTPLATE") || material.contains("ELYTRA")) {
            return "chest";
        }
        if (material.contains("LEGGINGS")) {
            return "legs";
        }
        if (material.contains("BOOTS")) {
            return "feet";
        }
        if (material.contains("WOLF_ARMOR") || material.contains("HORSE_ARMOR")) {
            return "body";
        }
        if (material.contains("SHIELD")) {
            return "offhand";
        }
        return "mainhand";
    }

    private String normalizeDamageTypeSet(String value) {
        String text = value != null && !value.isBlank() ? value.trim().toLowerCase(Locale.ROOT) : "#minecraft:is_fire";
        if (text.startsWith("#")) {
            String tag = text.substring(1);
            return "#" + (tag.contains(":") ? tag : "minecraft:" + tag);
        }
        return normalizeMinecraftKey(text);
    }

    private String damageResistantSummary(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object types = map.get("types");
            if (types != null && !types.toString().isBlank()) {
                return "Resists " + types;
            }
        }
        return "Damage Resistant";
    }

    private String useCooldownSummary(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return "Cooldown";
        }
        Object seconds = map.get("seconds");
        if (seconds != null && !seconds.toString().isBlank()) {
            return "Cooldown " + seconds + "s";
        }
        return "Cooldown";
    }

    private Object currentAttributeValue(Map<String, Object> components, String componentId) {
        if (activeAttributeComponents != null && activeAttributeComponents.containsKey(componentId)) {
            return activeAttributeComponents.get(componentId);
        }
        if (attributePreviewValues.containsKey(componentId)) {
            return attributePreviewValues.get(componentId);
        }
        return components.get(componentId);
    }

    private Map<String, Object> attributeDraftComponents(Map<String, Object> fallback) {
        if (activeAttributeComponents != null) {
            return copyAttributeComponents(activeAttributeComponents);
        }
        return copyAttributeComponents(fallback);
    }

    private TitledRowWidget attributeEditorRow(String title, AnimatedWidget widget) {
        int width = Math.max(180, attributeRowWidth() - 4);
        return new TitledRowWidget.Builder()
            .title(title)
            .description(attributeEditorDescription(title))
            .size(width, 36)
            .padding(4)
            .addWidget(widget)
            .build();
    }

    private TitledRowWidget attributeLargeEditorRow(String title, AnimatedWidget widget, int height) {
        int width = Math.max(180, attributeRowWidth() - 4);
        return new TitledRowWidget.Builder()
            .title(title)
            .description(attributeEditorDescription(title))
            .size(width, height)
            .padding(4)
            .addWidget(widget)
            .build();
    }

    private TitledRowWidget attributeEntryRow(String title, String description, AnimatedWidget... widgets) {
        int width = Math.max(180, attributeRowWidth() - 4);
        return new TitledRowWidget.Builder()
            .title(title)
            .description(description)
            .size(width, 36)
            .padding(4)
            .addWidget(widgets)
            .build();
    }

    private TextInputWidget attributeCompactInput(String value, String placeholder) {
        TextInputWidget input = new TextInputWidget.Builder()
            .text(value == null ? "" : value)
            .placeholder(placeholder)
            .forcePlaceholder(false)
            .size(120, 18)
            .build();
        ReSyncStudioPanelState.disableEntrance(input);
        return input;
    }

    private SquareButtonWidget attributeSearchButton(TextInputWidget input, List<String> options, String loadingCatalog, Consumer<String> onSelected) {
        return new SquareButtonWidget.Builder()
            .imagePath("search.png")
            .size(18, 18)
            .hint("Search")
            .entranceAnimation(false)
            .onClick(() -> {
                List<String> choices = normalizedOptions(options, input.getText());
                if (choices.size() == 1 && "Loading".equals(choices.getFirst())) {
                    if (loadingCatalog != null && !loadingCatalog.isBlank()) {
                        requestCatalog(loadingCatalog);
                    }
                    return;
                }
                showSearchSelector(choices, input.getText(), value -> {
                    if (isRealOption(value)) {
                        onSelected.accept(value);
                    }
                }, input.getX(), input.getY() + input.getHeight());
            })
            .build();
    }

    private SquareButtonWidget attributeDeleteButton(Runnable action) {
        return new SquareButtonWidget.Builder()
            .imagePath("delete.png")
            .size(18, 18)
            .hint("Delete")
            .accentType(ThemeManager.getAccent("danger"))
            .entranceAnimation(false)
            .onClick(action)
            .build();
    }

    private RowWidget attributeAddSearchRow(String placeholder, List<String> options, String loadingCatalog, Consumer<String> onAdd) {
        TextInputWidget input = attributeCompactInput("", placeholder);
        SquareButtonWidget search = attributeSearchButton(input, options, loadingCatalog, value -> input.setText(value));
        SquareButtonWidget add = new SquareButtonWidget.Builder()
            .imagePath("add.png")
            .size(18, 18)
            .hint("Add")
            .accentType(ThemeManager.getAccent("nice"))
            .entranceAnimation(false)
            .onClick(() -> {
                String value = input.getText();
                if (value == null || value.isBlank()) {
                    return;
                }
                onAdd.accept(value);
                input.setText("");
            })
            .build();
        RowWidget row = new RowWidget.Builder()
            .size(260, 18)
            .padding(4)
            .addWidget(input, search, add)
            .build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private void updateAttributeComponentRoot(Map<String, Object> components, String componentId, Object value, boolean rebuildEditor) {
        if (rebuildEditor) {
            markAttributeEditorDirty(componentId);
        }
        updateNestedAttributeComponent(components, componentId, componentId, value);
    }

    private void removeListAttributeEntry(Map<String, Object> components, String componentId, int index) {
        List<Object> next = new ArrayList<>(arrayValue(currentAttributeValue(components, componentId)));
        if (index < 0 || index >= next.size()) {
            return;
        }
        next.remove(index);
        updateAttributeComponentRoot(components, componentId, next, true);
    }

    private String attributeEditorDescription(String title) {
        return switch (title) {
            case "Action" -> "Remove Unsupported Data";
            case "Glint" -> "Controls The Enchantment Shine";
            case "Model ID" -> "Resource Model To Render";
            case "Model Data" -> "Custom Model Number";
            case "Text" -> "Visible Item Text";
            case "Lore" -> "One Line Per Tooltip Row";
            case "Hide In Tooltip" -> "Components Hidden From Tooltip";
            case "Use Time" -> "Seconds Needed To Use";
            case "Animation" -> "Use Animation";
            case "Sound" -> "Sound Played On Use";
            case "Cooldown" -> "Seconds Before Reuse";
            case "Cooldown Group" -> "Shared Cooldown Key";
            case "Remainder" -> "Item Left After Use";
            case "Damage Types" -> "Damage Type Or Tag";
            case "Particles" -> "Consumption Particles";
            case "Food Restored" -> "Hunger Points Restored";
            case "Saturation" -> "Food Saturation Amount";
            case "Always Eat" -> "Allow Use At Full Hunger";
            case "Levels" -> "One Enchantment And Level Per Line";
            case "Add Enchantment" -> "Search And Append An Enchantment";
            case "Modifiers" -> "Attribute Amount Operation Slot";
            case "Add Attribute" -> "Search And Append An Attribute";
            case "Material" -> "Registry Material ID";
            case "Pattern" -> "Registry Pattern ID";
            case "Flight" -> "Rocket Flight Duration";
            case "Shape" -> "Explosion Shape";
            case "Colors" -> "Comma Separated Hex Colors";
            case "Fade Colors" -> "Comma Separated Fade Colors";
            case "Trail" -> "Adds A Trail";
            case "Twinkle" -> "Adds Sparkle";
            case "Patterns" -> "One Pattern And Color Per Line";
            case "Add Pattern" -> "Search And Append A Pattern";
            case "Projectiles" -> "One Projectile ID And Count Per Line";
            case "Items" -> "One Item ID And Count Per Line";
            case "Slots" -> "Slot Item ID Count";
            case "Add Item" -> "Search And Append An Item";
            case "Break Blocks" -> "One Block Or Tag Per Line";
            case "Place On" -> "One Block Or Tag Per Line";
            case "Add Block" -> "Search And Append A Block";
            case "Tooltip" -> "Show This Data In Tooltip";
            case "Tooltip Details" -> "Extra Tooltip Visibility";
            case "Stack Size" -> "Maximum Stack Count";
            case "Max Durability" -> "Maximum Damage Capacity";
            case "Damage Used" -> "Current Damage Value";
            case "Repair Cost" -> "Anvil Repair Cost";
            case "Attack Damage Cost" -> "Durability Lost Per Attack";
            case "Block Disable Time" -> "Seconds Shield Blocking Is Disabled";
            case "Rarity" -> "Tooltip Rarity Color";
            case "Enchantability" -> "Enchanting Power";
            case "Amplifier" -> "Ominous Bottle Level";
            case "Instrument" -> "Goat Horn Instrument";
            case "Slot" -> "Equipment Slot";
            case "Equip Sound" -> "Sound When Equipped";
            case "Asset ID" -> "Equipment Model Asset";
            case "Camera Overlay" -> "Optional Overlay Texture";
            case "Dispensable" -> "Can Be Equipped By Dispenser";
            case "Swappable" -> "Can Swap From Hotbar";
            case "Damage On Hurt" -> "Takes Durability Damage";
            case "Equip On Interact" -> "Equip When Used On Entity";
            case "Potion" -> "Potion Type";
            case "Color" -> "Hex Color";
            case "Song" -> "Jukebox Song ID";
            case "Movement" -> "Movement Behavior";
            case "Projectile" -> "Projectile Behavior";
            case "Damage" -> "Damage Behavior";
            case "Durability" -> "Durability Behavior";
            default -> title;
        };
    }

    private RowWidget attributeSearchTextEditor(Map<String, Object> components, String componentId, String path, Object value, List<String> options, boolean append, String loadingCatalog) {
        return attributeSearchTextEditor(components, componentId, path, value, options, append, loadingCatalog, true);
    }

    private RowWidget attributeSearchTextEditor(Map<String, Object> components, String componentId, String path, Object value, List<String> options, boolean append, String loadingCatalog, boolean namespacedStrings) {
        TextInputWidget input = new TextInputWidget.Builder()
            .text(formatComponentValue(value))
            .placeholder(editorPathLabel(path))
            .forcePlaceholder(false)
            .size(Math.max(100, attributeRowWidth() - 112), 18)
            .build();
        input.setOnChange(() -> updateNestedAttributeComponent(components, componentId, path, parseSearchEditorValue(value, input.getText(), namespacedStrings)));
        return searchableInputRow(input, options, append, loadingCatalog);
    }

    private Object parseSearchEditorValue(Object previous, String text) {
        return parseSearchEditorValue(previous, text, true);
    }

    private Object parseSearchEditorValue(Object previous, String text, boolean namespacedStrings) {
        if (previous instanceof String) {
            if (text == null || text.isBlank()) {
                return "";
            }
            return namespacedStrings ? normalizeMinecraftKey(text) : text;
        }
        return parseComponentEditorValue(previous, text);
    }

    private RowWidget attributeOptionalSearchTextEditor(Map<String, Object> components, String componentId, String path, Object value, List<String> options, String loadingCatalog) {
        TextInputWidget input = new TextInputWidget.Builder()
            .text(formatComponentValue(value))
            .placeholder(editorPathLabel(path))
            .forcePlaceholder(false)
            .size(Math.max(100, attributeRowWidth() - 112), 18)
            .build();
        input.setOnChange(() -> updateNestedAttributeComponent(components, componentId, path, input.getText().isBlank() ? null : normalizeMinecraftKey(input.getText())));
        return searchableInputRow(input, options, false, loadingCatalog);
    }

    private RowWidget attributeLinePicker(CodeEditorWidget editor, List<String> options, String placeholder, String loadingCatalog, Function<String, String> formatter) {
        TextInputWidget input = new TextInputWidget.Builder()
            .text("")
            .placeholder(placeholder)
            .forcePlaceholder(true)
            .size(Math.max(100, attributeRowWidth() - 112), 18)
            .build();
        SquareButtonWidget selectorButton = new SquareButtonWidget.Builder()
            .imagePath("search.png")
            .size(18, 18)
            .hint("Search")
            .entranceAnimation(false)
            .onClick(() -> {
                List<String> choices = normalizedOptions(options, input.getText());
                if (choices.size() == 1 && "Loading".equals(choices.getFirst())) {
                    if (loadingCatalog != null && !loadingCatalog.isBlank()) {
                        requestCatalog(loadingCatalog);
                    }
                }
                showSearchSelector(choices, input.getText(), value -> {
                    if (!isRealOption(value)) {
                        return;
                    }
                    appendEditorLine(editor, formatter.apply(value));
                    input.setText("");
                }, input.getX(), input.getY() + input.getHeight());
            })
            .build();
        ReSyncStudioPanelState.disableEntrance(input);
        RowWidget row = new RowWidget.Builder()
            .size(260, 18)
            .padding(4)
            .addWidget(input)
            .addWidget(selectorButton)
            .build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private RowWidget attributeColorPicker(TextInputWidget input, boolean append) {
        SquareButtonWidget selectorButton = new SquareButtonWidget.Builder()
            .imagePath("search.png")
            .size(18, 18)
            .hint("Dye Color")
            .entranceAnimation(false)
            .onClick(() -> {
                List<String> choices = normalizedOptions(dyeColorOptions(), "");
                if (choices.size() == 1 && "Loading".equals(choices.getFirst())) {
                    requestCatalog("server:minecraft:dye_color");
                    return;
                }
                showSearchSelector(choices, "", value -> {
                    if (!isRealOption(value)) {
                        return;
                    }
                    String hex = dyeColorHex(value);
                    if (append) {
                        List<String> existing = splitCsv(input.getText());
                        if (!existing.contains(hex)) {
                            existing.add(hex);
                        }
                        input.setText(String.join(", ", existing));
                    } else {
                        input.setText(hex);
                    }
                    input.runOnChange();
                }, input.getX(), input.getY() + input.getHeight());
            })
            .build();
        ReSyncStudioPanelState.disableEntrance(input);
        RowWidget row = new RowWidget.Builder()
            .size(260, 18)
            .padding(4)
            .addWidget(input)
            .addWidget(selectorButton)
            .build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private void appendEditorLine(CodeEditorWidget editor, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String current = editor.getText();
        String next = current == null || current.isBlank() ? line.trim() : current.stripTrailing() + "\n" + line.trim();
        editor.setText(next);
        editor.notifyTextChanged();
    }

    private TextInputWidget attributeNumberEditor(Map<String, Object> components, String componentId, String path, Object value, String placeholder) {
        TextInputWidget input = new TextInputWidget.Builder()
            .text(formatComponentValue(value))
            .placeholder(placeholder)
            .forcePlaceholder(false)
            .size(Math.max(140, attributeRowWidth() - 80), 18)
            .build();
        input.setOnChange(() -> updateNestedAttributeComponent(components, componentId, path, parseComponentEditorValue(value instanceof Number ? value : 0.0, input.getText().replace(',', '.'))));
        return input;
    }

    private DropDownWidget<String> attributeChoiceEditor(Map<String, Object> components, String componentId, String path, List<String> choices, String selected) {
        return attributeDropdown(choices, selected, next -> updateNestedAttributeComponent(components, componentId, path, next));
    }

    private DropDownWidget<String> attributeDropdown(List<String> choices, String selected, Consumer<String> onChange) {
        return attributeDropdown(choices, selected, onChange, Function.identity());
    }

    private DropDownWidget<String> attributeDropdown(List<String> choices, String selected, Consumer<String> onChange, Function<String, String> displayFunction) {
        List<String> options = normalizedOptions(choices, selected);
        return new DropDownWidget.Builder<>(options)
            .selectedItem(resolveSelectedOption(options, selected))
            .displayFunction(displayFunction)
            .onSelectionChanged(value -> {
                if (isRealOption(value)) {
                    onChange.accept(value);
                }
            })
            .size(120, 18)
            .maxVisibleItems(10)
            .entranceAnimation(false)
            .build();
    }

    private DropDownWidget<String> attributeOperationDropdown(String selected, Consumer<String> onChange) {
        return attributeDropdown(List.of("add_value", "add_multiplied_base", "add_multiplied_total"), selected, onChange, this::attributeOperationLabel);
    }

    private String attributeOperationLabel(String operation) {
        return switch (operation != null ? operation : "") {
            case "add_value" -> "Add";
            case "add_multiplied_base" -> "Base %";
            case "add_multiplied_total" -> "Total %";
            default -> titleCaseAttributeToken(operation);
        };
    }

    private List<String> materialModelOptions() {
        List<String> models = new ArrayList<>();
        List<String> materials = materialOptions();
        if (materials.equals(List.of("Loading"))) {
            return materials;
        }
        for (String material : materials) {
            if (isRealOption(material)) {
                models.add("minecraft:" + material.toLowerCase(Locale.ROOT));
            }
        }
        return normalizedOptions(models, "");
    }

    private List<String> hiddenTooltipComponentOptions() {
        List<String> options = new ArrayList<>();
        for (String id : List.of(
            "minecraft:attribute_modifiers",
            "minecraft:banner_patterns",
            "minecraft:bundle_contents",
            "minecraft:can_break",
            "minecraft:can_place_on",
            "minecraft:charged_projectiles",
            "minecraft:container",
            "minecraft:custom_data",
            "minecraft:custom_model_data",
            "minecraft:dyed_color",
            "minecraft:enchantments",
            "minecraft:firework_explosion",
            "minecraft:fireworks",
            "minecraft:instrument",
            "minecraft:jukebox_playable",
            "minecraft:lore",
            "minecraft:potion_contents",
            "minecraft:stored_enchantments",
            "minecraft:trim"
        )) {
            if (!options.contains(id)) {
                options.add(id);
            }
        }
        return options;
    }

    private void updateNestedAttributeComponent(Map<String, Object> current, String componentId, String path, Object value) {
        if (!activeAttributeComponents.containsKey(componentId) && componentId.equals(selectedAttributeComponent)) {
            Object component = attributePreviewValues.containsKey(componentId) ? attributePreviewValues.get(componentId) : current.get(componentId);
            String relativePath = path != null && path.startsWith(componentId + ".") ? path.substring(componentId.length() + 1) : "";
            Object updated = updatePath(component, relativePath, value);
            if (updated == null && (path == null || path.equals(componentId))) {
                attributePreviewValues.remove(componentId);
            } else {
                attributePreviewValues.put(componentId, updated);
            }
            syncAttributeRowsInPlace();
            return;
        }
        Map<String, Object> draft = attributeDraftComponents(activeAttributeComponents != null ? activeAttributeComponents : current);
        boolean wasActive = draft.containsKey(componentId);
        Object component = draft.get(componentId);
        String relativePath = path != null && path.startsWith(componentId + ".") ? path.substring(componentId.length() + 1) : "";
        Object updated = updatePath(component, relativePath, value);
        if (updated == null && (path == null || path.equals(componentId))) {
            draft.remove(componentId);
        } else {
            draft.put(componentId, updated);
        }
        commitAttributeDesignerDraft(draft);
        if (wasActive != draft.containsKey(componentId)) {
            refreshAttributeComponentList(false);
        }
    }

    @SuppressWarnings("unchecked")
    private Object updatePath(Object current, String path, Object value) {
        if (path == null || path.isBlank()) {
            return value;
        }
        String head = path;
        String tail = "";
        int split = path.indexOf('.');
        if (split >= 0) {
            head = path.substring(0, split);
            tail = path.substring(split + 1);
        }
        if (current instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(entry.getKey().toString(), entry.getValue());
                }
            }
            if (tail.isBlank() && value == null) {
                copy.remove(head);
            } else {
                copy.put(head, updatePath(copy.get(head), tail, value));
            }
            return copy;
        }
        if (current instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list);
            try {
                int index = Integer.parseInt(head);
                if (index >= 0 && index < copy.size()) {
                    copy.set(index, updatePath(copy.get(index), tail, value));
                }
            } catch (NumberFormatException ignored) {
            }
            return copy;
        }
        if (current == null) {
            if (value == null) {
                return null;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.put(head, updatePath(null, tail, value));
            return copy;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put(head, updatePath(null, tail, value));
        return copy;
    }

    private Map<String, OptionCatalogItem> attributeCatalog(String source) {
        Map<String, OptionCatalogItem> catalog = new LinkedHashMap<>();
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, source)) {
            if (item.getValue() != null && !item.getValue().isBlank()) {
                catalog.put(item.getValue(), item);
            }
        }
        return catalog;
    }

    private Map<String, Object> schemaMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }

    private Map<String, Object> schemaFields(Map<String, Object> schema) {
        return schemaMap(schema != null ? schema.get("fields") : null);
    }

    private Map<String, Object> attributeComponentSchema(String componentId) {
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        String source = definition != null ? attributeSchemaSource(definition.getMaterial()) : ATTRIBUTE_SCHEMA_SOURCE;
        OptionCatalogItem item = attributeCatalogItem(source, componentId);
        Map<String, Object> metadata = item != null && item.getMetadata() != null ? item.getMetadata() : Map.of();
        return schemaMap(metadata.get("schema"));
    }

    private String schemaKind(Map<String, Object> schema, Object value) {
        Object kind = schema != null ? schema.get("kind") : null;
        if (kind != null && !kind.toString().isBlank() && !"raw".equals(kind.toString())) {
            return kind.toString();
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        return "string";
    }

    private Map<String, Object> objectValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }

    private List<Object> arrayValue(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    private Object defaultValueForSchema(Map<String, Object> schema) {
        return switch (schemaKind(schema, null)) {
            case "boolean" -> false;
            case "number" -> 0;
            case "array" -> new ArrayList<>();
            case "object" -> defaultObjectForSchema(schema);
            default -> "";
        };
    }

    private Map<String, Object> defaultObjectForSchema(Map<String, Object> schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schemaFields(schema).entrySet()) {
            result.put(entry.getKey(), defaultValueForSchema(schemaMap(entry.getValue())));
        }
        return result;
    }

    private List<String> activeComponents(Map<String, Object> components, Map<String, OptionCatalogItem> catalog) {
        return components.keySet().stream()
            .sorted((left, right) -> {
                OptionCatalogItem leftItem = catalog.get(left);
                OptionCatalogItem rightItem = catalog.get(right);
                int groupRank = Integer.compare(attributeBrowseGroupRank(leftItem, left), attributeBrowseGroupRank(rightItem, right));
                if (groupRank != 0) {
                    return groupRank;
                }
                int rank = Integer.compare(attributeBrowseRank(leftItem, left), attributeBrowseRank(rightItem, right));
                return rank != 0 ? rank : componentLabel(left).compareToIgnoreCase(componentLabel(right));
            })
            .toList();
    }

    private List<OptionCatalogItem> availableComponents(Map<String, Object> components, Map<String, OptionCatalogItem> catalog, String query, String material) {
        return catalog.values().stream()
            .filter(item -> item != null && shouldOfferAttributeComponent(item.getValue(), query, material))
            .filter(item -> !components.containsKey(item.getValue()))
            .filter(item -> matchesAttributeSearch(item.getValue(), query, item))
            .sorted((left, right) -> {
                int groupRank = Integer.compare(attributeBrowseGroupRank(left), attributeBrowseGroupRank(right));
                if (groupRank != 0) {
                    return groupRank;
                }
                int applicable = Boolean.compare(!metadataBoolean(left, "applicable", false), !metadataBoolean(right, "applicable", false));
                if (applicable != 0) {
                    return applicable;
                }
                int recommended = Boolean.compare(!metadataBoolean(left, "recommended", false), !metadataBoolean(right, "recommended", false));
                if (recommended != 0) {
                    return recommended;
                }
                int rank = Integer.compare(attributeBrowseRank(left), attributeBrowseRank(right));
                return rank != 0 ? rank : componentLabel(left.getValue()).compareToIgnoreCase(componentLabel(right.getValue()));
            })
            .toList();
    }

    private boolean shouldOfferAttributeComponent(String id, String query, String material) {
        return id != null && !id.isBlank();
    }

    private boolean isPrimaryAttributeForMaterial(String id, String material) {
        String value = material != null ? material.toUpperCase(Locale.ROOT) : "";
        return switch (id != null ? id : "") {
            case "minecraft:consumable",
                 "minecraft:food",
                 "minecraft:enchantment_glint_override",
                 "minecraft:custom_model_data",
                 "minecraft:item_model",
                 "minecraft:item_name",
                 "minecraft:custom_name",
                 "minecraft:lore",
                 "minecraft:tooltip_display",
                 "minecraft:can_break",
                 "minecraft:can_place_on",
                 "minecraft:max_stack_size",
                 "minecraft:max_damage",
                 "minecraft:damage",
                 "minecraft:repair_cost",
                 "minecraft:rarity",
                 "minecraft:enchantable",
                 "minecraft:dyed_color",
                 "minecraft:enchantments",
                 "minecraft:stored_enchantments",
                 "minecraft:unbreakable",
                 "minecraft:damage_resistant" -> true;
            case "minecraft:trim" -> materialContains(value, "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
            case "minecraft:firework_explosion" -> materialContains(value, "FIREWORK_STAR");
            case "minecraft:fireworks" -> materialContains(value, "FIREWORK_ROCKET");
            case "minecraft:banner_patterns" -> materialContains(value, "BANNER", "SHIELD");
            case "minecraft:charged_projectiles" -> materialContains(value, "CROSSBOW");
            case "minecraft:bundle_contents" -> materialContains(value, "BUNDLE");
            case "minecraft:container" -> materialContains(value, "SHULKER_BOX");
            case "minecraft:instrument" -> materialContains(value, "GOAT_HORN");
            case "minecraft:jukebox_playable" -> materialContains(value, "MUSIC_DISC");
            case "minecraft:potion_contents" -> materialContains(value, "POTION", "TIPPED_ARROW");
            case "minecraft:glider" -> materialContains(value, "ELYTRA");
            case "minecraft:intangible_projectile" -> materialContains(value, "ARROW");
            case "minecraft:ominous_bottle_amplifier" -> materialContains(value, "OMINOUS_BOTTLE");
            case "minecraft:weapon" -> materialContains(value, "SWORD", "AXE", "TRIDENT", "MACE");
            default -> false;
        };
    }

    private boolean materialContains(String material, String... tokens) {
        for (String token : tokens) {
            if (material.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private int attributeBrowseRank(String id) {
        return switch (id != null ? id : "") {
            case "minecraft:item_name",
                 "minecraft:custom_name",
                 "minecraft:lore",
                 "minecraft:tooltip_display" -> 0;
            case "minecraft:custom_model_data",
                 "minecraft:item_model",
                 "minecraft:dyed_color",
                 "minecraft:enchantment_glint_override",
                 "minecraft:attribute_modifiers",
                 "minecraft:trim",
                 "minecraft:firework_explosion",
                 "minecraft:fireworks",
                 "minecraft:banner_patterns",
                 "minecraft:charged_projectiles",
                 "minecraft:bundle_contents",
                 "minecraft:container" -> 1;
            case "minecraft:consumable",
                 "minecraft:food",
                 "minecraft:use_cooldown",
                 "minecraft:use_remainder",
                 "minecraft:damage_resistant",
                 "minecraft:potion_contents",
                 "minecraft:can_break",
                 "minecraft:can_place_on" -> 2;
            case "minecraft:max_stack_size",
                 "minecraft:max_damage",
                 "minecraft:damage",
                 "minecraft:unbreakable",
                 "minecraft:repair_cost",
                 "minecraft:rarity" -> 3;
            case "minecraft:enchantable",
                 "minecraft:enchantments",
                 "minecraft:stored_enchantments" -> 4;
            case "minecraft:instrument",
                 "minecraft:jukebox_playable",
                 "minecraft:equippable",
                 "minecraft:glider",
                 "minecraft:intangible_projectile",
                 "minecraft:ominous_bottle_amplifier" -> 5;
            default -> 10;
        };
    }

    private int attributeBrowseRank(OptionCatalogItem item) {
        return attributeBrowseRank(item, item != null ? item.getValue() : "");
    }

    private int attributeBrowseRank(OptionCatalogItem item, String id) {
        int fallback = attributeBrowseRank(id) * 100;
        return metadataInt(item, "priority", fallback);
    }

    private int attributeBrowseGroupRank(OptionCatalogItem item) {
        return attributeBrowseGroupRank(item, item != null ? item.getValue() : "");
    }

    private int attributeBrowseGroupRank(OptionCatalogItem item, String id) {
        return switch (attributeBrowseGroup(id, item)) {
            case "Text" -> 0;
            case "Visuals" -> 1;
            case "Use" -> 2;
            case "Combat" -> 3;
            case "Durability" -> 4;
            case "Enchanting" -> 5;
            case "Inventory" -> 6;
            case "Rules" -> 7;
            case "Effects" -> 8;
            case "World" -> 9;
            case "Entity Variants" -> 10;
            case "Data" -> 11;
            case "Behavior" -> 2;
            case "Special" -> 9;
            default -> 12;
        };
    }

    private String attributeBrowseGroup(String id) {
        return switch (attributeBrowseRank(id)) {
            case 0 -> "Text";
            case 1 -> "Visuals";
            case 2 -> "Behavior";
            case 3 -> "Durability";
            case 4 -> "Enchanting";
            case 5 -> "Special";
            default -> "Other";
        };
    }

    private String attributeBrowseGroup(String id, OptionCatalogItem item) {
        String group = item != null ? item.getGroup() : "";
        if (group != null && !group.isBlank()) {
            return group;
        }
        group = metadataString(item, "category", "");
        if (!group.isBlank()) {
            return group;
        }
        return attributeBrowseGroup(id);
    }

    private String attributeBrowseGroupDescription(String group) {
        return switch (group) {
            case "Text" -> "Names And Tooltip";
            case "Visuals" -> "Model And Appearance";
            case "Use" -> "Eating And Reuse";
            case "Combat" -> "Damage And Attacks";
            case "Behavior" -> "Use And Placement";
            case "Durability" -> "Limits And Damage";
            case "Enchanting" -> "Levels And Modifiers";
            case "Inventory" -> "Stored Items";
            case "Rules" -> "Adventure Rules";
            case "Effects" -> "Potions And Status";
            case "World" -> "World Interactions";
            case "Entity Variants" -> "Entity-Specific Data";
            case "Data" -> "Custom Component Data";
            case "Special" -> "Extra Item Behavior";
            default -> "Search Results";
        };
    }

    private boolean hasIntentionalAttributeEditor(String id) {
        if (hasSpecializedAttributeEditor(id)) {
            return true;
        }
        return !attributeComponentSchema(id).isEmpty();
    }

    private boolean hasSpecializedAttributeEditor(String id) {
        return switch (id != null ? id : "") {
            case "minecraft:consumable",
                 "minecraft:food",
                 "minecraft:use_cooldown",
                 "minecraft:use_remainder",
                 "minecraft:enchantment_glint_override",
                 "minecraft:custom_model_data",
                 "minecraft:item_model",
                 "minecraft:item_name",
                 "minecraft:custom_name",
                 "minecraft:lore",
                 "minecraft:tooltip_display",
                 "minecraft:attribute_modifiers",
                 "minecraft:can_break",
                 "minecraft:can_place_on",
                 "minecraft:trim",
                 "minecraft:firework_explosion",
                 "minecraft:fireworks",
                 "minecraft:banner_patterns",
                 "minecraft:charged_projectiles",
                 "minecraft:bundle_contents",
                 "minecraft:container",
                 "minecraft:max_stack_size",
                 "minecraft:max_damage",
                 "minecraft:damage",
                 "minecraft:repair_cost",
                 "minecraft:rarity",
                 "minecraft:enchantable",
                 "minecraft:ominous_bottle_amplifier",
                 "minecraft:instrument",
                 "minecraft:jukebox_playable",
                 "minecraft:dyed_color",
                 "minecraft:enchantments",
                 "minecraft:stored_enchantments",
                 "minecraft:potion_contents",
                 "minecraft:glider",
                 "minecraft:intangible_projectile",
                 "minecraft:damage_resistant",
                 "minecraft:weapon",
                 "minecraft:equippable",
                 "minecraft:unbreakable" -> true;
            default -> false;
        };
    }

    private boolean matchesAttributeSearch(String id, String query) {
        return matchesAttributeSearch(id, query, null);
    }

    private boolean matchesAttributeSearch(String id, String query, OptionCatalogItem item) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        String haystack = (id + " "
            + componentLabel(id) + " "
            + (item != null ? item.getLabel() : "") + " "
            + (item != null ? item.getDescription() : "") + " "
            + (item != null ? item.getGroup() : "") + " "
            + metadataSearchText(item != null ? item.getMetadata() : Map.of()) + " "
            + attributeSearchAliases(id)).toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }

    private int metadataInt(OptionCatalogItem item, String key, int fallback) {
        Object value = metadataValue(item, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private boolean metadataBoolean(OptionCatalogItem item, String key, boolean fallback) {
        Object value = metadataValue(item, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
    }

    private String metadataString(OptionCatalogItem item, String key, String fallback) {
        Object value = metadataValue(item, key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString();
    }

    private Object metadataValue(OptionCatalogItem item, String key) {
        if (item == null || key == null || key.isBlank()) {
            return null;
        }
        Map<String, Object> metadata = item.getMetadata() != null ? item.getMetadata() : Map.of();
        if (metadata.containsKey(key)) {
            return metadata.get(key);
        }
        Object ui = metadata.get("ui");
        if (ui instanceof Map<?, ?> uiMap) {
            return uiMap.get(key);
        }
        return null;
    }

    private String metadataSearchText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    builder.append(' ').append(entry.getKey());
                }
                builder.append(' ').append(metadataSearchText(entry.getValue()));
            }
            return builder.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object item : iterable) {
                builder.append(' ').append(metadataSearchText(item));
            }
            return builder.toString();
        }
        return value.toString();
    }

    private String attributeSearchAliases(String id) {
        String value = id != null ? id.toLowerCase(Locale.ROOT) : "";
        if (value.contains("food") || value.contains("consumable")) {
            return "eat edible eatable eating consume";
        }
        if (value.contains("use_cooldown")) {
            return "cooldown delay reuse timer group";
        }
        if (value.contains("use_remainder")) {
            return "remainder result container bowl bottle after use";
        }
        if (value.contains("custom_model_data") || value.contains("item_model")) {
            return "model texture resource";
        }
        if (value.contains("tooltip") || value.contains("lore")) {
            return "text description line hidden";
        }
        if (value.contains("dyed_color")) {
            return "color tint leather dye rgb hex";
        }
        if (value.contains("enchant")) {
            return "enchantment enchantments level magic glint";
        }
        if (value.contains("attribute_modifiers")) {
            return "attribute modifier damage speed health armor attack";
        }
        if (value.contains("trim")) {
            return "armor trim pattern material smithing";
        }
        if (value.contains("firework")) {
            return "rocket explosion color fade trail twinkle shape";
        }
        if (value.contains("banner_patterns")) {
            return "banner shield pattern color dye";
        }
        if (value.contains("charged_projectiles")) {
            return "crossbow loaded arrow projectile rocket";
        }
        if (value.contains("bundle_contents")) {
            return "bundle item contents stored inventory";
        }
        if (value.contains("container")) {
            return "inventory storage slot contents item";
        }
        if (value.contains("potion")) {
            return "potion effect bottle color";
        }
        if (value.contains("can_break")) {
            return "break mine block tag tool adventure";
        }
        if (value.contains("can_place_on")) {
            return "place block tag adventure";
        }
        if (value.contains("jukebox")) {
            return "music disc song record sound";
        }
        if (value.contains("instrument")) {
            return "goat horn sound";
        }
        if (value.contains("glider")) {
            return "elytra flight movement";
        }
        if (value.contains("equippable")) {
            return "equipment armor slot sound wearable";
        }
        if (value.contains("damage_resistant")) {
            return "fire lava damage resistant immunity type tag";
        }
        if (value.contains("weapon")) {
            return "weapon attack damage durability shield blocking disable";
        }
        if (value.contains("enchantable")) {
            return "enchant table enchantment power";
        }
        return "";
    }

    private Map<String, Object> activateNormalizedAttributeComponents(Map<String, Object> components) {
        Map<String, Object> normalized = normalizeAttributeComponents(components);
        if (!normalized.equals(copyAttributeComponents(components))) {
            setProperty("components", copyAttributeComponents(normalized));
            updateSummary();
            refreshContentPanelIfAttributeDesignerClosed();
        }
        return normalized;
    }

    private Map<String, Object> normalizeAttributeComponents(Map<String, Object> components) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (components == null) {
            return normalized;
        }
        boolean hideTooltip = false;
        boolean fireResistant = false;
        for (Map.Entry<String, Object> entry : components.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if ("minecraft:hide_tooltip".equals(entry.getKey())) {
                hideTooltip = !Boolean.FALSE.equals(entry.getValue());
                continue;
            }
            if ("minecraft:hide_additional_tooltip".equals(entry.getKey())) {
                continue;
            }
            if ("minecraft:fire_resistant".equals(entry.getKey())) {
                fireResistant = !Boolean.FALSE.equals(entry.getValue());
                continue;
            }
            Object value = normalizeAttributeComponentValue(entry.getKey(), entry.getValue());
            if (value != null) {
                normalized.put(entry.getKey(), copyAttributeValue(value));
            }
        }
        if (hideTooltip) {
            Map<String, Object> tooltip = objectValue(normalized.get("minecraft:tooltip_display"));
            tooltip.put("hide_tooltip", true);
            normalized.put("minecraft:tooltip_display", tooltip);
        }
        if (fireResistant && !normalized.containsKey("minecraft:damage_resistant")) {
            normalized.put("minecraft:damage_resistant", Map.of("types", "#minecraft:is_fire"));
        }
        return normalized;
    }

    private Object normalizeAttributeComponentValue(String id, Object value) {
        if (value == null) {
            return null;
        }
        if ("minecraft:attribute_modifiers".equals(id) && value instanceof Map<?, ?> map && map.containsKey("modifiers")) {
            return copyAttributeValue(map.get("modifiers"));
        }
        if (("minecraft:can_break".equals(id) || "minecraft:can_place_on".equals(id)) && value instanceof Map<?, ?> map && map.containsKey("predicates")) {
            return copyAttributeValue(map.get("predicates"));
        }
        if ("minecraft:trim".equals(id) && value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && !"show_in_tooltip".equals(entry.getKey().toString())) {
                    copy.put(entry.getKey().toString(), copyAttributeValue(entry.getValue()));
                }
            }
            return copy;
        }
        if ("minecraft:dyed_color".equals(id) && value instanceof Map<?, ?> map) {
            Object rgb = map.get("rgb");
            if (rgb instanceof Number number) {
                return Math.clamp(number.intValue(), 0, 0xFFFFFF);
            }
            if (rgb != null) {
                return parseColorValue(0xFFFFFF, rgb.toString());
            }
        }
        if ("minecraft:jukebox_playable".equals(id) && value instanceof Map<?, ?> map && map.get("song") != null) {
            return normalizeMinecraftKey(map.get("song").toString());
        }
        if (("minecraft:enchantments".equals(id) || "minecraft:stored_enchantments".equals(id)) && value instanceof Map<?, ?> map && map.containsKey("levels")) {
            return copyAttributeValue(map.get("levels"));
        }
        if ("minecraft:enchantable".equals(id) && !(value instanceof Map<?, ?>)) {
            return Map.of("value", parsePositiveInt(value, 10));
        }
        if (("minecraft:unbreakable".equals(id) || "minecraft:glider".equals(id) || "minecraft:intangible_projectile".equals(id)) && value instanceof Boolean bool) {
            return bool ? Map.of() : null;
        }
        return copyAttributeValue(value);
    }

    private Map<String, Object> copyAttributeComponents(Map<String, Object> components) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (components == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : components.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey(), copyAttributeValue(entry.getValue()));
            }
        }
        return copy;
    }

    private Object copyAttributeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(entry.getKey().toString(), copyAttributeValue(entry.getValue()));
                }
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(copyAttributeValue(item));
            }
            return copy;
        }
        return value;
    }

    private String schemaArrayText(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Object item : list) {
            lines.add(formatComponentValue(item));
        }
        return String.join("\n", lines);
    }

    private List<Object> schemaArrayValue(String value) {
        List<Object> result = new ArrayList<>();
        for (String line : splitLines(value)) {
            result.add(parseComponentEditorValue("", line));
        }
        return result;
    }

    private Object defaultComponentValueFromItem(OptionCatalogItem item) {
        if (item == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> metadata = item.getMetadata() != null ? item.getMetadata() : Map.of();
        Object defaultValue = metadata.get("defaultValue");
        if (defaultValue != null) {
            return defaultValue;
        }
        Object exampleValue = metadata.get("exampleValue");
        if (exampleValue != null) {
            return exampleValue;
        }
        return defaultValueForSchema(schemaMap(metadata.get("schema")));
    }

    private String editorPathLabel(String path) {
        if (path == null || path.isBlank()) {
            return "Value";
        }
        int split = path.lastIndexOf('.');
        String token = split >= 0 ? path.substring(split + 1) : path;
        if (token.contains(":")) {
            token = token.substring(token.indexOf(':') + 1);
        }
        if (token.matches("\\d+")) {
            return "Item " + (Integer.parseInt(token) + 1);
        }
        return humanizeAttributeToken(token);
    }

    private String humanizeAttributeToken(String token) {
        return switch (token != null ? token : "") {
            case "show_in_tooltip" -> "Show In Tooltip";
            case "hidden_components" -> "Hidden Components";
            case "custom_color", "rgb", "color" -> "Color";
            case "levels" -> "Levels";
            case "potion" -> "Potion";
            case "custom_effects" -> "Effects";
            case "consume_seconds" -> "Use Time";
            case "use_cooldown" -> "Use Cooldown";
            case "use_remainder" -> "Use Remainder";
            case "damage_resistant" -> "Damage Resistant";
            case "weapon" -> "Weapon";
            case "has_consume_particles" -> "Show Particles";
            case "can_always_eat" -> "Always Eat";
            case "saturation_modifier" -> "Saturation";
            case "nutrition" -> "Food Restored";
            case "animation" -> "Animation";
            case "sound" -> "Sound";
            case "floats" -> "Model Data";
            case "strings" -> "Text Values";
            case "flags" -> "Flags";
            case "colors" -> "Colors";
            case "id" -> "ID";
            case "type" -> "Type";
            case "amount" -> "Amount";
            case "operation" -> "Operation";
            case "slot" -> "Slot";
            case "min", "minimum" -> "Minimum";
            case "max", "maximum" -> "Maximum";
            case "value" -> "Value";
            default -> titleCaseAttributeToken(token);
        };
    }

    private String titleCaseAttributeToken(String token) {
        if (token == null || token.isBlank()) {
            return "Value";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : token.replace('_', ' ').replace('-', ' ').replace('.', ' ').split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            if ("id".equalsIgnoreCase(part)) {
                builder.append("ID");
            } else if ("rgb".equalsIgnoreCase(part)) {
                builder.append("RGB");
            } else {
                builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? "Value" : builder.toString();
    }

    private OptionCatalogItem attributeCatalogItem(String source, String id) {
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, source)) {
            if (id != null && id.equalsIgnoreCase(item.getValue())) {
                return item;
            }
        }
        return null;
    }

    private String attributeValueLabel(String source, String value) {
        OptionCatalogItem item = attributeCatalogItem(source, value);
        if (item != null && item.getLabel() != null && !item.getLabel().isBlank()) {
            return item.getLabel();
        }
        String cleaned = value != null && value.contains(":") ? value.substring(value.indexOf(':') + 1) : String.valueOf(value);
        if (cleaned.startsWith("generic.")) {
            cleaned = cleaned.substring("generic.".length());
        }
        return titleCaseAttributeToken(cleaned);
    }

    private String componentLabel(String id) {
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        OptionCatalogItem item = definition != null ? attributeCatalogItem(attributeSchemaSource(definition.getMaterial()), id) : null;
        if (item != null && item.getLabel() != null && !item.getLabel().isBlank()) {
            return item.getLabel();
        }
        String cleaned = id != null && id.contains(":") ? id.substring(id.indexOf(':') + 1) : String.valueOf(id);
        StringBuilder builder = new StringBuilder();
        for (String part : cleaned.replace('_', ' ').replace('.', ' ').replace('/', ' ').split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return builder.isEmpty() ? cleaned : builder.toString();
    }

    private String attributeSchemaSource(String material) {
        String normalized = material != null && !material.isBlank() ? material.toLowerCase(Locale.ROOT) : "";
        return normalized.isBlank() ? ATTRIBUTE_SCHEMA_SOURCE : ATTRIBUTE_SCHEMA_SOURCE + ":" + normalized;
    }

    private void preloadAttributeSchema() {
        requestCatalog(ATTRIBUTE_SCHEMA_SOURCE);
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        if (definition != null) {
            requestCatalog(attributeSchemaSource(definition.getMaterial()));
        }
    }

    private String formatComponentValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private Object parseComponentEditorValue(Object previous, String value) {
        if (previous instanceof Number) {
            try {
                String trimmed = value.trim();
                if (previous instanceof Byte || previous instanceof Short || previous instanceof Integer) {
                    return Integer.parseInt(trimmed);
                }
                if (previous instanceof Long) {
                    return Long.parseLong(trimmed);
                }
                double parsed = Double.parseDouble(trimmed);
                if (!trimmed.contains(".") && !trimmed.contains("e") && !trimmed.contains("E")) {
                    return (long) parsed;
                }
                return parsed;
            } catch (NumberFormatException ignored) {
                return previous;
            }
        }
        return value;
    }

    private RowWidget searchableInputRow(TextInputWidget input, List<String> options, boolean append, String loadingCatalog) {
        SquareButtonWidget selectorButton = new SquareButtonWidget.Builder()
            .imagePath("search.png")
            .size(18, 18)
            .hint("Search")
            .entranceAnimation(false)
            .onClick(() -> {
                List<String> choices = normalizedOptions(options, input.getText());
                if (choices.size() == 1 && "Loading".equals(choices.getFirst())) {
                    if (loadingCatalog != null && !loadingCatalog.isBlank()) {
                        requestCatalog(loadingCatalog);
                    }
                }
                showSearchSelector(choices, input.getText(), value -> {
                    if (!isRealOption(value)) {
                        return;
                    }
                    if (append && input.getText() != null && !input.getText().isBlank()) {
                        List<String> existing = splitCsv(input.getText());
                        if (!existing.contains(value)) {
                            existing.add(value);
                        }
                        input.setText(String.join(", ", existing));
                    } else {
                        input.setText(value);
                    }
                    input.runOnChange();
                }, input.getX(), input.getY() + input.getHeight());
            })
            .build();
        ReSyncStudioPanelState.disableEntrance(input);
        RowWidget row = new RowWidget.Builder()
            .size(260, 18)
            .padding(4)
            .addWidget(input)
            .addWidget(selectorButton)
            .build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private String selectedDropdownValue(DropDownWidget<String> dropdown) {
        if (dropdown == null) {
            return "";
        }
        String value = dropdown.getSelectedItem();
        return isRealOption(value) ? value : "";
    }

    private List<String> normalizedOptions(List<String> choices, String selected) {
        List<String> options = new ArrayList<>();
        if (choices != null) {
            for (String choice : choices) {
                if (choice != null && !choice.isBlank() && !options.contains(choice)) {
                    options.add(choice);
                }
            }
        }
        if (options.isEmpty()) {
            options.add("No Options");
        }
        if (selected != null && !selected.isBlank() && !"Loading".equals(selected) && !options.contains(selected)) {
            options.addFirst(selected);
        }
        return options;
    }

    private String resolveSelectedOption(List<String> options, String selected) {
        if (selected != null && options.contains(selected)) {
            return selected;
        }
        if (selected != null) {
            String normalized = selected.toUpperCase(Locale.ROOT);
            if (options.contains(normalized)) {
                return normalized;
            }
        }
        return options.isEmpty() ? null : options.getFirst();
    }

    private boolean isRealOption(String value) {
        return value != null && !"Loading".equals(value) && !"No Options".equals(value);
    }

    private List<String> splitCsv(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split("[,\\r\\n]+")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> splitLines(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private void showSearchSelector(List<String> options, String selected, Consumer<String> onSelected, int x, int y) {
        if (options == null || options.isEmpty()) {
            return;
        }
        closeActiveSearchSelector();
        closeNodeItemSelector();
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(this)
            .size(220, 240)
            .dismissOnSelect(true)
            .onClose(() -> {
                closeActiveSearchSelector(selectorRef[0]);
            })
            .build();
        selectorRef[0] = selector;
        for (String option : options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            selector.addItem(option, () -> onSelected.accept(option));
        }
        selector.setSelectedItem(selected);
        activeSearchSelector = selector;
        int selectorX = Math.clamp(x, 8, Math.max(8, width - selector.getWidth() - 8));
        int selectorY = Math.clamp(y, 32, Math.max(32, height - selector.getHeight() - 20));
        selector.show(selectorX, selectorY);
    }

    private void closeActiveSearchSelector() {
        closeActiveSearchSelector(activeSearchSelector);
    }

    private void closeActiveSearchSelector(ItemSelectorWidget selector) {
        if (selector != null) {
            selector.onClose = null;
            selector.hide();
        }
        if (selector == activeSearchSelector) {
            activeSearchSelector = null;
        }
        setFocusedWidget(null);
    }

    private void requestMissingCatalogFor(String label) {
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        if ("Material".equals(label)) {
            requestCatalog("server:minecraft:material");
            return;
        }
        if ("External ID".equals(label) && definition != null) {
            for (String source : providerCatalogSources(definition.getProvider())) {
                requestCatalog(source);
            }
        }
    }

    private void requestCatalog(String source) {
        if (flowManager != null && source != null && !OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
            flowManager.ensureFlowClient(serverId).requestOptionCatalog(source);
        }
    }

    private void preloadWorldOptions() {
        requestCatalog("server:minecraft:world");
    }

    private void refreshWorldOptions() {
        if (flowManager != null) {
            flowManager.ensureFlowClient(serverId).requestOptionCatalog("server:minecraft:world");
        }
    }

    private boolean clickExpandedPanelDropdown(double mouseX, double mouseY, int button) {
        for (int i = panelDropdowns.size() - 1; i >= 0; i--) {
            DropDownWidget<String> dropdown = panelDropdowns.get(i);
            if (dropdown.isVisible() && dropdown.isExpanded() && dropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    private void renderPanelDropdownOverlays(IDrawContext context, int mouseX, int mouseY, float delta) {
        for (DropDownWidget<String> dropdown : panelDropdowns) {
            if (dropdown.isVisible() && dropdown.isExpanded()) {
                dropdown.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void renderActiveSearchSelector(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (activeSearchSelector != null && activeSearchSelector.visible) {
            activeSearchSelector.render(context, mouseX, mouseY, delta);
            activeSearchSelector.renderHintOverlay(context);
        }
    }

    private void ensureContentStart() {
        if (CustomContentGraphAdapter.findStartNode(graph) == null) {
            CustomContentGraphAdapter.findOrCreateStartNode(graph, "item", flowId);
        }
    }

    private void setBranchEnabled(String branch, boolean enabled) {
        List<String> branches = enabled
            ? new ArrayList<>(CustomContentGraphAdapter.getEnabledTriggerBranches(graph))
            : new ArrayList<>(storedBranches());
        if (enabled && !branches.contains(branch)) {
            branches.add(branch);
        } else if (!enabled) {
            branches.remove(branch);
            detachBranch(branch);
        }
        if (branches.isEmpty()) {
            selectedBranch = "";
        } else {
            selectedBranch = branches.contains(selectedBranch) ? selectedBranch : branches.getFirst();
        }
        CustomContentGraphAdapter.setEnabledTriggerBranches(graph, branches);
        refreshNodeRegistry();
        updateSummary();
        updateEventRows();
    }

    private void setProperty(String key, Object value) {
        CustomContentGraphAdapter.setContentProperty(graph, key, value);
        if ("content_id".equals(key)) {
            String type = CustomContentGraphAdapter.contentType(graph);
            String id = value == null ? "" : String.valueOf(value).trim();
            if (type != null && !id.isBlank()) {
                graph.setId(CustomContentGraphAdapter.contentFlowId(type, id));
            }
        }
    }

    private String textProperty(String key) {
        Object value = CustomContentGraphAdapter.getContentProperty(graph, key, "");
        return value == null ? "" : String.valueOf(value);
    }

    private boolean boolProperty(String key) {
        Object value = CustomContentGraphAdapter.getContentProperty(graph, key, false);
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private String firstBranch() {
        List<String> branches = CustomContentGraphAdapter.getEnabledTriggerBranches(graph);
        return branches.isEmpty() ? "use" : branches.getFirst();
    }

    private String contentSummary(CustomContentDefinition definition) {
        return definition.getType() + " | " + definition.getProvider();
    }

    private String contentCardPreview(CustomContentDefinition definition, String type) {
        String asset = "vanilla".equalsIgnoreCase(definition.getProvider()) ? definition.getMaterial() : definition.getExternalId();
        return type + " | " + definition.getProvider() + " | " + asset;
    }

    private String contentValidationSummary(CustomContentDefinition definition) {
        List<String> issues = new ArrayList<>();
        if (definition.getId() == null || definition.getId().isBlank()) {
            issues.add("Missing ID");
        }
        if (definition.getDisplayName() == null || definition.getDisplayName().isBlank()) {
            issues.add("Missing Name");
        }
        if (!"vanilla".equalsIgnoreCase(definition.getProvider()) && (definition.getExternalId() == null || definition.getExternalId().isBlank())) {
            issues.add("Missing Asset");
        }
        if (!attributeValidationErrors.isEmpty()) {
            issues.add(attributeValidationErrors.size() == 1 ? "Component Error" : attributeValidationErrors.size() + " Component Errors");
        }
        for (String branch : CustomContentGraphAdapter.getEnabledTriggerBranches(graph)) {
            if (branchActionCount(branch) == 0) {
                issues.add(eventTitle(branch) + " Empty");
            }
        }
        return issues.isEmpty() ? "Ready" : String.join(", ", issues);
    }

    private void updateSummary() {
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        if (summaryWidget != null && definition != null) {
            summaryWidget.setName(definition.getDisplayName());
            summaryWidget.setDescription(contentSummary(definition));
        }
    }

    private void updateEventRows() {
        List<String> branches = CustomContentGraphAdapter.getEnabledTriggerBranches(graph);
        for (Map.Entry<String, MountableButtonWidget> entry : eventRows.entrySet()) {
            boolean enabled = branches.contains(entry.getKey());
            MountableButtonWidget row = entry.getValue();
            row.setDescription((enabled ? "Enabled" : "Off") + " | " + actionSummary(entry.getKey()));
            row.setSelected(entry.getKey().equals(selectedBranch));
        }
    }

    private String actionSummary(String branch) {
        int count = branchActionCount(branch);
        return count == 1 ? "1 Action" : count + " Actions";
    }

    private int branchActionCount(String branch) {
        FlowNode start = CustomContentGraphAdapter.findStartNode(graph);
        String startId = start != null ? findNodeId(start) : null;
        if (startId == null || graph.getConnections() == null) {
            return 0;
        }
        int count = 0;
        String nodeId = startId;
        String pin = branch;
        while (true) {
            FlowConnection next = null;
            for (FlowConnection connection : graph.getConnections()) {
                if (nodeId.equals(connection.getSourceNodeId()) && pin.equals(connection.getSourcePin())) {
                    next = connection;
                    break;
                }
            }
            if (next == null) {
                return count;
            }
            count++;
            nodeId = next.getTargetNodeId();
            pin = "flow";
        }
    }

    private String findNodeId(FlowNode node) {
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            if (entry.getValue() == node) {
                return entry.getKey();
            }
        }
        return null;
    }

    private List<String> storedBranches() {
        Object value = CustomContentGraphAdapter.getContentProperty(graph, CustomContentGraphAdapter.FLOW_BRANCHES_KEY, List.of());
        List<String> branches = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    branches.add(item.toString());
                }
            }
        }
        return branches;
    }

    private void detachBranch(String branch) {
        FlowNode start = CustomContentGraphAdapter.findStartNode(graph);
        String startId = start != null ? findNodeId(start) : null;
        if (startId == null || graph.getConnections() == null) {
            return;
        }
        graph.getConnections().removeIf(connection -> startId.equals(connection.getSourceNodeId()) && branch.equals(connection.getSourcePin()));
    }

    private List<String> materialOptions() {
        return catalogOptions("server:minecraft:material");
    }

    private List<String> worldOptions() {
        return catalogOptions("server:minecraft:world");
    }

    private List<String> catalogOptions(String source) {
        List<String> values = OptionCatalogCache.getInstance().getValues(serverId, source);
        if (!values.isEmpty()) {
            return values;
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
            requestCatalog(source);
            return List.of("Loading");
        }
        return List.of();
    }

    private List<String> providerOptions() {
        List<String> catalogProviders = catalogOptions("server:custom_content:provider");
        List<String> providers = new ArrayList<>(catalogProviders);
        providers.remove("Loading");
        if (!providers.contains("vanilla")) {
            providers.add("vanilla");
        }
        for (PackContentRegistry.ProviderStatus status : PackContentRegistry.get().statuses()) {
            String name = status.providerName().toLowerCase(Locale.ROOT);
            if (name.contains("nexo") && !providers.contains("nexo")) {
                providers.add("nexo");
            }
            if (name.contains("itemsadder") && !providers.contains("itemsadder")) {
                providers.add("itemsadder");
            }
        }
        return providers;
    }

    private List<String> providerAssetOptions(String provider) {
        List<String> catalogAssets = providerCatalogAssets(provider);
        if (!catalogAssets.isEmpty() && !catalogAssets.equals(List.of("Loading"))) {
            return catalogAssets;
        }
        List<String> result = new ArrayList<>();
        for (PackContentRegistry.PackAssetOption option : PackContentRegistry.get().assetOptions(provider)) {
            result.add(option.id());
        }
        if (result.isEmpty() && catalogAssets.equals(List.of("Loading"))) {
            return catalogAssets;
        }
        return result;
    }

    private List<String> providerCatalogAssets(String provider) {
        List<String> values = new ArrayList<>();
        for (String source : providerCatalogSources(provider)) {
            values.addAll(catalogOptions(source));
        }
        List<String> assets = values.stream()
            .filter(value -> !"Loading".equals(value))
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return assets.isEmpty() && values.contains("Loading") ? List.of("Loading") : assets;
    }

    private List<String> providerCatalogSources(String provider) {
        if (provider == null || !provider.equalsIgnoreCase("nexo")) {
            return List.of();
        }
        String type = CustomContentGraphAdapter.contentType(graph);
        return switch (type) {
            case "block" -> List.of("server:custom_content:nexo_block", "server:custom_content:nexo_furniture");
            case "armor" -> List.of("server:custom_content:nexo_armor");
            default -> List.of("server:custom_content:nexo_item");
        };
    }

    private String saveState(CustomContentDefinition definition) {
        SyncedResourceState flowState = flowManager != null ? flowManager.getFlowState(serverId, graph.getId()) : SyncedResourceState.CLEAN;
        SyncedResourceState contentState = flowManager != null && definition != null ? flowManager.getCustomContentState(serverId, definition.getId()) : flowState;
        SyncedResourceState state = contentState != SyncedResourceState.CLEAN ? contentState : flowState;
        return switch (state) {
            case DIRTY -> "Unsaved";
            case SAVING -> "Saving";
            case SAVED -> "Saved";
            case FAILED -> "Failed";
            case STALE -> "Stale";
            default -> "Clean";
        };
    }

    private String eventTitle(String pin) {
        String[] parts = pin.replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return builder.toString();
    }

    private boolean showsHandFilter(String type, String branch) {
        return !"armor".equals(type) && List.of("use", "left_click", "right_click", "hit_entity", "damage_entity", "break_block", "place", "break", "interact", "step_on").contains(branch);
    }

    private boolean showsTargetFilter(String branch) {
        return List.of("hit_entity", "damage_entity", "drop", "pickup", "damaged", "nearby_player", "tick").contains(branch);
    }

    private String defaultMaterial(String type) {
        return switch (type) {
            case "block" -> "STONE";
            case "armor" -> "IRON_CHESTPLATE";
            default -> "STICK";
        };
    }

    private String iconForType(String type) {
        return switch (type) {
            case "block" -> "block.png";
            case "armor" -> "armor.png";
            default -> "item.png";
        };
    }

    private static final class AttributeComponentRowState {
        private final String id;
        private final MountableButtonWidget row;
        private final ToggleWidget toggle;
        private OptionCatalogItem item;
        private List<AnimatedWidget> editorWidgets;
        private boolean editorDirty;

        private AttributeComponentRowState(String id, MountableButtonWidget row, ToggleWidget toggle) {
            this.id = id;
            this.row = row;
            this.toggle = toggle;
        }
    }

    private static final class AttributeModifierDropdownRefs {
        private DropDownWidget<String> operation;
        private DropDownWidget<String> slot;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return 100.0;
        }
    }

    private static final class StudioRootNodeWidget extends FlowNodeWidget {
        StudioRootNodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId, Runnable onClose) {
            super(x, y, node, graph, nodeId, serverId, onClose);
        }

        @Override
        protected boolean shouldShowLiteralInput(NodeDefinition.PinDefinition input) {
            return input == null || !STUDIO_ROOT_INPUTS.contains(input.getName());
        }

        @Override
        protected boolean shouldShowInputPin(NodeDefinition.PinDefinition input) {
            return input == null || !STUDIO_ROOT_INPUTS.contains(input.getName());
        }
    }
}
