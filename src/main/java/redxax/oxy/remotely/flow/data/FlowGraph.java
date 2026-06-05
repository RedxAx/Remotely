package redxax.oxy.remotely.flow.data;

import redxax.oxy.remotely.nodegraph.editor.GraphModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlowGraph implements GraphModel {
    public static final int CURRENT_VERSION = 1;
    private String id;
    private int version;
    private Map<String, FlowNode> nodes;
    private List<FlowConnection> connections;
    private List<FlowVariable> localVariables;
    private boolean function;
    private List<FunctionParameter> functionInputs;
    private List<FunctionParameter> functionOutputs;
    private List<EditorPassthrough> editorPassthroughs;

    public static class FunctionParameter {
        private String name;
        private FlowDataType type;
        private String widget;
        private String optionsSource;
        private String defaultValue;

        public FunctionParameter() {
            this.name = "";
            this.type = FlowDataType.ANY;
            this.widget = "";
            this.optionsSource = "";
            this.defaultValue = "";
        }

        public FunctionParameter(String name, FlowDataType type) {
            this.name = name;
            this.type = type != null ? type : FlowDataType.ANY;
            this.widget = "";
            this.optionsSource = "";
            this.defaultValue = "";
        }

        public FunctionParameter(String name, FlowDataType type, String widget, String optionsSource, String defaultValue) {
            this.name = name;
            this.type = type != null ? type : FlowDataType.ANY;
            this.widget = widget != null ? widget : "";
            this.optionsSource = optionsSource != null ? optionsSource : "";
            this.defaultValue = defaultValue != null ? defaultValue : "";
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

        public String getWidget() {
            return widget;
        }

        public void setWidget(String widget) {
            this.widget = widget;
        }

        public String getOptionsSource() {
            return optionsSource;
        }

        public void setOptionsSource(String optionsSource) {
            this.optionsSource = optionsSource;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }

    public static class EditorPassthrough {
        private String nodeId;
        private String inputPin;

        public EditorPassthrough() {
            this.nodeId = "";
            this.inputPin = "";
        }

        public EditorPassthrough(String nodeId, String inputPin) {
            this.nodeId = nodeId;
            this.inputPin = inputPin;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getInputPin() {
            return inputPin;
        }

        public void setInputPin(String inputPin) {
            this.inputPin = inputPin;
        }
    }

    public FlowGraph() {
        this.id = UUID.randomUUID().toString();
        this.version = CURRENT_VERSION;
        this.nodes = new HashMap<>();
        this.connections = new ArrayList<>();
        this.localVariables = new ArrayList<>();
        this.function = false;
        this.functionInputs = new ArrayList<>();
        this.functionOutputs = new ArrayList<>();
        this.editorPassthroughs = new ArrayList<>();
    }

    public FlowGraph(String id, Map<String, FlowNode> nodes, List<FlowConnection> connections, List<FlowVariable> localVariables) {
        this(id, nodes, connections, localVariables, false, new ArrayList<>(), new ArrayList<>());
    }

    public FlowGraph(String id, Map<String, FlowNode> nodes, List<FlowConnection> connections, List<FlowVariable> localVariables,
                     boolean function, List<FunctionParameter> functionInputs, List<FunctionParameter> functionOutputs) {
        this.id = id;
        this.version = CURRENT_VERSION;
        this.nodes = nodes != null ? nodes : new HashMap<>();
        this.connections = connections != null ? connections : new ArrayList<>();
        this.localVariables = localVariables != null ? localVariables : new ArrayList<>();
        this.function = function;
        this.functionInputs = functionInputs != null ? functionInputs : new ArrayList<>();
        this.functionOutputs = functionOutputs != null ? functionOutputs : new ArrayList<>();
        this.editorPassthroughs = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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

    public List<EditorPassthrough> getEditorPassthroughs() {
        if (editorPassthroughs == null) {
            editorPassthroughs = new ArrayList<>();
        }
        return editorPassthroughs;
    }

    public void setEditorPassthroughs(List<EditorPassthrough> editorPassthroughs) {
        this.editorPassthroughs = editorPassthroughs != null ? editorPassthroughs : new ArrayList<>();
    }
}
