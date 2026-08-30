package redxax.oxy.remotely.settings.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public final class BundledServerSettingsRegistry {
    private static final List<String> RESOURCES = List.of(
            "/server-settings/purpur.yml",
            "/server-settings/bukkit.yml",
            "/server-settings/spigot.yml",
            "/server-settings/paper-global.yml",
            "/server-settings/paper-world.yml",
            "/server-settings/server-properties.yml",
            "/server-settings/velocity.yml"
    );

    private BundledServerSettingsRegistry() {
    }

    public static void loadInto(ServerSettingsRegistry registry, ServerSettingsMetadataReader parser) {
        for (String resource : RESOURCES) {
            try (InputStream input = BundledServerSettingsRegistry.class.getResourceAsStream(resource)) {
                if (input != null) registry.registerBuiltin("builtin:" + resource.toLowerCase(Locale.ROOT), parser.parse(input, resource));
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load built-in server settings metadata: " + resource, exception);
            }
        }
    }
}
