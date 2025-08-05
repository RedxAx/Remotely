package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static redxax.oxy.remotely.Render.drawInnerBorder;
import static redxax.oxy.remotely.Render.drawOuterBorder;
import static redxax.oxy.remotely.config.Config.*;

public class TabSwitchWidget extends AnimatedWidget {
    private List<String> options;
    private int currentIndex;
    private boolean multiSelect = false;
    private final List<Integer> selectedIndices = new ArrayList<>();
    public Runnable onChange;

    private final List<AnimatedButton> tabButtons = new ArrayList<>();

    public static class Builder extends AnimatedWidget.Builder<TabSwitchWidget, Builder> {
        public Builder() { super(new TabSwitchWidget(0, 0, 200, 20, "", List.of())); }
        public Builder label(String l) {
            return this; }
        public Builder options(List<String> opts) {
            widget.options = opts;
            widget.recreateButtons();
            return this;
        }
        public Builder currentIndex(int idx) { widget.setCurrentIndex(idx); return this; }
        public Builder selectedIndices(List<Integer> indices) { if(widget.multiSelect) { widget.selectedIndices.clear(); widget.selectedIndices.addAll(indices); } return this; }
        public Builder multiSelect(boolean ms) { widget.multiSelect = ms; return this; }
        public Builder onChange(Runnable r) { widget.onChange = r; return this; }
        public Builder onChange(Consumer<Integer> consumer) { widget.onChange = () -> consumer.accept(widget.getCurrentIndex()); return this;}
        public Builder onMultiChange(Consumer<List<Integer>> consumer) { widget.onChange = () -> consumer.accept(widget.getSelectedIndices()); return this; }
        @Override protected Builder self() { return this; }
    }

    public TabSwitchWidget(int x, int y, int width, int height, String label, List<String> options) {
        super(x, y, width, height, Text.empty());
        this.options = options;
        this.currentIndex = 0;
        this.flat = false;
        this.animateElevation = false;
        recreateButtons();
    }

    protected void recreateButtons() {
        tabButtons.clear();
        if (options == null) {
            return;
        }

        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            AnimatedButton button = new AnimatedButton(0, 0, 0, 0, Text.of(options.get(i)));
            button.action = () -> handleTabClick(index);
            tabButtons.add(button);
        }
    }

    private void handleTabClick(int index) {
        boolean changed = false;
        if (multiSelect) {
            if (selectedIndices.contains(index)) {
                selectedIndices.remove(Integer.valueOf(index));
            } else {
                selectedIndices.add(index);
            }
            changed = true;
        } else {
            if (index != currentIndex) {
                currentIndex = index;
                changed = true;
            }
        }
        if (changed && onChange != null) {
            onChange.run();
        }
    }

    public int getCurrentIndex() { return currentIndex; }
    public String getCurrentOption() {
        return options.isEmpty() || multiSelect ? "" : options.get(currentIndex);
    }

    public List<Integer> getSelectedIndices() {
        return new ArrayList<>(selectedIndices);
    }

    public boolean isIndexSelected(int index) {
        if (index < 0 || index >= options.size()) return false;
        if (multiSelect) {
            return selectedIndices.contains(index);
        }
        return currentIndex == index;
    }

    public void setCurrentIndex(int index) {
        if (!multiSelect && index >= 0 && index < options.size()) {
            this.currentIndex = index;
        }
    }

    public void selectAll() {
        if (multiSelect) {
            selectedIndices.clear();
            for (int i = 0; i < options.size(); i++) {
                selectedIndices.add(i);
            }
        }
    }

    public void deselectAll() {
        if (multiSelect) {
            selectedIndices.clear();
        }
    }



    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        if (options == null || options.isEmpty() || tabButtons.isEmpty()) {
            return;
        }
        int segmentCount = tabButtons.size();
        int padding = 1;
        int totalPaddingWidth = (segmentCount - 1) * padding;
        int availableWidth = getWidth() - totalPaddingWidth;
        int segmentWidth = availableWidth / segmentCount;
        int remainder = availableWidth % segmentCount;

        int currentX = getX();
        for (int i = 0; i < segmentCount; i++) {
            AnimatedButton button = tabButtons.get(i);
            button.selectable = true;
            int currentSegmentWidth = segmentWidth + (i < remainder ? 1 : 0);

            button.setPosition(currentX, getY());
            button.setDimensions(currentSegmentWidth, getHeight());
            button.selected = isIndexSelected(i);
            button.render(ctx, mouseX, mouseY, deltaTime);

            currentX += currentSegmentWidth + padding;
        }
    }

    @Override protected void drawBorder(DrawContext ctx) {}
    @Override protected void drawBackground(DrawContext ctx) {}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active) return false;

        for (AnimatedButton b : tabButtons) {
            if (b.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }



    public void setOnChange(Runnable action) {
        this.onChange = action;
    }
}