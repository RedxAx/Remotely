package redxax.oxy.remotely.terminal;

import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerState;
import redxax.oxy.remotely.ui.widgets.TerminalWidget;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class TerminalProcessManager {
    public Process terminalProcess;
    public InputStream terminalInputStream;
    public InputStream terminalErrorStream;
    public Writer writer;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private volatile boolean isRunning = true;
    protected final TerminalWidget widget;
    private final SSHManager sshManager;
    private String currentDirectory = System.getProperty("user.home");
    private static final Logger logger = Logger.getLogger(TerminalProcessManager.class.getName());
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

    public void launchTerminal() {
        if (isDetachedServer) {
            launchServerProcess();
        } else {
            launchGenericProcess();
        }
    }

    private void launchGenericProcess() {
        new Thread(() -> {
            try {
                if (terminalProcess != null && terminalProcess.isAlive()) {
                    shutdown();
                }
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder processBuilder;
                if (os.contains("win")) {
                    processBuilder = new ProcessBuilder("cmd.exe");
                } else if (os.contains("mac") || os.contains("darwin")) {
                    processBuilder = new ProcessBuilder("/bin/zsh", "-l");
                } else {
                    processBuilder = new ProcessBuilder("/bin/bash", "-l");
                }
                processBuilder.redirectErrorStream(true);
                processBuilder.directory(new File(currentDirectory));
                terminalProcess = processBuilder.start();
                setupStreamsAndReaders();
            } catch (Exception e) {
                widget.appendOutput("Failed to launch terminal process: " + e.getMessage() + "\n");
                logger.log(Level.SEVERE, "Failed to launch terminal process", e);
            }
        }, "Terminal-Launcher-Generic").start();
    }

    private void launchServerProcess() {
        new Thread(() -> {
            try {
                if (terminalProcess != null && terminalProcess.isAlive()) {
                    shutdown();
                }

                ServerInfo serverInfo = widget.getServerInfo();
                File workingDir = new File(serverInfo.path).getParentFile();
                if (workingDir == null || !workingDir.exists()) {
                    widget.appendOutput("Server directory not found: " + serverInfo.path);
                    return;
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
                    scriptFile.setExecutable(true);
                }

                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder mainProcess;
                if (os.contains("win")) {
                    mainProcess = new ProcessBuilder("cmd.exe", "/c", scriptFile.getName());
                } else {
                    mainProcess = new ProcessBuilder("/bin/bash", "-l", "-c", "./" + scriptFile.getName());
                }

                mainProcess.directory(workingDir);
                mainProcess.redirectErrorStream(true);
                terminalProcess = mainProcess.start();
                setupStreamsAndReaders();
            } catch (Exception e) {
                widget.setServerState(ServerState.CRASHED);
                widget.appendOutput("Failed to launch server process: " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        }, "Terminal-Launcher-Server").start();
    }

    private void setupStreamsAndReaders() {
        terminalInputStream = terminalProcess.getInputStream();
        terminalErrorStream = terminalProcess.getErrorStream();
        writer = new OutputStreamWriter(terminalProcess.getOutputStream(), StandardCharsets.UTF_8);
        startReaders();
    }

    protected void startReaders() {
        executorService.submit(this::readTerminalOutput);
    }

    private void readTerminalOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(terminalInputStream, StandardCharsets.UTF_8))) {
            String line;
            while (isRunning && (line = reader.readLine()) != null) {
                widget.appendOutput(line + "\n");
                if (sshManager != null && sshManager.isSSH() && line.trim().equalsIgnoreCase("logout")) {
                    sshManager.shutdown();
                    widget.appendOutput("SSH session closed. Returned to local terminal.\n");
                }
            }
        } catch (IOException e) {
            if (isRunning) { // Avoid error message on normal shutdown
                widget.appendOutput("Error reading terminal output: " + e.getMessage() + "\n");
                logger.log(Level.SEVERE, "Error reading terminal output", e);
            }
        } finally {
            if (widget.getServerInfo() != null && isRunning) {
                if (widget.getServerInfo().state != ServerState.STOPPED) {
                    widget.setServerState(ServerState.CRASHED);
                }
            }
        }
    }

    public Writer getWriter() {
        return writer;
    }

    public void shutdown() {
        isRunning = false;
        if (isDetachedServer && terminalProcess != null && terminalProcess.isAlive()) {
            devPrint("Shutting down server process...");
            try {
                if (writer != null) {
                    writer.write("stop\n");
                    writer.flush();
                    // Give server time to shut down gracefully
                    terminalProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (IOException | InterruptedException e) {
                devPrint("Error during graceful shutdown: " + e.getMessage());
            }
        }

        if (terminalProcess != null && terminalProcess.isAlive()) {
            devPrint("Forcibly destroying process.");
            terminalProcess.destroyForcibly();
        }

        if (sshManager != null) {
            sshManager.shutdown();
        }
        executorService.shutdownNow();

        if (isDetachedServer) {
            widget.appendOutput("Server process terminated.\n");
        } else {
            widget.appendOutput("Terminal closed.\n");
        }
    }

    public void saveTerminalOutput(Path path) throws IOException {
        widget.saveTerminalOutput(path);
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(String currentDirectory) {
        this.currentDirectory = currentDirectory;
    }
}