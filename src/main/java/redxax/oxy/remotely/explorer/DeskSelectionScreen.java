package redxax.oxy.remotely.explorer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.servers.RemoteHostInfo;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Sound;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.ImageUtil.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class DeskSelectionScreen extends ReScreen {
    private final FileExplorerScreen parent;

    private static BufferedImage diskIcon, folderIcon, fileIcon, pinIcon;

    static class ObjectItem {
        public boolean isDisk;
        String displayName;
        boolean isDirectory;
        Path localPath;
        boolean isRemote;
        RemoteHostInfo remoteHostInfo;
        Path remoteServerPath;
        boolean isFavorite;
    }

    public DeskSelectionScreen(FileExplorerScreen parent) {
        super(Text.literal("Desks/Servers"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        try {
            diskIcon = loadResourceIcon("/assets/remotely/icons/disk.png");
            folderIcon = loadResourceIcon("/assets/remotely/icons/folder.png");
            fileIcon = loadResourceIcon("/assets/remotely/icons/file.png");
            pinIcon = loadResourceIcon("/assets/remotely/icons/pin.png");
        } catch (Exception ignored) {}

        header().addRight("/assets/remotely/icons/close.png", () -> client.setScreen(parent), "Close").build();

        Container mainContainer = createContainer("main", 5, 36, width - 10, height - 5);
        mainContainer.columns(3).padding(2).layoutStyle(Container.LayoutStyle.RESTRICTED);
        setActiveContainer(mainContainer);

        loadObjects();
    }

    private void loadObjects() {
        Container mainContainer = container();
        if (mainContainer == null) return;
        mainContainer.clearWidgets();

        List<ObjectItem> objectItems = new ArrayList<>();

        try {
            Path favoritesFilePath = Paths.get(String.valueOf(remotelyDir), "data", "favorites.json");
            Set<String> favoriteLines = new HashSet<>();
            if (Files.exists(favoritesFilePath)) {
                try (BufferedReader br = new BufferedReader(new FileReader(favoritesFilePath.toFile()))) {
                    Gson gson = new Gson();
                    List<String> favorites = gson.fromJson(br, new TypeToken<List<String>>(){}.getType());
                    if (favorites != null) {
                        favoriteLines.addAll(favorites);
                    }
                }
            }

            for (File root : File.listRoots()) {
                ObjectItem item = new ObjectItem();
                item.displayName = root.toString();
                item.isDisk = true;
                item.isDirectory = true;
                item.localPath = root.toPath();
                item.isRemote = false;
                item.isFavorite = favoriteLines.contains(root.toString());
                objectItems.add(item);
            }

            Path serversJson = Paths.get(System.getProperty("user.dir"), "assets/remotely", "servers", "remotehosts.json");
            if (Files.exists(serversJson)) {
                try {
                    String content = new String(Files.readAllBytes(serversJson));
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

        for (ObjectItem item : objectItems) {
            mainContainer.addWidget(new DeskItemWidget.Builder(item, parent).build());
        }
        mainContainer.updateWidgetPositions();
    }

    public static class DeskItemWidget extends AnimatedWidget {
        private final ObjectItem item;
        private final FileExplorerScreen parentScreen;

        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

        public static class Builder extends AnimatedWidget.Builder<DeskItemWidget, Builder> {
            public Builder(ObjectItem item, FileExplorerScreen parent) {
                super(new DeskItemWidget(item, parent));
                this.size(0, 30);
            }

            @Override
            protected Builder self() {
                return this;
            }
        }

        protected DeskItemWidget(ObjectItem item, FileExplorerScreen parent) {
            super(0, 0, 0, 30, Text.literal(item.displayName));
            this.item = item;
            this.parentScreen = parent;
        }

        @Override
        protected void drawContent(DrawContext context, int mouseX, int mouseY) {
            BufferedImage iconToShow = item.isDisk ? diskIcon : (item.isDirectory ? folderIcon : fileIcon);
            if (iconToShow != null) {
                drawPixelArt(context, getX() + 7, getY() + (getHeight() / 2) - 8, 16, 16, iconToShow);
            }
            if (item.isFavorite && pinIcon != null) {
                drawPixelArt(context, getX() + 2, getY() + (getHeight() / 2) - 8, 16, 16, pinIcon);
            }

            String firstLine = item.displayName;
            String secondLine = item.isRemote ? (item.remoteServerPath != null ? item.remoteServerPath.toString() : (item.remoteHostInfo != null ? item.remoteHostInfo.getIp() : "")) : (item.localPath != null ? item.localPath.toAbsolutePath().normalize().toString() : "");

            int maxTextWidth = getWidth() - 32;
            if (tr.getWidth(secondLine) > maxTextWidth) {
                while (tr.getWidth(secondLine + "...") > maxTextWidth && !secondLine.isEmpty()) {
                    secondLine = secondLine.substring(0, secondLine.length() - 1);
                }
                secondLine = secondLine + "...";
            }
            context.drawText(tr, Text.literal(firstLine), getX() + 25, getY() + 7, Config.globalTextColor, Config.shadow);
            context.drawText(tr, Text.literal(secondLine), getX() + 25, getY() + 18, Config.globalDarkTextColor, Config.shadow);
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (button == 0) {
                playSound(Sound.CREATE);
                if (!item.isRemote) {
                    ServerInfo newServerInfo = new ServerInfo(false, null, item.localPath.toAbsolutePath().normalize().toString());
                    if (item.isDirectory || item.isDisk) {
                        mc.setScreen(new FileExplorerScreen(parentScreen, newServerInfo));
                    } else {
                        mc.setScreen(new FileEditorScreen(mc, parentScreen, item.localPath.toAbsolutePath().normalize(), newServerInfo));
                    }
                } else {
                    if (item.remoteHostInfo != null) {
                        mc.setScreen(new FileExplorerScreen(parentScreen, item.remoteHostInfo));
                    } else {
                        new Notification("Cannot open remote favorite", "Host information is missing.", Notification.Type.ERROR);
                    }
                }
            }
        }
    }
}