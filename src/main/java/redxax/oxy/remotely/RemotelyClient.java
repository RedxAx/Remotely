package redxax.oxy.remotely;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.ui.server.ServerManagerScreen;
import redxax.oxy.remotely.ui.server.ServerDetailsScreen;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.text.FontRegistry;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

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
        System.out.println("Remotely mod initialized on the client.");
        loadSnippets();

        os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            Thread t = new Thread(() -> {
                try {
                    String bgPath = System.getProperty("user.home") + "/AppData/Roaming/Microsoft/Windows/Themes/TranscodedWallpaper";
                    File file = new File(bgPath);
                    if (file.exists() && file.canRead()) {
                        restudio.rescreen.config.Config.windowsBackground = javax.imageio.ImageIO.read(file);
                    }
                } catch (Exception e) {
                    devPrint("Failed to load Windows background: " + e.getMessage());
                }
            }, "Remotely-Wallpaper-Loader");
            t.setDaemon(true);
            t.start();
        }

        FontRegistry.MONO_FONT = host.getFontIdentifier("remotely", "mono");
    }

    public void openMultiTerminal(Object parent) {
        if (multiTerminalTabs.isEmpty()) {
            multiTerminalTabs.add(UUID.randomUUID().toString());
            activeMultiTerminalTabIndex = 0;
        }
        host.setScreen(new ServerDetailsScreen(parent, this));
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
        if (currentScreen instanceof ServerDetailsScreen screen) {
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

}
