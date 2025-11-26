package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.List;
import java.util.Properties;

public class ServerPerformanceSettingsController {
    private final Instance instance;

    public ServerPerformanceSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Setting.Builder performance = new Setting.Builder("Performance Settings");
        Properties props = instance.getServerProperties();
        Properties settings = instance.getSettings();

        performance.addOption(ConfigOption.<Integer>builder("View Distance")
                .description("The radius of chunks sent to the client.")
                .range(2, 32)
                .bind(() -> Integer.parseInt(props.getProperty("view-distance", "8")),
                      val -> props.setProperty("view-distance", String.valueOf(val)))
                .defaultValue(10)
                .build());

        performance.addOption(ConfigOption.<Integer>builder("Simulation Distance")
                .description("The radius of chunks where entities/physics update.")
                .range(2, 32)
                .bind(() -> Integer.parseInt(props.getProperty("simulation-distance", "8")),
                      val -> props.setProperty("simulation-distance", String.valueOf(val)))
                .defaultValue(10)
                .build());

        performance.addOption(ConfigOption.<String>builder("Allocated Memory")
                .description("Java heap size (e.g., 4G, 2048M).")
                .bind(() -> settings.getProperty("memory", "4G"),
                      val -> settings.setProperty("memory", val))
                .defaultValue("4G")
                .build());

        performance.addOption(ConfigOption.<Boolean>builder("Use Aikar's Flags")
                .description("Apply optimized JVM flags for better performance.")
                .bind(() -> Boolean.parseBoolean(settings.getProperty("aikars_flags", "true")),
                      val -> settings.setProperty("aikars_flags", String.valueOf(val)))
                .defaultValue(true)
                .build());

        return List.of(performance.build());
    }
}
