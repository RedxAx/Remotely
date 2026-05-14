package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.Visual;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.game.MinecraftGameItems;
import restudio.rescreen.game.tooltip.MinecraftTextComponents;
import restudio.rescreen.game.tooltip.MinecraftTooltip;
import restudio.rescreen.game.tooltip.MinecraftTooltipLine;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.layout.FreeLayout;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.ScrollSelectorWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.ResourceManager;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.lwjgl.glfw.GLFW;

import static restudio.rescreen.config.Config.desktopMode;

public class GuiDesignerScreen extends ReScreen implements DesktopWindowBehaviorProvider {
    private static final int GRID_COLUMNS = 9;
    private static final int PANEL_PADDING = 8;
    private static final int MIN_SLOT_SIZE = 16;
    private static final int MAX_SLOT_SIZE = 26;
    private static final int SLOT_BASE_SIZE = 18;
    private static final int GUI_TEXTURE_WIDTH = 176;
    private static final int GUI_SIDE_MARGIN = 7;
    private static final int GUI_TOP_MARGIN = 17;
    private static final int GUI_TITLE_X = 8;
    private static final int GUI_TITLE_Y = 6;
    private static final int GUI_PLAYER_INV_OFFSET = 14;
    private static final int GUI_HOTBAR_OFFSET = 72;
    private static final int GUI_PLAYER_INV_HEIGHT = 96;
    private static final int GUI_BOTTOM_TEXTURE_Y = 126;
    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int PLAYER_INVENTORY_ROWS = 4;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int OVERLAY_COLOR = 0xA0101010;
    private static final int INSPECTOR_PANEL_WIDTH = 150;
    private static final String MATERIAL_OPTIONS_SOURCE = "server:minecraft:material";
    private static final Set<GuiDesignerScreen> OPEN_SCREENS = new CopyOnWriteArraySet<>();
    private static final List<String> ACTION_MODE_OPTIONS = List.of("Flows", "Menus", "Command");

    private static final List<String> FALLBACK_MATERIAL_OPTIONS = List.of(
        "STONE", "COBBLESTONE", "OAK_PLANKS", "OAK_LOG", "GLASS", "GLASS_PANE",
        "GRAY_STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE",
        "RED_STAINED_GLASS_PANE", "GREEN_STAINED_GLASS_PANE", "BLUE_STAINED_GLASS_PANE",
        "BARRIER", "CHEST", "ENDER_CHEST", "ANVIL", "BOOK", "PAPER", "MAP",
        "COMPASS", "CLOCK", "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
        "NETHERITE_INGOT", "REDSTONE", "AMETHYST_SHARD", "ENDER_PEARL",
        "TOTEM_OF_UNDYING", "PLAYER_HEAD", "NAME_TAG"
    );

    private enum GuiActionMode {
        FLOWS,
        MENUS,
        COMMAND;

        private static GuiActionMode fromIndex(int index) {
            GuiActionMode[] modes = values();
            return modes[Math.clamp(index, 0, modes.length - 1)];
        }
    }

    private final GuiDefinition gui;
    private final String serverId;
    private final Object parent;
    private final ReSyncStudioPanelState panelState = new ReSyncStudioPanelState().padding(6);
    private final boolean forceSuperScreen;

    private Container gridContainer;
    private SidePanel inspectorPanel;
    private TooltipOverlayWidget tooltipOverlay;
    private ItemSelectorWidget materialSelector;
    private ScrollSelectorWidget actionTypeSelector;
    private ItemSelectorWidget flowSelector;
    private ItemSelectorWidget guiSelector;
    private TextInputWidget commandInput;

    private final Map<Integer, SlotButton> slotButtons = new HashMap<>();
    private final Map<Integer, GuiElement> slotElements = new HashMap<>();
    private final Set<Integer> dragPreviewSlots = new HashSet<>();

    private ToggleWidget placeToggle;
    private ToggleWidget extendInventoryToggle;
    private final List<AnimatedWidget> inspectorDynamicWidgets = new ArrayList<>();
    private boolean placeMode;
    private boolean draggingPlacement;
    private int dragStartSlot = -1;
    private int dragEndSlot = -1;
    private GuiElement dragResizeElement;
    private GuiElement selectedElement;
    private GuiElement lastInspectorElement;
    private GuiActionMode inspectorActionMode = GuiActionMode.FLOWS;
    private boolean preserveInspectorActionMode;
    private SlotButton hoveredSlotButton;
    private Visual placementTemplate = new Visual("PAPER", "Item");

    private TextInputWidget guiTitleInput;
    private DropDownWidget<Integer> guiRowsSelect;

    private int gridOriginX;
    private int gridOriginY;
    private int guiBackgroundX;
    private int guiBackgroundY;
    private int guiBackgroundWidth;
    private int guiBackgroundHeight;
    private int playerInventoryOriginY;
    private int hotbarOriginY;
    private float guiScale = 1f;
    private int slotSize;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private boolean closingRequested;
    private boolean closeCompleted;
    private static final int MAX_UNDO_SIZE = 50;
    private final List<GuiSnapshot> undoStack = new ArrayList<>();
    private final List<GuiSnapshot> redoStack = new ArrayList<>();
    private boolean applyingHistory;

    private static class GuiSnapshot {
        private final String title;
        private final int rows;
        private final boolean extendToPlayerInventory;
        private final List<GuiElement> elements;
        private final int selectedIndex;
        private final Visual placementTemplate;

        private GuiSnapshot(String title, int rows, boolean extendToPlayerInventory, List<GuiElement> elements, int selectedIndex, Visual placementTemplate) {
            this.title = title;
            this.rows = rows;
            this.extendToPlayerInventory = extendToPlayerInventory;
            this.elements = elements;
            this.selectedIndex = selectedIndex;
            this.placementTemplate = placementTemplate;
        }
    }

    public GuiDesignerScreen(GuiDefinition gui, String serverId, Object parent) {
        this(gui, serverId, parent, !(parent instanceof Screen));
    }

    public GuiDesignerScreen(GuiDefinition gui, String serverId, Object parent, boolean forceSuperScreen) {
        super();
        this.gui = gui;
        this.serverId = serverId;
        this.parent = parent;
        this.forceSuperScreen = forceSuperScreen;
        this.autoResizeContainers = false;
        OPEN_SCREENS.add(this);
        ensureGuiDefaults();
    }

