package redxax.oxy.remotely.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.*;
import net.minecraft.util.math.MathHelper;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.config.Themes;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerState;
import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import redxax.oxy.remotely.terminal.TerminalProcessManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.RemotelyClient.themes;
import static redxax.oxy.remotely.config.Config.*;

public class TerminalWidget extends AnimatedWidget {

    private final List<LineText> outputBuffer = new CopyOnWriteArrayList<>();
    private final StringBuilder inputBuffer = new StringBuilder();
    private int cursorPosition = 0;

    private float scrollY = 0;
    private float targetScrollY = 0;
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = 0;

    private final ServerInfo serverInfo;
    private final SSHManager sshManager;
    public final TerminalProcessManager processManager;

    private boolean isSelecting = false;
    private int selectionStartLine, selectionStartChar, selectionEndLine, selectionEndChar;

    private static final Pattern ALL_ANSI = Pattern.compile("\u001B\\[[0-9;?]*(?:m|[A-Za-z])|\u001B=>|=\\u001B.*?\\\\|\\u001B]10;\\?\\\\|\\u001B]11;\\?\\\\|\u001B\\[\\?2004[hl]|\u001B=|\u001Bc|\u001B\\[\\?1h=\\u001B\\[\\?2004h|\u001B][0-9];.*?\u0007|\u001B][0-9];.*?\\\\|\u001BN|\u001BO|\u001BP[^\\\\]*\\\\|\u001B\\^|\u001B_|\u001B\\\\|\u001B]|\u001B[()][AB012]");
    private static final Pattern BRACKET_KEYWORD_PATTERN = Pattern.compile("\\[(.*?)\\b(WARNING|WARN|ERROR|INFO)\\b(.*?)]");

    private List<String> completions = new ArrayList<>();
    private int completionIndex = 0;
    private String lastPrefix = "";
    private String suggestion = "";
    private String currentBase = "";
    private volatile List<String> allCommands = new ArrayList<>();
    private volatile long commandsLastFetched = 0;
    private static final long COMMANDS_CACHE_DURATION = 60 * 1000;
    private volatile boolean isRefreshingCommands = false;
    private final ExecutorService commandRefreshExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, CachedDirectory> localDirectoryCache = new HashMap<>();
    private static final long LOCAL_DIR_CACHE_DURATION = 5000;
    private final Map<String, CachedDirectory> remoteDirectoryCache = new HashMap<>();
    private static final long REMOTE_DIR_CACHE_DURATION = 5000;

    private static class CachedDirectory {
        List<String> directories;
        long fetchedAt;

        CachedDirectory(List<String> directories, long fetchedAt) {
            this.directories = directories;
            this.fetchedAt = fetchedAt;
        }
    }

    public static class Builder extends AnimatedWidget.Builder<TerminalWidget, Builder> {
        private ServerInfo serverInfo;

        public Builder() {
            super(new TerminalWidget(0, 0, 200, 150, null));
        }

        public Builder server(ServerInfo info) {
            this.serverInfo = info;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TerminalWidget build() {
            return new TerminalWidget(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), serverInfo);
        }
    }

    public TerminalWidget(int x, int y, int width, int height, ServerInfo serverInfo) {
        super(x, y, width, height, Text.empty());
        this.serverInfo = serverInfo;

        if (this.serverInfo != null && this.serverInfo.isRemote) {
            this.sshManager = RemotelyClient.INSTANCE.getSSHManagerForHost(this.serverInfo.remoteHost);
            if (this.sshManager != null) {
                this.sshManager.setTerminalWidget(this);
            } else {
                appendOutput("Could not establish SSH connection for remote server.\n");
            }
            this.processManager = null;
        } else {
            this.sshManager = new SSHManager(this);
            this.processManager = new TerminalProcessManager(this, this.sshManager);
            if (serverInfo == null) {
                this.processManager.launchTerminal();
            }
        }
    }

    public void shutdown() {
        if (processManager != null) {
            processManager.shutdown();
        }
        if (sshManager != null) {
            sshManager.shutdown();
        }
        commandRefreshExecutor.shutdownNow();
    }

    public void appendOutput(String text) {
        text = text.replace("\r", "");
        String[] lines = text.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty() && i < lines.length -1) {
                outputBuffer.add(new LineText(Text.empty().asOrderedText(), ""));
                continue;
            }

