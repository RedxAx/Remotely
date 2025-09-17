package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.ui.settings.Setting;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.ArrayList;
import java.util.List;

public class ServerClientSettingsController {

    private final RemotelyConfigManager configManager;

    public ServerClientSettingsController(RemotelyConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Setting> getSettings() {
        List<Setting> settings = new ArrayList<>();
        Setting.Builder servers = new Setting.Builder("Servers");

        ToggleWidget scanServers = new ToggleWidget.Builder()
                .toggled(configManager.getScanServers())
                .onChange(configManager::setScanServers)
                .build();
        servers.addRow("Scan For Servers", false, 20, scanServers);

        ToggleWidget customProxy = new ToggleWidget.Builder()
                .toggled(configManager.getCustomReverseProxy())
                .onChange(configManager::setCustomReverseProxy)
                .build();
        servers.addRow("Use Custom Reverse Proxy", false, 20, customProxy);

        TextInputWidget proxyHost = new TextInputWidget.Builder()
                .text(configManager.getProxyHost())
                .onChange(configManager::setProxyHost)
                .build();
        servers.addRow("Reverse Proxy Host", true, 20, proxyHost);

        TextInputWidget proxyUser = new TextInputWidget.Builder()
                .text(configManager.getProxyUser())
                .onChange(configManager::setProxyUser)
                .build();
        servers.addRow("Reverse Proxy User", true, 20, proxyUser);

        settings.add(servers.build());
        return settings;
    }
}