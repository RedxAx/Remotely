package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowTypeRef;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class FunctionSignatureTypeResolver {
    private static final Set<String> FUNCTION_START_TYPES = Set.of("function_start", "function.start", "function.function_start");
    private static final Set<String> FUNCTION_END_TYPES = Set.of("function_end", "function.end", "function.function_end");

    private FunctionSignatureTypeResolver() {
    }

    public static int resolve(String serverId, FlowGraph graph) {
        NodeRegistry registry = NodeRegistry.getInstance();
        return registry != null ? resolve(graph, nodeType -> registry.getDefinition(serverId, nodeType)) : 0;
    }

    static int resolve(FlowGraph graph, Function<String, NodeDefinition> definitionResolver) {
        if (graph == null || !graph.isFunction() || graph.getConnections() == null || graph.getNodes() == null || definitionResolver == null) {
            return 0;
        }
        int changes = 0;
        for (FlowConnection connection : graph.getConnections()) {
            FlowNode source = graph.getNodes().get(connection.getSourceNodeId());
            FlowNode target = graph.getNodes().get(connection.getTargetNodeId());
            if (source != null && target != null && source.getType() != null && FUNCTION_START_TYPES.contains(source.getType()) && !"flow".equals(connection.getSourcePin())) {
                FlowTypeRef typeRef = pinType(definitionResolver.apply(target.getType()), connection.getTargetPin(), true);
                changes += applyType(graph.getFunctionInputs(), connection.getSourcePin(), typeRef);
            }
            if (source != null && target != null && target.getType() != null && FUNCTION_END_TYPES.contains(target.getType()) && !"flow".equals(connection.getTargetPin())) {
                FlowTypeRef typeRef = pinType(definitionResolver.apply(source.getType()), connection.getSourcePin(), false);
                changes += applyType(graph.getFunctionOutputs(), connection.getTargetPin(), typeRef);
            }
        }
        return changes;
    }

    private static FlowTypeRef pinType(NodeDefinition definition, String pinName, boolean input) {
        if (definition == null || pinName == null) {
            return null;
        }
        List<NodeDefinition.PinDefinition> pins = input ? definition.getInputs() : definition.getOutputs();
        return pins.stream().filter(pin -> pin != null && pinName.equals(pin.getName())).map(NodeDefinition.PinDefinition::getTypeRef).findFirst().orElse(null);
    }

    private static int applyType(List<FlowGraph.FunctionParameter> parameters, String parameterName, FlowTypeRef typeRef) {
        if (parameters == null || parameterName == null || typeRef == null) {
            return 0;
        }
        for (FlowGraph.FunctionParameter parameter : parameters) {
            if (parameter == null || !parameterName.equals(parameter.getName())) {
                continue;
            }
            FlowTypeRef currentType = parameter.getTypeRef();
            if (typeRef.equals(currentType) || !isImprecise(currentType) || isImprecise(typeRef)) {
                return 0;
            }
            parameter.setType(FlowDataType.fromString(typeRef.getTypeId()));
            parameter.setTypeRef(typeRef);
            return 1;
        }
        return 0;
    }

    private static boolean isImprecise(FlowTypeRef typeRef) {
        if (typeRef == null || "any".equals(typeRef.getTypeId())) {
            return true;
        }
        if (typeRef.getArguments().stream().anyMatch(FunctionSignatureTypeResolver::isImprecise)) {
            return true;
        }
        return typeRef.getArguments().isEmpty() && Set.of("list", "set", "map", "queue", "stack", "optional", "result", "job_reference", "resource_reference")
            .contains(typeRef.getTypeId());
    }
}
