package redxax.oxy.remotely.data.managed;

import com.google.gson.annotations.SerializedName;

public class SessionEvent {
    @SerializedName("timestamp")
    public long timestamp;
    @SerializedName("type")
    public SessionEventType type;
    @SerializedName("details")
    public String details;

    public SessionEvent(long timestamp, SessionEventType type, String details) {
        this.timestamp = timestamp;
        this.type = type;
        this.details = details;
    }
}