package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.flow.data.FlowType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeDefinition {
    public enum PinType {
        FLOW,
        DATA,
        EXEC
    }

    public enum PinDirection {
        INPUT,
        OUTPUT
    }

    public enum NodeCategory {
        EVENT,
        ACTION,
        LOGIC,
        DATA,
        VARIABLE,
        FUNCTION
    }

    private final String id;
    private final String displayName;
    private final NodeCategory category;
    private final List<PinDefinition> inputs;
    private final List<PinDefinition> outputs;
    private final int color;

    private NodeDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.category = builder.category;
        this.inputs = builder.inputs;
        this.outputs = builder.outputs;
        this.color = builder.color;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NodeCategory getCategory() {
        return category;
    }

    public List<PinDefinition> getInputs() {
        return inputs;
    }

    public List<PinDefinition> getOutputs() {
        return outputs;
    }

    public int getColor() {
        return color;
    }

    public static class PinDefinition {
        private final String name;
        private final PinType type;
        private final PinDirection direction;
        private final FlowType dataType;

        public PinDefinition(String name, PinType type, PinDirection direction, FlowType dataType) {
            this.name = name;
            this.type = type;
            this.direction = direction;
            this.dataType = dataType;
        }

        public String getName() {
            return name;
        }

        public PinType getType() {
            return type;
        }

        public PinDirection getDirection() {
            return direction;
        }

        public FlowType getDataType() {
            return dataType;
        }
    }

    public static class Builder {
        private String id;
        private String displayName;
        private NodeCategory category;
        private final List<PinDefinition> inputs = new ArrayList<>();
        private final List<PinDefinition> outputs = new ArrayList<>();
        private int color = 0xFFAAAAAA;

        public Builder(String id, String displayName, NodeCategory category) {
            this.id = id;
            this.displayName = displayName;
            this.category = category;
        }

        public Builder input(String name, PinType type, FlowType dataType) {
            inputs.add(new PinDefinition(name, type, PinDirection.INPUT, dataType));
            return this;
        }

        public Builder output(String name, PinType type, FlowType dataType) {
            outputs.add(new PinDefinition(name, type, PinDirection.OUTPUT, dataType));
            return this;
        }

        public Builder color(int color) {
            this.color = color;
            return this;
        }

        public Builder color(NodeCategory category) {
            switch (category) {
                case EVENT: this.color = 0xFFFF5555; break;
                case ACTION: this.color = 0xFF5555FF; break;
                case LOGIC: this.color = 0xFFFF55FF; break;
                case DATA: this.color = 0xFF55FFFF; break;
                case VARIABLE: this.color = 0xFFFFFF55; break;
                case FUNCTION: this.color = 0xFFFFAA55; break;
                default: this.color = 0xFFAAAAAA;
            }
            return this;
        }

        public NodeDefinition build() {
            if (color == 0xFFAAAAAA && category != null) {
                color(category);
            }
            return new NodeDefinition(this);
        }
    }
}
