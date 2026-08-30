package redxax.oxy.remotely.worldgen.registry;

import redxax.oxy.remotely.util.BrowserSafeState;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorldGenNodeRegistry {
    private static final WorldGenNodeRegistry INSTANCE = new WorldGenNodeRegistry();
    private final Map<String, Map<String, WorldGenNodeDefinition>> definitions = BrowserSafeState.map();

    public static WorldGenNodeRegistry getInstance() {
        return INSTANCE;
    }

    public void register(String serverId, WorldGenNodeDefinition definition) {
        definitions.computeIfAbsent(serverId, id -> BrowserSafeState.map()).put(definition.getId(), definition);
    }

    public void replaceDefinitions(String serverId, Collection<WorldGenNodeDefinition> newDefinitions) {
        Map<String, WorldGenNodeDefinition> serverDefinitions = new LinkedHashMap<>();
        if (newDefinitions != null) {
            for (WorldGenNodeDefinition definition : newDefinitions) {
                if (definition != null && definition.getId() != null) {
                    serverDefinitions.put(definition.getId(), definition);
                }
            }
        }
        definitions.put(serverId, Map.copyOf(serverDefinitions));
    }

    public WorldGenNodeDefinition getDefinition(String serverId, String nodeId) {
        Map<String, WorldGenNodeDefinition> serverDefinitions = definitions.get(serverId);
        return serverDefinitions != null ? serverDefinitions.get(nodeId) : null;
    }

    public Collection<WorldGenNodeDefinition> getAllDefinitions(String serverId) {
        Map<String, WorldGenNodeDefinition> serverDefinitions = definitions.get(serverId);
        if (serverDefinitions == null) return List.of();
        return serverDefinitions.values().stream().sorted(Comparator.comparingInt(WorldGenNodeDefinition::getPriority)).toList();
    }

    public boolean hasDefinitions(String serverId) {
        Map<String, WorldGenNodeDefinition> serverDefinitions = definitions.get(serverId);
        return serverDefinitions != null && !serverDefinitions.isEmpty();
    }
}
