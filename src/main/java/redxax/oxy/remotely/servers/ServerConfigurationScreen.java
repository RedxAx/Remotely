package redxax.oxy.remotely.servers;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.ui.settings.controllers.ServerAdvancedSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerExtraSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerGeneralSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerPerformanceSettingsController;
import restudio.rebase.ui.settings.controllers.VersionSettingsController;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.LoadingAnimationWidget;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
            if (instance.isRemote() || remoteHostContext != null) {
                this.tempInstance.setRemote(true);
                this.tempInstance.setRemoteHost(instance.getRemoteHost() != null ? instance.getRemoteHost() : remoteHostContext);
            }
        } else {
            this.tempInstance = new Instance("New Server", remotelyClient.getHost().getGameVersion(), "");
            if (remoteHostContext != null) {
                this.tempInstance.setRemote(true);
                this.tempInstance.setRemoteHost(remoteHostContext);
            }
        }
    }

    @Override
    public void init() {
        super.init();

        LoadingAnimationWidget loadingWidget = new LoadingAnimationWidget(0, 0, width, height);
        addDrawableChild(loadingWidget);

        CompletableFuture<Void> propertiesFuture;
        if (isEditMode) {
            if (tempInstance.isRemote()) {
                propertiesFuture = tempInstance.loadRemoteServerProperties();
            } else {
                propertiesFuture = CompletableFuture.runAsync(tempInstance::loadServerProperties);
            }
        } else {
            tempInstance.loadServerProperties();
            propertiesFuture = CompletableFuture.completedFuture(null);
        }

        propertiesFuture.thenRun(() -> ScreenManager.getInstance().execute(this::setupSettingsUI)).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                new Notification("Error", "Could not load server properties: " + e.getMessage(), Notification.Type.ERROR);
                close();
            });
            return null;
        });
    }

    private void setupSettingsUI() {
        Map<String, Supplier<List<Setting>>> settingsByTab = new LinkedHashMap<>();

        VersionSettingsController versionController = new VersionSettingsController(tempInstance);
        settingsByTab.put("Version", versionController::getSettings);

        ServerGeneralSettingsController generalController = new ServerGeneralSettingsController(tempInstance);
        settingsByTab.put("General", generalController::getSettings);

        ServerAdvancedSettingsController advancedController = new ServerAdvancedSettingsController(tempInstance);
        settingsByTab.put("Advanced", advancedController::getSettings);

        ServerPerformanceSettingsController performanceController = new ServerPerformanceSettingsController(tempInstance);
        settingsByTab.put("Performance", performanceController::getSettings);

        ServerExtraSettingsController extraController = new ServerExtraSettingsController(tempInstance);
        settingsByTab.put("Extra Files", extraController::getSettings);

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
        Notification notification = new Notification.Builder()
                .message("Creating Server...")
                .autoSlideOut(false)
                .image(Identifier.animatedIcon("loadingGreen.png"))
                .animateImage(true)
                .accent(ThemeManager.getAccent("calm"))
                .build();

        Rebase.get().getInstanceManager().createInstance(tempInstance, notification)
                .thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
                    newInstance.getServerProperties().putAll(tempInstance.getServerProperties());
                    newInstance.saveServerProperties();
                    notification.update().message(newInstance.getName() + " Created Successfully!").description("Click To Open").type(Notification.Type.SUCCESS).loading(false).image(null).action(() -> ServerManagerScreen.openServerScreen(newInstance.getPath()));
                    notification.loading = false;
                    notification.autoSlideOut = true;
                })).exceptionally(ex -> {
                    ScreenManager.getInstance().execute(() -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        notification.update().message("Server Creation Failed").description(cause.getMessage()).type(Notification.Type.ERROR).loading(false).image(null);
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

        restudio.rebase.instance.loaders.ModLoader oldLoader = originalInstance.getModLoader();
        String oldVersion = originalInstance.getVersionId();

        originalInstance.setModLoader(tempInstance.getModLoader());
        originalInstance.setVersionId(tempInstance.getVersionId());
        originalInstance.getSettings().putAll(tempInstance.getSettings());
        originalInstance.getServerProperties().clear();
        originalInstance.getServerProperties().putAll(tempInstance.getServerProperties());

        originalInstance.save();
        originalInstance.saveServerProperties();

        boolean versionChanged = oldLoader != originalInstance.getModLoader() || (oldVersion == null ? originalInstance.getVersionId() != null : !oldVersion.equals(originalInstance.getVersionId()));

        if (versionChanged) {
            Notification notification = new Notification.Builder()
                    .message("Applying Version Changes...")
                    .autoSlideOut(false)
                    .image(Identifier.animatedIcon("loadingGreen.png"))
                    .animateImage(true)
                    .accent(ThemeManager.getAccent("calm"))
                    .build();

            if (originalInstance.isRemote()) {
                Rebase.get().getInstanceManager().createRemoteInstance(originalInstance, originalInstance.getRemoteHost(), notification)
                        .thenCompose(newInstance -> Rebase.get().getInstanceManager().fetchRemoteInstances(originalInstance.getRemoteHost()).handle((v, e) -> null).thenApply(v -> newInstance))
                        .thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
                            notification.update().message("Server Updated Successfully!").description("Version changes applied.").type(Notification.Type.SUCCESS).loading(false).image(null);
                            notification.loading = false;
                            notification.autoSlideOut = true;
                        })).exceptionally(ex -> {
                            ScreenManager.getInstance().execute(() -> {
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                notification.update().message("Update Failed").description(cause.getMessage()).type(Notification.Type.ERROR).loading(false).image(null);
                                notification.loading = false;
                                notification.autoSlideOut = true;
                            });
                            return null;
                        });
            } else {
                Rebase.get().getInstanceManager().createInstance(originalInstance, notification)
                        .thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
                            notification.update().message("Server Updated Successfully!").description("Version changes applied.").type(Notification.Type.SUCCESS).loading(false).image(null);
                            notification.loading = false;
                            notification.autoSlideOut = true;
                        })).exceptionally(ex -> {
                            ScreenManager.getInstance().execute(() -> {
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                notification.update().message("Update Failed").description(cause.getMessage()).type(Notification.Type.ERROR).loading(false).image(null);
                                notification.loading = false;
                                notification.autoSlideOut = true;
                            });
                            return null;
                        });
            }
        } else {
            new Notification(originalInstance.getName() + " Edited Successfully!", Notification.Type.SUCCESS);
        }
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }

    public void close() {
        client.setScreen(parent);
    }
}
