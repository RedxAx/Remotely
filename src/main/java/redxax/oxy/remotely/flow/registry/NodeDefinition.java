package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.flow.data.FlowDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    public static final class NodeCategory {
        private static final Map<String, NodeCategory> REGISTRY = new LinkedHashMap<>();

        public static final NodeCategory EVENT = new NodeCategory("event", "Event", 0xFFFF5555, 100);
        public static final NodeCategory ACTION = new NodeCategory("action", "Action", 0xFF5555FF, 200);
        public static final NodeCategory LOGIC = new NodeCategory("logic", "Logic", 0xFFFF55FF, 300);
        public static final NodeCategory DATA = new NodeCategory("data", "Data", 0xFF55FFFF, 400);
        public static final NodeCategory VARIABLE = new NodeCategory("variable", "Variable", 0xFFFFFF55, 500);
        public static final NodeCategory FUNCTION = new NodeCategory("function", "Function", 0xFFFFAA55, 600);
        public static final NodeCategory ENTITY = new NodeCategory("entity", "Entity", 0xFF8B4513, 700);
        public static final NodeCategory BLOCK = new NodeCategory("block", "Block", 0xFF228B22, 800);
        public static final NodeCategory WORLD = new NodeCategory("world", "World", 0xFF228B22, 900);
        public static final NodeCategory INVENTORY = new NodeCategory("inventory", "Inventory", 0xFF00CED1, 1000);
        public static final NodeCategory ITEM = new NodeCategory("item", "Item", 0xFF32CD32, 1100);
        public static final NodeCategory SCOREBOARD = new NodeCategory("scoreboard", "Scoreboard", 0xFFDAA520, 1200);
        public static final NodeCategory ECONOMY = new NodeCategory("economy", "Economy", 0xFFFFFF00, 1300);
        public static final NodeCategory PERMISSION = new NodeCategory("permission", "Permission", 0xFFBA55D3, 1400);
        public static final NodeCategory VISUAL = new NodeCategory("visual", "Visual", 0xFFFF1493, 1500);
        public static final NodeCategory DATABASE = new NodeCategory("database", "Database", 0xFF4B0082, 1600);
        public static final NodeCategory HTTP = new NodeCategory("http", "HTTP", 0xFFFF6347, 1700);
        public static final NodeCategory DISCORD = new NodeCategory("discord", "Discord", 0xFF7289DA, 1800);
        public static final NodeCategory UTILITY = new NodeCategory("utility", "Utility", 0xFFA9A9A9, 1900);

        private final String id;
        private final String displayName;
        private final int color;
        private final int priority;

        private NodeCategory(String id, String displayName, int color, int priority) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.priority = priority;
            REGISTRY.put(id, this);
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColor() {
            return color;
        }

        public int getPriority() {
            return priority;
        }

        public static NodeCategory fromString(String id) {
            if (id == null || id.isBlank()) {
                return UTILITY;
            }
            NodeCategory cat = REGISTRY.get(id.toLowerCase());
            return cat != null ? cat : UTILITY;
        }

        public static List<NodeCategory> values() {
            return List.copyOf(REGISTRY.values());
        }

        public static NodeCategory registerServerCategory(String id, String displayName, int color, int priority) {
            NodeCategory existing = REGISTRY.get(id.toLowerCase());
            if (existing != null) {
                return existing;
            }
            return new NodeCategory(id, displayName, color, priority);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodeCategory)) return false;
            NodeCategory that = (NodeCategory) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return id;
        }
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

    public enum NodeKind {
        EVENT,
        ACTION,
        QUERY,
        PURE,
        FAMILY,
        ALIAS
    }

    private final String id;
    private final String displayName;
    private final NodeCategory category;
    private final List<PinDefinition> inputs;
    private final List<PinDefinition> outputs;
    private final int color;
    private final int priority;
    private final boolean hidden;
    private final String description;
    private final String handler;
    private final Map<String, Object> handlerConfig;
    private final boolean trigger;
    private final String eventType;
    private final List<String> aliases;
    private final List<PinMapping> outputMappings;
    private final int schemaVersion;
    private final NodeKind kind;
    private final Availability availability;
    private final String canonicalId;
    private final List<String> legacyIds;
    private final boolean deprecated;

    private NodeDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.category = builder.category;
        this.inputs = builder.inputs;
        this.outputs = builder.outputs;
        this.color = builder.color;
        this.priority = builder.priority;
        this.hidden = builder.hidden;
        this.description = builder.description;
        this.handler = builder.handler;
        this.handlerConfig = builder.handlerConfig;
        this.trigger = builder.trigger;
        this.eventType = builder.eventType;
        this.aliases = builder.aliases;
        this.outputMappings = builder.outputMappings;
        this.schemaVersion = builder.schemaVersion;
        this.kind = builder.kind;
        this.availability = builder.availability;
        this.canonicalId = builder.canonicalId;
        this.legacyIds = builder.legacyIds;
        this.deprecated = builder.deprecated;
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

    public String getDescription() {
        return description;
    }

    public String getHandler() {
        return handler;
    }

    public Map<String, Object> getHandlerConfig() {
        return handlerConfig;
    }

    public boolean isTrigger() {
        return trigger;
    }

    public String getEventType() {
        return eventType;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public List<PinMapping> getOutputMappings() {
        return outputMappings;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public NodeKind getKind() {
        return kind;
    }

    public Availability getAvailability() {
        return availability;
    }

    public String getCanonicalId() {
        return canonicalId;
    }

    public List<String> getLegacyIds() {
        return legacyIds;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public record PinMapping(String source, String target) {
    }

    public static class Availability {
        private final String plugin;
        private final String platform;
        private final String minVersion;

        public Availability(String plugin, String platform, String minVersion) {
            this.plugin = plugin;
            this.platform = platform;
            this.minVersion = minVersion;
        }

        public String getPlugin() {
            return plugin;
        }

        public String getPlatform() {
            return platform;
        }

        public String getMinVersion() {
            return minVersion;
        }
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
        private final FlowDataType dataType;
        private final WidgetType widgetType;
        private final List<String> options;
        private final String optionsSource;
        private final String defaultValue;
        private final PinConstraints constraints;
        private final Map<String, String> visibleWhen;
        private final String description;

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType) {
            this(name, type, direction, dataType, null, null, null, null, null, null, null);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType,
                             WidgetType widgetType, List<String> options, String defaultValue,
                              PinConstraints constraints, Map<String, String> visibleWhen, String description) {
            this(name, type, direction, dataType, widgetType, options, null, defaultValue, constraints, visibleWhen, description);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType,
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

        public FlowDataType getDataType() {
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
        private String description;
        private String handler;
        private Map<String, Object> handlerConfig;
        private boolean trigger = false;
        private String eventType;
        private List<String> aliases = Collections.emptyList();
        private List<PinMapping> outputMappings = Collections.emptyList();
        private int schemaVersion = 1;
        private NodeKind kind;
        private Availability availability;
        private String canonicalId;
        private List<String> legacyIds = Collections.emptyList();
        private boolean deprecated;

        public Builder(String id, String displayName, NodeCategory category) {
            this.id = id;
            this.displayName = displayName;
            this.category = category;
        }

        public Builder input(String name, PinType type, FlowDataType dataType) {
            inputs.add(new PinDefinition(name, type, PinDirection.INPUT, dataType));
            return this;
        }

        public Builder input(PinDefinition pin) {
            inputs.add(pin);
            return this;
        }

        public Builder output(String name, PinType type, FlowDataType dataType) {
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
            if (category != null) {
                this.color = category.getColor();
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

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder handler(String handler) {
            this.handler = handler;
            return this;
        }

        public Builder handlerConfig(Map<String, Object> handlerConfig) {
            this.handlerConfig = handlerConfig;
            return this;
        }

        public Builder trigger(boolean trigger) {
            this.trigger = trigger;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder aliases(List<String> aliases) {
            this.aliases = aliases != null ? aliases : Collections.emptyList();
            return this;
        }

        public Builder outputMappings(List<PinMapping> outputMappings) {
            this.outputMappings = outputMappings != null ? outputMappings : Collections.emptyList();
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder kind(NodeKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder availability(Availability availability) {
            this.availability = availability;
            return this;
        }

        public Builder canonicalId(String canonicalId) {
            this.canonicalId = canonicalId;
            return this;
        }

        public Builder legacyIds(List<String> legacyIds) {
            this.legacyIds = legacyIds != null ? legacyIds : Collections.emptyList();
            return this;
        }

        public Builder deprecated(boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        public Builder hidden() {
            return hidden(true);
        }

        public NodeDefinition build() {
            if (color == 0xFFAAAAAA && category != null) {
                color(category);
            }
            if (kind == null) {
                kind = inferKind();
            }
            return new NodeDefinition(this);
        }

        private NodeKind inferKind() {
            if (trigger) {
                return NodeKind.EVENT;
            }
            if (canonicalId != null && !canonicalId.isBlank() && hidden) {
                return NodeKind.ALIAS;
            }
            if (handler != null && List.of("player", "entity", "world", "block", "inventory", "itemstack").contains(handler)) {
                return NodeKind.FAMILY;
            }
            boolean hasFlowInput = inputs.stream().anyMatch(pin -> pin.getType() == PinType.FLOW && pin.getDirection() == PinDirection.INPUT);
            boolean hasFlowOutput = outputs.stream().anyMatch(pin -> pin.getType() == PinType.FLOW && pin.getDirection() == PinDirection.OUTPUT);
            if (hasFlowInput || hasFlowOutput) {
                return NodeKind.ACTION;
            }
            return NodeKind.QUERY;
        }
    }

    public static class PinBuilder {
        private String name;
        private PinType type;
        private PinDirection direction;
        private FlowDataType dataType;
        private WidgetType widgetType;
        private List<String> options;
        private String optionsSource;
        private String defaultValue;
        private PinConstraints constraints;
        private Map<String, String> visibleWhen;
        private String description;

        public PinBuilder(String name, PinType type, PinDirection direction, FlowDataType dataType) {
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
