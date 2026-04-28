package redxax.oxy.remotely.flow.sync;

public class FlowCategoryMetadata {
    private String id;
    private String displayName;
    private int color;
    private int priority;

    public FlowCategoryMetadata() {
    }

    public FlowCategoryMetadata(String id, String displayName, int color, int priority) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.priority = priority;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
