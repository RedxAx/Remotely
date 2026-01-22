package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class SystemEventNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("event:server_start", "Server Start", NodeDefinition.NodeCategory.EVENT)
            .output("server_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:server_stop", "Server Stop", NodeDefinition.NodeCategory.EVENT)
            .output("server_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:plugin_enable", "Plugin Enable", NodeDefinition.NodeCategory.EVENT)
            .output("plugin_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:plugin_disable", "Plugin Disable", NodeDefinition.NodeCategory.EVENT)
            .output("plugin_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:world_load", "World Load", NodeDefinition.NodeCategory.EVENT)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:world_unload", "World Unload", NodeDefinition.NodeCategory.EVENT)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:chunk_load", "Chunk Load", NodeDefinition.NodeCategory.EVENT)
            .output("chunk_x", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("chunk_z", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:chunk_unload", "Chunk Unload", NodeDefinition.NodeCategory.EVENT)
            .output("chunk_x", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("chunk_z", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:server_tick", "Server Tick", NodeDefinition.NodeCategory.EVENT)
            .output("tick_number", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("event:server_save", "Server Save", NodeDefinition.NodeCategory.EVENT)
            .output("world_name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("next", NodeDefinition.PinType.EXEC, FlowType.EXECUTION)
            .build());
    }
}
