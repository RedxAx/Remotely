package redxax.oxy.remotely.input;

import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.RemotelyClient.themes;

public class TabCompletionHandler {
    private List<String> completions = new ArrayList<>();
    private int completionIndex = 0;
    private String lastPrefix = "";
    private String originalPrefix = "";
    private boolean originalPrefixSet = false;
    private String suggestion = "";
    private final SSHManager sshManager;
    private String currentDirectory;
    private List<String> allCommands = new ArrayList<>();
    private long commandsLastFetched = 0;
    private static final long COMMANDS_CACHE_DURATION = 60 * 1000;
    private String currentBase = "";

    public TabCompletionHandler(SSHManager sshManager, String currentDirectory) {
        this.sshManager = sshManager;
        this.currentDirectory = currentDirectory;
    }

    public void handleTabCompletion(StringBuilder inputBuffer, int cursorPosition) {
        String input = inputBuffer.toString();
        String textBeforeCursor = input.substring(0, cursorPosition);
        if (textBeforeCursor.trim().isEmpty()) {
            resetTabCompletion();
            return;
        }
        String[] tokens = textBeforeCursor.split("\\s+");
        if (tokens.length == 0) {
            resetTabCompletion();
            return;
        }
        if (tokens[0].equals("cd")) {
            String pathPart = textBeforeCursor.substring(textBeforeCursor.indexOf("cd") + 2).trim();
            String base = "";
            String partial = "";
            int lastSep = Math.max(pathPart.lastIndexOf('/'), pathPart.lastIndexOf('\\'));
            if (lastSep != -1) {
                base = pathPart.substring(0, lastSep + 1);
                partial = pathPart.substring(lastSep + 1);
            } else {
                partial = pathPart;
            }
            currentBase = base;
            if (!originalPrefixSet) {
                originalPrefix = partial;
                originalPrefixSet = true;
            }
            List<String> dirs = sshManager.isSSH() ? getRemoteDirectoryCompletions(base, originalPrefix)
                    : getLocalDirectoryCompletions(base, originalPrefix);
            cycleCompletion(originalPrefix, dirs);
        } else if (tokens[0].equals("theme") && tokens.length <= 2) {
            String partial = tokens.length == 2 ? tokens[1] : "";
            if (!originalPrefixSet) {
                originalPrefix = partial;
                originalPrefixSet = true;
            }
            List<String> themeNames = getThemeCompletions(originalPrefix);
            cycleCompletion(originalPrefix, themeNames);
        } else {
            if (!originalPrefixSet) {
                originalPrefix = tokens[tokens.length - 1];
                originalPrefixSet = true;
            }
            currentBase = "";
            List<String> cmds = getAvailableCommands(originalPrefix);
            cycleCompletion(originalPrefix, cmds);
        }
    }

    private void cycleCompletion(String prefix, List<String> options) {
        if (options.isEmpty()) {
            suggestion = "";
            completions.clear();
            lastPrefix = "";
            completionIndex = 0;
            return;
        }
        completions = options;
        if (!prefix.equals(lastPrefix)) {
            completionIndex = 0;
            lastPrefix = prefix;
        }
        String candidate = completions.get(completionIndex);
        if (candidate.toLowerCase().startsWith(prefix.toLowerCase())) {
            suggestion = candidate.substring(prefix.length());
        } else {
            suggestion = candidate;
        }
        completionIndex = (completionIndex + 1) % completions.size();
    }

    public String getTabCompletionSuggestion() {
        return suggestion;
    }

    public String getOriginalPrefix() {
        if (!currentBase.isEmpty()) {
            return currentBase + originalPrefix;
        }
        return originalPrefix;
    }

    public void resetTabCompletion() {
        completions.clear();
        suggestion = "";
        lastPrefix = "";
        completionIndex = 0;
        originalPrefix = "";
        originalPrefixSet = false;
        currentBase = "";
    }

    public void setCurrentDirectory(String currentDirectory) {
        this.currentDirectory = currentDirectory;
    }

