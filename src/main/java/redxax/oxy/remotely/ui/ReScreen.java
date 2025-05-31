package redxax.oxy.remotely.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.ui.widgets.SquareButtonWidget;
import redxax.oxy.remotely.ui.widgets.AnimatedButton;
import redxax.oxy.remotely.ui.widgets.TextInputWidget;
import redxax.oxy.remotely.Render.ScrollBar;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.ui.widgets.AnimatedWidget.EntranceCorner.CENTER;
import static redxax.oxy.remotely.util.ImageUtil.drawBufferedImage;

public class ReScreen extends Screen {
    protected HeaderBuilder headerBuilder;
    protected Container container;
    protected Container sidePanel;
    protected TabsManager<?> tabsManager;

    protected ReScreen(Text title) {
        super(title);
        this.headerBuilder = new HeaderBuilder();
    }

    public HeaderBuilder header() {
        return headerBuilder;
    }

    public <T> TabsManager<T> tabs() {
        if (tabsManager == null) {
            tabsManager = new TabsManager<>();
        }
        return (TabsManager<T>) tabsManager;
    }

    public Container createContainer(int x, int y, int width, int height) {
        this.container = new Container(x, y, width, height);
        return container;
    }

    public Container container() {
        return container;
    }

    public class TabsManager<T> {
        private final List<Tab<T>> tabs = new ArrayList<>();
        private final Map<T, Object> stateCache = new HashMap<>();
        private int activeTabIndex = 0;
        private int x = 0;
        private int y = 0;
        private int width = 0;
        private int height = 30;
        private boolean visible = true;
        private boolean allowClose = true;
        private boolean allowRename = true;
        private boolean allowReorder = true;
        private boolean allowAdd = true;
        private boolean enableStateCache = false;
        private float scrollOffset = 0f;
        private float targetScrollOffset = 0f;
        private int tabPadding = 6;
        private int tabGap = 5;
        private SquareButtonWidget plusButton;
        private int renamingTabIndex = -1;
        private TextInputWidget renameWidget;
        private int draggingTabIndex = -1;
        private boolean dragging = false;
        private int dragOverIndex = -1;
        private float dragStartX = 0f;
        private float dragCurrentX = 0f;
        private Consumer<Tab<T>> onTabSelected;
        private Consumer<Tab<T>> onTabClosed;
        private Consumer<Tab<T>> onTabRenamed;
        private Runnable onTabAdded;
        private Consumer<List<Tab<T>>> onTabsReordered;

        public static class Tab<T> {
            private String name;
            private boolean unsaved;
            private final T data;
            private final int id;
            private AnimatedButton widget;
            private boolean visible = true;

            public Tab(String name, T data) {
                this.name = name;
                this.data = data;
                this.unsaved = false;
                this.id = System.identityHashCode(this);
            }

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public boolean isUnsaved() { return unsaved; }
            public void setUnsaved(boolean unsaved) { this.unsaved = unsaved; }
            public T getData() { return data; }
            public int getId() { return id; }
            public AnimatedButton getWidget() { return widget; }
            public boolean isVisible() { return visible; }
            public void setVisible(boolean visible) { this.visible = visible; }
        }

