package redxax.oxy.remotely;

import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.discord.DiscordRpcBridge;
import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.NetworkManager;
import redxax.oxy.remotely.network.NetworkMember;
import redxax.oxy.remotely.network.NetworkRuntimeSnapshot;
import redxax.oxy.remotely.session.TerminalSessionManager;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import redxax.oxy.remotely.ui.server.ServerDetailsScreen;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;
import restudio.rebase.terminal.ExecutorServiceManager;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.text.FontRegistry;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rebase.restudio.api.ReStudioApiClient;
import restudio.rebase.restudio.ReStudio;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncLiveServerSession;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;
import restudio.rescreen.util.Notification;
import restudio.resync.network.NetworkNodeStatus;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static redxax.oxy.remotely.config.Config.remotelyDir;

public class RemotelyClient {

    public static RemotelyClient INSTANCE;
    private final ApplicationHost host;
    private int activeHostIndex = 0;
    public static String os;
    public static ITextRenderer tr;
    private final List<Object> multiTerminalTabs = new CopyOnWriteArrayList<>();
    private int activeMultiTerminalTabIndex = 0;
    private final TerminalSessionManager sessionManager = new TerminalSessionManager();
    private FlowManager flowManager;
    private NetworkManager networkManager;
    private ServerManagerScreen desktopServerManagerScreen;
    private final Map<String, ClientServerView> restudioServerViews = new ConcurrentHashMap<>();

    public RemotelyClient(ApplicationHost host) {
        this.host = host;
        INSTANCE = this;
    }

    public void initialize() {
        Config.applicationDir = remotelyDir;
        try {
            if (Rebase.get() != null && Rebase.get().getConfigManager() instanceof RemotelyConfigManager) {
                Config.setConfigManager(Rebase.get().getConfigManager());
            } else {
                Config.setConfigManager(new RemotelyConfigManager(remotelyDir));
            }
        } catch (IllegalStateException e) {
            Config.setConfigManager(new RemotelyConfigManager(remotelyDir));
        }

        ThemeManager.init();
        host.ensureTextRenderer();
        networkManager = new NetworkManager(remotelyDir);
        if (!networkManager.getLoadError().isBlank()) {
            ReLog.logger(LogTypes.NETWORK).source(LogSource.application("Remotely")).component(RemotelyClient.class).with("reason", networkManager.getLoadError()).error("Could not load networks");
        }
        if (!networkManager.getJobManager().getLoadError().isBlank()) {
            ReLog.logger(LogTypes.NETWORK).source(LogSource.application("Remotely")).component(RemotelyClient.class).with("reason", networkManager.getJobManager().getLoadError()).error("Could not load network jobs");
        }
        try {
            List<Instance> instances = Rebase.get().getInstanceManager().getAllInstances();
            networkManager.recoverCompletedJobs(instances).whenComplete((unused, throwable) -> {
                if (throwable != null) {
                    ReLog.logger(LogTypes.NETWORK).source(LogSource.application("Remotely")).component(RemotelyClient.class).operation("Recover Network Jobs").error("Could not recover completed network jobs", throwable);
                }
                networkManager.reconcileInstanceBindings(Rebase.get().getInstanceManager().getAllInstances());
            });
            Rebase.get().getInstanceManager().addChangeListener(() -> networkManager.reconcileInstanceBindings(Rebase.get().getInstanceManager().getAllInstances()));
        } catch (IllegalStateException exception) {
            ReLog.logger(LogTypes.NETWORK).source(LogSource.application("Remotely")).component(RemotelyClient.class).operation("Reconcile Network Bindings").error("Could not register network reconciliation", exception);
        }
        if (host.managesPrimaryScreen()) {
            ScreenManager.getInstance().setDesktopSuperScreenSupplier(this::getOrCreateDesktopServerManagerScreen);
        }
        new NodeRegistry();
        ReLog.logger(LogTypes.APPLICATION).source(LogSource.application("Remotely")).component(RemotelyClient.class).info("Client initialized");
        loadSnippets();

        os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        FontRegistry.MONO_FONT = host.getFontIdentifier("remotely", "mono");

        if (Rebase.get() != null && ReStudio.getInstance() != null) {
            try {
                Field apiClientField = ReStudio.class.getDeclaredField("apiClient");
                apiClientField.setAccessible(true);
                ReStudioApiClient apiClient = (ReStudioApiClient) apiClientField.get(ReStudio.getInstance());
                if (apiClient != null) {
                    flowManager = new FlowManager(this, apiClient);
                }
            } catch (Exception e) {
                ReLog.logger(LogTypes.FLOW).source(LogSource.application("Remotely")).component(RemotelyClient.class).error("Could not initialize Flow Manager", e);
            }
        }
        if (flowManager == null) {
            flowManager = new FlowManager(this, null);
        }
        RemotelyConfigManager discordConfigManager = resolveDiscordConfigManager();
        if (host.supportsDesktopIntegrations() && discordConfigManager != null && Rebase.get() != null) {
            DiscordRpcBridge.start(discordConfigManager, Rebase.get().getInstanceManager());
            DiscordRpcBridge.setManagerActive();
            Rebase.get().getInstanceManager().addChangeListener(DiscordRpcBridge::refreshTrackedInstances);
        }
    }

