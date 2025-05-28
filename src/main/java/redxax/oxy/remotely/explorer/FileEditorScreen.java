package redxax.oxy.remotely.explorer;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.Render.ScrollBar;
import redxax.oxy.remotely.Render.TabsBar;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.explorer.FileExplorerScreen.EntryData;
import redxax.oxy.remotely.ui.AISidePanel;
import redxax.oxy.remotely.ui.widgets.ContextMenuWidget;
import redxax.oxy.remotely.util.ImageUtil;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Sound;
import redxax.oxy.remotely.util.TextAnimator;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.explorer.FileExplorerScreen.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class FileEditorScreen extends Screen {
    private static final Map<Path, SavedTabState> SAVED_TABS = new HashMap<>();
    private final MinecraftClient minecraftClient;
    private MultiLineTextEditor textEditor;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private static final double FAST_SCROLL_FACTOR = 3.0;
    private static final double HORIZONTAL_SCROLL_FACTOR = 10.0;
    private static final List<Tab> tabs = new ArrayList<>();
    private static int currentTabIndex = 0;
    private final int TAB_HEIGHT = 18;
    private final int TAB_PADDING = 5;
    private final int TAB_GAP = 5;
    private final List<Position> searchResults = new ArrayList<>();
    private int currentSearchIndex = 0;
    private final int searchBarWidth = 200;
    private final int searchBarHeight = 20;
    private final int clearSearchButtonWidth = 20;
    private boolean customSearchBarFocused = false;
    private final StringBuilder customSearchText = new StringBuilder();
    private int customCursorPosition = 0;
    private int customSelectionStart = -1;
    private int customSelectionEnd = -1;
    private boolean customShowCursor = true;
    private long customLastBlinkTime = 0;
    private final float customPathScrollOffset = 0;
    private final float customPathTargetScrollOffset = 0;
    private ImageUtil.IconWithTooltip closeIcon, saveIcon, explorerIcon, aiIcon;
    private int sidePanelWidth = 250;
    private List<SidePanelEntry> sidePanelEntries = new ArrayList<>();
    private boolean showSidePanel = true;
    private float animatedSidePanelWidth = 0f;
    private boolean isResizingSidePanel = false;
    private AISidePanel aiSidePanel;
    private int ContentYStart;
    private TabsBar<Tab> tabsBar;
    private ContextMenuWidget tabContextMenu;

    private static class SidePanelEntry {
        EntryData data;
        int depth;
        boolean isOpen;
        List<EntryData> children;
        SidePanelEntry(EntryData data, int depth) {
            this.data = data;
            this.depth = depth;
            this.isOpen = false;
            this.children = new ArrayList<>();
        }
    }

    private static class SavedTabState {
        ArrayList<String> lines;
        ArrayDeque<MultiLineTextEditor.EditorState> undoStack;
        ArrayDeque<MultiLineTextEditor.EditorState> redoStack;
        int cursorLine;
        int cursorPos;
        boolean unsaved;
        String originalContent;
        int selectionStartLine;
        int selectionStartChar;
        int selectionEndLine;
        int selectionEndChar;
        SavedTabState() {
            lines = new ArrayList<>();
            undoStack = new ArrayDeque<>();
            redoStack = new ArrayDeque<>();
        }
    }

    public class Tab {
        Path path;
        public String name;
        TextAnimator textAnimator;
        MultiLineTextEditor textEditor;
        public boolean unsaved;
        String originalContent;
        public float sidePanelScrollOffset = 0f;
        public float targetSidePanelScrollOffset = 0f;
        public Set<Path> openedPathsForSidePanel = new HashSet<>();
        public boolean sidePanelInitialized = false;
        public boolean initialScrollSet = false;

        Tab(Path path) {
            this.path = path.normalize();
            this.name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
            this.textAnimator = new TextAnimator(this.name, 0, 30);
            this.textAnimator.start();
            if (SAVED_TABS.containsKey(this.path)) {
                SavedTabState st = SAVED_TABS.get(this.path);
                this.textEditor = new MultiLineTextEditor(minecraftClient, new ArrayList<>(st.lines), this.name, this);
                this.textEditor.undoStack.clear();
                this.textEditor.undoStack.addAll(st.undoStack);
                this.textEditor.redoStack.clear();
                this.textEditor.redoStack.addAll(st.redoStack);
                this.textEditor.cursorLine = st.cursorLine;
                this.textEditor.cursorPos = st.cursorPos;
                this.textEditor.selectionStartLine = st.selectionStartLine;
                this.textEditor.selectionStartChar = st.selectionStartChar;
                this.textEditor.selectionEndLine = st.selectionEndLine;
                this.textEditor.selectionEndChar = st.selectionEndChar;
                this.originalContent = st.originalContent;
                this.unsaved = st.unsaved;
            } else {
                loadFileContent();
            }
            if (this.textEditor != null) {
                this.textEditor.smoothScrollOffsetVert = this.textEditor.targetScrollOffsetVert;
                this.textEditor.smoothScrollOffsetHoriz = this.textEditor.targetScrollOffsetHoriz;
            }
        }
        public void checkIfChanged(List<String> lines) {
            String joined = String.join("\n", lines);
            this.unsaved = !joined.equals(originalContent);
            if (tabsBar != null) tabsBar.setIsUnsaved(currentTabIndex, unsaved);
        }
        private void loadFileContent() {
            ArrayList<String> fileContent = new ArrayList<>();
            if (serverInfo.isRemote) {
                try {
                    if (serverInfo.remoteSSHManager == null) {
                        serverInfo.remoteSSHManager = new SSHManager(serverInfo);
                        serverInfo.remoteSSHManager.connectToRemoteHost(serverInfo.remoteHost.getUser(), serverInfo.remoteHost.ip, serverInfo.remoteHost.port, serverInfo.remoteHost.password);
                        serverInfo.remoteSSHManager.connectSFTP();
                    } else if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
                        serverInfo.remoteSSHManager.connectSFTP();
                    }
                    String remotePath = path.toString().replace("\\", "/");
                    String content = serverInfo.remoteSSHManager.readRemoteFile(remotePath);
                    String[] lines = content.split("\\r?\\n");
                    for (int i = 0; i < lines.length; i++) {
                        lines[i] = lines[i].replace("\\t", "\t");
                    }
                    Collections.addAll(fileContent, lines);
                } catch (Exception e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File load error (remote): " + e.getMessage() + "\n");
                    }
                }
            } else {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    reader.lines().forEach(line -> {
                        line = line.replace("\\t", "\t");
                        fileContent.add(line);
                    });
                } catch (IOException e) {
                    if (serverInfo.terminal != null) {
                        serverInfo.terminal.appendOutput("File load error: " + e.getMessage() + "\n");
                    }
                }
            }
            this.textEditor = new MultiLineTextEditor(minecraftClient, fileContent, path.getFileName() != null ? path.getFileName().toString() : "NULL", this);
            this.originalContent = String.join("\n", fileContent);
            this.unsaved = false;
        }

        public void saveFile() {
            saveFile(currentTabIndex);
        }

        public void saveFile(int idx) {
            ArrayList<String> newContent = new ArrayList<>(textEditor.getLines());
            if (serverInfo.isRemote) {
                try {
                    if (serverInfo.remoteSSHManager == null) {
                        serverInfo.remoteSSHManager = new SSHManager(serverInfo);
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
            if (tabsBar != null) tabsBar.setIsUnsaved(idx, false);
        }
    }

    public FileEditorScreen(MinecraftClient mc, Screen parent, Path filePath, ServerInfo info) {
        super(Text.literal("File Editor"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).path.equals(filePath.normalize())) {
                currentTabIndex = i;
                this.textEditor = tabs.get(i).textEditor;
                return;
            }
        }
        Tab initialTab = new Tab(filePath);
        tabs.add(initialTab);
        this.textEditor = initialTab.textEditor;
        originalMCScale = minecraftClient.getWindow().getScaleFactor();
        targetScaleFactor = globalScaleFactor;
        minecraftClient.getWindow().setScaleFactor(globalScaleFactor);
    }

    @Override
    protected void init() {
        super.init();
        this.textEditor.init(10, 60, this.width - sidePanelWidth - 15, this.height - 65);
        List<Path> loadedTabs = RemotelyClient.INSTANCE.loadFileEditorTabs();
        for (Path path : loadedTabs) {
            boolean tabExists = false;
            for (Tab tab : tabs) {
                if (tab.path.equals(path.normalize())) {
                    tabExists = true;
                    break;
                }
            }
            if (!tabExists) {
                Tab tab = new Tab(path);
                tabs.add(tab);
                tab.textEditor.init(10, 60, this.width - sidePanelWidth - 15, this.height - 65);
            }
        }
        if (!tabs.isEmpty()) {
            this.textEditor = tabs.get(currentTabIndex).textEditor;
        }
        try {
            closeIcon = new ImageUtil.IconWithTooltip("/assets/remotely/icons/close.png", "Close The Screen");
            saveIcon = new ImageUtil.IconWithTooltip("/assets/remotely/icons/save.png", "Save The File");
            explorerIcon = new ImageUtil.IconWithTooltip("/assets/remotely/icons/explorer.png", "Toggle Explorer Panel");
            aiIcon = new ImageUtil.IconWithTooltip("/assets/remotely/icons/ReemotelyAI.png", "Toggle RemotelyAI Panel");
        } catch (Exception e) {
            new Notification("Failed to load icons: " + e.getMessage(), Notification.Type.ERROR);
        }
        updateSidePanelEntries();
        aiSidePanel = new AISidePanel();
        List<TabsBar.Tab<Tab>> tabList = new ArrayList<>();
        for (Tab t : tabs) {
            tabList.add(new TabsBar.Tab<>(t.name, t.unsaved, t));
        }
        tabsBar = new TabsBar<>(tabList);
        tabsBar.setActiveTab(currentTabIndex);
        tabsBar.setHasPlus(false);
        tabsBar.setAllowClose(tabCloseButtons);
        tabsBar.setAllowRename(false);
        tabsBar.setAllowDrag(true);
        tabsBar.setAllowScroll(true);
        tabsBar.setTabBarBounds(5, 35, this.width - 5, TAB_HEIGHT);
        tabsBar.setOnTabOrderChanged(() -> {
            List<Tab> newTabs = tabsBar.getTabs().stream().map(tb -> tb.data).toList();
            tabs.clear();
            tabs.addAll(newTabs);
            currentTabIndex = tabsBar.getActiveTab();
            this.textEditor = tabs.get(currentTabIndex).textEditor;
            RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
        });
        tabsBar.setOnTabClosed(() -> {
            int idx = tabsBar.getActiveTab();
            if (idx >= 0 && idx < tabs.size()) {
                Tab removed = tabs.remove(idx);
                tabsBar.getTabs().remove(idx);
                SAVED_TABS.remove(removed.path);
                if (tabs.isEmpty()) {
                    minecraftClient.setScreen(parent);
                } else {
                    if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
                    tabsBar.setActiveTab(currentTabIndex);
                    this.textEditor = tabs.get(currentTabIndex).textEditor;
                    RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
                }
            }
        });
        tabsBar.setOnTabSelected(() -> {
            currentTabIndex = tabsBar.getActiveTab();
            this.textEditor = tabs.get(currentTabIndex).textEditor;
        });
    }

    @Override
    public void close() {
        for (Tab t : tabs) {
            saveTabState(t);
        }
        RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
        minecraftClient.setScreen(parent);
    }

    private void saveTabState(Tab tab) {
        SavedTabState st = new SavedTabState();
        st.lines.addAll(tab.textEditor.getLines());
        st.undoStack.addAll(tab.textEditor.undoStack);
        st.redoStack.addAll(tab.textEditor.redoStack);
        st.cursorLine = tab.textEditor.cursorLine;
        st.cursorPos = tab.textEditor.cursorPos;
        st.selectionStartLine = tab.textEditor.selectionStartLine;
        st.selectionStartChar = tab.textEditor.selectionStartChar;
        st.selectionEndLine = tab.textEditor.selectionEndLine;
        st.selectionEndChar = tab.textEditor.selectionEndChar;
        st.unsaved = tab.unsaved;
        st.originalContent = tab.originalContent;
        SAVED_TABS.put(tab.path, st);
    }

    @Override
    public void tick() {
        super.tick();
        if (customSearchBarFocused) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - customLastBlinkTime >= 500) {
                customShowCursor = !customShowCursor;
                customLastBlinkTime = currentTime;
            }
        }
        tabs.get(currentTabIndex).textEditor.tickDragScroll();
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (aiSidePanel.fieldFocused && aiMode && aiSidePanel.charTyped(chr, keyCode)) return true;
        if (aiSidePanel.fieldFocused && aiMode && aiSidePanel.charTyped(chr, keyCode)) {
            return true;
        }
        if (customSearchBarFocused) {
            if (chr == '\n' || chr == '\r') {
                handleSearchEnter();
                return true;
            }
            if (chr == 27) {
                customSearchBarFocused = false;
                return true;
            }
            if (chr != '\b') {
                if (customSelectionStart != -1 && customSelectionEnd != -1 && customSelectionStart != customSelectionEnd) {
                    int selStart = Math.min(customSelectionStart, customSelectionEnd);
                    int selEnd = Math.max(customSelectionStart, customSelectionEnd);
                    customSearchText.delete(selStart, selEnd);
                    customCursorPosition = selStart;
                    customSelectionStart = -1;
                    customSelectionEnd = -1;
                }
                customSearchText.insert(customCursorPosition, chr);
                customCursorPosition++;
            }
            updateSearchResults();
            return true;
        }
        return tabs.get(currentTabIndex).textEditor.charTyped(chr, keyCode) || super.charTyped(chr, keyCode);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tabsBar.handleTabsBarKey(keyCode, scanCode, modifiers)) return true;
        boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (aiMode && aiSidePanel.fieldFocused) {
            StringBuilder sb = new StringBuilder();
            sb.append("Current file path: ").append(tabs.get(currentTabIndex).path.toString()).append("\n");
            sb.append("Current file name: ").append(tabs.get(currentTabIndex).name).append("\n");
            sb.append("Current file content:\n");
            for (int i = 0; i < tabs.get(currentTabIndex).textEditor.lines.size(); i++) {
                sb.append(tabs.get(currentTabIndex).textEditor.lines.get(i)).append("\n");
            }
            aiSidePanel.setExtraContext(sb.toString());
            if (aiSidePanel.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_F) {
            customSearchBarFocused = true;
            String selected = tabs.get(currentTabIndex).textEditor.getSelectedText();
            if (!selected.isEmpty()) {
                customSearchText.setLength(0);
                customSearchText.append(selected);
                customCursorPosition = customSearchText.length();
            }
            customSelectionStart = -1;
            customSelectionEnd = -1;
            updateSearchResults();
            return true;
        }
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_S) {
            tabs.get(currentTabIndex).saveFile();
            return true;
        }
        if (customSearchBarFocused) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (customSelectionStart != -1 && customSelectionEnd != -1 && customSelectionStart != customSelectionEnd) {
                        int selStart = Math.min(customSelectionStart, customSelectionEnd);
                        int selEnd = Math.max(customSelectionStart, customSelectionEnd);
                        customSearchText.delete(selStart, selEnd);
                        customCursorPosition = selStart;
                        customSelectionStart = -1;
                        customSelectionEnd = -1;
                    } else {
                        if (customCursorPosition > 0) {
                            customSearchText.deleteCharAt(customCursorPosition - 1);
                            customCursorPosition--;
                        }
                    }
                    updateSearchResults();
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (customSelectionStart != -1 && customSelectionEnd != -1 && customSelectionStart != customSelectionEnd) {
                        int selStart = Math.min(customSelectionStart, customSelectionEnd);
                        int selEnd = Math.max(customSelectionStart, customSelectionEnd);
                        customSearchText.delete(selStart, selEnd);
                        customCursorPosition = selStart;
                        customSelectionStart = -1;
                        customSelectionEnd = -1;
                    } else {
                        if (customCursorPosition < customSearchText.length()) {
                            customSearchText.deleteCharAt(customCursorPosition);
                        }
                    }
                    updateSearchResults();
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    handleSearchEnter();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    customSearchBarFocused = false;
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    if (customCursorPosition > 0) {
                        customCursorPosition--;
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (customCursorPosition < customSearchText.length()) {
                        customCursorPosition++;
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_A -> {
                    if (ctrlHeld) {
                        customSelectionStart = 0;
                        customSelectionEnd = customSearchText.length();
                        customCursorPosition = customSearchText.length();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    if (ctrlHeld) {
                        String clipboard = minecraftClient.keyboard.getClipboard();
                        if (customSelectionStart != -1 && customSelectionEnd != -1 && customSelectionStart != customSelectionEnd) {
                            int selStart = Math.min(customSelectionStart, customSelectionEnd);
                            int selEnd = Math.max(customSelectionStart, customSelectionEnd);
                            customSearchText.delete(selStart, selEnd);
                            customCursorPosition = selStart;
                            customSelectionStart = -1;
                            customSelectionEnd = -1;
                        }
                        for (char c : clipboard.toCharArray()) {
                            customSearchText.insert(customCursorPosition, c);
                            customCursorPosition++;
                        }
                        updateSearchResults();
                    }
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
        String query = customSearchText.toString().toLowerCase();
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
        if (tabsBar.handleTabsBarMouse((int)mouseX, (int)mouseY, button)) return true;
        if (ScrollBar.handleMousePressed(this, (int) mouseX, (int) mouseY, tabs.get(currentTabIndex).textEditor.getTotalScrollHeight(), tabs.get(currentTabIndex).textEditor.getScrollOffset())) {
            return true;
        }
        if (aiMode && aiSidePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= width - 69 && mouseX <= width - 52 && mouseY >= 6 && mouseY <= 24 && button == 0) {
            playSound(Sound.PANEL);
            if (aiMode && showSidePanel) aiMode = false;
            else {
                showSidePanel = !showSidePanel;
                aiMode = false;
            }
            return true;
        }
        if (mouseX >= width - 92 && mouseX <= width - 75 && mouseY >= 6 && mouseY <= 24 && button == 0) {
            playSound(Sound.PANEL);
            if (aiMode && showSidePanel) showSidePanel = false;
            else {
                aiMode = true;
                showSidePanel = true;
            }
            return true;
        }
        int searchBarX = (this.width - searchBarWidth) / 2;
        int searchBarY = 5;
        int clearSearchButtonX = searchBarX + searchBarWidth;
        if (mouseX >= searchBarX && mouseX <= searchBarX + searchBarWidth && mouseY >= searchBarY && mouseY <= searchBarY + searchBarHeight) {
            playSound(Sound.SEARCH);
            customSearchBarFocused = true;
            return true;
        } else {
            if (mouseX >= clearSearchButtonX && mouseX <= clearSearchButtonX + clearSearchButtonWidth && mouseY >= searchBarY && mouseY <= searchBarY + searchBarHeight) {
                playSound(Sound.CLICK);
                customSearchText.setLength(0);
                customCursorPosition = 0;
                customSelectionStart = -1;
                customSelectionEnd = -1;
                updateSearchResults();
                return true;
            }
            customSearchBarFocused = false;
        }
        boolean clickedTab = false;
        int titleBarHeight = 30;
        int tabBarY = titleBarHeight + 5;
        int tabX = 5;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = minecraftClient.textRenderer.getWidth(tab.name) + 2 * TAB_PADDING;
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabBarY && mouseY <= tabBarY + TAB_HEIGHT) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    playSound(Sound.CLOSETAB);
                    SAVED_TABS.remove(tab.path);
                    tabs.remove(i);
                    if (i < tabsBar.getTabs().size()) {
                        tabsBar.getTabs().remove(i);
                    }
                    if (i == currentTabIndex) {
                        currentTabIndex = Math.max(0, currentTabIndex - 1);
                    }
                    if (!tabs.isEmpty()) {
                        if (currentTabIndex >= tabs.size()) {
                            currentTabIndex = tabs.size() - 1;
                        }
                        this.textEditor = tabs.get(currentTabIndex).textEditor;
                    } else {
                        close();
                    }
                    RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
                    return true;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    playSound(Sound.SWITCHTAB);
                    if (currentTabIndex != i) {
                        currentTabIndex = i;
                        this.textEditor = tab.textEditor;
                        ScrollBar.setPendingOffset((float) this.textEditor.targetScrollOffsetVert);
                        RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
                        updateSidePanelEntries();
                    }
                    clickedTab = true;
                    break;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
                    remove(tabContextMenu);
                    int finalI = i;
                    tabContextMenu = new ContextMenuWidget.Builder(this)
                            .addHeaderButton(closeIcon.getImage(), () -> {
                                playSound(Sound.CLOSETAB);
                                if (tabs.size() > 1) {
                                    saveTabState(tab);
                                    tabs.remove(finalI);
                                    tabsBar.closeTab(finalI);
                                    if (!tabs.isEmpty()) {
                                        this.textEditor = tab.textEditor;
                                    } else {
                                        close();
                                    }
                                    RemotelyClient.INSTANCE.saveFileEditorTabs(tabs.stream().map(t -> t.path).collect(Collectors.toList()));
                                } else {
                                    close();
                                }
                            }, "Close The Tab")
                            .addHeaderButton(saveIcon.getImage(), () -> {
                                playSound(Sound.SAVE);
                                tab.saveFile(finalI);
                            }, "Save The Current File")
                            .addHeaderButton("/assets/remotely/icons/external.png", () -> {
                                playSound(Sound.CLICK);
                                openExternally(tab.path);
                            }, "Open In The Default App")
                            .build();
                    addDrawableChild(tabContextMenu);
                    tabContextMenu.show((int) mouseX, (int) mouseY);
                    return true;
                } else {
                    tabContextMenu.hide();
                }
            }
            tabX += tabWidth + TAB_GAP;
        }
        if (clickedTab) {
            return true;
        }
        boolean clickedSave = mouseX >= width - 46 && mouseX <= width - 29 && mouseY >= 6 && mouseY <= 24 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (clickedSave) {
            playSound(Sound.SAVE);
            tabs.get(currentTabIndex).saveFile();
            return true;
        }
        boolean clickedBack = mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (clickedBack) {
            close();
            return true;
        }
        float animWidth = animatedSidePanelWidth;
        int panelX = this.width - (int) animWidth;
        int panelY = 60;
        int panelHeight = this.height - 65;
        if (animWidth > 0) {
            if (Math.abs(mouseX - (panelX - 1)) < 5 && mouseY >= panelY && mouseY <= panelY + panelHeight && button == 0) {
                isResizingSidePanel = true;
                return true;
            }
            if (mouseX >= panelX && mouseX <= panelX + animWidth && mouseY >= panelY && mouseY <= panelY + panelHeight && !aiMode) {
                int entryHeight = 20 + 2;
                double localY = mouseY - panelY + tabs.get(currentTabIndex).sidePanelScrollOffset;
                int indexPos = 0;
                for (SidePanelEntry entry : sidePanelEntries) {
                    if (localY >= indexPos && localY < indexPos + entryHeight) {
                        if (Files.isDirectory(entry.data.path)) {
                            Tab currentTab = tabs.get(currentTabIndex);
                            if (currentTab.openedPathsForSidePanel.contains(entry.data.path)) {
                                currentTab.openedPathsForSidePanel.remove(entry.data.path);
                            } else {
                                currentTab.openedPathsForSidePanel.add(entry.data.path);
                            }
                            updateSidePanelEntries();
                        } else {
                            if (!entry.data.path.equals(tabs.get(currentTabIndex).path) && isSupportedFile(entry.data.path)) {
                                boolean found = false;
                                for (int j = 0; j < tabs.size(); j++) {
                                    if (tabs.get(j).path.equals(entry.data.path.normalize())) {
                                        currentTabIndex = j;
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    Tab newTab = new Tab(entry.data.path);
                                    tabs.add(newTab);
                                    currentTabIndex = tabs.size() - 1;
                                }
                                this.textEditor = tabs.get(currentTabIndex).textEditor;
                                updateSidePanelEntries();
                            } else {
                                openExternally(entry.data.path);
                            }
                        }
                        return true;
                    }
                    indexPos += entryHeight;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button) || tabs.get(currentTabIndex).textEditor.mouseClicked(mouseX, mouseY, button);
    }

    private void updateSidePanelEntries() {
        sidePanelEntries.clear();
        if (tabs.isEmpty()) return;
        Tab currentTab = tabs.get(currentTabIndex);
        Path filePath = currentTab.path;
        if (filePath == null) return;
        if (!currentTab.sidePanelInitialized) {
            Path current = filePath;
            while (current != null) {
                currentTab.openedPathsForSidePanel.add(current);
                current = current.getParent();
            }
            currentTab.sidePanelScrollOffset = 0f;
            currentTab.targetSidePanelScrollOffset = 0f;
            currentTab.sidePanelInitialized = true;
        }
        Path rootPath = filePath;
        while (rootPath.getParent() != null) {
            rootPath = rootPath.getParent();
        }
        buildSidePanelData(rootPath, 0, currentTab.openedPathsForSidePanel);
        if (!currentTab.initialScrollSet) {
            int index = 0;
            int entryHeight = 22;
            for (int i = 0; i < sidePanelEntries.size(); i++) {
                if (sidePanelEntries.get(i).data.path.equals(filePath)) {
                    index = i;
                    break;
                }
            }
            currentTab.sidePanelScrollOffset = index * entryHeight;
            currentTab.targetSidePanelScrollOffset = index * entryHeight;
            currentTab.initialScrollSet = true;
        }
    }

    private void buildSidePanelData(Path currentPath, int depth, Set<Path> openedPaths) {
        if (currentPath == null || !Files.exists(currentPath)) return;
        EntryData data = getEntryDataForPath(currentPath);
        SidePanelEntry entry = new SidePanelEntry(data, depth);
        entry.isOpen = openedPaths.contains(currentPath);
        sidePanelEntries.add(entry);
        try {
            if (Files.isDirectory(currentPath) && entry.isOpen) {
                List<Path> dirChildren = new ArrayList<>();
                DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath);
                for (Path child : stream) {
                    dirChildren.add(child);
                }
                stream.close();
                dirChildren.sort((p1, p2) -> {
                    boolean d1 = Files.isDirectory(p1);
                    boolean d2 = Files.isDirectory(p2);
                    if (d1 && !d2) return -1;
                    if (!d1 && d2) return 1;
                    return p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString());
                });
                for (Path child : dirChildren) {
                    buildSidePanelData(child, depth + 1, openedPaths);
                }
            }
        } catch (Exception ignored) {}
    }

    public EntryData getEntryDataForPath(Path p) {
        String dn = p.getFileName() != null ? p.getFileName().toString() : p.toString();
        boolean isDirectory = Files.isDirectory(p);
        return new EntryData(p, isDirectory, "", "", dn);
    }

    private void renderSidePanel(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
        Tab currentTab = tabs.get(currentTabIndex);
        currentTab.sidePanelScrollOffset += (currentTab.targetSidePanelScrollOffset - currentTab.sidePanelScrollOffset) * globalScrollSpeed * deltaTime;
        int entryHeight = 20;
        int entryGap = 2;
        int totalEntryHeight = entryHeight + entryGap;
        int currentY = y - (int) currentTab.sidePanelScrollOffset;
        for (SidePanelEntry entry : sidePanelEntries) {
            if (currentY + entryHeight < y) {
                currentY += totalEntryHeight;
                continue;
            }
            if (currentY > y + height) {
                break;
            }
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= currentY && mouseY < currentY + entryHeight;
            int indent = entry.depth * 5;
            drawExplorerElements(context, hovered, entry.data.displayName.equals(tabs.get(currentTabIndex).name), false, entry.data, x + indent, currentY, width - indent, entryHeight, minecraftClient.textRenderer, false, null, null, 0);
            currentY += totalEntryHeight;
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tabsBar.handleTabsBarRelease(button)) return true;
        if (ScrollBar.handleMouseReleased()) {
            return true;
        }
        if (isResizingSidePanel && button == 0) {
            isResizingSidePanel = false;
            return true;
        }
        return tabs.get(currentTabIndex).textEditor.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (tabsBar.handleTabsBarDrag(button, deltaX)) return true;
        if (ScrollBar.handleMouseDragged(this, (int) mouseY, tabs.get(currentTabIndex).textEditor.getTotalScrollHeight())) {
            return true;
        }
        if (isResizingSidePanel && button == 0) {
            int newWidth = (int) (this.width - mouseX);
            sidePanelWidth = Math.max(80, Math.min(newWidth, this.width - 50));
            return true;
        }
        boolean handled = false;
        return tabs.get(currentTabIndex).textEditor.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, /*? !=1.20.1 {*/ double horizontalAmount, /*?}*/ double verticalAmount) {
        scaleScroll(verticalAmount);
        if (tabsBar.handleTabsBarScroll(verticalAmount, mouseX, mouseY)) return true;
        if (aiMode && aiSidePanel.mouseScrolled(mouseX, mouseY, verticalAmount, this.width - (int) animatedSidePanelWidth - 5, ContentYStart, (int) animatedSidePanelWidth, this.height - ContentYStart - 5)) {
            return true;
        }
        float animWidth = animatedSidePanelWidth;
        int panelX = this.width - (int) animWidth;
        int panelY = 60;
        int panelHeight = this.height - 65;
        if (animWidth > 0 && mouseX >= panelX && mouseX <= panelX + animWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            int scrollDir = verticalAmount >= 0 ? (int) Math.ceil(verticalAmount * 20) : (int) Math.floor(verticalAmount * 20);
            Tab currentTab = tabs.get(currentTabIndex);
            currentTab.targetSidePanelScrollOffset -= scrollDir;
            int totalHeight = sidePanelEntries.size() * 20;
            int maxScroll = Math.max(0, totalHeight - panelHeight + 10);
            if (currentTab.targetSidePanelScrollOffset < 0) currentTab.targetSidePanelScrollOffset = 0;
            if (currentTab.targetSidePanelScrollOffset > maxScroll) currentTab.targetSidePanelScrollOffset = maxScroll;
            return true;
        }
        long windowHandle = minecraftClient.getWindow().getHandle();
        boolean shiftHeld = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean ctrlHeld = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (shiftHeld) {
            tabs.get(currentTabIndex).textEditor.scrollHoriz((int) (-verticalAmount) * (int) HORIZONTAL_SCROLL_FACTOR);
        } else if (ctrlHeld) {
            tabs.get(currentTabIndex).textEditor.scrollVert((int) (-verticalAmount) * (int) FAST_SCROLL_FACTOR);
        } else {
            tabs.get(currentTabIndex).textEditor.scrollVert((int) (-verticalAmount));
        }
        ScrollBar.setPendingOffset((float) tabs.get(currentTabIndex).textEditor.targetScrollOffsetVert);
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawScreenHeader(context, width, height, width - 5 - (int)animatedSidePanelWidth, mouseX, mouseY, this, minecraftClient, closeIcon, saveIcon, explorerIcon, aiIcon, null, null, null, null, null);
        drawSearchBar(context, textRenderer, customSearchText, customSearchBarFocused, customCursorPosition, customSelectionStart, customSelectionEnd, customPathTargetScrollOffset, aiMode, "FileEditorScreen", mouseX, mouseY, "Search For Text In The File.");
        int tabOffsetY = 35;
        tabsBar.setTabBarBounds(5, tabOffsetY, this.width, TAB_HEIGHT);
        tabsBar.renderTabsBar(context, textRenderer, tabsBar, mouseX, mouseY, shadow);
        float targetWidth = showSidePanel ? sidePanelWidth : 0;
        animatedSidePanelWidth += (targetWidth - animatedSidePanelWidth) * globalExpandSpeed * deltaTime;
        int animWidth = (int)animatedSidePanelWidth;
        tabs.get(currentTabIndex).textEditor.updateBounds(10, 60, this.width - animWidth - 15, this.height - 65);
        tabs.get(currentTabIndex).textEditor.render(context, mouseX, mouseY, delta);
        ScrollBar.render(context, this, mouseX, mouseY, tabs.get(currentTabIndex).textEditor.getTotalScrollHeight(), (float)tabs.get(currentTabIndex).textEditor.targetScrollOffsetVert);
        tabs.get(currentTabIndex).textEditor.targetScrollOffsetVert = (int)ScrollBar.getPendingOffset();
        animatedScaling(this);
        if (animWidth > 0) {
            int panelX = this.width - animWidth;
            int panelY = 60;
            int panelHeight = this.height - 65;
            context.fill(panelX, panelY, panelX + animWidth, panelY + panelHeight, innerBackgroundColor);
            drawInnerBorder(context, panelX, panelY, animWidth, panelHeight, innerBorderColor);
            drawOuterBorder(context, panelX, panelY, animWidth, panelHeight, innerBackgroundColor);
            context.enableScissor(panelX, panelY, panelX + animWidth, panelY + panelHeight);
            if (aiMode) aiSidePanel.render(context, panelX, panelY, animWidth, panelHeight, mouseX, mouseY);
            else renderSidePanel(context, panelX, panelY, animWidth, panelHeight, mouseX, mouseY);
            context.disableScissor();
        }
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.FILEEDITOR);
    }

    @Override
    public void removed() {
        minecraftClient.getWindow().setScaleFactor(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
        if (parent == null) playSound(Sound.SCREEN);
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
        public int cursorLine;
        public int cursorPos;
        public int selectionStartLine = -1;
        public int selectionStartChar = -1;
        public int selectionEndLine = -1;
        public int selectionEndChar = -1;
        public final ArrayDeque<EditorState> undoStack = new ArrayDeque<>();
        public final ArrayDeque<EditorState> redoStack = new ArrayDeque<>();
        private final int textPadding = 4;
        private static long lastLeftClickTime = 0;
        private static int clickCount = 0;
        private List<Position> searchResults = new ArrayList<>();
        private boolean isDraggingSelection = false;
        private double lastDragX;
        private double lastDragY;

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
            smoothScrollOffsetVert += (targetScrollOffsetVert - smoothScrollOffsetVert) * globalScrollSpeed * deltaTime;
            smoothScrollOffsetHoriz += (targetScrollOffsetHoriz - smoothScrollOffsetHoriz) * globalScrollSpeed * deltaTime;
            context.enableScissor(x, y, x + width, y + height);
            int lineHeight = mc.textRenderer.fontHeight + 2;
            int visibleLines = height / lineHeight + 6;
            for (int i = 0; i < visibleLines; i++) {
                int lineIndex = (int) Math.floor(smoothScrollOffsetVert / lineHeight) + i;
                if (lineIndex < 0 || lineIndex >= lines.size()) continue;
                int renderY = y + i * lineHeight - (int) smoothScrollOffsetVert % lineHeight + 1;
                String text = lines.get(lineIndex);
                Text syntaxColoredLine = SyntaxHighlighter.highlight(text, fileName);
                context.drawText(mc.textRenderer, syntaxColoredLine, x + textPadding - (int) smoothScrollOffsetHoriz, renderY, 0xFFFFFF, shadow);
                if (isLineSelected(lineIndex)) {
                    drawSelection(context, lineIndex, renderY, text);
                }
                for (Position pos : searchResults) {
                    if (pos.line == lineIndex) {
                        int safeStart = Math.max(0, Math.min(pos.start, text.length()));
                        int safeEnd = Math.max(0, Math.min(pos.end, text.length()));
                        int startX = mc.textRenderer.getWidth(text.substring(0, safeStart));
                        int endX = mc.textRenderer.getWidth(text.substring(0, safeEnd));
                        context.fill(x + textPadding - (int) smoothScrollOffsetHoriz + startX, renderY, x + textPadding - (int) smoothScrollOffsetHoriz + endX, renderY + lineHeight - 2, globalSelectionColor);
                    }
                }
                if (lineIndex == cursorLine && !hasSelection()) {
                    int cursorX = mc.textRenderer.getWidth(text.substring(0, Math.min(cursorPos, text.length())));
                    int cy = renderY -1;
                    context.fill(x + textPadding - (int) smoothScrollOffsetHoriz + cursorX, cy, x + textPadding - (int) smoothScrollOffsetHoriz + cursorX + 1, cy + lineHeight - 2, globalCursorAnimatedColor);
                }
            }
            context.disableScissor();
        }

        public void tickDragScroll() {
            if (!isDraggingSelection) return;
            int lineHeight = mc.textRenderer.fontHeight + 2;
            int dragLine = (int) ((lastDragY - y + smoothScrollOffsetVert) / lineHeight);
            if (dragLine < 0) dragLine = 0;
            if (dragLine >= lines.size()) dragLine = lines.size() - 1;
            if (lines.isEmpty()) return;
            int localX = (int) lastDragX - (x + textPadding) + (int) smoothScrollOffsetHoriz;
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
            scrollToEdges(lastDragX, lastDragY, lineHeight);
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
                } else {
                    lines.add("");
                    cursorLine = lines.size() - 1;
                }
                cursorPos = 0;
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
            boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
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
                    if (shiftHeld && !hasSelection()) {
                        selectionStartLine = cursorLine;
                        selectionStartChar = cursorPos;
                    } else if (!shiftHeld) {
                        clearSelection();
                    }
                    if (ctrlHeld) {
                        cursorPos = moveCursorLeftWord();
                    } else {
                        if (cursorPos > 0) {
                            cursorPos--;
                        } else {
                            if (cursorLine > 0) {
                                cursorLine--;
                                cursorPos = lines.get(cursorLine).length();
                            }
                        }
                    }
                    if (shiftHeld) {
                        selectionEndLine = cursorLine;
                        selectionEndChar = cursorPos;
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (shiftHeld && !hasSelection()) {
                        selectionStartLine = cursorLine;
                        selectionStartChar = cursorPos;
                    } else if (!shiftHeld) {
                        clearSelection();
                    }
                    if (ctrlHeld) {
                        cursorPos = moveCursorRightWord();
                    } else {
                        if (cursorPos < lines.get(cursorLine).length()) {
                            cursorPos++;
                        } else {
                            if (cursorLine < lines.size() - 1) {
                                cursorLine++;
                                cursorPos = 0;
                            }
                        }
                    }
                    if (shiftHeld) {
                        selectionEndLine = cursorLine;
                        selectionEndChar = cursorPos;
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    if (cursorLine < lines.size() - 1) {
                        if (shiftHeld && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if (!shiftHeld) {
                            clearSelection();
                        }
                        cursorLine++;
                        cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
                        if (shiftHeld) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    if (cursorLine > 0) {
                        if (shiftHeld && !hasSelection()) {
                            selectionStartLine = cursorLine;
                            selectionStartChar = cursorPos;
                        } else if (!shiftHeld) {
                            clearSelection();
                        }
                        cursorLine--;
                        cursorPos = Math.min(cursorPos, lines.get(cursorLine).length());
                        if (shiftHeld) {
                            selectionEndLine = cursorLine;
                            selectionEndChar = cursorPos;
                        }
                    }
                    scrollToCursor();
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    if (ctrlHeld) {
                        deleteSelection();
                        pushState();
                        String clipboard = mc.keyboard.getClipboard();
                        String[] splitted = clipboard.replace("\r\n", "\n").split("\n");
                        for (int i = 0; i < splitted.length; i++) {
                            for (char c : splitted[i].toCharArray()) {
                                if (cursorLine < 0) cursorLine = 0;
                                if (cursorLine >= lines.size()) lines.add("");
                                String line = lines.get(cursorLine);
                                int pos = Math.min(cursorPos, line.length());
                                String newLine = line.substring(0, pos) + c + line.substring(pos);
                                lines.set(cursorLine, newLine);
                                cursorPos++;
                            }
                            if (i < splitted.length - 1) {
                                if (cursorLine < lines.size()) {
                                    String oldLine = lines.get(cursorLine);
                                    String beforeCursor = oldLine.substring(0, cursorPos);
                                    String afterCursor = oldLine.substring(cursorPos);
                                    lines.set(cursorLine, beforeCursor);
                                    lines.add(cursorLine + 1, afterCursor);
                                }
                                cursorLine++;
                                cursorPos = 0;
                            }
                        }
                        scrollToCursor();
                        parentTab.checkIfChanged(lines);
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_C -> {
                    if (ctrlHeld && hasSelection()) {
                        copySelectionToClipboard();
                        clearSelection();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (ctrlHeld && hasSelection()) {
                        copySelectionToClipboard();
                        deleteSelection();
                        clearSelection();
                        pushState();
                        scrollToCursor();
                        parentTab.checkIfChanged(lines);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    parentTab.checkIfChanged(lines);
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
                case GLFW.GLFW_KEY_TAB -> {
                    parentTab.checkIfChanged(lines);
                    if (!shiftHeld) {
                        deleteSelection();
                        pushState();
                        if (hasSelection()) {
                            indentSelection();
                        } else {
                            insertChar();
                        }
                        scrollToCursor();
                    } else {
                        deleteSelection();
                        pushState();
                        if (hasSelection()) {
                            unindentSelection();
                        } else {
                            removeIndentFromLine(cursorLine);
                        }
                        scrollToCursor();
                    }
                    parentTab.checkIfChanged(lines);
                    return true;
                }
            }
            return false;
        }

        private void indentSelection() {
            int startLine = Math.min(selectionStartLine, selectionEndLine);
            int endLine = Math.max(selectionStartLine, selectionEndLine);
            if (!hasSelection()) {
                insertChar();
                return;
            }
            for (int i = startLine; i <= endLine; i++) {
                if (i < 0 || i >= lines.size()) continue;
                String line = lines.get(i);
                lines.set(i, "\t" + line);
            }
            int newStartChar = selectionStartChar + 1;
            int newEndChar = selectionEndChar + 1;
            selectionStartChar = newStartChar;
            selectionEndChar = newEndChar;
        }

        private void unindentSelection() {
            int startLine = Math.min(selectionStartLine, selectionEndLine);
            int endLine = Math.max(selectionStartLine, selectionEndLine);
            for (int i = startLine; i <= endLine; i++) {
                removeIndentFromLine(i);
            }
        }

        private void removeIndentFromLine(int i) {
            if (i < 0 || i >= lines.size()) return;
            String line = lines.get(i);
            if (line.startsWith("\t")) {
                lines.set(i, line.substring(1));
            } else if (line.startsWith("    ")) {
                lines.set(i, line.substring(4));
            }
        }

        private void insertChar() {
            if (cursorLine < 0) cursorLine = 0;
            if (cursorLine >= lines.size()) lines.add("");
            String line = lines.get(cursorLine);
            int pos = Math.min(cursorPos, line.length());
            String newLine = line.substring(0, pos) + '\t' + line.substring(pos);
            lines.set(cursorLine, newLine);
            cursorPos++;
            parentTab.checkIfChanged(lines);
        }

        private int moveCursorLeftWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return 0;
            if (cursorPos == 0) {
                if (cursorLine > 0) {
                    cursorLine--;
                    cursorPos = lines.get(cursorLine).length();
                    return cursorPos;
                }
                return cursorPos;
            }
            String line = lines.get(cursorLine);
            int index = cursorPos - 1;
            while (index >= 0 && Character.isWhitespace(line.charAt(index))) {
                index--;
            }
            while (index >= 0 && !Character.isWhitespace(line.charAt(index))) {
                index--;
            }
            cursorPos = Math.max(0, index + 1);
            return cursorPos;
        }

        private int moveCursorRightWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return 0;
            String line = lines.get(cursorLine);
            if (cursorPos >= line.length()) {
                if (cursorLine < lines.size() - 1) {
                    cursorLine++;
                    cursorPos = 0;
                    return cursorPos;
                }
                return cursorPos;
            }
            int index = cursorPos;
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            while (index < line.length() && !Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            cursorPos = index;
            return cursorPos;
        }

        private void deleteWord() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return;
            String line = lines.get(cursorLine);
            if (cursorPos == 0) return;
            int startPos = cursorPos - 1;
            int spaceCount = 0;
            while (startPos >= 0 && line.charAt(startPos) == ' ') {
                spaceCount++;
                startPos--;
            }
            if (spaceCount > 1) {
                lines.set(cursorLine, line.substring(0, startPos + 1) + line.substring(cursorPos));
                cursorPos = startPos + 1;
            } else if (spaceCount == 1) {
                while (startPos >= 0 && !Character.isWhitespace(line.charAt(startPos))) {
                    startPos--;
                }
                lines.set(cursorLine, line.substring(0, startPos + 1) + line.substring(cursorPos));
                cursorPos = startPos + 1;
            }
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
                int clickedLine = (int) ((mouseY - y + smoothScrollOffsetVert) / lineHeight);
                if (clickedLine < 0) clickedLine = 0;
                if (clickedLine >= lines.size()) clickedLine = lines.size() - 1;
                if (lines.isEmpty()) return false;
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
                if (clickCount == 1) {
                    isDraggingSelection = true;
                    lastDragX = mouseX;
                    lastDragY = mouseY;
                }
            }
            return true;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                isDraggingSelection = false;
                return true;
            }
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isDraggingSelection) {
                lastDragX = mouseX;
                lastDragY = mouseY;
                int lineHeight = mc.textRenderer.fontHeight + 2;
                int dragLine = (int) ((mouseY - y + smoothScrollOffsetVert) / lineHeight);
                if (dragLine < 0) dragLine = 0;
                if (dragLine >= lines.size()) dragLine = lines.size() - 1;
                if (lines.isEmpty()) return false;
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
                selectionEndLine = cursorLine;
                selectionEndChar = cursorPos;
                scrollToEdges(mouseX, mouseY, lineHeight);
                return true;
            }
            return false;
        }

        private void scrollToEdges(double mouseX, double mouseY, int lineHeight) {
            int topVisible = (int) Math.floor(smoothScrollOffsetVert);
            int bottomVisible = topVisible + height - lineHeight;
            int cursorY = cursorLine * lineHeight;
            if (cursorY < topVisible) {
                targetScrollOffsetVert = cursorY;
            } else if (cursorY > bottomVisible) {
                targetScrollOffsetVert = cursorY - (height - lineHeight);
            }
            if (targetScrollOffsetVert < 0) targetScrollOffsetVert = 0;
            int maxScroll = Math.max(0, lines.size() * lineHeight - height);
            if (targetScrollOffsetVert > maxScroll) targetScrollOffsetVert = maxScroll;
            String lineText = cursorLine >= 0 && cursorLine < lines.size() ? lines.get(cursorLine) : "";
            int cursorX = mc.textRenderer.getWidth(lineText.substring(0, Math.min(cursorPos, lineText.length())));
            int leftVisible = (int) Math.floor(smoothScrollOffsetHoriz);
            int rightVisible = leftVisible + width - 20;
            if (cursorX < leftVisible) {
                targetScrollOffsetHoriz = cursorX;
            } else if (cursorX > rightVisible) {
                targetScrollOffsetHoriz = cursorX - (width - 20);
            }
            if (targetScrollOffsetHoriz < 0) targetScrollOffsetHoriz = 0;
            int maxScrollH = Math.max(0, getMaxLineWidth() - width);
            if (targetScrollOffsetHoriz > maxScrollH) targetScrollOffsetHoriz = maxScrollH;
            ScrollBar.setPendingOffset((float) tabs.get(currentTabIndex).textEditor.targetScrollOffsetVert);
        }

        public void scrollVert(int amount) {
            targetScrollOffsetVert += amount * (mc.textRenderer.fontHeight + 2);
            if (targetScrollOffsetVert < 0) targetScrollOffsetVert = 0;
            int additionalScroll = 30 * (mc.textRenderer.fontHeight + 2);
            int maxScroll = Math.max(0, lines.size() * (mc.textRenderer.fontHeight + 2) - height + additionalScroll);
            if (targetScrollOffsetVert > maxScroll) targetScrollOffsetVert = maxScroll;
        }

        public void scrollHoriz(int amount) {
            targetScrollOffsetHoriz += amount;
            int additionalScroll = 100;
            int maxScroll = Math.max(0, getMaxLineWidth() - width + additionalScroll);
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
                    String old = newLines.removeLast();
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

        private void drawSelection(DrawContext context, int lineNumber, int yPosition, String lineText) {
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
            int selectionXStart = x + 4 + mc.textRenderer.getWidth(beforeSelection) - (int) smoothScrollOffsetHoriz;
            int selectionWidth = mc.textRenderer.getWidth(selectionText);
            int lineHeight = mc.textRenderer.fontHeight + 2;
            context.fill(selectionXStart, yPosition, selectionXStart + selectionWidth, yPosition + lineHeight - 2, globalSelectionColor);
        }

        public void copySelectionToClipboard() {
            String selectedText = getSelectedText();
            if (!selectedText.isEmpty()) {
                mc.keyboard.setClipboard(selectedText);
            }
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
                lines.addAll(state != null ? state.lines : null);
                cursorLine = state != null ? state.cursorLine : 0;
                cursorPos = state != null ? state.cursorPos : 0;
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

        public void pushState() {
            redoStack.clear();
            undoStack.push(currentState());
            parentTab.checkIfChanged(lines);
        }

        private EditorState currentState() {
            return new EditorState(new ArrayList<>(lines), cursorLine, cursorPos);
        }

        private void scrollToCursor() {
            if (cursorLine < 0 || cursorLine >= lines.size()) return;
            int lineHeight = mc.textRenderer.fontHeight + 2;
            int cursorY = cursorLine * lineHeight;
            int halfHeight = height / 2;
            targetScrollOffsetVert = cursorY - halfHeight + lineHeight / 2f;
            if (targetScrollOffsetVert < 0) targetScrollOffsetVert = 0;
            int maxScrollVert = Math.max(0, lines.size() * lineHeight - height + lineHeight);
            if (targetScrollOffsetVert > maxScrollVert) targetScrollOffsetVert = maxScrollVert;
            String lineText = lines.get(cursorLine);
            int cursorX = mc.textRenderer.getWidth(lineText.substring(0, Math.min(cursorPos, lineText.length())));
            int halfWidth = width / 2;
            targetScrollOffsetHoriz = cursorX - halfWidth;
            if (targetScrollOffsetHoriz < 0) targetScrollOffsetHoriz = 0;
            int maxScrollHoriz = Math.max(0, getMaxLineWidth() - width + 100);
            if (targetScrollOffsetHoriz > maxScrollHoriz) targetScrollOffsetHoriz = maxScrollHoriz;
            ScrollBar.setPendingOffset((float) tabs.get(currentTabIndex).textEditor.targetScrollOffsetVert);
        }


        public void setCursor(int line, int start) {
            cursorLine = line;
            cursorPos = start;
            scrollToCursor();
        }

        public void updateBounds(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        static class EditorState {
            final ArrayList<String> lines;
            final int cursorLine;
            final int cursorPos;

            EditorState(ArrayList<String> lines, int cursorLine, int cursorPos) {
                this.lines = lines;
                this.cursorLine = cursorLine;
                this.cursorPos = cursorPos;
            }
        }

        public int getTotalScrollHeight() {
            int lineHeight = mc.textRenderer.fontHeight + 2;
            return lines.size() * lineHeight;
        }

        public int getScrollOffset() {
            return (int) smoothScrollOffsetVert;
        }

        public void setSearchResults(List<Position> results) {
            this.searchResults = results;
        }
    }

    static class Position {
        int line;
        int start;
        int end;
        Position(int line, int start, int end) {
            this.line = line;
            this.start = start;
            this.end = end;
        }
    }
}
