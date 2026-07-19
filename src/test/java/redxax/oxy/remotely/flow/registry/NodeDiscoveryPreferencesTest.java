package redxax.oxy.remotely.flow.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.config.Config;
import restudio.rescreen.config.UiConfigStore;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeDiscoveryPreferencesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recentsAndRecommendationsDriveDiscoveryOrder() {
        UiConfigStore previous = Config.configManager;
        RemotelyConfigManager config = new RemotelyConfigManager(temporaryDirectory);
        try {
            Config.configManager = config;
            NodeDiscoveryPreferences.recordRecent("node.recent");

            int recent = NodeDiscoveryPreferences.discoveryPriority("node.recent", 100, false);
            int recommended = NodeDiscoveryPreferences.discoveryPriority("node.recommended", 100, true);
            int ordinary = NodeDiscoveryPreferences.discoveryPriority("node.ordinary", 100, false);

            assertTrue(recent < recommended);
            assertTrue(recommended < ordinary);
        } finally {
            Config.configManager = previous;
        }
    }
}
