package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.RemotelyComposition;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class DesktopReSyncConnectionProfileProvider implements ReSyncConnectionProfileProvider {
    private final RemotelyClient client;

    public DesktopReSyncConnectionProfileProvider(RemotelyClient client) {
        this.client = client;
    }

    @Override
    public ReSyncConnectionManager.ReSyncConnectionProfile resolve(String serverId, Object serverObject) {
        Instance instance = find(serverId, serverObject);
        if (instance == null) {
            return null;
        }
        ReSyncConnectionManager.ReSyncConnectionProfile local = readLocal(instance);
        if (local != null) {
            return local;
        }
        BackendConfig backendConfig = instance.getBackendConfig();
        if (backendConfig == null || "RESTUDIO".equalsIgnoreCase(backendConfig.type)) {
            return null;
        }
        return readBackend(instance);
    }

    @Override
    public Object findInstance(String serverId, Object serverObject) {
        return find(serverId, serverObject);
    }

    @Override
    public boolean hasInstanceAccess() {
        return client != null && client.getComposition().capabilities().has(RemotelyComposition.Capability.INSTANCE_RECONCILIATION);
    }

    @Override
    public boolean isReStudioInstance(Object instance) {
        if (!(instance instanceof Instance value) || value.getBackendConfig() == null) {
            return false;
        }
        return "RESTUDIO".equalsIgnoreCase(value.getBackendConfig().type);
    }

    private Instance find(String serverId, Object serverObject) {
        if (serverId == null || serverId.isBlank() || !hasInstanceAccess()) {
            return null;
        }
        try {
            InstanceManager manager = client.getComposition().createInstanceManager();
            if (manager == null) {
                return null;
            }
            List<Instance> instances = new ArrayList<>(manager.getLocalInstances());
            for (var host : manager.getRemoteHosts()) {
                instances.addAll(manager.getRemoteInstances(host));
            }
            String serverName = serverObject instanceof ClientServerView server ? server.name : null;
            for (Instance instance : instances) {
                if (instance == null) {
                    continue;
                }
                if (serverId.equalsIgnoreCase(instance.getInstanceId())) {
                    return instance;
                }
                BackendConfig config = instance.getBackendConfig();
                if (config != null && config.credentials != null && serverId.equals(config.credentials.get("identifier"))) {
                    return instance;
                }
                if (serverName != null && serverName.equalsIgnoreCase(instance.getName())) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ReSyncConnectionManager.ReSyncConnectionProfile readLocal(Instance instance) {
        try {
            Path path = Path.of(instance.getPath()).resolve("plugins").resolve("ReSync").resolve("config.properties");
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return parse(Files.readString(path), "127.0.0.1");
        } catch (Exception ignored) {
            return null;
        }
    }

    private ReSyncConnectionManager.ReSyncConnectionProfile readBackend(Instance instance) {
        try {
            ServerBackend backend = instance.getBackend();
            if (backend == null) {
                return null;
            }
            if (!backend.isConnected()) {
                backend.connect();
            }
            FileSystemProvider fileSystem = backend.getFileSystem();
            if (fileSystem == null) {
                return null;
            }
            Path path = Path.of(instance.getPath()).resolve("plugins").resolve("ReSync").resolve("config.properties");
            if (!Boolean.TRUE.equals(fileSystem.exists(path).get(5, TimeUnit.SECONDS))) {
                return null;
            }
            String content = fileSystem.read(path).get(10, TimeUnit.SECONDS);
            BackendConfig config = instance.getBackendConfig();
            String host = config == null || config.credentials == null ? "" : text(config.credentials.get("host"));
            return parse(content, host);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ReSyncConnectionManager.ReSyncConnectionProfile parse(String content, String host) {
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
        return new ReSyncConnectionManager.ReSyncConnectionProfile(normalize(host + ":" + port), apiKey);
    }

    private String normalize(String value) {
        String raw = text(value).trim();
        if (raw.startsWith("ws://") || raw.startsWith("wss://")) {
            return raw;
        }
        if (raw.startsWith("http://")) {
            return "ws://" + raw.substring(7);
        }
        if (raw.startsWith("https://")) {
            return "wss://" + raw.substring(8);
        }
        return "ws://" + raw;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
