package redxax.oxy.remotely.servers;

import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.Render.ScrollBar;
import redxax.oxy.remotely.api.HangarAPI;
import redxax.oxy.remotely.api.IRemotelyResource;
import redxax.oxy.remotely.api.ModrinthAPI;
import redxax.oxy.remotely.api.SpigetAPI;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.util.ImageUtil;

import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.remotely.util.Sound;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.ImageUtil.drawBufferedImage;
import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class PluginModManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private final List<IRemotelyResource> resources = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<IRemotelyResource>> resourceCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> iconImages = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> scaledIcons = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        private static final int MAX_ENTRIES = 10000;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_ENTRIES;
        }
    });
    private final ExecutorService imageLoader = Executors.newFixedThreadPool(4);
    private final BufferedImage placeholderIcon = createPlaceholderIcon();
    private float smoothOffset = 0;
    private float targetOffset = 0;
    private final int entryHeight = 40;
    private final int gapBetweenEntries = 2;
    private int selectedIndex = -1;
    private volatile boolean isLoadingMore = false;
    private boolean hasMore = false;
    private int loadedCount = 0;
    private String currentSearch = "";
    private boolean fieldFocused = false;
    private final StringBuilder fieldText = new StringBuilder();
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private long lastBlinkTime = 0;
    private boolean showCursor = true;
    private TextRenderer textRenderer;
    private final Map<String, Integer> imageLoadRetries = new ConcurrentHashMap<>();
    private static final int MAX_IMAGE_LOAD_RETRIES = 3;
    private long lastResourceClickTime = 0;
    private int lastResourceClickIndex = -1;
    private ImageUtil.IconWithTooltip closeIcon;

    private enum TabMode { MODRINTH, SPIGOT, HANGAR, SORT }
    public static class Tab {
        TabMode mode;
        public String name;

        Tab(TabMode mode, String name) {
            this.mode = mode;
            this.name = name;
        }
    }
    private final List<Tab> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private final int TAB_HEIGHT = 18;
    private int currentSortIndex = 0;
    private final Map<TabMode, String[]> sortLabels = new HashMap<>();
    private final Map<TabMode, String[]> sortValues = new HashMap<>();

    private static boolean hasSavedState = false;
    private static List<IRemotelyResource> savedResources = new ArrayList<>();
    private static float savedSmoothOffset = 0;
    private static float savedTargetOffset = 0;
    private static int savedLoadedCount = 0;
    private static String savedSearch = "";
    private static int savedCurrentTabIndex = 0;
    private static Map<String, List<IRemotelyResource>> savedResourceCache = new ConcurrentHashMap<>();

    public PluginModManagerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal(info.isModServer() ? "Remotely - Mods Browser" : (info.isPluginServer() || info.isProxyServer() ? "Remotely - Plugins Browser" : "Remotely - Modpacks Browser")));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
        originalMCScale = minecraftClient.getWindow().getScaleFactor();
        targetScaleFactor = globalScaleFactor;
        minecraftClient.getWindow().setScaleFactor(globalScaleFactor);    }

    @Override
    protected void init() {
        super.init();
        this.textRenderer = this.minecraftClient.textRenderer;
        tabs.clear();
        tabs.add(new Tab(TabMode.MODRINTH, "Modrinth"));
        if (serverInfo.isPluginServer() || serverInfo.isProxyServer()) {
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
        if(hasSavedState) {
            fieldText.append(savedSearch);
            cursorPosition = fieldText.length();
            currentTabIndex = savedCurrentTabIndex;
            resources.addAll(savedResources);
            smoothOffset = savedSmoothOffset;
            targetOffset = savedTargetOffset;
            loadedCount = savedLoadedCount;
            resourceCache.putAll(savedResourceCache);
            hasSavedState = false;
        } else {
            cursorPosition = 0;
            loadResourcesAsync("", true);
        }
        try {
            closeIcon = new ImageUtil.IconWithTooltip("/assets/remotely/icons/close.png", "");
        } catch (Exception e) {
            devPrint("Failed to load close icon: " + e.getMessage());
        }
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
        if (ScrollBar.handleMousePressed(this, (int) mouseX, (int) mouseY, resources.size() * (entryHeight + gapBetweenEntries), smoothOffset)){
            return true;
        }
        boolean handled = false;
        int titleBarHeight = 30;
        int tabBarY = titleBarHeight + 5;
        int tabBarHeight = TAB_HEIGHT;
        int tabX = 5;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int TAB_PADDING = 5;
            int tabWidth = this.textRenderer.getWidth(tab.name) + 2 * TAB_PADDING;
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight) {
                playSound(Sound.SEARCH);
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
            int TAB_GAP = 5;
            tabX += tabWidth + TAB_GAP;
        }
        int plusTabX = tabX;
        if (!handled) {
            int PLUS_TAB_WIDTH = 18;
            if (mouseX >= plusTabX && mouseX <= plusTabX + PLUS_TAB_WIDTH && mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight) {
                handled = true;
            }
        }
        if (!handled) {
            boolean hoveredClose = mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24;
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
                int contentHeight = this.height - contentY - 5;
                int contentX = 5;
                int contentWidth = this.width - 10;
                if (mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= contentY && mouseY <= contentY + contentHeight) {
                    int relativeY = (int) mouseY - contentY + (int) smoothOffset;
                    int index = relativeY / (entryHeight + gapBetweenEntries);
                    if (index >= 0 && index < resources.size()) {
                        selectedIndex = index;
                        long currentTime = System.currentTimeMillis();
                        if (lastResourceClickIndex == index && (currentTime - lastResourceClickTime < 250)) {
                            minecraftClient.setScreen(new ResourcePageScreen(minecraftClient, this, resources.get(index), serverInfo));
                            return true;
                        } else {
                            playSound(Sound.SELECT);
                        }
                        lastResourceClickIndex = index;
                        lastResourceClickTime = currentTime;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (ScrollBar.handleMouseDragged(this, (int) mouseY, resources.size() * (entryHeight + gapBetweenEntries))) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ScrollBar.handleMouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
            devPrint("Failed to paste clipboard: " + e.getMessage());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, /*? !=1.20.1 {*/ double horizontalAmount, /*?}*/ double verticalAmount) {
        scaleScroll(verticalAmount);
        targetOffset -= (float) (verticalAmount * entryHeight);
        targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, resources.size() * (entryHeight + gapBetweenEntries) - (this.height - 70))));
        ScrollBar.setPendingOffset(targetOffset);
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawScreenHeader(context, width, height, width - 5, mouseX, mouseY, this, minecraftClient, closeIcon, null, null, null , null, null, null, null, null);
        int titleBarHeight = 30;
        context.drawText(this.textRenderer, Text.literal(this.getTitle().getString()), 10, 10, globalTextColor, Config.shadow);
        int tabBarY = titleBarHeight + 5;
        drawTabs(context, this.textRenderer, tabs, currentTabIndex, mouseX, mouseY, false, false);
        float pathScrollOffset = 0;
        float pathTargetScrollOffset = 0;
        drawSearchBar(context, textRenderer, fieldText, fieldFocused, cursorPosition, selectionStart, selectionEnd, pathTargetScrollOffset, false, "PluginModManagerScreen", mouseX, mouseY, "Search For Resources");
        smoothOffset += (targetOffset - smoothOffset) * globalScrollSpeed * deltaTime;
        int contentY = tabBarY + TAB_HEIGHT + 30;
        int contentHeight = this.height - contentY - 5;
        int contentX = 5;
        int contentWidth = this.width - 10;
        context.fill(contentX, contentY - 25, contentX + contentWidth, contentY, Config.innerBackgroundColor);
        drawInnerBorder(context, contentX, contentY - 25, contentWidth, 25, Config.innerBorderColor);
        drawOuterBorder(context, contentX, contentY - 25, contentWidth, 25, innerBackgroundColor);
        context.drawText(textRenderer, Text.literal("Name"), contentX + 10, contentY - 18, globalTextColor, Config.shadow);
        context.enableScissor(contentX - 1, contentY, contentX + contentWidth + 1, contentY + contentHeight);
        if (loading && resources.isEmpty()) {
            context.disableScissor();
            return;
        }
        int startIndex = (int) Math.floor(smoothOffset / (entryHeight + gapBetweenEntries));
        int visibleEntries = contentHeight / (entryHeight + gapBetweenEntries);
        int endIndex = Math.min(startIndex + visibleEntries + 2, resources.size());
        for (int i = startIndex; i < endIndex; i++) {
            IRemotelyResource resource = resources.get(i);
            int baseY = contentY + (i * (entryHeight + gapBetweenEntries)) - (int) smoothOffset;
            boolean hovered = mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= baseY && mouseY < baseY + entryHeight;
            int elevId = ("pmmEntry" + resource.hashCode()).hashCode();
            float targetOffset = hovered ? -3f : 0f;
            float currentOffset = elevationOffsets.getOrDefault(elevId, 0f);
            currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
            elevationOffsets.put(elevId, currentOffset);
            context.getMatrices().push();
            context.getMatrices().translate(0, currentOffset, 0);
            int bg = getElementBackgroundColor(resource.hashCode(), hovered, i == selectedIndex, true, false, false, false);
            int borderColorFinal = getElementBorderColor(resource.hashCode(), hovered, i == selectedIndex, true, false, false, false);
            context.fill(contentX, baseY, contentX + contentWidth, baseY + entryHeight, bg);
            drawInnerBorder(context, contentX, baseY, contentWidth, entryHeight, borderColorFinal);
            drawOuterBorder(context, contentX, baseY, contentWidth, entryHeight, bg);
            BufferedImage icon = resource.getIconUrl().isEmpty() ? placeholderIcon : iconImages.getOrDefault(resource.getIconUrl(), placeholderIcon);
            drawBufferedImage(context, icon, contentX + 5, baseY + (entryHeight - 30) / 2, 30, 30);
            String resourceName = resource.getName();
            context.drawText(textRenderer, Text.literal(resourceName), contentX + 40, baseY + 5, 0xFFFFFFFF, Config.shadow);
            String resourceDesc = resource.getDescription();
            int descMaxWidth = contentWidth - 50;
            if (textRenderer.getWidth(resourceDesc) > descMaxWidth) {
                while (textRenderer.getWidth(resourceDesc + "...") > descMaxWidth && resourceDesc.length() > 0) {
                    resourceDesc = resourceDesc.substring(0, resourceDesc.length() - 1);
                }
                resourceDesc += "...";
            }
            context.drawText(textRenderer, Text.literal(resourceDesc), contentX + 40, baseY + 16, globalTextColor, Config.shadow);
            String mrInfo = formatDownloads(resource.getDownloads()) + " | " + resource.getVersion()  + " | " + resource.getFollowers() + " Followers";
            String spInfo = formatDownloads(resource.getDownloads()) + " | " + resource.getAverageRating() + " Star Rating";
            String hgInfo = formatDownloads(resource.getDownloads()) + " | " + resource.getVersion()  + " | " + resource.getFollowers() + " Stars";
            if (tabs.get(currentTabIndex).mode == TabMode.MODRINTH) {
                context.drawText(textRenderer, Text.literal(mrInfo), contentX + 40, baseY + 30, Config.globalDarkTextColor, Config.shadow);
            } else if (tabs.get(currentTabIndex).mode == TabMode.SPIGOT) {
                context.drawText(textRenderer, Text.literal(spInfo), contentX + 40, baseY + 30, Config.globalDarkTextColor, Config.shadow);
            } else if (tabs.get(currentTabIndex).mode == TabMode.HANGAR) {
                context.drawText(textRenderer, Text.literal(hgInfo), contentX + 40, baseY + 30, Config.globalDarkTextColor, Config.shadow);
            }

            context.getMatrices().pop();
        }
        context.disableScissor();
        if (smoothOffset > 0) {
            context.fillGradient(contentX, contentY, contentX + contentWidth, contentY + 10, 0x80000000, 0x00000000);
        }
        int maxScroll = Math.max(0, resources.size() * (entryHeight + gapBetweenEntries) - contentHeight);
        if (smoothOffset < maxScroll) {
            context.fillGradient(contentX, contentY + contentHeight - 10, contentX + contentWidth, contentY + contentHeight, 0x00000000, 0x80000000);
        }
        ScrollBar.render(context, this, mouseX, mouseY, resources.size() * (entryHeight + gapBetweenEntries), targetOffset);
        targetOffset = ScrollBar.getPendingOffset();
        loadMoreIfNeeded();
        animatedScaling(this);
    }


    static String formatDownloads(int n) {
        if (n >= 1_000_000) {
            return String.format("%.1fM", n / 1_000_000.0);
        } else if (n >= 1000) {
            return String.format("%.1fK", n / 1000.0);
        } else {
            return Integer.toString(n);
        }
    }

    private void loadMoreIfNeeded() {
        if (!hasMore || isLoadingMore || loading) return;
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
        loading = true;
        CompletableFuture<List<IRemotelyResource>> searchFuture;
        String serverVersion = serverInfo.getVersion();
        int limit = 30;
        TabMode tabMode = tabs.get(currentTabIndex).mode == TabMode.SORT ? getPreviousMode() : tabs.get(currentTabIndex).mode;
        String sortParam = getCurrentSortParam(tabMode);
        if (tabMode == TabMode.MODRINTH) {
            if (serverInfo.isModServer()) {
                searchFuture = ModrinthAPI.searchMods(query, serverVersion, limit, loadedCount, serverInfo.type, sortParam)
                        .thenApply(ArrayList::new);
            } else if (serverInfo.isPluginServer()) {
                searchFuture = ModrinthAPI.searchPlugins(query, serverVersion, limit, loadedCount, serverInfo.type, sortParam)
                        .thenApply(ArrayList::new);
            } else {
                searchFuture = ModrinthAPI.searchModpacks(query, serverVersion, limit, loadedCount, sortParam)
                        .thenApply(ArrayList::new);
            }
        } else if (tabMode == TabMode.SPIGOT) {
            int page = loadedCount / limit;
            searchFuture = SpigetAPI.searchPlugins(query, limit, page, sortParam)
                    .thenApply(ArrayList::new);
        } else {
            int offset = loadedCount;
            searchFuture = HangarAPI.searchPlugins(query, limit, offset, sortParam)
                    .thenApply(ArrayList::new);
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
            loading = false;
            isLoadingMore = false;
            uniqueResources.forEach(resource -> {
                if (!resource.getIconUrl().isEmpty() && !iconImages.containsKey(resource.getIconUrl())) {
                    loadImageWithRetry(resource.getIconUrl());
                }
            });
        }).exceptionally(e -> {
            devPrint("Failed to load resources: " + e.getMessage());
            loading = false;
            isLoadingMore = false;
            return null;
        });
        updateSortLabel();
    }

    private void loadImageWithRetry(String url) {
        if (iconImages.containsKey(url)) {
            return;
        }
        imageLoadRetries.putIfAbsent(url, 0);
        imageLoader.submit(() -> {
            try (InputStream inputStream = new URL(url).openStream()) {
                BufferedImage bufferedImage = loadImage(inputStream, url);
                if (bufferedImage != null) {
                    iconImages.put(url, bufferedImage);
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
                    iconImages.put(url, placeholderIcon);
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

    private void nextSort() {
        TabMode mode = getPreviousMode();
        currentSortIndex++;
        if (currentSortIndex >= sortValues.get(mode).length) {
            currentSortIndex = 0;
        }
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public boolean isCanScroll() {
        return smoothOffset + (this.height - 70) < resources.size() * (entryHeight + gapBetweenEntries);
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }

    @Override
    public void removed() {
        hasSavedState = true;
        savedResources = new ArrayList<>(resources);
        savedSmoothOffset = smoothOffset;
        savedTargetOffset = targetOffset;
        savedLoadedCount = loadedCount;
        savedSearch = currentSearch;
        savedCurrentTabIndex = currentTabIndex;
        savedResourceCache = new ConcurrentHashMap<>(resourceCache);
        if (parent == null) playSound(Sound.SCREEN);
        super.removed();
    }
}
