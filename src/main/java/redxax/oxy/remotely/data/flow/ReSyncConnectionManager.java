package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.rebase.Rebase;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ReSyncConnectionManager {
    private final RemotelyClient client;
    private final ReStudioApiClient apiClient;
    private final Map<String, ReSyncFlowClient> flowClients = new ConcurrentHashMap<>();
    private final Map<String, ReSyncConnectionProfile> flowProfiles = new ConcurrentHashMap<>();
    private Consumer<String> connectionListener = serverId -> {};
    private Consumer<String> disconnectListener = serverId -> {};

    public record ReSyncConnectionProfile(String wsUrl, String apiKey) {
    }

    public ReSyncConnectionManager(RemotelyClient client, ReStudioApiClient apiClient) {
        this.client = client;
        this.apiClient = apiClient;
    }

    public ReStudioApiClient getApiClient() {
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
        return ensureFlowClient(serverId, profileForLocalInstance(serverId), showNotifications, true);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId, ReSyncConnectionProfile profile) {
        return ensureFlowClient(serverId, profile, true, true);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId) {
        return ensureFlowClient(serverId, true);
    }

    public ReSyncFlowClient activateLiveSession(ReSyncLiveServerSession session) {
        if (session == null || session.serverId() == null || session.serverId().isBlank() || session.transport() == null) {
            return null;
        }
        ReSyncFlowClient existing = flowClients.get(session.serverId());
        if (existing != null && existing.usesFrameTransport(session.transport())) {
            existing.connect();
            return existing;
        }
        existing = flowClients.remove(session.serverId());
        if (existing != null) {
            existing.shutdown();
        }
        flowProfiles.remove(session.serverId());
        ReSyncFlowClient flowClient = new ReSyncFlowClient(session.serverId(), session.transport(), client);
        flowClient.setConnectionListener(() -> connectionListener.accept(session.serverId()));
        flowClient.setDisconnectListener(() -> disconnectListener.accept(session.serverId()));
        flowClient.setErrorListener((nodeId, message) -> {
            String normalized = normalizeReSyncNotificationMessage(message);
            if (!"ReSync Connection Timed Out".equals(normalized)) {
                ScreenManager.getInstance().execute(() -> new Notification("ReSync", normalized, Notification.Type.ERROR));
            }
        });
        flowClients.put(session.serverId(), flowClient);
        flowClient.connect();
        return flowClient;
    }

    private ReSyncFlowClient ensureFlowClient(String serverId, ReSyncConnectionProfile profile, boolean showNotifications, boolean connectIfNeeded) {
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null && profile != null && !flowClient.isConnectedState() && !flowClient.matchesDirectProfile(profile.wsUrl(), profile.apiKey())) {
            flowClient.shutdown();
            flowClients.remove(serverId);
            flowClient = null;
        }
        if (flowClient == null) {
            if (profile != null && profile.wsUrl() != null && !profile.wsUrl().isBlank()) {
                flowClient = new ReSyncFlowClient(serverId, apiClient, profile.wsUrl(), profile.apiKey(), client);
            } else {
                flowClient = new ReSyncFlowClient(serverId, apiClient, client);
            }
            flowClients.put(serverId, flowClient);
        }
        flowClient.setConnectionListener(() -> connectionListener.accept(serverId));
        flowClient.setDisconnectListener(() -> disconnectListener.accept(serverId));
        if (showNotifications) {
            flowClient.setErrorListener((nodeId, message) -> ScreenManager.getInstance().execute(() -> {
                String normalized = normalizeReSyncNotificationMessage(message);
                Notification.Type type = "ReSync Connection Timed Out".equals(normalized) ? Notification.Type.WARN : Notification.Type.ERROR;
                new Notification("ReSync", normalized, type);
            }));
        } else {
            flowClient.setErrorListener((nodeId, message) -> {
            });
        }
        if (connectIfNeeded) {
            flowClient.connect();
        }
        return flowClient;
    }

    public void closeServerConnection(String serverId, Runnable onCacheClear) {
        ReSyncFlowClient flowClient = flowClients.remove(serverId);
        if (flowClient != null) {
            flowClient.shutdown();
        }
        flowProfiles.remove(serverId);
        if (NodeRegistry.getInstance() != null) {
            NodeRegistry.getInstance().clearServer(serverId);
        }
        onCacheClear.run();
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

    public void resolveAndStoreProfile(String serverId, ClientServerView server) {
        String actualServerId = server != null && server.identifier != null && !server.identifier.isBlank() ? server.identifier : serverId;
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

    public ReSyncConnectionProfile resolveConnectionProfile(String serverId, ClientServerView server) {
        Instance instance = findInstanceByServerId(serverId, server);
        if (instance == null) {
            return null;
        }
        ReSyncConnectionProfile localProfile = tryReadLocalReSyncConfig(instance);
        if (localProfile != null) {
            return localProfile;
        }
        BackendConfig backendConfig = instance.getBackendConfig();
        if (backendConfig == null) {
            return null;
        }
        if ("RESTUDIO".equalsIgnoreCase(backendConfig.type)) {
            return null;
        }
        return tryReadReSyncConfigFromBackend(instance);
    }

    public String getFlowAvailabilityIssue(String serverId, ClientServerView server) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        if (actualServerId == null || actualServerId.isBlank()) {
            return "ServerIdMissing";
        }
        if (server != null) {
            return null;
        }
        Instance instance = findInstanceByServerId(actualServerId, null);
        if (instance == null) {
            return "ServerNotFound";
        }
        BackendConfig backendConfig = instance.getBackendConfig();
        if (backendConfig != null && "RESTUDIO".equalsIgnoreCase(backendConfig.type)) {
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
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", "ReSync Isn't Installed/Enabled", Notification.Type.ERROR));
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        apiClient.provisionReSync(serverId).thenAccept(response -> {
            boolean ok = response != null && response.success;
            ScreenManager.getInstance().execute(() -> {
                if (!ok) {
                    String message = response != null && response.message != null && !response.message.isBlank() ? response.message : "Provision Failed";
                    String normalized = normalizeReSyncNotificationMessage(message);
                    Notification.Type type = "ReSync Connection Timed Out".equals(normalized) ? Notification.Type.WARN : Notification.Type.ERROR;
                    new Notification("ReSync", normalized, type);
                }
            });
            if (callback != null) {
                callback.accept(ok);
            }
        }).exceptionally(error -> {
            ScreenManager.getInstance().execute(() -> {
                String reason = error != null && error.getMessage() != null ? error.getMessage() : "ProvisionFailed";
                String normalized = normalizeReSyncNotificationMessage(reason);
                Notification.Type type = "ReSync Connection Timed Out".equals(normalized) ? Notification.Type.WARN : Notification.Type.ERROR;
                new Notification("ReSync", normalized, type);
            });
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
            ScreenManager.getInstance().execute(() -> new Notification("ReSync", "ReSync Isn't Installed/Enabled", Notification.Type.ERROR));
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        apiClient.updateReSync(serverId).thenAccept(response -> {
            boolean ok = response != null && response.success;
            ScreenManager.getInstance().execute(() -> {
                if (!ok) {
                    String message = response != null && response.message != null && !response.message.isBlank() ? response.message : "Update Failed";
                    String normalized = normalizeReSyncNotificationMessage(message);
                    Notification.Type type = "ReSync Connection Timed Out".equals(normalized) ? Notification.Type.WARN : Notification.Type.ERROR;
                    new Notification("ReSync", normalized, type);
                }
            });
            if (callback != null) {
                callback.accept(ok);
            }
        }).exceptionally(error -> {
            ScreenManager.getInstance().execute(() -> {
                String reason = error != null && error.getMessage() != null ? error.getMessage() : "UpdateFailed";
                String normalized = normalizeReSyncNotificationMessage(reason);
                Notification.Type type = "ReSync Connection Timed Out".equals(normalized) ? Notification.Type.WARN : Notification.Type.ERROR;
                new Notification("ReSync", normalized, type);
            });
            if (callback != null) {
                callback.accept(false);
            }
            return null;
        });
    }

    public CompletableFuture<String> getReSyncVersionForReStudioServer(String serverId) {
        if (serverId == null || serverId.isBlank() || apiClient == null) {
            return CompletableFuture.completedFuture("");
        }
        return apiClient.getReSyncVersion(serverId).exceptionally(error -> "");
    }

    public Instance getInstanceByServerId(String serverId) {
        return findInstanceByServerId(serverId, null);
    }

    public Instance findInstanceByServerId(String serverId, ClientServerView server) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        try {
            InstanceManager instanceManager = Rebase.get().getInstanceManager();
            List<Instance> instances = new ArrayList<>(instanceManager.getLocalInstances());
            for (var host : instanceManager.getRemoteHosts()) {
                instances.addAll(instanceManager.getRemoteInstances(host));
            }
            for (Instance instance : instances) {
                if (instance == null) {
                    continue;
                }
                if (serverId.equalsIgnoreCase(instance.getInstanceId())) {
                    return instance;
                }
                BackendConfig backendConfig = instance.getBackendConfig();
                if (backendConfig != null && backendConfig.credentials != null) {
                    String identifier = backendConfig.credentials.get("identifier");
                    if (identifier != null && identifier.equals(serverId)) {
                        return instance;
                    }
                }
            }
            if (server != null && server.name != null) {
                for (Instance instance : instances) {
                    if (instance != null && server.name.equalsIgnoreCase(instance.getName())) {
                        return instance;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public RemotelyClient getClient() {
        return client;
    }

    private ReSyncConnectionProfile tryReadReSyncConfigFromBackend(Instance instance) {
        if (instance == null || instance.getBackendConfig() == null) {
            return null;
        }
        try {
            ServerBackend backend = instance.getBackend();
            if (backend == null) {
                return null;
            }
            if (!backend.isConnected()) {
                backend.connect();
            }
            FileSystemProvider fs = backend.getFileSystem();
            if (fs == null) {
                return null;
            }
            Path configPath = Path.of(instance.getPath()).resolve("plugins").resolve("ReSync").resolve("config.properties");
            Boolean exists = fs.exists(configPath).get(5, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(exists)) {
                return null;
            }
            String content = fs.read(configPath).get(10, TimeUnit.SECONDS);
            if (content == null || content.isBlank()) {
                return null;
            }
            Map<String, String> credentials = instance.getBackendConfig().credentials;
            return parseReSyncProfile(content, credentials != null ? safeText(credentials.get("host")) : "");
        } catch (Exception ignored) {
            return null;
        }
    }

    private ReSyncConnectionProfile profileForLocalInstance(String serverId) {
        ReSyncConnectionProfile profile = flowProfiles.get(serverId);
        if (profile != null) {
            return profile;
        }
        Instance instance = findInstanceByServerId(serverId, null);
        if (instance == null) {
            return null;
        }
        profile = tryReadLocalReSyncConfig(instance);
        if (profile != null) {
            flowProfiles.put(serverId, profile);
        }
        return profile;
    }

    private ReSyncConnectionProfile tryReadLocalReSyncConfig(Instance instance) {
        if (instance == null) {
            return null;
        }
        try {
            Path configPath = Path.of(instance.getPath()).resolve("plugins").resolve("ReSync").resolve("config.properties");
            if (!Files.isRegularFile(configPath)) {
                return null;
            }
            return parseReSyncProfile(Files.readString(configPath), "127.0.0.1");
        } catch (Exception ignored) {
            return null;
        }
    }

    private ReSyncConnectionProfile parseReSyncProfile(String content, String host) {
        if (content == null || content.isBlank() || host == null || host.isBlank()) {
            return null;
        }
        String port = "";
        String apiKey = "";
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            switch (key) {
                case "port" -> port = value;
                case "api-key" -> apiKey = value;
            }
        }
        if (port.isBlank() || apiKey.isBlank()) {
            return null;
        }
        return new ReSyncConnectionProfile(normalizeWsUrl(host + ":" + port), apiKey);
    }

    private Instance findInstanceByServerId(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        try {
            InstanceManager instanceManager = Rebase.get().getInstanceManager();
            List<Instance> instances = new ArrayList<>(instanceManager.getLocalInstances());
            for (var host : instanceManager.getRemoteHosts()) {
                instances.addAll(instanceManager.getRemoteInstances(host));
            }
            for (Instance instance : instances) {
                if (instance == null || instance.getInstanceId() == null) {
                    continue;
                }
                if (serverId.equalsIgnoreCase(instance.getInstanceId())) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String normalizeWsUrl(String value) {
        String raw = safeText(value).trim();
        if (raw.isBlank()) {
            return null;
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
            return "ReSync Couldn't Connect. Check That The Server And ReSync Are Running";
        }
        return switch (message) {
            case "ServerIdMissing" -> "Server ID Missing";
            case "ServerNotFound" -> "Server Not Found";
            case "ReSyncNotConfigured" -> "ReSync Not Configured";
            case "ReSyncPortMissing" -> "ReSync Port Missing";
            case "ReSyncApiKeyMissing" -> "ReSync API Key Missing";
            case "ReSyncNotEnabled" -> "ReSync Isn't Installed/Enabled";
            case "ReSyncServerNotFound" -> "ReSync Server Not Found";
            default -> message;
        };
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
