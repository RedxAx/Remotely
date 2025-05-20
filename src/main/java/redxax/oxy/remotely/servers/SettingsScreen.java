package redxax.oxy.remotely.servers;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.config.Themes;
import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import redxax.oxy.remotely.util.ImageUtil;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Notification.*;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import redxax.oxy.remotely.util.Sound;

import static redxax.oxy.remotely.RemotelyClient.*;
import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.config.Themes.importThemesFromJar;
import static redxax.oxy.remotely.servers.ServerFactory.notification;
import static redxax.oxy.remotely.servers.SettingsScreen.ServerSettingType.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.Sound.enableSFX;
import static redxax.oxy.remotely.util.Sound.soundVolume;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class SettingsScreen extends Screen {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private final Screen parent;
    private final String mode;
    private final String settingsRoot;
    private final boolean editServerMode;
    private static boolean configMode = false;
    private ServerInfo serverInfo;
    private int currentTab;
    public static List<Settings> settings = new ArrayList<>();
    private List<String> tabs;
    private final Map<Settings, Float> textInputScrollOffsets = new HashMap<>();
    private final Map<Settings, Float> textInputTargetScrollOffsets = new HashMap<>();
    private final Map<Settings, Integer> textSelectionStart = new HashMap<>();
    private final Map<Settings, Integer> textSelectionEnd = new HashMap<>();
    private int selectedDropDown = -1;
    private float currentSettingsScroll = 0;
    private float targetSettingsScroll = 0;
    private final int rowHeight = 30;
    private Settings draggedSlider = null;
    private ImageUtil.IconWithTooltip closeIcon, createIcon;
    public enum ServerSettingType {TOGGLE, SLIDER, SCROLL_SWITCH, TAB_SWITCH, TEXT}
    static List<String> themeOptions = new ArrayList<>();

    public SettingsScreen(String mode, Screen parent, String settingsRoot, List<Settings> customSettings) {
        this(mode, parent, settingsRoot, customSettings, null);
    }

    public SettingsScreen(String mode, Screen parent, String settingsRoot, List<Settings> customSettings, ServerInfo serverInfo) {
        super(Text.literal("Server Settings"));
        this.parent = parent;
        this.mode = mode;
        this.settingsRoot = settingsRoot;
        this.editServerMode = mode.equalsIgnoreCase("editServer");
        configMode = mode.equalsIgnoreCase("config");
        this.serverInfo = serverInfo;
        settings = new ArrayList<>();
        tabs = new ArrayList<>();
        if (customSettings != null && !customSettings.isEmpty()) {
            settings.addAll(customSettings);
        } else {
           settings.add(new Settings.Builder("Error While Loading Settings", "No Settings Found.", "404", "none", TEXT, "Please Try Again.").build());
        }
        if (editServerMode) {
            if (serverInfo.isRemote) {
                loadSettingsRemote(serverInfo);
            } else {
                loadSettingsFromFiles();
            }
            for (Settings s : settings) {
                if (s.key.equalsIgnoreCase("server-name")) {
                    s.value = serverInfo.name;
                }
                if (s.key.equalsIgnoreCase("server-type")) {
                    s.value = serverInfo.type;
                }
                if (s.key.equalsIgnoreCase("server-version")) {
                    s.value = serverInfo.version;
                }
            }
        } else if (configMode) {
            loadClientConfiguration();
        }
        initTextInputOffsets();
        recalcTabs();
        originalMCScale = mc.getWindow().getScaleFactor();
        targetScaleFactor = globalScaleFactor;
        mc.getWindow().setScaleFactor(globalScaleFactor);
    }

    public static void loadClientConfiguration() {
        importThemesFromJar();
        loadThemesFromDir();
        themeOptions.clear();
        if (INSTANCE != null && themes != null && !themes.isEmpty()) {
            for (MultiTerminalScreen.Theme theme : themes) {
                themeOptions.add(theme.name);
            }
        } else {
            themeOptions.add("Default");
        }
        settings.clear();
        settings.add(new Settings.Builder("Theme", "Select and apply a theme on startup.", "Appearance", "theme", SCROLL_SWITCH, getCurrentTheme()).options(themeOptions).build());
        settings.add(new Settings.Builder("Menus Buttons Style", "Choose The Style of The Buttons In The Menus.", "Appearance", "mainMenuButtonsStyle", TAB_SWITCH, (mainMenuStyle.equals("Vanilla") ? "Vanilla" : mainMenuStyle.equals("Minimal") ? "Minimal" : mainMenuStyle.equals("Normal") ? "Normal" : "Disable")).options(Arrays.asList("Vanilla", "Minimal", "Normal", "Disable")).build());
        settings.add(new Settings.Builder("Custom Mouse Cursor", "Remotely's 2 Cute Little Squares.", "Appearance", "customMouse", TOGGLE, String.valueOf(customMouse)).build());
        settings.add(new Settings.Builder("Mouse Size", "Set The Size of The Custom Mouse Cursor.", "Appearance", "mouseSize", SLIDER, String.valueOf(mouseSize)).range(1, 100).dependency("customMouse", "true").build());
        settings.add(new Settings.Builder("Mouse Tail Size", "Set The Size of The Custom Mouse Tail.", "Appearance", "tailSize", SLIDER, String.valueOf(tailSize)).range(1, 100).dependency("customMouse", "true").build());
        settings.add(new Settings.Builder("Mouse Tail Speed", "Set The Speed of The Mouse Tail Following The Cursor.", "Appearance", "tailFollowSpeed", SLIDER, String.valueOf(tailFollowSpeed)).range(1, 100).dependency("customMouse", "true").build());
        settings.add(new Settings.Builder("Redesign Minecraft Buttons", "Enable The New Button Design.", "Appearance", "redesignMainMenu", TOGGLE, String.valueOf(redesignMainMenu)).build());
        settings.add(new Settings.Builder("Show Minecraft Background", "Display The Minecraft Panorama As The Background.", "Appearance", "background", TOGGLE, String.valueOf(background)).build());
        settings.add(new Settings.Builder("Show Wallpaper", "Display Your PC Wallpaper As The Background.", "Appearance", "wallpaper", TOGGLE, String.valueOf(wallpaper)).build());
        settings.add(new Settings.Builder("Text Shadow", "Enable Text Background / Shadow Effect.", "Appearance", "shadow", TOGGLE, String.valueOf(shadow)).build());
        settings.add(new Settings.Builder("Scroll Animation Speed", "Set The Global Speed of The Scrolling Animations.", "Appearance", "globalScrollSpeed", SLIDER, String.valueOf(globalScrollSpeed)).range(0, 60).build());
        settings.add(new Settings.Builder("Movement Animation Speed", "Set The Global Speed of The Movement Animations.", "Appearance", "globalMovementSpeed", SLIDER, String.valueOf(globalMovementSpeed)).range(0, 60).build());
        settings.add(new Settings.Builder("Scale Animation Speed", "Set The Global Speed of The Scale Animations.", "Appearance", "scaleAnimationSpeed", SLIDER, String.valueOf(scaleAnimationSpeed)).range(0, 60).build());
        settings.add(new Settings.Builder("Expand Animation Speed", "Set The Global Speed of The Expand/Shrink Animations.", "Appearance", "globalExpandSpeed", SLIDER, String.valueOf(globalExpandSpeed).replace("f", "")).range(0, 30).build());

        settings.add(new Settings.Builder("Sound Volume", "Set The Volume of The Sounds.", "Sounds", "soundVolume", SLIDER, String.valueOf(soundVolume)).range(0, 200).build());
        settings.add(new Settings.Builder("Pitch Variation", "Set The Variation of The Sound Pitch.", "Sounds", "pitchVariation", SLIDER, String.valueOf(Sound.pitchVariation)).range(0, 200).build());
        settings.add(new Settings.Builder("Sound Effects", "Toggle Sound Effects.", "Sounds", "soundEffects", TOGGLE, String.valueOf(enableSFX)).build());
        try {
            Field[] fields = Sound.class.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()) && field.getType() == boolean.class && field.getName().startsWith("sound")) {
                    String fieldName = field.getName();
                    String displayName = formatSoundFieldName(fieldName);
                    String description = "Toggle " + displayName + " Sound Effect.";
                    boolean currentValue = field.getBoolean(null);
                    settings.add(new Settings.Builder(displayName, description, "Sounds", fieldName, TOGGLE, String.valueOf(currentValue)).build());
                }
            }
        } catch (IllegalAccessException e) {
            devPrint("Error accessing Sound fields for settings: " + e.getMessage());
        }

        settings.add(new Settings.Builder("Scan For Servers", "Scan For Servers In The Default Remotely Directory.", "Servers", "scanServers", TOGGLE, String.valueOf(scanServers)).build());
        settings.add(new Settings.Builder("Use Custom Reverse Proxy", "Replace The Default Server With Your Own.", "Servers", "customReverseProxy", TOGGLE, String.valueOf(customReverseProxy)).build());
        settings.add(new Settings.Builder("Reverse Proxy Host", "Enter The Host To Your Server (e.g. `RedxAx.net`)", "Servers", "proxyHost", TEXT, String.valueOf(proxyHost)).build());
        settings.add(new Settings.Builder("Reverse Proxy User", "Enter The User Of Your Proxy Server (e.g. `tunnel`)", "Servers", "proxyUser", TEXT, String.valueOf(proxyUser)).build());

        settings.add(new Settings.Builder("Developer Mode", "Enable Developer Mode.", "Development", "isDev", TOGGLE, String.valueOf(isDev)).build());
        settings.add(new Settings.Builder("Enable Debug Tools", "Enable Visual Tools For Debugging.", "Development", "enableDebugTools", TOGGLE, String.valueOf(enableDebugTools)).build());
    }

    public static void defineSettings() {
        settings.clear();
        settings.add(new Settings.Builder("Server Name", "The name of your server.", "General", "server-name", TEXT, "My Server").build());
        settings.add(new Settings.Builder("Game Mode", "Select the default game mode for players.", "General", "gamemode", TAB_SWITCH, "Survival").file("server.properties").options(Arrays.asList("Survival", "Creative", "Adventure")).build());
        settings.add(new Settings.Builder("Difficulty", "Set the difficulty level of the server.", "General", "difficulty", TAB_SWITCH, "Normal").file("server.properties").options(Arrays.asList("Peaceful", "Easy", "Normal", "Hard")).build());
        settings.add(new Settings.Builder("End User License Agreement", "Do You Agree To Minecraft's EULA?", "General", "eula", TOGGLE, "true").file("eula.txt").build());
        settings.add(new Settings.Builder("PvP", "Toggle player vs player combat.", "General", "pvp", TOGGLE, "true").file("server.properties").build());
        settings.add(new Settings.Builder("Hardcore", "Toggle hardcore mode (one life).", "General", "hardcore", TOGGLE, "false").file("server.properties").build());
        settings.add(new Settings.Builder("Server Type", "Choose the server software type.", "General", "server-type", SCROLL_SWITCH, "Paper").options(Arrays.asList("Paper", "Leaf", "Vanilla", "Fabric", "Neoforge", "Forge", "Quilt", "Velocity", "Waterfall")).build());
        settings.add(new Settings.Builder("Server Version", "Specify the Minecraft server version to run.", "General", "server-version", TEXT, mc.getGameVersion()).build());
        settings.add(new Settings.Builder("Max Players", "Max online players limit.", "Advanced", "max-players", SLIDER, "20").file("server.properties").range(1, 200).build());
        settings.add(new Settings.Builder("MOTD", "Description for the server list.", "Advanced", "motd", TEXT, mc.getSession().getUsername() + "'s Server").file("server.properties").build());
        settings.add(new Settings.Builder("Seed", "Enter a specific seed (optional).", "Advanced", "level-seed", TEXT, "").file("server.properties").build());
        settings.add(new Settings.Builder("Spawn Protection", "Set the radius of spawn protection (set 0 to disable).", "Advanced", "spawn-protection", SLIDER, "16").file("server.properties").range(0, 32).build());
        settings.add(new Settings.Builder("Max Build Height", "Set the maximum height players can build to.", "Advanced", "max-build-height", SLIDER, "320").file("server.properties").range(0, 2048).build());
        settings.add(new Settings.Builder("Generate Structures", "Toggle whether structures are generated in the world.", "Advanced", "generate-structures", TOGGLE, "true").file("server.properties").build());
        settings.add(new Settings.Builder("Port", "Set the port number on which the server will run.", "Advanced", "server-port", TEXT, "25565").file("server.properties").build());
        settings.add(new Settings.Builder("Online Mode", "Authenticate with Minecraft (Secure).", "Advanced", "online-mode", TOGGLE, "true").file("server.properties").build());
        settings.add(new Settings.Builder("Whitelist", "Enable or disable the server whitelist.", "Advanced", "white-list", TOGGLE, "false").file("server.properties").build());
        settings.add(new Settings.Builder("Hide Online Players", "Hide online players from the server list.", "Advanced", "hide-online-players", TOGGLE, "false").file("server.properties").build());
        settings.add(new Settings.Builder("Allow Nether", "Toggle whether the Nether dimension is accessible.", "Advanced", "allow-nether", TOGGLE, "true").file("server.properties").build());
        settings.add(new Settings.Builder("Allow End", "Toggle whether the End dimension is accessible.", "Advanced", "settings.allow-end", TOGGLE, "true").file("bukkit.yml").dependency("server-type", "Paper", "Leaf", "Spigot", "Bukkit", "Purpur").build());
        settings.add(new Settings.Builder("Use Custom Java", "Use a custom Java installation (Not recommended).", "Advanced", "usecustomjava", TOGGLE, "false").build());
        settings.add(new Settings.Builder("Java Version", "Specify the Java version to use.", "Advanced", "launcher.java_version", TEXT, "").dependency("usecustomjava", "true").build());
        settings.add(new Settings.Builder("View Distance", "Adjust the number of chunks visible to players.", "Performance", "view-distance", SLIDER, "8").file("server.properties").range(1, 64).build());
        settings.add(new Settings.Builder("Simulation Distance", "Set the simulation distance (server tick radius).", "Performance", "simulation-distance", SLIDER, "8").file("server.properties").range(1, 64).build());
        settings.add(new Settings.Builder("Memory", "Set the maximum memory allocation for the server.", "Performance", "memory", TEXT, "4G").build());
        settings.add(new Settings.Builder("Aikars Flags", "Custom flags that highly optimizes server performance.", "Performance", "aikars_flags", TOGGLE, "true").build());
    }


    private static String formatSoundFieldName(String fieldName) {
        if (fieldName.startsWith("sound")) {
            String namePart = fieldName.substring("sound".length()).toLowerCase();
            StringBuilder formattedName = new StringBuilder();
            formattedName.append(Character.toUpperCase(namePart.charAt(0)));
            for (int i = 1; i < namePart.length(); i++) formattedName.append(namePart.charAt(i));
            return formattedName.toString();
        }
        return fieldName;
    }

    private void initTextInputOffsets() {
        for (Settings s : settings) {
            if (s.type == TEXT) {
                textInputScrollOffsets.put(s, 0f);
                textInputTargetScrollOffsets.put(s, 0f);
            }
        }
    }

    private void loadSettingsFromFiles() {
        for (Settings s : settings) {
            if (!s.file.equals("none")) {
                try {
                    Path filePath = Paths.get(settingsRoot, s.file);
                    if (Files.exists(filePath)) {
                        List<String> lines = Files.readAllLines(filePath);
                        for (String line : lines) {
                            if (line.startsWith(s.key + "=")) {
                                String val = line.substring((s.key + "=").length()).trim();
                                if (!val.isEmpty()) {
                                    s.value = val;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    devPrint("Error loading setting " + s.key + ": " + e.getMessage());
                }
            }
        }
    }

    private void loadSettingsRemote(ServerInfo serverInfo) {
        if (serverInfo == null || !serverInfo.isRemote || serverInfo.remoteHost == null) return;
        try {
            RemoteHostInfo rh = serverInfo.remoteHost;
            SSHManager ssh = INSTANCE.getSSHManagerForHost(serverInfo.remoteHost);
            if (!ssh.isSFTPConnected()) {
                ssh.connectToRemoteHost(rh.getUser(), rh.getIp(), rh.getPort(), rh.getPassword());
                while (!ssh.isSFTPConnected()) {
                    Thread.sleep(100);
                }
            }
            for (Settings s : settings) {
                if (!s.file.equals("none")) {
                    String remoteFilePath = serverInfo.path + "/" + s.file;
                    String fileContent = ssh.readRemoteFile(remoteFilePath);
                    if (fileContent != null) {
                        String[] lines = fileContent.split("\n");
                        for (String line : lines) {
                            if (line.startsWith(s.key + "=")) {
                                String val = line.substring((s.key + "=").length()).trim();
                                if (!val.isEmpty()) {
                                    s.value = val;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            devPrint("Error loading remote setting: " + e.getMessage());
        }
    }

    private void recalcTabs() {
        Set<String> tabSet = new LinkedHashSet<>();
        for (Settings s : settings) {
            if (dependencySatisfied(s)) {
                tabSet.add(s.tab);
            }
        }
        tabs = new ArrayList<>(tabSet);
        if (currentTab >= tabs.size()) {
            currentTab = 0;
        }
    }

    private boolean dependencySatisfied(Settings s) {
        if (s.dependencies == null || s.dependencies.isEmpty()) return true;
        for (Map.Entry<String, List<String>> entry : s.dependencies.entrySet()) {
            String depKey = entry.getKey();
            List<String> depValues = entry.getValue();
            boolean found = false;
            for (Settings setting : settings) {
                if (setting.key.equals(depKey) && depValues.contains(setting.value)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static void updateClientConfigSetting(String key, String value) {
        devPrint("Updated client config setting: " + key + " = " + value);
        boolean updated = false;
        if (key.startsWith("sound")) {
            try {
                Field field = Sound.class.getDeclaredField(key);
                if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()) && field.getType() == boolean.class) {
                    field.setBoolean(null, Boolean.parseBoolean(value));
                    updated = true;
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                devPrint("Error updating sound setting '" + key + "': " + e.getMessage());
            }
        }
        if (!updated) {
            switch (key) {
                case "background" -> background = Boolean.parseBoolean(value);
                case "customMouse" -> customMouse = Boolean.parseBoolean(value);
                case "redesignMainMenu" -> redesignMainMenu = Boolean.parseBoolean(value);
                case "wallpaper" -> wallpaper = Boolean.parseBoolean(value);
                case "shadow" -> shadow = Boolean.parseBoolean(value);
                case "mainMenuButtonsStyle" -> mainMenuStyle = value;
                case "globalScrollSpeed" -> globalScrollSpeed = Math.round(Float.parseFloat(value));
                case "globalMovementSpeed" -> globalMovementSpeed = Math.round(Float.parseFloat(value));
                case "globalExpandSpeed" -> globalExpandSpeed = Math.round(Float.parseFloat(value));
                case "scaleAnimationSpeed" -> scaleAnimationSpeed = Math.round(Float.parseFloat(value));
                case  "scanServers" -> scanServers = Boolean.parseBoolean(value);
                case "customReverseProxy" -> customReverseProxy = Boolean.parseBoolean(value);
                case "proxyHost" -> proxyHost = value;
                case "proxyUser" -> proxyUser = value;
                case "soundEffects" -> enableSFX = Boolean.parseBoolean(value);
                case "soundVolume" -> soundVolume = Integer.parseInt(value);
                case "pitchVariation" -> Sound.pitchVariation = Integer.parseInt(value);
                case "isDev" -> isDev = Boolean.parseBoolean(value);
                case "enableDebugTools" -> enableDebugTools = Boolean.parseBoolean(value);
                case "mouseSize" -> mouseSize = Integer.parseInt(value);
                case "tailSize" -> tailSize = Integer.parseInt(value);
                case "tailFollowSpeed" -> tailFollowSpeed = Integer.parseInt(value);
                case "theme" -> {
                    if (INSTANCE != null) {
                        for (MultiTerminalScreen.Theme theme : themes) {
                            if (theme.name.equals(value)) {
                                Themes.applyTheme(theme);
                                break;
                            }
                        }
                    }
                }
                default -> devPrint("Unrecognized client config key: " + key);
            }
        }
        saveClientConfigToJson();
    }

    public static void loadClientConfigFromJson() {
        try {
            Path configDir = Paths.get(String.valueOf(remotelyDir), "data");
            Path configFile = configDir.resolve("config.json");
            if (Files.exists(configFile)) {
                String jsonContent = new String(Files.readAllBytes(configFile));
                Pattern pattern = Pattern.compile("\"settings\"\\s*:\\s*\\{([^}]*)}");
                Matcher matcher = pattern.matcher(jsonContent);
                if (matcher.find()) {
                    String innerContent = matcher.group(1);
                    String[] pairs = innerContent.split(",");
                    for (String pair : pairs) {
                        String[] keyValue = pair.split(":");
                        if (keyValue.length == 2) {
                            String key = keyValue[0].trim();
                            if (key.startsWith("\"") && key.endsWith("\"")) {
                                key = key.substring(1, key.length() - 1);
                            }
                            String value = keyValue[1].trim();
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            updateClientConfigSetting(key, value);
                        }
                    }
                    devPrint("Loaded client config from: " + configFile);
                } else {
                    jsonContent = jsonContent.trim();
                    if (jsonContent.startsWith("{") && jsonContent.endsWith("}")) {
                        jsonContent = jsonContent.substring(1, jsonContent.length() - 1);
                        String[] pairs = jsonContent.split(",");
                        for (String pair : pairs) {
                            String[] keyValue = pair.split(":");
                            if (keyValue.length == 2) {
                                String key = keyValue[0].trim();
                                if (key.startsWith("\"") && key.endsWith("\"")) {
                                    key = key.substring(1, key.length() - 1);
                                }
                                String value = keyValue[1].trim();
                                if (value.startsWith("\"") && value.endsWith("\"")) {
                                    value = value.substring(1, value.length() - 1);
                                }
                                updateClientConfigSetting(key, value);
                            }
                        }
                        devPrint("Loaded client config (flat) from: " + configFile);
                    }
                }
            } else {
                devPrint("Config file does not exist, using defaults");
            }
        } catch (Exception e) {
            devPrint("Error loading client config: " + e.getMessage());
        }
    }

    private static void saveClientConfigToJson() {
        if (!configMode) return;
        Map<String, String> configMap = new LinkedHashMap<>();
        for (Settings s : settings) {
            configMap.put(s.key, s.value);
        }
        StringBuilder innerJson = new StringBuilder("{");
        boolean firstInner = true;
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            if (!firstInner) innerJson.append(",");
            innerJson.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue().equals("true") || entry.getValue().equals("false") || entry.getValue().matches("-?\\d+")) innerJson.append(entry.getValue());
            else innerJson.append("\"").append(entry.getValue()).append("\"");
            firstInner = false;
        }
        innerJson.append("}");
        StringBuilder json = new StringBuilder("{\"settings\":");
        json.append(innerJson);
        json.append("}");
        try {
            Path configDir = Paths.get(String.valueOf(remotelyDir), "data");
            Files.createDirectories(configDir);
            Path configFile = configDir.resolve("config.json");
            Files.write(configFile, json.toString().getBytes());
            devPrint("Saved client config to: " + configFile);
        } catch (Exception e) {
            devPrint("Error writing client config JSON: " + e.getMessage());
        }
    }

    public static String getCurrentTheme() {
        try {
            Path configDir = Path.of(String.valueOf(remotelyDir), "data");
            Path configFile = configDir.resolve("config.json");
            if (Files.exists(configFile)) {
                String jsonContent = Files.readString(configFile);
                Pattern pattern = Pattern.compile("\"theme\"\\s*:\\s*\"(.*?)\"");
                Matcher matcher = pattern.matcher(jsonContent);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            devPrint("Error reading theme from config: " + e.getMessage());
        }
        return "Default";
    }

    @Override
    protected void init() {
        super.init();
        try {
            closeIcon = new ImageUtil.IconWithTooltip("/assets/remotely/icons/close.png", "Cancel");
            createIcon = new ImageUtil.IconWithTooltip("/assets/remotely/icons/create.png", editServerMode ? "Apply Changes" : "Create Server");
        } catch (Exception e) {
            devPrint("Failed to load icons: " + e.getMessage());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawScreenHeader(context, width, height, width - 5, mouseX, mouseY, this, mc, closeIcon, configMode ? null : createIcon, null, null, null, null, null, null, null);
        recalcTabs();
        int headerHeight = 30;
        context.drawText(mc.textRenderer, Text.literal("Create New Server"), 10, 10, globalTextColor, shadow);
        drawTabs(context, mc.textRenderer, tabs, currentTab, mouseX, mouseY, false, false);
        int tabAreaHeight = 18;
        int contentY = headerHeight + tabAreaHeight + 10;
        int contentX = 5;
        int contentWidth = this.width - 10;
        int contentHeight = this.height - contentY - 10;
        List<Settings> currentSettings = new ArrayList<>();
        for (Settings s : settings) {
            if (s.tab.equals(tabs.get(currentTab)) && dependencySatisfied(s)) {
                currentSettings.add(s);
            }
        }
        int totalContentHeight = currentSettings.size() * rowHeight;
        currentSettingsScroll += (targetSettingsScroll - currentSettingsScroll) * globalScrollSpeed * deltaTime;
        context.enableScissor(contentX - 2, contentY - 2, contentX + contentWidth + 4, height - 5);
        int widgetWidth = 180;
        int widgetAreaX = this.width - widgetWidth - 12;
        for (int i = 0; i < currentSettings.size(); i++) {
            int rowY = contentY + i * rowHeight - (int) currentSettingsScroll;
            if (rowY + rowHeight < contentY || rowY > contentY + contentHeight) continue;
            int bgColor = getElementBackgroundColor(currentSettings.get(i).name.hashCode(), false, false, true, false, false, false);
            context.fill(contentX, rowY, contentX + contentWidth, rowY + rowHeight - 2, bgColor);
            drawInnerBorder(context, contentX, rowY, contentWidth, rowHeight - 2, getElementBorderColor(currentSettings.get(i).name.hashCode(), false, false, true, false, false, false));
            drawOuterBorder(context, contentX, rowY, contentWidth, rowHeight - 2, bgColor);
            String name = currentSettings.get(i).name;
            context.drawText(mc.textRenderer, Text.literal(name), contentX + 5, rowY + 5, globalTextColor, shadow);
            context.drawText(mc.textRenderer, Text.literal(currentSettings.get(i).description), contentX + 5, rowY + 5 + mc.textRenderer.fontHeight + 2, globalDarkTextColor, shadow);
            Settings s = currentSettings.get(i);
            int widgetY = rowY + (rowHeight - 20) / 2;
            boolean widgetHovered = mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth && mouseY >= rowY && mouseY <= rowY + 18;
            boolean toggleHovered = mouseX >= this.width - 40 - 12 && mouseX <= this.width - 12 && mouseY >= rowY && mouseY <= rowY + rowHeight;
            switch (s.type) {
                case TOGGLE -> drawToggle(context, this.width - 40 - 12, widgetY - 1, s.name + s.description, s.value.equals("true"), toggleHovered, 40, 20);
                case SLIDER -> {
                    double normalizedValue = (s.getIntValue() - s.min) / (double) (s.max - s.min);
                    drawSlider(context, mc, widgetAreaX, widgetY, s.name + ": " + s.value, normalizedValue, widgetHovered, false, mouseX, mouseY, "Shift + Click To Input Text.", 180, 18);
                }
                case SCROLL_SWITCH -> drawScrollSelector(context, mc, widgetAreaX, widgetY, s.options, s.getSelectedIndex(), widgetHovered, 180, 18);
                case TAB_SWITCH -> drawTabSwitch(context, mc, widgetAreaX, widgetY, s.name + s.key + s.description, s.options, s.getSelectedIndex(), mouseX, mouseY, 180, 18);
                case TEXT -> {
                    float currentScroll = textInputScrollOffsets.getOrDefault(s, 0f);
                    float targetScroll = textInputTargetScrollOffsets.getOrDefault(s, 0f);
                    drawTextInput(context, mc, widgetAreaX, widgetY, s.name, s.value, s.focused, s.cursorPos, textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos), widgetHovered, 180, 18, "Type...");
                    textInputScrollOffsets.put(s, currentScroll);
                    textInputTargetScrollOffsets.put(s, targetScroll);
                }
            }
        }
        context.disableScissor();
        int maxScroll = Math.max(0, totalContentHeight - contentHeight);
        if ((int) currentSettingsScroll > 2) {
            context.fillGradient(contentX, contentY - 2, contentX + contentWidth, contentY + 10, 0x55000000, 0x00000000);
        }
        if ((int) currentSettingsScroll <= maxScroll + 3) {
            context.fillGradient(contentX, height - 5, contentX + contentWidth, contentY + contentHeight, 0x00000000, 0x55000000);
        }
        animatedScaling(this);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Settings s : settings) {
                if (s.type == TEXT) {
                    s.focused = false;
                }
            }
            int tabAreaHeight = 18;
            if (mouseY >= 35 && mouseY <= 35 + tabAreaHeight) {
                int tabX = 5;
                for (int i = 0; i < tabs.size(); i++) {
                    int tabWidth = mc.textRenderer.getWidth(tabs.get(i)) + 10;
                    if (mouseX >= tabX && mouseX <= tabX + tabWidth) {
                        playSound(Sound.SWITCHTAB);
                        currentTab = i;
                        targetSettingsScroll = 0;
                        currentSettingsScroll = 0;
                        return true;
                    }
                    tabX += tabWidth + 5;
                }
            }
            int buttonY = 5;
            int createButtonX = this.width - 46;
            int cancelButtonX = this.width - 23;
            if (mouseY >= buttonY && mouseY <= buttonY + 18) {
                if (mouseX >= createButtonX && mouseX <= createButtonX + 18) {
                    createServer();
                    return true;
                }
                if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + 18) {
                    close();
                    return true;
                }
            }
            int headerH = 30;
            int tabH = 18;
            int contentY = headerH + tabH + 10;
            int widgetWidth = 180;
            int widgetAreaX = this.width - widgetWidth - 12;
            List<Settings> currentSettings = new ArrayList<>();
            for (Settings s : settings) {
                if (s.tab.equals(tabs.get(currentTab)) && dependencySatisfied(s)) {
                    currentSettings.add(s);
                }
            }
            for (int i = 0; i < currentSettings.size(); i++) {
                int rowY = contentY + i * rowHeight - (int) currentSettingsScroll;
                if (mouseX < widgetAreaX || mouseX > widgetAreaX + widgetWidth || mouseY < rowY || mouseY > rowY + rowHeight)
                    continue;
                Settings s = currentSettings.get(i);
                switch (s.type) {
                    case TOGGLE -> {
                        int toggleX = this.width - 40 - 12;
                        int toggleWidth = 40;
                        boolean toggleHovered = mouseX >= toggleX && mouseX <= toggleX + toggleWidth && mouseY >= rowY && mouseY <= rowY + rowHeight;
                        if (toggleHovered) {
                            playSound(Sound.CLICK);
                            s.value = s.value.equals("true") ? "false" : "true";
                            if (configMode) updateClientConfigSetting(s.key, s.value);
                        }
                    }
                    case SLIDER -> {
                        boolean sliderHovered = mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth;
                        if (sliderHovered) {
                            playSound(Sound.CLICK);
                            float relativeX = (float) (mouseX - widgetAreaX);
                            relativeX = Math.max(0, Math.min(relativeX, widgetWidth));
                            double percent = relativeX / (double) widgetWidth;
                            int newVal = (int) (s.min + (percent * (s.max - s.min)));
                            s.value = String.valueOf(newVal);
                            if (configMode) updateClientConfigSetting(s.key, s.value);
                            draggedSlider = s;
                        }
                    }
                    case SCROLL_SWITCH -> {
                        playSound(Sound.CLICK);
                        if (s.index == selectedDropDown) {
                            int centerX = widgetAreaX + widgetWidth / 2;
                            if (mouseX < centerX - 10) {
                                int currentIndex = s.getSelectedIndex();
                                if (currentIndex > 0) {
                                    s.value = s.options.get(currentIndex - 1);
                                } else {
                                    s.value = s.options.get(s.options.size() - 1);
                                }
                                if (configMode) updateClientConfigSetting(s.key, s.value);
                            } else if (mouseX > centerX + 10) {
                                int currentIndex = s.getSelectedIndex();
                                if (currentIndex < s.options.size() - 1) {
                                    s.value = s.options.get(currentIndex + 1);
                                } else {
                                    s.value = s.options.get(0);
                                }
                                if (configMode) updateClientConfigSetting(s.key, s.value);
                            } else {
                                selectedDropDown = -1;
                            }
                        } else {
                            selectedDropDown = s.index;
                        }
                    }
                    case TAB_SWITCH -> {
                        double relativeX = mouseX - widgetAreaX;
                        double relativeY = mouseY - rowY;
                        if (relativeX >= 0 && relativeX <= widgetWidth && relativeY >= 0 && relativeY <= 18) {
                            playSound(Sound.CLICK);
                            int segmentCount = s.options.size();
                            double segmentWidth = (double) widgetWidth / segmentCount;
                            int newIndex = (int) (relativeX / segmentWidth);
                            s.setOption(newIndex);
                            if (configMode) updateClientConfigSetting(s.key, s.value);
                        }
                    }
                    case TEXT -> {
                        if (mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth && mouseY >= rowY && mouseY <= rowY + rowHeight) {
                            playSound(Sound.SELECT);
                            s.focused = true;
                            int clickX = (int) mouseX - widgetAreaX - 5;
                            int pos = 0;
                            int cumulativeWidth = 0;
                            for (int j = 0; j < s.value.length(); j++) {
                                int charWidth = mc.textRenderer.getWidth(s.value.substring(j, j + 1));
                                if (cumulativeWidth + charWidth / 2 > clickX) {
                                    pos = j;
                                    break;
                                }
                                cumulativeWidth += charWidth;
                                pos = j + 1;
                            }
                            s.cursorPos = pos;
                            textSelectionStart.put(s, pos);
                            textSelectionEnd.put(s, pos);
                        }
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (Settings s : settings) {
            if (s.type == TEXT && s.focused) {
                int widgetWidth = 180;
                int widgetAreaX = this.width - widgetWidth - 12;
                List<Settings> currentSettings = new ArrayList<>();
                for (Settings setting : settings) {
                    if (setting.tab.equals(tabs.get(currentTab)) && dependencySatisfied(setting)) {
                        currentSettings.add(setting);
                    }
                }
                int index = currentSettings.indexOf(s);
                int headerH = 30;
                int tabH = 18;
                int contentY = headerH + tabH + 10;
                int rowY = contentY + index * rowHeight - (int) currentSettingsScroll;
                if (mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth && mouseY >= rowY && mouseY <= rowY + rowHeight) {
                    int clickX = (int) mouseX - widgetAreaX - 5;
                    int pos = 0;
                    int cumulativeWidth = 0;
                    for (int j = 0; j < s.value.length(); j++) {
                        int charWidth = mc.textRenderer.getWidth(s.value.substring(j, j + 1));
                        if (cumulativeWidth + charWidth / 2 > clickX) {
                            pos = j;
                            break;
                        }
                        cumulativeWidth += charWidth;
                        pos = j + 1;
                    }
                    s.cursorPos = pos;
                    textSelectionEnd.put(s, pos);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                    return true;
                }
            }
        }
        if (button == 0 && draggedSlider != null) {
            int widgetWidth = 180;
            int widgetAreaX = this.width - widgetWidth - 12;
            float relativeX = (float) (mouseX - widgetAreaX);
            if (relativeX < 0) relativeX = 0;
            if (relativeX > (float) widgetWidth) relativeX = (float) widgetWidth;
            float percent = relativeX / (float) widgetWidth;
            int newVal = draggedSlider.min + (int) (percent * (draggedSlider.max - draggedSlider.min));
            draggedSlider.value = String.valueOf(newVal);
            if (configMode) updateClientConfigSetting(draggedSlider.key, draggedSlider.value);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggedSlider = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scaleScroll(verticalAmount);
        int headerHeight = 30;
        int tabAreaHeight = 18;
        int contentY = headerHeight + tabAreaHeight + 10;
        int contentHeight = this.height - contentY - 10;
        List<Settings> currentSettings = new ArrayList<>();
        for (Settings s : settings) {
            if (s.tab.equals(tabs.get(currentTab)) && dependencySatisfied(s)) currentSettings.add(s);
        }
        int totalContentHeight = currentSettings.size() * rowHeight;
        targetSettingsScroll -= (float) (verticalAmount * 20);
        if (targetSettingsScroll < 0) targetSettingsScroll = 0;
        if (targetSettingsScroll > totalContentHeight - contentHeight) targetSettingsScroll = Math.max(0, totalContentHeight - contentHeight);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        for (Settings s : settings) {
            if (s.type == TEXT && s.focused) {
                boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
                boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
                int start = Math.min(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
                int end = Math.max(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
                if ((keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) && start < end) {
                    s.value = s.value.substring(0, start) + s.value.substring(end);
                    s.cursorPos = start;
                    textSelectionStart.put(s, start);
                    textSelectionEnd.put(s, start);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                    return true;
                } else if (ctrl && keyCode == GLFW.GLFW_KEY_BACKSPACE && s.cursorPos > 0) {
                    int pos = s.cursorPos;
                    while (pos > 0 && s.value.charAt(pos - 1) == ' ') pos--;
                    while (pos > 0 && s.value.charAt(pos - 1) != ' ') pos--;
                    s.value = s.value.substring(0, pos) + s.value.substring(s.cursorPos);
                    s.cursorPos = pos;
                    textSelectionStart.put(s, pos);
                    textSelectionEnd.put(s, pos);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                } else if (ctrl && keyCode == GLFW.GLFW_KEY_LEFT && s.cursorPos > 0) {
                    int pos = s.cursorPos;
                    while (pos > 0 && s.value.charAt(pos - 1) == ' ') pos--;
                    while (pos > 0 && s.value.charAt(pos - 1) != ' ') pos--;
                    s.cursorPos = pos;
                    if (shift) {
                        textSelectionEnd.put(s, pos);
                    } else {
                        textSelectionStart.put(s, pos);
                        textSelectionEnd.put(s, pos);
                    }
                } else if (ctrl && keyCode == GLFW.GLFW_KEY_RIGHT && s.cursorPos < s.value.length()) {
                    int pos = s.cursorPos;
                    while (pos < s.value.length() && s.value.charAt(pos) != ' ') pos++;
                    while (pos < s.value.length() && s.value.charAt(pos) == ' ') pos++;
                    s.cursorPos = pos;
                    if (shift) {
                        textSelectionEnd.put(s, pos);
                    } else {
                        textSelectionStart.put(s, pos);
                        textSelectionEnd.put(s, pos);
                    }
                } else if (!ctrl && keyCode == GLFW.GLFW_KEY_LEFT && s.cursorPos > 0) {
                    s.cursorPos--;
                    if (shift) {
                        textSelectionEnd.put(s, s.cursorPos);
                    } else {
                        textSelectionStart.put(s, s.cursorPos);
                        textSelectionEnd.put(s, s.cursorPos);
                    }
                } else if (!ctrl && keyCode == GLFW.GLFW_KEY_RIGHT && s.cursorPos < s.value.length()) {
                    s.cursorPos++;
                    if (shift) {
                        textSelectionEnd.put(s, s.cursorPos);
                    } else {
                        textSelectionStart.put(s, s.cursorPos);
                        textSelectionEnd.put(s, s.cursorPos);
                    }
                } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                    s.cursorPos = 0;
                    if (shift) {
                        textSelectionEnd.put(s, 0);
                    } else {
                        textSelectionStart.put(s, 0);
                        textSelectionEnd.put(s, 0);
                    }
                } else if (keyCode == GLFW.GLFW_KEY_END) {
                    s.cursorPos = s.value.length();
                    if (shift) {
                        textSelectionEnd.put(s, s.value.length());
                    } else {
                        textSelectionStart.put(s, s.value.length());
                        textSelectionEnd.put(s, s.value.length());
                    }
                } else if (ctrl && keyCode == GLFW.GLFW_KEY_DELETE && s.cursorPos < s.value.length()) {
                    int pos = s.cursorPos;
                    while (pos < s.value.length() && s.value.charAt(pos) == ' ') pos++;
                    while (pos < s.value.length() && s.value.charAt(pos) != ' ') pos++;
                    s.value = s.value.substring(0, s.cursorPos) + s.value.substring(pos);
                    textSelectionStart.put(s, s.cursorPos);
                    textSelectionEnd.put(s, s.cursorPos);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                } else if (!ctrl && keyCode == GLFW.GLFW_KEY_DELETE && s.cursorPos < s.value.length()) {
                    s.value = s.value.substring(0, s.cursorPos) + s.value.substring(s.cursorPos + 1);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && s.cursorPos > 0) {
                    s.value = s.value.substring(0, s.cursorPos - 1) + s.value.substring(s.cursorPos);
                    s.cursorPos--;
                    textSelectionStart.put(s, s.cursorPos);
                    textSelectionEnd.put(s, s.cursorPos);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && ctrl) {
                    s.value = "";
                    s.cursorPos = 0;
                    textSelectionStart.put(s, 0);
                    textSelectionEnd.put(s, 0);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
                    s.focused = false;
                } else if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                    textSelectionStart.put(s, 0);
                    textSelectionEnd.put(s, s.value.length());
                    s.cursorPos = s.value.length();
                } else if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
                    handleClipboardCopy(s);
                } else if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                    handleClipboardPaste(s);
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void handleClipboardCopy(Settings s) {
        try {
            int start = Math.min(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
            int end = Math.max(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
            if (start < end) {
                String selectedText = s.value.substring(start, end);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selectedText), null);
            }
        } catch (SecurityException | IllegalStateException e) {
            devPrint("Clipboard access error: " + e.getMessage());
        }
    }

    private void handleClipboardPaste(Settings s) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                String clipboardText = (String) clipboard.getData(DataFlavor.stringFlavor);
                if (clipboardText != null) {
                    int start = Math.min(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
                    int end = Math.max(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
                    s.value = s.value.substring(0, start) + clipboardText + s.value.substring(end);
                    s.cursorPos = start + clipboardText.length();
                    textSelectionStart.put(s, s.cursorPos);
                    textSelectionEnd.put(s, s.cursorPos);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                }
            }
        } catch (Exception e) {
            devPrint("Clipboard access error: " + e.getMessage());
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (Settings s : settings) {
            if (s.type == TEXT && s.focused) {
                int selStart = Math.min(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
                int selEnd = Math.max(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
                if (selStart != selEnd) {
                    s.value = s.value.substring(0, selStart) + s.value.substring(selEnd);
                    s.cursorPos = selStart;
                    textSelectionStart.put(s, selStart);
                    textSelectionEnd.put(s, selStart);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                }
                if (chr == 13 || chr == 27) return true;
                if (!Character.isISOControl(chr)) {
                    s.value = s.value.substring(0, s.cursorPos) + chr + s.value.substring(s.cursorPos);
                    s.cursorPos++;
                    textSelectionStart.put(s, s.cursorPos);
                    textSelectionEnd.put(s, s.cursorPos);
                    if (configMode) updateClientConfigSetting(s.key, s.value);
                }
            }
        }
        return super.charTyped(chr, modifiers);
    }


    private void writeSettings(SSHManager manager, String basePath) {
        Map<String, List<Settings>> fileGroups = new HashMap<>();
        for (Settings st : settings) {
            if (!st.file.equals("none") && dependencySatisfied(st) && !st.value.isEmpty()) {
                fileGroups.computeIfAbsent(st.file, k -> new ArrayList<>()).add(st);
            }
        }
        for (String fileName : fileGroups.keySet()) {
            boolean isYaml = fileName.endsWith(".yml") || fileName.endsWith(".yaml");
            if (manager == null) {
                try {
                    Path filePath = Paths.get(basePath, fileName);
                    List<String> originalLines = new ArrayList<>();
                    if (Files.exists(filePath)) {
                        originalLines = Files.readAllLines(filePath);
                    }
                    if (isYaml) {
                        Map<String, Object> yamlMap = new LinkedHashMap<>();
                        for (Settings st : fileGroups.get(fileName)) {
                            String[] parts = st.key.split("\\.");
                            Map<String, Object> current = yamlMap;
                            for (int i = 0; i < parts.length - 1; i++) {
                                current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
                            }
                            Object value;
                            if (st.value.equalsIgnoreCase("true") || st.value.equalsIgnoreCase("false")) {
                                value = Boolean.parseBoolean(st.value);
                            } else {
                                try {
                                    value = Integer.parseInt(st.value);
                                } catch (NumberFormatException e) {
                                    value = st.value;
                                }
                            }
                            current.put(parts[parts.length - 1], value);
                        }
                        StringBuilder yamlBuilder = new StringBuilder();
                        writeYaml(yamlMap, yamlBuilder, 0);
                        Files.write(filePath, yamlBuilder.toString().getBytes());
                        devPrint("Wrote YAML settings to local file: " + filePath);
                    } else {
                        Map<String, String> newSettings = new LinkedHashMap<>();
                        for (Settings st : fileGroups.get(fileName)) {
                            String value = st.value;
                            if (st.key.equalsIgnoreCase("gamemode") || st.key.equalsIgnoreCase("difficulty")) {
                                value = value.toLowerCase();
                            }
                            newSettings.put(st.key, st.key + "=" + value);
                        }
                        List<String> updatedLines = new ArrayList<>();
                        Set<String> keysUpdated = new HashSet<>();
                        for (String line : originalLines) {
                            boolean found = false;
                            for (String key : newSettings.keySet()) {
                                if (line.startsWith(key + "=")) {
                                    updatedLines.add(newSettings.get(key));
                                    keysUpdated.add(key);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                updatedLines.add(line);
                            }
                        }
                        for (String key : newSettings.keySet()) {
                            if (!keysUpdated.contains(key)) {
                                updatedLines.add(newSettings.get(key));
                            }
                        }
                        Files.write(filePath, String.join("\n", updatedLines).getBytes());
                        devPrint("Wrote settings to local file: " + filePath);
                    }
                } catch (Exception e) {
                    devPrint("Error writing local settings to file " + fileName + ": " + e.getMessage());
                }
            } else {
                if (isYaml) {
                    Map<String, Object> yamlMap = new LinkedHashMap<>();
                    for (Settings st : fileGroups.get(fileName)) {
                        String[] parts = st.key.split("\\.");
                        Map<String, Object> current = yamlMap;
                        for (int i = 0; i < parts.length - 1; i++) {
                            current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
                        }
                        Object value;
                        if (st.value.equalsIgnoreCase("true") || st.value.equalsIgnoreCase("false")) {
                            value = Boolean.parseBoolean(st.value);
                        } else {
                            try {
                                value = Integer.parseInt(st.value);
                            } catch (NumberFormatException e) {
                                value = st.value;
                            }
                        }
                        current.put(parts[parts.length - 1], value);
                    }
                    StringBuilder yamlBuilder = new StringBuilder();
                    writeYaml(yamlMap, yamlBuilder, 0);
                    manager.writeRemoteFile(basePath + "/" + fileName, yamlBuilder.toString());
                    devPrint("Wrote YAML settings to remote file: " + basePath + "/" + fileName);
                } else {
                    List<String> lines = new ArrayList<>();
                    for (Settings st : fileGroups.get(fileName)) {
                        if (st.key.equalsIgnoreCase("gamemode") || st.key.equalsIgnoreCase("difficulty")) {
                            lines.add(st.key + "=" + st.value.toLowerCase());
                        } else {
                            if (dependencySatisfied(st) && !st.value.isEmpty())
                                lines.add(st.key + "=" + st.value);
                        }
                    }
                    manager.writeRemoteFile(basePath + "/" + fileName, String.join("\n", lines));
                    devPrint("Wrote settings to remote file: " + basePath + "/" + fileName);
                }
            }
        }
    }

    private void writeYaml(Map<String, Object> map, StringBuilder builder, int indent) {
        String indentStr = "  ".repeat(indent);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                builder.append(indentStr).append(entry.getKey()).append(":\n");
                writeYaml((Map<String, Object>) nested, builder, indent + 1);
            } else {
                builder.append(indentStr).append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
    }

    public void createServer() {
        String serverName = "";
        String serverType = "";
        String serverVersion = "";
        String ramAmount = "";
        String aikarsFlags = "";
        for (Settings s : settings) {
            if (s.key.equals("server-name"))
                serverName = s.value.trim();
            if (s.key.equals("server-type"))
                serverType = s.value.trim();
            if (s.key.equals("server-version"))
                serverVersion = s.value.trim();
            if (s.key.equals("memory")) {
                ramAmount = s.value.trim();
                if (ramAmount.toLowerCase().endsWith("g")) {
                    double g = Double.parseDouble(ramAmount.substring(0, ramAmount.length() - 1));
                    int mb = (int) Math.round(g * 1024);
                    ramAmount = String.valueOf(mb);
                } else if (ramAmount.toLowerCase().endsWith("m")) {
                    ramAmount = ramAmount.substring(0, ramAmount.length() - 1);
                }
            }
            if (s.key.equals("aikars_flags")) {
                if (s.value.equalsIgnoreCase("true"))
                    aikarsFlags = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch";
                else
                    aikarsFlags = "";
            }
        }
        if (serverName.isEmpty())
            serverName = "MyServer";
        if (serverVersion.isEmpty())
            serverVersion = "latest";
        if (ramAmount.isEmpty())
            ramAmount = "2048";

        if (editServerMode && serverInfo != null) {
            editServer(serverName, serverType.toLowerCase(), serverVersion.toLowerCase());
            close();
            return;
        }

        int tabIndex = ServerManagerScreen.getActiveTabIndex();
        if (tabIndex > 0) {
            createRemoteServer(serverName, serverType.toLowerCase(), serverVersion.toLowerCase(), ramAmount, aikarsFlags);
        } else {
            String finalServerName = serverName;
            ServerFactory.createServerAsync(serverName, serverType.toLowerCase(), serverVersion.toLowerCase(), settingsRoot, ramAmount, aikarsFlags, exitCode -> {
                if (exitCode == 0) {
                    notification.change(finalServerName + " Created Successfully!", "Click To Open", Type.SUCCESS, () -> ServerManagerScreen.openServerScreen(settingsRoot + File.separator + finalServerName));
                    String serverDir = settingsRoot + File.separator + finalServerName;
                    writeSettings(null, serverDir);
                } else errorNotification(exitCode, notification);
                close();
            });
        }
    }

    private void createRemoteServer(String serverName, String serverType, String serverVersion, String ramAmount, String aikarsFlags) {
        try {
            int tabIndex = ServerManagerScreen.getActiveTabIndex();
            RemoteHostInfo rh = ServerManagerScreen.getRemoteHosts().get(tabIndex - 1);
            String remoteHome = rh.getHomeDirectory();
            String remoteServersPath = remoteHome + "remotely/servers";
            String remoteServerPath = remoteServersPath + "/" + serverName;

            notification = new Notification("Creating Remote Server...", "This Might Take Some Time..", Type.INFO);
            notification.loading = true;
            notification.autoSlideOut = false;

            SSHManager ssh = INSTANCE.getSSHManagerForHost(rh);
            if (!ssh.isSFTPConnected()) {
                ssh.connectToRemoteHost(rh.getUser(), rh.getIp(), rh.getPort(), rh.getPassword());
                while (!ssh.isSFTPConnected()) {
                    Thread.sleep(100);
                }
            }

            ssh.prepareRemoteDirectorySync(remoteHome + "/remotely");
            ssh.prepareRemoteDirectorySync(remoteServersPath);
            ssh.prepareRemoteDirectorySync(remoteServerPath);

            String downloadURL = ServerFactory.getDownloadURL(serverType, serverVersion);
            if (downloadURL == null) {
                notification.change("Unsupported Server Type or Version!", "Please Try Again.", Type.ERROR, null);
                close();
                return;
            }

            String remoteCmd = "cd " + remoteServerPath + " && " + "wget -O server.jar \"" + downloadURL + "\"";
            String output = ssh.runRemoteCommandWithOutput(remoteCmd);
            devPrint("wget output: " + output);
            String memSettings = "-Xms" + ramAmount + "M -Xmx" + ramAmount + "M";
            String javaCommand = "java " + memSettings + " " + aikarsFlags + " -jar server.jar nogui";
            String shContent = "#!/bin/bash\ncd \"$(dirname \"$0\")\"\n" + javaCommand;
            ssh.writeRemoteFile(remoteServerPath + "/start.sh", shContent);
            ssh.runRemoteCommand("chmod +x " + remoteServerPath + "/start.sh");

            writeSettings(ssh, remoteServerPath);
            ServerManagerScreen.addServer(serverName, remoteServerPath, serverType, serverVersion, true, rh);
            String url = ServerFactory.getDownloadURL(serverType, serverVersion);
            if (url != null) {
                ssh.runRemoteCommandWithOutput("cd " + remoteServerPath + " && wget -O server.jar \"" + url + "\"");
            } else {
                notification.change("Failed To Get Download URL", "Unsupported Server Type / Version", Type.ERROR, null);
                return;
            }
            notification.change(serverName + " Created Successfully!", "Click To Open", Type.SUCCESS, () -> ServerManagerScreen.openServerScreen(remoteServerPath));

        } catch (Exception e) {
            devPrint("Failed to create remote server: " + e.getMessage());
            notification.change("Server Creation Failed", e.getMessage(), Type.ERROR, null);
        } finally {
            close();
        }
    }

    private void editServer(String serverName, String serverType, String serverVersion) {
        boolean shouldRebuild = !serverInfo.version.equals(serverVersion) || !serverInfo.type.equalsIgnoreCase(serverType);
        try {
            if (!serverInfo.isRemote) {
                File serverDir = new File(settingsRoot);
                writeSettings(null, serverDir.getAbsolutePath());
                if (shouldRebuild) {
                    ServerFactory.createServerAsync(serverName, serverType.toLowerCase(), serverVersion.toLowerCase(), settingsRoot, "", "", exitCode -> {
                        if (exitCode == 0) {
                            new Notification(serverName + " Updated Successfully!", Type.SUCCESS);
                        } else errorNotification(exitCode, notification);
                    });
                } else {
                    new Notification(serverName + " Edited Successfully!", Type.SUCCESS);
                }
            } else {
                RemoteHostInfo rh = serverInfo.remoteHost;
                String remoteHome = rh.getHomeDirectory();
                String remoteServersPath = remoteHome + "remotely/servers";
                String remoteServerPath = remoteServersPath + "/" + serverName;
                SSHManager ssh = new SSHManager(rh);
                ssh.connectToRemoteHost(rh.getUser(), rh.getIp(), rh.getPort(), rh.getPassword());
                while (!ssh.isSFTPConnected()) {
                    Thread.sleep(100);
                }
                ssh.prepareRemoteDirectorySync(remoteHome + "remotely");
                ssh.prepareRemoteDirectorySync(remoteServersPath);
                ssh.prepareRemoteDirectorySync(remoteServerPath);

                if (shouldRebuild) {
                    String downloadURL = ServerFactory.getDownloadURL(serverType, serverVersion);
                    if (downloadURL == null) {
                        new Notification("Failed to get download URL", "Unsupported server type or version", Type.ERROR);
                        return;
                    }
                    String remoteCmd = "mkdir -p " + remoteServerPath + " && " + "cd " + remoteServerPath + " && " + "wget -O server.jar \"" + downloadURL + "\"";
                    new Notification("Updating remote server...", "This might take some time", Type.INFO);
                    ssh.runRemoteCommand(remoteCmd);
                }
                String ramDigits = "2048";
                String memSettings = "-Xms" + ramDigits + "M -Xmx" + ramDigits + "M";
                String javaCommand = "java " + memSettings + " -jar server.jar nogui";
                String shContent = "#!/bin/bash\ncd \"$(dirname \"$0\")\"\n" + javaCommand;
                ssh.writeRemoteFile(remoteServerPath + "/start.sh", shContent);
                ssh.runRemoteCommand("chmod +x " + remoteServerPath + "/start.sh");
                writeSettings(ssh, remoteServerPath);
                new Notification(serverName + " Updated Successfully!", Type.SUCCESS);
            }

            serverInfo.name = serverName;
            serverInfo.type = serverType;
            serverInfo.version = serverVersion;
            ServerManagerScreen.saveServers();
            ServerManagerScreen.saveRemoteHosts();
        } catch (Exception e) {
            devPrint("Failed to update server: " + e.getMessage());
            new Notification("Server Update Failed", e.getMessage(), Type.ERROR);
        }
    }

    private void errorNotification(int existCode, Notification notification) {
        if (existCode == 3) {
            notification.change("Unsupported Server Type or Version!", "Please Try Again.", Type.ERROR, null);
        } else if (existCode == 1) {
            notification.change("Download Failed!", "Check Your Internet Connection.", Type.ERROR, null);
        } else if (existCode == 2) {
            notification.change("Failed To Create Start Script!", "", Type.ERROR, null);
        } else {
            notification.change("Server Creation Failed", "Exit Code:  + existCode", Type.ERROR, null);
        }
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }

    @Override
    public void removed() {
        mc.getWindow().setScaleFactor(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
    }

    public void close() {
        mc.setScreen(parent);
    }
}
