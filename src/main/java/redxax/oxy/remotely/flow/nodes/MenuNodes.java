package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class MenuNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("menu_create", "Create Menu", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("title", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("rows", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("menu_set_item", "Set Item", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("slot", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("material", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("lore", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("flow_to_execute", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("menu_add_item", "Add Item", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("material", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("lore", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("flow_to_execute", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("menu_clear", "Clear Menu", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("menu_open", "Open Menu", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("menu_update", "Update Menu", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("menu_close", "Close Menu", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("menu_set_click_sound", "Set Click Sound", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("sound", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("menu_set_title", "Set Title", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("menu_id", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("title", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
    }
}
