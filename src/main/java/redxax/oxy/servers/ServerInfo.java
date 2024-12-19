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
    public int currentPlayers = 0;
    public int maxPlayers = 20;
    public int tps = 20;
    public String uptime = "0h 0m";

    public boolean isModServer() {
        return Objects.equals(type, "forge") || Objects.equals(type, "fabric") || Objects.equals(type, "neoforge");
    }
}
