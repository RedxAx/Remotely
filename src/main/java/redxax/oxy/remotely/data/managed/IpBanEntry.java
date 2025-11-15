package redxax.oxy.remotely.data.managed;

import com.google.gson.annotations.SerializedName;

public class IpBanEntry {
    @SerializedName("ip")
    public String ip;
    @SerializedName("created")
    public String created;
    @SerializedName("source")
    public String source;
    @SerializedName("expires")
    public String expires;
    @SerializedName("reason")
    public String reason;
}