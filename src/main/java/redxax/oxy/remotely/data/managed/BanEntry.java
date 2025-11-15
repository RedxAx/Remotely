package redxax.oxy.remotely.data.managed;

import com.google.gson.annotations.SerializedName;

public class BanEntry {
    @SerializedName("uuid")
    public String uuid;
    @SerializedName("name")
    public String name;
    @SerializedName("created")
    public String created;
    @SerializedName("source")
    public String source;
    @SerializedName("expires")
    public String expires;
    @SerializedName("reason")
    public String reason;
}