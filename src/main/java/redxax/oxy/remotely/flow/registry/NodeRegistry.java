package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.sync.*;

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
    private final Map<String, NodeDefinition> localDefinitions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, NodeDefinition>> serverDefinitions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, NodePluginPayload>> serverPlugins = new ConcurrentHashMap<>();
    private final Map<String, List<String>> serverNodeIds = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, List<String>>>> serverPropertyActions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, FlowDataType>>> serverPropertyOutputTypes = new ConcurrentHashMap<>();
    private final Map<String, List<redxax.oxy.remotely.flow.sync.FlowTypeMetadata>> serverTypeMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<redxax.oxy.remotely.flow.sync.FlowCategoryMetadata>> serverCategoryMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<redxax.oxy.remotely.flow.sync.FlowOptionSourceMetadata>> serverOptionSourceMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<redxax.oxy.remotely.flow.sync.FlowConversionRule>> serverConversionRules = new ConcurrentHashMap<>();
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

    public void registerServerDefinition(String serverId, NodeDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return;
        }
        String key = normalizeServerId(serverId);
        serverDefinitions.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(definition.getId(), definition);
    }

    public void unregisterServerDefinition(String serverId, String definitionId) {
        if (definitionId == null) {
            return;
        }
        String key = normalizeServerId(serverId);
        Map<String, NodeDefinition> defs = serverDefinitions.get(key);
        if (defs != null) {
            defs.remove(definitionId);
        }
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

        if (snapshot.getPropertyActions() != null) {
            serverPropertyActions.put(key, snapshot.getPropertyActions());
        }
        if (snapshot.getPropertyOutputTypes() != null) {
            serverPropertyOutputTypes.put(key, snapshot.getPropertyOutputTypes());
        }

        if (snapshot.getTypeMetadata() != null) {
            serverTypeMetadata.put(key, new ArrayList<>(snapshot.getTypeMetadata()));
            for (FlowTypeMetadata meta : snapshot.getTypeMetadata()) {
                FlowDataType.registerServerType(meta.getId(), meta.getDisplayName(), meta.getColor(), meta.getParentId(), meta.isCanStringify());
            }
        }
        if (snapshot.getCategoryMetadata() != null) {
            serverCategoryMetadata.put(key, new ArrayList<>(snapshot.getCategoryMetadata()));
            for (redxax.oxy.remotely.flow.sync.FlowCategoryMetadata meta : snapshot.getCategoryMetadata()) {
                NodeDefinition.NodeCategory.registerServerCategory(meta.getId(), meta.getDisplayName(), meta.getColor(), meta.getPriority());
            }
        }
        if (snapshot.getOptionSourceMetadata() != null) {
            serverOptionSourceMetadata.put(key, new ArrayList<>(snapshot.getOptionSourceMetadata()));
        }
        if (snapshot.getConversionRules() != null) {
            serverConversionRules.put(key, new ArrayList<>(snapshot.getConversionRules()));
        }

        rebuildServerDefinitions(key);
        notifyListeners(serverId);
    }

    public void clearServer(String serverId) {
        String key = normalizeServerId(serverId);
        serverPlugins.remove(key);
        serverNodeIds.remove(key);
        serverDefinitions.remove(key);
        serverPropertyActions.remove(key);
        serverPropertyOutputTypes.remove(key);
        serverTypeMetadata.remove(key);
        serverCategoryMetadata.remove(key);
        serverOptionSourceMetadata.remove(key);
        serverConversionRules.remove(key);
        notifyListeners(serverId);
    }

    public List<FlowCategoryMetadata> getServerCategories(String serverId) {
        String key = normalizeServerId(serverId);
        List<FlowCategoryMetadata> meta = serverCategoryMetadata.get(key);
        if (meta != null && !meta.isEmpty()) {
            return meta;
        }
        return NodeDefinition.NodeCategory.values().stream()
                .map(cat -> new FlowCategoryMetadata(cat.getId(), cat.getDisplayName(), cat.getColor(), cat.getPriority()))
                .toList();
    }

    public FlowOptionSourceMetadata getServerOptionSource(String serverId, String sourceId) {
        if (sourceId == null) {
            return null;
        }
        String key = normalizeServerId(serverId);
        List<FlowOptionSourceMetadata> list = serverOptionSourceMetadata.get(key);
        if (list == null) {
            return null;
        }
        for (FlowOptionSourceMetadata meta : list) {
            if (meta != null && meta.getId() != null && meta.getId().equalsIgnoreCase(sourceId)) {
                return meta;
            }
        }
        return null;
    }

    public FlowTypeMetadata getTypeMetadata(String serverId, String typeId) {
        if (typeId == null) {
            return null;
        }
        String key = normalizeServerId(serverId);
        List<FlowTypeMetadata> list = serverTypeMetadata.get(key);
        if (list == null) {
            return null;
        }
        for (FlowTypeMetadata meta : list) {
            if (meta != null && meta.getId() != null && meta.getId().equalsIgnoreCase(typeId)) {
                return meta;
            }
        }
        return null;
    }

    public List<FlowDataType> getServerDataTypes(String serverId) {
        String key = normalizeServerId(serverId);
        List<FlowTypeMetadata> list = serverTypeMetadata.get(key);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<FlowDataType> types = new ArrayList<>();
        for (FlowTypeMetadata meta : list) {
            if (meta == null || meta.getId() == null || meta.getId().isBlank()) {
                continue;
            }
            FlowDataType type = FlowDataType.fromString(meta.getId());
            if (type != FlowDataType.EXECUTION) {
                types.add(type);
            }
        }
        return types;
    }

    public boolean canConvertTypes(String serverId, FlowDataType source, FlowDataType target) {
        if (source == null || target == null) {
            return false;
        }
        if (source.canConvertTo(target)) {
            return true;
        }
        String key = normalizeServerId(serverId);
        List<FlowConversionRule> rules = serverConversionRules.get(key);
        if (rules == null) {
            return false;
        }
        String sourceId = source.getId();
        String targetId = target.getId();
        for (FlowConversionRule rule : rules) {
            if (rule != null
                    && rule.getSourceTypeId() != null
                    && rule.getTargetTypeId() != null
                    && rule.getSourceTypeId().equalsIgnoreCase(sourceId)
                    && rule.getTargetTypeId().equalsIgnoreCase(targetId)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getPropertyActions(String serverId, String family, String property) {
        String key = normalizeServerId(serverId);
        Map<String, Map<String, List<String>>> families = serverPropertyActions.get(key);
        if (families == null) {
            return List.of();
        }
        Map<String, List<String>> properties = families.get(family);
        if (properties == null) {
            return List.of();
        }
        List<String> actions = properties.get(property);
        return actions != null ? actions : List.of();
    }

    public FlowDataType getPropertyOutputType(String serverId, String family, String property) {
        String key = normalizeServerId(serverId);
        Map<String, Map<String, FlowDataType>> families = serverPropertyOutputTypes.get(key);
        if (families == null) {
            return FlowDataType.ANY;
        }
        Map<String, FlowDataType> properties = families.get(family);
        if (properties == null) {
            return FlowDataType.ANY;
        }
        FlowDataType type = properties.get(property);
        return type != null ? type : FlowDataType.ANY;
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
        Map<String, NodeDefinition> definitions = new ConcurrentHashMap<>();
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
