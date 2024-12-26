package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PluginModManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private final List<ModrinthResource> resources = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<ModrinthResource>> resourceCache = new ConcurrentHashMap<>();
    private final Map<String, Identifier> iconTextures = new ConcurrentHashMap<>();
    private TextFieldWidget searchField;
    private int entryHeight = 50;
    private volatile boolean isLoading = false;
    private volatile boolean isLoadingMore = false;
    private String currentSearch = "";
    private float smoothOffset = 0;
    private float targetOffset = 0;
    private float scrollSpeed = 0.2f;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private int loadedCount = 0;
    private boolean hasMore = false;

    public PluginModManagerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal("Plugin / Mod Manager"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
    }

    @Override
    protected void init() {
        super.init();
        searchField = new TextFieldWidget(this.textRenderer, 10, 30, this.width - 140, 20, Text.literal(""));
        searchField.setMaxLength(100);
        searchField.setChangedListener(text -> currentSearch = text);
        this.addDrawableChild(searchField);
        loadResourcesAsync("", true);
    }

    private void loadResourcesAsync(String query, boolean reset) {
        if (reset) {
            loadedCount = 0;
            resources.clear();
            smoothOffset = 0;
            targetOffset = 0;
        }
        if (resourceCache.containsKey(query + "_" + loadedCount)) {
            synchronized (resources) {
                resources.addAll(resourceCache.get(query + "_" + loadedCount));
            }
            return;
        }
        isLoading = true;
        if (reset) hasMore = true;
        ModrinthAPI.searchModpacks(query, 30, loadedCount).thenAccept(fetched -> {
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
            resourceCache.put(query + "_" + loadedCount, new ArrayList<>(uniqueResources));
            isLoading = false;
            isLoadingMore = false;
        });
    }

    private void loadMoreIfNeeded() {
        if (!hasMore || isLoadingMore || isLoading) return;
        if (smoothOffset + (this.height - 60) >= resources.size() * entryHeight - entryHeight) {
            isLoadingMore = true;
            loadedCount += 30;
            loadResourcesAsync(currentSearch, false);
        }
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (searchField.charTyped(chr, keyCode)) {
            return true;
        }
        return super.charTyped(chr, keyCode);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraftClient.setScreen(parent);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            resources.clear();
            loadResourcesAsync(currentSearch, true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        targetOffset -= verticalAmount * entryHeight * 2;
        targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, resources.size() * entryHeight - (this.height - 60))));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int searchButtonX = this.width - 120;
            int searchButtonY = 30;
            int searchButtonW = 50;
            int searchButtonH = 20;
            if (mouseX >= searchButtonX && mouseX <= searchButtonX + searchButtonW && mouseY >= searchButtonY && mouseY <= searchButtonY + searchButtonH) {
                resources.clear();
                loadResourcesAsync(currentSearch, true);
                return true;
            }
            int backButtonX = this.width - 60;
            int backButtonY = 5;
            int btnW = 50;
            int btnH = 20;
            if (mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH) {
                minecraftClient.setScreen(parent);
                return true;
            }
            int listStartY = 60;
            if (mouseY >= listStartY && mouseY <= this.height - 20) {
                int relativeY = (int) mouseY - listStartY + (int) smoothOffset;
                int index = relativeY / entryHeight;
                if (index >= 0 && index < resources.size()) {
                    ModrinthResource selected = resources.get(index);
                    minecraftClient.setScreen(new PluginModResourceScreen(minecraftClient, this, selected, serverInfo));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, 25, lighterColor);
        drawInnerBorder(context, 0, 0, this.width, 25, borderColor);
        context.drawText(this.textRenderer, Text.literal("Plugin / Mod Manager"), 10, 10, textColor, false);
        searchField.render(context, mouseX, mouseY, delta);

        int searchButtonX = this.width - 120;
        int searchButtonY = 30;
        int searchButtonW = 50;
        int searchButtonH = 20;
        boolean hoveredSearch = mouseX >= searchButtonX && mouseX <= searchButtonX + searchButtonW && mouseY >= searchButtonY && mouseY <= searchButtonY + searchButtonH;
        int bgSearch = hoveredSearch ? highlightColor : lighterColor;
        context.fill(searchButtonX, searchButtonY, searchButtonX + searchButtonW, searchButtonY + searchButtonH, bgSearch);
        drawInnerBorder(context, searchButtonX, searchButtonY, searchButtonW, searchButtonH, borderColor);
        int twSearch = this.textRenderer.getWidth("Search");
        int txSearch = searchButtonX + (searchButtonW - twSearch) / 2;
        int tySearch = searchButtonY + (searchButtonH - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Search"), txSearch, tySearch, textColor, false);

        int backButtonX = this.width - 60;
        int backButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH;
        int bgBack = hoveredBack ? highlightColor : lighterColor;
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack);
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, borderColor);
        int tw = this.textRenderer.getWidth("Back");
        int tx = backButtonX + (btnW - tw) / 2;
        int ty = backButtonY + (btnH - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Back"), tx, ty, textColor, false);

        if (isLoading && resources.isEmpty()) {
            context.drawText(this.textRenderer, Text.literal("Loading..."), 10, 55, 0xFF00FF, false);
            return;
        }

        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;
        int listStartY = 60;
        int listEndY = this.height - 20;
        int panelWidth = this.width - 20;

        context.enableScissor(0, listStartY, this.width, listEndY);

        int visibleEntries = (listEndY - listStartY) / entryHeight;
        int startIndex = Math.max(0, (int) Math.floor(smoothOffset / entryHeight));
        int endIndex = Math.min(startIndex + visibleEntries + 2, resources.size());

        for (int i = startIndex; i < endIndex; i++) {
            ModrinthResource resource = resources.get(i);
            int y = listStartY + (i * entryHeight) - (int) smoothOffset;
            if (y + entryHeight < listStartY || y > listEndY) continue;
            boolean hovered = mouseX >= 0 && mouseX <= this.width && mouseY >= y && mouseY <= y + entryHeight;
            int bg = hovered ? highlightColor : lighterColor;
            context.fill(10, y, panelWidth + 10, y + entryHeight, bg);
            drawInnerBorder(context, 10, y, panelWidth, entryHeight, borderColor);

            if (!resource.iconUrl.isEmpty()) {
                if (!iconTextures.containsKey(resource.iconUrl)) {
                    String texKey = "img_" + Integer.toHexString(resource.iconUrl.hashCode());
                    Identifier textureId = Identifier.tryParse("oxy_mod:" + texKey);
                    if (textureId != null) {
                        iconTextures.put(resource.iconUrl, textureId);
                        CompletableFuture.runAsync(() -> {
                            try (InputStream inputStream = new URL(resource.iconUrl).openStream()) {
                                NativeImage nativeImage = loadImage(inputStream, resource.iconUrl);
                                minecraftClient.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(nativeImage));
                            } catch (Exception ignored) {}
                        });
                    }
                }
                Identifier textureId = iconTextures.get(resource.iconUrl);
                if (textureId != null) {
                    minecraftClient.getTextureManager().bindTexture(textureId);
                    context.drawTexture(textureId, 15, y + 5, 0, 0, 40, 40, 40, 40);
                }
            }

            int infoX = this.width - 20 - this.textRenderer.getWidth(formatDownloads(resource.downloads) + " | " + resource.version);
            String displayDesc = resource.description;
            int descMaxWidth = infoX - 65;
            if (this.textRenderer.getWidth(displayDesc) > descMaxWidth) {
                while (this.textRenderer.getWidth(displayDesc + "...") > descMaxWidth && displayDesc.length() > 0) {
                    displayDesc = displayDesc.substring(0, displayDesc.length() - 1);
                }
                displayDesc += "...";
            }

            context.drawText(this.textRenderer, Text.literal(resource.name), 60, y + 5, textColor, false);
            context.drawText(this.textRenderer, Text.literal(displayDesc), 60, y + 20, 0xFFAAAAAA, false);
            context.drawText(this.textRenderer, Text.literal(formatDownloads(resource.downloads) + " | " + resource.version), infoX, y + 20, 0xFFAAAAAA, false);
        }

        context.disableScissor();

        if (smoothOffset > 0) {
            context.fillGradient(0, listStartY, this.width, listStartY + 10, 0x80000000, 0x00000000);
        }
        int maxScroll = Math.max(0, resources.size() * entryHeight - (listEndY - listStartY));
        if (smoothOffset < maxScroll) {
            context.fillGradient(0, listEndY - 10, this.width, listEndY, 0x00000000, 0x80000000);
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

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }

    private NativeImage loadImage(InputStream inputStream, String url) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = inputStream.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        inputStream.close();
        return NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));
    }
}
