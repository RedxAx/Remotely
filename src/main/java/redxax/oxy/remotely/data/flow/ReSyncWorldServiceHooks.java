package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.data.flow.world.WorldOperationResult;

public interface ReSyncWorldServiceHooks {
    default void requestWorldSnapshot(String serverId) {
    }

    default void requestWorldMapSnapshot(String serverId, String worldName, double centerX, double centerZ, int zoom) {
    }

    default void onAuditSnapshot(String serverId, Object data) {
    }

    default void onOperationResult(String serverId, WorldOperationResult result) {
    }

    default void onWorldSaveStarted(String serverId, String targetName, String title, long sequence) {
    }

    default void onWorldSaveFinished(String serverId, String targetName, String title, String message,
                                     ReSyncNotificationLevel level, long sequence) {
    }

    static ReSyncWorldServiceHooks noop() {
        return new ReSyncWorldServiceHooks() {
        };
    }
}
