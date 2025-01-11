package redxax.oxy;

import redxax.oxy.input.TerminalProcessManager;
import redxax.oxy.servers.ServerState;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static redxax.oxy.DevUtil.devPrint;

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
            // If an old process is alive, shut it down first.
            if (terminalProcess != null && terminalProcess.isAlive()) {
                shutdown();
            }

            File workingDir = new File(serverInstance.serverJarPath).getParentFile();
            File scriptFile = new File(workingDir, "start.bat");

            if (!scriptFile.exists()) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("You don't have a start.bat, creating one...\n");
                }
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

    @Override
    public void shutdown() {
        devPrint("Shutting down server process...");
        try {
            if (writer != null) {
                serverInstance.appendOutput("Sending stop command...\n");
                writer.write("stop\n");
                writer.flush();
            }
        } catch (IOException e) {
            devPrint("Error sending stop command: " + e.getMessage());
        }

        if (terminalProcess != null && terminalProcess.isAlive()) {
            try {
                long pid = terminalProcess.pid();
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/PID", Long.toString(pid), "/T", "/F");
                Process killProcess = pb.start();
                killProcess.waitFor();
                devPrint("Server process killed.");
            } catch (IOException | InterruptedException e) {
                devPrint("Failed to kill server process: " + e.getMessage());
            }
        }
    }
}
