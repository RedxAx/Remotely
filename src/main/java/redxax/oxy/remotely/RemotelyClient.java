package redxax.oxy.remotely;

import redxax.oxy.remotely.host.ApplicationHost;
import redxax.oxy.remotely.host.MinecraftApplicationHost;
import redxax.oxy.remotely.servers.ServerManagerScreen;
import redxax.oxy.remotely.ui.screens.RemotelyInstanceDetailsScreen;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.text.FontRegistry;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.config.Config;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.ui.core.Screen;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
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
        System.out.println("Remotely mod initialized on the client.");
        loadSnippets();
        try {
            String bgPath = System.getProperty("user.home") + "/AppData/Roaming/Microsoft/Windows/Themes/TranscodedWallpaper";
            restudio.rescreen.config.Config.windowsBackground = javax.imageio.ImageIO.read(new File(bgPath));
        } catch (Exception e) {
            devPrint("Failed to load Windows background: " + e.getMessage());
        }
        loadThemesFromDir();
        migrateRemotelyData();
        Runtime.getRuntime().addShutdownHook(new Thread(this::onClientShutdown));
        os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (host instanceof MinecraftApplicationHost) {
            FontRegistry.MONO_FONT = host.getFontIdentifier("remotely", "mono");
        }
    }

    private void onClientShutdown() {
        shutdownAllTerminals();
    }

    public static void loadThemesFromDir() {
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

    public List<Object> getMultiTerminalTabs() {
        return multiTerminalTabs;
    }

    public int getActiveMultiTerminalTabIndex() {
        return activeMultiTerminalTabIndex;
    }

    public void setActiveMultiTerminalTabIndex(int activeMultiTerminalTabIndex) {
        this.activeMultiTerminalTabIndex = activeMultiTerminalTabIndex;
    }

    public static void migrateRemotelyData() {
        Path oldPath = Paths.get("C:/remotely");
        if (System.getProperty("os.name").toLowerCase().contains("win") && Files.exists(oldPath)) {
            try {
                Path target = Paths.get(System.getProperty("user.home"), "/remotely");
                if (!Files.exists(target)) {
                    Files.createDirectories(target);
                }
                Files.walk(oldPath).forEach(sourcePath -> {
                    Path targetPath = target.resolve(oldPath.relativize(sourcePath));
                    try {
                        if (sourcePath.getFileName().toString().equals("themes")) {
                            Files.delete(sourcePath);
                            return;
                        }
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        devPrint("Failed to migrate Remotely data: " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                devPrint("Failed to migrate Remotely data: " + e.getMessage());
            }
            try {
                Files.walk(oldPath).sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        devPrint("Failed to delete old Remotely data: " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                devPrint("Failed to delete old Remotely data: " + e.getMessage());
            }
        }
    }

    public ApplicationHost getHost() {
        return host;
    }

    public static boolean isModLoaded(String modId) {
        return false;
    }
}