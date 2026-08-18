package redxax.oxy.remotely.util;

import restudio.rebase.platform.Async;
import restudio.rebase.platform.TaskScheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class AsyncTools {
    private AsyncTools() {
    }

    public static Async<Void> run(TaskScheduler scheduler, Runnable task) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(task, "task");
        Async<Void> result = Async.pending();
        try {
            scheduler.execute(() -> {
                try {
                    task.run();
                    result.complete(null);
                } catch (Throwable failure) {
                    result.fail(failure);
                }
            });
        } catch (Throwable failure) {
            result.fail(failure);
        }
        return result;
    }

    public static <T> Async<T> supply(TaskScheduler scheduler, Supplier<? extends T> supplier) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(supplier, "supplier");
        Async<T> result = Async.pending();
        try {
            scheduler.execute(() -> {
                try {
                    result.complete(supplier.get());
                } catch (Throwable failure) {
                    result.fail(failure);
                }
            });
        } catch (Throwable failure) {
            result.fail(failure);
        }
        return result;
    }

    public static Async<Void> delay(TaskScheduler scheduler, Duration delay) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(delay, "delay");
        Async<Void> result = Async.pending();
        try {
            TaskScheduler.ScheduledTask task = scheduler.schedule(() -> result.complete(null), delay);
            result.onCancel(task::cancel);
        } catch (Throwable failure) {
            result.fail(failure);
        }
        return result;
    }

    public static TaskScheduler.ScheduledTask schedule(TaskScheduler scheduler, Duration delay, Runnable task) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        return scheduler.schedule(task, delay);
    }

    public static <T> Async<T> withTimeout(Async<T> source, TaskScheduler scheduler, Duration timeout) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(timeout, "timeout");
        Async<T> result = Async.pending();
        TaskScheduler.ScheduledTask[] timeoutTask = new TaskScheduler.ScheduledTask[1];
        try {
            timeoutTask[0] = scheduler.schedule(() -> result.fail(new IllegalStateException("Operation Timed Out")), timeout);
            result.onCancel(() -> {
                source.cancel();
                timeoutTask[0].cancel();
            });
            source.whenComplete((value, failure) -> {
                TaskScheduler.ScheduledTask task = timeoutTask[0];
                if (task != null) task.cancel();
                if (failure == null) result.complete(value);
                else result.fail(failure);
            });
        } catch (Throwable failure) {
            result.fail(failure);
        }
        return result;
    }

    public static <A, B, R> Async<R> combine(Async<A> first, Async<B> second, BiFunction<? super A, ? super B, ? extends R> mapper) {
        return Async.allOf(first, second).thenApply(ignored -> mapper.apply(first.join(), second.join()));
    }
}
