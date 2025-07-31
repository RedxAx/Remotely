package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import redxax.oxy.remotely.Render;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.config.Config.deltaTime;
import static redxax.oxy.remotely.config.Config.globalDarkTextColor;
import static redxax.oxy.remotely.config.Config.globalScrollSpeed;
import static redxax.oxy.remotely.config.Config.globalTextColor;
import static redxax.oxy.remotely.config.Config.inClickableBackgroundColor;
import static redxax.oxy.remotely.config.Config.innerBackgroundColor;
import static redxax.oxy.remotely.config.Config.shadow;

public class PopupWidget extends AnimatedWidget {

    private final String title;
    public final List<PopupRow> rows = new ArrayList<>();
    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;
    private int contentHeight = 0;
    private boolean isDragging = false;
    private int dragStartY;
    private int dragStartX;
    private AnimatedButton closeButton;
    private Runnable onClose;

    private boolean resizable = false;
    private boolean isResizing = false;
    private int resizeEdge = 0;
    private int minWidth = 0, minHeight = 0;

    private ClickableWidget focusedWidget;

    private boolean expandWithDropdowns = true;
    private float currentAnimatedHeight;

    private boolean collapseOnClose = false;
    private boolean isCollapsed = false;

    private static final int TITLE_HEIGHT = 16;
    private static final int PADDING = 6;
    private static final int LABEL_HEIGHT = 12;
    private static final int FIELD_SPACING = 8;
    private static final int RESIZE_HANDLE_SIZE = 5;

    public static class PopupRow {
        final String label;
        public List<ClickableWidget> widgets;
        final int baseHeight;
        final boolean stretchWidgets;
        final boolean anchorRight;

        PopupRow(String label, List<ClickableWidget> widgets, int baseHeight, boolean stretchWidgets, boolean anchorRight) {
            this.label = label;
            this.widgets = new ArrayList<>(widgets);
            this.baseHeight = baseHeight;
            this.stretchWidgets = stretchWidgets;
            this.anchorRight = anchorRight;
        }

        int getCurrentHeight() {
            int extraHeight = 0;
            for (ClickableWidget w : widgets) {
                if (w instanceof DropDownWidget) {
                    extraHeight = Math.max(extraHeight, ((DropDownWidget<?>) w).getAnimatedHeight());
                }
            }

            int markdownHeight = -1;

            if (markdownHeight != -1) {
                int initialWidgetHeight = baseHeight - (label.isEmpty() ? 0 : LABEL_HEIGHT) - FIELD_SPACING;
                return (baseHeight - initialWidgetHeight + markdownHeight) + extraHeight;
            }

            return baseHeight + extraHeight;
        }
    }

    public static class Builder extends AnimatedWidget.Builder<PopupWidget, Builder> {
        public Builder(String title) {
            super(new PopupWidget((MinecraftClient.getInstance().currentScreen.width / 2) - 200, (MinecraftClient.getInstance().currentScreen.height / 2 - 150), 400, 300, title));
        }

        @Override
        public Builder size(int width, int height) {
            super.size(width, height);
            super.pos(MinecraftClient.getInstance().currentScreen.width / 2 - width / 2, MinecraftClient.getInstance().currentScreen.height / 2 - height / 2);
            return this;
        }

        public Builder onClose(Runnable action) {
            widget.onClose = action;
            return this;
        }

        public Builder setResizable(boolean resizable) {
            widget.resizable = resizable;
            return this;
        }

        public Builder enableCollapseOnClose(boolean collapseOnClose) {
            widget.collapseOnClose = collapseOnClose;
            return this;
        }

        public Builder setExpandWithDropdowns(boolean expandWithDropdowns) {
            widget.expandWithDropdowns = expandWithDropdowns;
            return this;
        }

        public Builder setMinSize(int minWidth, int minHeight) {
            widget.minWidth = minWidth;
            widget.minHeight = minHeight;
            return this;
        }

