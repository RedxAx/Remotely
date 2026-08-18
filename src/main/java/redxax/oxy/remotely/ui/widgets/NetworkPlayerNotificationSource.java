package redxax.oxy.remotely.ui.widgets;

import restudio.resync.network.NetworkEvent;
import restudio.resync.network.NetworkEventTopics;
import restudio.resync.network.NetworkPlayerLifecycle;
import restudio.resync.network.NetworkPlayerLifecycleCodec;
import restudio.resync.network.NetworkPlayerLifecycleType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class NetworkPlayerNotificationSource {
    private static final long MAXIMUM_EVENT_AGE_MS = 30_000L;
    private static final long MAXIMUM_CLOCK_SKEW_MS = 5_000L;
    private final Map<String, Long> handledEvents = new HashMap<>();

    public synchronized Optional<NetworkPlayerLifecycle> accept(NetworkEvent event, long now) {
        handledEvents.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (event == null || !NetworkEventTopics.PLAYER_LIFECYCLE.equals(event.channel()) || handledEvents.containsKey(event.eventId())) return Optional.empty();
        NetworkPlayerLifecycle lifecycle;
        try {
            lifecycle = NetworkPlayerLifecycleCodec.decode(event.payload());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        if (lifecycle.type() != NetworkPlayerLifecycleType.JOINED && lifecycle.type() != NetworkPlayerLifecycleType.LEFT) return Optional.empty();
        if (event.createdAt() > now + MAXIMUM_CLOCK_SKEW_MS || event.expiresAt() > 0 && event.expiresAt() <= now) return Optional.empty();
        if (lifecycle.occurredAt() < now - MAXIMUM_EVENT_AGE_MS || lifecycle.occurredAt() > now + MAXIMUM_CLOCK_SKEW_MS) return Optional.empty();
        handledEvents.put(event.eventId(), now + MAXIMUM_EVENT_AGE_MS);
        return Optional.of(lifecycle);
    }
}
