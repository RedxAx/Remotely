package redxax.oxy.remotely.network;

import java.util.Locale;

public record NetworkAdoptionRoute(String routeName, String address, int port, String instanceId, String finding, NetworkMemberManagement management) {
    public NetworkAdoptionRoute {
        routeName = normalize(routeName).toLowerCase(Locale.ROOT).replace(' ', '-');
        address = normalize(address);
        port = Math.clamp(port, 0, 65535);
        instanceId = normalize(instanceId);
        finding = normalize(finding);
        management = management == null ? NetworkMemberManagement.MANAGED : management;
    }

    public NetworkAdoptionRoute(String routeName, String address, int port, String instanceId, String finding) {
        this(routeName, address, port, instanceId, finding, NetworkMemberManagement.MANAGED);
    }

    public boolean matched() {
        return !instanceId.isBlank() || management == NetworkMemberManagement.EXTERNAL;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
