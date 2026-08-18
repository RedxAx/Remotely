package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.DesktopRemotelyServerApi;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.resync.protocol.ReSyncHandshakeCodec;
import restudio.resync.protocol.ReSyncHandshakeRequest;
import restudio.resync.protocol.ReSyncHandshakeResponse;
import restudio.resync.protocol.ReSyncProtocolContract;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReSyncFlowClientConnectionLifecycleTest {

    @Test
    void shutdownClientCannotStartAnotherConnection() {
        AtomicInteger requests = new AtomicInteger();
        ReSyncFlowClient client = desktopClient("live:proxy:test", new FailingApi(requests));

        client.shutdown();
        client.connect().join();

        assertEquals(0, requests.get());
    }

    @Test
    void repeatedConnectionFailureNotifiesOncePerOutage() {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger notifications = new AtomicInteger();
        ReSyncFlowClient client = desktopClient("server", new FailingApi(requests));
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
        ReSyncFlowClient client = desktopClient("live:bridge:test", transport);
        client.setErrorListener((nodeId, message) -> notifications.incrementAndGet());

        try {
            client.connect().join();

            byte[] message = "Invalid API key".getBytes(StandardCharsets.UTF_8);
            ByteBuffer payload = ByteBuffer.allocate(Integer.BYTES * 2 + message.length);
            payload.putInt(401);
            payload.putInt(message.length);
            payload.put(message);
            transport.receive(new RemotelyReSyncFrameCodec().encode(ReSyncProtocolContract.MESSAGE_ERROR, payload.array(), (short) 0, 1));

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
        ReSyncFlowClient client = desktopClient("live:bridge:test", transport);
        client.setConnectionListener(connections::incrementAndGet);

        try {
            client.connect().join();

            transport.receive(successfulHandshakeFrame());

            assertEquals(ReSyncFlowClient.ConnectionState.CONNECTED, client.connectionState());
            assertEquals(1, connections.get());
        } finally {
            client.shutdown();
        }
    }

    @Test
    void outboundHandshakeUsesCanonicalRequestContract() {
        TestTransport transport = new TestTransport();
        ReSyncFlowClient client = desktopClient("live:bridge:test", transport);

        try {
            client.connect().join();

            ReSyncDecodedFrame frame = new RemotelyReSyncFrameCodec().decode(transport.sentFrames().getFirst(), null);
            ReSyncHandshakeRequest request = new ReSyncHandshakeCodec().decodeRequest(frame.payload());

            assertEquals(ReSyncProtocolContract.MESSAGE_HANDSHAKE_REQUEST, frame.messageType());
            assertEquals(ReSyncProtocolContract.CHANNEL_CONTROL_ID, frame.channel());
            assertEquals(ReSyncProtocolContract.PROTOCOL_VERSION, request.protocolVersion());
            assertEquals("2.1.0", request.clientVersion());
            assertFalse(request.clientId().isBlank());
            assertTrue(request.capabilitiesJson().contains("nodes"));
            assertFalse(request.collaborationProfileJson().isBlank());
        } finally {
            client.shutdown();
        }
    }

    @Test
    void injectedTransportPreservesOutboundSequenceOrder() {
        TestTransport transport = new TestTransport();
        ReSyncFlowClient client = desktopClient("live:bridge:test", transport);

        try {
            client.connect().join();

            transport.receive(successfulHandshakeFrame());
            transport.clearSentFrames();

            client.requestWorldSnapshot();
            client.sendTriggerUpdate(List.of());

            List<Integer> sequences = transport.sentSequences();
            assertEquals(2, sequences.size());
            assertEquals(sequences.getFirst() + 1, sequences.get(1));
        } finally {
            client.shutdown();
        }
    }

    @Test
    void queuedWorldGenerationRetryRetainsMutationId() throws Exception {
        TestTransport transport = new TestTransport();
        ReSyncFlowClient client = desktopClient("live:bridge:test", transport);

        try {
            client.connect().join();

            transport.receive(successfulHandshakeFrame());
            transport.clearSentFrames();
            transport.close();

            client.sendWorldGenPreviewStop("preview");
            Field field = ReSyncFlowClient.class.getDeclaredField("pendingSends");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Deque<Runnable> pending = (Deque<Runnable>) field.get(client);
            Runnable retry = pending.pollFirst();
            assertNotNull(retry);

            transport.setOpen(true);
            client.connect().join();
            transport.receive(successfulHandshakeFrame());
            transport.clearSentFrames();

            retry.run();
            retry.run();

            List<String> requestIds = transport.sentFrames().stream()
                .filter(frame -> frame.length >= 13 && (frame[1] & 0xFF) == ReSyncProtocolContract.MESSAGE_DATA
                    && Short.toUnsignedInt(ByteBuffer.wrap(frame, 2, 2).getShort()) == ReSyncProtocolContract.CHANNEL_WORLDGEN_ID
                    && frame[12] == (byte) 0x22)
                .map(ReSyncFlowClientConnectionLifecycleTest::worldGenerationRequestId)
                .toList();
            assertEquals(2, requestIds.size());
            assertEquals(requestIds.getFirst(), requestIds.get(1));
        } finally {
            client.shutdown();
        }
    }

    private static String worldGenerationRequestId(byte[] frame) {
        ByteBuffer payload = ByteBuffer.wrap(frame);
        payload.position(12);
        payload.get();
        int length = payload.getInt();
        byte[] requestId = new byte[length];
        payload.get(requestId);
        return new String(requestId, StandardCharsets.UTF_8);
    }

    private static byte[] successfulHandshakeFrame() {
        ReSyncHandshakeResponse response = new ReSyncHandshakeResponse(
            true,
            "",
            ReSyncProtocolContract.PROTOCOL_VERSION,
            "test",
            List.of(),
            new int[0],
            Map.of(),
            ""
        );
        byte[] payload = new ReSyncHandshakeCodec().encodeResponse(response);
        return new RemotelyReSyncFrameCodec().encode(
            ReSyncProtocolContract.MESSAGE_HANDSHAKE_RESPONSE,
            payload,
            ReSyncProtocolContract.CHANNEL_CONTROL_ID,
            1
        );
    }

    private static ReSyncFlowClient desktopClient(String serverId, ReStudioApiClient apiClient) {
        return DesktopReSyncFlowClientFactory.create().create(serverId, new DesktopRemotelyServerApi(apiClient), null, null, null, null);
    }

    private static ReSyncFlowClient desktopClient(String serverId, ReSyncFrameTransport transport) {
        return DesktopReSyncFlowClientFactory.create().create(serverId, null, null, null, transport, null);
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
        private final List<byte[]> sentFrames = new ArrayList<>();
        private volatile boolean open = true;

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
            sentFrames.add(frame.clone());
        }

        @Override
        public void close() {
            open = false;
            if (closeHandler != null) {
                closeHandler.run();
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        private void receive(byte[] frame) {
            frameHandler.accept(frame);
        }

        private void clearSentFrames() {
            sentFrames.clear();
        }

        private void setOpen(boolean open) {
            this.open = open;
        }

        private List<byte[]> sentFrames() {
            return List.copyOf(sentFrames);
        }

        private List<Integer> sentSequences() {
            return sentFrames.stream().map(frame -> ByteBuffer.wrap(frame).getInt(4)).toList();
        }
    }
}
