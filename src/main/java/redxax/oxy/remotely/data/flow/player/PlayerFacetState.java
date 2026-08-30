package redxax.oxy.remotely.data.flow.player;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerFacetState {
    private String facetId;
    private String moduleId;
    private long updatedAt;
    private PlayerFacetMetadata metadata;
    private Map<String, Object> data = new LinkedHashMap<>();

    public PlayerFacetState() {
    }

    public PlayerFacetState(String facetId, String moduleId, long updatedAt, PlayerFacetMetadata metadata, Map<String, Object> data) {
        this.facetId = facetId; this.moduleId = moduleId; this.updatedAt = updatedAt; this.metadata = metadata; this.data = data;
    }

    public String getFacetId() {
        return facetId;
    }

    public String getModuleId() {
        return moduleId;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public PlayerFacetMetadata getMetadata() {
        return metadata;
    }

    public Map<String, Object> getData() {
        return data == null ? Map.of() : data;
    }
}
