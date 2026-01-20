package redxax.oxy.remotely.flow.data;

import java.util.ArrayList;
import java.util.List;

public class GuiElement {
    private List<Integer> slots;
    private Visual visual;
    private String flowId;

    public GuiElement() {
        this.slots = new ArrayList<>();
        this.visual = new Visual();
    }

    public GuiElement(List<Integer> slots, Visual visual, String flowId) {
        this.slots = slots != null ? slots : new ArrayList<>();
        this.visual = visual != null ? visual : new Visual();
        this.flowId = flowId;
    }

    public List<Integer> getSlots() {
        return slots;
    }

    public void setSlots(List<Integer> slots) {
        this.slots = slots != null ? slots : new ArrayList<>();
    }

    public Visual getVisual() {
        return visual;
    }

    public void setVisual(Visual visual) {
        this.visual = visual != null ? visual : new Visual();
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public GuiElement copy() {
        GuiElement copy = new GuiElement();
        if (this.slots != null) {
            copy.getSlots().addAll(this.slots);
        }
        if (this.visual != null) {
            copy.setVisual(this.visual.copy());
        }
        copy.setFlowId(this.flowId);
        return copy;
    }
}
