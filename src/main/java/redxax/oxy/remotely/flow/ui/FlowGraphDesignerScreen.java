package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.FlowGraph;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.ui.core.Screen;

public class FlowGraphDesignerScreen extends GraphEditorScreen {
    public FlowGraphDesignerScreen(FlowGraph graph, String serverId, Screen parent) {
        super(graph, serverId, parent);
    }

    public FlowGraphDesignerScreen(FlowGraph graph, String serverId, Screen parent, ClientServerView startupServer, String loaderHint) {
        super(graph, serverId, parent, startupServer, loaderHint);
    }

    public FlowGraphDesignerScreen(FlowGraph graph, String serverId, Screen parent, ClientServerView startupServer, String loaderHint, String serverTitle) {
        super(graph, serverId, parent, startupServer, loaderHint, serverTitle);
    }

    @Override
    public FlowGraphDesignerScreen enableStudioMode() {
        super.enableStudioMode();
        return this;
    }
}