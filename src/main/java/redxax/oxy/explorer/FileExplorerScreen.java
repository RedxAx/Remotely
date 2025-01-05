package redxax.oxy.explorer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.CursorUtils;
import redxax.oxy.servers.ServerInfo;
import redxax.oxy.SSHManager;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import static redxax.oxy.ImageUtil.*;

public class FileExplorerScreen extends Screen implements FileManager.FileManagerCallback {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private List<EntryData> fileEntries;
    private final Object fileEntriesLock = new Object();
    private float smoothOffset = 0;
    private int entryHeight = 25;
    private int baseColor = 0xFF181818;
    private int BgColor = 0xFF242424;
    private int BorderColor = 0xFF555555;
    private int elementBg = 0xFF2C2C2C;
    private int elementSelectedBorder = 0xFFd6f264;
    private int elementSelectedBg = 0xFF0b371c;
    private int elementBorder = 0xFF444444;
    private int elementBorderHover = 0xFF9d9d9d;
    private int highlightColor = 0xFF444444;
    private int favorateBorder = 0xFFffc800;
    private int favorateBg = 0xFF3b2d17;
    private int textColor = 0xFFFFFFFF;
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
    private boolean searchActive = false;
    private StringBuilder searchQuery = new StringBuilder();
    private int searchBarX = 10;
    private int searchBarY = 0;
    private int searchBarWidth = 200;
    private int searchBarHeight = 20;
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
    private BufferedImage loadingAnim;
    private List<BufferedImage> loadingFrames = new ArrayList<>();
    private int currentLoadingFrame = 0;
    private long lastFrameTime = 0;
    private final List<Path> favoritePaths = new ArrayList<>();
    private final Object favoritePathsLock = new Object();
    private final Path favoritesFilePath = Paths.get("C:/remotely/data/favorites.dat");

    private boolean pathFieldFocused = false;
    private StringBuilder pathFieldText = new StringBuilder();
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private long lastBlinkTime = 0;
    private boolean showCursor = true;
    private final int basePathFieldWidth = 200;
    private final int maxPathFieldWidth = 600;
    private float pathScrollOffset = 0;
    private float pathTargetScrollOffset = 0;


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
        this.fileManager = new FileManager(this, serverInfo);
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
        this.pathFieldText.append(currentPath.toString());
        this.cursorPosition = pathFieldText.length();
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
            loadingAnim = loadSpriteSheet("/assets/remotely/icons/loadinganim.png");
            int frameWidth = 16;
            int frameHeight = 16;
            int rows = loadingAnim.getHeight() / frameHeight;
            for (int i = 0; i < rows; i++) {
                BufferedImage frame = loadingAnim.getSubimage(0, i * frameHeight, frameWidth, frameHeight);
                loadingFrames.add(frame);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        loadDirectory(currentPath);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int explorerX = 5;
        int explorerY = 60;
        int explorerWidth = this.width - 10;
        int explorerHeight = this.height - 70;
        int gap = 1;

        int headerY = explorerY - 25;
        context.fill(explorerX, headerY, explorerX + explorerWidth, headerY + 25, BgColor);
        drawInnerBorder(context, explorerX, headerY, explorerWidth, 25, BorderColor);

        context.drawText(this.textRenderer, Text.literal("Name"), explorerX + 10, headerY + 5, textColor, true);
        if (!serverInfo.isRemote) {
            context.drawText(this.textRenderer, Text.literal("Size"), explorerX + 250, headerY + 5, textColor, true);
            context.drawText(this.textRenderer, Text.literal("Created"), explorerX + 350, headerY + 5, textColor, true);
        }

        int titleBarHeight = 30;
        context.fill(0, 0, this.width, titleBarHeight, 0xFF222222);
        drawInnerBorder(context, 0, 0, this.width, titleBarHeight, 0xFF333333);

        String prefixText = "Remotely - File Explorer";
        context.drawText(this.textRenderer, Text.literal(prefixText), 10, 10, textColor, true);

        int pathFieldWidthDynamic = basePathFieldWidth;
        int pathFieldX = (this.width - pathFieldWidthDynamic) / 2;
        int pathFieldY = 5;
        int pathFieldHeight = titleBarHeight - 10;
        int pathFieldColor = pathFieldFocused ? elementSelectedBg : elementBg;
        context.fill(pathFieldX, pathFieldY, pathFieldX + pathFieldWidthDynamic, pathFieldY + pathFieldHeight, pathFieldColor);
        drawInnerBorder(context, pathFieldX, pathFieldY, pathFieldWidthDynamic, pathFieldHeight, pathFieldFocused ? elementSelectedBorder : elementBorder);

        if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
            int selStart = Math.max(0, Math.min(selectionStart, selectionEnd));
            int selEnd = Math.min(pathFieldText.length(), Math.max(selectionStart, selectionEnd));
            if (selStart < 0) selStart = 0;
            if (selEnd > pathFieldText.length()) selEnd = pathFieldText.length();
            String beforeSelection = pathFieldText.substring(0, selStart);
            String selectedText = pathFieldText.substring(selStart, selEnd);
            int selX = pathFieldX + 5 + textRenderer.getWidth(beforeSelection);
            int selWidth = textRenderer.getWidth(selectedText);
            context.fill(selX, pathFieldY + 5, selX + selWidth, pathFieldY + 5 + textRenderer.fontHeight, 0x80FFFFFF);
        }

