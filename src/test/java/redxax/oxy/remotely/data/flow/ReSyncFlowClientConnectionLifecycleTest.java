package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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

    @Test
    void handshakeErrorStopsConnectingAndAllowsRetry() {
        AtomicInteger notifications = new AtomicInteger();
        TestTransport transport = new TestTransport();
        ReSyncFlowClient client = new ReSyncFlowClient("live:bridge:test", transport, null);
        client.setErrorListener((nodeId, message) -> notifications.incrementAndGet());

        try {
            client.connect().join();

            byte[] message = "Invalid API key".getBytes(StandardCharsets.UTF_8);
            ByteBuffer payload = ByteBuffer.allocate(Integer.BYTES * 2 + message.length);
            payload.putInt(401);
            payload.putInt(message.length);
            payload.put(message);
            transport.receive(new ReSyncFrameCodec().encode(ReSyncProtocolContract.MESSAGE_ERROR, payload.array(), (short) 0, 1));

            assertEquals(ReSyncFlowClient.ConnectionState.DISCONNECTED, client.connectionState());
            assertEquals(1, notifications.get());
        } finally {
            client.shutdown();
        }
    }

    @Test
    void successfulHandshakeSignalsConnection() {
        AtomicInteger connections = new AtomicInteger();
        TestTransport transport = new TestTransport();
        ReSyncFlowClient client = new ReSyncFlowClient("live:bridge:test", transport, null);
        client.setConnectionListener(connections::incrementAndGet);

        try {
            client.connect().join();

            ByteBuffer payload = ByteBuffer.allocate(1 + Integer.BYTES * 4);
            payload.put((byte) 1);
            payload.putInt(0);
            payload.putInt(ReSyncProtocolContract.PROTOCOL_VERSION);
            payload.putInt(0);
            payload.putInt(0);
            transport.receive(new ReSyncFrameCodec().encode(ReSyncProtocolContract.MESSAGE_HANDSHAKE_RESPONSE, payload.array(), (short) 0, 1));

            assertEquals(ReSyncFlowClient.ConnectionState.CONNECTED, client.connectionState());
            assertEquals(1, connections.get());
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

    private static final class TestTransport implements ReSyncFrameTransport {
        private Consumer<byte[]> frameHandler;
        private Runnable closeHandler;

        @Override
        public void setFrameHandler(Consumer<byte[]> handler) {
            frameHandler = handler;
        }

        @Override
        public void setCloseHandler(Runnable handler) {
            closeHandler = handler;
        }

        @Override
        public void send(byte[] frame) {
        }

        @Override
        public void close() {
            if (closeHandler != null) {
                closeHandler.run();
            }
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        private void receive(byte[] frame) {
            frameHandler.accept(frame);
        }
    }
}
