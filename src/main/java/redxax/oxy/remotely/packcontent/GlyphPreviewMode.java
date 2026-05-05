package redxax.oxy.remotely.packcontent;

public enum GlyphPreviewMode {
    OFF("Off"),
    HOVER("Hover"),
    INLINE_HOVER("Inline + Hover");

    private final String displayName;

    GlyphPreviewMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean inline() {
        return this == INLINE_HOVER;
    }

    public boolean hover() {
        return this == HOVER || this == INLINE_HOVER;
    }

    public static GlyphPreviewMode fromConfig(String value) {
        if (value == null) {
            return INLINE_HOVER;
        }
        for (GlyphPreviewMode mode : values()) {
            if (mode.displayName.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return INLINE_HOVER;
    }
}
