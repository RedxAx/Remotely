package redxax.oxy.servers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import redxax.oxy.Render;
import redxax.oxy.api.IRemotelyResource;
import redxax.oxy.config.Config;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static redxax.oxy.Render.*;
import static redxax.oxy.config.Config.*;
import static redxax.oxy.servers.PluginModManagerScreen.formatDownloads;
import static redxax.oxy.util.DevUtil.devPrint;
import static redxax.oxy.util.ImageUtil.drawBufferedImage;

public class ResourcePageScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final PluginModManagerScreen parentScreen;
    private final IRemotelyResource resource;
    private String htmlContent = "";
    private Document htmlDocument;
    private boolean isLoadingMarkdown = true;
    private float descScrollOffset = 0;
    private float descTargetScrollOffset = 0;
    private float versionsScrollOffset = 0;
    private float versionsTargetScrollOffset = 0;
    private final Map<String, BufferedImage> imageCache = new HashMap<>();
    private final Map<String, BufferedImage> scaledImageCache = new HashMap<>();
    private final List<LinkRegion> linkRegions = new LinkedList<>();
    private int cachedContentHeight = -1;
    private int cachedEditorWidth = -1;
    private List<HTMLRenderer.RenderCommand> cachedRenderCommands = null;
    private List<Tab> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private List<Version> versions = new ArrayList<>();
    private List<VersionButtonRegion> versionButtonRegions = new ArrayList<>();
    private static ServerInfo serverInfo;
    private Version headerDownloadVersion;
    private boolean isDownloadingMrpack = false;
    private double mrpackProgress = 0.0;

    public ResourcePageScreen(MinecraftClient mc, PluginModManagerScreen parent, IRemotelyResource resource, ServerInfo serverInfo) {
        super(Text.literal(resource.getName()));
        this.minecraftClient = mc;
        this.parentScreen = parent;
        this.resource = resource;
        ResourcePageScreen.serverInfo = serverInfo;
        loadMarkdown();
        initTabs();
        fetchVersions();
    }

    private void initTabs() {
        tabs.clear();
        tabs.add(new Tab(TabType.DESCRIPTION, "Description"));
        tabs.add(new Tab(TabType.VERSIONS, "Versions"));
        currentTabIndex = 0;
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
                MutableDataSet options = new MutableDataSet();
                Parser parser = Parser.builder(options).build();
                HtmlRenderer renderer = HtmlRenderer.builder(options).build();
                String html = renderer.render(parser.parse(markdownContent));
                Document doc = Jsoup.parse(html);
                htmlDocument = doc;
                htmlContent = doc.body().html();
            } catch (Exception e) {
                htmlContent = markdownContent;
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
                    url = "https://hangar.papermc.io/api/v1/projects/" + resource.getProjectId() + "/versions?includeHiddenChannels=true&channel=Release&platform=PAPER&limit=25&offset=" + offset;
                    devPrint("Hangar Versions URL: " + url);
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
                        String verNum = verObj.has("version_number")
                                ? verObj.get("version_number").getAsString()
                                : (verObj.has("name") ? verObj.get("name").getAsString() : "Unknown");
                        String mcVersions = "";
                        String dateUploaded = verObj.has("createdAt")
                                ? verObj.get("createdAt").getAsString()
                                : (verObj.has("date_published") ? verObj.get("date_published").getAsString() : "Unknown");
                        String fileUrl = "";
                        if (resource.getSlug().startsWith("hangar_")) {
                            if (verObj.has("downloads")) {
                                JsonObject downloads = verObj.getAsJsonObject("downloads");
                                if (downloads.has("PAPER")) {
                                    JsonObject paperObj = downloads.getAsJsonObject("PAPER");
                                    fileUrl = paperObj.has("downloadUrl") ? paperObj.get("downloadUrl").getAsString() : "";
                                }
                            }
                        } else if (verObj.has("files")) {
                            JsonArray files = verObj.getAsJsonArray("files");
                            if (files.size() > 0) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int headerHeight = 30;
        int tabAreaHeight = 18;
        int contentY = headerHeight + tabAreaHeight + 5;
        int contentHeight = this.height - contentY - 10;
        if(mouseY >= contentY && mouseY <= contentY + contentHeight){
            if(getCurrentTabType() == TabType.DESCRIPTION){
                descTargetScrollOffset -= verticalAmount * 30;
                int max = Math.max(0, cachedContentHeight - (contentHeight - 20));
                descTargetScrollOffset = Math.max(0, Math.min(descTargetScrollOffset, max));
            } else if(getCurrentTabType() == TabType.VERSIONS){
                int itemHeight = 35;
                int gap = 2;
                int totalHeight = versions.size() * (itemHeight + gap);
                versionsTargetScrollOffset -= verticalAmount * 30;
                int max = Math.max(0, totalHeight - contentHeight);
                versionsTargetScrollOffset = Math.max(0, Math.min(versionsTargetScrollOffset, max));
            }
        }
        int tabBarY = 35;
        int tabBarHeight = 18;
        if(mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight){
            int tabBarX = 5;
            for(int i=0;i<tabs.size();i++){
                Tab t = tabs.get(i);
                int tabWidth = minecraftClient.textRenderer.getWidth(t.name) + 10;
                if(mouseX >= tabBarX && mouseX <= tabBarX + tabWidth){
                    currentTabIndex = i;
                    return true;
                }
                tabBarX += tabWidth + 5;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int headerHeight = 30;
        int tabBarY = 35;
        int tabBarHeight = 18;
        if(mouseY >= tabBarY && mouseY <= tabBarY + tabBarHeight){
            int tabBarX = 5;
            for(int i=0;i<tabs.size();i++){
                Tab t = tabs.get(i);
                int tabWidth = minecraftClient.textRenderer.getWidth(t.name) + 10;
                if(mouseX >= tabBarX && mouseX <= tabBarX + tabWidth){
                    currentTabIndex = i;
                    return true;
                }
                tabBarX += tabWidth + 5;
            }
        }
        int buttonW = 60;
        int buttonH = 20;
        int spacing = 10;
        int backButtonX = this.width - buttonW - 10;
        int siteButtonX = backButtonX - (buttonW + spacing);
        int downloadButtonX = siteButtonX - (buttonW + spacing);
        int buttonY = (headerHeight - buttonH) / 2;
        if(mouseX >= downloadButtonX && mouseX <= downloadButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH) {
            if (resource.getFileName().toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
                downloadMrpackResource();
            } else {
                Version compVersion = getLatestCompatibleVersion();
                if(compVersion != null) {
                    headerDownloadVersion = compVersion;
                    downloadVersionResource(compVersion);
                }
            }
            return true;
        }
        if(mouseX >= siteButtonX && mouseX <= siteButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH) {
            String siteUrl = getSiteUrlForResource();
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
        if(mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH){
            minecraftClient.setScreen(parentScreen);
            return true;
        }
        if(getCurrentTabType() == TabType.DESCRIPTION){
            for (LinkRegion region : linkRegions) {
                if (mouseX >= region.x && mouseX <= region.x + region.width && mouseY >= region.y && mouseY <= region.y + region.height) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", region.url);
                        pb.start();
                    } catch (Exception e) {
                        devPrint("Failed to open browser: " + e.getMessage());
                    }
                    return true;
                }
            }
        }
        if(getCurrentTabType() == TabType.VERSIONS){
            for(VersionButtonRegion vr : versionButtonRegions){
                if(mouseX >= vr.x && mouseX <= vr.x+vr.width && mouseY >= vr.y && mouseY <= vr.y+vr.height){
                    downloadVersionResource(vr.version);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        int headerHeight = 30;
        int tabAreaHeight = 18;
        context.fillGradient(0, 0, this.width, this.height, browserScreenBackgroundColor, browserScreenBackgroundColor);
        context.fill(0, 0, this.width, headerHeight, headerBackgroundColor);
        drawInnerBorder(context, 0, 0, this.width, headerHeight, headerBorderColor);
        drawOuterBorder(context, 0, 0, this.width, headerHeight, globalBottomBorder);
        context.drawText(minecraftClient.textRenderer, Text.literal("Remotely Browser - " + resource.getName()), 10, 10, screensTitleTextColor, Config.shadow);
        int buttonW = 60;
        int buttonH = 20;
        int spacing = 10;
        int backButtonX = this.width - buttonW - 10;
        int siteButtonX = backButtonX - (buttonW + spacing);
        int downloadButtonX = siteButtonX - (buttonW + spacing);
        int buttonY = (headerHeight - buttonH) / 2;
        if(headerDownloadVersion != null && headerDownloadVersion.isDownloading) {
            int barWidth = buttonW;
            int barHeight = buttonH;
            int barX = downloadButtonX;
            int barY = buttonY;
            context.fill(barX, barY, barX + barWidth, barY + barHeight, browserElementBackgroundColor);
            int fillWidth = (int)(barWidth * headerDownloadVersion.progress);
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, buttonTextHoverColor);
            drawOuterBorder(context, barX, barY, barWidth, barHeight, globalBottomBorder);
            drawInnerBorder(context, barX, barY, barWidth, barHeight, browserElementBorderColor);
            String percentText = (int)(headerDownloadVersion.progress * 100) + "%";
            context.drawText(minecraftClient.textRenderer, Text.literal(percentText), barX + barWidth/2 - minecraftClient.textRenderer.getWidth(Text.literal(percentText))/2, barY + (barHeight - minecraftClient.textRenderer.fontHeight)/2, 0xFFFFFFFF, Config.shadow);
        } else if(isDownloadingMrpack) {
            int barWidth = buttonW;
            int barHeight = buttonH;
            int barX = downloadButtonX;
            int barY = buttonY;
            context.fill(barX, barY, barX + barWidth, barY + barHeight, browserElementBackgroundColor);
            int fillWidth = (int)(barWidth * mrpackProgress);
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, buttonTextHoverColor);
            drawOuterBorder(context, barX, barY, barWidth, barHeight, globalBottomBorder);
            drawInnerBorder(context, barX, barY, barWidth, barHeight, browserElementBorderColor);
            String percentText = (int)(mrpackProgress * 100) + "%";
            context.drawText(minecraftClient.textRenderer, Text.literal(percentText), barX + barWidth/2 - minecraftClient.textRenderer.getWidth(Text.literal(percentText))/2, barY + (barHeight - minecraftClient.textRenderer.fontHeight)/2, 0xFFFFFFFF, Config.shadow);
        } else {
            drawCustomButton(context, downloadButtonX, buttonY, "Download", minecraftClient, mouseX >= downloadButtonX && mouseX <= downloadButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH, false, true, buttonTextColor, buttonTextHoverColor);
        }
        drawCustomButton(context, siteButtonX, buttonY, "Site", minecraftClient, mouseX >= siteButtonX && mouseX <= siteButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH, false, true, buttonTextColor, buttonTextBrowseHoverColor);
        drawCustomButton(context, backButtonX, buttonY, "Back", minecraftClient, mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH, false, true, buttonTextColor, buttonTextDeleteColor);
        drawTabs(context, minecraftClient.textRenderer, tabs, currentTabIndex, mouseX, mouseY, false, false);
        int contentY = headerHeight + tabAreaHeight + 10;
        int contentHeight = this.height - contentY - 10;
        int contentX = 5;
        int contentWidth = this.width - 10;
        context.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, editorInnerBackgroundColor);
        drawInnerBorder(context, contentX, contentY, contentWidth, contentHeight, editorBorderColor);
        drawOuterBorder(context, contentX, contentY, contentWidth, contentHeight, globalBottomBorder);
        if(getCurrentTabType() == TabType.DESCRIPTION){
            if(isLoadingMarkdown){
                context.drawText(minecraftClient.textRenderer, Text.literal("Loading..."), this.width / 2 - 20, this.height / 2, screensTitleTextColor, Config.shadow);
                return;
            }
            linkRegions.clear();
            if(cachedEditorWidth != contentWidth || cachedRenderCommands == null){
                HTMLRenderer.RenderResult result = HTMLRenderer.buildRenderCommands(htmlDocument, contentX + 10, contentY + 10, contentWidth - 20, minecraftClient.textRenderer, imageCache, scaledImageCache, 10);
                cachedRenderCommands = result.commands;
                cachedContentHeight = result.totalHeight;
                cachedEditorWidth = contentWidth;
            }
            descScrollOffset += (descTargetScrollOffset - descScrollOffset) * delta * 0.2f;
            int maxScrollOffset = Math.max(0, cachedContentHeight - (contentHeight - 20));
            descTargetScrollOffset = Math.min(descTargetScrollOffset, maxScrollOffset);
            context.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
            for(HTMLRenderer.RenderCommand cmd : cachedRenderCommands){
                int drawY = cmd.y - (int)descScrollOffset;
                if(drawY + cmd.height < contentY || drawY > contentY + contentHeight) continue;
                switch(cmd.type){
                    case 0:
                        context.drawText(minecraftClient.textRenderer, Text.literal(cmd.format + cmd.text), cmd.x, drawY, cmd.color, Config.shadow);
                        break;
                    case 1:
                        context.fill(cmd.x, drawY, cmd.x + cmd.width, drawY + cmd.height, cmd.color);
                        break;
                    case 2:
                        drawBufferedImage(context, cmd.image, cmd.x, drawY, cmd.width, cmd.height);
                        break;
                    case 3:
                        context.fill(cmd.x, drawY, cmd.x + cmd.width, drawY + cmd.height, cmd.color);
                        context.drawText(minecraftClient.textRenderer, Text.literal(cmd.text), cmd.x + 5, drawY + 5, 0xFFFFFFFF, Config.shadow);
                        break;
                    default:
                        break;
                }
                if(cmd.href != null && !cmd.href.isEmpty()){
                    linkRegions.add(new LinkRegion(cmd.x, drawY, cmd.width, cmd.height, cmd.href));
                }
            }
            context.disableScissor();
        } else if(getCurrentTabType() == TabType.VERSIONS){
            versionButtonRegions.clear();
            versionsScrollOffset += (versionsTargetScrollOffset - versionsScrollOffset) * delta * 0.2f;
            int itemHeight = 35;
            context.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
            for(int i=0;i<versions.size();i++){
                Version ver = versions.get(i);
                int y = contentY + i*(itemHeight + 2) - (int)versionsScrollOffset;
                if(y + itemHeight < contentY || y > contentY + contentHeight) continue;
                boolean hovered = mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= y && mouseY < y + itemHeight;
                int bg = hovered ? browserElementBackgroundHoverColor : browserElementBackgroundColor;
                int borderColor = hovered ? browserElementBorderHoverColor : browserElementBorderColor;
                context.fill(contentX, y, contentX + contentWidth, y + itemHeight, bg);
                drawInnerBorder(context, contentX, y, contentWidth, itemHeight, borderColor);
                drawOuterBorder(context, contentX, y, contentWidth, itemHeight, globalBottomBorder);
                String title = resource.getName() + ": " + ver.version;
                context.drawText(minecraftClient.textRenderer, Text.literal(title), contentX +4, y + 3, 0xFFFFFFFF, Config.shadow);
                String desc = formatMCVersions(ver.mcVersions);
                context.drawText(minecraftClient.textRenderer, Text.literal(desc), contentX +4, y + 15, 0xFFAAAAAA, Config.shadow);
                String subDesc = getRelativeTime(ver.dateUploaded) + " | " + formatDownloads(ver.downloads) + " Downloads";
                context.drawText(minecraftClient.textRenderer, Text.literal(subDesc), contentX +4, y + 26, 0xFF777777, Config.shadow);
                if(ver.isDownloading) {
                    int barWidth = Render.buttonW;
                    int barHeight = Render.buttonH;
                    int barX = contentX + contentWidth - barWidth - 10;
                    int barY = y + (itemHeight - barHeight) / 2;
                    context.fill(barX, barY, barX + barWidth, barY + barHeight, browserElementBackgroundColor);
                    int fillWidth = (int)(barWidth * ver.progress);
                    context.fill(barX, barY, barX + fillWidth, barY + barHeight, buttonTextHoverColor);
                    drawOuterBorder(context, barX, barY, barWidth, barHeight, globalBottomBorder);
                    drawInnerBorder(context, barX, barY, barWidth, barHeight, browserElementBorderColor);
                    String percentText = (int)(ver.progress * 100) + "%";
                    context.drawText(minecraftClient.textRenderer, Text.literal(percentText), barX + barWidth/2 - minecraftClient.textRenderer.getWidth(Text.literal(percentText))/2, barY + (barHeight - minecraftClient.textRenderer.fontHeight)/2, 0xFFFFFFFF, Config.shadow);
                    String infoText = formatBytes(ver.downloadedBytes) + "/" + formatBytes(ver.totalBytes) + " | " + formatBytes((long)ver.speed) + "/s";
                    context.drawText(minecraftClient.textRenderer, Text.literal(infoText), barX - 5 - minecraftClient.textRenderer.getWidth(Text.literal(infoText)), barY + (barHeight - minecraftClient.textRenderer.fontHeight)/2, 0xFFCCCCCC, Config.shadow);
                } else {
                    int btnX = contentX + contentWidth - 70;
                    int btnY = y + (itemHeight - 20)/2;
                    drawCustomButton(context, btnX, btnY, ver.isInstalled, minecraftClient, mouseX >= btnX && mouseX <= btnX+60 && mouseY >= btnY && mouseY <= btnY+20, false, true, Objects.equals(ver.isInstalled, "Failed") ? buttonTextDeleteHoverColor : Objects.equals(ver.isInstalled, "Installed") ? buttonTextExplorerHoverColor : buttonTextColor, buttonTextHoverColor);
                    versionButtonRegions.add(new VersionButtonRegion(btnX, btnY, 60, 20, ver));
                }
            }
            context.disableScissor();
        }
    }

    private String getRelativeTime(String dateStr) {
        try {
            Instant uploaded;
            if(dateStr.matches("\\d+")) {
                long epoch = Long.parseLong(dateStr);
                uploaded = Instant.ofEpochSecond(epoch);
            } else {
                uploaded = Instant.parse(dateStr);
            }
            Duration duration = Duration.between(uploaded, Instant.now());
            long days = duration.toDays();
            if(days < 1) return "Today";
            if(days < 30) return days + " Days Ago";
            long months = days/30;
            return months + " Months Ago";
        } catch(Exception e) {
            return dateStr;
        }
    }

    private String formatMCVersions(String raw) {
        if(raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split(",\\s*");
        Map<String, List<Integer>> groups = new HashMap<>();
        List<String> others = new ArrayList<>();
        for(String ver : parts) {
            String trimmed = ver.trim();
            String[] nums = trimmed.split("\\.");
            if(nums.length >= 2) {
                String key = nums[0] + "." + nums[1];
                int patch = 0;
                if(nums.length >= 3) {
                    try {
                        patch = Integer.parseInt(nums[2]);
                    } catch(Exception e){}
                }
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(patch);
            } else {
                others.add(trimmed);
            }
        }
        List<String> results = new ArrayList<>();
        for(Map.Entry<String, List<Integer>> entry : groups.entrySet()){
            List<Integer> patches = entry.getValue();
            Collections.sort(patches);
            if(patches.size() >= 3 && patches.get(patches.size()-1) - patches.get(0) == patches.size()-1) {
                results.add(entry.getKey() + ".x");
            } else if(patches.size() >= 2) {
                String first = entry.getKey() + "." + patches.get(0);
                String last = entry.getKey() + "." + patches.get(patches.size()-1);
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
                if(ver.fileUrl.isEmpty()) {
                    ver.isInstalled = "Failed";
                    return;
                }
                if(serverInfo.isRemote && serverInfo.remoteSSHManager != null) {
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
                        } catch(Exception e){}
                        ver.downloadedBytes = remoteSize;
                        if(total > 0) {
                            ver.progress = (double) remoteSize / total;
                        }
                        long currentTime = System.currentTimeMillis();
                        long timeElapsed = currentTime - startTime;
                        if(timeElapsed > 0) {
                            ver.speed = remoteSize / (timeElapsed / 1000.0);
                        }
                        if(remoteSize >= total && total > 0) {
                            break;
                        }
                        Thread.sleep(500);
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
                int total = conn.getContentLength();
                ver.totalBytes = total;
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
                while((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    ver.downloadedBytes += bytesRead;
                    long currentTime = System.currentTimeMillis();
                    long timeElapsed = currentTime - startTime;
                    if(timeElapsed > 0) {
                        ver.speed = ver.downloadedBytes / (timeElapsed / 1000.0);
                    }
                    if(ver.totalBytes > 0) {
                        ver.progress = (double) ver.downloadedBytes / ver.totalBytes;
                    }
                }
                fos.close();
                in.close();
                ver.isDownloading = false;
                ver.isInstalled = "Installed";
            } catch(Exception e){
                ver.isDownloading = false;
                ver.isInstalled = "Failed";
            }
        }).start();
    }

    private void downloadMrpackResource() {
        new Thread(() -> {
            try {
                if(serverInfo.isRemote && serverInfo.remoteSSHManager != null) {
                    boolean success = serverInfo.remoteSSHManager.installMrPackOnRemote(serverInfo, resource);
                    minecraftClient.execute(() -> {
                        isDownloadingMrpack = false;
                    });
                    return;
                }
                String exePath = "C:\\remotely\\mrpack-install-windows.exe";
                String serverDir = "C:\\remotely\\servers\\" + resource.getName();
                Path exe = Path.of(exePath);
                Path serverPath = Path.of(serverDir);
                if (!Files.exists(serverPath)) Files.createDirectories(serverPath);
                if (!Files.exists(exe)) {
                    try {
                        URL url = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-windows.exe");
                        try (InputStream input = url.openStream()) {
                            Files.copy(input, exe, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
            } catch(Exception e){
                minecraftClient.execute(() -> {
                    isDownloadingMrpack = false;
                });
            }
        }).start();
    }

    private String formatBytes(long bytes) {
        if(bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static class HTMLRenderer {
        private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+|\\s+");
        static class InlineState {
            int x, y;
            InlineState(int x, int y) { this.x = x; this.y = y; }
        }
        static class RenderCommand {
            int type;
            int x, y, width, height;
            String text = "";
            int color;
            boolean shadow;
            String format = "";
            BufferedImage image;
            String href = "";
        }
        static class RenderResult {
            List<RenderCommand> commands;
            int totalHeight;
        }
        static RenderResult buildRenderCommands(Document doc, int startX, int startY, int maxWidth, TextRenderer renderer, Map<String, BufferedImage> imageCache, Map<String, BufferedImage> scaledCache, int padding) {
            List<RenderCommand> commands = new LinkedList<>();
            int currentY = startY;
            for (Element elem : doc.body().children()) {
                currentY = buildBlockCommands(elem, startX, currentY, maxWidth, renderer, commands, imageCache, scaledCache, padding);
                currentY += 5;
            }
            RenderResult result = new RenderResult();
            result.commands = commands;
            result.totalHeight = currentY - startY;
            return result;
        }
        static int buildBlockCommands(Element element, int x, int y, int maxWidth, TextRenderer renderer, List<RenderCommand> commands, Map<String, BufferedImage> imageCache, Map<String, BufferedImage> scaledCache, int padding) {
            String tag = element.tagName();
            if (tag.equals("br")) {
                return y + renderer.fontHeight;
            }
            if (tag.equals("hr")) {
                RenderCommand cmd = new RenderCommand();
                cmd.type = 1;
                cmd.x = x;
                cmd.y = y + renderer.fontHeight / 2;
                cmd.width = maxWidth;
                cmd.height = 1;
                cmd.color = 0xFFAAAAAA;
                commands.add(cmd);
                return y + renderer.fontHeight;
            }
            if (tag.equalsIgnoreCase("img")) {
                String src = element.attr("src");
                BufferedImage img = loadImage(src, imageCache, maxWidth, scaledCache);
                if (img != null) {
                    int imgW = Math.min(img.getWidth(), maxWidth);
                    int imgH = img.getHeight() * imgW / img.getWidth();
                    RenderCommand cmd = new RenderCommand();
                    cmd.type = 2;
                    cmd.x = x;
                    cmd.y = y;
                    cmd.width = imgW;
                    cmd.height = imgH;
                    cmd.image = imgW < img.getWidth() ? loadImage(src, imageCache, maxWidth, scaledCache) : img;
                    commands.add(cmd);
                    return y + imgH + 5;
                }
                return y;
            }
            if (tag.equals("ul") || tag.equals("ol")) {
                int index = 1;
                for (Element li : element.children()) {
                    String bullet = tag.equals("ul") ? "• " : index + ". ";
                    RenderCommand bulletCmd = new RenderCommand();
                    bulletCmd.type = 0;
                    bulletCmd.x = x;
                    bulletCmd.y = y;
                    bulletCmd.text = bullet;
                    bulletCmd.format = "";
                    bulletCmd.color = 0xFFFFFFFF;
                    bulletCmd.width = renderer.getWidth(Text.literal(bullet));
                    commands.add(bulletCmd);
                    int bulletWidth = renderer.getWidth(Text.literal(bullet));
                    InlineState state = buildInlineCommands(li.childNodes(), x + bulletWidth, x + bulletWidth, y, maxWidth - bulletWidth, renderer, commands, "", imageCache, scaledCache, false);
                    y = state.y + renderer.fontHeight;
                    index++;
                }
                return y;
            }
            if (tag.equalsIgnoreCase("pre")) {
                String codeText;
                Element codeElem = element.selectFirst("code");
                if(codeElem != null) {
                    codeText = codeElem.wholeText();
                } else {
                    codeText = element.wholeText();
                }
                String[] lines = codeText.split("\\r?\\n");
                int blockHeight = lines.length * renderer.fontHeight + 4;
                RenderCommand bgCmd = new RenderCommand();
                bgCmd.type = 1;
                bgCmd.x = x;
                bgCmd.y = y;
                bgCmd.width = maxWidth;
                bgCmd.height = blockHeight;
                bgCmd.color = 0xFF2E2E2E;
                commands.add(bgCmd);
                for (int i = 0; i < lines.length; i++) {
                    RenderCommand textCmd = new RenderCommand();
                    textCmd.type = 0;
                    textCmd.x = x + 2;
                    textCmd.y = y + 2 + i * renderer.fontHeight;
                    textCmd.text = lines[i];
                    textCmd.format = "";
                    textCmd.color = 0xFFFFFFFF;
                    textCmd.width = renderer.getWidth(Text.literal(lines[i]));
                    commands.add(textCmd);
                }
                return y + blockHeight + 5;
            }
            if (tag.equalsIgnoreCase("table") || tag.equalsIgnoreCase("thead") || tag.equalsIgnoreCase("tbody") || tag.equalsIgnoreCase("tfoot")) {
                List<List<Element>> tableRows = new LinkedList<>();
                for (Element row : element.select("tr")) {
                    List<Element> cells = new LinkedList<>();
                    for (Element cell : row.children()) {
                        cells.add(cell);
                    }
                    tableRows.add(cells);
                }
                if(tableRows.isEmpty()) return y;
                int numCols = 0;
                for(List<Element> row : tableRows) {
                    numCols = Math.max(numCols, row.size());
                }
                int border = 1;
                int cellPadding = 5;
                int headerExtra = 3;
                int[][] naturalWidths = new int[tableRows.size()][numCols];
                int[][] naturalHeights = new int[tableRows.size()][numCols];
                for (int i = 0; i < tableRows.size(); i++) {
                    List<Element> row = tableRows.get(i);
                    for (int j = 0; j < numCols; j++) {
                        if(j < row.size()){
                            Element cell = row.get(j);
                            boolean isHeader = cell.tagName().equalsIgnoreCase("th");
                            int localPadding = isHeader ? cellPadding + headerExtra : cellPadding;
                            List<RenderCommand> temp = new LinkedList<>();
                            buildInlineCommands(cell.childNodes(), 0, 0, 0, 1000, renderer, temp, "", imageCache, scaledCache, false);
                            int cellW = 0;
                            for(RenderCommand cmd : temp){
                                cellW = Math.max(cellW, cmd.x + cmd.width);
                            }
                            int cellH = renderer.fontHeight;
                            naturalWidths[i][j] = cellW + 2 * localPadding;
                            naturalHeights[i][j] = cellH + 2 * localPadding;
                        } else {
                            naturalWidths[i][j] = 0;
                            naturalHeights[i][j] = renderer.fontHeight + 2 * cellPadding;
                        }
                    }
                }
                int[] naturalColWidths = new int[numCols];
                for (int j = 0; j < numCols; j++){
                    int maxCol = 0;
                    for (int i = 0; i < tableRows.size(); i++){
                        maxCol = Math.max(maxCol, naturalWidths[i][j]);
                    }
                    naturalColWidths[j] = maxCol;
                }
                int totalNaturalWidth = (numCols + 1) * border;
                for (int j = 0; j < numCols; j++){
                    totalNaturalWidth += naturalColWidths[j];
                }
                boolean needWrap = totalNaturalWidth > maxWidth;
                int[] colWidths = new int[numCols];
                if (needWrap) {
                    int newColWidth = (maxWidth - (numCols + 1) * border) / numCols;
                    for (int j = 0; j < numCols; j++){
                        colWidths[j] = newColWidth;
                    }
                } else {
                    for (int j = 0; j < numCols; j++){
                        colWidths[j] = naturalColWidths[j];
                    }
                }
                int[][] cellHeights = new int[tableRows.size()][numCols];
                List<List<List<RenderCommand>>> cellCommands = new LinkedList<>();
                for (int i = 0; i < tableRows.size(); i++){
                    List<Element> row = tableRows.get(i);
                    List<List<RenderCommand>> rowCommands = new LinkedList<>();
                    for (int j = 0; j < numCols; j++){
                        List<RenderCommand> cmds = new LinkedList<>();
                        if(j < row.size()){
                            Element cell = row.get(j);
                            boolean isHeader = cell.tagName().equalsIgnoreCase("th");
                            int localPadding = isHeader ? cellPadding + headerExtra : cellPadding;
                            InlineState st = buildInlineCommands(cell.childNodes(), 0, 0, 0, colWidths[j] - 2 * localPadding, renderer, cmds, "", imageCache, scaledCache, false);
                            int usedHeight = st.y + renderer.fontHeight;
                            if (usedHeight < renderer.fontHeight) {
                                usedHeight = renderer.fontHeight;
                            }
                            cellHeights[i][j] = usedHeight + 2 * localPadding;
                        } else {
                            cellHeights[i][j] = renderer.fontHeight + 2 * cellPadding;
                        }
                        rowCommands.add(cmds);
                    }
                    cellCommands.add(rowCommands);
                }
                int[] rowHeights = new int[tableRows.size()];
                for (int i = 0; i < tableRows.size(); i++){
                    int maxRow = 0;
                    for (int j = 0; j < numCols; j++){
                        maxRow = Math.max(maxRow, cellHeights[i][j]);
                    }
                    rowHeights[i] = maxRow;
                }
                int tableX = x;
                int tableY = y;
                int tableWidth = (numCols + 1) * border;
                for (int j = 0; j < numCols; j++){
                    tableWidth += colWidths[j];
                }
                int tableHeight = (tableRows.size() + 1) * border;
                for (int i = 0; i < tableRows.size(); i++){
                    tableHeight += rowHeights[i];
                }
                RenderCommand tableBg = new RenderCommand();
                tableBg.type = 1;
                tableBg.x = tableX;
                tableBg.y = tableY;
                tableBg.width = tableWidth;
                tableBg.height = tableHeight;
                tableBg.color = 0xFF888888;
                commands.add(tableBg);
                int currentY = tableY + border;
                int rowIndex = 0;
                for (List<Element> row : tableRows){
                    int currentX = tableX + border;
                    List<List<RenderCommand>> rowCmds = cellCommands.get(rowIndex);
                    for (int j = 0; j < numCols; j++){
                        int cellW = colWidths[j];
                        int cellH = rowHeights[rowIndex];
                        RenderCommand cellBg = new RenderCommand();
                        cellBg.type = 1;
                        cellBg.x = currentX;
                        cellBg.y = currentY;
                        cellBg.width = cellW;
                        cellBg.height = cellH;
                        cellBg.color = 0xFF444444;
                        commands.add(cellBg);
                        if(j < row.size()){
                            boolean isHeader = row.get(j).tagName().equalsIgnoreCase("th");
                            int localPadding = isHeader ? cellPadding + headerExtra : cellPadding;
                            List<RenderCommand> cmds = rowCmds.get(j);
                            for (RenderCommand cmd : cmds){
                                cmd.x += currentX + localPadding;
                                cmd.y += currentY + localPadding;
                                commands.add(cmd);
                            }
                        }
                        RenderCommand vBorder = new RenderCommand();
                        vBorder.type = 1;
                        vBorder.x = currentX - border;
                        vBorder.y = currentY;
                        vBorder.width = border;
                        vBorder.height = cellH;
                        vBorder.color = 0xFF888888;
                        commands.add(vBorder);
                        currentX += cellW + border;
                    }
                    RenderCommand vBorder = new RenderCommand();
                    vBorder.type = 1;
                    vBorder.x = currentX - border;
                    vBorder.y = currentY;
                    vBorder.width = border;
                    vBorder.height = rowHeights[rowIndex];
                    vBorder.color = 0xFF888888;
                    commands.add(vBorder);
                    currentY += rowHeights[rowIndex] + border;
                    RenderCommand hBorder = new RenderCommand();
                    hBorder.type = 1;
                    hBorder.x = tableX;
                    hBorder.y = currentY - border;
                    hBorder.width = tableWidth;
                    hBorder.height = border;
                    hBorder.color = 0xFF888888;
                    commands.add(hBorder);
                    rowIndex++;
                }
                return tableY + tableHeight + 5;
            }
            if (hasTableChild(element)) {
                InlineState state = new InlineState(x, y);
                for (Node child : element.childNodes()) {
                    if (child instanceof Element && isTableElement((Element) child)) {
                        if (state.x > x) { state.y += renderer.fontHeight; state.x = x; }
                        state.y = buildBlockCommands((Element) child, x, state.y, maxWidth, renderer, commands, imageCache, scaledCache, padding);
                    } else {
                        state = buildInlineCommands(Collections.singletonList(child), x, state.x, state.y, maxWidth, renderer, commands, "", imageCache, scaledCache, false);
                    }
                }
                return state.y;
            }
            InlineState state = buildInlineCommands(element.childNodes(), x, x, y, maxWidth, renderer, commands, "", imageCache, scaledCache, false);
            return state.y + renderer.fontHeight;
        }
        static InlineState buildInlineCommands(List<Node> nodes, int startX, int x, int y, int maxWidth, TextRenderer renderer, List<RenderCommand> commands, String format, Map<String, BufferedImage> imageCache, Map<String, BufferedImage> scaledCache, boolean insideLink) {
            InlineState state = new InlineState(x, y);
            for (Node node : nodes) {
                if (node instanceof TextNode) {
                    String text = ((TextNode) node).text();
                    state = buildTextWithWrap(text, startX, state, maxWidth, renderer, commands, format);
                } else if (node instanceof Element) {
                    Element elem = (Element) node;
                    String tag = elem.tagName();
                    String newFormat = format;
                    if (tag.equals("img")) {
                        String src = elem.attr("src");
                        BufferedImage img = loadImage(src, imageCache, maxWidth, scaledCache);
                        if (img != null) {
                            int imgW = Math.min(img.getWidth(), maxWidth);
                            int imgH = img.getHeight() * imgW / img.getWidth();
                            RenderCommand imgCmd = new RenderCommand();
                            imgCmd.type = 2;
                            imgCmd.x = startX;
                            imgCmd.y = state.y;
                            imgCmd.width = imgW;
                            imgCmd.height = imgH;
                            imgCmd.image = img;
                            commands.add(imgCmd);
                            if (insideLink) {
                                imgCmd.href = elem.parent().attr("href");
                            }
                            state.x = startX;
                            state.y += imgH + 5;
                        }
                        continue;
                    } else if (tag.equals("a")) {
                        String href = elem.attr("href");
                        if (elem.select("img").size() > 0) {
                            for (Element childImg : elem.select("img")) {
                                BufferedImage img = loadImage(childImg.attr("src"), imageCache, maxWidth, scaledCache);
                                if (img != null) {
                                    int imgW = Math.min(img.getWidth(), maxWidth);
                                    int imgH = img.getHeight() * imgW / img.getWidth();
                                    RenderCommand imgCmd = new RenderCommand();
                                    imgCmd.type = 2;
                                    imgCmd.x = startX;
                                    imgCmd.y = state.y;
                                    imgCmd.width = imgW;
                                    imgCmd.height = imgH;
                                    imgCmd.image = img;
                                    imgCmd.href = href;
                                    commands.add(imgCmd);
                                    state.x = startX;
                                    state.y += imgH + 5;
                                }
                            }
                            List<Node> nonImageNodes = new LinkedList<>();
                            for (Node child : elem.childNodes()) {
                                if (child instanceof Element && ((Element) child).tagName().equals("img")) continue;
                                nonImageNodes.add(child);
                            }
                            if (!nonImageNodes.isEmpty()) {
                                state = buildInlineCommands(nonImageNodes, startX, state.x, state.y, maxWidth, renderer, commands, newFormat + "§n§9", imageCache, scaledCache, true);
                            }
                        } else {
                            int linkStartX = state.x;
                            int linkStartY = state.y;
                            state = buildInlineCommands(elem.childNodes(), startX, state.x, state.y, maxWidth, renderer, commands, newFormat + "§n§9", imageCache, scaledCache, true);
                            int linkWidth = state.x - linkStartX;
                            if (linkWidth <= 0) {
                                linkWidth = renderer.getWidth(Text.literal(elem.text()));
                            }
                            if (linkWidth > 0) {
                                RenderCommand linkCmd = new RenderCommand();
                                linkCmd.type = 0;
                                linkCmd.x = linkStartX;
                                linkCmd.y = linkStartY;
                                linkCmd.width = linkWidth;
                                linkCmd.height = renderer.fontHeight;
                                linkCmd.href = href;
                                commands.add(linkCmd);
                            }
                        }
                    } else if (tag.equals("iframe") || tag.equals("embed")) {
                        RenderCommand rectCmd = new RenderCommand();
                        rectCmd.type = 1;
                        rectCmd.x = startX;
                        rectCmd.y = state.y;
                        rectCmd.width = maxWidth;
                        rectCmd.height = renderer.fontHeight * 3;
                        rectCmd.color = 0xFF555555;
                        commands.add(rectCmd);
                        RenderCommand textCmd = new RenderCommand();
                        textCmd.type = 0;
                        textCmd.x = startX + 5;
                        textCmd.y = state.y + 5;
                        textCmd.text = "Embedded content";
                        textCmd.format = "";
                        textCmd.color = 0xFFFFFFFF;
                        commands.add(textCmd);
                        state.x = startX;
                        state.y += renderer.fontHeight * 3 + 5;
                    } else if (tag.equals("code")) {
                        state = buildCodeTextWithWrap(elem.text(), startX, state, maxWidth, renderer, commands);
                    } else {
                        state = buildInlineCommands(elem.childNodes(), startX, state.x, state.y, maxWidth, renderer, commands, newFormat, imageCache, scaledCache, insideLink);
                    }
                }
            }
            return state;
        }
        static InlineState buildTextWithWrap(String text, int startX, InlineState state, int maxWidth, TextRenderer renderer, List<RenderCommand> commands, String format) {
            if (text.indexOf(' ') == -1) {
                int textWidth = renderer.getWidth(Text.literal(format + text));
                if (state.x + textWidth > startX + maxWidth) {
                    state.x = startX;
                    state.y += renderer.fontHeight;
                }
                RenderCommand cmd = new RenderCommand();
                cmd.type = 0;
                cmd.x = state.x;
                cmd.y = state.y;
                cmd.text = text;
                cmd.format = format;
                cmd.color = 0xFFFFFFFF;
                cmd.width = textWidth;
                commands.add(cmd);
                state.x += textWidth;
                return state;
            }
            Matcher matcher = TOKEN_PATTERN.matcher(text);
            while (matcher.find()) {
                String token = matcher.group();
                int tokenWidth = renderer.getWidth(Text.literal(format + token));
                if (state.x + tokenWidth > startX + maxWidth) {
                    state.x = startX;
                    state.y += renderer.fontHeight;
                }
                RenderCommand cmd = new RenderCommand();
                cmd.type = 0;
                cmd.x = state.x;
                cmd.y = state.y;
                cmd.text = token;
                cmd.format = format;
                cmd.color = 0xFFFFFFFF;
                cmd.width = tokenWidth;
                commands.add(cmd);
                state.x += tokenWidth;
            }
            return state;
        }
        static InlineState buildCodeTextWithWrap(String text, int startX, InlineState state, int maxWidth, TextRenderer renderer, List<RenderCommand> commands) {
            while(!text.isEmpty()){
                int remainingWidth = startX + maxWidth - state.x;
                int fitLength = 0;
                for (int i = 1; i <= text.length(); i++){
                    int w = renderer.getWidth(Text.literal(text.substring(0, i)));
                    if(w > remainingWidth){
                        break;
                    }
                    fitLength = i;
                }
                if(fitLength == 0){
                    state.x = startX;
                    state.y += renderer.fontHeight;
                    continue;
                }
                String line = text.substring(0, fitLength);
                RenderCommand bgCmd = new RenderCommand();
                bgCmd.type = 1;
                bgCmd.x = state.x - 2;
                bgCmd.y = state.y - 2;
                bgCmd.width = renderer.getWidth(Text.literal(line)) + 4;
                bgCmd.height = renderer.fontHeight + 4;
                bgCmd.color = 0xFF2E2E2E;
                commands.add(bgCmd);
                RenderCommand codeCmd = new RenderCommand();
                codeCmd.type = 0;
                codeCmd.x = state.x;
                codeCmd.y = state.y;
                codeCmd.text = "§7" + line;
                codeCmd.format = "";
                codeCmd.color = 0xFFFFFFFF;
                codeCmd.width = renderer.getWidth(Text.literal(line));
                commands.add(codeCmd);
                state.x += renderer.getWidth(Text.literal(line));
                text = text.substring(fitLength);
                if(!text.isEmpty()){
                    state.x = startX;
                    state.y += renderer.fontHeight;
                }
            }
            return state;
        }
        static BufferedImage loadImage(String url, Map<String, BufferedImage> imageCache, int maxWidth, Map<String, BufferedImage> scaledCache) {
            try {
                if(!url.startsWith("http")){
                    return null;
                }
                if(url.contains("proxy.spigotmc.org") && url.contains("?url=")) {
                    url = URLDecoder.decode(url.substring(url.indexOf("?url=") + 5), StandardCharsets.UTF_8);
                }
                if (imageCache.containsKey(url)) {
                    BufferedImage img = imageCache.get(url);
                    if (img.getWidth() > maxWidth) {
                        String key = url + "_" + maxWidth;
                        if (scaledCache.containsKey(key)) {
                            return scaledCache.get(key);
                        } else {
                            int newWidth = maxWidth;
                            int newHeight = img.getHeight() * newWidth / img.getWidth();
                            int type = img.getType() > 0 ? img.getType() : BufferedImage.TYPE_INT_ARGB;
                            BufferedImage scaled = new BufferedImage(newWidth, newHeight, type);
                            Graphics2D g2d = scaled.createGraphics();
                            g2d.drawImage(img, 0, 0, newWidth, newHeight, null);
                            g2d.dispose();
                            scaledCache.put(key, scaled);
                            return scaled;
                        }
                    } else {
                        return img;
                    }
                } else {
                    URL imageUrl = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) imageUrl.openConnection();
                    conn.setRequestProperty("User-Agent", "Remotely");
                    if (url.contains("spigotmc.org")) {
                        conn.setRequestProperty("Referer", "https://www.spigotmc.org/");
                        conn.setRequestProperty("Cookie", "xf_csrf=1");
                    }
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    BufferedImage img = ImageIO.read(conn.getInputStream());
                    if (img != null) {
                        imageCache.put(url, img);
                        if (img.getWidth() > maxWidth) {
                            String key = url + "_" + maxWidth;
                            int newWidth = maxWidth;
                            int newHeight = img.getHeight() * newWidth / img.getWidth();
                            int type = img.getType() > 0 ? img.getType() : BufferedImage.TYPE_INT_ARGB;
                            BufferedImage scaled = new BufferedImage(newWidth, newHeight, type);
                            Graphics2D g2d = scaled.createGraphics();
                            g2d.drawImage(img, 0, 0, newWidth, newHeight, null);
                            g2d.dispose();
                            scaledCache.put(key, scaled);
                            return scaled;
                        } else {
                            return img;
                        }
                    }
                    return null;
                }
            } catch (Exception e) {
                devPrint("Failed to load image: " + url + " - " + e.getMessage());
                return null;
            }
        }
        private static boolean isTableElement(Element elem) {
            String tag = elem.tagName();
            return tag.equalsIgnoreCase("table") || tag.equalsIgnoreCase("thead") || tag.equalsIgnoreCase("tbody") || tag.equalsIgnoreCase("tfoot");
        }
        private static boolean hasTableChild(Element element) {
            for (Node node : element.childNodes()) {
                if (node instanceof Element && isTableElement((Element)node)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class LinkRegion {
        int x, y, width, height;
        String url;
        LinkRegion(int x, int y, int width, int height, String url) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.url = url;
        }
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
