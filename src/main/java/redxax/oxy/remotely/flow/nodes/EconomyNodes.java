package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class EconomyNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("eco_get_balance", "Get Balance", NodeDefinition.NodeCategory.ECONOMY)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("balance", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_set_balance", "Set Balance", NodeDefinition.NodeCategory.ECONOMY)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("balance", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_add_balance", "Add Balance", NodeDefinition.NodeCategory.ECONOMY)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("new_balance", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_remove_balance", "Remove Balance", NodeDefinition.NodeCategory.ECONOMY)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("new_balance", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_transfer", "Transfer Balance", NodeDefinition.NodeCategory.ECONOMY)
            .input("from_player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("to_player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_has_balance", "Has Balance", NodeDefinition.NodeCategory.ECONOMY)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("has", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_format", "Format Currency", NodeDefinition.NodeCategory.ECONOMY)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("formatted", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_get_currency", "Get Currency Name", NodeDefinition.NodeCategory.ECONOMY)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("singular", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("plural", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_deposit", "Deposit", NodeDefinition.NodeCategory.ECONOMY)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_withdraw", "Withdraw", NodeDefinition.NodeCategory.ECONOMY)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_has_bank", "Has Bank Support", NodeDefinition.NodeCategory.ECONOMY)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("has_bank", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("eco_create_bank", "Create Bank Account", NodeDefinition.NodeCategory.ECONOMY)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
    }
}