        public class Builder {
            public Builder position(int x, int y) {
                TabsManager.this.x = x;
                TabsManager.this.y = y;
                return this;
            }
            public Builder size(int width, int height) {
                TabsManager.this.width = width;
                TabsManager.this.height = height;
                return this;
            }
            public Builder visible(boolean visible) {
                TabsManager.this.visible = visible;
                return this;
            }
            public Builder allowClose(boolean allow) {
                TabsManager.this.allowClose = allow;
                return this;
            }
            public Builder allowRename(boolean allow) {
                TabsManager.this.allowRename = allow;
                return this;
            }
            public Builder allowReorder(boolean allow) {
                TabsManager.this.allowReorder = allow;
                return this;
            }
            public Builder allowAdd(boolean allow) {
                TabsManager.this.allowAdd = allow;
                return this;
            }
            public Builder enableStateCache(boolean enable) {
                TabsManager.this.enableStateCache = enable;
                return this;
            }
            public Builder onTabSelected(Consumer<Tab<T>> callback) {
                TabsManager.this.onTabSelected = callback;
                return this;
            }
            public Builder onTabClosed(Consumer<Tab<T>> callback) {
                TabsManager.this.onTabClosed = callback;
                return this;
            }
            public Builder onTabRenamed(Consumer<Tab<T>> callback) {
                TabsManager.this.onTabRenamed = callback;
                return this;
            }
            public Builder onTabAdded(Runnable callback) {
                TabsManager.this.onTabAdded = callback;
                return this;
            }
            public Builder onTabsReordered(Consumer<List<Tab<T>>> callback) {
                TabsManager.this.onTabsReordered = callback;
                return this;
            }
            public TabsManager<T> build() {
                if (allowAdd && plusButton == null) {
                    plusButton = new SquareButtonWidget.Builder()
                            .imagePath("/assets/remotely/icons/newTab.png")
                            .size(height, height)
                            .onClick(() -> { if (onTabAdded != null) onTabAdded.run(); })
                            .entranceCorner(CENTER)
                            .build();
                    addDrawableChild(plusButton);
                } else if (!allowAdd && plusButton != null) {
                    remove(plusButton);
                    plusButton = null;
                }
                updateLayout();
                return TabsManager.this;
            }
        }

        public Builder builder() {
            return new Builder();
        }

        public Tab<T> addTab(String name, T data) {
            Tab<T> tab = new Tab<>(name, data);
            tabs.add(tab);
            createTabWidget(tab);
            if (enableStateCache) {
                stateCache.put(data, null);
            }
            if (onTabAdded != null) {
                onTabAdded.run();
            }
            updateLayout();
            return tab;
        }

        public void removeTab(int index) {
            if (index >= 0 && index < tabs.size()) {
                Tab<T> tab = tabs.remove(index);
                if (tab.widget != null) remove(tab.widget);
                if (enableStateCache) stateCache.remove(tab.data);
                if (activeTabIndex >= tabs.size()) {
                    activeTabIndex = Math.max(0, tabs.size() - 1);
                }
                if (onTabClosed != null) onTabClosed.accept(tab);
                updateLayout();
            }
        }

        public void setActiveTab(int index) {
            if (index >= 0 && index < tabs.size()) {
                if (enableStateCache && activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
                    stateCache.put(tabs.get(activeTabIndex).data, captureCurrentState());
                }
                activeTabIndex = index;
                updateTabStates();
                if (enableStateCache) restoreFromState(tabs.get(index));
                if (onTabSelected != null) onTabSelected.accept(tabs.get(index));
            }
        }

        public Tab<T> getActiveTab() {
            return activeTabIndex >= 0 && activeTabIndex < tabs.size() ? tabs.get(activeTabIndex) : null;
        }

        public List<Tab<T>> getTabs() {
            return new ArrayList<>(tabs);
        }

        protected Object captureCurrentState() {
            return null;
        }

        protected void restoreFromState(Object state) {
        }

        private void createTabWidget(Tab<T> tab) {
            String displayName = tab.name + (tab.unsaved ? "*" : "");
            tab.widget = new AnimatedButton.ButtonBuilder()
                    .label(Text.literal(displayName))
                    .onClick(() -> {
                        int idx = tabs.indexOf(tab);
                        if (idx >= 0) setActiveTab(idx);
                    })
                    .entranceCorner(CENTER)
                    .build();
            addDrawableChild(tab.widget);
        }

