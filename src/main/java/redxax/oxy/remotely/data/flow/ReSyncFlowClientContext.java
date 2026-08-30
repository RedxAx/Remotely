package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.flow.registry.NodeRegistry;

public record ReSyncFlowClientContext(ReSyncFlowClientState state, ReSyncFlowCaches caches, NodeRegistry nodeRegistry,
                                      ReSyncWorldGenerationState worldGeneration, ReSyncLuckPermsProvider luckPermsProvider) {
    public ReSyncFlowClientContext(ReSyncFlowClientState state, ReSyncFlowCaches caches, NodeRegistry nodeRegistry,
                                   ReSyncWorldGenerationState worldGeneration) {
        this(state, caches, nodeRegistry, worldGeneration, ReSyncLuckPermsProvider.unavailable());
    }

    public ReSyncFlowClientContext {
        state = state == null ? ReSyncFlowClientState.noop() : state;
        caches = caches == null ? ReSyncFlowCaches.defaults() : caches;
        nodeRegistry = nodeRegistry == null ? new NodeRegistry() : nodeRegistry;
        worldGeneration = worldGeneration == null ? ReSyncWorldGenerationState.noop() : worldGeneration;
        luckPermsProvider = luckPermsProvider == null ? ReSyncLuckPermsProvider.unavailable() : luckPermsProvider;
    }

    public static ReSyncFlowClientContext defaults() {
        return new ReSyncFlowClientContext(ReSyncFlowClientState.noop(), ReSyncFlowCaches.defaults(), new NodeRegistry(),
            ReSyncWorldGenerationState.noop());
    }

    public ReSyncFlowClientContext withLuckPermsProvider(ReSyncLuckPermsProvider provider) {
        return new ReSyncFlowClientContext(state, caches, nodeRegistry, worldGeneration, provider);
    }
}
