package redxax.oxy.remotely.data.managed;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerSession {
    @SerializedName("uuid")
    public UUID uuid;
    @SerializedName("name")
    public String name;
    @SerializedName("ip")
    public String ip;
    @SerializedName("startTime")
    public long startTime;
    @SerializedName("endTime")
    public long endTime;
    @SerializedName("events")
    public List<SessionEvent> events = new ArrayList<>();

    public PlayerSession(UUID uuid, String name, String ip, long startTime) {
        this.uuid = uuid;
        this.name = name;
        this.ip = ip;
        this.startTime = startTime;
    }
}