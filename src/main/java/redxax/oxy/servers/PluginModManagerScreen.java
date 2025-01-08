package redxax.oxy.servers;

import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.Config;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static redxax.oxy.ImageUtil.drawBufferedImage;
import static redxax.oxy.ImageUtil.loadResourceIcon;
import static redxax.oxy.ImageUtil.loadSpriteSheet;
import static redxax.oxy.Render.*;

public class PluginModManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;

    private final List<ModrinthResource> resources = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<ModrinthResource>> resourceCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> iconImages = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> scaledIcons = Collections.synchronizedMap(new LinkedHashMap<String, BufferedImage>() {
        private static final int MAX_ENTRIES = 100;
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_ENTRIES;
        }
    });
    private final Map<String, Boolean> installingMrPack = new ConcurrentHashMap<>();
    private final Map<String, Boolean> installingResource = new ConcurrentHashMap<>();
    private final Map<String, String> installButtonTexts = new ConcurrentHashMap<>();

    private BufferedImage installIcon;
    private BufferedImage installingIcon;
    private BufferedImage installedIcon;
    private BufferedImage loadingAnim;
    private List<BufferedImage> loadingFrames = new ArrayList<>();
    private int currentLoadingFrame = 0;
    private long lastFrameTime = 0;

    private final ExecutorService imageLoader = Executors.newFixedThreadPool(4);
    private final BufferedImage placeholderIcon = createPlaceholderIcon();

    private float smoothOffset = 0;
    private float targetOffset = 0;
    private float scrollSpeed = 0.2f;
    private int entryHeight = 40;
    private final int gapBetweenEntries = 2;
    private int selectedIndex = -1;

    private volatile boolean isLoading = false;
    private volatile boolean isLoadingMore = false;
    private boolean hasMore = false;
    private int loadedCount = 0;
    private String currentSearch = "";

    private boolean fieldFocused = false;
    private StringBuilder fieldText = new StringBuilder();
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private long lastBlinkTime = 0;
    private boolean showCursor = true;

    private float pathScrollOffset = 0;
    private float pathTargetScrollOffset = 0;

    private TextRenderer textRenderer;
    private boolean searching = false;

    private enum TabMode { MODRINTH, SPIGOT, HANGAR }

    private static class Tab {
        TabMode mode;
        String name;
        float scrollOffset;

        Tab(TabMode mode, String name) {
            this.mode = mode;
            this.name = name;
        }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private final int TAB_HEIGHT = 18;
    private final int TAB_PADDING = 5;
    private final int TAB_GAP = 5;
    private final int PLUS_TAB_WIDTH = 18;

    public PluginModManagerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal(info.isModServer() ? "Remotely - Mods Browser" : (info.isPluginServer() ? "Remotely - Plugins Browser" : "Remotely - Modpacks Browser")));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
    }

    @Override
    protected void init() {
        super.init();
        this.textRenderer = this.minecraftClient.textRenderer;
        tabs.clear();
        tabs.add(new Tab(TabMode.MODRINTH, "Modrinth"));
        if (serverInfo.isPluginServer()) {
            tabs.add(new Tab(TabMode.SPIGOT, "Spigot (Soon)"));
            tabs.add(new Tab(TabMode.HANGAR, "Hangar (Soon)"));
        }
        fieldText.setLength(0);
        fieldText.append("");
        cursorPosition = 0;
        try {
            installIcon = loadResourceIcon("/assets/remotely/icons/download.png");
            installingIcon = loadResourceIcon("/assets/remotely/icons/loading.png");
            installedIcon = loadResourceIcon("/assets/remotely/icons/installed.png");
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
        loadResourcesAsync("", true);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = false;
        int titleBarHeight = 30;
        int tabBarY = titleBarHeight + 5;
        int tabBarHeight = TAB_HEIGHT;
        int tabX = 5;
        int tabY = tabBarY;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = this.textRenderer.getWidth(tab.name) + 2 * TAB_PADDING;
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabBarHeight) {
                currentTabIndex = i;
                handled = true;
                break;
            }
            tabX += tabWidth + TAB_GAP;
        }
        int plusTabX = tabX;
        if (!handled) {
            if (mouseX >= plusTabX && mouseX <= plusTabX + PLUS_TAB_WIDTH && mouseY >= tabY && mouseY <= tabY + tabBarHeight) {
                handled = true;
            }
        }
        if (!handled) {
            int closeButtonX = this.width - buttonW - 10;
            int closeButtonY = 5;
            boolean hoveredClose = mouseX >= closeButtonX && mouseX <= closeButtonX + buttonW && mouseY >= closeButtonY && mouseY <= closeButtonY + buttonH;
            if (hoveredClose && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                minecraftClient.setScreen(parent);
                return true;
            }
            int titleBarBaseX = (this.width / 2) - 100;
            int textFieldHeight = 20;
            if (mouseX >= titleBarBaseX && mouseX <= titleBarBaseX + 200 && mouseY >= 5 && mouseY <= 5 + textFieldHeight) {
                fieldFocused = true;
                return true;
            } else {
                fieldFocused = false;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int contentY = tabBarY + tabBarHeight + 30;
                int contentHeight = this.height - contentY - 10;
                int contentX = 5;
                int contentWidth = this.width - 10;
                if (mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= contentY && mouseY <= contentY + contentHeight) {
                    int relativeY = (int) mouseY - contentY + (int) smoothOffset;
                    int index = relativeY / (entryHeight + gapBetweenEntries);
                    if (index >= 0 && index < resources.size()) {
                        selectedIndex = index;
                        int iconX = contentX + 5;
                        int iconY = contentY + (index * (entryHeight + gapBetweenEntries)) - (int) smoothOffset + (entryHeight - 30) / 2;
                        int iconSize = 30;
                        if (mouseX >= iconX && mouseX <= iconX + iconSize && mouseY >= iconY && mouseY <= iconY + iconSize) {
                            ModrinthResource selected = resources.get(index);
                            if (!installButtonTexts.containsKey(selected.slug)) {
                                installButtonTexts.put(selected.slug, "Install");
                            }
                            if (!installButtonTexts.get(selected.slug).equalsIgnoreCase("Installed")) {
                                if (selected.fileName.toLowerCase(Locale.ROOT).endsWith(".mrpack") && Objects.equals(serverInfo.path, "modpack")) {
                                    if (!installingMrPack.containsKey(selected.slug) || !installingMrPack.get(selected.slug)) {
                                        installingMrPack.put(selected.slug, true);
                                        installButtonTexts.put(selected.slug, "Installing");
                                        installMrPack(selected);
                                    }
                                } else {
                                    if (!installingResource.containsKey(selected.slug) || !installingResource.get(selected.slug)) {
                                        installingResource.put(selected.slug, true);
                                        installButtonTexts.put(selected.slug, "Installing");
                                        fetchAndInstallResource(selected);
                                    }
                                }
                            }
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (fieldFocused) {
            if (chr == '\b' || chr == '\n') {
                return false;
            }
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
            searching = true;
            currentSearch = fieldText.toString();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (fieldFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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
                searching = true;
                currentSearch = fieldText.toString();
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
                searching = true;
                currentSearch = fieldText.toString();
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
                searching = true;
                currentSearch = fieldText.toString();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                resources.clear();
                loadResourcesAsync(fieldText.toString(), true);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraftClient.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        targetOffset -= verticalAmount * entryHeight * 2;
        targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, resources.size() * (entryHeight + gapBetweenEntries) - (this.height - 70))));
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
        context.drawText(this.textRenderer, Text.literal(this.getTitle().getString()), 10, 10, textColor, Config.shadow);

        int tabBarY = titleBarHeight + 5;
        int tabBarHeight = TAB_HEIGHT;
        int tabX = 5;
        int tabY = tabBarY;

        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = textRenderer.getWidth(tab.name) + 2 * TAB_PADDING;
            boolean isActive = (i == currentTabIndex);
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabBarHeight;
            int bgColor = isActive ? elementSelectedBg : (isHovered ? highlightColor : elementBg);
            context.fill(tabX, tabY, tabX + tabWidth, tabY + tabBarHeight, bgColor);
            drawInnerBorder(context, tabX, tabY, tabWidth, tabBarHeight, isActive ? elementSelectedBorder : (isHovered ? elementBorderHover : elementBorder));
            int textX = tabX + TAB_PADDING;
            int textY = tabY + (tabBarHeight - textRenderer.fontHeight) / 2;
            context.drawText(textRenderer, Text.literal(tab.name), textX, textY, isHovered ? elementSelectedBorder : textColor, Config.shadow);
            context.fill(tabX, tabY + tabBarHeight, tabX + tabWidth, tabY + tabBarHeight + 2, isActive ? 0xFF0b0b0b : 0xFF000000);
            tabX += tabWidth + TAB_GAP;
        }

        int textFieldHeight = 20;
        int textFieldX = (this.width / 2) - 100;
        int textFieldY = 5;
        int textFieldW = 200;
        int textFieldH = textFieldHeight;
        int fieldColor = fieldFocused ? elementSelectedBg : elementBg;
        context.fill(textFieldX, textFieldY, textFieldX + textFieldW, textFieldY + textFieldH, fieldColor);
        drawInnerBorder(context, textFieldX, textFieldY, textFieldW, textFieldH, fieldFocused ? elementSelectedBorder : elementBorder);

        if (selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
            int selStart = Math.max(0, Math.min(selectionStart, selectionEnd));
            int selEnd = Math.min(fieldText.length(), Math.max(selectionStart, selectionEnd));
            if (selStart < 0) selStart = 0;
            if (selEnd > fieldText.length()) selEnd = fieldText.length();
            String beforeSelection = fieldText.substring(0, selStart);
            String selectedText = fieldText.substring(selStart, selEnd);
            int selX = textFieldX + 5 + textRenderer.getWidth(beforeSelection);
            int selWidth = textRenderer.getWidth(selectedText);
            context.fill(selX, textFieldY + 4, selX + selWidth, textFieldY + 4 + textRenderer.fontHeight, 0x80FFFFFF);
        }

        String displayText = fieldText.toString();
        int textWidth = textRenderer.getWidth(displayText);
        int cursorX = textFieldX + 5 + textRenderer.getWidth(displayText.substring(0, Math.min(cursorPosition, displayText.length())));
        float cursorMargin = 50.0f;
        if (cursorX - pathScrollOffset > textFieldX + textFieldW - 5 - cursorMargin) {
            pathTargetScrollOffset = cursorX - (textFieldX + textFieldW - 5 - cursorMargin);
        } else if (cursorX - pathScrollOffset < textFieldX + 5 + cursorMargin) {
            pathTargetScrollOffset = cursorX - (textFieldX + 5 + cursorMargin);
        }
        pathTargetScrollOffset = Math.max(0, Math.min(pathTargetScrollOffset, textWidth - textFieldW + 10));
        pathScrollOffset += (pathTargetScrollOffset - pathScrollOffset) * scrollSpeed;

        context.enableScissor(textFieldX, textFieldY, textFieldX + textFieldW, textFieldY + textFieldH);
        context.drawText(textRenderer, Text.literal(displayText), textFieldX + 5 - (int) pathScrollOffset, textFieldY + 5, textColor, Config.shadow);
        if (fieldFocused && showCursor) {
            String beforeCursor = cursorPosition <= displayText.length() ? displayText.substring(0, cursorPosition) : displayText;
            int curX = textFieldX + 5 + textRenderer.getWidth(beforeCursor) - (int) pathScrollOffset;
            context.fill(curX, textFieldY + 5, curX + 1, textFieldY + 5 + textRenderer.fontHeight, 0xFFFFFFFF);
        }
        context.disableScissor();

        int closeButtonX = this.width - buttonW - 10;
        int closeButtonY = 5;
        boolean hoveredClose = mouseX >= closeButtonX && mouseX <= closeButtonX + buttonW && mouseY >= closeButtonY && mouseY <= closeButtonY + buttonH;
        drawHeaderButton(context, closeButtonX, closeButtonY, "Close", minecraftClient, hoveredClose, false, textColor, redVeryBright);

        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;
        int contentY = tabBarY + tabBarHeight + 30;
        int contentHeight = this.height - contentY - 10;
        int contentX = 5;
        int contentWidth = this.width - 10;

        context.fill(contentX, contentY - 25, contentX + contentWidth, contentY, BgColor);
        drawInnerBorder(context, contentX, contentY - 25, contentWidth, 25, borderColor);
        context.drawText(textRenderer, Text.literal("Name"), contentX + 10, contentY - 18, textColor, Config.shadow);

        context.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        if (isLoading && resources.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFrameTime >= 40) {
                currentLoadingFrame = (currentLoadingFrame + 1) % loadingFrames.size();
                lastFrameTime = currentTime;
            }
            BufferedImage currentFrame = loadingFrames.get(currentLoadingFrame);
            int scale = 8;
            int imgWidth = currentFrame.getWidth() * scale;
            int imgHeight = currentFrame.getHeight() * scale;
            int centerX = (this.width - imgWidth) / 2;
            int centerY = (this.height - imgHeight) / 2;
            drawBufferedImage(context, currentFrame, centerX, centerY, imgWidth, imgHeight);
            context.disableScissor();
            return;
        }

        int startIndex = (int) Math.floor(smoothOffset / (entryHeight + gapBetweenEntries));
        int visibleEntries = contentHeight / (entryHeight + gapBetweenEntries);
        int endIndex = Math.min(startIndex + visibleEntries + 2, resources.size());

        for (int i = startIndex; i < endIndex; i++) {
            ModrinthResource resource = resources.get(i);
            int y = contentY + (i * (entryHeight + gapBetweenEntries)) - (int) smoothOffset;
            boolean hovered = mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= y && mouseY < y + entryHeight;
            boolean isSelected = (i == selectedIndex);
            int bg = isSelected ? elementSelectedBg : (hovered ? highlightColor : elementBg);
            int borderColorFinal = isSelected ? elementSelectedBorder : (hovered ? elementBorderHover : elementBorder);
            context.fill(contentX, y, contentX + contentWidth, y + entryHeight, bg);
            drawInnerBorder(context, contentX, y, contentWidth, entryHeight, borderColorFinal);

            context.fill(contentX, y + entryHeight + 1, contentX + contentWidth, y + entryHeight, 0xFF000000);

            BufferedImage scaledImage = resource.iconUrl.isEmpty() ? placeholderIcon : scaledIcons.getOrDefault(resource.iconUrl, placeholderIcon);
            drawBufferedImage(context, scaledImage, contentX + 5, y + (entryHeight - 30) / 2, 30, 30);

            String resourceName = resource.name;
            context.drawText(textRenderer, Text.literal(resourceName), contentX + 40, y + 5, elementSelectedBorder, Config.shadow);

            String resourceDesc = resource.description;
            int descMaxWidth = contentWidth - 50;
            if (textRenderer.getWidth(resourceDesc) > descMaxWidth) {
                while (textRenderer.getWidth(resourceDesc + "...") > descMaxWidth && resourceDesc.length() > 0) {
                    resourceDesc = resourceDesc.substring(0, resourceDesc.length() - 1);
                }
                resourceDesc += "...";
            }
            context.drawText(textRenderer, Text.literal(resourceDesc), contentX + 40, y + 16, textColor, Config.shadow);

            String downloadsAndVersion = formatDownloads(resource.downloads) + " | " + resource.version  + " | " + resource.followers + " Followers";
            context.drawText(textRenderer, Text.literal(downloadsAndVersion), contentX + 40, y + 30, dimTextColor, Config.shadow);

            int buttonX = contentX + 5;
            int buttonY = y + (entryHeight - 30) / 2;
            int buttonSize = 30;
            boolean isHoveringInstall = mouseX >= buttonX && mouseX <= buttonX + buttonSize && mouseY >= buttonY && mouseY <= buttonY + buttonSize;
            if (isHoveringInstall) {
                context.fill(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, 0x80000000);
                BufferedImage buttonIcon = installIcon;
                String status = installButtonTexts.getOrDefault(resource.slug, "Install");
                if (status.equals("Installing")) {
                    buttonIcon = installingIcon;
                } else if (status.equals("Installed")) {
                    buttonIcon = installedIcon;
                }
                if (buttonIcon != null) {
                    drawBufferedImage(context, buttonIcon, buttonX, buttonY, buttonSize, buttonSize);
                }
            }
        }
        context.disableScissor();

        if (smoothOffset > 0) {
            context.fillGradient(contentX, contentY, contentX + contentWidth, contentY + 10, 0x80000000, 0x00000000);
        }
        int maxScroll = Math.max(0, resources.size() * (entryHeight + gapBetweenEntries) - contentHeight);
        if (smoothOffset < maxScroll) {
            context.fillGradient(contentX, contentY + contentHeight - 10, contentX + contentWidth, contentY + contentHeight, 0x00000000, 0x80000000);
        }

        loadMoreIfNeeded();
    }

    private static String formatDownloads(int n) {
        if (n >= 1_000_000) {
            return String.format("%.1fM", n / 1_000_000.0);
        } else if (n >= 1000) {
            return String.format("%.1fK", n / 1000.0);
        } else {
            return Integer.toString(n);
        }
    }

    private void loadMoreIfNeeded() {
        if (!hasMore || isLoadingMore || isLoading) return;
        if (smoothOffset + (this.height - 70) >= resources.size() * (entryHeight + gapBetweenEntries) - (entryHeight + gapBetweenEntries)) {
            isLoadingMore = true;
            loadedCount += 30;
            loadResourcesAsync(currentSearch, false);
        }
    }

    private void loadResourcesAsync(String query, boolean reset) {
        if (reset) {
            loadedCount = 0;
            resources.clear();
            smoothOffset = 0;
            targetOffset = 0;
        }
        String cacheKey = query + "_" + loadedCount + "_" + currentTabIndex;
        if (resourceCache.containsKey(cacheKey)) {
            synchronized (resources) {
                resources.addAll(resourceCache.get(cacheKey));
            }
            return;
        }
        isLoading = true;
        if (reset) hasMore = true;
        CompletableFuture<List<ModrinthResource>> searchFuture;
        String serverVersion = serverInfo.getVersion();
        int limit = 30;

        switch (tabs.get(currentTabIndex).mode) {
            case MODRINTH -> {
                if (serverInfo.isModServer()) {
                    searchFuture = ModrinthAPI.searchMods(query, serverVersion, limit, loadedCount, serverInfo.type);
                } else if (serverInfo.isPluginServer()) {
                    searchFuture = ModrinthAPI.searchPlugins(query, serverVersion, limit, loadedCount, serverInfo.type);
                } else {
                    searchFuture = ModrinthAPI.searchModpacks(query, serverVersion, limit, loadedCount);
                }
            }
            case SPIGOT -> {
                searchFuture = SpigetAPI.searchPlugins(query, limit, loadedCount / limit).thenApply(spigetList -> {
                    List<ModrinthResource> converted = new ArrayList<>();
                    for (SpigetResource sp : spigetList) {
                        if (serverVersion != null && !serverVersion.isEmpty()) {
                            if (!sp.tag.toLowerCase(Locale.ROOT).contains(serverVersion.toLowerCase(Locale.ROOT))) {
                                continue;
                            }
                        }
                        ModrinthResource r = new ModrinthResource(
                                sp.name,
                                sp.tag,
                                sp.description,
                                sp.name + ".jar",
                                sp.iconUrl,
                                sp.downloads,
                                0,
                                sp.name,
                                new ArrayList<>(),
                                "",
                                ""
                        );
                        converted.add(r);
                    }
                    return converted;
                });
            }
            case HANGAR -> {
                searchFuture = HangarAPI.searchPlugins(query, limit, loadedCount).thenApply(hangarList -> {
                    List<ModrinthResource> converted = new ArrayList<>();
                    for (HangarResource hr : hangarList) {
                        if (serverVersion != null && !serverVersion.isEmpty()) {
                            if (!hr.description.toLowerCase(Locale.ROOT).contains(serverVersion.toLowerCase(Locale.ROOT))) {
                                continue;
                            }
                        }
                        ModrinthResource r = new ModrinthResource(
                                hr.name,
                                "Unknown",
                                hr.description,
                                hr.name + ".jar",
                                "",
                                hr.downloads,
                                0,
                                hr.name,
                                new ArrayList<>(),
                                "",
                                ""
                        );
                        converted.add(r);
                    }
                    return converted;
                });
            }
            default -> {
                searchFuture = CompletableFuture.completedFuture(new ArrayList<>());
            }
        }

        searchFuture.thenAccept(fetched -> {
            if (fetched.size() < 30) hasMore = false;
            Set<String> seenSlugs = ConcurrentHashMap.newKeySet();
            List<ModrinthResource> uniqueResources = new ArrayList<>();
            for (ModrinthResource r : fetched) {
                if (seenSlugs.add(r.slug)) {
                    uniqueResources.add(r);
                }
            }
            synchronized (resources) {
                resources.addAll(uniqueResources);
            }
            resourceCache.put(cacheKey, new ArrayList<>(uniqueResources));
            isLoading = false;
            isLoadingMore = false;
            uniqueResources.forEach(resource -> {
                if (!resource.iconUrl.isEmpty() && !iconImages.containsKey(resource.iconUrl)) {
                    imageLoader.submit(() -> {
                        try (InputStream inputStream = new URL(resource.iconUrl).openStream()) {
                            BufferedImage bufferedImage = loadImage(inputStream, resource.iconUrl);
                            if (bufferedImage != null) {
                                iconImages.put(resource.iconUrl, bufferedImage);
                                BufferedImage scaled = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D g2d = scaled.createGraphics();
                                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                g2d.drawImage(bufferedImage, 0, 0, 30, 30, null);
                                g2d.dispose();
                                synchronized (scaledIcons) {
                                    scaledIcons.put(resource.iconUrl, scaled);
                                }
                            }
                        } catch (Exception e) {
                            scaledIcons.put(resource.iconUrl, placeholderIcon);
                        }
                    });
                }
            });
        }).exceptionally(e -> {
            e.printStackTrace();
            isLoading = false;
            isLoadingMore = false;
            return null;
        });
    }

    private BufferedImage loadImage(InputStream inputStream, String url) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = inputStream.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        inputStream.close();
        return ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
    }

    private BufferedImage createPlaceholderIcon() {
        BufferedImage img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(new Color(0xFF555555, true));
        g2d.fillRect(0, 0, 30, 30);
        g2d.dispose();
        return img;
    }

    private void installMrPack(ModrinthResource resource) {
        new Thread(() -> {
            try {
                String exePath = "C:\\remotely\\mrpack-install-windows.exe";
                String serverDir = "C:\\remotely\\servers\\" + resource.name;
                Path exe = Path.of(exePath);
                Path serverPath = Path.of(serverDir);
                if (!Files.exists(serverPath)) Files.createDirectories(serverPath);
                if (!Files.exists(exe)) {
                    try {
                        URL url = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-windows.exe");
                        try (InputStream input = url.openStream()) {
                            Files.copy(input, exe, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to download mrpack-install: " + e.getMessage());
                        return;
                    }
                }
                ProcessBuilder pb = new ProcessBuilder(exePath, resource.projectId, resource.version, "--server-dir", serverDir, "--server-file", "server.jar");
                pb.directory(serverPath.toFile());
                Process proc = pb.start();
                ExecutorService executor = Executors.newFixedThreadPool(2);
                executor.submit(() -> {
                    try (InputStream is = proc.getInputStream()) {
                        is.transferTo(System.out);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                executor.submit(() -> {
                    try (InputStream is = proc.getErrorStream()) {
                        is.transferTo(System.err);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                proc.waitFor();
                executor.shutdown();
                if (proc.exitValue() != 0) {
                    System.err.println("mrpack-install failed. Exit code: " + proc.exitValue());
                }
                installingMrPack.put(resource.slug, false);
                installButtonTexts.put(resource.slug, "Installed");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchAndInstallResource(ModrinthResource resource) {
        Thread t = new Thread(() -> {
            try {
                String versionID = (resource.versionId != null && !resource.versionId.isEmpty()) ? resource.versionId : resource.version;
                String downloadUrl = fetchDownloadUrl(versionID);
                if (downloadUrl.isEmpty()) {
                    minecraftClient.execute(() -> {
                        installingResource.put(resource.slug, false);
                        installButtonTexts.put(resource.slug, "Install");
                    });
                    return;
                }
                Path dest;
                String baseName = stripExtension(resource.fileName);
                String extension = "";
                if (serverInfo.isModServer() || serverInfo.isPluginServer()) {
                    extension = ".jar";
                }
                if (serverInfo.isModServer()) {
                    dest = Path.of(serverInfo.path, "mods", baseName + extension);
                } else if (serverInfo.isPluginServer()) {
                    dest = Path.of(serverInfo.path, "plugins", baseName + extension);
                } else {
                    dest = Path.of(serverInfo.path, "unknown", resource.fileName);
                }
                Files.createDirectories(dest.getParent());
                HttpClient httpClient = HttpClient.newBuilder().executor(imageLoader).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("User-Agent", "Remotely")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    Files.copy(response.body(), dest, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    System.err.println("Failed to fetch resource: " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch and install resource: " + e.getMessage());
                e.printStackTrace();
            }
            minecraftClient.execute(() -> {
                installingResource.put(resource.slug, false);
                installButtonTexts.put(resource.slug, "Installed");
            });
        });
        t.start();
    }

    private String fetchDownloadUrl(String versionID) {
        try {
            URI uri = URI.create("https://api.modrinth.com/v2/version/" + versionID);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "Remotely")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var version = JsonParser.parseString(response.body()).getAsJsonObject();
                var files = version.getAsJsonArray("files");
                if (!files.isEmpty()) {
                    var file = files.get(0).getAsJsonObject();
                    return file.get("url").getAsString();
                }
            } else {
                System.err.println("Failed to fetch download URL: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch download URL: " + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    private String stripExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) return filename;
        return filename.substring(0, lastDot);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }
}
