package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.flow.sync.NodePluginPayload;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NodeRegistry {
    private static final String DEFAULT_SERVER_KEY = "local";
    private final Map<String, NodeDefinition> localDefinitions = new HashMap<>();
    private final Map<String, Map<String, NodeDefinition>> serverDefinitions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, NodePluginPayload>> serverPlugins = new ConcurrentHashMap<>();
    private final Map<String, List<String>> serverNodeIds = new ConcurrentHashMap<>();
    private final List<NodeRegistryListener> listeners = new CopyOnWriteArrayList<>();

    private static NodeRegistry INSTANCE;

    public NodeRegistry() {
        INSTANCE = this;
    }

    public static NodeRegistry getInstance() {
        return INSTANCE;
    }

    public interface NodeRegistryListener {
        void onRegistryUpdated(String serverId);
    }

    public void register(NodeDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return;
        }
        localDefinitions.put(definition.getId(), definition);
    }

    public NodeDefinition getDefinition(String serverId, String nodeId) {
        if (nodeId == null) {
            return null;
        }
        String key = normalizeServerId(serverId);
        Map<String, NodeDefinition> definitions = serverDefinitions.get(key);
        if (definitions != null && definitions.containsKey(nodeId)) {
            return definitions.get(nodeId);
        }
        return null;
    }

    public boolean hasDefinitions(String serverId) {
        String key = normalizeServerId(serverId);
        Map<String, NodeDefinition> definitions = serverDefinitions.get(key);
        return definitions != null && !definitions.isEmpty();
    }

    public Map<String, NodeDefinition> getAllDefinitions(String serverId) {
        String key = normalizeServerId(serverId);
        Map<String, NodeDefinition> definitions = serverDefinitions.get(key);
        if (definitions == null) {
            return new HashMap<>();
        }
        return new HashMap<>(definitions);
    }

    public Map<String, NodeDefinition> getLocalDefinitions() {
        return new HashMap<>(localDefinitions);
    }

    public void applySnapshot(String serverId, NodeRegistrySnapshot snapshot) {
        if (serverId == null || snapshot == null) {
            return;
        }
        String key = normalizeServerId(serverId);
        if (snapshot.isFullSync()) {
            serverPlugins.remove(key);
            serverNodeIds.remove(key);
        }
        Map<String, NodePluginPayload> pluginMap = serverPlugins.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());

        if (snapshot.getRemovedPlugins() != null) {
            for (String pluginId : snapshot.getRemovedPlugins()) {
                pluginMap.remove(pluginId);
            }
        }

        if (snapshot.getPlugins() != null) {
            for (NodePluginPayload payload : snapshot.getPlugins()) {
                if (payload != null && payload.getPluginId() != null) {
                    pluginMap.put(payload.getPluginId(), payload);
                }
            }
        }

        if (snapshot.getNodeIds() != null && !snapshot.getNodeIds().isEmpty()) {
            serverNodeIds.put(key, new ArrayList<>(snapshot.getNodeIds()));
        }

        rebuildServerDefinitions(key);
        notifyListeners(serverId);
    }

    public void clearServer(String serverId) {
        String key = normalizeServerId(serverId);
        serverPlugins.remove(key);
        serverNodeIds.remove(key);
        serverDefinitions.remove(key);
        notifyListeners(serverId);
    }

    public void addListener(NodeRegistryListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(NodeRegistryListener listener) {
        listeners.remove(listener);
    }

    private void rebuildServerDefinitions(String key) {
        List<String> nodeIds = serverNodeIds.getOrDefault(key, List.of());
        Set<String> nodeIdSet = new HashSet<>(nodeIds);
        boolean hasNodeList = !nodeIds.isEmpty();
        Map<String, NodeDefinition> definitions = new HashMap<>();
        Map<String, NodePluginPayload> plugins = serverPlugins.getOrDefault(key, Map.of());

        for (NodePluginPayload payload : plugins.values()) {
            if (payload == null || payload.getNodes() == null) {
                continue;
            }
            for (NodeDefinition def : payload.getNodes()) {
                if (def == null || def.getId() == null) {
                    continue;
                }
                if (!hasNodeList || nodeIdSet.contains(def.getId())) {
                    definitions.put(def.getId(), def);
                }
            }
        }

        if (hasNodeList) {
            for (String nodeId : nodeIds) {
                if (!definitions.containsKey(nodeId)) {
                    System.out.println("[Flow] Missing definition for node: " + nodeId);
                }
            }
        }

        serverDefinitions.put(key, definitions);
    }

    private void notifyListeners(String serverId) {
        for (NodeRegistryListener listener : listeners) {
            listener.onRegistryUpdated(serverId);
        }
    }

    private String normalizeServerId(String serverId) {
        return serverId != null ? serverId : DEFAULT_SERVER_KEY;
    }
}
