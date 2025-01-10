package redxax.oxy.servers;

import redxax.oxy.SSHManager;
import java.util.List;

public class RemoteHostInfo {
    public String name;
    public String ip;
    public int port;
    public String password;
    public List<ServerInfo> servers;
    public String user;
    public boolean isConnecting = false;
    public boolean isConnected = false;
    public String connectionError = null;
    public SSHManager sshManager;

    public void setUser(String user) {
        this.user = user;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setPassword(String password) {
        this.password = password;
    }

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


    public String getHomeDirectory() {
        if ("root".equals(this.user)) {
            return "/root";
        } else {
            return "/home/" + this.user;
        }
    }
}
