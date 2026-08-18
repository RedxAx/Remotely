package redxax.oxy.remotely.config;

public record RemotelyRecentItem(Kind kind, String serverId, String path, String name) {
    public RemotelyRecentItem {
        kind = kind == null ? Kind.SERVER : kind;
        serverId = serverId == null ? "" : serverId.trim();
        path = path == null ? "" : path.trim();
        name = name == null || name.isBlank() ? path : name.trim();
    }

    public boolean valid() {
        return !path.isBlank() && (kind == Kind.SERVER || !serverId.isBlank());
    }

    public String key() {
        return kind.name() + "|" + serverId + "|" + path;
    }

    public enum Kind {
        SERVER,
        ITEM
    }
}
