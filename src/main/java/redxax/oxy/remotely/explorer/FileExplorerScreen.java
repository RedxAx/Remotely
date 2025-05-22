package redxax.oxy.remotely.explorer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import redxax.oxy.remotely.Render.ContextMenu;
import redxax.oxy.remotely.Render.ScrollBar;
import redxax.oxy.remotely.Render.TabsBar;
import redxax.oxy.remotely.servers.RemoteHostInfo;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.util.ImageUtil.IconWithTooltip;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Sound;
import redxax.oxy.remotely.util.TextAnimator;
import redxax.oxy.remotely.servers.ServerManagerScreen;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.ImageUtil.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;
import static redxax.oxy.remotely.util.searchUtils.isFuzzyMatch;

public class FileExplorerScreen extends net.minecraft.client.gui.screen.Screen implements FileManager.FileManagerCallback {
    private final MinecraftClient minecraftClient;
    private final net.minecraft.client.gui.screen.Screen parent;
    private final ServerInfo serverInfo;
    private final List<EntryData> fileEntries;
    private final Object fileEntriesLock = new Object();
    private final int entryHeight = 20;
    private Path currentPath;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private long lastClickTime = 0;
    private int lastClickedIndex = -1;
    private static final int DOUBLE_CLICK_INTERVAL = 500;
    private final Deque<Path> history = new ArrayDeque<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final TextRenderer textRenderer;
    private final FileManager fileManager;
    private final boolean importMode;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".txt", ".md", ".json", ".yml", ".yaml", ".conf", ".properties",
            ".xml", ".cfg", ".sk", ".log", ".mcmeta", ".bat", ".sh", ".json5", ".jsonc",
            ".html", ".js", ".java", ".py", ".css", ".vsh", ".fsh", ".glsl", ".nu",
            ".bash", ".fish", ".toml", ".mcfunction", ".nbt"
    );
    private final ExecutorService directoryLoader = Executors.newSingleThreadExecutor();
    private static final Map<String, List<EntryData>> remoteCache = new ConcurrentHashMap<>();
    public static BufferedImage fileIcon;
    public static BufferedImage folderIcon;
    public static BufferedImage pinIcon;
    private final List<BufferedImage> loadingFrames = new ArrayList<>();
    private final List<Path> favoritePaths = new ArrayList<>();
    private final Object favoritePathsLock = new Object();
    private final Path favoritesFilePath = Paths.get(remotelyDir.toString(), "data", "favorites.json");
    private boolean fieldFocused = false;
    private final StringBuilder fieldText = new StringBuilder();
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private final Gson GSON = new Gson();
    private boolean shiftPressed = false;

    private enum Mode { PATH, SEARCH }
    private Mode currentMode = Mode.PATH;
    private final TextAnimator pathTextAnimator;
    public static final Path FILE_EXPLORER_TABS_FILE = Paths.get(remotelyDir.toString(), "data", "file_explorer_tabs.json");
    private static final String CURRENT_TAB_INDEX_KEY = "currentTabIndex";
    private Path renamePath = null;
    private final StringBuilder renameBuffer = new StringBuilder();
    private int renameCursorPos = 0;
    private boolean creatingNew = false;
    private Path newCreationPath = null;
    private int loadRequestId = 0;
    private static final int CHUNK_SIZE = 500;
    private int loadedCount = 0;
    private boolean hasMore = false;
    private boolean isLoadingMore = false;
    boolean canScroll = false;
    private final List<EntryData> fullEntries = new ArrayList<>();
    private static final int MAX_NAME_WIDTH = 500;
    public static BufferedImage appsIcon, textIcon, shadersIcon, scriptIcon, javaIcon, pyIcon, minecraftIcon, jsonIcon, jsIcon, cssIcon, zipIcon, audioIcon, videoIcon, imageIcon, docxIcon, pdfIcon, pptxIcon, xlsxIcon;
    public static IconWithTooltip closeIcon, backIcon, forwardIcon, searchIcon, reloadIcon, newFileIcon, copyIcon, editIcon, favoriteIcon, winExplorerIcon, pasteIcon, deleteIcon, cutIcon;
    private TabsBar<Tab> tabsBar;

    public static class EntryData {
        public Path path;
        public boolean isDirectory;
        public String size;
        public String created;
        public String displayName;
        public boolean isMatched;

        EntryData(Path p, boolean d, String s, String c, String dn) {
            path = p;
            isDirectory = d;
            size = s;
            created = c;
            displayName = dn;
        }
    }

    static class TabData {
        Path path;
        boolean isRemote;
        RemoteHostInfo remoteHostInfo;
        float smoothOffset;
        float targetOffset;
        int requestId;
        List<Path> selectedPaths;
        int lastSelectedIndex;
        TabData(Path path, boolean isRemote, RemoteHostInfo remoteHostInfo) {
            this.path = path;
            this.isRemote = isRemote;
            this.remoteHostInfo = remoteHostInfo;
            this.smoothOffset = 0;
            this.targetOffset = 0;
            this.requestId = -1;
            this.selectedPaths = new ArrayList<>();
            this.lastSelectedIndex = -1;
        }
    }

    public static class Tab {
        TabData tabData;
        String name;
        TextAnimator textAnimator;
        Tab(TabData tabData) {
            this.tabData = tabData;
            this.name = tabData.path.getFileName() != null ? tabData.path.getFileName().toString() : tabData.path.toString();
            this.textAnimator = new TextAnimator(this.name, 0, 30);
            this.textAnimator.start();
        }
        public void setName(String newName) {
            textAnimator.setOnAnimationEnd(() -> {
                this.name = newName;
                textAnimator.updateText(newName);
                textAnimator.setOnAnimationEnd(null);
            });
            textAnimator.reverse();
        }
        public String getAnimatedText() {
            return textAnimator.getCurrentText();
        }
        public int getCurrentWidth(TextRenderer textRenderer) {
            int TAB_PADDING = 5;
            return textRenderer.getWidth(getAnimatedText()) + 2 * TAB_PADDING;
        }
    }

    List<Tab> tabs = new ArrayList<>();
    int currentTabIndex = 0;
    private final int TAB_HEIGHT = 18;
    public FileExplorerScreen(MinecraftClient mc, net.minecraft.client.gui.screen.Screen parent, ServerInfo info) {
        this(mc, parent, info, false);
    }

    public FileExplorerScreen(MinecraftClient mc, net.minecraft.client.gui.screen.Screen parent, ServerInfo info, boolean importMode) {
        super(Text.literal("File Explorer"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
        this.fileEntries = new ArrayList<>();
        this.textRenderer = mc.textRenderer;
        if(serverInfo.isRemote && serverInfo.remoteHost == null){
            devPrint("[Explorer] Remote ServerInfo Has a Null RemoteHostInfo, Attempting to Remap...");
            List<TabData> loadedTabs = loadFileExplorerTabs();
            for(TabData td : loadedTabs){
                if(td.remoteHostInfo != null){
                    devPrint("[Explorer] Found a Remote Tab With a Null RemoteHostInfo, Remapping...");
                    serverInfo.remoteHost = td.remoteHostInfo;
                    break;
                }
            }
        }
        this.fileManager = new FileManager(this, serverInfo, serverInfo.isRemote && serverInfo.remoteHost != null ? serverInfo.remoteHost.getSSHManager() : null);
        this.importMode = importMode;
        if (serverInfo.isRemote) {
            String normalized = serverInfo.path == null ? "" : serverInfo.path.replace("\\", "/").trim();
            if (normalized.isEmpty()) {
                normalized = "/";
            }
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            this.currentPath = Paths.get(normalized);
        } else {
            this.currentPath = Paths.get(serverInfo.path).toAbsolutePath().normalize();
        }
        this.fieldText.append(currentPath);
        this.cursorPosition = fieldText.length();
        this.pathTextAnimator = new TextAnimator(currentPath.toString(), 0, 30);
        this.pathTextAnimator.start();
        tabs.add(new Tab(new TabData(currentPath, serverInfo.isRemote, serverInfo.remoteHost)));
        originalMCScale = minecraftClient.getWindow().getScaleFactor();
        targetScaleFactor = globalScaleFactor;
        minecraftClient.getWindow().setScaleFactor(globalScaleFactor);
    }

    @Override
    protected void init() {
        super.init();
        try {
            if (!Files.exists(favoritesFilePath.getParent())) {
                Files.createDirectories(favoritesFilePath.getParent());
            }
            if (Files.exists(favoritesFilePath)) {
                try (Reader reader = Files.newBufferedReader(favoritesFilePath)) {
                    List<String> lines = GSON.fromJson(reader, new TypeToken<List<String>>() {
                    }.getType());
                    synchronized (favoritePathsLock) {
                        favoritePaths.clear();
                        for (String line : lines) {
                            Path p = serverInfo.isRemote ? Paths.get(line.replace("\\", "/")) : Paths.get(line);
                            favoritePaths.add(p);
                        }
                    }
                } catch (Exception e) {
                    devPrint("Failed to load favorites: " + e);
                }
            }
            fileIcon = loadResourceIcon("/assets/remotely/icons/file.png");
            folderIcon = loadResourceIcon("/assets/remotely/icons/folder.png");
            pinIcon = loadResourceIcon("/assets/remotely/icons/pin.png");
            BufferedImage loadingAnim = loadSpriteSheet("/assets/remotely/icons/loadinganim.png");
            int frameWidth = 16;
            int frameHeight = 16;
            int rows = loadingAnim.getHeight() / frameHeight;
            for (int i = 0; i < rows; i++) {
                BufferedImage frame = loadingAnim.getSubimage(0, i * frameHeight, frameWidth, frameHeight);
                loadingFrames.add(frame);
            }
            appsIcon = loadResourceIcon("/assets/remotely/icons/apps.png");
            cssIcon = loadResourceIcon("/assets/remotely/icons/css.png");
            jsIcon = loadResourceIcon("/assets/remotely/icons/js.png");
            jsonIcon = loadResourceIcon("/assets/remotely/icons/json.png");
            minecraftIcon = loadResourceIcon("/assets/remotely/icons/minecraft.png");
            pyIcon = loadResourceIcon("/assets/remotely/icons/py.png");
            javaIcon = loadResourceIcon("/assets/remotely/icons/java.png");
            scriptIcon = loadResourceIcon("/assets/remotely/icons/script.png");
            shadersIcon = loadResourceIcon("/assets/remotely/icons/shaders.png");
            textIcon = loadResourceIcon("/assets/remotely/icons/text.png");
            zipIcon = loadResourceIcon("/assets/remotely/icons/zip.png");
            audioIcon = loadResourceIcon("/assets/remotely/icons/audio.png");
            videoIcon = loadResourceIcon("/assets/remotely/icons/video.png");
            imageIcon = loadResourceIcon("/assets/remotely/icons/image.png");
            docxIcon = loadResourceIcon("/assets/remotely/icons/docx.png");
            pdfIcon = loadResourceIcon("/assets/remotely/icons/pdf.png");
            pptxIcon = loadResourceIcon("/assets/remotely/icons/pptx.png");
            xlsxIcon = loadResourceIcon("/assets/remotely/icons/xlsx.png");
            closeIcon = new IconWithTooltip("/assets/remotely/icons/close.png", "");
            backIcon = new IconWithTooltip("/assets/remotely/icons/goback.png", "");
            forwardIcon = new IconWithTooltip("/assets/remotely/icons/goforward.png", "");
            searchIcon = new IconWithTooltip("/assets/remotely/icons/search.png", "§6Search §rFor Items");
            reloadIcon = new IconWithTooltip("/assets/remotely/icons/reload.png", "§bReload §rThe Current Directory");
            newFileIcon = new IconWithTooltip("/assets/remotely/icons/newFile.png", "§aCreate §ra New File / Folder");
            copyIcon = new IconWithTooltip("/assets/remotely/icons/copy.png", "§6Copy §rSelected Items");
            editIcon = new IconWithTooltip("/assets/remotely/icons/edit.png", "§6Rename §rSelected Items");
            favoriteIcon = new IconWithTooltip("/assets/remotely/icons/favorite.png", "Toggle §6Favorites §rFor Selected Items");
            winExplorerIcon = new IconWithTooltip("/assets/remotely/icons/winexplorer.png", "Open The Current Directory In §bWindows §6Explorer");
            pasteIcon = new IconWithTooltip("/assets/remotely/icons/paste.png", "§6Paste §rCopied Items");
            deleteIcon = new IconWithTooltip("/assets/remotely/icons/delete.png", "§cDelete §rSelected Items");
            cutIcon = new IconWithTooltip("/assets/remotely/icons/cut.png", "§6Cut §rSelected Items");
            List<TabData> loadedTabs = loadFileExplorerTabs().stream().distinct().toList();
            if (loadedTabs.isEmpty()) {
                tabs.add(new Tab(new TabData(currentPath, serverInfo.isRemote, serverInfo.remoteHost)));
            } else {
                for (TabData td : loadedTabs) {
                    if (tabs.stream().noneMatch(t -> t.tabData.path.equals(td.path) && t.tabData.isRemote == td.isRemote &&
                            remoteHostInfosEqual(t.tabData.remoteHostInfo, td.remoteHostInfo))) {
                        tabs.add(new Tab(td));
                    }
                }
                if (!tabs.isEmpty()) {
                    currentTabIndex = loadCurrentTabIndex();
                    if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
                        currentTabIndex = 0;
                    }
                    Tab selectedTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
                    currentPath = selectedTab.tabData.path;
                    serverInfo.isRemote = selectedTab.tabData.isRemote;
                    serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
                    selectedTab.tabData.targetOffset = 0;
                    selectedTab.tabData.smoothOffset = 0;
                    loadDirectory(selectedTab.tabData.path, false, false, true);
                } else {
                    tabs.add(new Tab(new TabData(currentPath, serverInfo.isRemote, serverInfo.remoteHost)));
                }
            }
        } catch (Exception e) {
            devPrint("Failed to initialize: " + e);
        }
        loadDirectory(currentPath, false, true, true);
        long windowHandle = minecraftClient.getWindow().getHandle();
        GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
            String[] droppedFiles = new String[count];
            for (int i = 0; i < count; i++) {
                droppedFiles[i] = GLFWDropCallback.getName(names, i);
            }
            handleFileDrop(droppedFiles);
        });
        List<TabsBar.Tab<Tab>> tabList = new ArrayList<>();
        for (Tab t : tabs) {
            tabList.add(new TabsBar.Tab<>(t.name, false, t));
        }
        tabsBar = new TabsBar<>(tabList);
        tabsBar.setActiveTab(currentTabIndex);
        tabsBar.setHasPlus(true);
        tabsBar.setAllowClose(tabCloseButtons);
        tabsBar.setAllowRename(false);
        tabsBar.setAllowDrag(true);
        tabsBar.setAllowScroll(true);
        tabsBar.setTabBarBounds(5, 35, this.width - 5, TAB_HEIGHT);
        tabsBar.setOnTabOrderChanged(() -> {
            List<Tab> newTabs = new ArrayList<>();
            for (TabsBar.Tab<Tab> t : tabsBar.getTabs()) newTabs.add(t.data);
            tabs.clear();
            tabs.addAll(newTabs);
            currentTabIndex = tabsBar.getActiveTab();
            if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
                currentTabIndex = 0;
            }
            Tab selectedTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
            currentPath = selectedTab.tabData.path;
            serverInfo.isRemote = selectedTab.tabData.isRemote;
            serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
            selectedTab.tabData.targetOffset = 0;
            selectedTab.tabData.smoothOffset = 0;
            loadDirectory(selectedTab.tabData.path, false, false, true);
            saveFileExplorerTabs(tabs.stream().map(t -> t.tabData).collect(Collectors.toList()), currentTabIndex);
        });
        tabsBar.setOnTabClosed(() -> {
            int idx = tabsBar.getActiveTab();
            tabs.remove(idx);
            if (tabs.isEmpty()) {
                minecraftClient.setScreen(parent);
            } else {
                updateTab();
                saveFileExplorerTabs(tabs.stream().map(t -> t.tabData).collect(Collectors.toList()), currentTabIndex);
            }
        });
        tabsBar.setOnTabSelected(() -> {
            currentTabIndex = tabsBar.getActiveTab();
            updateTab();
            saveFileExplorerTabs(tabs.stream().map(t -> t.tabData).collect(Collectors.toList()), currentTabIndex);
        });
        tabsBar.setOnTabPlus(() -> {
            playSound(Sound.SCREEN);
            minecraftClient.setScreen(new DeskSelectionScreen(minecraftClient, this));
        });
        tabsBar.setOnTabRenamed(() -> {
            int idx = tabsBar.getRenamingTab();
            if (idx >= 0 && idx < tabs.size()) {
                tabs.get(idx).setName(tabsBar.getTabs().get(idx).name);
                saveFileExplorerTabs(tabs.stream().map(t -> t.tabData).collect(Collectors.toList()), currentTabIndex);
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int titleBarHeight = 30;
        int tabBarY = titleBarHeight + 5;
        drawScreenHeader(context, width, height, width - 5, mouseX, mouseY, this, minecraftClient, closeIcon, (tabs.get(Math.min(currentTabIndex, tabs.size() - 1)).tabData.selectedPaths.isEmpty() ? !FileManager.getClipboard().isEmpty() ? pasteIcon : null : shiftPressed ? pasteIcon : copyIcon), (tabs.get(Math.min(currentTabIndex, tabs.size() - 1)).tabData.selectedPaths.isEmpty() ? null : shiftPressed ? deleteIcon : editIcon), (tabs.get(Math.min(currentTabIndex, tabs.size() - 1)).tabData.selectedPaths.isEmpty() ? null : shiftPressed ? cutIcon : favoriteIcon), backIcon, forwardIcon, newFileIcon, winExplorerIcon, shiftPressed ? reloadIcon : searchIcon);
        tabsBar.renderTabsBar(context, this.textRenderer, tabsBar, mouseX, mouseY, Config.shadow);
        currentTabIndex = tabsBar.getActiveTab();
        tabsBar.setActiveTabName(tabs.get(Math.min(currentTabIndex, tabs.size() - 1)).getAnimatedText());
        int explorerY = tabBarY + TAB_HEIGHT + 30;
        int explorerHeight = this.height - explorerY - 5;
        int explorerX = 5;
        int explorerWidth = this.width - 10;
        int headerY = explorerY - 23;
        Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
        float pathScrollOffset = 0;
        float pathTargetScrollOffset = 0;
        drawSearchBar(context, textRenderer, fieldText, fieldFocused, cursorPosition, selectionStart, selectionEnd, pathTargetScrollOffset, currentMode == Mode.SEARCH, "FileExplorerScreen", mouseX, mouseY, "Search For Files or Directories");
        context.fill(explorerX, headerY, explorerX + explorerWidth, headerY + 27, Config.innerBackgroundColor);
        drawInnerBorder(context, explorerX, headerY, explorerWidth, 23, innerBorderColor);
        drawOuterBorder(context, explorerX, headerY, explorerWidth, 23, innerBackgroundColor);
        context.drawText(this.textRenderer, Text.literal("Name"), explorerX + 10, headerY + 5, globalTextColor, Config.shadow);
        if (!serverInfo.isRemote) {
            int createdX = explorerX + explorerWidth - 100;
            int sizeX = createdX - 100;
            context.drawText(this.textRenderer, Text.literal("Created"), createdX, headerY + 5, globalTextColor, Config.shadow);
            context.drawText(this.textRenderer, Text.literal("Size"), sizeX, headerY + 5, globalTextColor, Config.shadow);
        }
        if (loading && currentTab.tabData.isRemote) {
            return;
        }
        currentTab.tabData.smoothOffset += (currentTab.tabData.targetOffset - currentTab.tabData.smoothOffset) * globalScrollSpeed * deltaTime;
        List<EntryData> entriesToRender;
        synchronized (fileEntriesLock) {
            entriesToRender = new ArrayList<>(fileEntries);
        }
        int gap = 1;
        int itemHeight = entryHeight + gap;
        int visibleEntries = explorerHeight / itemHeight;
        int totalHeight = entriesToRender.size() * itemHeight;
        int startIndex = (int) Math.floor(currentTab.tabData.smoothOffset / itemHeight);
        int endIndex = startIndex + visibleEntries + 3;
        if (endIndex > entriesToRender.size()) endIndex = entriesToRender.size();
        context.enableScissor(explorerX -1, explorerY, explorerX + explorerWidth +1, explorerY + explorerHeight);
        if (entriesToRender.isEmpty() && !loading) {
            if (!serverInfo.isRemote) {
                context.drawText(this.textRenderer, Text.literal("No files/folders in this directory."), explorerX + explorerWidth / 2 - textRenderer.getWidth("No files/folders in this directory.") / 2, explorerY + explorerHeight / 2, globalTextColor, shadow);
            } else {
                if (serverInfo.remoteHost != null && serverInfo.remoteHost.getSSHManager().isSFTPConnected()) {
                    context.drawText(this.textRenderer, Text.literal("No files/folders in this directory."), explorerX + explorerWidth / 2 - textRenderer.getWidth("No files/folders in this directory.") / 2, explorerY + explorerHeight / 2, globalTextColor, shadow);
                } else {
                    context.drawText(this.textRenderer, Text.literal("Connection lost or SFTP error."), explorerX + explorerWidth / 2 - textRenderer.getWidth("Connection lost or SFTP error.") / 2, explorerY + explorerHeight / 2, globalTextColor, shadow);
                }
            }
        } else {
            for (int entryIndex = startIndex; entryIndex < endIndex; entryIndex++) {
                EntryData entry = entriesToRender.get(entryIndex);
                int entryY = explorerY + (entryIndex * itemHeight) - (int) currentTab.tabData.smoothOffset;
                boolean hovered = mouseX >= explorerX && mouseX <= explorerX + explorerWidth && mouseY >= entryY && mouseY < entryY + entryHeight;
                boolean isSelected = currentTab.tabData.selectedPaths.contains(entry.path);
                boolean isFavorite;
                synchronized (favoritePathsLock) {
                    isFavorite = favoritePaths.contains(entry.path);
                }
                drawExplorerElements(context, hovered && !ContextMenu.isOpen(), isSelected, isFavorite, entry, explorerX, entryY, explorerWidth, entryHeight, this.textRenderer, serverInfo.isRemote, String.valueOf(renamePath), renameBuffer, renameCursorPos);
            }
        }
        context.disableScissor();
        if (currentTab.tabData.smoothOffset > 2) {
            context.fillGradient(explorerX, explorerY, explorerX + explorerWidth, explorerY + 10, 0x80000000, 0x00000000);
        }
        if (currentTab.tabData.smoothOffset < Math.max(0, totalHeight - explorerHeight)) {
            context.fillGradient(explorerX, explorerY + explorerHeight - 10, explorerX + explorerWidth, explorerY + explorerHeight + 2, 0x00000000, 0x80000000);
        }
        ScrollBar.render(context, this, mouseX, mouseY, totalHeight + explorerY - tabBarY - 30, currentTab.tabData.targetOffset);
        canScroll = visibleEntries < entriesToRender.size();
        currentTab.tabData.targetOffset = ScrollBar.getPendingOffset();
        if (ContextMenu.isOpen()) {
            ContextMenu.renderMenu(context, minecraftClient, mouseX, mouseY);
        }
        loadMoreIfNeeded(explorerHeight);
        animatedScaling(this);
    }

    private boolean remoteHostInfosEqual(RemoteHostInfo a, RemoteHostInfo b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getUser(), b.getUser()) &&
                Objects.equals(a.getIp(), b.getIp()) &&
                a.getPort() == b.getPort() &&
                Objects.equals(a.getPassword(), b.getPassword());
    }

    public static BufferedImage getIconForFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".exe")) {
            return appsIcon;
        }
        if (fileName.endsWith(".css")) {
            return cssIcon;
        }
        if (fileName.endsWith(".js")) {
            return jsIcon;
        }
        if (fileName.endsWith(".json")) {
            return jsonIcon;
        }
        if (fileName.endsWith(".mcfunction") || fileName.endsWith(".mcmeta")) {
            return minecraftIcon;
        }
        if (fileName.endsWith(".py")) {
            return pyIcon;
        }
        if (fileName.endsWith(".java") || fileName.endsWith(".class") || fileName.endsWith(".jar")) {
            return javaIcon;
        }
        if (fileName.endsWith(".bat") || fileName.endsWith(".sh") || fileName.endsWith(".bash") || fileName.endsWith(".sk")) {
            return scriptIcon;
        }
        if (fileName.endsWith(".vsh") || fileName.endsWith(".fsh") || fileName.endsWith(".glsl")) {
            return shadersIcon;
        }
        if (fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".tar") || fileName.endsWith(".gz") || fileName.endsWith(".7z") || fileName.endsWith(".tar.gz") || fileName.endsWith(".tar.bz2")) {
            return zipIcon;
        }
        if (fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".flac") || fileName.endsWith(".aac") || fileName.endsWith(".ogg") || fileName.endsWith(".m4a") || fileName.endsWith(".wma")) {
            return audioIcon;
        }
        if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mkv") || fileName.endsWith(".mov") || fileName.endsWith(".flv") || fileName.endsWith(".wmv")) {
            return videoIcon;
        }
        if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".webp") || fileName.endsWith(".gif")) {
            return imageIcon;
        }
        if (fileName.endsWith(".docx") || fileName.endsWith(".doc")) {
            return docxIcon;
        }
        if (fileName.endsWith(".pdf")) {
            return pdfIcon;
        }
        if (fileName.endsWith(".pptx") || fileName.endsWith(".ppt")) {
            return pptxIcon;
        }
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return xlsxIcon;
        }
        if (fileName.endsWith(".txt") || fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".properties") ||
                fileName.endsWith(".toml") || fileName.endsWith(".md") || fileName.endsWith(".log") || fileName.endsWith(".html")) {
            return textIcon;
        }
        return fileIcon;
    }

    private void loadMoreIfNeeded(int explorerHeight) {
        int gap = 1;
        int itemHeight = entryHeight + gap;
        Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
        if (hasMore && !isLoadingMore && currentTab.tabData.smoothOffset + explorerHeight >= fileEntries.size() * itemHeight - (itemHeight * 2)) {
            isLoadingMore = true;
            loadMoreEntries();
        }
        currentTab.tabData.targetOffset = Math.max(0, Math.min(currentTab.tabData.targetOffset, Math.max(0, totalHeight(fileEntries, itemHeight) - explorerHeight)));
    }

    private int totalHeight(List<EntryData> entries, int itemHeight) {
        return entries.size() * itemHeight;
    }

    private void loadMoreEntries() {
        directoryLoader.submit(() -> {
            List<EntryData> chunk = new ArrayList<>();
            int end = Math.min(loadedCount + CHUNK_SIZE, fullEntries.size());
            for (int i = loadedCount; i < end; i++) {
                chunk.add(fullEntries.get(i));
            }
            loadedCount += chunk.size();
            if (loadedCount >= fullEntries.size()) {
                hasMore = false;
            }
            synchronized (fileEntriesLock) {
                fileEntries.addAll(chunk);
            }
            isLoadingMore = false;
        });
    }

    private void updatePathInfo() {
        fieldText.setLength(0);
        fieldText.append(currentPath.toString());
        cursorPosition = fieldText.length();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) shiftPressed = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tabsBar.handleTabsBarKey(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        shiftPressed = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
        if (renamePath != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                renameSelectedFile();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                renamePath = null;
                renameBuffer.setLength(0);
                creatingNew = false;
                newCreationPath = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (renameCursorPos > 0) {
                    renameBuffer.deleteCharAt(renameCursorPos - 1);
                    renameCursorPos--;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (renameCursorPos < renameBuffer.length()) {
                    renameBuffer.deleteCharAt(renameCursorPos);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                if (renameCursorPos > 0) {
                    renameCursorPos--;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (renameCursorPos < renameBuffer.length()) {
                    renameCursorPos++;
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (fieldFocused) {
            if (currentMode == Mode.PATH) {
                if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
                    currentMode = Mode.SEARCH;
                    fieldText.setLength(0);
                    cursorPosition = 0;
                    selectionStart = -1;
                    selectionEnd = -1;
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (ctrl) {
                        deleteWord();
                    } else {
                        if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                            int selStart = Math.min(selectionStart, selectionEnd);
                            int selEnd = Math.max(selectionStart, selectionEnd);
                            fieldText.delete(selStart, selEnd);
                            cursorPosition = selStart;
                            selectionStart = -1;
                            selectionEnd = -1;
                        } else {
                            if (cursorPosition > 0) {
                                fieldText.deleteCharAt(cursorPosition - 1);
                                cursorPosition--;
                            }
                        }
                    }
                    if (currentMode == Mode.SEARCH) {
                        filterFileEntries();
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    fieldFocused = false;
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DELETE) {
                    if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                        int selStart = Math.min(selectionStart, selectionEnd);
                        int selEnd = Math.max(selectionStart, selectionEnd);
                        fieldText.delete(selStart, selEnd);
                        cursorPosition = selStart;
                        selectionStart = -1;
                        selectionEnd = -1;
                    } else {
                        if (cursorPosition < fieldText.length()) {
                            fieldText.deleteCharAt(cursorPosition);
                        }
                    }
                    return true;
                }
                if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                    selectionStart = 0;
                    selectionEnd = fieldText.length();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    if (cursorPosition > 0) {
                        cursorPosition--;
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    if (cursorPosition < fieldText.length()) {
                        cursorPosition++;
                    }
                    return true;
                }
                if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                    pasteClipboard();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER) {
                    executePath();
                    return true;
                }
            } else if (currentMode == Mode.SEARCH) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    currentMode = Mode.PATH;
                    updatePathInfo();
                    fieldFocused = false;
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                        int selStart = Math.min(selectionStart, selectionEnd);
                        int selEnd = Math.max(selectionStart, selectionEnd);
                        fieldText.delete(selStart, selEnd);
                        cursorPosition = selStart;
                        selectionStart = -1;
                        selectionEnd = -1;
                    } else {
                        if (cursorPosition > 0) {
                            fieldText.deleteCharAt(cursorPosition - 1);
                            cursorPosition--;
                        }
                    }
                    filterFileEntries();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DELETE) {
                    if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                        int selStart = Math.min(selectionStart, selectionEnd);
                        int selEnd = Math.max(selectionStart, selectionEnd);
                        fieldText.delete(selStart, selEnd);
                        cursorPosition = selStart;
                        selectionStart = -1;
                        selectionEnd = -1;
                    } else {
                        if (cursorPosition < fieldText.length()) {
                            fieldText.deleteCharAt(cursorPosition);
                        }
                    }
                    filterFileEntries();
                    return true;
                }
                if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                    selectionStart = 0;
                    selectionEnd = fieldText.length();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    if (cursorPosition > 0) {
                        cursorPosition--;
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    if (cursorPosition < fieldText.length()) {
                        cursorPosition++;
                    }
                    return true;
                }
                if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                    pasteClipboard();
                    filterFileEntries();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER) {
                    currentMode = Mode.PATH;
                    updatePathInfo();
                    fieldFocused = false;
                    return true;
                }
            }
        }
        if (keyCode == this.minecraftClient.options.backKey.getDefaultKey().getCode() && keyCode != GLFW.GLFW_KEY_S) {
            navigateUp();
            return true;
        }
        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_C) {
                fileManager.copySelected(currentTab.tabData.selectedPaths);
                showNotification("Copied to clipboard", Notification.Type.INFO);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                fileManager.cutSelected(currentTab.tabData.selectedPaths);
                showNotification("Cut to clipboard", Notification.Type.INFO);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                if (currentMode != Mode.SEARCH) {
                    clearFieldBar();
                    pasteClipboard();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (serverInfo.isRemote) {
                    showNotification("Undo not supported for remote files.", Notification.Type.ERROR);
                    return true;
                }
                fileManager.undo(currentPath);
                showNotification("Undo action", Notification.Type.INFO);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_F) {
                currentMode = Mode.SEARCH;
                fieldFocused = true;
                fieldText.setLength(0);
                cursorPosition = 0;
                selectionStart = -1;
                selectionEnd = -1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_R) {
                loadDirectory(currentPath, false, true, true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_T) {
                synchronized (favoritePathsLock) {
                    for (Path p : currentTab.tabData.selectedPaths) {
                        if (!favoritePaths.contains(p)) {
                            favoritePaths.add(p);
                        } else {
                            favoritePaths.remove(p);
                        }
                    }
                    saveFavorites();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_N) {
                return createFile();
            }
            if (keyCode == GLFW.GLFW_KEY_E) {
                if (!currentTab.tabData.selectedPaths.isEmpty()) {
                    renamePath = currentTab.tabData.selectedPaths.get(0);
                    renameBuffer.setLength(0);
                    renameBuffer.append(renamePath.getFileName().toString());
                    renameCursorPos = renameBuffer.length();
                }
                return true;
            }
        }
        if (currentMode == Mode.PATH && keyCode == GLFW.GLFW_KEY_DELETE) {
            fileManager.deleteSelected(currentTab.tabData.selectedPaths, currentPath);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraftClient.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void clearFieldBar() {
        if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
            int selStart = Math.min(selectionStart, selectionEnd);
            int selEnd = Math.max(selectionStart, selectionEnd);
            fieldText.delete(selStart, selEnd);
            cursorPosition = selStart;
            selectionStart = -1;
            selectionEnd = -1;
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (tabsBar.handleTabsBarChar(chr)) {
            return true;
        }        if (renamePath != null) {
            if (chr == '\b' || chr == '\r' || chr == '\n') {
                return false;
            }
            renameBuffer.insert(renameCursorPos, chr);
            renameCursorPos++;
            return true;
        }
        if (fieldFocused) {
            if (chr == '\b' || chr == '\n') {
                return false;
            }
            if (currentMode == Mode.PATH) {
                clearFieldBar();
                fieldText.insert(cursorPosition, chr);
                cursorPosition++;
                if (currentMode == Mode.SEARCH) {
                    filterFileEntries();
                }
                return true;
            } else if (currentMode == Mode.SEARCH) {
                clearFieldBar();
                fieldText.insert(cursorPosition, chr);
                cursorPosition++;
                filterFileEntries();
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, /*? !=1.20.1 {*/ double horizontalAmount, /*?}*/ double verticalAmount) {
        if (tabsBar.handleTabsBarScroll(verticalAmount, mouseX, mouseY)) {
            return true;
        }
        scaleScroll(verticalAmount);
        boolean ctrl = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) || (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
        float scrollMultiplier = ctrl ? 5.0f : 1.0f;
        int gap = 1;
        int itemHeight = entryHeight + gap;
        Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
        currentTab.tabData.targetOffset -= (float) (verticalAmount * itemHeight * 0.5f * scrollMultiplier);
        List<EntryData> entriesToRender;
        synchronized (fileEntriesLock) {
            entriesToRender = new ArrayList<>(fileEntries);
        }
        int explorerHeight = this.height - (30 + TAB_HEIGHT + 5 + 30 + 10);
        int totalHeight = entriesToRender.size() * (entryHeight + gap);
        currentTab.tabData.targetOffset = Math.max(0, Math.min(currentTab.tabData.targetOffset, Math.max(0, totalHeight - explorerHeight)));
        ScrollBar.setPendingOffset(currentTab.tabData.targetOffset);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (tabsBar.handleTabsBarDrag(button, deltaX)) {
            return true;
        }        int gap = 1;
        int itemHeight = entryHeight + gap;
        int totalHeight = fileEntries.size() * itemHeight;
        if (ScrollBar.handleMouseDragged(this, (int) mouseY, totalHeight + 28)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tabsBar.handleTabsBarRelease(button)) {
            return true;
        }        if (ScrollBar.handleMouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ContextMenu.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        ContextMenu.hide();
        if (tabsBar.handleTabsBarMouse((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        int gap = 1;
        int itemHeight = entryHeight + gap;
        int totalHeight = fileEntries.size() * itemHeight;
        if (ScrollBar.handleMousePressed(this, (int) mouseX, (int) mouseY, totalHeight, tabs.get(Math.min(currentTabIndex, tabs.size() - 1)).tabData.smoothOffset)){
            return true;
        }
        boolean handled = false;
        int titleBarHeightLocal = 30;
        int tabBarYLocal = titleBarHeightLocal + 5;
        int tabBarHeight = TAB_HEIGHT;
        int tabX = 5;
        Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = tab.getCurrentWidth(textRenderer);
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabBarYLocal && mouseY <= tabBarYLocal + tabBarHeight) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    playSound(Sound.CLOSETAB);
                    closeTab(i);
                    tabsBar.closeTab(i);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
                    playSound(Sound.RIGHTCLICK);
                    ContextMenu.hide();
                    int finalI = i;
                    ContextMenu.addItem("Close", () -> {
                        closeTab(finalI);
                        tabsBar.closeTab(finalI);
                    }, false, false, false, "");
                    int finalI1 = i;
                    ContextMenu.addItem("Duplicate", () -> {
                        TabData originalTabData = tabs.get(finalI1).tabData;
                        TabData newTabData = new TabData(originalTabData.path, originalTabData.isRemote, originalTabData.remoteHostInfo);
                        Tab newTab = new Tab(newTabData);
                        tabs.add(newTab);
                        currentTabIndex = tabs.size() - 1;
                        loadDirectory(newTabData.path, false, false, false);
                        saveFileExplorerTabs(tabs.stream().map(t1 -> new TabData(t1.tabData.path, t1.tabData.isRemote, t1.tabData.remoteHostInfo)).collect(Collectors.toList()), Math.min(currentTabIndex, tabs.size() - 1));
                    }, false, false, false, "Duplicate This Tab.");
                    ContextMenu.addItem("Externally", () -> openExternally(tabs.get(finalI1).tabData.path), false, false, false, "Open In The Windows File Explorer.");
                    ContextMenu.addItem("Rename Tab", () -> tabsBar.renameTab(finalI), false, false, false, "Are You a Psychopath or Something?");
                    ContextMenu.show((int) mouseX, (int) mouseY, 60, this.width, this.height);
                }
                handled = true;
                break;
            }
            int TAB_GAP = 5;
            tabX += tab.getCurrentWidth(textRenderer) + TAB_GAP;
        }
        if (!handled) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1 || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                long currentTime = System.currentTimeMillis();
                boolean isDoubleClick = lastClickedIndex != -1 && (currentTime - lastClickTime) < DOUBLE_CLICK_INTERVAL;
                lastClickTime = currentTime;
                int explorerYLocal = tabBarYLocal + tabBarHeight + 30;
                int explorerHeightLocal = this.height - explorerYLocal - 10;
                int explorerXLocal = 5;
                int explorerWidthLocal = this.width - 10;
                int gapLocal = 1;
                if (mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24) {
                    if (parent == null) playSound(Sound.SCREEN);
                    minecraftClient.setScreen(parent);
                    return true;
                }
                if (mouseX >= width - 46 && mouseX <= width - 29 && mouseY >= 6 && mouseY <= 24) {
                    if (shiftPressed) {
                        playSound(Sound.PASTE);
                        fileManager.paste(currentPath);
                        showNotification("Pasted From Clipboard", Notification.Type.INFO);
                    } else {
                        if (!FileManager.getClipboard().isEmpty() && currentTab.tabData.selectedPaths.isEmpty()) {
                            playSound(Sound.PASTE);
                            fileManager.paste(currentPath);
                            showNotification("Pasted From Clipboard", Notification.Type.INFO);
                        } else if (!currentTab.tabData.selectedPaths.isEmpty()) {
                            playSound(Sound.COPY);
                            fileManager.copySelected(currentTab.tabData.selectedPaths);
                            showNotification("Copied To Clipboard", Notification.Type.INFO);
                        }
                    }
                    return true;
                }
                if (mouseX >= width - 69 && mouseX <= width - 52 && mouseY >= 6 && mouseY <= 24) {
                    if (shiftPressed) {
                        playSound(Sound.DELETE);
                        fileManager.deleteSelected(currentTab.tabData.selectedPaths, currentPath);
                    }
                    else {
                        if (!currentTab.tabData.selectedPaths.isEmpty()) {
                            playSound(Sound.CLICK);
                            renamePath = currentTab.tabData.selectedPaths.get(0);
                            renameBuffer.setLength(0);
                            renameBuffer.append(renamePath.getFileName().toString());
                            renameCursorPos = renameBuffer.length();
                        }
                    }
                    return true;
                }
                if (mouseX >= width - 92 && mouseX <= width - 75 && mouseY >= 6 && mouseY <= 24) {
                    if (shiftPressed) {
                        playSound(Sound.COPY);
                        fileManager.cutSelected(currentTab.tabData.selectedPaths);
                    } else {
                        synchronized (favoritePathsLock) {
                            playSound(Sound.CLICK);
                            for (Path p : currentTab.tabData.selectedPaths) {
                                if (!favoritePaths.contains(p)) {
                                    favoritePaths.add(p);
                                } else {
                                    favoritePaths.remove(p);
                                }
                            }
                            saveFavorites();
                        }
                    }
                    return true;
                }
                if (mouseX >= 5 && mouseX <= 22 && mouseY >= 6 && mouseY <= 24) {
                    playSound(Sound.CLICK);
                    navigateUp();
                    return true;
                }
                if (mouseX >= 28 && mouseX <= 45 && mouseY >= 6 && mouseY <= 24) {
                    playSound(Sound.CLICK);
                    navigateBack();
                    return true;
                }
                if (mouseX >= 51 && mouseX <= 68 && mouseY >= 6 && mouseY <= 24) {
                    playSound(Sound.CREATE);
                    createFile();
                    return true;
                }
                if (mouseX >= 74 && mouseX <= 91 && mouseY >= 6 && mouseY <= 24) {
                    playSound(Sound.CLICK);
                    openExternally(currentPath);
                    return true;
                }
                int searchBarWidth = 200;
                int specialIconX = (width - searchBarWidth) / 2 - 23;
                if (mouseX >= specialIconX && mouseX <= specialIconX + 17 && mouseY >= 6 && mouseY <= 24) {
                    if (!shiftPressed) {
                        playSound(Sound.SEARCH);
                        if (currentMode == Mode.SEARCH) {
                            currentMode = Mode.PATH;
                            updatePathInfo();
                        } else {
                            currentMode = Mode.SEARCH;
                            fieldFocused = true;
                            fieldText.setLength(0);
                            cursorPosition = 0;
                            selectionStart = -1;
                            selectionEnd = -1;
                        }
                        return true;
                    } else {
                        playSound(Sound.CLICK);
                        fieldFocused = false;
                        refreshDirectory(currentPath);
                    }
                }
                if (mouseX >= explorerXLocal && mouseX <= explorerXLocal + explorerWidthLocal && mouseY >= explorerYLocal && mouseY <= explorerYLocal + explorerHeightLocal) {
                    int relativeY = (int) mouseY - explorerYLocal + (int) currentTab.tabData.smoothOffset;
                    int clickedIndex = relativeY / (entryHeight + gapLocal);
                    List<EntryData> entriesToRender;
                    synchronized (fileEntriesLock) {
                        entriesToRender = new ArrayList<>(fileEntries);
                    }
                    if (clickedIndex >= 0 && clickedIndex < entriesToRender.size()) {
                        fieldFocused = false;
                        currentMode = Mode.PATH;
                        updatePathInfo();
                        EntryData entryData = entriesToRender.get(clickedIndex);
                        Path selectedPath = entryData.path;
                        if (isDoubleClick && lastClickedIndex == clickedIndex && button == GLFW.GLFW_MOUSE_BUTTON_1) {
                            if (entryData.isDirectory) {
                                playSound(Sound.CLICK);
                                Tab selectedTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
                                selectedTab.tabData.path = selectedPath;
                                selectedTab.setName(selectedPath.getFileName() != null ? selectedPath.getFileName().toString() : selectedPath.toString());
                                loadDirectory(selectedPath, false, false, false);
                            } else {
                                if (importMode && selectedPath.getFileName().toString().equalsIgnoreCase("server.jar")) {
                                    if (parent instanceof ServerManagerScreen sms) {
                                        String folderName = selectedPath.getParent().getFileName().toString();
                                        sms.importServerJar(selectedPath, folderName);
                                    }
                                    minecraftClient.setScreen(parent);
                                    return true;
                                }
                                if (isSupportedFile(selectedPath)) {
                                    minecraftClient.setScreen(new FileEditorScreen(minecraftClient, this, selectedPath, serverInfo));
                                } else {
                                    openExternally(selectedPath);
                                }
                            }
                            lastClickedIndex = -1;
                        } else {
                            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && entryData.isDirectory) {
                                playSound(Sound.CREATE);
                                TabData newTabData = new TabData(selectedPath, serverInfo.isRemote, serverInfo.remoteHost);
                                tabs.add(new Tab(newTabData));
                                currentTabIndex = tabs.size() - 1;
                                loadDirectory(newTabData.path, false, false, true);
                                Tab newTab = tabs.get(0);
                                tabsBar.getTabs().add(new TabsBar.Tab<>(newTab.name, false, newTab));
                                tabsBar.setActiveTab(Math.min(currentTabIndex, tabs.size() - 1));
                                return true;
                            }
                            lastClickedIndex = clickedIndex;
                            boolean ctrlPressed = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) || (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
                            if (ctrlPressed) {
                                playSound(Sound.SELECT);
                                if (currentTab.tabData.selectedPaths.contains(selectedPath)) {
                                    currentTab.tabData.selectedPaths.remove(selectedPath);
                                } else {
                                    currentTab.tabData.selectedPaths.add(selectedPath);
                                }
                                currentTab.tabData.lastSelectedIndex = clickedIndex;
                            } else if (shiftPressed && currentTab.tabData.lastSelectedIndex != -1) {
                                playSound(Sound.SELECT);
                                int start = Math.min(currentTab.tabData.lastSelectedIndex, clickedIndex);
                                int end = Math.max(currentTab.tabData.lastSelectedIndex, clickedIndex);
                                for (int iIdx = start; iIdx <= end; iIdx++) {
                                    if (iIdx >= 0 && iIdx < entriesToRender.size()) {
                                        Path path = entriesToRender.get(iIdx).path;
                                        if (!currentTab.tabData.selectedPaths.contains(path)) {
                                            currentTab.tabData.selectedPaths.add(path);
                                        }
                                    }
                                }
                            } else {
                                currentTab.tabData.selectedPaths.clear();
                                currentTab.tabData.selectedPaths.add(selectedPath);
                                if (selectedPath != null) playSound(Sound.SELECT);
                                currentTab.tabData.lastSelectedIndex = clickedIndex;
                            }
                        }
                        return true;
                    }
                }
                int fieldWidthDynamic = 200;
                int fieldX = (this.width - fieldWidthDynamic) / 2;
                int fieldY = 5;
                int fieldHeight = titleBarHeightLocal - 10;
                if (mouseX >= fieldX && mouseX <= fieldX + fieldWidthDynamic && mouseY >= fieldY && mouseY <= fieldY + fieldHeight) {
                    playSound(Sound.SEARCH);
                    fieldFocused = true;
                    cursorPosition = fieldText.length();
                    selectionStart = -1;
                    selectionEnd = -1;
                    return true;
                } else {
                    fieldFocused = false;
                    currentMode = Mode.PATH;
                    updatePathInfo();
                    currentTab.tabData.selectedPaths.clear();
                }
                return false;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
                playSound(Sound.CLICK);
                navigateUp();
                return true;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
                playSound(Sound.CLICK);
                navigateBack();
                return true;
            }
        }
        if (!handled && currentMode == Mode.SEARCH) {
            currentMode = Mode.PATH;
            updatePathInfo();
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
            int explorerYLocal = tabBarYLocal + tabBarHeight + 30;
            int explorerHeightLocal = this.height - explorerYLocal - 10;
            int explorerXLocal = 5;
            int explorerWidthLocal = this.width - 10;
            if (mouseX >= explorerXLocal && mouseX <= explorerXLocal + explorerWidthLocal && mouseY >= explorerYLocal && mouseY <= explorerYLocal + explorerHeightLocal) {
                int relativeY = (int) mouseY - explorerYLocal + (int) currentTab.tabData.smoothOffset;
                int clickedIndex = relativeY / (entryHeight + 1);
                List<EntryData> entriesToRender;
                synchronized (fileEntriesLock) {
                    entriesToRender = new ArrayList<>(fileEntries);
                }
                if (clickedIndex >= 0 && clickedIndex < entriesToRender.size()) {
                    EntryData entryData = entriesToRender.get(clickedIndex);
                    ContextMenu.addItem("Copy", () -> {
                        playSound(Sound.COPY);
                        fileManager.copySelected(currentTab.tabData.selectedPaths);
                    }, false, false, false, "");
                    ContextMenu.addItem("Cut", () -> {
                        playSound(Sound.COPY);
                        fileManager.cutSelected(currentTab.tabData.selectedPaths);
                    }, false, false, false, "");
                    ContextMenu.addItem("Paste", () -> {
                        playSound(Sound.PASTE);
                        fileManager.paste(currentPath);
                    }, false, false, false, "");
                    ContextMenu.addItem("Delete", () -> {
                        playSound(Sound.DELETE);
                        fileManager.deleteSelected(currentTab.tabData.selectedPaths, currentPath);
                    }, false, false, false, "");
                    ContextMenu.addItem("Copy Path", () -> {
                        playSound(Sound.COPY);
                        String quotedPath = "\"" + entryData.path.toString() + "\"";
                        minecraftClient.keyboard.setClipboard(quotedPath);
                    }, false, false, false, "");
                    ContextMenu.addItem("Undo", () -> {
                        if (serverInfo.isRemote) {
                            showNotification("Undo not supported for remote files.", Notification.Type.ERROR);
                        } else {
                            playSound(Sound.UNDO);
                            fileManager.undo(currentPath);
                        }
                    }, false, false, false, "");
                    ContextMenu.show((int) mouseX, (int) mouseY, 80, this.width, this.height);
                    playSound(Sound.RIGHTCLICK);
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateTab() {
        Tab selectedTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
        currentPath = selectedTab.tabData.path;
        serverInfo.isRemote = selectedTab.tabData.isRemote;
        serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
        loadDirectory(selectedTab.tabData.path, false, false, true);
    }

    private boolean createFile() {
        String defaultName = "Name Me!";
        Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
        if (!currentTab.tabData.selectedPaths.isEmpty()) {
            Path firstSelected = currentTab.tabData.selectedPaths.get(0);
            if (Files.isDirectory(firstSelected)) {
                defaultName = "Name Me!";
            }
        }
        newCreationPath = currentPath.resolve(defaultName);
        creatingNew = true;
        renamePath = newCreationPath;
        renameBuffer.setLength(0);
        renameBuffer.append(defaultName);
        renameCursorPos = renameBuffer.length();
        try {
            if (!serverInfo.isRemote) {
                Files.createDirectory(newCreationPath);
            } else {
                ensureRemoteConnected();
                serverInfo.remoteHost.getSSHManager().connectSFTPSync();
                serverInfo.remoteHost.getSSHManager().sftpExecutor.submit(() -> {
                    try {
                        serverInfo.remoteHost.getSSHManager().sftpChannel.mkdir(newCreationPath.toString().replace("\\", "/"));
                    } catch (Exception e) {
                        devPrint("Failed to create remote directory: " + e.getMessage());
                    }
                }).get();
            }
        } catch (Exception ignored) {}
        loadDirectory(currentPath, false, true, true);
        return true;
    }

    private void closeTab(int index) {
        if (tabs.size() > 1) {
            Tab tabToRemove = tabs.get(index);
            tabToRemove.textAnimator.setOnAnimationEnd(() -> {
                tabs.remove(tabToRemove);
                if (currentTabIndex >= tabs.size()) {
                    currentTabIndex = tabs.size() - 1;
                }
                if (tabs.isEmpty()) {
                    minecraftClient.setScreen(parent);
                } else {
                    Tab selectedTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
                    currentPath = selectedTab.tabData.path;
                    serverInfo.isRemote = selectedTab.tabData.isRemote;
                    serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
                    loadDirectory(currentPath, false, false, true);
                }
                saveFileExplorerTabs(tabs.stream().map(t1 -> new TabData(t1.tabData.path, t1.tabData.isRemote, t1.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
            });
            tabToRemove.textAnimator.reverse();
        } else {
            tabs.remove(0);
            minecraftClient.setScreen(parent);
        }
    }

    public static void openExternally(Path selectedPath) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("explorer.exe", selectedPath.toString());
        try {
            pb.start();
        } catch (IOException e) {
            devPrint("Failed to open file explorer: " + e.getMessage());
        }
    }

    private void renameSelectedFile() {
        if (renamePath == null || renameBuffer.isEmpty()) {
            renamePath = null;
            renameBuffer.setLength(0);
            creatingNew = false;
            newCreationPath = null;
            return;
        }
        Path oldPath = renamePath;
        String newName = renameBuffer.toString();
        Path newPath = oldPath.getParent() != null ? oldPath.getParent().resolve(newName) : Paths.get(newName);
        if (creatingNew && !serverInfo.isRemote) {
            boolean newShouldBeFile = newName.contains(".");
            boolean wasFile = Files.isRegularFile(oldPath);
            if (newShouldBeFile != wasFile) {
                try {
                    Files.deleteIfExists(oldPath);
                    if (newShouldBeFile) {
                        Files.createFile(newPath);
                    } else {
                        Files.createDirectory(newPath);
                    }
                } catch (Exception ignored) {}
            }
        }
        if (serverInfo.isRemote) {
            ensureRemoteConnected();
            try {
                serverInfo.remoteHost.getSSHManager().renameRemoteFile(oldPath.toString().replace("\\", "/"), newPath.toString().replace("\\", "/"));
            } catch (Exception ex) {
                devPrint(ex.getMessage());
                showNotification("Rename failed: " + ex.getMessage(), Notification.Type.ERROR);
            }
        } else {
            try {
                Files.move(oldPath, newPath);
            } catch (Exception ignored) {}
        }
        renamePath = null;
        renameBuffer.setLength(0);
        creatingNew = false;
        newCreationPath = null;
        loadDirectory(currentPath, false, true, true);
    }

    private void navigateBack() {
        if (!history.isEmpty()) {
            Path previousPath = history.pop();
            int foundIndex = -1;
            for (int i = 0; i < tabs.size(); i++) {
                TabData td = tabs.get(i).tabData;
                if (td.isRemote == serverInfo.isRemote && Objects.equals(td.remoteHostInfo, serverInfo.remoteHost)) {
                    foundIndex = i;
                    break;
                }
            }
            if (foundIndex == -1) {
                return;
            }
            currentTabIndex = foundIndex;
            Tab selectedTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
            selectedTab.tabData.path = previousPath;
            currentPath = previousPath;
            loadDirectory(previousPath, false, false, false);
            serverInfo.isRemote = selectedTab.tabData.isRemote;
            serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
            saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
        }
    }

    private void navigateUp() {
        if (tabs.isEmpty()) {
            minecraftClient.setScreen(parent);
            return;
        }
        Path parentPath = currentPath.getParent();
        if (parentPath != null) {
            Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
            loadDirectory(parentPath, true, false, false);
            currentTab.tabData.path = parentPath;
            currentTab.setName(parentPath.getFileName() != null ? parentPath.getFileName().toString() : parentPath.toString());
        } else {
            minecraftClient.setScreen(parent);
        }
        saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
    }

    private void handleFileDrop(String[] files) {
        for (String filePath : files) {
            Path src = Paths.get(filePath);
            if (Files.exists(src)) {
                if (serverInfo.isRemote) {
                    String currentRemote = currentPath.toString().replace("\\", "/");
                    if (!currentRemote.endsWith("/")) {
                        currentRemote += "/";
                    }
                    String remoteDest = currentRemote + src.getFileName().toString();
                    ensureRemoteConnected();
                    try {
                        serverInfo.remoteHost.getSSHManager().upload(src, remoteDest);
                    } catch (Exception e) {
                        showNotification("Failed uploading " + src.getFileName() + ": " + e.getMessage(), Notification.Type.ERROR);
                    }
                } else {
                    try {
                        Path dest = currentPath.resolve(src.getFileName());
                        if (Files.isDirectory(src)) {
                            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                    Path targetDir = dest.resolve(src.relativize(dir));
                                    Files.createDirectories(targetDir);
                                    return FileVisitResult.CONTINUE;
                                }
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } else {
                            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e) {
                        showNotification("Failed copying " + src.getFileName() + ": " + e.getMessage(), Notification.Type.ERROR);
                    }
                }
            }
        }
        loadDirectory(currentPath, false, true, true);
        showNotification(files.length + " item(s) dropped.", Notification.Type.INFO);
    }


    public void saveFileExplorerTabs(List<TabData> tabsData, int currentTabIndex) {
        try {
            if (!Files.exists(FILE_EXPLORER_TABS_FILE.getParent())) {
                Files.createDirectories(FILE_EXPLORER_TABS_FILE.getParent());
            }
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> tabList = new ArrayList<>();
            for (TabData td : tabsData) {
                Map<String, Object> map = new HashMap<>();
                map.put("path", td.path.toString());
                map.put("isRemote", td.isRemote);
                map.put("scrollOffset", td.targetOffset);
                if (td.isRemote && td.remoteHostInfo != null) {
                    Map<String, Object> hostMap = new HashMap<>();
                    hostMap.put("user", td.remoteHostInfo.getUser());
                    hostMap.put("ip", td.remoteHostInfo.getIp());
                    hostMap.put("port", td.remoteHostInfo.getPort());
                    hostMap.put("password", td.remoteHostInfo.getPassword());
                    map.put("remoteHostInfo", hostMap);
                }
                tabList.add(map);
            }
            data.put("tabs", tabList);
            data.put(CURRENT_TAB_INDEX_KEY, currentTabIndex);
            String json = GSON.toJson(data);
            Files.write(FILE_EXPLORER_TABS_FILE, json.getBytes());
        } catch (IOException e) {
            devPrint("Failed to save file explorer tabs: " + e);
        }
    }

    public List<TabData> loadFileExplorerTabs() {
        List<TabData> tabsData = new ArrayList<>();
        if (Files.exists(FILE_EXPLORER_TABS_FILE)) {
            try {
                String json = new String(Files.readAllBytes(FILE_EXPLORER_TABS_FILE));
                Map<String, Object> data = GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                List<Map<String, Object>> tabList = (List<Map<String, Object>>) data.get("tabs");
                for (Map<String, Object> map : tabList) {
                    String pathStr = (String) map.get("path");
                    boolean isRemote = map.get("isRemote") != null && (boolean) map.get("isRemote");
                    float scrollOffset = map.get("scrollOffset") != null ? ((Number) map.get("scrollOffset")).floatValue() : 0;
                    RemoteHostInfo remoteHostInfo = null;
                    if (isRemote && map.get("remoteHostInfo") != null) {
                        Map<String, Object> hostMap = (Map<String, Object>) map.get("remoteHostInfo");
                        String user = (String) hostMap.get("user");
                        String ip = (String) hostMap.get("ip");
                        int port = ((Number) hostMap.get("port")).intValue();
                        String password = (String) hostMap.get("password");
                        remoteHostInfo = new RemoteHostInfo();
                        remoteHostInfo.setUser(user);
                        remoteHostInfo.setIp(ip);
                        remoteHostInfo.setPort(port);
                        remoteHostInfo.setPassword(password);
                    }
                    Path p = Paths.get(pathStr);
                    TabData tabData = new TabData(p, isRemote, remoteHostInfo);
                    tabData.targetOffset = scrollOffset;
                    tabsData.add(tabData);
                }
            } catch (IOException e) {
                devPrint("Failed to load file explorer tabs: " + e);
            }
        }
        return tabsData;
    }

    private int loadCurrentTabIndex() {
        if (Files.exists(FILE_EXPLORER_TABS_FILE)) {
            try {
                String json = new String(Files.readAllBytes(FILE_EXPLORER_TABS_FILE));
                Map<String, Object> data = GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                if (data.containsKey(CURRENT_TAB_INDEX_KEY)) {
                    return ((Number) data.get(CURRENT_TAB_INDEX_KEY)).intValue();
                }
            } catch (IOException ignored) {}
        }
        return 0;
    }

    private void deleteWord() {
        if (cursorPosition == 0) return;
        int deleteStart = cursorPosition;
        while (deleteStart > 0) {
            char c = fieldText.charAt(deleteStart - 1);
            if (c == '/' || c == '\\') break;
            deleteStart--;
        }
        if (deleteStart < 0 || deleteStart > fieldText.length()) {
            deleteStart = 0;
        }
        fieldText.delete(deleteStart, cursorPosition);
        cursorPosition = deleteStart;
    }

    private void pasteClipboard() {
        try {
            if (fieldFocused) {
                clearFieldBar();
                String clipboard = minecraftClient.keyboard.getClipboard();
                fieldText.insert(cursorPosition, clipboard);
                cursorPosition += clipboard.length();
                if (currentMode == Mode.SEARCH) {
                    filterFileEntries();
                }
            } else {
                fileManager.paste(currentPath);
                showNotification("Pasted " + currentPath, Notification.Type.INFO);
            }
        } catch (Exception e) {
            showNotification("Failed to paste.", Notification.Type.ERROR);
        }
    }

    private void executePath() {
        Path newPath = Paths.get(fieldText.toString()).toAbsolutePath().normalize();
        boolean isRemote = serverInfo.isRemote;
        if (Files.exists(newPath) && Files.isDirectory(newPath)) {
            loadDirectory(newPath, false, false, false);
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).tabData.path.equals(newPath) && tabs.get(i).tabData.isRemote == isRemote && Objects.equals(tabs.get(i).tabData.remoteHostInfo, serverInfo.remoteHost)) {
                    currentTabIndex = i;
                    return;
                }
            }
            Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
            currentTab.tabData.path = newPath;
            currentTab.setName(newPath.getFileName() != null ? newPath.getFileName().toString() : newPath.toString());
            currentTab.tabData.targetOffset = 0;
            currentTab.tabData.smoothOffset = 0;
        } else {
            showNotification("Invalid path.", Notification.Type.ERROR);
        }
    }

    public void showNotification(String message, Notification.Type type) {
        new Notification(message, type);
    }

    void loadDirectory(Path dir, boolean addToHistory, boolean forceReload, boolean preserveState) {
        Tab currentTab = tabs.get(Math.min(currentTabIndex, tabs.size() - 1));
        if (addToHistory && currentPath != null && !currentPath.equals(dir)) {
            history.push(currentPath);
            currentTab.tabData.targetOffset = 0;
        }
        if (currentMode == Mode.SEARCH) {
            currentMode = Mode.PATH;
            fieldText.setLength(0);
            cursorPosition = 0;
        }
        currentPath = dir;
        fieldText.setLength(0);
        fieldText.append(currentPath.toString());
        cursorPosition = fieldText.length();
        if (!preserveState) {
            currentTab.tabData.selectedPaths.clear();
            currentTab.tabData.targetOffset = 0;
            currentTab.tabData.smoothOffset = 0;
        }
        currentTab.tabData.path = dir;
        pathTextAnimator.setOnAnimationEnd(() -> {
            pathTextAnimator.updateText(currentPath.toString());
            pathTextAnimator.setOnAnimationEnd(null);
        });
        pathTextAnimator.reverse();
        int thisRequestId = ++loadRequestId;
        currentTab.tabData.requestId = thisRequestId;
        String key = dir.toString() + "_" + serverInfo.isRemote + (serverInfo.isRemote && serverInfo.remoteHost != null ? "_" + serverInfo.remoteHost.getIp() + "_" + serverInfo.remoteHost.getPort() : "");
        boolean shouldCache = serverInfo.isRemote && serverInfo.remoteHost != null && !serverInfo.remoteHost.getIp().equals("127.0.0.1");
        if (!shouldCache) {
            remoteCache.remove(key);
        }
        loading = true;
        directoryLoader.submit(() -> {
            try {
                List<EntryData> temp;
                if (serverInfo.isRemote) {
                    ensureRemoteConnected();
                    if (serverInfo.remoteHost == null) {
                        temp = new ArrayList<>();
                    } else {
                        temp = loadRemoteDirectory(dir, forceReload);
                    }
                } else {
                    temp = loadLocalDirectory(dir);
                }
                if (thisRequestId == currentTab.tabData.requestId) {
                    synchronized (fileEntriesLock) {
                        fullEntries.clear();
                        fileEntries.clear();
                        fullEntries.addAll(temp);
                        loadedCount = 0;
                        hasMore = true;
                        isLoadingMore = false;
                    }
                    loadMoreEntries();
                    if (serverInfo.isRemote && shouldCache) {
                        remoteCache.put(key, temp);
                    }
                }
            } catch (Exception ignored) {}
            loading = false;
            saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
        });
    }

    private List<EntryData> loadRemoteDirectory(Path dir, boolean forceReload) {
        String remotePath = dir.toString().replace("\\", "/");
        String key = remotePath + "_true_" + serverInfo.remoteHost.getIp() + "_" + serverInfo.remoteHost.getPort();
        if (!forceReload && remoteCache.containsKey(key)) {
            return remoteCache.get(key);
        }

        List<EntryData> temp = new ArrayList<>();
        try {
            // Use the new method that returns both filenames and directory status in a single call
            Map<String, Boolean> entriesWithTypes = serverInfo.remoteHost.getSSHManager().listRemoteDirectoryWithTypes(remotePath);

            for (Map.Entry<String, Boolean> entry : entriesWithTypes.entrySet()) {
                String filename = entry.getKey();
                boolean isDirectory = entry.getValue();

                Path p = dir.resolve(filename);
                String displayName = filename;
                if (textRenderer.getWidth(displayName) > MAX_NAME_WIDTH) {
                    displayName = doEllipsize(displayName);
                }
                temp.add(new EntryData(p, isDirectory, "", "", displayName));
            }
        } catch (Exception e) {
            devPrint("Error loading remote directory: " + e.getMessage());
            return new ArrayList<>();
        }

        synchronized (favoritePathsLock) {
            temp.sort(Comparator.comparing((EntryData x) -> !favoritePaths.contains(x.path))
                    .thenComparing(x -> !x.isDirectory)
                    .thenComparing(x -> x.path.getFileName().toString().toLowerCase()));
        }
        remoteCache.put(key, temp);
        return temp;
    }

    private List<EntryData> loadLocalDirectory(Path dir) throws IOException {
        List<EntryData> temp = new ArrayList<>();
        if (!Files.exists(dir)) {
            return temp;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                boolean d = Files.isDirectory(entry);
                String sz = d ? "-" : getFileSize(entry);
                String cr = getCreationDate(entry);
                String n = entry.getFileName().toString();
                if (textRenderer.getWidth(n) > MAX_NAME_WIDTH) {
                    n = doEllipsize(n);
                }
                temp.add(new EntryData(entry, d, sz, cr, n));
            }
        }
        synchronized (favoritePathsLock) {
            temp.sort(Comparator.comparing((EntryData x) -> !favoritePaths.contains(x.path))
                    .thenComparing(x -> !x.isDirectory)
                    .thenComparing(x -> x.path.getFileName().toString().toLowerCase()));
        }
        return temp;
    }

    private String doEllipsize(String text) {
        int ellipsisWidth = textRenderer.getWidth("...");
        int maxWidth = MAX_NAME_WIDTH - ellipsisWidth;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (textRenderer.getWidth(sb.toString() + text.charAt(i)) > maxWidth) {
                sb.append("...");
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    public void ensureRemoteConnected() {
        if (serverInfo.remoteHost == null) {
            devPrint("Remote host is null, cannot connect");
            return;
        }

        try {
            // Get or create SSH manager
            serverInfo.remoteSSHManager = serverInfo.remoteHost.getSSHManager();

            // Check if SSH is connected
            if (!serverInfo.remoteSSHManager.isSSH()) {
                devPrint("SSH not connected, attempting to connect to " + serverInfo.remoteHost.getIp());
                serverInfo.remoteSSHManager.connectToRemoteHost(
                    serverInfo.remoteHost.getUser(),
                    serverInfo.remoteHost.getIp(),
                    serverInfo.remoteHost.getPort(),
                    serverInfo.remoteHost.getPassword()
                );
            }

            // Check if SFTP is connected
            if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
                devPrint("SFTP not connected, attempting to connect");
                serverInfo.remoteSSHManager.connectSFTPSync();
            }
        } catch (Exception e) {
            devPrint("Error ensuring remote connection: " + e.getMessage());
        }
    }

    private void filterFileEntries() {
        String query = fieldText.toString().toLowerCase().trim();
        List<EntryData> matched = new ArrayList<>();
        List<EntryData> fuzzyMatched = new ArrayList<>();
        List<EntryData> unmatched = new ArrayList<>();

        synchronized (fileEntriesLock) {
            if (query.isEmpty()) {
                fileEntries.clear();
                loadedCount = 0;
                hasMore = true;
                isLoadingMore = false;
                tabs.get(currentTabIndex).tabData.selectedPaths.clear();
                tabs.get(currentTabIndex).tabData.targetOffset = 0;
                ScrollBar.setPendingOffset(tabs.get(currentTabIndex).tabData.targetOffset);
                loadMoreEntries();
                return;
            }

            for (EntryData data : fullEntries) {
                String filename = data.path.getFileName().toString().toLowerCase();
                if (filename.contains(query)) {
                    matched.add(data);
                    data.isMatched = true;
                }
                else if (isFuzzyMatch(filename, query)) {
                    fuzzyMatched.add(data);
                    data.isMatched = true;
                }
                else {
                    unmatched.add(data);
                    data.isMatched = false;
                }
            }

            Comparator<EntryData> comp = Comparator.comparing((EntryData x) -> !favoritePaths.contains(x.path)).thenComparing(x -> !x.isDirectory).thenComparing(x -> x.path.getFileName().toString().toLowerCase());
            matched.sort(comp);
            fuzzyMatched.sort(comp);
            unmatched.sort(comp);
            List<EntryData> sorted = new ArrayList<>();
            sorted.addAll(matched);
            sorted.addAll(fuzzyMatched);
            sorted.addAll(unmatched);
            fileEntries.clear();
            loadedCount = 0;
            fullEntries.clear();
            fullEntries.addAll(sorted);
            hasMore = true;
            isLoadingMore = false;
            tabs.get(currentTabIndex).tabData.selectedPaths.clear();
            for (EntryData entry : matched) {
                tabs.get(currentTabIndex).tabData.selectedPaths.add(entry.path);
            }
            for (EntryData entry : fuzzyMatched) {
                tabs.get(currentTabIndex).tabData.selectedPaths.add(entry.path);
            }
        }
        loadMoreEntries();
        tabs.get(currentTabIndex).tabData.targetOffset = 0;
        ScrollBar.setPendingOffset(tabs.get(currentTabIndex).tabData.targetOffset);
    }

    public static boolean isSupportedFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private String getFileSize(Path file) {
        try {
            long size = Files.size(file);
            return humanReadableByteCountBin(size);
        } catch (IOException e) {
            return "N/A";
        }
    }

    private String humanReadableByteCountBin(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private String getCreationDate(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return dateFormat.format(new Date(attrs.creationTime().toMillis()));
        } catch (IOException e) {
            return "N/A";
        }
    }

    @Override
    public void refreshDirectory(Path path) {
        loadDirectory(path, false, true, true);
    }

    private void saveFavorites() {
        try (BufferedWriter writer = Files.newBufferedWriter(favoritesFilePath)) {
            List<String> list;
            synchronized (favoritePathsLock) {
                list = favoritePaths.stream().map(Path::toString).collect(Collectors.toList());
            }
            writer.write(GSON.toJson(list));
        } catch (IOException e) {
            showNotification("Error saving favorites: " + e.getMessage(), Notification.Type.ERROR);
        }
    }

    public boolean isCanScroll() {
        return canScroll;
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.FILEEXPLORER);
    }

    @Override
    public void removed() {
        minecraftClient.getWindow().setScaleFactor(originalMCScale);
        parent.width = minecraftClient.getWindow().getScaledWidth();
        parent.height = minecraftClient.getWindow().getScaledHeight();
        targetScaleFactor = globalScaleFactor = animScaleFactor;
    }
}
