package redxax.oxy.remotely.network;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record NetworkCreationRequest(String name, String proxyInstanceId, int entryPort, List<NetworkCreationMember> backends, boolean firewallVerified, List<String> fallbackRoutes, Map<String, List<String>> forcedHosts) {
    public NetworkCreationRequest {
        name = name == null ? "" : name.trim();
        proxyInstanceId = proxyInstanceId == null ? "" : proxyInstanceId.trim();
        entryPort = Math.clamp(entryPort, 0, 65535);
        backends = backends == null ? List.of() : List.copyOf(backends);
        fallbackRoutes = fallbackRoutes == null ? List.of() : List.copyOf(fallbackRoutes);
        if (forcedHosts == null) {
            forcedHosts = Map.of();
        } else {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            forcedHosts.forEach((host, routes) -> copy.put(host == null ? "" : host.trim(), routes == null ? List.of() : List.copyOf(routes)));
            forcedHosts = Map.copyOf(copy);
        }
    }

    public NetworkCreationRequest(String name, String proxyInstanceId, int entryPort, List<NetworkCreationMember> backends, boolean firewallVerified) {
        this(name, proxyInstanceId, entryPort, backends, firewallVerified, List.of(), Map.of());
    }
}
