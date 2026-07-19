package redxax.oxy.remotely.data.flow;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionReferenceAnalyzerTest {
    @Test
    void findsAndRefactorsGraphCallers() {
        FlowGraph graph = new FlowGraph();
        graph.setNodes(Map.of(
            "caller", new FlowNode("custom_function:library:old", 0, 0, Map.of()),
            "other", new FlowNode("message.send", 0, 0, Map.of())
        ));

        assertEquals(1, FunctionReferenceAnalyzer.findGraphReferences(graph, "library:old").size());
        assertEquals(1, FunctionReferenceAnalyzer.replaceGraphReferences(graph, "library:old", "library:new"));
        assertEquals("custom_function:library:new", graph.getNodes().get("caller").getType());
    }

    @Test
    void findsAndRefactorsNestedDesignerBindings() {
        JsonObject call = new JsonObject();
        call.addProperty("functionId", "library:old");
        JsonArray actions = new JsonArray();
        actions.add(call);
        JsonObject resource = new JsonObject();
        resource.add("actions", actions);

        assertEquals("$.actions[0].functionId", FunctionReferenceAnalyzer.findJsonReferences(resource, "library:old").getFirst());
        assertEquals(1, FunctionReferenceAnalyzer.replaceJsonReferences(resource, "library:old", "library:new"));
        assertEquals("library:new", call.get("functionId").getAsString());
    }

    @Test
    void changingAFunctionSignatureRemovesOnlyStaleCallerBindings() {
        FlowGraph graph = new FlowGraph();
        graph.setNodes(new HashMap<>(Map.of("caller", new FlowNode("custom_function:library:target", 0, 0,
            new HashMap<>(Map.of("kept", 1, "removed", 2))))));
        graph.setConnections(new ArrayList<>(List.of(
            new FlowConnection("source", "value", "caller", "kept"),
            new FlowConnection("source", "value", "caller", "removed"),
            new FlowConnection("caller", "kept_result", "target", "value"),
            new FlowConnection("caller", "removed_result", "target", "other")
        )));

        assertEquals(3, FunctionReferenceAnalyzer.reconcileGraphCallers(graph, "library:target", Set.of("kept"), Set.of("kept_result")));
        assertEquals(Map.of("kept", 1), graph.getNodes().get("caller").getInputValues());
        assertEquals(2, graph.getConnections().size());
    }
}
