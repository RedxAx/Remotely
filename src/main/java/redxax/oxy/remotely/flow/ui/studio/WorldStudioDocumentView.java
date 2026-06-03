package redxax.oxy.remotely.flow.ui.studio;

import redxax.oxy.remotely.data.flow.world.WorldOperationResult;

public interface WorldStudioDocumentView {
    void refreshWorlds();
    void handleWorldOperationResult(WorldOperationResult result);
}
