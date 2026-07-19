package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowTypeRef;
import redxax.oxy.remotely.flow.registry.NodeDefinition;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionSignatureTypeResolverTest {
    @Test
    void repairsExtractedFunctionGenericsFromBoundaryConnections() {
        FlowGraph graph = functionGraph();
        Map<String, NodeDefinition> definitions = Map.of(
            "permission.consumer", definition("permission.consumer", "permissions", true, FlowDataType.LIST, "list<permission>"),
            "resource.producer", definition("resource.producer", "lookup", false, FlowDataType.MAP, "map<string,resource_reference<gui>>")
        );

        assertEquals(2, FunctionSignatureTypeResolver.resolve(graph, definitions::get));
        assertEquals(FlowTypeRef.parse("list<permission>"), graph.getFunctionInputs().getFirst().getTypeRef());
        assertEquals(FlowTypeRef.parse("map<string,resource_reference<gui>>"), graph.getFunctionOutputs().getFirst().getTypeRef());
        assertEquals(0, FunctionSignatureTypeResolver.resolve(graph, definitions::get));
    }

    @Test
    void doesNotReplaceAnAlreadyPreciseDeclaredSignature() {
        FlowGraph graph = functionGraph();
        graph.getFunctionInputs().getFirst().setTypeRef(FlowTypeRef.parse("list<string>"));
        NodeDefinition definition = definition("permission.consumer", "permissions", true, FlowDataType.LIST, "list<permission>");

        assertEquals(0, FunctionSignatureTypeResolver.resolve(graph, nodeType -> "permission.consumer".equals(nodeType) ? definition : null));
        assertEquals(FlowTypeRef.parse("list<string>"), graph.getFunctionInputs().getFirst().getTypeRef());
    }

    private FlowGraph functionGraph() {
        FlowGraph graph = new FlowGraph();
        graph.setFunction(true);
        graph.getFunctionInputs().add(new FlowGraph.FunctionParameter("permissions", FlowDataType.LIST));
        graph.getFunctionOutputs().add(new FlowGraph.FunctionParameter("lookup", FlowDataType.MAP));
        graph.getNodes().put("start", new FlowNode("function.start", 0, 0, Map.of()));
        graph.getNodes().put("consumer", new FlowNode("permission.consumer", 0, 0, Map.of()));
        graph.getNodes().put("producer", new FlowNode("resource.producer", 0, 0, Map.of()));
        graph.getNodes().put("end", new FlowNode("function.end", 0, 0, Map.of()));
        graph.getConnections().add(new FlowConnection("start", "permissions", "consumer", "permissions"));
        graph.getConnections().add(new FlowConnection("producer", "lookup", "end", "lookup"));
        return graph;
    }

    private NodeDefinition definition(String id, String pinName, boolean input, FlowDataType dataType, String typeRef) {
        NodeDefinition.PinDefinition pin = new NodeDefinition.PinBuilder(pinName, NodeDefinition.PinType.DATA,
            input ? NodeDefinition.PinDirection.INPUT : NodeDefinition.PinDirection.OUTPUT, dataType).typeRef(FlowTypeRef.parse(typeRef)).build();
        NodeDefinition.Builder builder = new NodeDefinition.Builder(id, id, NodeDefinition.NodeCategory.FUNCTION);
        return (input ? builder.input(pin) : builder.output(pin)).build();
    }
}
