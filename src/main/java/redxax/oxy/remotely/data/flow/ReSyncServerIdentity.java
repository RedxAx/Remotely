package redxax.oxy.remotely.data.flow;

import restudio.rebase.restudio.api.models.ServerModels.ClientServerView;

public record ReSyncServerIdentity(String serverId, String displayName, String backendType) {
    public ReSyncServerIdentity(String serverId, String displayName) {
        this(serverId, displayName, "");
    }

    public ReSyncServerIdentity {
        serverId = normalize(serverId);
        displayName = normalize(displayName);
        backendType = normalize(backendType);
    }

    public static ReSyncServerIdentity of(String serverId) {
        return new ReSyncServerIdentity(serverId, "", "");
    }

    public static ReSyncServerIdentity from(String requestedServerId, ClientServerView server) {
        String identifier = server == null ? "" : normalize(server.identifier);
        if (identifier.isBlank() && server != null) {
            identifier = normalize(server.uuid);
        }
        if (identifier.isBlank()) {
            identifier = requestedServerId;
        }
        return new ReSyncServerIdentity(identifier, server == null ? "" : server.name,
            server == null ? "" : server.backendType);
    }

    public boolean present() {
        return !serverId.isBlank();
    }

    public boolean isReStudioTarget() {
        return "RESTUDIO".equalsIgnoreCase(backendType);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
