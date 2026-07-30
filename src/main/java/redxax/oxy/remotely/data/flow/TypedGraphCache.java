package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.flow.data.FlowGraph;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

final class TypedGraphCache {
    private static final List<ReSyncResourceType> GRAPH_TYPES = List.of(ReSyncResourceType.FUNCTION, ReSyncResourceType.FLOW, ReSyncResourceType.COMMAND);
    private final Map<ReSyncResourceType, SyncedResourceCache<FlowGraph>> stores = new EnumMap<>(ReSyncResourceType.class);

    TypedGraphCache() {
        for (ReSyncResourceType type : GRAPH_TYPES) {
            stores.put(type, new SyncedResourceCache<>(FlowGraph::getId, FlowGraph::getId));
        }
    }

    void clearForServer(String serverId) {
        stores.values().forEach(store -> store.clearForServer(serverId));
    }

    FlowGraph get(String serverId, String resourceId) {
        FlowGraph found = null;
        for (ReSyncResourceType type : GRAPH_TYPES) {
            FlowGraph graph = get(serverId, type, resourceId);
            if (graph == null) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = graph;
        }
        return found;
    }

    FlowGraph get(String serverId, ReSyncResourceType type, String resourceId) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null ? store.get(serverId, resourceId) : null;
    }

    FlowGraph getFromDraft(String serverId, String resourceId) {
        FlowGraph found = null;
        for (ReSyncResourceType type : GRAPH_TYPES) {
            FlowGraph graph = getFromDraft(serverId, type, resourceId);
            if (graph == null) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = graph;
        }
        return found;
    }

    FlowGraph getFromDraft(String serverId, ReSyncResourceType type, String resourceId) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null ? store.getFromDraft(serverId, resourceId) : null;
    }

    void putInDraft(String serverId, FlowGraph graph) {
        store(graph).putInDraft(serverId, graph);
    }

    void cache(String serverId, FlowGraph graph) {
        store(graph).cache(serverId, graph);
    }

    void replaceFromServer(String serverId, FlowGraph graph) {
        store(graph).replaceFromServer(serverId, graph);
    }

    void markSaving(String serverId, ReSyncResourceType type, String resourceId) {
        store(type).markSaving(serverId, resourceId);
    }

    void markSaving(String serverId, String resourceId) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, resourceId);
        if (store != null) {
            store.markSaving(serverId, resourceId);
        }
    }

    void markSaved(String serverId, ReSyncResourceType type, String resourceId) {
        store(type).markSaved(serverId, resourceId);
    }

    void markSaved(String serverId, String resourceId) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, resourceId);
        if (store != null) {
            store.markSaved(serverId, resourceId);
        }
    }

    void markFailed(String serverId, ReSyncResourceType type, String resourceId) {
        store(type).markFailed(serverId, resourceId);
    }

    void markFailed(String serverId, String resourceId) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, resourceId);
        if (store != null) {
            store.markFailed(serverId, resourceId);
        }
    }

    SyncedResourceState getState(String serverId, String resourceId) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, resourceId);
        return store != null ? store.getState(serverId, resourceId) : SyncedResourceState.CLEAN;
    }

    boolean containsServerId(String serverId, String resourceId) {
        return GRAPH_TYPES.stream().anyMatch(type -> store(type).containsServerId(serverId, resourceId));
    }

    boolean containsServerId(String serverId, ReSyncResourceType type, String resourceId) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null && store.containsServerId(serverId, resourceId);
    }

    boolean containsKey(String serverId, ReSyncResourceType type, String resourceId) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null && store.containsKey(serverId, resourceId);
    }

    void putNameIfAbsent(String serverId, String resourceId, String name) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, resourceId);
        if (store != null) {
            store.putNameIfAbsent(serverId, resourceId, name);
        }
    }

    void putNameIfAbsent(String serverId, ReSyncResourceType type, String resourceId, String name) {
        SyncedResourceCache<FlowGraph> store = store(type);
        if (store != null) {
            store.putNameIfAbsent(serverId, resourceId, name);
        }
    }

    SyncedResourceState getState(String serverId, ReSyncResourceType type, String resourceId) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null ? store.getState(serverId, resourceId) : SyncedResourceState.CLEAN;
    }

    void putName(String serverId, String resourceId, String name) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, resourceId);
        if (store != null) {
            store.putName(serverId, resourceId, name);
        }
    }

    void putName(String serverId, ReSyncResourceType type, String resourceId, String name) {
        SyncedResourceCache<FlowGraph> store = store(type);
        if (store != null) {
            store.putName(serverId, resourceId, name);
        }
    }

    String getName(String serverId, String resourceId) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, resourceId);
        return store != null ? store.getName(serverId, resourceId) : resourceId;
    }

    String getName(String serverId, ReSyncResourceType type, String resourceId) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null ? store.getName(serverId, resourceId) : resourceId;
    }

    String resolveDisplayName(String serverId, ReSyncResourceType type, String oldId, String newId) {
        return store(type).resolveDisplayName(serverId, oldId, newId);
    }

    void remove(String serverId, ReSyncResourceType type, String resourceId) {
        SyncedResourceCache<FlowGraph> store = store(type);
        if (store != null) {
            store.remove(serverId, resourceId);
        }
    }

    void remove(String serverId, String resourceId) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, resourceId);
        if (store != null) {
            store.remove(serverId, resourceId);
        }
    }

    boolean rename(String serverId, ReSyncResourceType type, String oldId, String newId, BiConsumer<FlowGraph, String> rename) {
        return store(type).rename(serverId, oldId, newId, rename);
    }

    boolean rename(String serverId, String oldId, String newId, BiConsumer<FlowGraph, String> rename) {
        SyncedResourceCache<FlowGraph> store = uniqueStore(serverId, oldId);
        return store != null && store.rename(serverId, oldId, newId, rename);
    }

    Map<String, FlowGraph> getForServer(String serverId) {
        Map<String, FlowGraph> graphs = new LinkedHashMap<>();
        for (ReSyncResourceType type : GRAPH_TYPES) {
            store(type).getForServer(serverId).forEach(graphs::putIfAbsent);
        }
        return graphs;
    }

    Map<String, FlowGraph> getForServer(String serverId, ReSyncResourceType type) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null ? store.getForServer(serverId) : Map.of();
    }

    List<FlowGraph> valuesForServer(String serverId) {
        return GRAPH_TYPES.stream().flatMap(type -> store(type).getForServer(serverId).values().stream()).toList();
    }

    boolean hasLoadedServerList(String serverId) {
        return GRAPH_TYPES.stream().anyMatch(type -> store(type).hasLoadedServerList(serverId));
    }

    boolean hasLoadedServerList(String serverId, ReSyncResourceType type) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null && store.hasLoadedServerList(serverId);
    }

    List<String> getResourceIds(String serverId, ReSyncResourceType type) {
        SyncedResourceCache<FlowGraph> store = store(type);
        return store != null ? store.getResourceIds(serverId) : List.of();
    }

    void applyServerList(String serverId, ReSyncResourceType type, List<String> ids) {
        SyncedResourceCache<FlowGraph> store = store(type);
        if (store != null) {
            store.applyServerList(serverId, ids);
        }
    }

    private SyncedResourceCache<FlowGraph> uniqueStore(String serverId, String resourceId) {
        SyncedResourceCache<FlowGraph> found = null;
        for (ReSyncResourceType type : GRAPH_TYPES) {
            SyncedResourceCache<FlowGraph> store = store(type);
            if (!store.containsKey(serverId, resourceId)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = store;
        }
        return found;
    }

    private SyncedResourceCache<FlowGraph> store(FlowGraph graph) {
        ReSyncResourceType type = graph != null ? ReSyncResourceType.byTypeId(graph.getResourceType()) : null;
        if (type == null || !type.isGraph()) {
            type = graph != null && graph.isFunction() ? ReSyncResourceType.FUNCTION : ReSyncResourceType.FLOW;
            if (graph != null) {
                graph.setResourceType(type.typeId());
            }
        }
        return store(type);
    }

    private SyncedResourceCache<FlowGraph> store(ReSyncResourceType type) {
        return type != null ? stores.get(type) : null;
    }
}
