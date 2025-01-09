package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class Render {

    public static int buttonW = 60;
    public static int buttonH = 18;
    public static final int baseColor = 0xFF181818;
    public static final int BgColor = 0xFF242424;
    public static final int borderColor = 0xFF555555;
    public static final int elementBg = 0xFF2C2C2C;
    public static final int greenBright = 0xFFd6f264;
    public static final int darkGreen = 0xFF0b371c;
    public static final int elementBorder = 0xFF444444;
    public static final int elementBorderHover = 0xFF9d9d9d;
    public static final int highlightColor = 0xFF444444;
    public static final int paleGold = 0xFFbfab61;
    public static final int kingsGold = 0xFFffc800;
    public static final int darkGold = 0xFF3b2d17;
    public static final int deleteColor = 0xFFff7a7a;
    public static final int deleteHoverColor = 0xFFb4202a;
    public static final int blueColor = 0xFF249fde;
    public static final int blueDark = 0xFF17253b;
    public static final int blueHoverColor = 0xFF6cc4f1;
    public static final int redColor = 0xFFb4202a;
    public static final int redBg = 0xFF3b1725;
    public static final int redBright = 0xFFdf3e23;
    public static final int redVeryBright = 0xFFff7a7a;
    public static final int textColor = 0xFFFFFFFF;
    public static final int dimTextColor = 0xFFBBBBBB;
    public static final int lighterColor = 0xFF222222;
    private static int MenuHoverColor = greenBright;

    public static void drawCustomButton(DrawContext context, int x, int y, String text, MinecraftClient mc, boolean hovered, boolean dynamic, boolean centered, int txColor, int hoverColor) {
        int bg = hovered ? highlightColor : elementBg;
        if (dynamic) {
            buttonW = mc.textRenderer.getWidth(text) + 10;
        } else {
            buttonW = 60;
        }
        context.fill(x, y, x + buttonW, y + buttonH, bg);
        drawInnerBorder(context, x, y, buttonW, buttonH, hovered ? elementBorderHover : elementBorder);
        context.fill(x, y + buttonH, x + buttonW, y + buttonH + 2, hovered ? 0xFF0b0b0b : 0xFF000000);
        int tw = mc.textRenderer.getWidth(text);
        int tx = centered ? x + (buttonW - tw) / 2 : x + 5;
        int ty = y + (buttonH - mc.textRenderer.fontHeight) / 2;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, hovered ? hoverColor : txColor, Config.shadow);
    }

    public static void drawInnerBorder(DrawContext context, int x, int y, int buttonW, int buttonH, int i) {
        context.fill(x, y, x + buttonW, y + 1, i);
        context.fill(x, y + buttonH - 1, x + buttonW, y + buttonH, i);
        context.fill(x, y, x + 1, y + buttonH, i);
        context.fill(x + buttonW - 1, y, x + buttonW, y + buttonH, i);
    }

    public static class ContextMenu {
        private static class MenuItem {
            String label;
            Runnable action;
            MenuItem(String label, Runnable action) {
                this.label = label;
                this.action = action;
            }
        }
        private static final List<MenuItem> items = new ArrayList<>();
        private static boolean open;
        private static int menuX;
        private static int menuY;
        private static int itemWidth;
        private static int itemHeight = 18;
        private static int gap = 1;

        public static void show(int x, int y, int width, int screenWidth, int screenHeight) {
            open = true;
            menuX = x;
            menuY = y;
            itemWidth = width;
            int margin = 60;

            if (menuX + itemWidth > screenWidth) {
                menuX = screenWidth - itemWidth;
            }

            int totalMenuHeight = items.size() * (itemHeight + gap) - gap;

            if (menuY + totalMenuHeight > screenHeight - margin) {
                menuY = screenHeight - totalMenuHeight - margin;
            }
        }

        public static void hide() {
            open = false;
            items.clear();
        }

        public static void addItem(String label, Runnable action, int HoverColor) {
            items.add(new MenuItem(label, action));
            MenuHoverColor = HoverColor;
        }

        public static boolean isOpen() {
            return open;
        }

        public static void renderMenu(DrawContext context, MinecraftClient mc, int mouseX, int mouseY) {
            if (!open) return;
            int currentY = menuY;
            for (int i = 0; i < items.size(); i++) {
                boolean hovered = mouseX >= menuX && mouseX <= menuX + itemWidth && mouseY >= currentY && mouseY < currentY + itemHeight;
                drawCustomButton(context, menuX, currentY, items.get(i).label, mc, hovered, false, false, textColor, MenuHoverColor);
                currentY += itemHeight + gap;
            }
        }

        public static boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!open) return false;
            int currentY = menuY;
            for (int i = 0; i < items.size(); i++) {
                boolean hovered = mouseX >= menuX && mouseX <= menuX + itemWidth && mouseY >= currentY && mouseY < currentY + itemHeight;
                if (hovered && button == GLFW.GLFW_MOUSE_BUTTON_1) {
                    items.get(i).action.run();
                    hide();
                    return true;
                }
                currentY += itemHeight + gap;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                hide();
            }
            return false;
        }
    }
}
