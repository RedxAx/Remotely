package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import redxax.oxy.*;
import redxax.oxy.explorer.FileExplorerScreen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import org.lwjgl.glfw.GLFW;

import static redxax.oxy.ImageUtil.drawBufferedImage;
import static redxax.oxy.ImageUtil.loadSpriteSheet;
import static redxax.oxy.MultiTerminalScreen.TAB_HEIGHT;
import static redxax.oxy.Render.*;

public class ServerManagerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    private final List<ServerInfo> localServers;
    private final List<RemoteHostInfo> remoteHosts = new ArrayList<>();
    private int activeTabIndex = 0;
    private boolean serverPopupActive;
    private boolean creatingServer;
    private boolean editingServer;
    private boolean deletingServer;
    private boolean serverCreationWarning;
    private int editingServerIndex = -1;
    private int serverPopupX;
    private int serverPopupY;
    private final int serverPopupWidth = 350;
    private final int serverPopupHeight = 160;
    private final StringBuilder serverNameBuffer = new StringBuilder();
    private final StringBuilder serverVersionBuffer = new StringBuilder();
    private String selectedServerType = "paper";
    private final List<String> serverTypes = Arrays.asList("paper","vanilla","fabric","forge","neoforge","quilt");
    private int selectedTypeIndex = 0;
    private long serverLastBlinkTime = 0;
    private boolean serverCursorVisible = true;
    private int serverNameCursorPos = 0;
    private int serverVersionCursorPos = 0;
    private int serverNameScrollOffset = 0;
    private int serverVersionScrollOffset = 0;
    private int hoveredServerIndex = -1;
    private boolean plusButtonHovered = false;
    private boolean remoteTabPlusHovered = false;
    private int tabScrollOffset = 0;
    private int tabPadding = 5;
    private int tabHeight = 25;
    private int verticalPadding = 2;
    private boolean nameFieldFocused = true;
    private boolean versionFieldFocused = false;
    private float smoothOffset = 0;
    private float targetOffset = 0;
    private float scrollSpeed = 0.2f;
    private boolean serverTypePopupActive;
    private boolean importingServer;
    private boolean modpackInstallation;
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
    private List<BufferedImage> loadingFrames = new ArrayList<>();
    private int currentLoadingFrame = 0;
    private long lastFrameTime = 0;
    private boolean loading = false;
    private final int entryHeight = 25;
    private final int topBarHeight = 30;

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
        try {
            loadingAnim = loadSpriteSheet("/assets/remotely/icons/loadinganim.png");
            int frameWidth = 16;
            int frameHeight = 16;
            int rows = loadingAnim.getHeight() / frameHeight;
            for (int i = 0; i < rows; i++) {
                BufferedImage frame = loadingAnim.getSubimage(0, i * frameHeight, frameWidth, frameHeight);
                loadingFrames.add(frame);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    static class TabInfo {
        String name;
        int width;
        TabInfo(String n, int w) {
            name = n;
            width = w;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - serverLastBlinkTime > 500) {
            serverCursorVisible = !serverCursorVisible;
            serverLastBlinkTime = currentTime;
        }
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, topBarHeight, 0xFF222222);
        drawInnerBorder(context, 0, 0, this.width, topBarHeight, 0xFF333333);
        String title = "Remotely - Server Manager";
        context.drawText(minecraftClient.textRenderer, Text.literal(title), 10, 10, textColor, Config.shadow);

        int closeButtonY = 5;
        int closeButtonX = this.width - 70;
        int closeButtonWidth = 60;
        boolean hoveredBack = mouseX >= closeButtonX && mouseX <= closeButtonX + closeButtonWidth && mouseY >= closeButtonY && mouseY <= closeButtonY + buttonH;
        Render.drawHeaderButton(context, closeButtonX, closeButtonY, "Close", minecraftClient, hoveredBack, false, textColor, redVeryBright);

        renderTabs(context, mouseX, mouseY);
        int contentYStart = topBarHeight + tabHeight + 5 + verticalPadding;
        int panelHeight = this.height - contentYStart - 5;
        int panelWidth = this.width - 10;
        context.fill(5, contentYStart, 5 + panelWidth, contentYStart + panelHeight, lighterColor);
        drawInnerBorder(context, 5, contentYStart, panelWidth, panelHeight, borderColor);
        if (activeTabIndex == 0) {
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
                drawBufferedImage(context, currentFrame, centerX, centerY, imgWidth, imgHeight);
            } else {
                renderServerList(context, localServers, mouseX, mouseY, contentYStart, panelHeight, panelWidth, delta);
            }
        } else {
            RemoteHostInfo hostInfo = remoteHosts.get(activeTabIndex - 1);
            if (hostInfo.isConnecting) {
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
                drawBufferedImage(context, currentFrame, centerX, centerY, imgWidth, imgHeight);
            } else if (hostInfo.connectionError != null) {
                drawCenteredString(context, "Error: " + hostInfo.connectionError, this.width / 2, (contentYStart + panelHeight) / 2, 0xFFFF5555);
            } else if (!hostInfo.isConnected) {
                drawCenteredString(context, "Not connected.", this.width / 2, (contentYStart + panelHeight) / 2, 0xFFFF5555);
            } else {
                renderServerList(context, hostInfo.servers, mouseX, mouseY, contentYStart, panelHeight, panelWidth, delta);
            }
        }
        if (serverTypePopupActive) {
            serverTypePopupX = (this.width - serverTypePopupWidth) / 2;
            serverTypePopupY = (this.height - serverTypePopupHeight) / 2;
            context.fill(serverTypePopupX, serverTypePopupY, serverTypePopupX + serverTypePopupWidth, serverTypePopupY + serverTypePopupHeight, baseColor);
            drawInnerBorder(context, serverTypePopupX, serverTypePopupY, serverTypePopupWidth, serverTypePopupHeight, borderColor);
            String stTitle = "Select Action";
            int stTitleW = minecraftClient.textRenderer.getWidth(stTitle);
            int stTitleX = serverTypePopupX + (serverTypePopupWidth - stTitleW) / 2;
            int stTitleY = serverTypePopupY + 5;
            context.drawText(minecraftClient.textRenderer, Text.literal(stTitle), stTitleX, stTitleY, textColor, false);
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
            serverPopupX = (this.width - serverPopupWidth) / 2;
            serverPopupY = (this.height - serverPopupHeight) / 2;
            context.fill(serverPopupX, serverPopupY, serverPopupX + serverPopupWidth, serverPopupY + serverPopupHeight, baseColor);
            drawInnerBorder(context, serverPopupX, serverPopupY, serverPopupWidth, serverPopupHeight, borderColor);
            if (deletingServer) {
                renderDeletePopup(context, mouseX, mouseY);
            } else {
                renderServerPopup(context, mouseX, mouseY);
            }
        }
        if (remoteHostPopupActive) {
            int px = (this.width - remoteHostPopupW) / 2;
            int py = (this.height - remoteHostPopupH) / 2;
            context.fill(px, py, px + remoteHostPopupW, py + remoteHostPopupH, baseColor);
            drawInnerBorder(context, px, py, remoteHostPopupW, remoteHostPopupH, borderColor);
            int labelY = py + 5;
            context.drawText(minecraftClient.textRenderer, Text.literal("Host Name:"), px + 5, labelY, textColor, false);
            int nameBoxY = labelY + 10;
            context.fill(px + 5, nameBoxY, px + remoteHostPopupW - 5, nameBoxY + 12, remoteHostActiveField == RemoteHostField.NAME ? 0xFF444466 : 0xFF333333);
            String nh = remoteHostNameBuffer.toString();
            nh = trimTextToWidthWithEllipsis(nh, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(nh), px + 8, nameBoxY + 2, textColor, false);
            int userLabelY = nameBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("User Name:"), px + 5, userLabelY, textColor, false);
            int userBoxY = userLabelY + 10;
            context.fill(px + 5, userBoxY, px + remoteHostPopupW - 5, userBoxY + 12, remoteHostActiveField == RemoteHostField.USER ? 0xFF444466 : 0xFF333333);
            String ub = remoteHostUserBuffer.toString();
            ub = trimTextToWidthWithEllipsis(ub, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ub), px + 8, userBoxY + 2, textColor, false);
            int ipLabelY = userBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("IP | Domain:"), px + 5, ipLabelY, textColor, false);
            int ipBoxY = ipLabelY + 10;
            context.fill(px + 5, ipBoxY, px + remoteHostPopupW - 5, ipBoxY + 12, remoteHostActiveField == RemoteHostField.IP ? 0xFF444466 : 0xFF333333);
            String ih = remoteHostIPBuffer.toString();
            ih = trimTextToWidthWithEllipsis(ih, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ih), px + 8, ipBoxY + 2, textColor, false);
            int portLabelY = ipBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("Port:"), px + 5, portLabelY, textColor, false);
            int portBoxY = portLabelY + 10;
            context.fill(px + 5, portBoxY, px + remoteHostPopupW - 5, portBoxY + 12, remoteHostActiveField == RemoteHostField.PORT ? 0xFF444466 : 0xFF333333);
            String ph = remoteHostPortBuffer.toString();
            ph = trimTextToWidthWithEllipsis(ph, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(ph), px + 8, portBoxY + 2, textColor, false);
            int passLabelY = portBoxY + 25;
            context.drawText(minecraftClient.textRenderer, Text.literal("Password:"), px + 5, passLabelY, textColor, false);
            int passBoxY = passLabelY + 10;
            context.fill(px + 5, passBoxY, px + remoteHostPopupW - 5, passBoxY + 12, remoteHostActiveField == RemoteHostField.PASSWORD ? 0xFF444466 : 0xFF333333);
            String mask = "";
            for (int i = 0; i < remoteHostPasswordBuffer.length(); i++) mask += "*";
            mask = trimTextToWidthWithEllipsis(mask, remoteHostPopupW - 12);
            context.drawText(minecraftClient.textRenderer, Text.literal(mask), px + 8, passBoxY + 2, textColor, false);
            int confirmButtonY = passBoxY + 33;
            String createText = isEditingHost ? "Save" : "Test & Add";
            int cw = minecraftClient.textRenderer.getWidth(createText) + 10;
            int confirmX = px + 5;
            boolean hoverConfirm = mouseX >= confirmX && mouseX <= confirmX + cw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawHeaderButton(context, confirmX, confirmButtonY, createText, minecraftClient, hoverConfirm, true, textColor, elementSelectedBorder);
            String cancelText = "Cancel";
            int cancW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
            int cancX = px + remoteHostPopupW - (cancW + 5);
            boolean hoverCancel = mouseX >= cancX && mouseX <= cancX + cancW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawHeaderButton(context, cancX, confirmButtonY, cancelText, minecraftClient, hoverCancel, true, textColor, redVeryBright);
            if (isEditingHost) {
                String deleteText = "Delete";
                int delW = minecraftClient.textRenderer.getWidth(deleteText) + 10;
                int delX = px + (remoteHostPopupW - delW) / 2;
                boolean hoverDelete = mouseX >= delX && mouseX <= delX + delW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
                drawHeaderButton(context, delX, confirmButtonY, deleteText, minecraftClient, hoverDelete, true, deleteColor, deleteHoverColor);
            }
            if (remoteHostCreationWarning) {
                String warning = isEditingHost ? "Failed to save changes" : "Invalid or Connection Failed";
                int ww = minecraftClient.textRenderer.getWidth(warning);
                context.drawText(minecraftClient.textRenderer, Text.literal(warning), px + (remoteHostPopupW - ww) / 2, passBoxY + 20, 0xFFFF4444, false);
            }
        }
    }

    private void renderTabs(DrawContext context, int mouseX, int mouseY) {
        int tabOffsetY = topBarHeight + 5;
        int availableTabWidth = this.width - 15 - 20;
        int tabStartX = 5;
        int tabY = tabOffsetY;
        int tabAreaHeight = TAB_HEIGHT;
        int tabPadding = 5;
        int totalTabsWidth = 0;
        List<String> allTabs = new ArrayList<>();
        allTabs.add("Local");
        for (RemoteHostInfo rh : remoteHosts) {
            allTabs.add(rh.name);
        }
        List<TabInfo> tabInfos = new ArrayList<>();
        for (String tabName : allTabs) {
            int tw = minecraftClient.textRenderer.getWidth(tabName);
            int paddingH = 10;
            int tabW = Math.max(tw + paddingH * 2, 45);
            tabInfos.add(new TabInfo(tabName, tabW));
        }
        for (TabInfo ti : tabInfos) totalTabsWidth += ti.width + tabPadding;
        if (totalTabsWidth < availableTabWidth) totalTabsWidth = availableTabWidth;
        tabScrollOffset = MathHelper.clamp(tabScrollOffset, 0, Math.max(0, totalTabsWidth - availableTabWidth));
        float renderX = tabStartX - tabScrollOffset;
        hoveredServerIndex = -1;
        for (int i = 0; i < tabInfos.size(); i++) {
            TabInfo ti = tabInfos.get(i);
            boolean tabHovered = mouseX >= renderX && mouseX <= renderX + ti.width && mouseY >= tabY && mouseY <= tabY + tabAreaHeight;
            if (tabHovered) hoveredServerIndex = i;
            int bgColor = (i == activeTabIndex) ? elementSelectedBg : (tabHovered ? 0xFF444444 : 0xFF333333);
            context.fill((int) renderX, tabY, (int) renderX + ti.width, tabY + tabAreaHeight, bgColor);
            drawInnerBorder(context, (int) renderX, tabY, ti.width, tabAreaHeight,(i == activeTabIndex) ? elementSelectedBorder : (tabHovered ? elementBorderHover : borderColor));
            context.fill((int) renderX, tabY + tabAreaHeight, (int) renderX + ti.width, tabY + tabAreaHeight + 2, (i == activeTabIndex) ? 0xFF0b0b0b : 0xFF000000);
            int tx = (int) renderX + (ti.width - minecraftClient.textRenderer.getWidth(ti.name)) / 2;
            int ty = tabY + (tabAreaHeight - minecraftClient.textRenderer.fontHeight) / 2;
            context.drawText(minecraftClient.textRenderer, Text.literal(ti.name), tx, ty, tabHovered ? elementSelectedBorder : textColor, false);
            renderX += ti.width + tabPadding;
        }
        int plusW = 18;
        remoteTabPlusHovered = mouseX >= renderX && mouseX <= renderX + plusW && mouseY >= tabY && mouseY <= tabY + tabAreaHeight;
        int plusBg = remoteTabPlusHovered ? highlightColor : elementBg;
        context.fill((int) renderX, tabY, (int) renderX + plusW, tabY + tabAreaHeight, plusBg);
        drawInnerBorder(context, (int) renderX, tabY, plusW, tabAreaHeight, remoteTabPlusHovered ? elementBorderHover : borderColor);
        String plus = "+";
        int pw = minecraftClient.textRenderer.getWidth(plus);
        int ptx = (int) renderX + (plusW - pw) / 2;
        int pty = tabY + (tabAreaHeight - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(plus), ptx, pty, remoteTabPlusHovered ? elementSelectedBorder : textColor, false);
    }

    private void renderServerList(DrawContext context, List<ServerInfo> currentServers, int mouseX, int mouseY, int contentYStart, int panelHeight, int panelWidth, float delta) {
        int listX = 10;
        int listY = contentYStart + 5;
        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;
        int visibleEntries = panelHeight / (entryHeight + 1);
        int startIndex = (int) Math.floor(smoothOffset / (entryHeight + 1));
        int endIndex = startIndex + visibleEntries + 2;
        if (endIndex > currentServers.size()) endIndex = currentServers.size();
        context.enableScissor(5, contentYStart, panelWidth, panelHeight);
        hoveredServerIndex = -1;
        for (int i = startIndex; i < endIndex; i++) {
            ServerInfo info = currentServers.get(i);
            int serverY = listY + (i * (entryHeight + 1)) - (int) smoothOffset;
            if (serverY + entryHeight < contentYStart || serverY > contentYStart + panelHeight) {
                continue;
            }
            boolean hovered = !(serverPopupActive || remoteHostPopupActive || serverTypePopupActive) && (mouseX >= listX && mouseX <= listX + (panelWidth - 10) && mouseY >= serverY && mouseY <= serverY + entryHeight);
            if (hovered) hoveredServerIndex = i;
            int bgColor = hovered ? highlightColor : elementBg;
            context.fill(listX, serverY, listX + panelWidth - 10, serverY + entryHeight, bgColor);
            int bdr = hovered ? elementBorderHover : borderColor;
            drawInnerBorder(context, listX, serverY, panelWidth - 10, entryHeight, bdr);
            String nameStr = info.name;
            context.drawText(minecraftClient.textRenderer, Text.literal(nameStr), listX + 5, serverY + 5, textColor, false);
            String pathStr = trimTextToWidthWithEllipsis(info.path, (panelWidth - 140));
            context.drawText(minecraftClient.textRenderer, Text.literal(pathStr), listX + 5, serverY + 15, dimTextColor, false);
            int editWidth = minecraftClient.textRenderer.getWidth("Edit") + 10;
            int editX = listX + panelWidth - 10 - editWidth - 5;
            int editY = serverY + (entryHeight - 20) / 2;
            boolean hoveredEdit = mouseX >= editX && mouseX <= editX + editWidth && mouseY >= editY && mouseY <= editY + 20;
            drawHeaderButton(context, editX, editY, "Edit", minecraftClient, hoveredEdit, true, textColor, elementSelectedBorder);
        }
        context.disableScissor();
        int bottomY = listY + (currentServers.size() * (entryHeight + 1)) - (int) smoothOffset + 10;
        int addWidth = minecraftClient.textRenderer.getWidth("Add New Server") + 10;
        int addX = listX + (panelWidth - 10 - addWidth) / 2;
        int addY = bottomY;
        if (bottomY + 20 < contentYStart + panelHeight) {
            boolean hoveredAdd = mouseX >= addX && mouseX <= addX + addWidth && mouseY >= addY && mouseY <= addY + 20;
            drawHeaderButton(context, addX, addY, "Add New Server", minecraftClient, hoveredAdd, true, textColor, elementSelectedBorder);
        }
        int maxScroll = Math.max(0, currentServers.size() * (entryHeight + 1) - panelHeight);
        if (smoothOffset > 0) {
            context.fillGradient(5, contentYStart, this.width - 5, contentYStart + 10, 0x80000000, 0x00000000);
        }
        if (smoothOffset < maxScroll) {
            context.fillGradient(5, contentYStart + panelHeight - 10, this.width - 5, contentYStart + panelHeight, 0x00000000, 0x80000000);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (remoteHostPopupActive) {
            return handleRemoteHostPopupClick(mouseX, mouseY, button);
        }
        if (serverTypePopupActive) {
            if (handleServerTypePopupClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        int closeButtonWidth = 60;
        int closeButtonX = this.width - closeButtonWidth - 10;
        int closeButtonY = 5;
        boolean inCloseButton = mouseX >= closeButtonX && mouseX <= closeButtonX + closeButtonWidth && mouseY >= closeButtonY && mouseY <= closeButtonY + buttonH;
        if (inCloseButton && button == 0) {
            this.close();
            return true;
        }
        if (!serverPopupActive && !remoteHostPopupActive) {
            int contentYStart = topBarHeight + tabHeight + 5 + verticalPadding;
            int panelWidth = this.width - 10;
            int listX = 10;
            int bottomY = 0;
            int visibleEntries = (this.height - contentYStart - 5) / (entryHeight + 1);
            if (activeTabIndex == 0) {
                bottomY = contentYStart + 5 + (localServers.size() * (entryHeight + 1)) - (int) smoothOffset + 10;
            } else {
                bottomY = contentYStart + 5 + (remoteHosts.get(activeTabIndex - 1).servers.size() * (entryHeight + 1)) - (int) smoothOffset + 10;
            }
            int addWidth = minecraftClient.textRenderer.getWidth("Add New Server") + 10;
            int addX = listX + (panelWidth - 10 - addWidth) / 2;
            int addY = bottomY;
            if (button == 0 && mouseX >= addX && mouseX <= addX + addWidth && mouseY >= addY && mouseY <= addY + 20) {
                serverTypePopupActive = true;
                return true;
            }
        }
        List<ServerInfo> currentServers = getCurrentServers();
        if (!serverPopupActive) {
            handleServerListClick(mouseX, mouseY, button, currentServers);
        }
        if (serverPopupActive) {
            handleServerPopupClick(mouseX, mouseY, button, currentServers);
        }
        if (button == 0 || button == 1) { // Handle left and right clicks
            int plusW = 18;
            int tabOffsetY = topBarHeight + 5;
            int tabY = tabOffsetY;
            int usedX = 5;
            List<String> allTabs = getAllTabNames();
            for (int i = 0; i < allTabs.size(); i++) {
                String tabName = allTabs.get(i);
                int tw = minecraftClient.textRenderer.getWidth(tabName);
                int paddingH = 10;
                int tabW = Math.max(tw + paddingH * 2, 45);
                boolean inTab = mouseX >= usedX && mouseX <= usedX + tabW && mouseY >= tabY && mouseY <= tabY + this.tabHeight;
                if (inTab) {
                    if (button == 0) {
                        activeTabIndex = i;
                        if (i > 0) {
                            RemoteHostInfo host = remoteHosts.get(i - 1);
                            if (!host.isConnected && !host.isConnecting) {
                                connectRemoteHostAsync(host);
                            }
                        }
                    } else if (button == 1) {
                        if (i > 0) {
                            RemoteHostInfo host = remoteHosts.get(i - 1);
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
                    }
                    return true;
                }
                usedX += tabW + 5;
            }
            boolean inPlus = mouseX >= usedX && mouseX <= usedX + plusW && mouseY >= tabY && mouseY <= tabY + this.tabHeight;
            if (inPlus) {
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
        int nameBoxY = py + 5 + 10;
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
                creatingServer = true;
                editingServer = false;
                deletingServer = false;
                editingServerIndex = -1;
                serverPopupActive = true;
                serverNameBuffer.setLength(0);
                serverVersionBuffer.setLength(0);
                serverNameBuffer.append("MyServer");
                selectedTypeIndex = 0;
                nameFieldFocused = true;
                versionFieldFocused = false;
                serverCreationWarning = false;
                serverNameCursorPos = serverNameBuffer.length();
                serverVersionCursorPos = 0;
                return true;
            }
            if (isInsideOptionBox(mouseX, mouseY, option2, serverTypePopupX, option2Y)) {
                serverTypePopupActive = false;
                importingServer = true;
                openImportFileExplorer();
                return true;
            }
            if (isInsideOptionBox(mouseX, mouseY, option3, serverTypePopupX, option3Y)) {
                serverTypePopupActive = false;
                modpackInstallation = true;
                openModpackInstallation();
                return true;
            }
        }
        return false;
    }

    private void handleServerListClick(double mouseX, double mouseY, int button, List<ServerInfo> currentServers) {
        int contentYStart = topBarHeight + tabHeight + 5 + verticalPadding;
        int panelHeight = this.height - contentYStart - 5;
        int panelWidth = this.width - 10;
        int listY = contentYStart + 5;
        if (mouseY < contentYStart || mouseY > contentYStart + panelHeight) {
            return;
        }
        int relativeMouseY = (int) (mouseY - listY + smoothOffset);
        int clickedIndex = relativeMouseY / (entryHeight + 1);
        if (clickedIndex >= 0 && clickedIndex < currentServers.size()) {
            int serverY = listY + (clickedIndex * (entryHeight + 1)) - (int) smoothOffset;
            if (serverY + entryHeight < contentYStart || serverY > contentYStart + panelHeight) {
                return;
            }
            ServerInfo info = currentServers.get(clickedIndex);
            int editWidth = minecraftClient.textRenderer.getWidth("Edit") + 10;
            int editX = 10 + panelWidth - 10 - editWidth - 5;
            int editY = serverY + (entryHeight - 20) / 2;
            boolean inEdit = mouseX >= editX && mouseX <= editX + editWidth && mouseY >= editY && mouseY <= editY + 20;
            if (inEdit && button == 0) {
                creatingServer = false;
                editingServer = true;
                deletingServer = false;
                editingServerIndex = clickedIndex;
                serverPopupActive = true;
                serverNameBuffer.setLength(0);
                serverNameBuffer.append(info.name);
                serverVersionBuffer.setLength(0);
                serverVersionBuffer.append(info.version);
                selectedTypeIndex = serverTypes.indexOf(info.type);
                if (selectedTypeIndex < 0) selectedTypeIndex = 0;
                nameFieldFocused = false;
                versionFieldFocused = false;
                serverCreationWarning = false;
                serverNameCursorPos = serverNameBuffer.length();
                serverVersionCursorPos = serverVersionBuffer.length();
                return;
            }
            if (button == 0 && !inEdit) {
                openServerScreen(clickedIndex, currentServers);
            }
        }
    }

    private void handleServerPopupClick(double mouseX, double mouseY, int button, List<ServerInfo> currentServers) {
        if (deletingServer) {
            int confirmButtonY = serverPopupY + serverPopupHeight - 30;
            String yesText = "Delete";
            int yesW = minecraftClient.textRenderer.getWidth(yesText) + 10;
            int yesX = serverPopupX + 5;
            int cancelButtonX = serverPopupX + serverPopupWidth - (minecraftClient.textRenderer.getWidth("Cancel") + 10 + 5);
            if (mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
                if (mouseX >= yesX && mouseX <= yesX + yesW && button == 0) {
                    if (editingServerIndex >= 0 && editingServerIndex < currentServers.size()) {
                        if (currentServers.get(editingServerIndex).isRemote) {
                            try {
                                RemoteHostInfo rh = currentServers.get(editingServerIndex).remoteHost;
                                if (rh != null && currentServers.get(editingServerIndex).remoteSSHManager != null) {
                                    currentServers.get(editingServerIndex).remoteSSHManager.deleteRemoteDirectory(currentServers.get(editingServerIndex).path);
                                }
                            } catch (Exception ignored) {}
                            currentServers.remove(editingServerIndex);
                        } else {
                            Path folderPath = Paths.get(currentServers.get(editingServerIndex).path);
                            try {
                                if (Files.exists(folderPath)) {
                                    Files.walk(folderPath).sorted(Comparator.reverseOrder()).forEach(p -> {
                                        try {
                                            Files.delete(p);
                                        } catch (IOException ignored) {}
                                    });
                                }
                            } catch (IOException ignored) {}
                            currentServers.remove(editingServerIndex);
                        }
                        saveServers();
                        saveRemoteHosts();
                    }
                    closePopup();
                    return;
                }
                if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + (minecraftClient.textRenderer.getWidth("Cancel") + 10) && button == 0) {
                    closePopup();
                    return;
                }
            }
            return;
        }
        int confirmButtonY = serverPopupY + serverPopupHeight - 30;
        String okText = editingServer ? "Save" : "Create";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmButtonX = serverPopupX + 5;
        int cancelButtonX = serverPopupX + serverPopupWidth - (minecraftClient.textRenderer.getWidth("Cancel") + 10 + 5);
        if (mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
            if (mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && button == 0) {
                if (serverNameBuffer.toString().trim().isEmpty()) {
                    serverCreationWarning = true;
                    return;
                }
                serverCreationWarning = false;
                createOrSaveServer();
                return;
            }
            if (editingServer) {
                String deleteText = "Delete Server";
                int dw = minecraftClient.textRenderer.getWidth(deleteText) + 10;
                int deleteX = serverPopupX + (serverPopupWidth - dw) / 2;
                if (mouseX >= deleteX && mouseX <= deleteX + dw && button == 0) {
                    deletingServer = true;
                    return;
                }
            }
            if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + (minecraftClient.textRenderer.getWidth("Cancel") + 10) && button == 0) {
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
        int typeBoxY = serverPopupY + 30 + 35;
        int arrowLeftX = serverPopupX + 5 + 150 + 5;
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
            info.terminal = new ServerTerminalInstance(minecraftClient, null, UUID.randomUUID(), info);
            info.isRunning = false;
        }
        minecraftClient.setScreen(new MultiTerminalScreen(minecraftClient, remotelyClient, info));
    }

    private void closePopup() {
        serverPopupActive = false;
        creatingServer = false;
        editingServer = false;
        deletingServer = false;
        editingServerIndex = -1;
        serverNameBuffer.setLength(0);
        serverVersionBuffer.setLength(0);
        serverNameCursorPos = 0;
        serverVersionCursorPos = 0;
        serverCreationWarning = false;
        importingServer = false;
        modpackInstallation = false;
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
        String path;
        if (activeTabIndex == 0) {
            path = "C:/remotely/servers/" + name;
        } else {
            RemoteHostInfo remoteHost = remoteHosts.get(activeTabIndex - 1);
            String user = remoteHost.getUser();
            String homeDir = user.equals("root") ? "/root" : "/home/" + user;
            path = homeDir + "/remotely/servers/" + name;
        }
        Path serverJarPath = Paths.get(path, "server.jar");
        if (editingServer && editingServerIndex >= 0 && editingServerIndex < currentServers.size()) {
            ServerInfo s = currentServers.get(editingServerIndex);
            s.name = name;
            s.path = path;
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
        } else {
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
            minecraftClient.setScreen(new PluginModManagerScreen(minecraftClient, this, new ServerInfo("modpack")));
        } catch (Exception ignored) {}
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
        context.drawText(minecraftClient.textRenderer, Text.literal(visible), x, y, textColor, false);
        if (drawCursor) {
            int cursorPosVisible = Math.min(cursorPos - charStart, visible.length());
            if (cursorPosVisible < 0) cursorPosVisible = 0;
            int cX = x + minecraftClient.textRenderer.getWidth(visible.substring(0, Math.min(cursorPosVisible, visible.length())));
            context.fill(cX, y - 1, cX + 1, y + minecraftClient.textRenderer.fontHeight, textColor);
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

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x+w, y+1, c);
        context.fill(x, y+h-1, x+w, y+h, c);
        context.fill(x, y, x+1, y+h, c);
        context.fill(x+w-1, y, x+w, y+h, c);
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
                            addServer(folderName, normalizedPath.toString());
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

    private void addServer(String name, String path) {
        ServerInfo newServer = new ServerInfo(path);
        newServer.name = name;
        newServer.path = path;
        newServer.type = "imported";
        newServer.version = "unknown";
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
            String bo = entry.substring(colon+1).trim();
            bo = bo.replace(",", "").replace("}", "").trim();
            return bo;
        }
        int quote2 = entry.indexOf("\"", quote1 + 1);
        if (quote2 < 0) return "";
        return entry.substring(quote1 + 1, quote2);
    }

    private void renderDeletePopup(DrawContext context, int mouseX, int mouseY) {
        int labelY = serverPopupY + 10;
        String warn = "Are you sure you want to delete this server?";
        int ww = minecraftClient.textRenderer.getWidth(warn);
        context.drawText(minecraftClient.textRenderer, Text.literal(warn), serverPopupX + (serverPopupWidth - ww) / 2, labelY, 0xFFFF4444, false);
        String yesText = "Delete";
        int yesW = minecraftClient.textRenderer.getWidth(yesText) + 10;
        int confirmButtonX = serverPopupX + 5;
        int confirmButtonY = serverPopupY + serverPopupHeight - 30;
        boolean yesHover = mouseX >= confirmButtonX && mouseX <= confirmButtonX + yesW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHeaderButton(context, confirmButtonX, confirmButtonY, yesText, minecraftClient, yesHover, true, textColor, elementSelectedBorder);
        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = serverPopupX + serverPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHeaderButton(context, cancelButtonX, confirmButtonY, cancelText, minecraftClient, cancelHover, true, textColor, redVeryBright);
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
        int bg = hovered ? highlightColor : elementBg;
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, bg);
        drawInnerBorder(context, boxX, boxY, boxW, boxH, hovered ? elementBorderHover : borderColor);
        int tw = minecraftClient.textRenderer.getWidth(text);
        int tx = boxX + (boxW - tw) / 2;
        int ty = boxY + (boxH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(text), tx, ty, textColor, false);
    }

    private void renderServerPopup(DrawContext context, int mouseX, int mouseY) {
        int nameLabelY = serverPopupY + 10;
        trimAndDrawText(context, "Server Name:", serverPopupX + 5, nameLabelY, serverPopupWidth - 10, textColor);
        int nameBoxY = nameLabelY + 12;
        int nameBoxHeight = 12;
        context.fill(serverPopupX + 5, nameBoxY, serverPopupX + serverPopupWidth - 5, nameBoxY + nameBoxHeight, nameFieldFocused ? 0xFF444466 : 0xFF333333);
        drawTextField(context, serverNameBuffer, serverNameCursorPos, serverNameScrollOffset, serverPopupX + 8, nameBoxY + 2, serverPopupWidth - 10, nameFieldFocused && serverCursorVisible);
        int typeLabelY = nameBoxY + nameBoxHeight + 15;
        trimAndDrawText(context, "Server Type:", serverPopupX + 5, typeLabelY, serverPopupWidth - 10, textColor);
        int typeBoxY = typeLabelY + 15;
        int boxWidth = 150;
        context.fill(serverPopupX + 5, typeBoxY, serverPopupX + 5 + boxWidth, typeBoxY + 12, 0xFF333333);
        String st = serverTypes.get(selectedTypeIndex);
        st = trimTextToWidthWithEllipsis(st, boxWidth - 2);
        context.drawText(minecraftClient.textRenderer, Text.literal(st), serverPopupX + 8, typeBoxY + 2, textColor, false);
        int arrowLeftX = serverPopupX + 5 + boxWidth + 5;
        context.fill(arrowLeftX, typeBoxY, arrowLeftX + 12, typeBoxY + 12, 0xFF555555);
        context.drawText(minecraftClient.textRenderer, Text.literal("<"), arrowLeftX + 4, typeBoxY + 2, textColor, false);
        int arrowRightX = arrowLeftX + 12 + 5;
        context.fill(arrowRightX, typeBoxY, arrowRightX + 12, typeBoxY + 12, 0xFF555555);
        context.drawText(minecraftClient.textRenderer, Text.literal(">"), arrowRightX + 3, typeBoxY + 2, textColor, false);
        int versionLabelY = typeBoxY + 20;
        trimAndDrawText(context, "Minecraft Version:", serverPopupX + 5, versionLabelY, serverPopupWidth - 10, textColor);
        int versionBoxY = versionLabelY + 12;
        context.fill(serverPopupX + 5, versionBoxY, serverPopupX + serverPopupWidth - 5, versionBoxY + 12, versionFieldFocused ? 0xFF444466 : 0xFF333333);
        drawTextField(context, serverVersionBuffer, serverVersionCursorPos, serverVersionScrollOffset, serverPopupX + 8, versionBoxY + 2, serverPopupWidth - 10, versionFieldFocused && serverCursorVisible);
        String okText = editingServer ? "Save" : "Create";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmButtonX = serverPopupX + 5;
        int confirmButtonY = serverPopupY + serverPopupHeight - 22;
        boolean okHover = mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHeaderButton(context, confirmButtonX, confirmButtonY, okText, minecraftClient, okHover, true, textColor, elementSelectedBorder);
        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = serverPopupX + serverPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHeaderButton(context, cancelButtonX, confirmButtonY, cancelText, minecraftClient, cancelHover, true, textColor, redVeryBright);
        if (editingServer) {
            String deleteText = "Delete Server";
            int dw = minecraftClient.textRenderer.getWidth(deleteText) + 10;
            int deleteX = serverPopupX + (serverPopupWidth - dw) / 2;
            boolean delHover = mouseX >= deleteX && mouseX <= deleteX + dw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawHeaderButton(context, deleteX, confirmButtonY, deleteText, minecraftClient, delHover, true, Render.deleteColor, Render.deleteHoverColor);
        }
        if (serverCreationWarning) {
            String warning = "Name cannot be empty";
            int ww = minecraftClient.textRenderer.getWidth(warning);
            trimAndDrawText(context, warning, serverPopupX + (serverPopupWidth - ww) / 2, serverPopupY + serverPopupHeight - 45, serverPopupWidth - 10, 0xFFFF0000);
        }
    }

    private enum RemoteHostField {
        NONE, NAME, USER, IP, PORT, PASSWORD
    }

    private void drawCenteredString(DrawContext context, String text, int centerX, int centerY, int color) {
        int w = minecraftClient.textRenderer.getWidth(text);
        int x = centerX - (w / 2);
        context.drawText(minecraftClient.textRenderer, Text.literal(text), x, centerY, color, false);
    }
}
