package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rebase.localcontrol.LocalServerControllerClient;
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
        Properties props = instance.getServerProperties();
        boolean isLocal = instance.getBackendConfig() == null || "LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);

        if (isLocal) {
            builder.addOption(ConfigOption.<Boolean>builder("Keep Running")
                    .description("Keep Server Running After Remotely Closes.")
                    .bind(instance::isLocalLifecyclePersistent,
                            val -> {
                                instance.setLocalLifecyclePersistent(val);
                                updateLocalController();
                            })
                    .defaultValue(false)
                    .build());
            builder.addOption(ConfigOption.<Boolean>builder("Auto Restart")
                    .description("Restart Persistent Servers After A Crash.")
                    .bind(instance::isLocalRestartOnCrash,
                            val -> {
                                instance.setLocalRestartOnCrash(val);
                                if (val) {
                                    instance.setLocalLifecyclePersistent(true);
                                }
                                updateLocalController();
                            })
                    .defaultValue(false)
                    .build());
            builder.addOption(ConfigOption.<Integer>builder("Restart Delay")
                    .description("Seconds Before Restarting After A Crash.")
                    .range(1, 300)
                    .bind(instance::getLocalRestartDelaySeconds,
                            val -> {
                                instance.setLocalRestartDelaySeconds(val);
                                updateLocalController();
                            })
                    .defaultValue(5)
                    .build());
            builder.addOption(ConfigOption.<Integer>builder("Restart Attempts")
                    .description("Maximum Consecutive Crash Restarts.")
                    .range(1, 50)
                    .bind(instance::getLocalRestartMaxAttempts,
                            val -> {
                                instance.setLocalRestartMaxAttempts(val);
                                updateLocalController();
                            })
                    .defaultValue(3)
                    .build());
        }

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

        builder.addOption(ConfigOption.<Boolean>builder("Enable RCON")
                .description("Allow online player data via RCON.")
                .bind(() -> Boolean.parseBoolean(props.getProperty("enable-rcon", "false")),
                        val -> props.setProperty("enable-rcon", String.valueOf(val)))
                .defaultValue(false)
                .build());

        builder.addOption(ConfigOption.<Integer>builder("Player Data Interval")
                .description("Real Time Update Interval")
                .range(250, 10000)
                .bind(() -> Integer.parseInt(s.getProperty("playerdata.refresh.intervalMs", "1000")),
                        val -> s.setProperty("playerdata.refresh.intervalMs", String.valueOf(val)))
                .defaultValue(1000)
                .build());

        return List.of(builder.build());
    }

    private void updateLocalController() {
        Thread.ofVirtual().name("Remotely Local Controller Configure").start(() -> LocalServerControllerClient.configure(instance));
    }
}
