package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class PermissionNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("perm_check", "Check Permission", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("permission", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("has", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_grant", "Grant Permission", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("permission", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_revoke", "Revoke Permission", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("permission", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_add_group", "Add Group", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_remove_group", "Remove Group", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_set_group", "Set Primary Group", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_has_group", "Has Group", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("has", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_get_groups", "Get Groups", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("groups", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_get_permissions", "Get Permissions", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("permissions", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_group_has_permission", "Group Has Permission", NodeDefinition.NodeCategory.PERMISSION)
            .input("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("permission", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("has", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_group_add_permission", "Add Group Permission", NodeDefinition.NodeCategory.PERMISSION)
            .input("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("permission", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_group_remove_permission", "Remove Group Permission", NodeDefinition.NodeCategory.PERMISSION)
            .input("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("permission", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_get_group_permissions", "Get Group Permissions", NodeDefinition.NodeCategory.PERMISSION)
            .input("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("permissions", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_get_all_groups", "Get All Groups", NodeDefinition.NodeCategory.PERMISSION)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("groups", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_get_primary_group", "Get Primary Group", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("group", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_set_prefix", "Set Prefix", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("prefix", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_set_suffix", "Set Suffix", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("suffix", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_get_prefix", "Get Prefix", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("prefix", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("perm_get_suffix", "Get Suffix", NodeDefinition.NodeCategory.PERMISSION)
            .input("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("success", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("error", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("suffix", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
    }
}