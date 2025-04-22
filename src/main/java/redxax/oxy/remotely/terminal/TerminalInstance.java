package redxax.oxy.remotely.terminal;

import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.input.InputHandler;
import redxax.oxy.remotely.input.InputProcessor;
import redxax.oxy.remotely.servers.ServerInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TerminalInstance {

    final MultiTerminalScreen parentScreen;
    public final UUID terminalId;
    public final TerminalRenderer renderer;
    public final InputHandler inputHandler;
    final SSHManager sshManager;
    protected ServerInfo serverInfo;

    private List<String> commandHistory;
    private int historyIndex;

    public TerminalInstance(Minecraft client, MultiTerminalScreen parent, UUID id) {
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

    public void render(GuiGraphics context, int screenWidth, int screenHeight, float scale) {
        renderer.render(context, screenWidth, screenHeight);
    }

    public boolean charTyped(char chr) {
        return inputHandler.charTyped(chr);
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        return inputHandler.keyPressed(keyCode, modifiers);
    }

    public void scrollToTop(int terminalHeight) {
        renderer.scrollToTop();
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

    public String getCurrentDir() {
        return inputHandler.getCurrentDir();
    }

}
