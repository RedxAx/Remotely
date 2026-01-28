package redxax.oxy.remotely.flow.data;

public enum FlowType {
    EXECUTION(0xFFFFFF, "Execution"),
    STRING(0xDA00FF, "String"),
    NUMBER(0x00FF93, "Number"),
    BOOLEAN(0xD20000, "Boolean"),
    PLAYER(0x0066FF, "Player"),
    LOCATION(0xFFA500, "Location"),
    ITEM(0x00AA00, "Item"),
    LIST(0xFF69B4, "List"),
    ENTITY(0x8B4513, "Entity"),
    ITEMSTACK(0x32CD32, "ItemStack"),
    JSON_OBJECT(0x4B0082, "JSONObject"),
    ANY(0x808080, "Any");

    private final int color;
    private final String displayName;

    FlowType(int color, String displayName) {
        this.color = color & 0xFFFFFF;
        this.displayName = displayName;
    }

    public int getColor() {
        return 0xFF000000 | color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isCompatibleWith(FlowType other) {
        if (this == ANY || other == ANY) {
            return true;
        }
        if ((this == PLAYER && other == ENTITY) || (this == ENTITY && other == PLAYER)) {
            return true;
        }
        return this == other;
    }

    public static FlowType fromString(String name) {
        for (FlowType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return ANY;
    }

    public static FlowType fromClassName(String className) {
        String lower = className.toLowerCase();
        if (lower.contains("list") || lower.contains("collection") || lower.contains("array")) return LIST;
        if (lower.contains("entity")) return ENTITY;
        if (lower.contains("itemstack")) return ITEMSTACK;
        if (lower.contains("json") || lower.contains("map")) return JSON_OBJECT;
        if (lower.contains("string")) return STRING;
        if (lower.contains("int") || lower.contains("double") || lower.contains("float") || lower.contains("long")) return NUMBER;
        if (lower.contains("bool")) return BOOLEAN;
        if (lower.contains("player")) return PLAYER;
        if (lower.contains("location") || lower.contains("vector")) return LOCATION;
        if (lower.contains("item")) return ITEM;
        return ANY;
    }
}
