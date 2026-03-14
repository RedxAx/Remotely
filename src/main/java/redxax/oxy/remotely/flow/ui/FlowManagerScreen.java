package redxax.oxy.remotely.flow.ui;

import com.google.gson.Gson;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.world.WorldDashboardEntry;
import redxax.oxy.remotely.data.flow.world.WorldGameRuleDescriptor;
import redxax.oxy.remotely.data.flow.world.WorldGeneratorDescriptor;
import redxax.oxy.remotely.data.flow.world.WorldInventoryGroup;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.data.flow.world.WorldPortal;
import redxax.oxy.remotely.data.flow.world.WorldProfileSettings;
import redxax.oxy.remotely.data.flow.world.WorldRegistryEntry;
import redxax.oxy.remotely.data.flow.world.WorldSignPortal;
import redxax.oxy.remotely.data.flow.world.WorldSnapshot;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static restudio.rescreen.config.Config.desktopMode;

public class FlowManagerScreen extends ReScreen {
    private static final Map<String, FlowManagerScreen> OPEN_SCREENS = new HashMap<>();
    private final String serverId;
    private final ClientServerView server;
    private final FlowManager flowManager;
    private final Screen parent;
    private final boolean tabMethodsAvailable;

    private TabsManager tabsManager;
    private Container blueprintsContainer;
    private Container guisContainer;
    private Container scoreboardsContainer;
    private Container worldsContainer;
    private Container portalsContainer;
    private Container inventoryGroupsContainer;
    private Container signPortalsContainer;
    private Container tabsContainer;
    private final Map<String, MountableButtonWidget> blueprintEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> guiEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> scoreboardEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> worldEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> portalEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> inventoryGroupEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> signPortalEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> tabEntries = new HashMap<>();
    private final Gson gson = new Gson();

    private static class CommandBindingContext {
        private String command;
        private List<String> subcommands;
        private Boolean structured;
    }

    private static class SelectorWidgets {
        private final TextInputWidget input;
        private final DropDownWidget<String> dropdown;

        private SelectorWidgets(TextInputWidget input, DropDownWidget<String> dropdown) {
            this.input = input;
            this.dropdown = dropdown;
        }
    }

