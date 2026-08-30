package redxax.oxy.remotely.data.flow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

import redxax.oxy.remotely.data.flow.world.WorldChannelMessage;
import redxax.oxy.remotely.data.flow.world.WorldDashboardEntry;
import redxax.oxy.remotely.data.flow.world.WorldInventoryGroup;
import redxax.oxy.remotely.data.flow.world.WorldMapSnapshot;
import redxax.oxy.remotely.data.flow.world.WorldOperationResult;
import redxax.oxy.remotely.data.flow.world.WorldProtocolJson;
import redxax.oxy.remotely.data.flow.world.WorldRegistryEntry;
import redxax.oxy.remotely.data.flow.world.WorldSnapshot;
import redxax.oxy.remotely.flow.data.FlowJson;
import redxax.oxy.remotely.util.BrowserSafeState;
import restudio.rescreen.platform.TaskScheduler;

public class ReSyncWorldService {
    private static final long SAVE_TIMEOUT_SECONDS = 30L;
    private final Map<String, WorldSnapshot> worldSnapshotCache = BrowserSafeState.map();
    private final Map<String, WorldMapSnapshot> worldMapSnapshotCache = BrowserSafeState.map();
    private final Map<String, WorldOperationResult> worldOperationCache = BrowserSafeState.map();
    private final Map<String, String> pendingWorldMapRequests = BrowserSafeState.map();
    private final Map<String, Integer> suppressedWorldSuccessNotifications = BrowserSafeState.map();
    private final Map<String, Queue<PendingWorldSave>> pendingWorldSaves = BrowserSafeState.map();
    private final BrowserSafeState.LongValue saveSequences = new BrowserSafeState.LongValue();
    private final TaskScheduler scheduler;
    private volatile ReSyncWorldServiceHooks hooks;

    public ReSyncWorldService() {
        this(TaskScheduler.unavailable(), ReSyncWorldServiceHooks.noop());
    }

    public ReSyncWorldService(TaskScheduler scheduler, ReSyncWorldServiceHooks hooks) {
        this.scheduler = scheduler == null ? TaskScheduler.unavailable() : scheduler;
        this.hooks = hooks == null ? ReSyncWorldServiceHooks.noop() : hooks;
    }

    public void setHooks(ReSyncWorldServiceHooks hooks) {
        this.hooks = hooks == null ? ReSyncWorldServiceHooks.noop() : hooks;
    }

    public Map<String, WorldRegistryEntry> getWorldsForServer(String serverId) {
        Map<String, WorldRegistryEntry> worlds = new LinkedHashMap<>();
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        if (snapshot == null) {
            return worlds;
        }
        for (WorldRegistryEntry world : snapshot.getWorlds()) {
            if (world != null && world.getWorldName() != null) {
                worlds.put(world.getWorldName(), world);
            }
        }
        return worlds;
    }

    public void applyCollaborativeWorld(String serverId, WorldRegistryEntry world) {
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        if (snapshot != null && world != null) {
            upsertSnapshotWorld(snapshot, world);
        }
    }

    public List<WorldDashboardEntry> getWorldDashboardForServer(String serverId) {
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        return snapshot == null ? List.of() : new ArrayList<>(snapshot.getDashboard());
    }

    public List<WorldInventoryGroup> getWorldInventoryGroupsForServer(String serverId) {
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        return snapshot == null ? List.of() : new ArrayList<>(snapshot.getInventoryGroups());
    }

    public WorldInventoryGroup getWorldInventoryGroup(String serverId, String groupId) {
        if (serverId == null || groupId == null || groupId.isBlank()) {
            return null;
        }
        for (WorldInventoryGroup group : getWorldInventoryGroupsForServer(serverId)) {
            if (group != null && group.getGroupId() != null && group.getGroupId().equalsIgnoreCase(groupId)) {
                return group;
            }
        }
        return null;
    }

    public WorldRegistryEntry getWorld(String serverId, String worldName) {
        if (serverId == null || worldName == null) {
            return null;
        }
        return getWorldsForServer(serverId).get(worldName);
    }

    public WorldSnapshot getWorldSnapshot(String serverId) {
        return worldSnapshotCache.get(serverId);
    }

    public WorldOperationResult getLastWorldOperationResult(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        return worldOperationCache.get(serverId);
    }

