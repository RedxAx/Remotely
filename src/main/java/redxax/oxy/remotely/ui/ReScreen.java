package redxax.oxy.remotely.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.ui.widgets.SquareButtonWidget;
import redxax.oxy.remotely.Render.ScrollBar;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.ui.widgets.AnimatedWidget.EntranceCorner.CENTER;
import static redxax.oxy.remotely.util.ImageUtil.drawBufferedImage;

public class ReScreen extends Screen {
    protected HeaderBuilder headerBuilder;
    protected Container container;

    protected ReScreen(Text title) {
        super(title);
        this.headerBuilder = new HeaderBuilder();
        this.container = new Container(0, 0, 0, 0);
    }

    public HeaderBuilder header() {
        return headerBuilder;
    }

    public Container createContainer(int x, int y, int width, int height) {
        return new Container(x, y, width, height);
    }

    public Container container() {
        return container;
    }

    public class Container {
        private final List<AnimatedWidget> widgets = new ArrayList<>();
        private int x;
        private int y;
        private int width;
        private int height;
        private int columns = 1;
        private int padding = 5;
        private float smoothOffset = 0;
        private float targetOffset = 0;
        private boolean canScroll = false;

        public Container(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Container pos(int x, int y) {
            this.x = x;
            this.y = y;
            updateWidgetPositions();
            return this;
        }

        public Container size(int width, int height) {
            this.width = width;
            this.height = height;
            updateWidgetPositions();
            return this;
        }

        public Container columns(int columns) {
            this.columns = Math.max(1, columns);
            updateWidgetPositions();
            return this;
        }

        public Container padding(int padding) {
            this.padding = padding;
            updateWidgetPositions();
            return this;
        }

        public Container addWidget(AnimatedWidget widget) {
            widgets.add(widget);
            widget.resetEntranceAnimation();
            addDrawableChild(widget);
            updateWidgetPositions();
            return this;
        }

        public Container removeWidget(ClickableWidget widget) {
            widgets.remove(widget);
            remove(widget);
            updateWidgetPositions();
            return this;
        }

        public Container clearWidgets() {
            for (ClickableWidget widget : widgets) {
                remove(widget);
            }
            widgets.clear();
            smoothOffset = 0;
            targetOffset = 0;
            return this;
        }

        private void updateWidgetPositions() {
            if (widgets.isEmpty()) return;

            int columnWidth = (width - padding * (columns + 1)) / columns;
            int currentRow = 0;
            int currentCol = 0;

            for (ClickableWidget widget : widgets) {
                int widgetX = x + padding + currentCol * (columnWidth + padding);
                int widgetY = y + padding + currentRow * (widget.getHeight() + padding);

                widget.setPosition(widgetX, widgetY);
                widget.setWidth(columnWidth);

                currentCol++;
                if (currentCol >= columns) {
                    currentCol = 0;
                    currentRow++;
                }
            }
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            smoothOffset += (targetOffset - smoothOffset) * globalScrollSpeed * deltaTime;

            context.fill(x, y, x + width, height, innerBackgroundColor);
            drawInnerBorder(context, x, y, width, height - y, innerBorderColor);
            drawOuterBorder(context, x, y, width, height - y, innerBackgroundColor);
            context.enableScissor(x, y, x + width, y + height);

            context.drawText(textRenderer, "Testing", x - 5, y + 5, globalTextColor, true);

            int totalHeight = calculateTotalHeight();
            int visibleHeight = height - 2 * padding;
            canScroll = totalHeight > visibleHeight;

            for (AnimatedWidget widget : widgets) {
                int adjustedY = widget.getY() - (int) smoothOffset;
                if (adjustedY + widget.getHeight() >= y && adjustedY <= height) {
                    widget.setY(adjustedY);
                    widget.renderWidget(context, mouseX, mouseY, delta);
                }
            }
            context.disableScissor();
            if (smoothOffset > 2) {
                context.fillGradient(x, y, x + width, y + 10, innerBackgroundColor, 0x00000000);
            }
            if (smoothOffset < Math.max(0, totalHeight - visibleHeight)) {
                context.fillGradient(x, height - 10, x + width, height, 0x00000000, innerBackgroundColor);
            }
            if (canScroll) {
                ScrollBar.render(context, ReScreen.this, mouseX, mouseY, totalHeight, targetOffset);
            }

            targetOffset = ScrollBar.getPendingOffset();
            targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, totalHeight - visibleHeight)));
        }

        private int calculateTotalHeight() {
            if (widgets.isEmpty()) return 0;

            int rows = (int) Math.ceil((double) widgets.size() / columns);
            int widgetHeight = widgets.get(0).getHeight();
            return padding + rows * (widgetHeight + padding);
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= height) {
                if (canScroll) {
                    int widgetHeight = widgets.isEmpty() ? 20 : widgets.get(0).getHeight();
                    targetOffset -= (float) (verticalAmount * widgetHeight * 0.5f);

                    int totalHeight = calculateTotalHeight();
                    int visibleHeight = height - 2 * padding;
                    targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, totalHeight - visibleHeight)));

                    ScrollBar.setPendingOffset(targetOffset);
                    return true;
                }
            }
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (canScroll && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= height) {
                int totalHeight = calculateTotalHeight();
                return ScrollBar.handleMouseDragged(ReScreen.this, (int) mouseY, totalHeight);
            }
            return false;
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (canScroll && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= height) {
                int totalHeight = calculateTotalHeight();
                return ScrollBar.handleMousePressed(ReScreen.this, (int) mouseX, (int) mouseY, totalHeight, smoothOffset);
            }
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (canScroll) {
                return ScrollBar.handleMouseReleased();
            }
            return false;
        }

        public boolean isCanScroll() {
            return canScroll;
        }

        public List<ClickableWidget> getWidgets() {
            return new ArrayList<>(widgets);
        }

        public int getPadding() {
            return padding;
        }
    }

    public class HeaderBuilder {
        private final List<SquareButtonWidget> leftButtons = new ArrayList<>();
        private final List<SquareButtonWidget> rightButtons = new ArrayList<>();
        private Position position = Position.TOP;
        private int headerSize = 30;
        private boolean visible = true;

        public enum Position {
            TOP, BOTTOM, LEFT, RIGHT
        }
        public void nextPosition() {
            switch (position) {
                case TOP -> position = Position.RIGHT;
                case RIGHT -> position = Position.BOTTOM;
                case BOTTOM -> position = Position.LEFT;
                case LEFT -> position = Position.TOP;
            }
            updateButtonPositions();
        }
        public HeaderBuilder position(Position pos) {
            this.position = pos;
            return this;
        }
        public HeaderBuilder size(int size) {
            this.headerSize = size;
            return this;
        }
        public HeaderBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }
        public HeaderBuilder addLeft(BufferedImage image, Runnable action, String hint) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().image(image).hint(hint).onClick(action).entranceCorner(CENTER).entranceAnimationStrength(.6f).build();
            leftButtons.add(button);
            return this;
        }
        public HeaderBuilder addLeft(String imagePath, Runnable action, String hint) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().imagePath(imagePath).hint(hint).onClick(action).entranceCorner(CENTER).entranceAnimationStrength(.6f).build();
            leftButtons.add(button);
            return this;
        }
        public HeaderBuilder addRight(BufferedImage image, Runnable action, String hint) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().image(image).hint(hint).onClick(action).entranceCorner(CENTER).entranceAnimationStrength(.6f).build();
            rightButtons.add(button);
            return this;
        }
        public HeaderBuilder addRight(String imagePath, Runnable action, String hint) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().imagePath(imagePath).hint(hint).onClick(action).entranceCorner(CENTER).entranceAnimationStrength(.6f).build();
            rightButtons.add(button);
            return this;
        }
        public void build() {
            clearHeaderWidgets();
            if (!visible) return;
            updateButtonPositions();
            for (SquareButtonWidget button : leftButtons) {
                addDrawableChild(button);
            }
            for (SquareButtonWidget button : rightButtons) {
                addDrawableChild(button);
            }
        }
        private void clearHeaderWidgets() {
            List<SquareButtonWidget> toRemove = new ArrayList<>();
            for (var child : children()) {
                if (child instanceof SquareButtonWidget) {
                    toRemove.add((SquareButtonWidget) child);
                }
            }
            for (SquareButtonWidget widget : toRemove) {
                remove(widget);
            }
        }
        private void updateButtonPositions() {
            switch (position) {
                case TOP -> updateTopPositions();
                case BOTTOM -> updateBottomPositions();
                case LEFT -> updateLeftPositions();
                case RIGHT -> updateRightPositions();
            }
            for (SquareButtonWidget button : leftButtons) {
                button.resetEntranceAnimation();
            }
            for (SquareButtonWidget button : rightButtons) {
                button.resetEntranceAnimation();
            }
        }
        private void updateTopPositions() {
            int leftX = 5;
            for (SquareButtonWidget button : leftButtons) {
                button.setPosition(leftX, 5);
                leftX += 23;
            }
            int rightX = width - 23;
            for (SquareButtonWidget button : rightButtons) {
                button.setPosition(rightX, 5);
                rightX -= 23;
            }
        }
        private void updateBottomPositions() {
            int leftX = 5;
            int y = height - headerSize + 5;
            for (SquareButtonWidget button : leftButtons) {
                button.setPosition(leftX, y);
                leftX += 23;
            }
            int rightX = width - 23;
            for (SquareButtonWidget button : rightButtons) {
                button.setPosition(rightX, y);
                rightX -= 23;
            }
        }
        private void updateLeftPositions() {
            int topY = 5;
            for (SquareButtonWidget button : leftButtons) {
                button.setPosition(5, topY);
                topY += 23;
            }
            int bottomY = height - 23;
            for (SquareButtonWidget button : rightButtons) {
                button.setPosition(5, bottomY);
                bottomY -= 23;
            }
        }
        private void updateRightPositions() {
            int x = width - headerSize + 5;
            int topY = 5;
            for (SquareButtonWidget button : leftButtons) {
                button.setPosition(x, topY);
                topY += 23;
            }
            int bottomY = height - 23;
            for (SquareButtonWidget button : rightButtons) {
                button.setPosition(x, bottomY);
                bottomY -= 23;
            }
        }
        private void renderHeaders(DrawContext context, int mouseX, int mouseY) {
            if (!visible) return;
            switch (position) {
                case TOP -> renderTopHeader(context);
                case BOTTOM -> renderBottomHeader(context);
                case LEFT -> renderLeftHeader(context);
                case RIGHT -> renderRightHeader(context);
            }
        }
        private void renderTopHeader(DrawContext context) {
            context.fill(0, 0, width, headerSize, innerBackgroundColor);
            drawInnerBorder(context, 0, 0, width, headerSize, innerBorderColor);
            drawOuterBorder(context, 0, 0, width, headerSize, innerBackgroundColor);
        }
        private void renderBottomHeader(DrawContext context) {
            int y = height - headerSize;
            context.fill(0, y, width, height, innerBackgroundColor);
            drawInnerBorder(context, 0, y, width, headerSize, innerBorderColor);
            drawOuterBorder(context, 0, y, width, headerSize, innerBackgroundColor);
        }
        private void renderLeftHeader(DrawContext context) {
            context.fill(0, 0, headerSize, height, innerBackgroundColor);
            drawInnerBorder(context, 0, 0, headerSize, height, innerBorderColor);
            drawOuterBorder(context, 0, 0, headerSize, height, innerBackgroundColor);
        }
        private void renderRightHeader(DrawContext context) {
            int x = width - headerSize;
            context.fill(x, 0, width, height, innerBackgroundColor);
            drawInnerBorder(context, x, 0, headerSize, height, innerBorderColor);
            drawOuterBorder(context, x, 0, headerSize, height, innerBackgroundColor);
        }
    }

    public void hideButton(int... indexPlusOne) {
        for (int i : indexPlusOne) {
            if (i < 0) {
                int idx = Math.abs(i) - 1;
                if (idx < headerBuilder.leftButtons.size()) {
                    headerBuilder.leftButtons.get(idx).visible = false;
                }
            } else {
                int idx = i - 1;
                if (idx >= 0 && idx < headerBuilder.rightButtons.size()) {
                    headerBuilder.rightButtons.get(idx).visible = false;
                }
            }
        }
        headerBuilder.build();
    }

    public void showButton(int... indexPlusOne) {
        for (int i : indexPlusOne) {
            if (i < 0) {
                int idx = Math.abs(i) - 1;
                if (idx < headerBuilder.leftButtons.size()) {
                    headerBuilder.leftButtons.get(idx).visible = true;
                }
            } else {
                int idx = i - 1;
                if (idx >= 0 && idx < headerBuilder.rightButtons.size()) {
                    headerBuilder.rightButtons.get(idx).visible = true;
                }
            }
        }
        headerBuilder.build();
    }

    public void showAllButtons() {
        for (SquareButtonWidget button : headerBuilder.leftButtons) {
            button.visible = true;
        }
        for (SquareButtonWidget button : headerBuilder.rightButtons) {
            button.visible = true;
        }
        headerBuilder.build();
    }

    @Override
    protected void init() {
        super.init();
        if (headerBuilder != null) {
            headerBuilder.build();
        }
        this.container = new Container(0, 0, this.width, this.height);
        AnimatedWidget.setCornerSpeedMultiplier(CENTER, .3f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (container != null) {
            container.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        if (wallpaper && windowsBackground != null) {
            drawBufferedImage(context, windowsBackground, 0, 0, this.width, this.height);
        } else if (!background) {
            context.fill(0, 0, width, height, backgroundColor);
        }
        headerBuilder.renderHeaders(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double verticalAmount) {
        if (container.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (container.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (container.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (container.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        if (headerBuilder != null) {
            headerBuilder.updateButtonPositions();
        }
    }
}
