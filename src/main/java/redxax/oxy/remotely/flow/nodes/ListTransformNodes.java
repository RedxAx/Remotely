package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class ListTransformNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("list_slice", "List Slice", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("start", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("end", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_sublist", "List Sublist", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("start", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("count", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_reverse", "List Reverse", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_shuffle", "List Shuffle", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_sort", "List Sort", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_sort_descending", "List Sort Descending", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_filter", "List Filter", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_map", "List Map", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_reduce", "List Reduce", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("initial", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_flatten", "List Flatten", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_unique", "List Unique", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_join", "List Join", NodeDefinition.NodeCategory.DATA)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("separator", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("string", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_concat", "List Concat", NodeDefinition.NodeCategory.DATA)
            .input("listA", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("listB", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_intersect", "List Intersect", NodeDefinition.NodeCategory.DATA)
            .input("listA", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("listB", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_difference", "List Difference", NodeDefinition.NodeCategory.DATA)
            .input("listA", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("listB", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("list_zip", "List Zip", NodeDefinition.NodeCategory.DATA)
            .input("listA", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("listB", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
    }
}
