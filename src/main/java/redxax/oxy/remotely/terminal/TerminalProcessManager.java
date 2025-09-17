package redxax.oxy.remotely.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import restudio.rebase.instance.Instance;

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

    private TerminalProcessManager() {
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

    public static TtyConnector createTtyConnector(Instance serverInfo, String workingDir, TermSize initialSize) throws IOException {
        PtyProcess process;
        if (serverInfo != null) {
            process = launchServerProcess(serverInfo, initialSize);
        } else {
            process = launchGenericProcess(initialSize, workingDir);
        }
        if (process == null) {
            throw new IOException("Failed to create PtyProcess");
        }
        return new PtyProcessTtyConnector(process, StandardCharsets.UTF_8);
    }

    public static PtyProcess launchGenericProcess(TermSize initialSize, String workingDir) throws IOException {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            Map<String, String> env = new HashMap<>(System.getenv());
            if (os.contains("win")) {
                command = new String[]{"powershell.exe", "-NoLogo"};
            } else {
                String shell = env.getOrDefault("SHELL", "/bin/bash");
                command = new String[]{shell, "-l"};
                env.put("TERM", "xterm-256color");
            }

            PtyProcessBuilder builder = new PtyProcessBuilder(command)
                    .setEnvironment(env)
                    .setDirectory(workingDir)
                    .setRedirectErrorStream(true);

            if (os.contains("win")) {
                builder.setConsole(false).setUseWinConPty(true);
            }

            if (initialSize.getColumns() > 0 && initialSize.getRows() > 0) {
                builder.setInitialColumns(initialSize.getColumns());
                builder.setInitialRows(initialSize.getRows());
            }

            return builder.start();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to launch terminal process", e);
            throw new IOException("Failed to launch terminal process: " + e.getMessage(), e);
        }
    }

    public static PtyProcess launchServerProcess(Instance serverInfo, TermSize initialSize) throws IOException {
        try {
            if (serverInfo == null) {
                throw new IOException("Instance is null for a detached server process");
            }
            File workingDir = new File(serverInfo.getPath());
            if (!workingDir.exists() || !workingDir.isDirectory()) {
                throw new IOException("Server directory not found or is not a directory: " + serverInfo.getPath());
            }

            File scriptFile = new File(workingDir, "start.bat");
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                scriptFile = new File(workingDir, "start.sh");
            }

            if (!scriptFile.exists()) {
                try (FileWriter fw = new FileWriter(scriptFile)) {
                    fw.write(getCommandStr().toString());
                }
                if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                    scriptFile.setExecutable(true, true);
                }
            }

            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            if (os.contains("win")) {
                command = new String[]{"cmd.exe", "/c", "start.bat"};
            } else {
                command = new String[]{"/bin/bash", "-l", "./start.sh"};
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

            if (initialSize.getColumns() > 0 && initialSize.getRows() > 0) {
                builder.setInitialColumns(initialSize.getColumns());
                builder.setInitialRows(initialSize.getRows());
            }

            return builder.start();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to launch server process", e);
            throw new IOException("Failed to launch server process: " + e.getMessage(), e);
        }
    }
}