package redxax.oxy.remotely.data.flow;

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
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.data.TriggerType;
import redxax.oxy.remotely.flow.data.Visual;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.FlowManagerScreen;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import redxax.oxy.remotely.flow.ui.ScoreboardDesignerScreen;
import redxax.oxy.remotely.flow.ui.TabDesignerScreen;
import redxax.oxy.remotely.ui.widgets.management.PlayerDataPopup;
import redxax.oxy.remotely.ui.widgets.management.PlayerManagerController;
import restudio.rebase.instance.Instance;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rebase.restudio.api.models.ServerModels.PteroFileObjectAttributes;
import restudio.rebase.ui.worldmap.WorldMapScreen;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FlowManager {
    private static FlowManager INSTANCE;
    private static final List<String> FLOW_TEMPLATES = List.of("Blank", "Command");
    private final RemotelyClient client;
    private final ReSyncConnectionManager connectionManager;
    private final ReSyncWorldService worldService;
    private final ReSyncPlayerService playerService;
    private final SyncedResourceCache<FlowGraph> flowStore = new SyncedResourceCache<>(FlowGraph::getId, FlowGraph::getId);
    private final SyncedResourceCache<GuiDefinition> guiStore = new SyncedResourceCache<>(GuiDefinition::getId, g -> g.getTitle() != null ? g.getTitle() : g.getId());
    private final SyncedResourceCache<ScoreboardDefinition> scoreboardStore = new SyncedResourceCache<>(ScoreboardDefinition::getId, s -> s.getTitle() != null ? s.getTitle() : s.getId());
    private final SyncedResourceCache<TabDefinition> tabStore = new SyncedResourceCache<>(TabDefinition::getId, TabDefinition::getId);
    private final SyncedResourceCache<CustomContentDefinition> customContentStore = new SyncedResourceCache<>(CustomContentDefinition::getId, c -> c.getDisplayName() != null ? c.getDisplayName() : c.getId());
    private final Map<String, List<TriggerBinding>> triggerBindings = new ConcurrentHashMap<>();
    private volatile boolean overlayEditable;
    private volatile String overlayServerId;
    private volatile String overlayGuiId;
    private volatile String overlayFlowId;
    private final AtomicInteger overlayRevision = new AtomicInteger();

    public FlowManager(RemotelyClient client, ReStudioApiClient apiClient) {
        this.client = client;
        this.connectionManager = new ReSyncConnectionManager(client, apiClient);
        this.worldService = new ReSyncWorldService();
        this.playerService = new ReSyncPlayerService();
        INSTANCE = this;
    }

    public static FlowManager getInstance() {
        return INSTANCE;
    }

    public void openFlowManager(String serverId, ClientServerView server, String loaderHint) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        connectionManager.resolveAndStoreProfile(actualServerId, server);
        client.getHost().setScreen(new FlowManagerScreen(actualServerId, server, loaderHint, ScreenManager.getInstance().getCurrentScreen()));
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

    public void closeServerConnection(String serverId) {
        connectionManager.closeServerConnection(serverId, () -> {
            flowStore.clearForServer(serverId);
            guiStore.clearForServer(serverId);
            scoreboardStore.clearForServer(serverId);
            tabStore.clearForServer(serverId);
            customContentStore.clearForServer(serverId);
            playerService.clearCache(serverId);
            worldService.clearCache(serverId);
        });
    }

    public void provisionReSyncForReStudioServer(String serverId, Consumer<Boolean> callback) {
        connectionManager.provisionReSyncForReStudioServer(serverId, callback);
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
        return connectionManager.getApiClient().listFiles(serverId, "/plugins")
                .thenApply(files -> {
                    if (files == null) return false;
                    for (PteroFileObjectAttributes file : files) {
                        if (file != null && file.isFile && file.name != null) {
                            String lower = file.name.toLowerCase(Locale.ROOT);
                            if (lower.startsWith("resync") && lower.endsWith(".jar")) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .exceptionally(ex -> false);
    }

    public String normalizeReSyncNotificationMessage(String message) {
        return connectionManager.normalizeReSyncNotificationMessage(message);
    }

    public void requestInitialFlowData(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        refreshFlowsFromServer(serverId);
        refreshGuisFromServer(serverId);
        refreshScoreboardsFromServer(serverId);
        refreshTabsFromServer(serverId);
        refreshCustomContentFromServer(serverId);
        refreshWorldsFromServer(serverId);
    }

    public void openFlowEditor(String serverId, ClientServerView server) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        String flowId = getOrCreateDefaultFlowId(actualServerId);
        openFlowEditor(actualServerId, server, flowId);
    }

    public void openFlowEditor(String serverId, ClientServerView server, String flowId) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(actualServerId);
        FlowGraph graph = flowStore.getFromDraft(actualServerId, flowId);
        if (graph != null) {
            client.getHost().setScreen(new FlowEditorScreen(graph, actualServerId, ScreenManager.getInstance().getCurrentScreen()));
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
        client.getHost().setScreen(new FlowEditorScreen(newGraph, actualServerId, ScreenManager.getInstance().getCurrentScreen()));
    }

    public void openGuiDesigner(String serverId, ClientServerView server) {
        openGuiDesigner(serverId, server, "main");
    }

    public void openGuiDesigner(String serverId, ClientServerView server, String guiId) {
        openGuiDesigner(serverId, server, guiId, null);
    }

    public void openGuiDesigner(String serverId, ClientServerView server, String guiId, Object parentOverride) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        GuiDefinition gui = guiStore.get(actualServerId, guiId);
        Object parent = resolveDesignerParent(parentOverride);
        if (gui == null) {
            guiStore.setPendingParent(actualServerId, guiId, parent);
            connectionManager.ensureFlowClient(actualServerId).requestGui(guiId, false);
            return;
        }
        client.getHost().setScreen(new GuiDesignerScreen(gui, actualServerId, parent));
    }

    public void openScoreboardDesigner(String serverId, ClientServerView server) {
        openScoreboardDesigner(serverId, server, "main");
    }

    public void openScoreboardDesigner(String serverId, ClientServerView server, String scoreboardId) {
        openScoreboardDesigner(serverId, server, scoreboardId, null);
    }

    public void openScoreboardDesigner(String serverId, ClientServerView server, String scoreboardId, Object parentOverride) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        ScoreboardDefinition scoreboard = scoreboardStore.get(actualServerId, scoreboardId);
        Object parent = resolveDesignerParent(parentOverride);
        if (scoreboard == null) {
            scoreboardStore.setPendingParent(actualServerId, scoreboardId, parent);
            connectionManager.ensureFlowClient(actualServerId).requestScoreboard(scoreboardId, false);
            return;
        }
        client.getHost().setScreen(new ScoreboardDesignerScreen(scoreboard, actualServerId, parent));
    }

    public void openTabDesigner(String serverId, ClientServerView server) {
        openTabDesigner(serverId, server, "main");
    }

    public void openTabDesigner(String serverId, ClientServerView server, String tabId) {
        openTabDesigner(serverId, server, tabId, null);
    }

    public void openTabDesigner(String serverId, ClientServerView server, String tabId, Object parentOverride) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        TabDefinition tab = tabStore.get(actualServerId, tabId);
        Object parent = resolveDesignerParent(parentOverride);
        if (tab == null) {
            tabStore.setPendingParent(actualServerId, tabId, parent);
            connectionManager.ensureFlowClient(actualServerId).requestTab(tabId, false);
            return;
        }
        client.getHost().setScreen(new TabDesignerScreen(tab, actualServerId, parent));
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

    public void saveFlow(String serverId, FlowGraph graph) {
        flowStore.putInCache(serverId, graph);
        CustomContentDefinition derivedContent = CustomContentGraphAdapter.toDefinition(graph);
        if (derivedContent != null) {
            customContentStore.putInCache(serverId, derivedContent);
            customContentStore.putNameIfAbsent(serverId, derivedContent.getId(), derivedContent.getDisplayName());
        }
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendFlowSave(graph);
            if (derivedContent != null) {
                flowClient.sendCustomContentSave(derivedContent);
            } else {
                for (FlowGraph contentGraph : getDefaultContentGraphs(serverId, graph)) {
                    CustomContentDefinition content = CustomContentGraphAdapter.toDefinition(contentGraph);
                    flowClient.sendFlowSave(contentGraph);
                    if (content != null) {
                        flowClient.sendCustomContentSave(content);
                    }
                }
                for (CustomContentDefinition content : customContentStore.getForServer(serverId).values()) {
                    if (graph != null && graph.getId() != null && graph.getId().equals(content.getFlowId())) {
                        flowClient.sendCustomContentSave(content);
                    }
                }
            }
        }
    }

    private List<FlowGraph> getDefaultContentGraphs(String serverId, FlowGraph graph) {
        if (graph == null || graph.getId() == null || graph.isFunction() || CustomContentGraphAdapter.isContentGraph(graph)) {
            return List.of();
        }
        List<FlowGraph> graphs = new ArrayList<>();
        for (String suffix : List.of("_default_item", "_default_block", "_default_armor")) {
            FlowGraph contentGraph = flowStore.get(serverId, graph.getId() + suffix);
            if (contentGraph != null && CustomContentGraphAdapter.isContentGraph(contentGraph)) {
                graphs.add(contentGraph);
            }
        }
        return graphs;
    }

    public void cacheFlow(String serverId, FlowGraph graph) {
        flowStore.cache(serverId, graph);
        if (graph != null && graph.getId() != null) {
            CustomContentDefinition derivedContent = CustomContentGraphAdapter.toDefinition(graph);
            if (derivedContent != null) {
                customContentStore.cache(serverId, derivedContent);
                customContentStore.putNameIfAbsent(serverId, derivedContent.getId(), derivedContent.getDisplayName());
                upsertFlowManagerEntry(serverId, graph.getId(), ReSyncResourceType.CUSTOM_CONTENT);
                return;
            }
            upsertFlowManagerEntry(serverId, graph.getId(), ReSyncResourceType.FLOW);
        }
    }

    public void saveGui(String serverId, GuiDefinition gui) {
        if (serverId == null || gui == null || gui.getId() == null) {
            return;
        }
        guiStore.putInCache(serverId, gui);
        guiStore.putNameIfAbsent(serverId, gui.getId(), gui.getTitle() != null ? gui.getTitle() : gui.getId());
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendGuiSave(gui);
        }
    }

    public void cacheGui(String serverId, GuiDefinition gui) {
        guiStore.cache(serverId, gui);
        if (gui != null && gui.getId() != null) {
            upsertFlowManagerEntry(serverId, gui.getId(), ReSyncResourceType.GUI);
        }
    }

    public void markFlowSaved(String serverId, String flowId) {
        flowStore.markSaved(serverId, flowId);
        refreshFlowManagerScreen(serverId);
    }

    public void markGuiSaved(String serverId, String guiId) {
        guiStore.markSaved(serverId, guiId);
        refreshFlowManagerScreen(serverId);
    }

    public void saveScoreboard(String serverId, ScoreboardDefinition scoreboard) {
        if (serverId == null || scoreboard == null || scoreboard.getId() == null) {
            return;
        }
        scoreboardStore.putInCache(serverId, scoreboard);
        scoreboardStore.putNameIfAbsent(serverId, scoreboard.getId(), scoreboard.getTitle() != null ? scoreboard.getTitle() : scoreboard.getId());
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendScoreboardSave(scoreboard);
        }
    }

    public void cacheScoreboard(String serverId, ScoreboardDefinition scoreboard) {
        scoreboardStore.cache(serverId, scoreboard);
        if (scoreboard != null && scoreboard.getId() != null) {
            upsertFlowManagerEntry(serverId, scoreboard.getId(), ReSyncResourceType.SCOREBOARD);
        }
    }

    public void markScoreboardSaved(String serverId, String scoreboardId) {
        scoreboardStore.markSaved(serverId, scoreboardId);
        refreshFlowManagerScreen(serverId);
    }

    public void saveTab(String serverId, TabDefinition tab) {
        if (serverId == null || tab == null || tab.getId() == null) {
            return;
        }
        tabStore.putInCache(serverId, tab);
        tabStore.putNameIfAbsent(serverId, tab.getId(), tab.getId());
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendTabSave(tab);
        }
    }

    public void cacheTab(String serverId, TabDefinition tab) {
        tabStore.cache(serverId, tab);
        if (tab != null && tab.getId() != null) {
            upsertFlowManagerEntry(serverId, tab.getId(), ReSyncResourceType.TAB);
        }
    }

    public void markTabSaved(String serverId, String tabId) {
        tabStore.markSaved(serverId, tabId);
        refreshFlowManagerScreen(serverId);
    }

    public void saveCustomContent(String serverId, CustomContentDefinition content) {
        if (serverId == null || content == null || content.getId() == null) {
            return;
        }
        customContentStore.putInCache(serverId, content);
        customContentStore.putNameIfAbsent(serverId, content.getId(), content.getDisplayName() != null ? content.getDisplayName() : content.getId());
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendCustomContentSave(content);
        }
    }

    public void cacheCustomContent(String serverId, CustomContentDefinition content) {
        customContentStore.cache(serverId, content);
        if (content != null && content.getId() != null) {
            upsertFlowManagerEntry(serverId, content.getId(), ReSyncResourceType.CUSTOM_CONTENT);
        }
        refreshFlowManagerScreen(serverId);
    }

    public void markCustomContentSaved(String serverId, String contentId) {
        customContentStore.markSaved(serverId, contentId);
        refreshFlowManagerScreen(serverId);
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

    public Map<String, TabDefinition> getTabsForServer(String serverId) {
        return tabStore.getForServer(serverId);
    }

    public Map<String, CustomContentDefinition> getCustomContentForServer(String serverId) {
        return customContentStore.getForServer(serverId);
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
        if (!function && !CustomContentGraphAdapter.isContentGraph(graph)) {
            createDefaultContentFlows(serverId, graph.getId());
        }
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
        return graph;
    }

    private void createDefaultContentFlows(String serverId, String flowId) {
        createDefaultContentFlow(serverId, flowId + "_default_item", "item", "Default Item");
        createDefaultContentFlow(serverId, flowId + "_default_block", "block", "Default Block");
        createDefaultContentFlow(serverId, flowId + "_default_armor", "armor", "Default Armor");
    }

    private void createDefaultContentFlow(String serverId, String contentFlowId, String type, String name) {
        if (flowStore.get(serverId, contentFlowId) != null) {
            return;
        }
        createContentFlow(serverId, contentFlowId, type, name);
    }

    public GuiDefinition createGui(String serverId, String id) {
        GuiDefinition gui = createDefaultGui(id);
        guiStore.putInDraft(serverId, gui);
        guiStore.putNameIfAbsent(serverId, id, gui.getTitle());
        return gui;
    }

    public ScoreboardDefinition createScoreboard(String serverId, String id) {
        ScoreboardDefinition scoreboard = createDefaultScoreboard(id);
        scoreboardStore.putInDraft(serverId, scoreboard);
        scoreboardStore.putNameIfAbsent(serverId, id, scoreboard.getTitle());
        return scoreboard;
    }

    public TabDefinition createTab(String serverId, String id) {
        TabDefinition tab = createDefaultTab(id);
        tabStore.putInDraft(serverId, tab);
        tabStore.putNameIfAbsent(serverId, id, tab.getId());
        return tab;
    }

    public void deleteFlow(String serverId, String flowId) {
        flowStore.remove(serverId, flowId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendFlowDelete(flowId);
        }
    }

    public void deleteGui(String serverId, String guiId) {
        guiStore.remove(serverId, guiId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendGuiDelete(guiId);
        }
    }

    public void deleteScoreboard(String serverId, String scoreboardId) {
        scoreboardStore.remove(serverId, scoreboardId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendScoreboardDelete(scoreboardId);
        }
    }

    public void deleteTab(String serverId, String tabId) {
        tabStore.remove(serverId, tabId);
        ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
        if (flowClient != null) {
            flowClient.sendTabDelete(tabId);
        }
    }

    public void deleteCustomContent(String serverId, String contentId) {
        customContentStore.remove(serverId, contentId);
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

    @SuppressWarnings("unchecked")
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
        refreshFlowManagerScreen(serverId);
        return true;
    }

    public void refreshFlowsFromServer(String serverId) {
        flowStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestFlowList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshGuisFromServer(String serverId) {
        guiStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestGuiList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshScoreboardsFromServer(String serverId) {
        scoreboardStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestScoreboardList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshTabsFromServer(String serverId) {
        tabStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestTabList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshCustomContentFromServer(String serverId) {
        customContentStore.clearForServer(serverId);
        connectionManager.ensureFlowClient(serverId, true).requestCustomContentList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshWorldsFromServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId, true);
        flowClient.requestWorldSnapshot();
        flowClient.requestPlayerTrackingSnapshot();
        refreshFlowManagerScreen(serverId);
    }

    public void applyServerFlowList(String serverId, List<String> flowIds) {
        flowStore.applyServerList(serverId, flowIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (flowIds != null) {
            for (String flowId : flowIds) {
                flowClient.requestFlow(flowId, false);
            }
        }
        refreshFlowManagerScreen(serverId);
    }

    public void applyServerGuiList(String serverId, List<String> guiIds) {
        guiStore.applyServerList(serverId, guiIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (guiIds != null) {
            for (String guiId : guiIds) {
                flowClient.requestGui(guiId, false);
            }
        }
        refreshFlowManagerScreen(serverId);
    }

    public void applyServerScoreboardList(String serverId, List<String> scoreboardIds) {
        scoreboardStore.applyServerList(serverId, scoreboardIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (scoreboardIds != null) {
            for (String scoreboardId : scoreboardIds) {
                flowClient.requestScoreboard(scoreboardId, false);
            }
        }
        refreshFlowManagerScreen(serverId);
    }

    public void applyServerTabList(String serverId, List<String> tabIds) {
        tabStore.applyServerList(serverId, tabIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (tabIds != null) {
            for (String tabId : tabIds) {
                flowClient.requestTab(tabId, false);
            }
        }
        refreshFlowManagerScreen(serverId);
    }

    public void applyServerCustomContentList(String serverId, List<String> contentIds) {
        customContentStore.applyServerList(serverId, contentIds);
        ReSyncFlowClient flowClient = connectionManager.ensureFlowClient(serverId);
        if (contentIds != null) {
            for (String contentId : contentIds) {
                flowClient.requestCustomContent(contentId, false);
            }
        }
        refreshFlowManagerScreen(serverId);
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
            bindings.add(new TriggerBinding(flowId + ":command", flowId, TriggerType.COMMAND, context));
            ensureCommandStartNode(serverId, flowId);
        }
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
            clearOverlayState();
            redxax.oxy.remotely.flow.ui.GuiEditOverlayState.clear();
            return;
        }
        overlayEditable = true;
        overlayServerId = serverId;
        overlayGuiId = guiId;
        overlayFlowId = flowId;
        overlayRevision.incrementAndGet();
        redxax.oxy.remotely.flow.ui.GuiEditOverlayState.update(serverId, guiId, flowId, true);
        if (guiId != null && !guiId.isBlank()) {
            ReSyncFlowClient flowClient = connectionManager.getFlowClient(serverId);
            if (flowClient != null) {
                flowClient.requestGui(guiId, false);
            }
        }
    }

    public boolean isOverlayEditable() { return overlayEditable; }
    public String getOverlayServerId() { return overlayServerId; }
    public String getOverlayGuiId() { return overlayGuiId; }
    public String getOverlayFlowId() { return overlayFlowId; }
    public int getOverlayRevision() { return overlayRevision.get(); }

    public void clearOverlayState() {
        overlayEditable = false;
        overlayServerId = null;
        overlayGuiId = null;
        overlayFlowId = null;
        overlayRevision.incrementAndGet();
    }

    public void handleGuiDataReceived(String serverId, GuiDefinition gui) {
        if (gui == null || gui.getId() == null || gui.getId().isBlank()) {
            return;
        }
        Object parent = guiStore.removePendingParent(serverId, gui.getId());
        if (parent != null) {
            client.getHost().setScreen(new GuiDesignerScreen(gui, serverId, parent));
        }
    }

    public void handleScoreboardDataReceived(String serverId, ScoreboardDefinition scoreboard) {
        if (scoreboard == null || scoreboard.getId() == null || scoreboard.getId().isBlank()) {
            return;
        }
        Object parent = scoreboardStore.removePendingParent(serverId, scoreboard.getId());
        if (parent != null) {
            client.getHost().setScreen(new ScoreboardDesignerScreen(scoreboard, serverId, parent));
        }
    }

    public void handleTabDataReceived(String serverId, TabDefinition tab) {
        if (tab == null || tab.getId() == null || tab.getId().isBlank()) {
            return;
        }
        Object parent = tabStore.removePendingParent(serverId, tab.getId());
        if (parent != null) {
            client.getHost().setScreen(new TabDesignerScreen(tab, serverId, parent));
        }
    }

    void refreshFlowManagerScreen(String serverId) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.refresh();
            }
        });
    }

    private void upsertFlowManagerEntry(String serverId, String resourceId, ReSyncResourceType type) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                switch (type) {
                    case FLOW -> screen.upsertFlowEntry(resourceId);
                    case GUI -> screen.upsertGuiEntry(resourceId);
                    case SCOREBOARD -> screen.upsertScoreboardEntry(resourceId);
                    case TAB -> screen.upsertTabEntry(resourceId);
                    case CUSTOM_CONTENT -> screen.upsertCustomContentEntry(resourceId);
                }
            }
        });
    }

    void upsertFlowManagerWorldEntry(String serverId, String worldName) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.upsertWorldEntry(worldName);
            }
        });
    }

    private FlowGraph createDefaultFlow() {
        return createDefaultFlow(false);
    }

    private FlowGraph createDefaultFlow(boolean function) {
        return createDefaultFlow(function, FLOW_TEMPLATES.getFirst());
    }

    private FlowGraph createDefaultFlow(boolean function, String templateName) {
        FlowGraph graph = new FlowGraph();
        graph.setFunction(function);
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
        if (graph == null || graph.getNodes() == null) {
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

    @SuppressWarnings("unchecked")
    private WorldMapScreen createWorldMapScreen(Screen parentScreen, Instance instance, String worldName, Consumer<restudio.rebase.minecraft.MinecraftPlayerLocation> onPlayerSelected) {
        try {
            return (WorldMapScreen) WorldMapScreen.class
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
