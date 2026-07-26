package redxax.oxy.remotely.ui.server;

import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import com.google.gson.Gson;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.discord.DiscordRpcBridge;
import redxax.oxy.remotely.discord.DiscordRpcSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.*;
import redxax.oxy.remotely.ui.settings.controllers.ServerBackupSettingsController;
import restudio.rebase.Rebase;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.backend.impl.PteroBackend;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.settings.controllers.VersionSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerSubuserSettingsController;
import restudio.rebase.util.Executors;
import restudio.rebase.util.VersionUtil;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.screens.DesktopWindowsOverlay;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.ScreenWindowWidget;
import restudio.rescreen.ui.widgets.LoadingAnimationWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static restudio.rescreen.util.BrowserUtils.openBrowser;
import static restudio.rescreen.util.SoundUtils.playSound;

@SuppressWarnings("unchecked")
public class ServerConfigurationScreen extends ReScreen {
    private record InitialConfigLoad(List<String> extraFiles) {}

    private final Screen parent;
    private final boolean isEditMode;
    private final Instance originalInstance;
    private final Instance tempInstance;
    private final RemoteHost remoteHostContext;
    private final RemotelyClient remotelyClient;
    private final boolean isReStudioCreation;
    private final String preselectedPlanName;
    private final Consumer<Instance> creationInitializer;
    private final Consumer<Instance> creationCallback;

    private final Map<String, String> remoteVariables = new HashMap<>();
    private final Map<String, String> originalRemoteVariables = new HashMap<>();
    private final boolean isReStudioBackend;
    private String serverIdentifier;
    private ServerPlanSettingsController planController;
    private ServerBackupSettingsController backupController;
    private ServerSubuserSettingsController subuserController;
    private ServerNetworkSettingsController networkController;
    private TextInputWidget instanceLocationField;
    private volatile boolean screenClosed;

    private static final Set<String> REINSTALL_TRIGGERING_VARS = Set.of(
        "VERSION", "SOFTWARE", "BUILD", "MODPACK_SOURCE", "DOWNLOAD_URL", "AUTOMATIC_UPDATING"
    );

    public ServerConfigurationScreen(Screen parent, Instance instance, RemoteHost remoteHostContext, RemotelyClient remotelyClient) {
        this(parent, instance, remoteHostContext, remotelyClient, false);
    }

    public ServerConfigurationScreen(Screen parent, Instance instance, RemoteHost remoteHostContext, RemotelyClient remotelyClient, boolean isReStudioCreation) {
        this(parent, instance, remoteHostContext, remotelyClient, isReStudioCreation, null);
    }

    public ServerConfigurationScreen(Screen parent, Instance instance, RemoteHost remoteHostContext, RemotelyClient remotelyClient, boolean isReStudioCreation, String preselectedPlanName) {
        this(parent, instance, remoteHostContext, remotelyClient, isReStudioCreation, preselectedPlanName, null, null, null);
    }

    public ServerConfigurationScreen(Screen parent, RemoteHost remoteHostContext, RemotelyClient remotelyClient, ModLoader preset, Consumer<Instance> creationCallback) {
        this(parent, null, remoteHostContext, remotelyClient, false, null, preset, null, creationCallback);
    }

    public ServerConfigurationScreen(Screen parent, RemoteHost remoteHostContext, RemotelyClient remotelyClient, ModLoader preset, Consumer<Instance> creationInitializer, Consumer<Instance> creationCallback) {
        this(parent, null, remoteHostContext, remotelyClient, false, null, preset, creationInitializer, creationCallback);
    }