    public FlowManagerScreen(String serverId, ClientServerView server, Screen parent) {
        super();
        this.serverId = serverId;
        this.server = server;
        this.flowManager = RemotelyClient.INSTANCE.getFlowManager();
        this.parent = parent;
        this.tabMethodsAvailable = hasTabMethods(this.flowManager);
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
        OPEN_SCREENS.put(serverId, this);

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
        scoreboardsContainer = createContainer("scoreboards", 5, contentY, width - 10, contentHeight);
        scoreboardsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);
        worldsContainer = createContainer("worlds", 5, contentY, width - 10, contentHeight);
        worldsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);
        portalsContainer = createContainer("portals", 5, contentY, width - 10, contentHeight);
        portalsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);
        inventoryGroupsContainer = createContainer("inventory-groups", 5, contentY, width - 10, contentHeight);
        inventoryGroupsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);
        signPortalsContainer = createContainer("sign-portals", 5, contentY, width - 10, contentHeight);
        signPortalsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);
        if (tabMethodsAvailable) {
            tabsContainer = createContainer("tabs", 5, contentY, width - 10, contentHeight);
            tabsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);
        }

        tabsManager.addTab("Blueprints", blueprintsContainer);
        tabsManager.addTab("GUIs", guisContainer);
        tabsManager.addTab("Scoreboards", scoreboardsContainer);
        tabsManager.addTab("Worlds", worldsContainer);
        tabsManager.addTab("Portals", portalsContainer);
        tabsManager.addTab("Groups", inventoryGroupsContainer);
        tabsManager.addTab("Signs", signPortalsContainer);
        if (tabMethodsAvailable && tabsContainer != null) {
            tabsManager.addTab("Tabs", tabsContainer);
        }

        rebuildBlueprints();
        rebuildGuis();
        rebuildScoreboards();
        rebuildWorlds();
        rebuildPortals();
        rebuildInventoryGroups();
        rebuildSignPortals();
        if (tabMethodsAvailable && tabsContainer != null) {
            rebuildTabs();
        }

        tabsManager.setActiveTab(0);
    }

    private void onTabSelected(TabsManager.Tab tab) {
        if (tab.getContainer() == blueprintsContainer) {
            rebuildBlueprints();
        } else if (tab.getContainer() == guisContainer) {
            rebuildGuis();
        } else if (tab.getContainer() == scoreboardsContainer) {
            rebuildScoreboards();
        } else if (tab.getContainer() == worldsContainer) {
            rebuildWorlds();
        } else if (tab.getContainer() == portalsContainer) {
            rebuildPortals();
        } else if (tab.getContainer() == inventoryGroupsContainer) {
            rebuildInventoryGroups();
        } else if (tab.getContainer() == signPortalsContainer) {
            rebuildSignPortals();
        } else if (tabMethodsAvailable && tabsContainer != null && tab.getContainer() == tabsContainer) {
            rebuildTabs();
        }
    }

    private static boolean hasTabMethods(FlowManager manager) {
        if (manager == null) {
            return false;
        }
        try {
            Class<?> type = manager.getClass();
            type.getMethod("getTabsForServer", String.class);
            type.getMethod("getTabName", String.class, String.class);
            type.getMethod("openTabDesigner", String.class, ClientServerView.class, String.class);
            type.getMethod("createTab", String.class, String.class);
            type.getMethod("deleteTab", String.class, String.class);
            type.getMethod("renameTab", String.class, String.class, String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private void rebuildBlueprints() {
        blueprintsContainer.clearWidgets();
        blueprintEntries.clear();

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
            upsertFlowEntry(flowId);
        }
    }

    public void upsertFlowEntry(String flowId) {
        if (blueprintsContainer == null || flowId == null) {
            return;
        }
        FlowGraph graph = flowManager.getFlowsForServer(serverId).get(flowId);
        if (graph == null) {
            return;
        }
        MountableButtonWidget existing = blueprintEntries.remove(flowId);
        if (existing != null) {
            blueprintsContainer.removeWidget(existing);
        }

        String displayName = flowManager.getFlowName(serverId, flowId);
        if (displayName == null || displayName.isBlank()) {
            displayName = flowId;
        }
        int nodeCount = graph.getNodes() != null ? graph.getNodes().size() : 0;
        int connectionCount = graph.getConnections() != null ? graph.getConnections().size() : 0;
        int variableCount = graph.getLocalVariables() != null ? graph.getLocalVariables().size() : 0;
        String description = "Nodes: " + nodeCount + " | Links: " + connectionCount + " | Vars: " + variableCount;

        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
            .imagePath("edit.png")
            .onClick(() -> showRenameFlowPopup(flowId))
            .build();

        SquareButtonWidget commandButton = new SquareButtonWidget.Builder()
            .imagePath("terminal.png")
            .onClick(() -> showCommandBindingPopup(flowId))
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
            .addButton(commandButton)
            .addButton(deleteButton)
            .build();
        widget.setSize(Math.max(200, blueprintsContainer.getWidth() - 20), 28);

        List<String> sortedIds = new ArrayList<>(blueprintEntries.keySet());
        sortedIds.add(flowId);
        sortedIds.sort(Comparator.naturalOrder());
        int insertIndex = 1 + sortedIds.indexOf(flowId);
        if (blueprintEntries.isEmpty() || insertIndex >= blueprintsContainer.getWidgets().size()) {
            blueprintsContainer.addWidget(widget);
        } else {
            blueprintsContainer.insertWidget(widget, insertIndex);
        }
        blueprintEntries.put(flowId, widget);
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

    private void showCommandBindingPopup(String flowId) {
        TriggerBinding existing = flowManager.getCommandBinding(serverId, flowId);
        CommandBindingContext initial = parseCommandContext(existing != null ? existing.getContext() : null);
        showCommandBindingPopup(flowId, initial);
    }

    private void showCommandBindingPopup(String flowId, CommandBindingContext initial) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Flow Command").setResizable(false);

        ToggleWidget structuredToggle = new ToggleWidget.Builder()
            .label("Structured")
            .size(110, 18)
            .toggled(initial.structured != null && initial.structured)
            .build();

        TextInputWidget commandInput = new TextInputWidget.Builder()
            .text(initial.command != null ? initial.command : "")
            .placeholder("Command label")
            .size(128, 18)
            .build();
        RowWidget commandRow = new RowWidget.Builder()
            .size(220, 18)
            .addWidget(commandInput)
            .addWidget(structuredToggle)
            .build();
        builder.addRow("Command", true, 18, commandRow);

        List<TextInputWidget> pathInputs = new ArrayList<>();
        List<String> existingPaths = new ArrayList<>(initial.subcommands != null ? initial.subcommands : List.of());
        if (existingPaths.isEmpty()) {
            existingPaths.add("");
        }
        PopupWidget[] popupRef = new PopupWidget[1];

        for (int i = 0; i < existingPaths.size(); i++) {
            int index = i;
            String value = existingPaths.get(i);
            String[] pathExamples = {
                "'pvp duel <online_player>'",
                "'report hacker <offline_player>'",
                "'database getPlayers <player_with_perm:my.permission.node>'",
                "'trade <online_player>'"
            };
            TextInputWidget pathInput = new TextInputWidget.Builder()
                .text(value)
                .placeholder(pathExamples[i % pathExamples.length])
                .size(150, 18)
                .build();
            pathInputs.add(pathInput);
            SquareButtonWidget upButton = new SquareButtonWidget.Builder()
                .imagePath("goforward.png").rotate(-90)
                .onClick(() -> {
                    List<String> draftPaths = collectCommandPathDraft(pathInputs);
                    if (index > 0 && index < draftPaths.size()) {
                        String temp = draftPaths.get(index - 1);
                        draftPaths.set(index - 1, draftPaths.get(index));
                        draftPaths.set(index, temp);
                    }
                    CommandBindingContext draft = new CommandBindingContext();
                    draft.command = commandInput.getText();
                    draft.subcommands = draftPaths;
                    draft.structured = structuredToggle.getValue();
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    showCommandBindingPopup(flowId, draft);
                })
                .build();
            SquareButtonWidget downButton = new SquareButtonWidget.Builder()
                .imagePath("goforward.png").rotate(90)
                .onClick(() -> {
                    List<String> draftPaths = collectCommandPathDraft(pathInputs);
                    if (index + 1 < draftPaths.size()) {
                        String temp = draftPaths.get(index + 1);
                        draftPaths.set(index + 1, draftPaths.get(index));
                        draftPaths.set(index, temp);
                    }
                    CommandBindingContext draft = new CommandBindingContext();
                    draft.command = commandInput.getText();
                    draft.subcommands = draftPaths;
                    draft.structured = structuredToggle.getValue();
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    showCommandBindingPopup(flowId, draft);
                })
                .build();
            SquareButtonWidget removeButton = new SquareButtonWidget.Builder()
                .imagePath("delete.png")
                .accentType(ThemeManager.getAccent("danger"))
                .onClick(() -> {
                    List<String> draftPaths = collectCommandPathDraft(pathInputs);
                    if (index < draftPaths.size()) {
                        draftPaths.remove(index);
                    }
                    if (draftPaths.isEmpty()) {
                        draftPaths.add("");
                    }
                    CommandBindingContext draft = new CommandBindingContext();
                    draft.command = commandInput.getText();
                    draft.subcommands = draftPaths;
                    draft.structured = structuredToggle.getValue();
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    showCommandBindingPopup(flowId, draft);
                })
                .build();
            RowWidget.Builder pathRowBuilder = new RowWidget.Builder()
                .size(220, 18)
                .addWidget(pathInput);
            if (i > 0) {
                pathRowBuilder.addWidget(upButton);
            }
            if (i < existingPaths.size() - 1) {
                pathRowBuilder.addWidget(downButton);
            }
            if (existingPaths.size() > 1) {
                pathRowBuilder.addWidget(removeButton);
            }
            RowWidget pathRow = pathRowBuilder.build();
            builder.addRow("Path " + (i + 1), true, 18, pathRow);
        }

        AnimatedButton addPathButton = new AnimatedButton.Builder()
            .label("Add New Path")
            .onClick(() -> {
                CommandBindingContext draft = new CommandBindingContext();
                draft.command = commandInput.getText();
                draft.subcommands = collectCommandPathDraft(pathInputs);
                draft.subcommands.add("");
                draft.structured = structuredToggle.getValue();
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                showCommandBindingPopup(flowId, draft);
            })
            .build();
        builder.addRow("", true, 18, addPathButton);

        AnimatedButton saveButton = new AnimatedButton.Builder()
            .label("Save")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String command = commandInput.getText() != null ? commandInput.getText().trim().toLowerCase(Locale.ROOT) : "";
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                if (command.isEmpty() || !command.matches("^[a-zA-Z0-9:_-]+$")) {
                    new Notification("Error", "Invalid command label", Notification.Type.ERROR);
                    return;
                }
                List<String> subcommands = collectCommandPaths(pathInputs);
                boolean structured = structuredToggle.getValue();
                CommandBindingContext context = new CommandBindingContext();
                context.command = command;
                context.subcommands = subcommands;
                context.structured = structured;
                String encodedContext = subcommands.isEmpty() && !structured ? command : gson.toJson(context);
                flowManager.setCommandBinding(serverId, flowId, encodedContext);
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                String usage = "/" + command + (subcommands.isEmpty() ? "" : " " + String.join("|", subcommands));
                new Notification("Saved", usage, Notification.Type.SUCCESS);
            })
            .build();

        AnimatedButton clearButton = new AnimatedButton.Builder()
            .label("Clear")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                flowManager.clearCommandBinding(serverId, flowId);
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                new Notification("Cleared", "Flow Command", Notification.Type.INFO);
            })
            .build();

        builder.addRow("", true, 18, saveButton);
        builder.addRow("", true, 18, clearButton);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private List<String> collectCommandPathDraft(List<TextInputWidget> pathInputs) {
        List<String> paths = new ArrayList<>();
        for (TextInputWidget input : pathInputs) {
            paths.add(input != null && input.getText() != null ? input.getText().trim() : "");
        }
        return paths;
    }

    private List<String> collectCommandPaths(List<TextInputWidget> pathInputs) {
        List<String> paths = new ArrayList<>();
        for (TextInputWidget input : pathInputs) {
            if (input == null || input.getText() == null) {
                continue;
            }
            String value = input.getText().trim();
            if (!value.isEmpty()) {
                paths.add(value);
            }
        }
        return paths;
    }

    private CommandBindingContext parseCommandContext(String context) {
        CommandBindingContext parsed = new CommandBindingContext();
        parsed.subcommands = new ArrayList<>();
        parsed.structured = false;
        if (context == null || context.isBlank()) {
            return parsed;
        }
        String trimmed = context.trim();
        if (trimmed.startsWith("{")) {
            try {
                CommandBindingContext decoded = gson.fromJson(trimmed, CommandBindingContext.class);
                if (decoded != null) {
                    parsed.command = decoded.command;
                    parsed.subcommands = decoded.subcommands != null ? decoded.subcommands : new ArrayList<>();
                    parsed.structured = decoded.structured != null && decoded.structured;
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        String normalized = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        parsed.command = normalized.toLowerCase(Locale.ROOT);
        return parsed;
    }

    private void rebuildGuis() {
        guisContainer.clearWidgets();
        guiEntries.clear();

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
            upsertGuiEntry(guiId);
        }
    }

    public void upsertGuiEntry(String guiId) {
        if (guisContainer == null || guiId == null) {
            return;
        }
        GuiDefinition gui = flowManager.getGuisForServer(serverId).get(guiId);
        if (gui == null) {
            return;
        }
        MountableButtonWidget existing = guiEntries.remove(guiId);
        if (existing != null) {
            guisContainer.removeWidget(existing);
        }

        String displayName = flowManager.getGuiName(serverId, guiId);
        if ((displayName == null || displayName.isBlank()) && gui.getTitle() != null) {
            displayName = gui.getTitle();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = guiId;
        }
        int elementCount = gui.getElements() != null ? gui.getElements().size() : 0;
        int rows = Math.max(gui.getRows(), 0);
        String description = "Rows: " + rows + " | Elements: " + elementCount;

        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
            .imagePath("edit.png")
            .onClick(() -> showRenameGuiPopup(guiId))
            .build();

        SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
            .imagePath("delete.png")
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

        List<String> sortedIds = new ArrayList<>(guiEntries.keySet());
        sortedIds.add(guiId);
        sortedIds.sort(Comparator.naturalOrder());
        int insertIndex = 1 + sortedIds.indexOf(guiId);
        if (guiEntries.isEmpty() || insertIndex >= guisContainer.getWidgets().size()) {
            guisContainer.addWidget(widget);
        } else {
            guisContainer.insertWidget(widget, insertIndex);
        }
        guiEntries.put(guiId, widget);
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

    private void rebuildScoreboards() {
        scoreboardsContainer.clearWidgets();
        scoreboardEntries.clear();

        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("New Scoreboard")
            .size(140, 22)
            .onClick(this::showCreateScoreboardPopup)
            .build();
        scoreboardsContainer.addWidget(createButton);

        Map<String, ScoreboardDefinition> scoreboards = flowManager.getScoreboardsForServer(serverId);
        List<String> scoreboardIds = new ArrayList<>(scoreboards.keySet());
        scoreboardIds.sort(Comparator.naturalOrder());

        for (String scoreboardId : scoreboardIds) {
            upsertScoreboardEntry(scoreboardId);
        }
    }

    public void upsertScoreboardEntry(String scoreboardId) {
        if (scoreboardsContainer == null || scoreboardId == null) {
            return;
        }
        ScoreboardDefinition scoreboard = flowManager.getScoreboardsForServer(serverId).get(scoreboardId);
        if (scoreboard == null) {
            return;
        }
        MountableButtonWidget existing = scoreboardEntries.remove(scoreboardId);
        if (existing != null) {
            scoreboardsContainer.removeWidget(existing);
        }

        String displayName = flowManager.getScoreboardName(serverId, scoreboardId);
        if ((displayName == null || displayName.isBlank()) && scoreboard.getTitle() != null) {
            displayName = scoreboard.getTitle();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = scoreboardId;
        }
        int lineCount = scoreboard.getLines() != null ? scoreboard.getLines().size() : 0;
        String slot = scoreboard.getDisplaySlot() != null ? scoreboard.getDisplaySlot() : "sidebar";
        String description = "Slot: " + slot + " | Lines: " + lineCount;

        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
            .imagePath("edit.png")
            .onClick(() -> showRenameScoreboardPopup(scoreboardId))
            .build();

        SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
            .imagePath("delete.png")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                flowManager.deleteScoreboard(serverId, scoreboardId);
                rebuildScoreboards();
            })
            .build();

        MountableButtonWidget widget = new MountableButtonWidget.Builder(displayName)
            .description(description)
            .hiddenText("ID: " + scoreboardId)
            .onClick(() -> flowManager.openScoreboardDesigner(serverId, server, scoreboardId))
            .addButton(editButton)
            .addButton(deleteButton)
            .build();
        widget.setSize(Math.max(200, scoreboardsContainer.getWidth() - 20), 28);

        List<String> sortedIds = new ArrayList<>(scoreboardEntries.keySet());
        sortedIds.add(scoreboardId);
        sortedIds.sort(Comparator.naturalOrder());
        int insertIndex = 1 + sortedIds.indexOf(scoreboardId);
        if (scoreboardEntries.isEmpty() || insertIndex >= scoreboardsContainer.getWidgets().size()) {
            scoreboardsContainer.addWidget(widget);
        } else {
            scoreboardsContainer.insertWidget(widget, insertIndex);
        }
        scoreboardEntries.put(scoreboardId, widget);
    }

    private void showCreateScoreboardPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create New Scoreboard").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .placeholder("Scoreboard ID (e.g. main_sidebar)")
            .size(220, 22)
            .build();

        builder.addRow("ID", true, 22, idInput);

        PopupWidget[] popupRef = new PopupWidget[1];

        AnimatedButton createBtn = new AnimatedButton.Builder()
            .label("Create")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String id = idInput.getText();
                if (id != null && id.matches("^[a-zA-Z0-9_]+$")) {
                    if (flowManager.getScoreboardsForServer(serverId).containsKey(id)) {
                        new Notification("Error", "Scoreboard ID already exists", Notification.Type.ERROR);
                        return;
                    }
                    ScoreboardDefinition scoreboard = flowManager.createScoreboard(serverId, id);
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    flowManager.openScoreboardDesigner(serverId, server, scoreboard.getId());
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

    private void showRenameScoreboardPopup(String scoreboardId) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Rename Scoreboard").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .text(scoreboardId)
            .placeholder("Scoreboard ID")
            .size(220, 22)
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
                if (newId.equals(scoreboardId)) {
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    return;
                }
                if (flowManager.getScoreboardsForServer(serverId).containsKey(newId)) {
                    new Notification("Error", "Scoreboard ID already exists", Notification.Type.ERROR);
                    return;
                }
                if (!flowManager.renameScoreboard(serverId, scoreboardId, newId)) {
                    new Notification("Error", "Unable to rename scoreboard.", Notification.Type.ERROR);
                    return;
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                rebuildScoreboards();
            })
            .build();

        builder.addRow("", true, 20, saveBtn);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void rebuildTabs() {
        tabsContainer.clearWidgets();
        tabEntries.clear();

        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("New Tab")
            .size(120, 22)
            .onClick(this::showCreateTabPopup)
            .build();
        tabsContainer.addWidget(createButton);

        Map<String, TabDefinition> tabs = flowManager.getTabsForServer(serverId);
        List<String> tabIds = new ArrayList<>(tabs.keySet());
        tabIds.sort(Comparator.naturalOrder());

        for (String tabId : tabIds) {
            upsertTabEntry(tabId);
        }
    }

    public void upsertTabEntry(String tabId) {
        if (tabsContainer == null || tabId == null) {
            return;
        }
        TabDefinition tab = flowManager.getTabsForServer(serverId).get(tabId);
        if (tab == null) {
            return;
        }
        MountableButtonWidget existing = tabEntries.remove(tabId);
        if (existing != null) {
            tabsContainer.removeWidget(existing);
        }

        String displayName = flowManager.getTabName(serverId, tabId);
        if (displayName == null || displayName.isBlank()) {
            displayName = tabId;
        }
        String description = "Header/Footer + Entries";

        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
            .imagePath("edit.png")
            .onClick(() -> showRenameTabPopup(tabId))
            .build();

        SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
            .imagePath("delete.png")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                flowManager.deleteTab(serverId, tabId);
                rebuildTabs();
            })
            .build();

        MountableButtonWidget widget = new MountableButtonWidget.Builder(displayName)
            .description(description)
            .hiddenText("ID: " + tabId)
            .onClick(() -> flowManager.openTabDesigner(serverId, server, tabId))
            .addButton(editButton)
            .addButton(deleteButton)
            .build();
        widget.setSize(Math.max(200, tabsContainer.getWidth() - 20), 28);

        List<String> sortedIds = new ArrayList<>(tabEntries.keySet());
        sortedIds.add(tabId);
        sortedIds.sort(Comparator.naturalOrder());
        int insertIndex = 1 + sortedIds.indexOf(tabId);
        if (tabEntries.isEmpty() || insertIndex >= tabsContainer.getWidgets().size()) {
            tabsContainer.addWidget(widget);
        } else {
            tabsContainer.insertWidget(widget, insertIndex);
        }
        tabEntries.put(tabId, widget);
    }

    private void showCreateTabPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create New Tab").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .placeholder("Tab ID (e.g. default_tab)")
            .size(220, 22)
            .build();

        builder.addRow("ID", true, 22, idInput);

        PopupWidget[] popupRef = new PopupWidget[1];

        AnimatedButton createBtn = new AnimatedButton.Builder()
            .label("Create")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String id = idInput.getText();
                if (id != null && id.matches("^[a-zA-Z0-9_]+$")) {
                    if (flowManager.getTabsForServer(serverId).containsKey(id)) {
                        new Notification("Error", "Tab ID already exists", Notification.Type.ERROR);
                        return;
                    }
                    TabDefinition tab = flowManager.createTab(serverId, id);
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    flowManager.openTabDesigner(serverId, server, tab.getId());
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

    private void showRenameTabPopup(String tabId) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Rename Tab").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .text(tabId)
            .placeholder("Tab ID")
            .size(220, 22)
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
                if (newId.equals(tabId)) {
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    return;
                }
                if (flowManager.getTabsForServer(serverId).containsKey(newId)) {
                    new Notification("Error", "Tab ID already exists", Notification.Type.ERROR);
                    return;
                }
                if (!flowManager.renameTab(serverId, tabId, newId)) {
                    new Notification("Error", "Unable to rename tab.", Notification.Type.ERROR);
                    return;
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
                rebuildTabs();
            })
            .build();

        builder.addRow("", true, 20, saveBtn);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void rebuildWorlds() {
        if (worldsContainer == null) {
            return;
        }
        worldsContainer.clearWidgets();
        worldEntries.clear();

        IconButton createButton = new IconButton.Builder()
            .label("New World")
            .accentType(ThemeManager.getAccent("nice"))
            .imagePath("create.png")
            .size(120, 18)
            .onClick(this::showCreateWorldPopup)
            .build();
        IconButton importButton = new IconButton.Builder()
            .label("Import")
            .imagePath("download.png")
            .size(130, 18)
            .onClick(() -> flowManager.importWorlds(serverId))
            .build();
        IconButton scanButton = new IconButton.Builder()
            .label("Scan")
            .imagePath("search.png")
            .size(120, 18)
            .onClick(() -> flowManager.scanWorlds(serverId))
            .build();
        IconButton refreshButton = new IconButton.Builder()
            .label("Refresh")
            .imagePath("reload.png")
            .size(130, 18)
            .onClick(() -> flowManager.refreshWorldsFromServer(serverId))
            .build();
        RowWidget topRow = new RowWidget.Builder()
            .size(Math.max(200, worldsContainer.getWidth() - 20), 18)
            .addWidget(createButton, importButton, scanButton, refreshButton)
            .build();
        worldsContainer.addWidget(topRow);

        List<String> worldNames = new ArrayList<>(flowManager.getWorldsForServer(serverId).keySet());
        worldNames.sort(Comparator.naturalOrder());
        for (String worldName : worldNames) {
            upsertWorldEntry(worldName);
        }
    }

    public void upsertWorldEntry(String worldName) {
        if (worldsContainer == null || worldName == null) {
            return;
        }
        WorldRegistryEntry world = flowManager.getWorld(serverId, worldName);
        if (world == null) {
            return;
        }
        MountableButtonWidget existing = worldEntries.remove(worldName);
        if (existing != null) {
            worldsContainer.removeWidget(existing);
        }

        WorldDashboardEntry dashboard = findWorldDashboard(worldName);
        String status = dashboard != null ? dashboard.getStatus() : world.isLoaded() ? "Loaded" : "Unloaded";
        int players = dashboard != null ? dashboard.getPlayerCount() : 0;
        String environment = dashboard != null && dashboard.getEnvironment() != null ? dashboard.getEnvironment() : world.getEnvironment();
        String difficulty = dashboard != null && dashboard.getDifficulty() != null ? dashboard.getDifficulty() : world.getDifficulty();
        String alias = dashboard != null ? safeText(dashboard.getAlias()) : safeText(world.getProfileSettings().getAlias());
        boolean hidden = dashboard != null && dashboard.isHidden();
        boolean forceGameMode = dashboard != null && dashboard.isForceGameMode();
        String gameMode = dashboard != null ? safeText(dashboard.getGameMode()) : safeText(world.getProfileSettings().getGameMode());
        boolean entryFeeEnabled = dashboard != null && dashboard.isEntryFeeEnabled();
        double entryFee = dashboard != null ? dashboard.getEntryFee() : world.getProfileSettings().getEntryFee();
        int portalCount = flowManager.getWorldPortals(serverId, worldName).size();
        String description = status + " | " + environment + " | Players: " + players + " | " + difficulty + " | Portals: " + portalCount;
        if (!alias.isBlank()) {
            description += " | Alias: " + alias;
        }
        StringBuilder hiddenText = new StringBuilder("Generator: " + safeText(world.getGenerator()));
        if (hidden) {
            hiddenText.append(" | Hidden");
        }
        if (forceGameMode && !gameMode.isBlank()) {
            hiddenText.append(" | Game Mode: ").append(gameMode);
        }
        if (entryFeeEnabled) {
            hiddenText.append(" | Entry Fee: ").append(entryFee);
        }
        if (!world.getProfileSettings().isPvpEnabled()) {
            hiddenText.append(" | PVP Off");
        }
        if (!world.getProfileSettings().isAutoSaveEnabled()) {
            hiddenText.append(" | Auto Save Off");
        }
        if (world.getProfileSettings().isKeepSpawnLoaded()) {
            hiddenText.append(" | Keep Spawn Loaded");
        }
        if (!safeText(world.getProfileSettings().getInventoryGroupId()).isBlank()) {
            hiddenText.append(" | Group: ").append(world.getProfileSettings().getInventoryGroupId());
        }
        if (!safeText(world.getProfileSettings().getLinkedNetherWorld()).isBlank()) {
            hiddenText.append(" | Nether: ").append(world.getProfileSettings().getLinkedNetherWorld());
        }
        if (!safeText(world.getProfileSettings().getLinkedEndWorld()).isBlank()) {
            hiddenText.append(" | End: ").append(world.getProfileSettings().getLinkedEndWorld());
        }
        if (!safeText(world.getProfileSettings().getLinkedOverworld()).isBlank()) {
            hiddenText.append(" | Overworld: ").append(world.getProfileSettings().getLinkedOverworld());
        }
        if (Double.compare(world.getProfileSettings().getNetherScale(), 8.0) != 0) {
            hiddenText.append(" | Nether Scale: ").append(formatDecimal(world.getProfileSettings().getNetherScale()));
        }
        if (Double.compare(world.getProfileSettings().getEndScale(), 1.0) != 0) {
            hiddenText.append(" | End Scale: ").append(formatDecimal(world.getProfileSettings().getEndScale()));
        }
        if (!world.getProfileSettings().isAutoLinkNetherPortal()) {
            hiddenText.append(" | Nether Auto Link Off");
        }
        if (!world.getProfileSettings().isAutoLinkEndPortal()) {
            hiddenText.append(" | End Auto Link Off");
        }
        if (!world.getProfileSettings().isNonLivingEntitySpawnsEnabled()) {
            hiddenText.append(" | Misc Spawns Off");
        }

        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
            .imagePath("edit.png").hint("Edit World")
            .onClick(() -> showWorldSettingsPopup(worldName))
            .build();
        SquareButtonWidget stateButton = new SquareButtonWidget.Builder()
            .imagePath(world.isLoaded() ? "hide.png" : "add.png")
            .hint(world.isLoaded() ? "Unload World" : "Load World")
            .onClick(() -> {
                if (world.isLoaded()) {
                    showWorldUnloadPopup(worldName);
                } else {
                    flowManager.loadWorld(serverId, worldName);
                }
            })
            .build();
        final SquareButtonWidget[] actionsButtonRef = new SquareButtonWidget[1];
        actionsButtonRef[0] = new SquareButtonWidget.Builder()
            .imagePath("apps.png").hint("More Actions")
            .onClick(() -> showWorldActionsMenu(worldName, actionsButtonRef[0]))
            .build();

        MountableButtonWidget widget = new MountableButtonWidget.Builder(worldName)
            .description(description)
            .hiddenText(hiddenText.toString())
            .onClick(() -> flowManager.openWorldMap(serverId, server, worldName))
            .addButton(editButton)
            .addButton(stateButton)
            .addButton(actionsButtonRef[0])
            .build();
        widget.setSize(Math.max(200, worldsContainer.getWidth() - 20), 26);

        List<String> sortedIds = new ArrayList<>(worldEntries.keySet());
        sortedIds.add(worldName);
        sortedIds.sort(Comparator.naturalOrder());
        int insertIndex = 1 + sortedIds.indexOf(worldName);
        if (worldEntries.isEmpty() || insertIndex >= worldsContainer.getWidgets().size()) {
            worldsContainer.addWidget(widget);
        } else {
            worldsContainer.insertWidget(widget, insertIndex);
        }
        worldEntries.put(worldName, widget);
    }

    private void showWorldActionsMenu(String worldName, SquareButtonWidget anchor) {
        if (anchor == null || worldName == null || worldName.isBlank()) {
            return;
        }
        WorldRegistryEntry world = flowManager.getWorld(serverId, worldName);
        if (world == null) {
            return;
        }
        ContextMenuWidget.Builder builder = new ContextMenuWidget.Builder(this)
            .addHeaderButton("map.png", () -> flowManager.openWorldMap(serverId, server, worldName), "Open World Map")
            .addHeaderButton("steve.png", () -> showWorldTeleportPopup(worldName), "Teleport Player")
            .addHeaderButton("search.png", () -> flowManager.whoWorld(serverId, worldName), "View Players")
            .addIconItem("Clone World", "copy.png", () -> showCloneWorldPopup(worldName), "")
            .addIconItem("Purge Entities", "delete.png", () -> showWorldPurgePopup(worldName), "", ThemeManager.getAccent("calm"))
            .addIconItem("Delete World", "delete.png", () -> showWorldDeletePopup(worldName), "", ThemeManager.getAccent("danger"));
        showContextMenu(anchor.getX() + anchor.getWidth() + 4, anchor.getY() + anchor.getHeight(), builder);
    }

    private void rebuildPortals() {
        if (portalsContainer == null) {
            return;
        }
        portalsContainer.clearWidgets();
        portalEntries.clear();

        IconButton createButton = new IconButton.Builder()
            .label("New Portal")
            .accentType(ThemeManager.getAccent("nice"))
            .imagePath("newFile.png")
            .size(130, 18)
            .onClick(this::showCreatePortalPopup)
            .build();
        IconButton refreshButton = new IconButton.Builder()
            .label("Refresh")
            .imagePath("reload.png")
            .size(120, 18)
            .onClick(() -> flowManager.refreshWorldsFromServer(serverId))
            .build();
        RowWidget topRow = new RowWidget.Builder()
            .size(Math.max(200, portalsContainer.getWidth() - 20), 18)
            .addWidget(createButton, refreshButton)
            .build();
        portalsContainer.addWidget(topRow);

        List<WorldPortal> portals = new ArrayList<>(flowManager.getWorldPortalsForServer(serverId));
        portals.sort(Comparator
            .comparing((WorldPortal portal) -> safeText(portal.getSourceWorld()), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(portal -> safeText(portal.getPortalName()).isBlank() ? safeText(portal.getPortalId()) : safeText(portal.getPortalName()), String.CASE_INSENSITIVE_ORDER));
        for (WorldPortal portal : portals) {
            if (portal == null) {
                continue;
            }
            upsertPortalEntry(portalKey(portal));
        }
    }

    private void upsertPortalEntry(String portalId) {
        if (portalsContainer == null || portalId == null || portalId.isBlank()) {
            return;
        }
        WorldPortal portal = flowManager.getWorldPortal(serverId, portalId);
        if (portal == null) {
            return;
        }
        MountableButtonWidget existing = portalEntries.remove(portalKey(portal));
        if (existing != null) {
            portalsContainer.removeWidget(existing);
        }

        String displayName = safeText(portal.getPortalName()).isBlank() ? safeText(portal.getPortalId()) : safeText(portal.getPortalName());
        String description = safeText(portal.getSourceWorld()) + " -> " + safeText(portal.getDestinationWorld())
            + " | " + (portal.isEnabled() ? "Enabled" : "Disabled")
            + " | Priority: " + portal.getPriority();
        StringBuilder hiddenText = new StringBuilder();
        hiddenText.append("Bounds: ")
            .append(formatDecimal(portal.getMinX())).append(',').append(formatDecimal(portal.getMinY())).append(',').append(formatDecimal(portal.getMinZ()))
            .append(" -> ")
            .append(formatDecimal(portal.getMaxX())).append(',').append(formatDecimal(portal.getMaxY())).append(',').append(formatDecimal(portal.getMaxZ()));
        hiddenText.append(" | Dest: ")
            .append(formatDecimal(portal.getDestinationX())).append(',').append(formatDecimal(portal.getDestinationY())).append(',').append(formatDecimal(portal.getDestinationZ()));
        hiddenText.append(" | Cooldown: ").append(portal.getCooldownMillis()).append(" ms");
        if (portal.isUsageFeeEnabled()) {
            hiddenText.append(" | Usage Fee: ").append(portal.getUsageFee());
        }
        if (!safeText(portal.getAccessPermission()).isBlank()) {
            hiddenText.append(" | Access: ").append(portal.getAccessPermission());
        }
        if (!safeText(portal.getBypassPermission()).isBlank()) {
            hiddenText.append(" | Bypass: ").append(portal.getBypassPermission());
        }
        if (portal.isSafeTeleport()) {
            hiddenText.append(" | Safe Teleport");
        }
        if (portal.isPreserveVelocity()) {
            hiddenText.append(" | Keep Velocity");
        }
        if (!portal.isVehiclePassthroughEnabled()) {
            hiddenText.append(" | Vehicle Pass Off");
        }
        if (!portal.isEntityPassthroughEnabled()) {
            hiddenText.append(" | Entity Pass Off");
        }
        if (!"WORLD".equalsIgnoreCase(portal.getDestinationMode())) {
            hiddenText.append(" | Mode: ").append(portal.getDestinationMode());
        }
        if ("CANNON".equalsIgnoreCase(portal.getDestinationMode()) || Double.compare(portal.getCannonPower(), 1.8) != 0) {
            hiddenText.append(" | Cannon Power: ").append(formatDecimal(portal.getCannonPower()));
        }

        String portalLookup = portalKey(portal);
        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
            .imagePath("edit.png").hint("Edit Portal")
            .onClick(() -> showEditPortalPopup(portalLookup))
            .build();

        SquareButtonWidget toggleButton = new SquareButtonWidget.Builder()
            .imagePath(portal.isEnabled() ? "hide.png" : "add.png").hint(portal.isEnabled() ? "Disable Portal" : "Enable Portal")
            .onClick(() -> flowManager.setPortalEnabled(serverId, portalLookup, !portal.isEnabled()))
            .build();

        SquareButtonWidget teleportButton = new SquareButtonWidget.Builder()
            .imagePath("steve.png").hint("Teleport Player")
            .onClick(() -> showPortalTeleportPopup(portalLookup))
            .build();

        SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
            .imagePath("delete.png").hint("Delete Portal")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> showPortalDeletePopup(portalLookup))
            .build();

        MountableButtonWidget widget = new MountableButtonWidget.Builder(displayName)
            .description(description)
            .hiddenText(hiddenText.toString())
            .onClick(() -> showEditPortalPopup(portalLookup))
            .addButton(editButton)
            .addButton(toggleButton)
            .addButton(teleportButton)
            .addButton(deleteButton)
            .build();
        widget.setSize(Math.max(200, portalsContainer.getWidth() - 20), 28);

        List<String> sortedIds = new ArrayList<>(portalEntries.keySet());
        sortedIds.add(portalLookup);
        sortedIds.sort((left, right) -> {
            WorldPortal leftPortal = flowManager.getWorldPortal(serverId, left);
            WorldPortal rightPortal = flowManager.getWorldPortal(serverId, right);
            String leftWorld = leftPortal == null ? "" : safeText(leftPortal.getSourceWorld());
            String rightWorld = rightPortal == null ? "" : safeText(rightPortal.getSourceWorld());
            int worldCompare = String.CASE_INSENSITIVE_ORDER.compare(leftWorld, rightWorld);
            if (worldCompare != 0) {
                return worldCompare;
            }
            String leftName = leftPortal == null ? left : (safeText(leftPortal.getPortalName()).isBlank() ? safeText(leftPortal.getPortalId()) : safeText(leftPortal.getPortalName()));
            String rightName = rightPortal == null ? right : (safeText(rightPortal.getPortalName()).isBlank() ? safeText(rightPortal.getPortalId()) : safeText(rightPortal.getPortalName()));
            return String.CASE_INSENSITIVE_ORDER.compare(leftName, rightName);
        });
        int insertIndex = 1 + sortedIds.indexOf(portalLookup);
        if (portalEntries.isEmpty() || insertIndex >= portalsContainer.getWidgets().size()) {
            portalsContainer.addWidget(widget);
        } else {
            portalsContainer.insertWidget(widget, insertIndex);
        }
        portalEntries.put(portalLookup, widget);
    }

    private void showCreatePortalPopup() {
        showPortalEditorPopup(null);
    }

    private void showEditPortalPopup(String portalId) {
        showPortalEditorPopup(flowManager.getWorldPortal(serverId, portalId));
    }

    private void showPortalEditorPopup(WorldPortal existingPortal) {
        boolean editing = existingPortal != null;
        List<String> worldOptions = worldNameOptions();
        PopupWidget.Builder builder = new PopupWidget.Builder(editing ? "Edit Portal" : "Create Portal")
            .setResizable(true)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(560, 500);

        TextInputWidget portalNameInput = new TextInputWidget.Builder()
            .text(editing ? safeText(existingPortal.getPortalName()) : "")
            .placeholder("Portal Name")
            .size(220, 20)
            .build();
        SelectorWidgets sourceWorldSelector = createSelectorWidgets("Source World", worldOptions,
            editing ? safeText(existingPortal.getSourceWorld()) : "", 180, false, Function.identity());
        SelectorWidgets destinationWorldSelector = createSelectorWidgets("Destination World", worldOptions,
            editing ? safeText(existingPortal.getDestinationWorld()) : "", 180, false, Function.identity());
        ToggleWidget enabledToggle = new ToggleWidget.Builder()
            .label("Enabled")
            .toggled(!editing || existingPortal.isEnabled())
            .size(85, 18)
            .build();
        TextInputWidget minXInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getMinX()) : "0").placeholder("Min X").size(78, 20).build();
        TextInputWidget minYInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getMinY()) : "64").placeholder("Min Y").size(78, 20).build();
        TextInputWidget minZInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getMinZ()) : "0").placeholder("Min Z").size(78, 20).build();
        TextInputWidget maxXInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getMaxX()) : "1").placeholder("Max X").size(78, 20).build();
        TextInputWidget maxYInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getMaxY()) : "66").placeholder("Max Y").size(78, 20).build();
        TextInputWidget maxZInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getMaxZ()) : "1").placeholder("Max Z").size(78, 20).build();
        TextInputWidget destinationXInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getDestinationX()) : "0").placeholder("Target X").size(78, 20).build();
        TextInputWidget destinationYInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getDestinationY()) : "64").placeholder("Target Y").size(78, 20).build();
        TextInputWidget destinationZInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getDestinationZ()) : "0").placeholder("Target Z").size(78, 20).build();
        TextInputWidget destinationYawInput = new TextInputWidget.Builder().text(editing ? String.valueOf(existingPortal.getDestinationYaw()) : "0").placeholder("Yaw").size(78, 20).build();
        TextInputWidget destinationPitchInput = new TextInputWidget.Builder().text(editing ? String.valueOf(existingPortal.getDestinationPitch()) : "0").placeholder("Pitch").size(78, 20).build();
        TextInputWidget accessPermissionInput = new TextInputWidget.Builder().text(editing ? safeText(existingPortal.getAccessPermission()) : "").placeholder("Access Permission").size(220, 20).build();
        TextInputWidget bypassPermissionInput = new TextInputWidget.Builder().text(editing ? safeText(existingPortal.getBypassPermission()) : "").placeholder("Bypass Permission").size(220, 20).build();
        ToggleWidget usageFeeToggle = new ToggleWidget.Builder().label("Usage Fee").toggled(editing && existingPortal.isUsageFeeEnabled()).size(95, 18).build();
        TextInputWidget usageFeeInput = new TextInputWidget.Builder().text(editing ? String.valueOf(existingPortal.getUsageFee()) : "0").placeholder("Fee").size(120, 20).build();
        TextInputWidget cooldownInput = new TextInputWidget.Builder().text(editing ? String.valueOf(existingPortal.getCooldownMillis()) : "1500").placeholder("Cooldown Ms").size(120, 20).build();
        TextInputWidget priorityInput = new TextInputWidget.Builder().text(editing ? String.valueOf(existingPortal.getPriority()) : "0").placeholder("Priority").size(120, 20).build();
        ToggleWidget safeTeleportToggle = new ToggleWidget.Builder().label("Safe Teleport").toggled(!editing || existingPortal.isSafeTeleport()).size(110, 18).build();
        ToggleWidget preserveVelocityToggle = new ToggleWidget.Builder().label("Keep Velocity").toggled(editing && existingPortal.isPreserveVelocity()).size(115, 18).build();
        ToggleWidget vehiclePassthroughToggle = new ToggleWidget.Builder().label("Vehicle Pass").toggled(!editing || existingPortal.isVehiclePassthroughEnabled()).size(118, 18).build();
        ToggleWidget entityPassthroughToggle = new ToggleWidget.Builder().label("Entity Pass").toggled(!editing || existingPortal.isEntityPassthroughEnabled()).size(110, 18).build();
        DropDownWidget<String> destinationModeSelect = new DropDownWidget.Builder<>(List.of("WORLD", "CANNON"))
            .size(150, 18)
            .selectedItem(editing ? safeText(existingPortal.getDestinationMode()).toUpperCase(Locale.ROOT) : "WORLD")
            .build();
        TextInputWidget cannonPowerInput = new TextInputWidget.Builder().text(editing ? formatDecimal(existingPortal.getCannonPower()) : "1.8").placeholder("Cannon Power").size(120, 20).build();
        TextInputWidget enterMessageInput = new TextInputWidget.Builder().text(editing ? safeText(existingPortal.getEnterMessage()) : "").placeholder("Enter Message").size(260, 20).build();

        if (editing) {
            IconButton portalIdButton = new IconButton.Builder().label(safeText(existingPortal.getPortalId())).autoWidthOnTextChange(true).build();
            portalIdButton.active = false;
            builder.addRow("Portal Id", true, 18, portalIdButton);
        }
        builder.addRow("Name", true, 18, portalNameInput, enabledToggle);
        builder.addRow("Source", true, 18, sourceWorldSelector.input, sourceWorldSelector.dropdown);
        builder.addRow("Bounds Min", true, 18, minXInput, minYInput, minZInput);
        builder.addRow("Bounds Max", true, 18, maxXInput, maxYInput, maxZInput);
        builder.addRow("Target", true, 18, destinationWorldSelector.input, destinationWorldSelector.dropdown);
        builder.addRow("Target Position", true, 18, destinationXInput, destinationYInput, destinationZInput);
        builder.addRow("Target Look", true, 18, destinationYawInput, destinationPitchInput);
        builder.addRow("Access", true, 18, accessPermissionInput);
        builder.addRow("Bypass", true, 18, bypassPermissionInput);
        builder.addRow("Rules", true, 18, usageFeeToggle, usageFeeInput, cooldownInput, priorityInput);
        builder.addRow("Travel", true, 18, safeTeleportToggle, preserveVelocityToggle);
        builder.addRow("Pass", true, 18, vehiclePassthroughToggle, entityPassthroughToggle);
        builder.addRow("Mode", true, 18, destinationModeSelect, cannonPowerInput);
        builder.addRow("Message", true, 18, enterMessageInput);

        PopupWidget[] popupRef = new PopupWidget[1];
        builder.addTitleButton(() -> {
            String portalName = safeText(portalNameInput.getText()).trim();
            String sourceWorld = selectedSelectorValue(sourceWorldSelector).trim();
            String destinationWorld = selectedSelectorValue(destinationWorldSelector).trim();
            if (portalName.isBlank()) {
                new Notification("Error", "Portal Name Required", Notification.Type.ERROR);
                return;
            }
            if (sourceWorld.isBlank() || destinationWorld.isBlank()) {
                new Notification("Error", "World Name Required", Notification.Type.ERROR);
                return;
            }
            if (!WorldUiSupport.containsIgnoreCase(worldOptions, sourceWorld)) {
                new Notification("Error", "Unknown Source World", Notification.Type.ERROR);
                return;
            }
            if (!WorldUiSupport.containsIgnoreCase(worldOptions, destinationWorld)) {
                new Notification("Error", "Unknown Destination World", Notification.Type.ERROR);
                return;
            }
            Double minXValue = parseNullableDouble(minXInput.getText());
            Double minYValue = parseNullableDouble(minYInput.getText());
            Double minZValue = parseNullableDouble(minZInput.getText());
            Double maxXValue = parseNullableDouble(maxXInput.getText());
            Double maxYValue = parseNullableDouble(maxYInput.getText());
            Double maxZValue = parseNullableDouble(maxZInput.getText());
            Double destinationXValue = parseNullableDouble(destinationXInput.getText());
            Double destinationYValue = parseNullableDouble(destinationYInput.getText());
            Double destinationZValue = parseNullableDouble(destinationZInput.getText());
            Float destinationYawValue = parseNullableFloat(destinationYawInput.getText());
            Float destinationPitchValue = parseNullableFloat(destinationPitchInput.getText());
            Double usageFeeValue = parseNullableDouble(usageFeeInput.getText());
            Long cooldownValue = parseNullableLong(cooldownInput.getText());
            Integer priorityValue = parseNullableInt(priorityInput.getText());
            Double cannonPowerValue = parseNullableDouble(cannonPowerInput.getText());
            if (minXValue == null || minYValue == null || minZValue == null || maxXValue == null || maxYValue == null || maxZValue == null) {
                new Notification("Error", "Invalid Bounds", Notification.Type.ERROR);
                return;
            }
            if (!WorldUiSupport.areOrderedBounds(minXValue, minYValue, minZValue, maxXValue, maxYValue, maxZValue)) {
                new Notification("Error", "Bounds Order Invalid", Notification.Type.ERROR);
                return;
            }
            if (destinationXValue == null || destinationYValue == null || destinationZValue == null || destinationYawValue == null || destinationPitchValue == null) {
                new Notification("Error", "Invalid Target Position", Notification.Type.ERROR);
                return;
            }
            if (usageFeeValue == null) {
                new Notification("Error", "Invalid Fee", Notification.Type.ERROR);
                return;
            }
            if (cooldownValue == null) {
                new Notification("Error", "Invalid Cooldown", Notification.Type.ERROR);
                return;
            }
            if (priorityValue == null) {
                new Notification("Error", "Invalid Priority", Notification.Type.ERROR);
                return;
            }
            if (cannonPowerValue == null || cannonPowerValue <= 0.0) {
                new Notification("Error", "Invalid Cannon Power", Notification.Type.ERROR);
                return;
            }
            double minX = minXValue;
            double minY = minYValue;
            double minZ = minZValue;
            double maxX = maxXValue;
            double maxY = maxYValue;
            double maxZ = maxZValue;
            double destinationX = destinationXValue;
            double destinationY = destinationYValue;
            double destinationZ = destinationZValue;
            float destinationYaw = destinationYawValue;
            float destinationPitch = destinationPitchValue;
            String accessPermission = safeText(accessPermissionInput.getText()).trim();
            String bypassPermission = safeText(bypassPermissionInput.getText()).trim();
            boolean usageFeeEnabled = usageFeeToggle.getValue();
            double usageFee = usageFeeValue;
            long cooldownMillis = cooldownValue;
            int priority = priorityValue;
            boolean safeTeleport = safeTeleportToggle.getValue();
            boolean preserveVelocity = preserveVelocityToggle.getValue();
            boolean vehiclePassthroughEnabled = vehiclePassthroughToggle.getValue();
            boolean entityPassthroughEnabled = entityPassthroughToggle.getValue();
            String destinationMode = safeText(destinationModeSelect.getSelectedItem()).trim();
            if (destinationMode.isBlank()) {
                destinationMode = "WORLD";
            }
            double cannonPower = cannonPowerValue;
            String enterMessage = safeText(enterMessageInput.getText()).trim();
            if (editing) {
                flowManager.suppressNextWorldSuccessNotification(serverId, "resizePortal");
                flowManager.resizePortal(serverId, existingPortal.getPortalId(), portalName, sourceWorld, minX, minY, minZ, maxX, maxY, maxZ,
                    destinationWorld, destinationX, destinationY, destinationZ, destinationYaw, destinationPitch, enabledToggle.getValue(),
                    accessPermission, bypassPermission, usageFeeEnabled, usageFee, cooldownMillis, priority, safeTeleport, preserveVelocity, enterMessage,
                    vehiclePassthroughEnabled, entityPassthroughEnabled, destinationMode, cannonPower);
            } else {
                flowManager.suppressNextWorldSuccessNotification(serverId, "createPortal");
                flowManager.createPortal(serverId, portalName, sourceWorld, minX, minY, minZ, maxX, maxY, maxZ,
                    destinationWorld, destinationX, destinationY, destinationZ, destinationYaw, destinationPitch, enabledToggle.getValue(),
                    accessPermission, bypassPermission, usageFeeEnabled, usageFee, cooldownMillis, priority, safeTeleport, preserveVelocity, enterMessage,
                    vehiclePassthroughEnabled, entityPassthroughEnabled, destinationMode, cannonPower);
            }
            new Notification("ReSync", editing ? "Portal Saved" : "Portal Created", Notification.Type.SUCCESS);
            if (popupRef[0] != null) {
                popupRef[0].hide();
            }
        }, editing ? "Save" : "Create", ThemeManager.getAccent("nice"));

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showPortalTeleportPopup(String portalId) {
        WorldPortal portal = flowManager.getWorldPortal(serverId, portalId);
        if (portal == null) {
            return;
        }
        List<String> playerOptions = onlinePlayerNames();
        String portalLabel = safeText(portal.getPortalName()).isBlank() ? safeText(portal.getPortalId()) : safeText(portal.getPortalName());
        PopupWidget.Builder builder = new PopupWidget.Builder("Teleport To Portal · " + portalLabel).setResizable(false);
        SelectorWidgets playerSelector = createSelectorWidgets("Player Name", playerOptions, "", 220, true, Function.identity());
        builder.addRow("Player", true, 18, playerSelector.input, playerSelector.dropdown);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton teleportButton = new AnimatedButton.Builder()
            .label("Teleport")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String playerName = selectedSelectorValue(playerSelector).trim();
                if (playerName.isBlank()) {
                    new Notification("Error", "Player Name Required", Notification.Type.ERROR);
                    return;
                }
                flowManager.suppressNextWorldSuccessNotification(serverId, "teleportPlayerToPortal");
                flowManager.teleportPlayerToPortal(serverId, playerName, portal.getPortalId());
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, teleportButton);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showWorldTeleportPopup(String worldName) {
        WorldRegistryEntry world = flowManager.getWorld(serverId, worldName);
        if (world == null) {
            return;
        }
        List<String> playerOptions = onlinePlayerNames();
        PopupWidget.Builder builder = new PopupWidget.Builder("Teleport To World · " + worldName).setResizable(false).size(420, 205);
        SelectorWidgets playerSelector = createSelectorWidgets("Player Name", playerOptions, "", 220, true, Function.identity());
        TextInputWidget xInput = new TextInputWidget.Builder().placeholder("X").size(85, 20).build();
        TextInputWidget yInput = new TextInputWidget.Builder().placeholder("Y").size(85, 20).build();
        TextInputWidget zInput = new TextInputWidget.Builder().placeholder("Z").size(85, 20).build();
        TextInputWidget yawInput = new TextInputWidget.Builder().placeholder("Yaw").size(85, 20).build();
        TextInputWidget pitchInput = new TextInputWidget.Builder().placeholder("Pitch").size(85, 20).build();
        builder.addRow("Player", true, 18, playerSelector.input, playerSelector.dropdown);
        builder.addRow("Pos", true, 18, xInput, yInput, zInput);
        builder.addRow("Look", true, 18, yawInput, pitchInput);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton spawnButton = new AnimatedButton.Builder()
            .label("World Spawn")
            .onClick(() -> {
                String playerName = selectedSelectorValue(playerSelector).trim();
                if (playerName.isBlank()) {
                    new Notification("Error", "Player Name Required", Notification.Type.ERROR);
                    return;
                }
                flowManager.suppressNextWorldSuccessNotification(serverId, "teleportPlayerToWorldSpawn");
                flowManager.teleportPlayerToWorldSpawn(serverId, playerName, worldName);
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        AnimatedButton teleportButton = new AnimatedButton.Builder()
            .label("Teleport")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String playerName = selectedSelectorValue(playerSelector).trim();
                if (playerName.isBlank()) {
                    new Notification("Error", "Player Name Required", Notification.Type.ERROR);
                    return;
                }
                if (!WorldUiSupport.isCompleteOrBlank(xInput.getText(), yInput.getText(), zInput.getText())) {
                    new Notification("Error", "Complete Position", Notification.Type.ERROR);
                    return;
                }
                if (!WorldUiSupport.isCompleteOrBlank(yawInput.getText(), pitchInput.getText())) {
                    new Notification("Error", "Complete Rotation", Notification.Type.ERROR);
                    return;
                }
                Double x = parseNullableDouble(xInput.getText());
                Double y = parseNullableDouble(yInput.getText());
                Double z = parseNullableDouble(zInput.getText());
                Float yaw = parseNullableFloat(yawInput.getText());
                Float pitch = parseNullableFloat(pitchInput.getText());
                if (WorldUiSupport.hasAnyValue(xInput.getText(), yInput.getText(), zInput.getText()) && (x == null || y == null || z == null)) {
                    new Notification("Error", "Invalid Position", Notification.Type.ERROR);
                    return;
                }
                if (WorldUiSupport.hasAnyValue(yawInput.getText(), pitchInput.getText()) && (yaw == null || pitch == null)) {
                    new Notification("Error", "Invalid Rotation", Notification.Type.ERROR);
                    return;
                }
                flowManager.suppressNextWorldSuccessNotification(serverId, "teleportPlayerToWorld");
                flowManager.teleportPlayerToWorld(serverId, playerName, worldName, x, y, z, yaw, pitch);
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, spawnButton, teleportButton);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showWorldPurgePopup(String worldName) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Purge Entities · " + worldName)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(420, 215);

        ToggleWidget monstersToggle = new ToggleWidget.Builder().label("Monsters").toggled(true).size(95, 18).build();
        ToggleWidget animalsToggle = new ToggleWidget.Builder().label("Animals").toggled(false).size(90, 18).build();
        ToggleWidget ambientToggle = new ToggleWidget.Builder().label("Ambient").toggled(false).size(90, 18).build();
        ToggleWidget miscToggle = new ToggleWidget.Builder().label("Misc").toggled(false).size(80, 18).build();
        ToggleWidget vehiclesToggle = new ToggleWidget.Builder().label("Vehicles").toggled(false).size(90, 18).build();
        ToggleWidget itemsToggle = new ToggleWidget.Builder().label("Items").toggled(false).size(80, 18).build();

        builder.addRow("Types", true, 18, monstersToggle, animalsToggle, ambientToggle);
        builder.addRow("More", true, 18, miscToggle, vehiclesToggle, itemsToggle);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton purgeButton = new AnimatedButton.Builder()
            .label("Purge")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                if (!monstersToggle.getValue() && !animalsToggle.getValue() && !ambientToggle.getValue() && !miscToggle.getValue()
                    && !vehiclesToggle.getValue() && !itemsToggle.getValue()) {
                    new Notification("Error", "Select Purge Types", Notification.Type.ERROR);
                    return;
                }
                flowManager.suppressNextWorldSuccessNotification(serverId, "purgeWorld");
                flowManager.purgeWorld(serverId, worldName, monstersToggle.getValue(), animalsToggle.getValue(), ambientToggle.getValue(),
                    miscToggle.getValue(), vehiclesToggle.getValue(), itemsToggle.getValue());
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, purgeButton);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showWorldUnloadPopup(String worldName) {
        List<String> fallbackOptions = fallbackWorldOptions(worldName);
        PopupWidget.Builder builder = new PopupWidget.Builder("Unload World · " + worldName)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(430, 145);
        String defaultFallbackWorld = selectDefaultFallbackWorld(worldName, fallbackOptions);
        SelectorWidgets fallbackSelector = createSelectorWidgets("Fallback World", fallbackOptions,
            defaultFallbackWorld, 200, false, Function.identity());
        builder.addRow("Fallback", true, 18, fallbackSelector.input, fallbackSelector.dropdown);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton unloadButton = new AnimatedButton.Builder()
            .label("Unload")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                String fallbackWorld = selectedSelectorValue(fallbackSelector).trim();
                if (fallbackWorld.isBlank()) {
                    new Notification("Error", "Fallback World Required", Notification.Type.ERROR);
                    return;
                }
                if (!WorldUiSupport.containsIgnoreCase(fallbackOptions, fallbackWorld)) {
                    new Notification("Error", "Unknown Fallback World", Notification.Type.ERROR);
                    return;
                }
                flowManager.suppressNextWorldSuccessNotification(serverId, "unloadWorld");
                flowManager.unloadWorld(serverId, worldName, fallbackWorld);
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, unloadButton);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showWorldDeletePopup(String worldName) {
        List<String> fallbackOptions = fallbackWorldOptions(worldName);
        PopupWidget.Builder builder = new PopupWidget.Builder("Delete World · " + worldName)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(460, 175);
        String defaultFallbackWorld = selectDefaultFallbackWorld(worldName, fallbackOptions);
        SelectorWidgets fallbackSelector = createSelectorWidgets("Fallback World", fallbackOptions,
            defaultFallbackWorld, 200, false, Function.identity());
        ToggleWidget deleteFilesToggle = new ToggleWidget.Builder().label("Delete Files").toggled(false).size(100, 18).build();
        builder.addRow("Delete Files", true, 18, deleteFilesToggle);
        builder.addRow("Fallback", true, 18, fallbackSelector.input, fallbackSelector.dropdown);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton deleteButton = new AnimatedButton.Builder()
            .label("Delete")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                String fallbackWorld = selectedSelectorValue(fallbackSelector).trim();
                if (fallbackWorld.isBlank()) {
                    new Notification("Error", "Fallback World Required", Notification.Type.ERROR);
                    return;
                }
                if (!WorldUiSupport.containsIgnoreCase(fallbackOptions, fallbackWorld)) {
                    new Notification("Error", "Unknown Fallback World", Notification.Type.ERROR);
                    return;
                }
                flowManager.suppressNextWorldSuccessNotification(serverId, "deleteWorld");
                flowManager.deleteWorld(serverId, worldName, deleteFilesToggle.getValue(), fallbackWorld);
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, deleteButton);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showPortalDeletePopup(String portalId) {
        WorldPortal portal = flowManager.getWorldPortal(serverId, portalId);
        if (portal == null) {
            return;
        }
        showDeleteConfirmation("Delete Portal · " + (safeText(portal.getPortalName()).isBlank() ? safeText(portal.getPortalId()) : safeText(portal.getPortalName())),
            () -> flowManager.deletePortal(serverId, portal.getPortalId()));
    }

    private void showInventoryGroupDeletePopup(String groupId) {
        WorldInventoryGroup group = flowManager.getWorldInventoryGroup(serverId, groupId);
        if (group == null) {
            return;
        }
        showDeleteConfirmation("Delete Group · " + (safeText(group.getDisplayName()).isBlank() ? safeText(group.getGroupId()) : safeText(group.getDisplayName())),
            () -> flowManager.deleteInventoryGroup(serverId, group.getGroupId()));
    }

    private void showSignPortalDeletePopup(String signId) {
        WorldSignPortal signPortal = flowManager.getWorldSignPortal(serverId, signId);
        if (signPortal == null) {
            return;
        }
        showDeleteConfirmation("Delete Sign · " + safeText(signPortal.getWorldName()) + " " + signPortal.getX() + "," + signPortal.getY() + "," + signPortal.getZ(),
            () -> flowManager.deleteSignPortal(serverId, signPortal.getSignId()));
    }

    private void showDeleteConfirmation(String title, Runnable action) {
        PopupWidget.Builder builder = new PopupWidget.Builder(title)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(400, 120);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton confirmButton = new AnimatedButton.Builder()
            .label("Confirm")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> {
                action.run();
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, confirmButton);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showCreateWorldPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Create World").setResizable(false);
        WorldSnapshot snapshot = flowManager.getWorldSnapshot(serverId);
        List<String> generatorHints = snapshot == null ? List.of() : snapshot.getGeneratorHints();
        List<WorldGeneratorDescriptor> generatorDescriptors = snapshot == null ? List.of() : snapshot.getGeneratorDescriptors();
        List<String> knownWorlds = worldNameOptions();

        TextInputWidget worldInput = new TextInputWidget.Builder().placeholder("World Name").size(220, 18).build();
        TextInputWidget seedInput = new TextInputWidget.Builder().placeholder("Seed").size(220, 18).build();
        DropDownWidget<String> environmentSelect = new DropDownWidget.Builder<>(List.of("NORMAL", "NETHER", "THE_END", "CUSTOM"))
            .size(220, 18)
            .selectedItem("NORMAL")
            .build();
        List<String> generatorOptions = new ArrayList<>();
        generatorOptions.add("Default");
        for (WorldGeneratorDescriptor descriptor : generatorDescriptors) {
            if (descriptor != null && descriptor.getId() != null && !descriptor.getId().isBlank()) {
                generatorOptions.add(descriptor.getId());
            }
        }
        DropDownWidget<String> generatorSelect = new DropDownWidget.Builder<>(generatorOptions)
            .size(220, 18)
            .selectedItem(generatorOptions.getFirst())
            .displayFunction(value -> {
                if (value == null || value.isBlank() || "Default".equalsIgnoreCase(value)) {
                    return "Default";
                }
                WorldGeneratorDescriptor descriptor = findGeneratorDescriptor(generatorDescriptors, value);
                return descriptor != null && descriptor.getDisplayName() != null && !descriptor.getDisplayName().isBlank() ? descriptor.getDisplayName() : value;
            })
            .build();
        TextInputWidget generatorConfigInput = new TextInputWidget.Builder().placeholder("Generator Config").size(220, 18).build();
        generatorConfigInput.active = false;
        generatorSelect.setOnSelectionChanged(selected -> {
            WorldGeneratorDescriptor descriptor = findGeneratorDescriptor(generatorDescriptors, selected);
            if (descriptor != null && descriptor.isConfigurable()) {
                generatorConfigInput.active = true;
                generatorConfigInput.setText(descriptor.getDefaultConfig() == null ? "" : descriptor.getDefaultConfig());
            } else {
                generatorConfigInput.active = false;
                generatorConfigInput.setText("");
            }
        });

        builder.addRow("World", true, 18, worldInput);
        builder.addRow("Seed", true, 18, seedInput);
        builder.addRow("Environment", true, 18, environmentSelect);
        builder.addRow("Generator", true, 18, generatorSelect);
        builder.addRow("Config", true, 18, generatorConfigInput);
        if (!generatorHints.isEmpty()) {
            String hintsText = String.join(", ", generatorHints.stream().limit(4).toList());
            if (generatorHints.size() > 4) {
                hintsText += "...";
            }
            IconButton hintsButton = new IconButton.Builder().label(hintsText).autoWidthOnTextChange(true).build();
            hintsButton.active = false;
            builder.addRow("Hints", true, 18, hintsButton);
        }

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton createBtn = new AnimatedButton.Builder()
            .label("Create")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String worldName = worldInput.getText() != null ? worldInput.getText().trim() : "";
                if (!WorldUiSupport.isValidSimpleId(worldName)) {
                    new Notification("Error", "Invalid World Name", Notification.Type.ERROR);
                    return;
                }
                if (WorldUiSupport.containsIgnoreCase(knownWorlds, worldName)) {
                    new Notification("Error", "World Already Exists", Notification.Type.ERROR);
                    return;
                }
                String generatorId = generatorSelect.getSelectedItem();
                if (generatorId != null && generatorId.equalsIgnoreCase("Default")) {
                    generatorId = "";
                }
                String environment = safeText(environmentSelect.getSelectedItem()).trim();
                flowManager.createWorld(serverId, worldName, seedInput.getText(), environment, generatorId, generatorConfigInput.getText());
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, createBtn);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showCloneWorldPopup(String sourceWorld) {
        PopupWidget.Builder builder = new PopupWidget.Builder("Clone World · " + sourceWorld).setResizable(false);

        TextInputWidget worldInput = new TextInputWidget.Builder().placeholder("Target World").size(220, 18).build();
        ToggleWidget loadAfterToggle = new ToggleWidget.Builder().label("Load After").toggled(true).size(80, 18).build();

        builder.addRow("Target", true, 18, worldInput);
        builder.addRow("", true, 18, loadAfterToggle);

        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton cloneBtn = new AnimatedButton.Builder()
            .label("Clone")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String targetWorld = worldInput.getText() != null ? worldInput.getText().trim() : "";
                if (!WorldUiSupport.isValidSimpleId(targetWorld)) {
                    new Notification("Error", "Invalid World Name", Notification.Type.ERROR);
                    return;
                }
                if (targetWorld.equalsIgnoreCase(sourceWorld)) {
                    new Notification("Error", "Choose A Different Target World", Notification.Type.ERROR);
                    return;
                }
                if (WorldUiSupport.containsIgnoreCase(worldNameOptions(), targetWorld)) {
                    new Notification("Error", "World Already Exists", Notification.Type.ERROR);
                    return;
                }
                flowManager.cloneWorld(serverId, sourceWorld, targetWorld, loadAfterToggle.getValue());
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, cloneBtn);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void showWorldSettingsPopup(String worldName) {
        WorldRegistryEntry world = flowManager.getWorld(serverId, worldName);
        if (world == null) {
            return;
        }
        WorldSnapshot snapshot = flowManager.getWorldSnapshot(serverId);
        List<WorldGameRuleDescriptor> descriptors = snapshot == null ? List.of() : snapshot.getGameRuleDescriptors();
        List<WorldInventoryGroup> inventoryGroups = snapshot == null ? List.of() : snapshot.getInventoryGroups();
        List<String> worldOptions = worldNameOptions();
        List<String> inventoryGroupOptions = new ArrayList<>();
        inventoryGroupOptions.add("None");
        List<String> sortedGroupIds = new ArrayList<>();
        for (WorldInventoryGroup inventoryGroup : inventoryGroups) {
            if (inventoryGroup != null && inventoryGroup.getGroupId() != null && !inventoryGroup.getGroupId().isBlank()) {
                sortedGroupIds.add(inventoryGroup.getGroupId());
            }
        }
        sortedGroupIds.sort(String.CASE_INSENSITIVE_ORDER);
        inventoryGroupOptions.addAll(sortedGroupIds);
        String selectedInventoryGroup = safeText(world.getProfileSettings().getInventoryGroupId()).trim();
        if (selectedInventoryGroup.isBlank()) {
            selectedInventoryGroup = "None";
        } else if (!inventoryGroupOptions.contains(selectedInventoryGroup)) {
            inventoryGroupOptions.add(selectedInventoryGroup);
        }
        PopupWidget.Builder builder = new PopupWidget.Builder("World Settings · " + worldName)
            .setResizable(true)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(620, 430);
        WorldProfileSettings profile = world.getProfileSettings();
        Map<String, List<String>> sectionRows = new LinkedHashMap<>();
        Map<String, String> sectionDescriptions = new LinkedHashMap<>();
        sectionDescriptions.put("Basics", "Name, visibility, difficulty, and shared player data.");
        sectionDescriptions.put("Access", "Who can enter, what it costs, and what they see.");
        sectionDescriptions.put("Spawn", "Where players arrive and which game mode they get.");
        sectionDescriptions.put("Rules", "Combat, saves, mobs, hunger, and healing.");
        sectionDescriptions.put("Links", "Portal routing between overworld, nether, and end.");
        sectionDescriptions.put("Game Rules", "Vanilla gamerules for this world.");
        sectionDescriptions.put("Advanced", "Low-level world state like isolated data, time, and weather.");

        List<String> difficultyOptions = WorldUiSupport.mergeOptions(List.of("PEACEFUL", "EASY", "NORMAL", "HARD"), safeText(world.getDifficulty()).trim().toUpperCase(Locale.ROOT));
        DropDownWidget<String> difficultySelect = new DropDownWidget.Builder<>(difficultyOptions)
            .size(180, 18)
            .selectedItem(safeText(world.getDifficulty()).trim().isBlank() ? "NORMAL" : safeText(world.getDifficulty()).trim().toUpperCase(Locale.ROOT))
            .build();
        TextInputWidget aliasInput = new TextInputWidget.Builder().text(safeText(profile.getAlias())).placeholder("Alias").size(180, 20).build();
        ToggleWidget hiddenToggle = new ToggleWidget.Builder().label("Hidden").toggled(profile.isHidden()).size(80, 18).build();
        TextInputWidget accessPermissionInput = new TextInputWidget.Builder().text(safeText(profile.getAccessPermission())).placeholder("Access Permission").size(180, 20).build();
        TextInputWidget bypassPermissionInput = new TextInputWidget.Builder().text(safeText(profile.getBypassPermission())).placeholder("Bypass Permission").size(180, 20).build();
        SelectorWidgets respawnWorldSelector = createSelectorWidgets("Respawn World", worldOptions, safeText(profile.getRespawnWorld()), 180, true, Function.identity());
        ToggleWidget forceGameModeToggle = new ToggleWidget.Builder().label("Force Game Mode").toggled(profile.isForceGameMode()).size(110, 18).build();
        DropDownWidget<String> gameModeSelect = new DropDownWidget.Builder<>(List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"))
            .size(140, 18)
            .selectedItem(safeText(profile.getGameMode()).isBlank() ? "SURVIVAL" : safeText(profile.getGameMode()).toUpperCase(Locale.ROOT))
            .build();
        ToggleWidget customSpawnToggle = new ToggleWidget.Builder().label("Custom Spawn").toggled(profile.isCustomSpawnEnabled()).size(110, 18).build();
        TextInputWidget spawnXInput = new TextInputWidget.Builder().text(String.valueOf(profile.getSpawnX())).placeholder("Spawn X").size(90, 20).build();
        TextInputWidget spawnYInput = new TextInputWidget.Builder().text(String.valueOf(profile.getSpawnY())).placeholder("Spawn Y").size(90, 20).build();
        TextInputWidget spawnZInput = new TextInputWidget.Builder().text(String.valueOf(profile.getSpawnZ())).placeholder("Spawn Z").size(90, 20).build();
        TextInputWidget spawnYawInput = new TextInputWidget.Builder().text(String.valueOf(profile.getSpawnYaw())).placeholder("Spawn Yaw").size(90, 20).build();
        TextInputWidget spawnPitchInput = new TextInputWidget.Builder().text(String.valueOf(profile.getSpawnPitch())).placeholder("Spawn Pitch").size(90, 20).build();
        ToggleWidget entryFeeToggle = new ToggleWidget.Builder().label("Entry Fee").toggled(profile.isEntryFeeEnabled()).size(90, 18).build();
        TextInputWidget entryFeeInput = new TextInputWidget.Builder().text(String.valueOf(profile.getEntryFee())).placeholder("Entry Fee Amount").size(120, 20).build();
        ToggleWidget pvpToggle = new ToggleWidget.Builder().label("PVP").toggled(profile.isPvpEnabled()).size(70, 18).build();
        ToggleWidget keepSpawnLoadedToggle = new ToggleWidget.Builder().label("Keep Spawn Loaded").toggled(profile.isKeepSpawnLoaded()).size(105, 18).build();
        ToggleWidget autoSaveToggle = new ToggleWidget.Builder().label("Auto Save").toggled(profile.isAutoSaveEnabled()).size(95, 18).build();
        ToggleWidget animalSpawnsToggle = new ToggleWidget.Builder().label("Animal Spawns").toggled(profile.isAnimalSpawnsEnabled()).size(120, 18).build();
        ToggleWidget monsterSpawnsToggle = new ToggleWidget.Builder().label("Monster Spawns").toggled(profile.isMonsterSpawnsEnabled()).size(125, 18).build();
        ToggleWidget hungerToggle = new ToggleWidget.Builder().label("Hunger").toggled(profile.isHungerEnabled()).size(85, 18).build();
        ToggleWidget autoHealToggle = new ToggleWidget.Builder().label("Auto Heal").toggled(profile.isAutoHealEnabled()).size(95, 18).build();
        ToggleWidget bedRespawnToggle = new ToggleWidget.Builder().label("Bed Respawn").toggled(profile.isBedRespawnEnabled()).size(110, 18).build();
        ToggleWidget anchorRespawnToggle = new ToggleWidget.Builder().label("Anchor Respawn").toggled(profile.isAnchorRespawnEnabled()).size(125, 18).build();
        ToggleWidget nonLivingSpawnsToggle = new ToggleWidget.Builder().label("Misc Spawns").toggled(profile.isNonLivingEntitySpawnsEnabled()).size(115, 18).build();
        TextInputWidget arrivalMessageInput = new TextInputWidget.Builder().text(safeText(profile.getArrivalMessage())).placeholder("Arrival Message").size(240, 20).build();
        TextInputWidget denyMessageInput = new TextInputWidget.Builder().text(safeText(profile.getDenyMessage())).placeholder("Deny Message").size(240, 20).build();
        DropDownWidget<String> inventoryGroupSelect = new DropDownWidget.Builder<>(inventoryGroupOptions)
            .size(220, 18)
            .selectedItem(selectedInventoryGroup)
            .displayFunction(value -> formatInventoryGroupOption(inventoryGroups, value))
            .build();
        SelectorWidgets linkedNetherWorldSelector = createSelectorWidgets("Linked Nether World", worldOptions, safeText(profile.getLinkedNetherWorld()), 170, true, Function.identity());
        SelectorWidgets linkedEndWorldSelector = createSelectorWidgets("Linked End World", worldOptions, safeText(profile.getLinkedEndWorld()), 170, true, Function.identity());
        SelectorWidgets linkedOverworldSelector = createSelectorWidgets("Linked Overworld", worldOptions, safeText(profile.getLinkedOverworld()), 170, true, Function.identity());
        TextInputWidget netherScaleInput = new TextInputWidget.Builder().text(formatDecimal(profile.getNetherScale())).placeholder("Nether Scale").size(120, 20).build();
        TextInputWidget endScaleInput = new TextInputWidget.Builder().text(formatDecimal(profile.getEndScale())).placeholder("End Scale").size(120, 20).build();
        ToggleWidget autoLinkNetherToggle = new ToggleWidget.Builder().label("Auto Link Nether").toggled(profile.isAutoLinkNetherPortal()).size(105, 18).build();
        ToggleWidget autoLinkEndToggle = new ToggleWidget.Builder().label("Auto Link End").toggled(profile.isAutoLinkEndPortal()).size(95, 18).build();
        ToggleWidget isolated = new ToggleWidget.Builder().label("Isolated State").toggled(world.isIsolatedPlayerState()).size(110, 18).build();
        ToggleWidget timeLock = new ToggleWidget.Builder().label("Time Lock").toggled(world.isTimeLockEnabled()).size(90, 18).build();
        TextInputWidget lockedTimeInput = new TextInputWidget.Builder().text(String.valueOf(world.getLockedTime())).placeholder("Locked Time").size(120, 20).build();
        ToggleWidget weatherLock = new ToggleWidget.Builder().label("Weather Lock").toggled(world.isWeatherLockEnabled()).size(100, 18).build();
        ToggleWidget storm = new ToggleWidget.Builder().label("Storm").toggled(world.isLockedStorm()).size(70, 18).build();
        ToggleWidget thundering = new ToggleWidget.Builder().label("Thunder").toggled(world.isLockedThundering()).size(80, 18).build();
        Map<String, ToggleWidget> booleanRules = new HashMap<>();
        Map<String, TextInputWidget> valueRules = new HashMap<>();
        List<Widget> booleanRow = new ArrayList<>();
        DropDownWidget<String> sectionSelect = new DropDownWidget.Builder<>(new ArrayList<>(sectionDescriptions.keySet()))
            .size(220, 18)
            .selectedItem("Basics")
            .build();
        sectionSelect.setHint(sectionDescriptions.get("Basics"));
        difficultySelect.setHint("How hard this world should feel for players.");
        aliasInput.setHint("Optional display name shown instead of the raw world id.");
        hiddenToggle.setHint("Hide this world from normal world browsing when enabled.");
        inventoryGroupSelect.setHint(describeInventoryGroupSelection(inventoryGroups, selectedInventoryGroup));
        accessPermissionInput.setHint("Players need this permission to enter the world. Leave empty for open access.");
        bypassPermissionInput.setHint("Players with this permission skip entry restrictions and fees.");
        entryFeeToggle.setHint("Charge players when they enter this world.");
        entryFeeInput.setHint("How much entry costs when entry fee is enabled.");
        arrivalMessageInput.setHint("Optional message shown when a player enters this world.");
        denyMessageInput.setHint("Optional message shown when entry is blocked.");
        respawnWorldSelector.input.setHint("Optional world players should respawn in after dying here.");
        respawnWorldSelector.dropdown.setHint("Pick a known respawn world.");
        forceGameModeToggle.setHint("Force everyone entering this world into a specific game mode.");
        gameModeSelect.setHint("Game mode applied when force game mode is enabled.");
        customSpawnToggle.setHint("Send players to this exact spawn instead of the default world spawn.");
        spawnXInput.setHint("Custom spawn X position.");
        spawnYInput.setHint("Custom spawn Y position.");
        spawnZInput.setHint("Custom spawn Z position.");
        spawnYawInput.setHint("Direction players face at the custom spawn.");
        spawnPitchInput.setHint("Vertical look angle at the custom spawn.");
        pvpToggle.setHint("Allow player versus player combat in this world.");
        keepSpawnLoadedToggle.setHint("Keep the spawn chunks loaded even when nobody is nearby.");
        autoSaveToggle.setHint("Let the world save itself automatically.");
        animalSpawnsToggle.setHint("Allow passive mobs to spawn naturally.");
        monsterSpawnsToggle.setHint("Allow hostile mobs to spawn naturally.");
        nonLivingSpawnsToggle.setHint("Allow non-living entities like dropped items, boats, or minecarts to appear naturally.");
        hungerToggle.setHint("Let players lose hunger in this world.");
        autoHealToggle.setHint("Let players heal naturally when their hunger is high enough.");
        bedRespawnToggle.setHint("Allow beds to set respawn points here.");
        anchorRespawnToggle.setHint("Allow respawn anchors to work here.");
        linkedNetherWorldSelector.input.setHint("Optional nether partner world for portal travel. Leave empty for normal behavior.");
        linkedNetherWorldSelector.dropdown.setHint("Pick a known nether-linked world.");
        linkedEndWorldSelector.input.setHint("Optional end partner world for portal travel.");
        linkedEndWorldSelector.dropdown.setHint("Pick a known end-linked world.");
        linkedOverworldSelector.input.setHint("Optional overworld partner used when this world links back out.");
        linkedOverworldSelector.dropdown.setHint("Pick a known overworld-linked world.");
        netherScaleInput.setHint("Coordinate scale used for overworld to nether travel. Default is 8.");
        endScaleInput.setHint("Coordinate scale used for overworld to end travel. Default is 1.");
        autoLinkNetherToggle.setHint("Automatically create sensible nether portal links when possible.");
        autoLinkEndToggle.setHint("Automatically create sensible end portal links when possible.");
        isolated.setHint("Keep player inventory and state separate from other worlds.");
        timeLock.setHint("Force this world to stay at one time of day.");
        lockedTimeInput.setHint("The exact time to keep when time lock is enabled.");
        weatherLock.setHint("Force this world to stay in one weather state.");
        storm.setHint("Keep rain or snow enabled when weather lock is on.");
        thundering.setHint("Keep thunder enabled when weather lock is on.");
        inventoryGroupSelect.setOnSelectionChanged(value -> inventoryGroupSelect.setHint(describeInventoryGroupSelection(inventoryGroups, safeText(value))));

        builder.addRow("Section", true, 18, sectionSelect);
        addPopupSectionRow(builder, sectionRows, "Basics", "ws-basics-difficulty", "Difficulty", 18, difficultySelect);
        addPopupSectionRow(builder, sectionRows, "Basics", "ws-basics-identity", "Identity", 18, aliasInput, hiddenToggle);
        addPopupSectionRow(builder, sectionRows, "Basics", "ws-basics-group", "Inventory Group", 18, inventoryGroupSelect);
        addPopupSectionRow(builder, sectionRows, "Access", "ws-access-permission", "Access Permission", 18, accessPermissionInput);
        addPopupSectionRow(builder, sectionRows, "Access", "ws-access-bypass", "Bypass Permission", 18, bypassPermissionInput);
        addPopupSectionRow(builder, sectionRows, "Access", "ws-access-entry-toggle", "Entry Fee", 18, entryFeeToggle);
        addPopupSectionRow(builder, sectionRows, "Access", "ws-access-entry-amount", "Entry Fee Amount", 18, entryFeeInput);
        addPopupSectionRow(builder, sectionRows, "Access", "ws-access-arrival", "Arrival Message", 18, arrivalMessageInput);
        addPopupSectionRow(builder, sectionRows, "Access", "ws-access-deny", "Deny Message", 18, denyMessageInput);
        addPopupSectionRow(builder, sectionRows, "Spawn", "ws-spawn-respawn", "Respawn World", 18, respawnWorldSelector.input, respawnWorldSelector.dropdown);
        addPopupSectionRow(builder, sectionRows, "Spawn", "ws-spawn-gamemode-toggle", "Force Game Mode", 18, forceGameModeToggle);
        addPopupSectionRow(builder, sectionRows, "Spawn", "ws-spawn-gamemode-value", "Game Mode", 18, gameModeSelect);
        addPopupSectionRow(builder, sectionRows, "Spawn", "ws-spawn-custom-toggle", "Custom Spawn", 18, customSpawnToggle);
        addPopupSectionRow(builder, sectionRows, "Spawn", "ws-spawn-position", "Spawn Position", 18, spawnXInput, spawnYInput, spawnZInput);
        addPopupSectionRow(builder, sectionRows, "Spawn", "ws-spawn-look", "Spawn Rotation", 18, spawnYawInput, spawnPitchInput);
        addPopupSectionRow(builder, sectionRows, "Rules", "ws-rules-world", "World Rules", 18, pvpToggle, keepSpawnLoadedToggle, autoSaveToggle);
        addPopupSectionRow(builder, sectionRows, "Rules", "ws-rules-spawns", "Mob Spawns", 18, animalSpawnsToggle, monsterSpawnsToggle, nonLivingSpawnsToggle);
        addPopupSectionRow(builder, sectionRows, "Rules", "ws-rules-player", "Player Survival", 18, hungerToggle, autoHealToggle, bedRespawnToggle, anchorRespawnToggle);
        addPopupSectionRow(builder, sectionRows, "Links", "ws-links-nether", "Nether Link", 18, linkedNetherWorldSelector.input, linkedNetherWorldSelector.dropdown);
        addPopupSectionRow(builder, sectionRows, "Links", "ws-links-end", "End Link", 18, linkedEndWorldSelector.input, linkedEndWorldSelector.dropdown);
        addPopupSectionRow(builder, sectionRows, "Links", "ws-links-overworld", "Overworld Link", 18, linkedOverworldSelector.input, linkedOverworldSelector.dropdown);
        addPopupSectionRow(builder, sectionRows, "Links", "ws-links-scale", "Scale", 18, netherScaleInput, endScaleInput);
        addPopupSectionRow(builder, sectionRows, "Links", "ws-links-auto", "Auto Link", 18, autoLinkNetherToggle, autoLinkEndToggle);
        for (WorldGameRuleDescriptor descriptor : descriptors) {
            if (descriptor == null || descriptor.getName() == null || descriptor.getName().isBlank()) {
                continue;
            }
            String ruleName = descriptor.getName();
            if ("boolean".equalsIgnoreCase(descriptor.getType())) {
                ToggleWidget toggle = new ToggleWidget.Builder()
                    .label(prettyRuleName(ruleName))
                    .toggled(ruleEnabled(world, ruleName))
                    .size(150, 18)
                    .build();
                toggle.setHint("Vanilla game rule override for this world.");
                booleanRules.put(ruleName, toggle);
                booleanRow.add(toggle);
                if (booleanRow.size() == 2) {
                    addPopupSectionRow(builder, sectionRows, "Game Rules", "ws-gamerules-bool-" + booleanRules.size(), "", 18, booleanRow.toArray(Widget[]::new));
                    booleanRow.clear();
                }
                continue;
            }
            TextInputWidget valueInput = new TextInputWidget.Builder()
                .text(safeText(world.getGameRules().get(ruleName)))
                .placeholder(prettyRuleName(ruleName))
                .size(180, 20)
                .build();
            valueInput.setHint("Vanilla game rule override for this world.");
            valueRules.put(ruleName, valueInput);
            addPopupSectionRow(builder, sectionRows, "Game Rules", "ws-gamerules-value-" + ruleName, prettyRuleName(ruleName), 18, valueInput);
        }
        if (!booleanRow.isEmpty()) {
            addPopupSectionRow(builder, sectionRows, "Game Rules", "ws-gamerules-bool-last", "", 18, booleanRow.toArray(Widget[]::new));
        }
        addPopupSectionRow(builder, sectionRows, "Advanced", "ws-advanced-isolated", "Player Data", 18, isolated);
        addPopupSectionRow(builder, sectionRows, "Advanced", "ws-advanced-time-toggle", "Time Lock", 18, timeLock);
        addPopupSectionRow(builder, sectionRows, "Advanced", "ws-advanced-locked-time", "Locked Time", 20, lockedTimeInput);
        addPopupSectionRow(builder, sectionRows, "Advanced", "ws-advanced-weather-toggle", "Weather Lock", 18, weatherLock);
        addPopupSectionRow(builder, sectionRows, "Advanced", "ws-advanced-locked-weather", "Locked Weather", 18, storm, thundering);

        PopupWidget[] popupRef = new PopupWidget[1];
        Runnable refreshSectionVisibility = () -> {
            if (popupRef[0] == null) {
                return;
            }
            String activeSection = safeText(sectionSelect.getSelectedItem()).trim();
            if (activeSection.isBlank()) {
                activeSection = "Basics";
            }
            sectionSelect.setHint(safeText(sectionDescriptions.getOrDefault(activeSection, sectionDescriptions.get("Basics"))));
            updatePopupSectionVisibility(popupRef[0], sectionRows, activeSection);
            popupRef[0].setRowVisibility("ws-access-entry-amount", "Access".equalsIgnoreCase(activeSection) && entryFeeToggle.getValue());
            popupRef[0].setRowVisibility("ws-spawn-gamemode-value", "Spawn".equalsIgnoreCase(activeSection) && forceGameModeToggle.getValue());
            boolean showCustomSpawn = "Spawn".equalsIgnoreCase(activeSection) && customSpawnToggle.getValue();
            popupRef[0].setRowVisibility("ws-spawn-position", showCustomSpawn);
            popupRef[0].setRowVisibility("ws-spawn-look", showCustomSpawn);
            popupRef[0].setRowVisibility("ws-advanced-locked-time", "Advanced".equalsIgnoreCase(activeSection) && timeLock.getValue());
            popupRef[0].setRowVisibility("ws-advanced-locked-weather", "Advanced".equalsIgnoreCase(activeSection) && weatherLock.getValue());
        };
        sectionSelect.setOnSelectionChanged(value -> refreshSectionVisibility.run());
        entryFeeToggle.setOnChange(refreshSectionVisibility);
        forceGameModeToggle.setOnChange(refreshSectionVisibility);
        customSpawnToggle.setOnChange(refreshSectionVisibility);
        timeLock.setOnChange(refreshSectionVisibility);
        weatherLock.setOnChange(refreshSectionVisibility);
        builder.addTitleButton(() -> {
                String selectedDifficulty = safeText(difficultySelect.getSelectedItem()).trim();
                String respawnWorld = selectedSelectorValue(respawnWorldSelector).trim();
                String linkedNetherWorld = selectedSelectorValue(linkedNetherWorldSelector).trim();
                String linkedEndWorld = selectedSelectorValue(linkedEndWorldSelector).trim();
                String linkedOverworld = selectedSelectorValue(linkedOverworldSelector).trim();
                Double spawnX = parseNullableDouble(spawnXInput.getText());
                Double spawnY = parseNullableDouble(spawnYInput.getText());
                Double spawnZ = parseNullableDouble(spawnZInput.getText());
                Float spawnYaw = parseNullableFloat(spawnYawInput.getText());
                Float spawnPitch = parseNullableFloat(spawnPitchInput.getText());
                Double entryFee = parseNullableDouble(entryFeeInput.getText());
                Double netherScale = parseNullableDouble(netherScaleInput.getText());
                Double endScale = parseNullableDouble(endScaleInput.getText());
                Long lockedTime = parseNullableLong(lockedTimeInput.getText());
                if (selectedDifficulty.isBlank()) {
                    new Notification("Error", "Difficulty Required", Notification.Type.ERROR);
                    return;
                }
                if (!WorldUiSupport.isKnownEntryOrBlank(worldOptions, respawnWorld)) {
                    new Notification("Error", "Unknown Respawn World", Notification.Type.ERROR);
                    return;
                }
                if (!WorldUiSupport.isKnownEntryOrBlank(worldOptions, linkedNetherWorld)
                    || !WorldUiSupport.isKnownEntryOrBlank(worldOptions, linkedEndWorld)
                    || !WorldUiSupport.isKnownEntryOrBlank(worldOptions, linkedOverworld)) {
                    new Notification("Error", "Unknown Linked World", Notification.Type.ERROR);
                    return;
                }
                if (spawnX == null || spawnY == null || spawnZ == null || spawnYaw == null || spawnPitch == null) {
                    new Notification("Error", "Invalid Spawn", Notification.Type.ERROR);
                    return;
                }
                if (entryFee == null) {
                    new Notification("Error", "Invalid Entry Fee", Notification.Type.ERROR);
                    return;
                }
                if (netherScale == null || endScale == null) {
                    new Notification("Error", "Invalid Scale", Notification.Type.ERROR);
                    return;
                }
                if (lockedTime == null) {
                    new Notification("Error", "Invalid Locked Time", Notification.Type.ERROR);
                    return;
                }
                boolean changedAnything = false;
                if (!selectedDifficulty.equalsIgnoreCase(safeText(world.getDifficulty()).trim())) {
                    flowManager.suppressNextWorldSuccessNotification(serverId, "setDifficulty");
                    flowManager.setWorldDifficulty(serverId, worldName, selectedDifficulty);
                    changedAnything = true;
                }
                WorldProfileSettings changedProfile = new WorldProfileSettings();
                changedProfile.setAlias(safeText(aliasInput.getText()).trim());
                changedProfile.setHidden(hiddenToggle.getValue());
                changedProfile.setAccessPermission(safeText(accessPermissionInput.getText()).trim());
                changedProfile.setBypassPermission(safeText(bypassPermissionInput.getText()).trim());
                changedProfile.setRespawnWorld(respawnWorld);
                changedProfile.setForceGameMode(forceGameModeToggle.getValue());
                changedProfile.setGameMode(safeText(gameModeSelect.getSelectedItem()));
                changedProfile.setCustomSpawnEnabled(customSpawnToggle.getValue());
                changedProfile.setSpawnX(spawnX);
                changedProfile.setSpawnY(spawnY);
                changedProfile.setSpawnZ(spawnZ);
                changedProfile.setSpawnYaw(spawnYaw);
                changedProfile.setSpawnPitch(spawnPitch);
                changedProfile.setEntryFeeEnabled(entryFeeToggle.getValue());
                changedProfile.setEntryFee(entryFee);
                changedProfile.setPvpEnabled(pvpToggle.getValue());
                changedProfile.setKeepSpawnLoaded(keepSpawnLoadedToggle.getValue());
                changedProfile.setAutoSaveEnabled(autoSaveToggle.getValue());
                changedProfile.setAnimalSpawnsEnabled(animalSpawnsToggle.getValue());
                changedProfile.setMonsterSpawnsEnabled(monsterSpawnsToggle.getValue());
                changedProfile.setHungerEnabled(hungerToggle.getValue());
                changedProfile.setAutoHealEnabled(autoHealToggle.getValue());
                changedProfile.setBedRespawnEnabled(bedRespawnToggle.getValue());
                changedProfile.setAnchorRespawnEnabled(anchorRespawnToggle.getValue());
                changedProfile.setNonLivingEntitySpawnsEnabled(nonLivingSpawnsToggle.getValue());
                changedProfile.setArrivalMessage(safeText(arrivalMessageInput.getText()).trim());
                changedProfile.setDenyMessage(safeText(denyMessageInput.getText()).trim());
                String inventoryGroupId = safeText(inventoryGroupSelect.getSelectedItem()).trim();
                changedProfile.setInventoryGroupId("None".equalsIgnoreCase(inventoryGroupId) ? "" : inventoryGroupId);
                changedProfile.setLinkedNetherWorld(linkedNetherWorld);
                changedProfile.setLinkedEndWorld(linkedEndWorld);
                changedProfile.setLinkedOverworld(linkedOverworld);
                changedProfile.setNetherScale(netherScale);
                changedProfile.setEndScale(endScale);
                changedProfile.setAutoLinkNetherPortal(autoLinkNetherToggle.getValue());
                changedProfile.setAutoLinkEndPortal(autoLinkEndToggle.getValue());
                if (profileChanged(profile, changedProfile)) {
                    flowManager.suppressNextWorldSuccessNotification(serverId, "setWorldProfile");
                    flowManager.setWorldProfile(serverId, worldName, changedProfile);
                    changedAnything = true;
                }
                Map<String, String> changedRules = new LinkedHashMap<>();
                for (Map.Entry<String, ToggleWidget> entry : booleanRules.entrySet()) {
                    String newValue = String.valueOf(entry.getValue().getValue());
                    String oldValue = safeText(world.getGameRules().get(entry.getKey()));
                    if (!newValue.equalsIgnoreCase(oldValue)) {
                        changedRules.put(entry.getKey(), newValue);
                    }
                }
                for (Map.Entry<String, TextInputWidget> entry : valueRules.entrySet()) {
                    String newValue = safeText(entry.getValue().getText()).trim();
                    String oldValue = safeText(world.getGameRules().get(entry.getKey())).trim();
                    if (newValue.isBlank()) {
                        newValue = oldValue;
                    }
                    if (!newValue.equals(oldValue)) {
                        changedRules.put(entry.getKey(), newValue);
                    }
                }
                if (!changedRules.isEmpty()) {
                    flowManager.suppressNextWorldSuccessNotification(serverId, "setGameRules");
                    flowManager.setWorldGameRules(serverId, worldName, changedRules);
                    changedAnything = true;
                }
                if (isolated.getValue() != world.isIsolatedPlayerState()) {
                    flowManager.suppressNextWorldSuccessNotification(serverId, "setIsolatedPlayerState");
                    flowManager.setWorldIsolatedState(serverId, worldName, isolated.getValue());
                    changedAnything = true;
                }
                long newLockedTime = lockedTime;
                if (timeLock.getValue() != world.isTimeLockEnabled() || newLockedTime != world.getLockedTime()) {
                    flowManager.suppressNextWorldSuccessNotification(serverId, "setTimeLock");
                    flowManager.setWorldTimeLock(serverId, worldName, timeLock.getValue(), newLockedTime);
                    changedAnything = true;
                }
                if (weatherLock.getValue() != world.isWeatherLockEnabled() || storm.getValue() != world.isLockedStorm() || thundering.getValue() != world.isLockedThundering()) {
                    flowManager.suppressNextWorldSuccessNotification(serverId, "setWeatherLock");
                    flowManager.setWorldWeatherLock(serverId, worldName, weatherLock.getValue(), storm.getValue(), thundering.getValue());
                    changedAnything = true;
                }
                if (changedAnything) {
                    new Notification("ReSync", "World Settings Saved", Notification.Type.SUCCESS);
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            }, "Save", ThemeManager.getAccent("nice"));

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        refreshSectionVisibility.run();
        popupRef[0].show();
    }

    private void rebuildInventoryGroups() {
        if (inventoryGroupsContainer == null) {
            return;
        }
        inventoryGroupsContainer.clearWidgets();
        inventoryGroupEntries.clear();

        IconButton createButton = new IconButton.Builder()
            .label("New Group")
            .accentType(ThemeManager.getAccent("nice"))
            .imagePath("newFile.png")
            .size(130, 18)
            .onClick(this::showCreateInventoryGroupPopup)
            .build();
        IconButton refreshButton = new IconButton.Builder()
            .label("Refresh")
            .imagePath("reload.png")
            .size(120, 18)
            .onClick(() -> flowManager.refreshWorldsFromServer(serverId))
            .build();
        RowWidget topRow = new RowWidget.Builder()
            .size(Math.max(200, inventoryGroupsContainer.getWidth() - 20), 18)
            .addWidget(createButton, refreshButton)
            .build();
        inventoryGroupsContainer.addWidget(topRow);

        List<WorldInventoryGroup> groups = new ArrayList<>(flowManager.getWorldInventoryGroupsForServer(serverId));
        groups.sort(Comparator.comparing(group -> safeText(group == null ? "" : group.getGroupId()), String.CASE_INSENSITIVE_ORDER));
        for (WorldInventoryGroup group : groups) {
            if (group == null || safeText(group.getGroupId()).isBlank()) {
                continue;
            }
            upsertInventoryGroupEntry(group.getGroupId());
        }
    }

    private void upsertInventoryGroupEntry(String groupId) {
        if (inventoryGroupsContainer == null || groupId == null || groupId.isBlank()) {
            return;
        }
        WorldInventoryGroup group = flowManager.getWorldInventoryGroup(serverId, groupId);
        if (group == null) {
            return;
        }
        MountableButtonWidget existing = inventoryGroupEntries.remove(groupId);
        if (existing != null) {
            inventoryGroupsContainer.removeWidget(existing);
        }

        String displayName = safeText(group.getDisplayName()).isBlank() ? group.getGroupId() : group.getDisplayName();
        String description = "Worlds: " + group.getWorlds().size() + " | " + summarizeInventoryGroupShares(group);
        String hiddenText = "Members: " + (group.getWorlds().isEmpty() ? "None" : String.join(", ", group.getWorlds()));

        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
            .imagePath("edit.png").hint("Edit Group")
            .onClick(() -> showEditInventoryGroupPopup(groupId))
            .build();

        SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
            .imagePath("delete.png").hint("Delete Group")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> showInventoryGroupDeletePopup(groupId))
            .build();

        MountableButtonWidget widget = new MountableButtonWidget.Builder(displayName)
            .description(description)
            .hiddenText(hiddenText)
            .onClick(() -> showEditInventoryGroupPopup(groupId))
            .addButton(editButton)
            .addButton(deleteButton)
            .build();
        widget.setSize(Math.max(200, inventoryGroupsContainer.getWidth() - 20), 28);

        List<String> sortedIds = new ArrayList<>(inventoryGroupEntries.keySet());
        sortedIds.add(groupId);
        sortedIds.sort(String.CASE_INSENSITIVE_ORDER);
        int insertIndex = 1 + sortedIds.indexOf(groupId);
        if (inventoryGroupEntries.isEmpty() || insertIndex >= inventoryGroupsContainer.getWidgets().size()) {
            inventoryGroupsContainer.addWidget(widget);
        } else {
            inventoryGroupsContainer.insertWidget(widget, insertIndex);
        }
        inventoryGroupEntries.put(groupId, widget);
    }

    private void showCreateInventoryGroupPopup() {
        showInventoryGroupPopup(null);
    }

    private void showEditInventoryGroupPopup(String groupId) {
        showInventoryGroupPopup(flowManager.getWorldInventoryGroup(serverId, groupId));
    }

    private void showInventoryGroupPopup(WorldInventoryGroup existingGroup) {
        boolean editing = existingGroup != null;
        List<String> worldOptions = worldNameOptions();
        PopupWidget.Builder builder = new PopupWidget.Builder(editing ? "Edit Group" : "Create Group")
            .setResizable(true)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(560, 420);

        TextInputWidget groupIdInput = new TextInputWidget.Builder()
            .text(editing ? safeText(existingGroup.getGroupId()) : "")
            .placeholder("Group Id")
            .size(200, 20)
            .build();
        groupIdInput.active = !editing;
        TextInputWidget displayNameInput = new TextInputWidget.Builder()
            .text(editing ? safeText(existingGroup.getDisplayName()) : "")
            .placeholder("Display Name")
            .size(220, 20)
            .build();
        TextInputWidget worldsInput = new TextInputWidget.Builder()
            .text(editing ? String.join(", ", existingGroup.getWorlds()) : "")
            .placeholder("worldA, worldB")
            .size(320, 20)
            .build();
        SelectorWidgets worldSelector = createSelectorWidgets("World Name", worldOptions, "", 180, false, Function.identity());
        AnimatedButton addWorldButton = new AnimatedButton.Builder()
            .label("Add")
            .size(70, 18)
            .onClick(() -> {
                String selectedWorld = selectedSelectorValue(worldSelector).trim();
                if (selectedWorld.isBlank()) {
                    return;
                }
                worldsInput.setText(appendCommaSeparatedValue(worldsInput.getText(), selectedWorld));
            })
            .build();
        ToggleWidget shareInventoryToggle = new ToggleWidget.Builder().label("Inventory").toggled(!editing || existingGroup.isShareInventory()).size(90, 18).build();
        ToggleWidget shareArmorToggle = new ToggleWidget.Builder().label("Armor").toggled(!editing || existingGroup.isShareArmor()).size(80, 18).build();
        ToggleWidget shareOffhandToggle = new ToggleWidget.Builder().label("Offhand").toggled(!editing || existingGroup.isShareOffhand()).size(90, 18).build();
        ToggleWidget shareEnderChestToggle = new ToggleWidget.Builder().label("Ender Chest").toggled(!editing || existingGroup.isShareEnderChest()).size(110, 18).build();
        ToggleWidget shareHealthToggle = new ToggleWidget.Builder().label("Health").toggled(!editing || existingGroup.isShareHealth()).size(82, 18).build();
        ToggleWidget shareHungerToggle = new ToggleWidget.Builder().label("Hunger").toggled(!editing || existingGroup.isShareHunger()).size(86, 18).build();
        ToggleWidget shareExperienceToggle = new ToggleWidget.Builder().label("Experience").toggled(!editing || existingGroup.isShareExperience()).size(102, 18).build();
        ToggleWidget shareGameModeToggle = new ToggleWidget.Builder().label("Game Mode").toggled(!editing || existingGroup.isShareGameMode()).size(102, 18).build();
        ToggleWidget sharePotionEffectsToggle = new ToggleWidget.Builder().label("Potions").toggled(!editing || existingGroup.isSharePotionEffects()).size(90, 18).build();
        ToggleWidget shareLastLocationToggle = new ToggleWidget.Builder().label("Last Location").toggled(!editing || existingGroup.isShareLastLocation()).size(112, 18).build();
        ToggleWidget shareBedSpawnToggle = new ToggleWidget.Builder().label("Bed Spawn").toggled(!editing || existingGroup.isShareBedSpawn()).size(100, 18).build();

        builder.addRow("Group Id", true, 18, groupIdInput);
        builder.addRow("Display", true, 18, displayNameInput);
        builder.addRow("Worlds", true, 18, worldsInput);
        builder.addRow("Known", true, 18, worldSelector.input, worldSelector.dropdown, addWorldButton);
        builder.addRow("Share A", true, 18, shareInventoryToggle, shareArmorToggle, shareOffhandToggle, shareEnderChestToggle);
        builder.addRow("Share B", true, 18, shareHealthToggle, shareHungerToggle, shareExperienceToggle, shareGameModeToggle);
        builder.addRow("Share C", true, 18, sharePotionEffectsToggle, shareLastLocationToggle, shareBedSpawnToggle);

        PopupWidget[] popupRef = new PopupWidget[1];
        builder.addTitleButton(() -> {
            String groupId = editing ? safeText(existingGroup.getGroupId()).trim() : safeText(groupIdInput.getText()).trim();
            if (!WorldUiSupport.isValidSimpleId(groupId)) {
                new Notification("Error", "Invalid Group Id", Notification.Type.ERROR);
                return;
            }
            List<String> groupWorlds = WorldUiSupport.normalizeUniqueEntries(parseCommaSeparatedList(worldsInput.getText()));
            for (String groupWorld : groupWorlds) {
                if (!WorldUiSupport.containsIgnoreCase(worldOptions, groupWorld)) {
                    new Notification("Error", "Unknown Group World", Notification.Type.ERROR);
                    return;
                }
            }
            WorldInventoryGroup group = new WorldInventoryGroup();
            group.setGroupId(groupId);
            group.setDisplayName(safeText(displayNameInput.getText()).trim());
            group.setWorlds(groupWorlds);
            group.setShareInventory(shareInventoryToggle.getValue());
            group.setShareArmor(shareArmorToggle.getValue());
            group.setShareOffhand(shareOffhandToggle.getValue());
            group.setShareEnderChest(shareEnderChestToggle.getValue());
            group.setShareHealth(shareHealthToggle.getValue());
            group.setShareHunger(shareHungerToggle.getValue());
            group.setShareExperience(shareExperienceToggle.getValue());
            group.setShareGameMode(shareGameModeToggle.getValue());
            group.setSharePotionEffects(sharePotionEffectsToggle.getValue());
            group.setShareLastLocation(shareLastLocationToggle.getValue());
            group.setShareBedSpawn(shareBedSpawnToggle.getValue());
            if (editing) {
                flowManager.updateInventoryGroup(serverId, group);
            } else {
                flowManager.createInventoryGroup(serverId, group);
            }
            new Notification("ReSync", editing ? "Group Saved" : "Group Created", Notification.Type.SUCCESS);
            if (popupRef[0] != null) {
                popupRef[0].hide();
            }
        }, editing ? "Save" : "Create", ThemeManager.getAccent("nice"));

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void rebuildSignPortals() {
        if (signPortalsContainer == null) {
            return;
        }
        signPortalsContainer.clearWidgets();
        signPortalEntries.clear();

        IconButton createButton = new IconButton.Builder()
            .label("New Sign")
            .accentType(ThemeManager.getAccent("nice"))
            .imagePath("newFile.png")
            .size(120, 18)
            .onClick(this::showCreateSignPortalPopup)
            .build();
        IconButton refreshButton = new IconButton.Builder()
            .label("Refresh")
            .imagePath("reload.png")
            .size(120, 18)
            .onClick(() -> flowManager.refreshWorldsFromServer(serverId))
            .build();
        RowWidget topRow = new RowWidget.Builder()
            .size(Math.max(200, signPortalsContainer.getWidth() - 20), 18)
            .addWidget(createButton, refreshButton)
            .build();
        signPortalsContainer.addWidget(topRow);

        List<WorldSignPortal> signPortals = new ArrayList<>(flowManager.getWorldSignPortalsForServer(serverId));
        signPortals.sort(Comparator
            .comparing((WorldSignPortal signPortal) -> safeText(signPortal == null ? "" : signPortal.getWorldName()), String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(signPortal -> signPortal == null ? 0 : signPortal.getX())
            .thenComparingInt(signPortal -> signPortal == null ? 0 : signPortal.getY())
            .thenComparingInt(signPortal -> signPortal == null ? 0 : signPortal.getZ()));
        for (WorldSignPortal signPortal : signPortals) {
            if (signPortal == null || safeText(signPortal.getSignId()).isBlank()) {
                continue;
            }
            upsertSignPortalEntry(signPortal.getSignId());
        }
    }

    private void upsertSignPortalEntry(String signId) {
        if (signPortalsContainer == null || signId == null || signId.isBlank()) {
            return;
        }
        WorldSignPortal signPortal = flowManager.getWorldSignPortal(serverId, signId);
        if (signPortal == null) {
            return;
        }
        MountableButtonWidget existing = signPortalEntries.remove(signId);
        if (existing != null) {
            signPortalsContainer.removeWidget(existing);
        }

        String targetPortal = !safeText(signPortal.getPortalName()).isBlank() ? signPortal.getPortalName() : safeText(signPortal.getPortalId());
        String displayName = safeText(signPortal.getWorldName()) + " " + signPortal.getX() + "," + signPortal.getY() + "," + signPortal.getZ();
        String description = targetPortal + " | " + (signPortal.isEnabled() ? "Enabled" : "Disabled");
        StringBuilder hiddenText = new StringBuilder("SignId: ").append(safeText(signPortal.getSignId()));
        if (!safeText(signPortal.getPortalId()).isBlank()) {
            hiddenText.append(" | PortalId: ").append(signPortal.getPortalId());
        }

        SquareButtonWidget editButton = new SquareButtonWidget.Builder()
            .imagePath("edit.png").hint("Edit Sign")
            .onClick(() -> showEditSignPortalPopup(signId))
            .build();

        SquareButtonWidget deleteButton = new SquareButtonWidget.Builder()
            .imagePath("delete.png").hint("Delete Sign")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> showSignPortalDeletePopup(signId))
            .build();

        MountableButtonWidget widget = new MountableButtonWidget.Builder(displayName)
            .description(description)
            .hiddenText(hiddenText.toString())
            .onClick(() -> showEditSignPortalPopup(signId))
            .addButton(editButton)
            .addButton(deleteButton)
            .build();
        widget.setSize(Math.max(200, signPortalsContainer.getWidth() - 20), 28);

        List<String> sortedIds = new ArrayList<>(signPortalEntries.keySet());
        sortedIds.add(signId);
        sortedIds.sort((left, right) -> {
            WorldSignPortal leftPortal = flowManager.getWorldSignPortal(serverId, left);
            WorldSignPortal rightPortal = flowManager.getWorldSignPortal(serverId, right);
            String leftWorld = leftPortal == null ? "" : safeText(leftPortal.getWorldName());
            String rightWorld = rightPortal == null ? "" : safeText(rightPortal.getWorldName());
            int worldCompare = String.CASE_INSENSITIVE_ORDER.compare(leftWorld, rightWorld);
            if (worldCompare != 0) {
                return worldCompare;
            }
            int xCompare = Integer.compare(leftPortal == null ? 0 : leftPortal.getX(), rightPortal == null ? 0 : rightPortal.getX());
            if (xCompare != 0) {
                return xCompare;
            }
            int yCompare = Integer.compare(leftPortal == null ? 0 : leftPortal.getY(), rightPortal == null ? 0 : rightPortal.getY());
            if (yCompare != 0) {
                return yCompare;
            }
            return Integer.compare(leftPortal == null ? 0 : leftPortal.getZ(), rightPortal == null ? 0 : rightPortal.getZ());
        });
        int insertIndex = 1 + sortedIds.indexOf(signId);
        if (signPortalEntries.isEmpty() || insertIndex >= signPortalsContainer.getWidgets().size()) {
            signPortalsContainer.addWidget(widget);
        } else {
            signPortalsContainer.insertWidget(widget, insertIndex);
        }
        signPortalEntries.put(signId, widget);
    }

    private void showCreateSignPortalPopup() {
        showSignPortalPopup(null);
    }

    private void showEditSignPortalPopup(String signId) {
        showSignPortalPopup(flowManager.getWorldSignPortal(serverId, signId));
    }

    private void showSignPortalPopup(WorldSignPortal existingSignPortal) {
        boolean editing = existingSignPortal != null;
        List<String> worldOptions = worldNameOptions();
        List<String> portalOptions = portalTargetOptions();
        PopupWidget.Builder builder = new PopupWidget.Builder(editing ? "Edit Sign" : "Create Sign")
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(470, 280);

        SelectorWidgets worldSelector = createSelectorWidgets("World Name", worldOptions,
            editing ? safeText(existingSignPortal.getWorldName()) : "", 180, false, Function.identity());
        TextInputWidget xInput = new TextInputWidget.Builder().text(editing ? String.valueOf(existingSignPortal.getX()) : "0").placeholder("X").size(90, 20).build();
        TextInputWidget yInput = new TextInputWidget.Builder().text(editing ? String.valueOf(existingSignPortal.getY()) : "64").placeholder("Y").size(90, 20).build();
        TextInputWidget zInput = new TextInputWidget.Builder().text(editing ? String.valueOf(existingSignPortal.getZ()) : "0").placeholder("Z").size(90, 20).build();
        String existingPortalTarget = editing && !safeText(existingSignPortal.getPortalId()).isBlank()
            ? safeText(existingSignPortal.getPortalId())
            : editing ? safeText(existingSignPortal.getPortalName()) : "";
        SelectorWidgets portalTargetSelector = createSelectorWidgets("Portal Target", portalOptions, existingPortalTarget, 180, true, Function.identity());
        ToggleWidget enabledToggle = new ToggleWidget.Builder().label("Enabled").toggled(!editing || existingSignPortal.isEnabled()).size(90, 18).build();

        if (editing) {
            IconButton signIdButton = new IconButton.Builder().label(safeText(existingSignPortal.getSignId())).autoWidthOnTextChange(true).build();
            signIdButton.active = false;
            builder.addRow("Sign Id", true, 18, signIdButton);
        }
        builder.addRow("World", true, 18, worldSelector.input, worldSelector.dropdown, enabledToggle);
        builder.addRow("Pos", true, 18, xInput, yInput, zInput);
        builder.addRow("Portal", true, 18, portalTargetSelector.input, portalTargetSelector.dropdown);

        PopupWidget[] popupRef = new PopupWidget[1];
        builder.addTitleButton(() -> {
            String worldName = selectedSelectorValue(worldSelector).trim();
            String portalTarget = selectedSelectorValue(portalTargetSelector).trim();
            if (worldName.isBlank()) {
                new Notification("Error", "World Name Required", Notification.Type.ERROR);
                return;
            }
            if (!WorldUiSupport.containsIgnoreCase(worldOptions, worldName)) {
                new Notification("Error", "Unknown World Name", Notification.Type.ERROR);
                return;
            }
            if (portalTarget.isBlank()) {
                new Notification("Error", "Portal Target Required", Notification.Type.ERROR);
                return;
            }
            if (!WorldUiSupport.containsIgnoreCase(portalOptions, portalTarget)) {
                new Notification("Error", "Unknown Portal Target", Notification.Type.ERROR);
                return;
            }
            Integer x = parseNullableInt(xInput.getText());
            Integer y = parseNullableInt(yInput.getText());
            Integer z = parseNullableInt(zInput.getText());
            if (x == null || y == null || z == null) {
                new Notification("Error", "Invalid Sign Position", Notification.Type.ERROR);
                return;
            }
            WorldPortal targetPortal = flowManager.getWorldPortal(serverId, portalTarget);
            WorldSignPortal signPortal = new WorldSignPortal();
            signPortal.setSignId(editing ? existingSignPortal.getSignId() : "");
            signPortal.setWorldName(worldName);
            signPortal.setX(x);
            signPortal.setY(y);
            signPortal.setZ(z);
            signPortal.setPortalId(targetPortal == null ? "" : safeText(targetPortal.getPortalId()));
            signPortal.setPortalName(targetPortal == null ? portalTarget : safeText(targetPortal.getPortalName()));
            signPortal.setEnabled(enabledToggle.getValue());
            flowManager.createSignPortal(serverId, signPortal);
            new Notification("ReSync", editing ? "Sign Saved" : "Sign Created", Notification.Type.SUCCESS);
            if (popupRef[0] != null) {
                popupRef[0].hide();
            }
        }, editing ? "Save" : "Create", ThemeManager.getAccent("nice"));

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    public void handleWorldOperationResult(WorldOperationResult result) {
        if (result == null || !result.isSuccess()) {
            return;
        }
        String action = safeText(result.getAction()).trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "whoworld" -> showWorldWhoPopup(result);
            case "purgeworld" -> showWorldPurgeResultPopup(result);
            case "createworld", "loadworld", "unloadworld" -> {
                String worldName = resultWorldName(result);
                if (!worldName.isBlank()) {
                    upsertWorldEntry(worldName);
                }
                if ("unloadworld".equals(action)) {
                    showWorldStateResultPopup(result);
                }
            }
            case "deleteworld" -> {
                String worldName = resultWorldName(result);
                if (!worldName.isBlank()) {
                    removeWorldEntry(worldName);
                }
                rebuildPortals();
                rebuildSignPortals();
                showWorldStateResultPopup(result);
            }
            case "createportal", "resizeportal", "setportalenabled", "setportaldestination", "setportalbounds" -> {
                WorldPortal portal = resultData(result, "portal", WorldPortal.class);
                if (portal != null) {
                    upsertPortalEntry(portalKey(portal));
                }
            }
            case "deleteportal" -> removePortalEntry(resultDataText(result, "portalId"));
            case "createinventorygroup", "updateinventorygroup" -> {
                WorldInventoryGroup group = resultData(result, "group", WorldInventoryGroup.class);
                if (group != null && !safeText(group.getGroupId()).isBlank()) {
                    upsertInventoryGroupEntry(group.getGroupId());
                }
            }
            case "deleteinventorygroup" -> removeInventoryGroupEntry(resultDataText(result, "groupId"));
            case "createsignportal" -> {
                WorldSignPortal signPortal = resultData(result, "signPortal", WorldSignPortal.class);
                if (signPortal != null && !safeText(signPortal.getSignId()).isBlank()) {
                    upsertSignPortalEntry(signPortal.getSignId());
                }
            }
            case "deletesignportal" -> removeSignPortalEntry(resultDataText(result, "signId"));
            default -> {
            }
        }
    }

    private void showWorldPurgeResultPopup(WorldOperationResult result) {
        String worldName = resultWorldName(result);
        int removed = parseResultInt(result.getData().get("removed"), 0);
        PopupWidget.Builder builder = new PopupWidget.Builder(worldName.isBlank() ? "Purge Result" : "Purge " + worldName)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(420, 180);
        builder.addRow("World", true, 18, readOnlyLabel(worldName.isBlank() ? "Unknown World" : worldName));
        builder.addRow("Removed", true, 18, readOnlyLabel(String.valueOf(removed)));
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private void showWorldStateResultPopup(WorldOperationResult result) {
        String action = safeText(result.getAction()).trim();
        String worldName = resultWorldName(result);
        PopupWidget.Builder builder = new PopupWidget.Builder(worldName.isBlank() ? formatTitleWords(action) : formatTitleWords(action) + " " + worldName)
            .setResizable(false)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(460, 200);
        builder.addRow("World", true, 18, readOnlyLabel(worldName.isBlank() ? "Unknown World" : worldName));
        if ("deleteWorld".equalsIgnoreCase(action)) {
            builder.addRow("Portals", true, 18, readOnlyLabel(String.valueOf(parseResultInt(result.getData().get("removedPortals"), 0))));
            builder.addRow("Files", true, 18, readOnlyLabel(Boolean.TRUE.equals(result.getData().get("deleteFiles")) ? "Deleted" : "Kept"));
        }
        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private <T> T resultData(WorldOperationResult result, String key, Class<T> type) {
        if (result == null || key == null || key.isBlank() || type == null) {
            return null;
        }
        Object raw = result.getData().get(key);
        if (raw == null) {
            return null;
        }
        try {
            return gson.fromJson(gson.toJsonTree(raw), type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resultDataText(WorldOperationResult result, String... keys) {
        if (result == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = safeText(Objects.toString(result.getData().get(key), "")).trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return safeText(result.getWorldName()).trim();
    }

    private String resultWorldName(WorldOperationResult result) {
        return resultDataText(result, "worldName", "world");
    }

    private void removeWorldEntry(String worldName) {
        String key = findEntryKeyIgnoreCase(worldEntries, worldName);
        if (key == null || worldsContainer == null) {
            return;
        }
        MountableButtonWidget existing = worldEntries.remove(key);
        if (existing != null) {
            worldsContainer.removeWidget(existing);
        }
    }

    private void removePortalEntry(String portalId) {
        String key = findEntryKeyIgnoreCase(portalEntries, portalId);
        if (key == null || portalsContainer == null) {
            return;
        }
        MountableButtonWidget existing = portalEntries.remove(key);
        if (existing != null) {
            portalsContainer.removeWidget(existing);
        }
    }

    private void removeInventoryGroupEntry(String groupId) {
        String key = findEntryKeyIgnoreCase(inventoryGroupEntries, groupId);
        if (key == null || inventoryGroupsContainer == null) {
            return;
        }
        MountableButtonWidget existing = inventoryGroupEntries.remove(key);
        if (existing != null) {
            inventoryGroupsContainer.removeWidget(existing);
        }
    }

    private void removeSignPortalEntry(String signId) {
        String key = findEntryKeyIgnoreCase(signPortalEntries, signId);
        if (key == null || signPortalsContainer == null) {
            return;
        }
        MountableButtonWidget existing = signPortalEntries.remove(key);
        if (existing != null) {
            signPortalsContainer.removeWidget(existing);
        }
    }

    private String findEntryKeyIgnoreCase(Map<String, MountableButtonWidget> entries, String target) {
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

    private IconButton readOnlyLabel(String text) {
        IconButton button = new IconButton.Builder().label(text).autoWidthOnTextChange(true).build();
        button.active = false;
        return button;
    }

    private List<String> onlinePlayerNames() {
        return WorldUiSupport.normalizeUniqueEntries(flowManager.getOnlinePlayerNamesForServer(serverId));
    }

    private List<String> worldNameOptions() {
        List<String> worldNames = new ArrayList<>(flowManager.getWorldsForServer(serverId).keySet());
        worldNames = WorldUiSupport.normalizeUniqueEntries(worldNames);
        worldNames.sort(String.CASE_INSENSITIVE_ORDER);
        return worldNames;
    }

    private List<String> fallbackWorldOptions(String worldName) {
        List<String> options = new ArrayList<>(worldNameOptions());
        options.removeIf(option -> option != null && option.equalsIgnoreCase(worldName));
        return options;
    }

    private String selectDefaultFallbackWorld(String worldName, List<String> fallbackOptions) {
        if (fallbackOptions == null || fallbackOptions.isEmpty()) {
            return "";
        }
        WorldRegistryEntry currentWorld = flowManager.getWorld(serverId, worldName);
        WorldProfileSettings profile = currentWorld == null ? null : currentWorld.getProfileSettings();
        List<String> preferred = new ArrayList<>();
        if (profile != null) {
            preferred.add(safeText(profile.getRespawnWorld()).trim());
            preferred.add(safeText(profile.getLinkedOverworld()).trim());
        }
        preferred.add("world");
        preferred.add("overworld");
        preferred.add("world_overworld");
        preferred.add("spawn");
        preferred.add(fallbackOptions.getFirst());
        for (String candidate : preferred) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            for (String option : fallbackOptions) {
                if (option != null && option.equalsIgnoreCase(candidate)) {
                    return option;
                }
            }
        }
        return fallbackOptions.getFirst();
    }

    private List<String> portalTargetOptions() {
        List<String> options = new ArrayList<>();
        for (WorldPortal portal : flowManager.getWorldPortalsForServer(serverId)) {
            if (portal == null) {
                continue;
            }
            if (!safeText(portal.getPortalId()).isBlank()) {
                options.add(portal.getPortalId());
            }
            if (!safeText(portal.getPortalName()).isBlank()) {
                options.add(portal.getPortalName());
            }
        }
        options = WorldUiSupport.normalizeUniqueEntries(options);
        options.sort(String.CASE_INSENSITIVE_ORDER);
        return options;
    }

    private SelectorWidgets createSelectorWidgets(String placeholder, List<String> options, String initialValue, int inputWidth,
                                                  boolean allowCustomInput, Function<String, String> displayFunction) {
        List<String> mergedOptions = WorldUiSupport.mergeOptions(options, initialValue);
        String selectedValue = safeText(initialValue).trim();
        if (!allowCustomInput && selectedValue.isBlank() && !mergedOptions.isEmpty()) {
            selectedValue = mergedOptions.getFirst();
        }
        TextInputWidget input = new TextInputWidget.Builder()
            .text(selectedValue)
            .placeholder(placeholder)
            .size(inputWidth, 20)
            .build();
        input.active = allowCustomInput;
        DropDownWidget<String> dropdown = new DropDownWidget.Builder<>(mergedOptions)
            .size(180, 18)
            .selectedItem(selectedValue.isBlank() ? (mergedOptions.isEmpty() ? null : mergedOptions.getFirst()) : selectedValue)
            .displayFunction(value -> displayFunction == null ? safeText(value) : safeText(displayFunction.apply(value)))
            .build();
        dropdown.setOnSelectionChanged(selected -> input.setText(safeText(selected)));
        return new SelectorWidgets(input, dropdown);
    }

    private String selectedSelectorValue(SelectorWidgets selector) {
        if (selector == null) {
            return "";
        }
        String inputValue = selector.input == null ? "" : safeText(selector.input.getText()).trim();
        if (selector.input != null && selector.input.active) {
            return inputValue;
        }
        if (!inputValue.isBlank()) {
            return inputValue;
        }
        return selector.dropdown == null ? "" : safeText(selector.dropdown.getSelectedItem()).trim();
    }

    private void showWorldWhoPopup(WorldOperationResult result) {
        String worldName = safeText(result.getWorldName()).trim();
        if (worldName.isBlank()) {
            worldName = safeText(String.valueOf(result.getData().getOrDefault("worldName", ""))).trim();
        }
        List<Map<String, Object>> players = extractWhoPlayers(result);
        PopupWidget.Builder builder = new PopupWidget.Builder(worldName.isBlank() ? "World Players" : "Players In " + worldName)
            .setResizable(true)
            .setAntiOutOfBound(true)
            .setBoundOffset(desktopMode ? 35 : 0)
            .size(620, Math.max(220, Math.min(520, 120 + players.size() * 28)));

        IconButton worldButton = new IconButton.Builder()
            .label(worldName.isBlank() ? "Unknown World" : worldName)
            .autoWidthOnTextChange(true)
            .build();
        worldButton.active = false;
        IconButton countButton = new IconButton.Builder()
            .label("Players: " + players.size())
            .autoWidthOnTextChange(true)
            .build();
        countButton.active = false;
        builder.addRow("World", true, 18, worldButton, countButton);

        if (players.isEmpty()) {
            IconButton emptyButton = new IconButton.Builder().label("No Players Online").autoWidthOnTextChange(true).build();
            emptyButton.active = false;
            builder.addRow("", true, 18, emptyButton);
        } else {
            for (Map<String, Object> player : players) {
                String playerName = safeText(String.valueOf(player.getOrDefault("playerName", "Unknown")));
                String gameMode = safeText(String.valueOf(player.getOrDefault("gameMode", "Unknown")));
                double health = parseResultDouble(player.get("health"), 0.0);
                double food = parseResultDouble(player.get("food"), 0.0);
                double x = parseResultDouble(player.get("x"), 0.0);
                double y = parseResultDouble(player.get("y"), 0.0);
                double z = parseResultDouble(player.get("z"), 0.0);

                IconButton playerButton = new IconButton.Builder()
                    .label(playerName + " | " + gameMode + " | Health " + formatDecimal(health) + " | Food " + formatDecimal(food))
                    .autoWidthOnTextChange(true)
                    .build();
                playerButton.active = false;
                IconButton locationButton = new IconButton.Builder()
                    .label(formatDecimal(x) + ", " + formatDecimal(y) + ", " + formatDecimal(z))
                    .autoWidthOnTextChange(true)
                    .build();
                locationButton.active = false;
                builder.addRow("", true, 18, playerButton, locationButton);
            }
        }

        PopupWidget popup = builder.build();
        addDrawableChild(popup);
        popup.show();
    }

    private WorldDashboardEntry findWorldDashboard(String worldName) {
        for (WorldDashboardEntry entry : flowManager.getWorldDashboardForServer(serverId)) {
            if (entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(worldName)) {
                return entry;
            }
        }
        return null;
    }

    private boolean ruleEnabled(WorldRegistryEntry world, String ruleName) {
        String value = world.getGameRules().get(ruleName);
        return Boolean.parseBoolean(value);
    }

    private String prettyRuleName(String ruleName) {
        if (ruleName == null || ruleName.isBlank()) {
            return "Rule";
        }
        return formatTitleWords(ruleName);
    }

    private WorldGeneratorDescriptor findGeneratorDescriptor(List<WorldGeneratorDescriptor> descriptors, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (WorldGeneratorDescriptor descriptor : descriptors) {
            if (descriptor != null && descriptor.getId() != null && descriptor.getId().equalsIgnoreCase(id)) {
                return descriptor;
            }
        }
        return null;
    }

    private boolean profileChanged(WorldProfileSettings oldProfile, WorldProfileSettings newProfile) {
        if (oldProfile == null) {
            return true;
        }
        return !safeText(oldProfile.getAlias()).equals(safeText(newProfile.getAlias()))
            || oldProfile.isHidden() != newProfile.isHidden()
            || !safeText(oldProfile.getAccessPermission()).equals(safeText(newProfile.getAccessPermission()))
            || !safeText(oldProfile.getBypassPermission()).equals(safeText(newProfile.getBypassPermission()))
            || !safeText(oldProfile.getRespawnWorld()).equals(safeText(newProfile.getRespawnWorld()))
            || oldProfile.isForceGameMode() != newProfile.isForceGameMode()
            || !safeText(oldProfile.getGameMode()).equalsIgnoreCase(safeText(newProfile.getGameMode()))
            || oldProfile.isCustomSpawnEnabled() != newProfile.isCustomSpawnEnabled()
            || Double.compare(oldProfile.getSpawnX(), newProfile.getSpawnX()) != 0
            || Double.compare(oldProfile.getSpawnY(), newProfile.getSpawnY()) != 0
            || Double.compare(oldProfile.getSpawnZ(), newProfile.getSpawnZ()) != 0
            || Float.compare(oldProfile.getSpawnYaw(), newProfile.getSpawnYaw()) != 0
            || Float.compare(oldProfile.getSpawnPitch(), newProfile.getSpawnPitch()) != 0
            || oldProfile.isEntryFeeEnabled() != newProfile.isEntryFeeEnabled()
            || Double.compare(oldProfile.getEntryFee(), newProfile.getEntryFee()) != 0
            || oldProfile.isPvpEnabled() != newProfile.isPvpEnabled()
            || oldProfile.isKeepSpawnLoaded() != newProfile.isKeepSpawnLoaded()
            || oldProfile.isAutoSaveEnabled() != newProfile.isAutoSaveEnabled()
            || oldProfile.isAnimalSpawnsEnabled() != newProfile.isAnimalSpawnsEnabled()
            || oldProfile.isMonsterSpawnsEnabled() != newProfile.isMonsterSpawnsEnabled()
            || oldProfile.isHungerEnabled() != newProfile.isHungerEnabled()
            || oldProfile.isAutoHealEnabled() != newProfile.isAutoHealEnabled()
            || oldProfile.isBedRespawnEnabled() != newProfile.isBedRespawnEnabled()
            || oldProfile.isAnchorRespawnEnabled() != newProfile.isAnchorRespawnEnabled()
            || oldProfile.isNonLivingEntitySpawnsEnabled() != newProfile.isNonLivingEntitySpawnsEnabled()
            || !safeText(oldProfile.getArrivalMessage()).equals(safeText(newProfile.getArrivalMessage()))
            || !safeText(oldProfile.getDenyMessage()).equals(safeText(newProfile.getDenyMessage()))
            || !safeText(oldProfile.getInventoryGroupId()).equals(safeText(newProfile.getInventoryGroupId()))
            || !safeText(oldProfile.getLinkedNetherWorld()).equals(safeText(newProfile.getLinkedNetherWorld()))
            || !safeText(oldProfile.getLinkedEndWorld()).equals(safeText(newProfile.getLinkedEndWorld()))
            || !safeText(oldProfile.getLinkedOverworld()).equals(safeText(newProfile.getLinkedOverworld()))
            || Double.compare(oldProfile.getNetherScale(), newProfile.getNetherScale()) != 0
            || Double.compare(oldProfile.getEndScale(), newProfile.getEndScale()) != 0
            || oldProfile.isAutoLinkNetherPortal() != newProfile.isAutoLinkNetherPortal()
            || oldProfile.isAutoLinkEndPortal() != newProfile.isAutoLinkEndPortal();
    }

    private WorldInventoryGroup findInventoryGroup(List<WorldInventoryGroup> inventoryGroups, String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return null;
        }
        for (WorldInventoryGroup inventoryGroup : inventoryGroups) {
            if (inventoryGroup != null && inventoryGroup.getGroupId() != null && inventoryGroup.getGroupId().equalsIgnoreCase(groupId)) {
                return inventoryGroup;
            }
        }
        return null;
    }

    private String formatInventoryGroupOption(List<WorldInventoryGroup> inventoryGroups, String groupId) {
        if (groupId == null || groupId.isBlank() || "None".equalsIgnoreCase(groupId)) {
            return "None";
        }
        WorldInventoryGroup inventoryGroup = findInventoryGroup(inventoryGroups, groupId);
        if (inventoryGroup == null) {
            return groupId;
        }
        String displayName = safeText(inventoryGroup.getDisplayName()).trim();
        return displayName.isBlank() || displayName.equalsIgnoreCase(groupId) ? groupId : displayName + " · " + groupId;
    }

    private void addPopupSectionRow(PopupWidget.Builder builder, Map<String, List<String>> sectionRows, String section,
                                    String rowId, String label, int height, Widget... widgets) {
        builder.addRow(rowId, label, true, height, widgets);
        sectionRows.computeIfAbsent(section, ignored -> new ArrayList<>()).add(rowId);
    }

    private void updatePopupSectionVisibility(PopupWidget popup, Map<String, List<String>> sectionRows, String activeSection) {
        if (popup == null || sectionRows == null || activeSection == null || activeSection.isBlank()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : sectionRows.entrySet()) {
            boolean visible = entry.getKey().equalsIgnoreCase(activeSection);
            for (String rowId : entry.getValue()) {
                popup.setRowVisibility(rowId, visible);
            }
        }
    }

    private String describeInventoryGroupSelection(List<WorldInventoryGroup> inventoryGroups, String groupId) {
        String normalized = safeText(groupId).trim();
        if (normalized.isBlank() || "None".equalsIgnoreCase(normalized)) {
            return "No group means this world keeps its own player data.";
        }
        WorldInventoryGroup group = findInventoryGroup(inventoryGroups, normalized);
        if (group == null) {
            return normalized;
        }
        List<String> members = group.getWorlds();
        String worldSummary = members.isEmpty() ? "No linked worlds yet" : String.join(", ", members);
        return "Shared with: " + worldSummary;
    }

    private String appendCommaSeparatedValue(String currentValue, String newValue) {
        List<String> values = parseCommaSeparatedList(currentValue);
        values.add(newValue);
        return String.join(", ", WorldUiSupport.normalizeUniqueEntries(values));
    }

    private List<String> parseCommaSeparatedList(String value) {
        List<String> values = new ArrayList<>();
        for (String part : safeText(value).split(",")) {
            String trimmed = safeText(part).trim();
            if (!trimmed.isBlank() && !values.contains(trimmed)) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private String summarizeInventoryGroupShares(WorldInventoryGroup group) {
        List<String> parts = new ArrayList<>();
        if (group.isShareInventory()) {
            parts.add("Inventory");
        }
        if (group.isShareArmor()) {
            parts.add("Armor");
        }
        if (group.isShareOffhand()) {
            parts.add("Offhand");
        }
        if (group.isShareEnderChest()) {
            parts.add("Ender Chest");
        }
        if (group.isShareHealth()) {
            parts.add("Health");
        }
        if (group.isShareHunger()) {
            parts.add("Hunger");
        }
        if (group.isShareExperience()) {
            parts.add("Experience");
        }
        if (group.isShareGameMode()) {
            parts.add("Game Mode");
        }
        if (group.isSharePotionEffects()) {
            parts.add("Potions");
        }
        if (group.isShareLastLocation()) {
            parts.add("Last Position");
        }
        if (group.isShareBedSpawn()) {
            parts.add("Bed Spawn");
        }
        return parts.isEmpty() ? "No Shared Data" : String.join(", ", parts);
    }

    private String formatTitleWords(String value) {
        String safe = safeText(value).trim();
        if (safe.isBlank()) {
            return "";
        }
        String normalized = safe.replace('_', ' ').replace('-', ' ');
        normalized = normalized.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        StringBuilder result = new StringBuilder();
        for (String part : normalized.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            if (part.equalsIgnoreCase("pvp")) {
                result.append("PVP");
            } else {
                result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return result.toString();
    }

    private List<Map<String, Object>> extractWhoPlayers(WorldOperationResult result) {
        List<Map<String, Object>> players = new ArrayList<>();
        Object rawPlayers = result.getData().get("players");
        if (!(rawPlayers instanceof List<?> rawList)) {
            return players;
        }
        for (Object rawEntry : rawList) {
            if (!(rawEntry instanceof Map<?, ?> rawMap)) {
                continue;
            }
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            for (Map.Entry<?, ?> mapEntry : rawMap.entrySet()) {
                if (mapEntry.getKey() == null) {
                    continue;
                }
                entry.put(String.valueOf(mapEntry.getKey()), mapEntry.getValue());
            }
            players.add(entry);
        }
        players.sort(Comparator.comparing(player -> safeText(String.valueOf(player.getOrDefault("playerName", ""))), String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    private double parseResultDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return parseDouble(Objects.toString(value, ""), fallback);
    }

    private int parseResultInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return parseInt(Objects.toString(value, ""), fallback);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Integer parseNullableInt(String value) {
        String trimmed = safeText(value).trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseNullableLong(String value) {
        String trimmed = safeText(value).trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parseNullableDouble(String value) {
        String trimmed = safeText(value).trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Float parseNullableFloat(String value) {
        String trimmed = safeText(value).trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            return Float.parseFloat(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String portalKey(WorldPortal portal) {
        if (portal == null) {
            return "";
        }
        return !safeText(portal.getPortalId()).isBlank() ? portal.getPortalId() : safeText(portal.getPortalName());
    }

    private String formatDecimal(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public void refresh() {
        if (tabsManager != null) {
             onTabSelected(tabsManager.getActiveTab());
        }
    }

    public String getServerId() {
        return serverId;
    }

    public static FlowManagerScreen getOpenScreen(String serverId) {
        return OPEN_SCREENS.get(serverId);
    }

    @Override
    public void close() {
        if (OPEN_SCREENS.get(serverId) == this) {
            OPEN_SCREENS.remove(serverId);
        }
        super.close();
        ScreenManager.getInstance().setScreen(parent);
    }

}