    private RemotelyConfigManager resolveDiscordConfigManager() {
        try {
            if (Rebase.get() != null && Rebase.get().getConfigManager() instanceof RemotelyConfigManager remotelyConfigManager) {
                return remotelyConfigManager;
            }
        } catch (IllegalStateException ignored) {
        }
        if (Config.configManager instanceof RemotelyConfigManager remotelyConfigManager) {
            return remotelyConfigManager;
        }
        return null;
    }

    public void openMultiTerminal(Object parent) {
        DiscordRpcBridge.setLocalTerminalActive();
        if (multiTerminalTabs.isEmpty()) {
            multiTerminalTabs.add(UUID.randomUUID().toString());
            activeMultiTerminalTabIndex = 0;
        }
        host.setScreen(new ServerDetailsScreen(parent, this));
    }

    public void openInstanceInTerminal(Object parent, Instance instance) {
        DiscordRpcBridge.setServerActive(instance, "Terminal");
        boolean found = multiTerminalTabs.stream().anyMatch(tab -> sameInstanceTab(tab, instance));
        if (!found) {
            multiTerminalTabs.add(instance);
        }

        for (int i = 0; i < multiTerminalTabs.size(); i++) {
            if (sameInstanceTab(multiTerminalTabs.get(i), instance)) {
                activeMultiTerminalTabIndex = i;
                break;
            }
        }

        host.setScreen(new ServerDetailsScreen(parent, this, instance));
    }

    private boolean sameInstanceTab(Object tab, Instance instance) {
        return tab instanceof Instance existing && sameInstance(existing, instance);
    }

    private boolean sameInstance(Instance a, Instance b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        String aId = a.getInstanceId();
        String bId = b.getInstanceId();
        if (aId != null && bId != null && !aId.isBlank() && !bId.isBlank()) {
            return aId.equals(bId);
        }
        return a.equals(b);
    }

    public void openServerManager(Object parent) {
        DiscordRpcBridge.setManagerActive();
        if (Config.desktopMode) {
            Screen desktopSuper = ScreenManager.getInstance().getDesktopSuperScreen();
            if (desktopSuper instanceof ServerManagerScreen existing) {
                desktopServerManagerScreen = existing;
                host.setScreen(existing);
                return;
            }
            host.setScreen(getOrCreateDesktopServerManagerScreen());
            return;
        }
        host.setScreen(new ServerManagerScreen(parent, this));
    }

    private ServerManagerScreen getOrCreateDesktopServerManagerScreen() {
        Screen desktopSuper = ScreenManager.getInstance().getDesktopSuperScreen();
        if (desktopSuper instanceof ServerManagerScreen existing) {
            desktopServerManagerScreen = existing;
            return existing;
        }
        if (desktopServerManagerScreen == null) {
            desktopServerManagerScreen = new ServerManagerScreen(null, this);
        }
        return desktopServerManagerScreen;
    }

    public void openFileExplorer(Object parent, Path path) {
        Screen reScreenParent = parent instanceof Screen ? (Screen) parent : null;
        host.setScreen(new FileExplorerScreen(reScreenParent, null, path, Path.of(remotelyDir.toString(), "data"), false) {
            public String getDesktopAppId() {
                return "file-explorer";
            }

            public String getDesktopAppTitle() {
                return "File Explorer";
            }

            public String getDesktopAppIconPath() {
                return "explorer.png";
            }

            @Override
            public void close() {
                host.openParentScreen(this, parent);
            }
        });
    }

    public void openInstanceFiles(Object parent, Instance instance) {
        if (instance == null) return;
        Screen reScreenParent = parent instanceof Screen ? (Screen) parent : null;
        host.setScreen(new FileExplorerScreen(reScreenParent, instance, Path.of(instance.getPath()), Path.of(remotelyDir.toString(), "data"), false) {
            public String getDesktopAppId() {
                return "file-explorer";
            }

            public String getDesktopAppTitle() {
                return "File Explorer";
            }

            public String getDesktopAppIconPath() {
                return "explorer.png";
            }

            @Override
            public void close() {
                host.openParentScreen(this, parent);
            }
        });
    }

    public void openServerTwin(Object parent, Instance instance) {
        host.setScreen(new ServerDetailsScreen(parent, this, instance, true));
    }

    public void shutdownAllTerminals() {
        DiscordRpcBridge.shutdown();
        if (flowManager != null) {
            flowManager.shutdown();
        }
        if (networkManager != null) {
            networkManager.close();
        }
        sessionManager.shutdownAll();
        TerminalWidget.shutdownAll();
        ExecutorServiceManager.shutdownSharedExecutors();
        saveSnippets();
    }

    public void saveTabIndex(int activeTabIndex) {
        activeHostIndex = activeTabIndex;
    }

    public int getSavedTabIndex() {
        return activeHostIndex;
    }

    public void saveSnippets() {}
    public void loadSnippets() {}

