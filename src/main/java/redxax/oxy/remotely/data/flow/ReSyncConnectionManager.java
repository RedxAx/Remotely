package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.util.BrowserSafeState;
import redxax.oxy.remotely.util.TaskSchedulers;

import redxax.oxy.remotely.RemotelyServerApi;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.TaskScheduler;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class ReSyncConnectionManager {
    private static final Duration CONNECTION_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_PENDING_PROFILE_ATTEMPTS = 100;
    private final Object client;
    private final RemotelyServerApi apiClient;
    private final ReSyncFlowClientFactory flowClientFactory;
    private final ReSyncConnectionProfileProvider profileProvider;
    private final ReSyncConnectionNotificationSink notificationSink;
    private final NodeRegistry nodeRegistry;
    private final ReSyncFlowClientContext flowClientContext;
    private final Map<String, ReSyncServerIdentity> identities = BrowserSafeState.map();
    private final Map<String, ReSyncFlowClient> flowClients = BrowserSafeState.map();
    private final Map<String, ReSyncConnectionProfile> flowProfiles = BrowserSafeState.map();
    private Consumer<String> connectionListener = serverId -> {};
    private Consumer<String> disconnectListener = serverId -> {};

    public record ReSyncConnectionProfile(String wsUrl, String apiKey, boolean apiManaged) {
        public ReSyncConnectionProfile(String wsUrl, String apiKey) {
            this(wsUrl, apiKey, false);
        }

        public ReSyncConnectionProfile {
            wsUrl = wsUrl == null ? "" : wsUrl.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
        }

        public static ReSyncConnectionProfile apiManagedProfile() {
            return new ReSyncConnectionProfile("", "", true);
        }
    }

    public ReSyncConnectionManager(Object client, RemotelyServerApi apiClient) {
        this(client, apiClient, ReSyncFlowClientFactory.unavailable(), ReSyncConnectionProfileProvider.unavailable(),
            ReSyncConnectionNotificationSink.noop(), null);
    }

    public ReSyncConnectionManager(Object client, RemotelyServerApi apiClient, ReSyncFlowClientFactory flowClientFactory) {
        this(client, apiClient, flowClientFactory, ReSyncConnectionProfileProvider.unavailable(), ReSyncConnectionNotificationSink.noop(), null);
    }

    public ReSyncConnectionManager(Object client, RemotelyServerApi apiClient, ReSyncFlowClientFactory flowClientFactory,
                                   ReSyncConnectionProfileProvider profileProvider,
                                   ReSyncConnectionNotificationSink notificationSink) {
        this(client, apiClient, flowClientFactory, profileProvider, notificationSink, null);
    }

    public ReSyncConnectionManager(Object client, RemotelyServerApi apiClient, ReSyncFlowClientFactory flowClientFactory,
                                   ReSyncConnectionProfileProvider profileProvider,
                                   ReSyncConnectionNotificationSink notificationSink, NodeRegistry nodeRegistry) {
        this(client, apiClient, flowClientFactory, profileProvider, notificationSink, nodeRegistry, null);
    }

    public ReSyncConnectionManager(Object client, RemotelyServerApi apiClient, ReSyncFlowClientFactory flowClientFactory,
                                   ReSyncConnectionProfileProvider profileProvider,
                                   ReSyncConnectionNotificationSink notificationSink, NodeRegistry nodeRegistry,
                                   ReSyncFlowClientContext flowClientContext) {
        this.client = client;
        this.apiClient = apiClient;
        this.flowClientFactory = flowClientFactory == null ? ReSyncFlowClientFactory.unavailable() : flowClientFactory;
        this.profileProvider = profileProvider == null ? ReSyncConnectionProfileProvider.unavailable() : profileProvider;
        this.notificationSink = notificationSink == null ? ReSyncConnectionNotificationSink.noop() : notificationSink;
        this.nodeRegistry = nodeRegistry;
        this.flowClientContext = flowClientContext;
    }

    public RemotelyServerApi getApiClient() {
        return apiClient;
    }

    public ReSyncFlowClient getFlowClient(String serverId) {
        return getFlowClient(ReSyncServerIdentity.of(serverId));
    }

    public ReSyncFlowClient getFlowClient(ReSyncServerIdentity identity) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        return canonical == null || !canonical.present() ? null : flowClients.get(canonical.serverId());
    }

    public ReSyncConnectionProfile getProfile(String serverId) {
        return getProfile(ReSyncServerIdentity.of(serverId));
    }

    public ReSyncConnectionProfile getProfile(ReSyncServerIdentity identity) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        return canonical == null || !canonical.present() ? null : flowProfiles.get(canonical.serverId());
    }

    public boolean isFlowClientConnected(String serverId) {
        return isFlowClientReady(serverId);
    }

    public boolean isFlowClientReady(String serverId) {
        ReSyncFlowClient flowClient = getFlowClient(serverId);
        return flowClient != null && flowClient.isReady();
    }

    public ReSyncFlowClient.ReadinessState getFlowClientReadiness(String serverId) {
        ReSyncFlowClient flowClient = getFlowClient(serverId);
        return flowClient == null ? ReSyncFlowClient.ReadinessState.DISCONNECTED : flowClient.readinessState();
    }

    public ReSyncFlowClient.ConnectionState getFlowClientConnectionState(String serverId) {
        ReSyncFlowClient flowClient = getFlowClient(serverId);
        return flowClient == null ? ReSyncFlowClient.ConnectionState.DISCONNECTED : flowClient.connectionState();
    }

    public ReSyncFlowClient ensureFlowClient(String serverId, boolean showNotifications) {
        return ensureFlowClient(ReSyncServerIdentity.of(serverId), showNotifications);
    }

    public ReSyncFlowClient ensureFlowClient(ReSyncServerIdentity identity, boolean showNotifications) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        if (canonical == null || !canonical.present()) {
            return null;
        }
        return ensureFlowClient(canonical, profileForLocalInstance(canonical), showNotifications, true);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId, ReSyncConnectionProfile profile) {
        return ensureFlowClient(ReSyncServerIdentity.of(serverId), profile);
    }

    public ReSyncFlowClient ensureFlowClient(ReSyncServerIdentity identity, ReSyncConnectionProfile profile) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        if (canonical == null || !canonical.present()) {
            return null;
        }
        return ensureFlowClient(canonical, profile, true, true);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId) {
        return ensureFlowClient(serverId, true);
    }

    public boolean canUseFlowClient(ReSyncServerIdentity identity) {
        return getFlowAvailabilityIssue(rememberIdentity(identity)) == null;
    }

    public boolean canSurfaceFlowClient(ReSyncServerIdentity identity) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        return canUseFlowClient(canonical) || profileProvider.connectionPending(canonical);
    }

    public boolean canUseFlowClient(String serverId, ClientServerView server) {
        return canUseFlowClient(ReSyncServerIdentity.from(serverId, server));
    }

    public boolean canActivateLiveSession(ReSyncLiveServerSession session) {
        return flowClientFactory.available() && session != null && session.serverId() != null
            && !session.serverId().isBlank() && session.transport() != null;
    }

    public Async<ReSyncFlowClient.ReadinessState> awaitFlowClientConnected(String serverId) {
        return awaitFlowClientConnected(serverId, true);
    }

    public Async<ReSyncFlowClient.ReadinessState> awaitFlowClientConnected(String serverId, boolean showNotifications) {
        ReSyncServerIdentity identity = ReSyncServerIdentity.of(serverId);
        if (!identity.present()) {
            return Async.completed(ReSyncFlowClient.ReadinessState.DISCONNECTED);
        }
        ReSyncFlowClient flowClient = ensureFlowClient(identity, profileForLocalInstance(identity), showNotifications, false);
        if (flowClient == null) {
            return profileProvider.connectionPending(identity)
                ? awaitPendingFlowClient(identity, showNotifications)
                : Async.completed(ReSyncFlowClient.ReadinessState.DISCONNECTED);
        }
        if (flowClient.isReady()) {
            return Async.completed(ReSyncFlowClient.ReadinessState.READY);
        }
        boolean initiate = flowClient.readinessState() == ReSyncFlowClient.ReadinessState.DISCONNECTED;
        Async<ReSyncFlowClient.ReadinessState> result = flowClient.awaitReady(CONNECTION_WAIT_TIMEOUT, false);
        if (initiate) {
            flowClient.connectAsync();
        }
        return result;
    }

    private Async<ReSyncFlowClient.ReadinessState> awaitPendingFlowClient(ReSyncServerIdentity identity,
                                                                            boolean showNotifications) {
        Async<ReSyncFlowClient.ReadinessState> result = Async.pending();
        BrowserSafeState.ReferenceValue<TaskScheduler.ScheduledTask> scheduled = new BrowserSafeState.ReferenceValue<>();
        result.onCancel(() -> {
            TaskScheduler.ScheduledTask task = scheduled.get();
            if (task != null) {
                task.cancel();
            }
        });
        pollPendingFlowClient(identity, showNotifications, result, scheduled, 0);
        return result;
    }

    private void pollPendingFlowClient(ReSyncServerIdentity identity, boolean showNotifications,
                                       Async<ReSyncFlowClient.ReadinessState> result,
                                       BrowserSafeState.ReferenceValue<TaskScheduler.ScheduledTask> scheduled,
                                       int attempt) {
        if (result.isDone()) {
            return;
        }
        ReSyncFlowClient flowClient = ensureFlowClient(identity, profileForLocalInstance(identity), showNotifications, false);
        if (flowClient != null) {
            if (flowClient.isReady()) {
                result.complete(ReSyncFlowClient.ReadinessState.READY);
                return;
            }
            boolean initiate = flowClient.readinessState() == ReSyncFlowClient.ReadinessState.DISCONNECTED;
            Async<ReSyncFlowClient.ReadinessState> readiness = flowClient.awaitReady(CONNECTION_WAIT_TIMEOUT, false);
            readiness.whenComplete((state, failure) -> {
                if (failure != null) {
                    result.complete(ReSyncFlowClient.ReadinessState.DISCONNECTED);
                } else {
                    result.complete(state == null ? ReSyncFlowClient.ReadinessState.DISCONNECTED : state);
                }
            });
            if (initiate) {
                flowClient.connectAsync();
            }
            return;
        }
        if (!profileProvider.connectionPending(identity) || attempt >= MAX_PENDING_PROFILE_ATTEMPTS) {
            result.complete(ReSyncFlowClient.ReadinessState.DISCONNECTED);
            return;
        }
        try {
            TaskScheduler.ScheduledTask task = TaskSchedulers.current().schedule(
                () -> pollPendingFlowClient(identity, showNotifications, result, scheduled, attempt + 1),
                Duration.ofMillis(50));
            scheduled.set(task);
        } catch (RuntimeException error) {
            result.complete(ReSyncFlowClient.ReadinessState.DISCONNECTED);
        }
    }

    public ReSyncFlowClient retryFlowClient(String serverId, boolean showNotifications) {
        ReSyncServerIdentity identity = ReSyncServerIdentity.of(serverId);
        if (!identity.present()) {
            return null;
        }
        ReSyncFlowClient flowClient = ensureFlowClient(identity, profileForLocalInstance(identity), showNotifications, false);
        if (flowClient != null) {
            flowClient.connect();
        }
        return flowClient;
    }

    public ReSyncFlowClient activateLiveSession(ReSyncLiveServerSession session) {
        if (!canActivateLiveSession(session)) {
            return null;
        }
        ReSyncServerIdentity identity = rememberIdentity(ReSyncServerIdentity.of(session.serverId()));
        String serverId = identity.serverId();
        ReSyncFlowClient existing = flowClients.get(serverId);
        if (existing != null && existing.usesFrameTransport(session.transport())) {
            existing.connectAsync();
            return existing;
        }
        existing = flowClients.remove(serverId);
        if (existing != null) {
            existing.shutdown();
        }
        flowProfiles.remove(serverId);
        Object clientState = flowClientContext == null ? client : flowClientContext;
        ReSyncFlowClient flowClient = flowClientFactory.createLive(serverId, session.transport(), clientState);
        if (flowClient == null) {
            return null;
        }
        flowClient.setReadyListener(() -> connectionListener.accept(serverId));
        flowClient.setDisconnectListener(() -> disconnectListener.accept(serverId));
        flowClient.setErrorListener((nodeId, message) -> {
            String normalized = normalizeReSyncNotificationMessage(message);
            if (!"ReSync Connection Timed Out".equals(normalized)) {
                notificationSink.show("ReSync", normalized, ReSyncNotificationLevel.ERROR);
            }
        });
        flowClients.put(serverId, flowClient);
        flowClient.connectAsync();
        return flowClient;
    }

    private ReSyncFlowClient ensureFlowClient(ReSyncServerIdentity identity, ReSyncConnectionProfile profile,
                                               boolean showNotifications, boolean connectIfNeeded) {
        if (identity == null || !identity.present() || !flowClientFactory.available()) {
            return null;
        }
        String serverId = identity.serverId();
        if (profile == null || !profileProvider.connectionAllowed(identity, profile)
                || !profile.apiManaged() && !hasDirectProfile(profile)
                || profile.apiManaged() && apiClient == null) {
            ReSyncFlowClient blocked = flowClients.remove(serverId);
            if (blocked != null) {
                blocked.shutdown();
            }
            flowProfiles.remove(serverId);
            return null;
        }
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null && profile != null && !profile.apiManaged() && !flowClient.isReady()
                && !flowClient.matchesDirectProfile(profile.wsUrl(), profile.apiKey())) {
            flowClient.shutdown();
            flowClients.remove(serverId);
            flowClient = null;
        }
        if (flowClient == null) {
            if (!profile.apiManaged()) {
                Object clientState = flowClientContext == null ? client : flowClientContext;
                flowClient = flowClientFactory.create(serverId, apiClient, profile.wsUrl(), profile.apiKey(), null, clientState);
            } else {
                Object clientState = flowClientContext == null ? client : flowClientContext;
                flowClient = flowClientFactory.create(serverId, apiClient, null, null, null, clientState);
            }
            if (flowClient == null) {
                return null;
            }
            flowClients.put(serverId, flowClient);
        }
        flowClient.setReadyListener(() -> connectionListener.accept(serverId));
        flowClient.setDisconnectListener(() -> disconnectListener.accept(serverId));
        if (showNotifications) {
            flowClient.setErrorListener((nodeId, message) -> {
                String normalized = normalizeReSyncNotificationMessage(message);
                ReSyncNotificationLevel type = "ReSync Connection Timed Out".equals(normalized) ? ReSyncNotificationLevel.WARN : ReSyncNotificationLevel.ERROR;
                notificationSink.show("ReSync", normalized, type);
            });
        } else {
            flowClient.setErrorListener((nodeId, message) -> {
            });
        }
        if (connectIfNeeded) {
            flowClient.connectAsync();
        }
        return flowClient;
    }

    private boolean hasDirectProfile(ReSyncConnectionProfile profile) {
        return profile.wsUrl() != null && !profile.wsUrl().isBlank()
            && (profile.wsUrl().startsWith("ws://") || profile.wsUrl().startsWith("wss://"))
            && profile.apiKey() != null && !profile.apiKey().isBlank();
    }

    public void closeServerConnection(String serverId, Runnable onCacheClear) {
        ReSyncServerIdentity identity = rememberIdentity(ReSyncServerIdentity.of(serverId));
        String canonicalServerId = identity == null ? "" : identity.serverId();
        ReSyncFlowClient flowClient = flowClients.remove(canonicalServerId);
        if (flowClient != null) {
            flowClient.shutdown();
        }
        flowProfiles.remove(canonicalServerId);
        identities.remove(canonicalServerId);
        if (nodeRegistry != null) {
            nodeRegistry.clearServer(canonicalServerId);
        }
        if (onCacheClear != null) {
            onCacheClear.run();
        }
    }

    public void disconnectServerConnection(String serverId) {
        ReSyncServerIdentity identity = rememberIdentity(ReSyncServerIdentity.of(serverId));
        String canonicalServerId = identity == null ? "" : identity.serverId();
        if (canonicalServerId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = flowClients.remove(canonicalServerId);
        if (flowClient != null) {
            flowClient.setDisconnectListener(() -> {});
            flowClient.shutdown();
        }
        flowProfiles.remove(canonicalServerId);
    }

    public void setDisconnectListener(Consumer<String> listener) {
        disconnectListener = listener != null ? listener : serverId -> {};
        flowClients.forEach((serverId, flowClient) -> flowClient.setDisconnectListener(() -> disconnectListener.accept(serverId)));
    }

    public void setConnectionListener(Consumer<String> listener) {
        connectionListener = listener != null ? listener : serverId -> {};
        flowClients.forEach((serverId, flowClient) -> flowClient.setReadyListener(() -> connectionListener.accept(serverId)));
    }

    public void shutdownAll() {
        List<ReSyncFlowClient> clients = new ArrayList<>(flowClients.values());
        flowClients.clear();
        flowProfiles.clear();
        identities.clear();
        for (ReSyncFlowClient flowClient : clients) {
            flowClient.shutdown();
        }
    }

    public void resolveAndStoreProfile(ReSyncServerIdentity identity) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        if (canonical == null || !canonical.present()) {
            return;
        }
        ReSyncConnectionProfile profile = resolveConnectionProfile(canonical);
        if (profile != null) {
            flowProfiles.put(canonical.serverId(), profile);
        } else {
            flowProfiles.remove(canonical.serverId());
        }
    }

    public void resolveAndStoreProfile(String serverId, ClientServerView server) {
        resolveAndStoreProfile(ReSyncServerIdentity.from(serverId, server));
    }

    public ReSyncConnectionProfile resolveConnectionProfile(ReSyncServerIdentity identity) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        return canonical == null || !canonical.present() ? null : profileProvider.resolve(canonical);
    }

    public ReSyncConnectionProfile resolveConnectionProfile(String serverId, ClientServerView server) {
        return resolveConnectionProfile(ReSyncServerIdentity.from(serverId, server));
    }

    public String getFlowAvailabilityIssue(ReSyncServerIdentity identity) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        if (canonical == null || !canonical.present()) {
            return "ServerIdMissing";
        }
        if (!flowClientFactory.available()) {
            return "ReSyncUnavailable";
        }
        ReSyncConnectionProfile profile = getOrResolveProfile(canonical);
        if (!profileProvider.connectionAllowed(canonical, profile)) {
            return "ReSyncUnavailable";
        }
        if (profile == null) {
            return profileProvider.hasInstanceAccess() && profileProvider.findInstance(canonical) == null
                ? "ServerNotFound" : "ReSyncNotConfigured";
        }
        if (profile.apiManaged()) {
            return apiClient == null ? "ReSyncUnavailable" : null;
        }
        if (profile.wsUrl() == null || profile.wsUrl().isBlank()) {
            return "ReSyncPortMissing";
        }
        if (!profile.wsUrl().startsWith("ws://") && !profile.wsUrl().startsWith("wss://")) {
            return "ReSyncEndpointInvalid";
        }
        if (profile.apiKey() == null || profile.apiKey().isBlank()) {
            return "ReSyncApiKeyMissing";
        }
        return null;
    }

    public String getFlowAvailabilityIssue(String serverId, ClientServerView server) {
        return getFlowAvailabilityIssue(ReSyncServerIdentity.from(serverId, server));
    }

    private ReSyncConnectionProfile getOrResolveProfile(ReSyncServerIdentity identity) {
        ReSyncConnectionProfile profile = flowProfiles.get(identity.serverId());
        if (profile != null) {
            return profile;
        }
        profile = resolveConnectionProfile(identity);
        if (profile != null) {
            flowProfiles.put(identity.serverId(), profile);
        }
        return profile;
    }

    public void provisionReSyncForReStudioServer(String serverId, Consumer<Boolean> callback) {
        if (serverId == null || serverId.isBlank()) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        if (apiClient == null) {
            notificationSink.show("ReSync", "ReSync Isn't Installed/Enabled", ReSyncNotificationLevel.ERROR);
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        apiClient.provisionReSync(serverId).thenAccept(response -> {
            boolean ok = response != null && response.success;
            if (!ok) {
                String message = response != null && response.message != null && !response.message.isBlank() ? response.message : "Provision Failed";
                String normalized = normalizeReSyncNotificationMessage(message);
                ReSyncNotificationLevel type = "ReSync Connection Timed Out".equals(normalized) ? ReSyncNotificationLevel.WARN : ReSyncNotificationLevel.ERROR;
                notificationSink.show("ReSync", normalized, type);
            }
            if (callback != null) {
                callback.accept(ok);
            }
        }).exceptionally(error -> {
            String reason = error != null && error.getMessage() != null ? error.getMessage() : "ProvisionFailed";
            String normalized = normalizeReSyncNotificationMessage(reason);
            ReSyncNotificationLevel type = "ReSync Connection Timed Out".equals(normalized) ? ReSyncNotificationLevel.WARN : ReSyncNotificationLevel.ERROR;
            notificationSink.show("ReSync", normalized, type);
            if (callback != null) {
                callback.accept(false);
            }
            return null;
        });
    }

    public void updateReSyncForReStudioServer(String serverId, Consumer<Boolean> callback) {
        if (serverId == null || serverId.isBlank()) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        if (apiClient == null) {
            notificationSink.show("ReSync", "ReSync Isn't Installed/Enabled", ReSyncNotificationLevel.ERROR);
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        apiClient.updateReSync(serverId).thenAccept(response -> {
            boolean ok = response != null && response.success;
            if (!ok) {
                String message = response != null && response.message != null && !response.message.isBlank() ? response.message : "Update Failed";
                String normalized = normalizeReSyncNotificationMessage(message);
                ReSyncNotificationLevel type = "ReSync Connection Timed Out".equals(normalized) ? ReSyncNotificationLevel.WARN : ReSyncNotificationLevel.ERROR;
                notificationSink.show("ReSync", normalized, type);
            }
            if (callback != null) {
                callback.accept(ok);
            }
        }).exceptionally(error -> {
            String reason = error != null && error.getMessage() != null ? error.getMessage() : "UpdateFailed";
            String normalized = normalizeReSyncNotificationMessage(reason);
            ReSyncNotificationLevel type = "ReSync Connection Timed Out".equals(normalized) ? ReSyncNotificationLevel.WARN : ReSyncNotificationLevel.ERROR;
            notificationSink.show("ReSync", normalized, type);
            if (callback != null) {
                callback.accept(false);
            }
            return null;
        });
    }

    public Async<String> getReSyncVersionForReStudioServer(String serverId) {
        if (serverId == null || serverId.isBlank() || apiClient == null) {
            return Async.completed("");
        }
        return apiClient.getReSyncVersion(serverId);
    }

    public <T> T getInstanceByServerId(String serverId) {
        return findInstanceByServerId(ReSyncServerIdentity.of(serverId));
    }

    @SuppressWarnings("unchecked")
    public <T> T findInstanceByServerId(ReSyncServerIdentity identity) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        return (T) (canonical == null ? null : profileProvider.findInstance(canonical));
    }

    public <T> T findInstanceByServerId(String serverId, ClientServerView server) {
        return findInstanceByServerId(ReSyncServerIdentity.from(serverId, server));
    }

    public Object getClient() {
        return client;
    }

    private ReSyncConnectionProfile profileForLocalInstance(ReSyncServerIdentity identity) {
        ReSyncServerIdentity canonical = rememberIdentity(identity);
        if (canonical == null || !canonical.present()) {
            return null;
        }
        ReSyncConnectionProfile profile = flowProfiles.get(canonical.serverId());
        if (profile != null) {
            return profile;
        }
        profile = profileProvider.resolve(canonical);
        if (profile != null) {
            flowProfiles.put(canonical.serverId(), profile);
        }
        return profile;
    }

    private ReSyncServerIdentity rememberIdentity(ReSyncServerIdentity identity) {
        if (identity == null || !identity.present()) {
            return identity;
        }
        String serverId = identity.serverId();
        ReSyncServerIdentity existing = identities.get(serverId);
        if (existing == null) {
            identities.put(serverId, identity);
            return identity;
        }
        String backendType = identity.backendType().isBlank() ? existing.backendType() : identity.backendType();
        String displayName = identity.displayName().isBlank() ? existing.displayName() : identity.displayName();
        ReSyncServerIdentity merged = new ReSyncServerIdentity(serverId, displayName, backendType);
        identities.put(serverId, merged);
        return merged;
    }

    public String normalizeReSyncNotificationMessage(String message) {
        if (message == null || message.isBlank()) {
            return "ReSync Isn't Installed/Enabled";
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("timed out")) {
            return "ReSync Connection Timed Out";
        }
        if (normalized.contains("server not found")) {
            return "ReSync Couldn't Find This Server. Reconnect The Server And Try Again";
        }
        if (normalized.contains("connection refused") || normalized.contains("connectfailed")) {
            return "ReSync Endpoint Unreachable. Check That The Server And ReSync Are Running";
        }
        if (normalized.contains("protocol mismatch") || normalized.contains("protocol version")) {
            return "ReSync Protocol Mismatch. Update ReSync And Remotely";
        }
        if (normalized.contains("flow registry version mismatch") || normalized.contains("flow contract version mismatch")) {
            return message;
        }
        if (normalized.contains("version mismatch") || normalized.contains("runtime version")) {
            return "ReSync Version Mismatch. Update ReSync And Remotely";
        }
        return switch (message) {
            case "ServerIdMissing" -> "Server ID Missing";
            case "ServerNotFound" -> "Server Not Found";
            case "ReSyncNotConfigured" -> "ReSync Not Configured";
            case "ReSyncPortMissing" -> "ReSync Port Missing";
            case "ReSyncEndpointInvalid" -> "ReSync Endpoint Is Invalid";
            case "ReSyncApiKeyMissing" -> "ReSync API Key Missing";
            case "ReSyncUnavailable" -> "ReSync Unavailable";
            case "ReSyncNotEnabled" -> "ReSync Isn't Installed/Enabled";
            case "ReSyncServerNotFound" -> "ReSync Server Not Found";
            default -> message;
        };
    }

}
