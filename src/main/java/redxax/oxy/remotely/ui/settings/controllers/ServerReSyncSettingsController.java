package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ServerReSyncSettingsController {
    private static final String RESYNC_ENABLED_KEY = "resyncEnabled";
    private static final String RESYNC_API_KEY_KEY = "resyncApiKey";
    private static final String RESYNC_PORT_KEY = "resyncPort";
    private static final String DEFAULT_RESYNC_PORT = "12441";
    private final ServerReSyncSettingsProvider provider;

    public ServerReSyncSettingsController(ServerReSyncSettingsProvider provider) {
        this.provider = provider;
    }

    public List<Setting> getSettings() {
        Setting.Builder setting = new Setting.Builder("ReSync Settings");
        Map<String, String> credentials = provider.credentials();
        if (credentials == null) {
            return List.of();
        }

        ConfigOption<Boolean> enabled = ConfigOption.<Boolean>builder("Enable ReSync")
            .description("Enable ReSync for this server")
            .bind(() -> Boolean.parseBoolean(safeText(credentials.get(RESYNC_ENABLED_KEY))), value -> {
                credentials.put(RESYNC_ENABLED_KEY, String.valueOf(value));
                if (!value) {
                    credentials.remove(RESYNC_PORT_KEY);
                    credentials.remove(RESYNC_API_KEY_KEY);
                    return;
                }
                if (safeText(credentials.get(RESYNC_PORT_KEY)).isBlank()) {
                    credentials.put(RESYNC_PORT_KEY, DEFAULT_RESYNC_PORT);
                }
            })
            .defaultValue(false)
            .build();
        setting.addOption(enabled);

        setting.addOption(ConfigOption.<String>builder("ReSync Port")
            .description("Server ReSync WebSocket port. Default is 12441")
            .bind(() -> safeText(credentials.get(RESYNC_PORT_KEY)), value -> {
                String normalized = safeText(value).trim();
                if (normalized.isBlank()) {
                    credentials.remove(RESYNC_PORT_KEY);
                    return;
                }
                credentials.put(RESYNC_PORT_KEY, normalized);
            })
            .defaultValue("")
            .dependsOn(enabled)
            .build());

        setting.addOption(ConfigOption.<String>builder("ReSync ApiKey")
            .description("Server ReSync api key")
            .bind(() -> safeText(credentials.get(RESYNC_API_KEY_KEY)), value -> {
                String normalized = safeText(value).trim();
                if (normalized.isBlank()) {
                    credentials.remove(RESYNC_API_KEY_KEY);
                    return;
                }
                credentials.put(RESYNC_API_KEY_KEY, normalized);
            })
            .defaultValue("")
            .dependsOn(enabled)
            .build());

        return List.of(setting.build());
    }

    public boolean shouldProvisionReStudio() {
        if (!provider.reStudioBackend() || provider.serverIdentifier() == null || provider.serverIdentifier().isBlank()) {
            return false;
        }
        Map<String, String> credentials = provider.credentials();
        if (credentials == null) {
            return false;
        }
        return Boolean.parseBoolean(safeText(credentials.get(RESYNC_ENABLED_KEY)));
    }

    public void provisionReStudio(Consumer<Boolean> callback) {
        provider.provision(callback);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
