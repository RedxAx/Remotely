package redxax.oxy.servers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.RemotelyClient;
import redxax.oxy.Render;
import redxax.oxy.SSHManager;
import redxax.oxy.config.Config;
import redxax.oxy.explorer.FileExplorerScreen;
import redxax.oxy.terminal.MultiTerminalScreen;
import redxax.oxy.terminal.ServerTerminalInstance;
import redxax.oxy.terminal.TerminalInstance;
import redxax.oxy.util.ImageUtil;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.lwjgl.glfw.GLFW;
import static redxax.oxy.config.Config.*;
import static redxax.oxy.Render.drawCustomButton;
import static redxax.oxy.Render.drawInnerBorder;
import static redxax.oxy.Render.drawOuterBorder;
import static redxax.oxy.util.DevUtil.devPrint;

public class ServerManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    private final List<ServerInfo> localServers;
    private final List<RemoteHostInfo> remoteHosts = new ArrayList<>();
    private int activeTabIndex = 0;
    private boolean serverPopupActive;
    private boolean editingServer;
    private int editingServerIndex = -1;
    private int serverPopupX;
    private int serverPopupY;
    private final int serverPopupWidth = 350;
    private final int serverPopupHeight = 160;
    private final StringBuilder serverNameBuffer = new StringBuilder();
    private final StringBuilder serverVersionBuffer = new StringBuilder();
    private String selectedServerType = "paper";
    private final List<String> serverTypes = Arrays.asList("paper", "vanilla", "fabric", "forge", "neoforge", "quilt");
    private int selectedTypeIndex = 0;
    private long serverLastBlinkTime = 0;
    private boolean serverCursorVisible = true;
    private int serverNameCursorPos = 0;
    private int serverVersionCursorPos = 0;
    private int serverNameScrollOffset = 0;
    private int serverVersionScrollOffset = 0;
    private final int tabHeight = 25;
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
    private BufferedImage loadingAnim;
    private final List<BufferedImage> loadingFrames = new ArrayList<>();
    private int currentLoadingFrame = 0;
    private long lastFrameTime = 0;
    private final boolean loading = false;
    private final int entryHeight = 25;
    private final int topBarHeight = 30;
    private BufferedImage terminalIcon, explorerIcon, editorIcon, terminal, serverIcon, paper, vanilla, fabric, forge, neoforge, quilt;
    private final int taskbarHeight = 20;
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
    }

    @Override
    protected void init() {
        super.init();
        if (localServers.isEmpty()) {
            loadSavedServers();
        }
        loadSavedRemoteHosts();
        scanForUnknownServers();
        activeTabIndex = remotelyClient.getSavedTabIndex();
        iconPosX.clear();
        iconPosY.clear();
        try {
            loadingAnim = ImageUtil.loadSpriteSheet("/assets/remotely/icons/loadinganim.png");
            int frameWidth = 16;
            int frameHeight = 16;
            int rows = loadingAnim.getHeight() / frameHeight;
            for (int i = 0; i < rows; i++) {
                BufferedImage frame = loadingAnim.getSubimage(0, i * frameHeight, frameWidth, frameHeight);
                loadingFrames.add(frame);
            }
            terminalIcon = ImageUtil.loadResourceIcon("/assets/remotely/icons/script.png");
            serverIcon = ImageUtil.loadResourceIcon("/assets/remotely/icons/server.png");
            explorerIcon = ImageUtil.loadResourceIcon("/assets/remotely/icons/folder.png");
            editorIcon = ImageUtil.loadResourceIcon("/assets/remotely/icons/text.png");
            terminal = ImageUtil.loadResourceIcon("/assets/remotely/icons/script.png");
            paper = ImageUtil.loadResourceIcon("/assets/remotely/icons/paper.png");
            vanilla = ImageUtil.loadResourceIcon("/assets/remotely/icons/vanilla.png");
            fabric = ImageUtil.loadResourceIcon("/assets/remotely/icons/fabric.png");
            forge = ImageUtil.loadResourceIcon("/assets/remotely/icons/forge.png");
            neoforge = ImageUtil.loadResourceIcon("/assets/remotely/icons/neoforge.png");
            quilt = ImageUtil.loadResourceIcon("/assets/remotely/icons/quilt.png");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, serverScreenBackgroundColor, serverScreenBackgroundColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        long currentTime = System.currentTimeMillis();
        if (currentTime - serverLastBlinkTime > 500) {
            serverCursorVisible = !serverCursorVisible;
            serverLastBlinkTime = currentTime;
        }
        if (serverPopupActive) {
            serverPopupX = (this.width - serverPopupWidth) / 2;
            serverPopupY = (this.height - serverPopupHeight) / 2;
        }
        if (serverTypePopupActive) {
            serverTypePopupX = (this.width - serverTypePopupWidth) / 2;
            serverTypePopupY = (this.height - serverTypePopupHeight) / 2;
        }
        if (remoteHostPopupActive) {
        }
        if (loading) {
            if (currentTime - lastFrameTime >= 40) {
                currentLoadingFrame = (currentLoadingFrame + 1) % loadingFrames.size();
                lastFrameTime = currentTime;
            }
            BufferedImage currentFrame = loadingFrames.get(currentLoadingFrame);
            int scale = 8;
            int imgWidth = currentFrame.getWidth() * scale;
            int imgHeight = currentFrame.getHeight() * scale;
            int centerX = (this.width - imgWidth) / 2;
            int centerY = (this.height - imgHeight) / 2;
            ImageUtil.drawBufferedImage(context, currentFrame, centerX, centerY, imgWidth, imgHeight);
        } else {
            renderDesktopIcons(context, mouseX, mouseY);
        }
        renderTaskbar(context, mouseX, mouseY);
        if (serverTypePopupActive) {
            context.fill(serverTypePopupX, serverTypePopupY, serverTypePopupX + serverTypePopupWidth, serverTypePopupY + serverTypePopupHeight, serverScreenBackgroundColor);
            drawInnerBorder(context, serverTypePopupX, serverTypePopupY, serverTypePopupWidth, serverTypePopupHeight, serverElementBorderColor);
            drawOuterBorder(context, serverTypePopupX, serverTypePopupY, serverTypePopupWidth, serverTypePopupHeight, globalBottomBorder);
            String stTitle = "Select Action";
            int stTitleW = minecraftClient.textRenderer.getWidth(stTitle);
            int stTitleX = serverTypePopupX + (serverTypePopupWidth - stTitleW) / 2;
            int stTitleY = serverTypePopupY + 5;
            context.drawText(minecraftClient.textRenderer, Text.literal(stTitle), stTitleX, stTitleY, buttonTextColor, Config.shadow);
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
        if (serverPopupActive) {
            context.fill(serverPopupX, serverPopupY, serverPopupX + serverPopupWidth, serverPopupY + serverPopupHeight, serverScreenBackgroundColor);
            drawInnerBorder(context, serverPopupX, serverPopupY, serverPopupWidth, serverPopupHeight, serverElementBorderColor);
            drawOuterBorder(context, serverPopupX, serverPopupY, serverPopupWidth, serverPopupHeight, globalBottomBorder);
            renderServerPopup(context, mouseX, mouseY);
        }
        if (remoteHostPopupActive) {
            int px = (this.width - remoteHostPopupW) / 2;
            int py = (this.height - remoteHostPopupH) / 2;
            context.fill(px, py, px + remoteHostPopupW, py + remoteHostPopupH, serverScreenBackgroundColor);
            drawInnerBorder(context, px, py, remoteHostPopupW, remoteHostPopupH, snippetPanelBorderColor);
            drawOuterBorder(context, px, py, remoteHostPopupW, remoteHostPopupH, globalBottomBorder);
            int labelY = py + 5;
            context.drawText(minecraftClient.textRenderer, Text.literal("Host Name:"), px + 5, labelY, buttonTextColor, false);
            int nameBoxY = labelY + 10;
            context.fill(px + 5, nameBoxY, px + remoteHostPopupW - 5, nameBoxY + 12, remoteHostActiveField == RemoteHostField.NAME ? popupFieldSelectedBackgroundColor : popupFieldBackgroundColor);
            String nh = remoteHostNameBuffer.toString();
            nh = trimTextToWidthWithEllipsis(nh, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(nh), px + 8, nameBoxY + 2, buttonTextColor, false);
            int userLabelY = nameBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("User Name:"), px + 5, userLabelY, buttonTextColor, false);
            int userBoxY = userLabelY + 10;
            context.fill(px + 5, userBoxY, px + remoteHostPopupW - 5, userBoxY + 12, remoteHostActiveField == RemoteHostField.USER ? popupFieldSelectedBackgroundColor : popupFieldBackgroundColor);
            String ub = remoteHostUserBuffer.toString();
            ub = trimTextToWidthWithEllipsis(ub, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ub), px + 8, userBoxY + 2, buttonTextColor, false);
            int ipLabelY = userBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("IP | Domain:"), px + 5, ipLabelY, buttonTextColor, false);
            int ipBoxY = ipLabelY + 10;
            context.fill(px + 5, ipBoxY, px + remoteHostPopupW - 5, ipBoxY + 12, remoteHostActiveField == RemoteHostField.IP ? popupFieldSelectedBackgroundColor : popupFieldBackgroundColor);
            String ih = remoteHostIPBuffer.toString();
            ih = trimTextToWidthWithEllipsis(ih, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ih), px + 8, ipBoxY + 2, buttonTextColor, false);
            int portLabelY = ipBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("Port:"), px + 5, portLabelY, buttonTextColor, false);
            int portBoxY = portLabelY + 10;
            context.fill(px + 5, portBoxY, px + remoteHostPopupW - 5, portBoxY + 12, remoteHostActiveField == RemoteHostField.PORT ? popupFieldSelectedBackgroundColor : popupFieldBackgroundColor);
            String ph = remoteHostPortBuffer.toString();
            ph = trimTextToWidthWithEllipsis(ph, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ph), px + 8, portBoxY + 2, buttonTextColor, false);
            int passLabelY = portBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("Password:"), px + 5, passLabelY, buttonTextColor, false);
            int passBoxY = passLabelY + 10;
            context.fill(px + 5, passBoxY, px + remoteHostPopupW - 5, passBoxY + 12, remoteHostActiveField == RemoteHostField.PASSWORD ? popupFieldSelectedBackgroundColor : popupFieldBackgroundColor);
            String mask = "";
            for (int i = 0; i < remoteHostPasswordBuffer.length(); i++) mask += "*";
            mask = trimTextToWidthWithEllipsis(mask, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(mask), px + 8, passBoxY + 2, buttonTextColor, false);
            int confirmButtonY = passBoxY + 33;
            String createText = isEditingHost ? "Save" : "Test & Add";
            int cw = minecraftClient.textRenderer.getWidth(createText) + 10;
            int confirmX = px + 5;
            boolean hoverConfirm = mouseX >= confirmX && mouseX <= confirmX + cw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawCustomButton(context, confirmX, confirmButtonY, createText, minecraftClient, hoverConfirm, true, true, buttonTextColor, buttonTextHoverColor);
            String cancelText = "Cancel";
            int cancW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
            int cancX = px + remoteHostPopupW - (cancW + 5);
            boolean hoverCancel = mouseX >= cancX && mouseX <= cancX + cancW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawCustomButton(context, cancX, confirmButtonY, cancelText, minecraftClient, hoverCancel, true, true, buttonTextColor, buttonTextDeleteColor);
            if (isEditingHost) {
                String deleteText = "Delete";
                int delW = minecraftClient.textRenderer.getWidth(deleteText) + 10;
                int delX = px + (remoteHostPopupW - delW) / 2;
                boolean hoverDelete = mouseX >= delX && mouseX <= delX + delW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
                drawCustomButton(context, delX, confirmButtonY, deleteText, minecraftClient, hoverDelete, true, true, buttonTextDeleteColor, buttonTextDeleteHoverColor);
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
            currentX = lerp(currentX, baseX, 0.2f);
            currentY = lerp(currentY, baseY, 0.2f);
            iconPosX.set(i, currentX);
            iconPosY.set(i, currentY);
            serverIconRects.add(new IconRect((int) currentX, (int) currentY, iconSize, iconSize, (i < currentServers.size() ? i : -1), i == currentServers.size()));
            if (i < currentServers.size()) {
                ServerInfo server = currentServers.get(i);
                BufferedImage icon = getServerIcon(server);
                ImageUtil.drawBufferedImage(context, icon, (int) currentX, (int) currentY, iconSize, iconSize);
                if (selectedDesktopIndex == i) {
                    drawInnerBorder(context, (int) currentX - 1, (int) currentY - 1, iconSize + 2, iconSize + 2, explorerElementSelectedBorderColor);
                    drawOuterBorder(context, (int) currentX - 1, (int) currentY - 1, iconSize + 2, iconSize + 2, globalBottomBorder);
                } else {
                    drawOuterBorder(context, (int) currentX, (int) currentY, iconSize, iconSize, globalBottomBorder);
                }
                if (mouseX >= currentX && mouseX <= currentX + iconSize && mouseY >= currentY && mouseY <= currentY + iconSize) {
                    context.fill((int) currentX, (int) currentY, (int) currentX + iconSize, (int) currentY + iconSize, 0x40FFFFFF);
                }
                String name = server.name;
                String trimmed = trimTextToWidthWithEllipsis(name, 80);
                int textWidth = minecraftClient.textRenderer.getWidth(trimmed);
                int textX = (int) currentX + (iconSize - textWidth) / 2;
                context.drawText(minecraftClient.textRenderer, Text.literal(trimmed), textX, (int) currentY + iconSize + 2, serverElementTextColor, Config.shadow);
            } else {
                ImageUtil.drawBufferedImage(context, serverIcon, (int) currentX, (int) currentY, iconSize, iconSize);
                if (mouseX >= currentX && mouseX <= currentX + iconSize && mouseY >= currentY && mouseY <= currentY + iconSize) {
                    context.fill((int) currentX, (int) currentY, (int) currentX + iconSize, (int) currentY + iconSize, 0x40FFFFFF);
                }
                String newLabel = "New Server";
                String trimmed = trimTextToWidthWithEllipsis(newLabel, 80);
                int labelWidth = minecraftClient.textRenderer.getWidth(trimmed);
                int labelX = (int) currentX + (iconSize - labelWidth) / 2;
                context.drawText(minecraftClient.textRenderer, Text.literal(trimmed), labelX, (int) currentY + iconSize + 2, serverElementTextColor, Config.shadow);
            }
        }
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void renderTaskbar(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, this.height - taskbarHeight, this.width, this.height, headerBackgroundColor);
        drawInnerBorder(context, 0, this.height - taskbarHeight, this.width, taskbarHeight, headerBorderColor);
        drawOuterBorder(context, 0, this.height - taskbarHeight, this.width, taskbarHeight, globalBottomBorder);

        int iconSize = 16;
        int padding = 5;
        int yTask = this.height - taskbarHeight + (taskbarHeight - iconSize) / 2;
        int xTask = padding;

        ImageUtil.drawBufferedImage(context, terminalIcon, xTask, yTask, iconSize, iconSize);
        xTask += iconSize + padding;
        ImageUtil.drawBufferedImage(context, explorerIcon, xTask, yTask, iconSize, iconSize);

        renderHostTabs(context, mouseX, mouseY);
    }

    private void renderHostTabs(DrawContext context, int mouseX, int mouseY) {
        List<String> tabs = getAllTabNames();
        int height = taskbarHeight - 4;
        int padding = 4;
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
        int y = this.height - taskbarHeight + 2;
        int plusWidth = 30;
        int startX = this.width - totalWidth - 5;
        int plusX = startX - gap - plusWidth;
        boolean isPlusHovered = mouseX >= plusX && mouseX <= plusX + plusWidth && mouseY >= y && mouseY <= y + height;
        context.fill(plusX, y, plusX + plusWidth, y + height, isPlusHovered ? tabBackgroundHoverColor : tabBackgroundColor);
        drawInnerBorder(context, plusX, y, plusWidth, height, isPlusHovered ? tabBorderHoverColor : tabBorderColor);
        drawOuterBorder(context, plusX, y, plusWidth, height, globalBottomBorder);
        String plus = "+";
        int plusTextWidth = minecraftClient.textRenderer.getWidth(plus);
        context.drawText(minecraftClient.textRenderer, Text.literal(plus), plusX + (plusWidth - plusTextWidth) / 2, y + (height - minecraftClient.textRenderer.fontHeight) / 2, tabTextColor, Config.shadow);
        int currentX = startX;
        for (int i = 0; i < tabs.size(); i++) {
            int w = widths[i];
            boolean isActive = (i == activeTabIndex);
            boolean isHovered = mouseX >= currentX && mouseX <= currentX + w && mouseY >= y && mouseY <= y + height;
            int bg = isActive ? tabSelectedBackgroundColor : isHovered ? tabBackgroundHoverColor : tabBackgroundColor;
            context.fill(currentX, y, currentX + w, y + height, bg);
            drawInnerBorder(context, currentX, y, w, height, isActive ? tabSelectedBorderColor : isHovered ? tabBorderHoverColor : tabBorderColor);
            drawOuterBorder(context, currentX, y, w, height, globalBottomBorder);
            String tabText = tabs.get(i);
            int textWidth = minecraftClient.textRenderer.getWidth(tabText);
            int textX = currentX + (w - textWidth) / 2;
            int textY = y + (height - minecraftClient.textRenderer.fontHeight) / 2;
            context.drawText(minecraftClient.textRenderer, Text.literal(tabText), textX, textY, tabTextColor, Config.shadow);
            currentX += w + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (serverPopupActive) {
            int spx = (this.width - serverPopupWidth) / 2;
            int spy = (this.height - serverPopupHeight) / 2;
            if (mouseX < spx || mouseX > spx + serverPopupWidth || mouseY < spy || mouseY > spy + serverPopupHeight) {
                closePopup();
                return true;
            }
        }
        if (serverTypePopupActive) {
            int stpx = (this.width - serverTypePopupWidth) / 2;
            int stpy = (this.height - serverTypePopupHeight) / 2;
            if (mouseX < stpx || mouseX > stpx + serverTypePopupWidth || mouseY < stpy || mouseY > stpy + serverTypePopupHeight) {
                serverTypePopupActive = false;
                return true;
            }
        }
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
                deleteServerTrash(deletionPopupServerIndex);
                deletionPopupActive = false;
                return true;
            }
            if (mouseX >= removeX && mouseX <= removeX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20 && button == 0) {
                deleteServerRemove(deletionPopupServerIndex);
                deletionPopupActive = false;
                return true;
            }
            if (mouseX >= cancelX && mouseX <= cancelX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20 && button == 0) {
                deletionPopupActive = false;
                return true;
            }
            return true;
        }
        if (serverPopupActive) {
            if (mouseX < serverPopupX || mouseX > serverPopupX + serverPopupWidth || mouseY < serverPopupY || mouseY > serverPopupY + serverPopupHeight) {
                closePopup();
                return true;
            }
            handleServerPopupClick(mouseX, mouseY, button, getCurrentServers());
            return true;
        }
        if (remoteHostPopupActive) {
            return handleRemoteHostPopupClick(mouseX, mouseY, button);
        }
        if (serverTypePopupActive) {
            if (handleServerTypePopupClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (Render.ContextMenu.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        boolean clickedOnIcon = false;
        for (IconRect rect : serverIconRects) {
            if (mouseX >= rect.x && mouseX <= rect.x + rect.width && mouseY >= rect.y && mouseY <= rect.y + rect.height) {
                clickedOnIcon = true;
                if (button == 0) {
                    if (rect.isCreate) {
                        serverTypePopupActive = true;
                        return true;
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
                        return true;
                    }
                } else if (button == 1 && !rect.isCreate) {
                    Render.ContextMenu.hide();
                    Render.ContextMenu.addItem("Edit", () -> {
                        editingServer = true;
                        editingServerIndex = rect.serverIndex;
                        serverPopupActive = true;
                        ServerInfo info = getCurrentServers().get(rect.serverIndex);
                        serverNameBuffer.setLength(0);
                        serverNameBuffer.append(info.name);
                        serverVersionBuffer.setLength(0);
                        serverVersionBuffer.append(info.version);
                        selectedTypeIndex = serverTypes.indexOf(info.type);
                        if (selectedTypeIndex < 0) selectedTypeIndex = 0;
                        nameFieldFocused = false;
                        versionFieldFocused = false;
                    }, buttonTextHoverColor);
                    Render.ContextMenu.addItem("Delete", () -> {
                        deletionPopupActive = true;
                        deletionPopupServerIndex = rect.serverIndex;
                    }, buttonTextHoverColor);
                    Render.ContextMenu.show((int) mouseX, (int) mouseY, 80, this.width, this.height);
                    return true;
                }
            }
        }
        if (!clickedOnIcon) {
            canDrag = false;
        }
        int taskbarY = this.height - taskbarHeight;
        int iconSize = 16;
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
                    minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, new ServerInfo("C:/"), false));
                } catch (Exception ignored) {}
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
        int plusButtonY = taskbarY + 2;
        int plusHeight = taskbarHeight - 4;
        if (mouseX >= plusX && mouseX <= plusX + plusWidth &&
                mouseY >= plusButtonY && mouseY <= plusButtonY + plusHeight && button == 0) {
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
                        activeTabIndex = actualIndex;
                        if (actualIndex > 0) {
                            RemoteHostInfo host = remoteHosts.get(actualIndex - 1);
                            if (!host.isConnected && !host.isConnecting) {
                                connectRemoteHostAsync(host);
                            }
                        }
                    } else if (button == 1 && actualIndex > 0) {
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
        int contentYStart = topBarHeight + tabHeight + 5 + verticalPadding;
        int panelHeight = this.height - contentYStart - 5;
        List<ServerInfo> currentServers = getCurrentServers();
        int maxScroll = Math.max(0, currentServers.size() * (entryHeight + 1) - panelHeight);
        targetOffset -= verticalAmount * entryHeight * 2;
        if (targetOffset < 0) targetOffset = 0;
        if (targetOffset > maxScroll) targetOffset = maxScroll;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
            } else if (serverPopupActive) {
                if (nameFieldFocused) {
                    serverNameBuffer.insert(serverNameCursorPos, clipboard);
                    serverNameCursorPos += clipboard.length();
                    return true;
                } else if (versionFieldFocused) {
                    serverVersionBuffer.insert(serverVersionCursorPos, clipboard);
                    serverVersionCursorPos += clipboard.length();
                    return true;
                }
            }
        }
        if (remoteHostPopupActive) {
            handleRemoteHostTypingKey(keyCode);
            return true;
        }
        if (!serverPopupActive) return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (!serverPopupActive) return super.charTyped(chr, modifiers);
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
        if (hostInfo.sshManager == null) {
            hostInfo.sshManager = new SSHManager(hostInfo);
        }
        hostInfo.isConnecting = true;
        new Thread(() -> {
            try {
                hostInfo.sshManager.connectToRemoteHost(hostInfo.getUser(), hostInfo.getIp(), hostInfo.getPort(), hostInfo.getPassword());
                hostInfo.sshManager.connectSFTP();
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

    private List<ServerInfo> getCurrentServers() {
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
                serverTypePopupActive = false;
                editingServer = false;
                editingServerIndex = -1;
                serverPopupActive = true;
                serverNameBuffer.setLength(0);
                serverVersionBuffer.setLength(0);
                serverNameBuffer.append("MyServer");
                selectedTypeIndex = 0;
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

    private void handleServerPopupClick(double mouseX, double mouseY, int button, List<ServerInfo> currentServers) {
        int confirmButtonY = serverPopupY + serverPopupHeight - 22;
        String okText = editingServer ? "Save" : "Create";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmX = serverPopupX + 5;
        if (mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
            if (mouseX >= confirmX && mouseX <= confirmX + okW && button == 0) {
                if (serverNameBuffer.toString().trim().isEmpty()) {
                    return;
                }
                createOrSaveServer();
                return;
            }
            if (mouseX >= serverPopupX + serverPopupWidth - (minecraftClient.textRenderer.getWidth("Cancel") + 10 + 5) && mouseX <= serverPopupX + serverPopupWidth - 5 && button == 0) {
                closePopup();
                return;
            }
        }
        int nameBoxY = serverPopupY + 30;
        int nameBoxH = 12;
        if (mouseX >= serverPopupX + 5 && mouseX <= serverPopupX + serverPopupWidth - 5 && mouseY >= nameBoxY && mouseY <= nameBoxY + nameBoxH && button == 0) {
            nameFieldFocused = true;
            versionFieldFocused = false;
            return;
        }
        int typeLabelY = nameBoxY + nameBoxH + 15;
        int typeBoxY = typeLabelY + 15;
        int boxWidth = 150;
        int arrowLeftX = serverPopupX + 5 + boxWidth + 5;
        int arrowRightX = arrowLeftX + 12 + 5;
        if (mouseX >= arrowLeftX && mouseX <= arrowLeftX + 12 && mouseY >= typeBoxY && mouseY <= typeBoxY + 12 && button == 0) {
            selectedTypeIndex = (selectedTypeIndex - 1 + serverTypes.size()) % serverTypes.size();
            selectedServerType = serverTypes.get(selectedTypeIndex);
            return;
        }
        if (mouseX >= arrowRightX && mouseX <= arrowRightX + 12 && mouseY >= typeBoxY && mouseY <= typeBoxY + 12 && button == 0) {
            selectedTypeIndex = (selectedTypeIndex + 1) % serverTypes.size();
            selectedServerType = serverTypes.get(selectedTypeIndex);
            return;
        }
        int versionLabelY = typeBoxY + 20;
        int versionBoxY = versionLabelY + 12;
        int versionBoxH = 12;
        if (mouseX >= serverPopupX + 5 && mouseX <= serverPopupX + serverPopupWidth - 5 && mouseY >= versionBoxY && mouseY <= versionBoxY + versionBoxH && button == 0) {
            nameFieldFocused = false;
            versionFieldFocused = true;
        }
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

    private void closePopup() {
        serverPopupActive = false;
        editingServer = false;
        editingServerIndex = -1;
        serverNameBuffer.setLength(0);
        serverVersionBuffer.setLength(0);
        serverNameCursorPos = 0;
        serverVersionCursorPos = 0;
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

    public void createOrSaveServer() {
        List<ServerInfo> currentServers = getCurrentServers();
        String name = serverNameBuffer.toString().trim();
        String ver = serverVersionBuffer.toString().trim().isEmpty() ? "latest" : serverVersionBuffer.toString().trim();
        selectedServerType = serverTypes.get(selectedTypeIndex);
        if (!editingServer) {
            String path;
            if (activeTabIndex == 0) {
                path = "C:/remotely/servers/" + name;
            } else {
                RemoteHostInfo remoteHost = remoteHosts.get(activeTabIndex - 1);
                String user = remoteHost.getUser();
                String homeDir = user.equals("root") ? "/root" : "/home/" + user;
                path = homeDir + "/remotely/servers/" + name;
            }
            ServerInfo newInfo = new ServerInfo(path);
            newInfo.name = name;
            newInfo.path = path;
            newInfo.type = selectedServerType;
            newInfo.version = ver;
            newInfo.isRunning = false;
            if (activeTabIndex == 0) {
                newInfo.isRemote = false;
                newInfo.remoteHost = null;
                currentServers.add(newInfo);
                Path serverJarPath = Paths.get(path, "server.jar");
                if (!Files.exists(serverJarPath)) {
                    runMrPackInstaller(newInfo);
                }
            } else {
                newInfo.isRemote = true;
                newInfo.remoteHost = remoteHosts.get(activeTabIndex - 1);
                newInfo.remoteSSHManager = new SSHManager(newInfo.remoteHost);
                newInfo.remoteSSHManager.connectToRemoteHost(newInfo.remoteHost.getUser(), newInfo.remoteHost.getIp(), newInfo.remoteHost.getPort(), newInfo.remoteHost.getPassword());
                if (!newInfo.remoteSSHManager.isSFTPConnected()) {
                    try {
                        newInfo.remoteSSHManager.connectSFTP();
                    } catch (Exception ignored) {}
                }
                runMrPackInstallerRemote(newInfo, newInfo.remoteHost);
                currentServers.add(newInfo);
            }
            saveServers();
            saveRemoteHosts();
        } else {
            ServerInfo s = currentServers.get(editingServerIndex);
            s.name = name;
            s.type = selectedServerType;
            s.version = ver;
            if (activeTabIndex != 0) {
                s.isRemote = true;
                s.remoteHost = remoteHosts.get(activeTabIndex - 1);
                if (s.remoteSSHManager == null) {
                    s.remoteSSHManager = new SSHManager(s.remoteHost);
                    s.remoteSSHManager.connectToRemoteHost(s.remoteHost.getUser(), s.remoteHost.getIp(), s.remoteHost.getPort(), s.remoteHost.getPassword());
                }
            } else {
                s.isRemote = false;
                s.remoteHost = null;
            }
            saveServers();
            saveRemoteHosts();
        }
        closePopup();
    }

    private void runMrPackInstaller(ServerInfo serverInfo) {
        new Thread(() -> {
            try {
                Path serverDir = Paths.get(serverInfo.path);
                if (!Files.exists(serverDir)) {
                    Files.createDirectories(serverDir);
                }
                String exePath = "C:/remotely/mrpack-install-windows.exe";
                if (!Files.exists(Paths.get(exePath))) {
                    try (InputStream in = new URL("https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-windows.exe").openStream()) {
                        Files.copy(in, Paths.get(exePath), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                List<String> cmd = new ArrayList<>();
                cmd.add(exePath);
                cmd.add("server");
                cmd.add(serverInfo.type.equalsIgnoreCase("vanilla") ? "vanilla" : serverInfo.type);
                cmd.add("--server-dir");
                cmd.add(serverInfo.path);
                if (!serverInfo.version.equalsIgnoreCase("latest")) {
                    cmd.add("--minecraft-version");
                    cmd.add(serverInfo.version);
                }
                cmd.add("--server-file");
                cmd.add("server.jar");
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(serverDir.toFile());
                pb.redirectErrorStream(true);
                pb.start().waitFor();
            } catch (Exception ignored) {}
        }).start();
    }

    private void runMrPackInstallerRemote(ServerInfo serverInfo, RemoteHostInfo hostInfo) {
        try {
            if (serverInfo.remoteSSHManager == null) {
                serverInfo.remoteSSHManager = new SSHManager(hostInfo);
                serverInfo.remoteSSHManager.connectToRemoteHost(hostInfo.getUser(), hostInfo.getIp(), hostInfo.getPort(), hostInfo.getPassword());
            }
            if (!serverInfo.remoteSSHManager.isSFTPConnected()) {
                serverInfo.remoteSSHManager.connectSFTP();
            }
            serverInfo.remoteSSHManager.prepareRemoteDirectory(serverInfo.path);
            serverInfo.remoteSSHManager.runMrPackOnRemote(serverInfo);
        } catch (Exception e) {
        }
    }

    private void openImportFileExplorer() {
        if (activeTabIndex == 0) {
            try {
                minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, new ServerInfo("C:/"), true));
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
                if (remoteHost.sshManager == null) {
                    remoteHost.sshManager = new SSHManager(remoteHost);
                    connectRemoteHostAsync(remoteHost);
                }
                serverInfo.remoteSSHManager = remoteHost.sshManager;
                if (!remoteHost.sshManager.isSSH()) {
                    long startTime = System.currentTimeMillis();
                    long timeout = 10000;
                    while (!remoteHost.sshManager.isSSH() && System.currentTimeMillis() - startTime < timeout) {
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
                serverInfo.path = "C:/remotely/servers/" + serverInfo.name;
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

    private void drawTextField(DrawContext context, StringBuilder buffer, int cursorPos, int scrollOffset, int x, int y, int maxWidth, boolean drawCursor) {
        String fullText = buffer.toString();
        int wBeforeCursor = minecraftClient.textRenderer.getWidth(fullText.substring(0, Math.min(cursorPos, fullText.length())));
        if (wBeforeCursor < scrollOffset) scrollOffset = wBeforeCursor;
        int availableWidth = maxWidth - 6;
        if (wBeforeCursor - scrollOffset > availableWidth) scrollOffset = wBeforeCursor - availableWidth;
        if (scrollOffset < 0) scrollOffset = 0;
        int charStart = 0;
        while (charStart < fullText.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullText.substring(0, charStart));
            if (cw >= scrollOffset) break;
            charStart++;
        }
        int visibleEnd = charStart;
        while (visibleEnd <= fullText.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullText.substring(charStart, visibleEnd));
            if (cw > availableWidth) break;
            visibleEnd++;
        }
        visibleEnd--;
        if (visibleEnd < charStart) visibleEnd = charStart;
        String visible = fullText.substring(Math.min(charStart, fullText.length()), Math.min(visibleEnd, fullText.length()));
        context.drawText(minecraftClient.textRenderer, Text.literal(visible), x, y, buttonTextColor, false);
        if (drawCursor) {
            int cursorPosVisible = Math.min(cursorPos - charStart, visible.length());
            if (cursorPosVisible < 0) cursorPosVisible = 0;
            int cX = x + minecraftClient.textRenderer.getWidth(visible.substring(0, Math.min(cursorPosVisible, visible.length())));
            context.fill(cX, y - 1, cX + 1, y + minecraftClient.textRenderer.fontHeight, buttonTextColor);
        }
        if (nameFieldFocused) serverNameScrollOffset = scrollOffset;
        if (versionFieldFocused) serverVersionScrollOffset = scrollOffset;
    }

    private void trimAndDrawText(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        String t = trimTextToWidthWithEllipsis(text, maxWidth);
        context.drawText(minecraftClient.textRenderer, Text.literal(t), x, y, color, false);
    }

    private String trimTextToWidthWithEllipsis(String text, int maxWidth) {
        if (minecraftClient.textRenderer.getWidth(text) <= maxWidth) return text;
        while (minecraftClient.textRenderer.getWidth(text + "..") > maxWidth && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
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

    private void saveRemoteHosts() {
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
        Path serversDir = Paths.get("C:/remotely/servers/").toAbsolutePath().normalize();
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

    private void saveServers() {
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
        context.fill(popupX, popupY, popupX + popupW, popupY + popupH, serverScreenBackgroundColor);
        drawInnerBorder(context, popupX, popupY, popupW, popupH, serverElementBorderColor);
        drawOuterBorder(context, popupX, popupY, popupW, popupH, globalBottomBorder);
        String warn = "Are you sure you want to delete this server?";
        int warnW = minecraftClient.textRenderer.getWidth(warn);
        context.drawText(minecraftClient.textRenderer, Text.literal(warn), popupX + (popupW - warnW) / 2, popupY + 20, 0xFFFF5555, Config.shadow);
        int btnWidth = (popupW - 40) / 3;
        int btnY = popupY + popupH - 40;
        int deleteX = popupX + 10;
        int removeX = deleteX + btnWidth + 10;
        int cancelX = removeX + btnWidth + 10;
        drawCustomButton(context, deleteX, btnY, "Delete", minecraftClient, (mouseX >= deleteX && mouseX <= deleteX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20), true, true, buttonTextColor, buttonTextHoverColor);
        drawCustomButton(context, removeX, btnY, "Remove", minecraftClient, (mouseX >= removeX && mouseX <= removeX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20), true, true, buttonTextColor, buttonTextHoverColor);
        drawCustomButton(context, cancelX, btnY, "Cancel", minecraftClient, (mouseX >= cancelX && mouseX <= cancelX + btnWidth && mouseY >= btnY && mouseY <= btnY + 20), true, true, buttonTextColor, buttonTextDeleteColor);
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
        int bg = hovered ? serverElementBackgroundHoverColor : serverElementBackgroundColor;
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, bg);
        drawInnerBorder(context, boxX, boxY, boxW, boxH, hovered ? serverElementBorderHoverColor : serverElementBorderColor);
        drawOuterBorder(context, boxX, boxY, boxW, boxH, globalBottomBorder);
        int tw = minecraftClient.textRenderer.getWidth(text);
        int tx = boxX + (boxW - tw) / 2;
        int ty = boxY + (boxH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(text), tx, ty, screensTitleTextColor, false);
    }

    private void renderServerPopup(DrawContext context, int mouseX, int mouseY) {
        int nameLabelY = serverPopupY + 10;
        trimAndDrawText(context, "Server Name:", serverPopupX + 5, nameLabelY, serverPopupWidth - 10, screensTitleTextColor);
        int nameBoxY = nameLabelY + 12;
        int nameBoxHeight = 12;
        context.fill(serverPopupX + 5, nameBoxY, serverPopupX + serverPopupWidth - 5, nameBoxY + nameBoxHeight, nameFieldFocused ? popupFieldSelectedBackgroundColor : popupFieldBackgroundColor);
        drawTextField(context, serverNameBuffer, serverNameCursorPos, serverNameScrollOffset, serverPopupX + 8, nameBoxY + 2, serverPopupWidth - 10, nameFieldFocused && serverCursorVisible);
        int typeLabelY = nameBoxY + nameBoxHeight + 15;
        trimAndDrawText(context, "Server Type:", serverPopupX + 5, typeLabelY, serverPopupWidth - 10, screensTitleTextColor);
        int typeBoxY = typeLabelY + 15;
        int boxWidth = 150;
        context.fill(serverPopupX + 5, typeBoxY, serverPopupX + 5 + boxWidth, typeBoxY + 12, popupFieldBackgroundColor);
        String st = serverTypes.get(selectedTypeIndex);
        st = trimTextToWidthWithEllipsis(st, boxWidth - 2);
        context.drawText(minecraftClient.textRenderer, Text.literal(st), serverPopupX + 8, typeBoxY + 2, screensTitleTextColor, false);
        int arrowLeftX = serverPopupX + 5 + boxWidth + 5;
        context.fill(arrowLeftX, typeBoxY, arrowLeftX + 12, typeBoxY + 12, 0xFF555555);
        context.drawText(minecraftClient.textRenderer, Text.literal("<"), arrowLeftX + 4, typeBoxY + 2, screensTitleTextColor, false);
        int arrowRightX = arrowLeftX + 12 + 5;
        context.fill(arrowRightX, typeBoxY, arrowRightX + 12, typeBoxY + 12, 0xFF555555);
        context.drawText(minecraftClient.textRenderer, Text.literal(">"), arrowRightX + 3, typeBoxY + 2, screensTitleTextColor, false);
        int versionLabelY = typeBoxY + 20;
        trimAndDrawText(context, "Minecraft Version:", serverPopupX + 5, versionLabelY, serverPopupWidth - 10, screensTitleTextColor);
        int versionBoxY = versionLabelY + 12;
        context.fill(serverPopupX + 5, versionBoxY, serverPopupX + serverPopupWidth - 5, versionBoxY + 12, versionFieldFocused ? popupFieldSelectedBackgroundColor : popupFieldBackgroundColor);
        drawTextField(context, serverVersionBuffer, serverVersionCursorPos, serverVersionScrollOffset, serverPopupX + 8, versionBoxY + 2, serverPopupWidth - 10, versionFieldFocused && serverCursorVisible);
        String okText = editingServer ? "Save" : "Create";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmButtonX = serverPopupX + 5;
        int confirmButtonY = serverPopupY + serverPopupHeight - 22;
        boolean okHover = mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawCustomButton(context, confirmButtonX, confirmButtonY, okText, minecraftClient, okHover, true, true, buttonTextColor, buttonTextHoverColor);
        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = serverPopupX + serverPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawCustomButton(context, cancelButtonX, confirmButtonY, cancelText, minecraftClient, cancelHover, true, true, buttonTextColor, buttonTextDeleteColor);
        if (editingServer) {
            String deleteText = "Delete Server";
            int dw = minecraftClient.textRenderer.getWidth(deleteText) + 10;
            int deleteX = serverPopupX + (serverPopupWidth - dw) / 2;
            boolean delHover = mouseX >= deleteX && mouseX <= deleteX + dw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawCustomButton(context, deleteX, confirmButtonY, deleteText, minecraftClient, delHover, true, true, buttonTextDeleteColor, buttonTextDeleteHoverColor);
        }
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

}
