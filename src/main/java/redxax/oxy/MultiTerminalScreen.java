package redxax.oxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultiTerminalScreen extends Screen {

    private final MinecraftClient minecraftClient;
    private final RemotelyClient remotelyClient;
    final List<TerminalInstance> terminals;
    final List<String> tabNames;
    int activeTerminalIndex = 0;

    public static final int TAB_HEIGHT = 30;
    private static final int TAB_PADDING = 5;
    private static final int ADD_TAB_BUTTON_WIDTH = 30;
    private static final int TAB_WIDTH = 110;
    private static final int MAX_TABS = 5;

    static final int SCROLL_STEP = 3;

    private float scale = 1.0f;
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 2.0f;

    private boolean isRenaming = false;
    private int renamingTabIndex = -1;
    private StringBuilder renameBuffer = new StringBuilder();

    private long lastRenameBlinkTime = 0;
    private boolean renameCursorVisible = true;
    private long lastRenameInputTime = 0;

    private boolean closedViaEscape = false;

    private String warningMessage = "";

    private static final Path TERMINAL_LOG_DIR = Paths.get(System.getProperty("user.dir"), "remotely_terminal_logs");

    final List<String> commandHistory = new ArrayList<>();
    int historyIndex = -1;

    public MultiTerminalScreen(MinecraftClient minecraftClient, RemotelyClient remotelyClient, List<TerminalInstance> terminals, List<String> tabNames) {
        super(Text.literal("Multi Terminal"));
        this.minecraftClient = minecraftClient;
        this.remotelyClient = remotelyClient;
        this.terminals = terminals;
        this.tabNames = tabNames;
        if (terminals.isEmpty()) {
            addNewTerminal();
        }
        this.activeTerminalIndex = remotelyClient.activeTerminalIndex;
        this.scale = remotelyClient.scale;
    }

    @Override
    protected void init() {
        super.init();
        refreshTabButtons();
    }

    void refreshTabButtons() {
        this.clearChildren();
        for (int i = 0; i < terminals.size(); i++) {
            final int index = i;
            String tabName = tabNames.get(i);
            Text tabLabel = isRenaming && renamingTabIndex == i ? Text.literal(renameBuffer.toString()) : Text.literal(tabName);
            ButtonWidget tabButton = ButtonWidget.builder(tabLabel,
                            button -> {
                                if (!isRenaming) {
                                    setActiveTerminal(index);
                                }
                            })
                    .position(10 + i * TAB_WIDTH, 5)
                    .size(TAB_WIDTH - 10, TAB_HEIGHT - 10)
                    .build();
            this.addDrawableChild(tabButton);
        }
        if (terminals.size() < MAX_TABS) {
            int addButtonX = 10 + terminals.size() * TAB_WIDTH;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> addNewTerminal())
                    .position(addButtonX, 5)
                    .size(ADD_TAB_BUTTON_WIDTH, TAB_HEIGHT - 10)
                    .build());
        }
    }

    private void addNewTerminal() {
        if (terminals.size() >= MAX_TABS) {
            warningMessage = "Maximum of " + MAX_TABS + " tabs reached!";
            minecraftClient.player.sendMessage(Text.literal(warningMessage), false);
            return;
        }
        try {
            if (!Files.exists(TERMINAL_LOG_DIR)) {
                Files.createDirectories(TERMINAL_LOG_DIR);
            }
        } catch (IOException e) {
            warningMessage = "Failed to create terminal log directory!";
            minecraftClient.player.sendMessage(Text.literal(warningMessage), false);
            return;
        }
        UUID terminalId = UUID.randomUUID();
        TerminalInstance newTerminal = new TerminalInstance(minecraftClient, this, terminalId);
        terminals.add(newTerminal);
        tabNames.add("Tab " + terminals.size());
        activeTerminalIndex = terminals.size() - 1;
        setActiveTerminal(activeTerminalIndex);
        refreshTabButtons();
    }

    private void closeTerminal(int index) {
        if (terminals.size() <= 1) {
            this.close();
        }
        TerminalInstance terminal = terminals.get(index);
        terminal.shutdown();
        terminals.remove(index);
        tabNames.remove(index);
        if (activeTerminalIndex >= terminals.size()) {
            activeTerminalIndex = terminals.size() - 1;
        }
        setActiveTerminal(activeTerminalIndex);
        refreshTabButtons();
    }

    private void setActiveTerminal(int index) {
        if (index >= 0 && index < terminals.size()) {
            activeTerminalIndex = index;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        if (!warningMessage.isEmpty()) {
            context.drawText(minecraftClient.textRenderer, Text.literal(warningMessage), 10, TAB_HEIGHT + 10, 0xFFAA0000, false);
            warningMessage = "";
        }
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            activeTerminal.render(context, mouseX, mouseY, delta, this.width, this.height, scale);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            if (activeTerminal.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        int tabY = 5;
        int tabHeight = TAB_HEIGHT - 10;
        int tabWidth = TAB_WIDTH - 10;
        for (int i = 0; i < terminals.size(); i++) {
            int tabX = 10 + i * TAB_WIDTH;
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabHeight) {
                if (button == 1) {
                    initiateTabRename(i);
                    return true;
                } else if (button == 2) {
                    closeTerminal(i);
                    return true;
                } else if (button == 0) {
                    if (!isRenaming) {
                        setActiveTerminal(i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            if (activeTerminal.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            if (activeTerminal.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void initiateTabRename(int tabIndex) {
        isRenaming = true;
        renamingTabIndex = tabIndex;
        renameBuffer.setLength(0);
        renameBuffer.append(tabNames.get(tabIndex));
        lastRenameInputTime = System.currentTimeMillis();
        refreshTabButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean ctrlHeld = InputUtil.isKeyPressed(
                this.minecraftClient.getWindow().getHandle(),
                GLFW.GLFW_KEY_LEFT_CONTROL) ||
                InputUtil.isKeyPressed(this.minecraftClient.getWindow().getHandle(),
                        GLFW.GLFW_KEY_RIGHT_CONTROL);

        if (ctrlHeld) {
            scale += verticalAmount > 0 ? 0.1f : -0.1f;
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
            return true;
        } else {
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                activeTerminal.scroll((int) verticalAmount, this.height - TAB_HEIGHT - 10);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closedViaEscape = true;
            this.close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                activeTerminal.scrollToTop(this.height - TAB_HEIGHT - 10);
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (!terminals.isEmpty()) {
                TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
                activeTerminal.scrollToBottom();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            closeTerminal(activeTerminalIndex);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            int nextIndex = (activeTerminalIndex + 1) % terminals.size();
            setActiveTerminal(nextIndex);
            return true;
        }

        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_EQUAL) {
            scale += 0.1f;
            scale = Math.min(MAX_SCALE, scale);
            return true;
        }

        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_MINUS) {
            scale -= 0.1f;
            scale = Math.max(MIN_SCALE, scale);
            return true;
        }

        if (isRenaming) {
            lastRenameInputTime = System.currentTimeMillis();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isRenaming = false;
                renamingTabIndex = -1;
                refreshTabButtons();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
                String newName = renameBuffer.toString().trim();
                if (!newName.isEmpty()) {
                    tabNames.set(renamingTabIndex, newName);
                }
                isRenaming = false;
                renamingTabIndex = -1;
                refreshTabButtons();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (renameBuffer.length() > 0) {
                    renameBuffer.deleteCharAt(renameBuffer.length() - 1);
                    refreshTabButtons();
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                return true;
            }
            return true;
        }

        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            return activeTerminal.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (isRenaming && renamingTabIndex != -1) {
            lastRenameInputTime = System.currentTimeMillis();
            if (chr == '\r') {
                String newName = renameBuffer.toString().trim();
                if (!newName.isEmpty()) {
                    tabNames.set(renamingTabIndex, newName);
                }
                isRenaming = false;
                renamingTabIndex = -1;
                refreshTabButtons();
                return true;
            } else if (chr == '\b') {
                if (renameBuffer.length() > 0) {
                    renameBuffer.deleteCharAt(renameBuffer.length() - 1);
                    refreshTabButtons();
                }
                return true;
            } else {
                renameBuffer.append(chr);
                refreshTabButtons();
                return true;
            }
        }

        if (!terminals.isEmpty()) {
            TerminalInstance activeTerminal = terminals.get(activeTerminalIndex);
            return activeTerminal.charTyped(chr, keyCode) || super.charTyped(chr, keyCode);
        }
        return super.charTyped(chr, keyCode);
    }

    @Override
    public void close() {
        super.close();
        if (closedViaEscape) {
            saveAllTerminals();
            remotelyClient.onMultiTerminalScreenClosed();
        }
        remotelyClient.activeTerminalIndex = this.activeTerminalIndex;
        remotelyClient.scale = this.scale;
    }

    public void shutdownAllTerminals() {
        if (terminals.isEmpty()) {
            return;
        }
        for (TerminalInstance terminal : terminals) {
            terminal.shutdown();
        }
        terminals.clear();
        tabNames.clear();
    }

    private void saveAllTerminals() {
        try {
            if (!Files.exists(TERMINAL_LOG_DIR)) {
                Files.createDirectories(TERMINAL_LOG_DIR);
            }
            for (int i = 0; i < terminals.size(); i++) {
                TerminalInstance terminal = terminals.get(i);
                String tabName = tabNames.get(i);
                terminal.saveTerminalOutput(TERMINAL_LOG_DIR.resolve(tabName + ".log"));
            }
        } catch (IOException e) {
            minecraftClient.player.sendMessage(Text.literal("Failed to save terminal logs."), false);
        }
    }
}
