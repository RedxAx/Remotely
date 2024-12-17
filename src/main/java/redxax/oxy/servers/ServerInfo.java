package redxax.oxy.servers;

import redxax.oxy.TerminalInstance;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class ServerInfo {
    public String name;
    public String path;
    public String type;
    public String version;
    public int maxPlayers = 20;
    public int port = 25565;
    public boolean onlineMode = true;
    public boolean isRunning;
    public TerminalInstance terminal;
    public ServerState state = ServerState.STOPPED;
    public float ramGb;

    public boolean isModServer() {
        return Objects.equals(type, "forge") || Objects.equals(type, "fabric") || Objects.equals(type, "neoforge");
    }
    public Path getServerPropertiesPath() {
        return Paths.get(path, "server.properties");
    }
}
