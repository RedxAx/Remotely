package redxax.oxy.input;

import redxax.oxy.SSHManager;
import redxax.oxy.TerminalInstance;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TabCompletionHandler {

    private List<String> tabCompletions = new ArrayList<>();
    private int tabCompletionIndex = -1;
    private String tabCompletionPrefix = "";
    private String tabCompletionOriginalInput = "";
    private String tabCompletionSuggestion = "";
    private final TerminalInstance terminalInstance;
    private final SSHManager sshManager;
    private final CommandExecutor commandExecutor;
    private String currentDirectory;

    private List<String> allCommands = new ArrayList<>();
    private long commandsLastFetched = 0;
    private static final long COMMANDS_CACHE_DURATION = 60 * 1000;

    public TabCompletionHandler(TerminalInstance terminalInstance, SSHManager sshManager, CommandExecutor commandExecutor, String currentDirectory) {
        this.terminalInstance = terminalInstance;
        this.sshManager = sshManager;
        this.commandExecutor = commandExecutor;
        this.currentDirectory = currentDirectory;
    }

    public void handleTabCompletion(StringBuilder inputBuffer, int cursorPosition) {
        String currentInput = inputBuffer.toString();
        String trimmedInput = currentInput.substring(0, cursorPosition).trim();

        if (trimmedInput.isEmpty()) {
            return;
        }

        String[] tokens = trimmedInput.split("\\s+");
        String command = tokens[0];

        if (command.equals("cd")) {
            String path = trimmedInput.substring(trimmedInput.indexOf("cd") + 2).trim();
            String separator = getPathSeparator();

            String basePath = "";
            String partial = "";

            int lastSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            if (lastSeparatorIndex != -1) {
                basePath = path.substring(0, lastSeparatorIndex + 1);
                partial = path.substring(lastSeparatorIndex + 1);
            } else {
                basePath = "";
                partial = path;
            }

            List<String> completions = getDirectoryCompletions(basePath + partial);

            if (!completions.isEmpty()) {
                completions.sort(Comparator.naturalOrder());
                String completion = completions.get(0);
                String newPath = basePath + completion + separator;

                tabCompletionSuggestion = newPath.substring(partial.length());
            } else {
                tabCompletionSuggestion = "";
            }
        } else if (trimmedInput.startsWith("./") || trimmedInput.startsWith(".\\")) {
            String path = trimmedInput.substring(2).trim();
            String separator = getPathSeparator();

            String basePath = "";
            String partial = "";

            int lastSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
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

                tabCompletionSuggestion = newPath.substring(partial.length());
            } else {
                tabCompletionSuggestion = "";
            }
        } else {
            String partial = trimmedInput;
            List<String> completions = getAvailableCommands(partial);

            if (!completions.isEmpty()) {
                completions.sort(Comparator.naturalOrder());
                String completion = completions.get(0);

                if (!completion.equals(partial)) {
                    tabCompletionSuggestion = completion.substring(partial.length());
                } else {
                    tabCompletionSuggestion = "";
                }
            } else {
                tabCompletionSuggestion = "";
            }
        }
    }

    public void resetTabCompletion() {
        tabCompletionIndex = -1;
        tabCompletions.clear();
        tabCompletionPrefix = "";
        tabCompletionOriginalInput = "";
        tabCompletionSuggestion = "";
    }

    public void updateTabCompletionSuggestion(StringBuilder inputBuffer, int cursorPosition) {
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

    public void setCurrentDirectory(String currentDirectory) {
        this.currentDirectory = currentDirectory;
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
                            if (file.isFile() && isExecutable(file) && !file.isHidden()) {
                                String fileName = file.getName();
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
                executables.sort(String.CASE_INSENSITIVE_ORDER);
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

    private String getPathSeparator() {
        return File.separator;
    }
}