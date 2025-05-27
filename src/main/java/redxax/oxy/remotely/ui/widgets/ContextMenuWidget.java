package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.Render;
import redxax.oxy.remotely.config.Config;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;

public class ContextMenuWidget extends AnimatedWidget {
    private final List<MenuItem> items = new ArrayList<>();
    private final List<SquareButtonWidget> headerButtons = new ArrayList<>();
    private boolean open = false;
    private final Screen parent;
    private static final int ITEM_HEIGHT = 18;
    private static final int GAP = 1;
    private static final int MARGIN = 4;
    private static final int HEADER_HEIGHT = 26;
    private static final int BUTTON_GAP = 4;
    private static final int HEADER_PADDING = 2;
    private float headerWidthTarget = 0f;
    private float headerWidthCurrent = 0f;
    private boolean hasHeader = false;

    public static class MenuItem {
        final String label;
        final Runnable action;
        final String tooltipText;
        final boolean danger;
        final boolean nice;
        final boolean calm;
        final BufferedImage icon;
        final AnimatedWidget button;

        MenuItem(String label, Runnable action, String tooltipText, boolean danger, boolean nice, boolean calm) {
            this.label = label;
            this.action = action;
            this.tooltipText = tooltipText;
            this.danger = danger;
            this.nice = nice;
            this.calm = calm;
            this.icon = null;
            this.button = new AnimatedButton.ButtonBuilder().label(Text.literal(label)).onClick(action).tooltip(tooltipText).centered(false).entranceAnimationStrength(0.6f).build();
        }

        MenuItem(String label, BufferedImage icon, Runnable action, String tooltipText, boolean danger, boolean nice, boolean calm) {
            this.label = label;
            this.action = action;
            this.tooltipText = tooltipText;
            this.danger = danger;
            this.nice = nice;
            this.calm = calm;
            this.icon = icon;
            this.button = new IconButton.Builder().label(Text.literal(label)).image(icon).onClick(action).tooltip(tooltipText).centered(false).entranceAnimationStrength(0.6f).build();
        }
    }

    public static class Builder extends AnimatedWidget.Builder<ContextMenuWidget, Builder> {
        public Builder(Screen parent) {
            super(new ContextMenuWidget(parent));
        }

        public Builder addItem(String label, Runnable action, String tooltipText) {
            return addItem(label, action, tooltipText, false, false, false);
        }

        public Builder addItem(String label, Runnable action, String tooltipText, boolean danger, boolean nice, boolean calm) {
            widget.items.add(new MenuItem(label, action, tooltipText, danger, nice, calm));
            widget.recalculateWidth();
            return this;
        }

        public Builder addIconItem(String label, BufferedImage icon, Runnable action, String tooltipText) {
            return addIconItem(label, icon, action, tooltipText, false, false, false);
        }

        public Builder addIconItem(String label, BufferedImage icon, Runnable action, String tooltipText, boolean danger, boolean nice, boolean calm) {
            widget.items.add(new MenuItem(label, icon, action, tooltipText, danger, nice, calm));
            widget.recalculateWidth();
            return this;
        }

        public Builder addIconItem(String label, String iconPath, Runnable action, String tooltipText) {
            return addIconItem(label, iconPath, action, tooltipText, false, false, false);
        }

        public Builder addIconItem(String label, String iconPath, Runnable action, String tooltipText, boolean danger, boolean nice, boolean calm) {
            try {
                BufferedImage icon = loadResourceIcon(iconPath);
                return addIconItem(label, icon, action, tooltipText, danger, nice, calm);
            } catch (Exception e) {
                return addItem(label, action, tooltipText, danger, nice, calm);
            }
        }

        public Builder addHeaderButton(BufferedImage image, Runnable action, String tooltipText) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().image(image).onClick(action).entranceAnimationStrength(0.6f).build();
            widget.headerButtons.add(button);
            widget.hasHeader = true;
            widget.recalculateWidth();
            return this;
        }

        public Builder addHeaderButton(String imagePath, Runnable action, String tooltipText) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().imagePath(imagePath).onClick(action).entranceAnimationStrength(0.6f).build();
            widget.headerButtons.add(button);
            widget.hasHeader = true;
            widget.recalculateWidth();
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    private ContextMenuWidget(Screen parent) {
        super(0, 0, 100, 0, Text.empty());
        this.parent = parent;
        this.entranceAnimationEnabled = false;
        this.animateElevation = false;
    }

    private void recalculateWidth() {
        int maxWidth = 0;
        int totalHeight = 0;

        if (hasHeader) {
            headerWidthTarget = HEADER_PADDING * 2;
            for (SquareButtonWidget button : headerButtons) {
                headerWidthTarget += button.getWidth() + BUTTON_GAP;
            }
            if (!headerButtons.isEmpty()) {
                headerWidthTarget -= BUTTON_GAP;
            }
            totalHeight += HEADER_HEIGHT + GAP;
        }

        for (MenuItem item : items) {
            int width = tr.getWidth(item.label) + (item.button instanceof AnimatedButton ? 6 : 6 + 20);
            if (width > maxWidth) {
                maxWidth = width;
            }
            totalHeight += ITEM_HEIGHT + GAP;
        }

        if (totalHeight > 0) {
            totalHeight -= GAP;
        }

        this.width = Math.max(40, maxWidth);
        this.height = totalHeight;
        updateButtonPositions();
    }

