package redxax.oxy.remotely.network;

import java.util.List;

public record NetworkRoutingPreparedPlan(NetworkDefinition baseNetwork, List<RoutingGroup> routingGroups, NetworkPreparedPlan prepared) {
    public NetworkRoutingPreparedPlan {
        routingGroups = routingGroups == null ? List.of() : List.copyOf(routingGroups);
    }
}
