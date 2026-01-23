package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowType;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.render.Render;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static restudio.rescreen.config.Config.shadow;
import static restudio.rescreen.render.TextRenderer.tr;

public class NodeWidget extends AnimatedWidget {
    private final FlowNode node;
    private final FlowGraph graph;
    private final String nodeId;
    private final String serverId;
    private final List<NodeDefinition.PinDefinition> inputs = new ArrayList<>();
    private final List<NodeDefinition.PinDefinition> outputs = new ArrayList<>();
    private final NodeDefinition definition;
    private final Map<String, Widget> inputWidgets = new HashMap<>();
    private final List<NodeDefinition.PinDefinition> visibleOutputs = new ArrayList<>();
    private final List<FlowBranch> flowBranches = new ArrayList<>();
    private final Runnable onClose;
    private final AnimatedButton closeButton;
    private AnimatedButton addBranchButton;
    private static final int TITLE_HEIGHT = 16;
    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_SPACING = 6;
    private static final int PIN_BUTTON_SIZE = 10;
    private static final int PIN_TEXT_GAP = 4;
    private static final int INPUT_FIELD_GAP = 6;
    private static final int INPUT_WIDGET_WIDTH = 90;
    private static final int INPUT_WIDGET_HEIGHT = 16;
    private static final int OUTPUT_WIDGET_WIDTH = 100;
    private static final int TOGGLE_WIDGET_WIDTH = 28;
    private static final int TOGGLE_WIDGET_HEIGHT = 12;
    private static final int COLUMN_GAP = 12;
    private static final int SINGLE_COLUMN_MIN_WIDTH = 100;
    private static final int DEFAULT_WIDTH = 170;
    private static final int PIN_HIT_PADDING = 4;
    private static final int CLOSE_BUTTON_WIDTH = 12;
    private static final int CLOSE_BUTTON_HEIGHT = 8;
    private static final String FLOW_BRANCHES_KEY = "__flow_branches";
    private boolean updatingBranchSelection = false;

