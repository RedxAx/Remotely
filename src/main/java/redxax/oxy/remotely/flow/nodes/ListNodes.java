package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class ListNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("list_create", "Create List", NodeDefinition.NodeCategory.DATA)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_of", "List Of", NodeDefinition.NodeCategory.DATA)
            .input("values", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_range", "List Range", NodeDefinition.NodeCategory.DATA)
            .input("min", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("max", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("step", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_repeat", "List Repeat", NodeDefinition.NodeCategory.DATA)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("count", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_add", "List Add", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_insert", "List Insert", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_remove", "List Remove", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_remove_at", "List Remove At", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_clear", "List Clear", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_get", "List Get", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_set", "List Set", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_size", "List Size", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("size", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_is_empty", "List Is Empty", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("empty", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_contains", "List Contains", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("contains", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_index_of", "List Index Of", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_count", "List Count", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("count", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
    }
}
