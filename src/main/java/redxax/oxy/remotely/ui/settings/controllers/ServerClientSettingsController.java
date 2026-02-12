package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;

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
                .description("Scan Your Remotely Instances.")
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
                .description("The hostname of reverse proxy.")
                .bind(configManager::getProxyHost, configManager::setProxyHost)
                .defaultValue("RedxAx.net")
                .dependsOn(customProxy)
                .build());

        servers.addOption(ConfigOption.<String>builder("Reverse Proxy User")
                .description("The username for reverse proxy connection.")
                .bind(configManager::getProxyUser, configManager::setProxyUser)
                .defaultValue("tunnel")
                .dependsOn(customProxy)
                .build());

        settings.add(servers.build());

        List<Instance> hiddenServers = getHiddenServers();
        if (!hiddenServers.isEmpty()) {
            Setting.Builder hiddenServersBuilder = new Setting.Builder("Hidden Servers");
            for (Instance inst : hiddenServers) {
                hiddenServersBuilder.addRow("", true, false, 30, createHiddenServerWidget(inst));
            }
            settings.add(hiddenServersBuilder.build());
        }

        return settings;
    }

    private List<Instance> getHiddenServers() {
        List<Instance> hidden = new ArrayList<>();
        InstanceManager instanceManager = Rebase.get().getInstanceManager();

        for (Instance inst : instanceManager.getLocalInstances()) {
            if (inst.isHidden()) {
                hidden.add(inst);
            }
        }

        for (var host : instanceManager.getRemoteHosts()) {
            for (Instance inst : instanceManager.getRemoteInstances(host)) {
                if (inst.isHidden()) {
                    hidden.add(inst);
                }
            }
        }

        List<String> hiddenRestudio = configManager.getHiddenRestudioServers();
        if (!hiddenRestudio.isEmpty()) {
            ServerManagerScreen serverManagerScreen = ScreenManager.currentScreen instanceof ServerManagerScreen ? (ServerManagerScreen) ScreenManager.currentScreen : null;
            if (serverManagerScreen != null) {
                for (var restudioInst : serverManagerScreen.getRestudioInstances()) {
                    if (hiddenRestudio.contains(restudioInst.getName())) {
                        hidden.add(restudioInst);
                    }
                }
            }
        }

        return hidden;
    }

    private MountableButtonWidget createHiddenServerWidget(Instance instance) {
        boolean isRestudio = instance.getBackendConfig() != null && "RESTUDIO".equalsIgnoreCase(instance.getBackendConfig().type);
        RemoteHost host = null;
        if (!isRestudio && instance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type)) {
            for (var h : Rebase.get().getInstanceManager().getRemoteHosts()) {
                if (instance.getBackendConfig().credentials.getOrDefault("host", "").equals(h.getIp())) {
                    host = h;
                    break;
                }
            }
        }

        IconButton unhideButton = new IconButton.Builder()
            .imagePath("add.png")
            .onClick(() -> {
                if (isRestudio) {
                    configManager.unhideRestudioServer(instance.getName());
                } else {
                    instance.setHidden(false);
                    instance.save();
                }
                refreshSettings();
            })
            .hint("Unhide this server")
            .size(18, 18)
            .build();

        String description = instance.getName();
        String pathInfo = host != null ? "Remote: " + host.name : isRestudio ? "ReStudio" : "Local";
        String versionInfo = "Version: " + instance.getVersionId();

        var button = new MountableButtonWidget.Builder(description)
            .description(pathInfo)
            .hiddenText(versionInfo)
            .addWidget(unhideButton)
            .build();
        button.xOffset = 2;
        return button;
    }

    private void refreshSettings() {
        if (ScreenManager.currentScreen instanceof SettingsScreen) {
            ((SettingsScreen) ScreenManager.currentScreen).refreshTab("Servers");
        }
    }
}
