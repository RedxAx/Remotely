package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class FlowControlNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("if_else", "If/Else", NodeDefinition.NodeCategory.LOGIC)
            .input("condition", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("true", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("false", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("switch_case", "Switch Case", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("cases", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("matched", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("case_0", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("case_1", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("case_2", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("case_3", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("default", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("branch_random", "Branch Random", NodeDefinition.NodeCategory.LOGIC)
            .input("branches", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("selected", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("branch_0", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("branch_1", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("branch_2", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("branch_3", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("branch_all", "Branch All", NodeDefinition.NodeCategory.LOGIC)
            .output("branch_0", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("branch_1", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("branch_2", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("branch_3", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("loop_count", "Loop Count", NodeDefinition.NodeCategory.LOGIC)
            .input("count", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("loop", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("completed", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("loop_for_each", "Loop For Each", NodeDefinition.NodeCategory.LOGIC)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .output("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("element", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("loop", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("completed", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("loop_for_each_player", "Loop Players", NodeDefinition.NodeCategory.LOGIC)
            .output("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("loop", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("completed", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("loop_for_each_entity", "Loop Entities", NodeDefinition.NodeCategory.LOGIC)
            .input("radius", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("center", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .output("loop", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("completed", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
    }
}
