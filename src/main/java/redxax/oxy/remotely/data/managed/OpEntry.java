package redxax.oxy.remotely.data.managed;

import com.google.gson.annotations.SerializedName;

public class OpEntry {
    @SerializedName("uuid")
    public String uuid;
    @SerializedName("name")
    public String name;
    @SerializedName("level")
    public int level;
    @SerializedName("bypassesPlayerLimit")
    public boolean bypassesPlayerLimit;
}