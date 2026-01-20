package redxax.oxy.remotely.flow.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlowGraph {
    private UUID id;
    private Map<String, FlowNode> nodes;
    private List<FlowConnection> connections;
    private List<FlowVariable> localVariables;

    public FlowGraph() {
        this.id = UUID.randomUUID();
        this.nodes = new HashMap<>();
        this.connections = new ArrayList<>();
        this.localVariables = new ArrayList<>();
    }

    public FlowGraph(UUID id, Map<String, FlowNode> nodes, List<FlowConnection> connections, List<FlowVariable> localVariables) {
        this.id = id;
        this.nodes = nodes != null ? nodes : new HashMap<>();
        this.connections = connections != null ? connections : new ArrayList<>();
        this.localVariables = localVariables != null ? localVariables : new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Map<String, FlowNode> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, FlowNode> nodes) {
        this.nodes = nodes;
    }

    public List<FlowConnection> getConnections() {
        return connections;
    }

    public void setConnections(List<FlowConnection> connections) {
        this.connections = connections;
    }

    public List<FlowVariable> getLocalVariables() {
        return localVariables;
    }

    public void setLocalVariables(List<FlowVariable> localVariables) {
        this.localVariables = localVariables;
    }
}
