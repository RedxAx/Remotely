package redxax.oxy.remotely.flow.data;

import com.google.gson.JsonElement;
import redxax.oxy.remotely.nodegraph.editor.GraphModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlowGraph implements GraphModel {
    public static final int CURRENT_VERSION = 2;
    private String id;
    private boolean enabled = true;
    private int version;
    private Map<String, FlowNode> nodes;
    private List<FlowConnection> connections;
    private List<FlowVariable> localVariables;
    private boolean function;
    private String functionOwner;
    private String functionNamespace;
    private int functionVersion;
    private String functionDescription;
    private List<FunctionParameter> functionInputs;
    private List<FunctionParameter> functionOutputs;
    private List<EditorPassthrough> editorPassthroughs;
    private Map<String, Object> contentProperties;
    private String resourceType;
    private long resourceRevision;
    private String resourceHash;
    private String resourceMutationId;
    private transient Map<String, JsonElement> opaqueProperties;

    public static class FunctionParameter {
        private String name;
        private FlowDataType type;
        private FlowTypeRef typeRef;
        private String widget;
        private String optionsSource;
        private String defaultValue;

        public FunctionParameter() {
            this.name = "";
            this.type = FlowDataType.ANY;
            this.typeRef = FlowTypeRef.simple(FlowDataType.ANY.getId()).normalizedGenerics();
            this.widget = "";
            this.optionsSource = "";
            this.defaultValue = "";
        }

        public FunctionParameter(String name, FlowDataType type) {
            this.name = name;
            this.type = type != null ? type : FlowDataType.ANY;
            this.typeRef = FlowTypeRef.simple(this.type.getId()).normalizedGenerics();
            this.widget = "";
            this.optionsSource = "";
            this.defaultValue = "";
        }

        public FunctionParameter(String name, FlowDataType type, String widget, String optionsSource, String defaultValue) {
            this.name = name;
            this.type = type != null ? type : FlowDataType.ANY;
            this.typeRef = FlowTypeRef.simple(this.type.getId()).normalizedGenerics();
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
            this.typeRef = FlowTypeRef.simple(type != null ? type.getId() : FlowDataType.ANY.getId()).normalizedGenerics();
        }

        public FlowTypeRef getTypeRef() {
            return (typeRef != null ? typeRef : FlowTypeRef.simple(type != null ? type.getId() : FlowDataType.ANY.getId())).normalizedGenerics();
        }

        public void setTypeRef(FlowTypeRef typeRef) {
            this.typeRef = (typeRef != null ? typeRef : FlowTypeRef.simple(type != null ? type.getId() : FlowDataType.ANY.getId())).normalizedGenerics();
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
        this.functionOwner = "server";
        this.functionNamespace = "local";
        this.functionVersion = 1;
        this.functionDescription = "";
        this.functionInputs = new ArrayList<>();
        this.functionOutputs = new ArrayList<>();
        this.editorPassthroughs = new ArrayList<>();
        this.resourceType = "";
        this.resourceHash = "";
        this.resourceMutationId = "";
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
        this.functionOwner = "server";
        this.functionNamespace = "local";
        this.functionVersion = 1;
        this.functionDescription = "";
        this.functionInputs = functionInputs != null ? functionInputs : new ArrayList<>();
        this.functionOutputs = functionOutputs != null ? functionOutputs : new ArrayList<>();
        this.editorPassthroughs = new ArrayList<>();
        this.resourceType = "";
        this.resourceHash = "";
        this.resourceMutationId = "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getFunctionOwner() {
        return functionOwner != null && !functionOwner.isBlank() ? functionOwner : "server";
    }

    public void setFunctionOwner(String functionOwner) {
        this.functionOwner = functionOwner != null && !functionOwner.isBlank() ? functionOwner : "server";
    }

    public String getFunctionNamespace() {
        return functionNamespace != null && !functionNamespace.isBlank() ? functionNamespace : "local";
    }

    public void setFunctionNamespace(String functionNamespace) {
        this.functionNamespace = functionNamespace != null && !functionNamespace.isBlank() ? functionNamespace : "local";
    }

    public int getFunctionVersion() {
        return Math.max(1, functionVersion);
    }

    public void setFunctionVersion(int functionVersion) {
        this.functionVersion = Math.max(1, functionVersion);
    }

    public String getFunctionDescription() {
        return functionDescription != null ? functionDescription : "";
    }

    public void setFunctionDescription(String functionDescription) {
        this.functionDescription = functionDescription != null ? functionDescription : "";
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

    public Map<String, Object> getContentProperties() {
        if (contentProperties == null) {
            contentProperties = new HashMap<>();
        }
        return contentProperties;
    }

    public void setContentProperties(Map<String, Object> contentProperties) {
        this.contentProperties = contentProperties != null ? contentProperties : new HashMap<>();
    }

    public String getResourceType() {
        return resourceType == null ? "" : resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType == null ? "" : resourceType;
    }

    public long getResourceRevision() {
        return resourceRevision;
    }

    public void setResourceRevision(long resourceRevision) {
        this.resourceRevision = Math.max(0, resourceRevision);
    }

    public String getResourceHash() {
        return resourceHash == null ? "" : resourceHash;
    }

    public void setResourceHash(String resourceHash) {
        this.resourceHash = resourceHash == null ? "" : resourceHash;
    }

    public String getResourceMutationId() {
        return resourceMutationId == null ? "" : resourceMutationId;
    }

    public void setResourceMutationId(String resourceMutationId) {
        this.resourceMutationId = resourceMutationId == null ? "" : resourceMutationId;
    }

    public Map<String, JsonElement> getOpaqueProperties() {
        if (opaqueProperties == null) {
            opaqueProperties = new HashMap<>();
        }
        return opaqueProperties;
    }

    public void setOpaqueProperties(Map<String, JsonElement> opaqueProperties) {
        this.opaqueProperties = opaqueProperties != null ? new HashMap<>(opaqueProperties) : new HashMap<>();
    }
}
