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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
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
    private int cachedContentHeight = -1;
    private int cachedEditorWidth = -1;
    private List<HTMLRenderer.RenderCommand> cachedRenderCommands = null;

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
        if (cachedEditorWidth != editorWidth || cachedRenderCommands == null) {
            HTMLRenderer.RenderResult result = HTMLRenderer.buildRenderCommands(htmlDocument, contentX, editorY + padding, contentWidth, minecraftClient.textRenderer, imageCache, scaledImageCache, padding);
            cachedRenderCommands = result.commands;
            cachedContentHeight = result.totalHeight;
            cachedEditorWidth = editorWidth;
        }
        int maxScrollOffset = Math.max(0, cachedContentHeight - (editorHeight - padding * 2));
        targetScrollOffset = Math.min(targetScrollOffset, maxScrollOffset);
        context.enableScissor(editorX, editorY, editorX + editorWidth, editorY + editorHeight);
        for (HTMLRenderer.RenderCommand cmd : cachedRenderCommands) {
            int drawY = cmd.y - (int) scrollOffset;
            if (drawY + cmd.height < editorY || drawY > editorY + editorHeight) continue;
            switch(cmd.type) {
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
}
