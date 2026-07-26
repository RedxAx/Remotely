package redxax.oxy.remotely.flow.registry;

import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowTypeRef;
import redxax.oxy.remotely.flow.sync.*;
import restudio.resync.flow.contract.FlowCategoryMetadata;
import restudio.resync.flow.contract.FlowTypeMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class NodeRegistry {
    private static final String DEFAULT_SERVER_KEY = "local";
    private final Map<String, NodeDefinition> localDefinitions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, NodeDefinition>> serverDefinitions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, NodePluginPayload>> serverPlugins = new ConcurrentHashMap<>();
    private final Map<String, Map<String, NodePluginPayload>> serverUnresolvedPlugins = new ConcurrentHashMap<>();
    private final Map<String, List<String>> serverNodeIds = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, List<String>>>> serverPropertyActions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, FlowDataType>>> serverPropertyOutputTypes = new ConcurrentHashMap<>();
    private final Map<String, List<FlowPropertyMetadata>> serverPropertyMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<FlowResourceMetadata>> serverResourceMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<FlowTypeMetadata>> serverTypeMetadata = new ConcurrentHashMap<>();
    private final Map<String, Map<String, FlowDataType>> serverDataTypes = new ConcurrentHashMap<>();
    private final Map<String, List<FlowCategoryMetadata>> serverCategoryMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<FlowOptionSourceMetadata>> serverOptionSourceMetadata = new ConcurrentHashMap<>();
    private final Map<String, List<FlowConversionRule>> serverConversionRules = new ConcurrentHashMap<>();
    private final Map<String, RegistrySessionMetadata> serverRegistrySessions = new ConcurrentHashMap<>();
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
        return getUnresolvedDefinition(serverId, nodeId);
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

    public boolean applySnapshot(String serverId, NodeRegistrySnapshot snapshot) {
        if (serverId == null || snapshot == null) {
            return false;
        }
        if (snapshot.getContractVersion() < NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION
            || snapshot.getContractVersion() > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || snapshot.getMinimumClientContractVersion() > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || !snapshot.getServerIdentity().isBlank() && !serverId.equals(snapshot.getServerIdentity())
            || snapshot.getCompatibleUntil() > 0 && snapshot.getCompatibleUntil() < System.currentTimeMillis()) {
            return false;
        }
        String key = normalizeServerId(serverId);
        RegistrySessionMetadata currentSession = serverRegistrySessions.get(key);
        String currentChecksum = currentSession != null ? currentSession.checksum() : "";
        if (!snapshot.canApplyTo(currentChecksum)) {
            return false;
        }
        Map<String, NodePluginPayload> previousPlugins = new HashMap<>(serverPlugins.getOrDefault(key, Map.of()));
        Map<String, NodePluginPayload> unresolvedPlugins = serverUnresolvedPlugins.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        serverRegistrySessions.put(key, new RegistrySessionMetadata(snapshot.getContractVersion(), snapshot.getMinimumClientContractVersion(),
            serverId, snapshot.getRegistryChecksum(), snapshot.getGeneratedAt(), snapshot.getCompatibleUntil(), snapshot.getCapabilities(),
            snapshot.getRegistryDiagnostics(), snapshot.isFullSync()));
        if (snapshot.isFullSync()) {
            serverPlugins.remove(key);
            serverNodeIds.remove(key);
            Set<String> incomingPluginIds = snapshot.getPlugins().stream().filter(payload -> payload != null && payload.getPluginId() != null)
                .map(NodePluginPayload::getPluginId).collect(Collectors.toSet());
            for (Map.Entry<String, NodePluginPayload> entry : previousPlugins.entrySet()) {
                if (!incomingPluginIds.contains(entry.getKey())) {
                    unresolvedPlugins.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Map<String, NodePluginPayload> pluginMap = serverPlugins.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());

        if (snapshot.getRemovedPlugins() != null) {
            for (String pluginId : snapshot.getRemovedPlugins()) {
                NodePluginPayload removed = pluginMap.remove(pluginId);
                if (removed != null) {
                    unresolvedPlugins.put(pluginId, removed);
                }
            }
        }

        if (snapshot.getPlugins() != null) {
            for (NodePluginPayload payload : snapshot.getPlugins()) {
                if (payload != null && payload.getPluginId() != null) {
                    pluginMap.put(payload.getPluginId(), payload);
                    unresolvedPlugins.remove(payload.getPluginId());
                }
            }
        }

        if (snapshot.getNodeIds() != null) {
            serverNodeIds.put(key, new ArrayList<>(snapshot.getNodeIds()));
        }

        if (snapshot.getPropertyActions() != null) {
            serverPropertyActions.put(key, snapshot.getPropertyActions());
        }
        if (snapshot.getPropertyOutputTypes() != null) {
            serverPropertyOutputTypes.put(key, snapshot.getPropertyOutputTypes());
        }
        if (snapshot.getPropertyMetadata() != null) {
            serverPropertyMetadata.put(key, new ArrayList<>(snapshot.getPropertyMetadata()));
        }
        if (snapshot.getResourceMetadata() != null) {
            serverResourceMetadata.put(key, new ArrayList<>(snapshot.getResourceMetadata()));
        }

        if (snapshot.getTypeMetadata() != null) {
            serverTypeMetadata.put(key, new ArrayList<>(snapshot.getTypeMetadata()));
            serverDataTypes.put(key, buildServerDataTypes(key, snapshot.getTypeMetadata()));
        }
        if (snapshot.getCategoryMetadata() != null) {
            serverCategoryMetadata.put(key, new ArrayList<>(snapshot.getCategoryMetadata()));
        }
        if (snapshot.getOptionSourceMetadata() != null) {
            serverOptionSourceMetadata.put(key, new ArrayList<>(snapshot.getOptionSourceMetadata()));
        }
        if (snapshot.getConversionRules() != null) {
            serverConversionRules.put(key, new ArrayList<>(snapshot.getConversionRules()));
        }

        rebuildServerDefinitions(key);
        notifyListeners(serverId);
        return true;
    }

    public void clearServer(String serverId) {
        String key = normalizeServerId(serverId);
        serverPlugins.remove(key);
        serverUnresolvedPlugins.remove(key);
        serverNodeIds.remove(key);
        serverDefinitions.remove(key);
        serverPropertyActions.remove(key);
        serverPropertyOutputTypes.remove(key);
        serverPropertyMetadata.remove(key);
        serverResourceMetadata.remove(key);
        serverTypeMetadata.remove(key);
        serverDataTypes.remove(key);
        serverCategoryMetadata.remove(key);
        serverOptionSourceMetadata.remove(key);
        serverConversionRules.remove(key);
        serverRegistrySessions.remove(key);
        notifyListeners(serverId);
    }

    public RegistrySessionMetadata getRegistrySessionMetadata(String serverId) {
        return serverRegistrySessions.get(normalizeServerId(serverId));
    }

    public record RegistrySessionMetadata(int contractVersion, int minimumClientContractVersion, String serverIdentity,
                                          String checksum, long generatedAt, long compatibleUntil, List<String> capabilities,
                                          Map<String, Object> diagnostics, boolean fullSync) {
        public RegistrySessionMetadata {
            serverIdentity = serverIdentity != null ? serverIdentity : "";
            checksum = checksum != null ? checksum : "";
            capabilities = capabilities != null ? List.copyOf(capabilities) : List.of();
            diagnostics = diagnostics != null ? Map.copyOf(diagnostics) : Map.of();
        }
    }

    public List<FlowCategoryMetadata> getServerCategories(String serverId) {
        String key = normalizeServerId(serverId);
        List<FlowCategoryMetadata> meta = serverCategoryMetadata.get(key);
        if (meta != null && !meta.isEmpty()) {
            return meta;
        }
        return NodeDefinition.NodeCategory.values().stream()
                .map(cat -> fallbackCategoryMetadata(cat))
                .toList();
    }

    private FlowCategoryMetadata fallbackCategoryMetadata(NodeDefinition.NodeCategory category) {
        String id = category.getId();
        if (List.of("logic", "data", "variable", "flow", "function", "utility").contains(id)) {
            return new FlowCategoryMetadata(id, category.getDisplayName(), category.getColor(), category.getPriority(), "flow", "Flow", 0xFF55FFFF, 100);
        }
        if (List.of("event", "action", "player", "entity", "block", "world", "inventory", "item", "visual", "world_gen").contains(id)) {
            return new FlowCategoryMetadata(id, category.getDisplayName(), category.getColor(), category.getPriority(), "minecraft", "Minecraft", 0xFF55AA55, 200);
        }
        if (List.of("command", "network", "chat", "scoreboard", "trade", "npc", "loot", "menu", "tab_list", "dialog", "custom_content", "recipe", "advancement", "text", "permission", "ability").contains(id)) {
            return new FlowCategoryMetadata(id, category.getDisplayName(), category.getColor(), category.getPriority(), "resync", "ReSync", 0xFF5CC8FF, 300);
        }
        return new FlowCategoryMetadata(id, category.getDisplayName(), category.getColor(), category.getPriority(), "integrations", "Integrations", 0xFF7289DA, 400);
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
            FlowDataType type = resolveType(key, meta.getId());
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
        FlowDataType resolvedSource = resolveType(serverId, source.getId());
        FlowDataType resolvedTarget = resolveType(serverId, target.getId());
        if (resolvedSource.canConvertTo(resolvedTarget)) {
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
                    && (rule.getAvailability() == null || rule.getAvailability().isBlank() || "available".equalsIgnoreCase(rule.getAvailability()))
                    && rule.getSourceTypeId().equalsIgnoreCase(sourceId)
                    && rule.getTargetTypeId().equalsIgnoreCase(targetId)) {
                return true;
            }
        }
        return false;
    }

    public FlowConversionRule findConversionRule(String serverId, FlowDataType source, FlowDataType target) {
        if (source == null || target == null) {
            return null;
        }
        List<FlowConversionRule> rules = serverConversionRules.get(normalizeServerId(serverId));
        if (rules == null) {
            return null;
        }
        return rules.stream()
            .filter(rule -> rule != null
                && rule.getSourceTypeId() != null
                && rule.getTargetTypeId() != null
                && (rule.getAvailability() == null || rule.getAvailability().isBlank() || "available".equalsIgnoreCase(rule.getAvailability()))
                && rule.getSourceTypeId().equalsIgnoreCase(source.getId())
                && rule.getTargetTypeId().equalsIgnoreCase(target.getId()))
            .min(Comparator.comparingInt(FlowConversionRule::getCost))
            .orElse(null);
    }

    public List<FlowOptionSourceMetadata> getServerOptionSources(String serverId) {
        List<FlowOptionSourceMetadata> metadata = serverOptionSourceMetadata.get(normalizeServerId(serverId));
        return metadata != null ? List.copyOf(metadata) : List.of();
    }

    public List<FlowConversionRule> getServerConversionRules(String serverId) {
        List<FlowConversionRule> rules = serverConversionRules.get(normalizeServerId(serverId));
        return rules != null ? List.copyOf(rules) : List.of();
    }

    public List<String> getServerPluginIds(String serverId) {
        Map<String, NodePluginPayload> plugins = serverPlugins.get(normalizeServerId(serverId));
        return plugins != null ? plugins.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList() : List.of();
    }

    public List<String> getUnresolvedPluginIds(String serverId) {
        Map<String, NodePluginPayload> plugins = serverUnresolvedPlugins.get(normalizeServerId(serverId));
        return plugins != null ? plugins.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList() : List.of();
    }

    public List<NodePluginPayload> getUnresolvedPluginPayloads(String serverId) {
        Map<String, NodePluginPayload> plugins = serverUnresolvedPlugins.get(normalizeServerId(serverId));
        return plugins != null ? plugins.values().stream().filter(payload -> payload != null)
            .sorted(Comparator.comparing(NodePluginPayload::getPluginId, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))).toList() : List.of();
    }

    public void restoreUnresolvedPlugins(String serverId, List<NodePluginPayload> payloads) {
        if (serverId == null || payloads == null || payloads.isEmpty()) {
            return;
        }
        String key = normalizeServerId(serverId);
        Map<String, NodePluginPayload> active = serverPlugins.getOrDefault(key, Map.of());
        Map<String, NodePluginPayload> unresolved = serverUnresolvedPlugins.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        for (NodePluginPayload payload : payloads) {
            if (payload != null && payload.getPluginId() != null && !active.containsKey(payload.getPluginId())) {
                unresolved.put(payload.getPluginId(), payload);
            }
        }
    }

    public NodeDefinition getUnresolvedDefinition(String serverId, String nodeId) {
        if (nodeId == null) {
            return null;
        }
        Map<String, NodePluginPayload> plugins = serverUnresolvedPlugins.get(normalizeServerId(serverId));
        if (plugins == null) {
            return null;
        }
        for (NodePluginPayload payload : plugins.values()) {
            if (payload == null || payload.getNodes() == null) {
                continue;
            }
            for (NodeDefinition definition : payload.getNodes()) {
                if (definition != null && nodeId.equals(definition.getId())) {
                    return definition;
                }
            }
        }
        return null;
    }

    public NodeRegistrySnapshot materializeSnapshot(String serverId, NodeRegistrySnapshot source) {
        if (serverId == null || source == null) {
            return null;
        }
        String key = normalizeServerId(serverId);
        NodeRegistrySnapshot snapshot = new NodeRegistrySnapshot();
        snapshot.setContractVersion(source.getContractVersion());
        snapshot.setMinimumClientContractVersion(source.getMinimumClientContractVersion());
        snapshot.setServerIdentity(serverId);
        snapshot.setCompatibleUntil(source.getCompatibleUntil());
        snapshot.setCapabilities(source.getCapabilities());
        snapshot.setRegistryDiagnostics(source.getRegistryDiagnostics());
        snapshot.setFullSync(true);
        snapshot.setRegistryChecksum(source.getRegistryChecksum());
        snapshot.setGeneratedAt(source.getGeneratedAt());
        snapshot.setNodeIds(new ArrayList<>(serverNodeIds.getOrDefault(key, List.of())));
        snapshot.setPlugins(serverPlugins.getOrDefault(key, Map.of()).values().stream()
            .filter(payload -> payload != null).sorted(Comparator.comparing(NodePluginPayload::getPluginId, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))).toList());
        snapshot.setRemovedPlugins(List.of());
        snapshot.setPropertyActions(serverPropertyActions.getOrDefault(key, Map.of()));
        snapshot.setPropertyOutputTypes(serverPropertyOutputTypes.getOrDefault(key, Map.of()));
        snapshot.setPropertyMetadata(new ArrayList<>(serverPropertyMetadata.getOrDefault(key, List.of())));
        snapshot.setResourceMetadata(new ArrayList<>(serverResourceMetadata.getOrDefault(key, List.of())));
        snapshot.setTypeMetadata(new ArrayList<>(serverTypeMetadata.getOrDefault(key, List.of())));
        snapshot.setCategoryMetadata(new ArrayList<>(serverCategoryMetadata.getOrDefault(key, List.of())));
        snapshot.setOptionSourceMetadata(new ArrayList<>(serverOptionSourceMetadata.getOrDefault(key, List.of())));
        snapshot.setConversionRules(new ArrayList<>(serverConversionRules.getOrDefault(key, List.of())));
        return snapshot;
    }

    public boolean canAssignTypes(String serverId, FlowTypeRef source, FlowTypeRef target) {
        if (source == null || target == null) {
            return false;
        }
        if (source.isTypeVariable() || target.isTypeVariable()) {
            return true;
        }
        FlowDataType sourceType = resolveType(serverId, source.getTypeId());
        FlowDataType targetType = resolveType(serverId, target.getTypeId());
        if (!targetType.isAssignableFrom(sourceType)) {
            return false;
        }
        if ("resource_reference".equals(target.getTypeId())) {
            return target.getArguments().isEmpty() || source.getArguments().size() == 1
                && target.getArguments().getFirst().getTypeId().equalsIgnoreCase(source.getArguments().getFirst().getTypeId());
        }
        if (target.getArguments().isEmpty()) {
            return true;
        }
        if (target.getArguments().size() != source.getArguments().size()) {
            return false;
        }
        for (int index = 0; index < target.getArguments().size(); index++) {
            if (!canAssignTypes(serverId, source.getArguments().get(index), target.getArguments().get(index))) {
                return false;
            }
        }
        return true;
    }

    public FlowDataType resolveType(String serverId, String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return FlowDataType.ANY;
        }
        String key = normalizeServerId(serverId);
        Map<String, FlowDataType> types = serverDataTypes.get(key);
        FlowDataType type = types != null ? types.get(typeId.toLowerCase()) : null;
        return type != null ? type : FlowDataType.fromString(typeId);
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
        return type != null ? resolveType(key, type.getId()) : FlowDataType.ANY;
    }

    public FlowPropertyMetadata getPropertyMetadata(String serverId, String family, String property) {
        if (family == null || property == null) {
            return null;
        }
        List<FlowPropertyMetadata> metadata = serverPropertyMetadata.get(normalizeServerId(serverId));
        if (metadata == null) {
            return null;
        }
        return metadata.stream()
            .filter(value -> value != null && family.equalsIgnoreCase(value.getFamily()) && property.equalsIgnoreCase(value.getProperty()))
            .findFirst()
            .orElse(null);
    }

    public List<FlowResourceMetadata> getResourceMetadata(String serverId) {
        List<FlowResourceMetadata> metadata = serverResourceMetadata.get(normalizeServerId(serverId));
        return metadata != null ? List.copyOf(metadata) : List.of();
    }

    public FlowResourceMetadata getResourceMetadata(String serverId, String typeId) {
        if (typeId == null) {
            return null;
        }
        return getResourceMetadata(serverId).stream()
            .filter(value -> value != null && typeId.equalsIgnoreCase(value.getTypeId()))
            .findFirst()
            .orElse(null);
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
                    ReLog.logger(LogTypes.FLOW).source(LogSource.resource(nodeId, nodeId)).component(NodeRegistry.class).debug("Node definition is unavailable");
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

    private Map<String, FlowDataType> buildServerDataTypes(String serverId, List<FlowTypeMetadata> metadata) {
        Map<String, FlowTypeMetadata> descriptors = new LinkedHashMap<>();
        for (FlowTypeMetadata descriptor : metadata) {
            if (descriptor != null && descriptor.getId() != null && !descriptor.getId().isBlank()) {
                descriptors.put(descriptor.getId().toLowerCase(), descriptor);
            }
        }
        Map<String, FlowDataType> types = new LinkedHashMap<>();
        for (String typeId : descriptors.keySet()) {
            resolveServerType(serverId, typeId, descriptors, types, new HashSet<>());
        }
        return Map.copyOf(types);
    }

    private FlowDataType resolveServerType(String serverId, String typeId, Map<String, FlowTypeMetadata> descriptors,
                                           Map<String, FlowDataType> types, Set<String> resolving) {
        String normalized = typeId.toLowerCase();
        FlowDataType resolved = types.get(normalized);
        if (resolved != null) {
            return resolved;
        }
        FlowDataType builtin = FlowDataType.fromString(normalized);
        if (builtin.isResolved() && "builtin".equals(builtin.getOwner())) {
            types.put(normalized, builtin);
            return builtin;
        }
        FlowTypeMetadata metadata = descriptors.get(normalized);
        if (metadata == null || !resolving.add(normalized)) {
            return builtin;
        }
        if (!metadata.isAvailable()) {
            types.put(normalized, builtin);
            resolving.remove(normalized);
            return builtin;
        }
        FlowDataType parent = null;
        if (metadata.getParentId() != null && !metadata.getParentId().isBlank()) {
            parent = resolveServerType(serverId, metadata.getParentId(), descriptors, types, resolving);
        }
        FlowDataType type = FlowDataType.serverType(metadata.getId(), metadata.getDisplayName(), metadata.getColor(), parent,
            metadata.isCanStringify(), metadata.getOwner() != null && !metadata.getOwner().isBlank() ? metadata.getOwner() : serverId);
        resolving.remove(normalized);
        types.put(normalized, type);
        return type;
    }

    private String normalizeServerId(String serverId) {
        return serverId != null ? serverId : DEFAULT_SERVER_KEY;
    }
}
