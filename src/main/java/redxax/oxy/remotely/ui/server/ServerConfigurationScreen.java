package redxax.oxy.remotely.ui.server;


import org.lwjgl.glfw.GLFW;
import com.google.gson.Gson;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.ui.settings.controllers.*;
import redxax.oxy.remotely.ui.settings.controllers.ServerBackupSettingsController;
import restudio.rebase.Rebase;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.backend.BackendConfig;
import restudio.rebase.hosting.RemoteHost;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceRepairer;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.settings.controllers.VersionSettingsController;
import redxax.oxy.remotely.ui.settings.controllers.ServerSubuserSettingsController;
import restudio.rebase.util.Executors;
import restudio.rebase.util.RebaseLogger;
import restudio.rebase.util.VersionUtil;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.screens.DesktopWindowsOverlay;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.ScreenWindowWidget;
import restudio.rescreen.ui.widgets.LoadingAnimationWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.Sound;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    private final Map<String, String> remoteVariables = new HashMap<>();
    private final Map<String, String> originalRemoteVariables = new HashMap<>();
    private final boolean isReStudioBackend;
    private String serverIdentifier;
    private ServerPlanSettingsController planController;
    private ServerBackupSettingsController backupController;
    private ServerSubuserSettingsController subuserController;
    private ServerNetworkSettingsController networkController;
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
        super();
        this.parent = parent;
        this.isEditMode = instance != null;
        this.originalInstance = instance;
        this.remoteHostContext = remoteHostContext;
        this.remotelyClient = remotelyClient;
        this.isReStudioCreation = isReStudioCreation;
        this.preselectedPlanName = preselectedPlanName;

        if (isEditMode) {
            this.tempInstance = new Instance(instance, instance.getName());
            boolean isRemote = instance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);
            this.isReStudioBackend = instance.getBackendConfig() != null && "RESTUDIO".equalsIgnoreCase(instance.getBackendConfig().type);
            this.serverIdentifier = isReStudioBackend ? instance.getBackendConfig().credentials.get("identifier") : null;

            if (isRemote || remoteHostContext != null) {
                if (remoteHostContext != null) {
                    Map<String, String> creds = new HashMap<>();
                    creds.put("host", remoteHostContext.getIp());
                    creds.put("port", String.valueOf(remoteHostContext.getPort()));
                    creds.put("user", remoteHostContext.getUser());
                    creds.put("password", remoteHostContext.getPassword());
                    creds.put("authMode", remoteHostContext.getAuthMode());
                    if (remoteHostContext.getKeyPath() != null && !remoteHostContext.getKeyPath().isBlank()) {
                        creds.put("keyPath", remoteHostContext.getKeyPath());
                    }
                    String passphrase = remoteHostContext.getKeyPassphrase();
                    if (passphrase != null && !passphrase.isBlank()) {
                        creds.put("keyPassphrase", passphrase);
                    }
                    this.tempInstance.setBackendConfig(new BackendConfig("SSH", creds));
                } else {
                    this.tempInstance.setBackendConfig(instance.getBackendConfig());
                }
            }
        } else {
            this.tempInstance = new Instance("New Server", remotelyClient.getHost().getGameVersion(), "");
            this.isReStudioBackend = false;
            if (remoteHostContext != null) {
                Map<String, String> creds = new HashMap<>();
                creds.put("host", remoteHostContext.getIp());
                creds.put("port", String.valueOf(remoteHostContext.getPort()));
                creds.put("user", remoteHostContext.getUser());
                creds.put("password", remoteHostContext.getPassword());
                creds.put("authMode", remoteHostContext.getAuthMode());
                if (remoteHostContext.getKeyPath() != null && !remoteHostContext.getKeyPath().isBlank()) {
                    creds.put("keyPath", remoteHostContext.getKeyPath());
                }
                String passphrase = remoteHostContext.getKeyPassphrase();
                if (passphrase != null && !passphrase.isBlank()) {
                    creds.put("keyPassphrase", passphrase);
                }
                this.tempInstance.setBackendConfig(new BackendConfig("SSH", creds));
            }
        }
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

        LoadingAnimationWidget loadingWidget = new LoadingAnimationWidget(0, 0, width, height);
        addDrawableChild(loadingWidget);

        loadInitialConfig().thenAccept(load -> ScreenManager.getInstance().execute(() -> {
            if (screenClosed) {
                return;
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
                        System.err.println("Failed to fetch startup config: " + e.getMessage());
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

            return CompletableFuture.allOf(propertiesFuture, settingsFuture, filesFuture, remoteConfigFuture).thenApply(ignored -> new InitialConfigLoad(new ArrayList<>(filesFuture.join())));
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

        ServerGeneralSettingsController generalController = new ServerGeneralSettingsController(tempInstance);

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
            settings.addAll(versionController.getSettings());
            return settings;
        });

        ServerAdvancedSettingsController advancedController = new ServerAdvancedSettingsController(tempInstance, isReStudioCreation);
        settingsByTab.put("Advanced", advancedController::getSettings);

        ServerFeatureSettingsController featureController = new ServerFeatureSettingsController(tempInstance);
        settingsByTab.put("Features", featureController::getSettings);

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
        String newName = tempInstance.getName();
        originalInstance.setName(newName);

        if (isReStudioBackend && serverIdentifier != null) {
            ReStudio.getInstance().getApi().renameServer(serverIdentifier, newName).exceptionally(e -> null);
        }

        ModLoader oldLoader = originalInstance.getModLoader();
        String oldVersion = originalInstance.getVersionId();
        String oldServerSoftware = originalInstance.getServerSoftwareType();
        String oldServerBuild = originalInstance.getServerBuildNumber();

        originalInstance.setModLoader(tempInstance.getModLoader());
        originalInstance.setVersionId(tempInstance.getVersionId());
        originalInstance.getSettings().putAll(tempInstance.getSettings());
        originalInstance.getServerProperties().clear();
        originalInstance.getServerProperties().putAll(tempInstance.getServerProperties());

        originalInstance.save();
        originalInstance.saveServerProperties();

        CompletableFuture<Void> scriptFuture = !isReStudioBackend
                ? InstanceRepairer.createStartScript(originalInstance).exceptionally(ex -> { RebaseLogger.log("Failed to create start script: " + ex.getMessage()); return null; })
                : CompletableFuture.completedFuture(null);

        scriptFuture.thenRun(() -> ScreenManager.getInstance().execute(() -> {
            if (isReStudioBackend) {
                saveRemoteVariables(resolveAllowedReStudioStartupChanges(oldLoader, oldVersion, oldServerSoftware, oldServerBuild));
            }

            boolean versionChanged = oldLoader != originalInstance.getModLoader() || (oldVersion == null ? originalInstance.getVersionId() != null : !oldVersion.equals(originalInstance.getVersionId()));
            boolean isRemote = originalInstance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(originalInstance.getBackendConfig().type);

                if (versionChanged) {
                    Notification notification = new Notification.Builder().message("Applying Version Changes...").autoSlideOut(false).image(Identifier.animatedIcon("loadingGreen.png")).animateImage(true).accent(ThemeManager.getAccent("calm")).build();

                    if (isRemote && !isReStudioBackend) {
                        RemoteHost host = remoteHostContext;
                        if (host == null) {
                            for (RemoteHost h : Rebase.get().getInstanceManager().getRemoteHosts()) {
                                if (originalInstance.getBackendConfig().credentials.getOrDefault("host", "").equals(h.getIp())) {
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
                    } else if (!isReStudioBackend) {
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
                    } else {
                        notification.update().message("Server Configuration Saved").description("Settings updated on panel.").type(Notification.Type.SUCCESS).loading(false).image(null).autoSlideOut(true);
                    }
                } else {
                    new Notification(originalInstance.getName() + " Edited Successfully!", Notification.Type.SUCCESS);
                }
        }));
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            });
        }
    }
}
