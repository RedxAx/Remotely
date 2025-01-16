package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.config.Config;

import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.config.Config.*;

public class Render {

    public static int buttonW = 60;
    public static int buttonH = 18;

    public static void drawCustomButton(DrawContext context, int x, int y, String text, MinecraftClient mc, boolean hovered, boolean dynamic, boolean centered, int txColor, int hoverColor) {
        int bg = hovered ? buttonBackgroundHoverColor : buttonBackgroundColor;
        if (dynamic) {
            buttonW = mc.textRenderer.getWidth(text) + 10;
        } else {
            buttonW = 60;
        }
        context.fill(x, y, x + buttonW, y + buttonH, bg);
        drawInnerBorder(context, x, y, buttonW, buttonH, hovered ? buttonBorderHoverColor : buttonBorderColor);
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
        private static int MenuHoverColor = 0xFFd6f264;

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
                drawCustomButton(context, menuX, currentY, items.get(i).label, mc, hovered, false, false, buttonTextColor, MenuHoverColor);
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
