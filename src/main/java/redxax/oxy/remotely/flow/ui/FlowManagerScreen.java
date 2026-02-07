package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class FlowManagerScreen extends ReScreen {
    private final String serverId;
    private final ClientServerView server;
    private final FlowManager flowManager;
    private final Screen parent;

    private TabsManager tabsManager;
    private Container blueprintsContainer;
    private Container guisContainer;

    public FlowManagerScreen(String serverId, ClientServerView server, Screen parent) {
        super();
        this.serverId = serverId;
        this.server = server;
        this.flowManager = RemotelyClient.INSTANCE.getFlowManager();
        this.parent = parent;
    }

    public String getDesktopAppId() {
        return "flow-manager";
    }

    public String getDesktopAppTitle() {
        return "Flow Manager";
    }

    public String getDesktopAppIconPath() {
        return "change.png";
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

        guisContainer = createContainer("guis", 5, contentY, width - 10, contentHeight);
        guisContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);

        tabsManager.addTab("Blueprints", blueprintsContainer);
        tabsManager.addTab("GUIs", guisContainer);

        rebuildBlueprints();
        rebuildGuis();

        tabsManager.setActiveTab(0);
    }

    private void onTabSelected(TabsManager.Tab tab) {
        if (tab.getContainer() == blueprintsContainer) {
            rebuildBlueprints();
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
            FlowGraph graph = flows.get(flowId);
            String displayName = flowManager.getFlowName(serverId, flowId);
            if (displayName == null || displayName.isBlank()) {
                displayName = flowId;
            }

            int nodeCount = graph != null && graph.getNodes() != null ? graph.getNodes().size() : 0;
            int connectionCount = graph != null && graph.getConnections() != null ? graph.getConnections().size() : 0;
            int variableCount = graph != null && graph.getLocalVariables() != null ? graph.getLocalVariables().size() : 0;
            String description = "Nodes: " + nodeCount + " | Links: " + connectionCount + " | Vars: " + variableCount;

            SquareButtonWidget editButton = new SquareButtonWidget.Builder()
                .imagePath("edit.png")
                .onClick(() -> showRenameFlowPopup(flowId))
                .build();

            SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
                .imagePath("delete.png")
                .accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> {
                    flowManager.deleteFlow(serverId, flowId);
                    rebuildBlueprints();
                })
                .build();

            MountableButtonWidget widget = new MountableButtonWidget.Builder(displayName)
                .description(description)
                .onClick(() -> flowManager.openFlowEditor(serverId, server, flowId))
                .addButton(editButton)
                .addButton(deleteButton)
                .build();
            widget.setSize(Math.max(200, blueprintsContainer.getWidth() - 20), 28);

            blueprintsContainer.addWidget(widget);
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

    private void showRenameFlowPopup(String flowId) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Rename Flow").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .text(flowId)
            .placeholder("Flow ID")
            .size(200, 22)
            .build();

        builder.addRow("ID", true, 22, idInput);

        PopupWidget[] popupRef = new PopupWidget[1];

        AnimatedButton saveBtn = new AnimatedButton.Builder()
            .label("Save")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String newId = idInput.getText() != null ? idInput.getText().trim() : "";
                if (!newId.matches("^[a-zA-Z0-9_]+$")) {
                    new Notification("Error", "Invalid ID. Alphanumeric only.", Notification.Type.ERROR);
                    return;
                }
                if (newId.equals(flowId)) {
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    return;
                }
                if (flowManager.getFlowsForServer(serverId).containsKey(newId)) {
                    new Notification("Error", "Flow ID already exists", Notification.Type.ERROR);
                    return;
                }
                if (!flowManager.renameFlow(serverId, flowId, newId)) {
                    new Notification("Error", "Unable to rename flow.", Notification.Type.ERROR);
                    return;
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                rebuildBlueprints();
            })
            .build();

        builder.addRow("", true, 20, saveBtn);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void rebuildGuis() {
        guisContainer.clearWidgets();

        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("New GUI")
            .size(120, 22)
            .onClick(this::showCreateGuiPopup)
            .build();
        guisContainer.addWidget(createButton);

        Map<String, GuiDefinition> guis = flowManager.getGuisForServer(serverId);
        List<String> guiIds = new ArrayList<>(guis.keySet());
        guiIds.sort(Comparator.naturalOrder());

        for (String guiId : guiIds) {
            GuiDefinition gui = guis.get(guiId);
            String displayName = flowManager.getGuiName(serverId, guiId);
            if ((displayName == null || displayName.isBlank()) && gui != null && gui.getTitle() != null) {
                displayName = gui.getTitle();
            }
            if (displayName == null || displayName.isBlank()) {
                displayName = guiId;
            }

            int elementCount = gui != null && gui.getElements() != null ? gui.getElements().size() : 0;
            int rows = gui != null ? Math.max(gui.getRows(), 0) : 0;
            String description = "Rows: " + rows + " | Elements: " + elementCount;

            SquareButtonWidget editButton = new SquareButtonWidget.Builder()
                .imagePath("edit.png")
                .onClick(() -> showRenameGuiPopup(guiId))
                .build();

            SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
                .imagePath("trash.png")
                .accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> {
                    flowManager.deleteGui(serverId, guiId);
                    rebuildGuis();
                })
                .build();

            MountableButtonWidget widget = new MountableButtonWidget.Builder(displayName)
                .description(description)
                .hiddenText("ID: " + guiId)
                .onClick(() -> flowManager.openGuiDesigner(serverId, server, guiId))
                .addButton(editButton)
                .addButton(deleteButton)
                .build();
            widget.setSize(Math.max(200, guisContainer.getWidth() - 20), 28);

            guisContainer.addWidget(widget);
        }
    }

    private void showCreateGuiPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create New GUI").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .placeholder("GUI ID (e.g. main_menu)")
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
                    if (flowManager.getGuisForServer(serverId).containsKey(id)) {
                        new Notification("Error", "GUI ID already exists", Notification.Type.ERROR);
                        return;
                    }
                    GuiDefinition gui = flowManager.createGui(serverId, id);
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    flowManager.openGuiDesigner(serverId, server, gui.getId());
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

    private void showRenameGuiPopup(String guiId) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Rename GUI").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .text(guiId)
            .placeholder("GUI ID")
            .size(200, 22)
            .build();

        builder.addRow("ID", true, 22, idInput);

        PopupWidget[] popupRef = new PopupWidget[1];

        AnimatedButton saveBtn = new AnimatedButton.Builder()
            .label("Save")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String newId = idInput.getText() != null ? idInput.getText().trim() : "";
                if (!newId.matches("^[a-zA-Z0-9_]+$")) {
                    new Notification("Error", "Invalid ID. Alphanumeric only.", Notification.Type.ERROR);
                    return;
                }
                if (newId.equals(guiId)) {
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    return;
                }
                if (flowManager.getGuisForServer(serverId).containsKey(newId)) {
                    new Notification("Error", "GUI ID already exists", Notification.Type.ERROR);
                    return;
                }
                if (!flowManager.renameGui(serverId, guiId, newId)) {
                    new Notification("Error", "Unable to rename GUI.", Notification.Type.ERROR);
                    return;
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                rebuildGuis();
            })
            .build();

        builder.addRow("", true, 20, saveBtn);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
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

}
