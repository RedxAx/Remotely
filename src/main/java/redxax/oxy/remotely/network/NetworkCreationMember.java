package redxax.oxy.remotely.network;

public record NetworkCreationMember(String instanceId, String routeName, NetworkMemberRole role, String address, int preferredPort, int capacity, boolean resyncEnabled, NetworkMemberManagement management) {
    public NetworkCreationMember {
        instanceId = normalize(instanceId);
        routeName = normalize(routeName);
        role = role == null || role == NetworkMemberRole.PROXY ? NetworkMemberRole.GAMEPLAY : role;
        address = normalize(address);
        preferredPort = Math.clamp(preferredPort, 0, 65535);
        capacity = Math.max(0, capacity);
        management = management == null ? NetworkMemberManagement.MANAGED : management;
        if (management == NetworkMemberManagement.EXTERNAL) resyncEnabled = false;
    }

    public NetworkCreationMember(String instanceId, String routeName, NetworkMemberRole role, String address, int preferredPort, int capacity, boolean resyncEnabled) {
        this(instanceId, routeName, role, address, preferredPort, capacity, resyncEnabled, NetworkMemberManagement.MANAGED);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
