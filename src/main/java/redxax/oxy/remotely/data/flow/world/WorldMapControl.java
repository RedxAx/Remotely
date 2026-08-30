package redxax.oxy.remotely.data.flow.world;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorldMapControl {
    private String extensionId;
    private String controlId;
    private String label;
    private String kind;
    private Map<String, Object> data = new LinkedHashMap<>();

    public String getExtensionId() {
        return extensionId;
    }

    public String getControlId() {
        return controlId;
    }

    public String getLabel() {
        return label;
    }

    public String getKind() {
        return kind;
    }

    public Map<String, Object> getData() {
        return data == null ? Map.of() : data;
    }

    void setExtensionId(String extensionId) {
        this.extensionId = extensionId;
    }

    void setControlId(String controlId) {
        this.controlId = controlId;
    }

    void setLabel(String label) {
        this.label = label;
    }

    void setKind(String kind) {
        this.kind = kind;
    }

    void setData(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }
}
