package redxax.oxy.remotely.data.managed;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;
import java.util.UUID;

public class PlayerLogEntry {
    @SerializedName("uuid")
    public UUID uuid;
    @SerializedName("name")
    public String name;
    @SerializedName("lastSeen")
    public long lastSeen;

    public PlayerLogEntry(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerLogEntry that = (PlayerLogEntry) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}