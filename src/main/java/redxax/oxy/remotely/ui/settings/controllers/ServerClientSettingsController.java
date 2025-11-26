package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

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

        servers.addOption(ConfigOption.<Boolean>builder("Scan For Servers")
                .description("Automatically scan local network for Remotely instances.")
                .bind(configManager::getScanServers, configManager::setScanServers)
                .defaultValue(true)
                .build());

        ConfigOption<Boolean> customProxy = ConfigOption.<Boolean>builder("Use Custom Reverse Proxy")
                .description("Enable connection via a custom reverse proxy server.")
                .bind(configManager::getCustomReverseProxy, configManager::setCustomReverseProxy)
                .defaultValue(false)
                .build();
        servers.addOption(customProxy);

        servers.addOption(ConfigOption.<String>builder("Reverse Proxy Host")
                .description("The hostname of the reverse proxy.")
                .bind(configManager::getProxyHost, configManager::setProxyHost)
                .defaultValue("RedxAx.net")
                .dependsOn(customProxy)
                .build());

        servers.addOption(ConfigOption.<String>builder("Reverse Proxy User")
                .description("The username for the reverse proxy connection.")
                .bind(configManager::getProxyUser, configManager::setProxyUser)
                .defaultValue("tunnel")
                .dependsOn(customProxy)
                .build());

        settings.add(servers.build());
        return settings;
    }
}
