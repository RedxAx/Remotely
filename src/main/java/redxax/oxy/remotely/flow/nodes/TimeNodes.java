package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class TimeNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("delay_ticks", "Delay Ticks", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("ticks", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("delay_seconds", "Delay Seconds", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("seconds", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("delay_minutes", "Delay Minutes", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("minutes", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("schedule_at_time", "Schedule at Time", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("time_string", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("flow_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("task_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("schedule_interval", "Schedule Interval", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("interval_ticks", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("flow_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("task_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("schedule_cron", "Schedule Cron", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("cron_expr", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("flow_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("task_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("cancel_schedule", "Cancel Schedule", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("task_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("get_current_time", "Get Current Time", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("time", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("get_current_ticks", "Get Current Ticks", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("ticks", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("get_server_uptime", "Get Server Uptime", NodeDefinition.NodeCategory.UTILITY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("uptime", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("uptime_ms", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
    }
}
