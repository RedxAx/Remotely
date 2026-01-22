package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class JsonNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("json_parse", "Parse JSON", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("json_string", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("object", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_to_string", "JSON to String", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("object", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("string", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_get", "Get JSON Value", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("object", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .input("path", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_set", "Set JSON Value", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("object", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .input("path", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_delete", "Delete JSON Key", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("object", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .input("path", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_has", "JSON Has Key", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("object", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .input("path", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("has", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_keys", "Get JSON Keys", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("object", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("keys", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_merge", "Merge JSON", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("object1", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .input("object2", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("merged", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_create", "Create JSON Object", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("object", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .build());
        
        registry.register(new NodeDefinition.Builder("json_set_array", "Create JSON Array", NodeDefinition.NodeCategory.DATABASE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("values", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("array", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
    }
}
