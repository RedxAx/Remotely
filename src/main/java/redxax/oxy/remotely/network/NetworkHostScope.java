package redxax.oxy.remotely.network;

import restudio.rebase.backend.BackendConfig;
import restudio.rebase.instance.Instance;

import java.util.Locale;

public final class NetworkHostScope {
    private NetworkHostScope() {
    }

    public static String resolve(Instance instance) {
        if (instance == null) {
            return "local";
        }
        BackendConfig backend = instance.getBackendConfig();
        if (backend == null || backend.type == null || backend.type.isBlank() || "LOCAL".equalsIgnoreCase(backend.type)) {
            return "local";
        }
        String type = backend.type.trim().toLowerCase(Locale.ROOT);
        String hostId = credential(backend, "hostId");
        if (!hostId.isBlank()) {
            return type + ":" + hostId;
        }
        String host = credential(backend, "host");
        String port = credential(backend, "port");
        if (!host.isBlank()) {
            return type + ":" + host.toLowerCase(Locale.ROOT) + (port.isBlank() ? "" : ":" + port);
        }
        String identifier = credential(backend, "identifier");
        return identifier.isBlank() ? type : type + ":" + identifier;
    }

    public static String resolveProviderAllocation(Instance instance, String address) {
        BackendConfig backend = instance == null ? null : instance.getBackendConfig();
        if (backend == null || backend.type == null || backend.type.isBlank()) {
            return resolve(instance);
        }
        String type = backend.type.trim().toLowerCase(Locale.ROOT);
        String controller = firstCredential(backend, "hostId", "apiUrl", "panelUrl", "host").toLowerCase(Locale.ROOT);
        String allocation = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        return type + ":" + (controller.isBlank() ? "provider" : controller) + ":" + (allocation.isBlank() ? "allocation" : allocation);
    }

    private static String firstCredential(BackendConfig backend, String... keys) {
        for (String key : keys) {
            String value = credential(backend, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String credential(BackendConfig backend, String key) {
        if (backend.credentials == null) {
            return "";
        }
        String value = backend.credentials.get(key);
        return value == null ? "" : value.trim();
    }
}
