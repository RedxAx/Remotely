package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingUpdate;
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
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FlowManager {
    private static FlowManager INSTANCE;
    private final RemotelyClient client;
    private final ReStudioApiClient apiClient;
    private final Map<String, ReSyncFlowClient> flowClients = new ConcurrentHashMap<>();
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
    private final java.util.Set<String> serverFlowIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> serverGuiIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> serverScoreboardIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> serverTabIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean overlayEditable;
    private volatile String overlayServerId;
    private volatile String overlayGuiId;
    private volatile String overlayFlowId;
    private final java.util.concurrent.atomic.AtomicInteger overlayRevision = new java.util.concurrent.atomic.AtomicInteger();

    public FlowManager(RemotelyClient client, ReStudioApiClient apiClient) {
        this.client = client;
        this.apiClient = apiClient;
        INSTANCE = this;
    }

    public static FlowManager getInstance() {
        return INSTANCE;
    }

    public void openFlowManager(String serverId, ClientServerView server) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        ensureFlowClient(actualServerId);
        refreshFlowsFromServer(actualServerId);
        refreshGuisFromServer(actualServerId);
        refreshScoreboardsFromServer(actualServerId);
        refreshTabsFromServer(actualServerId);
        client.getHost().setScreen(new FlowManagerScreen(actualServerId, server, ScreenManager.getInstance().getCurrentScreen()));
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
        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.requestFlowList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshGuisFromServer(String serverId) {
        clearGuiCache(serverId);
        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.requestGuiList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshScoreboardsFromServer(String serverId) {
        clearScoreboardCache(serverId);
        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.requestScoreboardList();
        refreshFlowManagerScreen(serverId);
    }

    public void refreshTabsFromServer(String serverId) {
        clearTabCache(serverId);
        ReSyncFlowClient client = ensureFlowClient(serverId);
        client.requestTabList();
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
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient == null) {
            flowClient = new ReSyncFlowClient(serverId, apiClient);
            flowClients.put(serverId, flowClient);
            flowClient.connect();
        }
        return flowClient;
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
