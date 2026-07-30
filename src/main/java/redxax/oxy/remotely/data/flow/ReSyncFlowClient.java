package redxax.oxy.remotely.data.flow;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.collaboration.CollaborationService;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowDataTypeAdapter;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.CustomContentGraphAdapter;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.cache.NodeRegistryCache;
import redxax.oxy.remotely.flow.cache.NodeRegistryTombstoneCache;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.NodePluginPayload;
import redxax.oxy.remotely.flow.sync.NodeRegistryRequest;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;
import redxax.oxy.remotely.flow.sync.OptionCatalogSnapshot;
import redxax.oxy.remotely.flow.ui.FlowEditorScreen;
import redxax.oxy.remotely.flow.ui.FlowGraphDesignerScreen;
import redxax.oxy.remotely.flow.ui.FocusedJsonResourceDesignerScreen;
import redxax.oxy.remotely.flow.ui.AdvancementDesignerScreen;
import redxax.oxy.remotely.flow.ui.ContentDesignerScreen;
import redxax.oxy.remotely.flow.ui.DialogDesignerScreen;
import redxax.oxy.remotely.flow.ui.GuiDesignerScreen;
import redxax.oxy.remotely.flow.ui.LootTableDesignerScreen;
import redxax.oxy.remotely.flow.ui.NpcDesignerScreen;
import redxax.oxy.remotely.flow.ui.ScoreboardDesignerScreen;
import redxax.oxy.remotely.flow.ui.TabDesignerScreen;
import redxax.oxy.remotely.flow.ui.TradeDesignerScreen;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingUpdate;
import redxax.oxy.remotely.data.flow.world.WorldChannelMessage;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsClient;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenSerializer;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import restudio.rescreen.logging.ReLogger;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.util.Notification;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.resync.flow.workspace.LiveDocumentChannel;
import restudio.resync.flow.workspace.WorkspacePatch;
import restudio.resync.flow.workspace.WorkspaceTarget;
import restudio.resync.resource.ReSyncResourceKey;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ReSyncFlowClient {
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public interface ErrorListener {
        void onError(String nodeId, String message);
    }

    public interface PluginChannelListener {
        void onData(String channelId, byte[] payload);

        default void onAvailable(String channelId) {
        }

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
    private final AtomicReference<String> notifiedConnectionError = new AtomicReference<>();
    private String apiKey;
    private final ReSyncFrameCodec frameCodec = new ReSyncFrameCodec();
    private static final int PROTOCOL_VERSION = ReSyncProtocolContract.PROTOCOL_VERSION;
    private static final String CLIENT_VERSION = "2.1.0";
    private static final List<String> FLOW_CONTRACT_CAPABILITIES = List.of("nodes", "types", "categories", "properties", "resources", "catalogs", "conversions", "extensions", "deltas", "diagnostics", "contextual_catalogs", "authorization", "destructive_safety", "function_tests", "jobs", "job_events", "resource_operation_diagnostics", "extension_validators", "resource_revisions", "asset_integrity", "transaction_recovery", "migration_fencing", "opaque_resources", "collaboration_presence", "collaboration_chat", "resource_events", "live_workspace");
    private static final List<String> REQUIRED_FLOW_CONTRACT_CAPABILITIES = List.of("nodes", "types", "categories", "properties", "resources", "catalogs", "conversions", "extensions", "deltas", "diagnostics");
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
    private final Set<String> availablePluginChannels = ConcurrentHashMap.newKeySet();
    private int sequenceCounter = 0;
    private ErrorListener errorListener;
    private volatile Runnable disconnectListener = () -> {};
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
    private final AtomicInteger playerControlSequence = new AtomicInteger();
    private final Map<String, CompletableFuture<JsonObject>> pendingPlayerControlRequests = new ConcurrentHashMap<>();
    private volatile JsonObject playerControlCapabilities;
    private final Set<UUID> watchedPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean playerTrackingSubscribed;
    private final Map<ReSyncResourceType, Set<String>> pendingOpenResources = new ConcurrentHashMap<>();
    private final Map<ReSyncResourceType, Integer> pendingResourceListRequests = new ConcurrentHashMap<>();
    private final AtomicInteger resourceListRequestSequence = new AtomicInteger();
    private final Object resourceListRequestLock = new Object();
    private final Set<String> pendingOptionCatalogRequests = ConcurrentHashMap.newKeySet();
    private final Map<String, JsonObject> jobs = new ConcurrentHashMap<>();
    private final Set<String> terminalJobNotifications = ConcurrentHashMap.newKeySet();
    private final Map<String, ReSyncResourceKey> pendingResourceDeletes = new ConcurrentHashMap<>();
    private volatile String durabilityHealthFingerprint = "";
    private final NodeRegistryCache nodeRegistryCache = NodeRegistryCache.getInstance();
    private final ScheduledExecutorService nodeRegistryScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> nodeRegistryTimeout;
    private volatile boolean nodeRegistrySynced = false;
    private volatile boolean usingCachedRegistry = false;
    private volatile long lastFullNodeRegistryRequestAt;
    private static final int NODE_REGISTRY_TIMEOUT_SECONDS = 5;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ReSyncFlow-Heartbeat");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> reconnectTask;
    private ScheduledFuture<?> connectTimeoutTask;
    private final AtomicInteger connectionGeneration = new AtomicInteger();
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;
    private static final int RECONNECT_DELAY_SECONDS = 3;
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private volatile boolean shutdownRequested = false;
    private final AtomicInteger placeholderRequestCounter = new AtomicInteger(1);
    private final Map<Integer, Consumer<String>> placeholderPreviewCallbacks = new ConcurrentHashMap<>();
    private final AtomicInteger functionTestRequestCounter = new AtomicInteger(1);
    private final Map<String, Consumer<JsonObject>> functionTestCallbacks = new ConcurrentHashMap<>();
    private final WorldGenProtocolHandler worldGenProtocolHandler;
    private final String stableClientId;
    private final ReSyncCollaborationClient collaboration;
    private final ReSyncWorkspaceClient workspaces;
    private volatile ReSyncLuckPermsClient luckPermsClient;

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
        this.stableClientId = stableClientId(serverId);
        this.collaboration = new ReSyncCollaborationClient(gson, stableClientId);
        bindCollaborationChannel();
        this.workspaces = new ReSyncWorkspaceClient(gson);
        bindWorkspaceChannel();
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
        this.stableClientId = stableClientId(serverId);
        this.collaboration = new ReSyncCollaborationClient(gson, stableClientId);
        bindCollaborationChannel();
        this.workspaces = new ReSyncWorkspaceClient(gson);
        bindWorkspaceChannel();
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            pendingOpenResources.put(type, ConcurrentHashMap.newKeySet());
        }
        loadCachedRegistry();
    }

    public void setErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }

    public void setDisconnectListener(Runnable listener) {
        disconnectListener = listener != null ? listener : () -> {};
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
        if (!isPluginChannelAvailable(channelId)) {
            return false;
        }
        Short channel = channelIds.get(channelId);
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

    public boolean isPluginChannelAvailable(String channelId) {
        return isConnectedState() && isPluginChannel(channelId) && channelIds.containsKey(channelId) && availablePluginChannels.contains(channelId);
    }

    public ReSyncLuckPermsClient luckPerms() {
        ReSyncLuckPermsClient current = luckPermsClient;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (luckPermsClient == null) {
                luckPermsClient = new ReSyncLuckPermsClient(this);
            }
            return luckPermsClient;
        }
    }

    public String getServerId() {
        return serverId;
    }

    public ReSyncCollaborationClient collaboration() {
        return collaboration;
    }

    private void bindCollaborationChannel() {
        collaboration.bind(new CollaborationService.Channel() {
            @Override
            public boolean available() {
                return isConnected();
            }

            @Override
            public void publishPresence(CollaborationService.PresenceUpdate update) {
                CollaborationService.Target target = update.target();
                sendCollaborationPresence(target.resourceType(), target.resourceId(), target.viewId(),
                    update.x(), update.y(), update.active(), update.typing());
            }

            @Override
            public void publishMessage(CollaborationService.MessageDraft message) {
                sendCollaborationMessage(message.message());
            }
        });
    }

    private void bindWorkspaceChannel() {
        workspaces.bind(new LiveDocumentChannel.Transport<>() {
            @Override
            public void join(WorkspaceTarget target) {
                sendWorkspaceTarget(ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_JOIN, target);
            }

            @Override
            public void leave(WorkspaceTarget target) {
                sendWorkspaceTarget(ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_LEAVE, target);
            }

            @Override
            public boolean publishOperation(WorkspaceTarget target, long baseSequence, String operationId,
                                            List<WorkspacePatch<JsonElement>> patches) {
                JsonObject request = workspaceTarget(target);
                request.addProperty("operationId", operationId);
                request.addProperty("baseSequence", baseSequence);
                request.add("patches", gson.toJsonTree(patches));
                return sendWorkspacePacket(ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_OPERATION, request);
            }

            @Override
            public boolean publishAwareness(WorkspaceTarget target, JsonObject state) {
                JsonObject request = workspaceTarget(target);
                request.add("state", state != null ? state : new JsonObject());
                return sendWorkspacePacket(ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_AWARENESS, request);
            }
        });
    }

    private void sendWorkspaceTarget(byte packetType, WorkspaceTarget target) {
        sendWorkspacePacket(packetType, workspaceTarget(target));
    }

    private JsonObject workspaceTarget(WorkspaceTarget target) {
        JsonObject request = new JsonObject();
        request.addProperty("type", target.resourceType());
        request.addProperty("resourceId", target.resourceId());
        return request;
    }

    public ReSyncWorkspaceClient workspaces() {
        return workspaces;
    }

    public CompletableFuture<Void> connect() {
        if (shutdownRequested) {
            return CompletableFuture.completedFuture(null);
        }
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
        lastFullNodeRegistryRequestAt = 0L;
        int generation = connectionGeneration.incrementAndGet();
        scheduleConnectTimeout(generation);
        logger().operation("Connect").info("Connecting to ReSync");

        if (directWsUrl != null && !directWsUrl.isBlank()) {
            this.apiKey = directApiKey;
            if (this.apiKey == null || this.apiKey.isBlank()) {
                logger().operation("Connect").warn("Direct ReSync credentials are unavailable");
                connecting.set(false);
                cancelConnectTimeout();
                if (errorListener != null) {
                    errorListener.onError(null, "ReSyncApiKeyMissing");
                }
                return CompletableFuture.completedFuture(null);
            }
            logger().operation("Connect").with("endpoint", directWsUrl).info("Using direct ReSync endpoint");
            initWebSocketConnection(normalizeWsUrl(directWsUrl), generation);
            return CompletableFuture.completedFuture(null);
        }

        return apiClient.getReSyncConfig(serverId).thenCompose(config -> {
            if (!isActiveGeneration(generation)) {
                return CompletableFuture.completedFuture(null);
            }
            if (config != null && config.port > 0) {
                logger().operation("Discover Endpoint").with("port", config.port).debug("ReSync configuration found");

                return apiClient.getServers().thenCompose(servers -> {
                    if (!isActiveGeneration(generation)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    String serverUrl = servers.stream()
                        .filter(s -> serverId.equals(s.identifier))
                        .findFirst()
                        .map(s -> {
                            String ip = (s.ipAlias != null && !s.ipAlias.isEmpty()) ? s.ipAlias : s.ip;
                            logger().operation("Discover Endpoint").with("name", s.name).with("ip", ip).with("ipAlias", s.ipAlias).debug("ReSync server found");
                            return ip + ":" + config.port;
                        })
                        .orElse(null);

                    if (serverUrl == null) {
                        logger().operation("Discover Endpoint").warn("ReSync server was not found");
                        connecting.set(false);
                        cancelConnectTimeout();
                        if (errorListener != null) {
                            errorListener.onError(null, "ReSyncServerNotFound");
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                    return apiClient.getReSyncApiKey(serverId).thenAccept(key -> {
                        if (!isActiveGeneration(generation)) {
                            return;
                        }
                        this.apiKey = key;
                        if (this.apiKey != null && !this.apiKey.isEmpty()) {
                            String wsUrl = normalizeWsUrl(serverUrl);
                            logger().operation("Connect").with("endpoint", wsUrl).info("Connecting to ReSync WebSocket");
                            initWebSocketConnection(wsUrl, generation);
                        } else {
                            logger().operation("Connect").warn("ReSync credentials are unavailable");
                            connecting.set(false);
                            cancelConnectTimeout();
                            if (errorListener != null) {
                                errorListener.onError(null, "ReSyncApiKeyMissing");
                            }
                        }
                    });
                });
            } else {
                logger().operation("Discover Endpoint").warn("ReSync is not enabled on this server");
                connecting.set(false);
                cancelConnectTimeout();
                if (errorListener != null) {
                    errorListener.onError(null, "ReSyncNotEnabled");
                }
                return CompletableFuture.completedFuture(null);
            }
        }).exceptionally(e -> {
            connecting.set(false);
            cancelConnectTimeout();
            if (notifyConnectionError("ReSync Connection Failed. Check That The Server Is Online And ReSync Is Enabled")) {
                logger().operation("Connect").error("Could not connect to ReSync", e);
            }
            scheduleReconnect();
            return null;
        });
    }

    private void initWebSocketConnection(String wsUrl, int generation) {
        try {
            URI uri = URI.create(wsUrl);
            logger().operation("Connect").with("endpoint", wsUrl).debug("Opening ReSync WebSocket");

            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    if (!isCurrentWebSocket(this, generation)) {
                        close();
                        return;
                    }
                    logger().operation("Connect").info("ReSync WebSocket opened");
                    logger().operation("Handshake").debug("Sending handshake");
                    sendHandshake();
                }

                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    if (!isCurrentWebSocket(this, generation)) {
                        return;
                    }
                    handleBinaryMessage(copyRemaining(bytes));
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (!isCurrentWebSocket(this, generation)) {
                        return;
                    }
                    authenticated.set(false);
                    disconnectCollaboration("Disconnected");
                    notifyPluginChannelsUnavailable();
                    connecting.set(false);
                    playerTrackingSubscribed = false;
                    playerControlCapabilities = null;
                    disconnectListener.run();
                    failPlayerControlRequests("ReSync Disconnected");
                    cancelConnectTimeout();
                    nodeRegistrySynced = false;
                    cancelNodeRegistryTimeout();
                    OptionCatalogCache.getInstance().clearRequestsInFlight(serverId);
                    OptionCatalogCache.getInstance().markServerStale(serverId);
                    stopHeartbeat();
                    logger().operation("Connect").with("code", code).with("reason", reason).with("remote", remote).warn("ReSync WebSocket closed");
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    if (!isCurrentWebSocket(this, generation)) {
                        return;
                    }
                    logger().operation("Connect").error("ReSync WebSocket failed", ex);
                    authenticated.set(false);
                    disconnectCollaboration("Connection Failed");
                    notifyPluginChannelsUnavailable();
                    connecting.set(false);
                    playerTrackingSubscribed = false;
                    playerControlCapabilities = null;
                    disconnectListener.run();
                    failPlayerControlRequests("ReSync Connection Failed");
                    cancelConnectTimeout();
                    nodeRegistrySynced = false;
                    cancelNodeRegistryTimeout();
                    OptionCatalogCache.getInstance().clearRequestsInFlight(serverId);
                    OptionCatalogCache.getInstance().markServerStale(serverId);
                    stopHeartbeat();
                    wsClient.compareAndSet(this, null);
                    close();
                    scheduleReconnect();
                }
            };
            wsClient.set(client);
            client.connect();
        } catch (Exception e) {
            logger().operation("Connect").error("Could not open ReSync WebSocket", e);
            connecting.set(false);
            cancelConnectTimeout();
            scheduleReconnect();
        }
    }

    private void sendHandshake() {
        String clientId = stableClientId;
        logger().operation("Handshake").with("clientId", clientId).debug("ReSync client identified");
        byte[] apiKeyBytes = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] clientIdBytes = clientId.getBytes(StandardCharsets.UTF_8);
        byte[] clientVersionBytes = CLIENT_VERSION.getBytes(StandardCharsets.UTF_8);
        byte[] capabilitiesBytes = gson.toJson(FLOW_CONTRACT_CAPABILITIES).getBytes(StandardCharsets.UTF_8);
        byte[] collaborationProfileBytes = collaborationProfile().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(
                4 + apiKeyBytes.length +
                        4 + clientIdBytes.length +
                        4 +
                        4 + clientVersionBytes.length +
                        4 + capabilitiesBytes.length +
                        4 + collaborationProfileBytes.length
        );

        buffer.putInt(apiKeyBytes.length);
        buffer.put(apiKeyBytes);

        buffer.putInt(clientIdBytes.length);
        buffer.put(clientIdBytes);

        buffer.putInt(PROTOCOL_VERSION);
        buffer.putInt(clientVersionBytes.length);
        buffer.put(clientVersionBytes);
        buffer.putInt(capabilitiesBytes.length);
        buffer.put(capabilitiesBytes);
        buffer.putInt(collaborationProfileBytes.length);
        buffer.put(collaborationProfileBytes);

        sendFrame(0, buffer.array(), (short) 0);
    }

    private String collaborationProfile() {
        JsonObject profile = new JsonObject();
        ReStudio studio = ReStudio.getInstance();
        String subjectId = studio.getUserId();
        String displayName = studio.getDisplayName();
        profile.addProperty("subjectId", subjectId != null && !subjectId.isBlank() ? subjectId : stableClientId);
        profile.addProperty("displayName", displayName != null && !displayName.isBlank() ? displayName : "Collaborator");
        profile.addProperty("avatar", studio.getAvatarUrl() != null ? studio.getAvatarUrl() : "");
        profile.addProperty("source", "restudio");
        return gson.toJson(profile);
    }

    private void subscribeStartupChannels() {
        sendSubscribe("flow");
        sendSubscribe("world_management");
        sendSubscribe("worldgen");
        if (!watchedPlayers.isEmpty()) {
            sendSubscribe("player_tracking", "{\"mode\":\"scoped\"}");
            playerTrackingSubscribed = true;
            for (UUID playerId : watchedPlayers) sendPlayerTrackingAction("watch", playerId);
        }
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
        lastFullNodeRegistryRequestAt = 0L;
        scheduleConnectTimeout(connectionGeneration.incrementAndGet());
        frameTransport.setFrameHandler(this::handleBinaryMessage);
        frameTransport.setCloseHandler(() -> {
            authenticated.set(false);
            disconnectCollaboration("Disconnected");
            notifyPluginChannelsUnavailable();
            connecting.set(false);
            playerTrackingSubscribed = false;
            playerControlCapabilities = null;
            disconnectListener.run();
            failPlayerControlRequests("ReSync Disconnected");
            cancelConnectTimeout();
            nodeRegistrySynced = false;
            cancelNodeRegistryTimeout();
            OptionCatalogCache.getInstance().clearRequestsInFlight(serverId);
            OptionCatalogCache.getInstance().markServerStale(serverId);
            stopHeartbeat();
        });
        this.apiKey = "bridge";
        sendHandshake();
        return CompletableFuture.completedFuture(null);
    }

    private void sendSubscribe(String channelId) {
        sendSubscribe(channelId, "");
    }

    private void sendSubscribe(String channelId, String data) {
        byte[] channelBytes = channelId.getBytes(StandardCharsets.UTF_8);
        byte[] dataBytes = data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + channelBytes.length + 4 + dataBytes.length);
        buffer.putInt(channelBytes.length);
        buffer.put(channelBytes);
        buffer.putInt(dataBytes.length);
        buffer.put(dataBytes);
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
            switch (frame.messageType()) {
                case ReSyncProtocolContract.MESSAGE_HANDSHAKE_RESPONSE:
                    handleHandshakeResponse(frame.payload());
                    break;
                case ReSyncProtocolContract.MESSAGE_DATA:
                    handleDataMessage(frame.channel(), frame.payload());
                    break;
                case ReSyncProtocolContract.MESSAGE_HEARTBEAT:
                    break;
                case ReSyncProtocolContract.MESSAGE_ERROR:
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
            logger().operation("Process Message").error("Could not process ReSync message", e);
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
        logger().operation("Protocol").with("protocolError", message).error("ReSync protocol error");
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
            logger().operation("Handshake").error("ReSync server rejected the connection");
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
        logger().operation("Handshake").with("protocolVersion", protocolVersion).debug("ReSync protocol negotiated");

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
        logger().operation("Handshake").with("worldCount", worldCount).debug("ReSync worlds received");
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
        if (buffer.remaining() >= 4) {
            String capabilitiesJson = readSizedString(buffer);
            if (capabilitiesJson != null && !capabilitiesJson.isBlank()) {
                JsonObject capabilities = gson.fromJson(capabilitiesJson, JsonObject.class);
                if (!validateNegotiatedFlowCapabilities(capabilities)) {
                    connecting.set(false);
                    cancelConnectTimeout();
                    return;
                }
                FlowManager manager = FlowManager.getInstance();
                if (manager != null && capabilities != null) {
                    manager.cacheServerCapabilities(serverId, capabilities);
                }
                notifyDurabilityHealth(capabilities);
            }
        }

        synchronized (resourceListRequestLock) {
            authenticated.set(true);
            notifiedConnectionError.set(null);
            subscribeStartupChannels();
            subscribePluginChannels();
            notifyPluginChannelsAvailable();
            collaboration.connectionReady();
            workspaces.connect();
            flushPendingResourceListRequests();
        }
        connecting.set(false);
        cancelConnectTimeout();
        logger().operation("Handshake").info("ReSync client authenticated");
        startHeartbeat();
        OptionCatalogCache.getInstance().markServerStale(serverId);
        requestNodeRegistry();
        requestJobSnapshots();
        flushPendingSends();
    }

    private boolean validateNegotiatedFlowCapabilities(JsonObject capabilities) {
        if (capabilities == null || !capabilities.has("flowContract") || !capabilities.get("flowContract").isJsonObject()) {
            protocolError("Server did not negotiate the Flow registry contract");
            return false;
        }
        JsonObject contract = capabilities.getAsJsonObject("flowContract");
        int version = contract.has("version") ? contract.get("version").getAsInt() : 0;
        int minimumClientVersion = contract.has("minimumClientVersion") ? contract.get("minimumClientVersion").getAsInt() : 0;
        if (version < NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION
            || version > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || minimumClientVersion > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION) {
            protocolError("Server Flow registry contract is incompatible: " + version);
            return false;
        }
        Set<String> negotiated = ConcurrentHashMap.newKeySet();
        if (contract.has("negotiated") && contract.get("negotiated").isJsonArray()) {
            for (JsonElement element : contract.getAsJsonArray("negotiated")) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    negotiated.add(element.getAsString());
                }
            }
        }
        if (!negotiated.containsAll(REQUIRED_FLOW_CONTRACT_CAPABILITIES)) {
            Set<String> missing = ConcurrentHashMap.newKeySet();
            missing.addAll(REQUIRED_FLOW_CONTRACT_CAPABILITIES);
            missing.removeAll(negotiated);
            protocolError("Server Flow registry contract is missing capabilities: " + String.join(", ", missing.stream().sorted().toList()));
            return false;
        }
        return true;
    }

    private void notifyDurabilityHealth(JsonObject capabilities) {
        if (capabilities == null || !capabilities.has("durabilityHealth") || !capabilities.get("durabilityHealth").isJsonObject()) {
            return;
        }
        JsonObject health = capabilities.getAsJsonObject("durabilityHealth");
        String fingerprint = health.toString();
        if (fingerprint.equals(durabilityHealthFingerprint)) {
            return;
        }
        durabilityHealthFingerprint = fingerprint;
        String status = health.has("status") ? health.get("status").getAsString() : "HEALTHY";
        int issues = health.has("issues") && health.get("issues").isJsonArray() ? health.getAsJsonArray("issues").size() : 0;
        int recovered = health.has("recoveredTransactions") ? health.get("recoveredTransactions").getAsInt() : 0;
        if ("HEALTHY".equals(status) && recovered == 0) {
            return;
        }
        if ("DEGRADED".equals(status) && onlyRecoverableCopies(health)) {
            return;
        }
        Notification.Type type = "CRITICAL".equals(status) ? Notification.Type.ERROR
            : "DEGRADED".equals(status) ? Notification.Type.WARN : Notification.Type.INFO;
        String title = "HEALTHY".equals(status) ? "Storage Recovered" : "Storage Needs Attention";
        String message = durabilityMessage(health, issues, recovered);
        ScreenManager.getInstance().execute(() -> new Notification(title, message, type));
    }

    private boolean onlyRecoverableCopies(JsonObject health) {
        if (!health.has("issues") || !health.get("issues").isJsonArray() || health.getAsJsonArray("issues").isEmpty()) {
            return false;
        }
        for (JsonElement element : health.getAsJsonArray("issues")) {
            if (!element.isJsonObject()) {
                return false;
            }
            String code = element.getAsJsonObject().has("code") ? element.getAsJsonObject().get("code").getAsString() : "";
            if (!Set.of("ORPHANED_RESOURCE_COPY", "ORPHANED_GRAPH_COPY").contains(code)) {
                return false;
            }
        }
        return true;
    }

    private String durabilityMessage(JsonObject health, int issues, int recovered) {
        if (issues <= 0) {
            return recovered + " Interrupted Saves Recovered";
        }
        JsonObject issue = health.getAsJsonArray("issues").get(0).getAsJsonObject();
        String resourceId = issue.has("resourceId") ? issue.get("resourceId").getAsString() : "";
        String detail = issue.has("message") ? issue.get("message").getAsString() : issues + " Storage Issues";
        String path = issue.has("path") ? issue.get("path").getAsString() : "";
        String message = resourceId.isBlank() ? detail : resourceId + " · " + detail;
        if (!path.isBlank()) {
            message += "\n" + path;
        }
        return issues > 1 ? message + " · " + (issues - 1) + " More" : message;
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
        subscribePluginChannels();
        notifyPluginChannelsAvailable();
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
        if (!availablePluginChannels.remove(channelId)) {
            return;
        }
        Set<PluginChannelListener> listeners = pluginChannelListeners.get(channelId);
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

    private void subscribePluginChannels() {
        if (!isConnectedState()) {
            return;
        }
        for (String channelId : new ArrayList<>(pluginChannelSubscriptions)) {
            if (isPluginChannel(channelId) && channelIds.containsKey(channelId)) {
                sendSubscribe(channelId);
            }
        }
    }

    private void notifyPluginChannelsAvailable() {
        if (!isConnectedState()) {
            return;
        }
        for (String channelId : channelIds.keySet()) {
            if (!isPluginChannel(channelId)) {
                continue;
            }
            if (!availablePluginChannels.add(channelId)) {
                continue;
            }
            Set<PluginChannelListener> listeners = pluginChannelListeners.get(channelId);
            if (listeners != null) {
                for (PluginChannelListener listener : listeners) {
                    listener.onAvailable(channelId);
                }
            }
        }
    }

    private void notifyPluginChannelsUnavailable() {
        for (String channelId : List.copyOf(availablePluginChannels)) {
            if (!availablePluginChannels.remove(channelId)) {
                continue;
            }
            Set<PluginChannelListener> listeners = pluginChannelListeners.get(channelId);
            if (listeners != null) {
                for (PluginChannelListener listener : listeners) {
                    listener.onRemoved(channelId);
                }
            }
        }
    }

    private short numericChannel(String channelId, short fallback) {
        Short numericId = channelIds.get(channelId);
        return numericId == null ? fallback : numericId;
    }

    private boolean isActiveGeneration(int generation) {
        return connectionGeneration.get() == generation && !shutdownRequested;
    }

    private boolean isCurrentWebSocket(WebSocketClient client, int generation) {
        return isActiveGeneration(generation) && wsClient.get() == client;
    }

    private void scheduleConnectTimeout(int generation) {
        cancelConnectTimeout();
        connectTimeoutTask = heartbeatScheduler.schedule(() -> {
            if (!isActiveGeneration(generation) || authenticated.get()) {
                return;
            }
            connecting.set(false);
            WebSocketClient client = wsClient.get();
            if (client != null && !authenticated.get()) {
                client.close();
                wsClient.compareAndSet(client, null);
            }
            connectionGeneration.compareAndSet(generation, generation + 1);
            DesignerSaveNotifications.SaveTarget failedSave = DesignerSaveNotifications.failAnyForServer(serverId, "ReSync Connection Timed Out");
            if (failedSave != null) {
                if (failedSave.shouldUpdateResourceState()) {
                    markResourceFailed(this.client != null ? this.client.getFlowManager() : null, failedSave.type(), failedSave.id());
                }
                return;
            }
            if (WorldGenManager.getInstance().failProjectSaveFromRequestId(serverId, "", "ReSync Connection Timed Out")) {
                return;
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

        ReSyncResourceType dataType = ReSyncResourceType.byDataResponse(packetId);
        if (dataType != null) {
            handleResourceData(dataType, buffer);
            return;
        }
        ReSyncResourceType listType = ReSyncResourceType.byListResponse(packetId);
        if (listType != null) {
            handleResourceList(listType, buffer);
            return;
        }
        ReSyncResourceType saveAckType = ReSyncResourceType.bySaveAck(packetId);
        if (saveAckType != null) {
            handleResourceSaveAck(saveAckType, buffer);
            return;
        }

        switch (packetId) {
            case ReSyncProtocolContract.FLOW_PACKET_GUI_STATE:
                handleGuiState(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_ERROR:
                handleFlowError(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_EDIT_TARGET_STATE:
                handleEditTargetState(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_PRESENCE_SNAPSHOT:
                handlePresenceSnapshot(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_RESOURCE_CHANGED:
                handleResourceEvent(buffer, false);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_RESOURCE_DELETED:
                handleResourceEvent(buffer, true);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_SNAPSHOT:
                workspaces.applySnapshot(readRemainingJson(buffer));
                break;
            case ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_OPERATION:
                workspaces.applyOperation(readRemainingJson(buffer));
                break;
            case ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_AWARENESS:
                workspaces.applyAwareness(readRemainingJson(buffer));
                break;
            case ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_RESYNC:
                workspaces.applyResync(readRemainingJson(buffer));
                break;
            case ReSyncProtocolContract.FLOW_PACKET_COLLABORATION_CHAT:
                collaboration.applyMessage(readRemainingJson(buffer));
                break;
            case ReSyncProtocolContract.FLOW_PACKET_QUICK_EDIT_OPEN:
                handleQuickEditOpen(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_QUICK_EDIT_RESULT:
                handleQuickEditResult(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_OPEN_CUSTOM_CONTENT:
                handleOpenCustomContent(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_JOB:
                handleFlowJob(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_PLACEHOLDER_PREVIEW:
                handlePlaceholderPreview(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_OPTION_CATALOG:
                handleOptionCatalog(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_TRACE_SNAPSHOT:
                handleTraceSnapshot(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_TRACE_EVENT:
                handleTraceEvent(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_DEBUG_EVENT:
                handleDebugSnapshot(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_FUNCTION_TEST_RESULT:
                handleFunctionTestResult(buffer);
                break;
            case ReSyncProtocolContract.MESSAGE_LOG_PACKET_RESPONSE:
                handleMessageLogPage(buffer);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_NODE_REGISTRY:
                handleNodeRegistrySnapshot(buffer, true);
                break;
            case ReSyncProtocolContract.FLOW_PACKET_NODE_REGISTRY_DELTA:
                handleNodeRegistrySnapshot(buffer, false);
                break;
        }
    }

    private void handlePlayerTrackingMessage(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            JsonObject envelope = gson.fromJson(json, JsonObject.class);
            String type = envelope != null && envelope.has("type") ? envelope.get("type").getAsString() : "";
            if ("player_control_capabilities".equals(type)) {
                playerControlCapabilities = envelope;
                return;
            }
            if ("player_control_response".equals(type)) {
                String requestId = envelope.has("requestId") ? envelope.get("requestId").getAsString() : "";
                CompletableFuture<JsonObject> pending = pendingPlayerControlRequests.remove(requestId);
                if (pending != null) pending.complete(envelope);
                return;
            }
            PlayerTrackingUpdate update = gson.fromJson(json, PlayerTrackingUpdate.class);
            if (update == null) {
                return;
            }
            if (client != null && client.getFlowManager() != null) {
                client.getFlowManager().applyPlayerTrackingUpdate(serverId, update);
            }
        } catch (Exception e) {
            logger().operation("Player Tracking").error("Could not read player tracking update", e);
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
            logger().operation("World Management").error("Could not read world management update", e);
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

    private void handleMessageLogPage(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        try {
            JsonObject page = gson.fromJson(json, JsonObject.class);
            FlowManager manager = client != null ? client.getFlowManager() : null;
            if (manager != null && page != null) {
                manager.cacheMessageLogPage(serverId, page);
            }
        } catch (Exception e) {
            logger().operation("Message Log").error("Could not read message log", e);
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
            jobId = stringField(data, "taskId");
        }
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        JsonObject previous = jobs.put(jobId, data);
        String status = stringField(data, "status");
        String action = stringField(data, "action");
        logger().operation("Run Job").with("jobId", jobId).with("action", action).with("status", status).debug("ReSync job updated");
        String previousStatus = previous != null ? stringField(previous, "status") : null;
        boolean duplicateTerminal = status != null && status.equalsIgnoreCase(previousStatus) && isTerminalJobStatus(status);
        if (duplicateTerminal) {
            return;
        }
        if ("succeeded".equalsIgnoreCase(status)) {
            completeResourceDelete(data);
            refreshAfterJob(action);
        } else if ("failed".equalsIgnoreCase(status)) {
            if (!terminalJobNotifications.add(jobId)) {
                return;
            }
            String reason = stringField(data, "errorText");
            if (reason == null || reason.isBlank()) {
                reason = stringField(data, "message");
            }
            List<Map<String, Object>> attributeErrors = parseAttributeValidationErrors(reason);
            if (!attributeErrors.isEmpty()) {
                List<Map<String, Object>> errors = attributeErrors;
                ScreenManager.getInstance().execute(() -> ContentDesignerScreen.handleAttributeValidationErrorsForServer(serverId, errors));
                reason = summarizeAttributeValidationErrors(attributeErrors);
            } else {
                reason = formatFlowDiagnostics(reason);
            }
            String message = reason == null || reason.isBlank() ? "Failed" : reason;
            String requestId = stringField(data, "requestId");
            if (requestId == null || requestId.isBlank()) {
                requestId = stringField(data, "operationId");
            }
            failResourceDelete(requestId, message);
            DesignerSaveNotifications.SaveTarget failedSave = DesignerSaveNotifications.failRequest(serverId, requestId, message);
            if (failedSave != null) {
                if (failedSave.shouldUpdateResourceState()) {
                    markResourceFailed(client != null ? client.getFlowManager() : null, failedSave.type(), failedSave.id());
                }
                return;
            }
            if (isWorldGenProjectSaveAction(action) && WorldGenManager.getInstance().failProjectSaveFromRequestId(serverId, requestId, message)) {
                return;
            }
            if (DesignerSaveNotifications.consumeRecentError(serverId, message)) {
                return;
            }
            String title = action == null || action.isBlank() ? "ReSync Failed" : action + " Failed";
            ScreenManager.getInstance().execute(() -> new Notification(title, message, Notification.Type.ERROR));
        }
    }

    private List<Map<String, Object>> parseAttributeValidationErrors(String reason) {
        if (reason == null || !reason.contains("ATTRIBUTE_VALIDATION:")) {
            return List.of();
        }
        String json = reason.substring(reason.indexOf("ATTRIBUTE_VALIDATION:") + "ATTRIBUTE_VALIDATION:".length());
        try {
            JsonElement root = gson.fromJson(json, JsonElement.class);
            if (root == null || !root.isJsonArray()) {
                return List.of();
            }
            List<Map<String, Object>> errors = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                        error.put(entry.getKey(), entry.getValue() != null && !entry.getValue().isJsonNull() ? entry.getValue().getAsString() : "");
                    }
                    errors.add(error);
                }
            }
            return errors;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private String summarizeAttributeValidationErrors(List<Map<String, Object>> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Invalid Components";
        }
        Map<String, Object> first = errors.getFirst();
        String component = String.valueOf(first.getOrDefault("component", "")).trim();
        String message = String.valueOf(first.getOrDefault("message", "")).trim();
        if (!component.isBlank() && !message.isBlank()) {
            return component + ": " + message;
        }
        if (!component.isBlank()) {
            return component;
        }
        return !message.isBlank() ? message : "Invalid Components";
    }

    private String formatFlowDiagnostics(String raw) {
        if (raw == null || raw.isBlank()) {
            return "The flow could not be saved.";
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            List<String> messages = new ArrayList<>();
            for (String issue : raw.split(";")) {
                String friendly = issue.trim().replaceFirst("^[A-Z][A-Z0-9_]*:\\s*", "");
                if (!friendly.isBlank() && !messages.contains(friendly)) {
                    messages.add(sentence(friendly));
                }
            }
            return messages.isEmpty() ? raw : String.join("\n", messages);
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw.substring(start, end + 1));
            if (!parsed.isJsonArray()) {
                return raw;
            }
            List<String> messages = new ArrayList<>();
            String remediation = "";
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject diagnostic = element.getAsJsonObject();
                String message = diagnosticText(diagnostic, "message");
                if (message.isBlank()) {
                    continue;
                }
                String pin = diagnosticText(diagnostic, "pin");
                String friendly = sentence(message);
                if (!pin.isBlank()) {
                    friendly += " Check " + sentenceLabel(pin) + ".";
                }
                messages.add(friendly);
                if (remediation.isBlank()) {
                    remediation = diagnosticText(diagnostic, "remediation");
                }
                if (messages.size() == 3) {
                    break;
                }
            }
            if (messages.isEmpty()) {
                return raw;
            }
            String result = String.join("\n", messages);
            int total = parsed.getAsJsonArray().size();
            if (total > messages.size()) {
                result += "\n" + (total - messages.size()) + " more issue" + (total - messages.size() == 1 ? "" : "s") + " need attention.";
            }
            if (!remediation.isBlank()) {
                result += "\nHow to fix: " + sentence(remediation);
            }
            return result;
        } catch (RuntimeException ignored) {
            return raw;
        }
    }

    private void completeResourceDelete(JsonObject data) {
        String requestId = stringField(data, "requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = stringField(data, "operationId");
        }
        ReSyncResourceKey key = pendingResourceDeletes.remove(requestId);
        FlowManager manager = client != null ? client.getFlowManager() : null;
        if (key == null || manager == null) {
            return;
        }
        ReSyncResourceType type = ReSyncResourceType.byTypeId(key.type());
        if (type != null) {
            manager.confirmResourceDeleted(serverId, type, key.id());
        }
    }

    private void failResourceDelete(String requestId, String message) {
        ReSyncResourceKey key = pendingResourceDeletes.remove(requestId);
        FlowManager manager = client != null ? client.getFlowManager() : null;
        if (key == null || manager == null) {
            return;
        }
        ReSyncResourceType type = ReSyncResourceType.byTypeId(key.type());
        if (type != null) {
            manager.failResourceDelete(serverId, type, key.id(), message);
        }
    }

    private void handlePresenceSnapshot(ByteBuffer buffer) {
        if (collaboration.applySnapshot(readRemainingJson(buffer))) {
            FlowManager manager = client != null ? client.getFlowManager() : null;
            if (manager != null) {
                manager.refreshStudioWorkspace(serverId, true);
            }
        }
    }

    private void handleResourceEvent(ByteBuffer buffer, boolean deleted) {
        ResourceEvent event;
        try {
            event = gson.fromJson(readRemainingJson(buffer), ResourceEvent.class);
        } catch (RuntimeException exception) {
            protocolError("Invalid resource event");
            return;
        }
        if (event == null || event.type() == null || event.resourceId() == null) {
            return;
        }
        collaboration.applyResourceChange(new ReSyncCollaborationClient.ResourceChange(event.type(), event.resourceId(),
            event.authorSessionId(), event.author(), event.changedAt(), deleted));
        ReSyncResourceType type = ReSyncResourceType.byTypeId(event.type());
        FlowManager manager = client != null ? client.getFlowManager() : null;
        if (manager == null || type == null) {
            return;
        }
        if (deleted) {
            manager.confirmResourceDeleted(serverId, type, event.resourceId());
            requestResourceList(type);
            return;
        }
        try {
            Object item = type.deserialize(event.payload());
            cacheResource(manager, type, item);
            handleResourceDataReceived(manager, type, item);
            manager.refreshStudioWorkspace(serverId, true);
        } catch (RuntimeException exception) {
            requestResource(type, event.resourceId(), false);
        }
    }

    public void publishPresence(String resourceType, String resourceId, String viewId, double x, double y, boolean active) {
        collaboration.publishPresence(resourceType, resourceId, viewId, x, y, active, false);
    }

    public void publishPresence(String resourceType, String resourceId, String viewId, double x, double y, boolean active, boolean typing) {
        collaboration.publishPresence(resourceType, resourceId, viewId, x, y, active, typing);
    }

    private void sendCollaborationPresence(String resourceType, String resourceId, String viewId,
                                           double x, double y, boolean active, boolean typing) {
        if (!isConnected()) {
            return;
        }
        JsonObject presence = new JsonObject();
        presence.addProperty("resourceType", resourceType != null ? resourceType : "");
        presence.addProperty("resourceId", resourceId != null ? resourceId : "");
        presence.addProperty("viewId", viewId != null ? viewId : "");
        presence.addProperty("x", Math.clamp(x, 0.0, 1.0));
        presence.addProperty("y", Math.clamp(y, 0.0, 1.0));
        presence.addProperty("active", active);
        presence.addProperty("typing", typing);
        if (Config.configManager instanceof RemotelyConfigManager config) {
            Integer color = config.getCollaborationColorOverride();
            if (color != null) {
                presence.addProperty("color", color);
            }
        }
        byte[] json = gson.toJson(presence).getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(1 + json.length);
        packet.put(ReSyncProtocolContract.FLOW_PACKET_PRESENCE_UPDATE);
        packet.put(json);
        sendFrame(ReSyncProtocolContract.MESSAGE_DATA, packet.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    public void publishCollaborationMessage(String message) {
        collaboration.publishMessage(message);
    }

    private void sendCollaborationMessage(String message) {
        String text = message != null ? message.trim() : "";
        if (text.isBlank() || !isConnected()) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("message", text.length() <= 240 ? text : text.substring(0, 240));
        byte[] json = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(1 + json.length);
        packet.put(ReSyncProtocolContract.FLOW_PACKET_COLLABORATION_CHAT);
        packet.put(json);
        sendFrame(ReSyncProtocolContract.MESSAGE_DATA, packet.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    public void joinWorkspace(String type, String resourceId, ReSyncWorkspaceClient.Listener listener) {
        workspaces.join(type, resourceId, listener);
    }

    public void leaveWorkspace(String type, String resourceId, ReSyncWorkspaceClient.Listener listener) {
        workspaces.leave(type, resourceId, listener);
    }

    public String publishWorkspaceOperation(String type, String resourceId, List<WorkspacePatch<JsonElement>> patches) {
        return workspaces.publishOperation(type, resourceId, patches);
    }

    public void publishWorkspaceAwareness(String type, String resourceId, JsonObject state) {
        workspaces.publishAwareness(type, resourceId, state);
    }

    public void resyncWorkspace(String type, String resourceId) {
        JsonObject request = new JsonObject();
        request.addProperty("type", type);
        request.addProperty("resourceId", resourceId);
        sendWorkspacePacket(ReSyncProtocolContract.FLOW_PACKET_WORKSPACE_JOIN, request);
    }

    private boolean sendWorkspacePacket(byte packetId, JsonObject payload) {
        if (!isConnected() || !supportsFlowCapability("live_workspace")) {
            return false;
        }
        byte[] json = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(1 + json.length);
        packet.put(packetId);
        packet.put(json);
        sendFrame(4, packet.array(), numericChannel("flow", FLOW_CHANNEL_ID));
        return true;
    }

    private record ResourceEvent(String type, String resourceId, String payload, String authorSessionId,
                                 ReSyncCollaborationClient.Identity author, long changedAt) {
    }

    private String diagnosticText(JsonObject diagnostic, String field) {
        JsonElement value = diagnostic.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
    }

    private String sentence(String value) {
        String text = value == null ? "" : value.trim();
        return text.isBlank() || text.endsWith(".") ? text : text + ".";
    }

    private String sentenceLabel(String value) {
        String text = value == null ? "" : value.trim().replace('_', ' ').replace('-', ' ');
        if (text.isBlank()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void refreshAfterJob(String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        switch (action) {
            case "saveFlow" -> {}
            case "deleteFlow" -> requestFlowList();
            case "saveGui", "deleteGui" -> requestGuiList();
            case "saveScoreboard", "deleteScoreboard" -> requestScoreboardList();
            case "saveTab", "deleteTab" -> requestTabList();
            case "saveCustomContent" -> {}
            case "deleteCustomContent" -> requestCustomContentList();
            case "saveProjectMetadata", "deleteProjectMetadata" -> {}
            case "saveWorldGenProject", "deleteWorldGenProject" -> requestWorldGenProjectList();
            default -> refreshJsonResourceAfterJob(action);
        }
    }

    private void refreshJsonResourceAfterJob(String action) {
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            String suffix = compactDisplayName(type.displayName());
            if (action.equals("save" + suffix) || action.equals("delete" + suffix)) {
                requestResourceList(type);
                return;
            }
        }
    }

    private String compactDisplayName(String displayName) {
        StringBuilder builder = new StringBuilder();
        boolean uppercaseNext = true;
        for (int i = 0; i < displayName.length(); i++) {
            char current = displayName.charAt(i);
            if (!Character.isLetterOrDigit(current)) {
                uppercaseNext = true;
                continue;
            }
            builder.append(uppercaseNext ? Character.toUpperCase(current) : current);
            uppercaseNext = false;
        }
        return builder.toString();
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

    private String resourceId(ReSyncResourceType type, Object item) {
        if (type == null || item == null) {
            return "null";
        }
        String id = type.extractId(item);
        return id != null ? id : "null";
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
                        if (type.isGraph()) {
                            FlowGraph graph = (FlowGraph) item;
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            if (studioScreen != null) {
                                studioScreen.openWorkspaceGraphEditor(graph);
                                fm.activateOpenStudio(serverId, false);
                                return;
                            }
                            Screen current = ScreenManager.getInstance().getCurrentScreen();
                            if (current instanceof FlowGraphDesignerScreen screen
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
                                fm.activateOpenStudio(serverId, false);
                                return;
                            }
                            client.getHost().setScreen(new GuiDesignerScreen((GuiDefinition) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.SCOREBOARD) {
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            if (studioScreen != null) {
                                studioScreen.openWorkspaceScoreboardDesigner(itemId);
                                fm.activateOpenStudio(serverId, false);
                                return;
                            }
                            client.getHost().setScreen(new ScoreboardDesignerScreen((ScoreboardDefinition) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.TAB) {
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            if (studioScreen != null) {
                                studioScreen.openWorkspaceTabDesigner(itemId);
                                fm.activateOpenStudio(serverId, false);
                                return;
                            }
                            client.getHost().setScreen(new TabDesignerScreen((TabDefinition) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.CUSTOM_CONTENT && FlowEditorScreen.getStudioScreen(serverId) != null) {
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            studioScreen.openWorkspaceResource(type.typeId(), itemId);
                            fm.activateOpenStudio(serverId, false);
                        } else if (FlowEditorScreen.getStudioScreen(serverId) != null && item instanceof JsonObject) {
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            studioScreen.openWorkspaceResource(type.typeId(), itemId);
                            fm.activateOpenStudio(serverId, false);
                        } else if (type == ReSyncResourceType.ADVANCEMENT_TREE) {
                            client.getHost().setScreen(new AdvancementDesignerScreen((JsonObject) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.DIALOG) {
                            FlowEditorScreen studioScreen = FlowEditorScreen.getStudioScreen(serverId);
                            if (studioScreen != null) {
                                studioScreen.openWorkspaceDialogDesigner(itemId);
                                fm.activateOpenStudio(serverId, false);
                                return;
                            }
                            client.getHost().setScreen(new DialogDesignerScreen((JsonObject) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.TRADE_PROFILE) {
                            client.getHost().setScreen(new TradeDesignerScreen(null, itemId, (JsonObject) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.NPC_DEFINITION) {
                            client.getHost().setScreen(new NpcDesignerScreen(null, itemId, (JsonObject) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        } else if (type == ReSyncResourceType.LOOT_TABLE) {
                            client.getHost().setScreen(new LootTableDesignerScreen(null, itemId, (JsonObject) item, serverId, ScreenManager.getInstance().getCurrentScreen()));
                        }
                    }
                });
            }
        }
    }

    private void cacheResource(FlowManager fm, ReSyncResourceType type, Object item) {
        if (type.isGraph()) fm.cacheFlow(serverId, (FlowGraph) item);
        else if (type == ReSyncResourceType.GUI) fm.cacheGui(serverId, (GuiDefinition) item);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.cacheScoreboard(serverId, (ScoreboardDefinition) item);
        else if (type == ReSyncResourceType.TAB) fm.cacheTab(serverId, (TabDefinition) item);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) fm.cacheCustomContent(serverId, (CustomContentDefinition) item);
        else if (type == ReSyncResourceType.PROJECT_METADATA) fm.cacheProjectMetadata(serverId, (ReSyncProjectMetadata) item);
        else if (item instanceof JsonObject json) fm.cacheJsonResource(serverId, type, json);
    }

    private void handleResourceDataReceived(FlowManager fm, ReSyncResourceType type, Object item) {
        if (type == ReSyncResourceType.GUI) fm.handleGuiDataReceived(serverId, (GuiDefinition) item);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.handleScoreboardDataReceived(serverId, (ScoreboardDefinition) item);
        else if (type == ReSyncResourceType.TAB) fm.handleTabDataReceived(serverId, (TabDefinition) item);
        else if (type == ReSyncResourceType.ADVANCEMENT_TREE && item instanceof JsonObject tree) fm.handleAdvancementTreeDataReceived(serverId, tree);
        else if (type == ReSyncResourceType.DIALOG && item instanceof JsonObject dialog) fm.handleDialogDataReceived(serverId, dialog);
        else if ((type == ReSyncResourceType.TRADE_PROFILE || type == ReSyncResourceType.NPC_DEFINITION || type == ReSyncResourceType.LOOT_TABLE) && item instanceof JsonObject resource) fm.handleFocusedJsonResourceDataReceived(serverId, type, resource);
    }

    private void markResourceSaved(FlowManager fm, ReSyncResourceType type, String id) {
        if (type.isGraph()) fm.markFlowSaved(serverId, type, id);
        else if (type == ReSyncResourceType.GUI) fm.markGuiSaved(serverId, id);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.markScoreboardSaved(serverId, id);
        else if (type == ReSyncResourceType.TAB) fm.markTabSaved(serverId, id);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) fm.markCustomContentSaved(serverId, id);
        else if (type == ReSyncResourceType.PROJECT_METADATA) fm.markProjectMetadataSaved(serverId);
        else fm.markJsonResourceSaved(serverId, type, id);
    }

    private void markResourceFailed(FlowManager fm, ReSyncResourceType type, String id) {
        if (fm != null && type != null && id != null && !id.isBlank()) {
            fm.markResourceSaveFailed(serverId, type, id);
        }
    }

    private void applyServerResourceList(FlowManager fm, ReSyncResourceType type, List<String> ids) {
        if (type.isGraph()) fm.applyServerGraphList(serverId, type, ids);
        else if (type == ReSyncResourceType.GUI) fm.applyServerGuiList(serverId, ids);
        else if (type == ReSyncResourceType.SCOREBOARD) fm.applyServerScoreboardList(serverId, ids);
        else if (type == ReSyncResourceType.TAB) fm.applyServerTabList(serverId, ids);
        else if (type == ReSyncResourceType.CUSTOM_CONTENT) fm.applyServerCustomContentList(serverId, ids);
        else if (type == ReSyncResourceType.PROJECT_METADATA) fm.applyServerProjectMetadataList(serverId, ids);
        else fm.applyServerJsonResourceList(serverId, type, ids);
    }

    private void handleQuickEditOpen(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        JsonObject root = gson.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), JsonObject.class);
        if (root == null || !root.has("definition") || !root.get("definition").isJsonObject()) {
            return;
        }
        String sessionId = root.has("sessionId") && !root.get("sessionId").isJsonNull() ? root.get("sessionId").getAsString() : "";
        CustomContentDefinition definition = gson.fromJson(root.get("definition"), CustomContentDefinition.class);
        if (definition == null) {
            return;
        }
        ScreenManager.getInstance().execute(() -> {
            FlowManager manager = client != null ? client.getFlowManager() : null;
            if (manager != null) {
                manager.openStudioDocument(serverId, "quick_edit:" + sessionId, studioScreen -> {
                    ContentDesignerScreen screen = ContentDesignerScreen.quickEdit(serverId, sessionId, definition, studioScreen);
                    String documentId = definition.getId() != null && !definition.getId().isBlank() ? definition.getId() : "quickedit_" + (sessionId != null && !sessionId.isBlank() ? sessionId : "item");
                    studioScreen.openWorkspaceContentDesigner(documentId, "Quick Edit", screen.getContentGraph(), screen, false);
                });
            }
        });
    }

    private void handleQuickEditResult(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        JsonObject root = gson.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), JsonObject.class);
        String status = root != null && root.has("status") && !root.get("status").isJsonNull() ? root.get("status").getAsString() : "";
        if ("applied".equals(status)) {
            ScreenManager.getInstance().execute(() -> new Notification("Quick Edit", "Applied", Notification.Type.SUCCESS));
        } else if ("failed".equals(status)) {
            String message = root.has("message") && !root.get("message").isJsonNull() ? root.get("message").getAsString() : "Apply Failed";
            ScreenManager.getInstance().execute(() -> new Notification("Quick Edit Failed", message, Notification.Type.ERROR));
        }
    }

    private void handleOpenCustomContent(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        JsonObject root = gson.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), JsonObject.class);
        if (root == null || !root.has("content") || !root.get("content").isJsonObject()) {
            return;
        }
        CustomContentDefinition content = gson.fromJson(root.get("content"), CustomContentDefinition.class);
        if (content == null || content.getId() == null || content.getId().isBlank()) {
            return;
        }
        FlowManager manager = client != null ? client.getFlowManager() : null;
        if (manager != null) {
            manager.cacheCustomContent(serverId, content);
        }
        ScreenManager.getInstance().execute(() -> openCustomContentEditor(content));
    }

    private void openCustomContentEditor(CustomContentDefinition content) {
        FlowManager manager = client != null ? client.getFlowManager() : null;
        if (manager == null || content == null) {
            return;
        }
        FlowGraph graph = content.getGraph();
        if (graph == null) {
            return;
        }
        manager.openStudioDocument(serverId, "custom_content:" + content.getId(), studioScreen -> studioScreen.openWorkspaceContentDesigner(content.getId(), content.getDisplayName(), graph));
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
        if (client != null && client.getFlowManager() != null) {
            client.getFlowManager().handleGuiStatePacket(serverId, editable, guiId, flowId);
        }
    }

    private void handleEditTargetState(ByteBuffer buffer) {
        boolean editable = buffer.get() == 1;
        String resourceType = readSizedString(buffer);
        String resourceId = readSizedString(buffer);
        String flowId = readSizedString(buffer);
        if (client != null && client.getFlowManager() != null) {
            client.getFlowManager().handleEditTargetStatePacket(serverId, editable, resourceType, resourceId, flowId);
        }
    }

    private void handleFlowError(ByteBuffer buffer) {
        int messageLen = buffer.getInt();
        if (messageLen < 0 || messageLen > buffer.remaining()) return;

        byte[] messageBytes = new byte[messageLen];
        buffer.get(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);

        logger().with("flowError", message).error("ReSync flow error");
        List<Map<String, Object>> attributeErrors = parseAttributeValidationErrors(message);
        if (!attributeErrors.isEmpty()) {
            ScreenManager.getInstance().execute(() -> ContentDesignerScreen.handleAttributeValidationErrorsForServer(serverId, attributeErrors));
            message = summarizeAttributeValidationErrors(attributeErrors);
        } else {
            message = formatFlowDiagnostics(message);
        }

        String finalMessage = message;
        DesignerSaveNotifications.SaveTarget failedSave = DesignerSaveNotifications.failAnyForServer(serverId, finalMessage);
        if (failedSave != null) {
            if (failedSave.shouldUpdateResourceState()) {
                markResourceFailed(client != null ? client.getFlowManager() : null, failedSave.type(), failedSave.id());
            }
            return;
        }
        if (DesignerSaveNotifications.consumeRecentError(serverId, finalMessage)) {
            return;
        }
        ScreenManager.getInstance().execute(() ->
            new Notification("Flow Save Failed", finalMessage, Notification.Type.ERROR)
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
        String requestId = "";
        if (buffer.remaining() >= 4) {
            int requestIdLen = buffer.getInt();
            if (requestIdLen < 0 || requestIdLen > buffer.remaining()) {
                return;
            }
            byte[] requestIdBytes = new byte[requestIdLen];
            buffer.get(requestIdBytes);
            requestId = new String(requestIdBytes, StandardCharsets.UTF_8);
        }
        long revision = 0L;
        String hash = "";
        if (buffer.remaining() >= Long.BYTES + Integer.BYTES) {
            revision = buffer.getLong();
            int hashLength = buffer.getInt();
            if (hashLength < 0 || hashLength > buffer.remaining()) {
                return;
            }
            byte[] hashBytes = new byte[hashLength];
            buffer.get(hashBytes);
            hash = new String(hashBytes, StandardCharsets.UTF_8);
        }

        boolean automaticNotificationSuppressed = DesignerSaveNotifications.consumeAutomaticNotificationSuppression(requestId);
        DesignerSaveNotifications.SaveTarget completedSave = DesignerSaveNotifications.complete(serverId, type, id, requestId);
        boolean completedNotification = completedSave != null;
        boolean showNotification = !automaticNotificationSuppressed && !completedNotification && shouldShowSaveNotification(type, id);
        if (showNotification) {
            ScreenManager.getInstance().execute(() ->
                new Notification(type.displayName() + " Saved", "ID: " + id, Notification.Type.SUCCESS)
            );
        }

        boolean markSaved = completedSave == null || completedSave.shouldUpdateResourceState();
        if (markSaved && client != null && client.getFlowManager() != null) {
            if (type.isGraph() && revision > 0L) {
                client.getFlowManager().markFlowSaved(serverId, type, id, revision, hash);
                return;
            }
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
        if (!type.isGraph() || client == null || client.getFlowManager() == null || id == null) {
            return false;
        }
        FlowManager manager = client.getFlowManager();
        FlowGraph graph = manager.getGraph(serverId, type, id);
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

    private void handleResourceList(ReSyncResourceType type, ByteBuffer buffer) {
        pendingResourceListRequests.remove(type);
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

    private void handleNodeRegistrySnapshot(ByteBuffer buffer, boolean fullSync) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        try {
            NodeRegistrySnapshot snapshot = gson.fromJson(json, NodeRegistrySnapshot.class);
            if (snapshot == null) {
                return;
            }
            if (!compatibleRegistrySnapshot(snapshot)) {
                return;
            }
            snapshot.setFullSync(fullSync);
            if (!snapshot.canApplyTo(currentRegistryChecksum())) {
                logger().operation("Node Registry").warn("Registry delta baseline changed; requesting a full snapshot");
                requestNodeRegistry(true);
                return;
            }
            if (snapshot.getServerIdentity().isBlank()) {
                snapshot.setServerIdentity(serverId);
            }
            NodeRegistry registry = NodeRegistry.getInstance();
            if (registry != null) {
                if (!registry.applySnapshot(serverId, snapshot)) {
                    logger().operation("Node Registry").warn("Active registry rejected the snapshot; requesting a full snapshot");
                    requestNodeRegistry(true);
                    return;
                }
                NodeRegistryTombstoneCache.getInstance().replace(serverId, registry.getUnresolvedPluginPayloads(serverId));
                nodeRegistryCache.applySnapshot(serverId, registry.materializeSnapshot(serverId, snapshot));
            } else if (snapshot.isFullSync()) {
                nodeRegistryCache.applySnapshot(serverId, snapshot);
            } else {
                requestNodeRegistry(true);
                return;
            }
            nodeRegistrySynced = true;
            usingCachedRegistry = false;
            if (snapshot.isFullSync()) {
                lastFullNodeRegistryRequestAt = 0L;
            }
            cancelNodeRegistryTimeout();
            notifyNodeRegistryUpdated();
        } catch (Exception e) {
            logger().operation("Node Registry").error("Could not read node registry snapshot", e);
        }
    }

    private boolean compatibleRegistrySnapshot(NodeRegistrySnapshot snapshot) {
        int version = snapshot.getContractVersion();
        if (version < NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION) {
            protocolError("Node registry contract " + version + " is older than supported contract " + NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION);
            return false;
        }
        if (version > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || snapshot.getMinimumClientContractVersion() > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION) {
            protocolError("Node registry contract " + version + " requires a newer Remotely client");
            return false;
        }
        if (!snapshot.getServerIdentity().isBlank() && !serverId.equals(snapshot.getServerIdentity())) {
            protocolError("Node registry snapshot belongs to another server");
            return false;
        }
        if (snapshot.getCompatibleUntil() > 0 && snapshot.getCompatibleUntil() < System.currentTimeMillis()) {
            protocolError("Node registry snapshot compatibility window has expired");
            return false;
        }
        return true;
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
            OptionCatalogSnapshot payload = gson.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), OptionCatalogSnapshot.class);
            if (payload != null && !payload.getSourceId().isBlank()) {
                if (payload.getVersion() > OptionCatalogSnapshot.CURRENT_VERSION) {
                    protocolError("Unsupported option catalog version: " + payload.getVersion());
                    return;
                }
                String contextKey = payload.getContextKey();
                String requestKey = optionCatalogRequestKey(payload.getSourceId(), contextKey);
                OptionCatalogCache cache = OptionCatalogCache.getInstance();
                pendingOptionCatalogRequests.remove(requestKey);
                boolean changed = cache.put(serverId, payload.getSourceId(), contextKey, payload.getRevision(), payload.getSequence(), payload.getValues(), payload.getItems(), payload.getStatus(), payload.getDiagnostic());
                if (!changed) {
                    return;
                }
                ScreenManager.getInstance().execute(() -> {
                    FlowEditorScreen.refreshCatalogForServer(serverId, payload.getSourceId());
                    GuiDesignerScreen.refreshCatalogForServer(serverId);
                    AdvancementDesignerScreen.refreshCatalogForServer(serverId);
                    DialogDesignerScreen.refreshCatalogForServer(serverId);
                    FocusedJsonResourceDesignerScreen.refreshCatalogForServer(serverId);
                    FlowManager manager = client != null ? client.getFlowManager() : null;
                    if (manager != null) {
                        manager.refreshStudioWorkspace(serverId, true);
                    }
                });
            }
        } catch (Exception e) {
            logger().operation("Option Catalog").error("Could not read option catalog", e);
        }
    }

    public void requestNodeRegistry() {
        requestNodeRegistry(false);
    }

    private void requestNodeRegistry(boolean fullSync) {
        if (!isConnected()) {
            pendingSends.add(() -> requestNodeRegistry(fullSync));
            ensureConnected();
            return;
        }
        long now = System.currentTimeMillis();
        if (fullSync && now - lastFullNodeRegistryRequestAt < 10_000L) {
            return;
        }
        if (fullSync) {
            lastFullNodeRegistryRequestAt = now;
        }
        NodeRegistryRequest request = new NodeRegistryRequest();
        request.setContractVersion(NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION);
        if (!fullSync) {
            request.setRegistryChecksum(currentRegistryChecksum());
            request.setPluginChecksums(nodeRegistryCache.getPluginChecksums(serverId));
        }
        String json = gson.toJson(request);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_NODE_REGISTRY_REQUEST);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
        scheduleNodeRegistryTimeout();
    }

    private String currentRegistryChecksum() {
        NodeRegistrySnapshot cached = nodeRegistryCache.getSnapshot(serverId);
        if (cached != null && cached.getRegistryChecksum() != null && !cached.getRegistryChecksum().isBlank()) {
            return cached.getRegistryChecksum();
        }
        NodeRegistry registry = NodeRegistry.getInstance();
        NodeRegistry.RegistrySessionMetadata metadata = registry != null ? registry.getRegistrySessionMetadata(serverId) : null;
        return metadata != null ? metadata.checksum() : "";
    }

    public void requestOptionCatalog(String sourceId) {
        requestOptionCatalog(sourceId, Map.of(), false);
    }

    public void requestOptionCatalog(String sourceId, boolean forceRefresh) {
        requestOptionCatalog(sourceId, Map.of(), forceRefresh);
    }

    public void requestOptionCatalog(String sourceId, Map<String, Object> context) {
        requestOptionCatalog(sourceId, context, false);
    }

    public String optionCatalogContextKey(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return "";
        }
        return gson.toJson(new TreeMap<>(context));
    }

    public void requestOptionCatalog(String sourceId, Map<String, Object> context, boolean forceRefresh) {
        if (sourceId == null || sourceId.isBlank()) {
            return;
        }
        Map<String, Object> normalizedContext = context != null && !context.isEmpty() ? new TreeMap<>(context) : Map.of();
        String contextKey = optionCatalogContextKey(normalizedContext);
        String requestKey = optionCatalogRequestKey(sourceId, contextKey);
        OptionCatalogCache cache = OptionCatalogCache.getInstance();
        if (forceRefresh) {
            if (cache.isRequestInFlight(serverId, sourceId, contextKey) || pendingOptionCatalogRequests.contains(requestKey)) {
                return;
            }
            cache.markStale(serverId, sourceId, contextKey);
            pendingOptionCatalogRequests.remove(requestKey);
        } else if (cache.hasCatalog(serverId, sourceId, contextKey) && !cache.isStale(serverId, sourceId, contextKey)) {
            return;
        }
        if (!isConnected()) {
            if (pendingOptionCatalogRequests.add(requestKey)) {
                pendingSends.add(() -> {
                    pendingOptionCatalogRequests.remove(requestKey);
                    requestOptionCatalog(sourceId, normalizedContext, forceRefresh);
                });
            }
            ensureConnected();
            return;
        }
        if (!cache.markRequestInFlight(serverId, sourceId, contextKey)) {
            return;
        }
        sendOptionCatalogRequest(sourceId, contextKey, normalizedContext);
    }

    private void sendOptionCatalogRequest(String sourceId, String contextKey, Map<String, Object> context) {
        byte[] sourceBytes = sourceId.getBytes(StandardCharsets.UTF_8);
        byte[] requestBytes = gson.toJson(Map.of("version", 2, "contextKey", contextKey, "context", context)).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + sourceBytes.length + 4 + requestBytes.length);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_OPTION_CATALOG_REQUEST);
        buffer.putInt(sourceBytes.length);
        buffer.put(sourceBytes);
        buffer.putInt(requestBytes.length);
        buffer.put(requestBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private String optionCatalogRequestKey(String sourceId, String contextKey) {
        return (sourceId != null ? sourceId : "") + "\u0000" + (contextKey != null ? contextKey : "");
    }

    public void requestMessageLog(int page, int pageSize, String query, String source) {
        if (!isConnected()) {
            pendingSends.add(() -> requestMessageLog(page, pageSize, query, source));
            ensureConnected();
            return;
        }
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("page", Math.max(0, page));
        request.put("pageSize", Math.clamp(pageSize <= 0 ? 20 : pageSize, 1, 100));
        request.put("query", query != null ? query : "");
        request.put("source", source != null ? source : "");
        byte[] jsonBytes = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put(ReSyncProtocolContract.MESSAGE_LOG_PACKET_REQUEST);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private void requestJobSnapshots() {
        requestFlowJobSnapshot();
        requestWorldJobSnapshot();
        requestWorldGenJobSnapshot();
    }

    private void requestFlowJobSnapshot() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_JOB_SNAPSHOT_REQUEST);
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
        List<NodePluginPayload> tombstones = NodeRegistryTombstoneCache.getInstance().get(serverId);
        if (cached == null && tombstones.isEmpty()) {
            return;
        }
        NodeRegistry registry = NodeRegistry.getInstance();
        boolean restored = false;
        if (registry != null) {
            if (cached != null) {
                restored = registry.applySnapshot(serverId, cached);
            }
            registry.restoreUnresolvedPlugins(serverId, tombstones);
            restored |= !tombstones.isEmpty();
        } else {
            restored = cached != null || !tombstones.isEmpty();
        }
        usingCachedRegistry = restored;
    }

    private void notifyNodeRegistryUpdated() {
        ScreenManager.getInstance().execute(() -> {
            Screen current = ScreenManager.getInstance().getCurrentScreen();
            if (current instanceof FlowGraphDesignerScreen screen && serverId.equals(screen.getServerId())) {
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

    public void requestResource(ReSyncResourceType type, String id, boolean openWhenReceived) {
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

    void requestResourceList(ReSyncResourceType type) {
        synchronized (resourceListRequestLock) {
            int token = resourceListRequestSequence.incrementAndGet();
            if (pendingResourceListRequests.putIfAbsent(type, token) != null) {
                if (!isConnected()) {
                    ensureConnected();
                }
                return;
            }
            if (!isConnected()) {
                ensureConnected();
                return;
            }
            sendResourceListRequest(type, token);
        }
    }

    private void sendResourceListRequest(ReSyncResourceType type, int token) {
        if (!Objects.equals(pendingResourceListRequests.get(type), token)) {
            return;
        }
        if (!isConnected()) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put(type.listRequestByte());
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private void flushPendingResourceListRequests() {
        for (ReSyncResourceType type : List.copyOf(pendingResourceListRequests.keySet())) {
            int token = resourceListRequestSequence.incrementAndGet();
            if (pendingResourceListRequests.computeIfPresent(type, (ignored, current) -> token) != null) {
                sendResourceListRequest(type, token);
            }
        }
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
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_DEBUG_COMMAND);
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
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_PLACEHOLDER_PREVIEW_REQUEST);
        buffer.putInt(requestId);
        buffer.put((byte) (usePapi ? 1 : 0));
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    public void requestFunctionTest(String graphId, String name, Map<String, Object> inputs, Map<String, Object> expectedOutputs,
                                    Map<String, Object> serverContext, String clockInstant, String zoneId, long timeoutMillis,
                                    Consumer<JsonObject> callback) {
        requestFunctionTest(graphId, null, name, inputs, expectedOutputs, serverContext, clockInstant, zoneId, timeoutMillis, callback);
    }

    public void requestFunctionTest(FlowGraph graph, String name, Map<String, Object> inputs, Map<String, Object> expectedOutputs,
                                    Map<String, Object> serverContext, String clockInstant, String zoneId, long timeoutMillis,
                                    Consumer<JsonObject> callback) {
        requestFunctionTest(graph != null ? graph.getId() : "", graph, name, inputs, expectedOutputs, serverContext, clockInstant, zoneId, timeoutMillis, callback);
    }

    private void requestFunctionTest(String graphId, FlowGraph graph, String name, Map<String, Object> inputs, Map<String, Object> expectedOutputs,
                                     Map<String, Object> serverContext, String clockInstant, String zoneId, long timeoutMillis,
                                     Consumer<JsonObject> callback) {
        if (graphId == null || graphId.isBlank() || callback == null) {
            return;
        }
        if (!isConnected()) {
            callback.accept(errorResult("NOT_CONNECTED", "ReSync is unavailable"));
            ensureConnected();
            return;
        }
        String requestId = stableClientId + ":function-test:" + functionTestRequestCounter.getAndIncrement();
        functionTestCallbacks.put(requestId, callback);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestId", requestId);
        request.put("graphId", graphId);
        if (graph != null) {
            request.put("graph", JsonParser.parseString(FlowSerializer.serialize(graph)));
        }
        request.put("name", name != null && !name.isBlank() ? name : "Fixture");
        request.put("inputs", inputs != null ? inputs : Map.of());
        request.put("expectedOutputs", expectedOutputs != null ? expectedOutputs : Map.of());
        request.put("serverContext", serverContext != null ? serverContext : Map.of());
        request.put("clockInstant", clockInstant != null ? clockInstant : "");
        request.put("zoneId", zoneId != null && !zoneId.isBlank() ? zoneId : "UTC");
        request.put("timeoutMillis", Math.clamp(timeoutMillis, 1L, 30000L));
        byte[] jsonBytes = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_FUNCTION_TEST_REQUEST);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
        heartbeatScheduler.schedule(() -> {
            Consumer<JsonObject> pending = functionTestCallbacks.remove(requestId);
            if (pending != null) {
                pending.accept(errorResult("FUNCTION_TEST_TIMEOUT", "Function test timed out"));
            }
        }, 35L, TimeUnit.SECONDS);
    }

    private void handleFunctionTestResult(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        JsonObject result = gson.fromJson(json, JsonObject.class);
        String requestId = result != null && result.has("requestId") ? result.get("requestId").getAsString() : "";
        Consumer<JsonObject> callback = functionTestCallbacks.remove(requestId);
        if (callback != null) {
            callback.accept(result != null ? result : errorResult("INVALID_FUNCTION_TEST_RESULT", "Function test result is invalid"));
        }
    }

    private JsonObject errorResult(String code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject root = new JsonObject();
        root.add("error", error);
        return root;
    }

    public void requestPlayerTrackingSnapshot() {
        sendPlayerTrackingAction("snapshot", null);
    }

    public void requestPlayerDossier(UUID playerId) {
        sendPlayerTrackingAction("dossier", playerId);
    }

    public void watchPlayer(UUID playerId) {
        if (playerId == null || !isConnected()) return;
        if (!playerTrackingSubscribed) {
            sendSubscribe("player_tracking", "{\"mode\":\"scoped\"}");
            playerTrackingSubscribed = true;
        }
        if (watchedPlayers.add(playerId)) sendPlayerTrackingAction("watch", playerId);
    }

    public void unwatchPlayer(UUID playerId) {
        watchedPlayers.remove(playerId);
        sendPlayerTrackingAction("unwatch", playerId);
    }

    public JsonObject getPlayerControlCapabilities() {
        return playerControlCapabilities;
    }

    public void requestPlayerControlCapabilities() {
        sendPlayerTrackingAction("capabilities", null);
    }

    public CompletableFuture<JsonObject> requestPlayerControl(String action, UUID playerId, Map<String, Object> payload) {
        if (!isConnected()) return CompletableFuture.failedFuture(new IllegalStateException("ReSync Unavailable"));
        String requestId = serverId + '-' + playerControlSequence.incrementAndGet();
        JsonObject request = payload == null ? new JsonObject() : gson.toJsonTree(payload).getAsJsonObject();
        request.addProperty("type", "player_control");
        request.addProperty("version", 2);
        request.addProperty("requestId", requestId);
        request.addProperty("action", action);
        if (playerId != null) request.addProperty("playerId", playerId.toString());
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        synchronized (pendingPlayerControlRequests) {
            if (!isConnected()) return CompletableFuture.failedFuture(new IllegalStateException("ReSync Unavailable"));
            pendingPlayerControlRequests.put(requestId, result);
        }
        try {
            heartbeatScheduler.schedule(() -> {
                CompletableFuture<JsonObject> pending = pendingPlayerControlRequests.remove(requestId);
                if (pending != null) pending.completeExceptionally(new TimeoutException("ReSync Player Request Timed Out"));
            }, 5, TimeUnit.SECONDS);
            sendFrame(4, gson.toJson(request).getBytes(StandardCharsets.UTF_8), numericChannel("player_tracking", PLAYER_TRACKING_CHANNEL_ID));
        } catch (Exception exception) {
            pendingPlayerControlRequests.remove(requestId);
            result.completeExceptionally(exception);
        }
        return result;
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
        sendResourceSave(type, item, item != null ? mutationRequestId(type.displayName(), type.extractId(item)) : "");
    }

    private void sendResourceSave(ReSyncResourceType type, Object item, String requestId) {
        if (item == null) {
            return;
        }
        String id = type.extractId(item);
        DesignerSaveNotifications.attachRequestId(serverId, type, id, requestId);
        if (!isConnected()) {
            logger().operation("Save Flow").with("flowType", type.displayName()).warn("ReSync is disconnected; save queued");
            pendingSends.add(() -> sendResourceSave(type, item, requestId));
            ensureConnected();
            return;
        }
        String json;
        try {
            json = type.serialize(item);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank() ? "Save Failed" : e.getMessage();
            DesignerSaveNotifications.SaveTarget failedSave = DesignerSaveNotifications.failResource(serverId, type, id, message);
            if (failedSave != null) {
                if (failedSave.shouldUpdateResourceState()) {
                    markResourceFailed(client != null ? client.getFlowManager() : null, failedSave.type(), failedSave.id());
                }
            }
            return;
        }
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        if (type == ReSyncResourceType.FLOW) {
            logger().operation("Save Flow").with("bytes", jsonBytes.length).debug("Sending flow save");
        }
        byte[] requestIdBytes = requestId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + jsonBytes.length);
        buffer.put(type.saveByte());
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    void sendResourceDelete(ReSyncResourceType type, String id) {
        sendResourceDelete(type, id, id);
    }

    private void sendResourceDelete(ReSyncResourceType type, String id, String payload) {
        String requestId = mutationRequestId(type.displayName() + "Delete", id);
        pendingResourceDeletes.put(requestId, new ReSyncResourceKey(type.typeId(), id));
        sendResourceDelete(type, id, payload, requestId);
    }

    private void sendResourceDelete(ReSyncResourceType type, String id, String payload, String requestId) {
        if (id == null || id.isEmpty()) {
            pendingResourceDeletes.remove(requestId);
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendResourceDelete(type, id, payload, requestId));
            ensureConnected();
            return;
        }
        byte[] requestIdBytes = requestId.getBytes(StandardCharsets.UTF_8);
        byte[] idBytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + idBytes.length);
        buffer.put(type.deleteByte());
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(idBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    public void sendFlowSave(FlowGraph graph) { sendResourceSave(ReSyncResourceType.FLOW, graph); }
    public void sendGraphSave(ReSyncResourceType type, FlowGraph graph) {
        if (type != null && type.isGraph()) {
            sendResourceSave(type, graph);
        }
    }
    public void sendGraphDelete(ReSyncResourceType type, String id) {
        if (type != null && type.isGraph()) {
            sendResourceDelete(type, id);
        }
    }
    public void sendGuiSave(GuiDefinition gui) { sendResourceSave(ReSyncResourceType.GUI, gui); }
    public void sendScoreboardSave(ScoreboardDefinition scoreboard) { sendResourceSave(ReSyncResourceType.SCOREBOARD, scoreboard); }
    public void sendTabSave(TabDefinition tab) { sendResourceSave(ReSyncResourceType.TAB, tab); }
    public void sendCustomContentSave(CustomContentDefinition content) { sendResourceSave(ReSyncResourceType.CUSTOM_CONTENT, content); }
    public void sendProjectMetadataSave(ReSyncProjectMetadata metadata) { sendResourceSave(ReSyncResourceType.PROJECT_METADATA, metadata); }
    public void sendQuickEditApply(String sessionId, CustomContentDefinition definition) {
        if (sessionId == null || sessionId.isBlank() || definition == null) {
            return;
        }
        if (!isConnected()) {
            pendingSends.add(() -> sendQuickEditApply(sessionId, definition));
            ensureConnected();
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("sessionId", sessionId);
        root.add("definition", gson.toJsonTree(definition));
        byte[] jsonBytes = gson.toJson(root).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_QUICK_EDIT_APPLY);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }
    public void sendFlowDelete(String flowId) {
        FlowManager manager = FlowManager.getInstance();
        FlowGraph graph = manager != null ? manager.getGraph(serverId, ReSyncResourceType.FLOW, flowId) : null;
        if (graph != null && graph.getResourceRevision() > 0L && supportsFlowCapability("resource_revisions")) {
            JsonObject payload = new JsonObject();
            payload.addProperty("id", flowId);
            payload.addProperty("expectedRevision", graph.getResourceRevision());
            sendResourceDelete(ReSyncResourceType.FLOW, flowId, gson.toJson(payload));
            return;
        }
        sendResourceDelete(ReSyncResourceType.FLOW, flowId);
    }
    public void sendGuiDelete(String guiId) { sendResourceDelete(ReSyncResourceType.GUI, guiId); }
    public void sendScoreboardDelete(String scoreboardId) { sendResourceDelete(ReSyncResourceType.SCOREBOARD, scoreboardId); }
    public void sendTabDelete(String tabId) { sendResourceDelete(ReSyncResourceType.TAB, tabId); }

    public boolean supportsFlowCapability(String capability) {
        FlowManager manager = FlowManager.getInstance();
        JsonObject capabilities = manager != null ? manager.getServerCapabilities(serverId) : null;
        if (capabilities == null || !capabilities.has("flowContract") || !capabilities.get("flowContract").isJsonObject()) {
            return false;
        }
        JsonObject contract = capabilities.getAsJsonObject("flowContract");
        if (!contract.has("negotiated") || !contract.get("negotiated").isJsonArray()) {
            return false;
        }
        for (JsonElement element : contract.getAsJsonArray("negotiated")) {
            if (element.isJsonPrimitive() && capability.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

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
            logger().operation("Update Trigger").warn("ReSync is disconnected; trigger update queued");
            pendingSends.add(() -> sendTriggerUpdate(bindings));
            ensureConnected();
            return;
        }

        String json = gson.toJson(bindings != null ? bindings : List.of());
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] requestIdBytes = mutationRequestId("triggerUpdate", serverId).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + jsonBytes.length);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_TRIGGER_UPDATE);
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private String mutationRequestId(String action, String target) {
        return stableClientId + ":" + action + ":" + (target != null ? target : "") + ":" + UUID.randomUUID();
    }

    private boolean isWorldGenProjectSaveAction(String action) {
        return "saveWorldGenProject".equals(action) || "worldGenProjectSave".equals(action);
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

    private boolean notifyConnectionError(String message) {
        boolean changed = !message.equals(notifiedConnectionError.getAndSet(message));
        if (changed && errorListener != null) {
            errorListener.onError(null, message);
        }
        return changed;
    }

    private void handleError(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int errorCode = buffer.getInt();
        int messageLen = buffer.getInt();
        byte[] messageBytes = new byte[messageLen];
        buffer.get(messageBytes);
        String errorText = new String(messageBytes);

        logger().with("errorCode", errorCode).with("errorText", errorText).error("ReSync returned an error");
    }

    public void shutdown() {
        logger().operation("Disconnect").info("Closing ReSync WebSocket");
        shutdownRequested = true;
        stopHeartbeat();
        ReSyncLuckPermsClient currentLuckPerms = luckPermsClient;
        if (currentLuckPerms != null) {
            currentLuckPerms.close();
        }
        if (frameTransport != null) {
            frameTransport.close();
        }
        WebSocketClient client = wsClient.getAndSet(null);
        if (client != null) {
            try {
                client.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                client.close();
            } catch (Exception ignored) {
                client.close();
            }
        }
        authenticated.set(false);
        connecting.set(false);
        pendingResourceListRequests.clear();
        placeholderPreviewCallbacks.clear();
        functionTestCallbacks.clear();
        failPlayerControlRequests("ReSync Disconnected");
        watchedPlayers.clear();
        playerTrackingSubscribed = false;
        cancelNodeRegistryTimeout();
        cancelConnectTimeout();
        collaboration.clear();
        workspaces.clear();
        nodeRegistryScheduler.shutdownNow();
        heartbeatScheduler.shutdownNow();
    }

    public boolean isConnectedState() {
        return isConnected();
    }

    private void disconnectCollaboration(String reason) {
        collaboration.connectionLost();
        workspaces.disconnect(reason);
    }

    public ConnectionState connectionState() {
        if (isConnected()) {
            return ConnectionState.CONNECTED;
        }
        return connecting.get() ? ConnectionState.CONNECTING : ConnectionState.DISCONNECTED;
    }

    private void failPlayerControlRequests(String reason) {
        synchronized (pendingPlayerControlRequests) {
            IllegalStateException error = new IllegalStateException(reason);
            for (Map.Entry<String, CompletableFuture<JsonObject>> entry : pendingPlayerControlRequests.entrySet()) {
                if (pendingPlayerControlRequests.remove(entry.getKey(), entry.getValue())) entry.getValue().completeExceptionally(error);
            }
        }
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

    private static String stableClientId(String serverId) {
        ReStudio studio = ReStudio.getInstance();
        String installationId = studio.getClientId();
        String seed = (serverId == null || serverId.isBlank() ? "default" : serverId) + ':' +
            (installationId == null || installationId.isBlank() ? "remotely" : installationId);
        return "remotely-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private ReLogger logger() {
        String sourceId = serverId == null || serverId.isBlank() ? "unresolved" : serverId;
        return ReLog.logger(LogTypes.FLOW).source(LogSource.server(sourceId, sourceId)).component(ReSyncFlowClient.class);
    }

    private static class PlayerTrackingRequest {
        private String action;
        private String playerId;
    }

}
