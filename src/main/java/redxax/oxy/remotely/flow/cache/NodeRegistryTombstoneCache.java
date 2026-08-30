package redxax.oxy.remotely.flow.cache;

import redxax.oxy.remotely.flow.sync.NodePluginPayload;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import redxax.oxy.remotely.data.flow.ReSyncStorage;

public class NodeRegistryTombstoneCache {
    private static final int CACHE_SCHEMA_VERSION = 1;
    private static final NodeRegistryTombstoneCache INSTANCE = new NodeRegistryTombstoneCache(ReSyncStorage.memory("remotely.node-registry-tombstones"));
    private final ReSyncStorage storage;
    private final Map<String, Map<String, NodePluginPayload>> servers = new HashMap<>();

    private NodeRegistryTombstoneCache() {
        this(ReSyncStorage.memory("remotely.node-registry-tombstones"));
    }

    public NodeRegistryTombstoneCache(ReSyncStorage storage) {
        this.storage = storage != null ? storage : ReSyncStorage.memory();
        load();
    }

    public static NodeRegistryTombstoneCache getInstance() {
        return INSTANCE;
    }

    public synchronized List<NodePluginPayload> get(String serverId) {
        Map<String, NodePluginPayload> plugins = servers.get(serverId != null ? serverId : "");
        return plugins != null ? plugins.values().stream().filter(payload -> payload != null)
            .sorted(Comparator.comparing(NodePluginPayload::getPluginId, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))).toList() : List.of();
    }

    public synchronized void replace(String serverId, List<NodePluginPayload> payloads) {
        String key = serverId != null ? serverId : "";
        Map<String, NodePluginPayload> plugins = new HashMap<>();
        if (payloads != null) {
            for (NodePluginPayload payload : payloads) {
                if (payload != null && payload.getPluginId() != null && !payload.getPluginId().isBlank()) {
                    plugins.put(payload.getPluginId(), payload);
                }
            }
        }
        if (plugins.isEmpty()) {
            servers.remove(key);
        } else {
            servers.put(key, plugins);
        }
        save();
    }

    private void load() {
        CacheState state = storage.readObject("node-registry-tombstones", CacheState.class);
        if (state == null || state.schemaVersion != CACHE_SCHEMA_VERSION || state.servers == null) return;
        for (Map.Entry<String, Map<String, NodePluginPayload>> entry : state.servers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            Map<String, NodePluginPayload> plugins = new HashMap<>();
            for (Map.Entry<String, NodePluginPayload> plugin : entry.getValue().entrySet()) {
                if (plugin.getKey() != null && plugin.getValue() != null) {
                    plugins.put(plugin.getKey(), plugin.getValue());
                }
            }
            if (!plugins.isEmpty()) servers.put(entry.getKey(), plugins);
        }
    }

    private void save() {
        try {
            storage.writeObject("node-registry-tombstones", new CacheState(servers));
        } catch (RuntimeException ignored) {
        }
    }

    private static class CacheState {
        private int schemaVersion = CACHE_SCHEMA_VERSION;
        private Map<String, Map<String, NodePluginPayload>> servers = new HashMap<>();

        private CacheState() {
        }

        private CacheState(Map<String, Map<String, NodePluginPayload>> servers) {
            this.servers = new HashMap<>();
            for (Map.Entry<String, Map<String, NodePluginPayload>> entry : servers.entrySet()) {
                this.servers.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
        }
    }
}
