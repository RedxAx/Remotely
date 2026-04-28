package redxax.oxy.remotely.flow.sync;

public class FlowTypeMetadata {
    private String id;
    private String displayName;
    private int color;
    private String parentId;
    private boolean canStringify;
    private boolean literalInput;
    private boolean objectPin;

    public FlowTypeMetadata() {
    }

    public FlowTypeMetadata(String id, String displayName, int color, String parentId, boolean canStringify, boolean literalInput, boolean objectPin) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.parentId = parentId;
        this.canStringify = canStringify;
        this.literalInput = literalInput;
        this.objectPin = objectPin;
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public boolean isCanStringify() {
        return canStringify;
    }

    public void setCanStringify(boolean canStringify) {
        this.canStringify = canStringify;
    }

    public boolean isLiteralInput() {
        return literalInput;
    }

    public void setLiteralInput(boolean literalInput) {
        this.literalInput = literalInput;
    }

    public boolean isObjectPin() {
        return objectPin;
    }

    public void setObjectPin(boolean objectPin) {
        this.objectPin = objectPin;
    }
}
