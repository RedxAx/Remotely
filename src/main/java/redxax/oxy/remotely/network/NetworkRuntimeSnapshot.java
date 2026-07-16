package redxax.oxy.remotely.network;

import restudio.resync.network.NetworkNodePresence;
import restudio.resync.network.NetworkNodeStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record NetworkRuntimeSnapshot(String networkId, NetworkRuntimeConnectionState state, String message, Map<String, NetworkNodePresence> nodes, long updatedAt) {
    public NetworkRuntimeSnapshot {
        networkId = networkId == null ? "" : networkId.trim();
        state = state == null ? NetworkRuntimeConnectionState.DISABLED : state;
        message = message == null ? "" : message.trim();
        nodes = nodes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(nodes));
    }

    public static NetworkRuntimeSnapshot disabled(String networkId) {
        return new NetworkRuntimeSnapshot(networkId, NetworkRuntimeConnectionState.DISABLED, "ReSync Runtime Disabled", Map.of(), System.currentTimeMillis());
    }

    public boolean connected() {
        return state == NetworkRuntimeConnectionState.CONNECTED;
    }

    public Optional<NetworkNodePresence> node(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public int players() {
        return nodes.values().stream().filter(presence -> presence.status() != NetworkNodeStatus.OFFLINE && presence.status() != NetworkNodeStatus.REVOKED).mapToInt(NetworkNodePresence::players).sum();
    }

    public NetworkRuntimeSnapshot connection(NetworkRuntimeConnectionState nextState, String nextMessage, boolean clearNodes) {
        return new NetworkRuntimeSnapshot(networkId, nextState, nextMessage, clearNodes ? Map.of() : nodes, System.currentTimeMillis());
    }

    public NetworkRuntimeSnapshot presence(NetworkNodePresence presence) {
        if (presence == null || !networkId.equals(presence.networkId())) {
            return this;
        }
        Map<String, NetworkNodePresence> nextNodes = new LinkedHashMap<>(nodes);
        nextNodes.put(presence.nodeId(), presence);
        return new NetworkRuntimeSnapshot(networkId, state, message, nextNodes, System.currentTimeMillis());
    }
}
