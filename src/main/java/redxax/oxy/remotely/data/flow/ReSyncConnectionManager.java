package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.util.BrowserSafeState;

import redxax.oxy.remotely.RemotelyServerApi;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.rebase.platform.Async;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class ReSyncConnectionManager {
    private static final Duration CONNECTION_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private final Object client;
    private final RemotelyServerApi apiClient;
    private final ReSyncFlowClientFactory flowClientFactory;
    private final ReSyncConnectionProfileProvider profileProvider;
    private final ReSyncConnectionNotificationSink notificationSink;
    private final NodeRegistry nodeRegistry;
    private final ReSyncFlowClientContext flowClientContext;
    private final Map<String, ReSyncFlowClient> flowClients = BrowserSafeState.map();
    private final Map<String, ReSyncConnectionProfile> flowProfiles = BrowserSafeState.map();
    private Consumer<String> connectionListener = serverId -> {};
    private Consumer<String> disconnectListener = serverId -> {};

    public record ReSyncConnectionProfile(String wsUrl, String apiKey) {
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
        return flowClients.get(serverId);
    }

    public ReSyncConnectionProfile getProfile(String serverId) {
        return flowProfiles.get(serverId);
    }

    public boolean isFlowClientConnected(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        return flowClient != null && flowClient.isConnectedState();
    }

    public ReSyncFlowClient.ConnectionState getFlowClientConnectionState(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return ReSyncFlowClient.ConnectionState.DISCONNECTED;
        }
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        return flowClient == null ? ReSyncFlowClient.ConnectionState.DISCONNECTED : flowClient.connectionState();
    }

    public ReSyncFlowClient ensureFlowClient(String serverId, boolean showNotifications) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        return ensureFlowClient(serverId, profileForLocalInstance(serverId), showNotifications, true);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId, ReSyncConnectionProfile profile) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        return ensureFlowClient(serverId, profile, true, true);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId) {
        return ensureFlowClient(serverId, true);
    }

    public boolean canUseFlowClient(String serverId, Object server) {
        return getFlowAvailabilityIssue(serverId, server) == null;
    }

    public boolean canActivateLiveSession(ReSyncLiveServerSession session) {
        return flowClientFactory.available() && session != null && session.serverId() != null
            && !session.serverId().isBlank() && session.transport() != null;
    }

    public Async<ReSyncFlowClient.ConnectionState> awaitFlowClientConnected(String serverId) {
        return awaitFlowClientConnected(serverId, true);
    }

    public Async<ReSyncFlowClient.ConnectionState> awaitFlowClientConnected(String serverId, boolean showNotifications) {
        if (serverId == null || serverId.isBlank()) {
            return Async.completed(ReSyncFlowClient.ConnectionState.DISCONNECTED);
        }
        ReSyncFlowClient flowClient = ensureFlowClient(serverId, profileForLocalInstance(serverId), showNotifications, false);
        if (flowClient == null) {
            return Async.completed(ReSyncFlowClient.ConnectionState.DISCONNECTED);
        }
        if (flowClient.isConnectedState()) {
            return Async.completed(ReSyncFlowClient.ConnectionState.CONNECTED);
        }
        boolean initiate = flowClient.connectionState() == ReSyncFlowClient.ConnectionState.DISCONNECTED;
        Async<ReSyncFlowClient.ConnectionState> result = flowClient.awaitConnected(CONNECTION_WAIT_TIMEOUT, false);
        if (initiate) {
            flowClient.connectAsync();
        }
        return result;
    }

    public ReSyncFlowClient activateLiveSession(ReSyncLiveServerSession session) {
        if (!canActivateLiveSession(session)) {
            return null;
        }
        ReSyncFlowClient existing = flowClients.get(session.serverId());
        if (existing != null && existing.usesFrameTransport(session.transport())) {
            existing.connectAsync();
            return existing;
        }
        existing = flowClients.remove(session.serverId());
        if (existing != null) {
            existing.shutdown();
        }
        flowProfiles.remove(session.serverId());
        Object clientState = flowClientContext == null ? client : flowClientContext;
        ReSyncFlowClient flowClient = flowClientFactory.createLive(session.serverId(), session.transport(), clientState);
        if (flowClient == null) {
            return null;
        }
        flowClient.setConnectionListener(() -> connectionListener.accept(session.serverId()));
        flowClient.setDisconnectListener(() -> disconnectListener.accept(session.serverId()));
        flowClient.setErrorListener((nodeId, message) -> {
            String normalized = normalizeReSyncNotificationMessage(message);
            if (!"ReSync Connection Timed Out".equals(normalized)) {
                notificationSink.show("ReSync", normalized, ReSyncNotificationLevel.ERROR);
            }
        });
        flowClients.put(session.serverId(), flowClient);
        flowClient.connectAsync();
        return flowClient;
    }

    private ReSyncFlowClient ensureFlowClient(String serverId, ReSyncConnectionProfile profile, boolean showNotifications, boolean connectIfNeeded) {
        if (serverId == null || serverId.isBlank() || !flowClientFactory.available()) {
            return null;
        }
        if (!profileProvider.connectionAllowed(serverId, profile)) {
            ReSyncFlowClient blocked = flowClients.remove(serverId);
            if (blocked != null) {
                blocked.shutdown();
            }
            flowProfiles.remove(serverId);
            return null;
        }
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null && profile != null && !flowClient.isConnectedState() && !flowClient.matchesDirectProfile(profile.wsUrl(), profile.apiKey())) {
            flowClient.shutdown();
            flowClients.remove(serverId);
            flowClient = null;
        }
        if (flowClient == null) {
            if (profile != null && profile.wsUrl() != null && !profile.wsUrl().isBlank()) {
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
        flowClient.setConnectionListener(() -> connectionListener.accept(serverId));
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

    public void closeServerConnection(String serverId, Runnable onCacheClear) {
        ReSyncFlowClient flowClient = flowClients.remove(serverId);
        if (flowClient != null) {
            flowClient.shutdown();
        }
        flowProfiles.remove(serverId);
        if (nodeRegistry != null) {
            nodeRegistry.clearServer(serverId);
        }
        if (onCacheClear != null) {
            onCacheClear.run();
        }
    }

    public void disconnectServerConnection(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        ReSyncFlowClient flowClient = flowClients.remove(serverId);
        if (flowClient != null) {
            flowClient.setDisconnectListener(() -> {});
            flowClient.shutdown();
        }
        flowProfiles.remove(serverId);
    }

    public void setDisconnectListener(Consumer<String> listener) {
        disconnectListener = listener != null ? listener : serverId -> {};
        flowClients.forEach((serverId, flowClient) -> flowClient.setDisconnectListener(() -> disconnectListener.accept(serverId)));
    }

    public void setConnectionListener(Consumer<String> listener) {
        connectionListener = listener != null ? listener : serverId -> {};
        flowClients.forEach((serverId, flowClient) -> flowClient.setConnectionListener(() -> connectionListener.accept(serverId)));
    }

    public void shutdownAll() {
        List<ReSyncFlowClient> clients = new ArrayList<>(flowClients.values());
        flowClients.clear();
        flowProfiles.clear();
        for (ReSyncFlowClient flowClient : clients) {
            flowClient.shutdown();
        }
    }

    public void resolveAndStoreProfile(String serverId, Object server) {
        String actualServerId = serverId;
        if (actualServerId == null || actualServerId.isBlank()) {
            return;
        }
        ReSyncConnectionProfile profile = resolveConnectionProfile(actualServerId, server);
        if (profile == null && serverId != null && !serverId.isBlank() && !actualServerId.equals(serverId)) {
            profile = resolveConnectionProfile(serverId, server);
        }
        if (profile != null) {
            flowProfiles.put(actualServerId, profile);
            if (serverId != null && !serverId.isBlank()) {
                flowProfiles.put(serverId, profile);
            }
        }
    }

    public ReSyncConnectionProfile resolveConnectionProfile(String serverId, Object server) {
        return profileProvider.resolve(serverId, server);
    }

    public String getFlowAvailabilityIssue(String serverId, Object server) {
        String actualServerId = serverId;
        if (actualServerId == null || actualServerId.isBlank()) {
            return "ServerIdMissing";
        }
        if (!flowClientFactory.available()) {
            return "ReSyncUnavailable";
        }
        if (!profileProvider.connectionAllowed(actualServerId, server)) {
            return "ReSyncUnavailable";
        }
        if (server != null) {
            return null;
        }
        Object instance = profileProvider.findInstance(actualServerId, null);
        if (instance == null) {
            return "ServerNotFound";
        }
        if (profileProvider.isReStudioInstance(instance)) {
            return null;
        }
        ReSyncConnectionProfile profile = resolveConnectionProfile(actualServerId, server);
        if (profile == null) {
            return "ReSyncNotConfigured";
        }
        if (profile.wsUrl() == null || profile.wsUrl().isBlank()) {
            return "ReSyncPortMissing";
        }
        if (profile.apiKey() == null || profile.apiKey().isBlank()) {
            return "ReSyncApiKeyMissing";
        }
        return null;
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
        return findInstanceByServerId(serverId, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T findInstanceByServerId(String serverId, Object server) {
        return (T) profileProvider.findInstance(serverId, server);
    }

    public Object getClient() {
        return client;
    }

    private ReSyncConnectionProfile profileForLocalInstance(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        ReSyncConnectionProfile profile = flowProfiles.get(serverId);
        if (profile != null) {
            return profile;
        }
        if (!profileProvider.hasInstanceAccess()) {
            return null;
        }
        profile = profileProvider.resolve(serverId, null);
        if (profile != null) {
            flowProfiles.put(serverId, profile);
        }
        return profile;
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
        if (normalized.contains("version mismatch") || normalized.contains("runtime version")) {
            return "ReSync Version Mismatch. Update ReSync And Remotely";
        }
        return switch (message) {
            case "ServerIdMissing" -> "Server ID Missing";
            case "ServerNotFound" -> "Server Not Found";
            case "ReSyncNotConfigured" -> "ReSync Not Configured";
            case "ReSyncPortMissing" -> "ReSync Port Missing";
            case "ReSyncApiKeyMissing" -> "ReSync API Key Missing";
            case "ReSyncUnavailable" -> "ReSync Unavailable";
            case "ReSyncNotEnabled" -> "ReSync Isn't Installed/Enabled";
            case "ReSyncServerNotFound" -> "ReSync Server Not Found";
            default -> message;
        };
    }

}
