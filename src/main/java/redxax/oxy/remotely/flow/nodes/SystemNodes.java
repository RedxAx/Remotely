package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class SystemNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("server_get_info", "Server Info", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("info", NodeDefinition.PinType.DATA, FlowType.JSON_OBJECT)
            .build());
        
        registry.register(new NodeDefinition.Builder("server_get_online_players", "Get Online Players", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("players", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("server_get_max_players", "Get Max Players", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("max", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("server_execute_command", "Execute Command", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("command", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("server_broadcast", "Broadcast Message", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("message", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("sent_count", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("server_shutdown", "Server Shutdown", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("reason", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("server_restart", "Server Restart", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("reason", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("server_reload", "Server Reload", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
    }
}
