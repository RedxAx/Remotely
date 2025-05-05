package redxax.oxy.remotely.servers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dediamondpro.minemark.minecraft.MineMarkDrawable;
import org.lwjgl.glfw.GLFW;
import org.xml.sax.SAXException;
import redxax.oxy.remotely.Render;
import redxax.oxy.remotely.Render.ScrollBar;
import redxax.oxy.remotely.api.IRemotelyResource;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.util.ImageUtil.IconWithTooltip;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.servers.PluginModManagerScreen.formatDownloads;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.remotely.util.Sound;

public class ResourcePageScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final PluginModManagerScreen parentScreen;
    private final IRemotelyResource resource;
    private boolean isLoadingMarkdown = true;
    private float descScrollOffset = 0;
    private float descTargetScrollOffset = 0;
    private float versionsScrollOffset = 0;
    private float versionsTargetScrollOffset = 0;
    private List<Tab> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private List<Version> versions = new ArrayList<>();
    private List<VersionButtonRegion> versionButtonRegions = new ArrayList<>();
    private static ServerInfo serverInfo;
    private boolean isDownloadingMrpack = false;
    private IconWithTooltip closeIcon, siteIcon, downloadIcon;
    private MineMarkDrawable mineMarkDrawable;

    public ResourcePageScreen(MinecraftClient mc, PluginModManagerScreen parent, IRemotelyResource resource, ServerInfo serverInfo) {
        super(Text.literal(resource.getName()));
        this.minecraftClient = mc;
        this.parentScreen = parent;
        this.resource = resource;
        ResourcePageScreen.serverInfo = serverInfo;
        loadMarkdown();
        init();
        fetchVersions();
        originalMCScale = minecraftClient.getWindow().getScaleFactor();
        targetScaleFactor = globalScaleFactor;
        minecraftClient.getWindow().setScaleFactor(globalScaleFactor);
    }

    public void init() {
        tabs.clear();
        tabs.add(new Tab(TabType.DESCRIPTION, "Description"));
        tabs.add(new Tab(TabType.VERSIONS, "Versions"));
        currentTabIndex = 0;
        try {
            closeIcon = new IconWithTooltip("/assets/remotely/icons/close.png", "");
            siteIcon = new IconWithTooltip("/assets/remotely/icons/site.png", "Open The Resource's Page In Your Default Browser.");
            downloadIcon = new IconWithTooltip("/assets/remotely/icons/download.png", "Download The Latest Compatible Version.");
        } catch (Exception e) {
            devPrint("Failed to load icons: " + e.getMessage());
        }
    }

    private void loadMarkdown() {
        new Thread(() -> {
            String markdownContent = "";
            try {
                String url;
                if (resource.getSlug().startsWith("spigot_")) {
                    url = "https://api.spiget.org/v2/resources/" + resource.getProjectId();
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestProperty("User-Agent", "Remotely");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    InputStream in = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int ch;
                    while ((ch = in.read()) != -1) {
                        sb.append((char) ch);
                    }
                    in.close();
                    markdownContent = new String(Base64.getDecoder().decode(JsonParser.parseString(sb.toString()).getAsJsonObject().get("description").getAsString()));
                } else if (resource.getSlug().startsWith("hangar_")) {
                    url = "https://hangar.papermc.io/api/v1/pages/main/" + resource.getProjectId();
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestProperty("User-Agent", "Remotely");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    InputStream in = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int ch;
                    while ((ch = in.read()) != -1) {
                        sb.append((char) ch);
                    }
                    in.close();
                    markdownContent = sb.toString();
                } else {
                    url = "https://api.modrinth.com/v2/project/" + resource.getProjectId();
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestProperty("User-Agent", "Remotely");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    InputStream in = conn.getInputStream();
                    InputStreamReader reader = new InputStreamReader(in);
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    markdownContent = obj.has("body") ? obj.get("body").getAsString() : "";
                    in.close();
                }
            } catch (Exception e) {
                markdownContent = resource.getDescription();
            }
            try {
                mineMarkDrawable = new MineMarkDrawable(markdownContent);
            } catch (Exception e) {
                try {
                    mineMarkDrawable = new MineMarkDrawable(resource.getDescription());
                } catch (IOException | SAXException ex) {
                    devPrint("Failed to load markdown: " + ex.getMessage());
                }
            }
            isLoadingMarkdown = false;
            minecraftClient.execute(() -> {});
        }).start();
    }

    private void fetchVersions() {
        new Thread(() -> {
            List<Version> fetched = new ArrayList<>();
            try {
                String url;
                if (resource.getSlug().startsWith("spigot_")) {
                    url = "https://api.spiget.org/v2/resources/" + resource.getProjectId() + "/versions?size=10000&sort=-releaseDate";
                } else if (resource.getSlug().startsWith("hangar_")) {
                    int offset = 0;
                    url = "https://hangar.papermc.io/api/v1/projects/" + resource.getProjectId()
                            + "/versions?includeHiddenChannels=true&channel=Release&platform=PAPER&limit=25&offset=" + offset;
                } else {
                    url = "https://api.modrinth.com/v2/project/" + resource.getProjectId() + "/version";
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "Remotely");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                InputStream in = conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                int ch;
                while ((ch = in.read()) != -1) {
                    sb.append((char) ch);
                }
                in.close();
                JsonElement elem = JsonParser.parseString(sb.toString());
                JsonArray arr = null;
                if (resource.getSlug().startsWith("hangar_")) {
                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("result")) {
                            arr = obj.getAsJsonArray("result");
                        }
                    }
                } else {
                    if (elem.isJsonArray()) {
                        arr = elem.getAsJsonArray();
                    } else if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("versions")) {
                            arr = obj.getAsJsonArray("versions");
                        }
                    }
                }
                if (arr != null) {
                    for (JsonElement je : arr) {
                        JsonObject verObj = je.getAsJsonObject();
                        String verNum = verObj.has("version_number") ? verObj.get("version_number").getAsString() : (verObj.has("name") ? verObj.get("name").getAsString() : "Unknown");
                        String mcVersions;
                        if (resource.getSlug().startsWith("spigot_")) {
                            mcVersions = verObj.has("testedVersions") ? verObj.get("testedVersions").getAsString() : "";
                        } else if (resource.getSlug().startsWith("hangar_")) {
                            mcVersions = verObj.has("gameVersion") ? verObj.get("gameVersion").getAsString() : "Unknown";
                        } else {
                            if (verObj.has("game_versions") && verObj.get("game_versions").isJsonArray()) {
                                JsonArray gameVersionsArray = verObj.getAsJsonArray("game_versions");
                                List<String> versionsList = new ArrayList<>();
                                for (JsonElement v : gameVersionsArray) {
                                    versionsList.add(v.getAsString());
                                }
                                mcVersions = String.join(", ", versionsList);
                            } else {
                                mcVersions = "Unknown";
                            }
                        }
                        String dateUploaded;
                        if (resource.getSlug().startsWith("spigot_")) {
                            dateUploaded = verObj.has("releaseDate") ? verObj.get("releaseDate").getAsString() : "Unknown";
                        } else {
                            dateUploaded = verObj.has("createdAt") ? verObj.get("createdAt").getAsString() : (verObj.has("date_published") ? verObj.get("date_published").getAsString() : "Unknown");
                        }
                        String fileUrl = "";
                        if (resource.getSlug().startsWith("spigot_")) {
                            int versionId = verObj.has("id") ? verObj.get("id").getAsInt() : 0;
                            fileUrl = "https://api.spiget.org/v2/resources/" + resource.getProjectId() + "/download?version=" + versionId;
                        } else if (resource.getSlug().startsWith("hangar_")) {
                            if (verObj.has("downloads")) {
                                JsonObject downloads = verObj.getAsJsonObject("downloads");
                                if (downloads.has("PAPER")) {
                                    JsonObject paperObj = downloads.getAsJsonObject("PAPER");
                                    fileUrl = paperObj.has("downloadUrl") ? paperObj.get("downloadUrl").getAsString() : "";
                                }
                            }
                        } else if (verObj.has("files")) {
                            JsonArray files = verObj.getAsJsonArray("files");
                            if (!files.isEmpty()) {
                                JsonObject fileObj = files.get(0).getAsJsonObject();
                                fileUrl = fileObj.has("url") ? fileObj.get("url").getAsString() : "";
                            }
                        } else if (verObj.has("downloadUrl")) {
                            fileUrl = verObj.get("downloadUrl").getAsString();
                        }
                        int downloadsCount = 0;
                        if (resource.getSlug().startsWith("hangar_")) {
                            if (verObj.has("stats")) {
                                JsonObject stats = verObj.getAsJsonObject("stats");
                                downloadsCount = stats.has("totalDownloads") ? stats.get("totalDownloads").getAsInt() : 0;
                            }
                        } else {
                            downloadsCount = verObj.has("downloads") ? verObj.get("downloads").getAsInt() : 0;
                        }
                        fetched.add(new Version(verNum, mcVersions, dateUploaded, fileUrl, downloadsCount));
                    }
                }
            } catch (Exception e) {
                devPrint("Failed to fetch versions: " + e.getMessage());
            }
            versions = fetched;
            minecraftClient.execute(() -> {});
        }).start();
    }

    private Version getLatestCompatibleVersion() {
        if (versions == null || versions.isEmpty()) return null;
        for (Version ver : versions) {
            if (ver.mcVersions.contains(serverInfo.version) && ver.fileUrl.toLowerCase().contains(serverInfo.type.toLowerCase())) {
                return ver;
            }
        }
        for (Version ver : versions) {
            if (ver.mcVersions.contains(serverInfo.version)) {
                return ver;
            }
        }
        return versions.get(0);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, /*? !=1.20.1 {*/ double horizontalAmount, /*?}*/ double verticalAmount) {
        scaleScroll(verticalAmount);
        int headerHeight = 30;
        int tabAreaHeight = 20;
        int contentY = headerHeight + tabAreaHeight + 10;
        int contentHeight = this.height - contentY - 5;
        if (mouseY >= contentY && mouseY <= contentY + contentHeight) {
            if (getCurrentTabType() == TabType.DESCRIPTION && mineMarkDrawable != null) {
                descTargetScrollOffset -= (float) (verticalAmount * 30);
                int totalMarkdownHeight = (int) mineMarkDrawable.getHeight();
                int max = Math.max(0, totalMarkdownHeight + 20 - contentHeight);
                descTargetScrollOffset = Math.max(0, Math.min(descTargetScrollOffset, max));
            } else if (getCurrentTabType() == TabType.VERSIONS) {
                int itemHeight = 35;
                int gap = 2;
                int total = versions.size() * (itemHeight + gap);
                versionsTargetScrollOffset -= (float) (verticalAmount * 30);
                int max = Math.max(0, total - contentHeight);
                versionsTargetScrollOffset = Math.max(0, Math.min(versionsTargetScrollOffset, max));
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (getCurrentTabType() == TabType.DESCRIPTION) {
            if (ScrollBar.handleMouseDragged(this, (int) mouseY, mineMarkDrawable != null ? (int) (mineMarkDrawable.getHeight() + 20f) : 0)) {
                return true;
            }
        } else if (getCurrentTabType() == TabType.VERSIONS) {
            int totalVersionHeight = versions.size() * (35 + 2);
            if (ScrollBar.handleMouseDragged(this, (int) mouseY, totalVersionHeight)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ScrollBar.handleMouseReleased()){
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int totalVersionHeight = versions.size() * (35 + 2);
        if (ScrollBar.handleMousePressed(this, (int) mouseX, (int) mouseY,
            getCurrentTabType() == TabType.DESCRIPTION ? (int) (mineMarkDrawable != null ? mineMarkDrawable.getHeight() + 20 : 0) : totalVersionHeight,
            getCurrentTabType() == TabType.DESCRIPTION ? descScrollOffset : versionsScrollOffset)) {
            return true;
        }
        int tabBarY = 35;
        int tabBarHeight = 18;
        if (mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight) {
            int tabBarX = 5;
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                int tabWidth = minecraftClient.textRenderer.getWidth(t.name) + 10;
                if (mouseX >= tabBarX && mouseX <= tabBarX + tabWidth) {
                    playSound(Sound.SWITCHTAB);
                    currentTabIndex = i;
                    return true;
                }
                tabBarX += tabWidth + 5;
            }
        }
        if (mouseX >= width - 69 && mouseX <= width - 52 && mouseY >= 6 && mouseY <= 24) {
            playSound(Sound.CLICK);
            if (resource.getFileName().toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
                downloadMrpackResource();
            } else {
                Version compVersion = getLatestCompatibleVersion();
                if (compVersion != null) {
                    downloadVersionResource(compVersion);
                }
            }
            return true;
        }
        if (mouseX >= width - 46 && mouseX <= width - 29 && mouseY >= 6 && mouseY <= 24) {
            playSound(Sound.CLICK);
            String siteUrl = getCurrentTabType() == TabType.DESCRIPTION
                    ? getSiteUrlForResource()
                    : getSiteUrlForResource() + (resource.getSlug().startsWith("spigot_") ? "/history" : "/versions");
            if (!siteUrl.isEmpty()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", siteUrl);
                    pb.start();
                } catch (Exception e) {
                    devPrint("Failed to open browser: " + e.getMessage());
                }
            }
            return true;
        }
        if (mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24) {
            minecraftClient.setScreen(parentScreen);
            return true;
        }
        if (getCurrentTabType() == TabType.DESCRIPTION && mineMarkDrawable != null) {
            mineMarkDrawable.onMouseClicked(15, 70 - descScrollOffset, (float) mouseX, (float) mouseY, button);
            playSound(Sound.CLICK);
            return true;
        }
        if (getCurrentTabType() == TabType.VERSIONS) {
            for (VersionButtonRegion vr : versionButtonRegions) {
                if (mouseX >= vr.x && mouseX <= vr.x + vr.width && mouseY >= vr.y && mouseY <= vr.y + vr.height) {
                    playSound(Sound.CLICK);
                    if (resource.getFileName().toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
                        downloadMrpackResource();
                    } else {
                        downloadVersionResource(vr.version);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraftClient.setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private TabType getCurrentTabType() {
        return tabs.get(currentTabIndex).type;
    }

    private String getSiteUrlForResource() {
        String projectId = resource.getProjectId();
        if (resource.getSlug().startsWith("spigot_")) {
            return "https://www.spigotmc.org/resources/" + projectId;
        } else if (resource.getSlug().startsWith("hangar_")) {
            return "https://hangar.papermc.io/" + resource.getAuthor() + "/" + projectId;
        } else {
            return "https://modrinth.com/mod/" + projectId;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int headerHeight = 30;
        int tabAreaHeight = 20;
        drawScreenHeader(context, width, height, width - 5, mouseX, mouseY, this, minecraftClient, closeIcon, siteIcon, downloadIcon, null, null, null, null, null, null);
        context.drawText(minecraftClient.textRenderer, Text.literal(resource.getName()), 10, 10, globalTextColor, Config.shadow);
        drawTabs(context, minecraftClient.textRenderer, tabs, currentTabIndex, mouseX, mouseY, false, false);
        int contentY = headerHeight + tabAreaHeight + 10;
        int contentHeight = this.height - contentY - 5;
        int contentX = 5;
        int contentWidth = this.width - 10;
        if (getCurrentTabType() == TabType.DESCRIPTION) {
            if (isLoadingMarkdown) {
                drawLoading(context, this.height, this.width);
                return;
            }
            descScrollOffset += (descTargetScrollOffset - descScrollOffset) * globalScrollSpeed * deltaTime;
            context.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
            mineMarkDrawable.draw(contentX + 10, contentY + 10 - (int) descScrollOffset, contentWidth - 20, mouseX, mouseY, context);
            context.disableScissor();
        } else if (getCurrentTabType() == TabType.VERSIONS) {
            versionButtonRegions.clear();
            versionsScrollOffset += (versionsTargetScrollOffset - versionsScrollOffset) * globalScrollSpeed * deltaTime;
            int itemHeight = 35;
            context.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
            for (int i = 0; i < versions.size(); i++) {
                Version ver = versions.get(i);
                int y = contentY + i * (itemHeight + 2) - (int) versionsScrollOffset;
                if (y + itemHeight < contentY || y > contentY + contentHeight) continue;
                boolean hovered = mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= y && mouseY < y + itemHeight;
                int bg = getElementBackgroundColor(ver.hashCode(), hovered, false, true, false, false, false);
                int borderColor = getElementBorderColor(ver.hashCode(), hovered, false, true, false, false, false);
                context.fill(contentX, y, contentX + contentWidth, y + itemHeight, bg);
                drawInnerBorder(context, contentX, y, contentWidth, itemHeight, borderColor);
                drawOuterBorder(context, contentX, y, contentWidth, itemHeight, bg);
                String title = resource.getName() + ": " + ver.version;
                context.drawText(minecraftClient.textRenderer, Text.literal(title), contentX + 4, y + 3, 0xFFFFFFFF, Config.shadow);
                String desc = formatMCVersions(ver.mcVersions);
                context.drawText(minecraftClient.textRenderer, Text.literal(desc), contentX + 4, y + 15, 0xFFAAAAAA, Config.shadow);
                String subDesc = getRelativeTime(ver.dateUploaded) + " | " + formatDownloads(ver.downloads) + " Downloads";
                context.drawText(minecraftClient.textRenderer, Text.literal(subDesc), contentX + 4, y + 26, 0xFF777777, Config.shadow);
                if (ver.isDownloading) {
                    int barWidth = Render.buttonW;
                    int barHeight = Render.buttonH;
                    int barX = contentX + contentWidth - barWidth - 10;
                    int barY = y + (itemHeight - barHeight) / 2;
                    int bgColor = getElementBackgroundColor(ver.hashCode(), hovered, true, true, false, false, false);
                    context.fill(barX, barY, barX + barWidth, barY + barHeight, bgColor);
                    int fillWidth = (int) (barWidth * ver.progress);
                    context.fill(barX, barY, barX + fillWidth, barY + barHeight, globalHoverTextColor);
                    drawOuterBorder(context, barX, barY, barWidth, barHeight, bgColor);
                    drawInnerBorder(context, barX, barY, barWidth, barHeight, getElementBorderColor(ver.hashCode(), hovered, true, true, false, false, false));
                    String percentText = (int) (ver.progress * 100) + "%";
                    context.drawText(minecraftClient.textRenderer, Text.literal(percentText), barX + barWidth / 2 - minecraftClient.textRenderer.getWidth(Text.literal(percentText)) / 2, barY + (barHeight - minecraftClient.textRenderer.fontHeight) / 2, 0xFFFFFFFF, Config.shadow);
                    String infoText = formatBytes(ver.downloadedBytes) + "/" + formatBytes(ver.totalBytes) + " | " + formatBytes((long) ver.speed) + "/s";
                    context.drawText(minecraftClient.textRenderer, Text.literal(infoText), barX - 5 - minecraftClient.textRenderer.getWidth(Text.literal(infoText)), barY + (barHeight - minecraftClient.textRenderer.fontHeight) / 2, 0xFFCCCCCC, Config.shadow);
                } else {
                    int btnX = contentX + contentWidth - 70;
                    int btnY = y + (itemHeight - 20) / 2;
                    drawCustomButton(context, btnX, btnY, ver.isInstalled, minecraftClient, mouseX >= btnX && mouseX <= btnX + 60 && mouseY >= btnY && mouseY <= btnY + 20, false, true, false, true, 60, 20, Objects.equals(ver.isInstalled, "Failed") ? Config.dangerDarkAccentColor : Objects.equals(ver.isInstalled, "Installed") ? Config.niceAccentColor : globalTextColor, globalHoverTextColor, mouseX, mouseY, "");
                    versionButtonRegions.add(new VersionButtonRegion(btnX, btnY, 60, 20, ver));
                }
            }
            context.disableScissor();
        }
        ScrollBar.render(context, this, mouseX, mouseY, getCurrentTabType() == TabType.DESCRIPTION ? (int) (mineMarkDrawable != null ? mineMarkDrawable.getHeight() + 20 : 0) : versions.size() * (35 + 2), getCurrentTabType() == TabType.DESCRIPTION ? descTargetScrollOffset : versionsTargetScrollOffset);
        if (ScrollBar.isDragging()) {
            if (getCurrentTabType() == TabType.DESCRIPTION) {
                descTargetScrollOffset = ScrollBar.getPendingOffset();
            } else if (getCurrentTabType() == TabType.VERSIONS) {
                versionsTargetScrollOffset = ScrollBar.getPendingOffset();
            }
        }
        animatedScaling(this);
    }

    private String getRelativeTime(String dateStr) {
        try {
            Instant uploaded;
            if (dateStr.matches("\\d+")) {
                long epoch = Long.parseLong(dateStr);
                uploaded = Instant.ofEpochSecond(epoch);
            } else {
                uploaded = Instant.parse(dateStr);
            }
            Duration duration = Duration.between(uploaded, Instant.now());
            long days = duration.toDays();
            if (days < 1) return "Today";
            if (days < 30) return days + " Days Ago";
            long months = days / 30;
            return months + " Months Ago";
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatMCVersions(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split(",\\s*");
        Map<String, List<Integer>> groups = new HashMap<>();
        List<String> others = new ArrayList<>();
        for (String ver : parts) {
            String trimmed = ver.trim();
            String[] nums = trimmed.split("\\.");
            if (nums.length >= 2) {
                String key = nums[0] + "." + nums[1];
                int patch = 0;
                if (nums.length >= 3) {
                    try {
                        patch = Integer.parseInt(nums[2]);
                    } catch (Exception ignored) {}
                }
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(patch);
            } else {
                others.add(trimmed);
            }
        }
        List<String> results = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            List<Integer> patches = entry.getValue();
            Collections.sort(patches);
            if (patches.size() >= 3 && patches.get(patches.size() - 1) - patches.get(0) == patches.size() - 1) {
                results.add(entry.getKey() + ".x");
            } else if (patches.size() >= 2) {
                String first = entry.getKey() + "." + patches.get(0);
                String last = entry.getKey() + "." + patches.get(patches.size() - 1);
                results.add(first + " - " + last);
            } else {
                results.add(entry.getKey() + (patches.size() == 1 ? "." + patches.get(0) : ""));
            }
        }
        results.addAll(others);
        return String.join(", ", results);
    }

    private void downloadVersionResource(Version ver) {
        new Thread(() -> {
            try {
                if (ver.fileUrl.isEmpty()) {
                    ver.isInstalled = "Failed";
                    return;
                }
                if (serverInfo.isRemote && serverInfo.remoteSSHManager != null) {
                    URL url = new URL(ver.fileUrl);
                    HttpURLConnection headConn = (HttpURLConnection) url.openConnection();
                    headConn.setRequestProperty("User-Agent", "Remotely");
                    headConn.setRequestMethod("HEAD");
                    headConn.setConnectTimeout(5000);
                    headConn.setReadTimeout(5000);
                    int total = headConn.getContentLength();
                    ver.totalBytes = total;
                    String remoteDir = serverInfo.path + File.separator + (serverInfo.isModServer() ? "mods" : serverInfo.isPluginServer() ? "plugins" : "");
                    String remotePath = remoteDir + File.separator + resource.getFileName();
                    String command = "wget -O \"" + remotePath.replace("\\", "/") + "\" \"" + ver.fileUrl + "\"";
                    devPrint("Remote Download: " + command);
                    serverInfo.remoteSSHManager.runRemoteCommand(command);
                    ver.isDownloading = true;
                    long startTime = System.currentTimeMillis();
                    while (true) {
                        String sizeCommand = "stat -c%s " + remotePath.replace("\\", "/");
                        String sizeOutput = serverInfo.remoteSSHManager.runRemoteCommandWithOutput(sizeCommand);
                        long remoteSize = 0;
                        try {
                            remoteSize = Long.parseLong(sizeOutput.trim());
                        } catch (Exception ignored) {}
                        ver.downloadedBytes = remoteSize;
                        if (total > 0) {
                            ver.progress = (double) remoteSize / total;
                        }
                        long currentTime = System.currentTimeMillis();
                        long timeElapsed = currentTime - startTime;
                        if (timeElapsed > 0) {
                            ver.speed = remoteSize / (timeElapsed / 1000.0);
                        }
                        if (remoteSize >= total && total > 0) {
                            break;
                        }
                    }
                    minecraftClient.execute(() -> {
                        ver.isDownloading = false;
                        ver.isInstalled = "Installed";
                    });
                    return;
                }
                URL url = new URL(ver.fileUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Remotely");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                ver.totalBytes = conn.getContentLength();
                ver.downloadedBytes = 0;
                ver.progress = 0.0;
                ver.isDownloading = true;
                long startTime = System.currentTimeMillis();
                InputStream in = conn.getInputStream();
                String serverDir = serverInfo.path + File.separator + (serverInfo.isModServer() ? "mods" : serverInfo.isPluginServer() ? "plugins" : "");
                Path serverPath = Path.of(serverDir);
                if (!Files.exists(serverPath)) Files.createDirectories(serverPath);
                String fileName = resource.getFileName();
                File outFile = new File(serverDir, fileName);
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    ver.downloadedBytes += bytesRead;
                    long currentTime = System.currentTimeMillis();
                    long timeElapsed = currentTime - startTime;
                    if (timeElapsed > 0) {
                        ver.speed = ver.downloadedBytes / (timeElapsed / 1000.0);
                    }
                    if (ver.totalBytes > 0) {
                        ver.progress = (double) ver.downloadedBytes / ver.totalBytes;
                    }
                }
                fos.close();
                in.close();
                ver.isDownloading = false;
                ver.isInstalled = "Installed";
            } catch (Exception e) {
                ver.isDownloading = false;
                ver.isInstalled = "Failed";
            }
        }).start();
    }

    private void downloadMrpackResource() {
        new Thread(() -> {
            try {
                if (serverInfo.isRemote && serverInfo.remoteSSHManager != null) {
                    serverInfo.remoteSSHManager.installMrPackOnRemote(serverInfo, resource);
                    minecraftClient.execute(() -> {
                        isDownloadingMrpack = false;
                    });
                    return;
                }
                String exePath;
                String serverDir;
                URL url;
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("windows")) {
                    exePath = Path.of(String.valueOf(remotelyDir), "mrpack-install-windows.exe").toString();
                    serverDir = Path.of(String.valueOf(remotelyDir), "servers", resource.getName()).toString();
                    url = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-windows.exe");
                } else if (os.contains("linux")) {
                    exePath = Path.of(String.valueOf(remotelyDir), "mrpack-install-linux").toString();
                    serverDir = Path.of(String.valueOf(remotelyDir), "servers", resource.getName()).toString();
                    url = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-linux");
                } else if (os.contains("mac") || os.contains("darwin")) {
                    exePath = Path.of(String.valueOf(remotelyDir), "mrpack-install-macos").toString();
                    serverDir = Path.of(String.valueOf(remotelyDir), "servers", resource.getName()).toString();
                    url = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-darwin");
                } else {
                    minecraftClient.execute(() -> {
                        isDownloadingMrpack = false;
                    });
                    return;
                }
                Path exe = Path.of(exePath);
                Path serverPath = Path.of(serverDir);
                if (!Files.exists(serverPath)) Files.createDirectories(serverPath);
                if (!Files.exists(exe)) {
                    try {
                        try (InputStream input = url.openStream()) {
                            Files.copy(input, exe, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e) {
                        minecraftClient.execute(() -> {
                            isDownloadingMrpack = false;
                        });
                        return;
                    }
                }
                isDownloadingMrpack = true;
                ProcessBuilder pb = new ProcessBuilder(exePath, resource.getProjectId(), resource.getVersion(), "--server-dir", serverDir, "--server-file", "server.jar");
                pb.directory(serverPath.toFile());
                Process proc = pb.start();
                proc.waitFor();
                minecraftClient.execute(() -> {
                    isDownloadingMrpack = false;
                });
            } catch (Exception e) {
                minecraftClient.execute(() -> {
                    isDownloadingMrpack = false;
                });
            }
        }).start();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public void onDisplayed() {
        playSound(Sound.SCREEN);
    }

    @Override
    public void removed() {
        minecraftClient.getWindow().setScaleFactor(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
    }

    private static class Tab {
        TabType type;
        String name;
        Tab(TabType type, String name) {
            this.type = type;
            this.name = name;
        }
        public String toString() {
            return name;
        }
    }

    private enum TabType { DESCRIPTION, VERSIONS }

    private static class Version {
        public String isInstalled;
        String version;
        String mcVersions;
        String dateUploaded;
        String fileUrl;
        int downloads;
        boolean isDownloading;
        double progress;
        long downloadedBytes;
        long totalBytes;
        double speed;
        Version(String version, String mcVersions, String dateUploaded, String fileUrl, int downloads) {
            this.version = version;
            this.mcVersions = mcVersions;
            this.dateUploaded = dateUploaded;
            this.fileUrl = fileUrl;
            this.downloads = downloads;
            this.isDownloading = false;
            this.progress = 0;
            this.downloadedBytes = 0;
            this.totalBytes = 0;
            this.speed = 0;
            this.isInstalled = "Download";
        }
    }

    private static class VersionButtonRegion {
        int x, y, width, height;
        Version version;
        VersionButtonRegion(int x, int y, int width, int height, Version version) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.version = version;
        }
    }
}
