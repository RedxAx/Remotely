package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class EntityEventNodes implements NodeCategoryRegistrar {

    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("event:entity_spawn", "On Entity Spawn", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("entity_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_target", "On Entity Target", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("target", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_breed", "On Entity Breed", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity1", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("entity2", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("experience", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("bred_entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_tame", "On Entity Tame", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("tamer", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("entity_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_transform", "On Entity Transform", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("old_entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("new_entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("new_entity_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_death", "On Entity Death", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("killer", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:item_merge", "On Item Merge", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item1", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("item2", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("event:chunk_load", "On Chunk Load", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("chunk", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("chunk_x", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("chunk_z", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:chunk_unload", "On Chunk Unload", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("chunk", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("chunk_x", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("chunk_z", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_combust", "On Entity Combust", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("duration", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_damaged", "On Entity Damaged", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("damager", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("damage", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("cause", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_heal", "On Entity Heal", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_regain_health", "On Entity Regain Health", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("new_health", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_pickup", "On Entity Pickup", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("remaining", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_drop", "On Entity Drop", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("dropped", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_enter_portal", "On Entity Enter Portal", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("portal_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        registry.register(new NodeDefinition.Builder("event:entity_exit_portal", "On Entity Exit Portal", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("portal_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
    }
}
