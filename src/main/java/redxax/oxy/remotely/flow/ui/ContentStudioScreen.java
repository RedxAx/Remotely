package redxax.oxy.remotely.flow.ui;

import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.ui.core.Screen;

public class ContentStudioScreen extends ContentDesignerScreen {
    public ContentStudioScreen(String serverId, ClientServerView server, String flowId, Screen parent) {
        super(serverId, server, flowId, parent);
    }
}
