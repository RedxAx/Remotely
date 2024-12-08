package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class InputHandler {

    private Process terminalProcess;
    private InputStream terminalInputStream;
    private InputStream terminalErrorStream;
    private Writer writer;
    private final StringBuilder inputBuffer = new StringBuilder();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private boolean isRunning = true;
    private final MinecraftClient minecraftClient;
    private final TerminalInstance terminalInstance;

    private int cursorPosition = 0;

    private final UUID terminalId;
    private static final Path COMMAND_LOG_DIR = Paths.get(System.getProperty("user.dir"), "remotely_command_logs");
    private final Path commandLogPath;

    private final SSHManager sshManager;

    private List<String> tabCompletions = new ArrayList<>();
    private int tabCompletionIndex = -1;
    private String tabCompletionPrefix = "";
    private String tabCompletionOriginalInput = "";
    private String tabCompletionSuggestion = "";

    private String currentDirectory = System.getProperty("user.dir");

    private List<String> allCommands = new ArrayList<>();
    private long commandsLastFetched = 0;
    private static final long COMMANDS_CACHE_DURATION = 60 * 1000;

    private boolean isMinecraftServerDetected = false;
    private boolean isMinecraftServerLoaded = false;

    private boolean collectingServerCommands = false;
    private boolean readingCommands = false;
    private List<String> serverCommands = new ArrayList<>();

    public InputHandler(MinecraftClient client, TerminalInstance terminalInstance) {
        this.minecraftClient = client;
        this.terminalInstance = terminalInstance;
        this.terminalId = terminalInstance.terminalId;
        this.commandLogPath = COMMAND_LOG_DIR.resolve("commands_" + terminalId.toString() + ".log");
        this.sshManager = terminalInstance.getSSHManager();
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
            e.printStackTrace();
        }
    }

    private void startReaders() {
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
                    updateCurrentDirectory(line);
                    if (!isMinecraftServerDetected) {
                        if (line.contains("Starting minecraft server")) {
                            isMinecraftServerDetected = true;
                        }
                    } else if (!isMinecraftServerLoaded) {
                        if (line.contains("Done") && line.contains("For help, type \"help\"")) {
                            isMinecraftServerLoaded = true;
                        }
                    } else if (collectingServerCommands) {
                        if (line.startsWith("----")) {
                            readingCommands = !readingCommands;
                            continue;
                        }
                        if (readingCommands) {
                            String cmd = line.trim().split(" ")[0];
                            serverCommands.add(cmd);
                        }
                        if (line.contains("For help, type \"help\"")) {
                            collectingServerCommands = false;
                            synchronized (this) {
                                allCommands = new ArrayList<>(serverCommands);
                            }
                        }
                    }
                }
            }
            if (outputBuffer.length() > 0) {
                terminalInstance.appendOutput(outputBuffer.toString());
                updateCurrentDirectory(outputBuffer.toString());
            }
        } catch (IOException e) {
            terminalInstance.appendOutput("Error reading terminal output: " + e.getMessage() + "\n");
            e.printStackTrace();
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
                }
            }
            if (outputBuffer.length() > 0) {
                terminalInstance.appendOutput("ERROR: " + outputBuffer.toString());
            }
        } catch (IOException e) {
            terminalInstance.appendOutput("Error reading terminal error output: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    public boolean charTyped(char chr, int keyCode) {
        if (sshManager.isAwaitingPassword()) {
            if (chr != '\n' && chr != '\r') {
                sshManager.setSshPassword(sshManager.getSshPassword() + chr);
                inputBuffer.append('*');
                cursorPosition++;
                terminalInstance.scrollToBottom();
                return true;
            }
            return false;
        }

        if (chr == '\t' || chr == '`') {
            return false;
        }

        if (chr >= 32 && chr != 127) {
            inputBuffer.insert(cursorPosition, chr);
            cursorPosition++;
            resetTabCompletion();
            updateTabCompletionSuggestion();
            terminalInstance.renderer.resetCursorBlink();
            terminalInstance.scrollToBottom();
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (sshManager.isAwaitingPassword()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                String password = sshManager.getSshPassword();
                sshManager.setSshPassword("");
                inputBuffer.setLength(0);
                cursorPosition = 0;
                terminalInstance.appendOutput("\n");
                sshManager.setAwaitingPassword(false);
                sshManager.connectSSHWithPassword(password);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!sshManager.getSshPassword().isEmpty()) {
                    sshManager.setSshPassword(sshManager.getSshPassword().substring(0, sshManager.getSshPassword().length() - 1));
                    if (inputBuffer.length() > 0) {
                        inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                        cursorPosition--;
                    }
                    terminalInstance.scrollToBottom();
                }
                return true;
            }
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            handleTabCompletion();
            terminalInstance.renderer.resetCursorBlink();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            inputBuffer.insert(cursorPosition, ' ');
            cursorPosition++;
            resetTabCompletion();
            updateTabCompletionSuggestion();
            terminalInstance.renderer.resetCursorBlink();
            terminalInstance.scrollToBottom();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_C && ctrlHeld) {
            terminalInstance.renderer.copySelectionToClipboard();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            try {
                String command = inputBuffer.toString().trim();
                terminalInstance.logCommand(command);

                if (command.startsWith("ssh ")) {
                    sshManager.startSSHConnection(command);
                } else if (sshManager.isSSH()) {
                    sshManager.getSshWriter().write(inputBuffer.toString() + "\n");
                    sshManager.getSshWriter().flush();
                } else {
                    if (command.equalsIgnoreCase("clear")) {
                        synchronized (terminalInstance.renderer.getTerminalOutput()) {
                            terminalInstance.renderer.getTerminalOutput().setLength(0);
                        }
                    } else {
                        writer.write(inputBuffer.toString() + "\n");
                        writer.flush();
                    }
                    updateCurrentDirectoryFromCommand(command);
                }

                if (!command.isEmpty() && (terminalInstance.getCommandHistory().isEmpty() || !command.equals(terminalInstance.getCommandHistory().get(terminalInstance.getCommandHistory().size() - 1)))) {
                    terminalInstance.getCommandHistory().add(command);
                    terminalInstance.setHistoryIndex(terminalInstance.getCommandHistory().size());
                }
                inputBuffer.setLength(0);
                cursorPosition = 0;
                resetTabCompletion();
                updateTabCompletionSuggestion();
                terminalInstance.renderer.resetCursorBlink();
                terminalInstance.scrollToBottom();
            } catch (Exception e) {
                terminalInstance.appendOutput("ERROR: " + e.getMessage() + "\n");
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (terminalInstance.getHistoryIndex() > 0) {
                terminalInstance.setHistoryIndex(terminalInstance.getHistoryIndex() - 1);
                inputBuffer.setLength(0);
                inputBuffer.append(terminalInstance.getCommandHistory().get(terminalInstance.getHistoryIndex()));
                cursorPosition = inputBuffer.length();
            }
            resetTabCompletion();
            updateTabCompletionSuggestion();
            terminalInstance.renderer.resetCursorBlink();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (terminalInstance.getHistoryIndex() < terminalInstance.getCommandHistory().size() - 1) {
                terminalInstance.setHistoryIndex(terminalInstance.getHistoryIndex() + 1);
                inputBuffer.setLength(0);
                inputBuffer.append(terminalInstance.getCommandHistory().get(terminalInstance.getHistoryIndex()));
                cursorPosition = inputBuffer.length();
            } else {
                terminalInstance.setHistoryIndex(terminalInstance.getCommandHistory().size());
                inputBuffer.setLength(0);
                cursorPosition = 0;
            }
            resetTabCompletion();
            updateTabCompletionSuggestion();
            terminalInstance.renderer.resetCursorBlink();
            return true;
        }

        if ((keyCode == GLFW.GLFW_KEY_BACKSPACE && ctrlHeld)) {
            int newCursorPos = moveCursorLeftWord(cursorPosition);
            if (newCursorPos != cursorPosition) {
                inputBuffer.delete(newCursorPos, cursorPosition);
                cursorPosition = newCursorPos;
                resetTabCompletion();
                updateTabCompletionSuggestion();
                terminalInstance.renderer.resetCursorBlink();
                terminalInstance.scrollToBottom();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPosition > 0) {
                inputBuffer.deleteCharAt(cursorPosition - 1);
                cursorPosition--;
                resetTabCompletion();
                updateTabCompletionSuggestion();
                terminalInstance.renderer.resetCursorBlink();
                terminalInstance.scrollToBottom();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            shutdown();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (ctrlHeld) {
                cursorPosition = moveCursorLeftWord(cursorPosition);
            } else {
                if (cursorPosition > 0) {
                    cursorPosition--;
                }
            }
            resetTabCompletion();
            updateTabCompletionSuggestion();
            terminalInstance.renderer.resetCursorBlink();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (ctrlHeld) {
                cursorPosition = moveCursorRightWord(cursorPosition);
            } else {
                if (cursorPosition < inputBuffer.length()) {
                    cursorPosition++;
                }
            }
            resetTabCompletion();
            updateTabCompletionSuggestion();
            terminalInstance.renderer.resetCursorBlink();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V && ctrlHeld) {
            String clipboard = this.minecraftClient.keyboard.getClipboard();
            inputBuffer.insert(cursorPosition, clipboard);
            cursorPosition += clipboard.length();
            resetTabCompletion();
            updateTabCompletionSuggestion();
            terminalInstance.renderer.resetCursorBlink();
            terminalInstance.scrollToBottom();
            return true;
        }

        return false;
    }

    private void updateCurrentDirectoryFromCommand(String command) {
        if (command.startsWith("cd ")) {
            String path = command.substring(3).trim();
            File dir = new File(currentDirectory, path);
            if (dir.isDirectory()) {
                currentDirectory = dir.getAbsolutePath();
            }
        }
    }

    private int moveCursorLeftWord(int position) {
        if (position == 0) return 0;
        int index = position - 1;
        while (index > 0 && Character.isWhitespace(inputBuffer.charAt(index))) {
            index--;
        }
        while (index > 0 && !Character.isWhitespace(inputBuffer.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private int moveCursorRightWord(int position) {
        int length = inputBuffer.length();
        if (position >= length) return length;
        int index = position;
        while (index < length && !Character.isWhitespace(inputBuffer.charAt(index))) {
            index++;
        }
        while (index < length && Character.isWhitespace(inputBuffer.charAt(index))) {
            index++;
        }
        return index;
    }

    public void handleTabCompletion() {
        String currentInput = inputBuffer.toString();
        String[] tokens = currentInput.trim().split("\\s+");
        String lastToken = tokens.length > 0 ? tokens[tokens.length - 1] : "";

        if (tabCompletionIndex == -1) {
            tabCompletionPrefix = lastToken;
            tabCompletionOriginalInput = currentInput;
            tabCompletions.clear();
            tabCompletionIndex = 0;

            if (tokens.length == 1) {
                tabCompletions.addAll(getAvailableCommands(lastToken));
            } else if (tokens[0].equals("cd")) {
                String path = currentInput.substring(currentInput.indexOf("cd") + 2).trim();
                tabCompletions.addAll(getDirectoryCompletions(path));
            } else {
                resetTabCompletion();
                return;
            }

            if (tabCompletions.isEmpty()) {
                resetTabCompletion();
                return;
            }
        } else {
            tabCompletionIndex = (tabCompletionIndex + 1) % tabCompletions.size();
        }

        String completion = tabCompletions.get(tabCompletionIndex);
        String newInput;
        if (tokens.length == 1) {
            newInput = completion;
        } else if (tabCompletionOriginalInput.lastIndexOf(lastToken) >= 0) {
            newInput = tabCompletionOriginalInput.substring(0, tabCompletionOriginalInput.lastIndexOf(lastToken)) + completion;
        } else {
            newInput = tabCompletionOriginalInput + completion;
        }

        inputBuffer.setLength(0);
        inputBuffer.append(newInput);
        cursorPosition = inputBuffer.length();
        updateTabCompletionSuggestion();
    }

    private void resetTabCompletion() {
        tabCompletionIndex = -1;
        tabCompletions.clear();
        tabCompletionPrefix = "";
        tabCompletionOriginalInput = "";
        tabCompletionSuggestion = "";
    }

    private void updateTabCompletionSuggestion() {
        if (inputBuffer.length() == 0) {
            tabCompletionSuggestion = "";
            return;
        }

        String currentInput = inputBuffer.toString();
        String[] tokens = currentInput.trim().split("\\s+");
        String lastToken = tokens.length > 0 ? tokens[tokens.length - 1] : "";

        if (tokens.length == 1) {
            List<String> suggestions = getAvailableCommands(lastToken);
            if (!suggestions.isEmpty()) {
                String suggestion = suggestions.get(0);
                if (suggestion.startsWith(lastToken) && !suggestion.equals(lastToken)) {
                    tabCompletionSuggestion = suggestion.substring(lastToken.length());
                } else {
                    tabCompletionSuggestion = "";
                }
            } else {
                tabCompletionSuggestion = "";
            }
        } else if (tokens[0].equals("cd")) {
            String path = currentInput.substring(currentInput.indexOf("cd") + 2).trim();
            List<String> suggestions = getDirectoryCompletions(path);
            if (!suggestions.isEmpty()) {
                String suggestion = suggestions.get(0);
                if (suggestion.startsWith(lastToken) && !suggestion.equals(lastToken)) {
                    tabCompletionSuggestion = suggestion.substring(lastToken.length());
                } else {
                    tabCompletionSuggestion = "";
                }
            } else {
                tabCompletionSuggestion = "";
            }
        } else {
            tabCompletionSuggestion = "";
        }
    }

    public String getTabCompletionSuggestion() {
        return tabCompletionSuggestion;
    }

    private synchronized void refreshAvailableCommands() {
        if (System.currentTimeMillis() - commandsLastFetched < COMMANDS_CACHE_DURATION) {
            return;
        }
        commandsLastFetched = System.currentTimeMillis();
        Set<String> commandsSet = new HashSet<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] pathDirs = pathEnv.split(File.pathSeparator);
            for (String dir : pathDirs) {
                File dirFile = new File(dir);
                if (dirFile.isDirectory()) {
                    File[] files = dirFile.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile() && isExecutable(file)) {
                                String fileName = file.getName();
                                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                                    int dotIndex = fileName.indexOf('.');
                                    if (dotIndex > 0) {
                                        fileName = fileName.substring(0, dotIndex);
                                    }
                                }
                                commandsSet.add(fileName);
                            }
                        }
                    }
                }
            }
        }
        allCommands = new ArrayList<>(commandsSet);
    }

    private List<String> getAvailableCommands(String prefix) {
        refreshAvailableCommands();
        List<String> result = new ArrayList<>();
        for (String cmd : allCommands) {
            if (cmd.startsWith(prefix)) {
                result.add(cmd);
            }
        }
        Collections.sort(result);
        return result;
    }

    private List<String> getDirectoryCompletions(String path) {
        File dir;
        String prefix;
        if (path.isEmpty()) {
            dir = new File(currentDirectory);
            prefix = "";
        } else {
            File file = new File(currentDirectory, path);
            if (file.isDirectory()) {
                dir = file;
                prefix = "";
            } else {
                dir = file.getParentFile();
                if (dir == null) dir = new File(currentDirectory);
                prefix = file.getName();
            }
        }
        File[] files = dir.listFiles();
        List<String> directories = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    String name = f.getName();
                    if (name.startsWith(prefix)) {
                        directories.add(name);
                    }
                }
            }
            Collections.sort(directories);
        }
        return directories;
    }

    private boolean isExecutable(File file) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            String name = file.getName().toLowerCase();
            return name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".cmd");
        } else {
            return file.canExecute();
        }
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

    public StringBuilder getInputBuffer() {
        return inputBuffer;
    }

    public int getCursorPosition() {
        return cursorPosition;
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
                e.printStackTrace();
            }
            terminalProcess = null;
        }
        sshManager.shutdown();
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
}