    public void show(int x, int y) {
        if (items.isEmpty()) return;
        open = true;
        int menuX = x;
        int menuY = y;
        this.setX(x);
        this.setY(y - HEADER_HEIGHT);
        int screenWidth = parent.width;
        int screenHeight = parent.height;
        if (menuX + width > screenWidth) {
            menuX = (int) (screenWidth - headerWidthTarget);
            this.setX(menuX);
        }
        if (menuY + height > screenHeight) {
            menuY = screenHeight - height - MARGIN;
            this.setY(menuY);
        }
        this.entranceAnimationProgress = 0f;
        this.entranceAnimationStarted = false;
        this.entranceAnimationDelayCalculated = false;
        updateButtonPositions();
    }

    private void updateButtonPositions() {
        int currentY = 0;

        if (hasHeader) {
            int buttonX = 0;
            for (SquareButtonWidget button : headerButtons) {
                button.setX(buttonX);
                button.setY(0);
                buttonX += button.getWidth() + BUTTON_GAP;
            }
            currentY += HEADER_HEIGHT + GAP;
        }

        for (MenuItem item : items) {
            item.button.setX(0);
            item.button.setY(currentY);
            item.button.setWidth(width);
            item.button.setHeight(ITEM_HEIGHT);

            item.button.animateColor = true;
            item.button.setFocused(item.danger || item.nice || item.calm);

            currentY += ITEM_HEIGHT + GAP;
        }
    }

    public void hide() {
        open = false;
        for (MenuItem item : items) {
            item.button.resetEntranceAnimation();
        }
        for (SquareButtonWidget button : headerButtons) {
            button.resetEntranceAnimation();
        }
        headerWidthCurrent = 0;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }

    @Override
    protected void drawContent(DrawContext context, int mouseX, int mouseY) {
        if (!open) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 499);

        int currentY = 0;

        if (hasHeader && !headerButtons.isEmpty()) {
            headerWidthCurrent += (headerWidthTarget - headerWidthCurrent) * Config.globalExpandSpeed * Config.deltaTime;
            int headerBgColor = Config.getElementBackgroundColor("contextMenuHeader".hashCode() + this.hashCode(), false, false, false, false, false, false);
            int headerLeft = getX();
            int headerTop = getY();
            int headerRight = getX() + (int)headerWidthCurrent + HEADER_PADDING * 2;
            int headerBottom = getY() + HEADER_HEIGHT;
            context.fill(headerLeft, headerTop, headerRight, headerBottom, headerBgColor);
            Render.drawInnerBorder(context, headerLeft, headerTop, (int)headerWidthCurrent + HEADER_PADDING * 2, HEADER_HEIGHT, Config.getElementBorderColor("contextMenuHeader".hashCode() + this.hashCode(), false, false, false, false, false, false));
            Render.drawOuterBorder(context, headerLeft, headerTop, (int)headerWidthCurrent + HEADER_PADDING * 2, HEADER_HEIGHT, headerBgColor);
            context.enableScissor(headerLeft, headerTop, headerRight, headerBottom);

            int targetButtonsWidth = 0;
            for (int i = 0; i < headerButtons.size(); i++) {
                targetButtonsWidth += headerButtons.get(i).getWidth();
                if (i < headerButtons.size() - 1) {
                    targetButtonsWidth += BUTTON_GAP;
                }
            }
            int availableWidth = (int)headerWidthTarget;
            int buttonX = headerLeft + HEADER_PADDING + (availableWidth - targetButtonsWidth) / 2;
            int buttonY = (headerTop + (HEADER_HEIGHT - headerButtons.get(0).getHeight()) / 2) - 1;
            for (int i = 0; i < headerButtons.size(); i++) {
                SquareButtonWidget button = headerButtons.get(i);
                button.setX(buttonX);
                button.setY(buttonY);
                button.render(context, mouseX, mouseY, 0);
                buttonX += button.getWidth();
                if (i < headerButtons.size() - 1) {
                    buttonX += BUTTON_GAP;
                }
            }
            context.disableScissor();
            currentY += HEADER_HEIGHT + 6;
        }

        for (MenuItem item : items) {
            item.button.setX(getX());
            item.button.setY(getY() + currentY);
            item.button.render(context, mouseX, mouseY, 0);
            currentY += ITEM_HEIGHT + GAP;
        }

        context.getMatrices().pop();
    }

    @Override protected void drawBorder(DrawContext context) {}
    @Override protected void drawBackground(DrawContext context) {}


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || !visible || !active) return false;

        if (hasHeader) {
            int headerY = getY();
            if (mouseY >= headerY && mouseY <= headerY + HEADER_HEIGHT) {
                for (SquareButtonWidget headerButton : headerButtons) {
                    if (headerButton.isMouseOver(mouseX, mouseY)) {
                        headerButton.onClick(mouseX, mouseY, button);
                        this.hide();
                        return true;
                    }
                }
            }
        }
        for (MenuItem item : items) {
            if (item.button.isMouseOver(mouseX, mouseY)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    item.button.onClick(mouseX, mouseY, button);
                    this.hide();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!open) return false;
        for (MenuItem item : items) {
            if (item.button.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!open || !visible) return false;
        int currentY = this.getY();
        for (int i = 0; i < items.size(); i++) {
            boolean over = mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= currentY && mouseY < currentY + ITEM_HEIGHT;
            if (over) return true;
            currentY += ITEM_HEIGHT + GAP;
        }
        return false;
    }
}
