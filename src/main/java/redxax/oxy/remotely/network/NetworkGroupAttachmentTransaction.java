package redxax.oxy.remotely.network;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public final class NetworkGroupAttachmentTransaction {
    private NetworkGroupAttachmentTransaction() {
    }

    public static <T> CompletableFuture<Void> execute(List<T> members, Function<T, CompletableFuture<Boolean>> attach,
                                                       Function<T, CompletableFuture<Void>> detach) {
        List<T> safeMembers = members != null ? List.copyOf(members) : List.of();
        List<T> attached = new ArrayList<>();
        CompletableFuture<Void> transaction = CompletableFuture.completedFuture(null);
        for (T member : safeMembers) {
            transaction = transaction.thenCompose(unused -> future(attach, member).thenAccept(changed -> {
                if (Boolean.TRUE.equals(changed)) {
                    attached.add(member);
                }
            }));
        }
        return transaction.handle((unused, failure) -> failure == null
            ? CompletableFuture.<Void>completedFuture(null)
            : rollback(attached, detach, unwrap(failure))).thenCompose(Function.identity());
    }

    private static <T> CompletableFuture<Void> rollback(List<T> attached, Function<T, CompletableFuture<Void>> detach, Throwable failure) {
        CompletableFuture<Void> rollback = CompletableFuture.completedFuture(null);
        for (int index = attached.size() - 1; index >= 0; index--) {
            T member = attached.get(index);
            rollback = rollback.thenCompose(unused -> future(detach, member).handle((ignored, rollbackFailure) -> {
                if (rollbackFailure != null) {
                    Throwable rollbackCause = unwrap(rollbackFailure);
                    if (rollbackCause != failure) {
                        failure.addSuppressed(rollbackCause);
                    }
                }
                return null;
            }));
        }
        return rollback.thenCompose(unused -> CompletableFuture.failedFuture(failure));
    }

    private static <T, R> CompletableFuture<R> future(Function<T, CompletableFuture<R>> action, T value) {
        try {
            CompletableFuture<R> future = action.apply(value);
            return future != null ? future : CompletableFuture.failedFuture(new IllegalStateException("Network group action returned no result"));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
