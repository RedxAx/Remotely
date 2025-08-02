package redxax.oxy.remotely.terminal;

import com.jediterm.core.Color;
import com.jediterm.core.compatibility.Point;
import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.util.Ascii;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.*;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.SSHManager;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerState;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.util.Notification;
import redxax.oxy.remotely.util.Sound;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.SoundUtils.playSound;


public class TerminalWidget extends AnimatedWidget implements TerminalDisplay {

    private final ServerInfo serverInfo;
    private final SSHManager sshManager;
    public final TerminalProcessManager processManager;

    private final JediTerminal myTerminal;
    private final TerminalTextBuffer myTextBuffer;
    private TerminalStarter myTerminalStarter;
    private final SettingsProvider mySettingsProvider;
    private final TerminalExecutorServiceManager myExecutorServiceManager;
    private final TypeAheadModel typeAheadModel;
    private final TerminalTypeAheadManager typeAheadManager;

    private int myCursorX = 1;
    private int myCursorY = 1;
    private boolean myCursorVisible = true;
    private CursorShape myCursorShape = CursorShape.BLINK_BLOCK;
    private long myLastCursorChange = System.currentTimeMillis();

    private float scrollY = 0;
    private float targetScrollY = 0;

    private TerminalSelection mySelection;
    private Point mySelectionStartPoint;
    private final AtomicBoolean myNeedsRepaint = new AtomicBoolean(true);
    Identifier font = Identifier.of("remotely", "mono");


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

        mySettingsProvider = new RemotelySettingsProvider();
        myExecutorServiceManager = new ExecutorServiceManager();
        StyleState styleState = new StyleState();
        styleState.setDefaultStyle(mySettingsProvider.getDefaultStyle());

        int termWidth = width / getCharWidth();
        int termHeight = height / (tr.fontHeight + 2);
        myTextBuffer = new TerminalTextBuffer(termWidth, termHeight, styleState);
        myTerminal = new MyJediTerminal(this, myTextBuffer, styleState);

        typeAheadModel = new TypeAheadModel(myTerminal, myTextBuffer, mySettingsProvider);
        typeAheadManager = new TerminalTypeAheadManager(typeAheadModel);
        DebouncerImpl debouncer = new DebouncerImpl(typeAheadManager::debounce, TerminalTypeAheadManager.MAX_TERMINAL_DELAY, myExecutorServiceManager);
        typeAheadManager.setClearPredictionsDebouncer(debouncer);

        if (this.serverInfo != null && this.serverInfo.isRemote) {
            this.sshManager = RemotelyClient.INSTANCE.getSSHManagerForHost(this.serverInfo.remoteHost);
            if (this.sshManager != null) {
                this.sshManager.setTerminalWidget(this);
            } else {
                appendOutput("Could not establish SSH connection for remote server.\n");
            }
            this.processManager = null;
        } else {
            this.sshManager = null;
            this.processManager = new TerminalProcessManager(this, null);
        }

        myTerminalStarter = null;

        animateElevation = enableHoverColors = false;

