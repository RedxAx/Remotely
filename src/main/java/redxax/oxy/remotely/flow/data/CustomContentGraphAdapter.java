package redxax.oxy.remotely.flow.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CustomContentGraphAdapter {
    public static final String ITEM_NODE = "custom_content.item";
    public static final String BLOCK_NODE = "custom_content.block";
    public static final String ARMOR_NODE = "custom_content.armor";
    public static final String FLOW_BRANCHES_KEY = "__flow_branches";

    private static final Map<String, List<TriggerDescriptor>> TRIGGERS = Map.of(
        "item", List.of(
            new TriggerDescriptor("use", "item.use"),
            new TriggerDescriptor("left_click", "item.left_click"),
            new TriggerDescriptor("right_click", "item.right_click"),
            new TriggerDescriptor("hit_entity", "item.hit_entity"),
            new TriggerDescriptor("damage_entity", "item.damage_entity"),
            new TriggerDescriptor("break_block", "item.break_block"),
            new TriggerDescriptor("consume", "item.consume"),
            new TriggerDescriptor("drop", "item.drop"),
            new TriggerDescriptor("pickup", "item.pickup")
        ),
        "block", List.of(
            new TriggerDescriptor("place", "block.place"),
            new TriggerDescriptor("break", "block.break"),
            new TriggerDescriptor("interact", "block.interact"),
            new TriggerDescriptor("step_on", "block.step_on"),
            new TriggerDescriptor("nearby_player", "block.nearby_player"),
            new TriggerDescriptor("redstone", "block.redstone"),
            new TriggerDescriptor("tick", "block.tick")
        ),
        "armor", List.of(
            new TriggerDescriptor("equip", "armor.equip"),
            new TriggerDescriptor("unequip", "armor.unequip"),
            new TriggerDescriptor("damaged", "armor.damaged"),
            new TriggerDescriptor("tick", "armor.tick"),
            new TriggerDescriptor("full_set", "armor.full_set"),
            new TriggerDescriptor("full_set_tick", "armor.full_set_tick")
        )
    );

    private CustomContentGraphAdapter() {
    }

    public static FlowGraph createContentGraph(String id, String type, String displayName) {
        FlowGraph graph = new FlowGraph();
        graph.setId(id);
        Map<String, Object> inputs = new HashMap<>();
        String normalizedType = normalizeType(type);
        inputs.put("content_id", id);
        inputs.put("name", displayName == null || displayName.isBlank() ? defaultName(normalizedType) : displayName);
        inputs.put("provider", "vanilla");
        inputs.put("external_id", "");
        inputs.put("material", defaultMaterial(normalizedType));
        inputs.put("armor_slot", "armor".equals(normalizedType) ? "chest" : "");
        inputs.put("cooldown_scope", "player");
        inputs.put("cooldown_ticks", 0);
        inputs.put("permission", "");
        inputs.put("cancel_event", false);
        inputs.put("consume_event", false);
        inputs.put("require_sneaking", false);
        inputs.put("require_on_ground", false);
        inputs.put("hand_filter", "any");
        inputs.put("target_filter", "any");
        inputs.put("allowed_worlds", "");
        inputs.put("denied_worlds", "");
        inputs.put("chance_percent", 100.0);
        inputs.put("max_activations_per_tick", 0);
        inputs.put(FLOW_BRANCHES_KEY, List.of(defaultBranch(normalizedType)));
        graph.getNodes().put(UUID.randomUUID().toString(), new FlowNode(nodeType(normalizedType), 120, 120, inputs));
        return graph;
    }

    public static boolean isContentGraph(FlowGraph graph) {
        return contentType(graph) != null;
    }

    public static String contentType(FlowGraph graph) {
        FlowNode node = findStartNode(graph);
        return node != null ? typeFromNode(node.getType()) : null;
    }

    public static FlowNode findStartNode(FlowGraph graph) {
        if (graph == null || graph.getNodes() == null) {
            return null;
        }
        return graph.getNodes().values().stream()
            .filter(node -> node != null && typeFromNode(node.getType()) != null)
            .findFirst()
            .orElse(null);
    }

    public static CustomContentDefinition toDefinition(FlowGraph graph) {
        String type = contentType(graph);
        FlowNode node = findStartNode(graph);
        if (graph == null || node == null || type == null) {
            return null;
        }
        Map<String, Object> inputs = node.getInputValues() != null ? node.getInputValues() : Map.of();
        String id = text(inputs.get("content_id"), graph.getId());
        if (id.isBlank()) {
            id = graph.getId();
        }
        CustomContentDefinition definition = new CustomContentDefinition();
        definition.setId(id);
        definition.setFlowId(graph.getId());
        definition.setType(type);
        definition.setDisplayName(text(inputs.get("name"), defaultName(type)));
        definition.setProvider(text(inputs.get("provider"), "vanilla"));
        definition.setExternalId(text(inputs.get("external_id"), ""));
        definition.setMaterial(normalizeMaterial(text(inputs.get("material"), defaultMaterial(type))));
        definition.setArmorSlot(text(inputs.get("armor_slot"), "armor".equals(type) ? "chest" : ""));
        definition.setAbilities(abilities(graph, node, type, id, inputs));
        return definition;
    }

    public static String displayName(FlowGraph graph) {
        FlowNode node = findStartNode(graph);
        if (node == null || node.getInputValues() == null) {
            return graph != null ? graph.getId() : "";
        }
        return text(node.getInputValues().get("name"), graph.getId());
    }

    public static String material(FlowGraph graph) {
        FlowNode node = findStartNode(graph);
        if (node == null || node.getInputValues() == null) {
            return "";
        }
        return text(node.getInputValues().get("material"), "");
    }

    public static String typeFromNode(String nodeType) {
        return switch (nodeType == null ? "" : nodeType) {
            case ITEM_NODE -> "item";
            case BLOCK_NODE -> "block";
            case ARMOR_NODE -> "armor";
            default -> null;
        };
    }

    public static String nodeType(String type) {
        return switch (normalizeType(type)) {
            case "block" -> BLOCK_NODE;
            case "armor" -> ARMOR_NODE;
            default -> ITEM_NODE;
        };
    }

    public static String triggerForPin(String type, String pin) {
        for (TriggerDescriptor descriptor : TRIGGERS.getOrDefault(normalizeType(type), List.of())) {
            if (descriptor.pin().equals(pin)) {
                return descriptor.trigger();
            }
        }
        return null;
    }

    public static String pinForTrigger(String trigger) {
        if (trigger == null) {
            return null;
        }
        for (List<TriggerDescriptor> descriptors : TRIGGERS.values()) {
            for (TriggerDescriptor descriptor : descriptors) {
                if (descriptor.trigger().equalsIgnoreCase(trigger)) {
                    return descriptor.pin();
                }
            }
        }
        return null;
    }

    private static List<CustomAbilityBinding> abilities(FlowGraph graph, FlowNode node, String type, String contentId, Map<String, Object> inputs) {
        List<String> branches = selectedBranches(graph, node, type, inputs);
        CustomTriggerRule rule = rule(inputs);
        List<CustomAbilityBinding> bindings = new ArrayList<>();
        for (String branch : branches) {
            String trigger = triggerForPin(type, branch);
            if (trigger == null) {
                continue;
            }
            CustomAbilityBinding binding = new CustomAbilityBinding(contentId + "." + trigger.replace('.', '_'), trigger, graph.getId());
            binding.setRule(rule);
            bindings.add(binding);
        }
        return bindings;
    }

    private static List<String> selectedBranches(FlowGraph graph, FlowNode node, String type, Map<String, Object> inputs) {
        Object stored = inputs.get(FLOW_BRANCHES_KEY);
        List<String> branches = new ArrayList<>();
        if (stored instanceof List<?> list) {
            for (Object entry : list) {
                if (entry != null) {
                    branches.add(entry.toString());
                }
            }
        }
        String nodeId = findNodeId(graph, node);
        if (nodeId != null && graph.getConnections() != null) {
            for (FlowConnection connection : graph.getConnections()) {
                if (nodeId.equals(connection.getSourceNodeId()) && triggerForPin(type, connection.getSourcePin()) != null && !branches.contains(connection.getSourcePin())) {
                    branches.add(connection.getSourcePin());
                }
            }
        }
        if (branches.isEmpty()) {
            branches.add(defaultBranch(type));
        }
        return branches;
    }

    private static String findNodeId(FlowGraph graph, FlowNode node) {
        if (graph == null || graph.getNodes() == null || node == null) {
            return null;
        }
        for (Map.Entry<String, FlowNode> entry : graph.getNodes().entrySet()) {
            if (entry.getValue() == node) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static CustomTriggerRule rule(Map<String, Object> inputs) {
        CustomTriggerRule rule = new CustomTriggerRule();
        rule.setCooldownScope(text(inputs.get("cooldown_scope"), "player"));
        rule.setCooldownTicks(number(inputs.get("cooldown_ticks"), 0).intValue());
        rule.setPermission(text(inputs.get("permission"), ""));
        rule.setCancelEvent(bool(inputs.get("cancel_event")));
        rule.setConsumeEvent(bool(inputs.get("consume_event")));
        rule.setRequireSneaking(bool(inputs.get("require_sneaking")));
        rule.setRequireOnGround(bool(inputs.get("require_on_ground")));
        rule.setHandFilter(text(inputs.get("hand_filter"), "any"));
        rule.setTargetFilter(text(inputs.get("target_filter"), "any"));
        rule.setAllowedWorlds(csv(inputs.get("allowed_worlds")));
        rule.setDeniedWorlds(csv(inputs.get("denied_worlds")));
        rule.setChancePercent(number(inputs.get("chance_percent"), 100.0).doubleValue());
        rule.setMaxActivationsPerTick(number(inputs.get("max_activations_per_tick"), 0).intValue());
        return rule;
    }

    private static List<String> csv(Object value) {
        String text = text(value, "");
        if (text.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static Number number(Object value, Number fallback) {
        if (value instanceof Number number) {
            return number;
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String text(Object value, String fallback) {
        return value != null ? value.toString() : fallback;
    }

    private static String normalizeType(String type) {
        String normalized = type != null ? type.toLowerCase(Locale.ROOT) : "item";
        return switch (normalized) {
            case "block", "armor" -> normalized;
            default -> "item";
        };
    }

    private static String defaultName(String type) {
        return switch (normalizeType(type)) {
            case "block" -> "New Block";
            case "armor" -> "New Armor";
            default -> "New Item";
        };
    }

    private static String defaultMaterial(String type) {
        return switch (normalizeType(type)) {
            case "block" -> "STONE";
            case "armor" -> "IRON_CHESTPLATE";
            default -> "STICK";
        };
    }

    private static String defaultBranch(String type) {
        return switch (normalizeType(type)) {
            case "block" -> "interact";
            case "armor" -> "tick";
            default -> "use";
        };
    }

    private static String normalizeMaterial(String material) {
        String normalized = material == null ? "" : material.trim();
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized.isBlank() ? "STICK" : normalized.toUpperCase(Locale.ROOT);
    }

    public record TriggerDescriptor(String pin, String trigger) {
    }
}
