package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import redxax.oxy.input.InputHandler;
import redxax.oxy.input.InputProcessor;

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

    public TerminalInstance(MinecraftClient client, MultiTerminalScreen parent, UUID id) {
        this.parentScreen = parent;
        this.terminalId = id;
        this.sshManager = new SSHManager(this);
        this.renderer = new TerminalRenderer(client, this);
        this.inputHandler = new InputHandler(client, this);
        inputHandler.launchTerminal();
    }

    public void render(DrawContext context, int screenWidth, int screenHeight, float scale) {
        renderer.render(context, screenWidth, screenHeight, scale);
    }

    public boolean charTyped(char chr) {
        return inputHandler.charTyped(chr);
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        return inputHandler.keyPressed(keyCode, modifiers);
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

    public SSHManager getSSHManager() {
        return sshManager;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return renderer.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return renderer.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        return renderer.mouseDragged(mouseX, mouseY, button);
    }

    public InputProcessor getInputHandler() {
        return inputHandler.inputProcessor;
    }
}
