package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkSharedDataPolicyTest {
    @Test
    void normalizesSelectionsAndLimits() {
        NetworkPathSync sync = new NetworkPathSync("luckperms", "LuckPerms", true, Set.of("lobby", "survival"), Set.of("server.properties", "plugins\\LuckPerms/"), NetworkSharedDataPolicy.ConflictPolicy.LOCAL_WINS, List.of("/lp reload"));
        NetworkSharedDataPolicy policy = new NetworkSharedDataPolicy(NetworkSharedDataPolicy.SelectionMode.ALLOW_LIST, Set.of(" Global ", "STAFF", ""), 0, NetworkSharedDataPolicy.SelectionMode.DENY_LIST, Set.of(" Items ", "WORLDS"), List.of(sync), NetworkSharedDataPolicy.ConflictPolicy.LOCAL_WINS, Integer.MAX_VALUE);

        assertEquals(Set.of("global", "staff"), policy.chatChannels());
        assertEquals(1_000, policy.chatRetentionMillis());
        assertEquals(Set.of("items", "worlds"), policy.resourceTypes());
        assertEquals(Set.of("server.properties", "plugins/LuckPerms"), policy.pathSyncs().getFirst().paths());
        assertEquals(List.of("lp reload"), policy.pathSyncs().getFirst().commands());
        assertEquals(500_000, policy.maximumPayloadBytes());
    }

    @Test
    void acceptsTheServerRootAndRejectsPathsOutsideIt() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkPathSync("unsafe", "Unsafe", true, Set.of("lobby", "survival"), Set.of("../secrets"), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, List.of()));
        NetworkPathSync sync = new NetworkPathSync("everything", "Everything", true, Set.of("lobby", "survival"), Set.of("."), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, List.of());
        NetworkSharedDataPolicy policy = new NetworkSharedDataPolicy(NetworkSharedDataPolicy.SelectionMode.ALL, Set.of(), 120_000, NetworkSharedDataPolicy.SelectionMode.ALL, Set.of(), List.of(sync), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, 500_000);
        assertEquals(Set.of("."), policy.pathSyncs().getFirst().paths());
    }

    @Test
    void preventsOverlappingEnabledSyncsOnTheSameServer() {
        NetworkPathSync root = new NetworkPathSync("root", "Root", true, Set.of("lobby", "survival"), Set.of("."), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, List.of());
        NetworkPathSync plugin = new NetworkPathSync("plugin", "Plugin", true, Set.of("lobby", "minigames"), Set.of("plugins/LuckPerms"), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, List.of());
        NetworkPathSync separate = new NetworkPathSync("separate", "Separate", true, Set.of("creative", "survival"), Set.of("plugins/LuckPerms"), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, List.of());

        assertThrows(IllegalArgumentException.class, () -> new NetworkSharedDataPolicy(NetworkSharedDataPolicy.SelectionMode.ALL, Set.of(), 120_000, NetworkSharedDataPolicy.SelectionMode.ALL, Set.of(), List.of(root, plugin), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, 500_000));
        assertDoesNotThrow(() -> new NetworkSharedDataPolicy(NetworkSharedDataPolicy.SelectionMode.ALL, Set.of(), 120_000, NetworkSharedDataPolicy.SelectionMode.ALL, Set.of(), List.of(plugin, separate), NetworkSharedDataPolicy.ConflictPolicy.NETWORK_WINS, 500_000));
    }
}
