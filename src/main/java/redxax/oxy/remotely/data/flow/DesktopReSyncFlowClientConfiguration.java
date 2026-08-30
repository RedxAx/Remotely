package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.DesktopRemotelyPaths;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsClient;
import redxax.oxy.remotely.data.integrations.luckperms.DesktopReSyncLuckPermsNetworkEnvironment;
import redxax.oxy.remotely.data.integrations.luckperms.DesktopReSyncLuckPermsCodec;
import redxax.oxy.remotely.flow.cache.NodeRegistryCache;
import redxax.oxy.remotely.flow.cache.NodeRegistryTombstoneCache;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.rebase.platform.jvm.JvmClock;

import java.nio.file.Path;

public final class DesktopReSyncFlowClientConfiguration {
    private DesktopReSyncFlowClientConfiguration() {
    }

    public static ReSyncFlowClientConfiguration create(RemotelyClient client, FlowManager manager,
                                                       ReSyncFlowClientFactory requestedFactory) {
        ReSyncFlowClientFactory factory = requestedFactory == null ? DesktopReSyncFlowClientFactory.create() : requestedFactory;
        ReSyncLuckPermsClientHolder holder = new ReSyncLuckPermsClientHolder(manager);
        DesktopReSyncFlowClientState state = new DesktopReSyncFlowClientState(manager, holder);
        Path flowDirectory = DesktopRemotelyPaths.appDir().resolve("data").resolve("flow");
        JvmClock clock = new JvmClock();
        ReSyncFlowCaches caches = new ReSyncFlowCaches(
            new NodeRegistryCache(DesktopReSyncStorage.fromKey(flowDirectory.resolve("node_registry_cache.json")), clock),
            new NodeRegistryTombstoneCache(DesktopReSyncStorage.fromKey(flowDirectory.resolve("node_registry_tombstones.json"))),
            OptionCatalogCache.getInstance());
        ReSyncFlowClientContext context = new ReSyncFlowClientContext(state, caches, new NodeRegistry(clock), state);
        return new ReSyncFlowClientConfiguration(factory, new DesktopReSyncConnectionProfileProvider(client),
            new DesktopReSyncConnectionNotificationSink(), context.nodeRegistry(), context);
    }

    private static final class ReSyncLuckPermsClientHolder implements DesktopReSyncFlowClientState.ReSyncLuckPermsHolder {
        private final FlowManager manager;
        private ReSyncLuckPermsClient value;

        private ReSyncLuckPermsClientHolder(FlowManager manager) {
            this.manager = manager;
        }

        @Override
        public Object get(ReSyncFlowClient client) {
            if (value == null) {
                value = new ReSyncLuckPermsClient(client, new DesktopReSyncLuckPermsNetworkEnvironment(manager), new DesktopReSyncLuckPermsCodec());
            }
            return value;
        }

        @Override
        public void close(ReSyncFlowClient client) {
            if (value != null) {
                value.close();
            }
        }
    }
}
