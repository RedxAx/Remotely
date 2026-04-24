package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.flow.data.FlowType;

import java.util.ArrayList;
import java.util.Collections;
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
        FUNCTION,
        ENTITY,
        WORLD,
        INVENTORY,
        DATABASE,
        HTTP,
        DISCORD,
        ECONOMY,
        PERMISSION,
        VISUAL,
        SCOREBOARD,
        UTILITY
    }

    public enum WidgetType {
        AUTO,
        TEXT,
        TOGGLE,
        DROPDOWN,
        SEARCHABLE_LIST,
        SLIDER,
        NUMBER,
        MULTILINE,
        COLOR
    }

    private final String id;
    private final String displayName;
    private final NodeCategory category;
    private final List<PinDefinition> inputs;
    private final List<PinDefinition> outputs;
    private final int color;
    private final int priority;
    private final boolean hidden;

    private NodeDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.category = builder.category;
        this.inputs = builder.inputs;
        this.outputs = builder.outputs;
        this.color = builder.color;
        this.priority = builder.priority;
        this.hidden = builder.hidden;
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

    public int getPriority() {
        return priority;
    }

    public boolean isHidden() {
        return hidden;
    }

    public static class PinConstraints {
        private final Double min;
        private final Double max;
        private final Double step;

        public PinConstraints(Double min, Double max, Double step) {
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public Double getMin() {
            return min;
        }

        public Double getMax() {
            return max;
        }

        public Double getStep() {
            return step;
        }
    }

    public static class PinDefinition {
        private final String name;
        private final PinType type;
        private final PinDirection direction;
        private final FlowType dataType;
        private final WidgetType widgetType;
        private final List<String> options;
        private final String optionsSource;
        private final String defaultValue;
        private final PinConstraints constraints;
        private final Map<String, String> visibleWhen;
        private final String description;

        public PinDefinition(String name, PinType type, PinDirection direction, FlowType dataType) {
            this(name, type, direction, dataType, null, null, null, null, null, null, null);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowType dataType,
                             WidgetType widgetType, List<String> options, String defaultValue,
                              PinConstraints constraints, Map<String, String> visibleWhen, String description) {
            this(name, type, direction, dataType, widgetType, options, null, defaultValue, constraints, visibleWhen, description);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowType dataType,
                             WidgetType widgetType, List<String> options, String optionsSource, String defaultValue,
                             PinConstraints constraints, Map<String, String> visibleWhen, String description) {
            this.name = name;
            this.type = type;
            this.direction = direction;
            this.dataType = dataType;
            this.widgetType = widgetType;
            this.options = options != null ? options : Collections.emptyList();
            this.optionsSource = optionsSource;
            this.defaultValue = defaultValue;
            this.constraints = constraints;
            this.visibleWhen = visibleWhen != null ? visibleWhen : Collections.emptyMap();
            this.description = description;
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

        public WidgetType getWidgetType() {
            return widgetType;
        }

        public List<String> getOptions() {
            return options;
        }

        public String getOptionsSource() {
            return optionsSource;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public PinConstraints getConstraints() {
            return constraints;
        }

        public Map<String, String> getVisibleWhen() {
            return visibleWhen;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class Builder {
        private String id;
        private String displayName;
        private NodeCategory category;
        private final List<PinDefinition> inputs = new ArrayList<>();
        private final List<PinDefinition> outputs = new ArrayList<>();
        private int color = 0xFFAAAAAA;
        private int priority = 0;
        private boolean hidden = false;

        public Builder(String id, String displayName, NodeCategory category) {
            this.id = id;
            this.displayName = displayName;
            this.category = category;
        }

        public Builder input(String name, PinType type, FlowType dataType) {
            inputs.add(new PinDefinition(name, type, PinDirection.INPUT, dataType));
            return this;
        }

        public Builder input(PinDefinition pin) {
            inputs.add(pin);
            return this;
        }

        public Builder output(String name, PinType type, FlowType dataType) {
            outputs.add(new PinDefinition(name, type, PinDirection.OUTPUT, dataType));
            return this;
        }

        public Builder output(PinDefinition pin) {
            outputs.add(pin);
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
                case ENTITY: this.color = 0xFF8B4513; break;
                case WORLD: this.color = 0xFF228B22; break;
                case INVENTORY: this.color = 0xFF00CED1; break;
                case DATABASE: this.color = 0xFF4B0082; break;
                case HTTP: this.color = 0xFFFF6347; break;
                case DISCORD: this.color = 0xFF7289DA; break;
                case ECONOMY: this.color = 0xFFFFFF00; break;
                case PERMISSION: this.color = 0xFFBA55D3; break;
                case VISUAL: this.color = 0xFFFF1493; break;
                case SCOREBOARD: this.color = 0xFFDAA520; break;
                case UTILITY: this.color = 0xFFA9A9A9; break;
                default: this.color = 0xFFAAAAAA;
            }
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder hidden() {
            return hidden(true);
        }

        public NodeDefinition build() {
            if (color == 0xFFAAAAAA && category != null) {
                color(category);
            }
            return new NodeDefinition(this);
        }
    }

    public static class PinBuilder {
        private String name;
        private PinType type;
        private PinDirection direction;
        private FlowType dataType;
        private WidgetType widgetType;
        private List<String> options;
        private String optionsSource;
        private String defaultValue;
        private PinConstraints constraints;
        private Map<String, String> visibleWhen;
        private String description;

        public PinBuilder(String name, PinType type, PinDirection direction, FlowType dataType) {
            this.name = name;
            this.type = type;
            this.direction = direction;
            this.dataType = dataType;
        }

        public PinBuilder widget(WidgetType widgetType) {
            this.widgetType = widgetType;
            return this;
        }

        public PinBuilder options(List<String> options) {
            this.options = options;
            return this;
        }

        public PinBuilder optionsSource(String optionsSource) {
            this.optionsSource = optionsSource;
            return this;
        }

        public PinBuilder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public PinBuilder constraints(Double min, Double max, Double step) {
            this.constraints = new PinConstraints(min, max, step);
            return this;
        }

        public PinBuilder visibleWhen(String pinName, String expectedValue) {
            if (this.visibleWhen == null) {
                this.visibleWhen = new HashMap<>();
            }
            this.visibleWhen.put(pinName, expectedValue);
            return this;
        }

        public PinBuilder visibleWhen(Map<String, String> conditions) {
            if (this.visibleWhen == null) {
                this.visibleWhen = new HashMap<>();
            }
            this.visibleWhen.putAll(conditions);
            return this;
        }

        public PinBuilder description(String description) {
            this.description = description;
            return this;
        }

        public PinDefinition build() {
            return new PinDefinition(name, type, direction, dataType, widgetType, options, optionsSource, defaultValue, constraints, visibleWhen, description);
        }
    }
}
