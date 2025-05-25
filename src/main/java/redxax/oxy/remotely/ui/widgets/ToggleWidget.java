package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import static redxax.oxy.remotely.Render.drawInnerBorder;
import static redxax.oxy.remotely.config.Config.*;

public class ToggleWidget extends AnimatedWidget {
    private boolean toggled;
    private Runnable onChange;
    private float currentKnobX;
    private float targetKnobX;

    public static class Builder extends AnimatedWidget.Builder<ToggleWidget, Builder> {
        public Builder() { super(new ToggleWidget(0, 0, 40, 18, Text.empty())); }
        public Builder toggled(boolean value) { widget.toggled = value; return this; }
        public Builder onChange(Runnable r) { widget.onChange = r; return this; }
        @Override protected Builder self() { return this; }
    }

    public ToggleWidget(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
        this.toggled = false;
        initializeKnobPositions();
    }

    private void initializeKnobPositions() {
        if (toggled) {
            this.currentKnobX = getX() + getWidth() - (getHeight() - 4) - 2;
            this.targetKnobX = getX() + getWidth() - (getHeight() - 4) - 2;
        } else {
            this.currentKnobX = getX() + 2;
            this.targetKnobX = getX() + 2;
        }
    }

    public boolean getValue() {
        return toggled;
    }

    public void setValue(boolean value) {
        this.toggled = value;
        updateTargetPosition();
    }

    public void toggle() {
        this.toggled = !this.toggled;
        updateTargetPosition();
        if (onChange != null) {
            onChange.run();
        }
    }

    private void updateTargetPosition() {
        targetKnobX = toggled ? getX() + getWidth() - (getHeight() - 4) - 2 : getX() + 2;
    }

    private void updateCurrentKnobPosition() {
        float oldLeftPos = getX() + 2;
        float oldRightPos = getX() + getWidth() - (getHeight() - 4) - 2;
        float oldRange = oldRightPos - oldLeftPos;

        float relativePosition = 0f;
        if (oldRange > 0) {
            relativePosition = (currentKnobX - oldLeftPos) / oldRange;
        }
        float newLeftPos = getX() + 2;
        float newRightPos = getX() + getWidth() - (getHeight() - 4) - 2;
        float newRange = newRightPos - newLeftPos;
        currentKnobX = newLeftPos + (relativePosition * newRange);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(getX(), getY() + (int)(getHeight() * 0.75), getX() + getWidth(), getY() + getHeight(), 0x20000000);
        updateTargetPosition();
        currentKnobX += (targetKnobX - currentKnobX) * globalMovementSpeed * deltaTime;
        int knobDiameter = getHeight() - 4;
        int knobX = (int) currentKnobX;
        int knobY = getY() + 2;
        int knobId = (this.hashCode() + "knob").hashCode();
        int knobColor = getElementBackgroundColor(knobId, isHovered(), false, active, false, false, false);
        ctx.fill(knobX, knobY, knobX + knobDiameter, knobY + knobDiameter, knobColor);
        drawInnerBorder(ctx, knobX, knobY, knobDiameter, knobDiameter, getElementBorderColor(knobId, isHovered(), false, active, false, false, false));
    }

    @Override
    protected void onClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            toggle();
        }
    }

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        updateCurrentKnobPosition();
        updateTargetPosition();
    }

    @Override
    public void setWidth(int w) {
        updateCurrentKnobPosition();
        super.setWidth(w);
        updateTargetPosition();
    }
}