    public WorldMapSnapshot getWorldMapSnapshot(String serverId, String worldName) {
        if (serverId == null || worldName == null) {
            return null;
        }
        return worldMapSnapshotCache.get(serverId + ":" + worldName.toLowerCase(Locale.ROOT));
    }

    public void clearCache(String serverId) {
        String prefix = serverId + ":";
        worldMapSnapshotCache.keySet().removeIf(key -> key.startsWith(prefix));
        pendingWorldMapRequests.keySet().removeIf(key -> key.startsWith(prefix));
        worldSnapshotCache.remove(serverId);
    }

    public void applyWorldManagementMessage(String serverId, WorldChannelMessage message) {
        if (serverId == null || serverId.isBlank() || message == null) {
            return;
        }
        String action = message.getAction() == null ? "" : message.getAction();
        WorldOperationResult operationResult = null;
        if ("snapshot".equalsIgnoreCase(action) && message.getData() != null) {
            WorldSnapshot snapshot = WorldProtocolJson.readSnapshot(message.getData());
            if (snapshot != null) {
                worldSnapshotCache.put(serverId, snapshot);
                hooks.requestWorldSnapshot(serverId);
            }
            return;
        }
        if ("mapSnapshot".equalsIgnoreCase(action) && message.getData() != null) {
            WorldMapSnapshot snapshot = WorldProtocolJson.readMapSnapshot(message.getData());
            String worldName = pendingWorldMapRequests.remove(serverId);
            if (snapshot != null && worldName != null && !worldName.isBlank()) {
                worldMapSnapshotCache.put(serverId + ":" + worldName.toLowerCase(Locale.ROOT), snapshot);
            }
            return;
        }
        if ("auditSnapshot".equalsIgnoreCase(action) && message.getData() != null) {
            hooks.onAuditSnapshot(serverId, message.getData());
            return;
        }
        if ("error".equalsIgnoreCase(message.getType())) {
            String text = prettyWorldMessage(message.getMessage());
            if (!failPendingWorldSave(serverId, text)) {
                hooks.onWorldSaveFinished(serverId, "", "ReSync", text, ReSyncNotificationLevel.ERROR, 0L);
            }
            return;
        }
        if ("response".equalsIgnoreCase(message.getType()) && "OperationStarted".equalsIgnoreCase(message.getMessage())) {
            return;
        }
        if ("response".equalsIgnoreCase(message.getType()) && message.getData() != null) {
            operationResult = cacheWorldOperationResult(serverId, message);
        }
        if ("response".equalsIgnoreCase(message.getType()) && message.isSuccess() && operationResult != null) {
            applyWorldOperationSnapshotPatch(serverId, operationResult);
        }
        if ("response".equalsIgnoreCase(message.getType()) && !message.isSuccess()) {
            String text = prettyWorldMessage(message.getMessage());
            if (!failPendingWorldSave(serverId, text)) {
                hooks.onWorldSaveFinished(serverId, "", "ReSync", text, ReSyncNotificationLevel.ERROR, 0L);
            }
        }
        if ("response".equalsIgnoreCase(message.getType()) && message.isSuccess() && operationResult != null) {
            dispatchWorldOperationResult(serverId, operationResult);
        }
        if ("response".equalsIgnoreCase(message.getType()) && message.isSuccess() && !"snapshot".equalsIgnoreCase(action) && !"mapSnapshot".equalsIgnoreCase(action)
            && !"setGameRule".equalsIgnoreCase(action)) {
            boolean suppressed = consumeSuppressedWorldSuccessNotification(serverId, action);
            if (suppressed) {
                completePendingWorldSaveStep(serverId);
            } else {
                hooks.onWorldSaveFinished(serverId, "", "ReSync", prettyWorldMessage(message.getMessage()), ReSyncNotificationLevel.SUCCESS, 0L);
            }
        }
        hooks.requestWorldSnapshot(serverId);
    }

    public void requestWorldMapSnapshot(String serverId, String worldName, double centerX, double centerZ, int zoom) {
        if (serverId == null || serverId.isBlank() || worldName == null || worldName.isBlank()) {
            return;
        }
        pendingWorldMapRequests.put(serverId, worldName);
        hooks.requestWorldMapSnapshot(serverId, worldName, centerX, centerZ, zoom);
    }

