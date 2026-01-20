package redxax.oxy.remotely.flow.data;

public enum FlowType {
    EXECUTION(0xFFFFFF, "Execution"),
    STRING(0xDA00FF, "String"),
    NUMBER(0x00FF93, "Number"),
    BOOLEAN(0xD20000, "Boolean"),
    PLAYER(0x0066FF, "Player"),
    LOCATION(0xFFA500, "Location"),
    ITEM(0x00AA00, "Item"),
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
}
