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
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

public class PluginModManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private final List<ModrinthResource> resources = new ArrayList<>();
    private final Map<String, List<ModrinthResource>> resourceCache = new HashMap<>();
    private final Map<String, Identifier> iconTextures = new HashMap<>();
    private TextFieldWidget searchField;
    private int entryHeight = 50;
    private boolean isLoading = false;
    private boolean isLoadingMore = false;
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
        loadResources("", true);
    }

    private void loadResources(String query, boolean reset) {
        if (reset) {
            loadedCount = 0;
            resources.clear();
        }
        if (resourceCache.containsKey(query + "_" + loadedCount)) {
            synchronized (resources) {
                resources.addAll(resourceCache.get(query + "_" + loadedCount));
            }
            return;
        }
        isLoading = true;
        if (reset) hasMore = true;
        new Thread(() -> {
            List<ModrinthResource> fetched = ModrinthAPI.searchResources(query, serverInfo, 30, loadedCount);
            if (fetched.size() < 30) hasMore = false;
            Set<String> seenSlugs = new HashSet<>();
            List<ModrinthResource> uniqueResources = new ArrayList<>();
            for (ModrinthResource r : fetched) {
                if (!seenSlugs.contains(r.fileName)) {
                    seenSlugs.add(r.fileName);
                    uniqueResources.add(r);
                }
            }
            synchronized (resources) {
                resources.addAll(uniqueResources);
            }
            resourceCache.put(query + "_" + loadedCount, new ArrayList<>(uniqueResources));
            isLoading = false;
            isLoadingMore = false;
        }).start();
    }

    private void loadMoreIfNeeded() {
        if (!hasMore || isLoadingMore || isLoading) return;
        if (smoothOffset + (this.height - 60) >= resources.size() * entryHeight - entryHeight) {
            isLoadingMore = true;
            loadedCount += 30;
            loadResources(currentSearch, false);
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
            loadResources(currentSearch, true);
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
                loadResources(currentSearch, true);
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

        if (isLoading) {
            context.drawText(this.textRenderer, Text.literal("Loading..."), 10, 55, 0xFF00FF, false);
            return;
        }
        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;
        int listStartY = 60;
        int listEndY = this.height - 20;
        int visibleEntries = (listEndY - listStartY) / entryHeight;
        int startIndex = (int) Math.floor(smoothOffset / entryHeight);
        int endIndex = startIndex + visibleEntries + 2;
        if (endIndex > resources.size()) endIndex = resources.size();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        double scale = minecraftClient.getWindow().getScaleFactor();
        int scissorX = 10;
        int scissorY = (int)((minecraftClient.getWindow().getScaledHeight() - listEndY) * scale);
        int scissorW = (int)((this.width - 20) * scale);
        int scissorH = (int)((listEndY - listStartY) * scale);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        for (int i = startIndex; i < endIndex; i++) {
            ModrinthResource resource = resources.get(i);
            int y = listStartY + (i * entryHeight) - (int) smoothOffset;
            if (y + entryHeight < listStartY || y > listEndY) continue;
            boolean hovered = mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= y && mouseY <= y + entryHeight;
            int bg = hovered ? highlightColor : lighterColor;
            context.fill(10, y, this.width - 10, y + entryHeight, bg);
            drawInnerBorder(context, 10, y, this.width - 20, entryHeight, borderColor);

            if (!resource.iconUrl.isEmpty()) {
                if (!iconTextures.containsKey(resource.iconUrl)) {
                    String texKey = "img_" + Integer.toHexString(resource.iconUrl.hashCode());
                    Identifier textureId = Identifier.tryParse("oxy_mod:" + texKey);
                    iconTextures.put(resource.iconUrl, textureId);
                    try (InputStream inputStream = new URL(resource.iconUrl).openStream()) {
                        NativeImage nativeImage = NativeImage.read(inputStream);
                        minecraftClient.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(nativeImage));
                    } catch (IOException e) {
                        System.err.println("Failed to load image from URL: " + resource.iconUrl);
                        e.printStackTrace();
                    }
                }
                Identifier textureId = iconTextures.get(resource.iconUrl);
                minecraftClient.getTextureManager().bindTexture(textureId);
                context.drawTexture(textureId, 15, y + 5, 0, 0, 40, 40, 40, 40);
            }

            String displayDesc = resource.description;
            int infoX = this.width - 20 - this.textRenderer.getWidth(formatDownloads(resource.downloads) + " | " + resource.version);
            context.drawText(this.textRenderer, Text.literal(resource.name), 60, y + 5, textColor, false);
            context.drawText(this.textRenderer, Text.literal(displayDesc), 60, y + 20, 0xFFAAAAAA, false);
            context.drawText(this.textRenderer, Text.literal(formatDownloads(resource.downloads) + " | " + resource.version), infoX, y + 20, 0xFFAAAAAA, false);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (smoothOffset > 0) {
            context.fillGradient(10, listStartY, this.width - 10, listStartY + 10, 0x80000000, 0x00000000);
        }
        if (smoothOffset < resources.size() * entryHeight - (listEndY - listStartY)) {
            context.fillGradient(10, listEndY - 10, this.width - 10, listEndY, 0x00000000, 0x80000000);
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
}
