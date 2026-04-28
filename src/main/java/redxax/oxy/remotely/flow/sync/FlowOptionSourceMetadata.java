package redxax.oxy.remotely.flow.sync;

public class FlowOptionSourceMetadata {
    private String id;
    private String provider;
    private String widgetType;
    private boolean searchable;

    public FlowOptionSourceMetadata() {
    }

    public FlowOptionSourceMetadata(String id, String provider, String widgetType, boolean searchable) {
        this.id = id;
        this.provider = provider;
        this.widgetType = widgetType;
        this.searchable = searchable;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getWidgetType() {
        return widgetType;
    }

    public void setWidgetType(String widgetType) {
        this.widgetType = widgetType;
    }

    public boolean isSearchable() {
        return searchable;
    }

    public void setSearchable(boolean searchable) {
        this.searchable = searchable;
    }
}
