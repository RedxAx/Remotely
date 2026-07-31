package redxax.oxy.remotely.flow.data;

public class TabDefinition {
    private String id;
    private boolean enabled = true;
    private String header;
    private String entryFormat;
    private String footer;

    public TabDefinition() {
        this.entryFormat = "%player%";
    }

    public TabDefinition(String id) {
        this.id = id;
        this.entryFormat = "%player%";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getEntryFormat() {
        return entryFormat;
    }

    public void setEntryFormat(String entryFormat) {
        this.entryFormat = entryFormat;
    }

    public String getFooter() {
        return footer;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }

    public TabDefinition copy() {
        TabDefinition copy = new TabDefinition();
        copy.id = id;
        copy.header = header;
        copy.entryFormat = entryFormat;
        copy.footer = footer;
        return copy;
    }
}
