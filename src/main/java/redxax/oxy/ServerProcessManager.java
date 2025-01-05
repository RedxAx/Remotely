package redxax.oxy;

import redxax.oxy.input.TerminalProcessManager;
import redxax.oxy.servers.ServerState;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ServerProcessManager extends TerminalProcessManager {
    private final ServerTerminalInstance serverInstance;

    public ServerProcessManager(ServerTerminalInstance terminalInstance) {
        super(terminalInstance, null);
        this.serverInstance = terminalInstance;
    }

    public void extract() throws IOException {
        Path localScriptPath = Paths.get("C:\\remotely\\scripts\\");
        Path assetsPath = Paths.get("/assets/remotely/scripts/");
        Path shScriptPath = assetsPath.resolve("start.sh");
        Path batScriptPath = assetsPath.resolve("start.bat");

        if (!Files.exists(localScriptPath)) {
            Files.createDirectories(localScriptPath);
        }

        try (InputStream shStream = getClass().getResourceAsStream(shScriptPath.toString());
             InputStream batStream = getClass().getResourceAsStream(batScriptPath.toString())) {

            if (shStream == null || batStream == null) {
                throw new IOException("Script resources not found.");
            }

            Files.copy(shStream, localScriptPath.resolve("start.sh"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(batStream, localScriptPath.resolve("start.bat"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void launchTerminal() {

        try {
            extract();
                if (terminalProcess != null && terminalProcess.isAlive()) {
                    shutdown();
                }
                String scriptName = System.getProperty("os.name").toLowerCase().contains("win") ? "start.bat" : "start.sh";
                Path scriptPath = Paths.get(serverInstance.serverJarPath).getParent().resolve(scriptName);
                if (!Files.exists(scriptPath)) {
                    copyScriptFromResources(scriptName, scriptPath);
                }
                File workingDir = scriptPath.getParent().toFile();
                ProcessBuilder pb = new ProcessBuilder(scriptPath.toString());
                if (workingDir.exists()) {
                    pb.directory(workingDir);
                }
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
        }
    }

    private void copyScriptFromResources(String scriptName, Path destination) throws IOException {
        try (InputStream resourceStream = getClass().getResourceAsStream("/assets/remotely/scripts/" + scriptName)) {
            if (resourceStream == null) {
                throw new IOException("Resource not found: " + scriptName);
            }
            Files.copy(resourceStream, destination);
        }
    }
}