package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.Visual;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.FlowManagerScreen;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.ui.core.ScreenManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlowManager {
    private static FlowManager INSTANCE;
    private final RemotelyClient client;
    private final ReStudioApiClient apiClient;
    private final Map<String, ReSyncFlowClient> flowClients = new ConcurrentHashMap<>();
    private final Map<String, GuiDefinition> guiCache = new ConcurrentHashMap<>();
    private final Map<String, FlowGraph> flowCache = new ConcurrentHashMap<>();
    private final Map<String, FlowGraph> draftFlows = new ConcurrentHashMap<>();
    private final Map<String, String> flowNames = new ConcurrentHashMap<>();
    private final Map<String, String> guiNames = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<redxax.oxy.remotely.flow.data.TriggerBinding>> triggerBindings = new ConcurrentHashMap<>();
    private final java.util.Set<String> serverFlowIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        client.getHost().setScreen(new FlowManagerScreen(actualServerId, server));
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
            client.getHost().setScreen(new FlowEditorScreen(graph, actualServerId));
            return;
        }

        if (flowClient != null && serverFlowIds.contains(key)) {
            flowClient.requestFlow(flowId, true);
            client.getHost().setScreen(new FlowEditorScreen(new FlowGraph(), actualServerId));
            return;
        }

        FlowGraph newGraph = createDefaultFlow();
        java.util.UUID parsedId = parseUuid(flowId);
        if (parsedId != null) {
            newGraph.setId(parsedId);
        }
        String actualFlowId = newGraph.getId().toString();
        String actualKey = actualServerId + ":" + actualFlowId;
        draftFlows.put(actualKey, newGraph);
        flowNames.putIfAbsent(actualKey, actualFlowId);
        client.getHost().setScreen(new FlowEditorScreen(newGraph, actualServerId));
    }

    public void openGuiDesigner(String serverId, ClientServerView server) {
        openGuiDesigner(serverId, server, "main");
    }

    public void openGuiDesigner(String serverId, ClientServerView server, String guiId) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        GuiDefinition gui = guiCache.get(actualServerId + ":" + guiId);
        if (gui == null) {
            gui = createDefaultGui(guiId);
            guiCache.put(actualServerId + ":" + guiId, gui);
            guiNames.putIfAbsent(actualServerId + ":" + guiId, gui.getTitle());
        }

        client.getHost().setScreen(new GuiDesignerScreen(gui, actualServerId));
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
        flowNames.putIfAbsent(key, graph.getId().toString());
        serverFlowIds.add(key);
        draftFlows.remove(key);
        refreshFlowManagerScreen(serverId);
    }

    public void closeServerConnection(String serverId) {
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null) {
            flowClient.shutdown();
            flowClients.remove(serverId);
        }
        clearServerCache(serverId);
    }

    private void clearServerCache(String serverId) {
        String prefix = serverId + ":";
        flowCache.keySet().removeIf(key -> key.startsWith(prefix));
        draftFlows.keySet().removeIf(key -> key.startsWith(prefix));
        serverFlowIds.removeIf(key -> key.startsWith(prefix));
        flowNames.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void refreshFlowManagerScreen(String serverId) {
        ScreenManager.getInstance().execute(() -> {
            if (ScreenManager.getInstance().getCurrentScreen() instanceof FlowManagerScreen screen
                && serverId.equals(screen.getServerId())) {
                screen.refresh();
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
        return guis;
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

    public FlowGraph createFlow(String serverId) {
        FlowGraph graph = createDefaultFlow();
        String key = serverId + ":" + graph.getId();
        draftFlows.put(key, graph);
        flowNames.putIfAbsent(key, graph.getId().toString());
        return graph;
    }

    public GuiDefinition createGui(String serverId, String id) {
        GuiDefinition gui = createDefaultGui(id);
        guiCache.put(serverId + ":" + id, gui);
        guiNames.putIfAbsent(serverId + ":" + id, gui.getTitle());
        return gui;
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
        guiCache.remove(serverId + ":" + guiId);
        guiNames.remove(serverId + ":" + guiId);
    }

    public java.util.List<redxax.oxy.remotely.flow.data.TriggerBinding> getBindings(String serverId) {
        return triggerBindings.computeIfAbsent(serverId, id -> new java.util.ArrayList<>());
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

    public void refreshFlowsFromServer(String serverId) {
        clearServerCache(serverId);
        ReSyncFlowClient client = ensureFlowClient(serverId);
        if (client != null) {
            client.requestFlowList();
        }
        refreshFlowManagerScreen(serverId);
    }

    public void applyServerFlowList(String serverId, java.util.List<String> flowIds) {
        clearServerCache(serverId);
        String prefix = serverId + ":";
        if (flowIds != null) {
            for (String flowId : flowIds) {
                String key = prefix + flowId;
                serverFlowIds.add(key);
                flowNames.putIfAbsent(key, flowId);
            }
        }

        ReSyncFlowClient client = ensureFlowClient(serverId);
        if (client != null && flowIds != null) {
            for (String flowId : flowIds) {
                client.requestFlow(flowId, false);
            }
        }

        refreshFlowManagerScreen(serverId);
    }

    public void addBinding(String serverId, redxax.oxy.remotely.flow.data.TriggerBinding binding) {
        java.util.List<redxax.oxy.remotely.flow.data.TriggerBinding> bindings = getBindings(serverId);
        bindings.add(binding);
        sendTriggerUpdate(serverId, bindings);
    }

    public void removeBinding(String serverId, String bindingId) {
        java.util.List<redxax.oxy.remotely.flow.data.TriggerBinding> bindings = getBindings(serverId);
        bindings.removeIf(binding -> bindingId.equals(binding.getId()));
        sendTriggerUpdate(serverId, bindings);
    }

    private void sendTriggerUpdate(String serverId, java.util.List<redxax.oxy.remotely.flow.data.TriggerBinding> bindings) {
        ReSyncFlowClient client = ensureFlowClient(serverId);
        if (client != null) {
            client.sendTriggerUpdate(bindings);
        }
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
        return graph.getId().toString();
    }

    public void handleGuiStatePacket(String serverId, boolean editable, String flowId) {
        if (editable) {
            openFlowEditor(serverId, null);
        }
    }
}
