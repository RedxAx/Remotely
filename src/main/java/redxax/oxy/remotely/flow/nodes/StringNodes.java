package redxax.oxy.remotely.flow.nodes;

import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeCategoryRegistrar;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.data.FlowType;

public class StringNodes implements NodeCategoryRegistrar {
    
    @Override
    public void registerNodes(NodeRegistry registry) {
        registry.register(new NodeDefinition.Builder("string_concat", "Concatenate", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("b", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_substring", "Substring", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("start", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .input("length", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_split", "Split", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("delimiter", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.LIST)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_join", "Join", NodeDefinition.NodeCategory.LOGIC)
            .input("list", NodeDefinition.PinType.DATA, FlowType.LIST)
            .input("separator", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_replace", "Replace", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("target", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("replacement", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_replace_regex", "Replace Regex", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("pattern", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("replacement", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_upper", "To Uppercase", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_lower", "To Lowercase", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_capitalize", "Capitalize", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_trim", "Trim", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_length", "Length", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_reverse", "Reverse", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_repeat", "Repeat", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("count", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .output("result", NodeDefinition.PinType.DATA, FlowType.STRING)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_contains", "Contains", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("substring", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_starts_with", "Starts With", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("prefix", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_ends_with", "Ends With", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("suffix", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_matches", "Matches Regex", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("pattern", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_index_of", "Index Of", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("substring", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_last_index_of", "Last Index Of", NodeDefinition.NodeCategory.LOGIC)
            .input("value", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("substring", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.NUMBER)
            .build());
        
        registry.register(new NodeDefinition.Builder("string_equals_ignore_case", "Equals Ignore Case", NodeDefinition.NodeCategory.LOGIC)
            .input("a", NodeDefinition.PinType.DATA, FlowType.STRING)
            .input("b", NodeDefinition.PinType.DATA, FlowType.STRING)
            .output("result", NodeDefinition.PinType.DATA, FlowType.BOOLEAN)
            .build());
    }
}
