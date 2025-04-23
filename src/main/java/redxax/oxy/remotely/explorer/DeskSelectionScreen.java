package redxax.oxy.remotely.explorer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.servers.RemoteHostInfo;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.util.ImageUtil.IconWithTooltip;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.ImageUtil.*;
import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.util.SoundUtils.playClick;

public class DeskSelectionScreen extends Screen {
    private final Minecraft minecraftClient;
    private final FileExplorerScreen parent;
    private final List<ObjectItem> objectItems = new ArrayList<>();
    private int itemWidth = 200;
    private final int itemHeight = 30;
    private final int columns = 3;
    private final int spacing = 2;
    private IconWithTooltip folderIcon, fileIcon, pinIcon, closeIcon;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    static class ObjectItem {
        String displayName;
        boolean isDirectory;
        Path localPath;
        boolean isRemote;
        RemoteHostInfo remoteHostInfo;
        Path remoteServerPath;
        boolean isFavorite;
    }

    public DeskSelectionScreen(Minecraft minecraftClient, FileExplorerScreen parent) {
        super(Component.literal("Desks/Servers"));
        this.minecraftClient = minecraftClient;
        this.parent = parent;
        originalMCScale = minecraftClient.getWindow().getGuiScale();
        targetScaleFactor = globalScaleFactor;
        minecraftClient.getWindow().setGuiScale(globalScaleFactor);
    }

    @Override
    protected void init() {
        super.init();
        try {
            folderIcon = new IconWithTooltip("/assets/remotely/icons/folder.png", "");
            fileIcon = new IconWithTooltip("/assets/remotely/icons/file.png", "");
            pinIcon = new IconWithTooltip("/assets/remotely/icons/pin.png", "");
            closeIcon = new IconWithTooltip("/assets/remotely/icons/close.png", "");
        } catch (Exception ignored) {}
        loadObjects();
    }

