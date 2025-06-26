package redxax.oxy.remotely.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.Render.ScrollBar;
import redxax.oxy.remotely.mixin.accessor.ClickableWidgetAccessor;
import redxax.oxy.remotely.ui.widgets.AnimatedButton;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.ui.widgets.SquareButtonWidget;
import redxax.oxy.remotely.ui.widgets.TextInputWidget;
import redxax.oxy.remotely.util.searchUtils;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.ui.widgets.AnimatedWidget.EntranceCorner.CENTER;
import static redxax.oxy.remotely.ui.widgets.AnimatedWidget.EntranceCorner.TOP_LEFT;
import static redxax.oxy.remotely.util.ImageUtil.drawBufferedImage;

public class ReScreen extends Screen {
    protected HeaderBuilder headerBuilder;
    public Container activeContainer;
    protected Map<String, Container> containers;
    protected Map<String, SidePanel> sidePanels;
    protected List<SidePanel> sidePanelList;
    protected TabsManager tabsManager;
    public boolean hitBottom;
    private boolean isInitialized = false;
    protected List<AnimatedWidget> currentSelectedWidgets = new ArrayList<>();
    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean needsLayoutUpdate = false;
    private int initialWidth = 0;
    private int initialHeight = 0;

    protected ReScreen(Text title) {
        super(title);
        this.headerBuilder = new HeaderBuilder();
        this.containers = new HashMap<>();
        this.sidePanels = new HashMap<>();
        this.sidePanelList = new ArrayList<>();
    }

    public HeaderBuilder header() {
        return headerBuilder;
    }

    public TabsManager tabs() {
        if (tabsManager == null) {
            tabsManager = new TabsManager();
        }
        return tabsManager;
    }

    public Container createContainer(int x, int y, int width, int height) {
        return new Container(x, y, width, height);
    }

    public Container createContainer(String id, int x, int y, int width, int height) {
        Container container = new Container(x, y, width, height);
        containers.put(id, container);
        return container;
    }

    public Container getContainer(String id) {
        return containers.get(id);
    }

    public void setActiveContainer(Container container) {
        if (activeContainer != null) {
            activeContainer.saveStateAndClearScreen();
        }
        this.activeContainer = container;
        if (activeContainer != null) {
            activeContainer.restoreStateToScreen();
            currentSelectedWidgets.clear();
            currentSelectedWidgets.addAll(activeContainer.selectedWidgets);
        }
    }

    public Container container() {
        return activeContainer;
    }

    public SidePanel createSidePanel(String id) {
        SidePanel panel = new SidePanel(id);
        sidePanels.put(id, panel);
        sidePanelList.add(panel);
        return panel;
    }

    public SidePanel getSidePanel(String id) {
        return sidePanels.get(id);
    }

    private int getTotalSidePanelWidth() {
        int w = 0;
        for (SidePanel sp : sidePanelList) {
            w += (int) sp.animatedWidth;
        }
        return w;
    }

    private void updateAllContainerWidths() {
        if (activeContainer != null) activeContainer.updateWidgetWidths();
        for (Container c : containers.values()) c.updateWidgetWidths();
        if (tabsManager != null) {
            for (TabsManager.Tab tab : tabsManager.getTabs()) {
                if (tab.getContainer() != null) {
                    tab.getContainer().updateWidgetWidths();
                }
            }
        }
    }

    private void updateAllLayouts() {
        if (headerBuilder != null) {
            headerBuilder.updateButtonPositions();
        }
        if (tabsManager != null) {
            tabsManager.updateLayout();
        }
        updateAllContainerWidths();
        for (SidePanel sp : sidePanelList) {
            sp.updateContainerBounds();
        }
        if (activeContainer != null) {
            activeContainer.updateContainerBounds();
        }
        for (Container c : containers.values()) {
            c.updateContainerBounds();
        }
        if (tabsManager != null) {
            for (TabsManager.Tab tab : tabsManager.getTabs()) {
                if (tab.getContainer() != null) {
                    tab.getContainer().updateContainerBounds();
                }
            }
        }
    }

    public record ContainerState(List<AnimatedWidget> widgets, List<Integer> originalYPositions, float smoothOffset, float targetOffset, List<AnimatedWidget> selectedWidgets) {
        public ContainerState(List<AnimatedWidget> widgets, List<Integer> originalYPositions, float smoothOffset, float targetOffset, List<AnimatedWidget> selectedWidgets) {
            this.widgets = new ArrayList<>(widgets);
            this.originalYPositions = new ArrayList<>(originalYPositions);
            this.smoothOffset = smoothOffset;
            this.targetOffset = targetOffset;
            this.selectedWidgets = new ArrayList<>(selectedWidgets);
        }
    }

    public class TabsManager {
        private final List<Tab> tabs = new ArrayList<>();
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
        private float scrollOffset = 0f;
        private float targetScrollOffset = 0f;
        private int tabPadding = 5;
        private int tabGap = 4;
        private SquareButtonWidget plusButton;
        public int renamingTabIndex = -1;
        private TextInputWidget renameWidget;
        private int draggingTabIndex = -1;
        private boolean dragging = false;
        private int dragOverIndex = -1;
        private float dragStartX = 0f;
        private float dragCurrentX = 0f;
        private Consumer<Tab> onTabSelected;
        private Consumer<Tab> onTabClosed;
        private Consumer<Tab> onTabRenamed;
        private Runnable onTabAdded;
        private Runnable onPlusButtonClicked;
        private Consumer<List<Tab>> onTabsReordered;

        public void loopTabs(boolean next) {
            if (tabs.isEmpty()) return;
            if (next) {
                activeTabIndex = (activeTabIndex + 1) % tabs.size();
            } else {
                activeTabIndex = (activeTabIndex - 1 + tabs.size()) % tabs.size();
            }
            setActiveTab(activeTabIndex);
        }

        public void clearWidgets() {
            for (Tab tab : tabs) {
                if (tab.widget != null) {
                    remove(tab.widget);
                    tab.widget = null;
                }
            }
        }

