package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import restudio.resync.flow.workspace.WorkspacePatch;

import java.util.List;

public interface ReSyncCollaborativeView {
    default boolean supportsCollaboration() {
        return true;
    }

    JsonObject collaborationDocument();

    void applyCollaborationDocument(JsonObject document, List<WorkspacePatch<JsonElement>> patches);

    default void rebaseCollaborationHistory(List<WorkspacePatch<JsonElement>> patches) {
    }
}
