package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class WorldEventNodes implements NodeCategoryRegistrar {

    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("event:block_redstone", "On Redstone Change", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("old_power", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("new_power", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:physics", "On Block Physics", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:explosion", "On Explosion", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("power", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("break_blocks", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("fire", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:grow", "On Block Grow", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("new_state", NodeDefinition.PinType.DATA, FlowType.ANY)
            .build());

        registry.register(new NodeDefinition.Builder("event:block_from_to", "On Block From To", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("from_block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("to_block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("from_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("to_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:structure_spawn", "On Structure Spawn", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("structure_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:world_save", "On World Save", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:weather_change", "On Weather Change", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("old_weather", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("new_weather", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:time_change", "On Time Change", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("old_time", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("new_time", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:block_break", "On Block Break", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:block_place", "On Block Place", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("placed_against", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("against_location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:block_dispense", "On Block Dispense", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("event:block_fade", "On Block Fade", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("new_state", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:block_form", "On Block Form", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("new_state", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:block_spread", "On Block Spread", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("new_block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:lightning_strike", "On Lightning Strike", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("struck_entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:leaf_decay", "On Leaf Decay", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:sign_change", "On Sign Change", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("lines", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());

        registry.register(new NodeDefinition.Builder("event:furnace_smelt", "On Furnace Smelt", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("furnace", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:inventory_open", "On Inventory Open", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("inventory_title", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("inventory_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:inventory_close", "On Inventory Close", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("inventory_title", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("inventory_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        registry.register(new NodeDefinition.Builder("event:note_play", "On Note Play", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("instrument", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("note", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:piston_extend", "On Piston Extend", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("length", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:piston_retract", "On Piston Retract", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("block", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());
    }
}
