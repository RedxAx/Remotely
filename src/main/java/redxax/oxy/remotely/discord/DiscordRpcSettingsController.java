package redxax.oxy.remotely.discord;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.List;

public class DiscordRpcSettingsController {

    public enum EnabledMode {
        INHERIT,
        ENABLED,
        DISABLED
    }

    private static final String DEFAULT_MANAGER_DETAILS = "In Remotely";
    private static final String DEFAULT_MANAGER_STATE = "Managing Servers";
    private static final String DEFAULT_SERVER_DETAILS = "Viewing Server";
    private static final String DEFAULT_SERVER_STATE = "{server}";
    private static final String DEFAULT_RUNNING_DETAILS = "Managing Server";
    private static final String DEFAULT_RUNNING_STATE = "{players} Players | {server}";
    private static final String DEFAULT_RESYNC_DETAILS = "Designing ReSync";
    private static final String DEFAULT_RESYNC_STATE = "{server} | {studio}";

    private final Instance instance;
    private final RemotelyConfigManager configManager;
    private final boolean global;

    public DiscordRpcSettingsController(Instance instance, RemotelyConfigManager configManager) {
        this.instance = instance;
        this.configManager = configManager;
        this.global = instance == null;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Discord Rpc");

        if (global) {
            builder.addOption(ConfigOption.<Boolean>builder("Enabled")
                    .description("Show activity in Discord.")
                    .bind(() -> Boolean.parseBoolean(configManager.getProperties().getProperty("discordRpc.enabled", "true")),
                            val -> configManager.getProperties().setProperty("discordRpc.enabled", String.valueOf(val)))
                    .defaultValue(true)
                    .build());

            builder.addOption(ConfigOption.<Integer>builder("Idle Timeout Minutes")
                    .description("After inactivity, show Idle. Set 0 to disable.")
                    .bind(() -> parseInt(configManager.getProperties().getProperty("discordRpc.idleTimeoutMinutes", "5"), 5),
                            val -> configManager.getProperties().setProperty("discordRpc.idleTimeoutMinutes", String.valueOf(Math.max(0, val))))
                    .defaultValue(5)
                    .build());

            builder.addOption(textOption("Manager Details", "discordRpc.manager.details", DEFAULT_MANAGER_DETAILS));
            builder.addOption(textOption("Manager State", "discordRpc.manager.state", DEFAULT_MANAGER_STATE));
            builder.addOption(textOption("Server Details", "discordRpc.server.details", DEFAULT_SERVER_DETAILS));
            builder.addOption(textOption("Server State", "discordRpc.server.state", DEFAULT_SERVER_STATE));
            builder.addOption(textOption("Running Details", "discordRpc.running.details", DEFAULT_RUNNING_DETAILS));
            builder.addOption(textOption("Running State", "discordRpc.running.state", DEFAULT_RUNNING_STATE));
            builder.addOption(textOption("ReSync Details", "discordRpc.resync.details", DEFAULT_RESYNC_DETAILS));
            builder.addOption(textOption("ReSync State", "discordRpc.resync.state", DEFAULT_RESYNC_STATE));
        } else {
            builder.addOption(ConfigOption.<EnabledMode>builder("Enabled")
                    .description("Global Default uses global settings.")
                    .options(List.of(EnabledMode.INHERIT, EnabledMode.ENABLED, EnabledMode.DISABLED))
                    .display(mode -> switch (mode) {
                        case INHERIT -> "Global Default";
                        case ENABLED -> "Enabled";
                        case DISABLED -> "Disabled";
                    })
                    .bind(this::getInstanceEnabledMode, this::setInstanceEnabledMode)
                    .defaultValue(EnabledMode.INHERIT)
                    .build());

            builder.addOption(ConfigOption.<String>builder("Details Override")
                    .description("Blank uses global Running Details.")
                    .bind(() -> instance.getSettings().getProperty("discordRpc.detailsOverride", ""),
                            val -> setInstanceStringOrRemove("discordRpc.detailsOverride", val))
                    .defaultValue("")
                    .build());

            builder.addOption(ConfigOption.<String>builder("State Override")
                    .description("Blank uses global Running State.")
                    .bind(() -> instance.getSettings().getProperty("discordRpc.stateOverride", ""),
                            val -> setInstanceStringOrRemove("discordRpc.stateOverride", val))
                    .defaultValue("")
                    .build());
        }

        return List.of(builder.build());
    }

    private ConfigOption<String> textOption(String name, String key, String defaultValue) {
        return ConfigOption.<String>builder(name)
                .description("Tokens: {server} {version} {loader} {state} {view} {studio} {players} {uptime} {cpu} {ram}")
                .bind(() -> configManager.getProperties().getProperty(key, defaultValue),
                        val -> configManager.getProperties().setProperty(key, val != null ? val : ""))
                .defaultValue(defaultValue)
                .build();
    }

    private EnabledMode getInstanceEnabledMode() {
        String raw = instance.getSettings().getProperty("discordRpc.enabledMode", EnabledMode.INHERIT.name());
        try {
            return EnabledMode.valueOf(raw);
        } catch (Exception ignored) {
            return EnabledMode.INHERIT;
        }
    }

    private void setInstanceEnabledMode(EnabledMode mode) {
        if (mode == null || mode == EnabledMode.INHERIT) {
            instance.getSettings().remove("discordRpc.enabledMode");
        } else {
            instance.getSettings().setProperty("discordRpc.enabledMode", mode.name());
        }
    }

    private void setInstanceStringOrRemove(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            instance.getSettings().remove(key);
        } else {
            instance.getSettings().setProperty(key, value);
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
