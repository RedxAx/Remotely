package redxax.oxy.remotely.data.flow.world;

import java.util.List;
import java.util.Map;

public class WorldMapDrawing {
    private String extensionId;
    private String drawingId;
    private String label;
    private String kind;
    private String worldName;
    private List<WorldMapCoordinate> coordinates;
    private Map<String, Object> data;

    public String getExtensionId() {
        return extensionId;
    }

    public String getDrawingId() {
        return drawingId;
    }

    public String getLabel() {
        return label;
    }

    public String getKind() {
        return kind;
    }

    public String getWorldName() {
        return worldName;
    }

    public List<WorldMapCoordinate> getCoordinates() {
        return coordinates == null ? List.of() : coordinates;
    }

    public Map<String, Object> getData() {
        return data == null ? Map.of() : data;
    }

    void setExtensionId(String extensionId) {
        this.extensionId = extensionId;
    }

    void setDrawingId(String drawingId) {
        this.drawingId = drawingId;
    }

    void setLabel(String label) {
        this.label = label;
    }

    void setKind(String kind) {
        this.kind = kind;
    }

    void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    void setCoordinates(List<WorldMapCoordinate> coordinates) {
        this.coordinates = coordinates;
    }

    void setData(Map<String, Object> data) {
        this.data = data;
    }
}