    public static void refreshCatalogForServer(String serverId) {
        for (GuiDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.serverId)) {
                screen.refreshMaterialSelector();
            }
        }
    }

    public String getDesktopAppId() {
        return "gui-designer";
    }

    public String getDesktopAppTitle() {
        return "GUI Designer";
    }

    public String getDesktopAppIconPath() {
        return "slot.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return DesktopWindowBehavior.SINGLETON;
    }

    @Override
    public boolean shouldForceSuperScreen() {
        return desktopMode && forceSuperScreen;
    }

    @Override
    public void init() {
        super.init();
        closingRequested = false;
        closeCompleted = false;
        buildHeader();
        buildContainers();
        buildInspectorPanel();
        rebuildGrid();
        updateLayout(true);
    }

    @Override
    public void close() {
        requestClose();
    }


    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateLayout(false);
        updateCloseAnimation();
        hoveredSlotButton = null;
        super.render(context, mouseX, mouseY, delta);
        updateHoveredSlot(mouseX, mouseY);
    }

    @Override
    public void renderBackground(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        if (forceSuperScreen) {
            context.fill(0, 0, width, height, OVERLAY_COLOR);
        }
        renderGuiPreview(context);
    }

    private void requestClose() {
        if (closeCompleted) {
            return;
        }
        if (!closingRequested) {
            closingRequested = true;
            if (inspectorPanel != null) {
                inspectorPanel.hide();
            }
        }
        updateCloseAnimation();
    }

    private void updateCloseAnimation() {
        if (!closingRequested || closeCompleted) {
            return;
        }
        if (inspectorPanel == null || inspectorPanel.getAnimatedWidth() <= 1f) {
            finishClose();
        }
    }

    private void finishClose() {
        if (closeCompleted) {
            return;
        }
        closeCompleted = true;
        OPEN_SCREENS.remove(this);
        super.close();
        if (parent != null) {
            if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
                RemotelyClient.INSTANCE.getHost().openParentScreen(this, parent);
            } else if (parent instanceof Screen screen) {
                ScreenManager.getInstance().setScreen(screen);
            }
        }
    }

    private void renderGuiPreview(IDrawContext context) {
        if (guiBackgroundWidth <= 0 || guiBackgroundHeight <= 0) {
            return;
        }
        MinecraftGameAssets gameAssets = getGameAssets();
        MinecraftAssetReference textureReference = gameAssets.containerTexture("generic_54.png");
        BufferedImage texture = gameAssets.getImage(textureReference);
        if (texture == null) {
            return;
        }
        int rows = Math.max(1, gui.getRows());
        int topHeight = GUI_TOP_MARGIN + rows * SLOT_BASE_SIZE;
        int topHeightScaled = Math.round(topHeight * guiScale);
        BufferedImage top = texture != ResourceManager.getInstance().getMissingTexture()
            ? texture.getSubimage(0, 0, GUI_TEXTURE_WIDTH, Math.min(topHeight, texture.getHeight()))
            : null;
        drawGuiTexture(context, gameAssets, textureReference, top, guiBackgroundX, guiBackgroundY, guiBackgroundWidth, topHeightScaled, 0, 0, GUI_TEXTURE_WIDTH, topHeight);

        if (texture.getHeight() >= GUI_BOTTOM_TEXTURE_Y + GUI_PLAYER_INV_HEIGHT) {
            BufferedImage bottom = texture.getSubimage(0, GUI_BOTTOM_TEXTURE_Y, GUI_TEXTURE_WIDTH, GUI_PLAYER_INV_HEIGHT);
            int bottomY = guiBackgroundY + topHeightScaled;
            int bottomHeightScaled = Math.round(GUI_PLAYER_INV_HEIGHT * guiScale);
            drawGuiTexture(context, gameAssets, textureReference, bottom, guiBackgroundX, bottomY, guiBackgroundWidth, bottomHeightScaled, 0, GUI_BOTTOM_TEXTURE_Y, GUI_TEXTURE_WIDTH, GUI_PLAYER_INV_HEIGHT);
        } else {
            int bottomY = guiBackgroundY + topHeightScaled;
            int bottomHeightScaled = Math.round(GUI_PLAYER_INV_HEIGHT * guiScale);
            drawGuiTexture(context, gameAssets, textureReference, null, guiBackgroundX, bottomY, guiBackgroundWidth, bottomHeightScaled, 0, GUI_BOTTOM_TEXTURE_Y, GUI_TEXTURE_WIDTH, GUI_PLAYER_INV_HEIGHT);
        }

        String title = gui.getTitle() != null && !gui.getTitle().isBlank() ? gui.getTitle() : gui.getId();
        if (title != null && !title.isBlank()) {
            int titleX = guiBackgroundX + Math.round(GUI_TITLE_X * guiScale);
            int titleY = guiBackgroundY + Math.round(GUI_TITLE_Y * guiScale);
            context.drawText(title, titleX, titleY, TITLE_COLOR, false);
        }
        int invLabelX = guiBackgroundX + Math.round(GUI_TITLE_X * guiScale);
        int invLabelY = guiBackgroundY + Math.round((GUI_TOP_MARGIN + rows * SLOT_BASE_SIZE + 3) * guiScale);
        context.drawText("Inventory", invLabelX, invLabelY, TITLE_COLOR, false);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        updateLayout(true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && gridContainer != null) {
            int slot = getSlotAt((int) mouseX, (int) mouseY);
            if (placeMode && slot >= 0 && !slotElements.containsKey(slot)) {
                startPlacementDrag(slot, null);
                return true;
            }
            if (gridContainer.isMouseOver(mouseX, mouseY)) {
                selectElement(null);
                return true;
            }
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingPlacement && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int slot = getSlotAt((int) mouseX, (int) mouseY);
            if (slot >= 0 && slot != dragEndSlot) {
                dragEndSlot = slot;
                updateDragPreview();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPlacement && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            finishPlacementDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean hasControl = hasControlDown();
        boolean hasShift = hasShiftDown();
        if (hasControl && !isGuiKeyboardInputFocused()) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (hasShift) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }
        if (selectedElement != null && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
            if (!isAnyPopupOpen()) {
                if (!isGuiKeyboardInputFocused()) {
                    removeElement(selectedElement);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isGuiKeyboardInputFocused() {
        return getFocusedWidget() instanceof TextInputWidget
                || getFocusedWidget() instanceof TextAreaWidget
                || getFocusedWidget() instanceof ItemSelectorWidget;
    }

    private void buildHeader() {
        header().reset();
        header().addRight("close.png", this::requestClose, "Back");
        header().addRight("save.png", this::saveGui, "Save GUI");
        placeToggle = new ToggleWidget.Builder()
            .label("Place")
            .size(70, 18)
            .toggled(placeMode)
            .onChange(this::setPlaceMode)
            .build();
        header().addLeft(placeToggle);
        header().build();
    }

    private void buildContainers() {
        gridContainer = createContainer("gui_grid", 0, 0, 200, 200);
        gridContainer.layout(new FreeLayout()).columns(1).padding(0).scrolling(false).enableSelecting(false).backgroundDrawing(false);
        addDrawableChild(gridContainer);

        tooltipOverlay = new TooltipOverlayWidget();
        tooltipOverlay.setLayer(1000);
        addDrawableChild(tooltipOverlay);

        inspectorPanel = createSidePanel("gui_inspector").width(INSPECTOR_PANEL_WIDTH).y(0).height(height).show();
        inspectorPanel.container().layout(new ManagedLayout()).columns(1).padding(panelState.padding()).scrolling(true).enableSelecting(false);
    }

    private void buildInspectorPanel() {
        if (inspectorPanel == null) {
            return;
        }
        Container container = inspectorPanel.container();
        float scrollOffset = container.getScrollOffset();
        ensureInspectorBase(container);
        rebuildInspectorItemSection(container);
        container.updateWidgetPositions();
        container.setScrollOffset(scrollOffset);
    }

    private void ensureInspectorBase(Container container) {
        if (guiTitleInput != null && guiRowsSelect != null && extendInventoryToggle != null) {
            return;
        }
        inspectorDynamicWidgets.clear();
        int rowWidth = inspectorRowWidth();
        guiTitleInput = new TextInputWidget.Builder()
            .text(gui.getTitle() != null ? gui.getTitle() : "")
            .placeholder("Title")
            .forcePlaceholder(false)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(this::updateGuiTitle)
            .build();
        disableEntrance(guiTitleInput);
        container.addWidget(panelState.row("Title", guiTitleInput, rowWidth));

        List<Integer> rowOptions = List.of(1, 2, 3, 4, 5, 6);
        guiRowsSelect = new DropDownWidget.Builder<>(rowOptions)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .selectedItem(Math.clamp(gui.getRows(), 1, 6))
            .displayFunction(rows -> rows + (rows == 1 ? " row" : " rows"))
            .onSelectionChanged(this::updateRows)
            .entranceAnimation(false)
            .build();
        disableEntrance(guiRowsSelect);
        container.addWidget(panelState.row("Rows", guiRowsSelect, rowWidth));

        extendInventoryToggle = new ToggleWidget.Builder()
            .label("Player Inventory")
            .size(rowWidth, 20)
            .toggled(gui.isExtendToPlayerInventory())
            .onChange(this::setExtendInventoryMode)
            .entranceAnimation(false)
            .build();
        disableEntrance(extendInventoryToggle);
        container.addWidget(panelState.row("Inventory", extendInventoryToggle, rowWidth));
    }

    private void rebuildInspectorItemSection(Container container) {
        if (selectedElement == lastInspectorElement && !inspectorDynamicWidgets.isEmpty()) {
            if (materialSelector != null) {
                refreshMaterialSelector();
            }
            if (flowSelector != null) {
                refreshFlowSelector();
            }
            if (guiSelector != null) {
                refreshGuiSelector();
            }
            return;
        }
        lastInspectorElement = selectedElement;
        for (AnimatedWidget widget : inspectorDynamicWidgets) {
            container.removeWidget(widget);
        }
        inspectorDynamicWidgets.clear();
        materialSelector = null;
        actionTypeSelector = null;
        flowSelector = null;
        guiSelector = null;
        commandInput = null;

        int rowWidth = inspectorRowWidth();
        if (selectedElement == null) {
            AnimatedButton hint = panelState.hint("Select Item", rowWidth);
            insertInspectorDynamic(container, hint);
            return;
        }

        Visual visual = ensureVisual(selectedElement);
        if (!preserveInspectorActionMode) {
            inspectorActionMode = resolveActionMode(selectedElement);
        }

        TextInputWidget slotsInput = new TextInputWidget.Builder()
            .text(formatSlots(selectedElement))
            .placeholder("Slots")
            .forcePlaceholder(false)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .active(false)
            .build();
        disableEntrance(slotsInput);
        AnimatedWidget slotsRow = panelState.row("Slots", slotsInput, rowWidth);
        insertInspectorDynamic(container, slotsRow);

        TextInputWidget nameInput = new TextInputWidget.Builder()
            .text(visual.getName() != null ? visual.getName() : "")
            .placeholder("Name")
            .forcePlaceholder(false)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(text -> {
                updateVisualName(visual, text);
                applySlotState();
            })
            .build();
        disableEntrance(nameInput);
        AnimatedWidget nameRow = panelState.row("Name", nameInput, rowWidth);
        insertInspectorDynamic(container, nameRow);

        materialSelector = new ItemSelectorWidget.Builder(this)
            .size(rowWidth, 140)
            .embedded(true)
            .dismissOnSelect(false)
            .emptyMessage("No items")
            .build();
        disableEntrance(materialSelector);
        AnimatedWidget materialRow = panelState.row("Material", materialSelector, rowWidth);
        materialRow.setHeight(156);
        insertInspectorDynamic(container, materialRow);

        actionTypeSelector = new ScrollSelectorWidget.Builder()
            .options(ACTION_MODE_OPTIONS)
            .selectedIndex(inspectorActionMode.ordinal())
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(index -> setActionMode(GuiActionMode.fromIndex(index)))
            .build();
        disableEntrance(actionTypeSelector);
        AnimatedWidget actionTypeRow = panelState.row("Action", actionTypeSelector, rowWidth);
        insertInspectorDynamic(container, actionTypeRow);

        buildActionEditor(container, rowWidth);

        TextInputWidget modelInput = new TextInputWidget.Builder()
            .text(visual.getModelData() != null ? String.valueOf(visual.getModelData()) : "")
            .placeholder("Model Data")
            .forcePlaceholder(false)
            .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(text -> updateModelData(visual, text))
            .build();
        disableEntrance(modelInput);
        AnimatedWidget modelRow = panelState.row("Model Data", modelInput, rowWidth);
        insertInspectorDynamic(container, modelRow);

        String loreText = visual.getLore() != null ? String.join("\n", visual.getLore()) : "";
        TextAreaWidget loreInput = new TextAreaWidget.Builder()
            .text(loreText)
            .placeholder("Lore")
            .size(rowWidth, 70)
            .onChange(text -> updateLore(visual, text))
            .build();
        disableEntrance(loreInput);
        AnimatedWidget loreRow = panelState.row("Lore", loreInput, rowWidth);
        loreRow.setHeight(88);
        insertInspectorDynamic(container, loreRow);

        AnimatedButton removeButton = new AnimatedButton.Builder()
            .label("Remove Item")
            .size(rowWidth, 22)
            .accentType(ThemeManager.getAccent("danger"))
            .entranceAnimation(false)
            .onClick(() -> removeElement(selectedElement))
            .build();
        disableEntrance(removeButton);
        insertInspectorDynamic(container, removeButton);
        if (materialSelector != null) {
            materialSelector.openEmbedded();
            refreshMaterialSelector();
        }
        if (flowSelector != null) {
            flowSelector.openEmbedded();
            refreshFlowSelector();
        }
        if (guiSelector != null) {
            guiSelector.openEmbedded();
            refreshGuiSelector();
        }
    }

    private void buildActionEditor(Container container, int rowWidth) {
        switch (inspectorActionMode) {
            case FLOWS -> {
                flowSelector = new ItemSelectorWidget.Builder(this)
                    .size(rowWidth, 140)
                    .embedded(true)
                    .dismissOnSelect(false)
                    .emptyMessage("No flows")
                    .build();
                disableEntrance(flowSelector);
                AnimatedWidget flowSelectorRow = panelState.row("Flow", flowSelector, rowWidth);
                flowSelectorRow.setHeight(156);
                insertInspectorDynamic(container, flowSelectorRow);

                RowWidget flowRow = new RowWidget.Builder()
                    .size(rowWidth, 22)
                    .addWidget(new AnimatedButton.Builder()
                        .label("Open Flow")
                        .size(rowWidth, 22)
                        .entranceAnimation(false)
                        .onClick(this::openSelectedFlow)
                        .build())
                    .build();
                disableEntrance(flowRow);
                insertInspectorDynamic(container, flowRow);
            }
            case MENUS -> {
                guiSelector = new ItemSelectorWidget.Builder(this)
                    .size(rowWidth, 120)
                    .embedded(true)
                    .dismissOnSelect(false)
                    .emptyMessage("No menus")
                    .build();
                disableEntrance(guiSelector);
                AnimatedWidget guiSelectorRow = panelState.row("Menu", guiSelector, rowWidth);
                guiSelectorRow.setHeight(136);
                insertInspectorDynamic(container, guiSelectorRow);

                RowWidget menuRow = new RowWidget.Builder()
                    .size(rowWidth, 22)
                    .addWidget(new AnimatedButton.Builder()
                        .label("Open Menu")
                        .size(rowWidth, 22)
                        .entranceAnimation(false)
                        .onClick(this::openSelectedMenu)
                        .build())
                    .build();
                disableEntrance(menuRow);
                insertInspectorDynamic(container, menuRow);
            }
            case COMMAND -> {
                commandInput = new TextInputWidget.Builder()
                    .text(selectedElement.getCommand() != null ? selectedElement.getCommand() : "")
                    .placeholder("Command")
                    .forcePlaceholder(false)
                    .size(rowWidth, ReSyncStudioPanelState.FIELD_HEIGHT)
                    .onChange(this::applyCommand)
                    .build();
                disableEntrance(commandInput);
                AnimatedWidget commandRow = panelState.row("Command", commandInput, rowWidth);
                insertInspectorDynamic(container, commandRow);
            }
        }
    }

    private void refreshMaterialSelector() {
        if (materialSelector == null) {
            return;
        }
        materialSelector.clearItems();
        Set<String> options = new HashSet<>(materialOptions());
        if (selectedElement != null && selectedElement.getVisual() != null) {
            String current = selectedElement.getVisual().getMaterial();
            if (current != null && !current.isBlank()) {
                options.add(current);
            }
        }
        List<String> sorted = new ArrayList<>(options);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        for (String material : sorted) {
            materialSelector.addItem(formatMaterialLabel(material), () -> applyMaterial(material));
        }
        if (selectedElement != null && selectedElement.getVisual() != null) {
            String current = selectedElement.getVisual().getMaterial();
            setSelectorSelection(materialSelector, formatMaterialLabel(current));
        }
    }

    private void insertInspectorDynamic(Container container, AnimatedWidget widget) {
        ReSyncStudioPanelState.disableEntrance(widget);
        container.addWidget(widget);
        inspectorDynamicWidgets.add(widget);
    }

    private int inspectorRowWidth() {
        if (inspectorPanel == null) {
            return Math.max(120, INSPECTOR_PANEL_WIDTH - panelState.padding() * 2);
        }
        return Math.max(120, inspectorPanel.getDesiredWidth() - panelState.padding() * 2);
    }

    private List<String> materialOptions() {
        List<String> values = OptionCatalogCache.getInstance().getValues(serverId, MATERIAL_OPTIONS_SOURCE);
        if (!values.isEmpty()) {
            return values;
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            flowManager.ensureFlowClient(serverId).requestOptionCatalog(MATERIAL_OPTIONS_SOURCE);
        }
        return FALLBACK_MATERIAL_OPTIONS;
    }

    private void refreshFlowSelector() {
        if (flowSelector == null) {
            return;
        }
        flowSelector.clearItems();
        flowSelector.addItem("none", () -> applyFlow(null));
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            List<String> flowIds = new ArrayList<>(flowManager.getFlowsForServer(serverId).keySet());
            flowIds.sort(String.CASE_INSENSITIVE_ORDER);
            for (String flowId : flowIds) {
                String label = flowManager.getFlowName(serverId, flowId);
                flowSelector.addItem(label, () -> applyFlow(flowId));
            }
            String selectedFlow = selectedElement != null ? selectedElement.getFlowId() : null;
            String selectedLabel = selectedFlow == null || selectedFlow.isBlank()
                ? "none"
                : flowManager.getFlowName(serverId, selectedFlow);
            setSelectorSelection(flowSelector, selectedLabel);
        }
    }

    private void refreshGuiSelector() {
        if (guiSelector == null) {
            return;
        }
        guiSelector.clearItems();
        guiSelector.addItem("none", () -> applyOpenGui(null));
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            List<String> guiIds = new ArrayList<>(flowManager.getGuisForServer(serverId).keySet());
            guiIds.sort(String.CASE_INSENSITIVE_ORDER);
            for (String guiId : guiIds) {
                String label = flowManager.getGuiName(serverId, guiId);
                guiSelector.addItem(label, () -> applyOpenGui(guiId));
            }
            String selectedGui = selectedElement != null ? selectedElement.getOpenGuiId() : null;
            String selectedLabel = selectedGui == null || selectedGui.isBlank()
                ? "none"
                : flowManager.getGuiName(serverId, selectedGui);
            setSelectorSelection(guiSelector, selectedLabel);
        }
    }

    private GuiActionMode resolveActionMode(GuiElement element) {
        if (element == null) {
            return GuiActionMode.FLOWS;
        }
        if (element.getOpenGuiId() != null && !element.getOpenGuiId().isBlank()) {
            return GuiActionMode.MENUS;
        }
        if (element.getCommand() != null && !element.getCommand().isBlank()) {
            return GuiActionMode.COMMAND;
        }
        return GuiActionMode.FLOWS;
    }

    private void setActionMode(GuiActionMode mode) {
        if (mode == null || inspectorActionMode == mode) {
            return;
        }
        inspectorActionMode = mode;
        clearInactiveActions(mode);
        lastInspectorElement = null;
        preserveInspectorActionMode = true;
        buildInspectorPanel();
        preserveInspectorActionMode = false;
        updateLayout(true);
    }

    private void clearInactiveActions(GuiActionMode mode) {
        if (selectedElement == null) {
            return;
        }
        boolean changed = false;
        if (mode != GuiActionMode.FLOWS && selectedElement.getFlowId() != null && !selectedElement.getFlowId().isBlank()) {
            changed = true;
        }
        if (mode != GuiActionMode.MENUS && selectedElement.getOpenGuiId() != null && !selectedElement.getOpenGuiId().isBlank()) {
            changed = true;
        }
        if (mode != GuiActionMode.COMMAND && selectedElement.getCommand() != null && !selectedElement.getCommand().isBlank()) {
            changed = true;
        }
        if (!changed) {
            return;
        }
        captureSnapshot();
        if (mode != GuiActionMode.FLOWS) {
            selectedElement.setFlowId(null);
        }
        if (mode != GuiActionMode.MENUS) {
            selectedElement.setOpenGuiId(null);
        }
        if (mode != GuiActionMode.COMMAND) {
            selectedElement.setCommand(null);
        }
    }

    private void applyMaterial(String material) {
        if (selectedElement == null) {
            placementTemplate.setMaterial(material);
            return;
        }
        Visual visual = ensureVisual(selectedElement);
        if ((material == null && visual.getMaterial() == null)
            || (material != null && material.equals(visual.getMaterial()))) {
            return;
        }
        captureSnapshot();
        visual.setMaterial(material);
        placementTemplate = visual.copy();
        setSelectorSelection(materialSelector, formatMaterialLabel(material));
        applySlotState();
    }

    private void applyFlow(String flowId) {
        if (selectedElement == null) {
            return;
        }
        if ((flowId == null && (selectedElement.getFlowId() == null || selectedElement.getFlowId().isBlank()))
            || (flowId != null && flowId.equals(selectedElement.getFlowId()))) {
            return;
        }
        captureSnapshot();
        selectedElement.setFlowId(flowId);
        if (flowId != null && !flowId.isBlank()) {
            selectedElement.setOpenGuiId(null);
            selectedElement.setCommand(null);
            setSelectorSelection(guiSelector, "none");
            if (commandInput != null) {
                commandInput.setText("");
            }
        }
        String label = flowId == null || flowId.isBlank() ? "none" : formatFlowLabel(flowId);
        setSelectorSelection(flowSelector, label);
    }

    private void applyOpenGui(String guiId) {
        if (selectedElement == null) {
            return;
        }
        if ((guiId == null && (selectedElement.getOpenGuiId() == null || selectedElement.getOpenGuiId().isBlank()))
            || (guiId != null && guiId.equals(selectedElement.getOpenGuiId()))) {
            return;
        }
        captureSnapshot();
        selectedElement.setOpenGuiId(guiId);
        if (guiId != null && !guiId.isBlank()) {
            selectedElement.setFlowId(null);
            selectedElement.setCommand(null);
            setSelectorSelection(flowSelector, "none");
            if (commandInput != null) {
                commandInput.setText("");
            }
        }
        String label = guiId == null || guiId.isBlank() ? "none" : formatGuiLabel(guiId);
        setSelectorSelection(guiSelector, label);
    }

    private void applyCommand(String command) {
        if (selectedElement == null) {
            return;
        }
        String next = command != null && !command.isBlank() ? command.trim() : null;
        String current = selectedElement.getCommand();
        if ((current == null && next == null) || (current != null && current.equals(next))) {
            return;
        }
        captureSnapshot();
        selectedElement.setCommand(next);
        if (next != null) {
            selectedElement.setFlowId(null);
            selectedElement.setOpenGuiId(null);
            setSelectorSelection(flowSelector, "none");
            setSelectorSelection(guiSelector, "none");
        }
    }

    private void openSelectedFlow() {
        if (selectedElement == null) {
            return;
        }
        String flowId = selectedElement.getFlowId();
        if (flowId == null || flowId.isBlank()) {
            return;
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            flowManager.openFlowEditor(serverId, null, flowId);
        }
    }

    private void openSelectedMenu() {
        if (selectedElement == null) {
            return;
        }
        String guiId = selectedElement.getOpenGuiId();
        if (guiId == null || guiId.isBlank()) {
            return;
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            flowManager.openGuiDesigner(serverId, null, guiId, this);
        }
    }

    private void showCreateFlowPopup() {
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null || serverId == null) {
            new Notification("Error", "No server connection", Notification.Type.ERROR);
            return;
        }

        PopupWidget.Builder builder = new PopupWidget.Builder("Create New Flow").setResizable(false);

        TextInputWidget idInput = new TextInputWidget.Builder()
            .placeholder("Flow ID (e.g. openLootBox)")
            .size(200, 22)
            .build();

        builder.addRow("ID", true, 22, idInput);

        ToggleWidget functionToggle = new ToggleWidget.Builder()
            .label("Create As Function")
            .size(200, 18)
            .toggled(false)
            .build();

        builder.addRow("", true, 18, functionToggle);

        PopupWidget[] popupRef = new PopupWidget[1];

        AnimatedButton createBtn = new AnimatedButton.Builder()
            .label("Create")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String id = idInput.getText();
                if (id != null && id.matches("^[a-zA-Z0-9_]+$")) {
                    if (flowManager.getFlowsForServer(serverId).containsKey(id)) {
                        new Notification("Error", "Flow ID already exists", Notification.Type.ERROR);
                        return;
                    }
                    FlowGraph graph = flowManager.createFlow(serverId, id, functionToggle.getValue());
                    if (popupRef[0] != null) {
                        popupRef[0].hide();
                    }
                    applyFlow(graph.getId());
                    refreshFlowSelector();
                    flowManager.openFlowEditor(serverId, null, graph.getId());
                } else {
                    new Notification("Error", "Invalid ID. Alphanumeric only.", Notification.Type.ERROR);
                }
            })
            .build();

        builder.addRow("", true, 20, createBtn);

        popupRef[0] = builder.build();
        addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    private void rebuildGrid() {
        gridContainer.clearWidgets();
        slotButtons.clear();

        refreshSlotMappings();
        buildSlotButtons();
        applySlotState();
        positionGridWidgets();
    }

    private void refreshSlotMappings() {
        slotElements.clear();
        if (gui.getElements() == null) {
            return;
        }
        int maxSlot = getTotalSlots();
        for (GuiElement element : gui.getElements()) {
            if (element == null || element.getSlots() == null) {
                continue;
            }
            for (Integer slot : element.getSlots()) {
                if (slot == null || slot < 0 || slot >= maxSlot) {
                    continue;
                }
                slotElements.put(slot, element);
            }
        }
    }

    private void buildSlotButtons() {
        int totalSlots = getTotalSlots();
        for (int slot = 0; slot < totalSlots; slot++) {
            SlotButton button = new SlotButton(slot, MIN_SLOT_SIZE, this::handleSlotClick);
            button.setElement(slotElements.get(slot));
            gridContainer.addWidget(button);
            slotButtons.put(slot, button);
        }
    }

    private void applySlotState() {
        int previewColor = ThemeManager.getAccent("calm").getAccentColor();
        int selectedColor = ThemeManager.getAccent("nice").getAccentColor();
        for (SlotButton button : slotButtons.values()) {
            boolean preview = dragPreviewSlots.contains(button.slot);
            GuiElement element = slotElements.get(button.slot);
            boolean isSelected = element != null && element == selectedElement;
            button.setElement(element);
            button.setPreview(preview);
            button.setHighlight(preview ? previewColor : (isSelected ? selectedColor : 0), isSelected);
        }
    }

    private void updateHoveredSlot(int mouseX, int mouseY) {
        if (draggingPlacement || isAnyPopupOpen()) {
            return;
        }
        for (SlotButton button : slotButtons.values()) {
            if (button.hasElement() && button.isMouseOver(mouseX, mouseY)) {
                hoveredSlotButton = button;
                return;
            }
        }
    }

    private void drawGuiSlotTooltip(IDrawContext context, int mouseX, int mouseY) {
        if (hoveredSlotButton == null || !hoveredSlotButton.hasElement()) {
            return;
        }
        GuiElement element = hoveredSlotButton.getElement();
        Visual visual = element.getVisual();
        if (visual == null) {
            return;
        }
        MinecraftTooltip fallback = buildVisualFallbackTooltip(visual);
        MinecraftRenderItem renderItem = hoveredSlotButton.getRenderItem();
        if (renderItem != null) {
            context.drawMinecraftItemTooltip(renderItem, fallback, mouseX, mouseY, getWidth(), getHeight());
            return;
        }
        context.drawMinecraftTooltip(fallback, mouseX, mouseY, getWidth(), getHeight());
    }

    private MinecraftTooltip buildVisualFallbackTooltip(Visual visual) {
        List<MinecraftTooltipLine> lines = new ArrayList<>();
        String title = visual.getName() != null && !visual.getName().isBlank() ? visual.getName() : formatMaterialLabel(visual.getMaterial());
        if (title == null || title.isBlank()) {
            title = "Item";
        }
        lines.add(new MinecraftTooltipLine(MinecraftTextComponents.fromValue(title)));
        if (visual.getLore() != null) {
            lines.addAll(MinecraftTextComponents.fromPlainLore(visual.getLore()));
        }
        if (shouldRenderAsTexture(visual) && visual.getMaterial() != null && !visual.getMaterial().isBlank()) {
            lines.add(new MinecraftTooltipLine(MinecraftTextComponents.fromValue(visual.getMaterial())));
        }
        return new MinecraftTooltip(lines);
    }

    private void handleSlotClick(int slot, int button) {
        GuiElement element = slotElements.get(slot);
        if (element != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                removeElement(element);
                return;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (placeMode) {
                    selectElement(element);
                    startPlacementDrag(slot, element);
                    return;
                }
                selectElement(element);
            }
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (placeMode) {
            startPlacementDrag(slot, null);
            return;
        }
        selectElement(null);
    }

    private void selectElement(GuiElement element) {
        selectedElement = element;
        if (selectedElement != null && selectedElement.getVisual() != null) {
            placementTemplate = selectedElement.getVisual().copy();
        }
        dragPreviewSlots.clear();
        applySlotState();
        buildInspectorPanel();
    }

    private void removeElement(GuiElement element) {
        if (element == null || gui.getElements() == null) {
            return;
        }
        captureSnapshot();
        gui.getElements().remove(element);
        if (selectedElement == element) {
            selectedElement = null;
        }
        rebuildGrid();
        buildInspectorPanel();
    }

    private void updateRows(int rows) {
        int clamped = Math.clamp(rows, 1, 6);
        if (clamped == gui.getRows()) {
            return;
        }
        captureSnapshot();
        gui.setRows(clamped);
        if (selectedElement != null && (gui.getElements() == null || !gui.getElements().contains(selectedElement))) {
            selectedElement = null;
        }
        rebuildGrid();
        buildInspectorPanel();
        updateLayout(true);
    }

    private void setExtendInventoryMode(boolean enabled) {
        if (gui.isExtendToPlayerInventory() == enabled) {
            if (extendInventoryToggle != null && extendInventoryToggle.getValue() != enabled) {
                extendInventoryToggle.setValue(enabled);
            }
            return;
        }
        captureSnapshot();
        gui.setExtendToPlayerInventory(enabled);
        if (extendInventoryToggle != null && extendInventoryToggle.getValue() != enabled) {
            extendInventoryToggle.setValue(enabled);
        }
        if (selectedElement != null && (gui.getElements() == null || !gui.getElements().contains(selectedElement))) {
            selectedElement = null;
        }
        rebuildGrid();
        buildInspectorPanel();
        updateLayout(true);
    }

    private void pruneElementsForSlots(int maxSlot) {
        if (gui.getElements() == null) {
            return;
        }
        gui.getElements().removeIf(element -> {
            if (element == null || element.getSlots() == null) {
                return true;
            }
            element.getSlots().removeIf(slot -> slot == null || slot < 0 || slot >= maxSlot);
            return element.getSlots().isEmpty();
        });
    }

    private void remapSlotsForRowChange(int oldRows, int newRows) {
        if (gui.getElements() == null) {
            return;
        }
        int oldInventoryStart = oldRows * GRID_COLUMNS;
        int newTopSlots = newRows * GRID_COLUMNS;
        int oldInventoryEnd = oldInventoryStart + PLAYER_INVENTORY_SLOTS;
        gui.getElements().removeIf(element -> {
            if (element == null || element.getSlots() == null) {
                return true;
            }
            List<Integer> remapped = new ArrayList<>();
            for (Integer slot : element.getSlots()) {
                if (slot == null || slot < 0) {
                    continue;
                }
                if (slot < oldInventoryStart) {
                    if (slot < newTopSlots) {
                        remapped.add(slot);
                    }
                    continue;
                }
                if (slot < oldInventoryEnd) {
                    int offset = slot - oldInventoryStart;
                    remapped.add(newTopSlots + offset);
                }
            }
            element.setSlots(remapped);
            return remapped.isEmpty();
        });
    }

    private int getTotalSlots() {
        return gui.getRows() * GRID_COLUMNS + (gui.isExtendToPlayerInventory() ? PLAYER_INVENTORY_SLOTS : 0);
    }

    private int getTotalRows() {
        return gui.getRows() + (gui.isExtendToPlayerInventory() ? PLAYER_INVENTORY_ROWS : 0);
    }

    private void setPlaceMode(boolean enabled) {
        placeMode = enabled;
        if (placeToggle != null && placeToggle.getValue() != enabled) {
            placeToggle.setValue(enabled);
        }
        draggingPlacement = false;
        dragStartSlot = -1;
        dragEndSlot = -1;
        dragResizeElement = null;
        dragPreviewSlots.clear();
        applySlotState();
    }

    private void startPlacementDrag(int slot, GuiElement resizeElement) {
        draggingPlacement = true;
        dragStartSlot = slot;
        dragEndSlot = slot;
        dragResizeElement = resizeElement;
        updateDragPreview();
    }

    private void updateDragPreview() {
        dragPreviewSlots.clear();
        for (int slot : computeSlotRange(dragStartSlot, dragEndSlot)) {
            GuiElement occupied = slotElements.get(slot);
            if (occupied == null || occupied == dragResizeElement) {
                dragPreviewSlots.add(slot);
            }
        }
        applySlotState();
    }

    private void finishPlacementDrag() {
        draggingPlacement = false;
        List<Integer> slots = computeSlotRange(dragStartSlot, dragEndSlot);
        GuiElement resizingElement = dragResizeElement;
        dragStartSlot = -1;
        dragEndSlot = -1;
        dragResizeElement = null;
        dragPreviewSlots.clear();

        if (slots.isEmpty()) {
            applySlotState();
            return;
        }
        for (int slot : slots) {
            GuiElement occupied = slotElements.get(slot);
            if (occupied != null && occupied != resizingElement) {
                selectElement(occupied);
                return;
            }
        }
        if (resizingElement != null) {
            captureSnapshot();
            resizingElement.setSlots(new ArrayList<>(slots));
            rebuildGrid();
            selectElement(resizingElement);
            return;
        }

        captureSnapshot();
        GuiElement element = new GuiElement();
        element.getSlots().addAll(slots);
        Visual template = placementTemplate != null ? placementTemplate.copy() : new Visual("STONE", "Item");
        element.setVisual(template);
        gui.getElements().add(element);
        rebuildGrid();
        selectElement(element);
    }

    private List<Integer> computeSlotRange(int startSlot, int endSlot) {
        if (startSlot < 0 || endSlot < 0) {
            return List.of();
        }
        int startRow = startSlot / GRID_COLUMNS;
        int startCol = startSlot % GRID_COLUMNS;
        int endRow = endSlot / GRID_COLUMNS;
        int endCol = endSlot % GRID_COLUMNS;
        int minRow = Math.min(startRow, endRow);
        int maxRow = Math.max(startRow, endRow);
        int minCol = Math.min(startCol, endCol);
        int maxCol = Math.max(startCol, endCol);

        int rows = getTotalRows();
        if (maxRow >= rows) {
            return List.of();
        }

        List<Integer> slots = new ArrayList<>();
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                slots.add(row * GRID_COLUMNS + col);
            }
        }
        return slots;
    }

    private int getSlotAt(int mouseX, int mouseY) {
        if (slotSize <= 0) {
            return -1;
        }
        for (SlotButton button : slotButtons.values()) {
            if (button.isMouseOver(mouseX, mouseY)) {
                return button.slot;
            }
        }
        return -1;
    }

    private void positionGridWidgets() {
        int topSlots = gui.getRows() * GRID_COLUMNS;
        boolean extendInventory = gui.isExtendToPlayerInventory();
        for (SlotButton button : slotButtons.values()) {
            int col = button.slot % GRID_COLUMNS;
            int x = gridOriginX + col * slotSize;
            int y;
            if (!extendInventory || button.slot < topSlots) {
                int row = button.slot / GRID_COLUMNS;
                y = gridOriginY + row * slotSize;
            } else {
                int index = button.slot - topSlots;
                if (index < 27) {
                    int row = index / GRID_COLUMNS;
                    y = playerInventoryOriginY + row * slotSize;
                } else {
                    y = hotbarOriginY;
                }
            }
            button.setPosition(x, y);
            button.setWidth(slotSize);
            button.setHeight(slotSize);
        }

        if (gridContainer != null) {
            gridContainer.updateWidgetPositions();
        }
    }

    private void updateLayout(boolean force) {
        if (!force && width == lastWidth && height == lastHeight) {
            return;
        }
        lastWidth = width;
        lastHeight = height;

        int contentTop = header().headerSize + 5;
        int contentHeight = Math.max(120, height - contentTop - PANEL_PADDING);

        if (inspectorPanel != null) {
            inspectorPanel.y(contentTop).height(contentHeight);
        }

        int rightWidth = inspectorPanel != null ? (int) inspectorPanel.getAnimatedWidth() : 0;
        int availableWidth = width - rightWidth - PANEL_PADDING * 2;
        int centerWidth = Math.max(120, availableWidth);
        slotSize = SLOT_BASE_SIZE;
        guiScale = 1f;

        guiBackgroundWidth = GUI_TEXTURE_WIDTH;
        int rows = Math.max(1, gui.getRows());
        int topHeight = GUI_TOP_MARGIN + rows * SLOT_BASE_SIZE;
        guiBackgroundHeight = topHeight + GUI_PLAYER_INV_HEIGHT;

        int gridX = PANEL_PADDING + Math.max(0, (centerWidth - guiBackgroundWidth) / 2);
        int gridY = contentTop + Math.max(0, (contentHeight - guiBackgroundHeight) / 2) - 14;

        guiBackgroundX = gridX;
        guiBackgroundY = gridY;
        gridOriginX = guiBackgroundX + GUI_SIDE_MARGIN;
        gridOriginY = guiBackgroundY + GUI_TOP_MARGIN;
        playerInventoryOriginY = guiBackgroundY + GUI_TOP_MARGIN + rows * SLOT_BASE_SIZE + GUI_PLAYER_INV_OFFSET;
        hotbarOriginY = guiBackgroundY + GUI_TOP_MARGIN + rows * SLOT_BASE_SIZE + GUI_HOTBAR_OFFSET;

        if (gridContainer != null) {
            gridContainer.setPosition(guiBackgroundX, guiBackgroundY);
            gridContainer.padding(0);
            gridContainer.size(guiBackgroundWidth, guiBackgroundHeight);
            positionGridWidgets();
        }
    }

    private void saveGui() {
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null && serverId != null) {
            flowManager.saveGui(serverId, gui);
        } else {
            String title = gui.getTitle() != null ? gui.getTitle() : gui.getId();
            new Notification("GUI Saved", title != null ? title : "GUI", Notification.Type.SUCCESS);
        }
    }

    private void ensureGuiDefaults() {
        if (gui.getRows() <= 0) {
            gui.setRows(3);
        }
        if (gui.getElements() == null) {
            gui.setElements(new ArrayList<>());
        }
    }

    private Visual ensureVisual(GuiElement element) {
        Visual visual = element.getVisual();
        if (visual == null) {
            visual = new Visual();
            element.setVisual(visual);
        }
        return visual;
    }

    private void updateGuiTitle(String title) {
        String current = gui.getTitle() != null ? gui.getTitle() : "";
        String next = title != null ? title : "";
        if (current.equals(next)) {
            return;
        }
        captureSnapshot();
        gui.setTitle(title);
    }

    private void updateVisualName(Visual visual, String name) {
        String current = visual.getName() != null ? visual.getName() : "";
        String next = name != null ? name : "";
        if (current.equals(next)) {
            return;
        }
        captureSnapshot();
        visual.setName(name);
    }

    private void updateModelData(Visual visual, String text) {
        Integer current = visual.getModelData();
        Integer next = current;
        String trimmed = text != null ? text.trim() : "";
        if (trimmed.isEmpty()) {
            next = null;
        } else {
            try {
                next = Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                applySlotState();
                return;
            }
        }
        if ((current == null && next == null) || (current != null && current.equals(next))) {
            applySlotState();
            return;
        }
        captureSnapshot();
        visual.setModelData(next);
        applySlotState();
    }

    private void updateLore(Visual visual, String text) {
        List<String> loreLines = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    loreLines.add(trimmed);
                }
            }
        }
        if (visual.getLore() != null && visual.getLore().equals(loreLines)) {
            return;
        }
        captureSnapshot();
        visual.setLore(loreLines);
    }

    private GuiSnapshot createSnapshot() {
        List<GuiElement> copiedElements = new ArrayList<>();
        if (gui.getElements() != null) {
            for (GuiElement element : gui.getElements()) {
                if (element != null) {
                    copiedElements.add(element.copy());
                }
            }
        }
        int selectedIndex = -1;
        if (selectedElement != null && gui.getElements() != null) {
            selectedIndex = gui.getElements().indexOf(selectedElement);
        }
        return new GuiSnapshot(
            gui.getTitle(),
            gui.getRows(),
            gui.isExtendToPlayerInventory(),
            copiedElements,
            selectedIndex,
            placementTemplate != null ? placementTemplate.copy() : null
        );
    }

    private void captureSnapshot() {
        if (applyingHistory) {
            return;
        }
        undoStack.add(createSnapshot());
        if (undoStack.size() > MAX_UNDO_SIZE) {
            undoStack.removeFirst();
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.add(createSnapshot());
        if (redoStack.size() > MAX_UNDO_SIZE) {
            redoStack.removeFirst();
        }
        GuiSnapshot snapshot = undoStack.removeLast();
        restoreSnapshot(snapshot);
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.add(createSnapshot());
        if (undoStack.size() > MAX_UNDO_SIZE) {
            undoStack.removeFirst();
        }
        GuiSnapshot snapshot = redoStack.removeLast();
        restoreSnapshot(snapshot);
    }

    private void restoreSnapshot(GuiSnapshot snapshot) {
        applyingHistory = true;
        gui.setTitle(snapshot.title);
        gui.setRows(snapshot.rows);
        gui.setExtendToPlayerInventory(snapshot.extendToPlayerInventory);
        List<GuiElement> restoredElements = new ArrayList<>();
        for (GuiElement element : snapshot.elements) {
            restoredElements.add(element.copy());
        }
        gui.setElements(restoredElements);
        if (snapshot.selectedIndex >= 0 && snapshot.selectedIndex < restoredElements.size()) {
            selectedElement = restoredElements.get(snapshot.selectedIndex);
        } else {
            selectedElement = null;
        }
        placementTemplate = snapshot.placementTemplate != null ? snapshot.placementTemplate.copy() : new Visual("PAPER", "Item");
        if (guiTitleInput != null) {
            guiTitleInput.setText(gui.getTitle() != null ? gui.getTitle() : "");
        }
        if (guiRowsSelect != null) {
            guiRowsSelect.setSelectedItem(Math.clamp(gui.getRows(), 1, 6));
        }
        if (extendInventoryToggle != null) {
            extendInventoryToggle.setValue(gui.isExtendToPlayerInventory());
        }
        applyingHistory = false;
        rebuildGrid();
        buildInspectorPanel();
        updateLayout(true);
    }

    private String formatSlots(GuiElement element) {
        if (element == null || element.getSlots() == null || element.getSlots().isEmpty()) {
            return "None";
        }
        List<Integer> slots = new ArrayList<>(element.getSlots());
        slots.sort(Integer::compareTo);
        StringBuilder builder = new StringBuilder();
        int max = Math.min(slots.size(), 6);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(slots.get(i) + 1);
        }
        if (slots.size() > max) {
            builder.append(" ... (").append(slots.size()).append(" slots)");
        }
        return builder.toString();
    }

    private String formatFlowLabel(String flowId) {
        if (flowId == null || flowId.isBlank() || serverId == null) {
            return "None";
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null) {
            return flowId;
        }
        String name = flowManager.getFlowName(serverId, flowId);
        if (name == null || name.isBlank() || name.equals(flowId)) {
            return flowId;
        }
        return name;
    }

    private String formatGuiLabel(String guiId) {
        if (guiId == null || guiId.isBlank() || serverId == null) {
            return "None";
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null) {
            return guiId;
        }
        String name = flowManager.getGuiName(serverId, guiId);
        if (name == null || name.isBlank() || name.equals(guiId)) {
            return guiId;
        }
        return name;
    }

    private void disableEntrance(AnimatedWidget widget) {
        if (widget != null) {
            widget.entranceAnimationEnabled = false;
        }
    }

    private void setSelectorSelection(ItemSelectorWidget selector, String label) {
        if (selector == null) {
            return;
        }
        try {
            selector.getClass().getMethod("setSelectedItem", String.class).invoke(selector, label);
        } catch (Exception ignored) {
        }
    }

    private String formatMaterialLabel(String material) {
        if (material == null || material.isBlank()) {
            return "unknown";
        }
        String cleaned = material.trim().toLowerCase(Locale.ROOT).replace("_", " ");
        String[] parts = cleaned.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private MinecraftGameAssets getGameAssets() {
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            MinecraftGameAssets gameAssets = RemotelyClient.INSTANCE.getHost().getGameAssets();
            if (gameAssets != null) {
                return gameAssets;
            }
        }
        return MinecraftGameAssets.EMPTY;
    }

    private void drawGuiTexture(IDrawContext context, MinecraftGameAssets gameAssets, MinecraftAssetReference reference, BufferedImage fallback, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        Object nativeIdentifier = gameAssets.getNativeIdentifier(reference);
        if (nativeIdentifier != null && context.drawNativeTexture(nativeIdentifier, x, y, width, height, u, v, regionWidth, regionHeight, 256, 256)) {
            return;
        }
        if (fallback != null && fallback != ResourceManager.getInstance().getMissingTexture()) {
            context.drawPixelArt(fallback, x, y, width, height);
        }
    }

    private MinecraftAssetReference resolveMaterialTexture(Visual visual) {
        if (visual == null) {
            return getGameAssets().resolveMaterialTexture("stone", null);
        }
        return getGameAssets().resolveMaterialTexture(visual.getMaterial(), visual.getModelData());
    }

    private MinecraftRenderItem toRenderItem(Visual visual) {
        if (visual == null || shouldRenderAsTexture(visual)) {
            return null;
        }
        return MinecraftGameItems.fromVisual(visual.getMaterial(), 1, visual.getName(), visual.getLore(), visual.getModelData());
    }

    private boolean shouldRenderAsTexture(Visual visual) {
        if (visual == null) {
            return true;
        }
        String material = visual.getMaterial();
        if (material == null || material.isBlank()) {
            return true;
        }
        String path = material.trim().replace('\\', '/');
        if (path.contains(":")) {
            String[] parts = path.split(":", 2);
            path = parts.length > 1 ? parts[1] : "";
        }
        return path.contains("/") || path.endsWith(".png");
    }

    private class SlotButton extends AnimatedButton {
        private final int slot;
        private final SlotClickHandler handler;
        private GuiElement element;
        private boolean preview;
        private int highlightColor;
        private boolean highlightOutline;

        private SlotButton(int slot, int size, SlotClickHandler handler) {
            super(0, 0, size, size, "");
            this.slot = slot;
            this.handler = handler;
            this.centered = true;
            this.selectable = false;
            this.flat = true;
            this.animateElevation = false;
            this.enableHoverColors = false;
            this.entranceAnimationEnabled = false;
            this.transparent = true;
        }

        private void setElement(GuiElement element) {
            this.element = element;
        }

        private void setPreview(boolean preview) {
            this.preview = preview;
        }

        private void setHighlight(int highlightColor, boolean outline) {
            this.highlightColor = highlightColor;
            this.highlightOutline = outline;
        }

        private GuiElement getElement() {
            return element;
        }

        private boolean hasElement() {
            return element != null;
        }

        private MinecraftRenderItem getRenderItem() {
            return element != null ? toRenderItem(element.getVisual()) : null;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            if (element != null) {
                MinecraftRenderItem renderItem = toRenderItem(element.getVisual());
                int padding = Math.max(1, Math.round(guiScale));
                int iconSize = Math.max(10, Math.min(getWidth(), getHeight()) - padding * 2);
                int iconX = getX() + (getWidth() - iconSize) / 2;
                int iconY = getY() + (getHeight() - iconSize) / 2;
                if (renderItem != null) {
                    ctx.drawItem(renderItem, iconX, iconY, 0);
                } else {
                    BufferedImage texture = getGameAssets().getImage(resolveMaterialTexture(element.getVisual()));
                    ctx.drawPixelArt(texture, iconX, iconY, iconSize, iconSize);
                }
            }
            if (highlightColor != 0) {
                int fill = (highlightColor & 0x00FFFFFF) | 0x55000000;
                ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fill);
                if (highlightOutline) {
                    int border = (highlightColor & 0x00FFFFFF) | 0xCC000000;
                    ctx.fillBorder(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 1, border);
                }
            }
            if (preview && highlightColor == 0) {
                int fill = 0x33000000;
                ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fill);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (handler != null) {
                handler.onClick(slot, button);
            }
        }
    }

    private class TooltipOverlayWidget extends AnimatedWidget {
        private TooltipOverlayWidget() {
            super(0, 0, 0, 0, "");
            this.active = false;
            this.transparent = true;
            this.enableHoverColors = false;
            this.animateElevation = false;
            this.entranceAnimationEnabled = false;
        }

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        }

        @Override
        public void renderHintOverlay(IDrawContext context) {
            drawGuiSlotTooltip(context, GuiDesignerScreen.this.mouseX, GuiDesignerScreen.this.mouseY);
        }
    }

    private interface SlotClickHandler {
        void onClick(int slot, int button);
    }

    private boolean isAnyPopupOpen() {
        if (widgets == null) {
            return false;
        }
        for (Object child : widgets) {
            if (child instanceof PopupWidget popup && popup.isVisible()) {
                return true;
            }
        }
        return false;
    }
}
