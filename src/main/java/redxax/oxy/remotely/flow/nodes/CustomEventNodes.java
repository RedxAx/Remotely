package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class CustomEventNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("custom_event_emit", "Custom Event Emit", NodeDefinition.NodeCategory.EVENT)
            .input("event_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("data_payload", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("custom_event_listen", "Custom Event Listen", NodeDefinition.NodeCategory.EVENT)
            .input("event_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("timeout_ticks", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("listening", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("custom_event_clear", "Custom Event Clear", NodeDefinition.NodeCategory.EVENT)
            .input("event_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("custom_event_get_data", "Custom Event Get Data", NodeDefinition.NodeCategory.EVENT)
            .input("event_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("data_key", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
    }
}
