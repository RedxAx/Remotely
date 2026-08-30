package redxax.oxy.remotely.flow.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import redxax.oxy.remotely.data.flow.DesktopReSyncStorage;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.sync.NodePluginPayload;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRegistryTombstoneCacheTest {
    @TempDir
    Path tempDirectory;

    @Test
    void unresolvedExtensionSchemasSurviveOfflineRestorationAndRemainServerScoped() {
        Path path = tempDirectory.resolve("tombstones.json");
        NodeDefinition definition = new NodeDefinition.Builder("request:quest_info", "Quest Info", NodeDefinition.NodeCategory.DATA).build();
        NodePluginPayload payload = new NodePluginPayload();
        payload.setPluginId("request");
        payload.setChecksum("request-a");
        payload.setNodes(List.of(definition));
        NodeRegistryTombstoneCache cache = new NodeRegistryTombstoneCache(DesktopReSyncStorage.fromKey(path));

        cache.replace("server-a", List.of(payload));
        NodeRegistryTombstoneCache restored = new NodeRegistryTombstoneCache(DesktopReSyncStorage.fromKey(path));

        assertEquals("request", restored.get("server-a").getFirst().getPluginId());
        assertEquals("request:quest_info", restored.get("server-a").getFirst().getNodes().getFirst().getId());
        assertTrue(restored.get("server-b").isEmpty());

        restored.replace("server-a", List.of());
        NodeRegistryTombstoneCache cleared = new NodeRegistryTombstoneCache(DesktopReSyncStorage.fromKey(path));

        assertTrue(cleared.get("server-a").isEmpty());
    }
}
