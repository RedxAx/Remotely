package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.GuiDefinition;
import redxax.oxy.remotely.flow.data.Visual;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.ThemeColor;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.HashMap;
import java.util.Map;

public class InspectorPanel extends AnimatedWidget {
    private GuiElement selectedElement;
    private GuiDefinition gui;
    private String serverId;
    private boolean expanded;
    private final Map<String, TextInputWidget> inputWidgets = new HashMap<>();
    private int editButtonX = 10;
    private int editButtonY = 230;
    private int editButtonW = 110;
    private int editButtonH = 20;
    private int saveButtonX = 130;
    private int saveButtonY = 230;
    private int saveButtonW = 110;
    private int saveButtonH = 20;
    private int newFlowButtonX = 10;
    private int newFlowButtonY = 200;
    private int newFlowButtonW = 230;
    private int newFlowButtonH = 20;
    private int fieldStartY = 35;
    private int fieldSpacing = 30;
    private int fieldLabelHeight = 12;
    private int fieldInputHeight = 18;
    private static final int HEADER_HEIGHT = 25;
    private DropDownWidget<String> flowDropdown;

    public InspectorPanel(GuiDefinition gui) {
        this(gui, null);
    }

    public InspectorPanel(GuiDefinition gui, String serverId) {
        super(0, 0, 250, 300, "");
        this.gui = gui;
        this.serverId = serverId;
        this.expanded = false;
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        int panelBg = ThemeManager.getColor(ThemeColor.elementBackground);
        int headerBg = ThemeManager.getAccent("calm").getAccentDarkColor();
        int text = ThemeManager.getColor(ThemeColor.text);
        int textDark = ThemeManager.getColor(ThemeColor.textDark);
        int panelX = getX();
        int panelY = getY();

        ctx.fill(panelX, panelY, panelX + getWidth(), panelY + getHeight(), panelBg);
        ctx.fill(panelX, panelY, panelX + getWidth(), panelY + HEADER_HEIGHT, headerBg);

        ctx.drawText("Inspector", panelX + 10, panelY + 6, text, true);

        if (selectedElement != null && expanded) {
            int y = panelY + fieldStartY;
            
            TextInputWidget nameWidget = inputWidgets.get("name");
            TextInputWidget materialWidget = inputWidgets.get("material");
            DropDownWidget<String> flowSelect = flowDropdown;
            TextInputWidget loreWidget = inputWidgets.get("lore");
            
            if (nameWidget != null) {
                drawFieldLabel(ctx, "Name:", panelX + 10, y);
                nameWidget.setPosition(panelX + 10, y + fieldLabelHeight);
                nameWidget.render(ctx, mouseX, mouseY, 0);
                y += fieldSpacing;
            }
            
            if (materialWidget != null) {
                drawFieldLabel(ctx, "Material:", panelX + 10, y);
                materialWidget.setPosition(panelX + 10, y + fieldLabelHeight);
                materialWidget.render(ctx, mouseX, mouseY, 0);
                y += fieldSpacing;
            }
            
            if (flowSelect != null) {
                drawFieldLabel(ctx, "On Click:", panelX + 10, y);
                flowSelect.setPosition(panelX + 10, y + fieldLabelHeight);
                flowSelect.setWidth(getFieldWidth());
                flowSelect.render(ctx, mouseX, mouseY, 0);
                y += fieldSpacing;
            }
            
            if (loreWidget != null) {
                drawFieldLabel(ctx, "Lore:", panelX + 10, y);
                loreWidget.setPosition(panelX + 10, y + fieldLabelHeight);
                loreWidget.render(ctx, mouseX, mouseY, 0);
                y += fieldSpacing;
            }

            drawButton(ctx, "New Flow", panelX + newFlowButtonX, panelY + newFlowButtonY, newFlowButtonW, newFlowButtonH, mouseX, mouseY);
            drawButton(ctx, "Edit Flow", panelX + editButtonX, panelY + editButtonY, editButtonW, editButtonH, mouseX, mouseY);
            drawButton(ctx, "Save", panelX + saveButtonX, panelY + saveButtonY, saveButtonW, saveButtonH, mouseX, mouseY);
        } else if (selectedElement == null) {
            ctx.drawText("No element selected", panelX + 10, panelY + 40, textDark, true);
        } else {
            ctx.drawText("Click to expand inspector", panelX + 10, panelY + 40, textDark, true);
        }
    }

