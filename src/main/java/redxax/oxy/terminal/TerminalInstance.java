package redxax.oxy.terminal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import redxax.oxy.SSHManager;
import redxax.oxy.input.InputHandler;
import redxax.oxy.input.InputProcessor;
import redxax.oxy.servers.ServerInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TerminalInstance {

    final MultiTerminalScreen parentScreen;
    public final UUID terminalId;
    public final TerminalRenderer renderer;
    public final InputHandler inputHandler;
    final SSHManager sshManager;
    protected ServerInfo serverInfo;

    private List<String> commandHistory;
    private int historyIndex;

    public TerminalInstance(MinecraftClient client, MultiTerminalScreen parent, UUID id) {
        this.parentScreen = parent;
        this.terminalId = id;
        this.sshManager = new SSHManager(this);
        this.renderer = new TerminalRenderer(client, this);
        this.inputHandler = new InputHandler(client, this);
        if (parent == null) {
            this.commandHistory = new ArrayList<>();
            this.historyIndex = 0;
        }
        inputHandler.launchTerminal();
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
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
        if (parentScreen != null) {
            return parentScreen.commandHistory;
        }
        return this.commandHistory;
    }

    public int getHistoryIndex() {
        if (parentScreen != null) {
            return parentScreen.historyIndex;
        }
        return this.historyIndex;
    }

    public void setHistoryIndex(int index) {
        if (parentScreen != null) {
            parentScreen.historyIndex = index;
        } else {
            this.historyIndex = index;
        }
    }

    public SSHManager getSSHManager() {
        return sshManager;
    }
    public TerminalRenderer getRenderer() {
        return renderer;
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
        return inputHandler.getInputProcessor();
    }

    public void launchServerProcess() {
        TerminalProcessManager processManager = inputHandler.getTerminalProcessManager();
        if (processManager != null) {
            processManager.shutdown();
        }
        processManager = new TerminalProcessManager(this, sshManager);
        processManager.launchTerminal();
    }

    public Boolean charTyped(char chr, int modifiers) {
        return inputHandler.charTyped(chr);
    }

    protected void setServerInfo(ServerInfo sInfo) {
        this.serverInfo = sInfo;
    }

}
