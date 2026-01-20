package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.data.GuiElement;
import redxax.oxy.remotely.flow.data.Visual;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.widgets.AnimatedWidget;

public class SlotBatchWidget extends AnimatedWidget {
    private final GuiElement element;
    private boolean isResizing;
    private int resizeHandle = 0;
    private boolean selected = false;

    public SlotBatchWidget(int x, int y, int width, int height, GuiElement element) {
        super(x, y, width, height, "");
        this.element = element;
    }

    @Override
    protected void drawContent(IDrawContext ctx, int mouseX, int mouseY) {
        Visual visual = element.getVisual();
        int color = 0xFF00AA00;

        if (visual != null && visual.getMaterial() != null) {
            String matName = visual.getMaterial().toUpperCase();
            if (matName.contains("DIAMOND")) color = 0xFF00BFFF;
            else if (matName.contains("GOLD")) color = 0xFFFFD700;
            else if (matName.contains("IRON")) color = 0xFFD3D3D;
            else if (matName.contains("REDSTONE")) color = 0xFFFF0000;
        }

        if (selected) {
            color = 0xFFFFFF00;
        }

        ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);

        if (visual != null && visual.getName() != null) {
            ctx.drawText(visual.getName().length() > 10 ? visual.getName().substring(0, 10) + ".." : visual.getName(),
                      getX() + 2, getY() + 5, 0xFFFFFFFF, true);
        }

        drawResizeHandles(ctx);
    }

    private void drawResizeHandles(IDrawContext ctx) {
        int handleSize = 4;
        ctx.fill(getX() + getWidth() - handleSize, getY() + getHeight() - handleSize,
                 getX() + getWidth(), getY() + getHeight(), 0xFF0000FF);
        ctx.fill(getX() + getWidth() - handleSize, getY(),
                 getX() + getWidth(), getY() + handleSize, 0xFF0000FF);
        ctx.fill(getX(), getY() + getHeight() - handleSize,
                 getX() + handleSize, getY() + getHeight(), 0xFF0000FF);
        ctx.fill(getX(), getY(), getX() + handleSize, getY() + handleSize, 0xFF0000FF);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    protected Builder self() {
        return new Builder(this) {
             protected Builder self() { return this; }
        };
    }

    public GuiElement getElement() {
        return element;
    }
}
