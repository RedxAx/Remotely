package redxax.oxy.servers;

import redxax.oxy.SSHManager;
import redxax.oxy.terminal.TerminalInstance;

import java.util.Objects;

public class ServerInfo {
    public String name;
    public String path;
    public String type;
    public String version;
    public boolean isRunning;
    public TerminalInstance terminal;
    public ServerState state = ServerState.STOPPED;
    public boolean isRemote = false;
    public RemoteHostInfo remoteHost;
    public SSHManager remoteSSHManager;

    public ServerInfo(String path) {
        this.path = path;
    }

    public ServerInfo(boolean b, RemoteHostInfo remoteHostInfo, String s) {
        this.isRemote = b;
        this.remoteHost = remoteHostInfo;
        this.path = s;
    }

    public boolean isModServer() {
        return Objects.equals(type, "forge") || Objects.equals(type, "fabric") || Objects.equals(type, "neoforge");
    }

    public boolean isPluginServer() {
        return Objects.equals(type, "paper");
    }

    public String getVersion() {
        return version;
    }
}
