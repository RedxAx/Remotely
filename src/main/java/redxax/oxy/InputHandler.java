package redxax.oxy;

import redxax.oxy.input.*;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Path;

public class InputHandler {

    private final TerminalProcessManager terminalProcessManager;
    private final CommandLogger commandLogger;
    private final TabCompletionHandler tabCompletionHandler;
    final InputProcessor inputProcessor;

    public InputHandler(MinecraftClient client, TerminalInstance terminalInstance) {
        SSHManager sshManager = terminalInstance.getSSHManager();

        this.terminalProcessManager = new TerminalProcessManager(terminalInstance, sshManager);
        this.commandLogger = new CommandLogger(terminalInstance);
        CommandExecutor commandExecutor = new CommandExecutor(terminalInstance, sshManager, terminalProcessManager.getWriter(), commandLogger, terminalProcessManager);
        this.tabCompletionHandler = new TabCompletionHandler(terminalInstance, sshManager, commandExecutor, terminalProcessManager.getCurrentDirectory());
        this.inputProcessor = new InputProcessor(client, terminalInstance, sshManager, tabCompletionHandler, commandExecutor);
    }

    public void launchTerminal() {
        terminalProcessManager.launchTerminal();
    }

    public boolean charTyped(char chr, int keyCode) {
        return inputProcessor.charTyped(chr, keyCode);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return inputProcessor.keyPressed(keyCode, scanCode, modifiers);
    }

    public String getTabCompletionSuggestion() {
        return tabCompletionHandler.getTabCompletionSuggestion();
    }

    public void shutdown() {
        terminalProcessManager.shutdown();
    }

    public void logCommand(String command) {
        commandLogger.logCommand(command);
    }

    public StringBuilder getInputBuffer() {
        return inputProcessor.getInputBuffer();
    }

    public int getCursorPosition() {
        return inputProcessor.getCursorPosition();
    }

    public void saveTerminalOutput(Path path) throws IOException {
        terminalProcessManager.saveTerminalOutput(path);
    }

    public void loadTerminalOutput(Path path) throws IOException {
        terminalProcessManager.loadTerminalOutput(path);
    }
}
