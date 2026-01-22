package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class WorldStateNodes implements NodeCategoryRegistrar {

    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("world_set_time", "Set Time", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("time", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_weather", "Set Weather", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("weather", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_thunder", "Set Thunder", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("thundering", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_spawn", "Set Spawn", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_difficulty", "Set Difficulty", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("difficulty", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_pvp", "Set PVP", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("pvp", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_keep_spawn", "Set Keep Spawn", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("keep_spawn_time", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_auto_save", "Set Auto Save", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("auto_save", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_spawn_lightning", "Spawn Lightning", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .input("effect", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_border_size", "Set Border Size", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("size", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_border_damage", "Set Border Damage", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("damage_amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        registry.register(new NodeDefinition.Builder("world_set_border_warning", "Set Border Warning", NodeDefinition.NodeCategory.WORLD)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("world", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("warning_distance", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
    }
}
