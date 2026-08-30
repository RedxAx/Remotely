package redxax.oxy.remotely.network;

import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.backend.feature.ServerInfoFeature;
import restudio.rebase.backend.impl.PteroBackend;
import restudio.rebase.instance.Instance;

import java.util.Locale;
import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;



public class NetworkProviderAllocationService {
    static {
        NetworkHostScope.installResolver(DesktopNetworkHostScope::describe);
    }

    public boolean isProviderManaged(Instance instance) {
        BackendConfig backend = instance == null ? null : instance.getBackendConfig();
        if (backend == null || backend.type == null) {
            return false;
        }
        return PteroBackend.isPanelType(backend.type) || "RESTUDIO".equalsIgnoreCase(backend.type);
    }

    public Async<NetworkProviderAllocation> resolve(Instance instance) {
        if (!isProviderManaged(instance)) {
            return Async.completed(null);
        }
        try {
            ServerBackend backend = instance.getBackend();
            if (backend == null) {
                return Async.failed(new IllegalStateException("Provider Connection Is Unavailable For " + instance.getName()));
            }
            ServerInfoFeature feature = backend.getFeature(ServerInfoFeature.class).orElse(null);
            if (feature == null) {
                return Async.failed(new IllegalStateException("Provider Allocation Discovery Is Unavailable For " + instance.getName()));
            }
            return JvmAsyncBridge.fromFuture(feature.getConnectionInfo()).thenApply(connection -> allocation(instance, connection)).exceptionally(throwable -> {
                throw new IllegalStateException(new IllegalStateException("Provider Allocation Is Unavailable For " + instance.getName() + " • " + rootMessage(throwable), throwable));
            });
        } catch (RuntimeException exception) {
            return Async.failed(exception);
        }
    }

    NetworkProviderAllocation allocation(Instance instance, ServerInfoFeature.ServerConnectionInfo connection) {
        String address = connection == null || connection.hostIp() == null ? "" : connection.hostIp().trim();
        if (address.isBlank() || "unknown".equalsIgnoreCase(address) || "unavailable".equalsIgnoreCase(address)) {
            throw new IllegalStateException("Provider Allocation Address Is Unavailable For " + instance.getName());
        }
        int port;
        try {
            port = Integer.parseInt(connection.serverPort() == null ? "" : connection.serverPort().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Provider Allocation Port Is Invalid For " + instance.getName(), exception);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Provider Allocation Port Is Invalid For " + instance.getName());
        }
        String provider = instance.getBackendConfig().type.trim().toUpperCase(Locale.ROOT);
        return new NetworkProviderAllocation(instance.getInstanceId(), provider, address, port, NetworkHostScope.resolveProviderAllocation(instance, address));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
