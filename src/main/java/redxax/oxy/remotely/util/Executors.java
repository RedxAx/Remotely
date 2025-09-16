package redxax.oxy.remotely.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class Executors {

    private static final ExecutorService IO_EXECUTOR = java.util.concurrent.Executors.newCachedThreadPool(new RemotelyThreadFactory("Remotely-IO-"));

    public static ExecutorService get() {
        return IO_EXECUTOR;
    }

    private static class RemotelyThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public RemotelyThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}