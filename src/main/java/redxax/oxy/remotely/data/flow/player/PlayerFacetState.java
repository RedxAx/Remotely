package redxax.oxy.remotely.data.flow.player;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerFacetState {
    private String facetId;
    private String moduleId;
    private long updatedAt;
    private Map<String, Object> data = new LinkedHashMap<>();

    public String getFacetId() {
        return facetId;
    }

    public String getModuleId() {
        return moduleId;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public Map<String, Object> getData() {
        return data == null ? Map.of() : data;
    }
}
