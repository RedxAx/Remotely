package redxax.oxy.remotely.terminal;

import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerState;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TerminalProcessManager {
    private static final Logger logger = Logger.getLogger(TerminalProcessManager.class.getName());

    protected final TerminalWidget widget;
    private final SSHManager sshManager;
    private String currentDirectory = System.getProperty("user.home");
    protected boolean isDetachedServer = false;

    public TerminalProcessManager(TerminalWidget widget, SSHManager sshManager) {
        this.widget = widget;
        this.sshManager = sshManager;
        this.isDetachedServer = widget.getServerInfo() != null && !widget.getServerInfo().isRemote;
    }

    public static StringBuilder getCommandStr() {
        StringBuilder commandStr = new StringBuilder();
        commandStr.append("java ");
        commandStr.append("-Djline.terminal=jline.UnsupportedTerminal ");
        commandStr.append("-Dnet.kyori.ansi.colorLevel=indexed256 ");
        commandStr.append("-Xms4G ");
        commandStr.append("-Xmx4G ");
        commandStr.append("-jar server.jar ");
        commandStr.append("--nogui");
        return commandStr;
    }

    public TtyConnector createTtyConnector() throws IOException {
        PtyProcess process;
        if (isDetachedServer) {
            process = launchServerProcess();
        } else {
            process = launchGenericProcess();
        }
        return new PtyProcessTtyConnector(process, StandardCharsets.UTF_8);
    }

    public PtyProcess launchGenericProcess() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            Map<String, String> env = new HashMap<>(System.getenv());
            if (os.contains("win")) {
                command = new String[]{"powershell.exe", "-NoLogo"};
            } else {
                String shell = env.getOrDefault("SHELL", "/bin/bash");
                if (os.contains("mac") || os.contains("darwin")) {
                    command = new String[]{shell, "-l"};
                } else {
                    command = new String[]{shell, "-l"};
                }
                env.put("TERM", "xterm-256color");
            }

            PtyProcessBuilder builder = new PtyProcessBuilder(command)
                    .setEnvironment(env)
                    .setDirectory(currentDirectory)
                    .setRedirectErrorStream(true);

            if (os.contains("win")) {
                builder.setConsole(false).setUseWinConPty(true);
            }

            return builder.start();
        } catch (Exception e) {
            widget.appendOutput("Failed to launch terminal process: " + e.getMessage() + "\n");
            logger.log(Level.SEVERE, "Failed to launch terminal process", e);
        }
        return null;
    }

    public PtyProcess launchServerProcess() {
        try {
            ServerInfo serverInfo = widget.getServerInfo();
            if (serverInfo == null) {
                throw new IOException("ServerInfo is null for a detached server process");
            }
            File workingDir = new File(serverInfo.path);
            if (!workingDir.exists() || !workingDir.isDirectory()) {
                widget.appendOutput("Server directory not found or is not a directory: " + serverInfo.path);
                throw new IOException("Server directory not found: " + serverInfo.path);
            }

            File scriptFile = new File(workingDir, "start.bat");
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                scriptFile = new File(workingDir, "start.sh");
            }

            if (!scriptFile.exists()) {
                widget.appendOutput("No start script found, creating one...\n");
                try (FileWriter fw = new FileWriter(scriptFile)) {
                    fw.write(getCommandStr().toString());
                }
                if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                    //noinspection ResultOfMethodCallIgnored
                    scriptFile.setExecutable(true, true);
                }
            }

            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            if (os.contains("win")) {
                command = new String[]{"cmd.exe", "/c", scriptFile.getName()};
            } else {
                command = new String[]{"/bin/bash", "-l", "-c", "./" + scriptFile.getName()};
            }
            Map<String, String> env = new HashMap<>(System.getenv());
            env.put("TERM", "xterm-256color");

            PtyProcessBuilder builder = new PtyProcessBuilder(command)
                    .setEnvironment(env)
                    .setDirectory(workingDir.getAbsolutePath())
                    .setRedirectErrorStream(true);

            if (os.contains("win")) {
                builder.setConsole(false).setUseWinConPty(true);
            }

            return builder.start();
        } catch (Exception e) {
            if (widget.getServerInfo() != null) {
                widget.setServerState(ServerState.CRASHED);
            }
            widget.appendOutput("Failed to launch server process: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
        return null;
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(String currentDirectory) {
        this.currentDirectory = currentDirectory;
    }
}