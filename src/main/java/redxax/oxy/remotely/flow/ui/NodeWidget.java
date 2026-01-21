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
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

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
    private final List<NodeDefinition.PinDefinition> inputs = new ArrayList<>();
    private final List<NodeDefinition.PinDefinition> outputs = new ArrayList<>();
    private final NodeDefinition definition;
    private final Map<String, TextInputWidget> inputWidgets = new HashMap<>();
    private static final int TITLE_HEIGHT = 16;
    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_SPACING = 6;
    private static final int PIN_BUTTON_SIZE = 10;
    private static final int PIN_TEXT_GAP = 4;
    private static final int INPUT_FIELD_GAP = 6;
    private static final int INPUT_WIDGET_WIDTH = 90;
    private static final int INPUT_WIDGET_HEIGHT = 16;
    private static final int COLUMN_GAP = 12;
    private static final int DEFAULT_WIDTH = 170;

    public NodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId) {
        super(x, y, DEFAULT_WIDTH, 100, "");
        this.node = node;
        this.graph = graph;
        this.nodeId = nodeId;
        this.definition = NodeRegistry.getInstance() != null ? NodeRegistry.getInstance().getDefinition(node.getType()) : null;
        this.enableHoverColors = false;
        this.animateElevation = false;

        if (definition != null) {
            inputs.addAll(definition.getInputs());
            outputs.addAll(definition.getOutputs());
            createInputWidgets();
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
                String currentValue = "";
                if (node.getInputValues() != null && node.getInputValues().containsKey(input.getName())) {
                    Object val = node.getInputValues().get(input.getName());
                    currentValue = val != null ? val.toString() : "";
                }

                TextInputWidget widget = new TextInputWidget.Builder()
                    .text(currentValue)
                    .placeholder("")
                    .forcePlaceholder(false)
                    .size(INPUT_WIDGET_WIDTH, INPUT_WIDGET_HEIGHT)
                    .onChange(this::saveInputValue)
                    .build();

                inputWidgets.put(input.getName(), widget);
            }
        }
    }

    public void refreshInputWidgets() {
        inputWidgets.clear();
        createInputWidgets();
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

        updateInputWidgetPositions();

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

            TextInputWidget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                inputWidget.render(ctx, mouseX, mouseY, 0);
            }
        }

        for (int i = 0; i < outputs.size(); i++) {
            NodeDefinition.PinDefinition output = outputs.get(i);
            int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
            int pinY = rowY + (ROW_HEIGHT - PIN_BUTTON_SIZE) / 2;
            int pinX = rightColumnStart + rightColumnWidth - PIN_BUTTON_SIZE;
            int labelWidth = tr.getWidth(output.getName());
            int textY = rowY + (ROW_HEIGHT - ITextRenderer.fontHeight) / 2 + 1;
            int labelX = pinX - PIN_TEXT_GAP - labelWidth;

            ctx.drawText(output.getName(), labelX, textY, labelText, shadow);
            drawPinButton(ctx, pinX, pinY, getPinColor(output.getDataType()));
        }
    }

    private void updateSize() {
        int rowCount = Math.max(inputs.size(), outputs.size());
        int leftColumnWidth = getLeftColumnWidth();
        int rightColumnWidth = getRightColumnWidth();
        int contentWidth = leftColumnWidth + rightColumnWidth + (leftColumnWidth > 0 && rightColumnWidth > 0 ? COLUMN_GAP : 0);
        int contentHeight = rowCount > 0 ? (rowCount * ROW_HEIGHT + (rowCount - 1) * ROW_SPACING) : 0;

        setWidth(Math.max(DEFAULT_WIDTH, PADDING * 2 + contentWidth));
        setHeight(TITLE_HEIGHT + PADDING * 2 + contentHeight);
    }

    public double[] getPinBounds(String pinName, boolean isInput) {
        List<NodeDefinition.PinDefinition> pins = isInput ? inputs : outputs;
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

    public TextInputWidget getInputWidgetAt(int wx, int wy) {
        updateInputWidgetPositions();
        for (TextInputWidget widget : inputWidgets.values()) {
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

        if (isMouseOverPin(wx, wy)) {
            return true;
        }

        TextInputWidget inputWidget = getInputWidgetAt(wx, wy);
        if (inputWidget != null) {
            inputWidget.mouseClicked(mouseX, mouseY, button);
            if (ScreenManager.getInstance().getCurrentScreen() != null) {
                ScreenManager.getInstance().getCurrentScreen().setFocusedWidget(inputWidget);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (TextInputWidget widget : inputWidgets.values()) {
            widget.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        TextInputWidget focusedWidget = getFocusedInputWidget();
        if (focusedWidget != null) {
            return focusedWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
        for (TextInputWidget widget : inputWidgets.values()) {
            if (widget.isFocused()) {
                return widget;
            }
        }
        return null;
    }

    private void updateInputWidgetPositions() {
        int leftColumnWidth = getLeftColumnWidth();
        int leftColumnEnd = getX() + PADDING + leftColumnWidth;

        for (int i = 0; i < inputs.size(); i++) {
            NodeDefinition.PinDefinition input = inputs.get(i);
            TextInputWidget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                int rowY = getRowStartY() + i * (ROW_HEIGHT + ROW_SPACING);
                int widgetY = rowY + (ROW_HEIGHT - INPUT_WIDGET_HEIGHT) / 2;
                int widgetX = leftColumnEnd - INPUT_WIDGET_WIDTH;
                inputWidget.setPosition(widgetX, widgetY);
                inputWidget.setWidth(INPUT_WIDGET_WIDTH);
                inputWidget.setHeight(INPUT_WIDGET_HEIGHT);
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
            if (inputWidgets.containsKey(input.getName())) {
                rowWidth += INPUT_FIELD_GAP + INPUT_WIDGET_WIDTH;
            }
            width = Math.max(width, rowWidth);
        }
        return width;
    }

    private int getRightColumnWidth() {
        int width = 0;
        for (NodeDefinition.PinDefinition output : outputs) {
            int labelWidth = tr.getWidth(output.getName());
            int rowWidth = labelWidth + PIN_TEXT_GAP + PIN_BUTTON_SIZE;
            width = Math.max(width, rowWidth);
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
        for (Map.Entry<String, TextInputWidget> entry : inputWidgets.entrySet()) {
            String value = entry.getValue().getText();
            if (value.isEmpty()) {
                node.getInputValues().remove(entry.getKey());
                continue;
            }
            NodeDefinition.PinDefinition def = findInputDefinition(entry.getKey());
            if (def != null) {
                Object typedValue = convertValue(value, def.getDataType());
                node.getInputValues().put(entry.getKey(), typedValue);
            }
        }
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
                case STRING, EXECUTION, PLAYER, LOCATION, ITEM -> value;
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
        for (NodeDefinition.PinDefinition output : outputs) {
            double[] bounds = getPinBounds(output.getName(), false);
            if (bounds != null && isInside(wx, wy, bounds)) {
                return output.getName();
            }
        }
        return null;
    }

    private boolean isInside(int x, int y, double[] bounds) {
        return x >= bounds[0] && x <= bounds[0] + bounds[2] && y >= bounds[1] && y <= bounds[1] + bounds[3];
    }
}
