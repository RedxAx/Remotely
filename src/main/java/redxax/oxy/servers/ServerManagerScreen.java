package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.RemotelyClient;
import redxax.oxy.ServerTerminalInstance;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
    private int serverPopupX;
    private int serverPopupY;
    private final int serverPopupWidth = 350;
    private final int serverPopupHeight = 160;
    private final StringBuilder serverNameBuffer = new StringBuilder();
    private final StringBuilder serverVersionBuffer = new StringBuilder();
    private String selectedServerType = "paper";
    private final List<String> serverTypes = Arrays.asList("paper","spigot","vanilla","fabric","forge","neoforge","quilt");
    private int selectedTypeIndex = 0;
    private long serverLastBlinkTime = 0;
    private boolean serverCursorVisible = true;
    private int serverNameCursorPos = 0;
    private int serverVersionCursorPos = 0;
    private int serverNameScrollOffset = 0;
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
    private int verticalPadding = 2;
    private boolean nameFieldFocused = true;
    private boolean versionFieldFocused = false;

    private float smoothOffset = 0;
    private float targetOffset = 0;
    private float scrollSpeed = 0.2f;

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

        int listX = 10;
        int listY = contentYStart + 5;
        int entryHeight = 60;
        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;

        int visibleEntries = (panelHeight - 10) / entryHeight;
        int startIndex = (int)Math.floor(smoothOffset / entryHeight);
        int endIndex = startIndex + visibleEntries + 2;
        if (endIndex > servers.size()) endIndex = servers.size();

        context.enableScissor(10, contentYStart + 5, panelWidth - 10, panelHeight - 10);
        hoveredServerIndex = -1;
        for (int i = startIndex; i < endIndex; i++) {
            ServerInfo info = servers.get(i);
            int serverY = listY + (i * entryHeight) - (int)smoothOffset;
            boolean hovered = (mouseX >= listX && mouseX <= listX + (panelWidth - 10) && mouseY >= serverY && mouseY <= serverY + entryHeight);
            if (hovered) hoveredServerIndex = i;
            int bgColor = hovered ? highlightColor : 0xFF444444;
            context.fill(listX, serverY, listX + panelWidth - 10, serverY + entryHeight, bgColor);
            drawInnerBorder(context, listX, serverY, panelWidth - 10, entryHeight, borderColor);

            String stateStr = switch (info.state) {
                case RUNNING -> "Running";
                case STARTING -> "Starting";
                case STOPPED -> "Stopped";
                case CRASHED -> "Crashed";
                default -> "Unknown";
            };

            String nameLine = trimTextToWidthWithEllipsis(info.name + " ["+stateStr+"]", panelWidth - 80);
            context.drawText(minecraftClient.textRenderer, Text.literal(nameLine), listX + 5, serverY + 5, textColor, false);

            String stats = "Players: " + info.currentPlayers + "/" + info.maxPlayers + " | TPS: " + info.tps + " | Uptime: " + info.uptime;
            stats = trimTextToWidthWithEllipsis(stats, panelWidth - 80);
            context.drawText(minecraftClient.textRenderer, Text.literal(stats), listX + 5, serverY + 20, dimTextColor, false);

            String pathStr = trimTextToWidthWithEllipsis(info.path, panelWidth - 80);
            context.drawText(minecraftClient.textRenderer, Text.literal(pathStr), listX + 5, serverY + 35, dimTextColor, false);

            int buttonSize = 20;
            int btnGap = 5;

            int editX = listX + panelWidth - 10 - (buttonSize + btnGap);
            int startStopX = editX - (buttonSize + btnGap);

            boolean canStop = info.state == ServerState.RUNNING || info.state == ServerState.STARTING;
            boolean hoveredStartStop = mouseX >= startStopX && mouseX <= startStopX+buttonSize && mouseY >= serverY+(entryHeight/2)-(buttonSize/2) && mouseY <= serverY+(entryHeight/2)-(buttonSize/2)+buttonSize;
            boolean hoveredEdit = mouseX >= editX && mouseX <= editX+buttonSize && mouseY >= serverY+(entryHeight/2)-(buttonSize/2) && mouseY <= serverY+(entryHeight/2)-(buttonSize/2)+buttonSize;

            int startStopBg = hoveredStartStop ? highlightColor : lighterColor;
            context.fill(startStopX, serverY+(entryHeight/2)-(buttonSize/2), startStopX+buttonSize, serverY+(entryHeight/2)-(buttonSize/2)+buttonSize, startStopBg);
            drawInnerBorder(context, startStopX, serverY+(entryHeight/2)-(buttonSize/2), buttonSize, buttonSize, borderColor);
            String startStopText = canStop ? "■" : "▶";
            int sstw = minecraftClient.textRenderer.getWidth(startStopText);
            context.drawText(minecraftClient.textRenderer, Text.literal(startStopText), startStopX+(buttonSize - sstw)/2, serverY+(entryHeight/2)-minecraftClient.textRenderer.fontHeight/2, textColor, false);

            int editBg = hoveredEdit ? highlightColor : lighterColor;
            context.fill(editX, serverY+(entryHeight/2)-(buttonSize/2), editX+buttonSize, serverY+(entryHeight/2)-(buttonSize/2)+buttonSize, editBg);
            drawInnerBorder(context, editX, serverY+(entryHeight/2)-(buttonSize/2), buttonSize, buttonSize, borderColor);
            String editText = "E";
            int etw = minecraftClient.textRenderer.getWidth(editText);
            context.drawText(minecraftClient.textRenderer, Text.literal(editText), editX+(buttonSize - etw)/2, serverY+(entryHeight/2)-minecraftClient.textRenderer.fontHeight/2, textColor, false);
        }
        context.disableScissor();

        if (smoothOffset > 0) {
            context.fillGradient(10, contentYStart + 5, this.width - 5, contentYStart + 15, 0x80000000, 0x00000000);
        }
        int maxScroll = Math.max(0, servers.size()*entryHeight - (panelHeight - 10));
        if (smoothOffset < maxScroll) {
            context.fillGradient(10, contentYStart + panelHeight - 15, this.width - 5, contentYStart + panelHeight - 5, 0x00000000, 0x80000000);
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

        if (!serverPopupActive) {
            int contentYStart = tabY + tabHeight + verticalPadding;
            int panelHeight = this.height - contentYStart - 5;
            int panelWidth = this.width - 10;
            int entryHeight = 60;
            int startIndex = (int)Math.floor(smoothOffset / entryHeight);
            int listY = contentYStart + 5;
            int relativeMouseY = (int)(mouseY - listY + smoothOffset);
            int clickedIndex = relativeMouseY / entryHeight;
            if (clickedIndex >= 0 && clickedIndex < servers.size()) {
                ServerInfo info = servers.get(clickedIndex);

                int buttonSize = 20;
                int btnGap = 5;
                int editX = 10 + panelWidth - 10 - (buttonSize + btnGap);
                int startStopX = editX - (buttonSize + btnGap);
                int serverY = listY + (clickedIndex * entryHeight) - (int)smoothOffset;
                int buttonY = serverY+(entryHeight/2)-(buttonSize/2);

                boolean inStartStop = mouseX >= startStopX && mouseX <= startStopX+buttonSize && mouseY >= buttonY && mouseY <= buttonY+buttonSize;
                boolean inEdit = mouseX >= editX && mouseX <= editX+buttonSize && mouseY >= buttonY && mouseY <= buttonY+buttonSize;

                if (inStartStop && button == 0) {
                    if (info.state == ServerState.RUNNING || info.state == ServerState.STARTING) {
                        if (info.terminal != null) {
                            info.terminal.appendOutput("Stopping server...\n");
                            try {
                                if (info.terminal instanceof ServerTerminalInstance sti && sti.processManager != null && sti.processManager.getWriter() != null) {
                                    sti.processManager.getWriter().write("stop\n");
                                    sti.processManager.getWriter().flush();
                                }
                            } catch (IOException ignored) {}
                            info.state = ServerState.STOPPED;
                        }
                    } else {
                        if (info.state == ServerState.STOPPED || info.state == ServerState.CRASHED) {
                            if (info.terminal == null) {
                                info.terminal = new ServerTerminalInstance(minecraftClient, null, UUID.randomUUID(), info);
                                info.isRunning = false;
                            }
                            info.terminal.appendOutput("Starting server...\n");
                            if (info.terminal instanceof ServerTerminalInstance) {
                                info.terminal.launchServerProcess();
                            }
                            info.state = ServerState.STARTING;
                        }
                    }
                    return true;
                }

                if (inEdit && button == 0) {
                    creatingServer = false;
                    editingServer = true;
                    deletingServer = false;
                    editingServerIndex = clickedIndex;
                    serverPopupActive = true;
                    ServerInfo s = servers.get(editingServerIndex);
                    serverNameBuffer.setLength(0);
                    serverNameBuffer.append(s.name);
                    serverVersionBuffer.setLength(0);
                    serverVersionBuffer.append(s.version);
                    selectedTypeIndex = serverTypes.indexOf(s.type);
                    if (selectedTypeIndex < 0) selectedTypeIndex = 0;
                    nameFieldFocused = false;
                    versionFieldFocused = false;
                    serverCreationWarning = false;
                    serverNameCursorPos = serverNameBuffer.length();
                    serverVersionCursorPos = serverVersionBuffer.length();
                    return true;
                }

                if (!inStartStop && !inEdit && button == 0) {
                    openServerScreen(clickedIndex);
                    return true;
                }
            }
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
            String okText = editingServer ? "Save" : "Create";
            int okW = minecraftClient.textRenderer.getWidth(okText) + 10;
            int confirmButtonX = serverPopupX + 5;
            int cancelButtonX = serverPopupX + serverPopupWidth - (minecraftClient.textRenderer.getWidth("Cancel") + 10 + 5);
            if (mouseY >= confirmButtonY && mouseY <= confirmButtonY + 10 + minecraftClient.textRenderer.fontHeight) {
                if (mouseX >= confirmButtonX && mouseX <= confirmButtonX + okW && button == 0) {
                    if (serverNameBuffer.toString().trim().isEmpty()) {
                        serverCreationWarning = true;
                        return true;
                    }
                    serverCreationWarning = false;
                    createOrSaveServer();
                    return true;
                }
                if (editingServer) {
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

            int nameBoxY = serverPopupY + 30;
            int nameBoxH = 12;
            if (mouseX >= serverPopupX + 5 && mouseX <= serverPopupX + serverPopupWidth - 5 && mouseY >= nameBoxY && mouseY <= nameBoxY + nameBoxH && button == 0) {
                nameFieldFocused = true;
                versionFieldFocused = false;
                return true;
            }

            int typeBoxY = serverPopupY + 30 + 35;
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
                versionFieldFocused = true;
                return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int contentYStart = 5 + tabHeight + verticalPadding;
        int panelHeight = this.height - contentYStart - 5;
        int entryHeight = 60;
        int maxScroll = Math.max(0, servers.size()*entryHeight - (panelHeight - 10));
        targetOffset -= verticalAmount * entryHeight * 2;
        if (targetOffset < 0) targetOffset = 0;
        if (targetOffset > maxScroll) targetOffset = maxScroll;
        return true;
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

    private void openServerScreen(int index) {
        ServerInfo info = servers.get(index);
        if (info.terminal == null) {
            info.terminal = new ServerTerminalInstance(minecraftClient, null, UUID.randomUUID(), info);
            info.isRunning = false;
        }
        minecraftClient.setScreen(new ServerTerminalScreen(minecraftClient, remotelyClient, info));
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
    }

    private void createOrSaveServer() {
        String name = serverNameBuffer.toString().trim();
        String path = "C:/remotely/servers/" + name;
        String ver = serverVersionBuffer.toString().trim().isEmpty() ? "latest" : serverVersionBuffer.toString().trim();
        selectedServerType = serverTypes.get(selectedTypeIndex);
        if (editingServer && editingServerIndex >= 0 && editingServerIndex < servers.size()) {
            ServerInfo s = servers.get(editingServerIndex);
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
            runMrPackInstaller(newInfo);
            saveServers();
        }
        closePopup();
    }

    private void runMrPackInstaller(ServerInfo serverInfo) {
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
            pb.start().waitFor();
        } catch (Exception ignored) {}
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
            info.currentPlayers = 0;
            info.maxPlayers = 20;
            info.tps = 20;
            info.uptime = "0h 0m";
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

        if (serverCreationWarning) {
            String warning = "Name cannot be empty";
            int ww = minecraftClient.textRenderer.getWidth(warning);
            trimAndDrawText(context, warning, serverPopupX + (serverPopupWidth - ww) / 2, serverPopupY + serverPopupHeight - 45, serverPopupWidth - 10, 0xFFFF0000);
        }
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
        String visible = fullText.substring(charStart, visibleEnd);
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

    @Override
    public void close() {
        super.close();
    }
}
