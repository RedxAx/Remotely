package redxax.oxy.remotely.network;

public record NetworkSecretRotationPreparedPlan(NetworkDefinition baseNetwork, NetworkDefinition candidate, NetworkPreparedPlan prepared) {
}
