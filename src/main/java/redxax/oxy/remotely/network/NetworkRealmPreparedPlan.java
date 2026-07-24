package redxax.oxy.remotely.network;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record NetworkRealmPreparedPlan(NetworkDefinition baseNetwork, List<SyncRealm> realms, Map<String, Boolean> features, NetworkSharedDataPolicy sharedDataPolicy, NetworkPreparedPlan prepared) {
    public NetworkRealmPreparedPlan {
        if (baseNetwork == null || prepared == null) {
            throw new IllegalArgumentException("Shared Data Review Requires A Network And Prepared Plan");
        }
        realms = realms == null ? List.of() : List.copyOf(realms);
        features = features == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(features));
        sharedDataPolicy = sharedDataPolicy == null ? NetworkSharedDataPolicy.defaults() : sharedDataPolicy;
    }

    public NetworkRealmPreparedPlan(NetworkDefinition baseNetwork, List<SyncRealm> realms, Map<String, Boolean> features, NetworkPreparedPlan prepared) {
        this(baseNetwork, realms, features, baseNetwork == null ? NetworkSharedDataPolicy.defaults() : baseNetwork.sharedDataPolicy(), prepared);
    }
}