    public void suppressNextWorldSuccessNotification(String serverId, String action) {
        if (serverId == null || serverId.isBlank() || action == null || action.isBlank()) {
            return;
        }
        suppressedWorldSuccessNotifications.merge(serverId + ":" + action.toLowerCase(Locale.ROOT), 1, Integer::sum);
    }

    public long beginWorldSaveNotification(String serverId, String worldName, int operationCount) {
        long sequence = saveSequences.incrementAndGet();
        return beginWorldOperationNotification(serverId, worldName, operationCount, "Saving World", "World Saved", "World Save Failed", sequence);
    }

    public void beginWorldOperationNotification(String serverId, String targetName, int operationCount, String savingTitle, String successTitle, String failureTitle) {
        beginWorldOperationNotification(serverId, targetName, operationCount, savingTitle, successTitle, failureTitle, 0L);
    }

    private long beginWorldOperationNotification(String serverId, String targetName, int operationCount, String savingTitle, String successTitle,
                                                 String failureTitle, long sequence) {
        if (serverId == null || serverId.isBlank() || targetName == null || targetName.isBlank()) {
            return 0L;
        }
        PendingWorldSave save = new PendingWorldSave(targetName, Math.max(1, operationCount), savingTitle, successTitle, failureTitle, sequence);
        pendingWorldSaves.computeIfAbsent(serverId, ignored -> BrowserSafeState.queue()).add(save);
        try {
            scheduler.schedule(() -> timeoutPendingWorldSave(serverId, save), Duration.ofSeconds(SAVE_TIMEOUT_SECONDS));
        } catch (UnsupportedOperationException ignored) {
        }
        hooks.onWorldSaveStarted(serverId, targetName, savingTitle, sequence);
        return sequence;
    }

    private void completePendingWorldSaveStep(String serverId) {
        Queue<PendingWorldSave> saves = pendingWorldSaves.get(serverId);
        PendingWorldSave save = saves != null ? saves.peek() : null;
        if (save == null) {
            return;
        }
        if (--save.remaining <= 0) {
            saves.poll();
            if (saves.isEmpty()) {
                pendingWorldSaves.remove(serverId, saves);
            }
            hooks.onWorldSaveFinished(serverId, save.targetName, save.successTitle, save.targetName,
                ReSyncNotificationLevel.SUCCESS, save.sequence);
        }
    }

    private boolean failPendingWorldSave(String serverId, String message) {
        Queue<PendingWorldSave> saves = pendingWorldSaves.get(serverId);
        PendingWorldSave save = saves != null ? saves.poll() : null;
        if (save == null) {
            return false;
        }
        if (saves.isEmpty()) {
            pendingWorldSaves.remove(serverId, saves);
        }
        hooks.onWorldSaveFinished(serverId, save.targetName, save.failureTitle, message, ReSyncNotificationLevel.ERROR, save.sequence);
        return true;
    }

    private void timeoutPendingWorldSave(String serverId, PendingWorldSave save) {
        Queue<PendingWorldSave> saves = pendingWorldSaves.get(serverId);
        if (saves != null && saves.remove(save)) {
            if (saves.isEmpty()) {
                pendingWorldSaves.remove(serverId, saves);
            }
            hooks.onWorldSaveFinished(serverId, save.targetName, save.failureTitle, "Save Timed Out", ReSyncNotificationLevel.ERROR, save.sequence);
        }
    }

    private boolean consumeSuppressedWorldSuccessNotification(String serverId, String action) {
        if (serverId == null || action == null) {
            return false;
        }
        String key = serverId + ":" + action.toLowerCase(Locale.ROOT);
        Integer count = suppressedWorldSuccessNotifications.get(key);
        if (count == null || count <= 0) {
            return false;
        }
        if (count == 1) {
            suppressedWorldSuccessNotifications.remove(key);
        } else {
            suppressedWorldSuccessNotifications.put(key, count - 1);
        }
        return true;
    }

    private static final class PendingWorldSave {
        private final String targetName;
        private final String savingTitle;
        private final String successTitle;
        private final String failureTitle;
        private final long sequence;
        private int remaining;

        private PendingWorldSave(String targetName, int remaining, String savingTitle, String successTitle, String failureTitle, long sequence) {
            this.targetName = targetName;
            this.remaining = remaining;
            this.savingTitle = savingTitle;
            this.successTitle = successTitle;
            this.failureTitle = failureTitle;
            this.sequence = sequence;
        }

    }

