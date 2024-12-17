package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.RemotelyClient;
import redxax.oxy.ServerTerminalInstance;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ServerManagerScreen extends Screen {

    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    private final List<ServerInfo> servers;

    private boolean serverPopupActive;
    private boolean creatingServer;
    private boolean editingServer;
    private boolean deletingServer;
    private boolean serverCreationWarning;
    private int editingServerIndex = -1;
    private int serverCreationStep = 0;
    private int serverPopupX;
    private int serverPopupY;
    private final int serverPopupWidth = 350;
    private final int serverPopupHeight = 200;
    private final StringBuilder serverNameBuffer = new StringBuilder();
    private final StringBuilder serverPathBuffer = new StringBuilder();
    private final StringBuilder serverVersionBuffer = new StringBuilder();
    private String selectedServerType = "paper";
    private final List<String> serverTypes = Arrays.asList("paper","spigot","vanilla","fabric","forge","newforge");
    private int selectedTypeIndex = 0;
    private long serverLastBlinkTime = 0;
    private boolean serverCursorVisible = true;
    private long serverLastInputTime = 0;
    private int serverNameCursorPos = 0;
    private int serverPathCursorPos = 0;
    private int serverVersionCursorPos = 0;
    private int serverNameScrollOffset = 0;
    private int serverPathScrollOffset = 0;
    private int serverVersionScrollOffset = 0;
    private int hoveredServerIndex = -1;
    private boolean plusButtonHovered = false;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private int dimTextColor = 0xFFBBBBBB;
    private int tabHeight = 25;
    private float tabScrollOffset = 0;
    private int tabPadding = 5;
    private int verticalPadding = 2;
    private boolean nameFieldFocused = true;
    private boolean pathFieldFocused = false;
    private boolean versionFieldFocused = false;

    public ServerManagerScreen(MinecraftClient minecraftClient, RemotelyClient remotelyClient, List<ServerInfo> servers) {
        super(Text.literal("Server Setup"));
        this.minecraftClient = minecraftClient;
        this.remotelyClient = remotelyClient;
        this.servers = servers;
    }

    @Override
    protected void init() {
        super.init();
        if (servers.isEmpty()) {
            loadSavedServers();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - serverLastBlinkTime > 500) {
            serverCursorVisible = !serverCursorVisible;
            serverLastBlinkTime = currentTime;
        }
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);

        int tabY = 5;
        context.drawText(minecraftClient.textRenderer, Text.literal("Servers"), 5, tabY + (tabHeight - minecraftClient.textRenderer.fontHeight)/2, textColor, false);

        int plusW = 20;
        float renderX = 60 - tabScrollOffset;
        plusButtonHovered = mouseX >= renderX && mouseX <= renderX + plusW && mouseY >= tabY && mouseY <= tabY + tabHeight;
        int plusBg = plusButtonHovered ? 0xFF666666 : 0xFF555555;
        context.fill((int) renderX, tabY, (int) renderX + plusW, tabY + tabHeight, plusBg);
        drawInnerBorder(context, (int) renderX, tabY, plusW, tabHeight, borderColor);
        String plus = "+";
        int pw = minecraftClient.textRenderer.getWidth(plus);
        int ptx = (int) renderX + (plusW - pw) / 2;
        int pty = tabY + (tabHeight - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(plus), ptx, pty, textColor, false);

        int contentYStart = tabY + tabHeight + verticalPadding;
        int panelHeight = this.height - contentYStart - 5;
        int panelWidth = this.width - 10;
        context.fill(5, contentYStart, 5 + panelWidth, contentYStart + panelHeight, lighterColor);
        drawInnerBorder(context, 5, contentYStart, panelWidth, panelHeight, borderColor);

        int yOffset = contentYStart + 5;
        hoveredServerIndex = -1;
        for (int i = 0; i < servers.size(); i++) {
            ServerInfo info = servers.get(i);
            int serverBoxHeight = 40;
            int serverX = 10;
            int serverY = yOffset;
            boolean hovered = (mouseX >= serverX && mouseX <= serverX + (panelWidth - 10) && mouseY >= serverY && mouseY <= serverY + serverBoxHeight);
            if (hovered) hoveredServerIndex = i;
            int bgColor = hovered ? highlightColor : 0xFF444444;
            context.fill(serverX, serverY, serverX + panelWidth - 10, serverY + serverBoxHeight, bgColor);
            drawInnerBorder(context, serverX, serverY, panelWidth - 10, serverBoxHeight, borderColor);

            int nameY = serverY + 5;
            String displayName = trimTextToWidthWithEllipsis(info.name, panelWidth - 20);
            context.drawText(minecraftClient.textRenderer, Text.literal(displayName), serverX + 5, nameY, textColor, false);

            String pathStr = trimTextToWidthWithEllipsis(info.path, panelWidth - 20);
            context.drawText(minecraftClient.textRenderer, Text.literal(pathStr), serverX + 5, serverY + 20, dimTextColor, false);

            yOffset += serverBoxHeight + 5;
        }

        if (serverPopupActive) {
            serverPopupX = (this.width - serverPopupWidth) / 2;
            serverPopupY = (this.height - serverPopupHeight) / 2;
            context.fill(serverPopupX, serverPopupY, serverPopupX + serverPopupWidth, serverPopupY + serverPopupHeight, baseColor);
            drawInnerBorder(context, serverPopupX, serverPopupY, serverPopupWidth, serverPopupHeight, borderColor);
            if (deletingServer) {
                renderDeletePopup(context, mouseX, mouseY);
            } else if (serverCreationStep == 0) {
                renderNamePathPopup(context, mouseX, mouseY);
            } else if (serverCreationStep == 1) {
                renderTypeVersionPopup(context, mouseX, mouseY);
            } else {
                renderSummaryPopup(context, mouseX, mouseY);
            }
        }
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
        drawHoverableButton(context, confirmButtonX, confirmButtonY, yesText, yesHover, 0xFFFF0000);

        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = serverPopupX + serverPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, cancelButtonX, confirmButtonY, cancelText, cancelHover, textColor);
    }

    private void renderNamePathPopup(DrawContext context, int mouseX, int mouseY) {
        int labelY = serverPopupY + 10;
        trimAndDrawText(context, "Server Name:", serverPopupX + 5, labelY, serverPopupWidth - 10, textColor);
        int nameBoxY = labelY + 12;
        int nameBoxHeight = 12;
        context.fill(serverPopupX + 5, nameBoxY, serverPopupX + serverPopupWidth - 5, nameBoxY + nameBoxHeight, nameFieldFocused ? 0xFF444466 : 0xFF333333);
        String fullName = serverNameBuffer.toString();
        int wBeforeCursor = minecraftClient.textRenderer.getWidth(fullName.substring(0, Math.min(serverNameCursorPos, fullName.length())));
        if (wBeforeCursor < serverNameScrollOffset) serverNameScrollOffset = wBeforeCursor;
        int maxVisibleWidth = serverPopupWidth - 10;
        if (wBeforeCursor - serverNameScrollOffset > maxVisibleWidth - 6) serverNameScrollOffset = wBeforeCursor - (maxVisibleWidth - 6);
        if (serverNameScrollOffset < 0) serverNameScrollOffset = 0;
        int charStart = 0;
        while (charStart < fullName.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullName.substring(0, charStart));
            if (cw >= serverNameScrollOffset) break;
            charStart++;
        }
        int visibleEnd = charStart;
        while (visibleEnd <= fullName.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullName.substring(charStart, visibleEnd));
            if (cw > maxVisibleWidth - 6) break;
            visibleEnd++;
        }
        visibleEnd--;
        if (visibleEnd < charStart) visibleEnd = charStart;
        String visibleName = fullName.substring(charStart, visibleEnd);
        int nameTextX = serverPopupX + 8;
        int nameTextY = nameBoxY + 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(visibleName), nameTextX, nameTextY, textColor, false);
        if (nameFieldFocused && serverCursorVisible) {
            int cursorPosVisible = Math.min(serverNameCursorPos - charStart, visibleName.length());
            if (cursorPosVisible < 0) cursorPosVisible = 0;
            int cX = nameTextX + minecraftClient.textRenderer.getWidth(visibleName.substring(0, Math.min(cursorPosVisible, visibleName.length())));
            context.fill(cX, nameTextY - 1, cX + 1, nameTextY + minecraftClient.textRenderer.fontHeight, textColor);
        }

        int pathLabelY = nameBoxY + nameBoxHeight + 10;
        trimAndDrawText(context, "Server Path:", serverPopupX + 5, pathLabelY, serverPopupWidth - 10, textColor);
        int pathBoxY = pathLabelY + 12;
        int pathBoxHeight = 12;
        context.fill(serverPopupX + 5, pathBoxY, serverPopupX + serverPopupWidth - 5, pathBoxY + pathBoxHeight, pathFieldFocused ? 0xFF444466 : 0xFF333333);
        String fullPath = serverPathBuffer.toString();
        wBeforeCursor = minecraftClient.textRenderer.getWidth(fullPath.substring(0, Math.min(serverPathCursorPos, fullPath.length())));
        if (wBeforeCursor < serverPathScrollOffset) serverPathScrollOffset = wBeforeCursor;
        if (wBeforeCursor - serverPathScrollOffset > maxVisibleWidth - 6) serverPathScrollOffset = wBeforeCursor - (maxVisibleWidth - 6);
        if (serverPathScrollOffset < 0) serverPathScrollOffset = 0;
        charStart = 0;
        while (charStart < fullPath.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullPath.substring(0, charStart));
            if (cw >= serverPathScrollOffset) break;
            charStart++;
        }
        visibleEnd = charStart;
        while (visibleEnd <= fullPath.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullPath.substring(charStart, visibleEnd));
            if (cw > maxVisibleWidth - 6) break;
            visibleEnd++;
        }
        visibleEnd--;
        if (visibleEnd < charStart) visibleEnd = charStart;
        String visiblePath = fullPath.substring(charStart, visibleEnd);
        int pathTextX = serverPopupX + 8;
        int pathTextY = pathBoxY + 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(visiblePath), pathTextX, pathTextY, textColor, false);
        if (pathFieldFocused && serverCursorVisible) {
            int cursorPosVisible = Math.min(serverPathCursorPos - charStart, visiblePath.length());
            if (cursorPosVisible < 0) cursorPosVisible = 0;
            int cX = pathTextX + minecraftClient.textRenderer.getWidth(visiblePath.substring(0, Math.min(cursorPosVisible, visiblePath.length())));
            context.fill(cX, pathTextY - 1, cX + 1, pathTextY + minecraftClient.textRenderer.fontHeight, textColor);
        }

        String okText = "Next";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmButtonX = serverPopupX + 5;
        int confirmButtonY = serverPopupY + serverPopupHeight - 30;
        boolean okHover = mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, confirmButtonX, confirmButtonY, okText, okHover, textColor);

        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = serverPopupX + serverPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, cancelButtonX, confirmButtonY, cancelText, cancelHover, textColor);

        if (serverCreationWarning) {
            String warning = "Name/Path cannot be empty";
            int ww = minecraftClient.textRenderer.getWidth(warning);
            trimAndDrawText(context, warning, serverPopupX + (serverPopupWidth - ww) / 2, serverPopupY + serverPopupHeight - 45, serverPopupWidth - 10, 0xFFFF0000);
        }
    }

    private void renderTypeVersionPopup(DrawContext context, int mouseX, int mouseY) {
        int typeLabelY = serverPopupY + 10;
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
        String fullVersion = serverVersionBuffer.toString();
        int wBeforeCursor = minecraftClient.textRenderer.getWidth(fullVersion.substring(0, Math.min(serverVersionCursorPos, fullVersion.length())));
        if (wBeforeCursor < serverVersionScrollOffset) serverVersionScrollOffset = wBeforeCursor;
        int maxVisibleWidth = serverPopupWidth - 10;
        if (wBeforeCursor - serverVersionScrollOffset > maxVisibleWidth - 6) serverVersionScrollOffset = wBeforeCursor - (maxVisibleWidth - 6);
        if (serverVersionScrollOffset < 0) serverVersionScrollOffset = 0;
        int charStart = 0;
        while (charStart < fullVersion.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullVersion.substring(0, charStart));
            if (cw >= serverVersionScrollOffset) break;
            charStart++;
        }
        int visibleEnd = charStart;
        while (visibleEnd <= fullVersion.length()) {
            int cw = minecraftClient.textRenderer.getWidth(fullVersion.substring(charStart, visibleEnd));
            if (cw > maxVisibleWidth - 6) break;
            visibleEnd++;
        }
        visibleEnd--;
        if (visibleEnd < charStart) visibleEnd = charStart;
        String visibleVer = fullVersion.substring(charStart, visibleEnd);
        int verTextX = serverPopupX + 8;
        int verTextY = versionBoxY + 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(visibleVer), verTextX, verTextY, textColor, false);
        if (versionFieldFocused && serverCursorVisible) {
            int cursorPosVisible = Math.min(serverVersionCursorPos - charStart, visibleVer.length());
            if (cursorPosVisible < 0) cursorPosVisible = 0;
            int cX = verTextX + minecraftClient.textRenderer.getWidth(visibleVer.substring(0, Math.min(cursorPosVisible, visibleVer.length())));
            context.fill(cX, verTextY - 1, cX + 1, verTextY + minecraftClient.textRenderer.fontHeight, textColor);
        }

        String okText = "Next";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmButtonX = serverPopupX + 5;
        int confirmButtonY = serverPopupY + serverPopupHeight - 30;
        boolean okHover = mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, confirmButtonX, confirmButtonY, okText, okHover, textColor);

        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = serverPopupX + serverPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, cancelButtonX, confirmButtonY, cancelText, cancelHover, textColor);
    }

    private void renderSummaryPopup(DrawContext context, int mouseX, int mouseY) {
        int summaryY = serverPopupY + 10;
        String summary1 = "Create/Edit server with:";
        trimAndDrawText(context, summary1, serverPopupX + 5, summaryY, serverPopupWidth - 10, textColor);
        summaryY += 15;
        String summary2 = "Name: " + serverNameBuffer.toString();
        trimAndDrawText(context, summary2, serverPopupX + 5, summaryY, serverPopupWidth - 10, textColor);
        summaryY += 12;
        String summary3 = "Path: " + serverPathBuffer.toString();
        trimAndDrawText(context, summary3, serverPopupX + 5, summaryY, serverPopupWidth - 10, textColor);
        summaryY += 12;
        String summary4 = "Type: " + selectedServerType;
        trimAndDrawText(context, summary4, serverPopupX + 5, summaryY, serverPopupWidth - 10, textColor);
        summaryY += 12;
        String summary5 = "Version: " + (!serverVersionBuffer.isEmpty() ? serverVersionBuffer.toString() : "latest");
        trimAndDrawText(context, summary5, serverPopupX + 5, summaryY, serverPopupWidth - 10, textColor);

        String okText = editingServer ? "Save" : "Create";
        int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
        int confirmButtonX = serverPopupX + 5;
        int confirmButtonY = serverPopupY + serverPopupHeight - 30;
        boolean okHover = mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, confirmButtonX, confirmButtonY, okText, okHover, textColor);

        String cancelText = "Cancel";
        int cancelW = minecraftClient.textRenderer.getWidth(cancelText) + 10;
        int cancelButtonX = serverPopupX + serverPopupWidth - (cancelW + 5);
        boolean cancelHover = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelW && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
        drawHoverableButton(context, cancelButtonX, confirmButtonY, cancelText, cancelHover, textColor);

        if (editingServer) {
            String deleteText = "Delete Server";
            int dw = minecraftClient.textRenderer.getWidth(deleteText) + 10;
            int deleteX = serverPopupX + (serverPopupWidth - dw) / 2;
            boolean delHover = mouseX >= deleteX && mouseX <= deleteX + dw && mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight;
            drawHoverableButton(context, deleteX, confirmButtonY, deleteText, delHover, 0xFFFF4444);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tabY = 5;
        float renderX = 60 - tabScrollOffset;
        int plusW = 20;
        if (mouseX >= renderX && mouseX <= renderX + plusW && mouseY >= tabY && mouseY <= tabY + tabHeight && button == 0) {
            creatingServer = true;
            editingServer = false;
            deletingServer = false;
            editingServerIndex = -1;
            serverPopupActive = true;
            serverCreationStep = 0;
            serverNameBuffer.setLength(0);
            serverPathBuffer.setLength(0);
            serverVersionBuffer.setLength(0);
            serverNameBuffer.append("NewServer");
            serverPathBuffer.append("C:/servers/NewServer");
            selectedTypeIndex = 0;
            nameFieldFocused = true;
            pathFieldFocused = false;
            versionFieldFocused = false;
            serverCreationWarning = false;
            serverNameCursorPos = serverNameBuffer.length();
            serverPathCursorPos = serverPathBuffer.length();
            serverVersionCursorPos = 0;
            return true;
        }
        int contentYStart = tabY + tabHeight + verticalPadding;
        int panelHeight = this.height - contentYStart - 5;
        int panelWidth = this.width - 10;
        int yOffset = contentYStart + 5;
        for (int i = 0; i < servers.size(); i++) {
            int serverBoxHeight = 40;
            int serverX = 10;
            int serverY = yOffset;
            boolean hovered = (mouseX >= serverX && mouseX <= serverX + (panelWidth - 10) && mouseY >= serverY && mouseY <= serverY + serverBoxHeight);
            if (hovered) {
                if (button == 1) {
                    editingServer = true;
                    creatingServer = false;
                    deletingServer = false;
                    editingServerIndex = i;
                    serverPopupActive = true;
                    serverCreationStep = 2;
                    ServerInfo s = servers.get(i);
                    serverNameBuffer.setLength(0);
                    serverNameBuffer.append(s.name);
                    serverPathBuffer.setLength(0);
                    serverPathBuffer.append(s.path);
                    serverVersionBuffer.setLength(0);
                    serverVersionBuffer.append(s.version);
                    selectedTypeIndex = serverTypes.indexOf(s.type);
                    if (selectedTypeIndex < 0) selectedTypeIndex = 0;
                    nameFieldFocused = false;
                    pathFieldFocused = false;
                    versionFieldFocused = false;
                    serverCreationWarning = false;
                    serverNameCursorPos = serverNameBuffer.length();
                    serverPathCursorPos = serverPathBuffer.length();
                    serverVersionCursorPos = serverVersionBuffer.length();
                    return true;
                }
                if (button == 0) {
                    openServerScreen(i);
                    return true;
                }
            }
            yOffset += serverBoxHeight + 5;
        }
        if (serverPopupActive) {
            if (deletingServer) {
                int confirmButtonY = serverPopupY + serverPopupHeight - 30;
                String yesText = "Delete";
                int yesW = minecraftClient.textRenderer.getWidth(yesText) + 10;
                int yesX = serverPopupX + 5;
                int cancelButtonX = serverPopupX + serverPopupWidth - (minecraftClient.textRenderer.getWidth("Cancel") + 10 + 5);
                if (mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
                    if (mouseX >= yesX && mouseX <= yesX + yesW && button == 0) {
                        if (editingServerIndex >= 0 && editingServerIndex < servers.size()) {
                            Path folderPath = Paths.get(servers.get(editingServerIndex).path);
                            try {
                                if (Files.exists(folderPath)) {
                                    Files.walk(folderPath).sorted(Comparator.reverseOrder()).forEach(p -> {
                                        try {
                                            Files.delete(p);
                                        } catch (IOException ignored) {}
                                    });
                                }
                            } catch (IOException ignored) {}
                            servers.remove(editingServerIndex);
                            saveServers();
                        }
                        closePopup();
                        return true;
                    }
                    if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + (minecraftClient.textRenderer.getWidth("Cancel") + 10) && button == 0) {
                        closePopup();
                        return true;
                    }
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }

            int confirmButtonY = serverPopupY + serverPopupHeight - 30;
            String okText;
            if (serverCreationStep == 0) okText = "Next";
            else if (serverCreationStep == 1) okText = "Next";
            else okText = editingServer ? "Save" : "Create";
            int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
            int confirmButtonX = serverPopupX + 5;
            int cancelButtonX = serverPopupX + serverPopupWidth - (minecraftClient.textRenderer.getWidth("Cancel") + 10 + 5);
            if (mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
                if (mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && button == 0) {
                    if (serverCreationStep == 0) {
                        if (serverNameBuffer.toString().trim().isEmpty() || serverPathBuffer.toString().trim().isEmpty()) {
                            serverCreationWarning = true;
                            return true;
                        }
                        serverCreationWarning = false;
                        serverCreationStep = 1;
                        return true;
                    } else if (serverCreationStep == 1) {
                        serverCreationStep = 2;
                        return true;
                    } else {
                        if (serverNameBuffer.toString().trim().isEmpty() || serverPathBuffer.toString().trim().isEmpty()) {
                            serverCreationWarning = true;
                            return true;
                        }
                        serverCreationWarning = false;
                        createOrSaveServer();
                        return true;
                    }
                }
                if (editingServer && serverCreationStep == 2) {
                    String deleteText = "Delete Server";
                    int dw = minecraftClient.textRenderer.getWidth(deleteText) + 10;
                    int deleteX = serverPopupX + (serverPopupWidth - dw) / 2;
                    if (mouseX >= deleteX && mouseX <= deleteX + dw && button == 0) {
                        deletingServer = true;
                        return true;
                    }
                }
                if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + (minecraftClient.textRenderer.getWidth("Cancel") + 10) && button == 0) {
                    closePopup();
                    return true;
                }
            }
            if (serverCreationStep == 0) {
                int nameBoxY = serverPopupY + 10 + 12;
                int nameBoxH = 12;
                int pathLabelY = nameBoxY + nameBoxH + 10;
                int pathBoxY = pathLabelY + 12;
                if (mouseX >= serverPopupX + 5 && mouseX <= serverPopupX + serverPopupWidth - 5 && mouseY >= nameBoxY && mouseY <= nameBoxY + nameBoxH && button == 0) {
                    nameFieldFocused = true;
                    pathFieldFocused = false;
                    versionFieldFocused = false;
                    return true;
                }
                if (mouseX >= serverPopupX + 5 && mouseX <= serverPopupX + serverPopupWidth - 5 && mouseY >= pathBoxY && mouseY <= pathBoxY + nameBoxH && button == 0) {
                    nameFieldFocused = false;
                    pathFieldFocused = true;
                    versionFieldFocused = false;
                    return true;
                }
            }
            if (serverCreationStep == 1) {
                int typeBoxY = serverPopupY + 10 + 15;
                int arrowLeftX = serverPopupX + 5 + 150 + 5;
                int arrowRightX = arrowLeftX + 12 + 5;
                if (mouseX >= arrowLeftX && mouseX <= arrowLeftX + 12 && mouseY >= typeBoxY && mouseY <= typeBoxY + 12 && button == 0) {
                    selectedTypeIndex = (selectedTypeIndex - 1 + serverTypes.size()) % serverTypes.size();
                    selectedServerType = serverTypes.get(selectedTypeIndex);
                    return true;
                }
                if (mouseX >= arrowRightX && mouseX <= arrowRightX + 12 && mouseY >= typeBoxY && mouseY <= typeBoxY + 12 && button == 0) {
                    selectedTypeIndex = (selectedTypeIndex + 1) % serverTypes.size();
                    selectedServerType = serverTypes.get(selectedTypeIndex);
                    return true;
                }
                int versionLabelY = typeBoxY + 20;
                int versionBoxY = versionLabelY + 12;
                int versionBoxH = 12;
                if (mouseX >= serverPopupX + 5 && mouseX <= serverPopupX + serverPopupWidth - 5 && mouseY >= versionBoxY && mouseY <= versionBoxY + versionBoxH && button == 0) {
                    nameFieldFocused = false;
                    pathFieldFocused = false;
                    versionFieldFocused = true;
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!serverPopupActive) return super.keyPressed(keyCode, scanCode, modifiers);
        if (serverCreationStep == 0) {
            if (nameFieldFocused) {
                if (handleTypingKey(keyCode, serverNameBuffer, true)) return true;
            } else if (pathFieldFocused) {
                if (handleTypingKey(keyCode, serverPathBuffer, false)) return true;
            }
        } else if (serverCreationStep == 1) {
            if (versionFieldFocused) {
                if (handleTypingKey(keyCode, serverVersionBuffer, false)) return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!serverPopupActive) return super.charTyped(chr, modifiers);
        if (serverCreationStep == 0) {
            if (nameFieldFocused) {
                insertChar(serverNameBuffer, chr, true);
                return true;
            } else if (pathFieldFocused) {
                insertChar(serverPathBuffer, chr, false);
                return true;
            }
        } else if (serverCreationStep == 1) {
            if (versionFieldFocused) {
                insertChar(serverVersionBuffer, chr, false);
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
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
                if (buffer == serverPathBuffer) {
                    if (keyCode == 259 && serverPathCursorPos > 0) {
                        buffer.deleteCharAt(serverPathCursorPos - 1);
                        serverPathCursorPos--;
                    } else if (keyCode == 261 && serverPathCursorPos < buffer.length()) {
                        buffer.deleteCharAt(serverPathCursorPos);
                    }
                } else {
                    if (keyCode == 259 && serverVersionCursorPos > 0) {
                        buffer.deleteCharAt(serverVersionCursorPos - 1);
                        serverVersionCursorPos--;
                    } else if (keyCode == 261 && serverVersionCursorPos < buffer.length()) {
                        buffer.deleteCharAt(serverVersionCursorPos);
                    }
                }
            }
            return true;
        } else if (keyCode == 263) {
            if (isNameField) {
                if (serverNameCursorPos > 0) serverNameCursorPos--;
            } else {
                if (buffer == serverPathBuffer) {
                    if (serverPathCursorPos > 0) serverPathCursorPos--;
                } else {
                    if (serverVersionCursorPos > 0) serverVersionCursorPos--;
                }
            }
            return true;
        } else if (keyCode == 262) {
            if (isNameField) {
                if (serverNameCursorPos < buffer.length()) serverNameCursorPos++;
            } else {
                if (buffer == serverPathBuffer) {
                    if (serverPathCursorPos < buffer.length()) serverPathCursorPos++;
                } else {
                    if (serverVersionCursorPos < buffer.length()) serverVersionCursorPos++;
                }
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
            if (buffer == serverPathBuffer) {
                buffer.insert(serverPathCursorPos, chr);
                serverPathCursorPos++;
            } else {
                buffer.insert(serverVersionCursorPos, chr);
                serverVersionCursorPos++;
            }
        }
    }

    private void openServerScreen(int index) {
        ServerInfo info = servers.get(index);
        if (info.terminal == null) {
            info.terminal = new ServerTerminalInstance(minecraftClient, null, UUID.randomUUID(), info);
            info.isRunning = false;
        }
        minecraftClient.setScreen(new ServerTerminalScreen(minecraftClient, info));
    }


    private void closePopup() {
        serverPopupActive = false;
        creatingServer = false;
        editingServer = false;
        deletingServer = false;
        editingServerIndex = -1;
        serverCreationStep = 0;
        serverNameBuffer.setLength(0);
        serverPathBuffer.setLength(0);
        serverVersionBuffer.setLength(0);
        serverNameCursorPos = 0;
        serverPathCursorPos = 0;
        serverVersionCursorPos = 0;
        serverCreationWarning = false;
    }

    private void createOrSaveServer() {
        String name = serverNameBuffer.toString().trim();
        String path = serverPathBuffer.toString().trim();
        String ver = serverVersionBuffer.toString().trim().isEmpty() ? "latest" : serverVersionBuffer.toString().trim();
        selectedServerType = serverTypes.get(selectedTypeIndex);
        if (editingServer && editingServerIndex >= 0 && editingServerIndex < servers.size()) {
            ServerInfo s = servers.get(editingServerIndex);
            if (!s.isRunning && !path.equalsIgnoreCase(s.path)) {
                try {
                    Path oldDir = Paths.get(s.path);
                    Path newDir = Paths.get(path);
                    if (!Files.exists(newDir)) Files.createDirectories(newDir);
                    Files.walk(oldDir).forEach(p -> {
                        try {
                            Path dest = newDir.resolve(oldDir.relativize(p));
                            if (Files.isDirectory(p)) {
                                if (!Files.exists(dest)) Files.createDirectories(dest);
                            } else {
                                Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                                Files.delete(p);
                            }
                        } catch (IOException ignored) {}
                    });
                    Files.deleteIfExists(oldDir);
                } catch (IOException ignored) {}
            }
            s.name = name;
            s.path = path;
            s.type = selectedServerType;
            s.version = ver;
            saveServers();
        } else {
            ServerInfo newInfo = new ServerInfo();
            newInfo.name = name;
            newInfo.path = path;
            newInfo.type = selectedServerType;
            newInfo.version = ver;
            newInfo.isRunning = false;
            servers.add(newInfo);
            downloadServerJar(newInfo);
            saveServers();
        }
        closePopup();
    }

    private void downloadServerJar(ServerInfo serverInfo) {
        try {
            Path serverDir = Paths.get(serverInfo.path);
            if (!Files.exists(serverDir)) {
                Files.createDirectories(serverDir);
            }
            Path jarPath = serverDir.resolve("server.jar");
            if (Files.exists(jarPath)) {
                Files.delete(jarPath);
            }
            String mcVersion = serverInfo.version.equalsIgnoreCase("latest") ? "latest" : serverInfo.version;
            String downloadUrl = "";
            if (serverInfo.type.equalsIgnoreCase("paper")) {
                downloadUrl = getPaperDownloadUrl(mcVersion);
            } else if (serverInfo.type.equalsIgnoreCase("spigot")) {
                downloadUrl = "https://download.getbukkit.org/spigot/spigot-" + mcVersion + ".jar";
            } else if (serverInfo.type.equalsIgnoreCase("vanilla")) {
                downloadUrl = "https://launcher.mojang.com/v1/objects/0/0/server.jar";
            } else if (serverInfo.type.equalsIgnoreCase("fabric")) {
                downloadUrl = "https://meta.fabricmc.net/v2/versions/loader/" + mcVersion + "/latest/server/jar";
            } else if (serverInfo.type.equalsIgnoreCase("forge") || serverInfo.type.equalsIgnoreCase("newforge")) {
                downloadUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + mcVersion + "/forge-" + mcVersion + "-installer.jar";
            }
            if (!downloadUrl.isEmpty()) {
                Files.copy(new java.net.URL(downloadUrl).openStream(), jarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }

    private String getPaperDownloadUrl(String mcVersion) {
        try {
            if (mcVersion.equalsIgnoreCase("latest")) {
                String apiUrl = "https://api.papermc.io/v2/projects/paper";
                String data = new String(new java.net.URL(apiUrl).openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String[] lines = data.split("\"versions\":\\[");
                if (lines.length > 1) {
                    String[] parts = lines[1].split("]", 2);
                    String versions = parts[0];
                    String[] splitted = versions.split(",");
                    mcVersion = splitted[splitted.length - 1].replace("\"", "").trim();
                }
            }
            String buildsUrl = "https://api.papermc.io/v2/projects/paper/versions/" + mcVersion;
            String buildsData = new String(new java.net.URL(buildsUrl).openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String[] splitted2 = buildsData.split("\"builds\":\\[");
            if (splitted2.length > 1) {
                String[] parts = splitted2[1].split("]", 2);
                String builds = parts[0];
                String[] splittedBuilds = builds.split(",");
                String lastBuild = splittedBuilds[splittedBuilds.length - 1].trim();
                return "https://api.papermc.io/v2/projects/paper/versions/" + mcVersion + "/builds/" + lastBuild + "/downloads/paper-" + mcVersion + "-" + lastBuild + ".jar";
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void saveServers() {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "remotely", "servers");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve("servers.json");
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < servers.size(); i++) {
                ServerInfo info = servers.get(i);
                sb.append("  {\n");
                sb.append("    \"name\": \"").append(info.name).append("\",\n");
                sb.append("    \"path\": \"").append(info.path).append("\",\n");
                sb.append("    \"type\": \"").append(info.type).append("\",\n");
                sb.append("    \"version\": \"").append(info.version).append("\",\n");
                sb.append("    \"isRunning\": ").append(info.isRunning).append("\n");
                sb.append("  }");
                if (i < servers.size() - 1) sb.append(",");
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
            servers.addAll(loaded);
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
            ServerInfo info = new ServerInfo();
            info.name = name;
            info.path = path;
            info.type = type;
            info.version = version;
            info.isRunning = runVal.equalsIgnoreCase("true");
            list.add(info);
        }
        return list;
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

    private void drawHoverableButton(DrawContext context, int x, int y, String text, boolean hovered, int textColor) {
        int w = minecraftClient.textRenderer.getWidth(text) + 10;
        int h = 10 + minecraftClient.textRenderer.fontHeight;
        int bgColor = hovered ? 0xFF666666 : 0xFF555555;
        context.fill(x+1, y+1, x+w-1, y+h-1, bgColor);
        context.fill(x, y, x+w, y+1, 0xFFAAAAAA);
        context.fill(x, y, x+1, y+h, 0xFFAAAAAA);
        context.fill(x, y+h-1, x+w, y+h, 0xFF333333);
        context.fill(x+w-1, y, x+w, y+h, 0xFF333333);
        int tx = x + (w - minecraftClient.textRenderer.getWidth(text)) / 2;
        int ty = y + (h - minecraftClient.textRenderer.fontHeight) / 2;
        trimAndDrawText(context, text, tx, ty, w, textColor);
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

    private int findPreviousWord(String text, int pos) {
        if (pos <= 0) return 0;
        int idx = pos - 1;
        while (idx > 0 && Character.isWhitespace(text.charAt(idx))) idx--;
        while (idx > 0 && !Character.isWhitespace(text.charAt(idx))) idx--;
        if (idx > 0 && Character.isWhitespace(text.charAt(idx))) idx++;
        return idx;
    }

    @Override
    public void close() {
        super.close();
    }
}
