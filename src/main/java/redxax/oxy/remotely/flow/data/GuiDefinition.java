package redxax.oxy.remotely.flow.data;

import java.util.ArrayList;
import java.util.List;

public class GuiDefinition {
    private String id;
    private String title;
    private int rows;
    private List<GuiElement> elements;

    public GuiDefinition() {
        this.elements = new ArrayList<>();
    }

    public GuiDefinition(String id, String title, int rows) {
        this.id = id;
        this.title = title;
        this.rows = rows;
        this.elements = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getRows() { return rows; }
    public void setRows(int rows) { this.rows = rows; }

    public List<GuiElement> getElements() { return elements; }
    public void setElements(List<GuiElement> elements) { this.elements = elements; }
}
