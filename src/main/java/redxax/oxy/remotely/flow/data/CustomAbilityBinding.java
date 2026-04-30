package redxax.oxy.remotely.flow.data;

public class CustomAbilityBinding {
    private String id;
    private String trigger;
    private String flowId;
    private boolean enabled = true;
    private CustomTriggerRule rule = new CustomTriggerRule();

    public CustomAbilityBinding() {
    }

    public CustomAbilityBinding(String id, String trigger, String flowId) {
        this.id = id;
        this.trigger = trigger;
        this.flowId = flowId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public CustomTriggerRule getRule() { return rule; }
    public void setRule(CustomTriggerRule rule) { this.rule = rule != null ? rule : new CustomTriggerRule(); }
}
