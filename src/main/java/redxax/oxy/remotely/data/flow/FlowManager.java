package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.Visual;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.FlowManagerScreen;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.ui.core.Screen;
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
    private final Map<String, GuiDefinition> draftGuis = new ConcurrentHashMap<>();
    private final Map<String, FlowGraph> flowCache = new ConcurrentHashMap<>();
    private final Map<String, FlowGraph> draftFlows = new ConcurrentHashMap<>();
    private final Map<String, String> flowNames = new ConcurrentHashMap<>();
    private final Map<String, String> guiNames = new ConcurrentHashMap<>();
    private final Map<String, Object> pendingGuiParents = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<redxax.oxy.remotely.flow.data.TriggerBinding>> triggerBindings = new ConcurrentHashMap<>();
    private final java.util.Set<String> serverFlowIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> serverGuiIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
            client.getHost().setScreen(new FlowEditorScreen(new FlowGraph(), actualServerId, ScreenManager.getInstance().getCurrentScreen()));
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
        Object parent = parentOverride != null ? parentOverride : ScreenManager.getInstance().getCurrentScreen();
        if (gui == null) {
            pendingGuiParents.put(key, parent);
            ReSyncFlowClient flowClient = ensureFlowClient(actualServerId);
            flowClient.requestGui(guiId, false);
            return;
        }

        client.getHost().setScreen(new GuiDesignerScreen(gui, actualServerId, parent));
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
        if (redxax.oxy.remotely.flow.registry.NodeRegistry.getInstance() != null) {
            redxax.oxy.remotely.flow.registry.NodeRegistry.getInstance().clearServer(serverId);
        }
        clearServerCache(serverId);
    }

    private void clearServerCache(String serverId) {
        clearFlowCache(serverId);
        clearGuiCache(serverId);
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
        for (var entry : draftGuis.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String guiId = entry.getKey().substring(prefix.length());
                guis.putIfAbsent(guiId, entry.getValue());
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
        if (client != null) {
            client.sendFlowSave(graph);
            if (wasServer) {
                client.sendFlowDelete(flowId);
            }
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
        if (client != null) {
            client.sendGuiSave(gui);
            if (wasServer) {
                client.sendGuiDelete(guiId);
            }
        }

        refreshFlowManagerScreen(serverId);
        return true;
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

    public void cacheGui(String serverId, GuiDefinition gui) {
        if (gui == null || gui.getId() == null) {
            return;
        }
        String key = serverId + ":" + gui.getId();
        guiCache.put(key, gui);
        guiNames.putIfAbsent(key, gui.getTitle() != null ? gui.getTitle() : gui.getId());
        serverGuiIds.add(key);
        draftGuis.remove(key);
        refreshFlowManagerScreen(serverId);
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
        client.sendTriggerUpdate(bindings);
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
}
