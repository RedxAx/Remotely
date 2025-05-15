package redxax.oxy.remotely.servers;

import redxax.oxy.remotely.terminal.ServerTerminalInstance;
import redxax.oxy.remotely.terminal.TerminalProcessManager;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

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
        commandStr.append("-Djline.terminal=jline.UnsupportedTerminal ");
        commandStr.append("-Dnet.kyori.ansi.colorLevel=indexed256 ");
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

            if (scriptFile.exists()) {
                String content = Files.readString(scriptFile.toPath());
                if (!content.contains("-Djline.terminal=jline.UnsupportedTerminal")) {
                    content = content.replaceFirst("(?i)^(java\\s+)", "$1-Djline.terminal=jline.UnsupportedTerminal ");
                    Files.writeString(scriptFile.toPath(), content);
                    devPrint("Added jline terminal flag to start.bat.");
                }
                if (!content.contains("-Dnet.kyori.ansi.colorLevel=indexed256")) {
                    content = content.replaceFirst("(?i)^(java\\s+)", "$1-Dnet.kyori.ansi.colorLevel=indexed256 ");
                    Files.writeString(scriptFile.toPath(), content);
                    devPrint("Added ANSI color flag to start.bat.");
                }
            } else {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("No start.bat found, creating one with the required flag...\n");
                }
                commandStr = getCommandStr();
                try (FileWriter fw = new FileWriter(scriptFile)) {
                    fw.write(commandStr.toString());
                }
                scriptFile.setExecutable(true);
            }

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder mainProcess;
            if (os.contains("mac") || os.contains("nix") || os.contains("nux") || os.contains("darwin")) {
                mainProcess = new ProcessBuilder("/bin/bash", "-l", "-c", "./start.sh");
            } else if (os.contains("win")) {
                mainProcess = new ProcessBuilder("cmd.exe", "/c", ".\\start.bat");
            } else {
                mainProcess = new ProcessBuilder("/bin/bash", "-l", "-c", "./start.sh");
            }
            mainProcess.directory(workingDir);
            mainProcess.redirectErrorStream(true);
            terminalProcess = mainProcess.start();
            terminalInputStream = terminalProcess.getInputStream();
            terminalErrorStream = terminalProcess.getErrorStream();
            writer = new OutputStreamWriter(terminalProcess.getOutputStream(), Charset.defaultCharset());
            startReaders();
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
