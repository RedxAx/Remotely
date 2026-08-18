package redxax.oxy.remotely.network;

import restudio.rebase.backend.BackendConfig;
import restudio.rebase.instance.Instance;

import java.util.LinkedHashMap;
import java.util.Map;

final class DesktopNetworkHostScope {
    private DesktopNetworkHostScope() {
    }

    static NetworkHostScope.HostIdentity describe(Object value) {
        if (!(value instanceof Instance instance)) {
            return NetworkHostScope.HostIdentity.local();
        }
        BackendConfig backend = instance.getBackendConfig();
        if (backend == null) {
            return NetworkHostScope.HostIdentity.local();
        }
        Map<String, String> credentials = new LinkedHashMap<>();
        if (backend.credentials != null) {
            credentials.putAll(backend.credentials);
        }
        return new NetworkHostScope.HostIdentity(backend.type, credentials);
    }
}
