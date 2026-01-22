package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class TextFormattingNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("format_color", "Format Color", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("color", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("format_bold", "Format Bold", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("format_italic", "Format Italic", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("format_underline", "Format Underline", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("format_strikethrough", "Format Strikethrough", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("format_obfuscated", "Format Obfuscated", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("format_reset", "Format Reset", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("format_hover", "Format Hover", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("hover_text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
        
        registry.register(new NodeDefinition.Builder("format_click", "Format Click", NodeDefinition.NodeCategory.VISUAL)
            .input("text", NodeDefinition.PinType.DATA, FlowType.ANY)
            .input("action", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.ANY)
            .hidden()
            .build());
    }
}
