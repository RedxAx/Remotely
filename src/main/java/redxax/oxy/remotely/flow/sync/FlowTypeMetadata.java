package redxax.oxy.remotely.flow.sync;

import java.util.List;

public class FlowTypeMetadata {
    private int schemaVersion = 1;
    private String id;
    private String canonicalId;
    private List<String> legacyIds = List.of();
    private String displayName;
    private String owner = "builtin";
    private int color;
    private String parentId;
    private String runtimeType;
    private String codecId;
    private int codecVersion = 1;
    private boolean transportable;
    private boolean persistable;
    private boolean canStringify;
    private boolean literalInput;
    private String literalEditor;
    private String catalogSource;
    private boolean objectPin;
    private boolean available = true;
    private String unavailableReason;

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

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCanonicalId() {
        return canonicalId != null && !canonicalId.isBlank() ? canonicalId : id;
    }

    public void setCanonicalId(String canonicalId) {
        this.canonicalId = canonicalId;
    }

    public List<String> getLegacyIds() {
        return legacyIds != null ? legacyIds : List.of();
    }

    public void setLegacyIds(List<String> legacyIds) {
        this.legacyIds = legacyIds != null ? List.copyOf(legacyIds) : List.of();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
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

    public String getRuntimeType() {
        return runtimeType;
    }

    public void setRuntimeType(String runtimeType) {
        this.runtimeType = runtimeType;
    }

    public String getCodecId() {
        return codecId;
    }

    public void setCodecId(String codecId) {
        this.codecId = codecId;
    }

    public int getCodecVersion() {
        return codecVersion;
    }

    public void setCodecVersion(int codecVersion) {
        this.codecVersion = codecVersion;
    }

    public boolean isTransportable() {
        return transportable;
    }

    public void setTransportable(boolean transportable) {
        this.transportable = transportable;
    }

    public boolean isPersistable() {
        return persistable;
    }

    public void setPersistable(boolean persistable) {
        this.persistable = persistable;
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

    public String getLiteralEditor() {
        return literalEditor;
    }

    public void setLiteralEditor(String literalEditor) {
        this.literalEditor = literalEditor;
    }

    public String getCatalogSource() {
        return catalogSource;
    }

    public void setCatalogSource(String catalogSource) {
        this.catalogSource = catalogSource;
    }

    public boolean isObjectPin() {
        return objectPin;
    }

    public void setObjectPin(boolean objectPin) {
        this.objectPin = objectPin;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }
}