    public NodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId) {
        this(x, y, node, graph, nodeId, null, null);
    }

    public NodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId) {
        this(x, y, node, graph, nodeId, serverId, null);
    }

    public NodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId, String serverId, Runnable onClose) {
        super(x, y, DEFAULT_WIDTH, 100, "");
        this.node = node;
        this.graph = graph;
        this.nodeId = nodeId;
        this.serverId = serverId;
        this.definition = NodeRegistry.getInstance() != null ? NodeRegistry.getInstance().getDefinition(serverId, node.getType()) : null;
        this.enableHoverColors = false;
        this.animateElevation = false;
        this.entranceAnimationEnabled = false;
        this.onClose = onClose;
        this.closeButton = new AnimatedButton.Builder()
            .onClick(() -> {
                if (this.onClose != null) {
                    this.onClose.run();
                }
            })
            .accentType(ThemeManager.getAccent("danger"))
            .animateElevation(false)
            .entranceAnimation(false)
            .size(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
            .hint("Delete Node")
            .build();
        this.closeButton.visible = this.onClose != null;

        if (definition != null) {
            inputs.addAll(definition.getInputs());
            outputs.addAll(definition.getOutputs());
            createInputWidgets();
            createOutputWidgets();
            updateSize();
        } else {
            createDefaultPins();
        }
    }

    private void createInputWidgets() {
        if (graph == null || nodeId == null || graph.getConnections() == null) return;

        for (NodeDefinition.PinDefinition input : inputs) {
            if (input.getType() != NodeDefinition.PinType.DATA) {
                continue;
            }
            if (!isLiteralType(input.getDataType())) {
                continue;
            }
            if (!isInputWired(input.getName())) {
                Object currentValue = node.getInputValues() != null ? node.getInputValues().get(input.getName()) : null;
                List<String> options = getDropdownOptions(input);

                if (options != null) {
                    String selected = currentValue != null ? currentValue.toString() : options.getFirst();
                    for (String option : options) {
                        if (option.equalsIgnoreCase(selected)) {
                            selected = option;
                            break;
                        }
                    }
                    DropDownWidget<String> widget = new DropDownWidget.Builder<>(options)
                        .selectedItem(selected)
                        .onSelectionChanged(value -> saveInputValue())
                        .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                        .entranceAnimation(false)
                        .build();
                    inputWidgets.put(input.getName(), widget);
                } else if (input.getDataType() == FlowType.BOOLEAN) {
                    boolean toggled = currentValue instanceof Boolean ? (boolean) currentValue : Boolean.parseBoolean(String.valueOf(currentValue));
                    ToggleWidget widget = new ToggleWidget.Builder()
                        .toggled(toggled)
                        .onChange(this::saveInputValue)
                        .entranceAnimation(false)
                        .build();
                    widget.setSize(TOGGLE_WIDGET_WIDTH, TOGGLE_WIDGET_HEIGHT);
                    inputWidgets.put(input.getName(), widget);
                } else {
                    String textValue = currentValue != null ? currentValue.toString() : "";
                    TextInputWidget widget = new TextInputWidget.Builder()
                        .text(textValue)
                        .placeholder("")
                        .forcePlaceholder(false)
                        .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                        .onChange(this::saveInputValue)
                        .entranceAnimation(false)
                        .build();
                    inputWidgets.put(input.getName(), widget);
                }
            }
        }
    }

    private List<String> getDropdownOptions(NodeDefinition.PinDefinition input) {
        if (definition == null) {
            return null;
        }
        if ("variable_access".equals(definition.getId())) {
            return switch (input.getName()) {
                case "mode" -> List.of("Get", "Set", "Exists", "Delete", "List", "Increment", "Decrement", "Multiply", "Divide");
                case "scope" -> List.of("Local", "Global", "Player");
                default -> null;
            };
        }
        return null;
    }

    public void refreshInputWidgets() {
        inputWidgets.clear();
        createInputWidgets();
        createOutputWidgets();
        updateSize();
    }

    private boolean isLiteralType(FlowType type) {
        return type == FlowType.STRING || type == FlowType.NUMBER || type == FlowType.BOOLEAN || type == FlowType.ANY;
    }

    private boolean isInputWired(String pinName) {
        for (FlowConnection conn : graph.getConnections()) {
            if (conn.getTargetNodeId().equals(nodeId) && conn.getTargetPin().equals(pinName)) {
                return true;
            }
        }
        return false;
    }

    private void createDefaultPins() {
        inputs.clear();
        outputs.clear();
        inputs.add(new NodeDefinition.PinDefinition("in", NodeDefinition.PinType.DATA, NodeDefinition.PinDirection.INPUT, FlowType.ANY));
        outputs.add(new NodeDefinition.PinDefinition("out", NodeDefinition.PinType.FLOW, NodeDefinition.PinDirection.OUTPUT, FlowType.EXECUTION));
        visibleOutputs.clear();
        visibleOutputs.addAll(outputs);
        updateSize();
    }

    private int getPinColor(FlowType dataType) {
        if (dataType == null) {
            return 0xFFAAAAAA;
        }
        return dataType.getColor();
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        int headerBg = ThemeManager.getColor(ThemeColor.inClickableBackground);
        int headerText = ThemeManager.getColor(ThemeColor.text);
        int labelText = ThemeManager.getColor(ThemeColor.textDark);
        int borderColor = ThemeManager.getColor(ThemeColor.innerBorder);

        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + TITLE_HEIGHT, headerBg);
        Render.drawInnerBorder(ctx, getX(), getY(), getWidth(), TITLE_HEIGHT, borderColor);
        ctx.drawText(definition != null ? definition.getDisplayName() : node.getType(), getX() + 4, getY() + 4, headerText, shadow);

        if (closeButton.visible) {
            int closeX = getX() + getWidth() - PADDING - CLOSE_BUTTON_WIDTH;
            int closeY = (getY() + (TITLE_HEIGHT - CLOSE_BUTTON_HEIGHT) / 2) - 1;
            closeButton.setPosition(closeX, closeY);
            closeButton.render(ctx, mouseX, mouseY, 0);
        }

        updateInputWidgetPositions();
        updateOutputWidgetPositions();

        int rightColumnWidth = getRightColumnWidth();
        int rightColumnStart = getX() + getWidth() - PADDING - rightColumnWidth;

        for (int i = 0; i < inputs.size(); i++) {
            NodeDefinition.PinDefinition input = inputs.get(i);
            int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
            int pinY = rowY + (ROW_HEIGHT - PIN_BUTTON_SIZE) / 2;
            int pinX = getX() + PADDING;
            drawPinButton(ctx, pinX, pinY, getPinColor(input.getDataType()));

            int textY = rowY + (ROW_HEIGHT - ITextRenderer.fontHeight) / 2 + 1;
            ctx.drawText(input.getName(), pinX + PIN_BUTTON_SIZE + PIN_TEXT_GAP, textY, labelText, shadow);

            Widget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                inputWidget.render(ctx, mouseX, mouseY, 0);
            }
        }

        for (int i = 0; i < visibleOutputs.size(); i++) {
            NodeDefinition.PinDefinition output = visibleOutputs.get(i);
            int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
            int pinY = rowY + (ROW_HEIGHT - PIN_BUTTON_SIZE) / 2;
            int pinX = rightColumnStart + rightColumnWidth - PIN_BUTTON_SIZE;
            int textY = rowY + (ROW_HEIGHT - ITextRenderer.fontHeight) / 2 + 1;
            DropDownWidget<String> branchWidget = getBranchWidget(output.getName());
            if (branchWidget == null) {
                int labelWidth = tr.getWidth(output.getName());
                int labelX = pinX - PIN_TEXT_GAP - labelWidth;
                ctx.drawText(output.getName(), labelX, textY, labelText, shadow);
            } else {
                branchWidget.render(ctx, mouseX, mouseY, 0);
            }
            drawPinButton(ctx, pinX, pinY, getPinColor(output.getDataType()));
        }

        if (addBranchButton != null && addBranchButton.visible) {
            addBranchButton.render(ctx, mouseX, mouseY, 0);
        }
    }

    private void updateSize() {
        int rowCount = Math.max(inputs.size(), visibleOutputs.size());
        int leftColumnWidth = getLeftColumnWidth();
        int rightColumnWidth = getRightColumnWidth();
        int contentWidth = leftColumnWidth + rightColumnWidth + (leftColumnWidth > 0 && rightColumnWidth > 0 ? COLUMN_GAP : 0);
        int contentHeight = rowCount > 0 ? (rowCount * ROW_HEIGHT + (rowCount - 1) * ROW_SPACING) : 0;
        if (addBranchButton != null && addBranchButton.visible) {
            contentHeight += ROW_HEIGHT + ROW_SPACING;
        }
        int minWidth = (inputs.isEmpty() || visibleOutputs.isEmpty()) ? SINGLE_COLUMN_MIN_WIDTH : DEFAULT_WIDTH;
        int titleWidth = tr.getWidth(definition != null ? definition.getDisplayName() : node.getType()) + PADDING * 2;
        if (closeButton.visible) {
            titleWidth += CLOSE_BUTTON_WIDTH + PADDING;
        }

        setWidth(Math.max(minWidth, Math.max(titleWidth, PADDING * 2 + contentWidth)));
        setHeight(TITLE_HEIGHT + PADDING * 2 + contentHeight);
    }

    public double[] getPinBounds(String pinName, boolean isInput) {
        List<NodeDefinition.PinDefinition> pins = isInput ? inputs : visibleOutputs;
        int index = -1;

        for (int i = 0; i < pins.size(); i++) {
            if (pins.get(i).getName().equals(pinName)) {
                index = i;
                break;
            }
        }

        if (index == -1) return null;

        int rowY = getRowStartY() + index * (ROW_HEIGHT + ROW_SPACING);
        int pinY = rowY + (ROW_HEIGHT - PIN_BUTTON_SIZE) / 2;
        int pinX = isInput ? getX() + PADDING : getX() + getWidth() - PADDING - PIN_BUTTON_SIZE;
        return new double[]{pinX, pinY, PIN_BUTTON_SIZE, PIN_BUTTON_SIZE};
    }

    public boolean isMouseOverPin(int wx, int wy) {
        return getPinAtPosition(wx, wy) != null;
    }

    public Widget getOutputWidgetAt(int wx, int wy) {
        updateOutputWidgetPositions();
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && branch.widget.isMouseOver(wx, wy)) {
                return branch.widget;
            }
        }
        if (addBranchButton != null && addBranchButton.visible && addBranchButton.isMouseOver(wx, wy)) {
            return addBranchButton;
        }
        return null;
    }

    public Widget getInputWidgetAt(int wx, int wy) {
        updateInputWidgetPositions();
        for (Widget widget : inputWidgets.values()) {
            if (widget.isMouseOver(wx, wy)) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int wx = (int)mouseX;
        int wy = (int)mouseY;

        if (closeButton.visible && closeButton.isMouseOver(wx, wy)) {
            closeButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        Widget outputWidget = getOutputWidgetAt(wx, wy);
        if (outputWidget != null) {
            outputWidget.mouseClicked(mouseX, mouseY, button);
            if (outputWidget instanceof DropDownWidget<?> && ScreenManager.getInstance().getCurrentScreen() != null) {
                ScreenManager.getInstance().getCurrentScreen().setFocusedWidget(outputWidget);
            }
            return true;
        }

        if (isMouseOverPin(wx, wy)) {
            return true;
        }

        Widget inputWidget = getInputWidgetAt(wx, wy);
        if (inputWidget != null) {
            inputWidget.mouseClicked(mouseX, mouseY, button);
            if (inputWidget instanceof TextInputWidget) {
                if (ScreenManager.getInstance().getCurrentScreen() != null) {
                    ScreenManager.getInstance().getCurrentScreen().setFocusedWidget(inputWidget);
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (Widget widget : inputWidgets.values()) {
            widget.mouseReleased(mouseX, mouseY, button);
        }
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null) {
                branch.widget.mouseReleased(mouseX, mouseY, button);
            }
        }
        if (addBranchButton != null && addBranchButton.visible) {
            addBranchButton.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && focusedWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        for (Widget widget : inputWidgets.values()) {
            if (widget != focusedWidget && widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && branch.widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        if (addBranchButton != null && addBranchButton.visible && addBranchButton.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        for (FlowBranch branch : flowBranches) {
            if (branch.widget != null && branch.widget.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        for (Widget widget : inputWidgets.values()) {
            if (widget.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && focusedWidget.charTyped(chr, modifiers)) {
            saveInputValue();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null && focusedWidget.keyPressed(keyCode, scanCode, modifiers)) {
            saveInputValue();
            return true;
        }
        return false;
    }

    private TextInputWidget getFocusedInputWidget() {
        for (Widget widget : inputWidgets.values()) {
            if (widget instanceof TextInputWidget textInput && textInput.isFocused()) {
                return textInput;
            }
        }
        return null;
    }

    private void updateInputWidgetPositions() {
        int leftColumnWidth = getLeftColumnWidth();
        int leftColumnEnd = getX() + PADDING + leftColumnWidth;

        for (int i = 0; i < inputs.size(); i++) {
            NodeDefinition.PinDefinition input = inputs.get(i);
            Widget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
                int widgetWidth = getInputWidgetWidth(inputWidget);
                int widgetHeight = getInputWidgetHeight(inputWidget);
                int widgetY = rowY + (ROW_HEIGHT - widgetHeight) / 2;
                int widgetX = leftColumnEnd - widgetWidth;
                inputWidget.setPosition(widgetX, widgetY);
                inputWidget.setWidth(widgetWidth);
                inputWidget.setHeight(widgetHeight);
                inputWidget.setPriority(inputs.size() - i);
                if (inputWidget instanceof AnimatedWidget w) {
                    w.setLayer(inputs.size() - i);
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        borderColor = ThemeManager.getColor(ThemeColor.innerBorder);
        outerBorderColor = ThemeManager.getColor(ThemeColor.globalOuterBorder);
        bgColor = ThemeManager.getColor(ThemeColor.innerBackground);
    }

    @Override
    protected void drawBackground(IDrawContext ctx) {
        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), ThemeManager.getColor(ThemeColor.innerBackground));
    }

    private int getLeftColumnWidth() {
        int width = 0;
        for (NodeDefinition.PinDefinition input : inputs) {
            int labelWidth = tr.getWidth(input.getName());
            int rowWidth = PIN_BUTTON_SIZE + PIN_TEXT_GAP + labelWidth;
            Widget widget = inputWidgets.get(input.getName());
            if (widget != null) {
                rowWidth += INPUT_FIELD_GAP + getInputWidgetWidth(widget);
            }
            width = Math.max(width, rowWidth);
        }
        return width;
    }

    private int getRightColumnWidth() {
        int width = 0;
        for (NodeDefinition.PinDefinition output : visibleOutputs) {
            DropDownWidget<String> branchWidget = getBranchWidget(output.getName());
            if (branchWidget != null) {
                int rowWidth = getOutputWidgetWidth(branchWidget) + PIN_TEXT_GAP + PIN_BUTTON_SIZE;
                width = Math.max(width, rowWidth);
            } else {
                int labelWidth = tr.getWidth(output.getName());
                int rowWidth = labelWidth + PIN_TEXT_GAP + PIN_BUTTON_SIZE;
                width = Math.max(width, rowWidth);
            }
        }
        if (addBranchButton != null && addBranchButton.visible) {
            width = Math.max(width, addBranchButton.getWidth());
        }
        return width;
    }

    private int getRowStartY() {
        return getY() + TITLE_HEIGHT + PADDING;
    }

    private void drawPinButton(IDrawContext ctx, int x, int y, int color) {
        int background = ThemeManager.getColor(ThemeColor.inClickableBackground);
        int border = ThemeManager.getColor(ThemeColor.innerBorder);
        ctx.fill(x, y, x + PIN_BUTTON_SIZE, y + PIN_BUTTON_SIZE, background);
        Render.drawInnerBorder(ctx, x, y, PIN_BUTTON_SIZE, PIN_BUTTON_SIZE, border);
        int inset = 2;
        ctx.fill(x + inset, y + inset, x + PIN_BUTTON_SIZE - inset, y + PIN_BUTTON_SIZE - inset, color);
    }

    private void saveInputValue() {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        for (Map.Entry<String, Widget> entry : inputWidgets.entrySet()) {
            NodeDefinition.PinDefinition def = findInputDefinition(entry.getKey());
            if (def == null) {
                continue;
            }
            Widget widget = entry.getValue();
            Object typedValue = null;
            if (widget instanceof TextInputWidget textInput) {
                String value = textInput.getText();
                if (value.isEmpty()) {
                    node.getInputValues().remove(entry.getKey());
                    continue;
                }
                typedValue = convertValue(value, def.getDataType());
            } else if (widget instanceof ToggleWidget toggle) {
                typedValue = toggle.getValue();
            } else if (widget instanceof DropDownWidget<?> dropdown) {
                Object selected = dropdown.getSelectedItem();
                if (selected == null) {
                    node.getInputValues().remove(entry.getKey());
                    continue;
                }
                typedValue = convertValue(selected.toString(), def.getDataType());
            }
            if (typedValue != null) {
                node.getInputValues().put(entry.getKey(), typedValue);
            }
        }
    }

    private int getInputWidgetWidth(Widget widget) {
        if (widget instanceof ToggleWidget) {
            return TOGGLE_WIDGET_WIDTH;
        }
        return INPUT_WIDGET_WIDTH;
    }

    private int getInputWidgetHeight(Widget widget) {
        if (widget instanceof ToggleWidget) {
            return TOGGLE_WIDGET_HEIGHT;
        }
        return INPUT_WIDGET_HEIGHT;
    }

    private NodeDefinition.PinDefinition findInputDefinition(String pinName) {
        for (NodeDefinition.PinDefinition input : inputs) {
            if (input.getName().equals(pinName)) {
                return input;
            }
        }
        return null;
    }

    private Object convertValue(String value, FlowType dataType) {
        if (dataType == null || dataType == FlowType.ANY) {
            return value;
        }
        try {
            return switch (dataType) {
                case STRING, EXECUTION, PLAYER, LOCATION, ITEM, LIST, ENTITY, ITEMSTACK, JSON_OBJECT -> value;
                case NUMBER -> Double.parseDouble(value);
                case BOOLEAN -> Boolean.parseBoolean(value);
                case ANY -> value;
            };
        } catch (NumberFormatException e) {
            return value;
        }
    }

    public FlowType getPinType(String pinName, boolean isInput) {
        if (isInput) {
            for (NodeDefinition.PinDefinition input : inputs) {
                if (input.getName().equals(pinName)) {
                    return input.getDataType();
                }
            }
        } else {
            for (NodeDefinition.PinDefinition output : outputs) {
                if (output.getName().equals(pinName)) {
                    return output.getDataType();
                }
            }
        }
        return null;
    }

    public NodeDefinition.PinType getPinKind(String pinName, boolean isInput) {
        if (isInput) {
            for (NodeDefinition.PinDefinition input : inputs) {
                if (input.getName().equals(pinName)) {
                    return input.getType();
                }
            }
        } else {
            for (NodeDefinition.PinDefinition output : outputs) {
                if (output.getName().equals(pinName)) {
                    return output.getType();
                }
            }
        }
        return null;
    }

    public String getPinAtPosition(int wx, int wy) {
        for (NodeDefinition.PinDefinition input : inputs) {
            double[] bounds = getPinBounds(input.getName(), true);
            if (bounds != null && isInside(wx, wy, bounds)) {
                return input.getName();
            }
        }
        for (NodeDefinition.PinDefinition output : visibleOutputs) {
            double[] bounds = getPinBounds(output.getName(), false);
            if (bounds != null && isInside(wx, wy, bounds)) {
                return output.getName();
            }
        }
        return null;
    }

    private void createOutputWidgets() {
        visibleOutputs.clear();
        flowBranches.clear();

        List<NodeDefinition.PinDefinition> flowOutputs = new ArrayList<>();
        List<NodeDefinition.PinDefinition> otherOutputs = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : outputs) {
            if (isFlowOutput(output)) {
                flowOutputs.add(output);
            } else {
                otherOutputs.add(output);
            }
        }

        if (flowOutputs.size() <= 2) {
            visibleOutputs.addAll(outputs);
            addBranchButton = null;
            return;
        }

        List<String> selectedBranches = resolveFlowBranches(flowOutputs);
        for (String branch : selectedBranches) {
            NodeDefinition.PinDefinition pin = findOutputDefinition(branch);
            if (pin != null) {
                visibleOutputs.add(pin);
                flowBranches.add(new FlowBranch(branch, buildBranchSelector(flowOutputs, branch)));
            }
        }

        visibleOutputs.addAll(otherOutputs);
        saveFlowBranches();
        updateAddBranchButton(flowOutputs);
    }

    private boolean isFlowOutput(NodeDefinition.PinDefinition output) {
        return output.getType() == NodeDefinition.PinType.FLOW && output.getDataType() == FlowType.EXECUTION;
    }

    private NodeDefinition.PinDefinition findOutputDefinition(String name) {
        for (NodeDefinition.PinDefinition output : outputs) {
            if (output.getName().equals(name)) {
                return output;
            }
        }
        return null;
    }

    private List<String> resolveFlowBranches(List<NodeDefinition.PinDefinition> flowOutputs) {
        List<String> options = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : flowOutputs) {
            options.add(output.getName());
        }

        List<String> selected = new ArrayList<>();
        if (node.getInputValues() != null) {
            Object stored = node.getInputValues().get(FLOW_BRANCHES_KEY);
            if (stored instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof String name && options.contains(name)) {
                        if (!selected.contains(name)) {
                            selected.add(name);
                        }
                    }
                }
            }
        }

        if (graph != null && graph.getConnections() != null) {
            for (FlowConnection conn : graph.getConnections()) {
                if (nodeId.equals(conn.getSourceNodeId()) && options.contains(conn.getSourcePin())) {
                    if (!selected.contains(conn.getSourcePin())) {
                        selected.add(conn.getSourcePin());
                    }
                }
            }
        }

        if (selected.isEmpty() && !options.isEmpty()) {
            selected.add(options.getFirst());
        }
        return selected;
    }

    private DropDownWidget<String> buildBranchSelector(List<NodeDefinition.PinDefinition> flowOutputs, String selected) {
        List<String> options = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : flowOutputs) {
            options.add(output.getName());
        }
        return new DropDownWidget.Builder<>(options)
            .selectedItem(selected)
            .onSelectionChanged(value -> updateFlowBranchSelection(selected, value))
            .size(OUTPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
            .entranceAnimation(false)
            .build();
    }

    private void updateFlowBranchSelection(String oldName, String newName) {
        if (updatingBranchSelection || oldName == null || newName == null || oldName.equals(newName)) {
            return;
        }
        FlowBranch targetBranch = null;
        boolean conflict = false;
        for (FlowBranch branch : flowBranches) {
            if (branch.outputName.equals(oldName)) {
                targetBranch = branch;
            } else if (branch.outputName.equals(newName)) {
                conflict = true;
            }
        }

        if (conflict) {
            updatingBranchSelection = true;
            if (targetBranch != null && targetBranch.widget != null) {
                targetBranch.widget.setSelectedItem(oldName);
            }
            updatingBranchSelection = false;
            return;
        }

        if (targetBranch != null) {
            targetBranch.outputName = newName;
        }

        if (graph != null && graph.getConnections() != null) {
            for (FlowConnection conn : graph.getConnections()) {
                if (nodeId.equals(conn.getSourceNodeId()) && oldName.equals(conn.getSourcePin())) {
                    conn.setSourcePin(newName);
                }
            }
        }

        saveFlowBranches();
        createOutputWidgets();
        updateSize();
    }

    private void updateAddBranchButton(List<NodeDefinition.PinDefinition> flowOutputs) {
        if (flowBranches.size() >= flowOutputs.size()) {
            addBranchButton = null;
            return;
        }
        if (addBranchButton == null) {
            addBranchButton = new AnimatedButton.Builder()
                .label("add flow branch")
                .onClick(this::addFlowBranch)
                .animateElevation(false)
                .entranceAnimation(false)
                .size(OUTPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                .build();
        }
        addBranchButton.visible = true;
    }

    private void addFlowBranch() {
        List<String> options = new ArrayList<>();
        for (NodeDefinition.PinDefinition output : outputs) {
            if (isFlowOutput(output)) {
                options.add(output.getName());
            }
        }
        for (String option : options) {
            boolean used = false;
            for (FlowBranch branch : flowBranches) {
                if (branch.outputName.equals(option)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                flowBranches.add(new FlowBranch(option, null));
                break;
            }
        }
        saveFlowBranches();
        createOutputWidgets();
        updateSize();
    }

    private void saveFlowBranches() {
        if (node.getInputValues() == null) {
            node.setInputValues(new HashMap<>());
        }
        List<String> branches = new ArrayList<>();
        for (FlowBranch branch : flowBranches) {
            branches.add(branch.outputName);
        }
        node.getInputValues().put(FLOW_BRANCHES_KEY, branches);
    }

    private void updateOutputWidgetPositions() {
        int rightColumnWidth = getRightColumnWidth();
        int buttonX = getX() + getWidth() - PADDING - rightColumnWidth;

        for (int i = 0; i < visibleOutputs.size(); i++) {
            NodeDefinition.PinDefinition output = visibleOutputs.get(i);
            DropDownWidget<String> branchWidget = getBranchWidget(output.getName());
            if (branchWidget != null) {
                int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
                int widgetWidth = getOutputWidgetWidth(branchWidget);
                int widgetHeight = getOutputWidgetHeight(branchWidget);
                int widgetY = rowY + (ROW_HEIGHT - widgetHeight) / 2;
                int widgetX = buttonX + rightColumnWidth - PIN_BUTTON_SIZE - PIN_TEXT_GAP - widgetWidth;
                branchWidget.setPosition(widgetX, widgetY);
                branchWidget.setWidth(widgetWidth);
                branchWidget.setHeight(widgetHeight);
                branchWidget.setPriority(visibleOutputs.size() - i);
                branchWidget.setLayer(visibleOutputs.size() - i);
            }
        }

        if (addBranchButton != null && addBranchButton.visible) {
            int rowCount = Math.max(inputs.size(), visibleOutputs.size());
            int rowY = getRowStartY() + rowCount * (ROW_HEIGHT + ROW_SPACING);
            addBranchButton.setPosition(buttonX, rowY);
            addBranchButton.setWidth(Math.min(OUTPUT_WIDGET_WIDTH, rightColumnWidth));
            addBranchButton.setHeight(INPUT_WIDGET_HEIGHT);
        }
    }

    private int getOutputWidgetWidth(Widget widget) {
        return OUTPUT_WIDGET_WIDTH;
    }

    private int getOutputWidgetHeight(Widget widget) {
        return INPUT_WIDGET_HEIGHT;
    }

    private DropDownWidget<String> getBranchWidget(String outputName) {
        for (FlowBranch branch : flowBranches) {
            if (branch.outputName.equals(outputName)) {
                return branch.widget;
            }
        }
        return null;
    }

    private static class FlowBranch {
        private String outputName;
        private final DropDownWidget<String> widget;

        private FlowBranch(String outputName, DropDownWidget<String> widget) {
            this.outputName = outputName;
            this.widget = widget;
        }
    }

    private boolean isInside(int x, int y, double[] bounds) {
        double minX = bounds[0] - PIN_HIT_PADDING;
        double minY = bounds[1] - PIN_HIT_PADDING;
        double maxX = bounds[0] + bounds[2] + PIN_HIT_PADDING;
        double maxY = bounds[1] + bounds[3] + PIN_HIT_PADDING;
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
