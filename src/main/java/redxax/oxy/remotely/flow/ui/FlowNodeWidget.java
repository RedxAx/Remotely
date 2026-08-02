package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowTypeRef;

import java.util.List;
import java.util.Set;

public class FlowNodeWidget extends NodeWidget {
    private static final Set<String> FUNCTION_START_TYPES = Set.of("function_start", "function.start", "function.function_start");
    private static final Set<String> FUNCTION_END_TYPES = Set.of("function_end", "function.end", "function.function_end");
    private final FlowNode flowNode;
    private final FlowGraph flowGraph;

    public FlowNodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId) {
        super(x, y, node, graph, nodeId);
        this.flowNode = node;
        this.flowGraph = graph;
    }

    public FlowNodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId) {
        super(x, y, node, graph, nodeId, serverId);
        this.flowNode = node;
        this.flowGraph = graph;
    }

    public FlowNodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId, Runnable onClose) {
        super(x, y, node, graph, nodeId, serverId, onClose);
        this.flowNode = node;
        this.flowGraph = graph;
    }

    public FlowNodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId, Runnable onClose, Runnable onMutation) {
        super(x, y, node, graph, nodeId, serverId, onClose, onMutation);
        this.flowNode = node;
        this.flowGraph = graph;
    }

    @Override
    public FlowTypeRef getPinTypeRef(String pinName, boolean isInput) {
        FlowTypeRef functionType = functionParameterTypeRef(pinName, isInput);
        return functionType != null ? functionType : super.getPinTypeRef(pinName, isInput);
    }

    private FlowTypeRef functionParameterTypeRef(String pinName, boolean isInput) {
        return resolveFunctionParameterType(flowNode, flowGraph, pinName, isInput);
    }

    static FlowTypeRef resolveFunctionParameterType(FlowNode flowNode, FlowGraph flowGraph, String pinName, boolean isInput) {
        if (flowNode == null || flowGraph == null || pinName == null || "flow".equals(pinName)) {
            return null;
        }
        String nodeType = flowNode.getType();
        if (nodeType == null) {
            return null;
        }
        if (!isInput && FUNCTION_START_TYPES.contains(nodeType)) {
            return parameterType(flowGraph.getFunctionInputs(), pinName);
        }
        if (isInput && FUNCTION_END_TYPES.contains(nodeType)) {
            return parameterType(flowGraph.getFunctionOutputs(), pinName);
        }
        return null;
    }

    private static FlowTypeRef parameterType(List<FlowGraph.FunctionParameter> parameters, String pinName) {
        if (parameters == null) {
            return null;
        }
        return parameters.stream().filter(parameter -> parameter != null && pinName.equals(parameter.getName())).map(FlowGraph.FunctionParameter::getTypeRef)
            .filter(typeRef -> typeRef != null).findFirst().orElse(null);
    }
}
