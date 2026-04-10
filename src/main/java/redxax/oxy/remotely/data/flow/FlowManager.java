package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
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
import restudio.rebase.Rebase;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rebase.ui.worldmap.WorldMapScreen;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FlowManager {
    private static final String RESYNC_API_KEY_KEY = "resyncApiKey";
    private static final String RESYNC_PORT_KEY = "resyncPort";
    private static FlowManager INSTANCE;
    private final RemotelyClient client;
    private final ReStudioApiClient apiClient;
    private final Map<String, ReSyncFlowClient> flowClients = new ConcurrentHashMap<>();
    private final Map<String, ReSyncConnectionProfile> flowProfiles = new ConcurrentHashMap<>();
    private final Map<String, GuiDefinition> guiCache = new ConcurrentHashMap<>();
    private final Map<String, GuiDefinition> draftGuis = new ConcurrentHashMap<>();
    private final Map<String, ScoreboardDefinition> scoreboardCache = new ConcurrentHashMap<>();
    private final Map<String, ScoreboardDefinition> draftScoreboards = new ConcurrentHashMap<>();
    private final Map<String, TabDefinition> tabCache = new ConcurrentHashMap<>();
    private final Map<String, TabDefinition> draftTabs = new ConcurrentHashMap<>();
    private final Map<String, FlowGraph> flowCache = new ConcurrentHashMap<>();
    private final Map<String, FlowGraph> draftFlows = new ConcurrentHashMap<>();
    private final Map<String, String> flowNames = new ConcurrentHashMap<>();
    private final Map<String, String> guiNames = new ConcurrentHashMap<>();
    private final Map<String, String> scoreboardNames = new ConcurrentHashMap<>();
    private final Map<String, String> tabNames = new ConcurrentHashMap<>();
    private final Map<String, Object> pendingGuiParents = new ConcurrentHashMap<>();
    private final Map<String, Object> pendingScoreboardParents = new ConcurrentHashMap<>();
    private final Map<String, Object> pendingTabParents = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<redxax.oxy.remotely.flow.data.TriggerBinding>> triggerBindings = new ConcurrentHashMap<>();
    private final Map<String, PlayerDossier> playerDossierCache = new ConcurrentHashMap<>();
    private final Map<String, WorldSnapshot> worldSnapshotCache = new ConcurrentHashMap<>();
    private final Map<String, WorldMapSnapshot> worldMapSnapshotCache = new ConcurrentHashMap<>();
    private final Map<String, WorldOperationResult> worldOperationCache = new ConcurrentHashMap<>();
    private final Map<String, String> pendingWorldMapRequests = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppressedWorldSuccessNotifications = new ConcurrentHashMap<>();
    private final java.util.Set<String> serverFlowIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> serverGuiIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> serverScoreboardIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> serverTabIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean overlayEditable;
    private volatile String overlayServerId;
    private volatile String overlayGuiId;
    private volatile String overlayFlowId;
    private final java.util.concurrent.atomic.AtomicInteger overlayRevision = new java.util.concurrent.atomic.AtomicInteger();
    private final Gson gson = new Gson();

    private record ReSyncConnectionProfile(String wsUrl, String apiKey) {
    }

    public FlowManager(RemotelyClient client, ReStudioApiClient apiClient) {
        this.client = client;
        this.apiClient = apiClient;
        INSTANCE = this;
    }

    public static FlowManager getInstance() {
        return INSTANCE;
    }

    public void openFlowManager(String serverId, ClientServerView server, String loaderHint) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        ReSyncConnectionProfile profile = resolveConnectionProfile(actualServerId, server);
        if (profile != null) {
            flowProfiles.put(actualServerId, profile);
        }
        client.getHost().setScreen(new FlowManagerScreen(actualServerId, server, loaderHint, ScreenManager.getInstance().getCurrentScreen()));
    }

    public void ensureFlowClientForStartup(String serverId, ClientServerView server, boolean showNotifications) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncConnectionProfile profile = resolveConnectionProfile(serverId, server);
        if (profile != null) {
            flowProfiles.put(serverId, profile);
        }
        ensureFlowClient(serverId, profile, showNotifications, true);
    }

    public boolean isFlowClientConnected(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        return flowClient != null && flowClient.isConnectedState();
    }

    public void requestInitialFlowData(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        refreshFlowsFromServer(serverId);
        refreshGuisFromServer(serverId);
        refreshScoreboardsFromServer(serverId);
        refreshTabsFromServer(serverId);
        refreshWorldsFromServer(serverId);
    }

    public void provisionReSyncForReStudioServer(String serverId, java.util.function.Consumer<Boolean> callback) {
        if (serverId == null || serverId.isBlank()) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        if (apiClient == null) {
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", "ReSync Isn't Installed/Enabled", Notification.Type.ERROR));
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        apiClient.provisionReSync(serverId).thenAccept(response -> {
            boolean ok = response != null && response.success;
            ScreenManager.getInstance().execute(() -> {
                if (!ok) {
                    String message = response != null && response.message != null && !response.message.isBlank() ? response.message : "Provision Failed";
                    String normalized = normalizeReSyncNotificationMessage(message);
                    Notification.Type type = "ReSync Connection Timed Out".equals(normalized) ? Notification.Type.WARN : Notification.Type.ERROR;
                    new Notification("ReSync", normalized, type);
                }
            });
            if (callback != null) {
                callback.accept(ok);
            }
        }).exceptionally(error -> {
            ScreenManager.getInstance().execute(() -> {
                String reason = error != null && error.getMessage() != null ? error.getMessage() : "ProvisionFailed";
                String normalized = normalizeReSyncNotificationMessage(reason);
                Notification.Type type = "ReSync Connection Timed Out".equals(normalized) ? Notification.Type.WARN : Notification.Type.ERROR;
                new Notification("ReSync", normalized, type);
            });
            if (callback != null) {
                callback.accept(false);
            }
            return null;
        });
    }

    public String getFlowAvailabilityIssue(String serverId, ClientServerView server) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        if (actualServerId == null || actualServerId.isBlank()) {
            return "ServerIdMissing";
        }
        if (server != null) {
            return null;
        }
        Instance instance = findInstanceByServerId(actualServerId);
        if (instance == null) {
            return "ServerNotFound";
        }
        BackendConfig backendConfig = instance.getBackendConfig();
        if (backendConfig != null && "RESTUDIO".equalsIgnoreCase(backendConfig.type)) {
            return null;
        }
        ReSyncConnectionProfile profile = resolveConnectionProfile(actualServerId, server);
        if (profile == null) {
            return "ReSyncNotConfigured";
        }
        if (profile.wsUrl() == null || profile.wsUrl().isBlank()) {
            return "ReSyncPortMissing";
        }
        if (profile.apiKey() == null || profile.apiKey().isBlank()) {
            return "ReSyncApiKeyMissing";
        }
        return null;
    }

    public Instance getInstanceByServerId(String serverId) {
        return findInstanceByServerId(serverId);
    }

    private ReSyncConnectionProfile resolveConnectionProfile(String serverId, ClientServerView server) {
        if (server != null) {
            return null;
        }
        Instance instance = findInstanceByServerId(serverId);
        if (instance == null || instance.getBackendConfig() == null || instance.getBackendConfig().credentials == null) {
            return tryReadReSyncConfigFromBackend(instance);
        }
        BackendConfig backendConfig = instance.getBackendConfig();
        if ("RESTUDIO".equalsIgnoreCase(backendConfig.type)) {
            return null;
        }
        Map<String, String> credentials = backendConfig.credentials;
        String port = safeText(credentials.get(RESYNC_PORT_KEY));
        String host;
        if ("LOCAL".equalsIgnoreCase(backendConfig.type)) {
            host = "127.0.0.1";
        } else {
            host = safeText(credentials.get("host"));
        }
        String apiKey = safeText(credentials.get(RESYNC_API_KEY_KEY));
        if (port.isBlank() || apiKey.isBlank()) {
            ReSyncConnectionProfile fsProfile = tryReadReSyncConfigFromBackend(instance);
            if (fsProfile != null) {
                return fsProfile;
            }
        }
        String wsUrl = null;
        if (!host.isBlank() && !port.isBlank()) {
            wsUrl = normalizeWsUrl(host + ":" + port);
        }
        if (apiKey.isBlank()) {
            return new ReSyncConnectionProfile(wsUrl, null);
        }
        return new ReSyncConnectionProfile(wsUrl, apiKey);
    }

    private ReSyncConnectionProfile tryReadReSyncConfigFromBackend(Instance instance) {
        if (instance == null || instance.getBackendConfig() == null) {
            return null;
        }
        try {
            ServerBackend backend = instance.getBackend();
            if (backend == null) {
                return null;
            }
            if (!backend.isConnected()) {
                backend.connect();
            }
            FileSystemProvider fs = backend.getFileSystem();
            if (fs == null) {
                return null;
            }
            Path configPath = Path.of(instance.getPath()).resolve("plugins").resolve("ReSync").resolve("config.properties");
            Boolean exists = fs.exists(configPath).get(5, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(exists)) {
                return null;
            }
            String content = fs.read(configPath).get(10, TimeUnit.SECONDS);
            if (content == null || content.isBlank()) {
                return null;
            }
            String port = null;
            String apiKey = null;
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                switch (key) {
                    case "port" -> port = value;
                    case "api-key" -> apiKey = value;
                }
            }
            String effectiveHost;
            if ("LOCAL".equalsIgnoreCase(instance.getBackendConfig().type)) {
                effectiveHost = "127.0.0.1";
            } else {
                Map<String, String> creds = instance.getBackendConfig().credentials;
                effectiveHost = creds != null ? safeText(creds.get("host")) : "";
            }
            if (effectiveHost.isBlank() || port == null || port.isBlank() || apiKey == null || apiKey.isBlank()) {
                return null;
            }
            String wsUrl = normalizeWsUrl(effectiveHost + ":" + port);
            return new ReSyncConnectionProfile(wsUrl, apiKey);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Instance findInstanceByServerId(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        try {
            InstanceManager instanceManager = Rebase.get().getInstanceManager();
            List<Instance> instances = new ArrayList<>(instanceManager.getLocalInstances());
            for (var host : instanceManager.getRemoteHosts()) {
                instances.addAll(instanceManager.getRemoteInstances(host));
            }
            for (Instance instance : instances) {
                if (instance == null || instance.getInstanceId() == null) {
                    continue;
                }
                if (serverId.equalsIgnoreCase(instance.getInstanceId())) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String normalizeWsUrl(String value) {
        String raw = safeText(value).trim();
        if (raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("ws://") || raw.startsWith("wss://")) {
            return raw;
        }
        if (raw.startsWith("http://")) {
            return "ws://" + raw.substring("http://".length());
        }
        if (raw.startsWith("https://")) {
            return "wss://" + raw.substring("https://".length());
        }
        return "ws://" + raw;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    public void openFlowEditor(String serverId, ClientServerView server) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        String flowId = getOrCreateDefaultFlowId(actualServerId);
        openFlowEditor(actualServerId, server, flowId);
    }

    public void openFlowEditor(String serverId, ClientServerView server, String flowId) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        ReSyncFlowClient flowClient = ensureFlowClient(actualServerId);

        String key = actualServerId + ":" + flowId;
        FlowGraph graph = draftFlows.get(key);
        if (graph != null) {
            client.getHost().setScreen(new FlowEditorScreen(graph, actualServerId, ScreenManager.getInstance().getCurrentScreen()));
            return;
        }

        if (serverFlowIds.contains(key)) {
            flowClient.requestFlow(flowId, true);
            return;
        }

        FlowGraph newGraph = createDefaultFlow();
        if (flowId != null) {
            newGraph.setId(flowId);
        }
        String actualFlowId = newGraph.getId();
        String actualKey = actualServerId + ":" + actualFlowId;
        draftFlows.put(actualKey, newGraph);
        flowNames.putIfAbsent(actualKey, actualFlowId);
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
        String key = actualServerId + ":" + guiId;
        GuiDefinition gui = guiCache.get(key);
        if (gui == null) {
            gui = draftGuis.get(key);
        }
        Object parent = resolveDesignerParent(parentOverride);
        if (gui == null) {
            pendingGuiParents.put(key, parent);
            ReSyncFlowClient flowClient = ensureFlowClient(actualServerId);
            flowClient.requestGui(guiId, false);
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
        String key = actualServerId + ":" + scoreboardId;
        ScoreboardDefinition scoreboard = scoreboardCache.get(key);
        if (scoreboard == null) {
            scoreboard = draftScoreboards.get(key);
        }
        Object parent = resolveDesignerParent(parentOverride);
        if (scoreboard == null) {
            pendingScoreboardParents.put(key, parent);
            ReSyncFlowClient flowClient = ensureFlowClient(actualServerId);
            flowClient.requestScoreboard(scoreboardId, false);
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
        String key = actualServerId + ":" + tabId;
        TabDefinition tab = tabCache.get(key);
        if (tab == null) {
            tab = draftTabs.get(key);
        }
        Object parent = resolveDesignerParent(parentOverride);
        if (tab == null) {
            pendingTabParents.put(key, parent);
            ReSyncFlowClient flowClient = ensureFlowClient(actualServerId);
            flowClient.requestTab(tabId, false);
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
        String key = serverId + ":" + graph.getId();
        flowCache.put(key, graph);

        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null) {
            flowClient.sendFlowSave(graph);
        }
    }

    public void cacheFlow(String serverId, FlowGraph graph) {
        if (graph == null || graph.getId() == null) {
            return;
        }
        String key = serverId + ":" + graph.getId();
        flowCache.put(key, graph);
        flowNames.putIfAbsent(key, graph.getId());
        serverFlowIds.add(key);
        draftFlows.remove(key);
        upsertFlowManagerFlowEntry(serverId, graph.getId());
    }

    public void closeServerConnection(String serverId) {
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null) {
            flowClient.shutdown();
            flowClients.remove(serverId);
        }
        flowProfiles.remove(serverId);
        if (redxax.oxy.remotely.flow.registry.NodeRegistry.getInstance() != null) {
            redxax.oxy.remotely.flow.registry.NodeRegistry.getInstance().clearServer(serverId);
        }
        clearServerCache(serverId);
    }

    private void clearServerCache(String serverId) {
        clearFlowCache(serverId);
        clearGuiCache(serverId);
        clearScoreboardCache(serverId);
        clearTabCache(serverId);
        clearPlayerDossierCache(serverId);
        clearWorldCache(serverId);
    }

    private void clearFlowCache(String serverId) {
        String prefix = serverId + ":";
        flowCache.keySet().removeIf(key -> key.startsWith(prefix));
        draftFlows.keySet().removeIf(key -> key.startsWith(prefix));
        serverFlowIds.removeIf(key -> key.startsWith(prefix));
        flowNames.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void clearGuiCache(String serverId) {
        String prefix = serverId + ":";
        guiCache.keySet().removeIf(key -> key.startsWith(prefix));
        draftGuis.keySet().removeIf(key -> key.startsWith(prefix));
        serverGuiIds.removeIf(key -> key.startsWith(prefix));
        guiNames.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void clearScoreboardCache(String serverId) {
        String prefix = serverId + ":";
        scoreboardCache.keySet().removeIf(key -> key.startsWith(prefix));
        draftScoreboards.keySet().removeIf(key -> key.startsWith(prefix));
        serverScoreboardIds.removeIf(key -> key.startsWith(prefix));
        scoreboardNames.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void clearTabCache(String serverId) {
        String prefix = serverId + ":";
        tabCache.keySet().removeIf(key -> key.startsWith(prefix));
        draftTabs.keySet().removeIf(key -> key.startsWith(prefix));
        serverTabIds.removeIf(key -> key.startsWith(prefix));
        tabNames.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void clearPlayerDossierCache(String serverId) {
        String prefix = serverId + ":";
        playerDossierCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void clearWorldCache(String serverId) {
        String prefix = serverId + ":";
        worldMapSnapshotCache.keySet().removeIf(key -> key.startsWith(prefix));
        pendingWorldMapRequests.keySet().removeIf(key -> key.startsWith(prefix));
        worldSnapshotCache.remove(serverId);
    }

    private void refreshFlowManagerScreen(String serverId) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.refresh();
            }
        });
    }

    private void upsertFlowManagerFlowEntry(String serverId, String flowId) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.upsertFlowEntry(flowId);
            }
        });
    }

    private void upsertFlowManagerGuiEntry(String serverId, String guiId) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.upsertGuiEntry(guiId);
            }
        });
    }

    private void upsertFlowManagerScoreboardEntry(String serverId, String scoreboardId) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.upsertScoreboardEntry(scoreboardId);
            }
        });
    }

    private void upsertFlowManagerTabEntry(String serverId, String tabId) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.upsertTabEntry(tabId);
            }
        });
    }

    private void upsertFlowManagerWorldEntry(String serverId, String worldName) {
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.upsertWorldEntry(worldName);
            }
        });
    }

    private FlowGraph createDefaultFlow() {
        return new FlowGraph();
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

    public Map<String, FlowGraph> getFlowsForServer(String serverId) {
        Map<String, FlowGraph> flows = new java.util.HashMap<>();
        String prefix = serverId + ":";
        for (var entry : flowCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String flowId = entry.getKey().substring(prefix.length());
                flows.put(flowId, entry.getValue());
            }
        }
        for (var entry : draftFlows.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String flowId = entry.getKey().substring(prefix.length());
                flows.putIfAbsent(flowId, entry.getValue());
            }
        }
        return flows;
    }

    public Map<String, GuiDefinition> getGuisForServer(String serverId) {
        Map<String, GuiDefinition> guis = new java.util.HashMap<>();
        String prefix = serverId + ":";
        for (var entry : guiCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String guiId = entry.getKey().substring(prefix.length());
                guis.put(guiId, entry.getValue());
            }
        }
        for (var entry : draftGuis.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String guiId = entry.getKey().substring(prefix.length());
                guis.putIfAbsent(guiId, entry.getValue());
            }
        }
        return guis;
    }

    public Map<String, ScoreboardDefinition> getScoreboardsForServer(String serverId) {
        Map<String, ScoreboardDefinition> scoreboards = new java.util.HashMap<>();
        String prefix = serverId + ":";
        for (var entry : scoreboardCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String scoreboardId = entry.getKey().substring(prefix.length());
                scoreboards.put(scoreboardId, entry.getValue());
            }
        }
        for (var entry : draftScoreboards.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String scoreboardId = entry.getKey().substring(prefix.length());
                scoreboards.putIfAbsent(scoreboardId, entry.getValue());
            }
        }
        return scoreboards;
    }

    public Map<String, TabDefinition> getTabsForServer(String serverId) {
        Map<String, TabDefinition> tabs = new java.util.HashMap<>();
        String prefix = serverId + ":";
        for (var entry : tabCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String tabId = entry.getKey().substring(prefix.length());
                tabs.put(tabId, entry.getValue());
            }
        }
        for (var entry : draftTabs.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String tabId = entry.getKey().substring(prefix.length());
                tabs.putIfAbsent(tabId, entry.getValue());
            }
        }
        return tabs;
    }

    public Map<String, WorldRegistryEntry> getWorldsForServer(String serverId) {
        Map<String, WorldRegistryEntry> worlds = new LinkedHashMap<>();
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        if (snapshot == null) {
            return worlds;
        }
        for (WorldRegistryEntry world : snapshot.getWorlds()) {
            if (world != null && world.getWorldName() != null) {
                worlds.put(world.getWorldName(), world);
            }
        }
        return worlds;
    }

    public List<WorldDashboardEntry> getWorldDashboardForServer(String serverId) {
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        return snapshot == null ? List.of() : new ArrayList<>(snapshot.getDashboard());
    }

    public List<WorldInventoryGroup> getWorldInventoryGroupsForServer(String serverId) {
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        return snapshot == null ? List.of() : new ArrayList<>(snapshot.getInventoryGroups());
    }

    public WorldInventoryGroup getWorldInventoryGroup(String serverId, String groupId) {
        if (serverId == null || groupId == null || groupId.isBlank()) {
            return null;
        }
        for (WorldInventoryGroup group : getWorldInventoryGroupsForServer(serverId)) {
            if (group != null && group.getGroupId() != null && group.getGroupId().equalsIgnoreCase(groupId)) {
                return group;
            }
        }
        return null;
    }

    public WorldRegistryEntry getWorld(String serverId, String worldName) {
        if (serverId == null || worldName == null) {
            return null;
        }
        return getWorldsForServer(serverId).get(worldName);
    }

    public WorldSnapshot getWorldSnapshot(String serverId) {
        return worldSnapshotCache.get(serverId);
    }

    public WorldOperationResult getLastWorldOperationResult(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        return worldOperationCache.get(serverId);
    }

    public WorldMapSnapshot getWorldMapSnapshot(String serverId, String worldName) {
        if (serverId == null || worldName == null) {
            return null;
        }
        return worldMapSnapshotCache.get(serverId + ":" + worldName.toLowerCase(Locale.ROOT));
    }

    public List<String> getOnlinePlayerNamesForServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return List.of();
        }
        String prefix = serverId + ":";
        Map<String, String> playerNames = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerDossier> entry : playerDossierCache.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            PlayerDossier dossier = entry.getValue();
            if (dossier == null || !dossier.isOnline()) {
                continue;
            }
            String playerName = dossier.getPlayerName();
            if (playerName == null || playerName.isBlank()) {
                continue;
            }
            playerNames.putIfAbsent(playerName.toLowerCase(Locale.ROOT), playerName);
        }
        List<String> values = new ArrayList<>(playerNames.values());
        values.sort(String.CASE_INSENSITIVE_ORDER);
        return values;
    }

    public String getFlowName(String serverId, String flowId) {
        return flowNames.getOrDefault(serverId + ":" + flowId, flowId);
    }

    public void setFlowName(String serverId, String flowId, String name) {
        flowNames.put(serverId + ":" + flowId, name);
    }

    public String getGuiName(String serverId, String guiId) {
        return guiNames.getOrDefault(serverId + ":" + guiId, guiId);
    }

    public void setGuiName(String serverId, String guiId, String name) {
        guiNames.put(serverId + ":" + guiId, name);
    }

    public String getScoreboardName(String serverId, String scoreboardId) {
        return scoreboardNames.getOrDefault(serverId + ":" + scoreboardId, scoreboardId);
    }

    public void setScoreboardName(String serverId, String scoreboardId, String name) {
        scoreboardNames.put(serverId + ":" + scoreboardId, name);
    }

    public String getTabName(String serverId, String tabId) {
        return tabNames.getOrDefault(serverId + ":" + tabId, tabId);
    }

    public void setTabName(String serverId, String tabId, String name) {
        tabNames.put(serverId + ":" + tabId, name);
    }

    public FlowGraph createFlow(String serverId) {
        return createFlow(serverId, null);
    }

    public FlowGraph createFlow(String serverId, String flowId) {
        FlowGraph graph = createDefaultFlow();
        if (flowId != null) {
            graph.setId(flowId);
        }
        String key = serverId + ":" + graph.getId();
        draftFlows.put(key, graph);
        flowNames.putIfAbsent(key, graph.getId());
        return graph;
    }

    public GuiDefinition createGui(String serverId, String id) {
        GuiDefinition gui = createDefaultGui(id);
        String key = serverId + ":" + id;
        draftGuis.put(key, gui);
        guiNames.putIfAbsent(key, gui.getTitle());
        return gui;
    }

    public ScoreboardDefinition createScoreboard(String serverId, String id) {
        ScoreboardDefinition scoreboard = createDefaultScoreboard(id);
        String key = serverId + ":" + id;
        draftScoreboards.put(key, scoreboard);
        scoreboardNames.putIfAbsent(key, scoreboard.getTitle());
        return scoreboard;
    }

    public TabDefinition createTab(String serverId, String id) {
        TabDefinition tab = createDefaultTab(id);
        String key = serverId + ":" + id;
        draftTabs.put(key, tab);
        tabNames.putIfAbsent(key, tab.getId());
        return tab;
    }

    public void deleteFlow(String serverId, String flowId) {
        flowCache.remove(serverId + ":" + flowId);
        draftFlows.remove(serverId + ":" + flowId);
        serverFlowIds.remove(serverId + ":" + flowId);
        flowNames.remove(serverId + ":" + flowId);

        ReSyncFlowClient client = flowClients.get(serverId);
        if (client != null) {
            client.sendFlowDelete(flowId);
        }
    }

    public void deleteGui(String serverId, String guiId) {
        String key = serverId + ":" + guiId;
        guiCache.remove(key);
        draftGuis.remove(key);
        serverGuiIds.remove(key);
        guiNames.remove(key);

        ReSyncFlowClient client = flowClients.get(serverId);
        if (client != null) {
            client.sendGuiDelete(guiId);
        }
    }

    public void deleteScoreboard(String serverId, String scoreboardId) {
        String key = serverId + ":" + scoreboardId;
        scoreboardCache.remove(key);
        draftScoreboards.remove(key);
        serverScoreboardIds.remove(key);
        scoreboardNames.remove(key);

        ReSyncFlowClient client = flowClients.get(serverId);
        if (client != null) {
            client.sendScoreboardDelete(scoreboardId);
        }
    }

    public void deleteTab(String serverId, String tabId) {
        String key = serverId + ":" + tabId;
        tabCache.remove(key);
        draftTabs.remove(key);
        serverTabIds.remove(key);
        tabNames.remove(key);

        ReSyncFlowClient client = flowClients.get(serverId);
        if (client != null) {
            client.sendTabDelete(tabId);
        }
    }

    public boolean renameFlow(String serverId, String flowId, String newFlowId) {
        if (serverId == null || flowId == null || newFlowId == null) {
            return false;
        }
        String trimmedId = newFlowId.trim();
        if (trimmedId.isEmpty()) {
            return false;
        }
        String oldKey = serverId + ":" + flowId;
        String newKey = serverId + ":" + trimmedId;
        if (oldKey.equals(newKey)) {
            return false;
        }
        if (flowCache.containsKey(newKey) || draftFlows.containsKey(newKey) || serverFlowIds.contains(newKey)) {
            return false;
        }

        FlowGraph graph = flowCache.get(oldKey);
        boolean wasDraft = false;
        if (graph == null) {
            graph = draftFlows.get(oldKey);
            wasDraft = true;
        }
        if (graph == null) {
            return false;
        }

        boolean wasServer = serverFlowIds.contains(oldKey);
        graph.setId(trimmedId);

        flowCache.remove(oldKey);
        draftFlows.remove(oldKey);

        if (wasDraft && !wasServer) {
            draftFlows.put(newKey, graph);
        } else {
            flowCache.put(newKey, graph);
        }

        serverFlowIds.remove(oldKey);
        if (wasServer) {
            serverFlowIds.add(newKey);
        }

        String displayName = flowNames.remove(oldKey);
        if (displayName == null || displayName.isBlank() || displayName.equals(flowId)) {
            displayName = trimmedId;
        }
        flowNames.put(newKey, displayName);

        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.sendFlowSave(graph);
        if (wasServer) {
            client.sendFlowDelete(flowId);
        }

        refreshFlowManagerScreen(serverId);
        return true;
    }

    public boolean renameGui(String serverId, String guiId, String newGuiId) {
        if (serverId == null || guiId == null || newGuiId == null) {
            return false;
        }
        String trimmedId = newGuiId.trim();
        if (trimmedId.isEmpty()) {
            return false;
        }
        String oldKey = serverId + ":" + guiId;
        String newKey = serverId + ":" + trimmedId;
        if (oldKey.equals(newKey)) {
            return false;
        }
        if (guiCache.containsKey(newKey) || draftGuis.containsKey(newKey) || serverGuiIds.contains(newKey)) {
            return false;
        }

        GuiDefinition gui = guiCache.get(oldKey);
        boolean wasDraft = false;
        if (gui == null) {
            gui = draftGuis.get(oldKey);
            wasDraft = true;
        }
        if (gui == null) {
            return false;
        }

        boolean wasServer = serverGuiIds.contains(oldKey);
        gui.setId(trimmedId);
        if (gui.getTitle() == null || gui.getTitle().isBlank() || gui.getTitle().equals(guiId)) {
            gui.setTitle(trimmedId);
        }

        guiCache.remove(oldKey);
        draftGuis.remove(oldKey);

        if (wasDraft && !wasServer) {
            draftGuis.put(newKey, gui);
        } else {
            guiCache.put(newKey, gui);
        }

        serverGuiIds.remove(oldKey);
        if (wasServer) {
            serverGuiIds.add(newKey);
        }

        String displayName = guiNames.remove(oldKey);
        if (displayName == null || displayName.isBlank() || displayName.equals(guiId)) {
            displayName = gui.getTitle() != null && !gui.getTitle().isBlank() ? gui.getTitle() : trimmedId;
        }
        guiNames.put(newKey, displayName);

        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.sendGuiSave(gui);
        if (wasServer) {
            client.sendGuiDelete(guiId);
        }

        refreshFlowManagerScreen(serverId);
        return true;
    }

    public boolean renameScoreboard(String serverId, String scoreboardId, String newScoreboardId) {
        if (serverId == null || scoreboardId == null || newScoreboardId == null) {
            return false;
        }
        String trimmedId = newScoreboardId.trim();
        if (trimmedId.isEmpty()) {
            return false;
        }
        String oldKey = serverId + ":" + scoreboardId;
        String newKey = serverId + ":" + trimmedId;
        if (oldKey.equals(newKey)) {
            return false;
        }
        if (scoreboardCache.containsKey(newKey) || draftScoreboards.containsKey(newKey) || serverScoreboardIds.contains(newKey)) {
            return false;
        }

        ScoreboardDefinition scoreboard = scoreboardCache.get(oldKey);
        boolean wasDraft = false;
        if (scoreboard == null) {
            scoreboard = draftScoreboards.get(oldKey);
            wasDraft = true;
        }
        if (scoreboard == null) {
            return false;
        }

        boolean wasServer = serverScoreboardIds.contains(oldKey);
        scoreboard.setId(trimmedId);
        if (scoreboard.getObjectiveId() == null || scoreboard.getObjectiveId().isBlank() || scoreboard.getObjectiveId().equals(scoreboardId)) {
            scoreboard.setObjectiveId(trimmedId);
        }
        if (scoreboard.getTitle() == null || scoreboard.getTitle().isBlank() || scoreboard.getTitle().equals(scoreboardId)) {
            scoreboard.setTitle(trimmedId);
        }

        scoreboardCache.remove(oldKey);
        draftScoreboards.remove(oldKey);

        if (wasDraft && !wasServer) {
            draftScoreboards.put(newKey, scoreboard);
        } else {
            scoreboardCache.put(newKey, scoreboard);
        }

        serverScoreboardIds.remove(oldKey);
        if (wasServer) {
            serverScoreboardIds.add(newKey);
        }

        String displayName = scoreboardNames.remove(oldKey);
        if (displayName == null || displayName.isBlank() || displayName.equals(scoreboardId)) {
            displayName = scoreboard.getTitle() != null && !scoreboard.getTitle().isBlank() ? scoreboard.getTitle() : trimmedId;
        }
        scoreboardNames.put(newKey, displayName);

        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.sendScoreboardSave(scoreboard);
        if (wasServer) {
            client.sendScoreboardDelete(scoreboardId);
        }

        refreshFlowManagerScreen(serverId);
        return true;
    }

    public boolean renameTab(String serverId, String tabId, String newTabId) {
        if (serverId == null || tabId == null || newTabId == null) {
            return false;
        }
        String trimmedId = newTabId.trim();
        if (trimmedId.isEmpty()) {
            return false;
        }
        String oldKey = serverId + ":" + tabId;
        String newKey = serverId + ":" + trimmedId;
        if (oldKey.equals(newKey)) {
            return false;
        }
        if (tabCache.containsKey(newKey) || draftTabs.containsKey(newKey) || serverTabIds.contains(newKey)) {
            return false;
        }

        TabDefinition tab = tabCache.get(oldKey);
        boolean wasDraft = false;
        if (tab == null) {
            tab = draftTabs.get(oldKey);
            wasDraft = true;
        }
        if (tab == null) {
            return false;
        }

        boolean wasServer = serverTabIds.contains(oldKey);
        tab.setId(trimmedId);

        tabCache.remove(oldKey);
        draftTabs.remove(oldKey);

        if (wasDraft && !wasServer) {
            draftTabs.put(newKey, tab);
        } else {
            tabCache.put(newKey, tab);
        }

        serverTabIds.remove(oldKey);
        if (wasServer) {
            serverTabIds.add(newKey);
        }

        String displayName = tabNames.remove(oldKey);
        if (displayName == null || displayName.isBlank() || displayName.equals(tabId)) {
            displayName = trimmedId;
        }
        tabNames.put(newKey, displayName);

        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.sendTabSave(tab);
        if (wasServer) {
            client.sendTabDelete(tabId);
        }

        refreshFlowManagerScreen(serverId);
        return true;
    }

    public java.util.List<redxax.oxy.remotely.flow.data.TriggerBinding> getBindings(String serverId) {
        return triggerBindings.computeIfAbsent(serverId, id -> new java.util.ArrayList<>());
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
        java.util.List<TriggerBinding> bindings = getBindings(serverId);
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

    private void ensureCommandStartNode(String serverId, String flowId) {
        String key = serverId + ":" + flowId;
        FlowGraph graph = draftFlows.get(key);
        if (graph == null) {
            graph = flowCache.get(key);
        }
        if (graph == null || graph.getNodes() == null) {
            return;
        }
        boolean hasCommandNode = graph.getNodes().values().stream().anyMatch(node ->
            node != null && ("event:resync_command".equals(node.getType()) || "event:command".equals(node.getType()))
        );
        if (hasCommandNode) {
            return;
        }
        graph.getNodes().put(UUID.randomUUID().toString(), new FlowNode("event:resync_command", 120, 120, new HashMap<>()));
        saveFlow(serverId, graph);
    }

    public void markFlowSaved(String serverId, String flowId) {
        if (serverId == null || flowId == null) {
            return;
        }
        String key = serverId + ":" + flowId;
        serverFlowIds.add(key);
        FlowGraph draft = draftFlows.remove(key);
        if (draft != null) {
            flowCache.put(key, draft);
        }
        refreshFlowManagerScreen(serverId);
    }

    public void saveGui(String serverId, GuiDefinition gui) {
        if (serverId == null || gui == null || gui.getId() == null) {
            return;
        }
        String key = serverId + ":" + gui.getId();
        guiCache.put(key, gui);
        guiNames.putIfAbsent(key, gui.getTitle() != null ? gui.getTitle() : gui.getId());

        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null) {
            flowClient.sendGuiSave(gui);
        }
    }

    public void saveScoreboard(String serverId, ScoreboardDefinition scoreboard) {
        if (serverId == null || scoreboard == null || scoreboard.getId() == null) {
            return;
        }
        String key = serverId + ":" + scoreboard.getId();
        scoreboardCache.put(key, scoreboard);
        scoreboardNames.putIfAbsent(key, scoreboard.getTitle() != null ? scoreboard.getTitle() : scoreboard.getId());

        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null) {
            flowClient.sendScoreboardSave(scoreboard);
        }
    }

    public void saveTab(String serverId, TabDefinition tab) {
        if (serverId == null || tab == null || tab.getId() == null) {
            return;
        }
        String key = serverId + ":" + tab.getId();
        tabCache.put(key, tab);
        tabNames.putIfAbsent(key, tab.getId());

        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null) {
            flowClient.sendTabSave(tab);
        }
    }

    public void cacheGui(String serverId, GuiDefinition gui) {
        if (gui == null || gui.getId() == null) {
            return;
        }
        String key = serverId + ":" + gui.getId();
        guiCache.put(key, gui);
        guiNames.putIfAbsent(key, gui.getTitle() != null ? gui.getTitle() : gui.getId());
        serverGuiIds.add(key);
        draftGuis.remove(key);
        upsertFlowManagerGuiEntry(serverId, gui.getId());
    }

    public void markGuiSaved(String serverId, String guiId) {
        if (serverId == null || guiId == null) {
            return;
        }
        String key = serverId + ":" + guiId;
        serverGuiIds.add(key);
        GuiDefinition draft = draftGuis.remove(key);
        if (draft != null) {
            guiCache.put(key, draft);
        }
        refreshFlowManagerScreen(serverId);
    }

    public void cacheScoreboard(String serverId, ScoreboardDefinition scoreboard) {
        if (scoreboard == null || scoreboard.getId() == null) {
            return;
        }
        String key = serverId + ":" + scoreboard.getId();
        scoreboardCache.put(key, scoreboard);
        scoreboardNames.putIfAbsent(key, scoreboard.getTitle() != null ? scoreboard.getTitle() : scoreboard.getId());
        serverScoreboardIds.add(key);
        draftScoreboards.remove(key);
        upsertFlowManagerScoreboardEntry(serverId, scoreboard.getId());
    }

    public void cacheTab(String serverId, TabDefinition tab) {
        if (tab == null || tab.getId() == null) {
            return;
        }
        String key = serverId + ":" + tab.getId();
        tabCache.put(key, tab);
        tabNames.putIfAbsent(key, tab.getId());
        serverTabIds.add(key);
        draftTabs.remove(key);
        upsertFlowManagerTabEntry(serverId, tab.getId());
    }

    public void markScoreboardSaved(String serverId, String scoreboardId) {
        if (serverId == null || scoreboardId == null) {
            return;
        }
        String key = serverId + ":" + scoreboardId;
        serverScoreboardIds.add(key);
        ScoreboardDefinition draft = draftScoreboards.remove(key);
        if (draft != null) {
            scoreboardCache.put(key, draft);
        }
        refreshFlowManagerScreen(serverId);
    }

    public void markTabSaved(String serverId, String tabId) {
        if (serverId == null || tabId == null) {
            return;
        }
        String key = serverId + ":" + tabId;
        serverTabIds.add(key);
        TabDefinition draft = draftTabs.remove(key);
        if (draft != null) {
            tabCache.put(key, draft);
        }
        refreshFlowManagerScreen(serverId);
    }

    public void refreshFlowsFromServer(String serverId) {
        clearFlowCache(serverId);
        ReSyncFlowClient client = ensureFlowClient(serverId, true);
        client.requestFlowList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshGuisFromServer(String serverId) {
        clearGuiCache(serverId);
        ReSyncFlowClient client = ensureFlowClient(serverId, true);
        client.requestGuiList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshScoreboardsFromServer(String serverId) {
        clearScoreboardCache(serverId);
        ReSyncFlowClient client = ensureFlowClient(serverId, true);
        client.requestScoreboardList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshTabsFromServer(String serverId) {
        clearTabCache(serverId);
        ReSyncFlowClient client = ensureFlowClient(serverId, true);
        client.requestTabList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshWorldsFromServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient client = ensureFlowClient(serverId, true);
        client.requestWorldSnapshot();
        client.requestPlayerTrackingSnapshot();
        refreshFlowManagerScreen(serverId);
    }

    public void applyServerFlowList(String serverId, java.util.List<String> flowIds) {
        clearFlowCache(serverId);
        String prefix = serverId + ":";
        if (flowIds != null) {
            for (String flowId : flowIds) {
                String key = prefix + flowId;
                serverFlowIds.add(key);
                flowNames.putIfAbsent(key, flowId);
            }
        }

        ReSyncFlowClient client = ensureFlowClient(serverId);
        if (flowIds != null) {
            for (String flowId : flowIds) {
                client.requestFlow(flowId, false);
            }
        }

        refreshFlowManagerScreen(serverId);
    }

    public void applyServerGuiList(String serverId, java.util.List<String> guiIds) {
        clearGuiCache(serverId);
        String prefix = serverId + ":";
        if (guiIds != null) {
            for (String guiId : guiIds) {
                String key = prefix + guiId;
                serverGuiIds.add(key);
                guiNames.putIfAbsent(key, guiId);
            }
        }

        ReSyncFlowClient client = ensureFlowClient(serverId);
        if (guiIds != null) {
            for (String guiId : guiIds) {
                client.requestGui(guiId, false);
            }
        }

        refreshFlowManagerScreen(serverId);
    }

    public void applyServerScoreboardList(String serverId, java.util.List<String> scoreboardIds) {
        clearScoreboardCache(serverId);
        String prefix = serverId + ":";
        if (scoreboardIds != null) {
            for (String scoreboardId : scoreboardIds) {
                String key = prefix + scoreboardId;
                serverScoreboardIds.add(key);
                scoreboardNames.putIfAbsent(key, scoreboardId);
            }
        }

        ReSyncFlowClient client = ensureFlowClient(serverId);
        if (scoreboardIds != null) {
            for (String scoreboardId : scoreboardIds) {
                client.requestScoreboard(scoreboardId, false);
            }
        }

        refreshFlowManagerScreen(serverId);
    }

    public void applyServerTabList(String serverId, java.util.List<String> tabIds) {
        clearTabCache(serverId);
        String prefix = serverId + ":";
        if (tabIds != null) {
            for (String tabId : tabIds) {
                String key = prefix + tabId;
                serverTabIds.add(key);
                tabNames.putIfAbsent(key, tabId);
            }
        }

        ReSyncFlowClient client = ensureFlowClient(serverId);
        if (tabIds != null) {
            for (String tabId : tabIds) {
                client.requestTab(tabId, false);
            }
        }

        refreshFlowManagerScreen(serverId);
    }

    public void addBinding(String serverId, TriggerBinding binding) {
        java.util.List<TriggerBinding> bindings = getBindings(serverId);
        bindings.add(binding);
        sendTriggerUpdate(serverId, bindings);
    }

    public void removeBinding(String serverId, String bindingId) {
        java.util.List<TriggerBinding> bindings = getBindings(serverId);
        bindings.removeIf(binding -> bindingId.equals(binding.getId()));
        sendTriggerUpdate(serverId, bindings);
    }

    private void sendTriggerUpdate(String serverId, java.util.List<TriggerBinding> bindings) {
        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.sendTriggerUpdate(bindings);
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
        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.requestPlaceholderPreview(value, true, callback);
    }

    private ReSyncFlowClient ensureFlowClient(String serverId) {
        return ensureFlowClient(serverId, true);
    }

    private ReSyncFlowClient ensureFlowClient(String serverId, boolean showNotifications) {
        return ensureFlowClient(serverId, flowProfiles.get(serverId), showNotifications, true);
    }

    private ReSyncFlowClient ensureFlowClient(String serverId, ReSyncConnectionProfile profile) {
        return ensureFlowClient(serverId, profile, true, true);
    }

    private ReSyncFlowClient ensureFlowClient(String serverId, ReSyncConnectionProfile profile, boolean showNotifications, boolean connectIfNeeded) {
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient == null) {
            if (profile != null && profile.wsUrl() != null && !profile.wsUrl().isBlank()) {
                flowClient = new ReSyncFlowClient(serverId, apiClient, profile.wsUrl(), profile.apiKey());
            } else {
                flowClient = new ReSyncFlowClient(serverId, apiClient);
            }
            flowClients.put(serverId, flowClient);
        }
        if (showNotifications) {
            flowClient.setErrorListener((nodeId, message) -> ScreenManager.getInstance().execute(() -> {
                String normalized = normalizeReSyncNotificationMessage(message);
                Notification.Type type = "ReSync Connection Timed Out".equals(normalized) ? Notification.Type.WARN : Notification.Type.ERROR;
                new Notification("ReSync", normalized, type);
            }));
        } else {
            flowClient.setErrorListener((nodeId, message) -> {
            });
        }
        if (connectIfNeeded) {
            flowClient.connect();
        }
        return flowClient;
    }

    public String normalizeReSyncNotificationMessage(String message) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
            return "ReSync Connection Timed Out";
        }
        return "ReSync Isn't Installed/Enabled";
    }

    public void applyPlayerTrackingUpdate(String serverId, PlayerTrackingUpdate update) {
        if (serverId == null || update == null) {
            return;
        }
        if ("snapshot".equalsIgnoreCase(update.getType())) {
            clearPlayerDossierCache(serverId);
            for (PlayerDossier dossier : update.getDossiers()) {
                cachePlayerDossier(serverId, dossier);
            }
            return;
        }
        cachePlayerDossier(serverId, update.getDossier());
    }

    public void requestPlayerTrackingSnapshot(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ensureFlowClient(serverId).requestPlayerTrackingSnapshot();
    }

    public void requestPlayerDossier(String serverId, UUID playerId) {
        if (serverId == null || serverId.isBlank() || playerId == null) {
            return;
        }
        ensureFlowClient(serverId).requestPlayerDossier(playerId);
    }

    public PlayerDossier getPlayerDossier(String serverId, UUID playerId) {
        if (serverId == null || playerId == null) {
            return null;
        }
        return playerDossierCache.get(serverId + ":" + playerId);
    }

    private void cachePlayerDossier(String serverId, PlayerDossier dossier) {
        if (serverId == null || dossier == null || dossier.getPlayerId() == null || dossier.getPlayerId().isBlank()) {
            return;
        }
        playerDossierCache.put(serverId + ":" + dossier.getPlayerId(), dossier);
    }

    public void applyWorldManagementMessage(String serverId, WorldChannelMessage message) {
        if (serverId == null || serverId.isBlank() || message == null) {
            return;
        }
        String action = message.getAction() == null ? "" : message.getAction();
        WorldOperationResult operationResult = null;
        if ("snapshot".equalsIgnoreCase(action) && message.getData() != null) {
            WorldSnapshot snapshot = gson.fromJson(message.getData(), WorldSnapshot.class);
            if (snapshot != null) {
                worldSnapshotCache.put(serverId, snapshot);
                for (WorldRegistryEntry world : snapshot.getWorlds()) {
                    if (world != null && world.getWorldName() != null) {
                        upsertFlowManagerWorldEntry(serverId, world.getWorldName());
                    }
                }
                refreshFlowManagerScreen(serverId);
            }
            return;
        }
        if ("mapSnapshot".equalsIgnoreCase(action) && message.getData() != null) {
            WorldMapSnapshot snapshot = gson.fromJson(message.getData(), WorldMapSnapshot.class);
            String worldName = pendingWorldMapRequests.remove(serverId);
            if (snapshot != null && worldName != null && !worldName.isBlank()) {
                worldMapSnapshotCache.put(serverId + ":" + worldName.toLowerCase(Locale.ROOT), snapshot);
            }
            return;
        }
        if ("error".equalsIgnoreCase(message.getType())) {
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", prettyWorldMessage(message.getMessage()), Notification.Type.ERROR));
            return;
        }
        if ("response".equalsIgnoreCase(message.getType()) && message.getData() != null) {
            operationResult = cacheWorldOperationResult(serverId, message);
        }
        if ("response".equalsIgnoreCase(message.getType()) && message.isSuccess() && operationResult != null) {
            applyWorldOperationSnapshotPatch(serverId, operationResult);
        }
        if ("response".equalsIgnoreCase(message.getType()) && !message.isSuccess()) {
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", prettyWorldMessage(message.getMessage()), Notification.Type.ERROR));
        }
        if ("response".equalsIgnoreCase(message.getType()) && message.isSuccess() && operationResult != null) {
            dispatchWorldOperationResult(serverId, operationResult);
        }
        if ("response".equalsIgnoreCase(message.getType()) && message.isSuccess() && !"snapshot".equalsIgnoreCase(action) && !"mapSnapshot".equalsIgnoreCase(action)
            && !"setGameRule".equalsIgnoreCase(action) && !consumeSuppressedWorldSuccessNotification(serverId, action)) {
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", prettyWorldMessage(message.getMessage()), Notification.Type.SUCCESS));
        }
        ensureFlowClient(serverId).requestWorldSnapshot();
    }

    public void requestWorldMapSnapshot(String serverId, String worldName, double centerX, double centerZ, int zoom) {
        if (serverId == null || serverId.isBlank() || worldName == null || worldName.isBlank()) {
            return;
        }
        pendingWorldMapRequests.put(serverId, worldName);
        ensureFlowClient(serverId).requestWorldMapSnapshot(worldName, centerX, centerZ, zoom);
    }

    public void createWorld(String serverId, String worldName, String seed, String environment, String generator, String generatorConfig) {
        sendWorldAction(serverId, worldAction("createWorld", "worldName", worldName, "seed", seed, "environment", environment, "generator", generator, "generatorConfig", generatorConfig));
    }

    public void importWorlds(String serverId) {
        sendWorldAction(serverId, worldAction("importUnregisteredWorlds"));
    }

    public void scanWorlds(String serverId) {
        sendWorldAction(serverId, worldAction("scanUnregisteredWorlds"));
    }

    public void cloneWorld(String serverId, String sourceWorld, String targetWorld, boolean loadAfterClone) {
        sendWorldAction(serverId, worldAction("cloneWorld", "sourceWorld", sourceWorld, "targetWorld", targetWorld, "loadAfterClone", loadAfterClone));
    }

    public void loadWorld(String serverId, String worldName) {
        sendWorldAction(serverId, worldAction("loadWorld", "worldName", worldName));
    }

    public void unloadWorld(String serverId, String worldName, String fallbackWorld) {
        sendWorldAction(serverId, worldAction("unloadWorld", "worldName", worldName, "fallbackWorld", fallbackWorld));
    }

    public void deleteWorld(String serverId, String worldName, boolean deleteFiles, String fallbackWorld) {
        sendWorldAction(serverId, worldAction("deleteWorld", "worldName", worldName, "deleteFiles", deleteFiles, "fallbackWorld", fallbackWorld));
    }

    public void setWorldGameRule(String serverId, String worldName, String ruleName, String value) {
        sendWorldAction(serverId, worldAction("setGameRule", "worldName", worldName, "ruleName", ruleName, "ruleValue", value));
    }

    public void setWorldGameRules(String serverId, String worldName, Map<String, String> rules) {
        sendWorldAction(serverId, worldAction("setGameRules", "worldName", worldName, "gameRules", rules));
    }

    public void suppressNextWorldSuccessNotification(String serverId, String action) {
        if (serverId == null || serverId.isBlank() || action == null || action.isBlank()) {
            return;
        }
        suppressedWorldSuccessNotifications.merge(serverId + ":" + action.toLowerCase(Locale.ROOT), 1, Integer::sum);
    }

    public void setWorldDifficulty(String serverId, String worldName, String difficulty) {
        sendWorldAction(serverId, worldAction("setDifficulty", "worldName", worldName, "difficulty", difficulty));
    }

    public void setWorldTimeLock(String serverId, String worldName, boolean enabled, long lockedTime) {
        sendWorldAction(serverId, worldAction("setTimeLock", "worldName", worldName, "enabled", enabled, "lockedTime", lockedTime));
    }

    public void setWorldWeatherLock(String serverId, String worldName, boolean enabled, boolean storm, boolean thundering) {
        sendWorldAction(serverId, worldAction("setWeatherLock", "worldName", worldName, "enabled", enabled, "storm", storm, "thundering", thundering));
    }

    public void setWorldIsolatedState(String serverId, String worldName, boolean enabled) {
        sendWorldAction(serverId, worldAction("setIsolatedPlayerState", "worldName", worldName, "enabled", enabled));
    }

    public void setWorldProfile(String serverId, String worldName, WorldProfileSettings profileSettings) {
        sendWorldAction(serverId, worldAction("setWorldProfile", "worldName", worldName, "profileSettings", profileSettings));
    }

    public void teleportPlayerToWorld(String serverId, String playerName, String worldName, Double x, Double y, Double z, Float yaw, Float pitch) {
        sendWorldAction(serverId, worldAction(
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
        sendWorldAction(serverId, worldAction("teleportPlayerToWorldSpawn", "playerName", playerName, "worldName", worldName));
    }

    public void createInventoryGroup(String serverId, WorldInventoryGroup group) {
        sendWorldAction(serverId, worldAction("createInventoryGroup", "inventoryGroup", group));
    }

    public void updateInventoryGroup(String serverId, WorldInventoryGroup group) {
        sendWorldAction(serverId, worldAction("updateInventoryGroup", "inventoryGroup", group));
    }

    public void deleteInventoryGroup(String serverId, String groupId) {
        sendWorldAction(serverId, worldAction("deleteInventoryGroup", "groupId", groupId));
    }

    public void whoWorld(String serverId, String worldName) {
        sendWorldAction(serverId, worldAction("whoWorld", "worldName", worldName));
    }

    public void purgeWorld(String serverId, String worldName, boolean monsters, boolean animals, boolean ambient, boolean misc, boolean vehicles, boolean items) {
        sendWorldAction(serverId, worldAction(
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
        try {
            InstanceManager instanceManager = Rebase.get().getInstanceManager();
            List<Instance> instances = new ArrayList<>(instanceManager.getLocalInstances());
            for (var host : instanceManager.getRemoteHosts()) {
                instances.addAll(instanceManager.getRemoteInstances(host));
            }
            for (Instance instance : instances) {
                BackendConfig backendConfig = instance.getBackendConfig();
                if (backendConfig != null && backendConfig.credentials != null) {
                    String identifier = backendConfig.credentials.get("identifier");
                    if (identifier != null && identifier.equals(serverId)) {
                        return instance;
                    }
                }
            }
            if (server != null && server.name != null) {
                for (Instance instance : instances) {
                    if (server.name.equalsIgnoreCase(instance.getName())) {
                        return instance;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void sendWorldAction(String serverId, Map<String, Object> request) {
        if (serverId == null || serverId.isBlank() || request == null || request.isEmpty()) {
            return;
        }
        ensureFlowClient(serverId).sendWorldAction(request);
    }

    private Map<String, Object> worldAction(String action, Object... pairs) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        if (pairs != null) {
            for (int index = 0; index + 1 < pairs.length; index += 2) {
                Object key = pairs[index];
                if (key instanceof String stringKey && !stringKey.isBlank()) {
                    data.put(stringKey, pairs[index + 1]);
                }
            }
        }
        return data;
    }

    private boolean consumeSuppressedWorldSuccessNotification(String serverId, String action) {
        if (serverId == null || action == null) {
            return false;
        }
        String key = serverId + ":" + action.toLowerCase(Locale.ROOT);
        Integer count = suppressedWorldSuccessNotifications.get(key);
        if (count == null || count <= 0) {
            return false;
        }
        if (count == 1) {
            suppressedWorldSuccessNotifications.remove(key);
        } else {
            suppressedWorldSuccessNotifications.put(key, count - 1);
        }
        return true;
    }

    private String prettyWorldMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Done";
        }
        String normalized = message.replace(':', ' ').replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? "Done" : builder.toString();
    }

    private void applyWorldOperationSnapshotPatch(String serverId, WorldOperationResult result) {
        if (serverId == null || serverId.isBlank() || result == null || !result.isSuccess()) {
            return;
        }
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        if (snapshot == null) {
            return;
        }
        String action = result.getAction() == null ? "" : result.getAction().trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "createinventorygroup", "updateinventorygroup" ->
                upsertSnapshotInventoryGroup(snapshot, convertWorldResultData(result, "group", WorldInventoryGroup.class));
            case "deleteinventorygroup" -> removeSnapshotInventoryGroup(snapshot, resultDataText(result, "groupId"));
            case "createworld", "loadworld", "unloadworld" ->
                upsertSnapshotWorld(snapshot, convertWorldResultData(result, "world", WorldRegistryEntry.class));
            case "deleteworld" -> removeSnapshotWorld(snapshot, resultDataText(result, "worldName", result.getWorldName()));
            default -> {
            }
        }
    }

    private <T> T convertWorldResultData(WorldOperationResult result, String key, Class<T> type) {
        if (result == null || result.getData() == null || key == null || key.isBlank() || type == null) {
            return null;
        }
        Object value = result.getData().get(key);
        if (value == null) {
            return null;
        }
        try {
            return gson.fromJson(gson.toJson(value), type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resultDataText(WorldOperationResult result, String... keys) {
        if (result == null) {
            return "";
        }
        if (keys != null && result.getData() != null) {
            for (String key : keys) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                Object value = result.getData().get(key);
                if (value != null) {
                    String text = String.valueOf(value).trim();
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        String worldName = result.getWorldName();
        return worldName == null ? "" : worldName.trim();
    }

    private void upsertSnapshotWorld(WorldSnapshot snapshot, WorldRegistryEntry world) {
        if (snapshot == null || world == null || world.getWorldName() == null || world.getWorldName().isBlank()) {
            return;
        }
        try {
            snapshot.getWorlds().removeIf(entry -> entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(world.getWorldName()));
            snapshot.getWorlds().add(world);
        } catch (Exception ignored) {
        }
    }

    private void removeSnapshotWorld(WorldSnapshot snapshot, String worldName) {
        if (snapshot == null || worldName == null || worldName.isBlank()) {
            return;
        }
        try {
            snapshot.getWorlds().removeIf(entry -> entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(worldName));
            snapshot.getDashboard().removeIf(entry -> entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(worldName));
        } catch (Exception ignored) {
        }
    }

    private void upsertSnapshotInventoryGroup(WorldSnapshot snapshot, WorldInventoryGroup group) {
        if (snapshot == null || group == null || group.getGroupId() == null || group.getGroupId().isBlank()) {
            return;
        }
        try {
            snapshot.getInventoryGroups().removeIf(entry -> entry != null && entry.getGroupId() != null && entry.getGroupId().equalsIgnoreCase(group.getGroupId()));
            snapshot.getInventoryGroups().add(group);
        } catch (Exception ignored) {
        }
    }

    private void removeSnapshotInventoryGroup(WorldSnapshot snapshot, String groupId) {
        if (snapshot == null || groupId == null || groupId.isBlank()) {
            return;
        }
        try {
            snapshot.getInventoryGroups().removeIf(entry -> entry != null && entry.getGroupId() != null && entry.getGroupId().equalsIgnoreCase(groupId));
        } catch (Exception ignored) {
        }
    }

    private void dispatchWorldOperationResult(String serverId, WorldOperationResult result) {
        if (serverId == null || serverId.isBlank() || result == null) {
            return;
        }
        ScreenManager.getInstance().execute(() -> {
            FlowManagerScreen screen = FlowManagerScreen.getOpenScreen(serverId);
            if (screen != null) {
                screen.handleWorldOperationResult(result);
            }
        });
    }

    private WorldOperationResult cacheWorldOperationResult(String serverId, WorldChannelMessage message) {
        if (serverId == null || serverId.isBlank() || message == null || message.getData() == null) {
            return null;
        }
        try {
            WorldOperationResult result = gson.fromJson(message.getData(), WorldOperationResult.class);
            if (result != null) {
                worldOperationCache.put(serverId, result);
            }
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private java.util.UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getOrCreateDefaultFlowId(String serverId) {
        Map<String, FlowGraph> flows = getFlowsForServer(serverId);
        if (!flows.isEmpty()) {
            return flows.keySet().iterator().next();
        }
        FlowGraph graph = createFlow(serverId);
        return graph.getId();
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
            ReSyncFlowClient flowClient = flowClients.get(serverId);
            if (flowClient != null) {
                flowClient.requestGui(guiId, false);
            }
        }
    }

    public boolean isOverlayEditable() {
        return overlayEditable;
    }

    public String getOverlayServerId() {
        return overlayServerId;
    }

    public String getOverlayGuiId() {
        return overlayGuiId;
    }

    public String getOverlayFlowId() {
        return overlayFlowId;
    }

    public int getOverlayRevision() {
        return overlayRevision.get();
    }

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
        String key = serverId + ":" + gui.getId();
        Object parent = pendingGuiParents.remove(key);
        if (parent != null) {
            client.getHost().setScreen(new GuiDesignerScreen(gui, serverId, parent));
        }
    }

    public void handleScoreboardDataReceived(String serverId, ScoreboardDefinition scoreboard) {
        if (scoreboard == null || scoreboard.getId() == null || scoreboard.getId().isBlank()) {
            return;
        }
        String key = serverId + ":" + scoreboard.getId();
        Object parent = pendingScoreboardParents.remove(key);
        if (parent != null) {
            client.getHost().setScreen(new ScoreboardDesignerScreen(scoreboard, serverId, parent));
        }
    }

    public void handleTabDataReceived(String serverId, TabDefinition tab) {
        if (tab == null || tab.getId() == null || tab.getId().isBlank()) {
            return;
        }
        String key = serverId + ":" + tab.getId();
        Object parent = pendingTabParents.remove(key);
        if (parent != null) {
            client.getHost().setScreen(new TabDesignerScreen(tab, serverId, parent));
        }
    }

}
