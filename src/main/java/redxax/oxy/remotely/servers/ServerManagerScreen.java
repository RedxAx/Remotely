package redxax.oxy.remotely.servers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.config.SettingsScreen;
import redxax.oxy.remotely.explorer.FileExplorerScreen;
import redxax.oxy.remotely.resources.ResourceManagerScreen;
import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import redxax.oxy.remotely.terminal.ServerTerminalInstance;
import redxax.oxy.remotely.terminal.TerminalInstance;
import redxax.oxy.remotely.ui.widgets.AnimatedButton;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.ui.widgets.PopupWidget;
import redxax.oxy.remotely.ui.widgets.TextInputWidget;
import redxax.oxy.remotely.util.ImageUtil.IconWithTooltip;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.servers.BrowserScreen.checkIfMcefExist;

import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Sound;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.config.SettingsScreen.defineSettings;
import static redxax.oxy.remotely.config.SettingsScreen.settings;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.ImageUtil.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class ServerManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    private static List<ServerInfo> localServers;
    private static final List<RemoteHostInfo> remoteHosts = new ArrayList<>();
    private static int activeTabIndex = 0;
    private boolean isEditingHost;
    private int serverIndexForDeletion = -1;
    private PopupWidget addServerPopup;
    private PopupWidget deleteServerPopup;
    private PopupWidget remoteHostPopup;
    private TextInputWidget remoteHostNameInput;
    private TextInputWidget remoteHostUserInput;
    private TextInputWidget remoteHostIpInput;
    private TextInputWidget remoteHostPortInput;
    private TextInputWidget remoteHostPasswordInput;
    private AnimatedButton remoteHostConfirmButton;
    private AnimatedButton remoteHostDeleteButton;
    private final List<BufferedImage> loadingFrames = new ArrayList<>();
    private final int entryHeight = 25;
    private final int topBarHeight = 30;
    private BufferedImage unknown, serverIcon, paper, vanilla, fabric, forge, neoforge, waterfall, velocity, leaf, quilt;
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
    private final List<Float> iconPosX = new ArrayList<>();
    private final List<Float> iconPosY = new ArrayList<>();
    private boolean canDrag = false;
    private final Screen parent;

    public static List<RemoteHostInfo> getRemoteHosts() {
        return remoteHosts;
    }

    public static int getActiveTabIndex() {
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

    public ServerManagerScreen(MinecraftClient minecraftClient, Screen parent, RemotelyClient remotelyClient, List<ServerInfo> servers) {
        super(Text.literal("Server Setup"));
        this.minecraftClient = minecraftClient;
        this.remotelyClient = remotelyClient;
        this.localServers = servers;
        this.parent = parent;
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
        createPopups();
        try {
            terminalIcon = new IconWithTooltip("/assets/remotely/icons/terminal.png", "Terminal");
            explorerIcon = new IconWithTooltip("/assets/remotely/icons/explorer.png", "File Explorer");
            browserIcon = new IconWithTooltip("/assets/remotely/icons/minibrowser.png", "Web Browser");
            settingsIcon = new IconWithTooltip("/assets/remotely/icons/remotely.png", "Settings");
            editorIcon = new IconWithTooltip("/assets/remotely/icons/text.png", "Text Editor");
            serverIcon = loadResourceIcon("/assets/remotely/icons/server.png");

            unknown = loadResourceIcon("/assets/remotely/icons/unknown.png");
            paper = loadResourceIcon("/assets/remotely/icons/paper.png");
            vanilla = loadResourceIcon("/assets/remotely/icons/vanilla.png");
            fabric =loadResourceIcon("/assets/remotely/icons/fabric.png");
            forge = loadResourceIcon("/assets/remotely/icons/forge.png");
            neoforge = loadResourceIcon("/assets/remotely/icons/neoforge.png");
            waterfall = loadResourceIcon("/assets/remotely/icons/waterfall.png");
            velocity = loadResourceIcon("/assets/remotely/icons/velocity.png");
            leaf = loadResourceIcon("/assets/remotely/icons/leaf.png");
            quilt = loadResourceIcon("/assets/remotely/icons/quilt.png");
        } catch (Exception e) {
            new Notification("Failed to load icons: " + e.getMessage(), Notification.Type.ERROR);
        }
    }

    private void createPopups() {
        createAddServerPopup();
        createDeleteServerPopup();
        createRemoteHostPopup();
    }

    private void createAddServerPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Add a Server")
                .size(260, 140)
                .onClose(() -> addServerPopup.hide());

        AnimatedButton createBtn = new AnimatedButton.Builder()
                .label(Text.literal("Server Creation"))
                .onClick(() -> {
                    minecraftClient.setScreen(new SettingsScreen("createServer", this, Path.of(String.valueOf(remotelyDir), "servers").toString(), settings));
                    addServerPopup.hide();
                })
                .build();

        AnimatedButton importBtn = new AnimatedButton.Builder()
                .label(Text.literal("Server Import"))
                .onClick(() -> {
                    addServerPopup.hide();
                    openImportFileExplorer();
                })
                .build();

        AnimatedButton modpackBtn = new AnimatedButton.Builder()
                .label(Text.literal("Modpack Installation"))
                .onClick(() -> {
                    addServerPopup.hide();
                    openModpackInstallation();
                })
                .build();

        builder.addRow("", true, 27, createBtn);
        builder.addRow("", true, 27, importBtn);
        builder.addRow("", true, 27, modpackBtn);

        addServerPopup = builder.build();
        addServerPopup.hide();
    }

    private void createDeleteServerPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Are You Sure?")
                .size(260, 140)
                .onClose(() -> deleteServerPopup.hide());

        AnimatedButton deleteTrashBtn = new AnimatedButton.Builder()
                .label(Text.literal("Delete The Server"))
                .onClick(() -> {
                    playSound(Sound.DELETE);
                    deleteServerTrash(serverIndexForDeletion);
                    deleteServerPopup.hide();
                })
                .build();

        AnimatedButton deleteRemoveBtn = new AnimatedButton.Builder()
                .label(Text.literal("Remove From List"))
                .onClick(() -> {
                    playSound(Sound.DELETE);
                    deleteServerRemove(serverIndexForDeletion);
                    deleteServerPopup.hide();
                })
                .build();

        AnimatedButton cancelBtn = new AnimatedButton.Builder()
                .label(Text.literal("Cancel"))
                .onClick(() -> {
                    playSound(Sound.CLICK);
                    deleteServerPopup.hide();
                })
                .build();

        builder.addRow("", true, 27, deleteTrashBtn);
        builder.addRow("", true, 27, deleteRemoveBtn);
        builder.addRow("", true, 27, cancelBtn);

        deleteServerPopup = builder.build();
        deleteServerPopup.hide();
    }

    private void createRemoteHostPopup() {
        PopupWidget.Builder builder = new PopupWidget.Builder("Remote Host")
                .onClose(this::closeRemoteHostPopup)
                .size(360, 250)
                .setResizable(true)
                .setMinSize(360, 250);

        remoteHostNameInput = new TextInputWidget.Builder().build();
        builder.addRow("Host Name:", true, 20, remoteHostNameInput);

        remoteHostUserInput = new TextInputWidget.Builder().text("root").build();
        builder.addRow("User Name:", true, 20, remoteHostUserInput);

        remoteHostIpInput = new TextInputWidget.Builder().build();
        builder.addRow("IP | Domain:", true, 20, remoteHostIpInput);

        remoteHostPortInput = new TextInputWidget.Builder().text("22").build();
        builder.addRow("Port:", true, 20, remoteHostPortInput);

        remoteHostPasswordInput = new TextInputWidget.Builder().build();
        builder.addRow("Password:", true, 20, remoteHostPasswordInput);

        remoteHostConfirmButton = new AnimatedButton.Builder().label(Text.literal("Test & Add")).onClick(this::onConfirmRemoteHost).build();
        AnimatedButton cancelButton = new AnimatedButton.Builder().label(Text.literal("Cancel")).onClick(this::closeRemoteHostPopup).build();
        remoteHostDeleteButton = new AnimatedButton.Builder().label(Text.literal("Delete")).onClick(this::onDeleteRemoteHost).accentType(Config.AccentType.DANGER).build();
        builder.addRow("", false, 20, remoteHostConfirmButton, cancelButton, remoteHostDeleteButton);

        remoteHostPopup = builder.build();
        remoteHostPopup.hide();
    }

    public void background(DrawContext context) {
        //? if =1.20.1 {
        /*context.fill(0, 0, this.width, this.height, Config.backgroundColor);
         *///?} else {
        if (wallpaper && windowsBackground != null) {
            drawBufferedImage(context, windowsBackground, 0, 0, this.width, this.height);
        } else if (!background) {
            context.fill(0, 0, width, height, backgroundColor);
        }
        //?}
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        background(context);
        renderDesktopIcons(context, mouseX, mouseY);
        renderTaskbar(context, mouseX, mouseY);

        addServerPopup.render(context, mouseX, mouseY, delta);
        deleteServerPopup.render(context, mouseX, mouseY, delta);
        remoteHostPopup.render(context, mouseX, mouseY, delta);

        ContextMenu.renderMenu(context, minecraftClient, mouseX, mouseY);
        animatedScaling(this);
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
                int selectionColor = getElementBorderColor(i, hovered, selectedDesktopIndex == i, true, false, false, false);
                if (selectedDesktopIndex == i) {
                    drawInnerBorder(context, (int) currentX - 1, (int) currentY - 1, iconSize + 2, iconSize + 2, selectionColor);
                    drawOuterBorder(context, (int) currentX - 1, (int) currentY - 1, iconSize + 2, iconSize + 2, selectionColor);
                } else {
                    drawOuterBorder(context, (int) currentX, (int) currentY, iconSize, iconSize, selectionColor);
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
                drawOuterBorder(context, (int) currentX, (int) currentY, iconSize, iconSize, elementBackgroundColor);
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
        drawOuterBorder(context, 0, this.height - taskbarHeight, this.width, taskbarHeight, innerBackgroundColor);
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
        int bgp = getElementBackgroundColor(id, isPlusHovered, false, true,false, true, false);
        context.fill(plusX, y, plusX + height, y + height, bgp);
        drawInnerBorder(context, plusX, y, height, height, getElementBorderColor(id, isPlusHovered, false, true, false, false, false));
        drawOuterBorder(context, plusX, y, height, height, bgp);
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
            drawOuterBorder(context, currentX, y, w, height, bg);
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
        if (remoteHostPopup.mouseClicked(mouseX, mouseY, button)) return true;
        if (addServerPopup.mouseClicked(mouseX, mouseY, button)) return true;
        if (deleteServerPopup.mouseClicked(mouseX, mouseY, button)) return true;
        if (ContextMenu.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (IconRect rect : serverIconRects) {
            if (mouseX >= rect.x && mouseX <= rect.x + rect.width && mouseY >= rect.y && mouseY <= rect.y + rect.height) {
                if (button == 0) {
                    if (rect.isCreate) {
                        playSound(Sound.CREATE);
                        addServerPopup.setX((this.width - addServerPopup.getWidth())/2);
                        addServerPopup.setY((this.height - addServerPopup.getHeight())/2);
                        addServerPopup.show();
                    } else {
                        long currentTime = System.currentTimeMillis();
                        if (lastClickedIndex == rect.serverIndex && (currentTime - lastClickTime) < 500) {
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
                    }
                    return true;
                } else if (button == 1 && !rect.isCreate) {
                    playSound(Sound.RIGHTCLICK);
                    ContextMenu.hide();
                    ContextMenu.addItem("Edit", () -> {
                        ServerInfo info = getCurrentServers().get(rect.serverIndex);
                        minecraftClient.setScreen(new SettingsScreen("editServer", this, info.path, settings, info));
                    }, false, false, false, "Open The Server's Settings");
                    ContextMenu.addItem("Open Folder", () -> minecraftClient.setScreen(new FileExplorerScreen(this, getCurrentServers().get(rect.serverIndex), false)), false, false, false, "Open The Server's Folder");
                    ContextMenu.addItem("Delete", () -> {
                        serverIndexForDeletion = rect.serverIndex;
                        deleteServerPopup.setX((this.width - deleteServerPopup.getWidth())/2);
                        deleteServerPopup.setY((this.height - deleteServerPopup.getHeight())/2);
                        deleteServerPopup.show();
                    }, false, false, false, "Show Deletion Options");
                    ContextMenu.show((int) mouseX, (int) mouseY, 80, this.width, this.height);
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
                minecraftClient.setScreen(new MultiTerminalScreen(minecraftClient, this, remotelyClient));
                return true;
            }
            xTask += iconSize + padding;
            if (mouseX >= xTask && mouseX <= xTask + iconSize) {
                try {
                    minecraftClient.setScreen(new FileExplorerScreen(this, new ServerInfo(remotelyDir.toString()), false));
                } catch (Exception ignored) {}
                return true;
            }
            xTask += iconSize + padding;
            if (mouseX >= xTask && mouseX <= xTask + iconSize) {
                if (minecraftClient.getSession().getUuidOrNull().equals(UUID.fromString("9cc444cd-47cf-4660-96b7-17a7bfef302c")) && checkIfMcefExist()) {
                    minecraftClient.setScreen(new BrowserScreen(minecraftClient, this, "nexomc.com"));
                } else if (checkIfMcefExist()) {
                    minecraftClient.setScreen(new BrowserScreen(minecraftClient, this, "www.google.com"));
                }
                return true;
            }
            xTask += iconSize + padding;
            if (mouseX >= xTask && mouseX <= xTask + iconSize) {
                minecraftClient.setScreen(new SettingsScreen("config", this, remotelyDir.toString(), settings));
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
            playSound(Sound.CREATE);
            openRemoteHostPopup(false);
            return true;
        }
        int hostTabAreaY = this.height - taskbarHeight + 2;
        if (mouseX >= startX && mouseX <= this.width) {
            int currentX = startX;
            for (int i = 0; i < tabs.size(); i++) {
                if (mouseX >= currentX && mouseX <= currentX + widths[i] && mouseY >= hostTabAreaY && mouseY <= hostTabAreaY + (taskbarHeight - 4)) {
                    if (button == 0) {
                        playSound(Sound.SWITCHTAB);
                        activeTabIndex = i;
                        if (i > 0) {
                            RemoteHostInfo host = remoteHosts.get(i - 1);
                            if (!host.isConnected && !host.isConnecting) {
                                connectRemoteHostAsync(host);
                            }
                        }
                    } else if (button == 1 && i > 0) {
                        playSound(Sound.CLICK);
                        openRemoteHostPopup(true);
                    }
                    return true;
                }
                currentX += widths[i] + gap;
            }
            if (mouseX >= plusX && mouseX <= plusX + 30 && mouseY >= hostTabAreaY && mouseY <= hostTabAreaY + (taskbarHeight - 4)) {
                playSound(Sound.CREATE);
                openRemoteHostPopup(false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (remoteHostPopup.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (addServerPopup.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (deleteServerPopup.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;

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
        if (remoteHostPopup.mouseReleased(mouseX, mouseY, button)) return true;
        if (addServerPopup.mouseReleased(mouseX, mouseY, button)) return true;
        if (deleteServerPopup.mouseReleased(mouseX, mouseY, button)) return true;

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
    public boolean mouseScrolled(double mouseX, double mouseY, /*? !=1.20.1 {*/ double horizontalAmount, /*?}*/ double verticalAmount) {
        if (remoteHostPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        if (addServerPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        if (deleteServerPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;

        scaleScroll(verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (remoteHostPopup.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (addServerPopup.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (deleteServerPopup.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (keyCode == GLFW.GLFW_KEY_S && modifiers == GLFW.GLFW_MOD_CONTROL) {
            minecraftClient.setScreen(new SettingsScreen("config", this, "", null));
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clipboard = minecraftClient.keyboard.getClipboard();
            // This now needs to be handled by the focused widget, which keyPressed delegation above should do.
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (remoteHostPopup.charTyped(chr, modifiers)) return true;
        if (addServerPopup.charTyped(chr, modifiers)) return true;
        if (deleteServerPopup.charTyped(chr, modifiers)) return true;

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

    private void openRemoteHostPopup(boolean isEditing) {
        this.isEditingHost = isEditing;
        if (isEditing) {
            RemoteHostInfo host = remoteHosts.get(activeTabIndex - 1);
            remoteHostConfirmButton.setMessage(Text.literal("Save"));
            remoteHostDeleteButton.visible = true;

            remoteHostNameInput.setText(host.name);
            remoteHostUserInput.setText(host.user);
            remoteHostIpInput.setText(host.ip);
            remoteHostPortInput.setText(String.valueOf(host.port));
            remoteHostPasswordInput.setText(host.password);
        } else {
            remoteHostConfirmButton.setMessage(Text.literal("Test & Add"));
            remoteHostDeleteButton.visible = false;
            remoteHostNameInput.setText("");
            remoteHostUserInput.setText("root");
            remoteHostIpInput.setText("");
            remoteHostPortInput.setText("22");
            remoteHostPasswordInput.setText("");
        }

        remoteHostPopup.setX((this.width - remoteHostPopup.getWidth()) / 2);
        remoteHostPopup.setY((this.height - remoteHostPopup.getHeight()) / 2);
        remoteHostPopup.show();
    }

    private void onConfirmRemoteHost() {
        String name = remoteHostNameInput.getText().trim();
        String user = remoteHostUserInput.getText().trim();
        String ip = remoteHostIpInput.getText().trim();
        String portStr = remoteHostPortInput.getText().trim();
        String password = remoteHostPasswordInput.getText();

        if (name.isEmpty() || ip.isEmpty() || portStr.isEmpty()) {
            new Notification("All fields must be filled!", Notification.Type.ERROR);
            return;
        }

        if (!isEditingHost && !testSSHConnection(user, ip, portStr, password)) {
            new Notification("Invalid details or Connection Failed", Notification.Type.ERROR);
            return;
        }

        RemoteHostInfo rh;
        if (isEditingHost) {
            rh = remoteHosts.get(activeTabIndex - 1);
            rh.name = name;
            rh.user = user;
            rh.ip = ip;
            try {
                rh.port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                rh.port = 22;
            }
            rh.password = password;
            saveRemoteHosts();
            closeRemoteHostPopup();
        } else {
            rh = new RemoteHostInfo();
            rh.name = name;
            rh.user = user;
            rh.ip = ip;
            try {
                rh.port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                rh.port = 22;
            }
            rh.password = password;
            rh.servers = new ArrayList<>();
            remoteHosts.add(rh);
            saveRemoteHosts();
            activeTabIndex = remoteHosts.size();
            closeRemoteHostPopup();
        }
    }

    private void onDeleteRemoteHost() {
        if (isEditingHost && activeTabIndex > 0 && activeTabIndex <= remoteHosts.size()) {
            remoteHosts.remove(activeTabIndex - 1);
            saveRemoteHosts();
            activeTabIndex = 0;
            closeRemoteHostPopup();
        }
    }

    private void openServerScreen(int index, List<ServerInfo> currentServers) {
        ServerInfo info = currentServers.get(index);
        MultiTerminalScreen mts = new MultiTerminalScreen(minecraftClient, this, remotelyClient, info);
        if (info.terminal == null) {
            info.terminal = new ServerTerminalInstance(minecraftClient, mts, UUID.randomUUID(), info);
            info.isRunning = false;
        }
        minecraftClient.setScreen(mts);
    }

    public static void openServerScreen(String path) {
        List<ServerInfo> allServers = new ArrayList<>(localServers);
        for (RemoteHostInfo rh : remoteHosts) {
            allServers.addAll(rh.servers);
        }
        for (ServerInfo info : allServers) {
            if (info.path.equals(path)) {
                MinecraftClient mc = MinecraftClient.getInstance();
                MultiTerminalScreen mts = new MultiTerminalScreen(mc, mc.currentScreen, RemotelyClient.INSTANCE, info);
                if (info.terminal == null) {
                    info.terminal = new ServerTerminalInstance(mc, mts, UUID.randomUUID(), info);
                    info.isRunning = false;
                }
                mc.setScreen(mts);
                return;
            }
        }
    }

    private void closeRemoteHostPopup() {
        if(remoteHostPopup != null) {
            remoteHostPopup.hide();
        }
    }

    private void openImportFileExplorer() {
        if (activeTabIndex == 0) {
            try {
                minecraftClient.setScreen(new FileExplorerScreen(this, new ServerInfo(remotelyDir.toString()), true));
            } catch (Exception ignored) {}
        } else {
            try {
                RemoteHostInfo remoteHost = remoteHosts.get(activeTabIndex - 1);
                String user = remoteHost.getUser();
                String homeDir = user.equals("root") ? "/root" : "/home/" + user;
                String path = homeDir + "/assets/remotely/servers/";
                ServerInfo rinfo = new ServerInfo(path);
                rinfo.isRemote = true;
                rinfo.remoteHost = remoteHost;
                minecraftClient.setScreen(new FileExplorerScreen(this, rinfo, true));
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
                serverInfo.path = remoteHost.getHomeDirectory() + "/assets/remotely/servers/" + serverInfo.name;
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
            ResourceManagerScreen modManagerScreen = new ResourceManagerScreen(minecraftClient, this, serverInfo);
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
        TerminalInstance dummyTerminal = new TerminalInstance(minecraftClient, null, UUID.randomUUID()) {
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

    public static void saveRemoteHosts() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "assets/remotely", "servers");
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
            Path dir = Paths.get(System.getProperty("user.dir"), "assets/remotely", "servers");
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
        if (!Files.exists(serversDir)) {
            try {
                Files.createDirectories(serversDir);
            } catch (IOException e) {
                new Notification("Failed To Create Server Directory.",  Notification.Type.ERROR);
            }
        }
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
                                boolean hasWaterfall = false;
                                boolean hasVelocity = false;
                                boolean hasFabric = false;
                                boolean hasVanilla = false;
                                boolean hasForge = false;
                                boolean hasNeoforge = false;
                                boolean hasLeaf = false;
                                boolean hasQuilt = false;
                                while (entriesZip.hasMoreElements()) {
                                    ZipEntry ze = entriesZip.nextElement();
                                    String name = ze.getName();
                                    if (name.startsWith("io/papermc/")) {
                                        hasPaper = true;
                                    }
                                    if (name.startsWith("io/github/waterfallmc/")) {
                                        hasWaterfall = true;
                                    }
                                    if (name.startsWith("com/velocitypowered/")) {
                                        hasVelocity = true;
                                    }
                                    if (name.equals("install.properties")) {
                                        hasFabric = true;
                                    }
                                    if (name.startsWith("net/minecraftforge/")) {
                                        hasForge = true;
                                    }
                                    if (name.startsWith("dev/mcvapi/")) {
                                        hasNeoforge = true;
                                    }
                                    if (name.startsWith("cn/dreeam/")) {
                                        hasLeaf = true;
                                    }
                                    if (name.equals("lang/installer.properties")) {
                                        hasQuilt = true;
                                    }
                                    if (name.startsWith("net/minecraft/")) {
                                        hasVanilla = true;
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
                                } else if (hasVelocity) {
                                    type = "Velocity";
                                } else if (hasWaterfall) {
                                    type = "Waterfall";
                                } else if (hasFabric) {
                                    type = "Fabric";
                                    ZipEntry installProps = zip.getEntry("install.properties");
                                    if (installProps != null) {
                                        Properties props = new Properties();
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
                                    ZipEntry versionJson = zip.getEntry("metadata.json");
                                    if (versionJson != null) {
                                        try (InputStream is = zip.getInputStream(versionJson);
                                             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                                            version = obj.get("version").getAsString();
                                        }
                                    }
                                } else if (hasLeaf) {
                                    type = "Leaf";
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
                                        Properties props = new Properties();
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
                            if (scanServers) addServer(folderName, normalizedPath.toString(), type, version, false, null);
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

    public static void addServer(String name, String path, String type, String version, boolean isRemote, RemoteHostInfo host) {
        ServerInfo newServer = new ServerInfo(path);
        newServer.name = name;
        newServer.path = path;
        newServer.type = type;
        newServer.version = version;
        newServer.isRunning = false;
        newServer.isRemote = isRemote;
        if (isRemote) {
            newServer.remoteHost = host;
            host.servers.add(newServer);
            saveRemoteHosts();
        } else {
            localServers.add(newServer);
            saveServers();
        }
    }

    public static void saveServers() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "assets/remotely", "servers");
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
            Path dir = Paths.get(System.getProperty("user.dir"), "assets/remotely", "servers");
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
            case "velocity" -> velocity;
            case "waterfall" -> waterfall;
            case "leaf" -> leaf;
            case "quilt" -> quilt;
            default -> unknown;
        };
    }

    private void deleteServerTrash(int index) {
        List<ServerInfo> currentServers = getCurrentServers();
        if (index >= 0 && index < currentServers.size()) {
            ServerInfo s = currentServers.get(index);
            String dateOfDeletion = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
            File trashDir = new File(System.getProperty("user.dir") + File.separator + "assets/remotely" + File.separator + "trash");
            if (!trashDir.exists()) trashDir.mkdirs();
            if (s.isRemote && s.remoteSSHManager != null) {
                String newRemotePath = s.path.replace("/servers/", "/trash/") + "-" + dateOfDeletion;
                try {
                    s.remoteSSHManager.renameRemoteFolder(s.path, newRemotePath);
                    new Notification("Server Moved To Trash.", Notification.Type.INFO);
                    currentServers.remove(index);
                } catch (Exception e) {
                    new Notification("Failed To Trash Remote Server.", Notification.Type.ERROR);
                }
            } else {
                File folderPath = new File(s.path);
                if (folderPath.exists()) {
                    String newLocalName = s.name + "-" + dateOfDeletion;
                    File trashSub = new File(trashDir, newLocalName);
                    if (folderPath.renameTo(trashSub)) {
                        new Notification("Server Moved To Trash.", Notification.Type.INFO);
                        currentServers.remove(index);
                    } else {
                        new Notification("Failed To Move Server To Trash.", Notification.Type.ERROR);
                    }

                }
            }
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

    @Override
    public void onDisplayed() {
        playSound(Sound.SERVERMANAGER);
    }

    @Override
    public void removed() {
        minecraftClient.getWindow().setScaleFactor(originalMCScale);
        parent.width = minecraftClient.getWindow().getScaledWidth();
        parent.height = minecraftClient.getWindow().getScaledHeight();
        targetScaleFactor = globalScaleFactor = animScaleFactor;
    }
}