    private List<String> getAvailableCommands(String prefix) {
        if (sshManager.isSSH()) {
            return sshManager.getSSHCommands(prefix).stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(prefix.toLowerCase()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        refreshAvailableCommands();
        List<String> result = new ArrayList<>();
        if ("theme".toLowerCase().startsWith(prefix.toLowerCase())) {
            result.add("theme");
        }
        for (String cmd : allCommands) {
            if (cmd.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(cmd);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private synchronized void refreshAvailableCommands() {
        if (sshManager.isSSH()) {
            return;
        }
        if (System.currentTimeMillis() - commandsLastFetched < COMMANDS_CACHE_DURATION) {
            return;
        }
        commandsLastFetched = System.currentTimeMillis();
        Set<String> cmds = new HashSet<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(File.pathSeparator);
            for (String dir : dirs) {
                File d = new File(dir);
                if (d.isDirectory()) {
                    File[] files = d.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile() && file.canExecute() && !file.isHidden()) {
                                cmds.add(file.getName());
                            }
                        }
                    }
                }
            }
        }
        allCommands = new ArrayList<>(cmds);
    }

    private List<String> getLocalDirectoryCompletions(String base, String partial) {
        File dir = base.isEmpty() ? new File(currentDirectory) : new File(currentDirectory, base);
        List<String> dirs = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && !f.isHidden()) {
                        String name = f.getName();
                        if (name.toLowerCase().startsWith(partial.toLowerCase())) {
                            dirs.add(name);
                        }
                    }
                }
            }
        }
        dirs.sort(String.CASE_INSENSITIVE_ORDER);
        return dirs;
    }

    private List<String> getRemoteDirectoryCompletions(String base, String partial) {
        List<String> dirs = new ArrayList<>();
        try {
            String remotePath = base.isEmpty() ? currentDirectory : currentDirectory + "/" + base;
            List<String> entries = sshManager.listRemoteDirectory(remotePath);
            for (String entry : entries) {
                String fullPath = remotePath.endsWith("/") ? remotePath + entry : remotePath + "/" + entry;
                if (sshManager.isRemoteDirectory(fullPath) && entry.toLowerCase().startsWith(partial.toLowerCase())) {
                    dirs.add(entry);
                }
            }
            dirs.sort(String.CASE_INSENSITIVE_ORDER);
        } catch (Exception e) {
            dirs.clear();
        }
        return dirs;
    }

    private List<String> getThemeCompletions(String prefix) {
        List<String> themeNames = new ArrayList<>();
        for (MultiTerminalScreen.Theme theme : themes) {
            if (theme.name.toLowerCase().startsWith(prefix.toLowerCase())) {
                themeNames.add(theme.name);
            }
        }
        themeNames.sort(String.CASE_INSENSITIVE_ORDER);
        return themeNames;
    }

    public void updateTabCompletionSuggestion(StringBuilder inputBuffer) {
        String input = inputBuffer.toString();
        if (input.trim().isEmpty()) {
            suggestion = "";
            return;
        }
        int wordStart = 0;
        for (int i = input.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(input.charAt(i))) {
                wordStart = i + 1;
                break;
            }
        }
        String prefix = input.substring(wordStart);
        if (input.startsWith("cd ")) {
            String pathPart = input.substring(3).trim();
            String base = "";
            String partial = "";
            int lastSep = Math.max(pathPart.lastIndexOf('/'), pathPart.lastIndexOf('\\'));
            if (lastSep != -1) {
                base = pathPart.substring(0, lastSep + 1);
                partial = pathPart.substring(lastSep + 1);
            } else {
                partial = pathPart;
            }
            currentBase = base;
            if (!originalPrefixSet) {
                originalPrefix = partial;
                originalPrefixSet = true;
            }
            List<String> dirs = sshManager.isSSH() ? getRemoteDirectoryCompletions(base, originalPrefix)
                    : getLocalDirectoryCompletions(base, originalPrefix);
            if (dirs.isEmpty()) {
                suggestion = "";
                return;
            }
            String candidate = dirs.get(0);
            suggestion = candidate.toLowerCase().startsWith(originalPrefix.toLowerCase()) ? candidate.substring(originalPrefix.length()) : candidate;
        } else if (input.startsWith("theme ")) {
            String partial = input.substring(6).trim();
            if (!originalPrefixSet) {
                originalPrefix = partial;
                originalPrefixSet = true;
            }
            List<String> themeNames = getThemeCompletions(originalPrefix);
            if (themeNames.isEmpty()) {
                suggestion = "";
                return;
            }
            String candidate = themeNames.get(0);
            suggestion = candidate.toLowerCase().startsWith(originalPrefix.toLowerCase()) ? candidate.substring(originalPrefix.length()) : candidate;
        } else {
            if (!originalPrefixSet) {
                originalPrefix = prefix;
                originalPrefixSet = true;
            }
            List<String> cmds = getAvailableCommands(originalPrefix);
            if (cmds.isEmpty()) {
                suggestion = "";
                return;
            }
            String candidate = cmds.get(0);
            suggestion = candidate.toLowerCase().startsWith(originalPrefix.toLowerCase()) ? candidate.substring(originalPrefix.length()) : candidate;
        }
    }

    public void clearTabCompletionSuggestion() {
        suggestion = "";
    }
}
