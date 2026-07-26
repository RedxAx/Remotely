package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.servers.QuickServerSyncManager;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.util.Notification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static redxax.oxy.remotely.config.Config.remotelyDir;

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

        Setting.Builder quickServer = new Setting.Builder("Quick Server");
        quickServer.addOption(ConfigOption.<Boolean>builder("Precreate Quick Server")
                .description("Prepare The World Server When Opening A World.")
                .bind(configManager::getQuickServerPrecreate, configManager::setQuickServerPrecreate)
                .defaultValue(false)
                .build());
        quickServer.addOption(ConfigOption.<Boolean>builder("Keep Running")
                .description("Keep Quick Servers Running After Minecraft Closes.")
                .bind(configManager::getQuickServerKeepRunning, configManager::setQuickServerKeepRunning)
                .defaultValue(false)
                .build());
        quickServer.addOption(ConfigOption.<Boolean>builder("Auto Restart")
                .description("Restart Quick Servers After A Crash.")
                .bind(configManager::getQuickServerAutoRestart, configManager::setQuickServerAutoRestart)
                .defaultValue(false)
                .build());
        quickServer.addOption(ConfigOption.<Boolean>builder("Mirror Mods")
                .description("Copy Server Compatible Mods Into Quick Servers.")
                .bind(configManager::getQuickServerMirrorMods, configManager::setQuickServerMirrorMods)
                .defaultValue(true)
                .build());
        List<Instance> quickServers = getQuickServers();
        for (Instance inst : quickServers) {
            quickServer.addRow("", createQuickServerWidget(inst));
        }
        settings.add(quickServer.build());

        List<Instance> hiddenServers = getHiddenServers();
        if (!hiddenServers.isEmpty()) {
            Setting.Builder hiddenServersBuilder = new Setting.Builder("Hidden Servers");
            for (Instance inst : hiddenServers) {
                hiddenServersBuilder.addRow("", createHiddenServerWidget(inst));
            }
            settings.add(hiddenServersBuilder.build());
        }

        return settings;
    }

    private List<Instance> getHiddenServers() {
        List<Instance> hidden = new ArrayList<>();
        InstanceManager instanceManager = Rebase.get().getInstanceManager();

        for (Instance inst : instanceManager.getLocalInstances()) {
            if (inst.isHidden() && !isQuickServer(inst)) {
                hidden.add(inst);
            }
        }

        for (var host : instanceManager.getRemoteHosts()) {
            for (Instance inst : instanceManager.getRemoteInstances(host)) {
                if (inst.isHidden() && !isQuickServer(inst)) {
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

    private List<Instance> getQuickServers() {
        List<Instance> quickServers = new ArrayList<>();
        InstanceManager instanceManager = Rebase.get().getInstanceManager();
        for (Instance inst : instanceManager.getLocalInstances()) {
            if (isQuickServer(inst) && quickServers.stream().noneMatch(existing -> sameInstance(existing, inst))) {
                quickServers.add(inst);
            }
        }
        Path quickServersDir = Rebase.get().getInstancesDir().resolve("quick-servers");
        if (Files.isDirectory(quickServersDir)) {
            try (var paths = Files.list(quickServersDir)) {
                for (Path path : paths.filter(Files::isDirectory).toList()) {
                    Instance inst = Instance.load(path);
                    if (isQuickServer(inst) && quickServers.stream().noneMatch(existing -> sameInstance(existing, inst))) {
                        instanceManager.registerLocalInstance(inst);
                        quickServers.add(inst);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return quickServers;
    }

    private boolean sameInstance(Instance first, Instance second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getInstanceId() != null && !first.getInstanceId().isBlank()) {
            return first.getInstanceId().equals(second.getInstanceId());
        }
        return first.getPath() != null && first.getPath().equals(second.getPath());
    }

    private boolean isQuickServer(Instance instance) {
        return instance != null && "true".equalsIgnoreCase(instance.getSettings().getProperty("quickServer.enabled"));
    }

    private MountableButtonWidget createQuickServerWidget(Instance instance) {
        IconButton visibilityButton = new IconButton.Builder()
            .imagePath(instance.isHidden() ? "add.png" : "hide.png")
            .onClick(() -> {
                instance.setHidden(!instance.isHidden());
                instance.save();
                refreshSettings();
            })
            .hint(instance.isHidden() ? "Show Quick Server" : "Hide Quick Server")
            .size(18, 18)
            .build();

        IconButton deleteButton = new IconButton.Builder()
            .imagePath("delete.png")
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> deleteQuickServer(instance))
            .hint("Delete Quick Server")
            .size(18, 18)
            .build();

        String visibility = instance.isHidden() ? "Hidden" : "Visible";
        String versionInfo = instance.getVersionId() == null || instance.getVersionId().isBlank() ? visibility : visibility + " | " + instance.getVersionId();

        var button = new MountableButtonWidget.Builder(instance.getName())
            .description("Quick Server")
            .hiddenText(versionInfo)
            .addWidget(visibilityButton)
            .addWidget(deleteButton)
            .build();
        button.xOffset = 2;
        return button;
    }

    private void deleteQuickServer(Instance instance) {
        Notification notification = new Notification.Builder()
            .message("Deleting Quick Server")
            .description(instance.getName())
            .type(Notification.Type.INFO)
            .loading(true)
            .autoSlideOut(false)
            .build();
        CompletableFuture.runAsync(() -> {
            try {
                QuickServerSyncManager.stopAndSyncBack(instance);
                Rebase.get().getInstanceManager().removeInstance(instance);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        })
            .whenComplete((unused, throwable) -> ScreenManager.getInstance().execute(() -> {
                if (throwable != null) {
                    Throwable error = throwable instanceof CompletionException completionException && completionException.getCause() != null ? completionException.getCause() : throwable;
                    notification.update()
                        .message("Delete Failed")
                        .description(error.getMessage() == null ? instance.getName() : error.getMessage())
                        .type(Notification.Type.ERROR)
                        .loading(false)
                        .autoSlideOut(true)
                        .commit();
                    return;
                }
                notification.update()
                    .message("Quick Server Deleted")
                    .description(instance.getName())
                    .type(Notification.Type.SUCCESS)
                    .loading(false)
                    .autoSlideOut(true)
                    .commit();
                refreshSettings();
            }));
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
