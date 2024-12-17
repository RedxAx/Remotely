package redxax.oxy.input;

import redxax.oxy.SSHManager;
import redxax.oxy.TerminalInstance;
import redxax.oxy.ServerTerminalInstance;
import redxax.oxy.servers.ServerState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TerminalProcessManager {

    public Process terminalProcess;
    public InputStream terminalInputStream;
    public InputStream terminalErrorStream;
    public Writer writer;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private boolean isRunning = true;
    public final TerminalInstance terminalInstance;
    private final SSHManager sshManager;
    private String currentDirectory = System.getProperty("user.dir");
    private static final Logger logger = Logger.getLogger(TerminalProcessManager.class.getName());

    public TerminalProcessManager(TerminalInstance terminalInstance, SSHManager sshManager) {
        this.terminalInstance = terminalInstance;
        this.sshManager = sshManager;
    }

    public void launchTerminal() {
        try {
            if (terminalProcess != null && terminalProcess.isAlive()) {
                shutdown();
            }
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/k", "powershell");
            processBuilder.redirectErrorStream(true);
            terminalProcess = processBuilder.start();
            terminalInputStream = terminalProcess.getInputStream();
            terminalErrorStream = terminalProcess.getErrorStream();
            writer = new OutputStreamWriter(terminalProcess.getOutputStream(), StandardCharsets.UTF_8);
            startReaders();
        } catch (Exception e) {
            terminalInstance.appendOutput("Failed to launch terminal process: " + e.getMessage() + "\n");
            logger.log(Level.SEVERE, "Failed to launch terminal process", e);
        }
    }

    protected void startReaders() {
        executorService.submit(this::readTerminalOutput);
        executorService.submit(this::readErrorOutput);
    }

    private void readTerminalOutput() {
        try {
            byte[] buffer = new byte[1024];
            int numRead;
            StringBuilder outputBuffer = new StringBuilder();
            while (isRunning && (numRead = terminalInputStream.read(buffer)) != -1) {
                String text = new String(buffer, 0, numRead, StandardCharsets.UTF_8).replace("\u0000", "");
                outputBuffer.append(text);
                int index;
                while ((index = outputBuffer.indexOf("\n")) != -1) {
                    String line = outputBuffer.substring(0, index);
                    outputBuffer.delete(0, index + 1);
                    terminalInstance.appendOutput(line + "\n");
                    if (sshManager != null && sshManager.isSSH() && line.trim().equalsIgnoreCase("logout")) {
                        sshManager.shutdown();
                        terminalInstance.appendOutput("SSH session closed. Returned to local terminal.\n");
                    }
                    if (terminalInstance instanceof ServerTerminalInstance) {
                        detectServerState((ServerTerminalInstance)terminalInstance, line);
                    }
                    updateCurrentDirectory(line);
                }
            }
            if (!outputBuffer.isEmpty()) {
                String leftover = outputBuffer.toString();
                terminalInstance.appendOutput(leftover);
                if (sshManager != null && sshManager.isSSH() && leftover.trim().equalsIgnoreCase("logout")) {
                    sshManager.shutdown();
                    terminalInstance.appendOutput("SSH session closed. Returned to local terminal.\n");
                }
                if (terminalInstance instanceof ServerTerminalInstance) {
                    detectServerState((ServerTerminalInstance)terminalInstance, leftover);
                }
                updateCurrentDirectory(leftover);
            }
            if (terminalInstance instanceof ServerTerminalInstance) {
                ServerTerminalInstance sti = (ServerTerminalInstance) terminalInstance;
                if (sti.processManager.terminalProcess != null && !sti.processManager.terminalProcess.isAlive()) {
                    if (sti.serverInfo.state != ServerState.STOPPED && sti.serverInfo.state != ServerState.CRASHED) {
                        sti.serverInfo.state = ServerState.STOPPED;
                    }
                }
            }
        } catch (IOException e) {
            terminalInstance.appendOutput("Error reading terminal output: " + e.getMessage() + "\n");
            logger.log(Level.SEVERE, "Error reading terminal output", e);
        }
    }

    private void detectServerState(ServerTerminalInstance sti, String line) {
        if (line.contains("Done (")) {
            sti.serverInfo.state = ServerState.RUNNING;
        } else if (line.toLowerCase().contains("exception") || line.toLowerCase().contains("crash")) {
            sti.serverInfo.state = ServerState.CRASHED;
        } else if (line.toLowerCase().contains("stopping server") || line.toLowerCase().contains("server stopped")) {
            sti.serverInfo.state = ServerState.STOPPED;
        } else if (line.toLowerCase().contains("starting minecraft server") && sti.serverInfo.state == ServerState.STARTING) {
            // Keep it in STARTING until "Done" appears
        }
    }

    private void updateCurrentDirectory(String outputLine) {
        if (outputLine.startsWith("Directory: ")) {
            currentDirectory = outputLine.substring("Directory: ".length()).trim();
        }
    }

    private void readErrorOutput() {
        try {
            byte[] buffer = new byte[1024];
            int numRead;
            StringBuilder outputBuffer = new StringBuilder();
            while (isRunning && (numRead = terminalErrorStream.read(buffer)) != -1) {
                String text = new String(buffer, 0, numRead, StandardCharsets.UTF_8).replace("\u0000", "");
                outputBuffer.append(text);
                int index;
                while ((index = outputBuffer.indexOf("\n")) != -1) {
                    String line = outputBuffer.substring(0, index);
                    outputBuffer.delete(0, index + 1);
                    terminalInstance.appendOutput("ERROR: " + line + "\n");
                    if (terminalInstance instanceof ServerTerminalInstance) {
                        detectServerCrash((ServerTerminalInstance)terminalInstance, line);
                    }
                }
            }
            if (!outputBuffer.isEmpty()) {
                terminalInstance.appendOutput("ERROR: " + outputBuffer);
                if (terminalInstance instanceof ServerTerminalInstance) {
                    detectServerCrash((ServerTerminalInstance)terminalInstance, outputBuffer.toString());
                }
            }
        }  catch (IOException e) {
            terminalInstance.appendOutput("Error reading terminal error output: " + e.getMessage() + "\n");
            logger.log(Level.SEVERE, "Error reading terminal error output", e);
        }
    }

    private void detectServerCrash(ServerTerminalInstance sti, String line) {
        if (line.toLowerCase().contains("exception") || line.toLowerCase().contains("crash")) {
            sti.serverInfo.state = ServerState.CRASHED;
        }
    }

    public Writer getWriter() {
        return writer;
    }

    public void shutdown() {
        isRunning = false;
        if (terminalProcess != null && terminalProcess.isAlive()) {
            try {
                long pid = terminalProcess.pid();
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/PID", Long.toString(pid), "/T", "/F");
                Process killProcess = pb.start();
                killProcess.waitFor();
                terminalInstance.appendOutput("Terminal process and its child processes terminated.\n");
            } catch (IOException | InterruptedException e) {
                terminalInstance.appendOutput("Error shutting down terminal: " + e.getMessage() + "\n");
                logger.log(Level.SEVERE, "Error shutting down terminal", e);
            }
            terminalProcess = null;
        }
        if (sshManager != null) {
            sshManager.shutdown();
        }
        executorService.shutdownNow();
        terminalInstance.appendOutput("Terminal closed.\n");
    }

    public void saveTerminalOutput(Path path) throws IOException {
        Files.writeString(path, terminalInstance.renderer.getTerminalOutput().toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void loadTerminalOutput(Path path) throws IOException {
        String content = Files.readString(path);
        synchronized (terminalInstance.renderer.getTerminalOutput()) {
            terminalInstance.renderer.getTerminalOutput().append(content);
        }
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(String currentDirectory) {
        this.currentDirectory = currentDirectory;
    }

    public OutputStream getOutputStream() {
        return terminalProcess.getOutputStream();
    }
}
