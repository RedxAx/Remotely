package redxax.oxy.explorer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.*;
import redxax.oxy.servers.RemoteHostInfo;
import redxax.oxy.servers.ServerInfo;
import redxax.oxy.config.Config;
import redxax.oxy.util.TabTextAnimator;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static redxax.oxy.Render.*;
import static redxax.oxy.config.Config.*;
import static redxax.oxy.util.ImageUtil.*;
public class FileExplorerScreen extends Screen implements FileManager.FileManagerCallback {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private List<EntryData> fileEntries;
    private final Object fileEntriesLock = new Object();
    private float smoothOffset = 0;
    private final int entryHeight = 25;
    private Path currentPath;
    private float targetOffset = 0;
    private float scrollSpeed = 0.2f;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private List<Path> selectedPaths = new ArrayList<>();
    private int lastSelectedIndex = -1;
    private long lastClickTime = 0;
    private static final int DOUBLE_CLICK_INTERVAL = 500;
    private int lastClickedIndex = -1;
    private Deque<Path> history = new ArrayDeque<>();
    private Deque<Path> forwardHistory = new ArrayDeque<>();
    private int searchBarWidth = 200;
    private List<Notification> notifications = new ArrayList<>();
    private final TextRenderer textRenderer;
    private final FileManager fileManager;
    private boolean importMode;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".txt", ".md", ".json", ".yml", ".yaml", ".conf", ".properties",
            ".xml", ".cfg", ".sk", ".log", ".mcmeta", ".bat", ".sh", ".json5", ".jsonc",
            ".html", ".js", ".java", ".py", ".css", ".vsh", ".fsh", ".glsl", ".nu",
            ".bash", ".fish", ".toml", ".mcfunction", ".nbt"
    );
    private final ExecutorService directoryLoader = Executors.newSingleThreadExecutor();
    private static final Map<String, List<EntryData>> remoteCache = new ConcurrentHashMap<>();
    private boolean loading = false;
    private BufferedImage fileIcon;
    private BufferedImage folderIcon;
    private BufferedImage pinIcon;
    private BufferedImage loadingAnim;
    private List<BufferedImage> loadingFrames = new ArrayList<>();
    private int currentLoadingFrame = 0;
    private long lastFrameTime = 0;
    private final List<Path> favoritePaths = new ArrayList<>();
    private final Object favoritePathsLock = new Object();
    private final Path favoritesFilePath = Paths.get("C:/remotely/data/favorites.json");
    private boolean fieldFocused = false;
    private StringBuilder fieldText = new StringBuilder();
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private long lastBlinkTime = 0;
    private boolean showCursor = true;
    private final int basePathFieldWidth = 200;
    private final int maxPathFieldWidth = 600;
    private float pathScrollOffset = 0;
    private float pathTargetScrollOffset = 0;
    private int buttonX;
    private int buttonY = 5;
    private Gson GSON = new Gson();
    private enum Mode { PATH, SEARCH }
    private Mode currentMode = Mode.PATH;
    private TabTextAnimator pathTextAnimator;
    public static final Path FILE_EXPLORER_TABS_FILE = Paths.get("C:/remotely/data/fileExplorerTabs.json");
    private static final String CURRENT_TAB_INDEX_KEY = "currentTabIndex";
    private Path renamePath = null;
    private StringBuilder renameBuffer = new StringBuilder();
    private int renameCursorPos = 0;
    private boolean creatingNew = false;
    private Path newCreationPath = null;
    private int loadRequestId = 0;
    private static final int CHUNK_SIZE = 500;
    private int loadedCount = 0;
    private boolean hasMore = false;
    private boolean isLoadingMore = false;
    private List<EntryData> fullEntries = new ArrayList<>();
    private static final int MAX_NAME_WIDTH = 500;
    private BufferedImage appsIcon, cssIcon, jsIcon, jsonIcon, minecraftIcon, pyIcon, javaIcon, scriptIcon, shadersIcon, textIcon;
    private static class EntryData {
        Path path;
        boolean isDirectory;
        String size;
        String created;
        String displayName;
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
        float scrollOffset;
        int requestId;
        TabData(Path path, boolean isRemote, RemoteHostInfo remoteHostInfo) {
            this.path = path;
            this.isRemote = isRemote;
            this.remoteHostInfo = remoteHostInfo;
            this.scrollOffset = 0;
            this.requestId = -1;
        }
    }
    public class Tab {
        TabData tabData;
        String name;
        TabTextAnimator textAnimator;
        private Path path;
        Tab(TabData tabData) {
            this.tabData = tabData;
            this.name = tabData.path.getFileName() != null ? tabData.path.getFileName().toString() : tabData.path.toString();
            this.textAnimator = new TabTextAnimator(this.name, 0, 30);
            this.textAnimator.start();
            this.path = tabData.path;
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
            return textRenderer.getWidth(getAnimatedText()) + 2 * TAB_PADDING;
        }
    }
    List<Tab> tabs = new ArrayList<>();
    int currentTabIndex = 0;
    private final int TAB_HEIGHT = 18;
    private final int TAB_PADDING = 5;
    private final int TAB_GAP = 5;
    private final int PLUS_TAB_WIDTH = 18;
    public FileExplorerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        this(mc, parent, info, false);
    }
    public FileExplorerScreen(MinecraftClient mc, Screen parent, ServerInfo info, boolean importMode) {
        super(Text.literal("File Explorer"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
        this.fileEntries = new ArrayList<>();
        this.textRenderer = mc.textRenderer;
        this.fileManager = new FileManager(this, serverInfo, serverInfo.remoteSSHManager);
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
        this.pathTextAnimator = new TabTextAnimator(currentPath.toString(), 0, 30);
        this.pathTextAnimator.start();
        tabs.add(new Tab(new TabData(currentPath, serverInfo.isRemote, serverInfo.remoteHost)));
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
                    List<String> lines = GSON.fromJson(reader, new TypeToken<List<String>>(){}.getType());
                    synchronized (favoritePathsLock) {
                        for (String line : lines) {
                            Path p = serverInfo.isRemote ? Paths.get(line.replace("\\", "/")) : Paths.get(line);
                            favoritePaths.add(p);
                        }
                    }
                } catch (Exception e) {}
            }
            fileIcon = loadResourceIcon("/assets/remotely/icons/file.png");
            folderIcon = loadResourceIcon("/assets/remotely/icons/folder.png");
            pinIcon = loadResourceIcon("/assets/remotely/icons/pin.png");
            loadingAnim = loadSpriteSheet("/assets/remotely/icons/loadinganim.png");
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
            List<TabData> loadedTabs = loadFileExplorerTabs().stream().distinct().toList();
            if (loadedTabs.isEmpty()) {
                tabs.add(new Tab(new TabData(currentPath, serverInfo.isRemote, serverInfo.remoteHost)));
            } else {
                for (TabData td : loadedTabs) {
                    if (tabs.stream().noneMatch(t -> t.tabData.path.equals(td.path) && t.tabData.isRemote == td.isRemote && Objects.equals(t.tabData.remoteHostInfo, td.remoteHostInfo))) {
                        tabs.add(new Tab(td));
                    }
                }
                if (!tabs.isEmpty()) {
                    currentTabIndex = loadCurrentTabIndex();
                    if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
                        currentTabIndex = 0;
                    }
                    Tab selectedTab = tabs.get(currentTabIndex);
                    currentPath = selectedTab.tabData.path;
                    serverInfo.isRemote = selectedTab.tabData.isRemote;
                    serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
                    loadDirectory(currentPath, false, false);
                } else {
                    tabs.add(new Tab(new TabData(currentPath, serverInfo.isRemote, serverInfo.remoteHost)));
                }
            }
        } catch (Exception e) {}
        loadDirectory(currentPath, false, false);
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        int titleBarHeight = 30;
        int tabBarY = titleBarHeight + 5;
        drawTabs(context, this.textRenderer, tabs, currentTabIndex, mouseX, mouseY, true, false);
        int explorerY = tabBarY + TAB_HEIGHT + 30;
        int explorerHeight = this.height - explorerY - 10;
        int explorerX = 5;
        int explorerWidth = this.width - 10;
        int headerY = explorerY - 25;
        context.fill(explorerX, headerY, explorerX + explorerWidth, headerY + 25, headerBackgroundColor);
        drawInnerBorder(context, explorerX, headerY, explorerWidth, 25, headerBorderColor);
        drawOuterBorder(context, explorerX, headerY, explorerWidth, 25, globalBottomBorder);
        context.drawText(this.textRenderer, Text.literal("Name"), explorerX + 10, headerY + 5, screensTitleTextColor, Config.shadow);
        if (!serverInfo.isRemote) {
            int createdX = explorerX + explorerWidth - 100;
            int sizeX = createdX - 100;
            context.drawText(this.textRenderer, Text.literal("Created"), createdX, headerY + 5, screensTitleTextColor, Config.shadow);
            context.drawText(this.textRenderer, Text.literal("Size"), sizeX, headerY + 5, screensTitleTextColor, Config.shadow);
        }
        context.fill(0, 0, this.width, titleBarHeight, headerBackgroundColor);
        drawInnerBorder(context, 0, 0, this.width, titleBarHeight, headerBorderColor);
        drawOuterBorder(context, 0, 0, this.width, titleBarHeight, globalBottomBorder);
        String prefixText = "Remotely - File Explorer";
        context.drawText(this.textRenderer, Text.literal(prefixText), 10, 10, screensTitleTextColor, Config.shadow);
        drawSearchBar(
                context,
                textRenderer,
                fieldText,
                fieldFocused,
                cursorPosition,
                selectionStart,
                selectionEnd,
                pathScrollOffset,
                pathTargetScrollOffset,
                showCursor,
                currentMode == Mode.SEARCH,
                "FileExplorerScreen"
        );
        int closeButtonX = this.width - buttonW - 10;
        boolean hoveredBack = mouseX >= closeButtonX && mouseX <= closeButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH;
        drawCustomButton(context, closeButtonX, buttonY, "Close", minecraftClient, hoveredBack, false, true, buttonTextColor, buttonTextDeleteColor);
        int backButtonX = closeButtonX - (buttonW + 10);
        int backYLocal = 5;
        boolean hoveredClose = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backYLocal && mouseY <= backYLocal + buttonH;
        drawCustomButton(context, backButtonX, backYLocal, "Back", minecraftClient, hoveredClose, false, true, buttonTextColor, buttonTextHoverColor);
        if (loading && tabs.get(currentTabIndex).tabData.isRemote) {
            long currentTimeLoading = System.currentTimeMillis();
            if (currentTimeLoading - lastFrameTime >= 40) {
                currentLoadingFrame = (currentLoadingFrame + 1) % loadingFrames.size();
                lastFrameTime = currentTimeLoading;
            }
            BufferedImage currentFrame = loadingFrames.get(currentLoadingFrame);
            int scale = 8;
            int imgWidth = currentFrame.getWidth() * scale;
            int imgHeight = currentFrame.getHeight() * scale;
            int centerX = (this.width - imgWidth) / 2;
            int centerY = (this.height - imgHeight) / 2;
            drawBufferedImage(context, currentFrame, centerX, centerY, imgWidth, imgHeight);
            return;
        }
        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;
        List<EntryData> entriesToRender;
        synchronized (fileEntriesLock) {
            entriesToRender = new ArrayList<>(fileEntries);
        }
        int gap = 1;
        int itemHeight = entryHeight + gap;
        int visibleEntries = explorerHeight / itemHeight;
        int totalHeight = entriesToRender.size() * itemHeight;
        int startIndex = (int) Math.floor(smoothOffset / itemHeight);
        int endIndex = startIndex + visibleEntries + 3;
        if (endIndex > entriesToRender.size()) endIndex = entriesToRender.size();
        context.enableScissor(explorerX -2, explorerY, explorerX + explorerWidth +4, explorerY + explorerHeight);
        if (entriesToRender.isEmpty() && !loading) {
            if (!serverInfo.isRemote) {
                context.drawText(this.textRenderer, Text.literal("No files/folders in this directory."), explorerX + explorerWidth / 2 - textRenderer.getWidth("No files/folders in this directory.") / 2, explorerY + explorerHeight / 2, 0xFFFFFFFF, false);
            } else {
                if (serverInfo.remoteSSHManager != null && serverInfo.remoteSSHManager.isSFTPConnected()) {
                    context.drawText(this.textRenderer, Text.literal("No files/folders in this directory."), explorerX + explorerWidth / 2 - textRenderer.getWidth("No files/folders in this directory.") / 2, explorerY + explorerHeight / 2, 0xFFFFFFFF, false);
                } else {
                    context.drawText(this.textRenderer, Text.literal("Connection lost or SFTP error."), explorerX + explorerWidth / 2 - textRenderer.getWidth("Connection lost or SFTP error.") / 2, explorerY + explorerHeight / 2, 0xFFFFFFFF, false);
                }
            }
        } else {
            for (int entryIndex = startIndex; entryIndex < endIndex; entryIndex++) {
                EntryData entry = entriesToRender.get(entryIndex);
                int entryY = explorerY + (entryIndex * itemHeight) - (int) smoothOffset;
                boolean hovered = mouseX >= explorerX && mouseX <= explorerX + explorerWidth && mouseY >= entryY && mouseY < entryY + entryHeight;
                boolean isSelected = selectedPaths.contains(entry.path);
                boolean isFavorite;
                synchronized (favoritePathsLock) {
                    isFavorite = favoritePaths.contains(entry.path);
                }
                int bg = isSelected ? (isFavorite ? explorerElementFavoriteBackgroundColor : explorerElementSelectedBackgroundColor) : (hovered ? explorerElementBackgroundHoverColor : explorerElementBackgroundColor);
                int borderWithOpacity = isFavorite ? isSelected ? explorerElementFavoriteSelectedBorderColor : explorerElementFavoriteBorderColor : (isSelected ? explorerElementSelectedBorderColor : (hovered ? explorerElementBorderHoverColor : explorerElementBorderColor));
                int textWithOpacity = explorerElementTextColor;
                drawOuterBorder(context, explorerX, entryY, explorerWidth, entryHeight, globalBottomBorder);
                context.fill(explorerX, entryY, explorerX + explorerWidth, entryY + entryHeight, bg);
                drawInnerBorder(context, explorerX, entryY, explorerWidth, entryHeight, borderWithOpacity);
                context.fill(explorerX, entryY + entryHeight - 1, explorerX + explorerWidth, entryY + entryHeight, borderWithOpacity);
                BufferedImage icon = entry.isDirectory ? folderIcon : getIconForFile(entry.path);
                drawBufferedImage(context, icon, explorerX + 10, entryY + 5, 16, 16);
                if (isFavorite) {
                    drawBufferedImage(context, pinIcon, entry.isDirectory ? explorerX + 5 : explorerX + 7, entryY + 5, 16, 16);
                }
                if (renamePath != null && renamePath.equals(entry.path)) {
                    int renameBoxX = explorerX + 30;
                    int renameBoxY = entryY + 5;
                    int renameBoxWidth = Math.max(100, textRenderer.getWidth(renameBuffer.toString()) + 20);
                    context.fill(renameBoxX, renameBoxY, renameBoxX + renameBoxWidth, renameBoxY + textRenderer.fontHeight + 4, explorerElementBackgroundColor);
                    drawInnerBorder(context, renameBoxX, renameBoxY, renameBoxWidth, textRenderer.fontHeight + 4, explorerElementBorderColor);
                    drawOuterBorder(context, renameBoxX, renameBoxY, renameBoxWidth, textRenderer.fontHeight + 4, globalBottomBorder);
                    String displayed = renameBuffer.toString();
                    int renameCursorX = renameBoxX + 2 + textRenderer.getWidth(displayed.substring(0, Math.min(renameCursorPos, displayed.length())));
                    context.drawText(this.textRenderer, Text.literal(displayed), renameBoxX + 2, renameBoxY + 2, textWithOpacity, false);
                    if (showCursor) {
                        context.fill(renameCursorX, renameBoxY + 2, renameCursorX + 1, renameBoxY + 2 + textRenderer.fontHeight, 0xFFFFFFFF);
                    }
                } else {
                    if (!serverInfo.isRemote) {
                        int createdX = explorerX + explorerWidth - 100;
                        int sizeX = createdX - 100;
                        context.drawText(this.textRenderer, Text.literal(entry.displayName), explorerX + 30, entryY + 5, textWithOpacity, Config.shadow);
                        context.drawText(this.textRenderer, Text.literal(entry.created), createdX, entryY + 5, textWithOpacity, Config.shadow);
                        context.drawText(this.textRenderer, Text.literal(entry.size), sizeX, entryY + 5, textWithOpacity, Config.shadow);
                    } else {
                        context.drawText(this.textRenderer, Text.literal(entry.displayName), explorerX + 30, entryY + 5, textWithOpacity, Config.shadow);
                    }
                }
            }
        }
        context.disableScissor();
        if (smoothOffset > 0) {
            context.fillGradient(explorerX, explorerY, explorerX + explorerWidth, explorerY + 10, 0x80000000, 0x00000000);
        }
        if (smoothOffset < Math.max(0, totalHeight - explorerHeight)) {
            context.fillGradient(explorerX, explorerY + explorerHeight - 10, explorerX + explorerWidth, explorerY + explorerHeight, 0x00000000, 0x80000000);
        }
        updateNotifications(delta);
        renderNotifications(context, mouseX, mouseY, delta);
        if (ContextMenu.isOpen()) {
            ContextMenu.renderMenu(context, minecraftClient, mouseX, mouseY);
        }
        loadMoreIfNeeded(explorerHeight);
    }

    private BufferedImage getIconForFile(Path file) {
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
        if (fileName.endsWith(".txt") || fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".properties") ||
                fileName.endsWith(".toml") || fileName.endsWith(".md") || fileName.endsWith(".log") || fileName.endsWith(".html")) {
            return textIcon;
        }
        return fileIcon;
    }
    private void loadMoreIfNeeded(int explorerHeight) {
        int gap = 1;
        int itemHeight = entryHeight + gap;
        int maxScroll = Math.max(0, fileEntries.size() * itemHeight - explorerHeight);
        if (hasMore && !isLoadingMore && smoothOffset + explorerHeight >= fileEntries.size() * itemHeight - (itemHeight * 2)) {
            isLoadingMore = true;
            loadMoreEntries();
        }
        targetOffset = Math.max(0, Math.min(targetOffset, maxScroll));
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
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
                    fieldFocused = true;
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
                    if (currentMode == Mode.SEARCH) {
                        filterFileEntries();
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
                fileManager.copySelected(selectedPaths);
                showNotification("Copied to clipboard", Notification.Type.INFO);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                fileManager.cutSelected(selectedPaths);
                showNotification("Cut to clipboard", Notification.Type.INFO);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                if (currentMode == Mode.SEARCH) {
                } else {
                    if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                        int selStart = Math.min(selectionStart, selectionEnd);
                        int selEnd = Math.max(selectionStart, selectionEnd);
                        fieldText.delete(selStart, selEnd);
                        cursorPosition = selStart;
                        selectionStart = -1;
                        selectionEnd = -1;
                    }
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
                loadDirectory(currentPath, false, true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_T) {
                synchronized (favoritePathsLock) {
                    for (Path p : selectedPaths) {
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
                String defaultName = "Name Me!";
                if (!selectedPaths.isEmpty()) {
                    Path firstSelected = selectedPaths.get(0);
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
                    if (defaultName.contains(".")) {
                        if (!serverInfo.isRemote) {
                            Files.createFile(newCreationPath);
                        } else {
                            ensureRemoteConnected();
                            serverInfo.remoteSSHManager.prepareRemoteDirectory(newCreationPath.toString().replace("\\", "/"));
                        }
                    } else {
                        if (!serverInfo.isRemote) {
                            Files.createDirectory(newCreationPath);
                        } else {
                            ensureRemoteConnected();
                            serverInfo.remoteSSHManager.prepareRemoteDirectory(newCreationPath.toString().replace("\\", "/"));
                        }
                    }
                } catch (Exception ignored) {}
                loadDirectory(currentPath, false, true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_E) {
                if (!selectedPaths.isEmpty()) {
                    renamePath = selectedPaths.get(0);
                    renameBuffer.setLength(0);
                    renameBuffer.append(renamePath.getFileName().toString());
                    renameCursorPos = renameBuffer.length();
                }
                return true;
            }
        }
        if (currentMode == Mode.PATH && keyCode == GLFW.GLFW_KEY_DELETE) {
            fileManager.deleteSelected(selectedPaths, currentPath);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (renamePath != null) {
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
                if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                    int selStart = Math.min(selectionStart, selectionEnd);
                    int selEnd = Math.max(selectionStart, selectionEnd);
                    fieldText.delete(selStart, selEnd);
                    cursorPosition = selStart;
                    selectionStart = -1;
                    selectionEnd = -1;
                }
                fieldText.insert(cursorPosition, chr);
                cursorPosition++;
                if (currentMode == Mode.SEARCH) {
                    filterFileEntries();
                }
                return true;
            } else if (currentMode == Mode.SEARCH) {
                if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                    int selStart = Math.min(selectionStart, selectionEnd);
                    int selEnd = Math.max(selectionStart, selectionEnd);
                    fieldText.delete(selStart, selEnd);
                    cursorPosition = selStart;
                    selectionStart = -1;
                    selectionEnd = -1;
                }
                fieldText.insert(cursorPosition, chr);
                cursorPosition++;
                filterFileEntries();
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean ctrl = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) ||
                (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
        float scrollMultiplier = ctrl ? 5.0f : 1.0f;
        int gap = 1;
        int itemHeight = entryHeight + gap;
        targetOffset -= (float) (verticalAmount * itemHeight * 0.5f * scrollMultiplier);
        List<EntryData> entriesToRender;
        synchronized (fileEntriesLock) {
            entriesToRender = new ArrayList<>(fileEntries);
        }
        int explorerHeight = this.height - (30 + TAB_HEIGHT + 5 + 30 + 10);
        int totalHeight = entriesToRender.size() * (entryHeight + gap);
        targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, totalHeight - explorerHeight)));
        return true;
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ContextMenu.isOpen()) {
            if (ContextMenu.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        boolean handled = false;
        int titleBarHeight = 30;
        int tabBarY = titleBarHeight + 5;
        int tabBarHeight = TAB_HEIGHT;
        int tabX = 5;
        int tabY = tabBarY;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = tab.getCurrentWidth(textRenderer);
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabBarHeight) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                    currentTabIndex = i;
                    Tab selectedTab = tabs.get(currentTabIndex);
                    currentPath = selectedTab.tabData.path;
                    serverInfo.isRemote = selectedTab.tabData.isRemote;
                    serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
                    targetOffset = selectedTab.tabData.scrollOffset;
                    loadDirectory(currentPath, false, false);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    closeTab(i);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
                    ContextMenu.hide();
                    int finalI = i;
                    ContextMenu.addItem("Close", () -> {
                        closeTab(finalI);
                    }, buttonTextHoverColor);
                    int finalI1 = i;
                    ContextMenu.addItem("Duplicate", () -> {
                        TabData originalTabData = tabs.get(finalI1).tabData;
                        TabData newTabData = new TabData(originalTabData.path, originalTabData.isRemote, originalTabData.remoteHostInfo);
                        Tab newTab = new Tab(newTabData);
                        tabs.add(newTab);
                        currentTabIndex = tabs.size() - 1;
                        loadDirectory(newTabData.path, false, false);
                        saveFileExplorerTabs(tabs.stream().map(t1 -> new TabData(t1.tabData.path, t1.tabData.isRemote, t1.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Externally", () -> {
                        openExternally(tabs.get(finalI1).tabData.path);
                    }, buttonTextHoverColor);
                    ContextMenu.show((int) mouseX, (int) mouseY, 60, this.width, this.height);
                }
                handled = true;
                break;
            }
            tabX += tabWidth + TAB_GAP;
        }
        if (!handled) {
            int plusTabX = tabX;
            if (mouseX >= plusTabX && mouseX <= plusTabX + PLUS_TAB_WIDTH && mouseY >= tabY && mouseY <= tabY + tabBarHeight) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                    minecraftClient.setScreen(new DeskSelectionScreen(minecraftClient, this));
                }
                handled = true;
            }
        }
        if (!handled) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1 || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                long currentTime = System.currentTimeMillis();
                boolean isDoubleClick = false;
                if (lastClickedIndex != -1 && (currentTime - lastClickTime) < DOUBLE_CLICK_INTERVAL) {
                    isDoubleClick = true;
                }
                lastClickTime = currentTime;
                int explorerY = tabBarY + tabBarHeight + 30;
                int explorerHeight = this.height - explorerY - 10;
                int explorerX = 5;
                int explorerWidth = this.width - 10;
                int closeButtonX = this.width - buttonW - 10;
                int backButtonX = closeButtonX - (buttonW + 10);
                int backButtonY = 5;
                int closeButtonY = 5;
                int gap = 1;
                if (mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH) {
                    navigateUp();
                    return true;
                }
                if (mouseX >= closeButtonX && mouseX <= closeButtonX + buttonW && mouseY >= closeButtonY && mouseY <= closeButtonY + buttonH) {
                    minecraftClient.setScreen(parent);
                    return true;
                }
                if (mouseX >= explorerX && mouseX <= explorerX + explorerWidth && mouseY >= explorerY && mouseY <= explorerY + explorerHeight) {
                    int relativeY = (int) mouseY - explorerY + (int) smoothOffset;
                    int clickedIndex = relativeY / (entryHeight + gap);
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
                                Tab selectedTab = tabs.get(currentTabIndex);
                                selectedTab.tabData.path = selectedPath;
                                selectedTab.setName(selectedPath.getFileName() != null ? selectedPath.getFileName().toString() : selectedPath.toString());
                                targetOffset = selectedTab.tabData.scrollOffset;
                                loadDirectory(selectedPath, false, false);
                            } else {
                                if (importMode && selectedPath.getFileName().toString().equalsIgnoreCase("server.jar")) {
                                    if (parent instanceof redxax.oxy.servers.ServerManagerScreen sms) {
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
                            return true;
                        } else {
                            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && entryData.isDirectory) {
                                TabData newTabData = new TabData(selectedPath, serverInfo.isRemote, serverInfo.remoteHost);
                                tabs.add(new Tab(newTabData));
                                currentTabIndex = tabs.size() - 1;
                                loadDirectory(newTabData.path, false, false);
                                return true;
                            }
                            lastClickedIndex = clickedIndex;
                            boolean ctrlPressed = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) || (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
                            boolean shiftPressed = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) || (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
                            if (ctrlPressed) {
                                if (selectedPaths.contains(selectedPath)) {
                                    selectedPaths.remove(selectedPath);
                                } else {
                                    selectedPaths.add(selectedPath);
                                }
                                lastSelectedIndex = clickedIndex;
                            } else if (shiftPressed && lastSelectedIndex != -1) {
                                int start = Math.min(lastSelectedIndex, clickedIndex);
                                int end = Math.max(lastSelectedIndex, clickedIndex);
                                for (int iIdx = start; iIdx <= end; iIdx++) {
                                    if (iIdx >= 0 && iIdx < entriesToRender.size()) {
                                        Path path = entriesToRender.get(iIdx).path;
                                        if (!selectedPaths.contains(path)) {
                                            selectedPaths.add(path);
                                        }
                                    }
                                }
                            } else {
                                selectedPaths.clear();
                                selectedPaths.add(selectedPath);
                                lastSelectedIndex = clickedIndex;
                            }
                            return true;
                        }
                    }
                }
                int fieldWidthDynamic = basePathFieldWidth;
                if (currentMode == Mode.SEARCH) {
                    fieldWidthDynamic = searchBarWidth;
                }
                fieldWidthDynamic = Math.min(fieldWidthDynamic + textRenderer.getWidth(fieldText.toString()), maxPathFieldWidth);
                fieldWidthDynamic = Math.max(basePathFieldWidth, fieldWidthDynamic);
                int fieldX = (this.width - fieldWidthDynamic) / 2;
                int fieldY = 5;
                int fieldHeight = titleBarHeight - 10;
                if (mouseX >= fieldX && mouseX <= fieldX + fieldWidthDynamic && mouseY >= fieldY && mouseY <= fieldY + fieldHeight) {
                    fieldFocused = true;
                    cursorPosition = fieldText.length();
                    selectionStart = -1;
                    selectionEnd = -1;
                    return true;
                } else {
                    fieldFocused = false;
                    currentMode = Mode.PATH;
                    updatePathInfo();
                }
                return false;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
                navigateUp();
                return true;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
                navigateBack();
                return true;
            }
        }
        if (!handled && currentMode == Mode.SEARCH) {
            currentMode = Mode.PATH;
            updatePathInfo();
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
            int explorerY = tabBarY + tabBarHeight + 30;
            int explorerHeight = this.height - explorerY - 10;
            int explorerX = 5;
            int explorerWidth = this.width - 10;
            if (mouseX >= explorerX && mouseX <= explorerX + explorerWidth && mouseY >= explorerY && mouseY <= explorerY + explorerHeight) {
                int relativeY = (int) mouseY - explorerY + (int) smoothOffset;
                int clickedIndex = relativeY / (entryHeight + 1);
                List<EntryData> entriesToRender;
                synchronized (fileEntriesLock) {
                    entriesToRender = new ArrayList<>(fileEntries);
                }
                if (clickedIndex >= 0 && clickedIndex < entriesToRender.size()) {
                    EntryData entryData = entriesToRender.get(clickedIndex);
                    selectedPaths.clear();
                    selectedPaths.add(entryData.path);
                    ContextMenu.hide();
                    ContextMenu.addItem("New Tab", () -> {
                        if (entryData.isDirectory) {
                            TabData newTabData = new TabData(entryData.path, serverInfo.isRemote, serverInfo.remoteHost);
                            tabs.add(new Tab(newTabData));
                            currentTabIndex = tabs.size() - 1;
                            loadDirectory(newTabData.path, false, false);
                        } else {
                            if (isSupportedFile(entryData.path)) {
                                minecraftClient.setScreen(new FileEditorScreen(minecraftClient, this, entryData.path, serverInfo));
                            } else {
                                openExternally(entryData.path);
                            }
                        }
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Externally", () -> {
                        openExternally(entryData.path);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Create File", () -> {
                        String defaultName = "NewFileOrFolder";
                        newCreationPath = currentPath.resolve(defaultName);
                        creatingNew = true;
                        renamePath = newCreationPath;
                        renameBuffer.setLength(0);
                        renameBuffer.append(defaultName);
                        renameCursorPos = renameBuffer.length();
                        try {
                            if (defaultName.contains(".")) {
                                if (!serverInfo.isRemote) {
                                    Files.createFile(newCreationPath);
                                } else {
                                    ensureRemoteConnected();
                                    serverInfo.remoteSSHManager.prepareRemoteDirectory(newCreationPath.toString().replace("\\", "/"));
                                }
                            } else {
                                if (!serverInfo.isRemote) {
                                    Files.createDirectory(newCreationPath);
                                } else {
                                    ensureRemoteConnected();
                                    serverInfo.remoteSSHManager.prepareRemoteDirectory(newCreationPath.toString().replace("\\", "/"));
                                }
                            }
                        } catch (Exception ignored) {}
                        loadDirectory(currentPath, false, true);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Rename", () -> {
                        renamePath = entryData.path;
                        renameBuffer.setLength(0);
                        renameBuffer.append(renamePath.getFileName().toString());
                        renameCursorPos = renameBuffer.length();
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Copy", () -> {
                        fileManager.copySelected(selectedPaths);
                        showNotification("Copied to clipboard", Notification.Type.INFO);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Cut", () -> {
                        fileManager.cutSelected(selectedPaths);
                        showNotification("Cut to clipboard", Notification.Type.INFO);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Paste", () -> {
                        fileManager.paste(currentPath);
                        showNotification("Pasted to " + currentPath, Notification.Type.INFO);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Delete", () -> {
                        fileManager.deleteSelected(selectedPaths, currentPath);
                    }, buttonTextDeleteHoverColor);
                    ContextMenu.addItem("Favorite", () -> {
                        synchronized (favoritePathsLock) {
                            for (Path p : selectedPaths) {
                                if (!favoritePaths.contains(p)) {
                                    favoritePaths.add(p);
                                } else {
                                    favoritePaths.remove(p);
                                }
                            }
                            saveFavorites();
                        }
                        showNotification("Favorites updated", Notification.Type.INFO);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Copy Path", () -> {
                        String quotedPath = "\"" + entryData.path.toString() + "\"";
                        minecraftClient.keyboard.setClipboard(quotedPath);
                        showNotification("Path copied", Notification.Type.INFO);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Refresh", () -> {
                        loadDirectory(currentPath, false, true);
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Undo", () -> {
                        if (serverInfo.isRemote) {
                            showNotification("Undo not supported for remote files.", Notification.Type.ERROR);
                        } else {
                            fileManager.undo(currentPath);
                        }
                    }, buttonTextHoverColor);
                    ContextMenu.addItem("Search", () -> {
                        currentMode = Mode.SEARCH;
                        fieldFocused = true;
                        fieldText.setLength(0);
                        cursorPosition = 0;
                        selectionStart = -1;
                        selectionEnd = -1;
                    }, buttonTextHoverColor);
                    ContextMenu.show((int) mouseX, (int) mouseY, 80, this.width, this.height);
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
                    Tab selectedTab = tabs.get(currentTabIndex);
                    currentPath = selectedTab.tabData.path;
                    serverInfo.isRemote = selectedTab.tabData.isRemote;
                    serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
                    targetOffset = selectedTab.tabData.scrollOffset;
                    loadDirectory(currentPath, false, false);
                }
                saveFileExplorerTabs(tabs.stream().map(t1 -> new TabData(t1.tabData.path, t1.tabData.isRemote, t1.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
            });
            tabToRemove.textAnimator.reverse();
        } else {
            tabs.remove(0);
            minecraftClient.setScreen(parent);
        }
    }

    private void openExternally(Path selectedPath) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("explorer.exe", selectedPath.toString());
        try {
            pb.start();
        } catch (IOException e) {
            showNotification("Failed to open file: " + e, Notification.Type.ERROR);
        }
    }

    private void renameSelectedFile() {
        if (renamePath == null || renameBuffer.length() == 0) {
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
                serverInfo.remoteSSHManager.renameRemoteFile(oldPath.toString().replace("\\", "/"), newPath.toString().replace("\\", "/"));
            } catch (Exception ignored) {}
        } else {
            try {
                Files.move(oldPath, newPath);
            } catch (Exception ignored) {}
        }
        renamePath = null;
        renameBuffer.setLength(0);
        creatingNew = false;
        newCreationPath = null;
        loadDirectory(currentPath, false, true);
    }
    private void navigateBack() {
        if (!history.isEmpty()) {
            Path previousPath = history.pop();
            forwardHistory.push(currentPath);
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
            Tab selectedTab = tabs.get(currentTabIndex);
            selectedTab.tabData.path = previousPath;
            currentPath = previousPath;
            loadDirectory(previousPath, false, false);
            serverInfo.isRemote = selectedTab.tabData.isRemote;
            serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
            targetOffset = selectedTab.tabData.scrollOffset;
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
            Tab currentTab = tabs.get(currentTabIndex);
            currentTab.tabData.scrollOffset = targetOffset;
            loadDirectory(parentPath, true, false);
            currentTab.tabData.path = parentPath;
            currentTab.setName(parentPath.getFileName() != null ? parentPath.getFileName().toString() : parentPath.toString());
        } else {
            minecraftClient.setScreen(parent);
        }
        saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
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
                map.put("scrollOffset", td.scrollOffset);
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
        } catch (IOException e) {}
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
                    tabData.scrollOffset = scrollOffset;
                    tabsData.add(tabData);
                }
            } catch (IOException e) {}
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
            } catch (IOException e) {}
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
                if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                    int selStart = Math.min(selectionStart, selectionEnd);
                    int selEnd = Math.max(selectionStart, selectionEnd);
                    fieldText.delete(selStart, selEnd);
                    cursorPosition = selStart;
                    selectionStart = -1;
                    selectionEnd = -1;
                }
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
            loadDirectory(newPath, false, false);
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).tabData.path.equals(newPath) && tabs.get(i).tabData.isRemote == isRemote && Objects.equals(tabs.get(i).tabData.remoteHostInfo, serverInfo.remoteHost)) {
                    currentTabIndex = i;
                    return;
                }
            }
            Tab currentTab = tabs.get(currentTabIndex);
            currentTab.tabData.path = newPath;
            currentTab.setName(newPath.getFileName() != null ? newPath.getFileName().toString() : newPath.toString());
            currentTab.tabData.scrollOffset = 0;
        } else {
            showNotification("Invalid path.", Notification.Type.ERROR);
        }
    }
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, explorerScreenBackgroundColor, explorerScreenBackgroundColor);
    }
    @Override
    public void tick() {
        super.tick();
        if (fieldFocused) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBlinkTime >= 500) {
                showCursor = !showCursor;
                lastBlinkTime = currentTime;
            }
        }
    }
    public void showNotification(String message, Notification.Type type) {
        notifications.add(new Notification(message, type, this.width, this.height));
    }
    private void updateNotifications(float delta) {
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            notification.update(delta);
            if (notification.isFinished()) {
                iterator.remove();
            }
        }
    }
    private void renderNotifications(DrawContext context, int mouseX, int mouseY, float delta) {
        for (Notification notification : notifications) {
            notification.render(context);
        }
    }
    void loadDirectory(Path dir, boolean addToHistory, boolean forceReload) {
        if (loading) {}
        if (addToHistory && currentPath != null && !currentPath.equals(dir)) {
            history.push(currentPath);
            forwardHistory.clear();
            Tab currentTab = tabs.get(currentTabIndex);
            currentTab.tabData.scrollOffset = targetOffset;
            targetOffset = 0;
        }
        if (addToHistory) {
            targetOffset = 0;
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
        pathTextAnimator.setOnAnimationEnd(() -> {
            pathTextAnimator.updateText(currentPath.toString());
            pathTextAnimator.setOnAnimationEnd(null);
        });
        pathTextAnimator.reverse();
        int thisRequestId = ++loadRequestId;
        Tab currentTab = tabs.get(currentTabIndex);
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
            } catch (Exception e) {}
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
        List<String> entries;
        try {
            entries = serverInfo.remoteSSHManager.listRemoteDirectory(remotePath);
        } catch (Exception e) {
            return new ArrayList<>();
        }
        List<EntryData> temp = new ArrayList<>();
        for (String e : entries) {
            Path p = dir.resolve(e);
            boolean d = serverInfo.remoteSSHManager.isRemoteDirectory(p.toString().replace("\\", "/"));
            String dn = e;
            if (textRenderer.getWidth(dn) > MAX_NAME_WIDTH) {
                dn = doEllipsize(dn);
            }
            temp.add(new EntryData(p, d, "", "", dn));
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
    private void ensureRemoteConnected() {
        if (serverInfo.remoteHost == null) {
            return;
        }
        if (serverInfo.remoteSSHManager == null) {
            serverInfo.remoteSSHManager = new SSHManager(serverInfo);
            try {
                serverInfo.remoteSSHManager.connectToRemoteHost(
                        serverInfo.remoteHost.getUser(),
                        serverInfo.remoteHost.getIp(),
                        serverInfo.remoteHost.getPort(),
                        serverInfo.remoteHost.getPassword()
                );
                serverInfo.remoteSSHManager.connectSFTP();
            } catch (Exception ignored) {}
        } else if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
            try {
                serverInfo.remoteSSHManager.connectSFTP();
            } catch (Exception ignored) {}
        }
    }
    private void filterFileEntries() {
        String query = fieldText.toString().toLowerCase();
        List<EntryData> filtered = new ArrayList<>();
        synchronized (fileEntriesLock) {
            for (EntryData data : fullEntries) {
                if (data.path.getFileName().toString().toLowerCase().contains(query)) {
                    filtered.add(data);
                }
            }
            filtered.sort(Comparator.comparing((EntryData x) -> !favoritePaths.contains(x.path)));
            filtered.sort(Comparator.comparing(x -> !x.isDirectory));
            filtered.sort(Comparator.comparing(x -> x.path.getFileName().toString().toLowerCase()));
            fileEntries.clear();
            loadedCount = 0;
            fullEntries.clear();
            fullEntries.addAll(filtered);
            hasMore = true;
            isLoadingMore = false;
        }
        loadMoreEntries();
        targetOffset = 0;
    }
    private boolean isSupportedFile(Path file) {
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
    private int blendColor(int color, float opacity) {
        int a = (int) ((color >> 24 & 0xFF) * opacity);
        int r = (color >> 16 & 0xFF);
        int g = (color >> 8 & 0xFF);
        int b = (color & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    @Override
    public void refreshDirectory(Path path) {
        loadDirectory(path, false, true);
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
    public class Notification {
        private final TextRenderer textRenderer = minecraftClient.textRenderer;
        enum Type { INFO, WARN, ERROR }
        private String message;
        private Type type;
        private float x;
        private float y;
        private float targetX;
        private float opacity;
        private float animationSpeed = 30.0f;
        private float fadeOutSpeed = 100.0f;
        private float currentOpacity = 0.0f;
        private float maxOpacity = 1.0f;
        private float duration = 50.0f;
        private float elapsedTime = 0.0f;
        private boolean fadingOut = false;
        private int padding = 10;
        private int width;
        private int height;
        private static final List<Notification> activeNotifications = new ArrayList<>();
        Notification(String message, Type type, int screenWidth, int screenHeight) {
            this.message = message;
            this.type = type;
            this.width = textRenderer.getWidth(message) + 2 * padding;
            this.height = textRenderer.fontHeight + 2 * padding;
            this.x = screenWidth;
            this.y = screenHeight - this.height - padding - (activeNotifications.size() * (this.height + padding));
            this.targetX = screenWidth - this.width - padding;
            this.opacity = 1.0f;
            this.currentOpacity = 1.0f;
            activeNotifications.add(this);
        }
        void update(float delta) {
            if (x > targetX) {
                float move = animationSpeed * delta;
                x -= move;
                if (x < targetX) {
                    x = targetX;
                }
            } else if (!fadingOut) {
                elapsedTime += delta;
                if (elapsedTime >= duration) {
                    fadingOut = true;
                }
            }
            if (fadingOut) {
                currentOpacity -= fadeOutSpeed * delta / 1000.0f;
                if (currentOpacity <= 0.0f) {
                    currentOpacity = 0.0f;
                    activeNotifications.remove(this);
                }
            } else {
                currentOpacity = maxOpacity;
            }
        }
        boolean isFinished() {
            return currentOpacity <= 0.0f;
        }
        void render(DrawContext context) {
            if (currentOpacity <= 0.0f) return;
            int color;
            switch (type) {
                case ERROR -> color = blendColor(0xFFFF5555, currentOpacity);
                case WARN -> color = blendColor(0xFFFFAA55, currentOpacity);
                default -> color = blendColor(0xFF5555FF, currentOpacity);
            }
            context.fill((int) x, (int) y, (int) x + width, (int) y + height, color);
            drawInnerBorder(context, (int) x, (int) y, width, height, blendColor(0xFF000000, currentOpacity));
            drawOuterBorder(context, (int) x, (int) y, width, height, globalBottomBorder);
            context.drawText(textRenderer, Text.literal(message), (int) x + padding, (int) y + padding, blendColor(0xFFFFFFFF, currentOpacity), Config.shadow);
        }
        private int blendColor(int color, float opacity) {
            int a = (int) ((color >> 24 & 0xFF) * opacity);
            int r = (color >> 16 & 0xFF);
            int g = (color >> 8 & 0xFF);
            int b = (color & 0xFF);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}
