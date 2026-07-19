package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSyncConfigurationTest {
    @Test
    void readsPresenceOnlyNetworks() {
        NetworkDefinition network = network(List.of(SyncRealm.presence("presence", "Presence", nodeIds())));
        NetworkSyncConfiguration configuration = NetworkSyncConfiguration.from(network);
        assertEquals(NetworkSyncMode.PRESENCE_ONLY, configuration.mode());
        assertEquals(nodeIds(), configuration.nodeIds());
    }

    @Test
    void createsSharedSurvivalRealm() {
        NetworkSyncConfiguration configuration = new NetworkSyncConfiguration(NetworkSyncMode.SHARED_SURVIVAL, nodeIds(), Set.of(SyncDataFamily.PRESENCE), SyncLocationPolicy.EXACT_COMPATIBLE_WORLD, Set.of(), 25, 45);
        SyncRealm realm = configuration.toRealms().getFirst();
        assertEquals("survival", realm.id());
        assertEquals(NetworkSyncConfiguration.SHARED_SURVIVAL_FAMILIES, realm.dataFamilies());
        assertEquals(SyncLocationPolicy.EXACT_COMPATIBLE_WORLD, realm.locationPolicy());
        assertEquals(25, realm.retainedSnapshots());
        assertEquals(45, realm.retentionDays());
    }

    @Test
    void createsCustomPersistentDataRealm() {
        Set<SyncDataFamily> families = Set.of(SyncDataFamily.INVENTORY, SyncDataFamily.PERSISTENT_DATA);
        NetworkSyncConfiguration configuration = new NetworkSyncConfiguration(NetworkSyncMode.CUSTOM, nodeIds(), families, SyncLocationPolicy.REALM_RETURN_POINT, Set.of("quests"), 20, 30);
        SyncRealm realm = configuration.toRealms().getFirst();
        assertTrue(realm.dataFamilies().contains(SyncDataFamily.PRESENCE));
        assertTrue(realm.dataFamilies().containsAll(families));
        assertEquals(Set.of("quests"), realm.persistentDataNamespaces());
        assertEquals(SyncLocationPolicy.NEVER, realm.locationPolicy());
    }

    @Test
    void rejectsStateSyncWithOneBackend() {
        NetworkSyncConfiguration configuration = new NetworkSyncConfiguration(NetworkSyncMode.SHARED_SURVIVAL, Set.of("lobby-node"), Set.of(), SyncLocationPolicy.REALM_RETURN_POINT, Set.of(), 20, 30);
        assertThrows(IllegalArgumentException.class, configuration::toRealms);
    }

    @Test
    void rejectsCustomModeWithoutPlayerState() {
        NetworkSyncConfiguration configuration = new NetworkSyncConfiguration(NetworkSyncMode.CUSTOM, nodeIds(), Set.of(SyncDataFamily.PRESENCE), SyncLocationPolicy.REALM_RETURN_POINT, Set.of(), 20, 30);
        assertThrows(IllegalArgumentException.class, configuration::toRealms);
    }

    private NetworkDefinition network(List<SyncRealm> realms) {
        String proxyId = UUID.randomUUID().toString();
        NetworkMember proxy = new NetworkMember(proxyId, "proxy-node", "proxy", NetworkMemberRole.PROXY, "local", "127.0.0.1", 25565, 0, true);
        NetworkMember lobby = new NetworkMember(UUID.randomUUID().toString(), "lobby-node", "lobby", NetworkMemberRole.LOBBY, "local", "127.0.0.1", 25566, 0, true);
        NetworkMember creative = new NetworkMember(UUID.randomUUID().toString(), "creative-node", "creative", NetworkMemberRole.GAMEPLAY, "local", "127.0.0.1", 25567, 0, true);
        NetworkDefinition base = NetworkDefinition.create("Network", proxyId, NetworkForwardingPolicy.secureDefault("secret"), List.of(NetworkEntryPoint.primary(25565)), List.of(proxy, lobby, creative));
        return new NetworkDefinition(base.schemaVersion(), base.networkId(), base.name(), base.revision(), base.proxyInstanceId(), base.desiredState(), base.forwarding(), base.entryPoints(), base.members(), base.routingGroups(), realms, base.runtime(), base.features(), base.createdAt(), base.updatedAt());
    }

    private Set<String> nodeIds() {
        return Set.of("lobby-node", "creative-node");
    }
}