        myTextBuffer.addModelListener(this::scheduleRepaint);
    }

    public void start() {
        myExecutorServiceManager.getUnboundedExecutorService().submit(() -> {
            TtyConnector ttyConnector = null;
            try {
                if (serverInfo != null && serverInfo.isRemote) {
                    if (sshManager != null) {
                        ttyConnector = sshManager.createTtyConnector();
                    }
                } else if (processManager != null) {
                    ttyConnector = processManager.createTtyConnector();
                }
            } catch (Exception e) {
                appendOutput("Failed to create TtyConnector: " + e.getMessage() + "\n");
            }

            if (ttyConnector != null) {
                myTerminalStarter = new TerminalStarter(myTerminal, ttyConnector, new TtyBasedArrayDataStream(ttyConnector, typeAheadManager::onTerminalStateChanged), typeAheadManager, myExecutorServiceManager);
                myTerminal.setTerminalOutput(myTerminalStarter);
                myTerminalStarter.start();
            }
        });
    }

    private void scheduleRepaint() {
        myNeedsRepaint.set(true);
    }

    public void shutdown() {
        if (myTerminalStarter != null) {
            myTerminalStarter.close();
        }
        if (sshManager != null) {
            sshManager.shutdown();
        }
        myExecutorServiceManager.shutdownWhenAllExecuted();
    }

    public void appendOutput(String text) {
        myTerminal.writeUnwrappedString(text);
    }

    private void detectServerState(String line) {
        if (serverInfo != null) {
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
    }

    @Override
    public void tick() {
        super.tick();
    }

    private int getCharWidth() {
        return tr.getWidth("R");
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        clampScroll();
        scrollY += (targetScrollY - scrollY) * globalScrollSpeed * deltaTime;
        myNeedsRepaint.getAndSet(false);
        int padding = 2;
        int statusHeight = tr.fontHeight + 4;
        int contentHeight = getHeight() - statusHeight - padding;
        int contentY = getY() + padding;
        int lineHeight = tr.fontHeight + 2;

        ctx.enableScissor(getX() + padding, getY(), getX() + getWidth() - padding, contentY + contentHeight);

        myTextBuffer.lock();
        try {
            int linesScrolled = (int) Math.floor(scrollY / lineHeight);
            int scrollOrigin = -linesScrolled;

            myTextBuffer.processHistoryAndScreenLines(scrollOrigin, (contentHeight / lineHeight) + 2,
                    new StyledTextConsumerAdapter() {
                        @Override
                        public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
                            int lineY = contentY + ((y - startRow) * lineHeight) - (int)(scrollY % lineHeight);
                            if (lineY >= contentY - lineHeight && lineY < contentY + contentHeight) {
                                MutableText lineText = Text.literal(characters.toString());

                                com.jediterm.core.Color foreground = style.getForeground() != null
                                        ? mySettingsProvider.getTerminalColorPalette().getForeground(style.getForeground())
                                        : fromAwtColor(new java.awt.Color(Config.terminalTextColor));
                                lineText.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(foreground.getRGB())).withFont(font));

                                ctx.drawText(tr, lineText, getX() + padding + (x * getCharWidth()), lineY, 0, shadow);
                            }
                        }
                    });

            if (myCursorVisible && isFocused()) {
                long time = System.currentTimeMillis();
                if ((time - myLastCursorChange) > mySettingsProvider.caretBlinkingMs()) {
                    myLastCursorChange = time;
                }
                if ((time - myLastCursorChange) < mySettingsProvider.caretBlinkingMs() / 2) {
                    int cursorScreenY = (myCursorY - 1 + linesScrolled) * lineHeight + contentY - (int)(scrollY % lineHeight);
                    ctx.fill(getX() + padding + (myCursorX - 1) * getCharWidth(), cursorScreenY, getX() + padding + myCursorX * getCharWidth(), cursorScreenY + lineHeight, Config.globalCursorAnimatedColor);
                }
            }
        } finally {
            myTextBuffer.unlock();
        }

        ctx.disableScissor();

        int statusY = getY() + getHeight() - statusHeight;
        drawStatusBar(ctx, getX(), statusY, getWidth(), statusHeight);
    }

    private Color fromAwtColor(java.awt.Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private void drawStatusBar(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, Config.terminalStatusBarColor);
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

        ctx.drawText(tr, leftText, x + 4, y + (h - tr.fontHeight) / 2, Config.terminalTextColor, shadow);
        ctx.drawText(tr, rightText, x + w - tr.getWidth(rightText) - 4, y + (h - tr.fontHeight) / 2, Config.terminalTextColor, shadow);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            targetScrollY += (float) (verticalAmount * (tr.fontHeight + 2) * 3);
            clampScroll();
            return true;
        }
        return false;
    }

    private boolean isAltPressedOnly(int awtModifiers) {
        return (awtModifiers & InputEvent.ALT_MASK) != 0 && (awtModifiers & InputEvent.CTRL_MASK) == 0 && (awtModifiers & InputEvent.SHIFT_MASK) == 0 && (awtModifiers & InputEvent.META_MASK) == 0;
    }

    private static char simpleMapKeyCodeToChar(int awtKeyCode, int awtModifiers) {
        if ((awtModifiers & InputEvent.SHIFT_MASK) != 0) {
            return Character.toUpperCase((char) awtKeyCode);
        }
        return Character.toLowerCase((char) awtKeyCode);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;

        int awtKeyCode = KeyCodeConverter.toAwtKeyCode(keyCode);
        int awtModifiers = KeyCodeConverter.toAwtModifiers(modifiers);

        if (handleAction(awtKeyCode, awtModifiers)) {
            return true;
        }

        byte[] code = myTerminal.getCodeForKey(awtKeyCode, awtModifiers);
        if (code != null) {
            if (myTerminalStarter != null) {
                myTerminalStarter.sendBytes(code, true);
                if (mySettingsProvider.scrollToBottomOnTyping()) {
                    scrollToBottom();
                }
            }
            return true;
        }

        if (isAltPressedOnly(awtModifiers) && mySettingsProvider.altSendsEscape()) {
            char c = (char)awtKeyCode;
            if (Character.isLetterOrDigit(c)) {
                if (myTerminalStarter != null) {
                    myTerminalStarter.sendString(new String(new char[]{Ascii.ESC, simpleMapKeyCodeToChar(awtKeyCode, awtModifiers)}), true);
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isMacOS() {
        return RemotelyClient.os != null && RemotelyClient.os.toLowerCase().contains("mac");
    }

    private boolean isCopyAction(int code, int modifiers) {
        if (isMacOS()) {
            return code == KeyEvent.VK_C && modifiers == InputEvent.META_DOWN_MASK;
        } else {
            return code == KeyEvent.VK_C && modifiers == (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        }
    }

    private boolean isPasteAction(int code, int modifiers) {
        if (isMacOS()) {
            return code == KeyEvent.VK_V && modifiers == InputEvent.META_DOWN_MASK;
        } else {
            return code == KeyEvent.VK_V && modifiers == (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        }
    }

    private boolean handleAction(int code, int modifiers) {
        if (isCopyAction(code, modifiers)) {
            handleCopy();
            return true;
        }
        if (isPasteAction(code, modifiers)) {
            handlePaste();
            return true;
        }
        return false;
    }

    private void handleCopy() {
        if (mySelection != null) {
            mc.keyboard.setClipboard(SelectionUtil.getSelectionText(mySelection.getStart(), mySelection.getEnd(), myTextBuffer));
        }
    }

    private void handlePaste() {
        String text = mc.keyboard.getClipboard();
        if (text != null) {
            try {
                if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                    text = text.replace("\r\n", "\n");
                }
                text = text.replace('\n', '\r');

                if (myTerminal.isModelEnabled(TerminalMode.BracketedPasteMode)) {
                    text = "\u001b[200~" + text + "\u001b[201~";
                }
                if (myTerminalStarter != null) {
                    myTerminalStarter.sendString(text, true);
                }
            } catch (Exception e) {
                new Notification("Filed To Paste", e.getMessage(), Notification.Type.ERROR);
            }
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused()) return false;
        if (myTerminalStarter != null) {
            myTerminalStarter.sendString(String.valueOf(chr), true);
        }
        return true;
    }

    public void executeCommand(String command) {
        if (myTerminalStarter != null) {
            myTerminalStarter.sendString(command + "\r", true);
        }
    }

    public void startRemoteServer() {
        if (serverInfo == null || !serverInfo.isRemote || sshManager == null || !sshManager.isSSH()) {
            appendOutput("Cannot start remote server: not a remote server or SSH not connected.\n");
            return;
        }
        try {
            String command = sshManager.launchRemoteServer(serverInfo.path);
            executeCommand(command);
        } catch (Exception e) {
            appendOutput("Failed to start remote server: " + e.getMessage() + "\n");
        }
    }

    private void scrollToBottom() {
        targetScrollY = 0;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, myTextBuffer.getHistoryLinesCount() * (tr.fontHeight + 2));
        targetScrollY = MathHelper.clamp(targetScrollY, 0, maxScroll);
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public void setServerState(ServerState state) {
        if (this.serverInfo != null) {
            this.serverInfo.state = state;
        }
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
        String fullOutput = myTextBuffer.getScreenLines();
        java.nio.file.Files.writeString(path, fullOutput);
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    public void setCursor(int x, int y) {
        myCursorX = x;
        myCursorY = y;
        scheduleRepaint();
    }

    @Override
    public void setCursorShape(@Nullable CursorShape cursorShape) {
        myCursorShape = cursorShape != null ? cursorShape : CursorShape.BLINK_BLOCK;
        scheduleRepaint();
    }

    @Override
    public void beep() {
        playSound(Sound.RECEIVEERROR);
    }

    @Override
    public void onResize(@NotNull TermSize newTermSize, @NotNull RequestOrigin origin) {
        scheduleRepaint();
    }

    @Override
    public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
        if (targetScrollY > 0) {
            targetScrollY -= dy * (tr.fontHeight + 2);
        }
        scheduleRepaint();
    }

    @Override
    public void setCursorVisible(boolean isCursorVisible) {
        myCursorVisible = isCursorVisible;
        scheduleRepaint();
    }

    @Override
    public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) {
        scheduleRepaint();
    }

    @Override
    public String getWindowTitle() {
        return "Remotely Terminal";
    }

    @Override
    public void setWindowTitle(@NotNull String windowTitle) {
        mc.getWindow().setTitle(windowTitle);
    }

    @Override
    public @Nullable TerminalSelection getSelection() {
        return mySelection;
    }

    @Override
    public void terminalMouseModeSet(@NotNull MouseMode mouseMode) {
    }

    @Override
    public void setMouseFormat(@NotNull MouseFormat mouseFormat) {
    }

    @Override
    public boolean ambiguousCharsAreDoubleWidth() {
        return false;
    }

    private class MyJediTerminal extends JediTerminal {
        public MyJediTerminal(@NotNull TerminalDisplay display, @NotNull TerminalTextBuffer buf, @NotNull StyleState initialStyleState) {
            super(display, buf, initialStyleState);
        }

        @Override
        public void eraseInDisplay(int arg) {
            super.eraseInDisplay(arg);
            if (arg == 2 || arg == 3) {
                scrollToBottom();
            }
        }
    }
}