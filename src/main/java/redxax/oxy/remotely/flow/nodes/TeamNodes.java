package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class TeamNodes implements NodeCategoryRegistrar {

    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("team_create", "Create Team", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_delete", "Delete Team", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_set_display_name", "Set Team Display Name", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_set_prefix", "Set Team Prefix", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("prefix", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_set_suffix", "Set Team Suffix", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("suffix", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_set_color", "Set Team Color", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("color", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_set_allow_friendly_fire", "Allow Friendly Fire", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("allow", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_see_friendly_invisibles", "See Friendly Invisibles", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("allow", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_add_player", "Add Player to Team", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_remove_player", "Remove Player from Team", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("team_has_player", "Has Player", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("has_player", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        registry.register(new NodeDefinition.Builder("team_get_players", "Get Team Players", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("players", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());

        registry.register(new NodeDefinition.Builder("team_get_team", "Get Player's Team", NodeDefinition.NodeCategory.SCOREBOARD)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("team_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("team_get_teams", "Get All Teams", NodeDefinition.NodeCategory.SCOREBOARD)
            .output("team_ids", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
    }
}
