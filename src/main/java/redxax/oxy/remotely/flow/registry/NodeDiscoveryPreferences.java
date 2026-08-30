package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.config.RemotelyConfigStore;
import redxax.oxy.remotely.data.flow.ReSyncStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NodeDiscoveryPreferences {
    private static final int RECENT_OFFSET = -200_000;
    private static final int RECOMMENDED_OFFSET = -100_000;
    private static volatile RecentNodeStore store = new MemoryRecentNodeStore();

    private NodeDiscoveryPreferences() {
    }

    public static int discoveryPriority(String nodeId, int basePriority, boolean recommended) {
        List<String> recentIds = store.read();
        if (nodeId == null || nodeId.isBlank()) {
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
        store.write(nodeIds);
    }

    public static void configure(RecentNodeStore nextStore) {
        store = Objects.requireNonNull(nextStore, "store");
    }

    public static void configure(ReSyncStorage storage) {
        Objects.requireNonNull(storage, "storage");
        configure(new StorageRecentNodeStore(storage));
    }

    public static void configure(RemotelyConfigStore config) {
        Objects.requireNonNull(config, "config");
        configure(new ConfigRecentNodeStore(config));
    }

    public static Snapshot snapshot() {
        return new Snapshot(store);
    }

    public static void restore(Snapshot snapshot) {
        store = Objects.requireNonNull(snapshot, "snapshot").store();
    }

    public record Snapshot(RecentNodeStore store) {
        public Snapshot {
            store = Objects.requireNonNull(store, "store");
        }
    }

    public interface RecentNodeStore {
        List<String> read();

        void write(List<String> nodeIds);
    }

    private static final class MemoryRecentNodeStore implements RecentNodeStore {
        private List<String> recent = List.of();

        @Override
        public synchronized List<String> read() {
            return recent;
        }

        @Override
        public synchronized void write(List<String> nodeIds) {
            List<String> next = new ArrayList<>(recent);
            prepend(next, nodeIds);
            recent = normalize(next);
        }
    }

    private static final class StorageRecentNodeStore implements RecentNodeStore {
        private final ReSyncStorage storage;

        private StorageRecentNodeStore(ReSyncStorage storage) {
            this.storage = storage;
        }

        @Override
        public List<String> read() {
            String value = storage.read("recent-flow-nodes");
            return value == null || value.isBlank() ? List.of() : normalize(List.of(value.split(",")));
        }

        @Override
        public void write(List<String> nodeIds) {
            List<String> next = new ArrayList<>(read());
            prepend(next, nodeIds);
            storage.write("recent-flow-nodes", String.join(",", normalize(next)));
        }
    }

    private static final class ConfigRecentNodeStore implements RecentNodeStore {
        private final RemotelyConfigStore config;

        private ConfigRecentNodeStore(RemotelyConfigStore config) {
            this.config = config;
        }

        @Override
        public List<String> read() {
            return config.getRecentFlowNodes();
        }

        @Override
        public void write(List<String> nodeIds) {
            config.recordRecentFlowNodes(nodeIds);
        }
    }

    private static void prepend(List<String> target, List<String> nodeIds) {
        if (nodeIds == null) {
            return;
        }
        for (String nodeId : nodeIds) {
            if (nodeId == null || nodeId.isBlank()) {
                continue;
            }
            target.removeIf(existing -> nodeId.equalsIgnoreCase(existing));
            target.addFirst(nodeId.trim());
        }
    }

    private static List<String> normalize(List<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        return nodeIds.stream().filter(nodeId -> nodeId != null && !nodeId.isBlank()).map(String::trim).distinct().limit(24).toList();
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
