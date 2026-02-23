package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.item.UiItem;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.Visual;
import restudio.rescreen.platform.IDrawContext;
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
import restudio.rescreen.ui.widgets.TextAreaWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Identifier;
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

    private static final List<String> MATERIAL_OPTIONS = List.of(
        "STONE", "COBBLESTONE", "OAK_PLANKS", "OAK_LOG", "GLASS", "GLASS_PANE",
        "GRAY_STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE",
        "RED_STAINED_GLASS_PANE", "GREEN_STAINED_GLASS_PANE", "BLUE_STAINED_GLASS_PANE",
        "BARRIER", "CHEST", "ENDER_CHEST", "ANVIL", "BOOK", "PAPER", "MAP",
        "COMPASS", "CLOCK", "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
        "NETHERITE_INGOT", "REDSTONE", "AMETHYST_SHARD", "ENDER_PEARL",
        "TOTEM_OF_UNDYING", "PLAYER_HEAD", "NAME_TAG"
    );

    private final GuiDefinition gui;
    private final String serverId;
    private final Object parent;

    private Container gridContainer;
    private SidePanel inspectorPanel;
    private ItemSelectorWidget materialSelector;
    private ItemSelectorWidget flowSelector;
    private ItemSelectorWidget guiSelector;

    private final Map<Integer, SlotButton> slotButtons = new HashMap<>();
    private final Map<Integer, GuiElement> slotElements = new HashMap<>();
    private final Set<Integer> dragPreviewSlots = new HashSet<>();

    private ToggleWidget placeToggle;
    private ToggleWidget extendInventoryToggle;
    private boolean placeMode;
    private boolean draggingPlacement;
    private int dragStartSlot = -1;
    private int dragEndSlot = -1;
    private GuiElement selectedElement;
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

    public GuiDesignerScreen(GuiDefinition gui) {
        this(gui, null, null);
    }

    public GuiDesignerScreen(GuiDefinition gui, String serverId) {
        this(gui, serverId, ScreenManager.getInstance().getCurrentScreen());
    }

    public GuiDesignerScreen(GuiDefinition gui, String serverId, Object parent) {
        super();
        this.gui = gui;
        this.serverId = serverId;
        this.parent = parent;
        this.autoResizeContainers = false;
        ensureGuiDefaults();
    }

    public String getDesktopAppId() {
        return "gui-designer";
    }

    public String getDesktopAppTitle() {
        return "GUI Designer";
    }

    public String getDesktopAppIconPath() {
        return "change.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return DesktopWindowBehavior.SINGLETON;
    }

    @Override
    public boolean shouldForceSuperScreen() {
        return desktopMode;
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
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, OVERLAY_COLOR);
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
        Identifier textureId = buildTextureIdentifier("minecraft", "textures/gui/container/generic_54.png");
        BufferedImage texture = ResourceManager.getInstance().getImage(textureId);
        if (texture == null || texture == ResourceManager.getInstance().getMissingTexture()) {
            return;
        }
        int rows = Math.max(1, gui.getRows());
        int topHeight = GUI_TOP_MARGIN + rows * SLOT_BASE_SIZE;
        int topHeightScaled = Math.round(topHeight * guiScale);
        BufferedImage top = texture.getSubimage(0, 0, GUI_TEXTURE_WIDTH, Math.min(topHeight, texture.getHeight()));
        context.drawPixelArt(top, guiBackgroundX, guiBackgroundY, guiBackgroundWidth, topHeightScaled);

        if (texture.getHeight() >= GUI_BOTTOM_TEXTURE_Y + GUI_PLAYER_INV_HEIGHT) {
            BufferedImage bottom = texture.getSubimage(0, GUI_BOTTOM_TEXTURE_Y, GUI_TEXTURE_WIDTH, GUI_PLAYER_INV_HEIGHT);
            int bottomY = guiBackgroundY + topHeightScaled;
            int bottomHeightScaled = Math.round(GUI_PLAYER_INV_HEIGHT * guiScale);
            context.drawPixelArt(bottom, guiBackgroundX, bottomY, guiBackgroundWidth, bottomHeightScaled);
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
                startPlacementDrag(slot);
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
        if (selectedElement != null && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
            if (!isAnyPopupOpen()) {
                if (!(getFocusedWidget() instanceof TextInputWidget) && !(getFocusedWidget() instanceof TextAreaWidget) && !(getFocusedWidget() instanceof ItemSelectorWidget)) {
                    removeElement(selectedElement);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        inspectorPanel = createSidePanel("gui_inspector").width(280).y(0).height(height).show();
        inspectorPanel.container().layout(new ManagedLayout()).columns(1).padding(6).scrolling(true).enableSelecting(false);
    }

    private void buildInspectorPanel() {
        if (inspectorPanel == null) {
            return;
        }
        Container container = inspectorPanel.container();
        container.clearWidgets();

        container.addWidget(buildSectionLabel("GUI"));
        guiTitleInput = new TextInputWidget.Builder()
            .text(gui.getTitle() != null ? gui.getTitle() : "")
            .placeholder("Title")
            .size(180, 22)
            .onChange(gui::setTitle)
            .build();
        disableEntrance(guiTitleInput);
        container.addWidget(guiTitleInput);

        container.addWidget(buildLabel("rows"));
        List<Integer> rowOptions = List.of(1, 2, 3, 4, 5, 6);
        guiRowsSelect = new DropDownWidget.Builder<>(rowOptions)
            .size(180, 22)
            .selectedItem(Math.max(1, Math.min(6, gui.getRows())))
            .displayFunction(rows -> rows + (rows == 1 ? " row" : " rows"))
            .onSelectionChanged(this::updateRows)
            .build();
        disableEntrance(guiRowsSelect);
        container.addWidget(guiRowsSelect);

        extendInventoryToggle = new ToggleWidget.Builder()
            .label("Extend to player inventory")
            .size(180, 18)
            .toggled(gui.isExtendToPlayerInventory())
            .onChange(this::setExtendInventoryMode)
            .build();
        disableEntrance(extendInventoryToggle);
        container.addWidget(extendInventoryToggle);

        container.addWidget(buildSectionLabel("Item"));
        if (selectedElement == null) {
            container.addWidget(buildHintLabel("Select an item to edit"));
            return;
        }

        Visual visual = ensureVisual(selectedElement);

        container.addWidget(buildLabel("slots"));
        TextInputWidget slotsInput = new TextInputWidget.Builder()
            .text(formatSlots(selectedElement))
            .placeholder("Slots")
            .size(180, 22)
            .active(false)
            .build();
        disableEntrance(slotsInput);
        container.addWidget(slotsInput);

        container.addWidget(buildLabel("name"));
        TextInputWidget nameInput = new TextInputWidget.Builder()
            .text(visual.getName() != null ? visual.getName() : "")
            .placeholder("Name")
            .size(180, 22)
            .onChange(text -> {
                visual.setName(text);
                applySlotState();
            })
            .build();
        disableEntrance(nameInput);
        container.addWidget(nameInput);

        container.addWidget(buildLabel("material"));
        materialSelector = new ItemSelectorWidget.Builder(this)
            .size(180, 140)
            .embedded(true)
            .dismissOnSelect(false)
            .emptyMessage("No items")
            .build();
        disableEntrance(materialSelector);
        container.addWidget(materialSelector);

        container.addWidget(buildLabel("model data"));
        TextInputWidget modelInput = new TextInputWidget.Builder()
            .text(visual.getModelData() != null ? String.valueOf(visual.getModelData()) : "")
            .placeholder("Custom model data")
            .size(180, 22)
            .onChange(text -> {
                String trimmed = text.trim();
                if (trimmed.isEmpty()) {
                    visual.setModelData(null);
                } else {
                    try {
                        visual.setModelData(Integer.parseInt(trimmed));
                    } catch (NumberFormatException ignored) {
                    }
                }
                applySlotState();
            })
            .build();
        disableEntrance(modelInput);
        container.addWidget(modelInput);

        container.addWidget(buildLabel("lore"));
        String loreText = visual.getLore() != null ? String.join("\n", visual.getLore()) : "";
        TextAreaWidget loreInput = new TextAreaWidget.Builder()
            .text(loreText)
            .placeholder("Lore lines")
            .size(180, 70)
            .onChange(text -> updateLore(visual, text))
            .build();
        disableEntrance(loreInput);
        container.addWidget(loreInput);

        container.addWidget(buildSectionLabel("Flow"));
        flowSelector = new ItemSelectorWidget.Builder(this)
            .size(180, 140)
            .embedded(true)
            .dismissOnSelect(false)
            .emptyMessage("No flows")
            .build();
        disableEntrance(flowSelector);
        container.addWidget(flowSelector);

        RowWidget flowRow = new RowWidget.Builder()
            .size(180, 22)
            .addWidget(new AnimatedButton.Builder()
                .label("Open flow")
                .size(86, 22)
                .onClick(this::openSelectedFlow)
                .build())
            .addWidget(new AnimatedButton.Builder()
                .label("New flow")
                .size(86, 22)
                .accentType(ThemeManager.getAccent("nice"))
                .onClick(this::showCreateFlowPopup)
                .build())
            .build();
        disableEntrance(flowRow);
        container.addWidget(flowRow);

        container.addWidget(buildSectionLabel("Menu"));
        guiSelector = new ItemSelectorWidget.Builder(this)
            .size(180, 120)
            .embedded(true)
            .dismissOnSelect(false)
            .emptyMessage("No menus")
            .build();
        disableEntrance(guiSelector);
        container.addWidget(guiSelector);

        AnimatedButton removeButton = new AnimatedButton.Builder()
            .label("Remove item")
            .size(180, 22)
            .accentType(ThemeManager.getAccent("danger"))
            .onClick(() -> removeElement(selectedElement))
            .build();
        disableEntrance(removeButton);
        container.addWidget(removeButton);
        container.updateWidgetPositions();
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

    private void refreshMaterialSelector() {
        if (materialSelector == null) {
            return;
        }
        materialSelector.clearItems();
        Set<String> options = new HashSet<>(MATERIAL_OPTIONS);
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

    private void applyMaterial(String material) {
        if (selectedElement == null) {
            placementTemplate.setMaterial(material);
            return;
        }
        Visual visual = ensureVisual(selectedElement);
        visual.setMaterial(material);
        placementTemplate = visual.copy();
        setSelectorSelection(materialSelector, formatMaterialLabel(material));
        applySlotState();
    }

    private void applyFlow(String flowId) {
        if (selectedElement == null) {
            return;
        }
        selectedElement.setFlowId(flowId);
        if (flowId != null && !flowId.isBlank()) {
            selectedElement.setOpenGuiId(null);
            setSelectorSelection(guiSelector, "none");
        }
        String label = flowId == null || flowId.isBlank() ? "none" : formatFlowLabel(flowId);
        setSelectorSelection(flowSelector, label);
    }

    private void applyOpenGui(String guiId) {
        if (selectedElement == null) {
            return;
        }
        selectedElement.setOpenGuiId(guiId);
        if (guiId != null && !guiId.isBlank()) {
            selectedElement.setFlowId(null);
            setSelectorSelection(flowSelector, "none");
        }
        String label = guiId == null || guiId.isBlank() ? "none" : formatGuiLabel(guiId);
        setSelectorSelection(guiSelector, label);
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
                    FlowGraph graph = flowManager.createFlow(serverId, id);
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
            button.hintDelay = 0.2f;
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
            button.hint = element != null ? buildElementHint(element) : "Slot " + (button.slot + 1) + " (empty)";
        }
    }

    private String buildElementHint(GuiElement element) {
        if (element == null) {
            return "";
        }
        Visual visual = element.getVisual();
        String name = visual != null && visual.getName() != null && !visual.getName().isBlank() ? visual.getName() : "Item";
        String material = visual != null && visual.getMaterial() != null && !visual.getMaterial().isBlank() ? visual.getMaterial() : "Material";
        return name + " [" + material + "]";
    }

    private void handleSlotClick(int slot, int button) {
        GuiElement element = slotElements.get(slot);
        if (element != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                removeElement(element);
                return;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selectElement(element);
            }
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (placeMode) {
            startPlacementDrag(slot);
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
        gui.getElements().remove(element);
        if (selectedElement == element) {
            selectedElement = null;
        }
        rebuildGrid();
        buildInspectorPanel();
    }

    private void updateRows(int rows) {
        int clamped = Math.max(1, Math.min(6, rows));
        int previousRows = gui.getRows();
        if (clamped == previousRows) {
            return;
        }
        gui.setRows(clamped);
        if (gui.isExtendToPlayerInventory()) {
            remapSlotsForRowChange(previousRows, clamped);
        } else {
            pruneElementsForSlots(clamped * GRID_COLUMNS);
        }
        if (selectedElement != null && (gui.getElements() == null || !gui.getElements().contains(selectedElement))) {
            selectedElement = null;
        }
        rebuildGrid();
        buildInspectorPanel();
        updateLayout(true);
    }

    private void setExtendInventoryMode(boolean enabled) {
        gui.setExtendToPlayerInventory(enabled);
        if (extendInventoryToggle != null && extendInventoryToggle.getValue() != enabled) {
            extendInventoryToggle.setValue(enabled);
        }
        if (!enabled) {
            pruneElementsForSlots(gui.getRows() * GRID_COLUMNS);
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
        dragPreviewSlots.clear();
        applySlotState();
    }

    private void startPlacementDrag(int slot) {
        draggingPlacement = true;
        dragStartSlot = slot;
        dragEndSlot = slot;
        updateDragPreview();
    }

    private void updateDragPreview() {
        dragPreviewSlots.clear();
        for (int slot : computeSlotRange(dragStartSlot, dragEndSlot)) {
            if (!slotElements.containsKey(slot)) {
                dragPreviewSlots.add(slot);
            }
        }
        applySlotState();
    }

    private void finishPlacementDrag() {
        draggingPlacement = false;
        List<Integer> slots = computeSlotRange(dragStartSlot, dragEndSlot);
        dragStartSlot = -1;
        dragEndSlot = -1;
        dragPreviewSlots.clear();

        if (slots.isEmpty()) {
            applySlotState();
            return;
        }
        for (int slot : slots) {
            GuiElement occupied = slotElements.get(slot);
            if (occupied != null) {
                selectElement(occupied);
                return;
            }
        }

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
        int desiredPanelWidth = Math.max(240, (int) (width * 0.28f));

        if (inspectorPanel != null) {
            inspectorPanel.y(contentTop).height(contentHeight).width(desiredPanelWidth);
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
        visual.setLore(loreLines);
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
            builder.append(" ... (" + slots.size() + " slots)");
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

    private AnimatedButton buildSectionLabel(String text) {
        AnimatedButton label = buildLabel(text);
        label.accentType = ThemeManager.getAccent("calm");
        return label;
    }

    private AnimatedButton buildLabel(String text) {
        AnimatedButton label = new AnimatedButton.Builder()
            .label(text)
            .size(180, 16)
            .centered(false)
            .active(false)
            .flat(true)
            .transparent(true)
            .animateElevation(false)
            .enableHoverColors(false)
            .build();
        disableEntrance(label);
        return label;
    }

    private AnimatedButton buildHintLabel(String text) {
        return buildLabel(text);
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

    private Identifier resolveMaterialIdentifier(Visual visual) {
        if (visual == null) {
            return buildTextureIdentifier("minecraft", "textures/item/stone.png");
        }
        String material = visual.getMaterial();
        String namespace = "minecraft";
        String path = material != null ? material.trim() : "";
        if (path.contains(":")) {
            String[] parts = path.split(":", 2);
            namespace = parts[0].isBlank() ? "minecraft" : parts[0];
            path = parts.length > 1 ? parts[1] : "";
        }
        path = path.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (path.isBlank()) {
            path = "stone";
        }

        Identifier resolved = null;
        if (!path.contains("/")) {
            Integer modelData = visual.getModelData();
            if (modelData != null) {
                Identifier modelId = buildTextureIdentifier(namespace, "textures/item/" + path + "_" + modelData + ".png");
                if (!isMissingTexture(modelId)) {
                    resolved = modelId;
                }
            }
            if (resolved == null) {
                Identifier itemId = buildTextureIdentifier(namespace, "textures/item/" + path + ".png");
                if (!isMissingTexture(itemId)) {
                    resolved = itemId;
                }
            }
            if (resolved == null) {
                resolved = buildTextureIdentifier(namespace, "textures/block/" + path + ".png");
            }
        } else {
            String texturePath = path.endsWith(".png") ? path : path + ".png";
            resolved = buildTextureIdentifier(namespace, texturePath.startsWith("textures/") ? texturePath : "textures/" + texturePath);
        }
        return resolved;
    }

    private Identifier buildTextureIdentifier(String namespace, String path) {
        String resolved = namespace + ":" + path;
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            Object hostId = RemotelyClient.INSTANCE.getHost().getFontIdentifier(namespace, path);
            if (hostId != null) {
                resolved = hostId.toString();
            }
        }
        return Identifier.of(resolved);
    }

    private boolean isMissingTexture(Identifier identifier) {
        return ResourceManager.getInstance().getImage(identifier) == ResourceManager.getInstance().getMissingTexture();
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

        @Override
        protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
            if (element != null) {
                UiItem uiItem = UiItem.fromVisual(element.getVisual());
                int padding = Math.max(1, Math.round(guiScale));
                int iconSize = Math.max(10, Math.min(getWidth(), getHeight()) - padding * 2);
                int iconX = getX() + (getWidth() - iconSize) / 2;
                int iconY = getY() + (getHeight() - iconSize) / 2;
                if (uiItem != null) {
                    ctx.drawItem(uiItem, iconX, iconY, 0);
                } else {
                    Identifier texture = resolveMaterialIdentifier(element.getVisual());
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
