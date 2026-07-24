package redxax.oxy.remotely.flow.registry;

import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowTypeRef;
import restudio.resync.flow.contract.FlowNodeCategoryContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

        public static final NodeCategory EVENT = builtin("event");
        public static final NodeCategory ACTION = builtin("action");
        public static final NodeCategory PLAYER = builtin("player");
        public static final NodeCategory LOGIC = builtin("logic");
        public static final NodeCategory NETWORK = builtin("network");
        public static final NodeCategory CHAT = builtin("chat");
        public static final NodeCategory DATA = builtin("data");
        public static final NodeCategory VARIABLE = builtin("variable");
        public static final NodeCategory FLOW = builtin("flow");
        public static final NodeCategory FUNCTION = builtin("function");
        public static final NodeCategory COMMAND = builtin("command");
        public static final NodeCategory ENTITY = builtin("entity");
        public static final NodeCategory BLOCK = builtin("block");
        public static final NodeCategory WORLD = builtin("world");
        public static final NodeCategory INVENTORY = builtin("inventory");
        public static final NodeCategory ITEM = builtin("item");
        public static final NodeCategory SCOREBOARD = builtin("scoreboard");
        public static final NodeCategory TRADE = builtin("trade");
        public static final NodeCategory NPC = builtin("npc");
        public static final NodeCategory LOOT = builtin("loot");
        public static final NodeCategory MENU = builtin("menu");
        public static final NodeCategory TAB_LIST = builtin("tab_list");
        public static final NodeCategory DIALOG = builtin("dialog");
        public static final NodeCategory CUSTOM_CONTENT = builtin("custom_content");
        public static final NodeCategory RECIPE = builtin("recipe");
        public static final NodeCategory ECONOMY = builtin("economy");
        public static final NodeCategory ADVANCEMENT = builtin("advancement");
        public static final NodeCategory TEXT = builtin("text");
        public static final NodeCategory PERMISSION = builtin("permission");
        public static final NodeCategory ABILITY = builtin("ability");
        public static final NodeCategory VISUAL = builtin("visual");
        public static final NodeCategory DATABASE = builtin("database");
        public static final NodeCategory HTTP = builtin("http");
        public static final NodeCategory DISCORD = builtin("discord");
        public static final NodeCategory UTILITY = builtin("utility");
        public static final NodeCategory WORLD_GEN = builtin("world_gen");

        private final String id;
        private final String displayName;
        private final int color;
        private final int priority;
        private final String owner;
        private final boolean resolved;

        private NodeCategory(String id, String displayName, int color, int priority) {
            this(id, displayName, color, priority, "builtin", true, true);
        }

        private static NodeCategory builtin(String id) {
            FlowNodeCategoryContract.Category category = FlowNodeCategoryContract.category(id);
            return new NodeCategory(category.id(), category.displayName(), category.color(), category.priority());
        }

        private NodeCategory(String id, String displayName, int color, int priority, String owner, boolean resolved, boolean register) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.priority = priority;
            this.owner = owner;
            this.resolved = resolved;
            if (register) {
                REGISTRY.put(id, this);
            }
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

        public String getOwner() {
            return owner;
        }

        public boolean isResolved() {
            return resolved;
        }

        public static NodeCategory fromString(String id) {
            if (id == null || id.isBlank()) {
                return UTILITY;
            }
            NodeCategory cat = REGISTRY.get(id.toLowerCase());
            if (cat != null) {
                return cat;
            }
            String normalized = id.toLowerCase();
            String displayName = normalized.substring(normalized.lastIndexOf(':') + 1).replace('_', ' ');
            if (!displayName.isBlank()) {
                displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
            }
            String owner = normalized.contains(":") ? normalized.substring(0, normalized.indexOf(':')) : "unresolved";
            return new NodeCategory(normalized, displayName, 0xFFAAAAAA, Integer.MAX_VALUE, owner, false, false);
        }

        public static List<NodeCategory> values() {
            return List.copyOf(REGISTRY.values());
        }

        public static NodeCategory registerServerCategory(String id, String displayName, int color, int priority) {
            NodeCategory existing = REGISTRY.get(id.toLowerCase());
            if (existing != null) {
                return existing;
            }
            return new NodeCategory(id, displayName, color, priority, "server", true, false);
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
    private final String hiddenReason;
    private final String owner;
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
    private final List<String> tags;
    private final List<String> examples;
    private final String family;
    private final boolean recommended;
    private final String replacementFor;
    private final String authorizationPolicy;
    private final boolean sensitive;
    private final boolean destructive;
    private final String auditPolicy;
    private final String confirmationPolicy;
    private final String clockDomain;

    private NodeDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.category = builder.category;
        this.inputs = builder.inputs;
        this.outputs = builder.outputs;
        this.color = builder.color;
        this.priority = builder.priority;
        this.hidden = builder.hidden;
        this.hiddenReason = builder.hiddenReason;
        this.owner = builder.owner;
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
        this.tags = builder.tags;
        this.examples = builder.examples;
        this.family = builder.family;
        this.recommended = builder.recommended;
        this.replacementFor = builder.replacementFor;
        this.authorizationPolicy = builder.authorizationPolicy;
        this.sensitive = builder.sensitive;
        this.destructive = builder.destructive;
        this.auditPolicy = builder.auditPolicy;
        this.confirmationPolicy = builder.confirmationPolicy;
        this.clockDomain = builder.clockDomain;
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
        return NodeDiscoveryPreferences.discoveryPriority(id, priority, recommended);
    }

    public boolean isHidden() {
        return hidden;
    }

    public String getHiddenReason() {
        return hiddenReason != null ? hiddenReason : "";
    }

    public String getOwner() {
        return owner != null && !owner.isBlank() ? owner : "builtin";
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

    public List<String> getTags() {
        return tags;
    }

    public List<String> getExamples() {
        return examples;
    }

    public String getFamily() {
        return family;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public String getReplacementFor() {
        return replacementFor;
    }

    public String getAuthorizationPolicy() {
        return authorizationPolicy != null && !authorizationPolicy.isBlank() ? authorizationPolicy : "trusted_server_flow";
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public boolean isDestructive() {
        return destructive;
    }

    public String getAuditPolicy() {
        return auditPolicy != null && !auditPolicy.isBlank() ? auditPolicy : "none";
    }

    public String getConfirmationPolicy() {
        return confirmationPolicy != null && !confirmationPolicy.isBlank() ? confirmationPolicy : "none";
    }

    public String getClockDomain() {
        return clockDomain != null ? clockDomain : "";
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

    public static class RepeatablePin {
        private final String groupId;
        private final int minItems;
        private final int maxItems;
        private final String itemLabel;

        public RepeatablePin(String groupId, int minItems, int maxItems, String itemLabel) {
            this.groupId = groupId;
            this.minItems = Math.max(0, minItems);
            this.maxItems = Math.max(this.minItems, maxItems);
            this.itemLabel = itemLabel;
        }

        public String getGroupId() {
            return groupId;
        }

        public int getMinItems() {
            return minItems;
        }

        public int getMaxItems() {
            return maxItems;
        }

        public String getItemLabel() {
            return itemLabel;
        }
    }

    public static class PinDefinition {
        private final String name;
        private final PinType type;
        private final PinDirection direction;
        private final FlowDataType dataType;
        private final FlowTypeRef typeRef;
        private final RepeatablePin repeatable;
        private final WidgetType widgetType;
        private final List<String> options;
        private final String optionsSource;
        private final String defaultValue;
        private final PinConstraints constraints;
        private final Map<String, String> visibleWhen;
        private final String description;
        private final boolean optional;

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType) {
            this(name, type, direction, dataType, null, null, null, null, null, null, null, false);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType, boolean optional) {
            this(name, type, direction, dataType, null, null, null, null, null, null, null, optional);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType, FlowTypeRef typeRef) {
            this(name, type, direction, dataType, null, null, null, null, null, null, null, false, typeRef);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType,
                             WidgetType widgetType, List<String> options, String defaultValue,
                              PinConstraints constraints, Map<String, String> visibleWhen, String description) {
            this(name, type, direction, dataType, widgetType, options, null, defaultValue, constraints, visibleWhen, description, false);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType,
                             WidgetType widgetType, List<String> options, String optionsSource, String defaultValue,
                             PinConstraints constraints, Map<String, String> visibleWhen, String description) {
            this(name, type, direction, dataType, widgetType, options, optionsSource, defaultValue, constraints, visibleWhen, description, false);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType,
                             WidgetType widgetType, List<String> options, String optionsSource, String defaultValue,
                             PinConstraints constraints, Map<String, String> visibleWhen, String description, boolean optional) {
            this(name, type, direction, dataType, widgetType, options, optionsSource, defaultValue, constraints, visibleWhen,
                description, optional, dataType != null ? FlowTypeRef.simple(dataType.getId()) : FlowTypeRef.simple("any"));
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType,
                             WidgetType widgetType, List<String> options, String optionsSource, String defaultValue,
                             PinConstraints constraints, Map<String, String> visibleWhen, String description, boolean optional,
                             FlowTypeRef typeRef) {
            this(name, type, direction, dataType, widgetType, options, optionsSource, defaultValue, constraints, visibleWhen,
                description, optional, typeRef, null);
        }

        public PinDefinition(String name, PinType type, PinDirection direction, FlowDataType dataType,
                             WidgetType widgetType, List<String> options, String optionsSource, String defaultValue,
                             PinConstraints constraints, Map<String, String> visibleWhen, String description, boolean optional,
                             FlowTypeRef typeRef, RepeatablePin repeatable) {
            this.name = name;
            this.type = type;
            this.direction = direction;
            this.dataType = dataType;
            this.typeRef = typeRef;
            this.repeatable = repeatable;
            this.widgetType = widgetType;
            this.options = options != null ? options : Collections.emptyList();
            this.optionsSource = optionsSource;
            this.defaultValue = defaultValue;
            this.constraints = constraints;
            this.visibleWhen = visibleWhen != null ? visibleWhen : Collections.emptyMap();
            this.description = description;
            this.optional = optional;
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

        public FlowTypeRef getTypeRef() {
            return typeRef != null ? typeRef : FlowTypeRef.simple(dataType != null ? dataType.getId() : "any");
        }

        public RepeatablePin getRepeatable() {
            return repeatable;
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

        public boolean isOptional() {
            return optional;
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
        private String hiddenReason = "";
        private String owner = "builtin";
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
        private List<String> tags = Collections.emptyList();
        private List<String> examples = Collections.emptyList();
        private String family;
        private boolean recommended;
        private String replacementFor;
        private String authorizationPolicy = "trusted_server_flow";
        private boolean sensitive;
        private boolean destructive;
        private String auditPolicy = "none";
        private String confirmationPolicy = "none";
        private String clockDomain = "";

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

        public Builder hiddenReason(String hiddenReason) {
            this.hiddenReason = hiddenReason != null ? hiddenReason : "";
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner != null && !owner.isBlank() ? owner : "builtin";
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

        public Builder tags(List<String> tags) {
            this.tags = tags != null ? tags : Collections.emptyList();
            return this;
        }

        public Builder examples(List<String> examples) {
            this.examples = examples != null ? examples : Collections.emptyList();
            return this;
        }

        public Builder family(String family) {
            this.family = family;
            return this;
        }

        public Builder recommended(boolean recommended) {
            this.recommended = recommended;
            return this;
        }

        public Builder replacementFor(String replacementFor) {
            this.replacementFor = replacementFor;
            return this;
        }

        public Builder authorizationPolicy(String authorizationPolicy) {
            this.authorizationPolicy = authorizationPolicy != null && !authorizationPolicy.isBlank() ? authorizationPolicy : "trusted_server_flow";
            return this;
        }

        public Builder sensitive(boolean sensitive) {
            this.sensitive = sensitive;
            return this;
        }

        public Builder destructive(boolean destructive) {
            this.destructive = destructive;
            return this;
        }

        public Builder auditPolicy(String auditPolicy) {
            this.auditPolicy = auditPolicy != null && !auditPolicy.isBlank() ? auditPolicy : "none";
            return this;
        }

        public Builder confirmationPolicy(String confirmationPolicy) {
            this.confirmationPolicy = confirmationPolicy != null && !confirmationPolicy.isBlank() ? confirmationPolicy : "none";
            return this;
        }

        public Builder clockDomain(String clockDomain) {
            this.clockDomain = clockDomain != null ? clockDomain : "";
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
            if (description == null || description.isBlank()) {
                description = displayName + " Flow capability.";
            }
            if (tags == null || tags.isEmpty()) {
                LinkedHashSet<String> resolvedTags = new LinkedHashSet<>();
                if (id != null) {
                    for (String token : id.toLowerCase(Locale.ROOT).split("[.:_\\-]+")) {
                        if (!token.isBlank()) {
                            resolvedTags.add(token);
                        }
                    }
                }
                if (category != null) {
                    resolvedTags.add(category.getId().toLowerCase(Locale.ROOT));
                }
                resolvedTags.add(kind.name().toLowerCase(Locale.ROOT));
                tags = List.copyOf(resolvedTags);
            }
            if (examples == null || examples.isEmpty()) {
                examples = List.of(defaultUsageHint());
            }
            if (destructive && "none".equals(confirmationPolicy)) {
                confirmationPolicy = "explicit_flow_intent";
            }
            return new NodeDefinition(this);
        }

        private String defaultUsageHint() {
            return switch (kind) {
                case EVENT -> "Connect the event Flow output to the actions that should run.";
                case ACTION -> "Connect the required inputs, then continue from the Flow output.";
                case QUERY, PURE -> "Connect the inputs and use the typed outputs in another node.";
                case FAMILY -> "Choose the operation and connect the inputs required by that operation.";
                case ALIAS -> "Replace this node with its canonical equivalent.";
            };
        }

        private NodeKind inferKind() {
            if (trigger) {
                return NodeKind.EVENT;
            }
            if (canonicalId != null && !canonicalId.isBlank() && hidden) {
                return NodeKind.ALIAS;
            }
            boolean hasFlowInput = inputs.stream().anyMatch(pin -> pin.getType() == PinType.FLOW && pin.getDirection() == PinDirection.INPUT);
            boolean hasFlowOutput = outputs.stream().anyMatch(pin -> pin.getType() == PinType.FLOW && pin.getDirection() == PinDirection.OUTPUT);
            if (handler != null && List.of("player", "entity", "world", "block", "inventory", "itemstack").contains(handler)) {
                return hasFlowInput ? NodeKind.FAMILY : NodeKind.QUERY;
            }
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
        private FlowTypeRef typeRef;
        private RepeatablePin repeatable;
        private WidgetType widgetType;
        private List<String> options;
        private String optionsSource;
        private String defaultValue;
        private PinConstraints constraints;
        private Map<String, String> visibleWhen;
        private String description;
        private boolean optional;

        public PinBuilder(String name, PinType type, PinDirection direction, FlowDataType dataType) {
            this.name = name;
            this.type = type;
            this.direction = direction;
            this.dataType = dataType;
            this.typeRef = dataType != null ? FlowTypeRef.simple(dataType.getId()) : FlowTypeRef.simple("any");
        }

        public PinBuilder typeRef(FlowTypeRef typeRef) {
            this.typeRef = typeRef;
            return this;
        }

        public PinBuilder repeatable(String groupId, int minItems, int maxItems, String itemLabel) {
            this.repeatable = new RepeatablePin(groupId, minItems, maxItems, itemLabel);
            return this;
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

        public PinBuilder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public PinDefinition build() {
            return new PinDefinition(name, type, direction, dataType, widgetType, options, optionsSource, defaultValue, constraints,
                visibleWhen, description, optional, typeRef, repeatable);
        }
    }
}
