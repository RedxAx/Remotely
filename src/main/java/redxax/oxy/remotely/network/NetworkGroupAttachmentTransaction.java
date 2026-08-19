package redxax.oxy.remotely.network;

import java.util.ArrayList;
import java.util.List;
import restudio.rescreen.platform.Async;

import java.util.function.Function;

public final class NetworkGroupAttachmentTransaction {
    private NetworkGroupAttachmentTransaction() {
    }

    public static <T> Async<Void> execute(List<T> members, Function<T, Async<Boolean>> attach,
                                                       Function<T, Async<Void>> detach) {
        List<T> safeMembers = members != null ? List.copyOf(members) : List.of();
        List<T> attached = new ArrayList<>();
        Async<Void> transaction = Async.completed(null);
        for (T member : safeMembers) {
            transaction = transaction.thenCompose(unused -> future(attach, member).thenAccept(changed -> {
                if (Boolean.TRUE.equals(changed)) {
                    attached.add(member);
                }
            }));
        }
        return transaction.handle((unused, failure) -> failure == null
            ? Async.<Void>completedFuture(null)
            : rollback(attached, detach, unwrap(failure))).thenCompose(Function.identity());
    }

    private static <T> Async<Void> rollback(List<T> attached, Function<T, Async<Void>> detach, Throwable failure) {
        Async<Void> rollback = Async.completed(null);
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
        return rollback.thenCompose(unused -> Async.failed(failure));
    }

    private static <T, R> Async<R> future(Function<T, Async<R>> action, T value) {
        try {
            Async<R> future = action.apply(value);
            return future != null ? future : Async.failed(new IllegalStateException("Network group action returned no result"));
        } catch (RuntimeException failure) {
            return Async.failed(failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
