package redxax.oxy.remotely.data.player.management;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PlayerOperationRouter {
    private final List<Route> routes = new CopyOnWriteArrayList<>();

    public void register(String providerId, int priority, Predicate<PlayerOperation> support, Function<PlayerOperation, CompletableFuture<PlayerOperationResult>> executor) {
        routes.add(new Route(providerId, priority, support, executor));
        routes.sort(Comparator.comparingInt(Route::priority).reversed());
    }

    public boolean supports(PlayerOperation operation) {
        return route(operation) != null;
    }

    public CompletableFuture<PlayerOperationResult> execute(PlayerOperation operation) {
        Route route = route(operation);
        if (route == null) return CompletableFuture.completedFuture(PlayerOperationResult.failed(operation.operationId(), "Unsupported Operation"));
        try {
            return route.executor().apply(operation);
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(PlayerOperationResult.failed(operation.operationId(), exception.getMessage()));
        }
    }

    private Route route(PlayerOperation operation) {
        if (operation == null) return null;
        for (Route route : routes) {
            if (route.support().test(operation)) return route;
        }
        return null;
    }

    private record Route(String providerId, int priority, Predicate<PlayerOperation> support, Function<PlayerOperation, CompletableFuture<PlayerOperationResult>> executor) {
    }
}
