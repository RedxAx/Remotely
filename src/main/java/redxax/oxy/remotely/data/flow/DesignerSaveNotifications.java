package redxax.oxy.remotely.data.flow;

import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

public final class DesignerSaveNotifications {
    private static final Map<String, PendingSave> pendingByResource = new ConcurrentHashMap<>();
    private static final Map<String, String> resourceByRequest = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentlyHandledErrors = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentlyTimedOut = new ConcurrentHashMap<>();
    private static final long ERROR_DEDUPLICATION_MS = 3000L;
    private static final long SAVE_TIMEOUT_SECONDS = 30L;

    private DesignerSaveNotifications() {
    }

    public static void start(String serverId, ReSyncResourceType type, String id, String name) {
        if (!shouldTrack(serverId, type, id)) {
            return;
        }
        String key = key(serverId, type, id);
        PendingSave pending = pendingByResource.compute(key, (ignored, existing) -> existing != null && !existing.finished ? existing : new PendingSave(serverId, type, id));
        pending.name = cleanName(name, id);
        long timeoutToken = pending.nextTimeoutToken();
        CompletableFuture.delayedExecutor(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> timeout(key, timeoutToken));
        ScreenManager.getInstance().execute(pending::showSaving);
    }

    public static void attachRequestId(String serverId, ReSyncResourceType type, String id, String requestId) {
        if (!shouldTrack(serverId, type, id) || requestId == null || requestId.isBlank()) {
            return;
        }
        resourceByRequest.put(requestId, key(serverId, type, id));
    }

    public static SaveTarget complete(String serverId, ReSyncResourceType type, String id) {
        String key = key(serverId, type, id);
        SaveTarget target = finish(key, type.displayName() + " Saved", "ID: " + id, Notification.Type.SUCCESS, null);
        if (target != null) {
            return target;
        }
        Long timedOutAt = recentlyTimedOut.remove(key);
        if (timedOutAt != null && System.currentTimeMillis() - timedOutAt <= SAVE_TIMEOUT_SECONDS * 1000L) {
            return new SaveTarget(type, id);
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
        String key = resourceByRequest.remove(requestId);
        if (key == null) {
            key = keyFromRequest(serverId, requestId);
        }
        if (key == null) {
            return null;
        }
        PendingSave pending = pendingByResource.get(key);
        String title = pending != null ? pending.type.displayName() + " Save Failed" : "Save Failed";
        return finish(key, title, cleanMessage(message), Notification.Type.ERROR, cleanMessage(message));
    }

    public static SaveTarget failAnyForServer(String serverId, String message) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        PendingSave pending = pendingByResource.values().stream()
            .filter(value -> value != null && serverId.equals(value.serverId) && !value.finished)
            .max(Comparator.comparingLong(value -> value.updatedAt))
            .orElse(null);
        if (pending == null) {
            return null;
        }
        return failResource(pending.serverId, pending.type, pending.id, message);
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
        PendingSave pending = pendingByResource.remove(key);
        if (pending == null) {
            return null;
        }
        resourceByRequest.entrySet().removeIf(entry -> key.equals(entry.getValue()));
        pending.finished = true;
        pending.finalTitle = title;
        pending.finalDescription = description;
        pending.finalType = type;
        if (handledError != null && !handledError.isBlank()) {
            recentlyHandledErrors.put(errorKey(pending.serverId, handledError), System.currentTimeMillis());
        }
        ScreenManager.getInstance().execute(pending::showFinished);
        return new SaveTarget(pending.type, pending.id);
    }

    private static void timeout(String key, long timeoutToken) {
        PendingSave pending = pendingByResource.get(key);
        if (pending != null && !pending.finished && pending.timeoutToken == timeoutToken) {
            SaveTarget target = finish(key, pending.type.displayName() + " Save Failed", "Save Timed Out", Notification.Type.ERROR, "Save Timed Out");
            recentlyTimedOut.put(key, System.currentTimeMillis());
            FlowManager manager = FlowManager.getInstance();
            if (target != null && manager != null) {
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

    public record SaveTarget(ReSyncResourceType type, String id) {
    }

    private static final class PendingSave {
        private final String serverId;
        private final ReSyncResourceType type;
        private final String id;
        private long updatedAt = System.currentTimeMillis();
        private long timeoutToken;
        private String name;
        private Notification notification;
        private boolean finished;
        private String finalTitle;
        private String finalDescription;
        private Notification.Type finalType;

        private PendingSave(String serverId, ReSyncResourceType type, String id) {
            this.serverId = serverId;
            this.type = type;
            this.id = id;
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