    private void loadObjects() {
        objectItems.clear();
        try {
            Path favoritesFilePath = Paths.get(String.valueOf(remotelyDir), "data", "favorites.json");
            Set<String> favoriteLines = new HashSet<>();
            if (Files.exists(favoritesFilePath)) {
                BufferedReader br = new BufferedReader(new FileReader(favoritesFilePath.toFile()));
                Gson gson = new Gson();
                List<String> favorites = gson.fromJson(br, new TypeToken<List<String>>(){}.getType());
                if (favorites != null) {
                    favoriteLines.addAll(favorites);
                }
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
                try {
                    String content = new String(Files.readAllBytes(serversJson));
                    content = content.replaceAll("(?<!\\\\)\\\\(?![\"\\\\/bfnrt])", "\\\\\\\\");
                    JsonReader reader = new JsonReader(new StringReader(content));
                    Gson gson = new GsonBuilder().setLenient().create();
                    List<Map<String, Object>> data = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>(){}.getType());
                    if (data != null) {
                        for (Map<String, Object> obj : data) {
                            String name = (String) obj.get("name");
                            String user = (String) obj.get("user");
                            String ip = (String) obj.get("ip");
                            double port = ((Number) obj.getOrDefault("port", 22.0)).doubleValue();
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
                    reader.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Error parsing remotehosts.json. Check for invalid escape sequences in file paths.");
                }
            }
            for (String fav : favoriteLines) {
                if (fav.startsWith("/")) {
                    boolean exists = false;
                    for (ObjectItem item : objectItems) {
                        if (item.isRemote && item.remoteServerPath != null &&
                                item.remoteServerPath.toString().equals(fav)) {
                            exists = true;
                            item.isFavorite = true;
                            break;
                        }
                    }
                    if (!exists) {
                        ObjectItem item = new ObjectItem();
                        item.displayName = fav.substring(fav.lastIndexOf('/') + 1);
                        if (item.displayName.isEmpty()) item.displayName = "/";
                        item.isDirectory = true;
                        item.isRemote = true;
                        item.remoteHostInfo = null;
                        item.remoteServerPath = Paths.get(fav);
                        item.isFavorite = true;
                        objectItems.add(item);
                    }
                } else {
                    Path p = Paths.get(fav);
                    if (Files.exists(p)) {
                        boolean exists = false;
                        String normalizedPath = p.toAbsolutePath().normalize().toString();
                        for (ObjectItem item : objectItems) {
                            if (!item.isRemote && item.localPath != null &&
                                    item.localPath.toAbsolutePath().normalize().toString().equals(normalizedPath)) {
                                exists = true;
                                item.isFavorite = true;
                                break;
                            }
                        }
                        if (!exists) {
                            ObjectItem item = new ObjectItem();
                            item.displayName = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                            item.isDirectory = Files.isDirectory(p);
                            item.localPath = p;
                            item.isRemote = false;
                            item.isFavorite = true;
                            objectItems.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        int rows = (int) Math.ceil((double) objectItems.size() / columns);
        maxScroll = Math.max(0, rows * (itemHeight + spacing) + spacing - (this.height - 60));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawScreenHeader(context, width, height, width - 5, mouseX, mouseY, this, minecraftClient, closeIcon, null, null, null, null, null, null, null, null);
        context.drawString(this.font, Component.literal("Remotely - New Tab"), 10, 10, globalTextColor, Config.shadow);
        int gridX = 5;
        int gridY = 60;
        int gridWidth = this.width - 5;
        int gridHeight = this.height - 5;
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
            int bgColor = getElementBackgroundColor(item.hashCode(), hovered, item.isFavorite, true, false, false, false);
            int id = ("explorer" + item.hashCode()).hashCode();
            float targetOffset = hovered ? -2f : 0f;
            float currentOffset = elevationOffsets.getOrDefault(id, 0f);
            currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
            elevationOffsets.put(id, currentOffset);
            context.pose().pushPose();
            context.pose().translate(0, currentOffset, 0);
            context.fill(drawX, drawY, drawX + itemWidth, drawY + itemHeight, bgColor);
            drawInnerBorder(context, drawX, drawY, itemWidth, itemHeight, getElementBorderColor(item.hashCode(), hovered, item.isFavorite, true, false, false, false));
            drawOuterBorder(context, drawX, drawY, itemWidth, itemHeight, globalOuterBorder);
            BufferedImage icon = (item.isDirectory ? folderIcon.getImage() : fileIcon.getImage());
            drawPixelArt(context, drawX + 7, drawY + (itemHeight / 2) - 8, 16, 16, icon);
            if (item.isFavorite) {
                drawPixelArt(context, item.isDirectory ? drawX + 2 : drawX + 4, drawY + (itemHeight / 2) - 8, 16, 16, pinIcon.getImage());
            }
            String firstLine = item.displayName;
            String secondLine = item.isRemote ? (item.remoteServerPath != null ? item.remoteServerPath.toString() : "") : item.localPath.toAbsolutePath().normalize().toString();
            int maxTextWidth = itemWidth - 32;
            if (font.width(secondLine) > maxTextWidth) {
                while (font.width(secondLine + "...") > maxTextWidth && !secondLine.isEmpty()) {
                    secondLine = secondLine.substring(0, secondLine.length() - 1);
                }
                secondLine = secondLine + "...";
            }
            context.drawString(this.font, Component.literal(firstLine), drawX + 25, drawY + 7, Config.globalTextColor, Config.shadow);
            context.drawString(this.font, Component.literal(secondLine), drawX + 25, drawY + 18, globalDarkTextColor, Config.shadow);
            idx++;
            context.pose().popPose();
        }
        animatedScaling(context, this, minecraftClient);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= width - 23 && mouseX <= width - 6 && mouseY >= 6 && mouseY <= 24) {
                playClick();
                minecraftClient.setScreen(parent);
                return true;
            }
            itemWidth = (this.width - (columns + 1) * spacing - 2 * spacing) / columns;
            int headerY = 35;
            int gridY = headerY + 10;
            int startY = gridY + spacing;
            for (int i = 0; i < objectItems.size(); i++) {
                ObjectItem item = objectItems.get(i);
                int row = i / columns;
                int col = i % columns;
                int drawX = spacing + col * (itemWidth + spacing) + spacing;
                int drawY = startY + row * (itemHeight + spacing) - scrollOffset;
                if (mouseX >= drawX && mouseX <= drawX + itemWidth && mouseY >= drawY && mouseY <= drawY + itemHeight) {
                    playClick();
                    if (!item.isRemote) {
                        if (item.isDirectory) {
                            FileExplorerScreen.TabData td = new FileExplorerScreen.TabData(item.localPath.toAbsolutePath().normalize(), false, null);
                            parent.tabs.add(new FileExplorerScreen.Tab(td));
                            parent.currentTabIndex = parent.tabs.size() - 1;
                            parent.loadDirectory(td.path, false, false, false);
                            minecraftClient.setScreen(parent);
                        } else {
                            minecraftClient.setScreen(new FileEditorScreen(minecraftClient, parent, item.localPath.toAbsolutePath().normalize(), new ServerInfo(false, null, item.localPath.toAbsolutePath().normalize().toString())));
                        }
                    } else {
                        if (item.remoteServerPath == null || item.remoteHostInfo == null) {
                            if (item.isDirectory) {
                                FileExplorerScreen.TabData td = new FileExplorerScreen.TabData(Paths.get("/"), true, item.remoteHostInfo);
                                parent.tabs.add(new FileExplorerScreen.Tab(td));
                                parent.currentTabIndex = parent.tabs.size() - 1;
                                parent.loadDirectory(td.path, false, false, false);
                                minecraftClient.setScreen(parent);
                            } else {
                                minecraftClient.setScreen(new FileEditorScreen(minecraftClient, parent, Paths.get("/"), new ServerInfo(true, item.remoteHostInfo, "/")));
                            }
                        } else {
                            if (item.isDirectory) {
                                FileExplorerScreen.TabData td = new FileExplorerScreen.TabData(item.remoteServerPath, true, item.remoteHostInfo);
                                parent.tabs.add(new FileExplorerScreen.Tab(td));
                                parent.currentTabIndex = parent.tabs.size() - 1;
                                parent.loadDirectory(td.path, false, false, false);
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
    public boolean mouseScrolled(double mouseX, double mouseY,/*? !=1.20.1 {*/ double horizontalAmount, /*?}*/ double verticalAmount) {
        scaleScroll(verticalAmount);
        scrollOffset -= (int) (verticalAmount * 10);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        return true;
    }

    @Override
    public void removed() {
        minecraftClient.getWindow().setGuiScale(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
    }
}
