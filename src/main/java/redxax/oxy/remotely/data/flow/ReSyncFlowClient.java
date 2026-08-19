package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.util.BrowserSafeState;

import redxax.oxy.remotely.RemotelyServerApi;
import redxax.oxy.remotely.collaboration.CollaborationService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redxax.oxy.remotely.flow.data.FlowJson;
import redxax.oxy.remotely.flow.data.FlowSerializer;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ScoreboardDefinition;
import redxax.oxy.remotely.flow.data.TabDefinition;
import redxax.oxy.remotely.flow.data.TriggerBinding;
import redxax.oxy.remotely.flow.cache.NodeRegistryCache;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.NodePluginPayload;
import redxax.oxy.remotely.flow.sync.NodeRegistryRequest;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshotJson;
import redxax.oxy.remotely.flow.sync.OptionCatalogSnapshot;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingUpdate;
import redxax.oxy.remotely.data.flow.player.PlayerTrackingJson;
import redxax.oxy.remotely.data.flow.world.WorldChannelMessage;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenSerializer;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import restudio.rescreen.logging.ReLogger;
import restudio.rescreen.platform.Clock;
import restudio.rescreen.platform.TaskScheduler;
import restudio.rescreen.platform.Async;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.resync.protocol.ReSyncHandshakeCodec;
import restudio.resync.protocol.ReSyncHandshakeRequest;
import restudio.resync.protocol.ReSyncHandshakeResponse;
import restudio.resync.protocol.ReSyncProtocolContract;
import restudio.resync.flow.workspace.LiveDocumentChannel;
import restudio.resync.flow.workspace.WorkspacePatch;
import restudio.resync.flow.workspace.WorkspaceTarget;
import restudio.resync.flow.contract.EditorError;
import restudio.resync.resource.ReSyncResourceKey;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

public class ReSyncFlowClient {
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public enum ReadinessState {
        DISCONNECTED,
        CONNECTING,
        WAITING_FOR_REGISTRY,
        READY,
        INCOMPATIBLE
    }

    public enum ConnectionFailure {
        NONE,
        ENDPOINT_UNREACHABLE,
        HANDSHAKE_REJECTED,
        PROTOCOL_MISMATCH,
        RUNTIME_VERSION_MISMATCH,
        ACCESS_DENIED
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
    private final RemotelyServerApi apiClient;
    private final String directWsUrl;
    private final String directApiKey;
    private final ReSyncFlowClientState state;
    private final ReSyncFlowCaches caches;
    private final NodeRegistry nodeRegistry;
    private final ReSyncLuckPermsProvider luckPermsProvider;
    private final ReSyncFrameTransport suppliedTransport;
    private final String suppliedTransportApiKey;
    private final ReSyncFrameTransportFactory transportFactory;
    private final TaskScheduler scheduler;
    private final boolean ownsScheduler;
    private final Clock clock;
    private final ReSyncIdentityProvider identityProvider;
    private final ReSyncCredentialProvider credentialProvider;
    private final BrowserSafeState.ReferenceValue<ReSyncFrameTransport> activeTransport = new BrowserSafeState.ReferenceValue<>();
    private final BrowserSafeState.BooleanValue authenticated = new BrowserSafeState.BooleanValue(false);
    private final BrowserSafeState.BooleanValue connecting = new BrowserSafeState.BooleanValue(false);
    private final BrowserSafeState.ReferenceValue<ConnectionFailure> connectionFailure = new BrowserSafeState.ReferenceValue<>(ConnectionFailure.NONE);
    private final BrowserSafeState.ReferenceValue<String> notifiedConnectionError = new BrowserSafeState.ReferenceValue<>();
    private final BrowserSafeState.ReferenceValue<ReadinessState> readinessState = new BrowserSafeState.ReferenceValue<>(ReadinessState.DISCONNECTED);
    private final Set<Async<ReadinessState>> readinessWaiters = BrowserSafeState.set();
    private String apiKey;
    private volatile boolean transportAuthenticated;
    private volatile boolean flowContractCompatible;
    private volatile boolean cachedRegistryValid;
    private volatile boolean terminalIncompatible;
    private volatile String readinessFailureMessage = "";
    private final Set<String> negotiatedFlowCapabilities = BrowserSafeState.set();
    private final RemotelyReSyncFrameCodec frameCodec = new RemotelyReSyncFrameCodec();
    private final ReSyncHandshakeCodec handshakeCodec = new ReSyncHandshakeCodec(
        ReSyncProtocolContract.MAX_DECOMPRESSED_PAYLOAD_BYTES,
        ReSyncProtocolContract.MAX_HANDSHAKE_FIELD_BYTES,
        ReSyncProtocolContract.MAX_HANDSHAKE_COLLECTION_ENTRIES
    );
    private static final int PROTOCOL_VERSION = ReSyncProtocolContract.PROTOCOL_VERSION;
    private static final List<String> FLOW_CONTRACT_CAPABILITIES = List.of("nodes", "types", "categories", "properties", "resources", "catalogs", "conversions", "extensions", "deltas", "diagnostics", "contextual_catalogs", "authorization", "destructive_safety", "function_tests", "jobs", "job_events", "resource_operation_diagnostics", "extension_validators", "resource_revisions", "asset_integrity", "transaction_recovery", "migration_fencing", "opaque_resources", "collaboration_presence", "collaboration_chat", "resource_events", "live_workspace");
    private static final List<String> REQUIRED_FLOW_CONTRACT_CAPABILITIES = List.of("nodes", "types", "categories", "properties", "resources", "catalogs", "conversions", "extensions", "deltas", "diagnostics");
    private static final short FLOW_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_FLOW_ID;
    private static final short PLAYER_TRACKING_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_PLAYER_TRACKING_ID;
    private static final short WORLD_MANAGEMENT_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_WORLD_MANAGEMENT_ID;
    private static final short WORLDGEN_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_WORLDGEN_ID;
    private static final short CONTROL_CHANNEL_ID = ReSyncProtocolContract.CHANNEL_CONTROL_ID;
    private final Map<String, Short> channelIds = BrowserSafeState.map();
    private final Map<Short, String> numericChannels = BrowserSafeState.map();
    private final Map<String, Set<PluginChannelListener>> pluginChannelListeners = BrowserSafeState.map();
    private final Set<String> pluginChannelSubscriptions = BrowserSafeState.set();
    private final Set<String> availablePluginChannels = BrowserSafeState.set();
    private final Set<Consumer<String>> protocolErrorListeners = BrowserSafeState.set();
    private final Set<Async<ConnectionState>> connectionStateWaiters = BrowserSafeState.set();
    private final Object outboundLock = new Object();
    private int sequenceCounter;
    private ErrorListener errorListener;
    private volatile Runnable connectionListener = () -> {};
    private volatile Runnable readyListener = () -> {};
    private volatile Runnable disconnectListener = () -> {};
    private static final int MAX_PENDING_SENDS = 512;
    private final Object pendingSendsLock = new Object();
    private final Deque<Runnable> pendingSends = new ArrayDeque<>();
    private final BrowserSafeState.IntegerValue playerControlSequence = new BrowserSafeState.IntegerValue();
    private final Map<String, Async<JsonObject>> pendingPlayerControlRequests = BrowserSafeState.map();
    private volatile JsonObject playerControlCapabilities;
    private final Set<UUID> watchedPlayers = BrowserSafeState.set();
    private volatile boolean playerTrackingSubscribed;
    private final Map<ReSyncResourceType, Set<String>> pendingOpenResources = BrowserSafeState.map();
    private final Map<ReSyncResourceType, Integer> pendingResourceListRequests = BrowserSafeState.map();
    private final BrowserSafeState.IntegerValue resourceListRequestSequence = new BrowserSafeState.IntegerValue();
    private final Object resourceListRequestLock = new Object();
    private final Set<TaskScheduler.ScheduledTask> scheduledTasks = BrowserSafeState.set();
    private final Set<String> pendingOptionCatalogRequests = BrowserSafeState.set();
    private final Map<String, JsonObject> jobs = BrowserSafeState.map();
    private final Set<String> terminalJobNotifications = BrowserSafeState.set();
    private final Map<String, ReSyncResourceKey> pendingResourceDeletes = BrowserSafeState.map();
    private volatile String durabilityHealthFingerprint = "";
    private final NodeRegistryCache nodeRegistryCache;
    private TaskScheduler.ScheduledTask nodeRegistryTimeout;
    private volatile boolean nodeRegistrySynced = false;
    private volatile boolean usingCachedRegistry = false;
    private volatile long lastFullNodeRegistryRequestAt;
    private static final int NODE_REGISTRY_TIMEOUT_SECONDS = 5;
    private TaskScheduler.ScheduledTask heartbeatTask;
    private TaskScheduler.ScheduledTask reconnectTask;
    private TaskScheduler.ScheduledTask connectTimeoutTask;
    private final BrowserSafeState.IntegerValue connectionGeneration = new BrowserSafeState.IntegerValue();
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;
    private static final int RECONNECT_INITIAL_DELAY_SECONDS = 3;
    private static final int RECONNECT_MAX_DELAY_SECONDS = 30;
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private final Object connectionLock = new Object();
    private final BrowserSafeState.IntegerValue reconnectAttempt = new BrowserSafeState.IntegerValue();
    private volatile boolean shutdownRequested = false;
    private final BrowserSafeState.IntegerValue placeholderRequestCounter = new BrowserSafeState.IntegerValue(1);
    private final Map<Integer, Consumer<String>> placeholderPreviewCallbacks = BrowserSafeState.map();
    private final BrowserSafeState.IntegerValue functionTestRequestCounter = new BrowserSafeState.IntegerValue(1);
    private final Map<String, Consumer<JsonObject>> functionTestCallbacks = BrowserSafeState.map();
    private final WorldGenProtocolHandler worldGenProtocolHandler;
    private final String stableClientId;
    private final ReSyncCollaborationClient collaboration;
    private final ReSyncWorkspaceClient workspaces;

    public ReSyncFlowClient(String serverId, RemotelyServerApi apiClient, String directWsUrl, String directApiKey,
                            Object ignored, TaskScheduler scheduler, Clock clock,
                            ReSyncFrameTransportFactory transportFactory, ReSyncIdentityProvider identityProvider) {
        this(serverId, apiClient, directWsUrl, directApiKey, null, null, ReSyncFlowClientContext.defaults(), scheduler, clock, transportFactory,
            identityProvider, ReSyncCredentialProvider.apiKey(), false);
    }

    public ReSyncFlowClient(String serverId, RemotelyServerApi apiClient, String directWsUrl, String directApiKey,
                            Object ignored, TaskScheduler scheduler, Clock clock,
                            ReSyncFrameTransportFactory transportFactory, ReSyncIdentityProvider identityProvider,
                            ReSyncCredentialProvider credentialProvider) {
        this(serverId, apiClient, directWsUrl, directApiKey, null, null, ReSyncFlowClientContext.defaults(), scheduler, clock, transportFactory,
            identityProvider, credentialProvider, false);
    }

    public ReSyncFlowClient(String serverId, ReSyncFrameTransport frameTransport, String apiKey, Object ignored,
                            TaskScheduler scheduler, Clock clock, ReSyncIdentityProvider identityProvider) {
        this(serverId, frameTransport, apiKey, ReSyncFlowClientContext.defaults(), scheduler, clock, identityProvider, ReSyncCredentialProvider.apiKey());
    }

    public ReSyncFlowClient(String serverId, ReSyncFrameTransport frameTransport, String apiKey, Object ignored,
                            TaskScheduler scheduler, Clock clock, ReSyncIdentityProvider identityProvider,
                            ReSyncCredentialProvider credentialProvider) {
        this(serverId, null, null, null, frameTransport, apiKey, ReSyncFlowClientContext.defaults(), scheduler, clock, null, identityProvider,
            credentialProvider, false);
    }

    public ReSyncFlowClient(String serverId, ReSyncFrameTransport frameTransport, String apiKey,
                            ReSyncFlowClientContext context, TaskScheduler scheduler, Clock clock,
                            ReSyncIdentityProvider identityProvider, ReSyncCredentialProvider credentialProvider) {
        this(serverId, null, null, null, frameTransport, apiKey, context, scheduler, clock, null, identityProvider,
            credentialProvider, false);
    }

