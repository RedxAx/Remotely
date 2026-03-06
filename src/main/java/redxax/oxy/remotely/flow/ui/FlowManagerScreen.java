package redxax.oxy.remotely.flow.ui;

import com.google.gson.Gson;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
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
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private Container tabsContainer;
    private final Map<String, MountableButtonWidget> blueprintEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> guiEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> scoreboardEntries = new HashMap<>();
    private final Map<String, MountableButtonWidget> tabEntries = new HashMap<>();
    private final Gson gson = new Gson();

    private static class CommandBindingContext {
        private String command;
        private List<String> subcommands;
        private Boolean structured;
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
        if (tabMethodsAvailable) {
            tabsContainer = createContainer("tabs", 5, contentY, width - 10, contentHeight);
            tabsContainer.layout(new ManagedLayout()).columns(1).padding(5).scrolling(true);
        }

        tabsManager.addTab("Blueprints", blueprintsContainer);
        tabsManager.addTab("GUIs", guisContainer);
        tabsManager.addTab("Scoreboards", scoreboardsContainer);
        if (tabMethodsAvailable && tabsContainer != null) {
            tabsManager.addTab("Tabs", tabsContainer);
        }

        rebuildBlueprints();
        rebuildGuis();
        rebuildScoreboards();
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
