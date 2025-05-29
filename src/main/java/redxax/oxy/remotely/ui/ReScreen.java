package redxax.oxy.remotely.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.ui.widgets.SquareButtonWidget;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.ui.widgets.AnimatedWidget.EntranceCorner.CENTER;
import static redxax.oxy.remotely.util.ImageUtil.drawBufferedImage;

public class ReScreen extends Screen {
    protected HeaderBuilder headerBuilder;

    protected ReScreen(Text title) {
        super(title);
        this.headerBuilder = new HeaderBuilder();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
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

    public HeaderBuilder header() {
        return headerBuilder;
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
        AnimatedWidget.setCornerSpeedMultiplier(CENTER, .3f);
    }

    @Override
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        if (headerBuilder != null) {
            headerBuilder.updateButtonPositions();
        }
    }
}