    private String prettyWorldMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Done";
        }
        String normalized = message.replace(':', ' ').replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? "Done" : builder.toString();
    }

    private void applyWorldOperationSnapshotPatch(String serverId, WorldOperationResult result) {
        if (serverId == null || serverId.isBlank() || result == null || !result.isSuccess()) {
            return;
        }
        WorldSnapshot snapshot = worldSnapshotCache.get(serverId);
        if (snapshot == null) {
            return;
        }
        String action = result.getAction() == null ? "" : result.getAction().trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "createinventorygroup", "updateinventorygroup" ->
                upsertSnapshotInventoryGroup(snapshot, WorldProtocolJson.readInventoryGroup(result.getData().get("group")));
            case "deleteinventorygroup" -> removeSnapshotInventoryGroup(snapshot, resultDataText(result, "groupId"));
            case "createworld", "loadworld", "unloadworld" ->
                upsertSnapshotWorld(snapshot, WorldProtocolJson.readWorld(result.getData().get("world")));
            case "deleteworld" -> removeSnapshotWorld(snapshot, resultDataText(result, "worldName", result.getWorldName()));
            default -> {
            }
        }
    }

    private String resultDataText(WorldOperationResult result, String... keys) {
        if (result == null) {
            return "";
        }
        if (keys != null && result.getData() != null) {
            for (String key : keys) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                Object value = result.getData().get(key);
                if (value != null) {
                    String text = FlowJson.text(value).trim();
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        String worldName = result.getWorldName();
        return worldName == null ? "" : worldName.trim();
    }

    private void upsertSnapshotWorld(WorldSnapshot snapshot, WorldRegistryEntry world) {
        if (snapshot == null || world == null || world.getWorldName() == null || world.getWorldName().isBlank()) {
            return;
        }
        try {
            snapshot.getWorlds().removeIf(entry -> entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(world.getWorldName()));
            snapshot.getWorlds().add(world);
        } catch (Exception ignored) {
        }
    }

    private void removeSnapshotWorld(WorldSnapshot snapshot, String worldName) {
        if (snapshot == null || worldName == null || worldName.isBlank()) {
            return;
        }
        try {
            snapshot.getWorlds().removeIf(entry -> entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(worldName));
            snapshot.getDashboard().removeIf(entry -> entry != null && entry.getWorldName() != null && entry.getWorldName().equalsIgnoreCase(worldName));
        } catch (Exception ignored) {
        }
    }

    private void upsertSnapshotInventoryGroup(WorldSnapshot snapshot, WorldInventoryGroup group) {
        if (snapshot == null || group == null || group.getGroupId() == null || group.getGroupId().isBlank()) {
            return;
        }
        try {
            snapshot.getInventoryGroups().removeIf(entry -> entry != null && entry.getGroupId() != null && entry.getGroupId().equalsIgnoreCase(group.getGroupId()));
            snapshot.getInventoryGroups().add(group);
        } catch (Exception ignored) {
        }
    }

    private void removeSnapshotInventoryGroup(WorldSnapshot snapshot, String groupId) {
        if (snapshot == null || groupId == null || groupId.isBlank()) {
            return;
        }
        try {
            snapshot.getInventoryGroups().removeIf(entry -> entry != null && entry.getGroupId() != null && entry.getGroupId().equalsIgnoreCase(groupId));
        } catch (Exception ignored) {
        }
    }

    private void dispatchWorldOperationResult(String serverId, WorldOperationResult result) {
        if (serverId == null || serverId.isBlank() || result == null) {
            return;
        }
        hooks.onOperationResult(serverId, result);
    }

    private WorldOperationResult cacheWorldOperationResult(String serverId, WorldChannelMessage message) {
        if (serverId == null || serverId.isBlank() || message == null || message.getData() == null) {
            return null;
        }
        try {
            WorldOperationResult result = WorldProtocolJson.readOperationResult(message.getData());
            if (result != null) {
                worldOperationCache.put(serverId, result);
            }
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Map<String, Object> worldAction(String action, Object... pairs) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        if (pairs != null) {
            for (int index = 0; index + 1 < pairs.length; index += 2) {
                Object key = pairs[index];
                if (key instanceof String stringKey && !stringKey.isBlank()) {
                    data.put(stringKey, pairs[index + 1]);
                }
            }
        }
        return data;
    }
}
