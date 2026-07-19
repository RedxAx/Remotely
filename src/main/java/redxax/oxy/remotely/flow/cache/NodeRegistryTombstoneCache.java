package redxax.oxy.remotely.flow.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowDataTypeAdapter;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.sync.NodePluginPayload;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class NodeRegistryTombstoneCache {
    private static final int CACHE_SCHEMA_VERSION = 1;
    private static final NodeRegistryTombstoneCache INSTANCE = new NodeRegistryTombstoneCache();
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(FlowDataType.class, new FlowDataTypeAdapter())
        .registerTypeAdapter(NodeDefinition.NodeCategory.class, new TypeAdapter<NodeDefinition.NodeCategory>() {
            @Override
            public void write(JsonWriter out, NodeDefinition.NodeCategory value) throws IOException {
                out.value(value != null ? value.getId() : null);
            }

            @Override
            public NodeDefinition.NodeCategory read(JsonReader in) throws IOException {
                return NodeDefinition.NodeCategory.fromString(in.nextString());
            }
        })
        .create();
    private final Path cachePath;
    private final Map<String, Map<String, NodePluginPayload>> servers = new HashMap<>();

    private NodeRegistryTombstoneCache() {
        this(remotelyDir.resolve("data").resolve("flow").resolve("node_registry_tombstones.json"));
    }

    NodeRegistryTombstoneCache(Path cachePath) {
        this.cachePath = cachePath;
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
        if (cachePath == null || Files.notExists(cachePath)) {
            return;
        }
        try {
            CacheState state = gson.fromJson(Files.readString(cachePath), CacheState.class);
            if (state == null || state.schemaVersion != CACHE_SCHEMA_VERSION || state.servers == null) {
                return;
            }
            for (Map.Entry<String, Map<String, NodePluginPayload>> entry : state.servers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                Map<String, NodePluginPayload> plugins = new HashMap<>();
                for (Map.Entry<String, NodePluginPayload> plugin : entry.getValue().entrySet()) {
                    if (plugin.getKey() != null && plugin.getValue() != null) {
                        plugins.put(plugin.getKey(), plugin.getValue());
                    }
                }
                if (!plugins.isEmpty()) {
                    servers.put(entry.getKey(), plugins);
                }
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Flow] Failed to load node registry tombstones: " + exception.getMessage());
        }
    }

    private void save() {
        if (cachePath == null) {
            return;
        }
        try {
            Path parent = cachePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(new CacheState(servers)));
            try {
                Files.move(temporary, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Flow] Failed to save node registry tombstones: " + exception.getMessage());
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
