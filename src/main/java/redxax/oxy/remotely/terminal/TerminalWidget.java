package redxax.oxy.remotely.terminal;

import com.jediterm.core.Color;
import com.jediterm.core.compatibility.Point;
import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.util.Ascii;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.*;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import kotlin.UByteArray;
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
    private boolean myCursorIsShown = true;
    private CursorShape myCursorShape = CursorShape.BLINK_BLOCK;
    private long myLastCursorChange = System.currentTimeMillis();

    private float scrollY = 0;
    private float targetScrollY = 0;

    private TerminalSelection mySelection;
    private Point mySelectionStartPoint;
    private final AtomicBoolean myNeedsRepaint = new AtomicBoolean(true);
    private TermSize myLastTermSize;
    Identifier font = Identifier.of("remotely", "mono");

    private boolean drawBackground = true;
    private MouseMode myMouseMode = MouseMode.MOUSE_REPORTING_NONE;

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

        int termWidth = Math.max(1, width / getCharWidth());
        int termHeight = Math.max(1, height / (tr.fontHeight + 2));
        myLastTermSize = new TermSize(termWidth, termHeight);
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

        int newTermWidth = Math.max(1, getWidth() / getCharWidth());
        int newTermHeight = Math.max(1, getHeight() / (tr.fontHeight + 2));

        TermSize newTermSize = new TermSize(newTermWidth, newTermHeight);
        if (!newTermSize.equals(myLastTermSize)) {
            myLastTermSize = newTermSize;
            if (myTerminalStarter != null) {
                myTerminalStarter.postResize(newTermSize, RequestOrigin.User);
            } else {
                myTerminal.resize(newTermSize, RequestOrigin.User);
            }
        }

        if (isFocused()) {
            long time = System.currentTimeMillis();
            if ((time - myLastCursorChange) > mySettingsProvider.caretBlinkingMs()) {
                myLastCursorChange = time;
                myCursorIsShown = !myCursorIsShown;
                scheduleRepaint();
            }
        }
    }

    private int getCharWidth() {
        return tr.getWidth("R");
    }

    private class OptimizedRenderer extends StyledTextConsumerAdapter {
        private final DrawContext ctx;
        private final int contentY;
        private final int contentHeight;
        private final int lineHeight;
        private final int padding;
        private final boolean drawBackground;
        private com.jediterm.core.Color currentBgColor = null;
        private int runStartColumn = -1;
        private int runEndColumn = -1;
        private int runLineY = -1;

        OptimizedRenderer(DrawContext ctx, int contentY, int contentHeight, int lineHeight, int padding, boolean drawBackground) {
            this.ctx = ctx;
            this.contentY = contentY;
            this.contentHeight = contentHeight;
            this.lineHeight = lineHeight;
            this.padding = padding;
            this.drawBackground = drawBackground;
        }

        public void flushBackgroundRun() {
            if (currentBgColor != null) {
                int startX = getX() + padding + runStartColumn * getCharWidth();
                int endX = getX() + padding + runEndColumn * getCharWidth();
                ctx.fill(startX, runLineY - 1, endX, runLineY + lineHeight - 1, currentBgColor.getRGB());
            }
            currentBgColor = null;
            runStartColumn = -1;
            runEndColumn = -1;
            runLineY = -1;
        }

        @Override
        public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
            int lineY = contentY + ((y - startRow) * lineHeight) - (int) (scrollY % lineHeight);
            if (lineY >= contentY - lineHeight && lineY < contentY + contentHeight) {
                if (drawBackground) {
                    com.jediterm.core.Color background = style.getBackground() != null
                            ? mySettingsProvider.getTerminalColorPalette().getBackground(style.getBackground())
                            : null;

                    if (background == null || !background.equals(currentBgColor) || lineY != runLineY || x != runEndColumn) {
                        flushBackgroundRun();
                        if (background != null) {
                            currentBgColor = background;
                            runStartColumn = x;
                            runLineY = lineY;
                        }
                    }
                    if (currentBgColor != null) {
                        runEndColumn = x + characters.length();
                    }
                } else {
                    flushBackgroundRun();
                }

                MutableText lineText = Text.literal(characters.toString());

                com.jediterm.core.Color foreground = style.getForeground() != null
                        ? mySettingsProvider.getTerminalColorPalette().getForeground(style.getForeground())
                        : fromAwtColor(new java.awt.Color(Config.terminalTextColor));
                lineText.setStyle(net.minecraft.text.Style.EMPTY.withColor(TextColor.fromRgb(foreground.getRGB())).withFont(font));

                ctx.drawText(tr, lineText, getX() + padding + (x * getCharWidth()), lineY, 0, shadow);
            } else {
                flushBackgroundRun();
            }
        }

        @Override
        public void consumeQueue(int x, int y, int nulIndex, int startRow) {
            flushBackgroundRun();
        }
    }

    @Override
    protected void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        clampScroll();
        scrollY += (targetScrollY - scrollY) * globalScrollSpeed * deltaTime;
        myNeedsRepaint.getAndSet(false);
        int padding = 2;
        int contentHeight = getHeight() - padding;
        int contentY = getY() + padding;
        int lineHeight = tr.fontHeight + 2;

        ctx.enableScissor(getX() + padding, getY(), getX() + getWidth() - padding, contentY + contentHeight);

        myTextBuffer.lock();
        try {
            int linesScrolled = (int) Math.floor(scrollY / lineHeight);
            int scrollOrigin = -linesScrolled;

            OptimizedRenderer renderer = new OptimizedRenderer(ctx, contentY, contentHeight, lineHeight, padding, drawBackground);
            myTextBuffer.processHistoryAndScreenLines(scrollOrigin, (contentHeight / lineHeight) + 2, renderer);
            renderer.flushBackgroundRun();

            if (myCursorVisible && isFocused() && myCursorIsShown) {
                int cursorScreenY = contentY + (myCursorY - 1) * lineHeight - (int)(scrollY);
                if (cursorScreenY >= contentY && cursorScreenY < contentY + contentHeight) {
                    ctx.fill(getX() + padding + (myCursorX - 1) * getCharWidth(), cursorScreenY - 1, getX() + padding + myCursorX * getCharWidth(), cursorScreenY + lineHeight - 1, Config.globalCursorAnimatedColor);
                }
            }
        } finally {
            myTextBuffer.unlock();
        }

        ctx.disableScissor();

    }

    private Color fromAwtColor(java.awt.Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isFocused() || myTerminalStarter == null) return super.mouseClicked(mouseX, mouseY, button);

        int awtModifiers = getAwtModifiersFromPoll();
        if (isRemoteMouseAction(button, awtModifiers)) {
            Point p = panelToScreenCoords(mouseX, mouseY);
            com.jediterm.core.input.MouseEvent event = createJediTermMouseEvent(button, awtModifiers);
            if (event.getButtonCode() != MouseButtonCodes.NONE) {
                myTerminal.mousePressed(p.x + 1, p.y + 1, event);
            }
        } else if (isLocalMouseAction(button, awtModifiers)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                mySelectionStartPoint = panelToBufferCoords(mouseX, mouseY);
                mySelection = null;
                scheduleRepaint();
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isFocused() || myTerminalStarter == null) return super.mouseReleased(mouseX, mouseY, button);

        int awtModifiers = getAwtModifiersFromPoll();
        if (isRemoteMouseAction(button, awtModifiers)) {
            Point p = panelToScreenCoords(mouseX, mouseY);
            com.jediterm.core.input.MouseEvent event = createJediTermMouseEvent(button, awtModifiers);
            if (event.getButtonCode() != MouseButtonCodes.NONE) {
                myTerminal.mouseReleased(p.x + 1, p.y + 1, event);
            }
        } else if (isLocalMouseAction(button, awtModifiers)) {
            if (mySettingsProvider.copyOnSelect() && mySelection != null) {
                handleCopy();
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!isFocused() || myTerminalStarter == null) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        int awtModifiers = getAwtModifiersFromPoll();

        if (isRemoteMouseAction(button, awtModifiers)) {
            Point screenCoords = panelToScreenCoords(mouseX, mouseY);
            com.jediterm.core.input.MouseEvent event = createJediTermMouseEvent(button, awtModifiers);
            if (event.getButtonCode() != MouseButtonCodes.NONE) {
                myTerminal.mouseDragged(screenCoords.x + 1, screenCoords.y + 1, event);
            }
            return true;
        }

        if (isLocalMouseAction(button, awtModifiers)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (mySelectionStartPoint != null) {
                    if (mySelection == null) {
                        mySelection = new TerminalSelection(mySelectionStartPoint);
                    }
                    Point bufferCoords = panelToBufferCoords(mouseX, mouseY);
                    mySelection.updateEnd(bufferCoords);
                    scheduleRepaint();
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            int awtModifiers = getAwtModifiersFromPoll();
            if (isRemoteMouseAction(-1, awtModifiers) && myTerminalStarter != null) {
                Point p = panelToScreenCoords(mouseX, mouseY);
                com.jediterm.core.input.MouseEvent event = createJediTermMouseWheelEvent(verticalAmount, awtModifiers);
                myTerminal.mousePressed(p.x + 1, p.y + 1, event);
                return true;
            }
            targetScrollY += (float) (verticalAmount * (tr.fontHeight + 2) * 3);
            clampScroll();
            return true;
        }
        return false;
    }

    private Point panelToBufferCoords(double mouseX, double mouseY) {
        int padding = 2;
        int lineHeight = tr.fontHeight + 2;
        int charWidth = getCharWidth();

        int charX = (int) ((mouseX - (getX() + padding)) / charWidth);
        charX = Math.max(0, Math.min(charX, myLastTermSize.getColumns() - 1));

        int lineOnScreen = (int) Math.floor((mouseY - (getY() + padding)) / lineHeight);

        int scrolledLines = (int) Math.floor(scrollY / lineHeight);
        int historyLines = myTextBuffer.getHistoryLinesCount();

        int bufferLine = lineOnScreen + scrolledLines - historyLines;

        return new Point(charX, bufferLine);
    }

    private Point panelToScreenCoords(double mouseX, double mouseY) {
        int padding = 2;
        int lineHeight = tr.fontHeight + 2;
        int charWidth = getCharWidth();
        int charX = (int) ((mouseX - (getX() + padding)) / charWidth);
        charX = Math.max(0, Math.min(charX, myLastTermSize.getColumns() - 1));
        int charY = (int) Math.floor((mouseY - (getY() + padding)) / lineHeight);
        charY = Math.max(0, Math.min(charY, myLastTermSize.getRows() - 1));
        return new Point(charX, charY);
    }

    private int getAwtModifiersFromPoll() {
        long windowHandle = mc.getWindow().getHandle();
        int glfwModifiers = 0;
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            glfwModifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
            glfwModifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
            glfwModifiers |= GLFW.GLFW_MOD_ALT;
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS) {
            glfwModifiers |= GLFW.GLFW_MOD_SUPER;
        }
        return KeyCodeConverter.toAwtModifiers(glfwModifiers);
    }

    private boolean isRemoteMouseAction(int button, int awtModifiers) {
        return myMouseMode != MouseMode.MOUSE_REPORTING_NONE && (awtModifiers & InputEvent.SHIFT_MASK) == 0;
    }

    private boolean isLocalMouseAction(int button, int awtModifiers) {
        return mySettingsProvider.forceActionOnMouseReporting() || (myMouseMode == MouseMode.MOUSE_REPORTING_NONE || (awtModifiers & InputEvent.SHIFT_MASK) != 0);
    }

    private com.jediterm.core.input.MouseEvent createJediTermMouseEvent(int button, int awtModifiers) {
        int jediButton = switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseButtonCodes.LEFT;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseButtonCodes.MIDDLE;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseButtonCodes.RIGHT;
            default -> MouseButtonCodes.NONE;
        };
        return new com.jediterm.core.input.MouseEvent(jediButton, awtModifiers);
    }

    private com.jediterm.core.input.MouseWheelEvent createJediTermMouseWheelEvent(double verticalAmount, int awtModifiers) {
        int jediButton = verticalAmount > 0 ? MouseButtonCodes.SCROLLUP : MouseButtonCodes.SCROLLDOWN;
        return new com.jediterm.core.input.MouseWheelEvent(jediButton, awtModifiers);
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
        if (code != null && myTerminalStarter != null) {
            myTerminalStarter.sendBytes(code, true);
            if (mySettingsProvider.scrollToBottomOnTyping()) {
                scrollToBottom();
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

    public JediTerminal getTerminal() {
        return myTerminal;
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
        this.myMouseMode = mouseMode;
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