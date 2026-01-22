package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class PlayerInventoryNodes implements NodeCategoryRegistrar {

    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("player_give_item", "Give Item", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_take_item", "Take Item", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_item", "Set Item", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("slot", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_clear_slot", "Clear Slot", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("slot", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_swap_items", "Swap Items", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("slot1", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("slot2", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_helmet", "Set Helmet", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_chestplate", "Set Chestplate", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_leggings", "Set Leggings", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_boots", "Set Boots", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_mainhand", "Set Main Hand", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_offhand", "Set Off Hand", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_inventory_title", "Inventory Title", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("title", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_armor_color", "Armor Color", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("slot", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("red", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("green", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("blue", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("player_repair_item", "Repair Item", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("player_enchant_item", "Enchant Item", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("enchantment", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("level", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("player_unenchant_item", "Unenchant Item", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("enchantment", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("player_clear_enchants", "Clear Enchants", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_item_name", "Item Name", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_item_lore", "Item Lore", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("lore", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_item_flags", "Item Flags", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("flags", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_item_custom_model", "Custom Model", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("model_data", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());

        registry.register(new NodeDefinition.Builder("player_set_item_unbreakable", "Unbreakable", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .input("unbreakable", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());
    }
}
