package redxax.oxy.remotely.servers;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import net.minecraft.client.MinecraftClient;
import redxax.oxy.remotely.config.Config;
import restudio.rescreen.util.Notification;

import java.util.HashMap;
import java.util.Map;

public class ReverseProxyManager {
    private static final Map<Integer, Session> activeSessions = new HashMap<>();

    public static void reverse(int localPort) {
        Notification notification = new Notification("Reversing " + localPort + "...", Notification.Type.INFO);
        notification.autoSlideOut = false;
        notification.loading = true;
        if (activeSessions.containsKey(localPort)) {
            Session existing = activeSessions.get(localPort);
            if (existing.isConnected()) {
                notification.change("Port " + localPort + " Is Already being forwarded", "Click To Disconnect", Notification.Type.WARN, () -> shutdown(localPort));
                notification.loading = false;
                return;
            }
        }
        String host;
        String user;
        if (Config.customReverseProxy){
            host = Config.proxyHost;
            user = Config.proxyUser;
        } else {
            host = "RedxAx.net";
            user = "tunnel";
        }
        int port = 722;
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(user, host, port);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect(10000);
                activeSessions.put(localPort, session);
                startReverseProxy(session, localPort, notification);
            } catch (JSchException e) {
                notification.change("Error Setting Up Reverse Proxy: ", e.getMessage(), Notification.Type.ERROR, null);
                notification.loading = false;
            }
        }, "Reverse Proxy Thread").start();
    }

    private static void startReverseProxy(Session session, int localPort, Notification notification) {
        try {
            session.setPortForwardingR(0, "localhost", localPort);
            String[] portForwarding = session.getPortForwardingR();
            String mappingStr = null;
            for (String mapping : portForwarding) {
                String[] parts = mapping.split(":");
                if (parts.length == 3) {
                    int lp = Integer.parseInt(parts[2]);
                    if (lp == localPort) {
                        mappingStr = mapping;
                        break;
                    }
                }
            }
            if (mappingStr == null) {
                throw new JSchException("No forwarding found for port " + localPort);
            }
            int allocatedPort = Integer.parseInt(mappingStr.split(":")[0]);
            notification.change("Reverse Proxy Started on Port " + allocatedPort, "Click To Copy The IP", Notification.Type.SUCCESS, () -> MinecraftClient.getInstance().keyboard.setClipboard(Config.proxyHost + ":" + allocatedPort));
            notification.loading = false;
        } catch (JSchException e) {
            notification.change("Error Setting Up Reverse Proxy: ", e.getMessage(), Notification.Type.ERROR, null);
            notification.loading = false;
        }
    }

    public static void shutdown(int localPort) {
        Session session = activeSessions.get(localPort);
        if (session != null && session.isConnected()) {
            try {
                String[] portForwarding = session.getPortForwardingR();
                String mappingStr = null;
                for (String mapping : portForwarding) {
                    String[] parts = mapping.split(":");
                    if (parts.length == 3) {
                        int lp = Integer.parseInt(parts[2]);
                        if (lp == localPort) {
                            mappingStr = mapping;
                            break;
                        }
                    }
                }
                if (mappingStr != null) {
                    int allocatedPort = Integer.parseInt(mappingStr.split(":")[0]);
                    session.delPortForwardingR(allocatedPort);
                }
                session.disconnect();
                activeSessions.remove(localPort);
                new Notification("Reverse Proxy for port " + localPort + " shutdown successfully", Notification.Type.INFO);
            } catch (JSchException e) {
                new Notification("Error Shutting Down Reverse Proxy: ", e.getMessage(), Notification.Type.ERROR);
            }
        } else {
            new Notification("Port " + localPort + " Isn't Forwarded", Notification.Type.WARN);
        }
    }

    public static void shutdownAll() {
        for (Map.Entry<Integer, Session> entry : activeSessions.entrySet()) {
            int localPort = entry.getKey();
            Session session = entry.getValue();
            if (session.isConnected()) {
                try {
                    String[] portForwarding = session.getPortForwardingR();
                    String mappingStr = null;
                    for (String mapping : portForwarding) {
                        String[] parts = mapping.split(":");
                        if (parts.length == 3 && Integer.parseInt(parts[2]) == localPort) {
                            mappingStr = mapping;
                            break;
                        }
                    }
                    if (mappingStr != null) {
                        int allocatedPort = Integer.parseInt(mappingStr.split(":")[0]);
                        session.delPortForwardingR(allocatedPort);
                    }
                    session.disconnect();
                } catch (JSchException e) {
                    new Notification("Error during shutdown: ", e.getMessage(), Notification.Type.ERROR);
                }
            }
        }
        activeSessions.clear();
        new Notification("All reverse proxies shutdown successfully", Notification.Type.INFO);
    }

    public static void listActivePorts() {
        if (activeSessions.isEmpty()) {
            new Notification("No active reverse proxies", Notification.Type.INFO);
            return;
        }
        StringBuilder ports = new StringBuilder();
        for (Map.Entry<Integer, Session> entry : activeSessions.entrySet()) {
            int localPort = entry.getKey();
            Session session = entry.getValue();
            if (session.isConnected()) {
                ports.append(localPort).append(" Active,");
            } else {
                ports.append(localPort).append(" Inactive,");
            }
        }
        new Notification("Ports Forwarded:", ports.toString(), Notification.Type.INFO);
    }

    public static boolean isPortForwarded(int localPort) {
        return activeSessions.containsKey(localPort) && activeSessions.get(localPort).isConnected();
    }

    public static void reverse(ServerInfo sInfo) {
        if (sInfo == null || sInfo.getPort() <= 0) {
            new Notification("Invalid Server Port", "Make Sure To Configure The Port Correctly", Notification.Type.ERROR);
        } else {
            if (isPortForwarded(sInfo.getPort())) {
                shutdown(sInfo.getPort());
            } else {
                reverse(sInfo.getPort());
            }
        }
    }
}
