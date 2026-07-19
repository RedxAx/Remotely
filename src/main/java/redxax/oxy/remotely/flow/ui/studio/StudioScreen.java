package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.data.flow.DesignerSaveNotifications;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.ui.AdvancementDesignerScreen;
import redxax.oxy.remotely.flow.ui.ChatDesignerScreen;
import redxax.oxy.remotely.flow.ui.ContentDesignerScreen;
import redxax.oxy.remotely.flow.ui.DialogDesignerScreen;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import redxax.oxy.remotely.flow.ui.LootTableDesignerScreen;
import redxax.oxy.remotely.flow.ui.MessageRuleDesignerScreen;
import redxax.oxy.remotely.flow.ui.MotdDesignerScreen;
import redxax.oxy.remotely.flow.ui.NpcDesignerScreen;
import redxax.oxy.remotely.flow.ui.RecipeDesignerScreen;
import redxax.oxy.remotely.flow.ui.ScoreboardDesignerScreen;
import redxax.oxy.remotely.flow.ui.StudioCloseHandledScreen;
import redxax.oxy.remotely.flow.ui.TabDesignerScreen;
import redxax.oxy.remotely.flow.ui.TextTemplateDesignerScreen;
import redxax.oxy.remotely.flow.ui.TradeDesignerScreen;
import redxax.oxy.remotely.flow.ui.WorldDesignerScreen;
import redxax.oxy.remotely.flow.ui.marketplace.ReSyncMarketplaceScreen;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.ui.WorldGenEditorScreen;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.IconMessage;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StudioScreen extends StudioInfiniteScreen {
    protected boolean studioMode;
    protected TabsManager studioTabsManager;
    protected ReSyncContentBrowserWidget studioContentBrowser;
    protected SidePanel studioResourcePanel;
    protected StudioPanel studioResourceStudioPanel;
    protected final ReSyncStudioPanelState studioPanelState = new ReSyncStudioPanelState();
    protected final List<AnimatedWidget> studioResourcePanelWidgets = new ArrayList<>();
    protected String studioResourcePanelKey = "";
    protected final Map<String, TextInputWidget> studioResourcePanelInputs = new HashMap<>();
    protected final Map<String, ToggleWidget> studioResourcePanelToggles = new HashMap<>();
    protected IconMessage studioEmptyMessage;
    protected final List<StudioDocument> studioDocuments = new ArrayList<>();
    protected StudioDocument activeStudioDocument;
    protected final FlowGraph studioEmptyGraph = new FlowGraph();
    protected boolean syncingStudioTabSelection;
    protected String activeNodeRegistryServerId;
    protected final Gson gson = new Gson();
    protected TextInputWidget commandLabelInput;
    protected final List<TextInputWidget> commandPathInputs = new ArrayList<>();
    protected ToggleWidget commandStructuredToggle;
    protected final List<AnimatedWidget> headerButtons = new ArrayList<>();
    protected final List<AnimatedWidget> activeViewHeaderButtons = new ArrayList<>();
    protected ItemSelectorWidget activeStudioSelector;
    protected boolean fullEditorHeaderCloseRequested;

    protected static class CommandBindingContext {
        public CommandBindingContext() {
        }

        public String command;
        public List<String> subcommands;
        public Boolean structured;
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        return handleStudioHistoryShortcut(event);
    }

    public void openWorkspaceResource(String type, String id) {
        if (type == null || type.isBlank() || id == null || id.isBlank()) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        ReSyncProjectMetadata.ResourceEntry resource = manager.getProjectMetadata(studioServerId()).findResource(type, id);
        if (resource != null) {
            openProjectResource(resource);
            return;
        }
        ReSyncResourceType jsonType = ReSyncResourceType.byTypeId(type);
        if (jsonType != null) {
            if (jsonType == ReSyncResourceType.CUSTOM_CONTENT) {
                CustomContentDefinition content = manager.getCustomContentForServer(studioServerId()).get(id);
                if (content == null) {
                    manager.ensureFlowClient(studioServerId()).requestResource(jsonType, id, true);
                    new Notification("Open Resource", "Loading " + id, Notification.Type.INFO);
                    return;
                }
                String graphId = content.getFlowId() != null && !content.getFlowId().isBlank() ? content.getFlowId() : id;
                FlowGraph contentGraph = content.getGraph() != null ? content.getGraph() : manager.getFlowsForServer(studioServerId()).get(graphId);
                if (contentGraph == null) {
                    manager.ensureFlowClient(studioServerId()).requestResource(jsonType, id, true);
                    new Notification("Open Resource", "Loading " + id, Notification.Type.INFO);
                    return;
                }
                openStudioViewDocument(type, id, content.getDisplayName(), contentGraph,
                    new ScreenBackedStudioView(this, new ContentDesignerScreen(studioServerId(), contentGraph, this)));
                return;
            }
            if (jsonType == ReSyncResourceType.GUI) {
                if (manager.getGuisForServer(studioServerId()).containsKey(id)) {
                    openStudioDesigner(type, id);
                } else {
                    manager.ensureFlowClient(studioServerId()).requestResource(jsonType, id, true);
                }
                return;
            }
            if (jsonType == ReSyncResourceType.SCOREBOARD) {
                if (manager.getScoreboardsForServer(studioServerId()).containsKey(id)) {
                    openStudioDesigner(type, id);
                } else {
                    manager.ensureFlowClient(studioServerId()).requestResource(jsonType, id, true);
                }
                return;
            }
            if (jsonType == ReSyncResourceType.TAB) {
                if (manager.getTabsForServer(studioServerId()).containsKey(id)) {
                    openStudioDesigner(type, id);
                } else {
                    manager.ensureFlowClient(studioServerId()).requestResource(jsonType, id, true);
                }
                return;
            }
            JsonObject json = manager.getJsonResourcesForServer(studioServerId(), jsonType).get(id);
            if (json == null) {
                manager.ensureFlowClient(studioServerId()).requestResource(jsonType, id, true);
                new Notification("Open Resource", "Loading " + id, Notification.Type.INFO);
                return;
            }
            if (ReSyncResourceDragPayload.ADVANCEMENT_TREE.equals(type) || ReSyncResourceDragPayload.DIALOG.equals(type)) {
                openStudioDesigner(type, id);
                return;
            }
            openFocusedResourceDocument(type, id, id, json);
            return;
        }
        if (ReSyncResourceDragPayload.WORLD.equals(type)) {
            openStudioWorldDocument(id, id);
            return;
        }
        new Notification("Open Resource", "No Designer For " + type, Notification.Type.ERROR);
    }

    public void openWorkspaceFlowEditor(String flowId, String branchPin) {
    }

    public void openWorkspaceFlowEditor(String flowId) {
        openWorkspaceFlowEditor(flowId, null);
    }

    public void openWorkspaceDesigner(String type, String id, boolean fullEditor) {
        openStudioDesigner(type, id, fullEditor);
    }

    public void openWorkspaceContentDesigner(String id, String title, FlowGraph graph) {
        openWorkspaceContentDesigner(id, title, graph, null, true);
    }

    public void openWorkspaceContentDesigner(String id, String title, FlowGraph graph, ContentDesignerScreen screen, boolean persistDocument) {
        if (id == null || id.isBlank() || graph == null || graph.getId() == null || graph.getId().isBlank()) {
            return;
        }
        String documentKey = ReSyncProjectMetadata.resourceKey(ReSyncResourceDragPayload.CUSTOM_CONTENT, id);
        if (persistDocument && studioDocuments.stream().anyMatch(document -> document.key().equals(documentKey))) {
            selectStudioDocument(documentKey);
            return;
        }
        String displayTitle = title != null && !title.isBlank() ? title : id;
        openStudioViewDocument(
            ReSyncResourceDragPayload.CUSTOM_CONTENT,
            id,
            displayTitle,
            graph,
            new ScreenBackedStudioView(this, screen != null ? screen : new ContentDesignerScreen(studioServerId(), graph, this)),
            !persistDocument,
            persistDocument
        );
    }

    public void refreshStudioWorkspace() {
        refreshStudioWorkspace(true);
    }

    public void refreshStudioWorkspace(boolean rebuildContentBrowser) {
        if (rebuildContentBrowser) {
            refreshStudioContentBrowser();
        }
        refreshStudioResourcePanel();
    }

    public void refreshStudioContentBrowserOnly() {
        refreshStudioContentBrowser();
    }

    protected String studioServerId() {
        return "";
    }

    protected String activeStudioDocumentKey() {
        return activeStudioDocument != null ? activeStudioDocument.key() : "none";
    }

    public boolean isStudioMode() {
        return studioMode;
    }

    protected void createStudioContentBrowser() {
        studioContentBrowser = new ReSyncContentBrowserWidget(this, 0, 0, Math.max(0, width), height);
        studioContentBrowser.resetToDefaultHeight();
        studioContentBrowser.clampHeight();
        studioContentBrowser.layoutInScreen();
    }

    protected void createStudioWorkspaceChrome() {
        createStudioWorkspaceChrome(true);
    }

    protected void createStudioWorkspaceChrome(boolean includeWorkspacePanels) {
        studioTabsManager = tabs().builder()
            .position(10, 10)
            .size(width - 220, 18)
            .allowAdd(false)
            .allowClose(true)
            .allowReorder(true)
            .onTabContextMenu(this::showStudioTabMenu)
            .onTabClosed(tab -> {
                Object data = tab.getData();
                if (data instanceof String key) {
                    studioDocuments.removeIf(document -> {
                        boolean match = document.key().equals(key);
                        if (match && document.view() != null) {
                            document.view().closed();
                        }
                        return match;
                    });
                    if (studioDocuments.isEmpty()) {
                        clearActiveStudioDocument();
                    } else if (activeStudioDocument != null && activeStudioDocument.key().equals(key)) {
                        selectStudioDocument(studioDocuments.getFirst().key());
                    } else {
                        refreshActiveViewHeaderButtons();
                        refreshStudioResourcePanel();
                    }
                }
            })
            .onTabSelected(tab -> {
                if (syncingStudioTabSelection) {
                    return;
                }
                Object data = tab.getData();
                if (data instanceof String key) {
                    selectStudioDocument(key);
                }
            })
            .build();
        if (includeWorkspacePanels) {
            ensureStudioWorkspacePanels(false);
        }
        syncStudioDocumentTabs();
        clearActiveStudioDocument();
    }

    private void showStudioTabMenu(TabsManager.Tab tab) {
        if (tab == null || tab.getWidget() == null) {
            return;
        }
        ContextMenuWidget.Builder menu = new ContextMenuWidget.Builder(this)
            .addIconItem("Close", "close.png", () -> closeStudioTab(tab), "Close Tab")
            .addIconItem("Close Others", "delete.png", () -> closeOtherStudioTabs(tab), "Keep This Tab")
            .addIconItem("Close All", "close.png", this::closeAllStudioTabs, "Close All Tabs");
        showStudioContextMenu(tab.getWidget().getX(), tab.getWidget().getY() + tab.getWidget().getHeight() + 2, menu);
    }

    private void closeStudioTab(TabsManager.Tab tab) {
        if (studioTabsManager == null) {
            return;
        }
        int index = studioTabsManager.getTabs().indexOf(tab);
        if (index >= 0) {
            studioTabsManager.removeTab(index);
        }
    }

    private void closeOtherStudioTabs(TabsManager.Tab retained) {
        if (studioTabsManager == null) {
            return;
        }
        List<TabsManager.Tab> tabs = studioTabsManager.getTabs();
        for (int index = tabs.size() - 1; index >= 0; index--) {
            if (tabs.get(index) != retained) {
                studioTabsManager.removeTab(index);
            }
        }
    }

    private void closeAllStudioTabs() {
        if (studioTabsManager == null) {
            return;
        }
        for (int index = studioTabsManager.getTabs().size() - 1; index >= 0; index--) {
            studioTabsManager.removeTab(index);
        }
    }

    protected void ensureStudioWorkspacePanels(boolean collapseContentBrowser) {
        if (studioContentBrowser == null) {
            createStudioContentBrowser();
        }
        if (collapseContentBrowser && studioContentBrowser != null) {
            studioContentBrowser.collapse();
        }
        if (studioResourceStudioPanel == null) {
            studioResourceStudioPanel = rightStudioPanel("studioResourcePanel")
                .show();
            studioResourcePanel = studioResourceStudioPanel.sidePanel();
            studioResourceStudioPanel.padding(studioPanelState.padding());
            studioResourcePanel.hide();
        }
    }

    protected void refreshStudioContentBrowser() {
        if (studioContentBrowser != null) {
            studioContentBrowser.rebuild();
        }
    }

    protected void updateStudioLayout() {
        if (studioTabsManager != null) {
            studioTabsManager.setPosition(10, 5);
            studioTabsManager.setSize(Math.max(80, width - studioHeaderRightReserve() - 20), 18);
        }
        if (studioContentBrowser != null) {
            studioContentBrowser.clampHeight();
            studioContentBrowser.layoutInScreen();
        }
        if (studioResourcePanel != null) {
            int previousRowWidth = studioResourceStudioPanel != null ? studioResourceStudioPanel.rowWidth() : studioPanelState.rowWidth();
            if (studioResourceStudioPanel != null) {
                studioResourceStudioPanel.layout();
            }
            int currentRowWidth = studioResourceStudioPanel != null ? studioResourceStudioPanel.rowWidth() : studioPanelState.rowWidth();
            if (previousRowWidth != currentRowWidth) {
                handleStudioResourcePanelRowWidthChanged(previousRowWidth, currentRowWidth);
            }
        }
        for (StudioDocument document : studioDocuments) {
            if (document.view() != null) {
                document.view().resize(width, studioEditorHeight());
            }
        }
    }

    protected void handleStudioResourcePanelRowWidthChanged(int previousRowWidth, int currentRowWidth) {
    }

    protected int studioHeaderRightReserve() {
        int reserve = 0;
        List<AnimatedWidget> buttons = visibleStudioHeaderButtons();
        int visibleCount = 0;
        for (AnimatedWidget button : buttons) {
            if (button != null) {
                reserve += button.getWidth();
                visibleCount++;
            }
        }
        return reserve + visibleCount * 5;
    }

    protected int studioEditorHeight() {
        return studioContentBrowser != null ? studioContentBrowser.editorHeight() : height;
    }

    protected boolean studioContentBrowserAffectsLayout() {
        return false;
    }

    @Override
    protected int studioPanelBottomReserve() {
        return super.studioPanelBottomReserve();
    }

    protected void startTopHeaderOpeningAnimation() {
        header().offset(0, -header().headerSize).animateOffsetTo(0, 0);
    }

    protected void startTopHeaderClosingAnimation() {
        header().animateOffsetTo(0, -header().headerSize - 5);
    }

    protected void updateTopHeaderAnimation() {
        header().updateOffsetAnimation();
    }

    protected boolean isTopHeaderAnimationFinished() {
        return header().isOffsetAnimationFinished();
    }

    protected int topHeaderContentTop(int spacing) {
        return Math.clamp(header().headerSize + header().getOffsetY(), 0, header().headerSize) + spacing;
    }

    protected int screenWidth() {
        return width;
    }

    protected int screenHeight() {
        return height;
    }

    public int studioContentBrowserWidth() {
        return studioContentBrowser != null ? studioContentBrowser.visibleLayoutWidth() : 0;
    }

    public int studioContentBrowserPanelWidth() {
        if (studioContentBrowser == null || studioContentBrowser.sidePanel() == null || !studioContentBrowser.sidePanel().isVisible()) {
            return 0;
        }
        return studioContentBrowser.sidePanel().getDesiredWidth();
    }

    public void setStudioContentBrowserTemporarilyHidden(boolean hidden) {
        if (studioContentBrowser != null) {
            studioContentBrowser.setTemporarilyHidden(hidden);
        }
    }

    public boolean isStudioContentBrowserTemporarilyHidden() {
        return studioContentBrowser != null && studioContentBrowser.isTemporarilyHidden();
    }

    protected void showStudioContextMenu(int mouseX, int mouseY, ContextMenuWidget.Builder builder) {
        showContextMenu(mouseX, mouseY, builder);
    }

    protected void refreshStudioLayoutPositions() {
        updatePositions();
    }

    protected void clearStudioFocus() {
        setFocusedWidget(null);
    }

    protected void openReSyncMarketplace() {
        ScreenManager.getInstance().setScreen(new ReSyncMarketplaceScreen(this, studioServerId()));
    }

    public boolean hasReSyncUpdateAvailable() {
        return false;
    }

    public boolean isReSyncUpdateRunning() {
        return false;
    }

    public void updateReSyncFromContentBrowser() {
    }

    protected void openProjectResource(ReSyncProjectMetadata.ResourceEntry resource) {
        openStudioResource(resource);
    }

    protected void openStudioResource(ReSyncProjectMetadata.ResourceEntry resource) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || resource == null) {
            return;
        }
        if (ReSyncResourceDragPayload.FLOW.equals(resource.getType()) || ReSyncResourceDragPayload.FUNCTION.equals(resource.getType()) || ReSyncResourceDragPayload.COMMAND.equals(resource.getType())) {
            FlowGraph targetGraph = ReSyncResourceDragPayload.COMMAND.equals(resource.getType())
                ? manager.resolveCommandFlowGraph(studioServerId(), resource.getId())
                : manager.getFlowsForServer(studioServerId()).get(resource.getId());
            if (targetGraph == null && ReSyncResourceDragPayload.COMMAND.equals(resource.getType())) {
                if (manager.isCommandFlowIdentityBlocked(studioServerId(), resource.getId())) {
                    new Notification("Command", "ID Conflicts With Content", Notification.Type.ERROR);
                    return;
                }
                targetGraph = manager.createFlow(studioServerId(), resource.getId(), false, "Command");
                manager.saveFlow(studioServerId(), targetGraph);
                manager.setCommandBinding(studioServerId(), resource.getId(), resource.getId());
            }
            if (targetGraph != null) {
                openStudioGraphDocument(resource.getType(), resource.getId(), resource.getDisplayName(), detachedGraph(targetGraph));
            } else {
                manager.openFlowEditor(studioServerId(), null, resource.getId());
            }
            return;
        }
        if (ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(resource.getType())) {
            String graphId = resource.getId();
            CustomContentDefinition content = manager.getCustomContentForServer(studioServerId()).get(resource.getId());
            if (content != null && content.getFlowId() != null && !content.getFlowId().isBlank()) {
                graphId = content.getFlowId();
            }
            FlowGraph contentGraph = content != null ? content.getGraph() : null;
            if (contentGraph == null) {
                contentGraph = manager.getFlowsForServer(studioServerId()).get(graphId);
            }
            if (contentGraph == null) {
                manager.ensureFlowClient(studioServerId()).requestResource(ReSyncResourceType.CUSTOM_CONTENT, resource.getId(), true);
                new Notification("Open Resource", "Loading " + resource.getId(), Notification.Type.INFO);
                return;
            }
            openStudioViewDocument(resource.getType(), resource.getId(), resource.getDisplayName(), contentGraph, new ScreenBackedStudioView(this, new ContentDesignerScreen(studioServerId(), contentGraph, this)));
            return;
        }
        if (ReSyncResourceDragPayload.WORLDGEN.equals(resource.getType())) {
            WorldGenManager worldGenManager = WorldGenManager.getInstance();
            WorldGenProject project = worldGenManager.getCachedProject(studioServerId(), resource.getId());
            openStudioWorldGenDocument(resource.getId(), resource.getDisplayName(), project);
            return;
        }
        if (ReSyncResourceDragPayload.GUI.equals(resource.getType())
            || ReSyncResourceDragPayload.SCOREBOARD.equals(resource.getType())
            || ReSyncResourceDragPayload.TAB.equals(resource.getType())) {
            openStudioDesigner(resource.getType(), resource.getId());
            return;
        }
        ReSyncResourceType jsonType = ReSyncResourceType.byTypeId(resource.getType());
        if (jsonType != null) {
            JsonObject json = manager.getJsonResourcesForServer(studioServerId(), jsonType).get(resource.getId());
            if (json == null) {
                manager.ensureFlowClient(studioServerId()).requestResource(jsonType, resource.getId(), false);
                json = manager.createJsonResource(studioServerId(), jsonType, resource.getId(), resource.getPath());
            }
            if (ReSyncResourceDragPayload.ADVANCEMENT_TREE.equals(resource.getType()) || ReSyncResourceDragPayload.DIALOG.equals(resource.getType())) {
                openStudioDesigner(resource.getType(), resource.getId());
            } else {
                openFocusedResourceDocument(resource.getType(), resource.getId(), resource.getDisplayName(), detachedJson(json));
            }
            return;
        }
        if (ReSyncResourceDragPayload.WORLD.equals(resource.getType())) {
            openStudioWorldDocument(resource.getId(), resource.getDisplayName());
        }
    }

    protected void openStudioGraphDocument(String type, String id, String title, FlowGraph targetGraph) {
        if (targetGraph == null) {
            return;
        }
        addStudioDocument(type, id, title == null || title.isBlank() ? id : title, targetGraph, null);
        syncStudioDocumentTabs();
        selectStudioDocument(ReSyncProjectMetadata.resourceKey(type, id));
    }

    protected void openStudioDesigner(String type, String id) {
        openStudioDesigner(type, id, false);
    }

    protected void openStudioDesigner(String type, String id, boolean fullEditor) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || id == null) {
            return;
        }
        if (fullEditor) {
            collapseStudioContentBrowser();
        }
        if (ReSyncResourceDragPayload.GUI.equals(type)) {
            GuiDefinition gui = manager.getGuisForServer(studioServerId()).get(id);
            if (gui != null) {
                openStudioViewDocument(type, id, manager.getGuiName(studioServerId(), id), screenBackedStudioView(new GuiDesignerScreen(detachedGui(gui), studioServerId(), this, fullEditor, fullEditor), fullEditor), fullEditor);
            } else {
                manager.openGuiDesigner(studioServerId(), null, id, this, fullEditor);
            }
            return;
        }
        if (ReSyncResourceDragPayload.SCOREBOARD.equals(type)) {
            ScoreboardDefinition scoreboard = manager.getScoreboardsForServer(studioServerId()).get(id);
            if (scoreboard != null) {
                openStudioViewDocument(type, id, manager.getScoreboardName(studioServerId(), id), screenBackedStudioView(new ScoreboardDesignerScreen(detachedScoreboard(scoreboard), studioServerId(), this, fullEditor, fullEditor), fullEditor), fullEditor);
            } else {
                manager.openScoreboardDesigner(studioServerId(), null, id, this, fullEditor);
            }
            return;
        }
        if (ReSyncResourceDragPayload.TAB.equals(type)) {
            TabDefinition tab = manager.getTabsForServer(studioServerId()).get(id);
            if (tab != null) {
                openStudioViewDocument(type, id, manager.getTabName(studioServerId(), id), screenBackedStudioView(new TabDesignerScreen(detachedTab(tab), studioServerId(), this, fullEditor, fullEditor), fullEditor), fullEditor);
            } else {
                manager.openTabDesigner(studioServerId(), null, id, this, fullEditor);
            }
            return;
        }
        if (ReSyncResourceDragPayload.ADVANCEMENT_TREE.equals(type)) {
            JsonObject tree = manager.getJsonResourcesForServer(studioServerId(), ReSyncResourceType.ADVANCEMENT_TREE).get(id);
            if (tree != null) {
                openStudioViewDocument(type, id, ReSyncResourceType.ADVANCEMENT_TREE.extractName(tree), screenBackedStudioView(new AdvancementDesignerScreen(detachedJson(tree), studioServerId(), this, fullEditor, fullEditor), fullEditor), fullEditor);
            }
            return;
        }
        if (ReSyncResourceDragPayload.DIALOG.equals(type)) {
            JsonObject dialog = manager.getJsonResourcesForServer(studioServerId(), ReSyncResourceType.DIALOG).get(id);
            if (dialog != null) {
                openStudioViewDocument(type, id, ReSyncResourceType.DIALOG.extractName(dialog), screenBackedStudioView(new DialogDesignerScreen(detachedJson(dialog), studioServerId(), this, fullEditor, fullEditor), fullEditor), fullEditor);
            } else {
                manager.openDialogDesigner(studioServerId(), id, this, fullEditor);
            }
        }
    }

    protected ScreenBackedStudioView screenBackedStudioView(Screen screen, boolean fullEditor) {
        if (fullEditor && screen instanceof StudioCloseHandledScreen closeHandledScreen) {
            closeHandledScreen.setStudioCloseHandler(this::requestCloseFullEditorStudioScreen);
        }
        return new ScreenBackedStudioView(this, screen, fullEditor);
    }

    protected void requestCloseFullEditorStudioScreen() {
        fullEditorHeaderCloseRequested = true;
        startTopHeaderClosingAnimation();
        if (studioContentBrowser != null) {
            studioContentBrowser.slideOut();
        }
    }

    protected void closeFullEditorStudioScreen() {
        FlowManager manager = FlowManager.getInstance();
        if (manager != null) {
            manager.requestCloseLiveStudioSuperScreen(studioServerId());
        } else {
            ScreenManager.getInstance().execute(() -> ScreenManager.getInstance().setScreen(null));
        }
    }

    protected void collapseStudioContentBrowser() {
        if (studioContentBrowser != null) {
            studioContentBrowser.collapse();
        }
    }

    protected void openStudioWorldGenDocument(String id, String title, WorldGenProject project) {
        WorldGenManager.getInstance().ensureLocalDefinitions(studioServerId());
        WorldGenProject initialProject = project != null ? project : WorldGenManager.getInstance().createProjectTemplate("Continental", id);
        openStudioViewDocument(ReSyncResourceDragPayload.WORLDGEN, id, title, new ScreenBackedStudioView(this, new WorldGenEditorScreen(studioServerId(), null, this, initialProject)));
        if (project == null) {
            WorldGenManager.getInstance().requestProject(studioServerId(), id);
        }
    }

    protected void openStudioWorldDocument(String id, String title) {
        openStudioViewDocument(ReSyncResourceDragPayload.WORLD, id, title == null || title.isBlank() ? id : title, screenBackedStudioView(new WorldDesignerScreen(id, studioServerId(), this), false));
    }

    protected List<ReSyncProjectMetadata.FolderEntry> studioFolders(String parentPath) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(studioServerId());
        return metadata.getFolders().stream()
            .filter(this::isVisibleStudioFolder)
            .filter(folder -> parentPath.equals(folder.getParentPath()))
            .sorted(Comparator.comparingInt(ReSyncProjectMetadata.FolderEntry::getSortOrder).thenComparing(ReSyncProjectMetadata.FolderEntry::getName))
            .toList();
    }

    protected List<ReSyncProjectMetadata.FolderEntry> studioAllFolders() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(studioServerId());
        return metadata.getFolders().stream()
            .filter(this::isVisibleStudioFolder)
            .toList();
    }

    protected List<ReSyncProjectMetadata.ResourceEntry> studioResources(String folderPath) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(studioServerId());
        return metadata.getResources().stream()
            .filter(this::isVisibleStudioResource)
            .filter(resource -> folderPath.equals(resource.getPath()))
            .sorted(Comparator.comparing(ReSyncProjectMetadata.ResourceEntry::getDisplayName))
            .toList();
    }

    protected List<ReSyncProjectMetadata.ResourceEntry> studioAllResources() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return List.of();
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(studioServerId());
        return metadata.getResources().stream()
            .filter(this::isVisibleStudioResource)
            .toList();
    }

    protected boolean isVisibleStudioResource(ReSyncProjectMetadata.ResourceEntry resource) {
        return resource != null;
    }

    protected boolean isVisibleStudioFolder(ReSyncProjectMetadata.FolderEntry folder) {
        return folder != null;
    }

    protected void openFocusedResourceDocument(String type, String id, String title, JsonObject resource) {
        openStudioViewDocument(type, id, title == null || title.isBlank() ? id : title, focusedResourceView(type, id, detachedJson(resource)));
    }

    protected ReSyncStudioView focusedResourceView(String type, String id, JsonObject resource) {
        return switch (type) {
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> new RecipeDesignerScreen(this, id, resource, studioServerId(), this);
            case ReSyncResourceDragPayload.MOTD_PROFILE -> new MotdDesignerScreen(this, id, resource, studioServerId(), this);
            case ReSyncResourceDragPayload.MESSAGE_RULE -> new MessageRuleDesignerScreen(this, id, resource, studioServerId(), this);
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> new TextTemplateDesignerScreen(this, id, resource, studioServerId(), this);
            case ReSyncResourceDragPayload.CHAT -> new ChatDesignerScreen(this, id, resource, studioServerId(), this);
            case ReSyncResourceDragPayload.TRADE_PROFILE -> new TradeDesignerScreen(this, id, resource, studioServerId(), this);
            case ReSyncResourceDragPayload.NPC_DEFINITION -> new NpcDesignerScreen(this, id, resource, studioServerId(), this);
            case ReSyncResourceDragPayload.LOOT_TABLE -> new LootTableDesignerScreen(this, id, resource, studioServerId(), this);
            default -> new TextTemplateDesignerScreen(this, id, resource, studioServerId(), this);
        };
    }

    protected void openStudioViewDocument(String type, String id, String title, ReSyncStudioView view) {
        openStudioViewDocument(type, id, title, null, view);
    }

    protected void openStudioViewDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view) {
        openStudioViewDocument(type, id, title, targetGraph, view, false);
    }

    protected void openStudioViewDocument(String type, String id, String title, ReSyncStudioView view, boolean replaceExisting) {
        openStudioViewDocument(type, id, title, null, view, replaceExisting);
    }

    protected void openStudioViewDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view, boolean replaceExisting) {
        openStudioViewDocument(type, id, title, targetGraph, view, replaceExisting, true);
    }

    protected void openStudioViewDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view, boolean replaceExisting, boolean persistDocument) {
        addStudioDocument(type, id, title == null || title.isBlank() ? id : title, targetGraph, view, replaceExisting, persistDocument);
        syncStudioDocumentTabs();
        selectStudioDocument(ReSyncProjectMetadata.resourceKey(type, id));
    }

    protected void addStudioDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view) {
        addStudioDocument(type, id, title, targetGraph, view, false);
    }

    protected void addStudioDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view, boolean replaceExisting) {
        addStudioDocument(type, id, title, targetGraph, view, replaceExisting, true);
    }

    protected void addStudioDocument(String type, String id, String title, FlowGraph targetGraph, ReSyncStudioView view, boolean replaceExisting, boolean persistDocument) {
        String key = ReSyncProjectMetadata.resourceKey(type, id);
        String documentTitle = persistDocument ? studioDocumentTitle(id, title) : title == null || title.isBlank() ? id : title;
        for (int i = 0; i < studioDocuments.size(); i++) {
            StudioDocument document = studioDocuments.get(i);
            if (document.key().equals(key)) {
                if (replaceExisting) {
                    if (document.view() != null) {
                        document.view().closed();
                    }
                    FlowGraph detachedGraph = detachedGraph(targetGraph);
                    StudioDocument updatedDocument = new StudioDocument(type, id, documentTitle, detachedGraph, view != null ? view : createStudioDocumentView(type, id, detachedGraph), document.viewport());
                    studioDocuments.set(i, updatedDocument);
                    if (activeStudioDocument != null && activeStudioDocument.key().equals(key)) {
                        activeStudioDocument = updatedDocument;
                    }
                } else if (!documentTitle.equals(document.title())) {
                    StudioDocument updatedDocument = new StudioDocument(document.type(), document.id(), documentTitle, document.graph(), document.view(), document.viewport());
                    studioDocuments.set(i, updatedDocument);
                    if (activeStudioDocument != null && activeStudioDocument.key().equals(key)) {
                        activeStudioDocument = updatedDocument;
                    }
                }
                if (persistDocument) {
                    persistOpenStudioDocument(type, id, documentTitle);
                }
                return;
            }
        }
        FlowGraph detachedGraph = detachedGraph(targetGraph);
        StudioDocument document = new StudioDocument(type, id, documentTitle, detachedGraph, view != null ? view : createStudioDocumentView(type, id, detachedGraph), new StudioViewportState());
        studioDocuments.add(document);
        if (persistDocument) {
            persistOpenStudioDocument(type, id, documentTitle);
        }
    }

    protected String studioDocumentTitle(String id, String title) {
        if (id != null && !id.isBlank()) {
            return id;
        }
        return title == null || title.isBlank() ? "" : title;
    }

    protected FlowGraph detachedGraph(FlowGraph graph) {
        return graph != null ? FlowSerializer.deserialize(FlowSerializer.serialize(graph)) : null;
    }

    protected GuiDefinition detachedGui(GuiDefinition gui) {
        return gui != null ? FlowSerializer.deserializeGui(FlowSerializer.serializeGui(gui)) : null;
    }

    protected ScoreboardDefinition detachedScoreboard(ScoreboardDefinition scoreboard) {
        return scoreboard != null ? FlowSerializer.deserializeScoreboard(FlowSerializer.serializeScoreboard(scoreboard)) : null;
    }

    protected TabDefinition detachedTab(TabDefinition tab) {
        return tab != null ? FlowSerializer.deserializeTab(FlowSerializer.serializeTab(tab)) : null;
    }

    protected JsonObject detachedJson(JsonObject json) {
        return json != null ? json.deepCopy() : new JsonObject();
    }

    protected ReSyncStudioView createStudioDocumentView(String type, String id, FlowGraph targetGraph) {
        return null;
    }

    protected void persistOpenStudioDocument(String type, String id, String title) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(studioServerId());
        metadata.addOpenDocument(type, id, title);
        manager.saveProjectMetadata(studioServerId(), metadata, false);
    }

    protected ReSyncStudioView activeStudioView() {
        return studioMode && activeStudioDocument != null ? activeStudioDocument.view() : null;
    }

    protected boolean activeStudioViewUsesResourcePanel() {
        ReSyncStudioView view = activeStudioView();
        return view != null && view.hasPanel();
    }

    protected boolean activeStudioDocumentUsesFlowGraphCanvas() {
        return activeStudioDocument != null && activeStudioDocument.graph() != null;
    }

    protected void refreshActiveViewHeaderButtons() {
        List<AnimatedWidget> nextButtons = new ArrayList<>();
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            nextButtons.addAll(view.headerButtons());
        }
        for (AnimatedWidget button : activeViewHeaderButtons) {
            if (button != null) {
                button.visible = false;
            }
        }
        activeViewHeaderButtons.clear();
        activeViewHeaderButtons.addAll(nextButtons);
        for (AnimatedWidget button : activeViewHeaderButtons) {
            if (button != null) {
                button.entranceAnimationEnabled = false;
            }
        }
        rebuildStudioHeaderButtons();
        if (shouldAnimateActiveStudioHeader(view)) {
            fullEditorHeaderCloseRequested = false;
            startTopHeaderOpeningAnimation();
        } else {
            header().offset(0, 0);
        }
    }

    protected boolean shouldAnimateActiveStudioHeader(ReSyncStudioView view) {
        if (!(view instanceof ScreenBackedStudioView screenView)) {
            return false;
        }
        if (!screenView.fullEditor()) {
            return false;
        }
        Screen screen = screenView.screen();
        return screen instanceof GuiDesignerScreen
            || screen instanceof ScoreboardDesignerScreen
            || screen instanceof TabDesignerScreen
            || screen instanceof AdvancementDesignerScreen
            || screen instanceof DialogDesignerScreen;
    }

    protected void refreshStudioResourcePanel() {
        if (studioResourcePanel == null) {
            return;
        }
        if (activeStudioDocument == null || hidesStudioResourcePanel(activeStudioDocument)) {
            clearStudioResourcePanelWidgets();
            studioResourcePanel.hide();
            return;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null && view.hasPanel()) {
            if (view.preferredPanelPlacement() == StudioPanel.Placement.LEFT) {
                studioResourcePanel.left();
            } else {
                studioResourcePanel.right();
            }
        } else {
            studioResourcePanel.right();
        }
        studioResourcePanel.show();
        if (view != null && view.hasPanel()) {
            view.configurePanel(studioResourceStudioPanel);
        } else if (!buildStudioResourcePanel(activeStudioDocument)) {
            clearStudioResourcePanelWidgets();
            studioResourcePanel.hide();
        }
        studioResourcePanel.container().updateWidgetPositions();
    }

    protected boolean hidesStudioResourcePanel(StudioDocument document) {
        return ReSyncResourceDragPayload.FLOW.equals(document.type())
            || ReSyncResourceDragPayload.FUNCTION.equals(document.type())
            || ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(document.type());
    }

    protected boolean buildStudioResourcePanel(StudioDocument document) {
        if (ReSyncResourceDragPayload.GUI.equals(document.type())) {
            buildGuiResourcePanel();
            return true;
        }
        if (ReSyncResourceDragPayload.COMMAND.equals(document.type())) {
            buildCommandResourcePanel();
            return true;
        }
        if (ReSyncResourceDragPayload.SCOREBOARD.equals(document.type())) {
            buildScoreboardResourcePanel();
            return true;
        }
        if (ReSyncResourceDragPayload.TAB.equals(document.type())) {
            buildTabResourcePanel();
            return true;
        }
        if (ReSyncResourceDragPayload.DIALOG.equals(document.type())) {
            buildDialogResourcePanel();
            return true;
        }
        if (ReSyncResourceDragPayload.WORLDGEN.equals(document.type())) {
            buildWorldGenResourcePanel();
            return true;
        }
        return false;
    }

    protected void buildGuiResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        GuiDefinition gui = manager != null ? manager.getGuisForServer(studioServerId()).get(activeStudioDocument.id()) : null;
        if (gui == null) {
            return;
        }
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            updateStudioPanelInput("title", gui.getTitle());
            updateStudioPanelInput("rows", String.valueOf(gui.getRows()));
            updateStudioPanelToggle("inventory", gui.isExtendToPlayerInventory());
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget title = panelInput("Title", gui.getTitle());
        TextInputWidget rows = panelInput("Rows", String.valueOf(gui.getRows()));
        ToggleWidget playerInventory = new ToggleWidget.Builder()
            .label("Inventory")
            .toggled(gui.isExtendToPlayerInventory())
            .size(studioPanelState.rowWidth(studioResourcePanel), 18)
            .entranceAnimation(false)
            .build();
        rememberStudioPanelInput("title", title);
        rememberStudioPanelInput("rows", rows);
        rememberStudioPanelToggle("inventory", playerInventory);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Title", title, rowWidth, studioResourceDescription("Gui Title")),
            studioPanelState.row("Rows", rows, rowWidth, studioResourceDescription("Gui Rows")),
            studioPanelState.row("Inventory", playerInventory, rowWidth, studioResourceDescription("Gui Inventory")), panelSaveButton(() -> {
            gui.setTitle(title.getText());
            gui.setRows(parseStudioInt(rows.getText(), gui.getRows(), 1, 6));
            gui.setExtendToPlayerInventory(playerInventory.getValue());
            DesignerSaveNotifications.start(studioServerId(), ReSyncResourceType.GUI, gui.getId(), gui.getTitle());
            manager.saveGui(studioServerId(), gui);
        }));
    }

    protected void buildCommandResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        TriggerBinding binding = manager.getCommandBinding(studioServerId(), activeStudioDocument.id());
        CommandBindingContext command = parseCommandContext(binding != null ? binding.getContext() : activeStudioDocument.id());
        buildCommandResourcePanel(command);
    }

    protected void buildScoreboardResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        ScoreboardDefinition scoreboard = manager != null ? manager.getScoreboardsForServer(studioServerId()).get(activeStudioDocument.id()) : null;
        if (scoreboard == null) {
            return;
        }
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            updateStudioPanelInput("title", scoreboard.getTitle());
            updateStudioPanelInput("objective", scoreboard.getObjectiveId());
            updateStudioPanelInput("lines", String.join("|", scoreboard.getLines()));
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget title = panelInput("Title", scoreboard.getTitle());
        TextInputWidget objective = panelInput("Objective", scoreboard.getObjectiveId());
        TextInputWidget lines = panelInput("Lines", String.join("|", scoreboard.getLines()));
        rememberStudioPanelInput("title", title);
        rememberStudioPanelInput("objective", objective);
        rememberStudioPanelInput("lines", lines);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Title", title, rowWidth, studioResourceDescription("Scoreboard Title")),
            studioPanelState.row("Objective", objective, rowWidth, studioResourceDescription("Objective")),
            studioPanelState.row("Lines", lines, rowWidth, studioResourceDescription("Scoreboard Lines")), panelSaveButton(() -> {
            scoreboard.setTitle(title.getText());
            scoreboard.setObjectiveId(objective.getText());
            scoreboard.setLines(parseStudioLines(lines.getText()));
            DesignerSaveNotifications.start(studioServerId(), ReSyncResourceType.SCOREBOARD, scoreboard.getId(), scoreboard.getTitle());
            manager.saveScoreboard(studioServerId(), scoreboard);
        }));
    }

    protected void buildTabResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        TabDefinition tab = manager != null ? manager.getTabsForServer(studioServerId()).get(activeStudioDocument.id()) : null;
        if (tab == null) {
            return;
        }
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            updateStudioPanelInput("header", tab.getHeader());
            updateStudioPanelInput("entry", tab.getEntryFormat());
            updateStudioPanelInput("footer", tab.getFooter());
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget header = panelInput("Header", tab.getHeader());
        TextInputWidget entry = panelInput("Entry", tab.getEntryFormat());
        TextInputWidget footer = panelInput("Footer", tab.getFooter());
        rememberStudioPanelInput("header", header);
        rememberStudioPanelInput("entry", entry);
        rememberStudioPanelInput("footer", footer);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Header", header, rowWidth, studioResourceDescription("Tab Header")),
            studioPanelState.row("Entry", entry, rowWidth, studioResourceDescription("Tab Entry")),
            studioPanelState.row("Footer", footer, rowWidth, studioResourceDescription("Tab Footer")), panelSaveButton(() -> {
            tab.setHeader(header.getText());
            tab.setEntryFormat(entry.getText());
            tab.setFooter(footer.getText());
            DesignerSaveNotifications.start(studioServerId(), ReSyncResourceType.TAB, tab.getId(), tab.getId());
            manager.saveTab(studioServerId(), tab);
        }));
    }

    protected void buildDialogResourcePanel() {
        FlowManager manager = FlowManager.getInstance();
        JsonObject dialog = manager != null ? manager.getJsonResourcesForServer(studioServerId(), ReSyncResourceType.DIALOG).get(activeStudioDocument.id()) : null;
        if (dialog == null) {
            return;
        }
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            updateStudioPanelInput("name", jsonText(dialog, "displayName"));
            updateStudioPanelInput("title", jsonText(dialog, "title"));
            updateStudioPanelInput("type", jsonText(dialog, "type"));
            updateStudioPanelToggle("enabled", jsonBool(dialog, "enabled", true));
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget name = panelInput("Name", jsonText(dialog, "displayName"));
        TextInputWidget title = panelInput("Title", jsonText(dialog, "title"));
        TextInputWidget type = panelInput("Type", jsonText(dialog, "type"));
        ToggleWidget enabled = new ToggleWidget.Builder()
            .label("Enabled")
            .toggled(jsonBool(dialog, "enabled", true))
            .size(studioPanelState.rowWidth(studioResourcePanel), 18)
            .entranceAnimation(false)
            .build();
        rememberStudioPanelInput("name", name);
        rememberStudioPanelInput("title", title);
        rememberStudioPanelInput("type", type);
        rememberStudioPanelToggle("enabled", enabled);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Name", name, rowWidth, studioResourceDescription("Dialog Name")),
            studioPanelState.row("Title", title, rowWidth, studioResourceDescription("Dialog Title")),
            studioPanelState.row("Type", type, rowWidth, studioResourceDescription("Dialog Type")),
            studioPanelState.row("Enabled", enabled, rowWidth, studioResourceDescription("Dialog Enabled")), panelSaveButton(() -> {
            dialog.addProperty("displayName", name.getText());
            dialog.addProperty("title", title.getText());
            dialog.addProperty("type", type.getText());
            dialog.addProperty("enabled", enabled.getValue());
            dialog.remove("pause");
            dialog.remove("external_title");
            DesignerSaveNotifications.start(studioServerId(), ReSyncResourceType.DIALOG, ReSyncResourceType.DIALOG.extractId(dialog), ReSyncResourceType.DIALOG.extractName(dialog));
            manager.saveJsonResource(studioServerId(), ReSyncResourceType.DIALOG, dialog);
        }));
    }

    protected void buildWorldGenResourcePanel() {
        String panelKey = activeStudioDocument.key();
        if (reuseStudioResourcePanel(panelKey)) {
            return;
        }
        setStudioResourcePanelKey(panelKey);
        setStudioResourcePanelWidgets(panelSaveButton(() -> {
            WorldGenProject project = WorldGenManager.getInstance().createProjectTemplate("Continental", activeStudioDocument.id());
            WorldGenManager.getInstance().saveWorldGen(studioServerId(), project);
        }));
    }

    protected void useStudioResourcePanel(StudioPanel panel) {
        studioResourceStudioPanel = panel;
        studioResourcePanel = panel != null ? panel.sidePanel() : null;
    }

    protected void clearStudioResourcePanelWidgets() {
        if (studioResourcePanel == null || studioResourcePanelWidgets.isEmpty()) {
            studioResourcePanelKey = "";
            studioResourcePanelInputs.clear();
            studioResourcePanelToggles.clear();
            return;
        }
        Container container = studioResourcePanel.container();
        for (AnimatedWidget widget : new ArrayList<>(studioResourcePanelWidgets)) {
            container.removeWidget(widget);
        }
        studioResourcePanelWidgets.clear();
        studioResourcePanelKey = "";
        studioResourcePanelInputs.clear();
        studioResourcePanelToggles.clear();
    }

    protected void setStudioResourcePanelWidgets(AnimatedWidget... widgets) {
        if (studioResourcePanel == null) {
            return;
        }
        Container container = studioResourcePanel.container();
        container.beginBatchAdd();
        for (AnimatedWidget widget : widgets) {
            ReSyncStudioPanelState.disableEntrance(widget);
            container.addWidget(widget);
            studioResourcePanelWidgets.add(widget);
        }
        container.endBatchAdd();
    }

    protected boolean reuseStudioResourcePanel(String key) {
        return key != null && key.equals(studioResourcePanelKey);
    }

    protected void setStudioResourcePanelKey(String key) {
        if (!Objects.equals(studioResourcePanelKey, key)) {
            clearStudioResourcePanelWidgets();
            studioResourcePanelKey = key;
        }
    }

    protected void rememberStudioPanelInput(String key, TextInputWidget input) {
        studioResourcePanelInputs.put(key, input);
    }

    protected void rememberStudioPanelToggle(String key, ToggleWidget toggle) {
        studioResourcePanelToggles.put(key, toggle);
    }

    protected void updateStudioPanelInput(String key, String value) {
        TextInputWidget input = studioResourcePanelInputs.get(key);
        if (input != null && !input.isFocused() && !Objects.equals(input.getText(), safeStudioText(value))) {
            input.setText(safeStudioText(value));
        }
    }

    protected void updateStudioPanelToggle(String key, boolean value) {
        ToggleWidget toggle = studioResourcePanelToggles.get(key);
        if (toggle != null && toggle.getValue() != value) {
            toggle.setValue(value);
        }
    }

    protected TextInputWidget panelInput(String placeholder, String value) {
        TextInputWidget input = new TextInputWidget.Builder()
            .placeholder(placeholder)
            .forcePlaceholder(false)
            .text(value != null ? value : "")
            .size(studioPanelState.rowWidth(studioResourcePanel), ReSyncStudioPanelState.FIELD_HEIGHT)
            .build();
        ReSyncStudioPanelState.disableEntrance(input);
        return input;
    }

    protected AnimatedButton panelSaveButton(Runnable action) {
        return studioPanelState.action("Save", studioPanelState.rowWidth(studioResourcePanel), action);
    }

    protected void buildCommandResourcePanel(CommandBindingContext command) {
        String panelKey = activeStudioDocument.key();
        List<String> paths = command.subcommands != null ? new ArrayList<>(command.subcommands) : new ArrayList<>();
        if (paths.isEmpty()) {
            paths.add("");
        }
        if (reuseStudioResourcePanel(panelKey) && commandPathInputs.size() == paths.size()) {
            updateStudioPanelInput("command", command.command != null && !command.command.isBlank() ? command.command : activeStudioDocument.id());
            updateStudioPanelToggle("structured", command.structured != null && command.structured);
            for (int i = 0; i < paths.size(); i++) {
                TextInputWidget input = commandPathInputs.get(i);
                if (input != null && !input.isFocused() && !Objects.equals(input.getText(), paths.get(i))) {
                    input.setText(paths.get(i));
                }
            }
            return;
        }
        clearStudioResourcePanelWidgets();
        commandPathInputs.clear();
        commandLabelInput = null;
        commandStructuredToggle = null;
        studioResourcePanelKey = panelKey;
        commandLabelInput = panelInput("Command", command.command != null && !command.command.isBlank() ? command.command : activeStudioDocument.id());
        commandStructuredToggle = new ToggleWidget.Builder()
            .label("Structured")
            .toggled(command.structured != null && command.structured)
            .size(studioPanelState.rowWidth(studioResourcePanel), 18)
            .entranceAnimation(false)
            .build();
        rememberStudioPanelInput("command", commandLabelInput);
        rememberStudioPanelToggle("structured", commandStructuredToggle);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        List<AnimatedWidget> widgets = new ArrayList<>();
        widgets.add(commandSummaryRow(rowWidth));
        widgets.add(studioPanelState.row("Command", commandLabelInput, rowWidth, studioResourceDescription("Command")));
        widgets.add(studioPanelState.row("Structured", commandStructuredToggle, rowWidth, studioResourceDescription("Structured")));
        for (int i = 0; i < paths.size(); i++) {
            RowWidget pathRow = commandPathEntryRow(paths, i, rowWidth);
            if (i == 0) {
                widgets.add(studioPanelState.row("Paths", pathRow, rowWidth, studioResourceDescription("Paths")));
            } else {
                widgets.add(pathRow);
            }
        }
        widgets.add(new AnimatedButton.Builder()
            .label("Add Path")
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .onClick(() -> {
                CommandBindingContext draft = currentCommandDraft();
                draft.subcommands.add("");
                rebuildCommandResourcePanel(draft);
            })
            .build());
        setStudioResourcePanelWidgets(widgets.toArray(new AnimatedWidget[0]));
    }

    protected MountableButtonWidget commandSummaryRow(int rowWidth) {
        String command = commandLabelInput != null && commandLabelInput.getText() != null && !commandLabelInput.getText().isBlank()
            ? "/" + normalizeCommandLabel(commandLabelInput.getText())
            : "/" + activeStudioDocument.id();
        MountableButtonWidget row = new MountableButtonWidget.Builder("Command")
            .description(command)
            .iconPath("terminal.png")
            .build();
        row.setSize(rowWidth, 30);
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    protected RowWidget commandPathEntryRow(List<String> paths, int index, int rowWidth) {
        String value = paths.get(index);
        String[] pathExamples = {
            "pvp duel <online_player>",
            "report hacker <offline_player>",
            "database getPlayers <player_with_perm:my.permission.node>",
            "trade <online_player>"
        };
        int buttonCount = 1;
        if (index > 0) {
            buttonCount++;
        }
        if (index < paths.size() - 1) {
            buttonCount++;
        }
        int inputWidth = Math.max(120, rowWidth - buttonCount * 18 - 8);
        TextInputWidget pathInput = new TextInputWidget.Builder()
            .text(value)
            .placeholder(pathExamples[index % pathExamples.length])
            .forcePlaceholder(false)
            .size(inputWidth, 18)
            .build();
        ReSyncStudioPanelState.disableEntrance(pathInput);
        commandPathInputs.add(pathInput);
        RowWidget.Builder rowBuilder = new RowWidget.Builder()
            .size(rowWidth, 18)
            .padding(2)
            .addWidget(pathInput);
        if (index > 0) {
            rowBuilder.addWidget(commandPathButton("goforward.png", -90, () -> moveCommandPath(index, -1)));
        }
        if (index < paths.size() - 1) {
            rowBuilder.addWidget(commandPathButton("goforward.png", 90, () -> moveCommandPath(index, 1)));
        }
        rowBuilder.addWidget(new SquareButtonWidget.Builder()
            .imagePath("delete.png")
            .size(18, 18)
            .hint("Remove Path")
            .accentType(ThemeManager.getAccent("danger"))
            .entranceAnimation(false)
            .onClick(() -> removeCommandPath(index))
            .build());
        RowWidget row = rowBuilder.build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    protected SquareButtonWidget commandPathButton(String imagePath, int rotation, Runnable action) {
        return new SquareButtonWidget.Builder()
            .imagePath(imagePath)
            .size(18, 18)
            .rotate(rotation)
            .hint(rotation < 0 ? "Move Up" : "Move Down")
            .entranceAnimation(false)
            .onClick(action)
            .build();
    }

    protected void moveCommandPath(int index, int direction) {
        CommandBindingContext draft = currentCommandDraft();
        int target = index + direction;
        if (index >= 0 && target >= 0 && index < draft.subcommands.size() && target < draft.subcommands.size()) {
            String value = draft.subcommands.get(index);
            draft.subcommands.set(index, draft.subcommands.get(target));
            draft.subcommands.set(target, value);
        }
        rebuildCommandResourcePanel(draft);
    }

    protected void removeCommandPath(int index) {
        CommandBindingContext draft = currentCommandDraft();
        if (index >= 0 && index < draft.subcommands.size()) {
            draft.subcommands.remove(index);
        }
        if (draft.subcommands.isEmpty()) {
            draft.subcommands.add("");
        }
        rebuildCommandResourcePanel(draft);
    }

    protected void rebuildCommandResourcePanel(CommandBindingContext draft) {
        studioResourcePanelKey = "";
        buildCommandResourcePanel(draft);
        studioResourcePanel.container().updateWidgetPositions();
    }

    protected List<String> collectCommandPathDraft() {
        List<String> paths = new ArrayList<>();
        for (TextInputWidget input : commandPathInputs) {
            paths.add(input != null && input.getText() != null ? input.getText().trim() : "");
        }
        return paths;
    }

    protected List<String> collectCommandPaths() {
        List<String> paths = new ArrayList<>();
        for (String path : collectCommandPathDraft()) {
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return paths;
    }

    protected CommandBindingContext currentCommandDraft() {
        CommandBindingContext draft = new CommandBindingContext();
        draft.command = commandLabelInput != null ? commandLabelInput.getText() : activeStudioDocument.id();
        draft.subcommands = collectCommandPathDraft();
        draft.structured = commandStructuredToggle != null && commandStructuredToggle.getValue();
        return draft;
    }

    protected CommandBindingContext parseCommandContext(String context) {
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
                    parsed.command = normalizeCommandLabel(decoded.command);
                    parsed.subcommands = decoded.subcommands != null ? decoded.subcommands : new ArrayList<>();
                    parsed.structured = decoded.structured != null && decoded.structured;
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        parsed.command = normalizeCommandLabel(trimmed);
        return parsed;
    }

    protected String encodeCommandContext(CommandBindingContext command) {
        if (command == null) {
            return "";
        }
        command.command = normalizeCommandLabel(command.command);
        command.subcommands = command.subcommands != null ? command.subcommands : new ArrayList<>();
        command.structured = command.structured != null && command.structured;
        return command.subcommands.isEmpty() && !command.structured ? command.command : gson.toJson(command);
    }

    protected String normalizeCommandLabel(String label) {
        String command = label != null ? label.trim().toLowerCase(Locale.ROOT) : "";
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        return command.matches("^[a-zA-Z0-9:_-]+$") ? command : "";
    }

    protected int parseStudioInt(String value, int fallback, int min, int max) {
        try {
            return Math.clamp(Integer.parseInt(value), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    protected List<String> parseStudioLines(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\|")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    protected String jsonText(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    protected boolean jsonBool(JsonObject object, String key, boolean fallback) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
    }

    protected String studioResourceDescription(String label) {
        return switch (label) {
            case "Command" -> "Root command label.\nDo not include the leading slash.\nExample: trade creates /trade.";
            case "Structured" -> "Structured command mode.\nOn: ReSync stores paths and argument tokens.\nOff: the command is treated as one flat trigger label.";
            case "Paths" -> "Subcommand paths matched after the root command.\nEach row is one path.\nTokens are separated by spaces.\nPlaceholders:\n<online_player> Online Bukkit player name.\n<offline_player> Known offline player name.\n<player_with_perm:permission.node> Online player with that permission.\n<any> Any single argument token.\nAny other <name> also matches one token.";
            case "Gui Title" -> "Inventory title shown at the top of the GUI.\nMinecraft displays it in the menu header, so keep it short.";
            case "Gui Rows" -> "Chest row count.\nValid range: 1 to 6.\nEach row adds 9 custom slots.";
            case "Gui Inventory" -> "Player inventory visibility.\nOn: show the player's inventory under the custom menu.\nOff: show only the custom menu slots.";
            case "Scoreboard Title" -> "Sidebar display title.\nMinecraft renders this above all scoreboard lines.";
            case "Objective" -> "Scoreboard objective id.\nKeep it stable because updates target this id.";
            case "Scoreboard Lines" -> "Sidebar rows under the title.\nMinecraft shows up to 15 lines.\nEarlier lines appear higher.";
            case "Tab Header" -> "Text above the player list in the tab overlay.\nSupports multiple lines.";
            case "Tab Entry" -> "Per-player tab row format.\n%player% is replaced in the preview.\nRuntime placeholders depend on the synced tab resource.";
            case "Tab Footer" -> "Text below the player list in the tab overlay.\nSupports multiple lines.";
            default -> "";
        };
    }

    protected void syncStudioDocumentTabs() {
        if (studioTabsManager == null) {
            return;
        }
        Set<String> documentKeys = new HashSet<>();
        for (StudioDocument document : studioDocuments) {
            documentKeys.add(document.key());
        }
        List<TabsManager.Tab> tabs = studioTabsManager.getTabs();
        for (int i = tabs.size() - 1; i >= 0; i--) {
            Object data = tabs.get(i).getData();
            if (!(data instanceof String key) || !documentKeys.contains(key)) {
                studioTabsManager.removeTabRaw(i);
            }
        }
        tabs = studioTabsManager.getTabs();
        for (StudioDocument document : studioDocuments) {
            TabsManager.Tab tab = findStudioTab(document.key(), tabs);
            if (tab == null) {
                Container container = new Container("document-" + document.key(), 0, 0, 1, 1);
                tab = studioTabsManager.addTab(document.title(), container, studioResourceIconPath(document.type(), document.id()));
                tab.setData(document.key());
                tabs.add(tab);
            } else {
                tab.setName(document.title());
                tab.setIconPath(studioResourceIconPath(document.type(), document.id()));
            }
        }
        if (activeStudioDocument != null) {
            TabsManager.Tab activeTab = findStudioTab(activeStudioDocument.key(), studioTabsManager.getTabs());
            if (activeTab != null && studioTabsManager.getActiveTab() != activeTab) {
                setStudioTabsManagerActiveTab(activeTab);
            } else if (activeTab == null) {
                clearActiveStudioDocument();
            }
        }
        studioTabsManager.updateLayout();
    }

    protected void selectStudioDocument(String key) {
        for (StudioDocument document : studioDocuments) {
            if (document.key().equals(key)) {
                if (activeStudioDocument != null && !activeStudioDocument.key().equals(key) && activeStudioDocument.view() != null) {
                    activeStudioDocument.view().deselected();
                }
                beforeStudioDocumentSelection();
                prepareStudioDocumentSelection();
                activeStudioDocument = document;
                setStudioTabsManagerActiveTab(findStudioTab(document.key(), studioTabsManager != null ? studioTabsManager.getTabs() : List.of()));
                if (document.view() != null) {
                    document.view().selected();
                }
                refreshActiveViewHeaderButtons();
                afterStudioDocumentSelected(document);
                if (studioResourcePanel != null) {
                    if (document.view() != null && !document.view().hasPanel()) {
                        studioResourcePanel.hide();
                    } else {
                        studioResourcePanel.show();
                    }
                }
                if (document.view() == null || document.view().hasPanel()) {
                    refreshStudioResourcePanel();
                }
                updatePositions();
                return;
            }
        }
        clearActiveStudioDocument();
    }

    protected void prepareStudioDocumentSelection() {
        closeStudioSelector();
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioScreen screen) {
            screen.closeStudioSelector();
            screen.clearStudioResourcePanelWidgets();
        }
        if (studioResourcePanel != null) {
            studioResourcePanel.container().clearWidgets();
        }
        studioResourcePanelWidgets.clear();
        studioResourcePanelKey = "";
        studioResourcePanelInputs.clear();
        studioResourcePanelToggles.clear();
    }

    protected void beforeStudioDocumentSelection() {
    }

    protected void afterStudioDocumentSelected(StudioDocument document) {
    }

    protected void clearActiveStudioDocument() {
        if (activeStudioDocument != null && activeStudioDocument.view() != null) {
            activeStudioDocument.view().deselected();
        }
        beforeClearActiveStudioDocument();
        activeStudioDocument = null;
        if (studioResourcePanel != null) {
            clearStudioResourcePanelWidgets();
            studioResourcePanel.hide();
        }
        afterActiveStudioDocumentCleared();
        refreshActiveViewHeaderButtons();
        updatePositions();
    }

    protected void beforeClearActiveStudioDocument() {
    }

    protected void afterActiveStudioDocumentCleared() {
    }

    protected TabsManager.Tab findStudioTab(String key, List<TabsManager.Tab> tabs) {
        for (TabsManager.Tab tab : tabs) {
            if (key.equals(tab.getData())) {
                return tab;
            }
        }
        return null;
    }

    protected void setStudioTabsManagerActiveTab(TabsManager.Tab tab) {
        if (studioTabsManager == null || tab == null || studioTabsManager.getActiveTab() == tab) {
            return;
        }
        syncingStudioTabSelection = true;
        try {
            studioTabsManager.setActiveTab(tab.getContainer());
        } finally {
            syncingStudioTabSelection = false;
        }
    }

    protected boolean handleStudioWorkspaceMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        ReMouseEvent event = studioMouseEvent(ReMouseEvent.Action.DRAGGED, mouseX, mouseY, button, deltaX, deltaY);
        return event.finish(handleStudioWorkspaceMouseDragged(event));
    }

    protected boolean handleStudioWorkspaceMouseDragged(ReMouseEvent event) {
        if (studioTabsManager != null && Widget.dispatchMouseDragged(studioTabsManager, event)) {
            return true;
        }
        if (handleActiveStudioSelectorMouseDragged(event)) {
            return true;
        }
        ReSyncStudioView priorityView = activeStudioView();
        if (priorityView instanceof StudioPriorityInputView && priorityView.mouseDragged(event)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && Widget.dispatchMouseDragged(studioContentBrowser, event)) {
            return true;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && Widget.dispatchMouseDragged(studioResourcePanel.container(), event)) {
                return true;
            }
            return view.mouseDragged(event);
        }
        return studioResourcePanel != null && Widget.dispatchMouseDragged(studioResourcePanel.container(), event);
    }

    protected boolean handleStudioWorkspaceMouseClicked(double mouseX, double mouseY, int button) {
        ReMouseEvent event = studioMouseEvent(ReMouseEvent.Action.PRESSED, mouseX, mouseY, button, 0, 0);
        return event.finish(handleStudioWorkspaceMouseClicked(event));
    }

    protected boolean handleStudioWorkspaceMouseClicked(ReMouseEvent event) {
        if (handleActiveStudioSelectorMouseClicked(event)) {
            return true;
        }
        ReSyncStudioView priorityView = activeStudioView();
        if (priorityView instanceof StudioPriorityInputView && priorityView.mouseClicked(event)) {
            return true;
        }
        if (handleStudioHudMouseClicked(event)) {
            return true;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && Widget.dispatchMouseClicked(studioResourcePanel.container(), event)) {
                return true;
            }
            boolean handled = view.mouseClicked(event);
            return handled || (event.button() == ReMouseButton.RIGHT && !activeStudioDocumentUsesFlowGraphCanvas());
        }
        boolean handled = studioResourcePanel != null && Widget.dispatchMouseClicked(studioResourcePanel.container(), event);
        return handled || (event.button() == ReMouseButton.RIGHT && !activeStudioDocumentUsesFlowGraphCanvas());
    }

    protected boolean handleStudioWorkspaceMouseReleased(double mouseX, double mouseY, int button) {
        ReMouseEvent event = studioMouseEvent(ReMouseEvent.Action.RELEASED, mouseX, mouseY, button, 0, 0);
        return event.finish(handleStudioWorkspaceMouseReleased(event));
    }

    protected boolean handleStudioWorkspaceMouseReleased(ReMouseEvent event) {
        if (studioTabsManager != null && Widget.dispatchMouseReleased(studioTabsManager, event)) {
            return true;
        }
        if (handleActiveStudioSelectorMouseReleased(event)) {
            return true;
        }
        ReSyncStudioView priorityView = activeStudioView();
        if (priorityView instanceof StudioPriorityInputView && priorityView.mouseReleased(event)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && Widget.dispatchMouseReleased(studioContentBrowser, event)) {
            return true;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && Widget.dispatchMouseReleased(studioResourcePanel.container(), event)) {
                return true;
            }
            return view.mouseReleased(event);
        }
        return studioResourcePanel != null && Widget.dispatchMouseReleased(studioResourcePanel.container(), event);
    }

    protected boolean handleStudioWorkspaceKeyPressed(int keyCode, int scanCode, int modifiers) {
        ReKeyEvent event = currentKeyPressedEvent(keyCode, scanCode, modifiers, false);
        return event.finish(handleStudioWorkspaceKeyPressed(event));
    }

    protected boolean handleStudioWorkspaceKeyPressed(ReKeyEvent event) {
        if (studioTabsManager != null && Widget.dispatchKeyPressed(studioTabsManager, event)) {
            return true;
        }
        if (handleActiveStudioSelectorKeyPressed(event)) {
            return true;
        }
        ReSyncStudioView priorityView = activeStudioView();
        if (priorityView instanceof StudioPriorityInputView && priorityView.keyPressed(event)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && Widget.dispatchKeyPressed(studioContentBrowser, event)) {
            return true;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.isVisible() && Widget.dispatchKeyPressed(studioResourcePanel.container(), event)) {
                return true;
            }
            return view.keyPressed(event);
        }
        return studioResourcePanel != null && studioResourcePanel.isVisible() && Widget.dispatchKeyPressed(studioResourcePanel.container(), event);
    }

    protected boolean handleStudioWorkspaceCharTyped(char chr, int modifiers) {
        ReTextInputEvent event = currentTextInputEvent(chr, modifiers);
        return event.finish(handleStudioWorkspaceTextInput(event));
    }

    protected boolean handleStudioWorkspaceTextInput(ReTextInputEvent event) {
        if (studioTabsManager != null && Widget.dispatchTextInput(studioTabsManager, event)) {
            return true;
        }
        if (handleActiveStudioSelectorTextInput(event)) {
            return true;
        }
        ReSyncStudioView priorityView = activeStudioView();
        if (priorityView instanceof StudioPriorityInputView && priorityView.textInput(event)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && Widget.dispatchTextInput(studioContentBrowser, event)) {
            return true;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.isVisible() && Widget.dispatchTextInput(studioResourcePanel.container(), event)) {
                return true;
            }
            return view.textInput(event);
        }
        return studioResourcePanel != null && studioResourcePanel.isVisible() && Widget.dispatchTextInput(studioResourcePanel.container(), event);
    }

    protected boolean handleStudioWorkspaceMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ReScrollEvent event = currentScrollEvent(mouseX, mouseY, horizontalAmount, verticalAmount);
        return event.finish(handleStudioWorkspaceMouseScrolled(event));
    }

    protected boolean handleStudioWorkspaceMouseScrolled(ReScrollEvent event) {
        if (studioTabsManager != null && Widget.dispatchMouseScrolled(studioTabsManager, event)) {
            return true;
        }
        if (handleActiveStudioSelectorMouseScrolled(event)) {
            return true;
        }
        if (studioMode && studioContentBrowser != null && Widget.dispatchMouseScrolled(studioContentBrowser, event)) {
            return true;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && studioResourcePanel.isVisible() && Widget.dispatchMouseScrolled(studioResourcePanel.container(), event)) {
                return true;
            }
            return view.mouseScrolled(event);
        }
        return studioResourcePanel != null && studioResourcePanel.isVisible() && Widget.dispatchMouseScrolled(studioResourcePanel.container(), event);
    }

    protected boolean handleStudioHudMouseClicked(double mouseX, double mouseY, int button) {
        ReMouseEvent event = studioMouseEvent(ReMouseEvent.Action.PRESSED, mouseX, mouseY, button, 0, 0);
        return event.finish(handleStudioHudMouseClicked(event));
    }

    protected boolean handleStudioHudMouseClicked(ReMouseEvent event) {
        if (!studioMode) {
            return false;
        }
        if (studioContentBrowser != null && studioContentBrowser.handleHistoryMouseButton(event)) {
            return true;
        }
        if (studioTabsManager != null && Widget.dispatchMouseClicked(studioTabsManager, event)) {
            return true;
        }
        return studioContentBrowser != null && Widget.dispatchMouseClicked(studioContentBrowser, event);
    }

    protected boolean handleActiveStudioSelectorMouseClicked(double mouseX, double mouseY, int button) {
        ReMouseEvent event = studioMouseEvent(ReMouseEvent.Action.PRESSED, mouseX, mouseY, button, 0, 0);
        return event.finish(handleActiveStudioSelectorMouseClicked(event));
    }

    protected boolean handleActiveStudioSelectorMouseClicked(ReMouseEvent event) {
        if (activeStudioSelector != null && activeStudioSelector.visible) {
            return Widget.dispatchMouseClicked(activeStudioSelector, event);
        }
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.mouseClicked(event);
        }
        return false;
    }

    protected boolean handleActiveStudioSelectorMouseReleased(double mouseX, double mouseY, int button) {
        ReMouseEvent event = studioMouseEvent(ReMouseEvent.Action.RELEASED, mouseX, mouseY, button, 0, 0);
        return event.finish(handleActiveStudioSelectorMouseReleased(event));
    }

    protected boolean handleActiveStudioSelectorMouseReleased(ReMouseEvent event) {
        if (activeStudioSelector != null && activeStudioSelector.visible) {
            return Widget.dispatchMouseReleased(activeStudioSelector, event);
        }
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.mouseReleased(event);
        }
        return false;
    }

    protected boolean handleActiveStudioSelectorMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        ReMouseEvent event = studioMouseEvent(ReMouseEvent.Action.DRAGGED, mouseX, mouseY, button, deltaX, deltaY);
        return event.finish(handleActiveStudioSelectorMouseDragged(event));
    }

    protected boolean handleActiveStudioSelectorMouseDragged(ReMouseEvent event) {
        if (activeStudioSelector != null && activeStudioSelector.visible) {
            return Widget.dispatchMouseDragged(activeStudioSelector, event);
        }
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.mouseDragged(event);
        }
        return false;
    }

    protected boolean handleActiveStudioSelectorMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ReScrollEvent event = currentScrollEvent(mouseX, mouseY, horizontalAmount, verticalAmount);
        return event.finish(handleActiveStudioSelectorMouseScrolled(event));
    }

    protected boolean handleActiveStudioSelectorMouseScrolled(ReScrollEvent event) {
        if (activeStudioSelector != null && activeStudioSelector.visible) {
            return Widget.dispatchMouseScrolled(activeStudioSelector, event);
        }
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.mouseScrolled(event);
        }
        return false;
    }

    protected boolean handleActiveStudioSelectorKeyPressed(int keyCode, int scanCode, int modifiers) {
        ReKeyEvent event = currentKeyPressedEvent(keyCode, scanCode, modifiers, false);
        return event.finish(handleActiveStudioSelectorKeyPressed(event));
    }

    protected boolean handleActiveStudioSelectorKeyPressed(ReKeyEvent event) {
        if (activeStudioSelector != null && activeStudioSelector.visible) {
            return Widget.dispatchKeyPressed(activeStudioSelector, event);
        }
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.keyPressed(event);
        }
        return false;
    }

    protected boolean handleActiveStudioSelectorCharTyped(char chr, int modifiers) {
        ReTextInputEvent event = currentTextInputEvent(chr, modifiers);
        return event.finish(handleActiveStudioSelectorTextInput(event));
    }

    protected boolean handleActiveStudioSelectorTextInput(ReTextInputEvent event) {
        if (activeStudioSelector != null && activeStudioSelector.visible) {
            return Widget.dispatchTextInput(activeStudioSelector, event);
        }
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioSelectorView selectorView && selectorView.hasActiveStudioSelector()) {
            return view.textInput(event);
        }
        return false;
    }

    private ReMouseEvent studioMouseEvent(ReMouseEvent.Action action, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return currentMouseEvent(action, mouseX, mouseY, button, deltaX, deltaY);
    }

    protected boolean handlePopupWidgetMouseClicked(ReMouseEvent event) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && Widget.dispatchMouseClicked(popup, event)) {
                return true;
            }
        }
        return false;
    }

    protected boolean handlePopupWidgetMouseReleased(ReMouseEvent event) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && Widget.dispatchMouseReleased(popup, event)) {
                return true;
            }
        }
        return false;
    }

    protected boolean handlePopupWidgetMouseDragged(ReMouseEvent event) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && Widget.dispatchMouseDragged(popup, event)) {
                return true;
            }
        }
        return false;
    }

    protected boolean handlePopupWidgetMouseScrolled(ReScrollEvent event) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && Widget.dispatchMouseScrolled(popup, event)) {
                return true;
            }
        }
        return false;
    }

    protected boolean handlePopupWidgetKeyPressed(ReKeyEvent event) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && Widget.dispatchKeyPressed(popup, event)) {
                return true;
            }
        }
        return false;
    }

    protected boolean handlePopupWidgetTextInput(ReTextInputEvent event) {
        List<Widget> widgetSnapshot = new ArrayList<>(widgets);
        for (int i = widgetSnapshot.size() - 1; i >= 0; i--) {
            Widget widget = widgetSnapshot.get(i);
            if (widget instanceof PopupWidget popup && popup.isVisible() && Widget.dispatchTextInput(popup, event)) {
                return true;
            }
        }
        return false;
    }

    protected ItemSelectorWidget showStudioSelector(List<String> labels, String selectedLabel, int selectorX, int selectorY, Consumer<ItemSelectorWidget> configure) {
        closeStudioSelector();
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(this)
            .size(220, 240)
            .dismissOnSelect(true)
            .onClose(() -> closeStudioSelector(selectorRef[0]))
            .build();
        selector.setLayer(900);
        selector.setPriority(30);
        selectorRef[0] = selector;
        if (configure != null) {
            configure.accept(selector);
        } else if (labels != null) {
            for (String label : labels) {
                selector.addItem(label, () -> {});
            }
        }
        if (selectedLabel != null) {
            selector.setSelectedItem(selectedLabel);
        }
        activeStudioSelector = selector;
        addDrawableChild(selector);
        int left = Math.clamp(selectorX, 8, Math.max(8, width - selector.getWidth() - 8));
        int top = Math.clamp(selectorY, 32, Math.max(32, height - selector.getHeight() - 20));
        selector.show(left, top);
        return selector;
    }

    protected ItemSelectorWidget showStudioSelector(ItemSelectorWidget selector, String selectedLabel, int selectorX, int selectorY) {
        closeStudioSelector();
        if (selector == null) {
            return null;
        }
        selector.onClose = () -> closeStudioSelector(selector);
        selector.setLayer(900);
        selector.setPriority(30);
        if (selectedLabel != null) {
            selector.setSelectedItem(selectedLabel);
        }
        activeStudioSelector = selector;
        addDrawableChild(selector);
        int left = Math.clamp(selectorX, 8, Math.max(8, width - selector.getWidth() - 8));
        int top = Math.clamp(selectorY, 32, Math.max(32, height - selector.getHeight() - 20));
        selector.show(left, top);
        return selector;
    }

    protected void closeStudioSelector() {
        closeStudioSelector(activeStudioSelector);
    }

    protected void closeStudioSelector(ItemSelectorWidget selector) {
        if (selector != null) {
            selector.onClose = null;
            selector.hide();
            remove(selector);
        }
        if (selector == activeStudioSelector) {
            activeStudioSelector = null;
            onStudioSelectorClosed();
        }
        setFocusedWidget(null);
    }

    protected void onStudioSelectorClosed() {
    }

    public boolean hasActiveStudioSelector() {
        return activeStudioSelector != null && activeStudioSelector.visible;
    }

    protected List<AnimatedWidget> visibleStudioHeaderButtons() {
        List<AnimatedWidget> buttons = new ArrayList<>();
        if (activeStudioDocument == null) {
            return buttons;
        }
        if (activeStudioView() == null) {
            buttons.addAll(headerButtons);
        } else {
            buttons.addAll(activeViewHeaderButtons);
        }
        return buttons;
    }

    protected void renderStudioEmptyMessage(IDrawContext context, int mouseX, int mouseY) {
        if (studioEmptyMessage == null) {
            return;
        }
        int top = 42;
        int bottom = studioContentBrowserAffectsLayout() ? studioContentBrowser.getY() - 8 : height - 8;
        int centerX = width / 2;
        int centerY = top + Math.max(0, bottom - top) / 2;
        studioEmptyMessage.setPosition(centerX - studioEmptyMessage.getWidth() / 2, centerY - studioEmptyMessage.getHeight() / 2);
        studioEmptyMessage.render(context, mouseX, mouseY, 0);
    }

    protected void layoutStudioHeaderButtons() {
        if (!studioMode) {
            return;
        }
        reconcileFullEditorHeaderButtons();
        header().build();
    }

    protected void reconcileFullEditorHeaderButtons() {
        ReSyncStudioView view = activeStudioView();
        if (!(view instanceof ScreenBackedStudioView screenView) || !screenView.initialized()) {
            return;
        }
        List<AnimatedWidget> buttons = screenView.headerButtons();
        if (!sameHeaderButtons(activeViewHeaderButtons, buttons)) {
            activeViewHeaderButtons.clear();
            activeViewHeaderButtons.addAll(buttons);
            rebuildStudioHeaderButtons();
            return;
        }
        if (!buttons.isEmpty() && !headerContainsButtons(visibleStudioHeaderButtons())) {
            rebuildStudioHeaderButtons();
        }
    }

    protected boolean sameHeaderButtons(List<AnimatedWidget> current, List<AnimatedWidget> expected) {
        if (current.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i) != expected.get(i)) {
                return false;
            }
        }
        return true;
    }

    protected boolean headerContainsButtons(List<AnimatedWidget> buttons) {
        for (AnimatedWidget button : buttons) {
            if (button != null && !header().leftButtons.contains(button) && !header().rightButtons.contains(button)) {
                return false;
            }
        }
        return true;
    }

    protected void rebuildStudioHeaderButtons() {
        if (!studioMode) {
            return;
        }
        header().reset();
        for (AnimatedWidget button : headerButtons) {
            if (button != null) {
                button.visible = false;
            }
        }
        for (AnimatedWidget button : activeViewHeaderButtons) {
            if (button != null) {
                button.visible = false;
            }
        }
        List<AnimatedWidget> buttons = visibleStudioHeaderButtons();
        for (AnimatedWidget button : buttons) {
            if (button != null) {
                button.visible = true;
                button.entranceAnimationEnabled = false;
                header().addRight(button);
            }
        }
        if (activeStudioView() == null) {
            syncDebugHeaderVisibility();
        }
        header().build();
    }

    protected void renderStudioOverlays(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (studioMode) {
            renderDesktopChromeBackground(context, mouseX, mouseY, delta);
            updateFullEditorHeaderClose();
            layoutStudioHeaderButtons();
            for (AnimatedWidget button : header().leftButtons) {
                if (button != null && button.visible) {
                    button.render(context, mouseX, mouseY, delta);
                }
            }
            for (AnimatedWidget button : header().rightButtons) {
                if (button != null && button.visible) {
                    button.render(context, mouseX, mouseY, delta);
                }
            }
            if (studioTabsManager != null) {
                studioTabsManager.setPosition(10, 5 + header().getOffsetY());
                studioTabsManager.render(context, mouseX, mouseY, delta);
            }
        }

        for (Widget widget : hudWidgets) {
            widget.render(context, mouseX, mouseY, delta);
        }

        if (studioMode && shouldRenderStudioContentBrowser()) {
            studioContentBrowser.layoutInScreen();
            renderStudioPanel(studioContentBrowser.sidePanel(), context, mouseX, mouseY, delta);
        }
        renderAdditionalStudioPanels(context, mouseX, mouseY, delta);
        if (activeStudioDocument != null && (activeStudioView() == null || activeStudioViewUsesResourcePanel()) && studioResourceStudioPanel != null) {
            renderStudioPanel(studioResourceStudioPanel, context, mouseX, mouseY, delta);
        }
        renderActiveResourceSelectorOverlay(context, mouseX, mouseY, delta);

        for (Widget widget : widgets) {
            if (widget instanceof ItemSelectorWidget || widget instanceof ContextMenuWidget) {
                widget.render(context, mouseX, mouseY, delta);
            }
        }

        for (Widget widget : hudWidgets) {
            if (widget instanceof AnimatedWidget animated) {
                animated.renderHintOverlay(context);
            }
        }
        if (studioMode && studioContentBrowser != null && !studioContentBrowser.isTemporarilyHidden()) {
            studioContentBrowser.sidePanel().container().renderHintOverlay(context);
        }
        renderHeaderHintOverlays(context);
        for (Widget widget : widgets) {
            if (widget instanceof ItemSelectorWidget selector && selector.visible) {
                selector.renderHintOverlay(context);
            } else if (widget instanceof ContextMenuWidget menu && menu.isVisible()) {
                menu.renderHintOverlay(context);
            }
        }
    }

    protected void updateFullEditorHeaderClose() {
        boolean contentBrowserClosed = studioContentBrowser == null || studioContentBrowser.isSlideOutFinished();
        if (fullEditorHeaderCloseRequested && isTopHeaderAnimationFinished() && contentBrowserClosed) {
            fullEditorHeaderCloseRequested = false;
            closeFullEditorStudioScreen();
        }
    }

    protected boolean shouldRenderStudioContentBrowser() {
        return studioContentBrowser != null && (!studioContentBrowser.isTemporarilyHidden() || !studioContentBrowser.isSlideOutFinished());
    }

    protected void renderAdditionalStudioPanels(IDrawContext context, int mouseX, int mouseY, float delta) {
    }

    protected void renderHeaderHintOverlays(IDrawContext context) {
        if (!studioMode) {
            return;
        }
        for (AnimatedWidget button : header().leftButtons) {
            if (button != null && button.visible) {
                button.renderHintOverlay(context);
            }
        }
        for (AnimatedWidget button : header().rightButtons) {
            if (button != null && button.visible) {
                button.renderHintOverlay(context);
            }
        }
    }

    protected void renderStudioDocumentPreview(IDrawContext context) {
        if (!studioMode || activeStudioDocument == null || activeStudioDocument.graph() != null) {
            return;
        }
        int top = 42;
        int bottom = studioContentBrowserAffectsLayout() ? studioContentBrowser.getY() - 8 : height - 8;
        int left = 12;
        int right = width - (studioResourcePanel != null && studioResourcePanel.isVisible() ? studioResourcePanel.getDesiredWidth() + 12 : 12);
        int areaWidth = Math.max(20, right - left);
        int areaHeight = Math.max(20, bottom - top);
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            view.renderPreview(context, left, top, areaWidth, areaHeight);
        } else if (ReSyncResourceDragPayload.WORLD.equals(activeStudioDocument.type())) {
            int text = ThemeManager.getColor(ThemeColor.text);
            context.drawText(activeStudioDocument.title(), left + 12, top + 12, text, false);
        }
    }

    protected void renderActiveResourceSelectorOverlay(IDrawContext context, int mouseX, int mouseY, float delta) {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof StudioOverlayView overlayView) {
            overlayView.renderStudioOverlay(context, mouseX, mouseY, delta);
        }
    }

    protected void syncDebugHeaderVisibility() {
    }

    protected void refreshStudioCatalogDocuments() {
        for (StudioDocument document : studioDocuments) {
            if (document.view() instanceof StudioCatalogRefreshView refreshView) {
                refreshView.onStudioCatalogRefreshed();
            }
        }
    }

    protected void refreshStudioWorldDocuments() {
        for (StudioDocument document : studioDocuments) {
            if (document.view() instanceof WorldStudioDocumentView worldView) {
                worldView.refreshWorlds();
            }
        }
    }

    protected void handleStudioWorldOperationResult(WorldOperationResult result) {
        for (StudioDocument document : studioDocuments) {
            if (document.view() instanceof WorldStudioDocumentView worldView) {
                worldView.handleWorldOperationResult(result);
            }
        }
    }

    protected String studioResourceIconPath(String type, String id) {
        return switch (type) {
            case ReSyncResourceDragPayload.FUNCTION -> "snippets.png";
            case ReSyncResourceDragPayload.COMMAND -> "terminal.png";
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> customContentIconPath(id);
            case ReSyncResourceDragPayload.GUI -> "fullPanel.png";
            case ReSyncResourceDragPayload.SCOREBOARD -> "panel.png";
            case ReSyncResourceDragPayload.TAB -> "topPanel.png";
            case ReSyncResourceDragPayload.CHAT -> "chat.png";
            case ReSyncResourceDragPayload.MOTD_PROFILE -> "hi.png";
            case ReSyncResourceDragPayload.MESSAGE_RULE -> "edit.png";
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "crafting.png";
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "text.png";
            case ReSyncResourceDragPayload.ADVANCEMENT_TREE -> "advancement.png";
            case ReSyncResourceDragPayload.DIALOG -> "VanillaButton.png";
            case ReSyncResourceDragPayload.TRADE_PROFILE -> "trade.png";
            case ReSyncResourceDragPayload.NPC_DEFINITION -> "steve.png";
            case ReSyncResourceDragPayload.WORLDGEN -> "map.png";
            case ReSyncResourceDragPayload.WORLD -> "earth.png";
            default -> "graph.png";
        };
    }

    protected String customContentIconPath(String id) {
        FlowManager manager = FlowManager.getInstance();
        CustomContentDefinition content = manager != null ? manager.getCustomContentForServer(studioServerId()).get(id) : null;
        return switch (content != null ? safeStudioText(content.getType()).toLowerCase(Locale.ROOT) : "") {
            case "armor" -> "armor.png";
            case "block" -> "block.png";
            case "item" -> "item.png";
            default -> "item.png";
        };
    }

    protected String safeStudioText(String value) {
        return value == null ? "" : value;
    }

    public static class History<T> {
        private final Supplier<T> snapshotSupplier;
        private final Consumer<T> restoreConsumer;
        private final int limit;
        private final List<T> undoStack = new ArrayList<>();
        private final List<T> redoStack = new ArrayList<>();
        private boolean restoring;

        public History(Supplier<T> snapshotSupplier, Consumer<T> restoreConsumer, int limit) {
            this.snapshotSupplier = snapshotSupplier;
            this.restoreConsumer = restoreConsumer;
            this.limit = Math.max(1, limit);
        }

        public void capture() {
            if (restoring) {
                return;
            }
            undoStack.add(snapshotSupplier.get());
            if (undoStack.size() > limit) {
                undoStack.removeFirst();
            }
            redoStack.clear();
        }

        public void clear() {
            undoStack.clear();
            redoStack.clear();
        }

        public boolean undo() {
            if (undoStack.isEmpty()) {
                return false;
            }
            restoring = true;
            try {
                redoStack.add(snapshotSupplier.get());
                if (redoStack.size() > limit) {
                    redoStack.removeFirst();
                }
                restoreConsumer.accept(undoStack.removeLast());
                return true;
            } finally {
                restoring = false;
            }
        }

        public boolean redo() {
            if (redoStack.isEmpty()) {
                return false;
            }
            restoring = true;
            try {
                undoStack.add(snapshotSupplier.get());
                if (undoStack.size() > limit) {
                    undoStack.removeFirst();
                }
                restoreConsumer.accept(redoStack.removeLast());
                return true;
            } finally {
                restoring = false;
            }
        }

        public boolean isRestoring() {
            return restoring;
        }

        public boolean canUndo() {
            return !undoStack.isEmpty();
        }

        public boolean canRedo() {
            return !redoStack.isEmpty();
        }
    }
}
