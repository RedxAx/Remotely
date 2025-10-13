package redxax.oxy.remotely.servers;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.ui.settings.controllers.ServerAdvancedSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerGeneralSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerPerformanceSettingsController;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.settings.Setting;
import restudio.rebase.settings.SettingsScreen;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static restudio.rescreen.util.SoundUtils.playSound;

public class ServerConfigurationScreen extends ReScreen {
    private final Screen parent;
    private final boolean isEditMode;
    private final Instance originalInstance;
    private final Instance tempInstance;
    private final RemoteHost remoteHostContext;
    private final RemotelyClient remotelyClient;

    public ServerConfigurationScreen(Screen parent, Instance instance, RemoteHost remoteHostContext, RemotelyClient remotelyClient) {
        super();
        this.parent = parent;
        this.isEditMode = instance != null;
        this.originalInstance = instance;
        this.remoteHostContext = remoteHostContext;
        this.remotelyClient = remotelyClient;

        if (isEditMode) {
            this.tempInstance = new Instance(instance, instance.getName());
        } else {
            this.tempInstance = new Instance("New Server", remotelyClient.getHost().getGameVersion(), "");
            this.tempInstance.loadServerProperties();
        }
    }

    @Override
    public void init() {
        super.init();

        Map<String, Supplier<List<Setting>>> settingsByTab = new LinkedHashMap<>();

        ServerGeneralSettingsController generalController = new ServerGeneralSettingsController(tempInstance, remotelyClient);
        settingsByTab.put("General", generalController::getSettings);

        ServerAdvancedSettingsController advancedController = new ServerAdvancedSettingsController(tempInstance);
        settingsByTab.put("Advanced", advancedController::getSettings);

        ServerPerformanceSettingsController performanceController = new ServerPerformanceSettingsController(tempInstance);
        settingsByTab.put("Performance", performanceController::getSettings);

        SettingsScreen settingsScreen = new SettingsScreen(
                parent,
                isEditMode ? "Edit " + originalInstance.getName() : "Create New Server",
                settingsByTab,
                this::saveConfiguration,
                null
        );
        client.setScreen(settingsScreen);
    }

    private void saveConfiguration() {
        if (isEditMode) {
            editServer();
        } else {
            if (remoteHostContext != null) {
                createNewRemoteServer();
            } else {
                createNewLocalServer();
            }
        }
        client.setScreen(parent);
    }

    private void createNewLocalServer() {
        Notification notification = new Notification("Creating server...", tempInstance.getName(), Notification.Type.INFO);
        notification.autoSlideOut = false;
        notification.loading = true;

        Rebase.get().getInstanceManager().createInstance(tempInstance, notification)
                .thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
                    newInstance.getServerProperties().putAll(tempInstance.getServerProperties());
                    newInstance.saveServerProperties();
                    notification.change(newInstance.getName() + " Created Successfully!", "Click To Open", Notification.Type.SUCCESS, () -> ServerManagerScreen.openServerScreen(newInstance.getPath()));
                    notification.loading = false;
                    notification.autoSlideOut = true;
                })).exceptionally(ex -> {
                    ScreenManager.getInstance().execute(() -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        notification.change("Server Creation Failed", cause.getMessage(), Notification.Type.ERROR, null);
                        notification.loading = false;
                        notification.autoSlideOut = true;
                    });
                    return null;
                });
    }

    private void createNewRemoteServer() {
        Notification notification = new Notification("Creating remote server...", tempInstance.getName(), Notification.Type.INFO);
        notification.autoSlideOut = false;
        notification.loading = true;

        Rebase.get().getInstanceManager().createRemoteInstance(tempInstance, remoteHostContext, notification)
                .thenCompose(newInstance ->
                        Rebase.get().getInstanceManager().fetchRemoteInstances(remoteHostContext).handle((v, e) -> null).thenApply(v -> newInstance)
                )
                .thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
                    notification.change(newInstance.getName() + " Created Successfully!", "Remote server created.", Notification.Type.SUCCESS, null);
                    notification.loading = false;
                    notification.autoSlideOut = true;
                })).exceptionally(ex -> {
                    ScreenManager.getInstance().execute(() -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        notification.change("Remote Server Creation Failed", cause.getMessage(), Notification.Type.ERROR, null);
                        notification.loading = false;
                        notification.autoSlideOut = true;
                    });
                    return null;
                });
    }

    private void editServer() {
        originalInstance.setName(tempInstance.getName());
        originalInstance.setModLoader(tempInstance.getModLoader());
        originalInstance.setVersionId(tempInstance.getVersionId());
        originalInstance.getSettings().putAll(tempInstance.getSettings());
        originalInstance.getServerProperties().clear();
        originalInstance.getServerProperties().putAll(tempInstance.getServerProperties());

        originalInstance.save();
        originalInstance.saveServerProperties();

        new Notification(originalInstance.getName() + " Edited Successfully!", Notification.Type.SUCCESS);
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }

    public void close() {
        client.setScreen(parent);
    }
}