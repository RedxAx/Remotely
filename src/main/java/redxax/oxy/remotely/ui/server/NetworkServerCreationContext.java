package redxax.oxy.remotely.ui.server;

import restudio.rebase.Rebase;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

record NetworkServerCreationContext(RemoteHost remoteHost, String hostLabel, boolean supported, String unavailableMessage) {
    static NetworkServerCreationContext local() {
        return new NetworkServerCreationContext(null, "Local", true, "");
    }

    static NetworkServerCreationContext active(RemoteHost remoteHost, boolean reStudio) {
        if (reStudio) {
            return new NetworkServerCreationContext(null, "ReStudio", false, "Create The Proxy On A Local Or SSH Host");
        }
        if (remoteHost == null) {
            return local();
        }
        if ("PTERO".equalsIgnoreCase(remoteHost.getType())) {
            return new NetworkServerCreationContext(remoteHost, remoteHost.name, false, "Create The Server In Pterodactyl, Then Add It Here");
        }
        return new NetworkServerCreationContext(remoteHost, remoteHost.name, true, "");
    }

    static NetworkServerCreationContext forInstance(Instance instance) {
        if (instance == null) {
            return new NetworkServerCreationContext(null, "Unknown Host", false, "The Proxy Host Is Unavailable");
        }
        BackendConfig backend = instance.getBackendConfig();
        if (backend == null || backend.type == null || "LOCAL".equalsIgnoreCase(backend.type)) {
            return local();
        }
        if ("RESTUDIO".equalsIgnoreCase(backend.type)) {
            return new NetworkServerCreationContext(null, "ReStudio", false, "Create The Server Through ReStudio, Then Add It Here");
        }
        Map<String, String> credentials = backend.credentials == null ? Map.of() : backend.credentials;
        RemoteHost remoteHost = Rebase.get().getInstanceManager().getRemoteHosts().stream().filter(host -> matches(host, credentials)).findFirst().orElse(null);
        if (remoteHost == null) {
            return new NetworkServerCreationContext(null, credentials.getOrDefault("host", backend.type), false, "Reconnect The Proxy Host Before Creating A Server");
        }
        return active(remoteHost, false);
    }

    static List<NetworkServerCreationContext> available() {
        List<NetworkServerCreationContext> contexts = new ArrayList<>();
        contexts.add(local());
        Rebase.get().getInstanceManager().getRemoteHosts().stream().filter(host -> !"PTERO".equalsIgnoreCase(host.getType())).map(host -> active(host, false)).forEach(contexts::add);
        return List.copyOf(contexts);
    }

    private static boolean matches(RemoteHost host, Map<String, String> credentials) {
        String hostId = credentials.get("hostId");
        if (hostId != null && !hostId.isBlank() && Objects.equals(hostId, host.hostId)) {
            return true;
        }
        String address = credentials.get("host");
        return address != null && !address.isBlank() && Objects.equals(address, host.getIp());
    }
}
