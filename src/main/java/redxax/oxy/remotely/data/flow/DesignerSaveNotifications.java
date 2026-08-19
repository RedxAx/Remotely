package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.util.BrowserSafeState;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.Clock;
import restudio.rescreen.platform.TaskScheduler;

import java.time.Duration;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class DesignerSaveNotifications {
    private static final Map<String, PendingSave> pendingByKey = BrowserSafeState.map();
    private static final Map<String, Deque<String>> pendingKeysByResource = BrowserSafeState.map();
    private static final Map<String, String> pendingKeyByRequest = BrowserSafeState.map();
    private static final Map<String, Long> recentlyHandledErrors = BrowserSafeState.map();
    private static final Map<String, TimedOutSave> recentlyTimedOut = BrowserSafeState.map();
    private static final Set<String> suppressedRequestIds = BrowserSafeState.set();
    private static final Object automaticNotificationMonitor = new Object();
    private static int automaticNotificationSuppression;
    private static final BrowserSafeState.LongValue pendingSequence = new BrowserSafeState.LongValue();
    private static final long ERROR_DEDUPLICATION_MS = 3000L;
    private static final Duration SAVE_TIMEOUT = Duration.ofSeconds(30L);
    private static volatile TaskScheduler scheduler = TaskScheduler.unavailable();
    private static volatile Clock clock = Clock.system();
    private static volatile ReSyncConnectionNotificationSink notificationSink = ReSyncConnectionNotificationSink.noop();
    private static volatile DocumentStateSink documentStateSink = DocumentStateSink.noop();

    private DesignerSaveNotifications() {
    }

    public static void configure(TaskScheduler taskScheduler, Clock saveClock,
                                 ReSyncConnectionNotificationSink saveNotificationSink,
                                 DocumentStateSink saveDocumentStateSink) {
        scheduler = taskScheduler == null ? TaskScheduler.unavailable() : taskScheduler;
        clock = saveClock == null ? Clock.system() : saveClock;
        notificationSink = saveNotificationSink == null ? ReSyncConnectionNotificationSink.noop() : saveNotificationSink;
        documentStateSink = saveDocumentStateSink == null ? DocumentStateSink.noop() : saveDocumentStateSink;
    }

    public static void start(String serverId, ReSyncResourceType type, String id, String name) {
        begin(serverId, type, id, name);
    }

    public static Async<Boolean> track(String serverId, ReSyncResourceType type, String id, String name) {
        return begin(serverId, type, id, name);
    }

    public static <T> T withoutAutomaticNotifications(Supplier<T> action) {
        synchronized (automaticNotificationMonitor) {
            automaticNotificationSuppression++;
            try {
                return action.get();
            } finally {
                automaticNotificationSuppression--;
            }
        }
    }

    public static boolean consumeAutomaticNotificationSuppression(String requestId) {
        return requestId != null && !requestId.isBlank() && suppressedRequestIds.remove(requestId);
    }

    private static Async<Boolean> begin(String serverId, ReSyncResourceType type, String id, String name) {
        if (!shouldTrack(serverId, type, id)) {
            return Async.completed(false);
        }
        String resourceKey = key(serverId, type, id);
        long sequence = pendingSequence.incrementAndGet();
        String pendingKey = pendingKey(resourceKey, sequence);
        PendingSave pending = new PendingSave(serverId, type, id, resourceKey, sequence);
        pendingByKey.put(pendingKey, pending);
        pendingKeysByResource.computeIfAbsent(resourceKey, ignored -> BrowserSafeState.deque()).add(pendingKey);
        pending.name = cleanName(name, id);
        documentStateSink.markSaving(serverId, type.typeId(), id, sequence);
        long timeoutToken = pending.nextTimeoutToken();
        schedule(() -> timeout(pendingKey, timeoutToken), SAVE_TIMEOUT);
        pending.showSaving();
        return pending.completion;
    }

    public static void attachRequestId(String serverId, ReSyncResourceType type, String id, String requestId) {
        if (!shouldTrack(serverId, type, id) || requestId == null || requestId.isBlank()) {
            return;
        }
        boolean suppress;
        synchronized (automaticNotificationMonitor) {
            suppress = automaticNotificationSuppression > 0;
        }
        if (suppress && suppressedRequestIds.add(requestId)) {
            schedule(() -> suppressedRequestIds.remove(requestId), SAVE_TIMEOUT);
        }
        if (pendingKeyByRequest.containsKey(requestId)) {
            return;
        }
        String resourceKey = key(serverId, type, id);
        String pendingKey = findUnboundPendingKey(resourceKey);
        if (pendingKey == null) {
            pendingKey = resourceKey;
        } else {
            PendingSave pending = pendingByKey.get(pendingKey);
            if (pending != null) {
                pending.requestId = requestId;
            }
        }
        pendingKeyByRequest.put(requestId, pendingKey);
    }

    public static SaveTarget complete(String serverId, ReSyncResourceType type, String id) {
        return complete(serverId, type, id, null);
    }

    public static SaveTarget complete(String serverId, ReSyncResourceType type, String id, String requestId) {
        String key = key(serverId, type, id);
        SaveTarget target;
        if (requestId == null || requestId.isBlank()) {
            target = finish(key, type.displayName() + " Saved", "ID: " + id, ReSyncNotificationLevel.SUCCESS, null);
        } else {
            String pendingKey = pendingKeyByRequest.remove(requestId);
            if (pendingKey != null && pendingByKey.containsKey(pendingKey)) {
                target = finish(pendingKey, type.displayName() + " Saved", "ID: " + id, ReSyncNotificationLevel.SUCCESS, null);
            } else if (hasPending(key)) {
                return new SaveTarget(type, id, false, 0L);
            } else {
                target = null;
            }
        }
        if (target != null) {
            return target;
        }
        TimedOutSave timedOut = recentlyTimedOut.remove(key);
        if (timedOut != null && clock.millis() - timedOut.timedOutAt() <= SAVE_TIMEOUT.toMillis()) {
            return new SaveTarget(type, id, true, timedOut.sequence());
        }
        return null;
    }

    public static SaveTarget failResource(String serverId, ReSyncResourceType type, String id, String message) {
        return finish(key(serverId, type, id), type.displayName() + " Save Failed", cleanMessage(message), ReSyncNotificationLevel.ERROR, cleanMessage(message));
    }

    public static SaveTarget failRequest(String serverId, String requestId, String message) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        String key = pendingKeyByRequest.remove(requestId);
        if (key == null) {
            key = keyFromRequest(serverId, requestId);
        }
        if (key == null) {
            return null;
        }
        PendingSave pending = pendingByKey.get(key);
        if (pending == null) {
            pending = pendingByKey.get(firstPendingKey(key));
        }
        String title = pending != null ? pending.type.displayName() + " Save Failed" : "Save Failed";
        return finish(key, title, cleanMessage(message), ReSyncNotificationLevel.ERROR, cleanMessage(message));
    }

    public static SaveTarget failAnyForServer(String serverId, String message) {
        return failAnyForServer(serverId, "", message);
    }

    public static SaveTarget failAnyForServer(String serverId, String title, String message) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        Map.Entry<String, PendingSave> pending = pendingByKey.entrySet().stream()
            .filter(entry -> entry.getValue() != null && serverId.equals(entry.getValue().serverId) && !entry.getValue().finished)
            .max(Comparator.comparingLong(entry -> entry.getValue().updatedAt))
            .orElse(null);
        if (pending == null || pending.getValue() == null) {
            return null;
        }
        PendingSave value = pending.getValue();
        String notificationTitle = title != null && !title.isBlank() ? title : value.type.displayName() + " Save Failed";
        return finish(pending.getKey(), notificationTitle, cleanMessage(message), ReSyncNotificationLevel.ERROR, cleanMessage(message));
    }

    public static boolean consumeRecentError(String serverId, String message) {
        String key = errorKey(serverId, message);
        Long handledAt = recentlyHandledErrors.remove(key);
        return handledAt != null && clock.millis() - handledAt <= ERROR_DEDUPLICATION_MS;
    }

    private static SaveTarget finish(String key, String title, String description, ReSyncNotificationLevel type, String handledError) {
        if (key == null || key.isBlank()) {
            return null;
        }
        PendingSave pending = pendingByKey.remove(key);
        if (pending == null) {
            String pendingKey = firstPendingKey(key);
            pending = pendingKey == null ? null : pendingByKey.remove(pendingKey);
            key = pendingKey;
        }
        if (pending == null) {
            return null;
        }
        boolean shouldUpdateResourceState = !hasNewerPending(pending.resourceKey, pending.sequence);
        removePendingKey(key, pending.resourceKey);
        String finishedKey = key;
        pendingKeyByRequest.values().removeIf(finishedKey::equals);
        pending.finished = true;
        pending.finalTitle = title;
        pending.finalDescription = description;
        pending.finalType = type;
        pending.completion.complete(type == ReSyncNotificationLevel.SUCCESS);
        if (handledError != null && !handledError.isBlank()) {
            recentlyHandledErrors.put(errorKey(pending.serverId, handledError), clock.millis());
        }
        pending.showFinished();
        return new SaveTarget(pending.type, pending.id, shouldUpdateResourceState, pending.sequence);
    }

    private static void timeout(String key, long timeoutToken) {
        PendingSave pending = pendingByKey.get(key);
        if (pending != null && !pending.finished && pending.timeoutToken == timeoutToken) {
            SaveTarget target = finish(key, pending.type.displayName() + " Save Failed", "Save Timed Out", ReSyncNotificationLevel.ERROR, "Save Timed Out");
            recentlyTimedOut.put(pending.resourceKey, new TimedOutSave(clock.millis(), target != null ? target.sequence() : 0L));
            FlowManager manager = FlowManager.getInstance();
            if (target != null && target.shouldUpdateResourceState() && manager != null) {
                manager.markResourceSaveFailed(pending.serverId, target.type(), target.id());
            }
        }
    }

    private static String keyFromRequest(String serverId, String requestId) {
        String[] parts = requestId.split(":", 4);
        if (parts.length < 4 || !parts[0].startsWith("remotely-")) {
            return null;
        }
        ReSyncResourceType type = typeByDisplayName(parts[1]);
        if (!shouldTrack(serverId, type, parts[2])) {
            return null;
        }
        return key(serverId, type, parts[2]);
    }

    private static ReSyncResourceType typeByDisplayName(String displayName) {
        for (ReSyncResourceType type : ReSyncResourceType.values()) {
            if (type.displayName().equals(displayName)) {
                return type;
            }
        }
        return null;
    }

    private static boolean shouldTrack(String serverId, ReSyncResourceType type, String id) {
        return serverId != null && !serverId.isBlank()
            && type != null
            && type != ReSyncResourceType.PROJECT_METADATA
            && id != null
            && !id.isBlank();
    }

    private static String key(String serverId, ReSyncResourceType type, String id) {
        return serverId + ":" + type.typeId() + ":" + id;
    }

    private static String pendingKey(String resourceKey, long sequence) {
        return resourceKey + "\n" + sequence;
    }

    private static String findUnboundPendingKey(String resourceKey) {
        Deque<String> keys = pendingKeysByResource.get(resourceKey);
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            PendingSave pending = pendingByKey.get(key);
            if (pending != null && !pending.finished && (pending.requestId == null || pending.requestId.isBlank())) {
                return key;
            }
        }
        return null;
    }

    private static String firstPendingKey(String resourceKey) {
        Deque<String> keys = pendingKeysByResource.get(resourceKey);
        if (keys == null) {
            return null;
        }
        while (true) {
            String key = keys.peekFirst();
            if (key == null) {
                pendingKeysByResource.remove(resourceKey, keys);
                return null;
            }
            PendingSave pending = pendingByKey.get(key);
            if (pending != null && !pending.finished) {
                return key;
            }
            keys.pollFirst();
        }
    }

    private static void removePendingKey(String pendingKey, String resourceKey) {
        Deque<String> keys = pendingKeysByResource.get(resourceKey);
        if (keys == null) {
            return;
        }
        keys.remove(pendingKey);
        if (keys.isEmpty()) {
            pendingKeysByResource.remove(resourceKey, keys);
        }
    }

    private static boolean hasNewerPending(String resourceKey, long sequence) {
        Deque<String> keys = pendingKeysByResource.get(resourceKey);
        if (keys == null) {
            return false;
        }
        for (String key : keys) {
            PendingSave pending = pendingByKey.get(key);
            if (pending != null && !pending.finished && pending.sequence > sequence) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPending(String resourceKey) {
        Deque<String> keys = pendingKeysByResource.get(resourceKey);
        if (keys == null) {
            return false;
        }
        for (String key : keys) {
            PendingSave pending = pendingByKey.get(key);
            if (pending != null && !pending.finished) {
                return true;
            }
        }
        return false;
    }

    private static String errorKey(String serverId, String message) {
        return (serverId == null ? "" : serverId) + "\n" + cleanMessage(message);
    }

    private static String cleanName(String name, String fallback) {
        String value = name == null ? "" : name.trim();
        return value.isBlank() ? fallback : value;
    }

    private static String cleanMessage(String message) {
        String value = message == null ? "" : message.trim();
        return value.isBlank() ? "Failed" : value;
    }

    public record SaveTarget(ReSyncResourceType type, String id, boolean shouldUpdateResourceState, long sequence) {
    }

    private static void schedule(Runnable action, Duration delay) {
        try {
            scheduler.schedule(action, delay);
        } catch (RuntimeException ignored) {
        }
    }

    private record TimedOutSave(long timedOutAt, long sequence) {
    }

    private static final class PendingSave {
        private final String serverId;
        private final ReSyncResourceType type;
        private final String id;
        private final String resourceKey;
        private final long sequence;
        private final Async<Boolean> completion = Async.pending();
        private final ReSyncConnectionNotificationSink notificationSink = DesignerSaveNotifications.notificationSink;
        private long updatedAt = clock.millis();
        private long timeoutToken;
        private String requestId;
        private String name;
        private boolean finished;
        private String finalTitle;
        private String finalDescription;
        private ReSyncNotificationLevel finalType;

        private PendingSave(String serverId, ReSyncResourceType type, String id, String resourceKey, long sequence) {
            this.serverId = serverId;
            this.type = type;
            this.id = id;
            this.resourceKey = resourceKey;
            this.sequence = sequence;
            this.name = id;
        }

        private long nextTimeoutToken() {
            updatedAt = clock.millis();
            return ++timeoutToken;
        }

        private void showSaving() {
            if (finished) {
                showFinished();
                return;
            }
            String description = name == null || name.isBlank() ? id : name;
            notificationSink.show("Saving " + type.displayName(), description, ReSyncNotificationLevel.INFO);
        }

        private void showFinished() {
            notificationSink.show(finalTitle, finalDescription, finalType);
        }
    }

    @FunctionalInterface
    public interface DocumentStateSink {
        void markSaving(String serverId, String resourceType, String resourceId, long sequence);

        static DocumentStateSink noop() {
            return (serverId, resourceType, resourceId, sequence) -> {
            };
        }
    }
}
