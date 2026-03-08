package redxax.oxy.remotely.data.flow.world;

import java.util.List;

public class WorldMapSnapshot {
    private List<WorldMapDrawing> drawings;
    private long generatedAt;

    public List<WorldMapDrawing> getDrawings() {
        return drawings == null ? List.of() : drawings;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }
}
