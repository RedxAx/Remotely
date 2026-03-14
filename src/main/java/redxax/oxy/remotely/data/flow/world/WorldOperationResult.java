package redxax.oxy.remotely.data.flow.world;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorldOperationResult {
    private boolean success;
    private String action;
    private String message;
    private String worldName;
    private Map<String, Object> data = new LinkedHashMap<>();

    public boolean isSuccess() {
        return success;
    }

    public String getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public String getWorldName() {
        return worldName;
    }

    public Map<String, Object> getData() {
        return data == null ? Map.of() : data;
    }
}
