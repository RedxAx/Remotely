package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class CompactBindingSupport {
    private CompactBindingSupport() {
    }

    static List<String> flowOptions(String serverId) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return List.of("none");
        }
        List<String> options = new ArrayList<>();
        options.add("none");
        Set<String> functionIds = functionResourceIds(manager, serverId);
        manager.getFlowsForServer(serverId).entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isFunction() && !functionIds.contains(entry.getKey()))
            .map(Map.Entry::getKey)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(options::add);
        return options;
    }

    static List<String> functionOptions(String serverId) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return List.of("none");
        }
        List<String> options = new ArrayList<>();
        options.add("none");
        Set<String> functionIds = functionResourceIds(manager, serverId);
        manager.getFlowsForServer(serverId).entrySet().stream()
            .filter(entry -> entry.getValue() != null && (entry.getValue().isFunction() || functionIds.contains(entry.getKey())))
            .map(Map.Entry::getKey)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(options::add);
        return options;
    }

    static FlowGraph selectedFunction(String serverId, String functionId, FunctionShape shape) {
        if (functionId == null || functionId.isBlank() || "none".equalsIgnoreCase(functionId)) {
            return null;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return null;
        }
        FlowGraph function = manager.getFlowsForServer(serverId).get(functionId);
        if (function == null) {
            return null;
        }
        normalizeFunction(serverId, function, shape);
        return function.isFunction() ? function : null;
    }

    static FlowGraph normalizeFunction(String serverId, FlowGraph function, FunctionShape shape) {
        if (function == null) {
            return null;
        }
        boolean changed = false;
        if (!function.isFunction()) {
            function.setFunction(true);
            changed = true;
        }
        if (function.getNodes() == null) {
            function.setNodes(new HashMap<>());
            changed = true;
        }
        if (function.getConnections() == null) {
            function.setConnections(new ArrayList<>());
            changed = true;
        }
        if (function.getLocalVariables() == null) {
            function.setLocalVariables(new ArrayList<>());
            changed = true;
        }
        if (function.getFunctionInputs() == null) {
            function.setFunctionInputs(new ArrayList<>());
            changed = true;
        }
        if (function.getFunctionOutputs() == null) {
            function.setFunctionOutputs(new ArrayList<>());
            changed = true;
        }
        String startId = findNode(function, "function_start", "function.start", "function.function_start");
        String endId = findNode(function, "function_end", "function.end", "function.function_end");
        if (startId == null) {
            startId = UUID.randomUUID().toString();
            function.getNodes().put(startId, new FlowNode("function_start", 120, 120, new HashMap<>()));
            changed = true;
        }
        if (endId == null) {
            endId = UUID.randomUUID().toString();
            function.getNodes().put(endId, new FlowNode("function_end", 380, 120, new HashMap<>()));
            changed = true;
        }
        if (function.getConnections().isEmpty()) {
            function.getConnections().add(new FlowConnection(startId, "flow", endId, "flow"));
            changed = true;
        }
        if (shape != null) {
            changed |= applyParameters(function.getFunctionInputs(), shape.inputs());
            changed |= applyParameters(function.getFunctionOutputs(), shape.outputs());
        }
        if (changed) {
            FlowManager manager = FlowManager.getInstance();
            if (manager != null && serverId != null) {
                manager.saveFlow(serverId, function);
            }
        }
        return function;
    }

    static FunctionShape playerActionShape() {
        return new FunctionShape(List.of(new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER)), List.of());
    }

    static FunctionShape playerPredicateShape() {
        return new FunctionShape(
            List.of(new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER)),
            List.of(new FlowGraph.FunctionParameter("result", FlowDataType.BOOLEAN, "boolean", "", "false"))
        );
    }

    private static boolean applyParameters(List<FlowGraph.FunctionParameter> target, List<FlowGraph.FunctionParameter> required) {
        boolean changed = false;
        for (FlowGraph.FunctionParameter parameter : required) {
            if (parameter == null || parameter.getName() == null || parameter.getName().isBlank()) {
                continue;
            }
            FlowGraph.FunctionParameter existing = target.stream()
                .filter(value -> value != null && parameter.getName().equals(value.getName()))
                .findFirst()
                .orElse(null);
            if (existing == null) {
                target.add(new FlowGraph.FunctionParameter(parameter.getName(), parameter.getType(), parameter.getWidget(), parameter.getOptionsSource(), parameter.getDefaultValue()));
                changed = true;
                continue;
            }
            if (parameter.getType() != null && !parameter.getType().equals(existing.getType())) {
                existing.setType(parameter.getType());
                changed = true;
            }
            if (parameter.getWidget() != null && !parameter.getWidget().isBlank() && !parameter.getWidget().equals(existing.getWidget())) {
                existing.setWidget(parameter.getWidget());
                changed = true;
            }
            if (parameter.getOptionsSource() != null && !parameter.getOptionsSource().isBlank() && !parameter.getOptionsSource().equals(existing.getOptionsSource())) {
                existing.setOptionsSource(parameter.getOptionsSource());
                changed = true;
            }
            if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isBlank() && !parameter.getDefaultValue().equals(existing.getDefaultValue())) {
                existing.setDefaultValue(parameter.getDefaultValue());
                changed = true;
            }
        }
        target.sort(Comparator.comparing(parameter -> parameter != null && parameter.getName() != null ? parameter.getName() : "", String.CASE_INSENSITIVE_ORDER));
        return changed;
    }

    private static String findNode(FlowGraph graph, String... types) {
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            FlowNode node = entry.getValue();
            if (node == null || node.getType() == null) {
                continue;
            }
            for (String type : types) {
                if (type.equals(node.getType())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static Set<String> functionResourceIds(FlowManager manager, String serverId) {
        Set<String> ids = new HashSet<>();
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        for (ReSyncProjectMetadata.ResourceEntry entry : metadata.getResources()) {
            if (entry != null && ReSyncResourceDragPayload.FUNCTION.equals(entry.getType()) && entry.getId() != null && !entry.getId().isBlank()) {
                ids.add(entry.getId());
            }
        }
        return ids;
    }

    record FunctionShape(List<FlowGraph.FunctionParameter> inputs, List<FlowGraph.FunctionParameter> outputs) {
    }
}
