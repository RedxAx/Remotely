package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.DesignerSaveNotifications;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncCollaborationClient;
import redxax.oxy.remotely.data.flow.ReSyncFlowClient;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowJson;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.ui.AdvancementDesignerScreen;
import redxax.oxy.remotely.flow.ui.AutomationDefinitionDesignerScreen;
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
import redxax.oxy.remotely.ui.collaboration.CollaborationAvatarResolver;
import redxax.oxy.remotely.ui.collaboration.CollaborationOverlay;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.ui.WorldGenEditorScreen;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseButton;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReScrollEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.core.WidgetCleanup;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.widgets.ContextMenuWidget;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.IconMessage;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.ReorderableWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static restudio.rescreen.render.TextRenderer.tr;

public class StudioScreen extends StudioInfiniteScreen {
    protected boolean studioMode;
    protected TabsManager studioTabsManager;
    protected ReSyncContentBrowserWidget studioContentBrowser;
    protected SidePanel studioResourcePanel;
    protected StudioPanel studioResourceStudioPanel;
    protected final ReSyncStudioPanelState studioPanelState = new ReSyncStudioPanelState();
    private final CollaborationOverlay collaborationOverlay = new CollaborationOverlay(
        new CollaborationAvatarResolver(), runnable -> ScreenManager.getInstance().execute(runnable));
    private TextInputWidget collaborationChatInput;
    private String collaborationChatDraft = "";

    @Override
    public Map<String, Widget> collaborationContainers() {
        Map<String, Widget> containers = new LinkedHashMap<>(super.collaborationContainers());
        if (studioResourcePanel != null && studioResourcePanel.isVisible()) {
            containers.put(studioResourcePanel.id(), studioResourcePanel.container());
        }
        return Collections.unmodifiableMap(containers);
    }

    @Override
    protected Iterable<Widget> screenOverlayContainers() {
        Screen viewScreen = activeStudioViewScreen();
        return viewScreen != null && viewScreen != this ? List.of() : super.screenOverlayContainers();
    }
    private long collaborationChatOpenedAt;
    private IconButton collaborationChangeBadge;
    private long lastPresenceAt;
    private int lastPresenceX = Integer.MIN_VALUE;
    private int lastPresenceY = Integer.MIN_VALUE;
    private String lastPresenceDocument = "";
    private boolean lastPresenceTyping;
    protected final List<AnimatedWidget> studioResourcePanelWidgets = new ArrayList<>();
    protected String studioResourcePanelKey = "";
    protected final Map<String, TextInputWidget> studioResourcePanelInputs = new HashMap<>();
    protected final Map<String, ToggleWidget> studioResourcePanelToggles = new HashMap<>();
    protected IconMessage studioEmptyMessage;
    protected final List<StudioDocument> studioDocuments = new ArrayList<>();
    private boolean restoringPersistedStudioDocuments;
    private boolean persistedStudioDocumentsRestored;
    private boolean persistedStudioRestoreDisposed;
    private long persistedStudioRestoreGeneration;
    private List<String> persistedStudioRestoreKeys = List.of();
    private String persistedStudioRestoreSelectedKey = "";
    private final Map<String, Long> pendingPersistedStudioDocumentRestores = new LinkedHashMap<>();
    private final Set<String> successfulPersistedStudioDocumentRestores = new HashSet<>();
    private final Set<String> terminalPersistedStudioDocumentRestores = new HashSet<>();
    private boolean persistedStudioRestoreRequestAllowed;
    protected final Map<String, Long> pendingStudioTabDiscards = new HashMap<>();
    protected static final long STUDIO_TAB_DISCARD_CONFIRMATION_MILLIS = 3_000L;
    protected static final long STUDIO_TAB_STATE_REFRESH_NANOS = 50_000_000L;
    protected long nextStudioTabStateRefreshAt;
    protected StudioDocument activeStudioDocument;
    protected final FlowGraph studioEmptyGraph = new FlowGraph();
    protected boolean syncingStudioTabSelection;
    protected String activeNodeRegistryServerId;
    protected TextInputWidget commandLabelInput;
    protected final List<TextInputWidget> commandPathInputs = new ArrayList<>();
    private ReorderableWidget<RowWidget> commandPathList;
    protected ToggleWidget commandStructuredToggle;
    private boolean syncingCommandPanel;
    protected final List<AnimatedWidget> headerButtons = new ArrayList<>();
    protected final List<AnimatedWidget> activeViewHeaderButtons = new ArrayList<>();
    protected ItemSelectorWidget activeStudioSelector;
    protected boolean fullEditorHeaderCloseRequested;
    protected ReSyncResourceDragPayload studioResourceDrag;
    protected AnimatedWidget studioResourceDragWidget;
    private int studioResourceDragSourceX;
    private int studioResourceDragSourceY;
    private int studioResourceDragSourceWidth;
    private int studioResourceDragSourceHeight;
    private int studioResourceDragDetachedWidth;
    private int studioResourceDragGrabX;
    private int studioResourceDragGrabY;
    private StudioResourceDragDestination studioResourceDragDestination;

    private record StudioResourceDragDestination(int x, int y, int width, int height, boolean close) {
    }

    protected static class CommandBindingContext {
        public CommandBindingContext() {
        }

        public String command;
        public List<String> subcommands;
        public Boolean structured;
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        if (openCollaborationChat(event)) {
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        return handleStudioHistoryShortcut(event);
    }

    @Override
    public boolean textInput(ReTextInputEvent event) {
        if (collaborationChatInput != null && System.currentTimeMillis() - collaborationChatOpenedAt < 150L
            && "t".equalsIgnoreCase(event.text())) {
            return true;
        }
        return super.textInput(event);
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        TextInputWidget input = collaborationChatInput;
        if (input != null) {
            if (input.isMouseOver(event.x(), event.y())) {
                setFocusedWidget(input);
                return input.mouseClicked(event.retarget(input, event.x(), event.y()));
            }
            dismissCollaborationChat(true);
        }
        return super.mouseClicked(event);
    }

    @Override
    public boolean mouseDragged(ReMouseEvent event) {
        TextInputWidget input = collaborationChatInput;
        if (input != null && getFocusedWidget() == input
            && input.mouseDragged(event.retarget(input, event.x(), event.y(), event.deltaX(), event.deltaY()))) {
            return true;
        }
        return super.mouseDragged(event);
    }

    private boolean openCollaborationChat(ReKeyEvent event) {
        if (collaborationChatInput != null || event.key() != ReKey.T || event.repeat() || event.modifiers().control()
            || event.modifiers().alt() || event.modifiers().superKey() || event.modifiers().shift()
            || !studioMode || isStudioKeyboardInputFocused()) {
            return false;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || !manager.isFlowClientReady(studioServerId())) {
            return false;
        }
        ReSyncFlowClient client = manager.ensureFlowClient(studioServerId());
        if (client == null || !client.supportsFlowCapability("collaboration_chat")) {
            return false;
        }
        collaborationChatInput = new TextInputWidget.Builder()
            .placeholder("Message")
            .text(collaborationChatDraft)
            .maxLength(240)
            .size(Math.clamp(width - 40, 140, 280), 18)
            .onChange(value -> lastPresenceAt = 0L)
            .onEnter(this::sendCollaborationChat)
            .onEscape(this::cancelCollaborationChat)
            .build();
        collaborationChatOpenedAt = System.currentTimeMillis();
        setFocusedWidget(collaborationChatInput);
        collaborationChatInput.selectAll();
        return true;
    }

    private void sendCollaborationChat() {
        if (collaborationChatInput == null) {
            return;
        }
        String message = collaborationChatInput.getText().trim();
        if (!message.isBlank()) {
            FlowManager manager = FlowManager.getInstance();
            if (manager != null && manager.isFlowClientReady(studioServerId())) {
                ReSyncFlowClient client = manager.ensureFlowClient(studioServerId());
                if (client != null) {
                    client.collaboration().publishMessage(message);
                }
            }
        }
        dismissCollaborationChat(false);
    }

    private void cancelCollaborationChat() {
        dismissCollaborationChat(false);
    }

