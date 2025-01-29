package redxax.oxy.servers;

import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.api.*;
import redxax.oxy.config.Config;

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

import static redxax.oxy.config.Config.*;
import static redxax.oxy.util.DevUtil.devPrint;
import static redxax.oxy.util.ImageUtil.drawBufferedImage;
import static redxax.oxy.util.ImageUtil.loadResourceIcon;
import static redxax.oxy.util.ImageUtil.loadSpriteSheet;
import static redxax.oxy.Render.*;

public class PluginModManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private final List<IRemotelyResource> resources = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<IRemotelyResource>> resourceCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> iconImages = new ConcurrentHashMap<>();

    private final Map<String, BufferedImage> scaledIcons = Collections.synchronizedMap(new LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
        private static final int MAX_ENTRIES = 10000;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_ENTRIES;
        }
    });

    private final Map<String, Boolean> installingMrPack = new ConcurrentHashMap<>();
    private final Map<String, Boolean> installingResource = new ConcurrentHashMap<>();
    private final Map<String, String> installButtonTexts = new ConcurrentHashMap<>();
    private final Map<String, Integer> resourceColors = new ConcurrentHashMap<>();
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
    private final int colorDownloadSuccess = 0xFF00FF00;
    private final int colorDownloadFail = 0xFFFF0000;
    private final int colorNotDownloaded = 0xFF999999;

    private final Map<String, Integer> imageLoadRetries = new ConcurrentHashMap<>();
    private static final int MAX_IMAGE_LOAD_RETRIES = 3;

    private enum TabMode { MODRINTH, SPIGOT, HANGAR, SORT }
    public static class Tab {
        TabMode mode;
        public String name;
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
    private int currentSortIndex = 0;
    private final Map<TabMode, String[]> sortLabels = new HashMap<>();
    private final Map<TabMode, String[]> sortValues = new HashMap<>();

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
            tabs.add(new Tab(TabMode.SPIGOT, "Spigot"));
            tabs.add(new Tab(TabMode.HANGAR, "Hangar"));
        }
        tabs.add(new Tab(TabMode.SORT, "Sort"));
        sortLabels.put(TabMode.MODRINTH, new String[]{"Relevance", "Downloads", "Most Followers", "Last Updated", "Newest"});
        sortValues.put(TabMode.MODRINTH, new String[]{"relevance", "downloads", "follows", "updated", "newest"});
        sortLabels.put(TabMode.SPIGOT, new String[]{"Highest Rating", "Most Downloads", "Last Updated", "Newest"});
        sortValues.put(TabMode.SPIGOT, new String[]{"-rating", "-downloads", "-updateDate", "-releaseDate"});
        sortLabels.put(TabMode.HANGAR, new String[]{"Most Stars", "Most Views", "Most Downloads", "Last Updated", "Newest"});
        sortValues.put(TabMode.HANGAR, new String[]{"stars", "views", "downloads", "updated", "newest"});
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
    private void updateSortLabel() {
        TabMode mode = getPreviousMode();
        String sortLabel = sortLabels.get(mode)[currentSortIndex];
        tabs.get(tabs.size() - 1).name = "Sort By: " + sortLabel;
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
                if (tab.mode == TabMode.SORT) {
                    nextSort();
                    loadResourcesAsync(currentSearch, true);
                } else {
                    currentTabIndex = i;
                    loadResourcesAsync(currentSearch, true);
                }
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
                            IRemotelyResource selected = resources.get(index);
                            if (!installButtonTexts.containsKey(selected.getSlug())) {
                                installButtonTexts.put(selected.getSlug(), "Install");
                            }
                            if (!installButtonTexts.get(selected.getSlug()).equalsIgnoreCase("Installed")) {
                                if (selected.getFileName().toLowerCase(Locale.ROOT).endsWith(".mrpack") || Objects.equals(serverInfo.path, "modpack")) {
                                    if (!installingMrPack.containsKey(selected.getSlug()) || !installingMrPack.get(selected.getSlug())) {
                                        installingMrPack.put(selected.getSlug(), true);
                                        installButtonTexts.put(selected.getSlug(), "Installing");
                                        resourceColors.put(selected.getSlug(), colorNotDownloaded);
                                        installMrPack(selected);
                                    }
                                } else {
                                    if (!installingResource.containsKey(selected.getSlug()) || !installingResource.get(selected.getSlug())) {
                                        installingResource.put(selected.getSlug(), true);
                                        installButtonTexts.put(selected.getSlug(), "Installing");
                                        resourceColors.put(selected.getSlug(), colorNotDownloaded);
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
        context.fillGradient(0, 0, this.width, this.height, browserScreenBackgroundColor, browserScreenBackgroundColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        int titleBarHeight = 30;
        context.fill(0, 0, this.width, titleBarHeight, headerBackgroundColor);
        drawInnerBorder(context, 0, 0, this.width, titleBarHeight, headerBorderColor);
        drawOuterBorder(context, 0, 0, this.width, titleBarHeight, globalBottomBorder);
        context.drawText(this.textRenderer, Text.literal(this.getTitle().getString()), 10, 10, screensTitleTextColor, Config.shadow);
        int tabBarY = titleBarHeight + 5;
        int tabBarHeight = TAB_HEIGHT;
        drawTabs(
                context,
                this.textRenderer,
                tabs,
                currentTabIndex,
                mouseX,
                mouseY,
                false);
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
                false,
                "PluginModManagerScreen"
        );
        int closeButtonX = this.width - buttonW - 10;
        int closeButtonY = 5;
        boolean hoveredClose = mouseX >= closeButtonX && mouseX <= closeButtonX + buttonW && mouseY >= closeButtonY && mouseY <= closeButtonY + buttonH;
        drawCustomButton(context, closeButtonX, closeButtonY, "Close", minecraftClient, hoveredClose, false, true, buttonTextColor, buttonTextDeleteColor);
        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;
        int contentY = tabBarY + tabBarHeight + 30;
        int contentHeight = this.height - contentY - 10;
        int contentX = 5;
        int contentWidth = this.width - 10;
        context.fill(contentX, contentY - 25, contentX + contentWidth, contentY, headerBackgroundColor);
        drawInnerBorder(context, contentX, contentY - 25, contentWidth, 25, headerBorderColor);
        drawOuterBorder(context, contentX, contentY - 25, contentWidth, 25, globalBottomBorder);
        context.drawText(textRenderer, Text.literal("Name"), contentX + 10, contentY - 18, screensTitleTextColor, Config.shadow);
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
            IRemotelyResource resource = resources.get(i);
            int y = contentY + (i * (entryHeight + gapBetweenEntries)) - (int) smoothOffset;
            boolean hovered = mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= y && mouseY < y + entryHeight;
            boolean isSelected = (i == selectedIndex);
            int bg = isSelected ? (currentTabIndex == 0 ? ModrinthBackgroundColor : currentTabIndex == 1 ? SpigotBackgroundColor : HangarBackgroundColor) : (hovered ? browserElementBackgroundHoverColor : browserElementBackgroundColor);
            int borderColorFinal = isSelected ? (currentTabIndex == 0 ? ModrinthBorderColor : currentTabIndex == 1 ? SpigotBorderColor : HangarBorderColor) : (hovered ? browserElementBorderHoverColor : browserElementBorderColor);
            context.fill(contentX, y, contentX + contentWidth, y + entryHeight, bg);
            drawInnerBorder(context, contentX, y, contentWidth, entryHeight, borderColorFinal);
            drawOuterBorder(context, contentX, y, contentWidth, entryHeight, globalBottomBorder);
            BufferedImage scaledImage = resource.getIconUrl().isEmpty() ? placeholderIcon : scaledIcons.getOrDefault(resource.getIconUrl(), placeholderIcon);
            drawBufferedImage(context, scaledImage, contentX + 5, y + (entryHeight - 30) / 2, 30, 30);
            int colorToUse = resourceColors.getOrDefault(resource.getSlug(), colorNotDownloaded);
            String resourceName = resource.getName();
            context.drawText(textRenderer, Text.literal(resourceName), contentX + 40, y + 5, colorToUse, Config.shadow);
            String resourceDesc = resource.getDescription();
            int descMaxWidth = contentWidth - 50;
            if (textRenderer.getWidth(resourceDesc) > descMaxWidth) {
                while (textRenderer.getWidth(resourceDesc + "...") > descMaxWidth && resourceDesc.length() > 0) {
                    resourceDesc = resourceDesc.substring(0, resourceDesc.length() - 1);
                }
                resourceDesc += "...";
            }
            context.drawText(textRenderer, Text.literal(resourceDesc), contentX + 40, y + 16, browserElementTextColor, Config.shadow);
            String mrInfo = formatDownloads(resource.getDownloads()) + " | " + resource.getVersion()  + " | " + resource.getFollowers() + " Followers";
            String spInfo = formatDownloads(resource.getDownloads()) + " | " + resource.getAverageRating() + " Star Rating";
            String hgInfo = formatDownloads(resource.getDownloads()) + " | " + resource.getVersion()  + " | " + resource.getFollowers() + " Stars";
            if (tabs.get(currentTabIndex).mode == TabMode.MODRINTH) {
                context.drawText(textRenderer, Text.literal(mrInfo), contentX + 40, y + 30, browserElementTextDimColor, Config.shadow);
            } else if (tabs.get(currentTabIndex).mode == TabMode.SPIGOT) {
                context.drawText(textRenderer, Text.literal(spInfo), contentX + 40, y + 30, browserElementTextDimColor, Config.shadow);
            } else if (tabs.get(currentTabIndex).mode == TabMode.HANGAR) {
                context.drawText(textRenderer, Text.literal(hgInfo), contentX + 40, y + 30, browserElementTextDimColor, Config.shadow);
            }
            int buttonX = contentX + 5;
            int buttonY = y + (entryHeight - 30) / 2;
            int buttonSize = 30;
            boolean isHoveringInstall = mouseX >= buttonX && mouseX <= buttonX + buttonSize && mouseY >= buttonY && mouseY <= buttonY + buttonSize;
            if (isHoveringInstall) {
                context.fill(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, 0x80000000);
                BufferedImage buttonIcon = installIcon;
                String status = installButtonTexts.getOrDefault(resource.getSlug(), "Install");
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
            resourceCache.clear();
            hasMore = true;
        }
        String cacheKey = query + "_" + loadedCount + "_" + currentTabIndex;
        if (resourceCache.containsKey(cacheKey)) {
            synchronized (resources) {
                resources.addAll(resourceCache.get(cacheKey));
            }
            return;
        }
        isLoading = true;
        CompletableFuture<List<IRemotelyResource>> searchFuture;
        String serverVersion = serverInfo.getVersion();
        int limit = 30;
        TabMode tabMode = tabs.get(currentTabIndex).mode == TabMode.SORT ? getPreviousMode() : tabs.get(currentTabIndex).mode;
        String sortParam = getCurrentSortParam(tabMode);
        if (tabMode == TabMode.MODRINTH) {
            if (serverInfo.isModServer()) {
                searchFuture = ModrinthAPI.searchMods(query, serverVersion, limit, loadedCount, serverInfo.type, sortParam)
                        .thenApply(list -> new ArrayList<>(list));
            } else if (serverInfo.isPluginServer()) {
                searchFuture = ModrinthAPI.searchPlugins(query, serverVersion, limit, loadedCount, serverInfo.type, sortParam)
                        .thenApply(list -> new ArrayList<>(list));
            } else {
                searchFuture = ModrinthAPI.searchModpacks(query, serverVersion, limit, loadedCount, sortParam)
                        .thenApply(list -> new ArrayList<>(list));
            }
        } else if (tabMode == TabMode.SPIGOT) {
            int page = loadedCount / limit;
            searchFuture = SpigetAPI.searchPlugins(query, limit, page, sortParam)
                    .thenApply(list -> {
                        List<IRemotelyResource> mapped = new ArrayList<>();
                        for (SpigetResource sr : list) {
                            mapped.add(sr);
                        }
                        return mapped;
                    });
        } else {
            int offset = loadedCount;
            searchFuture = HangarAPI.searchPlugins(query, limit, offset, sortParam)
                    .thenApply(list -> {
                        List<IRemotelyResource> mapped = new ArrayList<>();
                        for (HangarResource hr : list) {
                            mapped.add(hr);
                        }
                        return mapped;
                    });
        }
        searchFuture.thenAccept(fetched -> {
            if (fetched.size() < limit) hasMore = false;
            Set<String> seenSlugs = ConcurrentHashMap.newKeySet();
            List<IRemotelyResource> uniqueResources = new ArrayList<>();
            for (IRemotelyResource r : fetched) {
                if (seenSlugs.add(r.getSlug())) {
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
                if (!resource.getIconUrl().isEmpty() && !iconImages.containsKey(resource.getIconUrl())) {
                    loadImageWithRetry(resource.getIconUrl());
                }
            });
        }).exceptionally(e -> {
            e.printStackTrace();
            isLoading = false;
            isLoadingMore = false;
            return null;
        });
        updateSortLabel();
    }

    private void loadImageWithRetry(String url) {
        if (iconImages.containsKey(url) || scaledIcons.containsKey(url)) {
            return;
        }
        imageLoadRetries.putIfAbsent(url, 0);
        imageLoader.submit(() -> {
            try (InputStream inputStream = new URL(url).openStream()) {
                BufferedImage bufferedImage = loadImage(inputStream, url);
                if (bufferedImage != null) {
                    iconImages.put(url, bufferedImage);
                    BufferedImage scaled = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = scaled.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.drawImage(bufferedImage, 0, 0, 30, 30, null);
                    g2d.dispose();
                    synchronized (scaledIcons) {
                        scaledIcons.put(url, scaled);
                    }
                    imageLoadRetries.remove(url);
                }
            } catch (Exception e) {
                int retries = imageLoadRetries.getOrDefault(url, 0);
                if (retries < MAX_IMAGE_LOAD_RETRIES) {
                    imageLoadRetries.put(url, retries + 1);
                    devPrint("Retrying image load for URL: " + url + " (Attempt " + (retries + 1) + ")");
                    loadImageWithRetry(url);
                } else {
                    devPrint("Failed to load image after " + MAX_IMAGE_LOAD_RETRIES + " attempts: " + url);
                    scaledIcons.put(url, placeholderIcon);
                    imageLoadRetries.remove(url);
                }
            }
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
        byte[] imageData = baos.toByteArray();
        try {
            return ImageIO.read(new ByteArrayInputStream(imageData));
        } catch (Exception e) {
            if (url.toLowerCase(Locale.ROOT).contains(".webp")) {
                try (ByteArrayInputStream webpStream = new ByteArrayInputStream(imageData)) {
                    BufferedImage webpImage = ImageIO.read(webpStream);
                    if (webpImage != null) {
                        return webpImage;
                    }
                }
            }
            throw e;
        }
    }

    private BufferedImage createPlaceholderIcon() {
        BufferedImage img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(new Color(0xFF555555, true));
        g2d.fillRect(0, 0, 30, 30);
        g2d.dispose();
        return img;
    }

    private void installMrPack(IRemotelyResource resource) {
        if (serverInfo.isRemote && serverInfo.remoteSSHManager != null) {
            new Thread(() -> {
                boolean success = serverInfo.remoteSSHManager.installMrPackOnRemote(serverInfo, resource);
                minecraftClient.execute(() -> {
                    installingMrPack.put(resource.getSlug(), false);
                    installButtonTexts.put(resource.getSlug(), success ? "Installed" : "Install");
                    resourceColors.put(resource.getSlug(), success ? colorDownloadSuccess : colorDownloadFail);
                });
            }).start();
            return;
        }
        new Thread(() -> {
            try {
                String exePath = "C:\\remotely\\mrpack-install-windows.exe";
                String serverDir = "C:\\remotely\\servers\\" + resource.getName();
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
                        minecraftClient.execute(() -> {
                            installingMrPack.put(resource.getSlug(), false);
                            installButtonTexts.put(resource.getSlug(), "Install");
                            resourceColors.put(resource.getSlug(), colorDownloadFail);
                        });
                        return;
                    }
                }
                devPrint("Modpack Installation: Running " + exePath + " " + resource.getProjectId() + " " + resource.getVersion() + " --server-dir " + serverDir + " --server-file server.jar");
                ProcessBuilder pb = new ProcessBuilder(exePath, resource.getProjectId(), resource.getVersion(), "--server-dir", serverDir, "--server-file", "server.jar");
                pb.directory(serverPath.toFile());
                Process proc = pb.start();
                ExecutorService executor = Executors.newFixedThreadPool(2);
                executor.submit(() -> {
                    try (InputStream is = proc.getInputStream()) {
                        is.transferTo(System.out);
                    } catch (Exception ignored) {
                    }
                });
                executor.submit(() -> {
                    try (InputStream is = proc.getErrorStream()) {
                        is.transferTo(System.err);
                    } catch (Exception ignored) {
                    }
                });
                proc.waitFor();
                executor.shutdown();
                minecraftClient.execute(() -> {
                    installingMrPack.put(resource.getSlug(), false);
                    installButtonTexts.put(resource.getSlug(), "Installed");
                    resourceColors.put(resource.getSlug(), colorDownloadSuccess);
                });
            } catch (Exception e) {
                minecraftClient.execute(() -> {
                    installingMrPack.put(resource.getSlug(), false);
                    installButtonTexts.put(resource.getSlug(), "Install");
                    resourceColors.put(resource.getSlug(), colorDownloadFail);
                });
            }
        }).start();
    }

    private void fetchAndInstallResource(IRemotelyResource resource) {
        Thread t = new Thread(() -> {
            try {
                String downloadUrl = getDownloadUrlFor(resource);
                if (downloadUrl.isEmpty()) {
                    minecraftClient.execute(() -> {
                        installingResource.put(resource.getSlug(), false);
                        installButtonTexts.put(resource.getSlug(), "Install");
                        resourceColors.put(resource.getSlug(), colorDownloadFail);
                    });
                    return;
                }
                if (serverInfo.isRemote && serverInfo.remoteSSHManager != null) {
                    String remotePath = serverInfo.path + "/" + (serverInfo.isModServer() ? "mods" : serverInfo.isPluginServer() ? "plugins" : "unknown") + "/"  + resource.getFileName();
                    String command = "wget -O " + remotePath +  " " + downloadUrl;
                    devPrint("Remote Download: " + command);
                    serverInfo.remoteSSHManager.runRemoteCommand(command);
                    minecraftClient.execute(() -> {
                        installingResource.put(resource.getSlug(), false);
                        installButtonTexts.put(resource.getSlug(), "Installed");
                        resourceColors.put(resource.getSlug(), colorDownloadSuccess);
                    });
                } else {
                    Path dest;
                    String baseName = stripExtension(resource.getFileName());
                    String extension = "";
                    if (serverInfo.isModServer() || serverInfo.isPluginServer()) {
                        extension = ".jar";
                    }
                    if (serverInfo.isModServer()) {
                        dest = Path.of(serverInfo.path, "mods", baseName + extension);
                    } else if (serverInfo.isPluginServer()) {
                        dest = Path.of(serverInfo.path, "plugins", baseName + extension);
                    } else {
                        dest = Path.of(serverInfo.path, "unknown", resource.getFileName());
                    }
                    Files.createDirectories(dest.getParent());
                    HttpClient httpClient = HttpClient.newBuilder().executor(imageLoader).build();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(downloadUrl))
                            .header("User-Agent", "Remotely")
                            .header("Content-Type", "application/octet-stream")
                            .GET()
                            .build();
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    devPrint("Response Code: " + response.statusCode());
                    if (response.statusCode() == 200 || response.statusCode() == 302 || response.statusCode() == 303) {
                        devPrint("Downloading " + downloadUrl + " to " + dest);
                        Files.copy(response.body(), dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    minecraftClient.execute(() -> {
                        installingResource.put(resource.getSlug(), false);
                        installButtonTexts.put(resource.getSlug(), "Installed");
                        resourceColors.put(resource.getSlug(), colorDownloadSuccess);
                    });
                }
            } catch (Exception e) {
                minecraftClient.execute(() -> {
                    installingResource.put(resource.getSlug(), false);
                    installButtonTexts.put(resource.getSlug(), "Install");
                    resourceColors.put(resource.getSlug(), colorDownloadFail);
                });
            }
        });
        t.start();
    }

    private String getDownloadUrlFor(IRemotelyResource resource) {
        if (resource instanceof SpigetResource) {
            SpigetResource sp = (SpigetResource) resource;
            devPrint("External Spiget Download: " + "https://api.spiget.org/v2/resources/" + sp.getProjectId() + "/download");
            return "https://api.spiget.org/v2/resources/" + sp.getProjectId() + "/download";
        } else if (resource instanceof HangarResource) {
            HangarResource hg = (HangarResource) resource;
            String hgURI = "https://hangar.papermc.io/api/v1/versions/" + hg.getProjectId() +  "/" + serverInfo.type.toUpperCase() + "/download";
            devPrint("Hangar Download: " + hgURI);
            return followRedirect(hgURI);
        } else {
            try {
                URI uri = URI.create("https://api.modrinth.com/v2/version/" + resource.getVersionId());
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("User-Agent", "Remotely")
                        .header("Content-Type", "application/octet-stream")
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
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private String followRedirect(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Remotely")
                    .header("Content-Type", "application/octet-stream")
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 301 || response.statusCode() == 302 || response.statusCode() == 303 || response.statusCode() == 307 || response.statusCode() == 308) {
                return response.headers().firstValue("Location").orElse("");
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    private String stripExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) return filename;
        return filename.substring(0, lastDot);
    }

    private void nextSort() {
        TabMode mode = getPreviousMode();
        currentSortIndex++;
        if (currentSortIndex >= sortValues.get(mode).length) {
            currentSortIndex = 0;
        }
    }

    private TabMode getPreviousMode() {
        if (currentTabIndex == 0) {
            return TabMode.MODRINTH;
        } else if (tabs.get(currentTabIndex).mode == TabMode.SORT) {
            if (serverInfo.isPluginServer()) {
                return tabs.get(currentTabIndex - 1).mode == TabMode.HANGAR ? TabMode.HANGAR : (tabs.get(currentTabIndex - 1).mode == TabMode.SPIGOT ? TabMode.SPIGOT : TabMode.MODRINTH);
            } else {
                return TabMode.MODRINTH;
            }
        }
        return tabs.get(currentTabIndex).mode;
    }

    private String getCurrentSortParam(TabMode mode) {
        if (!sortValues.containsKey(mode)) {
            return "downloads";
        }
        return sortValues.get(mode)[currentSortIndex];
    }
}
