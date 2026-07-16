package redxax.oxy.remotely.network;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class NetworkRuntimeIdentity {
    private NetworkRuntimeIdentity() {
    }

    public static String operatorNodeId(String networkId) {
        String normalized = networkId == null ? "" : networkId.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Network ID Is Required");
        }
        return UUID.nameUUIDFromBytes(("remotely-network-operator:" + normalized).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