        private void updateLayout() {
            if (!visible) return;
            List<Integer> widths = new ArrayList<>();
            for (Tab<T> tab : tabs) {
                String label = tab.name + (tab.unsaved ? "*" : "");
                widths.add(textRenderer.getWidth(label) + 2 * tabPadding);
            }
            float totalWidth = 0;
            for (int w : widths) totalWidth += w + tabGap;
            if (allowAdd) totalWidth += height + tabGap;
            float maxScroll = Math.max(0, totalWidth - width);
            targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
            scrollOffset += (targetScrollOffset - scrollOffset) * globalScrollSpeed * deltaTime;

            List<Float> basePositions = new ArrayList<>();
            float cursor = x;
            for (int w : widths) {
                basePositions.add(cursor);
                cursor += w + tabGap;
            }
            float plusPos = cursor;

            for (int i = 0; i < tabs.size(); i++) {
                Tab<T> tab = tabs.get(i);
                if (tab.widget != null && tab.visible) {
                    int w = widths.get(i);
                    if (dragging && i == draggingTabIndex) {
                        tab.widget.setPosition((int)(dragCurrentX - w / 2f), y);
                    } else {
                        float worldX = basePositions.get(i);
                        if (dragging && allowReorder) {
                            if (draggingTabIndex < dragOverIndex && i > draggingTabIndex && i <= dragOverIndex) {
                                worldX -= widths.get(draggingTabIndex) + tabGap;
                            } else if (draggingTabIndex > dragOverIndex && i < draggingTabIndex && i >= dragOverIndex) {
                                worldX += widths.get(draggingTabIndex) + tabGap;
                            }
                        }
                        tab.widget.setPosition((int)(worldX - scrollOffset), y);
                    }
                    tab.widget.setWidth(widths.get(i));
                    tab.widget.setHeight(height);
                }
            }

            if (allowAdd && plusButton != null) {
                plusButton.setPosition((int)(plusPos - scrollOffset), y);
                plusButton.setWidth(height);
                plusButton.setHeight(height);
            }

            updateTabStates();
        }

        private void updateTabStates() {
            for (int i = 0; i < tabs.size(); i++) {
                Tab<T> tab = tabs.get(i);
                if (tab.widget != null) tab.widget.setFocused(i == activeTabIndex);
            }
        }

