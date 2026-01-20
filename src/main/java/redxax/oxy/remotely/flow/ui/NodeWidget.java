package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.FlowConnection;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowNode;
import redxax.oxy.remotely.flow.data.FlowType;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeWidget extends AnimatedWidget {
    private final FlowNode node;
    private final FlowGraph graph;
    private final String nodeId;
    private final List<NodeDefinition.PinDefinition> inputs = new ArrayList<>();
    private final List<NodeDefinition.PinDefinition> outputs = new ArrayList<>();
    private final NodeDefinition definition;
    private final Map<String, TextInputWidget> inputWidgets = new HashMap<>();
    private final boolean variableNode;
    private static final int PIN_SIZE = 8;
    private static final int PIN_SPACING = 20;
    private static final int HEADER_HEIGHT = 25;
    private static final int INPUT_WIDGET_WIDTH = 60;
    private static final int INPUT_WIDGET_HEIGHT = 16;
    private static final int DEFAULT_WIDTH = 150;
    private static final int VARIABLE_WIDTH = 100;
    private static final int VARIABLE_HEIGHT = 40;
    private static final int PIN_OUTSET = 4;

    public NodeWidget(int x, int y, FlowNode node, FlowGraph graph, String nodeId) {
        super(x, y, DEFAULT_WIDTH, 100, "");
        this.animateElevation = false;
        this.node = node;
        this.graph = graph;
        this.nodeId = nodeId;
        this.definition = NodeRegistry.getInstance() != null ? NodeRegistry.getInstance().getDefinition(node.getType()) : null;
        this.variableNode = definition != null && definition.getCategory() == NodeDefinition.NodeCategory.VARIABLE;

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
                    .placeholder(input.getName())
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
        if (variableNode) {
            drawVariableNode(ctx);
        } else {
            drawStandardNode(ctx, mouseX, mouseY);
        }
    }

    private void drawVariableNode(IDrawContext ctx) {
        int accent = ThemeManager.getAccent("nice").getAccentColor();
        int pinColor = ThemeManager.getColor(ThemeColor.innerBorder);
        int textColor = ThemeManager.getColor(ThemeColor.text);

        ctx.fill(getX() + 1, getY() + 1, getX() + 4, getY() + getHeight() - 1, accent);

        String name = definition != null ? definition.getDisplayName() : node.getType();
        String shortened = name.length() > 10 ? name.substring(0, 10) + ".." : name;
        ctx.drawText(shortened, getX() + 8, getY() + (getHeight() - 8) / 2, textColor, true);

        for (NodeDefinition.PinDefinition input : inputs) {
            double[] bounds = getPinBounds(input.getName(), true);
            if (bounds != null) {
                int color = getPinColor(input.getDataType());
                ctx.fill((int) bounds[0], (int) bounds[1], (int) (bounds[0] + bounds[2]), (int) (bounds[1] + bounds[3]), color);
            }
        }

        for (NodeDefinition.PinDefinition output : outputs) {
            double[] bounds = getPinBounds(output.getName(), false);
            if (bounds != null) {
                int color = getPinColor(output.getDataType());
                ctx.fill((int) bounds[0], (int) bounds[1], (int) (bounds[0] + bounds[2]), (int) (bounds[1] + bounds[3]), color);
            }
        }
    }

    private void drawStandardNode(IDrawContext ctx, int mouseX, int mouseY) {
        int headerAccent = ThemeManager.getAccent("calm").getAccentColor();
        int headerText = ThemeManager.getColor(ThemeColor.text);
        int labelText = ThemeManager.getColor(ThemeColor.textDark);

        ctx.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + HEADER_HEIGHT, headerAccent);

        ctx.drawText(definition != null ? definition.getDisplayName() : node.getType(),
                  getX() + 6, getY() + 5, headerText, true);

        updateInputWidgetPositions();

        int yOffset = HEADER_HEIGHT + 5;

        for (NodeDefinition.PinDefinition input : inputs) {
            int pinX = getX() - PIN_OUTSET - PIN_SIZE;
            ctx.fill(pinX, getY() + yOffset,
                     pinX + PIN_SIZE, getY() + yOffset + PIN_SIZE,
                     getPinColor(input.getDataType()));

            TextInputWidget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                inputWidget.render(ctx, mouseX, mouseY, 0);
            } else {
                ctx.drawText(input.getName(), getX() + 12, getY() + yOffset, labelText, true);
            }
            yOffset += PIN_SPACING;
        }

        yOffset = HEADER_HEIGHT + 5;
        for (NodeDefinition.PinDefinition output : outputs) {
            int textWidth = 8 * output.getName().length();
            ctx.drawText(output.getName(), getX() + getWidth() - 14 - textWidth, getY() + yOffset, labelText, true);
            int pinX = getX() + getWidth() + PIN_OUTSET;
            ctx.fill(pinX, getY() + yOffset, pinX + PIN_SIZE, getY() + yOffset + PIN_SIZE, getPinColor(output.getDataType()));
            yOffset += PIN_SPACING;
        }
    }

    private void updateSize() {
        int pinCount = Math.max(inputs.size(), outputs.size());
        if (variableNode) {
            setWidth(VARIABLE_WIDTH);
            setHeight(VARIABLE_HEIGHT);
        } else {
            setWidth(DEFAULT_WIDTH);
            setHeight(HEADER_HEIGHT + pinCount * PIN_SPACING);
        }
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

        int yOffset;
        if (variableNode && Math.max(inputs.size(), outputs.size()) <= 1) {
            yOffset = (getHeight() - PIN_SIZE) / 2;
        } else {
            yOffset = HEADER_HEIGHT + 5 + index * PIN_SPACING;
        }
        int pinX = isInput ? getX() - PIN_OUTSET - PIN_SIZE : getX() + getWidth() + PIN_OUTSET;
        return new double[]{pinX, getY() + yOffset, PIN_SIZE, PIN_SIZE};
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
        int yOffset = HEADER_HEIGHT + 5;
        for (NodeDefinition.PinDefinition input : inputs) {
            TextInputWidget inputWidget = inputWidgets.get(input.getName());
            if (inputWidget != null) {
                int widgetX = getX() + 12;
                int widgetY = getY() + yOffset;
                inputWidget.setPosition(widgetX, widgetY);
            }
            yOffset += PIN_SPACING;
        }
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