        String displayText = pathFieldText.toString();
        int displayWidth = pathFieldWidthDynamic - 10;
        int textWidth = textRenderer.getWidth(displayText);

        int cursorX = pathFieldX + 5 + textRenderer.getWidth(displayText.substring(0, Math.min(cursorPosition, displayText.length())));
        float cursorMargin = 50.0f;
        if (cursorX - pathScrollOffset > pathFieldX + pathFieldWidthDynamic - 5 - cursorMargin) {
            pathTargetScrollOffset = cursorX - (pathFieldX + pathFieldWidthDynamic - 5 - cursorMargin);
        } else if (cursorX - pathScrollOffset < pathFieldX + 5 + cursorMargin) {
            pathTargetScrollOffset = cursorX - (pathFieldX + 5 + cursorMargin);
        }

        pathTargetScrollOffset = Math.max(0, Math.min(pathTargetScrollOffset, textWidth - displayWidth));

        pathScrollOffset += (pathTargetScrollOffset - pathScrollOffset) * scrollSpeed;

        context.enableScissor(pathFieldX, pathFieldY, pathFieldX + pathFieldWidthDynamic, pathFieldY + pathFieldHeight);
        context.drawText(this.textRenderer, Text.literal(displayText), pathFieldX + 5 - (int) pathScrollOffset, pathFieldY + 5, textColor, true);

