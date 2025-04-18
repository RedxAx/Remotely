package redxax.oxy.common.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.common.RemotelyClient;
import redxax.oxy.common.SSHManager;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.config.Themes;
import redxax.oxy.common.terminal.MultiTerminalScreen;
import redxax.oxy.common.util.ImageUtil;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.io.*;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static redxax.oxy.common.RemotelyClient.*;
import static redxax.oxy.common.Render.*;
import static redxax.oxy.common.config.Config.*;
import static redxax.oxy.common.config.Themes.importThemesFromJar;
import static redxax.oxy.common.servers.SettingsScreen.ServerSettingType.*;
import static redxax.oxy.common.terminal.MultiTerminalScreen.THEMES_DIR;
import static redxax.oxy.common.util.DevUtil.devPrint;
import static redxax.oxy.common.util.ImageUtil.drawBufferedImage;
import static redxax.oxy.common.util.SoundUtils.playClick;

public class SettingsScreen extends Screen {
    private final MinecraftClient mc;
    private final ServerManagerScreen parent;
    private final String mode;
    private final String settingsRoot;
    private final boolean editServerMode;
    private static boolean configMode = false;
    private ServerInfo serverInfo;
    private int currentTab;
    private static List<Settings> settings = new ArrayList<>();
    private List<String> tabs = new ArrayList<>();
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
    List<String> themeOptions = new ArrayList<>();

    public SettingsScreen(MinecraftClient mc, String mode, ServerManagerScreen parent, String settingsRoot, List<Settings> customSettings) {
        this(mc, mode, parent, settingsRoot, customSettings, null);
    }

