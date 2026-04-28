package redxax.oxy.remotely.flow.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowDataTypeAdapter;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.sync.NodePluginPayload;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class NodeRegistryCache {
    private static NodeRegistryCache INSTANCE;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(FlowDataType.class, new FlowDataTypeAdapter())
            .registerTypeAdapter(NodeDefinition.NodeCategory.class, new TypeAdapter<NodeDefinition.NodeCategory>() {
                @Override
                public void write(JsonWriter out, NodeDefinition.NodeCategory value) throws IOException {
                    out.value(value != null ? value.getId() : null);
                }

                @Override
                public NodeDefinition.NodeCategory read(JsonReader in) throws IOException {
                    String id = in.nextString();
                    return NodeDefinition.NodeCategory.fromString(id);
                }
            })
            .create();
    private final Path cachePath;
    private CacheState state = new CacheState();

    private NodeRegistryCache() {
        this.cachePath = remotelyDir.resolve("data").resolve("flow").resolve("node_registry_cache.json");
        load();
    }

    public static NodeRegistryCache getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NodeRegistryCache();
        }
        return INSTANCE;
    }

    public synchronized NodeRegistrySnapshot getSnapshot(String serverId) {
        if (serverId == null) {
            return null;
        }
        ServerCache cache = state.servers.get(serverId);
        if (cache == null) {
            return null;
        }
        NodeRegistrySnapshot snapshot = new NodeRegistrySnapshot();
        snapshot.setFullSync(true);
        snapshot.setNodeIds(new ArrayList<>(cache.nodeIds));
        snapshot.setPlugins(new ArrayList<>(cache.plugins.values()));
        snapshot.setRemovedPlugins(new ArrayList<>());
        return snapshot;
    }

    public synchronized Map<String, String> getPluginChecksums(String serverId) {
        Map<String, String> checksums = new HashMap<>();
        if (serverId == null) {
            return checksums;
        }
        ServerCache cache = state.servers.get(serverId);
        if (cache == null) {
            return checksums;
        }
        for (var entry : cache.plugins.entrySet()) {
            String checksum = entry.getValue().getChecksum();
            if (checksum != null) {
                checksums.put(entry.getKey(), checksum);
            }
        }
        return checksums;
    }

    public synchronized void applySnapshot(String serverId, NodeRegistrySnapshot snapshot) {
        if (serverId == null || snapshot == null) {
            return;
        }
        ServerCache cache = state.servers.computeIfAbsent(serverId, ignored -> new ServerCache());
        if (snapshot.isFullSync()) {
            cache.plugins.clear();
            cache.nodeIds.clear();
        }
        if (snapshot.getRemovedPlugins() != null) {
            for (String pluginId : snapshot.getRemovedPlugins()) {
                cache.plugins.remove(pluginId);
            }
        }
        if (snapshot.getPlugins() != null) {
            for (NodePluginPayload payload : snapshot.getPlugins()) {
                if (payload.getPluginId() != null) {
                    cache.plugins.put(payload.getPluginId(), payload);
                }
            }
        }
        if (snapshot.getNodeIds() != null && !snapshot.getNodeIds().isEmpty()) {
            cache.nodeIds = new ArrayList<>(snapshot.getNodeIds());
        }
        cache.updatedAt = System.currentTimeMillis();
        save();
    }

    private void load() {
        if (Files.notExists(cachePath)) {
            return;
        }
        try {
            String json = Files.readString(cachePath);
            CacheState loaded = gson.fromJson(json, CacheState.class);
            if (loaded != null && loaded.servers != null) {
                this.state = loaded;
            }
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("[Flow] Failed to load node registry cache: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(cachePath.getParent());
            Files.writeString(cachePath, gson.toJson(state));
        } catch (IOException e) {
            System.err.println("[Flow] Failed to save node registry cache: " + e.getMessage());
        }
    }

    private static class CacheState {
        private Map<String, ServerCache> servers = new HashMap<>();
    }

    private static class ServerCache {
        private List<String> nodeIds = new ArrayList<>();
        private Map<String, NodePluginPayload> plugins = new HashMap<>();
        private long updatedAt = 0L;
    }
}
