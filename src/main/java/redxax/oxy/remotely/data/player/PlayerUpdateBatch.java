package redxax.oxy.remotely.data.player;

import redxax.oxy.remotely.data.player.model.BanInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerUpdateBatch {
    private final String source;
    private final int priority;
    private final List<PlayerUpdate> updates = new ArrayList<>();

    public PlayerUpdateBatch(String source, int priority) {
        this.source = source;
        this.priority = priority;
    }

    public void add(PlayerUpdate update) {
        updates.add(update);
    }

    public String getSource() {
        return source;
    }

    public int getPriority() {
        return priority;
    }

    public List<PlayerUpdate> getUpdates() {
        return updates;
    }

    public static class PlayerUpdate {
        private final UUID uuid;
        private final String name;

        private Boolean online;
        private Integer ping;
        private Boolean op;
        private BanInfo ban;
        private String ip;
        private Long lastSeen;

        public PlayerUpdate(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }

        public Boolean getOnline() { return online; }
        public void setOnline(Boolean online) { this.online = online; }

        public Integer getPing() { return ping; }
        public void setPing(Integer ping) { this.ping = ping; }

        public Boolean getOp() { return op; }
        public void setOp(Boolean op) { this.op = op; }

        public BanInfo getBan() { return ban; }
        public void setBan(BanInfo ban) { this.ban = ban; }
        public boolean shouldClearBan() { return clearBan; }
        public void clearBan() { this.clearBan = true; }

        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public boolean shouldClearIp() { return clearIp; }
        public void clearIp() { this.clearIp = true; }

        public Long getLastSeen() { return lastSeen; }
        public void setLastSeen(Long lastSeen) { this.lastSeen = lastSeen; }

        private boolean clearBan = false;
        private boolean clearIp = false;
    }
}
