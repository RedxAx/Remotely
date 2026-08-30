package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.config.RemotelyConfigStore;
import redxax.oxy.remotely.servers.QuickServerSyncManager;
import redxax.oxy.remotely.util.AsyncTools;
import redxax.oxy.remotely.util.TaskSchedulers;
import restudio.rebase.Rebase;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.ScreenWindowWidget;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public final class DesktopServerClientSettingsProvider implements ServerClientSettingsProvider {
    private final RemotelyConfigStore config;

    public DesktopServerClientSettingsProvider(RemotelyConfigStore config) {
        this.config = config;
    }

    @Override
    public List<QuickServer> quickServers() {
        InstanceManager manager = Rebase.get().getInstanceManager();
        List<Instance> instances = new ArrayList<>();
        manager.getLocalInstances().stream().filter(this::isQuickServer).forEach(instances::add);
        Path directory = manager.getInstancesDir().resolve("quick-servers");
        if (Files.isDirectory(directory)) {
            try (var paths = Files.list(directory)) {
                paths.filter(Files::isDirectory).map(Instance::load).filter(this::isQuickServer).forEach(instance -> {
                    if (instances.stream().noneMatch(existing -> sameInstance(existing, instance))) {
                        manager.registerLocalInstance(instance);
                        instances.add(instance);
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return instances.stream().map(instance -> new QuickServer(instance.getName(), instance.getVersionId(), instance.isHidden(), () -> {
            instance.setHidden(!instance.isHidden());
            instance.save();
            refreshSettings();
        }, () -> AsyncTools.run(TaskSchedulers.current(), () -> {
            try {
                QuickServerSyncManager.stopAndSyncBack(instance);
                manager.removeInstance(instance);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }))).toList();
    }

    @Override
    public List<HiddenServer> hiddenServers() {
        InstanceManager manager = Rebase.get().getInstanceManager();
        List<HiddenServer> hidden = new ArrayList<>();
        for (Instance instance : manager.getLocalInstances()) {
            if (instance.isHidden() && !isQuickServer(instance)) {
                hidden.add(hiddenServer(instance, null));
            }
        }
        for (RemoteHost host : manager.getRemoteHosts()) {
            for (Instance instance : manager.getRemoteInstances(host)) {
                if (instance.isHidden() && !isQuickServer(instance)) {
                    hidden.add(hiddenServer(instance, remoteHost(manager, instance)));
                }
            }
        }
        return hidden;
    }

    private HiddenServer hiddenServer(Instance instance, RemoteHost host) {
        BackendConfig backend = instance.getBackendConfig();
        if (backend != null && "RESTUDIO".equalsIgnoreCase(backend.type)) {
            return new HiddenServer(instance.getName(), "ReStudio", instance.getVersionId(), () -> {
                unhideRestudioServer(instance, backend);
                refreshSettings();
            });
        }
        String location = host == null ? "Local" : "Remote: " + host.name;
        return new HiddenServer(instance.getName(), location, instance.getVersionId(), () -> {
            instance.setHidden(false);
            instance.save();
            refreshSettings();
        });
    }

    private RemoteHost remoteHost(InstanceManager manager, Instance instance) {
        BackendConfig backend = instance.getBackendConfig();
        if (backend == null || backend.type == null || backend.type.isBlank() || "LOCAL".equalsIgnoreCase(backend.type)
                || backend.credentials == null) {
            return null;
        }
        String hostAddress = backend.credentials.getOrDefault("host", "");
        return manager.getRemoteHosts().stream().filter(host -> hostAddress.equals(host.getIp())).findFirst().orElse(null);
    }

    private boolean isQuickServer(Instance instance) {
        return "true".equalsIgnoreCase(instance.getSettings().getProperty("quickServer.enabled"));
    }

    private boolean sameInstance(Instance first, Instance second) {
        if (first.getInstanceId() != null && !first.getInstanceId().isBlank()) return first.getInstanceId().equals(second.getInstanceId());
        return first.getPath().equals(second.getPath());
    }

    private void unhideRestudioServer(Instance instance, BackendConfig backend) {
        String identifier = backend.credentials == null ? "" : backend.credentials.getOrDefault("identifier", "");
        if (identifier.isBlank()) {
            identifier = instance.getInstanceId();
        }
        String name = instance.getName();
        String primary = identifier == null || identifier.isBlank() ? name : identifier;
        if (primary != null && !primary.isBlank()) {
            config.unhideRestudioServer(primary);
        }
        if (identifier != null && !identifier.isBlank() && name != null && !name.isBlank() && !identifier.equals(name)
                && config.getHiddenRestudioServers().contains(name)) {
            config.unhideRestudioServer(name);
        }
    }

    private void refreshSettings() {
        ScreenManager screenManager = ScreenManager.getInstance();
        Set<SettingsScreen> settingsScreens = new LinkedHashSet<>();
        if (screenManager.getCurrentScreen() instanceof SettingsScreen settingsScreen) {
            settingsScreens.add(settingsScreen);
        }
        var overlay = screenManager.getDesktopWindowsOverlay();
        if (overlay != null) {
            for (ScreenWindowWidget window : overlay.getWindows()) {
                if (window.getScreen() instanceof SettingsScreen settingsScreen) {
                    settingsScreens.add(settingsScreen);
                }
            }
        }
        settingsScreens.forEach(settingsScreen -> settingsScreen.refreshTab("Servers"));
    }
}