    private ServerConfigurationScreen(Screen parent, Instance instance, RemoteHost remoteHostContext, RemotelyClient remotelyClient, boolean isReStudioCreation, String preselectedPlanName, ModLoader preset, Consumer<Instance> creationInitializer, Consumer<Instance> creationCallback) {
        super();
        this.parent = parent;
        this.isEditMode = instance != null;
        this.originalInstance = instance;
        this.remoteHostContext = remoteHostContext;
        this.remotelyClient = remotelyClient;
        this.isReStudioCreation = isReStudioCreation;
        this.preselectedPlanName = preselectedPlanName;
        this.creationInitializer = creationInitializer;
        this.creationCallback = creationCallback;

        if (isEditMode) {
            this.tempInstance = new Instance(instance, instance.getName());
            boolean isRemote = instance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);
            this.isReStudioBackend = instance.getBackendConfig() != null && "RESTUDIO".equalsIgnoreCase(instance.getBackendConfig().type);
            this.serverIdentifier = isReStudioBackend ? instance.getBackendConfig().credentials.get("identifier") : null;

            if (isRemote || remoteHostContext != null) {
                if (remoteHostContext != null) {
                    this.tempInstance.setBackendConfig(createBackendConfigForRemoteHost(remoteHostContext, instance));
                } else {
                    this.tempInstance.setBackendConfig(instance.getBackendConfig());
                }
            }
        } else {
            this.tempInstance = new Instance("New Server", remotelyClient.getHost().getGameVersion(), "");
            this.tempInstance.setLocalLifecyclePersistent(true);
            this.tempInstance.setLocalRestartOnCrash(true);
            if (preset != null) {
                this.tempInstance.setModLoader(preset);
            }
            this.isReStudioBackend = false;
            if (remoteHostContext != null) {
                this.tempInstance.setBackendConfig(createBackendConfigForRemoteHost(remoteHostContext, null));
            }
        }
    }

    private BackendConfig createBackendConfigForRemoteHost(RemoteHost remoteHost, Instance sourceInstance) {
        if (remoteHost != null && "PTERO".equalsIgnoreCase(remoteHost.getType())) {
            if (sourceInstance != null && sourceInstance.getBackendConfig() != null && "PTERO".equalsIgnoreCase(sourceInstance.getBackendConfig().type)) {
                return sourceInstance.getBackendConfig();
            }
            Map<String, String> creds = new HashMap<>();
            creds.put("host", remoteHost.getIp());
            creds.put("apiUrl", PteroBackend.normalizePanelUrl(remoteHost.getIp()));
            creds.put("hostId", remoteHost.hostId);
            return new BackendConfig("PTERO", creds);
        }
        Map<String, String> creds = new HashMap<>();
        creds.put("host", remoteHost.getIp());
        creds.put("port", String.valueOf(remoteHost.getPort()));
        creds.put("user", remoteHost.getUser());
        creds.put("password", remoteHost.getPassword());
        creds.put("authMode", remoteHost.getAuthMode());
        if (remoteHost.getKeyPath() != null && !remoteHost.getKeyPath().isBlank()) {
            creds.put("keyPath", remoteHost.getKeyPath());
        }
        String passphrase = remoteHost.getKeyPassphrase();
        if (passphrase != null && !passphrase.isBlank()) {
            creds.put("keyPassphrase", passphrase);
        }
        creds.put("hostId", remoteHost.hostId);
        return new BackendConfig("SSH", creds);
    }

    public String getDesktopAppId() {
        return "server-configuration";
    }

    public String getDesktopAppTitle() {
        return "Server Configuration";
    }

    public String getDesktopAppIconPath() {
        return "change.png";
    }

    @Override
    public void init() {
        super.init();
        if (isEditMode) {
            DiscordRpcBridge.setServerSettingsActive(originalInstance);
        } else {
            DiscordRpcBridge.setServerCreationActive();
        }

        LoadingAnimationWidget loadingWidget = new LoadingAnimationWidget(0, 0, width, height);
        addDrawableChild(loadingWidget);

        loadInitialConfig().thenAccept(load -> ScreenManager.getInstance().execute(() -> {
            if (screenClosed) {
                return;
            }
            if (!isEditMode && creationInitializer != null) {
                creationInitializer.accept(tempInstance);
            }
            setupSettingsUI(load.extraFiles());
        })).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                if (screenClosed) {
                    return;
                }
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                new Notification("Error", "Could not load server configuration: " + cause.getMessage(), Notification.Type.ERROR);
                close();
            });
            return null;
        });
    }

    private CompletableFuture<InitialConfigLoad> loadInitialConfig() {
        return CompletableFuture.runAsync(() -> {
        }, Executors.IO).thenCompose(v -> {
            CompletableFuture<Void> propertiesFuture;
            CompletableFuture<Void> settingsFuture;
            CompletableFuture<List<String>> filesFuture;
            CompletableFuture<Void> remoteConfigFuture;

            boolean isRemote = tempInstance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(tempInstance.getBackendConfig().type);

            if (isEditMode) {
                if (isRemote) {
                    propertiesFuture = tempInstance.loadRemoteServerProperties();
                    settingsFuture = tempInstance.reloadSettingsFromBackend();
                } else {
                    propertiesFuture = CompletableFuture.runAsync(tempInstance::loadServerProperties, Executors.IO);
                    settingsFuture = CompletableFuture.completedFuture(null);
                }
                filesFuture = RebaseApiFactory.get(tempInstance).listDirectory(Path.of(tempInstance.getPath())).thenApply(entries -> entries.stream().map(RebaseAPI.FileEntry::toString).toList()).exceptionally(e -> new ArrayList<>());

                if (isReStudioBackend) {
                    remoteConfigFuture = ReStudio.getInstance().getApi().getServerStartupConfig(serverIdentifier).thenAccept(data -> {
                        if (data.containsKey("data")) {
                            List<Map<String, Object>> vars = (List<Map<String, Object>>) data.get("data");
                            for (Map<String, Object> varWrapper : vars) {
                                Map<String, Object> attr = (Map<String, Object>) varWrapper.get("attributes");
                                String key = (String) attr.get("env_variable");
                                String val = (String) attr.get("server_value");
                                remoteVariables.put(key, val);
                                originalRemoteVariables.put(key, val);
                            }
                        }
                    }).exceptionally(e -> {
                        ReLog.logger(LogTypes.CONFIGURATION).source(LogSource.application("Remotely")).component(ServerConfigurationScreen.class).operation("Load Startup Configuration").error("Could not load startup configuration", e);
                        return null;
                    });
                } else {
                    remoteConfigFuture = CompletableFuture.completedFuture(null);
                }

            } else {
                propertiesFuture = CompletableFuture.runAsync(tempInstance::loadServerProperties, Executors.IO);
                settingsFuture = CompletableFuture.completedFuture(null);
                filesFuture = CompletableFuture.completedFuture(new ArrayList<>());

                if (isReStudioCreation) {
                    remoteVariables.put("SOFTWARE", "PAPER");
                    remoteVariables.put("VERSION", "latest");
                    remoteVariables.put("BUILD", "latest");
                }

                remoteConfigFuture = CompletableFuture.completedFuture(null);
            }

            return CompletableFuture.allOf(propertiesFuture, settingsFuture, remoteConfigFuture)
                    .thenCompose(ignored -> filesFuture)
                    .thenApply(files -> new InitialConfigLoad(new ArrayList<>(files)));
        });
    }

    private void setupSettingsUI(List<String> extraFiles) {
        Map<String, Supplier<List<Setting>>> settingsByTab = new LinkedHashMap<>();
        List<Runnable> cleanupActions = new ArrayList<>();

        VersionSettingsController versionController;
        if (isReStudioBackend || isReStudioCreation) {
            versionController = new VersionSettingsController(tempInstance);
            versionController.bindToRemoteVariables(remoteVariables);
        } else {
            versionController = new VersionSettingsController(tempInstance);
        }

        ServerGeneralSettingsController generalController = new ServerGeneralSettingsController(tempInstance, isEditMode);

        if (isReStudioCreation) {
            planController = new ServerPlanSettingsController();
            planController.selectPlanByName(preselectedPlanName);
        }

        settingsByTab.put("General", () -> {
            List<Setting> settings = new ArrayList<>();
            if (planController != null) {
                settings.addAll(planController.getSettings());
            }
            settings.addAll(generalController.getSettings());
            if (!isEditMode && !isReStudioCreation && remoteHostContext == null) {
                if (instanceLocationField == null) {
                    instanceLocationField = new TextInputWidget.Builder()
                            .text(Rebase.get().getInstancesDir().toString())
                            .placeholder("Instances Path")
                            .size(0, 20)
                            .build();
                }
                Setting.Builder storage = new Setting.Builder("Storage");
                storage.addRow("Location", instanceLocationField);
                settings.add(storage.build());
            }
            settings.addAll(versionController.getSettings());
            return settings;
        });

        ServerAdvancedSettingsController advancedController = new ServerAdvancedSettingsController(tempInstance, isReStudioCreation);
        settingsByTab.put("Advanced", advancedController::getSettings);

        ServerFeatureSettingsController featureController = new ServerFeatureSettingsController(tempInstance);
        settingsByTab.put("Features", featureController::getSettings);

        if (Rebase.get().getConfigManager() instanceof RemotelyConfigManager remotelyConfigManager) {
            DiscordRpcSettingsController discordRpcController = new DiscordRpcSettingsController(originalInstance != null ? originalInstance : tempInstance, remotelyConfigManager);
            settingsByTab.put("Discord", discordRpcController::getSettings);
        }

        ServerPerformanceSettingsController performanceController = new ServerPerformanceSettingsController(tempInstance);
        settingsByTab.put("Performance", performanceController::getSettings);

        ServerJvmSettingsController javaController = new ServerJvmSettingsController(tempInstance);
        if (isReStudioBackend || isReStudioCreation) {
            javaController.bindToRemoteVariables(remoteVariables);
        }
        settingsByTab.put("Java", javaController::getSettings);

        if (isEditMode) {
            if (backupController == null) {
                backupController = new ServerBackupSettingsController(this, tempInstance);
            }
            settingsByTab.put("Backups", backupController::getSettings);
            cleanupActions.add(backupController::cleanup);

        }

        if (isEditMode && isReStudioBackend) {
            if (networkController == null) {
                networkController = new ServerNetworkSettingsController(this, tempInstance);
            }
            settingsByTab.put("Network", networkController::getSettings);

            if (subuserController == null) {
                subuserController = new ServerSubuserSettingsController(this, tempInstance);
            }
            settingsByTab.put("Subusers", subuserController::getSettings);
        }

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

        if (isEditMode && !isReStudioCreation) {
            ServerExtraSettingsController extraController = new ServerExtraSettingsController(tempInstance, extraFiles);
            settingsByTab.put("Extra Files", extraController::getSettings);
        }

        Runnable combinedCleanup = () -> cleanupActions.forEach(Runnable::run);

        String title = isEditMode ? "Edit " + originalInstance.getName() : "Create New Server";
        if (isReStudioCreation) {
            title = "Order New Server";
        }

        SettingsScreen settingsScreen = new SettingsScreen(parent, title, settingsByTab, this::saveConfiguration, combinedCleanup);
        if (Config.desktopMode) {
            DesktopWindowsOverlay overlay = ScreenManager.getInstance().getDesktopWindowsOverlay();
            if (overlay != null) {
                for (ScreenWindowWidget window : overlay.getWindows()) {
                    if (window.getScreen() == this) {
                        window.setScreen(settingsScreen);
                        overlay.bringToFront(window);
                        return;
                    }
                }
            }
        }
        client.setScreen(settingsScreen);
    }

    private void saveConfiguration() {
        if (isReStudioCreation) {
            createReStudioServer();
        } else if (isEditMode) {
            editServer();
        } else {
            if (remoteHostContext != null) {
                createNewRemoteServer();
            } else {
                createNewLocalServer();
            }
        }
        DiscordRpcBridge.refreshTrackedInstances();
        DiscordRpcBridge.reloadSettings();
    }

    private void createReStudioServer() {
        if (planController == null) return;
        String planName = planController.getSelectedPlanName();
        if (planName == null) {
            new Notification("Error", "Please select a plan.", Notification.Type.ERROR);
            return;
        }

        Map<String, String> fileConfigs = new HashMap<>();
        try (StringWriter writer = new StringWriter()) {
            tempInstance.getServerProperties().store(writer, "Minecraft server properties");
            tempInstance.getServerProperties().remove("server-port");
            fileConfigs.put("server.properties", writer.toString());
            String opsJson = createOpMeFileContent(tempInstance);
            if (opsJson != null) {
                fileConfigs.put("ops.json", opsJson);
            }
        } catch (IOException e) {
            new Notification("Error", "Failed to prepare server properties: " + e.getMessage(), Notification.Type.ERROR);
            return;
        }

        String subdomain = planController.getSubdomain();

        ReStudio.getInstance().getApi().createCheckoutSessionDetails(tempInstance.getName(), planName, null, remoteVariables, fileConfigs, null, subdomain, null, planController.getCustomPlanRequest()).thenAccept(checkout -> {
            openBrowser(checkout.url);
            ScreenManager.getInstance().execute(this::close);
        }).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> new Notification("Checkout Error", e.getMessage(), Notification.Type.ERROR));
            return null;
        });
    }

    private void createNewLocalServer() {
        Path location;
        try {
            String requested = instanceLocationField == null ? "" : instanceLocationField.getText().trim();
            location = requested.isEmpty() ? Rebase.get().getInstancesDir() : Path.of(requested);
        } catch (RuntimeException exception) {
            new Notification("Invalid Location", "Choose a valid instances folder.", Notification.Type.ERROR);
            return;
        }
        ServerDetailsScreen details = new ServerDetailsScreen(parent, remotelyClient);
        closeCreationWindowForDesktop();
        client.setScreen(details);
        tempInstance.setState(InstanceState.INSTALLING);
        details.addInstanceTab(tempInstance);
        Rebase.get().getInstanceManager().createInstanceWithLogger(tempInstance, location).thenCompose(newInstance -> {
            newInstance.getServerProperties().putAll(tempInstance.getServerProperties());
            newInstance.getSettings().putAll(tempInstance.getSettings());
            return newInstance.saveServerProperties()
                    .thenCompose(v -> newInstance.save())
                    .thenApply(v -> newInstance);
        }).thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
            handleOpMe(newInstance);
            newInstance.setState(InstanceState.STOPPED);
            if (creationCallback != null) {
                creationCallback.accept(newInstance);
            }
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
        ServerDetailsScreen details = new ServerDetailsScreen(parent, remotelyClient);
        closeCreationWindowForDesktop();
        client.setScreen(details);
        tempInstance.setState(InstanceState.INSTALLING);
        details.addInstanceTab(tempInstance);
        Notification notification = new Notification.Builder()
                .message("Creating Remote Server")
                .description(tempInstance.getName())
                .type(Notification.Type.INFO)
                .loading(true)
                .autoSlideOut(false)
                .image(Identifier.animatedIcon("loadingGreen.png"))
                .animateImage(true)
                .accent(ThemeManager.getAccent("calm"))
                .build();
        Rebase.get().getInstanceManager().createRemoteInstanceWithLogger(tempInstance, remoteHostContext).thenCompose(newInstance -> Rebase.get().getInstanceManager().fetchRemoteInstances(remoteHostContext).handle((v, e) -> {
                if (e != null) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    ScreenManager.getInstance().execute(() -> new Notification("Refresh Failed", cause.getMessage(), Notification.Type.WARN));
                }
                return newInstance;
            }))
            .thenAccept(newInstance -> ScreenManager.getInstance().execute(() -> {
                handleOpMe(newInstance);
                newInstance.setState(InstanceState.STOPPED);
                if (creationCallback != null) {
                    creationCallback.accept(newInstance);
                }
                notification.update().message("Remote Server Created").description(newInstance.getName()).type(Notification.Type.SUCCESS).loading(false).image(null).autoSlideOut(true);
            })).exceptionally(ex -> {
                ScreenManager.getInstance().execute(() -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    tempInstance.getLogger().addLog("[Progress:0] Remote creation failed: " + cause.getMessage());
                    tempInstance.setState(InstanceState.CRASHED);
                    notification.update().message("Creation Failed").description(cause.getMessage()).type(Notification.Type.ERROR).loading(false).image(null).autoSlideOut(true);
                });
                return null;
            });
    }

    private void closeCreationWindowForDesktop() {
        if (!Config.desktopMode) {
            return;
        }
        DesktopWindowsOverlay overlay = ScreenManager.getInstance().getDesktopWindowsOverlay();
        if (overlay == null) {
            return;
        }
        ScreenWindowWidget window = overlay.getActiveWindow();
        if (window != null && window.getScreen() instanceof SettingsScreen) {
            overlay.requestCloseWindowForScreen(window.getScreen());
        }
    }

    private void editServer() {
        String newName = tempInstance.getName();
        ModLoader oldLoader = originalInstance.getModLoader();
        String oldVersion = originalInstance.getVersionId();
        String oldServerSoftware = originalInstance.getServerSoftwareType();
        String oldServerBuild = originalInstance.getServerBuildNumber();
        boolean versionChanged = oldLoader != tempInstance.getModLoader() || (oldVersion == null ? tempInstance.getVersionId() != null : !oldVersion.equals(tempInstance.getVersionId()));
        Notification updateNotification = versionChanged && !isReStudioBackend
                ? new Notification.Builder().message("Applying Version Changes...").autoSlideOut(false).image(Identifier.animatedIcon("loadingGreen.png")).animateImage(true).accent(ThemeManager.getAccent("calm")).build()
                : null;

        Rebase.get().getInstanceManager().applyInstanceEdit(originalInstance, tempInstance, newName, !isReStudioBackend, versionChanged && !isReStudioBackend, updateNotification)
                .thenRun(() -> ScreenManager.getInstance().execute(() -> {
                    if (isReStudioBackend && serverIdentifier != null) {
                        ReStudio.getInstance().getApi().renameServer(serverIdentifier, newName).exceptionally(e -> {
                            ScreenManager.getInstance().execute(() -> new Notification("Panel Rename Failed", e.getMessage(), Notification.Type.WARN));
                            return null;
                        });
                    }
                    if (isReStudioBackend) {
                        saveRemoteVariables(resolveAllowedReStudioStartupChanges(oldLoader, oldVersion, oldServerSoftware, oldServerBuild));
                    }

                    if (versionChanged) {
                        if (!isReStudioBackend) {
                            RemoteHost host = resolveRemoteHostForOriginalInstance();
                            CompletableFuture<Void> refreshFuture = host != null ? Rebase.get().getInstanceManager().fetchRemoteInstances(host) : CompletableFuture.completedFuture(null);
                            refreshFuture.whenComplete((refresh, refreshError) -> ScreenManager.getInstance().execute(() -> {
                                updateNotification.update().message("Server Updated Successfully!").description("Version changes applied.").type(Notification.Type.SUCCESS).loading(false).image(null);
                                updateNotification.loading = false;
                                updateNotification.autoSlideOut = true;
                                if (refreshError != null) {
                                    Throwable cause = refreshError.getCause() != null ? refreshError.getCause() : refreshError;
                                    new Notification("Refresh Failed", cause.getMessage(), Notification.Type.WARN);
                                }
                            }));
                        } else {
                            new Notification("Server Configuration Saved", "Settings updated on panel.", Notification.Type.SUCCESS);
                        }
                    } else {
                        new Notification(originalInstance.getName() + " Edited Successfully!", Notification.Type.SUCCESS);
                    }
                }))
                .exceptionally(ex -> {
                    ScreenManager.getInstance().execute(() -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        if (updateNotification != null) {
                            updateNotification.update().message("Update Failed").description(cause.getMessage()).type(Notification.Type.ERROR).loading(false).image(null);
                            updateNotification.loading = false;
                            updateNotification.autoSlideOut = true;
                            return;
                        }
                        new Notification("Edit Failed", cause.getMessage(), Notification.Type.ERROR);
                    });
                    return null;
                });
    }

    private RemoteHost resolveRemoteHostForOriginalInstance() {
        if (remoteHostContext != null) {
            return remoteHostContext;
        }
        BackendConfig config = originalInstance.getBackendConfig();
        if (config == null || config.credentials == null) {
            return null;
        }
        String hostId = config.credentials.get("hostId");
        String host = config.credentials.getOrDefault("host", "");
        for (RemoteHost remoteHost : Rebase.get().getInstanceManager().getRemoteHosts()) {
            if (hostId != null && hostId.equals(remoteHost.hostId)) {
                return remoteHost;
            }
            if (!host.isBlank() && host.equals(remoteHost.getIp())) {
                return remoteHost;
            }
        }
        return null;
    }

    private Set<String> resolveAllowedReStudioStartupChanges(ModLoader oldLoader, String oldVersion, String oldServerSoftware, String oldServerBuild) {
        Set<String> allowed = new HashSet<>();
        if (oldVersion == null ? originalInstance.getVersionId() != null : !oldVersion.equals(originalInstance.getVersionId())) {
            allowed.add("VERSION");
        }
        if (oldLoader != originalInstance.getModLoader() || !Objects.equals(normalized(oldServerSoftware), normalized(originalInstance.getServerSoftwareType()))) {
            allowed.add("SOFTWARE");
        }
        if (!Objects.equals(normalized(oldServerBuild), normalized(originalInstance.getServerBuildNumber()))) {
            allowed.add("BUILD");
        }
        return allowed;
    }

    private String normalized(String value) {
        return value == null || value.isBlank() || "latest".equalsIgnoreCase(value) ? null : value.trim();
    }

    private void saveRemoteVariables(Set<String> allowedReinstallVariables) {
        if (!isReStudioBackend || remoteVariables.isEmpty()) return;

        Set<String> reinstallTriggeringChanges = new HashSet<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<String, String> entry : remoteVariables.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            String oldValue = originalRemoteVariables.get(key);

            if (!newValue.equals(oldValue)) {
                if (REINSTALL_TRIGGERING_VARS.contains(key) && (allowedReinstallVariables == null || !allowedReinstallVariables.contains(key))) {
                    continue;
                }
                futures.add(ReStudio.getInstance().getApi().updateServerStartupVariable(serverIdentifier, key, newValue));

                if (REINSTALL_TRIGGERING_VARS.contains(key)) {
                    reinstallTriggeringChanges.add(key);
                }
            }
        }

        if (futures.isEmpty()) {
            return;
        }

        if (!reinstallTriggeringChanges.isEmpty()) {
            new Notification.Builder()
                .message("Server Reinstall Required")
                .description("Changes to " + String.join(", ", reinstallTriggeringChanges) + " will trigger a server reinstall.")
                .type(Notification.Type.WARN)
                .autoSlideOut(true)
                .build();
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> new Notification("Save Warning", "Some startup variables failed to update.", Notification.Type.WARN));
            return null;
        });
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }
    @Override
    public boolean keyPressed(ReKeyEvent event) {
        if (event.key() == ReKey.ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(event);
    }


    public void close() {
        screenClosed = true;
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        screenClosed = true;
        super.removed();
    }

    private void handleOpMe(Instance instance) {
        String json = createOpMeFileContent(instance);
        if (json == null) return;

        Path opsFile = Path.of(instance.getPath(), "ops.json");
        RebaseAPI api = RebaseApiFactory.get(instance);
        api.writeFile(opsFile, json).exceptionally(e -> {
            ReLog.logger(LogTypes.CONFIGURATION).source(LogSource.instance(instance.getInstanceId(), instance.getName())).component(ServerConfigurationScreen.class).operation("Grant Operator Access").error("Could not update operators", e);
            return null;
        });
    }

    private String createOpMeFileContent(Instance instance) {
        if (!Boolean.parseBoolean(instance.getSettings().getProperty("op-me", "false"))) return null;
        String uuid = RemotelyClient.INSTANCE.getHost().getGameUUID();
        String name = RemotelyClient.INSTANCE.getHost().getGameUserName();
        if (uuid == null || uuid.isBlank() || name == null || name.isBlank()) return null;

        Gson gson = new Gson();
        Map<String, Object> op = new HashMap<>();
        op.put("uuid", uuid);
        op.put("name", name);
        op.put("level", 4);
        op.put("bypassesPlayerLimit", false);

        return gson.toJson(List.of(op));
    }
}
