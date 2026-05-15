package redxax.oxy.remotely.resync.bridge;

import net.minecraft.client.Minecraft;
import redxax.oxy.remotely.Constants;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncFrameTransport;
import redxax.oxy.remotely.data.flow.ReSyncLiveServerSession;
import redxax.oxy.remotely.util.InitializationManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ReSyncVanillaBridgeManager {
    private static final ReSyncVanillaBridgeManager INSTANCE = new ReSyncVanillaBridgeManager();
    private static final long HELLO_RETRY_NANOS = 1_000_000_000L;
    private final ReSyncVanillaPacketAdapter adapter = new ReSyncVanillaPacketAdapter();
    private final ReSyncBridgeChunker chunker = new ReSyncBridgeChunker();
    private final AtomicInteger sequence = new AtomicInteger(1);
    private UUID sessionId = UUID.randomUUID();
    private boolean helloSent;
    private boolean authenticated;
    private String liveServerId;
    private String displayName = "Live Server";
    private Set<String> supportedChannels = Set.of();
    private BridgeTransport transport;
    private Object lastConnection;
    private long nextHelloNanos;
    private boolean channelRegistered;

    public static ReSyncVanillaBridgeManager getInstance() {
        return INSTANCE;
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        Object connection = client.getConnection();
        if (connection == null || client.player == null) {
            reset();
            return;
        }
        if (connection != lastConnection) {
            reset();
            lastConnection = connection;
        }
        if (!authenticated && System.nanoTime() >= nextHelloNanos) {
            sendHello();
        }
    }

    public void handlePayload(byte[] payload) {
        try {
            ReSyncBridgeEnvelope envelope = ReSyncBridgeEnvelope.decode(payload);
            byte[] complete = chunker.accept(envelope);
            if (complete == null) {
                return;
            }
            switch (envelope.type()) {
                case ReSyncBridgeEnvelope.AUTH_RESULT -> handleAuthResult(envelope.sessionId(), complete);
                case ReSyncBridgeEnvelope.DATA -> {
                    if (transport != null) {
                        transport.receive(complete);
                    }
                }
                case ReSyncBridgeEnvelope.CLOSE, ReSyncBridgeEnvelope.ERROR -> {
                    closeLiveSession();
                }
                default -> {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void openStudioFromKey() {
        InitializationManager.ensureInitialized();
        if (RemotelyClient.INSTANCE == null) {
            new Notification("ReSync", "Remotely Loading", Notification.Type.WARN);
            return;
        }
        if (!authenticated) {
            new Notification("ReSync", helloSent ? "Bridge Waiting" : "Bridge Not Ready", Notification.Type.WARN);
            return;
        }
        if (transport == null) {
            new Notification("ReSync", "Bridge Transport Missing", Notification.Type.WARN);
            return;
        }
        RemotelyClient.INSTANCE.openLiveReSyncStudio(new ReSyncLiveServerSession(liveServerId, displayName, transport));
    }

    private void sendHello() {
        nextHelloNanos = System.nanoTime() + HELLO_RETRY_NANOS;
        if (!channelRegistered) {
            channelRegistered = adapter.registerBridgeChannel();
        }
        Minecraft client = Minecraft.getInstance();
        UUID playerId = client.player != null ? client.player.getUUID() : UUID.randomUUID();
        String address = client.getCurrentServer() != null ? client.getCurrentServer().ip : "local";
        sessionId = UUID.nameUUIDFromBytes((address + ":" + playerId).getBytes(StandardCharsets.UTF_8));
        liveServerId = "live:" + address + ":" + playerId;
        byte[] versionBytes = Constants.VERSION.getBytes(StandardCharsets.UTF_8);
        byte[] addressBytes = address.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + versionBytes.length + 4 + addressBytes.length);
        buffer.putInt(1);
        buffer.putInt(versionBytes.length);
        buffer.put(versionBytes);
        buffer.putInt(addressBytes.length);
        buffer.put(addressBytes);
        final boolean[] sent = {false};
        chunker.send(sessionId, sequence.getAndIncrement(), ReSyncBridgeEnvelope.HELLO, buffer.array(), envelope -> sent[0] = adapter.send(envelope.encode()) || sent[0]);
        helloSent = sent[0];
    }

    private void handleAuthResult(UUID serverSessionId, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        if (buffer.remaining() < 1) {
            return;
        }
        boolean success = buffer.get() == 1;
        if (!success) {
            if (buffer.remaining() >= 4) {
                buffer.getInt();
            }
            String reason = readString(buffer, "ReSync Unavailable");
            authenticated = false;
            liveServerId = null;
            new Notification("ReSync", "No Permission".equals(reason) ? "No Permission" : "ReSync Unavailable", Notification.Type.WARN);
            return;
        }
        if (buffer.remaining() >= 4) {
            int resyncProtocol = buffer.getInt();
            if (resyncProtocol != 2) {
                authenticated = false;
                new Notification("ReSync", "ReSync Unavailable", Notification.Type.WARN);
                return;
            }
        }
        sessionId = serverSessionId;
        displayName = readString(buffer, "Live Server");
        supportedChannels = readChannels(buffer);
        transport = new BridgeTransport();
        authenticated = true;
    }

    private Set<String> readChannels(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return Set.of();
        }
        int count = buffer.getInt();
        if (count <= 0 || count > 256) {
            return Set.of();
        }
        Set<String> channels = new HashSet<>();
        for (int index = 0; index < count; index++) {
            String channel = readString(buffer, "");
            if (!channel.isBlank()) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private String readString(ByteBuffer buffer, String fallback) {
        if (buffer.remaining() < 4) {
            return fallback;
        }
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            return fallback;
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        return value.isBlank() ? fallback : value;
    }

    private void reset() {
        closeLiveSession();
        helloSent = false;
        lastConnection = null;
    }

    private void closeLiveSession() {
        if (transport != null) {
            transport.remoteClose();
        }
        if (liveServerId != null && FlowManager.getInstance() != null) {
            FlowManager.getInstance().clearLiveReSyncSession(liveServerId);
        }
        chunker.clear();
        authenticated = false;
        channelRegistered = false;
        liveServerId = null;
        supportedChannels = Set.of();
        transport = null;
    }

    private class BridgeTransport implements ReSyncFrameTransport {
        private Consumer<byte[]> frameHandler;
        private Runnable closeHandler;
        private boolean open = true;

        @Override
        public void setFrameHandler(Consumer<byte[]> handler) {
            this.frameHandler = handler;
        }

        @Override
        public void setCloseHandler(Runnable handler) {
            this.closeHandler = handler;
        }

        @Override
        public void send(byte[] frame) {
            if (open) {
                chunker.send(sessionId, sequence.getAndIncrement(), ReSyncBridgeEnvelope.DATA, frame, envelope -> adapter.send(envelope.encode()));
            }
        }

        @Override
        public void close() {
            if (!open) {
                return;
            }
            open = false;
            chunker.send(sessionId, sequence.getAndIncrement(), ReSyncBridgeEnvelope.CLOSE, new byte[0], envelope -> adapter.send(envelope.encode()));
            if (closeHandler != null) {
                ScreenManager.getInstance().execute(closeHandler);
            }
        }

        @Override
        public boolean isOpen() {
            return open && authenticated;
        }

        private void receive(byte[] frame) {
            if (open && frameHandler != null) {
                frameHandler.accept(frame);
            }
        }

        private void remoteClose() {
            open = false;
            if (closeHandler != null) {
                ScreenManager.getInstance().execute(closeHandler);
            }
        }
    }
}
