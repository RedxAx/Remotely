package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.RemotelyClient;
import redxax.oxy.ServerTerminalInstance;

public class ServerTerminalScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    private final ServerInfo serverInfo;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private int buttonX;
    private int buttonY;
    private int explorerButtonX;
    private int explorerButtonY;
    private int pluginButtonX;
    private int pluginButtonY;
    private int buttonW = 60;
    private int buttonH = 20;
    private float terminalScale = 1.0f;

    private int topBarHeight = 30;

    public ServerTerminalScreen(MinecraftClient client, RemotelyClient remotelyClient, ServerInfo info) {
        super(Text.literal(info.name + " - Server Screen"));
        this.minecraftClient = client;
        this.remotelyClient = remotelyClient;
        this.serverInfo = info;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);

        context.fill(0, 0, this.width, topBarHeight, lighterColor);
        drawInnerBorder(context, 0, 0, this.width, topBarHeight, borderColor);

        String stateText = switch (serverInfo.state) {
            case RUNNING -> "Running";
            case STARTING -> "Starting";
            case STOPPED -> "Stopped";
            case CRASHED -> "Crashed";
            default -> "Unknown";
        };
        String titleText = serverInfo.name + " - " + stateText;
        context.drawText(minecraftClient.textRenderer, Text.literal(titleText), 10, 10, textColor, false);

        buttonX = this.width - buttonW - 10;
        buttonY = 5;
        String buttonLabel = serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING ? "Stop" : "Start";
        boolean buttonHovered = mouseX >= buttonX && mouseX <= buttonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH;
        drawButton(context, buttonX, buttonY, buttonLabel, buttonHovered);

        int configButtonX = buttonX - (buttonW + 10);
        int configButtonY = 5;

        explorerButtonX = configButtonX - (buttonW + 10);
        explorerButtonY = 5;
        boolean explorerHovered = mouseX >= explorerButtonX && mouseX <= explorerButtonX + buttonW && mouseY >= explorerButtonY && mouseY <= explorerButtonY + buttonH;
        drawButton(context, explorerButtonX, explorerButtonY, "Explorer", explorerHovered);

        pluginButtonX = explorerButtonX - (buttonW + 10);
        pluginButtonY = 5;
        String pluginLabel = (serverInfo.type.equalsIgnoreCase("paper") || serverInfo.type.equalsIgnoreCase("spigot")) ? "Plugins" : "Mods";
        boolean pluginHovered = mouseX >= pluginButtonX && mouseX <= pluginButtonX + buttonW && mouseY >= pluginButtonY && mouseY <= pluginButtonY + buttonH;
        drawButton(context, pluginButtonX, pluginButtonY, pluginLabel, pluginHovered);

        int terminalOffsetY = topBarHeight + 5;
        int terminalAvailableHeight = this.height - topBarHeight - 10;
        if (serverInfo.terminal != null) {
            serverInfo.terminal.render(context, this.width - 10, terminalAvailableHeight, terminalScale);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= buttonX && mouseX <= buttonX + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH) {
                if (serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING) {
                    stopServer();
                } else {
                    startServer();
                }
                return true;
            }
            if (mouseX >= explorerButtonX && mouseX <= explorerButtonX + buttonW && mouseY >= explorerButtonY && mouseY <= explorerButtonY + buttonH) {
                minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, serverInfo));
                return true;
            }
            if (mouseX >= pluginButtonX && mouseX <= pluginButtonX + buttonW && mouseY >= pluginButtonY && mouseY <= pluginButtonY + buttonH) {
                minecraftClient.setScreen(new PluginModListScreen(minecraftClient, this, serverInfo));
                return true;
            }
        }
        if (serverInfo.terminal != null) {
            serverInfo.terminal.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (serverInfo.terminal != null) {
            if (serverInfo.terminal.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (serverInfo.terminal != null) {
            if (serverInfo.terminal.mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (serverInfo.terminal != null) {
            int scaledHeight = this.height - (topBarHeight + 10);
            serverInfo.terminal.scroll(verticalAmount > 0 ? 1 : -1, scaledHeight);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_EQUAL && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            terminalScale = Math.min(terminalScale + 0.1f, 2.0f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            terminalScale = Math.max(terminalScale - 0.1f, 0.1f);
            return true;
        }
        if (serverInfo.terminal != null) {
            if (serverInfo.terminal.keyPressed(keyCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        return super.charTyped(chr, keyCode);
    }

    private void startServer() {
        if (serverInfo.state == ServerState.STOPPED || serverInfo.state == ServerState.CRASHED) {
            if (serverInfo.terminal == null || !(serverInfo.terminal instanceof ServerTerminalInstance)) {
                ServerTerminalInstance srvTerminal = new ServerTerminalInstance(minecraftClient, null, java.util.UUID.randomUUID(), serverInfo);
                srvTerminal.isServerTerminal = true;
                srvTerminal.serverName = serverInfo.name;
                srvTerminal.serverJarPath = java.nio.file.Paths.get(serverInfo.path, "server.jar").toString().replace("\\", "/");
                serverInfo.terminal = srvTerminal;
            }
            serverInfo.terminal.appendOutput("Starting server...\n");
            if (serverInfo.terminal instanceof ServerTerminalInstance) {
                serverInfo.terminal.launchServerProcess();
            }
            serverInfo.state = ServerState.STARTING;
        }
    }

    private void stopServer() {
        if ((serverInfo.state == ServerState.RUNNING || serverInfo.state == ServerState.STARTING) && serverInfo.terminal != null) {
            serverInfo.terminal.appendOutput("Stopping server...\n");
            try {
                if (serverInfo.terminal instanceof ServerTerminalInstance sti && sti.processManager != null && sti.processManager.getWriter() != null) {
                    sti.processManager.getWriter().write("stop\n");
                    sti.processManager.getWriter().flush();
                }
            } catch (java.io.IOException ignored) {}
            serverInfo.state = ServerState.STOPPED;
        }
    }

    private void drawButton(DrawContext context, int x, int y, String text, boolean hovered) {
        int bg = hovered ? highlightColor : lighterColor;
        context.fill(x, y, x + buttonW, y + buttonH, bg);
        drawInnerBorder(context, x, y, buttonW, buttonH, borderColor);
        int tw = minecraftClient.textRenderer.getWidth(text);
        int tx = x + (buttonW - tw) / 2;
        int ty = y + (buttonH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(minecraftClient.textRenderer, Text.literal(text), tx, ty, textColor, false);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }
}