package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ServerConfigurationScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parentScreen;
    private final ServerInfo serverInfo;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private TextFieldWidget ramField;
    private TextFieldWidget maxPlayersField;
    private TextFieldWidget portField;
    private TextFieldWidget onlineModeField;
    private int backButtonX;
    private int backButtonY;
    private final int buttonW = 60;
    private final int buttonH = 20;

    public ServerConfigurationScreen(MinecraftClient client, Screen parent, ServerInfo info) {
        super(Text.literal("Server Configuration"));
        this.minecraftClient = client;
        this.parentScreen = parent;
        this.serverInfo = info;
    }

    @Override
    protected void init() {
        super.init();
        ramField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 50, 200, 20, Text.literal("RAM (MB)"));
        ramField.setText(Integer.toString(serverInfo.ramMB));
        addDrawableChild(ramField);
        maxPlayersField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 80, 200, 20, Text.literal("Max Players"));
        maxPlayersField.setText(Integer.toString(serverInfo.maxPlayers));
        addDrawableChild(maxPlayersField);
        portField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 110, 200, 20, Text.literal("Port"));
        portField.setText(Integer.toString(serverInfo.port));
        addDrawableChild(portField);
        onlineModeField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 140, 200, 20, Text.literal("Online Mode (true/false)"));
        onlineModeField.setText(Boolean.toString(serverInfo.onlineMode));
        addDrawableChild(onlineModeField);
        backButtonX = this.width / 2 - 30;
        backButtonY = 180;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, Text.literal("Server Configuration"), this.width / 2 - 80, 20, textColor, false);
        boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
        drawButton(context, backButtonX, backButtonY, "Back", hovered);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
            if (hovered) {
                saveSettings();
                minecraftClient.setScreen(parentScreen);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            saveSettings();
            minecraftClient.setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawButton(DrawContext context, int x, int y, String text, boolean hovered) {
        int bg = hovered ? highlightColor : lighterColor;
        context.fill(x, y, x + buttonW, y + buttonH, bg);
        drawInnerBorder(context, x, y, buttonW, buttonH, borderColor);
        int tw = this.textRenderer.getWidth(text);
        int tx = x + (buttonW - tw) / 2;
        int ty = y + (buttonH - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal(text), tx, ty, textColor, false);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void saveSettings() {
        serverInfo.maxPlayers = parseInt(maxPlayersField.getText(), serverInfo.maxPlayers);
        serverInfo.port = parseInt(portField.getText(), serverInfo.port);
        serverInfo.onlineMode = parseBoolean(onlineModeField.getText(), serverInfo.onlineMode);
        serverInfo.ramMB = parseInt(ramField.getText(), serverInfo.ramMB);
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private boolean parseBoolean(String s, boolean fallback) {
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        return fallback;
    }
}