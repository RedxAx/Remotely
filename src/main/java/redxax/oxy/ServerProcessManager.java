package redxax.oxy;

import redxax.oxy.input.TerminalProcessManager;
import redxax.oxy.servers.ServerState;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

public class ServerProcessManager extends TerminalProcessManager {
    private final ServerTerminalInstance serverInstance;
    public static StringBuilder commandStr;

    public ServerProcessManager(ServerTerminalInstance terminalInstance) {
        super(terminalInstance, null);
        this.serverInstance = terminalInstance;
    }

    public static StringBuilder getCommandStr() {
        commandStr = new StringBuilder();
        commandStr.append("java ");
        commandStr.append("-D_server=root.Remotely ");
        commandStr.append("-Xms4G ");
        commandStr.append("-Xmx4G ");
        commandStr.append("-jar server.jar ");
        commandStr.append("--nogui");
        return commandStr;
    }

    @Override
    public void launchTerminal() {
        try {
            if (terminalProcess != null && terminalProcess.isAlive()) {
                shutdown();
            }
            File workingDir = new File(serverInstance.serverJarPath).getParentFile();
            File scriptFile = new File(workingDir, "start.bat");

            if (!scriptFile.exists()) {
                if (terminalInstance != null) terminalInstance.appendOutput("You Don't Have a start.bat, Creating One...\n");

                commandStr = getCommandStr();
                try (FileWriter fw = new FileWriter(scriptFile)) {
                    fw.write(commandStr.toString());
                }
                scriptFile.setExecutable(true);
            }

            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/k", "powershell", ".\\start.bat");
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            terminalProcess = pb.start();
            terminalInputStream = terminalProcess.getInputStream();
            terminalErrorStream = terminalProcess.getErrorStream();
            writer = new OutputStreamWriter(terminalProcess.getOutputStream(), StandardCharsets.UTF_8);
            startReaders();
            serverInstance.appendOutput("Server process started.\n");
        } catch (Exception e) {
            serverInstance.serverInfo.state = ServerState.CRASHED;
            serverInstance.appendOutput("Failed to launch server process: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}
