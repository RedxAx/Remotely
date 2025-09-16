package redxax.oxy.remotely;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.screen.Screen;
import redxax.oxy.remotely.adapters.MinecraftTextRendererAdapter;
import redxax.oxy.remotely.adapters.ReScreenWrapper;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import redxax.oxy.remotely.servers.BrowserScreen;
import redxax.oxy.remotely.servers.RemoteHostInfo;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerManagerScreen;
import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import redxax.oxy.remotely.terminal.TerminalWidget;

import net.minecraft.client.MinecraftClient;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.ui.rescreen.ReScreen;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static redxax.oxy.remotely.config.Themes.importThemesFromJar;
import static redxax.oxy.remotely.config.Themes.parseHexColor;
import static redxax.oxy.remotely.servers.BrowserScreen.closeAll;
import static redxax.oxy.remotely.terminal.MultiTerminalScreen.THEMES_DIR;
import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class RemotelyClient {

    public List<ReScreen.TabsManager.Tab> multiTerminalTabs = new ArrayList<>();
    public MultiTerminalScreen multiTerminalScreen;
    private static final Path TERMINAL_LOG_DIR = Paths.get(String.valueOf(remotelyDir), "logs");
    private static final Path SNIPPETS_FILE = Paths.get(String.valueOf(remotelyDir), "data", "snippets.json");
    private static final Path FILE_EDITOR_TABS_FILE = Paths.get(String.valueOf(remotelyDir), "data", "file_editor_tabs.json");
    public static List<MultiTerminalScreen.Theme> themes = new ArrayList<>();
    public static FileExplorerScreen fileExplorer;


    private static final Gson GSON = new Gson();
    public int activeTerminalIndex = 0;
    public boolean showSnippetsPanel = false;
    public static List<CommandSnippet> globalSnippets = new ArrayList<>();
    public static RemotelyClient INSTANCE;
    public final List<ServerInfo> servers = new ArrayList<>();
    private int activeHostIndex = 0;
    private final Map<String, SSHManager> hostSSHManagers = new HashMap<>();
    public static String os;
    public static MinecraftClient mc = MinecraftClient.getInstance();
    public static ITextRenderer tr;

    public void initialize() {
        INSTANCE = this;
        RemotelyEntry remotelyEntry = new RemotelyEntry();
        remotelyEntry.init();
        System.out.println("Remotely mod initialized on the client.");
        loadSnippets();
        try {
            String bgPath = System.getProperty("user.home") + "/AppData/Roaming/Microsoft/Windows/Themes/TranscodedWallpaper";
            restudio.rescreen.config.Config.windowsBackground = javax.imageio.ImageIO.read(new File(bgPath));
        } catch (Exception e) {
            devPrint("Failed to load Windows background: " + e.getMessage());
        }
        importThemesFromJar();
        loadThemesFromDir();
        migrateRemotelyData();
        Runtime.getRuntime().addShutdownHook(new Thread(this::onClientShutdown));
        os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    }

    private void onClientShutdown() {
        shutdownAllTerminals();
        hostSSHManagers.values().forEach(SSHManager::shutdown);
        hostSSHManagers.clear();
    }

    public static void loadThemesFromDir() {
        themes.clear();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(THEMES_DIR, "*.yml")) {
            for (Path file : stream) {
                MultiTerminalScreen.Theme theme = parseThemeFile(file);
                if (theme != null) {
                    themes.add(theme);
                }
            }
        } catch (IOException ignored) {}
    }

    public static MultiTerminalScreen.Theme parseThemeFile(Path file) {
        MultiTerminalScreen.Theme theme = new MultiTerminalScreen.Theme();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(":", 2);
                if (parts.length < 2) continue;
                String key = parts[0].trim();
                String value = parts[1].trim().replace("\"", "");
                if (key.equals("name")) {
                    theme.name = value;
                } else {
                    if (value.startsWith("#")) {
                        try {
                            theme.colors.put(key, parseHexColor(value));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            if (theme.name == null || theme.name.isEmpty()) {
                theme.name = file.getFileName().toString().replace(".yml", "");
            }
            return theme;
        } catch (IOException ignored) {}
        return null;
    }

    public void openMultiTerminal(Screen parent) {
        restudio.rescreen.ui.core.Screen lib = new MultiTerminalScreen(parent, this);
        mc.setScreen(new ReScreenWrapper(lib));
    }

    public void openServerManager(Screen parent) {
        mc.setScreen(new ReScreenWrapper(new ServerManagerScreen(parent, this, servers)));
    }

    public void openFileExplorer(Screen parent) {
        mc.setScreen(new ReScreenWrapper(new FileExplorerScreen(parent, new ServerInfo(remotelyDir.toString()))));
    }

    public void openFileExplorer(restudio.rescreen.ui.core.Screen parent, Path path) {
        if (fileExplorer != null) {
            fileExplorer.close();
        }
        fileExplorer = new FileExplorerScreen(parent, path);
        mc.setScreen(new ReScreenWrapper(fileExplorer));
    }

    public void openBrowser(Screen parent) {
        if (BrowserScreen.checkIfMcefExist()) {
            mc.setScreen(new ReScreenWrapper(new BrowserScreen(parent, "https://www.google.com")));
        }
    }

    public void shutdownAllTerminals() {
        for (ReScreen.TabsManager.Tab tab : multiTerminalTabs) {
            if (tab.getData() instanceof TerminalWidget widget) {
                widget.shutdown();
            }
        }
        multiTerminalTabs.clear();

        saveSnippets();
        try {
            if (Files.exists(TERMINAL_LOG_DIR) && Files.isDirectory(TERMINAL_LOG_DIR)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(TERMINAL_LOG_DIR)) {
                    for (Path entry : stream) {
                        Files.deleteIfExists(entry);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to clear terminal log files.");
        }
        closeAll();
    }

    public void onMultiTerminalScreenClosed() {
        multiTerminalScreen = null;
    }

    public void saveTabIndex(int activeTabIndex) {
        activeHostIndex = activeTabIndex;
    }

    public int getSavedTabIndex() {
        return activeHostIndex;
    }

    public void saveFileEditorTabs(List<Path> tabs) {
        try {
            if (!Files.exists(FILE_EDITOR_TABS_FILE.getParent())) {
                Files.createDirectories(FILE_EDITOR_TABS_FILE.getParent());
            }
            List<String> tabPaths = new ArrayList<>();
            for (Path p : tabs) {
                tabPaths.add(p.toString());
            }
            String json = GSON.toJson(tabPaths);
            Files.write(FILE_EDITOR_TABS_FILE, json.getBytes());
        } catch (IOException e) {
            System.out.println("Failed to save File Editor tabs: " + e.getMessage());
        }
    }

    public List<Path> loadFileEditorTabs() {
        List<Path> tabs = new ArrayList<>();
        if (Files.exists(FILE_EDITOR_TABS_FILE)) {
            try {
                String json = new String(Files.readAllBytes(FILE_EDITOR_TABS_FILE));
                List<String> tabPaths = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
                for (String pathStr : tabPaths) {
                    tabs.add(Paths.get(pathStr));
                }
            } catch (IOException e) {
                System.out.println("Failed to load File Editor tabs: " + e.getMessage());
            }
        }
        return tabs;
    }

    public static class CommandSnippet {
        public String name;
        public String commands;
        public String shortcut;
        public CommandSnippet(String name, String commands, String shortcut) {
            this.name = name;
            this.commands = commands;
            this.shortcut = shortcut;
        }
    }

    public void saveSnippets() {
        try {
            if (!Files.exists(SNIPPETS_FILE.getParent())) {
                Files.createDirectories(SNIPPETS_FILE.getParent());
            }
            String json = GSON.toJson(globalSnippets);
            Files.write(SNIPPETS_FILE, json.getBytes());
        } catch (IOException e) {
            System.out.println("Failed to save snippets: " + e.getMessage());
        }
    }

    public void loadSnippets() {
        if (Files.exists(SNIPPETS_FILE)) {
            try {
                String json = new String(Files.readAllBytes(SNIPPETS_FILE));
                globalSnippets = GSON.fromJson(json, new TypeToken<List<CommandSnippet>>(){}.getType());
                if (globalSnippets == null) globalSnippets = new ArrayList<>();
            } catch (IOException e) {
                System.out.println("Failed to load snippets: " + e.getMessage());
            }
        }
    }

    public SSHManager getSSHManagerForHost(RemoteHostInfo host) {
        if (host == null) {
            devPrint("Cannot get SSH manager for null host");
            return null;
        }

        String key = host.getIp() + ":" + host.getPort() + ":" + host.getUser();

        synchronized (hostSSHManagers) {
            if (hostSSHManagers.containsKey(key)) {
                SSHManager existingManager = hostSSHManagers.get(key);
                if (existingManager.isSSH()) {
                    return existingManager;
                } else {
                    existingManager.shutdown();
                    hostSSHManagers.remove(key);
                }
            }

            SSHManager manager = new SSHManager(host);
            manager.connectToRemoteHost(host.getUser(), host.getIp(), host.getPort(), host.getPassword());
            hostSSHManagers.put(key, manager);
            return manager;
        }
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