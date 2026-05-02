package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.RemotelyClient;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ReSyncConnectionManager {
    private final RemotelyClient client;
    private final ReStudioApiClient apiClient;
    private final Map<String, ReSyncFlowClient> flowClients = new ConcurrentHashMap<>();
    private final Map<String, ReSyncConnectionProfile> flowProfiles = new ConcurrentHashMap<>();

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

    public ReSyncFlowClient ensureFlowClient(String serverId, boolean showNotifications) {
        return ensureFlowClient(serverId, flowProfiles.get(serverId), showNotifications, true);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId, ReSyncConnectionProfile profile) {
        return ensureFlowClient(serverId, profile, true, true);
    }

    public ReSyncFlowClient ensureFlowClient(String serverId) {
        return ensureFlowClient(serverId, true);
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
        ReSyncFlowClient flowClient = flowClients.get(serverId);
        if (flowClient != null) {
            flowClient.shutdown();
            flowClients.remove(serverId);
        }
        flowProfiles.remove(serverId);
        if (redxax.oxy.remotely.flow.registry.NodeRegistry.getInstance() != null) {
            redxax.oxy.remotely.flow.registry.NodeRegistry.getInstance().clearServer(serverId);
        }
        onCacheClear.run();
    }

    public void resolveAndStoreProfile(String serverId, ClientServerView server) {
        String actualServerId = (server != null && server.identifier != null) ? server.identifier : serverId;
        ReSyncConnectionProfile profile = resolveConnectionProfile(actualServerId, server);
        if (profile != null) {
            flowProfiles.put(actualServerId, profile);
        }
    }

    public ReSyncConnectionProfile resolveConnectionProfile(String serverId, ClientServerView server) {
        if (server != null) {
            return null;
        }
        Instance instance = findInstanceByServerId(serverId, null);
        if (instance == null || instance.getBackendConfig() == null) {
            return null;
        }
        BackendConfig backendConfig = instance.getBackendConfig();
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

    public void provisionReSyncForReStudioServer(String serverId, java.util.function.Consumer<Boolean> callback) {
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
            String port = null;
            String apiKey = null;
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                switch (key) {
                    case "port" -> port = value;
                    case "api-key" -> apiKey = value;
                }
            }
            String effectiveHost;
            if ("LOCAL".equalsIgnoreCase(instance.getBackendConfig().type)) {
                effectiveHost = "127.0.0.1";
            } else {
                Map<String, String> creds = instance.getBackendConfig().credentials;
                effectiveHost = creds != null ? safeText(creds.get("host")) : "";
            }
            if (effectiveHost.isBlank() || port == null || port.isBlank() || apiKey == null || apiKey.isBlank()) {
                return null;
            }
            String wsUrl = normalizeWsUrl(effectiveHost + ":" + port);
            return new ReSyncConnectionProfile(wsUrl, apiKey);
        } catch (Exception ignored) {
            return null;
        }
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
        if (message.toLowerCase(Locale.ROOT).contains("timed out")) {
            return "ReSync Connection Timed Out";
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
