package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class EntitySpawnNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("entity_spawn", "Spawn Entity", NodeDefinition.NodeCategory.ENTITY)
            .input("entity_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_despawn", "Despawn Entity", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_get_nearby", "Get Nearby Entities", NodeDefinition.NodeCategory.ENTITY)
            .input("center", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .input("radius", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("entity_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("entities", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_get_all", "Get All Entities", NodeDefinition.NodeCategory.ENTITY)
            .input("world", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .input("entity_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("entities", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_teleport", "Teleport Entity", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_remove", "Remove Entity", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_get_player_nearby", "Get Nearby Players", NodeDefinition.NodeCategory.ENTITY)
            .input("center", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .input("radius", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("players", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_get_mob_nearby", "Get Nearby Mobs", NodeDefinition.NodeCategory.ENTITY)
            .input("center", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .input("radius", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("entity_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("mobs", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
    }
}
