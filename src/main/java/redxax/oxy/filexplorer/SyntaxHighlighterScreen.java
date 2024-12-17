package redxax.oxy.filexplorer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SyntaxHighlighterScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parentScreen;
    private final File file;
    private String fileContent;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private int backButtonX;
    private int backButtonY;
    private final int buttonW = 60;
    private final int buttonH = 20;
    private int offsetY = 50;
    private int lineHeight = 12;
    private float scale = 1.0f;

    public SyntaxHighlighterScreen(MinecraftClient client, Screen parent, File file) {
        super(Text.literal("File Editor"));
        this.minecraftClient = client;
        this.parentScreen = parent;
        this.file = file;
    }

    @Override
    protected void init() {
        super.init();
        try {
            fileContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            fileContent = "Error reading file: " + e.getMessage();
        }
        backButtonX = this.width / 2 - 30;
        backButtonY = 20;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, Text.literal("Editing: " + file.getName()), 10, 10, textColor, false);
        boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
        drawButton(context, backButtonX, backButtonY, "Back", hovered);

        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0f);
        List<String> lines = fileContent.lines().toList();
        int yPos = offsetY;
        for (String line : lines) {
            int color = parseSyntaxColor(line);
            context.drawText(this.textRenderer, Text.literal(line), 10, yPos, color, false);
            yPos += lineHeight;
        }
        context.getMatrices().pop();
    }

    private int parseSyntaxColor(String line) {
        String lower = line.toLowerCase();
        if (lower.startsWith("yaml") || lower.contains(":")) return 0xFF55FFFF;
        if (lower.contains("{") || lower.contains("}")) return 0xFFFF55FF;
        if (lower.contains("mod") || lower.contains("plugin")) return 0xFFFFFF55;
        return textColor;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        offsetY += verticalAmount > 0 ? -10 : 10;
        offsetY = Math.max(offsetY, 20);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraftClient.setScreen(parentScreen);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            scale = Math.min(scale + 0.1f, 2.0f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            scale = Math.max(scale - 0.1f, 0.5f);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
            if (hovered) {
                minecraftClient.setScreen(parentScreen);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
