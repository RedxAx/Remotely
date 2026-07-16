package redxax.oxy.remotely.network;

public record NetworkAttachPreparedPlan(NetworkDefinition baseNetwork, NetworkDefinition candidate, NetworkMember member, String routingGroupId, NetworkPreparedPlan prepared) {
    public NetworkAttachPreparedPlan {
        routingGroupId = routingGroupId == null ? "" : routingGroupId.trim();
    }
}
