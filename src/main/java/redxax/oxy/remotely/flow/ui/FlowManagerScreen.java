package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.data.TriggerType;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlowManagerScreen extends ReScreen {
    private final String serverId;
    private final ClientServerView server;
    private final FlowManager flowManager;
    private final Screen parent;

    private TabsManager tabsManager;
    private Container blueprintsContainer;
    private Container bindingsContainer;
    private Container guisContainer;

    private DropDownWidget<String> commandFlowSelect;
    private DropDownWidget<String> eventFlowSelect;
    private DropDownWidget<String> eventTypeSelect;
    private TextInputWidget commandAliasInput;

    public FlowManagerScreen(String serverId, ClientServerView server, Screen parent) {
        super();
        this.serverId = serverId;
        this.server = server;
        this.flowManager = RemotelyClient.INSTANCE.getFlowManager();
        this.parent = parent;
    }

    @Override
    public void init() {
        super.init();

        IconButton closeButton = new IconButton.Builder()
            .imagePath("close.png")
            .size(18, 18)
            .onClick(this::close)
            .build();
        closeButton.setPosition(width - 25, 8);
        headerBuilder.addRight(closeButton);

        tabsManager = new TabsManager(this).builder()
            .position(5, 35).size(width - 10, 18)
            .allowAdd(false)
            .allowClose(false)
            .allowReorder(false)
            .onTabSelected(this::onTabSelected)
            .build();
        addDrawableChild(tabsManager);

        int contentY = 60;
        int contentHeight = height - contentY - 10;

        blueprintsContainer = createContainer("blueprints", 5, contentY, width - 10, contentHeight);
        blueprintsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);

        bindingsContainer = createContainer("bindings", 5, contentY, width - 10, contentHeight);
        bindingsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);

        guisContainer = createContainer("guis", 5, contentY, width - 10, contentHeight);
        guisContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);

        tabsManager.addTab("Blueprints", blueprintsContainer);
        tabsManager.addTab("Bindings", bindingsContainer);
        tabsManager.addTab("GUIs", guisContainer);

        rebuildBlueprints();
        rebuildBindings();
        rebuildGuis();

        tabsManager.setActiveTab(0);
    }

    private void onTabSelected(TabsManager.Tab tab) {
        if (tab.getContainer() == blueprintsContainer) {
            rebuildBlueprints();
        } else if (tab.getContainer() == bindingsContainer) {
            rebuildBindings();
        } else if (tab.getContainer() == guisContainer) {
            rebuildGuis();
        }
    }

    private void rebuildBlueprints() {
        blueprintsContainer.clearWidgets();

        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("New Flow")
            .size(120, 22)
            .onClick(this::showCreateFlowPopup)
            .build();
        blueprintsContainer.addWidget(createButton);

        Map<String, FlowGraph> flows = flowManager.getFlowsForServer(serverId);
        List<String> flowIds = new ArrayList<>(flows.keySet());
        flowIds.sort(Comparator.naturalOrder());

        for (String flowId : flowIds) {
            String displayName = flowManager.getFlowName(serverId, flowId);

            TextInputWidget nameInput = new TextInputWidget.Builder()
                .text(displayName)
                .placeholder("Flow name")
                .size(160, 22)
                .onChange(text -> flowManager.setFlowName(serverId, flowId, text))
                .build();

            AnimatedButton openButton = new AnimatedButton.Builder()
                .label("Open")
                .size(60, 22)
                .onClick(() -> flowManager.openFlowEditor(serverId, server, flowId))
                .build();

            AnimatedButton deleteButton = new AnimatedButton.Builder()
                .label("Delete")
                .size(60, 22)
                .accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> {
                    flowManager.deleteFlow(serverId, flowId);
                    rebuildBlueprints();
                })
                .build();

            RowWidget row = new RowWidget.Builder()
                .size(Math.max(200, blueprintsContainer.getWidth() - 20), 22)
                .addWidget(nameInput)
                .addWidget(openButton)
                .addWidget(deleteButton)
                .build();

            blueprintsContainer.addWidget(row);
        }
    }

    private void showCreateFlowPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create New Flow").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .placeholder("Flow ID (e.g. openLootBox)")
            .size(200, 22)
            .build();

        builder.addRow("ID", true, 22, idInput);

        PopupWidget[] popupRef = new PopupWidget[1];

        AnimatedButton createBtn = new AnimatedButton.Builder()
            .label("Create")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String id = idInput.getText();
                if (id != null && id.matches("^[a-zA-Z0-9_]+$")) {
                    if (flowManager.getFlowsForServer(serverId).containsKey(id)) {
                         new Notification("Error", "Flow ID already exists", Notification.Type.ERROR);
                         return;
                    }
                    FlowGraph graph = flowManager.createFlow(serverId, id);
                    if (popupRef[0] != null) popupRef[0].hide();
                    flowManager.openFlowEditor(serverId, server, graph.getId());
                } else {
                     new Notification("Error", "Invalid ID. Alphanumeric only.", Notification.Type.ERROR);
                }
            })
            .build();

         builder.addRow("", true, 20, createBtn);

         popupRef[0] = builder.build();
         addDrawableChild(popupRef[0]);
         popupRef[0].show();
    }

    private void rebuildBindings() {
        bindingsContainer.clearWidgets();

        List<String> flowOptions = new ArrayList<>(flowManager.getFlowsForServer(serverId).keySet());
        if (flowOptions.isEmpty()) {
            bindingsContainer.addWidget(new SectionLabel("No flows available. Create a flow first."));
            return;
        }

        commandAliasInput = new TextInputWidget.Builder()
            .text("/command")
            .placeholder("Command alias")
            .size(140, 22)
            .build();

        commandFlowSelect = new DropDownWidget.Builder<>(flowOptions)
            .size(140, 22)
            .build();

        AnimatedButton createCommandButton = new AnimatedButton.Builder()
            .label("New Command")
            .size(110, 22)
            .onClick(this::createCommandBinding)
            .build();

        RowWidget commandRow = new RowWidget.Builder()
            .size(Math.max(200, bindingsContainer.getWidth() - 20), 22)
            .addWidget(commandAliasInput)
            .addWidget(commandFlowSelect)
            .addWidget(createCommandButton)
            .build();

        bindingsContainer.addWidget(new SectionLabel("Commands"));
        bindingsContainer.addWidget(commandRow);

        eventTypeSelect = new DropDownWidget.Builder<>(getEventOptions())
            .size(160, 22)
            .build();

        eventFlowSelect = new DropDownWidget.Builder<>(flowOptions)
            .size(140, 22)
            .build();

        AnimatedButton createEventButton = new AnimatedButton.Builder()
            .label("New Event")
            .size(110, 22)
            .onClick(this::createEventBinding)
            .build();

        RowWidget eventRow = new RowWidget.Builder()
            .size(Math.max(200, bindingsContainer.getWidth() - 20), 22)
            .addWidget(eventTypeSelect)
            .addWidget(eventFlowSelect)
            .addWidget(createEventButton)
            .build();

        bindingsContainer.addWidget(new SectionLabel("Events"));
        bindingsContainer.addWidget(eventRow);

        bindingsContainer.addWidget(new SectionLabel("Active Bindings"));

        List<TriggerBinding> bindings = new ArrayList<>(flowManager.getBindings(serverId));
        bindings.sort(Comparator.comparing(TriggerBinding::getType).thenComparing(TriggerBinding::getContext));
        for (TriggerBinding binding : bindings) {
            RowWidget bindingRow = buildBindingRow(binding);
            bindingsContainer.addWidget(bindingRow);
        }
    }

    private RowWidget buildBindingRow(TriggerBinding binding) {
        TextInputWidget bindingInfo = new TextInputWidget.Builder()
            .text(binding.getType() + ": " + binding.getContext() + " -> " + binding.getFlowId())
            .placeholder("Binding")
            .size(200, 22)
            .active(false)
            .build();

        AnimatedButton deleteButton = new AnimatedButton.Builder()
            .label("Remove")
            .size(80, 22)
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                flowManager.removeBinding(serverId, binding.getId());
                rebuildBindings();
            })
            .build();

        return new RowWidget.Builder()
            .size(Math.max(200, bindingsContainer.getWidth() - 20), 22)
            .addWidget(bindingInfo)
            .addWidget(deleteButton)
            .build();
    }

    private void rebuildGuis() {
        guisContainer.clearWidgets();

        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("New GUI")
            .size(120, 22)
            .onClick(() -> {
                String guiId = "gui_" + UUID.randomUUID().toString().substring(0, 8);
                GuiDefinition gui = flowManager.createGui(serverId, guiId);
                flowManager.openGuiDesigner(serverId, server, gui.getId());
            })
            .build();
        guisContainer.addWidget(createButton);

        Map<String, GuiDefinition> guis = flowManager.getGuisForServer(serverId);
        List<String> guiIds = new ArrayList<>(guis.keySet());
        guiIds.sort(Comparator.naturalOrder());

        for (String guiId : guiIds) {
            String displayName = flowManager.getGuiName(serverId, guiId);
            GuiDefinition gui = guis.get(guiId);

            TextInputWidget nameInput = new TextInputWidget.Builder()
                .text(displayName)
                .placeholder("GUI name")
                .size(160, 22)
                .onChange(text -> {
                    flowManager.setGuiName(serverId, guiId, text);
                    if (gui != null) {
                        gui.setTitle(text);
                    }
                })
                .build();

            AnimatedButton openButton = new AnimatedButton.Builder()
                .label("Open")
                .size(60, 22)
                .onClick(() -> flowManager.openGuiDesigner(serverId, server, guiId))
                .build();

            AnimatedButton deleteButton = new AnimatedButton.Builder()
                .label("Delete")
                .size(60, 22)
                .accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> {
                    flowManager.deleteGui(serverId, guiId);
                    rebuildGuis();
                })
                .build();

            RowWidget row = new RowWidget.Builder()
                .size(Math.max(200, guisContainer.getWidth() - 20), 22)
                .addWidget(nameInput)
                .addWidget(openButton)
                .addWidget(deleteButton)
                .build();

            guisContainer.addWidget(row);
        }
    }

    private void createCommandBinding() {
        String alias = commandAliasInput != null ? commandAliasInput.getText() : "";
        String flowId = commandFlowSelect != null ? commandFlowSelect.getSelectedItem() : null;
        if (alias == null || alias.isBlank() || flowId == null || flowId.isBlank()) {
            return;
        }

        TriggerBinding binding = new TriggerBinding(UUID.randomUUID().toString(), flowId, TriggerType.COMMAND, alias);
        flowManager.addBinding(serverId, binding);
        rebuildBindings();
    }

    private void createEventBinding() {
        String eventType = eventTypeSelect != null ? eventTypeSelect.getSelectedItem() : null;
        String flowId = eventFlowSelect != null ? eventFlowSelect.getSelectedItem() : null;
        if (eventType == null || flowId == null) {
            return;
        }

        TriggerBinding binding = new TriggerBinding(UUID.randomUUID().toString(), flowId, TriggerType.EVENT, eventType);
        flowManager.addBinding(serverId, binding);
        rebuildBindings();
    }

    private List<String> getEventOptions() {
        List<String> events = new ArrayList<>();
        if (NodeRegistry.getInstance() != null && NodeRegistry.getInstance().hasDefinitions(serverId)) {
            Map<String, NodeDefinition> definitions = NodeRegistry.getInstance().getAllDefinitions(serverId);
            for (NodeDefinition def : definitions.values()) {
                if (def != null && def.getId() != null && def.getId().startsWith("event:")) {
                    events.add(def.getId().substring(6));
                }
            }
        }
        events.sort(String.CASE_INSENSITIVE_ORDER);
        return events;
    }

    public void refresh() {
        if (tabsManager != null) {
             onTabSelected(tabsManager.getActiveTab());
        }
    }

    public String getServerId() {
        return serverId;
    }

    @Override
    public void close() {
        super.close();
        ScreenManager.getInstance().setScreen(parent);
    }

    private static class SectionLabel extends AnimatedWidget {
        private final String label;

        private SectionLabel(String label) {
            super(0, 0, 120, 18, "");
            this.label = label;
            animateElevation = false;
            enableHoverColors = false;
            flat = true;
            transparent = true;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            ctx.drawText(label, getX() + 4, getY() + 4, ThemeManager.getColor(ThemeColor.textDark), true);
        }

        @Override
        public boolean canBeFocused() {
            return false;
        }
    }
}
