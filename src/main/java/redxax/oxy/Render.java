package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;

public class Render {

    public static int buttonW = 60;
    public static int buttonH = 18;
    public static final int baseColor = 0xFF181818;
    public static final int BgColor = 0xFF242424;
    public static final int BorderColor = 0xFF555555;
    public static final int elementBg = 0xFF2C2C2C;
    public static final int elementSelectedBorder = 0xFFd6f264;
    public static final int elementSelectedBg = 0xFF0b371c;
    public static final int elementBorder = 0xFF444444;
    public static final int elementBorderHover = 0xFF9d9d9d;
    public static final int highlightColor = 0xFF444444;
    public static final int favorateBorder = 0xFFbfab61;
    public static final int favorateSelectedBorder = 0xFFffc800;
    public static final int favorateBg = 0xFF3b2d17;
    public static final int deleteColor = 0xFFff7a7a;
    public static final int deleteHoverColor = 0xFFb4202a;
    public static final int blueColor = 0xFF6cc4f1;
    public static final int blueHoverColor = 0xFF285cc4;
    public static final int textColor = 0xFFFFFFFF;
    public static RenderLayer layer;
    private static int hoveredItemIndex = -1;

    public static void drawHeaderButton(DrawContext context, int x, int y, String text, MinecraftClient mc, boolean hovered, boolean dynamic, int txColor, int hoverColor) {
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
        int tx = x + (buttonW - tw) / 2;
        int ty = y + (buttonH - mc.textRenderer.fontHeight) / 2;
        context.drawText(mc.textRenderer, Text.literal(text), tx, ty, hovered ? hoverColor : txColor, Config.shadow);
    }

    public static void drawInnerBorder(DrawContext context, int x, int y, int buttonW, int buttonH, int i) {
        context.fill(x, y, x + buttonW, y + 1, i);
        context.fill(x, y + buttonH - 1, x + buttonW, y + buttonH, i);
        context.fill(x, y, x + 1, y + buttonH, i);
        context.fill(x + buttonW - 1, y, x + buttonW, y + buttonH, i);
    }
}