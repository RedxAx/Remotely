package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class EntityControlNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("entity_set_type", "Set Entity Type", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("entity_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_name", "Set Entity Name", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_custom_name_visible", "Set Name Visible", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("visible", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_health", "Set Health", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("health", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_max_health", "Set Max Health", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("max_health", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_speed", "Set Speed", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("speed", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_damage", "Set Damage", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("damage", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_armor", "Set Armor", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("armor", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_follow_range", "Set Follow Range", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("range", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_knockback_resistance", "Set Knockback Resistance", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("resistance", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_target", "Set Target", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("target", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_clear_target", "Clear Target", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_persistent", "Set Persistent", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("persistent", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_invulnerable", "Set Invulnerable", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("invulnerable", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_silent", "Set Silent", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("silent", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_glowing", "Set Glowing", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("glowing", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_burning", "Set Burning", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("ticks", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_frozen", "Set Frozen", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("ticks", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_wet", "Set Wet", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("wet", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_swimming", "Set Swimming", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("swimming", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_shaking", "Set Shaking", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("shaking", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_baby", "Set Baby", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("is_baby", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_tamed", "Set Tamed", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("tamed", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_owner", "Set Owner", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("owner", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_sitting", "Set Sitting", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("sitting", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_angry", "Set Angry", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("angry", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_love_mode", "Set Love Mode", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("ticks", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_color", "Set Color", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("color", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_variant", "Set Variant", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("variant", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_held_item", "Set Held Item", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_armor", "Set Armor", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("slot", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_set_drop_chances", "Set Drop Chances", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("chance", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_add_drop", "Add Drop", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("item", NodeDefinition.PinType.DATA, FlowType.ITEMSTACK)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_clear_drops", "Clear Drops", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_pickup_item", "Pickup Item", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("can_pickup", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("entity_kill", "Kill Entity", NodeDefinition.NodeCategory.ENTITY)
            .input("entity", NodeDefinition.PinType.DATA, FlowType.ENTITY)
            .input("reason", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
    }
}
