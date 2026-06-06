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
import redxax.oxy.remotely.data.flow.ReSyncProtocolContract;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.ReSyncResourceCreator;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.StudioPanel;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rescreen.game.MinecraftAssetReference;
import restudio.rescreen.game.MinecraftGameAssets;
import restudio.rescreen.game.MinecraftGameItems;
import restudio.rescreen.game.tooltip.MinecraftTextComponents;
import restudio.rescreen.game.tooltip.MinecraftTooltip;
import restudio.rescreen.game.tooltip.MinecraftTooltipLine;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.CompactBindingWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;
import restudio.rescreen.util.ResourceManager;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import static restudio.rescreen.config.Config.desktopMode;

public class DialogDesignerScreen extends StudioScreen implements DesktopWindowBehaviorProvider {
    private static final CopyOnWriteArraySet<DialogDesignerScreen> OPEN_SCREENS = new CopyOnWriteArraySet<>();
    private static final int OVERLAY_COLOR = 0xA0101010;
    private static final int DIALOG_WIDTH = 310;
    private static final int HEADER_HEIGHT = 33;
    private static final int FOOTER_HEIGHT = 5;
    private static final int CONTENT_MARGIN_TOP = 30;
    private static final int BODY_SPACING = 10;
    private static final int LABEL_SPACING = 4;
    private static final int GRID_SPACING = 2;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FIELD_HEIGHT = 20;
    private static final int WARNING_BUTTON_SIZE = 20;
    private static final int WARNING_TITLE_SPACING = 10;
    private static final int CHECKBOX_SIZE = 17;
    private static final int TEXT_LINE_HEIGHT = 9;
    private static final List<String> DIALOG_TYPES = List.of("minecraft:notice", "minecraft:confirmation", "minecraft:multi_action", "minecraft:dialog_list");
    private static final List<String> BODY_TYPES = List.of("minecraft:plain_message", "minecraft:item");
    private static final List<String> INPUT_TYPES = List.of("minecraft:text", "minecraft:boolean", "minecraft:number_range", "minecraft:single_option");
    private static final List<String> ACTION_MODES = List.of("None", "Run Flow", "Run Function", "Run Command", "Open Dialog", "Custom Event");
    private static final List<String> PREDICATE_MODES = CompactBindingSupport.PREDICATE_MODES;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, BufferedImage> imageSlices = new HashMap<>();
    private JsonObject dialog;
    private final String serverId;
    private final Object parent;
    private final boolean forceSuperScreen;
    private final ReSyncStudioPanelState panelState = new ReSyncStudioPanelState().padding(6);
    private final History<String> history = history(() -> gson.toJson(dialog), this::restore);
    private final List<AnimatedWidget> inspectorWidgets = new ArrayList<>();
    private final List<AnimatedWidget> selectionInspectorWidgets = new ArrayList<>();
    private final List<PreviewElementRect> previewElements = new ArrayList<>();
    private final List<PreviewOrderRect> previewOrderControls = new ArrayList<>();
    private StudioPanel inspectorStudioPanel;
    private SidePanel inspector;
    private Selection selection = Selection.global();
    private CompactBindingWidget actionBinding;
    private CompactBindingWidget predicateBinding;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private boolean closingRequested;
    private boolean closeCompleted;
    private boolean syncing;
    private boolean collectingSelectionWidgets;

    private record Selection(String kind, int index) {
        private static Selection global() {
            return new Selection("global", -1);
        }
    }

