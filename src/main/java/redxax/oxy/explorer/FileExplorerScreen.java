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

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static redxax.oxy.ImageUtil.*;
import static redxax.oxy.Render.*;

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
            ".xml", ".cfg", ".sk", ".log",  ".mcmeta", ".bat", ".sh", ".json5", ".jsonc",
            ".html", ".js", ".java", ".py", ".css", ".vsh", ".fsh", ".glsl", ".nu",
            ".bash", ".fish", ".toml"
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
    private final Path favoritesFilePath = Paths.get("C:/remotely/data/favorites.dat");

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

    private static class EntryData {
        Path path;
        boolean isDirectory;
        String size;
        String created;
        EntryData(Path p, boolean d, String s, String c) {
            path = p;
            isDirectory = d;
            size = s;
            created = c;
        }
    }

    private static class TabData {
        Path path;
        boolean isRemote;
        RemoteHostInfo remoteHostInfo;
        float scrollOffset;

        TabData(Path path, boolean isRemote, RemoteHostInfo remoteHostInfo) {
            this.path = path;
            this.isRemote = isRemote;
            this.remoteHostInfo = remoteHostInfo;
            this.scrollOffset = 0;
        }
    }

    class Tab {
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
            saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
        }

        public String getAnimatedText() {
            return textAnimator.getCurrentText();
        }

        public int getCurrentWidth(TextRenderer textRenderer) {
            return textRenderer.getWidth(getAnimatedText()) + 2 * TAB_PADDING;
        }
    }

    private List<Tab> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
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
                List<String> lines = Files.readAllLines(favoritesFilePath);
                synchronized (favoritePathsLock) {
                    for (String line : lines) {
                        Path p = serverInfo.isRemote ? Paths.get(line.replace("\\", "/")) : Paths.get(line);
                        favoritePaths.add(p);
                    }
                }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        loadDirectory(currentPath, false, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int titleBarHeight = 30;
        int tabBarY = titleBarHeight + 5;
        int tabBarHeight = TAB_HEIGHT;
        int tabX = 5;
        int tabY = tabBarY;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = tab.getCurrentWidth(textRenderer);
            boolean isActive = (i == currentTabIndex);
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabBarHeight;
            int bgColor = isActive ? darkGreen : (isHovered ? highlightColor : elementBg);
            context.fill(tabX, tabY, tabX + tabWidth, tabY + tabBarHeight, bgColor);
            drawInnerBorder(context, tabX, tabY, tabWidth, tabBarHeight, isActive ? greenBright : isHovered ? elementBorderHover : elementBorder);
            context.drawText(this.textRenderer, Text.literal(tab.getAnimatedText()), tabX + TAB_PADDING, tabY + 5, isHovered ? greenBright : textColor, Config.shadow);

            context.fill(tabX, tabY + tabBarHeight, tabX + tabWidth, tabY + tabBarHeight + 2, isActive ? 0xFF0b0b0b : 0xFF000000);

            tabX += tabWidth + TAB_GAP;
        }
        int plusTabX = tabX;
        boolean isPlusTabHovered = mouseX >= plusTabX && mouseX <= plusTabX + PLUS_TAB_WIDTH && mouseY >= tabY && mouseY <= tabY + tabBarHeight;
        context.fill(plusTabX, tabY, plusTabX + PLUS_TAB_WIDTH, tabY + tabBarHeight, isPlusTabHovered ? highlightColor : elementBg);
        drawInnerBorder(context, plusTabX, tabY, PLUS_TAB_WIDTH, tabBarHeight, isPlusTabHovered ? elementBorderHover : elementBorder);
        context.drawText(this.textRenderer, Text.literal("+"), plusTabX + PLUS_TAB_WIDTH / 2 - textRenderer.getWidth("+") / 2, tabY + 5, isPlusTabHovered ? greenBright : textColor, Config.shadow);

        int explorerY = tabBarY + tabBarHeight + 30;
        int explorerHeight = this.height - explorerY - 10;
        int explorerX = 5;
        int explorerWidth = this.width - 10;

        int headerY = explorerY - 25;
        context.fill(explorerX, headerY, explorerX + explorerWidth, headerY + 25, BgColor);
        drawInnerBorder(context, explorerX, headerY, explorerWidth, 25, borderColor);

        context.drawText(this.textRenderer, Text.literal("Name"), explorerX + 10, headerY + 5, textColor, Config.shadow);
        if (!serverInfo.isRemote) {
            int createdX = explorerX + explorerWidth - 100;
            int sizeX = createdX - 100;
            context.drawText(this.textRenderer, Text.literal("Created"), createdX, headerY + 5, textColor, Config.shadow);
            context.drawText(this.textRenderer, Text.literal("Size"), sizeX, headerY + 5, textColor, Config.shadow);
        }

        context.fill(0, 0, this.width, titleBarHeight, 0xFF222222);
        drawInnerBorder(context, 0, 0, this.width, titleBarHeight, 0xFF333333);

        String prefixText = "Remotely - File Explorer";
        context.drawText(this.textRenderer, Text.literal(prefixText), 10, 10, textColor, Config.shadow);

        int fieldWidthDynamic = basePathFieldWidth;
        if (currentMode == Mode.SEARCH) {
            fieldWidthDynamic = searchBarWidth;
        }

        int fieldX = (this.width - fieldWidthDynamic) / 2;
        int fieldY = 5;
        int fieldHeight = titleBarHeight - 10;
        int fieldColor = fieldFocused ? (currentMode == Mode.SEARCH ? redBg : darkGreen) : elementBg;
        context.fill(fieldX, fieldY, fieldX + fieldWidthDynamic, fieldY + fieldHeight, fieldColor);
        drawInnerBorder(context, fieldX, fieldY, fieldWidthDynamic, fieldHeight, fieldFocused ? (currentMode == Mode.SEARCH ? redBright : greenBright) : elementBorder);

        if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
            int selStart = Math.max(0, Math.min(selectionStart, selectionEnd));
            int selEnd = Math.min(fieldText.length(), Math.max(selectionStart, selectionEnd));
            if (selStart < 0) selStart = 0;
            if (selEnd > fieldText.length()) selEnd = fieldText.length();
            String beforeSelection = fieldText.substring(0, selStart);
            String selectedText = fieldText.substring(selStart, selEnd);
            int selX = fieldX + 5 + textRenderer.getWidth(beforeSelection);
            int selWidth = textRenderer.getWidth(selectedText);
            context.fill(selX, fieldY + 5, selX + selWidth, fieldY + 5 + textRenderer.fontHeight, 0x80FFFFFF);
        }

        String displayText = fieldFocused ? fieldText.toString() : pathTextAnimator.getCurrentText();
        int displayWidth = fieldWidthDynamic - 10;
        int textWidth = textRenderer.getWidth(displayText);

        int cursorX = fieldX + 5 + textRenderer.getWidth(displayText.substring(0, Math.min(cursorPosition, displayText.length())));
        float cursorMargin = 50.0f;
        if (cursorX - pathScrollOffset > fieldX + fieldWidthDynamic - 5 - cursorMargin) {
            pathTargetScrollOffset = cursorX - (fieldX + fieldWidthDynamic - 5 - cursorMargin);
        } else if (cursorX - pathScrollOffset < fieldX + 5 + cursorMargin) {
            pathTargetScrollOffset = cursorX - (fieldX + 5 + cursorMargin);
        }

        pathTargetScrollOffset = Math.max(0, Math.min(pathTargetScrollOffset, textWidth - displayWidth));

        pathScrollOffset += (pathTargetScrollOffset - pathScrollOffset) * scrollSpeed;
        Tab currentTab = tabs.get(currentTabIndex);
        currentTab.tabData.scrollOffset = targetOffset;

        context.enableScissor(fieldX, fieldY, fieldX + fieldWidthDynamic, fieldY + fieldHeight);
        context.drawText(this.textRenderer, Text.literal(displayText), fieldX + 5 - (int) pathScrollOffset, fieldY + 5, textColor, Config.shadow);

        if (currentMode == Mode.SEARCH && fieldFocused && fieldText.length() == 0) {
            context.drawText(this.textRenderer, Text.literal("Search..."), fieldX + 5 - (int) pathScrollOffset, fieldY + 5, 0xFF888888, false);
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlinkTime >= 500) {
            showCursor = !showCursor;
            lastBlinkTime = currentTime;
        }
        if (fieldFocused && showCursor) {
            String beforeCursor = cursorPosition <= displayText.length() ? displayText.substring(0, cursorPosition) : displayText;
            int cursorPosX = fieldX + 5 + textRenderer.getWidth(beforeCursor) - (int) pathScrollOffset;
            int blendedCursorColor = blendColor(0xFFFFFFFF, 1.0f);
            context.fill(cursorPosX, fieldY + 5, cursorPosX + 2, fieldY + 5 + textRenderer.fontHeight, blendedCursorColor);
        }
        context.disableScissor();

        int closeButtonX = this.width - buttonW - 10;
        boolean hoveredBack = mouseX >= closeButtonX && mouseX <= closeButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH;
        drawCustomButton(context, closeButtonX, buttonY, "Close", minecraftClient, hoveredBack, false, true, textColor, redVeryBright);

        int backButtonX = closeButtonX - (buttonW + 10);
        int backYLocal = 5;
        boolean hoveredClose = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backYLocal && mouseY <= backYLocal + buttonH;
        drawCustomButton(context, backButtonX, backYLocal, "Back", minecraftClient, hoveredClose, false, true, textColor, greenBright);

        if (loading && currentTab.tabData.isRemote) {
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
        int visibleEntries = explorerHeight / (entryHeight + 1);
        int startIndex = (int) Math.floor(smoothOffset / (entryHeight + 1)) - 1;
        if (startIndex < 0) startIndex = 0;
        int endIndex = startIndex + visibleEntries + 2;
        if (endIndex > entriesToRender.size()) endIndex = entriesToRender.size();

        context.enableScissor(explorerX, explorerY, explorerX + explorerWidth, explorerY + explorerHeight);

        for (int entryIndex = startIndex; entryIndex < endIndex; entryIndex++) {
            EntryData entry = entriesToRender.get(entryIndex);
            int entryY = explorerY + (entryIndex * (entryHeight + 1)) - (int) smoothOffset;
            boolean hovered = mouseX >= explorerX && mouseX <= explorerX + explorerWidth && mouseY >= entryY && mouseY < entryY + entryHeight;
            boolean isSelected = selectedPaths.contains(entry.path);
            boolean isFavorite;
            synchronized (favoritePathsLock) {
                isFavorite = favoritePaths.contains(entry.path);
            }
            int bg = isSelected ? (isFavorite ? darkGold : darkGreen) : (hovered ? highlightColor : elementBg);
            int borderWithOpacity = isFavorite ? isSelected ? kingsGold : paleGold : (isSelected ? greenBright : (hovered ? elementBorderHover : elementBorder));
            int textWithOpacity = textColor;
            context.fill(explorerX, entryY, explorerX + explorerWidth, entryY + entryHeight, bg);
            drawInnerBorder(context, explorerX, entryY, explorerWidth, entryHeight, borderWithOpacity);

            context.fill(explorerX, entryY + entryHeight - 1, explorerX + explorerWidth, entryY + entryHeight, borderWithOpacity);

            BufferedImage icon = entry.isDirectory ? folderIcon : fileIcon;
            drawBufferedImage(context, icon, explorerX + 10, entryY + 5, 16, 16);
            if (isFavorite) drawBufferedImage(context, pinIcon, explorerX + 5, entryY + 5, 16, 16);

            String displayName = entry.path.getFileName().toString();
            context.drawText(this.textRenderer, Text.literal(displayName), explorerX + 30, entryY + 5, textWithOpacity, Config.shadow);
            if (!serverInfo.isRemote) {
                int createdX = explorerX + explorerWidth - 100;
                int sizeX = createdX - 100;
                context.drawText(this.textRenderer, Text.literal(entry.created), createdX, entryY + 5, textWithOpacity, Config.shadow);
                context.drawText(this.textRenderer, Text.literal(entry.size), sizeX, entryY + 5, textWithOpacity, Config.shadow);
            }
        }

        context.disableScissor();

        if (smoothOffset > 0) {
            context.fillGradient(explorerX, explorerY, explorerX + explorerWidth, explorerY + 10, 0x80000000, 0x00000000);
        }
        if (smoothOffset < Math.max(0, (entriesToRender.size() * (entryHeight + 1)) - explorerHeight)) {
            context.fillGradient(explorerX, explorerY + explorerHeight - 10, explorerX + explorerWidth, explorerY + explorerHeight, 0x00000000, 0x80000000);
        }

        updateNotifications(delta);
        renderNotifications(context, mouseX, mouseY, delta);

        if (ContextMenu.isOpen()) {
            ContextMenu.renderMenu(context, minecraftClient, mouseX, mouseY);
        }
    }

    private void updatePathInfo() {
        fieldText.setLength(0);
        fieldText.append(currentPath.toString());
        cursorPosition = fieldText.length();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
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
        }
        if (currentMode == Mode.PATH && keyCode == GLFW.GLFW_KEY_DELETE) {
            fileManager.deleteSelected(selectedPaths, currentPath);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
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
        targetOffset -= (float) ((verticalAmount * entryHeight * 0.5f) * scrollMultiplier);
        List<EntryData> entriesToRender;
        synchronized (fileEntriesLock) {
            entriesToRender = new ArrayList<>(fileEntries);
        }
        targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, entriesToRender.size() * entryHeight - (this.height - 70))));
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
                    if (tabs.size() > 1) {
                        Tab tabToRemove = tabs.get(i);
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
                            saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
                        });
                        tabToRemove.textAnimator.reverse();
                    } else {
                        tabs.remove(0);
                        minecraftClient.setScreen(parent);
                    }
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
                    TabData newTabData = new TabData(currentPath, serverInfo.isRemote, serverInfo.remoteHost);
                    Tab newTab = new Tab(newTabData);
                    tabs.add(newTab);
                    currentTabIndex = tabs.size() - 1;
                    saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
                    loadDirectory(newTab.tabData.path, false, false);
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
                                loadDirectory(selectedPath, true, false);
                            } else {
                                if (importMode && selectedPath.getFileName().toString().equalsIgnoreCase("server.jar")) {
                                    String folderName = selectedPath.getParent().getFileName().toString();
                                    if (parent instanceof redxax.oxy.servers.ServerManagerScreen sms) {
                                        sms.importServerJar(selectedPath, folderName);
                                    }
                                    minecraftClient.setScreen(parent);
                                    return true;
                                }
                                if (isSupportedFile(selectedPath)) {
                                    minecraftClient.setScreen(new FileEditorScreen(minecraftClient, this, selectedPath, serverInfo));
                                } else {
                                    showNotification("Unsupported file.", Notification.Type.ERROR);
                                }
                            }
                            lastClickedIndex = -1;
                            return true;
                        } else {
                            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && entryData.isDirectory) {
                                TabData newTabData = new TabData(selectedPath, serverInfo.isRemote, serverInfo.remoteHost);
                                Tab newTab = new Tab(newTabData);
                                tabs.add(newTab);
                                currentTabIndex = tabs.size() - 1;
                                saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
                                loadDirectory(newTab.tabData.path, false, false);
                                return true;
                            }
                            lastClickedIndex = clickedIndex;
                            if (!serverInfo.isRemote) {
                                boolean ctrlPressed = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) ||
                                        (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
                                boolean shiftPressed = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) ||
                                        (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
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
                            } else {
                                selectedPaths.clear();
                                selectedPaths.add(selectedPath);
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
                if (mouseX >= fieldX && mouseX <= fieldX + fieldWidthDynamic &&
                        mouseY >= fieldY && mouseY <= fieldY + fieldHeight) {
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
                    Render.ContextMenu.hide();
                    Render.ContextMenu.addItem("Open New", () -> {
                        if (entryData.isDirectory) {
                            TabData newTabData = new TabData(entryData.path, serverInfo.isRemote, serverInfo.remoteHost);
                            Tab newTab = new Tab(newTabData);
                            tabs.add(newTab);
                            currentTabIndex = tabs.size() - 1;
                            saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
                            loadDirectory(newTab.tabData.path, false, false);
                        } else {
                            if (isSupportedFile(entryData.path)) {
                                minecraftClient.setScreen(new FileEditorScreen(minecraftClient, this, entryData.path, serverInfo));
                            } else {
                                showNotification("Unsupported file.", Notification.Type.ERROR);
                            }
                        }
                    }, greenBright);
                    Render.ContextMenu.addItem("Copy", () -> {
                        fileManager.copySelected(selectedPaths);
                        showNotification("Copied to clipboard", Notification.Type.INFO);
                    }, greenBright);
                    Render.ContextMenu.addItem("Favorite", () -> {
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
                    }, greenBright);
                    Render.ContextMenu.addItem("Cut", () -> {
                        fileManager.cutSelected(selectedPaths);
                        showNotification("Cut to clipboard", Notification.Type.INFO);
                    }, greenBright);
                    Render.ContextMenu.addItem("Paste", () -> {
                        fileManager.paste(currentPath);
                        showNotification("Pasted to " + currentPath, Notification.Type.INFO);
                    }, greenBright);
                    Render.ContextMenu.addItem("Delete", () -> {
                        fileManager.deleteSelected(selectedPaths, currentPath);
                    }, deleteHoverColor);
                    Render.ContextMenu.addItem("Copy Path", () -> {
                        minecraftClient.keyboard.setClipboard(entryData.path.toString());
                        showNotification("Path copied", Notification.Type.INFO);
                    }, greenBright);
                    Render.ContextMenu.show((int) mouseX, (int) mouseY, 80);
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void navigateBack() {
        if (!history.isEmpty()) {
            Path previousPath = history.pop();
            forwardHistory.push(currentPath);
            loadDirectory(previousPath, true, false);
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).tabData.path.equals(previousPath)) {
                    currentTabIndex = i;
                    Tab selectedTab = tabs.get(currentTabIndex);
                    serverInfo.isRemote = selectedTab.tabData.isRemote;
                    serverInfo.remoteHost = selectedTab.tabData.remoteHostInfo;
                    targetOffset = selectedTab.tabData.scrollOffset;
                    break;
                }
            }
            saveFileExplorerTabs(tabs.stream().map(t -> new TabData(t.tabData.path, t.tabData.isRemote, t.tabData.remoteHostInfo)).collect(Collectors.toList()), currentTabIndex);
        }
    }

    private void navigateUp() {
        if (tabs.isEmpty()) {
            minecraftClient.setScreen(parent);
            return;
        }
        Tab currentTab = tabs.get(currentTabIndex);
        if (currentTab.tabData.isRemote) {
            if (currentPath == null || currentPath.toString().equals("/")) {
                minecraftClient.setScreen(parent);
            } else {
                Path parentPath = currentPath.getParent();
                if (parentPath == null || parentPath.toString().isEmpty()) {
                    currentPath = Paths.get("/");
                    currentTab.tabData.scrollOffset = 0;
                    loadDirectory(currentPath, true, false);
                    currentTab.setName(currentPath.getFileName() != null ? currentPath.getFileName().toString() : currentPath.toString());
                } else {
                    currentTab.tabData.scrollOffset = targetOffset;
                    loadDirectory(parentPath, true, false);
                    currentTab.setName(parentPath.getFileName() != null ? parentPath.getFileName().toString() : parentPath.toString());
                }
            }
        } else {
            Path parentPath = currentPath.getParent();
            if (parentPath != null && parentPath.startsWith(Paths.get(serverInfo.path).toAbsolutePath().normalize())) {
                currentTab.tabData.scrollOffset = targetOffset;
                loadDirectory(parentPath, true, false);
                currentTab.setName(parentPath.getFileName() != null ? parentPath.getFileName().toString() : parentPath.toString());
            } else {
                minecraftClient.setScreen(parent);
            }
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
        } catch (IOException e) {
            System.out.println("Failed to save File Explorer tabs: " + e.getMessage());
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
                    tabData.scrollOffset = scrollOffset;
                    tabsData.add(tabData);
                }
            } catch (IOException e) {
                System.out.println("Failed to load File Explorer tabs: " + e.getMessage());
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
            } catch (IOException e) {
                System.out.println("Failed to load current tab index: " + e.getMessage());
            }
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
            loadDirectory(newPath, true, false);
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
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
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
        System.out.println(message);
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

    public void loadDirectory(Path dir) {
        loadDirectory(dir, true, false);
        updatePathInfo();
    }

    private void loadDirectory(Path dir, boolean addToHistory, boolean forceReload) {
        if (loading) return;
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
        Tab currentTab = tabs.get(currentTabIndex);
        String key = dir.toString() + "_" + serverInfo.isRemote + (serverInfo.isRemote && serverInfo.remoteHost != null ? "_" + serverInfo.remoteHost.getIp() + "_" + serverInfo.remoteHost.getPort() : "");
        boolean shouldCache = serverInfo.isRemote && serverInfo.remoteHost != null && !serverInfo.remoteHost.getIp().equals("127.0.0.1");
        if (!shouldCache) {
            remoteCache.remove(key);
        }
        if (!serverInfo.isRemote || !shouldCache) {
        } else {
            if (!forceReload && remoteCache.containsKey(key)) {
                synchronized (fileEntriesLock) {
                    fileEntries = remoteCache.get(key);
                }
                currentTab.tabData.scrollOffset = targetOffset;
                return;
            }
        }
        loading = true;
        directoryLoader.submit(() -> {
            try {
                List<EntryData> temp;
                if (serverInfo.isRemote) {
                    ensureRemoteConnected();
                    if (serverInfo.remoteHost == null) {
                        showNotification("Remote host information is missing.", Notification.Type.ERROR);
                        temp = new ArrayList<>();
                    } else {
                        temp = loadRemoteDirectory(dir, forceReload);
                    }
                } else {
                    temp = loadLocalDirectory(dir);
                }
                synchronized (fileEntriesLock) {
                    fileEntries = temp;
                }
                if (serverInfo.isRemote && shouldCache) {
                    remoteCache.put(key, temp);
                }
            } catch (Exception e) {
                showNotification("Error reading directory: " + e.getMessage(), Notification.Type.ERROR);
            } finally {
                loading = false;
            }
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
            showNotification("Error reading remote directory: " + e.getMessage(), Notification.Type.ERROR);
            return new ArrayList<>();
        }
        List<EntryData> temp = new ArrayList<>();
        for (String e : entries) {
            Path p = dir.resolve(e);
            boolean d = serverInfo.remoteSSHManager.isRemoteDirectory(p.toString().replace("\\", "/"));
            temp.add(new EntryData(p, d, "", ""));
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                boolean d = Files.isDirectory(entry);
                String sz = d ? "-" : getFileSize(entry);
                String cr = getCreationDate(entry);
                temp.add(new EntryData(entry, d, sz, cr));
            }
        }
        synchronized (favoritePathsLock) {
            temp.sort(Comparator.comparing((EntryData x) -> !favoritePaths.contains(x.path))
                    .thenComparing(x -> !x.isDirectory)
                    .thenComparing(x -> x.path.getFileName().toString().toLowerCase()));
        }
        return temp;
    }

    private void ensureRemoteConnected() {
        if (serverInfo.remoteHost == null) {
            showNotification("Remote host information is missing.", Notification.Type.ERROR);
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
            } catch (Exception e) {
                showNotification("Error connecting to remote host: " + e.getMessage(), Notification.Type.ERROR);
            }
        } else if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
            try {
                serverInfo.remoteSSHManager.connectSFTP();
            } catch (Exception e) {
                showNotification("Error connecting to SFTP: " + e.getMessage(), Notification.Type.ERROR);
            }
        }
    }

    private void filterFileEntries() {
        String query = fieldText.toString().toLowerCase();
        List<EntryData> filtered = new ArrayList<>();
        synchronized (fileEntriesLock) {
            for (EntryData data : fileEntries) {
                if (data.path.getFileName().toString().toLowerCase().contains(query)) {
                    filtered.add(data);
                }
            }
        }
        synchronized (favoritePathsLock) {
            filtered.sort(Comparator.comparing((EntryData x) -> !favoritePaths.contains(x.path)));
        }
        filtered.sort(Comparator.comparing(x -> !x.isDirectory));
        filtered.sort(Comparator.comparing(x -> x.path.getFileName().toString().toLowerCase()));
        synchronized (fileEntriesLock) {
            fileEntries = filtered;
        }
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

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
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
        loadDirectory(path, false, false);
    }

    private void saveFavorites() {
        try (BufferedWriter writer = Files.newBufferedWriter(favoritesFilePath)) {
            synchronized (favoritePathsLock) {
                for (Path p : favoritePaths) {
                    writer.write(p.toString());
                    writer.newLine();
                }
            }
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
            this.y = screenHeight - height - padding - (activeNotifications.size() * (height + padding));
            this.targetX = screenWidth - width - padding;
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
            context.drawText(this.textRenderer, Text.literal(message), (int) x + padding, (int) y + padding, blendColor(0xFFFFFFFF, currentOpacity), Config.shadow);
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
