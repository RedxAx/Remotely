package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.flow.registry.NodeRegistry;

public record ReSyncFlowClientConfiguration(ReSyncFlowClientFactory factory, ReSyncConnectionProfileProvider profileProvider,
                                             ReSyncConnectionNotificationSink notificationSink, NodeRegistry nodeRegistry,
                                             ReSyncFlowClientContext context) {
    public ReSyncFlowClientConfiguration {
        factory = factory == null ? ReSyncFlowClientFactory.unavailable() : factory;
        profileProvider = profileProvider == null ? ReSyncConnectionProfileProvider.unavailable() : profileProvider;
        notificationSink = notificationSink == null ? ReSyncConnectionNotificationSink.noop() : notificationSink;
        context = context == null ? ReSyncFlowClientContext.defaults() : context;
    }

    public static ReSyncFlowClientConfiguration browser() {
        return new ReSyncFlowClientConfiguration(ReSyncFlowClientFactory.unavailable(), ReSyncConnectionProfileProvider.unavailable(),
            ReSyncConnectionNotificationSink.noop(), new NodeRegistry(), ReSyncFlowClientContext.defaults());
    }
}
