package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.awt.image.BufferedImage;

import static redxax.oxy.remotely.util.ImageUtil.drawPixelArt;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;

public class IconButton extends AnimatedWidget {
    protected Runnable action;
    protected boolean centered = false;
    protected BufferedImage icon;
    protected BufferedImage missing;
    protected int iconSize = 16;
    protected int iconPadding = 2;

    public static class Builder extends AnimatedWidget.Builder<IconButton, Builder> {
        public Builder() { super(new IconButton(0, 0, 100, 20, Text.empty(), null)); }
        public Builder label(Text t) { widget.setMessage(t); return this; }
        public Builder onClick(Runnable c) { widget.action = c; return this; }
        public Builder centered(boolean c) { widget.centered = c; return this; }
        public Builder image(BufferedImage img) { widget.icon = img; return this; }
        public Builder imagePath(String path) {
            try {
                widget.icon = loadResourceIcon(path);
            } catch (Exception e) {
                widget.icon = null;
            }
            return this;
        }
        public Builder iconSize(int size) { widget.iconSize = size; return this; }
        public Builder iconPadding(int padding) { widget.iconPadding = padding; return this; }
        @Override protected Builder self() { return this; }
    }

    public IconButton(int x, int y, int width, int height, Text message, BufferedImage icon) {
        super(x, y, width, height, message);
        this.icon = icon;
        try {
            missing = loadResourceIcon("/assets/remotely/icons/missing.png");
        } catch (Exception ignored) {}
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        int iconX = getX() + iconPadding;
        int iconY = getY() + (getHeight() - iconSize) / 2;

        if (icon != null || missing != null) {
            drawPixelArt(ctx, iconX, iconY, iconSize, iconSize, icon != null ? icon : missing);
        }
        String label = getMessage().getString();
        int tw = tr.getWidth(label);
        int tx;
        int textStartX = (getX() + iconPadding * 2 + iconSize);
        if (centered) {
            int remainingWidth = getWidth() - (iconPadding * 2 + iconSize);
            tx = textStartX + (remainingWidth - tw) / 2;
        } else {
            tx = textStartX;
        }
        int ty = (getY() + (getHeight() - tr.fontHeight) / 2) + 1;
        ctx.drawText(tr, label, tx, ty, textColor, true);
    }

    @Override
    protected void onClick(double mouseX, double mouseY, int button) {
        if (action != null && button == 0) {
            action.run();
        }
    }
}
