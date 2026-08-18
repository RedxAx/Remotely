package redxax.oxy.remotely.util;

import restudio.rebase.platform.TaskScheduler;

import java.util.Objects;

public final class TaskSchedulers {
    private static volatile TaskScheduler current = TaskScheduler.unavailable();

    private TaskSchedulers() {
    }

    public static TaskScheduler current() {
        return current;
    }

    public static void configure(TaskScheduler scheduler) {
        current = Objects.requireNonNullElseGet(scheduler, TaskScheduler::unavailable);
    }
}
