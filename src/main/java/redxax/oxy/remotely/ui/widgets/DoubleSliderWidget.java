package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

import static redxax.oxy.remotely.Render.drawInnerBorder;
import static redxax.oxy.remotely.config.Config.*;

public class DoubleSliderWidget extends AnimatedWidget {
    private double currentValue;
    private String label;
    private String tooltipText = "";
    private Runnable onChange;
    private boolean dragging = false;
    private float currentKnobX;
    private float targetKnobX;
    private static final Map<Integer, Float> knobPositions = new HashMap<>();

    public static class Builder extends AnimatedWidget.Builder<DoubleSliderWidget, Builder> {
        public Builder() { super(new DoubleSliderWidget(0, 0, 100, 20, "", 0.0)); }
        public Builder value(double v) { widget.currentValue = v; return this; }
        public Builder label(String l) { widget.label = l; return this; }
        public Builder hint(String t) { widget.tooltipText = t; return this; }
        public Builder onChange(Runnable r) { widget.onChange = r; return this; }
        @Override protected Builder self() { return this; }
    }

    public DoubleSliderWidget(int x, int y, int width, int height, String label, double value) {
        super(x, y, width, height, Text.empty());
        this.label = label;
        this.currentValue = MathHelper.clamp(value, 0.0, 1.0);
        updateKnobPositions();
    }

    private void updateKnobPositions() {
        float clampedValue = (float) MathHelper.clamp(currentValue, 0.0, 1.0);
        int knobDiameter = getHeight() - 4;
        int availableWidth = getWidth() - knobDiameter - 4;
        targetKnobX = getX() + 2 + availableWidth * clampedValue;

        Integer knobId = (this.hashCode() + "knob").hashCode();
        currentKnobX = knobPositions.getOrDefault(knobId, targetKnobX);
    }

    public double getValue() { return currentValue; }

    public void setValue(double value) {
        this.currentValue = MathHelper.clamp(value, 0.0, 1.0);
        updateKnobPositions();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        updateKnobPositions();

        currentKnobX += (targetKnobX - currentKnobX) * globalMovementSpeed * deltaTime;
        knobPositions.put((this.hashCode() + "knob").hashCode(), currentKnobX);

        int knobDiameter = getHeight() - 4;
        int knobX = (int) currentKnobX;
        int knobY = getY() + 2;
        int knobId = (this.hashCode() + "knob").hashCode();
        int knobColor = getElementBackgroundColor(knobId, isHovered(), false, active, AccentType.DEFAULT);

        ctx.fill(knobX, knobY, knobX + knobDiameter, knobY + knobDiameter, knobColor);
        drawInnerBorder(ctx, knobX, knobY, knobDiameter, knobDiameter, getElementBorderColor(knobId, isHovered(), false, active, AccentType.DEFAULT));

        MinecraftClient mc = MinecraftClient.getInstance();
        int tw = mc.textRenderer.getWidth(label);
        int tx = getX() + (getWidth() - tw) / 2;
        int ty = getY() + (getHeight() - mc.textRenderer.fontHeight) / 2;
        ctx.drawText(mc.textRenderer, Text.literal(label), tx, ty, textColor, shadow);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = true;
            updateValueFromMouse(mouseX);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        dragging = false;
    }

    private void updateValueFromMouse(double mouseX) {
        int knobDiameter = getHeight() - 4;
        int availableWidth = getWidth() - knobDiameter - 4;
        double ratio = (mouseX - getX() - 2) / availableWidth;
        ratio = MathHelper.clamp(ratio, 0.0, 1.0);

        if (Math.abs(ratio - currentValue) > 0.001) {
            currentValue = ratio;
            if (onChange != null) {
                onChange.run();
            }
        }
    }

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        updateKnobPositions();
    }

    @Override
    public void setWidth(int w) {
        super.setWidth(w);
        updateKnobPositions();
    }
}