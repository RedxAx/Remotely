package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.util.ArrayList;
import java.util.List;

public class DevelopmentSettingsController {

    private final RemotelyConfigManager configManager;

    public DevelopmentSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Setting> getSettings() {
        List<Setting> settings = new ArrayList<>();
        Setting.Builder dev = new Setting.Builder("Development");

        dev.addOption(ConfigOption.<Boolean>builder("Developer Mode")
                .description("Enable developer-specific features and logging.")
                .bind(configManager::isDev, configManager::setDev)
                .defaultValue(false)
                .build());

        dev.addOption(ConfigOption.<Boolean>builder("Enable Debug Tools")
                .description("Show debug overlays and tools in the UI.")
                .bind(configManager::getEnableDebugTools, configManager::setEnableDebugTools)
                .defaultValue(false)
                .build());

        dev.addOption(ConfigOption.<Boolean>builder("Show IP Address")
                .description("Display the server IP address in the interface.")
                .bind(configManager::getShowIp, configManager::setShowIp)
                .defaultValue(true)
                .build());

        settings.add(dev.build());
        return settings;
    }
}
