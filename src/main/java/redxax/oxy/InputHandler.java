package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                    if (sshManager.isSSH() && line.trim().equalsIgnoreCase("logout")) {
                        sshManager.shutdown();
                        terminalInstance.appendOutput("SSH session closed. Returned to local terminal.\n");
                    }
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
                if (sshManager.isSSH() && outputBuffer.toString().trim().equalsIgnoreCase("logout")) {
                    sshManager.shutdown();
                    terminalInstance.appendOutput("SSH session closed. Returned to local terminal.\n");
                }
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

        if (chr == '\t' || chr == '`' || chr == ' ') {
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

                if (command.equalsIgnoreCase("exit")) {
                    if (sshManager.isSSH()) {
                        sshManager.getSshWriter().write("exit\n");
                        sshManager.getSshWriter().flush();
                    } else {
                        shutdown();
                    }
                } else if (command.equalsIgnoreCase("clear")) {
                    synchronized (terminalInstance.renderer.getTerminalOutput()) {
                        terminalInstance.renderer.getTerminalOutput().setLength(0);
                    }
                    if (sshManager.isSSH()) {
                        sshManager.getSshWriter().write("clear\n");
                        sshManager.getSshWriter().flush();
                    }
                } else if (command.startsWith("ssh ")) {
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
        String trimmedInput = currentInput.trim();

        if (trimmedInput.isEmpty()) {
            return;
        }

        String[] tokens = trimmedInput.split("\\s+");
        String command = tokens[0];

        if (command.equals("cd")) {
            String path = currentInput.substring(currentInput.indexOf("cd") + 2).trim();
            String separator = getPathSeparator();

            boolean endsWithSeparator = path.endsWith("/") || path.endsWith("\\");
            String basePath = "";
            String partial = "";

            if (endsWithSeparator) {
                basePath = path;
                partial = "";
            } else {
                int lastSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                if (lastSeparatorIndex != -1) {
                    basePath = path.substring(0, lastSeparatorIndex + 1);
                    partial = path.substring(lastSeparatorIndex + 1);
                } else {
                    basePath = "";
                    partial = path;
                }
            }

            List<String> completions = getDirectoryCompletions(basePath + partial);

            if (!completions.isEmpty()) {
                completions.sort(Comparator.naturalOrder());
                String completion = completions.get(0);
                String newPath = basePath + completion + (endsWithSeparator ? separator : "");
                String newCommand = "cd " + newPath;
                inputBuffer.setLength(0);
                inputBuffer.append(newCommand);
                cursorPosition = inputBuffer.length();
                resetTabCompletion();
            }
        } else if (trimmedInput.startsWith("./") || trimmedInput.startsWith(".\\")) {
            String path = trimmedInput.substring(2).trim();
            String separator = getPathSeparator();

            int lastSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            String basePath = "";
            String partial = "";

            if (lastSeparatorIndex != -1) {
                basePath = path.substring(0, lastSeparatorIndex + 1);
                partial = path.substring(lastSeparatorIndex + 1);
            } else {
                basePath = "";
                partial = path;
            }

            List<String> completions = getExecutableCompletions(basePath + partial);

            if (!completions.isEmpty()) {
                completions.sort(Comparator.naturalOrder());
                String completion = completions.get(0);
                String newPath = basePath + completion;
                String newCommand = trimmedInput.substring(0, 2) + newPath;
                inputBuffer.setLength(0);
                inputBuffer.append(newCommand);
                cursorPosition = inputBuffer.length();
                resetTabCompletion();
            }
        } else {
            String partial = trimmedInput;
            List<String> completions = getAvailableCommands(partial);

            if (!completions.isEmpty()) {
                completions.sort(Comparator.naturalOrder());
                String completion = completions.get(0);
                inputBuffer.setLength(0);
                inputBuffer.append(completion);
                cursorPosition = inputBuffer.length();
                resetTabCompletion();
            }
        }
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

        if (tokens.length == 1 && tokens[0].equals("cd")) {
            tabCompletionSuggestion = "";
            return;
        }

        if (tokens.length >= 1 && tokens[0].equals("cd")) {
            String path = currentInput.substring(currentInput.indexOf("cd") + 2).trim();
            String separator = getPathSeparator();

            boolean endsWithSeparator = path.endsWith("/") || path.endsWith("\\");
            String partial = "";

            if (endsWithSeparator) {
                partial = "";
            } else {
                int lastSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                if (lastSeparatorIndex != -1) {
                    partial = path.substring(lastSeparatorIndex + 1);
                } else {
                    partial = path;
                }
            }

            List<String> suggestions = getDirectoryCompletions(path);

            if (!suggestions.isEmpty()) {
                String suggestion = suggestions.get(0);
                if (suggestion.startsWith(partial) && !suggestion.equals(partial)) {
                    tabCompletionSuggestion = suggestion.substring(partial.length()) + (endsWithSeparator ? separator : "");
                } else {
                    tabCompletionSuggestion = "";
                }
            } else {
                tabCompletionSuggestion = "";
            }
        } else if (lastToken.startsWith("./") || lastToken.startsWith(".\\")) {
            String path = lastToken.substring(2).trim();
            String separator = getPathSeparator();

            int lastSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            String partial = "";

            if (lastSeparatorIndex != -1) {
                partial = path.substring(lastSeparatorIndex + 1);
            } else {
                partial = path;
            }

            List<String> suggestions = getExecutableCompletions(path);

            if (!suggestions.isEmpty()) {
                String suggestion = suggestions.get(0);
                if (suggestion.startsWith(partial) && !suggestion.equals(partial)) {
                    tabCompletionSuggestion = suggestion.substring(partial.length());
                } else {
                    tabCompletionSuggestion = "";
                }
            } else {
                tabCompletionSuggestion = "";
            }
        } else {
            String partial = lastToken;
            List<String> suggestions = getAvailableCommands(partial);

            if (!suggestions.isEmpty()) {
                String suggestion = suggestions.get(0);
                if (suggestion.startsWith(partial) && !suggestion.equals(partial)) {
                    tabCompletionSuggestion = suggestion.substring(partial.length());
                } else {
                    tabCompletionSuggestion = "";
                }
            } else {
                tabCompletionSuggestion = "";
            }
        }
    }

    public String getTabCompletionSuggestion() {
        return tabCompletionSuggestion;
    }

    private synchronized void refreshAvailableCommands() {
        if (sshManager.isSSH()) {
            return;
        }
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
        if (sshManager.isSSH()) {
            return sshManager.getSSHCommands(prefix);
        }
        refreshAvailableCommands();
        List<String> result = new ArrayList<>();
        for (String cmd : allCommands) {
            if (cmd.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(cmd);
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private List<String> getDirectoryCompletions(String path) {
        File dir;
        String partial = "";
        if (path.endsWith("/") || path.endsWith("\\")) {
            dir = new File(currentDirectory, path);
        } else {
            int lastSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            if (lastSeparatorIndex != -1) {
                String basePath = path.substring(0, lastSeparatorIndex + 1);
                partial = path.substring(lastSeparatorIndex + 1);
                dir = new File(currentDirectory, basePath);
            } else {
                dir = new File(currentDirectory);
                partial = path;
            }
        }
        List<String> directories = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && !f.isHidden()) {
                        String name = f.getName();
                        if (name.toLowerCase().startsWith(partial.toLowerCase())) {
                            directories.add(name);
                        }
                    }
                }
                Collections.sort(directories, String.CASE_INSENSITIVE_ORDER);
            }
        }
        return directories;
    }

    private List<String> getExecutableCompletions(String partialPath) {
        File dir;
        String partial = "";
        if (partialPath.endsWith("/") || partialPath.endsWith("\\")) {
            dir = new File(currentDirectory, partialPath);
        } else {
            int lastSeparatorIndex = Math.max(partialPath.lastIndexOf('/'), partialPath.lastIndexOf('\\'));
            if (lastSeparatorIndex != -1) {
                String basePath = partialPath.substring(0, lastSeparatorIndex + 1);
                partial = partialPath.substring(lastSeparatorIndex + 1);
                dir = new File(currentDirectory, basePath);
            } else {
                dir = new File(currentDirectory);
                partial = partialPath;
            }
        }
        List<String> executables = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isExecutable(f) && !f.isHidden()) {
                        String name = f.getName();
                        if (name.toLowerCase().startsWith(partial.toLowerCase())) {
                            executables.add(name);
                        }
                    }
                }
                Collections.sort(executables, String.CASE_INSENSITIVE_ORDER);
            }
        }
        return executables;
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

    private String getPathSeparator() {
        return File.separator;
    }
}
