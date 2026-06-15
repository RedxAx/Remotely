package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;

public class VillageDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public VillageDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.VILLAGE_PROFILE, resourceId, resource, serverId, parent);
    }
}
