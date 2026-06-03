package redxax.oxy.remotely.flow.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.ResourceManager;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AdvancementDesignerScreen extends ReScreen {
    private static final CopyOnWriteArraySet<AdvancementDesignerScreen> OPEN_SCREENS = new CopyOnWriteArraySet<>();
    private static final String BLOCK_CATALOG = "server:minecraft:block";
    private static final int WINDOW_WIDTH = 252;
    private static final int WINDOW_HEIGHT = 140;
    private static final int VIEWPORT_X = 9;
    private static final int VIEWPORT_Y = 18;
    private static final int VIEWPORT_WIDTH = 234;
    private static final int VIEWPORT_HEIGHT = 113;
    private static final int ADVANCEMENT_X_SCALE = 28;
    private static final int ADVANCEMENT_Y_SCALE = 27;
    private static final int NODE_WIDTH = 26;
    private static final int NODE_HEIGHT = 26;
    private static final int FRAME_X = 3;
    private static final int ICON_X = 8;
    private static final int ICON_Y = 5;
    private static final int TITLE_BOX_MIN_WIDTH = 109;
    private static final int TITLE_TEXT_MAX_WIDTH = 163;
    private static final int TITLE_TEXT_X = 32;
    private static final int DESCRIPTION_TEXT_X = 5;
    private static final int TEXT_LINE_HEIGHT = 9;
    private static final int TITLE_BOX_EXTRA_HEIGHT = 17;
    private static final int DESCRIPTION_BOX_EXTRA_HEIGHT = 6;
    private static final int DESCRIPTION_TEXT_Y_OFFSET = -1;
    private static final int TOOLTIP_SLICE_WIDTH = 200;
    private static final int TOOLTIP_SLICE_HEIGHT = 26;
    private static final int TOOLTIP_SLICE_BORDER = 10;
    private static final int BACKGROUND_TILE_SIZE = 16;
    private static final int DRAG_AUTO_PAN_EDGE = 18;
    private static final double DRAG_AUTO_PAN_SPEED = 4.0;
    private static final int[] DESCRIPTION_SPLIT_OFFSETS = {0, 10, -10, 25, -25};
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject tree;
    private final String serverId;
    private final Object parent;
    private final ReSyncStudioPanelState panelState = new ReSyncStudioPanelState();
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private final Map<String, BufferedImage> imageSlices = new HashMap<>();
    private final List<DropDownWidget<String>> panelDropdowns = new ArrayList<>();
    private final List<AnimatedWidget> logicPanelWidgets = new ArrayList<>();
    private final List<AnimatedWidget> rootMetaPanelWidgets = new ArrayList<>();
    private final List<AnimatedWidget> dynamicPanelWidgets = new ArrayList<>();
    private SidePanel inspector;
    private ItemSelectorWidget activeSearchSelector;
    private Supplier<List<String>> activeSelectorOptions;
    private Supplier<String> activeSelectorSelected;
    private Consumer<String> activeSelectorOnSelected;
    private boolean activeSelectorUsesRecipeCatalog;
    private String lastInspectorNode = "";
    private String lastDynamicStructure = "";
    private AnimatedButton dynamicCompletionFlowButton;
    private TextInputWidget dynamicCompletionCommandInput;
    private DropDownWidget<String> dynamicEventDropdown;
    private AnimatedButton dynamicPredicateFlowButton;
    private TextInputWidget dynamicXpInput;
    private TextInputWidget dynamicLootInput;
    private TextInputWidget dynamicRecipesInput;
    private AnimatedButton dynamicRunFlowButton;
    private TextInputWidget dynamicRunCommandInput;
    private AnimatedButton backgroundButton;
    private ToggleWidget enabledToggle;
    private TextInputWidget treeNameInput;
    private TextInputWidget titleInput;
    private TextInputWidget descriptionInput;
    private String inspectorEditNodeId = "root";
    private boolean syncingInspector;
    private AnimatedButton iconButton;
    private DropDownWidget<String> frameDropdown;
    private ToggleWidget showToastToggle;
    private ToggleWidget announceChatToggle;
    private ToggleWidget hiddenToggle;
    private DropDownWidget<String> parentDropdown;
    private DropDownWidget<String> completionSourceDropdown;
    private DropDownWidget<String> rewardTypeDropdown;
    private DropDownWidget<String> onCompleteTypeDropdown;
    private TitledRowWidget backgroundRow;
    private TitledRowWidget treeNameRow;
    private TitledRowWidget enabledRow;
    private TitledRowWidget titleRow;
    private TitledRowWidget descriptionRow;
    private TitledRowWidget iconRow;
    private TitledRowWidget frameRow;
    private TitledRowWidget showToastRow;
    private TitledRowWidget announceChatRow;
    private TitledRowWidget hiddenRow;
    private TitledRowWidget parentRow;
    private TitledRowWidget completionSourceRow;
    private TitledRowWidget rewardTypeRow;
    private TitledRowWidget onCompleteTypeRow;
    private String selectedNode = "root";
    private String draggedNode;
    private double panX;
    private double panY;
    private double dragOffsetX;
    private double dragOffsetY;
    private double dragViewPanX;
    private double dragViewPanY;
    private boolean customPan;

    private record TooltipLayout(String id, JsonObject node, int nodeX, int nodeY, int boxX, int titleY, int boxWidth, int titleHeight, int descriptionY, int descriptionTextY, int descriptionHeight, boolean flippedLeft, List<String> titleLines, List<String> descriptionLines) {
    }

    public AdvancementDesignerScreen(JsonObject tree, String serverId, Object parent) {
        this.tree = tree;
        this.serverId = serverId;
        this.parent = parent;
        autoResizeContainers = false;
        preserveStateOnDisplay = true;
        OPEN_SCREENS.add(this);
    }

    public static void refreshCatalogForServer(String serverId) {
        for (AdvancementDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.serverId)) {
                screen.onOptionCatalogRefreshed();
            }
        }
    }

    @Override
    public void init() {
        super.init();
        header().reset();
        header().addRight("save.png", this::save, "Save");
        header().addRight("goforward.png", this::redo, "Redo");
        header().addRight("goback.png", this::undo, "Undo");
        header().addRight("delete.png", this::deleteSelected, "Delete Node");
        header().addRight("add.png", this::addNode, "Add Node");
        header().build();
        preloadCatalogs();
        ensureInspectorPanel();
        applyInspectorSelection();
    }

    private void onOptionCatalogRefreshed() {
        if (inspector != null) {
            applyInspectorSelection();
        }
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSelectorSelected != null && activeSelectorOnSelected != null) {
            int selectorX = activeSearchSelector.getX();
            int selectorY = activeSearchSelector.getY();
            boolean recipeCatalog = activeSelectorUsesRecipeCatalog;
            Supplier<List<String>> options = activeSelectorOptions;
            Supplier<String> selected = activeSelectorSelected;
            Consumer<String> onSelected = activeSelectorOnSelected;
            closeActiveSearchSelector();
            if (recipeCatalog) {
                openRecipeItemSearchSelector(selected, onSelected, selectorX, selectorY);
            } else if (options != null) {
                openSearchSelector(options, selected, onSelected, selectorX, selectorY);
            }
        }
    }

    private void preloadCatalogs() {
        ItemOptionCatalog.ensureLoaded(serverId);
        requestCatalog(BLOCK_CATALOG);
    }

    private void requestCatalog(String source) {
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null && source != null && !OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(source);
        }
    }

    @Override
    public void renderHandler(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateInspectorLayout();
        if (inspector != null) {
            inspector.update();
        }
        super.renderHandler(context, mouseX, mouseY, delta);
        if (inspector != null) {
            inspector.container().render(context, mouseX, mouseY, delta);
            inspector.renderHeader(context, mouseX, mouseY);
        }
        renderPanelDropdownOverlays(context, mouseX, mouseY, delta);
        renderActiveSearchSelector(context, mouseX, mouseY, delta);
    }

    @Override
    public void updatePositions() {
        super.updatePositions();
        updateInspectorLayout();
    }

    @Override
    public void renderBackground(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        int windowX = advancementWindowX();
        int windowY = advancementWindowY();
        int contentX = windowX + VIEWPORT_X;
        int contentY = windowY + VIEWPORT_Y;
        MinecraftGameAssets gameAssets = getGameAssets();
        JsonObject nodes = nodes();
        double viewPanX = viewPanX(nodes);
        double viewPanY = viewPanY(nodes);
        drawAdvancementBackground(context, gameAssets, windowX, windowY);
        context.pushScissorState();
        context.enableScissor(contentX, contentY, contentX + VIEWPORT_WIDTH, contentY + VIEWPORT_HEIGHT);
        drawTiledBackground(context, gameAssets, contentX, contentY, viewPanX, viewPanY);
        drawViewportDepth(context, contentX, contentY);
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            drawConnection(context, nodes, node, contentX, contentY, viewPanX, viewPanY, true);
        }
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            drawConnection(context, nodes, node, contentX, contentY, viewPanX, viewPanY, false);
        }
        TooltipLayout tooltipLayout = null;
        boolean mouseInViewport = mouseX > contentX && mouseX < contentX + VIEWPORT_WIDTH && mouseY > contentY && mouseY < contentY + VIEWPORT_HEIGHT;
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            boolean hidden = hidden(node);
            int x = nodeX(node, contentX, viewPanX);
            int y = nodeY(node, contentY, viewPanY);
            if (!hidden && mouseInViewport && mouseX >= x && mouseX <= x + NODE_WIDTH && mouseY >= y && mouseY <= y + NODE_HEIGHT) {
                tooltipLayout = tooltipLayout(entry.getKey(), node, contentX, contentY, viewPanX, viewPanY);
            }
            drawSprite(context, gameAssets, frameSprite(node, entry.getKey().equals(selectedNode)), x + FRAME_X, y, NODE_WIDTH, NODE_HEIGHT);
            MinecraftRenderItem icon = icon(node);
            if (icon != null) {
                context.drawItem(icon, x + ICON_X, y + ICON_Y, 0);
            }
            if (hidden) {
                drawHiddenNodeMarker(context, x + FRAME_X, y);
            }
        }
        if (tooltipLayout != null) {
            context.pushScissorState();
            context.clearScissor();
            drawTooltip(context, gameAssets, tooltipLayout);
            context.popScissorState();
        }
        context.popScissorState();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (clickExpandedPanelDropdown(mouseX, mouseY, button)) {
            return true;
        }
        if (inspector != null && inspector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        int contentX = advancementWindowX() + VIEWPORT_X;
        int contentY = advancementWindowY() + VIEWPORT_Y;
        JsonObject nodes = nodes();
        double viewPanX = viewPanX(nodes);
        double viewPanY = viewPanY(nodes);
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            int x = nodeX(node, contentX, viewPanX);
            int y = nodeY(node, contentY, viewPanY);
            if (mouseX >= x && mouseX <= x + NODE_WIDTH && mouseY >= y && mouseY <= y + NODE_HEIGHT) {
                snapshot();
                selectNode(entry.getKey());
                draggedNode = entry.getKey();
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
                dragViewPanX = viewPanX;
                dragViewPanY = viewPanY;
                return true;
            }
        }
        if (mouseX >= contentX && mouseX <= contentX + VIEWPORT_WIDTH && mouseY >= contentY && mouseY <= contentY + VIEWPORT_HEIGHT) {
            draggedNode = "";
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        for (DropDownWidget<String> dropdown : panelDropdowns) {
            if (dropdown.isVisible() && dropdown.isExpanded() && dropdown.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        if (inspector != null && inspector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || draggedNode == null) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (draggedNode.isBlank()) {
            JsonObject nodes = nodes();
            if (!canPanViewport(nodes)) {
                return true;
            }
            beginCustomPan(nodes);
            panX += deltaX;
            panY += deltaY;
            clampPan(nodes);
            return true;
        }
        JsonObject nodes = nodes();
        JsonObject node = nodes.getAsJsonObject(draggedNode);
        moveNodeToMouse(node, mouseX, mouseY);
        autoPanDraggedNode(nodes, mouseX, mouseY);
        moveNodeToMouse(node, mouseX, mouseY);
        refreshJson();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        for (DropDownWidget<String> dropdown : panelDropdowns) {
            if (dropdown.isVisible() && dropdown.isExpanded() && dropdown.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (inspector != null && inspector.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        draggedNode = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        for (DropDownWidget<String> dropdown : panelDropdowns) {
            if (dropdown.isVisible() && dropdown.isExpanded() && dropdown.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount)) {
                return true;
            }
        }
        if (inspector != null && inspector.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        int contentX = advancementWindowX() + VIEWPORT_X;
        int contentY = advancementWindowY() + VIEWPORT_Y;
        if (mouseX < contentX || mouseX > contentX + VIEWPORT_WIDTH || mouseY < contentY || mouseY > contentY + VIEWPORT_HEIGHT) {
            return false;
        }
        JsonObject nodes = nodes();
        if (!canPanViewport(nodes)) {
            return false;
        }
        beginCustomPan(nodes);
        panX += horizontalAmount * 16;
        panY += verticalAmount * 16;
        clampPan(nodes);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (inspector != null && inspector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activeSearchSelector != null && activeSearchSelector.visible && activeSearchSelector.charTyped(chr, modifiers)) {
            return true;
        }
        if (inspector != null && inspector.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        commitInspectorEdits(inspectorEditNodeId);
        OPEN_SCREENS.remove(this);
        closeActiveSearchSelector();
        super.close();
    }

    private void updateInspectorLayout() {
        if (inspector == null) {
            return;
        }
        int top = header().headerSize + 5;
        panelState.width(inspector.getDesiredWidth());
        inspector.y(top).height(Math.max(120, height - top - 8)).width(panelState.width());
    }

    private void ensureInspectorPanel() {
        if (inspector == null) {
            inspector = new SidePanel(this, "advancement_inspector", this::updateInspectorLayout)
                .right()
                .minWidth(220)
                .width(250)
                .show();
            inspector.container()
                .layout(new ManagedLayout())
                .columns(1)
                .padding(panelState.padding())
                .scrolling(true)
                .enableSelecting(false);
        }
        if (titleInput == null) {
            buildInspectorWidgets(inspector.container(), panelState.rowWidth(inspector));
        }
    }

    private void selectNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || nodeId.equals(selectedNode)) {
            return;
        }
        selectedNode = nodeId;
        applyInspectorSelection();
    }

    private void applyInspectorSelection() {
        if (inspector == null) {
            return;
        }
        String previousNode = lastInspectorNode;
        if (previousNode != null && !previousNode.isBlank() && !previousNode.equals(selectedNode)) {
            commitInspectorEdits(previousNode);
        }
        inspectorEditNodeId = selectedNode;
        Container container = inspector.container();
        float scrollOffset = container.getScrollOffset();
        int rowWidth = panelState.rowWidth(inspector);
        boolean nodeChanged = !selectedNode.equals(previousNode);
        syncingInspector = true;
        try {
            syncInspectorValues(rowWidth, nodeChanged);
            String structure = dynamicStructureKey();
            if (!structure.equals(lastDynamicStructure)) {
                rebuildDynamicSection(container, rowWidth);
                lastDynamicStructure = structure;
            } else if (nodeChanged) {
                syncDynamicFieldValues();
            }
        } finally {
            syncingInspector = false;
        }
        lastInspectorNode = selectedNode;
        updateLogicSectionVisibility();
        updateRootMetaVisibility();
        container.updateWidgetPositions();
        container.setScrollOffset(scrollOffset);
    }

    private void commitInspectorEdits(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || !nodes().has(nodeId)) {
            return;
        }
        JsonObject node = nodes().getAsJsonObject(nodeId);
        JsonObject display = object(node, "display");
        if (titleInput != null) {
            update(display, "title", titleInput.getText());
        }
        if (descriptionInput != null) {
            update(display, "description", descriptionInput.getText());
        }
        if ("root".equals(nodeId) && treeNameInput != null) {
            tree.addProperty("displayName", treeNameInput.getText());
        }
    }

    private boolean clickExpandedPanelDropdown(double mouseX, double mouseY, int button) {
        for (int i = panelDropdowns.size() - 1; i >= 0; i--) {
            DropDownWidget<String> dropdown = panelDropdowns.get(i);
            if (dropdown.isVisible() && dropdown.isExpanded() && dropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    private void renderPanelDropdownOverlays(IDrawContext context, int mouseX, int mouseY, float delta) {
        for (DropDownWidget<String> dropdown : panelDropdowns) {
            if (dropdown.isVisible() && dropdown.isExpanded()) {
                dropdown.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void renderActiveSearchSelector(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (activeSearchSelector != null && activeSearchSelector.visible) {
            activeSearchSelector.render(context, mouseX, mouseY, delta);
            activeSearchSelector.renderHintOverlay(context);
        }
    }

    private void buildInspectorWidgets(Container container, int rowWidth) {
        JsonObject root = nodes().has("root") ? nodes().getAsJsonObject("root") : null;
        if (root != null) {
            JsonObject rootDisplay = object(root, "display");
            backgroundButton = searchableButton(this::backgroundOptions, () -> backgroundMaterial(object(root, "display")), BLOCK_CATALOG, rowWidth - 8, value -> {
                JsonObject rootNode = nodes().has("root") ? nodes().getAsJsonObject("root") : null;
                if (rootNode != null && isRealOption(value)) {
                    update(object(rootNode, "display"), "background", materialToBackgroundTexture(value));
                }
            });
            backgroundRow = panelState.row("Background", backgroundButton, rowWidth);
            addInspectorWidget(container, backgroundRow);
        }

        treeNameInput = panelState.input("Tree Name", text(tree, "displayName"), value -> {
            if (syncingInspector) {
                return;
            }
            snapshot();
            tree.addProperty("displayName", value != null ? value : "");
        });
        treeNameInput.setWidth(rowWidth - 8);
        treeNameRow = panelState.row("Tree Name", treeNameInput, rowWidth);
        addInspectorWidget(container, treeNameRow);
        rootMetaPanelWidgets.add(treeNameRow);

        enabledToggle = toggleWidget(true, value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject node = nodeById(inspectorEditNodeId);
            if (node != null) {
                snapshot();
                update(node, "enabled", value);
            }
        });
        enabledRow = panelState.row("Enabled", enabledToggle, rowWidth);
        addInspectorWidget(container, enabledRow);

        titleInput = panelState.input("Title", "", value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject display = displayForNode(inspectorEditNodeId);
            if (display != null) {
                snapshot();
                update(display, "title", value);
            }
        });
        titleInput.setWidth(rowWidth - 8);
        titleRow = panelState.row("Title", titleInput, rowWidth);
        addInspectorWidget(container, titleRow);

        descriptionInput = panelState.input("Description", "", value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject display = displayForNode(inspectorEditNodeId);
            if (display != null) {
                snapshot();
                update(display, "description", value);
            }
        });
        descriptionInput.setWidth(rowWidth - 8);
        descriptionRow = panelState.row("Description", descriptionInput, rowWidth);
        addInspectorWidget(container, descriptionRow);

        iconButton = searchableRecipeItemButton(() -> {
            JsonObject display = displayForNode(inspectorEditNodeId);
            return display != null ? text(display, "icon") : "minecraft:stone";
        }, rowWidth - 8, value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject display = displayForNode(inspectorEditNodeId);
            if (display != null && isRealOption(value)) {
                snapshot();
                update(display, "icon", value);
            }
        });
        iconRow = panelState.row("Icon", iconButton, rowWidth);
        addInspectorWidget(container, iconRow);

        frameDropdown = structuralDropdown(List.of("task", "goal", "challenge"), "task", value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject display = displayForNode(inspectorEditNodeId);
            if (display != null) {
                snapshot();
                update(display, "frame", value);
            }
        }, rowWidth - 8, false);
        frameRow = panelState.row("Frame", frameDropdown, rowWidth);
        addInspectorWidget(container, frameRow);

        showToastToggle = toggleWidget(true, value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject display = displayForNode(inspectorEditNodeId);
            if (display != null) {
                snapshot();
                update(display, "showToast", value);
            }
        });
        showToastRow = panelState.row("Show Toast", showToastToggle, rowWidth);
        addInspectorWidget(container, showToastRow);

        announceChatToggle = toggleWidget(false, value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject display = displayForNode(inspectorEditNodeId);
            if (display != null) {
                snapshot();
                update(display, "announceToChat", value);
            }
        });
        announceChatRow = panelState.row("Announce Chat", announceChatToggle, rowWidth);
        addInspectorWidget(container, announceChatRow);

        hiddenToggle = toggleWidget(false, value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject display = displayForNode(inspectorEditNodeId);
            if (display != null) {
                snapshot();
                update(display, "hidden", value);
            }
        });
        hiddenRow = panelState.row("Hidden", hiddenToggle, rowWidth);
        addInspectorWidget(container, hiddenRow);

        parentDropdown = structuralDropdown(parentChoices(), "root", value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject node = nodeById(inspectorEditNodeId);
            if (node != null) {
                snapshot();
                update(node, "parent", value);
            }
        }, rowWidth - 8, false);
        parentRow = panelState.row("Parent", parentDropdown, rowWidth);
        addInspectorWidget(container, parentRow);
        logicPanelWidgets.add(parentRow);

        completionSourceDropdown = structuralDropdown(List.of("Manual", "Flow", "Command", "Event"), "Manual", value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject node = nodeById(inspectorEditNodeId);
            if (node != null) {
                snapshot();
                updateCompletionSource(node, value);
            }
        }, rowWidth - 8, true);
        completionSourceRow = panelState.row("Completion Source", completionSourceDropdown, rowWidth);
        addInspectorWidget(container, completionSourceRow);
        logicPanelWidgets.add(completionSourceRow);

        rewardTypeDropdown = structuralDropdown(List.of("None", "Experience", "Loot", "Recipe"), "None", value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject node = nodeById(inspectorEditNodeId);
            if (node != null) {
                snapshot();
                updateRewardType(node, value);
            }
        }, rowWidth - 8, true);
        rewardTypeRow = panelState.row("Reward", rewardTypeDropdown, rowWidth);
        addInspectorWidget(container, rewardTypeRow);
        logicPanelWidgets.add(rewardTypeRow);

        onCompleteTypeDropdown = structuralDropdown(List.of("None", "Run Flow", "Run Command"), "None", value -> {
            if (syncingInspector) {
                return;
            }
            JsonObject node = nodeById(inspectorEditNodeId);
            if (node != null) {
                snapshot();
                updateOnCompleteType(node, value);
            }
        }, rowWidth - 8, true);
        onCompleteTypeRow = panelState.row("On Complete", onCompleteTypeDropdown, rowWidth);
        addInspectorWidget(container, onCompleteTypeRow);
        logicPanelWidgets.add(onCompleteTypeRow);
    }

    private void syncInspectorValues(int rowWidth, boolean nodeChanged) {
        JsonObject node = nodeById(inspectorEditNodeId);
        if (node == null) {
            return;
        }
        JsonObject display = object(node, "display");
        if (enabledToggle != null) {
            enabledToggle.setValue(bool(node, "enabled", true));
        }
        if (treeNameInput != null && (nodeChanged || !treeNameInput.isFocused())) {
            treeNameInput.setText(text(tree, "displayName"));
        }
        if (titleInput != null && (nodeChanged || !titleInput.isFocused())) {
            titleInput.setText(text(display, "title"));
        }
        if (descriptionInput != null && (nodeChanged || !descriptionInput.isFocused())) {
            descriptionInput.setText(text(display, "description"));
        }
        if (iconButton != null) {
            iconButton.setMessage(recipeItemButtonLabel(text(display, "icon")));
        }
        if (frameDropdown != null) {
            frameDropdown.setSelectedItem(frameValue(display));
        }
        if (showToastToggle != null) {
            showToastToggle.setValue(bool(display, "showToast", true));
        }
        if (announceChatToggle != null) {
            announceChatToggle.setValue(bool(display, "announceToChat", false));
        }
        if (hiddenToggle != null) {
            hiddenToggle.setValue(bool(display, "hidden", false));
        }
        JsonObject root = nodes().has("root") ? nodes().getAsJsonObject("root") : null;
        if (backgroundButton != null && root != null) {
            JsonObject rootDisplay = object(root, "display");
            backgroundButton.setMessage(resolveSelectedOption(normalizedOptions(backgroundOptions(), backgroundMaterial(rootDisplay)), backgroundMaterial(rootDisplay)));
        }
        if (backgroundRow != null) {
            backgroundRow.setVisible(root != null);
        }
        if (!"root".equals(selectedNode)) {
            refreshStructuralDropdown(parentDropdown, parentChoices(), parentValue(node));
            refreshStructuralDropdown(completionSourceDropdown, List.of("Manual", "Flow", "Command", "Event"), completionSource(node));
            refreshStructuralDropdown(rewardTypeDropdown, List.of("None", "Experience", "Loot", "Recipe"), rewardType(node));
            refreshStructuralDropdown(onCompleteTypeDropdown, List.of("None", "Run Flow", "Run Command"), onCompleteType(node));
        }
        int fieldWidth = rowWidth - 8;
        if (titleInput != null) {
            titleInput.setWidth(fieldWidth);
        }
        if (descriptionInput != null) {
            descriptionInput.setWidth(fieldWidth);
        }
        if (backgroundButton != null) {
            backgroundButton.setSize(fieldWidth, ReSyncStudioPanelState.FIELD_HEIGHT);
        }
        if (iconButton != null) {
            iconButton.setSize(fieldWidth, ReSyncStudioPanelState.FIELD_HEIGHT);
        }
    }

    private void updateLogicSectionVisibility() {
        boolean showLogic = nodeById(inspectorEditNodeId) != null && !"root".equals(inspectorEditNodeId);
        for (AnimatedWidget widget : logicPanelWidgets) {
            widget.setVisible(showLogic);
        }
    }

    private void updateRootMetaVisibility() {
        boolean showRootMeta = "root".equals(inspectorEditNodeId);
        for (AnimatedWidget widget : rootMetaPanelWidgets) {
            widget.setVisible(showRootMeta);
        }
    }

    private String dynamicStructureKey() {
        JsonObject node = currentNode();
        if (node == null || "root".equals(selectedNode)) {
            return "root";
        }
        return completionSource(node) + "|" + rewardType(node) + "|" + onCompleteType(node);
    }

    private void applyDynamicStructure() {
        if (inspector == null) {
            return;
        }
        Container container = inspector.container();
        float scrollOffset = container.getScrollOffset();
        int rowWidth = panelState.rowWidth(inspector);
        rebuildDynamicSection(container, rowWidth);
        lastDynamicStructure = dynamicStructureKey();
        syncDynamicFieldValues();
        container.updateWidgetPositions();
        container.setScrollOffset(scrollOffset);
    }

    private void rebuildDynamicSection(Container container, int rowWidth) {
        clearDynamicPanelWidgets(container);
        clearDynamicFieldRefs();
        JsonObject node = currentNode();
        if (node == null || "root".equals(selectedNode)) {
            return;
        }
        String source = completionSource(node);
        if ("Flow".equals(source)) {
            dynamicCompletionFlowButton = insertSearchableRow(container, completionSourceRow, "Completion Flow", this::flowOptions, null, () -> questCompletionValue(currentNode(), "flowId"), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateQuestCompletionString(current, "flowId", value);
                }
            });
        } else if ("Command".equals(source)) {
            dynamicCompletionCommandInput = insertTextRow(container, completionSourceRow, "Completion Command", questCompletionValue(node, "command"), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateQuestCompletionString(current, "command", value);
                }
            });
        } else if ("Event".equals(source)) {
            DropDownWidget<String> eventDropdown = createDynamicDropdown(eventTriggerOptions(), firstCriterionTrigger(node), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateFirstCriterionTrigger(current, value);
                }
            });
            TitledRowWidget eventRow = panelState.row("Event", eventDropdown, rowWidth);
            insertDynamicAfter(container, completionSourceRow, eventRow);
            dynamicEventDropdown = eventDropdown;
            dynamicPredicateFlowButton = insertSearchableRow(container, eventRow, "Predicate Flow", this::flowOptions, null, () -> firstCriterionValue(currentNode(), "predicateFlowId"), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateFirstCriterionString(current, "predicateFlowId", value);
                }
            });
        }
        String reward = rewardType(node);
        if ("Experience".equals(reward)) {
            dynamicXpInput = insertTextRow(container, rewardTypeRow, "XP", rewardValue(node, "experience"), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateRewardNumber(current, "experience", value);
                }
            });
        } else if ("Loot".equals(reward)) {
            dynamicLootInput = insertTextRow(container, rewardTypeRow, "Loot Tables", rewardArray(node, "loot"), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateRewardArray(current, "loot", value);
                }
            });
        } else if ("Recipe".equals(reward)) {
            dynamicRecipesInput = insertTextRow(container, rewardTypeRow, "Recipes", rewardArray(node, "recipes"), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateRewardArray(current, "recipes", value);
                }
            });
        }
        String completeType = onCompleteType(node);
        if ("Run Flow".equals(completeType)) {
            dynamicRunFlowButton = insertSearchableRow(container, onCompleteTypeRow, "Run Flow", this::flowOptions, null, () -> completionValue(currentNode(), "flowId"), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateCompletionString(current, "flowId", value);
                }
            });
        } else if ("Run Command".equals(completeType)) {
            dynamicRunCommandInput = insertTextRow(container, onCompleteTypeRow, "Run Command", completionArray(node, "commands"), rowWidth, value -> {
                JsonObject current = currentNode();
                if (current != null) {
                    updateCompletionArray(current, "commands", value);
                }
            });
        }
    }

    private void syncDynamicFieldValues() {
        JsonObject node = currentNode();
        if (node == null || "root".equals(selectedNode)) {
            return;
        }
        if (dynamicCompletionFlowButton != null) {
            String value = questCompletionValue(node, "flowId");
            dynamicCompletionFlowButton.setMessage(resolveSelectedOption(normalizedOptions(flowOptions(), value), value));
        }
        if (dynamicCompletionCommandInput != null && !dynamicCompletionCommandInput.isFocused()) {
            dynamicCompletionCommandInput.setText(questCompletionValue(node, "command"));
        }
        if (dynamicEventDropdown != null) {
            refreshStructuralDropdown(dynamicEventDropdown, eventTriggerOptions(), firstCriterionTrigger(node));
        }
        if (dynamicPredicateFlowButton != null) {
            String value = firstCriterionValue(node, "predicateFlowId");
            dynamicPredicateFlowButton.setMessage(resolveSelectedOption(normalizedOptions(flowOptions(), value), value));
        }
        if (dynamicXpInput != null && !dynamicXpInput.isFocused()) {
            dynamicXpInput.setText(rewardValue(node, "experience"));
        }
        if (dynamicLootInput != null && !dynamicLootInput.isFocused()) {
            dynamicLootInput.setText(rewardArray(node, "loot"));
        }
        if (dynamicRecipesInput != null && !dynamicRecipesInput.isFocused()) {
            dynamicRecipesInput.setText(rewardArray(node, "recipes"));
        }
        if (dynamicRunFlowButton != null) {
            String value = completionValue(node, "flowId");
            dynamicRunFlowButton.setMessage(resolveSelectedOption(normalizedOptions(flowOptions(), value), value));
        }
        if (dynamicRunCommandInput != null && !dynamicRunCommandInput.isFocused()) {
            dynamicRunCommandInput.setText(completionArray(node, "commands"));
        }
    }

    private void clearDynamicFieldRefs() {
        dynamicCompletionFlowButton = null;
        dynamicCompletionCommandInput = null;
        dynamicEventDropdown = null;
        dynamicPredicateFlowButton = null;
        dynamicXpInput = null;
        dynamicLootInput = null;
        dynamicRecipesInput = null;
        dynamicRunFlowButton = null;
        dynamicRunCommandInput = null;
    }

    private AnimatedButton insertSearchableRow(Container container, AnimatedWidget anchor, String label, Supplier<List<String>> choicesSupplier, String catalogSource, Supplier<String> selectedSupplier, int width, Consumer<String> onChange) {
        AnimatedButton button = searchableButton(choicesSupplier, selectedSupplier, catalogSource, width - 8, onChange);
        TitledRowWidget row = panelState.row(label, button, width);
        insertDynamicAfter(container, anchor, row);
        return button;
    }

    private TextInputWidget insertTextRow(Container container, AnimatedWidget anchor, String label, String value, int width, Consumer<String> onChange) {
        TextInputWidget input = panelState.input(label, value, next -> {
            snapshot();
            onChange.accept(next);
        });
        input.setWidth(width - 8);
        TitledRowWidget row = panelState.row(label, input, width);
        insertDynamicAfter(container, anchor, row);
        return input;
    }

    private void insertDynamicAfter(Container container, AnimatedWidget anchor, AnimatedWidget widget) {
        if (anchor == null) {
            addDynamicInspectorWidget(container, widget);
            return;
        }
        int index = container.getWidgets().indexOf(anchor);
        if (index < 0) {
            addDynamicInspectorWidget(container, widget);
            return;
        }
        ReSyncStudioPanelState.disableEntrance(widget);
        container.insertWidget(widget, index + 1);
        dynamicPanelWidgets.add(widget);
    }

    private void clearDynamicPanelWidgets(Container container) {
        for (AnimatedWidget widget : new ArrayList<>(dynamicPanelWidgets)) {
            container.removeWidget(widget);
        }
        dynamicPanelWidgets.clear();
        panelDropdowns.removeIf(dropdown -> dropdown != frameDropdown
            && dropdown != parentDropdown
            && dropdown != completionSourceDropdown
            && dropdown != rewardTypeDropdown
            && dropdown != onCompleteTypeDropdown);
    }

    private void addInspectorWidget(Container container, AnimatedWidget widget) {
        ReSyncStudioPanelState.disableEntrance(widget);
        container.addWidget(widget);
    }

    private void addDynamicInspectorWidget(Container container, AnimatedWidget widget) {
        addInspectorWidget(container, widget);
        dynamicPanelWidgets.add(widget);
    }

    private JsonObject nodeById(String nodeId) {
        return nodeId != null && nodes().has(nodeId) ? nodes().getAsJsonObject(nodeId) : null;
    }

    private JsonObject displayForNode(String nodeId) {
        JsonObject node = nodeById(nodeId);
        return node != null ? object(node, "display") : null;
    }

    private JsonObject currentNode() {
        return nodeById(selectedNode);
    }

    private JsonObject currentDisplay() {
        return displayForNode(selectedNode);
    }

    private AnimatedButton searchableRecipeItemButton(Supplier<String> selectedSupplier, int width, Consumer<String> onChange) {
        AnimatedButton button = new AnimatedButton.Builder()
            .label("")
            .size(width, ReSyncStudioPanelState.FIELD_HEIGHT)
            .entranceAnimation(false)
            .build();
        ReSyncStudioPanelState.disableEntrance(button);
        button.setAction(() -> {
            ItemOptionCatalog.ensureLoaded(serverId);
            String selected = selectedSupplier.get();
            if (!ItemOptionCatalog.isReady(serverId)) {
                button.setMessage("Loading");
                return;
            }
            button.setMessage(recipeItemButtonLabel(selected));
            openRecipeItemSearchSelector(selectedSupplier, value -> {
                if (isRealOption(value)) {
                    button.setMessage(recipeItemButtonLabel(value));
                    snapshot();
                    onChange.accept(value);
                }
            }, button.getX(), button.getY() + button.getHeight());
        });
        button.setMessage(recipeItemButtonLabel(selectedSupplier.get()));
        return button;
    }

    private String recipeItemButtonLabel(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        if (!ItemOptionCatalog.isReady(serverId)) {
            return "Loading";
        }
        return ItemOptionCatalog.label(serverId, value);
    }

    private AnimatedButton searchableButton(Supplier<List<String>> choicesSupplier, Supplier<String> selectedSupplier, String catalogSource, int width, Consumer<String> onChange) {
        AnimatedButton button = new AnimatedButton.Builder()
            .label("")
            .size(width, ReSyncStudioPanelState.FIELD_HEIGHT)
            .entranceAnimation(false)
            .build();
        ReSyncStudioPanelState.disableEntrance(button);
        button.setAction(() -> {
            String selected = selectedSupplier.get();
            List<String> options = normalizedOptions(choicesSupplier.get(), selected);
            button.setMessage(resolveSelectedOption(options, selected));
            if (options.size() == 1 && "Loading".equals(options.getFirst())) {
                if (catalogSource != null) {
                    requestCatalog(catalogSource);
                }
                return;
            }
            openSearchSelector(choicesSupplier, selectedSupplier, value -> {
                if (isRealOption(value)) {
                    button.setMessage(value);
                    snapshot();
                    onChange.accept(value);
                }
            }, button.getX(), button.getY() + button.getHeight());
        });
        refreshSearchButtonLabel(button, choicesSupplier, selectedSupplier);
        return button;
    }

    private void refreshSearchButtonLabel(AnimatedButton button, Supplier<List<String>> choicesSupplier, Supplier<String> selectedSupplier) {
        if (button == null || choicesSupplier == null || selectedSupplier == null) {
            return;
        }
        String selected = selectedSupplier.get();
        List<String> options = normalizedOptions(choicesSupplier.get(), selected);
        button.setMessage(resolveSelectedOption(options, selected));
    }

    private DropDownWidget<String> createDynamicDropdown(List<String> options, String selected, int width, Consumer<String> onChange) {
        List<String> safeOptions = options == null || options.isEmpty() ? List.of("") : options;
        String selectedValue = selected != null && safeOptions.contains(selected) ? selected : safeOptions.getFirst();
        DropDownWidget<String> dropdown = new DropDownWidget.Builder<>(safeOptions)
            .selectedItem(selectedValue)
            .size(width - 8, ReSyncStudioPanelState.FIELD_HEIGHT)
            .maxVisibleItems(8)
            .entranceAnimation(false)
            .onSelectionChanged(value -> {
                snapshot();
                onChange.accept(value);
            })
            .build();
        panelDropdowns.add(dropdown);
        return dropdown;
    }

    private DropDownWidget<String> structuralDropdown(List<String> options, String selected, Consumer<String> onChange, int width, boolean rebuildDynamic) {
        List<String> safeOptions = options == null || options.isEmpty() ? List.of("") : options;
        String selectedValue = selected != null && safeOptions.contains(selected) ? selected : safeOptions.getFirst();
        DropDownWidget<String> dropdown = new DropDownWidget.Builder<>(safeOptions)
            .selectedItem(selectedValue)
            .size(width, ReSyncStudioPanelState.FIELD_HEIGHT)
            .maxVisibleItems(8)
            .entranceAnimation(false)
            .onSelectionChanged(value -> {
                snapshot();
                onChange.accept(value);
                if (rebuildDynamic) {
                    applyDynamicStructure();
                }
            })
            .build();
        panelDropdowns.add(dropdown);
        return dropdown;
    }

    private void refreshStructuralDropdown(DropDownWidget<String> dropdown, List<String> options, String selected) {
        if (dropdown == null) {
            return;
        }
        List<String> safeOptions = options == null || options.isEmpty() ? List.of("") : options;
        String selectedValue = selected != null && safeOptions.contains(selected) ? selected : safeOptions.getFirst();
        dropdown.setItems(safeOptions, selectedValue);
    }

    private ToggleWidget toggleWidget(boolean value, Consumer<Boolean> onChange) {
        ToggleWidget toggle = new ToggleWidget.Builder()
            .toggled(value)
            .size(42, ReSyncStudioPanelState.FIELD_HEIGHT)
            .onChange(next -> {
                snapshot();
                onChange.accept(next);
            })
            .entranceAnimation(false)
            .build();
        ReSyncStudioPanelState.disableEntrance(toggle);
        return toggle;
    }

    private List<String> normalizedOptions(List<String> choices, String selected) {
        List<String> options = new ArrayList<>();
        if (choices != null) {
            for (String choice : choices) {
                if (choice != null && !choice.isBlank() && !options.contains(choice)) {
                    options.add(choice);
                }
            }
        }
        if (options.isEmpty()) {
            options.add("No Options");
        }
        if (selected != null && !selected.isBlank() && !"Loading".equals(selected) && !options.contains(selected)) {
            options.addFirst(selected);
        }
        return options;
    }

    private String resolveSelectedOption(List<String> options, String selected) {
        if (selected != null && options.contains(selected)) {
            return selected;
        }
        return options.isEmpty() ? "" : options.getFirst();
    }

    private void openRecipeItemSearchSelector(Supplier<String> selectedSupplier, Consumer<String> onSelected, int x, int y) {
        if (selectedSupplier == null || onSelected == null || serverId == null) {
            return;
        }
        ItemOptionCatalog.ensureLoaded(serverId);
        if (!ItemOptionCatalog.isReady(serverId)) {
            return;
        }
        String selected = selectedSupplier.get();
        List<String> values = ItemOptionCatalog.mergedValues(serverId);
        Map<String, OptionCatalogItem> catalogByValue = ItemOptionCatalog.byValue(serverId);
        closeActiveSearchSelector();
        activeSelectorUsesRecipeCatalog = true;
        activeSelectorOptions = null;
        activeSelectorSelected = selectedSupplier;
        activeSelectorOnSelected = onSelected;
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget.Builder builder = new ItemSelectorWidget.Builder(this)
            .size(220, 240)
            .dismissOnSelect(true)
            .emptyMessage("No Items")
            .onClose(() -> closeActiveSearchSelector(selectorRef[0]));
        builder.beginBatch();
        String lastGroup = null;
        boolean hasSelected = false;
        for (String value : values) {
            if (value == null || value.isBlank() || !isRealOption(value)) {
                continue;
            }
            OptionCatalogItem item = catalogByValue.get(value);
            String group = ItemOptionCatalog.groupForValue(value, item);
            if (!group.isBlank() && !group.equals(lastGroup)) {
                builder.addSectionHeader(group);
                lastGroup = group;
            }
            String label = item != null ? item.getLabel() : ItemOptionCatalog.label(serverId, value);
            if (value.equals(selected)) {
                hasSelected = true;
            }
            String description = item != null ? item.getDescription() : "";
            String searchTerms = value + " " + group + " " + description;
            builder.addItem(label, description, searchTerms, () -> onSelected.accept(value));
        }
        if (selected != null && !selected.isBlank() && !hasSelected && isRealOption(selected)) {
            builder.addItem(ItemOptionCatalog.label(serverId, selected), "", selected, () -> onSelected.accept(selected));
        }
        ItemSelectorWidget selector = builder.endBatch().build();
        selectorRef[0] = selector;
        selector.setSelectedItem(ItemOptionCatalog.label(serverId, selected));
        activeSearchSelector = selector;
        int selectorX = Math.clamp(x, 8, Math.max(8, width - selector.getWidth() - 8));
        int selectorY = Math.clamp(y, 32, Math.max(32, height - selector.getHeight() - 20));
        activeSearchSelector.show(selectorX, selectorY);
    }

    private void openSearchSelector(Supplier<List<String>> optionsSupplier, Supplier<String> selectedSupplier, Consumer<String> onSelected, int x, int y) {
        if (optionsSupplier == null || selectedSupplier == null || onSelected == null) {
            return;
        }
        List<String> options = normalizedOptions(optionsSupplier.get(), selectedSupplier.get());
        if (options.isEmpty()) {
            return;
        }
        closeActiveSearchSelector();
        activeSelectorUsesRecipeCatalog = false;
        activeSelectorOptions = optionsSupplier;
        activeSelectorSelected = selectedSupplier;
        activeSelectorOnSelected = onSelected;
        ItemSelectorWidget[] selectorRef = new ItemSelectorWidget[1];
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(this)
            .size(220, 240)
            .dismissOnSelect(true)
            .onClose(() -> closeActiveSearchSelector(selectorRef[0]))
            .build();
        selectorRef[0] = selector;
        for (String option : options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            if (!isRealOption(option)) {
                continue;
            }
            selector.addItem(option, () -> onSelected.accept(option));
        }
        selector.setSelectedItem(selectedSupplier.get());
        activeSearchSelector = selector;
        int selectorX = Math.clamp(x, 8, Math.max(8, width - selector.getWidth() - 8));
        int selectorY = Math.clamp(y, 32, Math.max(32, height - selector.getHeight() - 20));
        activeSearchSelector.show(selectorX, selectorY);
    }

    private void closeActiveSearchSelector() {
        closeActiveSearchSelector(activeSearchSelector);
    }

    private void closeActiveSearchSelector(ItemSelectorWidget selector) {
        if (selector != null) {
            selector.onClose = null;
            selector.hide();
            remove(selector);
        }
        if (selector == activeSearchSelector) {
            activeSearchSelector = null;
            activeSelectorOptions = null;
            activeSelectorSelected = null;
            activeSelectorOnSelected = null;
            activeSelectorUsesRecipeCatalog = false;
        }
        setFocusedWidget(null);
    }

    private void addNode() {
        snapshot();
        String id = nextNodeId();
        JsonObject node = new JsonObject();
        node.addProperty("enabled", true);
        node.addProperty("parent", selectedNode != null ? selectedNode : "root");
        JsonObject position = new JsonObject();
        position.addProperty("x", 2 + nodes().size());
        position.addProperty("y", nodes().size() % 4);
        node.add("position", position);
        JsonObject display = new JsonObject();
        display.addProperty("title", id);
        display.addProperty("description", "Server Progress");
        display.addProperty("icon", "minecraft:stone");
        display.addProperty("frame", "task");
        display.addProperty("showToast", true);
        display.addProperty("announceToChat", false);
        display.addProperty("hidden", false);
        node.add("display", display);
        JsonObject criterion = new JsonObject();
        criterion.addProperty("trigger", "impossible");
        JsonObject criteria = new JsonObject();
        criteria.add("requirement", criterion);
        node.add("criteria", criteria);
        ensureRequirements(node, "requirement");
        nodes().add(id, node);
        lastInspectorNode = "";
        lastDynamicStructure = "";
        refreshJson();
        selectNode(id);
    }

    private void deleteSelected() {
        if (selectedNode == null || "root".equals(selectedNode)) {
            return;
        }
        String removedId = selectedNode;
        snapshot();
        nodes().remove(removedId);
        for (Map.Entry<String, JsonElement> entry : nodes().entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            if (removedId.equals(text(node, "parent"))) {
                node.addProperty("parent", "root");
            }
        }
        lastInspectorNode = "";
        lastDynamicStructure = "";
        refreshJson();
        selectNode("root");
    }

    private void save() {
        commitInspectorEdits(inspectorEditNodeId);
        sanitizeTree();
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null) {
            manager.saveJsonResource(serverId, ReSyncResourceType.ADVANCEMENT_TREE, tree);
        }
        new Notification("Advancement Saved", text(tree, "displayName"), Notification.Type.SUCCESS);
    }

    private void sanitizeTree() {
        for (Map.Entry<String, JsonElement> entry : nodes().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject node = entry.getValue().getAsJsonObject();
            JsonObject display = object(node, "display");
            if (text(display, "icon").isBlank()) {
                display.addProperty("icon", "minecraft:stone");
            }
            if (text(display, "frame").isBlank()) {
                display.addProperty("frame", "task");
            }
            if (display.has("background")) {
                display.addProperty("background", normalizeBackgroundValue(text(display, "background")));
            }
            JsonObject criteria = object(node, "criteria");
            if ("root".equals(entry.getKey()) && criteria.entrySet().size() == 1 && criteria.has("requirement") && autoImpossibleCriterion(criteria.get("requirement"))) {
                criteria.remove("requirement");
                node.add("requirements", new JsonArray());
            }
            if (criteria.isEmpty()) {
                if ("root".equals(entry.getKey())) {
                    node.add("requirements", new JsonArray());
                } else {
                    JsonObject criterion = new JsonObject();
                    criterion.addProperty("trigger", "impossible");
                    criteria.add("requirement", criterion);
                }
            }
            if (!criteria.isEmpty()) {
                String criterion = criteria.keySet().stream().findFirst().orElse("requirement");
                ensureRequirements(node, criterion);
            }
            removeEmptyArrays(node, "rewards", "loot", "recipes");
            removeEmptyArrays(node, "onComplete", "commands");
            removeIfEmpty(node, "rewards");
            removeIfEmpty(node, "onComplete");
            removeIfEmpty(node, "questCompletion");
        }
    }

    private boolean autoImpossibleCriterion(JsonElement value) {
        if (value == null || !value.isJsonObject()) {
            return false;
        }
        JsonObject criterion = value.getAsJsonObject();
        return "impossible".equals(text(criterion, "trigger")) && !criterion.has("conditions") && !criterion.has("predicateFlowId");
    }

    private void removeEmptyArrays(JsonObject node, String objectKey, String... arrayKeys) {
        JsonObject object = optionalObject(node, objectKey);
        if (object == null) {
            return;
        }
        for (String arrayKey : arrayKeys) {
            if (object.has(arrayKey) && object.get(arrayKey).isJsonArray() && object.getAsJsonArray(arrayKey).isEmpty()) {
                object.remove(arrayKey);
            }
        }
    }

    private void snapshot() {
        undo.push(gson.toJson(tree));
        redo.clear();
    }

    private void undo() {
        if (!undo.isEmpty()) {
            redo.push(gson.toJson(tree));
            restore(undo.pop());
        }
    }

    private void redo() {
        if (!redo.isEmpty()) {
            undo.push(gson.toJson(tree));
            restore(redo.pop());
        }
    }

    private void restore(String json) {
        commitInspectorEdits(inspectorEditNodeId);
        JsonObject restored = JsonParser.parseString(json).getAsJsonObject();
        tree.keySet().clear();
        for (Map.Entry<String, JsonElement> entry : restored.entrySet()) {
            tree.add(entry.getKey(), entry.getValue());
        }
        if (!nodes().has(selectedNode)) {
            selectedNode = nodes().has("root") ? "root" : nodes().keySet().iterator().next();
        }
        lastInspectorNode = "";
        lastDynamicStructure = "";
        refreshJson();
        applyInspectorSelection();
    }

    private void refreshJson() {
    }

    private JsonObject nodes() {
        if (!tree.has("nodes") || !tree.get("nodes").isJsonObject()) {
            tree.add("nodes", new JsonObject());
        }
        return tree.getAsJsonObject("nodes");
    }

    private List<String> parentOptions() {
        List<String> options = new ArrayList<>();
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null) {
            for (Map.Entry<String, JsonObject> entry : manager.getJsonResourcesForServer(serverId, ReSyncResourceType.ADVANCEMENT_TREE).entrySet()) {
                JsonObject resourceNodes = entry.getValue().has("nodes") && entry.getValue().get("nodes").isJsonObject() ? entry.getValue().getAsJsonObject("nodes") : new JsonObject();
                for (String nodeId : resourceNodes.keySet()) {
                    options.add(entry.getKey().equals(text(tree, "id")) ? nodeId : entry.getKey() + "/" + nodeId);
                }
            }
        }
        if (options.isEmpty()) {
            options.addAll(nodes().keySet());
        }
        options.remove(selectedNode);
        return options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<String> parentChoices() {
        List<String> options = new ArrayList<>();
        options.add("root");
        options.addAll(parentOptions());
        return options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private String parentValue(JsonObject node) {
        String parent = text(node, "parent");
        return parent.isBlank() ? "root" : parent;
    }

    private List<String> backgroundOptions() {
        return catalogOptions(BLOCK_CATALOG, List.of("stone", "dirt", "sand", "netherrack", "end_stone", "deepslate"));
    }

    private List<String> catalogOptions(String source, List<String> fallback) {
        if (serverId == null || source == null) {
            return fallback.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        List<String> values = new ArrayList<>(OptionCatalogCache.getInstance().getValues(serverId, source));
        if (!values.isEmpty()) {
            return values.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
            requestCatalog(source);
            return List.of("Loading");
        }
        return fallback.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<String> eventTriggerOptions() {
        return List.of(
            "bee_nest_destroyed",
            "break_block",
            "break_furniture",
            "bred_animals",
            "changed_dimension",
            "consume_item",
            "craft_recipe",
            "effects_changed",
            "enchanted_item",
            "entity_hurt_player",
            "entity_killed_player",
            "fall_from_height",
            "filled_bucket",
            "fishing_rod_hooked",
            "held_item",
            "impossible",
            "in_biome",
            "interact_furniture",
            "item_durability_changed",
            "item_used_on_block",
            "kill_entity_with_item",
            "killed_by_arrow",
            "obtain_item",
            "permission",
            "place_block",
            "place_furniture",
            "player_hurt_entity",
            "player_interacted_with_entity",
            "player_killed_entity",
            "player_sheared_equipment",
            "recipe_crafted",
            "recipe_unlocked",
            "shoot_bow",
            "shot_crossbow",
            "slept_in_bed",
            "started_riding",
            "tame_animal",
            "used_ender_eye",
            "used_totem",
            "using_item",
            "villager_trade"
        );
    }

    private List<String> flowOptions() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return List.of("No Flow");
        }
        List<String> options = new ArrayList<>(manager.getFlowsForServer(serverId).keySet());
        options.removeIf(value -> manager.getCommandBinding(serverId, value) != null);
        if (options.isEmpty()) {
            options.add("No Flow");
        }
        return options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void update(JsonObject object, String key, String value) {
        object.addProperty(key, value != null ? value : "");
    }

    private void update(JsonObject object, String key, boolean value) {
        object.addProperty(key, value);
    }

    private boolean isRealOption(String value) {
        return value != null && !"Loading".equals(value) && !"No Options".equals(value);
    }

    private String frameValue(JsonObject display) {
        String frame = text(display, "frame").toLowerCase(Locale.ROOT);
        return List.of("task", "goal", "challenge").contains(frame) ? frame : "task";
    }

    private boolean bool(JsonObject value, String key, boolean fallback) {
        if (value == null || !value.has(key) || value.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return value.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String firstCriterionKey(JsonObject node) {
        JsonObject criteria = object(node, "criteria");
        return criteria.keySet().stream().findFirst().orElse("requirement");
    }

    private String firstCriterionTrigger(JsonObject node) {
        String trigger = firstCriterionValue(node, "trigger");
        return trigger.isBlank() ? "impossible" : trigger;
    }

    private String firstCriterionValue(JsonObject node, String key) {
        return text(firstCriterion(node), key);
    }

    private JsonObject firstCriterion(JsonObject node) {
        JsonObject criteria = object(node, "criteria");
        String key = firstCriterionKey(node);
        if (!criteria.has(key) || !criteria.get(key).isJsonObject()) {
            JsonObject criterion = new JsonObject();
            criterion.addProperty("trigger", "impossible");
            criteria.add(key, criterion);
        }
        ensureRequirements(node, key);
        return criteria.getAsJsonObject(key);
    }

    private void renameFirstCriterion(JsonObject node, String value) {
        String next = value == null || value.isBlank() ? "requirement" : value.trim();
        JsonObject criteria = object(node, "criteria");
        String current = firstCriterionKey(node);
        if (next.equals(current)) {
            ensureRequirements(node, next);
            return;
        }
        JsonElement existing = criteria.has(current) ? criteria.remove(current) : null;
        if (existing == null || !existing.isJsonObject()) {
            JsonObject criterion = new JsonObject();
            criterion.addProperty("trigger", "impossible");
            existing = criterion;
        }
        criteria.add(next, existing);
        ensureRequirements(node, next);
    }

    private void updateFirstCriterionTrigger(JsonObject node, String value) {
        String key = firstCriterionKey(node);
        JsonObject criterion = firstCriterion(node);
        criterion.addProperty("trigger", value == null || value.isBlank() ? "impossible" : value.trim());
        ensureRequirements(node, key);
    }

    private void updateFirstCriterionString(JsonObject node, String key, String value) {
        JsonObject criterion = firstCriterion(node);
        if (value == null || value.isBlank() || "No Flow".equals(value)) {
            criterion.remove(key);
            return;
        }
        criterion.addProperty(key, value.trim());
    }

    private String completionSource(JsonObject node) {
        JsonObject source = optionalObject(node, "questCompletion");
        String type = text(source, "type");
        if (List.of("Manual", "Flow", "Command", "Event").contains(type)) {
            return type;
        }
        return "Manual";
    }

    private void updateCompletionSource(JsonObject node, String value) {
        String type = value == null || value.isBlank() ? "Manual" : value;
        JsonObject source = object(node, "questCompletion");
        source.addProperty("type", type);
        if (!"Flow".equals(type)) {
            source.remove("flowId");
        }
        if (!"Command".equals(type)) {
            source.remove("command");
        }
        if (!"Event".equals(type)) {
            updateFirstCriterionTrigger(node, "impossible");
            updateFirstCriterionString(node, "predicateFlowId", "");
        } else if ("impossible".equals(firstCriterionTrigger(node))) {
            updateFirstCriterionTrigger(node, "obtain_item");
        }
        removeIfEmpty(node, "questCompletion");
    }

    private String questCompletionValue(JsonObject node, String key) {
        return text(optionalObject(node, "questCompletion"), key);
    }

    private void updateQuestCompletionString(JsonObject node, String key, String value) {
        JsonObject source = object(node, "questCompletion");
        if (value == null || value.isBlank() || "No Flow".equals(value)) {
            source.remove(key);
            removeIfEmpty(node, "questCompletion");
            return;
        }
        source.addProperty(key, value.trim());
    }

    private void ensureRequirements(JsonObject node, String key) {
        JsonArray group = new JsonArray();
        group.add(key);
        JsonArray requirements = new JsonArray();
        requirements.add(group);
        node.add("requirements", requirements);
    }

    private String rewardValue(JsonObject node, String key) {
        JsonObject rewards = optionalObject(node, "rewards");
        return text(rewards, key);
    }

    private String rewardArray(JsonObject node, String key) {
        return joinedArray(optionalObject(node, "rewards"), key);
    }

    private void updateRewardArray(JsonObject node, String key, String value) {
        updateStringArray(object(node, "rewards"), key, value);
        removeIfEmpty(node, "rewards");
    }

    private void updateRewardString(JsonObject node, String key, String value) {
        JsonObject rewards = object(node, "rewards");
        if (value == null || value.isBlank()) {
            rewards.remove(key);
            removeIfEmpty(node, "rewards");
            return;
        }
        rewards.addProperty(key, value.trim());
    }

    private void updateRewardNumber(JsonObject node, String key, String value) {
        JsonObject rewards = object(node, "rewards");
        if (value == null || value.isBlank()) {
            rewards.remove(key);
            removeIfEmpty(node, "rewards");
            return;
        }
        try {
            rewards.addProperty(key, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
        }
    }

    private String rewardType(JsonObject node) {
        JsonObject rewards = optionalObject(node, "rewards");
        if (!text(rewards, "experience").isBlank()) {
            return "Experience";
        }
        if (rewards != null && rewards.has("loot")) {
            return "Loot";
        }
        if (rewards != null && rewards.has("recipes")) {
            return "Recipe";
        }
        return "None";
    }

    private void updateRewardType(JsonObject node, String value) {
        if ("None".equals(value)) {
            node.remove("rewards");
            return;
        }
        JsonObject rewards = object(node, "rewards");
        rewards.remove("experience");
        rewards.remove("loot");
        rewards.remove("recipes");
        rewards.remove("function");
        switch (value) {
            case "Experience" -> rewards.addProperty("experience", 0);
            case "Loot" -> rewards.add("loot", new JsonArray());
            case "Recipe" -> rewards.add("recipes", new JsonArray());
            default -> removeIfEmpty(node, "rewards");
        }
    }

    private String completionValue(JsonObject node, String key) {
        return text(optionalObject(node, "onComplete"), key);
    }

    private String completionArray(JsonObject node, String key) {
        return joinedArray(optionalObject(node, "onComplete"), key);
    }

    private void updateCompletionString(JsonObject node, String key, String value) {
        JsonObject onComplete = object(node, "onComplete");
        if (value == null || value.isBlank() || "No Flow".equals(value)) {
            onComplete.remove(key);
            removeIfEmpty(node, "onComplete");
            return;
        }
        onComplete.addProperty(key, value.trim());
    }

    private void updateCompletionArray(JsonObject node, String key, String value) {
        updateStringArray(object(node, "onComplete"), key, value);
        removeIfEmpty(node, "onComplete");
    }

    private String onCompleteType(JsonObject node) {
        JsonObject onComplete = optionalObject(node, "onComplete");
        if (!text(onComplete, "flowId").isBlank()) {
            return "Run Flow";
        }
        if (onComplete != null && onComplete.has("commands")) {
            return "Run Command";
        }
        return "None";
    }

    private void updateOnCompleteType(JsonObject node, String value) {
        if ("None".equals(value)) {
            node.remove("onComplete");
            return;
        }
        JsonObject onComplete = object(node, "onComplete");
        onComplete.remove("flowId");
        onComplete.remove("commands");
        if ("Run Flow".equals(value)) {
            onComplete.addProperty("flowId", "");
        } else if ("Run Command".equals(value)) {
            onComplete.add("commands", new JsonArray());
        }
    }

    private String joinedArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray(key)) {
            if (element != null && !element.isJsonNull()) {
                values.add(element.getAsString());
            }
        }
        return String.join(", ", values);
    }

    private void updateStringArray(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) {
            object.remove(key);
            return;
        }
        JsonArray array = new JsonArray();
        for (String part : value.split("[,\\r\\n]+")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                array.add(trimmed);
            }
        }
        if (array.isEmpty()) {
            object.remove(key);
            return;
        }
        object.add(key, array);
    }

    private MinecraftRenderItem icon(JsonObject node) {
        String iconRef = text(object(node, "display"), "icon");
        ItemIconPreview.Preview preview = ItemIconPreview.resolve(serverId, iconRef);
        return preview.toRenderItem(title("", node));
    }

    private String nextNodeId() {
        int index = nodes().size();
        while (nodes().has("node_" + index)) {
            index++;
        }
        return "node_" + index;
    }

    private int advancementWindowX() {
        int panelWidth = inspector != null ? panelState.width() : 0;
        int availableWidth = Math.max(1, width - panelWidth);
        return Math.max(6, (availableWidth - WINDOW_WIDTH) / 2);
    }

    private int advancementWindowY() {
        int top = header().headerSize + 8;
        int availableHeight = Math.max(1, height - top);
        return Math.max(top, top + (availableHeight - WINDOW_HEIGHT) / 2);
    }

    private int nodeX(JsonObject node, int contentX, double viewPanX) {
        return (int) Math.floor(contentX + viewPanX + localNodeX(node));
    }

    private int nodeY(JsonObject node, int contentY, double viewPanY) {
        return (int) Math.floor(contentY + viewPanY + localNodeY(node));
    }

    private int localNodeX(JsonObject node) {
        return (int) Math.floor(decimal(object(node, "position"), "x") * ADVANCEMENT_X_SCALE);
    }

    private int localNodeY(JsonObject node) {
        return (int) Math.floor(decimal(object(node, "position"), "y") * ADVANCEMENT_Y_SCALE);
    }

    private double viewPanX(JsonObject nodes) {
        if (isDraggingNode()) {
            return dragViewPanX;
        }
        int[] bounds = contentBounds(nodes);
        if (bounds[2] - bounds[0] <= VIEWPORT_WIDTH) {
            if (customPan) {
                return panX;
            }
            return VIEWPORT_WIDTH / 2.0 - (bounds[0] + bounds[2]) / 2.0;
        }
        return Math.clamp(panX, VIEWPORT_WIDTH - bounds[2], -bounds[0]);
    }

    private double viewPanY(JsonObject nodes) {
        if (isDraggingNode()) {
            return dragViewPanY;
        }
        int[] bounds = contentBounds(nodes);
        if (bounds[3] - bounds[1] <= VIEWPORT_HEIGHT) {
            if (customPan) {
                return panY;
            }
            return VIEWPORT_HEIGHT / 2.0 - (bounds[1] + bounds[3]) / 2.0;
        }
        return Math.clamp(panY, VIEWPORT_HEIGHT - bounds[3], -bounds[1]);
    }

    private void beginCustomPan(JsonObject nodes) {
        if (customPan) {
            return;
        }
        panX = viewPanX(nodes);
        panY = viewPanY(nodes);
        customPan = true;
    }

    private boolean canScrollX(JsonObject nodes) {
        int[] bounds = contentBounds(nodes);
        return bounds[2] - bounds[0] > VIEWPORT_WIDTH;
    }

    private boolean canScrollY(JsonObject nodes) {
        int[] bounds = contentBounds(nodes);
        return bounds[3] - bounds[1] > VIEWPORT_HEIGHT;
    }

    private boolean canPanViewport(JsonObject nodes) {
        return customPan || canScrollX(nodes) || canScrollY(nodes);
    }

    private void autoPanDraggedNode(JsonObject nodes, double mouseX, double mouseY) {
        int contentX = advancementWindowX() + VIEWPORT_X;
        int contentY = advancementWindowY() + VIEWPORT_Y;
        double nextPanX = dragViewPanX;
        double nextPanY = dragViewPanY;
        if (mouseX < contentX + DRAG_AUTO_PAN_EDGE) {
            nextPanX += DRAG_AUTO_PAN_SPEED;
        } else if (mouseX > contentX + VIEWPORT_WIDTH - DRAG_AUTO_PAN_EDGE) {
            nextPanX -= DRAG_AUTO_PAN_SPEED;
        }
        if (mouseY < contentY + DRAG_AUTO_PAN_EDGE) {
            nextPanY += DRAG_AUTO_PAN_SPEED;
        } else if (mouseY > contentY + VIEWPORT_HEIGHT - DRAG_AUTO_PAN_EDGE) {
            nextPanY -= DRAG_AUTO_PAN_SPEED;
        }
        if (nextPanX == dragViewPanX && nextPanY == dragViewPanY) {
            return;
        }
        dragViewPanX = nextPanX;
        dragViewPanY = nextPanY;
        panX = dragViewPanX;
        panY = dragViewPanY;
        customPan = true;
        clampPan(nodes);
        dragViewPanX = panX;
        dragViewPanY = panY;
    }

    private void clampPan(JsonObject nodes) {
        int[] bounds = contentBounds(nodes);
        if (bounds[2] - bounds[0] > VIEWPORT_WIDTH) {
            panX = Math.clamp(panX, VIEWPORT_WIDTH - bounds[2], -bounds[0]);
        } else {
            panX = clampVisiblePan(panX, bounds[0], bounds[2], VIEWPORT_WIDTH);
        }
        if (bounds[3] - bounds[1] > VIEWPORT_HEIGHT) {
            panY = Math.clamp(panY, VIEWPORT_HEIGHT - bounds[3], -bounds[1]);
        } else {
            panY = clampVisiblePan(panY, bounds[1], bounds[3], VIEWPORT_HEIGHT);
        }
    }

    private double clampVisiblePan(double value, int min, int max, int viewportSize) {
        int visibleMargin = 8;
        double lower = -max + visibleMargin;
        double upper = viewportSize - min - visibleMargin;
        return Math.clamp(value, lower, upper);
    }

    private boolean isDraggingNode() {
        return draggedNode != null && !draggedNode.isBlank();
    }

    private void moveNodeToMouse(JsonObject node, double mouseX, double mouseY) {
        int contentX = advancementWindowX() + VIEWPORT_X;
        int contentY = advancementWindowY() + VIEWPORT_Y;
        JsonObject position = object(node, "position");
        position.addProperty("x", (mouseX - dragOffsetX - contentX - dragViewPanX) / ADVANCEMENT_X_SCALE);
        position.addProperty("y", (mouseY - dragOffsetY - contentY - dragViewPanY) / ADVANCEMENT_Y_SCALE);
        panX = dragViewPanX;
        panY = dragViewPanY;
        customPan = true;
    }

    private int[] contentBounds(JsonObject nodes) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            int x = localNodeX(node);
            int y = localNodeY(node);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + 30);
            maxY = Math.max(maxY, y + NODE_HEIGHT);
        }
        if (minX == Integer.MAX_VALUE) {
            return new int[] {0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT};
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    private void drawAdvancementBackground(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y) {
        MinecraftAssetReference window = gameAssets.asset("minecraft", "textures/gui/advancements/window.png");
        if (!drawAssetRegion(context, gameAssets, window, x, y, WINDOW_WIDTH, WINDOW_HEIGHT, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, 256, 256)) {
            context.fill(x, y, x + WINDOW_WIDTH, y + WINDOW_HEIGHT, 0xFFC6C6C6);
            context.fill(x + VIEWPORT_X, y + VIEWPORT_Y, x + VIEWPORT_X + VIEWPORT_WIDTH, y + VIEWPORT_Y + VIEWPORT_HEIGHT, 0xFF000000);
        }
        context.drawText(windowTitle(), x + 8, y + 6, 0xFF404040, false);
    }

    private String tabId(JsonObject value) {
        String id = text(value, "id");
        if (id.isBlank()) {
            id = text(value, "displayName");
        }
        return id.isBlank() ? "advancement" : id;
    }

    private JsonObject rootNode(JsonObject value) {
        if (value == null || !value.has("nodes") || !value.get("nodes").isJsonObject()) {
            return null;
        }
        JsonObject resourceNodes = value.getAsJsonObject("nodes");
        return resourceNodes.has("root") && resourceNodes.get("root").isJsonObject() ? resourceNodes.getAsJsonObject("root") : null;
    }

    private void drawTiledBackground(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, double viewPanX, double viewPanY) {
        String background = rootBackground();
        MinecraftAssetReference reference = assetReference(background, "textures/gui/advancements/backgrounds/stone.png");
        BufferedImage tile = gameAssets.getImage(reference);
        if (tile == null || tile == ResourceManager.getInstance().getMissingTexture()) {
            context.fill(x, y, x + VIEWPORT_WIDTH, y + VIEWPORT_HEIGHT, 0xFF202020);
            return;
        }
        int offsetX = (int) Math.floor(viewPanX) % BACKGROUND_TILE_SIZE;
        int offsetY = (int) Math.floor(viewPanY) % BACKGROUND_TILE_SIZE;
        for (int tileX = -1; tileX <= 15; tileX++) {
            for (int tileY = -1; tileY <= 8; tileY++) {
                context.drawPixelArt(tile, x + offsetX + BACKGROUND_TILE_SIZE * tileX, y + offsetY + BACKGROUND_TILE_SIZE * tileY, BACKGROUND_TILE_SIZE, BACKGROUND_TILE_SIZE);
            }
        }
    }

    private void drawViewportDepth(IDrawContext context, int x, int y) {
        int[] colors = {0x88000000, 0x66000000, 0x44000000, 0x22000000, 0x11000000};
        for (int i = 0; i < colors.length; i++) {
            context.fill(x, y + i, x + VIEWPORT_WIDTH, y + i + 1, colors[i]);
            context.fill(x + i, y, x + i + 1, y + VIEWPORT_HEIGHT, colors[i]);
            context.fill(x, y + VIEWPORT_HEIGHT - i - 1, x + VIEWPORT_WIDTH, y + VIEWPORT_HEIGHT - i, colors[i]);
            context.fill(x + VIEWPORT_WIDTH - i - 1, y, x + VIEWPORT_WIDTH - i, y + VIEWPORT_HEIGHT, colors[i]);
        }
    }

    private void drawConnection(IDrawContext context, JsonObject nodes, JsonObject node, int contentX, int contentY, double viewPanX, double viewPanY, boolean shadow) {
        JsonObject parentNode = parentNode(nodes, node);
        if (parentNode == null || hidden(node) || hidden(parentNode)) {
            return;
        }
        int parentCenterX = nodeX(parentNode, contentX, viewPanX) + 13;
        int parentExitX = nodeX(parentNode, contentX, viewPanX) + 30;
        int parentCenterY = nodeY(parentNode, contentY, viewPanY) + 13;
        int childCenterX = nodeX(node, contentX, viewPanX) + 13;
        int childCenterY = nodeY(node, contentY, viewPanY) + 13;
        int color = shadow ? 0xFF000000 : 0xFFFFFFFF;
        if (shadow) {
            hLine(context, parentExitX, parentCenterX, parentCenterY - 1, color);
            hLine(context, parentExitX + 1, parentCenterX, parentCenterY, color);
            hLine(context, parentExitX, parentCenterX, parentCenterY + 1, color);
            hLine(context, childCenterX, parentExitX - 1, childCenterY - 1, color);
            hLine(context, childCenterX, parentExitX - 1, childCenterY, color);
            hLine(context, childCenterX, parentExitX - 1, childCenterY + 1, color);
            vLine(context, parentExitX - 1, childCenterY, parentCenterY, color);
            vLine(context, parentExitX + 1, childCenterY, parentCenterY, color);
            return;
        }
        hLine(context, parentExitX, parentCenterX, parentCenterY, color);
        hLine(context, childCenterX, parentExitX, childCenterY, color);
        vLine(context, parentExitX, childCenterY, parentCenterY, color);
    }

    private void hLine(IDrawContext context, int x1, int x2, int y, int color) {
        context.fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, color);
    }

    private void vLine(IDrawContext context, int x, int y1, int y2, int color) {
        context.fill(x, Math.min(y1, y2), x + 1, Math.max(y1, y2) + 1, color);
    }

    private void drawHiddenNodeMarker(IDrawContext context, int x, int y) {
        for (int offset = 0; offset < 16; offset++) {
            context.fill(x + 6 + offset, y + 6 + offset, x + 7 + offset, y + 7 + offset, 0xCC000000);
            context.fill(x + 6 + offset, y + 7 + offset, x + 7 + offset, y + 8 + offset, 0x66000000);
        }
    }

    private TooltipLayout tooltipLayout(String id, JsonObject node, int contentX, int contentY, double viewPanX, double viewPanY) {
        int nodeX = nodeX(node, contentX, viewPanX);
        int nodeY = nodeY(node, contentY, viewPanY);
        JsonObject display = object(node, "display");
        String title = title(id, node);
        String description = text(display, "description");
        List<String> titleLines = wrapText(title, TITLE_TEXT_MAX_WIDTH, Integer.MAX_VALUE);
        int titleTextWidth = Math.max(widestLine(titleLines), 80);
        int targetDescriptionWidth = 29 + titleTextWidth;
        List<String> descriptionLines = findOptimalLines(description, targetDescriptionWidth);
        int boxWidth = Math.max(targetDescriptionWidth, widestLine(descriptionLines)) + 8;
        int titleHeight = Math.max(26, titleLines.size() * TEXT_LINE_HEIGHT + TITLE_BOX_EXTRA_HEIGHT);
        int descriptionTextHeight = descriptionLines.size() * TEXT_LINE_HEIGHT;
        int descriptionHeight = descriptionLines.isEmpty() ? 0 : descriptionTextHeight + DESCRIPTION_BOX_EXTRA_HEIGHT;
        int titleY = nodeY + (NODE_HEIGHT - titleHeight) / 2;
        int titleBottom = titleY + titleHeight;
        boolean flippedLeft = nodeX + boxWidth + 26 >= width;
        boolean flippedDescription = titleBottom + descriptionHeight >= contentY + VIEWPORT_HEIGHT;
        int boxX = flippedLeft ? nodeX - boxWidth + 26 + 6 : nodeX;
        int descriptionY = flippedDescription ? titleBottom - titleHeight - descriptionHeight : titleY;
        int descriptionTextY = (flippedDescription ? titleY - descriptionTextHeight + 1 : titleBottom) + DESCRIPTION_TEXT_Y_OFFSET;
        return new TooltipLayout(id, node, nodeX, nodeY, boxX, titleY, boxWidth, titleHeight, descriptionY, descriptionTextY, descriptionHeight, flippedLeft, titleLines, descriptionLines);
    }

    private void drawTooltip(IDrawContext context, MinecraftGameAssets gameAssets, TooltipLayout layout) {
        drawTooltipBoxTextures(context, gameAssets, layout);
        drawSprite(context, gameAssets, frameSprite(layout.node(), layout.id().equals(selectedNode)), layout.nodeX() + FRAME_X, layout.nodeY(), NODE_WIDTH, NODE_HEIGHT);
        drawTooltipBoxText(context, layout);
        MinecraftRenderItem icon = icon(layout.node());
        if (icon != null) {
            context.drawItem(icon, layout.nodeX() + ICON_X, layout.nodeY() + ICON_Y, 0);
        }
    }

    private void drawTooltipBoxTextures(IDrawContext context, MinecraftGameAssets gameAssets, TooltipLayout layout) {
        if (layout.descriptionHeight() > 0) {
            drawSpriteSlice(context, gameAssets, "advancements/title_box", layout.boxX(), layout.descriptionY(), layout.boxWidth(), layout.titleHeight() + layout.descriptionHeight());
        }
        drawSpriteSlice(context, gameAssets, layout.id().equals(selectedNode) ? "advancements/box_obtained" : "advancements/box_unobtained", layout.boxX(), layout.titleY(), layout.boxWidth(), layout.titleHeight());
    }

    private void drawTooltipBoxText(IDrawContext context, TooltipLayout layout) {
        int titleTextX = layout.flippedLeft() ? layout.boxX() + 5 : layout.nodeX() + TITLE_TEXT_X;
        int titleTextY = layout.titleY() + 9;
        for (String line : layout.titleLines()) {
            context.drawText(line, titleTextX, titleTextY, 0xFFFFFFFF, true);
            titleTextY += TEXT_LINE_HEIGHT;
        }
        int descriptionTextY = layout.descriptionTextY();
        for (String line : layout.descriptionLines()) {
            context.drawText(line, layout.boxX() + DESCRIPTION_TEXT_X, descriptionTextY, descriptionColor(text(object(layout.node(), "display"), "frame")), true);
            descriptionTextY += TEXT_LINE_HEIGHT;
        }
    }

    private void drawSprite(IDrawContext context, MinecraftGameAssets gameAssets, String sprite, int x, int y, int width, int height) {
        MinecraftAssetReference reference = gameAssets.asset("minecraft", "textures/gui/sprites/" + sprite + ".png");
        if (drawAssetRegion(context, gameAssets, reference, x, y, width, height, 0, 0, width, height, width, height)) {
            return;
        }
        context.fill(x, y, x + width, y + height, 0xFF1F1F1F);
        context.fill(x, y, x + width, y + 1, 0xFFFFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF555555);
        context.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
        context.fill(x + width - 1, y, x + width, y + height, 0xFF555555);
    }

    private void drawSpriteSlice(IDrawContext context, MinecraftGameAssets gameAssets, String sprite, int x, int y, int width, int height) {
        MinecraftAssetReference reference = gameAssets.asset("minecraft", "textures/gui/sprites/" + sprite + ".png");
        if (drawNineSlice(context, gameAssets, reference, x, y, width, height)) {
            return;
        }
        context.fill(x, y, x + width, y + height, 0xFF606060);
    }

    private boolean drawNineSlice(IDrawContext context, MinecraftGameAssets gameAssets, MinecraftAssetReference reference, int x, int y, int width, int height) {
        BufferedImage image = gameAssets.getImage(reference);
        Object nativeIdentifier = gameAssets.getNativeIdentifier(reference);
        if ((image == null || image == ResourceManager.getInstance().getMissingTexture()) && nativeIdentifier == null) {
            return false;
        }
        int border = Math.min(TOOLTIP_SLICE_BORDER, Math.min(width, height) / 2);
        int centerWidth = Math.max(0, width - border * 2);
        int centerHeight = Math.max(0, height - border * 2);
        int sourceCenterWidth = TOOLTIP_SLICE_WIDTH - TOOLTIP_SLICE_BORDER * 2;
        int sourceCenterHeight = TOOLTIP_SLICE_HEIGHT - TOOLTIP_SLICE_BORDER * 2;
        drawAssetRegion(context, gameAssets, reference, x, y, border, border, 0, 0, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        drawAssetRegion(context, gameAssets, reference, x + border, y, centerWidth, border, TOOLTIP_SLICE_BORDER, 0, sourceCenterWidth, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        drawAssetRegion(context, gameAssets, reference, x + border + centerWidth, y, border, border, TOOLTIP_SLICE_WIDTH - TOOLTIP_SLICE_BORDER, 0, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        drawAssetRegion(context, gameAssets, reference, x, y + border, border, centerHeight, 0, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, sourceCenterHeight, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        drawAssetRegion(context, gameAssets, reference, x + border, y + border, centerWidth, centerHeight, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, sourceCenterWidth, sourceCenterHeight, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        drawAssetRegion(context, gameAssets, reference, x + border + centerWidth, y + border, border, centerHeight, TOOLTIP_SLICE_WIDTH - TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, sourceCenterHeight, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        drawAssetRegion(context, gameAssets, reference, x, y + border + centerHeight, border, border, 0, TOOLTIP_SLICE_HEIGHT - TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        drawAssetRegion(context, gameAssets, reference, x + border, y + border + centerHeight, centerWidth, border, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_HEIGHT - TOOLTIP_SLICE_BORDER, sourceCenterWidth, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        drawAssetRegion(context, gameAssets, reference, x + border + centerWidth, y + border + centerHeight, border, border, TOOLTIP_SLICE_WIDTH - TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_HEIGHT - TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_BORDER, TOOLTIP_SLICE_WIDTH, TOOLTIP_SLICE_HEIGHT);
        return true;
    }

    private boolean drawAssetRegion(IDrawContext context, MinecraftGameAssets gameAssets, MinecraftAssetReference reference, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || regionWidth <= 0 || regionHeight <= 0) {
            return true;
        }
        Object nativeIdentifier = gameAssets.getNativeIdentifier(reference);
        if (nativeIdentifier != null && context.drawNativeTexture(nativeIdentifier, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight)) {
            return true;
        }
        BufferedImage image = gameAssets.getImage(reference);
        if (image == null || image == ResourceManager.getInstance().getMissingTexture()) {
            return false;
        }
        int safeU = Math.clamp(u, 0, Math.max(0, image.getWidth() - 1));
        int safeV = Math.clamp(v, 0, Math.max(0, image.getHeight() - 1));
        int safeWidth = Math.clamp(regionWidth, 1, image.getWidth() - safeU);
        int safeHeight = Math.clamp(regionHeight, 1, image.getHeight() - safeV);
        String key = reference.namespacedPath() + ":" + safeU + ":" + safeV + ":" + safeWidth + ":" + safeHeight;
        BufferedImage slice = imageSlices.computeIfAbsent(key, ignored -> image.getSubimage(safeU, safeV, safeWidth, safeHeight));
        context.drawPixelArt(slice, x, y, width, height);
        return true;
    }

    private String frameSprite(JsonObject node, boolean selected) {
        JsonObject display = object(node, "display");
        String frame = text(display, "frame").toLowerCase(Locale.ROOT);
        if (!List.of("task", "challenge", "goal").contains(frame)) {
            frame = "task";
        }
        return "advancements/" + frame + "_frame_" + (selected ? "obtained" : "unobtained");
    }

    private int descriptionColor(String frame) {
        return "challenge".equalsIgnoreCase(frame) ? 0xFFAA00AA : 0xFF55FF55;
    }

    private int textWidth(String value) {
        return value == null || value.isBlank() ? 0 : TextRenderer.getWidth(value);
    }

    private int widestLine(List<String> lines) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, textWidth(line));
        }
        return width;
    }

    private List<String> findOptimalLines(String value, int targetWidth) {
        List<String> closest = List.of();
        int closestDistance = Integer.MAX_VALUE;
        for (int offset : DESCRIPTION_SPLIT_OFFSETS) {
            List<String> lines = wrapText(value, Math.max(1, targetWidth - offset), Integer.MAX_VALUE);
            int distance = Math.abs(widestLine(lines) - targetWidth);
            if (distance <= 10) {
                return lines;
            }
            if (distance < closestDistance) {
                closest = lines;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private List<String> wrapText(String value, int maxWidth, int maxLines) {
        if (value == null || value.isBlank() || maxLines <= 0) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.replace('\r', '\n').split("\\n")) {
            wrapParagraph(paragraph.trim(), maxWidth, maxLines, lines);
            if (lines.size() >= maxLines) {
                break;
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private void wrapParagraph(String paragraph, int maxWidth, int maxLines, List<String> lines) {
        if (paragraph.isBlank()) {
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String word : paragraph.split("\\s+")) {
            String next = current.length() == 0 ? word : current + " " + word;
            if (textWidth(next) <= maxWidth) {
                current.setLength(0);
                current.append(next);
                continue;
            }
            if (current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                if (lines.size() >= maxLines) {
                    return;
                }
            }
            if (textWidth(word) <= maxWidth) {
                current.append(word);
                continue;
            }
            for (String part : splitLongWord(word, maxWidth)) {
                if (lines.size() >= maxLines) {
                    return;
                }
                if (textWidth(part) <= maxWidth && current.length() == 0) {
                    current.append(part);
                } else {
                    lines.add(part);
                }
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(current.toString());
        }
    }

    private List<String> splitLongWord(String word, int maxWidth) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            String next = current.toString() + word.charAt(i);
            if (textWidth(next) <= maxWidth || current.length() == 0) {
                current.append(word.charAt(i));
                continue;
            }
            parts.add(current.toString());
            current.setLength(0);
            current.append(word.charAt(i));
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private String windowTitle() {
        String title = text(tree, "displayName");
        if (title.isBlank()) {
            title = text(tree, "id");
        }
        if (title.isBlank()) {
            JsonObject root = nodes().has("root") ? nodes().getAsJsonObject("root") : null;
            title = root != null ? text(object(root, "display"), "title") : "";
        }
        return title.isBlank() ? "Advancements" : title;
    }

    private String rootBackground() {
        JsonObject root = nodes().has("root") ? nodes().getAsJsonObject("root") : null;
        String background = root != null ? backgroundPath(object(root, "display")) : "";
        if (background.isBlank()) {
            background = text(object(tree, "display"), "background");
        }
        if (background.isBlank()) {
            background = text(tree, "background");
        }
        return background.isBlank() ? "minecraft:textures/gui/advancements/backgrounds/stone.png" : background;
    }

    private String backgroundPath(JsonObject display) {
        return normalizeBackgroundValue(text(display, "background"));
    }

    private String backgroundMaterial(JsonObject display) {
        String background = backgroundPath(display);
        String path = background.contains(":") ? background.split(":", 2)[1] : background;
        if (path.startsWith("textures/block/") && path.endsWith(".png")) {
            return path.substring("textures/block/".length(), path.length() - ".png".length());
        }
        return "stone";
    }

    private String materialToBackgroundTexture(String value) {
        String material = value == null ? "" : value.trim();
        if (material.isBlank() || "Loading".equals(material)) {
            material = "STONE";
        }
        if (material.contains(":")) {
            material = material.split(":", 2)[1];
        }
        String path = material.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (path.startsWith("textures/")) {
            return "minecraft:" + normalizeAssetPath(path, "textures/block/stone.png");
        }
        if (path.endsWith(".png")) {
            return "minecraft:" + normalizeAssetPath(path, "textures/block/stone.png");
        }
        return "minecraft:textures/block/" + path + ".png";
    }

    private String normalizeBackgroundValue(String value) {
        String background = value == null ? "" : value.trim();
        if (background.isBlank()) {
            return "minecraft:textures/gui/advancements/backgrounds/stone.png";
        }
        if (!background.contains(":") && !background.contains("/") && !background.endsWith(".png")) {
            return materialToBackgroundTexture(background);
        }
        if (background.startsWith("minecraft:") && !background.substring("minecraft:".length()).contains("/") && !background.endsWith(".png")) {
            return materialToBackgroundTexture(background);
        }
        if (background.contains(":")) {
            String[] parts = background.split(":", 2);
            return parts[0] + ":" + normalizeAssetPath(parts[1], "textures/gui/advancements/backgrounds/stone.png");
        }
        return "minecraft:" + normalizeAssetPath(background, "textures/gui/advancements/backgrounds/stone.png");
    }

    private JsonObject parentNode(JsonObject nodes, JsonObject node) {
        String parentId = text(node, "parent");
        if (parentId.isBlank() || parentId.contains(":")) {
            return null;
        }
        String localParent = parentId;
        if (parentId.contains("/")) {
            String[] parts = parentId.split("/", 2);
            localParent = parts.length == 2 && parts[0].equals(text(tree, "id")) ? parts[1] : "";
        }
        return nodes.has(localParent) ? nodes.getAsJsonObject(localParent) : null;
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

    private MinecraftAssetReference assetReference(String value, String fallbackPath) {
        String asset = value == null ? "" : value.trim();
        if (asset.isBlank()) {
            return MinecraftAssetReference.of("minecraft", fallbackPath);
        }
        if (asset.contains(":")) {
            String[] parts = asset.split(":", 2);
            return MinecraftAssetReference.of(parts[0], normalizeAssetPath(parts[1], fallbackPath));
        }
        return MinecraftAssetReference.of("minecraft", normalizeAssetPath(asset, fallbackPath));
    }

    private String normalizeAssetPath(String path, String fallbackPath) {
        if (path == null || path.isBlank()) {
            return fallbackPath;
        }
        String clean = path.replace('\\', '/');
        if (clean.startsWith("gui/") || clean.startsWith("block/")) {
            clean = "textures/" + clean;
        } else if (!clean.startsWith("textures/")) {
            clean = "textures/gui/advancements/backgrounds/" + clean;
        }
        if (!clean.endsWith(".png")) {
            clean += ".png";
        }
        return clean;
    }

    private boolean hidden(JsonObject node) {
        JsonObject display = object(node, "display");
        return display.has("hidden") && display.get("hidden").getAsBoolean();
    }

    private String title(String id, JsonObject node) {
        JsonObject display = object(node, "display");
        String title = text(display, "title");
        return title.isBlank() ? id : title;
    }

    private JsonObject object(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonObject()) {
            value.add(key, new JsonObject());
        }
        return value.getAsJsonObject(key);
    }

    private JsonObject optionalObject(JsonObject value, String key) {
        return value != null && value.has(key) && value.get(key).isJsonObject() ? value.getAsJsonObject(key) : null;
    }

    private void removeIfEmpty(JsonObject value, String key) {
        JsonObject object = optionalObject(value, key);
        if (object != null && object.isEmpty()) {
            value.remove(key);
        }
    }

    private String text(JsonObject value, String key) {
        return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : "";
    }

    private double decimal(JsonObject value, String key) {
        return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsDouble() : 0;
    }
}
