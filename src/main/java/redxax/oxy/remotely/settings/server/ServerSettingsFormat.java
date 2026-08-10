package redxax.oxy.remotely.settings.server;

import java.util.Locale;

public enum ServerSettingsFormat {
    PROPERTIES,
    YAML,
    TOML;

    public static ServerSettingsFormat parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A document format is required");
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown server settings document format: " + value, exception);
        }
    }
}
