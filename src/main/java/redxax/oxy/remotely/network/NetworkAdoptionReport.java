package redxax.oxy.remotely.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record NetworkAdoptionReport(String proxyInstanceId, String bindAddress, int entryPort, boolean proxyOnlineMode, ForwardingMode forwardingMode, String secretFile, List<NetworkAdoptionRoute> routes, List<String> fallbackRoutes, Map<String, List<String>> forcedHosts, List<NetworkValidationIssue> issues) {
    public NetworkAdoptionReport {
        proxyInstanceId = normalize(proxyInstanceId);
        bindAddress = normalize(bindAddress);
        entryPort = Math.clamp(entryPort, 0, 65535);
        forwardingMode = forwardingMode == null ? ForwardingMode.NONE : forwardingMode;
        secretFile = normalize(secretFile);
        routes = routes == null ? List.of() : List.copyOf(routes);
        fallbackRoutes = fallbackRoutes == null ? List.of() : fallbackRoutes.stream().map(NetworkAdoptionReport::normalizeRoute).toList();
        if (forcedHosts == null) {
            forcedHosts = Map.of();
        } else {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            forcedHosts.forEach((host, routeNames) -> copy.put(normalize(host), routeNames == null ? List.of() : routeNames.stream().map(NetworkAdoptionReport::normalizeRoute).toList()));
            forcedHosts = Map.copyOf(copy);
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean canAdopt() {
        return issues.stream().noneMatch(NetworkValidationIssue::blocksPersistence) && routes.stream().allMatch(NetworkAdoptionRoute::matched);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeRoute(String value) {
        return normalize(value).toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
