package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.config.Config;

import java.util.List;

public final class NodeDiscoveryPreferences {
    private static final int RECENT_OFFSET = -200_000;
    private static final int RECOMMENDED_OFFSET = -100_000;
    private static volatile RemotelyConfigManager cachedConfig;
    private static volatile List<String> recentIds = List.of();

    private NodeDiscoveryPreferences() {
    }

    public static int discoveryPriority(String nodeId, int basePriority, boolean recommended) {
        RemotelyConfigManager config = refreshPreferences();
        if (config == null || nodeId == null || nodeId.isBlank()) {
            return recommended ? RECOMMENDED_OFFSET + normalizedBasePriority(basePriority) : basePriority;
        }
        int recentIndex = indexOf(recentIds, nodeId);
        if (recentIndex >= 0) {
            return RECENT_OFFSET + recentIndex;
        }
        return recommended ? RECOMMENDED_OFFSET + normalizedBasePriority(basePriority) : basePriority;
    }

    public static void recordRecent(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        recordRecent(List.of(nodeId));
    }

    public static void recordRecent(List<String> nodeIds) {
        RemotelyConfigManager config = config();
        if (config != null) {
            config.recordRecentFlowNodes(nodeIds);
            recentIds = List.copyOf(config.getRecentFlowNodes());
        }
    }

    private static RemotelyConfigManager config() {
        return Config.configManager instanceof RemotelyConfigManager config ? config : null;
    }

    private static RemotelyConfigManager refreshPreferences() {
        RemotelyConfigManager config = config();
        if (config != cachedConfig) {
            synchronized (NodeDiscoveryPreferences.class) {
                if (config != cachedConfig) {
                    cachedConfig = config;
                    recentIds = config != null ? List.copyOf(config.getRecentFlowNodes()) : List.of();
                }
            }
        }
        return config;
    }

    private static int indexOf(List<String> values, String nodeId) {
        if (values == null || nodeId == null) {
            return -1;
        }
        for (int index = 0; index < values.size(); index++) {
            if (nodeId.equalsIgnoreCase(values.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int normalizedBasePriority(int priority) {
        return Math.clamp(priority, 0, 50_000);
    }
}