    public SettingsScreen(MinecraftClient mc, String mode, ServerManagerScreen parent, String settingsRoot, List<Settings> customSettings, ServerInfo serverInfo) {
        super(Text.literal("Server Settings"));
        this.mc = mc;
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
            settings.add(new Settings("Error While Loading Settings", "No Settings Found.", "404", "none", "none", TEXT, "Please Try Again."));
        }
        if (editServerMode) {
            loadSettingsFromFiles();
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

    private void loadClientConfiguration() {
        importThemesFromJar();
        loadThemesFromDir();
        if (RemotelyClient.INSTANCE != null && themes != null && !themes.isEmpty()) {
            for (MultiTerminalScreen.Theme theme : themes) {
                themeOptions.add(theme.name);
            }
        } else {
            themeOptions.add("Default");
        }
        settings.clear();
        settings.add(new Settings("Theme", "Select and apply a theme on startup.", "Appearance", "none", "theme", SCROLL_SWITCH, getCurrentTheme(), themeOptions));
        settings.add(new Settings("Menus Buttons Style", "Choose The Style of The Buttons In The Menus.", "Appearance", "none", "mainMenuButtonsStyle", TAB_SWITCH, (mainMenuStyle.equals("Vanilla") ? "Vanilla" : mainMenuStyle.equals("Minimal") ? "Minimal" : mainMenuStyle.equals("Normal") ? "Normal" : "Disable"), Arrays.asList("Vanilla", "Minimal", "Normal", "Disable")));
        settings.add (new Settings("Redesign Minecraft Buttons", "Enable The New Button Design.", "Appearance", "none", "redesignMainMenu", TOGGLE, String.valueOf(redesignMainMenu)));
        settings.add(new Settings("Show Minecraft Background", "Display The Minecraft Panorama As The Background.", "Appearance", "none", "background", TOGGLE, String.valueOf(background)));
        settings.add(new Settings("Show Wallpaper", "Display Your PC Wallpaper As The Background.", "Appearance", "none", "wallpaper", TOGGLE, String.valueOf(wallpaper)));
        settings.add(new Settings("Text Shadow", "Enable Text Background / Shadow Effect.", "Appearance", "none", "shadow", TOGGLE, String.valueOf(shadow)));
        settings.add(new Settings("Scroll Animation Speed", "Set The Global Speed of The Scrolling Animations.", "Appearance", "none", "globalScrollSpeed", SLIDER, String.valueOf(globalScrollSpeed), 0, 60));
        settings.add(new Settings("Movement Animation Speed", "Set The Global Speed of The Movement Animations.", "Appearance", "none", "globalMovementSpeed", SLIDER, String.valueOf(globalMovementSpeed), 0, 60));
        settings.add(new Settings("Scale Animation Speed", "Set The Global Speed of The Scale Animations.", "Appearance", "none", "scaleAnimationSpeed", SLIDER, String.valueOf(scaleAnimationSpeed), 0, 60));
        settings.add(new Settings("Expand Animation Speed", "Set The Global Speed of The Expand/Shrink Animations.", "Appearance", "none", "globalExpandSpeed", SLIDER, String.valueOf(globalExpandSpeed).replace("f", ""), 0, 30));
        settings.add(new Settings("Enable Tab Close Button", "Adds a Close Button on The Top Right Corner of Tabs.", "Appearance", "none", "tabCloseButtons", TOGGLE, String.valueOf(tabCloseButtons)));
        settings.add(new Settings("Developer Mode", "Enable Developer Mode.", "Development", "none", "isDev", TOGGLE, String.valueOf(isDev)));
        settings.add(new Settings("Enable Debug Tools", "Enable Visual Tools For Debugging.", "Development", "none", "enableDebugTools", TOGGLE, String.valueOf(enableDebugTools)));
    }

    private void initTextInputOffsets() {
        for (Settings s : settings) {
            if (s.type == ServerSettingType.TEXT) {
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
        if (s.dependencyKey == null || s.dependencyKey.isEmpty()) return true;
        for (Settings setting : settings) {
            if (setting.key.equals(s.dependencyKey)) {
                return setting.value.equals(s.dependencyValue);
            }
        }
        return false;
    }

    private static void updateClientConfigSetting(String key, String value) {
        devPrint("Updated client config setting: " + key + " = " + value);
        switch (key) {
            case "background" -> background = Boolean.parseBoolean(value);
            case "redesignMainMenu" -> redesignMainMenu = Boolean.parseBoolean(value);
            case "wallpaper" -> wallpaper = Boolean.parseBoolean(value);
            case "shadow" -> shadow = Boolean.parseBoolean(value);
            case "mainMenuButtonsStyle" -> mainMenuStyle = value;
            case "globalScrollSpeed" -> globalScrollSpeed = Math.round(Float.parseFloat(value));
            case "globalMovementSpeed" -> globalMovementSpeed = Math.round(Float.parseFloat(value));
            case "globalExpandSpeed" -> globalExpandSpeed = Math.round(Float.parseFloat(value));
            case "scaleAnimationSpeed" -> scaleAnimationSpeed = Math.round(Float.parseFloat(value));
            case "tabCloseButtons" -> tabCloseButtons = Boolean.parseBoolean(value);
            case "isDev" -> isDev = Boolean.parseBoolean(value);
            case "enableDebugTools" -> enableDebugTools = Boolean.parseBoolean(value);
        }
        if (key.equals("theme") && RemotelyClient.INSTANCE != null) {
            for (MultiTerminalScreen.Theme theme : themes) {
                if (theme.name.equals(value)) {
                    Themes.applyTheme(theme);
                    break;
                }
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
                    devPrint("Loaded client config from: " + configFile);
                }
            } else {
                devPrint("Config file does not exist, using defaults");
            }
        } catch (Exception e) {
            devPrint("Error loading client config: " + e.getMessage());
        }
    }

    private static void saveClientConfigToJson() {
        if(!configMode) return;
        Map<String, String> configMap = new LinkedHashMap<>();
        for(Settings s: settings) {
            configMap.put(s.key, s.value);
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for(Map.Entry<String, String> entry : configMap.entrySet()){
            if(!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            if(entry.getValue().equals("true") || entry.getValue().equals("false") || entry.getValue().matches("-?\\d+"))
                json.append(entry.getValue());
            else
                json.append("\"").append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");
        try {
            Path configDir = Paths.get(String.valueOf(remotelyDir), "data");
            Files.createDirectories(configDir);
            Path configFile = configDir.resolve("config.json");
            Files.write(configFile, json.toString().getBytes());
            devPrint("Saved client config to: " + configFile);
        } catch(Exception e) {
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
        this.width = mc.getWindow().getScaledWidth();
        this.height = mc.getWindow().getScaledHeight();
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
        context.drawText(mc.textRenderer, Text.literal("Create New Server"), 10, 10, globalTextColor, Config.shadow);
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
        context.enableScissor(contentX -2, contentY -2, contentX + contentWidth + 4, height - 5);
        int widgetWidth = 180;
        int widgetAreaX = this.width - widgetWidth - 12;
        for (int i = 0; i < currentSettings.size(); i++) {
            int rowY = contentY + i * rowHeight - (int) currentSettingsScroll;
            if (rowY + rowHeight < contentY || rowY > contentY + contentHeight) continue;
            context.fill(contentX, rowY, contentX + contentWidth, rowY + rowHeight - 2, getElementBackgroundColor(currentSettings.get(i).name.hashCode(), false, false, true, false, false, false));
            drawInnerBorder(context, contentX, rowY, contentWidth, rowHeight - 2, getElementBorderColor(currentSettings.get(i).name.hashCode() , false, false, true, false, false, false));
            drawOuterBorder(context, contentX, rowY, contentWidth, rowHeight - 2, globalOuterBorder);
            String name = currentSettings.get(i).name;
            context.drawText(mc.textRenderer, Text.literal(name), contentX + 5, rowY + 5, globalTextColor, Config.shadow);
            context.drawText(mc.textRenderer, Text.literal(currentSettings.get(i).description), contentX + 5, rowY + 5 + mc.textRenderer.fontHeight + 2, Config.globalDarkTextColor, Config.shadow);
            Settings s = currentSettings.get(i);
            int widgetY = rowY + (rowHeight - 20) / 2;
            boolean widgetHovered = mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth && mouseY >= rowY && mouseY <= rowY + 18;
            boolean toggleHovered = mouseX >= this.width - 40 - 12 && mouseX <= this.width - 12 && mouseY >= rowY && mouseY <= rowY + rowHeight;
            switch (s.type) {
                case TOGGLE -> drawToggle(context, this.width - 40 - 12, widgetY - 1, s.name + s.description, s.value.equals("true"), toggleHovered, 40, 20);
                case SLIDER -> {
                    double normalizedValue = (s.getIntValue() - s.min) / (double)(s.max - s.min);
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
            context.fillGradient(contentX, contentY -2, contentX + contentWidth, contentY + 10, 0x55000000, 0x00000000);
        }
        if ((int) currentSettingsScroll <= maxScroll + 3) {
            context.fillGradient(contentX, height - 5, contentX + contentWidth, contentY + contentHeight, 0x00000000, 0x55000000);
        }
        animatedScaling(context, this, mc);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Settings s : settings) {
                if (s.type == ServerSettingType.TEXT) {
                    s.focused = false;
                }
            }
            int tabAreaHeight = 18;
            if (mouseY >= 35 && mouseY <= 35 + tabAreaHeight) {
                int tabX = 5;
                for (int i = 0; i < tabs.size(); i++) {
                    int tabWidth = mc.textRenderer.getWidth(tabs.get(i)) + 10;
                    if (mouseX >= tabX && mouseX <= tabX + tabWidth) {
                        playClick();
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
                    playClick();
                    createServer();
                    return true;
                }
                if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + 18) {
                    playClick();
                    onClose();
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
                if (s.tab.equals(tabs.get(currentTab))) {
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
                            playClick();
                            s.value = s.value.equals("true") ? "false" : "true";
                            if (configMode) updateClientConfigSetting(s.key, s.value);
                        }
                    }
                    case SLIDER -> {
                        boolean sliderHovered = mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth;
                        if (sliderHovered) {
                            playClick();
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
                        playClick();
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
                            playClick();
                            int segmentCount = s.options.size();
                            double segmentWidth = (double) widgetWidth / segmentCount;
                            int newIndex = (int) (relativeX / segmentWidth);
                            s.setOption(newIndex);
                            if (configMode) updateClientConfigSetting(s.key, s.value);
                        }
                    }
                    case TEXT -> {
                        if (mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth && mouseY >= rowY && mouseY <= rowY + rowHeight) {
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
            if (s.type == ServerSettingType.TEXT && s.focused) {
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
                    if(configMode) updateClientConfigSetting(s.key, s.value);
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
            onClose();
            return true;
        }
        for (Settings s : settings) {
            if (s.type == ServerSettingType.TEXT && s.focused) {
                boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
                boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

                int start = Math.min(textSelectionStart.getOrDefault(s, s.cursorPos),
                        textSelectionEnd.getOrDefault(s, s.cursorPos));
                int end = Math.max(textSelectionStart.getOrDefault(s, s.cursorPos),
                        textSelectionEnd.getOrDefault(s, s.cursorPos));
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
            int start = Math.min(textSelectionStart.getOrDefault(s, s.cursorPos),
                    textSelectionEnd.getOrDefault(s, s.cursorPos));
            int end = Math.max(textSelectionStart.getOrDefault(s, s.cursorPos),
                    textSelectionEnd.getOrDefault(s, s.cursorPos));
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
            if (s.type == ServerSettingType.TEXT && s.focused) {
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

    private String ensureLocalMcman() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        String fileName = os.contains("win") ? "mcman.exe" : "mcman";
        File file = new File(fileName);
        if (!file.exists()) {
            String url = os.contains("win") ? "https://github.com/ParadigmMC/mcman/releases/latest/download/mcman.exe" : "https://github.com/ParadigmMC/mcman/releases/latest/download/mcman";
            devPrint("Local mcman not found. Downloading from " + url);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, file.toPath());
            }
            file.setExecutable(true);
            devPrint("Local mcman downloaded to " + file.getAbsolutePath());
        }
        return file.getAbsolutePath();
    }

    private void ensureRemoteMcman(SSHManager ssh, String remoteHome) {
        String remotePath = remoteHome + "/remotely/mcman";
        devPrint("Checking if remote mcman exists at: " + remotePath);
        if (!ssh.remoteFileExists(remotePath)) {
            devPrint("Remote mcman not found. Downloading...");
            String cmd = "wget -O " + remotePath + " https://github.com/ParadigmMC/mcman/releases/latest/download/mcman && chmod +x " + remotePath;
            ssh.runRemoteCommand(cmd);
            try { Thread.sleep(2000); } catch (InterruptedException e) { }
            devPrint("Downloaded remote mcman at: " + remotePath);
        } else {
            devPrint("Remote mcman exists at: " + remotePath);
        }
    }

    private String getMcmanSettings() {
        StringBuilder sb = new StringBuilder();
        boolean headerAdded = false;
        for (Settings s : settings) {
            if (s.key.startsWith("launcher.")) {
                if (!headerAdded) {
                    sb.append(" && echo '[launcher]' >> server.toml");
                    headerAdded = true;
                }
                String key = s.key.substring("launcher.".length());
                sb.append(" && echo '").append(key).append(" = \"").append(s.value).append("\"' >> server.toml");
            }
        }
        return sb.toString();
    }

    private void writeSettings(SSHManager manager, String basePath) {
        Map<String, List<Settings>> fileGroups = new HashMap<>();
        for (Settings st : settings) {
            if (!st.file.equals("none") && dependencySatisfied(st) && !st.value.isEmpty()) {
                fileGroups.computeIfAbsent(st.file, k -> new ArrayList<>()).add(st);
            }
        }
        for (String fileName : fileGroups.keySet()) {
            if (manager == null) {
                try {
                    Path filePath = Paths.get(basePath, fileName);
                    List<String> originalLines = new ArrayList<>();
                    if (Files.exists(filePath)) {
                        originalLines = Files.readAllLines(filePath);
                    }
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
                } catch (Exception e) {
                    devPrint("Error writing local settings to file " + fileName + ": " + e.getMessage());
                }
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

    public void createServer() {
        String serverName = "";
        String serverType = "";
        String serverVersion = "";
        for (Settings s : settings) {
            if (s.name.equals("Server Name"))
                serverName = s.value.trim();
            if (s.name.equals("Server Type"))
                serverType = s.value.trim();
            if (s.name.equals("Server Version"))
                serverVersion = s.value.trim();
        }
        if (serverName.isEmpty())
            serverName = "MyServer";
        if (serverVersion.isEmpty())
            serverVersion = "latest";
        if (editServerMode && serverInfo != null) {
            editServer(serverName, serverType.toLowerCase(), serverVersion.toLowerCase());
        } else {
            createNewServer(serverName, serverType.toLowerCase(), serverVersion.toLowerCase());
        }
        onClose();
    }

    private void editServer(String serverName, String serverType, String serverVersion) {
        boolean shouldRebuild = !serverInfo.version.equals(serverVersion) || !serverInfo.type.equalsIgnoreCase(serverType);
        try {
            if (!serverInfo.isRemote) {
                String mcmanPath = ensureLocalMcman();
                File serverDir = new File(settingsRoot);
                devPrint("Running local init command...");
                ProcessBuilder pb = new ProcessBuilder(mcmanPath, "init", "--name", serverName);
                pb.directory(serverDir);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
                if (!finished) {
                    proc.destroyForcibly();
                    devPrint("mcman init process timed out and was terminated.");
                }
                devPrint("mcman init process exited with code: " + proc.exitValue() + " With output: " + output);
                writeSettings(null, serverDir.getAbsolutePath());
                Path serverToml = Paths.get(serverDir.getAbsolutePath(), "server.toml");
                List<String> tomlLines = new ArrayList<>();
                tomlLines.add("name = \"" + serverName + "\"");
                tomlLines.add("mc_version = \"" + serverVersion + "\"");
                tomlLines.add("[jar]");
                tomlLines.add("type = \"" + serverType.toLowerCase() + "\"");
                for (Settings s : settings) {
                    if (s.key.startsWith("launcher.")) {
                        String key = s.key.substring("launcher.".length());
                        tomlLines.add(key + " = \"" + s.value + "\"");
                    }
                }
                Files.write(serverToml, String.join("\n", tomlLines).getBytes());
                if (shouldRebuild) {
                    ProcessBuilder pbBuild = new ProcessBuilder(mcmanPath, "build", "--output", ".");
                    pbBuild.directory(serverDir);
                    pbBuild.redirectErrorStream(true);
                    Process procBuild = pbBuild.start();
                    BufferedReader readerBuild = new BufferedReader(new InputStreamReader(procBuild.getInputStream()));
                    String lineBuild;
                    while ((lineBuild = readerBuild.readLine()) != null) {
                        devPrint(lineBuild);
                    }
                    boolean finishedBuild = procBuild.waitFor(10, TimeUnit.SECONDS);
                    if (!finishedBuild) {
                        procBuild.destroyForcibly();
                        devPrint("mcman build process timed out and was terminated.");
                    }
                    devPrint("mcman build process exited with code: " + procBuild.exitValue());
                } else {
                    devPrint("Local server edited without rebuilding.");
                }
            } else {
                RemoteHostInfo rh = serverInfo.remoteHost;
                String remoteHome = rh.getHomeDirectory();
                String remoteMcmanPath = remoteHome + "/remotely/mcman";
                String remoteServersPath = remoteHome + "/remotely/servers";
                SSHManager ssh = new SSHManager(rh);
                ssh.connectToRemoteHost(rh.getUser(), rh.getIp(), rh.getPort(), rh.getPassword());
                while (!ssh.isSFTPConnected()) {
                    Thread.sleep(100);
                }
                devPrint("Preparing remote directories for edit...");
                ssh.prepareRemoteDirectory(remoteHome + "/remotely");
                ssh.prepareRemoteDirectory(remoteServersPath);
                ensureRemoteMcman(ssh, remoteHome);
                String cmdMkdir = "mkdir -p " + remoteServersPath + "/" + serverName;
                devPrint("Executing remote mkdir command: " + cmdMkdir);
                String mkdirOutput = ssh.runRemoteCommandWithOutput(cmdMkdir);
                devPrint("Remote mkdir output: " + mkdirOutput);
                String cmdUpdate = "cd " + remoteServersPath + "/" + serverName +
                        " && echo 'name = \"" + serverName + "\"' > server.toml" +
                        " && echo 'mc_version = \"" + serverVersion + "\"' >> server.toml" +
                        " && echo '[jar]' >> server.toml" +
                        " && echo 'type = \"" + serverType.toLowerCase() + "\"' >> server.toml" + getMcmanSettings();
                devPrint("Executing remote update command: " + cmdUpdate);
                String updateOutput = ssh.runRemoteCommandWithOutput(cmdUpdate);
                devPrint("Remote update output: " + updateOutput);
                writeSettings(ssh, remoteServersPath + "/" + serverName);
                if (shouldRebuild) {
                    String cmdBuild = "cd " + remoteServersPath + "/" + serverName + " && " + remoteMcmanPath + " build --output .";
                    devPrint("Executing remote build command: " + cmdBuild);
                    String buildOutput = ssh.runRemoteCommandWithOutput(cmdBuild);
                    for (String l : buildOutput.split("\n")) {
                        devPrint(l);
                    }
                } else {
                    devPrint("Remote server edited without rebuilding.");
                }
            }
            serverInfo.name = serverName;
            serverInfo.type = serverType;
            serverInfo.version = serverVersion;
            parent.saveServers();
            parent.saveRemoteHosts();
        } catch (Exception e) {
            devPrint("Failed to update server: " + e.getMessage());
        }
    }

    private void createNewServer(String serverName, String serverType, String serverVersion) {
        int tabIndex = parent.getActiveTabIndex();
        List<ServerInfo> currentServers = parent.getCurrentServers();
        try {
            if (tabIndex == 0) {
                devPrint("Creating new local server: " + serverName);
                String mcmanPath = ensureLocalMcman();
                String serverDirPath = settingsRoot + File.separator + serverName;
                File serverDir = new File(serverDirPath);
                if (!serverDir.exists()) {
                    serverDir.mkdirs();
                }
                StringBuilder sb = new StringBuilder();
                sb.append("name = \"").append(serverName).append("\"\n");
                sb.append("mc_version = \"").append(serverVersion).append("\"\n");
                sb.append("[jar]\n");
                sb.append("type = \"").append(serverType.toLowerCase()).append("\"\n");
                for (Settings s : settings) {
                    if (s.key.startsWith("launcher.")) {
                        String key = s.key.substring("launcher.".length());
                        sb.append(key).append(" = \"").append(s.value).append("\"\n");
                    }
                }
                Path serverToml = Paths.get(serverDirPath, "server.toml");
                Files.write(serverToml, sb.toString().getBytes());
                writeSettings(null, serverDirPath);
                ProcessBuilder pbBuild = new ProcessBuilder(mcmanPath, "build", "--output", ".");
                pbBuild.directory(serverDir);
                pbBuild.redirectErrorStream(true);
                Process procBuild = pbBuild.start();
                BufferedReader readerBuild = new BufferedReader(new InputStreamReader(procBuild.getInputStream()));
                StringBuilder outputBuild = new StringBuilder();
                String lineBuild;
                while ((lineBuild = readerBuild.readLine()) != null) {
                    devPrint(lineBuild);
                    outputBuild.append(lineBuild).append("\n");
                }
                boolean finishedBuild = procBuild.waitFor(10, TimeUnit.SECONDS);
                if (!finishedBuild) {
                    procBuild.destroyForcibly();
                    devPrint("mcman Build process timed out and was terminated.");
                }
                devPrint("mcman Build process exited with code: " + procBuild.exitValue() + " With output: " + outputBuild.toString());
                String path = serverDirPath;
                ServerInfo newInfo = new ServerInfo(path);
                newInfo.name = serverName;
                newInfo.path = path;
                newInfo.type = serverType.toLowerCase();
                newInfo.version = serverVersion;
                newInfo.isRunning = false;
                newInfo.isRemote = false;
                newInfo.remoteHost = null;
                currentServers.add(newInfo);
                devPrint("Created new local server: " + newInfo.name + " at " + newInfo.path);
                parent.saveServers();
            } else {
                devPrint("Creating new remote server: " + serverName);
                RemoteHostInfo rh = parent.getRemoteHosts().get(tabIndex - 1);
                String remoteHome = rh.getHomeDirectory();
                String remoteMcmanPath = remoteHome + "/remotely/mcman";
                String remoteServersPath = remoteHome + "/remotely/servers";
                String path = remoteServersPath + "/" + serverName;
                ServerInfo newInfo = new ServerInfo(path);
                newInfo.name = serverName;
                newInfo.path = path;
                newInfo.type = serverType.toLowerCase();
                newInfo.version = serverVersion;
                newInfo.isRunning = false;
                newInfo.isRemote = true;
                newInfo.remoteHost = rh;
                SSHManager ssh = new SSHManager(rh);
                ssh.connectToRemoteHost(rh.getUser(), rh.getIp(), rh.getPort(), rh.getPassword());
                while (!ssh.isSFTPConnected()) {
                    Thread.sleep(100);
                }
                devPrint("Preparing remote directories for new server...");
                ssh.prepareRemoteDirectory(remoteHome + "/remotely");
                ssh.prepareRemoteDirectory(remoteServersPath);
                ensureRemoteMcman(ssh, remoteHome);
                String cmdMkdir = "mkdir -p " + remoteServersPath + "/" + serverName;
                devPrint("Executing remote mkdir command: " + cmdMkdir);
                String mkdirOutput = ssh.runRemoteCommandWithOutput(cmdMkdir);
                devPrint("Remote mkdir output: " + mkdirOutput);
                String cmdUpdate = "cd " + remoteServersPath + "/" + serverName +
                        " && echo 'name = \"" + serverName + "\"' > server.toml" +
                        " && echo 'mc_version = \"" + serverVersion + "\"' >> server.toml" +
                        " && echo '[jar]' >> server.toml" +
                        " && echo 'type = \"" + serverType.toLowerCase() + "\"' >> server.toml" + getMcmanSettings();
                devPrint("Executing remote update command: " + cmdUpdate);
                String updateOutput = ssh.runRemoteCommandWithOutput(cmdUpdate);
                devPrint("Remote update output: " + updateOutput);
                writeSettings(ssh, remoteServersPath + "/" + serverName);
                String cmdBuild = "cd " + remoteServersPath + "/" + serverName + " && chmod +x " + remoteMcmanPath + " && " + remoteMcmanPath + " build --output .";
                devPrint("Executing remote build command: " + cmdBuild);
                String buildOutput = ssh.runRemoteCommandWithOutput(cmdBuild);
                devPrint("Remote build output: " + buildOutput);
                currentServers.add(newInfo);
                parent.saveServers();
                parent.saveRemoteHosts();
            }
        } catch (Exception e) {
            devPrint("Failed to create/update server: " + e.getMessage());
        }
    }

    @Override
    public void removed() {
        mc.getWindow().setScaleFactor(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
    }

    public void onClose() {
        mc.setScreen(parent);
    }
}