    private void drawFieldLabel(IDrawContext ctx, String label, int x, int y) {
        ctx.drawText(label, x, y, ThemeManager.getColor(ThemeColor.textDark), true);
    }

    private void drawButton(IDrawContext ctx, String text, int x, int y, int w, int h, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;

        int buttonBg = hover ? ThemeManager.getAccent("nice").getAccentColor() : ThemeManager.getColor(ThemeColor.inClickableBackground);
        int buttonText = ThemeManager.getColor(ThemeColor.text);

        ctx.fill(x, y, x + w, y + h, buttonBg);
        ctx.drawText(text, x + (w - 8 * text.length()) / 2, y + 6, buttonText, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedElement == null || !expanded || button != 0) return false;

        int mx = (int)mouseX;
        int my = (int)mouseY;
        int panelX = getX();
        int panelY = getY();

        for (TextInputWidget widget : inputWidgets.values()) {
            if (widget.isMouseOver(mx, my)) {
                widget.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }

        if (flowDropdown != null && flowDropdown.isMouseOver(mx, my)) {
            flowDropdown.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (mx >= panelX + newFlowButtonX && mx <= panelX + newFlowButtonX + newFlowButtonW &&
            my >= panelY + newFlowButtonY && my <= panelY + newFlowButtonY + newFlowButtonH) {
            onCreateFlow();
            return true;
        }

        if (mx >= panelX + editButtonX && mx <= panelX + editButtonX + editButtonW && 
            my >= panelY + editButtonY && my <= panelY + editButtonY + editButtonH) {
            onEditLogic();
            return true;
        }

        if (mx >= panelX + saveButtonX && mx <= panelX + saveButtonX + saveButtonW && 
            my >= panelY + saveButtonY && my <= panelY + saveButtonY + saveButtonH) {
            onSave();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selectedElement == null || !expanded || button != 0) return false;

        for (TextInputWidget widget : inputWidgets.values()) {
            widget.mouseReleased(mouseX, mouseY, button);
        }

        if (flowDropdown != null) {
            flowDropdown.mouseReleased(mouseX, mouseY, button);
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (selectedElement == null || !expanded) return false;

        for (TextInputWidget widget : inputWidgets.values()) {
            if (widget.charTyped(chr, modifiers)) {
                return true;
            }
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedElement == null || !expanded) return false;

        for (TextInputWidget widget : inputWidgets.values()) {
            if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onEditLogic() {
        if (selectedElement == null) {
            return;
        }
        String flowId = flowDropdown != null ? flowDropdown.getSelectedItem() : selectedElement.getFlowId();
        if (flowId == null) {
            return;
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager != null) {
            flowManager.openFlowEditor(serverId != null ? serverId : "default", null, flowId);
        }
    }

    private void onSave() {
        if (selectedElement == null) return;

        Visual visual = selectedElement.getVisual() != null ? selectedElement.getVisual() : new Visual();
        
        if (inputWidgets.containsKey("name")) {
            visual.setName(inputWidgets.get("name").getText());
        }
        
        if (inputWidgets.containsKey("material")) {
            visual.setMaterial(inputWidgets.get("material").getText());
        }
        
        if (flowDropdown != null && flowDropdown.getSelectedItem() != null) {
            selectedElement.setFlowId(flowDropdown.getSelectedItem());
        }

        TextInputWidget loreWidget = inputWidgets.get("lore");
        if (loreWidget != null && !loreWidget.getText().isEmpty()) {
            java.util.List<String> lore = new java.util.ArrayList<>();
            for (String line : loreWidget.getText().split("\\|")) {
                lore.add(line.trim());
            }
            visual.setLore(lore);
        } else {
            visual.getLore().clear();
        }
    }

    public void setSelectedElement(GuiElement element) {
        this.selectedElement = element;
        this.expanded = element != null;
        inputWidgets.clear();
        flowDropdown = null;

        if (element != null && element.getVisual() != null) {
            Visual visual = element.getVisual();
            int inputWidth = getFieldWidth();
            
            TextInputWidget nameWidget = new TextInputWidget.Builder()
                .text(visual.getName() != null ? visual.getName() : "")
                .placeholder("Name")
                .size(inputWidth, fieldInputHeight)
                .build();
            inputWidgets.put("name", nameWidget);
            
            TextInputWidget materialWidget = new TextInputWidget.Builder()
                .text(visual.getMaterial() != null ? visual.getMaterial() : "")
                .placeholder("Material")
                .size(inputWidth, fieldInputHeight)
                .build();
            inputWidgets.put("material", materialWidget);
            
            java.util.List<String> flowIds = new java.util.ArrayList<>();
            FlowManager flowManager = FlowManager.getInstance();
            if (flowManager != null && serverId != null) {
                flowIds.addAll(flowManager.getFlowsForServer(serverId).keySet());
            }
            String currentFlow = element.getFlowId();
            if (currentFlow != null && !flowIds.contains(currentFlow)) {
                flowIds.add(currentFlow);
            }
            if (!flowIds.isEmpty()) {
                flowDropdown = new DropDownWidget.Builder<>(flowIds)
                    .size(inputWidth, fieldInputHeight)
                    .selectedItem(currentFlow != null ? currentFlow : flowIds.get(0))
                    .onSelectionChanged(selection -> element.setFlowId(selection))
                    .build();
            }
            
            String loreText = "";
            if (visual.getLore() != null && !visual.getLore().isEmpty()) {
                loreText = String.join(" | ", visual.getLore());
            }
            TextInputWidget loreWidget = new TextInputWidget.Builder()
                .text(loreText)
                .placeholder("Lore (separate with |)")
                .size(inputWidth, fieldInputHeight)
                .build();
            inputWidgets.put("lore", loreWidget);
        }
        updateInputSizes();
    }

    public void setGui(GuiDefinition gui) {
        this.gui = gui;
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        updateInputSizes();
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        updateButtonLayout();
    }

    private int getFieldWidth() {
        return Math.max(120, getWidth() - 20);
    }

    private void updateInputSizes() {
        int inputWidth = getFieldWidth();
        for (TextInputWidget widget : inputWidgets.values()) {
            widget.setWidth(inputWidth);
        }
        if (flowDropdown != null) {
            flowDropdown.setWidth(inputWidth);
        }
        updateButtonLayout();
    }

    private void updateButtonLayout() {
        int buttonWidth = Math.max(80, (getWidth() - 30) / 2);
        editButtonX = 10;
        saveButtonX = editButtonX + buttonWidth + 10;
        editButtonW = buttonWidth;
        saveButtonW = buttonWidth;
        int buttonY = Math.max(fieldStartY + fieldSpacing * 5, getHeight() - 30);
        editButtonY = buttonY;
        saveButtonY = buttonY;
        newFlowButtonX = 10;
        newFlowButtonW = Math.max(120, getWidth() - 20);
        newFlowButtonY = buttonY - newFlowButtonH - 6;
    }

    private void onCreateFlow() {
        if (selectedElement == null || serverId == null) {
            return;
        }
        FlowManager flowManager = FlowManager.getInstance();
        if (flowManager == null) {
            return;
        }
        var graph = flowManager.createFlow(serverId);
        selectedElement.setFlowId(graph.getId().toString());
        setSelectedElement(selectedElement);
        flowManager.openFlowEditor(serverId, null, graph.getId().toString());
    }
}
