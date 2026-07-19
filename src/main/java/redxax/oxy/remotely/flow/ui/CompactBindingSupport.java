package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import restudio.rescreen.ui.widgets.CompactBindingWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class CompactBindingSupport {
    static final List<String> ACTION_MODES = List.of("None", "Run Flow", "Run Function", "Run Command");
    static final List<String> PREDICATE_MODES = List.of("None", "Function");

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
        return function;
    }

    static FunctionShape playerActionShape() {
        return new FunctionShape(List.of(new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER)), List.of());
    }

    static FunctionShape guiActionShape() {
        return new FunctionShape(List.of(
            new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER),
            new FlowGraph.FunctionParameter("item", FlowDataType.ITEM),
            new FlowGraph.FunctionParameter("slot", FlowDataType.NUMBER)
        ), List.of());
    }

    static FunctionShape recipeActionShape() {
        return new FunctionShape(List.of(
            new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER),
            new FlowGraph.FunctionParameter("item", FlowDataType.ITEM),
            new FlowGraph.FunctionParameter("source", FlowDataType.ITEM),
            new FlowGraph.FunctionParameter("recipe", FlowDataType.STRING)
        ), List.of());
    }

    static FunctionShape npcActionShape() {
        return new FunctionShape(List.of(
            new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER),
            new FlowGraph.FunctionParameter("entity", FlowDataType.ENTITY),
            new FlowGraph.FunctionParameter("location", FlowDataType.LOCATION),
            new FlowGraph.FunctionParameter("npc", FlowDataType.STRING),
            new FlowGraph.FunctionParameter("rightClick", FlowDataType.BOOLEAN),
            new FlowGraph.FunctionParameter("leftClick", FlowDataType.BOOLEAN),
            new FlowGraph.FunctionParameter("shifting", FlowDataType.BOOLEAN)
        ), List.of());
    }

    static FunctionShape tradeActionShape() {
        return new FunctionShape(List.of(
            new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER),
            new FlowGraph.FunctionParameter("entity", FlowDataType.ENTITY),
            new FlowGraph.FunctionParameter("tradedItem", FlowDataType.ITEM),
            new FlowGraph.FunctionParameter("success", FlowDataType.BOOLEAN),
            new FlowGraph.FunctionParameter("profile", FlowDataType.STRING)
        ), List.of());
    }

    static FunctionShape playerPredicateShape() {
        return new FunctionShape(
            List.of(new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER)),
            List.of(new FlowGraph.FunctionParameter("result", FlowDataType.BOOLEAN, "boolean", "", "false"))
        );
    }

    static FunctionShape recipePredicateShape() {
        return new FunctionShape(
            List.of(
                new FlowGraph.FunctionParameter("player", FlowDataType.PLAYER),
                new FlowGraph.FunctionParameter("item", FlowDataType.ITEM),
                new FlowGraph.FunctionParameter("source", FlowDataType.ITEM),
                new FlowGraph.FunctionParameter("recipe", FlowDataType.STRING)
            ),
            List.of(new FlowGraph.FunctionParameter("result", FlowDataType.BOOLEAN, "boolean", "", "false"))
        );
    }

    static List<String> functionInputOptions(FlowGraph.FunctionParameter input, String context) {
        if (input == null) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        addOption(options, functionInputContextDefault(input, context));
        FlowDataType type = input.getType();
        if (type != null && FlowDataType.PLAYER.isAssignableFrom(type)) {
            addOption(options, "$player");
            addOption(options, "$event.player");
            addOption(options, "$click.player");
            addOption(options, "$recipe.player");
        }
        if (type != null && (FlowDataType.ITEM.isAssignableFrom(type) || FlowDataType.MATERIAL.isAssignableFrom(type))) {
            addOption(options, "$item");
            addOption(options, "$clickedItem");
            addOption(options, "$craftedItem");
            addOption(options, "$cookedItem");
            addOption(options, "$sourceItem");
            addOption(options, "$tradedItem");
            addOption(options, "$resultItem");
            addOption(options, "$event.item");
            addOption(options, "$event.output");
            addOption(options, "$event.source");
        }
        if (type != null && FlowDataType.LOCATION.isAssignableFrom(type)) {
            addOption(options, "$location");
            addOption(options, "$event.location");
        }
        if (type != null && FlowDataType.ENTITY.isAssignableFrom(type)) {
            addOption(options, "$event.entity");
            addOption(options, "$event.target");
            addOption(options, "$player");
        }
        if (type != null && FlowDataType.STRING.isAssignableFrom(type)) {
            addOption(options, "$npcId");
            addOption(options, "$profileId");
            addOption(options, "$hook");
            addOption(options, "$recipe");
            addOption(options, "$world");
            addOption(options, "$permission");
            addOption(options, "$event.id");
        }
        if (type != null && FlowDataType.NUMBER.isAssignableFrom(type)) {
            addOption(options, "$slot");
            addOption(options, "$amount");
            addOption(options, "$event.slot");
        }
        if (type != null && FlowDataType.BOOLEAN.isAssignableFrom(type)) {
            addOption(options, "true");
            addOption(options, "false");
            addOption(options, "$success");
            addOption(options, "$rightClick");
            addOption(options, "$leftClick");
            addOption(options, "$shifting");
            addOption(options, "$sneaking");
            addOption(options, "$shiftClick");
        }
        return options;
    }

    static List<CompactBindingWidget.BindingChoice> functionInputChoices(String serverId, FlowGraph.FunctionParameter input, List<String> fallbackOptions) {
        List<CompactBindingWidget.BindingChoice> choices = new ArrayList<>();
        Set<String> values = new HashSet<>();
        String source = input != null ? input.getOptionsSource() : null;
        if (source != null && !source.isBlank()) {
            for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, source)) {
                if (item == null || item.getValue() == null || item.getValue().isBlank() || !values.add(item.getValue())) {
                    continue;
                }
                Object aliases = item.getMetadata().get("aliases");
                String searchTerms = String.join(" ", item.getValue(), item.getLabel(), item.getDescription(), item.getGroup(),
                    aliases != null ? aliases.toString() : "");
                choices.add(new CompactBindingWidget.BindingChoice(item.getValue(), item.getLabel(), item.getDescription(), item.getIcon(),
                    item.getGroup(), searchTerms));
            }
        }
        if (fallbackOptions != null) {
            for (String value : fallbackOptions) {
                if (value == null || value.isBlank() || !values.add(value)) {
                    continue;
                }
                String group = value.startsWith("$") ? "Context" : "Values";
                choices.add(new CompactBindingWidget.BindingChoice(value, value, "", "", group, value));
            }
        }
        return choices;
    }

    static String functionInputContextDefault(FlowGraph.FunctionParameter input, String context) {
        if (input == null || input.getType() == null) {
            return "";
        }
        FlowDataType type = input.getType();
        String name = input.getName() != null ? input.getName().toLowerCase() : "";
        String scope = context != null ? context.toLowerCase() : "";
        if (FlowDataType.BOOLEAN.isAssignableFrom(type)) {
            if (name.contains("right")) {
                return "$rightClick";
            }
            if (name.contains("left")) {
                return "$leftClick";
            }
            if (name.contains("shift") || name.contains("sneak")) {
                return "$shifting";
            }
            if (scope.contains("trade") || name.contains("success")) {
                return "$success";
            }
            return "false";
        }
        if (FlowDataType.PLAYER.isAssignableFrom(type)) {
            return "$player";
        }
        if (FlowDataType.ITEM.isAssignableFrom(type) || FlowDataType.MATERIAL.isAssignableFrom(type)) {
            if (scope.contains("trade") || name.contains("trade")) {
                return name.contains("result") || name.contains("output") ? "$resultItem" : "$tradedItem";
            }
            if (scope.contains("gui") || name.contains("click")) {
                return "$clickedItem";
            }
            if (scope.contains("cook")) {
                return name.contains("source") || name.contains("input") || name.contains("ingredient") ? "$sourceItem" : "$cookedItem";
            }
            if (scope.contains("recipe") || scope.contains("craft")) {
                return name.contains("source") || name.contains("input") || name.contains("ingredient") ? "$sourceItem" : "$craftedItem";
            }
            return "$item";
        }
        if (FlowDataType.ENTITY.isAssignableFrom(type)) {
            return "$event.entity";
        }
        if (FlowDataType.LOCATION.isAssignableFrom(type)) {
            return "$location";
        }
        if (FlowDataType.NUMBER.isAssignableFrom(type)) {
            if (scope.contains("gui") || name.contains("slot")) {
                return "$slot";
            }
            if (name.contains("amount")) {
                return "$amount";
            }
        }
        if (FlowDataType.STRING.isAssignableFrom(type)) {
            if (scope.contains("npc") || name.contains("npc")) {
                return "$npcId";
            }
            if (scope.contains("trade") || name.contains("profile")) {
                return "$profileId";
            }
            if (scope.contains("recipe") || name.contains("recipe")) {
                return "$recipe";
            }
            if (name.contains("world")) {
                return "$world";
            }
        }
        return "";
    }

    private static void addOption(List<String> options, String option) {
        if (option != null && !option.isBlank() && !options.contains(option)) {
            options.add(option);
        }
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
