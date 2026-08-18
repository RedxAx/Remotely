package redxax.oxy.remotely.data.flow;

import java.util.Objects;

@FunctionalInterface
public interface ReSyncConnectionProfileProvider {
    ReSyncConnectionManager.ReSyncConnectionProfile resolve(ReSyncServerIdentity identity);

    default boolean connectionAllowed(ReSyncServerIdentity identity, ReSyncConnectionManager.ReSyncConnectionProfile profile) {
        return true;
    }

    default boolean connectionPending(ReSyncServerIdentity identity) {
        return false;
    }

    default Object findInstance(ReSyncServerIdentity identity) {
        return null;
    }

    default boolean hasInstanceAccess() {
        return false;
    }

    static ReSyncConnectionProfileProvider unavailable() {
        return identity -> null;
    }

    static ReSyncConnectionProfileProvider require(ReSyncConnectionProfileProvider provider) {
        return Objects.requireNonNull(provider, "profileProvider");
    }
}
