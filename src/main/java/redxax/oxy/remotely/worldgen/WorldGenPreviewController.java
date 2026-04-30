package redxax.oxy.remotely.worldgen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class WorldGenPreviewController {
    private final Map<String, String> previewStates = new ConcurrentHashMap<>();

    void markCreating(String serverId, String previewId) {
        previewStates.put(previewKey(serverId, previewId), "creating");
    }

    void update(String serverId, String previewId, String status) {
        previewStates.put(previewKey(serverId, previewId), status);
    }

    void stop(String serverId, String previewId) {
        previewStates.remove(previewKey(serverId, previewId));
    }

    String state(String serverId, String previewId) {
        return previewStates.getOrDefault(previewKey(serverId, previewId), "stopped");
    }

    private String previewKey(String serverId, String previewId) {
        return (serverId == null ? "" : serverId) + ":" + (previewId == null ? "" : previewId);
    }
}