        public Builder addRow(String label, boolean stretch, int height, ClickableWidget... widgets) {
            return addRow(label, stretch, false, height, widgets);
        }

        public Builder addRow(String label, boolean stretch, boolean anchorRight, int height, ClickableWidget... widgets) {
            widget.addRow(label, Arrays.asList(widgets), height, stretch, anchorRight);
            return this;
        }

        public Builder addWidget(String label, ClickableWidget w, int fieldHeight) {
            widget.addRow(label, Collections.singletonList(w), fieldHeight, true, false);
            return this;
        }

        public Builder addTextField(String label, String value, Consumer<String> callback) {
            TextInputWidget.Builder builder = new TextInputWidget.Builder().text(value);
            TextInputWidget field = builder.widget;
            if (callback != null) {
                builder.onChange(() -> callback.accept(field.getText()));
            }
            return addRow(label, true, 20, builder.build());
        }

        public Builder addTextArea(String label, String value, int height, Consumer<String> callback) {
            TextAreaWidget.Builder builder = new TextAreaWidget.Builder().text(value);
            if (callback != null) {
                builder.onChange(callback);
            }
            return addRow(label, true, height, builder.build());
        }

        public Builder addToggle(String label, boolean value, Consumer<Boolean> callback) {
            ToggleWidget.Builder builder = new ToggleWidget.Builder().toggled(value);
            ToggleWidget field = builder.widget;
            if (callback != null) {
                builder.onChange(() -> callback.accept(field.getValue()));
            }
            return addRow(label, false, 18, builder.build());
        }

        public <T> Builder addDropdown(String label, List<T> items, T selected, Function<T, String> displayFunc, Consumer<T> callback) {
            DropDownWidget<T> field = new DropDownWidget.Builder<>(items)
                    .displayFunction(displayFunc)
                    .selectedItem(selected)
                    .onSelectionChanged(callback)
                    .size(180, 18)
                    .build();
            return addRow(label, true, 18, field);
        }

        public Builder addTabSwitch(String label, List<String> options, int selected, Consumer<Integer> callback) {
            TabSwitchWidget.Builder builder = new TabSwitchWidget.Builder().options(options).currentIndex(selected);
            TabSwitchWidget field = builder.widget;
            if (callback != null) {
                builder.onChange(() -> callback.accept(field.getCurrentIndex()));
            }
            return addRow(label, true, 20, builder.build());
        }

        public Builder addScrollSelector(String label, List<String> options, int selected, Consumer<Integer> callback) {
            ScrollSelectorWidget.Builder builder = new ScrollSelectorWidget.Builder().options(options).selectedIndex(selected);
            ScrollSelectorWidget field = builder.widget;
            if (callback != null) {
                builder.onChange(() -> callback.accept(field.getSelectedIndex()));
            }
            return addRow(label, true, 20, builder.build());
        }

        public Builder addDoubleSlider(String label, double value, Consumer<Double> callback) {
            DoubleSliderWidget.Builder builder = new DoubleSliderWidget.Builder().value(value);
            DoubleSliderWidget field = builder.widget;
            if (callback != null) {
                builder.onChange(() -> callback.accept(field.getValue()));
            }
            return addRow(label, true, 20, builder.build());
        }

        public Builder addIconButton(String label, String iconPath, Runnable action) {
            IconButton button = new IconButton.Builder().imagePath(iconPath).onClick(action).build();
            return addRow(label, false, 20, button);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }


    public PopupWidget(int x, int y, int width, int height, String title) {
        super(x, y, width, height, Text.empty());
        this.title = title;
        this.currentAnimatedHeight = height;
        this.animateLayout = enableHoverColors = animateElevation = entranceAnimationEnabled = false;
        this.entranceAnimationStrength = 10f;
        this.entranceAnimationSpeed = 2f;
        this.setLayer(490);
        this.closeButton = new AnimatedButton.Builder()
                .onClick(() -> {
                    if (collapseOnClose) {
                        isCollapsed = !isCollapsed;
                        if (isCollapsed) {
                            closeButton.accentType = (AccentType.NICE);
                        } else {
                            closeButton.accentType = (AccentType.DANGER);
                        }
                    } else {
                        if (onClose != null) onClose.run();
                        this.visible = false;
                        resetEntranceAnimation();
                    }
                })
                .accentType(AccentType.DANGER).animateElevation(false)
                .size(12, 8)
                .build();
        updateLayout();
    }