    private void dismissCollaborationChat(boolean preserveDraft) {
        TextInputWidget input = collaborationChatInput;
        if (input == null) {
            return;
        }
        collaborationChatDraft = preserveDraft ? input.getText() : "";
        collaborationChatInput = null;
        if (getFocusedWidget() == input) {
            setFocusedWidget(null);
        }
        lastPresenceAt = 0L;
    }

    @Override
    public void tick() {
        super.tick();
        updateStudioTabStates();
        ReSyncStudioView activeView = activeStudioView();
        if (activeView != null) {
            activeView.tick();
        }
        if (collaborationChatInput != null) {
            if (getFocusedWidget() != collaborationChatInput || !collaborationChatInput.isFocused()) {
                dismissCollaborationChat(true);
            } else {
                collaborationChatInput.tick();
            }
        }
        collaborationOverlay.tick();
    }

    public void openWorkspaceResource(String type, String id) {
        if (type == null || type.isBlank() || id == null || id.isBlank()) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        String key = ReSyncProjectMetadata.resourceKey(type, id);
        boolean alreadyOpen = studioDocuments.stream().anyMatch(document -> document.key().equals(key));
        if (alreadyOpen) {
            selectStudioDocument(key);
            return;
        }
        ReSyncResourceType freshType = ReSyncResourceType.byTypeId(type);
        if (freshType == null && (ReSyncResourceDragPayload.FLOW.equals(type) || ReSyncResourceDragPayload.FUNCTION.equals(type)
            || ReSyncResourceDragPayload.COMMAND.equals(type))) {
            freshType = ReSyncResourceType.FLOW;
        }
        if (freshType != null && (freshType.isGraph() || !restoringPersistedStudioDocuments)) {
            FlowGraph cachedGraph = manager.getGraph(studioServerId(), freshType, id);
            if (cachedGraph != null) {
                ReSyncProjectMetadata.ResourceEntry cachedResource = manager.getProjectMetadata(studioServerId()).findResource(type, id);
                String title = cachedResource != null ? cachedResource.getDisplayName() : id;
                openStudioGraphDocument(type, id, title, detachedGraph(cachedGraph));
            } else {
                requestWorkspaceResource(manager, freshType, id);
            }
            return;
        }
        ReSyncProjectMetadata.ResourceEntry resource = manager.getProjectMetadata(studioServerId()).findResource(type, id);
        if (resource != null && (!restoringPersistedStudioDocuments || freshType == null)) {
            openProjectResource(resource);
            return;
        }
        ReSyncResourceType jsonType = ReSyncResourceType.byTypeId(type);
        if (jsonType != null) {
            if (jsonType == ReSyncResourceType.CUSTOM_CONTENT) {
                CustomContentDefinition content = manager.getCustomContentForServer(studioServerId()).get(id);
                if (content == null) {
                    requestWorkspaceResource(manager, jsonType, id);
                    return;
                }
                String graphId = content.getFlowId() != null && !content.getFlowId().isBlank() ? content.getFlowId() : id;
                FlowGraph contentGraph = content.getGraph() != null ? content.getGraph() : manager.getGraph(studioServerId(), ReSyncResourceType.FLOW, graphId);
                if (contentGraph == null) {
                    requestWorkspaceResource(manager, jsonType, id);
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
                    requestWorkspaceResource(manager, jsonType, id);
                }
                return;
            }
            if (jsonType == ReSyncResourceType.SCOREBOARD) {
                if (manager.getScoreboardsForServer(studioServerId()).containsKey(id)) {
                    openStudioDesigner(type, id);
                } else {
                    requestWorkspaceResource(manager, jsonType, id);
                }
                return;
            }
            if (jsonType == ReSyncResourceType.TAB) {
                if (manager.getTabsForServer(studioServerId()).containsKey(id)) {
                    openStudioDesigner(type, id);
                } else {
                    requestWorkspaceResource(manager, jsonType, id);
                }
                return;
            }
            JsonObject json = manager.getJsonResourcesForServer(studioServerId(), jsonType).get(id);
            if (json == null) {
                requestWorkspaceResource(manager, jsonType, id);
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

    private void requestWorkspaceResource(FlowManager manager, ReSyncResourceType type, String id) {
        if (manager == null || type == null || id == null || id.isBlank()
            || restoringPersistedStudioDocuments && !persistedStudioRestoreRequestAllowed) {
            return;
        }
        ReSyncFlowClient client = manager.ensureFlowClient(studioServerId());
        if (client == null) {
            if (restoringPersistedStudioDocuments) {
                String key = ReSyncProjectMetadata.resourceKey(type.typeId(), id);
                pendingPersistedStudioDocumentRestores.remove(key);
                terminalPersistedStudioDocumentRestores.remove(key);
            }
            return;
        }
        client.requestResource(type, id, !restoringPersistedStudioDocuments);
    }

    public void openWorkspaceFlowEditor(String flowId, String branchPin) {
    }

    public void openWorkspaceFlowEditor(String flowId) {
        openWorkspaceFlowEditor(flowId, null);
    }

    public void openWorkspaceGraphEditor(FlowGraph graph) {
        if (graph != null) {
            openWorkspaceFlowEditor(graph.getId());
        }
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
        restorePersistedOpenStudioDocuments();
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
            .onTabCloseRequested(this::requestStudioTabClose)
            .onTabClosed(tab -> {
                Object data = tab.getData();
                if (data instanceof String key) {
                    pendingStudioTabDiscards.remove(key);
                    studioDocuments.removeIf(document -> {
                        boolean match = document.key().equals(key);
                        if (match) {
                            studioDocumentClosed(document);
                            removePersistedOpenStudioDocument(document.type(), document.id());
                            if (document.view() != null) {
                                document.view().closed();
                            }
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
        restorePersistedOpenStudioDocuments();
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

    private boolean requestStudioTabClose(TabsManager.Tab tab) {
        if (!(tab.getData() instanceof String key)) {
            return true;
        }
        StudioDocument document = findStudioDocument(key);
        if (document == null || !isStudioDocumentDirty(document)) {
            clearStudioDiscardPrompt(key);
            return true;
        }
        long now = System.currentTimeMillis();
        Long deadline = pendingStudioTabDiscards.get(key);
        if (deadline != null && deadline >= now) {
            clearStudioDiscardPrompt(key);
            discardStudioDocument(document);
            return true;
        }
        pendingStudioTabDiscards.put(key, now + STUDIO_TAB_DISCARD_CONFIRMATION_MILLIS);
        tab.setName("Discard Changes?");
        tab.setIconPath("report.png");
        tab.setUnsaved(false);
        studioTabsManager.updateLayout();
        return false;
    }

    protected StudioDocument findStudioDocument(String key) {
        return studioDocuments.stream().filter(document -> document.key().equals(key)).findFirst().orElse(null);
    }

    protected boolean isStudioDocumentDirty(StudioDocument document) {
        if (document.view() != null && document.view().hasUnsavedChanges()) {
            return true;
        }
        return document == activeStudioDocument && hasUnsavedChanges();
    }

    protected void discardStudioDocument(StudioDocument document) {
        if (document == null) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        ReSyncResourceType type = ReSyncResourceType.byTypeId(document.type());
        if (manager != null && type != null) {
            manager.discardResourceDraft(studioServerId(), type, document.id());
        }
        if (document.view() != null) {
            document.view().discardUnsavedChanges();
        }
        if (document == activeStudioDocument) {
            discardUnsavedChanges();
        }
    }

    public void markStudioDocumentSaved(String type, String id) {
        markStudioDocumentSaved(type, id, 0L);
    }

    public void markStudioDocumentSaving(String type, String id, long sequence) {
        String key = ReSyncProjectMetadata.resourceKey(type, id);
        StudioDocument document = findStudioDocument(key);
        if (document == null) {
            return;
        }
        if (document.view() != null) {
            document.view().markChangesSaving(sequence);
        }
        if (document == activeStudioDocument) {
            markChangesSaving(sequence);
        }
    }

    public void markStudioDocumentSaved(String type, String id, long sequence) {
        String key = ReSyncProjectMetadata.resourceKey(type, id);
        StudioDocument document = findStudioDocument(key);
        if (document == null) {
            return;
        }
        if (document.view() != null) {
            document.view().markChangesSaved(sequence);
        }
        if (document == activeStudioDocument) {
            markChangesSaved(sequence);
        }
        clearStudioDiscardPrompt(key);
        syncStudioDocumentTabs();
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
            if (button != null && button.visible) {
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

    protected void openReSyncPermissions() {
        FlowManager manager = FlowManager.getInstance();
        String serverId = studioServerId();
        if (manager == null || serverId == null || serverId.isBlank()) {
            new Notification("Permissions", "ReSync Is Not Connected", Notification.Type.ERROR);
            return;
        }
        ReSyncFlowClient client = manager.ensureFlowClient(serverId);
        if (client == null) {
            new Notification("Permissions", "ReSync Is Not Connected", Notification.Type.ERROR);
            return;
        }
        manager.getApplicationHost().openPermissionManager(this, client.luckPerms());
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
            ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(resource.getType());
            FlowGraph targetGraph = ReSyncResourceDragPayload.COMMAND.equals(resource.getType())
                ? manager.resolveCommandFlowGraph(studioServerId(), resource.getId())
                : manager.getGraph(studioServerId(), resourceType, resource.getId());
            if (targetGraph != null && (resourceType == null || !resource.getType().equals(targetGraph.getResourceType()))) {
                targetGraph = null;
            }
            if (targetGraph != null) {
                openStudioGraphDocument(resource.getType(), resource.getId(), resource.getDisplayName(), detachedGraph(targetGraph));
            } else if (resourceType != null) {
                if (ReSyncResourceDragPayload.COMMAND.equals(resource.getType()) && manager.isCommandFlowIdentityBlocked(studioServerId(), resource.getId())) {
                    new Notification("Command", "ID Conflicts With Content", Notification.Type.ERROR);
                    return;
                }
                ReSyncFlowClient client = manager.ensureFlowClient(studioServerId());
                if (client != null) {
                    client.requestResource(resourceType, resource.getId(), true);
                }
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
                contentGraph = manager.getGraph(studioServerId(), ReSyncResourceType.FLOW, graphId);
            }
            if (contentGraph == null) {
                ReSyncFlowClient client = manager.ensureFlowClient(studioServerId());
                if (client != null) {
                    client.requestResource(ReSyncResourceType.CUSTOM_CONTENT, resource.getId(), true);
                }
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
                ReSyncFlowClient client = manager.ensureFlowClient(studioServerId());
                if (client != null) {
                    client.requestResource(jsonType, resource.getId(), true);
                }
                return;
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
        recordRecentRestudioItem(type, id, id);
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
            return;
        }
        ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(type);
        if (resourceType == null || !Set.of(ReSyncResourceType.CHAT, ReSyncResourceType.MOTD_PROFILE, ReSyncResourceType.MESSAGE_RULE,
            ReSyncResourceType.RECIPE_DEFINITION, ReSyncResourceType.TEXT_TEMPLATE, ReSyncResourceType.TRADE_PROFILE,
            ReSyncResourceType.NPC_DEFINITION, ReSyncResourceType.LOOT_TABLE, ReSyncResourceType.VARIABLE_DEFINITION,
            ReSyncResourceType.TIMER_DEFINITION, ReSyncResourceType.SCHEDULE_DEFINITION).contains(resourceType)) {
            return;
        }
        JsonObject resource = manager.getJsonResourcesForServer(studioServerId(), resourceType).get(id);
        if (resource == null) {
            ReSyncFlowClient client = manager.ensureFlowClient(studioServerId());
            if (client != null) {
                client.requestResource(resourceType, id, true);
            }
            return;
        }
        openFocusedResourceDocument(type, id, resourceType.extractName(resource), detachedJson(resource));
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
            case ReSyncResourceDragPayload.VARIABLE_DEFINITION, ReSyncResourceDragPayload.TIMER_DEFINITION,
                 ReSyncResourceDragPayload.SCHEDULE_DEFINITION ->
                new AutomationDefinitionDesignerScreen(this, type, id, resource, studioServerId(), this);
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
        recordRecentRestudioItem(type, id, title);
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

    private void recordRecentRestudioItem(String type, String id, String title) {
        String serverId = studioServerId();
        RemotelyClient client = RemotelyClient.INSTANCE;
        if (client != null && serverId != null && !serverId.isBlank() && type != null && !type.isBlank() && id != null && !id.isBlank()) {
            client.getHost().serverScreenHost(client).recordRecentRestudioItem(serverId,
                    ReSyncProjectMetadata.resourceKey(type, id), title == null || title.isBlank() ? id : title);
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
        if (restoringPersistedStudioDocuments) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(studioServerId());
        metadata.addOpenDocument(type, id, title);
        manager.saveProjectMetadata(studioServerId(), metadata, false);
    }

    protected void restorePersistedOpenStudioDocuments() {
        if (!studioMode || persistedStudioRestoreDisposed || restoringPersistedStudioDocuments) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        String serverId = studioServerId();
        if (manager == null || serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        List<ReSyncProjectMetadata.OpenDocumentEntry> entries = new ArrayList<>();
        Set<String> entryKeys = new HashSet<>();
        for (ReSyncProjectMetadata.OpenDocumentEntry entry : List.copyOf(metadata.getOpenDocuments())) {
            if (entry != null && !entry.getType().isBlank() && !entry.getId().isBlank() && entryKeys.add(entry.key())) {
                entries.add(entry);
            }
        }
        List<String> snapshotKeys = entries.stream().map(ReSyncProjectMetadata.OpenDocumentEntry::key).toList();
        String selectedKey = metadata.getSelectedResourceKey() == null ? "" : metadata.getSelectedResourceKey();
        if (!persistedStudioRestoreKeys.equals(snapshotKeys)
            || !Objects.equals(persistedStudioRestoreSelectedKey, selectedKey)) {
            ++persistedStudioRestoreGeneration;
            persistedStudioRestoreKeys = snapshotKeys;
            persistedStudioRestoreSelectedKey = selectedKey;
            pendingPersistedStudioDocumentRestores.clear();
            successfulPersistedStudioDocumentRestores.clear();
            terminalPersistedStudioDocumentRestores.clear();
            persistedStudioDocumentsRestored = snapshotKeys.isEmpty();
        }
        if (persistedStudioDocumentsRestored) {
            return;
        }
        long generation = persistedStudioRestoreGeneration;
        for (ReSyncProjectMetadata.OpenDocumentEntry entry : entries) {
            String key = entry.key();
            if (successfulPersistedStudioDocumentRestores.contains(key) || terminalPersistedStudioDocumentRestores.contains(key)) {
                continue;
            }
            if (findStudioDocument(key) != null) {
                successfulPersistedStudioDocumentRestores.add(key);
                pendingPersistedStudioDocumentRestores.remove(key);
                continue;
            }
            ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(entry.getType());
            boolean requestWhenMissing = !Objects.equals(pendingPersistedStudioDocumentRestores.get(key), generation);
            boolean failed = false;
            restoringPersistedStudioDocuments = true;
            persistedStudioRestoreRequestAllowed = requestWhenMissing;
            try {
                openWorkspaceResource(entry.getType(), entry.getId());
            } catch (Throwable failure) {
                failed = true;
            } finally {
                persistedStudioRestoreRequestAllowed = false;
                restoringPersistedStudioDocuments = false;
            }
            if (findStudioDocument(key) != null) {
                successfulPersistedStudioDocumentRestores.add(key);
                pendingPersistedStudioDocumentRestores.remove(key);
            } else if (!failed && resourceType != null && resourceType != ReSyncResourceType.PROJECT_METADATA
                && manager.existingFlowClient(serverId) != null) {
                pendingPersistedStudioDocumentRestores.put(key, generation);
            } else {
                pendingPersistedStudioDocumentRestores.remove(key);
                if (failed || resourceType == null || resourceType == ReSyncResourceType.PROJECT_METADATA) {
                    terminalPersistedStudioDocumentRestores.add(key);
                }
            }
        }
        persistedStudioDocumentsRestored = entries.stream().allMatch(entry ->
            successfulPersistedStudioDocumentRestores.contains(entry.key()) || terminalPersistedStudioDocumentRestores.contains(entry.key()));
        if (persistedStudioDocumentsRestored && !persistedStudioRestoreSelectedKey.isBlank()
            && findStudioDocument(persistedStudioRestoreSelectedKey) != null) {
            selectStudioDocument(persistedStudioRestoreSelectedKey);
        }
    }

    protected void removePersistedOpenStudioDocument(String type, String id) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        String key = ReSyncProjectMetadata.resourceKey(type, id);
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(studioServerId());
        boolean removed = metadata.getOpenDocuments().removeIf(entry -> key.equals(entry.key()));
        if (!removed) {
            return;
        }
        if (key.equals(metadata.getSelectedResourceKey())) {
            metadata.setSelectedResourceKey("");
        }
        manager.saveProjectMetadata(studioServerId(), metadata, false);
    }

    protected void renameStudioDocument(String type, String oldId, String newId) {
        String oldKey = ReSyncProjectMetadata.resourceKey(type, oldId);
        String newKey = ReSyncProjectMetadata.resourceKey(type, newId);
        clearStudioDiscardPrompt(oldKey);
        boolean activeRenamed = activeStudioDocument != null && activeStudioDocument.key().equals(oldKey);
        int documentIndex = -1;
        if (activeRenamed) {
            documentIndex = studioDocuments.indexOf(activeStudioDocument);
        }
        if (documentIndex < 0) {
            for (int i = 0; i < studioDocuments.size(); i++) {
                if (studioDocuments.get(i).key().equals(oldKey)) {
                    documentIndex = i;
                    break;
                }
            }
        }
        if (documentIndex >= 0) {
            StudioDocument document = studioDocuments.get(documentIndex);
            if (document.graph() != null) {
                document.graph().setId(newId);
            }
            if (document.view() != null) {
                document.view().resourceRenamed(type, oldId, newId);
            }
            StudioDocument renamed = new StudioDocument(type, newId, newId, document.graph(), document.view(), document.viewport());
            studioDocumentRenamed(document, renamed);
            for (int i = studioDocuments.size() - 1; i >= 0; i--) {
                StudioDocument duplicate = studioDocuments.get(i);
                if (i == documentIndex || (!duplicate.key().equals(oldKey) && !duplicate.key().equals(newKey))) {
                    continue;
                }
                if (duplicate.view() != null && duplicate.view() != document.view()) {
                    duplicate.view().closed();
                }
                studioDocumentClosed(duplicate);
                studioDocuments.remove(i);
                if (i < documentIndex) {
                    documentIndex--;
                }
            }
            studioDocuments.set(documentIndex, renamed);
            if (activeRenamed) {
                activeStudioDocument = renamed;
            }
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager != null) {
            ReSyncProjectMetadata metadata = manager.getProjectMetadata(studioServerId());
            ReSyncProjectMetadata.OpenDocumentEntry renamedEntry = null;
            for (ReSyncProjectMetadata.OpenDocumentEntry entry : metadata.getOpenDocuments()) {
                if (entry.key().equals(oldKey)) {
                    renamedEntry = entry;
                    break;
                }
            }
            ReSyncProjectMetadata.OpenDocumentEntry keep = renamedEntry;
            metadata.getOpenDocuments().removeIf(entry -> entry != keep && entry.key().equals(newKey));
            if (renamedEntry != null) {
                renamedEntry.setId(newId);
                renamedEntry.setDisplayName(newId);
            }
            if (metadata.getSelectedResourceKey().equals(oldKey)) {
                metadata.setSelectedResourceKey(newKey);
            }
            manager.saveProjectMetadata(studioServerId(), metadata, false);
        }
        syncStudioDocumentTabs();
        if (activeRenamed) {
            selectStudioDocument(newKey);
        }
    }

    protected ReSyncStudioView activeStudioView() {
        return studioMode && activeStudioDocument != null ? activeStudioDocument.view() : null;
    }

    protected Screen activeStudioViewScreen() {
        ReSyncStudioView view = activeStudioView();
        if (view instanceof ScreenBackedStudioView screenView) {
            return screenView.screen();
        }
        return view instanceof Screen screen ? screen : null;
    }

    protected boolean activeStudioViewUsesResourcePanel() {
        ReSyncStudioView view = activeStudioView();
        return view != null && view.hasPanel();
    }

    protected boolean activeStudioDocumentUsesFlowGraphCanvas() {
        return activeStudioDocument != null && activeStudioDocument.view() == null && activeStudioDocument.graph() != null;
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
        CommandBindingContext graphCommand = commandContext(activeStudioDocument.graph());
        if (graphCommand != null) {
            buildCommandResourcePanel(graphCommand);
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
            return;
        }
        setStudioResourcePanelKey(panelKey);
        TextInputWidget name = panelInput("Name", jsonText(dialog, "displayName"));
        TextInputWidget title = panelInput("Title", jsonText(dialog, "title"));
        TextInputWidget type = panelInput("Type", jsonText(dialog, "type"));
        rememberStudioPanelInput("name", name);
        rememberStudioPanelInput("title", title);
        rememberStudioPanelInput("type", type);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        setStudioResourcePanelWidgets(studioPanelState.row("Name", name, rowWidth, studioResourceDescription("Dialog Name")),
            studioPanelState.row("Title", title, rowWidth, studioResourceDescription("Dialog Title")),
            studioPanelState.row("Type", type, rowWidth, studioResourceDescription("Dialog Type")), panelSaveButton(() -> {
            dialog.addProperty("displayName", name.getText());
            dialog.addProperty("title", title.getText());
            dialog.addProperty("type", type.getText());
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
            syncingCommandPanel = true;
            try {
                updateStudioPanelInput("command", command.command != null && !command.command.isBlank() ? command.command : activeStudioDocument.id());
                updateStudioPanelToggle("structured", command.structured != null && command.structured);
                reconcileCommandPathOrder(paths);
                for (int i = 0; i < paths.size(); i++) {
                    TextInputWidget input = commandPathInputs.get(i);
                    if (input != null && !input.isFocused() && !Objects.equals(input.getText(), paths.get(i))) {
                        input.setText(paths.get(i));
                    }
                }
            } finally {
                syncingCommandPanel = false;
            }
            return;
        }
        clearStudioResourcePanelWidgets();
        commandPathInputs.clear();
        commandPathList = null;
        commandLabelInput = null;
        commandStructuredToggle = null;
        studioResourcePanelKey = panelKey;
        commandLabelInput = panelInput("Command", command.command != null && !command.command.isBlank() ? command.command : activeStudioDocument.id());
        commandLabelInput.setOnChange(this::commandInteractionChanged);
        commandStructuredToggle = new ToggleWidget.Builder()
            .label("Structured")
            .toggled(command.structured != null && command.structured)
            .size(studioPanelState.rowWidth(studioResourcePanel), 18)
            .entranceAnimation(false)
            .onChange(this::commandInteractionChanged)
            .build();
        rememberStudioPanelInput("command", commandLabelInput);
        rememberStudioPanelToggle("structured", commandStructuredToggle);
        int rowWidth = studioPanelState.rowWidth(studioResourcePanel);
        List<AnimatedWidget> widgets = new ArrayList<>();
        widgets.add(commandSummaryRow(rowWidth));
        widgets.add(studioPanelState.row("Command", commandLabelInput, rowWidth, studioResourceDescription("Command")));
        widgets.add(studioPanelState.row("Structured", commandStructuredToggle, rowWidth, studioResourceDescription("Structured")));
        List<RowWidget> pathRows = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            pathRows.add(commandPathEntryRow(paths, i, rowWidth));
        }
        commandPathList = new ReorderableWidget.Builder<RowWidget>()
            .items(pathRows)
            .rowHeight(18)
            .gap(2)
            .onReordered(this::moveCommandPath)
            .size(rowWidth, paths.size() * 20 - 2)
            .build();
        commandPathList.setHint("Drag To Reorder · Alt + Up/Down");
        TitledRowWidget pathsRow = new TitledRowWidget.Builder()
            .title("Paths")
            .description(studioResourceDescription("Paths"))
            .size(rowWidth, commandPathList.getHeight())
            .gap(4)
            .addWidget(commandPathList)
            .build();
        ReSyncStudioPanelState.disableEntrance(commandPathList);
        ReSyncStudioPanelState.disableEntrance(pathsRow);
        widgets.add(pathsRow);
        widgets.add(new AnimatedButton.Builder()
            .label("Add Path")
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .onClick(() -> {
                CommandBindingContext draft = currentCommandDraft();
                draft.subcommands.add("");
                applyCommandInteraction(draft, true);
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
            "color <text:color_names>"
        };
        int inputWidth = Math.max(120, rowWidth - 26);
        TextInputWidget pathInput = new TextInputWidget.Builder()
            .text(value)
            .placeholder(pathExamples[index % pathExamples.length])
            .forcePlaceholder(false)
            .size(inputWidth, 18)
            .onChange(this::commandInteractionChanged)
            .build();
        ReSyncStudioPanelState.disableEntrance(pathInput);
        commandPathInputs.add(pathInput);
        RowWidget row = new RowWidget.Builder()
            .size(rowWidth, 18)
            .padding(2)
            .addWidget(pathInput)
            .addWidget(new SquareButtonWidget.Builder()
                .imagePath("delete.png")
                .size(18, 18)
                .hint("Remove Path")
                .accentType(ThemeManager.getAccent("danger"))
                .entranceAnimation(false)
                .onClick(() -> removeCommandPath(pathInput))
                .build())
            .build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    protected void moveCommandPath(int from, int to) {
        if (from < 0 || to < 0 || from >= commandPathInputs.size() || to >= commandPathInputs.size() || from == to) {
            return;
        }
        commandPathInputs.add(to, commandPathInputs.remove(from));
        applyCommandInteraction(currentCommandDraft(), false);
    }

    private void reconcileCommandPathOrder(List<String> paths) {
        if (commandPathList == null || paths == null || commandPathInputs.size() != paths.size()) {
            return;
        }
        List<RowWidget> rows = commandPathList.getItems();
        if (rows.size() != commandPathInputs.size()) {
            return;
        }
        List<TextInputWidget> reorderedInputs = new ArrayList<>();
        List<RowWidget> reorderedRows = new ArrayList<>();
        boolean[] used = new boolean[commandPathInputs.size()];
        for (String path : paths) {
            int match = -1;
            for (int index = 0; index < commandPathInputs.size(); index++) {
                if (!used[index] && Objects.equals(commandPathInputs.get(index).getText(), path)) {
                    match = index;
                    break;
                }
            }
            if (match < 0) {
                return;
            }
            used[match] = true;
            reorderedInputs.add(commandPathInputs.get(match));
            reorderedRows.add(rows.get(match));
        }
        if (reorderedInputs.equals(commandPathInputs)) {
            return;
        }
        commandPathInputs.clear();
        commandPathInputs.addAll(reorderedInputs);
        commandPathList.setItems(reorderedRows);
    }

    protected void removeCommandPath(TextInputWidget pathInput) {
        int index = commandPathInputs.indexOf(pathInput);
        CommandBindingContext draft = currentCommandDraft();
        if (index >= 0 && index < draft.subcommands.size()) {
            draft.subcommands.remove(index);
        }
        if (draft.subcommands.isEmpty()) {
            draft.subcommands.add("");
        }
        applyCommandInteraction(draft, true);
    }

    protected void rebuildCommandResourcePanel(CommandBindingContext draft) {
        syncingCommandPanel = true;
        try {
            studioResourcePanelKey = "";
            buildCommandResourcePanel(draft);
            studioResourcePanel.container().updateWidgetPositions();
        } finally {
            syncingCommandPanel = false;
        }
    }

    private void commandInteractionChanged() {
        if (!syncingCommandPanel) {
            applyCommandInteraction(currentCommandDraft(), false);
        }
    }

    private void applyCommandInteraction(CommandBindingContext draft, boolean rebuild) {
        if (activeStudioDocument == null || activeStudioDocument.graph() == null || draft == null) {
            return;
        }
        applyStudioCollaborationInteraction(() -> applyCommandContext(activeStudioDocument.graph(), draft));
        if (rebuild) {
            rebuildCommandResourcePanel(draft);
        }
    }

    protected void applyStudioCollaborationInteraction(Runnable mutation) {
        if (mutation != null) {
            mutation.run();
        }
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

    protected CommandBindingContext commandContext(FlowGraph graph) {
        if (graph == null || graph.getNodes() == null) {
            return null;
        }
        for (FlowNode node : graph.getNodes().values()) {
            if (node == null || !isCommandStartNode(node.getType()) || node.getInputValues() == null) {
                continue;
            }
            Object commandValue = node.getInputValues().get("command");
            String command = commandValue != null ? normalizeCommandLabel(FlowJson.text(commandValue)) : "";
            if (command.isBlank()) {
                continue;
            }
            CommandBindingContext context = new CommandBindingContext();
            context.command = command;
            Object pathsValue = node.getInputValues().get("subcommands");
            context.subcommands = pathsValue instanceof List<?> paths
                ? paths.stream().map(FlowJson::text).toList()
                : new ArrayList<>();
            Object structuredValue = node.getInputValues().get("structured");
            context.structured = structuredValue instanceof Boolean value ? value : Boolean.parseBoolean(FlowJson.text(structuredValue));
            return context;
        }
        return null;
    }

    private boolean isEditingCommandPanel() {
        if (commandLabelInput != null && commandLabelInput.isFocused()) {
            return true;
        }
        if (commandStructuredToggle != null && commandStructuredToggle.isFocused()) {
            return true;
        }
        return commandPathInputs.stream().anyMatch(input -> input != null && input.isFocused());
    }

    protected boolean applyCommandContext(FlowGraph graph, CommandBindingContext context) {
        if (graph == null || graph.getNodes() == null || context == null) {
            return false;
        }
        for (FlowNode node : graph.getNodes().values()) {
            if (node == null || !isCommandStartNode(node.getType())) {
                continue;
            }
            if (node.getInputValues() == null) {
                node.setInputValues(new HashMap<>());
            }
            node.getInputValues().put("command", normalizeCommandLabel(context.command));
            node.getInputValues().put("subcommands", context.subcommands != null ? new ArrayList<>(context.subcommands) : new ArrayList<>());
            node.getInputValues().put("structured", context.structured != null && context.structured);
            return true;
        }
        return false;
    }

    protected boolean isCommandStartNode(String type) {
        if (type == null) {
            return false;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "event.resync.command".equals(normalized) || "event:resync_command".equals(normalized);
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
                JsonObject decoded = FlowJson.parse(trimmed).getAsJsonObject();
                parsed.command = normalizeCommandLabel(commandJsonText(decoded, "command"));
                parsed.subcommands = commandJsonStrings(decoded, "subcommands");
                JsonElement structured = decoded.get("structured");
                parsed.structured = structured != null && !structured.isJsonNull() && structured.getAsBoolean();
                return parsed;
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
        if (command.subcommands.isEmpty() && !command.structured) return command.command;
        JsonObject encoded = new JsonObject();
        encoded.addProperty("command", command.command);
        JsonArray subcommands = new JsonArray();
        command.subcommands.forEach(subcommands::add);
        encoded.add("subcommands", subcommands);
        encoded.addProperty("structured", command.structured);
        return FlowJson.write(encoded);
    }

    private static String commandJsonText(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static List<String> commandJsonStrings(JsonObject value, String name) {
        JsonElement element = value.get(name);
        if (element == null || !element.isJsonArray()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        element.getAsJsonArray().forEach(item -> {
            if (item.isJsonPrimitive()) result.add(item.getAsString());
        });
        return result;
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
            case "Paths" -> "Subcommand paths matched after the root command.\nEach row is one path.\nTokens are separated by spaces.\nPlaceholders:\n<text:list_id> Values from a Text list or keys from a Text map.\n<text:map_id:values> Values from a Text map.\n<online_player> Online Bukkit player name.\n<offline_player> Known offline player name.\n<player_with_perm:permission.node> Online player with that permission.\n<any> Any single argument token.\nAny other <name> also matches one token.";
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

    protected void updateStudioTabStates() {
        if (studioTabsManager == null) {
            return;
        }
        long refreshAt = System.nanoTime();
        if (refreshAt < nextStudioTabStateRefreshAt) {
            return;
        }
        nextStudioTabStateRefreshAt = refreshAt + STUDIO_TAB_STATE_REFRESH_NANOS;
        long now = System.currentTimeMillis();
        boolean layoutChanged = false;
        for (TabsManager.Tab tab : studioTabsManager.getTabs()) {
            if (!(tab.getData() instanceof String key)) {
                continue;
            }
            StudioDocument document = findStudioDocument(key);
            if (document == null) {
                continue;
            }
            Long deadline = pendingStudioTabDiscards.get(key);
            if (deadline != null && deadline < now) {
                clearStudioDiscardPrompt(key);
                deadline = null;
            }
            boolean dirty = isStudioDocumentDirty(document);
            if (!dirty && deadline != null) {
                clearStudioDiscardPrompt(key);
                deadline = null;
            }
            boolean unsaved = dirty && deadline == null;
            if (tab.isUnsaved() != unsaved) {
                tab.setUnsaved(unsaved);
                layoutChanged = true;
            }
            if (deadline == null && (!tab.getName().equals(document.title()) || !Objects.equals(tab.getIconPath(), studioResourceIconPath(document.type(), document.id())))) {
                tab.setName(document.title());
                tab.setIconPath(studioResourceIconPath(document.type(), document.id()));
                layoutChanged = true;
            }
        }
        if (layoutChanged) {
            studioTabsManager.updateLayout();
        }
    }

    private void clearStudioDiscardPrompts() {
        for (String key : List.copyOf(pendingStudioTabDiscards.keySet())) {
            clearStudioDiscardPrompt(key);
        }
    }

    private void clearStudioDiscardPrompt(String key) {
        if (key == null) {
            return;
        }
        pendingStudioTabDiscards.remove(key);
        if (studioTabsManager == null) {
            return;
        }
        StudioDocument document = findStudioDocument(key);
        TabsManager.Tab tab = findStudioTab(key, studioTabsManager.getTabs());
        if (document == null || tab == null) {
            return;
        }
        tab.setName(document.title());
        tab.setIconPath(studioResourceIconPath(document.type(), document.id()));
        tab.setUnsaved(isStudioDocumentDirty(document));
        studioTabsManager.updateLayout();
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
            } else if (!pendingStudioTabDiscards.containsKey(document.key())) {
                tab.setName(document.title());
                tab.setIconPath(studioResourceIconPath(document.type(), document.id()));
            }
            tab.setUnsaved(isStudioDocumentDirty(document) && !pendingStudioTabDiscards.containsKey(document.key()));
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
        clearStudioDiscardPrompts();
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

    protected void studioDocumentClosed(StudioDocument document) {
    }

    protected void studioDocumentRenamed(StudioDocument previous, StudioDocument renamed) {
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
        if (dispatchOverlayMouseDragged(event.retarget(this, event.x(), event.y(), event.deltaX(), event.deltaY()))) {
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
        if (studioMode && studioContentBrowser != null) {
            studioContentBrowser.updateShortcutFocus(event);
        }
        if (handleActiveStudioSelectorMouseClicked(event)) {
            setFocusedWidget(null);
            return true;
        }
        if (dispatchOverlayMouseClicked(event.retarget(this, event.x(), event.y()))) {
            return true;
        }
        ReSyncStudioView priorityView = activeStudioView();
        if (priorityView instanceof StudioPriorityInputView && priorityView.mouseClicked(event)) {
            setFocusedWidget(null);
            return true;
        }
        if (handleStudioHudMouseClicked(event)) {
            ReSyncStudioView view = activeStudioView();
            if (view != null) {
                view.clearFocus();
            }
            return true;
        }
        ReSyncStudioView view = activeStudioView();
        if (view != null) {
            if (activeStudioViewUsesResourcePanel() && studioResourcePanel != null && Widget.dispatchMouseClicked(studioResourcePanel.container(), event)) {
                view.clearFocus();
                return true;
            }
            setFocusedWidget(null);
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
        if (studioResourceDrag != null && event.button() == ReMouseButton.LEFT) {
            if (studioMode && studioContentBrowser != null) {
                Widget.dispatchMouseReleased(studioContentBrowser, event);
            }
            ReSyncResourceDragPayload payload = studioResourceDrag;
            studioResourceDrag = null;
            boolean accepted = dropStudioResource(payload, event);
            if (studioResourceDragWidget != null && studioResourceDragDestination == null) {
                if (accepted) {
                    completeStudioResourceDragTo((int) event.x() - 4, (int) event.y() - 4, 8, 8, true);
                } else {
                    completeStudioResourceDragTo(studioResourceDragSourceX, studioResourceDragSourceY,
                        studioResourceDragSourceWidth, studioResourceDragSourceHeight, false);
                }
            }
            return true;
        }
        if (studioTabsManager != null && Widget.dispatchMouseReleased(studioTabsManager, event)) {
            return true;
        }
        if (handleActiveStudioSelectorMouseReleased(event)) {
            return true;
        }
        if (dispatchOverlayMouseReleased(event.retarget(this, event.x(), event.y()))) {
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

    public void beginStudioResourceDrag(ReSyncResourceDragPayload payload, AnimatedWidget transition, int grabX, int grabY) {
        if (payload != null && !payload.isFolder() && transition != null) {
            clearStudioResourceDragWidget();
            studioResourceDrag = payload;
            studioResourceDragWidget = transition;
            studioResourceDragSourceX = transition.getX();
            studioResourceDragSourceY = transition.getY();
            studioResourceDragSourceWidth = transition.getWidth();
            studioResourceDragSourceHeight = transition.getHeight();
            String label = payload.displayName() == null || payload.displayName().isBlank() ? payload.id() : payload.displayName();
            studioResourceDragGrabX = Math.clamp(grabX, 0, studioResourceDragSourceWidth);
            studioResourceDragGrabY = Math.clamp(grabY, 0, studioResourceDragSourceHeight);
            studioResourceDragDetachedWidth = Math.clamp(Math.max(tr.getWidth(label) + 40, studioResourceDragGrabX + 12),
                96, studioResourceDragSourceWidth);
            studioResourceDragDestination = null;
        }
    }

    protected void completeStudioResourceDragTo(int x, int y, int width, int height, boolean close) {
        if (studioResourceDragWidget == null) {
            return;
        }
        studioResourceDragDestination = new StudioResourceDragDestination(x, y, Math.max(1, width), Math.max(1, height), close);
        studioResourceDragWidget.setAnimateLayoutPosition(true);
        studioResourceDragWidget.setPosition(x, y);
        studioResourceDragWidget.setWidth(Math.max(1, width));
        studioResourceDragWidget.setHeight(Math.max(1, height));
    }

    protected int[] takeStudioResourceDragBounds() {
        if (studioResourceDragWidget == null) {
            return null;
        }
        int[] bounds = {
            studioResourceDragWidget.getX(),
            studioResourceDragWidget.getY(),
            studioResourceDragWidget.getWidth(),
            studioResourceDragWidget.getHeight()
        };
        clearStudioResourceDragWidget();
        studioResourceDragDestination = null;
        return bounds;
    }

    private void clearStudioResourceDragWidget() {
        if (studioResourceDragWidget == null) {
            return;
        }
        studioResourceDragWidget.visible = false;
        WidgetCleanup.cleanup(studioResourceDragWidget);
        studioResourceDragWidget = null;
    }

    protected boolean dropStudioResource(ReSyncResourceDragPayload payload, ReMouseEvent event) {
        return false;
    }

    protected boolean handleStudioWorkspaceKeyPressed(int keyCode, int scanCode, int modifiers) {
        ReKeyEvent event = currentKeyPressedEvent(keyCode, scanCode, modifiers, false);
        return event.finish(handleStudioWorkspaceKeyPressed(event));
    }

    protected boolean handleStudioWorkspaceKeyPressed(ReKeyEvent event) {
        if (collaborationChatInput != null) {
            return false;
        }
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
        if (collaborationChatInput != null) {
            return false;
        }
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
        if (dispatchOverlayMouseScrolled(event.retarget(this, event.x(), event.y()))) {
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

    public ItemSelectorWidget activeStudioSelector() {
        return hasActiveStudioSelector() ? activeStudioSelector : null;
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
        updateStudioPresence(mouseX, mouseY);
        if (studioMode) {
            updateStudioTabStates();
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
            if (widget instanceof ContextMenuWidget) {
                widget.render(context, mouseX, mouseY, delta);
            }
        }
        renderStudioResourceDrag(context, mouseX, mouseY, delta);
        renderStudioCollaboration(context);
        renderCollaborationChatInput(context, mouseX, mouseY, delta);

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
            if (widget instanceof ContextMenuWidget menu && menu.isVisible()) {
                menu.renderHintOverlay(context);
            }
        }
        if (!isOverlayPassDeferred()) {
            Screen viewScreen = activeStudioViewScreen();
            if (viewScreen != null && viewScreen != this) {
                viewScreen.renderOverlayPass(context, mouseX, mouseY, delta);
            }
        }
        if (!isOverlayPassDeferred()) {
            context.pushScissorState();
            context.clearScissor();
            try {
                renderStudioCollaborationOverlay(context);
            } finally {
                context.popScissorState();
            }
        }
    }

    protected void renderStudioCollaborationOverlay(IDrawContext context) {
    }

    private void updateStudioPresence(int mouseX, int mouseY) {
        if (!studioMode) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null) {
            return;
        }
        String type = activeStudioDocument != null ? safeStudioText(activeStudioDocument.type()) : "";
        String id = activeStudioDocument != null ? safeStudioText(activeStudioDocument.id()) : "";
        String document = type + '\u0000' + id;
        boolean typing = collaborationChatInput != null && !collaborationChatInput.getText().isBlank();
        long now = System.currentTimeMillis();
        if (mouseX == lastPresenceX && mouseY == lastPresenceY && document.equals(lastPresenceDocument)
            && typing == lastPresenceTyping && now - lastPresenceAt < 1000L) {
            return;
        }
        if (document.equals(lastPresenceDocument) && typing == lastPresenceTyping && now - lastPresenceAt < 35L) {
            return;
        }
        lastPresenceX = mouseX;
        lastPresenceY = mouseY;
        lastPresenceDocument = document;
        lastPresenceTyping = typing;
        lastPresenceAt = now;
        ReSyncStudioView view = activeStudioView();
        ReSyncFlowClient client = manager.existingFlowClient(studioServerId());
        if (client == null) {
            return;
        }
        client.collaboration().publishPresence(type, id,
            view == null ? "" : type.isBlank() ? "studio" : type,
            width > 0 ? (double) mouseX / width : 0.0, height > 0 ? (double) mouseY / height : 0.0,
            activeStudioDocument != null, typing);
    }

    private void renderStudioCollaboration(IDrawContext context) {
        FlowManager manager = FlowManager.getInstance();
        if (!studioMode || manager == null) {
            return;
        }
        ReSyncFlowClient client = manager.existingFlowClient(studioServerId());
        if (client == null) {
            return;
        }
        ensureCollaborationChat(client.collaboration());
        ReSyncCollaborationClient collaboration = client.collaboration();
        List<ReSyncCollaborationClient.Presence> collaborators = collaboration.snapshot();
        renderStudioCollaborators(context, collaboration, collaborators);
        renderRemoteCursors(context, collaboration, collaborators);
        renderRemoteResourceChange(context, collaboration);
    }

    private void ensureCollaborationChat(ReSyncCollaborationClient collaboration) {
        collaborationOverlay.bind(collaboration, this::receiveCollaborationMessage);
    }

    private void receiveCollaborationMessage(ReSyncCollaborationClient.Message message) {
        if (message == null || collaborationOverlay.service() == null || !studioMode) {
            return;
        }
        ReSyncCollaborationClient.Presence presence = collaborationOverlay.service().presence(message.authorSessionId());
        boolean cursorVisible = activeStudioDocument != null && presence != null && presence.active()
            && Objects.equals(activeStudioDocument.type(), message.resourceType())
            && Objects.equals(activeStudioDocument.id(), message.resourceId());
        if (cursorVisible) {
            return;
        }
        String name = message.author() != null
            ? CollaborationOverlay.compactName(message.author().displayName(), 100) : "Collaborator";
        new Notification(name, message.message(), Notification.Type.INFO);
        if (studioContentBrowser != null && message.resourceType() != null && !message.resourceType().isBlank()
            && message.resourceId() != null && !message.resourceId().isBlank()) {
            studioContentBrowser.highlightCollaborationChat(message.resourceType(), message.resourceId(),
                presence != null ? collaborationColor(presence) : message.color());
        }
    }

    private void renderCollaborationChatInput(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (collaborationChatInput == null) {
            return;
        }
        collaborationChatInput.setWidth(Math.clamp(width - 40, 140, 280));
        collaborationChatInput.setPosition((width - collaborationChatInput.getWidth()) / 2, Math.max(4, height - 30));
        collaborationChatInput.render(context, mouseX, mouseY, delta);
    }

    private void renderStudioCollaborators(IDrawContext context, ReSyncCollaborationClient collaboration,
                                           List<ReSyncCollaborationClient.Presence> collaborators) {
        int x = width - studioHeaderRightReserve() - 10;
        int shown = 0;
        for (int i = collaborators.size() - 1; i >= 0; i--) {
            ReSyncCollaborationClient.Presence presence = collaborators.get(i);
            if (collaboration.isSelf(presence) || presence.identity() == null) {
                continue;
            }
            String slot = "header:" + presence.sessionId();
            IconButton badge = collaborationBadge(slot, presence, presence.identity(), collaborationColor(presence), 92);
            x -= badge.getWidth();
            if (x < Math.max(width / 2, 200)) {
                break;
            }
            badge.setPosition(x, 5);
            badge.render(context, 0, 0, 0);
            renderCollaborationMessages(context, slot, presence, badge, true, 160);
            renderCollaborationTyping(context, slot, presence, badge, 160);
            x -= 4;
            shown++;
            if (shown == 5) {
                break;
            }
        }
    }

    private void renderRemoteCursors(IDrawContext context, ReSyncCollaborationClient collaboration,
                                     List<ReSyncCollaborationClient.Presence> collaborators) {
        if (activeStudioDocument == null || rendersWorkspaceCursors()) {
            return;
        }
        for (ReSyncCollaborationClient.Presence presence : collaborators) {
            if (collaboration.isSelf(presence) || !presence.active() || presence.identity() == null
                || !Objects.equals(activeStudioDocument.type(), presence.resourceType())
                || !Objects.equals(activeStudioDocument.id(), presence.resourceId())) {
                continue;
            }
            int cursorX = Math.clamp((int) Math.round(presence.x() * width), 0, Math.max(0, width - 1));
            int cursorY = Math.clamp((int) Math.round(presence.y() * height), 0, Math.max(0, height - 1));
            renderCollaborationCursor(context, presence, cursorX, cursorY);
        }
    }

    protected boolean rendersWorkspaceCursors() {
        return false;
    }

    protected void renderCollaborationCursor(IDrawContext context, ReSyncCollaborationClient.Presence presence, int cursorX, int cursorY) {
        collaborationOverlay.renderCursor(context, presence, cursorX, cursorY, width, height);
    }

    protected CollaborationOverlay collaborationOverlay() {
        return collaborationOverlay;
    }

    protected void renderCollaborationMessages(IDrawContext context, String slot,
                                               ReSyncCollaborationClient.Presence presence,
                                               AnimatedWidget anchor, boolean below, int maxMessageWidth) {
        collaborationOverlay.renderMessages(context, slot, presence, anchor,
            below ? CollaborationOverlay.Order.BELOW : CollaborationOverlay.Order.ABOVE,
            maxMessageWidth, width, height);
    }

    protected void renderCollaborationTyping(IDrawContext context, String slot,
                                             ReSyncCollaborationClient.Presence presence,
                                             AnimatedWidget anchor, int maxMessageWidth) {
        collaborationOverlay.renderTyping(context, slot, presence, anchor, maxMessageWidth);
    }

    protected ReSyncCollaborationClient.Presence collaborationPresence(String sessionId) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || sessionId == null) {
            return null;
        }
        ReSyncFlowClient client = manager.existingFlowClient(studioServerId());
        if (client == null) {
            return null;
        }
        return client.collaboration().snapshot().stream()
            .filter(presence -> Objects.equals(sessionId, presence.sessionId()))
            .findFirst()
            .orElse(null);
    }

    private void renderRemoteResourceChange(IDrawContext context, ReSyncCollaborationClient collaboration) {
        if (activeStudioDocument == null) {
            return;
        }
        ReSyncCollaborationClient.ResourceChange change = collaboration.resourceChange(activeStudioDocument.type(), activeStudioDocument.id());
        if (change == null || collaboration.isOwnChange(change) || System.currentTimeMillis() - change.changedAt() > 8000L) {
            return;
        }
        String author = change.author() != null ? safeStudioText(change.author().displayName()) : "Collaborator";
        String message = change.deleted() ? author + " Deleted This Resource" : author + " Updated This Resource";
        if (collaborationChangeBadge == null) {
            collaborationChangeBadge = new IconButton.Builder()
                .label(message)
                .size(tr.getWidth(message) + 16, 20)
                .centered(true)
                .autoWidthOnTextChange(true)
                .animateLayout(false)
                .entranceCorner(AnimatedWidget.EntranceCorner.CENTER)
                .active(false)
                .build();
            collaborationChangeBadge.setCursorHoverReactive(false);
        }
        collaborationChangeBadge.setMessage(message);
        collaborationChangeBadge.accentType = change.deleted() ? ThemeManager.getAccent("danger") : ThemeManager.getAccent("nice");
        collaborationChangeBadge.setPosition(Math.max(8, (width - collaborationChangeBadge.getWidth()) / 2), 29);
        collaborationChangeBadge.render(context, 0, 0, 0);
    }

    protected Identifier collaborationAvatar(ReSyncCollaborationClient.Presence presence) {
        return collaborationOverlay.avatar(presence);
    }

    protected Identifier collaborationAvatar(ReSyncCollaborationClient.Identity identity) {
        return collaborationOverlay.avatar(identity);
    }

    protected int collaborationColor(ReSyncCollaborationClient.Presence presence) {
        return collaborationOverlay.color(presence);
    }

    protected Accent collaborationAccent(ReSyncCollaborationClient.Presence presence) {
        return collaborationOverlay.accent(presence);
    }

    protected IconButton collaborationBadge(String slot, ReSyncCollaborationClient.Presence presence,
                                             ReSyncCollaborationClient.Identity identity, int color, int maxNameWidth) {
        return collaborationOverlay.badge(slot, presence, identity, color, maxNameWidth);
    }

    @Override
    public void removed() {
        persistedStudioRestoreDisposed = true;
        ++persistedStudioRestoreGeneration;
        persistedStudioRestoreRequestAllowed = false;
        restoringPersistedStudioDocuments = false;
        if (studioContentBrowser != null) {
            studioContentBrowser.cancelCatalogPreload();
        }
        pendingPersistedStudioDocumentRestores.clear();
        successfulPersistedStudioDocumentRestores.clear();
        terminalPersistedStudioDocumentRestores.clear();
        collaborationOverlay.close();
        dismissCollaborationChat(false);
        FlowManager manager = FlowManager.getInstance();
        if (studioMode && manager != null && manager.isFlowClientReady(studioServerId())) {
            ReSyncFlowClient client = manager.existingFlowClient(studioServerId());
            if (client != null) {
                client.collaboration().publishPresence("", "", "", 0.0, 0.0, false, false);
            }
        }
        super.removed();
    }

    private void renderStudioResourceDrag(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (studioResourceDragWidget == null) {
            return;
        }
        if (studioResourceDrag != null) {
            int targetX = mouseX - studioResourceDragGrabX;
            int targetY = mouseY - studioResourceDragGrabY;
            studioResourceDragWidget.setPosition(targetX, targetY);
            studioResourceDragWidget.setWidth(studioResourceDragDetachedWidth);
            studioResourceDragWidget.setHeight(Math.max(18, studioResourceDragSourceHeight));
        }
        studioResourceDragWidget.render(context, mouseX, mouseY, delta);
        if (studioResourceDragDestination == null) {
            return;
        }
        StudioResourceDragDestination destination = studioResourceDragDestination;
        boolean arrived = Math.abs(studioResourceDragWidget.getX() - destination.x()) <= 1
            && Math.abs(studioResourceDragWidget.getY() - destination.y()) <= 1
            && Math.abs(studioResourceDragWidget.getWidth() - destination.width()) <= 1
            && Math.abs(studioResourceDragWidget.getHeight() - destination.height()) <= 1;
        if (!arrived) {
            return;
        }
        if (!destination.close()) {
            clearStudioResourceDragWidget();
            studioResourceDragDestination = null;
        } else if (!studioResourceDragWidget.isClosingAnimationActive()) {
            studioResourceDragWidget.startClosingAnimation(AnimatedWidget.ClosingAnchor.CENTER);
        } else if (studioResourceDragWidget.isClosingAnimationFinished()) {
            clearStudioResourceDragWidget();
            studioResourceDragDestination = null;
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
            case ReSyncResourceDragPayload.VARIABLE_DEFINITION -> "edit.png";
            case ReSyncResourceDragPayload.TIMER_DEFINITION -> "history.png";
            case ReSyncResourceDragPayload.SCHEDULE_DEFINITION -> "calendar.png";
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
            case "projectile" -> "item.png";
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
        private T savedSnapshot;
        private boolean savedSnapshotInitialized;
        private int cursor;
        private int savedCursor;
        private final Map<Long, SavePoint<T>> savePoints = new HashMap<>();

        public History(Supplier<T> snapshotSupplier, Consumer<T> restoreConsumer, int limit) {
            this.snapshotSupplier = snapshotSupplier;
            this.restoreConsumer = restoreConsumer;
            this.limit = Math.max(1, limit);
        }

        public void capture() {
            if (restoring) {
                return;
            }
            T snapshot = snapshotSupplier.get();
            if (!savedSnapshotInitialized) {
                savedSnapshot = snapshot;
                savedSnapshotInitialized = true;
            }
            undoStack.add(snapshot);
            if (undoStack.size() > limit) {
                undoStack.removeFirst();
            }
            if (!redoStack.isEmpty() && savedCursor > cursor) {
                savedCursor = Integer.MIN_VALUE;
            }
            redoStack.clear();
            cursor++;
        }

        public void clear() {
            undoStack.clear();
            redoStack.clear();
            cursor = 0;
            savedCursor = 0;
            savedSnapshot = snapshotSupplier.get();
            savedSnapshotInitialized = true;
            savePoints.clear();
        }

        public boolean isDirty() {
            return cursor != savedCursor;
        }

        public boolean isDirty(BiPredicate<T, T> equality) {
            if (equality == null || !savedSnapshotInitialized) {
                return isDirty();
            }
            return !equality.test(snapshotSupplier.get(), savedSnapshot);
        }

        public void markSaved() {
            savedSnapshot = snapshotSupplier.get();
            savedSnapshotInitialized = true;
            savedCursor = cursor;
            savePoints.clear();
        }

        public void markSaving(long sequence) {
            if (sequence > 0L) {
                savePoints.put(sequence, new SavePoint<>(snapshotSupplier.get(), cursor));
                if (savePoints.size() > 32) {
                    savePoints.remove(savePoints.keySet().stream().min(Long::compareTo).orElse(sequence));
                }
            }
        }

        public void markSaved(long sequence) {
            SavePoint<T> point = sequence > 0L ? savePoints.remove(sequence) : null;
            if (point == null) {
                if (sequence <= 0L) {
                    markSaved();
                }
                return;
            }
            savedSnapshot = point.snapshot();
            savedSnapshotInitialized = true;
            savedCursor = point.cursor();
            savePoints.keySet().removeIf(value -> value <= sequence);
        }

        public void discardChanges() {
            if (!savedSnapshotInitialized) {
                clear();
                return;
            }
            restoring = true;
            try {
                restoreConsumer.accept(savedSnapshot);
                undoStack.clear();
                redoStack.clear();
                cursor = 0;
                savedCursor = 0;
                savedSnapshot = snapshotSupplier.get();
                savePoints.clear();
            } finally {
                restoring = false;
            }
        }

        public void rebase(UnaryOperator<T> rebaser) {
            if (rebaser == null || restoring) {
                return;
            }
            undoStack.replaceAll(rebaser);
            redoStack.replaceAll(rebaser);
            if (savedSnapshotInitialized) {
                savedSnapshot = rebaser.apply(savedSnapshot);
            }
            savePoints.replaceAll((sequence, point) -> new SavePoint<>(rebaser.apply(point.snapshot()), point.cursor()));
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
                cursor--;
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
                cursor++;
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

        private record SavePoint<T>(T snapshot, int cursor) {
        }
    }
}
