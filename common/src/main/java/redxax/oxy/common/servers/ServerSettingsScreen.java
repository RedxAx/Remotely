package redxax.oxy.common.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.common.SSHManager;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.util.ImageUtil;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static redxax.oxy.common.Render.*;
import static redxax.oxy.common.config.Config.*;
import static redxax.oxy.common.util.DevUtil.devPrint;
import static redxax.oxy.common.util.ImageUtil.drawPixelArt;
import static redxax.oxy.common.util.SoundUtils.playClick;

public class ServerSettingsScreen extends Screen {
    private final MinecraftClient mc;
    private final ServerManagerScreen parent;
    private final String mode;
    private final String settingsRoot;
    private final boolean editServerMode;
    private ServerInfo serverInfo;
    private int currentTab;
    private final List<ServerSetting> settings = new ArrayList<>();
    private List<String> tabs = new ArrayList<>();
    private final Map<ServerSetting, Float> textInputScrollOffsets = new HashMap<>();
    private final Map<ServerSetting, Float> textInputTargetScrollOffsets = new HashMap<>();
    private final Map<ServerSetting, Integer> textSelectionStart = new HashMap<>();
    private final Map<ServerSetting, Integer> textSelectionEnd = new HashMap<>();
    private int selectedDropDown = -1;
    private float currentSettingsScroll = 0;
    private float targetSettingsScroll = 0;
    private final int rowHeight = 30;
    private ServerSetting draggedSlider = null;
    private ImageUtil.IconWithTooltip closeIcon, createIcon;
    public enum ServerSettingType {TOGGLE, SLIDER, SCROLL_SWITCH, TAB_SWITCH, TEXT}

    public ServerSettingsScreen(MinecraftClient mc, String mode, ServerManagerScreen parent, String settingsRoot, List<ServerSetting> customSettings) {
        this(mc, mode, parent, settingsRoot, customSettings, null);
    }

