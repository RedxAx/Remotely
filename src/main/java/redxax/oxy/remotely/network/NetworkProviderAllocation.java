package redxax.oxy.remotely.network;

public record NetworkProviderAllocation(String instanceId, String provider, String address, int port, String hostScope) {
    public NetworkProviderAllocation {
        instanceId = normalize(instanceId);
        provider = normalize(provider);
        address = normalize(address);
        hostScope = normalize(hostScope);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
