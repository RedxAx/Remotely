package redxax.oxy.remotely.flow.data;

public class TriggerBinding {
    private String id;
    private String flowId;
    private TriggerType type;
    private String context;

    public TriggerBinding() {
    }

    public TriggerBinding(String id, String flowId, TriggerType type, String context) {
        this.id = id;
        this.flowId = flowId;
        this.type = type;
        this.context = context;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public TriggerType getType() {
        return type;
    }

    public void setType(TriggerType type) {
        this.type = type;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
