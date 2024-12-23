package redxax.oxy.servers;

import redxax.oxy.TerminalInstance;

import java.util.Objects;

public class ServerInfo {
    public String name;
    public String path;
    public String type;
    public String version;
    public boolean isRunning;
    public TerminalInstance terminal;
    public ServerState state = ServerState.STOPPED;

    public boolean isModServer() {
        return Objects.equals(type, "forge") || Objects.equals(type, "fabric") || Objects.equals(type, "neoforge");
    }
    public ServerInfo(String path) {
        this.path = path;
    }
}
