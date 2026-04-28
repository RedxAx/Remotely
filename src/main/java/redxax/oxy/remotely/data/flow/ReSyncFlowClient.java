package redxax.oxy.remotely.data.flow;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import redxax.oxy.remotely.RemotelyClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowDataTypeAdapter;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.cache.NodeRegistryCache;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.NodeRegistryRequest;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import redxax.oxy.remotely.flow.ui.ScoreboardDesignerScreen;
import redxax.oxy.remotely.flow.ui.TabDesignerScreen;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingUpdate;
import redxax.oxy.remotely.data.flow.world.WorldChannelMessage;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.util.Notification;
import restudio.rebase.restudio.api.ReStudioApiClient;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class ReSyncFlowClient {
    public interface ErrorListener {
        void onError(String nodeId, String message);
    }

    private final String serverId;
    private final ReStudioApiClient apiClient;
    private final String directWsUrl;
    private final String directApiKey;
    private final RemotelyClient client;
    private final AtomicReference<WebSocketClient> wsClient = new AtomicReference<>();
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private String apiKey;
    private final Inflater inflater = new Inflater();
    private static final int PROTOCOL_VERSION = 2;
    private static final String CLIENT_VERSION = "2.0.0";
    private static final short FLOW_CHANNEL_ID = 1001;
    private static final short PLAYER_TRACKING_CHANNEL_ID = 1002;
    private static final short WORLD_MANAGEMENT_CHANNEL_ID = 1003;
    private static final short CONTROL_CHANNEL_ID = 0;
    private int sequenceCounter = 0;
    private ErrorListener errorListener;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(FlowDataType.class, new FlowDataTypeAdapter())
            .registerTypeAdapter(NodeDefinition.NodeCategory.class, new com.google.gson.TypeAdapter<NodeDefinition.NodeCategory>() {
                @Override
                public void write(com.google.gson.stream.JsonWriter out, NodeDefinition.NodeCategory value) throws IOException {
                    out.value(value != null ? value.getId() : null);
                }

                @Override
                public NodeDefinition.NodeCategory read(com.google.gson.stream.JsonReader in) throws IOException {
                    String id = in.nextString();
                    return NodeDefinition.NodeCategory.fromString(id);
                }
            })
            .create();
    private final Queue<Runnable> pendingSends = new ConcurrentLinkedQueue<>();
    private final Map<ReSyncResourceType, Set<String>> pendingOpenResources = new ConcurrentHashMap<>();
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

    public ReSyncFlowClient(String serverId, ReStudioApiClient apiClient, RemotelyClient client) {
        this(serverId, apiClient, null, null, client);
    }

    public ReSyncFlowClient(String serverId, ReStudioApiClient apiClient, String directWsUrl, String directApiKey, RemotelyClient client) {
        this.serverId = serverId;
        this.apiClient = apiClient;
        this.directWsUrl = directWsUrl;
        this.directApiKey = directApiKey;
        this.client = client;
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            pendingOpenResources.put(type, ConcurrentHashMap.newKeySet());
        }
        loadCachedRegistry();
    }

    public void setErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }

    public CompletableFuture<Void> connect() {
        if (isConnected() || connecting.get()) {
            return CompletableFuture.completedFuture(null);
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
            initWebSocketConnection(directWsUrl);
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
                            System.out.println("[ReSyncFlow] Connecting to: ws://" + serverUrl);
                            System.out.println("[ReSyncFlow] Using API key: " + apiKey.substring(0, 8) + "...");
                            initWebSocketConnection("ws://" + serverUrl);
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
            e.printStackTrace();
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
                    handleBinaryMessage(bytes.array());
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
                    ex.printStackTrace();
                    connecting.set(false);
                    cancelConnectTimeout();
                    scheduleReconnect();
                }
            };
            wsClient.set(client);
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
            connecting.set(false);
            cancelConnectTimeout();
        }
    }

    private void sendHandshake() {
        String clientId = java.util.UUID.randomUUID().toString();
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

        sendSubscribe("flow", null);
        sendSubscribe("player_tracking", null);
        sendSubscribe("world_management", null);
    }

    private void sendSubscribe(String channelId, String data) {
        byte[] channelBytes = channelId.getBytes(StandardCharsets.UTF_8);
        byte[] dataBytes = data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
        ByteBuffer buffer = ByteBuffer.allocate(4 + channelBytes.length + 4 + dataBytes.length);
        buffer.putInt(channelBytes.length);
        buffer.put(channelBytes);
        buffer.putInt(dataBytes.length);
        if (dataBytes.length > 0) {
            buffer.put(dataBytes);
        }
        sendFrame(2, buffer.array(), CONTROL_CHANNEL_ID);
    }

    private void sendFrame(int messageType, byte[] payload, short channel) {
        WebSocketClient client = wsClient.get();
        if (client != null && client.isOpen()) {
            ByteBuffer frame = ByteBuffer.allocate(12 + payload.length);

            byte flags = 0;
            frame.put(flags);
            frame.put((byte) messageType);
            frame.putShort(channel);
            frame.putInt(sequenceCounter++);
            frame.putInt(payload.length);
            frame.put(payload);

            client.send(frame.array());
        }
    }

    private void handleBinaryMessage(byte[] data) {
        if (data.length < 12) return;

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte flags = buffer.get();
        boolean compressed = (flags & 0x80) != 0;
        boolean batch = (flags & 0x40) != 0;
        byte messageType = buffer.get();
        short channel = buffer.getShort();
        int sequence = buffer.getInt();
        int payloadLength = buffer.getInt();

        if (data.length < 12 + payloadLength) return;

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        System.out.println("[ReSyncFlow] Received message: Type=" + messageType + ", Channel=" + channel + ", Sequence=" + sequence + ", Compressed=" + compressed + ", PayloadSize=" + payloadLength);

        try {
            switch (messageType) {
                case 1:
                    System.out.println("[ReSyncFlow] Processing handshake response");
                    handleHandshakeResponse(payload);
                    break;
                case 4:
                    System.out.println("[ReSyncFlow] Processing data message on channel " + channel);
                    handleDataMessage(channel, payload, compressed);
                    break;
                case 5:
                    break;
                case 7:
                    System.err.println("[ReSyncFlow] Processing error message");
                    handleError(payload);
                    break;
            }
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleHandshakeResponse(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        byte success = buffer.get();
        if (success != 1) {
            System.out.println("[ReSyncFlow] Handshake failed - server rejected connection");
            return;
        }

        int messageLen = buffer.getInt();
        buffer.position(buffer.position() + messageLen);

        int protocolVersion = buffer.getInt();
        System.out.println("[ReSyncFlow] Server protocol version: " + protocolVersion);

        int serverVersionLen = buffer.getInt();
        buffer.position(buffer.position() + serverVersionLen);

        int worldCount = buffer.getInt();
        System.out.println("[ReSyncFlow] Available worlds: " + worldCount);

        authenticated.set(true);
        connecting.set(false);
        cancelConnectTimeout();
        System.out.println("[ReSyncFlow] Handshake complete, client authenticated");
        startHeartbeat();
        requestNodeRegistry();
        flushPendingSends();
    }

    private void scheduleConnectTimeout() {
        cancelConnectTimeout();
        connectTimeoutTask = heartbeatScheduler.schedule(() -> {
            if (authenticated.get() || shutdownRequested) {
                return;
            }
            connecting.set(false);
            WebSocketClient client = wsClient.get();
            if (client != null && !client.isOpen()) {
                client.close();
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

    private void handleDataMessage(short channel, byte[] payload, boolean compressed) {
        byte[] data = payload;
        if (compressed) {
            try {
                inflater.reset();
                inflater.setInput(data);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] tempBuffer = new byte[8192];
                while (!inflater.finished()) {
                    int count = inflater.inflate(tempBuffer);
                    baos.write(tempBuffer, 0, count);
                }
                data = baos.toByteArray();
            } catch (DataFormatException e) {
                System.err.println("[ReSyncFlow] Decompression error: " + e.getMessage());
                return;
            }
        }

        if (channel == PLAYER_TRACKING_CHANNEL_ID) {
            handlePlayerTrackingMessage(data);
            return;
        }

        if (channel == WORLD_MANAGEMENT_CHANNEL_ID) {
            handleWorldManagementMessage(data);
            return;
        }

        if (channel != FLOW_CHANNEL_ID) {
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        if (!buffer.hasRemaining()) return;
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
            case 0x28:
                handlePlaceholderPreview(buffer);
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
            if (client != null && client.getFlowManager() != null) {
                client.getFlowManager().applyWorldManagementMessage(serverId, message);
            }
        } catch (Exception e) {
            System.err.println("[ReSyncFlow] Failed to parse world management update: " + e.getMessage());
        }
    }

    private void handleResourceData(ReSyncResourceType type, ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        if (type == ReSyncResourceType.FLOW) {
            System.out.println("[ReSyncFlow] Received flow data: " + json.substring(0, Math.min(100, json.length())) + (json.length() > 100 ? "..." : ""));
        }
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
                            Screen current = ScreenManager.getInstance().getCurrentScreen();
                            if (current instanceof FlowEditorScreen screen
                                && serverId.equals(screen.getServerId())
                                && itemId.equals(screen.getFlowId())) {
                                screen.applyGraph(graph);
                                return;
                            }
                            client.getHost().setScreen(new FlowEditorScreen(graph, serverId, current));
                        } else if (type == ReSyncResourceType.GUI) {
                            client.getHost().setScreen(new GuiDesignerScreen((GuiDefinition) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.SCOREBOARD) {
                            client.getHost().setScreen(new ScoreboardDesignerScreen((ScoreboardDefinition) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.TAB) {
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
    }

    private void applyServerResourceList(FlowManager fm, ReSyncResourceType type, java.util.List<String> ids) {
        if (type == ReSyncResourceType.FLOW) fm.applyServerFlowList(serverId, ids);
        else if (type == ReSyncResourceType.GUI) fm.applyServerGuiList(serverId, ids);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.applyServerScoreboardList(serverId, ids);
        else if (type == ReSyncResourceType.TAB) fm.applyServerTabList(serverId, ids);
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
            redxax.oxy.remotely.flow.ui.GuiEditOverlayState.update(serverId, guiId, flowId, true);
        } else {
            redxax.oxy.remotely.flow.ui.GuiEditOverlayState.clear();
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

        ScreenManager.getInstance().execute(() ->
            new Notification(type.displayName() + " Saved", "ID: " + id, Notification.Type.SUCCESS)
        );

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

    private void handleFlowSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.FLOW, buffer); }
    private void handleGuiSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.GUI, buffer); }
    private void handleScoreboardSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.SCOREBOARD, buffer); }
    private void handleTabSaveAck(ByteBuffer buffer) { handleResourceSaveAck(ReSyncResourceType.TAB, buffer); }

    private void handleResourceList(ReSyncResourceType type, ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int count = buffer.getInt();
        java.util.List<String> ids = new java.util.ArrayList<>();
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
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
        scheduleNodeRegistryTimeout();
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
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
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
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void requestFlow(String flowId) {
        requestFlow(flowId, true);
    }

    public void requestFlow(String flowId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.FLOW, flowId, openWhenReceived);
    }

    public void requestFlowList() {
        requestResourceList(ReSyncResourceType.FLOW);
    }

    public void requestGui(String guiId) {
        requestGui(guiId, true);
    }

    public void requestGui(String guiId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.GUI, guiId, openWhenReceived);
    }

    public void requestGuiList() {
        requestResourceList(ReSyncResourceType.GUI);
    }

    public void requestScoreboard(String scoreboardId) {
        requestScoreboard(scoreboardId, true);
    }

    public void requestScoreboard(String scoreboardId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.SCOREBOARD, scoreboardId, openWhenReceived);
    }

    public void requestScoreboardList() {
        requestResourceList(ReSyncResourceType.SCOREBOARD);
    }

    public void requestTab(String tabId) {
        requestTab(tabId, true);
    }

    public void requestTab(String tabId, boolean openWhenReceived) {
        requestResource(ReSyncResourceType.TAB, tabId, openWhenReceived);
    }

    public void requestTabList() {
        requestResourceList(ReSyncResourceType.TAB);
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
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void requestPlayerTrackingSnapshot() {
        sendPlayerTrackingAction("snapshot", null);
    }

    public void requestPlayerDossier(UUID playerId) {
        sendPlayerTrackingAction("dossier", playerId);
    }

    public void requestWorldSnapshot() {
        java.util.LinkedHashMap<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("action", "snapshot");
        sendWorldRequest(request);
    }

    public void requestWorldMapSnapshot(String worldName, double centerX, double centerZ, int zoom) {
        java.util.LinkedHashMap<String, Object> request = new java.util.LinkedHashMap<>();
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
        sendFrame(4, jsonBytes, PLAYER_TRACKING_CHANNEL_ID);
    }

    private void sendWorldRequest(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendWorldRequest(request));
            ensureConnected();
            return;
        }
        byte[] jsonBytes = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
        sendFrame(4, jsonBytes, WORLD_MANAGEMENT_CHANNEL_ID);
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
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put(type.saveByte());
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
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
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put(type.deleteByte());
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void sendFlowSave(FlowGraph graph) { sendResourceSave(ReSyncResourceType.FLOW, graph); }
    public void sendGuiSave(GuiDefinition gui) { sendResourceSave(ReSyncResourceType.GUI, gui); }
    public void sendScoreboardSave(ScoreboardDefinition scoreboard) { sendResourceSave(ReSyncResourceType.SCOREBOARD, scoreboard); }
    public void sendTabSave(TabDefinition tab) { sendResourceSave(ReSyncResourceType.TAB, tab); }
    public void sendFlowDelete(String flowId) { sendResourceDelete(ReSyncResourceType.FLOW, flowId); }
    public void sendGuiDelete(String guiId) { sendResourceDelete(ReSyncResourceType.GUI, guiId); }
    public void sendScoreboardDelete(String scoreboardId) { sendResourceDelete(ReSyncResourceType.SCOREBOARD, scoreboardId); }
    public void sendTabDelete(String tabId) { sendResourceDelete(ReSyncResourceType.TAB, tabId); }

    public void sendTriggerUpdate(java.util.List<TriggerBinding> bindings) {
        if (!isConnected()) {
            System.err.println("[ReSyncFlow] WebSocket not connected - queueing trigger update");
            pendingSends.add(() -> sendTriggerUpdate(bindings));
            ensureConnected();
            return;
        }

        String json = gson.toJson(bindings != null ? bindings : java.util.List.of());
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put((byte) 0x06);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    private void sendAck(int sequence) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(sequence);
        sendFrame(6, buffer.array(), FLOW_CHANNEL_ID);
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

    private boolean isConnected() {
        WebSocketClient client = wsClient.get();
        return client != null && client.isOpen() && authenticated.get();
    }

    private void ensureConnected() {
        WebSocketClient client = wsClient.get();
        if (client != null && client.isOpen() && !authenticated.get()) {
            return;
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
}
