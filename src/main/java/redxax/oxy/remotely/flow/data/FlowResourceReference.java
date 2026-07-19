package redxax.oxy.remotely.flow.data;

import java.util.LinkedHashMap;
import java.util.Map;

public class FlowResourceReference {
    private String kind;
    private String id;
    private String owner;
    private boolean available = true;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public FlowResourceReference() {
    }

    public FlowResourceReference(String kind, String id, String owner) {
        this.kind = kind;
        this.id = id;
        this.owner = owner;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        return id != null ? id : "";
    }
}
