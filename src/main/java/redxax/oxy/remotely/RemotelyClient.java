package redxax.oxy.remotely;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import redxax.oxy.remotely.adapters.MinecraftTextRendererAdapter;
import redxax.oxy.remotely.adapters.ReScreenWrapper;
import redxax.oxy.remotely.servers.BrowserScreen;
import redxax.oxy.remotely.servers.ServerManagerScreen;
import redxax.oxy.remotely.ui.screens.RemotelyInstanceDetailsScreen;
import restudio.rebase.ui.screens.explorer.FileExplorerScreen;
import restudio.rebase.ui.text.FontRegistry;
import restudio.rebase.ui.widgets.TerminalWidget;

import net.minecraft.client.MinecraftClient;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class RemotelyClient {

    public static RemotelyClient INSTANCE;
    private int activeHostIndex = 0;
    public static String os;
    public static MinecraftClient mc = MinecraftClient.getInstance();
    public static ITextRenderer tr;

    public void initialize() {
        INSTANCE = this;
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

        FontRegistry.MONO_FONT = Identifier.of("remotely", "mono");
    }

    private void onClientShutdown() {
        shutdownAllTerminals();
        BrowserScreen.closeAll();
    }

    public static void loadThemesFromDir() {
    }

    public void openMultiTerminal(Screen parent) {
        mc.setScreen(new ReScreenWrapper(new RemotelyInstanceDetailsScreen(parent, null)));
    }

    public void openMultiTerminal(restudio.rescreen.ui.core.Screen parent) {
        mc.setScreen(new ReScreenWrapper(new RemotelyInstanceDetailsScreen(parent, null)));
    }

    public void openServerManager(Screen parent) {
        mc.setScreen(new ReScreenWrapper(new ServerManagerScreen(parent, this)));
    }

    public void openFileExplorer(restudio.rescreen.ui.core.Screen parent, Path path) {
        mc.setScreen(new ReScreenWrapper(new FileExplorerScreen(parent, null, path, Path.of(remotelyDir.toString(), "data"), false)));
    }

    public void openBrowser(Screen parent) {
        if (BrowserScreen.checkIfMcefExist()) {
            mc.setScreen(new ReScreenWrapper(new BrowserScreen(parent, "https://www.google.com")));
        }
    }

    public void openBrowser(restudio.rescreen.ui.core.Screen parent) {
        if (BrowserScreen.checkIfMcefExist()) {
            mc.setScreen(new ReScreenWrapper(new BrowserScreen(parent, "https://www.google.com")));
        }
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

    public void ensureTextRenderer() {
        restudio.rescreen.render.TextRenderer.setTextRendererAdapter(new MinecraftTextRendererAdapter());
        tr = restudio.rescreen.render.TextRenderer.getTr();
    }

    public static boolean isModLoaded(String modId) {
        return false;
    }
}