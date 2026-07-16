package redxax.oxy.remotely.network;

import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.backend.feature.ServerInfoFeature;
import restudio.rebase.instance.Instance;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class NetworkProviderAllocationService {
    public boolean isProviderManaged(Instance instance) {
        BackendConfig backend = instance == null ? null : instance.getBackendConfig();
        if (backend == null || backend.type == null) {
            return false;
        }
        return "PTERO".equalsIgnoreCase(backend.type) || "RESTUDIO".equalsIgnoreCase(backend.type);
    }

    public CompletableFuture<NetworkProviderAllocation> resolve(Instance instance) {
        if (!isProviderManaged(instance)) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            ServerBackend backend = instance.getBackend();
            if (backend == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Provider Connection Is Unavailable For " + instance.getName()));
            }
            ServerInfoFeature feature = backend.getFeature(ServerInfoFeature.class).orElse(null);
            if (feature == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Provider Allocation Discovery Is Unavailable For " + instance.getName()));
            }
            return feature.getConnectionInfo().thenApply(connection -> allocation(instance, connection)).exceptionally(throwable -> {
                throw new CompletionException(new IllegalStateException("Provider Allocation Is Unavailable For " + instance.getName() + " • " + rootMessage(throwable), throwable));
            });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
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
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
