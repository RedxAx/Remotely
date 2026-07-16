package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import restudio.resync.network.NetworkEvent;
import restudio.resync.network.NetworkEventTopics;
import restudio.resync.network.NetworkNodePresence;
import restudio.resync.network.NetworkNodeStatus;
import restudio.resync.network.NetworkPlayerLifecycle;
import restudio.resync.network.NetworkPlayerLifecycleCodec;
import restudio.resync.network.NetworkPlayerLifecycleType;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkIncidentManagerTest {
    @TempDir
    Path directory;

    @Test
    void persistsAndResolvesRuntimeIncidents() {
        NetworkDefinition network = NetworkValidatorTest.validNetwork();
        NetworkIncidentManager manager = new NetworkIncidentManager(directory);
        manager.observeRuntime(network, new NetworkRuntimeSnapshot(network.networkId(), NetworkRuntimeConnectionState.RECONNECTING, "Connection Lost", Map.of(), System.currentTimeMillis()));

        assertEquals(1, manager.openCount(network.networkId()));

        Map<String, NetworkNodePresence> healthy = new LinkedHashMap<>();
        network.members().forEach(member -> healthy.put(member.nodeId(), new NetworkNodePresence(network.networkId(), member.nodeId(), NetworkNodeStatus.ONLINE, 1, 100, 20, 10, 100, 1000, System.currentTimeMillis())));
        manager.observeRuntime(network, new NetworkRuntimeSnapshot(network.networkId(), NetworkRuntimeConnectionState.CONNECTED, "Connected", healthy, System.currentTimeMillis()));

        NetworkIncidentManager restored = new NetworkIncidentManager(directory);
        assertEquals(0, restored.openCount(network.networkId()));
        assertTrue(restored.incidents(network.networkId()).stream().anyMatch(incident -> incident.type().equals("runtime.connection") && incident.status() == NetworkIncidentStatus.RESOLVED));
    }

    @Test
    void retainsTransferFailuresForTopologyHeat() {
        NetworkDefinition network = NetworkValidatorTest.validNetwork();
        NetworkMember backend = network.members().stream().filter(member -> !member.isProxy()).findFirst().orElseThrow();
        NetworkPlayerLifecycle lifecycle = new NetworkPlayerLifecycle(NetworkPlayerLifecycleType.TRANSFER_FAILED, UUID.randomUUID(), "Player", "lobby", backend.routeName(), "STATE_HANDOFF_FAILED", System.currentTimeMillis());
        NetworkEvent event = new NetworkEvent(UUID.randomUUID().toString(), network.networkId(), NetworkEventTopics.PLAYER_LIFECYCLE, lifecycle.type().name(), NetworkPlayerLifecycleCodec.encode(lifecycle), network.proxyMember().nodeId(), lifecycle.occurredAt(), lifecycle.occurredAt() + 60_000);
        NetworkIncidentManager manager = new NetworkIncidentManager(directory);

        manager.observeEvent(network, event);
        manager.observeEvent(network, event);

        assertEquals(1, manager.transferFailureHeat(network.networkId()).get(backend.nodeId()).intValue());
        assertEquals(1, manager.incidents(network.networkId()).size());
    }
}
