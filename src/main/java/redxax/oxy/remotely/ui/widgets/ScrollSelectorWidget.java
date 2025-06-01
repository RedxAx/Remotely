package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static redxax.oxy.remotely.config.Config.*;

public class ScrollSelectorWidget extends AnimatedWidget {
    private List<String> options;
    private int selectedIndex;
    private Runnable onChange;
    private static final Map<Integer, Float> scrollIndexMap = new HashMap<>();
    private static final Map<Integer, Integer> previousIndexMap = new HashMap<>();

    public ScrollSelectorWidget(int x, int y, int width, int height, List<String> options) {
        super(x, y, width, height, Text.empty());
        this.options = options;
        this.selectedIndex = 0;
    }

    public static class Builder extends AnimatedWidget.Builder<ScrollSelectorWidget, Builder> {
        public Builder() { super(new ScrollSelectorWidget(0, 0, 150, 20, List.of())); }
        public Builder options(List<String> opts) { widget.options = opts; return this; }
        public Builder selectedIndex(int idx) { widget.selectedIndex = idx; return this; }
        public Builder onChange(Runnable r) { widget.onChange = r; return this; }
        @Override protected Builder self() { return this; }
    }

    public int getSelectedIndex() { return selectedIndex; }
    public String getSelectedOption() {
        return options.isEmpty() ? "" : options.get(selectedIndex);
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < options.size()) {
            this.selectedIndex = index;
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    public boolean scroll(double mouseX, double mouseY, double scrollAmount) {
        if (isMouseOver(mouseX, mouseY) && !options.isEmpty()) {
            int scrollDirection = scrollAmount > 0 ? 1 : -1;

            int newIndex = selectedIndex - scrollDirection;
            if (newIndex < 0) newIndex = options.size() - 1;
            if (newIndex >= options.size()) newIndex = 0;

            if (newIndex != selectedIndex) {
                selectedIndex = newIndex;
                if (onChange != null) {
                    onChange.run();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        if (options.isEmpty()) return;

        int id = this.hashCode();
        Float scrollIndex = scrollIndexMap.get(id);
        Integer prevIndex = previousIndexMap.get(id);

        if (scrollIndex == null || prevIndex == null) {
            scrollIndex = (float) selectedIndex;
            prevIndex = selectedIndex;
        }

        if (prevIndex != selectedIndex) {
            prevIndex = selectedIndex;
        }

        scrollIndex += (selectedIndex - scrollIndex) * globalScrollSpeed * deltaTime;

        while (scrollIndex - selectedIndex > options.size() / 2f) {
            scrollIndex -= options.size();
        }
        while (scrollIndex - selectedIndex < -options.size() / 2f) {
            scrollIndex += options.size();
        }

        scrollIndexMap.put(id, scrollIndex);
        previousIndexMap.put(id, prevIndex);

        int contentX = getX() + 1;
        int contentWidth = getWidth() - 2;
        ctx.enableScissor(contentX, getY(), contentX + contentWidth, getY() + getHeight());

        float centerSlot = contentX + contentWidth / 2f;

        int maxTextW = 0;
        for (String s : options) {
            int textW = mc.textRenderer.getWidth(s);
            if (textW > maxTextW) {
                maxTextW = textW;
            }
        }

        float slotSpacing = maxTextW + 15f;

        for (int i = 0; i < options.size(); i++) {
            float ringIndex = i - scrollIndex;
            if (ringIndex < -options.size() / 2f) ringIndex += options.size();
            if (ringIndex > options.size() / 2f) ringIndex -= options.size();

            float offsetX = centerSlot + ringIndex * slotSpacing;
            String s = options.get(i);
            int textW = mc.textRenderer.getWidth(s);
            float textX = offsetX - textW / 2f;
            float textY = (getY() + (getHeight() - mc.textRenderer.fontHeight) / 2f) + 1;

            int current = Math.round(scrollIndex) % options.size();
            if (current < 0) current += options.size();

            int textColor = getTextColor(id + i, isHovered(), i == current && isHovered(),
                    i == current, false, false, false);
            ctx.drawText(mc.textRenderer, Text.literal(s), (int) textX, (int) textY, textColor, shadow);
        }

        ctx.disableScissor();
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int midX = getX() + getWidth() / 2;
        if (mouseX >= midX) {
            selectedIndex = (selectedIndex + 1) % options.size();
        } else {
            selectedIndex = selectedIndex - 1;
            if (selectedIndex < 0) selectedIndex = options.size() - 1;
        }
        if (onChange != null) {
            onChange.run();
        }
    }
}
