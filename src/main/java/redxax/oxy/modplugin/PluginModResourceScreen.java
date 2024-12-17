package redxax.oxy.modplugin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class PluginModResourceScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parentScreen;
    private final String resourceName;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private int backButtonX;
    private int backButtonY;
    private final int buttonW = 60;
    private final int buttonH = 20;
    private int installButtonX;
    private int installButtonY;

    public PluginModResourceScreen(MinecraftClient client, Screen parent, String resourceName) {
        super(Text.literal("Resource Info"));
        this.minecraftClient = client;
        this.parentScreen = parent;
        this.resourceName = resourceName;
    }

    @Override
    protected void init() {
        super.init();
        backButtonX = this.width / 2 - 70;
        backButtonY = 30;
        installButtonX = this.width / 2 + 10;
        installButtonY = 30;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, Text.literal("Resource: " + resourceName), 10, 10, textColor, false);
        boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
        drawButton(context, backButtonX, backButtonY, "Back", hovered);
        boolean hovered2 = mouseX >= installButtonX && mouseX <= installButtonX + buttonW && mouseY >= installButtonY && mouseY <= installButtonY + buttonH;
        drawButton(context, installButtonX, installButtonY, "Install", hovered2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
            boolean hovered2 = mouseX >= installButtonX && mouseX <= installButtonX + buttonW && mouseY >= installButtonY && mouseY <= installButtonY + buttonH;
            if (hovered) {
                minecraftClient.setScreen(parentScreen);
                return true;
            }
            if (hovered2) {
                minecraftClient.setScreen(parentScreen);
                return true;
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
        context.drawText(this.textRenderer, Text.literal(text), tx, ty, 0xFFFFFFFF, false);
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }
}
