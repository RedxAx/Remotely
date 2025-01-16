package redxax.oxy.explorer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.util.Config;
import redxax.oxy.servers.RemoteHostInfo;
import redxax.oxy.servers.ServerInfo;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static redxax.oxy.util.ImageUtil.*;
import static redxax.oxy.Render.*;

public class DeskSelectionScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final FileExplorerScreen parent;
    private List<ObjectItem> objectItems = new ArrayList<>();
    private int itemWidth = 200;
    private int itemHeight = 30;
    private int columns = 3;
    private int spacing = 2;
    private BufferedImage folderIcon;
    private BufferedImage fileIcon;
    private BufferedImage pinIcon;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int selectedIndex = -1;
    private int backButtonX;
    private int backButtonY;
    private int backButtonWidth = 60;
    private int backButtonHeight = 20;

    static class ObjectItem {
        String displayName;
        boolean isDirectory;
        Path localPath;
        boolean isRemote;
        RemoteHostInfo remoteHostInfo;
        Path remoteServerPath;
        boolean isFavorite;
    }

    public DeskSelectionScreen(MinecraftClient minecraftClient, FileExplorerScreen parent) {
        super(Text.literal("Desks/Servers"));
        this.minecraftClient = minecraftClient;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        try {
            folderIcon = loadResourceIcon("/assets/remotely/icons/folder.png");
            fileIcon = loadResourceIcon("/assets/remotely/icons/file.png");
            pinIcon = loadResourceIcon("/assets/remotely/icons/pin.png");
        } catch (Exception ignored) {}
        loadObjects();
        backButtonX = this.width - backButtonWidth - 10;
        backButtonY = 5;
    }

    private void loadObjects() {
        objectItems.clear();
        try {
            Path favoritesFilePath = Paths.get("C:/remotely/data/favorites.dat");
            Set<String> favoriteLines = new HashSet<>();
            if (Files.exists(favoritesFilePath)) {
                favoriteLines.addAll(Files.readAllLines(favoritesFilePath));
            }
            for (File root : File.listRoots()) {
                ObjectItem item = new ObjectItem();
                item.displayName = root.toString();
                item.isDirectory = true;
                item.localPath = root.toPath();
                item.isRemote = false;
                item.isFavorite = favoriteLines.contains(root.toString());
                objectItems.add(item);
            }
            Path serversJson = Paths.get(System.getProperty("user.dir"), "remotely", "servers", "remotehosts.json");
            if (Files.exists(serversJson)) {
                BufferedReader br = new BufferedReader(new FileReader(serversJson.toFile()));
                Gson gson = new Gson();
                List<Map<String, Object>> data = gson.fromJson(br, new TypeToken<List<Map<String, Object>>>(){}.getType());
                for (Map<String, Object> obj : data) {
                    String name = (String) obj.get("name");
                    String user = (String) obj.get("user");
                    String ip = (String) obj.get("ip");
                    double port = (double) obj.get("port");
                    String password = (String) obj.get("password");
                    RemoteHostInfo info = new RemoteHostInfo();
                    info.setUser(user);
                    info.setIp(ip);
                    info.setPort((int) port);
                    info.setPassword(password);
                    ObjectItem hostItem = new ObjectItem();
                    hostItem.displayName = name;
                    hostItem.isDirectory = true;
                    hostItem.isRemote = true;
                    hostItem.remoteHostInfo = info;
                    hostItem.isFavorite = false;
                    objectItems.add(hostItem);
                }
            }
            for (String fav : favoriteLines) {
                Path p = Paths.get(fav);
                if (Files.exists(p) && objectItems.stream().noneMatch(x -> p.equals(x.localPath))) {
                    ObjectItem item = new ObjectItem();
                    item.displayName = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                    item.isDirectory = Files.isDirectory(p);
                    item.localPath = p;
                    item.isRemote = false;
                    item.isFavorite = true;
                    objectItems.add(item);
                } else if (!Files.exists(p) && fav.startsWith("/")) {
                    ObjectItem item = new ObjectItem();
                    item.displayName = fav.substring(fav.lastIndexOf('/') + 1);
                    item.isDirectory = true;
                    item.isRemote = true;
                    item.remoteHostInfo = null;
                    item.remoteServerPath = Paths.get(fav);
                    item.isFavorite = true;
                    objectItems.add(item);
                }
            }
        } catch (Exception ignored) {}
        int rows = (int) Math.ceil((double) objectItems.size() / columns);
        maxScroll = Math.max(0, rows * (itemHeight + spacing) + spacing - (this.height - 60));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, 30, 0xFF222222);
        drawInnerBorder(context, 0, 0, this.width, 30, 0xFF333333);
        context.drawText(this.textRenderer, Text.literal("Remotely - New Tab"), 10, 10, textColor, Config.shadow);
        int headerY = 35;
        int gridX = spacing;
        int gridY = headerY + 10;
        int gridWidth = this.width - 2 * spacing;
        int gridHeight = this.height - gridY - 10;
        context.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, lighterColor);
        drawInnerBorder(context, gridX, gridY, gridWidth, gridHeight, borderColor);
        itemWidth = (gridWidth - (columns + 1) * spacing) / columns;
        int startY = gridY + spacing;
        int idx = 0;
        for (ObjectItem item : objectItems) {
            int row = idx / columns;
            int col = idx % columns;
            int drawX = gridX + spacing + col * (itemWidth + spacing);
            int drawY = startY + row * (itemHeight + spacing) - scrollOffset;
            if (drawY + itemHeight < startY || drawY > gridY + gridHeight - spacing) {
                idx++;
                continue;
            }
            boolean hovered = mouseX >= drawX && mouseX <= drawX + itemWidth && mouseY >= drawY && mouseY <= drawY + itemHeight;
            int bgColor = item.isFavorite ? darkGold : (hovered ? highlightColor : elementBg);
            context.fill(drawX, drawY, drawX + itemWidth, drawY + itemHeight, bgColor);
            drawInnerBorder(context, drawX, drawY, itemWidth, itemHeight, item.isFavorite ? kingsGold : (hovered ? elementBorderHover : elementBorder));
            BufferedImage icon = item.isDirectory ? folderIcon : fileIcon;
            drawBufferedImage(context, icon, drawX + 7, drawY + (itemHeight / 2) - 8, 16, 16);
            if (item.isFavorite) {
                drawBufferedImage(context, pinIcon, item.isDirectory ? drawX + 2 : drawX + 4, drawY + (itemHeight / 2) - 8, 16, 16);
            }
            String firstLine = item.displayName;
            String secondLine = item.isRemote ? (item.remoteServerPath != null ? item.remoteServerPath.toString() : "") : item.localPath.toAbsolutePath().normalize().toString();
            int maxTextWidth = itemWidth - 32;
            if (textRenderer.getWidth(secondLine) > maxTextWidth) {
                while (textRenderer.getWidth(secondLine + "...") > maxTextWidth && secondLine.length() > 0) {
                    secondLine = secondLine.substring(0, secondLine.length() - 1);
                }
                secondLine = secondLine + "...";
            }
            context.drawText(this.textRenderer, Text.literal(firstLine), drawX + 25, drawY + 7, textColor, Config.shadow);
            context.drawText(this.textRenderer, Text.literal(secondLine), drawX + 25, drawY + 18, dimTextColor, Config.shadow);
            idx++;
        }
        boolean backButtonHovered = mouseX >= backButtonX && mouseX <= backButtonX + backButtonWidth && mouseY >= backButtonY && mouseY <= backButtonY + backButtonHeight;
        drawCustomButton(context, backButtonX, backButtonY, "Back", minecraftClient, backButtonHovered, false, true, textColor, deleteColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= backButtonX && mouseX <= backButtonX + backButtonWidth && mouseY >= backButtonY && mouseY <= backButtonY + backButtonHeight) {
                minecraftClient.setScreen(parent);
                return true;
            }
            itemWidth = (this.width - (columns + 1) * spacing - 2 * spacing) / columns;
            int headerY = 35;
            int gridY = headerY + 10;
            int gridWidth = this.width - 2 * spacing;
            int startY = gridY + spacing;
            for (int i = 0; i < objectItems.size(); i++) {
                ObjectItem item = objectItems.get(i);
                int row = i / columns;
                int col = i % columns;
                int drawX = spacing + col * (itemWidth + spacing) + spacing;
                int drawY = startY + row * (itemHeight + spacing) - scrollOffset;
                if (mouseX >= drawX && mouseX <= drawX + itemWidth && mouseY >= drawY && mouseY <= drawY + itemHeight) {
                    selectedIndex = i;
                    if (!item.isRemote) {
                        if (item.isDirectory) {
                            FileExplorerScreen.TabData td = new FileExplorerScreen.TabData(item.localPath.toAbsolutePath().normalize(), false, null);
                            parent.tabs.add(parent.new Tab(td));
                            parent.currentTabIndex = parent.tabs.size() - 1;
                            parent.loadDirectory(td.path, false, false);
                            minecraftClient.setScreen(parent);
                        } else {
                            minecraftClient.setScreen(new FileEditorScreen(minecraftClient, parent, item.localPath.toAbsolutePath().normalize(), new ServerInfo(false, null, item.localPath.toAbsolutePath().normalize().toString())));
                        }
                    } else {
                        if (item.remoteServerPath == null || item.remoteHostInfo == null) {
                            if (item.isDirectory) {
                                FileExplorerScreen.TabData td = new FileExplorerScreen.TabData(Paths.get("/"), true, item.remoteHostInfo);
                                parent.tabs.add(parent.new Tab(td));
                                parent.currentTabIndex = parent.tabs.size() - 1;
                                parent.loadDirectory(td.path, false, false);
                                minecraftClient.setScreen(parent);
                            } else {
                                minecraftClient.setScreen(new FileEditorScreen(minecraftClient, parent, Paths.get("/"), new ServerInfo(true, item.remoteHostInfo, "/")));
                            }
                        } else {
                            if (item.isDirectory) {
                                FileExplorerScreen.TabData td = new FileExplorerScreen.TabData(item.remoteServerPath, true, item.remoteHostInfo);
                                parent.tabs.add(parent.new Tab(td));
                                parent.currentTabIndex = parent.tabs.size() - 1;
                                parent.loadDirectory(td.path, false, false);
                                minecraftClient.setScreen(parent);
                            } else {
                                minecraftClient.setScreen(new FileEditorScreen(minecraftClient, parent, item.remoteServerPath, new ServerInfo(true, item.remoteHostInfo, item.remoteServerPath.toString())));
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= verticalAmount * 10;
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
    }
}
//almost