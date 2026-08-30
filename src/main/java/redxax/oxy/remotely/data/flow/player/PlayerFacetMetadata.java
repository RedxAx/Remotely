package redxax.oxy.remotely.data.flow.player;

public class PlayerFacetMetadata {
    private String title;
    private String tabName;
    private int priority;
    private boolean tab;

    public PlayerFacetMetadata() {
    }

    public PlayerFacetMetadata(String title, String tabName, int priority, boolean tab) {
        this.title = title; this.tabName = tabName; this.priority = priority; this.tab = tab;
    }

    public String getTitle() {
        return title;
    }

    public String getTabName() {
        return tabName;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isTab() {
        return tab;
    }
}
