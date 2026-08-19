package redxax.oxy.remotely.web.platform;

import org.teavm.jso.JSBody;
import restudio.rescreen.platform.TaskScheduler;
import restudio.rescreen.platform.browser.BrowserRuntimeDiagnostics;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BrowserTaskScheduler implements TaskScheduler {
    private static final Map<Integer, BrowserScheduledTask> TASKS = new HashMap<>();
    private static int nextTaskId = 1;
    private static long diagnosticsSequence;
    private static BrowserRuntimeDiagnostics diagnostics = BrowserRuntimeDiagnostics.NONE;
    private final Set<Integer> ownedTasks = new HashSet<>();

    public static void setDiagnostics(BrowserRuntimeDiagnostics sink) {
        diagnostics = sink == null ? BrowserRuntimeDiagnostics.NONE : sink;
    }

    @Override
    public void execute(Runnable task) {
        schedule(task, Duration.ZERO);
    }

    @Override
    public ScheduledTask schedule(Runnable task, Duration delay) {
        if (task == null) {
            return BrowserScheduledTask.cancelled();
        }
        int taskId = nextId();
        BrowserScheduledTask scheduledTask = new BrowserScheduledTask(this, taskId, false, task);
        TASKS.put(taskId, scheduledTask);
        ownedTasks.add(taskId);
        scheduledTask.nativeHandle = setTimeout(taskId, delayMillis(delay));
        return scheduledTask;
    }

    @Override
    public ScheduledTask scheduleAtFixedRate(Runnable task, Duration initialDelay, Duration period) {
        if (task == null) {
            return BrowserScheduledTask.cancelled();
        }
        int taskId = nextId();
        BrowserScheduledTask scheduledTask = new BrowserScheduledTask(this, taskId, true, task);
        TASKS.put(taskId, scheduledTask);
        ownedTasks.add(taskId);
        scheduledTask.nativeHandle = scheduleInterval(taskId, delayMillis(initialDelay), Math.max(1L, delayMillis(period)));
        return scheduledTask;
    }

    public static void run(int taskId) {
        BrowserScheduledTask task = TASKS.get(taskId);
        if (task == null || task.cancelled) {
            return;
        }
        if (!task.repeating) {
            TASKS.remove(taskId);
            task.owner.ownedTasks.remove(taskId);
        }
        try {
            task.action.run();
        } catch (RuntimeException | Error failure) {
            reportFailure(task, failure);
            if (task.repeating) task.cancel();
            throw failure;
        }
    }

    private static void reportFailure(BrowserScheduledTask task, Throwable failure) {
        try {
            ReLog.logger(LogTypes.USER_INTERFACE)
                    .source(LogSource.application("Remotely"))
                    .component("BrowserTaskScheduler")
                    .operation("Browser Scheduled Task")
                    .with("failure", failureType(failure))
                    .with("stack", stackTrace(failure))
                    .error("Scheduled task failed");
        } catch (RuntimeException ignored) {
        }
        BrowserRuntimeDiagnostics sink = diagnostics;
        if (sink != null && sink != BrowserRuntimeDiagnostics.NONE) {
            try {
                sink.report(new BrowserRuntimeDiagnostics.Event("", "", "scheduled-task", "scheduler-" + ++diagnosticsSequence,
                        failureType(failure), failureDescription(failure), stackTrace(failure), TASKS.size(), 0, 0));
            } catch (Throwable ignored) {
            }
        }
    }

    private static String failureType(Throwable failure) {
        if (failure == null) {
            return "Exception";
        }
        if (failure instanceof IllegalArgumentException) {
            return "IllegalArgumentException";
        }
        if (failure instanceof IllegalStateException) {
            return "IllegalStateException";
        }
        if (failure instanceof IndexOutOfBoundsException) {
            return "IndexOutOfBoundsException";
        }
        if (failure instanceof NullPointerException) {
            return "NullPointerException";
        }
        if (failure instanceof SecurityException) {
            return "SecurityException";
        }
        if (failure instanceof RuntimeException) {
            return "RuntimeException";
        }
        if (failure instanceof Error) {
            return "Error";
        }
        return "Exception";
    }

    private static String failureDescription(Throwable failure) {
        if (failure == null) {
            return "Exception";
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failureType(failure) : message;
    }

    private static String stackTrace(Throwable failure) {
        StringBuilder stack = new StringBuilder();
        for (StackTraceElement element : failure.getStackTrace()) {
            if (!stack.isEmpty()) {
                stack.append('\n');
            }
            stack.append(element.getClassName()).append('.').append(element.getMethodName());
            if (element.getLineNumber() >= 0) {
                stack.append(':').append(element.getLineNumber());
            }
            if (stack.length() >= 2048) {
                break;
            }
        }
        return stack.toString();
    }

    public void cancelAll() {
        for (Integer taskId : Set.copyOf(ownedTasks)) {
            BrowserScheduledTask task = TASKS.get(taskId);
            if (task != null) {
                task.cancel();
            } else {
                ownedTasks.remove(taskId);
            }
        }
    }

    private static int nextId() {
        int taskId = nextTaskId++;
        if (taskId <= 0) {
            nextTaskId = 2;
            taskId = 1;
        }
        return taskId;
    }

    private static long delayMillis(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return 0L;
        }
        return Math.max(0L, duration.toMillis());
    }

    @JSBody(params = {"taskId", "delay"}, script = "return window.setTimeout(function() { javaMethods.get('redxax.oxy.remotely.web.platform.BrowserTaskScheduler.run(I)V').invoke(taskId); }, Number(delay));")
    private static native int setTimeout(int taskId, long delay);

    @JSBody(params = {"taskId", "initialDelay", "period"}, script = "var key = String(taskId); var state = window.__remotelyTaskHandles || (window.__remotelyTaskHandles = {}); var initial = window.setTimeout(function() { var current = state[key]; if (!current || current.initial !== initial) return; var interval = window.setInterval(function() { javaMethods.get('redxax.oxy.remotely.web.platform.BrowserTaskScheduler.run(I)V').invoke(taskId); }, Number(period)); state[key] = {initial: 0, interval: interval}; javaMethods.get('redxax.oxy.remotely.web.platform.BrowserTaskScheduler.run(I)V').invoke(taskId); }, Number(initialDelay)); state[key] = {initial: initial, interval: 0}; return initial;")
    private static native int scheduleInterval(int taskId, long initialDelay, long period);

    @JSBody(params = {"taskId", "nativeHandle"}, script = "window.clearTimeout(nativeHandle); window.clearInterval(nativeHandle); const state = window.__remotelyTaskHandles; const entry = state && state[String(taskId)]; if (entry) { window.clearTimeout(entry.initial); window.clearInterval(entry.interval); delete state[String(taskId)]; }")
    private static native void clearNativeHandle(int taskId, int nativeHandle);

    private static final class BrowserScheduledTask implements ScheduledTask {
        private final BrowserTaskScheduler owner;
        private final int taskId;
        private final boolean repeating;
        private final Runnable action;
        private int nativeHandle;
        private boolean cancelled;

        private BrowserScheduledTask(BrowserTaskScheduler owner, int taskId, boolean repeating, Runnable action) {
            this.owner = owner;
            this.taskId = taskId;
            this.repeating = repeating;
            this.action = action;
        }

        private static BrowserScheduledTask cancelled() {
            BrowserScheduledTask task = new BrowserScheduledTask(null, 0, false, () -> {});
            task.cancelled = true;
            return task;
        }

        @Override
        public boolean cancel() {
            if (cancelled) {
                return false;
            }
            cancelled = true;
            TASKS.remove(taskId);
            if (owner != null) {
                owner.ownedTasks.remove(taskId);
            }
            clearNativeHandle(taskId, nativeHandle);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
