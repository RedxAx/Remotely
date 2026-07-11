package redxax.oxy.remotely.data.player.management;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerOperationRouterTest {
    @Test
    void dispatchesExactlyOneHighestPriorityRoute() {
        PlayerOperationRouter router = new PlayerOperationRouter();
        AtomicInteger executions = new AtomicInteger();
        router.register("fallback", 10, operation -> true, operation -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture(new PlayerOperationResult(operation.operationId(), true, "Fallback", 0L, null));
        });
        router.register("primary", 100, operation -> true, operation -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture(new PlayerOperationResult(operation.operationId(), false, "Rejected", 0L, null));
        });
        PlayerOperation operation = new PlayerOperation("operation", PlayerOperation.Type.KICK, UUID.randomUUID(), "", false, 0L, List.of());

        PlayerOperationResult result = router.execute(operation).join();

        assertFalse(result.success());
        assertEquals(1, executions.get());
    }

    @Test
    void unsupportedOperationDoesNotDispatch() {
        PlayerOperationRouter router = new PlayerOperationRouter();
        PlayerOperation operation = new PlayerOperation("operation", PlayerOperation.Type.INVENTORY_EDIT, UUID.randomUUID(), "", false, 0L, List.of());

        assertFalse(router.supports(operation));
        assertTrue(router.execute(operation).join().reason().contains("Unsupported"));
    }
}