    private void setFocusedWidget(ClickableWidget widget) {
        if (this.focusedWidget != null) {
            this.focusedWidget.setFocused(false);
        }
        this.focusedWidget = widget;
        if (this.focusedWidget != null) {
            this.focusedWidget.setFocused(true);
        }
    }


    public void addRow(String label, List<ClickableWidget> widgets, int height, boolean stretch) {
        addRow(label, widgets, height, stretch, false);
    }

    public void addRow(String label, List<ClickableWidget> widgets, int height, boolean stretch, boolean anchorRight) {
        for (ClickableWidget widget : widgets) {
            if (widget instanceof AnimatedWidget) {
                ((AnimatedWidget) widget).animateElevation = entranceAnimationEnabled = false;
            }
        }
        this.rows.add(new PopupRow(label, widgets, height + (label.isEmpty() ? 0 :  LABEL_HEIGHT) + FIELD_SPACING, stretch, !stretch && anchorRight));
        updateLayout();
    }

    public void clearRows() {
        this.rows.clear();
        setFocusedWidget(null);
        scrollOffset = 0f;
        targetScrollOffset = 0f;
        updateLayout();
    }

    public void replaceWidget(ClickableWidget oldWidget, ClickableWidget newWidget) {
        for (PopupRow row : rows) {
            int index = row.widgets.indexOf(oldWidget);
            if (index != -1) {
                row.widgets.set(index, newWidget);
                if (newWidget instanceof AnimatedWidget) {
                    ((AnimatedWidget) newWidget).animateElevation = entranceAnimationEnabled = false;
                }
                updateLayout();
                return;
            }
        }
    }

    private void updateLayout() {
        contentHeight = 0;
        for (PopupRow row : rows) {
            contentHeight += row.getCurrentHeight();
        }
        contentHeight += PADDING;

        int availableWidth = getWidth() - PADDING * 2;
        for (PopupRow row : rows) {
            if (row.stretchWidgets && !row.widgets.isEmpty()) {
                int widgetWidth = (availableWidth - (row.widgets.size() - 1) * PADDING) / row.widgets.size();
                for (ClickableWidget w : row.widgets) {
                    w.setWidth(widgetWidth);
                }
            }
        }
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        updateLayout();
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        clampScroll();
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    public void tick() {
        super.tick();
        updateLayout();

        if (!isResizing) {
            boolean shouldAnimate = false;
            int targetHeight = getHeight();

            if (isCollapsed) {
                shouldAnimate = true;
                targetHeight = TITLE_HEIGHT;
            } else {
                int expandedHeight = TITLE_HEIGHT + contentHeight + PADDING;
                expandedHeight = Math.max(minHeight, expandedHeight);
                if (MinecraftClient.getInstance().currentScreen != null) {
                    expandedHeight = Math.min(expandedHeight, MinecraftClient.getInstance().currentScreen.height - getY() - 20);
                }

                if (expandWithDropdowns) {
                    shouldAnimate = true;
                    targetHeight = expandedHeight;
                } else if (collapseOnClose && getHeight() <= TITLE_HEIGHT) {
                    shouldAnimate = true;
                    targetHeight = expandedHeight;
                }
            }

            if (shouldAnimate) {
                currentAnimatedHeight += (targetHeight - currentAnimatedHeight) * globalExpandSpeed * deltaTime;
                if (Math.abs(currentAnimatedHeight - targetHeight) < 1f) {
                    currentAnimatedHeight = targetHeight;
                }
                setHeight((int) currentAnimatedHeight);
            }
        }

        scrollOffset += (targetScrollOffset - scrollOffset) * globalScrollSpeed * deltaTime;
        if (Math.abs(targetScrollOffset - scrollOffset) < 0.5f) {
            scrollOffset = targetScrollOffset;
        }
        clampScroll();

        if (!isCollapsed) {
            for (PopupRow row : rows) {
                for (ClickableWidget widget : row.widgets) {
                    if (widget instanceof AnimatedWidget) {
                        ((AnimatedWidget) widget).tick();
                    }
                }
            }
        }
        closeButton.tick();
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 9);
        super.renderWidget(ctx, mouseX, mouseY, delta);
        ctx.getMatrices().pop();
    }

