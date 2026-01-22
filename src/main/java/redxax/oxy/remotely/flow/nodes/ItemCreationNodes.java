package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class ItemCreationNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("item_create", "Create Item", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("material", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_material", "Set Material", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("material", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_amount", "Set Amount", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_damage", "Set Damage", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("damage", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_max_damage", "Set Max Damage", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("max_damage", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_unbreakable", "Set Unbreakable", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("unbreakable", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_custom_name", "Set Custom Name", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_lore", "Set Lore", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("lore", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_add_lore", "Add Lore", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("lore", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_clear_lore", "Clear Lore", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_flags", "Set Flags", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("flags", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_add_flag", "Add Flag", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("flag", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_remove_flag", "Remove Flag", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("flag", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_add_enchant", "Add Enchant", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("enchantment", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("level", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_remove_enchant", "Remove Enchant", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("enchantment", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_clear_enchants", "Clear Enchants", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_custom_model", "Set Custom Model", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("model_data", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_color", "Set Color", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("red", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("green", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("blue", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_skull_owner", "Set Skull Owner", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("owner", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_book_pages", "Set Book Pages", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("title", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("author", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("pages", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_potion_effect", "Set Potion Effect", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("effect", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("duration", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("amplifier", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_get_nbt", "Get NBT", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("nbt", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
        
        registry.register(new NodeDefinition.Builder("item_set_nbt", "Set NBT", NodeDefinition.NodeCategory.INVENTORY)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("nbt", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
    }
}
