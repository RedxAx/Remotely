package redxax.oxy.remotely.flow.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlowGraph {
    private String id;
    private Map<String, FlowNode> nodes;
    private List<FlowConnection> connections;
    private List<FlowVariable> localVariables;
    private boolean function;
    private List<FunctionParameter> functionInputs;
    private List<FunctionParameter> functionOutputs;

    public static class FunctionParameter {
        private String name;
        private FlowDataType type;

        public FunctionParameter() {
            this.name = "";
            this.type = FlowDataType.ANY;
        }

        public FunctionParameter(String name, FlowDataType type) {
            this.name = name;
            this.type = type != null ? type : FlowDataType.ANY;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public FlowDataType getType() {
            return type;
        }

        public void setType(FlowDataType type) {
            this.type = type;
        }
    }

    public FlowGraph() {
        this.id = UUID.randomUUID().toString();
        this.nodes = new HashMap<>();
        this.connections = new ArrayList<>();
        this.localVariables = new ArrayList<>();
        this.function = false;
        this.functionInputs = new ArrayList<>();
        this.functionOutputs = new ArrayList<>();
    }

    public FlowGraph(String id, Map<String, FlowNode> nodes, List<FlowConnection> connections, List<FlowVariable> localVariables) {
        this(id, nodes, connections, localVariables, false, new ArrayList<>(), new ArrayList<>());
    }

    public FlowGraph(String id, Map<String, FlowNode> nodes, List<FlowConnection> connections, List<FlowVariable> localVariables,
                     boolean function, List<FunctionParameter> functionInputs, List<FunctionParameter> functionOutputs) {
        this.id = id;
        this.nodes = nodes != null ? nodes : new HashMap<>();
        this.connections = connections != null ? connections : new ArrayList<>();
        this.localVariables = localVariables != null ? localVariables : new ArrayList<>();
        this.function = function;
        this.functionInputs = functionInputs != null ? functionInputs : new ArrayList<>();
        this.functionOutputs = functionOutputs != null ? functionOutputs : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public boolean isFunction() {
        return function;
    }

    public void setFunction(boolean function) {
        this.function = function;
    }

    public List<FunctionParameter> getFunctionInputs() {
        return functionInputs;
    }

    public void setFunctionInputs(List<FunctionParameter> functionInputs) {
        this.functionInputs = functionInputs;
    }

    public List<FunctionParameter> getFunctionOutputs() {
        return functionOutputs;
    }

    public void setFunctionOutputs(List<FunctionParameter> functionOutputs) {
        this.functionOutputs = functionOutputs;
    }
}
