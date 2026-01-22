package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.flow.data.FlowType;
import java.util.HashMap;
import java.util.Map;

public class NodeRegistry {
    private final Map<String, NodeDefinition> nodes = new HashMap<>();

    private static NodeRegistry INSTANCE;

    public NodeRegistry() {
        registerStandardNodes();
        INSTANCE = this;
    }

    public static NodeRegistry getInstance() {
        return INSTANCE;
    }

    private void registerStandardNodes() {
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.MathNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.LogicNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.StringNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.TextFormattingNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.FlowControlNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.BlockNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.RegionNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.WorldStateNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.PlayerEventNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.EntityEventNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.WorldEventNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.PlayerActionNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.PlayerInventoryNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.PlayerMessagingNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.EntitySpawnNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.EntityControlNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.InventoryNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.ItemCreationNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.MenuNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.VariableNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.FileNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.JsonNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.TimeNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.RandomNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.ConversionNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.DebugNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.SystemNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.SystemEventNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.CustomEventNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.SoundNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.ParticleNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.TitleNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.EconomyNodes());
        new CategoryNodeLoader(this).registerCategory(new redxax.oxy.remotely.flow.nodes.PermissionNodes());

        register(new NodeDefinition.Builder("event:click", "On Click", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("event:join", "On Player Join", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("message", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        register(new NodeDefinition.Builder("event:quit", "On Player Quit", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("message", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        register(new NodeDefinition.Builder("event:chat", "On Chat", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("message", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .build());

        register(new NodeDefinition.Builder("event:death", "On Player Death", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("message", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .build());

        register(new NodeDefinition.Builder("event:block_break", "On Block Break", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("block_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("is_cancelled", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("event:block_place", "On Block Place", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("block_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("against_type", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("is_cancelled", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("event:sneak", "On Sneak", NodeDefinition.NodeCategory.EVENT)
            .output("next", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("player", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("is_sneaking", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("log", "Log To Console", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("text", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("player_message", "Send Message", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("text", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("give_item", "Give Item", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("material", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("amount", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("if", "If", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("condition", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("true", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("false", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("equals", "Equals", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("b", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("not_equals", "Not Equals", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("b", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("contains", "Contains", NodeDefinition.NodeCategory.LOGIC)
            .input("string", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("substring", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("number", "Number", NodeDefinition.NodeCategory.DATA)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());

        register(new NodeDefinition.Builder("string", "String", NodeDefinition.NodeCategory.DATA)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());

        register(new NodeDefinition.Builder("boolean", "Boolean", NodeDefinition.NodeCategory.DATA)
            .input("value", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("value", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("get_variable", "Get Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());

        register(new NodeDefinition.Builder("set_variable", "Set Variable", NodeDefinition.NodeCategory.VARIABLE)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("value", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .hidden()
            .build());

        register(new NodeDefinition.Builder("call_function", "Call Function", NodeDefinition.NodeCategory.FUNCTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("function", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("get_player_info", "Get Player Info", NodeDefinition.NodeCategory.DATA)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .output("name", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("uuid", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("health", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .output("is_op", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("player_kick", "Kick Player", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("reason", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("player_teleport", "Teleport Player", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("target", NodeDefinition.PinType.DATA, FlowType.PLAYER)
            .input("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .input("x", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("y", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("z", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("cancel_event", "Cancel Event", NodeDefinition.NodeCategory.ACTION)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("cancel", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());

        register(new NodeDefinition.Builder("get_location", "Get Location", NodeDefinition.NodeCategory.DATA)
            .input("x", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("y", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("z", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("location", NodeDefinition.PinType.DATA, FlowType.LOCATION)
            .build());

        register(new NodeDefinition.Builder("compare", "Compare", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("equals", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("greater", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .output("less", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());

        register(new NodeDefinition.Builder("loop", "Loop", NodeDefinition.NodeCategory.LOGIC)
            .input("flow", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .input("count", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("loop", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .output("index", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("completed", NodeDefinition.PinType.FLOW, FlowType.EXECUTION)
            .build());
    }

    public void register(NodeDefinition definition) {
        nodes.put(definition.getId(), definition);
    }

    public NodeDefinition getDefinition(String nodeId) {
        return nodes.get(nodeId);
    }

    public boolean hasDefinition(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    public Map<String, NodeDefinition> getAllDefinitions() {
        return new HashMap<>(nodes);
    }
}
