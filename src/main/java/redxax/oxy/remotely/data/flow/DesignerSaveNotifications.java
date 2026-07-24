package redxax.oxy.remotely.data.flow;

import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class DesignerSaveNotifications {
    private static final Map<String, PendingSave> pendingByKey = new ConcurrentHashMap<>();
    private static final Map<String, ConcurrentLinkedDeque<String>> pendingKeysByResource = new ConcurrentHashMap<>();
    private static final Map<String, String> pendingKeyByRequest = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentlyHandledErrors = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentlyTimedOut = new ConcurrentHashMap<>();
    private static final Set<String> suppressedRequestIds = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Integer> automaticNotificationSuppression = ThreadLocal.withInitial(() -> 0);
    private static final AtomicLong pendingSequence = new AtomicLong();
    private static final long ERROR_DEDUPLICATION_MS = 3000L;
    private static final long SAVE_TIMEOUT_SECONDS = 30L;

    private DesignerSaveNotifications() {
    }

    public static void start(String serverId, ReSyncResourceType type, String id, String name) {
        begin(serverId, type, id, name);
    }

    public static CompletableFuture<Boolean> track(String serverId, ReSyncResourceType type, String id, String name) {
        return begin(serverId, type, id, name);
    }

    public static <T> T withoutAutomaticNotifications(Supplier<T> action) {
        automaticNotificationSuppression.set(automaticNotificationSuppression.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = automaticNotificationSuppression.get() - 1;
            if (depth == 0) automaticNotificationSuppression.remove();
            else automaticNotificationSuppression.set(depth);
        }
    }

    public static boolean consumeAutomaticNotificationSuppression(String requestId) {
        return requestId != null && !requestId.isBlank() && suppressedRequestIds.remove(requestId);
    }

    private static CompletableFuture<Boolean> begin(String serverId, ReSyncResourceType type, String id, String name) {
        if (!shouldTrack(serverId, type, id)) {
            return CompletableFuture.completedFuture(false);
        }
        String resourceKey = key(serverId, type, id);
        long sequence = pendingSequence.incrementAndGet();
        String pendingKey = pendingKey(resourceKey, sequence);
        PendingSave pending = new PendingSave(serverId, type, id, resourceKey, sequence);
        pendingByKey.put(pendingKey, pending);
        pendingKeysByResource.computeIfAbsent(resourceKey, ignored -> new ConcurrentLinkedDeque<>()).add(pendingKey);
        pending.name = cleanName(name, id);
        long timeoutToken = pending.nextTimeoutToken();
        CompletableFuture.delayedExecutor(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> timeout(pendingKey, timeoutToken));
        ScreenManager.getInstance().execute(pending::showSaving);
        return pending.completion;
    }

    public static void attachRequestId(String serverId, ReSyncResourceType type, String id, String requestId) {
        if (!shouldTrack(serverId, type, id) || requestId == null || requestId.isBlank()) {
            return;
        }
        if (automaticNotificationSuppression.get() > 0 && suppressedRequestIds.add(requestId)) {
            CompletableFuture.delayedExecutor(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> suppressedRequestIds.remove(requestId));
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
            target = finish(key, type.displayName() + " Saved", "ID: " + id, Notification.Type.SUCCESS, null);
        } else {
            String pendingKey = pendingKeyByRequest.remove(requestId);
            if (pendingKey != null && pendingByKey.containsKey(pendingKey)) {
                target = finish(pendingKey, type.displayName() + " Saved", "ID: " + id, Notification.Type.SUCCESS, null);
            } else if (hasPending(key)) {
                return new SaveTarget(type, id, false);
            } else {
                target = null;
            }
        }
        if (target != null) {
            return target;
        }
        Long timedOutAt = recentlyTimedOut.remove(key);
        if (timedOutAt != null && System.currentTimeMillis() - timedOutAt <= SAVE_TIMEOUT_SECONDS * 1000L) {
            return new SaveTarget(type, id, true);
        }
        return null;
    }

    public static SaveTarget failResource(String serverId, ReSyncResourceType type, String id, String message) {
        return finish(key(serverId, type, id), type.displayName() + " Save Failed", cleanMessage(message), Notification.Type.ERROR, cleanMessage(message));
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
        return finish(key, title, cleanMessage(message), Notification.Type.ERROR, cleanMessage(message));
    }

    public static SaveTarget failAnyForServer(String serverId, String message) {
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
        return finish(pending.getKey(), value.type.displayName() + " Save Failed", cleanMessage(message), Notification.Type.ERROR, cleanMessage(message));
    }

    public static boolean consumeRecentError(String serverId, String message) {
        String key = errorKey(serverId, message);
        Long handledAt = recentlyHandledErrors.remove(key);
        return handledAt != null && System.currentTimeMillis() - handledAt <= ERROR_DEDUPLICATION_MS;
    }

    private static SaveTarget finish(String key, String title, String description, Notification.Type type, String handledError) {
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
        pending.completion.complete(type == Notification.Type.SUCCESS);
        if (handledError != null && !handledError.isBlank()) {
            recentlyHandledErrors.put(errorKey(pending.serverId, handledError), System.currentTimeMillis());
        }
        ScreenManager.getInstance().execute(pending::showFinished);
        return new SaveTarget(pending.type, pending.id, shouldUpdateResourceState);
    }

    private static void timeout(String key, long timeoutToken) {
        PendingSave pending = pendingByKey.get(key);
        if (pending != null && !pending.finished && pending.timeoutToken == timeoutToken) {
            SaveTarget target = finish(key, pending.type.displayName() + " Save Failed", "Save Timed Out", Notification.Type.ERROR, "Save Timed Out");
            recentlyTimedOut.put(pending.resourceKey, System.currentTimeMillis());
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
        ConcurrentLinkedDeque<String> keys = pendingKeysByResource.get(resourceKey);
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
        ConcurrentLinkedDeque<String> keys = pendingKeysByResource.get(resourceKey);
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
        ConcurrentLinkedDeque<String> keys = pendingKeysByResource.get(resourceKey);
        if (keys == null) {
            return;
        }
        keys.remove(pendingKey);
        if (keys.isEmpty()) {
            pendingKeysByResource.remove(resourceKey, keys);
        }
    }

    private static boolean hasNewerPending(String resourceKey, long sequence) {
        ConcurrentLinkedDeque<String> keys = pendingKeysByResource.get(resourceKey);
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
        ConcurrentLinkedDeque<String> keys = pendingKeysByResource.get(resourceKey);
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

    public record SaveTarget(ReSyncResourceType type, String id, boolean shouldUpdateResourceState) {
    }

    private static final class PendingSave {
        private final String serverId;
        private final ReSyncResourceType type;
        private final String id;
        private final String resourceKey;
        private final long sequence;
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();
        private long updatedAt = System.currentTimeMillis();
        private long timeoutToken;
        private String requestId;
        private String name;
        private Notification notification;
        private boolean finished;
        private String finalTitle;
        private String finalDescription;
        private Notification.Type finalType;

        private PendingSave(String serverId, ReSyncResourceType type, String id, String resourceKey, long sequence) {
            this.serverId = serverId;
            this.type = type;
            this.id = id;
            this.resourceKey = resourceKey;
            this.sequence = sequence;
            this.name = id;
        }

        private long nextTimeoutToken() {
            updatedAt = System.currentTimeMillis();
            return ++timeoutToken;
        }

        private void showSaving() {
            if (finished) {
                showFinished();
                return;
            }
            String description = name == null || name.isBlank() ? id : name;
            if (notification == null) {
                notification = new Notification.Builder()
                    .message("Saving " + type.displayName())
                    .description(description)
                    .type(Notification.Type.INFO)
                    .loading(true)
                    .autoSlideOut(false)
                    .build();
                return;
            }
            notification.update()
                .message("Saving " + type.displayName())
                .description(description)
                .type(Notification.Type.INFO)
                .loading(true)
                .autoSlideOut(false);
        }

        private void showFinished() {
            if (notification == null) {
                notification = new Notification.Builder()
                    .message(finalTitle)
                    .description(finalDescription)
                    .type(finalType)
                    .build();
                return;
            }
            notification.change(finalTitle, finalDescription, finalType, null);
        }
    }
}
