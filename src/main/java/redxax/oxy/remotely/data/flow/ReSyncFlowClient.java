package redxax.oxy.remotely.data.flow;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import redxax.oxy.remotely.RemotelyClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowDataTypeAdapter;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.cache.NodeRegistryCache;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.NodeRegistryRequest;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.GuiEditOverlayState;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import redxax.oxy.remotely.flow.ui.ScoreboardDesignerScreen;
import redxax.oxy.remotely.flow.ui.TabDesignerScreen;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingUpdate;
import redxax.oxy.remotely.data.flow.world.WorldChannelMessage;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenSerializer;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.util.Notification;
import restudio.rebase.restudio.api.ReStudioApiClient;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ReSyncFlowClient {
    public interface ErrorListener {
        void onError(String nodeId, String message);
    }

    public interface PluginChannelListener {
        void onData(String channelId, byte[] payload);

        default void onRemoved(String channelId) {
        }
    }

    private final String serverId;
    private final ReStudioApiClient apiClient;
    private final String directWsUrl;
    private final String directApiKey;
    private final RemotelyClient client;
    private final ReSyncFrameTransport frameTransport;
    private final AtomicReference<WebSocketClient> wsClient = new AtomicReference<>();
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private String apiKey;
    private final ReSyncFrameCodec frameCodec = new ReSyncFrameCodec();
    private static final int PROTOCOL_VERSION = ReSyncProtocolContract.PROTOCOL_VERSION;
    private static final String CLIENT_VERSION = "2.1.0";
    private static final short FLOW_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_FLOW_ID;
    private static final short PLAYER_TRACKING_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_PLAYER_TRACKING_ID;
    private static final short WORLD_MANAGEMENT_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_WORLD_MANAGEMENT_ID;
    private static final short WORLDGEN_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_WORLDGEN_ID;
    private static final short CONTROL_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_CONTROL_ID;
    private static final byte MESSAGE_CHANNEL_REGISTRY = (byte) 0x08;
    private final Map<String, Short> channelIds = new ConcurrentHashMap<>();
    private final Map<Short, String> numericChannels = new ConcurrentHashMap<>();
    private final Map<String, Set<PluginChannelListener>> pluginChannelListeners = new ConcurrentHashMap<>();
    private final Set<String> pluginChannelSubscriptions = ConcurrentHashMap.newKeySet();
    private int sequenceCounter = 0;
    private ErrorListener errorListener;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(FlowDataType.class, new FlowDataTypeAdapter())
            .registerTypeAdapter(NodeDefinition.NodeCategory.class, new TypeAdapter<NodeDefinition.NodeCategory>() {
                @Override
                public void write(JsonWriter out, NodeDefinition.NodeCategory value) throws IOException {
                    out.value(value != null ? value.getId() : null);
                }

                @Override
                public NodeDefinition.NodeCategory read(JsonReader in) throws IOException {
                    String id = in.nextString();
                    return NodeDefinition.NodeCategory.fromString(id);
                }
            })
            .create();
    private final Queue<Runnable> pendingSends = new ConcurrentLinkedQueue<>();
    private final Map<ReSyncResourceType, Set<String>> pendingOpenResources = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> jobs = new ConcurrentHashMap<>();
    private final Set<String> terminalJobNotifications = ConcurrentHashMap.newKeySet();
    private final NodeRegistryCache nodeRegistryCache = NodeRegistryCache.getInstance();
    private final ScheduledExecutorService nodeRegistryScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> nodeRegistryTimeout;
    private volatile boolean nodeRegistrySynced = false;
    private volatile boolean usingCachedRegistry = false;
    private static final int NODE_REGISTRY_TIMEOUT_SECONDS = 5;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ReSyncFlow-Heartbeat");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> reconnectTask;
    private ScheduledFuture<?> connectTimeoutTask;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;
    private static final int RECONNECT_DELAY_SECONDS = 3;
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private volatile boolean shutdownRequested = false;
    private final AtomicInteger placeholderRequestCounter = new AtomicInteger(1);
    private final Map<Integer, Consumer<String>> placeholderPreviewCallbacks = new ConcurrentHashMap<>();
    private final WorldGenProtocolHandler worldGenProtocolHandler;
    private final String stableClientId;

    public ReSyncFlowClient(String serverId, ReStudioApiClient apiClient, RemotelyClient client) {
        this(serverId, apiClient, null, null, client);
    }

    public ReSyncFlowClient(String serverId, ReStudioApiClient apiClient, String directWsUrl, String directApiKey, RemotelyClient client) {
        this.serverId = serverId;
        this.apiClient = apiClient;
        this.directWsUrl = directWsUrl;
        this.directApiKey = directApiKey;
        this.client = client;
        this.frameTransport = null;
        this.worldGenProtocolHandler = new WorldGenProtocolHandler(serverId, gson, this::trackGenericJob);
        this.stableClientId = "remotely-" + UUID.nameUUIDFromBytes((serverId == null ? "default" : serverId).getBytes(StandardCharsets.UTF_8));
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            pendingOpenResources.put(type, ConcurrentHashMap.newKeySet());
        }
        loadCachedRegistry();
    }

    public ReSyncFlowClient(String serverId, ReSyncFrameTransport frameTransport, RemotelyClient client) {
        this.serverId = serverId;
        this.apiClient = null;
        this.directWsUrl = null;
        this.directApiKey = null;
        this.client = client;
        this.frameTransport = frameTransport;
        this.worldGenProtocolHandler = new WorldGenProtocolHandler(serverId, gson, this::trackGenericJob);
        this.stableClientId = "remotely-" + UUID.nameUUIDFromBytes((serverId == null ? "default" : serverId).getBytes(StandardCharsets.UTF_8));
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            pendingOpenResources.put(type, ConcurrentHashMap.newKeySet());
        }
        loadCachedRegistry();
    }

    public void setErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }

    public boolean subscribePluginChannel(String channelId) {
        if (!isPluginChannel(channelId)) {
            return false;
        }
        pluginChannelSubscriptions.add(channelId);
        if (!channelIds.containsKey(channelId)) {
            return false;
        }
        sendSubscribe(channelId);
        return true;
    }

    public void unsubscribePluginChannel(String channelId) {
        if (!isPluginChannel(channelId)) {
            return;
        }
        pluginChannelSubscriptions.remove(channelId);
        if (!channelIds.containsKey(channelId)) {
            return;
        }
        byte[] channelBytes = channelId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + channelBytes.length);
        buffer.putInt(channelBytes.length);
        buffer.put(channelBytes);
        sendFrame(ReSyncProtocolContract.MESSAGE_UNSUBSCRIBE, buffer.array(), CONTROL_CHANNEL_ID);
    }

    public boolean sendPluginData(String channelId, byte[] payload) {
        if (!isPluginChannel(channelId)) {
            return false;
        }
        Short channel = channelIds.get(channelId);
        if (channel == null) {
            return false;
        }
        sendFrame(ReSyncProtocolContract.MESSAGE_DATA, payload, channel);
        return true;
    }

    public void addPluginChannelListener(String channelId, PluginChannelListener listener) {
        if (!isPluginChannel(channelId) || listener == null) {
            return;
        }
        pluginChannelListeners.computeIfAbsent(channelId, ignored -> ConcurrentHashMap.newKeySet()).add(listener);
    }

    public void removePluginChannelListener(String channelId, PluginChannelListener listener) {
        Set<PluginChannelListener> listeners = pluginChannelListeners.get(channelId);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public CompletableFuture<Void> connect() {
        if (frameTransport != null) {
            return connectFrameTransport();
        }
        if (isConnected() || connecting.get()) {
            return CompletableFuture.completedFuture(null);
        }
        WebSocketClient existingClient = wsClient.get();
        if (existingClient != null && existingClient.isOpen() && !authenticated.get()) {
            existingClient.close();
            wsClient.compareAndSet(existingClient, null);
        }
        connecting.set(true);
        nodeRegistrySynced = false;
        scheduleConnectTimeout();
        System.out.println("[ReSyncFlow] Attempting to connect to serverId=" + serverId);

        if (directWsUrl != null && !directWsUrl.isBlank()) {
            this.apiKey = directApiKey;
            if (this.apiKey == null || this.apiKey.isBlank()) {
                System.err.println("[ReSyncFlow] Direct ReSync apiKey is empty or null");
                connecting.set(false);
                cancelConnectTimeout();
                if (errorListener != null) {
                    errorListener.onError(null, "ReSyncApiKeyMissing");
                }
                return CompletableFuture.completedFuture(null);
            }
            System.out.println("[ReSyncFlow] Connecting with direct endpoint: " + directWsUrl);
            initWebSocketConnection(normalizeWsUrl(directWsUrl));
            return CompletableFuture.completedFuture(null);
        }

        return apiClient.getReSyncConfig(serverId).thenCompose(config -> {
            if (config != null && config.port > 0) {
                System.out.println("[ReSyncFlow] Got ReSync config: port=" + config.port);

                String serverUrl = apiClient.getServers().join().stream()
                    .filter(s -> serverId.equals(s.identifier))
                    .findFirst()
                    .map(s -> {
                        String ip = (s.ipAlias != null && !s.ipAlias.isEmpty()) ? s.ipAlias : s.ip;
                        System.out.println("[ReSyncFlow] Found server: " + s.name + ", ip: " + ip + ", ipAlias: " + s.ipAlias);
                        return ip + ":" + config.port;
                    })
                    .orElse(null);

                if (serverUrl != null) {
                    return apiClient.getReSyncApiKey(serverId).thenAccept(key -> {
                        this.apiKey = key;
                        if (this.apiKey != null && !this.apiKey.isEmpty()) {
                            String wsUrl = normalizeWsUrl(serverUrl);
                            System.out.println("[ReSyncFlow] Connecting to: " + wsUrl);
                            System.out.println("[ReSyncFlow] Using API key");
                            initWebSocketConnection(wsUrl);
                        } else {
                            System.err.println("[ReSyncFlow] API key is empty or null");
                            connecting.set(false);
                            cancelConnectTimeout();
                            if (errorListener != null) {
                                errorListener.onError(null, "ReSyncApiKeyMissing");
                            }
                        }
                    });
                } else {
                    System.err.println("[ReSyncFlow] Server not found in server list");
                    connecting.set(false);
                    cancelConnectTimeout();
                    if (errorListener != null) {
                        errorListener.onError(null, "ReSyncServerNotFound");
                    }
                    return CompletableFuture.completedFuture(null);
                }
            } else {
                System.err.println("[ReSyncFlow] No ReSync config found - ReSync is not enabled on this server");
                connecting.set(false);
                cancelConnectTimeout();
                if (errorListener != null) {
                    errorListener.onError(null, "ReSyncNotEnabled");
                }
                return CompletableFuture.completedFuture(null);
            }
        }).exceptionally(e -> {
            System.err.println("[ReSyncFlow] Error connecting to ReSync: " + e.getMessage());
            connecting.set(false);
            cancelConnectTimeout();
            if (errorListener != null) {
                errorListener.onError(null, "ReSyncConnectFailed: " + e.getMessage());
            }
            return null;
        });
    }

    private void initWebSocketConnection(String wsUrl) {
        try {
            URI uri = URI.create(wsUrl);
            System.out.println("[ReSyncFlow] Connecting to WebSocket: " + wsUrl);

            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("[ReSyncFlow] WebSocket connection opened successfully");
                    System.out.println("[ReSyncFlow] Sending handshake...");
                    sendHandshake();
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("[ReSyncFlow] Received text message: " + message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    handleBinaryMessage(copyRemaining(bytes));
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    authenticated.set(false);
                    connecting.set(false);
                    cancelConnectTimeout();
                    nodeRegistrySynced = false;
                    cancelNodeRegistryTimeout();
                    stopHeartbeat();
                    System.out.println("[ReSyncFlow] WebSocket closed - Code: " + code + ", Reason: " + reason + ", Remote: " + remote);
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("[ReSyncFlow] WebSocket error: " + ex.getMessage());
                    connecting.set(false);
                    cancelConnectTimeout();
                    scheduleReconnect();
                }
            };
            wsClient.set(client);
            client.connect();
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Failed to open WebSocket: " + e.getMessage());
            connecting.set(false);
            cancelConnectTimeout();
        }
    }

    private void sendHandshake() {
        String clientId = stableClientId;
        System.out.println("[ReSyncFlow] Client ID: " + clientId);

        ByteBuffer buffer = ByteBuffer.allocate(
                4 + apiKey.getBytes().length +
                        4 + clientId.getBytes().length +
                        4 +
                        4 + CLIENT_VERSION.getBytes().length
        );

        buffer.putInt(apiKey.getBytes().length);
        buffer.put(apiKey.getBytes());

        buffer.putInt(clientId.getBytes().length);
        buffer.put(clientId.getBytes());

        buffer.putInt(PROTOCOL_VERSION);
        buffer.putInt(CLIENT_VERSION.getBytes().length);
        buffer.put(CLIENT_VERSION.getBytes());

        sendFrame(0, buffer.array(), (short) 0);
    }

    private void subscribeStartupChannels() {
        sendSubscribe("flow");
        sendSubscribe("player_tracking");
        sendSubscribe("world_management");
        sendSubscribe("worldgen");
    }

    private CompletableFuture<Void> connectFrameTransport() {
        if (isConnected() || connecting.get()) {
            return CompletableFuture.completedFuture(null);
        }
        if (frameTransport == null || !frameTransport.isOpen()) {
            if (errorListener != null) {
                errorListener.onError(null, "ReSyncUnavailable");
            }
            return CompletableFuture.completedFuture(null);
        }
        connecting.set(true);
        nodeRegistrySynced = false;
        scheduleConnectTimeout();
        frameTransport.setFrameHandler(this::handleBinaryMessage);
        frameTransport.setCloseHandler(() -> {
            authenticated.set(false);
            connecting.set(false);
            cancelConnectTimeout();
            nodeRegistrySynced = false;
            cancelNodeRegistryTimeout();
            stopHeartbeat();
        });
        this.apiKey = "bridge";
        sendHandshake();
        return CompletableFuture.completedFuture(null);
    }

    private void sendSubscribe(String channelId) {
        byte[] channelBytes = channelId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + channelBytes.length + 4);
        buffer.putInt(channelBytes.length);
        buffer.put(channelBytes);
        buffer.putInt(0);
        sendFrame(2, buffer.array(), CONTROL_CHANNEL_ID);
    }

    private void sendFrame(int messageType, byte[] payload, short channel) {
        if (frameTransport != null) {
            if (frameTransport.isOpen()) {
                frameTransport.send(frameCodec.encode(messageType, payload, channel, sequenceCounter++));
            }
            return;
        }
        WebSocketClient client = wsClient.get();
        if (client != null && client.isOpen()) {
            client.send(frameCodec.encode(messageType, payload, channel, sequenceCounter++));
        }
    }

    private static byte[] copyRemaining(ByteBuffer buffer) {
        ByteBuffer copy = buffer.slice();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private void handleBinaryMessage(byte[] data) {
        try {
            ReSyncDecodedFrame frame = frameCodec.decode(data, validDataChannels());
            System.out.println("[ReSyncFlow] Received message: Type=" + frame.messageType() + ", Channel=" + frame.channel() + ", Sequence=" + frame.sequence() + ", Compressed=" + frame.compressed() + ", PayloadSize=" + frame.payload().length);
            switch (frame.messageType()) {
                case ReSyncProtocolContract.MESSAGE_HANDSHAKE_RESPONSE:
                    System.out.println("[ReSyncFlow] Processing handshake response");
                    handleHandshakeResponse(frame.payload());
                    break;
                case ReSyncProtocolContract.MESSAGE_DATA:
                    System.out.println("[ReSyncFlow] Processing data message on channel " + frame.channel());
                    handleDataMessage(frame.channel(), frame.payload());
                    break;
                case ReSyncProtocolContract.MESSAGE_HEARTBEAT:
                    break;
                case ReSyncProtocolContract.MESSAGE_ERROR:
                    System.err.println("[ReSyncFlow] Processing error message");
                    handleError(frame.payload());
                    break;
                case MESSAGE_CHANNEL_REGISTRY:
                    handleChannelRegistry(frame.payload());
                    break;
                default:
                    protocolError("Unknown message type: " + (frame.messageType() & 0xFF));
            }
        } catch (IllegalArgumentException exception) {
            protocolError(exception.getMessage());
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Error processing message: " + e.getMessage());
        }
    }

    private Set<Short> validDataChannels() {
        Set<Short> channels = ConcurrentHashMap.newKeySet();
        channels.add(numericChannel("flow", FLOW_CHANNEL_ID));
        channels.add(numericChannel("player_tracking", PLAYER_TRACKING_CHANNEL_ID));
        channels.add(numericChannel("world_management", WORLD_MANAGEMENT_CHANNEL_ID));
        channels.add(numericChannel("worldgen", WORLDGEN_CHANNEL_ID));
        channels.addAll(channelIds.values());
        return channels;
    }

    private void protocolError(String message) {
        System.err.println("[ReSyncFlow] Protocol error: " + message);
        if (errorListener != null) {
            errorListener.onError(null, "ReSyncProtocolError: " + message);
        }
    }

    private void handleHandshakeResponse(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        if (buffer.remaining() < 1) {
            protocolError("Handshake response too short");
            return;
        }

        byte success = buffer.get();
        if (success != 1) {
            System.out.println("[ReSyncFlow] Handshake failed - server rejected connection");
            return;
        }

        if (buffer.remaining() < 4) {
            protocolError("Handshake message length missing");
            return;
        }
        int messageLen = buffer.getInt();
        if (messageLen < 0 || messageLen > buffer.remaining()) {
            protocolError("Invalid handshake message length");
            return;
        }
        buffer.position(buffer.position() + messageLen);

        if (buffer.remaining() < 4) {
            protocolError("Handshake protocol version missing");
            return;
        }
        int protocolVersion = buffer.getInt();
        System.out.println("[ReSyncFlow] Server protocol version: " + protocolVersion);

        if (buffer.remaining() < 4) {
            protocolError("Handshake server version length missing");
            return;
        }
        int serverVersionLen = buffer.getInt();
        if (serverVersionLen < 0 || serverVersionLen > buffer.remaining()) {
            protocolError("Invalid handshake server version length");
            return;
        }
        buffer.position(buffer.position() + serverVersionLen);

        if (buffer.remaining() < 4) {
            protocolError("Handshake world count missing");
            return;
        }
        int worldCount = buffer.getInt();
        if (worldCount < 0) {
            protocolError("Invalid handshake world count");
            return;
        }
        System.out.println("[ReSyncFlow] Available worlds: " + worldCount);
        for (int i = 0; i < worldCount; i++) {
            if (readSizedString(buffer) == null) {
                protocolError("Invalid handshake world entry");
                return;
            }
        }
        if (buffer.remaining() >= 4) {
            int tileSizeCount = buffer.getInt();
            if (tileSizeCount < 0 || buffer.remaining() < tileSizeCount * Integer.BYTES) {
                protocolError("Invalid handshake tile size list");
                return;
            }
            buffer.position(buffer.position() + tileSizeCount * Integer.BYTES);
        }
        if (buffer.remaining() >= 4) {
            int channelCount = buffer.getInt();
            for (int i = 0; i < channelCount; i++) {
                String channelName = readSizedString(buffer);
                if (channelName == null || buffer.remaining() < 4) {
                    protocolError("Invalid handshake channel entry");
                    return;
                }
                int numericId = buffer.getInt();
                if (numericId >= 0 && numericId <= 0xFFFF) {
                    registerChannel(channelName, (short) numericId);
                }
            }
        }

        authenticated.set(true);
        connecting.set(false);
        cancelConnectTimeout();
        System.out.println("[ReSyncFlow] Handshake complete, client authenticated");
        subscribeStartupChannels();
        startHeartbeat();
        requestNodeRegistry();
        requestJobSnapshots();
        flushPendingSends();
    }

    private String readSizedString(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return null;
        }
        int length = buffer.getInt();
        if (length < 0 || buffer.remaining() < length) {
            return null;
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String readRemainingJson(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void handleChannelRegistry(byte[] payload) {
        JsonObject root = gson.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
        if (root == null) {
            return;
        }
        boolean snapshot = root.has("snapshot") && root.get("snapshot").getAsBoolean();
        Set<String> previousPluginChannels = ConcurrentHashMap.newKeySet();
        if (snapshot) {
            for (String channelId : channelIds.keySet()) {
                if (isPluginChannel(channelId)) {
                    previousPluginChannels.add(channelId);
                }
            }
        }
        if (snapshot) {
            channelIds.clear();
            numericChannels.clear();
        }
        JsonObject channels = root.has("channels") && root.get("channels").isJsonObject() ? root.getAsJsonObject("channels") : null;
        if (channels != null) {
            for (Map.Entry<String, JsonElement> entry : channels.entrySet()) {
                int numericId = entry.getValue().getAsInt();
                if (numericId >= 0 && numericId <= 0xFFFF) {
                    registerChannel(entry.getKey(), (short) numericId);
                }
            }
        }
        if (snapshot) {
            for (String channelId : previousPluginChannels) {
                if (!channelIds.containsKey(channelId)) {
                    removeChannel(channelId);
                }
            }
        }
        JsonElement removed = root.get("removedChannels");
        if (removed != null && removed.isJsonArray()) {
            for (JsonElement element : removed.getAsJsonArray()) {
                if (element != null && !element.isJsonNull()) {
                    removeChannel(element.getAsString());
                }
            }
        }
        for (String channelId : new ArrayList<>(pluginChannelSubscriptions)) {
            if (isPluginChannel(channelId) && channelIds.containsKey(channelId)) {
                sendSubscribe(channelId);
            }
        }
    }

    private void registerChannel(String channelId, short numericId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        channelIds.put(channelId, numericId);
        numericChannels.put(numericId, channelId);
    }

    private void removeChannel(String channelId) {
        Short numericId = channelIds.remove(channelId);
        if (numericId != null) {
            numericChannels.remove(numericId);
        }
        pluginChannelSubscriptions.remove(channelId);
        Set<PluginChannelListener> listeners = pluginChannelListeners.remove(channelId);
        if (listeners != null) {
            for (PluginChannelListener listener : listeners) {
                listener.onRemoved(channelId);
            }
        }
    }

    private boolean isPluginChannel(String channelId) {
        return channelId != null
            && !"flow".equals(channelId)
            && !"player_tracking".equals(channelId)
            && !"world_management".equals(channelId)
            && !"worldgen".equals(channelId);
    }

    private void dispatchPluginChannelData(String channelId, byte[] data) {
        Set<PluginChannelListener> listeners = pluginChannelListeners.get(channelId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        byte[] payload = data == null ? new byte[0] : data.clone();
        for (PluginChannelListener listener : listeners) {
            listener.onData(channelId, payload.clone());
        }
    }

    private short numericChannel(String channelId, short fallback) {
        Short numericId = channelIds.get(channelId);
        return numericId == null ? fallback : numericId;
    }

    private void scheduleConnectTimeout() {
        cancelConnectTimeout();
        connectTimeoutTask = heartbeatScheduler.schedule(() -> {
            if (authenticated.get() || shutdownRequested) {
                return;
            }
            connecting.set(false);
            WebSocketClient client = wsClient.get();
            if (client != null && !authenticated.get()) {
                client.close();
                wsClient.compareAndSet(client, null);
            }
            if (errorListener != null) {
                errorListener.onError(null, "ReSync Connection Timed Out");
            }
        }, CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelConnectTimeout() {
        if (connectTimeoutTask != null) {
            connectTimeoutTask.cancel(false);
            connectTimeoutTask = null;
        }
    }

    private void handleDataMessage(short channel, byte[] data) {
        if (channel == numericChannel("player_tracking", PLAYER_TRACKING_CHANNEL_ID)) {
            handlePlayerTrackingMessage(data);
            return;
        }

        if (channel == numericChannel("world_management", WORLD_MANAGEMENT_CHANNEL_ID)) {
            handleWorldManagementMessage(data);
            return;
        }

        if (channel == numericChannel("worldgen", WORLDGEN_CHANNEL_ID)) {
            handleWorldGenMessage(data);
            return;
        }

        if (channel != numericChannel("flow", FLOW_CHANNEL_ID)) {
            String channelId = numericChannels.get(channel);
            if (channelId != null && isPluginChannel(channelId)) {
                dispatchPluginChannelData(channelId, data);
                return;
            }
            protocolError("Unknown channel: " + (channel & 0xFFFF));
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        if (!buffer.hasRemaining()) {
            protocolError("Empty flow packet");
            return;
        }
        byte packetId = buffer.get();

        switch (packetId) {
            case 0x02:
                handleFlowData(buffer);
                break;
            case 0x04:
                handleGuiState(buffer);
                break;
            case 0x05:
                handleFlowError(buffer);
                break;
            case 0x07:
                handleFlowSaveAck(buffer);
                break;
            case 0x0A:
                handleFlowList(buffer);
                break;
            case 0x12:
                handleGuiData(buffer);
                break;
            case 0x15:
                handleGuiList(buffer);
                break;
            case 0x17:
                handleGuiSaveAck(buffer);
                break;
            case 0x1C:
                handleScoreboardData(buffer);
                break;
            case 0x1D:
                handleScoreboardList(buffer);
                break;
            case 0x1E:
                handleScoreboardSaveAck(buffer);
                break;
            case 0x24:
                handleTabData(buffer);
                break;
            case 0x25:
                handleTabList(buffer);
                break;
            case 0x26:
                handleTabSaveAck(buffer);
                break;
            case 0x31:
                handleCustomContentList(buffer);
                break;
            case 0x32:
                handleCustomContentData(buffer);
                break;
            case 0x35:
                handleCustomContentSaveAck(buffer);
                break;
            case 0x52:
                handleProjectMetadataData(buffer);
                break;
            case 0x53:
                handleProjectMetadataList(buffer);
                break;
            case 0x56:
                handleProjectMetadataSaveAck(buffer);
                break;
            case 0x44:
                handleFlowJob(buffer);
                break;
            case 0x28:
                handlePlaceholderPreview(buffer);
                break;
            case 0x38:
                handleOptionCatalog(buffer);
                break;
            case 0x41:
                handleTraceSnapshot(buffer);
                break;
            case 0x42:
                handleTraceEvent(buffer);
                break;
            case 0x47:
                handleDebugSnapshot(buffer);
                break;
            case 0x0B:
                handleNodeRegistrySnapshot(buffer, true);
                break;
            case 0x0D:
                handleNodeRegistrySnapshot(buffer, false);
                break;
        }
    }

    private void handlePlayerTrackingMessage(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            PlayerTrackingUpdate update = gson.fromJson(json, PlayerTrackingUpdate.class);
            if (update == null) {
                return;
            }
            if (client != null && client.getFlowManager() != null) {
                client.getFlowManager().applyPlayerTrackingUpdate(serverId, update);
            }
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Failed to parse player tracking update: " + e.getMessage());
        }
    }

    private void handleWorldManagementMessage(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            WorldChannelMessage message = gson.fromJson(json, WorldChannelMessage.class);
            if (message == null) {
                return;
            }
            trackWorldJob(message);
            if (client != null && client.getFlowManager() != null) {
                client.getFlowManager().applyWorldManagementMessage(serverId, message);
            }
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Failed to parse world management update: " + e.getMessage());
        }
    }

    private void trackWorldJob(WorldChannelMessage message) {
        if (message == null || message.getData() == null) {
            return;
        }
        if (!"job".equals(message.getType()) && !"jobStatus".equals(message.getAction()) && !"jobAccepted".equals(message.getAction())) {
            return;
        }
        if (message.getData().isJsonArray()) {
            for (JsonElement item : message.getData().getAsJsonArray()) {
                if (item.isJsonObject()) {
                    trackGenericJob(item.getAsJsonObject());
                }
            }
            return;
        }
        if (message.getData().isJsonObject()) {
            JsonObject data = message.getData().getAsJsonObject();
            trackGenericJob(data);
        }
    }

    private void handleFlowJob(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        JsonObject envelope = gson.fromJson(json, JsonObject.class);
        if (envelope != null && envelope.has("data")) {
            trackJobElement(envelope.get("data"));
        } else {
            trackGenericJob(envelope);
        }
    }

    private void handleTraceSnapshot(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        FlowManager manager = client != null ? client.getFlowManager() : null;
        if (manager != null && manager.getDebugController() != null) {
            manager.getDebugController().applyTraceSnapshot(serverId, json);
        }
    }

    private void handleTraceEvent(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        FlowManager manager = client != null ? client.getFlowManager() : null;
        if (manager != null && manager.getDebugController() != null) {
            manager.getDebugController().applyTraceEvent(serverId, json);
        }
    }

    private void handleDebugSnapshot(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        FlowManager manager = client != null ? client.getFlowManager() : null;
        if (manager != null && manager.getDebugController() != null) {
            manager.getDebugController().applyDebugSnapshot(serverId, json);
        }
    }

    private void trackJobElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                trackJobElement(item);
            }
            return;
        }
        if (element.isJsonObject()) {
            trackGenericJob(element.getAsJsonObject());
        }
    }

    private void trackGenericJob(JsonObject data) {
        if (data == null) {
            return;
        }
        if (data.has("data")) {
            trackJobElement(data.get("data"));
            return;
        }
        String jobId = stringField(data, "jobId");
        if (jobId == null || jobId.isBlank()) {
            jobId = stringField(data, "operationId");
        }
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        JsonObject previous = jobs.put(jobId, data);
        String status = stringField(data, "status");
        String action = stringField(data, "action");
        String previousStatus = previous != null ? stringField(previous, "status") : null;
        boolean duplicateTerminal = status != null && status.equalsIgnoreCase(previousStatus) && isTerminalJobStatus(status);
        if (duplicateTerminal) {
            return;
        }
        if ("succeeded".equalsIgnoreCase(status)) {
            refreshAfterJob(action);
        } else if ("failed".equalsIgnoreCase(status)) {
            if (!terminalJobNotifications.add(jobId)) {
                return;
            }
            String reason = stringField(data, "errorText");
            if (reason == null || reason.isBlank()) {
                reason = stringField(data, "message");
            }
            String title = action == null || action.isBlank() ? "ReSync Failed" : action + " Failed";
            String message = reason == null || reason.isBlank() ? "Failed" : reason;
            ScreenManager.getInstance().execute(() -> new Notification(title, message, Notification.Type.ERROR));
        }
    }

    private void refreshAfterJob(String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        switch (action) {
            case "saveFlow", "deleteFlow" -> requestFlowList();
            case "saveGui", "deleteGui" -> requestGuiList();
            case "saveScoreboard", "deleteScoreboard" -> requestScoreboardList();
            case "saveTab", "deleteTab" -> requestTabList();
            case "saveCustomContent", "deleteCustomContent" -> requestCustomContentList();
            case "saveProjectMetadata", "deleteProjectMetadata" -> {}
            case "saveWorldGenProject", "deleteWorldGenProject" -> requestWorldGenProjectList();
            default -> {
            }
        }
    }

    private boolean isTerminalJobStatus(String status) {
        return "succeeded".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status);
    }

    private String stringField(JsonObject data, String name) {
        if (data == null || !data.has(name) || data.get(name).isJsonNull()) {
            return null;
        }
        return data.get(name).getAsString();
    }

    private void handleWorldGenMessage(byte[] data) {
        worldGenProtocolHandler.handle(data);
    }

    private void handleResourceData(ReSyncResourceType type, ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        Object item = type.deserialize(json);
        FlowManager fm = client != null ? client.getFlowManager() : null;
        if (fm != null) {
            if (type == ReSyncResourceType.TAB) {
                try {
                    cacheResource(fm, type, item);
                    handleResourceDataReceived(fm, type, item);
                } catch (NoSuchMethodError ignored) {
                }
            } else {
                cacheResource(fm, type, item);
                handleResourceDataReceived(fm, type, item);
            }
        }
        if (item != null) {
            String itemId = type.extractId(item);
            if (itemId != null && pendingOpenResources.get(type).remove(itemId)) {
                ScreenManager.getInstance().execute(() -> {
                    if (client != null && client.getHost() != null) {
                        if (type == ReSyncResourceType.FLOW) {
                            FlowGraph graph = (FlowGraph) item;
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            if (studioScreen != null) {
                                studioScreen.openWorkspaceFlowEditor(itemId);
                                return;
                            }
                            Screen current = ScreenManager.getInstance().getCurrentScreen();
                            if (current instanceof FlowEditorScreen screen
                                && serverId.equals(screen.getServerId())
                                && itemId.equals(screen.getFlowId())) {
                                screen.applyGraph(graph);
                                return;
                            }
                            client.getHost().setScreen(new FlowEditorScreen(graph, serverId, current));
                        } else if (type == ReSyncResourceType.GUI) {
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            if (studioScreen != null) {
                                studioScreen.openWorkspaceGuiDesigner(itemId);
                                return;
                            }
                            client.getHost().setScreen(new GuiDesignerScreen((GuiDefinition) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.SCOREBOARD) {
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            if (studioScreen != null) {
                                studioScreen.openWorkspaceScoreboardDesigner(itemId);
                                return;
                            }
                            client.getHost().setScreen(new ScoreboardDesignerScreen((ScoreboardDefinition) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.TAB) {
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            if (studioScreen != null) {
                                studioScreen.openWorkspaceTabDesigner(itemId);
                                return;
                            }
                            client.getHost().setScreen(new TabDesignerScreen((TabDefinition) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        }
                    }
                });
            }
        }
    }

    private void cacheResource(FlowManager fm, ReSyncResourceType type, Object item) {
        if (type == ReSyncResourceType.FLOW) fm.cacheFlow(serverId, (FlowGraph) item);
        else if (type == ReSyncResourceType.GUI) fm.cacheGui(serverId, (GuiDefinition) item);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.cacheScoreboard(serverId, (ScoreboardDefinition) item);
        else if (type == ReSyncResourceType.TAB) fm.cacheTab(serverId, (TabDefinition) item);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) fm.cacheCustomContent(serverId, (CustomContentDefinition) item);
        else if (type == ReSyncResourceType.PROJECT_METADATA) fm.cacheProjectMetadata(serverId, (ReSyncProjectMetadata) item);
    }

    private void handleResourceDataReceived(FlowManager fm, ReSyncResourceType type, Object item) {
        if (type == ReSyncResourceType.GUI) fm.handleGuiDataReceived(serverId, (GuiDefinition) item);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.handleScoreboardDataReceived(serverId, (ScoreboardDefinition) item);
        else if (type == ReSyncResourceType.TAB) fm.handleTabDataReceived(serverId, (TabDefinition) item);
    }

    private void markResourceSaved(FlowManager fm, ReSyncResourceType type, String id) {
        if (type == ReSyncResourceType.FLOW) fm.markFlowSaved(serverId, id);
        else if (type == ReSyncResourceType.GUI) fm.markGuiSaved(serverId, id);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.markScoreboardSaved(serverId, id);
        else if (type == ReSyncResourceType.TAB) fm.markTabSaved(serverId, id);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) fm.markCustomContentSaved(serverId, id);
        else if (type == ReSyncResourceType.PROJECT_METADATA) fm.markProjectMetadataSaved(serverId);
    }

    private void applyServerResourceList(FlowManager fm, ReSyncResourceType type, List<String> ids) {
        if (type == ReSyncResourceType.FLOW) fm.applyServerFlowList(serverId, ids);
        else if (type == ReSyncResourceType.GUI) fm.applyServerGuiList(serverId, ids);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.applyServerScoreboardList(serverId, ids);
        else if (type == ReSyncResourceType.TAB) fm.applyServerTabList(serverId, ids);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) fm.applyServerCustomContentList(serverId, ids);
        else if (type == ReSyncResourceType.PROJECT_METADATA) fm.applyServerProjectMetadataList(serverId, ids);
    }

    private void handleFlowData(ByteBuffer buffer) {
        handleResourceData(ReSyncResourceType.FLOW, buffer);
    }

    private void handleGuiData(ByteBuffer buffer) {
        handleResourceData(ReSyncResourceType.GUI, buffer);
    }

    private void handleScoreboardData(ByteBuffer buffer) {
        handleResourceData(ReSyncResourceType.SCOREBOARD, buffer);
    }

    private void handleTabData(ByteBuffer buffer) {
        handleResourceData(ReSyncResourceType.TAB, buffer);
    }

    private void handleCustomContentData(ByteBuffer buffer) {
        handleResourceData(ReSyncResourceType.CUSTOM_CONTENT, buffer);
    }

    private void handleProjectMetadataData(ByteBuffer buffer) {
        handleResourceData(ReSyncResourceType.PROJECT_METADATA, buffer);
    }

    private void handleGuiState(ByteBuffer buffer) {
        boolean editable = buffer.get() == 1;
        String guiId = null;
        String flowId = null;
        if (buffer.remaining() >= 4) {
            int guiLen = buffer.getInt();
            if (guiLen >= 0 && guiLen <= buffer.remaining()) {
                if (guiLen > 0) {
                    byte[] guiBytes = new byte[guiLen];
                    buffer.get(guiBytes);
                    guiId = new String(guiBytes, StandardCharsets.UTF_8);
                }
            }
        }
        if (buffer.remaining() >= 4) {
            int flowLen = buffer.getInt();
            if (flowLen >= 0 && flowLen <= buffer.remaining()) {
                if (flowLen > 0) {
                    byte[] flowBytes = new byte[flowLen];
                    buffer.get(flowBytes);
                    flowId = new String(flowBytes, StandardCharsets.UTF_8);
                }
            }
        }
        if (editable) {
            GuiEditOverlayState.update(serverId, guiId, flowId, true);
        } else {
            GuiEditOverlayState.clear();
        }
        if (client != null && client.getFlowManager() != null) {
            client.getFlowManager().handleGuiStatePacket(serverId, editable, guiId, flowId);
        }
    }

    private void handleFlowError(ByteBuffer buffer) {
        int messageLen = buffer.getInt();
        if (messageLen < 0 || messageLen > buffer.remaining()) return;

        byte[] messageBytes = new byte[messageLen];
        buffer.get(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);

        System.err.println("[ReSyncFlow] Flow Error: " + message);

        if (errorListener != null) {
            errorListener.onError(null, message);
        }

        ScreenManager.getInstance().execute(() ->
            new Notification("Flow Save Failed", message, Notification.Type.ERROR)
        );
    }

    private void handleResourceSaveAck(ReSyncResourceType type, ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int idLen = buffer.getInt();
        if (idLen < 0 || idLen > buffer.remaining()) {
            return;
        }
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        String id = new String(idBytes, StandardCharsets.UTF_8);

        boolean showNotification = shouldShowSaveNotification(type, id);
        if (showNotification) {
            ScreenManager.getInstance().execute(() ->
                new Notification(type.displayName() + " Saved", "ID: " + id, Notification.Type.SUCCESS)
            );
        }

        if (client != null && client.getFlowManager() != null) {
            if (type == ReSyncResourceType.TAB) {
                try {
                    markResourceSaved(client.getFlowManager(), type, id);
                } catch (NoSuchMethodError ignored) {
                }
            } else {
                markResourceSaved(client.getFlowManager(), type, id);
            }
        }
    }

    private boolean shouldShowSaveNotification(ReSyncResourceType type, String id) {
        if (type == ReSyncResourceType.PROJECT_METADATA) {
            return false;
        }
        return !isBackingFlowAck(type, id);
    }

    private boolean isBackingFlowAck(ReSyncResourceType type, String id) {
        if (type != ReSyncResourceType.FLOW || client == null || client.getFlowManager() == null || id == null) {
            return false;
        }
        FlowManager manager = client.getFlowManager();
        FlowGraph graph = manager.getFlowsForServer(serverId).get(id);
        if (CustomContentGraphAdapter.isContentGraph(graph) || manager.getCommandBinding(serverId, id) != null) {
            return true;
        }
        for (CustomContentDefinition content : manager.getCustomContentForServer(serverId).values()) {
            if (content != null && id.equals(content.getFlowId())) {
                return true;
            }
        }
        return false;
    }

    private void handleFlowSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.FLOW, buffer); }
    private void handleGuiSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.GUI, buffer); }
    private void handleScoreboardSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.SCOREBOARD, buffer); }
    private void handleTabSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.TAB, buffer); }
    private void handleCustomContentSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.CUSTOM_CONTENT, buffer); }
    private void handleProjectMetadataSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.PROJECT_METADATA, buffer); }

    private void handleResourceList(ReSyncResourceType type, ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int count = buffer.getInt();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (buffer.remaining() < 4) {
                break;
            }
            int len = buffer.getInt();
            if (len < 0 || len > buffer.remaining()) {
                break;
            }
            byte[] idBytes = new byte[len];
            buffer.get(idBytes);
            ids.add(new String(idBytes, StandardCharsets.UTF_8));
        }

        if (client != null && client.getFlowManager() != null) {
            if (type == ReSyncResourceType.TAB) {
                try {
                    applyServerResourceList(client.getFlowManager(), type, ids);
                } catch (NoSuchMethodError ignored) {
                }
            } else {
                applyServerResourceList(client.getFlowManager(), type, ids);
            }
        }
    }

    private void handleFlowList(ByteBuffer buffer) { handleResourceList(ReSyncResourceType.FLOW, buffer); }
    private void handleGuiList(ByteBuffer buffer) { handleResourceList(ReSyncResourceType.GUI, buffer); }
    private void handleScoreboardList(ByteBuffer buffer) { handleResourceList(ReSyncResourceType.SCOREBOARD, buffer); }
    private void handleTabList(ByteBuffer buffer) { handleResourceList(ReSyncResourceType.TAB, buffer); }
    private void handleCustomContentList(ByteBuffer buffer) { handleResourceList(ReSyncResourceType.CUSTOM_CONTENT, buffer); }
    private void handleProjectMetadataList(ByteBuffer buffer) { handleResourceList(ReSyncResourceType.PROJECT_METADATA, buffer); }

    private void handleNodeRegistrySnapshot(ByteBuffer buffer, boolean fullSync) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        try {
            NodeRegistrySnapshot snapshot = gson.fromJson(json, NodeRegistrySnapshot.class);
            if (snapshot == null) {
                return;
            }
            snapshot.setFullSync(fullSync || snapshot.isFullSync());
            NodeRegistry registry = NodeRegistry.getInstance();
            if (registry != null) {
                registry.applySnapshot(serverId, snapshot);
            }
            nodeRegistryCache.applySnapshot(serverId, snapshot);
            nodeRegistrySynced = true;
            usingCachedRegistry = false;
            cancelNodeRegistryTimeout();
            notifyNodeRegistryUpdated();
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Failed to parse node registry snapshot: " + e.getMessage());
        }
    }

    private void handlePlaceholderPreview(ByteBuffer buffer) {
        if (buffer.remaining() < 8) {
            return;
        }
        int requestId = buffer.getInt();
        int textLen = buffer.getInt();
        if (textLen < 0 || textLen > buffer.remaining()) {
            return;
        }
        byte[] textBytes = new byte[textLen];
        buffer.get(textBytes);
        String rendered = new String(textBytes, StandardCharsets.UTF_8);
        Consumer<String> callback = placeholderPreviewCallbacks.remove(requestId);
        if (callback != null) {
            ScreenManager.getInstance().execute(() -> callback.accept(rendered));
        }
    }

    private void handleOptionCatalog(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        try {
            OptionCatalogPayload payload = gson.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), OptionCatalogPayload.class);
            if (payload != null && payload.sourceId != null) {
                OptionCatalogCache.getInstance().put(serverId, payload.sourceId, payload.revision, payload.values, payload.items);
                ScreenManager.getInstance().execute(() -> {
                    FlowEditorScreen.refreshCatalogForServer(serverId);
                    GuiDesignerScreen.refreshCatalogForServer(serverId);
                });
            }
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Failed to parse option catalog: " + e.getMessage());
        }
    }

    public void requestNodeRegistry() {
        if (!isConnected()) {
            pendingSends.add(this::requestNodeRegistry);
            ensureConnected();
            return;
        }
        NodeRegistryRequest request = new NodeRegistryRequest();
        request.setPluginChecksums(nodeRegistryCache.getPluginChecksums(serverId));
        String json = gson.toJson(request);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put((byte) 0x0C);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
        scheduleNodeRegistryTimeout();
    }

    public void requestOptionCatalog(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> requestOptionCatalog(sourceId));
            ensureConnected();
            return;
        }
        byte[] sourceBytes = sourceId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + sourceBytes.length);
        buffer.put((byte) 0x37);
        buffer.putInt(sourceBytes.length);
        buffer.put(sourceBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private void requestJobSnapshots() {
        requestFlowJobSnapshot();
        requestWorldJobSnapshot();
        requestWorldGenJobSnapshot();
    }

    private void requestFlowJobSnapshot() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0x45);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private void requestWorldJobSnapshot() {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("action", "jobSnapshot");
        sendWorldRequest(request);
    }

    private void requestWorldGenJobSnapshot() {
        sendWorldGenJson((byte) 0x3A, "{}");
    }

    private void loadCachedRegistry() {
        NodeRegistrySnapshot cached = nodeRegistryCache.getSnapshot(serverId);
        if (cached == null) {
            return;
        }
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry != null) {
            registry.applySnapshot(serverId, cached);
        }
        usingCachedRegistry = true;
    }

    private void notifyNodeRegistryUpdated() {
        ScreenManager.getInstance().execute(() -> {
            Screen current = ScreenManager.getInstance().getCurrentScreen();
            if (current instanceof FlowEditorScreen screen && serverId.equals(screen.getServerId())) {
                screen.refreshNodeRegistry();
            }
        });
    }

    private void scheduleNodeRegistryTimeout() {
        cancelNodeRegistryTimeout();
        if (!usingCachedRegistry) {
            return;
        }
        nodeRegistryTimeout = nodeRegistryScheduler.schedule(() -> {
            if (!nodeRegistrySynced && usingCachedRegistry) {
                showCachedRegistryNotice();
            }
        }, NODE_REGISTRY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelNodeRegistryTimeout() {
        if (nodeRegistryTimeout != null) {
            nodeRegistryTimeout.cancel(false);
            nodeRegistryTimeout = null;
        }
    }

    private void showCachedRegistryNotice() {
        ScreenManager.getInstance().execute(() ->
            new Notification("Flow Nodes", "Using cached node definitions for " + serverId, Notification.Type.WARN)
        );
    }

    private void sendResourceRequest(ReSyncResourceType type, String id) {
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put(type.requestByte());
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private void requestResource(ReSyncResourceType type, String id, boolean openWhenReceived) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (openWhenReceived) {
            pendingOpenResources.get(type).add(id);
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendResourceRequest(type, id));
            ensureConnected();
            return;
        }
        sendResourceRequest(type, id);
    }

    private void requestResourceList(ReSyncResourceType type) {
        if (!isConnected()) {
            pendingSends.add(() -> requestResourceList(type));
            ensureConnected();
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put(type.listRequestByte());
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    public void requestFlow(String flowId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.FLOW, flowId, openWhenReceived);
    }

    public void requestFlowList() {
        requestResourceList(ReSyncResourceType.FLOW);
    }

    public void sendDebugCommand(Map<String, Object> command) {
        if (command == null || command.isEmpty()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendDebugCommand(command));
            ensureConnected();
            return;
        }
        byte[] jsonBytes = gson.toJson(command).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put((byte) 0x46);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    public void requestGui(String guiId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.GUI, guiId, openWhenReceived);
    }

    public void requestGuiList() {
        requestResourceList(ReSyncResourceType.GUI);
    }

    public void requestScoreboard(String scoreboardId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.SCOREBOARD, scoreboardId, openWhenReceived);
    }

    public void requestScoreboardList() {
        requestResourceList(ReSyncResourceType.SCOREBOARD);
    }

    public void requestTab(String tabId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.TAB, tabId, openWhenReceived);
    }

    public void requestTabList() {
        requestResourceList(ReSyncResourceType.TAB);
    }

    public void requestCustomContent(String contentId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.CUSTOM_CONTENT, contentId, openWhenReceived);
    }

    public void requestCustomContentList() {
        requestResourceList(ReSyncResourceType.CUSTOM_CONTENT);
    }

    public void requestProjectMetadata(String metadataId) {
        requestResource(ReSyncResourceType.PROJECT_METADATA, metadataId, false);
    }

    public void requestProjectMetadataList() {
        requestResourceList(ReSyncResourceType.PROJECT_METADATA);
    }

    public void requestPlaceholderPreview(String text, boolean usePapi, Consumer<String> callback) {
        String value = text != null ? text : "";
        if (callback == null) {
            return;
        }
        if (!isConnected()) {
            callback.accept(value);
            ensureConnected();
            return;
        }
        int requestId = placeholderRequestCounter.getAndIncrement();
        placeholderPreviewCallbacks.put(requestId, callback);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + valueBytes.length);
        buffer.put((byte) 0x27);
        buffer.putInt(requestId);
        buffer.put((byte) (usePapi ? 1 : 0));
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    public void requestPlayerTrackingSnapshot() {
        sendPlayerTrackingAction("snapshot", null);
    }

    public void requestPlayerDossier(UUID playerId) {
        sendPlayerTrackingAction("dossier", playerId);
    }

    public void requestWorldSnapshot() {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("action", "snapshot");
        sendWorldRequest(request);
    }

    public void requestWorldMapSnapshot(String worldName, double centerX, double centerZ, int zoom) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("action", "mapSnapshot");
        request.put("worldName", worldName);
        request.put("centerX", centerX);
        request.put("centerZ", centerZ);
        request.put("zoom", zoom);
        sendWorldRequest(request);
    }

    public void sendWorldAction(Map<String, Object> request) {
        sendWorldRequest(request);
    }

    private void sendPlayerTrackingAction(String action, UUID playerId) {
        if (action == null || action.isBlank()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendPlayerTrackingAction(action, playerId));
            ensureConnected();
            return;
        }
        PlayerTrackingRequest request = new PlayerTrackingRequest();
        request.action = action;
        request.playerId = playerId != null ? playerId.toString() : null;
        byte[] jsonBytes = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
        sendFrame(4, jsonBytes, numericChannel("player_tracking", PLAYER_TRACKING_CHANNEL_ID));
    }

    private void sendWorldRequest(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        attachRequestId(request);
        if (!isConnected()) {
            pendingSends.add(() -> sendWorldRequest(request));
            ensureConnected();
            return;
        }
        byte[] jsonBytes = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
        sendFrame(4, jsonBytes, numericChannel("world_management", WORLD_MANAGEMENT_CHANNEL_ID));
    }

    private void attachRequestId(Map<String, Object> request) {
        Object actionValue = request.get("action");
        String action = actionValue instanceof String value ? value : "";
        if (action.isBlank() || isReadOnlyWorldAction(action) || request.containsKey("requestId")) {
            return;
        }
        request.put("requestId", stableClientId + ":" + action + ":" + UUID.randomUUID());
    }

    private boolean isReadOnlyWorldAction(String action) {
        return switch (action) {
            case "snapshot", "auditSnapshot", "jobSnapshot", "operationStatus", "mapSnapshot", "whoWorld", "scanUnregisteredWorlds" -> true;
            default -> false;
        };
    }

    void sendResourceSave(ReSyncResourceType type, Object item) {
        if (item == null) {
            return;
        }
        if (!isConnected()) {
            System.err.println("[ReSyncFlow] WebSocket not connected - queueing " + type.displayName() + " save");
            pendingSends.add(() -> sendResourceSave(type, item));
            ensureConnected();
            return;
        }
        String json = type.serialize(item);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        if (type == ReSyncResourceType.FLOW) {
            System.out.println("[ReSyncFlow] Sending flow save: " + jsonBytes.length + " bytes");
        }
        byte[] requestIdBytes = mutationRequestId(type.displayName(), type.extractId(item)).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + jsonBytes.length);
        buffer.put(type.saveByte());
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    void sendResourceDelete(ReSyncResourceType type, String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendResourceDelete(type, id));
            ensureConnected();
            return;
        }
        byte[] requestIdBytes = mutationRequestId(type.displayName() + "Delete", id).getBytes(StandardCharsets.UTF_8);
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + idBytes.length);
        buffer.put(type.deleteByte());
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    public void sendFlowSave(FlowGraph graph) { sendResourceSave(ReSyncResourceType.FLOW, graph); }
    public void sendGuiSave(GuiDefinition gui) { sendResourceSave(ReSyncResourceType.GUI, gui); }
    public void sendScoreboardSave(ScoreboardDefinition scoreboard) { sendResourceSave(ReSyncResourceType.SCOREBOARD, scoreboard); }
    public void sendTabSave(TabDefinition tab) { sendResourceSave(ReSyncResourceType.TAB, tab); }
    public void sendCustomContentSave(CustomContentDefinition content) { sendResourceSave(ReSyncResourceType.CUSTOM_CONTENT, content); }
    public void sendProjectMetadataSave(ReSyncProjectMetadata metadata) { sendResourceSave(ReSyncResourceType.PROJECT_METADATA, metadata); }
    public void sendFlowDelete(String flowId) { sendResourceDelete(ReSyncResourceType.FLOW, flowId); }
    public void sendGuiDelete(String guiId) { sendResourceDelete(ReSyncResourceType.GUI, guiId); }
    public void sendScoreboardDelete(String scoreboardId) { sendResourceDelete(ReSyncResourceType.SCOREBOARD, scoreboardId); }
    public void sendTabDelete(String tabId) { sendResourceDelete(ReSyncResourceType.TAB, tabId); }

    public void sendWorldGenSave(WorldGenProject project) {
        if (project == null) {
            return;
        }
        sendWorldGenMutationJson((byte) 0x30, "worldGenProjectSave", project.getId(), WorldGenSerializer.serializeProject(project));
    }

    public void requestWorldGenProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        sendWorldGenJson((byte) 0x32, projectId);
    }

    public void sendWorldGenProjectDelete(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        sendWorldGenMutationJson((byte) 0x33, "worldGenProjectDelete", projectId, projectId);
    }

    public void requestWorldGenProjectList() {
        sendWorldGenJson((byte) 0x34, "{}");
    }

    public void sendWorldGenPreviewApply(String projectId, WorldGenProject draftProject, String previewId, String environment, long seed, String playerUuid) {
        if ((projectId == null || projectId.isBlank()) && draftProject == null) {
            return;
        }
        if (previewId == null || previewId.isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        if (projectId != null && !projectId.isBlank()) {
            payload.put("projectId", projectId);
        }
        if (draftProject != null) {
            payload.put("draftProject", draftProject);
        }
        payload.put("previewId", previewId);
        payload.put("environment", environment != null && !environment.isBlank() ? environment : "NORMAL");
        payload.put("seed", seed);
        payload.put("playerUuid", playerUuid);
        sendWorldGenMutationJson((byte) 0x31, "worldGenPreviewApply", previewId, gson.toJson(payload));
    }

    public void sendWorldGenPreviewStop(String previewId) {
        if (previewId == null || previewId.isBlank()) {
            return;
        }
        sendWorldGenMutationJson((byte) 0x22, "worldGenPreviewStop", previewId, gson.toJson(Map.of("previewId", previewId)));
    }

    public void requestWorldGenRegistry() {
        sendWorldGenJson((byte) 0x24, gson.toJson(Map.of("pluginChecksums", Map.of())));
    }

    private void sendWorldGenJson(byte packetId, String json) {
        if (!isConnected()) {
            pendingSends.add(() -> sendWorldGenJson(packetId, json));
            ensureConnected();
            return;
        }
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put(packetId);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("worldgen", WORLDGEN_CHANNEL_ID));
    }

    private void sendWorldGenMutationJson(byte packetId, String action, String target, String json) {
        if (!isConnected()) {
            pendingSends.add(() -> sendWorldGenMutationJson(packetId, action, target, json));
            ensureConnected();
            return;
        }
        byte[] requestIdBytes = mutationRequestId(action, target).getBytes(StandardCharsets.UTF_8);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + jsonBytes.length);
        buffer.put(packetId);
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("worldgen", WORLDGEN_CHANNEL_ID));
    }

    public void sendTriggerUpdate(List<TriggerBinding> bindings) {
        if (!isConnected()) {
            System.err.println("[ReSyncFlow] WebSocket not connected - queueing trigger update");
            pendingSends.add(() -> sendTriggerUpdate(bindings));
            ensureConnected();
            return;
        }

        String json = gson.toJson(bindings != null ? bindings : List.of());
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] requestIdBytes = mutationRequestId("triggerUpdate", serverId).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + jsonBytes.length);
        buffer.put((byte) 0x06);
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private String mutationRequestId(String action, String target) {
        return stableClientId + ":" + action + ":" + (target != null ? target : "") + ":" + UUID.randomUUID();
    }

    private void startHeartbeat() {
        stopHeartbeat();
        if (shutdownRequested) {
            return;
        }
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (shutdownRequested) {
                return;
            }
            if (!isConnected()) {
                return;
            }
            sendHeartbeat();
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void sendHeartbeat() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(System.currentTimeMillis());
        sendFrame(5, buffer.array(), CONTROL_CHANNEL_ID);
    }

    private String normalizeWsUrl(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) {
            return raw;
        }
        if (raw.startsWith("ws://") || raw.startsWith("wss://")) {
            return raw;
        }
        if (raw.startsWith("http://")) {
            return "ws://" + raw.substring("http://".length());
        }
        if (raw.startsWith("https://")) {
            return "wss://" + raw.substring("https://".length());
        }
        return "ws://" + raw;
    }

    private void scheduleReconnect() {
        if (shutdownRequested) {
            return;
        }
        if (reconnectTask != null && !reconnectTask.isDone()) {
            return;
        }
        reconnectTask = heartbeatScheduler.schedule(this::ensureConnected, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void handleError(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int errorCode = buffer.getInt();
        int messageLen = buffer.getInt();
        byte[] messageBytes = new byte[messageLen];
        buffer.get(messageBytes);
        String errorText = new String(messageBytes);

        System.err.println("[ReSyncFlow] Error " + errorCode + ": " + errorText);
    }

    public void shutdown() {
        System.out.println("[ReSyncFlow] Shutting down WebSocket connection");
        shutdownRequested = true;
        stopHeartbeat();
        if (frameTransport != null) {
            frameTransport.close();
        }
        WebSocketClient client = wsClient.getAndSet(null);
        if (client != null) {
            client.close();
        }
        authenticated.set(false);
        connecting.set(false);
        placeholderPreviewCallbacks.clear();
        cancelNodeRegistryTimeout();
        cancelConnectTimeout();
        nodeRegistryScheduler.shutdownNow();
        heartbeatScheduler.shutdownNow();
    }

    public boolean isConnectedState() {
        return isConnected();
    }

    public boolean matchesDirectProfile(String wsUrl, String apiKey) {
        return Objects.equals(normalizeWsUrl(directWsUrl), normalizeWsUrl(wsUrl)) && Objects.equals(directApiKey, apiKey);
    }

    private boolean isConnected() {
        if (frameTransport != null) {
            return frameTransport.isOpen() && authenticated.get();
        }
        WebSocketClient client = wsClient.get();
        return client != null && client.isOpen() && authenticated.get();
    }

    private void ensureConnected() {
        if (frameTransport != null) {
            connect();
            return;
        }
        WebSocketClient client = wsClient.get();
        if (client != null && client.isOpen() && !authenticated.get()) {
            client.close();
            wsClient.compareAndSet(client, null);
        }
        connect();
    }

    private void flushPendingSends() {
        Runnable pending;
        while ((pending = pendingSends.poll()) != null) {
            pending.run();
        }
    }

    private static class PlayerTrackingRequest {
        private String action;
        private String playerId;
    }

    private static class OptionCatalogPayload {
        private String sourceId;
        private String revision;
        private List<String> values;
        private List<OptionCatalogItem> items;
    }
}
