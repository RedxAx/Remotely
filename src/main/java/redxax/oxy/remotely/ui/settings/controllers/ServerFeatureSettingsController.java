package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.List;
import java.util.Properties;

public class ServerFeatureSettingsController {
    private final Instance instance;

    public ServerFeatureSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Features & Integrations");
        Properties s = instance.getSettings();

        builder.addOption(ConfigOption.<Boolean>builder("Enable MSMP")
                .description("Minecraft Server Management Protocol support.")
                .bind(() -> Boolean.parseBoolean(s.getProperty("provider.msmp.enabled", "true")),
                      val -> s.setProperty("provider.msmp.enabled", String.valueOf(val)))
                .defaultValue(true)
                .build());

        builder.addOption(ConfigOption.<Boolean>builder("Enable Backend API")
                .description("Integration with ReBase backend services.")
                .bind(() -> Boolean.parseBoolean(s.getProperty("provider.backend.enabled", "true")),
                      val -> s.setProperty("provider.backend.enabled", String.valueOf(val)))
                .defaultValue(true)
                .build());

        builder.addOption(ConfigOption.<Boolean>builder("Enable Standard Provider")
                .description("Standard local file-based management.")
                .bind(() -> Boolean.parseBoolean(s.getProperty("provider.standard.enabled", "true")),
                      val -> s.setProperty("provider.standard.enabled", String.valueOf(val)))
                .defaultValue(true)
                .build());

        builder.addOption(ConfigOption.<String>builder("Global Priority")
                .description("Order of providers: msmp, backend, standard.")
                .bind(() -> s.getProperty("provider.priority", "msmp,backend,standard"),
                      val -> s.setProperty("provider.priority", val))
                .defaultValue("msmp,backend,standard")
                .build());

        builder.addOption(ConfigOption.<String>builder("Files Priority")
                .description("Provider priority for file operations.")
                .bind(() -> s.getProperty("provider.priority.files", ""),
                      val -> s.setProperty("provider.priority.files", val))
                .defaultValue("")
                .build());

        builder.addOption(ConfigOption.<String>builder("Console Priority")
                .description("Provider priority for console access.")
                .bind(() -> s.getProperty("provider.priority.console", ""),
                      val -> s.setProperty("provider.priority.console", val))
                .defaultValue("")
                .build());

        builder.addOption(ConfigOption.<String>builder("Players Priority")
                .description("Provider priority for player management.")
                .bind(() -> s.getProperty("provider.priority.players", ""),
                      val -> s.setProperty("provider.priority.players", val))
                .defaultValue("")
                .build());

        return List.of(builder.build());
    }
}
