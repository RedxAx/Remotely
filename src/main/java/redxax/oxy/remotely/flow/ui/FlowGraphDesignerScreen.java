package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.registry.NodeDiscoveryPreferences;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.ui.core.Screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        Set<String> nodesBefore = new HashSet<>(graph.getNodes().keySet());
        boolean handled = super.mouseClicked(event);
        recordAddedNodes(nodesBefore);
        return handled;
    }

    @Override
    public boolean mouseReleased(ReMouseEvent event) {
        Set<String> nodesBefore = new HashSet<>(graph.getNodes().keySet());
        boolean handled = super.mouseReleased(event);
        recordAddedNodes(nodesBefore);
        return handled;
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        Set<String> nodesBefore = new HashSet<>(graph.getNodes().keySet());
        boolean handled = super.keyPressed(event);
        recordAddedNodes(nodesBefore);
        return handled;
    }

    private void recordAddedNodes(Set<String> nodesBefore) {
        List<String> addedTypes = new ArrayList<>();
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            if (nodesBefore.contains(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            addedTypes.add(entry.getValue().getType());
        }
        if (!addedTypes.isEmpty()) {
            NodeDiscoveryPreferences.recordRecent(addedTypes);
        }
    }
}
