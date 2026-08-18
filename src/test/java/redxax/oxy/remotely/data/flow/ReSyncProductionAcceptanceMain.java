package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.flow.registry.NodeRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReSyncProductionAcceptanceMain {
    private ReSyncProductionAcceptanceMain() {
    }

    public static void main(String[] args) throws Exception {
        String mode = environment("RESYNC_ACCEPTANCE_MODE", "live");
        String serverId = environment("RESYNC_SERVER_ID", "local-paper");
        int minimumNodes = Integer.parseInt(environment("RESYNC_ACCEPTANCE_MINIMUM_NODES", "1000"));
        NodeRegistry registry = new NodeRegistry();
        if ("cache".equalsIgnoreCase(mode)) {
            runCacheAcceptance(registry, serverId, minimumNodes);
            return;
        }
        runLiveAcceptance(registry, serverId, minimumNodes);
    }

    private static void runLiveAcceptance(NodeRegistry registry, String serverId, int minimumNodes) throws Exception {
        String wsUrl = requiredEnvironment("RESYNC_WS_URL");
        String apiKey = requiredEnvironment("RESYNC_API_KEY");
        int expectedReconnects = Integer.parseInt(environment("RESYNC_ACCEPTANCE_EXPECT_RECONNECTS", "0"));
        Duration timeout = Duration.ofSeconds(Long.parseLong(environment("RESYNC_ACCEPTANCE_TIMEOUT_SECONDS", expectedReconnects > 0 ? "120" : "30")));
        AtomicInteger disconnects = new AtomicInteger();
        List<String> errors = new ArrayList<>();
        ReSyncFlowClient client = DesktopReSyncFlowClientFactory.create().create(serverId, null, wsUrl, apiKey, null, null);
        client.setDisconnectListener(disconnects::incrementAndGet);
        client.setErrorListener((nodeId, message) -> {
            synchronized (errors) {
                errors.add(message);
            }
        });
        Instant deadline = Instant.now().plus(timeout);
        long initialGeneration = 0L;
        int reconnects = 0;
        boolean previouslyConnected = false;
        boolean initialSync = false;
        try {
            client.connect();
            while (Instant.now().isBefore(deadline)) {
                boolean connected = client.isConnectedState();
                NodeRegistry.RegistrySessionMetadata metadata = registry.getRegistrySessionMetadata(serverId);
                int nodeCount = registry.getAllDefinitions(serverId).size();
                boolean synced = connected && metadata != null && !metadata.checksum().isBlank() && nodeCount >= minimumNodes;
                if (synced && !initialSync) {
                    initialSync = true;
                    initialGeneration = metadata.generatedAt();
                } else if (synced && !previouslyConnected && initialSync && metadata.generatedAt() != initialGeneration) {
                    reconnects++;
                    initialGeneration = metadata.generatedAt();
                }
                previouslyConnected = connected;
                if (initialSync && reconnects >= expectedReconnects) {
                    printResult("live", serverId, nodeCount, metadata, disconnects.get(), reconnects, errors.size());
                    return;
                }
                Thread.sleep(100L);
            }
            NodeRegistry.RegistrySessionMetadata metadata = registry.getRegistrySessionMetadata(serverId);
            throw new IllegalStateException("Live acceptance timed out: connected=" + client.isConnectedState()
                + ", nodes=" + registry.getAllDefinitions(serverId).size() + ", disconnects=" + disconnects.get()
                + ", reconnects=" + reconnects + ", errors=" + errors);
        } finally {
            client.shutdown();
        }
    }

    private static void runCacheAcceptance(NodeRegistry registry, String serverId, int minimumNodes) {
        boolean expectEmpty = Boolean.parseBoolean(environment("RESYNC_ACCEPTANCE_EXPECT_EMPTY", "false"));
        ReSyncFlowClient client = DesktopReSyncFlowClientFactory.create().create(serverId, null, "ws://127.0.0.1:1", "unused", null, null);
        try {
            NodeRegistry.RegistrySessionMetadata metadata = registry.getRegistrySessionMetadata(serverId);
            int nodeCount = registry.getAllDefinitions(serverId).size();
            if (expectEmpty) {
                if (metadata != null || nodeCount != 0) {
                    throw new IllegalStateException("Registry cache leaked across server identities: nodes=" + nodeCount);
                }
            } else if (metadata == null || metadata.checksum().isBlank() || nodeCount < minimumNodes) {
                throw new IllegalStateException("Registry cache is unavailable or incomplete: nodes=" + nodeCount);
            }
            printResult("cache", serverId, nodeCount, metadata, 0, 0, 0);
        } finally {
            client.shutdown();
        }
    }

    private static void printResult(String mode, String serverId, int nodeCount, NodeRegistry.RegistrySessionMetadata metadata,
                                    int disconnects, int reconnects, int errors) {
        String checksum = metadata != null ? metadata.checksum() : "";
        int contractVersion = metadata != null ? metadata.contractVersion() : 0;
        System.out.println("RESYNC_ACCEPTANCE_RESULT mode=" + mode + " serverId=" + serverId + " nodes=" + nodeCount
            + " checksum=" + checksum + " contractVersion=" + contractVersion + " disconnects=" + disconnects
            + " reconnects=" + reconnects + " errors=" + errors);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value : fallback;
    }
}
