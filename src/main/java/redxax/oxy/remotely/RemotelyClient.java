package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.host.MinecraftApplicationHost;
import redxax.oxy.remotely.servers.ServerManagerScreen;
import redxax.oxy.remotely.ui.screens.RemotelyInstanceDetailsScreen;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.text.FontRegistry;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.util.Notification;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class RemotelyClient {

    public static RemotelyClient INSTANCE;
    private final ApplicationHost host;
    private int activeHostIndex = 0;
    public static String os;
    public static ITextRenderer tr;
    private final List<Object> multiTerminalTabs = new CopyOnWriteArrayList<>();
    private int activeMultiTerminalTabIndex = 0;
    private final ScheduledExecutorService connectionScheduler = Executors.newSingleThreadScheduledExecutor();

    public RemotelyClient(ApplicationHost host) {
        this.host = host;
        INSTANCE = this;
    }

    public void initialize() {
        Config.applicationDir = remotelyDir;
        Config.setConfigManager(new RemotelyConfigManager(remotelyDir));
        System.out.println("Remotely mod initialized on the client.");
        loadSnippets();
        try {
            String bgPath = System.getProperty("user.home") + "/AppData/Roaming/Microsoft/Windows/Themes/TranscodedWallpaper";
            restudio.rescreen.config.Config.windowsBackground = javax.imageio.ImageIO.read(new File(bgPath));
        } catch (Exception e) {
            devPrint("Failed to load Windows background: " + e.getMessage());
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::onClientShutdown));
        os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (host instanceof MinecraftApplicationHost) {
            FontRegistry.MONO_FONT = host.getFontIdentifier("remotely", "mono");
        }

        connectAllRemoteHosts();
        connectionScheduler.scheduleAtFixedRate(this::checkRemoteHostConnections, 1, 1, TimeUnit.MINUTES);
    }

    private void onClientShutdown() {
        shutdownAllTerminals();
        shutdownConnections();
    }

    private void connectAllRemoteHosts() {
        Rebase.get().getInstanceManager().getRemoteHosts().forEach(host -> CompletableFuture.runAsync(() -> {
            try {
                host.getSshManager().connect();
            } catch (Exception e) {
                devPrint("Failed to connect to " + host.name + ": " + e.getMessage());
            }
        }));
    }

    private void checkRemoteHostConnections() {
        Rebase.get().getInstanceManager().getRemoteHosts().forEach(host -> {
            if (!host.getSshManager().isSSH() || !host.getSshManager().isSFTPConnected()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        devPrint("Reconnecting to " + host.name);
                        host.getSshManager().connect();
                    } catch (Exception e) {
                        devPrint("Failed to reconnect to " + host.name + ": " + e.getMessage());
                    }
                });
            }
        });
    }

    public void shutdownConnections() {
        connectionScheduler.shutdownNow();
        Rebase.get().getInstanceManager().getRemoteHosts().forEach(host -> host.getSshManager().disconnect());
    }

    public void openMultiTerminal(Object parent) {
        if (multiTerminalTabs.isEmpty()) {
            multiTerminalTabs.add(UUID.randomUUID().toString());
            activeMultiTerminalTabIndex = 0;
        }
        host.setScreen(new RemotelyInstanceDetailsScreen(parent, this));
    }

    public void openInstanceInTerminal(Object parent, Instance instance) {
        boolean found = multiTerminalTabs.stream().anyMatch(o -> o instanceof Instance i && i.getInstanceId().equals(instance.getInstanceId()));
        if (!found) {
            multiTerminalTabs.add(instance);
        }

        for (int i = 0; i < multiTerminalTabs.size(); i++) {
            Object o = multiTerminalTabs.get(i);
            if (o instanceof Instance inst && inst.getInstanceId().equals(instance.getInstanceId())) {
                activeMultiTerminalTabIndex = i;
                break;
            }
        }

        Screen currentScreen = host.getCurrentScreen();
        if (currentScreen instanceof RemotelyInstanceDetailsScreen screen) {
            screen.addInstanceTab(instance);
        } else {
            openMultiTerminal(parent);
        }
    }

    public void openServerManager(Object parent) {
        host.setScreen(new ServerManagerScreen(parent, this));
    }

    public void openFileExplorer(Object parent, Path path) {
        Screen reScreenParent = parent instanceof Screen ? (Screen) parent : null;
        host.setScreen(new FileExplorerScreen(reScreenParent, null, path, Path.of(remotelyDir.toString(), "data"), false));
    }

    public void shutdownAllTerminals() {
        TerminalWidget.shutdownAll();
        saveSnippets();
    }

    public void saveTabIndex(int activeTabIndex) {
        activeHostIndex = activeTabIndex;
    }

    public int getSavedTabIndex() {
        return activeHostIndex;
    }

    public void saveSnippets() {
    }

    public void loadSnippets() {
    }

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
            return  true;
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

    public static boolean isModLoaded(String modId) {
        return false;
    }
}