package redxax.oxy.remotely.data.managed;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public class PlayerIdentifier {
    @SerializedName("uuid")
    public UUID uuid;
    @SerializedName("name")
    public String name;

    public PlayerIdentifier(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}