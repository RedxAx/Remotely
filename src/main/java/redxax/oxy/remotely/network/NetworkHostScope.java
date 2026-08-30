package redxax.oxy.remotely.network;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class NetworkHostScope {
    private static volatile Resolver resolver = ignored -> HostIdentity.local();

    private NetworkHostScope() {
    }

    public static void installResolver(Resolver nextResolver) {
        resolver = nextResolver == null ? ignored -> HostIdentity.local() : nextResolver;
    }

    public static String resolve(Object instance) {
        HostIdentity identity = instance instanceof HostIdentity value ? value : resolver.resolve(instance);
        return resolve(identity);
    }

    public static String resolveProviderAllocation(Object instance, String address) {
        HostIdentity identity = instance instanceof HostIdentity value ? value : resolver.resolve(instance);
        if (identity == null || identity.type().isBlank()) {
            return resolve(instance);
        }
        String type = identity.type().trim().toLowerCase(Locale.ROOT);
        String controller = firstCredential(identity, "hostId", "apiUrl", "panelUrl", "host").toLowerCase(Locale.ROOT);
        String allocation = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        return type + ":" + (controller.isBlank() ? "provider" : controller) + ":" + (allocation.isBlank() ? "allocation" : allocation);
    }

    private static String resolve(HostIdentity identity) {
        if (identity == null || identity.type().isBlank() || "LOCAL".equalsIgnoreCase(identity.type())) {
            return "local";
        }
        String type = identity.type().trim().toLowerCase(Locale.ROOT);
        String hostId = credential(identity, "hostId");
        if (!hostId.isBlank()) {
            return type + ":" + hostId;
        }
        String host = credential(identity, "host");
        String port = credential(identity, "port");
        if (!host.isBlank()) {
            return type + ":" + host.toLowerCase(Locale.ROOT) + (port.isBlank() ? "" : ":" + port);
        }
        String identifier = credential(identity, "identifier");
        return identifier.isBlank() ? type : type + ":" + identifier;
    }

    private static String firstCredential(HostIdentity identity, String... keys) {
        for (String key : keys) {
            String value = credential(identity, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String credential(HostIdentity identity, String key) {
        String value = identity.credentials().get(key);
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    public interface Resolver {
        HostIdentity resolve(Object instance);
    }

    public record HostIdentity(String type, Map<String, String> credentials) {
        public HostIdentity {
            type = type == null ? "" : type.trim();
            Map<String, String> normalized = new LinkedHashMap<>();
            if (credentials != null) {
                credentials.forEach((key, value) -> {
                    if (key != null && value != null) {
                        normalized.put(key, value);
                    }
                });
            }
            credentials = Map.copyOf(normalized);
        }

        public static HostIdentity local() {
            return new HostIdentity("LOCAL", Map.of());
        }
    }
}
