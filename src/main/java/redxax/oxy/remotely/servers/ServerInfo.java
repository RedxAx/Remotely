package redxax.oxy.remotely.servers;

import java.util.Objects;
import java.util.Properties;

public class ServerInfo {
    public String name;
    public String path;
    public String type;
    public String version;
    public boolean isRunning;
    public ServerState state = ServerState.STOPPED;
    public boolean isRemote = false;
    public RemoteHostInfo remoteHost;

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
        return type.equalsIgnoreCase("paper") ||
                type.equalsIgnoreCase("spigot") ||
                type.equalsIgnoreCase("leaf") ||
                type.equalsIgnoreCase("purpur");
    }
    public boolean isProxyServer() {
        return type.equalsIgnoreCase("bungee") ||
                type.equalsIgnoreCase("waterfall") ||
                type.equalsIgnoreCase("velocity");
    }

    public String getVersion() {
        return version;
    }

    public int getPort() {
        if (isRemote) {
            // This logic is likely incorrect for fetching a remote server's port,
            // but is preserved from the original to limit scope. It should ideally
            // parse server.properties on the remote host.
            return remoteHost.getPort();
        } else {
            Properties properties = new Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(path + "/server.properties")) {
                properties.load(fis);
                String portStr = properties.getProperty("server-port");
                if (portStr != null) {
                    return Integer.parseInt(portStr);
                }
            } catch (Exception ignored) {}
            return -1;

        }
    }
}