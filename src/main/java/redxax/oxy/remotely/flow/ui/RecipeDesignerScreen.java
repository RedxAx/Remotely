package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;

public class RecipeDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public RecipeDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.RECIPE_DEFINITION, resourceId, resource, serverId, parent);
    }
}
