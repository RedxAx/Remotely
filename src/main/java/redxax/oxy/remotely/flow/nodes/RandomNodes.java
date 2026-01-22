package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class RandomNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("random_number", "Random Number", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("min", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("max", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("number", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("random_range", "Random Range", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("min", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("max", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("number", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("random_choice", "Random Choice", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("element", NodeDefinition.PinType.DATA, FlowType.ANY)
            .build());
        
        registry.register(new NodeDefinition.Builder("random_chance", "Random Chance", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("chance_0_to_100", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("random_item", "Random Item", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("items", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());
        
        registry.register(new NodeDefinition.Builder("random_player", "Random Player", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .build());
        
        registry.register(new NodeDefinition.Builder("random_uuid", "Random UUID", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("uuid", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("random_color", "Random Color", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("color", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("dye_color", NodeDefinition.PinType.DATA, FlowType.ANY)
            .build());
    }
}
