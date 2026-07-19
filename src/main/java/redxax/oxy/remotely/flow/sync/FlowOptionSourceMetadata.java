package redxax.oxy.remotely.flow.sync;

import java.util.List;

public class FlowOptionSourceMetadata {
    private String id;
    private String provider;
    private String widgetType;
    private boolean searchable;
    private String displayName;
    private String valueType;
    private List<String> contextKeys;

    public FlowOptionSourceMetadata() {
    }

    public FlowOptionSourceMetadata(String id, String provider, String widgetType, boolean searchable) {
        this(id, provider, widgetType, searchable, "", "string", List.of());
    }

    public FlowOptionSourceMetadata(String id, String provider, String widgetType, boolean searchable, String displayName, String valueType) {
        this(id, provider, widgetType, searchable, displayName, valueType, List.of());
    }

    public FlowOptionSourceMetadata(String id, String provider, String widgetType, boolean searchable, String displayName, String valueType,
                                    List<String> contextKeys) {
        this.id = id;
        this.provider = provider;
        this.widgetType = widgetType;
        this.searchable = searchable;
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : resolveDisplayName(id);
        this.valueType = valueType;
        this.contextKeys = contextKeys != null ? List.copyOf(contextKeys) : List.of();
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

    public String getDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return resolveDisplayName(id);
    }

    private static String resolveDisplayName(String id) {
        String source = id != null ? id : "";
        String[] segments = source.split(":");
        String name = segments.length > 0 ? segments[segments.length - 1] : source;
        StringBuilder resolved = new StringBuilder();
        for (String word : name.replace('-', '_').split("_")) {
            if (word.isBlank()) {
                continue;
            }
            if (!resolved.isEmpty()) {
                resolved.append(' ');
            }
            if ("id".equalsIgnoreCase(word)) {
                resolved.append("ID");
            } else if ("ids".equalsIgnoreCase(word)) {
                resolved.append("IDs");
            } else {
                resolved.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return resolved.isEmpty() ? source : resolved.toString();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getValueType() {
        return valueType != null && !valueType.isBlank() ? valueType : "string";
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public List<String> getContextKeys() {
        return contextKeys != null ? List.copyOf(contextKeys) : List.of();
    }

    public void setContextKeys(List<String> contextKeys) {
        this.contextKeys = contextKeys != null ? List.copyOf(contextKeys) : List.of();
    }
}
