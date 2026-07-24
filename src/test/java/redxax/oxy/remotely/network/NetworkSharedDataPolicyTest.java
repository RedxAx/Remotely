package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkSharedDataPolicyTest {
    @Test
    void normalizesSelectionsAndLimits() {
        NetworkSharedDataPolicy policy = new NetworkSharedDataPolicy(NetworkSharedDataPolicy.SelectionMode.ALLOW_LIST, Set.of(" Global ", "STAFF", ""), 0, NetworkSharedDataPolicy.SelectionMode.DENY_LIST, Set.of(" Items ", "WORLDS"), NetworkSharedDataPolicy.ConflictPolicy.LOCAL_WINS, Integer.MAX_VALUE);

        assertEquals(Set.of("global", "staff"), policy.chatChannels());
        assertEquals(1_000, policy.chatRetentionMillis());
        assertEquals(Set.of("items", "worlds"), policy.resourceTypes());
        assertEquals(1_048_576, policy.maximumPayloadBytes());
    }
}
