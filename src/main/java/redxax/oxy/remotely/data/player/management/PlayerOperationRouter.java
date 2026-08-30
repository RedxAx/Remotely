package redxax.oxy.remotely.data.player.management;

import redxax.oxy.remotely.util.BrowserSafeState;

import java.util.Comparator;
import java.util.List;
import restudio.rescreen.platform.Async;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PlayerOperationRouter {
    private final List<Route> routes = BrowserSafeState.list();

    public void register(String providerId, int priority, Predicate<PlayerOperation> support,
                         Function<PlayerOperation, Async<PlayerOperationResult>> executor) {
        routes.add(new Route(providerId, priority, support, executor));
        routes.sort(Comparator.comparingInt(Route::priority).reversed());
    }

    public boolean supports(PlayerOperation operation) {
        return route(operation) != null;
    }

    public Async<PlayerOperationResult> execute(PlayerOperation operation) {
        Route route = route(operation);
        if (route == null) return Async.completed(PlayerOperationResult.failed(operation.operationId(), "Unsupported Operation"));
        try {
            return route.executor().apply(operation);
        } catch (Exception exception) {
            return Async.completed(PlayerOperationResult.failed(operation.operationId(), exception.getMessage()));
        }
    }

    private Route route(PlayerOperation operation) {
        if (operation == null) return null;
        for (Route route : routes) {
            if (route.support().test(operation)) return route;
        }
        return null;
    }

    private record Route(String providerId, int priority, Predicate<PlayerOperation> support,
                         Function<PlayerOperation, Async<PlayerOperationResult>> executor) {
    }
}
