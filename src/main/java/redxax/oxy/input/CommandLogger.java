package redxax.oxy.input;

import redxax.oxy.TerminalInstance;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

public class CommandLogger {

    private static final Path COMMAND_LOG_DIR = Paths.get(System.getProperty("user.dir"), "remotely_command_logs");
    private final Path commandLogPath;
    private final TerminalInstance terminalInstance;

    public CommandLogger(TerminalInstance terminalInstance) {
        this.terminalInstance = terminalInstance;
        UUID terminalId = terminalInstance.terminalId;
        this.commandLogPath = COMMAND_LOG_DIR.resolve("commands_" + terminalId.toString() + ".log");
    }

    public void logCommand(String command) {
        try {
            if (!Files.exists(COMMAND_LOG_DIR)) {
                Files.createDirectories(COMMAND_LOG_DIR);
            }
            Files.writeString(commandLogPath, command + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            terminalInstance.appendOutput("Failed to log command: " + e.getMessage() + "\n");
        }
    }
}
