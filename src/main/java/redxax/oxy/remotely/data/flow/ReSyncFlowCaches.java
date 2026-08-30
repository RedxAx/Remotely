package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.flow.cache.NodeRegistryCache;
import redxax.oxy.remotely.flow.cache.NodeRegistryTombstoneCache;

import java.util.Objects;

public record ReSyncFlowCaches(NodeRegistryCache nodeRegistry, NodeRegistryTombstoneCache tombstones,
                               OptionCatalogCache optionCatalogs) {
    public ReSyncFlowCaches {
        nodeRegistry = Objects.requireNonNull(nodeRegistry, "nodeRegistry");
        tombstones = Objects.requireNonNull(tombstones, "tombstones");
        optionCatalogs = Objects.requireNonNull(optionCatalogs, "optionCatalogs");
    }

    public static ReSyncFlowCaches defaults() {
        return new ReSyncFlowCaches(NodeRegistryCache.getInstance(), NodeRegistryTombstoneCache.getInstance(), OptionCatalogCache.getInstance());
    }
}
