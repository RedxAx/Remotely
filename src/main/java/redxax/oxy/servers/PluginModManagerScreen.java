package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.concurrent.*;

import javax.imageio.ImageIO;

import com.google.gson.JsonParser;
import redxax.oxy.SSHManager;

import static redxax.oxy.ImageUtil.*;

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

    private BufferedImage installIcon;
    private BufferedImage installingIcon;
    private BufferedImage installedIcon;

    private BufferedImage loadingAnim;
    private List<BufferedImage> loadingFrames = new ArrayList<>();
    private int currentLoadingFrame = 0;
    private long lastFrameTime = 0;

    private final ExecutorService imageLoader = Executors.newFixedThreadPool(4);
    private final BufferedImage placeholderIcon = createPlaceholderIcon();

    public PluginModManagerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal(getTitle(info)));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
    }

    private static String getTitle(ServerInfo info) {
        if (info.isModServer()) {
            return "Mod Manager";
        } else if (info.isPluginServer()) {
            return "Plugin Manager";
        } else {
            return "Modpack Manager";
        }
    }

    @Override
    protected void init() {
        super.init();
        searchField = new TextFieldWidget(this.textRenderer, 10, 30, this.width - 140, 20, Text.literal(""));
        searchField.setMaxLength(100);
        searchField.setChangedListener(text -> currentSearch = text);
        this.addDrawableChild(searchField);
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

    private BufferedImage createPlaceholderIcon() {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(new Color(0xFF555555, true));
        g2d.fillRect(0, 0, 40, 40);
        g2d.dispose();
        return img;
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
        CompletableFuture<List<ModrinthResource>> searchFuture;
        String serverVersion = serverInfo.getVersion();
        int limit = 30;
        if (serverInfo.isModServer()) {
            searchFuture = ModrinthAPI.searchMods(query, serverVersion, limit, loadedCount, serverInfo.type);
        } else if (serverInfo.isPluginServer()) {
            searchFuture = ModrinthAPI.searchPlugins(query, serverVersion, limit, loadedCount, serverInfo.type);
        } else {
            searchFuture = ModrinthAPI.searchModpacks(query, serverVersion, limit, loadedCount);
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
            resourceCache.put(query + "_" + loadedCount, new ArrayList<>(uniqueResources));
            isLoading = false;
            isLoadingMore = false;
            uniqueResources.forEach(resource -> {
                if (!resource.iconUrl.isEmpty() && !iconImages.containsKey(resource.iconUrl)) {
                    imageLoader.submit(() -> {
                        try (InputStream inputStream = new URL(resource.iconUrl).openStream()) {
                            BufferedImage bufferedImage = loadImage(inputStream, resource.iconUrl);
                            if (bufferedImage != null) {
                                iconImages.put(resource.iconUrl, bufferedImage);
                                BufferedImage scaled = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D g2d = scaled.createGraphics();
                                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                g2d.drawImage(bufferedImage, 0, 0, 40, 40, null);
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
                    int iconX = 15;
                    int iconY = listStartY + (index * entryHeight) - (int) smoothOffset + 5;
                    int iconSize = 40;
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, 25, lighterColor);
        drawInnerBorder(context, 0, 0, this.width, 25, borderColor);
        context.drawText(this.textRenderer, Text.literal(getTitle(serverInfo)), 10, 10, textColor, false);
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

            BufferedImage scaledImage;
            if (!resource.iconUrl.isEmpty()) {
                scaledImage = scaledIcons.getOrDefault(resource.iconUrl, placeholderIcon);
            } else {
                scaledImage = placeholderIcon;
            }
            drawBufferedImage(context, scaledImage, 15, y + 5, 40, 40);

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

            int iconX = 15;
            int iconY = y + 5;
            int iconSize = 40;
            boolean isHoveringInstall = mouseX >= iconX && mouseX <= iconX + iconSize && mouseY >= iconY && mouseY <= iconY + iconSize;
            if (isHoveringInstall) {
                context.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x80000000);
                BufferedImage buttonIcon = installIcon;
                String status = installButtonTexts.getOrDefault(resource.slug, "Install");
                if (status.equals("Installing")) {
                    buttonIcon = installingIcon;
                } else if (status.equals("Installed")) {
                    buttonIcon = installedIcon;
                }
                if (buttonIcon != null) {
                    drawBufferedImage(context, buttonIcon, iconX, iconY, iconSize, iconSize);
                }
            }
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
                ProcessBuilder pb = new ProcessBuilder(
                        exePath,
                        resource.projectId,
                        resource.version,
                        "--server-dir",
                        serverDir,
                        "--server-file",
                        "server.jar"
                );
                pb.directory(serverPath.toFile());
                Process proc = pb.start();

                ExecutorService executor = Executors.newFixedThreadPool(2);
                executor.submit(() -> {
                    try (InputStream is = proc.getInputStream()) {
                        is.transferTo(System.out);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                executor.submit(() -> {
                    try (InputStream is = proc.getErrorStream()) {
                        is.transferTo(System.err);
                    } catch (IOException e) {
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
            } catch (IOException | InterruptedException e) {
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
                HttpClient httpClient = HttpClient.newBuilder()
                        .executor(imageLoader)
                        .build();
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
}
