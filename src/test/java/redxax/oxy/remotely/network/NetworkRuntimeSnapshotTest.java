package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import restudio.resync.network.NetworkNodePresence;
import restudio.resync.network.NetworkNodeStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRuntimeSnapshotTest {
    @Test
    void preservesNodeObservationsAcrossReconnectAndClearsThemAfterAuthorization() {
        NetworkNodePresence presence = new NetworkNodePresence("network", "lobby", NetworkNodeStatus.ONLINE, 12, 100, 19.9, 4.2, 128, 512, 1000);
        NetworkRuntimeSnapshot connected = new NetworkRuntimeSnapshot("network", NetworkRuntimeConnectionState.CONNECTED, "Connected", Map.of(), 1000).presence(presence);

        NetworkRuntimeSnapshot reconnecting = connected.connection(NetworkRuntimeConnectionState.RECONNECTING, "Reconnecting", false);
        NetworkRuntimeSnapshot authorized = reconnecting.connection(NetworkRuntimeConnectionState.CONNECTED, "Connected", true);

        assertTrue(connected.connected());
        assertEquals(12, connected.players());
        assertEquals(presence, reconnecting.node("lobby").orElseThrow());
        assertFalse(authorized.node("lobby").isPresent());
    }

    @Test
    void ignoresPresenceFromAnotherNetwork() {
        NetworkRuntimeSnapshot snapshot = NetworkRuntimeSnapshot.disabled("network");
        NetworkNodePresence presence = new NetworkNodePresence("other", "lobby", NetworkNodeStatus.ONLINE, 1, 10, 20, 1, 1, 2, 1000);

        assertTrue(snapshot.presence(presence).nodes().isEmpty());
    }
}
