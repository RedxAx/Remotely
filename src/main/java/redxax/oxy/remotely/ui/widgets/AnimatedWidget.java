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

    public enum EntranceCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CENTER
    }

    private static final Map<EntranceCorner, Float> CORNER_SPEED_MULTIPLIERS = new HashMap<>();

    static {
        CORNER_SPEED_MULTIPLIERS.put(EntranceCorner.TOP_LEFT, 1.0f);
        CORNER_SPEED_MULTIPLIERS.put(EntranceCorner.TOP_RIGHT, 1.0f);
        CORNER_SPEED_MULTIPLIERS.put(EntranceCorner.BOTTOM_LEFT, 1.0f);
        CORNER_SPEED_MULTIPLIERS.put(EntranceCorner.BOTTOM_RIGHT, 1.0f);
        CORNER_SPEED_MULTIPLIERS.put(EntranceCorner.CENTER, 1.0f);
    }

    public static void setCornerSpeedMultiplier(EntranceCorner corner, float multiplier) {
        CORNER_SPEED_MULTIPLIERS.put(corner, multiplier);
    }

    public static float getCornerSpeedMultiplier(EntranceCorner corner) {
        return CORNER_SPEED_MULTIPLIERS.getOrDefault(corner, 1.0f);
    }

    protected float elevation = 0f;
    protected boolean animateColor = true, animateElevation = true, flat = false;
    protected boolean animateLayout = false;
    protected boolean layoutInitialized = false;
    protected float animationSpeed = 0.2f;
    protected float targetX, targetY, targetWidth, targetHeight;
    protected float animatedX, animatedY, animatedWidth, animatedHeight;

    protected boolean entranceAnimationEnabled = true;
    protected EntranceAnimationType entranceAnimationType = EntranceAnimationType.ELEVATION;
    protected EntranceCorner entranceCorner = EntranceCorner.TOP_LEFT;
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
    protected float pivotX = 0f;
    protected float pivotY = 0f;
    protected int zLayer = 0;
    protected boolean absolutePivot = false;

    protected boolean hasScissorRegion = false;
    protected int scissorX1, scissorY1, scissorX2, scissorY2;

    protected static MinecraftClient mc = MinecraftClient.getInstance();
    protected TextRenderer tr = mc.textRenderer;
    public static final Map<Integer, Float> elevationOffsets = new HashMap<>();

    public static abstract class Builder<T extends AnimatedWidget, B extends Builder<T, B>> {
        protected final T widget;

        protected Builder(T widget) { this.widget = widget; }

        public B pos(int x, int y) { widget.setX(x); widget.setY(y); return self(); }
        public B size(int w, int h) { widget.setWidth(w); widget.height = h; return self(); }
        public B focused(boolean f) { widget.setFocused(f); return self(); }
        public B active(boolean a) { widget.active = a; return self(); }
        public B visible(boolean v) { widget.visible = v; return self(); }
        public B animateColor(boolean b) { widget.animateColor = b; return self(); }
        public B animateElevation(boolean b) { widget.animateElevation = b; return self(); }
        public B animateLayout(boolean b) { widget.animateLayout = b; return self(); }
        public B flat(boolean f) { widget.flat = f; return self(); }
        public B animationSpeed(float s) { widget.animationSpeed = s; return self(); }
        public B hint(String text) { widget.hint = text; return self(); }
        public B hintDelay(float delay) { widget.hintDelay = delay; return self(); }
        public B entranceAnimation(boolean enabled) { widget.entranceAnimationEnabled = enabled; return self(); }
        public B entranceAnimationType(EntranceAnimationType type) { widget.entranceAnimationType = type; return self(); }
        public B entranceAnimationStrength(float strength) { widget.entranceAnimationStrength = strength; return self(); }
        public B entranceAnimationSpeed(float speed) { widget.entranceAnimationSpeed = speed; return self(); }
        public B entranceCorner(EntranceCorner corner) { widget.entranceCorner = corner; return self(); }
        public B pivot(float x, float y) { widget.setAbsolutePivot(x, y); return self(); }
        protected abstract B self();
        public T build() { return widget; }
    }

    public AnimatedWidget(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
        targetX = animatedX = x;
        targetY = animatedY = y;
        targetWidth = animatedWidth = width;
        targetHeight = animatedHeight = height;
    }

    public void setScissorRegion(int x1, int y1, int x2, int y2) {
        this.hasScissorRegion = true;
        this.scissorX1 = x1;
        this.scissorY1 = y1;
        this.scissorX2 = x2;
        this.scissorY2 = y2;
    }

    public void clearScissorRegion() {
        this.hasScissorRegion = false;
    }

    @Override
    public void setX(int x) {
        if (animateLayout) {
            targetX = x;
        } else {
            super.setX(x);
            targetX = animatedX = x;
        }
    }

    @Override
    public void setY(int y) {
        if (animateLayout) {
            targetY = y;
        } else {
            super.setY(y);
            targetY = animatedY = y;
        }
    }

    @Override
    public void setWidth(int width) {
        if (animateLayout) {
            targetWidth = width;
        } else {
            super.setWidth(width);
            targetWidth = animatedWidth = width;
        }
    }

    public void setHeight(int height) {
        if (animateLayout) {
            targetHeight = height;
        } else {
            this.height = height;
            targetHeight = animatedHeight = height;
        }
    }

    protected void calculateEntranceDelay() {
        if (!entranceAnimationDelayCalculated) {
            int screenWidth = mc.getWindow().getScaledWidth();
            int screenHeight = mc.getWindow().getScaledHeight();
            float startX = 0, startY = 0;
            if (mc.currentScreen != null) {
                startY = switch (entranceCorner) {
                    case TOP_LEFT -> {
                        startX = 0;
                        yield 0;
                    }
                    case TOP_RIGHT -> {
                        startX = screenWidth;
                        yield 0;
                    }
                    case BOTTOM_LEFT -> {
                        startX = 0;
                        yield screenHeight;
                    }
                    case BOTTOM_RIGHT -> {
                        startX = screenWidth;
                        yield screenHeight;
                    }
                    case CENTER -> {
                        startX = screenWidth / 2.0f;
                        yield screenHeight / 2.0f;
                    }
                };
            }
            float distanceX = getX() - startX;
            float distanceY = getY() - startY;
            float distance = (float) Math.sqrt(distanceX * distanceX + distanceY * distanceY);
            float guiScale = (float) mc.getWindow().getScaleFactor();
            if (guiScale > 0) {
                distance = distance * guiScale;
            }
            float cornerSpeedMultiplier = getCornerSpeedMultiplier(entranceCorner);
            entranceAnimationDelay = distance * 0.001f * cornerSpeedMultiplier;
            entranceAnimationDelayCalculated = true;
        }
    }

    public void resetEntranceAnimation() {
        entranceAnimationStarted = false;
        entranceAnimationProgress = 0f;
        entranceAnimationDelayCalculated = false;
        entranceAnimationDelay = 0f;
        updateEntranceAnimation();
    }

    public void setEntranceCorner(EntranceCorner corner) {
        if (this.entranceCorner != corner) {
            this.entranceCorner = corner;
            resetEntranceAnimation();
        }
    }

    public EntranceCorner getEntranceCorner() {
        return entranceCorner;
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
        if (animateLayout) {
            if (!layoutInitialized) {
                animatedX = targetX;
                animatedY = targetY;
                animatedWidth = targetWidth;
                animatedHeight = targetHeight;
                super.setX(Math.round(animatedX));
                super.setY(Math.round(animatedY));
                super.setWidth(Math.round(animatedWidth));
                this.height = Math.round(animatedHeight);
                layoutInitialized = true;
            } else {
                animatedX += (targetX - animatedX) * globalMovementSpeed * deltaTime;
                animatedY += (targetY - animatedY) * globalMovementSpeed * deltaTime;
                animatedWidth += (targetWidth - animatedWidth) * globalExpandSpeed * deltaTime;
                animatedHeight += (targetHeight - animatedHeight) * globalExpandSpeed * deltaTime;
                super.setX(Math.round(animatedX));
                super.setY(Math.round(animatedY));
                super.setWidth(Math.round(animatedWidth));
                this.height = Math.round(animatedHeight);
            }
        }
        updateEntranceAnimation();
        updateHint();
        if (animateElevation && active) {
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
            } else if (!active) {
                bgColor = innerBackgroundColor;
                borderColor = inClickableBorderColor;
            } else {
                bgColor = elementBackgroundColor;
                borderColor = elementBorderColor;
            }
        }
    }

    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        tick();
        float alpha = getEntranceAlpha();
        if (alpha <= 0f) return;
        boolean shouldClip = hasScissorRegion && (getX() + getWidth() < scissorX1 || getX() > scissorX2 || getY() + getHeight() < scissorY1 || getY() > scissorY2);
        if (shouldClip) return;
        ctx.getMatrices().push();
        if (hasScissorRegion) {
            ctx.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
        }
        float pivotPosX, pivotPosY;
        if (absolutePivot) {
            pivotPosX = pivotX;
            pivotPosY = pivotY;
        } else {
            pivotPosX = getX() + (getWidth() * pivotX);
            pivotPosY = getY() + (getHeight() * pivotY);
        }
        ctx.getMatrices().translate(pivotPosX, pivotPosY, 0);
        float elevationOffset = getEntranceElevationOffset();
        ctx.getMatrices().translate(0, elevation + elevationOffset, 0);
        ctx.getMatrices().translate(-pivotPosX, -pivotPosY, 0);
        int originalBgColor = bgColor;
        int originalBorderColor = borderColor;
        int originalTextColor = textColor;
        if (alpha < 1f) {
            bgColor = applyAlpha(bgColor, alpha);
            borderColor = applyAlpha(borderColor, alpha);
            textColor = applyAlpha(textColor, alpha);
        }
        ctx.getMatrices().translate(0, 0, zLayer);
        drawBackground(ctx);
        drawBorder(ctx);
        drawContent(ctx, mouseX, mouseY);
        bgColor = originalBgColor;
        borderColor = originalBorderColor;
        textColor = originalTextColor;
        if (hasScissorRegion) {
            ctx.disableScissor();
        }
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

    public void onClick(double mouseX, double mouseY, int button) {}

    public void setAbsolutePivot(float x, float y) {
        this.pivotX = x;
        this.pivotY = y;
        this.absolutePivot = true;
    }

    public void setLayer(int i) {
        this.zLayer = i;
    }
}