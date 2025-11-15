package redxax.oxy.remotely.data.managed;

import java.util.UUID;

public class ManagedPlayer {
    public final UUID uuid;
    public String name;
    public boolean isOnline = false;
    public int ping = -1;
    public String address;
    public long lastSeen = 0;

    public boolean isOp = false;
    public int opLevel = 0;

    public boolean isBanned = false;
    public BanEntry banInfo;

    public boolean isIpBanned = false;
    public IpBanEntry ipBanInfo;

    public ManagedPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ManagedPlayer that = (ManagedPlayer) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}