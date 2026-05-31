package redxax.oxy.remotely.data.flow;

import java.util.Map;

public class OptionCatalogItem {
    private String value;
    private String label;
    private String description;
    private String icon;
    private String group;
    private Map<String, Object> metadata;

    public void setValue(String value) {
        this.value = value;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label != null && !label.isBlank() ? label : value;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public String getIcon() {
        return icon != null ? icon : "";
    }

    public String getGroup() {
        return group != null ? group : "";
    }

    public Map<String, Object> getMetadata() {
        return metadata != null ? metadata : Map.of();
    }
}
