package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.registry.WorldGenNodeDefinition;

import java.util.List;
import java.util.Map;

public interface ReSyncWorldGenerationState {
    default void onPreviewStatus(String serverId, String previewId, String state, String message) {
    }

    default void onRegistrySnapshot(String serverId, List<WorldGenNodeDefinition> definitions, Object capabilities) {
    }

    default void onProjectData(String serverId, WorldGenProject project) {
    }

    default void onProjectList(String serverId, List<String> ids) {
    }

    default void onProjectSaveAcknowledged(String serverId, String payload) {
    }

    default void onCompileDiagnostics(String serverId, String payload) {
    }

    static ReSyncWorldGenerationState noop() {
        return new ReSyncWorldGenerationState() {
        };
    }
}
