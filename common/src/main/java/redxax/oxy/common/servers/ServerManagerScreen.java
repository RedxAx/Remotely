package redxax.oxy.common.servers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.common.RemotelyClient;
import redxax.oxy.common.Render;
import redxax.oxy.common.SSHManager;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.explorer.FileExplorerScreen;
import redxax.oxy.common.terminal.MultiTerminalScreen;
import redxax.oxy.common.terminal.ServerTerminalInstance;
import redxax.oxy.common.terminal.TerminalInstance;
import redxax.oxy.common.util.ImageUtil;

import static redxax.oxy.common.config.Config.windowsBackground;
import static redxax.oxy.common.servers.BrowserScreen.checkIfMcefExist;
import static redxax.oxy.common.servers.SettingsScreen.ServerSettingType.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.common.util.Notification;

import static redxax.oxy.common.Render.*;
import static redxax.oxy.common.config.Config.*;
import static redxax.oxy.common.util.DevUtil.devPrint;
import static redxax.oxy.common.util.ImageUtil.*;
import static redxax.oxy.common.util.SoundUtils.playClick;

public class ServerManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    private List<ServerInfo> localServers;
    private final List<RemoteHostInfo> remoteHosts = new ArrayList<>();
    private int activeTabIndex = 0;
    private boolean editingServer;
    private final int serverPopupWidth = 350;
    private final int serverPopupHeight = 160;
    private final StringBuilder serverNameBuffer = new StringBuilder();
    private final StringBuilder serverVersionBuffer = new StringBuilder();
    private int serverNameCursorPos = 0;
    private int serverVersionCursorPos = 0;
    private final int verticalPadding = 2;
    private boolean nameFieldFocused = true;
    private boolean versionFieldFocused = false;
    private float targetOffset = 0;
    private boolean serverTypePopupActive;
    private int serverTypePopupX;
    private int serverTypePopupY;
    private final int serverTypePopupWidth = 260;
    private final int serverTypePopupHeight = 140;
    private boolean remoteHostPopupActive;
    private final int remoteHostPopupW = 360;
    private final int remoteHostPopupH = 210;
    private final StringBuilder remoteHostNameBuffer = new StringBuilder();
    private final StringBuilder remoteHostUserBuffer = new StringBuilder("root");
    private final StringBuilder remoteHostIPBuffer = new StringBuilder();
    private final StringBuilder remoteHostPortBuffer = new StringBuilder("22");
    private final StringBuilder remoteHostPasswordBuffer = new StringBuilder();
    private boolean remoteHostCreationWarning;
    private RemoteHostField remoteHostActiveField = RemoteHostField.NONE;
    private boolean isEditingHost = false;
    private final List<BufferedImage> loadingFrames = new ArrayList<>();
    private final int entryHeight = 25;
    private final int topBarHeight = 30;
    private BufferedImage terminal, serverIcon, paper, vanilla, fabric, forge, neoforge, quilt;
    private IconWithTooltip terminalIcon, explorerIcon, editorIcon, browserIcon, settingsIcon;
    private final int taskbarHeight = 28;
    private final List<IconRect> serverIconRects = new ArrayList<>();
    private int selectedDesktopIndex = -1;
    private long lastClickTime = 0;
    private int lastClickedIndex = -1;
    private int draggingServerIndex = -1;
    private int draggingStartX = 0;
    private int draggingStartY = 0;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean isDragging = false;
    private boolean deletionPopupActive = false;
    private int deletionPopupServerIndex = -1;
    private final List<Float> iconPosX = new ArrayList<>();
    private final List<Float> iconPosY = new ArrayList<>();
    private boolean canDrag = false;
    private final ArrayList<Settings> settings = new ArrayList<>();
    private final ArrayList<Settings> clientSettings = new ArrayList<>();

    public List<RemoteHostInfo> getRemoteHosts() {
        return remoteHosts;
    }

    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    private static class IconRect {
        int x, y, width, height, serverIndex;
        boolean isCreate;
        IconRect(int x, int y, int width, int height, int serverIndex, boolean isCreate) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.serverIndex = serverIndex;
            this.isCreate = isCreate;
        }
    }

    public ServerManagerScreen(MinecraftClient minecraftClient, RemotelyClient remotelyClient, List<ServerInfo> servers) {
        super(Text.literal("Server Setup"));
        this.minecraftClient = minecraftClient;
        this.remotelyClient = remotelyClient;
        this.localServers = servers;
        originalMCScale = minecraftClient.getWindow().getScaleFactor();
        targetScaleFactor = globalScaleFactor;
        minecraftClient.getWindow().setScaleFactor(globalScaleFactor);
    }

    @Override
    protected void init() {
        super.init();
        if (localServers.isEmpty()) {
            loadSavedServers();
        }
        loadSavedRemoteHosts();
        scanForUnknownServers();
        defineSettings();
        activeTabIndex = remotelyClient.getSavedTabIndex();
        iconPosX.clear();
        iconPosY.clear();
        try {
            terminalIcon = new IconWithTooltip("/assets/remotely/icons/terminal.png", "Terminal");
            explorerIcon = new IconWithTooltip("/assets/remotely/icons/explorer.png", "File Explorer");
            browserIcon = new IconWithTooltip("/assets/remotely/icons/minibrowser.png", "Web Browser");
            settingsIcon = new IconWithTooltip("/assets/remotely/icons/remotely.png", "Settings");
            editorIcon = new IconWithTooltip("/assets/remotely/icons/text.png", "Text Editor");
            serverIcon = loadResourceIcon("/assets/remotely/icons/server.png");

            terminal = loadResourceIcon("/assets/remotely/icons/script.png");
            paper = loadResourceIcon("/assets/remotely/icons/paper.png");
            vanilla = loadResourceIcon("/assets/remotely/icons/vanilla.png");
            fabric =loadResourceIcon("/assets/remotely/icons/fabric.png");
            forge = loadResourceIcon("/assets/remotely/icons/forge.png");
            neoforge = loadResourceIcon("/assets/remotely/icons/neoforge.png");
            quilt = loadResourceIcon("/assets/remotely/icons/quilt.png");
        } catch (Exception e) {
            new Notification("Failed to load icons: " + e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void defineSettings() {
        settings.clear();
        settings.add(new Settings("Server Name", "The name of your server.", "General", "none", "server-name", TEXT, "My Server"));
        settings.add(new Settings("Game Mode", "server.properties", "gamemode", TAB_SWITCH, "Survival", "General", "Select the default game mode for players.", Arrays.asList("Survival", "Creative", "Adventure")));
        settings.add(new Settings("Difficulty", "server.properties", "difficulty", TAB_SWITCH, "Normal", "General", "Set the difficulty level of the server.", Arrays.asList("Peaceful", "Easy", "Normal", "Hard")));
        settings.add(new Settings("PvP", "Toggle player vs player combat.", "General", "server.properties", "pvp", TOGGLE, "true"));
        settings.add(new Settings("Hardcore", "Toggle hardcore mode (one life).", "General", "server.properties", "hardcore", TOGGLE, "false"));
        settings.add(new Settings("Server Type", "none", "server-type", SCROLL_SWITCH, "Paper", "General", "Choose the server software type.", Arrays.asList("Paper", "Vanilla", "Fabric", "Forge", "Neoforge", "Quilt")));
        settings.add(new Settings("Server Version", "Specify the Minecraft server version to run.", "General", "none", "server-version", TEXT, minecraftClient.getGameVersion()));
        settings.add(new Settings("Max Players", "server.properties", "max-players", SLIDER, "20", "Advanced", "Max online players limit.", 1, 200));
        settings.add(new Settings("MOTD", "Description for the server list.", "Advanced", "server.properties", "motd", TEXT, minecraftClient.getSession().getUsername() + "'s Server"));
        settings.add(new Settings("Seed", "Enter a specific seed (optional).", "Advanced", "server.properties", "level-seed", TEXT, ""));
        settings.add(new Settings("Spawn Protection", "server.properties", "spawn-protection", SLIDER, "16", "Advanced", "Set the radius of spawn protection (set 0 to disable).", 0, 32));
        settings.add(new Settings("Max Build Height", "server.properties", "max-build-height", SLIDER, "320", "Advanced", "Set the maximum height players can build to.", 0, 2048));
        settings.add(new Settings("Generate Structures", "Toggle whether structures are generated in the world.", "Advanced", "server.properties", "generate-structures", TOGGLE, "true"));
        settings.add(new Settings("Port", "Set the port number on which the server will run.", "Advanced", "server.properties", "server-port", TEXT, "25565"));
        settings.add(new Settings("Online Mode", "Authenticate with Minecraft (Secure).", "Advanced", "server.properties", "online-mode", TOGGLE, "true"));
        settings.add(new Settings("Whitelist", "Enable or disable the server whitelist.", "Advanced", "server.properties", "white-list", TOGGLE, "false"));
        settings.add(new Settings("Hide Online Players", "Hide online players from the server list.", "Advanced", "server.properties", "hide-online-players", TOGGLE, "false"));
        settings.add(new Settings("Allow Nether", "Toggle whether the Nether dimension is accessible.", "Advanced", "server.properties", "allow-nether", TOGGLE, "true"));
        settings.add(new Settings("Allow End", "Toggle whether the End dimension is accessible.", "Advanced", "bukkit.yml", "allow-end", TOGGLE, "true"));
        settings.add(new Settings("Use Custom Java", "Use a custom Java installation (Not recommended).", "Advanced", "none", "usecustomjava", TOGGLE, "false"));
        settings.add(new Settings("Java Version", "none", "launcher.java_version", TEXT, "", "Advanced", "Specify the Java version to use.", "usecustomjava", "true"));
        settings.add(new Settings("View Distance", "server.properties", "view-distance", SLIDER, "8", "Performance", "Adjust the number of chunks visible to players.", 1, 64));
        settings.add(new Settings("Simulation Distance", "server.properties", "simulation-distance", SLIDER, "8", "Performance", "Set the simulation distance (server tick radius).", 1, 64));
        settings.add(new Settings("Memory", "Set the maximum memory allocation for the server.", "Performance", "none", "launcher.memory", TEXT, "4G"));
        settings.add(new Settings("Aikars Flags", "Custom flags that highly optimizes server performance.", "Performance", "none", "launcher.aikars_flags", TOGGLE, "true"));
        settings.add(new Settings("JVM Arguments", "Custom JVM arguments.", "Advanced", "none", "launcher.jvm_args", TEXT, "-Dnet.kyori.ansi.colorLevel=indexed256"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (serverTypePopupActive) {
            serverTypePopupX = (this.width - serverTypePopupWidth) / 2;
            serverTypePopupY = (this.height - serverTypePopupHeight) / 2;
        }
        renderDesktopIcons(context, mouseX, mouseY);
        renderTaskbar(context, mouseX, mouseY);
        if (serverTypePopupActive) {
            context.fill(serverTypePopupX, serverTypePopupY, serverTypePopupX + serverTypePopupWidth, serverTypePopupY + serverTypePopupHeight, Config.backgroundColor);
            drawInnerBorder(context, serverTypePopupX, serverTypePopupY, serverTypePopupWidth, serverTypePopupHeight, Config.elementBorderColor);
            drawOuterBorder(context, serverTypePopupX, serverTypePopupY, serverTypePopupWidth, serverTypePopupHeight, globalOuterBorder);
            String stTitle = "Select Action";
            int stTitleW = minecraftClient.textRenderer.getWidth(stTitle);
            int stTitleX = serverTypePopupX + (serverTypePopupWidth - stTitleW) / 2;
            int stTitleY = serverTypePopupY + 5;
            context.drawText(minecraftClient.textRenderer, Text.literal(stTitle), stTitleX, stTitleY, globalTextColor, Config.shadow);
            int option1Y = stTitleY + 20;
            int option2Y = option1Y + 30;
            int option3Y = option2Y + 30;
            String option1 = "Server Creation";
            String option2 = "Server Import";
            String option3 = "Modpack Installation";
            drawOptionBox(context, option1, serverTypePopupX, option1Y, mouseX, mouseY);
            drawOptionBox(context, option2, serverTypePopupX, option2Y, mouseX, mouseY);
            drawOptionBox(context, option3, serverTypePopupX, option3Y, mouseX, mouseY);
        }
        if (remoteHostPopupActive) {
            int px = (this.width - remoteHostPopupW) / 2;
            int py = (this.height - remoteHostPopupH) / 2;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 499);
            context.fill(px, py, px + remoteHostPopupW, py + remoteHostPopupH, Config.backgroundColor);
            drawInnerBorder(context, px, py, remoteHostPopupW, remoteHostPopupH, Config.innerBorderColor);
            drawOuterBorder(context, px, py, remoteHostPopupW, remoteHostPopupH, globalOuterBorder);
            int labelY = py + 5;
            context.drawText(minecraftClient.textRenderer, Text.literal("Host Name:"), px + 5, labelY, globalTextColor, false);
            int nameBoxY = labelY + 10;
            context.fill(px + 5, nameBoxY, px + remoteHostPopupW - 5, nameBoxY + 12, remoteHostActiveField == RemoteHostField.NAME ? innerBackgroundSelectedColor : Config.innerBackgroundColor);
            String nh = remoteHostNameBuffer.toString();
            nh = trimTextToWidthWithEllipsis(nh, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(nh), px + 8, nameBoxY + 2, globalTextColor, false);
            int userLabelY = nameBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("User Name:"), px + 5, userLabelY, globalTextColor, false);
            int userBoxY = userLabelY + 10;
            context.fill(px + 5, userBoxY, px + remoteHostPopupW - 5, userBoxY + 12, remoteHostActiveField == RemoteHostField.USER ? innerBackgroundSelectedColor : Config.innerBackgroundColor);
            String ub = remoteHostUserBuffer.toString();
            ub = trimTextToWidthWithEllipsis(ub, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ub), px + 8, userBoxY + 2, globalTextColor, false);
            int ipLabelY = userBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("IP | Domain:"), px + 5, ipLabelY, globalTextColor, false);
            int ipBoxY = ipLabelY + 10;
            context.fill(px + 5, ipBoxY, px + remoteHostPopupW - 5, ipBoxY + 12, remoteHostActiveField == RemoteHostField.IP ? innerBackgroundSelectedColor : Config.innerBackgroundColor);
            String ih = remoteHostIPBuffer.toString();
            ih = trimTextToWidthWithEllipsis(ih, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ih), px + 8, ipBoxY + 2, globalTextColor, false);
            int portLabelY = ipBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("Port:"), px + 5, portLabelY, globalTextColor, false);
            int portBoxY = portLabelY + 10;
            context.fill(px + 5, portBoxY, px + remoteHostPopupW - 5, portBoxY + 12, remoteHostActiveField == RemoteHostField.PORT ? innerBackgroundSelectedColor : Config.innerBackgroundColor);
            String ph = remoteHostPortBuffer.toString();
            ph = trimTextToWidthWithEllipsis(ph, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ph), px + 8, portBoxY + 2, globalTextColor, false);
            int passLabelY = portBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("Password:"), px + 5, passLabelY, globalTextColor, false);
            int passBoxY = passLabelY + 10;
            context.fill(px + 5, passBoxY, px + remoteHostPopupW - 5, passBoxY + 12, remoteHostActiveField == RemoteHostField.PASSWORD ? innerBackgroundSelectedColor : Config.innerBackgroundColor);
            String mask = "";
            for (int i = 0; i < remoteHostPasswordBuffer.length(); i++) mask += "*";
            mask = trimTextToWidthWithEllipsis(mask, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(mask), px + 8, passBoxY + 2, globalTextColor, false);
            int confirmButtonY = passBoxY + 33;
            String createText = isEditingHost ? "Save" : "Test & Add";
            int cw = minecraftClient.textRenderer.getWidth(createText) + 10;
            int confirmX = px + 5;
            boolean hoverConfirm = mouseX >= confirmX && mouseX <= confirmX + cw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawCustomButton(context, confirmX, confirmButtonY, createText, minecraftClient, hoverConfirm, true, true, false, true, 60, 20, globalTextColor, globalHoverTextColor, mouseX, mouseY, "Test The Connection And Add The Host To The List.");
            String cancelText = "Cancel";
            int cancW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
            int cancX = px + remoteHostPopupW - (cancW + 5);
            boolean hoverCancel = mouseX >= cancX && mouseX <= cancX + cancW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawCustomButton(context, cancX, confirmButtonY, cancelText, minecraftClient, hoverCancel, true, true, false, true, 60, 20, globalTextColor, dangerLightAccentColor, mouseX,mouseY, "");
            if (isEditingHost) {
                String deleteText = "Delete";
                int delW = minecraftClient.textRenderer.getWidth(deleteText) + 10;
                int delX = px + (remoteHostPopupW - delW) / 2;
                boolean hoverDelete = mouseX >= delX && mouseX <= delX + delW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
                drawCustomButton(context, delX, confirmButtonY, deleteText, minecraftClient, hoverDelete, true, true, false, true, 60, 20, dangerLightAccentColor, Config.dangerDarkAccentColor, mouseX,mouseY,"Remove This Host.");
            }
            if (remoteHostCreationWarning) {
                String warning = isEditingHost ? "Failed to save changes" : "Invalid or Connection Failed";
                int ww = minecraftClient.textRenderer.getWidth(warning);
                context.drawText(minecraftClient.textRenderer, Text.literal(warning), px + (remoteHostPopupW - ww) / 2, passBoxY + 20, 0xFFFF4444, Config.shadow);
            }
        }
        if (deletionPopupActive) {
            renderDeletePopup(context, mouseX, mouseY);
        }
        Render.ContextMenu.renderMenu(context, minecraftClient, mouseX, mouseY);
        animatedScaling(context, this, minecraftClient);
    }

    private void renderDesktopIcons(DrawContext context, int mouseX, int mouseY) {
        serverIconRects.clear();
        List<ServerInfo> currentServers = getCurrentServers();
        int iconSize = 32;
        int margin = 20;
        int spacing = 20;
        int totalIcons = currentServers.size() + 1;
        int availableHeight = this.height - taskbarHeight - 2 * margin;
        int rows = availableHeight / (iconSize + spacing);
        if (rows < 1) rows = 1;
        if (iconPosX.size() < totalIcons) {
            iconPosX.clear();
            iconPosY.clear();
            for (int i = 0; i < totalIcons; i++) {
                int col = i / rows;
                int row = i % rows;
                iconPosX.add((float) (margin + col * (iconSize + spacing)));
                iconPosY.add((float) (margin + row * (iconSize + spacing)));
            }
        }
        int targetIndex = -1;
        if (isDragging && draggingServerIndex != -1) {
            int candidateCol = (mouseX - margin) / (iconSize + spacing);
            int candidateRow = (mouseY - margin) / (iconSize + spacing);
            targetIndex = candidateCol * rows + candidateRow;
            if (targetIndex < 0) targetIndex = 0;
            if (targetIndex >= currentServers.size()) targetIndex = currentServers.size() - 1;
        }
        for (int i = 0; i < totalIcons; i++) {
            int col = i / rows;
            int row = i % rows;
            float baseX = margin + col * (iconSize + spacing);
            float baseY = margin + row * (iconSize + spacing);
            if (isDragging && i == draggingServerIndex) {
                baseX = mouseX - dragOffsetX;
                baseY = mouseY - dragOffsetY;
            } else if (isDragging && draggingServerIndex != -1) {
                if (draggingServerIndex < targetIndex && i > draggingServerIndex && i <= targetIndex) {
                    int adjustedIndex = i - 1;
                    int ac = adjustedIndex / rows;
                    int ar = adjustedIndex % rows;
                    baseX = margin + ac * (iconSize + spacing);
                    baseY = margin + ar * (iconSize + spacing);
                } else if (draggingServerIndex > targetIndex && i >= targetIndex && i < draggingServerIndex) {
                    int adjustedIndex = i + 1;
                    int ac = adjustedIndex / rows;
                    int ar = adjustedIndex % rows;
                    baseX = margin + ac * (iconSize + spacing);
                    baseY = margin + ar * (iconSize + spacing);
                }
            }
            float currentX = iconPosX.get(i);
            float currentY = iconPosY.get(i);
            currentX = lerp(currentX, baseX, globalMovementSpeed * deltaTime);
            currentY = lerp(currentY, baseY, globalMovementSpeed * deltaTime);
            iconPosX.set(i, currentX);
            iconPosY.set(i, currentY);
            boolean hovered = (mouseX >= currentX && mouseX <= currentX + iconSize && mouseY >= currentY && mouseY <= currentY + iconSize) || (isDragging && i == draggingServerIndex);
            int elevId = ("desktopIcon" + i).hashCode();
            float targetOffset = hovered ? -3f : 0f;
            float currentOffset = elevationOffsets.getOrDefault(elevId, 0f);
            currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
            elevationOffsets.put(elevId, currentOffset);
            context.getMatrices().push();
            context.getMatrices().translate(0, currentOffset, i == draggingServerIndex ? 499 : 0);
            serverIconRects.add(new IconRect((int) currentX, (int) currentY, iconSize, iconSize, (i < currentServers.size() ? i : -1), i == currentServers.size()));
            if (i < currentServers.size()) {
                ServerInfo server = currentServers.get(i);
                BufferedImage icon = getServerIcon(server);
                drawPixelArt(context, (int) currentX, (int) currentY, iconSize, iconSize, icon);
                if (selectedDesktopIndex == i) {
                    drawInnerBorder(context, (int) currentX - 1, (int) currentY - 1, iconSize + 2, iconSize + 2, accentColor);
                    drawOuterBorder(context, (int) currentX - 1, (int) currentY - 1, iconSize + 2, iconSize + 2, globalOuterBorder);
                } else {
                    drawOuterBorder(context, (int) currentX, (int) currentY, iconSize, iconSize, globalOuterBorder);
                }
                if (hovered) {
                    context.fill((int) currentX, (int) currentY, (int) currentX + iconSize, (int) currentY + iconSize, 0x40FFFFFF);
                }
                String name = server.name;
                String trimmed = trimTextToWidthWithEllipsis(name, 80);
                int textWidth = minecraftClient.textRenderer.getWidth(trimmed);
                int textX = (int) currentX + (iconSize - textWidth) / 2;
                context.drawText(minecraftClient.textRenderer, Text.literal(trimmed), textX, (int) currentY + iconSize + 2, globalTextColor, Config.shadow);
            } else {
                drawPixelArt(context, (int) currentX, (int) currentY, iconSize, iconSize, serverIcon);
                drawOuterBorder(context, (int) currentX, (int) currentY, iconSize, iconSize, globalOuterBorder);
                if (hovered) {
                    context.fill((int) currentX, (int) currentY, (int) currentX + iconSize, (int) currentY + iconSize, 0x40FFFFFF);
                }
                String newLabel = "New Server";
                String trimmed = trimTextToWidthWithEllipsis(newLabel, 80);
                int labelWidth = minecraftClient.textRenderer.getWidth(trimmed);
                int labelX = (int) currentX + (iconSize - labelWidth) / 2;
                context.drawText(minecraftClient.textRenderer, Text.literal(trimmed), labelX, (int) currentY + iconSize + 2, globalTextColor, Config.shadow);
            }
            context.getMatrices().pop();
        }
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void renderTaskbar(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, this.height - taskbarHeight, this.width, this.height, Config.innerBackgroundColor);
        drawInnerBorder(context, 0, this.height - taskbarHeight, this.width, taskbarHeight, Config.innerBorderColor);
        drawOuterBorder(context, 0, this.height - taskbarHeight, this.width, taskbarHeight, globalOuterBorder);
        int iconSize = 20;
        int padding = 5;
        int yTask = this.height - taskbarHeight + ((taskbarHeight - iconSize) / 2);
        int xTask = padding;
        boolean terminalHovered = mouseX >= xTask && mouseX <= xTask + iconSize && mouseY >= yTask && mouseY <= yTask + iconSize;
        drawSquareButton(context, xTask, yTask, minecraftClient, terminalHovered, mouseX, mouseY, terminalIcon.getTooltip(), terminalIcon.getImage());
        xTask += iconSize + padding;
        boolean explorerHovered = mouseX >= xTask && mouseX <= xTask + iconSize && mouseY >= yTask && mouseY <= yTask + iconSize;
        drawSquareButton(context, xTask, yTask, minecraftClient, explorerHovered, mouseX, mouseY, explorerIcon.getTooltip(), explorerIcon.getImage());
        xTask += iconSize + padding;
        boolean browserHovered = mouseX >= xTask && mouseX <= xTask + iconSize && mouseY >= yTask && mouseY <= yTask + iconSize;
        drawSquareButton(context, xTask, yTask, minecraftClient, browserHovered, mouseX, mouseY, browserIcon.getTooltip(), browserIcon.getImage());
        xTask += iconSize + padding;
        boolean settingsHovered = mouseX >= xTask && mouseX <= xTask + iconSize && mouseY >= yTask && mouseY <= yTask + iconSize;
        drawSquareButton(context, xTask, yTask, minecraftClient, settingsHovered, mouseX, mouseY, settingsIcon.getTooltip(), settingsIcon.getImage());
        renderHostTabs(context, mouseX, mouseY);
    }

    private void renderHostTabs(DrawContext context, int mouseX, int mouseY) {
        List<String> tabs = getAllTabNames();
        int height = 18;
        int padding = 5;
        int gap = 4;
        int minWidth = 50;
        int[] widths = new int[tabs.size()];
        int totalWidth = 0;
        for (int i = 0; i < tabs.size(); i++) {
            int w = minecraftClient.textRenderer.getWidth(tabs.get(i)) + 2 * padding;
            if (w < minWidth) w = minWidth;
            widths[i] = w;
            totalWidth += w;
            if (i > 0) totalWidth += gap;
        }
        int y = this.height - taskbarHeight + 4;
        int startX = this.width - totalWidth - 5;
        int plusX = startX - gap - height;
        int plusTextWidth = minecraftClient.textRenderer.getWidth("+");
        boolean isPlusHovered = mouseX >= plusX && mouseX <= plusX + height && mouseY >= y && mouseY <= y + height;
        float targetOffset = isPlusHovered ? -3f : 0f;
        int id = ("ServerManagerPlusIcon").hashCode();
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        context.fill(plusX, y, plusX + height, y + height, getElementBackgroundColor(id, isPlusHovered, false, true,false, true, false));
        drawInnerBorder(context, plusX, y, height, height, getElementBorderColor(id, isPlusHovered, false, true, false, false, false));
        drawOuterBorder(context, plusX, y, height, height, globalOuterBorder);
        context.drawText(minecraftClient.textRenderer, Text.literal("+"), plusX + (height - plusTextWidth) / 2, y + ((height - minecraftClient.textRenderer.fontHeight) / 2) + 1, globalTextColor, Config.shadow);
        context.getMatrices().pop();
        int currentX = startX;
        for (int i = 0; i < tabs.size(); i++) {
            int w = widths[i];
            boolean isActive = (i == activeTabIndex);
            boolean isHovered = mouseX >= currentX && mouseX <= currentX + w && mouseY >= y && mouseY <= y + height;
            int bg = getElementBackgroundColor(500 + i, isHovered, isActive, true, false, false, false);
            float tabsTargetOffset = isHovered ? -3f : 0f;
            int tabsId = (tabs.get(i)).hashCode();
            float tabsCurrentOffset = elevationOffsets.getOrDefault(tabsId, 0f);
            tabsCurrentOffset += (tabsTargetOffset - tabsCurrentOffset) * globalMovementSpeed * deltaTime;
            elevationOffsets.put(tabsId, tabsCurrentOffset);
            context.getMatrices().push();
            context.getMatrices().translate(0, tabsCurrentOffset, 0);
            context.fill(currentX, y, currentX + w, y + height, bg);
            drawInnerBorder(context, currentX, y, w, height, getElementBorderColor(500 + i, isHovered, isActive, true, false, false, false));
            drawOuterBorder(context, currentX, y, w, height, globalOuterBorder);
            String tabText = tabs.get(i);
            int textWidth = minecraftClient.textRenderer.getWidth(tabText);
            int textX = currentX + (w - textWidth) / 2;
            int textY = 1 + y + (height - minecraftClient.textRenderer.fontHeight) / 2;
            context.drawText(minecraftClient.textRenderer, Text.literal(tabText), textX, textY, globalTextColor, Config.shadow);
            context.getMatrices().pop();
            currentX += w + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (remoteHostPopupActive) {
            int rhpx = (this.width - remoteHostPopupW) / 2;
            int rhpy = (this.height - remoteHostPopupH) / 2;
            if (mouseX < rhpx || mouseX > rhpx + remoteHostPopupW || mouseY < rhpy || mouseY > rhpy + remoteHostPopupH) {
                closeRemoteHostPopup();
                return true;
            }
        }
        if (deletionPopupActive) {
            int popupW = serverPopupWidth;
            int popupH = serverPopupHeight;
            int popupX = (this.width - popupW) / 2;
            int popupY = (this.height - popupH) / 2;
            if (mouseX < popupX || mouseX > popupX + popupW || mouseY < popupY || mouseY > popupY + popupH) {
                deletionPopupActive = false;
                return true;
            }
            int btnWidth = (popupW - 40) / 3;
            int btnY = popupY + popupH - 40;
            int deleteX = popupX + 10;
            int removeX = deleteX + btnWidth + 10;
            int cancelX = removeX + btnWidth + 10;
            if (mouseX >= deleteX && mouseX <= deleteX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20 && button == 0) {
                playClick();
                deleteServerTrash(deletionPopupServerIndex);
                deletionPopupActive = false;
                return true;
            }
            if (mouseX >= removeX && mouseX <= removeX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20 && button == 0) {
                playClick();
                deleteServerRemove(deletionPopupServerIndex);
                deletionPopupActive = false;
                return true;
            }
            if (mouseX >= cancelX && mouseX <= cancelX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20 && button == 0) {
                playClick();
                deletionPopupActive = false;
                return true;
            }
            return true;
        }
        if (serverTypePopupActive) {
            if (handleServerTypePopupClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (Render.ContextMenu.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (remoteHostPopupActive) {
            return handleRemoteHostPopupClick(mouseX, mouseY, button);
        }
        for (IconRect rect : serverIconRects) {
            if (mouseX >= rect.x && mouseX <= rect.x + rect.width && mouseY >= rect.y && mouseY <= rect.y + rect.height) {
                if (button == 0) {
                    if (rect.isCreate) {
                        playClick();
                        serverTypePopupActive = true;
                        return true;
                    } else {
                        long currentTime = System.currentTimeMillis();
                        if (lastClickedIndex == rect.serverIndex && (currentTime - lastClickTime) < 500) {
                            playClick();
                            openServerScreen(rect.serverIndex, getCurrentServers());
                            lastClickedIndex = -1;
                        } else {
                            selectedDesktopIndex = rect.serverIndex;
                            lastClickedIndex = rect.serverIndex;
                            lastClickTime = currentTime;
                            canDrag = true;
                            draggingServerIndex = rect.serverIndex;
                            draggingStartX = (int) mouseX;
                            draggingStartY = (int) mouseY;
                        }
                        return true;
                    }
                } else if (button == 1 && !rect.isCreate) {
                    playClick();
                    Render.ContextMenu.hide();
                    Render.ContextMenu.addItem("Edit", () -> {
                        ServerInfo info = getCurrentServers().get(rect.serverIndex);
                        minecraftClient.setScreen(new SettingsScreen(minecraftClient, "editServer", this, info.path, settings, info));
                    }, globalHoverTextColor, "Open The Server's Settings");
                    Render.ContextMenu.addItem("Open Folder", () -> minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, getCurrentServers().get(rect.serverIndex), false)), globalHoverTextColor, "Open The Server's Folder");
                    Render.ContextMenu.addItem("Delete", () -> {
                        deletionPopupActive = true;
                        deletionPopupServerIndex = rect.serverIndex;
                    }, globalHoverTextColor, "Show Deletion Options");
                    Render.ContextMenu.show((int) mouseX, (int) mouseY, 80, this.width, this.height);
                    return true;
                }
            }
        }
        int taskbarY = this.height - taskbarHeight;
        int iconSize = 20;
        int padding = 5;
        int yTask = taskbarY + (taskbarHeight - iconSize) / 2;
        int xTask = padding;
        if (mouseY >= yTask && mouseY <= yTask + iconSize) {
            if (mouseX >= xTask && mouseX <= xTask + iconSize) {
                playClick();
                minecraftClient.setScreen(new MultiTerminalScreen(minecraftClient, this, remotelyClient));
                return true;
            }
            xTask += iconSize + padding;
            if (mouseX >= xTask && mouseX <= xTask + iconSize) {
                playClick();
                try {
                    minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, new ServerInfo(remotelyDir.toString()), false));
                } catch (Exception ignored) {}
                return true;
            }
            xTask += iconSize + padding;
            if (mouseX >= xTask && mouseX <= xTask + iconSize) {
                playClick();
                if (minecraftClient.getSession().getUuidOrNull().equals(UUID.fromString("9cc444cd-47cf-4660-96b7-17a7bfef302c")) && checkIfMcefExist()) {
                    minecraftClient.setScreen(new BrowserScreen(minecraftClient, this, "nexomc.com"));
                } else if (checkIfMcefExist()) {
                    minecraftClient.setScreen(new BrowserScreen(minecraftClient, this, "www.google.com"));
                }
                return true;
            }
            xTask += iconSize + padding;
            if (mouseX >= xTask && mouseX <= xTask + iconSize) {
                playClick();
                minecraftClient.setScreen(new SettingsScreen(minecraftClient, "config", this, remotelyDir.toString(), settings));
                return true;
            }
        }
        int tabWidth = 50;
        int gap = 4;
        List<String> tabs = getAllTabNames();
        int totalWidth = 0;
        int[] widths = new int[tabs.size()];
        int paddingTabs = 4;
        for (int i = 0; i < tabs.size(); i++) {
            int w = minecraftClient.textRenderer.getWidth(tabs.get(i)) + 2 * paddingTabs;
            if (w < tabWidth) w = tabWidth;
            widths[i] = w;
            totalWidth += w;
            if (i > 0) totalWidth += gap;
        }
        int startX = this.width - totalWidth - 5;
        int plusWidth = 30;
        int plusX = startX - gap - plusWidth;
        boolean isPlusHovered = mouseX >= plusX && mouseX <= plusX + plusWidth && mouseY >= this.height - taskbarHeight + 2 && mouseY <= this.height - taskbarHeight + 2 + (taskbarHeight - 4);
        if (isPlusHovered && button == 0) {
            playClick();
            remoteHostPopupActive = true;
            return true;
        }
        int hostTabAreaY = this.height - taskbarHeight + 2;
        if (mouseX >= startX && mouseX <= this.width) {
            int currentX = startX;
            for (int i = 0; i < tabs.size(); i++) {
                if (mouseX >= currentX && mouseX <= currentX + widths[i] && mouseY >= hostTabAreaY && mouseY <= hostTabAreaY + (taskbarHeight - 4)) {
                    int actualIndex = i;
                    if (button == 0) {
                        playClick();
                        activeTabIndex = actualIndex;
                        if (actualIndex > 0) {
                            RemoteHostInfo host = remoteHosts.get(actualIndex - 1);
                            if (!host.isConnected && !host.isConnecting) {
                                connectRemoteHostAsync(host);
                            }
                        }
                    } else if (button == 1 && actualIndex > 0) {
                        playClick();
                        RemoteHostInfo host = remoteHosts.get(actualIndex - 1);
                        remoteHostPopupActive = true;
                        isEditingHost = true;
                        remoteHostCreationWarning = false;
                        remoteHostActiveField = RemoteHostField.NONE;
                        remoteHostNameBuffer.setLength(0);
                        remoteHostNameBuffer.append(host.name);
                        remoteHostUserBuffer.setLength(0);
                        remoteHostUserBuffer.append(host.user);
                        remoteHostIPBuffer.setLength(0);
                        remoteHostIPBuffer.append(host.ip);
                        remoteHostPortBuffer.setLength(0);
                        remoteHostPortBuffer.append(host.port);
                        remoteHostPasswordBuffer.setLength(0);
                        remoteHostPasswordBuffer.append(host.password);
                    }
                    return true;
                }
                currentX += widths[i] + gap;
            }
            if (mouseX >= plusX && mouseX <= plusX + 30 && mouseY >= hostTabAreaY && mouseY <= hostTabAreaY + (taskbarHeight - 4)) {
                playClick();
                remoteHostPopupActive = true;
                isEditingHost = false;
                remoteHostCreationWarning = false;
                remoteHostActiveField = RemoteHostField.NONE;
                remoteHostNameBuffer.setLength(0);
                remoteHostUserBuffer.setLength(0);
                remoteHostUserBuffer.append("root");
                remoteHostIPBuffer.setLength(0);
                remoteHostPortBuffer.setLength(0);
                remoteHostPasswordBuffer.setLength(0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && canDrag && draggingServerIndex != -1) {
            if (!isDragging) {
                isDragging = true;
                dragOffsetX = draggingStartX - (serverIconRects.get(draggingServerIndex).x);
                dragOffsetY = draggingStartY - (serverIconRects.get(draggingServerIndex).y);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging && draggingServerIndex != -1) {
            List<ServerInfo> currentServers = getCurrentServers();
            int iconSize = 32;
            int margin = 20;
            int spacing = 20;
            int availableHeight = this.height - taskbarHeight - 2 * margin;
            int rows = availableHeight / (iconSize + spacing);
            if (rows < 1) rows = 1;
            int candidateCol = (int) ((mouseX - margin) / (iconSize + spacing));
            int candidateRow = (int) ((mouseY - margin) / (iconSize + spacing));
            int newIndex = candidateCol * rows + candidateRow;
            if (newIndex < 0) newIndex = 0;
            if (newIndex >= currentServers.size()) newIndex = currentServers.size() - 1;
            if (newIndex != draggingServerIndex) {
                ServerInfo dragged = currentServers.remove(draggingServerIndex);
                currentServers.add(newIndex, dragged);
                selectedDesktopIndex = newIndex;
                iconPosX.add(newIndex, iconPosX.remove(draggingServerIndex));
                iconPosY.add(newIndex, iconPosY.remove(draggingServerIndex));
            }
            if (activeTabIndex == 0) {
                localServers = currentServers;
                saveServers();
            } else {
                remoteHosts.get(activeTabIndex - 1).servers = currentServers;
                saveRemoteHosts();
            }
            isDragging = false;
            draggingServerIndex = -1;
            dragOffsetX = 0;
            dragOffsetY = 0;
            canDrag = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scaleScroll(verticalAmount);
        int tabHeight = 25;
        int contentYStart = topBarHeight + tabHeight + 5 + verticalPadding;
        int panelHeight = this.height - contentYStart - 5;
        List<ServerInfo> currentServers = getCurrentServers();
        int maxScroll = Math.max(0, currentServers.size() * (entryHeight + 1) - panelHeight);
        targetOffset -= (float) (verticalAmount * entryHeight * 2);
        if (targetOffset < 0) targetOffset = 0;
        if (targetOffset > maxScroll) targetOffset = maxScroll;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_S && modifiers == GLFW.GLFW_MOD_CONTROL) {
            clientSettings.clear();
            minecraftClient.setScreen(new SettingsScreen(minecraftClient, "config", this, "", null));
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clipboard = minecraftClient.keyboard.getClipboard();
            if (remoteHostPopupActive) {
                switch (remoteHostActiveField) {
                    case NAME -> remoteHostNameBuffer.append(clipboard);
                    case USER -> remoteHostUserBuffer.append(clipboard);
                    case IP -> remoteHostIPBuffer.append(clipboard);
                    case PORT -> remoteHostPortBuffer.append(clipboard);
                    case PASSWORD -> remoteHostPasswordBuffer.append(clipboard);
                }
                return true;
            }
        }
        if (remoteHostPopupActive) {
            handleRemoteHostTypingKey(keyCode);
            return true;
        }
        if (nameFieldFocused) {
            if (handleTypingKey(keyCode, serverNameBuffer, true)) return true;
        } else if (versionFieldFocused) {
            if (handleTypingKey(keyCode, serverVersionBuffer, false)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (remoteHostPopupActive) {
            if (chr == 27) {
                closeRemoteHostPopup();
                return true;
            }
            if (Character.isISOControl(chr)) return true;
            if (remoteHostActiveField == RemoteHostField.NAME) {
                if (chr >= 32 && remoteHostNameBuffer.length() < 100) remoteHostNameBuffer.append(chr);
            } else if (remoteHostActiveField == RemoteHostField.USER) {
                if (chr >= 32 && remoteHostUserBuffer.length() < 100) remoteHostUserBuffer.append(chr);
            } else if (remoteHostActiveField == RemoteHostField.IP) {
                if ((Character.isLetterOrDigit(chr) || chr == '.' || chr == '-') && remoteHostIPBuffer.length() < 100) {
                    remoteHostIPBuffer.append(chr);
                }
            } else if (remoteHostActiveField == RemoteHostField.PORT) {
                if (Character.isDigit(chr) && remoteHostPortBuffer.length() < 6) {
                    remoteHostPortBuffer.append(chr);
                }
            } else if (remoteHostActiveField == RemoteHostField.PASSWORD) {
                if (chr >= 32 && chr < 127 && remoteHostPasswordBuffer.length() < 100) {
                    remoteHostPasswordBuffer.append(chr);
                }
            }
            return true;
        }
        if (nameFieldFocused) {
            insertChar(serverNameBuffer, chr, true);
            return true;
        } else if (versionFieldFocused) {
            insertChar(serverVersionBuffer, chr, false);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void connectRemoteHostAsync(RemoteHostInfo hostInfo) {
        hostInfo.getSSHManager();
        hostInfo.isConnecting = true;
        new Thread(() -> {
            try {
                hostInfo.getSSHManager().connectToRemoteHost(hostInfo.getUser(), hostInfo.getIp(), hostInfo.getPort(), hostInfo.getPassword());
                hostInfo.getSSHManager().connectSFTP();
                hostInfo.isConnected = true;
                hostInfo.isConnecting = false;
                hostInfo.connectionError = null;
                for (ServerInfo s : hostInfo.servers) {
                    if (s.isRemote && s.remoteSSHManager == null) {
                        s.remoteSSHManager = new SSHManager(s.remoteHost);
                        s.remoteSSHManager.connectToRemoteHost(s.remoteHost.getUser(), s.remoteHost.getIp(), s.remoteHost.getPort(), s.remoteHost.getPassword());
                        s.remoteSSHManager.connectSFTP();
                    }
                }
            } catch (Exception ex) {
                hostInfo.isConnected = false;
                hostInfo.isConnecting = false;
                hostInfo.connectionError = "Failed to connect: " + ex.getMessage();
            }
        }).start();
    }

    private List<String> getAllTabNames() {
        List<String> all = new ArrayList<>();
        all.add("Local");
        for (RemoteHostInfo rh : remoteHosts) {
            all.add(rh.name);
        }
        return all;
    }

    List<ServerInfo> getCurrentServers() {
        return activeTabIndex == 0 ? localServers : remoteHosts.get(activeTabIndex - 1).servers;
    }

    private boolean handleRemoteHostPopupClick(double mouseX, double mouseY, int button) {
        int px = (this.width - remoteHostPopupW) / 2;
        int py = (this.height - remoteHostPopupH) / 2;
        int nameBoxY = py + 15;
        int userLabelY = nameBoxY + 25;
        int userBoxY = userLabelY + 10;
        int ipLabelY = userBoxY + 25;
        int ipBoxY = ipLabelY + 10;
        int portLabelY = ipBoxY + 25;
        int portBoxY = portLabelY + 10;
        int passLabelY = portBoxY + 25;
        int passBoxY = passLabelY + 10;
        int confirmButtonY = passBoxY + 35;
        String createText = isEditingHost ? "Save" : "Test & Add";
        int cw = minecraftClient.textRenderer.getWidth(createText) + 10;
        int confirmX = px + 5;
        boolean hoverConfirm = mouseX >= confirmX && mouseX <= confirmX + cw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        if (hoverConfirm && button == 0) {
            if (remoteHostNameBuffer.toString().trim().isEmpty() || remoteHostIPBuffer.toString().trim().isEmpty() || remoteHostPortBuffer.toString().trim().isEmpty()) {
                remoteHostCreationWarning = true;
                return true;
            }
            if (!isEditingHost && !testSSHConnection(remoteHostUserBuffer.toString().trim(), remoteHostIPBuffer.toString().trim(), remoteHostPortBuffer.toString().trim(), remoteHostPasswordBuffer.toString())) {
                remoteHostCreationWarning = true;
                return true;
            }
            if (isEditingHost) {
                RemoteHostInfo rh = remoteHosts.get(activeTabIndex - 1);
                rh.name = remoteHostNameBuffer.toString().trim();
                rh.user = remoteHostUserBuffer.toString().trim();
                rh.ip = remoteHostIPBuffer.toString().trim();
                try {
                    rh.port = Integer.parseInt(remoteHostPortBuffer.toString().trim());
                } catch (NumberFormatException e) {
                    rh.port = 22;
                }
                rh.password = remoteHostPasswordBuffer.toString();
                saveRemoteHosts();
                remoteHostPopupActive = false;
                return true;
            } else {
                RemoteHostInfo rh = new RemoteHostInfo();
                rh.name = remoteHostNameBuffer.toString().trim();
                rh.user = remoteHostUserBuffer.toString().trim();
                rh.ip = remoteHostIPBuffer.toString().trim();
                try {
                    rh.port = Integer.parseInt(remoteHostPortBuffer.toString().trim());
                } catch (NumberFormatException e) {
                    rh.port = 22;
                }
                rh.password = remoteHostPasswordBuffer.toString();
                rh.servers = new ArrayList<>();
                remoteHosts.add(rh);
                saveRemoteHosts();
                activeTabIndex = remoteHosts.size();
                closeRemoteHostPopup();
                return true;
            }
        }
        String cancelText = "Cancel";
        int cancW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancX = px + remoteHostPopupW - (cancW + 5);
        boolean hoverCancel = mouseX >= cancX && mouseX <= cancX + cancW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        if (hoverCancel && button == 0) {
            closeRemoteHostPopup();
            return true;
        }
        if (isEditingHost) {
            String deleteText = "Delete";
            int delW = minecraftClient.textRenderer.getWidth(deleteText) + 10;
            int delX = px + (remoteHostPopupW - delW) / 2;
            boolean hoverDelete = mouseX >= delX && mouseX <= delX + delW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            if (hoverDelete && button == 0) {
                if (activeTabIndex > 0 && activeTabIndex <= remoteHosts.size()) {
                    remoteHosts.remove(activeTabIndex - 1);
                    saveRemoteHosts();
                    activeTabIndex = 0;
                    closeRemoteHostPopup();
                    return true;
                }
            }
        }
        int nBoxH = 12;
        if (mouseX >= px + 5 && mouseX <= px + remoteHostPopupW - 5 && mouseY >= nameBoxY && mouseY <= nameBoxY + nBoxH && button == 0) {
            remoteHostActiveField = RemoteHostField.NAME;
            return true;
        }
        int uBoxH = 12;
        if (mouseX >= px + 5 && mouseX <= px + remoteHostPopupW - 5 && mouseY >= userBoxY && mouseY <= userBoxY + uBoxH && button == 0) {
            remoteHostActiveField = RemoteHostField.USER;
            return true;
        }
        int iBoxH = 12;
        if (mouseX >= px + 5 && mouseX <= px + remoteHostPopupW - 5 && mouseY >= ipBoxY && mouseY <= ipBoxY + iBoxH && button == 0) {
            remoteHostActiveField = RemoteHostField.IP;
            return true;
        }
        int pBoxH = 12;
        if (mouseX >= px + 5 && mouseX <= px + remoteHostPopupW - 5 && mouseY >= portBoxY && mouseY <= portBoxY + pBoxH && button == 0) {
            remoteHostActiveField = RemoteHostField.PORT;
            return true;
        }
        int pwdBoxH = 12;
        if (mouseX >= px + 5 && mouseX <= px + remoteHostPopupW - 5 && mouseY >= passBoxY && mouseY <= passBoxY + pwdBoxH && button == 0) {
            remoteHostActiveField = RemoteHostField.PASSWORD;
            return true;
        }
        return false;
    }

    private boolean handleServerTypePopupClick(double mouseX, double mouseY, int button) {
        String option1 = "Server Creation";
        String option2 = "Server Import";
        String option3 = "Modpack Installation";
        int option1Y = serverTypePopupY + 25;
        int option2Y = option1Y + 30;
        int option3Y = option2Y + 30;
        if (button == 0) {
            if (isInsideOptionBox(mouseX, mouseY, option1, serverTypePopupX, option1Y)) {
                minecraftClient.setScreen(new SettingsScreen(minecraftClient, "createServer", this, Path.of(String.valueOf(remotelyDir), "servers").toString(), settings));
                serverTypePopupActive = false;
                editingServer = false;
                serverNameBuffer.setLength(0);
                serverVersionBuffer.setLength(0);
                serverNameBuffer.append("MyServer");
                nameFieldFocused = true;
                versionFieldFocused = false;
                serverNameCursorPos = serverNameBuffer.length();
                serverVersionCursorPos = 0;
                return true;
            }
            if (isInsideOptionBox(mouseX, mouseY, option2, serverTypePopupX, option2Y)) {
                serverTypePopupActive = false;
                openImportFileExplorer();
                return true;
            }
            if (isInsideOptionBox(mouseX, mouseY, option3, serverTypePopupX, option3Y)) {
                serverTypePopupActive = false;
                openModpackInstallation();
                return true;
            }
        }
        return false;
    }

    private boolean handleTypingKey(int keyCode, StringBuilder buffer, boolean isNameField) {
        if (keyCode == 259 || keyCode == 261) {
            if (isNameField) {
                if (keyCode == 259 && serverNameCursorPos > 0) {
                    buffer.deleteCharAt(serverNameCursorPos - 1);
                    serverNameCursorPos--;
                } else if (keyCode == 261 && serverNameCursorPos < buffer.length()) {
                    buffer.deleteCharAt(serverNameCursorPos);
                }
            } else {
                if (keyCode == 259 && serverVersionCursorPos > 0) {
                    buffer.deleteCharAt(serverVersionCursorPos - 1);
                    serverVersionCursorPos--;
                } else if (keyCode == 261 && serverVersionCursorPos < buffer.length()) {
                    buffer.deleteCharAt(serverVersionCursorPos);
                }
            }
            return true;
        } else if (keyCode == 263) {
            if (isNameField) {
                if (serverNameCursorPos > 0) serverNameCursorPos--;
            } else {
                if (serverVersionCursorPos > 0) serverVersionCursorPos--;
            }
            return true;
        } else if (keyCode == 262) {
            if (isNameField) {
                if (serverNameCursorPos < buffer.length()) serverNameCursorPos++;
            } else {
                if (serverVersionCursorPos < buffer.length()) serverVersionCursorPos++;
            }
            return true;
        }
        return false;
    }

    private void insertChar(StringBuilder buffer, char chr, boolean isNameField) {
        if (chr == 13 || chr == 27) return;
        if (isNameField) {
            buffer.insert(serverNameCursorPos, chr);
            serverNameCursorPos++;
        } else {
            buffer.insert(serverVersionCursorPos, chr);
            serverVersionCursorPos++;
        }
    }

    private void openServerScreen(int index, List<ServerInfo> currentServers) {
        ServerInfo info = currentServers.get(index);
        if (info.terminal == null) {
            info.terminal = new ServerTerminalInstance(minecraftClient, null, java.util.UUID.randomUUID(), info);
            info.isRunning = false;
        }
        minecraftClient.setScreen(new MultiTerminalScreen(minecraftClient, this, remotelyClient, info));
    }

    private void closeRemoteHostPopup() {
        remoteHostPopupActive = false;
        remoteHostCreationWarning = false;
        remoteHostActiveField = RemoteHostField.NONE;
        remoteHostNameBuffer.setLength(0);
        remoteHostUserBuffer.setLength(0);
        remoteHostUserBuffer.append("root");
        remoteHostIPBuffer.setLength(0);
        remoteHostPortBuffer.setLength(0);
        remoteHostPasswordBuffer.setLength(0);
    }

    private void openImportFileExplorer() {
        if (activeTabIndex == 0) {
            try {
                minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, new ServerInfo(remotelyDir.toString()), true));
            } catch (Exception ignored) {}
        } else {
            try {
                RemoteHostInfo remoteHost = remoteHosts.get(activeTabIndex - 1);
                String user = remoteHost.getUser();
                String homeDir = user.equals("root") ? "/root" : "/home/" + user;
                String path = homeDir + "/remotely/servers/";
                ServerInfo rinfo = new ServerInfo(path);
                rinfo.isRemote = true;
                rinfo.remoteHost = remoteHost;
                minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, rinfo, true));
            } catch (Exception ignored) {}
        }
    }

    private void openModpackInstallation() {
        try {
            ServerInfo serverInfo = new ServerInfo("modpack");
            serverInfo.name = "Modpack Server";
            serverInfo.type = "modpack";
            serverInfo.version = "latest";
            if (activeTabIndex > 0 && activeTabIndex - 1 < remoteHosts.size()) {
                RemoteHostInfo remoteHost = remoteHosts.get(activeTabIndex - 1);
                serverInfo.isRemote = true;
                serverInfo.remoteHost = remoteHost;
                serverInfo.path = remoteHost.getHomeDirectory() + "/remotely/servers/" + serverInfo.name;
                remoteHost.getSSHManager();
                serverInfo.remoteSSHManager = remoteHost.getSSHManager();
                if (!remoteHost.getSSHManager().isSSH()) {
                    long startTime = System.currentTimeMillis();
                    long timeout = 10000;
                    while (!remoteHost.getSSHManager().isSSH() && System.currentTimeMillis() - startTime < timeout) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } else {
                serverInfo.isRemote = false;
                serverInfo.remoteHost = null;
                serverInfo.path = remotelyDir + "/servers/" + serverInfo.name;
            }
            PluginModManagerScreen modManagerScreen = new PluginModManagerScreen(minecraftClient, this, serverInfo);
            minecraftClient.setScreen(modManagerScreen);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void importServerJar(Path jarPath, String folderName) {
        Path parentDir = jarPath.getParent();
        if (parentDir != null) {
            ServerInfo newInfo = new ServerInfo(parentDir.toString());
            newInfo.name = folderName;
            newInfo.path = parentDir.toString();
            newInfo.type = "imported";
            newInfo.version = "unknown";
            newInfo.isRunning = false;
            if (activeTabIndex == 0) {
                newInfo.isRemote = false;
                newInfo.remoteHost = null;
                localServers.add(newInfo);
                saveServers();
            } else {
                newInfo.isRemote = true;
                newInfo.remoteHost = remoteHosts.get(activeTabIndex - 1);
                remoteHosts.get(activeTabIndex - 1).servers.add(newInfo);
                saveRemoteHosts();
            }
        }
    }

    @Override
    public void close() {
        remotelyClient.saveTabIndex(activeTabIndex);
        super.close();
    }

    private Boolean testSSHConnection(String user, String ip, String portStr, String password) {
        int port = 22;
        try {
            port = Integer.parseInt(portStr);
        } catch (Exception ignored) {}
        TerminalInstance dummyTerminal = new TerminalInstance(minecraftClient, null, java.util.UUID.randomUUID()) {
            @Override
            public void appendOutput(String output) {
                System.out.print(output);
            }
        };
        SSHManager sshCheck = new SSHManager(dummyTerminal);
        boolean connectionResult = false;
        try {
            sshCheck.startSSHConnection("ssh " + user + "@" + ip + ":" + port);
            boolean initialized = sshCheck.waitForSessionInitialization(5000);
            if (!initialized) {
                return false;
            }
            sshCheck.setSshPassword(password);
            sshCheck.connectSSHWithPassword(password);
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 10000) {
                if (sshCheck.isSSH()) {
                    connectionResult = true;
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception ignored) {}
        return connectionResult;
    }

    private void handleRemoteHostTypingKey(int keyCode) {
        if (keyCode == 259 || keyCode == 261) {
            if (remoteHostActiveField == RemoteHostField.NAME) {
                if (!remoteHostNameBuffer.isEmpty() && keyCode == 259) {
                    remoteHostNameBuffer.deleteCharAt(remoteHostNameBuffer.length() - 1);
                }
            } else if (remoteHostActiveField == RemoteHostField.USER) {
                if (!remoteHostUserBuffer.isEmpty() && keyCode == 259) {
                    remoteHostUserBuffer.deleteCharAt(remoteHostUserBuffer.length() - 1);
                }
            } else if (remoteHostActiveField == RemoteHostField.IP) {
                if (!remoteHostIPBuffer.isEmpty() && keyCode == 259) {
                    remoteHostIPBuffer.deleteCharAt(remoteHostIPBuffer.length() - 1);
                }
            } else if (remoteHostActiveField == RemoteHostField.PORT) {
                if (!remoteHostPortBuffer.isEmpty() && keyCode == 259) {
                    remoteHostPortBuffer.deleteCharAt(remoteHostPortBuffer.length() - 1);
                }
            } else if (remoteHostActiveField == RemoteHostField.PASSWORD) {
                if (!remoteHostPasswordBuffer.isEmpty() && keyCode == 259) {
                    remoteHostPasswordBuffer.deleteCharAt(remoteHostPasswordBuffer.length() - 1);
                }
            }
        }
    }

    void saveRemoteHosts() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("remotehosts.json");
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < remoteHosts.size(); i++) {
                RemoteHostInfo rh = remoteHosts.get(i);
                sb.append("  {\n");
                sb.append("    \"name\": \"").append(rh.name).append("\",\n");
                sb.append("    \"user\": \"").append(rh.user).append("\",\n");
                sb.append("    \"ip\": \"").append(rh.ip).append("\",\n");
                sb.append("    \"port\": ").append(rh.port).append(",\n");
                sb.append("    \"password\": \"").append(rh.password).append("\",\n");
                sb.append("    \"servers\": [\n");
                for (int j = 0; j < rh.servers.size(); j++) {
                    ServerInfo info = rh.servers.get(j);
                    sb.append("      {\n");
                    sb.append("        \"name\": \"").append(info.name).append("\",\n");
                    sb.append("        \"path\": \"").append(info.path).append("\",\n");
                    sb.append("        \"type\": \"").append(info.type).append("\",\n");
                    sb.append("        \"version\": \"").append(info.version).append("\",\n");
                    sb.append("        \"isRunning\": ").append(info.isRunning).append(",\n");
                    sb.append("        \"isRemote\": ").append(info.isRemote).append("\n");
                    sb.append("      }");
                    if (j < rh.servers.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("    ]\n");
                sb.append("  }");
                if (i < remoteHosts.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]\n");
            Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }

    private void loadSavedRemoteHosts() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("remotehosts.json");
            if (!Files.exists(file)) return;
            String json = Files.readString(file);
            List<RemoteHostInfo> loaded = parseRemoteHostsJson(json);
            for (RemoteHostInfo newHost : loaded) {
                boolean exists = remoteHosts.stream().anyMatch(existingHost ->
                        existingHost.name.equals(newHost.name) &&
                                existingHost.ip.equals(newHost.ip) &&
                                existingHost.port == newHost.port &&
                                existingHost.user.equals(newHost.user)
                );
                if (!exists) {
                    remoteHosts.add(newHost);
                }
            }
        } catch (IOException ignored) {}
    }

    private void scanForUnknownServers() {
        Path serversDir = Paths.get(remotelyDir + "/servers/").toAbsolutePath().normalize();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(serversDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path serverJarPath = entry.resolve("server.jar");
                    if (Files.exists(serverJarPath)) {
                        String folderName = entry.getFileName().toString();
                        String fullPath = entry.toAbsolutePath().toString().replace("/", "\\");
                        Path normalizedPath = Paths.get(fullPath).normalize();
                        if (!isServerRegistered(normalizedPath.toString())) {
                            String type = "imported";
                            String version = "unknown";
                            try (ZipFile zip = new ZipFile(serverJarPath.toFile())) {
                                Enumeration<? extends ZipEntry> entriesZip = zip.entries();
                                boolean hasPaper = false;
                                boolean hasFabric = false;
                                boolean hasVanilla = false;
                                boolean hasForge = false;
                                boolean hasNeoforge = false;
                                boolean hasQuilt = false;
                                while (entriesZip.hasMoreElements()) {
                                    ZipEntry ze = entriesZip.nextElement();
                                    String name = ze.getName();
                                    if (name.startsWith("io/papermc/")) {
                                        hasPaper = true;
                                    }
                                    if (name.equals("install.properties")) {
                                        hasFabric = true;
                                    }
                                    if (name.startsWith("net/minecraftforge/")) {
                                        hasForge = true;
                                    }
                                    if (name.startsWith("cpw/mods/")) {
                                        hasNeoforge = true;
                                    }
                                    if (name.startsWith("net/minecraft/")) {
                                        hasVanilla = true;
                                    }
                                    if (name.equals("lang/installer.properties")) {
                                        hasQuilt = true;
                                    }
                                }
                                if (hasPaper) {
                                    type = "Paper";
                                    ZipEntry versionJson = zip.getEntry("version.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("id").getAsString();
                                        }
                                    }
                                } else if (hasFabric) {
                                    type = "Fabric";
                                    ZipEntry installProps = zip.getEntry("install.properties");
                                    if (installProps != null) {
                                        java.util.Properties props = new java.util.Properties();
                                        try (InputStream is = zip.getInputStream(installProps)) {
                                            props.load(is);
                                            version = props.getProperty("game-version", "unknown");
                                        }
                                    }
                                } else if (hasForge) {
                                    type = "Forge";
                                    ZipEntry versionJson = zip.getEntry("version.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("id").getAsString();
                                        }
                                    }
                                } else if (hasNeoforge) {
                                    type = "Neoforge";
                                    ZipEntry versionJson = zip.getEntry("version.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("id").getAsString();
                                        }
                                    }
                                } else if (hasQuilt) {
                                    type = "Quilt";
                                    ZipEntry installerProps = zip.getEntry("lang/installer.properties");
                                    if (installerProps != null) {
                                        java.util.Properties props = new java.util.Properties();
                                        try (InputStream is = zip.getInputStream(installerProps)) {
                                            props.load(is);
                                        }
                                    }
                                } else if (hasVanilla) {
                                    type = "Vanilla";
                                    ZipEntry versionJson = zip.getEntry("version.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("id").getAsString();
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            addServer(folderName, normalizedPath.toString(), type, version);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isServerRegistered(String normalizedPath) {
        for (ServerInfo server : localServers) {
            String serverPath = Paths.get(server.path).toAbsolutePath().normalize().toString().replace("/", "\\");
            if (serverPath.equalsIgnoreCase(normalizedPath)) {
                return true;
            }
        }
        for (RemoteHostInfo host : remoteHosts) {
            for (ServerInfo server : host.servers) {
                String serverPath = Paths.get(server.path).toAbsolutePath().normalize().toString().replace("/", "\\");
                if (serverPath.equalsIgnoreCase(normalizedPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addServer(String name, String path, String type, String version) {
        ServerInfo newServer = new ServerInfo(path);
        newServer.name = name;
        newServer.path = path;
        newServer.type = type;
        newServer.version = version;
        newServer.isRunning = false;
        newServer.isRemote = false;
        localServers.add(newServer);
        saveServers();
    }

    void saveServers() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("servers.json");
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < localServers.size(); i++) {
                ServerInfo info = localServers.get(i);
                sb.append("  {\n");
                sb.append("    \"name\": \"").append(info.name).append("\",\n");
                sb.append("    \"path\": \"").append(info.path).append("\",\n");
                sb.append("    \"type\": \"").append(info.type).append("\",\n");
                sb.append("    \"version\": \"").append(info.version).append("\",\n");
                sb.append("    \"isRunning\": ").append(info.isRunning).append(",\n");
                sb.append("    \"isRemote\": ").append(info.isRemote).append("\n");
                sb.append("  }");
                if (i < localServers.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]\n");
            Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}
    }

    private void loadSavedServers() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("servers.json");
            if (!Files.exists(file)) return;
            String json = Files.readString(file);
            List<ServerInfo> loaded = parseServersJson(json);
            localServers.addAll(loaded);
        } catch (IOException ignored) {}
    }

    private List<ServerInfo> parseServersJson(String json) {
        List<ServerInfo> list = new ArrayList<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return list;
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        String[] entries = splitJsonObjects(inner);
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            String name = extractJsonValue(entry, "name");
            String path = extractJsonValue(entry, "path");
            String type = extractJsonValue(entry, "type");
            String version = extractJsonValue(entry, "version");
            String runVal = extractJsonValue(entry, "isRunning");
            String remoteVal = extractJsonValue(entry, "isRemote");
            ServerInfo info = new ServerInfo(path);
            info.name = name;
            info.path = path;
            info.type = type;
            info.version = version;
            info.isRunning = runVal.equalsIgnoreCase("true");
            info.isRemote = remoteVal.equalsIgnoreCase("true");
            list.add(info);
        }
        return list;
    }

    private String extractJsonValue(String entry, String key) {
        String k = "\"" + key + "\"";
        int idx = entry.indexOf(k);
        if (idx < 0) return "";
        int colon = entry.indexOf(":", idx + k.length());
        int quote1 = entry.indexOf("\"", colon + 1);
        if (quote1 < 0) {
            String bo = entry.substring(colon + 1).trim();
            bo = bo.replace(",", "").replace("}", "").trim();
            return bo;
        }
        int quote2 = entry.indexOf("\"", quote1 + 1);
        if (quote2 < 0) return "";
        return entry.substring(quote1 + 1, quote2);
    }

    private void renderDeletePopup(DrawContext context, int mouseX, int mouseY) {
        int popupW = serverPopupWidth;
        int popupH = serverPopupHeight;
        int popupX = (this.width - popupW) / 2;
        int popupY = (this.height - popupH) / 2;
        context.fill(popupX, popupY, popupX + popupW, popupY + popupH, Config.backgroundColor);
        drawInnerBorder(context, popupX, popupY, popupW, popupH, Config.elementBorderColor);
        drawOuterBorder(context, popupX, popupY, popupW, popupH, globalOuterBorder);
        String warn = "Are you sure you want to delete this server?";
        int warnW = minecraftClient.textRenderer.getWidth(warn);
        context.drawText(minecraftClient.textRenderer, Text.literal(warn), popupX + (popupW - warnW) / 2, popupY + 20, 0xFFFF5555, Config.shadow);
        int btnWidth = (popupW - 40) / 3;
        int btnY = popupY + popupH - 40;
        int deleteX = popupX + 10;
        int removeX = deleteX + btnWidth + 10;
        int cancelX = removeX + btnWidth + 10;
        drawCustomButton(context, deleteX, btnY, "Delete", minecraftClient, (mouseX >= deleteX && mouseX <= deleteX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20), true, true, false, true, 60, 20, globalTextColor, globalHoverTextColor, mouseX, mouseY, "Delete The Server And The Files.");
        drawCustomButton(context, removeX, btnY, "Remove", minecraftClient, (mouseX >= removeX && mouseX <= removeX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20), true, true, false, true, 60, 20, globalTextColor, globalHoverTextColor, mouseX, mouseY, "Remove The Server From The List \n Without Deleting Files.");
        drawCustomButton(context, cancelX, btnY, "Cancel", minecraftClient, (mouseX >= cancelX && mouseX <= cancelX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20), true, true, false, true, 60, 20, globalTextColor, dangerLightAccentColor, mouseX, mouseY, "");
    }

    private String[] splitJsonObjects(String json) {
        List<String> objs = new ArrayList<>();
        int braceCount = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            sb.append(c);
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            if (braceCount == 0 && c == '}') {
                objs.add(sb.toString());
                sb.setLength(0);
            }
        }
        return objs.toArray(new String[0]);
    }

    private List<RemoteHostInfo> parseRemoteHostsJson(String json) {
        List<RemoteHostInfo> list = new ArrayList<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return list;
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        String[] entries = splitJsonObjects(inner);
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            RemoteHostInfo rh = new RemoteHostInfo();
            rh.name = extractJsonValue(entry, "name");
            rh.user = extractJsonValue(entry, "user");
            rh.ip = extractJsonValue(entry, "ip");
            String portVal = extractJsonValue(entry, "port");
            try {
                rh.port = Integer.parseInt(portVal);
            } catch (NumberFormatException e) {
                rh.port = 22;
            }
            rh.password = extractJsonValue(entry, "password");
            String serversStr = parseServersBlock(entry);
            rh.servers = parseServersJson(serversStr);
            for (ServerInfo s : rh.servers) {
                s.remoteHost = rh;
            }
            list.add(rh);
        }
        return list;
    }

    private String parseServersBlock(String json) {
        int idx = json.indexOf("\"servers\"");
        if (idx < 0) return "[]";
        int bracketStart = json.indexOf("[", idx);
        if (bracketStart < 0) return "[]";
        int bracketCount = 0;
        for (int i = bracketStart; i < json.length(); i++) {
            if (json.charAt(i) == '[') bracketCount++;
            if (json.charAt(i) == ']') bracketCount--;
            if (bracketCount == 0) {
                return json.substring(bracketStart, i + 1);
            }
        }
        return "[]";
    }

    private boolean isInsideOptionBox(double mouseX, double mouseY, String text, int popupX, int boxY) {
        int boxW = 140;
        int boxH = 16 + minecraftClient.textRenderer.fontHeight;
        int boxX = popupX + (serverTypePopupWidth - boxW) / 2;
        return (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH);
    }

    private void drawOptionBox(DrawContext context, String text, int popupX, int boxY, double mouseX, double mouseY) {
        int boxW = 140;
        int boxH = 16 + minecraftClient.textRenderer.fontHeight;
        int boxX = popupX + (serverTypePopupWidth - boxW) / 2;
        boolean hovered = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
        int bg = getElementBackgroundColor(text.hashCode(), hovered, false, true, false, false, false);
        float targetOffset = hovered ? -2f : 0f;
        int id = text.hashCode();
        float currentOffset = elevationOffsets.getOrDefault(id, 0f);
        currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
        elevationOffsets.put(id, currentOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0, currentOffset, 0);
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, bg);
        drawInnerBorder(context, boxX, boxY, boxW, boxH, getElementBorderColor(text.hashCode(), hovered, false, true, false, false, false));
        drawOuterBorder(context, boxX, boxY, boxW, boxH, globalOuterBorder);
        int tw = minecraftClient.textRenderer.getWidth(text);
        int tx = boxX + (boxW - tw) / 2;
        int ty = boxY + (boxH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(text), tx, ty, globalTextColor, false);
        context.getMatrices().pop();
    }


    private BufferedImage getServerIcon(ServerInfo server) {
        try {
            File iconFile = new File(server.path, "icon.png");
            if (iconFile.exists() && iconFile.isFile()) {
                return ImageIO.read(iconFile);
            }
        } catch (IOException e) {
            devPrint("Failed to load server icon: " + e.getMessage());
        }
        return switch (server.type.toLowerCase()) {
            case "vanilla" -> vanilla;
            case "fabric" -> fabric;
            case "forge" -> forge;
            case "paper" -> paper;
            case "neoforge" -> neoforge;
            case "quilt" -> quilt;
            case "modpack" -> vanilla;
            default -> terminal;
        };
    }

    private void deleteServerTrash(int index) {
        List<ServerInfo> currentServers = getCurrentServers();
        if (index >= 0 && index < currentServers.size()) {
            ServerInfo s = currentServers.get(index);
            String dateOfDeletion = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
            File trashDir = new File(System.getProperty("user.dir") + File.separator + "remotely" + File.separator + "trash");
            if (!trashDir.exists()) trashDir.mkdirs();
            if (s.isRemote && s.remoteSSHManager != null) {
                String newRemotePath = s.path.replace("/servers/", "/trash/") + "-" + dateOfDeletion;
                try {
                    s.remoteSSHManager.renameRemoteFolder(s.path, newRemotePath);
                } catch (Exception ignored) {}
            } else {
                File folderPath = new File(s.path);
                if (folderPath.exists()) {
                    String newLocalName = s.name + "-" + dateOfDeletion;
                    File trashSub = new File(trashDir, newLocalName);
                    folderPath.renameTo(trashSub);
                }
            }
            currentServers.remove(index);
            saveServers();
            saveRemoteHosts();
        }
    }

    private void deleteServerRemove(int index) {
        List<ServerInfo> currentServers = getCurrentServers();
        if (index >= 0 && index < currentServers.size()) {
            currentServers.remove(index);
            saveServers();
            saveRemoteHosts();
        }
    }

    private enum RemoteHostField {
        NONE, NAME, USER, IP, PORT, PASSWORD
    }

    @Override
    public void removed() {
        minecraftClient.getWindow().setScaleFactor(originalMCScale);
        targetScaleFactor = globalScaleFactor = animScaleFactor;
    }
}
