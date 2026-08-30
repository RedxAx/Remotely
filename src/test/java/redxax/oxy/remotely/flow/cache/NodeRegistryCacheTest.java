package redxax.oxy.remotely.flow.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import redxax.oxy.remotely.data.flow.DesktopReSyncStorage;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRegistryCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectionDiagnosticsRemainScopedToTheirServer() {
        NodeRegistryCache cache = new NodeRegistryCache(DesktopReSyncStorage.fromKey(tempDir.resolve("registry.json")));
        NodeRegistrySnapshot incompatible = snapshot("server-a", NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION + 1, "invalid");

        cache.applySnapshot("server-a", incompatible);

        assertTrue(cache.getDiagnostic("server-a").invalidationReason().contains("Rejected incompatible"));
        assertEquals("", cache.getDiagnostic("server-b").invalidationReason());
        assertFalse(cache.getDiagnostic("server-a").present());
    }

    @Test
    void acceptedSnapshotPersistsAtomicallyAndClearsPriorRejection() {
        Path path = tempDir.resolve("registry.json");
        NodeRegistryCache cache = new NodeRegistryCache(DesktopReSyncStorage.fromKey(path));
        cache.applySnapshot("server-a", snapshot("another-server", NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION, "wrong"));
        assertFalse(cache.getDiagnostic("server-a").invalidationReason().isBlank());

        NodeRegistrySnapshot accepted = snapshot("server-a", NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION, "registry-a");
        accepted.setNodeIds(List.of("flow.start"));
        accepted.setRegistryDiagnostics(Map.of("parity", true));
        cache.applySnapshot("server-a", accepted);

        assertEquals("", cache.getDiagnostic("server-a").invalidationReason());
        assertTrue(Files.exists(path));
        assertFalse(Files.exists(path.resolveSibling("registry.json.tmp")));

        NodeRegistrySnapshot restored = new NodeRegistryCache(DesktopReSyncStorage.fromKey(path)).getSnapshot("server-a");
        assertNotNull(restored);
        assertEquals("registry-a", restored.getRegistryChecksum());
        assertEquals(List.of("flow.start"), restored.getNodeIds());
        assertEquals(true, restored.getRegistryDiagnostics().get("parity"));
    }

    private NodeRegistrySnapshot snapshot(String serverId, int contractVersion, String checksum) {
        NodeRegistrySnapshot snapshot = new NodeRegistrySnapshot();
        snapshot.setContractVersion(contractVersion);
        snapshot.setMinimumClientContractVersion(NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION);
        snapshot.setServerIdentity(serverId);
        snapshot.setFullSync(true);
        snapshot.setRegistryChecksum(checksum);
        snapshot.setGeneratedAt(1L);
        snapshot.setCapabilities(List.of("nodes"));
        return snapshot;
    }
}
