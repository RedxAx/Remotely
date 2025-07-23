package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import redxax.oxy.remotely.Render;
import redxax.oxy.remotely.util.Sound;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class DropDownWidget<T> extends AnimatedWidget {
    private final List<T> items;
    private T selectedItem;
    private boolean expanded = false;
    private float dropdownAnimationProgress = 0f;
    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;
    private int itemHeight = 20;
    private int maxVisibleItems = 8;
    private int hoverIndex = -1;
    private Function<T, String> displayFunction;
    private Consumer<T> onSelectionChanged;

    public DropDownWidget(List<T> items, int x, int y, int width, int height, Function<T, String> displayFunction) {
        super(x, y, width, height, Text.empty());
        this.items = items;
        this.displayFunction = displayFunction;
        this.animateColor = true;
        this.animateElevation = true;
        if (!items.isEmpty()) {
            selectedItem = items.get(0);
        }
    }

    public int getAnimatedHeight() {
        return (int)(getVisibleDropdownHeight() * dropdownAnimationProgress);
    }

    public static class Builder<T> extends AnimatedWidget.Builder<DropDownWidget<T>, Builder<T>> {
        private final List<T> items;
        private Function<T, String> displayFunction = Object::toString;

        public Builder(List<T> items) {
            super(new DropDownWidget<>(items, 0, 0, 200, 30, Object::toString));
            this.items = items;
        }

        @Override
        protected Builder<T> self() {
            return this;
        }

        public Builder<T> displayFunction(Function<T, String> func) {
            widget.displayFunction = func;
            return this;
        }

        public Builder<T> selectedItem(T item) {
            widget.selectedItem = item;
            return this;
        }

        public Builder<T> maxVisibleItems(int count) {
            widget.maxVisibleItems = count;
            return this;
        }

        public Builder<T> itemHeight(int height) {
            widget.itemHeight = height;
            return this;
        }

        public Builder<T> onSelectionChanged(Consumer<T> callback) {
            widget.onSelectionChanged = callback;
            return this;
        }

        public Builder<T> pos(int x, int y) {
            widget.setX(x);
            widget.setY(y);
            return this;
        }

        public Builder<T> size(int width, int height) {
            widget.setWidth(width);
            widget.setHeight(height);
            return this;
        }

        public DropDownWidget<T> build() {
            return widget;
        }
    }

    public T getSelectedItem() {
        return selectedItem;
    }

    public void setSelectedItem(T item) {
        if (items.contains(item)) {
            T oldItem = this.selectedItem;
            this.selectedItem = item;
            if (onSelectionChanged != null && !item.equals(oldItem)) {
                onSelectionChanged.accept(item);
            }
        }
    }

    public void setOnSelectionChanged(Consumer<T> callback) {
        this.onSelectionChanged = callback;
    }

    @Override
    public void tick() {
        super.tick();
        float targetProgress = expanded ? 1.0f : 0.0f;
        dropdownAnimationProgress += (targetProgress - dropdownAnimationProgress) * globalExpandSpeed * deltaTime;
        scrollOffset += (targetScrollOffset - scrollOffset) * globalScrollSpeed * deltaTime;
    }

    private int getTotalContentHeight() {
        return items.size() * itemHeight;
    }

    private int getVisibleDropdownHeight() {
        return Math.min(items.size(), maxVisibleItems) * itemHeight;
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        String displayText = selectedItem != null ? displayFunction.apply(selectedItem) : "Select...";
        ctx.drawText(tr, displayText, getX() + 8, getY() + (getHeight() - tr.fontHeight) / 2, globalDarkTextColor, shadow);

        if (dropdownAnimationProgress > 0.01f) {
            int dropdownHeight = getVisibleDropdownHeight();
            int actualHeight = (int)(dropdownHeight * dropdownAnimationProgress);
            if (actualHeight <= 0) return;

            int dropdownY = getY() + getHeight();
            int totalHeight = getTotalContentHeight();

            ctx.fill(getX(), dropdownY, getX() + getWidth(), dropdownY + actualHeight, bgColor);
            Render.drawInnerBorder(ctx, getX(), dropdownY, getWidth(), actualHeight, borderColor);
            if (!flat) {
                Render.drawOuterBorder(ctx, getX(), dropdownY, getWidth(), actualHeight, bgColor);
            }

            int scrollbarWidth = 4;
            int contentWidth = getWidth() - scrollbarWidth;
            ctx.enableScissor(getX(), dropdownY, getX() + getWidth(), dropdownY + actualHeight);

            hoverIndex = -1;
            if (mouseX >= getX() && mouseX < getX() + contentWidth) {
                int relativeY = (int)(mouseY - dropdownY + scrollOffset);
                int index = relativeY / itemHeight;
                if (index >= 0 && index < items.size() && mouseY >= dropdownY && mouseY < dropdownY + actualHeight) {
                    hoverIndex = index;
                }
            }

            for (int i = 0; i < items.size(); i++) {
                T item = items.get(i);
                int itemY = dropdownY + i * itemHeight - (int)scrollOffset;
                if (itemY + itemHeight < dropdownY || itemY > dropdownY + actualHeight) continue;
                boolean isSelected = item.equals(selectedItem);
                boolean isHovered = i == hoverIndex;
                int highlightColor = getElementBackgroundColor(("item" + i).hashCode(), isHovered, isSelected, true, AccentType.DEFAULT);
                ctx.fill(getX(), itemY, getX() + contentWidth, itemY + itemHeight, highlightColor);
                ctx.drawText(tr, displayFunction.apply(item), getX() + 8, itemY + (itemHeight - tr.fontHeight) / 2, getTextColor(("text" + i).hashCode(), false, isSelected, isHovered, AccentType.DEFAULT), shadow);
            }

            Render.ScrollBar.render(ctx, null, mouseX, mouseY, totalHeight, scrollOffset, getX() + getWidth() - scrollbarWidth, dropdownY, scrollbarWidth, actualHeight);
            ctx.disableScissor();
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (mouseY < getY() + getHeight()) {
            playSound(Sound.CLICK);
            expanded = !expanded;
            if (expanded) {
                scrollOffset = 0;
                targetScrollOffset = 0;
            }
        } else if (expanded && dropdownAnimationProgress > 0.9f) {
            int dropdownY = getY() + getHeight();
            int dropdownHeight = getVisibleDropdownHeight();
            boolean needsScrollbar = items.size() > maxVisibleItems;
            int contentWidth = needsScrollbar ? getWidth() - 6 : getWidth();

            if (needsScrollbar && mouseX >= getX() + contentWidth) {
                Render.ScrollBar.handleMousePressed(null, (int)mouseX, (int)mouseY, getTotalContentHeight(), scrollOffset, getX() + getWidth() - 6, dropdownY, 6, dropdownHeight);
                targetScrollOffset = Render.ScrollBar.getPendingOffset();
            } else if (hoverIndex >= 0 && hoverIndex < items.size()) {
                T newSelectedItem = items.get(hoverIndex);
                T oldSelectedItem = selectedItem;
                selectedItem = newSelectedItem;
                playSound(Sound.CLICK);
                expanded = false;

                if (onSelectionChanged != null && !newSelectedItem.equals(oldSelectedItem)) {
                    onSelectionChanged.accept(newSelectedItem);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && visible && isMouseOver(mouseX, mouseY)) {
            onClick(mouseX, mouseY, button);
            return true;
        }
        if (expanded) {
            int dropdownY = getY() + getHeight();
            int dropdownHeight = getVisibleDropdownHeight();
            if (mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight && mouseX >= getX() && mouseX < getX() + getWidth()) {
                onClick(mouseX, mouseY, button);
                return true;
            }
            expanded = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (expanded && Render.ScrollBar.isDragging()) {
            int dropdownHeight = getVisibleDropdownHeight();
            Render.ScrollBar.handleMouseDragged(null, (int)mouseY, getTotalContentHeight(), dropdownHeight);
            targetScrollOffset = Render.ScrollBar.getPendingOffset();
            return true;
        }
        return false;
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (Render.ScrollBar.handleMouseReleased()) {
            targetScrollOffset = Render.ScrollBar.getPendingOffset();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (expanded && mouseX >= getX() && mouseX <= getX() + getWidth() && mouseY >= getY() + getHeight() && mouseY <= getY() + getHeight() + getVisibleDropdownHeight()) {
            int totalContentHeight = getTotalContentHeight();
            int visibleHeight = getVisibleDropdownHeight();
            if (totalContentHeight > visibleHeight) {
                targetScrollOffset -= (float) (verticalAmount * 20f);
                targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, totalContentHeight - visibleHeight));
                return true;
            }
        }
        return false;
    }
}