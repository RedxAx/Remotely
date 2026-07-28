package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.data.FlowResourceReference;

import java.util.Map;

final class ReSyncResourceDropCapabilities {
    private static final String CUSTOM_FUNCTION_NODE_PREFIX = "custom_function:";

    private ReSyncResourceDropCapabilities() {
    }

    static DropSpec forResource(ReSyncResourceDragPayload resource) {
        if (resource == null || resource.type() == null || resource.id() == null || resource.id().isBlank()) {
            return null;
        }
        return switch (resource.type()) {
            case ReSyncResourceDragPayload.FLOW -> new DropSpec("flow.run", "selected_flow");
            case ReSyncResourceDragPayload.FUNCTION -> new DropSpec(CUSTOM_FUNCTION_NODE_PREFIX + resource.id(), null);
            case ReSyncResourceDragPayload.COMMAND -> new DropSpec("command.run", "command");
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> new DropSpec("custom_content.get", "content");
            case ReSyncResourceDragPayload.GUI -> new DropSpec("gui.open", "gui");
            case ReSyncResourceDragPayload.SCOREBOARD -> new DropSpec("scoreboard.show.template", "scoreboard_id");
            case ReSyncResourceDragPayload.TAB -> new DropSpec("tab.apply", "tab");
            case ReSyncResourceDragPayload.CHAT -> new DropSpec("chat.get.profile", "profile");
            case ReSyncResourceDragPayload.MOTD_PROFILE -> new DropSpec("motd.get.profile", "profile");
            case ReSyncResourceDragPayload.MESSAGE_RULE -> new DropSpec("message_rule.get", "rule");
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> new DropSpec("recipe.get", "recipe");
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> new DropSpec("text.get.template", "template");
            case ReSyncResourceDragPayload.ADVANCEMENT_TREE -> new DropSpec("advancement.get.tree", "tree");
            case ReSyncResourceDragPayload.DIALOG -> new DropSpec("dialog.get", "dialog");
            case ReSyncResourceDragPayload.TRADE_PROFILE -> new DropSpec("trade.get.profile", "profile");
            case ReSyncResourceDragPayload.NPC_DEFINITION -> new DropSpec("npc.get", "npc");
            case ReSyncResourceDragPayload.LOOT_TABLE -> new DropSpec("loot.get.table", "loot_table");
            case ReSyncResourceDragPayload.WORLDGEN -> new DropSpec("worldgen.get", "project");
            case ReSyncResourceDragPayload.WORLD -> new DropSpec("world.world_get_by_name", "world_name");
            case ReSyncResourceDragPayload.VARIABLE_DEFINITION -> new DropSpec("automation.variable", "variable", ReSyncResourceDragPayload.VARIABLE_DEFINITION);
            case ReSyncResourceDragPayload.TIMER_DEFINITION -> new DropSpec("automation.timer", "timer", ReSyncResourceDragPayload.TIMER_DEFINITION);
            case ReSyncResourceDragPayload.SCHEDULE_DEFINITION -> new DropSpec("automation.schedule", "schedule", ReSyncResourceDragPayload.SCHEDULE_DEFINITION);
            default -> null;
        };
    }

    record DropSpec(String nodeType, String inputPin, String referenceKind) {
        DropSpec(String nodeType, String inputPin) {
            this(nodeType, inputPin, null);
        }

        Map<String, Object> inputValues(String resourceId) {
            if (inputPin == null || inputPin.isBlank()) {
                return Map.of();
            }
            Object value = referenceKind != null ? new FlowResourceReference(referenceKind, resourceId, "server") : resourceId;
            return Map.of(inputPin, value);
        }
    }
}
