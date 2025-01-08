package redxax.oxy.explorer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.*;
import redxax.oxy.servers.ServerInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static redxax.oxy.Render.*;

public class FileEditorScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private MultiLineTextEditor textEditor;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private int backButtonX;
    private int backButtonY;
    private int saveButtonX;
    private int saveButtonY;
    private int btnW;
    private int btnH;
    private static final double FAST_SCROLL_FACTOR = 3.0;
    private static final double HORIZONTAL_SCROLL_FACTOR = 10.0;
    private List<Tab> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private final int TAB_HEIGHT = 18;
    private final int TAB_PADDING = 5;
    private final int TAB_GAP = 5;
    private StringBuilder searchText = new StringBuilder();
    private boolean searchBarFocused = false;
    private List<Position> searchResults = new ArrayList<>();
    private int currentSearchIndex = 0;
    private int searchBarWidth = 200;
    private int searchBarHeight = 20;
    private int clearSearchButtonWidth = 20;

    class Position {
        int line;
        int start;
        int end;

        Position(int line, int start, int end) {
            this.line = line;
            this.start = start;
            this.end = end;
        }
    }

    class Tab {
        Path path;
        String name;
        TabTextAnimator textAnimator;
        MultiLineTextEditor textEditor;
        boolean unsaved;
        String originalContent;

        Tab(Path path) {
            this.path = path;
            this.name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
            this.textAnimator = new TabTextAnimator(this.name, 0, 30);
            this.textAnimator.start();
            loadFileContent();
            this.unsaved = false;
        }

        public void markUnsaved() {
            this.unsaved = true;
        }

        public void checkIfChanged(List<String> lines) {
            String joined = String.join("\n", lines);
            if (joined.equals(originalContent)) {
                this.unsaved = false;
            } else {
                this.unsaved = true;
            }
        }

        private void loadFileContent() {
            ArrayList<String> fileContent = new ArrayList<>();
            if (serverInfo.isRemote) {
                try {
                    if (serverInfo.remoteSSHManager == null) {
                        serverInfo.remoteSSHManager = new redxax.oxy.SSHManager(serverInfo);
                        serverInfo.remoteSSHManager.connectToRemoteHost(serverInfo.remoteHost.getUser(), serverInfo.remoteHost.ip, serverInfo.remoteHost.port, serverInfo.remoteHost.password);
                        serverInfo.remoteSSHManager.connectSFTP();
                    } else if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
                        serverInfo.remoteSSHManager.connectSFTP();
                    }
                    String remotePath = path.toString().replace("\\", "/");
                    String content = serverInfo.remoteSSHManager.readRemoteFile(remotePath);
                    String[] lines = content.split("\\r?\\n");
                    Collections.addAll(fileContent, lines);
                } catch (Exception e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File load error (remote): " + e.getMessage() + "\n");
                    }
                }
            } else {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    reader.lines().forEach(fileContent::add);
                } catch (IOException e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File load error: " + e.getMessage() + "\n");
                    }
                }
            }
            this.textEditor = new MultiLineTextEditor(minecraftClient, fileContent, path.getFileName().toString(), this);
            this.originalContent = String.join("\n", fileContent);
        }

        public void saveFile() {
            ArrayList<String> newContent = new ArrayList<>(textEditor.getLines());
            if (serverInfo.isRemote) {
                try {
                    if (serverInfo.remoteSSHManager == null) {
                        serverInfo.remoteSSHManager = new redxax.oxy.SSHManager(serverInfo);
                        serverInfo.remoteSSHManager.connectToRemoteHost(serverInfo.remoteHost.getUser(), serverInfo.remoteHost.ip, serverInfo.remoteHost.port, serverInfo.remoteHost.password);
                        serverInfo.remoteSSHManager.connectSFTP();
                    } else if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
                        serverInfo.remoteSSHManager.connectSFTP();
                    }
                    String remotePath = path.toString().replace("\\", "/");
                    String joined = String.join("\n", newContent);
                    serverInfo.remoteSSHManager.writeRemoteFile(remotePath, joined);
                    this.unsaved = false;
                    this.originalContent = joined;
                } catch (Exception e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File save error (remote): " + e.getMessage() + "\n");
                    }
                }
            } else {
                try {
                    Files.write(path, newContent);
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File saved: " + path + "\n");
                    }
                    this.unsaved = false;
                    this.originalContent = String.join("\n", newContent);
                } catch (IOException e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File save error: " + e.getMessage() + "\n");
                    }
                }
            }
        }
    }

    public FileEditorScreen(MinecraftClient mc, Screen parent, Path filePath, ServerInfo info) {
        super(Text.literal("File Editor"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
        Tab initialTab = new Tab(filePath);
        tabs.add(initialTab);
        this.textEditor = initialTab.textEditor;
    }

    @Override
    protected void init() {
        super.init();
        this.textEditor.init(5, 60, this.width - 10, this.height - 70);
        btnW = 50;
        btnH = 20;
        saveButtonX = this.width - 60;
        saveButtonY = 5;
        backButtonX = saveButtonX - (btnW + 10);
        backButtonY = saveButtonY;
        List<Path> loadedTabs = RemotelyClient.INSTANCE.loadFileEditorTabs();
        for (Path path : loadedTabs) {
            boolean tabExists = false;
            for (Tab tab : tabs) {
                if (tab.path.equals(path)) {
                    tabExists = true;
                    break;
                }
            }
            if (!tabExists) {
                Tab tab = new Tab(path);
                tabs.add(tab);
                tab.textEditor.init(5, 60, this.width - 10, this.height - 70);
            }
        }
        if (!tabs.isEmpty()) {
            this.textEditor = tabs.get(0).textEditor;
        }
    }

    @Override
    public void close() {
        RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
        minecraftClient.setScreen(parent);
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (searchBarFocused) {
            if (chr == '\n' || chr == '\r') {
                handleSearchEnter();
                return true;
            }
            if (chr == 27) {
                searchBarFocused = false;
                return true;
            }
            searchText.append(chr);
            updateSearchResults();
            return true;
        }
        return tabs.get(currentTabIndex).textEditor.charTyped(chr, keyCode) || super.charTyped(chr, keyCode);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_F) {
            searchBarFocused = true;
            String selected = tabs.get(currentTabIndex).textEditor.getSelectedText();
            if (!selected.isEmpty()) {
                searchText.setLength(0);
                searchText.append(selected);
            }
            updateSearchResults();
            return true;
        }
        if (searchBarFocused) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchText.length() > 0) {
                        searchText.deleteCharAt(searchText.length() - 1);
                        updateSearchResults();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    handleSearchEnter();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    searchBarFocused = false;
                    return true;
                }
            }
            return true;
        }
        if (keyCode == this.minecraftClient.options.backKey.getDefaultKey().getCode() && keyCode != GLFW.GLFW_KEY_S) {
            close();
            return true;
        }
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_Z) {
            tabs.get(currentTabIndex).textEditor.undo();
            return true;
        }
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_Y) {
            tabs.get(currentTabIndex).textEditor.redo();
            return true;
        }
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_A) {
            tabs.get(currentTabIndex).textEditor.selectAll();
            return true;
        }
        return tabs.get(currentTabIndex).textEditor.keyPressed(keyCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void handleSearchEnter() {
        if (searchResults.isEmpty()) return;
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size();
        Position pos = searchResults.get(currentSearchIndex);
        tabs.get(currentTabIndex).textEditor.setCursor(pos.line, pos.start);
    }

    private void updateSearchResults() {
        searchResults.clear();
        String query = searchText.toString().toLowerCase();
        if (query.isEmpty()) {
            textEditor.setSearchResults(searchResults);
            return;
        }
        List<String> lines = textEditor.getLines();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).toLowerCase();
            int index = 0;
            while ((index = line.indexOf(query, index)) != -1) {
                searchResults.add(new Position(i, index, index + query.length()));
                index += query.length();
            }
        }
        currentSearchIndex = 0;
        textEditor.setSearchResults(searchResults);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int searchBarX = (this.width - searchBarWidth) / 2;
        int searchBarY = 5;
        int clearSearchButtonX = searchBarX + searchBarWidth;
        if (mouseX >= searchBarX && mouseX <= searchBarX + searchBarWidth && mouseY >= searchBarY && mouseY <= searchBarY + searchBarHeight) {
            searchBarFocused = true;
            return true;
        } else {
            if (mouseX >= clearSearchButtonX && mouseX <= clearSearchButtonX + clearSearchButtonWidth && mouseY >= searchBarY && mouseY <= searchBarY + searchBarHeight) {
                searchText.setLength(0);
                updateSearchResults();
                return true;
            }
            searchBarFocused = false;
        }
        boolean clickedTab = false;
        int titleBarHeight = 30;
        int tabBarY = titleBarHeight + 5;
        int tabBarHeight = TAB_HEIGHT;
        int tabX = 5;
        int tabY = tabBarY;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = minecraftClient.textRenderer.getWidth(tab.name) + 2 * TAB_PADDING;
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabBarHeight) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    tabs.remove(i);
                    if (i == currentTabIndex) {
                        currentTabIndex = Math.max(0, currentTabIndex - 1);
                    }
                    if (!tabs.isEmpty()) {
                        this.textEditor = tabs.get(currentTabIndex).textEditor;
                    } else {
                        close();
                    }
                    RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
                    return true;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (currentTabIndex != i) {
                        if (tabs.get(currentTabIndex).unsaved) {
                            tabs.get(currentTabIndex).saveFile();
                        }
                        currentTabIndex = i;
                        this.textEditor = tabs.get(currentTabIndex).textEditor;
                        RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
                    }
                    clickedTab = true;
                    break;
                }
            }
            tabX += tabWidth + TAB_GAP;
        }
        if (clickedTab) {
            return true;
        }
        boolean clickedSave = mouseX >= saveButtonX && mouseX <= saveButtonX + btnW && mouseY >= saveButtonY && mouseY <= saveButtonY + btnH && button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (clickedSave) {
            tabs.get(currentTabIndex).saveFile();
            return true;
        }
        boolean clickedBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH && button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (clickedBack) {
            close();
            return true;
        }
        return tabs.get(currentTabIndex).textEditor.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return tabs.get(currentTabIndex).textEditor.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return tabs.get(currentTabIndex).textEditor.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        long windowHandle = minecraftClient.getWindow().getHandle();
        boolean shiftHeld = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean ctrlHeld = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (shiftHeld) {
            tabs.get(currentTabIndex).textEditor.scrollHoriz((int) (-vertAmount) * (int) HORIZONTAL_SCROLL_FACTOR);
        } else if (ctrlHeld) {
            tabs.get(currentTabIndex).textEditor.scrollVert((int) (-vertAmount) * (int) FAST_SCROLL_FACTOR);
        } else {
            tabs.get(currentTabIndex).textEditor.scrollVert((int) (-vertAmount));
        }
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int titleBarHeight = 30;
        context.fill(0, 0, this.width, titleBarHeight, 0xFF222222);
        drawInnerBorder(context, 0, 0, this.width, titleBarHeight, 0xFF333333);
        String titleText = "Remotely - File Editor";
        context.drawText(this.textRenderer, Text.literal(titleText), 10, 10, textColor, Config.shadow);

        int searchBarX = (this.width - searchBarWidth) / 2;
        int searchBarY = 5;
        int searchBarH = searchBarHeight;
        int searchBarW = searchBarWidth;
        int searchBarColor = searchBarFocused ? greenDark : elementBg;
        context.fill(searchBarX, searchBarY, searchBarX + searchBarW, searchBarY + searchBarH, searchBarColor);
        drawInnerBorder(context, searchBarX, searchBarY, searchBarW, searchBarH, searchBarFocused ? greenBright : elementBorder);
        context.drawText(this.textRenderer, Text.literal(searchText.toString()), searchBarX + 5, searchBarY + 5, textColor, Config.shadow);
        if (searchBarFocused && searchText.length() == 0) {
            context.drawText(this.textRenderer, Text.literal("Search..."), searchBarX + 5, searchBarY + 5, 0xFF888888, false);
        }
        if (searchText.length() > 0) {
            int clearButtonX = searchBarX + searchBarW;
            context.drawText(this.textRenderer, Text.literal("x"), clearButtonX + 4, searchBarY + 5, redColor, true);
        }

        int tabBarY = titleBarHeight + 5;
        int tabBarHeight = TAB_HEIGHT;
        int tabX = 5;
        int tabY = tabBarY;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = minecraftClient.textRenderer.getWidth(tab.name) + 2 * TAB_PADDING;
            boolean isActive = (i == currentTabIndex);
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabBarHeight;
            int bgColor = isActive ? (tab.unsaved ? redBg : 0xFF0b371c) : (isHovered ? highlightColor : 0xFF2C2C2C);
            context.fill(tabX, tabY, tabX + tabWidth, tabY + tabBarHeight, bgColor);
            drawInnerBorder(context, tabX, tabY, tabWidth, tabBarHeight, isActive ? (tab.unsaved ? redBright : 0xFFd6f264) : (isHovered ? 0xFF00000 : 0xFF444444));
            context.drawText(this.textRenderer, Text.literal(tab.unsaved ? tab.name + "*" : tab.name), tabX + TAB_PADDING, tabY + 5, isHovered ? greenBright : textColor, Config.shadow);
            context.fill(tabX, tabY + tabBarHeight, tabX + tabWidth, tabY + tabBarHeight + 2, isActive ? 0xFF0b0b0b : 0xFF000000);
            tabX += tabWidth + TAB_GAP;
        }

        int editorY = tabBarY + tabBarHeight + 5;
        int editorHeight = this.height - editorY - 10;
        int editorX = 5;
        int editorWidth = this.width - 10;
        context.fill(editorX, editorY, editorX + editorWidth, editorY + editorHeight, lighterColor);
        drawInnerBorder(context, editorX, editorY, editorWidth, editorHeight, borderColor);
        tabs.get(currentTabIndex).textEditor.render(context, mouseX, mouseY, delta);

        int buttonX = this.width - buttonW - 10;
        int ButtonY = 5;
        boolean hovered = mouseX >= buttonX && mouseX <= buttonX + Render.buttonW && mouseY >= ButtonY && mouseY <= ButtonY + Render.buttonH;
        Render.drawCustomButton(context, buttonX, ButtonY, "Save", minecraftClient, hovered, false, textColor, greenBright);
        buttonX = buttonX - (buttonW + 10);
        hovered = mouseX >= buttonX && mouseX <= buttonX + Render.buttonW && mouseY >= ButtonY && mouseY <= ButtonY + Render.buttonH;
        Render.drawCustomButton(context, buttonX, ButtonY, "Back", minecraftClient, hovered, false, textColor, greenBright);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static class MultiLineTextEditor {
        private final MinecraftClient mc;
        private final Tab parentTab;
        public final ArrayList<String> lines;
        private final String fileName;
        private int x;
        private int y;
        private int width;
        private int height;
        private double smoothScrollOffsetVert = 0;
        private double smoothScrollOffsetHoriz = 0;
        private double targetScrollOffsetVert = 0;
        private double targetScrollOffsetHoriz = 0;
        private double scrollSpeed = 0.3;
        private int cursorLine;
        private int cursorPos;
        private int selectionStartLine = -1;
        private int selectionStartChar = -1;
        private int selectionEndLine = -1;
        private int selectionEndChar = -1;
        private final ArrayDeque<EditorState> undoStack = new ArrayDeque<>();
        private final ArrayDeque<EditorState> redoStack = new ArrayDeque<>();
        private int textPadding = 4;
        private int paddingTop = 5;
        private int paddingRight = 5;
        private int cursor = 0xFFFF0000;
        private float cursorOpacity = 1.0f;
        private boolean cursorFadingOut = true;
        private long lastCursorBlinkTime = 0;
        private static final long CURSOR_BLINK_INTERVAL = 30;
        private static long lastLeftClickTime = 0;
        private static int clickCount = 0;
        private List<Position> searchResults = new ArrayList<>();

        public MultiLineTextEditor(MinecraftClient mc, ArrayList<String> content, String fileName, Tab parentTab) {
            this.mc = mc;
            this.lines = new ArrayList<>(content);
            this.fileName = fileName;
            this.parentTab = parentTab;
        }

        public void init(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.smoothScrollOffsetVert = 0;
            this.smoothScrollOffsetHoriz = 0;
            this.targetScrollOffsetVert = 0;
            this.targetScrollOffsetHoriz = 0;
            this.cursorLine = 0;
            this.cursorPos = 0;
            pushState();
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            smoothScrollOffsetVert += (targetScrollOffsetVert - smoothScrollOffsetVert) * scrollSpeed;
            smoothScrollOffsetHoriz += (targetScrollOffsetHoriz - smoothScrollOffsetHoriz) * scrollSpeed;
            context.enableScissor(x, y, x + width, y + height);
            int lineHeight = mc.textRenderer.fontHeight + 2;
            int visibleLines = height / lineHeight;
            for (int i = 0; i < visibleLines; i++) {
                int lineIndex = (int) Math.floor(smoothScrollOffsetVert / lineHeight) + i;
                if (lineIndex < 0 || lineIndex >= lines.size()) continue;
                int renderY = y + i * lineHeight - (int) smoothScrollOffsetVert % lineHeight;
                String text = lines.get(lineIndex);
                Text syntaxColoredLine = SyntaxHighlighter.highlight(text, fileName);
                context.drawText(mc.textRenderer, syntaxColoredLine, x + textPadding - (int) smoothScrollOffsetHoriz, renderY, 0xFFFFFF, Config.shadow);
                if (isLineSelected(lineIndex)) {
                    drawSelection(context, lineIndex, renderY, text, textPadding);
                }
                for (Position pos : searchResults) {
                    if (pos.line == lineIndex) {
                        int startX = mc.textRenderer.getWidth(text.substring(0, pos.start));
                        int endX = mc.textRenderer.getWidth(text.substring(0, pos.end));
                        context.fill(x + textPadding - (int) smoothScrollOffsetHoriz + startX, renderY, x + textPadding - (int) smoothScrollOffsetHoriz + endX, renderY + lineHeight - 2, 0x80d6f264);
                    }
                }
                if (lineIndex == cursorLine && !hasSelection()) {
                    int cursorX = mc.textRenderer.getWidth(text.substring(0, Math.min(cursorPos, text.length())));
                    int cy = renderY;
                    int cursorColor = CursorUtils.blendColor();
                    context.fill(x + textPadding - (int) smoothScrollOffsetHoriz + cursorX, cy, x + textPadding - (int) smoothScrollOffsetHoriz + cursorX + 1, cy + lineHeight - 2, cursorColor);
                }
            }
            context.disableScissor();
        }

        private int getMaxLineWidth() {
            int maxWidth = 0;
            for (String line : lines) {
                int lineWidth = mc.textRenderer.getWidth(line);
                if (lineWidth > maxWidth) {
                    maxWidth = lineWidth;
                }
            }
            return maxWidth;
        }

        private boolean charTyped(char chr, int keyCode) {
            if (chr == '\n' || chr == '\r') {
                deleteSelection();
                pushState();
                if (cursorLine >= 0 && cursorLine < lines.size()) {
                    String oldLine = lines.get(cursorLine);
                    String before = oldLine.substring(0, Math.min(cursorPos, oldLine.length()));
                    String after = oldLine.substring(Math.min(cursorPos, oldLine.length()));
                    lines.set(cursorLine, before);
                    lines.add(cursorLine + 1, after);
                    cursorLine++;
                    cursorPos = 0;
                } else {
                    lines.add("");
                    cursorLine = lines.size() - 1;
                    cursorPos = 0;
                }
                scrollToCursor();
                parentTab.checkIfChanged(lines);
                return true;
            } else if (chr >= 32 && chr != 127) {
                deleteSelection();
                pushState();
                if (cursorLine < 0) cursorLine = 0;
                if (cursorLine >= lines.size()) lines.add("");
                String line = lines.get(cursorLine);
                int pos = Math.min(cursorPos, line.length());
                String newLine = line.substring(0, pos) + chr + line.substring(pos);
                lines.set(cursorLine, newLine);
                cursorPos++;
                scrollToCursor();
                parentTab.checkIfChanged(lines);
                return true;
            }
            parentTab.checkIfChanged(lines);
            return false;
        }

        public boolean keyPressed(int keyCode, int modifiers) {
            boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (ctrlHeld) {
                        deleteWord();
                        pushState();
                        scrollToCursor();
                        return true;
                    }
                    if (hasSelection()) {
                        deleteSelection();
                        pushState();
                        scrollToCursor();
                        return true;
                    }
                    if (cursorLine < 0 || cursorLine >= lines.size()) return true;
                    pushState();
                    if (cursorPos > 0) {
                        String line = lines.get(cursorLine);
                        String newLine = line.substring(0, cursorPos - 1) + line.substring(cursorPos);
                        lines.set(cursorLine, newLine);
                        cursorPos--;
                    } else {
                        if (cursorLine > 0) {
                            int oldLen = lines.get(cursorLine - 1).length();
                            lines.set(cursorLine - 1, lines.get(cursorLine - 1) + lines.get(cursorLine));
                            lines.remove(cursorLine);
                            cursorLine--;
                            cursorPos = oldLen;
                        }
                    }
                    scrollToCursor();
                    parentTab.checkIfChanged(lines);
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    if (ctrlHeld) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
                            clearSelection();
                        }
                        int newPos = moveCursorLeftWord();
                        cursorPos = newPos;
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    } else {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if (modifiers == 0) {
                            clearSelection();
                        }
                        if (cursorPos > 0) {
                            cursorPos--;
                        } else {
                            if (cursorLine > 0) {
                                cursorLine--;
                                cursorPos = lines.get(cursorLine).length();
                            }
                        }
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (ctrlHeld) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
                            clearSelection();
                        }
                        int newPos = moveCursorRightWord();
                        cursorPos = newPos;
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    } else {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if (modifiers == 0) {
                            clearSelection();
                        }
                        if (cursorPos < lines.get(cursorLine).length()) {
                            cursorPos++;
                        } else {
                            if (cursorLine < lines.size() - 1) {
                                cursorLine++;
                                cursorPos = 0;
                            }
                        }
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    if (cursorLine < lines.size() - 1) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if (modifiers == 0) {
                            clearSelection();
                        }
                        cursorLine++;
                        cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    if (cursorLine > 0) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if (modifiers == 0) {
                            clearSelection();
                        }
                        cursorLine--;
                        cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    if (ctrlHeld) {
                        parentTab.markUnsaved();
                        deleteSelection();
                        pushState();
                        String clipboard = mc.keyboard.getClipboard();
                        for (char c : clipboard.toCharArray()) {
                            if (c == '\n' || c == '\r') {
                                if (cursorLine < lines.size()) {
                                    String oldLine = lines.get(cursorLine);
                                    String beforeCursor = oldLine.substring(0, Math.min(cursorPos, oldLine.length()));
                                    String afterCursor = oldLine.substring(Math.min(cursorPos, oldLine.length()));
                                    lines.set(cursorLine, beforeCursor);
                                    lines.add(cursorLine + 1, afterCursor);
                                }
                                cursorLine++;
                                cursorPos = 0;
                            } else {
                                if (cursorLine < 0) cursorLine = 0;
                                if (cursorLine >= lines.size()) lines.add("");
                                String line = lines.get(cursorLine);
                                int pos = Math.min(cursorPos, line.length());
                                String newLine = line.substring(0, pos) + c + line.substring(pos);
                                lines.set(cursorLine, newLine);
                                cursorPos++;
                            }
                        }
                        scrollToCursor();
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_C -> {
                    if (ctrlHeld && hasSelection()) {
                        parentTab.markUnsaved();
                        copySelectionToClipboard();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (ctrlHeld && hasSelection()) {
                        parentTab.markUnsaved();
                        copySelectionToClipboard();
                        deleteSelection();
                        pushState();
                        scrollToCursor();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    parentTab.markUnsaved();
                    deleteSelection();
                    pushState();
                    if (cursorLine < 0) {
                        cursorLine = 0;
                    }
                    if (cursorLine >= lines.size()) {
                        lines.add("");
                    } else {
                        String currentLine = lines.get(cursorLine);
                        String beforeCursor = currentLine.substring(0, cursorPos);
                        String afterCursor = currentLine.substring(cursorPos);
                        lines.set(cursorLine, beforeCursor);
                        lines.add(cursorLine + 1, afterCursor);
                    }
                    cursorLine++;
                    cursorPos = 0;
                    scrollToCursor();
                    return true;
                }
            }
            return false;
        }

        private int moveCursorLeftWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return 0;
            String line = lines.get(cursorLine);
            int index = Math.min(cursorPos - 1, line.length() - 1);
            while (index >= 0 && Character.isWhitespace(line.charAt(index))) {
                index--;
            }
            while (index >= 0 && !Character.isWhitespace(line.charAt(index))) {
                index--;
            }
            if (index < 0 && cursorLine > 0) {
                cursorLine--;
                cursorPos = lines.get(cursorLine).length();
                return cursorPos;
            }
            return Math.max(0, index + 1);
        }

        private int moveCursorRightWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return 0;
            String line = lines.get(cursorLine);
            int index = cursorPos;
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            while (index < line.length() && !Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            if (index >= line.length() && cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorPos = 0;
                return cursorPos;
            }
            return index;
        }

        private void deleteWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return;
            String line = lines.get(cursorLine);
            int startPos = cursorPos;
            while (startPos > 0 && Character.isWhitespace(line.charAt(startPos - 1))) {
                startPos--;
            }
            while (startPos > 0 && !Character.isWhitespace(line.charAt(startPos - 1))) {
                startPos--;
            }
            String newLine = line.substring(0, startPos) + line.substring(cursorPos);
            lines.set(cursorLine, newLine);
            cursorPos = startPos;
            scrollToCursor();
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLeftClickTime < 250) {
                    clickCount++;
                } else {
                    clickCount = 1;
                }
                lastLeftClickTime = currentTime;
                clearSelection();
                int lineHeight = mc.textRenderer.fontHeight + 2;
                int localY = (int) mouseY - y;
                int clickedLine = (int) Math.floor(smoothScrollOffsetVert / lineHeight) + (localY / lineHeight);
                if (clickedLine < 0) clickedLine = 0;
                if (clickedLine >= lines.size()) clickedLine = lines.size() - 1;
                int localX = (int) mouseX - (x + textPadding) + (int) smoothScrollOffsetHoriz;
                String text = lines.get(clickedLine);
                int cPos = 0;
                int widthSum = 0;
                for (char c : text.toCharArray()) {
                    int charWidth = mc.textRenderer.getWidth(String.valueOf(c));
                    if (widthSum + charWidth / 2 >= localX) break;
                    widthSum += charWidth;
                    cPos++;
                }
                cursorLine = clickedLine;
                cursorPos = cPos;
                if (clickCount == 2) {
                    int wordStart = cursorPos;
                    int wordEnd = cursorPos;
                    while (wordStart > 0 && !Character.isWhitespace(text.charAt(wordStart - 1)) && !"=\"'".contains(String.valueOf(text.charAt(wordStart - 1)))) {
                        wordStart--;
                    }
                    while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd)) && !"=\"'".contains(String.valueOf(text.charAt(wordEnd)))) {
                        wordEnd++;
                    }
                    selectionStartLine = cursorLine;
                    selectionStartChar = wordStart;
                    selectionEndLine = cursorLine;
                    selectionEndChar = wordEnd;
                } else if (clickCount >= 3) {
                    selectionStartLine = cursorLine;
                    selectionStartChar = 0;
                    selectionEndLine = cursorLine;
                    selectionEndChar = text.length();
                } else {
                    selectionStartLine = cursorLine;
                    selectionStartChar = cursorPos;
                    selectionEndLine = cursorLine;
                    selectionEndChar = cursorPos;
                }
            }
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int lineHeight = mc.textRenderer.fontHeight + 2;
                int localY = (int) mouseY - y;
                int dragLine = (int) Math.floor(smoothScrollOffsetVert / lineHeight) + (localY / lineHeight);
                if (dragLine < 0) dragLine = 0;
                if (dragLine >= lines.size()) dragLine = lines.size() - 1;
                int localX = (int) mouseX - (x + textPadding) + (int) smoothScrollOffsetHoriz;
                String text = lines.get(dragLine);
                int cPos = 0;
                int widthSum = 0;
                for (char c : text.toCharArray()) {
                    int charWidth = mc.textRenderer.getWidth(String.valueOf(c));
                    if (widthSum + charWidth / 2 >= localX) break;
                    widthSum += charWidth;
                    cPos++;
                }
                cursorLine = dragLine;
                cursorPos = cPos;
                selectionEndLine = dragLine;
                selectionEndChar = cPos;
                scrollToCursor();
                return true;
            }
            return false;
        }

        public void scrollVert(int amount) {
            targetScrollOffsetVert += amount * (mc.textRenderer.fontHeight + 2);
            if (targetScrollOffsetVert < 0) targetScrollOffsetVert = 0;
            int maxScroll = Math.max(0, lines.size() * (mc.textRenderer.fontHeight + 2) - height);
            if (targetScrollOffsetVert > maxScroll) targetScrollOffsetVert = maxScroll;
        }

        public void scrollHoriz(int amount) {
            targetScrollOffsetHoriz += amount;
            int maxScroll = Math.max(0, getMaxLineWidth() - width);
            if (targetScrollOffsetHoriz < 0) targetScrollOffsetHoriz = 0;
            if (targetScrollOffsetHoriz > maxScroll) targetScrollOffsetHoriz = maxScroll;
        }

        private void deleteSelection() {
            if (!hasSelection()) return;
            int startLine = selectionStartLine;
            int endLine = selectionEndLine;
            int startChar = selectionStartChar;
            int endChar = selectionEndChar;
            if (startLine > endLine || (startLine == endLine && startChar > endChar)) {
                int tmpLine = startLine; startLine = endLine; endLine = tmpLine;
                int tmpChar = startChar; startChar = endChar; endChar = tmpChar;
            }
            ArrayList<String> newLines = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (i < startLine || i > endLine) {
                    newLines.add(lines.get(i));
                } else if (i == startLine && i == endLine) {
                    String line = lines.get(i);
                    String newLine = line.substring(0, Math.max(0, startChar)) + line.substring(Math.min(endChar, line.length()));
                    newLines.add(newLine);
                    cursorLine = i;
                    cursorPos = Math.max(0, startChar);
                } else if (i == startLine) {
                    String line = lines.get(i);
                    String newLine = line.substring(0, Math.max(0, startChar));
                    newLines.add(newLine);
                } else if (i == endLine) {
                    String line = lines.get(i);
                    String old = newLines.remove(newLines.size() - 1);
                    String combined = old + line.substring(Math.min(endChar, line.length()));
                    newLines.add(combined);
                    cursorLine = startLine;
                    cursorPos = old.length();
                }
            }
            lines.clear();
            lines.addAll(newLines);
            clearSelection();
            scrollToCursor();
        }

        private boolean hasSelection() {
            return !(selectionStartLine == selectionEndLine && selectionStartChar == selectionEndChar)
                    && selectionStartLine != -1 && selectionEndLine != -1;
        }

        private boolean isLineSelected(int lineNumber) {
            if (selectionStartLine == -1 || selectionEndLine == -1) return false;
            int startLine = Math.min(selectionStartLine, selectionEndLine);
            int endLine = Math.max(selectionStartLine, selectionEndLine);
            return lineNumber >= startLine && lineNumber <= endLine;
        }

        private void drawSelection(DrawContext context, int lineNumber, int yPosition, String lineText, int padding) {
            int selectionStart = 0;
            int selectionEnd = lineText.length();
            if (lineNumber == selectionStartLine) {
                selectionStart = selectionStartChar;
            }
            if (lineNumber == selectionEndLine) {
                selectionEnd = selectionEndChar;
            }
            if (selectionStart > selectionEnd) {
                int temp = selectionStart;
                selectionStart = selectionEnd;
                selectionEnd = temp;
            }
            if (selectionStart >= lineText.length() || selectionEnd < 0) {
                return;
            }
            selectionStart = Math.max(0, selectionStart);
            selectionEnd = Math.min(lineText.length(), selectionEnd);
            String beforeSelection = lineText.substring(0, selectionStart);
            String selectionText = lineText.substring(selectionStart, selectionEnd);
            int selectionXStart = x + padding + mc.textRenderer.getWidth(beforeSelection) - (int) smoothScrollOffsetHoriz;
            int selectionWidth = mc.textRenderer.getWidth(selectionText);
            int lineHeight = mc.textRenderer.fontHeight + 2;
            context.fill(selectionXStart, yPosition, selectionXStart + selectionWidth, yPosition + lineHeight - 2, 0x804A90E2);
        }

        public void copySelectionToClipboard() {
            String selectedText = getSelectedText();
            if (!selectedText.isEmpty()) {
                mc.keyboard.setClipboard(selectedText);
            }
            clearSelection();
        }

        public String getSelectedText() {
            if (selectionStartLine == -1 || selectionEndLine == -1) {
                return "";
            }
            int startLine = selectionStartLine;
            int endLine = selectionEndLine;
            int startChar = selectionStartChar;
            int endChar = selectionEndChar;
            if (startLine > endLine || (startLine == endLine && startChar > endChar)) {
                int tmpLine = startLine; startLine = endLine; endLine = tmpLine;
                int tmpChar = startChar; startChar = endChar; endChar = tmpChar;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = startLine; i <= endLine; i++) {
                if (i < 0 || i >= lines.size()) continue;
                String lineText = lines.get(i);
                int lineStartChar = (i == startLine) ? startChar : 0;
                int lineEndChar = (i == endLine) ? endChar : lineText.length();
                if (lineStartChar > lineEndChar) {
                    int temp = lineStartChar;
                    lineStartChar = lineEndChar;
                    lineEndChar = temp;
                }
                if (lineStartChar >= lineText.length() || lineEndChar < 0) {
                    continue;
                }
                lineStartChar = Math.max(0, lineStartChar);
                lineEndChar = Math.min(lineText.length(), lineEndChar);
                sb.append(lineText, lineStartChar, lineEndChar);
                if (i != endLine) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        }

        private void clearSelection() {
            selectionStartLine = -1;
            selectionStartChar = -1;
            selectionEndLine = -1;
            selectionEndChar = -1;
        }

        public List<String> getLines() {
            return lines;
        }

        public void undo() {
            if (undoStack.size() > 1) {
                redoStack.push(currentState());
                undoStack.pop();
                EditorState state = undoStack.peek();
                lines.clear();
                lines.addAll(state.lines);
                cursorLine = state.cursorLine;
                cursorPos = state.cursorPos;
                clearSelection();
                scrollToCursor();
                parentTab.checkIfChanged(lines);
            }
        }

        public void redo() {
            if (!redoStack.isEmpty()) {
                undoStack.push(currentState());
                EditorState state = redoStack.pop();
                lines.clear();
                lines.addAll(state.lines);
                cursorLine = state.cursorLine;
                cursorPos = state.cursorPos;
                clearSelection();
                scrollToCursor();
                parentTab.checkIfChanged(lines);
            }
        }

        public void selectAll() {
            selectionStartLine = 0;
            selectionStartChar = 0;
            selectionEndLine = lines.size() - 1;
            selectionEndChar = lines.get(lines.size() - 1).length();
            cursorLine = lines.size() - 1;
            cursorPos = lines.get(lines.size() - 1).length();
            scrollToCursor();
        }

        private void pushState() {
            redoStack.clear();
            undoStack.push(currentState());
            parentTab.checkIfChanged(lines);
        }

        private EditorState currentState() {
            return new EditorState(new ArrayList<>(lines), cursorLine, cursorPos);
        }

        private void scrollToCursor() {
            int lineHeight = mc.textRenderer.fontHeight + 2;
            int cursorY = cursorLine * lineHeight;
            double desiredScrollVert = cursorY - (height / 2.0) + (3 * lineHeight) - paddingTop;
            targetScrollOffsetVert = desiredScrollVert;
            if (targetScrollOffsetVert < 0) targetScrollOffsetVert = 0;
            int maxScrollVert = Math.max(0, lines.size() * lineHeight - height);
            if (targetScrollOffsetVert > maxScrollVert) targetScrollOffsetVert = maxScrollVert;
            String lineText = lines.get(cursorLine);
            int cursorX = mc.textRenderer.getWidth(lineText.substring(0, Math.min(cursorPos, lineText.length())));
            double desiredScrollHoriz = cursorX - (width / 2.0) + (3 * mc.textRenderer.getWidth("word"));
            targetScrollOffsetHoriz = desiredScrollHoriz;
            if (targetScrollOffsetHoriz < 0) targetScrollOffsetHoriz = 0;
            int maxScrollHoriz = Math.max(0, getMaxLineWidth() - width);
            if (targetScrollOffsetHoriz > maxScrollHoriz) targetScrollOffsetHoriz = maxScrollHoriz;
        }

        public void setCursor(int line, int start) {
            cursorLine = line;
            cursorPos = start;
            scrollToCursor();
        }

        private static class EditorState {
            private final ArrayList<String> lines;
            private final int cursorLine;
            private final int cursorPos;

            public EditorState(ArrayList<String> lines, int cursorLine, int cursorPos) {
                this.lines = lines;
                this.cursorLine = cursorLine;
                this.cursorPos = cursorPos;
            }
        }

        public void setSearchResults(List<Position> results) {
            this.searchResults = results;
        }
    }
}
