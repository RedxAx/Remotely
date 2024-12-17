package redxax.oxy.modplugin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.servers.ServerInfo;
import redxax.oxy.servers.ServerState;

public class PluginModBrowserScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parentScreen;
    private final ServerInfo serverInfo;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private int backButtonX;
    private int backButtonY;
    private final int buttonW = 60;
    private final int buttonH = 20;
    private int offsetY = 60;
    private List<String> resourceList;
    private String filterText = "";
    private String installedType = "";
    private String serverVersion = "";

    public PluginModBrowserScreen(MinecraftClient client, Screen parent, ServerInfo serverInfo) {
        super(Text.literal("Plugin / Mod Browser"));
        this.minecraftClient = client;
        this.parentScreen = parent;
        this.serverInfo = serverInfo;
        this.resourceList = new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();
        if (serverInfo.state == ServerState.RUNNING) {
            if (serverInfo.name.toLowerCase().contains("paper") || serverInfo.name.toLowerCase().contains("spigot")) {
                installedType = "paper";
            } else {
                installedType = "forge";
            }
            serverVersion = "1.19.2";
        }
        resourceList.add("Example " + installedType + " resource for version " + serverVersion);
        resourceList.add("Filter-based Resource #2");
        backButtonX = this.width / 2 - 30;
        backButtonY = 20;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, Text.literal("Plugin / Mod Browser"), 10, 10, textColor, false);
        boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
        drawButton(context, backButtonX, backButtonY, "Back", hovered);
        int yPos = offsetY;
        for (String res : resourceList) {
            context.fill(10, yPos, this.width - 10, yPos + 16, lighterColor);
            context.drawText(this.textRenderer, Text.literal(res), 14, yPos + 2, textColor, false);
            yPos += 16;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
            if (hovered) {
                minecraftClient.setScreen(parentScreen);
                return true;
            }
            int yPos = offsetY;
            for (String res : resourceList) {
                if (mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= yPos && mouseY <= yPos + 16) {
                    minecraftClient.setScreen(new PluginModResourceScreen(minecraftClient, this, res));
                    return true;
                }
                yPos += 16;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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
}
