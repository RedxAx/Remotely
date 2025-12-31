package redxax.oxy.remotely.ui.server;

import com.google.gson.Gson;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.ui.settings.controllers.*;
import restudio.rebase.Rebase;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.settings.controllers.VersionSettingsController;
import restudio.rebase.util.VersionUtil;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.LoadingAnimationWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.nio.file.Path;
import java.util.*;
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
            boolean isRemote = instance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);

            if (isRemote || remoteHostContext != null) {
                if (remoteHostContext != null) {
                    Map<String, String> creds = new HashMap<>();
                    creds.put("host", remoteHostContext.getIp());
                    creds.put("port", String.valueOf(remoteHostContext.getPort()));
                    creds.put("user", remoteHostContext.getUser());
                    creds.put("password", remoteHostContext.getPassword());
                    this.tempInstance.setBackendConfig(new BackendConfig("SSH", creds));
                } else {
                    this.tempInstance.setBackendConfig(instance.getBackendConfig());
                }
            }
        } else {
            this.tempInstance = new Instance("New Server", remotelyClient.getHost().getGameVersion(), "");
            if (remoteHostContext != null) {
                Map<String, String> creds = new HashMap<>();
                creds.put("host", remoteHostContext.getIp());
                creds.put("port", String.valueOf(remoteHostContext.getPort()));
                creds.put("user", remoteHostContext.getUser());
                creds.put("password", remoteHostContext.getPassword());
                this.tempInstance.setBackendConfig(new BackendConfig("SSH", creds));
            }
        }
    }

    @Override
    public void init() {
        super.init();

        LoadingAnimationWidget loadingWidget = new LoadingAnimationWidget(0, 0, width, height);
        addDrawableChild(loadingWidget);

        CompletableFuture<Void> propertiesFuture;
        CompletableFuture<Void> settingsFuture;
        CompletableFuture<List<String>> filesFuture;

        boolean isRemote = tempInstance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(tempInstance.getBackendConfig().type);

        if (isEditMode) {
            if (isRemote) {
                propertiesFuture = tempInstance.loadRemoteServerProperties();
                settingsFuture = tempInstance.reloadSettingsFromBackend();
            } else {
                propertiesFuture = CompletableFuture.runAsync(tempInstance::loadServerProperties);
                settingsFuture = CompletableFuture.completedFuture(null);
            }
            filesFuture = RebaseApiFactory.get(tempInstance).listDirectory(Path.of(tempInstance.getPath()))
                .thenApply(entries -> entries.stream().map(RebaseAPI.FileEntry::toString).toList())
                .exceptionally(e -> new ArrayList<>());
        } else {
            tempInstance.loadServerProperties();
            propertiesFuture = CompletableFuture.completedFuture(null);
            settingsFuture = CompletableFuture.completedFuture(null);
            filesFuture = CompletableFuture.completedFuture(new ArrayList<>());
        }

        CompletableFuture.allOf(propertiesFuture, settingsFuture, filesFuture).thenRun(() -> {
            List<String> files = filesFuture.join();
            ScreenManager.getInstance().execute(() -> setupSettingsUI(files));
        }).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                new Notification("Error", "Could not load server configuration: " + e.getMessage(), Notification.Type.ERROR);
                close();
            });
            return null;
        });
    }

    private void setupSettingsUI(List<String> extraFiles) {
        Map<String, Supplier<List<Setting>>> settingsByTab = new LinkedHashMap<>();
        List<Runnable> cleanupActions = new ArrayList<>();

        VersionSettingsController versionController = new VersionSettingsController(tempInstance);
        ServerGeneralSettingsController generalController = new ServerGeneralSettingsController(tempInstance);

        settingsByTab.put("General", () -> {
            List<Setting> settings = new ArrayList<>();
            settings.addAll(generalController.getSettings());
            settings.addAll(versionController.getSettings());
            return settings;
        });

        ServerAdvancedSettingsController advancedController = new ServerAdvancedSettingsController(tempInstance);
        settingsByTab.put("Advanced", advancedController::getSettings);

        ServerFeatureSettingsController featureController = new ServerFeatureSettingsController(tempInstance);
        settingsByTab.put("Features", featureController::getSettings);

        ServerPerformanceSettingsController performanceController = new ServerPerformanceSettingsController(tempInstance);
        settingsByTab.put("Performance", performanceController::getSettings);

        boolean msmpCompatible = VersionUtil.isMSMPCompatible(tempInstance.getVersionId());

        if (msmpCompatible) {
            ServerManagementSettingsController managementController = new ServerManagementSettingsController(tempInstance);
            settingsByTab.put("Management", managementController::getSettings);
        }

        boolean msmpEnabled = Boolean.parseBoolean(tempInstance.getServerProperties().getProperty("management-server-enabled", "false"));

        if (isEditMode) {
            PlayerActionsSettingsController playerActionsController = new PlayerActionsSettingsController(originalInstance);
            settingsByTab.put("Player Actions", playerActionsController::getSettings);

            if (msmpCompatible && msmpEnabled) {
                ServerGameRulesSettingsController gameRulesController = new ServerGameRulesSettingsController(originalInstance);
                cleanupActions.add(gameRulesController::cleanup);

                ServerLiveSettingsController liveSettingsController = new ServerLiveSettingsController(originalInstance);
                Supplier<List<Setting>> settings = () -> {
                    List<Setting> combinedSettings = new ArrayList<>();
                    combinedSettings.addAll(gameRulesController.getSettings());
                    combinedSettings.addAll(liveSettingsController.getSettings());
                    return combinedSettings;
                };
                settingsByTab.put("Live Settings", settings);
                cleanupActions.add(liveSettingsController::cleanup);
            }
        }

        ServerExtraSettingsController extraController = new ServerExtraSettingsController(tempInstance, extraFiles);
        settingsByTab.put("Extra Files", extraController::getSettings);

        Runnable combinedCleanup = () -> cleanupActions.forEach(Runnable::run);

        SettingsScreen settingsScreen = new SettingsScreen(parent, isEditMode ? "Edit " + originalInstance.getName() : "Create New Server", settingsByTab, this::saveConfiguration, combinedCleanup);
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

    }

    private void createNewLocalServer() {
        ServerDetailsScreen details = new ServerDetailsScreen(this, remotelyClient);
        client.setScreen(details);
        tempInstance.setState(InstanceState.INSTALLING);
        details.addInstanceTab(tempInstance);
        Rebase.get().getInstanceManager().createInstanceWithLogger(tempInstance).thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
            newInstance.getServerProperties().putAll(tempInstance.getServerProperties());
            newInstance.getSettings().putAll(tempInstance.getSettings());
            newInstance.saveServerProperties();
            newInstance.save();
            handleOpMe(newInstance);
            newInstance.setState(InstanceState.STOPPED);
        })).exceptionally(ex -> {
            ScreenManager.getInstance().execute(() -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                tempInstance.getLogger().addLog("[Progress:0] Creation failed: " + cause.getMessage());
                tempInstance.setState(InstanceState.CRASHED);
            });
            return null;
        });
    }

    private void createNewRemoteServer() {
        ServerDetailsScreen details = new ServerDetailsScreen(this, remotelyClient);
        client.setScreen(details);
        tempInstance.setState(InstanceState.INSTALLING);
        details.addInstanceTab(tempInstance);
        Rebase.get().getInstanceManager().createRemoteInstanceWithLogger(tempInstance, remoteHostContext).thenCompose(newInstance -> Rebase.get().getInstanceManager().fetchRemoteInstances(remoteHostContext).handle((v, e) -> null).thenApply(v -> newInstance))
            .thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
                handleOpMe(newInstance);
                newInstance.setState(InstanceState.STOPPED);
            })).exceptionally(ex -> {
                ScreenManager.getInstance().execute(() -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    tempInstance.getLogger().addLog("[Progress:0] Remote creation failed: " + cause.getMessage());
                    tempInstance.setState(InstanceState.CRASHED);
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
        boolean isRemote = originalInstance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(originalInstance.getBackendConfig().type);

        if (versionChanged) {
            Notification notification = new Notification.Builder().message("Applying Version Changes...").autoSlideOut(false).image(Identifier.animatedIcon("loadingGreen.png")).animateImage(true).accent(ThemeManager.getAccent("calm")).build();

            if (isRemote) {
                RemoteHost host = remoteHostContext;
                if(host == null) {
                    for(RemoteHost h : Rebase.get().getInstanceManager().getRemoteHosts()) {
                        if(originalInstance.getBackendConfig().credentials.getOrDefault("host", "").equals(h.getIp())) {
                            host = h;
                            break;
                        }
                    }
                }

                if (host != null) {
                    RemoteHost finalHost = host;
                    Rebase.get().getInstanceManager().createRemoteInstance(originalInstance, host, notification).thenCompose(newInstance -> Rebase.get().getInstanceManager().fetchRemoteInstances(finalHost).handle((v, e) -> null).thenApply(v -> newInstance)).thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
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
                    notification.update().message("Update Failed").description("Could not resolve remote host context").type(Notification.Type.ERROR);
                }
            } else {
                Rebase.get().getInstanceManager().createInstance(originalInstance, notification).thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
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

    private void handleOpMe(Instance instance) {
        if (Boolean.parseBoolean(instance.getSettings().getProperty("op-me", "false"))) {
            Gson gson = new Gson();

            Map<String, Object> op = new HashMap<>();
            op.put("uuid", RemotelyClient.INSTANCE.getHost().getGameUUID());
            op.put("name", RemotelyClient.INSTANCE.getHost().getGameUserName());
            op.put("level", 4);
            op.put("bypassesPlayerLimit", false);

            List<Map<String, Object>> ops = List.of(op);
            String json = gson.toJson(ops);

            Path opsFile = Path.of(instance.getPath(), "ops.json");
            RebaseAPI api = RebaseApiFactory.get(instance);
            api.writeFile(opsFile, json).exceptionally(e -> {
                System.err.println("Failed to write ops.json: " + e.getMessage());
                return null;
            }).join();
        }
    }
}
