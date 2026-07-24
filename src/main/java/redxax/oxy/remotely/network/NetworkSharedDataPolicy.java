package redxax.oxy.remotely.network;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record NetworkSharedDataPolicy(SelectionMode chatChannelMode, Set<String> chatChannels, long chatRetentionMillis, SelectionMode resourceTypeMode, Set<String> resourceTypes, ConflictPolicy resourceConflictPolicy, int maximumPayloadBytes) {
    public static final int DEFAULT_MAXIMUM_PAYLOAD_BYTES = 524_288;

    public NetworkSharedDataPolicy {
        chatChannelMode = chatChannelMode == null ? SelectionMode.ALL : chatChannelMode;
        chatChannels = normalized(chatChannels);
        chatRetentionMillis = Math.clamp(chatRetentionMillis, 1_000, 31_536_000_000L);
        resourceTypeMode = resourceTypeMode == null ? SelectionMode.ALL : resourceTypeMode;
        resourceTypes = normalized(resourceTypes);
        resourceConflictPolicy = resourceConflictPolicy == null ? ConflictPolicy.NETWORK_WINS : resourceConflictPolicy;
        maximumPayloadBytes = Math.clamp(maximumPayloadBytes, 1_024, 1_048_576);
    }

    public static NetworkSharedDataPolicy defaults() {
        return new NetworkSharedDataPolicy(SelectionMode.ALL, Set.of(), 120_000, SelectionMode.ALL, Set.of(), ConflictPolicy.NETWORK_WINS, DEFAULT_MAXIMUM_PAYLOAD_BYTES);
    }

    private static Set<String> normalized(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream().map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public enum SelectionMode {
        ALL,
        ALLOW_LIST,
        DENY_LIST
    }

    public enum ConflictPolicy {
        NETWORK_WINS,
        LOCAL_WINS
    }
}
