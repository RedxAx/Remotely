package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static redxax.oxy.remotely.Render.drawInnerBorder;
import static redxax.oxy.remotely.Render.drawOuterBorder;
import static redxax.oxy.remotely.config.Config.*;

public class TabSwitchWidget extends AnimatedWidget {
    private String label;
    private List<String> options;
    private int currentIndex;
    private Runnable onChange;
    private static final Map<Integer, Float> tabElevations = new HashMap<>();

    public static class Builder extends AnimatedWidget.Builder<TabSwitchWidget, Builder> {
        public Builder() { super(new TabSwitchWidget(0, 0, 200, 20, "", List.of())); }
        public Builder label(String l) { widget.label = l; return this; }
        public Builder options(List<String> opts) { widget.options = opts; return this; }
        public Builder currentIndex(int idx) { widget.currentIndex = idx; return this; }
        public Builder onChange(Runnable r) { widget.onChange = r; return this; }
        public TabSwitchWidget.Builder onChange(Consumer<Integer> consumer) { widget.onChange = () -> consumer.accept(widget.currentIndex); return this;}
        @Override protected Builder self() { return this; }
    }

    public TabSwitchWidget(int x, int y, int width, int height, String label, List<String> options) {
        super(x, y, width, height, Text.empty());
        this.label = label;
        this.options = options;
        this.currentIndex = 0;
        this.flat = false;
        this.animateElevation = false;
    }

    public int getCurrentIndex() { return currentIndex; }
    public String getCurrentOption() {
        return options.isEmpty() ? "" : options.get(currentIndex);
    }

    public void setCurrentIndex(int index) {
        if (index >= 0 && index < options.size()) {
            this.currentIndex = index;
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        if (options.isEmpty()) return;

        int segmentCount = options.size();
        int segmentWidth = getWidth() / segmentCount;

        for (int i = 0; i < segmentCount; i++) {
            int segX = getX() + i * segmentWidth;
            int segW = (i == segmentCount - 1) ? (getX() + getWidth() - segX) : segmentWidth;
            boolean segmentHovered = mouseX >= segX && mouseX < segX + segW && mouseY >= getY() && mouseY < getY() + getHeight();
            boolean selected = i == currentIndex;
            int segId = (label + options.get(i)).hashCode();
            float targetOffset = segmentHovered ? -2f : 0f;
            float currentOffset = tabElevations.getOrDefault(segId, 0f);
            currentOffset += (targetOffset - currentOffset) * globalMovementSpeed * deltaTime;
            tabElevations.put(segId, currentOffset);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, currentOffset, 0);
            int color = getElementBackgroundColor(segId, segmentHovered, selected, active, AccentType.DEFAULT);
            ctx.fill(segX, getY(), segX + segW, getY() + getHeight(), color);
            drawInnerBorder(ctx, segX, getY(), segW, getHeight(), getElementBorderColor(segId, segmentHovered, selected, active, AccentType.DEFAULT));
            int textWidth = tr.getWidth(options.get(i));
            int textX = segX + (segW - textWidth) / 2;
            int textY = getY() + ((getHeight() - tr.fontHeight) / 2) + 1;
            ctx.drawText(tr, Text.literal(options.get(i)), textX, textY, textColor, shadow);
            ctx.getMatrices().pop();
        }
    }

    @Override
    protected void drawBorder(DrawContext ctx) {
        drawInnerBorder(ctx, getX(), getY(), getWidth(), getHeight(), elementBorderColor);
        if (!flat) {
            drawOuterBorder(ctx, getX(), getY(), getWidth(), getHeight(), elementBorderColor);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (button == 0 && !options.isEmpty()) {
            int segmentCount = options.size();
            int segmentWidth = getWidth() / segmentCount;
            int clickedSegment = (int) ((mouseX - getX()) / segmentWidth);

            if (clickedSegment >= 0 && clickedSegment < segmentCount && clickedSegment != currentIndex) {
                currentIndex = clickedSegment;
                if (onChange != null) {
                    onChange.run();
                }
            }
        }
    }
}