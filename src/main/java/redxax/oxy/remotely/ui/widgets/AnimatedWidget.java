package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import redxax.oxy.remotely.Render;

import java.util.HashMap;
import java.util.Map;

import static redxax.oxy.remotely.config.Config.*;

public abstract class AnimatedWidget extends ClickableWidget {

    public enum EntranceAnimationType {
        ELEVATION
    }

    protected float elevation = 0f;
    protected boolean animateColor = true, animateElevation = true, flat = false;
    protected float animationSpeed = 0.2f;

    protected boolean entranceAnimationEnabled = true;
    protected EntranceAnimationType entranceAnimationType = EntranceAnimationType.ELEVATION;
    protected float entranceAnimationStrength = 0.4f;
    protected float entranceAnimationSpeed = 1.0f;

    protected boolean entranceAnimationStarted = false;
    protected float entranceAnimationProgress = 0f;
    protected float entranceAnimationDelay = 0f;
    protected boolean entranceAnimationDelayCalculated = false;

    protected int bgColor = elementBackgroundColor;
    protected int borderColor = elementBorderColor;
    protected int textColor = globalTextColor;
    protected String hint = "";
    protected float hintDelay = 1.5f;
    protected boolean hintVisible = false;
    protected float hintHoverTime = 0f;
    protected float hintWidth = 0f;
    protected float hintTargetWidth = 0f;
    protected boolean wasHovered = false;

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
        public B hint(String text) { widget.hint = text; return self(); }
        public B hintDelay(float delay) { widget.hintDelay = delay; return self(); }
        public B entranceAnimation(boolean enabled) { widget.entranceAnimationEnabled = enabled; return self(); }
        public B entranceAnimationType(EntranceAnimationType type) { widget.entranceAnimationType = type; return self(); }
        public B entranceAnimationStrength(float strength) { widget.entranceAnimationStrength = strength; return self(); }
        public B entranceAnimationSpeed(float speed) { widget.entranceAnimationSpeed = speed; return self(); }
        protected abstract B self();
        public T build() { return widget; }
    }

    public AnimatedWidget(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    protected void calculateEntranceDelay() {
        if (!entranceAnimationDelayCalculated) {
            float distance = (float) Math.sqrt(getX() * getX() + getY() * getY());
            entranceAnimationDelay = distance * 0.001f;
            entranceAnimationDelayCalculated = true;
        }
    }

    protected void resetEntranceAnimation() {
        entranceAnimationStarted = false;
        entranceAnimationProgress = 0f;
        entranceAnimationDelayCalculated = false;
        entranceAnimationDelay = 0f;
        updateEntranceAnimation();
    }

    protected void updateEntranceAnimation() {
        if (!entranceAnimationEnabled) {
            entranceAnimationProgress = 1f;
            return;
        }

        calculateEntranceDelay();

        if (!entranceAnimationStarted) {
            entranceAnimationDelay -= deltaTime;
            if (entranceAnimationDelay <= 0) {
                entranceAnimationStarted = true;
            }
            return;
        }

        if (entranceAnimationProgress < 1f) {
            entranceAnimationProgress += entranceAnimationSpeed * deltaTime * 2f;
            entranceAnimationProgress = Math.min(1f, entranceAnimationProgress);
        }
    }

    protected float getEntranceAlpha() {
        if (!entranceAnimationEnabled) return 1f;
        if (!entranceAnimationStarted) return 0f;

        return Math.min(1f, entranceAnimationProgress * 2f);
    }

    protected float getEntranceElevationOffset() {
        if (!entranceAnimationEnabled || entranceAnimationType != EntranceAnimationType.ELEVATION) return 0f;
        if (!entranceAnimationStarted) return 20f * entranceAnimationStrength;

        float t = entranceAnimationProgress;
        float easeOut = 1f - (float) Math.pow(1f - t, 3);
        return (1f - easeOut) * 20f * entranceAnimationStrength;
    }

    protected int applyAlpha(int color, float alpha) {
        if (alpha >= 1f) return color;
        int originalAlpha = (color >> 24) & 0xFF;
        int newAlpha = (int) (originalAlpha * alpha);
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    protected void updateHint() {
        if (hint.isEmpty() || !visible || !active) {
            hintVisible = false;
            hintHoverTime = 0f;
            hintWidth = 0f;
            return;
        }
        boolean currentlyHovered = isHovered();
        if (currentlyHovered && !wasHovered) {
            hintHoverTime = 0f;
        }

        if (currentlyHovered) {
            hintHoverTime += deltaTime;
            if (hintHoverTime >= hintDelay && !hintVisible) {
                hintVisible = true;
                hintTargetWidth = tr.getWidth(hint) + 8;
            }
        } else {
            hintVisible = false;
            hintHoverTime = 0f;
        }
        if (hintVisible) {
            hintWidth += (hintTargetWidth - hintWidth) * globalExpandSpeed * deltaTime;
        } else {
            hintWidth = 0f;
        }
        wasHovered = currentlyHovered;
    }

    protected void drawHint(DrawContext context) {
        if (!hintVisible || hint.isEmpty() || hintWidth <= 0) return;
        int hintHeight = 15;
        int hintPadding = 4;
        int hintX = (getX() + getWidth() / 2 - (int)(hintWidth / 2));
        int hintY = (getY() - hintHeight - 4) - 1;
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        if (hintX < 4) {
            hintX = 4;
        }
        if (hintX + hintWidth > screenWidth - 4) {
            hintX = screenWidth - (int)hintWidth - 4;
        }
        if (hintY < 4) {
            hintY = getY() + getHeight() + 4;
        }
        if (hintY + hintHeight > screenHeight - 4) {
            hintY = screenHeight - hintHeight - 4;
        }
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 500);
        int hintBgColor = getElementBackgroundColor("hint".hashCode() + this.hashCode(), false, false, false, AccentType.DEFAULT);
        int hintBorderColor = getElementBorderColor("hint".hashCode() + this.hashCode(), false, false, false, AccentType.DEFAULT);
        context.fill(hintX, hintY, hintX + (int)hintWidth, hintY + hintHeight, hintBgColor);
        Render.drawInnerBorder(context, hintX, hintY, (int)hintWidth, hintHeight, hintBorderColor);
        Render.drawOuterBorder(context, hintX, hintY, (int)hintWidth, hintHeight, hintBgColor);
        context.enableScissor(hintX, hintY, hintX + (int)hintWidth, hintY + hintHeight);
        int textX = hintX + hintPadding;
        int textY = (hintY + (hintHeight - tr.fontHeight) / 2) + 1;
        context.drawText(tr, hint, textX, textY, globalTextColor, false);
        context.disableScissor();
        context.getMatrices().pop();
    }

    public void tick() {
        updateEntranceAnimation();
        updateHint();

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
            if (isHovered() && isFocused()) {
                bgColor = accentDarkHoverColor;
                borderColor = accentHoverColor;
            } else if (isFocused()) {
                bgColor = accentDarkColor;
                borderColor = accentColor;
            } else if (isHovered()) {
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

        float alpha = getEntranceAlpha();
        if (alpha <= 0f) return;

        ctx.getMatrices().push();

        float elevationOffset = getEntranceElevationOffset();

        ctx.getMatrices().translate(0, elevation + elevationOffset, 0);

        int originalBgColor = bgColor;
        int originalBorderColor = borderColor;
        int originalTextColor = textColor;

        if (alpha < 1f) {
            bgColor = applyAlpha(bgColor, alpha);
            borderColor = applyAlpha(borderColor, alpha);
            textColor = applyAlpha(textColor, alpha);
        }

        drawBackground(ctx);
        drawBorder(ctx);
        drawContent(ctx, mouseX, mouseY);

        bgColor = originalBgColor;
        borderColor = originalBorderColor;
        textColor = originalTextColor;
        ctx.getMatrices().pop();
        drawHint(ctx);
    }

    protected void drawBackground(DrawContext ctx) {
        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
    }

    protected void drawBorder(DrawContext ctx) {
        float alpha = getEntranceAlpha();
        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + 1, borderColor);
        ctx.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), borderColor);
        ctx.fill(getX(), getY(), getX() + 1, getY() + getHeight(), borderColor);
        ctx.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), borderColor);
        if (!flat) {
            int outerBorderAlpha = applyAlpha(globalOuterBorder, alpha);
            int bgShadowAlpha = applyAlpha(bgColor, alpha);
            int shadowAlpha = applyAlpha(0x40000000, alpha);
            int gradientStartAlpha = applyAlpha(0x00000000, alpha);
            int gradientEndAlpha = applyAlpha(0x60000000, alpha);

            ctx.fill(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY(), outerBorderAlpha);
            ctx.fill(getX() - 1, getY() + getHeight(), getX() + getWidth() + 1, getY() + getHeight() + 3, outerBorderAlpha);
            ctx.fill(getX() - 1, getY(), getX(), getY() + getHeight(), outerBorderAlpha);
            ctx.fill(getX() + getWidth(), getY(), getX() + getWidth() + 1, getY() + getHeight(), outerBorderAlpha);
            ctx.fill(getX(), getY() + getHeight(), getX() + getWidth(), getY() + getHeight() + 2, bgShadowAlpha);
            ctx.fill(getX(), getY() + getHeight(), getX() + getWidth(), getY() + getHeight() + 2, shadowAlpha);
            ctx.fillGradient(getX(), getY() + getHeight() + 2, getX() + getWidth(), getY() + getHeight() + 4, gradientStartAlpha, gradientEndAlpha);
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