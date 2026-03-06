package redxax.oxy.remotely.flow.data;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardDefinition {
    private String id;
    private String title;
    private String objectiveId;
    private String displaySlot;
    private List<String> lines;

    public ScoreboardDefinition() {
        this.lines = new ArrayList<>();
        this.displaySlot = "sidebar";
    }

    public ScoreboardDefinition(String id, String title) {
        this.id = id;
        this.title = title;
        this.objectiveId = id;
        this.displaySlot = "sidebar";
        this.lines = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getObjectiveId() {
        return objectiveId;
    }

    public void setObjectiveId(String objectiveId) {
        this.objectiveId = objectiveId;
    }

    public String getDisplaySlot() {
        return displaySlot;
    }

    public void setDisplaySlot(String displaySlot) {
        this.displaySlot = displaySlot;
    }

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public ScoreboardDefinition copy() {
        ScoreboardDefinition copy = new ScoreboardDefinition();
        copy.id = id;
        copy.title = title;
        copy.objectiveId = objectiveId;
        copy.displaySlot = displaySlot;
        copy.lines = new ArrayList<>(lines != null ? lines : List.of());
        return copy;
    }
}
