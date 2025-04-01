package redxax.oxy.common;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.servers.RemoteHostInfo;
import redxax.oxy.common.servers.ServerInfo;
import redxax.oxy.common.servers.ServerManagerScreen;
import redxax.oxy.common.servers.SettingsScreen;
import redxax.oxy.common.terminal.MultiTerminalScreen;
import redxax.oxy.common.terminal.TerminalInstance;
import redxax.oxy.common.SSHManager;

import javax.imageio.ImageIO;
import java.io.BufferedReader;
import java.io.File;
import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import static redxax.oxy.common.config.Themes.parseHexColor;
import static redxax.oxy.common.servers.BrowserScreen.closeAll;
import static redxax.oxy.common.terminal.MultiTerminalScreen.THEMES_DIR;
import static redxax.oxy.common.util.DevUtil.devPrint;

public class RemotelyClient implements ClientModInitializer {

    public ArrayList<TerminalInstance> multiTerminals;
    public ArrayList<String> multiTabNames;
    private KeyBinding openTerminalKeyBinding;
    private KeyBinding openServerManagerKeyBinding;
    public MultiTerminalScreen multiTerminalScreen;
    private ServerManagerScreen serverManagerScreen;
    private static final Path TERMINAL_LOG_DIR = Paths.get("C:/remotely/data/remotely", "logs");
    private static final Path SNIPPETS_FILE = Paths.get("C:/remotely/data/snippets.json");
    private static final Path FILE_EDITOR_TABS_FILE = Paths.get("C:/remotely/data/file_editor_tabs.dat");
    public static List<MultiTerminalScreen.Theme> themes = new ArrayList<>();

    private static final Gson GSON = new Gson();
    public List<TerminalInstance> terminals = new ArrayList<>();
    public List<String> tabNames = new ArrayList<>();
    public int activeTerminalIndex = 0;
    public float scale = 1.0f;
    public int snippetPanelWidth = 150;
    public boolean showSnippetsPanel = false;
    public static List<CommandSnippet> globalSnippets = new ArrayList<>();
    public static RemotelyClient INSTANCE;
    public final List<ServerInfo> servers = new ArrayList<>();
    private int activeHostIndex = 0;
    private Map<String, SSHManager> hostSSHManagers = new HashMap<>();

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        System.out.println("Remotely mod initialized on the client.");
        loadSnippets();
        multiTerminals = new ArrayList<>();
        multiTabNames = new ArrayList<>();
        openTerminalKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Open Terminal",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "Remotely"
        ));
        openServerManagerKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Open Server Manager",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "Remotely"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client != null && client.player != null) {
                if (openTerminalKeyBinding.wasPressed()) {
                    openMultiTerminalGUI(client);
                }
                if (openServerManagerKeyBinding.wasPressed()) {
                    client.setScreen(new ServerManagerScreen(client, RemotelyClient.INSTANCE, RemotelyClient.INSTANCE.servers));
                }
            }
        });
        try {
            String bgPath = System.getProperty("user.home") + "/AppData/Roaming/Microsoft/Windows/Themes/TranscodedWallpaper";
            Config.windowsBackground = ImageIO.read(new File(bgPath));
        } catch (Exception e) {
            devPrint("Failed to load Windows background: " + e.getMessage());
        }
        loadThemesFromDir();
        SettingsScreen.loadClientConfigFromJson();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAllTerminals));
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
                switch (key) {
                    case "name" -> theme.name = value;
                    default -> {
                        if (value.startsWith("#")) {
                            try {
                                theme.colors.put(key, parseHexColor(value));
                            } catch (Exception ignored) {}
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

    public void openMultiTerminalGUI(MinecraftClient client) {
        if (multiTerminalScreen == null || !client.isWindowFocused()) {
            if (multiTerminals.isEmpty() && terminals.isEmpty()) {
                loadSavedTerminals();
            }
            if (!multiTerminals.isEmpty()) {
                terminals.clear();
                terminals.addAll(multiTerminals);
                tabNames.clear();
                tabNames.addAll(multiTabNames);
            }
            multiTerminalScreen = new MultiTerminalScreen(client, null, this, terminals, tabNames);
            client.setScreen(multiTerminalScreen);
        } else {
            if (multiTerminals.isEmpty() && terminals.isEmpty()) {
                loadSavedTerminals();
            }
            if (!multiTerminals.isEmpty()) {
                terminals.clear();
                terminals.addAll(multiTerminals);
                tabNames.clear();
                tabNames.addAll(multiTabNames);
            }
            multiTerminalScreen = new MultiTerminalScreen(client, null, this, terminals, tabNames);
            client.setScreen(multiTerminalScreen);
        }
    }

    private void loadSavedTerminals() {
        if (Files.exists(TERMINAL_LOG_DIR) && Files.isDirectory(TERMINAL_LOG_DIR) && terminals.isEmpty()) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(TERMINAL_LOG_DIR, "*.log")) {
                for (Path entry : stream) {
                    String fileName = entry.getFileName().toString();
                    String tabName = fileName.substring(0, fileName.length() - 4);
                    TerminalInstance terminal = new TerminalInstance(MinecraftClient.getInstance(), multiTerminalScreen, UUID.randomUUID());
                    terminal.loadTerminalOutput(entry);
                    terminals.add(terminal);
                    tabNames.add(tabName);
                }
                if (!terminals.isEmpty() && multiTerminalScreen != null) {
                    multiTerminalScreen.activeTerminalIndex = activeTerminalIndex;
                }
            } catch (IOException e) {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(Text.literal("Failed to load saved terminals."), false);
                }
            }
        }
    }

    public void shutdownAllTerminals() {
        for (TerminalInstance terminal : terminals) {
            terminal.shutdown();
        }
        terminals.clear();
        tabNames.clear();
        if (multiTerminalScreen != null) {
            multiTerminalScreen.shutdownAllTerminals();
            multiTerminalScreen = null;
        }
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
        String key = host.getIp() + ":" + host.getPort() + ":" + host.getUser();
        if (hostSSHManagers.containsKey(key)) {
            return hostSSHManagers.get(key);
        } else {
            SSHManager manager = new SSHManager(host);
            hostSSHManagers.put(key, manager);
            return manager;
        }
    }
}