    @Override
    protected void drawBackground(DrawContext ctx) {
        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), innerBackgroundColor);
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + TITLE_HEIGHT, inClickableBackgroundColor);
        Render.drawInnerBorder(ctx, getX(), getY(), getWidth(), TITLE_HEIGHT, borderColor);
        ctx.drawText(tr, title, getX() + 4, getY() + 4, globalTextColor, shadow);

        closeButton.setPosition(getX() + getWidth() - PADDING - 10, getY() + 3);
        closeButton.render(ctx, mouseX, mouseY, 0);

        if (isCollapsed) return;

        int contentY = getY() + TITLE_HEIGHT;
        int contentW = getWidth();
        int contentH = getHeight() - TITLE_HEIGHT;
        ctx.enableScissor(getX(), contentY + 1, getX() + contentW, contentY + contentH - 3);

        int currentY = contentY + PADDING - (int) scrollOffset;
        for (PopupRow row : rows) {
            if (row.label != null && !row.label.isEmpty()) {
                ctx.drawText(tr, row.label, getX() + PADDING, currentY, globalDarkTextColor, false);
            }
            int widgetY = currentY + (row.label != null && !row.label.isEmpty() ? LABEL_HEIGHT : 0);

            int currentX;
            if (row.anchorRight) {
                int totalWidgetsWidth = 0;
                for (ClickableWidget widget : row.widgets) {
                    totalWidgetsWidth += widget.getWidth();
                }
                totalWidgetsWidth += Math.max(0, row.widgets.size() - 1) * PADDING;
                currentX = getX() + getWidth() - PADDING - totalWidgetsWidth;
            } else {
                currentX = getX() + PADDING;
            }

            for (ClickableWidget widget : row.widgets) {
                widget.setPosition(currentX, widgetY);
                ctx.enableScissor(getX(), contentY, getX() + contentW, contentY + contentH);
                widget.render(ctx, mouseX, mouseY, 0f);
                ctx.disableScissor();
                currentX += widget.getWidth() + PADDING;
            }

            currentY += row.getCurrentHeight();
        }

        ctx.disableScissor();
    }

    private int getResizeEdge(double mouseX, double mouseY) {
        if (!resizable) return 0;

        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean onLeft = mouseX >= x - RESIZE_HANDLE_SIZE && mouseX <= x + RESIZE_HANDLE_SIZE;
        boolean onRight = mouseX >= x + w - RESIZE_HANDLE_SIZE && mouseX <= x + w + RESIZE_HANDLE_SIZE;
        boolean onTop = mouseY >= y - RESIZE_HANDLE_SIZE && mouseY <= y + RESIZE_HANDLE_SIZE;
        boolean onBottom = mouseY >= y + h - RESIZE_HANDLE_SIZE && mouseY <= y + h + RESIZE_HANDLE_SIZE;

        if (onLeft && onTop) return 8;
        if (onRight && onTop) return 7;
        if (onLeft && onBottom) return 6;
        if (onRight && onBottom) return 5;
        if (onLeft) return 1;
        if (onRight) return 2;
        if (onTop) return 3;
        if (onBottom) return 4;

        return 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        resizeEdge = getResizeEdge(mouseX, mouseY);
        if (resizable && resizeEdge != 0 && button == 0) {
            isResizing = true;
            dragStartX = (int) mouseX;
            dragStartY = (int) mouseY;
            return true;
        }

        if (closeButton.isMouseOver(mouseX, mouseY)) {
            closeButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (isMouseOver(mouseX, mouseY)) {
            if (mouseY < getY() + TITLE_HEIGHT) {
                if (button == 0) {
                    isDragging = true;
                    dragStartX = (int) (mouseX - getX());
                    dragStartY = (int) (mouseY - getY());
                }
                return true;
            }

            if (isCollapsed) return true;

            for (int i = rows.size() - 1; i >= 0; i--) {
                PopupRow row = rows.get(i);
                for (ClickableWidget widget : row.widgets) {
                    if (widget.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }

            this.setFocusedWidget(null);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        isResizing = false;
        resizeEdge = 0;

        if (!visible) return false;

        if (isMouseOver(mouseX, mouseY) && !isCollapsed) {
            for (PopupRow row : rows) {
                for (ClickableWidget widget : row.widgets) {
                    if (widget.mouseReleased(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && button == 0) {
            setX((int) mouseX - dragStartX);
            setY(Math.max(0, (int) mouseY - dragStartY));
            return true;
        }
        if (isResizing && button == 0) {
            int newX = getX(), newY = getY(), newW = getWidth(), newH = getHeight();
            int dx = (int) (mouseX - dragStartX);
            int dy = (int) (mouseY - dragStartY);

            if (resizeEdge == 1 || resizeEdge == 6 || resizeEdge == 8) {
                if (getWidth() - dx >= minWidth) {
                    newX += dx;
                    newW -= dx;
                }
            }
            if (resizeEdge == 2 || resizeEdge == 5 || resizeEdge == 7) {
                newW += dx;
            }
            if (resizeEdge == 3 || resizeEdge == 7 || resizeEdge == 8) {
                if (getHeight() - dy >= minHeight) {
                    newY += dy;
                    newH -= dy;
                }
            }
            if (resizeEdge == 4 || resizeEdge == 5 || resizeEdge == 6) {
                newH += dy;
            }

            if (newW < minWidth) newW = minWidth;
            if (newH < minHeight) newH = minHeight;

            setX(newX);
            setY(Math.max(0, newY));
            setWidth(newW);
            setHeight(newH);

            if (expandWithDropdowns) {
                currentAnimatedHeight = newH;
            }

            dragStartX = (int) mouseX;
            dragStartY = (int) mouseY;
            return true;
        }
        if (isMouseOver(mouseX, mouseY) && !isCollapsed) {
            for (PopupRow row : rows) {
                for (ClickableWidget widget : row.widgets) {
                    if (widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!visible) return false;
        if (mouseX >= getX() && mouseX <= getX() + getWidth() && mouseY >= getY() + TITLE_HEIGHT && mouseY <= getY() + getHeight()) {
            for (PopupRow row : rows) {
                for (ClickableWidget widget : row.widgets) {
                    if (widget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                        return true;
                    }
                }
            }
            int contentH = getHeight() - TITLE_HEIGHT;
            if (contentHeight > contentH) {
                targetScrollOffset -= (float) (verticalAmount * 20f);
                clampScroll();
                return true;
            }
        }
        return false;
    }

    private void clampScroll() {
        int contentH = getHeight() - TITLE_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - contentH);
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || isCollapsed) return false;

        if (this.focusedWidget != null && this.focusedWidget.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible || isCollapsed) return false;

        if (this.focusedWidget != null && this.focusedWidget.charTyped(chr, modifiers)) {
            return true;
        }

        return super.charTyped(chr, modifiers);
    }

    public void show() {
        this.visible = true;
        this.active = true;
    }

    public void hide() {
        this.visible = false;
        this.active = false;
    }
}