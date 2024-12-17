package redxax.oxy.filexplorer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileExplorerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parentScreen;
    private final String rootPath;
    private List<File> files;
    private int baseColor = 0xFF181818;
    private int lighterColor = 0xFF222222;
    private int borderColor = 0xFF333333;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private int backButtonX;
    private int backButtonY;
    private final int buttonW = 60;
    private final int buttonH = 20;
    private int fileStartY = 60;
    private int fileHeight = 16;

    public FileExplorerScreen(MinecraftClient client, Screen parent, String rootPath) {
        super(Text.literal("File Explorer"));
        this.minecraftClient = client;
        this.parentScreen = parent;
        this.rootPath = rootPath;
        this.files = new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();
        File root = new File(rootPath);
        if (root.exists() && root.isDirectory()) {
            File[] arr = root.listFiles();
            if (arr != null) {
                for (File f : arr) {
                    files.add(f);
                }
            }
        }
        backButtonX = this.width / 2 - 30;
        backButtonY = 30;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, Text.literal("File Explorer - " + rootPath), 10, 10, textColor, false);
        boolean hovered = mouseX >= backButtonX && mouseX <= backButtonX + buttonW && mouseY >= backButtonY && mouseY <= backButtonY + buttonH;
        drawButton(context, backButtonX, backButtonY, "Back", hovered);
        int yPos = fileStartY;
        for (File file : files) {
            boolean isHover = mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= yPos && mouseY <= yPos + fileHeight;
            int color = isHover ? highlightColor : lighterColor;
            context.fill(10, yPos, this.width - 10, yPos + fileHeight, color);
            String name = file.isDirectory() ? "[DIR] " + file.getName() : file.getName();
            context.drawText(this.textRenderer, Text.literal(name), 14, yPos + 2, textColor, false);
            yPos += fileHeight;
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
            int yPos = fileStartY;
            for (File file : files) {
                boolean isHover = mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= yPos && mouseY <= yPos + fileHeight;
                if (isHover) {
                    if (file.isDirectory()) {
                        minecraftClient.setScreen(new FileExplorerScreen(minecraftClient, this, file.getAbsolutePath()));
                    } else {
                        minecraftClient.setScreen(new SyntaxHighlighterScreen(minecraftClient, this, file));
                    }
                    return true;
                }
                yPos += fileHeight;
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
