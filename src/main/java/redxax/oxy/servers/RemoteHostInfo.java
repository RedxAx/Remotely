package redxax.oxy.servers;

import redxax.oxy.SSHManager;
import java.util.List;

public class RemoteHostInfo {
    public String name;
    public String ip;
    public int port;
    public String password;
    public List<ServerInfo> servers;
    String user;
    public boolean isConnecting = false;
    public boolean isConnected = false;
    public String connectionError = null;
    public SSHManager sshManager;

    public String getUser() {
        return this.user;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getPassword() {
        return password;
    }
}