    private record PreviewElementRect(String kind, int index, int x, int y, int width, int height, MinecraftTooltip tooltip) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record PreviewOrderRect(String kind, int index, int direction, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public DialogDesignerScreen(JsonObject dialog, String serverId, Object parent) {
        this(dialog, serverId, parent, !(parent instanceof Screen));
    }

    public DialogDesignerScreen(JsonObject dialog, String serverId, Object parent, boolean forceSuperScreen) {
        this.dialog = dialog != null ? dialog : new JsonObject();
        this.serverId = serverId;
        this.parent = parent;
        this.forceSuperScreen = forceSuperScreen;
        autoResizeContainers = false;
        preserveStateOnDisplay = true;
        OPEN_SCREENS.add(this);
        ensureDefaults();
    }

    public static void refreshCatalogForServer(String serverId) {
        for (DialogDesignerScreen screen : OPEN_SCREENS) {
            if (screen != null && serverId != null && serverId.equals(screen.serverId)) {
                screen.refreshBindings();
            }
        }
    }

    public String getDesktopAppId() {
        return "dialog-designer";
    }

    public String getDesktopAppTitle() {
        return "Dialog Designer";
    }

    public String getDesktopAppIconPath() {
        return "chat.png";
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
        ensureInspector();
        rebuildInspector();
        updateLayout(true);
    }

    @Override
    public void close() {
        requestClose();
    }

    @Override
    public void renderHandler(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderHandler(context, mouseX, mouseY, delta);
        if (inspectorStudioPanel != null && inspector != null && inspector.isVisible()) {
            renderStudioPanel(inspectorStudioPanel, context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        updateLayout(false);
        updateCloseAnimation();
        super.render(context, mouseX, mouseY, delta);
        drawPreviewTooltip(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        if (forceSuperScreen) {
            context.fill(0, 0, width, height, OVERLAY_COLOR);
        }
        drawPreview(context, mouseX, mouseY);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        updateLayout(true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inspector != null) {
            if (inspector.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (inspector.isMouseOver(mouseX, mouseY)) {
                setFocusedWidget(null);
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseClickedPreview(mouseX, mouseY)) {
            setFocusedWidget(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean mouseClickedPreview(double mouseX, double mouseY) {
        for (PreviewOrderRect rect : previewOrderControls) {
            if (rect.contains(mouseX, mouseY)) {
                moveSelectedElement(rect.kind(), rect.index(), rect.direction());
                return true;
            }
        }
        for (int i = previewElements.size() - 1; i >= 0; i--) {
            PreviewElementRect rect = previewElements.get(i);
            if (rect.contains(mouseX, mouseY)) {
                select(new Selection(rect.kind(), rect.index()));
                return true;
            }
        }
        if (mouseX >= previewX && mouseX <= previewX + previewWidth && mouseY >= previewY && mouseY <= previewY + previewHeight) {
            select(Selection.global());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (inspector != null && inspector.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (inspector != null && inspector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inspector != null && inspector.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleStudioHistoryShortcut(keyCode, modifiers)) {
            return true;
        }
        if (inspector != null && inspector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && !isStudioKeyboardInputFocused()) {
            return deleteSelection();
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inspector != null && inspector.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void buildHeader() {
        header().reset();
        if (shouldShowBackButton()) {
            header().addRight("close.png", this::requestClose, "Back");
        }
        header().addRight("save.png", this::save, "Save");
        header().addRight("add.png", this::addAction, "Add Button");
        header().addRight("topPanel.png", this::addInput, "Add Input");
        header().addRight("text.png", this::addBody, "Add Text");
        header().build();
    }

    private boolean shouldShowBackButton() {
        return !desktopMode || shouldForceSuperScreen();
    }

    private void ensureInspector() {
        if (inspector != null) {
            return;
        }
        inspectorStudioPanel = rightStudioPanel("dialog_inspector").show();
        inspector = inspectorStudioPanel.sidePanel();
        inspectorStudioPanel.padding(panelState.padding());
    }

    private void requestClose() {
        if (closeCompleted) {
            return;
        }
        if (!closingRequested) {
            closingRequested = true;
            if (inspector != null) {
                inspector.hide();
            }
        }
        updateCloseAnimation();
    }

    private void updateCloseAnimation() {
        if (!closingRequested || closeCompleted) {
            return;
        }
        if (inspector == null || inspector.getAnimatedWidth() <= 1f) {
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

    private void updateLayout(boolean force) {
        if (!force && width == lastWidth && height == lastHeight) {
            return;
        }
        lastWidth = width;
        lastHeight = height;
        if (inspectorStudioPanel != null) {
            inspectorStudioPanel.layout();
        }
        int contentTop = header().headerSize + 8;
        int rightWidth = inspector != null ? (int) inspector.getAnimatedWidth() : 0;
        int availableWidth = Math.max(160, width - rightWidth - 24);
        previewWidth = availableWidth;
        previewHeight = Math.max(120, height - contentTop - 20);
        previewX = 12;
        previewY = contentTop;
    }

    private void rebuildInspector() {
        if (inspector == null) {
            return;
        }
        Container container = inspector.container();
        float scroll = container.getScrollOffset();
        for (AnimatedWidget widget : new ArrayList<>(inspectorWidgets)) {
            container.removeWidget(widget);
        }
        for (AnimatedWidget widget : new ArrayList<>(selectionInspectorWidgets)) {
            container.removeWidget(widget);
        }
        inspectorWidgets.clear();
        selectionInspectorWidgets.clear();
        int rowWidth = inspectorStudioPanel != null ? inspectorStudioPanel.rowWidth() : panelState.rowWidth(inspector);
        syncing = true;
        try {
            buildGlobalSection(container, rowWidth);
        } finally {
            syncing = false;
        }
        rebuildSelectionSection(container, rowWidth);
        container.setScrollOffset(scroll);
    }

    private void rebuildSelectionSection() {
        if (inspector == null) {
            return;
        }
        Container container = inspector.container();
        float scroll = container.getScrollOffset();
        int rowWidth = inspectorStudioPanel != null ? inspectorStudioPanel.rowWidth() : panelState.rowWidth(inspector);
        rebuildSelectionSection(container, rowWidth);
        container.snapWidgetPositions();
        container.setScrollOffset(scroll);
    }

    private void rebuildSelectionSection(Container container, int rowWidth) {
        for (AnimatedWidget widget : new ArrayList<>(selectionInspectorWidgets)) {
            container.removeWidget(widget);
        }
        selectionInspectorWidgets.clear();
        actionBinding = null;
        predicateBinding = null;
        collectingSelectionWidgets = true;
        syncing = true;
        try {
            buildBodySection(container, rowWidth);
            buildInputSection(container, rowWidth);
            buildActionSection(container, rowWidth);
        } finally {
            syncing = false;
            collectingSelectionWidgets = false;
        }
        container.updateWidgetPositions();
        if (actionBinding != null) {
            actionBinding.refresh();
        }
        if (predicateBinding != null) {
            predicateBinding.refresh();
        }
    }

    private void refreshBindings() {
        if (actionBinding != null) {
            actionBinding.refresh();
        }
        if (predicateBinding != null) {
            predicateBinding.refresh();
        }
    }

    private void buildGlobalSection(Container container, int rowWidth) {
        addRow(container, panelState.hint("Dialog", rowWidth));
        addTextRow(container, "Name", text(dialog, "displayName"), value -> updateString(dialog, "displayName", value), rowWidth);
        DropDownWidget<String> type = dropdown(DIALOG_TYPES, dialogType(), value -> updateDialogType(value));
        addRow(container, panelState.row("Type", type, rowWidth, inspectorDescription("Type")));
        addTextRow(container, "Title", text(dialog, "title"), value -> updateString(dialog, "title", value), rowWidth);
        ToggleWidget enabled = toggle(bool(dialog, "enabled", true), value -> updateBoolean(dialog, "enabled", value));
        addRow(container, panelState.row("Enabled", enabled, rowWidth, inspectorDescription("Enabled")));
        ToggleWidget escape = toggle(bool(dialog, "can_close_with_escape", true), value -> updateBoolean(dialog, "can_close_with_escape", value));
        addRow(container, panelState.row("Escape", escape, rowWidth, inspectorDescription("Escape")));
        DropDownWidget<String> afterAction = dropdown(List.of("close", "none", "wait_for_response"), textOr(dialog, "after_action", "close"), value -> updateString(dialog, "after_action", value));
        addRow(container, panelState.row("After", afterAction, rowWidth, inspectorDescription("After")));
        addTextRow(container, "Columns", String.valueOf(intValue(dialog, "columns", 1)), value -> updateInt(dialog, "columns", value, 1, 8), rowWidth);
    }

    private void buildBodySection(Container container, int rowWidth) {
        JsonArray body = array("body");
        if (selection.kind().equals("body") && validIndex(body, selection.index())) {
            addRow(container, panelState.hint("Text", rowWidth));
            buildSelectedBody(container, rowWidth, objectAt(body, selection.index()));
        }
    }

    private void buildSelectedBody(Container container, int rowWidth, JsonObject block) {
        DropDownWidget<String> type = dropdown(BODY_TYPES, textOr(block, "type", "minecraft:plain_message"), value -> updateBodyType(block, value));
        addRow(container, panelState.row("Body Type", type, rowWidth, inspectorDescription("Body Type")));
        if ("minecraft:item".equals(text(block, "type"))) {
            addTextRow(container, "Item", textOr(block, "item", "minecraft:stone"), value -> updateString(block, "item", value), rowWidth);
            addTextRow(container, "Count", String.valueOf(intValue(block, "count", 1)), value -> updateInt(block, "count", value, 1, 64), rowWidth);
            addTextRow(container, "Description", text(block, "description"), value -> updateString(block, "description", value), rowWidth);
            addTextRow(container, "Width", String.valueOf(intValue(block, "width", 32)), value -> updateInt(block, "width", value, 1, 256), rowWidth);
            addTextRow(container, "Height", String.valueOf(intValue(block, "height", 32)), value -> updateInt(block, "height", value, 1, 256), rowWidth);
            ToggleWidget decorations = toggle(bool(block, "show_decorations", true), value -> updateBoolean(block, "show_decorations", value));
            addRow(container, panelState.row("Decorations", decorations, rowWidth, inspectorDescription("Decorations")));
            ToggleWidget tooltip = toggle(bool(block, "show_tooltip", true), value -> updateBoolean(block, "show_tooltip", value));
            addRow(container, panelState.row("Tooltip", tooltip, rowWidth, inspectorDescription("Tooltip")));
        } else {
            TextAreaWidget contents = new TextAreaWidget.Builder()
                .text(text(block, "contents"))
                .placeholder("Contents")
                .size(rowWidth, 64)
                .onChange(value -> updateString(block, "contents", value))
                .build();
            addRow(container, tallRow("Contents", contents, rowWidth, 82));
            addTextRow(container, "Width", String.valueOf(intValue(block, "width", 200)), value -> updateInt(block, "width", value, 1, 1024), rowWidth);
        }
        addRemoveRow(container, rowWidth, array("body"));
    }

    private void buildInputSection(Container container, int rowWidth) {
        JsonArray inputs = array("inputs");
        if (selection.kind().equals("input") && validIndex(inputs, selection.index())) {
            addRow(container, panelState.hint("Input", rowWidth));
            buildSelectedInput(container, rowWidth, objectAt(inputs, selection.index()));
        }
    }

    private void buildSelectedInput(Container container, int rowWidth, JsonObject input) {
        DropDownWidget<String> type = dropdown(INPUT_TYPES, textOr(input, "type", "minecraft:text"), value -> updateInputType(input, value));
        addRow(container, panelState.row("Input Type", type, rowWidth, inspectorDescription("Input Type")));
        addTextRow(container, "Key", text(input, "key"), value -> updateString(input, "key", value), rowWidth);
        addTextRow(container, "Label", text(input, "label"), value -> updateString(input, "label", value), rowWidth);
        addTextRow(container, "Width", String.valueOf(intValue(input, "width", 200)), value -> updateInt(input, "width", value, 1, 1024), rowWidth);
        String typeName = text(input, "type");
        if ("minecraft:boolean".equals(typeName)) {
            ToggleWidget initial = toggle(bool(input, "initial", false), value -> updateBoolean(input, "initial", value));
            addRow(container, panelState.row("Initial", initial, rowWidth, inspectorDescription("Initial")));
        } else if ("minecraft:number_range".equals(typeName)) {
            addTextRow(container, "Min", textOr(input, "min", "0"), value -> updateString(input, "min", value), rowWidth);
            addTextRow(container, "Max", textOr(input, "max", "100"), value -> updateString(input, "max", value), rowWidth);
            addTextRow(container, "Initial", textOr(input, "initial", "0"), value -> updateString(input, "initial", value), rowWidth);
            addTextRow(container, "Step", textOr(input, "step", "1"), value -> updateString(input, "step", value), rowWidth);
        } else if ("minecraft:single_option".equals(typeName)) {
            TextAreaWidget options = new TextAreaWidget.Builder()
                .text(joinStringArray(array(input, "options")))
                .placeholder("Options")
                .size(rowWidth, 58)
                .onChange(value -> updateStringArray(input, "options", value))
                .build();
            addRow(container, tallRow("Options", options, rowWidth, 76));
            addTextRow(container, "Initial", text(input, "initial"), value -> updateString(input, "initial", value), rowWidth);
        } else {
            addTextRow(container, "Initial", text(input, "initial"), value -> updateString(input, "initial", value), rowWidth);
            addTextRow(container, "Max Length", String.valueOf(intValue(input, "max_length", 128)), value -> updateInt(input, "max_length", value, 1, 1024), rowWidth);
            ToggleWidget multiline = toggle(bool(input, "multiline", false), value -> updateBoolean(input, "multiline", value));
            addRow(container, panelState.row("Multiline", multiline, rowWidth, inspectorDescription("Multiline")));
        }
        addRemoveRow(container, rowWidth, array("inputs"));
    }

    private void buildActionSection(Container container, int rowWidth) {
        JsonArray actions = array("actions");
        if (selection.kind().equals("action") && validIndex(actions, selection.index())) {
            addRow(container, panelState.hint("Button", rowWidth));
            buildSelectedAction(container, rowWidth, objectAt(actions, selection.index()));
        }
    }

    private void buildSelectedAction(Container container, int rowWidth, JsonObject action) {
        addTextRow(container, "Label", text(action, "label"), value -> updateString(action, "label", value), rowWidth);
        addTextRow(container, "Tooltip", text(action, "tooltip"), value -> updateString(action, "tooltip", value), rowWidth);
        addTextRow(container, "Width", String.valueOf(intValue(action, "width", 150)), value -> updateInt(action, "width", value, 1, 1024), rowWidth);
        actionBinding = new CompactBindingWidget.Builder(
            this,
            ACTION_MODES,
            () -> actionMode(action),
            value -> updateActionMode(action, value),
            () -> actionTargetOptions(action),
            () -> actionTarget(action),
            value -> updateActionTarget(action, value),
            () -> actionBindingInputs(action),
            () -> openActionTarget(action)
        )
            .createAction("Create New", () -> canCreateActionTarget(action), () -> createActionTarget(action))
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .build();
        addRow(container, panelState.row("Action", actionBinding, rowWidth, inspectorDescription("Action")));
        predicateBinding = new CompactBindingWidget.Builder(
            this,
            PREDICATE_MODES,
            () -> predicateMode(action),
            value -> updatePredicateMode(action, value),
            () -> predicateTargetOptions(action),
            () -> predicateTarget(action),
            value -> updatePredicateTarget(action, value),
            () -> functionBindingInputs(predicateCall(action), CompactBindingSupport.playerPredicateShape()),
            () -> openPredicateTarget(action)
        )
            .createAction("Create New", () -> "Function".equals(predicateMode(action)), () -> createPredicateTarget(action))
            .size(rowWidth, 18)
            .entranceAnimation(false)
            .build();
        addRow(container, panelState.row("Predicate", predicateBinding, rowWidth, inspectorDescription("Predicate")));
        addRemoveRow(container, rowWidth, array("actions"));
    }

    private void addTextRow(Container container, String label, String value, Consumer<String> onChange, int rowWidth) {
        addRow(container, panelState.row(label, input(label, value, onChange), rowWidth, inspectorDescription(label)));
    }

    private AnimatedWidget tallRow(String label, Widget widget, int rowWidth, int height) {
        ReSyncStudioPanelState.disableEntrance(widget);
        AnimatedWidget row = new TitledRowWidget.Builder()
            .title(label)
            .description(inspectorDescription(label))
            .size(rowWidth, height)
            .padding(4)
            .addWidget(widget)
            .build();
        ReSyncStudioPanelState.disableEntrance(row);
        return row;
    }

    private void addRemoveRow(Container container, int rowWidth, JsonArray array) {
        AnimatedButton remove = new AnimatedButton.Builder()
            .label("Remove")
            .size(rowWidth, 20)
            .accentType(ThemeManager.getAccent("danger"))
            .entranceAnimation(false)
            .onClick(() -> {
                int index = selection.index();
                if (!validIndex(array, index)) {
                    return;
                }
                snapshot();
                array.remove(index);
                selection = Selection.global();
                rebuildSelectionSection();
            })
            .build();
        addRow(container, remove);
    }

    private String inspectorDescription(String label) {
        return switch (label) {
            case "Name" -> "Project display name shown in Studio views.";
            case "Type" -> "Vanilla dialog layout type.";
            case "Title" -> "Main title shown at the top of the dialog.";
            case "Enabled" -> "Export state for this dialog resource.";
            case "Escape" -> "Allow players to close the dialog with Escape.";
            case "After" -> "Default behavior after an action is clicked.";
            case "Columns" -> "Button grid columns for multi action dialogs.";
            case "Body Type" -> "Selected body element kind.";
            case "Contents" -> "Visible message text. MiniMessage colors are supported.";
            case "Item" -> "Item id rendered in this dialog body.";
            case "Count" -> "Displayed item stack count.";
            case "Description" -> "Optional text rendered beside the item.";
            case "Width" -> "Element width used by the vanilla dialog layout.";
            case "Height" -> "Element height used by the vanilla dialog layout.";
            case "Decorations" -> "Show item count and decoration overlays.";
            case "Tooltip" -> "User-defined hover text for this element.";
            case "Input Type" -> "Selected input control kind.";
            case "Key" -> "Input key submitted with the dialog response.";
            case "Label" -> "Visible label text. MiniMessage colors are supported.";
            case "Initial" -> "Default value shown when the dialog opens.";
            case "Min" -> "Minimum value for number range inputs.";
            case "Max" -> "Maximum value for number range inputs.";
            case "Step" -> "Increment used by number range inputs.";
            case "Options" -> "Single option choices. Put one option per line.";
            case "Max Length" -> "Maximum typed text length.";
            case "Multiline" -> "Use a multiline text field.";
            case "Action" -> "Button click behavior handled by ReSync.";
            case "Predicate" -> "Optional condition checked before the action runs.";
            default -> "";
        };
    }

    private void moveArrayElement(JsonArray array, int index, int direction) {
        int target = index + direction;
        if (index < 0 || target < 0 || index >= array.size() || target >= array.size()) {
            return;
        }
        snapshot();
        JsonElement source = array.get(index);
        array.set(index, array.get(target));
        array.set(target, source);
        selection = new Selection(selection.kind(), target);
    }

    private void addRow(Container container, AnimatedWidget widget) {
        if (widget == null) {
            return;
        }
        ReSyncStudioPanelState.disableEntrance(widget);
        container.addWidget(widget);
        if (collectingSelectionWidgets) {
            selectionInspectorWidgets.add(widget);
        } else {
            inspectorWidgets.add(widget);
        }
    }

    private TextInputWidget input(String label, String value, Consumer<String> onChange) {
        TextInputWidget input = panelState.input(label, value, next -> {
            if (!syncing && onChange != null) {
                onChange.accept(next);
            }
        });
        ReSyncStudioPanelState.disableEntrance(input);
        return input;
    }

    private ToggleWidget toggle(boolean value, Consumer<Boolean> onChange) {
        ToggleWidget toggle = new ToggleWidget.Builder()
            .label("")
            .toggled(value)
            .size(panelState.rowWidth(), ReSyncStudioPanelState.FIELD_HEIGHT)
            .entranceAnimation(false)
            .onChange(next -> {
                if (!syncing && onChange != null) {
                    onChange.accept(next);
                }
            })
            .build();
        ReSyncStudioPanelState.disableEntrance(toggle);
        return toggle;
    }

    private DropDownWidget<String> dropdown(List<String> values, String selected, Consumer<String> onChange) {
        List<String> safeValues = values == null || values.isEmpty() ? List.of("") : values;
        String safeSelected = safeValues.contains(selected) ? selected : safeValues.getFirst();
        DropDownWidget<String> dropdown = new DropDownWidget.Builder<>(safeValues)
            .size(panelState.rowWidth(), ReSyncStudioPanelState.FIELD_HEIGHT)
            .selectedItem(safeSelected)
            .displayFunction(this::shortTypeName)
            .onSelectionChanged(value -> {
                if (!syncing && onChange != null) {
                    onChange.accept(value);
                }
            })
            .entranceAnimation(false)
            .build();
        ReSyncStudioPanelState.disableEntrance(dropdown);
        return dropdown;
    }

    private void select(Selection next) {
        Selection resolved = next != null ? next : Selection.global();
        if (selection.kind().equals(resolved.kind()) && selection.index() == resolved.index()) {
            return;
        }
        selection = resolved;
        rebuildSelectionSection();
    }

    private void addBody() {
        snapshot();
        JsonObject body = new JsonObject();
        body.addProperty("type", "minecraft:plain_message");
        body.addProperty("contents", "Message");
        body.addProperty("width", 200);
        array("body").add(body);
        select(new Selection("body", array("body").size() - 1));
    }

    private void addInput() {
        snapshot();
        JsonObject input = new JsonObject();
        input.addProperty("type", "minecraft:text");
        input.addProperty("key", "input_" + (array("inputs").size() + 1));
        input.addProperty("label", "Input");
        input.addProperty("width", 200);
        input.addProperty("initial", "");
        input.addProperty("max_length", 128);
        input.addProperty("multiline", false);
        array("inputs").add(input);
        select(new Selection("input", array("inputs").size() - 1));
    }

    private void addAction() {
        snapshot();
        JsonObject action = new JsonObject();
        action.addProperty("label", "Button");
        action.addProperty("width", 150);
        JsonObject resync = new JsonObject();
        resync.addProperty("actionMode", "None");
        resync.addProperty("predicateMode", "None");
        action.add("resync", resync);
        array("actions").add(action);
        select(new Selection("action", array("actions").size() - 1));
    }

    private boolean deleteSelection() {
        JsonArray selectedArray = switch (selection.kind()) {
            case "body" -> array("body");
            case "input" -> array("inputs");
            case "action" -> array("actions");
            default -> null;
        };
        if (selectedArray == null || !validIndex(selectedArray, selection.index())) {
            return false;
        }
        snapshot();
        selectedArray.remove(selection.index());
        selection = Selection.global();
        rebuildSelectionSection();
        return true;
    }

    private void updateDialogType(String value) {
        snapshot();
        dialog.addProperty("type", value);
        if ("minecraft:notice".equals(value) && array("actions").isEmpty()) {
            addDefaultAction("Ok");
        } else if ("minecraft:confirmation".equals(value) && array("actions").size() < 2) {
            while (array("actions").size() < 2) {
                addDefaultAction(array("actions").isEmpty() ? "Yes" : "No");
            }
        }
        rebuildSelectionSection();
    }

    private void addDefaultAction(String label) {
        JsonObject action = new JsonObject();
        action.addProperty("label", label);
        action.addProperty("width", 150);
        JsonObject resync = new JsonObject();
        resync.addProperty("actionMode", "None");
        resync.addProperty("predicateMode", "None");
        action.add("resync", resync);
        array("actions").add(action);
    }

    private void updateBodyType(JsonObject block, String value) {
        snapshot();
        block.keySet().clear();
        block.addProperty("type", value);
        if ("minecraft:item".equals(value)) {
            block.addProperty("item", "minecraft:stone");
            block.addProperty("count", 1);
            block.addProperty("width", 32);
            block.addProperty("height", 32);
            block.addProperty("show_decorations", true);
            block.addProperty("show_tooltip", true);
        } else {
            block.addProperty("contents", "Message");
            block.addProperty("width", 200);
        }
        rebuildSelectionSection();
    }

    private void updateInputType(JsonObject input, String value) {
        snapshot();
        String key = text(input, "key");
        String label = text(input, "label");
        int width = intValue(input, "width", 200);
        input.keySet().clear();
        input.addProperty("type", value);
        input.addProperty("key", key.isBlank() ? "input" : key);
        input.addProperty("label", label.isBlank() ? "Input" : label);
        input.addProperty("width", width);
        if ("minecraft:boolean".equals(value)) {
            input.addProperty("initial", false);
        } else if ("minecraft:number_range".equals(value)) {
            input.addProperty("min", "0");
            input.addProperty("max", "100");
            input.addProperty("initial", "0");
            input.addProperty("step", "1");
        } else if ("minecraft:single_option".equals(value)) {
            JsonArray options = new JsonArray();
            options.add("Option");
            input.add("options", options);
            input.addProperty("initial", "Option");
        } else {
            input.addProperty("initial", "");
            input.addProperty("max_length", 128);
            input.addProperty("multiline", false);
        }
        rebuildSelectionSection();
    }

    private void updateString(JsonObject object, String key, String value) {
        String current = text(object, key);
        String next = value != null ? value : "";
        if (current.equals(next)) {
            return;
        }
        snapshot();
        object.addProperty(key, next);
    }

    private void updateBoolean(JsonObject object, String key, boolean value) {
        if (bool(object, key, false) == value && object.has(key)) {
            return;
        }
        snapshot();
        object.addProperty(key, value);
    }

    private void updateInt(JsonObject object, String key, String value, int min, int max) {
        int next;
        try {
            next = Math.clamp(Integer.parseInt(value.trim()), min, max);
        } catch (Exception ignored) {
            return;
        }
        if (intValue(object, key, min) == next && object.has(key)) {
            return;
        }
        snapshot();
        object.addProperty(key, next);
    }

    private void updateStringArray(JsonObject object, String key, String value) {
        JsonArray next = new JsonArray();
        if (value != null) {
            for (String part : value.split("[\\r\\n]+")) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    next.add(trimmed);
                }
            }
        }
        snapshot();
        object.add(key, next);
    }

    private void save() {
        ensureDefaults();
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null) {
            manager.saveJsonResource(serverId, ReSyncResourceType.DIALOG, dialog);
        } else {
            new Notification("Dialog Saved", textOr(dialog, "displayName", text(dialog, "id")), Notification.Type.SUCCESS);
        }
    }

    private void snapshot() {
        if (!history.isRestoring()) {
            history.capture();
        }
    }

    private void restore(String json) {
        JsonObject restored = JsonParser.parseString(json).getAsJsonObject();
        dialog.keySet().clear();
        for (Map.Entry<String, JsonElement> entry : restored.entrySet()) {
            dialog.add(entry.getKey(), entry.getValue().deepCopy());
        }
        ensureDefaults();
        rebuildInspector();
        updateLayout(true);
    }

    private void ensureDefaults() {
        if (dialog.has("widgets") && dialog.get("widgets").isJsonArray()) {
            migrateCanvasDialog();
        }
        dialog.remove("mode");
        dialog.remove("canvas");
        dialog.remove("widgets");
        dialog.remove("external_title");
        dialog.remove("pause");
        ReSyncProtocolContract.dialogResource(dialog, "dialog").applyDefaults(ReSyncResourceType.DIALOG.defaultFolder());
    }

    private void migrateCanvasDialog() {
        JsonArray widgets = dialog.getAsJsonArray("widgets");
        JsonArray body = new JsonArray();
        JsonArray inputs = new JsonArray();
        JsonArray actions = new JsonArray();
        for (JsonElement element : widgets) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject widget = element.getAsJsonObject();
            String kind = text(widget, "kind");
            if ("title".equals(kind) && !text(widget, "text").isBlank()) {
                dialog.addProperty("title", text(widget, "text"));
            } else if ("text".equals(kind)) {
                JsonObject block = new JsonObject();
                block.addProperty("type", "minecraft:plain_message");
                block.addProperty("contents", textOr(widget, "text", "Text"));
                block.addProperty("width", intValue(widget, "width", 200));
                body.add(block);
            } else if ("text_input".equals(kind) || "checkbox".equals(kind) || "slider".equals(kind)) {
                JsonObject input = new JsonObject();
                input.addProperty("type", switch (kind) {
                    case "checkbox" -> "minecraft:boolean";
                    case "slider" -> "minecraft:number_range";
                    default -> "minecraft:text";
                });
                input.addProperty("key", textOr(widget, "key", textOr(widget, "id", "input")));
                input.addProperty("label", textOr(widget, "label", textOr(widget, "text", "Input")));
                input.addProperty("width", intValue(widget, "width", 200));
                input.addProperty("initial", textOr(widget, "value", text(widget, "text")));
                inputs.add(input);
            } else if ("button".equals(kind)) {
                JsonObject action = new JsonObject();
                action.addProperty("label", textOr(widget, "text", "Button"));
                action.addProperty("width", intValue(widget, "width", 150));
                if (widget.has("resync") && widget.get("resync").isJsonObject()) {
                    action.add("resync", widget.getAsJsonObject("resync").deepCopy());
                }
                actions.add(action);
            }
        }
        if (!body.isEmpty()) {
            dialog.add("body", body);
        }
        if (!inputs.isEmpty()) {
            dialog.add("inputs", inputs);
        }
        if (!actions.isEmpty()) {
            dialog.add("actions", actions);
        }
    }

    private void drawPreview(IDrawContext context, int mouseX, int mouseY) {
        previewElements.clear();
        previewOrderControls.clear();
        MinecraftGameAssets gameAssets = getGameAssets();
        int screenWidth = Math.max(1, previewWidth);
        int screenHeight = Math.max(1, previewHeight);
        int bodyWidth = previewBodyWidth(screenWidth);
        int bodyHeight = previewBodyHeight(bodyWidth);
        int bodyX = previewX + (screenWidth - bodyWidth) / 2;
        int bodyY = previewY + Math.min(HEADER_HEIGHT + CONTENT_MARGIN_TOP, Math.max(HEADER_HEIGHT, screenHeight - FOOTER_HEIGHT - bodyHeight));
        String title = textOr(dialog, "title", textOr(dialog, "displayName", text(dialog, "id")));
        drawTitle(context, gameAssets, title, previewX, previewY, screenWidth);
        int y = bodyY;
        List<JsonObject> bodies = objectArray("body");
        for (int i = 0; i < bodies.size(); i++) {
            y = drawBody(context, gameAssets, bodies.get(i), i, bodyX, y, bodyWidth) + BODY_SPACING;
        }
        List<JsonObject> inputs = objectArray("inputs");
        for (int i = 0; i < inputs.size(); i++) {
            y = drawInput(context, gameAssets, inputs.get(i), i, bodyX, y, bodyWidth, mouseX, mouseY) + BODY_SPACING;
        }
        drawActions(context, gameAssets, bodyX, y, bodyWidth, mouseX, mouseY);
        drawPreviewSelection(context, mouseX, mouseY);
    }

    private void drawTitle(IDrawContext context, MinecraftGameAssets gameAssets, String title, int screenX, int screenY, int screenWidth) {
        int titleWidth = richTextWidth(title);
        int rowWidth = titleWidth + WARNING_TITLE_SPACING + WARNING_BUTTON_SIZE;
        int rowX = screenX + (screenWidth - rowWidth) / 2;
        int rowY = screenY + (HEADER_HEIGHT - WARNING_BUTTON_SIZE) / 2;
        context.drawRichText(title, rowX, rowY + (WARNING_BUTTON_SIZE - TEXT_LINE_HEIGHT) / 2, 0xFFFFFFFF, true);
        int warningX = rowX + titleWidth + WARNING_TITLE_SPACING;
        int warningY = rowY;
        if (warningX < screenX || warningY < screenY || warningX > screenX + screenWidth - WARNING_BUTTON_SIZE || warningY > screenY + previewHeight - WARNING_BUTTON_SIZE) {
            warningX = screenX + Math.max(0, screenWidth - WARNING_BUTTON_SIZE * 2);
            warningY = screenY + Math.min(5, Math.max(0, previewHeight - WARNING_BUTTON_SIZE));
        }
        int selectionX = Math.min(rowX, warningX);
        int selectionWidth = Math.max(rowX + titleWidth, warningX + WARNING_BUTTON_SIZE) - selectionX;
        addPreviewElement("title", -1, selectionX, rowY, selectionWidth, WARNING_BUTTON_SIZE, emptyTooltip());
        drawWarningButton(context, gameAssets, warningX, warningY);
    }

    private void drawWarningButton(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y) {
        if (!drawSprite(context, gameAssets, "dialog/warning_button", x, y, WARNING_BUTTON_SIZE, WARNING_BUTTON_SIZE)) {
            context.fill(x, y, x + WARNING_BUTTON_SIZE, y + WARNING_BUTTON_SIZE, 0xFF404040);
            context.fillBorder(x, y, x + WARNING_BUTTON_SIZE, y + WARNING_BUTTON_SIZE, 1, 0xFF000000);
            context.drawText("!", x + 8, y + 6, 0xFFFFFF55, true);
        }
    }

    private int drawBody(IDrawContext context, MinecraftGameAssets gameAssets, JsonObject body, int index, int x, int y, int contentWidth) {
        int startY = y;
        if ("minecraft:item".equals(text(body, "type"))) {
            int itemSize = Math.clamp(intValue(body, "width", 32), 16, 64);
            MinecraftRenderItem item = MinecraftGameItems.fromVisual(textOr(body, "item", "STONE"), Math.max(1, intValue(body, "count", 1)), "", List.of(), null);
            int descriptionWidth = richTextBlockWidth(text(body, "description"), Math.max(40, contentWidth - itemSize - GRID_SPACING));
            int rowWidth = itemSize + (descriptionWidth > 0 ? GRID_SPACING + descriptionWidth : 0);
            int rowX = x + (contentWidth - rowWidth) / 2;
            context.drawItem(item, rowX, y, 0);
            String description = text(body, "description");
            if (!description.isBlank()) {
                int textX = rowX + itemSize + GRID_SPACING;
                int lineY = y + 2;
                for (String line : wrapRichText(description, Math.max(40, contentWidth - itemSize - GRID_SPACING))) {
                    context.drawRichText(line, textX, lineY, 0xFFFFFFFF, false);
                    lineY += TEXT_LINE_HEIGHT;
                }
            }
            int endY = y + Math.max(itemSize, TEXT_LINE_HEIGHT);
            addPreviewElement("body", index, rowX, startY, rowWidth, endY - startY, userTooltip(body));
            return endY;
        }
        int width = Math.min(contentWidth, Math.max(1, intValue(body, "width", contentWidth)));
        int textX = x + (contentWidth - width) / 2;
        int lineY = y;
        for (String line : wrapRichText(textOr(body, "contents", "Message"), width)) {
            drawCenteredRichText(context, line, textX, lineY, width, 0xFFFFFFFF, false);
            lineY += TEXT_LINE_HEIGHT;
        }
        addPreviewElement("body", index, textX, startY, width, lineY - startY, userTooltip(body));
        return lineY;
    }

    private int drawInput(IDrawContext context, MinecraftGameAssets gameAssets, JsonObject input, int index, int x, int y, int contentWidth, int mouseX, int mouseY) {
        int startY = y;
        String label = textOr(input, "label", textOr(input, "key", "Input"));
        int width = Math.min(contentWidth, Math.max(1, intValue(input, "width", contentWidth)));
        String type = text(input, "type");
        if ("minecraft:boolean".equals(type)) {
            int rowWidth = Math.min(contentWidth, CHECKBOX_SIZE + LABEL_SPACING + richTextWidth(label));
            int rowX = x + (contentWidth - rowWidth) / 2;
            drawCheckbox(context, gameAssets, rowX, y, CHECKBOX_SIZE, bool(input, "initial", false));
            context.drawRichText(label, rowX + CHECKBOX_SIZE + LABEL_SPACING, y + (CHECKBOX_SIZE - TEXT_LINE_HEIGHT) / 2, 0xFFE0E0E0, false);
            addPreviewElement("input", index, rowX, startY, rowWidth, CHECKBOX_SIZE, userTooltip(input));
            return y + CHECKBOX_SIZE;
        }
        drawCenteredRichText(context, label, x, y, contentWidth, 0xFFFFFFFF, false);
        y += TEXT_LINE_HEIGHT + LABEL_SPACING;
        int controlX = x + (contentWidth - width) / 2;
        if ("minecraft:number_range".equals(type)) {
            drawSlider(context, gameAssets, controlX, y, width, FIELD_HEIGHT, sliderPercent(input), numberRangeText(input), mouseX >= controlX && mouseX <= controlX + width && mouseY >= y && mouseY <= y + FIELD_HEIGHT);
            addPreviewElement("input", index, controlX, startY, width, y + FIELD_HEIGHT - startY, userTooltip(input));
            return y + FIELD_HEIGHT;
        }
        if ("minecraft:single_option".equals(type)) {
            drawButton(context, gameAssets, controlX, y, width, FIELD_HEIGHT, textOr(input, "initial", firstArrayValue(input, "options")), mouseX >= controlX && mouseX <= controlX + width && mouseY >= y && mouseY <= y + FIELD_HEIGHT);
            addPreviewElement("input", index, controlX, startY, width, y + FIELD_HEIGHT - startY, userTooltip(input));
            return y + FIELD_HEIGHT;
        }
        int height = bool(input, "multiline", false) ? Math.max(40, intValue(input, "height", 60)) : FIELD_HEIGHT;
        drawTextField(context, gameAssets, controlX, y, width, height, text(input, "initial"));
        addPreviewElement("input", index, controlX, startY, width, y + height - startY, userTooltip(input));
        return y + height;
    }

    private void drawActions(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int contentWidth, int mouseX, int mouseY) {
        JsonArray actions = array("actions");
        if (actions.isEmpty()) {
            JsonObject close = new JsonObject();
            close.addProperty("label", "Close");
            close.addProperty("width", 150);
            actions = new JsonArray();
            actions.add(close);
        }
        int columns = actionColumns(actions.size());
        for (int i = 0; i < actions.size(); i++) {
            int row = i / columns;
            JsonObject action = objectAt(actions, i);
            int buttonWidth = actionButtonWidth(action);
            int rowWidth = actionRowWidth(actions, row, columns);
            int bx = x + (contentWidth - rowWidth) / 2;
            for (int c = row * columns; c < i; c++) {
                bx += actionButtonWidth(objectAt(actions, c)) + GRID_SPACING;
            }
            int by = y + row * (BUTTON_HEIGHT + GRID_SPACING);
            boolean hovered = mouseX >= bx && mouseX <= bx + buttonWidth && mouseY >= by && mouseY <= by + BUTTON_HEIGHT;
            drawButton(context, gameAssets, bx, by, buttonWidth, BUTTON_HEIGHT, textOr(action, "label", "Button"), hovered || selection.kind().equals("action") && selection.index() == i);
            if (i < array("actions").size()) {
                addPreviewElement("action", i, bx, by, buttonWidth, BUTTON_HEIGHT, userTooltip(action));
            }
        }
    }

    private void addPreviewElement(String kind, int index, int x, int y, int width, int height, MinecraftTooltip tooltip) {
        previewElements.add(new PreviewElementRect(kind, index, x, y, width, height, tooltip));
    }

    private void drawPreviewSelection(IDrawContext context, int mouseX, int mouseY) {
        PreviewElementRect selected = selectedPreviewElement();
        if (selected == null) {
            return;
        }
        if (!"action".equals(selection.kind())) {
            context.fillBorder(selected.x() - 3, selected.y() - 3, selected.x() + selected.width() + 3, selected.y() + selected.height() + 3, 1, 0xFFFFFFFF);
        }
        if (!hasOrder(selection.kind())) {
            return;
        }
        int controlX = selected.x() + selected.width() + 8;
        int controlY = selected.y() + Math.max(0, (selected.height() - BUTTON_HEIGHT * 2 - GRID_SPACING) / 2);
        drawOrderControl(context, mouseX, mouseY, selection.kind(), selection.index(), -1, controlX, controlY, true);
        drawOrderControl(context, mouseX, mouseY, selection.kind(), selection.index(), 1, controlX, controlY + BUTTON_HEIGHT + GRID_SPACING, false);
    }

    private void drawOrderControl(IDrawContext context, int mouseX, int mouseY, String kind, int index, int direction, int x, int y, boolean up) {
        boolean enabled = canMoveElement(kind, index, direction);
        IconButton button = new IconButton.Builder()
            .imagePath(up ? "up" : "down")
            .size(18, 18).pos(x, y)
            .entranceAnimation(false).active(enabled)
            .build();
        button.render(context, mouseX, mouseY, 0f);
        if (enabled) {
            previewOrderControls.add(new PreviewOrderRect(kind, index, direction, x, y, 30, BUTTON_HEIGHT));
        }
    }

    private PreviewElementRect selectedPreviewElement() {
        for (PreviewElementRect rect : previewElements) {
            if (rect.kind().equals(selection.kind()) && rect.index() == selection.index()) {
                return rect;
            }
        }
        return null;
    }

    private PreviewElementRect hoveredPreviewElement(int mouseX, int mouseY) {
        for (int i = previewElements.size() - 1; i >= 0; i--) {
            PreviewElementRect rect = previewElements.get(i);
            if (rect.contains(mouseX, mouseY)) {
                return rect;
            }
        }
        return null;
    }

    private void drawPreviewTooltip(IDrawContext context, int mouseX, int mouseY) {
        PreviewElementRect hovered = hoveredPreviewElement(mouseX, mouseY);
        if (hovered == null || hovered.tooltip() == null || hovered.tooltip().isEmpty()) {
            return;
        }
        context.drawMinecraftTooltip(hovered.tooltip(), mouseX, mouseY, getWidth(), getHeight());
    }

    private MinecraftTooltip userTooltip(JsonObject object) {
        String tooltip = text(object, "tooltip");
        if (tooltip.isBlank()) {
            return emptyTooltip();
        }
        return new MinecraftTooltip(List.of(new MinecraftTooltipLine(MinecraftTextComponents.fromValue(tooltip))));
    }

    private MinecraftTooltip emptyTooltip() {
        return new MinecraftTooltip(List.of());
    }

    private boolean hasOrder(String kind) {
        return "body".equals(kind) || "input".equals(kind) || "action".equals(kind);
    }

    private boolean canMoveElement(String kind, int index, int direction) {
        JsonArray array = arrayForKind(kind);
        int target = index + direction;
        return array != null && index >= 0 && index < array.size() && target >= 0 && target < array.size();
    }

    private void moveSelectedElement(String kind, int index, int direction) {
        JsonArray array = arrayForKind(kind);
        if (array == null) {
            return;
        }
        moveArrayElement(array, index, direction);
    }

    private JsonArray arrayForKind(String kind) {
        return switch (kind) {
            case "body" -> array("body");
            case "input" -> array("inputs");
            case "action" -> array("actions");
            default -> null;
        };
    }

    private int bodyHeight(JsonObject body, int contentWidth) {
        if ("minecraft:item".equals(text(body, "type"))) {
            return Math.max(16, intValue(body, "height", 32));
        }
        return Math.max(TEXT_LINE_HEIGHT, wrapRichText(textOr(body, "contents", "Message"), Math.min(contentWidth, intValue(body, "width", contentWidth))).size() * TEXT_LINE_HEIGHT);
    }

    private int inputHeight(JsonObject input) {
        if ("minecraft:boolean".equals(text(input, "type"))) {
            return CHECKBOX_SIZE;
        }
        return TEXT_LINE_HEIGHT + LABEL_SPACING + (bool(input, "multiline", false) ? Math.max(40, intValue(input, "height", 60)) : FIELD_HEIGHT);
    }

    private int actionGridHeight() {
        int count = Math.max(1, array("actions").size());
        int columns = actionColumns(count);
        int rows = (int) Math.ceil(count / (double) columns);
        return rows * BUTTON_HEIGHT + Math.max(0, rows - 1) * GRID_SPACING;
    }

    private int previewBodyWidth(int screenWidth) {
        int maxWidth = Math.min(DIALOG_WIDTH, Math.max(1, screenWidth - 40));
        int width = 0;
        for (JsonObject body : objectArray("body")) {
            width = Math.max(width, bodyWidth(body, maxWidth));
        }
        for (JsonObject input : objectArray("inputs")) {
            width = Math.max(width, inputWidth(input, maxWidth));
        }
        width = Math.max(width, actionGridWidth());
        return Math.clamp(width, 1, maxWidth);
    }

    private int previewBodyHeight(int contentWidth) {
        int total = 0;
        int count = 0;
        for (JsonObject body : objectArray("body")) {
            total += bodyHeight(body, contentWidth);
            count++;
        }
        for (JsonObject input : objectArray("inputs")) {
            total += inputHeight(input);
            count++;
        }
        total += actionGridHeight();
        count++;
        return total + Math.max(0, count - 1) * BODY_SPACING;
    }

    private int bodyWidth(JsonObject body, int contentWidth) {
        if ("minecraft:item".equals(text(body, "type"))) {
            int itemSize = Math.clamp(intValue(body, "width", 32), 16, 64);
            String description = text(body, "description");
            if (description.isBlank()) {
                return itemSize;
            }
            return Math.min(contentWidth, itemSize + GRID_SPACING + richTextBlockWidth(description, Math.max(40, contentWidth - itemSize - GRID_SPACING)));
        }
        return Math.min(contentWidth, Math.max(1, intValue(body, "width", contentWidth)));
    }

    private int inputWidth(JsonObject input, int contentWidth) {
        String label = textOr(input, "label", textOr(input, "key", "Input"));
        if ("minecraft:boolean".equals(text(input, "type"))) {
            return Math.min(contentWidth, CHECKBOX_SIZE + LABEL_SPACING + richTextWidth(label));
        }
        int width = Math.min(contentWidth, Math.max(1, intValue(input, "width", contentWidth)));
        return Math.max(width, Math.min(contentWidth, richTextWidth(label)));
    }

    private int actionGridWidth() {
        JsonArray actions = array("actions");
        int count = Math.max(1, actions.size());
        int columns = actionColumns(count);
        int width = 0;
        int rows = (int) Math.ceil(count / (double) columns);
        for (int row = 0; row < rows; row++) {
            width = Math.max(width, actionRowWidth(actions, row, columns));
        }
        return width;
    }

    private int actionRowWidth(JsonArray actions, int row, int columns) {
        int count = actions.isEmpty() ? 1 : actions.size();
        int start = row * columns;
        int end = Math.min(count, start + columns);
        int width = 0;
        for (int i = start; i < end; i++) {
            JsonObject action = actions.isEmpty() ? defaultCloseAction() : objectAt(actions, i);
            width += actionButtonWidth(action);
            if (i > start) {
                width += GRID_SPACING;
            }
        }
        return width;
    }

    private int actionColumns(int count) {
        int columns = Math.max(1, intValue(dialog, "columns", 1));
        if ("minecraft:notice".equals(dialogType())) {
            return 1;
        }
        if ("minecraft:confirmation".equals(dialogType())) {
            return Math.min(2, Math.max(1, count));
        }
        return columns;
    }

    private int actionButtonWidth(JsonObject action) {
        return Math.clamp(intValue(action, "width", 150), 1, DIALOG_WIDTH);
    }

    private JsonObject defaultCloseAction() {
        JsonObject close = new JsonObject();
        close.addProperty("label", "Close");
        close.addProperty("width", 150);
        return close;
    }

    private void drawButton(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int width, int height, String label, boolean hovered) {
        String sprite = hovered ? "widget/button_highlighted" : "widget/button";
        if (!drawSprite(context, gameAssets, sprite, x, y, width, height)) {
            context.fill(x, y, x + width, y + height, hovered ? 0xFF7F7F7F : 0xFF606060);
            context.fillBorder(x, y, x + width, y + height, 1, 0xFF000000);
        }
        context.enableScissor(x + 4, y, x + width - 4, y + height);
        drawCenteredRichText(context, label, x, y + (height - 9) / 2 + 1, width, 0xFFFFFFFF, true);
        context.disableScissor();
    }

    private void drawTextField(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int width, int height, String value) {
        if (!drawSprite(context, gameAssets, "widget/text_field", x, y, width, height)) {
            context.fill(x, y, x + width, y + height, 0xFF000000);
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF303030);
        }
        if (value != null && !value.isBlank()) {
            context.enableScissor(x + 4, y + 2, x + width - 4, y + height - 2);
            context.drawRichText(value, x + 4, y + Math.max(2, (height - 9) / 2 + 1), 0xFFFFFFFF, false);
            context.disableScissor();
        }
    }

    private void drawCheckbox(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int size, boolean checked) {
        String sprite = checked ? "widget/checkbox_selected" : "widget/checkbox";
        if (!drawSprite(context, gameAssets, sprite, x, y, size, size)) {
            context.fill(x, y, x + size, y + size, 0xFF000000);
            context.fill(x + 1, y + 1, x + size - 1, y + size - 1, checked ? 0xFF55AA55 : 0xFF303030);
            if (checked) {
                context.drawText("x", x + 5, y + 4, 0xFFFFFFFF, false);
            }
        }
    }

    private void drawSlider(IDrawContext context, MinecraftGameAssets gameAssets, int x, int y, int width, int height, float percent, String label, boolean hovered) {
        if (!drawSprite(context, gameAssets, hovered ? "widget/slider_highlighted" : "widget/slider", x, y, width, height)) {
            context.fill(x, y + height / 2 - 1, x + width, y + height / 2 + 1, 0xFF808080);
        }
        int knobWidth = 8;
        int knobX = x + Math.round((width - knobWidth) * Math.clamp(percent, 0f, 1f));
        if (!drawSprite(context, gameAssets, hovered ? "widget/slider_handle_highlighted" : "widget/slider_handle", knobX, y, knobWidth, height)) {
            drawButton(context, gameAssets, knobX, y, knobWidth, height, "", hovered);
        }
        context.enableScissor(x + 4, y, x + width - 4, y + height);
        drawCenteredRichText(context, label, x, y + (height - TEXT_LINE_HEIGHT) / 2 + 1, width, 0xFFFFFFFF, true);
        context.disableScissor();
    }

    private boolean drawSprite(IDrawContext context, MinecraftGameAssets gameAssets, String sprite, int x, int y, int width, int height) {
        MinecraftAssetReference reference = gameAssets.asset("minecraft", "textures/gui/sprites/" + sprite + ".png");
        BufferedImage image = gameAssets.getImage(reference);
        int sourceWidth = image != null && image != ResourceManager.getInstance().getMissingTexture() ? image.getWidth() : width;
        int sourceHeight = image != null && image != ResourceManager.getInstance().getMissingTexture() ? image.getHeight() : height;
        return drawAssetRegion(context, gameAssets, reference, x, y, width, height, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
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

    private MinecraftGameAssets getGameAssets() {
        if (RemotelyClient.INSTANCE != null && RemotelyClient.INSTANCE.getHost() != null) {
            MinecraftGameAssets gameAssets = RemotelyClient.INSTANCE.getHost().getGameAssets();
            if (gameAssets != null) {
                return gameAssets;
            }
        }
        return MinecraftGameAssets.EMPTY;
    }

    private void drawCenteredRichText(IDrawContext context, String text, int x, int y, int width, int color, boolean shadow) {
        context.drawRichText(text, x + (width - richTextWidth(text)) / 2, y, color, shadow);
    }

    private int richTextWidth(String text) {
        return TextRenderer.getWidth(plainPreviewText(text));
    }

    private String plainPreviewText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder plain = new StringBuilder();
        boolean inTag = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inTag) {
                if (c == '>') {
                    inTag = false;
                }
                continue;
            }
            if (c == '<') {
                inTag = true;
                continue;
            }
            if ((c == '&' || c == '§') && i + 1 < text.length() && isLegacyFormatCode(text.charAt(i + 1))) {
                i++;
                continue;
            }
            plain.append(c);
        }
        return plain.toString();
    }

    private boolean isLegacyFormatCode(char value) {
        char code = Character.toLowerCase(value);
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f' || code >= 'k' && code <= 'o' || code == 'r' || code == 'x';
    }

    private List<String> wrapRichText(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.replace('\r', '\n').split("\\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String next = current.isEmpty() ? word : current + " " + word;
                if (richTextWidth(next) <= maxWidth) {
                    current.setLength(0);
                    current.append(next);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                    }
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    private int richTextBlockWidth(String text, int maxWidth) {
        int width = 0;
        for (String line : wrapRichText(text, maxWidth)) {
            width = Math.max(width, richTextWidth(line));
        }
        return Math.min(maxWidth, width);
    }

    private float sliderPercent(JsonObject input) {
        try {
            float min = Float.parseFloat(textOr(input, "min", "0"));
            float max = Float.parseFloat(textOr(input, "max", "100"));
            float value = Float.parseFloat(textOr(input, "initial", "0"));
            if (max <= min) {
                return 0f;
            }
            return Math.clamp((value - min) / (max - min), 0f, 1f);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private String numberRangeText(JsonObject input) {
        String label = textOr(input, "label", textOr(input, "key", "Value"));
        String value = textOr(input, "initial", textOr(input, "min", "0"));
        if (label.isBlank()) {
            return value;
        }
        return label + ": " + value;
    }

    private String actionMode(JsonObject action) {
        JsonObject resync = resync(action);
        String mode = text(resync, "actionMode");
        return ACTION_MODES.contains(mode) ? mode : "None";
    }

    private void updateActionMode(JsonObject action, String mode) {
        snapshot();
        JsonObject resync = resync(action);
        resync.addProperty("actionMode", mode == null ? "None" : mode);
        resync.remove("flowId");
        resync.remove("action");
        resync.remove("commands");
        resync.remove("dialogId");
        resync.remove("customEventId");
        if ("Run Command".equals(mode)) {
            resync.add("commands", new JsonArray());
        } else if ("Run Function".equals(mode)) {
            JsonObject call = new JsonObject();
            call.addProperty("type", "functionRef");
            call.addProperty("functionId", "");
            resync.add("action", call);
        } else if ("Run Flow".equals(mode)) {
            resync.addProperty("flowId", "");
        } else if ("Open Dialog".equals(mode)) {
            resync.addProperty("dialogId", "");
        } else if ("Custom Event".equals(mode)) {
            resync.addProperty("customEventId", "");
        }
        refreshBindings();
    }

    private List<String> actionTargetOptions(JsonObject action) {
        return switch (actionMode(action)) {
            case "Run Flow" -> flowOptions();
            case "Run Function" -> functionOptions();
            case "Open Dialog" -> dialogOptions();
            default -> List.of("none");
        };
    }

    private String actionTarget(JsonObject action) {
        JsonObject resync = resync(action);
        return switch (actionMode(action)) {
            case "Run Flow" -> text(resync, "flowId");
            case "Run Function" -> text(optionalObject(resync, "action"), "functionId");
            case "Run Command" -> "Command";
            case "Open Dialog" -> text(resync, "dialogId");
            case "Custom Event" -> "Event";
            default -> "";
        };
    }

    private void updateActionTarget(JsonObject action, String value) {
        snapshot();
        JsonObject resync = resync(action);
        String mode = actionMode(action);
        if ("Run Flow".equals(mode)) {
            resync.addProperty("flowId", realValue(value));
        } else if ("Run Function".equals(mode)) {
            functionCall(resync, "action").addProperty("functionId", realValue(value));
        } else if ("Open Dialog".equals(mode)) {
            resync.addProperty("dialogId", realValue(value));
        }
        refreshBindings();
    }

    private List<CompactBindingWidget.BindingInput> actionBindingInputs(JsonObject action) {
        JsonObject resync = resync(action);
        if ("Run Command".equals(actionMode(action))) {
            return List.of(new CompactBindingWidget.BindingInput(
                "commands",
                "Commands",
                joinStringArray(array(resync, "commands")),
                FlowDataType.STRING.getColor(),
                null,
                value -> updateStringArray(resync, "commands", value),
                CompactBindingWidget.InputKind.COMMAND
            ));
        }
        if ("Custom Event".equals(actionMode(action))) {
            return List.of(new CompactBindingWidget.BindingInput(
                "event",
                "Event",
                text(resync, "customEventId"),
                FlowDataType.STRING.getColor(),
                null,
                value -> updateString(resync, "customEventId", value),
                CompactBindingWidget.InputKind.TEXT
            ));
        }
        return functionBindingInputs(optionalObject(resync, "action"), CompactBindingSupport.playerActionShape());
    }

    private boolean canCreateActionTarget(JsonObject action) {
        String mode = actionMode(action);
        return "Run Flow".equals(mode) || "Run Function".equals(mode) || "Open Dialog".equals(mode);
    }

    private void createActionTarget(JsonObject action) {
        String mode = actionMode(action);
        if ("Run Flow".equals(mode)) {
            createResource(ReSyncResourceDragPayload.FLOW, id -> {
                updateActionTarget(action, id);
                openFlowGraph(id);
            });
        } else if ("Run Function".equals(mode)) {
            createResource(ReSyncResourceDragPayload.FUNCTION, id -> {
                normalizeBindingFunction(id, CompactBindingSupport.playerActionShape());
                updateActionTarget(action, id);
                openFlowGraph(id);
            });
        } else if ("Open Dialog".equals(mode)) {
            createResource(ReSyncResourceDragPayload.DIALOG, id -> {
                updateActionTarget(action, id);
                openDialog(id);
            });
        }
    }

    private void openActionTarget(JsonObject action) {
        String mode = actionMode(action);
        if ("Run Flow".equals(mode)) {
            openFlowGraph(text(resync(action), "flowId"));
        } else if ("Run Function".equals(mode)) {
            openFlowGraph(text(optionalObject(resync(action), "action"), "functionId"));
        } else if ("Open Dialog".equals(mode)) {
            openDialog(text(resync(action), "dialogId"));
        }
    }

    private String predicateMode(JsonObject action) {
        JsonObject resync = resync(action);
        String mode = text(resync, "predicateMode");
        return PREDICATE_MODES.contains(mode) ? mode : "None";
    }

    private void updatePredicateMode(JsonObject action, String mode) {
        snapshot();
        JsonObject resync = resync(action);
        resync.addProperty("predicateMode", mode == null ? "None" : mode);
        resync.remove("predicateFlowId");
        resync.remove("predicate");
        if ("Function".equals(mode)) {
            JsonObject predicate = new JsonObject();
            predicate.addProperty("type", "functionRef");
            predicate.addProperty("functionId", "");
            resync.add("predicate", predicate);
        }
        refreshBindings();
    }

    private List<String> predicateTargetOptions(JsonObject action) {
        return switch (predicateMode(action)) {
            case "Function" -> functionOptions();
            default -> List.of("none");
        };
    }

    private String predicateTarget(JsonObject action) {
        JsonObject resync = resync(action);
        return "Function".equals(predicateMode(action)) ? text(optionalObject(resync, "predicate"), "functionId") : "";
    }

    private void updatePredicateTarget(JsonObject action, String value) {
        snapshot();
        JsonObject resync = resync(action);
        if ("Function".equals(predicateMode(action))) {
            functionCall(resync, "predicate").addProperty("functionId", realValue(value));
        }
        refreshBindings();
    }

    private JsonObject predicateCall(JsonObject action) {
        return optionalObject(resync(action), "predicate");
    }

    private void createPredicateTarget(JsonObject action) {
        if ("Function".equals(predicateMode(action))) {
            createResource(ReSyncResourceDragPayload.FUNCTION, id -> {
                normalizeBindingFunction(id, CompactBindingSupport.playerPredicateShape());
                updatePredicateTarget(action, id);
                openFlowGraph(id);
            });
        }
    }

    private void openPredicateTarget(JsonObject action) {
        if ("Function".equals(predicateMode(action))) {
            openFlowGraph(text(optionalObject(resync(action), "predicate"), "functionId"));
        }
    }

    private List<CompactBindingWidget.BindingInput> functionBindingInputs(JsonObject call, CompactBindingSupport.FunctionShape shape) {
        FlowGraph function = selectedFunction(text(call, "functionId"), shape);
        if (function == null || function.getFunctionInputs() == null || function.getFunctionInputs().isEmpty()) {
            return List.of();
        }
        List<CompactBindingWidget.BindingInput> inputs = new ArrayList<>();
        for (FlowGraph.FunctionParameter input : function.getFunctionInputs()) {
            if (input == null || input.getName() == null || input.getName().isBlank()) {
                continue;
            }
            String name = input.getName();
            inputs.add(new CompactBindingWidget.BindingInput(
                name,
                ItemOptionCatalog.formatOptionLabel(name),
                functionInputValue(call, input),
                input.getType() != null ? input.getType().getColor() : FlowDataType.ANY.getColor(),
                () -> functionInputOptions(input),
                value -> updateFunctionInput(call, input, value),
                input.getType() != null && FlowDataType.BOOLEAN.isAssignableFrom(input.getType()) ? CompactBindingWidget.InputKind.BOOLEAN : CompactBindingWidget.InputKind.TEXT
            ));
        }
        return inputs;
    }

    private FlowGraph selectedFunction(String id, CompactBindingSupport.FunctionShape shape) {
        return CompactBindingSupport.selectedFunction(serverId, id, shape);
    }

    private void normalizeBindingFunction(String functionId, CompactBindingSupport.FunctionShape shape) {
        FlowManager manager = FlowManager.getInstance();
        FlowGraph function = manager != null && serverId != null ? manager.getFlowsForServer(serverId).get(functionId) : null;
        CompactBindingSupport.normalizeFunction(serverId, function, shape);
    }

    private String functionInputValue(JsonObject call, FlowGraph.FunctionParameter input) {
        if (call == null || input == null || input.getName() == null || input.getName().isBlank()) {
            return "";
        }
        JsonObject inputs = optionalObject(call, "inputs");
        String name = input.getName();
        if (inputs != null && inputs.has(name) && !inputs.get(name).isJsonNull()) {
            return inputs.get(name).getAsString();
        }
        if (input.getDefaultValue() != null && !input.getDefaultValue().isBlank()) {
            return input.getDefaultValue();
        }
        return functionInputContextDefault(input);
    }

    private List<String> functionInputOptions(FlowGraph.FunctionParameter input) {
        return CompactBindingSupport.functionInputOptions(input, "dialog");
    }

    private String functionInputContextDefault(FlowGraph.FunctionParameter input) {
        return CompactBindingSupport.functionInputContextDefault(input, "dialog");
    }

    private void updateFunctionInput(JsonObject call, FlowGraph.FunctionParameter input, String value) {
        if (call == null || input == null || input.getName() == null || input.getName().isBlank()) {
            return;
        }
        snapshot();
        JsonObject inputs = object(call, "inputs");
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value) || "No Options".equals(value) || "Loading".equals(value)) {
            inputs.remove(input.getName());
            removeIfEmpty(call, "inputs");
            return;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            inputs.addProperty(input.getName(), Boolean.parseBoolean(trimmed));
            return;
        }
        try {
            inputs.addProperty(input.getName(), Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            inputs.addProperty(input.getName(), trimmed);
        }
    }

    private void createResource(String type, Consumer<String> onCreated) {
        ReSyncResourceCreator.showCreatePopup(this, serverId, type, "", null, result -> {
            if (result != null && onCreated != null) {
                onCreated.accept(result.id());
            }
        });
    }

    private void openFlowGraph(String flowId) {
        if (flowId == null || flowId.isBlank() || "none".equalsIgnoreCase(flowId)) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null) {
            manager.openFlowEditor(serverId, null, flowId);
        }
    }

    private void openDialog(String dialogId) {
        if (dialogId == null || dialogId.isBlank() || "none".equalsIgnoreCase(dialogId)) {
            return;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null) {
            manager.openDialogDesigner(serverId, dialogId, this);
        }
    }

    private List<String> flowOptions() {
        return CompactBindingSupport.flowOptions(serverId);
    }

    private List<String> functionOptions() {
        return CompactBindingSupport.functionOptions(serverId);
    }

    private List<String> dialogOptions() {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return List.of("none");
        }
        List<String> options = new ArrayList<>();
        options.add("none");
        options.addAll(manager.getJsonResourcesForServer(serverId, ReSyncResourceType.DIALOG).keySet());
        return options;
    }

    private JsonObject resync(JsonObject action) {
        return object(action, "resync");
    }

    private JsonObject functionCall(JsonObject object, String key) {
        JsonObject call = object(object, key);
        if (!call.has("type")) {
            call.addProperty("type", "functionRef");
        }
        if (!call.has("functionId")) {
            call.addProperty("functionId", "");
        }
        return call;
    }

    private String realValue(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value) ? "" : value;
    }

    private String dialogType() {
        String type = text(dialog, "type");
        return DIALOG_TYPES.contains(type) ? type : "minecraft:multi_action";
    }

    private String shortTypeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String type = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
        return ItemOptionCatalog.formatOptionLabel(type.replace('_', ' '));
    }

    private JsonArray array(String key) {
        return array(dialog, key);
    }

    private JsonArray array(JsonObject object, String key) {
        if (object == null) {
            return new JsonArray();
        }
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            object.add(key, new JsonArray());
        }
        return object.getAsJsonArray(key);
    }

    private List<JsonObject> objectArray(String key) {
        List<JsonObject> values = new ArrayList<>();
        for (JsonElement element : array(key)) {
            if (element != null && element.isJsonObject()) {
                values.add(element.getAsJsonObject());
            }
        }
        return values;
    }

    private JsonObject object(JsonObject object, String key) {
        if (object == null) {
            return new JsonObject();
        }
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            object.add(key, new JsonObject());
        }
        return object.getAsJsonObject(key);
    }

    private JsonObject optionalObject(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private void removeIfEmpty(JsonObject object, String key) {
        JsonObject value = optionalObject(object, key);
        if (value != null && value.isEmpty()) {
            object.remove(key);
        }
    }

    private JsonObject objectAt(JsonArray array, int index) {
        if (!validIndex(array, index)) {
            return new JsonObject();
        }
        JsonElement element = array.get(index);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        JsonObject object = new JsonObject();
        array.set(index, object);
        return object;
    }

    private boolean validIndex(JsonArray array, int index) {
        return array != null && index >= 0 && index < array.size();
    }

    private String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String textOr(JsonObject object, String key, String fallback) {
        String value = text(object, key);
        return value.isBlank() ? fallback : value;
    }

    private boolean bool(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int intValue(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            try {
                return Integer.parseInt(object.get(key).getAsString());
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private String joinStringArray(JsonArray array) {
        List<String> values = new ArrayList<>();
        if (array != null) {
            for (JsonElement element : array) {
                if (element != null && !element.isJsonNull()) {
                    values.add(element.getAsString());
                }
            }
        }
        return String.join("\n", values);
    }

    private String firstArrayValue(JsonObject object, String key) {
        JsonArray array = array(object, key);
        return array.isEmpty() ? "" : array.get(0).getAsString();
    }
}
