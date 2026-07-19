package redxax.oxy.remotely.data.flow;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FunctionReferenceAnalyzer {
    public static final String NODE_PREFIX = "custom_function:";

    private FunctionReferenceAnalyzer() {
    }

    public static List<String> findGraphReferences(FlowGraph graph, String functionId) {
        if (graph == null || graph.getNodes() == null || functionId == null || functionId.isBlank()) {
            return List.of();
        }
        String expectedType = NODE_PREFIX + functionId;
        List<String> locations = new ArrayList<>();
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            FlowNode node = entry.getValue();
            if (node != null && expectedType.equals(node.getType())) {
                locations.add("nodes." + entry.getKey() + ".type");
            }
        }
        return List.copyOf(locations);
    }

    public static int replaceGraphReferences(FlowGraph graph, String oldFunctionId, String newFunctionId) {
        if (graph == null || graph.getNodes() == null || oldFunctionId == null || newFunctionId == null) {
            return 0;
        }
        String expectedType = NODE_PREFIX + oldFunctionId;
        String replacementType = NODE_PREFIX + newFunctionId;
        int replacements = 0;
        for (FlowNode node : graph.getNodes().values()) {
            if (node != null && expectedType.equals(node.getType())) {
                node.setType(replacementType);
                replacements++;
            }
        }
        return replacements;
    }

    public static int reconcileGraphCallers(FlowGraph graph, String functionId, Set<String> inputPins, Set<String> outputPins) {
        if (graph == null || graph.getNodes() == null || functionId == null || functionId.isBlank()) {
            return 0;
        }
        String expectedType = NODE_PREFIX + functionId;
        int changes = 0;
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            FlowNode node = entry.getValue();
            if (node == null || !expectedType.equals(node.getType())) {
                continue;
            }
            if (node.getInputValues() != null) {
                int before = node.getInputValues().size();
                node.getInputValues().keySet().removeIf(pin -> !inputPins.contains(pin));
                changes += before - node.getInputValues().size();
            }
            if (graph.getConnections() != null) {
                int before = graph.getConnections().size();
                graph.getConnections().removeIf(connection ->
                    entry.getKey().equals(connection.getTargetNodeId()) && !"flow".equals(connection.getTargetPin()) && !inputPins.contains(connection.getTargetPin())
                        || entry.getKey().equals(connection.getSourceNodeId()) && !"flow".equals(connection.getSourcePin()) && !outputPins.contains(connection.getSourcePin()));
                changes += before - graph.getConnections().size();
            }
        }
        return changes;
    }

    public static List<String> findJsonReferences(JsonElement root, String functionId) {
        if (root == null || root.isJsonNull() || functionId == null || functionId.isBlank()) {
            return List.of();
        }
        List<String> locations = new ArrayList<>();
        findJsonReferences(root, functionId, "$", locations);
        return List.copyOf(locations);
    }

    public static int replaceJsonReferences(JsonElement root, String oldFunctionId, String newFunctionId) {
        if (root == null || root.isJsonNull() || oldFunctionId == null || newFunctionId == null) {
            return 0;
        }
        if (root.isJsonArray()) {
            int replacements = 0;
            for (JsonElement element : root.getAsJsonArray()) {
                replacements += replaceJsonReferences(element, oldFunctionId, newFunctionId);
            }
            return replacements;
        }
        if (!root.isJsonObject()) {
            return 0;
        }
        JsonObject object = root.getAsJsonObject();
        int replacements = 0;
        JsonElement functionId = object.get("functionId");
        if (functionId != null && functionId.isJsonPrimitive() && oldFunctionId.equals(functionId.getAsString())) {
            object.addProperty("functionId", newFunctionId);
            replacements++;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!"functionId".equals(entry.getKey())) {
                replacements += replaceJsonReferences(entry.getValue(), oldFunctionId, newFunctionId);
            }
        }
        return replacements;
    }

    private static void findJsonReferences(JsonElement element, String functionId, String path, List<String> locations) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                findJsonReferences(array.get(index), functionId, path + "[" + index + "]", locations);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement candidate = object.get("functionId");
        if (candidate != null && candidate.isJsonPrimitive() && functionId.equals(candidate.getAsString())) {
            locations.add(path + ".functionId");
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!"functionId".equals(entry.getKey())) {
                findJsonReferences(entry.getValue(), functionId, path + "." + entry.getKey(), locations);
            }
        }
    }
}
