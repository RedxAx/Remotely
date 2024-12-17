package redxax.oxy.servers;

import redxax.oxy.TerminalInstance;

public class ServerInfo {
    public String name;
    public String path;
    public String type;
    public String version;
    public boolean isRunning;
    public TerminalInstance terminal;
    public ServerState state = ServerState.STOPPED;
}
