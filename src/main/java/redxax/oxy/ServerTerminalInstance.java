package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import redxax.oxy.servers.ServerInfo;
import redxax.oxy.servers.ServerState;
import redxax.oxy.input.TerminalProcessManager;
import java.util.UUID;

public class ServerTerminalInstance extends TerminalInstance {
    public boolean isServerTerminal = false;
    public String serverJarPath = "";
    public String serverName = "";
    public ServerInfo serverInfo;
    public TerminalProcessManager processManager;

    public ServerTerminalInstance(MinecraftClient mc, MultiTerminalScreen screen, UUID id, ServerInfo sInfo) {
        super(mc, screen, id);
        this.serverInfo = sInfo;
        this.serverJarPath = java.nio.file.Paths.get(serverInfo.path, "server.jar").toString().replace("\\", "/");
    }

    @Override
    public void launchServerProcess() {
        if (processManager != null) {
            processManager.shutdown();
        }
        serverInfo.state = ServerState.STARTING;
        processManager = new ServerProcessManager(this);
        processManager.setCurrentDirectory(serverJarPath.replace("server.jar",""));
        processManager.launchTerminal();
    }
}
