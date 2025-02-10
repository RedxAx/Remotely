package redxax.oxy.servers;

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
import redxax.oxy.api.IRemotelyResource;
import redxax.oxy.config.Config;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static redxax.oxy.Render.*;
import static redxax.oxy.config.Config.*;
import static redxax.oxy.util.DevUtil.devPrint;
import static redxax.oxy.util.ImageUtil.drawBufferedImage;

public class ResourcePageScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final PluginModManagerScreen parentScreen;
    private final IRemotelyResource resource;
    private String htmlContent = "";
    private Document htmlDocument;
    private boolean isLoadingMarkdown = true;
    private float scrollOffset = 0;
    private float targetScrollOffset = 0;
    private final Map<String, BufferedImage> imageCache = new HashMap<>();
    private final Map<String, BufferedImage> scaledImageCache = new HashMap<>();
    private final List<LinkRegion> linkRegions = new LinkedList<>();

    public ResourcePageScreen(MinecraftClient mc, PluginModManagerScreen parent, IRemotelyResource resource) {
        super(Text.literal(resource.getName()));
        this.minecraftClient = mc;
        this.parentScreen = parent;
        this.resource = resource;
        loadMarkdown();
    }

    private void loadMarkdown() {
        new Thread(() -> {
            String markdownContent = "";
            try {
                String url;
                if (resource.getSlug().startsWith("spigot_")) {
                    url = "https://www.spigotmc.org/resources/" + resource.getSlug().replace("spigot_", "") + "/readme";
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
                } else if (resource.getSlug().startsWith("hangar_")) {
                    url = "https://hangar.papermc.io/api/v1/projects/" + resource.getSlug().replace("hangar_", "") + "/readme";
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
                    markdownContent = JsonParser.parseReader(reader).getAsJsonObject().get("body").getAsString();
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

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        targetScrollOffset -= verticalAmount * 30;
        targetScrollOffset = Math.max(0, targetScrollOffset);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int headerHeight = 30;
        int buttonW = 60;
        int buttonH = 20;
        int spacing = 10;
        int backButtonX = this.width - buttonW - 10;
        int siteButtonX = backButtonX - (buttonW + spacing);
        int downloadButtonX = siteButtonX - (buttonW + spacing);
        int buttonY = (headerHeight - buttonH) / 2;
        if (mouseX >= downloadButtonX && mouseX <= downloadButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH) {
            if (resource.getFileName().toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
                parentScreen.installMrPack(resource);
            } else {
                parentScreen.fetchAndInstallResource(resource);
            }
            return true;
        }
        if (mouseX >= siteButtonX && mouseX <= siteButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH) {
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
        if (mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH) {
            minecraftClient.setScreen(parentScreen);
            return true;
        }
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String getSiteUrlForResource() {
        if (resource.getSlug().startsWith("spigot_")) {
            return "https://www.spigotmc.org/resources/" + resource.getSlug().replace("spigot_", "") + "/";
        } else if (resource.getSlug().startsWith("hangar_")) {
            return "https://hangar.papermc.io/projects/" + resource.getProjectId();
        } else {
            return "https://modrinth.com/mod/" + resource.getSlug();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        scrollOffset += (targetScrollOffset - scrollOffset) * delta * 0.2f;
        context.fillGradient(0, 0, this.width, this.height, browserScreenBackgroundColor, browserScreenBackgroundColor);
        int headerHeight = 30;
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
        drawCustomButton(context, downloadButtonX, buttonY, "Download", minecraftClient, mouseX >= downloadButtonX && mouseX <= downloadButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH, false, true, buttonTextColor, buttonTextDeleteColor);
        drawCustomButton(context, siteButtonX, buttonY, "Site", minecraftClient, mouseX >= siteButtonX && mouseX <= siteButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH, false, true, buttonTextColor, buttonTextDeleteColor);
        drawCustomButton(context, backButtonX, buttonY, "Back", minecraftClient, mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH, false, true, buttonTextColor, buttonTextDeleteColor);
        if (isLoadingMarkdown) {
            context.drawText(minecraftClient.textRenderer, Text.literal("Loading..."), this.width / 2 - 20, this.height / 2, screensTitleTextColor, Config.shadow);
            return;
        }
        linkRegions.clear();
        int editorX = 10;
        int editorY = headerHeight + 10;
        int editorWidth = this.width - 20;
        int editorHeight = this.height - headerHeight - 20;
        context.fill(editorX, editorY, editorX + editorWidth, editorY + editorHeight, editorInnerBackgroundColor);
        drawInnerBorder(context, editorX, editorY, editorWidth, editorHeight, editorBorderColor);
        drawOuterBorder(context, editorX, editorY, editorWidth, editorHeight, globalBottomBorder);
        int padding = 10;
        int contentX = editorX + padding;
        int contentWidth = editorWidth - 2 * padding;
        context.enableScissor(editorX, editorY, editorX + editorWidth, editorY + editorHeight);
        int contentY = editorY + padding - (int) scrollOffset;
        HTMLRenderer.renderHtml(context, htmlDocument, contentX, contentY, contentWidth, minecraftClient.textRenderer, linkRegions, imageCache, scaledImageCache, padding);
        context.disableScissor();
    }

    private static class HTMLRenderer {
        static class InlineState {
            int x, y;
            InlineState(int x, int y) { this.x = x; this.y = y; }
        }
        static int renderHtml(DrawContext context, Document doc, int x, int y, int maxWidth, TextRenderer renderer, List<LinkRegion> links, Map<String, BufferedImage> imageCache, Map<String, BufferedImage> scaledCache, int padding) {
            int currentY = y;
            for (Element elem : doc.body().children()) {
                currentY = renderBlock(context, elem, x, currentY, maxWidth, renderer, links, imageCache, scaledCache);
                currentY += 5;
            }
            return currentY - y;
        }
        static int renderBlock(DrawContext context, Element element, int x, int y, int maxWidth, TextRenderer renderer, List<LinkRegion> links, Map<String, BufferedImage> imageCache, Map<String, BufferedImage> scaledCache) {
            String tag = element.tagName();
            if (tag.equals("br")) {
                return y + renderer.fontHeight;
            }
            if (tag.equals("hr")) {
                context.fill(x, y + renderer.fontHeight / 2, x + maxWidth, y + renderer.fontHeight / 2 + 1, 0xFFAAAAAA);
                return y + renderer.fontHeight;
            }
            if (tag.equals("img")) {
                String src = element.attr("src");
                BufferedImage img = loadImage(src, imageCache, maxWidth, scaledCache);
                if (img != null) {
                    int imgW = Math.min(img.getWidth(), maxWidth);
                    int imgH = img.getHeight() * imgW / img.getWidth();
                    drawBufferedImage(context, img, x, y, imgW, imgH);
                    return y + imgH + 5;
                }
                return y;
            }
            if (tag.equals("ul") || tag.equals("ol")) {
                int index = 1;
                for (Element li : element.children()) {
                    String bullet = tag.equals("ul") ? "• " : index + ". ";
                    context.drawText(renderer, Text.literal(bullet), x, y, 0xFFFFFFFF, Config.shadow);
                    InlineState state = renderInline(context, li.childNodes(), x + renderer.getWidth(bullet), x + renderer.getWidth(bullet), y, maxWidth - renderer.getWidth(bullet), renderer, links, "", imageCache, scaledCache);
                    y = state.y + renderer.fontHeight;
                    index++;
                }
                return y;
            }
            if (tag.equals("table")) {
                for (Element row : element.select("tr")) {
                    StringBuilder rowText = new StringBuilder();
                    for (Element cell : row.children()) {
                        rowText.append(cell.text()).append(" | ");
                    }
                    if (rowText.length() >= 3) {
                        rowText.setLength(rowText.length() - 3);
                    }
                    context.drawText(renderer, Text.literal(rowText.toString()), x, y, 0xFFFFFFFF, Config.shadow);
                    y += renderer.fontHeight;
                }
                return y;
            }
            if (tag.matches("h[1-6]")) {
                int level = Integer.parseInt(tag.substring(1));
                float scale = 2.0f - (level - 1) * 0.2f;
                String newFormat = "§l";
                InlineState state = renderInline(context, element.childNodes(), x, x, y, maxWidth, renderer, links, newFormat, imageCache, scaledCache);
                y = state.y + (int) (renderer.fontHeight * scale) + 4;
                context.fill(x, y, x + maxWidth, y + 1, 0xFFAAAAAA);
                return y + 5;
            }
            InlineState state = renderInline(context, element.childNodes(), x, x, y, maxWidth, renderer, links, "", imageCache, scaledCache);
            return state.y + renderer.fontHeight;
        }
        static InlineState renderInline(DrawContext context, List<Node> nodes, int startX, int x, int y, int maxWidth, TextRenderer renderer, List<LinkRegion> links, String format, Map<String, BufferedImage> imageCache, Map<String, BufferedImage> scaledCache) {
            InlineState state = new InlineState(x, y);
            for (Node node : nodes) {
                if (node instanceof TextNode) {
                    String text = ((TextNode) node).text();
                    state = renderTextWithWrap(context, text, startX, state, maxWidth, renderer, format);
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
                            state.x = startX;
                            drawBufferedImage(context, img, startX, state.y, imgW, imgH);
                            state.y += imgH + 5;
                            state.x = startX;
                        }
                        continue;
                    } else if (tag.equals("a")) {
                        newFormat += "§n§9";
                        int linkStartX = state.x;
                        int linkStartY = state.y;
                        InlineState innerState = renderInline(context, elem.childNodes(), startX, state.x, state.y, maxWidth, renderer, links, newFormat, imageCache, scaledCache);
                        int linkWidth = innerState.x - linkStartX;
                        if(linkWidth > 0) {
                            links.add(new LinkRegion(linkStartX, linkStartY, linkWidth, renderer.fontHeight, elem.attr("href")));
                        }
                        state = innerState;
                    } else if (tag.equals("code")) {
                        newFormat += "§7";
                        state = renderInline(context, elem.childNodes(), startX, state.x, state.y, maxWidth, renderer, links, newFormat, imageCache, scaledCache);
                    } else if (tag.equals("strong") || tag.equals("b")) {
                        newFormat += "§l";
                        state = renderInline(context, elem.childNodes(), startX, state.x, state.y, maxWidth, renderer, links, newFormat, imageCache, scaledCache);
                    } else if (tag.equals("em") || tag.equals("i")) {
                        newFormat += "§o";
                        state = renderInline(context, elem.childNodes(), startX, state.x, state.y, maxWidth, renderer, links, newFormat, imageCache, scaledCache);
                    } else {
                        state = renderInline(context, elem.childNodes(), startX, state.x, state.y, maxWidth, renderer, links, newFormat, imageCache, scaledCache);
                    }
                }
            }
            return state;
        }
        static InlineState renderTextWithWrap(DrawContext context, String text, int startX, InlineState state, int maxWidth, TextRenderer renderer, String format) {
            Pattern pattern = Pattern.compile("\\S+|\\s+");
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String token = matcher.group();
                int baseWidth = renderer.getWidth(Text.literal(token));
                if(format.contains("§l")) {
                    baseWidth += token.length();
                }
                if (state.x + baseWidth > startX + maxWidth) {
                    state.x = startX;
                    state.y += renderer.fontHeight;
                }
                context.drawText(renderer, Text.literal(format + token), state.x, state.y, 0xFFFFFFFF, Config.shadow);
                int tokenWidth = renderer.getWidth(Text.literal(token));
                if(format.contains("§l")) {
                    tokenWidth += token.length();
                }
                state.x += tokenWidth;
            }
            return state;
        }
        static BufferedImage loadImage(String url, Map<String, BufferedImage> imageCache, int maxWidth, Map<String, BufferedImage> scaledCache) {
            try {
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
                    BufferedImage img = ImageIO.read(imageUrl);
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
                return null;
            }
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
}