        private int calculateDragOverIndex(float mouseX) {
            float localX = mouseX + scrollOffset;
            float cursor = x;
            for (int i = 0; i < tabs.size(); i++) {
                Tab<T> tab = tabs.get(i);
                String label = tab.name + (tab.unsaved ? "*" : "");
                int w = textRenderer.getWidth(label) + 2 * tabPadding;
                float center = cursor + w / 2f;
                if (localX < center) return i;
                cursor += w + tabGap;
            }
            return tabs.size() - 1;
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            updateLayout();
            for (Tab<T> tab : tabs) {
                if (tab.widget != null && tab.visible) {
                    String displayName = tab.name + (tab.unsaved ? "*" : "");
                    tab.widget.setMessage(Text.literal(displayName));
                }
            }
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!visible) return false;
            for (int i = 0; i < tabs.size(); i++) {
                Tab<T> tab = tabs.get(i);
                if (tab.widget != null && tab.visible) {
                    int tx = tab.widget.getX(), ty = tab.widget.getY();
                    int tw = tab.widget.getWidth(), th = tab.widget.getHeight();
                    if (mouseX >= tx && mouseX <= tx + tw &&
                            mouseY >= ty && mouseY <= ty + th) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                            if (allowReorder) {
                                draggingTabIndex = i;
                                dragStartX = (float)mouseX;
                                dragCurrentX = dragStartX;
                                dragging = false;
                            }
                            if (!allowRename) setActiveTab(i);
                            return true;
                        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && allowRename) {
                            startRename(i);
                            return true;
                        } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && allowClose) {
                            removeTab(i);
                            return true;
                        } else if (allowClose && mouseX >= tx + tw - 10) {
                            removeTab(i);
                            return true;
                        }
                    }
                }
            }
            if (allowAdd && plusButton != null) {
                int px = plusButton.getX(), py = plusButton.getY();
                int pw = plusButton.getWidth(), ph = plusButton.getHeight();
                if (mouseX >= px && mouseX <= px + pw &&
                        mouseY >= py && mouseY <= py + ph &&
                        button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (onTabAdded != null) onTabAdded.run();
                    return true;
                }
            }
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!visible || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || draggingTabIndex < 0) return false;
            dragCurrentX = (float)mouseX;
            if (!dragging && Math.abs(dragCurrentX - dragStartX) > 5) dragging = true;
            if (dragging && allowReorder) {
                int over = calculateDragOverIndex((float)mouseX);
                if (over != dragOverIndex) dragOverIndex = over;
            }
            return dragging;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!visible || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || draggingTabIndex < 0) return false;
            if (dragging && allowReorder && dragOverIndex >= 0 && dragOverIndex != draggingTabIndex) {
                Tab<T> moved = tabs.remove(draggingTabIndex);
                tabs.add(dragOverIndex, moved);
                if (activeTabIndex == draggingTabIndex) {
                    activeTabIndex = dragOverIndex;
                } else if (activeTabIndex > draggingTabIndex && activeTabIndex <= dragOverIndex) {
                    activeTabIndex--;
                } else if (activeTabIndex < draggingTabIndex && activeTabIndex >= dragOverIndex) {
                    activeTabIndex++;
                }
                if (onTabsReordered != null) onTabsReordered.accept(tabs);
                updateLayout();
            }
            draggingTabIndex = -1;
            dragging = false;
            dragOverIndex = -1;
            return true;
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
            if (!visible) return false;
            if (mouseX >= x && mouseX <= x + width &&
                    mouseY >= y && mouseY <= y + height) {
                targetScrollOffset -= (float)(verticalAmount * (height + tabGap));
                return true;
            }
            return false;
        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (renamingTabIndex >= 0 && renameWidget != null) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    finishRename();
                    return true;
                }
                return renameWidget.keyPressed(keyCode, scanCode, modifiers);
            }
            return false;
        }

        public boolean charTyped(char chr, int modifiers) {
            if (renamingTabIndex >= 0 && renameWidget != null) {
                return renameWidget.charTyped(chr, modifiers);
            }
            return false;
        }

        public void startRename(int index) {
            if (!allowRename || index < 0 || index >= tabs.size()) return;
            finishRename();
            renamingTabIndex = index;
            Tab<T> tab = tabs.get(index);
            if (tab.widget != null) remove(tab.widget);
            renameWidget = new TextInputWidget.Builder()
                    .text(tab.name)
                    .onChange(() -> {
                        if (renamingTabIndex >= 0 && renamingTabIndex < tabs.size()) {
                            tabs.get(renamingTabIndex).name = renameWidget.getText();
                        }
                    })
                    .build();
            renameWidget.setPosition(tab.widget.getX(), tab.widget.getY());
            renameWidget.setWidth(Math.max(100, tab.widget.getWidth()));
            renameWidget.setHeight(tab.widget.getHeight());
            renameWidget.setFocused(true);
            addDrawableChild(renameWidget);
        }

        public void finishRename() {
            if (renamingTabIndex >= 0 && renameWidget != null) {
                Tab<T> tab = tabs.get(renamingTabIndex);
                tab.name = renameWidget.getText();
                remove(renameWidget);
                renameWidget = null;
                createTabWidget(tab);
                updateLayout();
                if (onTabRenamed != null) onTabRenamed.accept(tab);
                renamingTabIndex = -1;
            }
        }

        public void setTabUnsaved(int index, boolean unsaved) {
            if (index >= 0 && index < tabs.size()) {
                tabs.get(index).unsaved = unsaved;
            }
        }

        public void clearCache() {
            stateCache.clear();
        }

        public void setCacheEnabled(boolean enabled) {
            this.enableStateCache = enabled;
            if (!enabled) clearCache();
        }
    }

    public class SidePanel {
        private final Container parentContainer;
        private boolean visible = false;
        private int desiredWidth = 200;
        private float animatedWidth = 0;
        private boolean isResizing = false;
        private final int minWidth = 80;
        private final int maxWidthRatio = 70;

        public SidePanel(Container parent) {
            this.parentContainer = parent;
        }

        public Container create() {
            if (sidePanel == null) {
                sidePanel = new Container(0, 0, 0, 0, false);
            }
            return sidePanel;
        }

        public SidePanel width(int width) {
            this.desiredWidth = Math.max(minWidth, Math.min(width, parentContainer.cWidth * maxWidthRatio / 100));
            return this;
        }

        public SidePanel show() {
            this.visible = true;
            return this;
        }

        public SidePanel hide() {
            this.visible = false;
            return this;
        }

        public void toggle() {
            this.visible = !this.visible;
        }

        public boolean isVisible() {
            return visible;
        }

        public boolean isResizing() {
            return isResizing;
        }

        public float getAnimatedWidth() {
            return animatedWidth;
        }

        private void updateAnimation() {
            float targetWidth = visible ? desiredWidth : 0;
            float previousWidth = animatedWidth;
            animatedWidth += (targetWidth - animatedWidth) * globalExpandSpeed * deltaTime;

            if (Math.abs(animatedWidth - targetWidth) < 0.5f) {
                animatedWidth = targetWidth;
            }

            if (Math.abs(previousWidth - animatedWidth) > 0.5f) {
                parentContainer.updateWidgetWidths();
            }
        }

        private void updateContainerBounds() {
            if (animatedWidth > 1 && sidePanel != null) {
                int panelX = parentContainer.x + parentContainer.cWidth - (int) animatedWidth;
                int panelY = parentContainer.y;
                int panelWidth = (int) animatedWidth;
                int panelHeight = parentContainer.cHeight;

                sidePanel.pos(panelX, panelY);
                sidePanel.size(panelWidth, panelHeight);
            }
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            updateAnimation();
            updateContainerBounds();

            if (animatedWidth > 1) {
                int panelX = parentContainer.x + parentContainer.cWidth - (int) animatedWidth;
                int panelY = parentContainer.y;
                int panelWidth = (int) animatedWidth;
                int panelHeight = parentContainer.cHeight;

                context.fill(panelX, panelY, panelX, panelHeight, innerBorderColor);
                context.fill(panelX, panelY, panelX + panelWidth, panelHeight, innerBackgroundColor);
                drawInnerBorder(context, panelX, panelY, panelWidth, panelHeight - panelY, innerBorderColor);
                drawOuterBorder(context, panelX, panelY, panelWidth, panelHeight - panelY, innerBackgroundColor);

                context.enableScissor(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1);
                if (sidePanel != null) {
                    sidePanel.render(context, mouseX, mouseY, delta);
                }
                context.disableScissor();
            }
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (animatedWidth > 1) {
                int panelX = parentContainer.x + parentContainer.cWidth - (int) animatedWidth;
                int panelY = parentContainer.y;
                int panelWidth = (int) animatedWidth;
                int panelHeight = parentContainer.cHeight;

                if (Math.abs(mouseX - (panelX - 1)) < 5 && mouseY >= panelY && mouseY <= panelY + panelHeight && button == 0) {
                    isResizing = true;
                    return true;
                }

                if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
                    return sidePanel != null && sidePanel.mouseClicked(mouseX, mouseY, button);
                }
            }
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && isResizing) {
                isResizing = false;
                return true;
            }
            if (animatedWidth > 1 && sidePanel != null) {
                return sidePanel.mouseReleased(mouseX, mouseY, button);
            }
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (isResizing && button == 0) {
                int newWidth = parentContainer.x + parentContainer.cWidth - (int) mouseX;
                desiredWidth = Math.max(minWidth, Math.min(newWidth, parentContainer.cWidth * maxWidthRatio / 100));
                return true;
            }
            if (animatedWidth > 1 && sidePanel != null) {
                return sidePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            }
            return false;
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
            if (animatedWidth > 1 && sidePanel != null) {
                int panelX = parentContainer.x + parentContainer.cWidth - (int) animatedWidth;
                int panelY = parentContainer.y;
                int panelWidth = (int) animatedWidth;
                int panelHeight = parentContainer.cHeight;

                if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
                    return sidePanel.mouseScrolled(mouseX, mouseY, verticalAmount);
                }
            }
            return false;
        }
    }

    public class Container {
        private final List<AnimatedWidget> widgets = new ArrayList<>();
        private final List<Integer> originalYPositions = new ArrayList<>();
        private int x;
        private int y;
        private int cWidth;
        private int cHeight;
        private int columns = 1;
        private int padding = 5;
        private float smoothOffset = 0;
        private float targetOffset = 0;
        private boolean canScroll = false;
        private final int scrollbarWidth = 2;
        private SidePanel sidePanel;

        public Container(int x, int y, int width, int height) {
            this(x, y, width, height, true);
        }

        public Container(int x, int y, int width, int height, boolean createSidePanel) {
            this.x = x;
            this.y = y;
            this.cWidth = width;
            this.cHeight = height;
            if (createSidePanel) {
                this.sidePanel = new SidePanel(this);
            }
        }

        public SidePanel sidePanel() {
            return sidePanel;
        }

        public void pos(int x, int y) {
            this.x = x;
            this.y = y;
            updateWidgetPositions();
        }

        public Container size(int width, int height) {
            this.cWidth = width;
            this.cHeight = height;
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
            originalYPositions.add(0);
            widget.resetEntranceAnimation();
            widget.setScissorRegion(x + 1, y + 1, x + getEffectiveWidth() - 1, cHeight - 1);
            addDrawableChild(widget);
            widget.setLayer(490);
            updateWidgetPositions();
            return this;
        }

        public Container removeWidget(ClickableWidget widget) {
            int index = widgets.indexOf(widget);
            if (index >= 0) {
                widgets.remove(index);
                originalYPositions.remove(index);
                if (widget instanceof AnimatedWidget) {
                    ((AnimatedWidget) widget).clearScissorRegion();
                }
            }
            remove(widget);
            updateWidgetPositions();
            return this;
        }

        public Container clearWidgets() {
            for (AnimatedWidget widget : widgets) {
                if (widget instanceof AnimatedWidget) {
                    widget.clearScissorRegion();
                }
                remove(widget);
            }
            widgets.clear();
            originalYPositions.clear();
            smoothOffset = 0;
            targetOffset = 0;
            return this;
        }

        private int getEffectiveWidth() {
            int sidebarWidth = (sidePanel != null ? (int) sidePanel.animatedWidth : 0);
            float gapProgress = sidePanel != null ? Math.min(1.0f, sidePanel.animatedWidth / 15f) : 0;
            int gap = (int) (4 * gapProgress);
            return cWidth - sidebarWidth - gap;
        }

        private void updateWidgetPositions() {
            if (widgets.isEmpty()) return;
            int effectiveWidth = getEffectiveWidth();
            int columnWidth = (effectiveWidth - padding * (columns + 1)) / columns;
            int currentRow = 0;
            int currentCol = 0;
            for (int i = 0; i < widgets.size(); i++) {
                AnimatedWidget widget = widgets.get(i);
                int widgetX = x + padding + currentCol * (columnWidth + padding);
                int widgetY = y + padding + currentRow * (widget.getHeight() + padding);
                widget.setPosition(widgetX, widgetY);
                widget.setWidth(columnWidth);
                if (i < originalYPositions.size()) {
                    originalYPositions.set(i, widgetY);
                }
                if (widget instanceof AnimatedWidget) {
                    widget.setScissorRegion(x, y, x + effectiveWidth, cHeight);
                }
                currentCol++;
                if (currentCol >= columns) {
                    currentCol = 0;
                    currentRow++;
                }
            }

            updateScrollState();
        }

        private void updateWidgetWidths() {
            if (widgets.isEmpty()) return;
            int effectiveWidth = getEffectiveWidth();
            int columnWidth = (effectiveWidth - padding * (columns + 1)) / columns;

            for (int i = 0; i < widgets.size(); i++) {
                AnimatedWidget widget = widgets.get(i);
                int widgetIndex = i % columns;
                int widgetX = x + padding + widgetIndex * (columnWidth + padding);
                int currentY = widget.getY();
                widget.setPosition(widgetX, currentY);
                widget.setWidth(columnWidth);
                widget.setScissorRegion(x, y, x + effectiveWidth, cHeight);
            }
        }

        private void updateScrollState() {
            int totalHeight = calculateTotalHeight();
            int visibleHeight = cHeight - y - 2 * padding;
            canScroll = totalHeight > visibleHeight;
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            smoothOffset += (targetOffset - smoothOffset) * globalScrollSpeed * deltaTime;

            int effectiveWidth = getEffectiveWidth();
            context.fill(x, y, x + effectiveWidth, cHeight, innerBackgroundColor);
            drawInnerBorder(context, x, y, effectiveWidth, cHeight - y, innerBorderColor);
            drawOuterBorder(context, x, y, effectiveWidth, cHeight - y, innerBackgroundColor);

            int totalHeight = calculateTotalHeight();
            int visibleHeight = cHeight - y - 2 * padding;
            canScroll = totalHeight > visibleHeight;

            for (int i = 0; i < widgets.size(); i++) {
                AnimatedWidget widget = widgets.get(i);
                int originalY = originalYPositions.get(i);
                int adjustedY = originalY - (int) smoothOffset;
                int currentX = widget.getX();
                widget.setPosition(currentX, adjustedY);
            }

            if (smoothOffset > 2) {
                context.fillGradient(x, y, x + effectiveWidth, y + 10, innerBackgroundColor, 0x00000000);
            }
            if (smoothOffset < Math.max(0, totalHeight - visibleHeight)) {
                context.fillGradient(x, cHeight - 10, x + effectiveWidth, cHeight, 0x00000000, innerBackgroundColor);
            }

            if (canScroll) {
                int scrollbarX = x + effectiveWidth + 2;
                int scrollbarY = y;
                int scrollbarHeight = cHeight - y;
                if (sidePanel != null && !sidePanel.isVisible()) {
                    ScrollBar.render(context, ReScreen.this, mouseX, mouseY, totalHeight, smoothOffset, scrollbarX, scrollbarY, scrollbarWidth, scrollbarHeight);
                }
            }

            if (sidePanel != null) {
                sidePanel.render(context, mouseX, mouseY, delta);
            }

            targetOffset = ScrollBar.getPendingOffset();
            targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, totalHeight - visibleHeight)));
        }

        private int calculateTotalHeight() {
            if (widgets.isEmpty()) return 0;

            int rows = (int) Math.ceil((double) widgets.size() / columns);
            int widgetHeight = widgets.get(0).getHeight();
            return rows * (widgetHeight + padding) + padding;
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
            if (sidePanel != null && sidePanel.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }

            int effectiveWidth = getEffectiveWidth();
            if (mouseX >= x && mouseX <= x + effectiveWidth && mouseY >= y && mouseY <= cHeight) {
                if (canScroll) {
                    int widgetHeight = widgets.isEmpty() ? 20 : widgets.get(0).getHeight();
                    targetOffset -= (float) (verticalAmount * widgetHeight * 0.5f);

                    int totalHeight = calculateTotalHeight();
                    int visibleHeight = cHeight - y - 2 * padding;
                    targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, totalHeight - visibleHeight)));

                    ScrollBar.setPendingOffset(targetOffset);
                    return true;
                }
            }
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (sidePanel != null && sidePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }

            if (canScroll) {
                int totalHeight = calculateTotalHeight();
                int visibleHeight = cHeight - y - 2 * padding;
                if (ScrollBar.handleMouseDragged(ReScreen.this, (int) mouseY, totalHeight, visibleHeight)) {
                    targetOffset = ScrollBar.getPendingOffset();
                    return true;
                }
            }
            return false;
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (sidePanel != null && sidePanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            int effectiveWidth = getEffectiveWidth();
            int scrollbarX = x + effectiveWidth + 2;

            if (mouseX >= x && mouseX <= scrollbarX + scrollbarWidth && mouseY >= y && mouseY <= cHeight) {
                if (canScroll && sidePanel != null && sidePanel.animatedWidth < 10) {
                    int totalHeight = calculateTotalHeight();
                    int scrollbarY = y;
                    int scrollbarHeight = cHeight - y;
                    if (ScrollBar.handleMousePressed(ReScreen.this, (int) mouseX, (int) mouseY, totalHeight, smoothOffset, scrollbarX, scrollbarY, scrollbarWidth, scrollbarHeight)) {
                        targetOffset = ScrollBar.getPendingOffset();
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (sidePanel != null && sidePanel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }

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

        public int getColumns() {
            return columns;
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
        animatedScaling(this);
        if (container != null) {
            container.render(context, mouseX, mouseY, delta);
        }
        if (tabsManager != null) {
            tabsManager.render(context, mouseX, mouseY, delta);
        }
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
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
        if (scaleScroll(verticalAmount)) return true;
        if (tabsManager != null && tabsManager.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        assert client != null;
        boolean shift = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        double scrollSpeed = shift ? 10 : 4;
        if (container.mouseScrolled(mouseX, mouseY, verticalAmount * scrollSpeed)) {
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
        if (tabsManager != null && tabsManager.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tabsManager != null && tabsManager.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (tabsManager != null && tabsManager.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        if (headerBuilder != null) {
            headerBuilder.updateButtonPositions();
        }
    }
}