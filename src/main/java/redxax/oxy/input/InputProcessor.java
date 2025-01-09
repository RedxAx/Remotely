package redxax.oxy.input;

import redxax.oxy.SSHManager;
import redxax.oxy.TerminalInstance;
import redxax.oxy.ServerTerminalInstance;
import redxax.oxy.servers.ServerState;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import java.io.IOException;

public class InputProcessor {
    private final StringBuilder inputBuffer = new StringBuilder();
    private int cursorPosition = 0;
    private final MinecraftClient minecraftClient;
    private final TerminalInstance terminalInstance;
    private final SSHManager sshManager;
    public final TabCompletionHandler tabCompletionHandler;
    public final CommandExecutor commandExecutor;

    public InputProcessor(MinecraftClient client, TerminalInstance terminalInstance, SSHManager sshManager, TabCompletionHandler tabCompletionHandler, CommandExecutor commandExecutor) {
        this.minecraftClient = client;
        this.terminalInstance = terminalInstance;
        this.sshManager = sshManager;
        this.tabCompletionHandler = tabCompletionHandler;
        this.commandExecutor = commandExecutor;
    }

    public boolean charTyped(char chr) {
        if (terminalInstance instanceof ServerTerminalInstance) {
            ServerTerminalInstance sti = (ServerTerminalInstance) terminalInstance;
            if (sti.serverInfo.state != ServerState.RUNNING && sti.serverInfo.state != ServerState.STARTING) {
                return false;
            }
        }
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
        if (chr == '`' || chr == ' ') {
            return false;
        }
        if (chr >= 32 && chr != 127) {
            inputBuffer.insert(cursorPosition, chr);
            cursorPosition++;
            tabCompletionHandler.resetTabCompletion();
            tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
            terminalInstance.renderer.resetCursorBlink();
            terminalInstance.scrollToBottom();
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        if (terminalInstance instanceof ServerTerminalInstance) {
            ServerTerminalInstance sti = (ServerTerminalInstance) terminalInstance;
            if (sti.serverInfo.state != ServerState.RUNNING && sti.serverInfo.state != ServerState.STARTING) {
                return false;
            }
        }
        boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (sshManager.isAwaitingPassword()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ENTER:
                case GLFW.GLFW_KEY_KP_ENTER:
                    String password = sshManager.getSshPassword();
                    sshManager.setSshPassword("");
                    inputBuffer.setLength(0);
                    cursorPosition = 0;
                    terminalInstance.appendOutput("\n");
                    sshManager.setAwaitingPassword(false);
                    sshManager.connectSSHWithPassword(password);
                    return true;
                case GLFW.GLFW_KEY_BACKSPACE:
                    if (!sshManager.getSshPassword().isEmpty()) {
                        sshManager.setSshPassword(sshManager.getSshPassword().substring(0, sshManager.getSshPassword().length() - 1));
                        if (inputBuffer.length() > 0) {
                            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                            cursorPosition--;
                        }
                        terminalInstance.scrollToBottom();
                    }
                    return true;
                case GLFW.GLFW_KEY_V:
                    if (ctrlHeld) {
                        String clipboard = this.minecraftClient.keyboard.getClipboard();
                        sshManager.setSshPassword(sshManager.getSshPassword() + clipboard);
                        for (int i = 0; i < clipboard.length(); i++) {
                            inputBuffer.append('*');
                        }
                        cursorPosition += clipboard.length();
                        terminalInstance.scrollToBottom();
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB:
                int wordStart = findWordStart(inputBuffer, cursorPosition);
                StringBuilder partial = new StringBuilder(inputBuffer.substring(wordStart, cursorPosition));
                tabCompletionHandler.handleTabCompletion(inputBuffer, cursorPosition);
                String suggestion = tabCompletionHandler.getTabCompletionSuggestion();
                if (!suggestion.isEmpty()) {
                    inputBuffer.replace(wordStart, cursorPosition, partial.toString() + suggestion);
                    cursorPosition = wordStart + partial.length() + suggestion.length();
                    tabCompletionHandler.resetTabCompletion();
                }
                updateTabCompletionCurrentDirectory();
                terminalInstance.renderer.resetCursorBlink();
                terminalInstance.scrollToBottom();
                return true;
            case GLFW.GLFW_KEY_SPACE:
                inputBuffer.insert(cursorPosition, ' ');
                cursorPosition++;
                tabCompletionHandler.resetTabCompletion();
                tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                terminalInstance.renderer.resetCursorBlink();
                terminalInstance.scrollToBottom();
                return true;
            case GLFW.GLFW_KEY_C:
                if (ctrlHeld) {
                    terminalInstance.renderer.copySelectionToClipboard();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                try {
                    String command = inputBuffer.toString().trim();
                    commandExecutor.executeCommand(command, inputBuffer);
                    updateTabCompletionCurrentDirectory();
                    inputBuffer.setLength(0);
                    cursorPosition = 0;
                    tabCompletionHandler.resetTabCompletion();
                    tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                    terminalInstance.renderer.resetCursorBlink();
                    terminalInstance.scrollToBottom();
                    terminalInstance.setHistoryIndex(terminalInstance.getCommandHistory().size());
                } catch (IOException e) {
                    terminalInstance.appendOutput("ERROR: " + e.getMessage() + "\n");
                }
                return true;
            case GLFW.GLFW_KEY_UP:
                if (terminalInstance.getHistoryIndex() > 0) {
                    terminalInstance.setHistoryIndex(terminalInstance.getHistoryIndex() - 1);
                    inputBuffer.setLength(0);
                    inputBuffer.append(terminalInstance.getCommandHistory().get(terminalInstance.getHistoryIndex()));
                    cursorPosition = inputBuffer.length();
                }
                tabCompletionHandler.resetTabCompletion();
                tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                terminalInstance.renderer.resetCursorBlink();
                return true;
            case GLFW.GLFW_KEY_DOWN:
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
                tabCompletionHandler.resetTabCompletion();
                tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                terminalInstance.renderer.resetCursorBlink();
                return true;
            case GLFW.GLFW_KEY_BACKSPACE:
                if (ctrlHeld) {
                    int newCursorPos = moveCursorLeftWord(cursorPosition);
                    if (newCursorPos != cursorPosition) {
                        inputBuffer.delete(newCursorPos, cursorPosition);
                        cursorPosition = newCursorPos;
                        tabCompletionHandler.resetTabCompletion();
                        tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                        terminalInstance.renderer.resetCursorBlink();
                        terminalInstance.scrollToBottom();
                    }
                } else {
                    if (cursorPosition > 0) {
                        inputBuffer.deleteCharAt(cursorPosition - 1);
                        cursorPosition--;
                        tabCompletionHandler.resetTabCompletion();
                        tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                        terminalInstance.renderer.resetCursorBlink();
                        terminalInstance.scrollToBottom();
                    }
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                shutdown();
                return true;
            case GLFW.GLFW_KEY_LEFT:
                if (ctrlHeld) {
                    cursorPosition = moveCursorLeftWord(cursorPosition);
                } else {
                    if (cursorPosition > 0) {
                        cursorPosition--;
                    }
                }
                tabCompletionHandler.resetTabCompletion();
                tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                terminalInstance.renderer.resetCursorBlink();
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                if (ctrlHeld) {
                    cursorPosition = moveCursorRightWord(cursorPosition);
                } else {
                    if (cursorPosition < inputBuffer.length()) {
                        cursorPosition++;
                    }
                }
                tabCompletionHandler.resetTabCompletion();
                tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                terminalInstance.renderer.resetCursorBlink();
                return true;
            case GLFW.GLFW_KEY_V:
                if (ctrlHeld) {
                    String clipboard = this.minecraftClient.keyboard.getClipboard();
                    wordStart = findWordStart(inputBuffer, cursorPosition);
                    inputBuffer.replace(wordStart, cursorPosition, clipboard);
                    cursorPosition = wordStart + clipboard.length();
                    tabCompletionHandler.resetTabCompletion();
                    tabCompletionHandler.updateTabCompletionSuggestion(inputBuffer);
                    terminalInstance.renderer.resetCursorBlink();
                    terminalInstance.scrollToBottom();
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
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

    private void shutdown() {
        terminalInstance.shutdown();
    }

    public StringBuilder getInputBuffer() {
        return inputBuffer;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    private void updateTabCompletionCurrentDirectory() {
        String newDirectory = commandExecutor.getTerminalProcessManager().getCurrentDirectory();
        tabCompletionHandler.setCurrentDirectory(newDirectory);
    }

    private int findWordStart(StringBuilder buffer, int position) {
        if (position == 0) return 0;
        int index = position - 1;
        while (index >= 0 && !Character.isWhitespace(buffer.charAt(index))) {
            index--;
        }
        return index + 1;
    }
}
