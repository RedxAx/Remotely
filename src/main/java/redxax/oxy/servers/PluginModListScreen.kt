package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class PluginModListScreen extends Screen {
    private final MinecraftClient mc;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private List<EntryInfo> entries;
    private float smoothOffset = 0;
    private float targetOffset = 0;
    private float scrollSpeed = 0.2f;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private int entryHeight = 40;

    public PluginModListScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal(info.type.equalsIgnoreCase("paper")||info.type.equalsIgnoreCase("spigot")?"Installed Plugins":"Installed Mods"));
        this.mc = mc;
        this.parent = parent;
        this.serverInfo = info;
    }

    @Override
    protected void init() {
        super.init();
        loadEntries();
    }

    private void loadEntries() {
        entries = new ArrayList<>();
        Path dir;
        boolean isPlugin = serverInfo.type.equalsIgnoreCase("paper") || serverInfo.type.equalsIgnoreCase("spigot");
        if (isPlugin) {
            dir = Paths.get(serverInfo.path, "plugins");
        } else {
            dir = Paths.get(serverInfo.path, "mods");
        }
        try {
            if (Files.exists(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path p : stream) {
                        if (p.toString().toLowerCase().endsWith(".jar")) {
                            EntryInfo info = new EntryInfo();
                            info.path = p;
                            if (isPlugin) {
                                readPluginInfo(info);
                            } else {
                                info.displayName = p.getFileName().toString();
                                info.version = "";
                            }
                            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                            long diff = System.currentTimeMillis() - attr.creationTime().toMillis();
                            info.dateString = humanReadableTime(diff) + " Ago";
                            info.isPlugin = isPlugin;
                            entries.add(info);
                        }
                    }
                }
            }
        } catch (IOException ignored){}
    }

    private void readPluginInfo(EntryInfo info) {
        try (FileSystem fs = FileSystems.newFileSystem(info.path, (ClassLoader)null)) {
            Path pluginYml = fs.getPath("plugin.yml");
            Path paperPluginYml = fs.getPath("paper-plugin.yml");
            if (Files.exists(pluginYml)) {
                readYaml(pluginYml, info);
            } else if (Files.exists(paperPluginYml)) {
                readYaml(paperPluginYml, info);
            } else {
                info.displayName = info.path.getFileName().toString();
                info.version = "";
            }
        } catch (IOException e) {
            info.displayName = info.path.getFileName().toString();
            info.version = "";
        }
    }

    private void readYaml(Path yml, EntryInfo info) throws IOException {
        List<String> lines = Files.readAllLines(yml);
        String name = null;
        String version = null;
        for (String line : lines) {
            line = line.trim();
            if (line.toLowerCase().startsWith("name:")) {
                name = line.substring(line.indexOf(":") + 1).trim().replaceAll("^['\"]|['\"]$", "");
            } else if (line.toLowerCase().startsWith("version:")) {
                version = line.substring(line.indexOf(":") + 1).trim().replaceAll("^['\"]|['\"]$", "");
            }
            if (name != null && version != null) break;
        }
        info.displayName = name == null ? info.path.getFileName().toString() : name;
        info.version = version == null ? "" : version;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelHeight = this.height - 60;
        int maxScroll = Math.max(0, entries.size()*entryHeight - (panelHeight));
        targetOffset -= verticalAmount * entryHeight * 2;
        if (targetOffset < 0) targetOffset = 0;
        if (targetOffset > maxScroll) targetOffset = maxScroll;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int backButtonX = this.width - 60;
        int backButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        int downloadButtonX = backButtonX - (btnW + 10);
        int downloadButtonY = 5;

        if (mouseX >= backButtonX && mouseX <= backButtonX+btnW && mouseY >= backButtonY && mouseY <= backButtonY+btnH && button == 0) {
            mc.setScreen(parent);
            return true;
        }

        if (mouseX >= downloadButtonX && mouseX <= downloadButtonX+btnW && mouseY >= downloadButtonY && mouseY <= downloadButtonY+btnH && button == 0) {
            mc.setScreen(new PluginModManagerScreen(mc, this, serverInfo));
            return true;
        }

        int listY = 50;
        int relativeY = (int) mouseY - listY + (int) smoothOffset;
        int index = relativeY / entryHeight;
        if (index >= 0 && index < entries.size()) {
            EntryInfo e = entries.get(index);
            int y = listY + (index * entryHeight) - (int)smoothOffset;
            if (e.isPlugin) {
                int folderBtnX = this.width - 30;
                int folderBtnW = 20;
                int folderBtnH = 20;
                int folderBtnY = y + entryHeight - folderBtnH - 5;
                if (mouseX >= folderBtnX && mouseX <= folderBtnX+folderBtnW && mouseY >= folderBtnY && mouseY <= folderBtnY+folderBtnH && button == 0) {
                    Path dir = Paths.get(serverInfo.path, "plugins", e.displayName);
                    mc.setScreen(new FileExplorerScreen(mc, this, serverInfo) {
                        @Override
                        protected void init() {
                            super.init();
                            loadDirectory(dir);
                        }
                    });
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

        context.fill(0, 0, this.width, 30, lighterColor);
        drawInnerBorder(context, 0, 0, this.width, 30, borderColor);
        context.drawText(this.textRenderer, this.title, 10, 10, textColor, false);

        int backButtonX = this.width - 60;
        int backButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW && mouseY >= backButtonY && mouseY <= backButtonY + btnH;
        int bgBack = hoveredBack ? highlightColor : lighterColor;
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack);
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, borderColor);
        int twb = this.textRenderer.getWidth("Back");
        int txb = backButtonX + (btnW - twb) / 2;
        int ty = backButtonY + (btnH - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Back"), txb, ty, textColor, false);

        int downloadButtonX = backButtonX - (btnW + 10);
        int downloadButtonY = 5;
        boolean hoveredDownload = mouseX >= downloadButtonX && mouseX <= downloadButtonX + btnW && mouseY >= downloadButtonY && mouseY <= downloadButtonY + btnH;
        int bgDownload = hoveredDownload ? highlightColor : lighterColor;
        context.fill(downloadButtonX, downloadButtonY, downloadButtonX + btnW, downloadButtonY + btnH, bgDownload);
        drawInnerBorder(context, downloadButtonX, downloadButtonY, btnW, btnH, borderColor);
        int twd = this.textRenderer.getWidth("Download");
        int txd = downloadButtonX + (btnW - twd) / 2;
        context.drawText(this.textRenderer, Text.literal("Download"), txd, ty, textColor, false);

        int listY = 50;
        int panelHeight = this.height - 60;

        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;

        // Adjust scissor to clip only the top and bottom edges
        context.enableScissor(0, listY, this.width, listY + panelHeight);

        for (int i = 0; i < entries.size(); i++) {
            EntryInfo e = entries.get(i);
            int y = listY + (i * entryHeight) - (int) smoothOffset;
            if (y + entryHeight < listY || y > listY + panelHeight) continue;
            boolean hovered = mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= y && mouseY <= y + entryHeight;
            int bg = hovered ? highlightColor : lighterColor;
            context.fill(10, y, this.width - 10, y + entryHeight, bg);
            drawInnerBorder(context, 10, y, this.width - 20, entryHeight, borderColor);

            if (e.isPlugin) {
                context.drawText(this.textRenderer, Text.literal(e.displayName), 15, y + 5, textColor, false);
                context.drawText(this.textRenderer, Text.literal(e.version.isEmpty() ? "Unknown Version" : e.version), 15, y + 20, 0xFFAAAAAA, false);
                int folderBtnX = this.width - 40;
                int folderBtnY = y + entryHeight - 25;
                int folderBtnW = 20;
                int folderBtnH = 20;
                boolean folderHover = mouseX >= folderBtnX && mouseX <= folderBtnX + folderBtnW && mouseY >= folderBtnY && mouseY <= folderBtnY + folderBtnH;
                int fbg = folderHover ? highlightColor : lighterColor;
                context.fill(folderBtnX, folderBtnY, folderBtnX + folderBtnW, folderBtnY + folderBtnH, fbg);
                drawInnerBorder(context, folderBtnX, folderBtnY, folderBtnW, folderBtnH, borderColor);
                context.drawText(this.textRenderer, Text.literal("§6🗁"), folderBtnX + 4, folderBtnY + 4, textColor, false);
                int infoX = folderBtnX + folderBtnW + 5;
                context.drawText(this.textRenderer, Text.literal(e.dateString), infoX, y + 20, 0xFFAAAAAA, false);
            } else {
                context.drawText(this.textRenderer, Text.literal(e.displayName), 15, y + 5, textColor, false);
                context.drawText(this.textRenderer, Text.literal(e.dateString), 15, y + 20, 0xFFAAAAAA, false);
            }
        }

        context.disableScissor();

        if (smoothOffset > 0) {
            context.fillGradient(0, listY, this.width, listY + 10, 0x80000000, 0x00000000);
        }
        if (smoothOffset < Math.max(0, entries.size() * entryHeight - panelHeight)) {
            context.fillGradient(0, listY + panelHeight - 10, this.width, listY + panelHeight, 0x00000000, 0x80000000);
        }
    }

    private String humanReadableTime(long ms) {
        long seconds = ms/1000;
        if (seconds < 60) return seconds+" Seconds";
        long minutes = seconds/60;
        if (minutes < 60) return minutes+" Minutes";
        long hours = minutes/60;
        if (hours < 24) return hours+" Hours";
        long days = hours/24;
        return days+" Days";
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x+w, y+1, c);
        context.fill(x, y+h-1, x+w, y+h, c);
        context.fill(x, y, x+1, y+h, c);
        context.fill(x+w-1, y, x+w, y+h, c);
    }

    @Override
    public void close() {
        mc.setScreen(parent);
    }

    private static class EntryInfo {
        Path path;
        String displayName;
        String version;
        String dateString;
        boolean isPlugin;
    }
}