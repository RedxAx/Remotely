package redxax.oxy.remotely.data.player.model;

import redxax.oxy.remotely.util.BrowserSafeState;

import java.util.UUID;

public class UnifiedPlayer {
    private final UUID uuid;
    private volatile String name;

    private volatile PlayerAttribute<Boolean> online = new PlayerAttribute<>(false, "default", -1);
    private volatile PlayerAttribute<Integer> ping = new PlayerAttribute<>(-1, "default", -1);
    private volatile PlayerAttribute<Boolean> op = new PlayerAttribute<>(false, "default", -1);
    private volatile PlayerAttribute<BanInfo> ban = new PlayerAttribute<>(null, "default", -1);
    private volatile PlayerAttribute<String> ip = new PlayerAttribute<>(null, "default", -1);
    private volatile PlayerAttribute<Long> lastSeen = new PlayerAttribute<>(0L, "default", -1);

    public UnifiedPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlayerAttribute<Boolean> getOnline() { return online; }
    public PlayerAttribute<Integer> getPing() { return ping; }
    public PlayerAttribute<Boolean> getOp() { return op; }
    public PlayerAttribute<BanInfo> getBan() { return ban; }
    public PlayerAttribute<String> getIp() { return ip; }
    public PlayerAttribute<Long> getLastSeen() { return lastSeen; }

    public boolean isOnline() { return online.getValue() != null && online.getValue(); }
    public int getPingValue() { return ping.getValue() == null ? -1 : ping.getValue(); }
    public boolean isOp() { return op.getValue() != null && op.getValue(); }
    public long getLastSeenValue() { return lastSeen.getValue() == null ? 0L : lastSeen.getValue(); }

    private <T> PlayerAttribute<T> resolve(PlayerAttribute<T> current, T newValue, String source, int priority) {
        if (priority >= current.getPriority() || source.equals(current.getSource())) {
            return new PlayerAttribute<>(newValue, source, priority);
        }
        return current;
    }

    public void updateOnline(boolean value, String source, int priority) {
        this.online = resolve(this.online, value, source, priority);
    }

    public void updatePing(int value, String source, int priority) {
        this.ping = resolve(this.ping, value, source, priority);
    }

    public void updateOp(boolean value, String source, int priority) {
        this.op = resolve(this.op, value, source, priority);
    }

    public void updateBan(BanInfo value, String source, int priority) {
        this.ban = resolve(this.ban, value, source, priority);
    }

    public void updateIp(String value, String source, int priority) {
        this.ip = resolve(this.ip, value, source, priority);
    }

    public void updateLastSeen(long value, String source, int priority) {
        this.lastSeen = resolve(this.lastSeen, value, source, priority);
    }
}
