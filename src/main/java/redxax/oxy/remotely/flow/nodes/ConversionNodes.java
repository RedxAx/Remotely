package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class ConversionNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("to_string", "To String", NodeDefinition.NodeCategory.DATA)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("string", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("to_number", "To Number", NodeDefinition.NodeCategory.DATA)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("number", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("to_boolean", "To Boolean", NodeDefinition.NodeCategory.DATA)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("boolean", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("to_player", "To Player", NodeDefinition.NodeCategory.DATA)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("uuid_or_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .build());
        
        registry.register(new NodeDefinition.Builder("to_location", "To Location", NodeDefinition.NodeCategory.DATA)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("x", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("y", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("z", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("world", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());
        
        registry.register(new NodeDefinition.Builder("to_item", "To Item", NodeDefinition.NodeCategory.DATA)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("material", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());
        
        registry.register(new NodeDefinition.Builder("to_list", "To List", NodeDefinition.NodeCategory.DATA)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("value_or_separator", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
    }
}
