package redxax.oxy.input;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.TerminalInstance;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class CommandLogger {

    private static final Path COMMAND_LOG_PATH = Paths.get(System.getProperty("user.dir"), "remotely", "logs", "commands_global.log");
    private final TerminalInstance terminalInstance;
    private final Gson gson = new Gson();

    public CommandLogger(TerminalInstance terminalInstance) {
        this.terminalInstance = terminalInstance;
    }

    public void logCommand(String command) {
        try {
            List<String> commands = loadCommands();
            commands.add(command);
            String json = gson.toJson(commands);
            Files.writeString(COMMAND_LOG_PATH, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            terminalInstance.appendOutput("Failed to log command: " + e.getMessage() + "\n");
        }
    }

    public List<String> loadCommands() {
        try {
            if (Files.exists(COMMAND_LOG_PATH)) {
                String json = Files.readString(COMMAND_LOG_PATH);
                Type listType = new TypeToken<ArrayList<String>>() {}.getType();
                return gson.fromJson(json, listType);
            }
        } catch (IOException e) {
            terminalInstance.appendOutput("Failed to load commands: " + e.getMessage() + "\n");
        }
        return new ArrayList<>();
    }
}