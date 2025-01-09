package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import redxax.oxy.servers.ServerInfo;
import redxax.oxy.servers.ServerState;
import redxax.oxy.input.TerminalProcessManager;
import java.nio.file.Paths;
import java.util.UUID;

public class ServerTerminalInstance extends TerminalInstance {
    public boolean isServerTerminal = false;
    public String serverJarPath;
    public String serverName = "";
    public ServerInfo serverInfo;
    public TerminalProcessManager processManager;

    public ServerTerminalInstance(MinecraftClient mc, MultiTerminalScreen screen, UUID id, ServerInfo sInfo) {
        super(mc, screen, id);
        this.serverInfo = sInfo;
        if (serverInfo.isRemote) {
            this.serverJarPath = serverInfo.path.replace("\\", "/") + "/server.jar";

        } else {
            this.serverJarPath = Paths.get(serverInfo.path, "server.jar").toString().replace("\\", "/");
        }
    }

    @Override
    public void launchServerProcess() {
        if (serverInfo.isRemote && serverInfo.remoteHost != null) {
            serverInfo.state = ServerState.STARTING;
            if (serverInfo.remoteSSHManager == null) {
                serverInfo.remoteSSHManager = new SSHManager(serverInfo.remoteHost);
            }
            serverInfo.remoteSSHManager.setTerminalInstance(this);
            serverInfo.remoteSSHManager.connectToRemoteHost(
                    serverInfo.remoteHost.getUser(),
                    serverInfo.remoteHost.ip,
                    serverInfo.remoteHost.port,
                    serverInfo.remoteHost.password
            );
            serverInfo.remoteSSHManager.connectSFTP();
            serverInfo.remoteSSHManager.launchRemoteServer(serverInfo.path, serverJarPath);
            super.setServerInfo(this.serverInfo);
        } else {
            if (processManager != null) {
                processManager.shutdown();
            }
            serverInfo.state = ServerState.STARTING;
            processManager = new ServerProcessManager(this);
            processManager.setCurrentDirectory(serverJarPath.replace("server.jar",""));
            processManager.launchTerminal();
        }
    }

    public void clearOutput() {
        renderer.clearOutput();
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }
}
