package redxax.oxy.remotely.worldgen.registry;

import redxax.oxy.remotely.flow.data.FlowDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorldGenNodeDefinition {
    private final String id;
    private final String displayName;
    private final String category;
    private final List<PinDefinition> inputs;
    private final List<PinDefinition> outputs;
    private final int color;
    private final int priority;
    private final String description;
    private final boolean hidden;

    private WorldGenNodeDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.category = builder.category;
        this.inputs = List.copyOf(builder.inputs);
        this.outputs = List.copyOf(builder.outputs);
        this.color = builder.color;
        this.priority = builder.priority;
        this.description = builder.description;
        this.hidden = builder.hidden;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
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

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHidden() {
        return hidden;
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static class Builder {
        private final String id;
        private final String displayName;
        private String category = "World Gen";
        private final List<PinDefinition> inputs = new ArrayList<>();
        private final List<PinDefinition> outputs = new ArrayList<>();
        private int color = 0xFF228B22;
        private int priority = 2000;
        private String description = "";
        private boolean hidden;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder input(String name, FlowDataType dataType, Object defaultValue, String widgetType) {
            inputs.add(new PinDefinition(name, dataType, PinDirection.INPUT, defaultValue, widgetType, Map.of(), "", null, List.of()));
            return this;
        }

        public Builder input(String name, FlowDataType dataType, Object defaultValue, String widgetType, List<String> options) {
            inputs.add(new PinDefinition(name, dataType, PinDirection.INPUT, defaultValue, widgetType, Map.of(), "", null, options != null ? options : List.of()));
            return this;
        }

        public Builder output(String name, FlowDataType dataType) {
            outputs.add(new PinDefinition(name, dataType, PinDirection.OUTPUT, null, null, Map.of(), "", null, List.of()));
            return this;
        }

        public Builder color(int color) {
            this.color = color;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public WorldGenNodeDefinition build() {
            return new WorldGenNodeDefinition(this);
        }
    }

    public enum PinDirection {
        INPUT,
        OUTPUT
    }

    public record PinDefinition(String name, FlowDataType dataType, PinDirection direction, Object defaultValue, String widgetType,
                                Map<String, Object> constraints, String description, String visibleWhen, List<String> options) {
    }
}
