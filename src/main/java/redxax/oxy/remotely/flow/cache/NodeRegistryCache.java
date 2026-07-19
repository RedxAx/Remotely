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
import redxax.oxy.remotely.flow.sync.FlowCategoryMetadata;
import redxax.oxy.remotely.flow.sync.FlowConversionRule;
import redxax.oxy.remotely.flow.sync.FlowOptionSourceMetadata;
import redxax.oxy.remotely.flow.sync.FlowPropertyMetadata;
import redxax.oxy.remotely.flow.sync.FlowResourceMetadata;
import redxax.oxy.remotely.flow.sync.FlowTypeMetadata;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class NodeRegistryCache {
    private static final int CACHE_SCHEMA_VERSION = 11;
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
        this(remotelyDir.resolve("data").resolve("flow").resolve("node_registry_cache.json"));
    }

    NodeRegistryCache(Path cachePath) {
        this.cachePath = cachePath;
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
        if (cache.contractVersion < NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION
            || cache.contractVersion > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || cache.minimumClientContractVersion > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || cache.compatibleUntil > 0 && cache.compatibleUntil < System.currentTimeMillis()
            || cache.serverIdentity != null && !cache.serverIdentity.isBlank() && !serverId.equals(cache.serverIdentity)) {
            state.servers.remove(serverId);
            state.invalidationReasons.put(serverId, "Cached node registry is incompatible or expired");
            save();
            return null;
        }
        NodeRegistrySnapshot snapshot = new NodeRegistrySnapshot();
        snapshot.setContractVersion(cache.contractVersion);
        snapshot.setMinimumClientContractVersion(cache.minimumClientContractVersion);
        snapshot.setServerIdentity(cache.serverIdentity);
        snapshot.setCompatibleUntil(cache.compatibleUntil);
        snapshot.setCapabilities(new ArrayList<>(cache.capabilities));
        snapshot.setRegistryDiagnostics(cache.registryDiagnostics);
        snapshot.setFullSync(true);
        snapshot.setRegistryChecksum(cache.registryChecksum);
        snapshot.setGeneratedAt(cache.generatedAt);
        snapshot.setNodeIds(new ArrayList<>(cache.nodeIds));
        snapshot.setPlugins(new ArrayList<>(cache.plugins.values()));
        snapshot.setRemovedPlugins(new ArrayList<>());
        snapshot.setPropertyActions(cache.propertyActions);
        snapshot.setPropertyOutputTypes(cache.propertyOutputTypes);
        snapshot.setPropertyMetadata(new ArrayList<>(cache.propertyMetadata));
        snapshot.setResourceMetadata(new ArrayList<>(cache.resourceMetadata));
        snapshot.setTypeMetadata(new ArrayList<>(cache.typeMetadata));
        snapshot.setCategoryMetadata(new ArrayList<>(cache.categoryMetadata));
        snapshot.setOptionSourceMetadata(new ArrayList<>(cache.optionSourceMetadata));
        snapshot.setConversionRules(new ArrayList<>(cache.conversionRules));
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
        if (snapshot.getContractVersion() < NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION
            || snapshot.getContractVersion() > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || snapshot.getMinimumClientContractVersion() > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION) {
            state.invalidationReasons.put(serverId, "Rejected incompatible node registry contract " + snapshot.getContractVersion());
            save();
            return;
        }
        if (!snapshot.getServerIdentity().isBlank() && !serverId.equals(snapshot.getServerIdentity())) {
            state.invalidationReasons.put(serverId, "Rejected node registry snapshot for another server");
            save();
            return;
        }
        ServerCache cache = state.servers.computeIfAbsent(serverId, ignored -> new ServerCache());
        cache.contractVersion = snapshot.getContractVersion();
        cache.minimumClientContractVersion = snapshot.getMinimumClientContractVersion();
        cache.serverIdentity = serverId;
        cache.compatibleUntil = snapshot.getCompatibleUntil();
        cache.capabilities = new ArrayList<>(snapshot.getCapabilities());
        cache.registryDiagnostics = new HashMap<>(snapshot.getRegistryDiagnostics());
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
        if (snapshot.getRegistryChecksum() != null && !snapshot.getRegistryChecksum().isBlank()) {
            cache.registryChecksum = snapshot.getRegistryChecksum();
        }
        if (snapshot.getGeneratedAt() > 0) {
            cache.generatedAt = snapshot.getGeneratedAt();
        }
        if (snapshot.getPropertyActions() != null) {
            cache.propertyActions = snapshot.getPropertyActions();
        }
        if (snapshot.getPropertyOutputTypes() != null) {
            cache.propertyOutputTypes = snapshot.getPropertyOutputTypes();
        }
        if (snapshot.getPropertyMetadata() != null) {
            cache.propertyMetadata = new ArrayList<>(snapshot.getPropertyMetadata());
        }
        if (snapshot.getResourceMetadata() != null) {
            cache.resourceMetadata = new ArrayList<>(snapshot.getResourceMetadata());
        }
        if (snapshot.getTypeMetadata() != null) {
            cache.typeMetadata = new ArrayList<>(snapshot.getTypeMetadata());
        }
        if (snapshot.getCategoryMetadata() != null) {
            cache.categoryMetadata = new ArrayList<>(snapshot.getCategoryMetadata());
        }
        if (snapshot.getOptionSourceMetadata() != null) {
            cache.optionSourceMetadata = new ArrayList<>(snapshot.getOptionSourceMetadata());
        }
        if (snapshot.getConversionRules() != null) {
            cache.conversionRules = new ArrayList<>(snapshot.getConversionRules());
        }
        cache.updatedAt = System.currentTimeMillis();
        state.invalidationReasons.remove(serverId);
        save();
    }

    public synchronized CacheDiagnostic getDiagnostic(String serverId) {
        ServerCache cache = serverId != null ? state.servers.get(serverId) : null;
        String invalidationReason = serverId != null ? state.invalidationReasons.getOrDefault(serverId, state.invalidationReasons.getOrDefault("*", "")) : "";
        if (cache == null) {
            return new CacheDiagnostic(false, 0, 0, 0, "", 0, 0, 0, invalidationReason);
        }
        return new CacheDiagnostic(true, cache.nodeIds.size(), cache.plugins.size(), cache.updatedAt, cache.registryChecksum, cache.generatedAt,
            state.schemaVersion, cache.contractVersion, invalidationReason);
    }

    private void load() {
        if (Files.notExists(cachePath)) {
            return;
        }
        try {
            String json = Files.readString(cachePath);
            CacheState loaded = gson.fromJson(json, CacheState.class);
            if (loaded != null && loaded.servers != null) {
                if (loaded.schemaVersion == CACHE_SCHEMA_VERSION) {
                    this.state = loaded;
                } else {
                    this.state = new CacheState();
                    String reason = "Node registry cache schema changed from " + loaded.schemaVersion + " to " + CACHE_SCHEMA_VERSION;
                    this.state.invalidationReasons.put("*", reason);
                    System.err.println("[Flow] " + reason);
                    save();
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("[Flow] Failed to load node registry cache: " + e.getMessage());
        }
    }

    private void save() {
        Path temporary = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
        try {
            Files.createDirectories(cachePath.getParent());
            Files.writeString(temporary, gson.toJson(state));
            try {
                Files.move(temporary, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[Flow] Failed to save node registry cache: " + e.getMessage());
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private static class CacheState {
        private int schemaVersion = CACHE_SCHEMA_VERSION;
        private Map<String, ServerCache> servers = new HashMap<>();
        private Map<String, String> invalidationReasons = new HashMap<>();
    }

    private static class ServerCache {
        private int contractVersion;
        private int minimumClientContractVersion;
        private String serverIdentity = "";
        private long compatibleUntil;
        private List<String> capabilities = new ArrayList<>();
        private Map<String, Object> registryDiagnostics = new HashMap<>();
        private List<String> nodeIds = new ArrayList<>();
        private Map<String, NodePluginPayload> plugins = new HashMap<>();
        private Map<String, Map<String, List<String>>> propertyActions = new HashMap<>();
        private Map<String, Map<String, FlowDataType>> propertyOutputTypes = new HashMap<>();
        private List<FlowPropertyMetadata> propertyMetadata = new ArrayList<>();
        private List<FlowResourceMetadata> resourceMetadata = new ArrayList<>();
        private List<FlowTypeMetadata> typeMetadata = new ArrayList<>();
        private List<FlowCategoryMetadata> categoryMetadata = new ArrayList<>();
        private List<FlowOptionSourceMetadata> optionSourceMetadata = new ArrayList<>();
        private List<FlowConversionRule> conversionRules = new ArrayList<>();
        private String registryChecksum = "";
        private long generatedAt = 0L;
        private long updatedAt = 0L;
    }

    public record CacheDiagnostic(boolean present, int nodeCount, int pluginCount, long updatedAt, String registryChecksum, long generatedAt,
                                  int schemaVersion, int contractVersion, String invalidationReason) {
    }
}
