package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

import static redxax.oxy.remotely.Render.drawInnerBorder;
import static redxax.oxy.remotely.Render.drawOuterBorder;
import static redxax.oxy.remotely.config.Config.*;


public abstract class AnimatedWidget extends ClickableWidget {
    protected float elevation = 0f;
    protected boolean animateColor = true, animateElevation = true, flat = false;
    protected float animationSpeed = 0.2f;

    protected int bgColor = elementBackgroundColor;
    protected int borderColor = elementBorderColor;
    protected int textColor = globalTextColor;
    protected String tooltipText = "";
    protected static MinecraftClient mc = MinecraftClient.getInstance();
    protected TextRenderer tr = mc.textRenderer;
    public static final Map<Integer, Float> elevationOffsets = new HashMap<>();


    public static abstract class Builder<T extends AnimatedWidget, B extends Builder<T, B>> {
        protected final T widget;

        protected Builder(T widget) { this.widget = widget; }

        public B pos(int x, int y) { widget.setX(x); widget.setY(y); return self(); }
        public B size(int w, int h) { widget.setWidth(w); widget.setHeight(h); return self(); }
        public B focused(boolean f) { widget.setFocused(f); return self(); }
        public B active(boolean a) { widget.active = a; return self(); }
        public B visible(boolean v) { widget.visible = v; return self(); }
        public B animateColor(boolean b) { widget.animateColor = b; return self(); }
        public B animateElevation(boolean b) { widget.animateElevation = b; return self(); }
        public B flat(boolean f) { widget.flat = f; return self(); }
        public B animationSpeed(float s) { widget.animationSpeed = s; return self(); }
        public B tooltip(String text) { widget.tooltipText = text; return self(); }
        protected abstract B self();
        public T build() { return widget; }
    }

    public AnimatedWidget(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    public void tick() {
        if (animateElevation) {
            float elevationTarget = hovered ? -2f : 0f;
            elevation = elevationOffsets.getOrDefault(this.hashCode(), 0f);
            elevation += (elevationTarget - elevation) * globalMovementSpeed * deltaTime;
            elevationOffsets.put(this.hashCode(), elevation);
        }
        if (animateColor) {
            bgColor = getElementBackgroundColor(this.hashCode(), isHovered(), isFocused(), active, AccentType.DEFAULT);
            borderColor = getElementBorderColor(this.hashCode(), isHovered(), isFocused(), active, AccentType.DEFAULT);
        } else {
            if (isHovered()) {
                bgColor = elementHoverBackgroundColor;
                borderColor = elementHoverBorderColor;
            } else {
                bgColor = elementBackgroundColor;
                borderColor = elementBorderColor;
            }
        }
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        tick();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, elevation, 0);
        drawBackground(ctx);
        drawBorder(ctx);
        drawContent(ctx, mouseX, mouseY);
        ctx.getMatrices().pop();
    }

    protected void drawBackground(DrawContext ctx) {
        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
    }

    protected void drawBorder(DrawContext ctx) {
        drawInnerBorder(ctx, getX(), getY(), getWidth(), getHeight(), borderColor);
        if (!flat) {
            drawOuterBorder(ctx, getX(), getY(), getWidth(), getHeight(), bgColor);
        }
    }

    protected abstract void drawContent(DrawContext ctx, int mouseX, int mouseY);

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && visible && isMouseOver(mouseX, mouseY)) {
            onClick(mouseX, mouseY, button);
            return true;
        }
        return false;
    }

    protected void onClick(double mouseX, double mouseY, int button) {}
}
