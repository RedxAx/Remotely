package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;

public class MessageRuleDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public MessageRuleDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.MESSAGE_RULE, resourceId, resource, serverId, parent);
    }
}
