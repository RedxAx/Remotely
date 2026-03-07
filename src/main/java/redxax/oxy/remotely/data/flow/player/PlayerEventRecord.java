package redxax.oxy.remotely.data.flow.player;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerEventRecord {
    private String eventId;
    private long timestamp;
    private String moduleId;
    private String category;
    private String type;
    private Map<String, Object> data = new LinkedHashMap<>();

    public String getEventId() {
        return eventId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getModuleId() {
        return moduleId;
    }

    public String getCategory() {
        return category;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data == null ? Map.of() : data;
    }
}
