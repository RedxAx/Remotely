package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class PlayerEventNodes implements NodeCategoryRegistrar {

    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("event:move", "On Player Move", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("from_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("to_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("distance", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:interact", "On Player Interact", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("clicked_block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("clicked_entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("action_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_interact", "On Entity Interact", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_damage", "On Entity Damage", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("damager", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("victim", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("damage", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:shoot", "On Projectile Shoot", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("projectile", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:projectile_hit", "On Projectile Hit", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("projectile", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("hit_entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:pickup", "On Item Pickup", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("event:drop", "On Item Drop", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("event:consume", "On Item Consume", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("event:craft", "On Item Craft", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("event:smelt", "On Item Smelt", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("event:enchant", "On Item Enchant", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("enchantment", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("level", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:bed_enter", "On Bed Enter", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("bed_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:bed_leave", "On Bed Leave", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("bed_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:respawn", "On Player Respawn", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("respawn_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("death_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:level_up", "On Level Up", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("old_level", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("new_level", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:command", "On Player Command", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("command_label", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("args", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:tab_complete", "On Tab Complete", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("command", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("completions", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:teleport", "On Player Teleport", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("from_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("to_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("cause", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:gamemode_change", "On Gamemode Change", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("old_gamemode", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("new_gamemode", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:flight_toggle", "On Flight Toggle", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("is_flying", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        registry.register(new NodeDefinition.Builder("event:vanish_toggle", "On Vanish Toggle", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("is_vanished", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        registry.register(new NodeDefinition.Builder("event:fish", "On Player Fish", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("state", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("caught", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:shear", "On Entity Shear", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:item_damage", "On Item Damage", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("damage", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:item_break", "On Item Break", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("broken_item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("event:exp_change", "On Experience Change", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
    }
}
