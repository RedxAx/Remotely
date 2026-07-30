package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReSyncFlowClientConnectionLifecycleTest {

    @Test
    void shutdownClientCannotStartAnotherConnection() {
        AtomicInteger requests = new AtomicInteger();
        ReSyncFlowClient client = new ReSyncFlowClient("live:proxy:test", new FailingApi(requests), null);

        client.shutdown();
        client.connect().join();

        assertEquals(0, requests.get());
    }

    @Test
    void repeatedConnectionFailureNotifiesOncePerOutage() {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger notifications = new AtomicInteger();
        ReSyncFlowClient client = new ReSyncFlowClient("server", new FailingApi(requests), null);
        client.setErrorListener((nodeId, message) -> notifications.incrementAndGet());

        try {
            client.connect().join();
            client.connect().join();

            assertEquals(2, requests.get());
            assertEquals(1, notifications.get());
        } finally {
            client.shutdown();
        }
    }

    private static final class FailingApi extends ReStudioApiClient {
        private final AtomicInteger requests;

        private FailingApi(AtomicInteger requests) {
            this.requests = requests;
        }

        @Override
        public CompletableFuture<ServerModels.ReSyncConfig> getReSyncConfig(String serverId) {
            requests.incrementAndGet();
            return CompletableFuture.failedFuture(new ApiException(404, "server_not_found", "Server not found: " + serverId));
        }
    }
}
