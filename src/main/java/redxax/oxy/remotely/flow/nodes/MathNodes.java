package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class MathNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("math_add", "Add", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_subtract", "Subtract", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_multiply", "Multiply", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_divide", "Divide", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_modulo", "Modulo", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_power", "Power", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_sqrt", "Square Root", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_abs", "Absolute", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_floor", "Floor", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_ceil", "Ceil", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_round", "Round", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_min", "Minimum", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_max", "Maximum", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_clamp", "Clamp", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("min", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("max", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_lerp", "Lerp", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("b", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("t", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_sin", "Sin", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_cos", "Cos", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_tan", "Tan", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_to_radians", "To Radians", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_to_degrees", "To Degrees", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_distance", "Distance", NodeDefinition.NodeCategory.LOGIC)
            .input("x1", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("y1", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("z1", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("x2", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("y2", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("z2", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("math_negate", "Negate", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
    }
}
