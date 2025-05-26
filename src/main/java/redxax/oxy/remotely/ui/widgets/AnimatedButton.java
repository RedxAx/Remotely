package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

public class AnimatedButton extends AnimatedWidget {
    protected Runnable action;
    protected boolean centered = true;

    public static class ButtonBuilder extends Builder<AnimatedButton, ButtonBuilder> {
        public ButtonBuilder() { super(new AnimatedButton(0, 0, 100, 20, Text.empty())); }
        public ButtonBuilder label(Text t) { widget.setMessage(t); return this; }
        public ButtonBuilder onClick(Runnable c) { widget.action = c; return this; }
        public ButtonBuilder centered(boolean c) { widget.centered = c; return this; }
        @Override protected ButtonBuilder self() { return this; }
    }

    public AnimatedButton(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        String label = getMessage().getString();
        int tw = tr.getWidth(label);
        int tx;
        if (centered) {
            tx = getX() + (getWidth() - tw) / 2;
        } else {
            tx = getX() + 3;
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
