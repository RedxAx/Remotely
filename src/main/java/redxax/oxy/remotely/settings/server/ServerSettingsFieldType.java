package redxax.oxy.remotely.settings.server;

import java.util.Locale;

public enum ServerSettingsFieldType {
    BOOLEAN,
    BOOLEAN_OR_DEFAULT,
    BOOLEAN_OR_DISABLED,
    INTEGER,
    DECIMAL,
    INTEGER_OR_DEFAULT,
    INTEGER_OR_DISABLED,
    DECIMAL_OR_DEFAULT,
    DECIMAL_OR_DISABLED,
    TEXT,
    SELECT,
    DURATION,
    DURATION_OR_DISABLED,
    LIST,
    MAP;

    public static ServerSettingsFieldType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A field type is required");
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown server settings field type: " + value, exception);
        }
    }

    public boolean numeric() {
        return this == INTEGER || this == DECIMAL || this == INTEGER_OR_DEFAULT || this == INTEGER_OR_DISABLED || this == DECIMAL_OR_DEFAULT || this == DECIMAL_OR_DISABLED;
    }

    public boolean booleanUnion() {
        return this == BOOLEAN_OR_DEFAULT || this == BOOLEAN_OR_DISABLED;
    }

    public boolean integerUnion() {
        return this == INTEGER_OR_DEFAULT || this == INTEGER_OR_DISABLED;
    }

    public boolean decimalUnion() {
        return this == DECIMAL_OR_DEFAULT || this == DECIMAL_OR_DISABLED;
    }

    public boolean sentinel() {
        return booleanUnion() || integerUnion() || decimalUnion() || this == DURATION_OR_DISABLED;
    }

    public boolean structured() {
        return this == LIST || this == MAP;
    }
}