            if (serverInfo != null) {
                detectServerState(line);
            }

            List<StyleTextPair> segments = parseLine(line);
            List<LineText> wrapped = wrapStyledText(segments, getWidth() - 10);
            outputBuffer.addAll(wrapped);
        }
        scrollToBottom();
    }

    private void detectServerState(String line) {
        if (line.contains("Done (")) {
            serverInfo.state = ServerState.RUNNING;
        } else if (line.matches(".*\\b[Ff]atal\\b.*") || line.matches(".*\\b[Uu]nhandled exception\\b.*") || line.contains("You need to agree to the EULA") || line.contains("Error: Unable to access jarfile") || line.contains("Failed to bind to port") || line.contains("java.lang.OutOfMemoryError") || line.contains("locked by another process")) {
            serverInfo.state = ServerState.CRASHED;
        } else if (line.toLowerCase().contains("stopping server") || line.toLowerCase().contains("server stopped")) {
            serverInfo.state = ServerState.STOPPED;
        } else if (line.toLowerCase().contains("starting minecraft server")) {
            serverInfo.state = ServerState.STARTING;
        }
    }

    @Override
    public void tick() {
        super.tick();
        scrollY += (targetScrollY - scrollY) * globalScrollSpeed * deltaTime;
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        int padding = 2;
        int inputHeight = tr.fontHeight + 4;
        int statusHeight = tr.fontHeight + 4;
        int contentHeight = getHeight() - inputHeight - statusHeight - (padding * 2);
        int contentY = getY() + padding;

        ctx.enableScissor(getX() + padding, getY(), getX() + getWidth() - padding, contentY + contentHeight);

        int lineHeight = tr.fontHeight + 2;
        int firstLine = (int) Math.floor(scrollY / lineHeight);
        int visibleLineCount = (int)Math.ceil((float)contentHeight / lineHeight) + 1;

        for (int i = 0; i < visibleLineCount; i++) {
            int lineIndex = firstLine + i;
            if (lineIndex < 0 || lineIndex >= outputBuffer.size()) continue;

            int lineY = contentY + (i * lineHeight) - (int)(scrollY % lineHeight);
            LineText lineText = outputBuffer.get(lineIndex);

            if (isSelecting) {
                drawSelection(ctx, lineIndex, getX() + padding, lineY);
            }

            ctx.drawText(tr, lineText.orderedText, getX() + padding, lineY, terminalTextColor, shadow);
        }

        ctx.disableScissor();

        int inputY = getY() + getHeight() - statusHeight - inputHeight;
        drawInput(ctx, getX() + padding, inputY);

        int statusY = getY() + getHeight() - statusHeight;
        drawStatusBar(ctx, getX(), statusY, getWidth(), statusHeight);
    }

    private void drawInput(DrawContext ctx, int x, int y) {
        String prompt = sshManager != null && sshManager.isAwaitingPassword() ? "Password: " : "> ";
        String textToDraw = prompt + (sshManager != null && sshManager.isAwaitingPassword() ? "*".repeat(inputBuffer.length()) : inputBuffer.toString());

        ctx.drawText(tr, Text.literal(textToDraw), x, y, terminalTextInputColor, shadow);

        if (!suggestion.isEmpty() && !inputBuffer.isEmpty()) {
            int inputTextWidth = tr.getWidth(textToDraw);
            ctx.drawText(tr, Text.literal(suggestion).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(globalDarkTextColor))), x + inputTextWidth, y, globalDarkTextColor, shadow);
        }

        if (isFocused() && System.currentTimeMillis() % 1000 > 500) {
            String beforeCursorText = prompt + (sshManager != null && sshManager.isAwaitingPassword() ? "*".repeat(cursorPosition) : inputBuffer.substring(0, cursorPosition));
            int cursorX = x + tr.getWidth(beforeCursorText);
            ctx.fill(cursorX, y - 1, cursorX + 1, y + tr.fontHeight, globalCursorAnimatedColor);
        }
    }

    private void drawStatusBar(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, terminalStatusBarColor);
        String leftText = "Remotely";
        String rightText = new java.util.Date().toString();

        if (serverInfo != null) {
            leftText = serverInfo.name + " - " + serverInfo.state.name();
            if (serverInfo.isRemote && sshManager != null) {
                rightText = serverInfo.remoteHost.name + " (" + (sshManager.isSSH() ? "Connected" : "Disconnected") + ")";
            } else {
                rightText = "Local";
            }
        }

        ctx.drawText(tr, leftText, x + 4, y + (h - tr.fontHeight) / 2, terminalTextColor, shadow);
        ctx.drawText(tr, rightText, x + w - tr.getWidth(rightText) - 4, y + (h - tr.fontHeight) / 2, terminalTextColor, shadow);
    }

    private void drawSelection(DrawContext ctx, int lineIndex, int x, int y) {
        SelectionPoint start = getOrderedSelectionStart();
        SelectionPoint end = getOrderedSelectionEnd();

        if (lineIndex < start.line || lineIndex > end.line) return;

        String lineText = outputBuffer.get(lineIndex).plainText;
        int selStartCol = (lineIndex == start.line) ? start.col : 0;
        int selEndCol = (lineIndex == end.line) ? end.col : lineText.length();

        if (selStartCol >= selEndCol) return;

        int selX = x + tr.getWidth(lineText.substring(0, selStartCol));
        int selW = tr.getWidth(lineText.substring(selStartCol, selEndCol));

        ctx.fill(selX, y, selX + selW, y + tr.fontHeight + 2, globalSelectionColor);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            targetScrollY -= (float) (verticalAmount * (tr.fontHeight + 2) * 3);
            clampScroll();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isSelecting = true;
            SelectionPoint p = getPosFromCoords(mouseX, mouseY);
            selectionStartLine = selectionEndLine = p.line;
            selectionStartChar = selectionEndChar = p.col;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isFocused() && button == 0 && isSelecting) {
            SelectionPoint p = getPosFromCoords(mouseX, mouseY);
            selectionEndLine = p.line;
            selectionEndChar = p.col;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isSelecting = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (sshManager != null && sshManager.isAwaitingPassword()) {
            return handlePasswordInput(keyCode, ctrl);
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                executeCommand(inputBuffer.toString());
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                handleTabCompletion();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursorPosition > 0) {
                    inputBuffer.deleteCharAt(cursorPosition - 1);
                    cursorPosition--;
                    resetTabCompletion();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursorPosition < inputBuffer.length()) {
                    inputBuffer.deleteCharAt(cursorPosition);
                    resetTabCompletion();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursorPosition > 0) cursorPosition--;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursorPosition < inputBuffer.length()) cursorPosition++;
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (historyIndex > 0) {
                    historyIndex--;
                    inputBuffer.setLength(0);
                    inputBuffer.append(commandHistory.get(historyIndex));
                    cursorPosition = inputBuffer.length();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (historyIndex < commandHistory.size() - 1) {
                    historyIndex++;
                    inputBuffer.setLength(0);
                    inputBuffer.append(commandHistory.get(historyIndex));
                    cursorPosition = inputBuffer.length();
                } else {
                    historyIndex = commandHistory.size();
                    inputBuffer.setLength(0);
                    cursorPosition = 0;
                }
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clipboard = mc.keyboard.getClipboard();
                    inputBuffer.insert(cursorPosition, clipboard);
                    cursorPosition += clipboard.length();
                }
                return true;
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) {
                    copySelectionToClipboard();
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handlePasswordInput(int keyCode, boolean ctrl) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            sshManager.connectSSHWithPassword(inputBuffer.toString());
            inputBuffer.setLength(0);
            cursorPosition = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && cursorPosition > 0) {
            inputBuffer.deleteCharAt(cursorPosition - 1);
            cursorPosition--;
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clipboard = mc.keyboard.getClipboard();
            inputBuffer.insert(cursorPosition, clipboard);
            cursorPosition += clipboard.length();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || Character.isISOControl(chr)) return false;
        inputBuffer.insert(cursorPosition, chr);
        cursorPosition++;
        resetTabCompletion();
        return true;
    }

    public void executeCommand(String command) {
        try {
            String trimmedCommand = command.trim();
            if (!trimmedCommand.isBlank()) {
                if (commandHistory.isEmpty() || !trimmedCommand.equals(commandHistory.get(commandHistory.size() - 1))) {
                    commandHistory.add(trimmedCommand);
                }
                historyIndex = commandHistory.size();
            }


            if (trimmedCommand.equalsIgnoreCase("exit")) {
                if (sshManager != null && sshManager.isSSH()) {
                    sshManager.getSshWriter().write("exit\n");
                    sshManager.getSshWriter().flush();
                } else {
                    shutdown();
                }
            } else if (trimmedCommand.equalsIgnoreCase("clear")) {
                outputBuffer.clear();
            } else if (trimmedCommand.startsWith("ssh ") && sshManager != null) {
                sshManager.startSSHConnection(trimmedCommand);
            } else if (trimmedCommand.startsWith("theme ")) {
                handleThemeCommand(trimmedCommand);
            } else if (sshManager != null && sshManager.isSSH()) {
                sshManager.getSshWriter().write(command + "\n");
                sshManager.getSshWriter().flush();
            } else if (processManager != null && processManager.getWriter() != null) {
                processManager.getWriter().write(command + "\n");
                processManager.getWriter().flush();
                updateCurrentDirectoryFromCommand(trimmedCommand);
            } else {
                appendOutput("No process to send command to.\n");
            }
        } catch (IOException e) {
            appendOutput("ERROR: " + e.getMessage() + "\n");
        } finally {
            inputBuffer.setLength(0);
            cursorPosition = 0;
            resetTabCompletion();
        }
    }

    private void handleThemeCommand(String command) {
        String themeName = command.substring(6).trim().replace('_', ' ');
        for (MultiTerminalScreen.Theme theme : themes) {
            if (theme.name.equalsIgnoreCase(themeName)) {
                Themes.applyTheme(theme);
                appendOutput("Theme changed to: " + theme.name + "\n");
                return;
            }
        }
        String available = themes.stream().map(t -> t.name).collect(Collectors.joining(", "));
        appendOutput("Theme not found: " + themeName + "\nAvailable: " + available + "\n");
    }

    private void updateCurrentDirectoryFromCommand(String command) {
        if (command.startsWith("cd ") && processManager != null) {
            String path = command.substring(3).trim();
            File dir = new File(processManager.getCurrentDirectory(), path);
            if (dir.isDirectory()) {
                processManager.setCurrentDirectory(dir.getAbsolutePath());
            }
        }
    }

    private void handleTabCompletion() {
        String textBeforeCursor = inputBuffer.substring(0, cursorPosition);
        if (textBeforeCursor.trim().isEmpty()) {
            resetTabCompletion();
            return;
        }

        String[] tokens = textBeforeCursor.split("\\s+");
        if (tokens.length == 0) {
            resetTabCompletion();
            return;
        }

        List<String> options;
        String prefix;
        if (tokens[0].equals("cd")) {
            String pathPart = textBeforeCursor.substring(textBeforeCursor.indexOf("cd") + 2).trim();
            int lastSep = Math.max(pathPart.lastIndexOf('/'), pathPart.lastIndexOf('\\'));
            currentBase = (lastSep != -1) ? pathPart.substring(0, lastSep + 1) : "";
            prefix = (lastSep != -1) ? pathPart.substring(lastSep + 1) : pathPart;
            options = (sshManager != null && sshManager.isSSH()) ? getRemoteDirectoryCompletions(currentBase, prefix) : getLocalDirectoryCompletions(currentBase, prefix);
        } else if (tokens[0].equals("theme")) {
            prefix = textBeforeCursor.substring(5).trim();
            currentBase = "";
            options = getThemeCompletions(prefix).stream().map(name -> name.replace(" ", "_")).collect(Collectors.toList());
        } else {
            prefix = tokens[tokens.length - 1];
            currentBase = "";
            options = getAvailableCommands(prefix);
        }

        if (!prefix.equals(lastPrefix)) {
            completions = options;
            completionIndex = 0;
        }
        lastPrefix = prefix;

        if (completions.isEmpty()) {
            suggestion = "";
            return;
        }

        String candidate = completions.get(completionIndex);
        suggestion = candidate.substring(prefix.length());

        inputBuffer.replace(cursorPosition - prefix.length(), cursorPosition, candidate);
        cursorPosition = cursorPosition - prefix.length() + candidate.length();

        completionIndex = (completionIndex + 1) % completions.size();
        suggestion = "";
    }

    private void resetTabCompletion() {
        completions.clear();
        suggestion = "";
        lastPrefix = "";
        completionIndex = 0;
        currentBase = "";
    }

    private List<String> getAvailableCommands(String prefix) {
        if (sshManager != null && sshManager.isSSH()) {
            return sshManager.getSSHCommands(prefix).stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(prefix.toLowerCase()))
                    .sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
        }

        long now = System.currentTimeMillis();
        if (now - commandsLastFetched > COMMANDS_CACHE_DURATION && !isRefreshingCommands) {
            isRefreshingCommands = true;
            commandRefreshExecutor.submit(this::refreshAvailableCommandsInternal);
        }

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

    private synchronized void refreshAvailableCommandsInternal() {
        if (sshManager != null && sshManager.isSSH()) return;

        Set<String> cmds = new HashSet<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
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
        commandsLastFetched = System.currentTimeMillis();
        isRefreshingCommands = false;
    }

    private List<String> getLocalDirectoryCompletions(String base, String partial) {
        File dir = base.isEmpty() ? new File(getCurrentDir()) : new File(getCurrentDir(), base);
        String cacheKey = dir.getAbsolutePath();
        long now = System.currentTimeMillis();

        CachedDirectory cached = localDirectoryCache.get(cacheKey);
        if (cached != null && (now - cached.fetchedAt) < LOCAL_DIR_CACHE_DURATION) {
            return cached.directories.stream().filter(name -> name.toLowerCase().startsWith(partial.toLowerCase())).collect(Collectors.toList());
        }

        List<String> allDirs = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && !f.isHidden()) {
                        allDirs.add(f.getName());
                    }
                }
            }
        }
        allDirs.sort(String.CASE_INSENSITIVE_ORDER);
        localDirectoryCache.put(cacheKey, new CachedDirectory(allDirs, now));
        return allDirs.stream().filter(name -> name.toLowerCase().startsWith(partial.toLowerCase())).collect(Collectors.toList());
    }

    private List<String> getRemoteDirectoryCompletions(String base, String partial) {
        String remotePath = base.isEmpty() ? getCurrentDir() : getCurrentDir() + "/" + base;
        long now = System.currentTimeMillis();

        CachedDirectory cached = remoteDirectoryCache.get(remotePath);
        if (cached != null && (now - cached.fetchedAt) < REMOTE_DIR_CACHE_DURATION) {
            return cached.directories.stream().filter(name -> name.toLowerCase().startsWith(partial.toLowerCase())).collect(Collectors.toList());
        }

        List<String> dirs = new ArrayList<>();
        try {
            if (sshManager == null || !sshManager.isSSH()) return dirs;
            List<String> entries = sshManager.listRemoteDirectory(remotePath);
            for (String entry : entries) {
                String fullPath = remotePath.endsWith("/") ? remotePath + entry : remotePath + "/" + entry;
                if (sshManager.isRemoteDirectory(fullPath)) {
                    dirs.add(entry);
                }
            }
            dirs.sort(String.CASE_INSENSITIVE_ORDER);
            remoteDirectoryCache.put(remotePath, new CachedDirectory(dirs, now));
        } catch (Exception e) {
            dirs.clear();
        }
        return dirs.stream().filter(name -> name.toLowerCase().startsWith(partial.toLowerCase())).collect(Collectors.toList());
    }

    private List<String> getThemeCompletions(String prefix) {
        return themes.stream()
                .map(t -> t.name)
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private void scrollToBottom() {
        int contentHeight = getHeight() - (tr.fontHeight + 4) * 2 - 4;
        targetScrollY = Math.max(0, outputBuffer.size() * (tr.fontHeight + 2) - contentHeight);
        clampScroll();
    }

    private void clampScroll() {
        int contentHeight = getHeight() - (tr.fontHeight + 4) * 2 - 4;
        int maxScroll = Math.max(0, outputBuffer.size() * (tr.fontHeight + 2) - contentHeight);
        targetScrollY = MathHelper.clamp(targetScrollY, 0, maxScroll);
    }

    private void copySelectionToClipboard() {
        if (!isSelecting) return;
        SelectionPoint start = getOrderedSelectionStart();
        SelectionPoint end = getOrderedSelectionEnd();

        StringBuilder sb = new StringBuilder();
        for (int i = start.line; i <= end.line; i++) {
            if (i >= outputBuffer.size()) break;
            String lineText = outputBuffer.get(i).plainText;
            int startCol = (i == start.line) ? start.col : 0;
            int endCol = (i == end.line) ? end.col : lineText.length();

            if(startCol < endCol) {
                sb.append(lineText, startCol, endCol);
            }
            if (i < end.line) {
                sb.append("\n");
            }
        }
        mc.keyboard.setClipboard(sb.toString());
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public void setServerState(ServerState state) {
        if (this.serverInfo != null) {
            this.serverInfo.state = state;
        }
    }

    public List<String> getCommandHistory() {
        return commandHistory;
    }

    public int getHistoryIndex() {
        return historyIndex;
    }

    public void setHistoryIndex(int index) {
        this.historyIndex = index;
    }

    public String getCurrentDir() {
        if (processManager != null) {
            return processManager.getCurrentDirectory();
        } else if (sshManager != null && serverInfo != null && serverInfo.isRemote) {
            return serverInfo.path;
        }
        return "/";
    }

    public void saveTerminalOutput(Path path) throws IOException {
        StringBuilder fullOutput = new StringBuilder();
        for(LineText line : outputBuffer) {
            fullOutput.append(line.plainText).append("\n");
        }
        java.nio.file.Files.writeString(path, fullOutput.toString());
    }

    private SelectionPoint getPosFromCoords(double mouseX, double mouseY) {
        int padding = 2;
        int contentY = getY() + padding;
        int lineHeight = tr.fontHeight + 2;

        int line = (int)Math.floor((mouseY - contentY + scrollY) / lineHeight);
        line = MathHelper.clamp(line, 0, outputBuffer.size() - 1);

        if (outputBuffer.isEmpty()) return new SelectionPoint(0,0);

        String lineText = outputBuffer.get(line).plainText;
        int col = 0;
        int minDx = Integer.MAX_VALUE;
        for (int i = 0; i <= lineText.length(); i++) {
            int dx = Math.abs((int)mouseX - (getX() + padding + tr.getWidth(lineText.substring(0, i))));
            if (dx < minDx) {
                minDx = dx;
                col = i;
            }
        }
        return new SelectionPoint(line, col);
    }

    private SelectionPoint getOrderedSelectionStart() {
        return new SelectionPoint(selectionStartLine, selectionStartChar).isBefore(new SelectionPoint(selectionEndLine, selectionEndChar))
                ? new SelectionPoint(selectionStartLine, selectionStartChar)
                : new SelectionPoint(selectionEndLine, selectionEndChar);
    }

    private SelectionPoint getOrderedSelectionEnd() {
        return new SelectionPoint(selectionStartLine, selectionStartChar).isBefore(new SelectionPoint(selectionEndLine, selectionEndChar))
                ? new SelectionPoint(selectionEndLine, selectionEndChar)
                : new SelectionPoint(selectionStartLine, selectionStartChar);
    }

    private record LineText(OrderedText orderedText, String plainText) {}
    private record StyleTextPair(Style style, String text) {}
    private record SelectionPoint(int line, int col) {
        boolean isBefore(SelectionPoint other) {
            return this.line < other.line || (this.line == other.line && this.col < other.col);
        }
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    private List<StyleTextPair> parseLine(String text) {
        text = text.replace("\u000f", "").replace("\t", "    ");
        String cleanText = removeAllAnsiSequences(text);

        Matcher bracketMatcher = BRACKET_KEYWORD_PATTERN.matcher(cleanText);
        List<StyleTextPair> result = new ArrayList<>();
        int lastEnd = 0;

        while (bracketMatcher.find()) {
            if (bracketMatcher.start() > lastEnd) {
                result.addAll(parseAnsi(text.substring(lastEnd, bracketMatcher.start())));
            }

            String keyword = bracketMatcher.group(2).toUpperCase();
            TextColor keywordColor = switch (keyword) {
                case "WARNING", "WARN" -> TextColor.fromRgb(terminalTextWarnColor);
                case "ERROR" -> TextColor.fromRgb(terminalTextErrorColor);
                case "INFO" -> TextColor.fromRgb(terminalTextInfoColor);
                default -> TextColor.fromRgb(terminalTextColor);
            };
            result.add(new StyleTextPair(Style.EMPTY.withColor(keywordColor), bracketMatcher.group()));
            lastEnd = bracketMatcher.end();
        }

        if (lastEnd < text.length()) {
            result.addAll(parseAnsi(text.substring(lastEnd)));
        }

        return result;
    }

    private List<StyleTextPair> parseAnsi(String text) {
        List<StyleTextPair> result = new ArrayList<>();
        AttributedString attributedString = AttributedString.fromAnsi(text);
        String plain = attributedString.toString();
        if (plain.isEmpty()) return result;

        AttributedStyle currentAttr = attributedString.styleAt(0);
        StringBuilder segmentBuilder = new StringBuilder();
        for (int i = 0; i < plain.length(); i++) {
            AttributedStyle attr = attributedString.styleAt(i);
            if (!attr.equals(currentAttr) && !segmentBuilder.isEmpty()) {
                result.add(new StyleTextPair(convertStyle(currentAttr), segmentBuilder.toString()));
                segmentBuilder.setLength(0);
                currentAttr = attr;
            }
            segmentBuilder.append(plain.charAt(i));
        }
        if (!segmentBuilder.isEmpty()) {
            result.add(new StyleTextPair(convertStyle(currentAttr), segmentBuilder.toString()));
        }
        return result;
    }

    private String removeAllAnsiSequences(String text) {
        if (text.indexOf('\u001B') < 0) return text;
        return ALL_ANSI.matcher(text).replaceAll("");
    }

    private Style convertStyle(AttributedStyle attr) {
        try {
            Field styleField = attr.getClass().getDeclaredField("style");
            styleField.setAccessible(true);
            int styleValue = styleField.getInt(attr);

            Field fForegroundField = attr.getClass().getDeclaredField("F_FOREGROUND");
            fForegroundField.setAccessible(true);
            int F_FOREGROUND = fForegroundField.getInt(attr);

            Field fgColorExpField = attr.getClass().getDeclaredField("FG_COLOR_EXP");
            fgColorExpField.setAccessible(true);
            int FG_COLOR_EXP = fgColorExpField.getInt(attr);

            if ((styleValue & F_FOREGROUND) != 0) {
                int index = (styleValue >> FG_COLOR_EXP) & 0xFF;
                int rgb = get256ColorRGB(index);
                return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
            }
        } catch (Exception ignored) {}
        return Style.EMPTY.withColor(TextColor.fromRgb(terminalTextColor));
    }

    private int get256ColorRGB(int index) {
        if (index < 16) {
            return getStandardColorRGB(index);
        } else if (index < 232) {
            index -= 16;
            int r = (index / 36) % 6 * 51;
            int g = (index / 6) % 6 * 51;
            int b = index % 6 * 51;
            return (r << 16) | (g << 8) | b;
        } else {
            int gray = 8 + (index - 232) * 10;
            return (gray << 16) | (gray << 8) | gray;
        }
    }

    private int getStandardColorRGB(int index) {
        return switch (index) {
            case 0 -> 0x000000; case 1 -> 0xAA0000; case 2 -> 0x00AA00; case 3 -> 0xAA5500;
            case 4 -> 0x0000AA; case 5 -> 0xAA00AA; case 6 -> 0x00AAAA; case 7 -> 0xAAAAAA;
            case 8 -> 0x555555; case 9 -> 0xFF5555; case 10 -> 0x55FF55; case 11 -> 0xFFFF55;
            case 12 -> 0x5555FF; case 13 -> 0xFF55FF; case 14 -> 0x55FFFF;
            default -> 0xFFFFFF;
        };
    }

    private List<LineText> wrapStyledText(List<StyleTextPair> segments, int maxWidth) {
        if (segments.isEmpty()) return Collections.emptyList();

        List<LineText> wrappedLines = new ArrayList<>();
        MutableText currentLine = Text.literal("");
        StringBuilder plainBuilder = new StringBuilder();
        int currentWidth = 0;

        for (StyleTextPair segment : segments) {
            String text = segment.text;
            Style style = segment.style;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                int charWidth = tr.getWidth(String.valueOf(c));
                if (currentWidth + charWidth > maxWidth) {
                    wrappedLines.add(new LineText(currentLine.asOrderedText(), plainBuilder.toString()));
                    currentLine = Text.literal("");
                    plainBuilder.setLength(0);
                    currentWidth = 0;
                }
                currentLine.append(Text.literal(String.valueOf(c)).setStyle(style));
                plainBuilder.append(c);
                currentWidth += charWidth;
            }
        }

        if (currentWidth > 0) {
            wrappedLines.add(new LineText(currentLine.asOrderedText(), plainBuilder.toString()));
        }

        return wrappedLines;
    }
}