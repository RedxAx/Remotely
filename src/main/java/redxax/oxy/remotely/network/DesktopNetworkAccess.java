package redxax.oxy.remotely.network;

import redxax.oxy.remotely.RemotelyClient;
import restudio.rebase.instance.Instance;

public final class DesktopNetworkAccess {
    private DesktopNetworkAccess() {
    }

    public static NetworkManager<Instance, PortReservation> capability(RemotelyClient client) {
        return client.getNetworkManager(Instance.class, PortReservation.class);
    }

    public static DesktopNetworkManager manager(RemotelyClient client) {
        if (client == null) {
            return null;
        }
        try {
            return client.getNetworkManagerAs(DesktopNetworkManager.class);
        } catch (ClassCastException exception) {
            return null;
        }
    }
}
