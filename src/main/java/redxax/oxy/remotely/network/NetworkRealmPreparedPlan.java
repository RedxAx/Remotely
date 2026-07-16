package redxax.oxy.remotely.network;

import java.util.List;

public record NetworkRealmPreparedPlan(NetworkDefinition baseNetwork, List<SyncRealm> realms, NetworkPreparedPlan prepared) {
    public NetworkRealmPreparedPlan {
        if (baseNetwork == null || prepared == null) {
            throw new IllegalArgumentException("Realm Review Requires A Network And Prepared Plan");
        }
        realms = realms == null ? List.of() : List.copyOf(realms);
    }
}
