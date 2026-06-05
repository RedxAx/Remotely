package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;

public class TextTemplateDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public TextTemplateDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.TEXT_TEMPLATE, resourceId, resource, serverId, parent);
    }
}
