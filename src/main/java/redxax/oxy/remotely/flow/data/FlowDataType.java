package redxax.oxy.remotely.flow.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlowDataType {
    private static final Map<String, FlowDataType> REGISTRY = new LinkedHashMap<>();

    public static final FlowDataType EXECUTION = new FlowDataType("execution", null, 0xFFFFFF, "Execution");
    public static final FlowDataType ANY = new FlowDataType("any", null, 0x808080, "Any");
    public static final FlowDataType STRING = new FlowDataType("string", null, 0xDA00FF, "String");
    public static final FlowDataType NUMBER = new FlowDataType("number", null, 0x00FF93, "Number");
    public static final FlowDataType FLOAT = new FlowDataType("float", NUMBER, 0x00BFFF, "Float");
    public static final FlowDataType BOOLEAN = new FlowDataType("boolean", null, 0xD20000, "Boolean");
    public static final FlowDataType ENTITY = new FlowDataType("entity", null, 0x8B4513, "Entity");
    public static final FlowDataType LIVING_ENTITY = new FlowDataType("living_entity", ENTITY, 0xA0522D, "Living Entity");
    public static final FlowDataType PLAYER = new FlowDataType("player", LIVING_ENTITY, 0x0066FF, "Player");
    public static final FlowDataType MATERIAL = new FlowDataType("material", null, 0x00AA00, "Material");
    public static final FlowDataType BLOCK = new FlowDataType("block", null, 0x228B22, "Block");
    public static final FlowDataType ITEM = new FlowDataType("item", MATERIAL, 0x32CD32, "Item");
    public static final FlowDataType ITEMSTACK = ITEM;
    public static final FlowDataType WORLD = new FlowDataType("world", null, 0x00CED1, "World");
    public static final FlowDataType BIOME = new FlowDataType("biome", null, 0x20B2AA, "Biome");
    public static final FlowDataType VECTOR = new FlowDataType("vector", null, 0x7FFFD4, "Vector");
    public static final FlowDataType LOCATION = new FlowDataType("location", VECTOR, 0xFFA500, "Location");
    public static final FlowDataType VECTOR2 = new FlowDataType("vector2", VECTOR, 0x7FFFD4, "Vector2");
    public static final FlowDataType VECTOR3 = new FlowDataType("vector3", VECTOR, 0x40E0D0, "Vector3");
    public static final FlowDataType SEED = new FlowDataType("seed", NUMBER, 0xFFD700, "Seed");
    public static final FlowDataType COLOR = new FlowDataType("color", null, 0xFF66CC, "Color");
    public static final FlowDataType UUID = new FlowDataType("uuid", null, 0x708090, "UUID");
    public static final FlowDataType GAMEMODE = new FlowDataType("gamemode", null, 0x4169E1, "Gamemode");
    public static final FlowDataType DIFFICULTY = new FlowDataType("difficulty", null, 0xDC143C, "Difficulty");
    public static final FlowDataType ENTITY_TYPE = new FlowDataType("entity_type", null, 0xCD853F, "Entity Type");
    public static final FlowDataType ENCHANTMENT = new FlowDataType("enchantment", null, 0x9370DB, "Enchantment");
    public static final FlowDataType INVENTORY = new FlowDataType("inventory", null, 0x4682B4, "Inventory");
    public static final FlowDataType POTION_EFFECT = new FlowDataType("potion_effect", null, 0xFF1493, "Potion Effect");
    public static final FlowDataType SOUND = new FlowDataType("sound", null, 0xFFB347, "Sound");
    public static final FlowDataType ADVANCEMENT = new FlowDataType("advancement", null, 0xFFD700, "Advancement");
    public static final FlowDataType PERMISSION_GROUP = new FlowDataType("permission_group", null, 0x6A5ACD, "Permission Group");
    public static final FlowDataType SCOREBOARD = new FlowDataType("scoreboard", null, 0x1E90FF, "Scoreboard");
    public static final FlowDataType TEAM = new FlowDataType("team", null, 0x00BFFF, "Team");
    public static final FlowDataType REGION = new FlowDataType("region", null, 0x9ACD32, "Region");
    public static final FlowDataType COMPONENT = new FlowDataType("component", STRING, 0xE066FF, "Component");
    public static final FlowDataType JSON_OBJECT = new FlowDataType("json_object", null, 0x4B0082, "JSON Object");
    public static final FlowDataType LIST = new FlowDataType("list", null, 0xFF69B4, "List");
    public static final FlowDataType MAP = new FlowDataType("map", null, 0x9932CC, "Map");
    public static final FlowDataType SET = new FlowDataType("set", null, 0xFF4500, "Set");
    public static final FlowDataType QUEUE = new FlowDataType("queue", null, 0x2E8B57, "Queue");
    public static final FlowDataType STACK = new FlowDataType("stack", null, 0x4682B4, "Stack");

    private final String id;
    private FlowDataType parent;
    private int color;
    private String displayName;
    private boolean canStringify;

    private FlowDataType(String id, FlowDataType parent, int color, String displayName) {
        this(id, parent, color, displayName, !"execution".equals(id) && !"any".equals(id));
    }

    private FlowDataType(String id, FlowDataType parent, int color, String displayName, boolean canStringify) {
        this.id = id;
        this.parent = parent;
        this.color = color;
        this.displayName = displayName;
        this.canStringify = canStringify;
        REGISTRY.put(id, this);
    }

    static {
        REGISTRY.put("flow", EXECUTION);
    }

    public String getId() {
        return id;
    }

    public FlowDataType getParent() {
        return parent;
    }

    public int getColor() {
        return 0xFF000000 | color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAssignableFrom(FlowDataType other) {
        if (this == ANY || this == other) return true;
        if (other == null) return false;
        if (other.parent != null && (this == other.parent || isAssignableFrom(other.parent))) return true;
        return false;
    }

    public boolean canConvertTo(FlowDataType target) {
        if (target == null) return false;
        if (this == ANY || target == ANY) return true;
        if (target.isAssignableFrom(this)) return true;
        if (target == STRING) return canStringify();
        return false;
    }

    public boolean canStringify() {
        return canStringify;
    }

    public static List<FlowDataType> values() {
        return REGISTRY.values().stream().distinct().filter(type -> type != EXECUTION).toList();
    }

    public static FlowDataType fromString(String id) {
        if (id == null || id.isEmpty()) return ANY;
        String normalized = id.toLowerCase();
        FlowDataType type = REGISTRY.get(normalized);
        if (type != null) return type;
        if (normalized.contains(":")) {
            String displayName = normalized.substring(normalized.lastIndexOf(':') + 1).replace('_', ' ');
            if (!displayName.isBlank()) {
                displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
            }
            return registerServerType(normalized, displayName, 0x808080, "string", true);
        }
        return ANY;
    }

    public static FlowDataType registerServerType(String id, String displayName, int color, String parentId, boolean canStringify) {
        if (id == null || id.isBlank()) {
            return ANY;
        }
        FlowDataType parent = parentId != null ? fromString(parentId) : null;
        if (parent == ANY && parentId != null && !parentId.equalsIgnoreCase("any")) {
            parent = null;
        }
        FlowDataType existing = REGISTRY.get(id.toLowerCase());
        if (existing != null) {
            existing.parent = parent;
            existing.color = color;
            existing.displayName = displayName != null && !displayName.isBlank() ? displayName : existing.displayName;
            existing.canStringify = canStringify;
            return existing;
        }
        FlowDataType type = new FlowDataType(id, parent, color, displayName, canStringify);
        return type;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowDataType)) return false;
        FlowDataType that = (FlowDataType) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
