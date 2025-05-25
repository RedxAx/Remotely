package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.awt.image.BufferedImage;

import static redxax.oxy.remotely.util.ImageUtil.drawPixelArt;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;

public class SquareButtonWidget extends AnimatedWidget {
    protected BufferedImage image;
    protected Runnable action;
    protected BufferedImage missing;

    public static class Builder extends AnimatedWidget.Builder<SquareButtonWidget, Builder> {
        public Builder() { super(new SquareButtonWidget(0, 0, 18, 18, null)); }
        public Builder image(BufferedImage img) { widget.image = img; return this; }
        public Builder imagePath(String path) {
            try {
                widget.image = loadResourceIcon(path);
            } catch (Exception e) {
                widget.image = null;
            }
            return this;
        }
        public Builder onClick(Runnable c) { widget.action = c; return this; }
        @Override protected Builder self() { return this; }
    }

    public SquareButtonWidget(int x, int y, int width, int height, BufferedImage image) {
        super(x, y, width, height, Text.empty());
        this.image = image;
        try {
            missing = loadResourceIcon("/assets/remotely/icons/missing.png");
        } catch (Exception ignored) {}
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        drawPixelArt(ctx, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, image != null ? image : missing);
    }

    @Override
    protected void onClick(double mouseX, double mouseY, int button) {
        if (action != null && button == 0) {
            action.run();
        }
    }
}