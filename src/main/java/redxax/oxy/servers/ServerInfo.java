package redxax.oxy.servers;

import redxax.oxy.TerminalInstance;

public class ServerInfo {
    public String name;
    public String path;
    public String type;
    public String version;
    public int maxPlayers = 20;
    public int port = 25565;
    public boolean onlineMode = true;
    public int ramMB = 4;
    public boolean isRunning;
    public TerminalInstance terminal;
    public ServerState state = ServerState.STOPPED;
}
