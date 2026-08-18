package redxax.oxy.remotely.ui.server;

import restudio.rebase.restudio.api.models.ServerModels;

import java.util.HashMap;

public final class ClientServerDetailsTarget implements ServerDetailsTarget {
    private final ServerModels.ClientServerView server;

    public ClientServerDetailsTarget(ServerModels.ClientServerView server) {
        this.server = server;
    }

    @Override public Object value() { return server; }
    @Override public String id() {
        if (server == null) return "";
        return server.identifier == null || server.identifier.isBlank() ? server.uuid == null ? "" : server.uuid : server.identifier;
    }
    @Override public String name() { return server == null || server.name == null || server.name.isBlank() ? id() : server.name; }
    @Override public String backendType() {
        if (server == null) return "";
        if (server.nodeName != null && !server.nodeName.isBlank()) return server.nodeName;
        return server.environment == null ? "" : server.environment.getOrDefault("backend", "");
    }
    @Override public ServerScreenHost.ServerState state() {
        if (server == null) return ServerScreenHost.ServerState.UNKNOWN;
        if (server.isInstalling) return ServerScreenHost.ServerState.INSTALLING;
        String value = setting("state", "");
        if (value.isBlank()) value = setting("currentState", "");
        if (value.isBlank()) value = setting("current_state", "");
        return ServerScreenHost.ServerState.parse(value);
    }
    @Override public boolean server() { return server != null; }
    @Override public boolean local() { return false; }
    @Override public String version() { return server == null || server.version == null ? "" : server.version; }
    @Override public int port() { return server == null ? 0 : server.port; }
    @Override public String setting(String key, String fallback) {
        if (server == null || server.environment == null) return fallback;
        String value = server.environment.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }
    @Override public void setState(ServerScreenHost.ServerState state) {
        if (server == null || state == null) return;
        if (server.environment == null) server.environment = new HashMap<>();
        server.environment.put("state", state.name());
        server.environment.put("currentState", state.name());
    }
}
