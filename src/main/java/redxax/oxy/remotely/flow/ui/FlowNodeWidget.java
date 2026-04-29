package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;

public class FlowNodeWidget extends NodeWidget {
    public FlowNodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId) {
        super(x, y, node, graph, nodeId);
    }

    public FlowNodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId) {
        super(x, y, node, graph, nodeId, serverId);
    }

    public FlowNodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId, Runnable onClose) {
        super(x, y, node, graph, nodeId, serverId, onClose);
    }
}