        CursorUtils.updateCursorOpacity();

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlinkTime >= 500) {
            showCursor = !showCursor;
            lastBlinkTime = currentTime;
        }
        if (pathFieldFocused && showCursor) {
            String beforeCursor = cursorPosition <= displayText.length() ? displayText.substring(0, cursorPosition) : displayText;
            int cursorPosX = pathFieldX + 5 + textRenderer.getWidth(beforeCursor) - (int) pathScrollOffset;
            int blendedCursorColor = CursorUtils.blendColor();
            context.fill(cursorPosX, pathFieldY + 5, cursorPosX + 2, pathFieldY + 5 + textRenderer.fontHeight, blendedCursorColor);
        }
        context.disableScissor();

        int backButtonX = this.width - 120;
        int backButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH;
        int bgBack = hoveredBack ? highlightColor : BgColor;
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack);
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, BorderColor);
        int twb = minecraftClient.textRenderer.getWidth("Back");
        int txb = backButtonX + (btnW - twb) / 2;
        int ty = backButtonY + (btnH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Back"), txb, ty, textColor, true);

        int closeButtonX = this.width - 60;
        int closeButtonY = 5;
        int closeBtnW = 50;
        int closeBtnH = 20;
        boolean hoveredClose = mouseX >= closeButtonX && mouseX <= closeButtonX + closeBtnW && mouseY >= closeButtonY && mouseY <= closeButtonY + closeBtnH;
        int bgClose = hoveredClose ? highlightColor : BgColor;
        context.fill(closeButtonX, closeButtonY, closeButtonX + closeBtnW, closeButtonY + closeBtnH, bgClose);
        drawInnerBorder(context, closeButtonX, closeButtonY, closeBtnW, closeBtnH, BorderColor);
        int tcw = minecraftClient.textRenderer.getWidth("Close");
        int tcx = closeButtonX + (closeBtnW - tcw) / 2;
        int tcy = closeButtonY + (btnH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Close"), tcx, tcy, textColor, true);

        if (loading) {
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
        int visibleEntries = explorerHeight / (entryHeight + gap);
        int startIndex = (int) Math.floor(smoothOffset / (entryHeight + gap)) - 1;
        if (startIndex < 0) startIndex = 0;
        int endIndex = startIndex + visibleEntries + 2;
        if (endIndex > entriesToRender.size()) endIndex = entriesToRender.size();

        context.enableScissor(explorerX, explorerY, explorerX + explorerWidth, explorerY + explorerHeight);

        for (int entryIndex = startIndex; entryIndex < endIndex; entryIndex++) {
            EntryData entry = entriesToRender.get(entryIndex);
            int entryY = explorerY + (entryIndex * (entryHeight + gap)) - (int) smoothOffset;
            boolean hovered = mouseX >= explorerX && mouseX <= explorerX + explorerWidth && mouseY >= entryY && mouseY < entryY + entryHeight;
            boolean isSelected = selectedPaths.contains(entry.path);
            boolean isFavorite;
            synchronized (favoritePathsLock) {
                isFavorite = favoritePaths.contains(entry.path);
            }
            int bg = isSelected ? (isFavorite ? favorateBg : elementSelectedBg) : (hovered ? highlightColor : elementBg);
            int borderWithOpacity = isFavorite ? favorateBorder : (isSelected ? elementSelectedBorder : (hovered ? elementBorderHover : elementBorder));
            int textWithOpacity = textColor;
            context.fill(explorerX, entryY, explorerX + explorerWidth, entryY + entryHeight, bg);
            drawInnerBorder(context, explorerX, entryY, explorerWidth, entryHeight, borderWithOpacity);

            context.fill(explorerX, entryY + entryHeight - 1, explorerX + explorerWidth, entryY + entryHeight, borderWithOpacity);

            BufferedImage icon = entry.isDirectory ? folderIcon : fileIcon;
            drawBufferedImage(context, icon, explorerX + 10, entryY + 5, 16, 16);

            String displayName = entry.path.getFileName().toString();
            context.drawText(this.textRenderer, Text.literal(displayName), explorerX + 30, entryY + 5, textWithOpacity, true);
            if (!serverInfo.isRemote) {
                context.drawText(this.textRenderer, Text.literal(entry.size), explorerX + 250, entryY + 5, textWithOpacity, true);
                context.drawText(this.textRenderer, Text.literal(entry.created), explorerX + 350, entryY + 5, textWithOpacity, true);
            }
        }

        context.disableScissor();

        if (smoothOffset > 0) {
            context.fillGradient(0, explorerY, this.width, explorerY + 10, 0x80000000, 0x00000000);
        }
        if (smoothOffset < Math.max(0, (entriesToRender.size() * (entryHeight + gap)) - explorerHeight)) {
            context.fillGradient(0, explorerY + explorerHeight - 10, this.width, explorerY + explorerHeight, 0x00000000, 0x80000000);
        }

        if (searchActive) {
            context.fill(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + searchBarHeight, 0xFF333333);
            drawInnerBorder(context, searchBarX, searchBarY, searchBarWidth, searchBarHeight, 0xFF555555);
            context.drawText(this.textRenderer, Text.literal(searchQuery.toString()), searchBarX + 5, searchBarY + 5, 0xFFFFFFFF, true);
        }

        updateNotifications(delta);
        renderNotifications(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (pathFieldFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (ctrl) {
                    deleteWord();
                } else {
                    if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                        int selStart = Math.min(selectionStart, selectionEnd);
                        int selEnd = Math.max(selectionStart, selectionEnd);
                        pathFieldText.delete(selStart, selEnd);
                        cursorPosition = selStart;
                        selectionStart = -1;
                        selectionEnd = -1;
                    } else {
                        if (cursorPosition > 0) {
                            pathFieldText.deleteCharAt(cursorPosition - 1);
                            cursorPosition--;
                        }
                    }
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pathFieldFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                    int selStart = Math.min(selectionStart, selectionEnd);
                    int selEnd = Math.max(selectionStart, selectionEnd);
                    pathFieldText.delete(selStart, selEnd);
                    cursorPosition = selStart;
                    selectionStart = -1;
                    selectionEnd = -1;
                } else {
                    if (cursorPosition < pathFieldText.length()) {
                        pathFieldText.deleteCharAt(cursorPosition);
                    }
                }
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                selectionStart = 0;
                selectionEnd = pathFieldText.length();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                if (cursorPosition > 0) {
                    cursorPosition--;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (cursorPosition < pathFieldText.length()) {
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
        }
        if (keyCode == this.minecraftClient.options.backKey.getDefaultKey().getCode() && keyCode != GLFW.GLFW_KEY_S) {
            navigateUp();
            return true;
        }
        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_C && !serverInfo.isRemote) {
                fileManager.copySelected(selectedPaths);
                showNotification("Copied to clipboard", Notification.Type.INFO);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X && !serverInfo.isRemote) {
                fileManager.cutSelected(selectedPaths);
                showNotification("Cut to clipboard", Notification.Type.INFO);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && !serverInfo.isRemote) {
                if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                    int selStart = Math.min(selectionStart, selectionEnd);
                    int selEnd = Math.max(selectionStart, selectionEnd);
                    pathFieldText.delete(selStart, selEnd);
                    cursorPosition = selStart;
                    selectionStart = -1;
                    selectionEnd = -1;
                }
                pasteClipboard();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Z && !serverInfo.isRemote) {
                fileManager.undo(currentPath);
                showNotification("Undo action", Notification.Type.INFO);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_F) {
                searchActive = true;
                searchQuery.setLength(0);
                searchBarY = this.height - searchBarHeight - 10;
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
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            fileManager.deleteSelected(selectedPaths, currentPath);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (pathFieldFocused) {
            if (chr == '\b' || chr == '\n') {
                return false;
            }
            if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                int selStart = Math.min(selectionStart, selectionEnd);
                int selEnd = Math.max(selectionStart, selectionEnd);
                pathFieldText.delete(selStart, selEnd);
                cursorPosition = selStart;
                selectionStart = -1;
                selectionEnd = -1;
            }
            pathFieldText.insert(cursorPosition, chr);
            cursorPosition++;
            return true;
        }
        if (searchActive) {
            if (chr == '\b' || chr == '\n') {
                return false;
            }
            searchQuery.append(chr);
            filterFileEntries();
            return true;
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
        boolean handled = false;
        if (searchActive) {
            if (mouseX >= searchBarX && mouseX <= searchBarX + searchBarWidth &&
                    mouseY >= searchBarY && mouseY <= searchBarY + searchBarHeight) {
                handled = true;
            }
        }
        if (!handled) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                long currentTime = System.currentTimeMillis();
                boolean isDoubleClick = false;
                if (lastClickedIndex != -1 && (currentTime - lastClickTime) < DOUBLE_CLICK_INTERVAL) {
                    isDoubleClick = true;
                }
                lastClickTime = currentTime;
                int explorerX = 5;
                int explorerY = 60;
                int explorerWidth = this.width - 10;
                int explorerHeight = this.height - 70;
                int backButtonX = this.width - 120;
                int backButtonY = 5;
                int btnW = 50;
                int btnH = 20;
                int closeButtonX = this.width - 60;
                int closeButtonY = 5;
                int closeBtnW = 50;
                int closeBtnH = 20;
                int gap = 1;

                if (mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH) {
                    navigateUp();
                    return true;
                }
                if (mouseX >= closeButtonX && mouseX <= closeButtonX + closeBtnW && mouseY >= closeButtonY && mouseY <= closeButtonY + btnH) {
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
                        pathFieldFocused = false;
                        EntryData entryData = entriesToRender.get(clickedIndex);
                        Path selectedPath = entryData.path;
                        if (isDoubleClick && lastClickedIndex == clickedIndex) {
                            if (entryData.isDirectory) {
                                loadDirectory(selectedPath);
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
                                    for (int i = start; i <= end; i++) {
                                        if (i >= 0 && i < entriesToRender.size()) {
                                            Path path = entriesToRender.get(i).path;
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

                int titleBarHeight = 30;
                int pathFieldWidthDynamic = Math.min(basePathFieldWidth + textRenderer.getWidth(pathFieldText.toString()), maxPathFieldWidth);
                pathFieldWidthDynamic = Math.max(basePathFieldWidth, pathFieldWidthDynamic);
                int pathFieldX = (this.width - pathFieldWidthDynamic) / 2;
                int pathFieldY = 5;
                int pathFieldHeight = titleBarHeight - 10;
                if (mouseX >= pathFieldX && mouseX <= pathFieldX + pathFieldWidthDynamic &&
                        mouseY >= pathFieldY && mouseY <= pathFieldY + pathFieldHeight) {
                    pathFieldFocused = true;
                    cursorPosition = pathFieldText.length();
                    selectionStart = -1;
                    selectionEnd = -1;
                    return true;
                } else {
                    pathFieldFocused = false;
                }

                if (mouseX >= explorerX && mouseX <= explorerX + explorerWidth && mouseY >= explorerY && mouseY <= explorerY + explorerHeight) {
                    int relativeY = (int) mouseY - explorerY + (int) smoothOffset;
                    int clickedIndex = relativeY / (entryHeight + gap);
                    List<EntryData> entriesToRender;
                    synchronized (fileEntriesLock) {
                        entriesToRender = new ArrayList<>(fileEntries);
                    }
                    if (clickedIndex >= 0 && clickedIndex < entriesToRender.size()) {
                        EntryData entryData = entriesToRender.get(clickedIndex);
                        Path selectedPath = entryData.path;
                        if (isDoubleClick && lastClickedIndex == clickedIndex) {
                            if (entryData.isDirectory) {
                                loadDirectory(selectedPath);
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
                                    for (int i = start; i <= end; i++) {
                                        if (i >= 0 && i < entriesToRender.size()) {
                                            Path path = entriesToRender.get(i).path;
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
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
                navigateUp();
                return true;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
                navigateBack();
                return true;
            }
        }
        if (!handled && searchActive) {
            searchActive = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void navigateBack() {
        if (!history.isEmpty()) {
            Path previousPath = history.pop();
            forwardHistory.push(currentPath);
            loadDirectory(previousPath, false, false);
        }
    }

    private void navigateUp() {
        if (serverInfo.isRemote) {
            if (currentPath == null || currentPath.toString().equals("/")) {
                minecraftClient.setScreen(parent);
            } else {
                Path parentPath = currentPath.getParent();
                if (parentPath == null || parentPath.toString().isEmpty()) {
                    currentPath = Paths.get("/");
                    loadDirectory(currentPath);
                } else {
                    loadDirectory(parentPath);
                }
            }
        } else {
            Path parentPath = currentPath.getParent();
            if (parentPath != null && parentPath.startsWith(Paths.get(serverInfo.path).toAbsolutePath().normalize())) {
                loadDirectory(parentPath);
            } else {
                minecraftClient.setScreen(parent);
            }
        }
    }

    private void deleteWord() {
        if (cursorPosition == 0) return;
        int deleteStart = cursorPosition;
        while (deleteStart > 0) {
            char c = pathFieldText.charAt(deleteStart - 1);
            if (c == '/' || c == '\\') break;
            deleteStart--;
        }
        if (deleteStart < 0 || deleteStart > pathFieldText.length()) {
            deleteStart = 0;
        }
        pathFieldText.delete(deleteStart, cursorPosition);
        cursorPosition = deleteStart;
    }

    private void pasteClipboard() {
        try {
            String clipboard = minecraftClient.keyboard.getClipboard();
            if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                int selStart = Math.min(selectionStart, selectionEnd);
                int selEnd = Math.max(selectionStart, selectionEnd);
                pathFieldText.delete(selStart, selEnd);
                cursorPosition = selStart;
                selectionStart = -1;
                selectionEnd = -1;
            }
            pathFieldText.insert(cursorPosition, clipboard);
            cursorPosition += clipboard.length();
        } catch (Exception e) {
            showNotification("Failed to paste from clipboard.", Notification.Type.ERROR);
        }
    }

    private void executePath() {
        Path newPath = Paths.get(pathFieldText.toString()).toAbsolutePath().normalize();
        if (Files.exists(newPath) && Files.isDirectory(newPath)) {
            loadDirectory(newPath);
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
        if (pathFieldFocused) {
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

    public void loadDirectory(Path dir) {
        loadDirectory(dir, true, false);
    }

    private void loadDirectory(Path dir, boolean addToHistory, boolean forceReload) {
        if (loading) return;
        if (addToHistory && currentPath != null && !currentPath.equals(dir)) {
            history.push(currentPath);
            forwardHistory.clear();
            targetOffset = 0;
        }
        if (addToHistory) {
            targetOffset = 0;
        }
        if (searchActive) {
            searchActive = false;
            searchQuery.setLength(0);
        }
        currentPath = dir;
        pathFieldText.setLength(0);
        pathFieldText.append(currentPath.toString());
        cursorPosition = pathFieldText.length();
        String key = dir.toString();
        if (!serverInfo.isRemote) {
            if (!forceReload && remoteCache.containsKey(key)) {
                synchronized (fileEntriesLock) {
                    fileEntries = remoteCache.get(key);
                }
                return;
            }
        }
        loading = true;
        directoryLoader.submit(() -> {
            try {
                List<EntryData> temp;
                if (serverInfo.isRemote) {
                    ensureRemoteConnected();
                    temp = loadRemoteDirectory(dir, forceReload);
                } else {
                    temp = loadLocalDirectory(dir);
                }
                synchronized (fileEntriesLock) {
                    fileEntries = temp;
                }
                if (!serverInfo.isRemote) {
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
        if (!forceReload && remoteCache.containsKey(remotePath)) {
            return remoteCache.get(remotePath);
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
        remoteCache.put(remotePath, temp);
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
        if (serverInfo.remoteSSHManager == null) {
            serverInfo.remoteSSHManager = new SSHManager(serverInfo);
            try {
                serverInfo.remoteSSHManager.connectToRemoteHost(
                        serverInfo.remoteHost.getUser(),
                        serverInfo.remoteHost.ip,
                        serverInfo.remoteHost.port,
                        serverInfo.remoteHost.password
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
        if (searchQuery.length() == 0) {
            loadDirectory(currentPath, false, false);
            return;
        }
        String query = searchQuery.toString().toLowerCase();
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
            context.drawText(this.textRenderer, Text.literal(message), (int) x + padding, (int) y + padding, blendColor(0xFFFFFFFF, currentOpacity), true);
        }

        private int blendColor(int color, float opacity) {
            int a = (int) ((color >> 24 & 0xFF) * opacity);
            int r = (color >> 16 & 0xFF);
            int g = (color >> 8 & 0xFF);
            int b = (color & 0xFF);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
            context.fill(x, y, x + w, y + 1, c);
            context.fill(x, y + h - 1, x + w, y + h, c);
            context.fill(x, y, x + 1, y + h, c);
            context.fill(x + w - 1, y, x + w, y + h, c);
        }
    }
}
