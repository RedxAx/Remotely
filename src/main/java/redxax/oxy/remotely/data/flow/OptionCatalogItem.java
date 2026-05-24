package redxax.oxy.remotely.data.flow;

import java.util.Map;

public class OptionCatalogItem {
    private String value;
    private String label;
    private String description;
    private String icon;
    private String group;
    private Map<String, Object> metadata;

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