    public ServerSettingsScreen(MinecraftClient mc, String mode, ServerManagerScreen parent, String settingsRoot, List<ServerSetting> customSettings, ServerInfo serverInfo) {
        super(Text.literal("Server Settings"));
        this.mc = mc;
        this.parent = parent;
        this.mode = mode;
        this.settingsRoot = settingsRoot;
        this.editServerMode = mode.equalsIgnoreCase("editServer");
        this.serverInfo = serverInfo;
        if (customSettings != null && !customSettings.isEmpty()) {
            this.settings.addAll(customSettings);
        } else {
            settings.add(new ServerSetting("Error While Loading Settings", "none", "error", ServerSettingType.TEXT, "Hmmmmmmmmmmberger", "Error", "Please Try Again."));
        }
        if (editServerMode) {
            loadSettingsFromFiles();
            for (ServerSetting s : settings) {
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
        }
        initTextInputOffsets();
        recalcTabs();
    }

    private void initTextInputOffsets() {
        for (ServerSetting s : settings) {
            if (s.type == ServerSettingType.TEXT) {
                textInputScrollOffsets.put(s, 0f);
                textInputTargetScrollOffsets.put(s, 0f);
            }
        }
    }

    private void loadSettingsFromFiles() {
        for (ServerSetting s : settings) {
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
        for (ServerSetting s : settings) {
            if (dependencySatisfied(s)) {
                tabSet.add(s.tab);
            }
        }
        tabs = new ArrayList<>(tabSet);
        if (currentTab >= tabs.size()) {
            currentTab = 0;
        }
    }

    private boolean dependencySatisfied(ServerSetting s) {
        if (s.dependencyKey == null || s.dependencyKey.isEmpty()) return true;
        for (ServerSetting setting : settings) {
            if (setting.key.equals(s.dependencyKey)) {
                return setting.value.equals(s.dependencyValue);
            }
        }
        return false;
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, Config.backgroundColor, Config.backgroundColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        recalcTabs();
        if (Config.background) renderBackground(context, mouseX, mouseY, delta);
        int headerHeight = 30;
        context.fill(0, 0, this.width, headerHeight, Config.innerBackgroundColor);
        drawInnerBorder(context, 0, 0, this.width, headerHeight, Config.innerBorderColor);
        drawOuterBorder(context, 0, 0, this.width, headerHeight, globalOuterBorder);
        context.drawText(mc.textRenderer, Text.literal("Create New Server"), 10, 10, globalTextColor, Config.shadow);
        drawTabs(context, mc.textRenderer, tabs, currentTab, mouseX, mouseY, false, false);
        int buttonY = 5;
        int createButtonX = this.width - 46;
        boolean isCreateHovered = mouseX >= createButtonX && mouseX <= createButtonX + 18 && mouseY >= buttonY && mouseY <= buttonY + 18;
        drawSquareButton(context, createButtonX, buttonY, mc, isCreateHovered, mouseX, mouseY, editServerMode ? "Apply Changes" : "Create Server");
        drawPixelArt(context, createIcon.getImage(), width - 44 - 1, 6, 16, 16);
        int cancelButtonX = this.width - 23;
        boolean isCancelHovered = mouseX >= cancelButtonX && mouseX <= cancelButtonX + 18 && mouseY >= buttonY && mouseY <= buttonY + 18;
        drawSquareButton(context, cancelButtonX, buttonY, mc, isCancelHovered, mouseX, mouseY, "Cancel");
        drawPixelArt(context, closeIcon.getImage(), width - 21 - 1, 6, 16, 16);
        int tabAreaHeight = 18;
        int contentY = headerHeight + tabAreaHeight + 10;
        int contentX = 5;
        int contentWidth = this.width - 10;
        int contentHeight = this.height - contentY - 10;
        context.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, innerBackgroundColor);
        drawInnerBorder(context, contentX, contentY, contentWidth, contentHeight, innerBorderColor);
        drawOuterBorder(context, contentX, contentY, contentWidth, contentHeight, globalOuterBorder);
        List<ServerSetting> currentSettings = new ArrayList<>();
        for (ServerSetting s : settings) {
            if (s.tab.equals(tabs.get(currentTab)) && dependencySatisfied(s)) {
                currentSettings.add(s);
            }
        }
        int totalContentHeight = currentSettings.size() * rowHeight;
        currentSettingsScroll += (targetSettingsScroll - currentSettingsScroll) * delta * 0.2f;
        context.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        int widgetWidth = 180;
        int widgetAreaX = this.width - widgetWidth - 12;
        for (int i = 0; i < currentSettings.size(); i++) {
            int rowY = contentY + i * rowHeight - (int) currentSettingsScroll;
            if (rowY + rowHeight < contentY || rowY > contentY + contentHeight) continue;
            context.fill(contentX, rowY, contentX + contentWidth, rowY + rowHeight - 2, elementBackgroundColor);
            drawInnerBorder(context, contentX, rowY, contentWidth, rowHeight - 2, elementBorderColor);
            drawOuterBorder(context, contentX, rowY, contentWidth, rowHeight - 2, globalOuterBorder);
            String name = currentSettings.get(i).name;
            context.drawText(mc.textRenderer, Text.literal(name), contentX + 5, rowY + 5, globalTextColor, Config.shadow);
            context.drawText(mc.textRenderer, Text.literal(currentSettings.get(i).description), contentX + 5, rowY + 5 + mc.textRenderer.fontHeight + 2, Config.globalDarkTextColor, Config.shadow);
            ServerSetting s = currentSettings.get(i);
            int widgetY = rowY + (rowHeight - 20) / 2;
            boolean widgetHovered = mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth && mouseY >= rowY && mouseY <= rowY + 18;
            boolean toggleHovered = mouseX >= this.width - 40 - 12 && mouseX <= this.width - 12 && mouseY >= rowY && mouseY <= rowY + rowHeight;
            switch (s.type) {
                case TOGGLE -> drawToggle(context, mc, this.width - 40 - 12, widgetY - 1, s.name + s.key + s.description, s.value.equals("true"), toggleHovered);
                case SLIDER -> drawSlider(context, mc, widgetAreaX, widgetY, s.name + s.key + s.description + s.max + s.min, s.getIntValue(), s.min, s.max, widgetHovered, mouseX, mouseY, "Shift + Click To Input Text.");
                case SCROLL_SWITCH -> drawScrollSelector(context, mc, widgetAreaX, widgetY, s.options, s.getSelectedIndex(), widgetHovered);
                case TAB_SWITCH -> drawTabSwitch(context, mc, widgetAreaX, widgetY, s.name + s.key + s.description, s.options, s.getSelectedIndex(), mouseX, mouseY);
                case TEXT -> {
                    float currentScroll = textInputScrollOffsets.getOrDefault(s, 0f);
                    float targetScroll = textInputTargetScrollOffsets.getOrDefault(s, 0f);
                    drawTextInput(context, mc, widgetAreaX, widgetY, "", s.value, s.focused, s.cursorPos,
                            textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos),
                            widgetHovered, currentScroll, targetScroll);
                    textInputScrollOffsets.put(s, currentScroll);
                    textInputTargetScrollOffsets.put(s, targetScroll);
                }
            }
        }
        context.disableScissor();
        int maxScroll = Math.max(0, totalContentHeight - contentHeight);
        if ((int) currentSettingsScroll > 0) {
            context.fillGradient(contentX, contentY, contentX + contentWidth, contentY + 10, 0x55000000, 0x00000000);
        }
        if ((int) currentSettingsScroll < maxScroll) {
            context.fillGradient(contentX, contentY + contentHeight - 10, contentX + contentWidth, contentY + contentHeight, 0x00000000, 0x55000000);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ServerSetting s : settings) {
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
            List<ServerSetting> currentSettings = new ArrayList<>();
            for (ServerSetting s : settings) {
                if (s.tab.equals(tabs.get(currentTab))) {
                    currentSettings.add(s);
                }
            }
            for (int i = 0; i < currentSettings.size(); i++) {
                int rowY = contentY + i * rowHeight - (int) currentSettingsScroll;
                if (mouseX < widgetAreaX || mouseX > widgetAreaX + widgetWidth || mouseY < rowY || mouseY > rowY + rowHeight)
                    continue;
                ServerSetting s = currentSettings.get(i);
                switch (s.type) {
                    case TOGGLE -> {
                        int toggleX = this.width - 40 - 12;
                        int toggleWidth = 40;
                        boolean toggleHovered = mouseX >= toggleX && mouseX <= toggleX + toggleWidth && mouseY >= rowY && mouseY <= rowY + rowHeight;
                        if (toggleHovered) {
                            playClick();
                            s.value = s.value.equals("true") ? "false" : "true";
                        }
                    }
                    case SLIDER -> {
                        boolean sliderHovered = mouseX >= widgetAreaX && mouseX <= widgetAreaX + widgetWidth;
                        if (sliderHovered) {
                            playClick();
                            float relativeX = (float) (mouseX - widgetAreaX);
                            relativeX = Math.max(0, Math.min(relativeX, widgetWidth));
                            float percent = relativeX / widgetWidth;
                            int newVal = s.min + (int) (percent * (s.max - s.min));
                            s.value = String.valueOf(newVal);
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
                            } else if (mouseX > centerX + 10) {
                                int currentIndex = s.getSelectedIndex();
                                if (currentIndex < s.options.size() - 1) {
                                    s.value = s.options.get(currentIndex + 1);
                                } else {
                                    s.value = s.options.get(0);
                                }
                            } else {
                                selectedDropDown = -1;
                            }
                        } else {
                            selectedDropDown = s.index;
                        }
                    }
                    case TAB_SWITCH -> {
                        int barWidth = widgetWidth;
                        double relativeX = mouseX - widgetAreaX;
                        double relativeY = mouseY - rowY;
                        if (relativeX >= 0 && relativeX <= barWidth && relativeY >= 0 && relativeY <= 18) {
                            playClick();
                            int segmentCount = s.options.size();
                            double segmentWidth = (double) barWidth / segmentCount;
                            int newIndex = (int) (relativeX / segmentWidth);
                            s.setOption(newIndex);
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
        for (ServerSetting s : settings) {
            if (s.type == ServerSettingType.TEXT && s.focused) {
                int widgetWidth = 180;
                int widgetAreaX = this.width - widgetWidth - 12;
                List<ServerSetting> currentSettings = new ArrayList<>();
                for (ServerSetting setting : settings) {
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
        int headerHeight = 30;
        int tabAreaHeight = 18;
        int contentY = headerHeight + tabAreaHeight + 10;
        int contentHeight = this.height - contentY - 10;
        List<ServerSetting> currentSettings = new ArrayList<>();
        for (ServerSetting s : settings) {
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
        for (ServerSetting s : settings) {
            if (s.type == ServerSettingType.TEXT && s.focused) {
                boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
                boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
                if (ctrl && keyCode == GLFW.GLFW_KEY_BACKSPACE && s.cursorPos > 0) {
                    int pos = s.cursorPos;
                    while (pos > 0 && s.value.charAt(pos - 1) == ' ') pos--;
                    while (pos > 0 && s.value.charAt(pos - 1) != ' ') pos--;
                    s.value = s.value.substring(0, pos) + s.value.substring(s.cursorPos);
                    s.cursorPos = pos;
                    textSelectionStart.put(s, pos);
                    textSelectionEnd.put(s, pos);
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
                } else if (!ctrl && keyCode == GLFW.GLFW_KEY_DELETE && s.cursorPos < s.value.length()) {
                    s.value = s.value.substring(0, s.cursorPos) + s.value.substring(s.cursorPos + 1);
                } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && s.cursorPos > 0) {
                    s.value = s.value.substring(0, s.cursorPos - 1) + s.value.substring(s.cursorPos);
                    s.cursorPos--;
                    textSelectionStart.put(s, s.cursorPos);
                    textSelectionEnd.put(s, s.cursorPos);
                } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && ctrl) {
                    s.value = "";
                    s.cursorPos = 0;
                    textSelectionStart.put(s, 0);
                    textSelectionEnd.put(s, 0);
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

    private void handleClipboardCopy(ServerSetting s) {
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

    private void handleClipboardPaste(ServerSetting s) {
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
                }
            }
        } catch (Exception e) {
            devPrint("Clipboard access error: " + e.getMessage());
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (ServerSetting s : settings) {
            if (s.type == ServerSettingType.TEXT && s.focused) {
                int selStart = Math.min(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
                int selEnd = Math.max(textSelectionStart.getOrDefault(s, s.cursorPos), textSelectionEnd.getOrDefault(s, s.cursorPos));
                if (selStart != selEnd) {
                    s.value = s.value.substring(0, selStart) + s.value.substring(selEnd);
                    s.cursorPos = selStart;
                    textSelectionStart.put(s, selStart);
                    textSelectionEnd.put(s, selStart);
                }
                if (chr == 13 || chr == 27) return true;
                if (!Character.isISOControl(chr)) {
                    s.value = s.value.substring(0, s.cursorPos) + chr + s.value.substring(s.cursorPos);
                    s.cursorPos++;
                    textSelectionStart.put(s, s.cursorPos);
                    textSelectionEnd.put(s, s.cursorPos);
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
        for (ServerSetting s : settings) {
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
        Map<String, List<ServerSetting>> fileGroups = new HashMap<>();
        for (ServerSetting st : settings) {
            if (!st.file.equals("none") && dependencySatisfied(st) && !st.value.isEmpty()) {
                fileGroups.computeIfAbsent(st.file, k -> new ArrayList<>()).add(st);
            }
        }
        for (String fileName : fileGroups.keySet()) {
            List<String> lines = new ArrayList<>();
            for (ServerSetting st : fileGroups.get(fileName)) {
                if (st.key.equalsIgnoreCase("gamemode") || st.key.equalsIgnoreCase("difficulty")) {
                    lines.add(st.key + "=" + st.value.toLowerCase());
                } else {
                    if (dependencySatisfied(st) && !st.value.isEmpty())
                        lines.add(st.key + "=" + st.value);
                }
            }
            if (manager == null) {
                try {
                    Path filePath = Paths.get(basePath, fileName);
                    Files.write(filePath, String.join("\n", lines).getBytes());
                    devPrint("Wrote settings to local file: " + filePath);
                } catch (Exception e) {
                    devPrint("Error writing local settings to file " + fileName + ": " + e.getMessage());
                }
            } else {
                manager.writeRemoteFile(basePath + "/" + fileName, String.join("\n", lines));
                devPrint("Wrote settings to remote file: " + basePath + "/" + fileName);
            }
        }
    }


    private void createServer() {
        String serverName = "";
        String serverType = "";
        String serverVersion = "";
        for (ServerSetting s : settings) {
            if (s.name.equals("Server Name")) serverName = s.value.trim();
            if (s.name.equals("Server Type")) serverType = s.value.trim();
            if (s.name.equals("Server Version")) serverVersion = s.value.trim();
        }
        if (serverName.isEmpty()) serverName = "MyServer";
        if (serverVersion.isEmpty()) serverVersion = "latest";
        int tabIndex = parent.getActiveTabIndex();
        List<ServerInfo> currentServers = parent.getCurrentServers();
        try {
            if (editServerMode && serverInfo != null) {
                devPrint("Editing server: " + serverName);
                if (!serverInfo.isRemote) {
                    String mcmanPath = ensureLocalMcman();
                    devPrint("Running local edit command...");
                    ProcessBuilder pb = new ProcessBuilder(mcmanPath, "init", "--edit", "--name", serverName);
                    pb.directory(new File(settingsRoot));
                    Process proc = pb.start();
                    proc.waitFor();
                    ProcessBuilder pbBuild = new ProcessBuilder(mcmanPath, "build", "--output", ".");
                    pbBuild.directory(new File(settingsRoot + File.separator + serverName));
                    Process procBuild = pbBuild.start();
                    procBuild.waitFor();
                    devPrint("Local server edited and built.");
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
                    String cmdBuild = "cd " + remoteServersPath + "/" + serverName + " && " + remoteMcmanPath + " build --output .";
                    devPrint("Executing remote build command: " + cmdBuild);
                    String buildOutput = ssh.runRemoteCommandWithOutput(cmdBuild);
                    devPrint("Remote build output: " + buildOutput);
                }
                serverInfo.name = serverName;
                serverInfo.type = serverType;
                serverInfo.version = serverVersion;
                parent.saveServers();
                parent.saveRemoteHosts();
            } else {
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
                    for (ServerSetting s : settings) {
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
                    Process procBuild = pbBuild.start();
                    procBuild.waitFor();
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
                    String cmdBuild = "cd " + remoteServersPath + "/" + serverName + " && " + remoteMcmanPath + " build --output .";
                    devPrint("Executing remote build command: " + cmdBuild);
                    String buildOutput = ssh.runRemoteCommandWithOutput(cmdBuild);
                    devPrint("Remote build output: " + buildOutput);
                    currentServers.add(newInfo);
                    parent.saveServers();
                    parent.saveRemoteHosts();
                }
            }
        } catch (Exception e) {
            devPrint("Failed to create/update server: " + e.getMessage());
        }
        onClose();
    }

    public void onClose() {
        mc.setScreen(parent);
    }
}
