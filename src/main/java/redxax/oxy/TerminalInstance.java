package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import redxax.oxy.input.CommandLogger;
import redxax.oxy.input.InputProcessor;
import redxax.oxy.input.TerminalProcessManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class TerminalInstance {

    final MultiTerminalScreen parentScreen;
    public final UUID terminalId;
    public final TerminalRenderer renderer;
    final InputHandler inputHandler;
    final SSHManager sshManager;
    private final CommandLogger commandLogger;

    public TerminalInstance(MinecraftClient client, MultiTerminalScreen parent, UUID id) {
        this.parentScreen = parent;
        this.terminalId = id;
        this.sshManager = new SSHManager(this);
        this.renderer = new TerminalRenderer(client, this);
        this.inputHandler = new InputHandler(client, this);
        this.commandLogger = new CommandLogger(this);
        inputHandler.launchTerminal();
        loadCommandHistory();
    }

    private void loadCommandHistory() {
        List<String> commands = commandLogger.loadCommands();
        parentScreen.commandHistory.addAll(commands);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta, int screenWidth, int screenHeight, float scale) {
        renderer.render(context, mouseX, mouseY, delta, screenWidth, screenHeight, scale);
    }

    public boolean charTyped(char chr, int keyCode) {
        return inputHandler.charTyped(chr, keyCode);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return inputHandler.keyPressed(keyCode, scanCode, modifiers);
    }

    public void scroll(int direction, int terminalHeight) {
        renderer.scroll(direction, terminalHeight);
    }

    public void scrollToTop(int terminalHeight) {
        renderer.scrollToTop(terminalHeight);
    }

    public void scrollToBottom() {
        renderer.scrollToBottom();
    }

    public void shutdown() {
        inputHandler.shutdown();
    }

    public void saveTerminalOutput(Path path) {
        try {
            inputHandler.saveTerminalOutput(path);
        } catch (IOException e) {
            renderer.appendOutput("Failed to save terminal output: " + e.getMessage() + "\n");
        }
    }

    public void loadTerminalOutput(Path path) {
        try {
            inputHandler.loadTerminalOutput(path);
        } catch (IOException e) {
            renderer.appendOutput("Failed to load terminal output: " + e.getMessage() + "\n");
        }
    }

    public void appendOutput(String text) {
        renderer.appendOutput(text);
    }


    public List<String> getCommandHistory() {
        return parentScreen.commandHistory;
    }

    public int getHistoryIndex() {
        return parentScreen.historyIndex;
    }

    public void setHistoryIndex(int index) {
        parentScreen.historyIndex = index;
    }

    public void logCommand(String command) {
        inputHandler.logCommand(command);
    }

    public SSHManager getSSHManager() {
        return sshManager;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return renderer.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return renderer.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return renderer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public InputProcessor getInputHandler() {
        return inputHandler.inputProcessor;
    }
}
