package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.SyncedResourceState;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.packcontent.PackContentRegistry;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ContentStudioScreen extends FlowEditorScreen {
    private static final int PANEL_WIDTH = 150;
    private static final int PANEL_TOP = 54;
    private final String flowId;
    private final FlowManager flowManager;
    private SidePanel contentPanel;
    private String selectedBranch = "";
    private MountableButtonWidget summaryWidget;
    private final Map<String, MountableButtonWidget> eventRows = new java.util.HashMap<>();
    private final List<DropDownWidget<String>> panelDropdowns = new ArrayList<>();
    private ItemSelectorWidget activeSearchSelector;
    private static final Set<String> STUDIO_ROOT_INPUTS = Set.of(
        "content_id",
        "name",
        "material",
        "provider",
        "external_id",
        "custom_model_data",
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

    public ContentStudioScreen(String serverId, ClientServerView server, String flowId, Screen parent) {
        super(loadGraph(serverId, flowId), serverId, parent);
        this.flowId = flowId;
        this.flowManager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
    }

    private static FlowGraph loadGraph(String serverId, String flowId) {
        FlowManager manager = RemotelyClient.INSTANCE != null ? RemotelyClient.INSTANCE.getFlowManager() : null;
        if (manager == null || serverId == null || flowId == null) {
            return new FlowGraph();
        }
        FlowGraph graph = manager.getFlowsForServer(serverId).get(flowId);
        return graph != null ? graph : new FlowGraph();
    }

    @Override
    public String getDesktopAppId() {
        return "content-studio";
    }

    @Override
    public String getDesktopAppTitle() {
        return "Content Studio";
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
        if (paletteSidePanel != null) {
            paletteSidePanel.hide();
        }
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
    protected boolean showExtractButton() {
        return false;
    }

    @Override
    protected void addCustomHeaderButtons() {
        addHeaderButton(new IconButton.Builder()
            .label("Content")
            .imagePath("panel.png")
            .size(78, 18)
            .onClick(this::toggleContentPanel)
            .entranceAnimation(false)
            .build());
        addHeaderButton(new IconButton.Builder()
            .label("Open Nodes")
            .imagePath("graph.png")
            .size(104, 18)
            .onClick(this::toggleNodePalette)
            .entranceAnimation(false)
            .build());
    }

    @Override
    protected void onOptionCatalogRefreshed() {
        super.onOptionCatalogRefreshed();
        closeActiveSearchSelector();
        refreshContentPanel();
    }

    @Override
    public void renderHandler(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (contentPanel != null) {
            contentPanel.update();
        }
        super.renderHandler(context, mouseX, mouseY, delta);
        if (contentPanel != null) {
            contentPanel.renderHeader(context);
        }
        renderPanelDropdownOverlays(context, mouseX, mouseY, delta);
        renderActiveSearchSelector(context, mouseX, mouseY, delta);
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        if (contentPanel != null) {
            contentPanel.y(PANEL_TOP).height(height - PANEL_TOP - 10).update();
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.charTyped(chr, modifiers)) {
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
        super.close();
    }

    private void buildContentPanel() {
        contentPanel = new SidePanel(this, "contentPanel", this::updatePositions)
            .left()
            .width(PANEL_WIDTH)
            .y(PANEL_TOP)
            .height(height - PANEL_TOP - 10)
            .show();
        contentPanel.container()
            .layout(new ManagedLayout())
            .columns(1)
            .padding(5)
            .scrolling(true)
            .enableSelecting(false);
    }

    private void refreshContentPanel() {
        if (contentPanel == null) {
            return;
        }
        Container container = contentPanel.container();
        container.clearWidgets();
        eventRows.clear();
        panelDropdowns.clear();
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        String type = CustomContentGraphAdapter.contentType(graph);
        if (definition == null || type == null) {
            return;
        }
        int rowWidth = Math.max(260, (contentPanel != null ? contentPanel.getDesiredWidth() : PANEL_WIDTH) - 16);
        summaryWidget = new MountableButtonWidget.Builder(definition.getDisplayName())
            .description(contentSummary(definition))
            .iconPath(iconForType(type))
            .onClick(() -> selectedBranch = firstBranch())
            .addButton(new SquareButtonWidget.Builder().imagePath("save.png").hint("Save Content").onClick(this::onSave).build())
            .build();
        summaryWidget.setSize(rowWidth, 30);
        container.addWidget(summaryWidget);
        container.addWidget(textRow("Name", definition.getDisplayName(), rowWidth, value -> {
            setProperty("name", value);
            updateSummary();
        }));
        container.addWidget(dropdownRow("Type", List.of("item", "armor", "block"), type, rowWidth, value -> {
            FlowNode start = CustomContentGraphAdapter.findStartNode(graph);
            if (start != null) {
                start.setType(CustomContentGraphAdapter.nodeType(value));
                setProperty("material", defaultMaterial(value));
                selectedBranch = firstBranch();
                refreshNodeRegistry();
                refreshContentPanel();
            }
        }));
        container.addWidget(dropdownRow("Provider", providerOptions(), definition.getProvider(), rowWidth, value -> {
            setProperty("provider", value);
            refreshNodeRegistry();
            refreshContentPanel();
        }));
        if ("vanilla".equalsIgnoreCase(definition.getProvider())) {
            container.addWidget(searchableRow("Material", materialOptions(), definition.getMaterial(), rowWidth, value -> {
                setProperty("material", value.toUpperCase(Locale.ROOT));
                updateSummary();
            }));
        } else {
            container.addWidget(searchableRow("External ID", providerAssetOptions(definition.getProvider()), definition.getExternalId(), rowWidth, value -> {
                setProperty("external_id", value);
                updateSummary();
            }));
        }
        RowWidget detailRow = new RowWidget.Builder()
            .size(rowWidth, 20)
            .padding(4)
            .addWidget(new AnimatedButton.Builder().label("Details").onClick(this::showDetailsPopup).build())
            .addWidget(new AnimatedButton.Builder().label("Rules").onClick(this::showRulesPopup).build())
            .build();
        container.addWidget(detailRow);
        container.addWidget(section("Events", rowWidth));
        for (CustomContentGraphAdapter.TriggerDescriptor trigger : CustomContentGraphAdapter.triggersForType(type)) {
            container.addWidget(eventRow(trigger, rowWidth));
        }
        container.updateWidgetPositions();
    }

    private AnimatedButton section(String label, int width) {
        return new AnimatedButton.Builder()
            .label(label)
            .size(width, 20)
            .active(false)
            .animateElevation(false)
            .entranceAnimation(false)
            .accentType(ThemeManager.getAccent("calm"))
            .build();
    }

    private TitledRowWidget textRow(String label, String value, int width, java.util.function.Consumer<String> onChange) {
        TextInputWidget input = new TextInputWidget.Builder()
            .text(value == null ? "" : value)
            .placeholder(label)
            .forcePlaceholder(false)
            .onChange(onChange)
            .build();
        return new TitledRowWidget.Builder().title(label).size(width, 36).padding(4).addWidget(input).build();
    }

    private TitledRowWidget dropdownRow(String label, List<String> choices, String selected, int width, java.util.function.Consumer<String> onChange) {
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
        return new TitledRowWidget.Builder().title(label).size(width, 36).padding(4).addWidget(dropdown).build();
    }

    private TitledRowWidget searchableRow(String label, List<String> choices, String selected, int width, java.util.function.Consumer<String> onChange) {
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
        return new TitledRowWidget.Builder().title(label).size(width, 36).padding(4).addWidget(button).build();
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
            .description((enabled ? "Enabled" : "Off") + "  " + actionSummary(trigger.pin()))
            .onClick(() -> {
                selectedBranch = trigger.pin();
                updateEventRows();
            })
            .addWidget(toggle)
            .build();
        row.setSize(width, 30);
        if (selected) {
            row.setAccent(ThemeManager.getAccent("nice"));
        }
        eventRows.put(trigger.pin(), row);
        return row;
    }

    private void showDetailsPopup() {
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        if (definition == null) {
            return;
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("Details").size(460, 330).setResizable(true);
        TextInputWidget id = popupInput(definition.getId(), "Content ID");
        TextInputWidget model = popupInput(definition.getCustomModelData() == null ? "" : String.valueOf(definition.getCustomModelData()), "Custom Model Data");
        TextInputWidget tags = popupInput(String.join(", ", definition.getTags()), "Tags");
        CodeEditorWidget lore = new CodeEditorWidget(0, 0, 320, 150);
        lore.setText(String.join("\n", definition.getLore()));
        builder.addRow("ID", true, 22, id);
        builder.addRow("Model", true, 22, model);
        builder.addRow("Lore", true, 154, lore);
        builder.addRow("Tags", true, 22, tags);
        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton apply = new AnimatedButton.Builder()
            .label("Apply")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                setProperty("content_id", id.getText());
                setProperty("custom_model_data", model.getText());
                setProperty("lore", lore.getText());
                setProperty("tags", tags.getText());
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                updateSummary();
            })
            .build();
        builder.addRow("", true, 22, apply);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showRulesPopup() {
        String type = CustomContentGraphAdapter.contentType(graph);
        PopupWidget.Builder builder = new PopupWidget.Builder("Rules")
            .size(400, showsTargetFilter(selectedBranch) || showsHandFilter(type, selectedBranch) ? 270 : 230)
            .setResizable(false)
            .setExpandWithDropdowns(true);
        TextInputWidget permission = popupInput(textProperty("permission"), "Permission");
        TextInputWidget cooldown = popupInput(textProperty("cooldown_ticks"), "Cooldown");
        TextInputWidget chance = popupInput(textProperty("chance_percent"), "Chance");
        TextInputWidget worlds = popupInput(textProperty("allowed_worlds"), "Worlds");
        ToggleWidget cancel = new ToggleWidget.Builder().label("Cancel").toggled(boolProperty("cancel_event")).size(90, 20).build();
        ToggleWidget consume = new ToggleWidget.Builder().label("Consume").toggled(boolProperty("consume_event")).size(90, 20).build();
        builder.addRow("Permission", true, 22, permission);
        builder.addRow("Cooldown", true, 22, cooldown);
        builder.addRow("Chance", true, 22, chance);
        builder.addRow("Worlds", true, 22, searchableInputRow(worlds, worldOptions(), true));
        DropDownWidget<String> hand = null;
        DropDownWidget<String> target = null;
        if (showsHandFilter(type, selectedBranch)) {
            hand = popupDropdown(List.of("any", "main hand", "offhand"), textProperty("hand_filter"), value -> {});
            builder.addRow("Hand", true, 22, hand);
        }
        if (showsTargetFilter(selectedBranch)) {
            target = popupDropdown(List.of("any", "player", "living entity", "hostile", "passive"), textProperty("target_filter"), value -> {});
            builder.addRow("Target", true, 22, target);
        }
        builder.addRow("", true, 22, cancel, consume);
        PopupWidget[] popupRef = new PopupWidget[1];
        DropDownWidget<String> handRef = hand;
        DropDownWidget<String> targetRef = target;
        AnimatedButton apply = new AnimatedButton.Builder()
            .label("Apply")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                setProperty("permission", permission.getText());
                setProperty("cooldown_ticks", parseInt(cooldown.getText()));
                setProperty("chance_percent", parseDouble(chance.getText()));
                setProperty("allowed_worlds", worlds.getText());
                setProperty("cancel_event", cancel.getValue());
                setProperty("consume_event", consume.getValue());
                if (handRef != null) {
                    setProperty("hand_filter", selectedDropdownValue(handRef));
                }
                if (targetRef != null) {
                    setProperty("target_filter", selectedDropdownValue(targetRef));
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                updateSummary();
            })
            .build();
        builder.addRow("", true, 22, apply);
        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private TextInputWidget popupInput(String value, String placeholder) {
        return new TextInputWidget.Builder()
            .text(value == null ? "" : value)
            .placeholder(placeholder)
            .forcePlaceholder(false)
            .size(240, 20)
            .build();
    }

    private DropDownWidget<String> popupDropdown(List<String> choices, String selected, java.util.function.Consumer<String> onChange) {
        List<String> options = normalizedOptions(choices, selected);
        return new DropDownWidget.Builder<>(options)
            .selectedItem(resolveSelectedOption(options, selected))
            .onSelectionChanged(value -> {
                if (isRealOption(value)) {
                    onChange.accept(value);
                }
            })
            .size(240, 20)
            .maxVisibleItems(10)
            .entranceAnimation(false)
            .build();
    }

    private RowWidget searchableInputRow(TextInputWidget input, List<String> options, boolean append) {
        SquareButtonWidget selectorButton = new SquareButtonWidget.Builder()
            .imagePath("search.png")
            .hint("Search")
            .onClick(() -> {
                List<String> choices = normalizedOptions(options, input.getText());
                if (choices.size() == 1 && "Loading".equals(choices.getFirst())) {
                    requestCatalog("server:minecraft:world");
                    return;
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
                }, input.getX(), input.getY() + input.getHeight());
            })
            .build();
        return new RowWidget.Builder()
            .size(260, 20)
            .padding(4)
            .addWidget(input)
            .addWidget(selectorButton)
            .build();
    }

    private String selectedDropdownValue(DropDownWidget<String> dropdown) {
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

    private void showSearchSelector(List<String> options, String selected, java.util.function.Consumer<String> onSelected, int x, int y) {
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
            remove(selector);
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

    private void toggleContentPanel() {
        if (contentPanel != null) {
            contentPanel.toggle();
        }
    }

    private void toggleNodePalette() {
        if (paletteSidePanel != null) {
            paletteSidePanel.toggle();
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
        return definition.getType() + "  " + definition.getProvider();
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
            row.setDescription((enabled ? "Enabled" : "Off") + "  " + actionSummary(entry.getKey()));
            row.setAccent(entry.getKey().equals(selectedBranch) ? ThemeManager.getAccent("nice") : ThemeManager.getDefaultAccent());
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
