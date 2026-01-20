package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.data.TriggerType;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.RestrictedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlowManagerScreen extends ReScreen {
    private static final int HEADER_HEIGHT = 32;
    private static final int PANEL_PADDING = 8;
    private static final int SIDEBAR_WIDTH = 170;

    private final String serverId;
    private final ClientServerView server;
    private final FlowManager flowManager;

    private Container sidebar;
    private Container content;
    private Tab activeTab = Tab.BLUEPRINTS;

    private int lastWidth;
    private int lastHeight;

    private DropDownWidget<String> commandFlowSelect;
    private DropDownWidget<String> eventFlowSelect;
    private DropDownWidget<String> eventTypeSelect;
    private TextInputWidget commandAliasInput;

    private enum Tab {
        BLUEPRINTS,
        BINDINGS,
        GUIS
    }

    public FlowManagerScreen(String serverId, ClientServerView server) {
        super();
        this.serverId = serverId;
        this.server = server;
        this.flowManager = RemotelyClient.INSTANCE.getFlowManager();
    }

    @Override
    public void init() {
        super.init();
        buildLayout();
        rebuildContent();
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateLayout();
        super.render(context, mouseX, mouseY, delta);
    }

    private void buildLayout() {
        sidebar = createContainer("flow_manager_sidebar", PANEL_PADDING, HEADER_HEIGHT, SIDEBAR_WIDTH, height - HEADER_HEIGHT - PANEL_PADDING);
        sidebar.columns(1).padding(6).layout(new RestrictedLayout()).enableSelecting(false).scrolling(false);

        content = createContainer("flow_manager_content", SIDEBAR_WIDTH + PANEL_PADDING * 2, HEADER_HEIGHT,
            width - SIDEBAR_WIDTH - PANEL_PADDING * 3, height - HEADER_HEIGHT - PANEL_PADDING);
        content.columns(1).padding(6).layout(new RestrictedLayout()).enableSelecting(false).scrolling(true);

        addDrawableChild(sidebar);
        addDrawableChild(content);

        sidebar.addWidget(new TabButton("Blueprints", Tab.BLUEPRINTS));
        sidebar.addWidget(new TabButton("Bindings", Tab.BINDINGS));
        sidebar.addWidget(new TabButton("GUIs", Tab.GUIS));

        lastWidth = width;
        lastHeight = height;
    }

    private void updateLayout() {
        if (width == lastWidth && height == lastHeight) {
            return;
        }
        lastWidth = width;
        lastHeight = height;

        sidebar.setPosition(PANEL_PADDING, HEADER_HEIGHT);
        sidebar.size(SIDEBAR_WIDTH, height - HEADER_HEIGHT - PANEL_PADDING);

        int contentX = SIDEBAR_WIDTH + PANEL_PADDING * 2;
        content.setPosition(contentX, HEADER_HEIGHT);
        content.size(width - contentX - PANEL_PADDING, height - HEADER_HEIGHT - PANEL_PADDING);
        content.updateWidgetPositions();
    }

    private void rebuildContent() {
        content.clearWidgets();
        if (flowManager == null) {
            return;
        }

        switch (activeTab) {
            case BLUEPRINTS -> buildFlowList();
            case BINDINGS -> buildBindings();
            case GUIS -> buildGuiList();
        }
    }

    private void buildFlowList() {
        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("New Flow")
            .size(120, 22)
            .onClick(() -> {
                FlowGraph graph = flowManager.createFlow(serverId);
                flowManager.openFlowEditor(serverId, server, graph.getId().toString());
            })
            .build();
        content.addWidget(createButton);

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
                    rebuildContent();
                })
                .build();

            RowWidget row = new RowWidget.Builder()
                .size(Math.max(200, content.getWidth() - 20), 22)
                .addWidget(nameInput)
                .addWidget(openButton)
                .addWidget(deleteButton)
                .build();

            content.addWidget(row);
        }
    }

    private void buildGuiList() {
        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("New GUI")
            .size(120, 22)
            .onClick(() -> {
                String guiId = "gui_" + UUID.randomUUID().toString().substring(0, 8);
                GuiDefinition gui = flowManager.createGui(serverId, guiId);
                flowManager.openGuiDesigner(serverId, server, gui.getId());
            })
            .build();
        content.addWidget(createButton);

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
                    rebuildContent();
                })
                .build();

            RowWidget row = new RowWidget.Builder()
                .size(Math.max(200, content.getWidth() - 20), 22)
                .addWidget(nameInput)
                .addWidget(openButton)
                .addWidget(deleteButton)
                .build();

            content.addWidget(row);
        }
    }

    private void buildBindings() {
        List<String> flowOptions = new ArrayList<>(flowManager.getFlowsForServer(serverId).keySet());
        if (flowOptions.isEmpty()) {
            FlowGraph graph = flowManager.createFlow(serverId);
            flowOptions.add(graph.getId().toString());
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
            .onClick(() -> createCommandBinding())
            .build();

        RowWidget commandRow = new RowWidget.Builder()
            .size(Math.max(200, content.getWidth() - 20), 22)
            .addWidget(commandAliasInput)
            .addWidget(commandFlowSelect)
            .addWidget(createCommandButton)
            .build();

        content.addWidget(new SectionLabel("Commands"));
        content.addWidget(commandRow);

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
            .size(Math.max(200, content.getWidth() - 20), 22)
            .addWidget(eventTypeSelect)
            .addWidget(eventFlowSelect)
            .addWidget(createEventButton)
            .build();

        content.addWidget(new SectionLabel("Events"));
        content.addWidget(eventRow);

        content.addWidget(new SectionLabel("Active Bindings"));

        List<TriggerBinding> bindings = new ArrayList<>(flowManager.getBindings(serverId));
        bindings.sort(Comparator.comparing(TriggerBinding::getType).thenComparing(TriggerBinding::getContext));
        for (TriggerBinding binding : bindings) {
            RowWidget bindingRow = buildBindingRow(binding);
            content.addWidget(bindingRow);
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
                rebuildContent();
            })
            .build();

        return new RowWidget.Builder()
            .size(Math.max(200, content.getWidth() - 20), 22)
            .addWidget(bindingInfo)
            .addWidget(deleteButton)
            .build();
    }

    private void createCommandBinding() {
        String alias = commandAliasInput != null ? commandAliasInput.getText() : "";
        String flowId = commandFlowSelect != null ? commandFlowSelect.getSelectedItem() : null;
        if (alias == null || alias.isBlank() || flowId == null || flowId.isBlank()) {
            return;
        }

        TriggerBinding binding = new TriggerBinding(UUID.randomUUID().toString(), flowId, TriggerType.COMMAND, alias);
        flowManager.addBinding(serverId, binding);
        rebuildContent();
    }

    private void createEventBinding() {
        String eventType = eventTypeSelect != null ? eventTypeSelect.getSelectedItem() : null;
        String flowId = eventFlowSelect != null ? eventFlowSelect.getSelectedItem() : null;
        if (eventType == null || flowId == null) {
            return;
        }

        TriggerBinding binding = new TriggerBinding(UUID.randomUUID().toString(), flowId, TriggerType.EVENT, eventType);
        flowManager.addBinding(serverId, binding);
        rebuildContent();
    }

    private List<String> getEventOptions() {
        List<String> events = new ArrayList<>();
        events.add("join");
        events.add("quit");
        events.add("chat");
        events.add("sneak");
        events.add("death");
        events.add("block_break");
        events.add("block_place");
        return events;
    }

    private class TabButton extends AnimatedButton {
        private final Tab tab;

        private TabButton(String label, Tab tab) {
            super(0, 0, SIDEBAR_WIDTH - 10, 24, label);
            this.tab = tab;
            this.animateElevation = false;
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return;
            }
            activeTab = tab;
            rebuildContent();
        }
    }

    public void refresh() {
        rebuildContent();
    }

    public String getServerId() {
        return serverId;
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
