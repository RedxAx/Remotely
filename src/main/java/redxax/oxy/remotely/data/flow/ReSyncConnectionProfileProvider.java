package redxax.oxy.remotely.data.flow;

import java.util.Objects;

@FunctionalInterface
public interface ReSyncConnectionProfileProvider {
    ReSyncConnectionManager.ReSyncConnectionProfile resolve(String serverId, Object server);

    default boolean connectionAllowed(String serverId, Object server) {
        return true;
    }

    default Object findInstance(String serverId, Object server) {
        return null;
    }

    default boolean hasInstanceAccess() {
        return false;
    }

    default boolean isReStudioInstance(Object instance) {
        return false;
    }

    static ReSyncConnectionProfileProvider unavailable() {
        return (serverId, server) -> null;
    }

    static ReSyncConnectionProfileProvider require(ReSyncConnectionProfileProvider provider) {
        return Objects.requireNonNull(provider, "profileProvider");
    }
}
