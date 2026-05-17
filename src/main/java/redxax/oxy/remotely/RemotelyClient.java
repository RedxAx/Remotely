package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.session.TerminalSessionManager;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import redxax.oxy.remotely.ui.server.ServerDetailsScreen;
import redxax.oxy.remotely.ui.server.ServerTwinScreen;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
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
    private ServerManagerScreen desktopServerManagerScreen;
    private final Map<String, ClientServerView> restudioServerViews = new java.util.concurrent.ConcurrentHashMap<>();

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
        ScreenManager.getInstance().setDesktopSuperScreenSupplier(this::getOrCreateDesktopServerManagerScreen);
        new NodeRegistry();
        System.out.println("Remotely mod initialized on client.");
        loadSnippets();

        os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        FontRegistry.MONO_FONT = host.getFontIdentifier("remotely", "mono");

        if (Rebase.get() != null && ReStudio.getInstance() != null) {
            try {
                java.lang.reflect.Field apiClientField = ReStudio.class.getDeclaredField("apiClient");
                apiClientField.setAccessible(true);
                ReStudioApiClient apiClient = (ReStudioApiClient) apiClientField.get(ReStudio.getInstance());
                if (apiClient != null) {
                    flowManager = new FlowManager(this, apiClient);
                }
            } catch (Exception e) {
                System.err.println("Failed to initialize FlowManager: " + e.getMessage());
            }
        }
        if (flowManager == null) {
            flowManager = new FlowManager(this, null);
        }
    }

    public void openMultiTerminal(Object parent) {
        if (multiTerminalTabs.isEmpty()) {
            multiTerminalTabs.add(UUID.randomUUID().toString());
            activeMultiTerminalTabIndex = 0;
        }
        host.setScreen(new ServerDetailsScreen(parent, this));
    }

    public void openInstanceInTerminal(Object parent, Instance instance) {
        boolean found = multiTerminalTabs.stream().anyMatch(o -> o instanceof Instance i && i.equals(instance));
        if (!found) {
            multiTerminalTabs.add(instance);
        }

        for (int i = 0; i < multiTerminalTabs.size(); i++) {
            Object o = multiTerminalTabs.get(i);
            if (o instanceof Instance inst && inst.equals(instance)) {
                activeMultiTerminalTabIndex = i;
                break;
            }
        }

        host.setScreen(new ServerDetailsScreen(parent, this, instance));
    }

    public void openServerManager(Object parent) {
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

    public void openServerTwin(Object parent, Instance instance) {
        host.setScreen(new ServerTwinScreen(parent, this, instance));
    }

    public void shutdownAllTerminals() {
        sessionManager.shutdownAll();
        TerminalWidget.shutdownAll();
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
            try (java.io.PrintWriter writer = new java.io.PrintWriter(tempFile)) {
                writer.println("-cp");
                writer.println(classpath);
                writer.println(className);
            }

            new ProcessBuilder(javaBin, "@" + tempFile.getAbsolutePath()).start();
            return true;
        } catch (IOException e) {
            System.out.println("Failed to open Remotely externally: " + e.getMessage());
            e.printStackTrace();
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

    public void openReSyncStudio(Object parent, Instance instance) {
        openReSyncStudio(parent, instance, null);
    }

    public void openReSyncStudio(Object parent, Instance instance, ClientServerView serverView) {
        if (flowManager == null) {
            new Notification.Builder().message("ReSync Studio Not Available").type(Notification.Type.WARN).build();
            return;
        }
        if (instance == null) return;
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
        flowManager.openReSyncStudio(serverId, serverView, loaderHint, serverTitle);
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
        flowManager.openLiveReSyncStudio(session);
    }

}