        public void rebuildAllTabWidgets() {
            clearWidgets();
            for (Tab tab : tabs) {
                if (tab.visible) {
                    createTabWidget(tab);
                }
            }
            if (allowAdd && plusButton == null) {
                plusButton = new SquareButtonWidget.Builder()
                        .imagePath("/assets/remotely/icons/newTab.png")
                        .size(18, 18)
                        .animateLayout(true)
                        .onClick(() -> {
                            if (onPlusButtonClicked != null) onPlusButtonClicked.run();
                        })
                        .entranceCorner(TOP_LEFT)
                        .build();
                addDrawableChild(plusButton);
            } else if (allowAdd && plusButton != null) {
                addDrawableChild(plusButton);
            }
            updateLayout();
        }

        public static class Tab {
            private String name;
            private boolean unsaved;
            private Container container;
            private final int id;
            private AnimatedButton widget;
            private boolean visible = true;
            private Object data = null;

            public Tab(String name, Container container) {
                this.name = name;
                this.container = container;
                this.unsaved = false;
                this.id = System.identityHashCode(this);
            }

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public boolean isUnsaved() { return unsaved; }
            public void setUnsaved(boolean unsaved) { this.unsaved = unsaved; }
            public Container getContainer() { return container; }
            public void setContainer(Container container) { this.container = container; }
            public int getId() { return id; }
            public AnimatedButton getWidget() { return widget; }
            public boolean isVisible() { return visible; }
            public void setVisible(boolean visible) { this.visible = visible; }
            public Object getData() {return data;}
            public void setData(Object data) {this.data = data;}
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
            public Builder onTabSelected(Consumer<Tab> callback) {
                TabsManager.this.onTabSelected = callback;
                return this;
            }
            public Builder onTabClosed(Consumer<Tab> callback) {
                TabsManager.this.onTabClosed = callback;
                return this;
            }
            public Builder onTabRenamed(Consumer<Tab> callback) {
                TabsManager.this.onTabRenamed = callback;
                return this;
            }
            public Builder onTabAdded(Runnable callback) {
                TabsManager.this.onTabAdded = callback;
                return this;
            }
            public Builder onPlusButtonClicked(Runnable callback) {
                TabsManager.this.onPlusButtonClicked = callback;
                return this;
            }
            public Builder onTabsReordered(Consumer<List<Tab>> callback) {
                TabsManager.this.onTabsReordered = callback;
                return this;
            }
            public TabsManager build() {
                if (allowAdd && plusButton == null) {
                    plusButton = new SquareButtonWidget.Builder().imagePath("/assets/remotely/icons/newTab.png").size(18, 18).animateLayout(true)
                            .onClick(() -> { if (onPlusButtonClicked != null) onPlusButtonClicked.run(); }).entranceCorner(TOP_LEFT)
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

        public Tab addTab(String name, Container container) {
            Tab tab = new Tab(name, container);
            tabs.add(tab);
            createTabWidget(tab);
            if (onTabAdded != null) {
                onTabAdded.run();
            }
            updateLayout();
            return tab;
        }

        public void removeTab(int index) {
            if (index >= 0 && index < tabs.size()) {
                Tab tab = tabs.remove(index);
                if (tab.widget != null) remove(tab.widget);
                if (activeTabIndex >= tabs.size()) {
                    activeTabIndex = Math.max(0, tabs.size() - 1);
                }
                if (tabs.size() > 0) {
                    setActiveTab(activeTabIndex);
                } else {
                    setActiveContainer(null);
                }
                if (onTabClosed != null) onTabClosed.accept(tab);
                updateLayout();
            }
        }

        public void setActiveTab(int index) {
            if (index >= 0 && index < tabs.size()) {
                activeTabIndex = index;
                Tab activeTab = tabs.get(index);
                setActiveContainer(activeTab.getContainer());
                updateTabStates();
                if (onTabSelected != null) onTabSelected.accept(activeTab);
            }
        }

        public void setActiveTab(Container container) {
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).getContainer().equals(container)) {
                    setActiveTab(i);
                    return;
                }
            }
        }

        public Tab getActiveTab() {
            return activeTabIndex >= 0 && activeTabIndex < tabs.size() ? tabs.get(activeTabIndex) : null;
        }

        public int getActiveTabIndex() {
            return activeTabIndex;
        }

        public List<Tab> getTabs() {
            return new ArrayList<>(tabs);
        }

        private void createTabWidget(Tab tab) {
            String displayName = tab.name + (tab.unsaved ? "*" : "");
            tab.widget = new AnimatedButton.Builder()
                    .label(Text.literal(displayName))
                    .onClick(() -> {
                        int idx = tabs.indexOf(tab);
                        if (idx >= 0) setActiveTab(idx);
                    })
                    .entranceCorner(TOP_LEFT)
                    .animateLayout(true)
                    .setSelectable(true)
                    .build();
            addDrawableChild(tab.widget);
        }

        private void updateLayout() {
            if (!visible) return;
            width = ReScreen.this.width;
            List<Integer> widths = new ArrayList<>();
            for (Tab tab : tabs) {
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
                Tab tab = tabs.get(i);
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
                    ((ClickableWidgetAccessor) tab.widget).setHeight(height);
                }
            }

            if (allowAdd && plusButton != null) {
                plusButton.setPosition((int)(plusPos - scrollOffset), y);
            }
            updateTabStates();
        }

