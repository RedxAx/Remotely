package redxax.oxy.remotely.ui.widgets;

import org.junit.jupiter.api.Test;
import restudio.resync.network.NetworkEvent;
import restudio.resync.network.NetworkEventTopics;
import restudio.resync.network.NetworkPlayerLifecycle;
import restudio.resync.network.NetworkPlayerLifecycleCodec;
import restudio.resync.network.NetworkPlayerLifecycleType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPlayerNotificationSourceTest {
    private static final long NOW = 100_000L;

    @Test
    void acceptsNetworkJoinsAndLeavesOnce() {
        NetworkPlayerNotificationSource source = new NetworkPlayerNotificationSource();
        NetworkEvent joined = event("joined", NetworkPlayerLifecycleType.JOINED, NOW);
        NetworkEvent left = event("left", NetworkPlayerLifecycleType.LEFT, NOW);
        NetworkEvent noExpiry = new NetworkEvent("no-expiry", joined.networkId(), joined.channel(), joined.subject(), joined.payload(), joined.originNodeId(), joined.createdAt(), 0);

        assertEquals(NetworkPlayerLifecycleType.JOINED, source.accept(joined, NOW).orElseThrow().type());
        assertTrue(source.accept(joined, NOW).isEmpty());
        assertEquals(NetworkPlayerLifecycleType.LEFT, source.accept(left, NOW).orElseThrow().type());
        assertEquals(NetworkPlayerLifecycleType.JOINED, source.accept(noExpiry, NOW).orElseThrow().type());
    }

    @Test
    void ignoresTransfersAndUnrelatedEvents() {
        NetworkPlayerNotificationSource source = new NetworkPlayerNotificationSource();
        NetworkEvent transfer = event("transfer", NetworkPlayerLifecycleType.TRANSFER_COMPLETED, NOW);
        NetworkEvent unrelated = new NetworkEvent("unrelated", "network", "other", "JOINED", transfer.payload(), "proxy", NOW, NOW + 60_000L);

        assertTrue(source.accept(transfer, NOW).isEmpty());
        assertTrue(source.accept(unrelated, NOW).isEmpty());
    }

    @Test
    void ignoresStaleAndInvalidEvents() {
        NetworkPlayerNotificationSource source = new NetworkPlayerNotificationSource();
        NetworkEvent stale = event("stale", NetworkPlayerLifecycleType.JOINED, NOW - 30_001L);
        NetworkEvent current = event("expired", NetworkPlayerLifecycleType.JOINED, NOW);
        NetworkEvent expired = new NetworkEvent(current.eventId(), current.networkId(), current.channel(), current.subject(), current.payload(), current.originNodeId(), current.createdAt(), NOW);
        NetworkEvent invalid = new NetworkEvent("invalid", "network", NetworkEventTopics.PLAYER_LIFECYCLE, "JOINED", new byte[]{1}, "proxy", NOW, NOW + 60_000L);

        assertTrue(source.accept(stale, NOW).isEmpty());
        assertTrue(source.accept(expired, NOW).isEmpty());
        assertTrue(source.accept(invalid, NOW).isEmpty());
    }

    private NetworkEvent event(String eventId, NetworkPlayerLifecycleType type, long occurredAt) {
        NetworkPlayerLifecycle lifecycle = new NetworkPlayerLifecycle(type, UUID.randomUUID(), "Player", "lobby", "survival", "", occurredAt);
        return new NetworkEvent(eventId, "network", NetworkEventTopics.PLAYER_LIFECYCLE, type.name(), NetworkPlayerLifecycleCodec.encode(lifecycle), "proxy", occurredAt, occurredAt + 60_000L);
    }
}
