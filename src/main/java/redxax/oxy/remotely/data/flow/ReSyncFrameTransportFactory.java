package redxax.oxy.remotely.data.flow;

@FunctionalInterface
public interface ReSyncFrameTransportFactory {
    ReSyncFrameTransport create(String endpoint);
}