    public ReSyncFlowClient(String serverId, RemotelyServerApi apiClient, String directWsUrl, String directApiKey,
                            ReSyncFlowClientContext context, TaskScheduler scheduler, Clock clock,
                            ReSyncFrameTransportFactory transportFactory, ReSyncIdentityProvider identityProvider,
                            ReSyncCredentialProvider credentialProvider) {
        this(serverId, apiClient, directWsUrl, directApiKey, null, null, context, scheduler, clock, transportFactory,
            identityProvider, credentialProvider, false);
    }

    ReSyncFlowClient(String serverId, RemotelyServerApi apiClient, String directWsUrl, String directApiKey,
                     Object ignored, TaskScheduler scheduler, Clock clock,
                     ReSyncFrameTransportFactory transportFactory, ReSyncIdentityProvider identityProvider,
                     ReSyncCredentialProvider credentialProvider, boolean ownsScheduler) {
        this(serverId, apiClient, directWsUrl, directApiKey, null, null, ReSyncFlowClientContext.defaults(), scheduler, clock, transportFactory,
            identityProvider, credentialProvider, ownsScheduler);
    }

    ReSyncFlowClient(String serverId, ReSyncFrameTransport frameTransport, String apiKey, Object ignored,
                     TaskScheduler scheduler, Clock clock, ReSyncIdentityProvider identityProvider,
                     ReSyncCredentialProvider credentialProvider, boolean ownsScheduler) {
        this(serverId, null, null, null, frameTransport, apiKey, ReSyncFlowClientContext.defaults(), scheduler, clock, null, identityProvider,
            credentialProvider, ownsScheduler);
    }

    private ReSyncFlowClient(String serverId, RemotelyServerApi apiClient, String directWsUrl, String directApiKey,
                             ReSyncFrameTransport suppliedTransport, String suppliedTransportApiKey, ReSyncFlowClientContext context,
                             TaskScheduler scheduler, Clock clock, ReSyncFrameTransportFactory transportFactory,
                             ReSyncIdentityProvider identityProvider, ReSyncCredentialProvider credentialProvider,
                             boolean ownsScheduler) {
        this.serverId = serverId;
        this.apiClient = apiClient;
        this.directWsUrl = directWsUrl;
        this.directApiKey = directApiKey;
        ReSyncFlowClientContext resolvedContext = context == null ? ReSyncFlowClientContext.defaults() : context;
        this.state = resolvedContext.state();
        this.caches = resolvedContext.caches();
        this.nodeRegistry = resolvedContext.nodeRegistry();
        this.luckPermsProvider = resolvedContext.luckPermsProvider();
        this.nodeRegistryCache = caches.nodeRegistry();
        this.suppliedTransport = suppliedTransport;
        this.suppliedTransportApiKey = suppliedTransportApiKey;
        this.transportFactory = transportFactory;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownsScheduler = ownsScheduler;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identityProvider = Objects.requireNonNull(identityProvider, "identityProvider");
        this.credentialProvider = Objects.requireNonNull(credentialProvider, "credentialProvider");
        this.activeTransport.set(suppliedTransport);
        applyCredential(suppliedTransport != null ? resolveCredential("", suppliedTransportApiKey) : ReSyncCredential.apiKey(null));
        this.worldGenProtocolHandler = new WorldGenProtocolHandler(serverId, this::trackGenericJob, resolvedContext.worldGeneration());
        String resolvedClientId = identityProvider.clientId(serverId);
        this.stableClientId = resolvedClientId == null || resolvedClientId.isBlank() ? "remotely-" + (serverId == null ? "default" : serverId) : resolvedClientId;
        this.collaboration = new ReSyncCollaborationClient(stableClientId);
        bindCollaborationChannel();
        this.workspaces = new ReSyncWorkspaceClient(null, collaboration);
        bindWorkspaceChannel();
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            pendingOpenResources.put(type, BrowserSafeState.set());
        }
        loadCachedRegistry();
    }