        private void updateTabStates() {
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                if (tab.widget != null) tab.widget.setSelected(i == activeTabIndex);
            }
        }

        private int calculateDragOverIndex(float mouseX) {
            float localX = mouseX + scrollOffset;
            float cursor = x;
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
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
            for (Tab tab : tabs) {
                if (tab.widget != null && tab.visible) {
                    String displayName = tab.name + (tab.unsaved ? "*" : "");
                    tab.widget.setMessage(Text.literal(displayName));
                }
            }
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!visible) return false;
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                if (tab.widget != null && tab.visible) {
                    int tx = tab.widget.getX(), ty = tab.widget.getY();
                    int tw = tab.widget.getWidth(), th = tab.widget.getHeight();
                    if (mouseX >= tx && mouseX <= tx + tw &&
                            mouseY >= ty && mouseY <= ty + th) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                            setActiveTab(i);
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
                Tab moved = tabs.remove(draggingTabIndex);
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
            Tab tab = tabs.get(index);
            if (tab.widget != null) remove(tab.widget);
            renameWidget = new TextInputWidget.Builder()
                    .text(tab.name)
                    .size(tab.widget.getWidth(), tab.widget.getHeight())
                    .onChange(() -> {
                        if (renamingTabIndex >= 0 && renamingTabIndex < tabs.size()) {
                            tabs.get(renamingTabIndex).name = renameWidget.getText();
                        }
                    })
                    .pos(tab.widget.getX(), tab.widget.getY())
                    .animateLayout(true)
                    .dynamicWidth(true)
                    .entranceAnimation(false)
                    .build();
            renameWidget.setPosition(tab.widget.getX(), tab.widget.getY());
            renameWidget.setWidth(Math.max(100, tab.widget.getWidth()));
            ((ClickableWidgetAccessor) renameWidget).setHeight(tab.widget.getHeight());
            renameWidget.setFocused(true);
            addDrawableChild(renameWidget);
        }

        public void finishRename() {
            if (renamingTabIndex >= 0 && renameWidget != null) {
                Tab tab = tabs.get(renamingTabIndex);
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

        public boolean isInTabsArea(double mouseX, double mouseY) {
            return visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public class SidePanel {
        private final String id;
        private final Container innerContainer;
        private boolean visible = false;
        private int desiredWidth = 200;
        private float animatedWidth = 0;
        private boolean isResizing = false;
        private final int minWidth = 80;
        private final int maxWidthRatio = 70;
        private int yPos = 0;
        private int panelHeight = height;

        public SidePanel(String id) {
            this.id = id;
            this.innerContainer = new Container(0, 0, 0, 0, true);
        }

        public String id() {
            return id;
        }

        public SidePanel width(int width) {
            this.desiredWidth = Math.max(minWidth, Math.min(width, ReScreen.this.width * maxWidthRatio / 100));
            return this;
        }

        public SidePanel y(int y) {
            this.yPos = y;
            return this;
        }

        public SidePanel height(int h) {
            this.panelHeight = h;
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

        public float getAnimatedWidth() {
            return animatedWidth;
        }

        public Container container() {
            return innerContainer;
        }

        public SidePanel addWidget(AnimatedWidget widget) {
            innerContainer.addWidget(widget);
            return this;
        }

        public void rebuildWidgets() {
            if (animatedWidth > 1) {
                innerContainer.restoreStateToScreen();
            }
        }

        private void updateAnimation() {
            float targetWidth = visible ? desiredWidth : 0;
            float previousWidth = animatedWidth;
            animatedWidth += (targetWidth - animatedWidth) * globalExpandSpeed * deltaTime;
            if (Math.abs(animatedWidth - targetWidth) < 0.5f) animatedWidth = targetWidth;
            if (Math.abs(previousWidth - animatedWidth) > 0.5f) updateAllContainerWidths();
        }

        private void updateContainerBounds() {
            int panelX = ReScreen.this.width - (int) animatedWidth;
            innerContainer.pos(panelX, yPos);
            innerContainer.size((int) animatedWidth, panelHeight);
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            updateAnimation();
            updateContainerBounds();
            if (animatedWidth > 1) {
                innerContainer.render(context, mouseX, mouseY, delta);
            }
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (animatedWidth > 1) {
                int panelX = ReScreen.this.width - (int) animatedWidth;
                int panelY = yPos;
                int panelWidth = (int) animatedWidth;
                int panelHeight = this.panelHeight;
                if (Math.abs(mouseX - (panelX - 1)) < 5 && mouseY >= panelY && mouseY <= panelY + panelHeight && button == 0) {
                    isResizing = true;
                    return true;
                }
                if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
                    return innerContainer.mouseClicked(mouseX, mouseY, button);
                }
            }
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && isResizing) {
                isResizing = false;
                return true;
            }
            if (animatedWidth > 1) {
                return innerContainer.mouseReleased(mouseX, mouseY, button);
            }
            return false;
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (isResizing && button == 0) {
                int newWidth = ReScreen.this.width - (int) mouseX;
                desiredWidth = Math.max(minWidth, Math.min(newWidth, ReScreen.this.width * maxWidthRatio / 100));
                return true;
            }
            if (animatedWidth > 1) {
                return innerContainer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            }
            return false;
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
            if (animatedWidth > 1) {
                int panelX = ReScreen.this.width - (int) animatedWidth;
                int panelY = yPos;
                int panelWidth = (int) animatedWidth;
                int panelHeight = this.panelHeight;
                if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
                    return innerContainer.mouseScrolled(mouseX, mouseY, verticalAmount);
                }
            }
            return false;
        }
    }

    public class Container {
        public enum LayoutStyle {
            RESTRICTED,
            MANAGED,
            FREE
        }

        private final List<AnimatedWidget> widgets = new ArrayList<>();
        private final List<Integer> originalYPositions = new ArrayList<>();
        private final List<AnimatedWidget> selectedWidgets = new ArrayList<>();
        private int x;
        private int y;
        private int cWidth;
        private int cHeight;
        private int widthOffset = 0;
        private int heightOffset = 0;
        private boolean relativeToScreen = false;
        private int columns = 1;
        private int padding = 5;
        private float smoothOffset = 0;
        private float targetOffset = 0;
        private boolean canScroll = false;
        private final int scrollbarWidth = 2;
        private final boolean sidePanelContainer;
        private ContainerState savedState;
        private LayoutStyle layoutStyle = LayoutStyle.RESTRICTED;
        private boolean enableSelecting = false;
        private long lastClickTime = 0;
        private int DOUBLE_CLICK_DELAY = 500;
        private AnimatedWidget lastClickedWidget = null;

        public Container(int x, int y, int width, int height) {
            this(x, y, width, height, false);
        }

        public Container(int x, int y, int width, int height, boolean sidePanelContainer) {
            this.x = x;
            this.y = y;
            this.cWidth = width;
            this.cHeight = height;
            this.sidePanelContainer = sidePanelContainer;

            if (!sidePanelContainer && ReScreen.this.initialWidth > 0 && ReScreen.this.initialHeight > 0) {
                this.widthOffset = width - ReScreen.this.initialWidth;
                this.heightOffset = height - ReScreen.this.initialHeight;
                this.relativeToScreen = true;
            }
        }

        public void pos(int x, int y) {
            this.x = x;
            this.y = y;
            updateWidgetPositions();
        }

        public Container size(int width, int height) {
            this.cWidth = width;
            this.cHeight = height;
            if (!sidePanelContainer && ReScreen.this.initialWidth > 0 && ReScreen.this.initialHeight > 0) {
                this.widthOffset = width - ReScreen.this.initialWidth;
                this.heightOffset = height - ReScreen.this.initialHeight;
                this.relativeToScreen = true;
            }
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

        public Container layoutStyle(LayoutStyle style) {
            this.layoutStyle = style;
            updateWidgetPositions();
            return this;
        }

        public Container enableSelecting(boolean enable) {
            this.enableSelecting = enable;
            if (!enable) {
                clearSelection();
            }
            return this;
        }

        public Container setDoubleClickDelay(int delay) {
            this.DOUBLE_CLICK_DELAY = delay;
            return this;
        }

        public void columnsGlobally(int columns) {
            for (Container c : containers.values()) {
                c.columns(columns);
                c.updateWidgetPositions();
            }
            this.columns = Math.max(1, columns);
        }

        public Container addWidget(AnimatedWidget widget) {
            widgets.add(widget);
            originalYPositions.add(0);
            widget.setScissorRegion(x + 1, y + 1, x + getEffectiveWidth() - 1, cHeight - 1);
            if (enableSelecting) {
                widget.selectable = true;
            }
            if (this == activeContainer || sidePanelContainer) {
                addDrawableChild(widget);
            }
            widget.setLayer(490);
            updateWidgetPositions();
            return this;
        }

        public Container removeWidget(AnimatedWidget widget) {
            int index = widgets.indexOf(widget);
            if (index >= 0) {
                widgets.remove(index);
                originalYPositions.remove(index);
                selectedWidgets.remove(widget);
                currentSelectedWidgets.remove(widget);
                if (widget instanceof AnimatedWidget aw)
                    aw.clearScissorRegion();
            }
            remove(widget);
            updateWidgetPositions();
            return this;
        }

        public Container clearWidgets() {
            for (AnimatedWidget widget : widgets) {
                widget.clearScissorRegion();
                remove(widget);
            }
            widgets.clear();
            originalYPositions.clear();
            selectedWidgets.clear();
            if (this == activeContainer) {
                currentSelectedWidgets.clear();
            }
            smoothOffset = 0;
            targetOffset = 0;
            return this;
        }

        public ContainerState saveState() {
            return new ContainerState(widgets, originalYPositions, smoothOffset, targetOffset, selectedWidgets);
        }

        public void restoreState(ContainerState state) {
            clearWidgets();
            widgets.addAll(state.widgets());
            originalYPositions.addAll(state.originalYPositions());
            smoothOffset = state.smoothOffset();
            targetOffset = state.targetOffset();
            selectedWidgets.addAll(state.selectedWidgets());
            if (this == activeContainer || sidePanelContainer) {
                for (AnimatedWidget widget : widgets) {
                    addDrawableChild(widget);
                    widget.setScissorRegion(x + 1, y + 1, x + getEffectiveWidth() - 1, cHeight - 1);
                }
            }
            updateWidgetPositions();
        }

        public void saveStateAndClearScreen() {
            savedState = saveState();
            for (AnimatedWidget widget : widgets) {
                remove(widget);
                widget.clearScissorRegion();
            }
        }

        public void restoreStateToScreen() {
            if (savedState != null) {
                restoreState(savedState);
                savedState = null;
            } else {
                for (AnimatedWidget widget : widgets) {
                    addDrawableChild(widget);
                    widget.setScissorRegion(x + 1, y + 1, x + getEffectiveWidth() - 1, cHeight - 1);
                }
                updateWidgetPositions();
            }
        }

        public int getEffectiveWidth() {
            return sidePanelContainer ? cWidth : cWidth - getTotalSidePanelWidth();
        }

        public void updateContainerBounds() {
            if (!sidePanelContainer && relativeToScreen) {
                this.cWidth = ReScreen.this.width + widthOffset;
                this.cHeight = ReScreen.this.height + heightOffset;
            }
            updateWidgetPositions();
        }

        public void updateWidgetPositions() {
            if (widgets.isEmpty())
                return;
            switch (layoutStyle) {
                case RESTRICTED -> layoutRestricted();
                case MANAGED -> layoutManaged();
                case FREE -> layoutFree();
            }
            updateScrollState();
        }

        private void layoutRestricted() {
            int effectiveWidth = getEffectiveWidth();
            int columnWidth = (effectiveWidth - padding * (columns + 1)) / columns;
            int rowHeight = widgets.isEmpty() ? 20 : widgets.get(0).getHeight();
            int currentRow = 0;
            int currentCol = 0;
            for (int i = 0; i < widgets.size(); i++) {
                AnimatedWidget w = widgets.get(i);
                w.setWidth(columnWidth);
                w.setHeight(rowHeight);
                int wx = x + padding + currentCol * (columnWidth + padding);
                int wy = y + padding + currentRow * (rowHeight + padding);
                w.setPosition(wx, wy);
                w.setScissorRegion(x, y, x + effectiveWidth, cHeight);
                originalYPositions.set(i, wy);
                currentCol++;
                if (currentCol >= columns) {
                    currentCol = 0;
                    currentRow++;
                }
            }
        }

        private void layoutManaged() {
            int effectiveWidth = getEffectiveWidth();
            int columnWidth = (effectiveWidth - padding * (columns + 1)) / columns;
            int[] colHeights = new int[columns];
            Arrays.fill(colHeights, y + padding);
            for (int i = 0; i < widgets.size(); i++) {
                AnimatedWidget w = widgets.get(i);
                w.setWidth(columnWidth);
                int col = indexOfMin(colHeights);
                int wx = x + padding + col * (columnWidth + padding);
                int wy = colHeights[col];
                w.setPosition(wx, wy);
                w.setScissorRegion(x, y, x + effectiveWidth, cHeight);
                originalYPositions.set(i, wy);
                colHeights[col] += w.getHeight() + padding;
            }
        }

        private int indexOfMin(int[] arr) {
            int idx = 0;
            for (int i = 1; i < arr.length; i++)
                if (arr[i] < arr[idx])
                    idx = i;
            return idx;
        }

        private void layoutFree() {
            int effectiveWidth = getEffectiveWidth();
            class PlacedRect {
                int x, y, w, h;
                PlacedRect(int x, int y, int w, int h) {
                    this.x = x;
                    this.y = y;
                    this.w = w;
                    this.h = h;
                }
                boolean intersects(PlacedRect r) {
                    return x < r.x + r.w && r.x < x + w && y < r.y + r.h && r.y < y + h;
                }
            }
            List<PlacedRect> placed = new ArrayList<>();
            for (int i = 0; i < widgets.size(); i++) {
                AnimatedWidget w = widgets.get(i);
                int wWidth = w.getWidth();
                int wHeight = w.getHeight();
                boolean placedFlag = false;
                for (int yy = y + padding; yy <= y + cHeight - padding - wHeight; yy++) {
                    for (int xx = x + padding; xx <= x + effectiveWidth - padding - wWidth; xx++) {
                        PlacedRect cand = new PlacedRect(xx, yy, wWidth + padding, wHeight + padding);
                        boolean collision = false;
                        for (PlacedRect pr : placed) {
                            if (cand.intersects(pr)) {
                                collision = true;
                                break;
                            }
                        }
                        if (!collision) {
                            w.setPosition(xx, yy);
                            w.setScissorRegion(x, y, x + effectiveWidth, cHeight);
                            originalYPositions.set(i, yy);
                            placed.add(cand);
                            placedFlag = true;
                            break;
                        }
                    }
                    if (placedFlag) break;
                }
                if (!placedFlag) {
                    int maxY = y + padding;
                    for (PlacedRect pr : placed) {
                        maxY = Math.max(maxY, pr.y + pr.h);
                    }
                    int newY = Math.min(maxY, y + cHeight - padding - wHeight);
                    int newX = x + padding;
                    PlacedRect cand = new PlacedRect(newX, newY, wWidth + padding, wHeight + padding);
                    w.setPosition(newX, newY);
                    w.setScissorRegion(x, y, x + effectiveWidth, cHeight);
                    originalYPositions.set(i, newY);
                    placed.add(cand);
                }
            }
        }

        public void updateWidgetWidths() {
            updateWidgetPositions();
        }

        private void updateScrollState() {
            int totalHeight = calculateTotalHeight();
            int visibleHeight = cHeight - y - 2 * padding;
            canScroll = totalHeight > visibleHeight;
        }

        private int calculateTotalHeight() {
            if (widgets.isEmpty()) return 0;
            int maxBottom = y + padding;
            for (int i = 0; i < widgets.size(); i++) {
                int bottom = originalYPositions.get(i) + widgets.get(i).getHeight();
                maxBottom = Math.max(maxBottom, bottom);
            }
            return maxBottom - y + padding;
        }

        private void selectWidget(AnimatedWidget widget, boolean addToSelection) {
            if (!enableSelecting) return;
            if (!addToSelection) {
                clearSelection();
            }
            if (!selectedWidgets.contains(widget)) {
                selectedWidgets.add(widget);
                widget.setSelected(true);
                if (this == activeContainer) {
                    currentSelectedWidgets.add(widget);
                }
            }
        }

        private void clearSelection() {
            for (AnimatedWidget widget : selectedWidgets) {
                widget.setSelected(false);
            }
            selectedWidgets.clear();
            if (this == activeContainer) {
                currentSelectedWidgets.clear();
            }
        }

        private void selectRange(int fromIndex, int toIndex) {
            if (!enableSelecting) return;
            int start = Math.min(fromIndex, toIndex);
            int end = Math.max(fromIndex, toIndex);
            for (int i = start; i <= end && i < widgets.size(); i++) {
                AnimatedWidget widget = widgets.get(i);
                if (!selectedWidgets.contains(widget)) {
                    selectedWidgets.add(widget);
                    widget.setSelected(true);
                    if (this == activeContainer) {
                        currentSelectedWidgets.add(widget);
                    }
                }
            }
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!sidePanelContainer && this != activeContainer) return;

            updateWidgetPositions();

            smoothOffset += (targetOffset - smoothOffset) * globalScrollSpeed * deltaTime;
            int effectiveWidth = getEffectiveWidth();
            drawBackground(context, effectiveWidth);
            int totalHeight = calculateTotalHeight();
            int visibleHeight = cHeight - y - 2 * padding;
            canScroll = totalHeight > visibleHeight;
            hitBottom = smoothOffset > Math.max(0, totalHeight - visibleHeight) - 2;
            for (int i = 0; i < widgets.size(); i++) {
                AnimatedWidget w = widgets.get(i);
                int newY = originalYPositions.get(i) - (int) smoothOffset;
                w.setPosition(w.getX(), newY);
            }
            if (smoothOffset > 2) {
                context.fillGradient(x, y, x + effectiveWidth, y + 10, innerBackgroundColor, 0x00000000);
            }
            if (smoothOffset < Math.max(0, totalHeight - visibleHeight)) {
                context.fillGradient(x, cHeight - 10, x + effectiveWidth, cHeight, 0x00000000, innerBackgroundColor);
            }
            if (canScroll && (sidePanelContainer || getTotalSidePanelWidth() < 10)) {
                int scrollbarX = x + effectiveWidth + 2;
                int scrollbarY = y;
                int scrollbarHeight = cHeight - y;
                ScrollBar.render(context, ReScreen.this, mouseX, mouseY, totalHeight, smoothOffset, scrollbarX, scrollbarY, scrollbarWidth, scrollbarHeight);
            }
            targetOffset = ScrollBar.getPendingOffset();
            targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, totalHeight - visibleHeight)));
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
            if (!sidePanelContainer && this != activeContainer) return false;
            int effectiveWidth = getEffectiveWidth();
            if (mouseX >= x && mouseX <= x + effectiveWidth &&
                    mouseY >= y && mouseY <= cHeight) {
                if (canScroll) {
                    int step = 30;
                    targetOffset -= verticalAmount * step;
                    int totalHeight = calculateTotalHeight();
                    int visibleHeight = cHeight - y - 2 * padding;
                    targetOffset = Math.max(0,
                            Math.min(targetOffset, Math.max(0, totalHeight - visibleHeight)));
                    ScrollBar.setPendingOffset(targetOffset);
                    return true;
                }
            }
            return false;
        }

        public void drawBackground(DrawContext context, int effectiveWidth) {
            context.fill(x, y, x + effectiveWidth, cHeight, innerBackgroundColor);
            drawInnerBorder(context, x, y, effectiveWidth, cHeight - y, innerBorderColor);
            drawOuterBorder(context, x, y, effectiveWidth, cHeight - y, innerBackgroundColor);
        }

        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!sidePanelContainer && this != activeContainer) return false;
            if (canScroll) {
                int totalHeight = calculateTotalHeight();
                int visibleHeight = cHeight - y - 2 * padding;
                if (ScrollBar.handleMouseDragged(ReScreen.this, (int) mouseY, totalHeight,
                        visibleHeight)) {
                    targetOffset = ScrollBar.getPendingOffset();
                    return true;
                }
            }
            return false;
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!sidePanelContainer && this != activeContainer) return false;
            int effectiveWidth = getEffectiveWidth();
            int scrollbarX = x + effectiveWidth + 2;

            if (enableSelecting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                long currentTime = System.currentTimeMillis();
                boolean isDoubleClick = false;
                AnimatedWidget clickedWidget = null;
                for (AnimatedWidget widget : widgets) {
                    if (mouseX >= widget.getX() && mouseX <= widget.getX() + widget.getWidth() && mouseY >= widget.getY() && mouseY <= widget.getY() + widget.getHeight()) {
                        clickedWidget = widget;
                        break;
                    }
                }

                if (clickedWidget != null && clickedWidget == lastClickedWidget && currentTime - lastClickTime <= DOUBLE_CLICK_DELAY) {
                    isDoubleClick = true;
                }

                if (isDoubleClick) {
                    lastClickTime = 0;
                    lastClickedWidget = null;
                    return false;
                } else if (clickedWidget != null) {
                    lastClickTime = currentTime;
                    lastClickedWidget = clickedWidget;
                    if (hasShiftDown() && !selectedWidgets.isEmpty()) {
                        int lastSelectedIndex = widgets.indexOf(selectedWidgets.getLast());
                        int clickedIndex = widgets.indexOf(clickedWidget);
                        selectRange(lastSelectedIndex, clickedIndex);
                    } else if (hasControlDown()) {
                        if (selectedWidgets.contains(clickedWidget)) {
                            selectedWidgets.remove(clickedWidget);
                            clickedWidget.setSelected(false);
                            if (this == activeContainer) {
                                currentSelectedWidgets.remove(clickedWidget);
                            }
                        } else {
                            selectWidget(clickedWidget, true);
                        }
                    } else {
                        selectWidget(clickedWidget, false);
                    }
                    return true;
                }
            }

            if (mouseX >= x && mouseX <= scrollbarX + scrollbarWidth &&
                    mouseY >= y && mouseY <= cHeight) {
                if (canScroll && (sidePanelContainer || getTotalSidePanelWidth() < 10)) {
                    int totalHeight = calculateTotalHeight();
                    int scrollbarY = y;
                    int scrollbarHeight = cHeight - y;
                    if (ScrollBar.handleMousePressed(ReScreen.this, (int) mouseX, (int) mouseY,
                            totalHeight, smoothOffset, scrollbarX, scrollbarY, scrollbarWidth,
                            scrollbarHeight)) {
                        targetOffset = ScrollBar.getPendingOffset();
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!sidePanelContainer && this != activeContainer) return false;
            return canScroll && ScrollBar.handleMouseReleased();
        }

        public boolean isCanScroll() {
            return canScroll;
        }

        public List<AnimatedWidget> getWidgets() {
            return new ArrayList<>(widgets);
        }

        public List<AnimatedWidget> getSelectedWidgets() {
            return new ArrayList<>(selectedWidgets);
        }

        public void addSelectedWidget(AnimatedWidget widget) {
            if (enableSelecting && !selectedWidgets.contains(widget)) {
                selectWidget(widget, true);
            }
        }

        public void selectAllWidgets() {
            if (enableSelecting) {
                clearSelection();
                selectedWidgets.addAll(widgets);
                currentSelectedWidgets.addAll(widgets);
                for (AnimatedWidget widget : widgets) {
                    widget.setSelected(true);
                }
            }
        }

        public int getPadding() {
            return padding;
        }

        public int getColumns() {
            return columns;
        }

        public boolean isEnableSelecting() {
            return enableSelecting;
        }
    }

    public class HeaderBuilder {
        public final List<SquareButtonWidget> leftButtons = new ArrayList<>();
        private final List<SquareButtonWidget> rightButtons = new ArrayList<>();
        private Position position = Position.TOP;
        private int headerSize = 30;
        private boolean visible = true;
        private TextInputWidget searchBox;
        private SearchMode currentSearchMode;
        public boolean liveUpdate = false;
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
            SquareButtonWidget button = new SquareButtonWidget.Builder().image(image).hint(hint).onClick(action).entranceCorner(CENTER).entranceAnimationStrength(.6f).hintDelay(.4f).hintDelay(.4f).build();
            leftButtons.add(button);
            return this;
        }
        public HeaderBuilder addLeft(String imagePath, Runnable action, String hint) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().imagePath(imagePath).hint(hint).onClick(action).entranceCorner(CENTER).entranceAnimationStrength(.6f).hintDelay(.4f).build();
            leftButtons.add(button);
            return this;
        }
        public HeaderBuilder addRight(BufferedImage image, Runnable action, String hint) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().image(image).hint(hint).onClick(action).entranceCorner(CENTER).entranceAnimationStrength(.6f).hintDelay(.4f).build();
            rightButtons.add(button);
            return this;
        }
        public HeaderBuilder addRight(String imagePath, Runnable action, String hint) {
            SquareButtonWidget button = new SquareButtonWidget.Builder().imagePath(imagePath).hint(hint).onClick(action).entranceCorner(CENTER).entranceAnimationStrength(.6f).hintDelay(.4f).build();
            rightButtons.add(button);
            return this;
        }
        public HeaderBuilder setSearchMode(SearchMode mode, boolean liveUpdate) {
            currentSearchMode = mode;
            this.liveUpdate = liveUpdate;
            if (mode != null) {
                if (searchBox == null) {
                    searchBox = new SearchTextInputWidget(0, 0, 200, 18);
                    ((SearchTextInputWidget) searchBox).onEnter = text -> {
                        if (currentSearchMode != null && currentSearchMode.getOnSearchEnter() != null) {
                            currentSearchMode.getOnSearchEnter().accept(text);
                        }
                        if (currentSearchMode != null && currentSearchMode.isSortMode() && container() != null) {
                            container().widgets.sort((w1, w2) -> {
                                String t1 = w1.getMessage().getString();
                                String t2 = w2.getMessage().getString();
                                boolean m1 = searchUtils.isFuzzyMatch(t1, text);
                                boolean m2 = searchUtils.isFuzzyMatch(t2, text);
                                if (m1 && !m2) return -1;
                                if (!m1 && m2) return 1;
                                return 0;
                            });
                            container().updateWidgetPositions();
                        }
                    };
                    ((SearchTextInputWidget) searchBox).onTextChange = text -> {
                        if (currentSearchMode != null && currentSearchMode.getOnTextChange() != null) {
                            currentSearchMode.getOnTextChange().accept(text);
                        }
                    };
                }
            } else {
                if (searchBox != null) {
                    ReScreen.this.remove(searchBox);
                    searchBox = null;
                }
            }
            updateButtonPositions();
            return headerBuilder;
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
            if (searchBox != null) {
                ReScreen.this.remove(searchBox);
                ReScreen.this.addDrawableChild(searchBox);
            }
            leftButtons.clear();
            rightButtons.clear();
        }
        private void clearHeaderWidgets() {
            for (SquareButtonWidget btn : leftButtons) {
                remove(btn);
            }
            for (SquareButtonWidget btn : rightButtons) {
                remove(btn);
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
            if (searchBox != null) {
                switch (position) {
                    case TOP -> searchBox.setPosition((ReScreen.this.width - 200) / 2, (headerSize - searchBox.getHeight()) / 2);
                    case BOTTOM -> searchBox.setPosition((ReScreen.this.width - 200) / 2, ReScreen.this.height - headerSize + (headerSize - searchBox.getHeight()) / 2);
                    case LEFT -> searchBox.setPosition((headerSize - 200) / 2, (ReScreen.this.height - searchBox.getHeight()) / 2);
                    case RIGHT -> searchBox.setPosition(ReScreen.this.width - headerSize + (headerSize - 200) / 2, (ReScreen.this.height - searchBox.getHeight()) / 2);
                }
            }
        }
        private void updateTopPositions() {
            int leftX = 5;
            for (SquareButtonWidget button : leftButtons) {
                button.setPosition(leftX, 5);
                leftX += 23;
            }
            int rightX = ReScreen.this.width - 28;
            for (SquareButtonWidget button : rightButtons) {
                button.setPosition(rightX, 5);
                rightX -= 23;
            }
        }
        private void updateBottomPositions() {
            int leftX = 5;
            int y = ReScreen.this.height - headerSize + 5;
            for (SquareButtonWidget button : leftButtons) {
                button.setPosition(leftX, y);
                leftX += 23;
            }
            int rightX = ReScreen.this.width - 28;
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
            int bottomY = ReScreen.this.height - 28;
            for (SquareButtonWidget button : rightButtons) {
                button.setPosition(5, bottomY);
                bottomY -= 23;
            }
        }
        private void updateRightPositions() {
            int x = ReScreen.this.width - headerSize + 5;
            int topY = 5;
            for (SquareButtonWidget button : leftButtons) {
                button.setPosition(x, topY);
                topY += 23;
            }
            int bottomY = ReScreen.this.height - 28;
            for (SquareButtonWidget button : rightButtons) {
                button.setPosition(x, bottomY);
                bottomY -= 23;
            }
        }
        private void renderHeaders(DrawContext context) {
            if (!visible) return;
            switch (position) {
                case TOP -> renderTopHeader(context);
                case BOTTOM -> renderBottomHeader(context);
                case LEFT -> renderLeftHeader(context);
                case RIGHT -> renderRightHeader(context);
            }
        }
        private void renderTopHeader(DrawContext context) {
            context.fill(0, 0, ReScreen.this.width, headerSize, innerBackgroundColor);
            drawInnerBorder(context, 0, 0, ReScreen.this.width, headerSize, innerBorderColor);
            drawOuterBorder(context, 0, 0, ReScreen.this.width, headerSize, innerBackgroundColor);
        }
        private void renderBottomHeader(DrawContext context) {
            int y = ReScreen.this.height - headerSize;
            context.fill(0, y, ReScreen.this.width, ReScreen.this.height, innerBackgroundColor);
            drawInnerBorder(context, 0, y, ReScreen.this.width, headerSize, innerBorderColor);
            drawOuterBorder(context, 0, y, ReScreen.this.width, headerSize, innerBackgroundColor);
        }
        private void renderLeftHeader(DrawContext context) {
            context.fill(0, 0, headerSize, ReScreen.this.height, innerBackgroundColor);
            drawInnerBorder(context, 0, 0, headerSize, ReScreen.this.height, innerBorderColor);
            drawOuterBorder(context, 0, 0, headerSize, ReScreen.this.height, innerBackgroundColor);
        }
        private void renderRightHeader(DrawContext context) {
            int x = ReScreen.this.width - headerSize;
            context.fill(x, 0, ReScreen.this.width, ReScreen.this.height, innerBackgroundColor);
            drawInnerBorder(context, x, 0, headerSize, ReScreen.this.height, innerBorderColor);
            drawOuterBorder(context, x, 0, headerSize, ReScreen.this.height, innerBackgroundColor);
        }

        public boolean isInHeaderArea(double mouseX, double mouseY) {
            if (!visible) return false;
            return switch (position) {
                case TOP -> mouseY >= 0 && mouseY <= headerSize;
                case BOTTOM -> mouseY >= ReScreen.this.height - headerSize && mouseY <= ReScreen.this.height;
                case LEFT -> mouseX >= 0 && mouseX <= headerSize;
                case RIGHT -> mouseX >= ReScreen.this.width - headerSize && mouseX <= ReScreen.this.width;
            };
        }
    }

    public class SearchTextInputWidget extends TextInputWidget {
        public Consumer<String> onEnter;
        public Consumer<String> onTextChange;
        public SearchTextInputWidget(int x, int y, int width, int height) {
            super(x, y, width, height);
        }
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER && onEnter != null) {
                onEnter.accept(getText());
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        @Override
        public boolean charTyped(char chr, int modifiers) {
            boolean result = super.charTyped(chr, modifiers);
            if (onTextChange != null) {
                onTextChange.accept(getText());
                if (header().liveUpdate) {
                    onEnter.accept(getText());
                }
            }
            return result;
        }
    }

    public static class SearchMode {
        private boolean sortMode;
        private Consumer<String> onSearchEnter;
        private Consumer<String> onTextChange;
        public SearchMode(boolean sortMode) {
            this.sortMode = sortMode;
        }
        public boolean isSortMode() {
            return sortMode;
        }
        public void setOnSearchEnter(Consumer<String> onSearchEnter) {
            this.onSearchEnter = onSearchEnter;
        }
        public Consumer<String> getOnSearchEnter() {
            return onSearchEnter;
        }
        public void setOnTextChange(Consumer<String> onTextChange) {
            this.onTextChange = onTextChange;
        }
        public Consumer<String> getOnTextChange() {
            return onTextChange;
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
        this.initialWidth = this.width;
        this.initialHeight = this.height;
        if (headerBuilder != null) {
            headerBuilder.build();
        }
        rebuildAllWidgets();
        if (activeContainer != null) {
            activeContainer.size(this.width, this.height);
        }
        AnimatedWidget.setCornerSpeedMultiplier(CENTER, .3f);
        isInitialized = true;
    }

    private void rebuildAllWidgets() {
        if (tabsManager != null) {
            tabsManager.rebuildAllTabWidgets();
        }
        for (SidePanel sidePanel : sidePanelList) {
            sidePanel.rebuildWidgets();
        }
        if (activeContainer != null) {
            activeContainer.restoreStateToScreen();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.width != lastWidth || this.height != lastHeight) {
            lastWidth = this.width;
            lastHeight = this.height;
            needsLayoutUpdate = true;
        }

        if (needsLayoutUpdate) {
            updateAllLayouts();
            needsLayoutUpdate = false;
        }

        try {super.render(context, mouseX, mouseY, delta);} catch (ConcurrentModificationException ignored) {}
        animatedScaling(this);
        for (SidePanel sp : sidePanelList) {
            sp.render(context, mouseX, mouseY, delta);
        }
        if (activeContainer != null) {
            activeContainer.render(context, mouseX, mouseY, delta);
        }
        if (tabsManager != null) {
            tabsManager.render(context, mouseX, mouseY, delta);
        }
    }

    @Override public void renderBackground(DrawContext context , int mouseX, int mouseY, float delta) {
        super.renderBackground(context , mouseX, mouseY, delta );
        if (wallpaper && windowsBackground != null) {
            drawBufferedImage(context, windowsBackground, 0, 0, this.width, this.height);
        } else if (!background) {
            context.fill(0, 0, width, height, backgroundColor);
        }
        headerBuilder.renderHeaders(context);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (scaleScroll(verticalAmount)) return true;
        for (SidePanel sp : sidePanelList) {
            if (sp.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        }
        if (tabsManager != null && tabsManager.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        assert client != null;
        boolean shift = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        double scrollSpeed = shift ? 10 : 4;
        if (activeContainer != null && activeContainer.mouseScrolled(mouseX, mouseY, verticalAmount * scrollSpeed)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount , verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (SidePanel sp : sidePanelList) {
            if (sp.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        }
        if (tabsManager != null && tabsManager.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (activeContainer != null && activeContainer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean isInTabs = tabsManager != null && tabsManager.isInTabsArea(mouseX, mouseY);
        boolean isInHeader = headerBuilder.isInHeaderArea(mouseX, mouseY);
        boolean isInSidePanel = false;

        for (SidePanel sp : sidePanelList) {
            if (sp.mouseClicked(mouseX, mouseY, button)) {
                isInSidePanel = true;
                break;
            }
        }

        if (isInSidePanel) return true;

        if (tabsManager != null && tabsManager.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (activeContainer != null && activeContainer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (!isInTabs && !isInHeader && activeContainer != null && activeContainer.enableSelecting) {
            activeContainer.clearSelection();
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (SidePanel sp : sidePanelList) {
            if (sp.mouseReleased(mouseX, mouseY, button)) return true;
        }
        if (tabsManager != null && tabsManager.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (activeContainer != null && activeContainer.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tabsManager != null && tabsManager.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == GLFW.GLFW_KEY_TAB && ctrl) {
            if (tabsManager != null) {
                tabsManager.loopTabs(!shift);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_A && ctrl) {
            activeContainer.selectAllWidgets();
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
        needsLayoutUpdate = true;
    }
}