    public boolean openExternal() {
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");
            String className = RemotelyInit.class.getName();

            File tempFile = File.createTempFile("remotely_args", ".txt");
            tempFile.deleteOnExit();
            try (PrintWriter writer = new PrintWriter(tempFile)) {
                writer.println("-cp");
                writer.println(classpath);
                writer.println(className);
            }

            new ProcessBuilder(javaBin, "@" + tempFile.getAbsolutePath()).start();
            return true;
        } catch (IOException e) {
            ReLog.logger(LogTypes.USER_INTERFACE).source(LogSource.application("Remotely")).component(RemotelyClient.class).operation("Open External Window").error("Could not open Remotely externally", e);
            return false;
        }
    }

    public List<Object> getMultiTerminalTabs() {
        return multiTerminalTabs;
    }

    public int getActiveMultiTerminalTabIndex() {
        return activeMultiTerminalTabIndex;
    }

    public void setActiveMultiTerminalTabIndex(int activeMultiTerminalTabIndex) {
        this.activeMultiTerminalTabIndex = activeMultiTerminalTabIndex;
    }

    public ApplicationHost getHost() {
        return host;
    }

    public TerminalSessionManager getSessionManager() {
        return sessionManager;
    }

    public FlowManager getFlowManager() {
        return flowManager;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public void openReSyncStudio(Object parent, Instance instance) {
        openReSyncStudio(parent, instance, null);
    }

    public void openReSyncStudio(Object parent, Instance instance, ClientServerView serverView) {
        if (flowManager == null) {
            new Notification.Builder().message("ReSync Studio Not Available").type(Notification.Type.WARN).build();
            return;
        }
        if (instance == null) return;
        NetworkStudioTarget networkTarget = resolveNetworkStudioTarget(instance);
        if (networkTarget != null) {
            DiscordRpcBridge.setReSyncStudioActive(instance, networkTarget.title(), "Network Studio");
            flowManager.openReSyncStudio(networkTarget.instance().getInstanceId(), null, loader(networkTarget.instance()), networkTarget.title());
            return;
        }
        String serverId;
        boolean isReStudio = instance.getBackendConfig() != null && "RESTUDIO".equalsIgnoreCase(instance.getBackendConfig().type);
        if (isReStudio) {
            serverId = instance.getBackendConfig().credentials.get("identifier");
            if (serverView == null) {
                serverView = restudioServerViews.get(instance.getName());
            }
        } else {
            serverId = instance.getInstanceId();
        }
        String loaderHint = "";
        if (instance.getModLoader() != null) {
            loaderHint = instance.getModLoader().name();
        }
        if (serverView != null && serverView.loader != null && !serverView.loader.isBlank()) {
            loaderHint = serverView.loader;
        }
        String serverTitle = serverView != null && serverView.name != null && !serverView.name.isBlank() ? serverView.name : instance.getName();
        DiscordRpcBridge.setReSyncStudioActive(instance, serverTitle, "Studio");
        flowManager.openReSyncStudio(serverId, serverView, loaderHint, serverTitle);
    }

    private NetworkStudioTarget resolveNetworkStudioTarget(Instance instance) {
        if (networkManager == null) {
            return null;
        }
        NetworkDefinition network = networkManager.getNetworkForInstance(instance.getInstanceId()).orElse(null);
        if (network == null || !network.proxyInstanceId().equals(instance.getInstanceId())) {
            return null;
        }
        NetworkRuntimeSnapshot runtime = networkManager.getRuntimeSnapshot(network.networkId());
        if (runtime == null || !runtime.connected()) {
            return null;
        }
        InstanceManager instances = Rebase.get() == null ? null : Rebase.get().getInstanceManager();
        if (instances == null) {
            return null;
        }
        for (NetworkMember member : network.members()) {
            if (member.isProxy() || !member.isManaged() || !member.resyncEnabled()) {
                continue;
            }
            NetworkNodeStatus status = runtime.node(member.nodeId()).map(presence -> presence.status()).orElse(NetworkNodeStatus.OFFLINE);
            if (status == NetworkNodeStatus.OFFLINE || status == NetworkNodeStatus.REVOKED) {
                continue;
            }
            Instance backend = instances.getInstanceById(member.instanceId());
            if (backend != null && flowManager.getFlowAvailabilityIssue(backend.getInstanceId(), null) == null) {
                return new NetworkStudioTarget(backend, network.name() + " Network");
            }
        }
        return null;
    }

    private static String loader(Instance instance) {
        return instance.getModLoader() == null ? "" : instance.getModLoader().name();
    }

    public void cacheReStudioServerViews(Map<String, ClientServerView> views) {
        restudioServerViews.clear();
        restudioServerViews.putAll(views);
    }

    public void openLiveReSyncStudio(ReSyncLiveServerSession session) {
        if (flowManager == null) {
            new Notification.Builder().message("ReSync Studio Not Available").type(Notification.Type.WARN).build();
            return;
        }
        DiscordRpcBridge.setReSyncStudioActive(null, session != null ? session.displayName() : "", "Live Studio");
        flowManager.openLiveReSyncStudio(session);
    }

    private record NetworkStudioTarget(Instance instance, String title) {
    }

}
