package redxax.oxy.remotely.data.flow;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import redxax.oxy.remotely.RemotelyClient;
import com.google.gson.Gson;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.cache.NodeRegistryCache;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.NodeRegistryRequest;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import redxax.oxy.remotely.flow.ui.ScoreboardDesignerScreen;
import redxax.oxy.remotely.flow.ui.TabDesignerScreen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.util.Notification;
import restudio.rebase.restudio.api.ReStudioApiClient;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
    private final AtomicReference<WebSocketClient> wsClient = new AtomicReference<>();
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private String apiKey;
    private final Inflater inflater = new Inflater();
    private static final int PROTOCOL_VERSION = 2;
    private static final String CLIENT_VERSION = "2.0.0";
    private static final short FLOW_CHANNEL_ID = 1001;
    private static final short CONTROL_CHANNEL_ID = 0;
    private int sequenceCounter = 0;
    private ErrorListener errorListener;
    private final Gson gson = new Gson();
    private final Queue<Runnable> pendingSends = new ConcurrentLinkedQueue<>();
    private final Set<String> pendingOpenFlows = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingOpenGuis = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingOpenScoreboards = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingOpenTabs = ConcurrentHashMap.newKeySet();
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
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;
    private static final int RECONNECT_DELAY_SECONDS = 3;
    private volatile boolean shutdownRequested = false;
    private final AtomicInteger placeholderRequestCounter = new AtomicInteger(1);
    private final Map<Integer, Consumer<String>> placeholderPreviewCallbacks = new ConcurrentHashMap<>();

    public ReSyncFlowClient(String serverId, ReStudioApiClient apiClient) {
        this.serverId = serverId;
        this.apiClient = apiClient;
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
        System.out.println("[ReSyncFlow] Attempting to connect to serverId=" + serverId);

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
                            if (errorListener != null) {
                                errorListener.onError(null, "ReSync API key not available");
                            }
                        }
                    });
                } else {
                    System.err.println("[ReSyncFlow] Server not found in server list");
                    connecting.set(false);
                    if (errorListener != null) {
                        errorListener.onError(null, "Server not found");
                    }
                    return CompletableFuture.completedFuture(null);
                }
            } else {
                System.err.println("[ReSyncFlow] No ReSync config found - ReSync is not enabled on this server");
                connecting.set(false);
                if (errorListener != null) {
                    errorListener.onError(null, "ReSync is not enabled on this server");
                }
                return CompletableFuture.completedFuture(null);
            }
        }).exceptionally(e -> {
            System.err.println("[ReSyncFlow] Error connecting to ReSync: " + e.getMessage());
            e.printStackTrace();
            connecting.set(false);
            if (errorListener != null) {
                errorListener.onError(null, "Failed to connect to ReSync: " + e.getMessage());
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
                    scheduleReconnect();
                }
            };
            wsClient.set(client);
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
            connecting.set(false);
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

        sendSubscribe();
    }

    private void sendSubscribe() {
        byte[] subscribeData = ByteBuffer.allocate(4 + "flow".getBytes().length)
                .putInt("flow".getBytes().length)
                .put("flow".getBytes())
                .array();

        sendFrame(2, subscribeData, CONTROL_CHANNEL_ID);
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
        System.out.println("[ReSyncFlow] Handshake complete, client authenticated");
        startHeartbeat();
        requestNodeRegistry();
        flushPendingSends();
    }

    private void handleDataMessage(short channel, byte[] payload, boolean compressed) {
        if (channel != FLOW_CHANNEL_ID) return;

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

    private void handleFlowData(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        System.out.println("[ReSyncFlow] Received flow data: " + json.substring(0, Math.min(100, json.length())) + (json.length() > 100 ? "..." : ""));

        FlowGraph graph = FlowSerializer.deserialize(json);

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().cacheFlow(serverId, graph);
        }

        if (graph != null) {
            String flowId = graph.getId() != null ? graph.getId().toString() : null;
            if (flowId != null && pendingOpenFlows.remove(flowId)) {
                ScreenManager.getInstance().execute(() -> {
                    if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
                        Screen current = ScreenManager.getInstance().getCurrentScreen();
                        if (current instanceof FlowEditorScreen screen
                            && serverId.equals(screen.getServerId())
                            && flowId.equals(screen.getFlowId())) {
                            screen.applyGraph(graph);
                            return;
                        }
                        RemotelyClient.INSTANCE.getHost().setScreen(new FlowEditorScreen(graph, serverId, current));
                    }
                });
            }
        }
    }

    private void handleGuiData(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        GuiDefinition gui = FlowSerializer.deserializeGui(json);

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().cacheGui(serverId, gui);
        }

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().handleGuiDataReceived(serverId, gui);
        }

        if (gui != null && gui.getId() != null && pendingOpenGuis.remove(gui.getId())) {
            ScreenManager.getInstance().execute(() -> {
                if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
                    RemotelyClient.INSTANCE.getHost().setScreen(new GuiDesignerScreen(gui, serverId, ScreenManager.getInstance().getCurrentScreen()));
                }
            });
        }
    }

    private void handleScoreboardData(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        ScoreboardDefinition scoreboard = FlowSerializer.deserializeScoreboard(json);

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().cacheScoreboard(serverId, scoreboard);
            RemotelyClient.INSTANCE.getFlowManager().handleScoreboardDataReceived(serverId, scoreboard);
        }

        if (scoreboard != null && scoreboard.getId() != null && pendingOpenScoreboards.remove(scoreboard.getId())) {
            ScreenManager.getInstance().execute(() -> {
                if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
                    RemotelyClient.INSTANCE.getHost().setScreen(new ScoreboardDesignerScreen(scoreboard, serverId, ScreenManager.getInstance().getCurrentScreen()));
                }
            });
        }
    }

    private void handleTabData(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        TabDefinition tab = FlowSerializer.deserializeTab(json);

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            try {
                RemotelyClient.INSTANCE.getFlowManager().cacheTab(serverId, tab);
                RemotelyClient.INSTANCE.getFlowManager().handleTabDataReceived(serverId, tab);
            } catch (NoSuchMethodError ignored) {
            }
        }

        if (tab != null && tab.getId() != null && pendingOpenTabs.remove(tab.getId())) {
            ScreenManager.getInstance().execute(() -> {
                if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
                    RemotelyClient.INSTANCE.getHost().setScreen(new TabDesignerScreen(tab, serverId, ScreenManager.getInstance().getCurrentScreen()));
                }
            });
        }
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
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().handleGuiStatePacket(serverId, editable, guiId, flowId);
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

    private void handleFlowSaveAck(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int idLen = buffer.getInt();
        if (idLen < 0 || idLen > buffer.remaining()) {
            return;
        }
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        String flowId = new String(idBytes, StandardCharsets.UTF_8);

        ScreenManager.getInstance().execute(() ->
            new Notification("Flow Saved", "ID: " + flowId, Notification.Type.SUCCESS)
        );

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().markFlowSaved(serverId, flowId);
        }
    }

    private void handleGuiSaveAck(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int idLen = buffer.getInt();
        if (idLen < 0 || idLen > buffer.remaining()) {
            return;
        }
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        String guiId = new String(idBytes, StandardCharsets.UTF_8);

        ScreenManager.getInstance().execute(() ->
            new Notification("GUI Saved", "ID: " + guiId, Notification.Type.SUCCESS)
        );

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().markGuiSaved(serverId, guiId);
        }
    }

    private void handleScoreboardSaveAck(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int idLen = buffer.getInt();
        if (idLen < 0 || idLen > buffer.remaining()) {
            return;
        }
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        String scoreboardId = new String(idBytes, StandardCharsets.UTF_8);

        ScreenManager.getInstance().execute(() ->
            new Notification("Scoreboard Saved", "ID: " + scoreboardId, Notification.Type.SUCCESS)
        );

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().markScoreboardSaved(serverId, scoreboardId);
        }
    }

    private void handleTabSaveAck(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int idLen = buffer.getInt();
        if (idLen < 0 || idLen > buffer.remaining()) {
            return;
        }
        byte[] idBytes = new byte[idLen];
        buffer.get(idBytes);
        String tabId = new String(idBytes, StandardCharsets.UTF_8);

        ScreenManager.getInstance().execute(() ->
            new Notification("Tab Saved", "ID: " + tabId, Notification.Type.SUCCESS)
        );

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            try {
                RemotelyClient.INSTANCE.getFlowManager().markTabSaved(serverId, tabId);
            } catch (NoSuchMethodError ignored) {
            }
        }
    }

    private void handleFlowList(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int count = buffer.getInt();
        java.util.List<String> flowIds = new java.util.ArrayList<>();
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
            flowIds.add(new String(idBytes, StandardCharsets.UTF_8));
        }

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().applyServerFlowList(serverId, flowIds);
        }
    }

    private void handleGuiList(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int count = buffer.getInt();
        java.util.List<String> guiIds = new java.util.ArrayList<>();
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
            guiIds.add(new String(idBytes, StandardCharsets.UTF_8));
        }

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().applyServerGuiList(serverId, guiIds);
        }
    }

    private void handleScoreboardList(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int count = buffer.getInt();
        java.util.List<String> scoreboardIds = new java.util.ArrayList<>();
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
            scoreboardIds.add(new String(idBytes, StandardCharsets.UTF_8));
        }

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            RemotelyClient.INSTANCE.getFlowManager().applyServerScoreboardList(serverId, scoreboardIds);
        }
    }

    private void handleTabList(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int count = buffer.getInt();
        java.util.List<String> tabIds = new java.util.ArrayList<>();
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
            tabIds.add(new String(idBytes, StandardCharsets.UTF_8));
        }

        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getFlowManager() != null) {
            try {
                RemotelyClient.INSTANCE.getFlowManager().applyServerTabList(serverId, tabIds);
            } catch (NoSuchMethodError ignored) {
            }
        }
    }

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

    private void sendFlowRequest(String flowId) {
        byte[] idBytes = flowId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put((byte) 0x01);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    private void sendGuiRequest(String guiId) {
        byte[] idBytes = guiId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put((byte) 0x11);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    private void sendScoreboardRequest(String scoreboardId) {
        byte[] idBytes = scoreboardId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put((byte) 0x18);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    private void sendTabRequest(String tabId) {
        byte[] idBytes = tabId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put((byte) 0x20);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void requestFlow(String flowId) {
        requestFlow(flowId, true);
    }

    public void requestFlow(String flowId, boolean openWhenReceived) {
        if (flowId == null || flowId.isEmpty()) {
            return;
        }
        if (openWhenReceived) {
            pendingOpenFlows.add(flowId);
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendFlowRequest(flowId));
            ensureConnected();
            return;
        }
        sendFlowRequest(flowId);
    }

    public void requestFlowList() {
        if (!isConnected()) {
            pendingSends.add(this::requestFlowList);
            ensureConnected();
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0x09);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void requestGui(String guiId) {
        requestGui(guiId, true);
    }

    public void requestGui(String guiId, boolean openWhenReceived) {
        if (guiId == null || guiId.isEmpty()) {
            return;
        }
        if (openWhenReceived) {
            pendingOpenGuis.add(guiId);
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendGuiRequest(guiId));
            ensureConnected();
            return;
        }
        sendGuiRequest(guiId);
    }

    public void requestGuiList() {
        if (!isConnected()) {
            pendingSends.add(this::requestGuiList);
            ensureConnected();
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0x14);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void requestScoreboard(String scoreboardId) {
        requestScoreboard(scoreboardId, true);
    }

    public void requestScoreboard(String scoreboardId, boolean openWhenReceived) {
        if (scoreboardId == null || scoreboardId.isEmpty()) {
            return;
        }
        if (openWhenReceived) {
            pendingOpenScoreboards.add(scoreboardId);
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendScoreboardRequest(scoreboardId));
            ensureConnected();
            return;
        }
        sendScoreboardRequest(scoreboardId);
    }

    public void requestScoreboardList() {
        if (!isConnected()) {
            pendingSends.add(this::requestScoreboardList);
            ensureConnected();
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0x1A);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void requestTab(String tabId) {
        requestTab(tabId, true);
    }

    public void requestTab(String tabId, boolean openWhenReceived) {
        if (tabId == null || tabId.isEmpty()) {
            return;
        }
        if (openWhenReceived) {
            pendingOpenTabs.add(tabId);
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendTabRequest(tabId));
            ensureConnected();
            return;
        }
        sendTabRequest(tabId);
    }

    public void requestTabList() {
        if (!isConnected()) {
            pendingSends.add(this::requestTabList);
            ensureConnected();
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0x22);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
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

    public void sendFlowSave(FlowGraph graph) {
        if (graph == null) {
            return;
        }
        if (!isConnected()) {
            System.err.println("[ReSyncFlow] WebSocket not connected - queueing flow save");
            pendingSends.add(() -> sendFlowSave(graph));
            ensureConnected();
            return;
        }

        String json = FlowSerializer.serialize(graph);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        System.out.println("[ReSyncFlow] Sending flow save: " + jsonBytes.length + " bytes");

        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put((byte) 0x03);
        buffer.put(jsonBytes);

        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void sendGuiSave(GuiDefinition gui) {
        if (gui == null) {
            return;
        }
        if (!isConnected()) {
            System.err.println("[ReSyncFlow] WebSocket not connected - queueing GUI save");
            pendingSends.add(() -> sendGuiSave(gui));
            ensureConnected();
            return;
        }

        String json = FlowSerializer.serializeGui(gui);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put((byte) 0x13);
        buffer.put(jsonBytes);

        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void sendScoreboardSave(ScoreboardDefinition scoreboard) {
        if (scoreboard == null) {
            return;
        }
        if (!isConnected()) {
            System.err.println("[ReSyncFlow] WebSocket not connected - queueing scoreboard save");
            pendingSends.add(() -> sendScoreboardSave(scoreboard));
            ensureConnected();
            return;
        }

        String json = FlowSerializer.serializeScoreboard(scoreboard);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put((byte) 0x19);
        buffer.put(jsonBytes);

        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void sendTabSave(TabDefinition tab) {
        if (tab == null) {
            return;
        }
        if (!isConnected()) {
            System.err.println("[ReSyncFlow] WebSocket not connected - queueing tab save");
            pendingSends.add(() -> sendTabSave(tab));
            ensureConnected();
            return;
        }

        String json = FlowSerializer.serializeTab(tab);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put((byte) 0x21);
        buffer.put(jsonBytes);

        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void sendFlowDelete(String flowId) {
        if (flowId == null || flowId.isEmpty()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendFlowDelete(flowId));
            ensureConnected();
            return;
        }
        byte[] idBytes = flowId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put((byte) 0x08);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void sendGuiDelete(String guiId) {
        if (guiId == null || guiId.isEmpty()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendGuiDelete(guiId));
            ensureConnected();
            return;
        }
        byte[] idBytes = guiId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put((byte) 0x16);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void sendScoreboardDelete(String scoreboardId) {
        if (scoreboardId == null || scoreboardId.isEmpty()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendScoreboardDelete(scoreboardId));
            ensureConnected();
            return;
        }
        byte[] idBytes = scoreboardId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put((byte) 0x1B);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

    public void sendTabDelete(String tabId) {
        if (tabId == null || tabId.isEmpty()) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendTabDelete(tabId));
            ensureConnected();
            return;
        }
        byte[] idBytes = tabId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + idBytes.length);
        buffer.put((byte) 0x23);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), FLOW_CHANNEL_ID);
    }

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
        nodeRegistryScheduler.shutdownNow();
        heartbeatScheduler.shutdownNow();
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
}