    public void setErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }

    public void setConnectionListener(Runnable listener) {
        connectionListener = listener != null ? listener : () -> {};
    }

    public void setReadyListener(Runnable listener) {
        readyListener = listener != null ? listener : () -> {};
    }

    public void setDisconnectListener(Runnable listener) {
        disconnectListener = listener != null ? listener : () -> {};
    }

    public void addProtocolErrorListener(Consumer<String> listener) {
        if (listener != null) {
            protocolErrorListeners.add(listener);
        }
    }

    public void removeProtocolErrorListener(Consumer<String> listener) {
        if (listener != null) {
            protocolErrorListeners.remove(listener);
        }
    }

    public ReadinessState readinessState() {
        ReadinessState state = readinessState.get();
        return state == null ? ReadinessState.DISCONNECTED : state;
    }

    public boolean isReady() {
        return readinessState() == ReadinessState.READY && isTransportConnected();
    }

    public boolean isIncompatible() {
        return readinessState() == ReadinessState.INCOMPATIBLE;
    }

    public String readinessFailureMessage() {
        return readinessFailureMessage;
    }

    public Async<ReadinessState> awaitReady(Duration timeout) {
        return awaitReady(timeout, true);
    }

    Async<ReadinessState> awaitReady(Duration timeout, boolean initiate) {
        ReadinessState current = readinessState();
        if (current == ReadinessState.INCOMPATIBLE || shutdownRequested || current == ReadinessState.READY && isTransportConnected()) {
            return Async.completed(current == ReadinessState.INCOMPATIBLE || current == ReadinessState.READY
                ? current : ReadinessState.DISCONNECTED);
        }
        Async<ReadinessState> result = Async.pending();
        readinessWaiters.add(result);
        result.onCancel(() -> readinessWaiters.remove(result));
        Duration resolvedTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
            ? Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS) : timeout;
        schedule(() -> completeReadinessWaiter(result, readinessState()), resolvedTimeout);
        if (initiate) {
            connectAsync().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    completeReadinessWaiter(result, readinessState());
                } else if (isReady()) {
                    completeReadinessWaiter(result, ReadinessState.READY);
                }
            });
        }
        return result;
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
        if (!isTransportConnected() || !channelIds.containsKey(channelId)) {
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
        pluginChannelListeners.computeIfAbsent(channelId, ignored -> BrowserSafeState.set()).add(listener);
    }

    public void removePluginChannelListener(String channelId, PluginChannelListener listener) {
        Set<PluginChannelListener> listeners = pluginChannelListeners.get(channelId);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public boolean isPluginChannelAvailable(String channelId) {
        return isReady() && isPluginChannel(channelId) && channelIds.containsKey(channelId) && availablePluginChannels.contains(channelId);
    }

    @SuppressWarnings("unchecked")
    public <T> T luckPerms() {
        Object value = state.luckPerms(this);
        return (T) (value == null ? luckPermsProvider.get(this) : value);
    }

    public String getServerId() {
        return serverId;
    }

    public TaskScheduler scheduler() {
        return scheduler;
    }

    public ReSyncCollaborationClient collaboration() {
        return collaboration;
    }

    private void bindCollaborationChannel() {
        collaboration.bind(new CollaborationService.Channel() {
            @Override
            public boolean available() {
                return isReady();
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
                request.add("patches", workspacePatches(patches));
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

    public Async<Void> connect() {
        return connectAsync(true);
    }

    public Async<ConnectionState> awaitConnected(Duration timeout) {
        return awaitConnected(timeout, true);
    }

    Async<ConnectionState> awaitConnected(Duration timeout, boolean initiate) {
        if (isTransportConnected()) return Async.completed(ConnectionState.CONNECTED);
        if (shutdownRequested) return Async.completed(ConnectionState.DISCONNECTED);
        Async<ConnectionState> result = Async.pending();
        connectionStateWaiters.add(result);
        result.onCancel(() -> connectionStateWaiters.remove(result));
        Duration resolvedTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
            ? Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)
            : timeout;
        schedule(() -> completeConnectionWaiter(result, connectionState()), resolvedTimeout);
        if (initiate) {
            connectAsync().whenComplete((ignored, failure) -> {
                if (failure != null) completeConnectionWaiter(result, ConnectionState.DISCONNECTED);
                else if (isTransportConnected()) completeConnectionWaiter(result, ConnectionState.CONNECTED);
            });
        }
        return result;
    }

    public Async<Void> connectAsync() {
        return connectAsync(false);
    }

    private Async<Void> connectAsync(boolean force) {
        if (shutdownRequested) {
            return Async.completed(null);
        }
        if (terminalIncompatible && !force) {
            return Async.completed(null);
        }
        if (suppliedTransport != null && !suppliedTransport.reconnectable() && hasTerminalConnectionFailure() && !force) {
            return Async.completed(null);
        }
        if (suppliedTransport != null) {
            return connectFrameTransportAsync(force);
        }
        int generation = beginConnectionAttempt(force, true);
        if (generation < 0) {
            return Async.completed(null);
        }
        nodeRegistrySynced = false;
        lastFullNodeRegistryRequestAt = 0L;
        scheduleConnectTimeout(generation);
        logger().operation("Connect").info("Connecting to ReSync");

        if (directWsUrl != null && !directWsUrl.isBlank()) {
            applyCredential(resolveCredential(normalizeWsUrl(directWsUrl), directApiKey));
            if (!credentialUsable()) {
                logger().operation("Connect").warn("Direct ReSync credentials are unavailable");
                settleConnectionAttempt(ConnectionFailure.ACCESS_DENIED, "ReSync API Key Missing");
                notifyConnectionError("ReSyncApiKeyMissing");
                return Async.completed(null);
            }
            logger().operation("Connect").with("endpoint", directWsUrl).info("Using direct ReSync endpoint");
            initWebSocketConnection(normalizeWsUrl(directWsUrl), generation);
            return Async.completed(null);
        }

        if (apiClient == null) {
            settleConnectionAttempt(ConnectionFailure.ENDPOINT_UNREACHABLE, "ReSync API Is Unavailable");
            notifyConnectionError("ReSync API Is Unavailable");
            return Async.completed(null);
        }

        return apiClient.getReSyncConfig(serverId).thenCompose(config -> {
            if (!isActiveGeneration(generation)) {
                return Async.completed(null);
            }
            if (config != null && config.port > 0) {
                logger().operation("Discover Endpoint").with("port", config.port).debug("ReSync configuration found");

                return apiClient.getServers().thenCompose(servers -> {
                    if (!isActiveGeneration(generation)) {
                        return Async.completed(null);
                    }
                    String serverUrl = servers.stream()
                        .filter(this::matchesCanonicalServer)
                        .findFirst()
                        .map(s -> {
                            String ip = (s.ipAlias != null && !s.ipAlias.isEmpty()) ? s.ipAlias : s.ip;
                            logger().operation("Discover Endpoint").with("name", s.name).with("ip", ip).with("ipAlias", s.ipAlias).debug("ReSync server found");
                            return ip + ":" + config.port;
                        })
                        .orElse(null);

                    if (serverUrl == null) {
                        logger().operation("Discover Endpoint").warn("ReSync server was not found");
                        settleConnectionAttempt(ConnectionFailure.ENDPOINT_UNREACHABLE, "ReSync Server Not Found");
                        notifyConnectionError("ReSyncServerNotFound");
                        return Async.completed(null);
                    }
                    return apiClient.getReSyncApiKey(serverId).thenAccept(key -> {
                        if (!isActiveGeneration(generation)) {
                            return;
                        }
                        String wsUrl = normalizeWsUrl(serverUrl);
                        applyCredential(resolveCredential(wsUrl, key));
                        if (credentialUsable()) {
                            logger().operation("Connect").with("endpoint", wsUrl).info("Connecting to ReSync WebSocket");
                            initWebSocketConnection(wsUrl, generation);
                        } else {
                            logger().operation("Connect").warn("ReSync credentials are unavailable");
                            settleConnectionAttempt(ConnectionFailure.ACCESS_DENIED, "ReSync API Key Missing");
                            notifyConnectionError("ReSyncApiKeyMissing");
                        }
                    });
                });
            } else {
                logger().operation("Discover Endpoint").warn("ReSync is not enabled on this server");
                settleConnectionAttempt(ConnectionFailure.HANDSHAKE_REJECTED, "ReSync Is Not Enabled");
                notifyConnectionError("ReSyncNotEnabled");
                return Async.completed(null);
            }
        }).exceptionally(e -> {
            if (!isActiveGeneration(generation)) {
                return null;
            }
            settleConnectionAttempt(ConnectionFailure.ENDPOINT_UNREACHABLE,
                "ReSync Connection Failed. Check That The Server Is Online And ReSync Is Enabled");
            if (notifyConnectionError("ReSync Connection Failed. Check That The Server Is Online And ReSync Is Enabled")) {
                logger().operation("Connect").error("Could not connect to ReSync", e);
            }
            scheduleReconnect();
            return null;
        });
    }

    private int beginConnectionAttempt(boolean force, boolean closeUnauthenticatedTransport) {
        ReSyncFrameTransport transportToClose = null;
        int generation;
        synchronized (connectionLock) {
            if (shutdownRequested || isTransportConnected() || connecting.get()) {
                return -1;
            }
            if (terminalIncompatible && !force) {
                return -1;
            }
            if (!force && hasPendingReconnectLocked()) {
                return -1;
            }
            if (closeUnauthenticatedTransport) {
                ReSyncFrameTransport existingTransport = activeTransport.get();
                if (existingTransport != null && existingTransport.isOpen() && !authenticated.get()) {
                    activeTransport.compareAndSet(existingTransport, null);
                    transportToClose = existingTransport;
                }
            }
            if (!connecting.compareAndSet(false, true)) {
                return -1;
            }
            if (force) {
                cancelReconnectLocked();
                terminalIncompatible = false;
                readinessFailureMessage = "";
                notifiedConnectionError.set(null);
            }
            connectionFailure.set(ConnectionFailure.NONE);
            flowContractCompatible = false;
            negotiatedFlowCapabilities.clear();
            readinessState.set(ReadinessState.CONNECTING);
            nodeRegistrySynced = false;
            lastFullNodeRegistryRequestAt = 0L;
            generation = connectionGeneration.incrementAndGet();
        }
        if (transportToClose != null) {
            transportToClose.close();
        }
        return generation;
    }

    private void initWebSocketConnection(String wsUrl, int generation) {
        ReSyncFrameTransport transport = null;
        try {
            logger().operation("Connect").with("endpoint", wsUrl).debug("Opening ReSync WebSocket");
            if (transportFactory == null) {
                throw new IllegalStateException("ReSync transport factory is unavailable");
            }
            transport = transportFactory.create(wsUrl);
            if (transport == null) {
                throw new IllegalStateException("ReSync transport factory returned no transport");
            }
            ReSyncFrameTransport previous = activeTransport.getAndSet(transport);
            if (previous != null && previous != transport) {
                previous.close();
            }
            bindTransport(transport, generation);
            transport.connect();
        } catch (Exception e) {
            if (transport != null) {
                synchronized (connectionLock) {
                    boolean currentGeneration = isActiveGeneration(generation);
                    if (currentGeneration ? activeTransport.compareAndSet(transport, null) : activeTransport.get() != transport) {
                        try {
                            transport.close();
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            }
            if (!isActiveGeneration(generation)) {
                return;
            }
            boolean firstFailure = notifyConnectionError(connectionFailureMessage(ConnectionFailure.ENDPOINT_UNREACHABLE));
            if (firstFailure) {
                logger().operation("Connect").error("Could not open ReSync WebSocket", e);
            } else {
                logger().operation("Connect").debug("ReSync WebSocket remains unavailable");
            }
            settleConnectionAttempt(ConnectionFailure.ENDPOINT_UNREACHABLE,
                connectionFailureMessage(ConnectionFailure.ENDPOINT_UNREACHABLE));
            if (suppliedTransport == null || suppliedTransport.reconnectable()) {
                scheduleReconnect();
            }
        }
    }

    private void bindTransport(ReSyncFrameTransport transport, int generation) {
        transport.setFrameHandler(data -> {
            if (isCurrentTransport(transport, generation)) {
                handleBinaryMessage(data);
            }
        });
        transport.setOpenHandler(() -> handleTransportOpen(transport, generation));
        transport.setCloseReasonHandler(reason -> handleTransportClosed(transport, generation, reason));
        transport.setCloseHandler(() -> handleTransportClosed(transport, generation, ""));
        transport.setErrorHandler(error -> handleTransportError(transport, generation, error));
        if (transport.isOpen()) {
            handleTransportOpen(transport, generation);
        }
    }

    private void handleTransportOpen(ReSyncFrameTransport transport, int generation) {
        if (!isCurrentTransport(transport, generation)) {
            transport.close();
            return;
        }
        logger().operation("Connect").info("ReSync WebSocket opened");
        connectionFailure.set(ConnectionFailure.NONE);
        logger().operation("Handshake").debug("Sending handshake");
        sendHandshake();
    }

    private void handleTransportClosed(ReSyncFrameTransport transport, int generation, String closeReason) {
        if (!isCurrentTransport(transport, generation)) {
            return;
        }
        activeTransport.compareAndSet(transport, null);
        boolean wasAuthenticated = authenticated.get();
        ConnectionFailure handshakeFailure = !wasAuthenticated
            ? classifyTransportCloseFailure(closeReason) : ConnectionFailure.NONE;
        if (!wasAuthenticated && isTerminalHandshakeFailure(handshakeFailure)) {
            transitionToTerminalFailure(connectionFailureMessage(handshakeFailure), handshakeFailure, false);
            return;
        }
        clearConnectionState("Disconnected", true);
        if (!wasAuthenticated) {
            setConnectionFailure(handshakeFailure);
            readinessFailureMessage = connectionFailureMessage(handshakeFailure);
            if (notifyConnectionError(connectionFailureMessage(handshakeFailure))) {
                logger().operation("Connect").warn("ReSync WebSocket closed");
            } else {
                logger().operation("Connect").debug("ReSync WebSocket remains closed");
            }
        } else {
            logger().operation("Connect").warn("ReSync WebSocket closed");
        }
        if (shouldReconnect(transport)) {
            scheduleReconnect();
        }
    }

    private void handleTransportError(ReSyncFrameTransport transport, int generation, Throwable error) {
        if (!isCurrentTransport(transport, generation)) {
            return;
        }
        activeTransport.compareAndSet(transport, null);
        clearConnectionState("Connection Failed", true);
        setConnectionFailure(ConnectionFailure.ENDPOINT_UNREACHABLE);
        if (notifyConnectionError(connectionFailureMessage(ConnectionFailure.ENDPOINT_UNREACHABLE))) {
            logger().operation("Connect").error("ReSync WebSocket failed", error);
        } else {
            logger().operation("Connect").debug("ReSync WebSocket remains unavailable");
        }
        transport.close();
        if (shouldReconnect(transport)) {
            scheduleReconnect();
        }
    }

    private boolean shouldReconnect(ReSyncFrameTransport transport) {
        return !terminalIncompatible && (suppliedTransport == null || transport.reconnectable());
    }

    private void clearConnectionState(String reason, boolean notifyDisconnect) {
        clearConnectionState(reason, notifyDisconnect, true);
    }

    private void clearConnectionState(String reason, boolean notifyDisconnect, boolean completeReadiness) {
        readinessState.set(ReadinessState.DISCONNECTED);
        authenticated.set(false);
        transportAuthenticated = false;
        flowContractCompatible = false;
        negotiatedFlowCapabilities.clear();
        disconnectCollaboration(reason);
        notifyPluginChannelsUnavailable();
        connecting.set(false);
        playerTrackingSubscribed = false;
        playerControlCapabilities = null;
        if (notifyDisconnect) {
            disconnectListener.run();
        }
        failPlayerControlRequests("ReSync Disconnected");
        cancelConnectTimeout();
        nodeRegistrySynced = false;
        cancelNodeRegistryTimeout();
        caches.optionCatalogs().clearRequestsInFlight(serverId);
        caches.optionCatalogs().markServerStale(serverId);
        stopHeartbeat();
        completeConnectionWaiters(ConnectionState.DISCONNECTED);
        if (completeReadiness) {
            completeReadinessWaiters(ReadinessState.DISCONNECTED);
        }
    }

    private void settleConnectionAttempt(ConnectionFailure failure, String message) {
        ConnectionFailure resolvedFailure = failure == null || failure == ConnectionFailure.NONE
            ? ConnectionFailure.HANDSHAKE_REJECTED : failure;
        String resolvedMessage = message == null || message.isBlank()
            ? connectionFailureMessage(resolvedFailure) : message;
        connecting.set(false);
        cancelConnectTimeout();
        setConnectionFailure(resolvedFailure);
        readinessFailureMessage = resolvedMessage;
        readinessState.set(ReadinessState.DISCONNECTED);
        completeConnectionWaiters(ConnectionState.DISCONNECTED);
        completeReadinessWaiters(ReadinessState.DISCONNECTED);
    }

    private void markReady() {
        if (!authenticated.get() || !flowContractCompatible || !hasRequiredFlowCapabilities()
            || !nodeRegistrySynced && !cachedRegistryValid || terminalIncompatible) {
            return;
        }
        if (!readinessState.compareAndSet(ReadinessState.WAITING_FOR_REGISTRY, ReadinessState.READY)) {
            return;
        }
        readinessFailureMessage = "";
        cancelNodeRegistryTimeout();
        caches.optionCatalogs().markServerStale(serverId);
        startHeartbeat();
        synchronized (resourceListRequestLock) {
            runStartupStep("plugin availability", this::notifyPluginChannelsAvailable);
            runStartupStep("collaboration", collaboration::connectionReady);
            runStartupStep("workspaces", workspaces::connect);
        }
        runStartupStep("job snapshots", this::requestJobSnapshots);
        flushPendingResourceListRequests();
        flushPendingSends();
        runStartupStep("connection listener", connectionListener);
        runStartupStep("ready listener", readyListener);
        completeReadinessWaiters(ReadinessState.READY);
    }

    private boolean hasRequiredFlowCapabilities() {
        return negotiatedFlowCapabilities.containsAll(REQUIRED_FLOW_CONTRACT_CAPABILITIES);
    }

    private String registryContractVersionMismatch(int version, int minimumClientVersion) {
        return "ReSync Flow Registry Version Mismatch. Server Contract " + version + ", Minimum Supported "
            + NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION + ", Server Minimum Client " + minimumClientVersion
            + ". Update ReSync And Remotely";
    }

    private String flowContractVersionMismatch(int version, int minimumClientVersion) {
        return "ReSync Flow Contract Version Mismatch. Server Contract " + version + ", Minimum Supported "
            + NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION + ", Server Minimum Client " + minimumClientVersion
            + ". Update ReSync And Remotely";
    }

    private void transitionToIncompatible(String message) {
        transitionToTerminalFailure(message, ConnectionFailure.PROTOCOL_MISMATCH, true);
    }

    private void transitionToTerminalFailure(String message, ConnectionFailure failure, boolean notifyProtocol) {
        String reason = message == null || message.isBlank() ? connectionFailureMessage(failure) : message;
        synchronized (connectionLock) {
            if (shutdownRequested || terminalIncompatible) {
                return;
            }
            terminalIncompatible = true;
            cancelReconnectLocked();
            connectionGeneration.incrementAndGet();
        }
        ReSyncFrameTransport transport = activeTransport.getAndSet(null);
        boolean notifyDisconnect = authenticated.get() || connecting.get();
        clearConnectionState("Flow Contract Incompatible", notifyDisconnect, false);
        setConnectionFailure(failure);
        readinessFailureMessage = reason;
        readinessState.set(ReadinessState.INCOMPATIBLE);
        cancelPendingNormalWork(reason);
        completeReadinessWaiters(ReadinessState.INCOMPATIBLE);
        if (notifyProtocol) {
            notifyProtocolError(reason);
        }
        notifyConnectionError(reason);
        if (transport != null) {
            try {
                transport.close();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void cancelPendingNormalWork(String reason) {
        synchronized (pendingSendsLock) {
            pendingSends.clear();
        }
        pendingResourceListRequests.clear();
        pendingOpenResources.values().forEach(Set::clear);
        pendingOptionCatalogRequests.clear();
        pendingResourceDeletes.clear();
        placeholderPreviewCallbacks.clear();
        functionTestCallbacks.clear();
        watchedPlayers.clear();
        collaboration.clear();
        workspaces.clear();
        caches.optionCatalogs().clearRequestsInFlight(serverId);
        ReSyncSaveTarget failedSave = state.failAnySave(serverId, "", reason);
        if (failedSave != null && failedSave.shouldUpdateResourceState()) {
            state.onResourceStateFailed(serverId, failedSave.type(), failedSave.id());
        }
        state.onWorldGenerationSaveFailed(serverId, "", reason);
        failPlayerControlRequests(reason);
    }

    private void sendHandshake() {
        String clientId = stableClientId;
        logger().operation("Handshake").with("clientId", clientId).debug("ReSync client identified");
        ReSyncHandshakeRequest request = new ReSyncHandshakeRequest(
            apiKey,
            clientId,
            PROTOCOL_VERSION,
            ReSyncClientVersion.resolve(identityProvider.clientVersion()),
            FlowJson.write(FlowJson.value(FLOW_CONTRACT_CAPABILITIES)),
            collaborationProfile()
        );
        sendFrame(ReSyncProtocolContract.MESSAGE_HANDSHAKE_REQUEST, handshakeCodec.encodeRequest(request), CONTROL_CHANNEL_ID);
    }

    private String collaborationProfile() {
        CollaborationService.Identity identity = collaborationIdentity();
        collaboration.identify(identity);
        return FlowJson.write(collaborationIdentity(identity));
    }

    private CollaborationService.Identity collaborationIdentity() {
        return identityProvider.collaborationIdentity(stableClientId);
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

    private Async<Void> connectFrameTransportAsync(boolean force) {
        ReSyncFrameTransport transport = suppliedTransport;
        if (transport == null) {
            settleConnectionAttempt(ConnectionFailure.ENDPOINT_UNREACHABLE, "ReSync Transport Is Unavailable");
            notifyConnectionError("ReSyncUnavailable");
            return Async.completed(null);
        }
        if (!transport.isOpen() && !transport.reconnectable() && transport.state() != ReSyncFrameTransport.State.NEW) {
            settleConnectionAttempt(connectionFailure() == ConnectionFailure.NONE
                ? ConnectionFailure.ENDPOINT_UNREACHABLE : connectionFailure(), connectionFailureMessage(connectionFailure()));
            notifyConnectionError(connectionFailureMessage(connectionFailure()));
            return Async.completed(null);
        }
        int generation = beginConnectionAttempt(force, false);
        if (generation < 0) {
            return Async.completed(null);
        }
        activeTransport.set(transport);
        scheduleConnectTimeout(generation);
        applyCredential(resolveCredential("", suppliedTransportApiKey));
        try {
            bindTransport(transport, generation);
            if (!transport.isOpen()) {
                transport.connect();
            }
        } catch (RuntimeException exception) {
            if (isCurrentTransport(transport, generation)) {
                handleTransportError(transport, generation, exception);
            }
        }
        return Async.completed(null);
    }

    private void sendSubscribe(String channelId) {
        sendSubscribe(channelId, "");
    }

    private void sendSubscribe(String channelId, String data) {
        if (!isTransportConnected()) {
            return;
        }
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
        ReSyncFrameTransport transport = activeTransport.get();
        if (transport == null || !transport.isOpen()) {
            return;
        }
        synchronized (outboundLock) {
            if (activeTransport.get() != transport || !transport.isOpen()) {
                return;
            }
            try {
                transport.send(frameCodec.encode(messageType, payload, channel, sequenceCounter++));
            } catch (RuntimeException exception) {
                handleTransportError(transport, connectionGeneration.get(), exception);
            }
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
                case ReSyncProtocolContract.MESSAGE_CHANNEL_REGISTRY:
                    handleChannelRegistry(frame.payload());
                    break;
                default:
                    protocolError("Unknown message type: " + (frame.messageType() & 0xFF));
            }
        } catch (IllegalArgumentException exception) {
            protocolError(exception.getMessage());
        } catch (Exception e) {
            logger().operation("Process Message").error("Could not process ReSync message", e);
            if (!authenticated.get()) {
                failConnectionAttempt(connectionFailureMessage(ConnectionFailure.HANDSHAKE_REJECTED), ConnectionFailure.HANDSHAKE_REJECTED);
            }
        }
    }

    private Set<Short> validDataChannels() {
        Set<Short> channels = BrowserSafeState.set();
        channels.add(numericChannel("flow", FLOW_CHANNEL_ID));
        channels.add(numericChannel("player_tracking", PLAYER_TRACKING_CHANNEL_ID));
        channels.add(numericChannel("world_management", WORLD_MANAGEMENT_CHANNEL_ID));
        channels.add(numericChannel("worldgen", WORLDGEN_CHANNEL_ID));
        channels.addAll(channelIds.values());
        return channels;
    }

    private void protocolError(String message) {
        String reason = message == null || message.isBlank() ? "Unknown protocol error" : message;
        logger().operation("Protocol").with("protocolError", reason).error("ReSync protocol error");
        notifyProtocolError(reason);
        if (!authenticated.get()) {
            ConnectionFailure failure = classifyConnectionFailure(reason);
            failConnectionAttempt(connectionFailureMessage(failure), failure);
            return;
        }
        if (errorListener != null) {
            errorListener.onError(null, "ReSyncProtocolError: " + reason);
        }
    }

    private void handleHandshakeResponse(byte[] payload) {
        ReSyncHandshakeResponse response = handshakeCodec.decodeResponse(payload);
        if (!response.success()) {
            ConnectionFailure failure = classifyConnectionFailure(response.message());
            String failureMessage = connectionFailureMessage(failure);
            if (notifyConnectionError(failureMessage)) {
                logger().operation("Handshake").with("reason", response.message()).error("ReSync server rejected the connection");
            } else {
                logger().operation("Handshake").debug("ReSync server continues rejecting the connection");
            }
            failConnectionAttempt(failureMessage, failure);
            return;
        }
        int protocolVersion = response.serverProtocolVersion();
        logger().operation("Handshake").with("protocolVersion", protocolVersion).debug("ReSync protocol negotiated");
        if (protocolVersion != PROTOCOL_VERSION) {
            failConnectionAttempt("ReSync Protocol Mismatch. Update ReSync And Remotely", ConnectionFailure.PROTOCOL_MISMATCH);
            return;
        }
        logger().operation("Handshake").with("worldCount", response.worlds().size()).debug("ReSync worlds received");
        response.channels().forEach((channel, numericId) -> registerChannel(channel, (short) numericId.intValue()));
        JsonObject capabilities = response.capabilitiesJson().isBlank()
            ? null : FlowJson.parse(response.capabilitiesJson()).getAsJsonObject();
        if (!validateNegotiatedFlowCapabilities(capabilities)) {
            return;
        }
        state.onServerCapabilities(serverId, capabilities);
        try {
            notifyDurabilityHealth(capabilities);
        } catch (RuntimeException exception) {
            logger().operation("Handshake").with("reason", exception.getMessage()).warn("Could not display storage health");
        }

        authenticated.set(true);
        notifiedConnectionError.set(null);
        connecting.set(false);
        reconnectAttempt.set(0);
        synchronized (connectionLock) {
            cancelReconnectLocked();
        }
        cancelConnectTimeout();
        readinessState.set(ReadinessState.WAITING_FOR_REGISTRY);
        completeConnectionWaiters(ConnectionState.CONNECTED);
        synchronized (resourceListRequestLock) {
            runStartupStep("startup subscriptions", this::subscribeStartupChannels);
            runStartupStep("plugin subscriptions", this::subscribePluginChannels);
        }
        logger().operation("Handshake").info("ReSync client authenticated");
        requestNodeRegistry(true);
        if (cachedRegistryValid) {
            markReady();
        }
    }

    private void runStartupStep(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            logger().operation("Handshake").with("step", name).with("reason", exception.getMessage()).warn("ReSync startup step failed");
        }
    }

    private boolean validateNegotiatedFlowCapabilities(JsonObject capabilities) {
        if (capabilities == null || !capabilities.has("flowContract") || !capabilities.get("flowContract").isJsonObject()) {
            transitionToIncompatible("ReSync Flow Registry Contract Missing. Update ReSync And Remotely");
            return false;
        }
        JsonObject contract = capabilities.getAsJsonObject("flowContract");
        int version = contract.has("version") ? contract.get("version").getAsInt() : 0;
        int minimumClientVersion = contract.has("minimumClientVersion") ? contract.get("minimumClientVersion").getAsInt() : 0;
        if (version < NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION
            || version > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || minimumClientVersion > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION) {
            transitionToIncompatible(flowContractVersionMismatch(version, minimumClientVersion));
            return false;
        }
        Set<String> negotiated = BrowserSafeState.set();
        if (contract.has("negotiated") && contract.get("negotiated").isJsonArray()) {
            for (JsonElement element : contract.getAsJsonArray("negotiated")) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    negotiated.add(element.getAsString());
                }
            }
        }
        if (!negotiated.containsAll(REQUIRED_FLOW_CONTRACT_CAPABILITIES)) {
            Set<String> missing = BrowserSafeState.set();
            missing.addAll(REQUIRED_FLOW_CONTRACT_CAPABILITIES);
            missing.removeAll(negotiated);
            transitionToIncompatible("ReSync Flow Registry Capabilities Missing: " + String.join(", ", missing.stream().sorted().toList())
                + ". Update ReSync And Remotely");
            return false;
        }
        negotiatedFlowCapabilities.clear();
        negotiatedFlowCapabilities.addAll(negotiated);
        flowContractCompatible = true;
        return true;
    }

    private void notifyDurabilityHealth(JsonObject capabilities) {
        if (capabilities == null || !capabilities.has("durabilityHealth") || !capabilities.get("durabilityHealth").isJsonObject()) {
            return;
        }
        JsonObject health = capabilities.getAsJsonObject("durabilityHealth");
        String fingerprint = FlowJson.write(health);
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
        ReSyncNotificationLevel type = "CRITICAL".equals(status) ? ReSyncNotificationLevel.ERROR
            : "DEGRADED".equals(status) ? ReSyncNotificationLevel.WARN : ReSyncNotificationLevel.INFO;
        String title = "HEALTHY".equals(status) ? "Storage Recovered" : "Storage Needs Attention";
        String message = durabilityMessage(health, issues, recovered);
        state.onNotification(title, message, type);
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
        JsonObject root = FlowJson.parse(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root == null) {
            return;
        }
        boolean snapshot = root.has("snapshot") && root.get("snapshot").getAsBoolean();
        Set<String> previousPluginChannels = BrowserSafeState.set();
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
        if (!isTransportConnected()) {
            return;
        }
        for (String channelId : new ArrayList<>(pluginChannelSubscriptions)) {
            if (isPluginChannel(channelId) && channelIds.containsKey(channelId)) {
                sendSubscribe(channelId);
            }
        }
    }

    private void notifyPluginChannelsAvailable() {
        if (!isReady()) {
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

    private boolean isCurrentTransport(ReSyncFrameTransport transport, int generation) {
        return isActiveGeneration(generation) && activeTransport.get() == transport;
    }

    private void scheduleConnectTimeout(int generation) {
        cancelConnectTimeout();
        connectTimeoutTask = schedule(() -> {
            if (!isActiveGeneration(generation) || authenticated.get()) {
                return;
            }
            ReSyncFrameTransport transport = activeTransport.getAndSet(null);
            clearConnectionState("Connection Timed Out", true);
            if (transport != null) {
                transport.close();
            }
            connectionGeneration.compareAndSet(generation, generation + 1);
            if (transport == null ? suppliedTransport == null : shouldReconnect(transport)) {
                scheduleReconnect();
            }
            setConnectionFailure(ConnectionFailure.ENDPOINT_UNREACHABLE);
            String timeoutMessage = connectionFailureMessage(ConnectionFailure.ENDPOINT_UNREACHABLE);
            ReSyncSaveTarget failedSave = state.failAnySave(serverId, "", timeoutMessage);
            if (failedSave != null) {
                if (failedSave.shouldUpdateResourceState()) {
                    state.onResourceStateFailed(serverId, failedSave.type(), failedSave.id());
                }
                return;
            }
            state.onWorldGenerationSaveFailed(serverId, "", timeoutMessage);
            notifyConnectionError(timeoutMessage);
        }, Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));
    }

    private void cancelConnectTimeout() {
        if (connectTimeoutTask != null) {
            scheduledTasks.remove(connectTimeoutTask);
            connectTimeoutTask.cancel();
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
            case ReSyncProtocolContract.FLOW_PACKET_RESOURCE_ACTIVATION_RESULT:
                handleResourceActivationResult(buffer);
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
            JsonObject envelope = FlowJson.parse(json).getAsJsonObject();
            String type = envelope != null && envelope.has("type") ? envelope.get("type").getAsString() : "";
            if ("player_control_capabilities".equals(type)) {
                playerControlCapabilities = envelope;
                return;
            }
            if ("player_control_response".equals(type)) {
                String requestId = envelope.has("requestId") ? envelope.get("requestId").getAsString() : "";
                Async<JsonObject> pending = pendingPlayerControlRequests.remove(requestId);
                if (pending != null) pending.complete(envelope);
                return;
            }
            PlayerTrackingUpdate update = PlayerTrackingJson.read(FlowJson.parse(json).getAsJsonObject());
            if (update == null) {
                return;
            }
            state.onPlayerTrackingUpdate(serverId, update);
        } catch (Exception e) {
            logger().operation("Player Tracking").error("Could not read player tracking update", e);
        }
    }

    private void handleWorldManagementMessage(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            JsonObject encoded = FlowJson.parse(json).getAsJsonObject();
            WorldChannelMessage message = new WorldChannelMessage(FlowJson.string(encoded, "type", ""), FlowJson.string(encoded, "action", ""),
                FlowJson.bool(encoded, "success", false), FlowJson.string(encoded, "message", ""), encoded.get("data"),
                FlowJson.longValue(encoded, "timestamp", 0));
            if (message == null) {
                return;
            }
            trackWorldJob(message);
            state.onWorldManagementMessage(serverId, message);
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
        JsonObject envelope = FlowJson.parse(json).getAsJsonObject();
        if (envelope != null && envelope.has("data")) {
            trackJobElement(envelope.get("data"));
        } else {
            trackGenericJob(envelope);
        }
    }

    private void handleTraceSnapshot(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        state.onTraceSnapshot(serverId, json);
    }

    private void handleTraceEvent(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        state.onTraceEvent(serverId, json);
    }

    private void handleDebugSnapshot(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        state.onDebugSnapshot(serverId, json);
    }

    private void handleMessageLogPage(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        try {
            JsonObject page = FlowJson.parse(json).getAsJsonObject();
            if (page != null) {
                state.onMessageLogPage(serverId, page);
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
            ReSyncSaveTarget failedSave = state.failSaveRequest(serverId, requestId, message);
            if (failedSave != null) {
                if (failedSave.shouldUpdateResourceState()) {
                    state.onResourceStateFailed(serverId, failedSave.type(), failedSave.id());
                }
                return;
            }
            if (isWorldGenProjectSaveAction(action) && state.onWorldGenerationSaveFailed(serverId, requestId, message)) {
                return;
            }
            if (state.consumeRecentError(serverId, message)) {
                return;
            }
            String title = action == null || action.isBlank() ? "ReSync Failed" : action + " Failed";
            state.onNotification(title, message, ReSyncNotificationLevel.ERROR);
        }
    }

    private List<Map<String, Object>> parseAttributeValidationErrors(String reason) {
        if (reason == null || !reason.contains("ATTRIBUTE_VALIDATION:")) {
            return List.of();
        }
        String json = reason.substring(reason.indexOf("ATTRIBUTE_VALIDATION:") + "ATTRIBUTE_VALIDATION:".length());
        try {
            JsonElement root = FlowJson.parse(json);
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
        String component = FlowJson.text(first.getOrDefault("component", "")).trim();
        String message = FlowJson.text(first.getOrDefault("message", "")).trim();
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
            JsonElement parsed = FlowJson.parse(raw.substring(start, end + 1));
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
        if (key == null) {
            return;
        }
        ReSyncResourceType type = ReSyncResourceType.byTypeId(key.type());
        if (type != null) {
            state.onResourceDeleted(serverId, type, key.id());
        }
    }

    private void failResourceDelete(String requestId, String message) {
        ReSyncResourceKey key = pendingResourceDeletes.remove(requestId);
        if (key == null) {
            return;
        }
        ReSyncResourceType type = ReSyncResourceType.byTypeId(key.type());
        if (type != null) {
            state.onResourceSaveFailed(serverId, type, key.id(), message);
        }
    }

    private void handlePresenceSnapshot(ByteBuffer buffer) {
        if (collaboration.applySnapshot(readRemainingJson(buffer))) {
            state.onNodeRegistryUpdated(serverId);
        }
    }

    private void handleResourceEvent(ByteBuffer buffer, boolean deleted) {
        ResourceEvent event;
        try {
            event = resourceEvent(FlowJson.parse(readRemainingJson(buffer)).getAsJsonObject());
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
        if (type == null) {
            return;
        }
        if (deleted) {
            state.onResourceDeleted(serverId, type, event.resourceId());
            requestResourceList(type);
            return;
        }
        try {
            Object item = type.deserialize(event.payload());
            state.onResourceData(serverId, type, item, false);
            state.onNodeRegistryUpdated(serverId);
        } catch (RuntimeException exception) {
            requestResource(type, event.resourceId(), false);
        }
    }

    private void handleResourceActivationResult(ByteBuffer buffer) {
        ResourceActivationResult result;
        try {
            result = resourceActivation(FlowJson.parse(readRemainingJson(buffer)).getAsJsonObject());
        } catch (RuntimeException exception) {
            protocolError("Invalid resource update result");
            return;
        }
        ReSyncResourceType type = result != null ? ReSyncResourceType.byTypeId(result.type()) : null;
        if (result == null || type == null) {
            return;
        }
        state.onResourceActivation(serverId, type, result.resourceId(), result.enabled(), result.requestId(), result.success(), result.message(),
            result.editorError());
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
        Integer color = state.collaborationColorOverride();
        if (color != null) {
            presence.addProperty("color", color);
        }
        byte[] json = FlowJson.write(presence).getBytes(StandardCharsets.UTF_8);
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
        byte[] json = FlowJson.write(request).getBytes(StandardCharsets.UTF_8);
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
        byte[] json = FlowJson.write(payload).getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(1 + json.length);
        packet.put(packetId);
        packet.put(json);
        sendFrame(4, packet.array(), numericChannel("flow", FLOW_CHANNEL_ID));
        return true;
    }

    private record ResourceEvent(String type, String resourceId, String payload, String authorSessionId,
                                 ReSyncCollaborationClient.Identity author, long changedAt) {
    }

    private record ResourceActivationResult(boolean success, String type, String resourceId, boolean enabled, String requestId, String message,
                                            boolean editorError) {
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
        state.onResourceData(serverId, type, item, item != null && pendingOpenResources.get(type).remove(type.extractId(item)));
    }

    private void handleQuickEditOpen(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        JsonObject root = FlowJson.parse(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root == null || !root.has("definition") || !root.get("definition").isJsonObject()) {
            return;
        }
        String sessionId = root.has("sessionId") && !root.get("sessionId").isJsonNull() ? root.get("sessionId").getAsString() : "";
        CustomContentDefinition definition = root.get("definition").isJsonObject() ? FlowJson.customContent(root.getAsJsonObject("definition")) : null;
        if (definition == null) {
            return;
        }
        state.onQuickEditOpen(serverId, sessionId, definition);
    }

    private void handleQuickEditResult(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        JsonObject root = FlowJson.parse(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        String status = root != null && root.has("status") && !root.get("status").isJsonNull() ? root.get("status").getAsString() : "";
        if ("applied".equals(status)) {
            state.onQuickEditResult(serverId, true, "Applied");
        } else if ("failed".equals(status)) {
            String message = root.has("message") && !root.get("message").isJsonNull() ? root.get("message").getAsString() : "Apply Failed";
            state.onQuickEditResult(serverId, false, message);
        }
    }

    private void handleOpenCustomContent(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        JsonObject root = FlowJson.parse(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root == null || !root.has("content") || !root.get("content").isJsonObject()) {
            return;
        }
        CustomContentDefinition content = root.get("content").isJsonObject() ? FlowJson.customContent(root.getAsJsonObject("content")) : null;
        if (content == null || content.getId() == null || content.getId().isBlank()) {
            return;
        }
        state.onOpenCustomContent(serverId, content);
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
        state.onGuiState(serverId, editable, guiId, flowId);
    }

    private void handleEditTargetState(ByteBuffer buffer) {
        boolean editable = buffer.get() == 1;
        String resourceType = readSizedString(buffer);
        String resourceId = readSizedString(buffer);
        String flowId = readSizedString(buffer);
        state.onEditTargetState(serverId, editable, resourceType, resourceId, flowId);
    }

    private void handleFlowError(ByteBuffer buffer) {
        int messageLen = buffer.getInt();
        if (messageLen < 0 || messageLen > buffer.remaining()) return;

        byte[] messageBytes = new byte[messageLen];
        buffer.get(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);

        logger().with("flowError", message).error("ReSync flow error");
        EditorError editorError = ReSyncEditorDiagnostics.parse(message);
        List<Map<String, Object>> attributeErrors = editorError == null ? parseAttributeValidationErrors(message) : List.of();
        if (editorError != null) {
            message = ReSyncEditorDiagnostics.summary(editorError);
        } else if (!attributeErrors.isEmpty()) {
            message = summarizeAttributeValidationErrors(attributeErrors);
        } else {
            message = formatFlowDiagnostics(message);
        }

        String finalMessage = message;
        ReSyncSaveTarget failedSave = editorError != null
            ? state.failAnySave(serverId, ReSyncEditorDiagnostics.title(editorError), finalMessage)
            : state.failAnySave(serverId, "", finalMessage);
        if (failedSave != null) {
            if (failedSave.shouldUpdateResourceState()) {
                state.onResourceStateFailed(serverId, failedSave.type(), failedSave.id());
            }
            state.onFlowError(serverId, editorError, attributeErrors, finalMessage);
            return;
        }
        if (state.consumeRecentError(serverId, finalMessage)) {
            return;
        }
        state.onFlowError(serverId, editorError, attributeErrors, finalMessage);
        String finalTitle = editorError != null ? ReSyncEditorDiagnostics.title(editorError) : "Flow Save Failed";
        state.onNotification(finalTitle, finalMessage, ReSyncNotificationLevel.ERROR);
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

        boolean automaticNotificationSuppressed = state.consumeAutomaticNotificationSuppression(requestId);
        ReSyncSaveTarget completedSave = state.completeSave(serverId, type, id, requestId);
        boolean completedNotification = completedSave != null;
        boolean showNotification = !automaticNotificationSuppressed && !completedNotification && shouldShowSaveNotification(type, id);
        if (showNotification) {
            state.onNotification(type.displayName() + " Saved", "ID: " + id, ReSyncNotificationLevel.SUCCESS);
        }

        boolean markSaved = completedSave == null || completedSave.shouldUpdateResourceState();
        if (markSaved) {
            state.onResourceSaveSucceeded(serverId, type, id, completedSave != null ? completedSave.sequence() : 0L, revision, hash);
        }
    }

    private boolean shouldShowSaveNotification(ReSyncResourceType type, String id) {
        if (type == ReSyncResourceType.PROJECT_METADATA) {
            return false;
        }
        return !isBackingFlowAck(type, id);
    }

    private boolean isBackingFlowAck(ReSyncResourceType type, String id) {
        return state.isBackingFlowAcknowledgement(serverId, type, id);
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

        state.onResourceList(serverId, type, ids);
    }

    private void handleNodeRegistrySnapshot(ByteBuffer buffer, boolean fullSync) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        try {
            NodeRegistrySnapshot snapshot = NodeRegistrySnapshotJson.read(json);
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
            NodeRegistry registry = nodeRegistry;
            if (registry != null) {
                if (!registry.applySnapshot(serverId, snapshot)) {
                    logger().operation("Node Registry").warn("Active registry rejected the snapshot; requesting a full snapshot");
                    requestNodeRegistry(true);
                    return;
                }
                caches.tombstones().replace(serverId, registry.getUnresolvedPluginPayloads(serverId));
                nodeRegistryCache.applySnapshot(serverId, registry.materializeSnapshot(serverId, snapshot));
            } else if (snapshot.isFullSync()) {
                nodeRegistryCache.applySnapshot(serverId, snapshot);
            } else {
                requestNodeRegistry(true);
                return;
            }
            nodeRegistrySynced = true;
            usingCachedRegistry = false;
            cachedRegistryValid = false;
            if (snapshot.isFullSync()) {
                lastFullNodeRegistryRequestAt = 0L;
            }
            cancelNodeRegistryTimeout();
            notifyNodeRegistryUpdated();
            markReady();
        } catch (Exception e) {
            logger().operation("Node Registry").error("Could not read node registry snapshot", e);
        }
    }

    private boolean compatibleRegistrySnapshot(NodeRegistrySnapshot snapshot) {
        int version = snapshot.getContractVersion();
        if (version < NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION) {
            transitionToIncompatible(registryContractVersionMismatch(version, snapshot.getMinimumClientContractVersion()));
            return false;
        }
        if (version > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || snapshot.getMinimumClientContractVersion() > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION) {
            transitionToIncompatible(registryContractVersionMismatch(version, snapshot.getMinimumClientContractVersion()));
            return false;
        }
        if (!snapshot.getServerIdentity().isBlank() && !serverId.equals(snapshot.getServerIdentity())) {
            transitionToIncompatible("ReSync Node Registry Server Mismatch. Reconnect The Correct Server");
            return false;
        }
        if (snapshot.getCompatibleUntil() > 0 && snapshot.getCompatibleUntil() < clock.millis()) {
            transitionToIncompatible("ReSync Node Registry Snapshot Expired. Update ReSync And Remotely");
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
            callback.accept(rendered);
        }
    }

    private void handleOptionCatalog(ByteBuffer buffer) {
        byte[] jsonBytes = new byte[buffer.remaining()];
        buffer.get(jsonBytes);
        try {
            OptionCatalogSnapshot payload = FlowJson.optionCatalog(FlowJson.parse(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject());
            if (payload != null && !payload.getSourceId().isBlank()) {
                if (payload.getVersion() > OptionCatalogSnapshot.CURRENT_VERSION) {
                    protocolError("Unsupported option catalog version: " + payload.getVersion());
                    return;
                }
                String contextKey = payload.getContextKey();
                String requestKey = optionCatalogRequestKey(payload.getSourceId(), contextKey);
                OptionCatalogCache cache = caches.optionCatalogs();
                pendingOptionCatalogRequests.remove(requestKey);
                boolean changed = cache.put(serverId, payload.getSourceId(), contextKey, payload.getRevision(), payload.getSequence(), payload.getValues(), payload.getItems(), payload.getStatus(), payload.getDiagnostic());
                if (!changed) {
                    return;
                }
                state.onOptionCatalog(serverId, payload);
            }
        } catch (Exception e) {
            logger().operation("Option Catalog").error("Could not read option catalog", e);
        }
    }

    public void requestNodeRegistry() {
        requestNodeRegistry(false);
    }

    private void requestNodeRegistry(boolean fullSync) {
        if (!isTransportConnected()) {
            queuePendingSend(() -> requestNodeRegistry(fullSync));
            ensureConnected();
            return;
        }
        long now = clock.millis();
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
        String json = FlowJson.write(FlowJson.nodeRegistryRequest(request));
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
        NodeRegistry registry = nodeRegistry;
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
        return FlowJson.write(FlowJson.value(new TreeMap<>(context)));
    }

    public void requestOptionCatalog(String sourceId, Map<String, Object> context, boolean forceRefresh) {
        if (sourceId == null || sourceId.isBlank()) {
            return;
        }
        Map<String, Object> normalizedContext = context != null && !context.isEmpty() ? new TreeMap<>(context) : Map.of();
        String contextKey = optionCatalogContextKey(normalizedContext);
        String requestKey = optionCatalogRequestKey(sourceId, contextKey);
        OptionCatalogCache cache = caches.optionCatalogs();
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
                if (!queuePendingSend(() -> {
                    pendingOptionCatalogRequests.remove(requestKey);
                    requestOptionCatalog(sourceId, normalizedContext, forceRefresh);
                })) {
                    pendingOptionCatalogRequests.remove(requestKey);
                }
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
        byte[] requestBytes = FlowJson.write(FlowJson.value(Map.of("version", 2, "contextKey", contextKey, "context", context))).getBytes(StandardCharsets.UTF_8);
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
            queuePendingSend(() -> requestMessageLog(page, pageSize, query, source));
            ensureConnected();
            return;
        }
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("page", Math.max(0, page));
        request.put("pageSize", Math.clamp(pageSize <= 0 ? 20 : pageSize, 1, 100));
        request.put("query", query != null ? query : "");
        request.put("source", source != null ? source : "");
        JsonObject encoded = FlowJson.value(request).getAsJsonObject();
        byte[] jsonBytes = FlowJson.write(encoded).getBytes(StandardCharsets.UTF_8);
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
        List<NodePluginPayload> tombstones = caches.tombstones().get(serverId);
        cachedRegistryValid = false;
        usingCachedRegistry = false;
        if (cached == null && tombstones.isEmpty()) {
            return;
        }
        NodeRegistry registry = nodeRegistry;
        boolean restored = cached != null && cachedRegistryCompatible(cached);
        if (registry != null && cached != null && restored) {
            restored = registry.applySnapshot(serverId, cached);
        }
        if (registry != null) {
            registry.restoreUnresolvedPlugins(serverId, tombstones);
        }
        cachedRegistryValid = restored;
        usingCachedRegistry = restored;
    }

    private boolean cachedRegistryCompatible(NodeRegistrySnapshot snapshot) {
        if (snapshot == null || snapshot.getContractVersion() < NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION
            || snapshot.getContractVersion() > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || snapshot.getMinimumClientContractVersion() > NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION
            || !snapshot.getServerIdentity().isBlank() && !serverId.equals(snapshot.getServerIdentity())
            || snapshot.getCompatibleUntil() > 0 && snapshot.getCompatibleUntil() < clock.millis()) {
            return false;
        }
        return snapshot.isFullSync();
    }

    private void notifyNodeRegistryUpdated() {
        state.onNodeRegistryUpdated(serverId);
    }

    private void scheduleNodeRegistryTimeout() {
        cancelNodeRegistryTimeout();
        if (!usingCachedRegistry) {
            return;
        }
        nodeRegistryTimeout = schedule(() -> {
            if (!nodeRegistrySynced && usingCachedRegistry) {
                showCachedRegistryNotice();
            }
        }, Duration.ofSeconds(NODE_REGISTRY_TIMEOUT_SECONDS));
    }

    private void cancelNodeRegistryTimeout() {
        if (nodeRegistryTimeout != null) {
            scheduledTasks.remove(nodeRegistryTimeout);
            nodeRegistryTimeout.cancel();
            nodeRegistryTimeout = null;
        }
    }

    private void showCachedRegistryNotice() {
        state.onNotification("Flow Nodes", "Using cached node definitions for " + serverId, ReSyncNotificationLevel.WARN);
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
            queuePendingSend(() -> sendResourceRequest(type, id));
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
            queuePendingSend(() -> sendDebugCommand(command));
            ensureConnected();
            return;
        }
        byte[] jsonBytes = FlowJson.write(FlowJson.value(command)).getBytes(StandardCharsets.UTF_8);
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
            request.put("graph", FlowJson.parse(FlowSerializer.serialize(graph)));
        }
        request.put("name", name != null && !name.isBlank() ? name : "Fixture");
        request.put("inputs", inputs != null ? inputs : Map.of());
        request.put("expectedOutputs", expectedOutputs != null ? expectedOutputs : Map.of());
        request.put("serverContext", serverContext != null ? serverContext : Map.of());
        request.put("clockInstant", clockInstant != null ? clockInstant : "");
        request.put("zoneId", zoneId != null && !zoneId.isBlank() ? zoneId : "UTC");
        request.put("timeoutMillis", Math.clamp(timeoutMillis, 1L, 30000L));
        byte[] jsonBytes = FlowJson.write(FlowJson.value(request)).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_FUNCTION_TEST_REQUEST);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
        schedule(() -> {
            Consumer<JsonObject> pending = functionTestCallbacks.remove(requestId);
            if (pending != null) {
                pending.accept(errorResult("FUNCTION_TEST_TIMEOUT", "Function test timed out"));
            }
        }, Duration.ofSeconds(35L));
    }

    private void handleFunctionTestResult(ByteBuffer buffer) {
        String json = readRemainingJson(buffer);
        JsonObject result = FlowJson.parse(json).getAsJsonObject();
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

    public Async<JsonObject> requestPlayerControl(String action, UUID playerId, Map<String, Object> payload) {
        if (!isConnected()) return Async.failed(new IllegalStateException("ReSync Unavailable"));
        String requestId = serverId + '-' + playerControlSequence.incrementAndGet();
        JsonObject request = payload == null ? new JsonObject() : FlowJson.value(payload).getAsJsonObject();
        request.addProperty("type", "player_control");
        request.addProperty("version", 2);
        request.addProperty("requestId", requestId);
        request.addProperty("action", action);
        if (playerId != null) request.addProperty("playerId", playerId.toString());
        Async<JsonObject> result = Async.pending();
        synchronized (pendingPlayerControlRequests) {
            if (!isConnected()) return Async.failed(new IllegalStateException("ReSync Unavailable"));
            pendingPlayerControlRequests.put(requestId, result);
        }
        try {
            schedule(() -> {
                Async<JsonObject> pending = pendingPlayerControlRequests.remove(requestId);
                if (pending != null) pending.fail(new IllegalStateException("ReSync Player Request Timed Out"));
            }, Duration.ofSeconds(5L));
            sendFrame(4, FlowJson.write(request).getBytes(StandardCharsets.UTF_8), numericChannel("player_tracking", PLAYER_TRACKING_CHANNEL_ID));
        } catch (Exception exception) {
            pendingPlayerControlRequests.remove(requestId);
            result.fail(exception);
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
        if (!isTransportConnected()) {
            return;
        }
        PlayerTrackingRequest request = new PlayerTrackingRequest();
        request.action = action;
        request.playerId = playerId != null ? playerId.toString() : null;
        byte[] jsonBytes = FlowJson.write(FlowJson.value(request)).getBytes(StandardCharsets.UTF_8);
        sendFrame(4, jsonBytes, numericChannel("player_tracking", PLAYER_TRACKING_CHANNEL_ID));
    }

    private void sendWorldRequest(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        attachRequestId(request);
        if (!isConnected()) {
            queuePendingSend(() -> sendWorldRequest(request));
            ensureConnected();
            return;
        }
        byte[] jsonBytes = FlowJson.write(FlowJson.value(request)).getBytes(StandardCharsets.UTF_8);
        sendFrame(4, jsonBytes, numericChannel("world_management", WORLD_MANAGEMENT_CHANNEL_ID));
    }

    private void attachRequestId(Map<String, Object> request) {
        Object actionValue = request.get("action");
        String action = actionValue instanceof String value ? value : "";
        if (action.isBlank() || isReadOnlyWorldAction(action) || request.containsKey("requestId")) {
            return;
        }
        request.put("requestId", stableClientId + ":" + action + ":" + UUID.randomUUID().toString());
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
        state.attachSaveRequest(serverId, type, id, requestId);
        if (!isConnected()) {
            logger().operation("Save Flow").with("flowType", type.displayName()).warn("ReSync is disconnected; save queued");
            if (!queuePendingSend(() -> sendResourceSave(type, item, requestId))) {
                ReSyncSaveTarget failedSave = state.failResourceSave(serverId, type, id,
                    "ReSync Pending Operation Queue Full");
                if (failedSave != null && failedSave.shouldUpdateResourceState()) {
                    state.onResourceStateFailed(serverId, failedSave.type(), failedSave.id());
                }
            }
            ensureConnected();
            return;
        }
        String json;
        try {
            json = type.serialize(item);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank() ? "Save Failed" : e.getMessage();
            ReSyncSaveTarget failedSave = state.failResourceSave(serverId, type, id, message);
            if (failedSave != null) {
                if (failedSave.shouldUpdateResourceState()) {
                    state.onResourceStateFailed(serverId, failedSave.type(), failedSave.id());
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

    void sendResourceActivation(ReSyncResourceType type, String resourceId, boolean enabled, String requestId) {
        if (type == null || resourceId == null || resourceId.isBlank() || requestId == null || requestId.isBlank()) {
            return;
        }
        if (!isConnected()) {
            state.onResourceActivation(serverId, type, resourceId, enabled, requestId, false,
                "ReSync disconnected before the resource could be updated.", true);
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("type", type.typeId());
        request.addProperty("resourceId", resourceId);
        request.addProperty("enabled", enabled);
        request.addProperty("requestId", requestId);
        byte[] json = FlowJson.write(request).getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(1 + json.length);
        packet.put(ReSyncProtocolContract.FLOW_PACKET_RESOURCE_ACTIVATION);
        packet.put(json);
        sendFrame(4, packet.array(), numericChannel("flow", FLOW_CHANNEL_ID));
        schedule(() -> {
            state.onResourceActivation(serverId, type, resourceId, enabled, requestId, false,
                "ReSync did not confirm the update. Try again.", true);
        }, Duration.ofSeconds(15L));
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
            if (!queuePendingSend(() -> sendResourceDelete(type, id, payload, requestId))) {
                pendingResourceDeletes.remove(requestId);
            }
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
            queuePendingSend(() -> sendQuickEditApply(sessionId, definition));
            ensureConnected();
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("sessionId", sessionId);
        root.add("definition", FlowJson.customContent(definition));
        byte[] jsonBytes = FlowJson.write(root).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + jsonBytes.length);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_QUICK_EDIT_APPLY);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }
    public void sendFlowDelete(String flowId) {
        FlowGraph graph = state.graph(serverId, ReSyncResourceType.FLOW, flowId);
        if (graph != null && graph.getResourceRevision() > 0L && supportsFlowCapability("resource_revisions")) {
            JsonObject payload = new JsonObject();
            payload.addProperty("id", flowId);
            payload.addProperty("expectedRevision", graph.getResourceRevision());
            sendResourceDelete(ReSyncResourceType.FLOW, flowId, FlowJson.write(payload));
            return;
        }
        sendResourceDelete(ReSyncResourceType.FLOW, flowId);
    }
    public void sendGuiDelete(String guiId) { sendResourceDelete(ReSyncResourceType.GUI, guiId); }
    public void sendScoreboardDelete(String scoreboardId) { sendResourceDelete(ReSyncResourceType.SCOREBOARD, scoreboardId); }
    public void sendTabDelete(String tabId) { sendResourceDelete(ReSyncResourceType.TAB, tabId); }

    public boolean supportsFlowCapability(String capability) {
        JsonObject capabilities = state.serverCapabilities(serverId);
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
        sendWorldGenMutationJson((byte) 0x31, "worldGenPreviewApply", previewId, FlowJson.write(FlowJson.value(payload)));
    }

    public void sendWorldGenPreviewStop(String previewId) {
        if (previewId == null || previewId.isBlank()) {
            return;
        }
        sendWorldGenMutationJson((byte) 0x22, "worldGenPreviewStop", previewId, FlowJson.write(FlowJson.value(Map.of("previewId", previewId))));
    }

    public void requestWorldGenRegistry() {
        sendWorldGenJson((byte) 0x24, FlowJson.write(FlowJson.value(Map.of("pluginChecksums", Map.of()))));
    }

    private void sendWorldGenJson(byte packetId, String json) {
        if (!isConnected()) {
            queuePendingSend(() -> sendWorldGenJson(packetId, json));
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
        String requestId = mutationRequestId(action, target);
        sendWorldGenMutationJson(packetId, json, requestId);
    }

    private void sendWorldGenMutationJson(byte packetId, String json, String requestId) {
        if (!isConnected()) {
            queuePendingSend(() -> sendWorldGenMutationJson(packetId, json, requestId));
            ensureConnected();
            return;
        }
        byte[] requestIdBytes = requestId.getBytes(StandardCharsets.UTF_8);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + jsonBytes.length);
        buffer.put(packetId);
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("worldgen", WORLDGEN_CHANNEL_ID));
    }

    public void sendTriggerUpdate(List<TriggerBinding> bindings) {
        JsonArray encodedBindings = new JsonArray();
        if (bindings != null) bindings.forEach(binding -> {
            JsonObject value = new JsonObject(); value.addProperty("id", binding.getId()); value.addProperty("flowId", binding.getFlowId());
            if (binding.getType() == null) value.add("type", JsonNull.INSTANCE); else value.addProperty("type", binding.getType().name());
            value.addProperty("context", binding.getContext()); encodedBindings.add(value);
        });
        String json = FlowJson.write(encodedBindings);
        String requestId = mutationRequestId("triggerUpdate", serverId);
        sendTriggerUpdate(json, requestId);
    }

    private void sendTriggerUpdate(String json, String requestId) {
        if (!isConnected()) {
            logger().operation("Update Trigger").warn("ReSync is disconnected; trigger update queued");
            queuePendingSend(() -> sendTriggerUpdate(json, requestId));
            ensureConnected();
            return;
        }

        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] requestIdBytes = requestId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + requestIdBytes.length + jsonBytes.length);
        buffer.put(ReSyncProtocolContract.FLOW_PACKET_TRIGGER_UPDATE);
        buffer.putInt(requestIdBytes.length);
        buffer.put(requestIdBytes);
        buffer.put(jsonBytes);
        sendFrame(4, buffer.array(), numericChannel("flow", FLOW_CHANNEL_ID));
    }

    private String mutationRequestId(String action, String target) {
        return stableClientId + ":" + action + ":" + (target != null ? target : "") + ":" + UUID.randomUUID().toString();
    }

    private boolean isWorldGenProjectSaveAction(String action) {
        return "saveWorldGenProject".equals(action) || "worldGenProjectSave".equals(action);
    }

    private void startHeartbeat() {
        stopHeartbeat();
        if (shutdownRequested) {
            return;
        }
        heartbeatTask = scheduleAtFixedRate(() -> {
            if (shutdownRequested) {
                return;
            }
            if (!isConnected()) {
                return;
            }
            sendHeartbeat();
        }, Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS), Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS));
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            scheduledTasks.remove(heartbeatTask);
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    private TaskScheduler.ScheduledTask schedule(Runnable action, Duration delay) {
        BrowserSafeState.ReferenceValue<TaskScheduler.ScheduledTask> reference = new BrowserSafeState.ReferenceValue<>();
        TaskScheduler.ScheduledTask task = scheduler.schedule(() -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                logger().operation("Scheduled Task").with("reason", exception.getMessage()).warn("ReSync scheduled task failed");
            } finally {
                TaskScheduler.ScheduledTask scheduled = reference.get();
                if (scheduled != null) {
                    scheduledTasks.remove(scheduled);
                }
            }
        }, delay);
        reference.set(task);
        if (task != null) {
            scheduledTasks.add(task);
        }
        return task;
    }

    private TaskScheduler.ScheduledTask scheduleAtFixedRate(Runnable action, Duration initialDelay, Duration period) {
        TaskScheduler.ScheduledTask task = scheduler.scheduleAtFixedRate(() -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                logger().operation("Scheduled Task").with("reason", exception.getMessage()).warn("ReSync scheduled task failed");
            }
        }, initialDelay, period);
        if (task != null) {
            scheduledTasks.add(task);
        }
        return task;
    }

    private void sendHeartbeat() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(clock.millis());
        sendFrame(5, buffer.array(), CONTROL_CHANNEL_ID);
    }

    private ReSyncCredential resolveCredential(String endpoint, String suppliedCredential) {
        ReSyncCredential credential = credentialProvider.resolve(serverId, endpoint, suppliedCredential);
        return credential == null ? ReSyncCredential.apiKey(null) : credential;
    }

    private JsonArray workspacePatches(List<WorkspacePatch<JsonElement>> patches) {
        JsonArray encoded = new JsonArray();
        if (patches != null) patches.forEach(patch -> {
            JsonObject value = new JsonObject(); value.addProperty("op", patch.op()); value.addProperty("path", patch.path());
            value.add("value", patch.value() == null ? JsonNull.INSTANCE : patch.value().deepCopy()); encoded.add(value);
        });
        return encoded;
    }

    private JsonObject collaborationIdentity(CollaborationService.Identity identity) {
        JsonObject value = new JsonObject();
        if (identity == null) return value;
        value.addProperty("subjectId", identity.subjectId()); value.addProperty("displayName", identity.displayName());
        value.addProperty("avatar", identity.avatar()); value.addProperty("source", identity.source()); return value;
    }

    private ResourceEvent resourceEvent(JsonObject json) {
        JsonObject author = FlowJson.object(json, "author");
        CollaborationService.Identity identity = author == null ? null : new CollaborationService.Identity(
            FlowJson.string(author, "subjectId", ""), FlowJson.string(author, "displayName", ""),
            FlowJson.string(author, "avatar", ""), FlowJson.string(author, "source", ""));
        return new ResourceEvent(FlowJson.string(json, "type", ""), FlowJson.string(json, "resourceId", ""),
            FlowJson.string(json, "payload", ""), FlowJson.string(json, "authorSessionId", ""), identity,
            FlowJson.longValue(json, "changedAt", 0));
    }

    private ResourceActivationResult resourceActivation(JsonObject json) {
        return new ResourceActivationResult(FlowJson.bool(json, "success", false), FlowJson.string(json, "type", ""),
            FlowJson.string(json, "resourceId", ""), FlowJson.bool(json, "enabled", false),
            FlowJson.string(json, "requestId", ""), FlowJson.string(json, "message", ""),
            FlowJson.bool(json, "editorError", false));
    }

    private void applyCredential(ReSyncCredential credential) {
        apiKey = credential.handshakeValue();
        transportAuthenticated = credential.transportAuthenticated();
    }

    private boolean credentialUsable() {
        return transportAuthenticated || (apiKey != null && !apiKey.isBlank());
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

    private boolean matchesCanonicalServer(ServerModels.ClientServerView server) {
        if (server == null || serverId == null || serverId.isBlank()) {
            return false;
        }
        String identifier = server.identifier == null ? "" : server.identifier.trim();
        if (!identifier.isBlank() && serverId.equalsIgnoreCase(identifier)) {
            return true;
        }
        String uuid = server.uuid == null ? "" : server.uuid.trim();
        return !uuid.isBlank() && serverId.equalsIgnoreCase(uuid);
    }

    private void scheduleReconnect() {
        synchronized (connectionLock) {
            if (shutdownRequested || terminalIncompatible || connecting.get() || hasPendingReconnectLocked()) {
                return;
            }
            int attempt = reconnectAttempt.incrementAndGet();
            int exponent = Math.min(Math.max(attempt - 1, 0), 3);
            long delaySeconds = Math.min(RECONNECT_MAX_DELAY_SECONDS,
                (long) RECONNECT_INITIAL_DELAY_SECONDS * (1L << exponent));
            int retryGeneration = connectionGeneration.get();
            reconnectTask = schedule(() -> {
                synchronized (connectionLock) {
                    if (connectionGeneration.get() != retryGeneration) {
                        return;
                    }
                    reconnectTask = null;
                }
                ensureConnected();
            }, Duration.ofSeconds(delaySeconds));
        }
    }

    private boolean notifyConnectionError(String message) {
        String resolvedMessage = message == null || message.isBlank() ? "ReSync Connection Is Unavailable" : message;
        boolean changed = notifiedConnectionError.compareAndSet(null, resolvedMessage);
        if (changed && errorListener != null) {
            errorListener.onError(null, resolvedMessage);
        }
        return changed;
    }

    private boolean hasPendingReconnectLocked() {
        return reconnectTask != null && !reconnectTask.isCancelled();
    }

    private void cancelReconnectLocked() {
        TaskScheduler.ScheduledTask task = reconnectTask;
        reconnectTask = null;
        if (task != null) {
            scheduledTasks.remove(task);
            task.cancel();
        }
    }

    private void handleError(byte[] payload) {
        if (payload == null || payload.length < Integer.BYTES * 2) {
            protocolError("Invalid ReSync error message");
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int errorCode = buffer.getInt();
        int messageLen = buffer.getInt();
        if (messageLen < 0 || messageLen > buffer.remaining()) {
            protocolError("Invalid ReSync error message length");
            return;
        }
        byte[] messageBytes = new byte[messageLen];
        buffer.get(messageBytes);
        String errorText = new String(messageBytes, StandardCharsets.UTF_8);

        logger().with("errorCode", errorCode).with("errorText", errorText).error("ReSync returned an error");
        notifyProtocolError(errorText);
        if (!authenticated.get()) {
            String message = switch (errorCode) {
                case 400 -> "ReSync Protocol Mismatch. Update ReSync And Remotely";
                case 401 -> "ReSync Access Denied. Check The API Key Or Bridge Permission";
                default -> "ReSync Handshake Rejected. Check The Installed Runtime";
            };
            ConnectionFailure failure = switch (errorCode) {
                case 400 -> ConnectionFailure.PROTOCOL_MISMATCH;
                case 401 -> ConnectionFailure.ACCESS_DENIED;
                default -> ConnectionFailure.HANDSHAKE_REJECTED;
            };
            failConnectionAttempt(message, failure);
        }
    }

    private void failConnectionAttempt(String message) {
        failConnectionAttempt(message, classifyConnectionFailure(message));
    }

    private void failConnectionAttempt(String message, ConnectionFailure failure) {
        ConnectionFailure resolvedFailure = failure == null || failure == ConnectionFailure.NONE
            ? ConnectionFailure.HANDSHAKE_REJECTED : failure;
        if (isTerminalHandshakeFailure(resolvedFailure)) {
            transitionToTerminalFailure(message, resolvedFailure, false);
            return;
        }
        ReSyncFrameTransport transport = activeTransport.getAndSet(null);
        clearConnectionState("Connection Failed", false);
        setConnectionFailure(resolvedFailure);
        readinessFailureMessage = message == null || message.isBlank() ? connectionFailureMessage(resolvedFailure) : message;
        notifyConnectionError(connectionFailureMessage(resolvedFailure));
        connectionGeneration.incrementAndGet();
        if (transport != null) {
            try {
                transport.close();
            } catch (RuntimeException ignored) {
            }
        }
        if (suppliedTransport != null && !suppliedTransport.reconnectable()) {
            return;
        }
        scheduleReconnect();
    }

    private boolean isTerminalHandshakeFailure(ConnectionFailure failure) {
        return switch (failure) {
            case PROTOCOL_MISMATCH, RUNTIME_VERSION_MISMATCH, ACCESS_DENIED, HANDSHAKE_REJECTED -> true;
            default -> false;
        };
    }

    private void setConnectionFailure(ConnectionFailure failure) {
        connectionFailure.set(failure == null ? ConnectionFailure.HANDSHAKE_REJECTED : failure);
    }

    private ConnectionFailure classifyConnectionFailure(String message) {
        String value = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (value.contains("protocol") || value.contains("contract") || value.contains("capabilit") || value.contains("400")) {
            return ConnectionFailure.PROTOCOL_MISMATCH;
        }
        if (value.contains("version") || value.contains("client")) {
            return ConnectionFailure.RUNTIME_VERSION_MISMATCH;
        }
        if (value.contains("access") || value.contains("credential") || value.contains("api key") || value.contains("unauthor")) {
            return ConnectionFailure.ACCESS_DENIED;
        }
        if (value.contains("unreachable") || value.contains("refused") || value.contains("connect") || value.contains("timeout")) {
            return ConnectionFailure.ENDPOINT_UNREACHABLE;
        }
        return ConnectionFailure.HANDSHAKE_REJECTED;
    }

    private ConnectionFailure classifyTransportCloseFailure(String message) {
        String value = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (value.isBlank() || value.contains("unreachable") || value.contains("refused")
                || value.contains("timeout") || value.contains("timed out") || value.contains("network")) {
            return ConnectionFailure.ENDPOINT_UNREACHABLE;
        }
        if (value.contains("protocol") || value.contains("contract") || value.contains("capabilit") || value.contains("400")) {
            return ConnectionFailure.PROTOCOL_MISMATCH;
        }
        if (value.contains("version") || value.contains("client")) {
            return ConnectionFailure.RUNTIME_VERSION_MISMATCH;
        }
        if (value.contains("access") || value.contains("credential") || value.contains("api key")
                || value.contains("unauthor") || value.contains("forbidden") || value.contains("401") || value.contains("403")) {
            return ConnectionFailure.ACCESS_DENIED;
        }
        if (value.contains("handshake") || value.contains("reject") || value.contains("mismatch")) {
            return ConnectionFailure.HANDSHAKE_REJECTED;
        }
        return ConnectionFailure.ENDPOINT_UNREACHABLE;
    }

    private String connectionFailureMessage(ConnectionFailure failure) {
        return switch (failure == null ? ConnectionFailure.HANDSHAKE_REJECTED : failure) {
            case ENDPOINT_UNREACHABLE -> "ReSync Endpoint Unreachable. Check That The Server And ReSync Are Running";
            case PROTOCOL_MISMATCH -> "ReSync Protocol Mismatch. Update ReSync And Remotely";
            case RUNTIME_VERSION_MISMATCH -> "ReSync Version Mismatch. Update ReSync And Remotely";
            case ACCESS_DENIED -> "ReSync Access Denied. Check The API Key Or Bridge Permission";
            case HANDSHAKE_REJECTED -> "ReSync Handshake Rejected. Check The Installed Runtime";
            case NONE -> "ReSync Connection Is Unavailable";
        };
    }

    private void notifyProtocolError(String message) {
        for (Consumer<String> listener : protocolErrorListeners) {
            try {
                listener.accept(message);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public void shutdown() {
        logger().operation("Disconnect").info("Closing ReSync WebSocket");
        shutdownRequested = true;
        terminalIncompatible = false;
        readinessState.set(ReadinessState.DISCONNECTED);
        readinessFailureMessage = "";
        stopHeartbeat();
        state.closeIntegrations(this);
        luckPermsProvider.close(this);
        ReSyncFrameTransport transport = activeTransport.getAndSet(null);
        if (transport != null) {
            transport.close();
        }
        if (authenticated.get() || connecting.get()) {
            disconnectListener.run();
        }
        authenticated.set(false);
        transportAuthenticated = false;
        connecting.set(false);
        completeConnectionWaiters(ConnectionState.DISCONNECTED);
        completeReadinessWaiters(ReadinessState.DISCONNECTED);
        pendingResourceListRequests.clear();
        placeholderPreviewCallbacks.clear();
        functionTestCallbacks.clear();
        failPlayerControlRequests("ReSync Disconnected");
        watchedPlayers.clear();
        playerTrackingSubscribed = false;
        cancelNodeRegistryTimeout();
        cancelConnectTimeout();
        synchronized (connectionLock) {
            cancelReconnectLocked();
        }
        for (TaskScheduler.ScheduledTask task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
        synchronized (pendingSendsLock) {
            pendingSends.clear();
        }
        collaboration.clear();
        workspaces.clear();
        if (ownsScheduler && scheduler instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isConnectedState() {
        return isReady();
    }

    boolean usesFrameTransport(ReSyncFrameTransport transport) {
        return suppliedTransport == transport;
    }

    private void disconnectCollaboration(String reason) {
        collaboration.connectionLost();
        workspaces.disconnect(reason);
    }

    public ConnectionState connectionState() {
        if (isTransportConnected()) {
            return ConnectionState.CONNECTED;
        }
        return connecting.get() ? ConnectionState.CONNECTING : ConnectionState.DISCONNECTED;
    }

    public ConnectionFailure connectionFailure() {
        ConnectionFailure failure = connectionFailure.get();
        return failure == null ? ConnectionFailure.NONE : failure;
    }

    public boolean hasTerminalConnectionFailure() {
        return connectionFailure() != ConnectionFailure.NONE;
    }

    private void completeConnectionWaiters(ConnectionState state) {
        for (Async<ConnectionState> waiter : List.copyOf(connectionStateWaiters)) {
            completeConnectionWaiter(waiter, state);
        }
    }

    private void completeConnectionWaiter(Async<ConnectionState> waiter, ConnectionState state) {
        if (waiter != null && connectionStateWaiters.remove(waiter)) waiter.complete(state);
    }

    private void completeReadinessWaiters(ReadinessState state) {
        for (Async<ReadinessState> waiter : List.copyOf(readinessWaiters)) {
            completeReadinessWaiter(waiter, state);
        }
    }

    private void completeReadinessWaiter(Async<ReadinessState> waiter, ReadinessState state) {
        if (waiter != null && readinessWaiters.remove(waiter)) waiter.complete(state);
    }

    private void failPlayerControlRequests(String reason) {
        synchronized (pendingPlayerControlRequests) {
            IllegalStateException error = new IllegalStateException(reason);
            for (Map.Entry<String, Async<JsonObject>> entry : pendingPlayerControlRequests.entrySet()) {
                if (pendingPlayerControlRequests.remove(entry.getKey(), entry.getValue())) entry.getValue().fail(error);
            }
        }
    }

    public boolean matchesDirectProfile(String wsUrl, String apiKey) {
        if (!Objects.equals(normalizeWsUrl(directWsUrl), normalizeWsUrl(wsUrl))) {
            return false;
        }
        return resolveCredential(normalizeWsUrl(wsUrl), apiKey).transportAuthenticated() || Objects.equals(directApiKey, apiKey);
    }

    private boolean isConnected() {
        return isReady();
    }

    private boolean isTransportConnected() {
        ReSyncFrameTransport transport = activeTransport.get();
        return transport != null && transport.isOpen() && authenticated.get();
    }

    private void ensureConnected() {
        if (suppliedTransport != null) {
            connectAsync();
            return;
        }
        ReSyncFrameTransport transport = activeTransport.get();
        if (transport != null && transport.isOpen() && !authenticated.get()) {
            transport.close();
            activeTransport.compareAndSet(transport, null);
        }
        connectAsync();
    }

    private void flushPendingSends() {
        int flushed = 0;
        while (flushed < MAX_PENDING_SENDS) {
            Runnable pending;
            synchronized (pendingSendsLock) {
                pending = pendingSends.pollFirst();
            }
            if (pending == null) {
                return;
            }
            flushed++;
            try {
                pending.run();
            } catch (RuntimeException exception) {
                logger().operation("Flush Pending Operation").error("Queued ReSync operation failed", exception);
            }
            if (!isConnected()) {
                return;
            }
        }
    }

    private boolean queuePendingSend(Runnable action) {
        if (action == null) {
            return false;
        }
        synchronized (pendingSendsLock) {
            if (pendingSends.size() < MAX_PENDING_SENDS) {
                pendingSends.addLast(action);
                return true;
            }
        }
        notifyConnectionError("ReSync Pending Operation Queue Full");
        return false;
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
