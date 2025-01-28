package redxax.oxy.input;

import redxax.oxy.SSHManager;
import redxax.oxy.terminal.TerminalInstance;
import net.minecraft.client.MinecraftClient;
import redxax.oxy.terminal.TerminalProcessManager;

import java.io.IOException;
import java.nio.file.Path;

public class InputHandler {

    final TerminalProcessManager terminalProcessManager;
    private final TabCompletionHandler tabCompletionHandler;
    public final InputProcessor inputProcessor;
    public CommandExecutor commandExecutor;


    public InputHandler(MinecraftClient client, TerminalInstance terminalInstance) {
        SSHManager sshManager = terminalInstance.getSSHManager();

        this.terminalProcessManager = new TerminalProcessManager(terminalInstance, sshManager);
        CommandExecutor commandExecutor = new CommandExecutor(terminalInstance, sshManager, terminalProcessManager.getWriter(), terminalProcessManager);
        this.tabCompletionHandler = new TabCompletionHandler(sshManager, terminalProcessManager.getCurrentDirectory());
        this.inputProcessor = new InputProcessor(client, terminalInstance, sshManager, tabCompletionHandler, commandExecutor);

    }

    public void launchTerminal() {
        terminalProcessManager.launchTerminal();
    }

    public boolean charTyped(char chr) {
        return inputProcessor.charTyped(chr);
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        return inputProcessor.keyPressed(keyCode, modifiers);
    }

    public String getTabCompletionSuggestion() {
        return tabCompletionHandler.getTabCompletionSuggestion();
    }

    public void shutdown() {
        terminalProcessManager.shutdown();
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

    public TerminalProcessManager getTerminalProcessManager() {
        return terminalProcessManager;
    }

    public InputProcessor getInputProcessor() {
        return inputProcessor;
    }

    public String getCurrentDir() {
        return terminalProcessManager.getCurrentDirectory();
    }
}
