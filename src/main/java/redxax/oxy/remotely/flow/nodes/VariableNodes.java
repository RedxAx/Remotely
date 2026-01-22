package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class VariableNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("variable_access", "Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("mode", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("scope", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("exists", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("variables", NodeDefinition.PinType.DATA, FlowType.LIST)
            .priority(-10)
            .build());

        registry.register(new NodeDefinition.Builder("variable_set_global", "Set Global Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_set_local", "Set Local Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_set_player", "Set Player Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_get_global", "Get Global Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_get_local", "Get Local Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_get_player", "Get Player Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_delete", "Delete Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("scope", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_exists", "Variable Exists", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("scope", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("exists", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_list_all", "List All Variables", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("scope", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("variables", NodeDefinition.PinType.DATA, FlowType.LIST)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_increment", "Increment Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("scope", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_decrement", "Decrement Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("scope", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_multiply", "Multiply Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("scope", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("variable_divide", "Divide Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("scope", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());
    }
}
