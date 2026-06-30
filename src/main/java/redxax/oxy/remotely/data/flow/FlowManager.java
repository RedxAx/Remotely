package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingUpdate;
import redxax.oxy.remotely.data.flow.world.WorldChannelMessage;
import redxax.oxy.remotely.data.flow.world.WorldDashboardEntry;
import redxax.oxy.remotely.data.flow.world.WorldInventoryGroup;
import redxax.oxy.remotely.data.flow.world.WorldMapSnapshot;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.data.flow.world.WorldProfileSettings;
import redxax.oxy.remotely.data.flow.world.WorldRegistryEntry;
import redxax.oxy.remotely.data.flow.world.WorldSnapshot;
import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.data.TriggerType;
import redxax.oxy.remotely.flow.data.Visual;
import redxax.oxy.remotely.flow.ui.AdvancementDesignerScreen;
import redxax.oxy.remotely.flow.ui.DialogDesignerScreen;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.FocusedJsonResourceDesignerScreen;
import redxax.oxy.remotely.flow.ui.GuiEditOverlayState;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import redxax.oxy.remotely.flow.ui.LootTableDesignerScreen;
import redxax.oxy.remotely.flow.ui.NpcDesignerScreen;
import redxax.oxy.remotely.flow.ui.ScoreboardDesignerScreen;
import redxax.oxy.remotely.flow.ui.TabDesignerScreen;
import redxax.oxy.remotely.flow.ui.VillageDesignerScreen;
import redxax.oxy.remotely.ui.widgets.management.PlayerDataPopup;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import restudio.rebase.instance.Instance;
import restudio.rebase.minecraft.MinecraftPlayerLocation;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.MarketplaceModels;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rebase.restudio.marketplace.MarketplaceContentImportService;
import restudio.rebase.ui.worldmap.WorldMapScreen;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.screens.DesktopWindowsOverlay;
import restudio.rescreen.ui.widgets.ScreenWindowWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FlowManager {
    private static FlowManager INSTANCE;
    private static final List<String> FLOW_TEMPLATES = List.of("Blank", "Command");
    private static final int MAX_TARGETED_FLOW_REFRESH_IDS = 16;
    private final RemotelyClient client;
    private final ReSyncConnectionManager connectionManager;
    private final FlowDebugController debugController;
    private final ReSyncWorldService worldService;
    private final ReSyncPlayerService playerService;
    private final SyncedResourceCache<FlowGraph> flowStore = new SyncedResourceCache<>(FlowGraph::getId, FlowGraph::getId);
    private final SyncedResourceCache<GuiDefinition> guiStore = new SyncedResourceCache<>(GuiDefinition::getId, g -> g.getTitle() != null ? g.getTitle() : g.getId());
    private final SyncedResourceCache<ScoreboardDefinition> scoreboardStore = new SyncedResourceCache<>(ScoreboardDefinition::getId, s -> s.getTitle() != null ? s.getTitle() : s.getId());
    private final SyncedResourceCache<TabDefinition> tabStore = new SyncedResourceCache<>(TabDefinition::getId, TabDefinition::getId);
    private final SyncedResourceCache<CustomContentDefinition> customContentStore = new SyncedResourceCache<>(CustomContentDefinition::getId, c -> c.getDisplayName() != null ? c.getDisplayName() : c.getId());
    private final SyncedResourceCache<ReSyncProjectMetadata> projectMetadataStore = new SyncedResourceCache<>(m -> m.getServerId() == null || m.getServerId().isBlank() ? "project" : m.getServerId(), m -> "Project");
    private final Map<ReSyncResourceType, SyncedResourceCache<JsonObject>> jsonResourceStores = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> serverCapabilities = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> messageLogPages = new ConcurrentHashMap<>();
    private final Map<String, List<TriggerBinding>> triggerBindings = new ConcurrentHashMap<>();
    private final Map<String, Integer> projectCatalogRevisions = new ConcurrentHashMap<>();
    private final Map<String, Integer> hydratedProjectCatalogRevisions = new ConcurrentHashMap<>();
    private final Map<String, PendingFlowWorkspaceRefresh> pendingFlowWorkspaceRefreshes = new ConcurrentHashMap<>();
    private final Object flowWorkspaceRefreshLock = new Object();
    private final Set<String> loadedProjectMetadataLists = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingProjectMetadataDocuments = ConcurrentHashMap.newKeySet();
    private final StudioFullEditorSession studioFullEditorSession = new StudioFullEditorSession();
    private volatile boolean guiOverlayEditable;
    private volatile String guiOverlayServerId;
    private volatile String guiOverlayGuiId;
    private volatile String guiOverlayFlowId;
    private volatile boolean editTargetOverlayEditable;
    private volatile String editTargetOverlayServerId;
    private volatile String editTargetOverlayResourceType;
    private volatile String editTargetOverlayResourceId;
    private volatile String editTargetOverlayFlowId;
    private volatile String marketplaceImportServerId;
    private final AtomicInteger overlayRevision = new AtomicInteger();
    private final AtomicInteger projectCatalogRevision = new AtomicInteger();
    private final Gson gson = new Gson();

    public FlowManager(RemotelyClient client, ReStudioApiClient apiClient) {
        this.client = client;
        this.connectionManager = new ReSyncConnectionManager(client, apiClient);
        this.debugController = new FlowDebugController(this);
        this.worldService = new ReSyncWorldService();
        this.playerService = new ReSyncPlayerService();
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            if (usesJsonResourceStore(type)) {
                jsonResourceStores.put(type, new SyncedResourceCache<>(this::jsonResourceId, this::jsonResourceName));
            }
        }
        INSTANCE = this;
        MarketplaceContentImportService.register(this::importMarketplaceContent);
    }

    public static FlowManager getInstance() {
        return INSTANCE;
    }

    public void shutdown() {
        connectionManager.shutdownAll();
        INSTANCE = null;
    }

    public void openReSyncStudio(String serverId, ClientServerView server, String loaderHint) {
        openReSyncStudio(serverId, server, loaderHint, server != null ? server.name : "");
    }

    public void openReSyncStudio(String serverId, ClientServerView server, String loaderHint, String serverTitle) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        marketplaceImportServerId = actualServerId;
        connectionManager.resolveAndStoreProfile(actualServerId, server);
        FlowEditorScreen screen = new FlowEditorScreen(new FlowGraph(), actualServerId, ScreenManager.getInstance().getCurrentScreen(), server, loaderHint, serverTitle).enableStudioMode();
        client.getHost().setScreen(screen);
    }

    public void openLiveReSyncStudio(ReSyncLiveServerSession session) {
        if (session == null || session.serverId() == null || session.serverId().isBlank()) {
            new Notification("ReSync", "ReSync Unavailable", Notification.Type.WARN);
            return;
        }
        marketplaceImportServerId = session.serverId();
        activateLiveReSyncSession(session);
        FlowEditorScreen existingScreen = FlowEditorScreen.getStudioScreen(session.serverId());
        if (existingScreen != null) {
            activateStudioScreen(existingScreen, false);
            flushPendingStudioEditTarget(session.serverId());
            return;
        }
        FlowEditorScreen screen = new FlowEditorScreen(new FlowGraph(), session.serverId(), ScreenManager.getInstance().getCurrentScreen(), null, "", session.displayName()).enableStudioMode();
        client.getHost().setScreen(screen);
    }

    public void openLiveStudioDesigner(ReSyncLiveServerSession session, String type, String id, boolean fullEditor) {
        openLiveStudioDesigner(session, type, id, fullEditor, null);
    }

    public void openLiveStudioDesigner(ReSyncLiveServerSession session, String type, String id, boolean fullEditor, Object parent) {
        studioFullEditorSession.open(session, new StudioEditTarget(type, id, fullEditor), parent);
    }

    public void createLiveStudioAdvancementTree(ReSyncLiveServerSession session, boolean fullEditor) {
        createLiveStudioAdvancementTree(session, fullEditor, null);
    }

    public void createLiveStudioAdvancementTree(ReSyncLiveServerSession session, boolean fullEditor, Object parent) {
        if (session == null || session.serverId() == null || session.serverId().isBlank()) {
            return;
        }
        marketplaceImportServerId = session.serverId();
        activateLiveReSyncSession(session);
        String id = nextAdvancementTreeId(session.serverId());
        JsonObject tree = createJsonResource(session.serverId(), ReSyncResourceType.ADVANCEMENT_TREE, id, ReSyncResourceType.ADVANCEMENT_TREE.defaultFolder());
        if (tree == null) {
            return;
        }
        saveJsonResource(session.serverId(), ReSyncResourceType.ADVANCEMENT_TREE, tree);
        ReSyncProjectMetadata metadata = getProjectMetadata(session.serverId());
        ReSyncProjectMetadata.ResourceEntry entry = metadata.ensureResource(ReSyncResourceDragPayload.ADVANCEMENT_TREE, id, id, ReSyncResourceType.ADVANCEMENT_TREE.defaultFolder());
        entry.setPath(ReSyncResourceType.ADVANCEMENT_TREE.defaultFolder());
        saveProjectMetadata(session.serverId(), metadata);
        openLiveStudioDesigner(session, ReSyncResourceDragPayload.ADVANCEMENT_TREE, id, fullEditor, parent);
    }

    public ReSyncFlowClient activateLiveReSyncSession(ReSyncLiveServerSession session) {
        if (session == null || session.serverId() == null || session.serverId().isBlank()) {
            return null;
        }
        marketplaceImportServerId = session.serverId();
        return connectionManager.activateLiveSession(session);
    }

    public void clearLiveReSyncSession(String serverId) {
        if (serverId == null || !serverId.startsWith("live:")) {
            return;
        }
        if (serverId.equals(guiOverlayServerId) || serverId.equals(editTargetOverlayServerId)) {
            clearOverlayState();
            GuiEditOverlayState.clear();
        }
        closeServerConnection(serverId);
    }

    public void ensureFlowClientForStartup(String serverId, ClientServerView server, boolean showNotifications) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        connectionManager.resolveAndStoreProfile(serverId, server);
        connectionManager.ensureFlowClient(serverId, showNotifications);
    }

    public boolean isFlowClientConnected(String serverId) {
        return connectionManager.isFlowClientConnected(serverId);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId) {
        return connectionManager.ensureFlowClient(serverId);
    }

    public FlowDebugController getDebugController() {
        return debugController;
    }

    public void closeServerConnection(String serverId) {
        connectionManager.closeServerConnection(serverId, () -> {
            flowStore.clearForServer(serverId);
            guiStore.clearForServer(serverId);
            scoreboardStore.clearForServer(serverId);
            tabStore.clearForServer(serverId);
            customContentStore.clearForServer(serverId);
            projectMetadataStore.clearForServer(serverId);
            jsonResourceStores.values().forEach(store -> store.clearForServer(serverId));
            serverCapabilities.remove(serverId);
            studioFullEditorSession.clear(serverId);
            projectCatalogRevisions.remove(serverId);
            hydratedProjectCatalogRevisions.remove(serverId);
            loadedProjectMetadataLists.remove(serverId);
            pendingProjectMetadataDocuments.remove(serverId);
            playerService.clearCache(serverId);
            worldService.clearCache(serverId);
            WorldGenManager.getInstance().clearCache(serverId);
        });
    }

    public void provisionReSyncForReStudioServer(String serverId, Consumer<Boolean> callback) {
        connectionManager.provisionReSyncForReStudioServer(serverId, callback);
    }

    public void updateReSyncForReStudioServer(String serverId, Consumer<Boolean> callback) {
        connectionManager.updateReSyncForReStudioServer(serverId, callback);
    }

    public CompletableFuture<String> getReSyncVersionForReStudioServer(String serverId) {
        return connectionManager.getReSyncVersionForReStudioServer(serverId);
    }

    public String getFlowAvailabilityIssue(String serverId, ClientServerView server) {
        return connectionManager.getFlowAvailabilityIssue(serverId, server);
    }

    public Instance getInstanceByServerId(String serverId) {
        return connectionManager.getInstanceByServerId(serverId);
    }

    public Instance findInstanceByServerId(String serverId, ClientServerView server) {
        return connectionManager.findInstanceByServerId(serverId, server);
    }

    public ReStudioApiClient getApiClient() {
        return connectionManager.getApiClient();
    }

    public List<String> getFlowTemplates() {
        return FLOW_TEMPLATES;
    }

    public CompletableFuture<Boolean> isReSyncPluginInstalled(String serverId) {
        if (connectionManager.getApiClient() == null) {
            return CompletableFuture.completedFuture(false);
        }
        return connectionManager.getReSyncVersionForReStudioServer(serverId)
                .thenApply(version -> version != null && !version.isBlank())
                .exceptionally(ex -> false);
    }

    public String normalizeReSyncNotificationMessage(String message) {
        return connectionManager.normalizeReSyncNotificationMessage(message);
    }

    public void requestInitialFlowData(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId, true);
        if (flowClient == null) {
            return;
        }
        if (!hasResourceData(flowStore, serverId)) {
            flowClient.requestFlowList();
        }
        if (!hasResourceData(guiStore, serverId)) {
            flowClient.requestGuiList();
        }
        if (!hasResourceData(scoreboardStore, serverId)) {
            flowClient.requestScoreboardList();
        }
        if (!hasResourceData(tabStore, serverId)) {
            flowClient.requestTabList();
        }
        if (!hasResourceData(customContentStore, serverId)) {
            flowClient.requestCustomContentList();
        }
        if (!hasProjectMetadataData(serverId)) {
            flowClient.requestProjectMetadataList();
        }
        requestMissingCustomizationResources(flowClient, serverId);
        if (worldService.getWorldSnapshot(serverId) == null) {
            flowClient.requestWorldSnapshot();
            flowClient.requestPlayerTrackingSnapshot();
        }
    }

    private <T> boolean hasResourceData(SyncedResourceCache<T> store, String serverId) {
        return store.hasLoadedServerList(serverId) || !store.getResourceIds(serverId).isEmpty() || !store.getForServer(serverId).isEmpty();
    }

    private boolean hasLoadedProjectMetadataFromServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        return loadedProjectMetadataLists.contains(serverId);
    }

    private boolean hasCachedProjectMetadata(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        return projectMetadataStore.containsServerId(serverId, serverId);
    }

    private boolean shouldHydrateProjectMetadata(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        if (hasCachedProjectMetadata(serverId)) {
            return true;
        }
        return loadedProjectMetadataLists.contains(serverId) && !pendingProjectMetadataDocuments.contains(serverId);
    }

    private boolean canPersistProjectMetadata(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        if (hasCachedProjectMetadata(serverId)) {
            return true;
        }
        return loadedProjectMetadataLists.contains(serverId) && !pendingProjectMetadataDocuments.contains(serverId);
    }

    private boolean hasProjectMetadataData(String serverId) {
        return hasLoadedProjectMetadataFromServer(serverId);
    }

    private void requestMissingCustomizationResources(ReSyncFlowClient flowClient, String serverId) {
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
            if (store != null && !hasResourceData(store, serverId)) {
                flowClient.requestResourceList(type);
            }
        }
    }

    public void openFlowEditor(String serverId, ClientServerView server) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        marketplaceImportServerId = actualServerId;
        String flowId = getOrCreateDefaultFlowId(actualServerId);
        openFlowEditor(actualServerId, server, flowId);
    }

    public void openFlowEditor(String serverId, ClientServerView server, String flowId) {
        openFlowEditor(serverId, server, flowId, null);
    }

    public void openFlowEditor(String serverId, ClientServerView server, String flowId, String branchPin) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(actualServerId);
        FlowGraph graph = flowStore.getFromDraft(actualServerId, flowId);
        if (graph != null) {
            if (openExistingStudioScreen(actualServerId, screen -> screen.openWorkspaceFlowEditor(flowId, branchPin))) {
                return;
            }
            FlowEditorScreen screen = new FlowEditorScreen(graph, actualServerId, ScreenManager.getInstance().getCurrentScreen());
            if (branchPin != null) {
                screen.focusContentBranch(branchPin);
            }
            client.getHost().setScreen(screen);
            return;
        }
        if (flowStore.containsServerId(actualServerId, flowId)) {
            flowClient.requestFlow(flowId, true);
            return;
        }
        FlowGraph newGraph = createDefaultFlow();
        if (flowId != null) {
            newGraph.setId(flowId);
        }
        String actualFlowId = newGraph.getId();
        flowStore.putInDraft(actualServerId, newGraph);
        flowStore.putNameIfAbsent(actualServerId, actualFlowId, actualFlowId);
        if (openExistingStudioScreen(actualServerId, screen -> screen.openWorkspaceFlowEditor(actualFlowId, branchPin))) {
            return;
        }
        FlowEditorScreen screen = new FlowEditorScreen(newGraph, actualServerId, ScreenManager.getInstance().getCurrentScreen());
        if (branchPin != null) {
            screen.focusContentBranch(branchPin);
        }
        client.getHost().setScreen(screen);
    }

    public void openGuiDesigner(String serverId, ClientServerView server) {
        openGuiDesigner(serverId, server, "main");
    }

    public void openGuiDesigner(String serverId, ClientServerView server, String guiId) {
        openGuiDesigner(serverId, server, guiId, null);
    }

    public void openGuiDesigner(String serverId, ClientServerView server, String guiId, Object parentOverride) {
        openGuiDesigner(serverId, server, guiId, parentOverride, false);
    }

    public void openGuiDesigner(String serverId, ClientServerView server, String guiId, Object parentOverride, boolean fullEditor) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        requestDesignerCatalogs(actualServerId);
        GuiDefinition gui = guiStore.get(actualServerId, guiId);
        Object parent = resolveDesignerParent(parentOverride);
        if (gui == null) {
            guiStore.setPendingParent(actualServerId, guiId, designerOpenContext(parent, fullEditor));
            connectionManager.ensureFlowClient(actualServerId).requestGui(guiId, false);
            return;
        }
        if (openExistingStudioDesigner(actualServerId, ReSyncResourceDragPayload.GUI, guiId, fullEditor)) {
            return;
        }
        client.getHost().setScreen(new GuiDesignerScreen(detachedGui(gui), actualServerId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
    }

    public void openScoreboardDesigner(String serverId, ClientServerView server) {
        openScoreboardDesigner(serverId, server, "main");
    }

    public void openScoreboardDesigner(String serverId, ClientServerView server, String scoreboardId) {
        openScoreboardDesigner(serverId, server, scoreboardId, null);
    }

    public void openScoreboardDesigner(String serverId, ClientServerView server, String scoreboardId, Object parentOverride) {
        openScoreboardDesigner(serverId, server, scoreboardId, parentOverride, false);
    }

    public void openScoreboardDesigner(String serverId, ClientServerView server, String scoreboardId, Object parentOverride, boolean fullEditor) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        ScoreboardDefinition scoreboard = scoreboardStore.get(actualServerId, scoreboardId);
        Object parent = resolveDesignerParent(parentOverride);
        if (scoreboard == null) {
            scoreboardStore.setPendingParent(actualServerId, scoreboardId, designerOpenContext(parent, fullEditor));
            connectionManager.ensureFlowClient(actualServerId).requestScoreboard(scoreboardId, false);
            return;
        }
        if (openExistingStudioDesigner(actualServerId, ReSyncResourceDragPayload.SCOREBOARD, scoreboardId, fullEditor)) {
            return;
        }
        client.getHost().setScreen(new ScoreboardDesignerScreen(detachedScoreboard(scoreboard), actualServerId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
    }

    public void openTabDesigner(String serverId, ClientServerView server) {
        openTabDesigner(serverId, server, "main");
    }

    public void openTabDesigner(String serverId, ClientServerView server, String tabId) {
        openTabDesigner(serverId, server, tabId, null);
    }

    public void openTabDesigner(String serverId, ClientServerView server, String tabId, Object parentOverride) {
        openTabDesigner(serverId, server, tabId, parentOverride, false);
    }

    public void openTabDesigner(String serverId, ClientServerView server, String tabId, Object parentOverride, boolean fullEditor) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        TabDefinition tab = tabStore.get(actualServerId, tabId);
        Object parent = resolveDesignerParent(parentOverride);
        if (tab == null) {
            tabStore.setPendingParent(actualServerId, tabId, designerOpenContext(parent, fullEditor));
            connectionManager.ensureFlowClient(actualServerId).requestTab(tabId, false);
            return;
        }
        if (openExistingStudioDesigner(actualServerId, ReSyncResourceDragPayload.TAB, tabId, fullEditor)) {
            return;
        }
        client.getHost().setScreen(new TabDesignerScreen(detachedTab(tab), actualServerId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
    }

    public void openAdvancementDesigner(String serverId, String treeId, Object parentOverride) {
        openAdvancementDesigner(serverId, treeId, parentOverride, false);
    }

    public void openAdvancementDesigner(String serverId, String treeId, Object parentOverride, boolean fullEditor) {
        if (serverId == null || serverId.isBlank() || treeId == null || treeId.isBlank()) {
            return;
        }
        requestDesignerCatalogs(serverId);
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(ReSyncResourceType.ADVANCEMENT_TREE);
        if (store == null) {
            return;
        }
        Object parent = resolveDesignerParent(parentOverride);
        JsonObject tree = store.get(serverId, treeId);
        if (tree == null) {
            store.setPendingParent(serverId, treeId, designerOpenContext(parent, fullEditor));
            connectionManager.ensureFlowClient(serverId).requestResource(ReSyncResourceType.ADVANCEMENT_TREE, treeId, false);
            return;
        }
        if (fullEditor && openExistingStudioDesigner(serverId, ReSyncResourceDragPayload.ADVANCEMENT_TREE, treeId, true)) {
            return;
        }
        client.getHost().setScreen(new AdvancementDesignerScreen(detachedJson(tree), serverId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
    }

    public void openDialogDesigner(String serverId, String dialogId, Object parentOverride) {
        openDialogDesigner(serverId, dialogId, parentOverride, false);
    }

    public void openDialogDesigner(String serverId, String dialogId, Object parentOverride, boolean fullEditor) {
        if (serverId == null || serverId.isBlank() || dialogId == null || dialogId.isBlank()) {
            return;
        }
        requestDesignerCatalogs(serverId);
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(ReSyncResourceType.DIALOG);
        if (store == null) {
            return;
        }
        Object parent = resolveDesignerParent(parentOverride);
        JsonObject dialog = store.get(serverId, dialogId);
        if (dialog == null) {
            store.setPendingParent(serverId, dialogId, designerOpenContext(parent, fullEditor));
            connectionManager.ensureFlowClient(serverId).requestResource(ReSyncResourceType.DIALOG, dialogId, false);
            return;
        }
        if (openExistingStudioDesigner(serverId, ReSyncResourceDragPayload.DIALOG, dialogId, fullEditor)) {
            return;
        }
        client.getHost().setScreen(new DialogDesignerScreen(detachedJson(dialog), serverId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
    }

    private boolean openExistingStudioScreen(String serverId, Consumer<FlowEditorScreen> opener) {
        return openExistingStudioScreen(serverId, opener, false);
    }

    private boolean openExistingStudioScreen(String serverId, Consumer<FlowEditorScreen> opener, boolean fullEditor) {
        FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
        if (studioScreen == null) {
            return false;
        }
        opener.accept(studioScreen);
        activateStudioScreen(studioScreen, fullEditor);
        return true;
    }

    private boolean openExistingStudioDesigner(String serverId, String type, String id, boolean fullEditor) {
        return openExistingStudioScreen(serverId, screen -> screen.openWorkspaceDesigner(type, id, fullEditor), fullEditor);
    }

    private void requestDesignerCatalogs(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId, true);
        if (flowClient == null) {
            return;
        }
        if (!hasResourceData(flowStore, serverId)) {
            flowClient.requestFlowList();
        }
        if (!hasResourceData(guiStore, serverId)) {
            flowClient.requestGuiList();
        }
        if (!hasProjectMetadataData(serverId)) {
            flowClient.requestProjectMetadataList();
        }
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
            if (store != null && !hasResourceData(store, serverId)) {
                flowClient.requestResourceList(type);
            }
        }
    }

    private void requestDesignerCatalogs(String serverId, StudioEditTarget target) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId, true);
        if (flowClient == null) {
            return;
        }
        if (!hasResourceData(flowStore, serverId)) {
            flowClient.requestFlowList();
        }
        if (!hasProjectMetadataData(serverId)) {
            flowClient.requestProjectMetadataList();
        }
        if (target != null && ReSyncResourceDragPayload.GUI.equals(target.type()) && !hasResourceData(guiStore, serverId)) {
            flowClient.requestGuiList();
        }
    }

    private void activateStudioScreen(FlowEditorScreen studioScreen, boolean fullEditor) {
        studioFullEditorSession.activate(studioScreen, fullEditor);
    }

    public void onStudioReady(String serverId) {
        studioFullEditorSession.onReady(serverId);
    }

    public void requestCloseLiveStudioSuperScreen(String serverId) {
        studioFullEditorSession.requestClose(serverId);
    }

    private boolean flushPendingStudioEditTarget(String serverId) {
        return studioFullEditorSession.flush(serverId);
    }

    private boolean flushPendingStudioEditTarget(String serverId, String type, String id) {
        return studioFullEditorSession.flush(serverId, type, id);
    }

    private void requestStudioEditTargetData(String serverId, StudioEditTarget target) {
        if (serverId == null || serverId.isBlank() || target == null) {
            return;
        }
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (ReSyncResourceDragPayload.GUI.equals(target.type())) {
            if (guiStore.get(serverId, target.id()) == null) {
                flowClient.requestGui(target.id(), false);
            }
            return;
        }
        if (ReSyncResourceDragPayload.SCOREBOARD.equals(target.type())) {
            if (scoreboardStore.get(serverId, target.id()) == null) {
                flowClient.requestScoreboard(target.id(), false);
            }
            return;
        }
        if (ReSyncResourceDragPayload.TAB.equals(target.type())) {
            if (tabStore.get(serverId, target.id()) == null) {
                flowClient.requestTab(target.id(), false);
            }
            return;
        }
        ReSyncResourceType jsonType = ReSyncResourceType.byTypeId(target.type());
        SyncedResourceCache<JsonObject> store = jsonType != null ? jsonResourceStores.get(jsonType) : null;
        if (jsonType != null && store != null && store.get(serverId, target.id()) == null) {
            flowClient.requestResource(jsonType, target.id(), false);
        }
    }

    private boolean studioEditTargetAvailable(String serverId, StudioEditTarget target) {
        if (ReSyncResourceDragPayload.GUI.equals(target.type())) {
            return guiStore.get(serverId, target.id()) != null;
        }
        if (ReSyncResourceDragPayload.SCOREBOARD.equals(target.type())) {
            return scoreboardStore.get(serverId, target.id()) != null;
        }
        if (ReSyncResourceDragPayload.TAB.equals(target.type())) {
            return tabStore.get(serverId, target.id()) != null;
        }
        ReSyncResourceType jsonType = ReSyncResourceType.byTypeId(target.type());
        SyncedResourceCache<JsonObject> store = jsonType != null ? jsonResourceStores.get(jsonType) : null;
        return store != null && store.get(serverId, target.id()) != null;
    }

    public void createAdvancementTreeFromVanillaScreen(String serverId, Object parentOverride) {
        createAdvancementTreeFromVanillaScreen(serverId, parentOverride, false);
    }

    public void createAdvancementTreeFromVanillaScreen(String serverId, Object parentOverride, boolean fullEditor) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        String id = nextAdvancementTreeId(serverId);
        JsonObject tree = createJsonResource(serverId, ReSyncResourceType.ADVANCEMENT_TREE, id, ReSyncResourceType.ADVANCEMENT_TREE.defaultFolder());
        if (tree == null) {
            return;
        }
        saveJsonResource(serverId, ReSyncResourceType.ADVANCEMENT_TREE, tree);
        ReSyncProjectMetadata metadata = getProjectMetadata(serverId);
        ReSyncProjectMetadata.ResourceEntry entry = metadata.ensureResource(ReSyncResourceDragPayload.ADVANCEMENT_TREE, id, id, ReSyncResourceType.ADVANCEMENT_TREE.defaultFolder());
        entry.setPath(ReSyncResourceType.ADVANCEMENT_TREE.defaultFolder());
        saveProjectMetadata(serverId, metadata);
        openAdvancementDesigner(serverId, id, parentOverride, fullEditor);
    }

    private String nextAdvancementTreeId(String serverId) {
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(ReSyncResourceType.ADVANCEMENT_TREE);
        String base = "advancement";
        if (store == null || !store.containsKey(serverId, base)) {
            return base;
        }
        int index = 2;
        while (store.containsKey(serverId, base + "_" + index)) {
            index++;
        }
        return base + "_" + index;
    }

    private Object resolveDesignerParent(Object parentOverride) {
        if (parentOverride != null) {
            return parentOverride;
        }
        Screen current = ScreenManager.getInstance().getCurrentScreen();
        if (!Config.desktopMode) {
            return current;
        }
        Screen desktopSuperScreen = ScreenManager.getInstance().getDesktopSuperScreen();
        if (current != null && current.isDesktopWindow() && desktopSuperScreen != null) {
            return desktopSuperScreen;
        }
        return current;
    }

    private Object designerOpenContext(Object parent, boolean fullEditor) {
        return fullEditor ? new DesignerOpenContext(parent, true) : parent;
    }

    private Object designerParent(Object value) {
        return value instanceof DesignerOpenContext context ? context.parent() : value;
    }

    private boolean designerFullEditor(Object value) {
        return value instanceof DesignerOpenContext context && context.fullEditor();
    }

    private class StudioFullEditorSession {
        private final Map<String, StudioEditTarget> pendingTargets = new ConcurrentHashMap<>();
        private final Map<String, ReSyncLiveServerSession> pendingSessions = new ConcurrentHashMap<>();
        private final Map<String, Object> returnParents = new ConcurrentHashMap<>();
        private final Map<String, StudioEditTarget> activeTargets = new ConcurrentHashMap<>();
        private final Set<String> savedTargets = ConcurrentHashMap.newKeySet();
        private final Map<String, UUID> closeRequests = new ConcurrentHashMap<>();

        boolean hasPendingTarget(String serverId) {
            return serverId != null && pendingTargets.containsKey(serverId);
        }

        void open(ReSyncLiveServerSession session, StudioEditTarget target, Object parent) {
            if (session == null || session.serverId() == null || session.serverId().isBlank() || target == null || target.type() == null || target.type().isBlank() || target.id() == null || target.id().isBlank()) {
                new Notification("ReSync", "ReSync Unavailable", Notification.Type.WARN);
                return;
            }
            String serverId = session.serverId();
            marketplaceImportServerId = serverId;
            activateLiveReSyncSession(session);
            closeRequests.remove(serverId);
            pendingTargets.put(serverId, target);
            pendingSessions.put(serverId, session);
            rememberReturnParent(serverId, parent);
            requestDesignerCatalogs(serverId, target);
            requestStudioEditTargetData(serverId, target);
            openPending(serverId, true);
        }

        void rememberReturnParent(String serverId, Object parent) {
            Object actualParent = parent != null ? parent : captureCurrentParent();
            if (actualParent != null) {
                returnParents.put(serverId, actualParent);
            }
        }

        Object captureCurrentParent() {
            ScreenManager screenManager = ScreenManager.getInstance();
            Screen currentScreen = screenManager.getCurrentScreen();
            Screen desktopSuperScreen = screenManager.getDesktopSuperScreen();
            if (currentScreen != null && currentScreen.isDesktopWindow() && desktopSuperScreen != null) {
                return desktopSuperScreen;
            }
            if (currentScreen != null) {
                return currentScreen;
            }
            Screen hostScreen = client.getHost().getCurrentScreen();
            return hostScreen != null ? hostScreen : desktopSuperScreen;
        }

        void activate(FlowEditorScreen studioScreen, boolean fullEditor) {
            if (studioScreen == null) {
                return;
            }
            studioScreen.setLiveStudioFullEditorMode(fullEditor);
            ScreenManager screenManager = ScreenManager.getInstance();
            if (Config.desktopMode && fullEditor) {
                DesktopWindowsOverlay overlay = screenManager.getDesktopWindowsOverlay();
                ScreenWindowWidget window = overlay != null ? overlay.getWindowForScreen(studioScreen) : null;
                if (window != null) {
                    overlay.closeWindow(window);
                }
                client.getHost().setScreen(studioScreen);
                return;
            }
            if (Config.desktopMode) {
                DesktopWindowsOverlay overlay = screenManager.getDesktopWindowsOverlay();
                ScreenWindowWidget window = overlay != null ? overlay.getWindowForScreen(studioScreen) : null;
                if (window != null) {
                    overlay.restoreWindow(window);
                    return;
                }
            }
            if (screenManager.getCurrentScreen() != studioScreen && screenManager.getDesktopSuperScreen() != studioScreen) {
                client.getHost().setScreen(studioScreen);
            }
        }

        void onReady(String serverId) {
            if (serverId == null || serverId.isBlank()) {
                return;
            }
            requestStudioEditTargetData(serverId, pendingTargets.get(serverId));
            openPending(serverId, false);
        }

        boolean flush(String serverId) {
            return openPending(serverId, true);
        }

        boolean flush(String serverId, String type, String id) {
            StudioEditTarget target = pendingTargets.get(serverId);
            if (target == null || !target.type().equals(type) || !target.id().equals(id)) {
                return false;
            }
            return flush(serverId);
        }

        boolean openPending(String serverId, boolean activate) {
            StudioEditTarget target = pendingTargets.get(serverId);
            if (target == null) {
                return false;
            }
            if (!studioEditTargetAvailable(serverId, target)) {
                requestStudioEditTargetData(serverId, target);
                return false;
            }
            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
            if (studioScreen == null) {
                if (!activate) {
                    return false;
                }
                ReSyncLiveServerSession session = pendingSessions.get(serverId);
                if (session == null) {
                    return false;
                }
                studioScreen = new FlowEditorScreen(new FlowGraph(), serverId, ScreenManager.getInstance().getCurrentScreen(), null, "", session.displayName()).enableStudioMode();
            }
            studioScreen.setLiveStudioFullEditorMode(target.fullEditor());
            if (!studioScreen.isStudioWorkspaceReady()) {
                if (!activate) {
                    return false;
                }
                studioScreen.prepareLiveStudioWorkspace();
                activate(studioScreen, target.fullEditor());
                target = pendingTargets.get(serverId);
                if (target == null) {
                    pendingSessions.remove(serverId);
                    return true;
                }
                studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                if (studioScreen == null || !studioScreen.isStudioWorkspaceReady()) {
                    return false;
                }
                studioScreen.setLiveStudioFullEditorMode(target.fullEditor());
                if (!studioEditTargetAvailable(serverId, target)) {
                    requestStudioEditTargetData(serverId, target);
                    return false;
                }
            }
            pendingTargets.remove(serverId, target);
            pendingSessions.remove(serverId);
            activeTargets.put(serverId, target);
            savedTargets.remove(targetKey(serverId, target));
            studioScreen.openWorkspaceDesigner(target.type(), target.id(), target.fullEditor());
            if (activate) {
                activate(studioScreen, target.fullEditor());
            }
            return true;
        }

        void markSaved(String serverId, String type, String id) {
            StudioEditTarget target = activeTargets.get(serverId);
            if (target != null && target.fullEditor() && target.type().equals(type) && target.id().equals(id)) {
                savedTargets.add(targetKey(serverId, target));
            }
        }

        boolean savedActiveTarget(String serverId) {
            StudioEditTarget target = activeTargets.get(serverId);
            return target != null && savedTargets.contains(targetKey(serverId, target));
        }

        String targetKey(String serverId, StudioEditTarget target) {
            return serverId + ":" + target.type() + ":" + target.id();
        }

        void requestClose(String serverId) {
            if (serverId == null || serverId.isBlank()) {
                return;
            }
            UUID closeRequest = UUID.randomUUID();
            if (closeRequests.putIfAbsent(serverId, closeRequest) != null) {
                return;
            }
            ScreenManager.getInstance().execute(() -> closeNow(serverId, closeRequest));
        }

        void closeNow(String serverId, UUID closeRequest) {
            if (serverId == null || serverId.isBlank()) {
                return;
            }
            if (!closeRequest.equals(closeRequests.remove(serverId))) {
                return;
            }
            pendingTargets.remove(serverId);
            pendingSessions.remove(serverId);
            boolean savedActiveTarget = savedActiveTarget(serverId);
            activeTargets.remove(serverId);
            Object returnParent = returnParents.remove(serverId);
            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
            ScreenManager screenManager = ScreenManager.getInstance();
            Screen currentScreen = screenManager.getCurrentScreen();
            Screen desktopSuperScreen = screenManager.getDesktopSuperScreen();
            Screen hostScreen = client.getHost().getCurrentScreen();
            if (studioScreen == null || (currentScreen != studioScreen && desktopSuperScreen != studioScreen && hostScreen != studioScreen)) {
                return;
            }
            if (studioScreen != null) {
                studioScreen.setLiveStudioFullEditorMode(false);
                studioScreen.dismissStudioWorkspace();
                if (desktopSuperScreen == studioScreen) {
                    screenManager.setDesktopSuperScreen(null);
                }
            }
            if (!savedActiveTarget && returnParent != null && returnParent != studioScreen) {
                client.getHost().openParentScreen(studioScreen, returnParent);
            } else {
                client.getHost().openParentScreen(studioScreen, null);
            }
        }

        void clear(String serverId) {
            if (serverId == null || serverId.isBlank()) {
                return;
            }
            closeRequests.remove(serverId);
            pendingTargets.remove(serverId);
            pendingSessions.remove(serverId);
            returnParents.remove(serverId);
            activeTargets.remove(serverId);
            savedTargets.removeIf(key -> key.startsWith(serverId + ":"));
        }
    }

    private record DesignerOpenContext(Object parent, boolean fullEditor) {}

    private record StudioEditTarget(String type, String id, boolean fullEditor) {}

    private record FlowWorkspaceRefreshSnapshot(boolean rebuildContentBrowser, boolean refreshAllFlowBindings, Set<String> flowIds) {}

    private static final class PendingFlowWorkspaceRefresh {
        private final Set<String> flowIds = new HashSet<>();
        private boolean scheduled;
        private boolean rebuildContentBrowser;
        private boolean refreshAllFlowBindings;

        private void add(String changedFlowId, boolean rebuildContentBrowser) {
            this.rebuildContentBrowser = this.rebuildContentBrowser || rebuildContentBrowser;
            if (changedFlowId == null || changedFlowId.isBlank()) {
                refreshAllFlowBindings = true;
                flowIds.clear();
                return;
            }
            if (refreshAllFlowBindings) {
                return;
            }
            flowIds.add(changedFlowId);
            if (flowIds.size() > MAX_TARGETED_FLOW_REFRESH_IDS) {
                refreshAllFlowBindings = true;
                flowIds.clear();
            }
        }

        private FlowWorkspaceRefreshSnapshot snapshot() {
            return new FlowWorkspaceRefreshSnapshot(rebuildContentBrowser, refreshAllFlowBindings, Set.copyOf(flowIds));
        }
    }

    public void saveFlow(String serverId, FlowGraph graph) {
        flowStore.putInDraft(serverId, graph);
        CustomContentDefinition derivedContent = CustomContentGraphAdapter.toDefinition(graph);
        if (derivedContent != null) {
            customContentStore.putInDraft(serverId, derivedContent);
            customContentStore.putNameIfAbsent(serverId, derivedContent.getId(), derivedContent.getDisplayName());
        }
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            if (derivedContent != null) {
                customContentStore.markSaving(serverId, derivedContent.getId());
                flowClient.sendCustomContentSave(derivedContent);
            } else {
                flowStore.markSaving(serverId, graph.getId());
                flowClient.sendFlowSave(graph);
            }
        }
    }

    public void cacheFlow(String serverId, FlowGraph graph) {
        FlowGraph loadedGraph = graph != null && graph.getId() != null ? flowStore.get(serverId, graph.getId()) : null;
        boolean loadedFlow = loadedGraph != null;
        boolean flowTypeChanged = loadedGraph != null && graph != null && loadedGraph.isFunction() != graph.isFunction();
        flowStore.cache(serverId, graph);
        if (graph != null && graph.getId() != null) {
            CustomContentDefinition derivedContent = CustomContentGraphAdapter.toDefinition(graph);
            if (derivedContent != null) {
                boolean loadedContent = customContentStore.get(serverId, derivedContent.getId()) != null;
                customContentStore.cache(serverId, derivedContent);
                customContentStore.putNameIfAbsent(serverId, derivedContent.getId(), derivedContent.getDisplayName());
                if (!loadedFlow || !loadedContent || flowTypeChanged) {
                    invalidateProjectCatalog(serverId);
                }
                refreshFlowWorkspace(serverId, graph.getId(), !loadedFlow || !loadedContent || flowTypeChanged);
                return;
            }
            if (!loadedFlow || flowTypeChanged) {
                invalidateProjectCatalog(serverId);
            }
            refreshFlowWorkspace(serverId, graph.getId(), !loadedFlow || flowTypeChanged);
        }
    }

    public void saveGui(String serverId, GuiDefinition gui) {
        if (serverId == null || gui == null || gui.getId() == null) {
            return;
        }
        guiStore.putInDraft(serverId, gui);
        guiStore.putNameIfAbsent(serverId, gui.getId(), gui.getTitle() != null ? gui.getTitle() : gui.getId());
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            guiStore.markSaving(serverId, gui.getId());
            studioFullEditorSession.markSaved(serverId, ReSyncResourceDragPayload.GUI, gui.getId());
            flowClient.sendGuiSave(gui);
        }
    }

    public void cacheGui(String serverId, GuiDefinition gui) {
        guiStore.cache(serverId, gui);
        if (gui != null && gui.getId() != null) {
            refreshStudioWorkspace(serverId);
        }
    }

    public void markFlowSaved(String serverId, String flowId) {
        flowStore.markSaved(serverId, flowId);
        refreshFlowWorkspace(serverId, flowId, false);
    }

    public void markGuiSaved(String serverId, String guiId) {
        guiStore.markSaved(serverId, guiId);
        refreshStudioWorkspace(serverId);
    }

    public void saveScoreboard(String serverId, ScoreboardDefinition scoreboard) {
        if (serverId == null || scoreboard == null || scoreboard.getId() == null) {
            return;
        }
        scoreboardStore.putInDraft(serverId, scoreboard);
        scoreboardStore.putNameIfAbsent(serverId, scoreboard.getId(), scoreboard.getTitle() != null ? scoreboard.getTitle() : scoreboard.getId());
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            scoreboardStore.markSaving(serverId, scoreboard.getId());
            flowClient.sendScoreboardSave(scoreboard);
        }
    }

    public void cacheScoreboard(String serverId, ScoreboardDefinition scoreboard) {
        scoreboardStore.cache(serverId, scoreboard);
        if (scoreboard != null && scoreboard.getId() != null) {
            refreshStudioWorkspace(serverId);
        }
    }

    public void markScoreboardSaved(String serverId, String scoreboardId) {
        scoreboardStore.markSaved(serverId, scoreboardId);
        refreshStudioWorkspace(serverId);
    }

    public void saveTab(String serverId, TabDefinition tab) {
        if (serverId == null || tab == null || tab.getId() == null) {
            return;
        }
        tabStore.putInDraft(serverId, tab);
        tabStore.putNameIfAbsent(serverId, tab.getId(), tab.getId());
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            tabStore.markSaving(serverId, tab.getId());
            flowClient.sendTabSave(tab);
        }
    }

    public void cacheTab(String serverId, TabDefinition tab) {
        tabStore.cache(serverId, tab);
        if (tab != null && tab.getId() != null) {
            refreshStudioWorkspace(serverId);
        }
    }

    public void markTabSaved(String serverId, String tabId) {
        tabStore.markSaved(serverId, tabId);
        refreshStudioWorkspace(serverId);
    }

    public void saveCustomContent(String serverId, CustomContentDefinition content) {
        if (serverId == null || content == null || content.getId() == null) {
            return;
        }
        customContentStore.putInDraft(serverId, content);
        customContentStore.putNameIfAbsent(serverId, content.getId(), content.getDisplayName() != null ? content.getDisplayName() : content.getId());
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            customContentStore.markSaving(serverId, content.getId());
            flowClient.sendCustomContentSave(content);
        }
    }

    public void cacheCustomContent(String serverId, CustomContentDefinition content) {
        boolean loadedContent = content != null && content.getId() != null && customContentStore.get(serverId, content.getId()) != null;
        FlowGraph graph = content != null ? content.getGraph() : null;
        FlowGraph loadedGraph = graph != null && graph.getId() != null ? flowStore.get(serverId, graph.getId()) : null;
        boolean loadedFlow = loadedGraph != null;
        boolean flowTypeChanged = loadedGraph != null && loadedGraph.isFunction() != graph.isFunction();
        customContentStore.cache(serverId, content);
        if (content != null && content.getId() != null) {
            if (graph != null && graph.getId() != null) {
                flowStore.cache(serverId, graph);
                flowStore.putNameIfAbsent(serverId, graph.getId(), content.getDisplayName() != null ? content.getDisplayName() : content.getId());
            }
        }
        boolean catalogChanged = !loadedContent || (graph != null && (!loadedFlow || flowTypeChanged));
        if (catalogChanged) {
            invalidateProjectCatalog(serverId);
        }
        refreshFlowWorkspace(serverId, graph != null ? graph.getId() : null, catalogChanged);
    }

    private CompletableFuture<Boolean> importMarketplaceContent(MarketplaceModels.Listing listing, MarketplaceModels.Version version, String payloadJson) {
        return CompletableFuture.supplyAsync(() -> {
            String serverId = marketplaceImportServerId;
            if (serverId == null || serverId.isBlank() || listing == null || payloadJson == null || payloadJson.isBlank()) {
                return false;
            }
            try {
                if (installMarketplaceBundle(serverId, listing, version, payloadJson)) {
                    return true;
                }
                switch (listing.type) {
                    case "FLOW" -> saveFlow(serverId, gson.fromJson(payloadJson, FlowGraph.class));
                    case "UI" -> saveGui(serverId, gson.fromJson(payloadJson, GuiDefinition.class));
                    case "TAB_LIST" -> saveTab(serverId, gson.fromJson(payloadJson, TabDefinition.class));
                    case "SCOREBOARD" -> saveScoreboard(serverId, gson.fromJson(payloadJson, ScoreboardDefinition.class));
                    case "CUSTOM_CONTENT", "RESYNC_CONTENT" -> saveFlow(serverId, gson.fromJson(payloadJson, FlowGraph.class));
                    default -> {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public boolean installMarketplaceBundle(String serverId, MarketplaceModels.Listing listing, MarketplaceModels.Version version, String payloadJson) {
        JsonElement parsed = JsonParser.parseString(payloadJson);
        if (!parsed.isJsonObject()) {
            return false;
        }
        JsonObject bundle = parsed.getAsJsonObject();
        if (!bundle.has("assets") || !bundle.get("assets").isJsonArray()) {
            return false;
        }
        String folderName = text(bundle, "folderName");
        if (folderName.isBlank()) {
            folderName = listing != null && listing.title != null && !listing.title.isBlank() ? listing.title : "Marketplace Bundle";
        }
        String rootFolder = "Marketplace/" + safeFolderName(folderName);
        ReSyncProjectMetadata metadata = getProjectMetadata(serverId);
        ensureMarketplaceFolder(metadata, "Marketplace");
        ensureMarketplaceFolder(metadata, rootFolder);
        ReSyncProjectMetadata.InstalledBundleEntry installed = metadata.findInstalledBundle(listing.marketplaceSlug, listing.slug);
        Set<String> previousKeys = installed == null ? new HashSet<>() : new HashSet<>(installed.getResourceKeys());
        JsonArray assets = bundle.getAsJsonArray("assets");
        List<String> resourceKeys = new ArrayList<>();
        for (JsonElement element : assets) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            String type = text(asset, "type");
            String id = text(asset, "id");
            String displayName = text(asset, "displayName");
            if (type.isBlank() || id.isBlank() || !asset.has("payload")) {
                continue;
            }
            importMarketplaceBundleAsset(serverId, type, id, displayName, asset.get("payload"));
            String folder = rootFolder + "/" + marketplaceBundleFolder(type);
            ensureMarketplaceFolder(metadata, folder);
            ReSyncProjectMetadata.ResourceEntry resource = metadata.ensureResource(type, id, displayName.isBlank() ? id : displayName, folder);
            resource.setPath(folder);
            resourceKeys.add(resource.key());
        }
        previousKeys.removeAll(resourceKeys);
        for (String key : previousKeys) {
            ReSyncProjectMetadata.ResourceEntry resource = metadata.getResources().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
            if (resource != null) {
                deleteMarketplaceBundleResource(serverId, resource);
            }
        }
        metadata.getResources().removeIf(resource -> previousKeys.contains(resource.key()));
        if (installed == null) {
            installed = new ReSyncProjectMetadata.InstalledBundleEntry();
            installed.setMarketplaceSlug(listing.marketplaceSlug);
            installed.setListingSlug(listing.slug);
            metadata.getInstalledBundles().add(installed);
        }
        installed.setTitle(listing.title != null && !listing.title.isBlank() ? listing.title : folderName);
        installed.setVersionId(version != null ? version.id : "");
        installed.setVersion(version != null ? version.version : "");
        installed.setRootPath(rootFolder);
        installed.setIconMediaId(listing.iconMediaId);
        installed.setEnabled(true);
        installed.setResourceKeys(resourceKeys);
        saveProjectMetadata(serverId, metadata);
        return true;
    }

    public void deleteMarketplaceBundle(String serverId, ReSyncProjectMetadata.InstalledBundleEntry bundle) {
        if (bundle == null) {
            return;
        }
        ReSyncProjectMetadata metadata = getProjectMetadata(serverId);
        Set<String> ownedKeys = new HashSet<>(bundle.getResourceKeys());
        if (ownedKeys.isEmpty() && !bundle.getRootPath().isBlank()) {
            for (ReSyncProjectMetadata.ResourceEntry resource : metadata.getResources()) {
                if (resource.getPath().equals(bundle.getRootPath()) || resource.getPath().startsWith(bundle.getRootPath() + "/")) {
                    ownedKeys.add(resource.key());
                }
            }
        }
        for (String key : ownedKeys) {
            ReSyncProjectMetadata.ResourceEntry resource = metadata.getResources().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
            if (resource != null) {
                deleteMarketplaceBundleResource(serverId, resource);
            }
        }
        metadata.getResources().removeIf(resource -> ownedKeys.contains(resource.key()));
        String rootPath = bundle.getRootPath();
        if (!rootPath.isBlank()) {
            metadata.getFolders().removeIf(folder -> folder.getPath().equals(rootPath) || folder.getPath().startsWith(rootPath + "/"));
        }
        metadata.getInstalledBundles().removeIf(entry -> entry.key().equals(bundle.key()));
        saveProjectMetadata(serverId, metadata);
        refreshStudioWorkspace(serverId);
    }

    public void setMarketplaceBundleEnabled(String serverId, ReSyncProjectMetadata.InstalledBundleEntry bundle, boolean enabled) {
        if (bundle == null || bundle.isEnabled() == enabled) {
            return;
        }
        ReSyncProjectMetadata metadata = getProjectMetadata(serverId);
        ReSyncProjectMetadata.InstalledBundleEntry stored = metadata.findInstalledBundle(bundle.getMarketplaceSlug(), bundle.getListingSlug());
        if (stored == null) {
            return;
        }
        stored.setEnabled(enabled);
        for (String key : stored.getResourceKeys()) {
            ReSyncProjectMetadata.ResourceEntry resource = metadata.getResources().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
            if (resource != null && ReSyncResourceDragPayload.COMMAND.equals(resource.getType())) {
                if (enabled) {
                    setCommandBinding(serverId, resource.getId(), resource.getDisplayName().isBlank() ? resource.getId() : resource.getDisplayName());
                } else {
                    clearCommandBinding(serverId, resource.getId());
                }
            }
        }
        saveProjectMetadata(serverId, metadata);
    }

    private void deleteMarketplaceBundleResource(String serverId, ReSyncProjectMetadata.ResourceEntry resource) {
        switch (resource.getType()) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> deleteFlow(serverId, resource.getId());
            case ReSyncResourceDragPayload.COMMAND -> {
                clearCommandBinding(serverId, resource.getId());
                deleteFlow(serverId, resource.getId());
            }
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> deleteCustomContent(serverId, resource.getId());
            case ReSyncResourceDragPayload.GUI -> deleteGui(serverId, resource.getId());
            case ReSyncResourceDragPayload.SCOREBOARD -> deleteScoreboard(serverId, resource.getId());
            case ReSyncResourceDragPayload.TAB -> deleteTab(serverId, resource.getId());
            case ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.VILLAGE_PROFILE, ReSyncResourceDragPayload.NPC_DEFINITION,
                 ReSyncResourceDragPayload.LOOT_TABLE -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(resource.getType());
                if (resourceType != null) {
                    deleteJsonResource(serverId, resourceType, resource.getId());
                }
            }
            default -> {
            }
        }
    }

    private void importMarketplaceBundleAsset(String serverId, String type, String id, String displayName, JsonElement payload) {
        switch (type) {
            case ReSyncResourceDragPayload.COMMAND -> {
                FlowGraph graph = gson.fromJson(payload, FlowGraph.class);
                if (graph != null) {
                    graph.setId(id);
                    saveFlow(serverId, graph);
                    setCommandBinding(serverId, id, displayName.isBlank() ? id : displayName);
                }
            }
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> {
                FlowGraph graph = gson.fromJson(payload, FlowGraph.class);
                if (graph != null) {
                    graph.setId(id);
                    saveFlow(serverId, graph);
                }
            }
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> {
                CustomContentDefinition content = gson.fromJson(payload, CustomContentDefinition.class);
                if (content != null) {
                    content.setId(id);
                    saveCustomContent(serverId, content);
                }
            }
            case ReSyncResourceDragPayload.GUI -> {
                GuiDefinition gui = gson.fromJson(payload, GuiDefinition.class);
                if (gui != null) {
                    gui.setId(id);
                    saveGui(serverId, gui);
                }
            }
            case ReSyncResourceDragPayload.SCOREBOARD -> {
                ScoreboardDefinition scoreboard = gson.fromJson(payload, ScoreboardDefinition.class);
                if (scoreboard != null) {
                    scoreboard.setId(id);
                    saveScoreboard(serverId, scoreboard);
                }
            }
            case ReSyncResourceDragPayload.TAB -> {
                TabDefinition tab = gson.fromJson(payload, TabDefinition.class);
                if (tab != null) {
                    tab.setId(id);
                    saveTab(serverId, tab);
                }
            }
            case ReSyncResourceDragPayload.CHAT, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG, ReSyncResourceDragPayload.VILLAGE_PROFILE, ReSyncResourceDragPayload.NPC_DEFINITION,
                 ReSyncResourceDragPayload.LOOT_TABLE -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(type);
                JsonObject resource = payload != null && payload.isJsonObject() ? payload.getAsJsonObject() : null;
                if (resourceType != null && resource != null) {
                    resource.addProperty("id", id);
                    saveJsonResource(serverId, resourceType, resource);
                }
            }
            default -> {
            }
        }
    }

    private void ensureMarketplaceFolder(ReSyncProjectMetadata metadata, String path) {
        String normalized = ReSyncProjectMetadata.normalizePath(path);
        int split = normalized.lastIndexOf('/');
        String parent = split > 0 ? normalized.substring(0, split) : "";
        metadata.ensureFolder(normalized, parent, metadata.getFolders().size());
    }

    private String marketplaceBundleFolder(String type) {
        return switch (type) {
            case ReSyncResourceDragPayload.FUNCTION -> "Functions";
            case ReSyncResourceDragPayload.COMMAND -> "Commands";
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "Content";
            case ReSyncResourceDragPayload.GUI -> "GUIs";
            case ReSyncResourceDragPayload.SCOREBOARD -> "Scoreboards";
            case ReSyncResourceDragPayload.TAB -> "Tabs";
            case ReSyncResourceDragPayload.CHAT -> "Chat";
            case ReSyncResourceDragPayload.DIALOG -> "Dialogs";
            case ReSyncResourceDragPayload.VILLAGE_PROFILE -> "Villages";
            case ReSyncResourceDragPayload.NPC_DEFINITION -> "NPCs";
            case ReSyncResourceDragPayload.LOOT_TABLE -> "Loot Tables";
            default -> "Flows";
        };
    }

    private String safeFolderName(String value) {
        String name = value == null ? "" : value.trim().replace('\\', '/').replaceAll("[/:*?\"<>|]+", "-");
        name = name.replaceAll("\\s+", " ").trim();
        return name.isBlank() ? "Bundle" : name;
    }

    private String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean usesJsonResourceStore(ReSyncResourceType type) {
        return type == ReSyncResourceType.CHAT
            || type == ReSyncResourceType.MOTD_PROFILE
            || type == ReSyncResourceType.MESSAGE_RULE
            || type == ReSyncResourceType.RECIPE_DEFINITION
            || type == ReSyncResourceType.TEXT_TEMPLATE
            || type == ReSyncResourceType.ADVANCEMENT_TREE
            || type == ReSyncResourceType.DIALOG
            || type == ReSyncResourceType.VILLAGE_PROFILE
            || type == ReSyncResourceType.NPC_DEFINITION
            || type == ReSyncResourceType.LOOT_TABLE;
    }

    private String jsonResourceId(JsonObject resource) {
        return text(resource, "id");
    }

    private String jsonResourceName(JsonObject resource) {
        String displayName = text(resource, "displayName");
        if (!displayName.isBlank()) {
            return displayName;
        }
        String name = text(resource, "name");
        return name.isBlank() ? text(resource, "id") : name;
    }

    private JsonObject defaultJsonResource(ReSyncResourceType type, String id, String folder) {
        JsonObject resource = new JsonObject();
        resource.addProperty("id", id);
        resource.addProperty("displayName", id);
        resource.addProperty("folder", folder == null || folder.isBlank() ? type.defaultFolder() : folder);
        resource.addProperty("enabled", true);
        switch (type) {
            case CHAT -> {
                JsonObject channel = new JsonObject();
                channel.addProperty("priority", 0);
                channel.addProperty("defaultChannel", true);
                channel.addProperty("autojoin", true);
                channel.addProperty("prefix", "<gray>[Chat]</gray> ");
                channel.addProperty("format", "");
                channel.addProperty("range", -1);
                channel.addProperty("allowMiniMessage", false);
                resource.add("channel", channel);
                JsonObject format = new JsonObject();
                format.addProperty("template", "{prefix}{sender}: {message}");
                resource.add("format", format);
                JsonObject rule = new JsonObject();
                rule.addProperty("contains", "");
                rule.addProperty("action", "replace");
                rule.addProperty("replacement", "{message}");
                resource.add("rule", rule);
                JsonObject privateMessages = new JsonObject();
                privateMessages.addProperty("sender", "<gray>To <white>{receiver}</white>: <message>");
                privateMessages.addProperty("receiver", "<gray>From <white>{sender}</white>: <message>");
                privateMessages.addProperty("spy", "<gray>Spy <white>{sender}</white> -> <white>{receiver}</white>: <message>");
                resource.add("privateMessages", privateMessages);
                JsonObject mention = new JsonObject();
                mention.addProperty("template", "<yellow>@{player}</yellow>");
                resource.add("mention", mention);
                JsonObject ignore = new JsonObject();
                ignore.add("players", new JsonArray());
                resource.add("ignore", ignore);
            }
            case MOTD_PROFILE -> {
                resource.addProperty("priority", 0);
                resource.addProperty("line1", "<green>ReSync Server");
                resource.addProperty("line2", "<gray>Powered By ReStudio");
                resource.addProperty("playerCountMode", "real");
            }
            case MESSAGE_RULE -> {
                resource.addProperty("source", "join");
                resource.addProperty("priority", 0);
                resource.addProperty("action", "replace_section");
                resource.addProperty("contains", "");
                resource.addProperty("replacement", "{message}");
            }
            case RECIPE_DEFINITION -> {
                resource.addProperty("type", "shaped");
                JsonObject output = new JsonObject();
                output.addProperty("material", "STONE");
                output.addProperty("amount", 1);
                resource.add("output", output);
                JsonArray shape = new JsonArray();
                shape.add("A");
                resource.add("shape", shape);
                JsonObject keys = new JsonObject();
                keys.addProperty("A", "STONE");
                resource.add("keys", keys);
                resource.addProperty("experience", 0);
                resource.addProperty("cookingTime", 200);
                resource.add("conditions", new JsonObject());
            }
            case TEXT_TEMPLATE -> {
                resource.addProperty("text", id);
            }
            case ADVANCEMENT_TREE -> {
                JsonObject nodes = new JsonObject();
                JsonObject root = new JsonObject();
                root.addProperty("enabled", true);
                root.addProperty("parent", "");
                JsonObject position = new JsonObject();
                position.addProperty("x", 0);
                position.addProperty("y", 0);
                root.add("position", position);
                JsonObject display = new JsonObject();
                display.addProperty("title", id);
                display.addProperty("description", "Server Progress");
                display.addProperty("icon", "minecraft:nether_star");
                display.addProperty("frame", "task");
                display.addProperty("background", "minecraft:gui/advancements/backgrounds/adventure");
                display.addProperty("showToast", false);
                display.addProperty("announceToChat", false);
                display.addProperty("hidden", false);
                root.add("display", display);
                root.add("criteria", new JsonObject());
                root.add("requirements", new JsonArray());
                JsonObject rewards = new JsonObject();
                rewards.addProperty("experience", 0);
                rewards.add("loot", new JsonArray());
                rewards.add("recipes", new JsonArray());
                root.add("rewards", rewards);
                JsonObject onComplete = new JsonObject();
                onComplete.add("commands", new JsonArray());
                onComplete.addProperty("flowId", "");
                root.add("onComplete", onComplete);
                nodes.add("root", root);
                resource.add("nodes", nodes);
            }
            case DIALOG -> {
                resource.addProperty("type", "minecraft:multi_action");
                resource.addProperty("title", id);
                resource.add("body", new JsonArray());
                resource.add("inputs", new JsonArray());
                resource.addProperty("can_close_with_escape", true);
                resource.addProperty("after_action", "close");
                resource.addProperty("columns", 1);
                JsonArray actions = new JsonArray();
                JsonObject button = new JsonObject();
                button.addProperty("label", "Button");
                button.addProperty("width", 150);
                JsonObject resync = new JsonObject();
                resync.addProperty("actionMode", "None");
                resync.addProperty("predicateMode", "None");
                button.add("resync", resync);
                actions.add(button);
                resource.add("actions", actions);
            }
            case VILLAGE_PROFILE -> {
                resource.addProperty("profession", "librarian");
                resource.addProperty("villagerType", "plains");
                resource.addProperty("level", 1);
                resource.addProperty("restockTicks", 24000);
                resource.addProperty("maxUses", 12);
                resource.addProperty("lootTable", "");
                JsonArray offers = new JsonArray();
                JsonObject offer = new JsonObject();
                offer.addProperty("result", "minecraft:book");
                offer.addProperty("resultAmount", 1);
                offer.addProperty("cost", "minecraft:emerald");
                offer.addProperty("costAmount", 1);
                offer.addProperty("weight", 1);
                offers.add(offer);
                resource.add("offers", offers);
                JsonObject hooks = new JsonObject();
                hooks.addProperty("openFlow", "");
                hooks.addProperty("completeFlow", "");
                hooks.addProperty("deniedFlow", "");
                resource.add("hooks", hooks);
            }
            case NPC_DEFINITION -> {
                resource.addProperty("entityType", "villager");
                resource.addProperty("displayName", id);
                resource.addProperty("spawnMode", "manual");
                resource.addProperty("invulnerable", true);
                resource.addProperty("gravity", true);
                resource.addProperty("ai", false);
                resource.addProperty("followPlayer", false);
                resource.addProperty("followRange", 12);
                resource.addProperty("tradeProfile", "");
                resource.addProperty("lootTable", "");
                JsonObject skin = new JsonObject();
                skin.addProperty("username", "");
                resource.add("skin", skin);
                JsonObject equipment = new JsonObject();
                equipment.addProperty("mainHand", "");
                equipment.addProperty("offHand", "");
                equipment.addProperty("helmet", "");
                equipment.addProperty("chestplate", "");
                equipment.addProperty("leggings", "");
                equipment.addProperty("boots", "");
                resource.add("equipment", equipment);
                resource.add("hooks", new JsonObject());
            }
            case LOOT_TABLE -> {
                resource.addProperty("displayName", id);
                resource.addProperty("enabled", true);
                JsonObject trigger = new JsonObject();
                trigger.addProperty("event", "none");
                trigger.addProperty("target", "");
                trigger.addProperty("entity", "");
                trigger.addProperty("tool", "");
                trigger.addProperty("overrideDrops", true);
                resource.add("trigger", trigger);
                JsonArray pools = new JsonArray();
                JsonObject pool = new JsonObject();
                pool.addProperty("rolls", 1);
                JsonArray entries = new JsonArray();
                JsonObject entry = new JsonObject();
                entry.addProperty("item", "minecraft:stone");
                entry.addProperty("minAmount", 1);
                entry.addProperty("maxAmount", 1);
                entry.addProperty("weight", 1);
                entry.addProperty("chance", 100);
                entries.add(entry);
                pool.add("entries", entries);
                pools.add(pool);
                resource.add("pools", pools);
                JsonObject hooks = new JsonObject();
                hooks.addProperty("beforeRollFlow", "");
                hooks.addProperty("afterRollFlow", "");
                hooks.addProperty("deniedRollFlow", "");
                resource.add("hooks", hooks);
            }
            default -> {
            }
        }
        return resource;
    }

    public void markCustomContentSaved(String serverId, String contentId) {
        customContentStore.markSaved(serverId, contentId);
        CustomContentDefinition content = customContentStore.get(serverId, contentId);
        refreshFlowWorkspace(serverId, content != null ? content.getFlowId() : null, false);
    }

    public void saveProjectMetadata(String serverId, ReSyncProjectMetadata metadata) {
        saveProjectMetadata(serverId, metadata, true);
    }

    public void saveProjectMetadata(String serverId, ReSyncProjectMetadata metadata, boolean refreshWorkspace) {
        if (serverId == null || metadata == null) {
            return;
        }
        metadata.setServerId(serverId);
        projectMetadataStore.putInDraft(serverId, metadata);
        if (canPersistProjectMetadata(serverId)) {
            ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
            if (flowClient != null) {
                projectMetadataStore.markSaving(serverId, serverId);
                flowClient.sendProjectMetadataSave(metadata);
            }
        }
        refreshStudioWorkspace(serverId, refreshWorkspace);
    }

    public void cacheProjectMetadata(String serverId, ReSyncProjectMetadata metadata) {
        if (serverId == null || metadata == null) {
            return;
        }
        metadata.setServerId(serverId);
        metadata.ensureDefaultFolders();
        loadedProjectMetadataLists.add(serverId);
        pendingProjectMetadataDocuments.remove(serverId);
        projectMetadataStore.replaceFromServer(serverId, metadata);
        refreshStudioWorkspace(serverId);
    }

    public void markProjectMetadataSaved(String serverId) {
        projectMetadataStore.markSaved(serverId, serverId);
        refreshStudioWorkspace(serverId, false);
    }

    public void cacheJsonResource(String serverId, ReSyncResourceType type, JsonObject resource) {
        if (serverId == null) {
            return;
        }
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        if (store != null) {
            store.cache(serverId, resource);
            refreshStudioWorkspace(serverId);
        }
    }

    public void cacheServerCapabilities(String serverId, JsonObject capabilities) {
        if (serverId != null && capabilities != null) {
            serverCapabilities.put(serverId, capabilities);
            refreshStudioWorkspace(serverId, false);
        }
    }

    public JsonObject getServerCapabilities(String serverId) {
        return serverCapabilities.get(serverId);
    }

    public void requestMessageLog(String serverId, int page, int pageSize, String query, String source) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (flowClient != null) {
            flowClient.requestMessageLog(page, pageSize, query, source);
        }
    }

    public void cacheMessageLogPage(String serverId, JsonObject page) {
        if (serverId == null || page == null) {
            return;
        }
        messageLogPages.put(serverId, page);
        ScreenManager.getInstance().execute(() -> FocusedJsonResourceDesignerScreen.refreshMessageLogForServer(serverId));
    }

    public JsonObject getMessageLogPage(String serverId) {
        return messageLogPages.get(serverId);
    }

    public void markJsonResourceSaved(String serverId, ReSyncResourceType type, String id) {
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        if (store != null) {
            store.markSaved(serverId, id);
            refreshStudioWorkspace(serverId);
        }
    }

    public JsonObject createJsonResource(String serverId, ReSyncResourceType type, String id, String folder) {
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        if (store == null || id == null || id.isBlank()) {
            return null;
        }
        JsonObject resource = defaultJsonResource(type, id, folder);
        store.putInDraft(serverId, resource);
        store.putNameIfAbsent(serverId, id, type.extractName(resource));
        return resource;
    }

    public void saveJsonResource(String serverId, ReSyncResourceType type, JsonObject resource) {
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        if (store == null || resource == null) {
            return;
        }
        String id = type.extractId(resource);
        if (id == null || id.isBlank()) {
            return;
        }
        store.putInDraft(serverId, resource);
        store.putName(serverId, id, type.extractName(resource));
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            store.markSaving(serverId, id);
            flowClient.sendResourceSave(type, resource);
        }
        refreshStudioWorkspace(serverId);
    }

    public void deleteJsonResource(String serverId, ReSyncResourceType type, String id) {
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        if (store == null || id == null || id.isBlank()) {
            return;
        }
        store.remove(serverId, id);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendResourceDelete(type, id);
        }
        refreshStudioWorkspace(serverId);
    }

    public void applyServerJsonResourceList(String serverId, ReSyncResourceType type, List<String> ids) {
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        if (store == null) {
            return;
        }
        store.applyServerList(serverId, ids);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (ids != null) {
            for (String id : ids) {
                flowClient.requestResource(type, id, false);
            }
        }
        refreshStudioWorkspace(serverId);
    }

    public Map<String, JsonObject> getJsonResourcesForServer(String serverId, ReSyncResourceType type) {
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        return store != null ? store.getForServer(serverId) : Map.of();
    }

    public Map<String, FlowGraph> getFlowsForServer(String serverId) {
        return flowStore.getForServer(serverId);
    }

    public Map<String, GuiDefinition> getGuisForServer(String serverId) {
        return guiStore.getForServer(serverId);
    }

    public Map<String, ScoreboardDefinition> getScoreboardsForServer(String serverId) {
        return scoreboardStore.getForServer(serverId);
    }

    public ScoreboardDefinition getScoreboard(String serverId, String scoreboardId) {
        return scoreboardStore.get(serverId, scoreboardId);
    }

    public String resolveScoreboardId(String serverId, String objectiveId) {
        if (serverId == null || serverId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return objectiveId;
        }
        for (ScoreboardDefinition scoreboard : scoreboardStore.getForServer(serverId).values()) {
            if (scoreboard == null || scoreboard.getId() == null) {
                continue;
            }
            String expectedObjectiveId = scoreboard.getObjectiveId() != null && !scoreboard.getObjectiveId().isBlank() ? scoreboard.getObjectiveId() : scoreboard.getId();
            if (objectiveId.equals(expectedObjectiveId)) {
                return scoreboard.getId();
            }
        }
        return objectiveId;
    }

    public Map<String, TabDefinition> getTabsForServer(String serverId) {
        return tabStore.getForServer(serverId);
    }

    public Map<String, CustomContentDefinition> getCustomContentForServer(String serverId) {
        return customContentStore.getForServer(serverId);
    }

    public boolean isCatalogResourceIdTaken(String serverId, String id, String ignoreType, String ignoreId) {
        if (serverId == null || id == null || id.isBlank()) {
            return false;
        }
        for (ReSyncProjectMetadata.ResourceEntry resource : getProjectMetadata(serverId).getResources()) {
            if (!id.equals(resource.getId())) {
                continue;
            }
            if (ignoreType != null && ignoreType.equals(resource.getType()) && id.equals(ignoreId)) {
                continue;
            }
            return true;
        }
        if (storeContainsId(customContentStore, serverId, id, ReSyncResourceDragPayload.CUSTOM_CONTENT, ignoreType, ignoreId)) {
            return true;
        }
        if (storeContainsId(flowStore, serverId, id, ReSyncResourceDragPayload.FLOW, ignoreType, ignoreId)
            || storeContainsId(flowStore, serverId, id, ReSyncResourceDragPayload.FUNCTION, ignoreType, ignoreId)
            || storeContainsId(flowStore, serverId, id, ReSyncResourceDragPayload.COMMAND, ignoreType, ignoreId)) {
            return true;
        }
        if (storeContainsId(guiStore, serverId, id, ReSyncResourceDragPayload.GUI, ignoreType, ignoreId)) {
            return true;
        }
        if (storeContainsId(scoreboardStore, serverId, id, ReSyncResourceDragPayload.SCOREBOARD, ignoreType, ignoreId)) {
            return true;
        }
        if (storeContainsId(tabStore, serverId, id, ReSyncResourceDragPayload.TAB, ignoreType, ignoreId)) {
            return true;
        }
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
            if (store != null && storeContainsId(store, serverId, id, type.typeId(), ignoreType, ignoreId)) {
                return true;
            }
        }
        return getCommandBinding(serverId, id) != null && !(ReSyncResourceDragPayload.COMMAND.equals(ignoreType) && id.equals(ignoreId));
    }

    private <T> boolean storeContainsId(SyncedResourceCache<T> store, String serverId, String id, String resourceType, String ignoreType, String ignoreId) {
        if (!store.getForServer(serverId).containsKey(id)) {
            return false;
        }
        return !(ignoreType != null && ignoreType.equals(resourceType) && id.equals(ignoreId));
    }

    public ReSyncProjectMetadata getProjectMetadata(String serverId) {
        String actualServerId = serverId != null ? serverId : "";
        ReSyncProjectMetadata metadata = projectMetadataStore.getFromDraft(actualServerId, actualServerId);
        if (metadata == null) {
            metadata = projectMetadataStore.getFromCache(actualServerId, actualServerId);
        }
        if (metadata == null) {
            metadata = new ReSyncProjectMetadata(actualServerId);
            metadata.ensureDefaultFolders();
            projectMetadataStore.putInDraft(actualServerId, metadata);
        }
        if (shouldHydrateProjectMetadata(actualServerId)) {
            int revision = projectCatalogRevisions.getOrDefault(actualServerId, 0);
            if (hydratedProjectCatalogRevisions.getOrDefault(actualServerId, -1) != revision) {
                hydrateProjectMetadata(actualServerId, metadata);
                hydratedProjectCatalogRevisions.put(actualServerId, revision);
            }
        }
        return metadata;
    }

    private void invalidateProjectCatalog(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        projectCatalogRevisions.put(serverId, projectCatalogRevision.incrementAndGet());
        hydratedProjectCatalogRevisions.remove(serverId);
    }

    public void moveProjectResource(String serverId, ReSyncResourceDragPayload payload, String folderPath) {
        if (payload == null || payload.id() == null || payload.id().isBlank()) {
            return;
        }
        ReSyncProjectMetadata metadata = getProjectMetadata(serverId);
        metadata.moveResource(payload.type(), payload.id(), folderPath);
        saveProjectMetadata(serverId, metadata);
    }

    public void createProjectFolder(String serverId, String parentPath, String folderName) {
        String name = folderName == null ? "" : folderName.trim();
        if (name.isBlank()) {
            return;
        }
        ReSyncProjectMetadata metadata = getProjectMetadata(serverId);
        String parent = ReSyncProjectMetadata.normalizePath(parentPath);
        String path = parent.isBlank() ? name : parent + "/" + name;
        metadata.ensureFolder(path, parent, metadata.getFolders().size());
        saveProjectMetadata(serverId, metadata);
    }

    public void setProjectFolderCollapsed(String serverId, String folderPath, boolean collapsed) {
        ReSyncProjectMetadata metadata = getProjectMetadata(serverId);
        metadata.setFolderCollapsed(folderPath, collapsed);
        saveProjectMetadata(serverId, metadata);
    }

    public SyncedResourceState getFlowState(String serverId, String flowId) {
        return flowStore.getState(serverId, flowId);
    }

    public SyncedResourceState getCustomContentState(String serverId, String contentId) {
        return customContentStore.getState(serverId, contentId);
    }

    public Map<String, FlowGraph> getContentGraphsForServer(String serverId, String type) {
        String normalizedType = type != null ? type.toLowerCase(Locale.ROOT) : "";
        Map<String, FlowGraph> result = new HashMap<>();
        for (Map.Entry<String, FlowGraph> entry : flowStore.getForServer(serverId).entrySet()) {
            FlowGraph graph = entry.getValue();
            String contentType = CustomContentGraphAdapter.contentType(graph);
            if (contentType != null && (normalizedType.isBlank() || normalizedType.equals(contentType))) {
                result.put(entry.getKey(), graph);
            }
        }
        return result;
    }

    public String getFlowName(String serverId, String flowId) { return flowStore.getName(serverId, flowId); }
    public void setFlowName(String serverId, String flowId, String name) { flowStore.putName(serverId, flowId, name); }
    public String getGuiName(String serverId, String guiId) { return guiStore.getName(serverId, guiId); }
    public void setGuiName(String serverId, String guiId, String name) { guiStore.putName(serverId, guiId, name); }
    public String getScoreboardName(String serverId, String scoreboardId) { return scoreboardStore.getName(serverId, scoreboardId); }
    public void setScoreboardName(String serverId, String scoreboardId, String name) { scoreboardStore.putName(serverId, scoreboardId, name); }
    public String getTabName(String serverId, String tabId) { return tabStore.getName(serverId, tabId); }
    public void setTabName(String serverId, String tabId, String name) { tabStore.putName(serverId, tabId, name); }
    public String getCustomContentName(String serverId, String contentId) { return customContentStore.getName(serverId, contentId); }
    public void setCustomContentName(String serverId, String contentId, String name) { customContentStore.putName(serverId, contentId, name); }

    public FlowGraph createFlow(String serverId) {
        return createFlow(serverId, null);
    }

    public FlowGraph createFlow(String serverId, String flowId) {
        return createFlow(serverId, flowId, false);
    }

    public FlowGraph createFlow(String serverId, String flowId, boolean function) {
        return createFlow(serverId, flowId, function, FLOW_TEMPLATES.getFirst());
    }

    public FlowGraph createFlow(String serverId, String flowId, boolean function, String templateName) {
        FlowGraph graph = createDefaultFlow(function, templateName);
        if (flowId != null) {
            graph.setId(flowId);
        }
        flowStore.putInDraft(serverId, graph);
        flowStore.putNameIfAbsent(serverId, graph.getId(), graph.getId());
        invalidateProjectCatalog(serverId);
        return graph;
    }

    public FlowGraph createContentFlow(String serverId, String flowId, String type, String displayName) {
        FlowGraph graph = CustomContentGraphAdapter.createContentGraph(flowId, type, displayName);
        flowStore.putInDraft(serverId, graph);
        flowStore.putNameIfAbsent(serverId, graph.getId(), CustomContentGraphAdapter.displayName(graph));
        CustomContentDefinition definition = CustomContentGraphAdapter.toDefinition(graph);
        if (definition != null) {
            customContentStore.putInDraft(serverId, definition);
            customContentStore.putNameIfAbsent(serverId, definition.getId(), definition.getDisplayName());
        }
        invalidateProjectCatalog(serverId);
        return graph;
    }

    public GuiDefinition createGui(String serverId, String id) {
        GuiDefinition gui = createDefaultGui(id);
        guiStore.putInDraft(serverId, gui);
        guiStore.putNameIfAbsent(serverId, id, gui.getTitle());
        invalidateProjectCatalog(serverId);
        return gui;
    }

    public ScoreboardDefinition createScoreboard(String serverId, String id) {
        ScoreboardDefinition scoreboard = createDefaultScoreboard(id);
        scoreboardStore.putInDraft(serverId, scoreboard);
        scoreboardStore.putNameIfAbsent(serverId, id, scoreboard.getTitle());
        invalidateProjectCatalog(serverId);
        return scoreboard;
    }

    public TabDefinition createTab(String serverId, String id) {
        TabDefinition tab = createDefaultTab(id);
        tabStore.putInDraft(serverId, tab);
        tabStore.putNameIfAbsent(serverId, id, tab.getId());
        invalidateProjectCatalog(serverId);
        return tab;
    }

    public void deleteFlow(String serverId, String flowId) {
        flowStore.remove(serverId, flowId);
        invalidateProjectCatalog(serverId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendFlowDelete(flowId);
        }
    }

    public void deleteGui(String serverId, String guiId) {
        guiStore.remove(serverId, guiId);
        invalidateProjectCatalog(serverId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendGuiDelete(guiId);
        }
    }

    public void deleteScoreboard(String serverId, String scoreboardId) {
        scoreboardStore.remove(serverId, scoreboardId);
        invalidateProjectCatalog(serverId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendScoreboardDelete(scoreboardId);
        }
    }

    public void deleteTab(String serverId, String tabId) {
        tabStore.remove(serverId, tabId);
        invalidateProjectCatalog(serverId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendTabDelete(tabId);
        }
    }

    public void deleteCustomContent(String serverId, String contentId) {
        customContentStore.remove(serverId, contentId);
        invalidateProjectCatalog(serverId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendResourceDelete(ReSyncResourceType.CUSTOM_CONTENT, contentId);
        }
    }

    public CustomContentDefinition createCustomContent(String serverId, String type) {
        String normalizedType = type != null ? type.toLowerCase(Locale.ROOT) : "item";
        String id = normalizedType + "_" + UUID.randomUUID().toString().substring(0, 8);
        FlowGraph graph = createContentFlow(serverId, id, normalizedType, null);
        CustomContentDefinition content = CustomContentGraphAdapter.toDefinition(graph);
        if (content != null) {
            saveFlow(serverId, graph);
        }
        return content;
    }

    public boolean renameFlow(String serverId, String flowId, String newFlowId) {
        return renameResource(flowStore, serverId, flowId, newFlowId, ReSyncResourceType.FLOW);
    }

    public boolean renameGui(String serverId, String guiId, String newGuiId) {
        return renameResource(guiStore, serverId, guiId, newGuiId, ReSyncResourceType.GUI);
    }

    public boolean renameScoreboard(String serverId, String scoreboardId, String newScoreboardId) {
        return renameResource(scoreboardStore, serverId, scoreboardId, newScoreboardId, ReSyncResourceType.SCOREBOARD);
    }

    public boolean renameTab(String serverId, String tabId, String newTabId) {
        return renameResource(tabStore, serverId, tabId, newTabId, ReSyncResourceType.TAB);
    }

    public boolean renameJsonResource(String serverId, ReSyncResourceType type, String oldId, String newId) {
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        return store != null && renameResource(store, serverId, oldId, newId, type);
    }

    private <T> boolean renameResource(SyncedResourceCache<T> store, String serverId, String oldId, String newId, ReSyncResourceType type) {
        String trimmedId = newId.trim();
        if (!store.rename(serverId, oldId, trimmedId, type::applyRename)) {
            return false;
        }
        store.resolveDisplayName(serverId, oldId, trimmedId);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        T item = store.get(serverId, trimmedId);
        if (item != null) {
            flowClient.sendResourceSave(type, item);
        }
        if (store.containsServerId(serverId, trimmedId)) {
            flowClient.sendResourceDelete(type, oldId);
        }
        refreshStudioWorkspace(serverId);
        return true;
    }

    public void refreshFlowsFromServer(String serverId) {
        flowStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestFlowList();
        refreshStudioWorkspace(serverId);
    }

    public void refreshGuisFromServer(String serverId) {
        guiStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestGuiList();
        refreshStudioWorkspace(serverId);
    }

    public void refreshScoreboardsFromServer(String serverId) {
        scoreboardStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestScoreboardList();
        refreshStudioWorkspace(serverId);
    }

    public void refreshTabsFromServer(String serverId) {
        tabStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestTabList();
        refreshStudioWorkspace(serverId);
    }

    public void refreshCustomContentFromServer(String serverId) {
        customContentStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestCustomContentList();
        refreshStudioWorkspace(serverId);
    }

    public void refreshProjectMetadataFromServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        refreshCustomizationResourcesFromServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestProjectMetadataList();
        refreshStudioWorkspace(serverId);
    }

    public void refreshCustomizationResourcesFromServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId, true);
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
            if (store != null) {
                store.clearForServer(serverId);
                flowClient.requestResourceList(type);
            }
        }
    }

    public void refreshWorldsFromServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId, true);
        flowClient.requestWorldSnapshot();
        flowClient.requestPlayerTrackingSnapshot();
        refreshStudioWorkspace(serverId);
    }

    public void applyServerFlowList(String serverId, List<String> flowIds) {
        flowStore.applyServerList(serverId, flowIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (flowIds != null) {
            for (String flowId : flowIds) {
                flowClient.requestFlow(flowId, false);
            }
        }
        refreshStudioWorkspace(serverId);
    }

    public void applyServerGuiList(String serverId, List<String> guiIds) {
        guiStore.applyServerList(serverId, guiIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (guiIds != null) {
            for (String guiId : guiIds) {
                flowClient.requestGui(guiId, false);
            }
        }
        refreshStudioWorkspace(serverId);
    }

    public void applyServerScoreboardList(String serverId, List<String> scoreboardIds) {
        scoreboardStore.applyServerList(serverId, scoreboardIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (scoreboardIds != null) {
            for (String scoreboardId : scoreboardIds) {
                flowClient.requestScoreboard(scoreboardId, false);
            }
        }
        refreshStudioWorkspace(serverId);
    }

    public void applyServerTabList(String serverId, List<String> tabIds) {
        tabStore.applyServerList(serverId, tabIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (tabIds != null) {
            for (String tabId : tabIds) {
                flowClient.requestTab(tabId, false);
            }
        }
        refreshStudioWorkspace(serverId);
    }

    public void applyServerCustomContentList(String serverId, List<String> contentIds) {
        customContentStore.applyServerList(serverId, contentIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (contentIds != null) {
            for (String contentId : contentIds) {
                flowClient.requestCustomContent(contentId, false);
            }
        }
        refreshStudioWorkspace(serverId);
    }

    public void applyServerProjectMetadataList(String serverId, List<String> metadataIds) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        loadedProjectMetadataLists.add(serverId);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (metadataIds != null && !metadataIds.isEmpty()) {
            pendingProjectMetadataDocuments.add(serverId);
            flowClient.requestProjectMetadata(metadataIds.getFirst());
        } else {
            refreshFlowWorkspace(serverId, false);
        }
    }

    private void hydrateProjectMetadata(String serverId, ReSyncProjectMetadata metadata) {
        metadata.setServerId(serverId);
        metadata.ensureDefaultFolders();
        Set<String> commandFlowIds = new HashSet<>(getBindings(serverId).stream()
            .filter(binding -> binding != null && binding.getType() == TriggerType.COMMAND && binding.getFlowId() != null && !binding.getFlowId().isBlank())
            .map(TriggerBinding::getFlowId)
            .toList());
        List<String> metadataCommandIds = metadata.getResources().stream()
            .filter(resource -> resource != null && ReSyncResourceDragPayload.COMMAND.equals(resource.getType()) && resource.getId() != null && !resource.getId().isBlank())
            .map(ReSyncProjectMetadata.ResourceEntry::getId)
            .toList();
        commandFlowIds.addAll(metadataCommandIds);
        commandFlowIds.removeIf(flowId -> isCommandFlowIdentityBlocked(serverId, flowId));
        boolean removedCorruptCommands = metadata.getResources().removeIf(resource -> resource != null
            && ReSyncResourceDragPayload.COMMAND.equals(resource.getType())
            && isCommandFlowIdentityBlocked(serverId, resource.getId()));
        if (removedCorruptCommands) {
            getBindings(serverId).removeIf(binding -> binding != null
                && binding.getType() == TriggerType.COMMAND
                && isCommandFlowIdentityBlocked(serverId, binding.getFlowId()));
        }
        Map<String, String> commandPaths = new HashMap<>();
        for (ReSyncProjectMetadata.ResourceEntry resource : metadata.getResources()) {
            if (resource == null || resource.getId() == null || resource.getId().isBlank()) {
                continue;
            }
            if (ReSyncResourceDragPayload.COMMAND.equals(resource.getType()) && !resource.getPath().isBlank()) {
                commandPaths.put(resource.getId(), resource.getPath());
            } else if (ReSyncResourceDragPayload.FLOW.equals(resource.getType()) && commandFlowIds.contains(resource.getId()) && !resource.getPath().isBlank()) {
                commandPaths.put(resource.getId(), resource.getPath());
            }
        }
        metadata.getResources().removeIf(resource -> resource != null && ReSyncResourceDragPayload.FLOW.equals(resource.getType()) && (commandFlowIds.contains(resource.getId()) || metadataCommandIds.contains(resource.getId())));
        for (Map.Entry<String, FlowGraph> entry : flowStore.getForServer(serverId).entrySet()) {
            FlowGraph graph = entry.getValue();
            if (graph == null || CustomContentGraphAdapter.isContentGraph(graph) || commandFlowIds.contains(entry.getKey()) || metadataCommandIds.contains(entry.getKey())) {
                continue;
            }
            String type = graph.isFunction() ? ReSyncResourceDragPayload.FUNCTION : ReSyncResourceDragPayload.FLOW;
            metadata.ensureResource(type, entry.getKey(), getFlowName(serverId, entry.getKey()), graph.isFunction() ? ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.FUNCTION) : ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.FLOW));
        }
        for (Map.Entry<String, CustomContentDefinition> entry : customContentStore.getForServer(serverId).entrySet()) {
            CustomContentDefinition content = entry.getValue();
            String contentType = content != null && content.getType() != null ? content.getType().toLowerCase(Locale.ROOT) : "item";
            metadata.ensureResource(ReSyncResourceDragPayload.CUSTOM_CONTENT, entry.getKey(), getCustomContentName(serverId, entry.getKey()), switch (contentType) {
                case "armor" -> "Content/Armor";
                case "block" -> "Content/Blocks";
                default -> ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.CUSTOM_CONTENT);
            });
        }
        for (Map.Entry<String, GuiDefinition> entry : guiStore.getForServer(serverId).entrySet()) {
            metadata.ensureResource(ReSyncResourceDragPayload.GUI, entry.getKey(), getGuiName(serverId, entry.getKey()), ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.GUI));
        }
        for (Map.Entry<String, ScoreboardDefinition> entry : scoreboardStore.getForServer(serverId).entrySet()) {
            metadata.ensureResource(ReSyncResourceDragPayload.SCOREBOARD, entry.getKey(), getScoreboardName(serverId, entry.getKey()), ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.SCOREBOARD));
        }
        for (Map.Entry<String, TabDefinition> entry : tabStore.getForServer(serverId).entrySet()) {
            metadata.ensureResource(ReSyncResourceDragPayload.TAB, entry.getKey(), getTabName(serverId, entry.getKey()), ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.TAB));
        }
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
            if (store == null) {
                continue;
            }
            for (Map.Entry<String, JsonObject> entry : store.getForServer(serverId).entrySet()) {
                metadata.ensureResource(type.typeId(), entry.getKey(), type.extractName(entry.getValue()), type.defaultFolder());
            }
        }
        for (String commandFlowId : commandFlowIds) {
            metadata.ensureResource(ReSyncResourceDragPayload.COMMAND, commandFlowId, getFlowName(serverId, commandFlowId), commandPaths.getOrDefault(commandFlowId, ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.COMMAND)));
        }
        for (String projectId : WorldGenManager.getInstance().getProjectIds(serverId)) {
            metadata.ensureResource(ReSyncResourceDragPayload.WORLDGEN, projectId, projectId, ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.WORLDGEN));
        }
        for (String worldName : getWorldsForServer(serverId).keySet()) {
            metadata.ensureResource(ReSyncResourceDragPayload.WORLD, worldName, worldName, ReSyncResourceType.defaultFolderFor(ReSyncResourceDragPayload.WORLD));
        }
        if (removedCorruptCommands && canPersistProjectMetadata(serverId)) {
            saveProjectMetadata(serverId, metadata, false);
            sendTriggerUpdate(serverId, getBindings(serverId));
        }
    }

    public void applyPlayerTrackingUpdate(String serverId, PlayerTrackingUpdate update) {
        playerService.applyPlayerTrackingUpdate(serverId, update);
    }

    public PlayerDossier getPlayerDossier(String serverId, UUID playerId) {
        return playerService.getPlayerDossier(serverId, playerId);
    }

    public List<String> getOnlinePlayerNamesForServer(String serverId) {
        return playerService.getOnlinePlayerNamesForServer(serverId);
    }

    public List<PlayerDossier> getOnlinePlayersForServer(String serverId) {
        return playerService.getOnlinePlayersForServer(serverId);
    }

    public void requestPlayerTrackingSnapshot(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        connectionManager.ensureFlowClient(serverId).requestPlayerTrackingSnapshot();
    }

    public void requestPlayerDossier(String serverId, UUID playerId) {
        if (serverId == null || serverId.isBlank() || playerId == null) {
            return;
        }
        connectionManager.ensureFlowClient(serverId).requestPlayerDossier(playerId);
    }

    public Map<String, WorldRegistryEntry> getWorldsForServer(String serverId) {
        return worldService.getWorldsForServer(serverId);
    }

    public List<WorldDashboardEntry> getWorldDashboardForServer(String serverId) {
        return worldService.getWorldDashboardForServer(serverId);
    }

    public List<WorldInventoryGroup> getWorldInventoryGroupsForServer(String serverId) {
        return worldService.getWorldInventoryGroupsForServer(serverId);
    }

    public WorldInventoryGroup getWorldInventoryGroup(String serverId, String groupId) {
        return worldService.getWorldInventoryGroup(serverId, groupId);
    }

    public WorldRegistryEntry getWorld(String serverId, String worldName) {
        return worldService.getWorld(serverId, worldName);
    }

    public WorldSnapshot getWorldSnapshot(String serverId) {
        return worldService.getWorldSnapshot(serverId);
    }

    public WorldOperationResult getLastWorldOperationResult(String serverId) {
        return worldService.getLastWorldOperationResult(serverId);
    }

    public WorldMapSnapshot getWorldMapSnapshot(String serverId, String worldName) {
        return worldService.getWorldMapSnapshot(serverId, worldName);
    }

    public void applyWorldManagementMessage(String serverId, WorldChannelMessage message) {
        worldService.applyWorldManagementMessage(serverId, message, this);
    }

    public void requestWorldMapSnapshot(String serverId, String worldName, double centerX, double centerZ, int zoom) {
        worldService.requestWorldMapSnapshot(serverId, worldName, centerX, centerZ, zoom, this);
    }

    public void requestWorldAuditSnapshot(String serverId) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("auditSnapshot", "limit", 25));
    }

    public void suppressNextWorldSuccessNotification(String serverId, String action) {
        worldService.suppressNextWorldSuccessNotification(serverId, action);
    }

    public void createWorld(String serverId, String worldName, String seed, String environment, String generator, String generatorConfig) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("createWorld", "worldName", worldName, "seed", seed, "environment", environment, "generator", generator, "generatorConfig", generatorConfig));
    }

    public void importWorlds(String serverId) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("importUnregisteredWorlds"));
    }

    public void scanWorlds(String serverId) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("scanUnregisteredWorlds"));
    }

    public void cloneWorld(String serverId, String sourceWorld, String targetWorld, boolean loadAfterClone) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("cloneWorld", "sourceWorld", sourceWorld, "targetWorld", targetWorld, "loadAfterClone", loadAfterClone));
    }

    public void loadWorld(String serverId, String worldName) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("loadWorld", "worldName", worldName));
    }

    public void unloadWorld(String serverId, String worldName, String fallbackWorld) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("unloadWorld", "worldName", worldName, "fallbackWorld", fallbackWorld));
    }

    public void deleteWorld(String serverId, String worldName, boolean deleteFiles, String fallbackWorld) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("deleteWorld", "worldName", worldName, "deleteFiles", deleteFiles, "fallbackWorld", fallbackWorld));
    }

    public void setWorldGameRule(String serverId, String worldName, String ruleName, String value) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("setGameRule", "worldName", worldName, "ruleName", ruleName, "ruleValue", value));
    }

    public void setWorldGameRules(String serverId, String worldName, Map<String, String> rules) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("setGameRules", "worldName", worldName, "gameRules", rules));
    }

    public void setWorldDifficulty(String serverId, String worldName, String difficulty) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("setDifficulty", "worldName", worldName, "difficulty", difficulty));
    }

    public void setWorldTimeLock(String serverId, String worldName, boolean enabled, long lockedTime) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("setTimeLock", "worldName", worldName, "enabled", enabled, "lockedTime", lockedTime));
    }

    public void setWorldWeatherLock(String serverId, String worldName, boolean enabled, boolean storm, boolean thundering) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("setWeatherLock", "worldName", worldName, "enabled", enabled, "storm", storm, "thundering", thundering));
    }

    public void setWorldIsolatedState(String serverId, String worldName, boolean enabled) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("setIsolatedPlayerState", "worldName", worldName, "enabled", enabled));
    }

    public void setWorldProfile(String serverId, String worldName, WorldProfileSettings profileSettings) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("setWorldProfile", "worldName", worldName, "profileSettings", profileSettings));
    }

    public void teleportPlayerToWorld(String serverId, String playerName, String worldName, Double x, Double y, Double z, Float yaw, Float pitch) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction(
            "teleportPlayerToWorld",
            "playerName", playerName,
            "worldName", worldName,
            "destinationX", x,
            "destinationY", y,
            "destinationZ", z,
            "destinationYaw", yaw,
            "destinationPitch", pitch,
            "hasPosition", x != null && y != null && z != null,
            "hasRotation", yaw != null && pitch != null
        ));
    }

    public void teleportPlayerToWorldSpawn(String serverId, String playerName, String worldName) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("teleportPlayerToWorldSpawn", "playerName", playerName, "worldName", worldName));
    }

    public void createInventoryGroup(String serverId, WorldInventoryGroup group) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("createInventoryGroup", "inventoryGroup", group));
    }

    public void updateInventoryGroup(String serverId, WorldInventoryGroup group) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("updateInventoryGroup", "inventoryGroup", group));
    }

    public void deleteInventoryGroup(String serverId, String groupId) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("deleteInventoryGroup", "groupId", groupId));
    }

    public void whoWorld(String serverId, String worldName) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction("whoWorld", "worldName", worldName));
    }

    public void purgeWorld(String serverId, String worldName, boolean monsters, boolean animals, boolean ambient, boolean misc, boolean vehicles, boolean items) {
        sendWorldAction(serverId, ReSyncWorldService.worldAction(
            "purgeWorld",
            "worldName", worldName,
            "purgeMonsters", monsters,
            "purgeAnimals", animals,
            "purgeAmbient", ambient,
            "purgeMisc", misc,
            "purgeVehicles", vehicles,
            "purgeItems", items
        ));
    }

    public void openWorldMap(String serverId, ClientServerView server, String worldName) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        Instance instance = resolveInstance(actualServerId, server);
        if (instance == null) {
            new Notification("WorldMap", "InstanceUnavailable", Notification.Type.ERROR);
            return;
        }
        Object parent = resolveDesignerParent(null);
        Screen parentScreen = parent instanceof Screen screen ? screen : ScreenManager.getInstance().getCurrentScreen();
        WorldMapScreen mapScreen = createWorldMapScreen(parentScreen, instance, worldName, location -> {
            if (location == null || location.uuid() == null) {
                return;
            }
            PlayerManagerController controller = PlayerManagerController.getOrCreate(instance);
            controller.requestPlayerDossier(location.uuid());
            UnifiedPlayer player = new UnifiedPlayer(location.uuid(), location.name());
            new PlayerDataPopup(ScreenManager.getInstance().getCurrentScreen(), player, controller);
        });
        client.getHost().setScreen(mapScreen);
    }

    public List<TriggerBinding> getBindings(String serverId) {
        return triggerBindings.computeIfAbsent(serverId, id -> new ArrayList<>());
    }

    public FlowGraph resolveCommandFlowGraph(String serverId, String commandResourceId) {
        if (serverId == null || commandResourceId == null || commandResourceId.isBlank()) {
            return null;
        }
        FlowGraph graph = flowStore.get(serverId, commandResourceId);
        if (graph != null && CustomContentGraphAdapter.isContentGraph(graph)) {
            return null;
        }
        return graph;
    }

    public boolean isCommandFlowIdentityBlocked(String serverId, String flowId) {
        if (serverId == null || flowId == null || flowId.isBlank()) {
            return false;
        }
        FlowGraph graph = flowStore.get(serverId, flowId);
        if (graph != null && CustomContentGraphAdapter.isContentGraph(graph)) {
            return true;
        }
        return customContentStore.getForServer(serverId).containsKey(flowId);
    }

    public TriggerBinding getCommandBinding(String serverId, String flowId) {
        if (serverId == null || flowId == null) {
            return null;
        }
        for (TriggerBinding binding : getBindings(serverId)) {
            if (flowId.equals(binding.getFlowId()) && binding.getType() == TriggerType.COMMAND) {
                return binding;
            }
        }
        return null;
    }

    public void setCommandBinding(String serverId, String flowId, String context) {
        if (serverId == null || flowId == null) {
            return;
        }
        List<TriggerBinding> bindings = getBindings(serverId);
        bindings.removeIf(binding -> flowId.equals(binding.getFlowId()) && binding.getType() == TriggerType.COMMAND);
        if (context != null && !context.isBlank()) {
            if (isCommandFlowIdentityBlocked(serverId, flowId)) {
                return;
            }
            bindings.add(new TriggerBinding(flowId + ":command", flowId, TriggerType.COMMAND, context));
            ensureCommandStartNode(serverId, flowId);
        }
        invalidateProjectCatalog(serverId);
        sendTriggerUpdate(serverId, bindings);
    }

    public void clearCommandBinding(String serverId, String flowId) {
        setCommandBinding(serverId, flowId, null);
    }

    public void addBinding(String serverId, TriggerBinding binding) {
        getBindings(serverId).add(binding);
        sendTriggerUpdate(serverId, getBindings(serverId));
    }

    public void removeBinding(String serverId, String bindingId) {
        getBindings(serverId).removeIf(binding -> bindingId.equals(binding.getId()));
        sendTriggerUpdate(serverId, getBindings(serverId));
    }

    public void resolvePlaceholderPreview(String serverId, String text, Consumer<String> callback) {
        if (callback == null) {
            return;
        }
        String value = text != null ? text : "";
        if (serverId == null || serverId.isBlank()) {
            callback.accept(value);
            return;
        }
        connectionManager.ensureFlowClient(serverId).requestPlaceholderPreview(value, true, callback);
    }

    public void handleGuiStatePacket(String serverId, boolean editable, String guiId, String flowId) {
        if (!editable) {
            boolean sameServer = serverId == null || serverId.isBlank() || serverId.equals(guiOverlayServerId);
            boolean sameGui = guiId != null && !guiId.isBlank() && guiId.equals(guiOverlayGuiId);
            if (sameServer && sameGui) {
                clearGuiOverlayState();
                GuiEditOverlayState.clear();
            }
            return;
        }
        guiOverlayEditable = true;
        guiOverlayServerId = serverId;
        guiOverlayGuiId = guiId;
        guiOverlayFlowId = flowId;
        overlayRevision.incrementAndGet();
        GuiEditOverlayState.update(serverId, guiId, flowId, true);
        if (guiId != null && !guiId.isBlank()) {
            ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
            if (flowClient != null) {
                flowClient.requestGui(guiId, false);
            }
        }
    }

    public void handleEditTargetStatePacket(String serverId, boolean editable, String resourceType, String resourceId, String flowId) {
        if (!editable) {
            if (resourceType == null || resourceType.isBlank() || resourceType.equals(editTargetOverlayResourceType)) {
                clearEditTargetOverlayState();
            }
            return;
        }
        if (resourceType == null || resourceType.isBlank() || resourceId == null || resourceId.isBlank()) {
            return;
        }
        editTargetOverlayEditable = true;
        editTargetOverlayServerId = serverId;
        editTargetOverlayResourceType = resourceType;
        editTargetOverlayResourceId = resourceId;
        editTargetOverlayFlowId = flowId;
        overlayRevision.incrementAndGet();
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            if ("gui".equals(resourceType)) {
                flowClient.requestGui(resourceId, false);
            } else if ("scoreboard".equals(resourceType)) {
                flowClient.requestScoreboard(resourceId, false);
            } else {
                ReSyncResourceType jsonType = ReSyncResourceType.byTypeId(resourceType);
                if (jsonType != null) {
                    flowClient.requestResource(jsonType, resourceId, false);
                }
            }
        }
    }

    public boolean isGuiOverlayEditable() { return guiOverlayEditable; }
    public String getGuiOverlayServerId() { return guiOverlayServerId; }
    public String getGuiOverlayGuiId() { return guiOverlayGuiId; }
    public String getGuiOverlayFlowId() { return guiOverlayFlowId; }
    public boolean isEditTargetOverlayEditable() { return editTargetOverlayEditable; }
    public String getEditTargetOverlayServerId() { return editTargetOverlayServerId; }
    public String getEditTargetOverlayResourceType() { return editTargetOverlayResourceType; }
    public String getEditTargetOverlayResourceId() { return editTargetOverlayResourceId; }
    public String getEditTargetOverlayFlowId() { return editTargetOverlayFlowId; }
    public int getOverlayRevision() { return overlayRevision.get(); }

    public void clearOverlayState() {
        clearGuiOverlayState();
        clearEditTargetOverlayState();
    }

    private void clearGuiOverlayState() {
        guiOverlayEditable = false;
        guiOverlayServerId = null;
        guiOverlayGuiId = null;
        guiOverlayFlowId = null;
        overlayRevision.incrementAndGet();
    }

    private void clearEditTargetOverlayState() {
        editTargetOverlayEditable = false;
        editTargetOverlayServerId = null;
        editTargetOverlayResourceType = null;
        editTargetOverlayResourceId = null;
        editTargetOverlayFlowId = null;
        overlayRevision.incrementAndGet();
    }

    public void handleGuiDataReceived(String serverId, GuiDefinition gui) {
        if (gui == null || gui.getId() == null || gui.getId().isBlank()) {
            return;
        }
        if (flushPendingStudioEditTarget(serverId, ReSyncResourceDragPayload.GUI, gui.getId())) {
            return;
        }
        Object parent = guiStore.removePendingParent(serverId, gui.getId());
        if (parent != null) {
            boolean fullEditor = designerFullEditor(parent);
            parent = designerParent(parent);
            if (openExistingStudioDesigner(serverId, ReSyncResourceDragPayload.GUI, gui.getId(), fullEditor)) {
                return;
            }
            client.getHost().setScreen(new GuiDesignerScreen(detachedGui(gui), serverId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
        }
    }

    public void handleScoreboardDataReceived(String serverId, ScoreboardDefinition scoreboard) {
        if (scoreboard == null || scoreboard.getId() == null || scoreboard.getId().isBlank()) {
            return;
        }
        if (flushPendingStudioEditTarget(serverId, ReSyncResourceDragPayload.SCOREBOARD, scoreboard.getId())) {
            return;
        }
        Object parent = scoreboardStore.removePendingParent(serverId, scoreboard.getId());
        if (parent != null) {
            boolean fullEditor = designerFullEditor(parent);
            parent = designerParent(parent);
            if (openExistingStudioDesigner(serverId, ReSyncResourceDragPayload.SCOREBOARD, scoreboard.getId(), fullEditor)) {
                return;
            }
            client.getHost().setScreen(new ScoreboardDesignerScreen(detachedScoreboard(scoreboard), serverId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
        }
    }

    public void handleTabDataReceived(String serverId, TabDefinition tab) {
        if (tab == null || tab.getId() == null || tab.getId().isBlank()) {
            return;
        }
        if (flushPendingStudioEditTarget(serverId, ReSyncResourceDragPayload.TAB, tab.getId())) {
            return;
        }
        Object parent = tabStore.removePendingParent(serverId, tab.getId());
        if (parent != null) {
            boolean fullEditor = designerFullEditor(parent);
            parent = designerParent(parent);
            if (openExistingStudioDesigner(serverId, ReSyncResourceDragPayload.TAB, tab.getId(), fullEditor)) {
                return;
            }
            client.getHost().setScreen(new TabDesignerScreen(detachedTab(tab), serverId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
        }
    }

    public void handleAdvancementTreeDataReceived(String serverId, JsonObject tree) {
        String treeId = ReSyncResourceType.ADVANCEMENT_TREE.extractId(tree);
        if (treeId == null || treeId.isBlank()) {
            return;
        }
        if (flushPendingStudioEditTarget(serverId, ReSyncResourceDragPayload.ADVANCEMENT_TREE, treeId)) {
            return;
        }
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(ReSyncResourceType.ADVANCEMENT_TREE);
        Object parent = store != null ? store.removePendingParent(serverId, treeId) : null;
        if (parent != null) {
            boolean fullEditor = designerFullEditor(parent);
            parent = designerParent(parent);
            if (fullEditor && openExistingStudioDesigner(serverId, ReSyncResourceDragPayload.ADVANCEMENT_TREE, treeId, true)) {
                return;
            }
            client.getHost().setScreen(new AdvancementDesignerScreen(detachedJson(tree), serverId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
        }
    }

    public void handleDialogDataReceived(String serverId, JsonObject dialog) {
        String dialogId = ReSyncResourceType.DIALOG.extractId(dialog);
        if (dialogId == null || dialogId.isBlank()) {
            return;
        }
        if (flushPendingStudioEditTarget(serverId, ReSyncResourceDragPayload.DIALOG, dialogId)) {
            return;
        }
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(ReSyncResourceType.DIALOG);
        Object parent = store != null ? store.removePendingParent(serverId, dialogId) : null;
        if (parent != null) {
            boolean fullEditor = designerFullEditor(parent);
            parent = designerParent(parent);
            if (openExistingStudioDesigner(serverId, ReSyncResourceDragPayload.DIALOG, dialogId, fullEditor)) {
                return;
            }
            client.getHost().setScreen(new DialogDesignerScreen(detachedJson(dialog), serverId, parent, fullEditor || !(parent instanceof Screen), fullEditor));
        }
    }

    public void handleFocusedJsonResourceDataReceived(String serverId, ReSyncResourceType type, JsonObject resource) {
        String resourceId = type.extractId(resource);
        if (resourceId == null || resourceId.isBlank()) {
            return;
        }
        if (flushPendingStudioEditTarget(serverId, type.typeId(), resourceId)) {
            return;
        }
        SyncedResourceCache<JsonObject> store = jsonResourceStores.get(type);
        Object parent = store != null ? store.removePendingParent(serverId, resourceId) : null;
        if (parent == null) {
            return;
        }
        boolean fullEditor = designerFullEditor(parent);
        parent = designerParent(parent);
        if (openExistingStudioDesigner(serverId, type.typeId(), resourceId, fullEditor)) {
            return;
        }
        switch (type) {
            case VILLAGE_PROFILE -> client.getHost().setScreen(new VillageDesignerScreen(null, resourceId, detachedJson(resource), serverId, parent));
            case NPC_DEFINITION -> client.getHost().setScreen(new NpcDesignerScreen(null, resourceId, detachedJson(resource), serverId, parent));
            case LOOT_TABLE -> client.getHost().setScreen(new LootTableDesignerScreen(null, resourceId, detachedJson(resource), serverId, parent));
            default -> {
            }
        }
    }

    void refreshStudioWorkspace(String serverId) {
        refreshStudioWorkspace(serverId, true);
    }

    void refreshStudioWorkspace(String serverId, boolean rebuildContentBrowser) {
        invalidateProjectCatalog(serverId);
        ScreenManager.getInstance().execute(() -> {
            refreshOpenStudioWorkspace(serverId, rebuildContentBrowser);
            flushPendingStudioEditTarget(serverId);
            AdvancementDesignerScreen.refreshCatalogForServer(serverId);
            DialogDesignerScreen.refreshCatalogForServer(serverId);
            FocusedJsonResourceDesignerScreen.refreshCatalogForServer(serverId);
            GuiDesignerScreen.refreshCatalogForServer(serverId);
            FlowEditorScreen.refreshWorldsForServer(serverId);
        });
    }

    void refreshFlowWorkspace(String serverId, boolean rebuildContentBrowser) {
        refreshFlowWorkspace(serverId, null, rebuildContentBrowser);
    }

    void refreshFlowWorkspace(String serverId, String changedFlowId, boolean rebuildContentBrowser) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        if (!hasFlowWorkspaceRefreshTargets(serverId, changedFlowId, rebuildContentBrowser)) {
            return;
        }
        boolean schedule;
        synchronized (flowWorkspaceRefreshLock) {
            PendingFlowWorkspaceRefresh pending = pendingFlowWorkspaceRefreshes.computeIfAbsent(serverId, ignored -> new PendingFlowWorkspaceRefresh());
            pending.add(changedFlowId, rebuildContentBrowser);
            schedule = !pending.scheduled;
            if (schedule) {
                pending.scheduled = true;
            }
        }
        if (!schedule) {
            return;
        }
        ScreenManager.getInstance().execute(() -> {
            FlowWorkspaceRefreshSnapshot refresh;
            synchronized (flowWorkspaceRefreshLock) {
                PendingFlowWorkspaceRefresh pending = pendingFlowWorkspaceRefreshes.remove(serverId);
                if (pending == null) {
                    return;
                }
                refresh = pending.snapshot();
            }
            if (refresh.rebuildContentBrowser()) {
                refreshOpenStudioContentBrowser(serverId);
            }
            flushPendingStudioEditTarget(serverId);
            if (refresh.refreshAllFlowBindings() || refresh.flowIds().isEmpty()) {
                refreshFlowBindingsForServer(serverId, null);
                return;
            }
            for (String flowId : refresh.flowIds()) {
                refreshFlowBindingsForServer(serverId, flowId);
            }
        });
    }

    private void refreshFlowBindingsForServer(String serverId, String flowId) {
        AdvancementDesignerScreen.refreshFlowBindingsForServer(serverId, flowId);
        DialogDesignerScreen.refreshFlowBindingsForServer(serverId, flowId);
        FocusedJsonResourceDesignerScreen.refreshFlowBindingsForServer(serverId, flowId);
        GuiDesignerScreen.refreshFlowBindingsForServer(serverId, flowId);
    }

    private boolean hasFlowWorkspaceRefreshTargets(String serverId, String changedFlowId, boolean rebuildContentBrowser) {
        if (rebuildContentBrowser || FlowEditorScreen.hasOpenStudioScreenForServer(serverId) || studioFullEditorSession.hasPendingTarget(serverId)) {
            return true;
        }
        if (changedFlowId == null || changedFlowId.isBlank()) {
            return AdvancementDesignerScreen.hasOpenScreenForServer(serverId)
                || DialogDesignerScreen.hasOpenScreenForServer(serverId)
                || FocusedJsonResourceDesignerScreen.hasOpenScreenForServer(serverId)
                || GuiDesignerScreen.hasOpenScreenForServer(serverId);
        }
        return AdvancementDesignerScreen.hasFlowBindingForServer(serverId, changedFlowId)
            || DialogDesignerScreen.hasFlowBindingForServer(serverId, changedFlowId)
            || FocusedJsonResourceDesignerScreen.hasFlowBindingForServer(serverId, changedFlowId)
            || GuiDesignerScreen.hasFlowBindingForServer(serverId, changedFlowId);
    }

    private GuiDefinition detachedGui(GuiDefinition gui) {
        return gui != null ? FlowSerializer.deserializeGui(FlowSerializer.serializeGui(gui)) : null;
    }

    private ScoreboardDefinition detachedScoreboard(ScoreboardDefinition scoreboard) {
        return scoreboard != null ? FlowSerializer.deserializeScoreboard(FlowSerializer.serializeScoreboard(scoreboard)) : null;
    }

    private TabDefinition detachedTab(TabDefinition tab) {
        return tab != null ? FlowSerializer.deserializeTab(FlowSerializer.serializeTab(tab)) : null;
    }

    private JsonObject detachedJson(JsonObject json) {
        return json != null ? json.deepCopy() : new JsonObject();
    }

    private void refreshOpenStudioWorkspace(String serverId, boolean rebuildContentBrowser) {
        FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
        if (studioScreen != null) {
            studioScreen.refreshStudioWorkspace(rebuildContentBrowser);
        }
    }

    private void refreshOpenStudioContentBrowser(String serverId) {
        FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
        if (studioScreen != null) {
            studioScreen.refreshStudioContentBrowserOnly();
        }
    }

    void refreshStudioWorlds(String serverId) {
        ScreenManager.getInstance().execute(() -> {
            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
            if (studioScreen != null) {
                studioScreen.refreshStudioWorkspace();
            }
            FlowEditorScreen.refreshWorldsForServer(serverId);
        });
    }

    private FlowGraph createDefaultFlow() {
        return createDefaultFlow(false, FLOW_TEMPLATES.getFirst());
    }

    private FlowGraph createDefaultFlow(boolean function, String templateName) {
        FlowGraph graph = new FlowGraph();
        graph.setFunction(function);
        if (function) {
            String startId = UUID.randomUUID().toString();
            String endId = UUID.randomUUID().toString();
            graph.getNodes().put(startId, new FlowNode("function_start", 120, 120, new HashMap<>()));
            graph.getNodes().put(endId, new FlowNode("function_end", 380, 120, new HashMap<>()));
            graph.getConnections().add(new FlowConnection(startId, "flow", endId, "flow"));
            return graph;
        }
        if (!function && "command".equalsIgnoreCase(templateName)) {
            graph.getNodes().put(UUID.randomUUID().toString(), new FlowNode("event.resync.command", 120, 120, new HashMap<>()));
        }
        return graph;
    }

    private GuiDefinition createDefaultGui(String id) {
        GuiDefinition gui = new GuiDefinition();
        gui.setId(id);
        gui.setTitle("Main Menu");
        gui.setRows(3);
        Visual visual = new Visual("DIAMOND", "<yellow>Main Button</yellow>");
        GuiElement btn = new GuiElement();
        btn.getSlots().add(13);
        btn.setVisual(visual);
        btn.setFlowId("main_flow");
        gui.getElements().add(btn);
        return gui;
    }

    private ScoreboardDefinition createDefaultScoreboard(String id) {
        ScoreboardDefinition scoreboard = new ScoreboardDefinition();
        scoreboard.setId(id);
        scoreboard.setObjectiveId(id);
        scoreboard.setTitle("Server");
        scoreboard.setDisplaySlot("sidebar");
        scoreboard.getLines().add("<gray>Online: <white>%server_online%</white>");
        scoreboard.getLines().add("<gray>Ping: <green>%player_ping%</green>");
        scoreboard.getLines().add("<gray>Rank: <gold>%vault_rank%</gold>");
        return scoreboard;
    }

    private TabDefinition createDefaultTab(String id) {
        TabDefinition tab = new TabDefinition();
        tab.setId(id);
        tab.setHeader("<gold>Server Network");
        tab.setEntryFormat("<gray>•</gray> <white>%player%</white>");
        tab.setFooter("<gray>Online: <green>%server_online%</green>");
        return tab;
    }

    private void ensureCommandStartNode(String serverId, String flowId) {
        FlowGraph graph = flowStore.get(serverId, flowId);
        if (graph == null || graph.getNodes() == null || CustomContentGraphAdapter.isContentGraph(graph)) {
            return;
        }
        boolean changed = false;
        boolean hasCommandNode = false;
        for (FlowNode node : graph.getNodes().values()) {
            if (node == null) {
                continue;
            }
            if ("event.resync.command".equals(node.getType())) {
                hasCommandNode = true;
                continue;
            }
            if ("event:resync_command".equals(node.getType())) {
                node.setType("event.resync.command");
                hasCommandNode = true;
                changed = true;
            }
        }
        if (hasCommandNode) {
            if (changed) {
                saveFlow(serverId, graph);
            }
            return;
        }
        graph.getNodes().put(UUID.randomUUID().toString(), new FlowNode("event.resync.command", 120, 120, new HashMap<>()));
        saveFlow(serverId, graph);
    }

    private void sendTriggerUpdate(String serverId, List<TriggerBinding> bindings) {
        connectionManager.ensureFlowClient(serverId).sendTriggerUpdate(bindings);
    }

    private void sendWorldAction(String serverId, Map<String, Object> request) {
        if (serverId == null || serverId.isBlank() || request == null || request.isEmpty()) {
            return;
        }
        connectionManager.ensureFlowClient(serverId).sendWorldAction(request);
    }

    private WorldMapScreen createWorldMapScreen(Screen parentScreen, Instance instance, String worldName, Consumer<MinecraftPlayerLocation> onPlayerSelected) {
        try {
            return WorldMapScreen.class
                .getConstructor(Screen.class, Instance.class, String.class, Consumer.class)
                .newInstance(parentScreen, instance, worldName, onPlayerSelected);
        } catch (Exception ignored) {
            return new WorldMapScreen(parentScreen, instance, onPlayerSelected);
        }
    }

    private Instance resolveInstance(String serverId, ClientServerView server) {
        return connectionManager.findInstanceByServerId(serverId, server);
    }

    private String getOrCreateDefaultFlowId(String serverId) {
        Map<String, FlowGraph> flows = getFlowsForServer(serverId);
        if (!flows.isEmpty()) {
            return flows.keySet().iterator().next();
        }
        FlowGraph graph = createFlow(serverId);
        return graph.getId();
    }
}
