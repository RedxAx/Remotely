package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.ui.settings.Setting;
import restudio.rescreen.ui.widgets.ToggleWidget;

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

        ToggleWidget devMode = new ToggleWidget.Builder()
                .toggled(configManager.isDev())
                .onChange(configManager::setDev)
                .build();
        dev.addRow("Developer Mode", false, 20, devMode);

        ToggleWidget debugTools = new ToggleWidget.Builder()
                .toggled(configManager.getEnableDebugTools())
                .onChange(configManager::setEnableDebugTools)
                .build();
        dev.addRow("Enable Debug Tools", false, 20, debugTools);

        ToggleWidget showIp = new ToggleWidget.Builder()
                .toggled(configManager.getShowIp())
                .onChange(configManager::setShowIp)
                .build();
        dev.addRow("Show IP Address", false, 20, showIp);

        settings.add(dev.build());
        return settings;
    }
}