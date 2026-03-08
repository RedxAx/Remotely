package redxax.oxy.remotely.data.flow.world;

import com.google.gson.JsonElement;

public class WorldChannelMessage {
    private String type;
    private String action;
    private boolean success;
    private String message;
    private JsonElement data;
    private long timestamp;

    public String getType() {
        return type;
    }

    public String getAction() {
        return action;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public JsonElement getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
