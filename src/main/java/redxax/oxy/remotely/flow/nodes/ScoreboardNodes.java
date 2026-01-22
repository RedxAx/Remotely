package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class ScoreboardNodes implements NodeCategoryRegistrar {

    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("scoreboard_create", "Create Objective", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("criteria", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_delete", "Delete Objective", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_set_display", "Set Display Slot", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("display_slot", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_set_score", "Set Score", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("score", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_add_score", "Add Score", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_remove_score", "Remove Score", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_reset_score", "Reset Score", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("score", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_get_score", "Get Score", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("score", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_get_objectives", "Get Objectives", NodeDefinition.NodeCategory.SCOREBOARD)
            .output("objective_ids", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_set_name", "Set Objective Name", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_set_render_type", "Set Render Type", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("render_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("scoreboard_clear", "Clear Score", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("objective_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
    }
}
