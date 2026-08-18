package redxax.oxy.remotely.data.flow.world;

import java.util.List;

public class WorldMapSnapshot {
    private List<WorldMapControl> controls;
    private List<WorldMapDrawing> drawings;
    private long generatedAt;

    public List<WorldMapControl> getControls() {
        return controls == null ? List.of() : controls;
    }

    public List<WorldMapDrawing> getDrawings() {
        return drawings == null ? List.of() : drawings;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    void setControls(List<WorldMapControl> controls) {
        this.controls = controls;
    }

    void setDrawings(List<WorldMapDrawing> drawings) {
        this.drawings = drawings;
    }

    void setGeneratedAt(long generatedAt) {
        this.generatedAt = generatedAt;
    }
}
