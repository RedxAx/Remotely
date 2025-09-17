package redxax.oxy.remotely.servers;

import net.minecraft.client.MinecraftClient;
import redxax.oxy.remotely.ui.settings.controllers.ServerAdvancedSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerGeneralSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerPerformanceSettingsController;
import restudio.rebase.Rebase;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.settings.Setting;
import restudio.rebase.ui.settings.SettingsScreen;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.nio.file.Path;
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

    public ServerConfigurationScreen(Screen parent, Path settingsRoot, Instance instance, RemoteHost remoteHostContext) {
        super();
        this.parent = parent;
        this.isEditMode = instance != null;
        this.originalInstance = instance;
        this.remoteHostContext = remoteHostContext;

        if (isEditMode) {
            this.tempInstance = new Instance(instance, instance.getName());
        } else {
            this.tempInstance = new Instance("New Server", MinecraftClient.getInstance().getGameVersion(), "");
        }
    }

    @Override
    public void init() {
        super.init();

        Map<String, Supplier<List<Setting>>> settingsByTab = new LinkedHashMap<>();

        ServerGeneralSettingsController generalController = new ServerGeneralSettingsController(tempInstance);
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
            client.setScreen(parent);
            return;
        }

        if (remoteHostContext != null) {
            // TODO: Implement remote server creation
            new Notification("Not Implemented", "Remote server creation is not yet supported.", Notification.Type.WARN);
        } else {
            createNewLocalServer();
        }
        client.setScreen(parent);
    }

    private void createNewLocalServer() {
        Notification notification = new Notification("Creating server...", tempInstance.getName(), Notification.Type.INFO);
        notification.autoSlideOut = false;
        notification.loading = true;

        Rebase.get().getInstanceManager().createInstance(tempInstance, notification)
                .thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
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

    private void editServer() {
        originalInstance.setName(tempInstance.getName());
        originalInstance.setModLoader(tempInstance.getModLoader());
        originalInstance.setVersionId(tempInstance.getVersionId());
        originalInstance.getSettings().putAll(tempInstance.getSettings());
        originalInstance.save();

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