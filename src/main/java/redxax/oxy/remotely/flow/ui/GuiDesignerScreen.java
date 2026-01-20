package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.Visual;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.InfiniteScreen;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.layout.RestrictedLayout;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.IconButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GuiDesignerScreen extends InfiniteScreen {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_PADDING = 6;
    private static final int PANEL_PADDING = 8;
    private static final int HEADER_HEIGHT = 30;

    private final GuiDefinition gui;
    private final String serverId;
    private final Map<Integer, SlotBatchWidget> elementWidgets = new HashMap<>();
    private InspectorPanel inspector;
    private SlotBatchWidget selectedWidget;
    private GuiElement selectedElement;
    private Container palettePanel;
    private Container propertiesPanel;
    private IconButton backButton;

    private PaletteEntry draggingEntry;
    private boolean draggingPalette;
    private int hoverSlot = -1;
    private int selectedSlot = -1;

    private int gridOriginX;
    private int gridOriginY;
    private int gridWidth;
    private int gridHeight;
    private int centerX;
    private int centerWidth;
    private int rightPanelX;

    private int lastWidth = -1;
    private int lastHeight = -1;

    public GuiDesignerScreen(GuiDefinition gui) {
        this(gui, null);
    }

    public GuiDesignerScreen(GuiDefinition gui, String serverId) {
        super();
        this.gui = gui;
        this.serverId = serverId;

        allowZoom = false;
        allowPan = false;
        setZoomLevel(1.0f);
        setPan(0, 0);
    }

    @Override
    public void init() {
        super.init();
        buildPanels();
        syncElementWidgets();
        updateLayout(true);
    }

    private void buildPanels() {
        int panelHeight = Math.max(100, height - HEADER_HEIGHT - PANEL_PADDING);

        palettePanel = new Container("gui_palette", PANEL_PADDING, HEADER_HEIGHT, 200, panelHeight);
        palettePanel.columns(1).padding(6).layout(new RestrictedLayout()).enableSelecting(false).scrolling(true);

        propertiesPanel = new Container("gui_properties", width - 220 - PANEL_PADDING, HEADER_HEIGHT, 220, panelHeight);
        propertiesPanel.columns(1).padding(6).layout(new RestrictedLayout()).enableSelecting(false).scrolling(true);

        addDrawableChild(palettePanel, propertiesPanel);

        inspector = new InspectorPanel(gui, serverId);
        propertiesPanel.addWidget(inspector);

        createBackButton();
        buildPaletteItems();
    }

    private void createBackButton() {
        backButton = new IconButton.Builder()
            .label("Back")
            .pos(PANEL_PADDING, PANEL_PADDING)
            .size(60, 20)
            .onClick(this::close)
            .build();
        addDrawableChild(backButton);
    }

    private void buildPaletteItems() {
        palettePanel.clearWidgets();
        palettePanel.addWidget(new PaletteHeaderWidget("Presets"));
        List<PaletteEntry> presets = getPresetEntries();
        if (presets.isEmpty()) {
            palettePanel.addWidget(new PaletteNoteWidget("No presets found"));
        } else {
            for (PaletteEntry entry : presets) {
                palettePanel.addWidget(new PaletteItemWidget(entry));
            }
        }

        palettePanel.addWidget(new PaletteHeaderWidget("Standard Elements"));
        for (PaletteEntry entry : getStandardEntries()) {
            palettePanel.addWidget(new PaletteItemWidget(entry));
        }
    }

    private List<PaletteEntry> getPresetEntries() {
        return new ArrayList<>();
    }

    private List<PaletteEntry> getStandardEntries() {
        List<PaletteEntry> entries = new ArrayList<>();
        entries.add(new PaletteEntry("button", "Button", new Visual("STONE_BUTTON", "<white>Button</white>")));
        entries.add(new PaletteEntry("label", "Label", new Visual("PAPER", "<white>Label</white>")));
        entries.add(new PaletteEntry("placeholder", "Placeholder", new Visual("GRAY_STAINED_GLASS_PANE", "<white>Placeholder</white>")));
        return entries;
    }

    private void updateLayout(boolean force) {
        if (!force && width == lastWidth && height == lastHeight) {
            return;
        }

        lastWidth = width;
        lastHeight = height;

        int panelTop = HEADER_HEIGHT;
        int panelHeight = Math.max(100, height - panelTop - PANEL_PADDING);
        int leftPanelWidth = Math.max(180, (int) (width * 0.2f));
        int rightPanelWidth = Math.max(220, (int) (width * 0.2f));

        int leftX = PANEL_PADDING;
        rightPanelX = width - rightPanelWidth - PANEL_PADDING;
        centerX = leftX + leftPanelWidth + PANEL_PADDING;
        centerWidth = Math.max(120, rightPanelX - PANEL_PADDING - centerX);

        palettePanel.setPosition(leftX, panelTop);
        palettePanel.size(leftPanelWidth, panelHeight);

        propertiesPanel.setPosition(rightPanelX, panelTop);
        propertiesPanel.size(rightPanelWidth, panelHeight);

        inspector.setWidth(rightPanelWidth - propertiesPanel.getPadding() * 2);
        inspector.setHeight(panelHeight - propertiesPanel.getPadding() * 2);

        gridWidth = 9 * SLOT_SIZE;
        gridHeight = gui.getRows() * SLOT_SIZE;

        gridOriginX = centerX + Math.max(0, (centerWidth - gridWidth) / 2);
        gridOriginY = panelTop + Math.max(0, (panelHeight - gridHeight) / 2);

        positionElementWidgets();
    }

    private void syncElementWidgets() {
        Set<Integer> activeSlots = new HashSet<>();
        if (gui.getElements() != null) {
            for (GuiElement element : gui.getElements()) {
                if (element.getSlots() == null) {
                    continue;
                }
                for (int slot : element.getSlots()) {
                    activeSlots.add(slot);
                    SlotBatchWidget widget = elementWidgets.get(slot);
                    if (widget == null || widget.getElement() != element) {
                        widget = new SlotBatchWidget(0, 0, SLOT_SIZE - 2, SLOT_SIZE - 2, element);
                        elementWidgets.put(slot, widget);
                    }
                }
            }
        }

        elementWidgets.keySet().removeIf(slot -> !activeSlots.contains(slot));
        positionElementWidgets();
    }

    private void positionElementWidgets() {
        for (Map.Entry<Integer, SlotBatchWidget> entry : elementWidgets.entrySet()) {
            int slot = entry.getKey();
            SlotBatchWidget widget = entry.getValue();
            int slotX = gridOriginX + (slot % 9) * SLOT_SIZE + 1;
            int slotY = gridOriginY + (slot / 9) * SLOT_SIZE + 1;
            widget.setPosition(slotX, slotY);
            widget.setWidth(SLOT_SIZE - 2);
            widget.setHeight(SLOT_SIZE - 2);
        }
    }

    private int getSlotAt(int mouseX, int mouseY) {
        if (mouseX < gridOriginX || mouseY < gridOriginY
            || mouseX >= gridOriginX + gridWidth || mouseY >= gridOriginY + gridHeight) {
            return -1;
        }
        int gridX = (mouseX - gridOriginX) / SLOT_SIZE;
        int gridY = (mouseY - gridOriginY) / SLOT_SIZE;
        if (gridX < 0 || gridX >= 9 || gridY < 0 || gridY >= gui.getRows()) {
            return -1;
        }
        return gridY * 9 + gridX;
    }

    private int[] getSlotBounds(int slot) {
        int slotX = gridOriginX + (slot % 9) * SLOT_SIZE;
        int slotY = gridOriginY + (slot / 9) * SLOT_SIZE;
        return new int[] { slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE };
    }

    private SlotBatchWidget getWidgetAt(int mouseX, int mouseY) {
        for (SlotBatchWidget widget : elementWidgets.values()) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                return widget;
            }
        }
        return null;
    }

    private void selectWidget(SlotBatchWidget widget) {
        clearSelection();
        selectedWidget = widget;
        selectedElement = widget != null ? widget.getElement() : null;
        selectedSlot = findSlotForWidget(widget);
        if (selectedWidget != null) {
            selectedWidget.setSelected(true);
        }
        if (inspector != null) {
            inspector.setSelectedElement(selectedElement);
        }
    }

    private int findSlotForWidget(SlotBatchWidget widget) {
        if (widget == null) {
            return -1;
        }
        for (Map.Entry<Integer, SlotBatchWidget> entry : elementWidgets.entrySet()) {
            if (entry.getValue() == widget) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private void clearSelection() {
        if (selectedWidget != null) {
            selectedWidget.setSelected(false);
        }
        selectedWidget = null;
        selectedElement = null;
        selectedSlot = -1;
        if (inspector != null) {
            inspector.setSelectedElement(null);
        }
    }

    private void startPaletteDrag(PaletteEntry entry, double mouseX, double mouseY) {
        draggingEntry = entry;
        draggingPalette = true;
        hoverSlot = getSlotAt((int) mouseX, (int) mouseY);
    }

    private void placePaletteEntry(PaletteEntry entry, int slot) {
        if (entry == null || slot < 0) {
            return;
        }

        SlotBatchWidget existing = elementWidgets.get(slot);
        if (existing != null) {
            selectWidget(existing);
            return;
        }

        GuiElement newElement = new GuiElement();
        newElement.getSlots().add(slot);
        newElement.setVisual(entry.createVisual());
        gui.getElements().add(newElement);

        syncElementWidgets();
        SlotBatchWidget created = elementWidgets.get(slot);
        if (created != null) {
            selectWidget(created);
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateLayout(false);
        hoverSlot = draggingPalette ? getSlotAt(mouseX, mouseY) : -1;
        renderGrid(context);

        for (SlotBatchWidget widget : elementWidgets.values()) {
            widget.render(context, mouseX, mouseY, delta);
        }

        renderDragPreview(context, mouseX, mouseY);
    }

    private void renderGrid(IDrawContext context) {
        int gridBg = ThemeManager.getColor(ThemeColor.elementBackground);
        int gridBorder = ThemeManager.getColor(ThemeColor.innerBorder);
        int slotBg = ThemeManager.getColor(ThemeColor.innerBackground);
        int hoverColor = (ThemeManager.getAccent("nice").getAccentColor() & 0x00FFFFFF) | 0x33000000;
        int selectedColor = (ThemeManager.getAccent("calm").getAccentColor() & 0x00FFFFFF) | 0x55000000;

        context.fill(gridOriginX - GRID_PADDING, gridOriginY - GRID_PADDING,
            gridOriginX + gridWidth + GRID_PADDING, gridOriginY + gridHeight + GRID_PADDING, gridBg);

        for (int x = 0; x <= 9; x++) {
            int lineX = gridOriginX + x * SLOT_SIZE;
            context.fill(lineX, gridOriginY, lineX + 1, gridOriginY + gridHeight, gridBorder);
        }
        for (int y = 0; y <= gui.getRows(); y++) {
            int lineY = gridOriginY + y * SLOT_SIZE;
            context.fill(gridOriginX, lineY, gridOriginX + gridWidth, lineY + 1, gridBorder);
        }

        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < gui.getRows(); y++) {
                int slotX = gridOriginX + x * SLOT_SIZE + 1;
                int slotY = gridOriginY + y * SLOT_SIZE + 1;
                context.fill(slotX, slotY, slotX + SLOT_SIZE - 2, slotY + SLOT_SIZE - 2, slotBg);
            }
        }

        if (hoverSlot >= 0) {
            int[] bounds = getSlotBounds(hoverSlot);
            context.fill(bounds[0] + 1, bounds[1] + 1, bounds[2] - 1, bounds[3] - 1, hoverColor);
        }

        if (selectedSlot >= 0) {
            int[] bounds = getSlotBounds(selectedSlot);
            context.fill(bounds[0] + 1, bounds[1] + 1, bounds[2] - 1, bounds[3] - 1, selectedColor);
        }
    }

    private void renderDragPreview(IDrawContext context, int mouseX, int mouseY) {
        if (draggingPalette && draggingEntry != null) {
            context.drawText(draggingEntry.label, mouseX + 12, mouseY + 12,
                ThemeManager.getColor(ThemeColor.text), true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (button == 0) {
            SlotBatchWidget widget = getWidgetAt(mx, my);
            if (widget != null) {
                selectWidget(widget);
                return true;
            }
            if (getSlotAt(mx, my) >= 0) {
                clearSelection();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPalette && button == 0) {
            int slot = getSlotAt((int) mouseX, (int) mouseY);
            if (slot >= 0) {
                placePaletteEntry(draggingEntry, slot);
            }
            draggingPalette = false;
            draggingEntry = null;
            hoverSlot = -1;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingPalette) {
            hoverSlot = getSlotAt((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public GuiDefinition getGui() {
        return gui;
    }

    public InspectorPanel getInspector() {
        return inspector;
    }

    private static class PaletteEntry {
        private final String id;
        private final String label;
        private final Visual visual;

        private PaletteEntry(String id, String label, Visual visual) {
            this.id = id;
            this.label = label;
            this.visual = visual;
        }

        private Visual createVisual() {
            return visual != null ? visual.copy() : new Visual("STONE", "<white>Element</white>");
        }
    }

    private class PaletteHeaderWidget extends AnimatedWidget {
        private final String label;

        private PaletteHeaderWidget(String label) {
            super(0, 0, 120, 18, "");
            this.label = label;
            animateElevation = false;
            enableHoverColors = false;
            flat = true;
            transparent = true;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            ctx.drawText(label, getX() + 4, getY() + 4, ThemeManager.getColor(ThemeColor.textDark), true);
        }

        @Override
        public boolean canBeFocused() {
            return false;
        }
    }

    private class PaletteNoteWidget extends AnimatedWidget {
        private final String label;

        private PaletteNoteWidget(String label) {
            super(0, 0, 120, 18, "");
            this.label = label;
            animateElevation = false;
            enableHoverColors = false;
            flat = true;
            transparent = true;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            ctx.drawText(label, getX() + 6, getY() + 4, ThemeManager.getColor(ThemeColor.textDark), true);
        }

        @Override
        public boolean canBeFocused() {
            return false;
        }
    }

    private class PaletteItemWidget extends AnimatedWidget {
        private final PaletteEntry entry;

        private PaletteItemWidget(PaletteEntry entry) {
            super(0, 0, 120, 20, "");
            this.entry = entry;
            animateElevation = false;
            enableHoverColors = true;
            flat = true;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            int textColor = ThemeManager.getColor(ThemeColor.text);
            int dotColor = ThemeManager.getAccent("nice").getAccentColor();
            ctx.fill(getX() + 4, getY() + 6, getX() + 8, getY() + 10, dotColor);
            ctx.drawText(entry.label, getX() + 12, getY() + 6, textColor, true);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isMouseOver(mouseX, mouseY)) {
                startPaletteDrag(entry, mouseX, mouseY);
                return true;
            }
            return false;
        }
    }
}
